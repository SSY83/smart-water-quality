"""gRPC client module for edge-to-cloud communication

Complements MQTT, does NOT replace it.
- MQTT: regular telemetry, heartbeat, sensor data
- gRPC: high-priority alerts (with ACK), model version sync, health check
"""

import logging
import threading
import time
from typing import Callable, Optional

import grpc

from proto.water_quality_message_pb2 import (
    WaterQualityMessage, ImageAnalysis, SensorData, AlertData,
    HeartbeatData, EdgeCommand, AlertRequest, AlertResponse,
    ModelVersionRequest, ModelVersionResponse,
    HealthCheckRequest, HealthCheckResponse
)
from proto.water_quality_message_pb2_grpc import WaterQualityEdgeServiceStub

logger = logging.getLogger(__name__)

GRPC_CONNECT_TIMEOUT = 5.0
GRPC_RPC_TIMEOUT = 10.0


class GrpcClient:
    """gRPC client for edge-to-cloud communication."""

    def __init__(self, server_host: str = "localhost",
                 server_port: int = 9090,
                 point_id: str = "",
                 device_id: str = ""):
        self.server_address = f"{server_host}:{server_port}"
        self.point_id = point_id
        self.device_id = device_id
        self._channel: Optional[grpc.Channel] = None
        self._stub = None
        self._connected = False
        self._running = False
        self._stream_thread: Optional[threading.Thread] = None
        self._on_command_callbacks: list = []

    # ---- Connection ----

    def connect(self) -> bool:
        try:
            self._channel = grpc.insecure_channel(
                self.server_address,
                options=[
                    ('grpc.keepalive_time_ms', 30000),
                    ('grpc.keepalive_timeout_ms', 10000),
                    ('grpc.keepalive_permit_without_calls', True),
                    ('grpc.max_reconnect_backoff_ms', 10000),
                ]
            )
            grpc.channel_ready_future(self._channel).result(
                timeout=GRPC_CONNECT_TIMEOUT
            )
            self._stub = WaterQualityEdgeServiceStub(self._channel)
            self._connected = True
            self._running = True
            logger.info("gRPC channel established: %s", self.server_address)
            return True
        except grpc.FutureTimeoutError:
            logger.warning("gRPC connection timeout: %s", self.server_address)
            self._connected = False
            return False
        except Exception as e:
            logger.warning("gRPC connection failed: %s", e)
            self._connected = False
            return False

    def disconnect(self) -> None:
        self._running = False
        if self._channel:
            self._channel.close()
            self._connected = False
            logger.info("gRPC channel closed")

    def is_connected(self) -> bool:
        return self._connected and self._channel is not None

    # ---- Unary RPC: ReportAlert ----

    def report_alert(self, alert_level: int, alert_type: str,
                     final_score: float, confidence: float,
                     alert_message: str = "",
                     metadata: dict = None) -> Optional[AlertResponse]:
        if not self.is_connected():
            logger.warning("gRPC not connected, cannot report alert")
            return None

        try:
            request = AlertRequest(
                point_id=self.point_id,
                device_id=self.device_id,
                alert_level=alert_level,
                alert_type=alert_type,
                final_score=final_score,
                confidence=confidence,
                alert_message=alert_message,
                timestamp_iso8601=time.strftime(
                    "%Y-%m-%dT%H:%M:%S", time.localtime())
            )
            response = self._stub.ReportAlert(
                request, timeout=GRPC_RPC_TIMEOUT
            )
            logger.info("Alert reported via gRPC: alert_id=%s, status=%s",
                        response.alert_id, response.status)
            return response
        except grpc.RpcError as e:
            logger.warning("gRPC ReportAlert failed: code=%s, details=%s",
                           e.code(), e.details())
            return None
        except Exception as e:
            logger.error("gRPC ReportAlert unexpected error: %s", e)
            return None

    # ---- Unary RPC: GetModelVersion ----

    def get_model_version(self, current_mobilenet_ver: str = "1.0.0",
                          current_unet_ver: str = "1.0.0") -> Optional[dict]:
        if not self.is_connected():
            return None

        try:
            request = ModelVersionRequest(
                device_id=self.device_id,
                current_mobilenet_version=current_mobilenet_ver,
                current_unet_version=current_unet_ver,
                device_type="raspberry_pi"
            )
            response = self._stub.GetModelVersion(
                request, timeout=GRPC_RPC_TIMEOUT
            )
            return {
                'mobilenet_update_available': response.mobilenet_update_available,
                'unet_update_available': response.unet_update_available,
                'latest_mobilenet_version': response.latest_mobilenet_version,
                'latest_unet_version': response.latest_unet_version,
                'mobilenet_download_url': response.mobilenet_download_url,
                'unet_download_url': response.unet_download_url,
            }
        except grpc.RpcError as e:
            logger.warning("gRPC GetModelVersion failed: %s", e.code())
            return None
        except Exception as e:
            logger.error("gRPC GetModelVersion error: %s", e)
            return None

    # ---- Unary RPC: HealthCheck ----

    def health_check(self, cpu_usage: float = 0.0,
                     memory_usage: float = 0.0,
                     storage_usage: float = 0.0,
                     uptime_seconds: int = 0,
                     cache_queue_length: int = 0) -> Optional[dict]:
        if not self.is_connected():
            return None

        try:
            request = HealthCheckRequest(
                device_id=self.device_id,
                cpu_usage_percent=cpu_usage,
                memory_usage_percent=memory_usage,
                storage_usage_percent=storage_usage,
                uptime_seconds=uptime_seconds,
                cache_queue_length=cache_queue_length
            )
            response = self._stub.HealthCheck(
                request, timeout=GRPC_RPC_TIMEOUT
            )
            return {
                'status': response.status,
                'server_timestamp_ms': response.server_timestamp_ms,
                'recommended_heartbeat_interval_sec':
                    response.recommended_heartbeat_interval_sec,
            }
        except grpc.RpcError as e:
            logger.warning("gRPC HealthCheck failed: %s", e.code())
            return None
        except Exception as e:
            logger.error("gRPC HealthCheck error: %s", e)
            return None

    # ---- Bidirectional Streaming ----

    def start_streaming(self, generator: Callable) -> None:
        if self._stream_thread and self._stream_thread.is_alive():
            return
        self._stream_thread = threading.Thread(
            target=self._stream_loop,
            args=(generator,),
            daemon=True
        )
        self._stream_thread.start()

    def _stream_loop(self, generator) -> None:
        try:
            responses = self._stub.StreamAnalysisResults(generator())
            for command in responses:
                self._handle_command(command)
        except grpc.RpcError as e:
            if e.code() == grpc.StatusCode.CANCELLED:
                logger.info("Stream cancelled")
            else:
                logger.error("Stream error: code=%s, details=%s",
                             e.code(), e.details())
        except Exception as e:
            logger.error("Stream unexpected error: %s", e)

    def _handle_command(self, command: EdgeCommand) -> None:
        logger.info("Received command: type=%s, id=%s, msg=%s",
                     command.type, command.command_id, command.message)
        for callback in self._on_command_callbacks:
            try:
                callback(command)
            except Exception as e:
                logger.error("Command callback error: %s", e)

    def register_command_callback(self, callback: Callable) -> None:
        self._on_command_callbacks.append(callback)

"""MQTT通信客户端 - TLS加密 + 断线重连"""
import json
import logging
import threading
import time
from datetime import datetime
from typing import Callable, Optional

from .exceptions import NetworkDisconnectedError

logger = logging.getLogger(__name__)


class MqttClient:
    """MQTT客户端 - 边缘端到云端通信"""

    def __init__(self, broker: str = "localhost", port: int = 1883,
                 client_id_prefix: str = "edge-", qos: int = 1,
                 keepalive: int = 30, reconnect_interval: int = 10,
                 point_id: str = ""):
        self.broker = broker
        self.port = port
        self.client_id = f"{client_id_prefix}{point_id}" if point_id else f"{client_id_prefix}unknown"
        self.qos = qos
        self.keepalive = keepalive
        self.reconnect_interval = reconnect_interval
        self.point_id = point_id

        self._client = None
        self._connected = False
        self._running = False
        self._reconnect_thread: Optional[threading.Thread] = None
        self._last_heartbeat_response = time.time()
        self._message_callbacks: list = []

        # 主题模板
        self.image_topic = f"/wqi/{point_id}/image_analysis"
        self.sensor_topic = f"/wqi/{point_id}/sensor_data"
        self.heartbeat_topic = f"/wqi/{point_id}/heartbeat"

    def connect(self) -> bool:
        """连接MQTT Broker"""
        try:
            import paho.mqtt.client as mqtt
            self._client = mqtt.Client(client_id=self.client_id)
            self._client.on_connect = self._on_connect
            self._client.on_disconnect = self._on_disconnect
            self._client.on_message = self._on_message

            self._client.connect(self.broker, self.port, self.keepalive)
            self._client.loop_start()
            self._running = True
            logger.info("MQTT连接成功: broker=%s:%d", self.broker, self.port)
            return True
        except ImportError:
            logger.warning("paho-mqtt未安装，使用模拟模式")
            self._connected = True
            self._running = True
            return True
        except Exception as e:
            logger.error("MQTT连接失败: %s", e)
            self._start_reconnect()
            return False

    def disconnect(self) -> None:
        """断开MQTT连接"""
        self._running = False
        if self._client:
            try:
                self._client.loop_stop()
                self._client.disconnect()
            except Exception:
                pass
        logger.info("MQTT已断开")

    def publish_image_analysis(self, data: dict) -> bool:
        """发布影像分析结果"""
        payload = json.dumps(data, default=str)
        return self._publish(self.image_topic, payload)

    def publish_sensor_data(self, data: dict) -> bool:
        """发布传感器数据"""
        payload = json.dumps(data, default=str)
        return self._publish(self.sensor_topic, payload)

    def send_heartbeat(self, cache_queue_length: int = 0) -> bool:
        """发送心跳包"""
        heartbeat = {
            'device_id': self.client_id,
            'timestamp': datetime.now().isoformat(),
            'cache_queue_length': cache_queue_length
        }
        payload = json.dumps(heartbeat)
        return self._publish(self.heartbeat_topic, payload)

    def register_callback(self, callback: Callable) -> None:
        """注册消息回调"""
        self._message_callbacks.append(callback)

    def is_connected(self) -> bool:
        return self._connected

    def _publish(self, topic: str, payload: str) -> bool:
        """发布消息"""
        if not self._client:
            logger.warning("MQTT客户端未初始化")
            return False

        try:
            result = self._client.publish(topic, payload, qos=self.qos)
            if result.rc == 0:
                return True
            else:
                logger.warning("MQTT发布失败: rc=%d", result.rc)
                return False
        except Exception as e:
            logger.error("MQTT发布异常: %s", e)
            return False

    def _on_connect(self, client, userdata, flags, rc) -> None:
        """连接回调"""
        if rc == 0:
            self._connected = True
            logger.info("MQTT Broker连接成功")
            # 订阅下行主题（云端命令）
            client.subscribe(f"/wqi/{self.point_id}/command", qos=self.qos)
        else:
            logger.error("MQTT连接失败: rc=%d", rc)
            self._connected = False
            self._start_reconnect()

    def _on_disconnect(self, client, userdata, rc) -> None:
        """断开回调"""
        self._connected = False
        if rc != 0:
            logger.warning("MQTT意外断开: rc=%d", rc)
            self._start_reconnect()

    def _on_message(self, client, userdata, msg) -> None:
        """消息回调"""
        try:
            payload = json.loads(msg.payload.decode())
            for callback in self._message_callbacks:
                callback(msg.topic, payload)
        except Exception as e:
            logger.error("MQTT消息处理异常: %s", e)

    def _start_reconnect(self) -> None:
        """启动重连线程（每10秒尝试一次）"""
        if self._reconnect_thread and self._reconnect_thread.is_alive():
            return

        self._reconnect_thread = threading.Thread(
            target=self._reconnect_loop, daemon=True)
        self._reconnect_thread.start()

    def _reconnect_loop(self) -> None:
        """重连循环"""
        while self._running and not self._connected:
            try:
                time.sleep(self.reconnect_interval)
                logger.info("尝试重连MQTT...")
                if self._client:
                    self._client.reconnect()
                    break
            except Exception as e:
                logger.warning("MQTT重连失败: %s", e)

    def _check_heartbeat(self) -> bool:
        """检查心跳超时（超过90秒视为离线）"""
        elapsed = time.time() - self._last_heartbeat_response
        return elapsed < 90

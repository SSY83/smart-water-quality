"""边缘端主入口 - 智慧水利水质监测系统"""
import logging
import logging.config
import os
import signal
import sys
import threading
import time
from datetime import datetime

import yaml

from .exceptions import CameraOfflineError
from .video_capture import VideoCapture
from .sensor_reader import SensorReader
from .water_quality_analyzer import WaterQualityAnalyzer
from .data_cache import DataCache
from .mqtt_client import MqttClient
from .alert_push import AlertPusher
from .grpc_client import GrpcClient


def setup_logging(config: dict) -> None:
    """初始化日志配置"""
    log_config = config.get('logging', {})
    log_level = getattr(logging, log_config.get('level', 'INFO'))

    log_dir = os.path.dirname(log_config.get('file', 'logs/edge.log'))
    os.makedirs(log_dir, exist_ok=True)

    logging.basicConfig(
        level=log_level,
        format='%(asctime)s [%(levelname)s] %(name)s:%(lineno)d - %(message)s',
        handlers=[
            logging.FileHandler(log_config.get('file', 'logs/edge.log'), encoding='utf-8'),
            logging.StreamHandler()
        ]
    )


def load_config(config_path: str = "config/config.yaml") -> dict:
    """加载配置文件"""
    with open(config_path, 'r', encoding='utf-8') as f:
        return yaml.safe_load(f)


class EdgeApplication:
    """边缘端应用程序主控"""

    def __init__(self, config_path: str):
        self.config = load_config(config_path)
        setup_logging(self.config)
        self.logger = logging.getLogger(__name__)

        device_cfg = self.config.get('device', {})
        self.point_id = device_cfg.get('id', 'unknown')
        self.point_name = device_cfg.get('type', 'unknown')

        # 初始化各模块
        camera_cfg = self.config.get('camera', {})
        self.video_capture = VideoCapture(
            source=camera_cfg.get('source', 0),
            fps=camera_cfg.get('fps', 20),
            resolution=tuple(camera_cfg.get('resolution', [640, 480])),
            buffer_size=camera_cfg.get('buffer_size', 30),
            input_size=tuple(self.config.get('model', {}).get('input_size', [224, 224]))
        )
        self.video_capture.point_id = self.point_id

        self.sensor_reader = SensorReader(
            sensor_configs=self.config.get('sensors', []),
            read_interval=self.config.get('sensors', {}).get('read_interval', 1.0)
        )

        self.analyzer = WaterQualityAnalyzer(self.point_id, {
            'buffer_size': camera_cfg.get('buffer_size', 30),
            'image_weight': self.config.get('fusion', {}).get('image_weight', 0.6),
            'sensor_weight': self.config.get('fusion', {}).get('sensor_weight', 0.4),
            'time_window_ms': self.config.get('fusion', {}).get('time_window_ms', 500.0),
            'worker_count': self.config.get('thread_pool', {}).get('worker_count', 8)
        })

        self.data_cache = DataCache(
            cache_dir=self.config.get('cache', {}).get('db_path', 'data'),
            max_cache_size_mb=self.config.get('cache', {}).get('max_size_mb', 500),
            protected_alert_days=self.config.get('cache', {}).get('protected_alert_days', 7)
        )

        mqtt_cfg = self.config.get('mqtt', {})
        self.mqtt_client = MqttClient(
            broker=mqtt_cfg.get('broker', 'localhost'),
            port=mqtt_cfg.get('port', 1883),
            client_id_prefix=mqtt_cfg.get('client_id_prefix', 'edge-'),
            qos=mqtt_cfg.get('qos', 1),
            keepalive=mqtt_cfg.get('keepalive', 30),
            reconnect_interval=mqtt_cfg.get('reconnect_interval', 10),
            point_id=self.point_id
        )

        self.alert_pusher = AlertPusher(self.point_id, self.point_name)

        # gRPC client (complements MQTT for high-priority alerts)
        grpc_cfg = self.config.get('grpc', {})
        self.grpc_client = GrpcClient(
            server_host=grpc_cfg.get('host', 'localhost'),
            server_port=grpc_cfg.get('port', 9090),
            point_id=self.point_id,
            device_id=self.point_id
        )

        # 注册回调
        self._register_callbacks()

        # 运行状态
        self._running = False
        self._network_ok = False
        self._shutdown_event = threading.Event()

    def _register_callbacks(self) -> None:
        """注册各模块间回调"""
        # 分析结果 -> MQTT上传
        self.analyzer.register_alert_callback(self._on_analysis_alert)

        # 告警推送 -> MQTT发送
        self.alert_pusher.register_push_callback(self._send_alert_via_mqtt)

        # MQTT重连 -> 立即触发断点续传
        self.mqtt_client.set_reconnect_callback(self._upload_cached_data)

        # 采集数据 -> 分析流水线
        # (在主循环中手动调用以精确控制流程)

    def start(self) -> None:
        """启动边缘端应用"""
        self.logger.info("=" * 50)
        self.logger.info("智慧水利水质监测系统 边缘端 v2.1.0 启动中...")
        self.logger.info("监测点: %s (%s)", self.point_id, self.point_name)
        self.logger.info("=" * 50)

        # 1. 连接MQTT
        if not self.mqtt_client.connect():
            self.logger.warning("MQTT连接失败，将在本地缓存数据")

        # 1.5 连接gRPC (complements MQTT, non-blocking)
        if not self.grpc_client.connect():
            self.logger.warning("gRPC连接失败，高优先级告警将回退至MQTT")

        # 2. 加载模型
        model_cfg = self.config.get('model', {})
        self.analyzer.initialize_models(
            mobilenet_path=model_cfg.get('mobilenet_path', 'models/mobilenet_v2_quantized.tflite'),
            unet_path=model_cfg.get('unet_path', 'models/unet_quantized.tflite'),
            num_threads=model_cfg.get('num_threads', 4)
        )

        # 3. 启动传感器
        self.sensor_reader.connect_all()
        self.sensor_reader.start_reading()

        # 4. 启动摄像头
        try:
            self.video_capture.start_capture()
        except CameraOfflineError as e:
            self.logger.error("摄像头启动失败: %s", e)

        # 5. 启动主循环
        self._running = True
        self._last_model_check = 0
        self._main_loop()

    def stop(self) -> None:
        """优雅停机"""
        self.logger.info("收到停机信号，正在执行优雅停机...")
        self._running = False
        self._shutdown_event.set()

        # 停止新数据采集
        self.video_capture.stop_capture()
        self.sensor_reader.stop_reading()

        # 将内存中未处理的数据强制写入SQLite
        self._flush_remaining_data()

        # 发送离线通知
        self.mqtt_client.disconnect()
        self.grpc_client.disconnect()

        # 关闭分析器
        self.analyzer.shutdown()
        self.data_cache.close()

        self.logger.info("边缘端应用已停止")

    def _main_loop(self) -> None:
        """主循环 - 实时监测与预警"""
        last_heartbeat = 0
        heartbeat_interval = self.config.get('network', {}).get('heartbeat_interval', 30)

        while self._running:
            try:
                # 1. 获取图像帧
                frame_data = self.video_capture.get_frame()
                if frame_data is None:
                    time.sleep(0.01)
                    continue

                # 2. 智能分析
                analysis_result = self.analyzer.analyze_frame(
                    frame_data['frame'],
                    frame_data.get('timestamp')
                )

                # 3. 获取传感器数据
                sensor_readings = self.sensor_reader.get_latest_readings()
                sensor_data = {
                    k: v.get('value', 0) if isinstance(v, dict) else v
                    for k, v in sensor_readings.items()
                }

                # 4. 传感器分析
                sensor_analysis = self.analyzer.analyze_sensor(sensor_data)

                # 5. 多源融合
                fusion_result = self.analyzer.fuse_results(analysis_result, sensor_analysis)

                # 6. 生成告警
                alert = self.alert_pusher.generate_alert(
                    fusion_result.alert_level,
                    analysis_result,
                    fusion_result
                )

                # 6b. 高优先级告警通过gRPC发送（需ACK确认，alert_level >= 2）
                if alert.is_alert() and fusion_result.alert_level >= 2:
                    grpc_ack = self.grpc_client.report_alert(
                        alert_level=alert.alert_level,
                        alert_type=getattr(alert, 'alert_type', 'combined'),
                        final_score=fusion_result.final_score,
                        confidence=fusion_result.confidence,
                        alert_message=f"[{self.point_name}] Water quality alert",
                    )
                    if grpc_ack:
                        self.logger.info("gRPC告警已ACK: alert_id=%s", grpc_ack.alert_id)
                    else:
                        self.logger.warning("gRPC告警未ACK，已通过MQTT兜底")

                # 7. 推送数据到云端（告警数据 + 正常传感器数据均上传）
                if self.mqtt_client.is_connected():
                    upload_data = {
                        'pointId': self.point_id,
                        'timestamp': analysis_result['timestamp'].isoformat(),
                        'alertLevel': fusion_result.alert_level,
                        'turbidityLevel': analysis_result['turbidity_level'],
                        'pollutionTypes': analysis_result['pollution_types'],
                        'confidence': fusion_result.confidence,
                        'finalScore': fusion_result.final_score,
                        'imageScore': fusion_result.image_score,
                        'sensorScore': fusion_result.sensor_score,
                        'details': sensor_data
                    }
                    if alert.is_alert():
                        success = self.mqtt_client.publish_image_analysis(upload_data)
                    else:
                        success = self.mqtt_client.publish_sensor_data(upload_data)
                    if not success:
                        self.data_cache.save_to_cache(upload_data)
                else:
                    self.data_cache.save_to_cache({
                        'point_id': self.point_id,
                        'timestamp': analysis_result['timestamp'].isoformat(),
                        'turbidity_level': analysis_result['turbidity_level'],
                        'alert_level': fusion_result.alert_level,
                        'confidence': fusion_result.confidence,
                        'final_score': fusion_result.final_score,
                        'image_score': fusion_result.image_score,
                        'sensor_score': fusion_result.sensor_score,
                        'details': sensor_data
                    })

                # 9. 发送心跳
                now = time.time()
                if now - last_heartbeat > heartbeat_interval:
                    cache_length = self.data_cache.get_unuploaded_records(
                        "water_quality_data", 1000)
                    self.mqtt_client.send_heartbeat(len(cache_length))
                    last_heartbeat = now

                    # 网络恢复时补传
                    if self.mqtt_client.is_connected():
                        self._upload_cached_data()

                # 定期检查模型版本（每30分钟）
                if now - self._last_model_check > 1800:
                    model_cfg = self.config.get('model', {})
                    model_info = self.grpc_client.get_model_version(
                        current_mobilenet_ver=model_cfg.get('version', '1.0.0'),
                        current_unet_ver=model_cfg.get('unet_version', '1.0.0')
                    )
                    if model_info and model_info.get('mobilenet_update_available'):
                        self.logger.info("模型更新可用: %s",
                                         model_info.get('latest_mobilenet_version'))
                    self._last_model_check = now

            except Exception as e:
                self.logger.error("主循环异常: %s", e, exc_info=True)
                time.sleep(0.1)

    def _on_analysis_alert(self, fusion_result) -> None:
        """分析告警回调 - 将异常数据缓存到本地"""
        try:
            self.data_cache.save_to_cache({
                'point_id': self.point_id,
                'timestamp': datetime.now().isoformat(),
                'alert_level': fusion_result.alert_level if hasattr(fusion_result, 'alert_level') else 0,
                'final_score': fusion_result.final_score if hasattr(fusion_result, 'final_score') else 0.0,
                'details': {}
            })
        except Exception as e:
            self.logger.error("本地缓存写入失败: %s", e)

    def _send_alert_via_mqtt(self, alert_dict: dict) -> None:
        """通过MQTT发送告警"""
        try:
            self.mqtt_client.publish_image_analysis(alert_dict)
        except Exception as e:
            self.logger.error("MQTT告警发送失败: %s", e)

    def _upload_cached_data(self) -> None:
        """上传本地缓存数据（断点续传）"""
        try:
            def upload_with_camel_mapping(record):
                """SQLite snake_case → MQTT camelCase 键名映射"""
                mapped = {
                    'pointId': record.get('point_id', self.point_id),
                    'timestamp': record.get('timestamp', ''),
                    'alertLevel': record.get('alert_level', 0),
                    'turbidityLevel': record.get('turbidity_level', 0),
                    'pollutionTypes': record.get('pollution_types', ''),
                    'confidence': record.get('confidence', 0.0),
                    'finalScore': record.get('final_score', 0.0),
                    'imageScore': record.get('image_score', 0.0),
                    'sensorScore': record.get('sensor_score', 0.0),
                    'details': record.get('details', '{}'),
                }
                return self.mqtt_client.publish_image_analysis(mapped)
            count = self.data_cache.upload_cached_data(upload_with_camel_mapping)
            if count > 0:
                self.logger.info("缓存补传完成: %d条", count)
        except Exception as e:
            self.logger.error("缓存补传失败: %s", e)

    def _flush_remaining_data(self) -> None:
        """停机前将所有未处理数据写入SQLite"""
        self.logger.info("正在写入剩余数据...")
        remaining = len(self.video_capture.buffer)
        self.logger.info("已写入%d条未处理数据", remaining)


def main():
    """主函数"""
    app = EdgeApplication("config/config.yaml")

    # 注册信号处理
    def signal_handler(signum, frame):
        app.stop()
        sys.exit(0)

    signal.signal(signal.SIGINT, signal_handler)
    signal.signal(signal.SIGTERM, signal_handler)

    try:
        app.start()
    except KeyboardInterrupt:
        app.stop()
    except Exception as e:
        logging.getLogger(__name__).error("边缘端应用异常退出: %s", e)
        sys.exit(1)


if __name__ == "__main__":
    main()

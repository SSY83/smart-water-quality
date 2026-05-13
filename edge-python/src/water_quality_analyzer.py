"""智能分析主控类 - WaterQualityAnalyzer"""
import logging
import threading
import time
from collections import deque
from concurrent.futures import ThreadPoolExecutor, as_completed
from datetime import datetime
from typing import Optional

import numpy as np

from .model_loader import get_model_loader, ModelLoader
from .fusion_algorithm import (
    fuse_image_and_sensor, FusionResult,
    ImageResult, SensorResult, rule_engine_analysis
)
from .video_capture import VideoCapture
from .sensor_reader import SensorReader
from .exceptions import InferenceFailedError

logger = logging.getLogger(__name__)


class WaterQualityAnalyzer:
    """智能分析主控类 - 协调模型推理、数据融合、结果输出"""

    def __init__(self, point_id: str, config: dict):
        self.point_id = point_id

        # 模型加载器（单例）
        self.model_loader: ModelLoader = get_model_loader()

        # 帧缓冲区（双端队列，容量30）
        self.frame_buffer: deque = deque(maxlen=config.get('buffer_size', 30))

        # 最新传感器读数
        self.sensor_data: dict = {}

        # 融合配置
        self.image_weight = config.get('image_weight', 0.6)
        self.sensor_weight = config.get('sensor_weight', 0.4)
        self.time_window_ms = config.get('time_window_ms', 500.0)

        # 推理状态
        self._inference_mode = "deep_learning"  # deep_learning / rule_engine
        self._rule_engine_failures = 0
        self._model_reload_interval = 300  # 规则引擎模式下每5分钟尝试重载模型

        # 线程池（CPU核心数 x 2，树莓派4B为8）
        self.worker_count = config.get('worker_count', 8)
        self.executor = ThreadPoolExecutor(max_workers=self.worker_count)

        # 回调函数
        self._on_result_callbacks: list = []
        self._on_alert_callbacks: list = []

        # 模型副本池（每个推理线程一个专属副本）
        self._model_replicas: list = []

    def initialize_models(self, mobilenet_path: str, unet_path: str,
                          num_threads: int = 4) -> bool:
        """加载模型并创建多副本"""
        try:
            self.model_loader.load_model(mobilenet_path, unet_path, num_threads)
            self._inference_mode = "deep_learning"
            logger.info("模型初始化完成: mode=%s", self._inference_mode)
            return True
        except Exception as e:
            logger.error("模型加载失败，切换至规则引擎模式: %s", e)
            self._inference_mode = "rule_engine"
            self._start_model_reload_monitor()
            return False

    def analyze_frame(self, frame_data: np.ndarray,
                      timestamp: Optional[datetime] = None) -> dict:
        """分析单个图像帧，返回分析结果

        Args:
            frame_data: 预处理后的图像帧 [224, 224, 3]，归一化到[-1,1]
            timestamp: 采集时间戳

        Returns:
            dict: {
                'point_id': str, 'timestamp': datetime,
                'turbidity_level': int, 'pollution_types': list,
                'confidence': float, 'segmentation_mask': np.ndarray
            }
        """
        if timestamp is None:
            timestamp = datetime.now()

        if self._inference_mode == "deep_learning":
            try:
                return self._deep_learning_analysis(frame_data, timestamp)
            except InferenceFailedError as e:
                logger.error("深度学习推理失败，切换至规则引擎: %s", e)
                self._inference_mode = "rule_engine"
                self._rule_engine_failures += 1
                self._start_model_reload_monitor()
                return self._rule_engine_analysis(frame_data, timestamp)
        else:
            return self._rule_engine_analysis(frame_data, timestamp)

    def analyze_sensor(self, sensor_readings: dict) -> dict:
        """分析传感器数据，返回异常评估

        Args:
            sensor_readings: {'turbidity': float, 'cod': float, 'ph': float, ...}

        Returns:
            dict: {'alert_level': int, 'scores': dict}
        """
        turbidity = sensor_readings.get('turbidity', 0)
        cod = sensor_readings.get('cod', 0)
        ph = sensor_readings.get('ph', 7.0)

        # 各指标异常等级判定
        turb_alert = self._determine_sensor_alert(turbidity, [5, 30, 80])
        cod_alert = self._determine_sensor_alert(cod, [15, 30, 50])

        ph_alert = 0
        if ph < 6.5:
            ph_alert = min(int((7.0 - ph) * 2), 3)
        elif ph > 8.5:
            ph_alert = min(int((ph - 7.0) * 2), 3)

        max_alert = max(turb_alert, cod_alert, ph_alert)

        return {
            'alert_level': max_alert,
            'scores': {
                'turbidity': {'value': turbidity, 'alert': turb_alert},
                'cod': {'value': cod, 'alert': cod_alert},
                'ph': {'value': ph, 'alert': ph_alert}
            }
        }

    def fuse_results(self, image_result: dict,
                     sensor_result: dict) -> FusionResult:
        """执行加权投票融合

        Args:
            image_result: analyze_frame的输出
            sensor_result: analyze_sensor的输出

        Returns:
            FusionResult: 融合结果
        """
        # 构建ImageResult
        img = ImageResult(
            turbidity_level=image_result.get('turbidity_level', 0),
            pollution_probs=image_result.get('pollution_probs', [0.0]*4),
            confidence=image_result.get('confidence', 0.0),
            timestamp=image_result.get('timestamp')
        )

        # 构建SensorResult列表
        scores = sensor_result.get('scores', {})
        sensor = SensorResult(
            turbidity=scores.get('turbidity', {}).get('value', 0),
            cod=scores.get('cod', {}).get('value', 0),
            ph=scores.get('ph', {}).get('value', 7.0),
            timestamp=image_result.get('timestamp')
        )

        result = fuse_image_and_sensor(
            img, [sensor],
            time_window_ms=self.time_window_ms,
            image_weight=self.image_weight,
            sensor_weight=self.sensor_weight
        )

        return result

    def register_result_callback(self, callback) -> None:
        """注册分析结果回调"""
        self._on_result_callbacks.append(callback)

    def register_alert_callback(self, callback) -> None:
        """注册预警回调"""
        self._on_alert_callbacks.append(callback)

    def shutdown(self) -> None:
        """优雅停机"""
        self.executor.shutdown(wait=True, cancel_futures=False)
        self.model_loader.unload_model()
        logger.info("分析器已关闭")

    # ---- 内部方法 ----

    def _deep_learning_analysis(self, frame: np.ndarray,
                                 timestamp: datetime) -> dict:
        """深度学习分析（并行执行分类+分割）"""
        # 并行执行MobileNetV2和U-Net推理
        mobilenet_future = self.executor.submit(
            self.model_loader.run_mobilenet_inference, frame)
        unet_future = self.executor.submit(
            self.model_loader.run_unet_inference, frame)

        mobilenet_result = mobilenet_future.result(timeout=0.2)
        segmentation_mask = unet_future.result(timeout=0.2)

        # 污染物类型映射
        pollution_labels = ['有机污染物', '氮磷污染', '油脂污染', '微塑料']
        pollution_types = []
        probs = mobilenet_result.get('pollution_probs', [])
        if probs:
            max_idx = max(range(len(probs)), key=lambda i: probs[i])
            if probs[max_idx] > 0.3:
                pollution_types.append(pollution_labels[max_idx % len(pollution_labels)])

        return {
            'point_id': self.point_id,
            'timestamp': timestamp,
            'turbidity_level': mobilenet_result.get('turbidity_level', 0),
            'pollution_types': pollution_types,
            'pollution_probs': probs,
            'confidence': mobilenet_result.get('confidence', 0.0),
            'segmentation_mask': segmentation_mask
        }

    def _rule_engine_analysis(self, frame: np.ndarray,
                               timestamp: datetime) -> dict:
        """规则引擎降级分析"""
        # 基于颜色直方图判断水体浑浊度
        avg_brightness = np.mean(frame)
        turbidity_level = 0
        if avg_brightness < 0.2:
            turbidity_level = 3
        elif avg_brightness < 0.4:
            turbidity_level = 2
        elif avg_brightness < 0.6:
            turbidity_level = 1

        return {
            'point_id': self.point_id,
            'timestamp': timestamp,
            'turbidity_level': turbidity_level,
            'pollution_types': [],
            'pollution_probs': [],
            'confidence': 0.5,
            'segmentation_mask': np.zeros((224, 224), dtype=np.uint8)
        }

    def _determine_sensor_alert(self, value: float,
                                  thresholds: list) -> int:
        """根据阈值确定传感器异常等级"""
        for i, threshold in enumerate(thresholds):
            if value <= threshold:
                return i
        return len(thresholds)

    def _start_model_reload_monitor(self) -> None:
        """启动模型重新加载监控线程（每5分钟尝试一次）"""
        def _reload_monitor():
            while self._inference_mode == "rule_engine":
                time.sleep(self._model_reload_interval)
                try:
                    logger.info("尝试重新加载深度学习模型...")
                    self._inference_mode = "deep_learning"
                    self._rule_engine_failures = 0
                    logger.info("模型重载成功，已切回深度学习模式")
                    break
                except Exception:
                    logger.warning("模型重载失败，继续使用规则引擎")
        threading.Thread(target=_reload_monitor, daemon=True).start()

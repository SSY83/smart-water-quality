"""视频流采集子模块 - OpenCV"""
import logging
import threading
import time
from collections import deque
from datetime import datetime
from typing import Optional

import cv2
import numpy as np

from .exceptions import CameraOfflineError, InvalidFrameError

logger = logging.getLogger(__name__)


class VideoCapture:
    """视频流采集子模块 - 异步生产者-消费者模式"""

    SUPPORTED_FORMATS = {'.jpg', '.jpeg', '.png'}

    def __init__(self, source: int = 0, fps: int = 20,
                 resolution: tuple = (640, 480), buffer_size: int = 30,
                 input_size: tuple = (224, 224)):
        self.source = source
        self.fps = fps
        self.resolution = resolution
        self.input_size = input_size
        self.buffer: deque = deque(maxlen=buffer_size)
        self.cap: Optional[cv2.VideoCapture] = None
        self._running = False
        self._capture_thread: Optional[threading.Thread] = None
        self._frame_interval = 1.0 / fps
        self._consecutive_failures = 0
        self._max_failures = 5
        self.point_id: str = "unknown"

    def start_capture(self) -> None:
        """启动摄像头采集"""
        self.cap = cv2.VideoCapture(self.source)
        if not self.cap.isOpened():
            raise CameraOfflineError(f"无法打开摄像头: {self.source}")

        self.cap.set(cv2.CAP_PROP_FRAME_WIDTH, self.resolution[0])
        self.cap.set(cv2.CAP_PROP_FRAME_HEIGHT, self.resolution[1])
        self.cap.set(cv2.CAP_PROP_FPS, self.fps)

        self._running = True
        self._capture_thread = threading.Thread(target=self._capture_loop, daemon=True)
        self._capture_thread.start()
        logger.info("视频采集已启动: source=%d, fps=%d, resolution=%s",
                    self.source, self.fps, self.resolution)

    def stop_capture(self) -> None:
        """停止摄像头采集"""
        self._running = False
        if self._capture_thread:
            self._capture_thread.join(timeout=5.0)
        if self.cap:
            self.cap.release()
        logger.info("视频采集已停止")

    def get_frame(self) -> Optional[dict]:
        """从缓冲区获取一帧"""
        try:
            return self.buffer.popleft()
        except IndexError:
            return None

    def _capture_loop(self) -> None:
        """采集主循环"""
        while self._running:
            try:
                ret, frame = self.cap.read()
                if not ret:
                    self._consecutive_failures += 1
                    logger.warning("读取帧失败 (%d/%d)",
                                  self._consecutive_failures, self._max_failures)
                    if self._consecutive_failures >= self._max_failures:
                        raise CameraOfflineError("连续读取帧失败")
                    time.sleep(0.1)
                    continue

                self._consecutive_failures = 0
                processed = self._preprocess(frame)
                timestamp = datetime.now()

                self.buffer.append({
                    'frame': processed,
                    'timestamp': timestamp,
                    'point_id': self.point_id
                })

                time.sleep(self._frame_interval)
            except CameraOfflineError:
                logger.error("摄像头采集异常，停止采集")
                self._running = False
                break
            except Exception as e:
                logger.error("采集异常: %s", e)

    def _preprocess(self, frame: np.ndarray) -> np.ndarray:
        """图像预处理: 缩放+归一化+RGB转换"""
        if frame is None or frame.size == 0:
            raise InvalidFrameError()

        # 缩放至224x224
        resized = cv2.resize(frame, self.input_size)

        # BGR转RGB
        rgb = cv2.cvtColor(resized, cv2.COLOR_BGR2RGB)

        # 归一化到[-1, 1]
        normalized = (rgb.astype(np.float32) / 127.5) - 1.0

        return normalized

    @property
    def is_running(self) -> bool:
        return self._running and self.cap is not None and self.cap.isOpened()

    @property
    def buffer_size(self) -> int:
        return len(self.buffer)

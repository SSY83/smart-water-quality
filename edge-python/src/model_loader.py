"""TensorFlow Lite模型加载器 - 单例模式"""
import logging
import threading
import time
import numpy as np

from .exceptions import InferenceFailedError

logger = logging.getLogger(__name__)


class ModelLoader:
    """TensorFlow Lite模型加载器（单例）"""
    _instance = None
    _lock = threading.Lock()

    def __new__(cls, *args, **kwargs):
        if cls._instance is None:
            with cls._lock:
                if cls._instance is None:
                    cls._instance = super().__new__(cls)
        return cls._instance

    def __init__(self):
        if hasattr(self, '_initialized') and self._initialized:
            return
        self._initialized = True
        self.mobilenet_path: str = ""
        self.unet_path: str = ""
        self.mobilenet_interpreter = None
        self.unet_interpreter = None
        self.input_details_mobilenet = None
        self.output_details_mobilenet = None
        self.input_details_unet = None
        self.output_details_unet = None
        self.quantized: bool = False
        self.model_loaded: bool = False

    def load_model(self, mobilenet_path: str, unet_path: str, num_threads: int = 4):
        """加载TensorFlow Lite量化模型"""
        try:
            import tflite_runtime.interpreter as tflite
        except ImportError:
            logger.warning("tflite_runtime未安装，使用全量TensorFlow")
            import tensorflow as tf
            tflite = tf.lite

        self.mobilenet_path = mobilenet_path
        self.unet_path = unet_path

        # 加载MobileNetV2分类模型
        self.mobilenet_interpreter = tflite.Interpreter(
            model_path=mobilenet_path,
            num_threads=num_threads
        )
        self.mobilenet_interpreter.allocate_tensors()
        self.input_details_mobilenet = self.mobilenet_interpreter.get_input_details()
        self.output_details_mobilenet = self.mobilenet_interpreter.get_output_details()

        # 加载U-Net分割模型
        self.unet_interpreter = tflite.Interpreter(
            model_path=unet_path,
            num_threads=num_threads
        )
        self.unet_interpreter.allocate_tensors()
        self.input_details_unet = self.unet_interpreter.get_input_details()
        self.output_details_unet = self.unet_interpreter.get_output_details()

        self.quantized = self.input_details_mobilenet[0]['dtype'] == np.uint8
        self.model_loaded = True
        logger.info("模型加载完成: mobilenet=%s (量化=%s), unet=%s",
                    mobilenet_path, self.quantized, unet_path)

    def quantize_input(self, image_array: np.ndarray) -> np.ndarray:
        """将浮点输入转换为量化格式"""
        if not self.quantized:
            return image_array
        input_details = self.input_details_mobilenet[0]
        scale, zero_point = input_details['quantization']
        if scale == 0.0:
            return image_array.astype(np.uint8)
        return (image_array / scale + zero_point).astype(np.uint8)

    def run_mobilenet_inference(self, preprocessed_image: np.ndarray) -> dict:
        """执行MobileNetV2推理

        输入: [1, 224, 224, 3] 归一化到[-1, 1]
        输出: {
            'turbidity_level': int (0-清晰,1-轻度,2-中度,3-重度),
            'pollution_probs': [float, float, float, float],
            'confidence': float
        }
        """
        if not self.model_loaded:
            raise InferenceFailedError("模型未加载")

        try:
            input_data = preprocessed_image.astype(np.float32)
            if len(input_data.shape) == 3:
                input_data = np.expand_dims(input_data, axis=0)

            if self.quantized:
                input_data = self.quantize_input(input_data)

            self.mobilenet_interpreter.set_tensor(
                self.input_details_mobilenet[0]['index'], input_data)
            self.mobilenet_interpreter.invoke()

            output = self.mobilenet_interpreter.get_tensor(
                self.output_details_mobilenet[0]['index'])[0]

            # 输出: [清晰, 轻度浑浊, 中度浑浊, 重度浑浊, 有机污染物]
            turbidity_level = int(np.argmax(output[:4]))
            confidence = float(np.max(output[:4]))
            pollution_probs = output[4:8].tolist() if len(output) >= 8 else [0.0, 0.0, 0.0, 0.0]

            return {
                'turbidity_level': turbidity_level,
                'pollution_probs': pollution_probs,
                'confidence': confidence
            }
        except Exception as e:
            raise InferenceFailedError(f"MobileNetV2推理失败: {e}")

    def run_unet_inference(self, preprocessed_image: np.ndarray) -> np.ndarray:
        """执行U-Net分割推理

        输入: [1, 224, 224, 3]
        输出: [224, 224] 分割掩码
        """
        if not self.model_loaded:
            raise InferenceFailedError("模型未加载")

        try:
            input_data = preprocessed_image.astype(np.float32)
            if len(input_data.shape) == 3:
                input_data = np.expand_dims(input_data, axis=0)

            self.unet_interpreter.set_tensor(
                self.input_details_unet[0]['index'], input_data)
            self.unet_interpreter.invoke()

            output = self.unet_interpreter.get_tensor(
                self.output_details_unet[0]['index'])[0]
            # 取argmax得到分割掩码
            mask = np.argmax(output, axis=-1).astype(np.uint8)
            return mask
        except Exception as e:
            raise InferenceFailedError(f"U-Net推理失败: {e}")

    def unload_model(self):
        """卸载模型释放内存"""
        self.mobilenet_interpreter = None
        self.unet_interpreter = None
        self.model_loaded = False
        logger.info("模型已卸载")


def get_model_loader() -> ModelLoader:
    """获取ModelLoader单例"""
    return ModelLoader()

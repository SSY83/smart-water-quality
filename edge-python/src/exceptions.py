"""边缘端异常定义"""


class EdgeException(Exception):
    """边缘端基础异常"""
    def __init__(self, message: str, error_code: str = "3000"):
        super().__init__(message)
        self.error_code = error_code


class CameraOfflineError(EdgeException):
    """摄像头离线异常"""
    def __init__(self, message: str = "摄像头离线"):
        super().__init__(message, "3001")


class SensorTimeoutError(EdgeException):
    """传感器读取超时异常"""
    def __init__(self, sensor_type: str = ""):
        super().__init__(f"传感器读取超时: {sensor_type}", "3002")


class StorageFullError(EdgeException):
    """存储空间不足异常"""
    def __init__(self, used_mb: int = 0, max_mb: int = 0):
        super().__init__(f"存储空间不足: {used_mb}/{max_mb}MB", "3003")


class NetworkDisconnectedError(EdgeException):
    """网络连接断开异常"""
    def __init__(self):
        super().__init__("网络连接断开", "3004")


class InferenceFailedError(EdgeException):
    """模型推理失败异常"""
    def __init__(self, cause: str = ""):
        super().__init__(f"模型推理失败: {cause}", "2001")


class InvalidFrameError(EdgeException):
    """无效图像帧异常"""
    def __init__(self):
        super().__init__("图像帧格式无法解析", "3001")

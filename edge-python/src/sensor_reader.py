"""传感器数据采集子模块 - Modbus RTU协议"""
import logging
import threading
import time
from collections import deque
from datetime import datetime
from typing import Optional

from .exceptions import SensorTimeoutError

logger = logging.getLogger(__name__)


class SensorReader:
    """传感器数据采集 - 通过Modbus RTU轮询pH/浊度/COD传感器"""

    # 传感器合理范围
    VALID_RANGES = {
        'ph': (0.0, 14.0),
        'turbidity': (0.0, 1000.0),
        'cod': (0.0, 100.0)
    }

    def __init__(self, sensor_configs: list, read_interval: float = 1.0):
        self.sensor_configs = sensor_configs
        self.read_interval = read_interval
        self.latest_readings: dict = {}
        self._running = False
        self._read_thread: Optional[threading.Thread] = None
        self._last_valid: dict = {}  # 保存最近一次有效值
        self._consecutive_invalid: dict = {}  # 连续无效计数
        self._max_invalid = 10  # 连续无效超过此值触发告警

        # Modbus模拟模式（当无实际硬件时）
        self._simulation_mode = True

    def connect_all(self) -> bool:
        """连接所有传感器"""
        for config in self.sensor_configs:
            sensor_type = config.get('type', 'unknown')
            try:
                if not self._simulation_mode:
                    self._connect_sensor(config)
                logger.info("传感器已就绪: type=%s", sensor_type)
            except Exception as e:
                logger.error("传感器连接失败: type=%s, error=%s", sensor_type, e)
        return True

    def start_reading(self) -> None:
        """启动传感器轮询"""
        self._running = True
        self._read_thread = threading.Thread(target=self._read_loop, daemon=True)
        self._read_thread.start()
        logger.info("传感器数据采集已启动: interval=%.1fs", self.read_interval)

    def stop_reading(self) -> None:
        """停止传感器轮询"""
        self._running = False
        if self._read_thread:
            self._read_thread.join(timeout=5.0)

    def get_latest_readings(self) -> dict:
        """获取最新的传感器读数"""
        return dict(self.latest_readings)

    def _read_loop(self) -> None:
        """轮询主循环"""
        while self._running:
            timestamp = datetime.now()
            readings = {}

            for config in self.sensor_configs:
                sensor_type = config.get('type', 'unknown')
                try:
                    if self._simulation_mode:
                        value = self._simulate_reading(sensor_type)
                    else:
                        value = self._read_sensor(config)

                    # 范围校验
                    valid_range = self.VALID_RANGES.get(sensor_type, (float('-inf'), float('inf')))
                    if valid_range[0] <= value <= valid_range[1]:
                        readings[sensor_type] = {'value': value, 'timestamp': timestamp}
                        self._last_valid[sensor_type] = value
                        self._consecutive_invalid[sensor_type] = 0
                    else:
                        # 使用最近一次有效值代替
                        logger.warning("传感器读数超出范围: type=%s, value=%.2f", sensor_type, value)
                        self._consecutive_invalid[sensor_type] = \
                            self._consecutive_invalid.get(sensor_type, 0) + 1
                        if self._consecutive_invalid.get(sensor_type, 0) >= self._max_invalid:
                            logger.error("传感器连续无效，触发故障告警: type=%s", sensor_type)

                        if sensor_type in self._last_valid:
                            readings[sensor_type] = {
                                'value': self._last_valid[sensor_type],
                                'timestamp': timestamp,
                                'invalid': True
                            }

                except SensorTimeoutError as e:
                    logger.error("传感器超时: %s", e)
                    self._consecutive_invalid[sensor_type] = \
                        self._consecutive_invalid.get(sensor_type, 0) + 1
                except Exception as e:
                    logger.error("传感器读取异常: type=%s, error=%s", sensor_type, e)

            self.latest_readings = readings
            time.sleep(self.read_interval)

    def _simulate_reading(self, sensor_type: str) -> float:
        """模拟传感器读数（开发测试用）"""
        import random
        import math
        base = {
            'ph': 7.0,
            'turbidity': 10.0,
            'cod': 15.0
        }.get(sensor_type, 0.0)
        noise = math.sin(time.time()) * base * 0.1 + random.gauss(0, base * 0.05)
        return max(0, base + noise)

    def _connect_sensor(self, config: dict) -> None:
        """通过Modbus RTU连接传感器（需实际硬件）"""
        try:
            import minimalmodbus
            instrument = minimalmodbus.Instrument(
                config.get('port', '/dev/ttyUSB0'),
                config.get('slave_address', 1)
            )
            instrument.serial.baudrate = config.get('baudrate', 9600)
            instrument.serial.timeout = config.get('timeout', 2.0)
            config['_instrument'] = instrument
        except ImportError:
            logger.warning("minimalmodbus未安装，使用模拟模式")

    def _read_sensor(self, config: dict) -> float:
        """从Modbus读取传感器数值"""
        instrument = config.get('_instrument')
        if instrument is None:
            return self._simulate_reading(config.get('type', 'unknown'))

        try:
            # 读取保持寄存器 (地址0, 2个寄存器存储float)
            value = instrument.read_float(0, 3, 2)
            return value
        except Exception as e:
            raise SensorTimeoutError(config.get('type', ''))

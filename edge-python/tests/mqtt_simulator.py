"""
MQTT边缘设备模拟器 — 用于端到端通信联调测试

模拟边缘设备行为：
1. 定期发送传感器数据 (sensor_data)
2. 定期发送影像分析结果 (image_analysis)
3. 发送心跳包 (heartbeat)
4. 支持多种告警等级场景测试

使用方式:
    python mqtt_simulator.py --broker localhost --port 1883 --point-id 1
    python mqtt_simulator.py --scenario normal   # 正常水质
    python mqtt_simulator.py --scenario mild     # 轻度异常
    python mqtt_simulator.py --scenario severe   # 重度异常
"""
import argparse
import json
import logging
import random
import time
from datetime import datetime
from typing import Optional

logging.basicConfig(level=logging.INFO,
                    format='%(asctime)s [%(levelname)s] %(message)s')
logger = logging.getLogger(__name__)

try:
    import paho.mqtt.client as mqtt
    HAS_MQTT = True
except ImportError:
    logger.warning("paho-mqtt 未安装，使用打印模式。安装: pip install paho-mqtt")
    HAS_MQTT = False


SCENARIOS = {
    'normal': {
        'turbidity_range': (0, 5),
        'cod_range': (0, 15),
        'ph_range': (6.8, 7.2),
        'turbidity_level': 0,
        'alert_level': 0,
        'confidence': (0.85, 0.98)
    },
    'mild': {
        'turbidity_range': (5, 30),
        'cod_range': (15, 30),
        'ph_range': (6.5, 8.5),
        'turbidity_level': 1,
        'alert_level': 1,
        'confidence': (0.7, 0.85)
    },
    'moderate': {
        'turbidity_range': (30, 80),
        'cod_range': (30, 50),
        'ph_range': (5.5, 9.5),
        'turbidity_level': 2,
        'alert_level': 2,
        'confidence': (0.6, 0.75)
    },
    'severe': {
        'turbidity_range': (80, 200),
        'cod_range': (50, 100),
        'ph_range': (4.0, 10.0),
        'turbidity_level': 3,
        'alert_level': 3,
        'confidence': (0.5, 0.7)
    }
}


class EdgeSimulator:
    """边缘设备模拟器"""

    def __init__(self, broker: str = "localhost", port: int = 1883,
                 point_id: str = "1", scenario: str = "normal",
                 interval: float = 5.0, qos: int = 1):
        self.broker = broker
        self.port = port
        self.point_id = point_id
        self.scenario = scenario
        self.interval = interval
        self.qos = qos
        self.client_id = f"sim-{point_id}-{random.randint(1000, 9999)}"
        self.client: Optional[mqtt.Client] = None
        self.running = False

        # 主题
        self.sensor_topic = f"/wqi/{point_id}/sensor_data"
        self.image_topic = f"/wqi/{point_id}/image_analysis"
        self.heartbeat_topic = f"/wqi/{point_id}/heartbeat"

        # 计数
        self.send_count = 0

    def connect(self) -> bool:
        if not HAS_MQTT:
            logger.info("模拟模式：消息将打印到控制台")
            return True

        self.client = mqtt.Client(client_id=self.client_id)
        self.client.on_connect = self._on_connect
        self.client.on_disconnect = self._on_disconnect

        try:
            self.client.connect(self.broker, self.port, keepalive=30)
            self.client.loop_start()
            time.sleep(1)
            return True
        except Exception as e:
            logger.error("MQTT连接失败: %s", e)
            return False

    def disconnect(self):
        self.running = False
        if self.client:
            self.client.loop_stop()
            self.client.disconnect()

    def run(self, count: int = 0):
        """
        Args:
            count: 发送次数，0=无限循环
        """
        self.running = True
        iteration = 0

        logger.info("=" * 60)
        logger.info("边缘模拟器启动")
        logger.info("  场景: %s", self.scenario.upper())
        logger.info("  监测点: %s", self.point_id)
        logger.info("  传感器主题: %s", self.sensor_topic)
        logger.info("  影像主题: %s", self.image_topic)
        logger.info("=" * 60)

        while self.running:
            if count > 0 and iteration >= count:
                break
            iteration += 1

            try:
                # 1. 发送传感器数据
                sensor_data = self._generate_sensor_data()
                self._publish(self.sensor_topic, sensor_data)
                logger.info("[%d] 传感器数据 → pH=%.2f 浊度=%.1f COD=%.1f",
                           iteration, sensor_data['data']['ph'],
                           sensor_data['data']['turbidity'],
                           sensor_data['data']['cod'])

                # 2. 发送影像分析
                image_data = self._generate_image_analysis()
                self._publish(self.image_topic, image_data)
                logger.info("[%d] 影像分析 → 浑浊等级=%d 置信度=%.2f",
                           iteration, image_data['turbidityLevel'],
                           image_data['confidence'])

                # 3. 每5次发送一个心跳
                if iteration % 5 == 0:
                    heartbeat = self._generate_heartbeat()
                    self._publish(self.heartbeat_topic, heartbeat)
                    logger.info("[%d] 心跳 → 缓存队列=%d",
                               iteration, heartbeat['cache_queue_length'])

                self.send_count += 2

            except Exception as e:
                logger.error("发送失败: %s", e)

            time.sleep(self.interval)

        logger.info("模拟器停止，共发送 %d 条消息", self.send_count)

    def _generate_sensor_data(self) -> dict:
        cfg = SCENARIOS[self.scenario]
        return {
            'device_id': self.client_id,
            'timestamp': datetime.now().isoformat(),
            'data': {
                'turbidity': round(random.uniform(*cfg['turbidity_range']), 1),
                'cod': round(random.uniform(*cfg['cod_range']), 1),
                'ph': round(random.uniform(*cfg['ph_range']), 2)
            }
        }

    def _generate_image_analysis(self) -> dict:
        cfg = SCENARIOS[self.scenario]
        return {
            'device_id': self.client_id,
            'timestamp': datetime.now().isoformat(),
            'turbidityLevel': cfg['turbidity_level'],
            'alertLevel': cfg['alert_level'],
            'confidence': round(random.uniform(*cfg['confidence']), 3),
            'finalScore': round(random.uniform(0.0, 1.0), 3),
            'imageScore': round(random.uniform(0.0, 1.0), 3),
            'pollutionTypes': self._pick_pollution_type(),
            'details': {
                'turbidity': round(random.uniform(*cfg['turbidity_range']), 1),
                'cod': round(random.uniform(*cfg['cod_range']), 1),
                'ph': round(random.uniform(*cfg['ph_range']), 2),
                'model_version': 'mobilenetv2-v1.0'
            }
        }

    def _generate_heartbeat(self) -> dict:
        return {
            'device_id': self.client_id,
            'timestamp': datetime.now().isoformat(),
            'cache_queue_length': random.randint(0, 50)
        }

    def _publish(self, topic: str, payload: dict) -> bool:
        json_str = json.dumps(payload, default=str)
        if self.client and HAS_MQTT:
            result = self.client.publish(topic, json_str, qos=self.qos)
            return result.rc == mqtt.MQTT_ERR_SUCCESS
        else:
            # 模拟模式
            logger.debug("  [SIM] %s ← %s", topic, json_str[:100])
            return True

    def _pick_pollution_type(self) -> str:
        cfg = SCENARIOS[self.scenario]
        if cfg['alert_level'] == 0:
            return 'normal'
        types = []
        if cfg['turbidity_range'][0] > 5:
            types.append('turbidity')
        if cfg['cod_range'][0] > 15:
            types.append('cod')
        if cfg['ph_range'][0] < 6.0 or cfg['ph_range'][1] > 9.0:
            types.append('ph')
        return ','.join(types) if types else 'normal'

    def _on_connect(self, client, userdata, flags, rc):
        if rc == 0:
            logger.info("MQTT Broker连接成功: %s:%d", self.broker, self.port)
        else:
            logger.error("MQTT连接失败: rc=%d", rc)

    def _on_disconnect(self, client, userdata, rc):
        if rc != 0:
            logger.warning("MQTT意外断开: rc=%d", rc)


def main():
    parser = argparse.ArgumentParser(description='边缘设备MQTT模拟器')
    parser.add_argument('--broker', default='localhost', help='MQTT Broker地址')
    parser.add_argument('--port', type=int, default=1883, help='MQTT端口')
    parser.add_argument('--point-id', default='1', help='监测点ID')
    parser.add_argument('--scenario', choices=['normal', 'mild', 'moderate', 'severe'],
                       default='normal', help='测试场景')
    parser.add_argument('--interval', type=float, default=5.0, help='发送间隔(秒)')
    parser.add_argument('--count', type=int, default=0, help='发送次数(0=无限)')
    parser.add_argument('--qos', type=int, default=1, help='MQTT QoS')
    args = parser.parse_args()

    sim = EdgeSimulator(
        broker=args.broker,
        port=args.port,
        point_id=args.point_id,
        scenario=args.scenario,
        interval=args.interval,
        qos=args.qos
    )

    if not sim.connect():
        logger.error("无法连接到MQTT Broker，请确保Mosquitto已启动")
        return

    try:
        sim.run(count=args.count)
    except KeyboardInterrupt:
        logger.info("收到中断信号")
    finally:
        sim.disconnect()


if __name__ == '__main__':
    main()

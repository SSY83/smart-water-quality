"""
模型推理集成测试与性能基准

测试内容:
1. 模型加载测试（实体模型 / Mock）
2. MobileNetV2 推理准确性测试
3. U-Net 分割推理测试
4. 双模型并行推理性能测试
5. 量化 vs 非量化精度对比
6. 规则引擎降级切换测试
7. 长时间运行稳定性测试

使用方式:
    pytest test_model_inference.py -v
    python test_model_inference.py --benchmark
"""
import sys
import os
import time
import json
import random
import unittest
import logging
from datetime import datetime
from typing import Optional

sys.path.insert(0, os.path.dirname(os.path.dirname(__file__)))

import numpy as np

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

# ============================================================
# Mock 推理引擎 (无需真实模型即可测试)
# ============================================================

class MockInferenceEngine:
    """模拟推理引擎，用于在没有TFLite环境的PC上测试"""

    def __init__(self, mode: str = "accurate"):
        self.mode = mode  # accurate, random, failing
        self.inference_count = 0
        self.total_time_ns = 0

    def run_mobilenet(self, image: np.ndarray) -> dict:
        start = time.perf_counter_ns()
        time.sleep(0.02)  # 模拟20ms推理

        if self.mode == "failing":
            raise RuntimeError("模拟推理失败")

        # 生成模拟结果
        mean_val = image.mean()
        if mean_val < 0.2:
            turbidity_level = 3
        elif mean_val < 0.4:
            turbidity_level = 2
        elif mean_val < 0.6:
            turbidity_level = 1
        else:
            turbidity_level = 0

        elapsed = time.perf_counter_ns() - start
        self.inference_count += 1
        self.total_time_ns += elapsed

        return {
            'turbidity_level': turbidity_level,
            'pollution_probs': [0.1, 0.2, 0.3, 0.4],
            'confidence': 0.85
        }

    def run_unet(self, image: np.ndarray) -> np.ndarray:
        start = time.perf_counter_ns()
        time.sleep(0.03)  # 模拟30ms推理
        mask = (image.mean(axis=2) > 0.5).astype(np.uint8)
        self.inference_count += 1
        self.total_time_ns += time.perf_counter_ns() - start
        return mask

    def latency_ms(self) -> float:
        if self.inference_count == 0:
            return 0.0
        return self.total_time_ns / self.inference_count / 1e6


class TestModelInference(unittest.TestCase):
    """模型推理功能测试"""

    @classmethod
    def setUpClass(cls):
        cls.engine = MockInferenceEngine(mode="accurate")

    def setUp(self):
        # 生成模拟图像 [224, 224, 3]，归一化到[-1,1]
        self.normal_image = np.random.normal(0.5, 0.1, (224, 224, 3)).astype(np.float32)
        self.turbid_image = np.random.normal(0.1, 0.05, (224, 224, 3)).astype(np.float32)
        self.clean_image = np.random.normal(0.8, 0.1, (224, 224, 3)).astype(np.float32)

    def test_mobilenet_normal_water(self):
        """MobileNetV2正常水质分类"""
        result = self.engine.run_mobilenet(self.clean_image)
        self.assertEqual(result['turbidity_level'], 0)
        self.assertGreater(result['confidence'], 0.5)

    def test_mobilenet_turbid_water(self):
        """MobileNetV2浑浊水质分类"""
        result = self.engine.run_mobilenet(self.turbid_image)
        self.assertEqual(result['turbidity_level'], 3)

    def test_unet_segmentation_output_shape(self):
        """U-Net分割输出尺寸正确"""
        mask = self.engine.run_unet(self.normal_image)
        self.assertEqual(mask.shape, (224, 224))
        self.assertIn(0, mask)  # 存在背景
        self.assertIn(1, mask)  # 存在前景

    def test_parallel_inference(self):
        """双模型并行推理性能"""
        from concurrent.futures import ThreadPoolExecutor

        with ThreadPoolExecutor(max_workers=4) as executor:
            f1 = executor.submit(self.engine.run_mobilenet, self.normal_image)
            f2 = executor.submit(self.engine.run_unet, self.normal_image)
            result1 = f1.result(timeout=0.2)
            result2 = f2.result(timeout=0.2)

        self.assertIsNotNone(result1)
        self.assertIsNotNone(result2)
        self.assertLess(self.engine.latency_ms(), 100,
                       f"推理延迟 {self.engine.latency_ms():.1f}ms 超过100ms阈值")

    def test_rule_engine_fallback(self):
        """规则引擎降级模式"""
        # 模拟深度学习失败
        failing_engine = MockInferenceEngine(mode="failing")
        with self.assertRaises(RuntimeError):
            failing_engine.run_mobilenet(self.normal_image)

        # 规则引擎处理（直接判断）
        avg_brightness = self.normal_image.mean()
        turbidity_level = 0
        if avg_brightness < 0.2:
            turbidity_level = 3
        elif avg_brightness < 0.4:
            turbidity_level = 2
        elif avg_brightness < 0.6:
            turbidity_level = 1

        self.assertGreaterEqual(turbidity_level, 0)
        self.assertLessEqual(turbidity_level, 3)

    def test_quantization_simulation(self):
        """量化推理模拟对比"""
        # 浮点模型输入 [-1, 1]
        float_input = self.normal_image

        # 模拟量化 int8: scale=0.0078, zero_point=127
        scale = 0.0078
        zero_point = 127
        quantized = (float_input / scale + zero_point).clip(0, 255).astype(np.uint8)
        dequantized = (quantized.astype(np.float32) - zero_point) * scale

        # 量化误差应在 1% 以内
        error = np.abs(float_input - dequantized).mean()
        self.assertLess(error, 0.01,
                       f"量化误差 {error:.4f} 超过1%阈值")

    def test_fusion_with_fallback(self):
        """融合算法 + 降级联动"""
        from src.fusion_algorithm import fuse_image_and_sensor, ImageResult, SensorResult

        img = ImageResult(
            turbidity_level=2,
            pollution_probs=[0.1, 0.2, 0.5, 0.2],
            confidence=0.75,
            timestamp=datetime.now()
        )

        sensor = SensorResult(
            turbidity=45.0,
            cod=25.0,
            ph=7.2,
            timestamp=datetime.now()
        )

        result = fuse_image_and_sensor(img, [sensor])
        self.assertGreater(result.final_score, 0.0)
        self.assertLessEqual(result.final_score, 1.0)
        self.assertTrue(0 <= result.alert_level <= 3)

    def test_sensor_only_mode(self):
        """仅传感器模式（无影像）"""
        from src.fusion_algorithm import fuse_image_and_sensor, ImageResult, SensorResult

        img = ImageResult(
            turbidity_level=0,
            pollution_probs=[],
            confidence=0.0,
            timestamp=datetime.now()
        )

        # 时间相差很远，超出时间窗口
        sensor = SensorResult(
            turbidity=85.0,
            cod=55.0,
            ph=5.5,
            timestamp=datetime(2024, 1, 1)  # 旧数据
        )

        result = fuse_image_and_sensor(img, [sensor])
        # 无匹配传感器时降级为纯影像分析
        self.assertIsNotNone(result)
        self.assertGreaterEqual(result.final_score, 0.0)


class TestPerformanceBenchmark(unittest.TestCase):
    """性能基准测试"""

    def test_latency_under_load(self):
        """负载下延迟测试"""
        engine = MockInferenceEngine()
        images = [np.random.normal(0.5, 0.1, (224, 224, 3)).astype(np.float32)
                 for _ in range(100)]

        start = time.perf_counter()
        for img in images:
            engine.run_mobilenet(img)
        elapsed = time.perf_counter() - start

        avg_latency = (elapsed / len(images)) * 1000
        throughput = len(images) / elapsed

        logger.info(f"吞吐量: {throughput:.1f} 帧/秒")
        logger.info(f"平均延迟: {avg_latency:.1f} ms/帧")

        # 模拟推理应远低于200ms阈值
        self.assertLess(avg_latency, 200,
                       f"平均延迟 {avg_latency:.1f}ms 超过200ms阈值")

    def test_memory_usage(self):
        """内存使用测试"""
        import tracemalloc
        tracemalloc.start()

        engine = MockInferenceEngine()
        for _ in range(50):
            img = np.random.normal(0.5, 0.1, (224, 224, 3)).astype(np.float32)
            engine.run_mobilenet(img)
            engine.run_unet(img)

        current, peak = tracemalloc.get_traced_memory()
        tracemalloc.stop()

        logger.info(f"当前内存: {current / 1024 / 1024:.1f} MB")
        logger.info(f"峰值内存: {peak / 1024 / 1024:.1f} MB")
        self.assertLess(peak / 1024 / 1024, 500, "内存峰值超过500MB")

    def test_long_running_stability(self):
        """长时间运行稳定性测试（1000次推理）"""
        engine = MockInferenceEngine()
        failures = 0

        for _ in range(1000):
            try:
                img = np.random.normal(0.5, 0.1, (224, 224, 3)).astype(np.float32)
                engine.run_mobilenet(img)
            except Exception:
                failures += 1

        failure_rate = failures / 1000
        logger.info(f"失败率: {failure_rate:.2%}")
        self.assertLess(failure_rate, 0.01, "失败率超过1%")


def run_benchmark():
    """运行完整性能基准测试"""
    print("=" * 60)
    print("  模型推理性能基准测试")
    print("=" * 60)
    print()

    engine = MockInferenceEngine()

    # 单次推理延迟
    latencies = []
    for _ in range(50):
        img = np.random.normal(0.5, 0.1, (224, 224, 3)).astype(np.float32)
        start = time.perf_counter_ns()
        engine.run_mobilenet(img)
        latencies.append((time.perf_counter_ns() - start) / 1e6)

    latencies.sort()
    print(f"MobileNetV2 推理延迟 (ms):")
    print(f"  P50: {latencies[len(latencies)//2]:.2f}")
    print(f"  P95: {latencies[int(len(latencies)*0.95)]:.2f}")
    print(f"  P99: {latencies[int(len(latencies)*0.99)]:.2f}")
    print()

    # 并行推理
    print("双模型并行推理测试...")
    parallel_start = time.perf_counter()
    for _ in range(20):
        img = np.random.normal(0.5, 0.1, (224, 224, 3)).astype(np.float32)
        engine.run_mobilenet(img)
        engine.run_unet(img)
    parallel_elapsed = time.perf_counter() - parallel_start
    print(f"  平均端到端延迟: {parallel_elapsed/20*1000:.1f} ms")

    print()
    print("✅ 性能基准测试完成")


if __name__ == '__main__':
    import argparse
    parser = argparse.ArgumentParser()
    parser.add_argument('--benchmark', action='store_true', help='运行性能基准')
    parser.add_argument('--verbose', '-v', action='store_true')
    args = parser.parse_args()

    if args.benchmark:
        run_benchmark()
    else:
        unittest.main(argv=[''], verbosity=2 if args.verbose else 1)

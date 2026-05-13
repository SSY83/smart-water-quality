"""单元测试 - 多源数据融合算法"""
import sys
import os
sys.path.insert(0, os.path.join(os.path.dirname(__file__), '..'))

from datetime import datetime
from src.fusion_algorithm import (
    fuse_image_and_sensor,
    ImageResult, SensorResult, FusionResult,
    rule_engine_analysis
)


def test_normal_fusion():
    """测试正常融合场景"""
    image = ImageResult(
        turbidity_level=1,
        pollution_probs=[0.1, 0.2, 0.5, 0.1],
        confidence=0.85,
        timestamp=datetime.now()
    )
    sensor = SensorResult(
        turbidity=10.0,
        cod=20.0,
        ph=7.2,
        timestamp=datetime.now()
    )

    result = fuse_image_and_sensor(image, [sensor])
    assert 0 <= result.final_score <= 1.0
    assert 0 <= result.confidence <= 1.0
    assert result.alert_level in [0, 1, 2, 3]
    print(f"[PASS] 正常融合: score={result.final_score:.3f}, "
          f"confidence={result.confidence:.3f}, alert={result.alert_level}")


def test_sensor_missing():
    """测试传感器数据缺失场景"""
    image = ImageResult(
        turbidity_level=2,
        pollution_probs=[0.1, 0.1, 0.1, 0.3],
        confidence=0.75,
        timestamp=datetime.now()
    )

    # 无传感器数据
    result = fuse_image_and_sensor(image, [])
    assert result.sensor_score == 0.0
    assert result.alert_level >= 0
    print(f"[PASS] 传感器缺失: score={result.final_score:.3f}, "
          f"confidence={result.confidence:.3f}")


def test_sensor_expired():
    """测试传感器数据过期场景（时间窗口外）"""
    from datetime import timedelta
    image = ImageResult(
        turbidity_level=0,
        pollution_probs=[0.05, 0.05, 0.05, 0.05],
        confidence=0.9,
        timestamp=datetime.now()
    )
    sensor = SensorResult(
        turbidity=50.0,
        cod=40.0,
        ph=6.0,
        timestamp=datetime.now() - timedelta(seconds=10)
    )

    # 传感器数据超出500ms时间窗口
    result = fuse_image_and_sensor(image, [sensor], time_window_ms=500.0)
    # 超出时间窗口应该降级为纯影像
    print(f"[PASS] 传感器过期: score={result.final_score:.3f}, "
          f"confidence={result.confidence:.3f}")


def test_severe_alert():
    """测试重度异常场景"""
    image = ImageResult(
        turbidity_level=3,
        pollution_probs=[0.8, 0.1, 0.05, 0.05],
        confidence=0.95,
        timestamp=datetime.now()
    )
    sensor = SensorResult(
        turbidity=85.0,
        cod=55.0,
        ph=9.0,
        timestamp=datetime.now()
    )

    result = fuse_image_and_sensor(image, [sensor])
    assert result.alert_level == 3, f"期望alert_level=3，实际={result.alert_level}"
    print(f"[PASS] 重度异常: score={result.final_score:.3f}, "
          f"confidence={result.confidence:.3f}, alert={result.alert_level}")


def test_normal_water():
    """测试正常水质场景"""
    image = ImageResult(
        turbidity_level=0,
        pollution_probs=[0.05, 0.02, 0.01, 0.01],
        confidence=0.95,
        timestamp=datetime.now()
    )
    sensor = SensorResult(
        turbidity=2.0,
        cod=5.0,
        ph=7.1,
        timestamp=datetime.now()
    )

    result = fuse_image_and_sensor(image, [sensor])
    assert result.alert_level == 0, f"期望alert_level=0，实际={result.alert_level}"
    assert result.final_score < 0.4
    print(f"[PASS] 正常水质: score={result.final_score:.3f}, "
          f"confidence={result.confidence:.3f}")


def test_rule_engine():
    """测试规则引擎降级模式"""
    sensor_data = {'turbidity': 90.0, 'cod': 60.0, 'ph': 5.5}
    result = rule_engine_analysis(sensor_data=sensor_data)
    assert result['alert_level'] == 3
    print(f"[PASS] 规则引擎降级: alert={result['alert_level']}")

    sensor_data2 = {'turbidity': 3.0, 'cod': 10.0, 'ph': 7.2}
    result2 = rule_engine_analysis(sensor_data=sensor_data2)
    assert result2['alert_level'] == 0
    print(f"[PASS] 规则引擎正常: alert={result2['alert_level']}")


if __name__ == "__main__":
    print("=" * 50)
    print("多源数据融合算法 - 单元测试")
    print("=" * 50)

    tests = [
        test_normal_fusion,
        test_sensor_missing,
        test_sensor_expired,
        test_severe_alert,
        test_normal_water,
        test_rule_engine
    ]

    passed = 0
    failed = 0
    for test in tests:
        try:
            test()
            passed += 1
        except AssertionError as e:
            print(f"[FAIL] {test.__name__}: {e}")
            failed += 1
        except Exception as e:
            print(f"[ERROR] {test.__name__}: {e}")
            failed += 1

    print(f"\n测试结果: {passed}通过, {failed}失败, {len(tests)}总计")

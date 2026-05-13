"""多源数据融合算法 - 加权投票 + 时间对齐"""
import logging
from datetime import datetime
from dataclasses import dataclass, field
from typing import Optional

logger = logging.getLogger(__name__)


@dataclass
class ImageResult:
    """影像分析结果"""
    turbidity_level: int = 0          # 0-清晰,1-轻度,2-中度,3-重度
    pollution_probs: list = field(default_factory=lambda: [0.0, 0.0, 0.0, 0.0])
    confidence: float = 0.0
    timestamp: Optional[datetime] = None


@dataclass
class SensorResult:
    """传感器结果"""
    turbidity: float = 0.0            # NTU
    cod: float = 0.0                  # mg/L
    ph: float = 7.0
    timestamp: Optional[datetime] = None


@dataclass
class FusionResult:
    """融合结果"""
    final_score: float = 0.0
    confidence: float = 0.0
    alert_level: int = 0              # 0-正常,1-轻度,2-中度,3-重度
    image_score: float = 0.0
    sensor_score: float = 0.0


def fuse_image_and_sensor(
    image_result: ImageResult,
    sensor_results: list,
    time_window_ms: float = 500.0,
    image_weight: float = 0.6,
    sensor_weight: float = 0.4
) -> FusionResult:
    """
    多源数据融合算法

    详细伪代码见设计报告5.1节

    Args:
        image_result: 影像分析结果
        sensor_results: 传感器结果列表
        time_window_ms: 时间对齐窗口(毫秒)
        image_weight: 影像分析权重
        sensor_weight: 传感器分析权重

    Returns:
        FusionResult: 融合后的综合判定结果
    """
    # 步骤1：时间对齐 - 寻找与影像时间戳最接近的传感器数据
    matched_sensor = None
    min_diff = time_window_ms

    if image_result.timestamp and sensor_results:
        for sensor in sensor_results:
            if sensor.timestamp is None:
                continue
            diff = abs((sensor.timestamp - image_result.timestamp).total_seconds() * 1000)
            if diff < min_diff:
                min_diff = diff
                matched_sensor = sensor

    # 步骤2：计算传感器分数（0-1）
    if matched_sensor is None:
        sensor_score_final = 0.0
        effective_sensor_weight = 0.0  # 无匹配数据时降级为纯影像
        confidence_penalty = 0.8
        logger.debug("无匹配的传感器数据，降级为纯影像分析模式")
    else:
        # 浊度80 NTU对应满分，COD 50 mg/L对应满分，pH偏离7越多分越高
        turb_score = min(matched_sensor.turbidity / 80.0, 1.0)
        cod_score = min(matched_sensor.cod / 50.0, 1.0)
        ph_score = min(abs(matched_sensor.ph - 7.0) / 3.5, 1.0)

        # 传感器加权：浊度50%，COD30%，pH20%
        sensor_score_final = turb_score * 0.5 + cod_score * 0.3 + ph_score * 0.2
        effective_sensor_weight = sensor_weight
        confidence_penalty = 1.0

    # 步骤3：计算影像分数
    turb_map = {0: 0.0, 1: 0.33, 2: 0.67, 3: 1.0}
    turb_score_img = turb_map.get(image_result.turbidity_level, 0.0)

    # 最高污染物概率
    max_pollution_prob = max(image_result.pollution_probs) if image_result.pollution_probs else 0.0

    # 浑浊度等级权重0.7 + 污染物概率权重0.3
    image_score = turb_score_img * 0.7 + max_pollution_prob * 0.3

    # 步骤4：加权融合
    final_score = image_score * (1.0 - effective_sensor_weight) + sensor_score_final * effective_sensor_weight

    # 步骤5：计算置信度
    if effective_sensor_weight > 0:
        consistency = 1.0 - abs(image_score - sensor_score_final)
    else:
        consistency = 0.5
    confidence = consistency * confidence_penalty * image_result.confidence

    # 步骤6：确定异常等级
    if final_score < 0.4:
        alert_level = 0  # 正常
    elif final_score < 0.7:
        alert_level = 1  # 轻度异常
    elif final_score < 0.9:
        alert_level = 2  # 中度异常
    else:
        alert_level = 3  # 重度异常

    return FusionResult(
        final_score=final_score,
        confidence=confidence,
        alert_level=alert_level,
        image_score=image_score,
        sensor_score=sensor_score_final
    )


def rule_engine_analysis(image: object = None, sensor_data: dict = None) -> dict:
    """
    规则引擎降级模式 - 基于传统图像处理算法进行水质判断

    当深度学习模型推理失败时自动切换到此模式
    """
    result = {'alert_level': 0, 'confidence': 0.0}

    if sensor_data:
        turbidity = sensor_data.get('turbidity', 0)
        cod = sensor_data.get('cod', 0)
        ph = sensor_data.get('ph', 7.0)

        # 基于阈值的规则判断
        if turbidity > 80 or cod > 50:
            result['alert_level'] = 3
        elif turbidity > 30 or cod > 30:
            result['alert_level'] = 2
        elif turbidity > 5 or cod > 15:
            result['alert_level'] = 1
        elif ph < 6.5 or ph > 8.5:
            result['alert_level'] = 2

        result['confidence'] = 0.6  # 规则引擎置信度低于深度学习

    return result

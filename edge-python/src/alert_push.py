"""预警推送模块 - 边缘端本地告警"""
import logging
from datetime import datetime
from dataclasses import dataclass, field
from typing import Optional

logger = logging.getLogger(__name__)


@dataclass
class AlertInfo:
    """告警信息数据结构"""
    alert_id: str = ""
    point_id: str = ""
    alert_level: int = 0  # 0-正常,1-轻度,2-中度,3-重度
    alert_type: str = "combined"
    details: dict = field(default_factory=dict)
    confidence: float = 0.0
    final_score: float = 0.0
    timestamp: Optional[datetime] = None
    push_channels: list = field(default_factory=lambda: ["platform"])

    def is_alert(self) -> bool:
        return self.alert_level > 0

    def is_severe(self) -> bool:
        return self.alert_level >= 3


class AlertPusher:
    """边缘端预警推送器 - 生成告警并通过MQTT上报"""

    ALERT_TEMPLATES = {
        1: {
            'color': 'blue',
            'title': '水质轻度异常提示',
            'action': '建议关注该监测点'
        },
        2: {
            'color': 'yellow',
            'title': '水质中度异常告警',
            'action': '建议安排现场巡检'
        },
        3: {
            'color': 'red',
            'title': '水质重度异常紧急告警',
            'action': '请立即采取处置措施'
        }
    }

    def __init__(self, point_id: str, point_name: str = ""):
        self.point_id = point_id
        self.point_name = point_name
        self._alert_counter = 0
        self._on_push_callbacks: list = []

    def generate_alert(self, alert_level: int, analysis_result: dict,
                       fusion_result=None) -> AlertInfo:
        """生成告警信息

        Args:
            alert_level: 异常等级
            analysis_result: 分析结果
            fusion_result: 融合结果（可选）

        Returns:
            AlertInfo: 告警信息，alert_level=0时返回空告警
        """
        if alert_level == 0:
            return AlertInfo(
                alert_id="",
                point_id=self.point_id,
                alert_level=0,
                timestamp=analysis_result.get('timestamp', datetime.now())
            )

        self._alert_counter += 1

        alert_type = self._determine_alert_type(analysis_result)
        template = self.ALERT_TEMPLATES.get(alert_level, {})

        details = {
            'turbidity_level': analysis_result.get('turbidity_level', 0),
            'pollution_types': analysis_result.get('pollution_types', []),
            'confidence': analysis_result.get('confidence', 0.0),
        }
        if fusion_result:
            details['final_score'] = fusion_result.final_score
            details['image_score'] = fusion_result.image_score
            details['sensor_score'] = fusion_result.sensor_score

        push_channels = self._determine_channels(alert_level)

        alert = AlertInfo(
            alert_id=f"ALT-{self.point_id}-{datetime.now().strftime('%Y%m%d%H%M%S')}-{self._alert_counter:04d}",
            point_id=self.point_id,
            alert_level=alert_level,
            alert_type=alert_type,
            details=details,
            confidence=analysis_result.get('confidence', 0.0),
            final_score=fusion_result.final_score if fusion_result else 0.0,
            timestamp=analysis_result.get('timestamp', datetime.now()),
            push_channels=push_channels
        )

        logger.info("告警生成: %s [%s] %s", alert.alert_id,
                     alert.alert_level, template.get('title', ''))

        return alert

    def push_alert(self, alert: AlertInfo) -> bool:
        """推送告警（通过回调通知外部MQTT客户端）"""
        if not alert.is_alert():
            return False

        alert_dict = {
            'alertId': alert.alert_id,
            'pointId': alert.point_id,
            'alertLevel': alert.alert_level,
            'alertType': alert.alert_type,
            'details': alert.details,
            'confidence': alert.confidence,
            'finalScore': alert.final_score,
            'timestamp': alert.timestamp.isoformat() if alert.timestamp else None,
            'pushChannels': alert.push_channels
        }

        for callback in self._on_push_callbacks:
            try:
                callback(alert_dict)
            except Exception as e:
                logger.error("预警推送回调失败: %s", e)

        return True

    def register_push_callback(self, callback) -> None:
        """注册推送回调（通常为MQTT发送函数）"""
        self._on_push_callbacks.append(callback)

    def _determine_alert_type(self, result: dict) -> str:
        """确定告警类型"""
        pollution_types = result.get('pollution_types', [])
        if pollution_types:
            type_map = {
                '有机污染物': 'organic',
                '氮磷污染': 'nitrogen_phosphorus',
                '油脂污染': 'oil',
                '微塑料': 'microplastic'
            }
            return type_map.get(pollution_types[0], 'combined')
        return 'combined'

    def _determine_channels(self, alert_level: int) -> list:
        """确定推送渠道"""
        if alert_level == 1:
            return ["platform"]
        elif alert_level == 2:
            return ["platform", "websocket"]
        else:
            return ["platform", "websocket", "sms"]

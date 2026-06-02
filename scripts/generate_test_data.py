"""水质监测测试数据生成器 - 生成模拟水质数据并插入MySQL"""
import json
import math
import random
import sys
from datetime import datetime, timedelta

import pymysql

# MySQL 连接配置
DB_CONFIG = {
    "host": "localhost",
    "user": "root",
    "password": "szy180720",
    "database": "smart_water_quality",
    "charset": "utf8mb4",
}

# 监测点列表（与数据库现有数据对应）
MONITORING_POINTS = [
    {"id": 1, "name": "钱塘江监测点A", "location": "钱塘江上游-杭州段"},
    {"id": 2, "name": "富春江监测点B", "location": "富春江中游-桐庐段"},
    {"id": 3, "name": "新安江监测点C", "location": "新安江上游-淳安段"},
]

# 污染物类型
POLLUTION_TYPES = [
    "clear", "algae", "sediment", "industrial_waste",
    "domestic_sewage", "agricultural_runoff", "oil_spill", "heavy_metal",
]

# 告警阈值（与 application.yml 一致）
THRESHOLDS = {
    "turbidity": {"mild": 5.0, "moderate": 30.0, "severe": 80.0},
    "cod": {"mild": 15.0, "moderate": 30.0, "severe": 50.0},
    "ph": {"min": 6.5, "max": 8.5},
}


def generate_sensor_value(base, noise_pct, min_val, max_val):
    """生成带噪声的传感器值"""
    noise = random.gauss(0, base * noise_pct)
    return round(max(min_val, min(max_val, base + noise)), 2)


def generate_ph():
    """生成pH值（正常范围6.5-8.5）"""
    if random.random() < 0.05:
        # 5% 概率异常
        return round(random.uniform(5.0, 10.0), 2)
    return round(random.gauss(7.2, 0.3), 2)


def generate_turbidity():
    """生成浊度值 NTU"""
    if random.random() < 0.03:
        # 3% 概率出现高浊度（暴雨、污染事件）
        return round(random.uniform(50, 150), 2)
    if random.random() < 0.08:
        # 8% 概率出现中轻度污染
        return round(random.uniform(10, 40), 2)
    return round(abs(random.gauss(3, 2)), 2)


def generate_cod():
    """生成COD值 mg/L"""
    if random.random() < 0.03:
        return round(random.uniform(40, 80), 2)
    if random.random() < 0.08:
        return round(random.uniform(20, 40), 2)
    return round(abs(random.gauss(10, 4)), 2)


def determine_turbidity_level(ntu):
    """根据浊度判断等级"""
    if ntu >= 80:
        return 3
    elif ntu >= 30:
        return 2
    elif ntu >= 5:
        return 1
    return 0


def determine_alert_level(turbidity, cod, ph):
    """综合判断告警等级 0-正常 1-轻度 2-中度 3-重度"""
    level = 0
    if turbidity >= THRESHOLDS["turbidity"]["severe"] or cod >= THRESHOLDS["cod"]["severe"]:
        level = max(level, 3)
    if turbidity >= THRESHOLDS["turbidity"]["moderate"] or cod >= THRESHOLDS["cod"]["moderate"]:
        level = max(level, 2)
    elif turbidity >= THRESHOLDS["turbidity"]["mild"] or cod >= THRESHOLDS["cod"]["mild"]:
        level = max(level, 1)
    if ph < THRESHOLDS["ph"]["min"] or ph > THRESHOLDS["ph"]["max"]:
        level = max(level, 2)
    return level


def get_pollution_types(turbidity_level, alert_level):
    """根据数据情况返回污染物类型"""
    if alert_level == 0:
        return json.dumps(["clear"])
    if turbidity_level >= 3:
        return json.dumps(random.sample(["sediment", "algae", "industrial_waste"], k=random.randint(1, 3)))
    if turbidity_level == 2:
        return json.dumps(random.sample(["algae", "sediment", "agricultural_runoff"], k=random.randint(1, 2)))
    return json.dumps(random.sample(["algae", "domestic_sewage", "agricultural_runoff"], k=random.randint(1, 2)))


def generate_records(point, start_time, end_time, interval_minutes=5):
    """为一个监测点生成连续时间段的水质数据"""
    records = []
    current = start_time
    while current <= end_time:
        # 加入每日趋势：早晨水质较好，下午略差
        hour_factor = 1.0 + 0.3 * math.sin((current.hour - 6) * math.pi / 12)
        # 加入周趋势：工作日略差（人类活动）
        weekday_factor = 1.0 + 0.15 * (1 if current.weekday() < 5 else 0)

        ph = generate_ph()
        turbidity = round(generate_turbidity() * hour_factor * weekday_factor, 2)
        cod = round(generate_cod() * hour_factor * weekday_factor, 2)

        turbidity_level = determine_turbidity_level(turbidity)
        alert_level = determine_alert_level(turbidity, cod, ph)

        # 影像分析分数（模拟 AI 从摄像头判断的异常程度）
        if alert_level >= 2:
            image_score = round(random.uniform(0.6, 0.98), 3)
        elif alert_level == 1:
            image_score = round(random.uniform(0.2, 0.55), 3)
        else:
            image_score = round(random.uniform(0.01, 0.18), 3)

        # 传感器分析分数
        sensor_score = round(
            (min(turbidity / 100, 1.0) * 0.4 +
             min(cod / 60, 1.0) * 0.35 +
             (abs(ph - 7.0) / 3.5) * 0.25),
            3,
        )

        # 多源融合分数
        final_score = round(image_score * 0.6 + sensor_score * 0.4, 3)
        confidence = round(random.uniform(0.75, 0.99), 3)

        records.append({
            "point_id": point["id"],
            "timestamp": current.strftime("%Y-%m-%d %H:%M:%S"),
            "turbidity_level": turbidity_level,
            "turbidity_ntu": turbidity,
            "cod_value": cod,
            "ph_value": ph,
            "pollution_types": get_pollution_types(turbidity_level, alert_level),
            "alert_level": alert_level,
            "confidence": confidence,
            "final_score": final_score,
            "image_score": image_score,
            "sensor_score": sensor_score,
        })

        current += timedelta(minutes=interval_minutes)
    return records


def insert_water_quality_data(conn, records, batch_size=500):
    """批量插入水质数据"""
    sql = """
        INSERT INTO water_quality_data
        (point_id, timestamp, turbidity_level, turbidity_ntu, cod_value, ph_value,
         pollution_types, alert_level, confidence, final_score, image_score, sensor_score)
        VALUES (%(point_id)s, %(timestamp)s, %(turbidity_level)s, %(turbidity_ntu)s,
                %(cod_value)s, %(ph_value)s, %(pollution_types)s, %(alert_level)s,
                %(confidence)s, %(final_score)s, %(image_score)s, %(sensor_score)s)
    """
    cursor = conn.cursor()
    total = len(records)
    for i in range(0, total, batch_size):
        batch = records[i : i + batch_size]
        cursor.executemany(sql, batch)
        conn.commit()
        pct = min(i + batch_size, total)
        print(f"  已插入: {pct}/{total} ({100 * pct // total}%)", end="\r")
    print()


def generate_alert_records(water_records):
    """根据水质数据生成对应的告警记录"""
    alerts = []
    alert_id = 0
    for r in water_records:
        if r["alert_level"] >= 1:
            alert_id += 1
            # 确定告警类型
            alert_types = []
            t = THRESHOLDS
            if r["turbidity_ntu"] >= t["turbidity"]["mild"]:
                alert_types.append("turbidity")
            if r["cod_value"] >= t["cod"]["mild"]:
                alert_types.append("cod")
            if r["ph_value"] < t["ph"]["min"] or r["ph_value"] > t["ph"]["max"]:
                alert_types.append("ph")
            if not alert_types:
                alert_types.append("combined")

            push_status = random.choices(
                ["sent", "confirmed", "pending"], weights=[0.6, 0.3, 0.1]
            )[0]

            details = json.dumps({
                "turbidity_ntu": float(r["turbidity_ntu"]),
                "cod_value": float(r["cod_value"]),
                "ph_value": float(r["ph_value"]),
                "final_score": float(r["final_score"]),
                "confidence": float(r["confidence"]),
            })

            alerts.append({
                "point_id": r["point_id"],
                "alert_level": r["alert_level"],
                "alert_type": ",".join(alert_types),
                "details": details,
                "push_status": push_status,
                "retry_count": random.randint(0, 2) if push_status == "pending" else 0,
                "create_time": r["timestamp"],
                "confirm_time": (
                    (datetime.strptime(r["timestamp"], "%Y-%m-%d %H:%M:%S") +
                     timedelta(minutes=random.randint(5, 60))).strftime("%Y-%m-%d %H:%M:%S")
                    if push_status == "confirmed" else None
                ),
            })
    return alerts


def insert_alert_records(conn, alerts):
    """批量插入告警记录"""
    sql = """
        INSERT INTO alert_record
        (point_id, alert_level, alert_type, details, push_status, retry_count, create_time, confirm_time)
        VALUES (%(point_id)s, %(alert_level)s, %(alert_type)s, %(details)s,
                %(push_status)s, %(retry_count)s, %(create_time)s, %(confirm_time)s)
    """
    cursor = conn.cursor()
    for i in range(0, len(alerts), 500):
        batch = alerts[i : i + 500]
        cursor.executemany(sql, batch)
        conn.commit()
        print(f"  告警: {min(i + 500, len(alerts))}/{len(alerts)}", end="\r")
    print()


def main():
    conn = pymysql.connect(**DB_CONFIG)
    print("数据库已连接: smart_water_quality\n")

    # 配置：生成最近7天，每5分钟一条的数据
    end_time = datetime.now().replace(second=0, microsecond=0)
    start_time = end_time - timedelta(days=60)
    interval = 5  # 分钟

    total_records = 0
    all_water_records = []

    for point in MONITORING_POINTS:
        print(f"生成 {point['name']} (point_id={point['id']}) 的数据...")
        records = generate_records(point, start_time, end_time, interval)
        print(f"  共 {len(records)} 条记录")
        insert_water_quality_data(conn, records)
        all_water_records.extend(records)
        total_records += len(records)

    print(f"\n=== 水质数据生成完成，共 {total_records} 条 ===")

    # 生成告警记录
    print("\n生成告警记录...")
    alerts = generate_alert_records(all_water_records)
    print(f"  共 {len(alerts)} 条告警")
    insert_alert_records(conn, alerts)
    print(f"=== 告警记录生成完成 ===")

    # 统计数据概览
    print("\n=== 数据统计 ===")
    cursor = conn.cursor()
    cursor.execute("""
        SELECT alert_level, COUNT(*) AS cnt
        FROM water_quality_data
        GROUP BY alert_level
        ORDER BY alert_level
    """)
    for row in cursor.fetchall():
        labels = {0: "正常", 1: "轻度", 2: "中度", 3: "重度"}
        print(f"  {labels.get(row[0], '未知')}: {row[1]} 条")

    cursor.execute("""
        SELECT p.name, COUNT(*) AS cnt, MAX(w.timestamp) AS latest
        FROM water_quality_data w
        JOIN monitoring_point p ON w.point_id = p.id
        GROUP BY w.point_id, p.name
        ORDER BY p.name
    """)
    print("\n各监测点数据:")
    for row in cursor.fetchall():
        print(f"  {row[0]}: {row[1]} 条, 最新: {row[2]}")

    conn.close()
    print("\n全部完成!")


if __name__ == "__main__":
    main()

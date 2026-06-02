"""本地数据缓存 - LRU变种算法 + SQLite"""
import json
import logging
import os
import sqlite3
import threading
import time
from collections import OrderedDict, deque
from datetime import datetime, timedelta
from typing import Optional

import msgpack

from .exceptions import StorageFullError

logger = logging.getLogger(__name__)


class DataCache:
    """LRU变种本地缓存 - 热数据区(LRU) + 冷数据区(FIFO)"""

    def __init__(self, cache_dir: str = "data",
                 max_cache_size_mb: int = 500,
                 protected_alert_days: int = 7):
        self.cache_dir = cache_dir
        self.max_cache_size_mb = max_cache_size_mb
        self.protected_alert_days = protected_alert_days

        # 热数据区：LRU (OrderedDict)
        self.hot_zone: OrderedDict = OrderedDict()
        self.hot_zone_max = 100

        # 冷数据区：FIFO (deque)
        self.cold_zone: deque = deque()
        self.cold_zone_max = 500

        # 访问计数器 (用于晋升判断)
        self._access_count: dict = {}

        # SQLite 数据库连接
        self.db_path = os.path.join(cache_dir, "local_cache.db")
        self._db_conn: Optional[sqlite3.Connection] = None
        self._db_lock = threading.Lock()

        self._init_storage()

    def _init_storage(self) -> None:
        """初始化存储目录和SQLite数据库"""
        os.makedirs(self.cache_dir, exist_ok=True)
        with self._db_lock:
            self._db_conn = sqlite3.connect(self.db_path, check_same_thread=False)
            self._db_conn.execute("""
                CREATE TABLE IF NOT EXISTS water_quality_data (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    point_id TEXT NOT NULL,
                    timestamp TEXT NOT NULL,
                    turbidity_level INTEGER DEFAULT 0,
                    turbidity_ntu REAL,
                    cod_value REAL,
                    ph_value REAL,
                    pollution_types TEXT,
                    alert_level INTEGER DEFAULT 0,
                    confidence REAL,
                    final_score REAL,
                    image_score REAL,
                    sensor_score REAL,
                    details TEXT,
                    uploaded INTEGER DEFAULT 0,
                    created_at TEXT DEFAULT (datetime('now'))
                )
            """)
            self._db_conn.execute("""
                CREATE TABLE IF NOT EXISTS alert_record (
                    id INTEGER PRIMARY KEY AUTOINCREMENT,
                    point_id TEXT NOT NULL,
                    alert_level INTEGER NOT NULL,
                    alert_type TEXT,
                    details TEXT,
                    push_status TEXT DEFAULT 'pending',
                    uploaded INTEGER DEFAULT 0,
                    create_time TEXT DEFAULT (datetime('now'))
                )
            """)
            self._db_conn.execute("""
                CREATE INDEX IF NOT EXISTS idx_wq_uploaded
                ON water_quality_data(uploaded)
            """)
            self._db_conn.execute("""
                CREATE INDEX IF NOT EXISTS idx_alert_uploaded
                ON alert_record(uploaded)
            """)
            self._db_conn.execute("""
                CREATE UNIQUE INDEX IF NOT EXISTS idx_wq_point_timestamp
                ON water_quality_data(point_id, timestamp)
            """)
            self._db_conn.commit()
        logger.info("本地缓存已初始化: dir=%s, max=%dMB", self.cache_dir, self.max_cache_size_mb)

    def save_to_cache(self, data: dict) -> bool:
        """保存数据到缓存

        新写入的数据先进入冷数据区，访问2次后晋升到热数据区
        """
        try:
            key = self._make_key(data)
            entry = {
                'key': key,
                'data': data,
                'timestamp': time.time(),
                'size_kb': len(json.dumps(data, default=str)) / 1024
            }

            # 新数据进入冷数据区
            self.cold_zone.append(entry)

            # 检查容量
            self._check_capacity()

            # 写入SQLite
            self._write_to_db(data)

            return True
        except Exception as e:
            logger.error("缓存写入失败: %s", e)
            return False

    def get_from_cache(self, key: str) -> Optional[dict]:
        """从缓存读取数据（更新访问计数）"""
        # 先查热数据区
        if key in self.hot_zone:
            self.hot_zone.move_to_end(key)
            return self.hot_zone[key]['data']

        # 再查冷数据区
        for entry in self.cold_zone:
            if entry['key'] == key:
                self._access_count[key] = self._access_count.get(key, 0) + 1
                # 访问2次后晋升到热数据区
                if self._access_count[key] >= 2:
                    self._promote_to_hot(entry)
                return entry['data']

        return None

    def get_unuploaded_records(self, table: str = "water_quality_data",
                                limit: int = 50) -> list:
        """获取未上传的记录（用于断点续传）"""
        with self._db_lock:
            cursor = self._db_conn.execute(
                f"SELECT * FROM {table} WHERE uploaded = 0 ORDER BY timestamp ASC LIMIT {limit}"
            )
            columns = [desc[0] for desc in cursor.description]
            return [dict(zip(columns, row)) for row in cursor.fetchall()]

    def mark_uploaded(self, table: str, record_id: int) -> None:
        """标记记录已上传"""
        with self._db_lock:
            self._db_conn.execute(
                f"UPDATE {table} SET uploaded = 1 WHERE id = ?", (record_id,)
            )
            self._db_conn.commit()

    def upload_cached_data(self, upload_func) -> int:
        """网络恢复后批量上传缓存数据（按时间戳升序）"""
        records = self.get_unuploaded_records("water_quality_data", 50)
        uploaded_count = 0
        consecutive_failures = 0
        max_consecutive_failures = 5
        for record in records:
            try:
                if upload_func(record):
                    self.mark_uploaded("water_quality_data", record['id'])
                    uploaded_count += 1
                    consecutive_failures = 0
                else:
                    logger.warning("上传失败，跳过: id=%s", record['id'])
                    consecutive_failures += 1
            except Exception as e:
                logger.error("上传异常: id=%s, error=%s", record['id'], e)
                consecutive_failures += 1
            if consecutive_failures >= max_consecutive_failures:
                logger.warning("连续%d次上传失败，暂停补传", consecutive_failures)
                break

        return uploaded_count

    def clear_cache(self) -> None:
        """清空已上传的缓存"""
        with self._db_lock:
            self._db_conn.execute(
                "DELETE FROM water_quality_data WHERE uploaded = 1"
            )
            self._db_conn.execute(
                "DELETE FROM alert_record WHERE uploaded = 1"
            )
            self._db_conn.commit()
        self.hot_zone.clear()
        self.cold_zone.clear()
        self._access_count.clear()
        logger.info("已上传缓存已清空")

    def get_storage_usage_mb(self) -> float:
        """获取当前缓存使用量(MB)"""
        total = 0
        for entry in list(self.hot_zone.values()) + list(self.cold_zone):
            total += entry.get('size_kb', 0)
        return total / 1024

    def _check_capacity(self) -> None:
        """检查并执行缓存淘汰"""
        current_mb = self.get_storage_usage_mb()
        if current_mb < self.max_cache_size_mb:
            return

        logger.warning("缓存容量超过限制: %.1fMB/%.1fMB", current_mb, self.max_cache_size_mb)

        # 优先淘汰冷数据区（FIFO），每次淘汰100条
        to_remove = 100
        protect_before = time.time() - self.protected_alert_days * 86400

        removed = 0
        while removed < to_remove and self.cold_zone:
            entry = self.cold_zone.popleft()
            # 保护机制：中度/重度异常保留至少7天
            if self._is_protected(entry, protect_before):
                self.cold_zone.appendleft(entry)
                break
            removed += 1
            self._access_count.pop(entry['key'], None)

        logger.info("缓存淘汰完成: 移除%d条", removed)

    def _is_protected(self, entry: dict, protect_before: float) -> bool:
        """检查数据是否受保护（异常数据保留7天）"""
        data = entry.get('data', {})
        alert_level = data.get('alert_level', 0)
        timestamp = entry.get('timestamp', 0)
        return alert_level >= 2 and timestamp > protect_before

    def _promote_to_hot(self, entry: dict) -> None:
        """将条目从冷数据区晋升到热数据区"""
        if len(self.hot_zone) >= self.hot_zone_max:
            # LRU淘汰最旧的
            self.hot_zone.popitem(last=False)
        self.hot_zone[entry['key']] = entry
        # 从冷数据区移除
        for i, e in enumerate(self.cold_zone):
            if e['key'] == entry['key']:
                del self.cold_zone[i]
                break

    def _make_key(self, data: dict) -> str:
        """生成缓存键"""
        return f"{data.get('point_id', 'unknown')}_{data.get('timestamp', '')}"

    def _write_to_db(self, data: dict) -> None:
        """将数据写入SQLite数据库"""
        try:
            with self._db_lock:
                self._db_conn.execute("""
                    INSERT OR IGNORE INTO water_quality_data
                    (point_id, timestamp, turbidity_level, turbidity_ntu,
                     cod_value, ph_value, alert_level, confidence,
                     final_score, image_score, sensor_score, details)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, (
                    data.get('point_id', ''),
                    data.get('timestamp', datetime.now().isoformat()),
                    data.get('turbidity_level', 0),
                    data.get('turbidity_ntu'),
                    data.get('cod_value'),
                    data.get('ph_value'),
                    data.get('alert_level', 0),
                    data.get('confidence'),
                    data.get('final_score'),
                    data.get('image_score'),
                    data.get('sensor_score'),
                    json.dumps(data.get('details', {}))
                ))
                self._db_conn.commit()
        except Exception as e:
            logger.error("SQLite写入失败: %s", e)

    def close(self) -> None:
        """关闭数据库连接"""
        if self._db_conn:
            self._db_conn.close()
            logger.info("本地缓存已关闭")

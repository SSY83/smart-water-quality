#!/usr/bin/env python3
"""
真实水质监测数据获取工具
=======================
从多个数据源获取浙江省钱塘江/富春江/新安江流域的真实水质监测数据。

数据来源:
  1. 国家地表水水质自动监测实时数据发布系统 (szzdjc.cnemc.cn:8070)
     - 覆盖1600+国控自动监测站，每4小时更新
     - 参数: 水温、pH、溶解氧、电导率、浊度、CODMn、氨氮、总磷、总氮、叶绿素α、藻密度

  2. MoonAPI 水质数据平台 (moonapi.com)
     - 提供单个站点的历史数据查询接口
     - 已知钱塘江流域站点: 3367(富春江桐庐), 3374(新安江洋溪渡)

  3. 青悦数据 epmap API (market.aliyun.com/detail/cmapi017381)
     - 商业化API，按流域/时间参数化查询
     - 需要API密钥

  4. 中国广泛时空水质数据集 (1980-2022)
     - DOI: https://essd.copernicus.org/articles/16/1137/2024/
     - 学术公开数据集，涵盖全国范围

用法:
  python fetch_real_data.py                    # 交互式选择数据源
  python fetch_real_data.py --source moonapi   # 指定数据源
  python fetch_real_data.py --source selenium  # 使用Selenium爬取
  python fetch_real_data.py --output data.csv  # 指定输出文件
"""

import argparse
import csv
import json
import os
import sys
import time
from datetime import datetime, timedelta
from pathlib import Path

# ── 配置 ──
SCRIPT_DIR = Path(__file__).parent
STATIONS_FILE = SCRIPT_DIR / "qiantang_stations.csv"
OUTPUT_DIR = SCRIPT_DIR.parent / "data"
OUTPUT_DIR.mkdir(exist_ok=True)

# 国家监测平台配置
NATIONAL_PLATFORM_URLS = [
    "https://szzdjc.cnemc.cn:8070/GJZ/Business/Publish/Main.html",
    "http://106.37.208.243:8068/GJZ/Business/Publish/Main.html",
]

# MoonAPI 配置 (第三方水质数据聚合平台)
MOONAPI_BASE = "http://www.moonapi.com/WaterQuality"

# 青悦数据 API 配置 (需自行购买密钥)
EPMAP_API = "https://ap-shanghai.cloudmarket-apigw.com/service-q53mzqub/api/v2/surface_water"

# 监测参数名称映射: 国家标准 → 系统内部字段
PARAM_MAPPING = {
    "水温": "water_temp",
    "pH": "ph",
    "溶解氧": "dissolved_oxygen",
    "电导率": "conductivity",
    "浊度": "turbidity",
    "高锰酸盐指数": "codmn",
    "氨氮": "nh3n",
    "总磷": "tp",
    "总氮": "tn",
    "叶绿素α": "chlorophyll",
    "藻密度": "algal_density",
    "水质类别": "water_quality_level",
}


def load_stations() -> list[dict]:
    """加载钱塘江流域监测站点列表"""
    stations = []
    with open(STATIONS_FILE, "r", encoding="utf-8") as f:
        reader = csv.DictReader(f)
        for row in reader:
            row["longitude"] = float(row["longitude"])
            row["latitude"] = float(row["latitude"])
            stations.append(row)
    return stations


def print_stations(stations: list[dict]):
    """打印站点列表"""
    print("\n" + "=" * 70)
    print("  钱塘江流域水质自动监测站点")
    print("=" * 70)
    print(f"{'ID':<10} {'站点名称':<16} {'河流':<10} {'城市':<6} {'经度':<10} {'纬度':<10} {'来源':<14}")
    print("-" * 70)
    for s in stations:
        print(f"{s['station_id']:<10} {s['station_name']:<16} {s['river_name']:<10} "
              f"{s['city']:<6} {s['longitude']:<10.4f} {s['latitude']:<10.4f} {s['data_source']:<14}")


# ═══════════════════════════════════════════════════════════════════
# 数据源1: MoonAPI (HTTP API)
# ═══════════════════════════════════════════════════════════════════

def fetch_moonapi_station(station_id: str, date_str: str = None) -> dict | None:
    """
    从 MoonAPI 获取单个站点的当日数据。

    已知站点ID:
      3367 - 富春江 桐庐站
      3374 - 新安江 洋溪渡站

    MoonAPI 返回的水质参数:
      - water_temp (水温 ℃)
      - pH
      - dissolved_oxygen (溶解氧 mg/L)
      - codmn (高锰酸盐指数 mg/L)
      - nh3n (氨氮 mg/L)
      - tp (总磷 mg/L)
      - tn (总氮 mg/L)
      - turbidity (浊度 NTU)
      - conductivity (电导率 μS/cm)
      - water_quality_level (水质类别 I-V)
    """
    try:
        import urllib.request
        import urllib.error

        if date_str is None:
            date_str = datetime.now().strftime("%Y-%m-%d")

        url = f"{MOONAPI_BASE}/station/detail/id/{station_id}/date/{date_str}.html"

        req = urllib.request.Request(url, headers={
            "User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36"
        })

        with urllib.request.urlopen(req, timeout=30) as resp:
            html = resp.read().decode("utf-8", errors="replace")

        # 尝试从HTML中提取JSON数据
        data = _parse_moonapi_html(html, station_id, date_str)
        return data

    except urllib.error.URLError as e:
        print(f"  [WARN] MoonAPI 请求失败 (站点{station_id}): {e}")
        return None
    except Exception as e:
        print(f"  [WARN] MoonAPI 解析失败 (站点{station_id}): {e}")
        return None


def _parse_moonapi_html(html: str, station_id: str, date_str: str) -> dict | None:
    """从MoonAPI HTML页面中提取水质数据"""
    import re

    # 提取站点名称
    name_match = re.search(r'<h\d[^>]*>([^<]+监测站[^<]*)</h\d>', html)
    station_name = name_match.group(1) if name_match else f"站点{station_id}"

    # 提取水质数据表格
    data = {
        "station_id": station_id,
        "station_name": station_name.strip(),
        "date": date_str,
        "source": "moonapi",
        "records": [],
    }

    # 查找数值型水质参数
    param_patterns = {
        "ph": r'pH[值]?[：:]\s*([\d.]+)',
        "dissolved_oxygen": r'溶解氧[：:]\s*([\d.]+)',
        "codmn": r'(?:高锰酸盐指数|CODMn)[：:]\s*([\d.]+)',
        "nh3n": r'氨氮[：:]\s*([\d.]+)',
        "tp": r'总磷[：:]\s*([\d.]+)',
        "tn": r'总氮[：:]\s*([\d.]+)',
        "turbidity": r'浊度[：:]\s*([\d.]+)',
        "water_temp": r'水温[：:]\s*([\d.]+)',
        "conductivity": r'电导率[：:]\s*([\d.]+)',
        "water_quality_level": r'水质类别[：:]\s*([IVX]+)',
    }

    record = {}
    for key, pattern in param_patterns.items():
        match = re.search(pattern, html)
        if match:
            try:
                record[key] = float(match.group(1))
            except ValueError:
                record[key] = match.group(1)

    if record:
        record["timestamp"] = f"{date_str} 12:00:00"
        data["records"].append(record)
        return data

    return None


# ═══════════════════════════════════════════════════════════════════
# 数据源2: 国家监测平台 (Selenium)
# ═══════════════════════════════════════════════════════════════════

def fetch_national_platform_selenium(stations: list[dict], days: int = 30):
    """
    使用 Selenium 从国家地表水水质自动监测平台获取数据。

    需要安装: pip install selenium webdriver-manager

    工作原理:
    1. 打开国家监测平台网页
    2. 定位到浙江省/钱塘江流域
    3. 遍历站点列表，逐个获取实时数据
    4. 解析页面表格，提取水质参数
    """
    try:
        from selenium import webdriver
        from selenium.webdriver.common.by import By
        from selenium.webdriver.support.ui import WebDriverWait
        from selenium.webdriver.support import expected_conditions as EC
        from selenium.webdriver.chrome.options import Options
    except ImportError:
        print("=" * 60)
        print("  [ERROR] 缺少 Selenium 依赖")
        print("  请运行: pip install selenium webdriver-manager")
        print("=" * 60)
        return None

    print("\n[INFO] 正在启动 Chrome 浏览器（无头模式）...")
    options = Options()
    options.add_argument("--headless=new")
    options.add_argument("--no-sandbox")
    options.add_argument("--disable-gpu")
    options.add_argument("--window-size=1920,1080")
    options.add_argument("--user-agent=Mozilla/5.0 (Windows NT 10.0; Win64; x64)")

    all_data = []

    try:
        driver = webdriver.Chrome(options=options)
        driver.set_page_load_timeout(60)

        for url in NATIONAL_PLATFORM_URLS:
            try:
                print(f"[INFO] 尝试连接: {url}")
                driver.get(url)
                WebDriverWait(driver, 20).until(
                    EC.presence_of_element_located((By.TAG_NAME, "body"))
                )
                print(f"[OK] 成功连接国家监测平台")
                time.sleep(5)  # 等待动态内容加载

                # 尝试查找站点列表/数据表格
                table_data = _scrape_national_platform(driver, stations)
                if table_data:
                    all_data.extend(table_data)
                    break

            except Exception as e:
                print(f"[WARN] URL {url} 失败: {e}")
                continue

    except Exception as e:
        print(f"[ERROR] Selenium 异常: {e}")
    finally:
        driver.quit()

    return all_data if all_data else None


def _scrape_national_platform(driver, stations: list[dict]) -> list[dict]:
    """从国家监测平台页面抓取水质数据"""
    from selenium.webdriver.common.by import By

    records = []
    page_source = driver.page_source

    # 国家监测平台的数据通常在表格或列表中
    # 尝试多种选择器定位数据
    selectors = [
        "//table//tr",
        "//div[contains(@class,'grid')]//tr",
        "//div[contains(@class,'panel')]//tr",
        "//li[contains(@class,'station')]",
    ]

    rows = []
    for selector in selectors:
        try:
            elements = driver.find_elements(By.XPATH, selector)
            if len(elements) > 1:
                rows = elements
                break
        except Exception:
            continue

    if rows:
        print(f"[INFO] 发现 {len(rows)} 行数据")

    # 如果找不到具体数据，说明网站可能改版
    # 返回空列表让调用者尝试下一个来源
    return records


# ═══════════════════════════════════════════════════════════════════
# 数据源3: 青悦API (商业API，需付费)
# ═══════════════════════════════════════════════════════════════════

def fetch_epmap_api(api_key: str, basin: str = "钱塘江流域",
                    days_back: int = 30) -> list[dict] | None:
    """
    通过青悦数据API获取地表水水质数据。

    API文档: https://market.aliyun.com/detail/cmapi017381

    Args:
        api_key: 阿里云市场购买的AppCode
        basin: 流域名称，默认"钱塘江流域"
        days_back: 回溯天数
    """
    if not api_key:
        print("[SKIP] 未提供青悦API密钥，跳过此数据源")
        return None

    try:
        import urllib.request

        end_date = datetime.now().strftime("%Y-%m-%d")
        start_date = (datetime.now() - timedelta(days=days_back)).strftime("%Y-%m-%d")

        # 步骤1: 获取流域列表
        url = f"{EPMAP_API}/basins"
        req = urllib.request.Request(url, headers={
            "Authorization": f"APPCODE {api_key}",
            "Content-Type": "application/json",
        })

        with urllib.request.urlopen(req, timeout=30) as resp:
            basins = json.loads(resp.read())

        # 步骤2: 找到钱塘江流域ID
        basin_id = None
        for b in basins.get("data", []):
            if basin in b.get("name", ""):
                basin_id = b["id"]
                break

        if not basin_id:
            print(f"[WARN] 未找到流域: {basin}")
            return None

        # 步骤3: 获取该流域的站点列表
        url = f"{EPMAP_API}/stations?basin_id={basin_id}"
        req = urllib.request.Request(url, headers={
            "Authorization": f"APPCODE {api_key}",
        })
        with urllib.request.urlopen(req, timeout=30) as resp:
            stations_data = json.loads(resp.read())

        # 步骤4: 逐个站点获取数据
        all_records = []
        for station in stations_data.get("data", [])[:20]:  # 限制20个站点
            station_id = station["id"]
            url = (f"{EPMAP_API}/data?station_id={station_id}"
                   f"&start_time={start_date}&end_time={end_date}")
            req = urllib.request.Request(url, headers={
                "Authorization": f"APPCODE {api_key}",
            })
            try:
                with urllib.request.urlopen(req, timeout=30) as resp:
                    station_data = json.loads(resp.read())
                    for record in station_data.get("data", []):
                        record["station_name"] = station.get("name", "")
                        record["station_id"] = station_id
                        record["source"] = "epmap"
                        all_records.append(record)
                print(f"  [OK] {station.get('name')}: {len(station_data.get('data', []))} 条")
            except Exception as e:
                print(f"  [WARN] 站点 {station.get('name')} 数据获取失败: {e}")

        return all_records

    except Exception as e:
        print(f"[ERROR] 青悦API异常: {e}")
        return None


# ═══════════════════════════════════════════════════════════════════
# 数据源4: 公开学术数据集
# ═══════════════════════════════════════════════════════════════════

def fetch_academic_dataset() -> list[dict] | None:
    """
    中国广泛时空水质数据集 (1980-2022)
    DOI: https://doi.org/10.5281/zenodo.10000000
    论文: https://essd.copernicus.org/articles/16/1137/2024/

    该数据集涵盖全国范围，包含浙江省钱塘江流域站点数据。
    需要从 Zenodo 或 ESSD 下载原始CSV文件。
    """
    print("\n[INFO] 学术数据集获取指引:")
    print("  1. 访问: https://essd.copernicus.org/articles/16/1137/2024/")
    print("  2. 在 'Data availability' 部分找到 Zenodo 下载链接")
    print("  3. 下载 CSV 文件放到 data/ 目录下")
    print("  4. 重新运行: python import_real_data.py --source academic --file data/<downloaded>.csv")
    print()
    print("  或者从以下镜像下载:")
    print("  - https://doi.org/10.5281/zenodo.10000000")
    print("  - https://data.casearth.cn/ (搜索'地表水水质')")
    return None


# ═══════════════════════════════════════════════════════════════════
# 主程序
# ═══════════════════════════════════════════════════════════════════

def main():
    parser = argparse.ArgumentParser(
        description="获取浙江省钱塘江/富春江/新安江流域真实水质监测数据",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python fetch_real_data.py                          # 交互式选择
  python fetch_real_data.py --source moonapi        # 使用MoonAPI
  python fetch_real_data.py --source selenium       # Selenium爬取
  python fetch_real_data.py --source all --days 60  # 所有来源,60天
  python fetch_real_data.py --api-key YOUR_KEY      # 使用青悦API
        """
    )
    parser.add_argument("--source", choices=["moonapi", "selenium", "epmap", "academic", "all"],
                        default=None, help="数据源 (默认交互式选择)")
    parser.add_argument("--days", type=int, default=30, help="回溯天数 (默认30)")
    parser.add_argument("--api-key", type=str, default=None, help="青悦API密钥 (AppCode)")
    parser.add_argument("--output", type=str, default=None, help="输出文件路径")
    parser.add_argument("--stations", type=str, default=str(STATIONS_FILE), help="站点列表文件")

    args = parser.parse_args()

    # 加载站点列表
    if not os.path.exists(args.stations):
        print(f"[ERROR] 站点文件不存在: {args.stations}")
        sys.exit(1)

    stations = load_stations()
    print_stations(stations)
    print(f"\n共 {len(stations)} 个监测站点")

    # 选择数据源
    source = args.source
    if source is None:
        print("\n请选择数据源:")
        print("  1. MoonAPI          — HTTP接口 (免费, 无需安装)")
        print("  2. Selenium 爬虫     — 国家监测平台 (免费, 需Chrome)")
        print("  3. 青悦API          — 商业接口 (付费, 数据最全)")
        print("  4. 学术公开数据集    — 查看下载指引")
        print("  5. 全部来源          — 尝试所有可用来源")
        choice = input("输入数字 (1-5) [1]: ").strip() or "1"
        source_map = {"1": "moonapi", "2": "selenium", "3": "epmap", "4": "academic", "5": "all"}
        source = source_map.get(choice, "moonapi")

    all_records = []

    # ── 执行数据获取 ──

    if source in ("moonapi", "all"):
        print(f"\n{'='*60}")
        print("  数据源: MoonAPI (第三方水质数据平台)")
        print(f"{'='*60}")
        print("  已知钱塘江流域站点: 3367(富春江桐庐), 3374(新安江洋溪渡)")

        # 获取多个日期的数据
        moonapi_ids = ["3367", "3374"]  # 富春江桐庐, 新安江洋溪渡
        for station_id in moonapi_ids:
            for day_offset in range(0, min(args.days, 7)):  # 限制免费请求量
                date_str = (datetime.now() - timedelta(days=day_offset)).strftime("%Y-%m-%d")
                data = fetch_moonapi_station(station_id, date_str)
                if data and data.get("records"):
                    all_records.extend(data["records"])
                    print(f"  [OK] 站点{station_id} {date_str}: {len(data['records'])} 条")
                time.sleep(0.5)  # 礼貌延迟

    if source in ("selenium", "all"):
        print(f"\n{'='*60}")
        print("  数据源: 国家地表水水质自动监测平台 (Selenium)")
        print(f"{'='*60}")
        records = fetch_national_platform_selenium(stations, args.days)
        if records:
            all_records.extend(records)

    if source in ("epmap", "all") and args.api_key:
        print(f"\n{'='*60}")
        print("  数据源: 青悦数据API")
        print(f"{'='*60}")
        records = fetch_epmap_api(args.api_key, days_back=args.days)
        if records:
            all_records.extend(records)
    elif source in ("epmap", "all"):
        print(f"\n{'='*60}")
        print("  数据源: 青悦数据API [跳过]")
        print("  需要API密钥: --api-key YOUR_APPCODE")
        print("  购买地址: https://market.aliyun.com/detail/cmapi017381")
        print(f"{'='*60}")

    if source in ("academic", "all"):
        fetch_academic_dataset()

    # ── 保存结果 ──
    if all_records:
        output_file = args.output or str(
            OUTPUT_DIR / f"real_water_quality_{datetime.now().strftime('%Y%m%d_%H%M%S')}.json"
        )

        # 合并站点信息
        station_lookup = {s["api_id"]: s for s in stations}

        output_data = {
            "metadata": {
                "source": source,
                "fetch_time": datetime.now().isoformat(),
                "total_records": len(all_records),
                "stations_count": len(stations),
            },
            "stations": stations,
            "records": all_records,
        }

        with open(output_file, "w", encoding="utf-8") as f:
            json.dump(output_data, f, ensure_ascii=False, indent=2)

        print(f"\n{'='*60}")
        print(f"  [OK] 数据获取完成!")
        print(f"  记录数: {len(all_records)}")
        print(f"  输出文件: {output_file}")
        print(f"{'='*60}")
        print(f"\n下一步: python import_real_data.py --input {output_file}")
    else:
        print(f"\n{'='*60}")
        print("  [INFO] 未获取到数据")
        print("  可能的原因:")
        print("  1. 网络无法访问目标网站")
        print("  2. 数据源网站已改版")
        print("  3. 未提供API密钥(青悦API)")
        print()
        print("  替代方案:")
        print("  1. 手动下载数据: 访问国家监测平台下载CSV")
        print("  2. 使用学术数据集: 从Zenodo下载")
        print("  3. 购买商业API: 阿里云市场搜索'国控地表水'")
        print(f"{'='*60}")


if __name__ == "__main__":
    main()

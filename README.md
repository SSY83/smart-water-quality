# 智慧水利 — 基于轻量化深度学习的河道水质智能监测与预警系统

**Smart Water Quality** — Edge-Cloud Collaborative River Water Quality Monitoring & Early Warning System

基于 MobileNetV2 + U-Net 轻量化深度学习模型，结合边缘-云协同架构，实现对浙江河道（苕溪、钱塘江、西湖）水质指标的实时监测与多级预警。

---

## 1. 系统架构

```
+---------------------------------------------------------------------+
|                        边缘端 (Edge Device)                           |
|                 Raspberry Pi 4B / Jetson Nano                        |
|                                                                      |
|  +------------------+    +-----------------+    +-----------------+  |
|  |  video_capture   |    |  sensor_reader  |    |  mqtt_client    |  |
|  |  OpenCV 视频采集  |    |  Modbus RTU     |    |  TLS 加密通信   |  |
|  +--------+---------+    +--------+--------+    +--------+--------+  |
|           |                       |                       |          |
|  +--------v-----------------------v-----------------------+-------+  |
|  |                    water_quality_analyzer                       |  |
|  |  水质分析主控: 并行推理调度 + 模型降级监控 + 规则引擎回退       |  |
|  +--------+-------------------------+---------+---------+--------+  |
|           |                         |         |         |           |
|  +--------v--------+  +------------v--+  +---v---------v-------+   |
|  |  model_loader   |  | fusion_algorithm| |   data_cache        |   |
|  |  TFLite 单例    |  | 多源融合 0.6+0.4| |  SQLite 离线缓存    |   |
|  +-----------------+  +----------------+ +---------------------+   |
+---------------------------------------------------------------------+
                              |  MQTT/TLS + Protobuf
                              v
+---------------------------------------------------------------------+
|                         云端 (Cloud Backend)                         |
|                    Java 11 + Spring Boot 2.7                         |
|                                                                      |
|  +------------------+    +-----------------+    +-----------------+  |
|  | MqttSubscriber   |    | AuthService     |    | AlertWebSocket  |  |
|  | 通配符主题订阅   |    | JWT+BCrypt+RBAC |    | 实时告警推送    |  |
|  +--------+---------+    +--------+--------+    +--------+--------+  |
|           |                       |                       |          |
|  +--------v-----------------------v-----------------------+-------+  |
|  |              IntelligentAnalysisService                          |  |
|  |   融合验证: 云端二次验证 + 异常等级判定 + 规则降级              |  |
|  +--------+-------------------------+---------+---------+--------+  |
|           |                         |         |         |           |
|  +--------v--------+  +------------v--+  +---v---------v-------+   |
|  |DataCollection   |  |AlertPushService| |VisualizationService |   |
|  |异步队列写入DB   |  |多级推送+重试   | |缓存旁路趋势查询    |   |
|  +-----------------+  +----------------+ +---------------------+   |
|                                                                      |
|  MySQL 8.4 (分区表)  +  Redis (Cache-aside)  +  Caffeine (本地缓存) |
+---------------------------------------------------------------------+
```

---

## 2. 项目结构

```
smart-water-quality/
|
+-- database/
|   +-- schema.sql                      # 完整DDL (8表, 按月分区, 索引, 预置数据)
|
+-- cloud-backend/                      # 云端 Java 后端 (48个源文件)
|   +-- pom.xml                         # Maven 构建配置 (SB 2.7.18, Java 11)
|   +-- test-api.ps1                    # API 一键测试脚本
|   +-- src/main/java/com/waterquality/
|   |   +-- WaterQualityApplication.java  # 启动类 @EnableAsync @EnableScheduling
|   |   +-- config/                        # 6 个配置类
|   |   |   +-- CaffeineConfig.java        #   本地缓存配置
|   |   |   +-- RedisConfig.java           #   Redis 条件装配
|   |   |   +-- ThreadPoolConfig.java      #   4个线程池 (sms/db-write/mqtt/retry)
|   |   |   +-- WebMvcConfig.java          #   CORS + 拦截器注册
|   |   |   +-- WebSocketConfig.java       #   WebSocket 端点注册
|   |   +-- constant/
|   |   |   +-- ErrorCode.java             #   8个错误码定义
|   |   +-- controller/                    # 5 个 REST 控制器
|   |   |   +-- AlertController.java       #   GET /api/alerts/point/{id}
|   |   |   +-- AuthController.java        #   POST /api/auth/login,logout
|   |   |   +-- DataController.java        #   POST /api/data/report,heartbeat
|   |   |   +-- MonitoringPointController  #   GET /api/monitoring-points
|   |   |   +-- VisualizationController    #   POST /api/visualization/trend,heatmap,query
|   |   +-- dto/                           # 6 个数据传输对象
|   |   |   +-- AlertResult.java           #   告警推送结果 (alertId + pushStatus)
|   |   |   +-- AnalysisResult.java        #   边缘端上报分析结果
|   |   |   +-- LoginRequest.java          #   登录请求体
|   |   |   +-- PushTask.java              #   推送重试任务 (PriorityQueue)
|   |   |   +-- QueryParams.java           #   通用查询参数 (PointIds+时间范围+分页)
|   |   |   +-- Result.java                #   统一响应封装 <T> (code+msg+data+total)
|   |   +-- entity/                        # 7 个实体类 (MyBatis Plus 映射)
|   |   |   +-- AlertRecord.java           #   告警记录 (8字段, 自增ID)
|   |   |   +-- BaseEntity.java            #   基础实体 (创建/更新时间)
|   |   |   +-- EdgeDevice.java            #   边缘设备
|   |   |   +-- MonitoringPoint.java       #   水质监测点
|   |   |   +-- SysConfig.java             #   系统配置项
|   |   |   +-- User.java                  #   系统用户 (BCrypt密码)
|   |   |   +-- WaterQualityData.java      #   水质监测数据 (分区表)
|   |   +-- enums/
|   |   |   +-- AlertLevel.java            #   异常等级枚举 (0-正常/1-轻度/2-中度/3-重度)
|   |   |   +-- SensorType.java            #   传感器类型枚举
|   |   +-- exception/                     # 3 个异常处理类
|   |   |   +-- BusinessException.java     #   业务异常
|   |   |   +-- ErrorResponse.java         #   错误响应体
|   |   |   +-- GlobalExceptionHandler.java#   全局异常拦截 (@RestControllerAdvice)
|   |   +-- mapper/                        # 6 个 MyBatis Plus Mapper
|   |   |   +-- AlertRecordMapper.java     #   自定义: 按点+时间查询, 待重试扫描
|   |   |   +-- EdgeDeviceMapper.java      #   CRUD 基础
|   |   |   +-- MonitoringPointMapper.java #   CRUD 基础
|   |   |   +-- SysConfigMapper.java       #   CRUD 基础
|   |   |   +-- UserMapper.java            #   自定义: 按用户名查询
|   |   |   +-- WaterQualityDataMapper.java#   自定义: 多点时间范围查询, 聚合统计
|   |   +-- mqtt/                          # MQTT 通信模块
|   |   |   +-- MqttMessageHandler.java    #   Protobuf 反序列化 + 消息路由
|   |   |   +-- MqttSubscriber.java        #   订阅器: 自动重连, 通配符主题
|   |   +-- security/                      # 安全模块
|   |   |   +-- JwtAuthenticationFilter.java # JWT 请求拦截
|   |   |   +-- JwtTokenProvider.java       # HS256 令牌生成/验证/刷新
|   |   |   +-- SecurityConfig.java         # BCrypt 密码编码 + RBAC 配置
|   |   +-- service/                       # 6 个业务服务
|   |   |   +-- AlertPushService.java      #   告警推送: 多级策略, 指数退避重试
|   |   |   +-- AuthService.java           #   认证服务: 登录/登出/令牌刷新
|   |   |   +-- DataCollectionService.java #   数据采集: 异步队列写入 (LinkedBlockingQueue)
|   |   |   +-- IntelligentAnalysisService #   智能分析: 融合验证, 等级判定, 规则降级
|   |   |   +-- SmsService.java            #   短信服务: HTTP调用 + 熔断保护
|   |   |   +-- VisualizationService.java  #   可视化: 趋势/热力图/分页 + Redis缓存
|   |   +-- websocket/
|   |       +-- AlertWebSocketHandler.java #   WebSocket: JWT认证 + 心跳 + 广播/单播
|   +-- src/main/resources/
|       +-- application.yml                #   完整配置 (MySQL+Redis+MQTT+WebSocket)
|       +-- application-local.yml          #   本地轻量配置 (仅MySQL)
|
+-- edge-python/                          # 边缘端 Python 框架 (11个源文件)
|   +-- requirements.txt                  #   Python 依赖
|   +-- config/
|   |   +-- config.yaml                   #   边缘端配置 (传感器/摄像头/MQTT/模型)
|   +-- src/
|   |   +-- main.py                       #   主入口: 多线程调度 + 信号处理
|   |   +-- video_capture.py              #   视频采集: OpenCV 帧读取 + 定时截图
|   |   +-- sensor_reader.py              #   传感器读取: Modbus RTU (pH/浊度/COD)
|   |   +-- model_loader.py               #   模型加载: TFLite 单例 MobileNetV2+U-Net
|   |   +-- water_quality_analyzer.py     #   分析主控: 并行推理 + 降级监控 + 规则回退
|   |   +-- fusion_algorithm.py           #   融合算法: 时序对齐 + 加权投票 (0.6+0.4)
|   |   +-- mqtt_client.py                #   MQTT客户端: TLS加密 + JSON序列化 + QoS1
|   |   +-- data_cache.py                 #   本地缓存: LRU变种 + SQLite + 断点续传
|   |   +-- alert_push.py                 #   本地告警: 边端触发 + 上传
|   |   +-- exceptions.py                 #   异常定义: SensorError/ModelError/MqttError
|   +-- tests/
|       +-- test_fusion.py                #   融合算法 6 个单元测试 (全部通过)
|
+-- docs/                                 # 设计文档
|   +-- 第13组-《智慧水利应用》详细设计报告.pdf
|   +-- 智慧水利应用-功能开发与项目进度报告.md
|   +-- 智慧水利应用-功能开发与项目进度报告.docx
```

---

## 3. 核心类与函数定义

### 3.1 云端后端 — Service 层

#### IntelligentAnalysisService（智能分析服务）

| 方法 | 功能 |
|------|------|
| `processAnalysisResult(AnalysisResult)` | 接收边缘端分析结果 → 存储数据 → 触发告警推送 |
| `verifyFusionScore(imageScore, sensorScore)` | 云端融合验证: `image*0.6 + sensor*0.4` |
| `determineAlertLevel(finalScore)` | 异常等级判定: <0.4正常 / <0.7轻度 / <0.9中度 / ≥0.9重度 |
| `ruleBasedAnalysis(pointId, sensorData)` | 降级模式: 基于传感器阈值规则判断 (浊度/COD/pH) |

#### AlertPushService（告警推送服务）

| 方法 | 功能 |
|------|------|
| `pushAlert(AnalysisResult)` | 多级推送: 轻度→仅入库, 中度→+WebSocket, 重度→+短信 |
| `determineChannels(alertLevel)` | 根据异常等级选择推送渠道 |
| `retryFailedPushes()` | 定时扫描重试队列 (@Scheduled 5min), 指数退避 1/2/4/8/16min |
| `executeRetry(PushTask)` | 单次重试执行, 超5次标记 failed |

#### DataCollectionService（数据采集服务）

| 方法 | 功能 |
|------|------|
| `receiveData(AnalysisResult)` | 异步写入: 数据入队 (LinkedBlockingQueue, 容量1000) |
| `batchInsert(List<WaterQualityData>)` | 批量插入数据库 |

#### VisualizationService（可视化服务）

| 方法 | 功能 |
|------|------|
| `getTrendData(QueryParams)` | ECharts 趋势数据: Redis 缓存旁路 → 多点时间范围查询 |
| `getHeatmapData(pointIds, start, end)` | 热力图聚合: 各监测点平均异常分数 |
| `queryData(QueryParams)` | 分页历史查询 |

#### AuthService（认证服务）

| 方法 | 功能 |
|------|------|
| `login(username, password)` | JWT 令牌生成 (HS256, 24h), BCrypt 密码验证 (cost=10) |
| `logout(userId)` | 令牌失效 (Redis 黑名单) |

#### SmsService（短信服务）

| 方法 | 功能 |
|------|------|
| `sendAlert(alertId)` | HTTP POST 短信网关, 熔断器保护 (5次失败→熔断10min) |

---

### 3.2 边缘端 Python — 核心模块

#### water_quality_analyzer（水质分析主控）

| 类/函数 | 功能 |
|------|------|
| `WaterQualityAnalyzer` | 主控类: 管理模型加载/推理调度/结果融合 |
| `analyze_frame(frame)` | 对视频帧进行 MobileNetV2 + U-Net 并行推理 |
| `analyze_sensor(data)` | 传感器数据规则分析 |
| `fuse_results(img_result, sensor_result)` | 调用融合算法合并结果 |
| `run_fallback(data)` | 模型不可用时降级为纯规则引擎 |

#### fusion_algorithm（多源数据融合算法）

| 函数 | 功能 |
|------|------|
| `fuse_image_and_sensor(img_score, sen_score, img_ts, sen_ts)` | 时序对齐 (500ms窗口) + 加权融合 (0.6+0.4) + 置信度计算 |
| `rule_engine_analysis(sensor_data)` | 规则引擎: 浊度/COD阈值 + pH范围判断 |

#### model_loader（TensorFlow Lite 模型加载器 — 单例）

| 类/函数 | 功能 |
|------|------|
| `ModelLoader` | 单例模式, 线程安全加载 TFLite 模型 |
| `run_mobilenet_inference(image)` | MobileNetV2 水质分类 (浑浊/藻类/工业污染/正常) |
| `run_unet_inference(image)` | U-Net 污染区域分割 |

#### data_cache（本地 LRU 变种缓存）

| 类/函数 | 功能 |
|------|------|
| `LRUVariantCache` | 热区 LRU + 冷区 FIFO, SQLite 持久化 |
| `put(key, value)` | 写入: 首次进冷区, 二次访问晋升热区 |
| `get(key)` | 读取: 热区命中直接返回, 冷区命中晋升 |
| `upload_pending()` | 网络恢复后按时间戳升序补传 |

#### sensor_reader（Modbus RTU 传感器读取）

| 函数 | 功能 |
|------|------|
| `read_ph()` | 读取 pH 值 (0-14) |
| `read_turbidity()` | 读取浊度 (NTU) |
| `read_cod()` | 读取 COD 化学需氧量 (mg/L) |
| `read_all()` | 三参数批量读取 |

#### mqtt_client（MQTT 通信）

| 函数 | 功能 |
|------|------|
| `connect()` | TLS 1.2+ 连接 MQTT Broker |
| `publish_sensor_data(point_id, data)` | 发布传感器数据 → `/wqi/{id}/sensor_data` |
| `publish_image_analysis(point_id, result)` | 发布图像分析 → `/wqi/{id}/image_analysis` |

---

### 3.3 安全模块

| 类 | 功能 |
|------|------|
| `JwtTokenProvider` | `generateToken()` HS256签名 / `validateToken()` 验证+过期 / `refreshToken()` 滑动刷新 |
| `JwtAuthenticationFilter` | 拦截除 `/api/auth/**` 外所有请求, 从 Header 提取 Bearer Token |
| `SecurityConfig` | BCryptPasswordEncoder, 角色: admin/user/readonly |

---

## 4. 数据库设计

| 表名 | 说明 | 关键字段 |
|------|------|---------|
| `sys_user` | 系统用户 | id, username, password(BCrypt), role(admin/user/readonly) |
| `monitoring_point` | 监测点 | id, name, location, latitude, longitude |
| `water_quality_data` | 水质数据 (按月分区) | id, point_id, turbidity_ntu, cod_value, ph_value, alert_level, confidence |
| `alert_record` | 告警记录 | id, point_id, alert_level, alert_type, push_status, retry_count |
| `user_point_permission` | 用户-监测点权限 | user_id, point_id (多对多) |
| `edge_device` | 边缘设备注册 | device_id, point_id, last_heartbeat |
| `sys_config` | 系统配置 | config_key, config_value |
| `alert_confirmation` | 告警确认 | alert_id, confirmed_by, confirm_time |

---

## 5. API 接口清单

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/login` | No | 用户登录 → JWT Token |
| POST | `/api/auth/logout` | Yes | 用户登出 |
| GET | `/api/monitoring-points` | Yes | 监测点列表 |
| POST | `/api/data/report` | Yes | 边缘端上报水质分析结果 |
| POST | `/api/data/heartbeat` | Yes | 边缘端心跳 |
| GET | `/api/alerts/point/{id}` | Yes | 查询监测点告警记录 |
| POST | `/api/visualization/trend` | Yes | 水质趋势曲线 (ECharts) |
| POST | `/api/visualization/heatmap` | Yes | 热力图聚合数据 |
| POST | `/api/visualization/query` | Yes | 分页历史数据查询 |

---

## 6. 技术栈

| 层级 | 技术 |
|------|------|
| **云端框架** | Java 11 + Spring Boot 2.7.18 + MyBatis Plus 3.5.3.1 |
| **数据库** | MySQL 8.4 (分区表) + Redis (Cache-aside) + Caffeine (Local) |
| **通信** | MQTT (Paho) + WebSocket + gRPC |
| **安全** | JWT (HS256, 24h) + BCrypt (cost=10) + RBAC |
| **边缘端** | Python 3.9 + TensorFlow Lite + OpenCV + Modbus RTU |
| **模型** | MobileNetV2 (分类) + U-Net (分割) |
| **构建** | Maven Wrapper (无需安装Maven) |

---

## 7. 快速开始

### 7.1 环境要求

- JDK 11+ (Amazon Corretto)
- MySQL 8.4
- Python 3.9+ (边缘端)

### 7.2 数据库初始化

```bash
mysql -u root -p < database/schema.sql
```

### 7.3 启动云端后端

```bash
cd cloud-backend
./mvnw clean package -DskipTests
java -jar target/smart-water-quality-2.0.0.jar --spring.profiles.active=local
```

默认账号: `admin` / `123456`  
端口: `8080`

### 7.4 API 测试

```powershell
powershell -ExecutionPolicy Bypass -File cloud-backend/test-api.ps1
```

### 7.5 边缘端 (可选, 需要硬件)

```bash
cd edge-python
pip install -r requirements.txt
python src/main.py --config config/config.yaml
```

---

## 8. 开发团队

- **完成人**: 单智屹
- **版本**: v2.0.0
- **日期**: 2026-05-13

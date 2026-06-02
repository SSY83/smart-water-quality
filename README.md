# 智慧水利 — 基于轻量化深度学习的河道水质智能监测与预警系统

**Smart Water Quality** — Edge-Cloud Collaborative River Water Quality Monitoring & Early Warning System

基于 MobileNetV2 + U-Net 轻量化深度学习模型，结合边缘-云协同架构，实现对浙江河道（苕溪、钱塘江、西湖）水质指标的实时监测与多级预警。**v2.1 — 第3周生产就绪版本**。

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
|  |   融合验证 + 滑动窗口趋势 + 历史对比 + 水质预测 + 规则降级     |  |
|  +--------+-------------------------+---------+---------+--------+  |
|           |                         |         |         |           |
|  +--------v--------+  +------------v--+  +---v---------v-------+   |
|  |DataCollection   |  |AlertPushService| |VisualizationService |   |
|  |异步队列写入DB   |  |联合规则+升级   | |缓存旁路+三重防护    |   |
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
|   +-- migration_week3.sql              # 第3周增量迁移 (3个性能索引)
|
+-- cloud-backend/                      # 云端 Java 后端 (70+ 个源文件)
|   +-- pom.xml                         # Maven 构建配置 (SB 2.7.18, Java 11)
|   +-- setup-mosquitto.ps1             # Mosquitto MQTT Broker 配置启动脚本
|   +-- src/main/java/com/waterquality/
|   |   +-- WaterQualityApplication.java  # 启动类 @EnableAsync @EnableScheduling @EnableCaching
|   |   +-- config/                        # 8 个配置类
|   |   |   +-- CaffeineConfig.java        #   本地缓存配置
|   |   |   +-- RedisConfig.java           #   Redis 条件装配
|   |   |   +-- SmsConfig.java             #   短信Provider条件装配 (第3周)
|   |   |   +-- ThreadPoolConfig.java      #   4个线程池 (sms/db-write/mqtt/retry) + 优雅关闭
|   |   |   +-- WebMvcConfig.java          #   CORS + 拦截器注册 (限流 + JWT)
|   |   |   +-- WebSocketConfig.java       #   WebSocket 端点注册
|   |   |   +-- datasource/
|   |   |       +-- RoutingDataSource.java         #   读写分离动态路由 (第3周)
|   |   |       +-- DataSourceContextHolder.java   #   数据源类型ThreadLocal (第3周)
|   |   |       +-- DataSourceRoutingAspect.java   #   AOP自动读库路由 (第3周)
|   |   |       +-- ReadWriteDataSourceConfig.java #   读写数据源装配 (第3周)
|   |   |       +-- DataSourceType.java            #   读写枚举 (第3周)
|   |   +-- constant/
|   |   |   +-- ErrorCode.java             #   28个错误码定义
|   |   +-- controller/                    # 6 个 REST 控制器
|   |   |   +-- AlertController.java       #   GET/POST 告警查询/确认/解除/统计/联合评估
|   |   |   +-- AuthController.java        #   POST /api/auth/login,logout
|   |   |   +-- DataController.java        #   POST 数据上报/心跳 + GET 趋势/对比/预测/融合验证
|   |   |   +-- MonitoringPointController  #   GET /api/monitoring-points
|   |   |   +-- SystemMonitorController    #   GET /api/system/dashboard (第3周)
|   |   |   +-- VisualizationController    #   POST 趋势/热力图/查询 + GET 缓存统计/预热
|   |   +-- dto/                           # 6 个数据传输对象
|   |   +-- entity/                        # 7 个实体类 (MyBatis Plus 映射)
|   |   +-- enums/
|   |   +-- exception/                     # 3 个异常处理类
|   |   +-- filter/
|   |   |   +-- SecurityFilter.java        #   SQL注入/XSS防护: 正则检测+参数转义 (第3周)
|   |   |   +-- TraceIdFilter.java         #   全链路追踪: UUID注入MDC+响应头 (第3周)
|   |   +-- mapper/                        # 6 个 MyBatis Plus Mapper
|   |   +-- mqtt/                          # MQTT 通信模块
|   |   +-- security/                      # 安全模块 (第3周增强)
|   |   |   +-- JwtAuthenticationFilter.java # JWT 请求拦截 + 黑名单校验
|   |   |   +-- JwtBlacklist.java           #   JWT 黑名单: Caffeine缓存24h (第3周)
|   |   |   +-- JwtTokenProvider.java       #   HS256 令牌生成/验证/过期查询
|   |   |   +-- RateLimitInterceptor.java   #   Guava限流: 全局200/s+每用户20/s (第3周)
|   |   |   +-- SecurityConfig.java         #   BCrypt 密码编码 + RBAC 配置
|   |   +-- service/                       # 7 个业务服务 + SMS子包
|   |   |   +-- sms/
|   |   |   |   +-- SmsProvider.java        #   Provider接口 (第3周)
|   |   |   |   +-- AliyunSmsProvider.java  #   阿里云短信实现 (第3周)
|   |   |   |   +-- MockSmsProvider.java    #   模拟短信实现 (第3周)
|   |   |   +-- AlertPushService.java      #   告警推送: 联合规则+升级策略+多级推送+统计报表
|   |   |   +-- AuthService.java           #   认证服务: 登录/登出+暴力破解防护 (第3周)
|   |   |   +-- DataCollectionService.java #   数据采集: 批量写入50条/200ms批次 (第3周)
|   |   |   +-- IntelligentAnalysisService #   智能分析: 融合验证+趋势检测+历史对比+水质预测+规则降级
|   |   |   +-- SmsService.java            #   短信服务: 多提供商架构+熔断保护 (第3周)
|   |   |   +-- VisualizationService.java  #   可视化: 趋势/热力图/分页 + 缓存三重防护
|   |   +-- websocket/
|   |       +-- AlertWebSocketHandler.java #   WebSocket: JWT认证+心跳+广播/单播+连接计数
|   +-- src/main/proto/
|   |   +-- water_quality_message.proto    #   Protobuf 消息定义
|   +-- src/main/resources/
|   |   +-- application.yml                #   完整配置 (MySQL+Redis+MQTT+WebSocket+Actuator)
|   |   +-- application-local.yml          #   本地轻量配置 (仅MySQL+WebSocket+SMS Mock)
|   |   +-- logback-spring.xml             #   结构化日志: JSON格式+traceId+3环境profile (第3周)
|   |   +-- filebeat.yml                   #   ELK日志采集: Filebeat→Elasticsearch (第3周)
|   |   +-- static/
|   |       +-- ws-test.html               #   WebSocket 实时告警测试页面
|   +-- src/test/java/com/waterquality/
|       +-- service/
|       |   +-- IntelligentAnalysisServiceTest.java  # 10个单元测试
|       |   +-- AlertPushServiceTest.java            # 9个单元测试
|       +-- controller/
|           +-- ApiIntegrationTest.java              # 13个API集成测试
|
+-- edge-python/                          # 边缘端 Python 框架 (14个源文件)
|   +-- requirements.txt                  #   Python 依赖
|   +-- config/
|   |   +-- config.yaml                   #   边缘端配置 (传感器/摄像头/MQTT/模型)
|   +-- src/
|   |   +-- main.py                       #   主入口: 多线程调度+信号处理+正常数据上传 (第3周)
|   |   +-- video_capture.py              #   视频采集: OpenCV 帧读取 + 定时截图
|   |   +-- sensor_reader.py              #   传感器读取: Modbus RTU (pH/浊度/COD)
|   |   +-- model_loader.py               #   模型加载: TFLite 单例 MobileNetV2+U-Net
|   |   +-- water_quality_analyzer.py     #   分析主控: 并行推理 + 降级监控 + 规则回退
|   |   +-- fusion_algorithm.py           #   融合算法: 时序对齐 + 加权投票 (0.6+0.4)
|   |   +-- mqtt_client.py                #   MQTT客户端: TLS+JSON+QoS1+重连回调 (第3周)
|   |   +-- data_cache.py                 #   本地缓存: LRU变种+SQLite+UNIQUE约束+断点续传 (第3周)
|   |   +-- alert_push.py                 #   本地告警: 边端触发 + 上传
|   |   +-- exceptions.py                 #   异常定义: SensorError/ModelError/MqttError
|   +-- tests/
|       +-- test_fusion.py                #   融合算法 6 个单元测试 (全部通过)
|       +-- test_model_inference.py       #   模型推理 11 个集成测试 (全部通过)
|       +-- mqtt_simulator.py             #   MQTT 边缘设备模拟器 (4种告警场景)
|
+-- tests/                                # 全链路测试 (第3周)
|   +-- e2e_test.ps1                      #   端到端验证: 启动→API→安全→清理 10项
|   +-- stress_test.ps1                   #   压力测试: JMeter自动执行+HTML报告
|   +-- jmeter/
|       +-- water_quality_stress_test.jmx #   6线程组压力测试计划 (登录/查询/可视化/告警/仪表盘/数据上报)
```

---

## 3. 核心类与函数定义

### 3.1 云端后端 — Service 层

#### IntelligentAnalysisService（智能分析服务）

| 方法                                         | 功能                                                       |
| -------------------------------------------- | ---------------------------------------------------------- |
| `processAnalysisResult(AnalysisResult)`      | 接收边缘端分析结果 → 存储数据 → 触发告警推送               |
| `verifyFusionScore(imageScore, sensorScore)` | 云端融合验证: `image*0.6 + sensor*0.4`                     |
| `determineAlertLevel(finalScore)`            | 异常等级判定: <0.4正常 / <0.7轻度 / <0.9中度 / ≥0.9重度    |
| `ruleBasedAnalysis(pointId, sensorData)`     | 降级模式: 基于传感器阈值规则判断 (浊度/COD/pH)             |
| `detectTrendAnomaly(pointId, windowMinutes)` | **新增**: 滑动窗口异常趋势检测, 前后段均值比较, 突变点检测 |
| `compareWithHistory(pointId, ...)`           | **新增**: 与30天历史基线Z-score偏离度对比分析              |
| `predictWaterQuality(pointId, minutesAhead)` | **新增**: 线性回归水质预测, R²置信度评估                   |

#### AlertPushService（告警推送服务）

| 方法                                          | 功能                                                       |
| --------------------------------------------- | ---------------------------------------------------------- |
| `pushAlert(AnalysisResult)`                   | 多级推送: 轻度→仅入库, 中度→+WebSocket, 重度→+短信         |
| `evaluateCombinedAlert(turbidity, cod, ph)`   | **新增**: 多参数联合告警规则, 三参数同时超标自动升级       |
| `checkAlertEscalation(pointId, currentLevel)` | **新增**: 告警升级策略, 30分钟内频繁告警自动升级           |
| `determineChannels(alertLevel)`               | 根据异常等级选择推送渠道                                   |
| `getAlertStatistics(pointIds, start, end)`    | **新增**: 多点聚合告警统计报表                             |
| `getPointAlertStats(pointId, days)`           | **新增**: 单监测点告警统计 (确认率/等级分布)               |
| `retryFailedPushes()`                         | 定时扫描重试队列 (@Scheduled 5min), 指数退避 1/2/4/8/16min |
| `executeRetry(PushTask)`                      | 单次重试执行, 超5次标记 failed                             |

#### VisualizationService（可视化服务）

| 方法                                   | 功能                                            |
| -------------------------------------- | ----------------------------------------------- |
| `getTrendData(QueryParams)`            | ECharts 趋势数据: Redis 缓存旁路 + 互斥锁防击穿 |
| `getHeatmapData(pointIds, start, end)` | 热力图聚合: 各监测点平均异常分数                |
| `queryData(QueryParams)`               | 分页历史查询                                    |
| `warmUpCache(hotPointIds)`             | **新增**: 缓存预热, 批量预加载热点数据          |
| `getCacheStats()`                      | **新增**: 缓存统计 (Redis状态/Key数量/TTL配置)  |

---

### 3.2 边缘端 Python — 核心模块

#### water_quality_analyzer（水质分析主控）

| 类/函数                                   | 功能                                      |
| ----------------------------------------- | ----------------------------------------- |
| `WaterQualityAnalyzer`                    | 主控类: 管理模型加载/推理调度/结果融合    |
| `analyze_frame(frame)`                    | 对视频帧进行 MobileNetV2 + U-Net 并行推理 |
| `analyze_sensor(data)`                    | 传感器数据规则分析                        |
| `fuse_results(img_result, sensor_result)` | 调用融合算法合并结果                      |
| `run_fallback(data)`                      | 模型不可用时降级为纯规则引擎              |

#### fusion_algorithm（多源数据融合算法）

| 函数                                | 功能                                                   |
| ----------------------------------- | ------------------------------------------------------ |
| `fuse_image_and_sensor(...)`        | 时序对齐 (500ms窗口) + 加权融合 (0.6+0.4) + 置信度计算 |
| `rule_engine_analysis(sensor_data)` | 规则引擎: 浊度/COD阈值 + pH范围判断                    |

#### model_loader（TensorFlow Lite 模型加载器 — 单例）

| 类/函数                          | 功能                                           |
| -------------------------------- | ---------------------------------------------- |
| `ModelLoader`                    | 单例模式, 线程安全加载 TFLite 模型             |
| `run_mobilenet_inference(image)` | MobileNetV2 水质分类 (浑浊/藻类/工业污染/正常) |
| `run_unet_inference(image)`      | U-Net 污染区域分割                             |

#### data_cache（本地 LRU 变种缓存）

| 类/函数            | 功能                                 |
| ------------------ | ------------------------------------ |
| `LRUVariantCache`  | 热区 LRU + 冷区 FIFO, SQLite 持久化  |
| `put(key, value)`  | 写入: 首次进冷区, 二次访问晋升热区   |
| `get(key)`         | 读取: 热区命中直接返回, 冷区命中晋升 |
| `upload_pending()` | 网络恢复后按时间戳升序补传           |

---

### 3.3 安全模块

| 类                        | 功能                                                                                  |
| ------------------------- | ------------------------------------------------------------------------------------- |
| `JwtTokenProvider`        | `generateToken()` HS256签名 / `validateToken()` 验证+过期 / `refreshToken()` 滑动刷新 |
| `JwtAuthenticationFilter` | 拦截除 `/api/auth/**` 外所有请求, 从 Header 提取 Bearer Token                         |
| `SecurityConfig`          | BCryptPasswordEncoder, 角色: admin/user/readonly                                      |

---

## 4. 数据库设计

| 表名                    | 说明                | 关键字段                                                                  |
| ----------------------- | ------------------- | ------------------------------------------------------------------------- |
| `sys_user`              | 系统用户            | id, username, password(BCrypt), role(admin/user/readonly)                 |
| `monitoring_point`      | 监测点              | id, name, longitude, latitude, contact_phone, status                      |
| `water_quality_data`    | 水质数据 (按月分区) | id, point_id, turbidity_ntu, cod_value, ph_value, alert_level, confidence |
| `alert_record`          | 告警记录            | id, point_id, alert_level, alert_type, push_status, retry_count           |
| `user_point_permission` | 用户-监测点权限     | user_id, point_id (多对多)                                                |
| `edge_device`           | 边缘设备注册        | device_id, point_id, last_heartbeat                                       |
| `sys_config`            | 系统配置            | config_key, config_value                                                  |
| `alert_confirmation`    | 告警确认            | alert_id, confirmed_by, confirm_time                                      |

---

## 5. API 接口清单

### 认证与基础

| Method | Path                     | Auth | Description          |
| ------ | ------------------------ | ---- | -------------------- |
| POST   | `/api/auth/login`        | No   | 用户登录 → JWT Token |
| POST   | `/api/auth/logout`       | Yes  | 用户登出             |
| GET    | `/api/monitoring-points` | Yes  | 监测点列表           |

### 数据上报

| Method | Path                  | Auth | Description            |
| ------ | --------------------- | ---- | ---------------------- |
| POST   | `/api/data/report`    | Yes  | 边缘端上报水质分析结果 |
| POST   | `/api/data/heartbeat` | Yes  | 边缘端心跳             |

### 智能分析（第2周新增）

| Method | Path                                   | Auth | Description          |
| ------ | -------------------------------------- | ---- | -------------------- |
| GET    | `/api/data/analysis/trend/{pointId}`   | Yes  | 滑动窗口异常趋势检测 |
| GET    | `/api/data/analysis/compare/{pointId}` | Yes  | 与历史基线对比分析   |
| GET    | `/api/data/analysis/predict/{pointId}` | Yes  | 水质等级预测         |
| POST   | `/api/data/analysis/verify-fusion`     | Yes  | 云端融合验证         |

### 告警管理

| Method | Path                            | Auth | Description          |
| ------ | ------------------------------- | ---- | -------------------- |
| GET    | `/api/alerts/point/{pointId}`   | Yes  | 查询监测点告警记录   |
| POST   | `/api/alerts/{alertId}/confirm` | Yes  | 确认告警             |
| POST   | `/api/alerts/{alertId}/dismiss` | Yes  | 解除/关闭告警        |
| GET    | `/api/alerts/evaluate-combined` | Yes  | 多参数联合规则评估   |
| POST   | `/api/alerts/statistics`        | Yes  | 多点聚合告警统计报表 |
| GET    | `/api/alerts/stats/{pointId}`   | Yes  | 单点告警统计         |
| GET    | `/api/alerts/pending-retry`     | Yes  | 查询待重试告警       |

### 系统监控（第3周新增）

| Method | Path                    | Auth | Description                                  |
| ------ | ----------------------- | ---- | -------------------------------------------- |
| GET    | `/api/system/dashboard` | Yes  | 系统仪表盘 (内存/线程/GC/WebSocket/运行时长) |

### 可视化

| Method | Path                              | Auth | Description            |
| ------ | --------------------------------- | ---- | ---------------------- |
| POST   | `/api/visualization/trend`        | Yes  | 水质趋势曲线 (ECharts) |
| POST   | `/api/visualization/heatmap`      | Yes  | 热力图聚合数据         |
| POST   | `/api/visualization/query`        | Yes  | 分页历史数据查询       |
| GET    | `/api/visualization/cache-stats`  | Yes  | 缓存统计信息           |
| POST   | `/api/visualization/cache-warmup` | Yes  | 手动缓存预热           |

### WebSocket

| Path                                   | Description                           |
| -------------------------------------- | ------------------------------------- |
| `ws://host:8080/ws/alerts?token={JWT}` | 实时告警推送 (JWT认证通过URL参数传递) |

### 前端 SPA（第3周新增）

| 页面           | 路由            | 功能                                           |
| -------------- | --------------- | ---------------------------------------------- |
| **登录页**     | `/`             | JWT 认证登录，Token 自动存储                   |
| **仪表盘**     | `dashboard`     | 监测点统计 + 系统资源 + 最近告警               |
| **告警管理**   | `alerts`        | 告警列表查询/确认/解除 + WebSocket实时推送弹窗 |
| **数据可视化** | `visualization` | ECharts 趋势曲线 + 热力图, 监测点选择          |
| **监测点管理** | `monitoring`    | 监测点列表展示（名称/坐标/状态）               |
| **系统监控**   | `system`        | 堆内存/线程/GC/连接数/运行时长                 |
| **告警统计**   | `statistics`    | 总告警数/确认率/等级分布                       |

技术栈: Vue 3 + Element Plus + ECharts 5, 纯静态 SPA 由 Spring Boot 直接服务

---

## 6. 缓存防护体系（第2周新增）

| 机制         | 实现                              | 说明                              |
| ------------ | --------------------------------- | --------------------------------- |
| **缓存穿透** | 空值缓存 `NULL_VALUE_MARKER`      | 查询不存在的数据缓存空值, TTL=60s |
| **缓存击穿** | 互斥锁 `ReentrantLock` + 双重检查 | 热点数据过期时仅一个线程重建缓存  |
| **缓存雪崩** | 随机TTL偏移 `±60s`                | 避免大量缓存同时过期              |
| **缓存预热** | `warmUpCache()`                   | 系统启动后预加载热点数据          |

---

## 7. 告警规则体系（第2周新增）

| 机制               | 说明                                               |
| ------------------ | -------------------------------------------------- |
| **多参数联合规则** | 浊度+COD+pH 三参数组合判断, 多参数同时超标自动升级 |
| **告警升级策略**   | 30分钟内同一监测点频繁告警 → 自动提升告警等级      |
| **三级推送渠道**   | 轻度→仅入库, 中度→+WebSocket, 重度→+短信           |
| **指数退避重试**   | 1/2/4/8/16 分钟间隔, 最多5次, 熔断保护             |

---

## 8. 第3周新增特性（生产就绪）

### 8.1 安全加固

| 特性                 | 实现                                           | 说明                                  |
| -------------------- | ---------------------------------------------- | ------------------------------------- |
| **JWT 黑名单**       | Caffeine 缓存 24h, 登出时加入黑名单            | 已登出 Token 立即失效, 拦截器校验     |
| **接口限流**         | Guava RateLimiter: 全局200/s + 每用户20/s      | 超限返回 429 Too Many Requests        |
| **暴力破解防护**     | 5次错误密码 → 账号锁定15分钟                   | 内存计数器, 登录成功自动清除          |
| **API 路径优化**     | JWT 过滤器排除登录/注册/Actuator/WebSocket     | 公开端点无需 Token                    |
| **SQL 注入防护**     | SecurityFilter: 请求参数正则检测 + 响应拦截    | 拦截 SELECT/UNION/DROP/-- 等注入模式  |
| **XSS 跨站脚本防护** | SecurityFilter: HTML标签转义 + js协议过滤      | 请求参数自动转义 < > " ' & 等特殊字符 |
| **HTTPS/TLS 加密**   | application.yml SSL 配置: PKCS12 证书 + HTTP/2 | 环境变量 SSL_ENABLED 控制启用         |

### 8.2 日志与监控

| 特性              | 实现                                                               |
| ----------------- | ------------------------------------------------------------------ |
| **结构化日志**    | logback-spring.xml: JSON格式文件日志, 按local/dev/prod分环境       |
| **全链路追踪**    | TraceIdFilter: UUID 注入 MDC, 响应头 X-Trace-Id 返回               |
| **系统仪表盘**    | GET /api/system/dashboard: 堆内存/线程/GC/WebSocket连接数/运行时长 |
| **Actuator 端点** | 暴露 health/info/metrics/env/loggers/threaddump                    |
| **ELK 日志采集**  | filebeat.yml: 收集JSON日志发送至Elasticsearch                      | 多行合并 + 自动索引 + SSL支持 |

### 8.3 性能优化

| 特性               | 说明                                                                      |
| ------------------ | ------------------------------------------------------------------------- |
| **批量写入**       | DataCollectionService: 50条或200ms批次批量INSERT, HikariCP连接池调优      |
| **数据库索引**     | idx_point_alert_score, idx_retry_status_time, idx_device_status_heartbeat |
| **缓存启用**       | @EnableCaching + Caffeine 本地缓存                                        |
| **线程池优雅关闭** | mqttExecutor/dbWriteExecutor/retryExecutor 均配置 awaitTermination        |
| **@EnableCaching** | 激活 Spring Cache 抽象 (之前缺失)                                         |
| **数据库读写分离** | RoutingDataSource + AOP: 写走主库, 读自动路由从库                         | select*/get*/query*/find* 方法 → 读库, 其余 → 写库 |

### 8.4 短信平台对接

| 特性                 | 说明                                                                        |
| -------------------- | --------------------------------------------------------------------------- |
| **多提供商架构**     | SmsProvider 接口 + AliyunSmsProvider + MockSmsProvider + SmsConfig 条件装配 |
| **手机号查找链**     | MonitoringPoint.contactPhone → User 表管理员 → 默认号码                     |
| **重度告警立即短信** | SEVERE 告警直接异步推送, 不等重试调度周期                                   |
| **熔断保护**         | 连续失败5次 → 熔断器打开600秒, 半开状态自动恢复                             |

### 8.5 边缘端增强

| 特性             | 说明                                                                      |
| ---------------- | ------------------------------------------------------------------------- |
| **正常数据上传** | 非告警数据也上传云端 (sensor_data topic), 不再丢弃                        |
| **断点续传修复** | SQLite snake_case → MQTT camelCase 键名映射, 5次连续失败才停              |
| **重复数据防护** | water_quality_data 表 UNIQUE 索引 (point_id, timestamp), INSERT OR IGNORE |
| **重连回调**     | MQTT 重连成功 → 立即触发断点续传, 不等心跳周期                            |

### 8.6 全链路测试

| 特性                  | 说明                                                                  |
| --------------------- | --------------------------------------------------------------------- |
| **E2E 测试**          | PowerShell 一键脚本: 编译→启动→健康检查→API测试→安全验证→清理         |
| **压力测试**          | JMeter 6线程组: 登录/数据查询/可视化/告警统计/系统仪表盘/数据上报模拟 |
| **P95 响应 + 吞吐量** | 自动生成 HTML 报告, 汇总成功率/P95延迟/吞吐量                         |

---

## 9. 测试覆盖（v2.1.0 实际运行验证）

> **验证日期**: 2026-06-01 | **环境**: Windows 11 + OpenJDK 11.0.31 + Python 3.13

### 9.1 单元测试执行结果

| 测试类型 | 用例数 | 通过 | 失败 | 通过率 | 执行时间 |
|----------|--------|------|------|--------|----------|
| Python 融合算法 (`test_fusion.py`) | 6 | 6 | 0 | 100% | 0.06s |
| Python 模型推理 (`test_model_inference.py`) | 11 | 11 | 0 | 100% | 31.44s |
| Java 智能分析服务 (`IntelligentAnalysisServiceTest`) | 10 | 10 | 0 | 100% | 0.06s |
| Java 告警推送服务 (`AlertPushServiceTest`) | 9 | 9 | 0 | 100% | 0.72s |
| **合计** | **36** | **36** | **0** | **100%** | **32.28s** |

### 9.2 API 端点运行时验证（Demo 模式实跑）

| API 端点 | 方法 | 验证结果 | 说明 |
|----------|------|---------|------|
| `/api/auth/login` | POST | ✅ | JWT HS256 签发成功, 24h 过期 |
| `/api/data/report` | POST | ✅ | 数据入库 + 告警自动生成 (ID=605) |
| `/api/data/analysis/trend/1` | GET | ✅ | 趋势检测: 快速恶化, 变化率 62.6% |
| `/api/data/analysis/predict/1` | GET | ✅ | 预测: 评分 0.46, 23 数据点 |
| `/api/data/analysis/compare/1` | GET | ✅ | 基线对比: 显著偏离 (3σ) |
| `/api/data/analysis/verify-fusion` | POST | ✅ | 融合: 0.6×0.88+0.4×0.92=0.896 |
| `/api/alerts/point/1` | GET | ✅ | 50 条告警分页返回 |
| `/api/alerts/stats/1` | GET | ✅ | 7 天统计: 201 条, 三级分布正常 |
| `/api/monitoring-points` | GET | ✅ | 3 个监测点返回 |
| `/api/system/dashboard` | GET | ✅ | JVM 3.16%, 27 线程, 无 Old GC |
| `/api/visualization/cache-stats` | GET | ✅ | 缓存状态正常 |

### 9.3 系统运行时仪表盘数据

| 指标 | 数值 | 评价 |
|------|------|------|
| JVM 堆内存 | 126MB / 4002MB (3.16%) | 极低 |
| 活跃线程 | 27 (峰值 29, 守护 20) | 正常 |
| G1 Young GC | 13 次 / 98ms | 高效 |
| G1 Old GC | 0 次 | 无 Full GC |
| 告警数据 (7天) | 201 条 (轻 68% / 中 20% / 重 11%) | 分布合理 |
| 安全拦截 (无Token) | HTTP 401 | 正常工作 |

### 9.4 压力测试

| 类型 | 文件 | 线程组 | 状态 |
|------|------|--------|------|
| JMeter 压力测试 | `tests/jmeter/water_quality_stress_test.jmx` | 6 (登录/查询/可视化/告警/仪表盘/上报) | ✅ 可执行 |
| E2E 全链路 | `tests/e2e_test.ps1` | 10 项 (编译→启动→API→安全→清理) | ✅ 可执行 |

---

## 10. 技术栈

| 层级         | 技术                                                        |
| ------------ | ----------------------------------------------------------- |
| **云端框架** | Java 11 + Spring Boot 2.7.18 + MyBatis Plus 3.5.3.1         |
| **数据库**   | MySQL 8.4 (分区表) + Redis (Cache-aside) + Caffeine (Local) |
| **通信**     | MQTT (Paho) + WebSocket + Protobuf                          |
| **前端**     | Vue 3 + Element Plus + ECharts 5 (SPA)                      |
| **安全**     | JWT (HS256, 24h) + BCrypt (cost=10) + RBAC                  |
| **边缘端**   | Python 3.9 + TensorFlow Lite + OpenCV + Modbus RTU          |
| **模型**     | MobileNetV2 (分类) + U-Net (分割)                           |
| **测试**     | JUnit 5 + Mockito + Spring MockMvc + unittest               |
| **构建**     | Maven Wrapper (无需安装Maven)                               |

---

## 11. 快速开始

### 方式一：演示模式（推荐，零依赖）

**仅需 Java 11+，无需 MySQL / MQTT / Redis**

```bash
# 方式A: 双击 start.bat (Windows)
start.bat

# 方式B: PowerShell 命令行
powershell -ExecutionPolicy Bypass -File start.ps1

# 方式C: 指定端口
powershell -ExecutionPolicy Bypass -File start.ps1 -Mode demo -Port 9090
```

启动后自动打开浏览器 → 登录 `admin` / `admin123`。H2 内存数据库预置 7 天模拟数据。

### 方式二：本地开发模式（需 MySQL）

```bash
# 1. 初始化数据库
mysql -u root -p < database/schema.sql

# 2. 一键启动 (指定 local 模式)
powershell -ExecutionPolicy Bypass -File start.ps1 -Mode local

# 或手动:
cd cloud-backend
./mvnw clean package -DskipTests
java -jar target/smart-water-quality-2.1.0.jar --spring.profiles.active=local
```

### 方式三：完整模式（需 MySQL + MQTT）

```bash
# 先启动 MQTT Broker
powershell -ExecutionPolicy Bypass -File cloud-backend/setup-mosquitto.ps1

# 一键启动
powershell -ExecutionPolicy Bypass -File start.ps1 -Mode full
```

---

### 环境变量（可选）

| 变量 | 默认值 | 说明 |
|---|---|---|
| MYSQL_HOST | localhost | MySQL 地址 |
| MYSQL_PORT | 3306 | MySQL 端口 |
| MYSQL_USER | root | MySQL 用户 |
| MYSQL_PASSWORD | root | MySQL 密码 |
| MQTT_HOST | (空) | MQTT Broker 地址 |

---

### 运行测试

```bash
# Java 单元测试
cd cloud-backend
./mvnw test -Dtest="IntelligentAnalysisServiceTest,AlertPushServiceTest"

# Python 边缘端测试
cd edge-python
python tests/test_fusion.py
python tests/test_model_inference.py --benchmark

# E2E 全链路测试
powershell -ExecutionPolicy Bypass -File tests/e2e_test.ps1
```

---

## 12. 已知问题

| 编号 | 问题 | 严重度 | 状态 |
|------|------|--------|------|
| 1 | `logback-spring.xml` 缺少 `demo` profile: Demo 模式下日志静默（系统正常运行但不输出日志） | ⚠️ 中 | 待修复 |

---

## 13. 运行测试报告

完整运行测试报告（含全部 API 验证数据和性能分析）：

- 📄 **[水质监测系统运行报告.md](水质监测系统运行报告.md)** — 含 53 项测试数据、API 实跑结果、性能评估、安全验证

---

## 14. 开发团队

- **完成人**: 单智屹、朱益坤
- **版本**: v2.1.0
- **完成日期**: 2026-05-14
- **测试验证**: 2026-06-01
- **GitHub**: https://github.com/SSY83/smart-water-quality

### 开发进度

| 阶段  | 时间        | 内容                                                                     | 状态     |
| ----- | ----------- | ------------------------------------------------------------------------ | -------- |
| 第1周 | 5.10 - 5.16 | 系统架构/数据库/脚手架/实体/认证/CRUD/融合算法                           | ✅ 已完成 |
| 第2周 | 5.17 - 5.23 | MQTT联调/WebSocket推送/智能分析增强/告警规则/Redis缓存/模型测试/单元测试 | ✅ 已完成 |
| 第3周 | 5.24 - 5.30 | 全链路联调/短信对接/断点续传/性能优化/安全加固/日志监控/压力测试         | ✅ 已完成 |

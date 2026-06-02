# Mosquitto MQTT Broker 配置与启动脚本 (Windows)
# 使用方法: .\setup-mosquitto.ps1

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Mosquitto MQTT Broker 配置脚本" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

$mosquittoPath = "C:\Program Files\mosquitto"
$configPath = "$mosquittoPath\mosquitto.conf"
$logPath = "$mosquittoPath\log"

# 检查是否已安装
if (-not (Test-Path $mosquittoPath)) {
    Write-Host "Mosquitto 未安装。请从 https://mosquitto.org/download/ 下载安装。" -ForegroundColor Yellow
    Write-Host "或使用 winget 安装: winget install EclipseFoundation.Mosquitto" -ForegroundColor Yellow
    exit 1
}

Write-Host "[1/3] Mosquitto 已安装在: $mosquittoPath" -ForegroundColor Green

# 创建日志目录
if (-not (Test-Path $logPath)) {
    New-Item -ItemType Directory -Path $logPath -Force | Out-Null
}

Write-Host "[2/3] 生成配置文件..." -ForegroundColor Green

$config = @"
# Mosquitto 智能水利系统配置
listener 1883
protocol mqtt

# 日志配置
log_dest file $logPath\mosquitto.log
log_type all
connection_messages true

# 允许匿名连接 (开发环境)
allow_anonymous true

# 持久化配置
persistence true
persistence_location $mosquittoPath\data

# 最大连接数
max_connections 1000

# 消息大小限制
message_size_limit 1048576

# ============== TLS 配置 (生产环境启用) ==============
# listener 8883
# protocol mqtt
# cafile $mosquittoPath\certs\ca.crt
# certfile $mosquittoPath\certs\server.crt
# keyfile $mosquittoPath\certs\server.key
# tls_version tlsv1.2
# require_certificate true
"@

Set-Content -Path $configPath -Value $config -Encoding UTF8

Write-Host "[3/3] 配置文件已生成: $configPath" -ForegroundColor Green
Write-Host ""

# 询问是否启动
Write-Host "启动 Mosquitto Broker?" -ForegroundColor Yellow
Write-Host "  手动启动命令: mosquitto -c `"$configPath`" -v" -ForegroundColor White
Write-Host "  或执行: net start mosquitto" -ForegroundColor White
Write-Host ""

try {
    # 尝试通过服务启动
    Start-Service mosquitto -ErrorAction Stop
    Write-Host "Mosquitto 服务已启动 (端口 1883)" -ForegroundColor Green
} catch {
    Write-Host "无法通过服务启动，尝试直接启动..." -ForegroundColor Yellow
    Start-Process -FilePath "$mosquittoPath\mosquitto.exe" -ArgumentList "-c `"$configPath`" -v" -NoNewWindow
}

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  测试连接:" -ForegroundColor White
Write-Host "    cd ..\edge-python\tests" -ForegroundColor White
Write-Host "    python mqtt_simulator.py --scenario normal" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Cyan

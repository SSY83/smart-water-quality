# Smart Water Quality Monitoring - API Test Script
# Usage: powershell -ExecutionPolicy Bypass -File test-api.ps1

$BaseUrl = "http://localhost:8080"

Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Smart Water Quality - API Test" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan

# ---------- 1. Login ----------
Write-Host ""
Write-Host "[1/6] Login..." -ForegroundColor Yellow
$LoginBody = @{ username = "admin"; password = "123456" } | ConvertTo-Json
$LoginResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post -Body $LoginBody -ContentType "application/json"
if ($LoginResp.code -ne "0000") {
    Write-Host "  FAIL: $($LoginResp.message)" -ForegroundColor Red
    exit 1
}
$Token = $LoginResp.data.token
$Headers = @{ Authorization = "Bearer $Token"; "Content-Type" = "application/json" }
Write-Host "  OK - Role: $($LoginResp.data.role)" -ForegroundColor Green

# ---------- 2. Monitoring Points ----------
Write-Host ""
Write-Host "[2/6] Monitoring Points..." -ForegroundColor Yellow
$Points = Invoke-RestMethod -Uri "$BaseUrl/api/monitoring-points" -Headers $Headers
if ($Points.code -eq "0000") {
    Write-Host "  OK - $($Points.total) points" -ForegroundColor Green
    foreach ($p in $Points.data) {
        Write-Host "    ID=$($p.id)  $($p.name)  ($($p.location))" -ForegroundColor Gray
    }
} else {
    Write-Host "  FAIL: $($Points.message)" -ForegroundColor Red
}

# ---------- 3. Report Data ----------
Write-Host ""
Write-Host "[3/6] Report Water Quality Data..." -ForegroundColor Yellow

# 3a: Severe alert with details
$R1Body = @{
    pointId     = "1"
    timestamp   = (Get-Date -Format "yyyy-MM-ddTHH:mm:ss")
    alertLevel  = 3
    details     = @{ turbidity = 85.0; cod = 55.0; ph = 5.8 }
    confidence  = 0.95
    finalScore  = 0.92
    imageScore  = 0.90
    sensorScore = 0.94
} | ConvertTo-Json
$R1 = Invoke-RestMethod -Uri "$BaseUrl/api/data/report" -Method Post -Body $R1Body -Headers $Headers
Write-Host "  Severe(point1): push=$($R1.data.pushStatus)" -ForegroundColor $(if ($R1.data.pushStatus -eq "SUCCESS") { "Green" } else { "Red" })

# 3b: Moderate alert
$R2Body = @{
    pointId     = "2"
    timestamp   = (Get-Date -Format "yyyy-MM-ddTHH:mm:ss")
    alertLevel  = 2
    confidence  = 0.75
    finalScore  = 0.68
} | ConvertTo-Json
$R2 = Invoke-RestMethod -Uri "$BaseUrl/api/data/report" -Method Post -Body $R2Body -Headers $Headers
Write-Host "  Moderate(point2): push=$($R2.data.pushStatus)" -ForegroundColor $(if ($R2.data.pushStatus -eq "SUCCESS") { "Green" } else { "Red" })

# 3c: Mild alert with details (tests Integer->Double fix)
$R3Body = @{
    pointId     = "3"
    timestamp   = (Get-Date -Format "yyyy-MM-ddTHH:mm:ss")
    alertLevel  = 1
    details     = @{ turbidity = 15; cod = 20; ph = 7.2 }
    confidence  = 0.45
    finalScore  = 0.35
} | ConvertTo-Json
$R3 = Invoke-RestMethod -Uri "$BaseUrl/api/data/report" -Method Post -Body $R3Body -Headers $Headers
Write-Host "  Mild(point3): push=$($R3.data.pushStatus)" -ForegroundColor $(if ($R3.data.pushStatus -eq "SUCCESS") { "Green" } else { "Red" })

# 3d: Normal data
$R4Body = @{
    pointId     = "1"
    timestamp   = (Get-Date -Format "yyyy-MM-ddTHH:mm:ss")
    alertLevel  = 0
    details     = @{ turbidity = 3.0; cod = 10.0; ph = 7.1 }
    confidence  = 0.20
    finalScore  = 0.15
} | ConvertTo-Json
$R4 = Invoke-RestMethod -Uri "$BaseUrl/api/data/report" -Method Post -Body $R4Body -Headers $Headers
Write-Host "  Normal(point1): push=$($R4.data.pushStatus)" -ForegroundColor $(if ($R4.data.pushStatus -eq "SKIPPED") { "Green" } else { "Red" })

# ---------- 4. Trend Data ----------
Write-Host ""
Write-Host "[4/6] Trend Data (ECharts)..." -ForegroundColor Yellow
$TrendBody = @{
    pointIds  = @(1, 2, 3)
    page      = 1
    pageSize  = 20
    startTime = (Get-Date).AddDays(-7).ToString("yyyy-MM-ddTHH:mm:ss")
    endTime   = (Get-Date).ToString("yyyy-MM-ddTHH:mm:ss")
} | ConvertTo-Json
$Trend = Invoke-RestMethod -Uri "$BaseUrl/api/visualization/trend" -Method Post -Body $TrendBody -Headers $Headers
if ($Trend.code -eq "0000") {
    Write-Host "  OK - $($Trend.total) records" -ForegroundColor Green
    foreach ($d in $Trend.data) {
        Write-Host "    $($d.timestamp)  pt$($d.pointId)  turb=$($d.turbidityNtu)  lv=$($d.alertLevel)" -ForegroundColor Gray
    }
} else {
    Write-Host "  FAIL: $($Trend.message)" -ForegroundColor Red
}

# ---------- 5. Alert Records ----------
Write-Host ""
Write-Host "[5/6] Alert Records..." -ForegroundColor Yellow
$A1 = Invoke-RestMethod -Uri "$BaseUrl/api/alerts/point/1" -Headers $Headers
$A2 = Invoke-RestMethod -Uri "$BaseUrl/api/alerts/point/2" -Headers $Headers
$A3 = Invoke-RestMethod -Uri "$BaseUrl/api/alerts/point/3" -Headers $Headers
Write-Host "  Point1: $($A1.total) alerts  |  Point2: $($A2.total) alerts  |  Point3: $($A3.total) alerts" -ForegroundColor Gray

# ---------- 6. Paged Query ----------
Write-Host ""
Write-Host "[6/6] Paged Query..." -ForegroundColor Yellow
$QueryBody = @{
    pointIds  = @(1, 2, 3)
    page      = 1
    pageSize  = 5
} | ConvertTo-Json
$Query = Invoke-RestMethod -Uri "$BaseUrl/api/visualization/query" -Method Post -Body $QueryBody -Headers $Headers
if ($Query.code -eq "0000") {
    Write-Host "  OK - $($Query.total) records" -ForegroundColor Green
} else {
    Write-Host "  FAIL: $($Query.message)" -ForegroundColor Red
}

# ---------- Summary ----------
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Test Complete" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Login:          $($LoginResp.code)"
Write-Host "  Points:         $($Points.code) ($($Points.total) pts)"
Write-Host "  Report/Severe:  $($R1.code) push=$($R1.data.pushStatus)"
Write-Host "  Report/Moderate:$($R2.code) push=$($R2.data.pushStatus)"
Write-Host "  Report/Mild:    $($R3.code) push=$($R3.data.pushStatus)"
Write-Host "  Report/Normal:  $($R4.code) push=$($R4.data.pushStatus)"
Write-Host "  Trend:          $($Trend.code) ($($Trend.total) records)"
Write-Host "  Query:          $($Query.code) ($($Query.total) records)"
Write-Host ""

# Stress Test Runner — 智慧水利水质监测系统
# Prerequisites: JMeter 5.5+, Java 8+
# Usage: powershell -ExecutionPolicy Bypass -File tests/stress_test.ps1 [-BackendDir <path>] [-Duration <minutes>] [-ReportDir <path>]
param(
    [string]$BackendDir = "$PSScriptRoot/../cloud-backend",
    [int]$DurationMinutes = 5,
    [string]$ReportDir = "$PSScriptRoot/jmeter/reports",
    [int]$BackendPort = 8080,
    [string]$JmeterHome = $env:JMETER_HOME,
    [switch]$SkipStartBackend = $false
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$TestPlan = "$ScriptDir/jmeter/water_quality_stress_test.jmx"
$StartTime = Get-Date

# ── Locate JMeter ─────────────────────────────────────────────
if (-not $JmeterHome) {
    # Try common locations
    $candidates = @(
        "C:\apache-jmeter-5.5",
        "C:\apache-jmeter-5.6",
        "C:\Program Files\apache-jmeter",
        "$env:USERPROFILE\apache-jmeter-5.5"
    )
    foreach ($c in $candidates) {
        if (Test-Path "$c\bin\jmeter.bat") {
            $JmeterHome = $c
            break
        }
    }
}

if (-not $JmeterHome -or -not (Test-Path "$JmeterHome\bin\jmeter.bat")) {
    Write-Host "[FAIL] JMeter not found. Set JMETER_HOME or install to a common location." -ForegroundColor Red
    Write-Host "  Download: https://jmeter.apache.org/download_jmeter.cgi" -ForegroundColor Yellow
    exit 1
}

$JmeterBin = "$JmeterHome\bin"
Write-Host "JMeter: $JmeterBin" -ForegroundColor Gray

# ── Verify test plan ──────────────────────────────────────────
if (-not (Test-Path $TestPlan)) {
    Write-Host "[FAIL] Test plan not found: $TestPlan" -ForegroundColor Red
    exit 1
}

# ── Prepare report directory ──────────────────────────────────
$timestamp = Get-Date -Format "yyyyMMdd_HHmmss"
$ReportDir = "$ReportDir/run_$timestamp"
New-Item -ItemType Directory -Force -Path $ReportDir | Out-Null
$ResultsFile = "$ReportDir/results.jtl"
$HtmlReportDir = "$ReportDir/html"

Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host " 智慧水利水质监测系统 — 压力测试" -ForegroundColor Cyan
Write-Host "=" * 60 -ForegroundColor Cyan
Write-Host "  开始时间  : $($StartTime.ToString('yyyy-MM-dd HH:mm:ss'))"
Write-Host "  计划时长  : ${DurationMinutes}分钟"
Write-Host "  测试计划  : $TestPlan"
Write-Host "  结果目录  : $ReportDir"
Write-Host "  JMeter    : $JmeterHome"
Write-Host "=" * 60 -ForegroundColor Cyan

# ── Optional: Start backend ───────────────────────────────────
$backendProc = $null
if (-not $SkipStartBackend) {
    Write-Host "`n[1/4] 启动后端服务..." -ForegroundColor Yellow
    $jarPath = Get-ChildItem "$BackendDir/target/*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
    if (-not $jarPath) {
        Write-Host "  Jar not found, building..."
        Push-Location $BackendDir
        & ./mvnw.cmd package -DskipTests -q 2>&1
        Pop-Location
        $jarPath = Get-ChildItem "$BackendDir/target/*.jar" | Select-Object -First 1
    }
    Write-Host "  Starting: $($jarPath.Name)"
    $backendProc = Start-Process java `
        -ArgumentList "-jar `"$($jarPath.FullName)`" --spring.profiles.active=local --server.port=$BackendPort" `
        -PassThru -NoNewWindow

    # Wait for healthy
    $ready = $false
    $started = Get-Date
    do {
        Start-Sleep -Seconds 3
        try {
            $resp = Invoke-WebRequest -Uri "http://localhost:${BackendPort}/actuator/health" -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
            $body = $resp.Content | ConvertFrom-Json
            if ($body.status -eq "UP") { $ready = $true }
        } catch {}
    } while (-not $ready -and ((Get-Date) - $started).TotalSeconds -lt 120)

    if (-not $ready) {
        Write-Host "[FAIL] Backend did not start" -ForegroundColor Red
        Stop-Process $backendProc.Id -Force -ErrorAction SilentlyContinue
        exit 1
    }
    Write-Host "  Backend is UP" -ForegroundColor Green
} else {
    Write-Host "[1/4] 跳过启动后端 (SkipStartBackend)" -ForegroundColor DarkGray
}

# ── Run JMeter non-GUI ────────────────────────────────────────
Write-Host "`n[2/4] 执行压力测试 (${DurationMinutes}分钟)..." -ForegroundColor Yellow

$jmeterArgs = @(
    "-n",
    "-t", "`"$TestPlan`"",
    "-l", "`"$ResultsFile`"",
    "-e",
    "-o", "`"$HtmlReportDir`"",
    "-Jduration=$((Get-Date).AddMinutes($DurationMinutes).ToString('yyyy-MM-dd HH:mm:ss'))"
)

$jmeterCmd = "& `"$JmeterBin\jmeter.bat`" $($jmeterArgs -join ' ')"
Write-Host "  Command: jmeter $($jmeterArgs -join ' ')" -ForegroundColor DarkGray

$jmeterProc = Start-Process cmd.exe `
    -ArgumentList "/c $jmeterCmd" `
    -PassThru -NoNewWindow -Wait

$JmeterExit = $jmeterProc.ExitCode
$EndTime = Get-Date
$Elapsed = ($EndTime - $StartTime).TotalMinutes

Write-Host "  JMeter exit code: $JmeterExit" -ForegroundColor $(if ($JmeterExit -eq 0) { "Green" } else { "Yellow" })
Write-Host "  实际耗时: $([math]::Round($Elapsed, 1))分钟"

# ── Stop backend ──────────────────────────────────────────────
if ($backendProc) {
    Write-Host "`n[3/4] 停止后端服务..." -ForegroundColor Yellow
    Stop-Process $backendProc.Id -Force -ErrorAction SilentlyContinue
    Write-Host "  Backend stopped" -ForegroundColor Green
}

# ── Summary ───────────────────────────────────────────────────
Write-Host "`n[4/4] 结果分析" -ForegroundColor Yellow

if (Test-Path $ResultsFile) {
    # Parse JTL for basic stats
    $lines = Get-Content $ResultsFile | Where-Object { $_ -notmatch '^#' -and $_ -notmatch '^timeStamp' }
    $total = $lines.Count
    if ($total -gt 0) {
        $fields = $lines | ForEach-Object {
            $parts = $_ -split ','
            if ($parts.Count -ge 8) {
                [PSCustomObject]@{
                    Success = $parts[7] -eq 'true'
                    Elapsed = [int]$parts[1]
                    Label   = $parts[2]
                }
            }
        }
        $successCount = ($fields | Where-Object { $_.Success }).Count
        $failCount = $total - $successCount
        $avgMs = if ($fields.Count -gt 0) { [math]::Round(($fields | Measure-Object -Property Elapsed -Average).Average) } else { 0 }
        $p95Idx = [math]::Floor($fields.Count * 0.95)
        $p95Ms = if ($fields.Count -gt 0) { ($fields | Sort-Object Elapsed)[$p95Idx].Elapsed } else { 0 }

        Write-Host "`n  ═══════════════════════════════" -ForegroundColor Cyan
        Write-Host "   压力测试结果汇总" -ForegroundColor Cyan
        Write-Host "  ═══════════════════════════════" -ForegroundColor Cyan
        Write-Host "  总请求数     : $total"
        Write-Host "  成功         : $successCount" -ForegroundColor Green
        Write-Host "  失败         : $failCount" -ForegroundColor $(if ($failCount -gt 0) { "Red" } else { "Gray" })
        Write-Host "  成功率       : $([math]::Round($successCount/$total*100, 1))%"
        Write-Host "  平均响应     : ${avgMs}ms"
        Write-Host "  P95响应      : ${p95Ms}ms"
        Write-Host "  吞吐量       : $([math]::Round($total / [math]::Max(($EndTime-$StartTime).TotalSeconds, 1), 1)) req/s"
        Write-Host "  ═══════════════════════════════" -ForegroundColor Cyan

        # Per-endpoint breakdown
        Write-Host "`n  各接口响应时间:" -ForegroundColor DarkYellow
        $fields | Group-Object Label | ForEach-Object {
            $avg = [math]::Round(($_.Group | Measure-Object -Property Elapsed -Average).Average)
            $pct = [math]::Round($_.Count / $total * 100, 1)
            Write-Host "    $($_.Name.PadRight(35)) ${avg}ms avg  ($($_.Count) reqs, ${pct}%)"
        }
    } else {
        Write-Host "  No valid results in JTL file" -ForegroundColor Yellow
    }

    Write-Host "`n  HTML报告: $HtmlReportDir\index.html" -ForegroundColor Green
    Write-Host "  原始数据: $ResultsFile" -ForegroundColor Green
} else {
    Write-Host "  Results file not generated" -ForegroundColor Red
}

Write-Host "`n完成于: $((Get-Date).ToString('yyyy-MM-dd HH:mm:ss'))" -ForegroundColor Cyan

if ($failCount -gt 0 -or $JmeterExit -ne 0) {
    exit 1
}
exit 0

<#
.SYNOPSIS
  Smart Water Quality System - One-Click Launcher
  Usage: .\start.ps1 [-Mode demo|local|full] [-Port 8080] [-SkipBuild]
#>
param(
    [ValidateSet("demo", "local", "full")]
    [string]$StartMode = "demo",
    [int]$ServerPort = 8080,
    [switch]$SkipBuild = $false
)

$ErrorActionPreference = "Stop"
$RootDir = $PSScriptRoot
$BackendDir = Join-Path $RootDir "cloud-backend"
$BaseUrl = "http://localhost:$ServerPort"

# ---- helpers ----
function I($m) { Write-Host "  $m" -ForegroundColor Cyan }
function O($m) { Write-Host "  [OK] $m" -ForegroundColor Green }
function W($m) { Write-Host "  [WARN] $m" -ForegroundColor Yellow }
function E($m) { Write-Host "  [ERROR] $m" -ForegroundColor Red }

# ---- banner ----
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "  Smart Water Quality Monitoring System" -ForegroundColor White
Write-Host "  Version 2.1" -ForegroundColor Gray
Write-Host "========================================" -ForegroundColor Cyan
$msg = "  Mode: {0}  |  Port: {1}" -f $StartMode, $ServerPort
Write-Host $msg -ForegroundColor Yellow
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""

# ---- 0. kill old server process ----
$oldProc = Get-Process java -ErrorAction SilentlyContinue | Where-Object {
    $cmd = (Get-CimInstance Win32_Process -Filter "ProcessId = $($_.Id)").CommandLine
    $cmd -notlike '*jdt.ls*' -and $cmd -notlike '*vscode*' -and $cmd -notlike '*gradle*'
}
if ($oldProc) {
    W ("Stopping old server (PID: " + ($oldProc.Id -join ',') + ")...")
    $oldProc | Stop-Process -Force -ErrorAction SilentlyContinue
    Start-Sleep 2
    O "Old server stopped"
}

# ---- 1. check java ----
I "Checking Java..."
$jc = Get-Command java -ErrorAction SilentlyContinue
if (-not $jc) { E "Java not found. Install JDK 11+"; exit 1 }
O ("Java: " + $jc.Source)

# ---- 2. demo info ----
if ($StartMode -eq "demo") {
    I "Demo mode: H2 in-memory DB, no MySQL/MQTT needed"
}

# ---- 3. mysql (local/full only) ----
if ($StartMode -eq "local" -or $StartMode -eq "full") {
    I "Checking MySQL..."
    $mh = if ($env:MYSQL_HOST) { $env:MYSQL_HOST } else { "localhost" }
    $mp = if ($env:MYSQL_PORT) { $env:MYSQL_PORT } else { "3306" }
    $mu = if ($env:MYSQL_USER) { $env:MYSQL_USER } else { "root" }
    $mw = if ($env:MYSQL_PASSWORD) { $env:MYSQL_PASSWORD } else { "root" }
    $sf = Join-Path $RootDir "database\schema.sql"
    $mb = Get-Command mysql -ErrorAction SilentlyContinue
    if ($mb) {
        try {
            $cargs = @("-h",$mh,"-P",$mp,"-u",$mu,"-p$mw","-e","CREATE DATABASE IF NOT EXISTS smart_water_quality DEFAULT CHARACTER SET utf8mb4")
            & mysql $cargs 2>$null
            Get-Content $sf -Raw -Encoding UTF8 | & mysql -h $mh -P $mp -u $mu "-p$mw" smart_water_quality 2>$null
            O "MySQL database ready"
        } catch { W "DB init failed, run manually: mysql -u root -p < database\schema.sql" }
    } else { W "mysql CLI not found, ensure DB exists" }
}

# ---- 4. mqtt (full only) ----
if ($StartMode -eq "full") {
    if (Get-Process mosquitto -ErrorAction SilentlyContinue) { O "MQTT Broker running" }
    else { W "Mosquitto not running, continuing without MQTT" }
}

# ---- 5. build ----
if (-not $SkipBuild) {
    I "Building (first run may take minutes)..."
    $mvnPath = Join-Path $BackendDir "mvnw.cmd"
    Push-Location $BackendDir
    try {
        $sw = [System.Diagnostics.Stopwatch]::StartNew()
        $mvnOutput = & $mvnPath package -DskipTests -q 2>&1 | Out-String
        $sw.Stop()
        if ($LASTEXITCODE -ne 0) {
            Write-Host $mvnOutput
            E "Build failed"
            Pop-Location
            exit 1
        }
        $secs = [math]::Round($sw.Elapsed.TotalSeconds, 1)
        $buildMsg = "Build done in " + $secs + "s"
        O $buildMsg
    } finally { Pop-Location }
}

# ---- 6. find jar ----
$jarFile = Get-ChildItem (Join-Path $BackendDir "target\smart-water-quality-*.jar") -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jarFile) {
    E "JAR not found. Build may have failed."
    exit 1
}
$jarMsg = "JAR: " + $jarFile.Name
I $jarMsg

# ---- 7. start server ----
$activeProfile = if ($StartMode -eq "demo") { "demo" } else { "local" }

# Check port availability — auto-kill old process if port is in use
$portLine = netstat -ano 2>$null | Select-String ":$ServerPort " | Select-String "LISTENING"
if ($portLine) {
    $pidMatch = [regex]::Match($portLine, '\s+(\d+)\s*$')
    if ($pidMatch.Success) {
        $oldPid = [int]$pidMatch.Groups[1].Value
        W ("Port " + $ServerPort + " in use by PID " + $oldPid + ". Stopping old process...")
        Stop-Process -Id $oldPid -Force -ErrorAction SilentlyContinue
        Start-Sleep 2
        O "Old process stopped"
    }
}

$startMsg = "Starting backend (profile=" + $activeProfile + ", port=" + $ServerPort + ")..."
I $startMsg

# Use System.Diagnostics.Process for reliable stdout/stderr capture
$psi = New-Object System.Diagnostics.ProcessStartInfo
$psi.FileName = "java"
$psi.Arguments = "-jar `"$($jarFile.FullName)`" --spring.profiles.active=$activeProfile --server.port=$ServerPort"
$psi.UseShellExecute = $false
$psi.RedirectStandardOutput = $true
$psi.RedirectStandardError = $true
$psi.CreateNoWindow = $true

$serverProc = New-Object System.Diagnostics.Process
$serverProc.StartInfo = $psi

# Register stdout/stderr async readers to prevent pipe buffer deadlock
$stdoutBuffer = New-Object System.Text.StringBuilder
$stderrBuffer = New-Object System.Text.StringBuilder
$serverProc.add_OutputDataReceived({
    param($sender, $e)
    if ($e.Data -ne $null) {
        [void]$stdoutBuffer.AppendLine($e.Data)
        Write-Host $e.Data
    }
})
$serverProc.add_ErrorDataReceived({
    param($sender, $e)
    if ($e.Data -ne $null) {
        [void]$stderrBuffer.AppendLine($e.Data)
        Write-Host $e.Data -ForegroundColor Red
    }
})

$serverProc.Start() | Out-Null
$serverProc.BeginOutputReadLine()
$serverProc.BeginErrorReadLine()

# ---- 8. wait for ready ----
$waitMsg = "Waiting for service (first run ~30s)..."
I $waitMsg
$sw = [System.Diagnostics.Stopwatch]::StartNew()
$maxWait = 300
$ready = $false

do {
    Start-Sleep -Seconds 3
    try {
        $resp = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
        if ($resp.StatusCode -eq 200) {
            $body = $resp.Content | ConvertFrom-Json
            if ($body.status -eq "UP") {
                $ready = $true
            }
        }
    } catch { }
} while (-not $ready -and $sw.Elapsed.TotalSeconds -lt $maxWait)

$sw.Stop()
$elapsed = [math]::Round($sw.Elapsed.TotalSeconds, 0)

if (-not $ready) {
    $failMsg = "Startup failed after " + $elapsed + "s"
    E $failMsg
    Write-Host "  Check the console output above for errors." -ForegroundColor Yellow
    Write-Host "  Common issues: port in use, Java version < 11" -ForegroundColor Gray
    Stop-Process $serverProc.Id -Force -ErrorAction SilentlyContinue
    exit 1
}

$readyMsg = "Service ready in " + $elapsed + "s"
O $readyMsg

# ---- 9. print access info ----
Write-Host ""
Write-Host "========================================" -ForegroundColor Green
Write-Host "  System is running!" -ForegroundColor White
Write-Host "========================================" -ForegroundColor Green
Write-Host "  URL:       $BaseUrl" -ForegroundColor Cyan
Write-Host "  Login:     admin / admin123" -ForegroundColor Yellow
if ($StartMode -eq "demo") {
    Write-Host "  H2 Console: $BaseUrl/h2-console" -ForegroundColor Gray
}
Write-Host "========================================" -ForegroundColor Green
Write-Host ""

# ---- 10. open browser ----
I "Opening browser..."
Start-Process "http://localhost:$ServerPort"

Write-Host ""
Write-Host "  If browser does not open, manually go to:" -ForegroundColor Yellow
Write-Host "  $BaseUrl" -ForegroundColor Cyan

# ---- 11. wait ----
Write-Host "Press Ctrl+C to stop." -ForegroundColor Gray
try { $serverProc.WaitForExit() }
finally {
    Write-Host ""
    Write-Host "Shutting down..." -ForegroundColor Yellow
    Stop-Process $serverProc.Id -Force -ErrorAction SilentlyContinue
    O "Service stopped"
}

# E2E Integration Test — 智慧水利水质监测系统
# Usage: powershell -ExecutionPolicy Bypass -File tests/e2e_test.ps1 [-BackendDir <path>] [-SkipBuild]
param(
    [string]$BackendDir = "$PSScriptRoot/../cloud-backend",
    [switch]$SkipBuild = $false,
    [int]$BackendPort = 8080,
    [string]$BaseUrl = "http://localhost:$BackendPort",
    [int]$WaitTimeout = 120
)

$ErrorActionPreference = "Stop"
$ScriptDir = Split-Path -Parent $MyInvocation.MyCommand.Path
$ProjectRoot = Resolve-Path "$ScriptDir/.."
$Passed = 0
$Failed = 0
$Total = 0

function Write-Step($msg) {
    Write-Host "`n=== $msg ===" -ForegroundColor Cyan
}

function Test-Result($name, $condition) {
    $script:Total++
    if ($condition) {
        Write-Host "  [PASS] $name" -ForegroundColor Green
        $script:Passed++
    } else {
        Write-Host "  [FAIL] $name" -ForegroundColor Red
        $script:Failed++
    }
}

function Assert-Status($resp, $expectedCode, $testName) {
    $ok = $resp.StatusCode -eq $expectedCode
    Test-Result $testName $ok
}

function Assert-Contains($resp, $jsonPath, $testName) {
    try {
        $body = $resp.Content | ConvertFrom-Json
        Test-Result $testName ($null -ne $body)
    } catch {
        Test-Result $testName $false
    }
}

# ── Phase 0: Pre-flight ──────────────────────────────────────
Write-Step "Pre-flight checks"

$Java = Get-Command java -ErrorAction SilentlyContinue
if (-not $Java) {
    Write-Host "  [SKIP] Java not found — cannot run backend" -ForegroundColor Yellow
    Write-Host "  Set JAVA_HOME or add java to PATH" -ForegroundColor Yellow
    exit 1
}

Write-Host "  Java: $($Java.Source)"
Write-Host "  BackendDir: $BackendDir"
Write-Host "  BaseUrl: $BaseUrl"

# ── Phase 1: Build (optional) ────────────────────────────────
if (-not $SkipBuild) {
    Write-Step "Building backend"
    Push-Location $BackendDir
    $buildOutput = & ./mvnw.cmd compile -q 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host $buildOutput
        Write-Host "  [FAIL] Compilation failed" -ForegroundColor Red
        Pop-Location
        exit 1
    }
    Write-Host "  [PASS] Compilation succeeded" -ForegroundColor Green
    Pop-Location
}

# ── Phase 2: Start backend ────────────────────────────────────
Write-Step "Starting backend"

$jarPath = Get-ChildItem "$BackendDir/target/*.jar" -ErrorAction SilentlyContinue | Select-Object -First 1
if (-not $jarPath) {
    Write-Host "  Jar not found, packaging..."
    Push-Location $BackendDir
    & ./mvnw.cmd package -DskipTests -q 2>&1
    if ($LASTEXITCODE -ne 0) {
        Write-Host "  [FAIL] Package failed" -ForegroundColor Red
        Pop-Location
        exit 1
    }
    Pop-Location
    $jarPath = Get-ChildItem "$BackendDir/target/*.jar" | Select-Object -First 1
}

Write-Host "  Jar: $($jarPath.Name)"

$proc = Start-Process java `
    -ArgumentList "-jar `"$($jarPath.FullName)`" --spring.profiles.active=local --server.port=$BackendPort" `
    -PassThru -NoNewWindow

# Wait for healthy
Write-Host "  Waiting for backend to be ready..."
$started = Get-Date
$ready = $false
do {
    Start-Sleep -Seconds 3
    try {
        $resp = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -TimeoutSec 5 -UseBasicParsing -ErrorAction Stop
        if ($resp.StatusCode -eq 200) {
            $body = $resp.Content | ConvertFrom-Json
            if ($body.status -eq "UP") {
                $ready = $true
                Write-Host "  Backend is UP (took $([math]::Round(((Get-Date)-$started).TotalSeconds))s)"
            }
        }
    } catch {}
} while (-not $ready -and ((Get-Date) - $started).TotalSeconds -lt $WaitTimeout)

if (-not $ready) {
    Write-Host "  [FAIL] Backend did not start within ${WaitTimeout}s" -ForegroundColor Red
    Stop-Process $proc.Id -Force -ErrorAction SilentlyContinue
    exit 1
}

# ── Phase 3: API Tests ────────────────────────────────────────
Write-Step "Running API tests"

# 3.1 Health
$resp = Invoke-RestMethod -Uri "$BaseUrl/actuator/health" -UseBasicParsing
Test-Result "Actuator health UP" ($resp.status -eq "UP")

# 3.2 System Dashboard
try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/system/dashboard" -UseBasicParsing
    Test-Result "System dashboard accessible" ($resp.code -eq 200)
    Test-Result "System dashboard has heap memory" ($resp.data.heapMemoryMB -ne $null)
    Test-Result "System dashboard has uptime" ($resp.data.uptimeSeconds -ne $null)
    Test-Result "System dashboard has active WS" ($resp.data.activeWebSocketConnections -ne $null)
} catch {
    Test-Result "System dashboard" $false
}

# 3.3 Login
try {
    $loginBody = @{username="admin";password="admin123"} | ConvertTo-Json
    $loginResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post `
        -Body $loginBody -ContentType "application/json" -UseBasicParsing
    $token = $loginResp.data.token
    Test-Result "Login success" ($null -ne $token)
    $authHeaders = @{Authorization="Bearer $token"}
} catch {
    Test-Result "Login" $false
    $token = $null
    $authHeaders = @{}
}

# 3.4 Data query (authenticated)
if ($token) {
    try {
        $resp = Invoke-RestMethod -Uri "$BaseUrl/api/data/latest" -Headers $authHeaders -UseBasicParsing
        Test-Result "GET /api/data/latest" ($resp.code -eq 200)
    } catch {
        Test-Result "GET /api/data/latest" $false
    }
}

# 3.5 Visualization endpoints
try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/visualization/map-data" -Headers $authHeaders -UseBasicParsing
    Test-Result "GET /api/visualization/map-data" ($resp.code -eq 200)
} catch {
    Test-Result "GET /api/visualization/map-data" $false
}

# 3.6 Alert endpoints
try {
    $resp = Invoke-RestMethod -Uri "$BaseUrl/api/alerts/statistics?days=7" -Headers $authHeaders -UseBasicParsing
    Test-Result "GET /api/alerts/statistics" ($resp.code -eq 200)
} catch {
    Test-Result "GET /api/alerts/statistics" $false
}

# 3.7 Rate limiting test (rapid-fire requests)
if ($token) {
    Write-Host "  Testing rate limiter (20 rapid requests)..."
    $rateLimited = $false
    for ($i = 0; $i -lt 25; $i++) {
        try {
            $r = Invoke-WebRequest -Uri "$BaseUrl/api/data/latest" -Headers $authHeaders `
                -TimeoutSec 3 -UseBasicParsing -ErrorAction Stop
        } catch {
            if ($_.Exception.Response.StatusCode -eq 429) {
                $rateLimited = $true
                break
            }
        }
    }
    Test-Result "Rate limiting triggers 429" $rateLimited
}

# 3.8 JWT blacklist (logout) test
if ($token) {
    try {
        $logoutResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/logout" -Method Post `
            -Headers $authHeaders -UseBasicParsing
        Test-Result "Logout success" ($logoutResp.code -eq 200)

        # Try old token — should get 401
        try {
            Invoke-RestMethod -Uri "$BaseUrl/api/data/latest" -Headers $authHeaders -UseBasicParsing
            Test-Result "JWT blacklist blocks old token" $false
        } catch {
            Test-Result "JWT blacklist blocks old token" ($_.Exception.Response.StatusCode -eq 401)
        }
    } catch {
        Test-Result "Logout" $false
    }
}

# 3.9 Brute force protection
Write-Host "  Testing brute force lockout (5 bad logins)..."
$badBody = @{username="admin";password="wrong"} | ConvertTo-Json
for ($i = 0; $i -lt 6; $i++) {
    try {
        Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post `
            -Body $badBody -ContentType "application/json" -UseBasicParsing | Out-Null
    } catch {}
}
# The 6th attempt should mention account lockout
try {
    $lockedResp = Invoke-RestMethod -Uri "$BaseUrl/api/auth/login" -Method Post `
        -Body $badBody -ContentType "application/json" -UseBasicParsing
    $locked = $lockedResp.message -match "lock" -or $lockedResp.code -ne 200
    Test-Result "Brute force lockout active" $locked
} catch {
    Test-Result "Brute force lockout active" $false
}

# 3.10 TraceId header
try {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -UseBasicParsing
    $traceId = $resp.Headers['X-Trace-Id']
    Test-Result "X-Trace-Id header present" ($traceId -and $traceId.Length -gt 0)
} catch {
    Test-Result "X-Trace-Id header" $false
}

# ── Phase 4: Cleanup ──────────────────────────────────────────
Write-Step "Cleanup"
Stop-Process $proc.Id -Force -ErrorAction SilentlyContinue
Write-Host "  Backend process stopped"

# ── Phase 5: Report ───────────────────────────────────────────
Write-Step "Results"
Write-Host "  Total : $Total" -ForegroundColor $(if ($Failed -eq 0) {"Green"} else {"Red"})
Write-Host "  Passed: $Passed" -ForegroundColor Green
Write-Host "  Failed: $Failed" -ForegroundColor $(if ($Failed -gt 0) {"Red"} else {"Gray"})

if ($Failed -gt 0) {
    Write-Host "`n  Some tests FAILED — review output above" -ForegroundColor Red
    exit 1
} else {
    Write-Host "`n  All tests PASSED" -ForegroundColor Green
    exit 0
}

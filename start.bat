@echo off
title Smart Water Quality System
echo ========================================
echo   Smart Water Quality Monitoring System
echo   Starting demo mode...
echo ========================================
echo.
powershell -ExecutionPolicy Bypass -File "%~dp0start.ps1" -StartMode demo
start http://localhost:8080
pause

@echo off
title SecureNote Desktop
cd /d "%~dp0pc\secure-note"
echo ===========================================
echo Launching SecureNote Desktop App...
echo ===========================================
call .\gradlew.bat :desktopApp:run --no-configuration-cache
if %ERRORLEVEL% NEQ 0 (
    echo.
    echo Something went wrong. Press any key to exit.
    pause
)

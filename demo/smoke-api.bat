@echo off
>nul chcp 65001
rem Сквозной автотест API.
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0smoke-api.ps1"
echo.
pause
exit /b %errorlevel%

@echo off
echo [stop] stopping rag-service python processes
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0stop-rag.ps1"
timeout /t 2 /nobreak >nul
echo [stop] done

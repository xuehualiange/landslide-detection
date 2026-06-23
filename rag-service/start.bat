@echo off
setlocal EnableDelayedExpansion
cd /d "%~dp0"
set "PY=%CD%\.venv\Scripts\python.exe"
set "PORT=8000"
set "HF_ENDPOINT=https://hf-mirror.com"

if /i "%~1"=="--reinstall" (
  echo [reinstall] stopping old processes
  call "%~dp0stop-rag.bat"
  if exist .venv (
    set "BAK=.venv_old_%RANDOM%"
    echo [reinstall] rename .venv to !BAK!
    ren .venv "!BAK!" 2>nul
    if exist .venv (
      echo [error] cannot rename .venv - run stop-rag.bat and close Python windows
      pause
      exit /b 1
    )
  )
  if exist .venv\install.ok del /f /q .venv\install.ok
)

if not exist .venv\pyvenv.cfg (
  if exist .venv (
    echo [setup] broken venv detected, removing
    call "%~dp0stop-rag.bat"
    set "BAK=.venv_broken_%RANDOM%"
    ren .venv "!BAK!" 2>nul
    if exist .venv rmdir /s /q .venv 2>nul
  )
  echo [setup] creating virtual environment
  python -m venv .venv
)

if not exist "%PY%" (
  echo [error] venv python not found
  pause
  exit /b 1
)

if not exist .venv\install.ok (
  echo [setup] installing dependencies
  "%PY%" -m pip install -U pip
  "%PY%" -m pip install -r requirements.txt --prefer-binary
  if errorlevel 1 (
    echo [error] pip install failed - try: start.bat --reinstall
    pause
    exit /b 1
  )
  echo ok> .venv\install.ok
)

if not exist faiss_index\index.faiss (
  echo [setup] building FAISS index
  "%PY%" ingest.py
)

if "%DEEPSEEK_API_KEY%"=="" (
  if exist .deepseek_key set /p DEEPSEEK_API_KEY=<.deepseek_key
)
if "%DEEPSEEK_API_KEY%"=="" echo [warn] DEEPSEEK_API_KEY not set

echo [start] RAG service on port %PORT%
set PYTHONUTF8=1
set PYTHONIOENCODING=utf-8
"%PY%" -m uvicorn main:app --host 0.0.0.0 --port %PORT%

#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")"
export HF_ENDPOINT="${HF_ENDPOINT:-https://hf-mirror.com}"
PY=".venv/bin/python"
export PORT="${PORT:-8000}"

if [ ! -d .venv ]; then python3 -m venv .venv; fi
if [ ! -f .venv/install.ok ]; then
  "$PY" -m pip install -U pip
  "$PY" -m pip install -r requirements.txt --prefer-binary
  touch .venv/install.ok
fi
if [ ! -f faiss_index/index.faiss ]; then "$PY" ingest.py; fi
exec "$PY" -m uvicorn main:app --host 0.0.0.0 --port "$PORT"

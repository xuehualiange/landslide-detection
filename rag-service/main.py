"""FastAPI RAG service: POST /ask"""
from __future__ import annotations

import os

os.environ.setdefault("PYTHONUTF8", "1")
from contextlib import asynccontextmanager

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from rag_engine import engine

PORT = int(os.getenv("PORT", "8000"))
CORS_ORIGINS = os.getenv("CORS_ORIGINS", "http://localhost:5173,http://127.0.0.1:5173").split(",")


class AskRequest(BaseModel):
  question: str = Field(..., min_length=1, description="用户问题")


class SourceItem(BaseModel):
  source: str
  snippet: str


class AskResponse(BaseModel):
  answer: str
  sources: list[SourceItem]


@asynccontextmanager
async def lifespan(_app: FastAPI):
  engine.initialize()
  if not engine.ready:
    print(f"[WARN] RAG not ready: {engine.error}")
  else:
    print("[OK] RAG engine ready")
  yield


app = FastAPI(title="Landslide RAG Service", lifespan=lifespan)
app.add_middleware(
  CORSMiddleware,
  allow_origins=[o.strip() for o in CORS_ORIGINS if o.strip()],
  allow_credentials=True,
  allow_methods=["*"],
  allow_headers=["*"],
)


@app.get("/health")
def health():
  return {"status": "ok" if engine.ready else "degraded", "ready": engine.ready, "error": engine.error}


@app.post("/ask", response_model=AskResponse)
def ask(body: AskRequest):
  if not engine.ready:
    raise HTTPException(status_code=503, detail=engine.error or "RAG engine not ready")
  try:
    data = engine.ask(body.question.strip())
    return AskResponse(**data)
  except Exception as exc:  # noqa: BLE001
    raise HTTPException(status_code=500, detail=str(exc)) from exc


if __name__ == "__main__":
  import uvicorn
  uvicorn.run("main:app", host="0.0.0.0", port=PORT, reload=False)

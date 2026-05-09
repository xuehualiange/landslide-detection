"""
FastAPI: POST /chat JSON {"user_input": "...", "language": "可选", "session_id": "可选"}
Returns {"reply": "..."}.
"""

from __future__ import annotations

import os

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, Field

from record_and_invoke import record_and_invoke, reset_history


class ChatRequest(BaseModel):
    user_input: str = Field(..., description="User message")
    language: str | None = Field(
        default=None,
        description="Reply language for system prompt, e.g. 中文 / English",
    )
    session_id: str | None = Field(
        default=None,
        description="Isolate multi-turn history per session; omit uses bucket 'default'",
    )
    record_context: str | None = Field(
        default=None,
        description="Recent detection summary from Java backend",
    )



class ChatResponse(BaseModel):
    reply: str = Field(..., description="Assistant reply")


class ChatResetResponse(BaseModel):
    ok: bool = True


app = FastAPI(title="LangChain Chat API", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)


@app.get("/health")
def health() -> dict[str, str]:
    return {"status": "ok"}


@app.post("/chat", response_model=ChatResponse)
def chat(body: ChatRequest) -> ChatResponse:
    if not body.user_input or not str(body.user_input).strip():
        raise HTTPException(status_code=400, detail="user_input is required")
    lang = body.language or os.getenv("CHAT_DEFAULT_LANGUAGE", "中文")
    try:
        reply = record_and_invoke(
            body.user_input,
            language=lang,
            session_id=body.session_id,
            record_context=body.record_context,
        )
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    return ChatResponse(reply=reply)


@app.post("/chat/reset", response_model=ChatResetResponse)
def chat_reset(session_id: str | None = None) -> ChatResetResponse:
    """Clear history for one session; omit session_id clears the default bucket."""
    try:
        reset_history(session_id)
    except Exception as exc:
        raise HTTPException(status_code=500, detail=str(exc)) from exc
    return ChatResetResponse()


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host=os.getenv("HOST", "0.0.0.0"),
        port=int(os.getenv("PORT", "8000")),
        reload=False,
    )
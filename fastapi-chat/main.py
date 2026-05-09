from __future__ import annotations

import os

from fastapi import FastAPI, HTTPException
from fastapi.middleware.cors import CORSMiddleware
from pydantic import BaseModel, ConfigDict, Field

from get_ai_response import get_ai_response


class ChatRequest(BaseModel):
    model_config = ConfigDict(extra="ignore")

    user_input: str = Field(..., description="User message")


class ChatReply(BaseModel):
    reply: str


app = FastAPI(title="Chat API", version="1.0.0")

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


@app.post("/chat", response_model=ChatReply)
def chat(body: ChatRequest) -> ChatReply:
    if not body.user_input or not str(body.user_input).strip():
        raise HTTPException(status_code=400, detail="user_input is required")
    try:
        response = get_ai_response(body.user_input)
    except Exception as exc:
        raise HTTPException(status_code=502, detail=str(exc)) from exc
    return ChatReply(reply=response)


if __name__ == "__main__":
    import uvicorn

    uvicorn.run(
        "main:app",
        host=os.getenv("HOST", "0.0.0.0"),
        port=int(os.getenv("PORT", "8000")),
        reload=os.getenv("UVICORN_RELOAD", "").lower() in ("1", "true", "yes"),
    )
# RAG Service

滑坡知识库问答（Chroma + HuggingFace Embeddings + DeepSeek）。

## 端口

默认 **8000**（`/ask`）。若与 `langchain-chat-api` 冲突，可设置环境变量 `PORT=8001`，并同步修改 Spring `rag.api.ask-url`。

## 快速开始

```powershell
cd rag-service
$env:DEEPSEEK_API_KEY = "sk-你的密钥"
.\start.bat
```

首次启动会自动 `ingest.py` 构建 `chroma_db/`。

## 测试

```bash
curl -X POST http://localhost:8000/ask -H "Content-Type: application/json" -d "{\"question\":\"滑坡的常见诱因有哪些？\"}"
```

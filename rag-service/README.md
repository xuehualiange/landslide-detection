# RAG Service

滑坡知识库问答（FAISS + HuggingFace Embeddings + DeepSeek）。

## 端口

默认 **8000**（`/ask`）。与 `langchain-chat-api` 同时运行时，知识库保持 8000，智能助手使用 **8001**（`start-all.ps1` 默认配置）。

## 快速开始

```powershell
cd rag-service
$env:DEEPSEEK_API_KEY = "sk-你的密钥"
.\start.bat
```

首次启动会自动 `ingest.py` 构建 `faiss_index/`。

## 测试

```bash
curl -X POST http://localhost:8000/ask -H "Content-Type: application/json" -d "{\"question\":\"滑坡的常见诱因有哪些？\"}"
```

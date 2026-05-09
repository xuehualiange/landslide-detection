# LangChain Chat API

基于 FastAPI + LangChain（DeepSeek/OpenAI 兼容接口）的对话服务，提供 `POST /chat`。

## 常见问题：`ModuleNotFoundError: No module named 'langchain'`

原因通常是下面之一：

1. **依赖未装全**  
   在项目目录执行（务必先激活虚拟环境）：
   ```bash
   pip install -r requirements.txt
   ```
   LangChain 0.2 起拆成多个包；部分旧教程仍使用 `import langchain`。本项目已在 `requirements.txt` 中包含 **`langchain`**、`langchain-core`、`langchain-openai`，安装后即可消除多数该类报错。

2. **未使用当前项目的虚拟环境**  
   确认启动 uvicorn 时使用的 Python 与执行 `pip install` 的是同一个解释器：
   ```bash
   python -c "import sys; print(sys.executable)"
   python -c "import langchain; import langchain_core"
   ```

3. **在错误目录运行**  
   请在 `langchain-chat-api` 目录下启动（或把 `PYTHONPATH` 指到该目录），例如：
   ```bash
   cd langchain-chat-api
   uvicorn main:app --host 0.0.0.0 --port 8000
   ```

若仍报错，可尝试升级：`pip install -U langchain langchain-core langchain-openai`。

---

## 接口说明

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/health` | 健康检查，返回 `{"status":"ok"}` |
| POST | `/chat` | 对话，请求体 JSON，返回助手回复 |
| POST | `/chat/reset` | 清空某一会话的多轮历史（查询参数 `session_id`，可省略） |

### `POST /chat`

**请求体（JSON）**

| 字段 | 类型 | 必填 | 说明 |
|------|------|------|------|
| `user_input` | string | 是 | 用户输入 |
| `language` | string | 否 | 系统提示中的回答语言，默认取环境变量 `CHAT_DEFAULT_LANGUAGE`，未设置时为 `中文` |
| `session_id` | string | 否 | **会话隔离**：相同 `session_id` 共用多轮上下文；不传则使用内置桶 `default` |

示例：

```json
{"user_input": "你好", "language": "中文", "session_id": "user-1001"}
```

**响应（JSON）**

| 字段 | 类型 | 说明 |
|------|------|------|
| `reply` | string | 模型回复正文 |

HTTP 4xx：参数错误（如 `user_input` 为空）。  
HTTP 502：调用模型或链路的异常信息在 `detail` 中。

### `POST /chat/reset`

清空指定会话在服务端内存中的历史（不影响其它 `session_id`）。

- 查询参数：`session_id`（可选）。不传则清空默认桶 `default`。
- 示例：`POST /chat/reset`、`POST /chat/reset?session_id=user-1001`
- 响应：`{"ok": true}`

---

## 依赖安装

```bash
cd langchain-chat-api
python -m venv .venv

# Windows PowerShell
.\.venv\Scripts\Activate.ps1

pip install -r requirements.txt
```

---

## 运行方式

1. 配置 API Key（示例为 DeepSeek）：

```powershell
$env:DEEPSEEK_API_KEY = "your-key"
```

2. 启动服务：

```bash
uvicorn main:app --host 0.0.0.0 --port 8000
```

或使用：

```bash
python main.py
```

默认端口 `8000`，可通过环境变量 `HOST`、`PORT` 调整（见 `main.py`）。

---

## 环境变量

| 变量 | 说明 | 默认值 |
|------|------|--------|
| `DEEPSEEK_API_KEY` | DeepSeek API Key（必填） | 无 |
| `DEEPSEEK_MODEL` | 模型名 | `deepseek-chat` |
| `DEEPSEEK_API_BASE` | API 基地址 | `https://api.deepseek.com` |
| `DEEPSEEK_TEMPERATURE` | 采样温度 | `0.7` |
| `CHAT_DEFAULT_LANGUAGE` | 未传 `language` 时的默认语言 | `中文` |
| `CHAT_MAX_ROUNDS` | **每个 session** 保留的最近对话轮数（每轮 = 用户 + 助手各一条） | `10` |

---

## 对话长度与 Token

每个 `session_id`（含默认桶）在内存中维护各自的 `chat_history`。每次回复后会**只保留最近 `CHAT_MAX_ROUNDS` 轮**（默认 10 轮，最多 20 条 message）。

同一 `session_id` 的请求串行加锁，避免并发打乱一轮对话；不同 `session_id` 可并行调用模型。

注意：历史仅存于**当前进程内存**，重启服务后丢失。生产环境若要多实例，请改为 Redis 等外部存储。

---

## 与 Java Spring 对接

Spring Boot 侧 `ChatApiService` 已支持：

- `getAiResponse(userInput)`：默认会话桶；
- `getAiResponse(userInput, sessionId)`：随 JSON 发送 `session_id`；
- `resetChatSession(sessionId)`：调用 `POST /chat/reset`（由 `chat.api.chat-url` 推导 `/chat/reset`）。

---

## CORS

已在 `main.py` 中配置 `CORSMiddleware`，默认允许任意来源（`allow_origins=["*"]`）。生产环境建议改为明确的前端域名列表。
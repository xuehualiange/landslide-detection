# 基于人工智能算法的滑坡识别系统

本科毕业设计题目：`基于人工智能算法的滑坡识别系统设计与实现`

## 技术栈
- 后端：`Spring Boot 2.7`、`MyBatis-Plus`、`MySQL`、`Redis`、`Spring Security`、`JWT`
- 前端：`Vue 3`、`Vite`、`Element Plus`、`Axios`、`Pinia`
- AI推理：`Java + OpenCV DNN`（加载 `YOLOv8 ONNX`）

## 项目结构
- `backend`：后端服务
- `frontend`：前端工程
- `docs`：数据库脚本与文档
- `start-all.ps1`：一键启动脚本

## 环境要求
- JDK：17+（推荐 21）
- Maven：3.9+
- Node.js：18+
- MySQL：8.0+

## 数据库初始化
在 MySQL 中执行：

```powershell
Get-Content "e:\landslide-ai-system\docs\db-schema-v2.sql" | mysql -u root -p
Get-Content "e:\landslide-ai-system\docs\data.sql" | mysql -u root -p
```

> 如果你已经初始化过数据库，可跳过。

## 一键启动（推荐）
在项目根目录执行（会依次打开 **后端、前端**；若配置了 DeepSeek Key，会再打开 **LangChain 智能助手**）：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-all.ps1
```

**智能助手 API Key（任选其一）：**

1. 启动前设置环境变量：`$env:DEEPSEEK_API_KEY = "sk-你的密钥"`
2. 或在 `langchain-chat-api` 目录下创建文件 **`.deepseek_key`**（纯文本一行，仅密钥，勿提交 Git）
3. 或传参：`-DeepSeekApiKey "sk-..."`

可选参数示例：

```powershell
powershell -ExecutionPolicy Bypass -File .\start-all.ps1 `
  -JavaHome "C:\Program Files\JetBrains\PyCharm 2024.2.4\jbr" `
  -MavenHome "E:\apache-maven-3.9.6" `
  -NodeDir "C:\Program Files\nodejs" `
  -DbUser "root" `
  -DbPassword "你的数据库密码" `
  -DeepSeekApiKey "sk-你的密钥" `
  -ChatPort "8000"
```

仅启动前后端、不要 Python 窗口：`start-all.ps1 -SkipAssistant`

默认数据库密码参数为 `root`（与 `application-dev.yml` 中默认一致）；若你本地 MySQL 密码不同，请改 `-DbPassword`。

## 手动启动（备选）
### 后端
```powershell
cd e:\landslide-ai-system\backend
$env:MAVEN_HOME="E:\apache-maven-3.9.6"
$env:Path="$env:MAVEN_HOME\bin;$env:Path"
$env:DB_USERNAME="root"
$env:DB_PASSWORD="你的数据库密码"
powershell -ExecutionPolicy Bypass -File .\scripts\start-backend.ps1
```

### 前端
```powershell
cd e:\landslide-ai-system\frontend
$env:Path="C:\Program Files\nodejs;$env:Path"
npm install
npm run dev
```

## 访问地址
- 前端：`http://localhost:5173`（若端口占用，Vite 会自动切换）
- 后端：`http://localhost:8080`
- 健康检查：`http://localhost:8080/api/health`
- 智能助手（LangChain，一键启动后）：`http://localhost:8000/docs`

## 默认测试账号
密码均为 `123456`：
- `superadmin`
- `admin`
- `monitor`

你也可以从登录页进入“注册”创建新的监测员账号。

## 当前可演示功能
- 登录/注册（JWT）
- 识别任务（图片上传、结果可视化）
- 灾情等级判定（I/II/III/IV）
- 历史记录（分页、筛选、导出）
- 灾情动态（WebSocket 实时预警、确认处理）
- 个人中心（查看并修改真实姓名、手机号）
- 用户/角色管理（管理员与超级管理员）

## YOLO 模型说明
模型文件路径：
- `backend/models/landslide-yolov8.onnx`

若模型不存在，系统会降级启动（管理类功能仍可使用）。

## 常见问题
- 页面提示 `ECONNREFUSED`：后端未启动或端口不通
- 前端白屏或 Vite 报模板错误：通常是文件编码污染，建议 `Ctrl + F5` 并检查近期改动
- `mvn` 找不到：检查 `MAVEN_HOME` 与 `Path`
- 识别框过多/过少：调整后端 `ai.yolo.conf-threshold`
- 预警列表加载失败：先检查 `http://localhost:8080/api/health` 是否可访问

## 答辩演示建议
请参考：`docs/demo-script.md`


## 智能助手（对话）

- 登录后在侧边栏进入 **「智能助手」**；Java 后端把请求转发到 Python（配置项 **`chat.api.chat-url`**，默认 **`http://localhost:8000/chat`**）。
- **端口说明**：同一台电脑上 **`fastapi-chat`**（离线占位）与 **`langchain-chat-api`**（真模型）不要同时占用 **8000**，用哪一个就只启动哪一个。

### 真模型连续对话（推荐答辩演示）

1. 在 [DeepSeek](https://platform.deepseek.com/) 等平台创建 **API Key**。
2. **关掉** 占着 8000 端口的 `fastapi-chat` 进程（若之前启过）。
3. 在 PowerShell 中执行（将密钥换成你的）：

```powershell
Set-Location -LiteralPath "e:\landslide-ai-system\langchain-chat-api"
$env:DEEPSEEK_API_KEY = "sk-你的密钥"
.\start-langchain-chat.ps1
```



若提示「禁止运行脚本」：在同一窗口先执行 Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass，再执行 `.\start-langchain-chat.ps1`；或改用：powershell -ExecutionPolicy Bypass -File .\start-langchain-chat.ps1。

若出现「文件名、目录名或卷标语法不正确」：用 PowerShell（不要用 CMD）逐行执行上面三行；引号用英文半角；勿嵌套外层 powershell。

或手动：`python -m venv .venv` → `pip install -r requirements.txt` → `python -m uvicorn main:app --host 0.0.0.0 --port 8000`（同样要先设置 **`DEEPSEEK_API_KEY`**）。

4. 保持 Spring Boot、前端已启动，浏览器打开 **智能助手** 即可多轮对话。服务端按登录用户区分会话（默认会话 id：`landslide-user-用户名`），历史长度见环境变量 **`CHAT_MAX_ROUNDS`**（默认约 10 轮）。

更完整的 Python 端说明见：`langchain-chat-api/README.md`。

### 离线占位（无 API Key 时）

- 使用 **`fastapi-chat`**：规则/关键词回复，用于不联网或断 Key 时演示。启动后同样占用 8000，**与真模型二选一**。

### 接口约定（给联调用）

- `POST /api/assistant/chat`：请求体 `userInput`，可选 `language`、`sessionId`；清空上下文：`POST /api/assistant/chat/reset`。

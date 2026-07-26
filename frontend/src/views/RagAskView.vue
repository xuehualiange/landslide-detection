<template>
  <el-card>
    <template #header>
      <div class="hdr">
        <span>知识库智能问答（RAG）</span>
        <el-button size="small" @click="clearHistory">清空记录</el-button>
      </div>
    </template>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      <template #title>
        <span>基于滑坡知识库检索增强问答，流式生成回答（引用来源暂不可用）。</span>
      </template>
      <div class="hint-links">
        <router-link to="/ai-chat">滑坡智能助手</router-link>
        <span class="sep">·</span>
        <router-link to="/detect-task">识别任务</router-link>
      </div>
    </el-alert>
    <div class="quick-bar">
      <span class="qb-label">快捷提问：</span>
      <el-tag size="small" class="qb-tag" @click="fillDraft('什么是滑坡？')">什么是滑坡？</el-tag>
      <el-tag size="small" class="qb-tag" @click="fillDraft('滑坡常见诱发因素有哪些？')">诱发因素</el-tag>
      <el-tag size="small" class="qb-tag" @click="fillDraft('遥感如何监测滑坡？')">遥感监测</el-tag>
    </div>
    <div ref="scrollRef" class="msgs">
      <div v-if="!messages.length" class="empty">在下方输入问题，开始知识库问答</div>
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
        <div class="who">{{ m.role === "user" ? "我" : "知识库助手" }}</div>
        <div class="bubble">
          {{ m.text }}<span v-if="m.streaming" class="stream-cursor">|</span>
        </div>
      </div>
    </div>
    <div class="inp-row">
      <el-input
        v-model="draft"
        type="textarea"
        :rows="3"
        placeholder="例如：什么是滑坡？常见诱发因素有哪些？（Shift+Enter 换行）"
        @keydown.enter.exact.prevent="send"
      />
      <el-button type="primary" :loading="loading" style="margin-left: 8px" @click="send">发送</el-button>
    </div>
  </el-card>
</template>

<script setup>
// 知识库 RAG 问答：直连 rag-service POST /qa/stream（SSE 流式）
import { nextTick, ref } from "vue";
import { ElMessage } from "element-plus";

const RAG_STREAM_URL =
  import.meta.env.VITE_RAG_STREAM_URL || "http://localhost:8000/qa/stream";

const messages = ref([]);
const draft = ref("");
const loading = ref(false);
const scrollRef = ref(null);

const fillDraft = (text) => {
  draft.value = text;
};

const scrollBottom = async () => {
  await nextTick();
  const el = scrollRef.value;
  if (el) el.scrollTop = el.scrollHeight;
};

/** 解析 SSE 行：data: {...} 或 data: [DONE] */
const parseSseLine = (line, onContent) => {
  if (!line.startsWith("data: ")) return "skip";
  const payload = line.slice(6).trim();
  if (payload === "[DONE]") return "done";
  try {
    const parsed = JSON.parse(payload);
    const piece = parsed.content;
    if (piece) onContent(String(piece));
  } catch {
    /* 忽略非 JSON 行 */
  }
  return "continue";
};

/** fetch 流式读取 + TextDecoder 按行解析 SSE */
const consumeSseStream = async (response, onContent) => {
  const reader = response.body.getReader();
  const decoder = new TextDecoder();
  let buffer = "";

  while (true) {
    const { done, value } = await reader.read();
    if (done) break;
    buffer += decoder.decode(value, { stream: true });
    const lines = buffer.split("\n");
    buffer = lines.pop() || "";
    for (const line of lines) {
      const status = parseSseLine(line.trim(), onContent);
      if (status === "done") return;
    }
  }

  const tail = buffer.trim();
  if (tail) {
    const status = parseSseLine(tail, onContent);
    if (status === "done") return;
  }
};

const send = async () => {
  const text = (draft.value || "").trim();
  if (!text || loading.value) return;
  loading.value = true;
  messages.value.push({ role: "user", text });
  draft.value = "";

  const assistantIdx = messages.value.length;
  messages.value.push({ role: "assistant", text: "", streaming: true });
  await scrollBottom();

  try {
    const response = await fetch(RAG_STREAM_URL, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ question: text })
    });

    if (!response.ok) {
      let detail = response.statusText;
      try {
        const errBody = await response.json();
        detail = errBody.detail || detail;
      } catch {
        /* 非 JSON 错误体 */
      }
      throw new Error(detail || `HTTP ${response.status}`);
    }

    if (!response.body) {
      throw new Error("浏览器不支持流式响应");
    }

    await consumeSseStream(response, (chunk) => {
      messages.value[assistantIdx].text += chunk;
      scrollBottom();
    });

    if (!messages.value[assistantIdx].text) {
      messages.value[assistantIdx].text = "（空回复）";
    }
  } catch (e) {
    const msg = e?.message || "请求失败";
    ElMessage.error(msg);
    messages.value[assistantIdx].text = "请求异常：" + msg;
  } finally {
    messages.value[assistantIdx].streaming = false;
    loading.value = false;
    await scrollBottom();
  }
};

const clearHistory = () => {
  messages.value = [];
  ElMessage.success("已清空对话记录");
};
</script>

<style scoped>
.hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.msgs {
  max-height: 480px;
  overflow-y: auto;
  padding: 8px;
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 12px;
}
.empty {
  text-align: center;
  color: #909399;
  padding: 32px 12px;
  font-size: 14px;
}
.msg {
  margin-bottom: 12px;
}
.msg.user .bubble {
  background: #ecf5ff;
}
.msg.assistant .bubble {
  background: #fff;
  border: 1px solid #ebeef5;
}
.who {
  font-size: 12px;
  color: #909399;
  margin-bottom: 4px;
}
.bubble {
  padding: 10px 12px;
  border-radius: 8px;
  white-space: pre-wrap;
  word-break: break-word;
}
.stream-cursor {
  display: inline-block;
  margin-left: 1px;
  color: var(--el-color-primary);
  animation: blink 1s step-end infinite;
}
@keyframes blink {
  50% {
    opacity: 0;
  }
}
.inp-row {
  display: flex;
  align-items: flex-end;
}
.inp-row .el-input {
  flex: 1;
}
.hint-links {
  margin-top: 6px;
  font-size: 13px;
}
.hint-links a {
  color: var(--el-color-primary);
}
.sep {
  margin: 0 6px;
  color: #909399;
}
.quick-bar {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 8px;
  margin-bottom: 12px;
}
.qb-label {
  font-size: 13px;
  color: #606266;
}
.qb-tag {
  cursor: pointer;
}
</style>

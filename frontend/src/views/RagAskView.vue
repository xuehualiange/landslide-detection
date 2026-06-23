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
        <span>基于滑坡知识库检索增强问答：回答会附带引用来源，仅依据知识库内容作答。</span>
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
        <div class="bubble">{{ m.text }}</div>
        <div v-if="m.sources && m.sources.length" class="sources">
          <div class="src-title">引用来源（{{ m.sources.length }}）</div>
          <el-collapse accordion>
            <el-collapse-item
              v-for="(s, j) in m.sources"
              :key="j"
              :title="sourceLabel(s.source)"
              :name="String(j)"
            >
              <div class="snippet">{{ s.snippet || "（无摘要）" }}</div>
            </el-collapse-item>
          </el-collapse>
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
// 知识库 RAG 问答：调用后端 /api/rag/ask，由 Java 转发 Python rag-service
import { nextTick, ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../utils/request";

const messages = ref([]);
const draft = ref("");
const loading = ref(false);
const scrollRef = ref(null);

const fillDraft = (text) => {
  draft.value = text;
};

const sourceLabel = (path) => {
  if (!path) return "未知来源";
  const normalized = String(path).replace(/\\/g, "/");
  const name = normalized.split("/").pop();
  return name || path;
};

const scrollBottom = async () => {
  await nextTick();
  const el = scrollRef.value;
  if (el) el.scrollTop = el.scrollHeight;
};

const send = async () => {
  const text = (draft.value || "").trim();
  if (!text || loading.value) return;
  loading.value = true;
  messages.value.push({ role: "user", text });
  draft.value = "";
  await scrollBottom();
  try {
    const { data } = await request.post(
      "/rag/ask",
      { question: text },
      { timeout: 120000 }
    );
    if (data.code !== 200) {
      ElMessage.error(data.message || "发送失败");
      messages.value.push({
        role: "assistant",
        text: data.message || "（未返回）",
        sources: []
      });
      await scrollBottom();
      return;
    }
    const payload = data.data || {};
    const answer = payload.answer != null ? String(payload.answer) : "";
    const sources = Array.isArray(payload.sources) ? payload.sources : [];
    messages.value.push({
      role: "assistant",
      text: answer || "（空回复）",
      sources
    });
  } catch (e) {
    const d = e?.response?.data;
    const msg =
      (d && typeof d === "object" && d.message) ||
      (typeof d === "string" ? d : "") ||
      e?.message ||
      "请求失败";
    ElMessage.error(msg);
    messages.value.push({
      role: "assistant",
      text: "请求异常：" + msg,
      sources: []
    });
  } finally {
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
.sources {
  margin-top: 8px;
  padding: 0 4px;
}
.src-title {
  font-size: 12px;
  color: #606266;
  margin-bottom: 6px;
}
.snippet {
  white-space: pre-wrap;
  word-break: break-word;
  font-size: 13px;
  color: #606266;
  line-height: 1.6;
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

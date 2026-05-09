<template>
  <el-card>
    <template #header>
      <div class="hdr">
        <span>滑坡智能助手</span>
        <el-button size="small" @click="clearSession">清空上下文</el-button>
      </div>
    </template>
    <el-alert type="info" :closable="false" show-icon style="margin-bottom: 12px">
      <template #title>
        <span>滑坡智能助手：解释灾情等级、置信度等指标；结合你最近的识别摘要回答问题；支持多轮对话记忆。</span>
      </template>
      <div class="hint-links">
        <router-link to="/history">历史记录</router-link>
        <span class="sep">·</span>
        <router-link to="/detect-task">识别任务</router-link>
      </div>
    </el-alert>
    <div class="quick-bar">
      <span class="qb-label">快捷提问：</span>
      <el-tag size="small" class="qb-tag" @click="fillDraft('我最近有哪些识别记录？')">我最近有哪些识别记录？</el-tag>
      <el-tag size="small" class="qb-tag" @click="fillDraft('灾情等级 I、II、III、IV 分别代表什么？')">灾情等级含义</el-tag>
      <el-tag size="small" class="qb-tag" @click="fillDraft('系统里置信度和检测框是什么意思？')">置信度与检测框</el-tag>
    </div>
    <div ref="scrollRef" class="msgs">
      <div v-for="(m, i) in messages" :key="i" :class="['msg', m.role]">
        <div class="who">{{ m.role === "user" ? "我" : "助手" }}</div>
        <div class="bubble">{{ m.text }}</div>
      </div>
    </div>
    <div class="inp-row">
      <el-input
        v-model="draft"
        type="textarea"
        :rows="3"
        placeholder="例如：我最近一次识别结果怎样？灾情等级怎么理解？（Shift+Enter 换行）"
        @keydown.enter.exact.prevent="send"
      />
      <el-button type="primary" :loading="loading" style="margin-left: 8px" @click="send">发送</el-button>
    </div>
  </el-card>
</template>

<script setup>
import { nextTick, ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../utils/request";

const messages = ref([]);
const draft = ref("");
const loading = ref(false);
const scrollRef = ref(null);

const fillDraft = (t) => {
  draft.value = t;
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
      "/assistant/chat",
      {
        userInput: text,
        language: "中文"
      },
      { timeout: 120000 }
    );
    if (data.code !== 200) {
      ElMessage.error(data.message || "发送失败");
      messages.value.push({
        role: "assistant",
        text: data.message ? `（未返回）${data.message}` : "(未返回)"
      });
      await scrollBottom();
      return;
    }
    const reply = data.data && data.data.reply != null ? String(data.data.reply) : "";
    messages.value.push({ role: "assistant", text: reply || "(空回复)" });
  } catch (e) {
    const d = e?.response?.data;
    const msg =
      (d && typeof d === "object" && d.message) ||
      (typeof d === "string" ? d : "") ||
      e?.message ||
      "请求失败";
    ElMessage.error(msg);
    messages.value.push({ role: "assistant", text: "请求异常：" + msg });
  } finally {
    loading.value = false;
    await scrollBottom();
  }
};

const clearSession = async () => {
  try {
    const { data } = await request.post("/assistant/chat/reset");
    if (data.code !== 200) {
      ElMessage.warning(data.message || "清空失败");
      return;
    }
    messages.value = [];
    ElMessage.success(data.data || "已清空");
  } catch (e) {
    ElMessage.error(e?.response?.data?.message || "清空失败");
  }
};
</script>

<style scoped>
.hdr {
  display: flex;
  align-items: center;
  justify-content: space-between;
}
.msgs {
  max-height: 420px;
  overflow-y: auto;
  padding: 8px;
  background: #fafafa;
  border-radius: 8px;
  margin-bottom: 12px;
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
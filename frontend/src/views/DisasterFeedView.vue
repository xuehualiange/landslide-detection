<template>
  <el-card>
    <template #header>
      <div class="header-row">
        <span>灾情动态（实时预警）</span>
        <el-space>
          <el-tag :type="connected ? 'success' : 'danger'">
            {{ connected ? '实时通道已连接' : '实时通道未连接' }}
          </el-tag>
          <el-tag type="warning">未确认：{{ unreadCount }}</el-tag>
          <el-button size="small" @click="reconnect">重连</el-button>
          <el-button size="small" @click="loadEvents">刷新</el-button>
        </el-space>
      </div>
    </template>

    <el-alert
      v-if="!connected"
      type="warning"
      :closable="false"
      title="当前未连接到预警推送服务，请确认后端已启动且 /ws 可访问。"
      style="margin-bottom: 12px"
    />

    <div class="toolbar-row">
      <el-space>
        <span>状态筛选：</span>
        <el-select v-model="statusFilter" style="width: 140px" @change="onFilterChange">
          <el-option label="全部" value="ALL" />
          <el-option label="未确认" value="UNREAD" />
          <el-option label="已确认" value="ACK" />
        </el-select>
      </el-space>
    </div>

    <el-table :data="messages" border>
      <el-table-column prop="createdTime" label="时间" min-width="190" />
      <el-table-column prop="message" label="预警内容" min-width="320" />
      <el-table-column label="灾情等级" min-width="120">
        <template #default="{ row }">
          <el-tag :type="levelTagType(row.disasterLevel)">{{ row.disasterLevel || '-' }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column prop="landslideArea" label="滑坡面积" min-width="120" />
      <el-table-column prop="maxConfidence" label="最大置信度" min-width="120" />
      <el-table-column prop="latestDeformationRate" label="变形速率(mm/天)" min-width="150" />
      <el-table-column prop="status" label="状态" min-width="110">
        <template #default="{ row }">
          <el-tag :type="row.status === 'ACK' ? 'success' : 'danger'">
            {{ row.status === 'ACK' ? '已确认' : '未确认' }}
          </el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" min-width="120">
        <template #default="{ row }">
          <el-button
            v-if="row.status !== 'ACK'"
            type="primary"
            size="small"
            @click="ackEvent(row.id)"
          >
            确认
          </el-button>
          <span v-else>-</span>
        </template>
      </el-table-column>
    </el-table>

    <div class="pager-row">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :current-page="page"
        :page-size="pageSize"
        :page-sizes="[10, 20, 50, 100]"
        @current-change="onPageChange"
        @size-change="onSizeChange"
      />
    </div>
  </el-card>
</template>

<script setup>
import { onBeforeUnmount, onMounted, ref } from "vue";
import { ElMessage } from "element-plus";
import SockJS from "sockjs-client/dist/sockjs";
import { Client } from "@stomp/stompjs";
import request from "../utils/request";

const connected = ref(false);
const unreadCount = ref(0);
const messages = ref([]);
const statusFilter = ref("ALL");
const page = ref(1);
const pageSize = ref(20);
const total = ref(0);
let stompClient = null;
let stompErrorNotified = false;

const wsEndpoint =
  import.meta.env.VITE_WS_URL ||
  (import.meta.env.DEV
    ? `${window.location.protocol}//${window.location.host}/ws`
    : `${window.location.protocol}//${window.location.hostname}:8080/ws`);

/** Poll until Spring health returns UP (avoids SockJS racing the JVM). */
const waitForBackendReady = async (maxMs = 120000, stepMs = 1500) => {
  const start = Date.now();
  while (Date.now() - start < maxMs) {
    try {
      const { data } = await request.get("/health", { timeout: 4000 });
      if (data.code === 200 && data.data?.status === "UP") {
        return true;
      }
    } catch {
      /* retry */
    }
    await new Promise((r) => setTimeout(r, stepMs));
  }
  return false;
};

const levelTagType = (level) => {
  if (level?.startsWith("I")) return "danger";
  if (level?.startsWith("II")) return "warning";
  if (level?.startsWith("III")) return "success";
  return "info";
};

const normalizeEvent = (payload = {}) => ({
  id: payload.id,
  createdTime: payload.createdTime || payload.time || new Date().toISOString(),
  message: payload.message || "(无消息内容)",
  disasterLevel: payload.disasterLevel || "-",
  landslideArea: payload.landslideArea ?? "-",
  maxConfidence: payload.maxConfidence ?? "-",
  latestDeformationRate: payload.latestDeformationRate ?? "-",
  status: payload.status || "UNREAD"
});

const loadUnreadCount = async () => {
  try {
    const { data } = await request.get("/warning/unread-count");
    if (data.code === 200) {
      unreadCount.value = data.data?.unread ?? 0;
    }
  } catch (error) {
    unreadCount.value = 0;
  }
};

const loadEvents = async () => {
  try {
    const { data } = await request.get("/warning/events", {
      params: {
        status: statusFilter.value,
        page: page.value,
        pageSize: pageSize.value
      }
    });
    if (data.code === 200) {
      const records = data.data?.records || [];
      messages.value = records.map(normalizeEvent);
      total.value = data.data?.total || 0;
    }
  } catch (error) {
    ElMessage.error("预警事件加载失败");
  } finally {
    loadUnreadCount();
  }
};

const ackEvent = async (id) => {
  try {
    const { data } = await request.post(`/warning/events/${id}/ack`);
    if (data.code !== 200) {
      ElMessage.error(data.message || "确认失败");
      return;
    }
    ElMessage.success("预警已确认");
    await loadEvents();
  } catch (error) {
    ElMessage.error("确认失败");
  }
};

const onFilterChange = async () => {
  page.value = 1;
  await loadEvents();
};

const onPageChange = async (nextPage) => {
  page.value = nextPage;
  await loadEvents();
};

const onSizeChange = async (nextSize) => {
  pageSize.value = nextSize;
  page.value = 1;
  await loadEvents();
};

const connect = () => {
  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
  }

  stompClient = new Client({
    webSocketFactory: () => new SockJS(wsEndpoint),
    connectionTimeout: 20000,
    reconnectDelay: 4000,
    maxReconnectDelay: 60000,
    onConnect: () => {
      connected.value = true;
      stompErrorNotified = false;
      stompClient.subscribe("/topic/warning/nearby", (frame) => {
        try {
          const payload = JSON.parse(frame.body || "{}");
          const event = normalizeEvent(payload);

          if (statusFilter.value === "ALL" || statusFilter.value === event.status) {
            messages.value.unshift(event);
            if (messages.value.length > pageSize.value) {
              messages.value.pop();
            }
          }

          total.value += 1;
          unreadCount.value += 1;
        } catch (error) {
          // ignore malformed payload
        }
      });
    },
    onStompError: () => {
      connected.value = false;
      if (!stompErrorNotified) {
        stompErrorNotified = true;
        ElMessage.error("预警通道订阅失败（将自动重试）");
      }
    },
    onWebSocketClose: () => {
      connected.value = false;
    }
  });

  stompClient.activate();
};

const reconnect = () => {
  connect();
};

onMounted(async () => {
  const ready = await waitForBackendReady();
  if (!ready) {
    ElMessage.warning("后端暂未就绪，实时通道将后台自动重试连接");
  }
  await loadEvents();
  connect();
});

onBeforeUnmount(() => {
  if (stompClient) {
    stompClient.deactivate();
    stompClient = null;
  }
});
</script>

<style scoped>
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.toolbar-row {
  margin-bottom: 12px;
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.pager-row {
  margin-top: 12px;
  display: flex;
  justify-content: flex-end;
}
</style>
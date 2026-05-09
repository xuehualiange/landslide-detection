<template>
  <div style="padding: 24px">
    <el-space direction="vertical" fill>
      <el-card>
        <div>当前用户：{{ auth.username }}（{{ auth.role }}）</div>
      </el-card>
      <el-card>
        <el-space>
          <el-button type="primary" @click="goDetect">进入图像识别</el-button>
          <el-button @click="loadWarnings">查看预警列表</el-button>
          <el-button type="danger" @click="logout">退出登录</el-button>
        </el-space>
      </el-card>
      <el-card v-if="warnings.length">
        <div v-for="(item, i) in warnings" :key="i" style="margin: 8px 0">{{ item }}</div>
      </el-card>
    </el-space>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { useAuthStore } from "../stores/auth";
import request from "../utils/request";

const router = useRouter();
const auth = useAuthStore();
const warnings = ref([]);

const goDetect = () => router.push("/detect");
const logout = () => {
  auth.logout();
  router.push("/login");
};
const loadWarnings = async () => {
  const { data } = await request.get("/warning/list");
  if (data.code !== 200) {
    ElMessage.error(data.message);
    return;
  }
  warnings.value = data.data;
};
</script>

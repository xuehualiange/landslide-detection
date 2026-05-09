<template>
  <el-container style="min-height: 100vh">
    <el-aside width="220px" style="background: #001529">
      <div class="logo">滑坡识别系统</div>
      <el-menu
        :default-active="activePath"
        background-color="#001529"
        text-color="#d8dce6"
        active-text-color="#409eff"
        router
      >
        <el-menu-item v-for="item in visibleMenus" :key="item.path" :index="item.path">
          {{ item.title }}
        </el-menu-item>
      </el-menu>
    </el-aside>

    <el-container>
      <el-header class="header">
        <div>当前用户：{{ auth.username }}（{{ roleLabel }}）</div>
        <el-button type="danger" size="small" @click="logout">退出登录</el-button>
      </el-header>
      <el-main style="background: #f5f7fa">
        <router-view />
      </el-main>
    </el-container>
  </el-container>
</template>

<script setup>
import { computed } from "vue";
import { useRoute, useRouter } from "vue-router";
import { useAuthStore } from "../stores/auth";

const route = useRoute();
const router = useRouter();
const auth = useAuthStore();

const allMenus = [
  { path: "/detect-task", title: "识别任务", roles: ["MONITOR", "ADMIN", "SUPER_ADMIN"] },
  { path: "/ai-chat", title: "智能助手", roles: ["MONITOR", "ADMIN", "SUPER_ADMIN"] },
  { path: "/history", title: "历史记录", roles: ["MONITOR", "ADMIN", "SUPER_ADMIN"] },
  { path: "/profile", title: "个人中心", roles: ["MONITOR", "ADMIN", "SUPER_ADMIN"] },
  { path: "/users", title: "用户管理", roles: ["ADMIN", "SUPER_ADMIN"] },
  { path: "/disaster-feed", title: "灾情动态", roles: ["ADMIN", "SUPER_ADMIN"] },
  { path: "/roles", title: "角色管理", roles: ["SUPER_ADMIN"] }
];

const visibleMenus = computed(() => allMenus.filter((item) => item.roles.includes(auth.role)));
const activePath = computed(() => route.path);
const roleLabel = computed(() => {
  if (auth.role === "SUPER_ADMIN") return "超级管理员";
  if (auth.role === "ADMIN") return "管理员";
  if (auth.role === "MONITOR") return "监测员";
  return auth.role || "-";
});

const logout = () => {
  auth.logout();
  router.push("/login");
};
</script>

<style scoped>
.logo {
  color: #fff;
  font-size: 16px;
  font-weight: 600;
  text-align: center;
  padding: 18px 12px;
  border-bottom: 1px solid rgba(255, 255, 255, 0.12);
}

.header {
  background: #fff;
  border-bottom: 1px solid #ebeef5;
  display: flex;
  align-items: center;
  justify-content: space-between;
}
</style>
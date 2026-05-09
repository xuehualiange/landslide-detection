<template>
  <div style="max-width: 380px; margin: 120px auto">
    <el-card>
      <template #header>滑坡识别系统登录</template>
      <el-form :model="form" label-width="80px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="记住我">
          <el-switch v-model="form.rememberMe" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" @click="doLogin">登录</el-button>
        <div style="margin-top: 12px; text-align: right">
          <el-link type="primary" @click="goRegister">没有账号？去注册</el-link>
        </div>
      </el-form>
    </el-card>
  </div>
</template>

<script setup>
import { reactive } from "vue";
import { ElMessage } from "element-plus";
import { useRouter } from "vue-router";
import request from "../utils/request";
import { useAuthStore } from "../stores/auth";

const router = useRouter();
const auth = useAuthStore();
const form = reactive({ username: "admin", password: "123456", rememberMe: true });

const goRegister = () => {
  router.push("/register");
};

const doLogin = async () => {
  try {
    const payload = { username: form.username, password: form.password };
    const { data } = await request.post("/user/login", payload);
    if (data.code !== 200) {
      ElMessage.error(data.message || "登录失败");
      return;
    }

    auth.setAuth(
      {
        token: data.data?.token || "",
        username: data.data?.username || form.username,
        role: data.data?.role || "MONITOR"
      },
      form.rememberMe
    );
    ElMessage.success("登录成功");
    router.push("/detect-task");
  } catch (error) {
    const d = error?.response?.data;
    const msg =
      (d && typeof d === "object" && d.message) ||
      error?.message ||
      "登录请求失败";
    ElMessage.error(msg);
  }
};
</script>
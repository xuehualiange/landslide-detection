<template>
  <div style="max-width: 420px; margin: 100px auto">
    <el-card>
      <template #header>用户注册</template>
      <el-form :model="form" label-width="90px">
        <el-form-item label="用户名">
          <el-input v-model="form.username" />
        </el-form-item>
        <el-form-item label="密码">
          <el-input v-model="form.password" type="password" show-password />
        </el-form-item>
        <el-form-item label="真实姓名">
          <el-input v-model="form.realName" />
        </el-form-item>
        <el-form-item label="手机号">
          <el-input v-model="form.phone" />
        </el-form-item>
        <el-button type="primary" style="width: 100%" @click="doRegister">注册</el-button>
        <div style="margin-top: 12px; text-align: right">
          <el-link type="primary" @click="goLogin">返回登录</el-link>
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

const router = useRouter();
const form = reactive({
  username: "",
  password: "",
  realName: "",
  phone: ""
});

const doRegister = async () => {
  if (!form.username || !form.password) {
    ElMessage.warning("用户名和密码不能为空");
    return;
  }

  try {
    const payload = {
      username: form.username,
      password: form.password,
      realName: form.realName,
      phone: form.phone
    };
    const { data } = await request.post("/user/register", payload);
    if (data.code !== 200) {
      ElMessage.error(data.message || "注册失败");
      return;
    }
    ElMessage.success("注册成功，请登录");
    router.push("/login");
  } catch (error) {
    ElMessage.error("注册请求失败");
  }
};

const goLogin = () => {
  router.push("/login");
};
</script>

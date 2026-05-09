<template>
  <el-card>
    <template #header>个人中心</template>

    <el-descriptions :column="1" border style="margin-bottom: 16px">
      <el-descriptions-item label="用户名">{{ profile.username || auth.username }}</el-descriptions-item>
      <el-descriptions-item label="角色">{{ roleLabel(profile.role || auth.role) }}</el-descriptions-item>

      <template v-if="!editing">
        <el-descriptions-item label="真实姓名">{{ profile.realName || "-" }}</el-descriptions-item>
        <el-descriptions-item label="手机号">{{ profile.phone || "-" }}</el-descriptions-item>
      </template>

      <template v-else>
        <el-descriptions-item label="真实姓名">
          <el-input v-model="form.realName" style="max-width: 320px" />
        </el-descriptions-item>
        <el-descriptions-item label="手机号">
          <el-input v-model="form.phone" style="max-width: 320px" />
        </el-descriptions-item>
      </template>
    </el-descriptions>

    <div style="display: flex; justify-content: flex-end; gap: 8px">
      <el-button v-if="!editing" type="primary" @click="startEdit">修改资料</el-button>
      <template v-else>
        <el-button @click="cancelEdit">取消</el-button>
        <el-button type="primary" :loading="saving" @click="saveProfile">保存</el-button>
      </template>
    </div>
  </el-card>
</template>

<script setup>
import { onMounted, reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../utils/request";
import { useAuthStore } from "../stores/auth";

const auth = useAuthStore();
const saving = ref(false);
const editing = ref(false);

const profile = reactive({
  username: "",
  role: "",
  realName: "",
  phone: ""
});

const form = reactive({
  realName: "",
  phone: ""
});

const roleLabel = (role) => {
  if (role === "SUPER_ADMIN") return "超级管理员";
  if (role === "ADMIN") return "管理员";
  if (role === "MONITOR") return "监测员";
  return role || "-";
};

const fillFormFromProfile = () => {
  form.realName = profile.realName || "";
  form.phone = profile.phone || "";
};

const loadProfile = async () => {
  try {
    const { data } = await request.get("/user/profile");
    if (data.code !== 200) {
      ElMessage.error(data.message || "加载个人信息失败");
      return;
    }

    const user = data.data || {};
    profile.username = user.username || auth.username || "";
    profile.role = user.role || auth.role || "";
    profile.realName = user.realName || "";
    profile.phone = user.phone || "";
    fillFormFromProfile();
  } catch (error) {
    ElMessage.error("加载个人信息失败");
  }
};

const startEdit = () => {
  fillFormFromProfile();
  editing.value = true;
};

const cancelEdit = () => {
  fillFormFromProfile();
  editing.value = false;
};

const saveProfile = async () => {
  if (!form.realName || !form.realName.trim()) {
    ElMessage.warning("真实姓名不能为空");
    return;
  }

  saving.value = true;
  try {
    const payload = {
      realName: form.realName,
      phone: form.phone
    };
    const { data } = await request.put("/user/profile", payload);
    if (data.code !== 200) {
      ElMessage.error(data.message || "保存失败");
      return;
    }

    profile.realName = form.realName;
    profile.phone = form.phone;
    editing.value = false;
    ElMessage.success("保存成功");
  } catch (error) {
    ElMessage.error("保存失败");
  } finally {
    saving.value = false;
  }
};

onMounted(() => {
  loadProfile();
});
</script>
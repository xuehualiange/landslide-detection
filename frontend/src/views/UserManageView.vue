<template>
  <el-card>
    <template #header>
      <div class="header-row">
        <span>用户管理</span>
        <el-button type="primary" @click="openCreateDialog">新增用户</el-button>
      </div>
    </template>

    <el-table :data="tableData" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="username" label="用户名" min-width="120" />
      <el-table-column prop="realName" label="真实姓名" min-width="120" />
      <el-table-column prop="phone" label="手机号" min-width="140" />
      <el-table-column prop="roleId" label="角色" min-width="120">
        <template #default="{ row }">{{ roleLabel(row.roleId) }}</template>
      </el-table-column>
      <el-table-column prop="status" label="状态" width="100">
        <template #default="{ row }">
          <el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? "启用" : "禁用" }}</el-tag>
        </template>
      </el-table-column>
      <el-table-column label="操作" width="340" fixed="right">
        <template #default="{ row }">
          <el-space>
            <el-button size="small" @click="openEditDialog(row)">编辑</el-button>
            <el-button
              size="small"
              :type="row.status === 1 ? 'warning' : 'success'"
              @click="toggleStatus(row)"
            >
              {{ row.status === 1 ? "禁用" : "启用" }}
            </el-button>
            <el-popconfirm title="确认删除该用户？" @confirm="removeUser(row)">
              <template #reference>
                <el-button size="small" type="danger">删除</el-button>
              </template>
            </el-popconfirm>
          </el-space>
        </template>
      </el-table-column>
    </el-table>

    <div style="display: flex; justify-content: flex-end; margin-top: 16px">
      <el-pagination
        background
        layout="total, sizes, prev, pager, next"
        :total="total"
        :page-size="pageSize"
        :current-page="pageNum"
        :page-sizes="[10, 20, 50]"
        @size-change="onSizeChange"
        @current-change="onCurrentChange"
      />
    </div>
  </el-card>

  <el-dialog v-model="createVisible" title="新增用户" width="520px" destroy-on-close>
    <el-form :model="createForm" label-width="90px">
      <el-form-item label="用户名" required>
        <el-input v-model="createForm.username" />
      </el-form-item>
      <el-form-item label="密码" required>
        <el-input v-model="createForm.password" type="password" show-password />
      </el-form-item>
      <el-form-item label="真实姓名" required>
        <el-input v-model="createForm.realName" />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="createForm.phone" />
      </el-form-item>
      <el-form-item label="角色" required>
        <el-select v-model="createForm.roleId" style="width: 100%">
          <el-option v-for="role in roleOptions" :key="role.value" :label="role.label" :value="role.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" required>
        <el-select v-model="createForm.status" style="width: 100%">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="createVisible = false">取消</el-button>
      <el-button type="primary" @click="createUser">确定</el-button>
    </template>
  </el-dialog>

  <el-dialog v-model="editVisible" title="编辑用户" width="520px" destroy-on-close>
    <el-form :model="editForm" label-width="90px">
      <el-form-item label="用户名">
        <el-input v-model="editForm.username" disabled />
      </el-form-item>
      <el-form-item label="新密码">
        <el-input v-model="editForm.password" type="password" show-password placeholder="不填则不修改" />
      </el-form-item>
      <el-form-item label="真实姓名" required>
        <el-input v-model="editForm.realName" />
      </el-form-item>
      <el-form-item label="手机号">
        <el-input v-model="editForm.phone" />
      </el-form-item>
      <el-form-item label="角色" required>
        <el-select v-model="editForm.roleId" style="width: 100%">
          <el-option v-for="role in roleOptions" :key="role.value" :label="role.label" :value="role.value" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态" required>
        <el-select v-model="editForm.status" style="width: 100%">
          <el-option label="启用" :value="1" />
          <el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="editVisible = false">取消</el-button>
      <el-button type="primary" @click="updateUser">保存</el-button>
    </template>
  </el-dialog>
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../utils/request";

const roleOptions = [
  { label: "超级管理员", value: 1 },
  { label: "管理员", value: 2 },
  { label: "监测人员", value: 3 }
];

const tableData = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(10);

const createVisible = ref(false);
const editVisible = ref(false);

const createForm = reactive({
  username: "",
  password: "",
  realName: "",
  phone: "",
  roleId: 3,
  status: 1
});

const editForm = reactive({
  id: null,
  username: "",
  password: "",
  realName: "",
  phone: "",
  roleId: 3,
  status: 1
});

const roleLabel = (roleId) => {
  const role = roleOptions.find((item) => item.value === roleId);
  return role ? role.label : `角色(${roleId})`;
};

const resetCreateForm = () => {
  createForm.username = "";
  createForm.password = "";
  createForm.realName = "";
  createForm.phone = "";
  createForm.roleId = 3;
  createForm.status = 1;
};

const openCreateDialog = () => {
  resetCreateForm();
  createVisible.value = true;
};

const openEditDialog = (row) => {
  editForm.id = row.id;
  editForm.username = row.username;
  editForm.password = "";
  editForm.realName = row.realName;
  editForm.phone = row.phone || "";
  editForm.roleId = row.roleId;
  editForm.status = row.status;
  editVisible.value = true;
};

const validateCreate = () => {
  if (!createForm.username || !createForm.password || !createForm.realName) {
    ElMessage.warning("请填写用户名、密码、真实姓名");
    return false;
  }
  return true;
};

const validateEdit = () => {
  if (!editForm.realName) {
    ElMessage.warning("请填写真实姓名");
    return false;
  }
  return true;
};

const loadUsers = async () => {
  const { data } = await request.get("/admin/users", {
    params: {
      pageNum: pageNum.value,
      pageSize: pageSize.value
    }
  });
  if (data.code !== 200) {
    ElMessage.error(data.message || "查询失败");
    return;
  }
  tableData.value = data.data.records || [];
  total.value = data.data.total || 0;
};

const createUser = async () => {
  if (!validateCreate()) return;
  const { data } = await request.post("/admin/users", { ...createForm });
  if (data.code !== 200) {
    ElMessage.error(data.message || "创建失败");
    return;
  }
  ElMessage.success("创建成功");
  createVisible.value = false;
  pageNum.value = 1;
  loadUsers();
};

const updateUser = async () => {
  if (!validateEdit()) return;
  const payload = {
    realName: editForm.realName,
    phone: editForm.phone,
    roleId: editForm.roleId,
    status: editForm.status,
    password: editForm.password || undefined
  };
  const { data } = await request.put(`/admin/users/${editForm.id}`, payload);
  if (data.code !== 200) {
    ElMessage.error(data.message || "更新失败");
    return;
  }
  ElMessage.success("更新成功");
  editVisible.value = false;
  loadUsers();
};

const removeUser = async (row) => {
  const { data } = await request.delete(`/admin/users/${row.id}`);
  if (data.code !== 200) {
    ElMessage.error(data.message || "删除失败");
    return;
  }
  ElMessage.success("删除成功");
  if (tableData.value.length === 1 && pageNum.value > 1) {
    pageNum.value -= 1;
  }
  loadUsers();
};

const toggleStatus = async (row) => {
  const targetStatus = row.status === 1 ? 0 : 1;
  const { data } = await request.put(`/admin/users/${row.id}/status`, { status: targetStatus });
  if (data.code !== 200) {
    ElMessage.error(data.message || "状态更新失败");
    return;
  }
  ElMessage.success("状态更新成功");
  loadUsers();
};

const onCurrentChange = (val) => {
  pageNum.value = val;
  loadUsers();
};

const onSizeChange = (val) => {
  pageSize.value = val;
  pageNum.value = 1;
  loadUsers();
};

loadUsers();
</script>

<style scoped>
.header-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>

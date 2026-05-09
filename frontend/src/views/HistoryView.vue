<template>
  <el-card>
    <template #header>历史记录</template>
    <el-form :inline="true">
      <el-form-item label="时间范围">
        <el-date-picker
          v-model="filters.timeRange"
          type="daterange"
          start-placeholder="开始时间"
          end-placeholder="结束时间"
          value-format="YYYY-MM-DD"
        />
      </el-form-item>
      <el-form-item label="灾情等级">
        <el-select v-model="filters.level" placeholder="全部" clearable style="width: 180px">
          <el-option label="I级特别重大" value="I级特别重大" />
          <el-option label="II级重大" value="II级重大" />
          <el-option label="III级较大" value="III级较大" />
          <el-option label="IV级一般" value="IV级一般" />
        </el-select>
      </el-form-item>
      <el-form-item>
        <el-button type="primary" @click="onSearch">查询</el-button>
      </el-form-item>
      <el-form-item>
        <el-button @click="exportCsv">导出全部筛选结果</el-button>
      </el-form-item>
    </el-form>

    <el-table :data="tableData" border>
      <el-table-column prop="createdTime" label="识别时间" min-width="180" />
      <el-table-column prop="disasterLevel" label="灾情等级" min-width="140" />
      <el-table-column prop="landslideArea" label="滑坡面积" />
      <el-table-column prop="maxConfidence" label="最大置信度" />
      <el-table-column prop="warningTriggered" label="预警状态">
        <template #default="{ row }">
          <el-tag :type="row.warningTriggered ? 'danger' : 'info'">
            {{ row.warningTriggered ? "已触发" : "未触发" }}
          </el-tag>
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
</template>

<script setup>
import { reactive, ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../utils/request";

const filters = reactive({
  timeRange: [],
  level: ""
});

const tableData = ref([]);
const total = ref(0);
const pageNum = ref(1);
const pageSize = ref(10);

const buildFilterParams = () => ({
  level: filters.level || undefined,
  startTime: filters.timeRange?.[0],
  endTime: filters.timeRange?.[1]
});

const loadRecords = async () => {
  const params = {
    pageNum: pageNum.value,
    pageSize: pageSize.value,
    ...buildFilterParams()
  };

  const { data } = await request.get("/admin/history", { params });
  if (data.code !== 200) {
    ElMessage.error(data.message || "查询失败");
    return;
  }

  tableData.value = data.data?.records || [];
  total.value = data.data?.total || 0;
};

const onSearch = () => {
  pageNum.value = 1;
  loadRecords();
};

const onCurrentChange = (val) => {
  pageNum.value = val;
  loadRecords();
};

const onSizeChange = (val) => {
  pageSize.value = val;
  pageNum.value = 1;
  loadRecords();
};

const exportCsv = async () => {
  try {
    const params = buildFilterParams();
    const response = await request.get("/admin/history/export", {
      params,
      responseType: "blob"
    });

    const blob = new Blob([response.data], { type: "text/csv;charset=utf-8;" });
    const url = URL.createObjectURL(blob);
    const link = document.createElement("a");
    link.href = url;
    link.download = `history_export_${new Date().getTime()}.csv`;
    document.body.appendChild(link);
    link.click();
    document.body.removeChild(link);
    URL.revokeObjectURL(url);

    ElMessage.success("导出成功");
  } catch (error) {
    ElMessage.error("导出失败");
  }
};

loadRecords();
</script>

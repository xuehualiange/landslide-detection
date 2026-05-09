<template>
  <div style="padding: 24px">
    <el-card>
      <template #header>滑坡图像识别</template>
      <el-upload :auto-upload="false" :show-file-list="true" :on-change="onFileChange" :limit="1">
        <el-button type="primary">选择图片</el-button>
      </el-upload>
      <el-button style="margin-top: 16px" type="success" :disabled="!file" @click="submitDetect">
        开始识别
      </el-button>
    </el-card>

    <el-card v-if="result" style="margin-top: 16px">
      <div>识别置信度：{{ result.confidence }}</div>
      <div>滑坡区域占比：{{ result.landslideAreaRatio }}</div>
      <div>灾情等级：{{ result.disasterLevel }}</div>
      <div>建议：{{ result.suggestion }}</div>
    </el-card>
  </div>
</template>

<script setup>
import { ref } from "vue";
import { ElMessage } from "element-plus";
import request from "../utils/request";

const file = ref(null);
const result = ref(null);

const onFileChange = (uploadFile) => {
  file.value = uploadFile.raw;
};

const submitDetect = async () => {
  const formData = new FormData();
  formData.append("file", file.value);
  const { data } = await request.post("/detect/image", formData);
  if (data.code !== 200) {
    ElMessage.error(data.message);
    return;
  }
  result.value = data.data;
  ElMessage.success("识别完成");
};
</script>

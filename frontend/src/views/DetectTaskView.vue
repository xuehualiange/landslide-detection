<template>
  <el-card>
    <template #header>{{ t.uploadHeader }}</template>

    <el-alert
      :type="modelReady ? 'success' : 'warning'"
      :closable="false"
      :title="modelReady ? t.modelLoaded : t.modelFallback"
      style="margin-bottom: 12px"
    />

    <el-upload
      drag
      :auto-upload="false"
      :show-file-list="true"
      :limit="1"
      :on-change="onFileChange"
      accept="image/*"
    >
      <el-icon style="font-size: 26px"><UploadFilled /></el-icon>
      <div style="margin-top: 8px">{{ t.uploadHint }}</div>
    </el-upload>

    <el-button
      style="margin-top: 16px"
      type="primary"
      :disabled="!selectedFile || loading"
      :loading="loading"
      @click="submitDetect"
    >
      {{ loading ? t.detecting : t.startDetect }}
    </el-button>
  </el-card>

  <el-card v-if="previewUrl" style="margin-top: 16px">
    <template #header>{{ t.visualHeader }}</template>
    <canvas ref="canvasRef" class="result-canvas"></canvas>
  </el-card>

  <el-card v-if="result" style="margin-top: 16px">
    <template #header>{{ t.resultHeader }}</template>
    <el-alert type="success" :closable="false" :title="t.savedTip" style="margin-bottom: 12px" />

    <el-descriptions :column="2" border>
      <el-descriptions-item :label="t.levelLabel">
        <el-tag :type="levelTagType(result.disasterLevel)">{{ result.disasterLevel }}</el-tag>
      </el-descriptions-item>
      <el-descriptions-item :label="t.maxConfLabel">{{ result.maxConfidence }}</el-descriptions-item>
      <el-descriptions-item :label="t.areaLabel">{{ result.landslideArea }}</el-descriptions-item>
      <el-descriptions-item :label="t.warningLabel">{{ result.warningTriggered ? t.yes : t.no }}</el-descriptions-item>
      <el-descriptions-item :label="t.rateLabel">{{ result.latestDeformationRate }}</el-descriptions-item>
      <el-descriptions-item :label="t.reasonLabel">{{ levelReason }}</el-descriptions-item>
    </el-descriptions>

    <el-table :data="result.boxes || []" style="margin-top: 16px" border>
      <el-table-column prop="category" :label="t.colCategory" />
      <el-table-column prop="confidence" :label="t.colConfidence" />
      <el-table-column prop="x" label="X" />
      <el-table-column prop="y" label="Y" />
      <el-table-column prop="width" :label="t.colWidth" />
      <el-table-column prop="height" :label="t.colHeight" />
    </el-table>

    <div style="margin-top: 14px; display: flex; justify-content: flex-end">
      <el-button type="primary" plain @click="goHistory">{{ t.historyBtn }}</el-button>
    </div>
  </el-card>
</template>

<script setup>
import { computed, nextTick, onMounted, ref, watch } from "vue";
import { useRouter } from "vue-router";
import { ElMessage } from "element-plus";
import { UploadFilled } from "@element-plus/icons-vue";
import request from "../utils/request";

const t = {
  uploadHeader: "\u6ed1\u5761\u8bc6\u522b\u4e0a\u4f20",
  modelLoaded: "AI\u6a21\u578b\u72b6\u6001\uff1a\u5df2\u52a0\u8f7d",
  modelFallback: "AI\u6a21\u578b\u72b6\u6001\uff1a\u672a\u52a0\u8f7d\uff08\u964d\u7ea7\u6a21\u5f0f\uff09",
  uploadHint: "\u5c06\u56fe\u7247\u62d6\u62fd\u5230\u6b64\u5904\uff0c\u6216\u70b9\u51fb\u4e0a\u4f20",
  detecting: "\u8bc6\u522b\u4e2d...",
  startDetect: "\u5f00\u59cb\u8bc6\u522b",
  visualHeader: "\u8bc6\u522b\u53ef\u89c6\u5316",
  resultHeader: "\u8bc6\u522b\u7ed3\u679c",
  savedTip: "\u8bc6\u522b\u5b8c\u6210\uff0c\u8bb0\u5f55\u5df2\u4fdd\u5b58\u3002",
  levelLabel: "\u707e\u60c5\u7b49\u7ea7",
  maxConfLabel: "\u6700\u5927\u7f6e\u4fe1\u5ea6",
  areaLabel: "\u6ed1\u5761\u9762\u79ef",
  warningLabel: "\u89e6\u53d1\u9884\u8b66",
  rateLabel: "\u6700\u65b0\u53d8\u5f62\u901f\u7387(mm/\u5929)",
  reasonLabel: "\u5224\u5b9a\u4f9d\u636e",
  colCategory: "\u7c7b\u522b",
  colConfidence: "\u7f6e\u4fe1\u5ea6",
  colWidth: "\u5bbd\u5ea6",
  colHeight: "\u9ad8\u5ea6",
  historyBtn: "\u67e5\u770b\u5386\u53f2",
  yes: "\u662f",
  no: "\u5426"
};

const router = useRouter();
const selectedFile = ref(null);
const previewUrl = ref("");
const result = ref(null);
const modelReady = ref(false);
const loading = ref(false);
const canvasRef = ref(null);

const onFileChange = (uploadFile) => {
  selectedFile.value = uploadFile.raw;
  result.value = null;
  if (!selectedFile.value) {
    previewUrl.value = "";
    return;
  }
  previewUrl.value = URL.createObjectURL(selectedFile.value);
};

const loadModelStatus = async () => {
  try {
    const { data } = await request.get("/detect/model/status");
    if (data.code === 200) {
      modelReady.value = !!data.data.ready;
      return;
    }
    modelReady.value = false;
    ElMessage.warning(data.message || "\u6a21\u578b\u72b6\u6001\u67e5\u8be2\u5931\u8d25");
  } catch (error) {
    modelReady.value = false;
    const status = error?.response?.status;
    if (status === 401) {
      ElMessage.warning("\u767b\u5f55\u5df2\u8fc7\u671f\uff0c\u8bf7\u91cd\u65b0\u767b\u5f55");
    } else if (status) {
      ElMessage.warning(`\u6a21\u578b\u72b6\u6001\u67e5\u8be2\u5931\u8d25\uff08HTTP ${status}\uff09`);
    } else {
      ElMessage.warning("\u6a21\u578b\u72b6\u6001\u67e5\u8be2\u5931\u8d25\uff1a\u65e0\u6cd5\u8fde\u63a5\u540e\u7aef");
    }
  }
};

const submitDetect = async () => {
  if (!selectedFile.value || loading.value) return;
  loading.value = true;
  const formData = new FormData();
  formData.append("file", selectedFile.value);

  try {
    const { data } = await request.post("/detect/image", formData, {
      timeout: 120000
    });
    if (data.code !== 200) {
      ElMessage.error(data.message || "\u8bc6\u522b\u5931\u8d25");
      return;
    }
    result.value = data.data;
    ElMessage.success("\u8bc6\u522b\u5b8c\u6210");
    await nextTick();
    drawBoxes();
  } catch (error) {
    const status = error?.response?.status;
    const message = error?.response?.data?.message;
    if (status) {
      ElMessage.error(message || `\u8bc6\u522b\u5931\u8d25\uff08HTTP ${status}\uff09`);
    } else if (error?.code === "ECONNABORTED") {
      ElMessage.error("\u8bc6\u522b\u8d85\u65f6\uff0c\u8bf7\u7a0d\u540e\u91cd\u8bd5\u6216\u4f7f\u7528\u66f4\u5c0f\u56fe\u7247");
    } else {
      ElMessage.error("\u8bc6\u522b\u8bf7\u6c42\u5931\u8d25\uff0c\u8bf7\u68c0\u67e5\u540e\u7aef\u548c\u767b\u5f55\u72b6\u6001");
    }
  } finally {
    loading.value = false;
  }
};

const levelTagType = (level) => {
  if (level?.startsWith("I")) return "danger";
  if (level?.startsWith("II")) return "warning";
  if (level?.startsWith("III")) return "success";
  return "info";
};

const levelReason = computed(() => {
  if (!result.value) return "-";
  return `\u9762\u79ef=${result.value.landslideArea}\uff0c\u6700\u5927\u7f6e\u4fe1\u5ea6=${result.value.maxConfidence}\uff0c\u53d8\u5f62\u901f\u7387=${result.value.latestDeformationRate} mm/\u5929`;
});

const drawBoxes = () => {
  if (!canvasRef.value || !previewUrl.value) return;

  const img = new Image();
  img.onload = () => {
    const canvas = canvasRef.value;
    const ctx = canvas.getContext("2d");

    canvas.width = img.width;
    canvas.height = img.height;

    ctx.drawImage(img, 0, 0, img.width, img.height);

    const boxes = result.value?.boxes || [];
    boxes.forEach((box) => {
      ctx.strokeStyle = "#ff4d4f";
      ctx.lineWidth = 2;
      ctx.strokeRect(box.x, box.y, box.width, box.height);

      const text = `${box.category || "landslide"} ${Number(box.confidence || 0).toFixed(2)}`;
      ctx.font = "16px sans-serif";
      const textW = ctx.measureText(text).width + 8;
      const textH = 22;
      const tx = Math.max(0, box.x);
      const ty = Math.max(textH, box.y);

      ctx.fillStyle = "rgba(255, 77, 79, 0.9)";
      ctx.fillRect(tx, ty - textH, textW, textH);
      ctx.fillStyle = "#fff";
      ctx.fillText(text, tx + 4, ty - 6);
    });
  };

  img.src = previewUrl.value;
};

const goHistory = () => {
  router.push("/history");
};

watch([previewUrl, result], () => {
  if (previewUrl.value && result.value) {
    drawBoxes();
  }
});

onMounted(() => {
  loadModelStatus();
});
</script>

<style scoped>
.result-canvas {
  max-width: 100%;
  border: 1px solid #ebeef5;
  border-radius: 4px;
}
</style>
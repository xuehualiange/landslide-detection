# 简历项目描述（实测数据）

## 一句话（中文 · 推荐放简历）

基于 Java + OpenCV DNN 部署 YOLOv8n ONNX，实现遥感影像滑坡区域自动检测与 Canvas 可视化标注；验证集 mAP@0.5 **95.1%**（精确率 90.4%、召回率 91.4%），单张 640×640 全链路推理中位数 **103 ms**。

## 英文一行

Java + OpenCV DNN deploys YOLOv8n ONNX for landslide detection on remote-sensing images with Canvas visualization; val mAP@0.5 **95.1%** (P=90.4%, R=91.4%); single-image inference **103 ms** median (640×640, full Java pipeline).

---

## 完整项目经历（可直接粘贴）

**滑坡遥感智能识别与预警系统** | 毕业设计 / 个人项目  
技术栈：Java 17 · Spring Boot · Vue 3 · OpenCV DNN · YOLOv8 · WebSocket · MySQL

- 使用 **Java + OpenCV DNN** 加载自训练 YOLOv8n ONNX 权重，在服务端完成 640×640 推理、NMS 后处理与灾害等级（I–IV 级）评估，前端 Canvas 实时绘制检测框。
- 基于毕节滑坡数据集（2773 张，含正负样本）训练 100 epoch；验证集 **mAP@0.5 = 95.1%**，mAP@0.5:0.95 = 64.9%，精确率/召回率 **90.4% / 91.4%**。
- 图像级混淆矩阵（conf=0.45）：TP=130、FN=24、FP=4、TN=396，兼顾「有滑坡」检测与背景误报控制。
- 压测（`YoloDetectorBenchmark`，5 次预热 + 30 次采样）：Java 全链路推理中位数 **103 ms**（min 95 / avg 103.5 / p95 115 ms，本机实测）。
- 集成 WebSocket 预警推送、RBAC 权限、检测记录管理与 LangChain 智能问答助手，支持一键本地启动联调。

---

## 指标速查表

| 指标 | 实测值 |
|------|--------|
| mAP@0.5 | **95.1%** |
| mAP@0.5:0.95 | 64.9% |
| Precision / Recall | 90.4% / 91.4% |
| 数据集 | 毕节滑坡，2773 张（770 正 / 2003 负） |
| 推理耗时（Java 全链路） | **103 ms**（中位数，640×640） |
| 置信度阈值 | 0.45（降低非滑坡误报） |

---

## 压测复现

```powershell
cd e:\landslide-ai-system\backend
powershell -ExecutionPolicy Bypass -File scripts\benchmark-yolo.ps1
# 强制重新编译：加 -Rebuild
```

压测类：`backend/src/main/java/com/graduation/landslide/ai/YoloDetectorBenchmark.java`  
走与线上一致的 `YoloDetector.detect()` 全链路（读图 → blob → forward → 解析 → NMS），比仅测 `net.forward()` 更贴近真实接口耗时。

---

## 面试可补充说明

1. **为何比预估 87.6% 高？** 同一 run（bijie_v1）在 epoch 100 验证集上的官方指标即为 95.1%，简历应写实测值而非早期估算。
2. **103 ms 环境**：Windows 本机、OpenCV DNN CPU 推理；若面试官问 GPU，可说明当前部署为 CPU 路径，生产可换 ONNX Runtime / TensorRT。
3. **误报控制**：将 `conf-threshold` 从 0.002 调至 0.45 后，图像级 FP 仅 4 例，TN=396。

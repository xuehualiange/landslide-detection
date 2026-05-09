package com.graduation.landslide.ai.service.impl;

import com.graduation.landslide.ai.dto.DetectResult;
import com.graduation.landslide.ai.service.LandslideDetectService;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class OnnxLandslideDetectServiceImpl implements LandslideDetectService {

    @Override
    public DetectResult detect(MultipartFile imageFile) {
        // TODO: 接入 OpenCV DNN + YOLOv8 ONNX 实际推理与后处理
        double mockAreaRatio = 0.42;
        String level = mockAreaRatio < 0.2 ? "轻度" : (mockAreaRatio < 0.5 ? "中度" : "重度");
        return new DetectResult(0.91, mockAreaRatio, level, "建议立即开展现场巡检并持续监控");
    }
}

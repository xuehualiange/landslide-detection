package com.graduation.landslide.ai.service;

import com.graduation.landslide.ai.dto.DetectResult;
import org.springframework.web.multipart.MultipartFile;

public interface LandslideDetectService {
    DetectResult detect(MultipartFile imageFile);
}

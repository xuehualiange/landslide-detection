package com.graduation.landslide.ai;

import com.graduation.landslide.common.ApiResponse;
import com.graduation.landslide.entity.SysUser;
import com.graduation.landslide.service.SysUserService;
import com.graduation.landslide.warning.dto.DisasterAssessmentResult;
import com.graduation.landslide.warning.service.DisasterLevelService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/detect")
public class LandslideDetectController {

    private final DisasterLevelService disasterLevelService;
    private final SysUserService sysUserService;
    private final YoloDetector yoloDetector;

    public LandslideDetectController(DisasterLevelService disasterLevelService,
                                     SysUserService sysUserService,
                                     YoloDetector yoloDetector) {
        this.disasterLevelService = disasterLevelService;
        this.sysUserService = sysUserService;
        this.yoloDetector = yoloDetector;
    }

    @PostMapping("/image")
    public ApiResponse<DisasterAssessmentResult> detect(@RequestParam("file") MultipartFile file) {
        if (file == null || file.isEmpty()) {
            return ApiResponse.fail("请上传有效图片");
        }

        try {
            Long userId = resolveCurrentUserId();
            if (userId == null) {
                return ApiResponse.fail("识别失败: 系统未找到可关联的用户，请先创建用户后重试");
            }

            DisasterAssessmentResult result = disasterLevelService.assessAndWarn(
                    userId,
                    file.getOriginalFilename(),
                    file.getBytes()
            );
            return ApiResponse.success(result);
        } catch (Exception ex) {
            return ApiResponse.fail("识别失败: " + ex.getMessage());
        }
    }

    private Long resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? null : authentication.getName();
        boolean hasLoginUser = username != null && !username.isBlank() && !"anonymousUser".equals(username);

        if (hasLoginUser) {
            SysUser loginUser = sysUserService.lambdaQuery()
                    .eq(SysUser::getUsername, username)
                    .one();
            if (loginUser != null) {
                return loginUser.getId();
            }
        }

        SysUser adminUser = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, "admin")
                .one();
        if (adminUser != null) {
            return adminUser.getId();
        }

        SysUser anyUser = sysUserService.lambdaQuery()
                .last("limit 1")
                .one();
        return anyUser == null ? null : anyUser.getId();
    }

    @GetMapping("/model/status")
    public ApiResponse<Map<String, Object>> modelStatus() {
        Map<String, Object> data = new HashMap<>();
        data.put("ready", yoloDetector.isModelReady());
        data.put("message", yoloDetector.isModelReady() ? "模型已加载" : "模型未加载，当前为降级模式");
        return ApiResponse.success(data);
    }
}
package com.graduation.landslide.assistant;

import java.time.format.DateTimeFormatter;
import java.util.List;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.landslide.entity.LandslideDetectRecord;
import com.graduation.landslide.entity.SysUser;
import com.graduation.landslide.service.LandslideDetectRecordService;
import com.graduation.landslide.service.SysUserService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
@RequiredArgsConstructor
public class AssistantHistoryContextService {

    private static final int MAX_RECORDS = 15;

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final LandslideDetectRecordService detectRecordService;
    private final SysUserService sysUserService;

    public String buildSummaryForAssistant() {
        try {
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String username = authentication != null ? authentication.getName() : null;
            if (!StringUtils.hasText(username) || "anonymousUser".equals(username)) {
                return "（用户未登录，暂无个人识别记录摘要。）";
            }

            SysUser user = sysUserService.lambdaQuery()
                    .eq(SysUser::getUsername, username)
                    .one();
            if (user == null) {
                return "（无法解析当前用户，暂无识别记录摘要。）";
            }

            Long currentUserId = user.getId();
            boolean monitorOnly = isMonitorRole(authentication);

            Page<LandslideDetectRecord> page = new Page<>(1, MAX_RECORDS);
            Page<LandslideDetectRecord> result = detectRecordService.lambdaQuery()
                    .eq(monitorOnly && currentUserId != null, LandslideDetectRecord::getUserId, currentUserId)
                    .orderByDesc(LandslideDetectRecord::getCreatedTime)
                    .page(page);

            List<LandslideDetectRecord> records = result.getRecords();
            if (records == null || records.isEmpty()) {
                return monitorOnly
                        ? "（当前账号暂无识别记录；完成识别任务后将出现在此摘要中。）"
                        : "（系统中暂无识别记录；或当前筛选下无数据。）";
            }

            StringBuilder sb = new StringBuilder();
            sb.append("共 ").append(records.size()).append(" 条（按时间从新到旧）：\n");
            int n = 1;
            for (LandslideDetectRecord r : records) {
                sb.append(n++).append(". ");
                sb.append("记录ID=").append(r.getId());
                sb.append("；时间=").append(r.getCreatedTime() != null ? TIME_FMT.format(r.getCreatedTime()) : "-");
                sb.append("；灾情等级=").append(blankToDash(r.getDisasterLevel()));
                sb.append("；滑坡面积=").append(r.getLandslideArea() != null ? r.getLandslideArea().toPlainString() : "-");
                sb.append("；最高置信度=").append(r.getMaxConfidence() != null ? r.getMaxConfidence().toPlainString() : "-");
                sb.append("；预警=").append(r.getWarningTriggered() != null && r.getWarningTriggered() == 1 ? "已触发" : "未触发");
                sb.append("\n");
            }
            return sb.toString();
        } catch (Exception ex) {
            log.warn("加载识别记录摘要失败，已跳过摘要：{}", ex.toString());
            return "（识别记录摘要暂时不可用，仍可继续对话。）";
        }
    }

    private static boolean isMonitorRole(Authentication authentication) {
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MONITOR".equals(a.getAuthority()));
    }

    private static String blankToDash(String s) {
        return StringUtils.hasText(s) ? s : "-";
    }
}

package com.graduation.landslide.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.landslide.admin.dto.PageResult;
import com.graduation.landslide.common.ApiResponse;
import com.graduation.landslide.entity.LandslideDetectRecord;
import com.graduation.landslide.entity.SysUser;
import com.graduation.landslide.service.LandslideDetectRecordService;
import com.graduation.landslide.service.SysUserService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
@PreAuthorize("hasRole('MONITOR') or hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminHistoryController {

    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final LandslideDetectRecordService detectRecordService;
    private final SysUserService sysUserService;

    public AdminHistoryController(LandslideDetectRecordService detectRecordService,
                                  SysUserService sysUserService) {
        this.detectRecordService = detectRecordService;
        this.sysUserService = sysUserService;
    }

    @GetMapping("/history")
    public ApiResponse<PageResult<LandslideDetectRecord>> history(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize,
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime) {

        LocalDateTime startDateTime = parseStart(startTime);
        LocalDateTime endDateTime = parseEnd(endTime);
        Long currentUserId = resolveCurrentUserId();
        boolean monitorOnly = isMonitorRole();

        Page<LandslideDetectRecord> page = detectRecordService.lambdaQuery()
                .eq(level != null && !level.isBlank(), LandslideDetectRecord::getDisasterLevel, level)
                .ge(startDateTime != null, LandslideDetectRecord::getCreatedTime, startDateTime)
                .le(endDateTime != null, LandslideDetectRecord::getCreatedTime, endDateTime)
                .eq(monitorOnly && currentUserId != null, LandslideDetectRecord::getUserId, currentUserId)
                .orderByDesc(LandslideDetectRecord::getCreatedTime)
                .page(new Page<>(pageNum, pageSize));

        PageResult<LandslideDetectRecord> result = new PageResult<>(
                pageNum,
                pageSize,
                page.getTotal(),
                page.getRecords()
        );
        return ApiResponse.success(result);
    }

    @GetMapping("/history/export")
    public void exportHistory(
            @RequestParam(required = false) String level,
            @RequestParam(required = false) String startTime,
            @RequestParam(required = false) String endTime,
            HttpServletResponse response) throws IOException {

        LocalDateTime startDateTime = parseStart(startTime);
        LocalDateTime endDateTime = parseEnd(endTime);
        Long currentUserId = resolveCurrentUserId();
        boolean monitorOnly = isMonitorRole();

        List<LandslideDetectRecord> records = detectRecordService.lambdaQuery()
                .eq(level != null && !level.isBlank(), LandslideDetectRecord::getDisasterLevel, level)
                .ge(startDateTime != null, LandslideDetectRecord::getCreatedTime, startDateTime)
                .le(endDateTime != null, LandslideDetectRecord::getCreatedTime, endDateTime)
                .eq(monitorOnly && currentUserId != null, LandslideDetectRecord::getUserId, currentUserId)
                .orderByDesc(LandslideDetectRecord::getCreatedTime)
                .list();

        String fileName = URLEncoder.encode("history_export.csv", StandardCharsets.UTF_8.name());
        response.setContentType("text/csv;charset=UTF-8");
        response.setHeader("Content-Disposition", "attachment; filename*=UTF-8''" + fileName);

        StringBuilder csv = new StringBuilder();
        csv.append("识别时间,灾情等级,滑坡面积,最大置信度,预警状态\n");

        for (LandslideDetectRecord item : records) {
            csv.append(escape(item.getCreatedTime())).append(',')
                    .append(escape(item.getDisasterLevel())).append(',')
                    .append(escape(item.getLandslideArea())).append(',')
                    .append(escape(item.getMaxConfidence())).append(',')
                    .append(item.getWarningTriggered() != null && item.getWarningTriggered() == 1 ? "已触发" : "未触发")
                    .append('\n');
        }

        response.getWriter().write('\uFEFF');
        response.getWriter().write(csv.toString());
        response.getWriter().flush();
    }

    private Long resolveCurrentUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? null : authentication.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return null;
        }

        SysUser user = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, username)
                .one();
        return user == null ? null : user.getId();
    }

    private boolean isMonitorRole() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getAuthorities() == null) {
            return false;
        }
        return authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_MONITOR".equals(a.getAuthority()));
    }

    private String escape(Object value) {
        if (value == null) {
            return "\"\"";
        }
        String str = String.valueOf(value).replace("\"", "\"\"");
        return "\"" + str + "\"";
    }

    private LocalDateTime parseStart(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date, DATE_FORMATTER).atStartOfDay();
    }

    private LocalDateTime parseEnd(String date) {
        if (date == null || date.isBlank()) {
            return null;
        }
        return LocalDate.parse(date, DATE_FORMATTER).atTime(23, 59, 59);
    }
}
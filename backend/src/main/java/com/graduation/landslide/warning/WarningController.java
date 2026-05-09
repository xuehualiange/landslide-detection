package com.graduation.landslide.warning;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.landslide.common.ApiResponse;
import com.graduation.landslide.entity.SysUser;
import com.graduation.landslide.entity.WarningEvent;
import com.graduation.landslide.service.SysUserService;
import com.graduation.landslide.service.WarningEventService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/warning")
public class WarningController {

    private final WarningEventService warningEventService;
    private final SysUserService sysUserService;

    public WarningController(WarningEventService warningEventService,
                             SysUserService sysUserService) {
        this.warningEventService = warningEventService;
        this.sysUserService = sysUserService;
    }

    @GetMapping("/events")
    public ApiResponse<Map<String, Object>> listEvents(
            @RequestParam(value = "status", defaultValue = "ALL") String status,
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "pageSize", defaultValue = "20") long pageSize) {

        LambdaQueryWrapper<WarningEvent> wrapper = new LambdaQueryWrapper<WarningEvent>()
                .orderByDesc(WarningEvent::getId);
        if (!"ALL".equalsIgnoreCase(status)) {
            wrapper.eq(WarningEvent::getStatus, status.toUpperCase());
        }

        Page<WarningEvent> pager = warningEventService.page(new Page<>(page, pageSize), wrapper);
        Map<String, Object> data = new HashMap<>();
        data.put("total", pager.getTotal());
        data.put("records", pager.getRecords());
        return ApiResponse.success(data);
    }

    @GetMapping("/unread-count")
    public ApiResponse<Map<String, Object>> unreadCount() {
        long unread = warningEventService.lambdaQuery()
                .eq(WarningEvent::getStatus, "UNREAD")
                .count();
        Map<String, Object> data = new HashMap<>();
        data.put("unread", unread);
        return ApiResponse.success(data);
    }

    @PostMapping("/events/{id}/ack")
    public ApiResponse<String> ackEvent(@PathVariable("id") Long id) {
        WarningEvent event = warningEventService.getById(id);
        if (event == null) {
            return ApiResponse.fail("预警事件不存在");
        }
        if ("ACK".equalsIgnoreCase(event.getStatus())) {
            return ApiResponse.success("该预警已确认");
        }

        event.setStatus("ACK");
        event.setAckTime(LocalDateTime.now());
        event.setAckUserId(resolveCurrentUserId());
        warningEventService.updateById(event);
        return ApiResponse.success("预警已确认");
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
}

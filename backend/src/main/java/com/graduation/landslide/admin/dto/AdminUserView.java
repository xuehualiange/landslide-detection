package com.graduation.landslide.admin.dto;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AdminUserView {
    private Long id;
    private String username;
    private String realName;
    private String phone;
    private Long roleId;
    private Integer status;
    private LocalDateTime createdTime;
    private LocalDateTime updatedTime;
}

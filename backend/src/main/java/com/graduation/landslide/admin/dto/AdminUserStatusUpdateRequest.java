package com.graduation.landslide.admin.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

@Data
public class AdminUserStatusUpdateRequest {
    @NotNull
    private Integer status;
}

package com.graduation.landslide.user.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

@Data
public class UserProfileUpdateRequest {
    @NotBlank
    private String realName;

    private String phone;
}

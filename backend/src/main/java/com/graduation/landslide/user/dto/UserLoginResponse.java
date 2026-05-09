package com.graduation.landslide.user.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class UserLoginResponse {
    private String token;
    private String tokenType;
    private String username;
    private String role;
}
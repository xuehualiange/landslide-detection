package com.graduation.landslide.auth;

import com.graduation.landslide.auth.dto.LoginRequest;
import com.graduation.landslide.auth.dto.LoginResponse;
import com.graduation.landslide.common.ApiResponse;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final JwtUtil jwtUtil;
    private static final Map<String, String> USER_ROLE_MAP = new HashMap<>();
    private static final Map<String, String> USER_PASSWORD_MAP = new HashMap<>();

    static {
        USER_ROLE_MAP.put("superadmin", "SUPER_ADMIN");
        USER_ROLE_MAP.put("admin", "ADMIN");
        USER_ROLE_MAP.put("monitor", "MONITOR");

        USER_PASSWORD_MAP.put("superadmin", "123456");
        USER_PASSWORD_MAP.put("admin", "123456");
        USER_PASSWORD_MAP.put("monitor", "123456");
    }

    public AuthController(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Validated @RequestBody LoginRequest request) {
        String password = USER_PASSWORD_MAP.get(request.getUsername());
        if (password == null || !password.equals(request.getPassword())) {
            return ApiResponse.fail("用户名或密码错误");
        }
        String role = USER_ROLE_MAP.get(request.getUsername());
        String token = jwtUtil.createToken(request.getUsername(), role);
        return ApiResponse.success(new LoginResponse(token, request.getUsername(), role));
    }
}

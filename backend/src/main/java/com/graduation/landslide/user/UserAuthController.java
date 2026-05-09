package com.graduation.landslide.user;

import com.graduation.landslide.common.ApiResponse;
import com.graduation.landslide.entity.SysRole;
import com.graduation.landslide.entity.SysUser;
import com.graduation.landslide.security.JwtTokenService;
import com.graduation.landslide.service.SysRoleService;
import com.graduation.landslide.service.SysUserService;
import com.graduation.landslide.user.dto.UserLoginRequest;
import com.graduation.landslide.user.dto.UserLoginResponse;
import com.graduation.landslide.user.dto.UserProfileResponse;
import com.graduation.landslide.user.dto.UserProfileUpdateRequest;
import com.graduation.landslide.user.dto.UserRegisterRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import lombok.extern.slf4j.Slf4j;

import java.util.Locale;

@Slf4j
@RestController
@RequestMapping("/api/user")
public class UserAuthController {

    private final AuthenticationManager authenticationManager;
    private final JwtTokenService jwtTokenService;
    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;
    private final PasswordEncoder passwordEncoder;

    public UserAuthController(AuthenticationManager authenticationManager,
                              JwtTokenService jwtTokenService,
                              SysUserService sysUserService,
                              SysRoleService sysRoleService,
                              PasswordEncoder passwordEncoder) {
        this.authenticationManager = authenticationManager;
        this.jwtTokenService = jwtTokenService;
        this.sysUserService = sysUserService;
        this.sysRoleService = sysRoleService;
        this.passwordEncoder = passwordEncoder;
    }

    @PostMapping("/login")
    public ApiResponse<UserLoginResponse> login(@Validated @RequestBody UserLoginRequest request) {
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(request.getUsername(), request.getPassword())
            );
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            String token = jwtTokenService.generateToken(userDetails);
            String role = extractRole(userDetails);
            return ApiResponse.success(new UserLoginResponse(token, "Bearer", userDetails.getUsername(), role));
        } catch (BadCredentialsException ex) {
            return ApiResponse.fail("用户名或密码错误");
        } catch (DisabledException ex) {
            return ApiResponse.fail("账号已被禁用");
        } catch (AuthenticationException ex) {
            log.warn("Login failed: {}", ex.getMessage());
            return ApiResponse.fail("用户名或密码错误");
        } catch (Exception ex) {
            log.error("Login error", ex);
            return ApiResponse.fail("登录失败：请确认 MySQL 已启动且已执行数据库脚本（docs），或查看后端日志");
        }
    }

    @PostMapping("/register")
    public ApiResponse<String> register(@Validated @RequestBody UserRegisterRequest request) {
        String username = request.getUsername() == null ? "" : request.getUsername().trim();
        if (username.isEmpty()) {
            return ApiResponse.fail("用户名不能为空");
        }

        boolean exists = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, username)
                .exists();
        if (exists) {
            return ApiResponse.fail("用户名已存在");
        }

        SysRole monitorRole = sysRoleService.lambdaQuery()
                .eq(SysRole::getRoleCode, "MONITOR")
                .one();
        if (monitorRole == null) {
            return ApiResponse.fail("系统角色未初始化，请联系管理员");
        }

        SysUser user = new SysUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName((request.getRealName() == null || request.getRealName().isBlank()) ? username : request.getRealName().trim());
        user.setPhone(request.getPhone());
        user.setRoleId(monitorRole.getId());
        user.setStatus(1);
        sysUserService.save(user);

        return ApiResponse.success("注册成功，请登录");
    }

    @GetMapping("/profile")
    public ApiResponse<UserProfileResponse> profile() {
        SysUser user = currentUser();
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }

        String roleCode = "MONITOR";
        SysRole role = sysRoleService.getById(user.getRoleId());
        if (role != null && role.getRoleCode() != null && !role.getRoleCode().isBlank()) {
            roleCode = role.getRoleCode().toUpperCase(Locale.ROOT);
        }

        return ApiResponse.success(new UserProfileResponse(
                user.getUsername(),
                user.getRealName(),
                user.getPhone(),
                roleCode
        ));
    }

    @PutMapping("/profile")
    public ApiResponse<String> updateProfile(@Validated @RequestBody UserProfileUpdateRequest request) {
        SysUser user = currentUser();
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }

        user.setRealName(request.getRealName().trim());
        user.setPhone(request.getPhone());
        sysUserService.updateById(user);
        return ApiResponse.success("个人信息更新成功");
    }

    private SysUser currentUser() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        String username = authentication == null ? null : authentication.getName();
        if (username == null || username.isBlank() || "anonymousUser".equals(username)) {
            return null;
        }

        return sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, username)
                .one();
    }

    private String extractRole(UserDetails userDetails) {
        for (GrantedAuthority authority : userDetails.getAuthorities()) {
            String auth = authority.getAuthority();
            if (auth != null && auth.startsWith("ROLE_")) {
                return auth.substring(5).toUpperCase(Locale.ROOT);
            }
        }
        return "MONITOR";
    }
}
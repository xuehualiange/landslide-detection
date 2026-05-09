package com.graduation.landslide.admin;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.graduation.landslide.admin.dto.AdminUserCreateRequest;
import com.graduation.landslide.admin.dto.AdminUserStatusUpdateRequest;
import com.graduation.landslide.admin.dto.AdminUserUpdateRequest;
import com.graduation.landslide.admin.dto.AdminUserView;
import com.graduation.landslide.admin.dto.PageResult;
import com.graduation.landslide.common.ApiResponse;
import com.graduation.landslide.entity.SysUser;
import com.graduation.landslide.service.SysUserService;
import org.springframework.beans.BeanUtils;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/admin/users")
@PreAuthorize("hasRole('ADMIN') or hasRole('SUPER_ADMIN')")
public class AdminUserController {

    private final SysUserService sysUserService;
    private final PasswordEncoder passwordEncoder;

    public AdminUserController(SysUserService sysUserService, PasswordEncoder passwordEncoder) {
        this.sysUserService = sysUserService;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    public ApiResponse<PageResult<AdminUserView>> listUsers(
            @RequestParam(defaultValue = "1") long pageNum,
            @RequestParam(defaultValue = "10") long pageSize) {
        Page<SysUser> page = sysUserService.page(new Page<>(pageNum, pageSize));
        List<AdminUserView> records = page.getRecords()
                .stream()
                .map(this::toView)
                .collect(Collectors.toList());
        return ApiResponse.success(new PageResult<>(pageNum, pageSize, page.getTotal(), records));
    }

    @PostMapping
    public ApiResponse<String> createUser(@Validated @RequestBody AdminUserCreateRequest request) {
        long exists = sysUserService.lambdaQuery()
                .eq(SysUser::getUsername, request.getUsername())
                .count();
        if (exists > 0) {
            return ApiResponse.fail("用户名已存在");
        }

        SysUser user = new SysUser();
        user.setUsername(request.getUsername());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setRoleId(request.getRoleId());
        user.setStatus(request.getStatus());
        sysUserService.save(user);
        return ApiResponse.success("创建成功");
    }

    @PutMapping("/{id}")
    public ApiResponse<String> updateUser(@PathVariable Long id,
                                          @Validated @RequestBody AdminUserUpdateRequest request) {
        SysUser user = sysUserService.getById(id);
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }

        user.setRealName(request.getRealName());
        user.setPhone(request.getPhone());
        user.setRoleId(request.getRoleId());
        user.setStatus(request.getStatus());
        if (request.getPassword() != null && !request.getPassword().isBlank()) {
            user.setPassword(passwordEncoder.encode(request.getPassword()));
        }
        sysUserService.updateById(user);
        return ApiResponse.success("修改成功");
    }

    @DeleteMapping("/{id}")
    public ApiResponse<String> deleteUser(@PathVariable Long id) {
        boolean removed = sysUserService.removeById(id);
        if (!removed) {
            return ApiResponse.fail("删除失败，用户不存在");
        }
        return ApiResponse.success("删除成功");
    }

    @PutMapping("/{id}/status")
    public ApiResponse<String> updateUserStatus(@PathVariable Long id,
                                                @Validated @RequestBody AdminUserStatusUpdateRequest request) {
        SysUser user = sysUserService.getById(id);
        if (user == null) {
            return ApiResponse.fail("用户不存在");
        }
        user.setStatus(request.getStatus());
        sysUserService.updateById(user);
        return ApiResponse.success("状态更新成功");
    }

    private AdminUserView toView(SysUser user) {
        AdminUserView view = new AdminUserView();
        BeanUtils.copyProperties(user, view);
        return view;
    }
}

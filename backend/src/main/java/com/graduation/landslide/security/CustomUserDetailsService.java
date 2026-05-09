package com.graduation.landslide.security;

import com.graduation.landslide.entity.SysRole;
import com.graduation.landslide.entity.SysUser;
import com.graduation.landslide.service.SysRoleService;
import com.graduation.landslide.service.SysUserService;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.Locale;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final SysUserService sysUserService;
    private final SysRoleService sysRoleService;

    public CustomUserDetailsService(SysUserService sysUserService, SysRoleService sysRoleService) {
        this.sysUserService = sysUserService;
        this.sysRoleService = sysRoleService;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        SysUser user = sysUserService.lambdaQuery().eq(SysUser::getUsername, username).one();
        if (user == null) {
            throw new UsernameNotFoundException("用户不存在");
        }

        SysRole role = sysRoleService.getById(user.getRoleId());
        String roleCode = "MONITOR";
        if (role != null && role.getRoleCode() != null && !role.getRoleCode().isBlank()) {
            roleCode = role.getRoleCode();
        }
        String normalizedRole = roleCode.toUpperCase(Locale.ROOT);

        return User.builder()
                .username(user.getUsername())
                .password(user.getPassword())
                .disabled(user.getStatus() != null && user.getStatus() == 0)
                .authorities(Collections.singletonList(new SimpleGrantedAuthority("ROLE_" + normalizedRole)))
                .build();
    }
}

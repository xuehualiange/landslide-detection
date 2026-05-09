package com.graduation.landslide.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graduation.landslide.entity.SysRole;
import com.graduation.landslide.mapper.SysRoleMapper;
import com.graduation.landslide.service.SysRoleService;
import org.springframework.stereotype.Service;

@Service
public class SysRoleServiceImpl extends ServiceImpl<SysRoleMapper, SysRole> implements SysRoleService {
}

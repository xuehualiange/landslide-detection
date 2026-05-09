package com.graduation.landslide.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graduation.landslide.entity.SysUser;
import com.graduation.landslide.mapper.SysUserMapper;
import com.graduation.landslide.service.SysUserService;
import org.springframework.stereotype.Service;

@Service
public class SysUserServiceImpl extends ServiceImpl<SysUserMapper, SysUser> implements SysUserService {
}

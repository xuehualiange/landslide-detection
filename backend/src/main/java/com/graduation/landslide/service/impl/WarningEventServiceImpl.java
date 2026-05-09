package com.graduation.landslide.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graduation.landslide.entity.WarningEvent;
import com.graduation.landslide.mapper.WarningEventMapper;
import com.graduation.landslide.service.WarningEventService;
import org.springframework.stereotype.Service;

@Service
public class WarningEventServiceImpl extends ServiceImpl<WarningEventMapper, WarningEvent>
        implements WarningEventService {
}

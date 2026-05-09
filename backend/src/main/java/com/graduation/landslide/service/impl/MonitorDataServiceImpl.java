package com.graduation.landslide.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graduation.landslide.entity.MonitorData;
import com.graduation.landslide.mapper.MonitorDataMapper;
import com.graduation.landslide.service.MonitorDataService;
import org.springframework.stereotype.Service;

@Service
public class MonitorDataServiceImpl extends ServiceImpl<MonitorDataMapper, MonitorData> implements MonitorDataService {
}

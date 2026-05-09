package com.graduation.landslide.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.graduation.landslide.entity.LandslideDetectRecord;
import com.graduation.landslide.mapper.LandslideDetectRecordMapper;
import com.graduation.landslide.service.LandslideDetectRecordService;
import org.springframework.stereotype.Service;

@Service
public class LandslideDetectRecordServiceImpl extends ServiceImpl<LandslideDetectRecordMapper, LandslideDetectRecord>
        implements LandslideDetectRecordService {
}

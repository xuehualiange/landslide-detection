package com.graduation.landslide.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("monitor_data")
public class MonitorData {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("deformation_rate")
    private BigDecimal deformationRate;

    private BigDecimal temperature;

    @TableField("seepage_pressure")
    private BigDecimal seepagePressure;

    @TableField("geoelectric_field")
    private BigDecimal geoelectricField;

    @TableField("collect_time")
    private LocalDateTime collectTime;

    @TableField("created_time")
    private LocalDateTime createdTime;
}

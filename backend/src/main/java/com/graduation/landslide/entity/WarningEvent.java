package com.graduation.landslide.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("warning_event")
public class WarningEvent {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("message")
    private String message;

    @TableField("disaster_level")
    private String disasterLevel;

    @TableField("landslide_area")
    private BigDecimal landslideArea;

    @TableField("max_confidence")
    private BigDecimal maxConfidence;

    @TableField("latest_deformation_rate")
    private BigDecimal latestDeformationRate;

    @TableField("status")
    private String status;

    @TableField("ack_user_id")
    private Long ackUserId;

    @TableField("ack_time")
    private LocalDateTime ackTime;

    @TableField("created_time")
    private LocalDateTime createdTime;
}

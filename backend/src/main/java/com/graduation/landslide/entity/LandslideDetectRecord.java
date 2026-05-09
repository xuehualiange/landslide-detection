package com.graduation.landslide.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("landslide_detect_record")
public class LandslideDetectRecord {

    @TableId(type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("image_path")
    private String imagePath;

    @TableField("landslide_area")
    private BigDecimal landslideArea;

    @TableField("max_confidence")
    private BigDecimal maxConfidence;

    @TableField("disaster_level")
    private String disasterLevel;

    @TableField("warning_triggered")
    private Integer warningTriggered;

    @TableField("created_time")
    private LocalDateTime createdTime;
}

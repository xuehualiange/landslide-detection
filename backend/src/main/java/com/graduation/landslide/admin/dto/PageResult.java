package com.graduation.landslide.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class PageResult<T> {
    private long pageNum;
    private long pageSize;
    private long total;
    private List<T> records;
}

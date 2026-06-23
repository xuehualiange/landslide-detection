package com.graduation.landslide.rag.dto;

import java.util.ArrayList;
import java.util.List;
import lombok.Data;

@Data
public class RagAskResponse {
    private String answer;
    private List<RagSourceItem> sources = new ArrayList<>();
}

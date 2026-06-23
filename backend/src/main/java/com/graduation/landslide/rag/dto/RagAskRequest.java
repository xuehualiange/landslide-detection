package com.graduation.landslide.rag.dto;

import javax.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class RagAskRequest {
    @NotBlank(message = "问题不能为空")
    private String question;
}
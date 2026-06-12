package com.virtuallover.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class SendMessageRequest {
    @NotBlank
    private String content;
    private String imageUrl;
}

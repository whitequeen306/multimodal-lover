package com.virtuallover.service.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CreateCharacterRequest {
    @NotBlank
    private String name;
    private String avatarUrl;
    private String gender;
    private String personality;
    private String speakingStyle;
    private String backstory;
    @NotBlank(message = "角色 Prompt 不能为空")
    private String promptTemplate;
}

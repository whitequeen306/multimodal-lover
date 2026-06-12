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
}

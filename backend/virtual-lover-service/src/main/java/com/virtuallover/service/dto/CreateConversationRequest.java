package com.virtuallover.service.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateConversationRequest {
    @NotNull
    private Long characterId;
}

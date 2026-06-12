package com.virtuallover.service.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {
    @Size(max = 50, message = "昵称不能超过 50 个字符")
    private String nickname;

    private String avatarUrl;
}

package com.virtuallover.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.virtuallover.common.base.Result;
import com.virtuallover.service.AuthService;
import com.virtuallover.service.dto.LoginRequest;
import com.virtuallover.service.dto.RegisterRequest;
import com.virtuallover.service.dto.UpdateProfileRequest;
import com.virtuallover.storage.MinioStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "Auth", description = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;
    private final MinioStorageService minioStorageService;

    @Operation(summary = "注册")
    @PostMapping("/register")
    public Result<Map<String, String>> register(@Valid @RequestBody RegisterRequest request) {
        String token = authService.register(request.getUsername(), request.getPassword(), request.getNickname());
        return Result.ok(Map.of("token", token));
    }

    @Operation(summary = "登录")
    @PostMapping("/login")
    public Result<Map<String, String>> login(@Valid @RequestBody LoginRequest request) {
        String token = authService.login(request.getUsername(), request.getPassword());
        return Result.ok(Map.of("token", token));
    }

    @Operation(summary = "当前用户")
    @GetMapping("/me")
    public Result<?> me() {
        return Result.ok(authService.getCurrentUser());
    }

    @Operation(summary = "更新个人资料")
    @PutMapping("/profile")
    public Result<?> updateProfile(@Valid @RequestBody UpdateProfileRequest request) {
        return Result.ok(authService.updateProfile(StpUtil.getLoginIdAsLong(), request));
    }

    @Operation(summary = "上传用户头像")
    @PostMapping("/avatar-upload")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String url = minioStorageService.uploadAvatar(file, StpUtil.getLoginIdAsLong(), "user-avatars");
        return Result.ok(Map.of("avatarUrl", url));
    }
}

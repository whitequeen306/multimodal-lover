package com.virtuallover.web.controller;

import com.virtuallover.common.base.Result;
import com.virtuallover.service.AuthService;
import com.virtuallover.service.dto.LoginRequest;
import com.virtuallover.service.dto.RegisterRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "Auth", description = "认证")
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {
    private final AuthService authService;

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
}

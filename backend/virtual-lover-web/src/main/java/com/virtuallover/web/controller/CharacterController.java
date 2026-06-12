package com.virtuallover.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.virtuallover.common.base.Result;
import com.virtuallover.dao.entity.Character;
import com.virtuallover.service.CharacterService;
import com.virtuallover.service.dto.CreateCharacterRequest;
import com.virtuallover.storage.QiniuStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Tag(name = "Character", description = "角色管理")
@RestController
@RequestMapping("/api/character")
@RequiredArgsConstructor
public class CharacterController {
    private final CharacterService characterService;
    private final QiniuStorageService qiniuStorageService;

    @GetMapping
    public Result<List<Character>> list() {
        return Result.ok(characterService.listByUser(StpUtil.getLoginIdAsLong()));
    }

    @GetMapping("/{id}")
    public Result<Character> get(@PathVariable Long id) {
        return Result.ok(characterService.getById(id));
    }

    @PostMapping
    public Result<Character> create(@Valid @RequestBody CreateCharacterRequest request) {
        return Result.ok(characterService.create(StpUtil.getLoginIdAsLong(), request));
    }

    @PutMapping("/{id}")
    public Result<Character> update(@PathVariable Long id, @Valid @RequestBody CreateCharacterRequest request) {
        return Result.ok(characterService.update(StpUtil.getLoginIdAsLong(), id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        characterService.delete(StpUtil.getLoginIdAsLong(), id);
        return Result.ok();
    }

    @Operation(summary = "上传角色头像")
    @PostMapping("/avatar-upload")
    public Result<Map<String, String>> uploadAvatar(@RequestParam("file") MultipartFile file) {
        String url = qiniuStorageService.uploadChatImage(file, StpUtil.getLoginIdAsLong());
        return Result.ok(Map.of("avatarUrl", url));
    }
}

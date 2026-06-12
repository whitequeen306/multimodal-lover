package com.virtuallover.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtuallover.ai.AiChatService;
import com.virtuallover.common.base.Result;
import com.virtuallover.dao.entity.Character;
import com.virtuallover.service.CharacterService;
import com.virtuallover.service.dto.CreateCharacterRequest;
import com.virtuallover.storage.QiniuStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

@Slf4j
@Tag(name = "Character", description = "角色管理")
@RestController
@RequestMapping("/api/character")
@RequiredArgsConstructor
public class CharacterController {
    private final CharacterService characterService;
    private final QiniuStorageService qiniuStorageService;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

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

    @Operation(summary = "AI 生成角色设定")
    @PostMapping("/generate")
    public Result<Map<String, String>> generate(@Valid @RequestBody GenerateRequest request) {
        String raw = aiChatService.generateCharacterPersona(request.getCharacterName());
        String json = raw.trim();
        if (json.startsWith("```")) {
            json = json.replaceAll("```json\\s*", "").replaceAll("```\\s*", "").trim();
        }
        try {
            var node = objectMapper.readTree(json);
            return Result.ok(Map.of(
                    "name", node.has("name") ? node.get("name").asText() : request.getCharacterName(),
                    "personality", node.has("personality") ? node.get("personality").asText() : "",
                    "speakingStyle", node.has("speakingStyle") ? node.get("speakingStyle").asText() : "",
                    "backstory", node.has("backstory") ? node.get("backstory").asText() : ""
            ));
        } catch (Exception e) {
            log.warn("Failed to parse AI persona JSON, raw: {}", raw);
            return Result.ok(Map.of(
                    "name", request.getCharacterName(),
                    "personality", raw,
                    "speakingStyle", "",
                    "backstory", ""
            ));
        }
    }

    @Data
    static class GenerateRequest {
        @NotBlank
        private String characterName;
    }
}

package com.virtuallover.web.controller;

import cn.dev33.satoken.stp.StpUtil;
import com.virtuallover.common.base.Result;
import com.virtuallover.dao.entity.Conversation;
import com.virtuallover.dao.entity.Message;
import com.virtuallover.service.ConversationService;
import com.virtuallover.service.dto.CreateConversationRequest;
import com.virtuallover.service.dto.SendMessageRequest;
import com.virtuallover.storage.MinioStorageService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;
import java.util.Map;

@Tag(name = "Conversation", description = "会话与消息")
@RestController
@RequestMapping("/api/conversation")
@RequiredArgsConstructor
public class ConversationController {
    private final ConversationService conversationService;
    private final MinioStorageService minioStorageService;

    @PostMapping
    public Result<Conversation> create(@Valid @RequestBody CreateConversationRequest request) {
        return Result.ok(conversationService.createConversation(StpUtil.getLoginIdAsLong(), request.getCharacterId()));
    }

    @GetMapping
    public Result<List<Conversation>> list() {
        return Result.ok(conversationService.listConversations(StpUtil.getLoginIdAsLong()));
    }

    @Operation(summary = "聊天图片代理（从 MinIO 读取，仅允许 chat-images 路径）")
    @GetMapping("/image")
    public ResponseEntity<byte[]> proxyImage(@RequestParam("url") String url) {
        byte[] bytes = minioStorageService.tryFetchImageBytes(url);
        if (bytes == null || bytes.length == 0) {
            return ResponseEntity.notFound().build();
        }
        String contentType = minioStorageService.guessContentType(url);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=3600")
                .contentType(MediaType.parseMediaType(contentType))
                .body(bytes);
    }

    @GetMapping("/{id}")
    public Result<Conversation> get(@PathVariable Long id) {
        return Result.ok(conversationService.getConversation(StpUtil.getLoginIdAsLong(), id));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        conversationService.deleteConversation(StpUtil.getLoginIdAsLong(), id);
        return Result.ok();
    }

    @PostMapping("/upload-image")
    public Result<Map<String, String>> uploadImage(@RequestParam("file") MultipartFile file) {
        String url = minioStorageService.uploadChatImage(file, StpUtil.getLoginIdAsLong());
        conversationService.preDescribeImageAsync(url);
        return Result.ok(Map.of("imageUrl", url));
    }

    @PostMapping(value = "/{id}/messages/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter sendMessageStream(@PathVariable Long id, @Valid @RequestBody SendMessageRequest request) {
        return conversationService.sendMessageStream(StpUtil.getLoginIdAsLong(), id, request);
    }

    @GetMapping("/{id}/messages")
    public Result<List<Message>> getMessages(@PathVariable Long id,
                                              @RequestParam(required = false) Long beforeSeq,
                                              @RequestParam(defaultValue = "50") int limit) {
        return Result.ok(conversationService.getMessages(StpUtil.getLoginIdAsLong(), id, beforeSeq, limit));
    }
}

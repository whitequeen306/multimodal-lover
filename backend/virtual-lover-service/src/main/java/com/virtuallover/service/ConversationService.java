package com.virtuallover.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtuallover.ai.AiChatService;
import com.virtuallover.ai.AssistantReplySplitter;
import com.virtuallover.ai.PromptBuilder;
import com.virtuallover.ai.VisionAnalysisResult;
import com.virtuallover.ai.VisionImageInput;
import com.virtuallover.ai.VisionResultParser;
import com.virtuallover.common.base.ErrorCode;
import com.virtuallover.common.exception.BusinessException;
import com.virtuallover.common.util.UserInputSanitizer;
import com.virtuallover.dao.entity.Character;
import com.virtuallover.dao.entity.Conversation;
import com.virtuallover.dao.entity.Message;
import com.virtuallover.dao.mapper.CharacterMapper;
import com.virtuallover.dao.mapper.ConversationMapper;
import com.virtuallover.dao.mapper.MessageMapper;
import com.virtuallover.service.dto.SendMessageRequest;
import com.virtuallover.storage.MinioStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {
    private final ConversationMapper conversationMapper;
    private final CharacterMapper characterMapper;
    private final MessageMapper messageMapper;
    private final AiChatService aiChatService;
    private final AssistantReplySplitter replySplitter;
    private final VisionResultParser visionResultParser;
    private final PromptBuilder promptBuilder;
    private final MinioStorageService minioStorageService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${lover.ai.context-window:20}")
    private int contextWindow;

    @Value("${lover.ai.max-replies-per-turn:3}")
    private int maxRepliesPerTurn;

    private static final String SEQ_KEY_PREFIX = "seq:conv:";
    private static final String VISION_CACHE_PREFIX = "lover:vision:";
    private static final Duration VISION_CACHE_TTL = Duration.ofHours(2);
    private static final int MAX_IMAGES_PER_MESSAGE = 2;

    @Transactional
    public Conversation createConversation(Long userId, Long characterId) {
        Character character = characterMapper.selectById(characterId);
        if (character == null || !character.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND);
        }

        Conversation existing = conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .eq(Conversation::getCharacterId, characterId)
                        .orderByDesc(Conversation::getCreatedAt)
                        .last("LIMIT 1"));
        if (existing != null) {
            return existing;
        }

        Conversation conv = new Conversation();
        conv.setUserId(userId);
        conv.setCharacterId(characterId);
        conv.setTitle(character.getName());
        conversationMapper.insert(conv);
        return conv;
    }

    public List<Conversation> listConversations(Long userId) {
        return conversationMapper.selectList(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .orderByDesc(Conversation::getCreatedAt));
    }

    public Conversation getConversation(Long userId, Long convId) {
        Conversation conv = conversationMapper.selectById(convId);
        if (conv == null || !conv.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CONVERSATION_NOT_FOUND);
        }
        return conv;
    }

    @Transactional
    public void deleteConversation(Long userId, Long convId) {
        getConversation(userId, convId);
        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, convId));
        conversationMapper.deleteById(convId);
        redisTemplate.delete(SEQ_KEY_PREFIX + convId);
    }

    /**
     * 上传后后台预识图，发送时可直接命中缓存。
     */
    public void preDescribeImageAsync(String imageUrl) {
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }
        CompletableFuture.runAsync(() -> {
            try {
                String trustedUrl = minioStorageService.resolveTrustedImageUrl(imageUrl);
                String cacheKey = visionCacheKey(List.of(trustedUrl));
                if (Boolean.TRUE.equals(redisTemplate.hasKey(cacheKey))) {
                    return;
                }
                VisionAnalysisResult result = resolveVisionAnalysis(List.of(trustedUrl), null, null);
                if (result == null) {
                    log.warn("Pre-describe returned null, url={}", imageUrl);
                    return;
                }
                log.info("Pre-described image cached, key={}", cacheKey);
            } catch (Exception e) {
                log.warn("Pre-describe image failed, url={}, reason={}", imageUrl, e.getMessage());
            }
        });
    }

    /**
     * SSE 流式发送消息：先返回连接，识图与对话在后台异步执行。
     */
    public SseEmitter sendMessageStream(Long userId, Long convId, SendMessageRequest request) {
        Conversation conv = getConversation(userId, convId);
        Character character = characterMapper.selectById(conv.getCharacterId());
        if (character == null) {
            throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND);
        }

        String rawContent = request.getContent() == null ? "" : request.getContent();
        UserInputSanitizer.SanitizedUserText sanitized =
                UserInputSanitizer.sanitizeChatMessage(rawContent);
        String safeContent = sanitized.storedText();
        String modelContent = sanitized.modelText();

        List<String> rawUrls = request.resolveImageUrls();
        if (rawUrls.size() > MAX_IMAGES_PER_MESSAGE) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "一次最多发送 2 张图片");
        }
        List<String> imageUrls = rawUrls.stream()
                .map(minioStorageService::resolveTrustedImageUrl)
                .collect(Collectors.toList());

        long seq = redisTemplate.opsForValue().increment(SEQ_KEY_PREFIX + convId);

        Message userMsg = new Message();
        userMsg.setConversationId(convId);
        userMsg.setRole("user");
        userMsg.setContent(safeContent);
        userMsg.setImageUrlList(imageUrls);
        userMsg.setSeq((int) seq);
        messageMapper.insert(userMsg);

        List<Message> history = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, convId)
                        .orderByDesc(Message::getSeq)
                        .last("LIMIT " + contextWindow));
        Collections.reverse(history);

        SseEmitter emitter = new SseEmitter(300_000L);
        boolean hasImage = !imageUrls.isEmpty();

        CompletableFuture.runAsync(() -> runMessagePipeline(
                emitter, convId, character, userMsg, history, imageUrls, safeContent, modelContent, hasImage));

        return emitter;
    }

    private void runMessagePipeline(
            SseEmitter emitter,
            Long convId,
            Character character,
            Message userMsg,
            List<Message> history,
            List<String> imageUrls,
            String userText,
            String modelUserText,
            boolean hasImage) {
        try {
            VisionAnalysisResult visionAnalysis = null;
            if (hasImage) {
                sendSseStatus(emitter, "vision");
                try {
                    visionAnalysis = resolveVisionAnalysis(imageUrls, character, userText);
                    userMsg.setVisionContext(visionAnalysis.toContextBlock());
                    messageMapper.updateById(userMsg);
                    if (visionAnalysis.lowQualityGate()) {
                        sendSseVisionQuality(emitter, "low");
                    }
                } catch (Exception e) {
                    log.error("VL analyze failed for imageUrls={}", imageUrls.size(), e);
                    visionAnalysis = VisionAnalysisResult.fallback(
                            "图片暂时无法识别，但你可以根据用户的文字来回应。");
                }
            }
            runSingleReplyPipeline(emitter, convId, character, userMsg, history, visionAnalysis, modelUserText);
        } catch (BusinessException e) {
            log.warn("Message pipeline business error for convId={}: {}", convId, e.getMessage());
            failPipeline(emitter, e);
        } catch (Exception e) {
            log.error("Message pipeline failed for convId={}", convId, e);
            failPipeline(emitter, e);
        }
    }

    private void runSingleReplyPipeline(
            SseEmitter emitter,
            Long convId,
            Character character,
            Message userMsg,
            List<Message> history,
            VisionAnalysisResult visionAnalysis,
            String modelUserText) throws IOException {
        int maxPieces = Math.max(1, Math.min(maxRepliesPerTurn, 5));
        String systemPrompt = promptBuilder.buildSystemPrompt(character)
                + promptBuilder.buildMultiReplyGuidance(maxPieces);
        if (visionAnalysis != null && visionAnalysis.category() != null) {
            systemPrompt += promptBuilder.buildCategoryReplyGuidance(visionAnalysis.category());
            systemPrompt += promptBuilder.buildSubIntentReplyGuidance(visionAnalysis.subIntent());
            if (visionAnalysis.lowQualityGate() || visionAnalysis.quality().needsCaution()) {
                systemPrompt += promptBuilder.buildLowQualityGuidance();
            }
        }
        String priorImageContext = findPriorImageContext(history, userMsg.getId());
        if (priorImageContext != null && !priorImageContext.isBlank()) {
            systemPrompt += "\n\n=== 会话内历史图片摘要 ===\n" + priorImageContext;
        }

        int historyUserWithVision = (int) history.stream()
                .filter(m -> "user".equals(m.getRole()))
                .filter(m -> m.getVisionContext() != null && !m.getVisionContext().isBlank())
                .count();
        log.info("[CTX_ASSEMBLY] convId={} historyMessages={} contextWindow={} currentHasVision={} "
                        + "priorImageInSystem={} historyUserMsgsWithVision={}",
                convId, history.size(), contextWindow, visionAnalysis != null,
                priorImageContext != null, historyUserWithVision);
        if (priorImageContext != null) {
            log.info("[CTX_PRIOR_IMAGE] convId={} summary={}", convId, priorImageContext);
        }

        String visionContext = visionAnalysis != null ? visionAnalysis.toContextBlock() : null;
        List<org.springframework.ai.chat.messages.Message> aiMessages = new ArrayList<>();
        aiMessages.add(new SystemMessage(systemPrompt));
        aiMessages.addAll(buildAiMessagesFromHistory(history, userMsg, visionContext, modelUserText));

        sendSseStatus(emitter, "chat");
        String fullContent = streamCollect(emitter, aiMessages);
        List<String> pieces = replySplitter.split(fullContent, maxPieces);
        if (pieces.isEmpty() && fullContent != null && !fullContent.isBlank()) {
            pieces = List.of(fullContent.trim());
        }
        persistAssistantPieces(convId, pieces);
        if (pieces.size() > 1) {
            sendSplitContents(emitter, pieces);
        }
        sendDone(emitter);
        emitter.complete();
    }

    private String findPriorImageContext(List<Message> history, Long currentMsgId) {
        Message prior = null;
        for (int i = history.size() - 1; i >= 0; i--) {
            Message msg = history.get(i);
            if (msg.getId() != null && msg.getId().equals(currentMsgId)) {
                continue;
            }
            if ("user".equals(msg.getRole()) && msg.hasImages()) {
                prior = msg;
                break;
            }
        }
        if (prior == null) {
            return null;
        }
        if (prior.getVisionContext() != null && !prior.getVisionContext().isBlank()) {
            return "[上一轮图片]\n" + prior.getVisionContext().trim();
        }
        int count = prior.getImageUrlList().size();
        return count > 1
                ? "[上一轮图片] 用户曾发送 " + count + " 张图片（摘要未缓存）"
                : "[上一轮图片] 用户曾发送一张图片（摘要未缓存）";
    }

    private List<org.springframework.ai.chat.messages.Message> buildAiMessagesFromHistory(
            List<Message> history,
            Message userMsg,
            String visionDescription,
            String modelUserText) {
        List<org.springframework.ai.chat.messages.Message> aiMessages = new ArrayList<>();
        for (Message msg : history) {
            if ("user".equals(msg.getRole())) {
                boolean isCurrent = msg.getId() != null && msg.getId().equals(userMsg.getId());
                String combined = buildCombinedUserContent(
                        msg,
                        isCurrent ? visionDescription : null,
                        isCurrent,
                        isCurrent ? modelUserText : null);
                if (!combined.isBlank()) {
                    aiMessages.add(new UserMessage(combined));
                }
            } else if ("assistant".equals(msg.getRole())) {
                if (msg.getContent() != null && !msg.getContent().isBlank()) {
                    aiMessages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }
        return aiMessages;
    }

    private String streamCollect(
            SseEmitter emitter,
            List<org.springframework.ai.chat.messages.Message> messages) {
        StringBuilder fullContent = new StringBuilder();
        final boolean[] failed = {false};
        final Throwable[] error = {null};

        aiChatService.streamChatCompletionSync(
                messages,
                text -> {
                    fullContent.append(text);
                    try {
                        sendSseChunk(emitter, text);
                    } catch (IOException e) {
                        log.error("SSE send error", e);
                    }
                },
                () -> { },
                throwable -> {
                    failed[0] = true;
                    error[0] = throwable;
                });

        if (failed[0]) {
            throw error[0] instanceof RuntimeException re
                    ? re
                    : new RuntimeException(error[0]);
        }
        return fullContent.toString();
    }

    private void persistAssistantPieces(Long convId, List<String> pieces) {
        if (pieces == null || pieces.isEmpty()) {
            return;
        }
        for (String piece : pieces) {
            if (piece == null || piece.isBlank()) {
                continue;
            }
            long replySeq = redisTemplate.opsForValue().increment(SEQ_KEY_PREFIX + convId);
            Message assistantMsg = new Message();
            assistantMsg.setConversationId(convId);
            assistantMsg.setRole("assistant");
            assistantMsg.setContent(piece.trim());
            assistantMsg.setSeq((int) replySeq);
            messageMapper.insert(assistantMsg);
        }
    }

    private VisionAnalysisResult resolveVisionAnalysis(
            List<String> imageUrls, Character character, String userText) {
        if (imageUrls == null || imageUrls.isEmpty()) {
            return VisionAnalysisResult.fallback("无法识别图片内容");
        }
        String cacheKey = visionCacheKey(imageUrls);
        String cached = redisTemplate.opsForValue().get(cacheKey);
        if (cached != null && !cached.isBlank()) {
            log.info("[VL_CACHE_HIT] key={} contextBlock={}", cacheKey, cached);
            return visionResultParser.parseContextBlock(cached);
        }
        log.info("[VL_CACHE_MISS] key={} imageCount={}", cacheKey, imageUrls.size());

        List<VisionImageInput> inputs = new ArrayList<>();
        for (String url : imageUrls) {
            byte[] imageBytes = minioStorageService.fetchImageBytes(url);
            String contentType = minioStorageService.guessContentType(url);
            inputs.add(new VisionImageInput(imageBytes, contentType, minioStorageService.toObjectKey(url)));
        }
        VisionAnalysisResult result = aiChatService.analyzeImages(inputs, character, userText);
        if (result == null) {
            return VisionAnalysisResult.fallback("无法识别图片内容");
        }
        redisTemplate.opsForValue().set(cacheKey, result.toContextBlock(), VISION_CACHE_TTL);
        return result;
    }

    private String visionCacheKey(List<String> imageUrls) {
        List<String> keys = imageUrls.stream()
                .map(minioStorageService::toObjectKey)
                .sorted()
                .collect(Collectors.toList());
        return VISION_CACHE_PREFIX + "multi:" + String.join("+", keys);
    }

    public List<Message> getMessages(Long userId, Long convId, Long beforeSeq, int limit) {
        getConversation(userId, convId);
        LambdaQueryWrapper<Message> wrapper = new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, convId)
                .orderByDesc(Message::getSeq);
        if (beforeSeq != null) {
            wrapper.lt(Message::getSeq, beforeSeq);
        }
        wrapper.last("LIMIT " + Math.min(limit, 50));
        List<Message> list = messageMapper.selectList(wrapper);
        Collections.reverse(list);
        return list;
    }

    private String buildCombinedUserContent(
            Message msg,
            String visionDescription,
            boolean isCurrent,
            String modelUserText) {
        StringBuilder sb = new StringBuilder();
        List<String> urls = msg.getImageUrlList();
        boolean hasImage = !urls.isEmpty();
        boolean hasText = msg.getContent() != null && !msg.getContent().isBlank();

        if (isCurrent && visionDescription != null && !visionDescription.isBlank()) {
            sb.append(visionDescription);
        } else if (hasImage) {
            if (msg.getVisionContext() != null && !msg.getVisionContext().isBlank()) {
                sb.append(msg.getVisionContext().trim());
            } else if (urls.size() > 1) {
                sb.append("[用户发送了").append(urls.size()).append("张图片]");
            } else {
                sb.append("[用户发送了一张图片]");
            }
        }

        if (hasText) {
            if (!sb.isEmpty()) {
                sb.append("\n\n");
            }
            if (isCurrent && modelUserText != null && !modelUserText.isBlank()) {
                sb.append(modelUserText.trim());
            } else {
                sb.append(UserInputSanitizer.wrapStoredTextForModel(msg.getContent().trim()));
            }
        }

        return sb.toString();
    }

    private void sendSseChunk(SseEmitter emitter, String text) throws IOException {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("content", text);
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    private void sendSseStatus(SseEmitter emitter, String status) throws IOException {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("status", status);
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    private void sendSseVisionQuality(SseEmitter emitter, String quality) throws IOException {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("visionQuality", quality);
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    private void sendSplitContents(SseEmitter emitter, List<String> pieces) throws IOException {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("splitContents", pieces);
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    private void sendDone(SseEmitter emitter) throws IOException {
        Map<String, Boolean> payload = new LinkedHashMap<>();
        payload.put("done", true);
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    private void sendSseError(SseEmitter emitter, String message) throws IOException {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("error", message);
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    private void failPipeline(SseEmitter emitter, Exception e) {
        String message;
        if (e instanceof BusinessException be) {
            message = be.getCustomMessage() != null && !be.getCustomMessage().isBlank()
                    ? be.getCustomMessage()
                    : be.getErrorCode().getMessage();
        } else {
            message = "消息处理失败，请稍后重试";
        }
        try {
            sendSseError(emitter, message);
            emitter.complete();
        } catch (IOException ioException) {
            log.error("Failed to send SSE error event", ioException);
            emitter.completeWithError(ioException);
        }
    }
}

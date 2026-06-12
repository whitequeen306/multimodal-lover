package com.virtuallover.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtuallover.ai.AiChatService;
import com.virtuallover.ai.PromptBuilder;
import com.virtuallover.common.base.ErrorCode;
import com.virtuallover.common.exception.BusinessException;
import com.virtuallover.common.util.UserInputSanitizer;
import com.virtuallover.dao.entity.Character;
import com.virtuallover.dao.entity.Conversation;
import com.virtuallover.dao.entity.Message;
import com.virtuallover.dao.mapper.CharacterMapper;
import com.virtuallover.dao.mapper.ConversationMapper;
import com.virtuallover.dao.mapper.MessageMapper;
import com.virtuallover.service.dto.CreateConversationRequest;
import com.virtuallover.service.dto.SendMessageRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {
    private final ConversationMapper conversationMapper;
    private final CharacterMapper characterMapper;
    private final MessageMapper messageMapper;
    private final AiChatService aiChatService;
    private final PromptBuilder promptBuilder;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${lover.ai.context-window:20}")
    private int contextWindow;

    private static final String SEQ_KEY_PREFIX = "seq:conv:";

    @Transactional
    public Conversation createConversation(Long userId, Long characterId) {
        Character character = characterMapper.selectById(characterId);
        if (character == null || !character.getOwnerUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND);
        }

        // Reuse existing conversation if one exists for this user+character pair
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
        Conversation conv = getConversation(userId, convId);
        messageMapper.delete(new LambdaQueryWrapper<Message>()
                .eq(Message::getConversationId, convId));
        conversationMapper.deleteById(convId);
        redisTemplate.delete(SEQ_KEY_PREFIX + convId);
    }

    /**
     * SSE 流式发送消息 - 核心方法。
     * 使用 AiChatService.buildChatModel() 自行管理流式调用与 SSE 事件发送，
     * 以便在流式完成后持久化 assistant 消息。
     */
    public SseEmitter sendMessageStream(Long userId, Long convId, SendMessageRequest request) {
        Conversation conv = getConversation(userId, convId);
        Character character = characterMapper.selectById(conv.getCharacterId());
        if (character == null) {
            throw new BusinessException(ErrorCode.CHARACTER_NOT_FOUND);
        }

        // Sanitize user input (security hardening)
        UserInputSanitizer.SanitizedUserText sanitized =
                UserInputSanitizer.sanitizeChatMessage(request.getContent());
        String safeContent = sanitized.storedText();

        // Generate seq
        long seq = redisTemplate.opsForValue().increment(SEQ_KEY_PREFIX + convId);

        // Save user message
        Message userMsg = new Message();
        userMsg.setConversationId(convId);
        userMsg.setRole("user");
        userMsg.setContent(safeContent);
        userMsg.setImageUrl(request.getImageUrl());
        userMsg.setSeq((int) seq);
        messageMapper.insert(userMsg);

        // Fetch history (including the just-inserted user message)
        List<Message> history = messageMapper.selectList(
                new LambdaQueryWrapper<Message>()
                        .eq(Message::getConversationId, convId)
                        .orderByDesc(Message::getSeq)
                        .last("LIMIT " + contextWindow));
        Collections.reverse(history);

        // Stage-1: VL image description
        String visionDescription = null;
        if (request.getImageUrl() != null && !request.getImageUrl().isBlank()) {
            try {
                visionDescription = aiChatService.describeImage(request.getImageUrl(), character);
            } catch (Exception e) {
                log.error("VL describe failed for imageUrl={}", request.getImageUrl(), e);
                visionDescription = "[图片描述] 图片暂时无法识别，但你可以根据用户的文字来回应。";
            }
        }

        // Build system prompt
        String systemPrompt = promptBuilder.buildSystemPrompt(character);

        // Convert to Spring AI Message list
        List<org.springframework.ai.chat.messages.Message> aiMessages = new ArrayList<>();
        for (Message msg : history) {
            if ("user".equals(msg.getRole())) {
                String content = msg.getContent() != null ? msg.getContent() : "";
                if (msg.getId().equals(userMsg.getId()) && visionDescription != null) {
                    content = content + "\n\n" + visionDescription;
                }
                aiMessages.add(new UserMessage(content));
            } else if ("assistant".equals(msg.getRole())) {
                if (msg.getContent() != null && !msg.getContent().isBlank()) {
                    aiMessages.add(new AssistantMessage(msg.getContent()));
                }
            }
        }

        // Build prompt with system + history
        List<org.springframework.ai.chat.messages.Message> allMessages = new ArrayList<>();
        allMessages.add(new SystemMessage(systemPrompt));
        allMessages.addAll(aiMessages);

        // Get chat model and create SseEmitter
        OpenAiChatModel chatModel = aiChatService.buildChatModel();
        SseEmitter emitter = new SseEmitter(300_000L);
        StringBuilder fullContent = new StringBuilder();

        Prompt prompt = new Prompt(allMessages, OpenAiChatOptions.builder()
                .model(chatModel.getDefaultOptions().getModel())
                .temperature(0.8)
                .build());

        Flux<ChatResponse> flux = chatModel.stream(prompt);
        flux.doOnNext(response -> {
                    String text = extractText(response);
                    if (text != null && !text.isEmpty()) {
                        fullContent.append(text);
                        try {
                            sendSseChunk(emitter, text);
                        } catch (IOException e) {
                            log.error("SSE send error", e);
                        }
                    }
                })
                .doOnComplete(() -> {
                    try {
                        // Save assistant message
                        if (!fullContent.isEmpty()) {
                            long replySeq = redisTemplate.opsForValue()
                                    .increment(SEQ_KEY_PREFIX + convId);
                            Message assistantMsg = new Message();
                            assistantMsg.setConversationId(convId);
                            assistantMsg.setRole("assistant");
                            assistantMsg.setContent(fullContent.toString());
                            assistantMsg.setSeq((int) replySeq);
                            messageMapper.insert(assistantMsg);
                        }
                        sendDone(emitter);
                        emitter.complete();
                    } catch (Exception e) {
                        log.error("Failed to save assistant message or send done", e);
                        emitter.completeWithError(e);
                    }
                })
                .doOnError(error -> {
                    log.error("AI stream error for convId={}", convId, error);
                    emitter.completeWithError(error);
                })
                .subscribe();

        return emitter;
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

    // ---- private SSE helpers ----

    private void sendSseChunk(SseEmitter emitter, String text) throws IOException {
        Map<String, String> payload = new LinkedHashMap<>();
        payload.put("content", text);
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    private void sendDone(SseEmitter emitter) throws IOException {
        Map<String, Boolean> payload = new LinkedHashMap<>();
        payload.put("done", true);
        emitter.send(SseEmitter.event().data(objectMapper.writeValueAsString(payload)));
    }

    private String extractText(ChatResponse response) {
        if (response == null || response.getResult() == null) {
            return null;
        }
        var output = response.getResult().getOutput();
        if (output == null) {
            return null;
        }
        return output.getText();
    }
}

package com.virtuallover.ai;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtuallover.dao.entity.Character;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.stereotype.Service;
import org.springframework.util.MimeTypeUtils;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

@Slf4j
@Service
public class AiChatService {

    private final PromptBuilder promptBuilder;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Value("${lover.ai.vision.base-url}")
    private String visionBaseUrl;

    @Value("${lover.ai.vision.model}")
    private String visionModel;

    @Value("${lover.ai.vision.api-key}")
    private String visionApiKey;

    @Value("${lover.ai.chat.base-url}")
    private String chatBaseUrl;

    @Value("${lover.ai.chat.model}")
    private String chatModel;

    @Value("${lover.ai.chat.api-key}")
    private String chatApiKey;

    public AiChatService(PromptBuilder promptBuilder, ObjectMapper objectMapper) {
        this.promptBuilder = promptBuilder;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * VL 识图：下载图片 → 调用 VL 模型描述 → 返回 [图片描述] 前缀文本
     */
    public String describeImage(String imageUrl, Character character) {
        byte[] imageBytes = downloadImage(imageUrl);
        String contentType = guessContentType(imageUrl);

        String visionPromptText = promptBuilder.buildVisionPrompt(character);

        OpenAiApi visionApi = OpenAiApi.builder()
                .baseUrl(normalizeBaseUrl(visionBaseUrl))
                .apiKey(visionApiKey)
                .build();
        OpenAiChatModel visionModel = OpenAiChatModel.builder()
                .openAiApi(visionApi)
                .defaultOptions(OpenAiChatOptions.builder().model(this.visionModel).build())
                .build();

        Media media = Media.builder()
                .mimeType(MimeTypeUtils.parseMimeType(contentType))
                .data(new ByteArrayResource(imageBytes))
                .build();
        UserMessage userMessage = UserMessage.builder()
                .text(visionPromptText)
                .media(media)
                .build();

        Prompt prompt = new Prompt(List.of(userMessage), OpenAiChatOptions.builder()
                .model(this.visionModel)
                .temperature(0.3)
                .maxTokens(500)
                .build());

        ChatResponse response = visionModel.call(prompt);
        String text = extractText(response);
        if (text == null || text.isBlank()) {
            log.warn("VL describe returned empty text for imageUrl={}", imageUrl);
            return "[图片描述] 无法识别这张图片的内容";
        }
        log.info("Image described via {} ({} chars)", this.visionModel, text.length());
        return "[图片描述] " + text.trim();
    }

    /**
     * 流式聊天：构建 SystemPrompt + 历史消息 → SSE 流式输出
     */
    public SseEmitter chatStream(String systemPrompt, List<Message> messages) {
        SseEmitter emitter = new SseEmitter(300_000L);

        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(normalizeBaseUrl(chatBaseUrl))
                .apiKey(chatApiKey)
                .build();
        OpenAiChatModel chatModelInstance = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(this.chatModel).build())
                .build();

        List<Message> allMessages = new java.util.ArrayList<>();
        allMessages.add(new SystemMessage(systemPrompt));
        allMessages.addAll(messages);

        Prompt prompt = new Prompt(allMessages, OpenAiChatOptions.builder()
                .model(this.chatModel)
                .temperature(0.8)
                .build());

        Flux<ChatResponse> flux = chatModelInstance.stream(prompt);
        flux.doOnNext(response -> {
                    String text = extractText(response);
                    if (text != null && !text.isEmpty()) {
                        try {
                            sendSseChunk(emitter, text);
                        } catch (IOException e) {
                            log.error("SSE send error", e);
                        }
                    }
                })
                .doOnComplete(() -> {
                    try {
                        sendDone(emitter);
                        emitter.complete();
                    } catch (IOException e) {
                        log.warn("SSE done send failed", e);
                        emitter.complete();
                    }
                })
                .doOnError(error -> {
                    log.error("AI stream error", error);
                    emitter.completeWithError(error);
                })
                .subscribe();

        return emitter;
    }

    /**
     * 构建聊天模型（可供外部使用）
     */
    public OpenAiChatModel buildChatModel() {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(normalizeBaseUrl(chatBaseUrl))
                .apiKey(chatApiKey)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(OpenAiChatOptions.builder().model(this.chatModel).build())
                .build();
    }

    /**
     * 构建 VL 模型（可供外部使用）
     */
    public OpenAiChatModel buildVisionModel() {
        OpenAiApi visionApi = OpenAiApi.builder()
                .baseUrl(normalizeBaseUrl(visionBaseUrl))
                .apiKey(visionApiKey)
                .build();
        return OpenAiChatModel.builder()
                .openAiApi(visionApi)
                .defaultOptions(OpenAiChatOptions.builder().model(this.visionModel).build())
                .build();
    }

    // ---- private helpers ----

    private byte[] downloadImage(String imageUrl) {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(imageUrl))
                    .timeout(Duration.ofSeconds(30))
                    .GET()
                    .build();
            HttpResponse<InputStream> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                throw new RuntimeException("Failed to download image, status: " + response.statusCode());
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            try (InputStream is = response.body()) {
                byte[] chunk = new byte[8192];
                int n;
                while ((n = is.read(chunk)) != -1) {
                    buffer.write(chunk, 0, n);
                }
            }
            return buffer.toByteArray();
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new RuntimeException("Failed to download image from: " + imageUrl, e);
        }
    }

    private String guessContentType(String imageUrl) {
        String lower = imageUrl.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        return "image/jpeg";
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

    /**
     * Remove trailing /v1 to avoid double /v1/v1 in requests
     */
    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            throw new IllegalStateException("AI base URL not configured");
        }
        String trimmed = baseUrl.replaceAll("/$", "");
        if (trimmed.endsWith("/v1")) {
            return trimmed.substring(0, trimmed.length() - 3);
        }
        return trimmed;
    }
}

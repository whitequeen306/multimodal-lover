package com.virtuallover.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtuallover.common.base.ErrorCode;
import com.virtuallover.common.exception.BusinessException;
import com.virtuallover.dao.entity.Character;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import java.util.concurrent.CompletableFuture;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.messages.AssistantMessage;
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

@Slf4j
@Service
public class AiChatService {

    private final PromptBuilder promptBuilder;
    private final VisionResultParser visionResultParser;
    private final ImagePreprocessor imagePreprocessor;
    private final VisionUserIntentDetector userIntentDetector;
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

    public AiChatService(
            PromptBuilder promptBuilder,
            VisionResultParser visionResultParser,
            ImagePreprocessor imagePreprocessor,
            VisionUserIntentDetector userIntentDetector,
            ObjectMapper objectMapper) {
        this.promptBuilder = promptBuilder;
        this.visionResultParser = visionResultParser;
        this.imagePreprocessor = imagePreprocessor;
        this.userIntentDetector = userIntentDetector;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    /**
     * VL 识图 + 场景分类（结构化结果，单图）。
     */
    public VisionAnalysisResult analyzeImageBytes(byte[] imageBytes, String contentType, Character character) {
        return analyzeImageBytes(imageBytes, contentType, character, "bytes", null);
    }

    public VisionAnalysisResult analyzeImageBytes(
            byte[] imageBytes, String contentType, Character character, String userText) {
        return analyzeImageBytes(imageBytes, contentType, character, "bytes", userText);
    }

    /**
     * VL 联合识图：1～2 张图一次 VL 调用。
     */
    public VisionAnalysisResult analyzeImages(
            List<VisionImageInput> images, Character character, String userText) {
        if (images == null || images.isEmpty()) {
            return VisionAnalysisResult.fallback("无法识别图片内容");
        }
        if (images.size() == 1) {
            VisionImageInput img = images.get(0);
            return analyzeImageBytes(img.bytes(), img.contentType(), character, img.logRef(), userText);
        }
        return analyzeMultipleImages(images, character, userText);
    }

    private VisionAnalysisResult analyzeImageBytes(
            byte[] imageBytes, String contentType, Character character, String logRef, String userText) {
        byte[] processed = imagePreprocessor.preprocess(imageBytes, contentType);
        String normalizedType = imagePreprocessor.normalizeContentType(contentType);
        return callVisionApi(
                List.of(new PreparedImage(processed, normalizedType)),
                character,
                userText,
                logRef,
                384);
    }

    private VisionAnalysisResult analyzeMultipleImages(
            List<VisionImageInput> images, Character character, String userText) {
        List<PreparedImage> prepared = new ArrayList<>();
        for (VisionImageInput img : images) {
            byte[] processed = imagePreprocessor.preprocess(img.bytes(), img.contentType());
            String normalizedType = imagePreprocessor.normalizeContentType(img.contentType());
            prepared.add(new PreparedImage(processed, normalizedType));
        }
        String logRef = images.stream().map(VisionImageInput::logRef).reduce((a, b) -> a + "+" + b).orElse("multi");
        return callVisionApi(prepared, character, userText, logRef, 640);
    }

    private VisionAnalysisResult callVisionApi(
            List<PreparedImage> images,
            Character character,
            String userText,
            String logRef,
            int maxTokens) {
        if (visionApiKey == null || visionApiKey.isBlank()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "未配置 AI API Key");
        }

        try {
            String userHint = userIntentDetector.hintForVl(userText);
            String visionPromptText = promptBuilder.buildVisionPrompt(character, images.size(), userHint);

            List<Map<String, Object>> contentParts = new ArrayList<>();
            contentParts.add(Map.of("type", "text", "text", visionPromptText));
            for (PreparedImage img : images) {
                String dataUrl = "data:" + img.contentType() + ";base64,"
                        + java.util.Base64.getEncoder().encodeToString(img.bytes());
                contentParts.add(Map.of("type", "image_url", "image_url", Map.of("url", dataUrl)));
            }
            List<Map<String, Object>> messages = List.of(
                    Map.of("role", "user", "content", contentParts));

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", visionModel);
            body.put("messages", messages);
            body.put("max_tokens", maxTokens);
            body.put("temperature", 0.2);
            body.put("enable_thinking", false);

            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(visionBaseUrl) + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + visionApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("VL describe failed, status={}", response.statusCode());
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            String text = contentNode.isMissingNode() ? "" : contentNode.asText();
            if (text.isBlank()) {
                log.warn("VL analyze returned empty text for ref={}", logRef);
                return VisionAnalysisResult.fallback("无法识别这张图片的内容");
            }
            VisionAnalysisResult parsed = visionResultParser.parse(text.trim());
            VisionSubIntent mergedIntent = userIntentDetector.merge(parsed.subIntent(),
                    userIntentDetector.detect(userText));
            VisionAnalysisResult result = new VisionAnalysisResult(
                    parsed.category(),
                    parsed.description(),
                    parsed.contextBlock(),
                    parsed.confidence(),
                    mergedIntent,
                    parsed.quality(),
                    parsed.imageCount(),
                    parsed.categoryAdjusted());
            log.info("Image analyzed via {}, category={}, quality={}, intent={}, images={}, ref={}",
                    visionModel, result.category(), result.quality(), result.subIntent(),
                    result.imageCount(), logRef);
            return result;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.error("VL describe request failed, ref={}", logRef, e);
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "图片识别失败");
        }
    }

    private record PreparedImage(byte[] bytes, String contentType) {
    }

    /**
     * 流式聊天（异步）：直连 DashScope，关闭思考模式以加快首字输出。
     */
    public void streamChatCompletion(
            List<Message> messages,
            Consumer<String> onChunk,
            Runnable onComplete,
            Consumer<Throwable> onError) {
        CompletableFuture.runAsync(() ->
                doStreamChatCompletion(messages, onChunk, onComplete, onError));
    }

    /**
     * 流式聊天（同步）：供已在后台线程中的流水线调用。
     */
    public void streamChatCompletionSync(
            List<Message> messages,
            Consumer<String> onChunk,
            Runnable onComplete,
            Consumer<Throwable> onError) {
        doStreamChatCompletion(messages, onChunk, onComplete, onError);
    }

    private void doStreamChatCompletion(
            List<Message> messages,
            Consumer<String> onChunk,
            Runnable onComplete,
            Consumer<Throwable> onError) {
        if (chatApiKey == null || chatApiKey.isBlank()) {
            onError.accept(new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "未配置 AI API Key"));
            return;
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", chatModel);
        body.put("messages", toApiMessages(messages));
        body.put("temperature", 0.8);
        body.put("stream", true);
        body.put("enable_thinking", false);

        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(chatBaseUrl) + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(300))
                    .header("Authorization", "Bearer " + chatApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<java.io.InputStream> response = httpClient.send(
                    request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                String errorBody = new String(response.body().readAllBytes(), StandardCharsets.UTF_8);
                log.warn("DashScope stream failed, status={}, body={}", response.statusCode(), errorBody);
                onError.accept(new BusinessException(ErrorCode.AI_PROVIDER_ERROR));
                return;
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(response.body(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.startsWith("data:")) {
                        continue;
                    }
                    String data = line.substring(5).trim();
                    if (data.isEmpty() || "[DONE]".equals(data)) {
                        continue;
                    }
                    JsonNode root = objectMapper.readTree(data);
                    JsonNode contentNode = root.path("choices").path(0).path("delta").path("content");
                    if (!contentNode.isMissingNode() && !contentNode.isNull()) {
                        String chunk = contentNode.asText();
                        if (!chunk.isEmpty()) {
                            onChunk.accept(chunk);
                        }
                    }
                }
            }
            onComplete.run();
        } catch (BusinessException e) {
            onError.accept(e);
        } catch (Exception e) {
            log.error("DashScope stream request failed", e);
            onError.accept(new BusinessException(ErrorCode.AI_PROVIDER_ERROR));
        }
    }

    /**
     * 流式聊天：构建 SystemPrompt + 历史消息 → SSE 流式输出
     */
    public SseEmitter chatStream(String systemPrompt, List<Message> messages) {
        SseEmitter emitter = new SseEmitter(300_000L);

        List<Message> allMessages = new ArrayList<>();
        allMessages.add(new SystemMessage(systemPrompt));
        allMessages.addAll(messages);

        streamChatCompletion(
                allMessages,
                text -> {
                    try {
                        sendSseChunk(emitter, text);
                    } catch (IOException e) {
                        log.error("SSE send error", e);
                    }
                },
                () -> {
                    try {
                        sendDone(emitter);
                        emitter.complete();
                    } catch (IOException e) {
                        log.warn("SSE done send failed", e);
                        emitter.complete();
                    }
                },
                error -> {
                    log.error("AI stream error", error);
                    emitter.completeWithError(error);
                });

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

    /**
     * AI 生成角色设定：联网搜索 + 大模型总结 → 返回人设 JSON
     */
    public String generateCharacterPersona(String characterName) {
        String sanitizedName = characterName == null ? "" : characterName.trim();
        if (sanitizedName.isEmpty()) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请输入角色名称");
        }
        if (sanitizedName.length() > 64) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "角色名称不能超过 64 个字符");
        }

        String systemPrompt = buildPersonaGenerationPrompt();
        String userPrompt = "请联网搜索并整理角色「" + sanitizedName + "」的公开资料，生成完整的人设设定。";

        String text = callChatCompletionsWithWebSearch(
                List.of(
                        Map.of("role", "system", "content", systemPrompt),
                        Map.of("role", "user", "content", userPrompt)),
                2000,
                0.7);
        if (text == null || text.isBlank()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "AI 生成失败，请稍后重试");
        }
        return text.trim();
    }

    private String buildPersonaGenerationPrompt() {
        return """
                你是一个专业的二次元角色设定生成器。用户会输入动漫/游戏角色名，你必须先联网调研再写设定。

                ## 必做流程
                - 联网搜索确认：来源作品、时间线阶段、核心关系、官方/公认称呼，再动笔。
                - 至少核对 2 类可靠来源（原作设定/官方资料/权威百科），禁止凭印象瞎编。
                - 写完后自检：称呼、语气、价值观、禁忌、关系边界是否与原作一致。

                ## 人设写作标准
                - 禁止模板化泛用称呼（如 Darling、亲爱的、主人）；必须写该角色在原作中**自然会用的称呼**。
                - 必须体现「外在语气 + 内在动机 + 关系演化」，禁止只堆「温柔可爱」「傲娇毒舌」等空泛词。
                - 必须包含可执行约束：不跳出设定、不自称 AI、不用与角色不符的口癖。
                - personality 与 promptTemplate 的语气必须一致，禁止 personality 写 gentle 却 prompt 写成 tsundere 嘴硬。

                ## 输出格式（严格 JSON，不要 markdown 代码块）
                {
                  "name": "角色名（官方中文名优先）",
                  "gender": "male 或 female",
                  "personality": "100-200字：一句高密度性格总结 + 外在语气 + 内在动机 + 行为边界（与原作一致）",
                  "speakingStyle": "40-80字：自称、对用户的称呼习惯、口癖、语速、情绪表达方式（要具体，不要模板）",
                  "backstory": "150-250字：原作出处、时间线定位、关键经历、与用户的关系定位（日常私聊语境，非剧透审判）",
                  "promptTemplate": "可执行的 System Prompt 模板，必须包含 {{name}} 和 {{persona}} 占位符。结构建议：\\n1) 你是谁、来自哪部作品\\n2) 你不能让任何人知道你是 AI\\n3) 与用户是手机私聊；[图片描述] 为识图客观结果，涉及「这是谁」必须依据它\\n4) {{persona}}\\n5) 回复规则：像真人微信聊天，可 1-3 条短消息（空行分隔），先接用户最后一句，禁止说教/首先其次/作为AI"
                }

                ## 最低质量门槛
                - ❌ 「温柔可爱，会叫你 Darling，保持角色口吻。」
                - ✅ 「按原作使用角色常用称呼；语气、价值观与关系推进均按该角色设定执行。」
                - promptTemplate 必须是可直接用于聊天的完整指令，不是 backstory 的重复粘贴。
                - 所有字段用中文（除 gender 用 male/female）。
                """;
    }

    private List<Map<String, String>> toApiMessages(List<Message> messages) {
        List<Map<String, String>> apiMessages = new ArrayList<>();
        for (Message message : messages) {
            String role;
            if (message instanceof SystemMessage) {
                role = "system";
            } else if (message instanceof UserMessage) {
                role = "user";
            } else if (message instanceof AssistantMessage) {
                role = "assistant";
            } else {
                continue;
            }
            String content = message.getText();
            if (content == null || content.isBlank()) {
                continue;
            }
            apiMessages.add(Map.of("role", role, "content", content));
        }
        return apiMessages;
    }

    // ---- private helpers ----

    private String callChatCompletionsWithWebSearch(
            List<Map<String, String>> messages, int maxTokens, double temperature) {
        if (chatApiKey == null || chatApiKey.isBlank()) {
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "未配置 AI API Key");
        }

        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", chatModel);
        body.put("messages", messages);
        body.put("temperature", temperature);
        body.put("max_tokens", maxTokens);
        body.put("enable_search", true);
        body.put("enable_thinking", false);
        body.put("search_options", Map.of("forced_search", true));

        try {
            String requestBody = objectMapper.writeValueAsString(body);
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(normalizeBaseUrl(chatBaseUrl) + "/v1/chat/completions"))
                    .timeout(Duration.ofSeconds(90))
                    .header("Authorization", "Bearer " + chatApiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(requestBody))
                    .build();
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("DashScope chat failed, status={}, body={}", response.statusCode(), response.body());
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode error = root.path("error");
            if (!error.isMissingNode() && !error.isNull()) {
                log.warn("DashScope chat error: {}", error);
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
            }

            JsonNode contentNode = root.path("choices").path(0).path("message").path("content");
            String content = contentNode.isMissingNode() ? "" : contentNode.asText();
            if (content.isBlank()) {
                throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR, "AI 返回内容为空");
            }
            return content;
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | InterruptedException e) {
            if (e instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            log.error("DashScope chat request failed", e);
            throw new BusinessException(ErrorCode.AI_PROVIDER_ERROR);
        }
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

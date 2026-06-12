package com.virtuallover.app.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.virtuallover.ai.AiChatService;
import com.virtuallover.app.MultiModalLoverApplication;
import com.virtuallover.common.base.ErrorCode;
import com.virtuallover.common.exception.BusinessException;
import com.virtuallover.storage.MinioStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.ai.chat.messages.Message;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import com.github.fppt.jedismock.RedisServer;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;

@SpringBootTest(classes = MultiModalLoverApplication.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
public abstract class AbstractApiIntegrationTest {

    protected static final String TOKEN_HEADER = "vlover-token";
    protected static final String PUBLIC_BASE = "http://localhost:9000";
    protected static final String BUCKET = "multimodal-lover";

    private static final RedisServer REDIS_SERVER;
    private static final int REDIS_PORT;

    static {
        try {
            REDIS_SERVER = RedisServer.newRedisServer();
            REDIS_SERVER.start();
            REDIS_PORT = REDIS_SERVER.getBindPort();
        } catch (IOException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    @DynamicPropertySource
    static void registerRedisPort(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", () -> "127.0.0.1");
        registry.add("spring.data.redis.port", () -> REDIS_PORT);
    }

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected ObjectMapper objectMapper;

    @MockBean
    protected MinioStorageService minioStorageService;

    @MockBean
    protected AiChatService aiChatService;

    @BeforeEach
    void setUpMocks() {
        when(minioStorageService.uploadAvatar(any(), anyLong(), anyString()))
                .thenAnswer(inv -> buildStorageUrl(inv.getArgument(2), inv.getArgument(1)));
        when(minioStorageService.uploadChatImage(any(), anyLong()))
                .thenAnswer(inv -> buildStorageUrl("chat-images", inv.getArgument(1)));
        when(minioStorageService.tryFetchImageBytes(anyString()))
                .thenReturn(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
        when(minioStorageService.fetchImageBytes(anyString()))
                .thenReturn(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0});
        when(minioStorageService.resolveTrustedImageUrl(anyString()))
                .thenAnswer(inv -> inv.getArgument(0));
        when(minioStorageService.guessContentType(anyString()))
                .thenReturn("image/jpeg");
        when(minioStorageService.toObjectKey(anyString()))
                .thenAnswer(inv -> extractObjectKey(inv.getArgument(0)));

        stubAvatarUrlValidation();

        when(aiChatService.generateCharacterPersona(anyString()))
                .thenReturn("""
                        {"name":"AI角色","gender":"female","personality":"活泼","speakingStyle":"口语","backstory":"背景","promptTemplate":"你是{{name}}"}
                        """);

        doAnswer(invocation -> {
            Consumer<String> onChunk = invocation.getArgument(1);
            Runnable onComplete = invocation.getArgument(2);
            onChunk.accept("test reply");
            onComplete.run();
            return null;
        }).when(aiChatService).streamChatCompletionSync(
                any(List.class), any(Consumer.class), any(Runnable.class), any(Consumer.class));
    }

    protected String uniqueName(String prefix) {
        return prefix + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    protected String registerUser(String username, String password, String nickname) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password,
                                "nickname", nickname != null ? nickname : username))))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        if (body.path("code").asInt() != 0) {
            throw new IllegalStateException("Register failed: " + body);
        }
        return body.path("data").path("token").asText();
    }

    protected String loginUser(String username, String password) throws Exception {
        MvcResult result = mockMvc.perform(MockMvcRequestBuilders.post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", password))))
                .andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        return body.path("data").path("token").asText();
    }

    protected MockMultipartFile jpegFile(String name) {
        return new MockMultipartFile(
                "file", name, "image/jpeg",
                new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10, 'J', 'F', 'I', 'F'});
    }

    protected JsonNode parseJson(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString());
    }

    protected MvcResult dispatchAsync(MvcResult asyncResult) throws Exception {
        return mockMvc.perform(asyncDispatch(asyncResult)).andReturn();
    }

    protected MvcResult startAsyncRequest(org.springframework.test.web.servlet.RequestBuilder request) throws Exception {
        return mockMvc.perform(request).andExpect(request().asyncStarted()).andReturn();
    }

    private void stubAvatarUrlValidation() {
        doAnswer(inv -> {
            Long userId = inv.getArgument(0);
            String avatarUrl = inv.getArgument(1);
            if (avatarUrl == null || avatarUrl.isBlank()) {
                return null;
            }
            if (!avatarUrl.contains("/user-avatars/" + userId + "/")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的头像地址");
            }
            return null;
        }).when(minioStorageService).validateUserAvatarUrl(anyLong(), any());

        doAnswer(inv -> {
            Long userId = inv.getArgument(0);
            String avatarUrl = inv.getArgument(1);
            if (avatarUrl == null || avatarUrl.isBlank()) {
                return null;
            }
            String trimmed = avatarUrl.trim();
            if (trimmed.startsWith("/avatars/") && !trimmed.contains("..")) {
                return null;
            }
            if (!trimmed.contains("/character-avatars/" + userId + "/")) {
                throw new BusinessException(ErrorCode.BAD_REQUEST, "无效的角色头像地址");
            }
            return null;
        }).when(minioStorageService).validateCharacterAvatarUrl(anyLong(), any());
    }

    private String buildStorageUrl(String category, Long userId) {
        return PUBLIC_BASE + "/" + BUCKET + "/" + category + "/" + userId + "/test.jpg";
    }

    private String extractObjectKey(String url) {
        if (url == null) {
            return "";
        }
        int idx = url.indexOf("chat-images/");
        if (idx >= 0) {
            return url.substring(idx);
        }
        idx = url.indexOf("user-avatars/");
        if (idx >= 0) {
            return url.substring(idx);
        }
        idx = url.indexOf("character-avatars/");
        if (idx >= 0) {
            return url.substring(idx);
        }
        return "chat-images/1/test.jpg";
    }
}

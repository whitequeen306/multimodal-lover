package com.virtuallover.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.virtuallover.app.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.asyncDispatch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.request;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Conversation API")
class ConversationApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    @DisplayName("POST / creates conversation for owned character")
    void createHappyPath() throws Exception {
        String token = registerUser(uniqueName("conv-create"), "pass123", "u");
        long characterId = firstCharacterId(token);

        mockMvc.perform(post("/api/conversation")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("characterId", characterId))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.characterId").value((int) characterId));
    }

    @Test
    @DisplayName("POST / returns 401 without token")
    void createUnauthorized() throws Exception {
        mockMvc.perform(post("/api/conversation")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("characterId", 1))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("GET / lists user conversations")
    void listHappyPath() throws Exception {
        String token = registerUser(uniqueName("conv-list"), "pass123", "u");
        long convId = createConversation(token);

        mockMvc.perform(get("/api/conversation").header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data[0].id").value((int) convId));
    }

    @Test
    @DisplayName("GET /{id} returns owned conversation")
    void getByIdHappyPath() throws Exception {
        String token = registerUser(uniqueName("conv-get"), "pass123", "u");
        long convId = createConversation(token);

        mockMvc.perform(get("/api/conversation/" + convId).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value((int) convId));
    }

    @Test
    @DisplayName("GET /{id} hides other user's conversation")
    void getByIdIdor() throws Exception {
        String tokenA = registerUser(uniqueName("conv-a"), "pass123", "a");
        String tokenB = registerUser(uniqueName("conv-b"), "pass123", "b");
        long convId = createConversation(tokenA);

        mockMvc.perform(get("/api/conversation/" + convId).header(TOKEN_HEADER, tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    @DisplayName("DELETE /{id} removes conversation")
    void deleteHappyPath() throws Exception {
        String token = registerUser(uniqueName("conv-del"), "pass123", "u");
        long convId = createConversation(token);

        mockMvc.perform(delete("/api/conversation/" + convId).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/conversation/" + convId).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(2002));
    }

    @Test
    @DisplayName("POST /upload-image returns imageUrl")
    void uploadImageHappyPath() throws Exception {
        String token = registerUser(uniqueName("conv-img"), "pass123", "u");

        mockMvc.perform(multipart("/api/conversation/upload-image").file(jpegFile("chat.jpg"))
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.imageUrl").value(containsString("chat-images")));
    }

    @Test
    @DisplayName("GET /image proxies image without auth")
    void proxyImageHappyPath() throws Exception {
        String imageUrl = PUBLIC_BASE + "/" + BUCKET + "/chat-images/1/test.jpg";

        mockMvc.perform(get("/api/conversation/image").param("url", imageUrl))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("image/jpeg")));
    }

    @Test
    @DisplayName("GET /image returns 404 when image missing")
    void proxyImageNotFound() throws Exception {
        org.mockito.Mockito.when(minioStorageService.tryFetchImageBytes(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(null);

        String imageUrl = PUBLIC_BASE + "/" + BUCKET + "/chat-images/1/missing.jpg";
        mockMvc.perform(get("/api/conversation/image").param("url", imageUrl))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /{id}/messages/stream returns SSE content type")
    void streamMessageStructure() throws Exception {
        String token = registerUser(uniqueName("conv-sse"), "pass123", "u");
        long convId = createConversation(token);

        MvcResult asyncResult = mockMvc.perform(post("/api/conversation/" + convId + "/messages/stream")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "你好"))))
                .andExpect(request().asyncStarted())
                .andReturn();

        mockMvc.perform(asyncDispatch(asyncResult))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/event-stream")));
    }

    @Test
    @DisplayName("GET /{id}/messages returns persisted messages after stream")
    void getMessagesAfterStream() throws Exception {
        String token = registerUser(uniqueName("conv-msgs"), "pass123", "u");
        long convId = createConversation(token);

        MvcResult asyncResult = mockMvc.perform(post("/api/conversation/" + convId + "/messages/stream")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("content", "hello stream"))))
                .andExpect(request().asyncStarted())
                .andReturn();
        mockMvc.perform(asyncDispatch(asyncResult)).andExpect(status().isOk());

        Thread.sleep(500);

        MvcResult messagesResult = mockMvc.perform(get("/api/conversation/" + convId + "/messages")
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();

        JsonNode messages = parseJson(messagesResult).path("data");
        assertThat(messages.isArray()).isTrue();
        assertThat(messages.size()).isGreaterThanOrEqualTo(2);

        boolean hasUser = false;
        boolean hasAssistant = false;
        for (JsonNode msg : messages) {
            if ("user".equals(msg.path("role").asText())) {
                hasUser = true;
                assertThat(msg.path("content").asText()).isEqualTo("hello stream");
            }
            if ("assistant".equals(msg.path("role").asText())) {
                hasAssistant = true;
                assertThat(msg.path("content").asText()).isNotBlank();
            }
        }
        assertThat(hasUser).isTrue();
        assertThat(hasAssistant).isTrue();
    }

    @Test
    @DisplayName("POST /{id}/messages/stream returns 400 for empty message")
    void streamValidationError() throws Exception {
        String token = registerUser(uniqueName("conv-val"), "pass123", "u");
        long convId = createConversation(token);

        mockMvc.perform(post("/api/conversation/" + convId + "/messages/stream")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("GET /{id}/messages returns 401 without token")
    void getMessagesUnauthorized() throws Exception {
        mockMvc.perform(get("/api/conversation/1/messages"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    private long firstCharacterId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/character").header(TOKEN_HEADER, token))
                .andReturn();
        return parseJson(result).path("data").get(0).path("id").asLong();
    }

    private long createConversation(String token) throws Exception {
        long characterId = firstCharacterId(token);
        MvcResult result = mockMvc.perform(post("/api/conversation")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("characterId", characterId))))
                .andReturn();
        JsonNode body = parseJson(result);
        assertThat(body.path("code").asInt()).isEqualTo(0);
        return body.path("data").path("id").asLong();
    }
}

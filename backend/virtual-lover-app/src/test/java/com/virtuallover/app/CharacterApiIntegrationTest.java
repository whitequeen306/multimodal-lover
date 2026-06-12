package com.virtuallover.app;

import com.fasterxml.jackson.databind.JsonNode;
import com.virtuallover.app.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Character API")
class CharacterApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    @DisplayName("GET / returns 401 without token")
    void listUnauthorized() throws Exception {
        mockMvc.perform(get("/api/character"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("GET / lists characters including builtin")
    void listHappyPath() throws Exception {
        String token = registerUser(uniqueName("char-list"), "pass123", "u");

        mockMvc.perform(get("/api/character").header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].name").value("江之岛盾子"))
                .andExpect(jsonPath("$.data[0].isBuiltin").value(1));
    }

    @Test
    @DisplayName("GET /{id} returns character owned by user")
    void getByIdHappyPath() throws Exception {
        String token = registerUser(uniqueName("char-get"), "pass123", "u");
        long characterId = firstCharacterId(token);

        mockMvc.perform(get("/api/character/" + characterId).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").value((int) characterId));
    }

    @Test
    @DisplayName("GET /{id} hides other user's character as not found")
    void getByIdIdor() throws Exception {
        String tokenA = registerUser(uniqueName("owner"), "pass123", "a");
        String tokenB = registerUser(uniqueName("other"), "pass123", "b");
        long characterId = firstCharacterId(tokenA);

        mockMvc.perform(get("/api/character/" + characterId).header(TOKEN_HEADER, tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("POST / creates custom character")
    void createHappyPath() throws Exception {
        String token = registerUser(uniqueName("char-create"), "pass123", "u");

        mockMvc.perform(post("/api/character")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCharacterBody("自定义角色"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("自定义角色"))
                .andExpect(jsonPath("$.data.isBuiltin").value(0));
    }

    @Test
    @DisplayName("POST / returns 400 when promptTemplate is missing")
    void createValidationError() throws Exception {
        String token = registerUser(uniqueName("char-val"), "pass123", "u");

        mockMvc.perform(post("/api/character")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("name", "无Prompt"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("PUT /{id} updates owned character")
    void updateHappyPath() throws Exception {
        String token = registerUser(uniqueName("char-upd"), "pass123", "u");
        long characterId = createCharacter(token, "待更新");

        mockMvc.perform(put("/api/character/" + characterId)
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCharacterBody("已更新"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("已更新"));
    }

    @Test
    @DisplayName("PUT /{id} returns 403 when updating another user's character")
    void updateIdorForbidden() throws Exception {
        String tokenA = registerUser(uniqueName("upd-a"), "pass123", "a");
        String tokenB = registerUser(uniqueName("upd-b"), "pass123", "b");
        long characterId = createCharacter(tokenA, "私有角色");

        mockMvc.perform(put("/api/character/" + characterId)
                        .header(TOKEN_HEADER, tokenB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCharacterBody("黑客"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("DELETE /{id} removes custom character")
    void deleteCustomCharacter() throws Exception {
        String token = registerUser(uniqueName("char-del"), "pass123", "u");
        long characterId = createCharacter(token, "可删除");

        mockMvc.perform(delete("/api/character/" + characterId).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/character/" + characterId).header(TOKEN_HEADER, token))
                .andExpect(jsonPath("$.code").value(2001));
    }

    @Test
    @DisplayName("DELETE /{id} rejects builtin character")
    void deleteBuiltinForbidden() throws Exception {
        String token = registerUser(uniqueName("char-builtin"), "pass123", "u");
        long builtinId = firstCharacterId(token);

        mockMvc.perform(delete("/api/character/" + builtinId).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("内置角色不能删除"));
    }

    @Test
    @DisplayName("DELETE /{id} returns 403 for another user's character")
    void deleteIdorForbidden() throws Exception {
        String tokenA = registerUser(uniqueName("del-a"), "pass123", "a");
        String tokenB = registerUser(uniqueName("del-b"), "pass123", "b");
        long characterId = createCharacter(tokenA, "不可删");

        mockMvc.perform(delete("/api/character/" + characterId).header(TOKEN_HEADER, tokenB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("POST / returns 400 when avatarUrl is invalid")
    void createInvalidAvatarUrl() throws Exception {
        String token = registerUser(uniqueName("char-avt-bad"), "pass123", "u");
        Map<String, Object> body = new java.util.HashMap<>(validCharacterBody("坏头像"));
        body.put("avatarUrl", "http://evil.com/avatar.jpg");

        mockMvc.perform(post("/api/character")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(body)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("无效的角色头像地址"));
    }

    @Test
    @DisplayName("POST /avatar-upload returns avatarUrl")
    void uploadAvatarHappyPath() throws Exception {
        String token = registerUser(uniqueName("char-avt"), "pass123", "u");

        mockMvc.perform(multipart("/api/character/avatar-upload").file(jpegFile("c.jpg"))
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.avatarUrl").value(org.hamcrest.Matchers.containsString("character-avatars")));
    }

    @Test
    @DisplayName("POST /generate returns parsed persona from mocked AI")
    void generateHappyPath() throws Exception {
        String token = registerUser(uniqueName("char-gen"), "pass123", "u");

        mockMvc.perform(post("/api/character/generate")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("characterName", "测试AI"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("AI角色"))
                .andExpect(jsonPath("$.data.personality").value("活泼"));
    }

    @Test
    @DisplayName("POST /generate returns 400 when characterName is blank")
    void generateValidationError() throws Exception {
        String token = registerUser(uniqueName("char-gen-val"), "pass123", "u");

        mockMvc.perform(post("/api/character/generate")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("characterName", ""))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    private Map<String, Object> validCharacterBody(String name) {
        return Map.of(
                "name", name,
                "gender", "female",
                "personality", "温柔",
                "speakingStyle", "口语",
                "backstory", "背景故事",
                "promptTemplate", "你是{{name}}，请保持人设。");
    }

    private long firstCharacterId(String token) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/character").header(TOKEN_HEADER, token))
                .andReturn();
        JsonNode data = parseJson(result).path("data");
        assertThat(data.isArray()).isTrue();
        assertThat(data.size()).isGreaterThan(0);
        return data.get(0).path("id").asLong();
    }

    private long createCharacter(String token, String name) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/character")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validCharacterBody(name))))
                .andReturn();
        JsonNode body = parseJson(result);
        assertThat(body.path("code").asInt()).isEqualTo(0);
        return body.path("data").path("id").asLong();
    }
}

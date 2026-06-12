package com.virtuallover.app;

import com.virtuallover.app.support.AbstractApiIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MvcResult;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Auth API")
class AuthApiIntegrationTest extends AbstractApiIntegrationTest {

    @Test
    @DisplayName("POST /register creates user and returns token")
    void registerHappyPath() throws Exception {
        String username = uniqueName("reg");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "password123",
                                "nickname", "测试用户"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    @DisplayName("POST /register rejects duplicate username with 400")
    void registerDuplicateUsername() throws Exception {
        String username = uniqueName("dup");
        registerUser(username, "password123", "nick");

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("用户名已存在"));
    }

    @Test
    @DisplayName("POST /register returns 400 when username is blank")
    void registerValidationError() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", "",
                                "password", "password123"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    @DisplayName("POST /login returns token for valid credentials")
    void loginHappyPath() throws Exception {
        String username = uniqueName("login");
        registerUser(username, "secret456", username);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "secret456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty());
    }

    @Test
    @DisplayName("POST /login returns 401 for wrong password")
    void loginWrongPassword() throws Exception {
        String username = uniqueName("badpw");
        registerUser(username, "correct", username);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "username", username,
                                "password", "wrong"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("GET /me returns 401 without token")
    void meUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("GET /me returns current user when authenticated")
    void meHappyPath() throws Exception {
        String username = uniqueName("me");
        String token = registerUser(username, "pass123", "昵称");

        mockMvc.perform(get("/api/auth/me").header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value(username))
                .andExpect(jsonPath("$.data.nickname").value("昵称"));
    }

    @Test
    @DisplayName("PUT /profile updates nickname")
    void updateProfileHappyPath() throws Exception {
        String username = uniqueName("profile");
        String token = registerUser(username, "pass123", "旧昵称");

        mockMvc.perform(put("/api/auth/profile")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nickname", "新昵称"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.nickname").value("新昵称"));
    }

    @Test
    @DisplayName("PUT /profile rejects invalid avatar URL")
    void updateProfileInvalidAvatar() throws Exception {
        String username = uniqueName("avatar-bad");
        String token = registerUser(username, "pass123", username);

        mockMvc.perform(put("/api/auth/profile")
                        .header(TOKEN_HEADER, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of(
                                "avatarUrl", "http://evil.com/avatar.jpg"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("无效的头像地址"));
    }

    @Test
    @DisplayName("PUT /profile returns 401 without token")
    void updateProfileUnauthorized() throws Exception {
        mockMvc.perform(put("/api/auth/profile")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(Map.of("nickname", "x"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("POST /avatar-upload returns avatarUrl when authenticated")
    void uploadAvatarHappyPath() throws Exception {
        String username = uniqueName("avt");
        String token = registerUser(username, "pass123", username);
        MockMultipartFile file = jpegFile("avatar.jpg");

        MvcResult result = mockMvc.perform(multipart("/api/auth/avatar-upload").file(file)
                        .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.avatarUrl").isNotEmpty())
                .andReturn();

        String avatarUrl = parseJson(result).path("data").path("avatarUrl").asText();
        assertThat(avatarUrl).contains("/user-avatars/");
    }

    @Test
    @DisplayName("POST /avatar-upload returns 401 without token")
    void uploadAvatarUnauthorized() throws Exception {
        mockMvc.perform(multipart("/api/auth/avatar-upload").file(jpegFile("a.jpg")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}

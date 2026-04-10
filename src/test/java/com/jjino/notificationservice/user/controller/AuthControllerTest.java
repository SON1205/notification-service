package com.jjino.notificationservice.user.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjino.notificationservice.user.controller.dto.LoginRequest;
import com.jjino.notificationservice.user.controller.dto.SignupRequest;
import com.jjino.notificationservice.user.service.AuthService;
import com.jjino.notificationservice.user.service.dto.LoginCommand;
import com.jjino.notificationservice.user.service.dto.SignupCommand;
import com.jjino.notificationservice.user.service.dto.SignupInfo;
import com.jjino.notificationservice.user.service.dto.TokenInfo;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private AuthService authService;

    @Nested
    @DisplayName("POST /api/v1/auth/signup")
    class Signup {

        @Test
        @DisplayName("정상 회원가입 시 201을 반환한다")
        void returns201OnSuccess() throws Exception {
            // given
            given(authService.signup(any(SignupCommand.class)))
                    .willReturn(new SignupInfo(1L, "testuser"));

            SignupRequest request = new SignupRequest("testuser", "password123");

            // when & then
            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.userId").value(1))
                    .andExpect(jsonPath("$.username").value("testuser"));
        }

        @Test
        @DisplayName("username이 3자 미만이면 400을 반환한다")
        void returns400ForShortUsername() throws Exception {
            SignupRequest request = new SignupRequest("ab", "password123");

            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }

        @Test
        @DisplayName("password가 8자 미만이면 400을 반환한다")
        void returns400ForShortPassword() throws Exception {
            SignupRequest request = new SignupRequest("testuser", "short");

            mockMvc.perform(post("/api/v1/auth/signup")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/login")
    class Login {

        @Test
        @DisplayName("정상 로그인 시 토큰을 반환한다")
        void returns200WithToken() throws Exception {
            // given
            given(authService.login(any(LoginCommand.class)))
                    .willReturn(new TokenInfo("jwt-token-value"));

            LoginRequest request = new LoginRequest("testuser", "password123");

            // when & then
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.token").value("jwt-token-value"));
        }

        @Test
        @DisplayName("로그인 시 Set-Cookie에 HttpOnly, Secure, SameSite=Lax, Path=/api가 포함된다")
        void loginResponseContainsCorrectCookieAttributes() throws Exception {
            // given
            given(authService.login(any(LoginCommand.class)))
                    .willReturn(new TokenInfo("jwt-token-value"));

            LoginRequest request = new LoginRequest("testuser", "password123");

            // when
            MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Set-Cookie"))
                    .andReturn();

            // then
            String setCookie = result.getResponse().getHeader("Set-Cookie");
            assertThat(setCookie).contains("access_token=jwt-token-value");
            assertThat(setCookie).containsIgnoringCase("HttpOnly");
            assertThat(setCookie).containsIgnoringCase("Secure");
            assertThat(setCookie).contains("SameSite=Lax");
            assertThat(setCookie).contains("Path=/api");
        }

        @Test
        @DisplayName("username이 빈값이면 400을 반환한다")
        void returns400ForBlankUsername() throws Exception {
            LoginRequest request = new LoginRequest("", "password123");

            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("POST /api/v1/auth/logout")
    class Logout {

        @Test
        @DisplayName("로그아웃 시 쿠키 삭제 Set-Cookie가 포함된다")
        void logoutDeletesCookie() throws Exception {
            // when
            MvcResult result = mockMvc.perform(post("/api/v1/auth/logout"))
                    .andExpect(status().isOk())
                    .andExpect(header().exists("Set-Cookie"))
                    .andReturn();

            // then
            String setCookie = result.getResponse().getHeader("Set-Cookie");
            assertThat(setCookie).contains("access_token=");
            assertThat(setCookie).contains("Max-Age=0");
            assertThat(setCookie).contains("Path=/api");
        }
    }
}

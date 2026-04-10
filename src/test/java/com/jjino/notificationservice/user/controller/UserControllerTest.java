package com.jjino.notificationservice.user.controller;

import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jjino.notificationservice.user.service.AuthService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class UserControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @Test
    @DisplayName("인증된 사용자의 탈퇴 요청 시 204를 반환한다")
    @WithMockUser
    void returns204OnWithdraw() throws Exception {
        // given
        willDoNothing().given(authService).withdraw(any());

        // when & then
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("인증 없이 탈퇴 요청 시 403을 반환한다")
    void returns403WithoutAuth() throws Exception {
        mockMvc.perform(delete("/api/v1/users/me"))
                .andExpect(status().isForbidden());
    }
}

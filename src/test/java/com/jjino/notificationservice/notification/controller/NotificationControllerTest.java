package com.jjino.notificationservice.notification.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willDoNothing;
import org.mockito.ArgumentCaptor;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jjino.notificationservice.notification.controller.dto.CreateNotificationRequest;
import com.jjino.notificationservice.notification.domain.NotificationType;
import com.jjino.notificationservice.notification.service.NotificationService;
import com.jjino.notificationservice.notification.service.SseEmitterService;
import com.jjino.notificationservice.notification.service.dto.CreateNotificationCommand;
import com.jjino.notificationservice.notification.service.dto.NotificationInfo;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class NotificationControllerTest {

    @Autowired
    private MockMvc mockMvc;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @MockitoBean
    private NotificationService notificationService;

    @MockitoBean
    private SseEmitterService sseEmitterService;

    private NotificationInfo createInfo(Long id) {
        return new NotificationInfo(
                id, NotificationType.SYSTEM, "제목", "내용", false, LocalDateTime.now()
        );
    }

    @Nested
    @DisplayName("POST /api/v1/notifications")
    class SendNotification {

        @Test
        @DisplayName("정상 생성 시 201을 반환한다")
        @WithMockUser
        void returns201OnSuccess() throws Exception {
            // given
            NotificationInfo info = createInfo(1L);
            ArgumentCaptor<CreateNotificationCommand> commandCaptor =
                    ArgumentCaptor.forClass(CreateNotificationCommand.class);
            given(notificationService.send(any(CreateNotificationCommand.class))).willReturn(info);

            CreateNotificationRequest request = new CreateNotificationRequest(
                    1L, NotificationType.SYSTEM, "제목", "내용"
            );

            // when & then
            mockMvc.perform(post("/api/v1/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.id").value(1))
                    .andExpect(jsonPath("$.type").value("SYSTEM"))
                    .andExpect(jsonPath("$.title").value("제목"));

            // 서비스에 전달된 command 검증
            then(notificationService).should().send(commandCaptor.capture());
            CreateNotificationCommand captured = commandCaptor.getValue();
            assertThat(captured.userId()).isEqualTo(1L);
            assertThat(captured.type()).isEqualTo(NotificationType.SYSTEM);
            assertThat(captured.title()).isEqualTo("제목");
            assertThat(captured.content()).isEqualTo("내용");
        }

        @Test
        @DisplayName("validation 실패 시 400을 반환한다")
        @WithMockUser
        void returns400OnValidationFailure() throws Exception {
            // given - title이 빈 문자열
            CreateNotificationRequest request = new CreateNotificationRequest(
                    1L, NotificationType.SYSTEM, "", "내용"
            );

            // when & then
            mockMvc.perform(post("/api/v1/notifications")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(request)))
                    .andExpect(status().isBadRequest());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/notifications")
    class GetAllNotifications {

        @Test
        @DisplayName("전체 알림 목록을 반환한다")
        @WithMockUser
        void returnsAllNotifications() throws Exception {
            // given
            given(notificationService.getAll(any())).willReturn(
                    List.of(createInfo(1L), createInfo(2L))
            );

            // when & then
            mockMvc.perform(get("/api/v1/notifications"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(2));
        }
    }

    @Nested
    @DisplayName("GET /api/v1/notifications/unread")
    class GetUnreadNotifications {

        @Test
        @DisplayName("안 읽은 알림 목록을 반환한다")
        @WithMockUser
        void returnsUnreadNotifications() throws Exception {
            // given
            given(notificationService.getUnread(any())).willReturn(
                    List.of(createInfo(1L))
            );

            // when & then
            mockMvc.perform(get("/api/v1/notifications/unread"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(1));
        }
    }

    @Nested
    @DisplayName("PATCH /api/v1/notifications/{id}/read")
    class MarkAsRead {

        @Test
        @DisplayName("읽음 처리 시 204를 반환한다")
        @WithMockUser
        void returns204OnSuccess() throws Exception {
            // given
            willDoNothing().given(notificationService).markAsRead(any(), eq(1L));

            // when & then
            mockMvc.perform(patch("/api/v1/notifications/1/read"))
                    .andExpect(status().isNoContent());
        }
    }

    @Nested
    @DisplayName("GET /api/v1/notifications/stream")
    class SseStream {

        @Test
        @DisplayName("SSE 스트림 엔드포인트가 text/event-stream으로 응답한다")
        @WithMockUser
        void returnsSseStreamWithCorrectContentType() throws Exception {
            // given
            given(sseEmitterService.subscribe(any())).willReturn(new SseEmitter());

            // when & then
            mockMvc.perform(get("/api/v1/notifications/stream")
                            .accept(MediaType.TEXT_EVENT_STREAM))
                    .andExpect(status().isOk())
                    .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_EVENT_STREAM));
        }
    }

    @Nested
    @DisplayName("인증 없이 접근")
    class Unauthorized {

        @Test
        @DisplayName("인증 없이 접근하면 403을 반환한다")
        void returnsForbiddenWithoutAuth() throws Exception {
            mockMvc.perform(get("/api/v1/notifications"))
                    .andExpect(status().isForbidden());
        }
    }
}

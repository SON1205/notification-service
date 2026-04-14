package com.jjino.notificationservice.global.auth;

import static com.jjino.notificationservice.global.common.Constants.ATTR_AUTH_ERROR;
import static com.jjino.notificationservice.global.common.Constants.MDC_REQUEST_ID;
import static com.jjino.notificationservice.global.error.ErrorCode.UNAUTHORIZED;

import com.jjino.notificationservice.global.error.ErrorCode;
import com.jjino.notificationservice.global.error.ErrorResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 인증 실패 시 응답을 생성하는 EntryPoint.
 *
 * Spring Security 흐름:
 * 1. JwtAuthenticationFilter에서 토큰 파싱 실패 → request attribute에 원인(ErrorCode) 저장 → 인증 없이 통과
 * 2. AuthorizationFilter에서 인증 없음 감지 → AccessDeniedException
 * 3. ExceptionTranslationFilter가 잡아서 → 이 EntryPoint의 commence() 호출
 * 4. request attribute에서 원인을 꺼내 원인별 응답 반환
 *
 * 이렇게 하면 서블릿 필터에서 예외를 던지지 않으면서도,
 * GlobalExceptionHandler와 동일한 ErrorResponse 형식으로 원인별 401 응답을 줄 수 있다.
 */
@Component
public class CustomAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper = JsonMapper.builder()
            .findAndAddModules()
            .build();

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        // JwtAuthenticationFilter에서 저장한 실패 원인. 없으면 토큰 자체가 없는 경우.
        ErrorCode errorCode = (ErrorCode) request.getAttribute(ATTR_AUTH_ERROR);
        if (errorCode == null) {
            errorCode = UNAUTHORIZED;
        }

        String requestId = MDC.get(MDC_REQUEST_ID);
        ErrorResponse errorResponse = ErrorResponse.of(errorCode, requestId);

        response.setStatus(errorCode.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}

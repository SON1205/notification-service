package com.jjino.notificationservice.global.interceptor;

import static java.nio.charset.StandardCharsets.UTF_8;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

@Slf4j
@Component
@Profile("dev")
public class DevLoggingInterceptor extends AbstractLoggingInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        log.info(formatRequestLog(request, handler));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
                                Exception ex) {
        logRequestBody(request);
        log.info("<<< {} {} {}ms", response.getStatus(), request.getRequestURI(), getDuration(request));
        logResponseBody(response);
    }

    private void logRequestBody(HttpServletRequest request) {
        ContentCachingRequestWrapper wrapper = unwrapRequest(request, ContentCachingRequestWrapper.class);
        if (wrapper != null) {
            byte[] body = wrapper.getContentAsByteArray();
            if (body.length > 0) {
                String content = new String(body, UTF_8);
                log.info(">>> Body: {}", maskSensitiveFields(truncate(content)));
            }
        }
    }

    private void logResponseBody(HttpServletResponse response) {
        ContentCachingResponseWrapper wrapper = unwrapResponse(response, ContentCachingResponseWrapper.class);
        if (wrapper != null) {
            byte[] body = wrapper.getContentAsByteArray();
            if (body.length > 0) {
                String content = new String(body, UTF_8);
                log.info("<<< Body: {}", maskSensitiveFields(truncate(content)));
            }
        }
    }
}

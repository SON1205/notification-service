package com.jjino.notificationservice.global.filter;

import static com.jjino.notificationservice.global.common.Constants.MAX_BODY_LOG_SIZE;
import static com.jjino.notificationservice.global.common.Constants.SSE_STREAM_PATH;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.context.annotation.Profile;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

/**
 * Wraps request/response for body caching. Runs after MdcFilter, before Security filter chain.
 */
@Component
@Profile("dev")
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class BodyCachingFilter extends OncePerRequestFilter {

    private static final int MAX_BODY_SIZE = MAX_BODY_LOG_SIZE;

    // ContentCachingResponseWrapper가 SSE 스트림을 버퍼링하면 connect 이벤트만 보내고 연결이 끊김
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().equals(SSE_STREAM_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        ContentCachingRequestWrapper requestWrapper = new ContentCachingRequestWrapper(request, MAX_BODY_SIZE);
        ContentCachingResponseWrapper responseWrapper = new ContentCachingResponseWrapper(response);

        try {
            filterChain.doFilter(requestWrapper, responseWrapper);
        } finally {
            responseWrapper.copyBodyToResponse();
        }
    }
}

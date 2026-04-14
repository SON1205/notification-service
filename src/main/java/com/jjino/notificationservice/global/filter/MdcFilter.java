package com.jjino.notificationservice.global.filter;

import static com.jjino.notificationservice.global.common.Constants.ATTR_START_TIME;
import static com.jjino.notificationservice.global.common.Constants.HEADER_REQUEST_ID;
import static com.jjino.notificationservice.global.common.Constants.MDC_REQUEST_ID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Runs before Security filter chain.
 * <p>
 * MDC(Mapped Diagnostic Context): 각 스레드마다 별도의 컨텍스트 정보를 유지하고 로그 메세지에 추가적인 정보를 포함시키는데 사용
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class MdcFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String requestId = UUID.randomUUID().toString().substring(0, 8);
        MDC.put(MDC_REQUEST_ID, requestId);
        response.addHeader(HEADER_REQUEST_ID, requestId);
        request.setAttribute(ATTR_START_TIME, System.currentTimeMillis());

        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.clear();
        }
    }
}

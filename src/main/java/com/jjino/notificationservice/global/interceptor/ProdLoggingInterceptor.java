package com.jjino.notificationservice.global.interceptor;

import com.jjino.notificationservice.global.common.Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Prod profile: logs only errors and slow requests.
 * - 2xx: no log
 * - 4xx: WARN
 * - 5xx: ERROR
 * - slow request (over 3s): WARN
 */
@Slf4j
@Component
@Profile("prod")
public class ProdLoggingInterceptor extends AbstractLoggingInterceptor {

    private static final long SLOW_REQUEST_THRESHOLD_MS = Constants.SLOW_REQUEST_THRESHOLD_MS;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        log.info(formatRequestLog(request, handler));
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        long duration = getDuration(request);
        int status = response.getStatus();
        String uri = request.getRequestURI();

        if (status >= 500) {
            log.error("<<< {} {} {}ms", status, uri, duration);
        } else if (status >= 400) {
            log.warn("<<< {} {} {}ms", status, uri, duration);
        } else if (duration >= SLOW_REQUEST_THRESHOLD_MS) {
            log.warn("<<< SLOW {} {} {}ms", status, uri, duration);
        }
    }
}

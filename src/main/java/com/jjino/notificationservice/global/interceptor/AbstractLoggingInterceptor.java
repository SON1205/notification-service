package com.jjino.notificationservice.global.interceptor;

import com.jjino.notificationservice.global.common.Constants;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletResponseWrapper;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.Map;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

public abstract class AbstractLoggingInterceptor implements HandlerInterceptor {

    protected static final int MAX_BODY_LOG_SIZE = Constants.MAX_BODY_LOG_SIZE;

    protected String formatRequestLog(HttpServletRequest request, Object handler) {
        if (handler instanceof HandlerMethod handlerMethod) {
            String controllerName = handlerMethod.getBeanType().getSimpleName();
            String methodName = handlerMethod.getMethod().getName();

            if (request.getParameterNames().hasMoreElements()) {
                return String.format(">>> %s %s %s -> %s.%s clientIp=%s",
                        request.getMethod(), request.getRequestURI(), getRequestParams(request),
                        controllerName, methodName, getClientIp(request));
            }
            return String.format(">>> %s %s -> %s.%s clientIp=%s",
                    request.getMethod(), request.getRequestURI(),
                    controllerName, methodName, getClientIp(request));
        }
        return String.format(">>> %s %s clientIp=%s",
                request.getMethod(), request.getRequestURI(), getClientIp(request));
    }

    protected long getDuration(HttpServletRequest request) {
        Long startTime = (Long) request.getAttribute(Constants.ATTR_START_TIME);
        return (startTime != null) ? System.currentTimeMillis() - startTime : -1;
    }

    protected String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader(Constants.HEADER_X_FORWARDED_FOR);
        if (ip == null || ip.isEmpty()) {
            ip = request.getRemoteAddr();
        }
        if ("0:0:0:0:0:0:0:1".equals(ip)) {
            ip = "127.0.0.1";
        }
        return ip;
    }

    protected Map<String, String> getRequestParams(HttpServletRequest request) {
        Map<String, String> paramMap = new HashMap<>();
        Enumeration<String> parameterNames = request.getParameterNames();
        while (parameterNames.hasMoreElements()) {
            String paramName = parameterNames.nextElement();
            paramMap.put(paramName, request.getParameter(paramName));
        }
        return paramMap;
    }

    protected String maskSensitiveFields(String content) {
        String masked = content;
        for (int i = 0; i < Constants.SENSITIVE_PATTERNS.size(); i++) {
            String replacement = String.format(Constants.MASK_REPLACEMENT_FORMAT, Constants.SENSITIVE_FIELDS.get(i));
            masked = Constants.SENSITIVE_PATTERNS.get(i).matcher(masked).replaceAll(replacement);
        }
        return masked;
    }

    protected String truncate(String content) {
        if (content.length() > MAX_BODY_LOG_SIZE) {
            return content.substring(0, MAX_BODY_LOG_SIZE) + "...(truncated)";
        }
        return content;
    }

    /**
     * Unwrap nested request wrappers (e.g. Security wrappers) to find the target type.
     */
    @SuppressWarnings("unchecked")
    protected <T> T unwrapRequest(HttpServletRequest request, Class<T> targetType) {
        HttpServletRequest current = request;
        while (current != null) {
            if (targetType.isInstance(current)) {
                return (T) current;
            }
            if (current instanceof HttpServletRequestWrapper wrapper) {
                current = (HttpServletRequest) wrapper.getRequest();
            } else {
                break;
            }
        }
        return null;
    }

    /**
     * Unwrap nested response wrappers to find the target type.
     */
    @SuppressWarnings("unchecked")
    protected <T> T unwrapResponse(HttpServletResponse response, Class<T> targetType) {
        HttpServletResponse current = response;
        while (current != null) {
            if (targetType.isInstance(current)) {
                return (T) current;
            }
            if (current instanceof HttpServletResponseWrapper wrapper) {
                current = (HttpServletResponse) wrapper.getResponse();
            } else {
                break;
            }
        }
        return null;
    }
}

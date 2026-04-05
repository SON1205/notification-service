package com.jjino.notificationservice.global.common;

import java.util.List;
import java.util.regex.Pattern;

public final class Constants {

    private Constants() {
    }

    // Profile
    public static final String PROFILE_DEV = "dev";
    public static final String PROFILE_PROD = "prod";

    // MDC
    public static final String MDC_REQUEST_ID = "requestId";

    // Header
    public static final String HEADER_REQUEST_ID = "X-Request-ID";
    public static final String HEADER_X_FORWARDED_FOR = "X-Forwarded-For";

    // Request Attribute
    public static final String ATTR_START_TIME = "startTime";

    // Logging
    public static final int MAX_BODY_LOG_SIZE = 10 * 1024;
    public static final long SLOW_REQUEST_THRESHOLD_MS = 3000;

    // Sensitive field masking
    public static final List<String> SENSITIVE_FIELDS = List.of(
            "password", "token", "secret", "authorization"
    );

    public static final List<Pattern> SENSITIVE_PATTERNS = SENSITIVE_FIELDS.stream()
            .map(field -> Pattern.compile("\"" + field + "\"\\s*:\\s*\"[^\"]*\"", Pattern.CASE_INSENSITIVE))
            .toList();

    public static final String MASK_REPLACEMENT_FORMAT = "\"%s\":\"***\"";
}

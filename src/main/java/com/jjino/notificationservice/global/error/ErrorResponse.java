package com.jjino.notificationservice.global.error;

import java.time.LocalDateTime;
import java.util.List;

public record ErrorResponse(
        String code,
        String message,
        String requestId,
        LocalDateTime timestamp,
        List<FieldError> fieldErrors,
        String debugMessage
) {

    public record FieldError(
            String field,
            String reason
    ) {
    }

    /**
     * General error response (BusinessException, etc.)
     */
    public static ErrorResponse of(ErrorCode errorCode, String requestId) {
        return new ErrorResponse(
                errorCode.name(),
                errorCode.getMessage(),
                requestId,
                LocalDateTime.now(),
                null,
                null
        );
    }

    /**
     * Validation error response with field-level details
     */
    public static ErrorResponse of(ErrorCode errorCode, String requestId, List<FieldError> fieldErrors) {
        return new ErrorResponse(
                errorCode.name(),
                errorCode.getMessage(),
                requestId,
                LocalDateTime.now(),
                fieldErrors,
                null
        );
    }

    /**
     * System/unhandled error response with debug info (dev profile only)
     */
    public static ErrorResponse of(ErrorCode errorCode, String requestId, String debugMessage) {
        return new ErrorResponse(
                errorCode.name(),
                errorCode.getMessage(),
                requestId,
                LocalDateTime.now(),
                null,
                debugMessage
        );
    }
}

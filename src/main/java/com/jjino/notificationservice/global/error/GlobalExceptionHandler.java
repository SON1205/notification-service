package com.jjino.notificationservice.global.error;

import static com.jjino.notificationservice.global.common.Constants.*;

import com.jjino.notificationservice.global.common.Constants;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @Value("${spring.profiles.active:" + PROFILE_DEV + "}")
    private String activeProfile;

    private String getRequestId() {
        return MDC.get(MDC_REQUEST_ID);
    }

    private boolean isDev() {
        return PROFILE_DEV.equals(activeProfile);
    }

    @ExceptionHandler(BusinessException.class)
    protected ResponseEntity<ErrorResponse> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        log.warn("BusinessException: errorCode={}, message={}", errorCode.name(), e.getMessage());
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, getRequestId()));
    }

    @ExceptionHandler(SystemException.class)
    protected ResponseEntity<ErrorResponse> handleSystemException(SystemException e) {
        ErrorCode errorCode = e.getErrorCode();
        logError("SystemException", errorCode, e);

        String debugMessage = isDev() ? e.getMessage() : null;
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(ErrorResponse.of(errorCode, getRequestId(), debugMessage));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    protected ResponseEntity<ErrorResponse> handleValidationException(MethodArgumentNotValidException e) {
        List<ErrorResponse.FieldError> fieldErrors = e.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(error -> new ErrorResponse.FieldError(
                        error.getField(),
                        error.getDefaultMessage()
                ))
                .toList();

        log.warn("ValidationException: fields={}", fieldErrors);
        return ResponseEntity
                .badRequest()
                .body(ErrorResponse.of(ErrorCode.INVALID_INPUT, getRequestId(), fieldErrors));
    }

    @ExceptionHandler(Exception.class)
    protected ResponseEntity<ErrorResponse> handleException(Exception e) {
        logError("UnhandledException", ErrorCode.INTERNAL_SERVER_ERROR, e);

        String debugMessage = isDev() ? e.getMessage() : null;
        return ResponseEntity
                .internalServerError()
                .body(ErrorResponse.of(ErrorCode.INTERNAL_SERVER_ERROR, getRequestId(), debugMessage));
    }

    /**
     * dev: full stack trace
     * prod: single line with root cause
     */
    private void logError(String label, ErrorCode errorCode, Exception e) {
        if (isDev()) {
            log.error("{}: errorCode={}, message={}", label, errorCode.name(), e.getMessage(), e);
        } else {
            Throwable rootCause = getRootCause(e);
            log.error("{}: errorCode={}, message={}, rootCause={}",
                    label, errorCode.name(), e.getMessage(),
                    rootCause.getClass().getSimpleName() + ": " + rootCause.getMessage());
        }
    }

    private Throwable getRootCause(Throwable e) {
        Throwable cause = e;
        while (cause.getCause() != null) {
            cause = cause.getCause();
        }
        return cause;
    }
}

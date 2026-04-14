package com.jjino.notificationservice.global.error;

import static com.jjino.notificationservice.global.common.Constants.MDC_REQUEST_ID;
import static com.jjino.notificationservice.global.common.Constants.PROFILE_DEV;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.async.AsyncRequestTimeoutException;

@Slf4j
@RequiredArgsConstructor
@RestControllerAdvice
public class GlobalExceptionHandler {

    private final Environment environment;

    private String getRequestId() {
        return MDC.get(MDC_REQUEST_ID);
    }

    // Environment.acceptsProfiles()는 복수 프로필(dev,local 등)에서도 정확히 동작.
    private boolean isDev() {
        return environment.matchesProfiles(PROFILE_DEV);
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

    // SSE 타임아웃은 정상 동작이므로 예외만 삼키고 로그를 남기지 않는다.
    // 주의: SSE는 chunked response라서 첫 chunk 전송 시점에 이미 200으로 응답이 커밋됨.
    // 따라서 여기서 반환하는 204는 실제로 클라이언트에 전달되지 않는다.
    // 이 핸들러의 역할은 예외가 handleException()으로 넘어가 불필요한 500 에러 로그가 찍히는 것을 방지하는 것.
    @ExceptionHandler(AsyncRequestTimeoutException.class)
    protected ResponseEntity<Void> handleAsyncTimeout(AsyncRequestTimeoutException e) {
        return ResponseEntity.noContent().build();
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
     * dev: full stack trace prod: single line with root cause
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

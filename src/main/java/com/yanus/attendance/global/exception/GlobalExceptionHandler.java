package com.yanus.attendance.global.exception;

import com.yanus.attendance.global.response.ApiResponse;
import com.yanus.attendance.global.logging.SafeLogThrowable;
import java.time.format.DateTimeParseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger LOG = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ApiResponse<Void>> handleBusinessException(BusinessException e) {
        ErrorCode errorCode = e.getErrorCode();
        var event = errorCode.getStatus().is5xxServerError() ? LOG.atError()
                : e.getCause() != null ? LOG.atWarn() : LOG.atInfo();
        event.addKeyValue("event", "application.rejected").addKeyValue("errorCode", errorCode.getCode());
        if (errorCode.getStatus().is5xxServerError() || e.getCause() != null) {
            event.setCause(SafeLogThrowable.from(e));
        }
        event.log("Application request rejected");
        return ResponseEntity
                .status(errorCode.getStatus())
                .body(new ApiResponse<>(errorCode.getCode(), errorCode.getMessage(), null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("유효성 검사 실패");
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(ErrorCode.BAD_REQUEST.getCode(), message, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleException(Exception e) {
        LOG.atError().addKeyValue("event", "application.failed")
                .addKeyValue("errorCode", ErrorCode.INTERNAL_ERROR.getCode())
                .setCause(SafeLogThrowable.from(e)).log("Unexpected application failure");
        return ResponseEntity
                .status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiResponse<>(
                        ErrorCode.INTERNAL_ERROR.getCode(),
                        ErrorCode.INTERNAL_ERROR.getMessage(),
                        null));
    }

    @ExceptionHandler(DateTimeParseException.class)
    public ResponseEntity<ApiResponse<Void>> handleDateTimeParseException(DateTimeParseException e) {
        return ResponseEntity
                .status(HttpStatus.BAD_REQUEST)
                .body(new ApiResponse<>(
                        ErrorCode.BAD_REQUEST.getCode(),
                        "yearMonth는 yyyy-MM 형식이어야 합니다",
                        null
                ));
    }
}

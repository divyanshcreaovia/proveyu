package com.proveyu.shared.error;

import com.proveyu.shared.response.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Object>> handleDomainException(DomainException ex) {
        log.warn("[BUSINESS RULE EXCEPTION] ErrorCode=[{}] Status=[{}] Message=[{}]",
                ex.getErrorCode(), ex.getStatus(), ex.getMessage());
        ApiResponse<Object> response = ApiResponse.error(ex.getMessage(), ex.getErrorCode());
        return ResponseEntity.status(ex.getStatus()).body(response);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Object>> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        log.warn("[CONCURRENCY EXCEPTION] Slot lock contention detected: {}", ex.getMessage());
        ApiResponse<Object> response = ApiResponse.error(
                "Conflict detected due to high concurrent reservations. Please retry your request.",
                "SLOT_CONTENDED_RETRY"
        );
        return ResponseEntity.status(HttpStatus.CONFLICT).body(response);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Map<String, String>>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error ->
                fieldErrors.put(error.getField(), error.getDefaultMessage())
        );

        log.warn("[VALIDATION EXCEPTION] Invalid request payload fields: {}", fieldErrors);

        ApiResponse<Map<String, String>> response = new ApiResponse<>(
                false,
                "Validation failed for request fields",
                fieldErrors,
                "VALIDATION_ERROR"
        );

        return ResponseEntity.badRequest().body(response);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
        log.error("[UNCAUGHT SYSTEM EXCEPTION] Unexpected error occurred: ", ex);
        String msg = ex.getMessage() != null ? ex.getMessage() : "An unexpected error occurred";
        ApiResponse<Object> response = ApiResponse.error(msg, "INTERNAL_SERVER_ERROR");
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(response);
    }
}

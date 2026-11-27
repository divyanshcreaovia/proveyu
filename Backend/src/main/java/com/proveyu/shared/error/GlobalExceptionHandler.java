package com.proveyu.shared.error;

import com.proveyu.shared.response.ApiResponse;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Every error leaves the API as {success:false, timestamp, status, code, errorCode, message, fieldErrors}.
 * Internal exception messages are never returned to the client for 5xx errors.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private ResponseEntity<ApiResponse<Object>> build(HttpStatus status, String message, String code, Map<String, String> fieldErrors) {
        return ResponseEntity.status(status).body(ApiResponse.error(status.value(), message, code, fieldErrors));
    }

    @ExceptionHandler(DomainException.class)
    public ResponseEntity<ApiResponse<Object>> handleDomainException(DomainException ex) {
        log.warn("[BUSINESS RULE EXCEPTION] ErrorCode=[{}] Status=[{}] Message=[{}]", ex.getErrorCode(), ex.getStatus(), ex.getMessage());
        return build(ex.getStatus(), ex.getMessage(), ex.getErrorCode(), null);
    }

    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Object>> handleOptimisticLockingFailure(ObjectOptimisticLockingFailureException ex) {
        log.warn("[CONCURRENCY EXCEPTION] {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "Conflict detected due to concurrent updates. Please retry your request.", "SLOT_CONTENDED_RETRY", null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Object>> handleValidationExceptions(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(error -> fieldErrors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        ex.getBindingResult().getGlobalErrors().forEach(error -> fieldErrors.putIfAbsent(error.getObjectName(), error.getDefaultMessage()));
        log.warn("[VALIDATION EXCEPTION] {}", fieldErrors);
        return build(HttpStatus.BAD_REQUEST, "Validation failed for request fields", "VALIDATION_ERROR", fieldErrors);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleConstraintViolation(ConstraintViolationException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getConstraintViolations().forEach(v -> fieldErrors.put(v.getPropertyPath().toString(), v.getMessage()));
        return build(HttpStatus.BAD_REQUEST, "Validation failed", "VALIDATION_ERROR", fieldErrors);
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ApiResponse<Object>> handleAccessDenied(Exception ex) {
        return build(HttpStatus.FORBIDDEN, "You do not have permission to perform this action", "FORBIDDEN", null);
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class,
            MissingServletRequestPartException.class, HttpMessageNotReadableException.class, IllegalArgumentException.class})
    public ResponseEntity<ApiResponse<Object>> handleBadRequest(Exception ex) {
        log.warn("[BAD REQUEST] {}", ex.getMessage());
        String msg = ex instanceof HttpMessageNotReadableException ? "Malformed request body" : ex.getMessage();
        return build(HttpStatus.BAD_REQUEST, msg, "BAD_REQUEST", null);
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ApiResponse<Object>> handleMaxUpload(MaxUploadSizeExceededException ex) {
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Uploaded file exceeds the maximum allowed size", "FILE_TOO_LARGE", null);
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ApiResponse<Object>> handleIntegrity(DataIntegrityViolationException ex) {
        log.warn("[DATA INTEGRITY] {}", ex.getMostSpecificCause().getMessage());
        return build(HttpStatus.CONFLICT, "The request conflicts with existing data (duplicate or referenced record)", "DATA_CONFLICT", null);
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMethod(HttpRequestMethodNotSupportedException ex) {
        return build(HttpStatus.METHOD_NOT_ALLOWED, ex.getMessage(), "METHOD_NOT_ALLOWED", null);
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ApiResponse<Object>> handleNoResource(NoResourceFoundException ex) {
        return build(HttpStatus.NOT_FOUND, "Resource not found", "NOT_FOUND", null);
    }

    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiResponse<Object>> handleMediaType(org.springframework.web.HttpMediaTypeNotSupportedException ex) {
        return build(HttpStatus.UNSUPPORTED_MEDIA_TYPE, ex.getMessage(), "UNSUPPORTED_MEDIA_TYPE", null);
    }

    @ExceptionHandler(org.springframework.dao.PessimisticLockingFailureException.class)
    public ResponseEntity<ApiResponse<Object>> handleLock(org.springframework.dao.PessimisticLockingFailureException ex) {
        log.warn("[LOCK CONTENTION] {}", ex.getMessage());
        return build(HttpStatus.CONFLICT, "Someone else is changing this right now. Please try again.", "BUSY_RETRY", null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Object>> handleGenericException(Exception ex) {
        log.error("[UNCAUGHT SYSTEM EXCEPTION] Unexpected error occurred: ", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred", "INTERNAL_SERVER_ERROR", null);
    }
}

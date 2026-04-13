package com.mali.smartbudget.exception;

import com.mali.smartbudget.exception.DuplicateStatementException;
import jakarta.persistence.EntityNotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(DuplicateStatementException.class)
    public ResponseEntity<Map<String, Object>> handleDuplicateStatement(DuplicateStatementException e) {
        log.warn("Mükerrer ekstre tespiti [{}]: {}", e.getDuplicateType(), e.getMessage());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp",      LocalDateTime.now().toString());
        body.put("status",         HttpStatus.CONFLICT.value());
        body.put("error",          "Conflict");
        body.put("message",        e.getMessage());
        body.put("duplicateType",  e.getDuplicateType() != null ? e.getDuplicateType().name() : null);
        body.put("periodStart",    e.getPeriodStart() != null ? e.getPeriodStart().toString() : null);
        body.put("periodEnd",      e.getPeriodEnd()   != null ? e.getPeriodEnd().toString()   : null);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(MissingServletRequestParameterException e) {
        log.warn("Eksik istek parametresi: {}", e.getParameterName());
        return build(HttpStatus.BAD_REQUEST, "Zorunlu parametre eksik: " + e.getParameterName());
    }

    @ExceptionHandler(EntityNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleEntityNotFound(EntityNotFoundException e) {
        log.error("EntityNotFoundException: {}", e.getMessage());
        return build(HttpStatus.NOT_FOUND, e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
        log.error("IllegalArgumentException: {}", e.getMessage(), e);
        return build(HttpStatus.UNPROCESSABLE_ENTITY, e.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
        log.error("Dosya boyutu limiti aşıldı: {}", e.getMessage());
        return build(HttpStatus.PAYLOAD_TOO_LARGE, "Dosya boyutu izin verilen limiti (2MB) aştı.");
    }

    @ExceptionHandler(java.io.IOException.class)
    public ResponseEntity<Map<String, Object>> handleIOException(java.io.IOException e) {
        log.error("IOException: {}", e.getMessage(), e);
        return build(HttpStatus.UNPROCESSABLE_ENTITY, "Dosya işlenemedi: " + e.getMessage());
    }

    /** Beklenmedik tüm hatalar — en az bu kadarını görmeliyiz. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleAll(Exception e) {
        log.error("Beklenmeyen hata [{}]: {}", e.getClass().getSimpleName(), e.getMessage(), e);
        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                e.getClass().getSimpleName() + ": " + e.getMessage());
    }

    private ResponseEntity<Map<String, Object>> build(HttpStatus status, String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", LocalDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("message", message);
        return ResponseEntity.status(status).body(body);
    }
}

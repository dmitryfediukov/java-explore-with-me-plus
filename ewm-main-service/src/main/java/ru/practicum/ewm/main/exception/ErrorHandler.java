package ru.practicum.ewm.main.exception;

import jakarta.validation.ConstraintViolationException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ErrorHandler {
    private static final DateTimeFormatter FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<Map<String, Object>> handleApi(ApiException e) {
        return response(e.getStatus(), e.getReason(), e.getMessage(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException e) {
        List<String> errors = e.getBindingResult().getFieldErrors().stream()
                .map(error -> error.getField() + ": " + error.getDefaultMessage()).toList();
        return response(HttpStatus.BAD_REQUEST, "Validation failed.", "Request validation failed", errors);
    }

    @ExceptionHandler({ ConstraintViolationException.class, HttpMessageNotReadableException.class,
            IllegalArgumentException.class })
    public ResponseEntity<Map<String, Object>> handleBadRequest(Exception e) {
        return response(HttpStatus.BAD_REQUEST, "Incorrectly made request.", e.getMessage(), List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConflict(DataIntegrityViolationException e) {
        return response(HttpStatus.CONFLICT, "Integrity constraint has been violated.",
                "A conflicting object already exists", List.of());
    }

    private ResponseEntity<Map<String, Object>> response(HttpStatus status, String reason, String message,
            List<String> errors) {
        return ResponseEntity.status(status)
                .body(Map.of("errors", errors, "message", message == null ? status.getReasonPhrase() : message,
                        "reason", reason, "status", status.name(), "timestamp", LocalDateTime.now().format(FORMAT)));
    }
}

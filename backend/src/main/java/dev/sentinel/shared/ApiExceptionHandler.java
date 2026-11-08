package dev.sentinel.shared;

import dev.sentinel.checking.DestinationPolicy.InvalidDestination;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.*;
import org.springframework.http.*;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.method.annotation.*;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import java.time.Instant;
import java.util.*;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);
    public record ApiError(Instant timestamp, int status, String code, String message, Map<String, String> errors) {}
    private ResponseEntity<ApiError> error(HttpStatus status, String code, String message, Map<String, String> errors) {
        return ResponseEntity.status(status).body(new ApiError(Instant.now(), status.value(), code, message, errors));
    }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiError> validation(MethodArgumentNotValidException e) {
        Map<String, String> fields = new TreeMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return error(HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", "Check the submitted fields", fields);
    }
    @ExceptionHandler(InvalidDestination.class)
    public ResponseEntity<ApiError> destination(InvalidDestination e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_DESTINATION", e.getMessage(), Map.of("url", e.getMessage()));
    }
    @ExceptionHandler({ConstraintViolationException.class, MethodArgumentTypeMismatchException.class,
            HttpMessageNotReadableException.class, HandlerMethodValidationException.class})
    public ResponseEntity<ApiError> malformed(Exception e) {
        return error(HttpStatus.BAD_REQUEST, "INVALID_REQUEST", "Invalid request or pagination parameters", Map.of());
    }
    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    public ResponseEntity<ApiError> missing(Exception e) {
        return error(HttpStatus.NOT_FOUND, "NOT_FOUND", "Resource not found", Map.of());
    }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ApiError> method(Exception e) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METHOD_NOT_ALLOWED", "Method not allowed", Map.of());
    }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<ApiError> contentType(Exception e) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "UNSUPPORTED_MEDIA_TYPE", "Use application/json for request bodies", Map.of());
    }
    @ExceptionHandler(org.springframework.web.HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<ApiError> accept(Exception e) {
        return error(HttpStatus.NOT_ACCEPTABLE, "NOT_ACCEPTABLE", "Only JSON responses are available", Map.of());
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> unexpected(Exception e) {
        log.error("Request failed ({})", e.getClass().getSimpleName());
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", "An unexpected server error occurred", Map.of());
    }
}

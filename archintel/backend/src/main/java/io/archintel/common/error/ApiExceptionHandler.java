package io.archintel.common.error;

import io.archintel.connector.http.ExternalApiException;
import io.archintel.connector.http.RateLimitExceededException;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class ApiExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> validation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) fields.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        return build(HttpStatus.BAD_REQUEST, "Validation failed", fields);
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    ResponseEntity<ApiError> malformed(Exception ex) { return build(HttpStatus.BAD_REQUEST, "Malformed request", null); }

    @ExceptionHandler(BadRequestException.class)
    ResponseEntity<ApiError> badRequest(BadRequestException ex) { return build(HttpStatus.BAD_REQUEST, ex.getMessage(), null); }

    @ExceptionHandler(InvalidCredentialsException.class)
    ResponseEntity<ApiError> credentials(InvalidCredentialsException ex) { return build(HttpStatus.UNAUTHORIZED, ex.getMessage(), null); }

    @ExceptionHandler({NotFoundException.class, NoResourceFoundException.class})
    ResponseEntity<ApiError> notFound(Exception ex) {
        return build(HttpStatus.NOT_FOUND, ex instanceof NotFoundException ? ex.getMessage() : "Not found", null);
    }

    @ExceptionHandler(ConflictException.class)
    ResponseEntity<ApiError> conflict(ConflictException ex) { return build(HttpStatus.CONFLICT, ex.getMessage(), null); }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<ApiError> method(HttpRequestMethodNotSupportedException ex) { return build(HttpStatus.METHOD_NOT_ALLOWED, "Method not allowed", null); }

    @ExceptionHandler(RateLimitExceededException.class)
    ResponseEntity<ApiError> rateLimited(RateLimitExceededException ex) {
        return build(HttpStatus.SERVICE_UNAVAILABLE, "An upstream rate limit was hit; retry after " + ex.resetAt(), null);
    }

    @ExceptionHandler(ExternalApiException.class)
    ResponseEntity<ApiError> upstream(ExternalApiException ex) {
        log.warn("Upstream failure: {}", ex.getMessage());
        return build(HttpStatus.BAD_GATEWAY, "An upstream service failed", null);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> unexpected(Exception ex) {
        log.error("Unhandled error", ex);
        return build(HttpStatus.INTERNAL_SERVER_ERROR, "Unexpected error", null); // never leak internals
    }

    private static ResponseEntity<ApiError> build(HttpStatus s, String msg, Map<String, String> fields) {
        return ResponseEntity.status(s).body(new ApiError(Instant.now(), s.value(), s.getReasonPhrase(), msg, fields));
    }
}

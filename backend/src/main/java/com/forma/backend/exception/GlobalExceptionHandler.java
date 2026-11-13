package com.forma.backend.exception;

import com.forma.backend.exception.DomainExceptions.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(UnauthorizedAccessException.class)
    public ResponseEntity<Map<String, Object>> handleUnauthorized(UnauthorizedAccessException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), Map.of("authenticated", false));
    }

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(InvalidCredentialsException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(AccountDeletedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountDeleted(AccountDeletedException ex) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), Map.of("accountDeleted", true));
    }

    @ExceptionHandler(AccountPausedException.class)
    public ResponseEntity<Map<String, Object>> handleAccountPaused(AccountPausedException ex) {
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), Map.of("accountPaused", true));
    }

    @ExceptionHandler(AccountAlreadyExistsException.class)
    public ResponseEntity<Map<String, Object>> handleAccountAlreadyExists(AccountAlreadyExistsException ex) {
        Map<String, Object> extra = new LinkedHashMap<>();
        extra.put("accountExists", true);
        if (ex.getEmail() != null) {
            extra.put("email", ex.getEmail());
        }
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), extra);
    }

    @ExceptionHandler(AiBlockedException.class)
    public ResponseEntity<Map<String, Object>> handleAiBlocked(AiBlockedException ex) {
        if (ex.isGlobalBlock()) {
            return buildResponse(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage(), Map.of("blocked", true));
        }
        return buildResponse(HttpStatus.FORBIDDEN, ex.getMessage(), Map.of("aiBlockedForUser", true));
    }

    @ExceptionHandler(AiConsentRequiredException.class)
    public ResponseEntity<Map<String, Object>> handleAiConsentRequired(AiConsentRequiredException ex) {
        return buildResponse(
            HttpStatus.FORBIDDEN,
            ex.getMessage(),
            Map.of("aiBlockedForUser", true, "aiConsentRequired", true)
        );
    }

    @ExceptionHandler(AiQuotaExceededException.class)
    public ResponseEntity<Map<String, Object>> handleAiQuotaExceeded(AiQuotaExceededException ex) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), Map.of("quotaExceeded", true));
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<Map<String, Object>> handleRateLimit(RateLimitExceededException ex) {
        return buildResponse(HttpStatus.TOO_MANY_REQUESTS, ex.getMessage(), Map.of("rateLimited", true));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNotFound(ResourceNotFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(AiUpstreamException.class)
    public ResponseEntity<Map<String, Object>> handleAiUpstream(AiUpstreamException ex) {
        return buildResponse(HttpStatus.BAD_GATEWAY, ex.getMessage(), Map.of("aiTemporaryError", true));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleBadRequest(IllegalArgumentException ex) {
        return buildResponse(HttpStatus.BAD_REQUEST, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalState(IllegalStateException ex) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), Map.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(NoResourceFoundException ex) {
        return buildResponse(HttpStatus.NOT_FOUND, "Recurso ou endpoint não encontrado.", Map.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        log.error("[HTTP 500] Erro interno não tratado: {}", ex.getMessage(), ex);
        return buildResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "Erro interno inesperado no servidor Forma. Tente novamente em instantes.",
            Map.of("internalError", true)
        );
    }

    private ResponseEntity<Map<String, Object>> buildResponse(
        HttpStatus status,
        String errorMessage,
        Map<String, Object> extraFields
    ) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", errorMessage);
        body.put("message", errorMessage);
        body.putAll(extraFields);
        body.put("timestamp", Instant.now().toString());
        return ResponseEntity.status(status).body(body);
    }
}

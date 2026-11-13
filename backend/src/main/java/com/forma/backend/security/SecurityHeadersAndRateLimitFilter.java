package com.forma.backend.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Aplica cabeçalhos HTTP de segurança em todas as respostas (OWASP / LGPD Art. 46)
 * e rate limiting em janela deslizante por IP nas rotas críticas de autenticação, admin e IA.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 10)
public class SecurityHeadersAndRateLimitFilter extends OncePerRequestFilter {

    private final boolean rateLimitEnabled;
    private final int authMaxRequestsPerMinute;
    private final int aiMaxRequestsPerMinute;
    private final Map<String, Deque<Long>> requestBuckets = new ConcurrentHashMap<>();

    public SecurityHeadersAndRateLimitFilter(
        @Value("${app.security.rate-limit.enabled:true}") boolean rateLimitEnabled,
        @Value("${app.security.rate-limit.auth-per-minute:20}") int authMaxRequestsPerMinute,
        @Value("${app.security.rate-limit.ai-per-minute:30}") int aiMaxRequestsPerMinute
    ) {
        this.rateLimitEnabled = rateLimitEnabled;
        this.authMaxRequestsPerMinute = authMaxRequestsPerMinute;
        this.aiMaxRequestsPerMinute = aiMaxRequestsPerMinute;
    }

    @Override
    protected void doFilterInternal(
        HttpServletRequest request,
        HttpServletResponse response,
        FilterChain filterChain
    ) throws ServletException, IOException {
        applySecurityHeaders(request, response);

        String path = request.getRequestURI();
        if (rateLimitEnabled && path != null && !"OPTIONS".equalsIgnoreCase(request.getMethod())) {
            int limit = resolveRateLimitForPath(path);
            if (limit > 0) {
                String bucketKey = extractClientIp(request) + "|" + bucketGroup(path);
                if (!tryAcquire(bucketKey, limit)) {
                    response.setStatus(429);
                    response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                    response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                    String body = "{\"error\":\"Muitas tentativas em sequência. Aguarde 1 minuto.\",\"message\":\"Muitas tentativas em sequência. Aguarde 1 minuto.\",\"rateLimited\":true,\"timestamp\":\""
                        + Instant.now() + "\"}";
                    response.getWriter().write(body);
                    return;
                }
            }
        }

        filterChain.doFilter(request, response);
    }

    private void applySecurityHeaders(HttpServletRequest request, HttpServletResponse response) {
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("X-Frame-Options", "DENY");
        response.setHeader("Referrer-Policy", "strict-origin-when-cross-origin");
        response.setHeader("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()");
        response.setHeader("Strict-Transport-Security", "max-age=31536000; includeSubDomains; preload");

        String path = request.getRequestURI();
        if (path != null && path.startsWith("/api/")) {
            response.setHeader("Cache-Control", "no-store, no-cache, must-revalidate, private");
            response.setHeader("Pragma", "no-cache");
        } else {
            response.setHeader(
                "Content-Security-Policy",
                "default-src 'self'; "
                    + "script-src 'self' 'unsafe-inline'; "
                    + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; "
                    + "font-src 'self' https://fonts.gstatic.com data:; "
                    + "img-src 'self' data: https:; "
                    + "connect-src 'self' wss: ws: https:; "
                    + "frame-ancestors 'none'; "
                    + "base-uri 'self'"
            );
        }
    }

    private int resolveRateLimitForPath(String path) {
        if (path.equals("/api/auth/login")
            || path.equals("/api/auth/register")
            || path.equals("/api/auth/forgot-password")
            || path.equals("/api/auth/reset-password")
            || path.equals("/api/admin/login")) {
            return authMaxRequestsPerMinute;
        }
        if (path.startsWith("/api/ai/")) {
            return aiMaxRequestsPerMinute;
        }
        return -1;
    }

    private String bucketGroup(String path) {
        if (path.startsWith("/api/ai/")) return "AI";
        return path;
    }

    private boolean tryAcquire(String key, int maxPerMinute) {
        long now = System.currentTimeMillis();
        long cutoff = now - 60_000L;
        Deque<Long> timestamps = requestBuckets.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (timestamps) {
            while (!timestamps.isEmpty() && timestamps.peekFirst() < cutoff) {
                timestamps.pollFirst();
            }
            if (timestamps.size() >= maxPerMinute) {
                return false;
            }
            timestamps.addLast(now);
            return true;
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) return first;
        }
        String remote = request.getRemoteAddr();
        return remote != null ? remote : "unknown";
    }
}

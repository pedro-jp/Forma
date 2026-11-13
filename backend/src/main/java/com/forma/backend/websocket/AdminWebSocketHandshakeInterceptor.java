package com.forma.backend.websocket;

import com.forma.backend.security.AuthContextResolver;
import com.forma.backend.service.JwtService;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.http.server.ServletServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Garante que apenas operadores administrativos autenticados via Cookie HttpOnly ou JWT Admin
 * consigam estabelecer conexão WebSocket em /ws/admin.
 */
@Component
public class AdminWebSocketHandshakeInterceptor implements HandshakeInterceptor {

    public static final String ATTR_ADMIN_AUTHENTICATED = "formaAdminAuthenticated";
    private final JwtService jwtService;

    public AdminWebSocketHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Map<String, Object> attributes
    ) {
        String token = extractAdminToken(request);
        if (token != null && jwtService.validateAdminToken(token)) {
            attributes.put(ATTR_ADMIN_AUTHENTICATED, Boolean.TRUE);
            return true;
        }
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        return false;
    }

    @Override
    public void afterHandshake(
        ServerHttpRequest request,
        ServerHttpResponse response,
        WebSocketHandler wsHandler,
        Exception exception
    ) {}

    private String extractAdminToken(ServerHttpRequest request) {
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String bearer = authHeader.substring(7).trim();
            if (!bearer.isEmpty()) return bearer;
        }

        if (request instanceof ServletServerHttpRequest servletReq) {
            HttpServletRequest httpReq = servletReq.getServletRequest();
            if (httpReq.getCookies() != null) {
                for (Cookie c : httpReq.getCookies()) {
                    if (AuthContextResolver.ADMIN_COOKIE_NAME.equals(c.getName())
                        && c.getValue() != null && !c.getValue().isBlank()) {
                        return c.getValue().trim();
                    }
                }
            }
        }

        URI uri = request.getURI();
        if (uri != null && uri.getRawQuery() != null) {
            for (String param : uri.getRawQuery().split("&")) {
                int eq = param.indexOf('=');
                if (eq > 0 && "token".equals(param.substring(0, eq))) {
                    String val = URLDecoder.decode(param.substring(eq + 1), StandardCharsets.UTF_8).trim();
                    if (!val.isEmpty()) return val;
                }
            }
        }
        return null;
    }
}

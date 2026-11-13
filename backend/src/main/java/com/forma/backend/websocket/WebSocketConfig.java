package com.forma.backend.websocket;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

import java.util.Arrays;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AdminWebSocketHandler adminWebSocketHandler;
    private final AdminWebSocketHandshakeInterceptor adminHandshakeInterceptor;
    private final UserAccountWebSocketHandler userAccountWebSocketHandler;
    private final String[] allowedOrigins;

    public WebSocketConfig(
        AdminWebSocketHandler adminWebSocketHandler,
        AdminWebSocketHandshakeInterceptor adminHandshakeInterceptor,
        UserAccountWebSocketHandler userAccountWebSocketHandler,
        @Value("${app.cors.allowed-origins:https://forma-94vp.onrender.com,http://localhost:5173,http://localhost:8080,http://localhost:8085}") String allowedOriginsCsv
    ) {
        this.adminWebSocketHandler = adminWebSocketHandler;
        this.adminHandshakeInterceptor = adminHandshakeInterceptor;
        this.userAccountWebSocketHandler = userAccountWebSocketHandler;
        this.allowedOrigins = Arrays.stream(allowedOriginsCsv.split(","))
            .map(String::trim)
            .filter(s -> !s.isEmpty())
            .toArray(String[]::new);
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(adminWebSocketHandler, "/ws/admin")
            .addInterceptors(adminHandshakeInterceptor)
            .setAllowedOriginPatterns(allowedOrigins);
        registry.addHandler(userAccountWebSocketHandler, "/ws/account")
            .setAllowedOriginPatterns("*");
    }
}

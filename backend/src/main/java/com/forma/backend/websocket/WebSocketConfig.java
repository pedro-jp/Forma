package com.forma.backend.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final AdminWebSocketHandler adminWebSocketHandler;
    private final UserAccountWebSocketHandler userAccountWebSocketHandler;

    public WebSocketConfig(
        AdminWebSocketHandler adminWebSocketHandler,
        UserAccountWebSocketHandler userAccountWebSocketHandler
    ) {
        this.adminWebSocketHandler = adminWebSocketHandler;
        this.userAccountWebSocketHandler = userAccountWebSocketHandler;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(adminWebSocketHandler, "/ws/admin")
            .setAllowedOrigins("*");
        registry.addHandler(userAccountWebSocketHandler, "/ws/account")
            .setAllowedOrigins("*");
    }
}

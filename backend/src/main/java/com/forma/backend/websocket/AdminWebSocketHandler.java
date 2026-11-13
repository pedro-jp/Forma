package com.forma.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class AdminWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(AdminWebSocketHandler.class);

    private final Set<WebSocketSession> activeSessions = ConcurrentHashMap.newKeySet();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        if (!Boolean.TRUE.equals(session.getAttributes().get(AdminWebSocketHandshakeInterceptor.ATTR_ADMIN_AUTHENTICATED))) {
            try {
                session.close(CloseStatus.POLICY_VIOLATION);
            } catch (IOException ignored) {}
            return;
        }

        activeSessions.add(session);
        log.info("[WebSocket Admin] Nova conexão autenticada estabelecida: session={}", session.getId());

        try {
            String welcomePayload = objectMapper.writeValueAsString(Map.of(
                "type", "CONNECTED",
                "timestamp", Instant.now().toString(),
                "message", "Conectado ao canal WebSocket autenticado de telemetria do Forma",
                "sessionId", session.getId()
            ));
            session.sendMessage(new TextMessage(welcomePayload));
        } catch (IOException e) {
            log.warn("[WebSocket Admin] Falha ao enviar mensagem de boas-vindas: {}", e.getMessage());
        }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        activeSessions.remove(session);
        log.info("[WebSocket Admin] Conexão encerrada: session={}, status={}", session.getId(), status);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        log.warn("[WebSocket Admin] Erro no transporte: session={}, error={}", session.getId(), exception.getMessage());
        try {
            session.close(CloseStatus.SERVER_ERROR);
        } catch (IOException ignored) {}
        activeSessions.remove(session);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        String payload = message.getPayload();
        if ("ping".equalsIgnoreCase(payload.trim())) {
            try {
                session.sendMessage(new TextMessage("pong"));
            } catch (IOException ignored) {}
        }
    }

    public void broadcast(String eventType, Object data) {
        if (activeSessions.isEmpty()) return;

        try {
            Map<String, Object> messageMap = Map.of(
                "type", eventType,
                "timestamp", Instant.now().toString(),
                "data", data
            );
            String json = objectMapper.writeValueAsString(messageMap);
            TextMessage textMessage = new TextMessage(json);

            for (WebSocketSession session : activeSessions) {
                if (session.isOpen()) {
                    synchronized (session) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            log.warn("[WebSocket Admin] Falha ao enviar evento para {}: {}", session.getId(), e.getMessage());
                        }
                    }
                }
            }
        } catch (Exception e) {
            log.error("[WebSocket Admin] Erro ao serializar broadcast: {}", e.getMessage());
        }
    }

    public int getConnectedCount() {
        return activeSessions.size();
    }
}

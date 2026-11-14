package com.forma.backend.websocket;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forma.backend.entity.User;
import com.forma.backend.repository.DeletedAccountTombstoneRepository;
import com.forma.backend.repository.UserRepository;
import com.forma.backend.service.JwtService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class UserAccountWebSocketHandler extends TextWebSocketHandler {

    private static final Logger log = LoggerFactory.getLogger(UserAccountWebSocketHandler.class);
    private static final String ATTR_USER_ID = "authenticatedUserId";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final DeletedAccountTombstoneRepository tombstoneRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<UUID, Set<WebSocketSession>> sessionsByUserId = new ConcurrentHashMap<>();

    public UserAccountWebSocketHandler(JwtService jwtService, UserRepository userRepository) {
        this(jwtService, userRepository, null);
    }

    @Autowired
    public UserAccountWebSocketHandler(
        JwtService jwtService,
        UserRepository userRepository,
        DeletedAccountTombstoneRepository tombstoneRepository
    ) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.tombstoneRepository = tombstoneRepository;
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
        String token = extractJwtToken(session);
        if (token == null || !jwtService.validateToken(token)) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        UUID tokenUserId;
        int tokenVersion;
        try {
            tokenUserId = jwtService.getUserIdFromToken(token);
            tokenVersion = jwtService.extractTokenVersion(token);
        } catch (Exception e) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        if (tombstoneRepository != null && tombstoneRepository.existsById(tokenUserId.toString())) {
            sendDirectJson(session, Map.of(
                "type", "ACCOUNT_DELETED",
                "timestamp", Instant.now().toString()
            ));
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        Optional<User> userOpt = userRepository.findById(tokenUserId);
        if (userOpt.isEmpty()) {
            sendDirectJson(session, Map.of(
                "type", "ACCOUNT_DELETED",
                "timestamp", Instant.now().toString()
            ));
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        User user = userOpt.get();
        if (user.getTokenVersion() != tokenVersion) {
            closeQuietly(session, CloseStatus.POLICY_VIOLATION);
            return;
        }

        UUID effectiveUserId = user.getId();
        session.getAttributes().put(ATTR_USER_ID, effectiveUserId);
        sessionsByUserId
            .computeIfAbsent(effectiveUserId, k -> ConcurrentHashMap.newKeySet())
            .add(session);

        log.info("[WebSocket Account] Usuário conectado em tempo real: userId={}, tier={}, session={}",
            effectiveUserId, user.getAccountTier(), session.getId());

        sendDirectJson(session, buildStatePayload(user, "CONNECTED"));
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus status) {
        removeSession(session);
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) {
        removeSession(session);
        closeQuietly(session, CloseStatus.SERVER_ERROR);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message) {
        if ("ping".equalsIgnoreCase(message.getPayload().trim())) {
            synchronized (session) {
                try {
                    if (session.isOpen()) {
                        session.sendMessage(new TextMessage("pong"));
                    }
                } catch (IOException ignored) {
                }
            }
        }
    }

    public void sendAccountStateToUser(User user, String eventName) {
        sendSubscriptionStateToUser(user, eventName, null, null, null, null);
    }

    public void sendSubscriptionStateToUser(
        User user,
        String eventName,
        String subscriptionStatus,
        Integer overdueDay,
        String graceEndsAtUtc,
        String warningMessage
    ) {
        if (user == null || user.getId() == null) return;
        Set<WebSocketSession> sessions = sessionsByUserId.get(user.getId());
        if (sessions == null || sessions.isEmpty()) return;

        Map<String, Object> payload = buildStatePayload(user, eventName);
        if (subscriptionStatus != null && !subscriptionStatus.isBlank()) {
            payload.put("subscriptionStatus", subscriptionStatus);
        }
        if (overdueDay != null) {
            payload.put("overdueDay", overdueDay);
        }
        if (graceEndsAtUtc != null && !graceEndsAtUtc.isBlank()) {
            payload.put("graceEndsAtUtc", graceEndsAtUtc);
        }
        if (warningMessage != null && !warningMessage.isBlank()) {
            payload.put("warningMessage", warningMessage);
        }

        String json;
        try {
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[WebSocket Account] Erro ao serializar estado para {}: {}", user.getId(), e.getMessage());
            return;
        }

        TextMessage textMessage = new TextMessage(json);
        for (WebSocketSession session : sessions) {
            if (session.isOpen()) {
                synchronized (session) {
                    try {
                        session.sendMessage(textMessage);
                    } catch (IOException e) {
                        log.warn("[WebSocket Account] Falha ao enviar push para session={}: {}", session.getId(), e.getMessage());
                    }
                }
            }
        }
    }

    public void broadcastGlobalAiState(boolean aiBlocked) {
        if (sessionsByUserId.isEmpty()) return;

        String json;
        try {
            Map<String, Object> payload = Map.of(
                "type", "GLOBAL_AI_STATE",
                "aiBlocked", aiBlocked,
                "timestamp", Instant.now().toString()
            );
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[WebSocket Account] Erro ao serializar GLOBAL_AI_STATE: {}", e.getMessage());
            return;
        }

        TextMessage textMessage = new TextMessage(json);
        for (Set<WebSocketSession> userSessions : sessionsByUserId.values()) {
            for (WebSocketSession session : userSessions) {
                if (session.isOpen()) {
                    synchronized (session) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            log.warn("[WebSocket Account] Falha ao enviar GLOBAL_AI_STATE para session={}: {}", session.getId(), e.getMessage());
                        }
                    }
                }
            }
        }
    }

    public void broadcastCatalogUpdated(String reason) {
        if (sessionsByUserId.isEmpty()) return;

        String json;
        try {
            Map<String, Object> payload = Map.of(
                "type", "BILLING_CATALOG_UPDATED",
                "reason", reason != null ? reason : "UPDATED",
                "timestamp", Instant.now().toString()
            );
            json = objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            log.warn("[WebSocket Account] Erro ao serializar BILLING_CATALOG_UPDATED: {}", e.getMessage());
            return;
        }

        TextMessage textMessage = new TextMessage(json);
        for (Set<WebSocketSession> userSessions : sessionsByUserId.values()) {
            for (WebSocketSession session : userSessions) {
                if (session.isOpen()) {
                    synchronized (session) {
                        try {
                            session.sendMessage(textMessage);
                        } catch (IOException e) {
                            log.warn("[WebSocket Account] Falha ao enviar catálogo atualizado para session={}: {}", session.getId(), e.getMessage());
                        }
                    }
                }
            }
        }
    }

    public void sendAccountDeletedToUser(UUID userId) {
        if (userId == null) return;
        Set<WebSocketSession> sessions = sessionsByUserId.remove(userId);
        if (sessions == null || sessions.isEmpty()) return;

        Map<String, Object> payload = Map.of(
            "type", "ACCOUNT_DELETED",
            "userId", userId.toString(),
            "timestamp", Instant.now().toString()
        );

        for (WebSocketSession session : sessions) {
            sendDirectJson(session, payload);
            closeQuietly(session, CloseStatus.NORMAL);
        }
    }

    public void disconnectUser(UUID userId) {
        if (userId == null) return;
        Set<WebSocketSession> sessions = sessionsByUserId.remove(userId);
        if (sessions == null || sessions.isEmpty()) return;
        for (WebSocketSession session : sessions) {
            closeQuietly(session, CloseStatus.NORMAL);
        }
    }

    private Map<String, Object> buildStatePayload(User user, String eventName) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("type", "ACCOUNT_STATE");
        payload.put("event", eventName != null ? eventName : "UPDATED");
        payload.put("userId", user.getId().toString());
        payload.put("accountTier", user.getAccountTier());
        payload.put("status", user.getStatus() != null ? user.getStatus() : "ACTIVE");
        payload.put("pausedReason", user.getPausedReason() != null ? user.getPausedReason() : "");
        payload.put("aiBlocked", user.isAiBlocked());
        payload.put("aiConsentGranted", user.isAiConsentGranted());
        payload.put("timestamp", Instant.now().toString());
        return payload;
    }

    private void sendDirectJson(WebSocketSession session, Map<String, Object> payload) {
        if (session == null || !session.isOpen()) return;
        synchronized (session) {
            try {
                String json = objectMapper.writeValueAsString(payload);
                session.sendMessage(new TextMessage(json));
            } catch (IOException ignored) {
            }
        }
    }

    private void removeSession(WebSocketSession session) {
        Object rawId = session.getAttributes().get(ATTR_USER_ID);
        if (rawId instanceof UUID userId) {
            Set<WebSocketSession> set = sessionsByUserId.get(userId);
            if (set != null) {
                set.remove(session);
                if (set.isEmpty()) {
                    sessionsByUserId.remove(userId, set);
                }
            }
        }
    }

    private void closeQuietly(WebSocketSession session, CloseStatus status) {
        try {
            if (session != null && session.isOpen()) {
                session.close(status);
            }
        } catch (IOException ignored) {
        }
    }

    private String extractJwtToken(WebSocketSession session) {
        String authHeader = session.getHandshakeHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String bearer = authHeader.substring(7).trim();
            if (!bearer.isEmpty()) {
                return bearer;
            }
        }

        URI uri = session.getUri();
        if (uri != null && uri.getRawQuery() != null) {
            for (String param : uri.getRawQuery().split("&")) {
                int eq = param.indexOf('=');
                if (eq > 0) {
                    String key = param.substring(0, eq);
                    if ("token".equals(key)) {
                        String val = URLDecoder.decode(param.substring(eq + 1), StandardCharsets.UTF_8).trim();
                        if (!val.isEmpty()) {
                            return val;
                        }
                    }
                }
            }
        }
        return null;
    }
}

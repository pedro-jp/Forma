package com.forma.backend.websocket;

import com.forma.backend.entity.AiRequestLogEntity;
import com.forma.backend.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.Map;
import java.util.UUID;

@Service
public class AdminEventPublisherService {

    private final AdminWebSocketHandler webSocketHandler;
    private final UserAccountWebSocketHandler userAccountWebSocketHandler;

    public AdminEventPublisherService(AdminWebSocketHandler webSocketHandler) {
        this(webSocketHandler, null);
    }

    @Autowired
    public AdminEventPublisherService(
        AdminWebSocketHandler webSocketHandler,
        UserAccountWebSocketHandler userAccountWebSocketHandler
    ) {
        this.webSocketHandler = webSocketHandler;
        this.userAccountWebSocketHandler = userAccountWebSocketHandler;
    }

    public void publishAiRequest(AiRequestLogEntity log, long totalAiRequests) {
        webSocketHandler.broadcast("AI_REQUEST", Map.of(
            "id", log.getId(),
            "userId", log.getUserId(),
            "endpoint", log.getEndpoint(),
            "promptSummary", log.getPromptSummary(),
            "status", log.getStatus(),
            "durationMs", log.getDurationMs(),
            "createdAt", log.getCreatedAt().toString(),
            "totalAiRequests", totalAiRequests
        ));
    }

    public void publishUserActivity(String event, String email, String name, String authProvider, long totalUsers) {
        webSocketHandler.broadcast("USER_ACTIVITY", Map.of(
            "event", event,
            "email", email,
            "name", name != null ? name : "Atleta",
            "authProvider", authProvider != null ? authProvider : "EMAIL",
            "totalUsers", totalUsers
        ));
    }

    public void publishSyncActivity(String entityType, int count, long totalFoods, long totalWorkouts) {
        webSocketHandler.broadcast("SYNC_ACTIVITY", Map.of(
            "entityType", entityType,
            "count", count,
            "totalFoods", totalFoods,
            "totalWorkouts", totalWorkouts
        ));
    }

    public void publishKillSwitchState(boolean isBlocked) {
        webSocketHandler.broadcast("KILL_SWITCH_CHANGED", Map.of(
            "isBlocked", isBlocked,
            "message", isBlocked ? "⛔️ Requisições à API de IA foram BLOQUEADAS" : "🟢 Requisições à API de IA foram LIBERADAS"
        ));
    }

    public void publishConfigUpdated(String keyMasked, String model) {
        webSocketHandler.broadcast("CONFIG_UPDATED", Map.of(
            "activeApiKeyMasked", keyMasked,
            "geminiModel", model
        ));
    }

    public void publishUserAccountState(User user, String eventName) {
        if (userAccountWebSocketHandler == null || user == null) return;
        runAfterCommitOrNow(() -> userAccountWebSocketHandler.sendAccountStateToUser(user, eventName));
    }

    public void publishUserAccountDeleted(UUID userId) {
        if (userAccountWebSocketHandler == null || userId == null) return;
        runAfterCommitOrNow(() -> userAccountWebSocketHandler.sendAccountDeletedToUser(userId));
    }

    private void runAfterCommitOrNow(Runnable action) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    action.run();
                }
            });
        } else {
            action.run();
        }
    }
}

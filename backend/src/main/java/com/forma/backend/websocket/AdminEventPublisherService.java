package com.forma.backend.websocket;

import com.forma.backend.entity.AdminAuditLogEntity;
import com.forma.backend.entity.AiRequestLogEntity;
import com.forma.backend.entity.SecurityIncidentLogEntity;
import com.forma.backend.entity.User;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Instant;
import java.util.LinkedHashMap;
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
        if (log == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", log.getId());
        payload.put("userId", log.getUserId());
        payload.put("endpoint", log.getEndpoint());
        payload.put("promptSummary", "[Solicitação autenticada — conteúdo protegido pela LGPD]");
        payload.put("status", log.getStatus());
        payload.put("durationMs", log.getDurationMs());
        payload.put("createdAt", (log.getCreatedAt() != null ? log.getCreatedAt() : Instant.now()).toString());
        payload.put("totalAiRequests", totalAiRequests);
        runAfterCommitOrNow(() -> {
            if (webSocketHandler != null) webSocketHandler.broadcast("AI_REQUEST", payload);
        });
    }

    public void publishUserActivity(String event, String email, String name, String authProvider, long totalUsers) {
        Map<String, Object> payload = Map.of(
            "event", event != null ? event : "UPDATED",
            "email", maskEmail(email),
            "name", maskName(name),
            "authProvider", authProvider != null ? authProvider : "EMAIL",
            "totalUsers", totalUsers
        );
        runAfterCommitOrNow(() -> {
            if (webSocketHandler != null) webSocketHandler.broadcast("USER_ACTIVITY", payload);
        });
    }

    public void publishSyncActivity(String entityType, int count, long totalFoods, long totalWorkouts) {
        Map<String, Object> payload = Map.of(
            "entityType", entityType != null ? entityType : "SYNC",
            "count", count,
            "totalFoods", totalFoods,
            "totalWorkouts", totalWorkouts
        );
        runAfterCommitOrNow(() -> {
            if (webSocketHandler != null) webSocketHandler.broadcast("SYNC_ACTIVITY", payload);
        });
    }

    public void publishKillSwitchState(boolean isBlocked) {
        Map<String, Object> payload = Map.of(
            "isBlocked", isBlocked,
            "message", isBlocked ? "Requisições à API de IA foram BLOQUEADAS" : "Requisições à API de IA foram LIBERADAS"
        );
        runAfterCommitOrNow(() -> {
            if (webSocketHandler != null) {
                webSocketHandler.broadcast("KILL_SWITCH_CHANGED", payload);
            }
            if (userAccountWebSocketHandler != null) {
                userAccountWebSocketHandler.broadcastGlobalAiState(isBlocked);
            }
        });
    }

    public void publishConfigUpdated(String keyMasked, String model) {
        Map<String, Object> payload = Map.of(
            "activeApiKeyMasked", keyMasked != null ? keyMasked : "Não configurada",
            "geminiModel", model != null ? model : ""
        );
        runAfterCommitOrNow(() -> {
            if (webSocketHandler != null) webSocketHandler.broadcast("CONFIG_UPDATED", payload);
        });
    }

    public void publishAdminAuditLog(AdminAuditLogEntity audit) {
        if (audit == null || webSocketHandler == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", audit.getId());
        payload.put("adminActor", audit.getAdminActor());
        payload.put("actionType", audit.getActionType());
        payload.put("targetRef", audit.getTargetRef() != null ? audit.getTargetRef() : "");
        payload.put("detailSummary", audit.getDetailSummary() != null ? audit.getDetailSummary() : "");
        payload.put("createdAtUtc", (audit.getCreatedAtUtc() != null ? audit.getCreatedAtUtc() : Instant.now()).toString());
        runAfterCommitOrNow(() -> webSocketHandler.broadcast("AUDIT_LOG_CREATED", payload));
    }

    public void publishSecurityIncident(SecurityIncidentLogEntity inc) {
        if (inc == null || webSocketHandler == null) return;
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("id", inc.getId());
        payload.put("severity", inc.getSeverity());
        payload.put("incidentType", inc.getIncidentType());
        payload.put("affectedCategories", inc.getAffectedCategories() != null ? inc.getAffectedCategories() : "");
        payload.put("description", inc.getDescription());
        payload.put("mitigationActions", inc.getMitigationActions() != null ? inc.getMitigationActions() : "");
        payload.put("anpdNotified", inc.isAnpdNotified());
        payload.put("detectedAtUtc", (inc.getDetectedAtUtc() != null ? inc.getDetectedAtUtc() : Instant.now()).toString());
        runAfterCommitOrNow(() -> webSocketHandler.broadcast("SECURITY_INCIDENT_CREATED", payload));
    }

    public void publishUserAccountState(User user, String eventName) {
        if (userAccountWebSocketHandler == null || user == null) return;
        runAfterCommitOrNow(() -> userAccountWebSocketHandler.sendAccountStateToUser(user, eventName));
    }

    public void publishUserSubscriptionState(
        User user,
        String eventName,
        String subscriptionStatus,
        Integer overdueDay,
        String graceEndsAtUtc,
        String warningMessage
    ) {
        if (userAccountWebSocketHandler == null || user == null) return;
        runAfterCommitOrNow(() -> userAccountWebSocketHandler.sendSubscriptionStateToUser(
            user,
            eventName,
            subscriptionStatus,
            overdueDay,
            graceEndsAtUtc,
            warningMessage
        ));
    }

    public void publishBillingUpdated(String reason) {
        runAfterCommitOrNow(() -> {
            String effectiveReason = reason != null ? reason : "UPDATED";
            if (webSocketHandler != null) {
                webSocketHandler.broadcast("BILLING_UPDATED", Map.of(
                    "reason", effectiveReason,
                    "timestamp", java.time.Instant.now().toString()
                ));
            }
            if (userAccountWebSocketHandler != null) {
                userAccountWebSocketHandler.broadcastCatalogUpdated(effectiveReason);
            }
        });
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

    private static String maskEmail(String email) {
        if (email == null || email.isBlank()) return "***";
        int at = email.indexOf('@');
        if (at <= 1) return "***" + (at >= 0 ? email.substring(at) : "");
        return email.charAt(0) + "***" + email.substring(at);
    }

    private static String maskName(String name) {
        if (name == null || name.isBlank()) return "Atleta";
        String trimmed = name.trim();
        return trimmed.charAt(0) + "***";
    }
}

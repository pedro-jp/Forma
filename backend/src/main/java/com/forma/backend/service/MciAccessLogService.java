package com.forma.backend.service;

import com.forma.backend.entity.AdminAuditLogEntity;
import com.forma.backend.entity.MciAccessLogEntity;
import com.forma.backend.repository.AdminAuditLogRepository;
import com.forma.backend.repository.MciAccessLogRepository;
import com.forma.backend.security.FieldEncryptionService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * Registra guarda legal de acesso à aplicação conforme Marco Civil da Internet (Lei 12.965/2014, Art. 15)
 * por exatos 180 dias (com IP criptografado AES-256-GCM e porta de origem, sem histórico de navegação/saúde)
 * e trilha administrativa LGPD Art. 37.
 */
@Service
public class MciAccessLogService {

    private static final Logger log = LoggerFactory.getLogger(MciAccessLogService.class);
    private static final Duration MCI_RETENTION_PERIOD = Duration.ofDays(180);

    private final MciAccessLogRepository mciAccessLogRepository;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final FieldEncryptionService fieldEncryptionService;

    public MciAccessLogService(
        MciAccessLogRepository mciAccessLogRepository,
        AdminAuditLogRepository adminAuditLogRepository,
        FieldEncryptionService fieldEncryptionService
    ) {
        this.mciAccessLogRepository = mciAccessLogRepository;
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.fieldEncryptionService = fieldEncryptionService;
    }

    public void recordAccessEvent(String accountRef, String eventType, HttpServletRequest request) {
        try {
            String safeRef = (accountRef != null && !accountRef.isBlank()) ? accountRef.trim().toLowerCase() : "anonymous";
            String accountHash = fieldEncryptionService.hmacSha256Hex(safeRef);
            String clientIp = extractClientIp(request);
            Integer sourcePort = extractSourcePort(request);
            Instant now = Instant.now();

            MciAccessLogEntity entry = new MciAccessLogEntity(
                accountHash,
                eventType != null ? eventType : "AUTH_EVENT",
                clientIp,
                sourcePort,
                now,
                now.plus(MCI_RETENTION_PERIOD)
            );
            mciAccessLogRepository.saveAndFlush(entry);
        } catch (Exception e) {
            log.warn("[MCI] Falha ao gravar registro de acesso (evento={}): {}", eventType, e.getMessage());
        }
    }

    public void recordAdminAudit(String actor, String actionType, UUID targetUserId, String summary, HttpServletRequest request) {
        recordAdminAudit(actor, actionType, targetUserId != null ? targetUserId.toString() : null, summary, request);
    }

    public void recordAdminAudit(String actor, String actionType, String targetRef, String summary, HttpServletRequest request) {
        try {
            String safeActor = (actor != null && !actor.isBlank()) ? actor.trim() : "admin";
            String safeTarget = targetRef != null ? fieldEncryptionService.hmacSha256Hex(targetRef).substring(0, 24) : null;
            String safeSummary = summary != null && summary.length() > 480 ? summary.substring(0, 480) : summary;
            AdminAuditLogEntity audit = new AdminAuditLogEntity(
                safeActor,
                actionType,
                safeTarget,
                safeSummary,
                extractClientIp(request)
            );
            adminAuditLogRepository.saveAndFlush(audit);
        } catch (Exception e) {
            log.warn("[AdminAudit] Falha ao gravar auditoria administrativa: {}", e.getMessage());
        }
    }

    @Scheduled(cron = "0 15 3 * * *")
    @Transactional
    public void purgeExpiredMciAccessLogs() {
        int removed = mciAccessLogRepository.deleteExpiredLogs(Instant.now());
        if (removed > 0) {
            log.info("[MCI] Expurgo automático diário concluído: {} registros acima de 180 dias removidos.", removed);
        }
    }

    public String extractClientIp(HttpServletRequest request) {
        if (request == null) return "0.0.0.0";
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            String first = xff.split(",")[0].trim();
            if (!first.isEmpty()) return first;
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        String remote = request.getRemoteAddr();
        return (remote != null && !remote.isBlank()) ? remote : "0.0.0.0";
    }

    private Integer extractSourcePort(HttpServletRequest request) {
        if (request == null) return null;
        String xForwardedPort = request.getHeader("X-Forwarded-Port");
        if (xForwardedPort != null && !xForwardedPort.isBlank()) {
            try {
                return Integer.parseInt(xForwardedPort.trim());
            } catch (NumberFormatException ignored) {
            }
        }
        int port = request.getRemotePort();
        return port > 0 ? port : null;
    }
}

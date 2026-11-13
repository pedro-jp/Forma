package com.forma.backend.entity;

import com.forma.backend.security.EncryptedStringAttributeConverter;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "admin_audit_logs")
public class AdminAuditLogEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id = UUID.randomUUID().toString();

    @Column(name = "admin_actor", nullable = false, length = 80)
    private String adminActor;

    @Column(name = "action_type", nullable = false, length = 80)
    private String actionType;

    @Column(name = "target_ref", length = 128)
    private String targetRef;

    @Column(name = "detail_summary", length = 500)
    private String detailSummary;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "ip_address_encrypted", columnDefinition = "TEXT")
    private String ipAddressEncrypted;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc = Instant.now();

    public AdminAuditLogEntity() {}

    public AdminAuditLogEntity(
        String adminActor,
        String actionType,
        String targetRef,
        String detailSummary,
        String ipAddressEncrypted
    ) {
        this.id = UUID.randomUUID().toString();
        this.adminActor = adminActor;
        this.actionType = actionType;
        this.targetRef = targetRef;
        this.detailSummary = detailSummary;
        this.ipAddressEncrypted = ipAddressEncrypted;
        this.createdAtUtc = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAdminActor() { return adminActor; }
    public void setAdminActor(String adminActor) { this.adminActor = adminActor; }

    public String getActionType() { return actionType; }
    public void setActionType(String actionType) { this.actionType = actionType; }

    public String getTargetRef() { return targetRef; }
    public void setTargetRef(String targetRef) { this.targetRef = targetRef; }

    public String getDetailSummary() { return detailSummary; }
    public void setDetailSummary(String detailSummary) { this.detailSummary = detailSummary; }

    public String getIpAddressEncrypted() { return ipAddressEncrypted; }
    public void setIpAddressEncrypted(String ipAddressEncrypted) { this.ipAddressEncrypted = ipAddressEncrypted; }

    public Instant getCreatedAtUtc() { return createdAtUtc; }
    public void setCreatedAtUtc(Instant createdAtUtc) { this.createdAtUtc = createdAtUtc; }
}

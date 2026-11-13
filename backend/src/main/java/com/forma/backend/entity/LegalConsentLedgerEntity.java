package com.forma.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "legal_consent_ledger")
public class LegalConsentLedgerEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id = UUID.randomUUID().toString();

    @Column(name = "user_id", nullable = false, length = 128)
    private String userId;

    @Column(name = "consent_type", nullable = false, length = 64)
    private String consentType;

    @Column(name = "granted", nullable = false)
    private boolean granted;

    @Column(name = "document_version", nullable = false, length = 40)
    private String documentVersion;

    @Column(name = "document_sha256", nullable = false, length = 128)
    private String documentSha256;

    @Column(name = "source_platform", length = 40)
    private String sourcePlatform;

    @Column(name = "ip_hash", length = 128)
    private String ipHash;

    @Column(name = "recorded_at_utc", nullable = false)
    private Instant recordedAtUtc = Instant.now();

    public LegalConsentLedgerEntity() {}

    public LegalConsentLedgerEntity(
        String userId,
        String consentType,
        boolean granted,
        String documentVersion,
        String documentSha256,
        String sourcePlatform,
        String ipHash
    ) {
        this.id = UUID.randomUUID().toString();
        this.userId = userId;
        this.consentType = consentType;
        this.granted = granted;
        this.documentVersion = documentVersion;
        this.documentSha256 = documentSha256;
        this.sourcePlatform = sourcePlatform;
        this.ipHash = ipHash;
        this.recordedAtUtc = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public String getConsentType() { return consentType; }
    public void setConsentType(String consentType) { this.consentType = consentType; }

    public boolean isGranted() { return granted; }
    public void setGranted(boolean granted) { this.granted = granted; }

    public String getDocumentVersion() { return documentVersion; }
    public void setDocumentVersion(String documentVersion) { this.documentVersion = documentVersion; }

    public String getDocumentSha256() { return documentSha256; }
    public void setDocumentSha256(String documentSha256) { this.documentSha256 = documentSha256; }

    public String getSourcePlatform() { return sourcePlatform; }
    public void setSourcePlatform(String sourcePlatform) { this.sourcePlatform = sourcePlatform; }

    public String getIpHash() { return ipHash; }
    public void setIpHash(String ipHash) { this.ipHash = ipHash; }

    public Instant getRecordedAtUtc() { return recordedAtUtc; }
    public void setRecordedAtUtc(Instant recordedAtUtc) { this.recordedAtUtc = recordedAtUtc; }
}

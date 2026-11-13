package com.forma.backend.entity;

import com.forma.backend.security.EncryptedStringAttributeConverter;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "mci_access_logs")
public class MciAccessLogEntity {

    @Id
    @Column(name = "id", length = 64)
    private String id = UUID.randomUUID().toString();

    @Column(name = "account_ref_hash", nullable = false, length = 128)
    private String accountRefHash;

    @Column(name = "event_type", nullable = false, length = 48)
    private String eventType;

    @Convert(converter = EncryptedStringAttributeConverter.class)
    @Column(name = "ip_address_encrypted", nullable = false, columnDefinition = "TEXT")
    private String ipAddressEncrypted;

    @Column(name = "source_port")
    private Integer sourcePort;

    @Column(name = "accessed_at_utc", nullable = false)
    private Instant accessedAtUtc = Instant.now();

    @Column(name = "retain_until_utc", nullable = false)
    private Instant retainUntilUtc;

    public MciAccessLogEntity() {}

    public MciAccessLogEntity(
        String accountRefHash,
        String eventType,
        String ipAddressEncrypted,
        Integer sourcePort,
        Instant accessedAtUtc,
        Instant retainUntilUtc
    ) {
        this.id = UUID.randomUUID().toString();
        this.accountRefHash = accountRefHash;
        this.eventType = eventType;
        this.ipAddressEncrypted = ipAddressEncrypted;
        this.sourcePort = sourcePort;
        this.accessedAtUtc = accessedAtUtc;
        this.retainUntilUtc = retainUntilUtc;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getAccountRefHash() { return accountRefHash; }
    public void setAccountRefHash(String accountRefHash) { this.accountRefHash = accountRefHash; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public String getIpAddressEncrypted() { return ipAddressEncrypted; }
    public void setIpAddressEncrypted(String ipAddressEncrypted) { this.ipAddressEncrypted = ipAddressEncrypted; }

    public Integer getSourcePort() { return sourcePort; }
    public void setSourcePort(Integer sourcePort) { this.sourcePort = sourcePort; }

    public Instant getAccessedAtUtc() { return accessedAtUtc; }
    public void setAccessedAtUtc(Instant accessedAtUtc) { this.accessedAtUtc = accessedAtUtc; }

    public Instant getRetainUntilUtc() { return retainUntilUtc; }
    public void setRetainUntilUtc(Instant retainUntilUtc) { this.retainUntilUtc = retainUntilUtc; }
}

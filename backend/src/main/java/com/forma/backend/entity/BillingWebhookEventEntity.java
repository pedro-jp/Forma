package com.forma.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "billing_webhook_events")
public class BillingWebhookEventEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "event_id", nullable = false, unique = true, length = 128)
    private String eventId;

    @Column(nullable = false, length = 40)
    private String provider = "CUSTOM_GATEWAY";

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "signature_valid", nullable = false)
    private Boolean signatureValid = false;

    @Column(name = "processing_status", nullable = false, length = 32)
    private String processingStatus = "PROCESSED";

    @Column(name = "target_user_email", length = 255)
    private String targetUserEmail;

    @Column(name = "payload_excerpt", columnDefinition = "TEXT")
    private String payloadExcerpt;

    @Column(name = "error_message", length = 500)
    private String errorMessage;

    @Column(name = "received_at_utc", nullable = false)
    private Instant receivedAtUtc = Instant.now();

    public BillingWebhookEventEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }

    public String getEventType() { return eventType; }
    public void setEventType(String eventType) { this.eventType = eventType; }

    public boolean isSignatureValid() { return Boolean.TRUE.equals(signatureValid); }
    public void setSignatureValid(Boolean signatureValid) { this.signatureValid = Boolean.TRUE.equals(signatureValid); }

    public String getProcessingStatus() { return processingStatus; }
    public void setProcessingStatus(String processingStatus) { this.processingStatus = processingStatus; }

    public String getTargetUserEmail() { return targetUserEmail; }
    public void setTargetUserEmail(String targetUserEmail) { this.targetUserEmail = targetUserEmail; }

    public String getPayloadExcerpt() { return payloadExcerpt; }
    public void setPayloadExcerpt(String payloadExcerpt) { this.payloadExcerpt = payloadExcerpt; }

    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }

    public Instant getReceivedAtUtc() { return receivedAtUtc; }
    public void setReceivedAtUtc(Instant receivedAtUtc) { this.receivedAtUtc = receivedAtUtc; }
}

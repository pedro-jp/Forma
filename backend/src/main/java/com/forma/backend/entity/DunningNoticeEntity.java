package com.forma.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "dunning_notices")
public class DunningNoticeEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "subscription_id", nullable = false)
    private UUID subscriptionId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "day_number", nullable = false)
    private Integer dayNumber = 1;

    /**
     * OVERDUE_DAY_1 | OVERDUE_DAY_2 | OVERDUE_DAY_3 | AUTO_PAUSED_AFTER_3_DAYS | AUTO_CANCELED_AFTER_3_DAYS
     */
    @Column(name = "notice_type", nullable = false, length = 32)
    private String noticeType = "OVERDUE_DAY_1";

    @Column(nullable = false, length = 160)
    private String title;

    @Column(nullable = false, length = 500)
    private String message;

    @Column(name = "websocket_delivered", nullable = false)
    private Boolean websocketDelivered = false;

    @Column(name = "email_delivered", nullable = false)
    private Boolean emailDelivered = false;

    @Column(name = "acknowledged_at_utc")
    private Instant acknowledgedAtUtc;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc = Instant.now();

    public DunningNoticeEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public int getDayNumber() { return dayNumber != null ? dayNumber : 1; }
    public void setDayNumber(Integer dayNumber) { this.dayNumber = dayNumber; }

    public String getNoticeType() { return noticeType; }
    public void setNoticeType(String noticeType) { this.noticeType = noticeType; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public boolean isWebsocketDelivered() { return Boolean.TRUE.equals(websocketDelivered); }
    public void setWebsocketDelivered(Boolean websocketDelivered) { this.websocketDelivered = Boolean.TRUE.equals(websocketDelivered); }

    public boolean isEmailDelivered() { return Boolean.TRUE.equals(emailDelivered); }
    public void setEmailDelivered(Boolean emailDelivered) { this.emailDelivered = Boolean.TRUE.equals(emailDelivered); }

    public Instant getAcknowledgedAtUtc() { return acknowledgedAtUtc; }
    public void setAcknowledgedAtUtc(Instant acknowledgedAtUtc) { this.acknowledgedAtUtc = acknowledgedAtUtc; }

    public Instant getCreatedAtUtc() { return createdAtUtc; }
    public void setCreatedAtUtc(Instant createdAtUtc) { this.createdAtUtc = createdAtUtc; }
}

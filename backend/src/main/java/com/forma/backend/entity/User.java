package com.forma.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "users")
public class User {
    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash")
    private String passwordHash;

    private String name;

    @Column(name = "auth_provider", nullable = false)
    private String authProvider = "EMAIL";

    @Column(name = "provider_id")
    private String providerId;

    @Column(name = "created_at")
    private Instant createdAt = Instant.now();

    @Column(name = "last_login_at")
    private Instant lastLoginAt;

    @Column(name = "last_synced_at")
    private Instant lastSyncedAt;

    @Column(name = "status")
    private String status = "ACTIVE";

    @Column(name = "paused_reason")
    private String pausedReason;

    @Column(name = "ai_blocked")
    private Boolean aiBlocked = false;

    @Column(name = "daily_ai_limit")
    private Integer dailyAiLimit;

    @Column(name = "force_reset_at")
    private Instant forceResetAt;

    @Column(name = "reset_code_hash")
    private String resetCodeHash;

    @Column(name = "reset_code_expires_at")
    private Instant resetCodeExpiresAt;

    @Column(name = "apple_refresh_token", length = 1024)
    private String appleRefreshToken;

    public User() {}

    public User(String email, String name, String authProvider, String providerId) {
        this.email = email;
        this.name = name;
        this.authProvider = authProvider;
        this.providerId = providerId;
        this.createdAt = Instant.now();
        this.lastLoginAt = Instant.now();
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getAuthProvider() { return authProvider; }
    public void setAuthProvider(String authProvider) { this.authProvider = authProvider; }

    public String getProviderId() { return providerId; }
    public void setProviderId(String providerId) { this.providerId = providerId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getLastLoginAt() { return lastLoginAt; }
    public void setLastLoginAt(Instant lastLoginAt) { this.lastLoginAt = lastLoginAt; }

    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }

    public String getStatus() { return (status == null || status.isBlank()) ? "ACTIVE" : status; }
    public void setStatus(String status) { this.status = status; }
    public boolean isPaused() { return "PAUSED".equalsIgnoreCase(getStatus()); }

    public String getPausedReason() { return pausedReason; }
    public void setPausedReason(String pausedReason) { this.pausedReason = pausedReason; }

    public boolean isAiBlocked() { return Boolean.TRUE.equals(aiBlocked); }
    public void setAiBlocked(Boolean aiBlocked) { this.aiBlocked = aiBlocked; }

    public Integer getDailyAiLimit() { return dailyAiLimit; }
    public void setDailyAiLimit(Integer dailyAiLimit) { this.dailyAiLimit = dailyAiLimit; }

    public Instant getForceResetAt() { return forceResetAt; }
    public void setForceResetAt(Instant forceResetAt) { this.forceResetAt = forceResetAt; }

    public String getResetCodeHash() { return resetCodeHash; }
    public void setResetCodeHash(String resetCodeHash) { this.resetCodeHash = resetCodeHash; }

    public Instant getResetCodeExpiresAt() { return resetCodeExpiresAt; }
    public void setResetCodeExpiresAt(Instant resetCodeExpiresAt) { this.resetCodeExpiresAt = resetCodeExpiresAt; }

    public String getAppleRefreshToken() { return appleRefreshToken; }
    public void setAppleRefreshToken(String appleRefreshToken) { this.appleRefreshToken = appleRefreshToken; }
}

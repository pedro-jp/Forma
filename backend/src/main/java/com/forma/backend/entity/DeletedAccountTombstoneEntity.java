package com.forma.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "deleted_accounts_tombstone")
public class DeletedAccountTombstoneEntity {

    @Id
    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(name = "deleted_at_utc", nullable = false)
    private Instant deletedAtUtc = Instant.now();

    @Column(name = "reason", length = 120)
    private String reason;

    public DeletedAccountTombstoneEntity() {}

    public DeletedAccountTombstoneEntity(String userId, String reason) {
        this.userId = userId;
        this.deletedAtUtc = Instant.now();
        this.reason = reason;
    }

    public String getUserId() { return userId; }
    public void setUserId(String userId) { this.userId = userId; }

    public Instant getDeletedAtUtc() { return deletedAtUtc; }
    public void setDeletedAtUtc(Instant deletedAtUtc) { this.deletedAtUtc = deletedAtUtc; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
}

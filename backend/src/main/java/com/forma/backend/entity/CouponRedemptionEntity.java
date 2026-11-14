package com.forma.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupon_redemptions")
public class CouponRedemptionEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "coupon_id", nullable = false)
    private UUID couponId;

    @Column(name = "coupon_code", nullable = false, length = 64)
    private String couponCode;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    @Column(name = "benefit_summary", length = 255)
    private String benefitSummary;

    @Column(name = "redeemed_at_utc", nullable = false)
    private Instant redeemedAtUtc = Instant.now();

    public CouponRedemptionEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getCouponId() { return couponId; }
    public void setCouponId(UUID couponId) { this.couponId = couponId; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }

    public String getBenefitSummary() { return benefitSummary; }
    public void setBenefitSummary(String benefitSummary) { this.benefitSummary = benefitSummary; }

    public Instant getRedeemedAtUtc() { return redeemedAtUtc; }
    public void setRedeemedAtUtc(Instant redeemedAtUtc) { this.redeemedAtUtc = redeemedAtUtc; }
}

package com.forma.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "coupons")
public class CouponEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(nullable = false, unique = true, length = 64)
    private String code;

    @Column(length = 255)
    private String description;

    /**
     * FREE_PERIOD_DAYS | PERCENT_DISCOUNT | FIXED_CENTS_DISCOUNT
     */
    @Column(name = "coupon_type", nullable = false, length = 32)
    private String couponType = "FREE_PERIOD_DAYS";

    @Column(name = "discount_percent")
    private Integer discountPercent;

    @Column(name = "discount_cents")
    private Integer discountCents;

    @Column(name = "free_period_days")
    private Integer freePeriodDays;

    @Column(name = "applicable_plan_id", length = 64)
    private String applicablePlanId;

    @Column(name = "max_redemptions")
    private Integer maxRedemptions = 1;

    @Column(name = "current_redemptions", nullable = false)
    private Integer currentRedemptions = 0;

    @Column(name = "per_user_limit", nullable = false)
    private Integer perUserLimit = 1;

    @Column(name = "restricted_to_email", length = 180)
    private String restrictedToEmail;

    @Column(name = "first_time_only")
    private Boolean firstTimeOnly = false;

    @Column(name = "expires_at_utc")
    private Instant expiresAtUtc;

    @Column(name = "exhausted_at_utc")
    private Instant exhaustedAtUtc;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "created_by_admin", length = 80)
    private String createdByAdmin;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc = Instant.now();

    public CouponEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getCouponType() { return couponType != null ? couponType : "FREE_PERIOD_DAYS"; }
    public void setCouponType(String couponType) { this.couponType = couponType; }

    public Integer getDiscountPercent() { return discountPercent; }
    public void setDiscountPercent(Integer discountPercent) { this.discountPercent = discountPercent; }

    public Integer getDiscountCents() { return discountCents; }
    public void setDiscountCents(Integer discountCents) { this.discountCents = discountCents; }

    public Integer getFreePeriodDays() { return freePeriodDays; }
    public void setFreePeriodDays(Integer freePeriodDays) { this.freePeriodDays = freePeriodDays; }

    public String getApplicablePlanId() { return applicablePlanId; }
    public void setApplicablePlanId(String applicablePlanId) { this.applicablePlanId = applicablePlanId; }

    public boolean hasLegacyUnlimitedQuota() {
        return maxRedemptions == null || maxRedemptions <= 0;
    }

    public int getMaxRedemptions() { return maxRedemptions != null && maxRedemptions > 0 ? maxRedemptions : 1; }
    public void setMaxRedemptions(Integer maxRedemptions) { this.maxRedemptions = maxRedemptions; }

    public int getCurrentRedemptions() { return currentRedemptions != null ? currentRedemptions : 0; }
    public void setCurrentRedemptions(Integer currentRedemptions) { this.currentRedemptions = currentRedemptions != null ? currentRedemptions : 0; }

    public int getRemainingRedemptions() { return Math.max(0, getMaxRedemptions() - getCurrentRedemptions()); }
    public boolean isExhausted() { return getCurrentRedemptions() >= getMaxRedemptions(); }

    public int getPerUserLimit() { return perUserLimit != null && perUserLimit > 0 ? perUserLimit : 1; }
    public void setPerUserLimit(Integer perUserLimit) { this.perUserLimit = perUserLimit; }

    public String getRestrictedToEmail() { return restrictedToEmail; }
    public void setRestrictedToEmail(String restrictedToEmail) { this.restrictedToEmail = restrictedToEmail; }

    public boolean isFirstTimeOnly() { return Boolean.TRUE.equals(firstTimeOnly); }
    public void setFirstTimeOnly(Boolean firstTimeOnly) { this.firstTimeOnly = Boolean.TRUE.equals(firstTimeOnly); }

    public Instant getExpiresAtUtc() { return expiresAtUtc; }
    public void setExpiresAtUtc(Instant expiresAtUtc) { this.expiresAtUtc = expiresAtUtc; }
    public boolean isExpired(Instant now) { return expiresAtUtc != null && (now != null ? now : Instant.now()).isAfter(expiresAtUtc); }

    public Instant getExhaustedAtUtc() { return exhaustedAtUtc; }
    public void setExhaustedAtUtc(Instant exhaustedAtUtc) { this.exhaustedAtUtc = exhaustedAtUtc; }

    public boolean isActive() { return !Boolean.FALSE.equals(active); }
    public void setActive(Boolean active) { this.active = active; }

    public String getCreatedByAdmin() { return createdByAdmin; }
    public void setCreatedByAdmin(String createdByAdmin) { this.createdByAdmin = createdByAdmin; }

    public Instant getCreatedAtUtc() { return createdAtUtc; }
    public void setCreatedAtUtc(Instant createdAtUtc) { this.createdAtUtc = createdAtUtc; }
}

package com.forma.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "user_subscriptions")
public class UserSubscriptionEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "plan_id", nullable = false, length = 64)
    private String planId = "PRO_MONTHLY";

    @Column(name = "plan_name", length = 120)
    private String planName = "Forma Pro Mensal";

    /**
     * APPLE_STOREKIT | COUPON_FREE_PERIOD | ADMIN_MANUAL | EXTERNAL_GATEWAY
     */
    @Column(nullable = false, length = 32)
    private String source = "ADMIN_MANUAL";

    /**
     * TRIALING | ACTIVE | PAST_DUE | PAUSED | CANCELED | EXPIRED
     */
    @Column(nullable = false, length = 32)
    private String status = "ACTIVE";

    @Column(name = "price_cents", nullable = false)
    private Integer priceCents = 0;

    @Column(length = 8)
    private String currency = "BRL";

    @Column(name = "applied_coupon_code", length = 64)
    private String appliedCouponCode;

    @Column(name = "auto_renew", nullable = false)
    private Boolean autoRenew = true;

    @Column(name = "external_transaction_id", length = 160)
    private String externalTransactionId;

    @Column(name = "current_period_start_utc")
    private Instant currentPeriodStartUtc;

    @Column(name = "current_period_end_utc")
    private Instant currentPeriodEndUtc;

    @Column(name = "trial_ends_at_utc")
    private Instant trialEndsAtUtc;

    @Column(name = "overdue_since_utc")
    private Instant overdueSinceUtc;

    @Column(name = "grace_ends_at_utc")
    private Instant graceEndsAtUtc;

    @Column(name = "dunning_warnings_sent", nullable = false)
    private Integer dunningWarningsSent = 0;

    @Column(name = "last_warning_day_sent", nullable = false)
    private Integer lastWarningDaySent = 0;

    @Column(name = "last_warning_at_utc")
    private Instant lastWarningAtUtc;

    /**
     * Null inherits global admin setting (PAUSE | CANCEL)
     */
    @Column(name = "overdue_action_override", length = 16)
    private String overdueActionOverride;

    @Column(name = "paused_at_utc")
    private Instant pausedAtUtc;

    @Column(name = "canceled_at_utc")
    private Instant canceledAtUtc;

    @Column(name = "status_reason", length = 255)
    private String statusReason;

    @Column(name = "next_billing_discount_percent")
    private Integer nextBillingDiscountPercent;

    @Column(name = "next_billing_discount_cents")
    private Integer nextBillingDiscountCents;

    @Column(name = "next_billing_coupon_code", length = 64)
    private String nextBillingCouponCode;

    @Column(name = "next_billing_price_cents")
    private Integer nextBillingPriceCents;

    @Column(name = "next_billing_discount_note", length = 180)
    private String nextBillingDiscountNote;

    @Column(name = "created_at_utc")
    private Instant createdAtUtc = Instant.now();

    @Column(name = "updated_at_utc")
    private Instant updatedAtUtc = Instant.now();

    public UserSubscriptionEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public String getPlanId() { return planId; }
    public void setPlanId(String planId) { this.planId = planId; }

    public String getPlanName() { return planName; }
    public void setPlanName(String planName) { this.planName = planName; }

    public String getSource() { return source != null ? source : "ADMIN_MANUAL"; }
    public void setSource(String source) { this.source = source; }

    public String getStatus() { return status != null ? status : "ACTIVE"; }
    public void setStatus(String status) { this.status = status; }

    public int getPriceCents() { return priceCents != null ? priceCents : 0; }
    public void setPriceCents(Integer priceCents) { this.priceCents = priceCents; }

    public String getCurrency() { return currency != null ? currency : "BRL"; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getAppliedCouponCode() { return appliedCouponCode; }
    public void setAppliedCouponCode(String appliedCouponCode) { this.appliedCouponCode = appliedCouponCode; }

    public boolean isAutoRenew() { return !Boolean.FALSE.equals(autoRenew); }
    public void setAutoRenew(Boolean autoRenew) { this.autoRenew = autoRenew; }

    public String getExternalTransactionId() { return externalTransactionId; }
    public void setExternalTransactionId(String externalTransactionId) { this.externalTransactionId = externalTransactionId; }

    public Instant getCurrentPeriodStartUtc() { return currentPeriodStartUtc; }
    public void setCurrentPeriodStartUtc(Instant currentPeriodStartUtc) { this.currentPeriodStartUtc = currentPeriodStartUtc; }

    public Instant getCurrentPeriodEndUtc() { return currentPeriodEndUtc; }
    public void setCurrentPeriodEndUtc(Instant currentPeriodEndUtc) { this.currentPeriodEndUtc = currentPeriodEndUtc; }

    public Instant getTrialEndsAtUtc() { return trialEndsAtUtc; }
    public void setTrialEndsAtUtc(Instant trialEndsAtUtc) { this.trialEndsAtUtc = trialEndsAtUtc; }

    public Instant getOverdueSinceUtc() { return overdueSinceUtc; }
    public void setOverdueSinceUtc(Instant overdueSinceUtc) { this.overdueSinceUtc = overdueSinceUtc; }

    public Instant getGraceEndsAtUtc() { return graceEndsAtUtc; }
    public void setGraceEndsAtUtc(Instant graceEndsAtUtc) { this.graceEndsAtUtc = graceEndsAtUtc; }

    public int getDunningWarningsSent() { return dunningWarningsSent != null ? dunningWarningsSent : 0; }
    public void setDunningWarningsSent(Integer dunningWarningsSent) { this.dunningWarningsSent = dunningWarningsSent != null ? dunningWarningsSent : 0; }

    public int getLastWarningDaySent() { return lastWarningDaySent != null ? lastWarningDaySent : 0; }
    public void setLastWarningDaySent(Integer lastWarningDaySent) { this.lastWarningDaySent = lastWarningDaySent != null ? lastWarningDaySent : 0; }

    public Instant getLastWarningAtUtc() { return lastWarningAtUtc; }
    public void setLastWarningAtUtc(Instant lastWarningAtUtc) { this.lastWarningAtUtc = lastWarningAtUtc; }

    public String getOverdueActionOverride() { return overdueActionOverride; }
    public void setOverdueActionOverride(String overdueActionOverride) { this.overdueActionOverride = overdueActionOverride; }

    public Instant getPausedAtUtc() { return pausedAtUtc; }
    public void setPausedAtUtc(Instant pausedAtUtc) { this.pausedAtUtc = pausedAtUtc; }

    public Instant getCanceledAtUtc() { return canceledAtUtc; }
    public void setCanceledAtUtc(Instant canceledAtUtc) { this.canceledAtUtc = canceledAtUtc; }

    public String getStatusReason() { return statusReason; }
    public void setStatusReason(String statusReason) { this.statusReason = statusReason; }

    public Integer getNextBillingDiscountPercent() { return nextBillingDiscountPercent; }
    public void setNextBillingDiscountPercent(Integer nextBillingDiscountPercent) { this.nextBillingDiscountPercent = nextBillingDiscountPercent; }

    public Integer getNextBillingDiscountCents() { return nextBillingDiscountCents; }
    public void setNextBillingDiscountCents(Integer nextBillingDiscountCents) { this.nextBillingDiscountCents = nextBillingDiscountCents; }

    public String getNextBillingCouponCode() { return nextBillingCouponCode; }
    public void setNextBillingCouponCode(String nextBillingCouponCode) { this.nextBillingCouponCode = nextBillingCouponCode; }

    public Integer getNextBillingPriceCents() { return nextBillingPriceCents; }
    public void setNextBillingPriceCents(Integer nextBillingPriceCents) { this.nextBillingPriceCents = nextBillingPriceCents; }

    public String getNextBillingDiscountNote() { return nextBillingDiscountNote; }
    public void setNextBillingDiscountNote(String nextBillingDiscountNote) { this.nextBillingDiscountNote = nextBillingDiscountNote; }

    public void clearNextBillingDiscount() {
        this.nextBillingDiscountPercent = null;
        this.nextBillingDiscountCents = null;
        this.nextBillingCouponCode = null;
        this.nextBillingPriceCents = null;
        this.nextBillingDiscountNote = null;
    }

    public Instant getCreatedAtUtc() { return createdAtUtc; }
    public void setCreatedAtUtc(Instant createdAtUtc) { this.createdAtUtc = createdAtUtc; }

    public Instant getUpdatedAtUtc() { return updatedAtUtc; }
    public void setUpdatedAtUtc(Instant updatedAtUtc) { this.updatedAtUtc = updatedAtUtc; }
}

package com.forma.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_entries")
public class PaymentEntryEntity {

    @Id
    private UUID id = UUID.randomUUID();

    @Column(name = "user_id")
    private UUID userId;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "subscription_id")
    private UUID subscriptionId;

    /**
     * SUBSCRIPTION_CHARGE | MANUAL_INCOME | COUPON_TRIAL_GRANT | REFUND | ADJUSTMENT
     */
    @Column(name = "entry_type", nullable = false, length = 32)
    private String entryType = "MANUAL_INCOME";

    /**
     * PAID | PENDING | OVERDUE | REFUNDED | CANCELED
     */
    @Column(nullable = false, length = 24)
    private String status = "PAID";

    @Column(name = "gross_amount_cents", nullable = false)
    private Integer grossAmountCents = 0;

    @Column(name = "discount_cents", nullable = false)
    private Integer discountCents = 0;

    @Column(name = "net_amount_cents", nullable = false)
    private Integer netAmountCents = 0;

    @Column(length = 8)
    private String currency = "BRL";

    /**
     * APPLE_IAP | PIX | CARD | COUPON | MANUAL | WEBHOOK
     */
    @Column(name = "payment_method", nullable = false, length = 32)
    private String paymentMethod = "MANUAL";

    @Column(name = "coupon_code", length = 64)
    private String couponCode;

    @Column(name = "reference_code", length = 128)
    private String referenceCode;

    @Column(nullable = false, length = 255)
    private String description;

    @Column(name = "paid_at_utc")
    private Instant paidAtUtc;

    @Column(name = "due_at_utc")
    private Instant dueAtUtc;

    @Column(name = "created_by", length = 80)
    private String createdBy;

    @Column(name = "created_at_utc", nullable = false)
    private Instant createdAtUtc = Instant.now();

    public PaymentEntryEntity() {}

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getUserId() { return userId; }
    public void setUserId(UUID userId) { this.userId = userId; }

    public String getUserEmail() { return userEmail; }
    public void setUserEmail(String userEmail) { this.userEmail = userEmail; }

    public UUID getSubscriptionId() { return subscriptionId; }
    public void setSubscriptionId(UUID subscriptionId) { this.subscriptionId = subscriptionId; }

    public String getEntryType() { return entryType != null ? entryType : "MANUAL_INCOME"; }
    public void setEntryType(String entryType) { this.entryType = entryType; }

    public String getStatus() { return status != null ? status : "PENDING"; }
    public void setStatus(String status) { this.status = status; }

    public int getGrossAmountCents() { return grossAmountCents != null ? grossAmountCents : 0; }
    public void setGrossAmountCents(Integer grossAmountCents) { this.grossAmountCents = grossAmountCents != null ? grossAmountCents : 0; }

    public int getDiscountCents() { return discountCents != null ? discountCents : 0; }
    public void setDiscountCents(Integer discountCents) { this.discountCents = discountCents != null ? discountCents : 0; }

    public int getNetAmountCents() { return netAmountCents != null ? netAmountCents : 0; }
    public void setNetAmountCents(Integer netAmountCents) { this.netAmountCents = netAmountCents != null ? netAmountCents : 0; }

    public String getCurrency() { return currency != null ? currency : "BRL"; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getPaymentMethod() { return paymentMethod != null ? paymentMethod : "MANUAL"; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getCouponCode() { return couponCode; }
    public void setCouponCode(String couponCode) { this.couponCode = couponCode; }

    public String getReferenceCode() { return referenceCode; }
    public void setReferenceCode(String referenceCode) { this.referenceCode = referenceCode; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Instant getPaidAtUtc() { return paidAtUtc; }
    public void setPaidAtUtc(Instant paidAtUtc) { this.paidAtUtc = paidAtUtc; }

    public Instant getDueAtUtc() { return dueAtUtc; }
    public void setDueAtUtc(Instant dueAtUtc) { this.dueAtUtc = dueAtUtc; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public Instant getCreatedAtUtc() { return createdAtUtc; }
    public void setCreatedAtUtc(Instant createdAtUtc) { this.createdAtUtc = createdAtUtc; }
}

package com.forma.backend.entity;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "subscription_plans")
public class SubscriptionPlanEntity {

    @Id
    @Column(length = 64)
    private String id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(name = "storekit_product_id", length = 120)
    private String storekitProductId;

    @Column(name = "billing_interval", nullable = false, length = 24)
    private String billingInterval = "MONTHLY";

    @Column(name = "price_cents", nullable = false)
    private Integer priceCents = 2490;

    @Column(length = 8)
    private String currency = "BRL";

    @Column(name = "default_trial_days")
    private Integer defaultTrialDays = 0;

    @Column(nullable = false)
    private Boolean active = true;

    @Column(name = "updated_at_utc")
    private Instant updatedAtUtc = Instant.now();

    public SubscriptionPlanEntity() {}

    public SubscriptionPlanEntity(
        String id,
        String name,
        String description,
        String storekitProductId,
        String billingInterval,
        Integer priceCents,
        String currency,
        Integer defaultTrialDays,
        Boolean active
    ) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.storekitProductId = storekitProductId;
        this.billingInterval = billingInterval;
        this.priceCents = priceCents;
        this.currency = currency;
        this.defaultTrialDays = defaultTrialDays;
        this.active = active;
        this.updatedAtUtc = Instant.now();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getStorekitProductId() { return storekitProductId; }
    public void setStorekitProductId(String storekitProductId) { this.storekitProductId = storekitProductId; }

    public String getBillingInterval() { return billingInterval != null ? billingInterval : "MONTHLY"; }
    public void setBillingInterval(String billingInterval) { this.billingInterval = billingInterval; }

    public int getPriceCents() { return priceCents != null ? priceCents : 0; }
    public void setPriceCents(Integer priceCents) { this.priceCents = priceCents; }

    public String getCurrency() { return currency != null ? currency : "BRL"; }
    public void setCurrency(String currency) { this.currency = currency; }

    public int getDefaultTrialDays() { return defaultTrialDays != null ? defaultTrialDays : 0; }
    public void setDefaultTrialDays(Integer defaultTrialDays) { this.defaultTrialDays = defaultTrialDays; }

    public boolean isActive() { return !Boolean.FALSE.equals(active); }
    public void setActive(Boolean active) { this.active = active; }

    public Instant getUpdatedAtUtc() { return updatedAtUtc; }
    public void setUpdatedAtUtc(Instant updatedAtUtc) { this.updatedAtUtc = updatedAtUtc; }
}

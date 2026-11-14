package com.forma.backend.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.forma.backend.dto.BillingDto.*;
import com.forma.backend.security.AuthContextResolver;
import com.forma.backend.service.AppConfigService;
import com.forma.backend.service.MciAccessLogService;
import com.forma.backend.service.SubscriptionBillingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin/billing")
public class AdminBillingController {

    private final AuthContextResolver authContextResolver;
    private final SubscriptionBillingService billingService;
    private final AppConfigService appConfigService;
    private final MciAccessLogService mciAccessLogService;
    private final String configuredAdminUsername;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminBillingController(
        AuthContextResolver authContextResolver,
        SubscriptionBillingService billingService,
        AppConfigService appConfigService,
        MciAccessLogService mciAccessLogService,
        @Value("${app.admin.username:admin}") String configuredAdminUsername
    ) {
        this.authContextResolver = authContextResolver;
        this.billingService = billingService;
        this.appConfigService = appConfigService;
        this.mciAccessLogService = mciAccessLogService;
        this.configuredAdminUsername = configuredAdminUsername;
    }

    @GetMapping("/dashboard")
    public ResponseEntity<BillingDashboardResponse> getDashboard(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        return ResponseEntity.ok(billingService.getAdminDashboard(null));
    }

    @PutMapping("/plans/{planId}")
    public ResponseEntity<PlanSummaryDto> updatePlan(
        @PathVariable String planId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody UpdatePlanRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        PlanSummaryDto updated = billingService.updatePlan(planId, req);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "BILLING_PLAN_UPDATED", planId, "Plano atualizado R$ " + (updated.priceCents() / 100.0), request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/coupons")
    public ResponseEntity<CouponSummaryDto> createCoupon(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody CreateOrUpdateCouponRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        CouponSummaryDto created = billingService.createCoupon(req, configuredAdminUsername);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "COUPON_CREATED", created.code(), "Cupom criado (" + created.couponType() + ")", request);
        return ResponseEntity.ok(created);
    }

    @PutMapping("/coupons/{couponId}")
    public ResponseEntity<CouponSummaryDto> updateCoupon(
        @PathVariable UUID couponId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody CreateOrUpdateCouponRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        CouponSummaryDto updated = billingService.updateCoupon(couponId, req);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "COUPON_UPDATED", updated.code(), "Cupom atualizado", request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/coupons/{couponId}/toggle")
    public ResponseEntity<CouponSummaryDto> toggleCoupon(
        @PathVariable UUID couponId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        CouponSummaryDto toggled = billingService.toggleCoupon(couponId);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "COUPON_TOGGLED", toggled.code(), "active=" + toggled.active(), request);
        return ResponseEntity.ok(toggled);
    }

    @DeleteMapping("/coupons/{couponId}")
    public ResponseEntity<?> deleteCoupon(
        @PathVariable UUID couponId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        billingService.deleteCoupon(couponId);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "COUPON_DELETED", couponId.toString(), "Cupom removido", request);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PostMapping("/subscriptions/grant")
    public ResponseEntity<UserSubscriptionStatusDto> grantSubscription(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody AdminGrantSubscriptionRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        UserSubscriptionStatusDto sub = billingService.adminGrantSubscription(req, configuredAdminUsername);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "SUBSCRIPTION_GRANTED", sub.userId(), "Plano " + sub.planId() + " (" + sub.status() + ")", request);
        return ResponseEntity.ok(sub);
    }

    @PostMapping("/subscriptions/{subscriptionId}/action")
    public ResponseEntity<UserSubscriptionStatusDto> performSubscriptionAction(
        @PathVariable UUID subscriptionId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody AdminSubscriptionActionRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        UserSubscriptionStatusDto updated = billingService.adminSubscriptionAction(subscriptionId, req, configuredAdminUsername);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "SUBSCRIPTION_ACTION", subscriptionId.toString(), "Ação=" + (req != null ? req.action() : ""), request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/subscriptions/next-discount")
    public ResponseEntity<UserSubscriptionStatusDto> applyNextBillingDiscount(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody ApplyNextBillingDiscountRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        UserSubscriptionStatusDto updated = billingService.applyNextBillingDiscount(req, configuredAdminUsername);
        mciAccessLogService.recordAdminAudit(
            configuredAdminUsername,
            "ADMIN_NEXT_BILLING_DISCOUNT",
            updated.userId(),
            updated.nextBillingDiscountLabel() != null ? updated.nextBillingDiscountLabel() : "Desconto próxima cobrança",
            request
        );
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/subscriptions/{subscriptionId}/next-discount")
    public ResponseEntity<UserSubscriptionStatusDto> clearNextBillingDiscount(
        @PathVariable UUID subscriptionId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        UserSubscriptionStatusDto updated = billingService.clearNextBillingDiscount(subscriptionId, configuredAdminUsername);
        mciAccessLogService.recordAdminAudit(
            configuredAdminUsername,
            "ADMIN_CLEAR_NEXT_BILLING_DISCOUNT",
            subscriptionId.toString(),
            "Desconto da próxima cobrança removido",
            request
        );
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/subscriptions/{subscriptionId}/simulate-overdue")
    public ResponseEntity<UserSubscriptionStatusDto> simulateOverdue(
        @PathVariable UUID subscriptionId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody(required = false) SimulateOverdueRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        UserSubscriptionStatusDto updated = billingService.simulateOverdueStage(subscriptionId, req);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "DUNNING_SIMULATED", subscriptionId.toString(), "Simulação dia=" + (req != null ? req.dayStage() : 1), request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/run-dunning-check")
    public ResponseEntity<DunningRunSummaryDto> runDunningCheck(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        DunningRunSummaryDto summary = billingService.evaluateOverdueSubscriptions();
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "DUNNING_SWEEP_RUN", (String) null, "Avaliadas=" + summary.evaluatedCount() + ", Avisos=" + summary.warningsSentCount(), request);
        return ResponseEntity.ok(summary);
    }

    @PostMapping("/entries")
    public ResponseEntity<PaymentEntryDto> createEntry(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody CreatePaymentEntryRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        PaymentEntryDto created = billingService.createManualPaymentEntry(req, configuredAdminUsername);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "PAYMENT_ENTRY_CREATED", created.id(), "Entrada " + created.entryType() + " net=" + created.netAmountCents(), request);
        return ResponseEntity.ok(created);
    }

    @PostMapping("/entries/{entryId}/status")
    public ResponseEntity<PaymentEntryDto> updateEntryStatus(
        @PathVariable UUID entryId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody UpdatePaymentEntryStatusRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        PaymentEntryDto updated = billingService.updatePaymentEntryStatus(entryId, req);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "PAYMENT_ENTRY_STATUS", entryId.toString(), "status=" + updated.status(), request);
        return ResponseEntity.ok(updated);
    }

    @DeleteMapping("/entries/{entryId}")
    public ResponseEntity<?> deleteEntry(
        @PathVariable UUID entryId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        billingService.deletePaymentEntry(entryId);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "PAYMENT_ENTRY_DELETED", entryId.toString(), "Entrada excluída", request);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PutMapping("/settings")
    public ResponseEntity<?> updateBillingSettings(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody BillingSettingsRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        if (req != null && req.overdueAction() != null) {
            appConfigService.setBillingOverdueAction(req.overdueAction());
        }
        String effective = appConfigService.getBillingOverdueAction();
        billingService.notifyBillingPolicyUpdated(effective);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "BILLING_POLICY_UPDATED", (String) null, "overdueAction=" + effective, request);
        return ResponseEntity.ok(Map.of("overdueAction", effective));
    }

    @GetMapping("/webhooks/config")
    public ResponseEntity<WebhookConfigDto> getWebhookConfig(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        return ResponseEntity.ok(new WebhookConfigDto(
            "/api/webhooks/billing",
            "X-Forma-Webhook-Signature",
            appConfigService.getMaskedWebhookSecret(),
            null
        ));
    }

    @PostMapping("/webhooks/rotate-secret")
    public ResponseEntity<WebhookConfigDto> rotateWebhookSecret(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        String newSecret = appConfigService.rotateWebhookSecret();
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "WEBHOOK_SECRET_ROTATED", (String) null, "Novo segredo HMAC-SHA256 gerado", request);
        return ResponseEntity.ok(new WebhookConfigDto(
            "/api/webhooks/billing",
            "X-Forma-Webhook-Signature",
            appConfigService.getMaskedWebhookSecret(),
            newSecret
        ));
    }

    @PostMapping("/webhooks/simulate")
    public ResponseEntity<WebhookEventDto> simulateSignedWebhook(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody SimulateWebhookRequest req
    ) throws Exception {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventId", "sim_" + UUID.randomUUID().toString().substring(0, 12));
        payload.put("provider", (req != null && req.provider() != null && !req.provider().isBlank()) ? req.provider().trim() : "ADMIN_SIMULATOR");
        payload.put("eventType", (req != null && req.eventType() != null && !req.eventType().isBlank()) ? req.eventType().trim() : "PAYMENT_SUCCEEDED");
        payload.put("userEmail", req != null ? req.userEmail() : null);
        payload.put("planId", (req != null && req.planId() != null) ? req.planId() : "PRO_MONTHLY");
        if (req != null && req.amountCents() != null) {
            payload.put("amountCents", req.amountCents());
        }
        payload.put("timestamp", Instant.now().toString());

        String json = objectMapper.writeValueAsString(payload);
        String sig = "sha256=" + billingService.computeHmacSha256Hex(json, appConfigService.getWebhookSecret());
        WebhookEventDto processed = billingService.processWebhook(json, sig, false);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "WEBHOOK_SIMULATED", processed.eventId(), processed.eventType() + " (" + processed.processingStatus() + ")", request);
        return ResponseEntity.ok(processed);
    }
}

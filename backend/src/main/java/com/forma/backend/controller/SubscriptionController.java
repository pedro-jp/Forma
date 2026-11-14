package com.forma.backend.controller;

import com.forma.backend.dto.BillingDto.*;
import com.forma.backend.entity.SecurityIncidentLogEntity;
import com.forma.backend.entity.User;
import com.forma.backend.exception.DomainExceptions.RateLimitExceededException;
import com.forma.backend.exception.DomainExceptions.ResourceNotFoundException;
import com.forma.backend.repository.SecurityIncidentLogRepository;
import com.forma.backend.security.AuthContextResolver;
import com.forma.backend.service.MciAccessLogService;
import com.forma.backend.service.SubscriptionBillingService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private static final CacheControl NO_STORE_CACHE = CacheControl.noStore().mustRevalidate();
    private static final int USER_MAX_FAILED_COUPON_ATTEMPTS = 5;
    private static final int IP_MAX_FAILED_COUPON_ATTEMPTS = 15;
    private static final long COUPON_WINDOW_MS = 10 * 60 * 1000L;

    private final AuthContextResolver authContextResolver;
    private final SubscriptionBillingService billingService;
    private final SecurityIncidentLogRepository securityIncidentLogRepository;
    private final MciAccessLogService mciAccessLogService;
    private final Map<String, Deque<Long>> failedCouponAttemptsByKey = new ConcurrentHashMap<>();
    private final Map<String, Long> incidentLoggedUntilByKey = new ConcurrentHashMap<>();

    public SubscriptionController(
        AuthContextResolver authContextResolver,
        SubscriptionBillingService billingService,
        SecurityIncidentLogRepository securityIncidentLogRepository,
        MciAccessLogService mciAccessLogService
    ) {
        this.authContextResolver = authContextResolver;
        this.billingService = billingService;
        this.securityIncidentLogRepository = securityIncidentLogRepository;
        this.mciAccessLogService = mciAccessLogService;
    }

    @GetMapping("/catalog")
    public ResponseEntity<LiveBillingCatalogDto> getLiveCatalog(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        UUID optionalUserId = null;
        if (authHeader != null && !authHeader.isBlank()) {
            try {
                optionalUserId = authContextResolver.requireActiveUser(authHeader).getId();
            } catch (Exception ignored) {
                optionalUserId = null;
            }
        }
        return ResponseEntity.ok()
            .cacheControl(NO_STORE_CACHE)
            .header("Pragma", "no-cache")
            .body(billingService.getLiveBillingCatalog(optionalUserId));
    }

    @GetMapping("/plans")
    public ResponseEntity<List<PlanSummaryDto>> listPlans() {
        return ResponseEntity.ok()
            .cacheControl(NO_STORE_CACHE)
            .header("Pragma", "no-cache")
            .body(billingService.listActivePlans());
    }

    @GetMapping("/me")
    public ResponseEntity<UserSubscriptionStatusDto> getMySubscription(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        return ResponseEntity.ok()
            .cacheControl(NO_STORE_CACHE)
            .header("Pragma", "no-cache")
            .body(billingService.getUserSubscriptionStatus(user));
    }

    @PostMapping("/validate-coupon")
    public ResponseEntity<CouponValidationResponse> validateCoupon(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody ValidateCouponRequest req,
        HttpServletRequest request
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        String clientIp = mciAccessLogService.extractClientIp(request);
        assertCouponRateLimitAllowed(user, clientIp);
        try {
            CouponValidationResponse res = billingService.validateCoupon(
                user,
                req != null ? req.code() : null,
                req != null ? req.planId() : null
            );
            clearUserCouponFailures(user.getId());
            return ResponseEntity.ok()
                .cacheControl(NO_STORE_CACHE)
                .header("Pragma", "no-cache")
                .body(res);
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            recordCouponFailure(user.getId(), clientIp);
            throw ex;
        }
    }

    @PostMapping("/redeem-coupon")
    public ResponseEntity<RedeemCouponResponse> redeemCoupon(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody RedeemCouponRequest req,
        HttpServletRequest request
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        String clientIp = mciAccessLogService.extractClientIp(request);
        assertCouponRateLimitAllowed(user, clientIp);
        try {
            RedeemCouponResponse res = billingService.redeemCoupon(
                user,
                req != null ? req.code() : null,
                req != null ? req.planId() : null
            );
            clearUserCouponFailures(user.getId());
            return ResponseEntity.ok(res);
        } catch (IllegalArgumentException | ResourceNotFoundException ex) {
            recordCouponFailure(user.getId(), clientIp);
            throw ex;
        }
    }

    @PostMapping("/sync-storekit")
    public ResponseEntity<UserSubscriptionStatusDto> syncStoreKit(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody SyncStoreKitRequest req
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        return ResponseEntity.ok(billingService.syncStoreKitTransaction(user, req));
    }

    @PostMapping("/acknowledge-warning")
    public ResponseEntity<?> acknowledgeWarning(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        billingService.acknowledgeLatestWarnings(user);
        return ResponseEntity.ok(Map.of("status", "acknowledged"));
    }

    private void assertCouponRateLimitAllowed(User user, String clientIp) {
        long nowMs = System.currentTimeMillis();
        String userKey = "user:" + user.getId();
        String ipKey = "ip:" + (clientIp != null && !clientIp.isBlank() ? clientIp : "0.0.0.0");

        boolean userBlocked = countRecentFailures(userKey, nowMs) >= USER_MAX_FAILED_COUPON_ATTEMPTS;
        boolean ipBlocked = countRecentFailures(ipKey, nowMs) >= IP_MAX_FAILED_COUPON_ATTEMPTS;
        if (userBlocked || ipBlocked) {
            String triggerKey = userBlocked ? userKey : ipKey;
            Long suppressedUntil = incidentLoggedUntilByKey.get(triggerKey);
            if (suppressedUntil == null || nowMs > suppressedUntil) {
                incidentLoggedUntilByKey.put(triggerKey, nowMs + COUPON_WINDOW_MS);
                Instant now = Instant.now();
                SecurityIncidentLogEntity incident = new SecurityIncidentLogEntity();
                incident.setSeverity("MEDIUM");
                incident.setIncidentType("COUPON_ENUMERATION_BLOCKED");
                incident.setAffectedCategories("COUPON_CODES,SUBSCRIPTIONS");
                incident.setAffectedCount(1);
                incident.setDescription(
                    "Tentativas repetidas de descoberta/enumeração de código promocional privado bloqueadas (conta="
                        + user.getEmail() + ", origem=" + clientIp + ")."
                );
                incident.setMitigationActions("Bloqueio temporário de 10 minutos aplicado (HTTP 429) e tentativas auditadas.");
                incident.setDetectedAtUtc(now);
                incident.setRetainUntilUtc(now.plus(Duration.ofDays(1825)));
                securityIncidentLogRepository.saveAndFlush(incident);
            }
            throw new RateLimitExceededException(
                "Muitas tentativas inválidas de código promocional. Aguarde 10 minutos antes de tentar novamente."
            );
        }
    }

    private int countRecentFailures(String key, long nowMs) {
        Deque<Long> deque = failedCouponAttemptsByKey.get(key);
        if (deque == null) {
            return 0;
        }
        synchronized (deque) {
            long cutoff = nowMs - COUPON_WINDOW_MS;
            while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
                deque.pollFirst();
            }
            return deque.size();
        }
    }

    private void recordCouponFailure(UUID userId, String clientIp) {
        long nowMs = System.currentTimeMillis();
        appendFailure("user:" + userId, nowMs);
        appendFailure("ip:" + (clientIp != null && !clientIp.isBlank() ? clientIp : "0.0.0.0"), nowMs);
    }

    private void appendFailure(String key, long nowMs) {
        Deque<Long> deque = failedCouponAttemptsByKey.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            long cutoff = nowMs - COUPON_WINDOW_MS;
            while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
                deque.pollFirst();
            }
            deque.addLast(nowMs);
        }
    }

    private void clearUserCouponFailures(UUID userId) {
        failedCouponAttemptsByKey.remove("user:" + userId);
    }
}

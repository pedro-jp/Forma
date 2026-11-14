package com.forma.backend.controller;

import com.forma.backend.dto.BillingDto.*;
import com.forma.backend.entity.User;
import com.forma.backend.security.AuthContextResolver;
import com.forma.backend.service.SubscriptionBillingService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/subscriptions")
public class SubscriptionController {

    private final AuthContextResolver authContextResolver;
    private final SubscriptionBillingService billingService;

    public SubscriptionController(
        AuthContextResolver authContextResolver,
        SubscriptionBillingService billingService
    ) {
        this.authContextResolver = authContextResolver;
        this.billingService = billingService;
    }

    @GetMapping("/plans")
    public ResponseEntity<List<PlanSummaryDto>> listPlans() {
        return ResponseEntity.ok(billingService.listActivePlans());
    }

    @GetMapping("/me")
    public ResponseEntity<UserSubscriptionStatusDto> getMySubscription(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        return ResponseEntity.ok(billingService.getUserSubscriptionStatus(user));
    }

    @PostMapping("/validate-coupon")
    public ResponseEntity<CouponValidationResponse> validateCoupon(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody ValidateCouponRequest req
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        return ResponseEntity.ok(billingService.validateCoupon(user.getId(), req != null ? req.code() : null, req != null ? req.planId() : null));
    }

    @PostMapping("/redeem-coupon")
    public ResponseEntity<RedeemCouponResponse> redeemCoupon(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody RedeemCouponRequest req
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        return ResponseEntity.ok(billingService.redeemCoupon(user, req != null ? req.code() : null, req != null ? req.planId() : null));
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
}

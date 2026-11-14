package com.forma.backend.dto;

import com.forma.backend.entity.*;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class BillingDto {

    // MARK: - Plans
    public record PlanSummaryDto(
        String id,
        String name,
        String description,
        String storekitProductId,
        String billingInterval,
        int priceCents,
        String currency,
        int defaultTrialDays,
        boolean active
    ) {
        public static PlanSummaryDto fromEntity(SubscriptionPlanEntity e) {
            return new PlanSummaryDto(
                e.getId(),
                e.getName(),
                e.getDescription(),
                e.getStorekitProductId(),
                e.getBillingInterval(),
                e.getPriceCents(),
                e.getCurrency(),
                e.getDefaultTrialDays(),
                e.isActive()
            );
        }
    }

    public record UpdatePlanRequest(
        String name,
        String description,
        Integer priceCents,
        Integer defaultTrialDays,
        Boolean active
    ) {}

    // MARK: - Coupons
    public record CreateOrUpdateCouponRequest(
        String code,
        String description,
        String couponType,
        Integer discountPercent,
        Integer discountCents,
        Integer freePeriodDays,
        String applicablePlanId,
        Integer maxRedemptions,
        Integer perUserLimit,
        String expiresAtUtc,
        Boolean active
    ) {}

    public record CouponSummaryDto(
        String id,
        String code,
        String description,
        String couponType,
        Integer discountPercent,
        Integer discountCents,
        Integer freePeriodDays,
        String applicablePlanId,
        Integer maxRedemptions,
        int currentRedemptions,
        int perUserLimit,
        String expiresAtUtc,
        boolean active,
        String createdByAdmin,
        String createdAtUtc
    ) {
        public static CouponSummaryDto fromEntity(CouponEntity c) {
            return new CouponSummaryDto(
                c.getId().toString(),
                c.getCode(),
                c.getDescription(),
                c.getCouponType(),
                c.getDiscountPercent(),
                c.getDiscountCents(),
                c.getFreePeriodDays(),
                c.getApplicablePlanId(),
                c.getMaxRedemptions(),
                c.getCurrentRedemptions(),
                c.getPerUserLimit(),
                c.getExpiresAtUtc() != null ? c.getExpiresAtUtc().toString() : null,
                c.isActive(),
                c.getCreatedByAdmin(),
                c.getCreatedAtUtc() != null ? c.getCreatedAtUtc().toString() : null
            );
        }
    }

    public record CouponRedemptionDto(
        String id,
        String couponCode,
        String userId,
        String userEmail,
        String benefitSummary,
        String redeemedAtUtc
    ) {
        public static CouponRedemptionDto fromEntity(CouponRedemptionEntity r) {
            return new CouponRedemptionDto(
                r.getId().toString(),
                r.getCouponCode(),
                r.getUserId().toString(),
                r.getUserEmail(),
                r.getBenefitSummary(),
                r.getRedeemedAtUtc() != null ? r.getRedeemedAtUtc().toString() : null
            );
        }
    }

    public record ValidateCouponRequest(String code, String planId) {}

    public record CouponValidationResponse(
        boolean valid,
        String code,
        String couponType,
        Integer freePeriodDays,
        Integer discountPercent,
        Integer discountCents,
        int originalPriceCents,
        int finalPriceCents,
        String benefitLabel,
        String message
    ) {}

    public record RedeemCouponRequest(String code, String planId) {}

    public record RedeemCouponResponse(
        boolean redeemed,
        String accountTier,
        String subscriptionStatus,
        String message,
        UserSubscriptionStatusDto subscription
    ) {}

    // MARK: - StoreKit & User Subscription Status
    public record SyncStoreKitRequest(
        String productId,
        String transactionId,
        String originalTransactionId,
        String purchaseDateUtc,
        String expiresDateUtc,
        Boolean isTrialPeriod,
        String couponCode,
        String environment,
        Integer paidAmountCents
    ) {}

    public record DunningNoticeDto(
        String id,
        String subscriptionId,
        String userId,
        String userEmail,
        int dayNumber,
        String noticeType,
        String title,
        String message,
        boolean websocketDelivered,
        boolean emailDelivered,
        String acknowledgedAtUtc,
        String createdAtUtc
    ) {
        public static DunningNoticeDto fromEntity(DunningNoticeEntity n) {
            return new DunningNoticeDto(
                n.getId().toString(),
                n.getSubscriptionId().toString(),
                n.getUserId().toString(),
                n.getUserEmail(),
                n.getDayNumber(),
                n.getNoticeType(),
                n.getTitle(),
                n.getMessage(),
                n.isWebsocketDelivered(),
                n.isEmailDelivered(),
                n.getAcknowledgedAtUtc() != null ? n.getAcknowledgedAtUtc().toString() : null,
                n.getCreatedAtUtc() != null ? n.getCreatedAtUtc().toString() : null
            );
        }
    }

    public record UserSubscriptionStatusDto(
        String id,
        String userId,
        String userEmail,
        String accountTier,
        String planId,
        String planName,
        String source,
        String status,
        int priceCents,
        String currency,
        String appliedCouponCode,
        boolean autoRenew,
        String externalTransactionId,
        String currentPeriodStartUtc,
        String currentPeriodEndUtc,
        String trialEndsAtUtc,
        String overdueSinceUtc,
        String graceEndsAtUtc,
        int dunningWarningsSent,
        int lastWarningDaySent,
        String overdueActionEffective,
        String statusReason,
        boolean pastDue,
        Integer overdueDay,
        String activeWarningMessage,
        String updatedAtUtc,
        List<DunningNoticeDto> recentNotices
    ) {
        public static UserSubscriptionStatusDto fromEntity(
            UserSubscriptionEntity s,
            String accountTier,
            String effectiveOverdueAction,
            Integer overdueDay,
            String activeWarningMessage,
            List<DunningNoticeDto> notices
        ) {
            return new UserSubscriptionStatusDto(
                s.getId().toString(),
                s.getUserId().toString(),
                s.getUserEmail(),
                accountTier,
                s.getPlanId(),
                s.getPlanName(),
                s.getSource(),
                s.getStatus(),
                s.getPriceCents(),
                s.getCurrency(),
                s.getAppliedCouponCode(),
                s.isAutoRenew(),
                s.getExternalTransactionId(),
                s.getCurrentPeriodStartUtc() != null ? s.getCurrentPeriodStartUtc().toString() : null,
                s.getCurrentPeriodEndUtc() != null ? s.getCurrentPeriodEndUtc().toString() : null,
                s.getTrialEndsAtUtc() != null ? s.getTrialEndsAtUtc().toString() : null,
                s.getOverdueSinceUtc() != null ? s.getOverdueSinceUtc().toString() : null,
                s.getGraceEndsAtUtc() != null ? s.getGraceEndsAtUtc().toString() : null,
                s.getDunningWarningsSent(),
                s.getLastWarningDaySent(),
                effectiveOverdueAction,
                s.getStatusReason(),
                "PAST_DUE".equalsIgnoreCase(s.getStatus()),
                overdueDay,
                activeWarningMessage,
                s.getUpdatedAtUtc() != null ? s.getUpdatedAtUtc().toString() : null,
                notices
            );
        }
    }

    // MARK: - Admin Subscription Actions
    public record AdminGrantSubscriptionRequest(
        UUID userId,
        String userEmail,
        String planId,
        Integer periodDays,
        Boolean isTrial,
        Integer customPriceCents,
        String paymentMethod,
        String couponCode,
        Boolean autoRenew,
        String overdueActionOverride,
        String note
    ) {}

    public record AdminSubscriptionActionRequest(
        String action,
        Integer extendDays,
        String overdueActionOverride,
        String reason
    ) {}

    public record SimulateOverdueRequest(
        Integer dayStage,
        Integer hoursOverdue
    ) {}

    public record DunningRunSummaryDto(
        int evaluatedCount,
        int warningsSentCount,
        int autoPausedCount,
        int autoCanceledCount,
        int expiredTrialsCount,
        String executedAtUtc
    ) {}

    // MARK: - Financial Entries
    public record CreatePaymentEntryRequest(
        UUID userId,
        String userEmail,
        UUID subscriptionId,
        String entryType,
        String status,
        Integer grossAmountCents,
        Integer discountCents,
        String paymentMethod,
        String couponCode,
        String referenceCode,
        String description,
        String paidAtUtc
    ) {}

    public record UpdatePaymentEntryStatusRequest(String status, String reason) {}

    public record PaymentEntryDto(
        String id,
        String userId,
        String userEmail,
        String subscriptionId,
        String entryType,
        String status,
        int grossAmountCents,
        int discountCents,
        int netAmountCents,
        String currency,
        String paymentMethod,
        String couponCode,
        String referenceCode,
        String description,
        String paidAtUtc,
        String dueAtUtc,
        String createdBy,
        String createdAtUtc
    ) {
        public static PaymentEntryDto fromEntity(PaymentEntryEntity p) {
            return new PaymentEntryDto(
                p.getId().toString(),
                p.getUserId() != null ? p.getUserId().toString() : null,
                p.getUserEmail(),
                p.getSubscriptionId() != null ? p.getSubscriptionId().toString() : null,
                p.getEntryType(),
                p.getStatus(),
                p.getGrossAmountCents(),
                p.getDiscountCents(),
                p.getNetAmountCents(),
                p.getCurrency(),
                p.getPaymentMethod(),
                p.getCouponCode(),
                p.getReferenceCode(),
                p.getDescription(),
                p.getPaidAtUtc() != null ? p.getPaidAtUtc().toString() : null,
                p.getDueAtUtc() != null ? p.getDueAtUtc().toString() : null,
                p.getCreatedBy(),
                p.getCreatedAtUtc() != null ? p.getCreatedAtUtc().toString() : null
            );
        }
    }

    // MARK: - Webhooks
    public record WebhookEventDto(
        String id,
        String eventId,
        String provider,
        String eventType,
        boolean signatureValid,
        String processingStatus,
        String targetUserEmail,
        String payloadExcerpt,
        String errorMessage,
        String receivedAtUtc
    ) {
        public static WebhookEventDto fromEntity(BillingWebhookEventEntity w) {
            return new WebhookEventDto(
                w.getId().toString(),
                w.getEventId(),
                w.getProvider(),
                w.getEventType(),
                w.isSignatureValid(),
                w.getProcessingStatus(),
                w.getTargetUserEmail(),
                w.getPayloadExcerpt(),
                w.getErrorMessage(),
                w.getReceivedAtUtc() != null ? w.getReceivedAtUtc().toString() : null
            );
        }
    }

    public record WebhookConfigDto(
        String endpointPath,
        String signatureHeader,
        String maskedSecret,
        String rotatedPlaintextSecret
    ) {}

    public record SimulateWebhookRequest(
        String provider,
        String eventType,
        String userEmail,
        String planId,
        Integer amountCents
    ) {}

    public record BillingSettingsRequest(String overdueAction) {}

    public record DailyRevenuePoint(
        String dateIso,
        String label,
        long revenueCents,
        long entriesCount
    ) {}

    // MARK: - Consolidated Admin Billing Dashboard
    public record BillingDashboardResponse(
        long mrrCents,
        long arrCents,
        long paidRevenue30dCents,
        long totalNetRevenueCents,
        long pendingRevenueCents,
        long refundedTotalCents,
        long activeSubscribersCount,
        long trialingCount,
        long pastDueDay1Count,
        long pastDueDay2Count,
        long pastDueDay3Count,
        long pausedCount,
        long canceledCount,
        long couponRedemptionsCount,
        String overdueActionPolicy,
        int graceDays,
        List<DailyRevenuePoint> dailyRevenueLast14Days,
        List<PlanSummaryDto> plans,
        List<UserSubscriptionStatusDto> subscriptions,
        List<CouponSummaryDto> coupons,
        List<CouponRedemptionDto> recentRedemptions,
        List<PaymentEntryDto> recentEntries,
        List<DunningNoticeDto> recentDunningNotices,
        List<WebhookEventDto> recentWebhooks,
        WebhookConfigDto webhookConfig
    ) {}
}

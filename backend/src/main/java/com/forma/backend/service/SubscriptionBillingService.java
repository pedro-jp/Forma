package com.forma.backend.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forma.backend.dto.BillingDto.*;
import com.forma.backend.entity.*;
import com.forma.backend.exception.DomainExceptions.ResourceNotFoundException;
import com.forma.backend.exception.DomainExceptions.UnauthorizedAccessException;
import com.forma.backend.repository.*;
import com.forma.backend.websocket.AdminEventPublisherService;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class SubscriptionBillingService {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionBillingService.class);
    private static final long THREE_DAYS_GRACE_HOURS = 72L;

    private final SubscriptionPlanRepository planRepository;
    private final CouponRepository couponRepository;
    private final CouponRedemptionRepository redemptionRepository;
    private final UserSubscriptionRepository subscriptionRepository;
    private final PaymentEntryRepository paymentEntryRepository;
    private final BillingWebhookEventRepository webhookEventRepository;
    private final DunningNoticeRepository dunningNoticeRepository;
    private final UserRepository userRepository;
    private final AppConfigService appConfigService;
    private final AdminEventPublisherService eventPublisherService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final HttpClient httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(8)).build();

    @Autowired
    public SubscriptionBillingService(
        SubscriptionPlanRepository planRepository,
        CouponRepository couponRepository,
        CouponRedemptionRepository redemptionRepository,
        UserSubscriptionRepository subscriptionRepository,
        PaymentEntryRepository paymentEntryRepository,
        BillingWebhookEventRepository webhookEventRepository,
        DunningNoticeRepository dunningNoticeRepository,
        UserRepository userRepository,
        AppConfigService appConfigService,
        AdminEventPublisherService eventPublisherService
    ) {
        this.planRepository = planRepository;
        this.couponRepository = couponRepository;
        this.redemptionRepository = redemptionRepository;
        this.subscriptionRepository = subscriptionRepository;
        this.paymentEntryRepository = paymentEntryRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.dunningNoticeRepository = dunningNoticeRepository;
        this.userRepository = userRepository;
        this.appConfigService = appConfigService;
        this.eventPublisherService = eventPublisherService;
    }

    @PostConstruct
    public void ensureDefaultPlans() {
        try {
            if (!planRepository.existsById("PRO_MONTHLY")) {
                planRepository.save(new SubscriptionPlanEntity(
                    "PRO_MONTHLY",
                    "Forma Pro Mensal",
                    "Acesso completo à IA nutricional, análise visual de pratos, treinos e evolução em tempo real.",
                    "jota.Forma.pro.monthly",
                    "MONTHLY",
                    2490,
                    "BRL",
                    0,
                    true
                ));
            }
            if (!planRepository.existsById("PRO_YEARLY")) {
                planRepository.save(new SubscriptionPlanEntity(
                    "PRO_YEARLY",
                    "Forma Pro Anual",
                    "Economia anual com todos os recursos de IA do Forma e 7 dias grátis na adesão.",
                    "jota.Forma.pro.yearly",
                    "YEARLY",
                    19990,
                    "BRL",
                    7,
                    true
                ));
            }
        } catch (Exception e) {
            log.warn("[Billing] Aviso ao inicializar planos padrão: {}", e.getMessage());
        }
    }

    // MARK: - Public & User Plans / Status

    public List<PlanSummaryDto> listActivePlans() {
        ensureDefaultPlans();
        return planRepository.findByActiveTrueOrderByPriceCentsAsc().stream()
            .map(PlanSummaryDto::fromEntity)
            .toList();
    }

    public UserSubscriptionStatusDto getUserSubscriptionStatus(User user) {
        ensureDefaultPlans();
        Optional<UserSubscriptionEntity> opt = subscriptionRepository.findByUserId(user.getId());
        List<DunningNoticeDto> notices = dunningNoticeRepository.findTop10ByUserIdOrderByCreatedAtUtcDesc(user.getId())
            .stream()
            .map(DunningNoticeDto::fromEntity)
            .toList();

        if (opt.isEmpty()) {
            return new UserSubscriptionStatusDto(
                null,
                user.getId().toString(),
                user.getEmail(),
                user.getAccountTier(),
                "FREE",
                user.isPro() ? "Forma Pro (Liberado pelo Painel)" : "Camada Essencial (Gratuita)",
                "NONE",
                user.isPro() ? "ACTIVE" : "FREE",
                0,
                "BRL",
                null,
                false,
                null,
                null,
                null,
                null,
                null,
                null,
                0,
                0,
                appConfigService.getBillingOverdueAction(),
                user.isPro() ? "Acesso Pro ativo." : "Sem assinatura paga vinculada.",
                false,
                null,
                null,
                null,
                notices
            );
        }

        return toSubscriptionDto(opt.get(), user.getAccountTier(), notices);
    }

    @Transactional
    public void acknowledgeLatestWarnings(User user) {
        List<DunningNoticeEntity> notices = dunningNoticeRepository.findTop10ByUserIdOrderByCreatedAtUtcDesc(user.getId());
        Instant now = Instant.now();
        for (DunningNoticeEntity n : notices) {
            if (n.getAcknowledgedAtUtc() == null) {
                n.setAcknowledgedAtUtc(now);
                dunningNoticeRepository.save(n);
            }
        }
    }

    // MARK: - Coupon Validation & Redemption

    public CouponValidationResponse validateCoupon(UUID userId, String rawCode, String planId) {
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Informe um código de cupom.");
        }
        String normalizedCode = rawCode.trim().toUpperCase(Locale.ROOT);
        CouponEntity coupon = couponRepository.findByCodeIgnoreCase(normalizedCode)
            .orElseThrow(() -> new ResourceNotFoundException("Cupom \"" + normalizedCode + "\" não encontrado."));

        SubscriptionPlanEntity plan = resolvePlanOrDefault(planId);
        validateCouponEligibility(coupon, userId, plan.getId());

        int originalCents = plan.getPriceCents();
        int finalCents = calculateFinalPriceCents(originalCents, coupon);
        String benefitLabel = buildCouponBenefitLabel(coupon);

        return new CouponValidationResponse(
            true,
            coupon.getCode(),
            coupon.getCouponType(),
            coupon.getFreePeriodDays(),
            coupon.getDiscountPercent(),
            coupon.getDiscountCents(),
            originalCents,
            finalCents,
            benefitLabel,
            "Cupom válido: " + benefitLabel
        );
    }

    @Transactional
    public RedeemCouponResponse redeemCoupon(User user, String rawCode, String planId) {
        ensureDefaultPlans();
        if (rawCode == null || rawCode.isBlank()) {
            throw new IllegalArgumentException("Informe o código do cupom para resgate.");
        }

        String normalizedCode = rawCode.trim().toUpperCase(Locale.ROOT);
        CouponEntity coupon = couponRepository.findByCodeIgnoreCase(normalizedCode)
            .orElseThrow(() -> new ResourceNotFoundException("Cupom \"" + normalizedCode + "\" não encontrado."));

        SubscriptionPlanEntity plan = resolvePlanOrDefault(planId != null ? planId : coupon.getApplicablePlanId());
        validateCouponEligibility(coupon, user.getId(), plan.getId());

        String type = coupon.getCouponType().toUpperCase(Locale.ROOT);
        boolean grantsFreeAccessNow = "FREE_PERIOD_DAYS".equals(type)
            || ("PERCENT_DISCOUNT".equals(type) && coupon.getDiscountPercent() != null && coupon.getDiscountPercent() >= 100);

        Instant now = Instant.now();
        UserSubscriptionEntity sub = subscriptionRepository.findByUserId(user.getId())
            .orElseGet(UserSubscriptionEntity::new);
        sub.setUserId(user.getId());
        sub.setUserEmail(user.getEmail());
        sub.setPlanId(plan.getId());
        sub.setPlanName(plan.getName());
        sub.setAppliedCouponCode(coupon.getCode());
        sub.setUpdatedAtUtc(now);

        String benefitSummary;
        String responseMessage;
        if (grantsFreeAccessNow) {
            int days = (coupon.getFreePeriodDays() != null && coupon.getFreePeriodDays() > 0)
                ? coupon.getFreePeriodDays()
                : 30;
            Instant baseEnd = (sub.getCurrentPeriodEndUtc() != null && sub.getCurrentPeriodEndUtc().isAfter(now))
                ? sub.getCurrentPeriodEndUtc()
                : now;
            Instant targetEnd = baseEnd.plus(Duration.ofDays(days));

            sub.setSource("COUPON_FREE_PERIOD");
            sub.setStatus("TRIALING");
            sub.setPriceCents(0);
            sub.setCurrency(plan.getCurrency());
            sub.setAutoRenew(false);
            sub.setCurrentPeriodStartUtc(now);
            sub.setCurrentPeriodEndUtc(targetEnd);
            sub.setTrialEndsAtUtc(targetEnd);
            clearOverdueFields(sub);
            sub.setStatusReason("Período grátis ativado pelo cupom " + coupon.getCode() + " (" + days + " dias)");
            sub = subscriptionRepository.save(sub);

            user.setAccountTier("PRO");
            userRepository.save(user);

            benefitSummary = days + " dias grátis no Forma Pro";
            responseMessage = "Cupom " + coupon.getCode() + " resgatado! Você recebeu " + days + " dias grátis de Forma Pro.";

            PaymentEntryEntity entry = new PaymentEntryEntity();
            entry.setUserId(user.getId());
            entry.setUserEmail(user.getEmail());
            entry.setSubscriptionId(sub.getId());
            entry.setEntryType("COUPON_TRIAL_GRANT");
            entry.setStatus("PAID");
            entry.setGrossAmountCents(plan.getPriceCents());
            entry.setDiscountCents(plan.getPriceCents());
            entry.setNetAmountCents(0);
            entry.setCurrency(plan.getCurrency());
            entry.setPaymentMethod("COUPON");
            entry.setCouponCode(coupon.getCode());
            entry.setReferenceCode("CPN-" + coupon.getCode() + "-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            entry.setDescription("Ativação de " + days + " dias grátis via cupom " + coupon.getCode());
            entry.setPaidAtUtc(now);
            entry.setCreatedBy(user.getEmail());
            paymentEntryRepository.save(entry);

            eventPublisherService.publishUserSubscriptionState(
                user,
                "SUBSCRIPTION_ACTIVATED",
                sub.getStatus(),
                null,
                null,
                responseMessage
            );
        } else {
            int discountedCents = calculateFinalPriceCents(plan.getPriceCents(), coupon);
            if (sub.getId() == null || sub.getCreatedAtUtc() == null) {
                sub.setCreatedAtUtc(now);
            }
            if (!"ACTIVE".equalsIgnoreCase(sub.getStatus()) && !"TRIALING".equalsIgnoreCase(sub.getStatus())) {
                sub.setSource("COUPON_DISCOUNT_PENDING");
                sub.setStatus("PENDING");
            }
            sub.setPriceCents(discountedCents);
            sub.setStatusReason("Cupom promocional " + coupon.getCode() + " vinculado ao plano " + plan.getName());
            sub = subscriptionRepository.save(sub);

            benefitSummary = buildCouponBenefitLabel(coupon);
            responseMessage = "Cupom " + coupon.getCode() + " aplicado (" + benefitSummary + "). Valor ajustado para o plano " + plan.getName() + ".";
        }

        coupon.setCurrentRedemptions(coupon.getCurrentRedemptions() + 1);
        couponRepository.save(coupon);

        CouponRedemptionEntity redemption = new CouponRedemptionEntity();
        redemption.setCouponId(coupon.getId());
        redemption.setCouponCode(coupon.getCode());
        redemption.setUserId(user.getId());
        redemption.setUserEmail(user.getEmail());
        redemption.setSubscriptionId(sub.getId());
        redemption.setBenefitSummary(benefitSummary);
        redemption.setRedeemedAtUtc(now);
        redemptionRepository.save(redemption);

        eventPublisherService.publishBillingUpdated("COUPON_REDEEMED:" + coupon.getCode());

        List<DunningNoticeDto> notices = dunningNoticeRepository.findTop10ByUserIdOrderByCreatedAtUtcDesc(user.getId())
            .stream().map(DunningNoticeDto::fromEntity).toList();
        return new RedeemCouponResponse(
            true,
            user.getAccountTier(),
            sub.getStatus(),
            responseMessage,
            toSubscriptionDto(sub, user.getAccountTier(), notices)
        );
    }

    // MARK: - StoreKit 2 Local / Apple Sync

    @Transactional
    public UserSubscriptionStatusDto syncStoreKitTransaction(User user, SyncStoreKitRequest req) {
        ensureDefaultPlans();
        if (req == null || req.productId() == null || req.productId().isBlank()) {
            throw new IllegalArgumentException("Identificador do produto StoreKit obrigatório.");
        }

        SubscriptionPlanEntity plan = planRepository.findByStorekitProductId(req.productId().trim())
            .orElseGet(() -> resolvePlanOrDefault(
                req.productId().toLowerCase(Locale.ROOT).contains("year") ? "PRO_YEARLY" : "PRO_MONTHLY"
            ));

        Instant now = Instant.now();
        Instant startAt = parseInstantOrDefault(req.purchaseDateUtc(), now);
        Duration defaultDuration = "YEARLY".equalsIgnoreCase(plan.getBillingInterval())
            ? Duration.ofDays(365)
            : Duration.ofDays(30);
        Instant endAt = parseInstantOrDefault(req.expiresDateUtc(), startAt.plus(defaultDuration));
        boolean isTrial = Boolean.TRUE.equals(req.isTrialPeriod());

        int paidCents = req.paidAmountCents() != null
            ? Math.max(0, req.paidAmountCents())
            : (isTrial ? 0 : plan.getPriceCents());
        int discountCents = Math.max(0, plan.getPriceCents() - paidCents);

        UserSubscriptionEntity sub = subscriptionRepository.findByUserId(user.getId())
            .orElseGet(UserSubscriptionEntity::new);
        sub.setUserId(user.getId());
        sub.setUserEmail(user.getEmail());
        sub.setPlanId(plan.getId());
        sub.setPlanName(plan.getName());
        sub.setSource("APPLE_STOREKIT");
        sub.setStatus(isTrial ? "TRIALING" : "ACTIVE");
        sub.setPriceCents(paidCents);
        sub.setCurrency(plan.getCurrency());
        sub.setAutoRenew(true);
        if (req.couponCode() != null && !req.couponCode().isBlank()) {
            sub.setAppliedCouponCode(req.couponCode().trim().toUpperCase(Locale.ROOT));
        }
        String txId = (req.originalTransactionId() != null && !req.originalTransactionId().isBlank())
            ? req.originalTransactionId().trim()
            : req.transactionId();
        sub.setExternalTransactionId(txId);
        sub.setCurrentPeriodStartUtc(startAt);
        sub.setCurrentPeriodEndUtc(endAt);
        if (isTrial) {
            sub.setTrialEndsAtUtc(endAt);
        }
        clearOverdueFields(sub);
        String env = (req.environment() != null && !req.environment().isBlank()) ? req.environment().trim() : "Xcode";
        sub.setStatusReason("Assinatura Apple StoreKit 2 sincronizada (" + env + ")");
        sub.setUpdatedAtUtc(now);
        sub = subscriptionRepository.save(sub);

        String ledgerRef = (req.transactionId() != null && !req.transactionId().isBlank())
            ? "SK2-" + req.transactionId().trim()
            : "SK2-" + UUID.randomUUID().toString().substring(0, 10);

        if (!paymentEntryRepository.existsByReferenceCode(ledgerRef)) {
            PaymentEntryEntity entry = new PaymentEntryEntity();
            entry.setUserId(user.getId());
            entry.setUserEmail(user.getEmail());
            entry.setSubscriptionId(sub.getId());
            entry.setEntryType(isTrial ? "COUPON_TRIAL_GRANT" : "SUBSCRIPTION_CHARGE");
            entry.setStatus("PAID");
            entry.setGrossAmountCents(plan.getPriceCents());
            entry.setDiscountCents(discountCents);
            entry.setNetAmountCents(paidCents);
            entry.setCurrency(plan.getCurrency());
            entry.setPaymentMethod("APPLE_IAP");
            entry.setCouponCode(sub.getAppliedCouponCode());
            entry.setReferenceCode(ledgerRef);
            entry.setDescription(plan.getName() + " • Apple StoreKit (" + env + ")");
            entry.setPaidAtUtc(startAt);
            entry.setCreatedBy("StoreKit2");
            paymentEntryRepository.save(entry);
        }

        user.setAccountTier("PRO");
        userRepository.save(user);

        eventPublisherService.publishUserSubscriptionState(
            user,
            "SUBSCRIPTION_ACTIVATED",
            sub.getStatus(),
            null,
            null,
            "Forma Pro ativo! Assinatura sincronizada com sucesso."
        );
        eventPublisherService.publishBillingUpdated("STOREKIT_SYNC:" + plan.getId());

        List<DunningNoticeDto> notices = dunningNoticeRepository.findTop10ByUserIdOrderByCreatedAtUtcDesc(user.getId())
            .stream().map(DunningNoticeDto::fromEntity).toList();
        return toSubscriptionDto(sub, user.getAccountTier(), notices);
    }

    // MARK: - 3-Day Overdue / Dunning Engine (Scheduler & On-Demand)

    @Scheduled(fixedDelayString = "${app.billing.dunning-interval-ms:1800000}", initialDelay = 45000)
    @Transactional
    public DunningRunSummaryDto evaluateOverdueSubscriptions() {
        Instant now = Instant.now();
        int evaluated = 0;
        int warnings = 0;
        int autoPaused = 0;
        int autoCanceled = 0;
        int expiredTrials = 0;

        List<UserSubscriptionEntity> candidates = subscriptionRepository.findByStatusIn(
            List.of("ACTIVE", "TRIALING", "PAST_DUE")
        );

        for (UserSubscriptionEntity sub : candidates) {
            evaluated++;
            User user = userRepository.findById(sub.getUserId()).orElse(null);

            if ("TRIALING".equalsIgnoreCase(sub.getStatus())
                && sub.getCurrentPeriodEndUtc() != null
                && now.isAfter(sub.getCurrentPeriodEndUtc())) {

                if (!sub.isAutoRenew()) {
                    sub.setStatus("EXPIRED");
                    sub.setStatusReason("Período grátis ou cupom encerrado em " + formatShortDate(sub.getCurrentPeriodEndUtc()));
                    sub.setUpdatedAtUtc(now);
                    subscriptionRepository.save(sub);
                    expiredTrials++;

                    if (user != null) {
                        user.setAccountTier("FREE");
                        userRepository.save(user);
                        eventPublisherService.publishUserSubscriptionState(
                            user,
                            "SUBSCRIPTION_EXPIRED",
                            "EXPIRED",
                            null,
                            null,
                            "Seu período grátis do Forma Pro terminou. Assine ou aplique outro cupom para continuar com a IA."
                        );
                    }
                    continue;
                } else {
                    transitionIntoOverdue(sub, sub.getCurrentPeriodEndUtc(), "Renovação após período grátis pendente", now);
                }
            }

            if ("ACTIVE".equalsIgnoreCase(sub.getStatus())
                && sub.getCurrentPeriodEndUtc() != null
                && now.isAfter(sub.getCurrentPeriodEndUtc())) {
                transitionIntoOverdue(sub, sub.getCurrentPeriodEndUtc(), "Fatura de renovação em aberto", now);
            }

            if ("PAST_DUE".equalsIgnoreCase(sub.getStatus())) {
                if (sub.getOverdueSinceUtc() == null) {
                    sub.setOverdueSinceUtc(now);
                }
                if (sub.getGraceEndsAtUtc() == null) {
                    sub.setGraceEndsAtUtc(sub.getOverdueSinceUtc().plus(Duration.ofHours(THREE_DAYS_GRACE_HOURS)));
                }

                // Passou dos 3 dias (72 horas) -> Pausa ou Cancela automaticamente
                if (!now.isBefore(sub.getGraceEndsAtUtc())) {
                    String action = resolveEffectiveOverdueAction(sub);
                    boolean cancel = "CANCEL".equalsIgnoreCase(action);
                    if (cancel) {
                        sub.setStatus("CANCELED");
                        sub.setCanceledAtUtc(now);
                        sub.setStatusReason("Assinatura cancelada automaticamente após 3 dias de atraso sem regularização.");
                        autoCanceled++;
                    } else {
                        sub.setStatus("PAUSED");
                        sub.setPausedAtUtc(now);
                        sub.setStatusReason("Assinatura pausada automaticamente após 3 dias de atraso sem regularização.");
                        autoPaused++;
                    }
                    sub.setUpdatedAtUtc(now);
                    subscriptionRepository.save(sub);

                    String title = cancel ? "Assinatura Forma Pro Cancelada" : "Assinatura Forma Pro Pausada";
                    String msg = cancel
                        ? "Após 3 dias de avisos sem pagamento, sua assinatura foi cancelada e a conta voltou para a Camada Essencial."
                        : "Após 3 dias de avisos sem pagamento, sua assinatura foi pausada e os recursos de IA foram bloqueados até regularização.";

                    DunningNoticeEntity notice = saveDunningNotice(
                        sub,
                        user,
                        4,
                        cancel ? "AUTO_CANCELED_AFTER_3_DAYS" : "AUTO_PAUSED_AFTER_3_DAYS",
                        title,
                        msg
                    );
                    if (user != null) {
                        user.setAccountTier("FREE");
                        userRepository.save(user);
                        eventPublisherService.publishUserSubscriptionState(
                            user,
                            "SUBSCRIPTION_ENFORCED",
                            sub.getStatus(),
                            4,
                            sub.getGraceEndsAtUtc().toString(),
                            msg
                        );
                        notice.setWebsocketDelivered(true);
                        dunningNoticeRepository.save(notice);
                    }
                    continue;
                }

                // Dentro da régua de 3 dias (Dia 1: 0..23h, Dia 2: 24..47h, Dia 3: 48..71h)
                long hoursElapsed = Math.max(0, Duration.between(sub.getOverdueSinceUtc(), now).toHours());
                int currentDay = (int) Math.min(3, (hoursElapsed / 24L) + 1L);

                if (sub.getLastWarningDaySent() < currentDay) {
                    String actionVerb = "CANCEL".equalsIgnoreCase(resolveEffectiveOverdueAction(sub)) ? "cancelada" : "pausada";
                    String title = buildWarningTitle(currentDay);
                    String msg = buildWarningMessage(currentDay, actionVerb, sub.getGraceEndsAtUtc());

                    DunningNoticeEntity notice = saveDunningNotice(
                        sub,
                        user,
                        currentDay,
                        "OVERDUE_DAY_" + currentDay,
                        title,
                        msg
                    );

                    sub.setLastWarningDaySent(currentDay);
                    sub.setDunningWarningsSent(sub.getDunningWarningsSent() + 1);
                    sub.setLastWarningAtUtc(now);
                    sub.setUpdatedAtUtc(now);
                    subscriptionRepository.save(sub);
                    warnings++;

                    if (user != null) {
                        eventPublisherService.publishUserSubscriptionState(
                            user,
                            "SUBSCRIPTION_OVERDUE_WARNING",
                            sub.getStatus(),
                            currentDay,
                            sub.getGraceEndsAtUtc().toString(),
                            msg
                        );
                        notice.setWebsocketDelivered(true);
                        dunningNoticeRepository.save(notice);
                    }
                }
            }
        }

        if (warnings > 0 || autoPaused > 0 || autoCanceled > 0 || expiredTrials > 0) {
            eventPublisherService.publishBillingUpdated("DUNNING_SWEEP");
        }

        return new DunningRunSummaryDto(evaluated, warnings, autoPaused, autoCanceled, expiredTrials, now.toString());
    }

    // MARK: - Signed Billing Webhooks (HMAC-SHA256 + Idempotency)

    @Transactional(noRollbackFor = {UnauthorizedAccessException.class, IllegalArgumentException.class})
    public WebhookEventDto processWebhook(String rawBody, String signatureHeader, boolean allowAdminInternalBypass) {
        String body = rawBody != null ? rawBody : "{}";
        String secret = appConfigService.getWebhookSecret();
        boolean signatureValid = allowAdminInternalBypass || verifyHmacSignature(body, signatureHeader, secret);

        String eventId = "evt_" + UUID.randomUUID();
        String provider = "CUSTOM_GATEWAY";
        String eventType = "UNKNOWN";
        String targetEmail = null;
        String userIdRaw = null;
        String externalTxId = null;
        String planId = null;
        Integer amountCents = null;

        try {
            JsonNode root = objectMapper.readTree(body);
            if (root.hasNonNull("eventId") && !root.path("eventId").asText().isBlank()) {
                eventId = root.path("eventId").asText().trim();
            } else if (root.hasNonNull("id") && !root.path("id").asText().isBlank()) {
                eventId = root.path("id").asText().trim();
            }
            if (root.hasNonNull("provider") && !root.path("provider").asText().isBlank()) {
                provider = root.path("provider").asText().trim().toUpperCase(Locale.ROOT);
            }
            if (root.hasNonNull("eventType") && !root.path("eventType").asText().isBlank()) {
                eventType = root.path("eventType").asText().trim().toUpperCase(Locale.ROOT);
            } else if (root.hasNonNull("type") && !root.path("type").asText().isBlank()) {
                eventType = root.path("type").asText().trim().toUpperCase(Locale.ROOT);
            }
            targetEmail = textOrNull(root, "userEmail");
            userIdRaw = textOrNull(root, "userId");
            externalTxId = textOrNull(root, "externalTransactionId");
            planId = textOrNull(root, "planId");
            if (root.hasNonNull("amountCents")) {
                amountCents = root.path("amountCents").asInt();
            }
        } catch (Exception e) {
            recordWebhookLog(eventId, provider, eventType, signatureValid, "INVALID_JSON", targetEmail, body, e.getMessage());
            throw new IllegalArgumentException("Payload JSON de webhook inválido.");
        }

        if (!signatureValid) {
            recordWebhookLog(eventId, provider, eventType, false, "REJECTED_SIGNATURE", targetEmail, body, "Assinatura HMAC-SHA256 ausente ou inválida");
            throw new UnauthorizedAccessException("Assinatura de webhook inválida.");
        }

        if (webhookEventRepository.existsByEventId(eventId)) {
            BillingWebhookEventEntity dup = recordWebhookLog(
                eventId + "_dup_" + System.currentTimeMillis(),
                provider,
                eventType,
                true,
                "IGNORED_DUPLICATE",
                targetEmail,
                body,
                "Evento já processado anteriormente (" + eventId + ")"
            );
            return WebhookEventDto.fromEntity(dup);
        }

        Optional<User> userOpt = resolveWebhookUser(userIdRaw, targetEmail, externalTxId);
        if (userOpt.isEmpty()) {
            recordWebhookLog(eventId, provider, eventType, true, "IGNORED_USER_NOT_FOUND", targetEmail, body, "Usuário não localizado para este evento");
            return WebhookEventDto.fromEntity(webhookEventRepository.findTop100ByOrderByReceivedAtUtcDesc().get(0));
        }

        User user = userOpt.get();
        targetEmail = user.getEmail();
        SubscriptionPlanEntity plan = resolvePlanOrDefault(planId);
        Instant now = Instant.now();

        UserSubscriptionEntity sub = subscriptionRepository.findByUserId(user.getId())
            .orElseGet(() -> {
                UserSubscriptionEntity created = new UserSubscriptionEntity();
                created.setUserId(user.getId());
                created.setUserEmail(user.getEmail());
                created.setPlanId(plan.getId());
                created.setPlanName(plan.getName());
                created.setSource("EXTERNAL_GATEWAY");
                created.setStatus("ACTIVE");
                created.setPriceCents(plan.getPriceCents());
                return created;
            });

        String processingStatus = "PROCESSED";
        switch (eventType) {
            case "PAYMENT_SUCCEEDED", "SUBSCRIPTION_RENEWED", "DID_RENEW", "SUBSCRIBED" -> {
                Duration period = "YEARLY".equalsIgnoreCase(plan.getBillingInterval())
                    ? Duration.ofDays(365) : Duration.ofDays(30);
                int effectiveCents = amountCents != null ? Math.max(0, amountCents) : plan.getPriceCents();
                sub.setPlanId(plan.getId());
                sub.setPlanName(plan.getName());
                sub.setStatus("ACTIVE");
                sub.setPriceCents(effectiveCents);
                sub.setCurrentPeriodStartUtc(now);
                sub.setCurrentPeriodEndUtc(now.plus(period));
                if (externalTxId != null) sub.setExternalTransactionId(externalTxId);
                clearOverdueFields(sub);
                sub.setStatusReason("Pagamento confirmado via webhook (" + provider + ")");
                sub.setUpdatedAtUtc(now);
                sub = subscriptionRepository.save(sub);

                user.setAccountTier("PRO");
                userRepository.save(user);

                PaymentEntryEntity entry = new PaymentEntryEntity();
                entry.setUserId(user.getId());
                entry.setUserEmail(user.getEmail());
                entry.setSubscriptionId(sub.getId());
                entry.setEntryType("SUBSCRIPTION_CHARGE");
                entry.setStatus("PAID");
                entry.setGrossAmountCents(effectiveCents);
                entry.setDiscountCents(0);
                entry.setNetAmountCents(effectiveCents);
                entry.setCurrency(plan.getCurrency());
                entry.setPaymentMethod("WEBHOOK");
                entry.setReferenceCode("WH-" + eventId);
                entry.setDescription(plan.getName() + " • Webhook " + eventType);
                entry.setPaidAtUtc(now);
                entry.setCreatedBy(provider);
                paymentEntryRepository.save(entry);

                eventPublisherService.publishUserSubscriptionState(
                    user, "SUBSCRIPTION_ACTIVATED", "ACTIVE", null, null,
                    "Pagamento confirmado! Seu Forma Pro foi renovado."
                );
            }
            case "PAYMENT_FAILED", "DID_FAIL_TO_RENEW", "BILLING_RETRY", "SUBSCRIPTION_OVERDUE" -> {
                transitionIntoOverdue(sub, now, "Falha de cobrança reportada via webhook (" + provider + ")", now);
                evaluateOverdueSubscriptions();
            }
            case "SUBSCRIPTION_PAUSED" -> {
                sub.setStatus("PAUSED");
                sub.setPausedAtUtc(now);
                sub.setStatusReason("Pausada via webhook (" + provider + ")");
                sub.setUpdatedAtUtc(now);
                subscriptionRepository.save(sub);
                user.setAccountTier("FREE");
                userRepository.save(user);
                eventPublisherService.publishUserSubscriptionState(
                    user, "SUBSCRIPTION_ENFORCED", "PAUSED", null, null,
                    "Sua assinatura foi pausada pelo provedor de pagamento."
                );
            }
            case "SUBSCRIPTION_CANCELED", "EXPIRED" -> {
                sub.setStatus("CANCELED");
                sub.setCanceledAtUtc(now);
                sub.setStatusReason("Cancelada via webhook (" + provider + ")");
                sub.setUpdatedAtUtc(now);
                subscriptionRepository.save(sub);
                user.setAccountTier("FREE");
                userRepository.save(user);
                eventPublisherService.publishUserSubscriptionState(
                    user, "SUBSCRIPTION_ENFORCED", "CANCELED", null, null,
                    "Sua assinatura Forma Pro foi cancelada."
                );
            }
            case "REFUNDED", "REFUND" -> {
                int refundCents = amountCents != null ? Math.max(0, amountCents) : Math.max(0, sub.getPriceCents());
                sub.setStatus("CANCELED");
                sub.setCanceledAtUtc(now);
                sub.setStatusReason("Estorno processado via webhook (" + provider + ")");
                sub.setUpdatedAtUtc(now);
                subscriptionRepository.save(sub);
                user.setAccountTier("FREE");
                userRepository.save(user);

                PaymentEntryEntity refund = new PaymentEntryEntity();
                refund.setUserId(user.getId());
                refund.setUserEmail(user.getEmail());
                refund.setSubscriptionId(sub.getId());
                refund.setEntryType("REFUND");
                refund.setStatus("REFUNDED");
                refund.setGrossAmountCents(refundCents);
                refund.setDiscountCents(0);
                refund.setNetAmountCents(refundCents);
                refund.setCurrency(plan.getCurrency());
                refund.setPaymentMethod("WEBHOOK");
                refund.setReferenceCode("REF-" + eventId);
                refund.setDescription("Estorno via webhook (" + provider + ")");
                refund.setPaidAtUtc(now);
                refund.setCreatedBy(provider);
                paymentEntryRepository.save(refund);

                eventPublisherService.publishUserSubscriptionState(
                    user, "SUBSCRIPTION_ENFORCED", "CANCELED", null, null,
                    "Reembolso confirmado. Conta retornada para a Camada Essencial."
                );
            }
            default -> processingStatus = "IGNORED_UNSUPPORTED_TYPE";
        }

        BillingWebhookEventEntity saved = recordWebhookLog(eventId, provider, eventType, true, processingStatus, targetEmail, body, null);
        eventPublisherService.publishBillingUpdated("WEBHOOK:" + eventType);
        return WebhookEventDto.fromEntity(saved);
    }

    // MARK: - Admin Management & Dashboard

    public BillingDashboardResponse getAdminDashboard(String justRotatedSecret) {
        ensureDefaultPlans();
        Instant now = Instant.now();
        Instant thirtyDaysAgo = now.minus(Duration.ofDays(30));

        List<SubscriptionPlanEntity> plans = planRepository.findAllByOrderByPriceCentsAsc();
        List<UserSubscriptionEntity> subs = subscriptionRepository.findAllByOrderByUpdatedAtUtcDesc();
        List<CouponEntity> coupons = couponRepository.findAllByOrderByCreatedAtUtcDesc();
        List<CouponRedemptionEntity> redemptions = redemptionRepository.findTop100ByOrderByRedeemedAtUtcDesc();
        List<PaymentEntryEntity> entries = paymentEntryRepository.findTop200ByOrderByCreatedAtUtcDesc();
        List<DunningNoticeEntity> notices = dunningNoticeRepository.findTop100ByOrderByCreatedAtUtcDesc();
        List<BillingWebhookEventEntity> webhooks = webhookEventRepository.findTop100ByOrderByReceivedAtUtcDesc();

        Map<UUID, User> usersById = new HashMap<>();
        userRepository.findAll().forEach(u -> usersById.put(u.getId(), u));

        long mrrCents = 0;
        long activeCount = 0;
        long trialingCount = 0;
        long pastDueDay1 = 0;
        long pastDueDay2 = 0;
        long pastDueDay3 = 0;
        long pausedCount = 0;
        long canceledCount = 0;

        List<UserSubscriptionStatusDto> subDtos = new ArrayList<>();
        for (UserSubscriptionEntity sub : subs) {
            User u = usersById.get(sub.getUserId());
            String tier = u != null ? u.getAccountTier() : "FREE";
            UserSubscriptionStatusDto dto = toSubscriptionDto(sub, tier, List.of());
            subDtos.add(dto);

            String st = sub.getStatus().toUpperCase(Locale.ROOT);
            if ("ACTIVE".equals(st)) {
                activeCount++;
                mrrCents += estimateMonthlyCents(sub, plans);
            } else if ("TRIALING".equals(st)) {
                trialingCount++;
            } else if ("PAST_DUE".equals(st)) {
                int day = dto.overdueDay() != null ? dto.overdueDay() : 1;
                if (day <= 1) pastDueDay1++;
                else if (day == 2) pastDueDay2++;
                else pastDueDay3++;
            } else if ("PAUSED".equals(st)) {
                pausedCount++;
            } else if ("CANCELED".equals(st) || "EXPIRED".equals(st)) {
                canceledCount++;
            }
        }

        long paid30d = 0;
        long totalNet = 0;
        long pendingCents = 0;
        long refundedCents = 0;

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Map<LocalDate, long[]> dailyBuckets = new LinkedHashMap<>();
        for (int i = 13; i >= 0; i--) {
            dailyBuckets.put(today.minusDays(i), new long[]{0L, 0L});
        }

        for (PaymentEntryEntity e : entries) {
            String st = e.getStatus().toUpperCase(Locale.ROOT);
            if ("PAID".equals(st)) {
                totalNet += e.getNetAmountCents();
                Instant refTime = e.getPaidAtUtc() != null ? e.getPaidAtUtc() : e.getCreatedAtUtc();
                if (refTime != null && !refTime.isBefore(thirtyDaysAgo)) {
                    paid30d += e.getNetAmountCents();
                }
                if (refTime != null) {
                    LocalDate day = refTime.atZone(ZoneOffset.UTC).toLocalDate();
                    long[] bucket = dailyBuckets.get(day);
                    if (bucket != null) {
                        bucket[0] += e.getNetAmountCents();
                        bucket[1] += 1;
                    }
                }
            } else if ("PENDING".equals(st) || "OVERDUE".equals(st)) {
                pendingCents += e.getNetAmountCents();
            } else if ("REFUNDED".equals(st)) {
                refundedCents += e.getNetAmountCents();
            }
        }

        long netRevenue = Math.max(0, totalNet - refundedCents);
        DateTimeFormatter brFmt = DateTimeFormatter.ofPattern("dd/MM");
        List<DailyRevenuePoint> dailySeries = dailyBuckets.entrySet().stream()
            .map(entry -> new DailyRevenuePoint(
                entry.getKey().toString(),
                entry.getKey().format(brFmt),
                entry.getValue()[0],
                entry.getValue()[1]
            ))
            .toList();

        return new BillingDashboardResponse(
            mrrCents,
            mrrCents * 12,
            paid30d,
            netRevenue,
            pendingCents,
            refundedCents,
            activeCount,
            trialingCount,
            pastDueDay1,
            pastDueDay2,
            pastDueDay3,
            pausedCount,
            canceledCount,
            redemptions.size(),
            appConfigService.getBillingOverdueAction(),
            3,
            dailySeries,
            plans.stream().map(PlanSummaryDto::fromEntity).toList(),
            subDtos,
            coupons.stream().map(CouponSummaryDto::fromEntity).toList(),
            redemptions.stream().map(CouponRedemptionDto::fromEntity).toList(),
            entries.stream().map(PaymentEntryDto::fromEntity).toList(),
            notices.stream().map(DunningNoticeDto::fromEntity).toList(),
            webhooks.stream().map(WebhookEventDto::fromEntity).toList(),
            new WebhookConfigDto(
                "/api/webhooks/billing",
                "X-Forma-Webhook-Signature",
                appConfigService.getMaskedWebhookSecret(),
                justRotatedSecret
            )
        );
    }

    @Transactional
    public PlanSummaryDto updatePlan(String planId, UpdatePlanRequest req) {
        ensureDefaultPlans();
        SubscriptionPlanEntity plan = planRepository.findById(planId)
            .orElseThrow(() -> new ResourceNotFoundException("Plano não encontrado: " + planId));
        if (req.name() != null && !req.name().isBlank()) plan.setName(req.name().trim());
        if (req.description() != null) plan.setDescription(req.description().trim());
        if (req.priceCents() != null && req.priceCents() >= 0) plan.setPriceCents(req.priceCents());
        if (req.defaultTrialDays() != null && req.defaultTrialDays() >= 0) plan.setDefaultTrialDays(req.defaultTrialDays());
        if (req.active() != null) plan.setActive(req.active());
        plan.setUpdatedAtUtc(Instant.now());
        SubscriptionPlanEntity saved = planRepository.save(plan);
        eventPublisherService.publishBillingUpdated("PLAN_UPDATED:" + planId);
        return PlanSummaryDto.fromEntity(saved);
    }

    @Transactional
    public CouponSummaryDto createCoupon(CreateOrUpdateCouponRequest req, String adminActor) {
        if (req == null || req.code() == null || req.code().isBlank()) {
            throw new IllegalArgumentException("Código do cupom obrigatório.");
        }
        String cleanCode = req.code().trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9_-]", "");
        if (cleanCode.length() < 3) {
            throw new IllegalArgumentException("O código do cupom deve ter ao menos 3 caracteres alfanuméricos.");
        }
        if (couponRepository.findByCodeIgnoreCase(cleanCode).isPresent()) {
            throw new IllegalStateException("Já existe um cupom com o código " + cleanCode + ".");
        }

        CouponEntity c = new CouponEntity();
        c.setCode(cleanCode);
        applyCouponFields(c, req);
        c.setCreatedByAdmin(adminActor);
        c.setCreatedAtUtc(Instant.now());
        CouponEntity saved = couponRepository.save(c);
        eventPublisherService.publishBillingUpdated("COUPON_CREATED:" + cleanCode);
        return CouponSummaryDto.fromEntity(saved);
    }

    @Transactional
    public CouponSummaryDto updateCoupon(UUID couponId, CreateOrUpdateCouponRequest req) {
        CouponEntity c = couponRepository.findById(couponId)
            .orElseThrow(() -> new ResourceNotFoundException("Cupom não encontrado."));
        applyCouponFields(c, req);
        CouponEntity saved = couponRepository.save(c);
        eventPublisherService.publishBillingUpdated("COUPON_UPDATED:" + saved.getCode());
        return CouponSummaryDto.fromEntity(saved);
    }

    @Transactional
    public CouponSummaryDto toggleCoupon(UUID couponId) {
        CouponEntity c = couponRepository.findById(couponId)
            .orElseThrow(() -> new ResourceNotFoundException("Cupom não encontrado."));
        c.setActive(!c.isActive());
        CouponEntity saved = couponRepository.save(c);
        eventPublisherService.publishBillingUpdated("COUPON_TOGGLED:" + saved.getCode());
        return CouponSummaryDto.fromEntity(saved);
    }

    @Transactional
    public void deleteCoupon(UUID couponId) {
        if (!couponRepository.existsById(couponId)) {
            throw new ResourceNotFoundException("Cupom não encontrado.");
        }
        couponRepository.deleteById(couponId);
        eventPublisherService.publishBillingUpdated("COUPON_DELETED");
    }

    @Transactional
    public UserSubscriptionStatusDto adminGrantSubscription(AdminGrantSubscriptionRequest req, String adminActor) {
        ensureDefaultPlans();
        User user = resolveTargetUser(req.userId(), req.userEmail());
        SubscriptionPlanEntity plan = resolvePlanOrDefault(req.planId());

        int days = (req.periodDays() != null && req.periodDays() > 0)
            ? req.periodDays()
            : ("YEARLY".equalsIgnoreCase(plan.getBillingInterval()) ? 365 : 30);
        boolean isTrial = Boolean.TRUE.equals(req.isTrial());
        int price = req.customPriceCents() != null
            ? Math.max(0, req.customPriceCents())
            : (isTrial ? 0 : plan.getPriceCents());
        Instant now = Instant.now();
        Instant end = now.plus(Duration.ofDays(days));

        UserSubscriptionEntity sub = subscriptionRepository.findByUserId(user.getId())
            .orElseGet(UserSubscriptionEntity::new);
        sub.setUserId(user.getId());
        sub.setUserEmail(user.getEmail());
        sub.setPlanId(plan.getId());
        sub.setPlanName(plan.getName());
        sub.setSource("ADMIN_MANUAL");
        sub.setStatus(isTrial ? "TRIALING" : "ACTIVE");
        sub.setPriceCents(price);
        sub.setCurrency(plan.getCurrency());
        sub.setAppliedCouponCode(req.couponCode() != null && !req.couponCode().isBlank() ? req.couponCode().trim().toUpperCase(Locale.ROOT) : null);
        sub.setAutoRenew(req.autoRenew() != null ? req.autoRenew() : !isTrial);
        if (req.overdueActionOverride() != null && !req.overdueActionOverride().isBlank()) {
            sub.setOverdueActionOverride(req.overdueActionOverride().trim().toUpperCase(Locale.ROOT));
        }
        sub.setCurrentPeriodStartUtc(now);
        sub.setCurrentPeriodEndUtc(end);
        sub.setTrialEndsAtUtc(isTrial ? end : null);
        clearOverdueFields(sub);
        sub.setStatusReason((req.note() != null && !req.note().isBlank())
            ? req.note().trim()
            : (isTrial ? "Período grátis liberado manualmente (" + days + " dias)" : "Assinatura liberada pelo administrador"));
        sub.setUpdatedAtUtc(now);
        sub = subscriptionRepository.save(sub);

        user.setAccountTier("PRO");
        userRepository.save(user);

        PaymentEntryEntity entry = new PaymentEntryEntity();
        entry.setUserId(user.getId());
        entry.setUserEmail(user.getEmail());
        entry.setSubscriptionId(sub.getId());
        entry.setEntryType(isTrial ? "COUPON_TRIAL_GRANT" : "MANUAL_INCOME");
        entry.setStatus("PAID");
        entry.setGrossAmountCents(price);
        entry.setDiscountCents(0);
        entry.setNetAmountCents(price);
        entry.setCurrency(plan.getCurrency());
        entry.setPaymentMethod((req.paymentMethod() != null && !req.paymentMethod().isBlank()) ? req.paymentMethod().trim().toUpperCase(Locale.ROOT) : "MANUAL");
        entry.setCouponCode(sub.getAppliedCouponCode());
        entry.setReferenceCode("ADM-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        entry.setDescription(plan.getName() + " • " + sub.getStatusReason());
        entry.setPaidAtUtc(now);
        entry.setCreatedBy(adminActor);
        paymentEntryRepository.save(entry);

        eventPublisherService.publishUserSubscriptionState(
            user, "SUBSCRIPTION_ACTIVATED", sub.getStatus(), null, null,
            "Forma Pro liberado (" + (isTrial ? days + " dias grátis" : plan.getName()) + ")!"
        );
        eventPublisherService.publishBillingUpdated("ADMIN_GRANT");

        return toSubscriptionDto(sub, user.getAccountTier(), List.of());
    }

    @Transactional
    public UserSubscriptionStatusDto adminSubscriptionAction(UUID subscriptionId, AdminSubscriptionActionRequest req, String adminActor) {
        UserSubscriptionEntity sub = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
        User user = userRepository.findById(sub.getUserId()).orElse(null);
        Instant now = Instant.now();
        String action = (req != null && req.action() != null) ? req.action().trim().toUpperCase(Locale.ROOT) : "";

        switch (action) {
            case "ACTIVATE_RENEW" -> {
                int days = (req.extendDays() != null && req.extendDays() > 0) ? req.extendDays() : 30;
                sub.setStatus("ACTIVE");
                sub.setCurrentPeriodStartUtc(now);
                sub.setCurrentPeriodEndUtc(now.plus(Duration.ofDays(days)));
                clearOverdueFields(sub);
                sub.setStatusReason((req.reason() != null && !req.reason().isBlank()) ? req.reason().trim() : "Regularizada manualmente pelo administrador");
                if (user != null) {
                    user.setAccountTier("PRO");
                    userRepository.save(user);
                    eventPublisherService.publishUserSubscriptionState(
                        user, "SUBSCRIPTION_ACTIVATED", "ACTIVE", null, null,
                        "Pagamento regularizado! Seu Forma Pro foi reativado."
                    );
                }
            }
            case "MARK_OVERDUE" -> {
                transitionIntoOverdue(sub, now, (req.reason() != null && !req.reason().isBlank()) ? req.reason().trim() : "Marcada em atraso pelo administrador", now);
                subscriptionRepository.save(sub);
                evaluateOverdueSubscriptions();
                sub = subscriptionRepository.findById(subscriptionId).orElse(sub);
            }
            case "PAUSE" -> {
                sub.setStatus("PAUSED");
                sub.setPausedAtUtc(now);
                sub.setStatusReason((req.reason() != null && !req.reason().isBlank()) ? req.reason().trim() : "Assinatura pausada pelo administrador");
                if (user != null) {
                    user.setAccountTier("FREE");
                    userRepository.save(user);
                    eventPublisherService.publishUserSubscriptionState(
                        user, "SUBSCRIPTION_ENFORCED", "PAUSED", null, null,
                        "Sua assinatura Forma Pro foi pausada no painel administrativo."
                    );
                }
            }
            case "CANCEL" -> {
                sub.setStatus("CANCELED");
                sub.setCanceledAtUtc(now);
                sub.setStatusReason((req.reason() != null && !req.reason().isBlank()) ? req.reason().trim() : "Assinatura cancelada pelo administrador");
                if (user != null) {
                    user.setAccountTier("FREE");
                    userRepository.save(user);
                    eventPublisherService.publishUserSubscriptionState(
                        user, "SUBSCRIPTION_ENFORCED", "CANCELED", null, null,
                        "Sua assinatura Forma Pro foi cancelada no painel administrativo."
                    );
                }
            }
            case "SET_POLICY" -> {
                String policy = (req.overdueActionOverride() != null && !req.overdueActionOverride().isBlank())
                    ? req.overdueActionOverride().trim().toUpperCase(Locale.ROOT)
                    : null;
                sub.setOverdueActionOverride(("PAUSE".equals(policy) || "CANCEL".equals(policy)) ? policy : null);
            }
            default -> throw new IllegalArgumentException("Ação de assinatura desconhecida: " + action);
        }

        sub.setUpdatedAtUtc(now);
        sub = subscriptionRepository.save(sub);
        eventPublisherService.publishBillingUpdated("ADMIN_SUB_ACTION:" + action);
        String tier = user != null ? user.getAccountTier() : "FREE";
        return toSubscriptionDto(sub, tier, List.of());
    }

    @Transactional
    public UserSubscriptionStatusDto simulateOverdueStage(UUID subscriptionId, SimulateOverdueRequest req) {
        UserSubscriptionEntity sub = subscriptionRepository.findById(subscriptionId)
            .orElseThrow(() -> new ResourceNotFoundException("Inscrição não encontrada."));
        Instant now = Instant.now();

        int stage = (req != null && req.dayStage() != null) ? req.dayStage() : 1;
        long hoursBack;
        if (req != null && req.hoursOverdue() != null && req.hoursOverdue() >= 0) {
            hoursBack = req.hoursOverdue();
        } else {
            hoursBack = switch (stage) {
                case 1 -> 1L;
                case 2 -> 25L;
                case 3 -> 50L;
                default -> 74L;
            };
        }

        Instant overdueStart = now.minus(Duration.ofHours(hoursBack));
        sub.setStatus("PAST_DUE");
        sub.setOverdueSinceUtc(overdueStart);
        sub.setGraceEndsAtUtc(overdueStart.plus(Duration.ofHours(THREE_DAYS_GRACE_HOURS)));
        if (stage <= 3) {
            sub.setLastWarningDaySent(Math.max(0, stage - 1));
        }
        sub.setStatusReason("Simulação de inadimplência (Estágio Dia " + stage + " • " + hoursBack + "h em atraso)");
        sub.setUpdatedAtUtc(now);
        subscriptionRepository.save(sub);

        evaluateOverdueSubscriptions();

        UserSubscriptionEntity updated = subscriptionRepository.findById(subscriptionId).orElse(sub);
        User user = userRepository.findById(updated.getUserId()).orElse(null);
        List<DunningNoticeDto> notices = dunningNoticeRepository.findTop10ByUserIdOrderByCreatedAtUtcDesc(updated.getUserId())
            .stream().map(DunningNoticeDto::fromEntity).toList();
        return toSubscriptionDto(updated, user != null ? user.getAccountTier() : "FREE", notices);
    }

    // MARK: - Financial Entries Ledger

    @Transactional
    public PaymentEntryDto createManualPaymentEntry(CreatePaymentEntryRequest req, String adminActor) {
        if (req == null || req.description() == null || req.description().isBlank()) {
            throw new IllegalArgumentException("Descrição da entrada financeira obrigatória.");
        }
        int gross = req.grossAmountCents() != null ? Math.max(0, req.grossAmountCents()) : 0;
        int discount = req.discountCents() != null ? Math.max(0, Math.min(gross, req.discountCents())) : 0;
        int net = Math.max(0, gross - discount);

        User user = null;
        if (req.userId() != null) {
            user = userRepository.findById(req.userId()).orElse(null);
        } else if (req.userEmail() != null && !req.userEmail().isBlank()) {
            user = userRepository.findByEmail(req.userEmail().trim().toLowerCase(Locale.ROOT)).orElse(null);
        }

        PaymentEntryEntity entry = new PaymentEntryEntity();
        entry.setUserId(user != null ? user.getId() : req.userId());
        entry.setUserEmail(user != null ? user.getEmail() : (req.userEmail() != null ? req.userEmail().trim() : null));
        entry.setSubscriptionId(req.subscriptionId());
        entry.setEntryType((req.entryType() != null && !req.entryType().isBlank()) ? req.entryType().trim().toUpperCase(Locale.ROOT) : "MANUAL_INCOME");
        String status = (req.status() != null && !req.status().isBlank()) ? req.status().trim().toUpperCase(Locale.ROOT) : "PAID";
        entry.setStatus(status);
        entry.setGrossAmountCents(gross);
        entry.setDiscountCents(discount);
        entry.setNetAmountCents(net);
        entry.setCurrency("BRL");
        entry.setPaymentMethod((req.paymentMethod() != null && !req.paymentMethod().isBlank()) ? req.paymentMethod().trim().toUpperCase(Locale.ROOT) : "PIX");
        entry.setCouponCode(req.couponCode() != null && !req.couponCode().isBlank() ? req.couponCode().trim().toUpperCase(Locale.ROOT) : null);
        entry.setReferenceCode((req.referenceCode() != null && !req.referenceCode().isBlank())
            ? req.referenceCode().trim()
            : "ENT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        entry.setDescription(req.description().trim());
        Instant now = Instant.now();
        entry.setPaidAtUtc("PAID".equals(status) ? parseInstantOrDefault(req.paidAtUtc(), now) : null);
        entry.setDueAtUtc(now);
        entry.setCreatedBy(adminActor);
        entry.setCreatedAtUtc(now);

        PaymentEntryEntity saved = paymentEntryRepository.save(entry);
        eventPublisherService.publishBillingUpdated("ENTRY_CREATED");
        return PaymentEntryDto.fromEntity(saved);
    }

    @Transactional
    public PaymentEntryDto updatePaymentEntryStatus(UUID entryId, UpdatePaymentEntryStatusRequest req) {
        PaymentEntryEntity entry = paymentEntryRepository.findById(entryId)
            .orElseThrow(() -> new ResourceNotFoundException("Entrada financeira não encontrada."));
        String nextStatus = (req != null && req.status() != null && !req.status().isBlank())
            ? req.status().trim().toUpperCase(Locale.ROOT)
            : "PAID";
        entry.setStatus(nextStatus);
        if ("PAID".equals(nextStatus) && entry.getPaidAtUtc() == null) {
            entry.setPaidAtUtc(Instant.now());
        }
        if (req != null && req.reason() != null && !req.reason().isBlank()) {
            entry.setDescription(entry.getDescription() + " [" + req.reason().trim() + "]");
        }
        PaymentEntryEntity saved = paymentEntryRepository.save(entry);
        eventPublisherService.publishBillingUpdated("ENTRY_STATUS_UPDATED:" + nextStatus);
        return PaymentEntryDto.fromEntity(saved);
    }

    @Transactional
    public void deletePaymentEntry(UUID entryId) {
        if (!paymentEntryRepository.existsById(entryId)) {
            throw new ResourceNotFoundException("Entrada financeira não encontrada.");
        }
        paymentEntryRepository.deleteById(entryId);
        eventPublisherService.publishBillingUpdated("ENTRY_DELETED");
    }

    // MARK: - Helpers

    private void transitionIntoOverdue(UserSubscriptionEntity sub, Instant overdueStart, String reason, Instant now) {
        sub.setStatus("PAST_DUE");
        if (sub.getOverdueSinceUtc() == null) {
            sub.setOverdueSinceUtc(overdueStart);
        }
        sub.setGraceEndsAtUtc(sub.getOverdueSinceUtc().plus(Duration.ofHours(THREE_DAYS_GRACE_HOURS)));
        sub.setStatusReason(reason);
        sub.setUpdatedAtUtc(now);
        subscriptionRepository.save(sub);

        if (paymentEntryRepository.findFirstBySubscriptionIdAndStatusOrderByCreatedAtUtcDesc(sub.getId(), "OVERDUE").isEmpty()) {
            PaymentEntryEntity overdueEntry = new PaymentEntryEntity();
            overdueEntry.setUserId(sub.getUserId());
            overdueEntry.setUserEmail(sub.getUserEmail());
            overdueEntry.setSubscriptionId(sub.getId());
            overdueEntry.setEntryType("SUBSCRIPTION_CHARGE");
            overdueEntry.setStatus("OVERDUE");
            int amount = sub.getPriceCents() > 0 ? sub.getPriceCents() : 2490;
            overdueEntry.setGrossAmountCents(amount);
            overdueEntry.setDiscountCents(0);
            overdueEntry.setNetAmountCents(amount);
            overdueEntry.setCurrency(sub.getCurrency());
            overdueEntry.setPaymentMethod("APPLE_IAP");
            overdueEntry.setReferenceCode("OVD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            overdueEntry.setDescription("Cobrança em atraso (" + sub.getPlanName() + ")");
            overdueEntry.setDueAtUtc(sub.getOverdueSinceUtc());
            overdueEntry.setCreatedBy("DunningEngine");
            paymentEntryRepository.save(overdueEntry);
        }
    }

    private void clearOverdueFields(UserSubscriptionEntity sub) {
        sub.setOverdueSinceUtc(null);
        sub.setGraceEndsAtUtc(null);
        sub.setDunningWarningsSent(0);
        sub.setLastWarningDaySent(0);
        sub.setLastWarningAtUtc(null);
        sub.setPausedAtUtc(null);
        sub.setCanceledAtUtc(null);
    }

    private DunningNoticeEntity saveDunningNotice(
        UserSubscriptionEntity sub,
        User user,
        int dayNumber,
        String noticeType,
        String title,
        String message
    ) {
        DunningNoticeEntity n = new DunningNoticeEntity();
        n.setSubscriptionId(sub.getId());
        n.setUserId(sub.getUserId());
        n.setUserEmail(user != null ? user.getEmail() : sub.getUserEmail());
        n.setDayNumber(dayNumber);
        n.setNoticeType(noticeType);
        n.setTitle(title);
        n.setMessage(message);
        n.setWebsocketDelivered(false);
        n.setEmailDelivered(trySendDunningEmail(n.getUserEmail(), title, message));
        n.setCreatedAtUtc(Instant.now());
        return dunningNoticeRepository.save(n);
    }

    private boolean trySendDunningEmail(String toEmail, String subject, String messageBody) {
        try {
            if (toEmail == null || toEmail.isBlank()) return false;
            String resendKey = appConfigService.getResendApiKey();
            String from = appConfigService.getMailFrom();
            if (resendKey == null || resendKey.isBlank() || from == null || from.isBlank()) {
                return false;
            }
            Map<String, Object> payload = Map.of(
                "from", from,
                "to", List.of(toEmail),
                "subject", "[Forma] " + subject,
                "html", "<div style=\"font-family:system-ui,sans-serif;padding:24px;background:#0f172a;color:#f8fafc;border-radius:12px;\">"
                    + "<h2 style=\"color:#fbbf24;margin-top:0;\">" + escapeHtml(subject) + "</h2>"
                    + "<p style=\"line-height:1.6;\">" + escapeHtml(messageBody) + "</p>"
                    + "<p style=\"color:#94a3b8;font-size:13px;\">Abra o aplicativo Forma &gt; Perfil &gt; Assinatura para regularizar ou aplicar um cupom.</p>"
                    + "</div>"
            );
            HttpRequest req = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(payload)))
                .timeout(Duration.ofSeconds(8))
                .build();
            HttpResponse<String> resp = httpClient.send(req, HttpResponse.BodyHandlers.ofString());
            return resp.statusCode() >= 200 && resp.statusCode() < 300;
        } catch (Exception e) {
            return false;
        }
    }

    private static String escapeHtml(String s) {
        return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private String resolveEffectiveOverdueAction(UserSubscriptionEntity sub) {
        if (sub.getOverdueActionOverride() != null && !sub.getOverdueActionOverride().isBlank()) {
            return "CANCEL".equalsIgnoreCase(sub.getOverdueActionOverride()) ? "CANCEL" : "PAUSE";
        }
        return appConfigService.getBillingOverdueAction();
    }

    private static String buildWarningTitle(int day) {
        return switch (day) {
            case 1 -> "Aviso 1/3: Pendência na Assinatura Forma Pro";
            case 2 -> "Aviso 2/3: Pagamento em Atraso";
            default -> "Último Aviso (3/3): Ação Automática Amanhã";
        };
    }

    private static String buildWarningMessage(int day, String actionVerb, Instant graceEnds) {
        String deadline = formatShortDate(graceEnds);
        return switch (day) {
            case 1 -> "Dia 1 de 3: Não identificamos sua renovação. Regularize até " + deadline + " para evitar que sua assinatura seja " + actionVerb + ".";
            case 2 -> "Dia 2 de 3: Sua assinatura continua em atraso. Faltam menos de 48h (até " + deadline + ") antes de ser " + actionVerb + " automaticamente.";
            default -> "Dia 3 de 3 (Último aviso): Sua assinatura será " + actionVerb + " automaticamente após " + deadline + " se não houver pagamento.";
        };
    }

    private static String formatShortDate(Instant instant) {
        if (instant == null) return "o prazo de 3 dias";
        return instant.atZone(ZoneOffset.UTC).format(DateTimeFormatter.ofPattern("dd/MM HH:mm")) + " UTC";
    }

    private void validateCouponEligibility(CouponEntity coupon, UUID userId, String planId) {
        if (!coupon.isActive()) {
            throw new IllegalArgumentException("Este cupom está desativado no momento.");
        }
        if (coupon.getExpiresAtUtc() != null && Instant.now().isAfter(coupon.getExpiresAtUtc())) {
            throw new IllegalArgumentException("Este cupom expirou em " + formatShortDate(coupon.getExpiresAtUtc()) + ".");
        }
        if (coupon.getMaxRedemptions() != null && coupon.getCurrentRedemptions() >= coupon.getMaxRedemptions()) {
            throw new IllegalArgumentException("Este cupom atingiu o limite máximo de resgates.");
        }
        if (coupon.getApplicablePlanId() != null && !coupon.getApplicablePlanId().isBlank()
            && !coupon.getApplicablePlanId().equalsIgnoreCase(planId)) {
            throw new IllegalArgumentException("Cupom válido apenas para o plano " + coupon.getApplicablePlanId() + ".");
        }
        long userCount = redemptionRepository.countByCouponIdAndUserId(coupon.getId(), userId);
        if (userCount >= coupon.getPerUserLimit()) {
            throw new IllegalArgumentException("Você já atingiu o limite de uso deste cupom na sua conta.");
        }
    }

    private void applyCouponFields(CouponEntity c, CreateOrUpdateCouponRequest req) {
        if (req.description() != null) c.setDescription(req.description().trim());
        String type = (req.couponType() != null && !req.couponType().isBlank())
            ? req.couponType().trim().toUpperCase(Locale.ROOT)
            : "FREE_PERIOD_DAYS";
        c.setCouponType(type);
        c.setDiscountPercent("PERCENT_DISCOUNT".equals(type) ? clampInt(req.discountPercent(), 1, 100, 10) : null);
        c.setDiscountCents("FIXED_CENTS_DISCOUNT".equals(type) ? clampInt(req.discountCents(), 100, 1000000, 500) : null);
        c.setFreePeriodDays("FREE_PERIOD_DAYS".equals(type) ? clampInt(req.freePeriodDays(), 1, 3650, 7) : null);
        c.setApplicablePlanId(req.applicablePlanId() != null && !req.applicablePlanId().isBlank() ? req.applicablePlanId().trim() : null);
        c.setMaxRedemptions(req.maxRedemptions() != null && req.maxRedemptions() > 0 ? req.maxRedemptions() : null);
        c.setPerUserLimit(req.perUserLimit() != null && req.perUserLimit() > 0 ? req.perUserLimit() : 1);
        c.setExpiresAtUtc(req.expiresAtUtc() != null && !req.expiresAtUtc().isBlank() ? Instant.parse(req.expiresAtUtc().trim()) : null);
        if (req.active() != null) c.setActive(req.active());
    }

    private static int clampInt(Integer val, int min, int max, int fallback) {
        if (val == null) return fallback;
        return Math.max(min, Math.min(max, val));
    }

    private static int calculateFinalPriceCents(int originalCents, CouponEntity c) {
        return switch (c.getCouponType().toUpperCase(Locale.ROOT)) {
            case "FREE_PERIOD_DAYS" -> 0;
            case "PERCENT_DISCOUNT" -> {
                int pct = c.getDiscountPercent() != null ? Math.min(100, Math.max(0, c.getDiscountPercent())) : 0;
                yield (int) Math.round(originalCents * ((100.0 - pct) / 100.0));
            }
            case "FIXED_CENTS_DISCOUNT" -> {
                int off = c.getDiscountCents() != null ? Math.max(0, c.getDiscountCents()) : 0;
                yield Math.max(0, originalCents - off);
            }
            default -> originalCents;
        };
    }

    private static String buildCouponBenefitLabel(CouponEntity c) {
        return switch (c.getCouponType().toUpperCase(Locale.ROOT)) {
            case "FREE_PERIOD_DAYS" -> (c.getFreePeriodDays() != null ? c.getFreePeriodDays() : 7) + " dias grátis de Forma Pro";
            case "PERCENT_DISCOUNT" -> (c.getDiscountPercent() != null ? c.getDiscountPercent() : 0) + "% de desconto";
            case "FIXED_CENTS_DISCOUNT" -> String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f de desconto", (c.getDiscountCents() != null ? c.getDiscountCents() : 0) / 100.0);
            default -> "Benefício promocional";
        };
    }

    private SubscriptionPlanEntity resolvePlanOrDefault(String planId) {
        if (planId != null && !planId.isBlank()) {
            Optional<SubscriptionPlanEntity> found = planRepository.findById(planId.trim());
            if (found.isPresent()) return found.get();
        }
        return planRepository.findById("PRO_MONTHLY")
            .orElseGet(() -> planRepository.findAll().stream().findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Nenhum plano cadastrado.")));
    }

    private User resolveTargetUser(UUID userId, String userEmail) {
        if (userId != null) {
            return userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado pelo ID."));
        }
        if (userEmail != null && !userEmail.isBlank()) {
            return userRepository.findByEmail(userEmail.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado pelo e-mail: " + userEmail));
        }
        throw new IllegalArgumentException("Informe userId ou userEmail.");
    }

    private Optional<User> resolveWebhookUser(String userIdRaw, String email, String externalTxId) {
        if (userIdRaw != null && !userIdRaw.isBlank()) {
            try {
                Optional<User> byId = userRepository.findById(UUID.fromString(userIdRaw.trim()));
                if (byId.isPresent()) return byId;
            } catch (Exception ignored) {}
        }
        if (email != null && !email.isBlank()) {
            Optional<User> byEmail = userRepository.findByEmail(email.trim().toLowerCase(Locale.ROOT));
            if (byEmail.isPresent()) return byEmail;
        }
        if (externalTxId != null && !externalTxId.isBlank()) {
            Optional<UserSubscriptionEntity> sub = subscriptionRepository.findByExternalTransactionId(externalTxId.trim());
            if (sub.isPresent()) {
                return userRepository.findById(sub.get().getUserId());
            }
        }
        return Optional.empty();
    }

    public boolean verifyHmacSignature(String rawBody, String signatureHeader, String secret) {
        if (secret == null || secret.isBlank() || signatureHeader == null || signatureHeader.isBlank()) {
            return false;
        }
        try {
            String provided = signatureHeader.trim();
            if (provided.toLowerCase(Locale.ROOT).startsWith("sha256=")) {
                provided = provided.substring(7).trim();
            }
            String expected = computeHmacSha256Hex(rawBody != null ? rawBody : "", secret);
            return MessageDigest.isEqual(
                expected.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8),
                provided.toLowerCase(Locale.ROOT).getBytes(StandardCharsets.UTF_8)
            );
        } catch (Exception e) {
            return false;
        }
    }

    public String computeHmacSha256Hex(String payload, String secret) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] digest = mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        return HexFormat.of().formatHex(digest);
    }

    private BillingWebhookEventEntity recordWebhookLog(
        String eventId,
        String provider,
        String eventType,
        boolean sigValid,
        String status,
        String email,
        String rawBody,
        String errorMessage
    ) {
        BillingWebhookEventEntity e = new BillingWebhookEventEntity();
        e.setEventId(eventId);
        e.setProvider(provider);
        e.setEventType(eventType);
        e.setSignatureValid(sigValid);
        e.setProcessingStatus(status);
        e.setTargetUserEmail(email);
        e.setPayloadExcerpt(rawBody != null && rawBody.length() > 1200 ? rawBody.substring(0, 1200) + "..." : rawBody);
        e.setErrorMessage(errorMessage);
        e.setReceivedAtUtc(Instant.now());
        return webhookEventRepository.save(e);
    }

    private UserSubscriptionStatusDto toSubscriptionDto(UserSubscriptionEntity s, String tier, List<DunningNoticeDto> notices) {
        Integer overdueDay = null;
        String activeWarning = null;
        String effectiveAction = resolveEffectiveOverdueAction(s);
        if ("PAST_DUE".equalsIgnoreCase(s.getStatus()) && s.getOverdueSinceUtc() != null) {
            long hours = Math.max(0, Duration.between(s.getOverdueSinceUtc(), Instant.now()).toHours());
            overdueDay = (int) Math.min(3, (hours / 24L) + 1L);
            String actionVerb = "CANCEL".equalsIgnoreCase(effectiveAction) ? "cancelada" : "pausada";
            activeWarning = buildWarningMessage(overdueDay, actionVerb, s.getGraceEndsAtUtc());
        } else if ("PAUSED".equalsIgnoreCase(s.getStatus()) || "CANCELED".equalsIgnoreCase(s.getStatus())) {
            activeWarning = s.getStatusReason();
        }
        return UserSubscriptionStatusDto.fromEntity(s, tier, effectiveAction, overdueDay, activeWarning, notices);
    }

    private static long estimateMonthlyCents(UserSubscriptionEntity s, List<SubscriptionPlanEntity> plans) {
        if ("PRO_YEARLY".equalsIgnoreCase(s.getPlanId()) || (s.getPlanName() != null && s.getPlanName().toLowerCase(Locale.ROOT).contains("anual"))) {
            return Math.round(s.getPriceCents() / 12.0);
        }
        return s.getPriceCents();
    }

    private static Instant parseInstantOrDefault(String iso, Instant fallback) {
        if (iso == null || iso.isBlank()) return fallback;
        try {
            return Instant.parse(iso.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field)) return null;
        String val = node.path(field).asText("").trim();
        return val.isEmpty() ? null : val;
    }
}

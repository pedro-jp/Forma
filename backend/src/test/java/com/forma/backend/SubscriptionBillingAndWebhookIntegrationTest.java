package com.forma.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forma.backend.dto.AuthDto;
import com.forma.backend.entity.BillingWebhookEventEntity;
import com.forma.backend.entity.PaymentEntryEntity;
import com.forma.backend.entity.SecurityIncidentLogEntity;
import com.forma.backend.repository.BillingWebhookEventRepository;
import com.forma.backend.repository.PaymentEntryRepository;
import com.forma.backend.repository.SecurityIncidentLogRepository;
import com.forma.backend.security.AuthContextResolver;
import com.forma.backend.service.AppConfigService;
import com.forma.backend.service.SubscriptionBillingService;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SubscriptionBillingAndWebhookIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private AppConfigService appConfigService;
    @Autowired private SubscriptionBillingService billingService;
    @Autowired private BillingWebhookEventRepository webhookEventRepository;
    @Autowired private PaymentEntryRepository paymentEntryRepository;
    @Autowired private SecurityIncidentLogRepository securityIncidentLogRepository;

    private Cookie loginAsAdminCookie() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", "admin",
                    "password", "test_admin_password"
                ))))
            .andExpect(status().isOk())
            .andExpect(cookie().exists(AuthContextResolver.ADMIN_COOKIE_NAME))
            .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("SameSite=Strict")))
            .andReturn();
        Cookie cookie = res.getResponse().getCookie(AuthContextResolver.ADMIN_COOKIE_NAME);
        assertThat(cookie).isNotNull();
        return cookie;
    }

    private AuthDto.AuthResponse registerUser(String emailPrefix) throws Exception {
        String email = emailPrefix + "+" + UUID.randomUUID().toString().substring(0, 8) + "@forma.app";
        String body = """
            {
              "email": "%s",
              "password": "StrongPassword123!",
              "name": "Assinante Teste",
              "termsAccepted": true,
              "termsVersion": "2026-11-v2",
              "sensitiveHealthConsentGranted": true,
              "aiConsentGranted": true,
              "ageConfirmed18Plus": true
            }
            """.formatted(email);

        MvcResult res = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(), AuthDto.AuthResponse.class);
    }

    @Test
    @DisplayName("B1: Resgate de cupom de período grátis (FREE_PERIOD_DAYS) e bloqueio de duplicidade por usuário")
    void shouldRedeemFreePeriodCouponAndEnforcePerUserLimit() throws Exception {
        Cookie adminCookie = loginAsAdminCookie();
        AuthDto.AuthResponse user = registerUser("cupom");
        String code = "VIP15D_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        mockMvc.perform(post("/api/admin/billing/coupons")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", code,
                    "description", "15 dias grátis para teste",
                    "couponType", "FREE_PERIOD_DAYS",
                    "freePeriodDays", 15,
                    "perUserLimit", 1,
                    "active", true
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.code").value(code))
            .andExpect(jsonPath("$.freePeriodDays").value(15));

        mockMvc.perform(post("/api/subscriptions/validate-coupon")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", code, "planId", "PRO_MONTHLY"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true))
            .andExpect(jsonPath("$.finalPriceCents").value(0))
            .andExpect(jsonPath("$.freePeriodDays").value(15));

        mockMvc.perform(post("/api/subscriptions/redeem-coupon")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", code, "planId", "PRO_MONTHLY"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redeemed").value(true))
            .andExpect(jsonPath("$.accountTier").value("PRO"))
            .andExpect(jsonPath("$.subscriptionStatus").value("TRIALING"))
            .andExpect(jsonPath("$.subscription.appliedCouponCode").value(code));

        // Repetir o mesmo cupom pelo mesmo usuário deve falhar com HTTP 400
        mockMvc.perform(post("/api/subscriptions/redeem-coupon")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", code, "planId", "PRO_MONTHLY"))))
            .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("B2: Sincronização StoreKit 2 idempotente, entradas manuais/estorno e KPIs no dashboard")
    void shouldSyncStoreKitIdempotentlyManageEntriesAndComputeDashboardKpis() throws Exception {
        Cookie adminCookie = loginAsAdminCookie();
        AuthDto.AuthResponse user = registerUser("storekit");
        String txId = "sk2_tx_" + UUID.randomUUID().toString().substring(0, 8);

        Map<String, Object> syncPayload = Map.of(
            "productId", "jota.Forma.pro.monthly",
            "transactionId", txId,
            "originalTransactionId", txId,
            "environment", "Xcode",
            "isTrialPeriod", false,
            "paidAmountCents", 2490
        );

        mockMvc.perform(post("/api/subscriptions/sync-storekit")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(syncPayload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.accountTier").value("PRO"))
            .andExpect(jsonPath("$.source").value("APPLE_STOREKIT"));

        // Segunda chamada com o mesmo transactionId não deve duplicar lançamento pago
        long entriesAfterFirst = paymentEntryRepository.findAll().stream()
            .filter(e -> ("SK2-" + txId).equals(e.getReferenceCode()))
            .count();
        assertThat(entriesAfterFirst).isEqualTo(1L);

        mockMvc.perform(post("/api/subscriptions/sync-storekit")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(syncPayload)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        long entriesAfterSecond = paymentEntryRepository.findAll().stream()
            .filter(e -> ("SK2-" + txId).equals(e.getReferenceCode()))
            .count();
        assertThat(entriesAfterSecond).isEqualTo(1L);

        // Lançamento manual PIX com desconto + segunda entrada estornada
        mockMvc.perform(post("/api/admin/billing/entries")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "userEmail", user.email(),
                    "entryType", "MANUAL_INCOME",
                    "status", "PAID",
                    "grossAmountCents", 5000,
                    "discountCents", 500,
                    "paymentMethod", "PIX",
                    "description", "Ajuste anual PIX"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.netAmountCents").value(4500))
            .andExpect(jsonPath("$.status").value("PAID"));

        MvcResult toRefundRes = mockMvc.perform(post("/api/admin/billing/entries")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "userEmail", user.email(),
                    "entryType", "MANUAL_INCOME",
                    "status", "PAID",
                    "grossAmountCents", 1200,
                    "discountCents", 0,
                    "paymentMethod", "CARD",
                    "description", "Lançamento para estorno"
                ))))
            .andExpect(status().isOk())
            .andReturn();
        String refundEntryId = objectMapper.readTree(toRefundRes.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(post("/api/admin/billing/entries/" + refundEntryId + "/status")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("status", "REFUNDED"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(get("/api/admin/billing/dashboard").cookie(adminCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.mrrCents").value(org.hamcrest.Matchers.greaterThanOrEqualTo(2490)))
            .andExpect(jsonPath("$.paidRevenue30dCents").value(org.hamcrest.Matchers.greaterThanOrEqualTo(6990)))
            .andExpect(jsonPath("$.refundedTotalCents").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1200)))
            .andExpect(jsonPath("$.activeSubscribersCount").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    @DisplayName("B3: Webhook HMAC-SHA256 e régua completa de atraso de 3 dias (Dia 1->2->3 -> Auto-Pause e Auto-Cancel)")
    void shouldValidateWebhookHmacAndRunFullThreeDayDunningPauseAndCancel() throws Exception {
        Cookie adminCookie = loginAsAdminCookie();
        AuthDto.AuthResponse user = registerUser("dunning");

        // Grant inicial pelo admin
        MvcResult grantRes = mockMvc.perform(post("/api/admin/billing/subscriptions/grant")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "userEmail", user.email(),
                    "planId", "PRO_MONTHLY",
                    "periodDays", 30,
                    "isTrial", false
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();

        String subId = objectMapper.readTree(grantRes.getResponse().getContentAsString()).path("id").asText();

        // 1. Webhook com assinatura forjada deve ser recusado (401) e auditado
        String badEventId = "evt_bad_" + UUID.randomUUID().toString().substring(0, 6);
        String failPayload = objectMapper.writeValueAsString(Map.of(
            "eventId", badEventId,
            "provider", "STRIPE_TEST",
            "eventType", "PAYMENT_FAILED",
            "userEmail", user.email(),
            "planId", "PRO_MONTHLY"
        ));

        mockMvc.perform(post("/api/webhooks/billing")
                .header("X-Forma-Webhook-Signature", "sha256=deadbeef")
                .contentType(MediaType.APPLICATION_JSON)
                .content(failPayload))
            .andExpect(status().isUnauthorized());

        List<BillingWebhookEventEntity> badEvents = webhookEventRepository.findTop100ByOrderByReceivedAtUtcDesc();
        assertThat(badEvents).anyMatch(w -> badEventId.equals(w.getEventId())
            && !w.isSignatureValid()
            && "REJECTED_SIGNATURE".equals(w.getProcessingStatus()));

        // 2. Webhook PAYMENT_FAILED assinado corretamente -> PAST_DUE + Aviso Dia 1
        String goodEventId = "evt_fail_" + UUID.randomUUID().toString().substring(0, 6);
        String signedPayload = objectMapper.writeValueAsString(Map.of(
            "eventId", goodEventId,
            "provider", "STRIPE_TEST",
            "eventType", "PAYMENT_FAILED",
            "userEmail", user.email(),
            "planId", "PRO_MONTHLY"
        ));
        String validSig = "sha256=" + billingService.computeHmacSha256Hex(signedPayload, appConfigService.getWebhookSecret());

        mockMvc.perform(post("/api/webhooks/billing")
                .header("X-Forma-Webhook-Signature", validSig)
                .contentType(MediaType.APPLICATION_JSON)
                .content(signedPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.signatureValid").value(true))
            .andExpect(jsonPath("$.processingStatus").value("PROCESSED"));

        mockMvc.perform(get("/api/subscriptions/me")
                .header("Authorization", "Bearer " + user.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAST_DUE"))
            .andExpect(jsonPath("$.pastDue").value(true))
            .andExpect(jsonPath("$.lastWarningDaySent").value(1))
            .andExpect(jsonPath("$.dunningWarningsSent").value(1))
            .andExpect(jsonPath("$.accountTier").value("PRO"));

        // 3. Simular Dia 2 e Dia 3 da régua de atraso
        mockMvc.perform(post("/api/admin/billing/subscriptions/" + subId + "/simulate-overdue")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dayStage\": 2}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lastWarningDaySent").value(2))
            .andExpect(jsonPath("$.dunningWarningsSent").value(2))
            .andExpect(jsonPath("$.status").value("PAST_DUE"));

        mockMvc.perform(post("/api/admin/billing/subscriptions/" + subId + "/simulate-overdue")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dayStage\": 3}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.lastWarningDaySent").value(3))
            .andExpect(jsonPath("$.dunningWarningsSent").value(3))
            .andExpect(jsonPath("$.status").value("PAST_DUE"));

        // 4. Após 3 dias (>72h) com política PAUSE -> status=PAUSED e conta rebaixada para FREE
        mockMvc.perform(put("/api/admin/billing/settings")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"overdueAction\": \"PAUSE\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overdueAction").value("PAUSE"));

        mockMvc.perform(post("/api/admin/billing/subscriptions/" + subId + "/simulate-overdue")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dayStage\": 4}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAUSED"))
            .andExpect(jsonPath("$.accountTier").value("FREE"));

        // 5. Reativar, alternar política global para CANCEL e simular >3 dias -> status=CANCELED e tier FREE
        mockMvc.perform(post("/api/admin/billing/subscriptions/grant")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "userEmail", user.email(),
                    "planId", "PRO_MONTHLY",
                    "periodDays", 30,
                    "isTrial", false
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.accountTier").value("PRO"));

        mockMvc.perform(put("/api/admin/billing/settings")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"overdueAction\": \"CANCEL\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overdueAction").value("CANCEL"));

        mockMvc.perform(post("/api/admin/billing/subscriptions/" + subId + "/simulate-overdue")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"dayStage\": 4}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELED"))
            .andExpect(jsonPath("$.accountTier").value("FREE"));
    }

    @Test
    @DisplayName("B4: Hardening do Painel Admin (bloqueio CSRF cross-site e lockout de força bruta após 5 falhas)")
    void shouldEnforceAdminCsrfProtectionAndBruteForceLockout() throws Exception {
        Cookie adminCookie = loginAsAdminCookie();
        String couponBody = objectMapper.writeValueAsString(Map.of(
            "code", "CSRF_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
            "couponType", "PERCENT_DISCOUNT",
            "discountPercent", 20,
            "active", true
        ));

        // Sec-Fetch-Site: cross-site bloqueado
        mockMvc.perform(post("/api/admin/billing/coupons")
                .cookie(adminCookie)
                .header("Sec-Fetch-Site", "cross-site")
                .contentType(MediaType.APPLICATION_JSON)
                .content(couponBody))
            .andExpect(status().isUnauthorized());

        // Origin externa bloqueada pela camada CORS/CSRF
        mockMvc.perform(post("/api/admin/billing/coupons")
                .cookie(adminCookie)
                .header("Origin", "https://attacker.evil")
                .contentType(MediaType.APPLICATION_JSON)
                .content(couponBody))
            .andExpect(status().isForbidden());

        // Same-origin (http://localhost no MockMvc) autorizado
        mockMvc.perform(post("/api/admin/billing/coupons")
                .cookie(adminCookie)
                .header("Origin", "http://localhost")
                .contentType(MediaType.APPLICATION_JSON)
                .content(couponBody))
            .andExpect(status().isOk());

        // Força bruta: 5 falhas no mesmo IP registram incidente e bloqueiam a 6ª tentativa
        String uniqueIp = "198.51.100." + (int) (Math.random() * 200 + 10);
        String wrongCreds = "{\"username\":\"admin\",\"password\":\"senha_errada_123\"}";
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/admin/login")
                    .header("X-Forwarded-For", uniqueIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(wrongCreds))
                .andExpect(status().isUnauthorized());
        }

        // 6ª tentativa (mesmo com senha correta) deve continuar bloqueada
        mockMvc.perform(post("/api/admin/login")
                .header("X-Forwarded-For", uniqueIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"username\":\"admin\",\"password\":\"test_admin_password\"}"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("bloqueado temporariamente")));

        List<SecurityIncidentLogEntity> incidents = securityIncidentLogRepository.findAll();
        assertThat(incidents).anyMatch(i -> "ADMIN_BRUTE_FORCE_BLOCKED".equals(i.getIncidentType()));
    }

    @Test
    @DisplayName("B5: Catálogo em tempo real reflete preço atualizado (R$ 29,90), prazo, política e cupons ativos sem cache HTTP")
    void shouldExposeLiveUncachedCatalogWithUpdatedPriceTrialPolicyAndActiveCoupons() throws Exception {
        Cookie adminCookie = loginAsAdminCookie();
        AuthDto.AuthResponse user = registerUser("catalog");
        String code = "PROMO29_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        // Admin altera preço do plano mensal para R$ 29,90 (2990 centavos) e 5 dias grátis de adesão
        mockMvc.perform(put("/api/admin/billing/plans/PRO_MONTHLY")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "priceCents", 2990,
                    "defaultTrialDays", 5,
                    "active", true
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.priceCents").value(2990))
            .andExpect(jsonPath("$.defaultTrialDays").value(5));

        // Admin alterna política de inadimplência e cadastra cupom de 10% ativo
        mockMvc.perform(put("/api/admin/billing/settings")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"overdueAction\": \"CANCEL\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.overdueAction").value("CANCEL"));

        mockMvc.perform(post("/api/admin/billing/coupons")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", code,
                    "description", "Desconto de lançamento 10%",
                    "couponType", "PERCENT_DISCOUNT",
                    "discountPercent", 10,
                    "applicablePlanId", "PRO_MONTHLY",
                    "perUserLimit", 1,
                    "active", true
                ))))
            .andExpect(status().isOk());

        // Consulta sem cache pelo endpoint /api/subscriptions/catalog (anônimo e autenticado)
        // O catálogo público não deve expor nem possuir qualquer campo de lista de cupons.
        mockMvc.perform(get("/api/subscriptions/catalog"))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(jsonPath("$.graceDays").value(3))
            .andExpect(jsonPath("$.overdueActionPolicy").value("CANCEL"))
            .andExpect(jsonPath("$.plans[?(@.id=='PRO_MONTHLY')].priceCents").value(org.hamcrest.Matchers.hasItem(2990)))
            .andExpect(jsonPath("$.plans[?(@.id=='PRO_MONTHLY')].defaultTrialDays").value(org.hamcrest.Matchers.hasItem(5)))
            .andExpect(jsonPath("$.activeCoupons").doesNotExist());

        mockMvc.perform(get("/api/subscriptions/catalog")
                .header("Authorization", "Bearer " + user.token()))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(jsonPath("$.activeCoupons").doesNotExist());

        // Validação de cupom usa o novo preço base de 2990 centavos -> final 2691 centavos
        mockMvc.perform(post("/api/subscriptions/validate-coupon")
                .header("Authorization", "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", code, "planId", "PRO_MONTHLY"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.originalPriceCents").value(2990))
            .andExpect(jsonPath("$.finalPriceCents").value(2691));
    }

    @Test
    @DisplayName("B6: Cortesia ativa protegida contra webhook REFUNDED e estorno de entrada R$ 0,00; estorno de entrada paga cancela e rebaixa")
    void shouldProtectCourtesyAccountAgainstRefundWhileCancelingPaidRefund() throws Exception {
        Cookie adminCookie = loginAsAdminCookie();
        AuthDto.AuthResponse courtesyUser = registerUser("courtesy-refund");

        // 1. Admin concede cortesia gratuita de 30 dias (COURTESY -> TRIALING, R$ 0)
        mockMvc.perform(post("/api/admin/billing/subscriptions/grant")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "userEmail", courtesyUser.email(),
                    "planId", "PRO_MONTHLY",
                    "periodDays", 30,
                    "isTrial", true,
                    "paymentMethod", "COURTESY",
                    "autoRenew", false,
                    "note", "Cortesia VIP"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("TRIALING"))
            .andExpect(jsonPath("$.priceCents").value(0))
            .andExpect(jsonPath("$.accountTier").value("PRO"));

        PaymentEntryEntity courtesyEntry = paymentEntryRepository.findAll().stream()
            .filter(e -> courtesyUser.email().equalsIgnoreCase(e.getUserEmail()))
            .findFirst()
            .orElseThrow();

        // 2. Webhook REFUNDED sem valor pago (amountCents=0) -> IGNORED_COURTESY_NO_CHARGE e usuário continua PRO TRIALING
        String zeroRefundPayload = objectMapper.writeValueAsString(Map.of(
            "eventId", "evt_ref_zero_" + UUID.randomUUID().toString().substring(0, 6),
            "provider", "APPLE_STORE",
            "eventType", "REFUNDED",
            "userEmail", courtesyUser.email(),
            "planId", "PRO_MONTHLY",
            "amountCents", 0
        ));
        String zeroSig = "sha256=" + billingService.computeHmacSha256Hex(zeroRefundPayload, appConfigService.getWebhookSecret());
        mockMvc.perform(post("/api/webhooks/billing")
                .header("X-Forma-Webhook-Signature", zeroSig)
                .contentType(MediaType.APPLICATION_JSON)
                .content(zeroRefundPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processingStatus").value("IGNORED_COURTESY_NO_CHARGE"));

        mockMvc.perform(get("/api/subscriptions/me")
                .header("Authorization", "Bearer " + courtesyUser.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("TRIALING"))
            .andExpect(jsonPath("$.accountTier").value("PRO"));

        // 3. Webhook REFUNDED com amountCents > 0 -> PROCESSED_COURTESY_PRESERVED (registra REFUND, mantém cortesia PRO ativa)
        String priorRefundPayload = objectMapper.writeValueAsString(Map.of(
            "eventId", "evt_ref_prev_" + UUID.randomUUID().toString().substring(0, 6),
            "provider", "STRIPE_TEST",
            "eventType", "REFUNDED",
            "userEmail", courtesyUser.email(),
            "planId", "PRO_MONTHLY",
            "amountCents", 2990
        ));
        String priorSig = "sha256=" + billingService.computeHmacSha256Hex(priorRefundPayload, appConfigService.getWebhookSecret());
        mockMvc.perform(post("/api/webhooks/billing")
                .header("X-Forma-Webhook-Signature", priorSig)
                .contentType(MediaType.APPLICATION_JSON)
                .content(priorRefundPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processingStatus").value("PROCESSED_COURTESY_PRESERVED"));

        mockMvc.perform(get("/api/subscriptions/me")
                .header("Authorization", "Bearer " + courtesyUser.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("TRIALING"))
            .andExpect(jsonPath("$.accountTier").value("PRO"));

        assertThat(paymentEntryRepository.findAll()).anyMatch(e ->
            courtesyUser.email().equalsIgnoreCase(e.getUserEmail())
                && "REFUND".equals(e.getEntryType())
                && "REFUNDED".equals(e.getStatus())
                && e.getNetAmountCents() == 2990
                && e.getDescription() != null
                && e.getDescription().contains("cortesia mantida")
        );

        // 4. Tentativa administrativa de estornar o lançamento gratuito/cortesia -> 400 Bad Request
        mockMvc.perform(post("/api/admin/billing/entries/" + courtesyEntry.getId() + "/status")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"REFUNDED\"}"))
            .andExpect(status().isBadRequest());

        // 5. Conta com assinatura paga via PIX: estorno cancela assinatura e rebaixa para FREE
        AuthDto.AuthResponse paidUser = registerUser("paid-refund");
        mockMvc.perform(post("/api/admin/billing/subscriptions/grant")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "userEmail", paidUser.email(),
                    "planId", "PRO_MONTHLY",
                    "periodDays", 30,
                    "isTrial", false,
                    "customPriceCents", 2990,
                    "paymentMethod", "PIX",
                    "autoRenew", true,
                    "note", "Pagamento PIX confirmado"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andExpect(jsonPath("$.accountTier").value("PRO"));

        PaymentEntryEntity paidEntry = paymentEntryRepository.findAll().stream()
            .filter(e -> paidUser.email().equalsIgnoreCase(e.getUserEmail()) && e.getNetAmountCents() == 2990)
            .findFirst()
            .orElseThrow();

        mockMvc.perform(post("/api/admin/billing/entries/" + paidEntry.getId() + "/status")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"status\": \"REFUNDED\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("REFUNDED"));

        mockMvc.perform(get("/api/subscriptions/me")
                .header("Authorization", "Bearer " + paidUser.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("CANCELED"))
            .andExpect(jsonPath("$.accountTier").value("FREE"));
    }

    @Test
    @DisplayName("B7: Endpoint unificado /api/admin/bootstrap exige autenticação admin e retorna payload completo sem cache HTTP")
    void shouldExposeUnifiedAdminBootstrapPayloadWithoutHttpCache() throws Exception {
        mockMvc.perform(get("/api/admin/bootstrap"))
            .andExpect(status().isUnauthorized());

        Cookie adminCookie = loginAsAdminCookie();
        mockMvc.perform(get("/api/admin/bootstrap")
                .cookie(adminCookie))
            .andExpect(status().isOk())
            .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
            .andExpect(jsonPath("$.username").value("admin"))
            .andExpect(jsonPath("$.overview.recentUsers").isArray())
            .andExpect(jsonPath("$.billing.plans").isArray())
            .andExpect(jsonPath("$.billing.plans").isNotEmpty())
            .andExpect(jsonPath("$.auditLogs").isArray())
            .andExpect(jsonPath("$.incidents").isArray())
            .andExpect(jsonPath("$.serverTimestampUtc").isNotEmpty());
    }

    @Test
    @DisplayName("B8: Admin aplica desconto na próxima cobrança do usuário, consome uma única vez na renovação e permite remoção manual")
    void shouldScheduleNextBillingDiscountConsumeOnceOnRenewalAndAllowClear() throws Exception {
        Cookie adminCookie = loginAsAdminCookie();
        AuthDto.AuthResponse user = registerUser("next-discount");

        MvcResult grantRes = mockMvc.perform(post("/api/admin/billing/subscriptions/grant")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "userEmail", user.email(),
                    "planId", "PRO_MONTHLY",
                    "periodDays", 30,
                    "isTrial", false,
                    "customPriceCents", 2990,
                    "paymentMethod", "PIX",
                    "autoRenew", true,
                    "note", "Assinatura mensal paga"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"))
            .andReturn();
        String subId = objectMapper.readTree(grantRes.getResponse().getContentAsString()).path("id").asText();

        // 1. Admin aplica -30% na próxima cobrança (2990 -> 2093 centavos)
        mockMvc.perform(post("/api/admin/billing/subscriptions/next-discount")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "userEmail", user.email(),
                    "discountType", "PERCENT",
                    "discountPercent", 30,
                    "note", "Bonificação VIP 30%"
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextBillingDiscountPercent").value(30))
            .andExpect(jsonPath("$.nextBillingDiscountCents").value(897))
            .andExpect(jsonPath("$.nextBillingPriceCents").value(2093))
            .andExpect(jsonPath("$.nextBillingDiscountLabel").value(org.hamcrest.Matchers.containsString("-30%")));

        // 2. App do usuário vê o desconto agendado em /api/subscriptions/me
        mockMvc.perform(get("/api/subscriptions/me")
                .header("Authorization", "Bearer " + user.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextBillingPriceCents").value(2093))
            .andExpect(jsonPath("$.nextBillingDiscountLabel").value(org.hamcrest.Matchers.containsString("20,93")));

        // 3. Renovação via webhook consome o desconto uma única vez e registra 2093 líquido / 897 desconto
        String eventId = "evt_renew_disc_" + UUID.randomUUID().toString().substring(0, 6);
        String renewPayload = objectMapper.writeValueAsString(Map.of(
            "eventId", eventId,
            "provider", "STRIPE_TEST",
            "eventType", "SUBSCRIPTION_RENEWED",
            "userEmail", user.email(),
            "planId", "PRO_MONTHLY"
        ));
        String validSig = "sha256=" + billingService.computeHmacSha256Hex(renewPayload, appConfigService.getWebhookSecret());
        mockMvc.perform(post("/api/webhooks/billing")
                .header("X-Forma-Webhook-Signature", validSig)
                .contentType(MediaType.APPLICATION_JSON)
                .content(renewPayload))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.processingStatus").value("PROCESSED"));

        PaymentEntryEntity renewedEntry = paymentEntryRepository.findAll().stream()
            .filter(e -> ("WH-" + eventId).equals(e.getReferenceCode()))
            .findFirst()
            .orElseThrow();
        assertThat(renewedEntry.getGrossAmountCents()).isEqualTo(2990);
        assertThat(renewedEntry.getDiscountCents()).isEqualTo(897);
        assertThat(renewedEntry.getNetAmountCents()).isEqualTo(2093);

        mockMvc.perform(get("/api/subscriptions/me")
                .header("Authorization", "Bearer " + user.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextBillingPriceCents").isEmpty())
            .andExpect(jsonPath("$.priceCents").value(2990));

        // 4. Admin agenda desconto fixo (R$ 5,00) e depois remove via DELETE
        mockMvc.perform(post("/api/admin/billing/subscriptions/next-discount")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "subscriptionId", subId,
                    "discountType", "FIXED_CENTS",
                    "discountCents", 500
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextBillingDiscountCents").value(500))
            .andExpect(jsonPath("$.nextBillingPriceCents").value(2490));

        mockMvc.perform(delete("/api/admin/billing/subscriptions/" + subId + "/next-discount")
                .cookie(adminCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nextBillingPriceCents").isEmpty())
            .andExpect(jsonPath("$.nextBillingDiscountLabel").isEmpty());
    }

    @Test
    @DisplayName("B9: Governança enterprise de cupons: limite finito obrigatório, auto-esgotamento, vínculo por e-mail, expansão de cota e bloqueio anti-enumeração (HTTP 429)")
    void shouldEnforceEnterprisePrivateCouponQuotaExhaustionEmailLockAndAntiEnumeration() throws Exception {
        Cookie adminCookie = loginAsAdminCookie();
        AuthDto.AuthResponse userA = registerUser("ent-a");
        AuthDto.AuthResponse userB = registerUser("ent-b");
        AuthDto.AuthResponse userC = registerUser("ent-c");

        // 1. Rejeitar limite inválido (maxRedemptions = 0)
        mockMvc.perform(post("/api/admin/billing/coupons")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", "ZERO_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                    "couponType", "FREE_PERIOD_DAYS",
                    "freePeriodDays", 7,
                    "maxRedemptions", 0
                ))))
            .andExpect(status().isBadRequest());

        // 2. Omitir maxRedemptions aplica o padrão enterprise de uso único individual (1)
        mockMvc.perform(post("/api/admin/billing/coupons")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", "DEF1_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(),
                    "couponType", "FREE_PERIOD_DAYS",
                    "freePeriodDays", 7
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxRedemptions").value(1))
            .andExpect(jsonPath("$.remainingRedemptions").value(1))
            .andExpect(jsonPath("$.exhausted").value(false));

        // 3. Cupom com cota = 2 e 1 por usuário: esgota após User A e User B, desativa automaticamente, e libera para User C só após ampliar cota para 3
        String quotaCode = "QUOTA2_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        MvcResult quotaRes = mockMvc.perform(post("/api/admin/billing/coupons")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", quotaCode,
                    "description", "Voucher limitado a 2 usos",
                    "couponType", "FREE_PERIOD_DAYS",
                    "freePeriodDays", 7,
                    "maxRedemptions", 2,
                    "perUserLimit", 1,
                    "active", true
                ))))
            .andExpect(status().isOk())
            .andReturn();
        String quotaCouponId = objectMapper.readTree(quotaRes.getResponse().getContentAsString()).path("id").asText();

        mockMvc.perform(post("/api/subscriptions/redeem-coupon")
                .header("Authorization", "Bearer " + userA.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", quotaCode, "planId", "PRO_MONTHLY"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redeemed").value(true));

        mockMvc.perform(post("/api/subscriptions/redeem-coupon")
                .header("Authorization", "Bearer " + userB.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", quotaCode, "planId", "PRO_MONTHLY"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redeemed").value(true));

        // User C bloqueado quando cota chegou a 2/2
        mockMvc.perform(post("/api/subscriptions/validate-coupon")
                .header("Authorization", "Bearer " + userC.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", quotaCode, "planId", "PRO_MONTHLY"))))
            .andExpect(status().isBadRequest());

        // Tentar reativar cupom esgotado sem aumentar cota falha (400)
        mockMvc.perform(post("/api/admin/billing/coupons/" + quotaCouponId + "/toggle")
                .cookie(adminCookie))
            .andExpect(status().isBadRequest());

        // Admin amplia limite para 3 e reativa -> User C passa a validar com sucesso
        mockMvc.perform(put("/api/admin/billing/coupons/" + quotaCouponId)
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "couponType", "FREE_PERIOD_DAYS",
                    "freePeriodDays", 7,
                    "maxRedemptions", 3,
                    "perUserLimit", 1,
                    "active", true
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.maxRedemptions").value(3))
            .andExpect(jsonPath("$.remainingRedemptions").value(1))
            .andExpect(jsonPath("$.exhausted").value(false))
            .andExpect(jsonPath("$.active").value(true));

        mockMvc.perform(post("/api/subscriptions/validate-coupon")
                .header("Authorization", "Bearer " + userC.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", quotaCode, "planId", "PRO_MONTHLY"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.valid").value(true));

        // 4. Cupom exclusivo vinculado ao e-mail de User C
        String lockedCode = "MAIL_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
        mockMvc.perform(post("/api/admin/billing/coupons")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", lockedCode,
                    "couponType", "FREE_PERIOD_DAYS",
                    "freePeriodDays", 14,
                    "maxRedemptions", 1,
                    "perUserLimit", 1,
                    "restrictedToEmail", userC.email(),
                    "active", true
                ))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.restrictedToEmail").value(userC.email().toLowerCase()));

        mockMvc.perform(post("/api/subscriptions/validate-coupon")
                .header("Authorization", "Bearer " + userA.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", lockedCode, "planId", "PRO_MONTHLY"))))
            .andExpect(status().isBadRequest());

        mockMvc.perform(post("/api/subscriptions/redeem-coupon")
                .header("Authorization", "Bearer " + userC.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of("code", lockedCode, "planId", "PRO_MONTHLY"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.redeemed").value(true));

        // 5. Anti-enumeração: 5 códigos inválidos -> 400; 6ª tentativa -> HTTP 429 + incidente COUPON_ENUMERATION_BLOCKED
        AuthDto.AuthResponse enumUser = registerUser("enum-guard");
        String enumIp = "198.51.100." + (100 + (int) (Math.random() * 100));
        for (int i = 0; i < 5; i++) {
            mockMvc.perform(post("/api/subscriptions/validate-coupon")
                    .header("Authorization", "Bearer " + enumUser.token())
                    .header("X-Forwarded-For", enumIp)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(Map.of(
                        "code", "CHUTE_INVALIDO_" + i,
                        "planId", "PRO_MONTHLY"
                    ))))
                .andExpect(status().isBadRequest());
        }

        mockMvc.perform(post("/api/subscriptions/validate-coupon")
                .header("Authorization", "Bearer " + enumUser.token())
                .header("X-Forwarded-For", enumIp)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "code", "CHUTE_INVALIDO_6",
                    "planId", "PRO_MONTHLY"
                ))))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.rateLimited").value(true));

        assertThat(securityIncidentLogRepository.findAll())
            .anyMatch(inc -> "COUPON_ENUMERATION_BLOCKED".equals(inc.getIncidentType()));
    }
}

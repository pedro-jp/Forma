package com.forma.backend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.forma.backend.dto.AuthDto;
import com.forma.backend.entity.BillingWebhookEventEntity;
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
}

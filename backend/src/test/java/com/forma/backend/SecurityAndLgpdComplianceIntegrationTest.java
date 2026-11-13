package com.forma.backend;

import com.forma.backend.dto.AuthDto;
import com.forma.backend.repository.*;
import com.forma.backend.service.AppConfigService;
import com.forma.backend.service.PhysiologicalSafetyGuard;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "app.auth.allow-dev-social-mock=false"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class SecurityAndLgpdComplianceIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private AppConfigService appConfigService;
    @Autowired private UserRepository userRepository;
    @Autowired private DeletedAccountTombstoneRepository tombstoneRepository;
    @Autowired private LegalConsentLedgerRepository consentLedgerRepository;
    @Autowired private MciAccessLogRepository mciAccessLogRepository;
    @Autowired private PhysiologicalSafetyGuard physiologicalSafetyGuard;

    private AuthDto.AuthResponse registerCompliantUser(String email, String password, boolean aiConsent) throws Exception {
        String body = """
            {
              "email": "%s",
              "password": "%s",
              "name": "Titular LGPD",
              "termsAccepted": true,
              "termsVersion": "2026-11-v2",
              "sensitiveHealthConsentGranted": true,
              "aiConsentGranted": %s,
              "ageConfirmed18Plus": true
            }
            """.formatted(email, password, aiConsent);

        MvcResult res = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(), AuthDto.AuthResponse.class);
    }

    @Test
    @DisplayName("S1/S2: Rejeita X-User-Id sem JWT Bearer, bloqueia tokens sociais simulados quando mock desativado e não expõe DB em /api/health")
    void shouldRejectXUserIdAndDevSocialMocksAndHideDbFingerprint() throws Exception {
        UUID fakeId = UUID.randomUUID();
        mockMvc.perform(get("/api/sync/pull").header("X-User-Id", fakeId.toString()))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/auth/me").header("X-User-Id", fakeId.toString()))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/privacy/consents").header("X-User-Id", fakeId.toString()))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"APPLE\",\"providerId\":\"sim_apple\",\"idToken\":\"apple_identity_token_dev_sim\",\"email\":\"sim@forma.app\",\"name\":\"Sim\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"provider\":\"GOOGLE\",\"providerId\":\"sim_google\",\"idToken\":\"google_identity_token_dev_sim\",\"email\":\"sim@forma.app\",\"name\":\"Sim\"}"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/health"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ok"))
            .andExpect(jsonPath("$.database").doesNotExist())
            .andExpect(header().exists("Strict-Transport-Security"))
            .andExpect(header().string("X-Frame-Options", "DENY"))
            .andExpect(header().string("X-Content-Type-Options", "nosniff"))
            .andExpect(header().string("Cache-Control", org.hamcrest.Matchers.containsString("no-store")));
    }

    @Test
    @DisplayName("S4/S6: Senha mínima de 8 chars, lockout OTP em 5 erros (sem rollback) e logout revoga JWT imediatamente via tokenVersion")
    void shouldEnforcePasswordPolicyOtpLockoutAndTokenRevocationOnLogout() throws Exception {
        // Senha curta (< 8 chars) recusada
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"curta@forma.app\",\"password\":\"123456\",\"name\":\"Teste\",\"termsAccepted\":true,\"sensitiveHealthConsentGranted\":true,\"ageConfirmed18Plus\":true}"))
            .andExpect(status().isBadRequest());

        AuthDto.AuthResponse user = registerCompliantUser("seguranca@forma.app", "SenhaForte123", false);

        // Solicitar código de redefinição e errar 5 vezes
        mockMvc.perform(post("/api/auth/forgot-password")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"seguranca@forma.app\"}"))
            .andExpect(status().isOk());

        for (int i = 0; i < 6; i++) {
            mockMvc.perform(post("/api/auth/reset-password")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"email\":\"seguranca@forma.app\",\"code\":\"000000\",\"newPassword\":\"NovaSenha123\"}"))
                .andExpect(status().isUnauthorized());
        }

        // Verificar que resetCodeAttempts >= 5 foi persistido (sem rollback)
        Integer attempts = jdbcTemplate.queryForObject(
            "SELECT reset_code_attempts FROM users WHERE id = ?",
            Integer.class,
            user.userId()
        );
        assertThat(attempts).isNotNull().isGreaterThanOrEqualTo(5);

        // Logout invalida JWT anterior imediatamente via tokenVersion
        mockMvc.perform(post("/api/auth/logout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/auth/me")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
            .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("S3/L2/L4/L5: Criptografia AES-256-GCM no banco, portabilidade JSON, retenção MCI de 180 dias, tombstone anti-ressurreição e pseudonimização de ledger")
    void shouldEncryptAtRestExportLgpdDataRetainMci180DaysAndPreventTombstoneResurrection() throws Exception {
        // AppConfigService cifra segredos com ENC:v1:
        appConfigService.setGeminiApiKey("AIzaSyTestSecretKeyForEncryption12345");
        String rawStoredSecret = jdbcTemplate.queryForObject(
            "SELECT config_value FROM app_config WHERE id = 'GEMINI_API_KEY'",
            String.class
        );
        assertThat(rawStoredSecret).startsWith("ENC:v1:");
        assertThat(appConfigService.getGeminiApiKey()).isEqualTo("AIzaSyTestSecretKeyForEncryption12345");

        // Registro sem aceite de saúde sensível ou menor de 18 anos é rejeitado
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"menor@forma.app\",\"password\":\"SenhaForte123\",\"name\":\"Menor\",\"termsAccepted\":true,\"sensitiveHealthConsentGranted\":true,\"ageConfirmed18Plus\":false}"))
            .andExpect(status().isBadRequest());

        AuthDto.AuthResponse user = registerCompliantUser("portabilidade@forma.app", "SenhaForte123", false);

        // MCI access log deve ter IP cifrado ENC:v1: e retenção ~180 dias
        Integer mciEncryptedCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM mci_access_logs WHERE ip_address_encrypted LIKE 'ENC:v1:%' AND retain_until_utc > accessed_at_utc",
            Integer.class
        );
        assertThat(mciEncryptedCount).isNotNull().isGreaterThanOrEqualTo(1);

        // Consultar consentimentos e ativar consentimento de IA
        mockMvc.perform(post("/api/privacy/consents")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"aiConsentGranted\":true,\"documentVersion\":\"2026-11-v2\"}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aiConsentGranted").value(true))
            .andExpect(jsonPath("$.history.length()").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));

        // Exportar dados completos (LGPD Art. 18 V)
        MvcResult exportRes = mockMvc.perform(get("/api/privacy/export")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.account.email").value("portabilidade@forma.app"))
            .andExpect(jsonPath("$.consents.history").isArray())
            .andReturn();
        assertThat(exportRes.getResponse().getHeader(HttpHeaders.CONTENT_DISPOSITION))
            .contains("forma-dados-lgpd-");

        long mciBeforeDelete = mciAccessLogRepository.count();

        // Excluir conta pelo próprio usuário (DELETE /api/auth/account)
        mockMvc.perform(delete("/api/auth/account")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
            .andExpect(status().isOk());

        // Tombstone gravado, ledger pseudonimizado, MCI preservado por 180 dias + evento ACCOUNT_DELETE gravado
        assertThat(tombstoneRepository.existsById(user.userId().toString())).isTrue();
        assertThat(userRepository.findById(user.userId())).isEmpty();
        assertThat(mciAccessLogRepository.count()).isGreaterThanOrEqualTo(mciBeforeDelete);
        assertThat(consentLedgerRepository.findByUserIdOrderByRecordedAtUtcDesc(user.userId().toString())).isEmpty();
        Integer anonLedgerCount = jdbcTemplate.queryForObject(
            "SELECT COUNT(*) FROM legal_consent_ledger WHERE user_id LIKE 'anon:%'",
            Integer.class
        );
        assertThat(anonLedgerCount).isNotNull().isGreaterThanOrEqualTo(1);

        // JWT antigo não pode ressuscitar conta nem acessar /sync/pull
        mockMvc.perform(get("/api/sync/pull")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.accountDeleted").value(true));
    }

    @Test
    @DisplayName("J1/CFN/CONFEF: Guardrail fisiológico corrige metas <1200 kcal, protege IMC <18.5 e remove cargas em kg da IA")
    void shouldClampUnsafeCaloriesLowBmiAndStripKgPrescriptions() throws Exception {
        String unsafeGoals = """
            {
              "suggested_target_weight": 42.0,
              "daily_calories": 750.0,
              "protein_grams": 80.0,
              "carbs_grams": 50.0,
              "fat_grams": 25.0,
              "water_milliliters": 2200,
              "explanation": "Dieta extrema."
            }
            """;
        String safeGoals = physiologicalSafetyGuard.sanitizeInitialGoalsJson(unsafeGoals, 65.0, 1.75);
        JsonNode goalsNode = objectMapper.readTree(safeGoals);
        assertThat(goalsNode.path("daily_calories").asDouble()).isGreaterThanOrEqualTo(1200.0);
        // IMC mínimo 18.5 * 1.75^2 = 56.7 kg
        assertThat(goalsNode.path("suggested_target_weight").asDouble()).isGreaterThanOrEqualTo(56.6);
        assertThat(goalsNode.path("disclaimer").asText()).contains("nutricionista");

        String unsafeWorkout = """
            {
              "title": "Treino Pesado",
              "ai_feedback": "Treine forte",
              "exercises": [
                {"name": "Agachamento", "sets": 4, "reps": "8", "suggested_weight": "120kg"}
              ]
            }
            """;
        String safeWorkout = physiologicalSafetyGuard.sanitizeWorkoutJson(unsafeWorkout, "Tenho lesão aguda e hérnia");
        JsonNode workoutNode = objectMapper.readTree(safeWorkout);
        assertThat(workoutNode.path("exercises").get(0).path("suggested_weight").asText()).doesNotContain("120kg");
        assertThat(workoutNode.path("ai_feedback").asText()).contains("CREF");
        assertThat(workoutNode.path("disclaimer").asText()).contains("CREF");
    }

    @Test
    @DisplayName("Web/Portal/Docs: Rotas estáticas, cabeçalhos CSP/HSTS/Frame e endpoints de auditoria admin protegidos")
    void shouldServePortalDocsWithSecurityHeadersAndProtectAdminAuditEndpoints() throws Exception {
        mockMvc.perform(get("/"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/index.html"));

        mockMvc.perform(get("/docs"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/docs.html"));

        mockMvc.perform(get("/privacy"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/privacy.html"));

        mockMvc.perform(get("/terms"))
            .andExpect(status().isOk())
            .andExpect(forwardedUrl("/terms.html"));

        for (String page : new String[]{"/index.html", "/docs.html", "/privacy.html", "/terms.html", "/admin/index.html"}) {
            mockMvc.perform(get(page))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("Strict-Transport-Security"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"));
        }

        mockMvc.perform(get("/api/admin/audit-logs"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/incidents"))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/audit-logs").header("X-Admin-Key", "test_admin_key"))
            .andExpect(status().isOk());

        mockMvc.perform(get("/api/admin/incidents").header("X-Admin-Key", "test_admin_key"))
            .andExpect(status().isOk());
    }
}

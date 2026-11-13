package com.forma.backend;

import com.forma.backend.dto.AdminDto;
import com.forma.backend.dto.AuthDto;
import com.forma.backend.entity.AiRequestLogEntity;
import com.forma.backend.repository.AiRequestLogRepository;
import com.forma.backend.security.AuthContextResolver;
import com.forma.backend.service.AppConfigService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
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

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AdminUserControlIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AiRequestLogRepository aiRequestLogRepository;

    @Autowired
    private AppConfigService appConfigService;

    @BeforeEach
    void resetGlobalFlags() {
        appConfigService.setAiBlocked(false);
    }

    private Cookie loginAsAdminCookie() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", "admin",
                    "password", "test_admin_password"
                ))))
            .andExpect(status().isOk())
            .andExpect(cookie().exists(AuthContextResolver.ADMIN_COOKIE_NAME))
            .andExpect(jsonPath("$.status").value("authenticated"))
            .andReturn();

        Cookie adminCookie = res.getResponse().getCookie(AuthContextResolver.ADMIN_COOKIE_NAME);
        assertThat(adminCookie).isNotNull();
        return adminCookie;
    }

    private AuthDto.AuthResponse registerAppUser(String email) throws Exception {
        MvcResult res = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthDto.RegisterRequest(email, "Senha1234", "Atleta Teste"))))
            .andExpect(status().isOk())
            .andReturn();
        return objectMapper.readValue(res.getResponse().getContentAsString(), AuthDto.AuthResponse.class);
    }

    @Test
    @DisplayName("Autenticação Admin via Cookie HttpOnly + listagem exclusiva de modelos Gemini 3.x")
    void shouldAuthenticateAdminAndFilterDeprecatedGeminiModels() throws Exception {
        // Sem credenciais -> 401
        mockMvc.perform(get("/api/admin/overview"))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.authenticated").value(false));

        Cookie adminCookie = loginAsAdminCookie();

        mockMvc.perform(get("/api/admin/auth-check").cookie(adminCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authenticated").value(true));

        MvcResult modelsRes = mockMvc.perform(get("/api/admin/models").cookie(adminCookie))
            .andExpect(status().isOk())
            .andReturn();

        JsonNode modelsArray = objectMapper.readTree(modelsRes.getResponse().getContentAsString()).path("models");
        assertThat(modelsArray.isArray()).isTrue();
        assertThat(modelsArray.size()).isGreaterThanOrEqualTo(3);

        for (JsonNode model : modelsArray) {
            String modelId = model.path("id").asText();
            assertThat(modelId).startsWith("gemini-3.");
            assertThat(modelId).doesNotContain("1.5").doesNotContain("2.0").doesNotContain("2.5");
            assertThat(modelId).isNotEqualTo("gemini-flash-lite-latest");
        }

        // Senha padrão legada (admin123) não autentica nem por login nem por X-Admin-Key
        mockMvc.perform(post("/api/admin/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", "admin",
                    "password", "admin123"
                ))))
            .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/admin/overview")
                .header("X-Admin-Key", "admin123"))
            .andExpect(status().isUnauthorized());

        // Login sob HTTPS (X-Forwarded-Proto: https) emite cookie HttpOnly + Secure
        MvcResult httpsLoginRes = mockMvc.perform(post("/api/admin/login")
                .header("X-Forwarded-Proto", "https")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(Map.of(
                    "username", "admin",
                    "password", "test_admin_password"
                ))))
            .andExpect(status().isOk())
            .andReturn();

        String setCookieHeader = httpsLoginRes.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookieHeader).contains("HttpOnly").contains("Secure");
    }

    @Test
    @DisplayName("Admin pausa/retoma conta, bloqueia IA individual, impõe cota diária (429) e exclui conta permanentemente (401 accountDeleted)")
    void shouldEnforcePauseAiQuotaAndPermanentDelete() throws Exception {
        Cookie adminCookie = loginAsAdminCookie();
        AuthDto.AuthResponse user = registerAppUser("controle@forma.app");

        // 1. Pausar conta -> 403 accountPaused no Sync
        mockMvc.perform(post("/api/admin/users/" + user.userId() + "/status")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AdminDto.UserStatusRequest(true, "Manutenção"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("PAUSED"));

        mockMvc.perform(get("/api/sync/pull")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.accountPaused").value(true));

        // 2. Retomar conta -> 200 OK no Sync
        mockMvc.perform(post("/api/admin/users/" + user.userId() + "/status")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AdminDto.UserStatusRequest(false, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("ACTIVE"));

        mockMvc.perform(get("/api/sync/pull")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountTier").value("FREE"));

        // 2b. Conta FREE bloqueia IA por padrão (403) até ser promovida para PRO pelo Admin
        mockMvc.perform(post("/api/ai/analyze-workout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Supino 4x10\",\"userWeight\":75.0,\"userName\":\"Atleta\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.aiBlockedForUser").value(true));

        // 2c. Admin promove conta para PRO -> reflete no retorno e no /api/sync/pull
        mockMvc.perform(post("/api/admin/users/" + user.userId() + "/tier")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AdminDto.UserTierRequest("PRO"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountTier").value("PRO"));

        mockMvc.perform(get("/api/sync/pull")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accountTier").value("PRO"));

        // 3. Bloquear IA individualmente -> 403 aiBlockedForUser
        mockMvc.perform(post("/api/admin/users/" + user.userId() + "/ai-access")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AdminDto.UserAiAccessRequest(true, null))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.aiBlocked").value(true));

        mockMvc.perform(post("/api/ai/analyze-workout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Supino 4x10\",\"userWeight\":75.0,\"userName\":\"Atleta\"}"))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.aiBlockedForUser").value(true));

        // 4. Liberar IA individual mas impor cota diária de 2 requisições -> 429 quotaExceeded quando atingir 2 usos hoje
        mockMvc.perform(post("/api/admin/users/" + user.userId() + "/ai-access")
                .cookie(adminCookie)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AdminDto.UserAiAccessRequest(false, 2))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.dailyAiLimit").value(2));

        aiRequestLogRepository.save(new AiRequestLogEntity(user.userId(), "/api/ai/analyze-meal", "Refeição 1", "SUCCESS", 350L));
        aiRequestLogRepository.save(new AiRequestLogEntity(user.userId(), "/api/ai/analyze-meal", "Refeição 2", "SUCCESS", 410L));

        mockMvc.perform(post("/api/ai/analyze-workout")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token())
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"description\":\"Agachamento 4x8\",\"userWeight\":75.0,\"userName\":\"Atleta\"}"))
            .andExpect(status().isTooManyRequests())
            .andExpect(jsonPath("$.quotaExceeded").value(true));

        // 5. Excluir permanentemente -> 401 com accountDeleted: true ao tentar usar o JWT antigo
        mockMvc.perform(delete("/api/admin/users/" + user.userId())
                .cookie(adminCookie))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.status").value("deleted"));

        mockMvc.perform(get("/api/sync/pull")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + user.token()))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.accountDeleted").value(true));
    }
}

package com.forma.backend;

import com.forma.backend.dto.AdminDto;
import com.forma.backend.dto.AuthDto;
import com.forma.backend.entity.User;
import com.forma.backend.repository.UserRepository;
import com.forma.backend.service.AdminUserService;
import com.forma.backend.service.AppConfigService;
import com.forma.backend.service.SocialAuthVerificationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.spec.ECGenParameterSpec;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private AdminUserService adminUserService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private AppConfigService appConfigService;

    @Autowired
    private SocialAuthVerificationService socialAuthVerificationService;

    @Test
    @DisplayName("GET /api/auth/config retorna googleClientId público")
    void shouldReturnPublicAuthConfig() throws Exception {
        mockMvc.perform(get("/api/auth/config"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.googleClientId").value("test-google-client-id.apps.googleusercontent.com"));
    }

    @Test
    @DisplayName("POST /api/auth/register cria usuário, normaliza e-mail, gera JWT e bloqueia duplicidade")
    void shouldRegisterUserAndRejectDuplicateEmail() throws Exception {
        AuthDto.RegisterRequest registerReq = new AuthDto.RegisterRequest(
            "  Joao.Atleta@Forma.App  ",
            "SenhaForte123",
            "João Pedro"
        );

        MvcResult result = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.email").value("joao.atleta@forma.app"))
            .andExpect(jsonPath("$.name").value("João Pedro"))
            .andExpect(jsonPath("$.authProvider").value("EMAIL"))
            .andExpect(jsonPath("$.profile.targetCalorieIntake").value(2800.0))
            .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertThat(body.path("userId").asText()).isNotBlank();

        // Tentativa de recadastro com o mesmo e-mail deve retornar 400 Bad Request
        mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error").value("E-mail já cadastrado."));
    }

    @Test
    @DisplayName("POST /api/auth/login valida credenciais, retorna 401 para senha errada e 403 para conta pausada")
    void shouldAuthenticateRejectWrongPasswordAndBlockPausedAccount() throws Exception {
        AuthDto.RegisterRequest registerReq = new AuthDto.RegisterRequest(
            "login@forma.app",
            "MinhaSenha2026",
            "Atleta Forma"
        );

        MvcResult regResult = mockMvc.perform(post("/api/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(registerReq)))
            .andExpect(status().isOk())
            .andReturn();

        UUID userId = UUID.fromString(
            objectMapper.readTree(regResult.getResponse().getContentAsString()).path("userId").asText()
        );

        // 1. Login válido -> 200 OK
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthDto.LoginRequest("LOGIN@forma.app", "MinhaSenha2026"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.token").isNotEmpty())
            .andExpect(jsonPath("$.userId").value(userId.toString()));

        // 2. Senha incorreta -> 401 Unauthorized
        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthDto.LoginRequest("login@forma.app", "SenhaIncorreta"))))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.error").value("Usuário ou senha incorretos."));

        // 3. Conta pausada pelo admin -> 403 Forbidden com accountPaused: true
        adminUserService.updateStatus(userId, new AdminDto.UserStatusRequest(true, "Revisão cadastral"));

        mockMvc.perform(post("/api/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(new AuthDto.LoginRequest("login@forma.app", "MinhaSenha2026"))))
            .andExpect(status().isForbidden())
            .andExpect(jsonPath("$.accountPaused").value(true));
    }

    @Test
    @DisplayName("POST /api/auth/social autentica Sign in with Apple / Google e reaproveita conta existente")
    void shouldAuthenticateSocialLoginAndReuseExistingUser() throws Exception {
        AuthDto.SocialLoginRequest appleReq = new AuthDto.SocialLoginRequest(
            "APPLE",
            "apple_identity_token_dev_sim",
            "apple.user@privaterelay.appleid.com",
            "Ana Clara",
            "apple_sub_001",
            "nonce_123"
        );

        MvcResult firstLogin = mockMvc.perform(post("/api/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(appleReq)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.authProvider").value("APPLE"))
            .andExpect(jsonPath("$.name").value("Ana Clara"))
            .andReturn();

        String firstUserId = objectMapper.readTree(firstLogin.getResponse().getContentAsString())
            .path("userId").asText();

        // Novo login social com o mesmo e-mail deve manter o mesmo userId
        MvcResult secondLogin = mockMvc.perform(post("/api/auth/social")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(appleReq)))
            .andExpect(status().isOk())
            .andReturn();

        String secondUserId = objectMapper.readTree(secondLogin.getResponse().getContentAsString())
            .path("userId").asText();

        assertThat(secondUserId).isEqualTo(firstUserId);
    }

    @Test
    @DisplayName("DELETE /api/auth/account assina client_secret ES256 e revoga tokens reais de Apple e Google antes de apagar o banco")
    void shouldRevokeAppleAndGoogleTokensWhenDeletingAccount() throws Exception {
        List<String> appleTokenBodies = new CopyOnWriteArrayList<>();
        List<String> appleRevokeBodies = new CopyOnWriteArrayList<>();
        List<String> googleRevokeBodies = new CopyOnWriteArrayList<>();

        HttpServer mockOAuthServer = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        mockOAuthServer.createContext("/apple/token", exchange -> {
            String reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            appleTokenBodies.add(reqBody);
            String tokenValue = reqBody.contains("apple_auth_code_delete")
                ? "apple_refresh_fresh_from_delete"
                : "apple_refresh_stored_from_login";
            byte[] resp = ("{\"refresh_token\":\"" + tokenValue + "\"}").getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, resp.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(resp);
            }
        });
        mockOAuthServer.createContext("/apple/revoke", exchange -> {
            String reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            appleRevokeBodies.add(reqBody);
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        mockOAuthServer.createContext("/google/revoke", exchange -> {
            String reqBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            googleRevokeBodies.add(reqBody);
            exchange.sendResponseHeaders(200, 0);
            exchange.close();
        });
        mockOAuthServer.start();

        int port = mockOAuthServer.getAddress().getPort();
        String baseUrl = "http://127.0.0.1:" + port;

        try {
            // Gera par de chaves EC P-256 (.p8 PKCS#8) real para testar a assinatura ES256 do client_secret da Apple
            KeyPairGenerator kpg = KeyPairGenerator.getInstance("EC");
            kpg.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair kp = kpg.generateKeyPair();
            String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getEncoder().encodeToString(kp.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----";

            appConfigService.setAppleAuthConfig("Q42LJ4LG32", "TESTKEY123", pem);
            socialAuthVerificationService.overrideOAuthEndpointsForTesting(
                baseUrl + "/apple/token",
                baseUrl + "/apple/revoke",
                baseUrl + "/google/revoke"
            );

            // 1. Fluxo Sign in with Apple -> troca authorizationCode no login e revoga ambos os tokens na exclusão
            AuthDto.SocialLoginRequest appleLogin = new AuthDto.SocialLoginRequest(
                "APPLE",
                "apple_identity_token_dev_sim",
                "revoke.apple@privaterelay.appleid.com",
                "Apple Revoke",
                "apple_sub_revoke_01",
                "nonce_apple",
                false,
                true,
                "apple_auth_code_login",
                null
            );

            MvcResult appleRes = mockMvc.perform(post("/api/auth/social")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(appleLogin)))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode appleJson = objectMapper.readTree(appleRes.getResponse().getContentAsString());
            UUID appleUserId = UUID.fromString(appleJson.path("userId").asText());
            String appleJwt = appleJson.path("token").asText();

            User savedAppleUser = userRepository.findById(appleUserId).orElseThrow();
            assertThat(savedAppleUser.getAppleRefreshToken()).isEqualTo("apple_refresh_stored_from_login");

            mockMvc.perform(delete("/api/auth/account")
                    .header("Authorization", "Bearer " + appleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new AuthDto.DeleteAccountRequest("apple_auth_code_delete", null))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"));

            assertThat(appleTokenBodies).hasSize(2);
            assertThat(appleRevokeBodies).hasSize(2);
            assertThat(appleRevokeBodies.get(0)).contains("token=apple_refresh_fresh_from_delete", "token_type_hint=refresh_token", "client_id=jota.Forma");
            assertThat(appleRevokeBodies.get(1)).contains("token=apple_refresh_stored_from_login");
            assertThat(userRepository.findById(appleUserId)).isEmpty();

            // 2. Fluxo Google Sign-In -> persiste google_refresh_token e revoga no endpoint OAuth2 do Google
            AuthDto.SocialLoginRequest googleLogin = new AuthDto.SocialLoginRequest(
                "GOOGLE",
                "google_identity_token_dev_sim",
                "revoke.google@gmail.com",
                "Google Revoke",
                "google_sub_revoke_01",
                "nonce_google",
                false,
                true,
                null,
                "google_refresh_token_stored_456"
            );

            MvcResult googleRes = mockMvc.perform(post("/api/auth/social")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(googleLogin)))
                .andExpect(status().isOk())
                .andReturn();

            JsonNode googleJson = objectMapper.readTree(googleRes.getResponse().getContentAsString());
            UUID googleUserId = UUID.fromString(googleJson.path("userId").asText());
            String googleJwt = googleJson.path("token").asText();

            User savedGoogleUser = userRepository.findById(googleUserId).orElseThrow();
            assertThat(savedGoogleUser.getGoogleRefreshToken()).isEqualTo("google_refresh_token_stored_456");

            mockMvc.perform(delete("/api/auth/account")
                    .header("Authorization", "Bearer " + googleJwt)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(new AuthDto.DeleteAccountRequest(null, "google_access_token_client_789"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("deleted"));

            assertThat(googleRevokeBodies).isNotEmpty();
            assertThat(googleRevokeBodies.get(0)).contains("token=google_access_token_client_789");
            assertThat(userRepository.findById(googleUserId)).isEmpty();
        } finally {
            socialAuthVerificationService.overrideOAuthEndpointsForTesting(null, null, null);
            mockOAuthServer.stop(0);
        }
    }
}

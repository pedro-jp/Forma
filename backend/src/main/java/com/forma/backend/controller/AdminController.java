package com.forma.backend.controller;

import com.forma.backend.dto.AdminDto;
import com.forma.backend.exception.DomainExceptions.InvalidCredentialsException;
import com.forma.backend.repository.*;
import com.forma.backend.security.AuthContextResolver;
import com.forma.backend.service.AdminUserService;
import com.forma.backend.service.AppConfigService;
import com.forma.backend.service.JwtService;
import com.forma.backend.websocket.AdminEventPublisherService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final UserRepository userRepository;
    private final FoodEntryRepository foodEntryRepository;
    private final WorkoutEntryRepository workoutEntryRepository;
    private final AiRequestLogRepository aiRequestLogRepository;
    private final AdminUserService adminUserService;
    private final AppConfigService appConfigService;
    private final JwtService jwtService;
    private final AuthContextResolver authContextResolver;
    private final AdminEventPublisherService adminEventPublisherService;
    private final String configuredAdminUsername;
    private final String configuredAdminPassword;
    private final String configuredAdminKey;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdminController(
        UserRepository userRepository,
        FoodEntryRepository foodEntryRepository,
        WorkoutEntryRepository workoutEntryRepository,
        AiRequestLogRepository aiRequestLogRepository,
        AdminUserService adminUserService,
        AppConfigService appConfigService,
        JwtService jwtService,
        AuthContextResolver authContextResolver,
        AdminEventPublisherService adminEventPublisherService,
        @Value("${app.admin.username:admin}") String configuredAdminUsername,
        @Value("${app.admin.password:admin123}") String configuredAdminPassword,
        @Value("${app.admin.key:}") String configuredAdminKey
    ) {
        this.userRepository = userRepository;
        this.foodEntryRepository = foodEntryRepository;
        this.workoutEntryRepository = workoutEntryRepository;
        this.aiRequestLogRepository = aiRequestLogRepository;
        this.adminUserService = adminUserService;
        this.appConfigService = appConfigService;
        this.jwtService = jwtService;
        this.authContextResolver = authContextResolver;
        this.adminEventPublisherService = adminEventPublisherService;
        this.configuredAdminUsername = configuredAdminUsername;
        this.configuredAdminPassword = configuredAdminPassword;
        this.configuredAdminKey = configuredAdminKey;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(
        @RequestBody Map<String, String> body,
        HttpServletRequest request,
        HttpServletResponse response
    ) {
        String username = body != null ? body.get("username") : null;
        String password = body != null ? body.get("password") : null;

        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException("Informe usuário e senha.");
        }

        boolean validUser = username.trim().equalsIgnoreCase(configuredAdminUsername.trim());
        boolean hasAdminKey = configuredAdminKey != null && !configuredAdminKey.trim().isEmpty();
        boolean hasAdminPassword = configuredAdminPassword != null && !configuredAdminPassword.trim().isEmpty();
        boolean validPass = (hasAdminPassword && password.trim().equals(configuredAdminPassword.trim()))
            || (hasAdminKey && password.trim().equals(configuredAdminKey.trim()));

        if (!validUser || !validPass) {
            throw new InvalidCredentialsException("Usuário ou senha de administrador inválidos.");
        }

        String adminToken = jwtService.generateAdminToken(username.trim());
        ResponseCookie cookie = ResponseCookie.from(AuthContextResolver.ADMIN_COOKIE_NAME, adminToken)
            .httpOnly(true)
            .secure(isSecureRequest(request))
            .path("/")
            .maxAge(Duration.ofDays(7))
            .sameSite("Lax")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(Map.of(
            "status", "authenticated",
            "username", configuredAdminUsername,
            "token", adminToken,
            "message", "Login realizado com sucesso!"
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(AuthContextResolver.ADMIN_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(isSecureRequest(request))
            .path("/")
            .maxAge(0)
            .sameSite("Lax")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of("status", "logged_out", "message", "Sessão encerrada com sucesso"));
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (request == null) {
            return false;
        }
        if (request.isSecure()) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        return forwardedProto != null && forwardedProto.trim().toLowerCase().startsWith("https");
    }

    @GetMapping("/auth-check")
    public ResponseEntity<?> checkAuth(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        return ResponseEntity.ok(Map.of(
            "authenticated", true,
            "username", configuredAdminUsername
        ));
    }

    @GetMapping("/overview")
    public ResponseEntity<AdminDto.AdminOverviewResponse> getOverview(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);

        List<AdminDto.UserSummaryDto> recentUsers = adminUserService.listAllUsers();
        List<AdminDto.AiLogSummaryDto> recentLogs = aiRequestLogRepository.findTop50ByOrderByCreatedAtDesc()
            .stream()
            .map(AdminDto.AiLogSummaryDto::fromEntity)
            .toList();

        return ResponseEntity.ok(new AdminDto.AdminOverviewResponse(
            userRepository.count(),
            foodEntryRepository.count(),
            workoutEntryRepository.count(),
            aiRequestLogRepository.count(),
            appConfigService.getMaskedApiKey(),
            !appConfigService.getGeminiApiKey().isBlank(),
            appConfigService.getGeminiModel(),
            appConfigService.isAiBlocked(),
            recentUsers,
            recentLogs
        ));
    }

    @PostMapping("/killswitch")
    public ResponseEntity<?> toggleKillSwitch(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody(required = false) Map<String, Object> body
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);

        boolean newState = (body != null && body.containsKey("blocked"))
            ? Boolean.parseBoolean(String.valueOf(body.get("blocked")))
            : !appConfigService.isAiBlocked();

        appConfigService.setAiBlocked(newState);
        adminEventPublisherService.publishKillSwitchState(newState);

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "isBlocked", newState,
            "message", newState
                ? "⛔️ Requisições à API de IA foram BLOQUEADAS com sucesso!"
                : "🟢 Requisições à API de IA foram LIBERADAS!"
        ));
    }

    @PostMapping("/key")
    public ResponseEntity<?> updateKey(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody AdminDto.UpdateKeyRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);

        if (req.apiKey() != null && !req.apiKey().isBlank()) {
            appConfigService.setGeminiApiKey(req.apiKey().trim());
        }
        if (req.model() != null && !req.model().isBlank()) {
            appConfigService.setGeminiModel(req.model().trim());
        }

        adminEventPublisherService.publishConfigUpdated(
            appConfigService.getMaskedApiKey(),
            appConfigService.getGeminiModel()
        );

        return ResponseEntity.ok(Map.of(
            "status", "updated",
            "maskedKey", appConfigService.getMaskedApiKey(),
            "model", appConfigService.getGeminiModel(),
            "message", "Chave do Gemini e modelo atualizados com sucesso!"
        ));
    }

    @PostMapping("/test-key")
    public ResponseEntity<?> testKey(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody(required = false) Map<String, String> body
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);

        String keyToTest = (body != null && body.containsKey("apiKey") && !body.get("apiKey").isBlank())
            ? body.get("apiKey").trim()
            : appConfigService.getGeminiApiKey();

        if (keyToTest.isBlank()) {
            throw new IllegalArgumentException("Nenhuma chave informada ou salva.");
        }

        String model = (body != null && body.containsKey("model") && !body.get("model").isBlank())
            ? body.get("model").trim()
            : appConfigService.getGeminiModel();

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent?key=" + keyToTest;
            String pingBody = "{\"contents\":[{\"parts\":[{\"text\":\"ping\"}]}]}";

            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
            HttpRequest httpRequest = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(pingBody))
                .timeout(Duration.ofSeconds(20))
                .build();

            HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() == 200) {
                return ResponseEntity.ok(Map.of(
                    "status", "valid",
                    "message", "✅ Chave Gemini válida e conectada com sucesso no modelo " + model + "!"
                ));
            }
            return ResponseEntity.status(resp.statusCode()).body(Map.of(
                "status", "invalid",
                "message", "Erro da API do Gemini (HTTP " + resp.statusCode() + ")"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Erro de conexão: " + e.getMessage()
            ));
        }
    }

    @GetMapping("/models")
    public ResponseEntity<AdminDto.GeminiModelsResponse> listModels(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);

        String currentModel = appConfigService.getGeminiModel();
        String apiKey = appConfigService.getGeminiApiKey();
        List<AdminDto.GeminiModelItemDto> models = new ArrayList<>();

        if (!apiKey.isBlank()) {
            try {
                String url = "https://generativelanguage.googleapis.com/v1beta/models?key=" + apiKey;
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .GET()
                    .timeout(Duration.ofSeconds(6))
                    .build();

                HttpResponse<String> resp = client.send(httpRequest, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(resp.body());
                    JsonNode modelsArray = root.path("models");
                    if (modelsArray.isArray()) {
                        for (JsonNode mNode : modelsArray) {
                            String id = mNode.path("name").asText("").replaceFirst("^models/", "");
                            if (isDeprecatedOrUnsupportedModel(id)) continue;

                            boolean supportsGenerate = false;
                            JsonNode methods = mNode.path("supportedGenerationMethods");
                            if (methods.isArray()) {
                                for (JsonNode method : methods) {
                                    if ("generateContent".equalsIgnoreCase(method.asText())) {
                                        supportsGenerate = true;
                                        break;
                                    }
                                }
                            }
                            if (supportsGenerate) {
                                String displayName = mNode.path("displayName").asText(id);
                                String description = mNode.path("description").asText("");
                                models.add(new AdminDto.GeminiModelItemDto(
                                    id,
                                    displayName + " (" + id + ")",
                                    description,
                                    id.equalsIgnoreCase(currentModel)
                                ));
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }

        if (models.isEmpty()) {
            models = List.of(
                new AdminDto.GeminiModelItemDto(
                    "gemini-3.1-flash-lite",
                    "Gemini 3.1 Flash Lite (Padrão Recomendado - Menor Latência)",
                    "Modelo rápido e estável para nutrição multimodal, treinos e evolução",
                    "gemini-3.1-flash-lite".equalsIgnoreCase(currentModel)
                ),
                new AdminDto.GeminiModelItemDto(
                    "gemini-3.6-flash",
                    "Gemini 3.6 Flash (Equilíbrio de Velocidade & Capacidade)",
                    "Excelente precisão multimodal para fotos de pratos e fichas de treino",
                    "gemini-3.6-flash".equalsIgnoreCase(currentModel)
                ),
                new AdminDto.GeminiModelItemDto(
                    "gemini-3.8-flash",
                    "Gemini 3.8 Flash (Geração Flash Mais Recente)",
                    "Alta capacidade multimodal e raciocínio rápido",
                    "gemini-3.8-flash".equalsIgnoreCase(currentModel)
                ),
                new AdminDto.GeminiModelItemDto(
                    "gemini-3.8-pro",
                    "Gemini 3.8 Pro (Raciocínio Profundo)",
                    "Modelo avançado para diagnósticos fisiológicos complexos",
                    "gemini-3.8-pro".equalsIgnoreCase(currentModel)
                )
            );
        }

        return ResponseEntity.ok(new AdminDto.GeminiModelsResponse(currentModel, models));
    }

    @GetMapping("/users")
    public ResponseEntity<List<AdminDto.UserSummaryDto>> listUsers(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        return ResponseEntity.ok(adminUserService.listAllUsers());
    }

    @PostMapping("/users")
    public ResponseEntity<AdminDto.UserSummaryDto> createUser(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody AdminDto.CreateUserRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        return ResponseEntity.ok(adminUserService.createUser(req));
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminDto.UserDetailDto> getUserDetail(
        @PathVariable UUID userId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        return ResponseEntity.ok(adminUserService.getUserDetail(userId));
    }

    @PutMapping("/users/{userId}")
    public ResponseEntity<AdminDto.UserSummaryDto> updateUser(
        @PathVariable UUID userId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody AdminDto.UpdateUserRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        return ResponseEntity.ok(adminUserService.updateUser(userId, req));
    }

    @PostMapping("/users/{userId}/status")
    public ResponseEntity<AdminDto.UserSummaryDto> updateUserStatus(
        @PathVariable UUID userId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody(required = false) AdminDto.UserStatusRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        return ResponseEntity.ok(adminUserService.updateStatus(userId, req));
    }

    @PostMapping("/users/{userId}/ai-access")
    public ResponseEntity<AdminDto.UserSummaryDto> updateUserAiAccess(
        @PathVariable UUID userId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody AdminDto.UserAiAccessRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        return ResponseEntity.ok(adminUserService.updateAiAccess(userId, req));
    }

    @PostMapping("/users/{userId}/reset")
    public ResponseEntity<?> resetUserAccount(
        @PathVariable UUID userId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody(required = false) AdminDto.ResetUserRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        AdminDto.UserSummaryDto updated = adminUserService.resetUserAccount(userId, req);
        return ResponseEntity.ok(Map.of(
            "status", "reset_completed",
            "user", updated,
            "message", "Conta zerada com sucesso e sinalizada para limpeza no aplicativo!"
        ));
    }

    @DeleteMapping("/users/{userId}")
    public ResponseEntity<?> deleteUser(
        @PathVariable UUID userId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        adminUserService.deleteUserPermanently(userId);
        authContextResolver.markUserDeleted(userId);
        return ResponseEntity.ok(Map.of(
            "status", "deleted",
            "message", "Usuário e todos os dados vinculados foram excluídos permanentemente."
        ));
    }

    private boolean isDeprecatedOrUnsupportedModel(String id) {
        if (id == null || id.isBlank()) return true;
        String lower = id.toLowerCase();
        return lower.contains("embedding")
            || lower.contains("aqa")
            || lower.startsWith("gemini-1.")
            || lower.startsWith("gemini-2.")
            || lower.equals("gemini-flash-lite-latest")
            || lower.equals("gemini-3.5-flash-lite");
    }
}

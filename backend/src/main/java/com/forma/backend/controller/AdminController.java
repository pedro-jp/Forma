package com.forma.backend.controller;

import com.forma.backend.dto.AdminDto;
import com.forma.backend.dto.BillingDto;
import com.forma.backend.entity.AdminAuditLogEntity;
import com.forma.backend.entity.SecurityIncidentLogEntity;
import com.forma.backend.exception.DomainExceptions.InvalidCredentialsException;
import com.forma.backend.repository.*;
import com.forma.backend.security.AuthContextResolver;
import com.forma.backend.service.AdminUserService;
import com.forma.backend.service.AppConfigService;
import com.forma.backend.service.JwtService;
import com.forma.backend.service.MciAccessLogService;
import com.forma.backend.service.SubscriptionBillingService;
import com.forma.backend.websocket.AdminEventPublisherService;
import com.forma.backend.websocket.UserAccountWebSocketHandler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Supplier;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private static final ExecutorService ADMIN_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final Semaphore ADMIN_CONCURRENCY = new Semaphore(8);

    @Autowired(required = false)
    private SubscriptionBillingService subscriptionBillingService;

    private final UserRepository userRepository;
    private final FoodEntryRepository foodEntryRepository;
    private final WorkoutEntryRepository workoutEntryRepository;
    private final AiRequestLogRepository aiRequestLogRepository;
    private final AdminUserService adminUserService;
    private final AppConfigService appConfigService;
    private final JwtService jwtService;
    private final AuthContextResolver authContextResolver;
    private final AdminEventPublisherService adminEventPublisherService;
    private final MciAccessLogService mciAccessLogService;
    private final AdminAuditLogRepository adminAuditLogRepository;
    private final SecurityIncidentLogRepository securityIncidentLogRepository;
    private final UserAccountWebSocketHandler userAccountWebSocketHandler;
    private final String configuredAdminUsername;
    private final String configuredAdminPassword;
    private final String configuredAdminKey;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Deque<Long>> failedAdminLoginsByIp = new ConcurrentHashMap<>();

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
        MciAccessLogService mciAccessLogService,
        AdminAuditLogRepository adminAuditLogRepository,
        SecurityIncidentLogRepository securityIncidentLogRepository,
        UserAccountWebSocketHandler userAccountWebSocketHandler,
        @Value("${app.admin.username:admin}") String configuredAdminUsername,
        @Value("${app.admin.password:}") String configuredAdminPassword,
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
        this.mciAccessLogService = mciAccessLogService;
        this.adminAuditLogRepository = adminAuditLogRepository;
        this.securityIncidentLogRepository = securityIncidentLogRepository;
        this.userAccountWebSocketHandler = userAccountWebSocketHandler;
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
        String clientIp = extractClientIp(request);
        if (isAdminIpLocked(clientIp)) {
            throw new InvalidCredentialsException("Muitas tentativas inválidas consecutivas. Acesso bloqueado temporariamente por segurança (10 min).");
        }

        String username = body != null ? body.get("username") : null;
        String password = body != null ? body.get("password") : null;

        if (username == null || password == null || username.isBlank() || password.isBlank()) {
            throw new IllegalArgumentException("Informe usuário e senha.");
        }

        boolean validUser = authContextResolver.constantTimeSecretMatch(
            configuredAdminUsername.trim().toLowerCase(),
            username.trim().toLowerCase()
        );
        boolean hasAdminKey = configuredAdminKey != null && !configuredAdminKey.trim().isEmpty();
        boolean hasAdminPassword = configuredAdminPassword != null && !configuredAdminPassword.trim().isEmpty();
        boolean validPass = (hasAdminPassword && authContextResolver.constantTimeSecretMatch(configuredAdminPassword.trim(), password.trim()))
            || (hasAdminKey && authContextResolver.constantTimeSecretMatch(configuredAdminKey.trim(), password.trim()));

        if (!validUser || !validPass) {
            recordFailedAdminAttempt(clientIp, username.trim(), request);
            mciAccessLogService.recordAdminAudit(username.trim(), "ADMIN_LOGIN_FAILED", (String) null, "Tentativa de login admin recusada", request);
            throw new InvalidCredentialsException("Usuário ou senha de administrador inválidos.");
        }

        failedAdminLoginsByIp.remove(clientIp);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "ADMIN_LOGIN_SUCCESS", (String) null, "Sessão administrativa autenticada", request);
        String adminToken = jwtService.generateAdminToken(username.trim());
        ResponseCookie cookie = ResponseCookie.from(AuthContextResolver.ADMIN_COOKIE_NAME, adminToken)
            .httpOnly(true)
            .secure(isSecureRequest(request))
            .path("/")
            .maxAge(Duration.ofDays(7))
            .sameSite("Strict")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());

        return ResponseEntity.ok(Map.of(
            "status", "authenticated",
            "username", configuredAdminUsername,
            "token", adminToken,
            "message", "Sessão autenticada."
        ));
    }

    @PostMapping("/logout")
    public ResponseEntity<?> logout(HttpServletRequest request, HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(AuthContextResolver.ADMIN_COOKIE_NAME, "")
            .httpOnly(true)
            .secure(isSecureRequest(request))
            .path("/")
            .maxAge(0)
            .sameSite("Strict")
            .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        return ResponseEntity.ok(Map.of("status", "logged_out", "message", "Sessão encerrada com sucesso"));
    }

    private boolean isAdminIpLocked(String clientIp) {
        Deque<Long> attempts = failedAdminLoginsByIp.get(clientIp);
        if (attempts == null) return false;
        long cutoff = System.currentTimeMillis() - 600_000L;
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst() < cutoff) {
                attempts.pollFirst();
            }
            return attempts.size() >= 5;
        }
    }

    private void recordFailedAdminAttempt(String clientIp, String attemptedUser, HttpServletRequest request) {
        long now = System.currentTimeMillis();
        long cutoff = now - 600_000L;
        Deque<Long> attempts = failedAdminLoginsByIp.computeIfAbsent(clientIp, k -> new ArrayDeque<>());
        boolean triggeredLockout = false;
        synchronized (attempts) {
            while (!attempts.isEmpty() && attempts.peekFirst() < cutoff) {
                attempts.pollFirst();
            }
            attempts.addLast(now);
            triggeredLockout = attempts.size() == 5;
        }
        if (triggeredLockout) {
            try {
                SecurityIncidentLogEntity inc = new SecurityIncidentLogEntity();
                inc.setSeverity("MEDIUM");
                inc.setIncidentType("ADMIN_BRUTE_FORCE_BLOCKED");
                inc.setAffectedCategories("Credenciais Administrativas");
                inc.setAffectedCount(0);
                inc.setDescription("Bloqueio automático após 5 tentativas inválidas de login admin (usuário informado: " + attemptedUser + ").");
                inc.setMitigationActions("IP bloqueado por 10 minutos; trilha registrada em admin_audit_logs.");
                inc.setDetectedAtUtc(Instant.now());
                inc.setRetainUntilUtc(Instant.now().plus(1825, ChronoUnit.DAYS));
                SecurityIncidentLogEntity savedInc = securityIncidentLogRepository.save(inc);
                adminEventPublisherService.publishSecurityIncident(savedInc);
            } catch (Exception ignored) {
            }
        }
    }

    private String extractClientIp(HttpServletRequest request) {
        if (request == null) return "unknown";
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            return xff.split(",")[0].trim();
        }
        return request.getRemoteAddr() != null ? request.getRemoteAddr() : "unknown";
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
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
            .body(buildOverviewResponse());
    }

    @GetMapping("/bootstrap")
    public ResponseEntity<AdminDto.AdminBootstrapResponse> getBootstrap(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);

        CompletableFuture<AdminDto.AdminOverviewResponse> overviewFut = supplyParallelOrInline(this::buildOverviewResponse);
        CompletableFuture<BillingDto.BillingDashboardResponse> billingFut = supplyParallelOrInline(
            () -> subscriptionBillingService != null ? subscriptionBillingService.getAdminDashboard(null) : null
        );
        CompletableFuture<List<AdminAuditLogEntity>> auditsFut = supplyParallelOrInline(
            () -> adminAuditLogRepository.findTop100ByOrderByCreatedAtUtcDesc().stream().limit(25).toList()
        );
        CompletableFuture<List<SecurityIncidentLogEntity>> incidentsFut = supplyParallelOrInline(
            () -> securityIncidentLogRepository.findTop50ByOrderByDetectedAtUtcDesc().stream().limit(15).toList()
        );

        CompletableFuture.allOf(overviewFut, billingFut, auditsFut, incidentsFut).join();

        AdminDto.AdminBootstrapResponse response = new AdminDto.AdminBootstrapResponse(
            configuredAdminUsername,
            overviewFut.join(),
            billingFut.join(),
            auditsFut.join(),
            incidentsFut.join(),
            Instant.now()
        );
        return ResponseEntity.ok()
            .header(HttpHeaders.CACHE_CONTROL, "no-store, no-cache, must-revalidate, max-age=0")
            .body(response);
    }

    private AdminDto.AdminOverviewResponse buildOverviewResponse() {
        CompletableFuture<List<AdminDto.UserSummaryDto>> usersFut = supplyParallelOrInline(adminUserService::listAllUsers);
        CompletableFuture<List<AdminDto.AiLogSummaryDto>> logsFut = supplyParallelOrInline(
            () -> aiRequestLogRepository.findTop50ByOrderByCreatedAtDesc()
                .stream()
                .map(AdminDto.AiLogSummaryDto::fromEntity)
                .toList()
        );
        CompletableFuture<Long> totalAiFut = supplyParallelOrInline(aiRequestLogRepository::count);

        CompletableFuture.allOf(usersFut, logsFut, totalAiFut).join();

        List<AdminDto.UserSummaryDto> recentUsers = usersFut.join();
        long totalUsers = recentUsers.size();
        long totalFoods = recentUsers.stream().mapToLong(AdminDto.UserSummaryDto::foodsCount).sum();
        long totalWorkouts = recentUsers.stream().mapToLong(AdminDto.UserSummaryDto::workoutsCount).sum();

        return new AdminDto.AdminOverviewResponse(
            totalUsers,
            totalFoods,
            totalWorkouts,
            totalAiFut.join(),
            appConfigService.getMaskedApiKey(),
            !appConfigService.getGeminiApiKey().isBlank(),
            appConfigService.getGeminiModel(),
            appConfigService.isAiBlocked(),
            recentUsers,
            logsFut.join()
        );
    }

    private static <T> CompletableFuture<T> supplyParallelOrInline(Supplier<T> supplier) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompletableFuture.completedFuture(supplier.get());
        }
        return CompletableFuture.supplyAsync(() -> {
            ADMIN_CONCURRENCY.acquireUninterruptibly();
            try {
                return supplier.get();
            } finally {
                ADMIN_CONCURRENCY.release();
            }
        }, ADMIN_EXECUTOR);
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
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "ADMIN_KILLSWITCH", (String) null, "KillSwitch AI=" + newState, request);

        return ResponseEntity.ok(Map.of(
            "status", "success",
            "isBlocked", newState,
            "message", newState
                ? "Requisições à API de IA bloqueadas."
                : "Requisições à API de IA liberadas."
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
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "ADMIN_KEY_UPDATED", (String) null, "Modelo/chave Gemini atualizado (" + appConfigService.getGeminiModel() + ")", request);

        return ResponseEntity.ok(Map.of(
            "status", "updated",
            "maskedKey", appConfigService.getMaskedApiKey(),
            "model", appConfigService.getGeminiModel(),
            "message", "Credencial e modelo Gemini atualizados."
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
            String pingBody = "{\"contents\":[{\"role\":\"user\",\"parts\":[{\"text\":\"ping\"}]}],\"generationConfig\":{\"maxOutputTokens\":8,\"temperature\":0.0}}";
            HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();

            boolean isVertexExpressKey = keyToTest.startsWith("AQ.");
            List<String> urlsToTry = new ArrayList<>();
            if (isVertexExpressKey) {
                urlsToTry.add("https://aiplatform.googleapis.com/v1/publishers/google/models/" + model + ":generateContent?key=" + URLEncoder.encode(keyToTest, StandardCharsets.UTF_8));
            }
            urlsToTry.add("https://generativelanguage.googleapis.com/v1beta/models/" + model + ":generateContent");

            HttpResponse<String> lastResp = null;
            for (String url : urlsToTry) {
                HttpRequest.Builder reqBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(pingBody))
                    .timeout(Duration.ofSeconds(15));
                if (!url.contains("aiplatform.googleapis.com")) {
                    reqBuilder.header("x-goog-api-key", keyToTest);
                }
                HttpResponse<String> resp = client.send(reqBuilder.build(), HttpResponse.BodyHandlers.ofString());
                lastResp = resp;
                if (resp.statusCode() == 200) {
                    return ResponseEntity.ok(Map.of(
                        "status", "valid",
                        "message", "Conexão validada com sucesso no modelo " + model + "."
                    ));
                }
                if (resp.statusCode() != 400 && resp.statusCode() != 401 && resp.statusCode() != 403 && resp.statusCode() != 404) {
                    break;
                }
            }
            int code = lastResp != null ? lastResp.statusCode() : 500;
            return ResponseEntity.status(code).body(Map.of(
                "status", "invalid",
                "message", "Erro da API do Gemini (HTTP " + code + ")"
            ));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(Map.of(
                "status", "error",
                "message", "Erro de conexão: " + (e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName())
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
                String url = "https://generativelanguage.googleapis.com/v1beta/models";
                HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(6)).build();
                HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("x-goog-api-key", apiKey)
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
                    "Modelo avançado para análises educacionais complexas de bem-estar",
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
        AdminDto.UserSummaryDto created = adminUserService.createUser(req);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "ADMIN_CREATE_USER", created.id(), "Criação manual de usuário", request);
        return ResponseEntity.ok(created);
    }

    @GetMapping("/users/{userId}")
    public ResponseEntity<AdminDto.UserDetailDto> getUserDetail(
        @PathVariable UUID userId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "ADMIN_VIEW_USER", userId, "Acesso a detalhe do titular (LGPD Art. 37)", request);
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
        AdminDto.UserSummaryDto updated = adminUserService.updateUser(userId, req);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "ADMIN_UPDATE_USER", userId, "Atualização cadastral pelo admin", request);
        return ResponseEntity.ok(updated);
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
        AdminDto.UserSummaryDto updated = adminUserService.updateStatus(userId, req);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "ADMIN_USER_STATUS", userId, "Status alterado para " + updated.status(), request);
        return ResponseEntity.ok(updated);
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
        AdminDto.UserSummaryDto updated = adminUserService.updateAiAccess(userId, req);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "ADMIN_USER_AI_ACCESS", userId, "aiBlocked=" + updated.aiBlocked(), request);
        return ResponseEntity.ok(updated);
    }

    @PostMapping("/users/{userId}/tier")
    public ResponseEntity<AdminDto.UserSummaryDto> updateUserTier(
        @PathVariable UUID userId,
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken,
        @RequestBody(required = false) AdminDto.UserTierRequest req
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        AdminDto.UserSummaryDto updated = adminUserService.updateAccountTier(userId, req);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "ADMIN_USER_TIER", userId, "Plano alterado para " + updated.accountTier(), request);
        return ResponseEntity.ok(updated);
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
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "ADMIN_RESET_USER", userId, "Reset de dados do titular", request);
        return ResponseEntity.ok(Map.of(
            "status", "reset_completed",
            "user", updated,
            "message", "Dados da conta reiniciados com sucesso."
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
        userAccountWebSocketHandler.disconnectUser(userId);
        mciAccessLogService.recordAdminAudit(configuredAdminUsername, "ADMIN_DELETE_USER", userId, "Exclusão permanente LGPD Art. 18", request);
        return ResponseEntity.ok(Map.of(
            "status", "deleted",
            "message", "Usuário e todos os dados vinculados foram excluídos permanentemente."
        ));
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<?> listAdminAuditLogs(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        return ResponseEntity.ok(adminAuditLogRepository.findTop100ByOrderByCreatedAtUtcDesc());
    }

    @GetMapping("/incidents")
    public ResponseEntity<?> listSecurityIncidents(
        HttpServletRequest request,
        @RequestHeader(value = "X-Admin-Key", required = false) String adminKey,
        @CookieValue(value = AuthContextResolver.ADMIN_COOKIE_NAME, required = false) String cookieToken
    ) {
        authContextResolver.requireAdmin(request, adminKey, cookieToken);
        return ResponseEntity.ok(securityIncidentLogRepository.findTop50ByOrderByDetectedAtUtcDesc());
    }

    private boolean isDeprecatedOrUnsupportedModel(String id) {
        if (id == null || id.isBlank()) return true;
        String lower = id.toLowerCase();
        return lower.contains("embedding")
            || lower.contains("aqa")
            || lower.contains("hydrogen")
            || lower.contains("robotics")
            || lower.startsWith("gemini-1.")
            || lower.startsWith("gemini-2.")
            || lower.equals("gemini-flash-lite-latest")
            || lower.equals("gemini-3.5-flash-lite");
    }
}

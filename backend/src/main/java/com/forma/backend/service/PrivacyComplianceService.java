package com.forma.backend.service;

import com.forma.backend.dto.PrivacyDto;
import com.forma.backend.dto.SyncDto;
import com.forma.backend.entity.AiRequestLogEntity;
import com.forma.backend.entity.LegalConsentLedgerEntity;
import com.forma.backend.entity.MciAccessLogEntity;
import com.forma.backend.entity.User;
import com.forma.backend.entity.UserProfile;
import com.forma.backend.repository.*;
import com.forma.backend.security.FieldEncryptionService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

@Service
public class PrivacyComplianceService {

    public static final String CURRENT_POLICY_VERSION = "2026.11-LGPD";

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final FoodEntryRepository foodEntryRepository;
    private final WorkoutEntryRepository workoutEntryRepository;
    private final WorkoutRoutineRepository workoutRoutineRepository;
    private final WaterEntryRepository waterEntryRepository;
    private final WeightEntryRepository weightEntryRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final AiRequestLogRepository aiRequestLogRepository;
    private final UserActivityLogRepository userActivityLogRepository;
    private final MciAccessLogRepository mciAccessLogRepository;
    private final LegalConsentLedgerRepository legalConsentLedgerRepository;
    private final FieldEncryptionService fieldEncryptionService;
    private final MciAccessLogService mciAccessLogService;
    private final ExecutorService exportExecutor = Executors.newFixedThreadPool(6);

    private volatile String currentDocumentsSha256 = "";

    public PrivacyComplianceService(
        UserRepository userRepository,
        UserProfileRepository userProfileRepository,
        FoodEntryRepository foodEntryRepository,
        WorkoutEntryRepository workoutEntryRepository,
        WorkoutRoutineRepository workoutRoutineRepository,
        WaterEntryRepository waterEntryRepository,
        WeightEntryRepository weightEntryRepository,
        AiAnalysisRepository aiAnalysisRepository,
        AiRequestLogRepository aiRequestLogRepository,
        UserActivityLogRepository userActivityLogRepository,
        MciAccessLogRepository mciAccessLogRepository,
        LegalConsentLedgerRepository legalConsentLedgerRepository,
        FieldEncryptionService fieldEncryptionService,
        MciAccessLogService mciAccessLogService
    ) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.foodEntryRepository = foodEntryRepository;
        this.workoutEntryRepository = workoutEntryRepository;
        this.workoutRoutineRepository = workoutRoutineRepository;
        this.waterEntryRepository = waterEntryRepository;
        this.weightEntryRepository = weightEntryRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.aiRequestLogRepository = aiRequestLogRepository;
        this.userActivityLogRepository = userActivityLogRepository;
        this.mciAccessLogRepository = mciAccessLogRepository;
        this.legalConsentLedgerRepository = legalConsentLedgerRepository;
        this.fieldEncryptionService = fieldEncryptionService;
        this.mciAccessLogService = mciAccessLogService;
    }

    @PostConstruct
    void computeDocumentsDigest() {
        String privacyText = readStaticResourceOrFallback("static/privacy.html", "privacy-v2026.11");
        String termsText = readStaticResourceOrFallback("static/terms.html", "terms-v2026.11");
        this.currentDocumentsSha256 = FieldEncryptionService.sha256Hex(privacyText + "\n---\n" + termsText);
    }

    @PreDestroy
    void shutdownExportExecutor() {
        exportExecutor.shutdownNow();
    }

    public String getCurrentPolicySha256() {
        if (currentDocumentsSha256 == null || currentDocumentsSha256.isBlank()) {
            computeDocumentsDigest();
        }
        return currentDocumentsSha256;
    }

    @Transactional(readOnly = true)
    public PrivacyDto.ConsentStatusResponse getConsentStatus(User user) {
        List<PrivacyDto.ConsentLedgerItemDto> history = legalConsentLedgerRepository
            .findByUserIdOrderByRecordedAtUtcDesc(user.getId().toString())
            .stream()
            .map(item -> new PrivacyDto.ConsentLedgerItemDto(
                item.getId(),
                item.getConsentType(),
                item.isGranted(),
                item.getDocumentVersion(),
                item.getDocumentSha256(),
                item.getSourcePlatform(),
                item.getRecordedAtUtc()
            ))
            .toList();

        return new PrivacyDto.ConsentStatusResponse(
            user.getId(),
            user.isAgeConfirmed18Plus(),
            user.getTermsAcceptedVersion(),
            user.getTermsAcceptedAt(),
            user.getHealthConsentAcceptedAt() != null,
            user.getHealthConsentAcceptedAt(),
            user.isAiConsentGranted(),
            user.getAiConsentUpdatedAt(),
            CURRENT_POLICY_VERSION,
            getCurrentPolicySha256(),
            history
        );
    }

    @Transactional
    public PrivacyDto.ConsentStatusResponse updateConsents(
        User user,
        PrivacyDto.ConsentUpdateRequest request,
        HttpServletRequest httpRequest
    ) {
        if (request == null) {
            return getConsentStatus(user);
        }

        String version = (request.documentVersion() != null && !request.documentVersion().isBlank())
            ? request.documentVersion().trim()
            : CURRENT_POLICY_VERSION;
        String platform = (request.sourcePlatform() != null && !request.sourcePlatform().isBlank())
            ? request.sourcePlatform().trim()
            : "ios";
        String sha256 = getCurrentPolicySha256();
        String ipHash = fieldEncryptionService.hmacSha256Hex(mciAccessLogService.extractClientIp(httpRequest));
        Instant now = Instant.now();

        if (request.ageConfirmed18Plus() != null) {
            user.setAgeConfirmed18Plus(request.ageConfirmed18Plus());
            saveLedgerEntry(user.getId(), "AGE_18_CONFIRMATION", request.ageConfirmed18Plus(), version, sha256, platform, ipHash);
        }
        if (request.termsAccepted() != null) {
            if (Boolean.TRUE.equals(request.termsAccepted())) {
                user.setTermsAcceptedVersion(version);
                user.setTermsAcceptedAt(now);
            }
            saveLedgerEntry(user.getId(), "TERMS_AND_PRIVACY", request.termsAccepted(), version, sha256, platform, ipHash);
        }
        if (request.sensitiveHealthConsentGranted() != null) {
            if (Boolean.TRUE.equals(request.sensitiveHealthConsentGranted())) {
                user.setHealthConsentAcceptedAt(now);
            } else {
                user.setHealthConsentAcceptedAt(null);
            }
            saveLedgerEntry(user.getId(), "SENSITIVE_HEALTH_ART11", request.sensitiveHealthConsentGranted(), version, sha256, platform, ipHash);
        }
        if (request.aiConsentGranted() != null) {
            user.setAiConsentGranted(request.aiConsentGranted());
            user.setAiConsentUpdatedAt(now);
            saveLedgerEntry(user.getId(), "AI_GEMINI_SHARING", request.aiConsentGranted(), version, sha256, platform, ipHash);
        }

        User saved = userRepository.save(user);
        return getConsentStatus(saved);
    }

    @Transactional
    public void recordRegistrationConsentsIfProvided(
        User user,
        Boolean age18,
        Boolean terms,
        Boolean sensitiveHealth,
        Boolean aiSharing,
        String version,
        HttpServletRequest httpRequest
    ) {
        boolean anyExplicit = age18 != null || terms != null || sensitiveHealth != null || aiSharing != null;
        if (!anyExplicit) {
            return;
        }
        updateConsents(
            user,
            new PrivacyDto.ConsentUpdateRequest(
                age18,
                terms != null ? terms : Boolean.TRUE,
                sensitiveHealth,
                aiSharing,
                version,
                "ios-register"
            ),
            httpRequest
        );
    }

    private <T> CompletableFuture<T> supplyParallelOrInline(Supplier<T> supplier) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompletableFuture.completedFuture(supplier.get());
        }
        return CompletableFuture.supplyAsync(supplier, exportExecutor);
    }

    public Map<String, Object> exportFullUserData(User user) {
        UUID userId = user.getId();
        String accountRefHash = fieldEncryptionService.hmacSha256Hex(userId.toString().toLowerCase());

        CompletableFuture<UserProfile> profileFut = supplyParallelOrInline(
            () -> userProfileRepository.findById(userId).orElse(null));
        CompletableFuture<PrivacyDto.ConsentStatusResponse> consentsFut = supplyParallelOrInline(
            () -> getConsentStatus(user));
        CompletableFuture<List<Map<String, Object>>> aiUsageFut = supplyParallelOrInline(
            () -> aiRequestLogRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(this::toExportAiLogMap).toList()
        );
        CompletableFuture<List<SyncDto.ActivityLogDto>> activityFut = supplyParallelOrInline(
            () -> userActivityLogRepository.findByUserIdOrderByActivityTimestampDesc(userId).stream()
                .map(SyncDto.ActivityLogDto::fromEntity).toList()
        );
        CompletableFuture<List<Map<String, Object>>> mciFut = supplyParallelOrInline(
            () -> mciAccessLogRepository.findByAccountRefHashOrderByAccessedAtUtcDesc(accountRefHash).stream()
                .map(this::toExportMciLogMap).toList()
        );
        CompletableFuture<List<SyncDto.FoodDto>> foodsFut = supplyParallelOrInline(
            () -> foodEntryRepository.findByUserIdOrderByConsumedAtDesc(userId).stream()
                .map(SyncDto.FoodDto::fromEntity).toList()
        );
        CompletableFuture<List<SyncDto.WorkoutDto>> workoutsFut = supplyParallelOrInline(
            () -> workoutEntryRepository.findByUserIdOrderByWorkoutDateDesc(userId).stream()
                .map(SyncDto.WorkoutDto::fromEntity).toList()
        );
        CompletableFuture<List<SyncDto.RoutineDto>> routinesFut = supplyParallelOrInline(
            () -> workoutRoutineRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(SyncDto.RoutineDto::fromEntity).toList()
        );
        CompletableFuture<List<SyncDto.WaterDto>> watersFut = supplyParallelOrInline(
            () -> waterEntryRepository.findByUserIdOrderByEntryDateDesc(userId).stream()
                .map(SyncDto.WaterDto::fromEntity).toList()
        );
        CompletableFuture<List<SyncDto.WeightDto>> weightsFut = supplyParallelOrInline(
            () -> weightEntryRepository.findByUserIdOrderByEntryDateDesc(userId).stream()
                .map(SyncDto.WeightDto::fromEntity).toList()
        );
        CompletableFuture<List<SyncDto.AiAnalysisDto>> aiAnalysesFut = supplyParallelOrInline(
            () -> aiAnalysisRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(SyncDto.AiAnalysisDto::fromEntity).toList()
        );

        CompletableFuture.allOf(
            profileFut, consentsFut, aiUsageFut, activityFut, mciFut,
            foodsFut, workoutsFut, routinesFut, watersFut, weightsFut, aiAnalysesFut
        ).join();

        UserProfile profile = profileFut.join();

        Map<String, Object> controllerMetadata = new LinkedHashMap<>();
        controllerMetadata.put("application", "Forma — Nutrição & Treinos Inteligentes");
        controllerMetadata.put("legalBasisOverview", "LGPD (Lei 13.709/2018) Arts. 7º, 11 e 18; Marco Civil da Internet Art. 15");
        controllerMetadata.put("dpoContactEmail", "privacidade@forma.app");
        controllerMetadata.put("exportedAtUtc", Instant.now().toString());
        controllerMetadata.put("policyVersion", CURRENT_POLICY_VERSION);
        controllerMetadata.put("subProcessors", List.of(
            "Supabase Inc. (PostgreSQL com RLS e criptografia AES-256-GCM em repouso)",
            "Render Services Inc. (Infraestrutura de API TLS 1.3)",
            "Google LLC — Gemini API Paga (Inferência transitória opt-in sem nome/EXIF e sem treinamento de modelos)",
            "Resend Inc. (E-mails transacionais de recuperação de senha)",
            "Apple Inc. (Sign in with Apple / HealthKit local no dispositivo)"
        ));

        Map<String, Object> accountData = new LinkedHashMap<>();
        accountData.put("id", user.getId());
        accountData.put("email", user.getEmail());
        accountData.put("name", user.getName());
        accountData.put("authProvider", user.getAuthProvider());
        accountData.put("accountTier", user.getAccountTier());
        accountData.put("status", user.getStatus());
        accountData.put("createdAt", user.getCreatedAt());
        accountData.put("lastLoginAt", user.getLastLoginAt());
        accountData.put("lastSyncedAt", user.getLastSyncedAt());

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("exportSchema", "forma-lgpd-art18-portability-v1");
        payload.put("metadata", controllerMetadata);
        payload.put("account", accountData);
        payload.put("consents", consentsFut.join());
        payload.put("profile", profile != null ? SyncDto.ProfileDto.fromEntity(profile) : null);
        payload.put("foods", foodsFut.join());
        payload.put("workouts", workoutsFut.join());
        payload.put("routines", routinesFut.join());
        payload.put("waters", watersFut.join());
        payload.put("weights", weightsFut.join());
        payload.put("aiAnalyses", aiAnalysesFut.join());
        payload.put("activityLogs", activityFut.join());
        payload.put("mciAccessLogs", mciFut.join());
        payload.put("recentAiRequestAudit", aiUsageFut.join());
        return payload;
    }

    private Map<String, Object> toExportAiLogMap(AiRequestLogEntity log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", log.getId());
        m.put("endpoint", log.getEndpoint());
        m.put("status", log.getStatus());
        m.put("durationMs", log.getDurationMs());
        m.put("createdAt", log.getCreatedAt());
        return m;
    }

    private Map<String, Object> toExportMciLogMap(MciAccessLogEntity log) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", log.getId());
        m.put("eventType", log.getEventType());
        m.put("accessedAtUtc", log.getAccessedAtUtc());
        m.put("retainUntilUtc", log.getRetainUntilUtc());
        return m;
    }

    private void saveLedgerEntry(
        UUID userId,
        String consentType,
        boolean granted,
        String version,
        String sha256,
        String platform,
        String ipHash
    ) {
        legalConsentLedgerRepository.save(new LegalConsentLedgerEntity(
            userId.toString(),
            consentType,
            granted,
            version,
            sha256,
            platform,
            ipHash
        ));
    }

    private String readStaticResourceOrFallback(String path, String fallback) {
        try {
            ClassPathResource res = new ClassPathResource(path);
            if (res.exists()) {
                return new String(res.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
        }
        return fallback;
    }
}

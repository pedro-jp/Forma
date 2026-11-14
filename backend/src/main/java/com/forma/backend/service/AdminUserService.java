package com.forma.backend.service;

import com.forma.backend.dto.AdminDto;
import com.forma.backend.dto.SyncDto;
import com.forma.backend.entity.DeletedAccountTombstoneEntity;
import com.forma.backend.entity.User;
import com.forma.backend.entity.UserProfile;
import com.forma.backend.entity.UserSubscriptionEntity;
import com.forma.backend.exception.DomainExceptions.ResourceNotFoundException;
import com.forma.backend.repository.*;
import com.forma.backend.security.FieldEncryptionService;
import com.forma.backend.websocket.AdminEventPublisherService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Service
public class AdminUserService {

    private static final ExecutorService QUERY_EXECUTOR = Executors.newVirtualThreadPerTaskExecutor();
    private static final Semaphore QUERY_CONCURRENCY = new Semaphore(10);

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final FoodEntryRepository foodEntryRepository;
    private final WorkoutEntryRepository workoutEntryRepository;
    private final WaterEntryRepository waterEntryRepository;
    private final WeightEntryRepository weightEntryRepository;
    private final WorkoutRoutineRepository workoutRoutineRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final UserActivityLogRepository userActivityLogRepository;
    private final AiRequestLogRepository aiRequestLogRepository;
    private final AuthService authService;
    private final SocialAuthVerificationService socialAuthVerificationService;
    private final AdminEventPublisherService adminEventPublisherService;
    private final LegalConsentLedgerRepository legalConsentLedgerRepository;
    private final DeletedAccountTombstoneRepository deletedAccountTombstoneRepository;
    private final FieldEncryptionService fieldEncryptionService;

    @Autowired(required = false)
    private UserSubscriptionRepository userSubscriptionRepository;

    @Autowired(required = false)
    private DunningNoticeRepository dunningNoticeRepository;

    @Autowired(required = false)
    private CouponRedemptionRepository couponRedemptionRepository;

    @Autowired
    public AdminUserService(
        UserRepository userRepository,
        UserProfileRepository userProfileRepository,
        FoodEntryRepository foodEntryRepository,
        WorkoutEntryRepository workoutEntryRepository,
        WaterEntryRepository waterEntryRepository,
        WeightEntryRepository weightEntryRepository,
        WorkoutRoutineRepository workoutRoutineRepository,
        AiAnalysisRepository aiAnalysisRepository,
        UserActivityLogRepository userActivityLogRepository,
        AiRequestLogRepository aiRequestLogRepository,
        AuthService authService,
        SocialAuthVerificationService socialAuthVerificationService,
        AdminEventPublisherService adminEventPublisherService,
        LegalConsentLedgerRepository legalConsentLedgerRepository,
        DeletedAccountTombstoneRepository deletedAccountTombstoneRepository,
        FieldEncryptionService fieldEncryptionService
    ) {
        this.userRepository = userRepository;
        this.userProfileRepository = userProfileRepository;
        this.foodEntryRepository = foodEntryRepository;
        this.workoutEntryRepository = workoutEntryRepository;
        this.waterEntryRepository = waterEntryRepository;
        this.weightEntryRepository = weightEntryRepository;
        this.workoutRoutineRepository = workoutRoutineRepository;
        this.aiAnalysisRepository = aiAnalysisRepository;
        this.userActivityLogRepository = userActivityLogRepository;
        this.aiRequestLogRepository = aiRequestLogRepository;
        this.authService = authService;
        this.socialAuthVerificationService = socialAuthVerificationService;
        this.adminEventPublisherService = adminEventPublisherService;
        this.legalConsentLedgerRepository = legalConsentLedgerRepository;
        this.deletedAccountTombstoneRepository = deletedAccountTombstoneRepository;
        this.fieldEncryptionService = fieldEncryptionService;
    }

    public List<AdminDto.UserSummaryDto> listAllUsers() {
        Instant startOfDay = currentStartOfDay();

        CompletableFuture<List<User>> usersFut = supplyParallelOrInline(userRepository::findAll);
        CompletableFuture<Map<UUID, UserProfile>> profilesFut = supplyParallelOrInline(() ->
            userProfileRepository.findAll().stream()
                .collect(Collectors.toMap(UserProfile::getUserId, Function.identity(), (a, b) -> a))
        );
        CompletableFuture<Map<UUID, Long>> todayAiFut = supplyParallelOrInline(
            () -> toCountMap(aiRequestLogRepository.countSinceGroupedByUserId(startOfDay))
        );
        CompletableFuture<Map<UUID, Long>> totalAiFut = supplyParallelOrInline(
            () -> toCountMap(aiRequestLogRepository.countTotalGroupedByUserId())
        );
        CompletableFuture<Map<UUID, Long>> foodsFut = supplyParallelOrInline(
            () -> toCountMap(foodEntryRepository.countGroupedByUserId())
        );
        CompletableFuture<Map<UUID, Long>> workoutsFut = supplyParallelOrInline(
            () -> toCountMap(workoutEntryRepository.countGroupedByUserId())
        );
        CompletableFuture<Map<UUID, Long>> routinesFut = supplyParallelOrInline(
            () -> toCountMap(workoutRoutineRepository.countGroupedByUserId())
        );
        CompletableFuture<Map<UUID, Long>> watersFut = supplyParallelOrInline(
            () -> toCountMap(waterEntryRepository.countGroupedByUserId())
        );
        CompletableFuture<Map<UUID, Long>> weightsFut = supplyParallelOrInline(
            () -> toCountMap(weightEntryRepository.countGroupedByUserId())
        );
        CompletableFuture<Map<UUID, Long>> analysesFut = supplyParallelOrInline(
            () -> toCountMap(aiAnalysisRepository.countGroupedByUserId())
        );

        CompletableFuture.allOf(
            usersFut, profilesFut, todayAiFut, totalAiFut, foodsFut,
            workoutsFut, routinesFut, watersFut, weightsFut, analysesFut
        ).join();

        List<User> users = usersFut.join();
        Map<UUID, UserProfile> profiles = profilesFut.join();
        Map<UUID, Long> todayAi = todayAiFut.join();
        Map<UUID, Long> totalAi = totalAiFut.join();
        Map<UUID, Long> foods = foodsFut.join();
        Map<UUID, Long> workouts = workoutsFut.join();
        Map<UUID, Long> routines = routinesFut.join();
        Map<UUID, Long> waters = watersFut.join();
        Map<UUID, Long> weights = weightsFut.join();
        Map<UUID, Long> analyses = analysesFut.join();

        return users.stream()
            .sorted(Comparator.comparing(
                (User u) -> u.getCreatedAt() != null ? u.getCreatedAt() : Instant.EPOCH
            ).reversed())
            .map(u -> {
                UUID id = u.getId();
                UserProfile profile = profiles.get(id);
                return new AdminDto.UserSummaryDto(
                    id,
                    u.getEmail(),
                    u.getName(),
                    u.getAuthProvider(),
                    u.getStatus(),
                    u.getPausedReason(),
                    u.isAiBlocked(),
                    u.getDailyAiLimit(),
                    todayAi.getOrDefault(id, 0L),
                    totalAi.getOrDefault(id, 0L),
                    foods.getOrDefault(id, 0L),
                    workouts.getOrDefault(id, 0L),
                    routines.getOrDefault(id, 0L),
                    waters.getOrDefault(id, 0L),
                    weights.getOrDefault(id, 0L),
                    analyses.getOrDefault(id, 0L),
                    profile != null ? profile.getGoal() : "Ganhar peso",
                    profile != null ? profile.getCurrentWeight() : 72.0,
                    profile != null ? profile.getTargetWeight() : 80.0,
                    profile != null ? profile.getTargetCalorieIntake() : 2800.0,
                    u.getCreatedAt(),
                    u.getLastLoginAt(),
                    u.getLastSyncedAt(),
                    u.getForceResetAt(),
                    u.getAccountTier()
                );
            })
            .toList();
    }

    @Transactional(readOnly = true)
    public AdminDto.UserDetailDto getUserDetail(UUID userId) {
        User user = findUserOrThrow(userId);
        Instant startOfDay = currentStartOfDay();

        UserProfile profile = userProfileRepository.findById(userId)
            .orElseGet(() -> new UserProfile(userId));

        List<SyncDto.FoodDto> recentFoods = foodEntryRepository.findByUserIdOrderByConsumedAtDesc(userId).stream()
            .limit(15)
            .map(SyncDto.FoodDto::fromEntity)
            .toList();

        List<SyncDto.WorkoutDto> recentWorkouts = workoutEntryRepository.findByUserIdOrderByWorkoutDateDesc(userId).stream()
            .limit(15)
            .map(SyncDto.WorkoutDto::fromEntity)
            .toList();

        List<AdminDto.AiLogSummaryDto> recentAiLogs = aiRequestLogRepository.findTop15ByUserIdOrderByCreatedAtDesc(userId).stream()
            .map(AdminDto.AiLogSummaryDto::fromEntity)
            .toList();

        return new AdminDto.UserDetailDto(
            toSummary(user, startOfDay),
            SyncDto.ProfileDto.fromEntity(profile),
            recentFoods,
            recentWorkouts,
            recentAiLogs
        );
    }

    @Transactional
    public AdminDto.UserSummaryDto createUser(AdminDto.CreateUserRequest req) {
        if (req == null || req.email() == null || req.email().isBlank()) {
            throw new IllegalArgumentException("Informe um e-mail válido.");
        }
        String email = req.email().trim().toLowerCase();
        if (userRepository.existsByEmail(email)) {
            throw new IllegalArgumentException("Já existe um usuário com este e-mail.");
        }
        if (req.password() == null || req.password().length() < 4) {
            throw new IllegalArgumentException("Informe uma senha com pelo menos 4 caracteres.");
        }

        User user = new User();
        user.setEmail(email);
        user.setName(req.name() != null && !req.name().isBlank() ? req.name().trim() : "Atleta");
        user.setPasswordHash(authService.hashPassword(req.password()));
        user.setAuthProvider("EMAIL");
        user.setStatus("ACTIVE");
        user.setAccountTier(User.normalizeTier(req.accountTier()));
        user.setAiBlocked(false);
        if (req.dailyAiLimit() != null && req.dailyAiLimit() > 0) {
            user.setDailyAiLimit(req.dailyAiLimit());
        }
        user.setCreatedAt(Instant.now());

        User saved = userRepository.save(user);
        userProfileRepository.save(new UserProfile(saved.getId()));
        syncSubscriptionForAdminTier(saved, saved.isPro());

        broadcastState("ADMIN_USER_CREATED", saved);
        return toSummary(saved, currentStartOfDay());
    }

    @Transactional
    public AdminDto.UserSummaryDto updateUser(UUID userId, AdminDto.UpdateUserRequest req) {
        User user = findUserOrThrow(userId);
        String previousTier = user.getAccountTier();

        if (req != null) {
            if (req.name() != null && !req.name().isBlank()) {
                user.setName(req.name().trim());
            }
            if (req.email() != null && !req.email().isBlank()) {
                String normalized = req.email().trim().toLowerCase();
                if (!normalized.equalsIgnoreCase(user.getEmail()) && userRepository.existsByEmail(normalized)) {
                    throw new IllegalArgumentException("Este e-mail já está em uso por outra conta.");
                }
                user.setEmail(normalized);
            }
            if (req.newPassword() != null && !req.newPassword().isBlank()) {
                user.setPasswordHash(authService.hashPassword(req.newPassword().trim()));
            }
            if (req.accountTier() != null && !req.accountTier().isBlank()) {
                user.setAccountTier(User.normalizeTier(req.accountTier()));
            }
        }
        User saved = userRepository.save(user);
        boolean tierChanged = req != null
            && req.accountTier() != null
            && !req.accountTier().isBlank()
            && !User.normalizeTier(previousTier).equalsIgnoreCase(saved.getAccountTier());
        syncSubscriptionForAdminTier(saved, tierChanged);

        if (req != null) {
            UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> new UserProfile(userId));
            if (req.goal() != null && !req.goal().isBlank()) profile.setGoal(req.goal());
            if (req.currentWeight() != null && req.currentWeight() > 0) profile.setCurrentWeight(req.currentWeight());
            if (req.targetWeight() != null && req.targetWeight() > 0) profile.setTargetWeight(req.targetWeight());
            if (req.heightMeters() != null && req.heightMeters() > 0) profile.setHeightMeters(req.heightMeters());
            if (req.targetCalorieIntake() != null && req.targetCalorieIntake() > 0) profile.setTargetCalorieIntake(req.targetCalorieIntake());
            if (req.targetCalorieBurn() != null && req.targetCalorieBurn() >= 0) profile.setTargetCalorieBurn(req.targetCalorieBurn());
            if (req.targetWaterMilliliters() != null && req.targetWaterMilliliters() > 0) {
                profile.setTargetWaterMilliliters(req.targetWaterMilliliters());
            }
            profile.setUpdatedAt(Instant.now());
            userProfileRepository.save(profile);
        }

        broadcastState("ADMIN_USER_UPDATED", saved);
        return toSummary(saved, currentStartOfDay());
    }

    @Transactional
    public AdminDto.UserSummaryDto updateStatus(UUID userId, AdminDto.UserStatusRequest req) {
        User user = findUserOrThrow(userId);

        boolean shouldPause = (req != null && req.paused() != null) ? req.paused() : !user.isPaused();
        user.setStatus(shouldPause ? "PAUSED" : "ACTIVE");
        user.setPausedReason(
            shouldPause && req != null && req.reason() != null && !req.reason().isBlank()
                ? req.reason().trim()
                : null
        );

        User saved = userRepository.save(user);
        broadcastState(shouldPause ? "ADMIN_USER_PAUSED" : "ADMIN_USER_RESUMED", saved);
        return toSummary(saved, currentStartOfDay());
    }

    @Transactional
    public AdminDto.UserSummaryDto updateAiAccess(UUID userId, AdminDto.UserAiAccessRequest req) {
        User user = findUserOrThrow(userId);

        if (req != null) {
            if (req.aiBlocked() != null) {
                user.setAiBlocked(req.aiBlocked());
            }
            if (req.dailyAiLimit() != null) {
                user.setDailyAiLimit(req.dailyAiLimit() > 0 ? req.dailyAiLimit() : null);
            }
        }

        User saved = userRepository.save(user);
        broadcastState("ADMIN_USER_AI_UPDATED", saved);
        return toSummary(saved, currentStartOfDay());
    }

    @Transactional
    public AdminDto.UserSummaryDto updateAccountTier(UUID userId, AdminDto.UserTierRequest req) {
        User user = findUserOrThrow(userId);
        String nextTier = (req != null && req.accountTier() != null && !req.accountTier().isBlank())
            ? User.normalizeTier(req.accountTier())
            : (user.isPro() ? "FREE" : "PRO");
        user.setAccountTier(nextTier);

        User saved = userRepository.save(user);
        syncSubscriptionForAdminTier(saved, true);
        broadcastState("PRO".equals(nextTier) ? "ADMIN_USER_TIER_PRO" : "ADMIN_USER_TIER_FREE", saved);
        return toSummary(saved, currentStartOfDay());
    }

    @Transactional
    public AdminDto.UserSummaryDto resetUserAccount(UUID userId, AdminDto.ResetUserRequest req) {
        User user = findUserOrThrow(userId);

        String scope = (req != null && req.scope() != null && !req.scope().isBlank())
            ? req.scope().trim().toUpperCase()
            : null;
        boolean clearMealsWorkouts;
        boolean clearHydrationWeights;
        boolean clearRoutines;
        boolean clearAnalysesLogs;
        boolean resetProfile;
        boolean clearAiLogsFlag;

        if (scope != null) {
            clearMealsWorkouts = "MEALS_AND_WORKOUTS".equals(scope)
                || "ALL_HEALTH_DATA".equals(scope)
                || Boolean.TRUE.equals(req.clearMealsAndWorkouts());
            clearHydrationWeights = "ALL_HEALTH_DATA".equals(scope)
                || Boolean.TRUE.equals(req.clearHydrationAndWeights());
            clearRoutines = "MEALS_AND_WORKOUTS".equals(scope)
                || "ALL_HEALTH_DATA".equals(scope)
                || Boolean.TRUE.equals(req.clearRoutines());
            clearAnalysesLogs = "AI_HISTORY".equals(scope)
                || "ALL_HEALTH_DATA".equals(scope)
                || Boolean.TRUE.equals(req.clearAnalysesAndLogs());
            resetProfile = "PROFILE_GOALS".equals(scope)
                || "ALL_HEALTH_DATA".equals(scope)
                || Boolean.TRUE.equals(req.resetProfileGoals());
            clearAiLogsFlag = !Boolean.FALSE.equals(req.clearAiLogs())
                && (Boolean.TRUE.equals(req.clearAiLogs())
                    || "AI_HISTORY".equals(scope)
                    || "ALL_HEALTH_DATA".equals(scope));
        } else {
            clearMealsWorkouts = req == null || req.clearMealsAndWorkouts() == null || req.clearMealsAndWorkouts();
            clearHydrationWeights = req == null || req.clearHydrationAndWeights() == null || req.clearHydrationAndWeights();
            clearRoutines = req == null || req.clearRoutines() == null || req.clearRoutines();
            clearAnalysesLogs = req == null || req.clearAnalysesAndLogs() == null || req.clearAnalysesAndLogs();
            resetProfile = req == null || req.resetProfileGoals() == null || req.resetProfileGoals();
            clearAiLogsFlag = req != null && Boolean.TRUE.equals(req.clearAiLogs());
        }

        if (clearMealsWorkouts) {
            foodEntryRepository.deleteByUserId(userId);
            workoutEntryRepository.deleteByUserId(userId);
        }
        if (clearHydrationWeights) {
            waterEntryRepository.deleteByUserId(userId);
            weightEntryRepository.deleteByUserId(userId);
        }
        if (clearRoutines) {
            workoutRoutineRepository.deleteByUserId(userId);
        }
        if (clearAnalysesLogs) {
            aiAnalysisRepository.deleteByUserId(userId);
            userActivityLogRepository.deleteByUserId(userId);
        }
        if (clearAiLogsFlag) {
            aiRequestLogRepository.deleteByUserId(userId);
        }
        if (resetProfile) {
            UserProfile cleanProfile = new UserProfile(userId);
            cleanProfile.setUpdatedAt(Instant.now());
            userProfileRepository.save(cleanProfile);
        }

        user.setForceResetAt(Instant.now());
        User saved = userRepository.save(user);

        adminEventPublisherService.publishSyncActivity(
            "ACCOUNT_RESET",
            0,
            foodEntryRepository.count(),
            workoutEntryRepository.count()
        );
        broadcastState("ADMIN_USER_RESET", saved);
        return toSummary(saved, currentStartOfDay());
    }

    @Transactional
    public void deleteUserPermanently(UUID userId) {
        deleteUserPermanently(userId, null, null);
    }

    @Transactional
    public void deleteUserPermanently(UUID userId, String appleAuthorizationCode) {
        deleteUserPermanently(userId, appleAuthorizationCode, null);
    }

    @Transactional
    public void deleteUserPermanently(UUID userId, String appleAuthorizationCode, String googleToken) {
        User user = findUserOrThrow(userId);

        if (socialAuthVerificationService != null) {
            boolean hasAppleLink = "APPLE".equalsIgnoreCase(user.getAuthProvider())
                || (user.getAppleRefreshToken() != null && !user.getAppleRefreshToken().isBlank())
                || (appleAuthorizationCode != null && !appleAuthorizationCode.isBlank());
            if (hasAppleLink) {
                socialAuthVerificationService.revokeAppleTokensOnAccountDeletion(
                    user.getAppleRefreshToken(),
                    appleAuthorizationCode
                );
            }

            boolean hasGoogleLink = "GOOGLE".equalsIgnoreCase(user.getAuthProvider())
                || (user.getGoogleRefreshToken() != null && !user.getGoogleRefreshToken().isBlank())
                || (googleToken != null && !googleToken.isBlank());
            if (hasGoogleLink) {
                socialAuthVerificationService.revokeGoogleTokensOnAccountDeletion(
                    user.getGoogleRefreshToken(),
                    googleToken
                );
            }
        }

        if (legalConsentLedgerRepository != null && fieldEncryptionService != null) {
            String anonId = "anon:" + fieldEncryptionService.hmacSha256Hex(userId.toString()).substring(0, 48);
            legalConsentLedgerRepository.pseudonymizeByUserId(userId.toString(), anonId);
        }
        if (deletedAccountTombstoneRepository != null) {
            deletedAccountTombstoneRepository.save(
                new DeletedAccountTombstoneEntity(userId.toString(), "PERMANENT_DELETE")
            );
        }

        foodEntryRepository.deleteByUserId(userId);
        workoutEntryRepository.deleteByUserId(userId);
        waterEntryRepository.deleteByUserId(userId);
        weightEntryRepository.deleteByUserId(userId);
        workoutRoutineRepository.deleteByUserId(userId);
        aiAnalysisRepository.deleteByUserId(userId);
        userActivityLogRepository.deleteByUserId(userId);
        aiRequestLogRepository.deleteByUserId(userId);
        if (dunningNoticeRepository != null) {
            dunningNoticeRepository.deleteByUserId(userId);
        }
        if (couponRedemptionRepository != null) {
            couponRedemptionRepository.deleteByUserId(userId);
        }
        if (userSubscriptionRepository != null) {
            userSubscriptionRepository.deleteByUserId(userId);
        }
        userProfileRepository.deleteById(userId);
        userRepository.delete(user);

        adminEventPublisherService.publishSyncActivity(
            "ACCOUNT_DELETED",
            0,
            foodEntryRepository.count(),
            workoutEntryRepository.count()
        );
        adminEventPublisherService.publishUserActivity(
            "ADMIN_USER_DELETED",
            user.getEmail(),
            user.getName(),
            user.getAuthProvider(),
            userRepository.count()
        );
        adminEventPublisherService.publishUserAccountDeleted(userId);
    }

    private User findUserOrThrow(UUID userId) {
        return userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
    }

    private static Instant currentStartOfDay() {
        return LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant();
    }

    private void syncSubscriptionForAdminTier(User saved, boolean tierChanged) {
        if (!tierChanged || userSubscriptionRepository == null || saved == null) {
            return;
        }
        Instant now = Instant.now();
        if (saved.isPro()) {
            UserSubscriptionEntity sub = userSubscriptionRepository.findByUserId(saved.getId())
                .orElseGet(UserSubscriptionEntity::new);
            String existingStatus = sub.getStatus();
            if (!"ACTIVE".equalsIgnoreCase(existingStatus) && !"TRIALING".equalsIgnoreCase(existingStatus)) {
                Instant end = now.plus(Duration.ofDays(30));
                sub.setUserId(saved.getId());
                sub.setUserEmail(saved.getEmail());
                sub.setPlanId("PRO_MONTHLY");
                sub.setPlanName("Forma Pro Mensal");
                sub.setSource("ADMIN_MANUAL");
                sub.setStatus("TRIALING");
                sub.setPriceCents(0);
                sub.setCurrency("BRL");
                sub.setAutoRenew(false);
                sub.setCurrentPeriodStartUtc(now);
                sub.setCurrentPeriodEndUtc(end);
                sub.setTrialEndsAtUtc(end);
                sub.setOverdueSinceUtc(null);
                sub.setGraceEndsAtUtc(null);
                sub.setLastWarningDaySent(0);
                sub.setDunningWarningsSent(0);
                sub.setPausedAtUtc(null);
                sub.setCanceledAtUtc(null);
                sub.setStatusReason("Cortesia liberada (30 dias grátis)");
                sub.setUpdatedAtUtc(now);
                userSubscriptionRepository.save(sub);
            }
            adminEventPublisherService.publishUserSubscriptionState(
                saved,
                "SUBSCRIPTION_ACTIVATED",
                "TRIALING",
                null,
                null,
                "Forma Pro liberado!"
            );
            adminEventPublisherService.publishBillingUpdated("ADMIN_USER_TIER_PRO");
        } else {
            userSubscriptionRepository.findByUserId(saved.getId()).ifPresent(sub -> {
                String status = sub.getStatus();
                if ("ACTIVE".equalsIgnoreCase(status)
                    || "TRIALING".equalsIgnoreCase(status)
                    || "PAST_DUE".equalsIgnoreCase(status)) {
                    sub.setStatus("CANCELED");
                    sub.setCanceledAtUtc(now);
                    sub.setAutoRenew(false);
                    sub.setOverdueSinceUtc(null);
                    sub.setGraceEndsAtUtc(null);
                    sub.setStatusReason("Alterado para Camada Essencial pelo administrador");
                    sub.setUpdatedAtUtc(now);
                    userSubscriptionRepository.save(sub);
                }
            });
            adminEventPublisherService.publishUserSubscriptionState(
                saved,
                "SUBSCRIPTION_ENFORCED",
                "CANCELED",
                null,
                null,
                "Conta alterada para a Camada Essencial."
            );
            adminEventPublisherService.publishBillingUpdated("ADMIN_USER_TIER_FREE");
        }
    }

    private static <T> CompletableFuture<T> supplyParallelOrInline(Supplier<T> supplier) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            return CompletableFuture.completedFuture(supplier.get());
        }
        return CompletableFuture.supplyAsync(() -> {
            QUERY_CONCURRENCY.acquireUninterruptibly();
            try {
                return supplier.get();
            } finally {
                QUERY_CONCURRENCY.release();
            }
        }, QUERY_EXECUTOR);
    }

    private static Map<UUID, Long> toCountMap(List<Object[]> rows) {
        Map<UUID, Long> result = new HashMap<>();
        if (rows == null) return result;
        for (Object[] row : rows) {
            if (row != null && row.length >= 2 && row[0] instanceof UUID id && row[1] instanceof Number num) {
                result.put(id, num.longValue());
            }
        }
        return result;
    }

    private void broadcastState(String eventName, User user) {
        adminEventPublisherService.publishUserActivity(
            eventName,
            user.getEmail(),
            user.getName(),
            user.getAuthProvider(),
            userRepository.count()
        );
        adminEventPublisherService.publishUserAccountState(user, eventName);
    }

    private AdminDto.UserSummaryDto toSummary(User u, Instant startOfDay) {
        UUID id = u.getId();
        UserProfile profile = userProfileRepository.findById(id).orElse(null);

        return new AdminDto.UserSummaryDto(
            id,
            u.getEmail(),
            u.getName(),
            u.getAuthProvider(),
            u.getStatus(),
            u.getPausedReason(),
            u.isAiBlocked(),
            u.getDailyAiLimit(),
            aiRequestLogRepository.countByUserIdAndCreatedAtAfter(id, startOfDay),
            aiRequestLogRepository.countByUserId(id),
            foodEntryRepository.countByUserId(id),
            workoutEntryRepository.countByUserId(id),
            workoutRoutineRepository.countByUserId(id),
            waterEntryRepository.countByUserId(id),
            weightEntryRepository.countByUserId(id),
            aiAnalysisRepository.countByUserId(id),
            profile != null ? profile.getGoal() : "Ganhar peso",
            profile != null ? profile.getCurrentWeight() : 72.0,
            profile != null ? profile.getTargetWeight() : 80.0,
            profile != null ? profile.getTargetCalorieIntake() : 2800.0,
            u.getCreatedAt(),
            u.getLastLoginAt(),
            u.getLastSyncedAt(),
            u.getForceResetAt(),
            u.getAccountTier()
        );
    }
}

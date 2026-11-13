package com.forma.backend.service;

import com.forma.backend.dto.SyncDto;
import com.forma.backend.entity.*;
import com.forma.backend.exception.DomainExceptions.ResourceNotFoundException;
import com.forma.backend.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class SyncService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final FoodEntryRepository foodEntryRepository;
    private final WorkoutEntryRepository workoutEntryRepository;
    private final WaterEntryRepository waterEntryRepository;
    private final WeightEntryRepository weightEntryRepository;
    private final WorkoutRoutineRepository workoutRoutineRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final UserActivityLogRepository userActivityLogRepository;

    public SyncService(
        UserRepository userRepository,
        UserProfileRepository userProfileRepository,
        FoodEntryRepository foodEntryRepository,
        WorkoutEntryRepository workoutEntryRepository,
        WaterEntryRepository waterEntryRepository,
        WeightEntryRepository weightEntryRepository,
        WorkoutRoutineRepository workoutRoutineRepository,
        AiAnalysisRepository aiAnalysisRepository,
        UserActivityLogRepository userActivityLogRepository
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
    }

    @Transactional
    public void pushData(UUID userId, SyncDto.SyncPushRequest request) {
        if (request == null) return;

        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        processDeletions(userId, request);

        upsertProfile(userId, request.profile());
        upsertFoods(userId, request.foods());
        upsertWorkouts(userId, request.workouts());
        upsertWaters(userId, request.waters());
        upsertWeights(userId, request.weights());
        upsertRoutines(userId, request.routines());
        upsertAnalyses(userId, request.analyses());
        upsertActivityLogs(userId, request.activityLogs());

        user.setLastSyncedAt(Instant.now());
        userRepository.save(user);
    }

    @Transactional
    public Map<String, Object> saveProfileDirect(UUID userId, SyncDto.ProfileWriteRequest req) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));

        if (req != null && req.name() != null && !req.name().isBlank()) {
            user.setName(req.name().trim());
        }

        UserProfile profile = userProfileRepository.findById(userId)
            .orElseGet(() -> new UserProfile(userId));

        if (req != null) {
            if (req.goal() != null && !req.goal().isBlank()) profile.setGoal(req.goal());
            if (isPositive(req.targetCalorieIntake())) profile.setTargetCalorieIntake(req.targetCalorieIntake());
            if (isNonNegative(req.targetCalorieBurn())) profile.setTargetCalorieBurn(req.targetCalorieBurn());
            if (isPositive(req.targetSteps())) profile.setTargetSteps(req.targetSteps());
            if (isPositive(req.targetWeight())) profile.setTargetWeight(req.targetWeight());
            if (isNonNegative(req.targetProtein())) profile.setTargetProtein(req.targetProtein());
            if (isNonNegative(req.targetCarbohydrates())) profile.setTargetCarbohydrates(req.targetCarbohydrates());
            if (isNonNegative(req.targetFat())) profile.setTargetFat(req.targetFat());
            if (req.targetWaterMilliliters() != null && req.targetWaterMilliliters() > 0) {
                profile.setTargetWaterMilliliters(req.targetWaterMilliliters());
            }
            if (isPositive(req.heightMeters())) profile.setHeightMeters(req.heightMeters());
            if (isPositive(req.currentWeight())) profile.setCurrentWeight(req.currentWeight());
            if (req.onboardingCompleted() != null) profile.setOnboardingCompleted(req.onboardingCompleted());
        }
        profile.setUpdatedAt(Instant.now());
        UserProfile savedProfile = userProfileRepository.save(profile);

        user.setLastSyncedAt(Instant.now());
        User savedUser = userRepository.save(user);

        return Map.of(
            "status", "saved",
            "name", savedUser.getName() != null ? savedUser.getName() : "",
            "accountTier", savedUser.getAccountTier(),
            "profile", SyncDto.ProfileDto.fromEntity(savedProfile)
        );
    }

    @Transactional
    public List<SyncDto.FoodDto> saveFoodsDirect(UUID userId, SyncDto.FoodBatchWriteRequest req) {
        if (req == null || req.foods() == null || req.foods().isEmpty()) {
            return List.of();
        }
        touchUserSyncTimestamp(userId);
        upsertFoods(userId, req.foods());
        upsertActivityLogs(userId, req.activityLogs());
        List<UUID> ids = req.foods().stream().map(SyncDto.FoodDto::id).filter(java.util.Objects::nonNull).toList();
        return foodEntryRepository.findAllById(ids).stream()
            .filter(e -> userId.equals(e.getUserId()))
            .map(SyncDto.FoodDto::fromEntity)
            .toList();
    }

    @Transactional
    public void deleteFoodDirect(UUID userId, UUID foodId) {
        if (foodId == null) return;
        deleteFoodsBatchDirect(userId, List.of(foodId));
    }

    @Transactional
    public void deleteFoodsBatchDirect(UUID userId, List<UUID> foodIds) {
        if (foodIds == null || foodIds.isEmpty()) return;
        touchUserSyncTimestamp(userId);
        for (UUID id : foodIds) {
            if (id == null) continue;
            foodEntryRepository.findById(id).ifPresent(entity -> {
                if (userId.equals(entity.getUserId())) {
                    foodEntryRepository.delete(entity);
                    deleteOwnedActivityLog(userId, id.toString());
                }
            });
        }
    }

    @Transactional
    public SyncDto.WorkoutDto saveWorkoutDirect(UUID userId, SyncDto.WorkoutWriteRequest req) {
        if (req == null || req.workout() == null || req.workout().id() == null) {
            throw new IllegalArgumentException("Dados do treino inválidos.");
        }
        touchUserSyncTimestamp(userId);
        upsertWorkouts(userId, List.of(req.workout()));
        if (req.activityLog() != null) {
            upsertActivityLogs(userId, List.of(req.activityLog()));
        }
        if (req.updatedRoutine() != null) {
            upsertRoutines(userId, List.of(req.updatedRoutine()));
        }
        return workoutEntryRepository.findById(req.workout().id())
            .filter(e -> userId.equals(e.getUserId()))
            .map(SyncDto.WorkoutDto::fromEntity)
            .orElse(req.workout());
    }

    @Transactional
    public void deleteWorkoutDirect(UUID userId, UUID workoutId) {
        if (workoutId == null) return;
        touchUserSyncTimestamp(userId);
        workoutEntryRepository.findById(workoutId).ifPresent(entity -> {
            if (userId.equals(entity.getUserId())) {
                workoutEntryRepository.delete(entity);
                deleteOwnedActivityLog(userId, workoutId.toString());
            }
        });
    }

    @Transactional
    public SyncDto.RoutineDto saveRoutineDirect(UUID userId, SyncDto.RoutineDto routine) {
        if (routine == null || routine.id() == null) {
            throw new IllegalArgumentException("Dados da ficha inválidos.");
        }
        touchUserSyncTimestamp(userId);
        upsertRoutines(userId, List.of(routine));
        return workoutRoutineRepository.findById(routine.id())
            .filter(e -> userId.equals(e.getUserId()))
            .map(SyncDto.RoutineDto::fromEntity)
            .orElse(routine);
    }

    @Transactional
    public void deleteRoutineDirect(UUID userId, UUID routineId) {
        if (routineId == null) return;
        touchUserSyncTimestamp(userId);
        workoutRoutineRepository.findById(routineId).ifPresent(entity -> {
            if (userId.equals(entity.getUserId())) {
                workoutRoutineRepository.delete(entity);
            }
        });
    }

    @Transactional
    public SyncDto.WaterDto saveWaterDirect(UUID userId, SyncDto.WaterWriteRequest req) {
        if (req == null || req.water() == null || req.water().id() == null) {
            throw new IllegalArgumentException("Dados de hidratação inválidos.");
        }
        touchUserSyncTimestamp(userId);
        upsertWaters(userId, List.of(req.water()));
        if (req.activityLog() != null) {
            upsertActivityLogs(userId, List.of(req.activityLog()));
        }
        return waterEntryRepository.findById(req.water().id())
            .filter(e -> userId.equals(e.getUserId()))
            .map(SyncDto.WaterDto::fromEntity)
            .orElse(req.water());
    }

    @Transactional
    public void deleteWaterDirect(UUID userId, UUID waterId) {
        if (waterId == null) return;
        touchUserSyncTimestamp(userId);
        waterEntryRepository.findById(waterId).ifPresent(entity -> {
            if (userId.equals(entity.getUserId())) {
                waterEntryRepository.delete(entity);
                deleteOwnedActivityLog(userId, waterId.toString());
            }
        });
    }

    @Transactional
    public SyncDto.WeightDto saveWeightDirect(UUID userId, SyncDto.WeightWriteRequest req) {
        if (req == null || req.weight() == null || req.weight().id() == null) {
            throw new IllegalArgumentException("Dados de pesagem inválidos.");
        }
        touchUserSyncTimestamp(userId);
        upsertWeights(userId, List.of(req.weight()));
        if (req.activityLog() != null) {
            upsertActivityLogs(userId, List.of(req.activityLog()));
        }
        if (req.profileUpdate() != null) {
            saveProfileDirect(userId, req.profileUpdate());
        } else if (isPositive(req.weight().weight())) {
            UserProfile profile = userProfileRepository.findById(userId)
                .orElseGet(() -> new UserProfile(userId));
            profile.setCurrentWeight(req.weight().weight());
            profile.setUpdatedAt(Instant.now());
            userProfileRepository.save(profile);
        }
        return weightEntryRepository.findById(req.weight().id())
            .filter(e -> userId.equals(e.getUserId()))
            .map(SyncDto.WeightDto::fromEntity)
            .orElse(req.weight());
    }

    @Transactional
    public void deleteWeightDirect(UUID userId, UUID weightId) {
        if (weightId == null) return;
        touchUserSyncTimestamp(userId);
        weightEntryRepository.findById(weightId).ifPresent(entity -> {
            if (userId.equals(entity.getUserId())) {
                weightEntryRepository.delete(entity);
                deleteOwnedActivityLog(userId, weightId.toString());
            }
        });
    }

    @Transactional
    public SyncDto.AiAnalysisDto saveAnalysisDirect(UUID userId, SyncDto.AiAnalysisDto dto) {
        if (dto == null || dto.id() == null) {
            throw new IllegalArgumentException("Dados da análise inválidos.");
        }
        touchUserSyncTimestamp(userId);
        upsertAnalyses(userId, List.of(dto));
        return aiAnalysisRepository.findById(dto.id())
            .filter(e -> userId.equals(e.getUserId()))
            .map(SyncDto.AiAnalysisDto::fromEntity)
            .orElse(dto);
    }

    private void touchUserSyncTimestamp(UUID userId) {
        User user = userRepository.findById(userId)
            .orElseThrow(() -> new ResourceNotFoundException("Usuário não encontrado."));
        user.setLastSyncedAt(Instant.now());
        userRepository.save(user);
    }

    private void processDeletions(UUID userId, SyncDto.SyncPushRequest request) {
        if (request.deletedFoodIds() != null) {
            for (UUID id : request.deletedFoodIds()) {
                if (id == null) continue;
                foodEntryRepository.findById(id).ifPresent(entity -> {
                    if (userId.equals(entity.getUserId())) {
                        foodEntryRepository.delete(entity);
                        deleteOwnedActivityLog(userId, id.toString());
                    }
                });
            }
        }
        if (request.deletedWorkoutIds() != null) {
            for (UUID id : request.deletedWorkoutIds()) {
                if (id == null) continue;
                workoutEntryRepository.findById(id).ifPresent(entity -> {
                    if (userId.equals(entity.getUserId())) {
                        workoutEntryRepository.delete(entity);
                        deleteOwnedActivityLog(userId, id.toString());
                    }
                });
            }
        }
        if (request.deletedWaterIds() != null) {
            for (UUID id : request.deletedWaterIds()) {
                if (id == null) continue;
                waterEntryRepository.findById(id).ifPresent(entity -> {
                    if (userId.equals(entity.getUserId())) {
                        waterEntryRepository.delete(entity);
                        deleteOwnedActivityLog(userId, id.toString());
                    }
                });
            }
        }
        if (request.deletedWeightIds() != null) {
            for (UUID id : request.deletedWeightIds()) {
                if (id == null) continue;
                weightEntryRepository.findById(id).ifPresent(entity -> {
                    if (userId.equals(entity.getUserId())) {
                        weightEntryRepository.delete(entity);
                        deleteOwnedActivityLog(userId, id.toString());
                    }
                });
            }
        }
        if (request.deletedRoutineIds() != null) {
            for (UUID id : request.deletedRoutineIds()) {
                if (id == null) continue;
                workoutRoutineRepository.findById(id).ifPresent(entity -> {
                    if (userId.equals(entity.getUserId())) {
                        workoutRoutineRepository.delete(entity);
                    }
                });
            }
        }
    }

    private void deleteOwnedActivityLog(UUID userId, String logId) {
        userActivityLogRepository.findById(logId).ifPresent(log -> {
            if (userId.equals(log.getUserId())) {
                userActivityLogRepository.delete(log);
            }
        });
    }

    @Transactional(readOnly = true)
    public SyncDto.SyncPullResponse pullData(UUID userId) {
        User user = userRepository.findById(userId).orElse(null);
        Long forceResetEpochMs = (user != null && user.getForceResetAt() != null)
            ? user.getForceResetAt().toEpochMilli()
            : null;

        UserProfile profile = userProfileRepository.findById(userId)
            .orElseGet(() -> new UserProfile(userId));

        return new SyncDto.SyncPullResponse(
            SyncDto.ProfileDto.fromEntity(profile),
            foodEntryRepository.findByUserIdOrderByConsumedAtDesc(userId).stream()
                .map(SyncDto.FoodDto::fromEntity).toList(),
            workoutEntryRepository.findByUserIdOrderByWorkoutDateDesc(userId).stream()
                .map(SyncDto.WorkoutDto::fromEntity).toList(),
            waterEntryRepository.findByUserIdOrderByEntryDateDesc(userId).stream()
                .map(SyncDto.WaterDto::fromEntity).toList(),
            weightEntryRepository.findByUserIdOrderByEntryDateDesc(userId).stream()
                .map(SyncDto.WeightDto::fromEntity).toList(),
            workoutRoutineRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(SyncDto.RoutineDto::fromEntity).toList(),
            aiAnalysisRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .map(SyncDto.AiAnalysisDto::fromEntity).toList(),
            userActivityLogRepository.findByUserIdOrderByActivityTimestampDesc(userId).stream()
                .map(SyncDto.ActivityLogDto::fromEntity).toList(),
            Instant.now(),
            forceResetEpochMs,
            user != null ? user.getAccountTier() : "FREE"
        );
    }

    private void upsertProfile(UUID userId, SyncDto.ProfileDto p) {
        if (p == null) return;
        UserProfile profile = userProfileRepository.findById(userId)
            .orElseGet(() -> new UserProfile(userId));
        if (p.goal() != null && !p.goal().isBlank()) profile.setGoal(p.goal());
        if (isPositive(p.targetCalorieIntake())) profile.setTargetCalorieIntake(p.targetCalorieIntake());
        if (isNonNegative(p.targetCalorieBurn())) profile.setTargetCalorieBurn(p.targetCalorieBurn());
        if (isPositive(p.targetSteps())) profile.setTargetSteps(p.targetSteps());
        if (isPositive(p.targetWeight())) profile.setTargetWeight(p.targetWeight());
        if (isNonNegative(p.targetProtein())) profile.setTargetProtein(p.targetProtein());
        if (isNonNegative(p.targetCarbohydrates())) profile.setTargetCarbohydrates(p.targetCarbohydrates());
        if (isNonNegative(p.targetFat())) profile.setTargetFat(p.targetFat());
        if (p.targetWaterMilliliters() != null && p.targetWaterMilliliters() > 0) {
            profile.setTargetWaterMilliliters(p.targetWaterMilliliters());
        }
        if (isPositive(p.heightMeters())) profile.setHeightMeters(p.heightMeters());
        if (isPositive(p.currentWeight())) profile.setCurrentWeight(p.currentWeight());
        if (p.onboardingCompleted() != null) profile.setOnboardingCompleted(p.onboardingCompleted());
        profile.setUpdatedAt(Instant.now());
        userProfileRepository.save(profile);
    }

    private void upsertFoods(UUID userId, List<SyncDto.FoodDto> foods) {
        if (foods == null || foods.isEmpty()) return;
        List<FoodEntryEntity> batch = new ArrayList<>();
        for (SyncDto.FoodDto f : foods) {
            if (f == null || f.id() == null) continue;
            var existing = foodEntryRepository.findById(f.id());
            if (existing.isPresent() && isOwnedByAnotherUser(existing.get().getUserId(), userId)) {
                continue;
            }
            FoodEntryEntity entity = existing.orElseGet(FoodEntryEntity::new);
            entity.setId(f.id());
            entity.setUserId(userId);
            entity.setName(f.name() != null && !f.name().isBlank() ? f.name() : "Alimento");
            entity.setBrand(f.brand());
            entity.setMealType(f.mealType() != null ? f.mealType() : "Lanches");
            entity.setPortionGrams(safeNonNegative(f.portionGrams(), 100.0));
            entity.setCalories(safeNonNegative(f.calories(), 0.0));
            entity.setProtein(safeNonNegative(f.protein(), 0.0));
            entity.setCarbohydrates(safeNonNegative(f.carbohydrates(), 0.0));
            entity.setFat(safeNonNegative(f.fat(), 0.0));
            entity.setConsumedAt(f.consumedAt() != null ? f.consumedAt() : Instant.now());
            batch.add(entity);
        }
        if (!batch.isEmpty()) foodEntryRepository.saveAll(batch);
    }

    private void upsertWorkouts(UUID userId, List<SyncDto.WorkoutDto> workouts) {
        if (workouts == null || workouts.isEmpty()) return;
        List<WorkoutEntryEntity> batch = new ArrayList<>();
        for (SyncDto.WorkoutDto w : workouts) {
            if (w == null || w.id() == null) continue;
            var existing = workoutEntryRepository.findById(w.id());
            if (existing.isPresent() && isOwnedByAnotherUser(existing.get().getUserId(), userId)) {
                continue;
            }
            WorkoutEntryEntity entity = existing.orElseGet(WorkoutEntryEntity::new);
            entity.setId(w.id());
            entity.setUserId(userId);
            entity.setTitle(w.title() != null && !w.title().isBlank() ? w.title() : "Treino");
            entity.setWorkoutType(w.workoutType() != null ? w.workoutType() : "Musculação");
            entity.setDurationMinutes(w.durationMinutes() != null && w.durationMinutes() > 0 ? w.durationMinutes() : 45);
            entity.setCaloriesBurned(safeNonNegative(w.caloriesBurned(), 300.0));
            entity.setNotes(w.notes());
            entity.setWorkoutDate(w.workoutDate() != null ? w.workoutDate() : Instant.now());
            batch.add(entity);
        }
        if (!batch.isEmpty()) workoutEntryRepository.saveAll(batch);
    }

    private void upsertWaters(UUID userId, List<SyncDto.WaterDto> waters) {
        if (waters == null || waters.isEmpty()) return;
        List<WaterEntryEntity> batch = new ArrayList<>();
        for (SyncDto.WaterDto wt : waters) {
            if (wt == null || wt.id() == null) continue;
            var existing = waterEntryRepository.findById(wt.id());
            if (existing.isPresent() && isOwnedByAnotherUser(existing.get().getUserId(), userId)) {
                continue;
            }
            WaterEntryEntity entity = existing.orElseGet(WaterEntryEntity::new);
            entity.setId(wt.id());
            entity.setUserId(userId);
            entity.setMilliliters(wt.milliliters() != null && wt.milliliters() > 0 ? wt.milliliters() : 250);
            entity.setEntryDate(wt.entryDate() != null ? wt.entryDate() : Instant.now());
            batch.add(entity);
        }
        if (!batch.isEmpty()) waterEntryRepository.saveAll(batch);
    }

    private void upsertWeights(UUID userId, List<SyncDto.WeightDto> weights) {
        if (weights == null || weights.isEmpty()) return;
        List<WeightEntryEntity> batch = new ArrayList<>();
        for (SyncDto.WeightDto wg : weights) {
            if (wg == null || wg.id() == null) continue;
            var existing = weightEntryRepository.findById(wg.id());
            if (existing.isPresent() && isOwnedByAnotherUser(existing.get().getUserId(), userId)) {
                continue;
            }
            WeightEntryEntity entity = existing.orElseGet(WeightEntryEntity::new);
            entity.setId(wg.id());
            entity.setUserId(userId);
            entity.setWeight(isPositive(wg.weight()) ? wg.weight() : 70.0);
            entity.setEntryDate(wg.entryDate() != null ? wg.entryDate() : Instant.now());
            batch.add(entity);
        }
        if (!batch.isEmpty()) weightEntryRepository.saveAll(batch);
    }

    private void upsertRoutines(UUID userId, List<SyncDto.RoutineDto> routines) {
        if (routines == null || routines.isEmpty()) return;
        List<WorkoutRoutineEntity> batch = new ArrayList<>();
        for (SyncDto.RoutineDto r : routines) {
            if (r == null || r.id() == null) continue;
            var existing = workoutRoutineRepository.findById(r.id());
            if (existing.isPresent() && isOwnedByAnotherUser(existing.get().getUserId(), userId)) {
                continue;
            }
            WorkoutRoutineEntity entity = existing.orElseGet(WorkoutRoutineEntity::new);
            entity.setId(r.id());
            entity.setUserId(userId);
            entity.setTitle(r.title() != null && !r.title().isBlank() ? r.title() : "Ficha de Treino");
            entity.setWorkoutType(r.workoutType() != null ? r.workoutType() : "Musculação");
            entity.setTargetMuscles(r.targetMuscles());
            entity.setHypertrophyFocus(r.hypertrophyFocus());
            entity.setEstimatedDurationMinutes(r.estimatedDurationMinutes() != null ? r.estimatedDurationMinutes() : 50);
            entity.setEstimatedCalories(safeNonNegative(r.estimatedCalories(), 350.0));
            entity.setRecommendedRestSeconds(r.recommendedRestSeconds() != null ? r.recommendedRestSeconds() : 90);
            entity.setIsFavorite(r.isFavorite() != null ? r.isFavorite() : false);
            entity.setExercisesJson(r.exercisesJson() != null ? r.exercisesJson() : "[]");
            entity.setNotes(r.notes());
            entity.setCreatedAt(r.createdAt() != null ? r.createdAt() : Instant.now());
            entity.setLastTrainedAt(r.lastTrainedAt());
            batch.add(entity);
        }
        if (!batch.isEmpty()) workoutRoutineRepository.saveAll(batch);
    }

    private void upsertAnalyses(UUID userId, List<SyncDto.AiAnalysisDto> analyses) {
        if (analyses == null || analyses.isEmpty()) return;
        List<AiAnalysisEntity> batch = new ArrayList<>();
        for (SyncDto.AiAnalysisDto a : analyses) {
            if (a == null || a.id() == null) continue;
            var existing = aiAnalysisRepository.findById(a.id());
            if (existing.isPresent() && isOwnedByAnotherUser(existing.get().getUserId(), userId)) {
                continue;
            }
            AiAnalysisEntity entity = existing.orElseGet(AiAnalysisEntity::new);
            entity.setId(a.id());
            entity.setUserId(userId);
            entity.setStatusSummary(a.statusSummary());
            entity.setCalorieDiagnostic(a.calorieDiagnostic());
            entity.setMacroDiagnostic(a.macroDiagnostic());
            entity.setHydrationDiagnostic(a.hydrationDiagnostic());
            entity.setWorkoutSuggestionsJson(a.workoutSuggestionsJson());
            entity.setNutritionSuggestionsJson(a.nutritionSuggestionsJson());
            entity.setWeeklyActionPlan(a.weeklyActionPlan());
            entity.setSuggestedTargetsJson(a.suggestedTargetsJson());
            entity.setRecommendedRoutineJson(a.recommendedRoutineJson());
            entity.setCreatedAt(a.createdAt() != null ? a.createdAt() : Instant.now());
            batch.add(entity);
        }
        if (!batch.isEmpty()) aiAnalysisRepository.saveAll(batch);
    }

    private void upsertActivityLogs(UUID userId, List<SyncDto.ActivityLogDto> logs) {
        if (logs == null || logs.isEmpty()) return;
        List<UserActivityLogEntity> batch = new ArrayList<>();
        for (SyncDto.ActivityLogDto l : logs) {
            if (l == null || l.id() == null || l.id().isBlank()) continue;
            var existing = userActivityLogRepository.findById(l.id());
            if (existing.isPresent() && isOwnedByAnotherUser(existing.get().getUserId(), userId)) {
                continue;
            }
            UserActivityLogEntity entity = existing.orElseGet(UserActivityLogEntity::new);
            entity.setId(l.id());
            entity.setUserId(userId);
            entity.setCategory(l.category() != null ? l.category() : "activity");
            entity.setTitle(l.title() != null ? l.title() : "");
            entity.setIsSnack(l.isSnack() != null ? l.isSnack() : false);
            entity.setCaloriesChange(l.caloriesChange() != null && Double.isFinite(l.caloriesChange()) ? l.caloriesChange() : 0.0);
            entity.setProteinGrams(l.proteinGrams());
            entity.setCarbsGrams(l.carbsGrams());
            entity.setFatGrams(l.fatGrams());
            entity.setWaterMilliliters(l.waterMilliliters());
            entity.setWeightKg(l.weightKg());
            entity.setDetails(l.details());
            entity.setActivityTimestamp(l.timestamp() != null ? l.timestamp() : Instant.now());
            batch.add(entity);
        }
        if (!batch.isEmpty()) userActivityLogRepository.saveAll(batch);
    }

    private static boolean isOwnedByAnotherUser(UUID existingOwner, UUID currentUserId) {
        return existingOwner != null && !existingOwner.equals(currentUserId);
    }

    private static boolean isPositive(Double value) {
        return value != null && Double.isFinite(value) && value > 0.0;
    }

    private static boolean isNonNegative(Double value) {
        return value != null && Double.isFinite(value) && value >= 0.0;
    }

    private static double safeNonNegative(Double value, double fallback) {
        return (value != null && Double.isFinite(value) && value >= 0.0) ? value : fallback;
    }
}

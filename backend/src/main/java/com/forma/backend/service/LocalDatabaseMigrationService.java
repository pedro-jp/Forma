package com.forma.backend.service;

import com.forma.backend.entity.*;
import com.forma.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Clona automaticamente, uma única vez, todos os dados existentes no banco H2 local
 * (forma_local_db.mv.db) para o banco remoto (PostgreSQL / Supabase) assim que o
 * servidor inicia conectado ao banco remoto.
 */
@Component
public class LocalDatabaseMigrationService implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(LocalDatabaseMigrationService.class);
    private static final String MIGRATION_FLAG_KEY = "LOCAL_DB_CLONED_ONCE";

    private final DataSource dataSource;
    private final AppConfigRepository appConfigRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final FoodEntryRepository foodEntryRepository;
    private final WorkoutEntryRepository workoutEntryRepository;
    private final WaterEntryRepository waterEntryRepository;
    private final WeightEntryRepository weightEntryRepository;
    private final WorkoutRoutineRepository workoutRoutineRepository;
    private final AiAnalysisRepository aiAnalysisRepository;
    private final UserActivityLogRepository userActivityLogRepository;

    public LocalDatabaseMigrationService(
        DataSource dataSource,
        AppConfigRepository appConfigRepository,
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
        this.dataSource = dataSource;
        this.appConfigRepository = appConfigRepository;
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

    @Override
    public void run(ApplicationArguments args) {
        try {
            String primaryJdbcUrl = resolvePrimaryJdbcUrl();
            if (primaryJdbcUrl == null || primaryJdbcUrl.startsWith("jdbc:h2:")) {
                log.debug("[Migration] Banco principal ativo é H2 local; clonagem para servidor remoto aguardando conexão PostgreSQL.");
                return;
            }

            List<Path> localDbFiles = discoverLocalH2Files();
            if (localDbFiles.isEmpty()) {
                log.debug("[Migration] Nenhum arquivo forma_local_db.mv.db encontrado neste host; pulando clonagem local.");
                return;
            }

            Optional<AppConfigEntity> existingFlag = appConfigRepository.findById(MIGRATION_FLAG_KEY);
            if (existingFlag.isPresent() && "true".equalsIgnoreCase(existingFlag.get().getConfigValue())) {
                log.info("[Migration] Clonagem única do banco local já foi realizada anteriormente ({}=true).", MIGRATION_FLAG_KEY);
                return;
            }

            Class.forName("org.h2.Driver");

            int totalMigratedRecords = 0;
            boolean anyFileProcessed = false;

            for (Path mvDbFile : localDbFiles) {
                try {
                    totalMigratedRecords += cloneFromH2File(mvDbFile);
                    anyFileProcessed = true;
                } catch (Exception fileEx) {
                    log.warn("[Migration] Não foi possível clonar o arquivo H2 {}: {}", mvDbFile, fileEx.getMessage());
                }
            }

            if (anyFileProcessed) {
                AppConfigEntity flag = existingFlag.orElseGet(() -> new AppConfigEntity(
                    MIGRATION_FLAG_KEY,
                    "true",
                    "Clonagem automática única do banco H2 local para o servidor remoto concluída"
                ));
                flag.setConfigValue("true");
                appConfigRepository.save(flag);
                log.info("[Migration] Clonagem única do banco local concluída com sucesso ({} registros sincronizados).", totalMigratedRecords);
            }
        } catch (Exception e) {
            log.warn("[Migration] Aviso durante verificação de migração automática do banco local: {}", e.getMessage());
        }
    }

    private String resolvePrimaryJdbcUrl() {
        try (Connection conn = dataSource.getConnection()) {
            return conn.getMetaData().getURL();
        } catch (Exception e) {
            log.warn("[Migration] Não foi possível inspecionar URL do DataSource principal: {}", e.getMessage());
            return null;
        }
    }

    private List<Path> discoverLocalH2Files() {
        List<Path> candidates = List.of(
            Path.of("./forma_local_db.mv.db"),
            Path.of("./backend/forma_local_db.mv.db"),
            Path.of("../forma_local_db.mv.db")
        );

        Map<Path, Path> uniqueByRealPath = new LinkedHashMap<>();
        for (Path candidate : candidates) {
            try {
                if (Files.isRegularFile(candidate) && Files.size(candidate) > 0) {
                    Path real = candidate.toRealPath();
                    uniqueByRealPath.putIfAbsent(real, real);
                }
            } catch (Exception ignored) {
            }
        }
        return new ArrayList<>(uniqueByRealPath.values());
    }

    private int cloneFromH2File(Path mvDbFile) throws SQLException {
        String fullPath = mvDbFile.toAbsolutePath().toString();
        if (!fullPath.endsWith(".mv.db")) {
            return 0;
        }
        String basePath = fullPath.substring(0, fullPath.length() - ".mv.db".length());
        log.info("[Migration] Iniciando clonagem única do banco local H2: {}", fullPath);

        int count = 0;
        try (Connection h2 = openH2Connection(basePath)) {
            Map<UUID, UUID> userIdMap = new HashMap<>();
            Set<UUID> validTargetUsers = new HashSet<>();
            userRepository.findAll().forEach(u -> validTargetUsers.add(u.getId()));

            count += migrateAppConfig(h2);
            count += migrateUsers(h2, userIdMap, validTargetUsers);
            count += migrateUserProfiles(h2, userIdMap, validTargetUsers);
            count += migrateFoods(h2, userIdMap, validTargetUsers);
            count += migrateWorkouts(h2, userIdMap, validTargetUsers);
            count += migrateWaters(h2, userIdMap, validTargetUsers);
            count += migrateWeights(h2, userIdMap, validTargetUsers);
            count += migrateRoutines(h2, userIdMap, validTargetUsers);
            count += migrateAnalyses(h2, userIdMap, validTargetUsers);
            count += migrateActivityLogs(h2, userIdMap, validTargetUsers);
        }
        return count;
    }

    private Connection openH2Connection(String basePath) throws SQLException {
        try {
            return DriverManager.getConnection("jdbc:h2:file:" + basePath + ";IFEXISTS=TRUE;ACCESS_MODE_DATA=r", "sa", "");
        } catch (SQLException readOnlyEx) {
            return DriverManager.getConnection("jdbc:h2:file:" + basePath + ";IFEXISTS=TRUE;AUTO_SERVER=TRUE", "sa", "");
        }
    }

    private int migrateAppConfig(Connection h2) {
        if (!tableExists(h2, "app_config")) return 0;
        int migrated = 0;
        try (Statement st = h2.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM app_config")) {
            Set<String> cols = readColumns(rs);
            while (rs.next()) {
                String id = getString(rs, cols, "id");
                if (id == null || id.isBlank() || MIGRATION_FLAG_KEY.equalsIgnoreCase(id)) continue;
                if (appConfigRepository.existsById(id)) continue;

                AppConfigEntity cfg = new AppConfigEntity();
                cfg.setId(id);
                cfg.setConfigValue(getString(rs, cols, "config_value"));
                cfg.setDescription(getString(rs, cols, "description"));
                Instant updatedAt = getInstant(rs, cols, "updated_at");
                cfg.setUpdatedAt(updatedAt != null ? updatedAt : Instant.now());
                appConfigRepository.save(cfg);
                migrated++;
            }
        } catch (Exception e) {
            log.warn("[Migration] Aviso ao migrar app_config: {}", e.getMessage());
        }
        return migrated;
    }

    private int migrateUsers(Connection h2, Map<UUID, UUID> userIdMap, Set<UUID> validTargetUsers) {
        if (!tableExists(h2, "users")) return 0;
        int migrated = 0;
        try (Statement st = h2.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM users")) {
            Set<String> cols = readColumns(rs);
            while (rs.next()) {
                UUID h2Id = getUUID(rs, cols, "id");
                String email = getString(rs, cols, "email");
                if (h2Id == null || email == null || email.isBlank()) continue;
                String normalizedEmail = email.trim().toLowerCase();

                Optional<User> existing = userRepository.findById(h2Id);
                if (existing.isEmpty()) {
                    existing = userRepository.findByEmail(normalizedEmail);
                }

                if (existing.isPresent()) {
                    User target = existing.get();
                    userIdMap.put(h2Id, target.getId());
                    validTargetUsers.add(target.getId());

                    boolean updated = false;
                    String pwdHash = getString(rs, cols, "password_hash");
                    if ((target.getPasswordHash() == null || target.getPasswordHash().isBlank())
                        && pwdHash != null && !pwdHash.isBlank()) {
                        target.setPasswordHash(pwdHash);
                        updated = true;
                    }
                    String providerId = getString(rs, cols, "provider_id");
                    if ((target.getProviderId() == null || target.getProviderId().isBlank())
                        && providerId != null && !providerId.isBlank()) {
                        target.setProviderId(providerId);
                        updated = true;
                    }
                    Integer dailyLimit = getInteger(rs, cols, "daily_ai_limit");
                    if (target.getDailyAiLimit() == null && dailyLimit != null) {
                        target.setDailyAiLimit(dailyLimit);
                        updated = true;
                    }
                    String tier = getString(rs, cols, "account_tier");
                    if (tier != null && !tier.isBlank() && !"PRO".equalsIgnoreCase(target.getAccountTier()) && "PRO".equalsIgnoreCase(tier.trim())) {
                        target.setAccountTier("PRO");
                        updated = true;
                    }
                    if (updated) {
                        userRepository.save(target);
                    }
                    continue;
                }

                User u = new User();
                u.setId(h2Id);
                u.setEmail(normalizedEmail);
                u.setPasswordHash(getString(rs, cols, "password_hash"));
                u.setName(getStringOrDefault(rs, cols, "name", "Usuário"));
                u.setAuthProvider(getStringOrDefault(rs, cols, "auth_provider", "EMAIL"));
                u.setAccountTier(getStringOrDefault(rs, cols, "account_tier", "FREE"));
                u.setProviderId(getString(rs, cols, "provider_id"));
                Instant createdAt = getInstant(rs, cols, "created_at");
                u.setCreatedAt(createdAt != null ? createdAt : Instant.now());
                u.setLastLoginAt(getInstant(rs, cols, "last_login_at"));
                u.setLastSyncedAt(getInstant(rs, cols, "last_synced_at"));
                u.setStatus(getStringOrDefault(rs, cols, "status", "ACTIVE"));
                u.setPausedReason(getString(rs, cols, "paused_reason"));
                Boolean aiBlocked = getBoolean(rs, cols, "ai_blocked");
                u.setAiBlocked(aiBlocked != null ? aiBlocked : false);
                u.setDailyAiLimit(getInteger(rs, cols, "daily_ai_limit"));
                u.setForceResetAt(getInstant(rs, cols, "force_reset_at"));
                u.setResetCodeHash(getString(rs, cols, "reset_code_hash"));
                u.setResetCodeExpiresAt(getInstant(rs, cols, "reset_code_expires_at"));

                userRepository.save(u);
                userIdMap.put(h2Id, h2Id);
                validTargetUsers.add(h2Id);
                migrated++;
            }
        } catch (Exception e) {
            log.warn("[Migration] Aviso ao migrar users: {}", e.getMessage());
        }
        return migrated;
    }

    private int migrateUserProfiles(Connection h2, Map<UUID, UUID> userIdMap, Set<UUID> validTargetUsers) {
        if (!tableExists(h2, "user_profiles")) return 0;
        int migrated = 0;
        try (Statement st = h2.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM user_profiles")) {
            Set<String> cols = readColumns(rs);
            while (rs.next()) {
                UUID h2UserId = getUUID(rs, cols, "user_id");
                UUID targetUserId = resolveUserId(h2UserId, userIdMap);
                if (targetUserId == null || !validTargetUsers.contains(targetUserId)) continue;

                Optional<UserProfile> existingOpt = userProfileRepository.findById(targetUserId);
                if (existingOpt.isPresent() && !isDefaultUntouchedProfile(existingOpt.get())) {
                    continue;
                }

                UserProfile p = existingOpt.orElseGet(() -> new UserProfile(targetUserId));
                String goal = getString(rs, cols, "goal");
                if (goal != null && !goal.isBlank()) p.setGoal(goal);
                Double calIn = getDouble(rs, cols, "target_calorie_intake");
                if (calIn != null) p.setTargetCalorieIntake(calIn);
                Double calBurn = getDouble(rs, cols, "target_calorie_burn");
                if (calBurn != null) p.setTargetCalorieBurn(calBurn);
                Double steps = getDouble(rs, cols, "target_steps");
                if (steps != null) p.setTargetSteps(steps);
                Double targetWeight = getDouble(rs, cols, "target_weight");
                if (targetWeight != null) p.setTargetWeight(targetWeight);
                Double protein = getDouble(rs, cols, "target_protein");
                if (protein != null) p.setTargetProtein(protein);
                Double carbs = getDouble(rs, cols, "target_carbohydrates");
                if (carbs != null) p.setTargetCarbohydrates(carbs);
                Double fat = getDouble(rs, cols, "target_fat");
                if (fat != null) p.setTargetFat(fat);
                Integer water = getInteger(rs, cols, "target_water_milliliters");
                if (water != null) p.setTargetWaterMilliliters(water);
                Double height = getDouble(rs, cols, "height_meters");
                if (height != null) p.setHeightMeters(height);
                Double currentWeight = getDouble(rs, cols, "current_weight");
                if (currentWeight != null) p.setCurrentWeight(currentWeight);
                Instant updatedAt = getInstant(rs, cols, "updated_at");
                p.setUpdatedAt(updatedAt != null ? updatedAt : Instant.now());

                userProfileRepository.save(p);
                migrated++;
            }
        } catch (Exception e) {
            log.warn("[Migration] Aviso ao migrar user_profiles: {}", e.getMessage());
        }
        return migrated;
    }

    private boolean isDefaultUntouchedProfile(UserProfile p) {
        return "gain".equals(p.getGoal())
            && Objects.equals(p.getTargetCalorieIntake(), 2800.0)
            && Objects.equals(p.getCurrentWeight(), 72.0)
            && Objects.equals(p.getHeightMeters(), 1.78);
    }

    private int migrateFoods(Connection h2, Map<UUID, UUID> userIdMap, Set<UUID> validTargetUsers) {
        if (!tableExists(h2, "food_entries")) return 0;
        List<FoodEntryEntity> batch = new ArrayList<>();
        try (Statement st = h2.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM food_entries")) {
            Set<String> cols = readColumns(rs);
            while (rs.next()) {
                UUID id = getUUID(rs, cols, "id");
                UUID userId = resolveUserId(getUUID(rs, cols, "user_id"), userIdMap);
                if (id == null || userId == null || !validTargetUsers.contains(userId)) continue;
                if (foodEntryRepository.existsById(id)) continue;

                FoodEntryEntity e = new FoodEntryEntity();
                e.setId(id);
                e.setUserId(userId);
                e.setName(getStringOrDefault(rs, cols, "name", "Alimento"));
                e.setBrand(getString(rs, cols, "brand"));
                e.setMealType(getStringOrDefault(rs, cols, "meal_type", "Lanches"));
                e.setPortionGrams(getDoubleOrDefault(rs, cols, "portion_grams", 100.0));
                e.setCalories(getDoubleOrDefault(rs, cols, "calories", 0.0));
                e.setProtein(getDoubleOrDefault(rs, cols, "protein", 0.0));
                e.setCarbohydrates(getDoubleOrDefault(rs, cols, "carbohydrates", 0.0));
                e.setFat(getDoubleOrDefault(rs, cols, "fat", 0.0));
                Instant consumedAt = getInstant(rs, cols, "consumed_at");
                e.setConsumedAt(consumedAt != null ? consumedAt : Instant.now());
                Instant createdAt = getInstant(rs, cols, "created_at");
                e.setCreatedAt(createdAt != null ? createdAt : Instant.now());
                batch.add(e);
            }
            if (!batch.isEmpty()) foodEntryRepository.saveAll(batch);
        } catch (Exception e) {
            log.warn("[Migration] Aviso ao migrar food_entries: {}", e.getMessage());
        }
        return batch.size();
    }

    private int migrateWorkouts(Connection h2, Map<UUID, UUID> userIdMap, Set<UUID> validTargetUsers) {
        if (!tableExists(h2, "workout_entries")) return 0;
        List<WorkoutEntryEntity> batch = new ArrayList<>();
        try (Statement st = h2.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM workout_entries")) {
            Set<String> cols = readColumns(rs);
            while (rs.next()) {
                UUID id = getUUID(rs, cols, "id");
                UUID userId = resolveUserId(getUUID(rs, cols, "user_id"), userIdMap);
                if (id == null || userId == null || !validTargetUsers.contains(userId)) continue;
                if (workoutEntryRepository.existsById(id)) continue;

                WorkoutEntryEntity e = new WorkoutEntryEntity();
                e.setId(id);
                e.setUserId(userId);
                e.setTitle(getStringOrDefault(rs, cols, "title", "Treino"));
                e.setWorkoutType(getStringOrDefault(rs, cols, "workout_type", "Musculação"));
                Integer duration = getInteger(rs, cols, "duration_minutes");
                e.setDurationMinutes(duration != null ? duration : 45);
                e.setCaloriesBurned(getDoubleOrDefault(rs, cols, "calories_burned", 300.0));
                e.setNotes(getString(rs, cols, "notes"));
                Instant workoutDate = getInstant(rs, cols, "workout_date");
                e.setWorkoutDate(workoutDate != null ? workoutDate : Instant.now());
                Instant createdAt = getInstant(rs, cols, "created_at");
                e.setCreatedAt(createdAt != null ? createdAt : Instant.now());
                batch.add(e);
            }
            if (!batch.isEmpty()) workoutEntryRepository.saveAll(batch);
        } catch (Exception e) {
            log.warn("[Migration] Aviso ao migrar workout_entries: {}", e.getMessage());
        }
        return batch.size();
    }

    private int migrateWaters(Connection h2, Map<UUID, UUID> userIdMap, Set<UUID> validTargetUsers) {
        if (!tableExists(h2, "water_entries")) return 0;
        List<WaterEntryEntity> batch = new ArrayList<>();
        try (Statement st = h2.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM water_entries")) {
            Set<String> cols = readColumns(rs);
            while (rs.next()) {
                UUID id = getUUID(rs, cols, "id");
                UUID userId = resolveUserId(getUUID(rs, cols, "user_id"), userIdMap);
                if (id == null || userId == null || !validTargetUsers.contains(userId)) continue;
                if (waterEntryRepository.existsById(id)) continue;

                WaterEntryEntity e = new WaterEntryEntity();
                e.setId(id);
                e.setUserId(userId);
                Integer ml = getInteger(rs, cols, "milliliters");
                e.setMilliliters(ml != null ? ml : 250);
                Instant entryDate = getInstant(rs, cols, "entry_date");
                e.setEntryDate(entryDate != null ? entryDate : Instant.now());
                Instant createdAt = getInstant(rs, cols, "created_at");
                e.setCreatedAt(createdAt != null ? createdAt : Instant.now());
                batch.add(e);
            }
            if (!batch.isEmpty()) waterEntryRepository.saveAll(batch);
        } catch (Exception e) {
            log.warn("[Migration] Aviso ao migrar water_entries: {}", e.getMessage());
        }
        return batch.size();
    }

    private int migrateWeights(Connection h2, Map<UUID, UUID> userIdMap, Set<UUID> validTargetUsers) {
        if (!tableExists(h2, "weight_entries")) return 0;
        List<WeightEntryEntity> batch = new ArrayList<>();
        try (Statement st = h2.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM weight_entries")) {
            Set<String> cols = readColumns(rs);
            while (rs.next()) {
                UUID id = getUUID(rs, cols, "id");
                UUID userId = resolveUserId(getUUID(rs, cols, "user_id"), userIdMap);
                if (id == null || userId == null || !validTargetUsers.contains(userId)) continue;
                if (weightEntryRepository.existsById(id)) continue;

                WeightEntryEntity e = new WeightEntryEntity();
                e.setId(id);
                e.setUserId(userId);
                e.setWeight(getDoubleOrDefault(rs, cols, "weight", 70.0));
                Instant entryDate = getInstant(rs, cols, "entry_date");
                e.setEntryDate(entryDate != null ? entryDate : Instant.now());
                Instant createdAt = getInstant(rs, cols, "created_at");
                e.setCreatedAt(createdAt != null ? createdAt : Instant.now());
                batch.add(e);
            }
            if (!batch.isEmpty()) weightEntryRepository.saveAll(batch);
        } catch (Exception e) {
            log.warn("[Migration] Aviso ao migrar weight_entries: {}", e.getMessage());
        }
        return batch.size();
    }

    private int migrateRoutines(Connection h2, Map<UUID, UUID> userIdMap, Set<UUID> validTargetUsers) {
        if (!tableExists(h2, "workout_routines")) return 0;
        List<WorkoutRoutineEntity> batch = new ArrayList<>();
        try (Statement st = h2.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM workout_routines")) {
            Set<String> cols = readColumns(rs);
            while (rs.next()) {
                UUID id = getUUID(rs, cols, "id");
                UUID userId = resolveUserId(getUUID(rs, cols, "user_id"), userIdMap);
                if (id == null || userId == null || !validTargetUsers.contains(userId)) continue;
                if (workoutRoutineRepository.existsById(id)) continue;

                WorkoutRoutineEntity e = new WorkoutRoutineEntity();
                e.setId(id);
                e.setUserId(userId);
                e.setTitle(getStringOrDefault(rs, cols, "title", "Ficha de Treino"));
                e.setWorkoutType(getStringOrDefault(rs, cols, "workout_type", "Musculação"));
                e.setTargetMuscles(getString(rs, cols, "target_muscles"));
                e.setHypertrophyFocus(getString(rs, cols, "hypertrophy_focus"));
                Integer estDur = getInteger(rs, cols, "estimated_duration_minutes");
                e.setEstimatedDurationMinutes(estDur != null ? estDur : 50);
                e.setEstimatedCalories(getDoubleOrDefault(rs, cols, "estimated_calories", 350.0));
                Integer restSec = getInteger(rs, cols, "recommended_rest_seconds");
                e.setRecommendedRestSeconds(restSec != null ? restSec : 90);
                Boolean fav = getBoolean(rs, cols, "is_favorite");
                e.setIsFavorite(fav != null ? fav : false);
                e.setExercisesJson(getStringOrDefault(rs, cols, "exercises_json", "[]"));
                e.setNotes(getString(rs, cols, "notes"));
                Instant createdAt = getInstant(rs, cols, "created_at");
                e.setCreatedAt(createdAt != null ? createdAt : Instant.now());
                e.setLastTrainedAt(getInstant(rs, cols, "last_trained_at"));
                batch.add(e);
            }
            if (!batch.isEmpty()) workoutRoutineRepository.saveAll(batch);
        } catch (Exception e) {
            log.warn("[Migration] Aviso ao migrar workout_routines: {}", e.getMessage());
        }
        return batch.size();
    }

    private int migrateAnalyses(Connection h2, Map<UUID, UUID> userIdMap, Set<UUID> validTargetUsers) {
        if (!tableExists(h2, "ai_analyses")) return 0;
        List<AiAnalysisEntity> batch = new ArrayList<>();
        try (Statement st = h2.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM ai_analyses")) {
            Set<String> cols = readColumns(rs);
            while (rs.next()) {
                UUID id = getUUID(rs, cols, "id");
                UUID userId = resolveUserId(getUUID(rs, cols, "user_id"), userIdMap);
                if (id == null || userId == null || !validTargetUsers.contains(userId)) continue;
                if (aiAnalysisRepository.existsById(id)) continue;

                AiAnalysisEntity e = new AiAnalysisEntity();
                e.setId(id);
                e.setUserId(userId);
                e.setStatusSummary(getString(rs, cols, "status_summary"));
                e.setCalorieDiagnostic(getString(rs, cols, "calorie_diagnostic"));
                e.setMacroDiagnostic(getString(rs, cols, "macro_diagnostic"));
                e.setHydrationDiagnostic(getString(rs, cols, "hydration_diagnostic"));
                e.setWorkoutSuggestionsJson(getString(rs, cols, "workout_suggestions_json"));
                e.setNutritionSuggestionsJson(getString(rs, cols, "nutrition_suggestions_json"));
                e.setWeeklyActionPlan(getString(rs, cols, "weekly_action_plan"));
                e.setSuggestedTargetsJson(getString(rs, cols, "suggested_targets_json"));
                e.setRecommendedRoutineJson(getString(rs, cols, "recommended_routine_json"));
                Instant createdAt = getInstant(rs, cols, "created_at");
                e.setCreatedAt(createdAt != null ? createdAt : Instant.now());
                batch.add(e);
            }
            if (!batch.isEmpty()) aiAnalysisRepository.saveAll(batch);
        } catch (Exception e) {
            log.warn("[Migration] Aviso ao migrar ai_analyses: {}", e.getMessage());
        }
        return batch.size();
    }

    private int migrateActivityLogs(Connection h2, Map<UUID, UUID> userIdMap, Set<UUID> validTargetUsers) {
        if (!tableExists(h2, "user_activity_logs")) return 0;
        List<UserActivityLogEntity> batch = new ArrayList<>();
        try (Statement st = h2.createStatement();
             ResultSet rs = st.executeQuery("SELECT * FROM user_activity_logs")) {
            Set<String> cols = readColumns(rs);
            while (rs.next()) {
                String id = getString(rs, cols, "id");
                UUID userId = resolveUserId(getUUID(rs, cols, "user_id"), userIdMap);
                if (id == null || id.isBlank() || userId == null || !validTargetUsers.contains(userId)) continue;
                if (userActivityLogRepository.existsById(id)) continue;

                UserActivityLogEntity e = new UserActivityLogEntity();
                e.setId(id);
                e.setUserId(userId);
                e.setCategory(getStringOrDefault(rs, cols, "category", "activity"));
                e.setTitle(getStringOrDefault(rs, cols, "title", ""));
                Boolean snack = getBoolean(rs, cols, "is_snack");
                e.setIsSnack(snack != null ? snack : false);
                e.setCaloriesChange(getDoubleOrDefault(rs, cols, "calories_change", 0.0));
                e.setProteinGrams(getDouble(rs, cols, "protein_grams"));
                e.setCarbsGrams(getDouble(rs, cols, "carbs_grams"));
                e.setFatGrams(getDouble(rs, cols, "fat_grams"));
                e.setWaterMilliliters(getInteger(rs, cols, "water_milliliters"));
                e.setWeightKg(getDouble(rs, cols, "weight_kg"));
                e.setDetails(getString(rs, cols, "details"));
                Instant ts = getInstant(rs, cols, "activity_timestamp");
                e.setActivityTimestamp(ts != null ? ts : Instant.now());
                Instant createdAt = getInstant(rs, cols, "created_at");
                e.setCreatedAt(createdAt != null ? createdAt : Instant.now());
                batch.add(e);
            }
            if (!batch.isEmpty()) userActivityLogRepository.saveAll(batch);
        } catch (Exception e) {
            log.warn("[Migration] Aviso ao migrar user_activity_logs: {}", e.getMessage());
        }
        return batch.size();
    }

    private static UUID resolveUserId(UUID h2UserId, Map<UUID, UUID> userIdMap) {
        if (h2UserId == null) return null;
        return userIdMap.getOrDefault(h2UserId, h2UserId);
    }

    private static boolean tableExists(Connection conn, String tableName) {
        try (ResultSet rs = conn.getMetaData().getTables(null, null, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String found = rs.getString("TABLE_NAME");
                if (found != null && found.equalsIgnoreCase(tableName)) {
                    return true;
                }
            }
        } catch (SQLException ignored) {
        }
        return false;
    }

    private static Set<String> readColumns(ResultSet rs) throws SQLException {
        ResultSetMetaData meta = rs.getMetaData();
        Set<String> cols = new HashSet<>();
        for (int i = 1; i <= meta.getColumnCount(); i++) {
            String label = meta.getColumnLabel(i);
            if (label != null) cols.add(label.toLowerCase(Locale.ROOT));
            String name = meta.getColumnName(i);
            if (name != null) cols.add(name.toLowerCase(Locale.ROOT));
        }
        return cols;
    }

    private static String getString(ResultSet rs, Set<String> cols, String col) throws SQLException {
        if (!cols.contains(col)) return null;
        return rs.getString(col);
    }

    private static String getStringOrDefault(ResultSet rs, Set<String> cols, String col, String fallback) throws SQLException {
        String val = getString(rs, cols, col);
        return (val != null && !val.isBlank()) ? val : fallback;
    }

    private static UUID getUUID(ResultSet rs, Set<String> cols, String col) throws SQLException {
        if (!cols.contains(col)) return null;
        Object raw = rs.getObject(col);
        if (raw == null) return null;
        if (raw instanceof UUID u) return u;
        if (raw instanceof byte[] bytes && bytes.length == 16) {
            long msb = 0;
            long lsb = 0;
            for (int i = 0; i < 8; i++) msb = (msb << 8) | (bytes[i] & 0xff);
            for (int i = 8; i < 16; i++) lsb = (lsb << 8) | (bytes[i] & 0xff);
            return new UUID(msb, lsb);
        }
        return UUID.fromString(raw.toString().trim());
    }

    private static Double getDouble(ResultSet rs, Set<String> cols, String col) throws SQLException {
        if (!cols.contains(col)) return null;
        Object raw = rs.getObject(col);
        if (raw == null) return null;
        if (raw instanceof Number n) return n.doubleValue();
        return Double.parseDouble(raw.toString());
    }

    private static Double getDoubleOrDefault(ResultSet rs, Set<String> cols, String col, double fallback) throws SQLException {
        Double val = getDouble(rs, cols, col);
        return val != null ? val : fallback;
    }

    private static Integer getInteger(ResultSet rs, Set<String> cols, String col) throws SQLException {
        if (!cols.contains(col)) return null;
        Object raw = rs.getObject(col);
        if (raw == null) return null;
        if (raw instanceof Number n) return n.intValue();
        return Integer.parseInt(raw.toString());
    }

    private static Boolean getBoolean(ResultSet rs, Set<String> cols, String col) throws SQLException {
        if (!cols.contains(col)) return null;
        Object raw = rs.getObject(col);
        if (raw == null) return null;
        if (raw instanceof Boolean b) return b;
        return Boolean.parseBoolean(raw.toString());
    }

    private static Instant getInstant(ResultSet rs, Set<String> cols, String col) throws SQLException {
        if (!cols.contains(col)) return null;
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toInstant() : null;
    }
}

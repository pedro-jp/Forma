package com.forma.backend.controller;

import com.forma.backend.dto.SyncDto;
import com.forma.backend.entity.User;
import com.forma.backend.repository.FoodEntryRepository;
import com.forma.backend.repository.WorkoutEntryRepository;
import com.forma.backend.security.AuthContextResolver;
import com.forma.backend.service.SyncService;
import com.forma.backend.websocket.AdminEventPublisherService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/sync")
public class SyncController {

    private final SyncService syncService;
    private final AuthContextResolver authContextResolver;
    private final FoodEntryRepository foodEntryRepository;
    private final WorkoutEntryRepository workoutEntryRepository;
    private final AdminEventPublisherService adminEventPublisherService;

    public SyncController(
        SyncService syncService,
        AuthContextResolver authContextResolver,
        FoodEntryRepository foodEntryRepository,
        WorkoutEntryRepository workoutEntryRepository,
        AdminEventPublisherService adminEventPublisherService
    ) {
        this.syncService = syncService;
        this.authContextResolver = authContextResolver;
        this.foodEntryRepository = foodEntryRepository;
        this.workoutEntryRepository = workoutEntryRepository;
        this.adminEventPublisherService = adminEventPublisherService;
    }

    @PostMapping("/push")
    public ResponseEntity<?> push(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody SyncDto.SyncPushRequest request
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);

        if (user.getForceResetAt() != null) {
            long resetEpoch = user.getForceResetAt().toEpochMilli();
            long clientAck = (request != null && request.clientResetAckEpochMs() != null)
                ? request.clientResetAckEpochMs()
                : 0L;
            if (clientAck < resetEpoch) {
                return ResponseEntity.ok(Map.of(
                    "status", "reset_required",
                    "forceResetEpochMs", resetEpoch,
                    "message", "Conta zerada pelo administrador. Limpando dados locais..."
                ));
            }
        }

        syncService.pushData(user.getId(), request);
        int count = (request != null && request.foods() != null ? request.foods().size() : 0)
            + (request != null && request.workouts() != null ? request.workouts().size() : 0);
        publishAdminSync("DATA_PUSH", count);
        return ResponseEntity.ok(Map.of(
            "status", "synced",
            "message", "Dados sincronizados com sucesso no Forma!"
        ));
    }

    @GetMapping("/pull")
    public ResponseEntity<SyncDto.SyncPullResponse> pull(
        @RequestHeader(value = "Authorization", required = false) String authHeader
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        return ResponseEntity.ok(syncService.pullData(user.getId()));
    }

    @PutMapping("/profile")
    public ResponseEntity<Map<String, Object>> saveProfile(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody SyncDto.ProfileWriteRequest request
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        Map<String, Object> result = syncService.saveProfileDirect(user.getId(), request);
        publishAdminSync("PROFILE_UPDATED", 1);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/foods")
    public ResponseEntity<Map<String, Object>> saveFoods(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody SyncDto.FoodBatchWriteRequest request
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        List<SyncDto.FoodDto> saved = syncService.saveFoodsDirect(user.getId(), request);
        publishAdminSync("FOOD_CREATED", saved.size());
        return ResponseEntity.ok(Map.of("status", "saved", "foods", saved));
    }

    @DeleteMapping("/foods/{id}")
    public ResponseEntity<Map<String, Object>> deleteFood(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @PathVariable UUID id
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        syncService.deleteFoodDirect(user.getId(), id);
        publishAdminSync("FOOD_DELETED", 1);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PostMapping("/foods/delete-batch")
    public ResponseEntity<Map<String, Object>> deleteFoodsBatch(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody SyncDto.BatchDeleteRequest request
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        List<UUID> ids = request != null && request.ids() != null ? request.ids() : List.of();
        syncService.deleteFoodsBatchDirect(user.getId(), ids);
        publishAdminSync("FOOD_BATCH_DELETED", ids.size());
        return ResponseEntity.ok(Map.of("status", "deleted", "count", ids.size()));
    }

    @PostMapping("/workouts")
    public ResponseEntity<Map<String, Object>> saveWorkout(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody SyncDto.WorkoutWriteRequest request
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        SyncDto.WorkoutDto saved = syncService.saveWorkoutDirect(user.getId(), request);
        publishAdminSync("WORKOUT_CREATED", 1);
        return ResponseEntity.ok(Map.of("status", "saved", "workout", saved));
    }

    @DeleteMapping("/workouts/{id}")
    public ResponseEntity<Map<String, Object>> deleteWorkout(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @PathVariable UUID id
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        syncService.deleteWorkoutDirect(user.getId(), id);
        publishAdminSync("WORKOUT_DELETED", 1);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PostMapping("/routines")
    public ResponseEntity<Map<String, Object>> saveRoutine(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody SyncDto.RoutineDto request
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        SyncDto.RoutineDto saved = syncService.saveRoutineDirect(user.getId(), request);
        publishAdminSync("ROUTINE_SAVED", 1);
        return ResponseEntity.ok(Map.of("status", "saved", "routine", saved));
    }

    @DeleteMapping("/routines/{id}")
    public ResponseEntity<Map<String, Object>> deleteRoutine(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @PathVariable UUID id
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        syncService.deleteRoutineDirect(user.getId(), id);
        publishAdminSync("ROUTINE_DELETED", 1);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PostMapping("/waters")
    public ResponseEntity<Map<String, Object>> saveWater(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody SyncDto.WaterWriteRequest request
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        SyncDto.WaterDto saved = syncService.saveWaterDirect(user.getId(), request);
        publishAdminSync("WATER_SAVED", 1);
        return ResponseEntity.ok(Map.of("status", "saved", "water", saved));
    }

    @DeleteMapping("/waters/{id}")
    public ResponseEntity<Map<String, Object>> deleteWater(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @PathVariable UUID id
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        syncService.deleteWaterDirect(user.getId(), id);
        publishAdminSync("WATER_DELETED", 1);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PostMapping("/weights")
    public ResponseEntity<Map<String, Object>> saveWeight(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody SyncDto.WeightWriteRequest request
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        SyncDto.WeightDto saved = syncService.saveWeightDirect(user.getId(), request);
        publishAdminSync("WEIGHT_SAVED", 1);
        return ResponseEntity.ok(Map.of("status", "saved", "weight", saved));
    }

    @DeleteMapping("/weights/{id}")
    public ResponseEntity<Map<String, Object>> deleteWeight(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @PathVariable UUID id
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        syncService.deleteWeightDirect(user.getId(), id);
        publishAdminSync("WEIGHT_DELETED", 1);
        return ResponseEntity.ok(Map.of("status", "deleted"));
    }

    @PostMapping("/analyses")
    public ResponseEntity<Map<String, Object>> saveAnalysis(
        @RequestHeader(value = "Authorization", required = false) String authHeader,
        @RequestBody SyncDto.AiAnalysisDto request
    ) {
        User user = authContextResolver.requireActiveUser(authHeader);
        SyncDto.AiAnalysisDto saved = syncService.saveAnalysisDirect(user.getId(), request);
        publishAdminSync("ANALYSIS_SAVED", 1);
        return ResponseEntity.ok(Map.of("status", "saved", "analysis", saved));
    }

    private void publishAdminSync(String action, int count) {
        adminEventPublisherService.publishSyncActivity(
            action,
            count,
            foodEntryRepository.count(),
            workoutEntryRepository.count()
        );
    }
}

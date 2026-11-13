package com.forma.backend.dto;

import com.forma.backend.entity.*;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class SyncDto {

    public record ProfileDto(
        String goal,
        Double targetCalorieIntake,
        Double targetCalorieBurn,
        Double targetSteps,
        Double targetWeight,
        Double targetProtein,
        Double targetCarbohydrates,
        Double targetFat,
        Integer targetWaterMilliliters,
        Double heightMeters,
        Double currentWeight,
        Boolean onboardingCompleted
    ) {
        public ProfileDto(
            String goal,
            Double targetCalorieIntake,
            Double targetCalorieBurn,
            Double targetSteps,
            Double targetWeight,
            Double targetProtein,
            Double targetCarbohydrates,
            Double targetFat,
            Integer targetWaterMilliliters,
            Double heightMeters,
            Double currentWeight
        ) {
            this(
                goal,
                targetCalorieIntake,
                targetCalorieBurn,
                targetSteps,
                targetWeight,
                targetProtein,
                targetCarbohydrates,
                targetFat,
                targetWaterMilliliters,
                heightMeters,
                currentWeight,
                null
            );
        }

        public static ProfileDto fromEntity(UserProfile profile) {
            return new ProfileDto(
                profile.getGoal(),
                profile.getTargetCalorieIntake(),
                profile.getTargetCalorieBurn(),
                profile.getTargetSteps(),
                profile.getTargetWeight(),
                profile.getTargetProtein(),
                profile.getTargetCarbohydrates(),
                profile.getTargetFat(),
                profile.getTargetWaterMilliliters(),
                profile.getHeightMeters(),
                profile.getCurrentWeight(),
                profile.getOnboardingCompleted()
            );
        }
    }

    public record ProfileWriteRequest(
        String name,
        String goal,
        Double targetCalorieIntake,
        Double targetCalorieBurn,
        Double targetSteps,
        Double targetWeight,
        Double targetProtein,
        Double targetCarbohydrates,
        Double targetFat,
        Integer targetWaterMilliliters,
        Double heightMeters,
        Double currentWeight,
        Boolean onboardingCompleted
    ) {}

    public record FoodDto(
        UUID id,
        String name,
        String brand,
        String mealType,
        Double portionGrams,
        Double calories,
        Double protein,
        Double carbohydrates,
        Double fat,
        Instant consumedAt
    ) {
        public static FoodDto fromEntity(FoodEntryEntity f) {
            return new FoodDto(
                f.getId(),
                f.getName(),
                f.getBrand(),
                f.getMealType(),
                f.getPortionGrams(),
                f.getCalories(),
                f.getProtein(),
                f.getCarbohydrates(),
                f.getFat(),
                f.getConsumedAt()
            );
        }
    }

    public record FoodBatchWriteRequest(
        List<FoodDto> foods,
        List<ActivityLogDto> activityLogs
    ) {}

    public record BatchDeleteRequest(
        List<UUID> ids
    ) {}

    public record WorkoutDto(
        UUID id,
        String title,
        String workoutType,
        Integer durationMinutes,
        Double caloriesBurned,
        String notes,
        Instant workoutDate
    ) {
        public static WorkoutDto fromEntity(WorkoutEntryEntity w) {
            return new WorkoutDto(
                w.getId(),
                w.getTitle(),
                w.getWorkoutType(),
                w.getDurationMinutes(),
                w.getCaloriesBurned(),
                w.getNotes(),
                w.getWorkoutDate()
            );
        }
    }

    public record WorkoutWriteRequest(
        WorkoutDto workout,
        ActivityLogDto activityLog,
        RoutineDto updatedRoutine
    ) {}

    public record WaterDto(
        UUID id,
        Integer milliliters,
        Instant entryDate
    ) {
        public static WaterDto fromEntity(WaterEntryEntity wt) {
            return new WaterDto(wt.getId(), wt.getMilliliters(), wt.getEntryDate());
        }
    }

    public record WaterWriteRequest(
        WaterDto water,
        ActivityLogDto activityLog
    ) {}

    public record WeightDto(
        UUID id,
        Double weight,
        Instant entryDate
    ) {
        public static WeightDto fromEntity(WeightEntryEntity wg) {
            return new WeightDto(wg.getId(), wg.getWeight(), wg.getEntryDate());
        }
    }

    public record WeightWriteRequest(
        WeightDto weight,
        ActivityLogDto activityLog,
        ProfileWriteRequest profileUpdate
    ) {}

    public record RoutineDto(
        UUID id,
        String title,
        String workoutType,
        String targetMuscles,
        String hypertrophyFocus,
        Integer estimatedDurationMinutes,
        Double estimatedCalories,
        Integer recommendedRestSeconds,
        Boolean isFavorite,
        String exercisesJson,
        String notes,
        Instant createdAt,
        Instant lastTrainedAt
    ) {
        public static RoutineDto fromEntity(WorkoutRoutineEntity r) {
            return new RoutineDto(
                r.getId(),
                r.getTitle(),
                r.getWorkoutType(),
                r.getTargetMuscles(),
                r.getHypertrophyFocus(),
                r.getEstimatedDurationMinutes(),
                r.getEstimatedCalories(),
                r.getRecommendedRestSeconds(),
                r.getIsFavorite(),
                r.getExercisesJson(),
                r.getNotes(),
                r.getCreatedAt(),
                r.getLastTrainedAt()
            );
        }
    }

    public record AiAnalysisDto(
        UUID id,
        String statusSummary,
        String calorieDiagnostic,
        String macroDiagnostic,
        String hydrationDiagnostic,
        String workoutSuggestionsJson,
        String nutritionSuggestionsJson,
        String weeklyActionPlan,
        String suggestedTargetsJson,
        String recommendedRoutineJson,
        Instant createdAt
    ) {
        public static AiAnalysisDto fromEntity(AiAnalysisEntity a) {
            return new AiAnalysisDto(
                a.getId(),
                a.getStatusSummary(),
                a.getCalorieDiagnostic(),
                a.getMacroDiagnostic(),
                a.getHydrationDiagnostic(),
                a.getWorkoutSuggestionsJson(),
                a.getNutritionSuggestionsJson(),
                a.getWeeklyActionPlan(),
                a.getSuggestedTargetsJson(),
                a.getRecommendedRoutineJson(),
                a.getCreatedAt()
            );
        }
    }

    public record ActivityLogDto(
        String id,
        String category,
        String title,
        Boolean isSnack,
        Double caloriesChange,
        Double proteinGrams,
        Double carbsGrams,
        Double fatGrams,
        Integer waterMilliliters,
        Double weightKg,
        String details,
        Instant timestamp
    ) {
        public static ActivityLogDto fromEntity(UserActivityLogEntity l) {
            return new ActivityLogDto(
                l.getId(),
                l.getCategory(),
                l.getTitle(),
                l.getIsSnack(),
                l.getCaloriesChange(),
                l.getProteinGrams(),
                l.getCarbsGrams(),
                l.getFatGrams(),
                l.getWaterMilliliters(),
                l.getWeightKg(),
                l.getDetails(),
                l.getActivityTimestamp()
            );
        }
    }

    public record SyncPushRequest(
        ProfileDto profile,
        List<FoodDto> foods,
        List<WorkoutDto> workouts,
        List<WaterDto> waters,
        List<WeightDto> weights,
        List<RoutineDto> routines,
        List<AiAnalysisDto> analyses,
        List<ActivityLogDto> activityLogs,
        Long clientResetAckEpochMs,
        List<UUID> deletedFoodIds,
        List<UUID> deletedWorkoutIds,
        List<UUID> deletedWaterIds,
        List<UUID> deletedWeightIds,
        List<UUID> deletedRoutineIds
    ) {
        public SyncPushRequest(
            ProfileDto profile,
            List<FoodDto> foods,
            List<WorkoutDto> workouts,
            List<WaterDto> waters,
            List<WeightDto> weights,
            List<RoutineDto> routines,
            List<AiAnalysisDto> analyses,
            List<ActivityLogDto> activityLogs,
            Long clientResetAckEpochMs
        ) {
            this(
                profile,
                foods,
                workouts,
                waters,
                weights,
                routines,
                analyses,
                activityLogs,
                clientResetAckEpochMs,
                List.of(),
                List.of(),
                List.of(),
                List.of(),
                List.of()
            );
        }
    }

    public record SyncPullResponse(
        ProfileDto profile,
        List<FoodDto> foods,
        List<WorkoutDto> workouts,
        List<WaterDto> waters,
        List<WeightDto> weights,
        List<RoutineDto> routines,
        List<AiAnalysisDto> analyses,
        List<ActivityLogDto> activityLogs,
        Instant syncedAt,
        Long forceResetEpochMs,
        String accountTier
    ) {
        public SyncPullResponse(
            ProfileDto profile,
            List<FoodDto> foods,
            List<WorkoutDto> workouts,
            List<WaterDto> waters,
            List<WeightDto> weights,
            List<RoutineDto> routines,
            List<AiAnalysisDto> analyses,
            List<ActivityLogDto> activityLogs,
            Instant syncedAt,
            Long forceResetEpochMs
        ) {
            this(
                profile,
                foods,
                workouts,
                waters,
                weights,
                routines,
                analyses,
                activityLogs,
                syncedAt,
                forceResetEpochMs,
                "FREE"
            );
        }
    }
}

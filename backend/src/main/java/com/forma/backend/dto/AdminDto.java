package com.forma.backend.dto;

import com.forma.backend.entity.AiRequestLogEntity;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AdminDto {

    public record AdminOverviewResponse(
        long totalUsers,
        long totalFoods,
        long totalWorkouts,
        long totalAiRequests,
        String activeApiKeyMasked,
        boolean hasValidApiKey,
        String geminiModel,
        boolean aiBlocked,
        List<UserSummaryDto> recentUsers,
        List<AiLogSummaryDto> recentLogs
    ) {}

    public record UpdateKeyRequest(
        String apiKey,
        String model,
        String adminKey
    ) {}

    public record UserSummaryDto(
        UUID id,
        String email,
        String name,
        String authProvider,
        String status,
        String pausedReason,
        boolean aiBlocked,
        Integer dailyAiLimit,
        long todayAiRequests,
        long totalAiRequests,
        long foodsCount,
        long workoutsCount,
        long routinesCount,
        long watersCount,
        long weightsCount,
        long analysesCount,
        String goal,
        Double currentWeight,
        Double targetWeight,
        Double targetCalorieIntake,
        Instant createdAt,
        Instant lastLoginAt,
        Instant lastSyncedAt,
        Instant forceResetAt,
        String accountTier
    ) {
        public UserSummaryDto(
            UUID id,
            String email,
            String name,
            String authProvider,
            String status,
            String pausedReason,
            boolean aiBlocked,
            Integer dailyAiLimit,
            long todayAiRequests,
            long totalAiRequests,
            long foodsCount,
            long workoutsCount,
            long routinesCount,
            long watersCount,
            long weightsCount,
            long analysesCount,
            String goal,
            Double currentWeight,
            Double targetWeight,
            Double targetCalorieIntake,
            Instant createdAt,
            Instant lastLoginAt,
            Instant lastSyncedAt,
            Instant forceResetAt
        ) {
            this(
                id,
                email,
                name,
                authProvider,
                status,
                pausedReason,
                aiBlocked,
                dailyAiLimit,
                todayAiRequests,
                totalAiRequests,
                foodsCount,
                workoutsCount,
                routinesCount,
                watersCount,
                weightsCount,
                analysesCount,
                goal,
                currentWeight,
                targetWeight,
                targetCalorieIntake,
                createdAt,
                lastLoginAt,
                lastSyncedAt,
                forceResetAt,
                "FREE"
            );
        }
    }

    public record UserDetailDto(
        UserSummaryDto user,
        SyncDto.ProfileDto profile,
        List<SyncDto.FoodDto> recentFoods,
        List<SyncDto.WorkoutDto> recentWorkouts,
        List<AiLogSummaryDto> recentAiLogs
    ) {}

    public record CreateUserRequest(
        String name,
        String email,
        String password,
        Integer dailyAiLimit,
        String accountTier
    ) {
        public CreateUserRequest(
            String name,
            String email,
            String password,
            Integer dailyAiLimit
        ) {
            this(name, email, password, dailyAiLimit, "FREE");
        }
    }

    public record UpdateUserRequest(
        String name,
        String email,
        String newPassword,
        String goal,
        Double currentWeight,
        Double targetWeight,
        Double heightMeters,
        Double targetCalorieIntake,
        Double targetCalorieBurn,
        Integer targetWaterMilliliters,
        String accountTier
    ) {
        public UpdateUserRequest(
            String name,
            String email,
            String newPassword,
            String goal,
            Double currentWeight,
            Double targetWeight,
            Double heightMeters,
            Double targetCalorieIntake,
            Double targetCalorieBurn,
            Integer targetWaterMilliliters
        ) {
            this(
                name,
                email,
                newPassword,
                goal,
                currentWeight,
                targetWeight,
                heightMeters,
                targetCalorieIntake,
                targetCalorieBurn,
                targetWaterMilliliters,
                null
            );
        }
    }

    public record UserTierRequest(
        String accountTier
    ) {}

    public record UserStatusRequest(
        Boolean paused,
        String reason
    ) {}

    public record UserAiAccessRequest(
        Boolean aiBlocked,
        Integer dailyAiLimit
    ) {}

    public record ResetUserRequest(
        Boolean clearMealsAndWorkouts,
        Boolean clearHydrationAndWeights,
        Boolean clearRoutines,
        Boolean clearAnalysesAndLogs,
        Boolean resetProfileGoals
    ) {}

    public record AiLogSummaryDto(
        UUID id,
        UUID userId,
        String endpoint,
        String promptSummary,
        String status,
        Long durationMs,
        Instant createdAt
    ) {
        public static AiLogSummaryDto fromEntity(AiRequestLogEntity l) {
            return new AiLogSummaryDto(
                l.getId(),
                l.getUserId(),
                l.getEndpoint(),
                l.getPromptSummary(),
                l.getStatus(),
                l.getDurationMs(),
                l.getCreatedAt()
            );
        }
    }

    public record GeminiModelItemDto(
        String id,
        String displayName,
        String description,
        boolean isCurrent
    ) {}

    public record GeminiModelsResponse(
        String currentModel,
        List<GeminiModelItemDto> models
    ) {}
}

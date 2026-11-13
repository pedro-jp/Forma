//
//  LiveActivityManager.swift
//  Forma
//
//  Created by João Pedro on 10/29/26.
//

import Foundation
import ActivityKit
import SwiftUI
import Combine

/// Gerenciador do ciclo de vida das Live Activities na Ilha Dinâmica e Tela Bloqueada
@MainActor
public final class LiveActivityManager: ObservableObject {
    public static let shared = LiveActivityManager()
    
    @Published public var isActivityActive: Bool = false
    @Published public var activityId: String? = nil
    
    private init() {
        // Encerra imediatamente qualquer atividade residual de calorias para liberar a Ilha Dinâmica
        Task { @MainActor in
            for activity in Activity<CalorieActivityAttributes>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
        checkActiveActivity()
    }
    
    public func checkActiveActivity() {
        let active = Activity<WorkoutActivityAttributes>.activities.first
        self.isActivityActive = active != nil
        self.activityId = active?.id
    }
    
    /// Desativado: A Ilha Dinâmica é de uso exclusivo dos Treinos e Exercícios ao Vivo
    public func startActivity(
        userName: String = "Você",
        consumed: Double,
        target: Double,
        burned: Double,
        protein: Double,
        proteinTarget: Double,
        carbs: Double,
        carbsTarget: Double,
        fat: Double,
        fatTarget: Double,
        goal: String,
        lastFoodName: String? = nil,
        lastFoodCalories: Double? = nil
    ) {
        Task { @MainActor in
            for activity in Activity<CalorieActivityAttributes>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
    }
    
    /// Desativado: Encerra qualquer atividade residual de calorias para liberar a Ilha
    public func updateActivity(
        activity: Activity<CalorieActivityAttributes>? = nil,
        consumed: Double,
        target: Double,
        burned: Double,
        protein: Double,
        proteinTarget: Double,
        carbs: Double,
        carbsTarget: Double,
        fat: Double,
        fatTarget: Double,
        goal: String,
        lastFoodName: String? = nil,
        lastFoodCalories: Double? = nil
    ) {
        Task { @MainActor in
            for act in Activity<CalorieActivityAttributes>.activities {
                await act.end(nil, dismissalPolicy: .immediate)
            }
        }
    }
    
    /// Finaliza todas as atividades ativas
    public func endAllActivities() {
        Task { @MainActor in
            for activity in Activity<CalorieActivityAttributes>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            for activity in Activity<WorkoutActivityAttributes>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
            self.isActivityActive = false
            self.activityId = nil
        }
    }
    
    // MARK: - Live Activity de Treino & Ilha Dinâmica (Rest Timer & Exercícios)
    
    public func startWorkoutActivity(
        workoutTitle: String,
        currentExerciseName: String = "",
        currentSetNumber: Int = 1,
        totalSetsForExercise: Int = 3,
        completedSetsTotal: Int = 0,
        totalSetsTotal: Int = 12,
        caloriesBurned: Double = 0.0,
        workoutStartDate: Date = Date(),
        isResting: Bool = false,
        restStartDate: Date? = nil,
        restEndDate: Date? = nil,
        restDurationSeconds: Int = 90,
        isAllCompleted: Bool = false,
        currentSetWeightKg: Double = 0.0,
        lastCompletedSetNumber: Int = 0,
        lastCompletedSetWeightKg: Double = 0.0,
        lastCompletedExerciseName: String = ""
    ) {
        let auth = ActivityAuthorizationInfo()
        guard auth.areActivitiesEnabled else {
            print("⚠️ [Live Activity] ATENÇÃO: As Atividades ao Vivo estão desativadas para o Forma nos Ajustes do iOS!")
            return
        }
        
        let shared = SharedWorkoutSessionManager.shared.loadSession()
        let lastInfo = shared?.lastCompletedSetInfo
        let resolvedCurWeight = currentSetWeightKg > 0 ? currentSetWeightKg : (shared?.currentSetWeightKg ?? 0.0)
        let resolvedLastSetNum = lastCompletedSetNumber > 0 ? lastCompletedSetNumber : (lastInfo?.setNumber ?? 0)
        let resolvedLastWeight = lastCompletedSetWeightKg > 0 ? lastCompletedSetWeightKg : (lastInfo?.weightKg ?? resolvedCurWeight)
        let resolvedLastExName = !lastCompletedExerciseName.isEmpty ? lastCompletedExerciseName : (lastInfo?.exerciseName ?? currentExerciseName)
        
        let state = WorkoutActivityAttributes.ContentState(
            workoutTitle: workoutTitle,
            currentExerciseName: currentExerciseName,
            currentSetNumber: currentSetNumber,
            totalSetsForExercise: totalSetsForExercise,
            completedSetsTotal: completedSetsTotal,
            totalSetsTotal: totalSetsTotal,
            caloriesBurned: caloriesBurned,
            workoutStartDate: workoutStartDate,
            isResting: isResting,
            restStartDate: restStartDate,
            restEndDate: restEndDate,
            restDurationSeconds: restDurationSeconds,
            isAllCompleted: isAllCompleted,
            currentSetWeightKg: resolvedCurWeight,
            lastCompletedSetNumber: resolvedLastSetNum,
            lastCompletedSetWeightKg: resolvedLastWeight,
            lastCompletedExerciseName: resolvedLastExName,
            weightStepKg: SharedWorkoutSessionManager.shared.currentWeightStepKg
        )
        
        if let existing = Activity<WorkoutActivityAttributes>.activities.first {
            Task { @MainActor in
                await existing.update(ActivityContent(state: state, staleDate: nil, relevanceScore: 100.0))
            }
            return
        }
        
        let attributes = WorkoutActivityAttributes()
        do {
            _ = try Activity.request(
                attributes: attributes,
                content: ActivityContent(state: state, staleDate: nil, relevanceScore: 100.0),
                pushType: nil
            )
            print("🏋️‍♂️ Live Activity de Treino iniciada com sucesso na Ilha Dinâmica!")
        } catch {
            print("⚠️ Falha ao iniciar Live Activity de Treino: \(error.localizedDescription)")
        }
    }
    
    public func updateWorkoutActivity(
        workoutTitle: String,
        currentExerciseName: String,
        currentSetNumber: Int,
        totalSetsForExercise: Int,
        completedSetsTotal: Int,
        totalSetsTotal: Int,
        caloriesBurned: Double,
        workoutStartDate: Date,
        isResting: Bool,
        restStartDate: Date? = nil,
        restEndDate: Date? = nil,
        restDurationSeconds: Int = 90,
        isAllCompleted: Bool = false,
        currentSetWeightKg: Double = 0.0,
        lastCompletedSetNumber: Int = 0,
        lastCompletedSetWeightKg: Double = 0.0,
        lastCompletedExerciseName: String = ""
    ) {
        let shared = SharedWorkoutSessionManager.shared.loadSession()
        let lastInfo = shared?.lastCompletedSetInfo
        let resolvedCurWeight = currentSetWeightKg > 0 ? currentSetWeightKg : (shared?.currentSetWeightKg ?? 0.0)
        let resolvedLastSetNum = lastCompletedSetNumber > 0 ? lastCompletedSetNumber : (lastInfo?.setNumber ?? 0)
        let resolvedLastWeight = lastCompletedSetWeightKg > 0 ? lastCompletedSetWeightKg : (lastInfo?.weightKg ?? resolvedCurWeight)
        let resolvedLastExName = !lastCompletedExerciseName.isEmpty ? lastCompletedExerciseName : (lastInfo?.exerciseName ?? currentExerciseName)
        
        guard let current = Activity<WorkoutActivityAttributes>.activities.first else {
            startWorkoutActivity(
                workoutTitle: workoutTitle,
                currentExerciseName: currentExerciseName,
                currentSetNumber: currentSetNumber,
                totalSetsForExercise: totalSetsForExercise,
                completedSetsTotal: completedSetsTotal,
                totalSetsTotal: totalSetsTotal,
                caloriesBurned: caloriesBurned,
                workoutStartDate: workoutStartDate,
                isResting: isResting,
                restStartDate: restStartDate,
                restEndDate: restEndDate,
                restDurationSeconds: restDurationSeconds,
                isAllCompleted: isAllCompleted,
                currentSetWeightKg: resolvedCurWeight,
                lastCompletedSetNumber: resolvedLastSetNum,
                lastCompletedSetWeightKg: resolvedLastWeight,
                lastCompletedExerciseName: resolvedLastExName
            )
            return
        }
        
        let state = WorkoutActivityAttributes.ContentState(
            workoutTitle: workoutTitle,
            currentExerciseName: currentExerciseName,
            currentSetNumber: currentSetNumber,
            totalSetsForExercise: totalSetsForExercise,
            completedSetsTotal: completedSetsTotal,
            totalSetsTotal: totalSetsTotal,
            caloriesBurned: caloriesBurned,
            workoutStartDate: workoutStartDate,
            isResting: isResting,
            restStartDate: restStartDate,
            restEndDate: restEndDate,
            restDurationSeconds: restDurationSeconds,
            isAllCompleted: isAllCompleted,
            currentSetWeightKg: resolvedCurWeight,
            lastCompletedSetNumber: resolvedLastSetNum,
            lastCompletedSetWeightKg: resolvedLastWeight,
            lastCompletedExerciseName: resolvedLastExName,
            weightStepKg: SharedWorkoutSessionManager.shared.currentWeightStepKg
        )
        
        Task { @MainActor in
            await current.update(ActivityContent(state: state, staleDate: nil, relevanceScore: 100.0))
        }
    }
    
    public func endWorkoutActivity() {
        Task { @MainActor in
            for activity in Activity<WorkoutActivityAttributes>.activities {
                await activity.end(nil, dismissalPolicy: .immediate)
            }
        }
    }
}

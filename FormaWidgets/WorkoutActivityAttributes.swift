//
//  WorkoutActivityAttributes.swift
//  FormaWidgets
//
//  Created by João Pedro on 10/29/26.
//

import Foundation
import ActivityKit

nonisolated public struct WorkoutActivityAttributes: ActivityAttributes, Sendable {
    public struct ContentState: Codable, Hashable, Sendable {
        public var workoutTitle: String
        public var currentExerciseName: String
        public var currentSetNumber: Int
        public var totalSetsForExercise: Int
        public var completedSetsTotal: Int
        public var totalSetsTotal: Int
        public var caloriesBurned: Double
        public var workoutStartDate: Date
        
        // Cronômetro Regressivo de Descanso na Ilha Dinâmica
        public var isResting: Bool
        public var restStartDate: Date?
        public var restEndDate: Date?
        public var restDurationSeconds: Int
        public var isAllCompleted: Bool
        
        // Carga da série atual e da série que acabou de passar
        public var currentSetWeightKg: Double
        public var lastCompletedSetNumber: Int
        public var lastCompletedSetWeightKg: Double
        public var lastCompletedExerciseName: String
        public var weightStepKg: Double
        
        public init(
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
            lastCompletedExerciseName: String = "",
            weightStepKg: Double = 2.0
        ) {
            self.workoutTitle = workoutTitle
            self.currentExerciseName = currentExerciseName
            self.currentSetNumber = currentSetNumber
            self.totalSetsForExercise = totalSetsForExercise
            self.completedSetsTotal = completedSetsTotal
            self.totalSetsTotal = totalSetsTotal
            self.caloriesBurned = caloriesBurned
            self.workoutStartDate = workoutStartDate
            self.isResting = isResting
            self.restStartDate = restStartDate
            self.restEndDate = restEndDate
            self.restDurationSeconds = restDurationSeconds
            self.isAllCompleted = isAllCompleted
            self.currentSetWeightKg = currentSetWeightKg
            self.lastCompletedSetNumber = lastCompletedSetNumber
            self.lastCompletedSetWeightKg = lastCompletedSetWeightKg
            self.lastCompletedExerciseName = lastCompletedExerciseName
            self.weightStepKg = weightStepKg > 0 ? weightStepKg : 2.0
        }
        
        public var overallProgress: Double {
            guard totalSetsTotal > 0 else { return 0 }
            return min(1.0, max(0.0, Double(completedSetsTotal) / Double(totalSetsTotal)))
        }
    }
    
    public var routineId: String
    
    public init(routineId: String = UUID().uuidString) {
        self.routineId = routineId
    }
}

//
//  WorkoutRoutine.swift
//  Forma
//
//  Created by João Pedro on 10/29/26.
//

import Foundation
import SwiftData

/// Item de exercício individual dentro de uma rotina ou ficha de treino
public struct WorkoutExerciseItem: Codable, Identifiable, Sendable, Equatable {
    public var id: UUID
    public var name: String
    public var sets: Int
    public var reps: String
    public var suggestedWeight: String
    public var targetMuscle: String
    public var executionTip: String
    public var restSeconds: Int
    public var completedSets: [Bool]
    public var setWeights: [Double]
    public var animationId: String?
    
    enum CodingKeys: String, CodingKey {
        case id
        case name
        case sets
        case reps
        case suggestedWeight = "suggested_weight"
        case targetMuscle = "target_muscle"
        case executionTip = "execution_tip"
        case restSeconds = "rest_seconds"
        case completedSets = "completed_sets"
        case setWeights = "set_weights"
        case animationId = "animation_id"
    }
    
    public init(
        id: UUID = UUID(),
        name: String,
        sets: Int = 3,
        reps: String = "10-12",
        suggestedWeight: String = "",
        targetMuscle: String = "",
        executionTip: String = "",
        restSeconds: Int = 90,
        completedSets: [Bool]? = nil,
        setWeights: [Double]? = nil,
        animationId: String? = nil
    ) {
        self.id = id
        self.name = name
        let safeSets = max(1, sets)
        self.sets = safeSets
        self.reps = reps.isEmpty ? "10-12" : reps
        self.suggestedWeight = suggestedWeight
        self.targetMuscle = targetMuscle
        self.executionTip = executionTip
        self.restSeconds = restSeconds > 0 ? restSeconds : 90
        self.completedSets = completedSets ?? Array(repeating: false, count: safeSets)
        let defaultKg = Self.parseWeightKg(suggestedWeight) ?? Self.recommendedDefaultWeightKg(exerciseName: name, targetMuscle: targetMuscle)
        if let providedWeights = setWeights, !providedWeights.isEmpty {
            var normalized = providedWeights
            while normalized.count < safeSets {
                normalized.append(normalized.last ?? defaultKg)
            }
            self.setWeights = Array(normalized.prefix(safeSets))
        } else {
            self.setWeights = Array(repeating: defaultKg, count: safeSets)
        }
        if let valid = ExerciseCatalog.shared.item(withId: animationId) {
            self.animationId = valid.id
            if self.executionTip.isEmpty {
                self.executionTip = valid.concentricInstruction
            }
        } else {
            let matched = ExerciseCatalog.shared.guaranteedMatch(for: name, targetMuscle: targetMuscle)
            self.animationId = matched.id
            if self.executionTip.isEmpty {
                self.executionTip = matched.concentricInstruction
            }
        }
    }
    
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = (try? container.decodeIfPresent(UUID.self, forKey: .id)) ?? UUID()
        let decodedName = (try? container.decodeIfPresent(String.self, forKey: .name)) ?? "Exercício"
        self.name = decodedName
        let rawSets = (try? container.decodeIfPresent(Int.self, forKey: .sets)) ?? 3
        let safeSets = max(1, rawSets)
        self.sets = safeSets
        self.reps = (try? container.decodeIfPresent(String.self, forKey: .reps)) ?? "10-12"
        let decodedSuggested = (try? container.decodeIfPresent(String.self, forKey: .suggestedWeight)) ?? ""
        self.suggestedWeight = decodedSuggested
        let decodedMuscle = (try? container.decodeIfPresent(String.self, forKey: .targetMuscle)) ?? ""
        self.targetMuscle = decodedMuscle
        self.executionTip = (try? container.decodeIfPresent(String.self, forKey: .executionTip)) ?? ""
        let rawRest = (try? container.decodeIfPresent(Int.self, forKey: .restSeconds)) ?? 90
        self.restSeconds = rawRest > 0 ? rawRest : 90
        let decodedAnimId = try? container.decodeIfPresent(String.self, forKey: .animationId)
        if let valid = ExerciseCatalog.shared.item(withId: decodedAnimId) {
            self.animationId = valid.id
            if self.executionTip.isEmpty {
                self.executionTip = valid.concentricInstruction
            }
        } else {
            let query = (decodedAnimId?.isEmpty == false) ? "\(decodedName) \(decodedAnimId!)" : decodedName
            let matched = ExerciseCatalog.shared.guaranteedMatch(for: query, targetMuscle: decodedMuscle)
            self.animationId = matched.id
            if self.executionTip.isEmpty {
                self.executionTip = matched.concentricInstruction
            }
        }
        
        if let decodedSets = try? container.decodeIfPresent([Bool].self, forKey: .completedSets) {
            self.completedSets = decodedSets
        } else {
            self.completedSets = Array(repeating: false, count: safeSets)
        }
        
        let fallbackKg = Self.parseWeightKg(decodedSuggested) ?? Self.recommendedDefaultWeightKg(exerciseName: decodedName, targetMuscle: decodedMuscle)
        if let decodedWeights = try? container.decodeIfPresent([Double].self, forKey: .setWeights), !decodedWeights.isEmpty {
            var normalized = decodedWeights
            while normalized.count < safeSets {
                normalized.append(normalized.last ?? fallbackKg)
            }
            self.setWeights = Array(normalized.prefix(safeSets))
        } else {
            self.setWeights = Array(repeating: fallbackKg, count: safeSets)
        }
    }
    
    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(id, forKey: .id)
        try container.encode(name, forKey: .name)
        try container.encode(sets, forKey: .sets)
        try container.encode(reps, forKey: .reps)
        try container.encode(suggestedWeight, forKey: .suggestedWeight)
        try container.encode(targetMuscle, forKey: .targetMuscle)
        try container.encode(executionTip, forKey: .executionTip)
        try container.encode(restSeconds, forKey: .restSeconds)
        try container.encode(completedSets, forKey: .completedSets)
        try container.encode(setWeights, forKey: .setWeights)
        try container.encodeIfPresent(animationId, forKey: .animationId)
    }
    
    public var isAllSetsCompleted: Bool {
        !completedSets.isEmpty && completedSets.allSatisfy { $0 }
    }
    
    public var completedCount: Int {
        completedSets.filter { $0 }.count
    }
    
    public func weightForSet(_ index: Int) -> Double {
        if index >= 0 && index < setWeights.count {
            return max(0, setWeights[index])
        }
        return Self.parseWeightKg(suggestedWeight) ?? Self.recommendedDefaultWeightKg(exerciseName: name, targetMuscle: targetMuscle)
    }
    
    /// Média de carga das séries concluídas (ou de todas as séries se nenhuma concluída), arredondada para 0,5 kg
    public var averageWeightKg: Double {
        let completedWeights: [Double] = (0..<sets).compactMap { idx in
            let isDone = idx < completedSets.count ? completedSets[idx] : false
            guard isDone else { return nil }
            let w = weightForSet(idx)
            return w > 0 ? w : nil
        }
        let pool = completedWeights.isEmpty ? setWeights.filter { $0 > 0 } : completedWeights
        guard !pool.isEmpty else {
            return Self.parseWeightKg(suggestedWeight) ?? 0
        }
        let avg = pool.reduce(0.0, +) / Double(pool.count)
        return (avg * 2.0).rounded() / 2.0
    }
    
    public static func parseWeightKg(_ raw: String) -> Double? {
        let normalized = raw.replacingOccurrences(of: ",", with: ".")
        guard let regex = try? NSRegularExpression(pattern: "([0-9]+(?:\\.[0-9]+)?)") else { return nil }
        let range = NSRange(normalized.startIndex..<normalized.endIndex, in: normalized)
        guard let match = regex.firstMatch(in: normalized, options: [], range: range),
              let swiftRange = Range(match.range(at: 1), in: normalized),
              let value = Double(normalized[swiftRange]),
              value > 0 else {
            return nil
        }
        return value
    }
    
    public static func formatWeightKg(_ kg: Double) -> String {
        let clamped = max(0.0, (kg * 2.0).rounded() / 2.0)
        if clamped <= 0 { return "Peso corporal" }
        if clamped.truncatingRemainder(dividingBy: 1.0) == 0 {
            return "\(Int(clamped)) kg"
        }
        return String(format: "%.1f kg", clamped).replacingOccurrences(of: ".", with: ",")
    }
    
    /// Calcula a carga inicial padrão recomendada para o usuário (baseada no peso corporal salvo no App Group e biomecânica do exercício)
    public static func recommendedDefaultWeightKg(
        exerciseName: String,
        targetMuscle: String = "",
        userBodyWeightKg: Double? = nil
    ) -> Double {
        let defaults = UserDefaults(suiteName: "group.jota.Forma")
        // 1. Se já existe média histórica da última vez que o exercício foi feito, usa ela!
        let key = normalizedExerciseKey(exerciseName)
        if let historyMap = defaults?.dictionary(forKey: "exercise_last_average_weights_v1") as? [String: Double],
           let lastAvg = historyMap[key], lastAvg > 0 {
            return (lastAvg * 2.0).rounded() / 2.0
        }
        
        // 2. Caso contrário, calcula carga padrão recomendada para o perfil do usuário
        let storedBw = defaults?.double(forKey: "user_body_weight_kg_v1") ?? 0
        let bw = max(45.0, min(160.0, userBodyWeightKg ?? (storedBw > 30 ? storedBw : 75.0)))
        let text = "\(exerciseName) \(targetMuscle)"
            .folding(options: .diacriticInsensitive, locale: .current)
            .lowercased()
        
        if text.contains("flexao") || text.contains("barra fixa") || text.contains("prancha") || text.contains("abdominal") {
            return 0.0
        }
        let ratio: Double
        if text.contains("leg press") {
            ratio = 1.20
        } else if text.contains("agachamento") || text.contains("levantamento terra") || text.contains("stiff") || text.contains("hack") || text.contains("hip thrust") || text.contains("elevacao pelvica") {
            ratio = 0.45
        } else if text.contains("supino") || text.contains("puxada") || text.contains("remada") || text.contains("pulley") || text.contains("extensora") || text.contains("flexora") {
            ratio = 0.35
        } else if text.contains("desenvolvimento") || text.contains("afundo") || text.contains("passada") || text.contains("bulgaro") || text.contains("panturrilha") || text.contains("crucifixo") || text.contains("voador") {
            ratio = 0.22
        } else if text.contains("elevacao lateral") || text.contains("elevacao frontal") || text.contains("rosca punho") || text.contains("coice") {
            ratio = 0.11
        } else {
            // Rosca direta/alternada, tríceps corda/testa, etc.
            ratio = 0.18
        }
        let raw = bw * ratio
        return max(4.0, (raw / 2.0).rounded() * 2.0)
    }
    
    public static func normalizedExerciseKey(_ name: String) -> String {
        name.folding(options: .diacriticInsensitive, locale: .current)
            .lowercased()
            .components(separatedBy: CharacterSet.alphanumerics.inverted)
            .filter { !$0.isEmpty }
            .joined(separator: "_")
    }
}

/// Ficha ou Rotina de Treino persistida no SwiftData
@Model
final class WorkoutRoutine {
    var id: UUID = UUID()
    var title: String = ""
    var workoutTypeRaw: String = "Musculação"
    var targetMuscles: String = ""
    var hypertrophyFocus: String = ""
    var estimatedDurationMinutes: Int = 50
    var estimatedCalories: Double = 350.0
    var recommendedRestSeconds: Int = 90
    var isFavorite: Bool = false
    var exercisesJson: String = "[]"
    var notes: String = ""
    var createdAt: Date = Date()
    var lastTrainedAt: Date? = nil
    
    var workoutType: WorkoutType {
        get { WorkoutType(rawValue: workoutTypeRaw) ?? .bodybuilding }
        set { workoutTypeRaw = newValue.rawValue }
    }
    
    var exercises: [WorkoutExerciseItem] {
        get {
            guard let data = exercisesJson.data(using: .utf8) else { return [] }
            return (try? JSONDecoder().decode([WorkoutExerciseItem].self, from: data)) ?? []
        }
        set {
            if let data = try? JSONEncoder().encode(newValue),
               let json = String(data: data, encoding: .utf8) {
                exercisesJson = json
            } else {
                exercisesJson = "[]"
            }
        }
    }
    
    init(
        id: UUID = UUID(),
        title: String,
        workoutType: WorkoutType = .bodybuilding,
        targetMuscles: String = "",
        hypertrophyFocus: String = "",
        estimatedDurationMinutes: Int = 50,
        estimatedCalories: Double = 350.0,
        recommendedRestSeconds: Int = 90,
        isFavorite: Bool = false,
        exercises: [WorkoutExerciseItem] = [],
        notes: String = "",
        createdAt: Date = Date(),
        lastTrainedAt: Date? = nil
    ) {
        self.id = id
        self.title = title
        self.workoutTypeRaw = workoutType.rawValue
        self.targetMuscles = targetMuscles
        self.hypertrophyFocus = hypertrophyFocus
        self.estimatedDurationMinutes = estimatedDurationMinutes
        self.estimatedCalories = estimatedCalories
        self.recommendedRestSeconds = recommendedRestSeconds
        self.isFavorite = isFavorite
        self.notes = notes
        self.createdAt = createdAt
        self.lastTrainedAt = lastTrainedAt
        
        if let data = try? JSONEncoder().encode(exercises),
           let json = String(data: data, encoding: .utf8) {
            self.exercisesJson = json
        } else {
            self.exercisesJson = "[]"
        }
    }
}

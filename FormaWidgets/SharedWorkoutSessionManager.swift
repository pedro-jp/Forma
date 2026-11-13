//
//  SharedWorkoutSessionManager.swift
//  FormaWidgets
//
//  Created by João Pedro on 10/29/26.
//

import Foundation
import ActivityKit

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
        case id, name, sets, reps
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
        self.animationId = animationId
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
        self.animationId = try? container.decodeIfPresent(String.self, forKey: .animationId)
        
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
    
    public static func recommendedDefaultWeightKg(
        exerciseName: String,
        targetMuscle: String = "",
        userBodyWeightKg: Double? = nil
    ) -> Double {
        let defaults = UserDefaults(suiteName: "group.jota.Forma")
        let key = normalizedExerciseKey(exerciseName)
        if let historyMap = defaults?.dictionary(forKey: "exercise_last_average_weights_v1") as? [String: Double],
           let lastAvg = historyMap[key], lastAvg > 0 {
            return (lastAvg * 2.0).rounded() / 2.0
        }
        
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

public struct SharedWorkoutState: Codable, Sendable {
    public var title: String
    public var workoutTypeRaw: String
    public var exercises: [WorkoutExerciseItem]
    public var currentExerciseIndex: Int
    public var currentSetIndex: Int
    public var sessionStartDate: Date
    public var baseCalories: Double
    public var baseDurationMinutes: Int
    public var isResting: Bool
    public var restStartDate: Date?
    public var restEndDate: Date?
    public var restDurationSeconds: Int
    public var isAllCompleted: Bool
    public var isFinished: Bool
    public var lastCompletedExerciseIndex: Int?
    public var lastCompletedSetIndex: Int?
    
    public init(
        title: String,
        workoutTypeRaw: String = "Musculação",
        exercises: [WorkoutExerciseItem] = [],
        currentExerciseIndex: Int = 0,
        currentSetIndex: Int = 0,
        sessionStartDate: Date = Date(),
        baseCalories: Double = 350.0,
        baseDurationMinutes: Int = 50,
        isResting: Bool = false,
        restStartDate: Date? = nil,
        restEndDate: Date? = nil,
        restDurationSeconds: Int = 90,
        isAllCompleted: Bool = false,
        isFinished: Bool = false,
        lastCompletedExerciseIndex: Int? = nil,
        lastCompletedSetIndex: Int? = nil
    ) {
        self.title = title
        self.workoutTypeRaw = workoutTypeRaw
        self.exercises = exercises
        self.currentExerciseIndex = currentExerciseIndex
        self.currentSetIndex = currentSetIndex
        self.sessionStartDate = sessionStartDate
        self.baseCalories = baseCalories
        self.baseDurationMinutes = baseDurationMinutes
        self.isResting = isResting
        self.restStartDate = restStartDate
        self.restEndDate = restEndDate
        self.restDurationSeconds = restDurationSeconds
        self.isAllCompleted = isAllCompleted
        self.isFinished = isFinished
        self.lastCompletedExerciseIndex = lastCompletedExerciseIndex
        self.lastCompletedSetIndex = lastCompletedSetIndex
    }
    
    public var totalSetsCount: Int {
        exercises.reduce(0) { $0 + $1.sets }
    }
    
    public var completedSetsCount: Int {
        exercises.reduce(0) { $0 + $1.completedCount }
    }
    
    public var liveCalories: Double {
        let elapsed = max(1.0, Date().timeIntervalSince(sessionStartDate) / 60.0)
        let rate = baseCalories / max(10.0, Double(baseDurationMinutes))
        return (elapsed * rate).rounded()
    }
    
    public var currentExercise: WorkoutExerciseItem? {
        guard currentExerciseIndex < exercises.count else { return exercises.last }
        return exercises[currentExerciseIndex]
    }
    
    public var currentSetWeightKg: Double {
        guard let ex = currentExercise else { return 0.0 }
        let idx = min(max(0, currentSetIndex), max(0, ex.sets - 1))
        return ex.weightForSet(idx)
    }
    
    public var lastCompletedSetInfo: (exerciseIndex: Int, setIndex: Int, setNumber: Int, weightKg: Double, exerciseName: String)? {
        if let exIdx = lastCompletedExerciseIndex,
           let sIdx = lastCompletedSetIndex,
           exIdx >= 0, exIdx < exercises.count {
            let ex = exercises[exIdx]
            let safeSet = min(max(0, sIdx), max(0, ex.sets - 1))
            return (exIdx, safeSet, safeSet + 1, ex.weightForSet(safeSet), ex.name)
        }
        for exIdx in exercises.indices.reversed() {
            let ex = exercises[exIdx]
            for sIdx in (0..<min(ex.sets, ex.completedSets.count)).reversed() where ex.completedSets[sIdx] {
                return (exIdx, sIdx, sIdx + 1, ex.weightForSet(sIdx), ex.name)
            }
        }
        return nil
    }
}

public final class SharedWorkoutSessionManager: @unchecked Sendable {
    public static let shared = SharedWorkoutSessionManager()
    
    private let appGroupSuite = "group.jota.Forma"
    private let sessionKey = "active_workout_session_state"
    private let pendingWorkoutKey = "pending_finished_workout"
    private let historyWeightsKey = "exercise_last_average_weights_v1"
    private let weightStepKey = "live_activity_weight_step_kg_v1"
    
    private var sharedDefaults: UserDefaults? {
        UserDefaults(suiteName: appGroupSuite)
    }
    
    public var currentWeightStepKg: Double {
        let val = sharedDefaults?.double(forKey: weightStepKey) ?? 0.0
        if val == 0.5 || val == 1.0 || val == 2.0 {
            return val
        }
        return 2.0
    }
    
    public func cycleWeightStepFromLiveActivity() async {
        let cur = currentWeightStepKg
        let next: Double
        if cur == 2.0 {
            next = 1.0
        } else if cur == 1.0 {
            next = 0.5
        } else {
            next = 2.0
        }
        sharedDefaults?.set(next, forKey: weightStepKey)
        if let session = loadSession() {
            await syncLiveActivityState(from: session)
        }
    }
    
    private init() {}
    
    public func loadSession() -> SharedWorkoutState? {
        guard let data = sharedDefaults?.data(forKey: sessionKey) else { return nil }
        return try? JSONDecoder().decode(SharedWorkoutState.self, from: data)
    }
    
    public func saveSession(_ session: SharedWorkoutState) {
        if let data = try? JSONEncoder().encode(session) {
            sharedDefaults?.set(data, forKey: sessionKey)
        }
    }
    
    public func clearSession() {
        sharedDefaults?.removeObject(forKey: sessionKey)
    }
    
    public func recordExerciseWeightsHistory(from exercises: [WorkoutExerciseItem]) {
        var map = (sharedDefaults?.dictionary(forKey: historyWeightsKey) as? [String: Double]) ?? [:]
        for ex in exercises {
            let avg = ex.averageWeightKg
            guard avg > 0 else { continue }
            let key = WorkoutExerciseItem.normalizedExerciseKey(ex.name)
            if !key.isEmpty {
                map[key] = avg
            }
            if let animId = ex.animationId, !animId.isEmpty {
                map["id_\(animId)"] = avg
            }
        }
        sharedDefaults?.set(map, forKey: historyWeightsKey)
    }
    
    public func adjustRecentSetWeightFromLiveActivity(deltaKg: Double) async {
        guard var session = loadSession(), !session.exercises.isEmpty else { return }
        
        let targetExIdx: Int
        let targetSetIdx: Int
        if session.isResting, let lastInfo = session.lastCompletedSetInfo {
            targetExIdx = lastInfo.exerciseIndex
            targetSetIdx = lastInfo.setIndex
        } else {
            targetExIdx = min(max(0, session.currentExerciseIndex), session.exercises.count - 1)
            let ex = session.exercises[targetExIdx]
            targetSetIdx = min(max(0, session.currentSetIndex), max(0, ex.sets - 1))
        }
        
        guard targetExIdx < session.exercises.count else { return }
        var ex = session.exercises[targetExIdx]
        let safeSets = max(1, ex.sets)
        let fallback = ex.weightForSet(targetSetIdx)
        while ex.setWeights.count < safeSets {
            ex.setWeights.append(fallback)
        }
        let currentWeight = ex.setWeights[targetSetIdx]
        let effectiveDelta = abs(deltaKg) == 2.0 ? (deltaKg < 0 ? -currentWeightStepKg : currentWeightStepKg) : deltaKg
        let newWeight = max(0.0, ((currentWeight + effectiveDelta) * 2.0).rounded() / 2.0)
        ex.setWeights[targetSetIdx] = newWeight
        
        for idx in (targetSetIdx + 1)..<safeSets {
            let done = idx < ex.completedSets.count ? ex.completedSets[idx] : false
            if !done {
                ex.setWeights[idx] = newWeight
            }
        }
        ex.suggestedWeight = WorkoutExerciseItem.formatWeightKg(ex.averageWeightKg)
        session.exercises[targetExIdx] = ex
        
        saveSession(session)
        await syncLiveActivityState(from: session)
    }
    
    public func completeCurrentSetFromLiveActivity() async {
        guard var session = loadSession() else { return }
        
        let exIndex = session.currentExerciseIndex
        guard exIndex < session.exercises.count else { return }
        
        var ex = session.exercises[exIndex]
        var sets = ex.completedSets
        while sets.count < ex.sets { sets.append(false) }
        
        let sIndex = min(session.currentSetIndex, ex.sets - 1)
        sets[sIndex] = true
        ex.completedSets = sets
        session.exercises[exIndex] = ex
        session.lastCompletedExerciseIndex = exIndex
        session.lastCompletedSetIndex = sIndex
        
        let allDone = session.exercises.allSatisfy { $0.isAllSetsCompleted }
        session.isAllCompleted = allDone
        
        if allDone {
            session.isResting = false
            session.restStartDate = nil
            session.restEndDate = nil
            WorkoutNotificationManager.shared.cancelRestNotification()
        } else {
            let targetRest = ex.restSeconds > 0 ? ex.restSeconds : session.restDurationSeconds
            let now = Date()
            session.isResting = true
            session.restStartDate = now
            session.restEndDate = now.addingTimeInterval(Double(targetRest))
            session.restDurationSeconds = targetRest
            
            if sIndex + 1 < ex.sets {
                session.currentSetIndex = sIndex + 1
            } else if exIndex + 1 < session.exercises.count {
                session.currentExerciseIndex = exIndex + 1
                session.currentSetIndex = 0
            }
            
            let nextEx = session.currentExercise ?? ex
            let nextSet = min(session.currentSetIndex + 1, nextEx.sets)
            let lastKg = ex.weightForSet(sIndex)
            WorkoutNotificationManager.shared.scheduleRestNotification(
                seconds: targetRest,
                exerciseName: nextEx.name,
                nextSet: nextSet,
                lastSetWeightKg: lastKg
            )
        }
        
        saveSession(session)
        await syncLiveActivityState(from: session)
    }
    
    public func skipRestFromLiveActivity() async {
        guard var session = loadSession() else { return }
        session.isResting = false
        session.restStartDate = nil
        session.restEndDate = nil
        WorkoutNotificationManager.shared.cancelRestNotification()
        saveSession(session)
        await syncLiveActivityState(from: session)
    }
    
    public func addRestSecondsFromLiveActivity(seconds: Int = 30) async {
        guard var session = loadSession(), session.isResting else { return }
        let currentEnd = session.restEndDate ?? Date()
        let newEnd = currentEnd.addingTimeInterval(Double(seconds))
        session.restEndDate = newEnd
        session.restDurationSeconds += seconds
        
        let rem = max(1, Int(newEnd.timeIntervalSinceNow))
        let curEx = session.currentExercise
        let curSet = min(session.currentSetIndex + 1, curEx?.sets ?? 3)
        let lastKg = session.lastCompletedSetInfo?.weightKg ?? session.currentSetWeightKg
        WorkoutNotificationManager.shared.scheduleRestNotification(
            seconds: rem,
            exerciseName: curEx?.name ?? "Treino",
            nextSet: curSet,
            lastSetWeightKg: lastKg
        )
        
        saveSession(session)
        await syncLiveActivityState(from: session)
    }
    
    public func finishWorkoutFromLiveActivity() async {
        guard var session = loadSession() else { return }
        session.isFinished = true
        WorkoutNotificationManager.shared.cancelRestNotification()
        recordExerciseWeightsHistory(from: session.exercises)
        
        let weightsSummary = session.exercises
            .filter { $0.averageWeightKg > 0 }
            .map { "\($0.name): \(WorkoutExerciseItem.formatWeightKg($0.averageWeightKg))" }
            .joined(separator: " • ")
        let notesSuffix = weightsSummary.isEmpty ? "" : " Cargas médias: \(weightsSummary)."
        
        let pending = PendingWorkoutRecord(
            title: session.title,
            workoutTypeRaw: session.workoutTypeRaw,
            durationMinutes: max(1, Int(Date().timeIntervalSince(session.sessionStartDate) / 60)),
            caloriesBurned: session.liveCalories,
            notes: "Treino operado e concluído via Ilha Dinâmica (\(session.completedSetsCount)/\(session.totalSetsCount) séries).\(notesSuffix)",
            date: Date()
        )
        if let data = try? JSONEncoder().encode(pending) {
            sharedDefaults?.set(data, forKey: pendingWorkoutKey)
        }
        
        clearSession()
        
        for activity in Activity<WorkoutActivityAttributes>.activities {
            await activity.end(nil, dismissalPolicy: .immediate)
        }
    }
    
    public func syncLiveActivityState(from session: SharedWorkoutState) async {
        let curEx = session.currentExercise
        let curExName = curEx?.name ?? "Treino"
        let curSetNumber = min(session.currentSetIndex + 1, curEx?.sets ?? 3)
        let totalSetsForEx = curEx?.sets ?? 3
        let lastInfo = session.lastCompletedSetInfo
        
        let state = WorkoutActivityAttributes.ContentState(
            workoutTitle: session.title,
            currentExerciseName: curExName,
            currentSetNumber: curSetNumber,
            totalSetsForExercise: totalSetsForEx,
            completedSetsTotal: session.completedSetsCount,
            totalSetsTotal: session.totalSetsCount,
            caloriesBurned: session.liveCalories,
            workoutStartDate: session.sessionStartDate,
            isResting: session.isResting,
            restStartDate: session.restStartDate,
            restEndDate: session.restEndDate,
            restDurationSeconds: session.restDurationSeconds,
            isAllCompleted: session.isAllCompleted,
            currentSetWeightKg: session.currentSetWeightKg,
            lastCompletedSetNumber: lastInfo?.setNumber ?? 0,
            lastCompletedSetWeightKg: lastInfo?.weightKg ?? session.currentSetWeightKg,
            lastCompletedExerciseName: lastInfo?.exerciseName ?? curExName,
            weightStepKg: currentWeightStepKg
        )
        
        for activity in Activity<WorkoutActivityAttributes>.activities {
            await activity.update(ActivityContent(state: state, staleDate: nil, relevanceScore: 100.0))
        }
    }
}

public struct PendingWorkoutRecord: Codable, Sendable {
    public var title: String
    public var workoutTypeRaw: String
    public var durationMinutes: Int
    public var caloriesBurned: Double
    public var notes: String
    public var date: Date
}

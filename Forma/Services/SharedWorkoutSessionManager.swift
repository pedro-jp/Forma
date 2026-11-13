//
//  SharedWorkoutSessionManager.swift
//  Forma
//
//  Created by João Pedro on 10/29/26.
//

import Foundation
import ActivityKit

/// Modelo unificado de sessão ativa de treino compartilhado via App Group (group.jota.Forma)
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
    
    /// Retorna os dados da última série concluída ("a série que passou") ou nil se nenhuma série foi concluída ainda
    public var lastCompletedSetInfo: (exerciseIndex: Int, setIndex: Int, setNumber: Int, weightKg: Double, exerciseName: String)? {
        if let exIdx = lastCompletedExerciseIndex,
           let sIdx = lastCompletedSetIndex,
           exIdx >= 0, exIdx < exercises.count {
            let ex = exercises[exIdx]
            let safeSet = min(max(0, sIdx), max(0, ex.sets - 1))
            return (exIdx, safeSet, safeSet + 1, ex.weightForSet(safeSet), ex.name)
        }
        // Fallback: procura a última série marcada como concluída na lista
        for exIdx in exercises.indices.reversed() {
            let ex = exercises[exIdx]
            for sIdx in (0..<min(ex.sets, ex.completedSets.count)).reversed() where ex.completedSets[sIdx] {
                return (exIdx, sIdx, sIdx + 1, ex.weightForSet(sIdx), ex.name)
            }
        }
        return nil
    }
}

/// Gerenciador compartilhado entre o App e a Ilha Dinâmica / Widget Extension
public final class SharedWorkoutSessionManager: @unchecked Sendable {
    public static let shared = SharedWorkoutSessionManager()
    
    private let appGroupSuite = "group.jota.Forma"
    private let sessionKey = "active_workout_session_state"
    private let pendingWorkoutKey = "pending_finished_workout"
    private let historyWeightsKey = "exercise_last_average_weights_v1"
    private let userBodyWeightKey = "user_body_weight_kg_v1"
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
    
    // MARK: - Histórico de Carga Média & Recomendação por Usuário
    
    public func saveUserBodyWeightKg(_ weightKg: Double) {
        guard weightKg > 30 else { return }
        sharedDefaults?.set(weightKg, forKey: userBodyWeightKey)
    }
    
    public func lastAverageWeightKg(for exerciseName: String, animationId: String? = nil) -> Double? {
        guard let map = sharedDefaults?.dictionary(forKey: historyWeightsKey) as? [String: Double] else {
            return nil
        }
        if let animId = animationId, !animId.isEmpty, let byId = map["id_\(animId)"], byId > 0 {
            return (byId * 2.0).rounded() / 2.0
        }
        let key = WorkoutExerciseItem.normalizedExerciseKey(exerciseName)
        if let byName = map[key], byName > 0 {
            return (byName * 2.0).rounded() / 2.0
        }
        return nil
    }
    
    /// Registra a carga média utilizada em cada exercício ao finalizar um treino
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
    
    /// Inicializa as séries de cada exercício com a carga média da última vez que foi feito ou a carga padrão recomendada para o usuário
    public func prepareExercisesWithInitialWeights(
        _ exercises: [WorkoutExerciseItem],
        userBodyWeightKg: Double? = nil
    ) -> [WorkoutExerciseItem] {
        if let bw = userBodyWeightKg, bw > 30 {
            saveUserBodyWeightKg(bw)
        }
        return exercises.map { ex in
            var copy = ex
            let safeSets = max(1, copy.sets)
            let initialKg: Double
            if let lastAvg = lastAverageWeightKg(for: copy.name, animationId: copy.animationId), lastAvg > 0 {
                initialKg = lastAvg
            } else if let parsed = WorkoutExerciseItem.parseWeightKg(copy.suggestedWeight), parsed > 0 {
                initialKg = parsed
            } else {
                initialKg = WorkoutExerciseItem.recommendedDefaultWeightKg(
                    exerciseName: copy.name,
                    targetMuscle: copy.targetMuscle,
                    userBodyWeightKg: userBodyWeightKg
                )
            }
            if copy.setWeights.count == safeSets && copy.setWeights.contains(where: { $0 > 0 }) {
                // Se já tinha pesos por série salvos na ficha e ainda não há histórico mais recente, preserva ou usa média histórica
                if let lastAvg = lastAverageWeightKg(for: copy.name, animationId: copy.animationId), lastAvg > 0 {
                    copy.setWeights = Array(repeating: lastAvg, count: safeSets)
                }
            } else {
                copy.setWeights = Array(repeating: initialKg, count: safeSets)
            }
            if initialKg > 0 {
                copy.suggestedWeight = WorkoutExerciseItem.formatWeightKg(copy.setWeights.first ?? initialKg)
            }
            return copy
        }
    }
    
    // MARK: - Leitura e Escrita
    
    public func loadSession() -> SharedWorkoutState? {
        guard let data = sharedDefaults?.data(forKey: sessionKey) else { return nil }
        return try? JSONDecoder().decode(SharedWorkoutState.self, from: data)
    }
    
    public func saveSession(_ session: SharedWorkoutState) {
        if let data = try? JSONEncoder().encode(session) {
            sharedDefaults?.set(data, forKey: sessionKey)
        }
        Task { @MainActor in
            WatchConnectivityManager.shared.syncContextToWatch()
        }
    }
    
    public func clearSession() {
        sharedDefaults?.removeObject(forKey: sessionKey)
        Task { @MainActor in
            WatchConnectivityManager.shared.syncContextToWatch()
        }
    }
    
    // MARK: - Operações Diretas pela Ilha Dinâmica / Notificação (App Intents)
    
    /// Aumenta ou reduz a carga (kg) utilizada na série que acabou de passar (durante o descanso) ou na série atual (durante a execução)
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
        
        // Propaga a nova carga para as séries seguintes ainda não concluídas do mesmo exercício
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
    
    /// Conclui a série atual a partir da Ilha Dinâmica ou Tela Bloqueada
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
        
        // Verifica se completou todas as séries de todos os exercícios
        let allDone = session.exercises.allSatisfy { $0.isAllSetsCompleted }
        session.isAllCompleted = allDone
        
        if allDone {
            session.isResting = false
            session.restStartDate = nil
            session.restEndDate = nil
            WorkoutNotificationManager.shared.cancelRestNotification()
        } else {
            // Dispara descanso de hipertrofia
            let targetRest = ex.restSeconds > 0 ? ex.restSeconds : session.restDurationSeconds
            let now = Date()
            session.isResting = true
            session.restStartDate = now
            session.restEndDate = now.addingTimeInterval(Double(targetRest))
            session.restDurationSeconds = targetRest
            
            // Avança para próxima série ou próximo exercício
            if sIndex + 1 < ex.sets {
                session.currentSetIndex = sIndex + 1
            } else if exIndex + 1 < session.exercises.count {
                session.currentExerciseIndex = exIndex + 1
                session.currentSetIndex = 0
            }
            
            // Agenda notificação Time-Sensitive que acende a tela e vibra, informando a carga
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
    
    /// Pula o descanso e inicia a próxima série imediatamente pela Ilha Dinâmica
    public func skipRestFromLiveActivity() async {
        guard var session = loadSession() else { return }
        session.isResting = false
        session.restStartDate = nil
        session.restEndDate = nil
        WorkoutNotificationManager.shared.cancelRestNotification()
        saveSession(session)
        await syncLiveActivityState(from: session)
    }
    
    /// Adiciona segundos de descanso pela Ilha Dinâmica (+30s)
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
    
    /// Finaliza o treino diretamente pela Ilha Dinâmica, salvando para sincronizar com Apple Health e gravando as cargas médias
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
    
    // MARK: - Sincronização com Live Activity
    
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
    
    // MARK: - Recuperação de Treino Finalizado pela Ilha Dinâmica
    
    public func consumePendingWorkout() -> PendingWorkoutRecord? {
        guard let data = sharedDefaults?.data(forKey: pendingWorkoutKey) else { return nil }
        sharedDefaults?.removeObject(forKey: pendingWorkoutKey)
        return try? JSONDecoder().decode(PendingWorkoutRecord.self, from: data)
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

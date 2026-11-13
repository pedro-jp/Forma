//
//  WatchSessionController.swift
//  FormaWatch
//
//  Controlador central do Apple Watch: sincroniza via WatchConnectivity com o iPhone,
//  monitora batimentos cardíacos/calorias via HKWorkoutSession e dispara vibração no pulso (Taptic Engine)
//  quando o descanso entre as séries termina.
//

import Foundation
import WatchConnectivity
import HealthKit
import Combine
#if canImport(WatchKit)
import WatchKit
#endif

@MainActor
public final class WatchSessionController: NSObject, ObservableObject {
    public static let shared = WatchSessionController()

    // MARK: - Estado do Treino Ativo
    @Published public var hasActiveWorkout: Bool = false
    @Published public var workoutTitle: String = "Treino"
    @Published public var currentExerciseName: String = "Exercício"
    @Published public var currentSetNumber: Int = 1
    @Published public var totalSetsForExercise: Int = 3
    @Published public var completedSetsTotal: Int = 0
    @Published public var totalSetsTotal: Int = 12
    @Published public var currentSetWeightKg: Double = 20.0
    @Published public var lastCompletedSetNumber: Int = 0
    @Published public var lastCompletedSetWeightKg: Double = 20.0
    @Published public var lastCompletedExerciseName: String = ""
    @Published public var isResting: Bool = false
    @Published public var restEndDate: Date? = nil
    @Published public var restDurationSeconds: Int = 90
    @Published public var workoutStartDate: Date = Date()
    @Published public var caloriesBurned: Double = 0
    @Published public var heartRateBpm: Double = 0
    @Published public var isAllCompleted: Bool = false

    // MARK: - Estado Diário (Calorias, Macros e Hidratação)
    @Published public var caloriesConsumed: Double = 0
    @Published public var caloriesTarget: Double = 2500
    @Published public var waterConsumed: Int = 0
    @Published public var waterTarget: Int = 2500
    @Published public var proteinConsumed: Double = 0
    @Published public var proteinTarget: Double = 160
    @Published public var carbsConsumed: Double = 0
    @Published public var carbsTarget: Double = 280
    @Published public var fatConsumed: Double = 0
    @Published public var fatTarget: Double = 70
    @Published public var stepsCount: Int = 0

    private let healthStore = HKHealthStore()
    private var hkWorkoutSession: HKWorkoutSession?
    #if os(watchOS)
    private var hkWorkoutBuilder: HKLiveWorkoutBuilder?
    #endif
    private var restHapticTimer: Timer?

    private override init() {
        super.init()
        setupWatchConnectivity()
    }

    public func setupWatchConnectivity() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    // MARK: - Ações Enviadas do Pulso para o iPhone

    public func completeCurrentSet() {
        playWristHaptic(.success)
        if isLocalSimulation {
            lastCompletedSetNumber = currentSetNumber
            lastCompletedSetWeightKg = currentSetWeightKg
            completedSetsTotal = min(totalSetsTotal, completedSetsTotal + 1)
            if completedSetsTotal >= totalSetsTotal {
                isAllCompleted = true
                isResting = false
                restEndDate = nil
            } else {
                if currentSetNumber < totalSetsForExercise {
                    currentSetNumber += 1
                } else {
                    currentSetNumber = 1
                    currentExerciseName = currentExerciseName == "Supino Reto com Barra" ? "Crucifixo Inclinado" : "Tríceps Pulley Corda"
                }
                isResting = true
                let end = Date().addingTimeInterval(Double(restDurationSeconds))
                restEndDate = end
                scheduleWristRestCompletionHaptic(at: end)
            }
        }
        sendAction(["action": "complete_set"])
    }

    public func adjustRecentSetWeight(deltaKg: Double) {
        if isResting && lastCompletedSetNumber > 0 {
            lastCompletedSetWeightKg = max(0, ((lastCompletedSetWeightKg + deltaKg) * 2.0).rounded() / 2.0)
            currentSetWeightKg = lastCompletedSetWeightKg
        } else {
            currentSetWeightKg = max(0, ((currentSetWeightKg + deltaKg) * 2.0).rounded() / 2.0)
        }
        playWristHaptic(.click)
        sendAction([
            "action": "adjust_weight",
            "deltaKg": deltaKg
        ])
    }

    public func skipRest() {
        isResting = false
        restEndDate = nil
        playWristHaptic(.directionUp)
        sendAction(["action": "skip_rest"])
    }

    public func addRest30Seconds() {
        if let end = restEndDate {
            restEndDate = end.addingTimeInterval(30)
        }
        playWristHaptic(.click)
        sendAction(["action": "add_rest_30s"])
    }

    public func finishWorkout() {
        playWristHaptic(.success)
        if isLocalSimulation {
            isLocalSimulation = false
            hasActiveWorkout = false
            isResting = false
            restEndDate = nil
        }
        stopHardwareWorkoutSession()
        sendAction(["action": "finish_workout"])
    }

    @Published public var isLocalSimulation: Bool = false

    public func startLocalDemoWorkout() {
        isLocalSimulation = true
        hasActiveWorkout = true
        workoutTitle = "Peito & Tríceps"
        currentExerciseName = "Supino Reto com Barra"
        currentSetNumber = 1
        totalSetsForExercise = 4
        completedSetsTotal = 0
        totalSetsTotal = 12
        currentSetWeightKg = 30.0
        lastCompletedSetNumber = 0
        lastCompletedSetWeightKg = 30.0
        lastCompletedExerciseName = "Supino Reto com Barra"
        isResting = false
        restEndDate = nil
        restDurationSeconds = 90
        isAllCompleted = false
        playWristHaptic(.directionUp)
    }

    public func addWater(milliliters: Int = 250) {
        waterConsumed += milliliters
        playWristHaptic(.success)
        sendAction([
            "action": "add_water",
            "milliliters": milliliters
        ])
    }

    public func requestSync() {
        sendAction(["action": "request_sync"])
    }

    private func sendAction(_ payload: [String: Any]) {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        if session.isReachable {
            session.sendMessage(payload, replyHandler: { [weak self] reply in
                Task { @MainActor in
                    self?.applyPayload(reply)
                }
            }, errorHandler: { _ in
                session.transferUserInfo(payload)
            })
        } else {
            session.transferUserInfo(payload)
        }
    }

    // MARK: - Aplicação do Contexto Recebido do iPhone

    private func applyPayload(_ dict: [String: Any]) {
        if let calIn = dict["caloriesConsumed"] as? Double { caloriesConsumed = calIn }
        if let calTarget = dict["caloriesTarget"] as? Double { caloriesTarget = calTarget }
        if let wIn = dict["waterConsumed"] as? Int { waterConsumed = wIn }
        if let wTarget = dict["waterTarget"] as? Int { waterTarget = wTarget }
        if let pIn = dict["proteinConsumed"] as? Double { proteinConsumed = pIn }
        if let pTarget = dict["proteinTarget"] as? Double { proteinTarget = pTarget }
        if let cIn = dict["carbsConsumed"] as? Double { carbsConsumed = cIn }
        if let cTarget = dict["carbsTarget"] as? Double { carbsTarget = cTarget }
        if let fIn = dict["fatConsumed"] as? Double { fatConsumed = fIn }
        if let fTarget = dict["fatTarget"] as? Double { fatTarget = fTarget }
        if let steps = dict["stepsCount"] as? Int { stepsCount = steps }

        let active = (dict["hasActiveWorkout"] as? Bool) ?? false
        if active {
            isLocalSimulation = false
        } else if isLocalSimulation {
            return
        }
        let wasActive = hasActiveWorkout
        hasActiveWorkout = active

        if active {
            workoutTitle = (dict["workoutTitle"] as? String) ?? "Treino"
            currentExerciseName = (dict["currentExerciseName"] as? String) ?? "Exercício"
            currentSetNumber = (dict["currentSetNumber"] as? Int) ?? 1
            totalSetsForExercise = (dict["totalSetsForExercise"] as? Int) ?? 3
            completedSetsTotal = (dict["completedSetsTotal"] as? Int) ?? 0
            totalSetsTotal = (dict["totalSetsTotal"] as? Int) ?? 12
            currentSetWeightKg = (dict["currentSetWeightKg"] as? Double) ?? 20.0
            lastCompletedSetNumber = (dict["lastCompletedSetNumber"] as? Int) ?? 0
            lastCompletedSetWeightKg = (dict["lastCompletedSetWeightKg"] as? Double) ?? currentSetWeightKg
            lastCompletedExerciseName = (dict["lastCompletedExerciseName"] as? String) ?? currentExerciseName
            isResting = (dict["isResting"] as? Bool) ?? false
            restDurationSeconds = (dict["restDurationSeconds"] as? Int) ?? 90
            caloriesBurned = (dict["caloriesBurned"] as? Double) ?? 0
            isAllCompleted = (dict["isAllCompleted"] as? Bool) ?? false

            if let startTs = dict["workoutStartTimestamp"] as? Double, startTs > 0 {
                workoutStartDate = Date(timeIntervalSince1970: startTs)
            }

            if isResting, let endTs = dict["restEndTimestamp"] as? Double, endTs > Date().timeIntervalSince1970 {
                let end = Date(timeIntervalSince1970: endTs)
                restEndDate = end
                scheduleWristRestCompletionHaptic(at: end)
            } else {
                restEndDate = nil
                restHapticTimer?.invalidate()
            }

            if !wasActive {
                startHardwareWorkoutSessionIfAvailable()
            }
        } else if wasActive {
            stopHardwareWorkoutSession()
        }
    }

    // MARK: - Haptics no Pulso e Sessão HealthKit (Apple Watch)

    private func scheduleWristRestCompletionHaptic(at endDate: Date) {
        restHapticTimer?.invalidate()
        let interval = endDate.timeIntervalSinceNow
        guard interval > 0 else { return }
        restHapticTimer = Timer.scheduledTimer(withTimeInterval: interval, repeats: false) { [weak self] _ in
            Task { @MainActor in
                self?.isResting = false
                self?.restEndDate = nil
                self?.playWristHaptic(.notification)
            }
        }
    }

    public enum WristHapticType {
        case success, click, directionUp, notification
    }

    public func playWristHaptic(_ type: WristHapticType) {
        #if os(watchOS)
        let device = WKInterfaceDevice.current()
        switch type {
        case .success:
            device.play(.success)
        case .click:
            device.play(.click)
        case .directionUp:
            device.play(.directionUp)
        case .notification:
            device.play(.notification)
        }
        #endif
    }

    private func startHardwareWorkoutSessionIfAvailable() {
        #if os(watchOS)
        guard HKHealthStore.isHealthDataAvailable(), hkWorkoutSession == nil else { return }
        let config = HKWorkoutConfiguration()
        config.activityType = .traditionalStrengthTraining
        config.locationType = .indoor

        do {
            let session = try HKWorkoutSession(healthStore: healthStore, configuration: config)
            let builder = session.associatedWorkoutBuilder()
            builder.dataSource = HKLiveWorkoutDataSource(healthStore: healthStore, workoutConfiguration: config)
            session.startActivity(with: workoutStartDate)
            builder.beginCollection(withStart: workoutStartDate) { _, _ in }
            self.hkWorkoutSession = session
            self.hkWorkoutBuilder = builder
        } catch {
            print("⚠️ Não foi possível iniciar HKWorkoutSession no Apple Watch: \(error)")
        }
        #endif
    }

    private func stopHardwareWorkoutSession() {
        #if os(watchOS)
        hkWorkoutSession?.end()
        hkWorkoutBuilder?.endCollection(withEnd: Date()) { [weak self] _, _ in
            self?.hkWorkoutBuilder?.finishWorkout { _, _ in }
        }
        hkWorkoutSession = nil
        hkWorkoutBuilder = nil
        #endif
    }
}

extension WatchSessionController: WCSessionDelegate {
    nonisolated public func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: Error?
    ) {
        let context = session.receivedApplicationContext
        Task { @MainActor in
            if !context.isEmpty {
                self.applyPayload(context)
            }
            self.requestSync()
        }
    }

    nonisolated public func session(_ session: WCSession, didReceiveApplicationContext applicationContext: [String: Any]) {
        Task { @MainActor in
            self.applyPayload(applicationContext)
        }
    }

    nonisolated public func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        Task { @MainActor in
            self.applyPayload(message)
        }
    }

    nonisolated public func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        Task { @MainActor in
            self.applyPayload(userInfo)
        }
    }
}

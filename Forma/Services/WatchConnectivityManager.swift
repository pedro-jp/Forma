//
//  WatchConnectivityManager.swift
//  Forma
//
//  Sincronização bidirecional em tempo real entre iPhone e Apple Watch (WatchConnectivity).
//  Envia treino ativo, série atual, carga (kg), cronômetro de descanso, água e calorias,
//  e recebe comandos do pulso (+2kg/-2kg, concluir série, pular descanso, +250ml de água).
//

import Foundation
import WatchConnectivity
import SwiftData
import Combine

@MainActor
public final class WatchConnectivityManager: NSObject, ObservableObject {
    public static let shared = WatchConnectivityManager()

    @Published public private(set) var isWatchPaired: Bool = false
    @Published public private(set) var isWatchReachable: Bool = false

    private override init() {
        super.init()
    }

    public func setup() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        session.delegate = self
        session.activate()
    }

    /// Constrói e envia o estado completo (Treino Ativo + Calorias/Água do Dia) para o Apple Watch
    public func syncContextToWatch() {
        guard WCSession.isSupported() else { return }
        let session = WCSession.default
        guard session.activationState == .activated else { return }

        let payload = buildWatchContextPayload()

        try? session.updateApplicationContext(payload)
        if session.isReachable {
            session.sendMessage(payload, replyHandler: nil, errorHandler: nil)
        }
    }

    private func buildWatchContextPayload() -> [String: Any] {
        let widgetData = WidgetDataManager.shared.loadWidgetData()
        var dict: [String: Any] = [
            "timestamp": Date().timeIntervalSince1970,
            "caloriesConsumed": widgetData.caloriesConsumed,
            "caloriesTarget": widgetData.caloriesTarget,
            "waterConsumed": widgetData.waterConsumed,
            "waterTarget": widgetData.waterTarget,
            "proteinConsumed": widgetData.proteinConsumed,
            "proteinTarget": widgetData.proteinTarget,
            "carbsConsumed": widgetData.carbsConsumed,
            "carbsTarget": widgetData.carbsTarget,
            "fatConsumed": widgetData.fatConsumed,
            "fatTarget": widgetData.fatTarget,
            "stepsCount": widgetData.stepsCount
        ]

        if let workout = SharedWorkoutSessionManager.shared.loadSession(), !workout.isFinished {
            let curEx = workout.currentExercise
            let curExName = curEx?.name ?? "Treino"
            let curSetNumber = min(workout.currentSetIndex + 1, curEx?.sets ?? 3)
            let totalSetsForEx = curEx?.sets ?? 3
            let lastInfo = workout.lastCompletedSetInfo
            dict["hasActiveWorkout"] = true
            dict["workoutTitle"] = workout.title
            dict["currentExerciseName"] = curExName
            dict["currentSetNumber"] = curSetNumber
            dict["totalSetsForExercise"] = totalSetsForEx
            dict["completedSetsTotal"] = workout.completedSetsCount
            dict["totalSetsTotal"] = workout.totalSetsCount
            dict["currentSetWeightKg"] = workout.currentSetWeightKg
            dict["lastCompletedSetNumber"] = lastInfo?.setNumber ?? 0
            dict["lastCompletedSetWeightKg"] = lastInfo?.weightKg ?? workout.currentSetWeightKg
            dict["lastCompletedExerciseName"] = lastInfo?.exerciseName ?? curExName
            dict["isResting"] = workout.isResting
            dict["restEndTimestamp"] = workout.restEndDate?.timeIntervalSince1970 ?? 0.0
            dict["restDurationSeconds"] = workout.restDurationSeconds
            dict["workoutStartTimestamp"] = workout.sessionStartDate.timeIntervalSince1970
            dict["caloriesBurned"] = workout.liveCalories
            dict["isAllCompleted"] = workout.isAllCompleted
        } else {
            dict["hasActiveWorkout"] = false
        }

        return dict
    }

    private func handleIncomingWatchAction(_ message: [String: Any]) {
        guard let action = message["action"] as? String else { return }

        switch action {
        case "complete_set":
            Task { @MainActor in
                await SharedWorkoutSessionManager.shared.completeCurrentSetFromLiveActivity()
                self.syncContextToWatch()
            }

        case "adjust_weight":
            let deltaKg = (message["deltaKg"] as? Double) ?? 0.0
            if deltaKg != 0 {
                Task { @MainActor in
                    await SharedWorkoutSessionManager.shared.adjustRecentSetWeightFromLiveActivity(deltaKg: deltaKg)
                    self.syncContextToWatch()
                }
            }

        case "skip_rest":
            Task { @MainActor in
                await SharedWorkoutSessionManager.shared.skipRestFromLiveActivity()
                self.syncContextToWatch()
            }

        case "add_rest_30s":
            Task { @MainActor in
                await SharedWorkoutSessionManager.shared.addRestSecondsFromLiveActivity(seconds: 30)
                self.syncContextToWatch()
            }

        case "finish_workout":
            Task { @MainActor in
                await SharedWorkoutSessionManager.shared.finishWorkoutFromLiveActivity()
                self.syncContextToWatch()
            }

        case "add_water":
            let ml = (message["milliliters"] as? Int) ?? 250
            let entry = WaterEntry(milliliters: ml, date: Date())
            Task { @MainActor in
                do {
                    try await FormaDataStore.shared.insertWaterEntryRemoteFirst(entry)
                } catch {
                    FormaDataStore.shared.mainContext.insert(entry)
                    try? FormaDataStore.shared.mainContext.save()
                    FormaDataStore.shared.syncTodayData()
                }
                self.syncContextToWatch()
            }

        case "request_sync":
            syncContextToWatch()

        default:
            break
        }
    }
}

extension WatchConnectivityManager: WCSessionDelegate {
    nonisolated public func session(
        _ session: WCSession,
        activationDidCompleteWith activationState: WCSessionActivationState,
        error: Error?
    ) {
        let paired = session.isPaired
        let reachable = session.isReachable
        Task { @MainActor in
            self.isWatchPaired = paired
            self.isWatchReachable = reachable
            if activationState == .activated {
                self.syncContextToWatch()
            }
        }
    }

    nonisolated public func sessionDidBecomeInactive(_ session: WCSession) {}

    nonisolated public func sessionDidDeactivate(_ session: WCSession) {
        session.activate()
    }

    nonisolated public func sessionReachabilityDidChange(_ session: WCSession) {
        let reachable = session.isReachable
        Task { @MainActor in
            self.isWatchReachable = reachable
            if reachable {
                self.syncContextToWatch()
            }
        }
    }

    nonisolated public func session(_ session: WCSession, didReceiveMessage message: [String: Any]) {
        Task { @MainActor in
            self.handleIncomingWatchAction(message)
        }
    }

    nonisolated public func session(
        _ session: WCSession,
        didReceiveMessage message: [String: Any],
        replyHandler: @escaping ([String: Any]) -> Void
    ) {
        Task { @MainActor in
            self.handleIncomingWatchAction(message)
            let updatedContext = self.buildWatchContextPayload()
            replyHandler(updatedContext)
        }
    }

    nonisolated public func session(_ session: WCSession, didReceiveUserInfo userInfo: [String: Any] = [:]) {
        Task { @MainActor in
            self.handleIncomingWatchAction(userInfo)
        }
    }
}

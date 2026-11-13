//
//  BackendSyncManager.swift
//  Forma
//
//  Sincronização Remote-First (Banco Supabase Primeiro -> Cache SwiftData Local Depois)
//  com isolamento multi-usuário estrito.
//

import Foundation
import SwiftData
import Combine

public struct RemoteProfilePayload: Sendable {
    public var name: String?
    public var goal: String
    public var targetCalorieIntake: Double
    public var targetCalorieBurn: Double
    public var targetSteps: Double
    public var targetWeight: Double
    public var targetProtein: Double
    public var targetCarbohydrates: Double
    public var targetFat: Double
    public var targetWaterMilliliters: Int
    public var heightMeters: Double
    public var currentWeight: Double
    public var onboardingCompleted: Bool

    public init(
        name: String?,
        goal: String,
        targetCalorieIntake: Double,
        targetCalorieBurn: Double,
        targetSteps: Double,
        targetWeight: Double,
        targetProtein: Double,
        targetCarbohydrates: Double,
        targetFat: Double,
        targetWaterMilliliters: Int,
        heightMeters: Double,
        currentWeight: Double,
        onboardingCompleted: Bool
    ) {
        self.name = name
        self.goal = goal
        self.targetCalorieIntake = targetCalorieIntake
        self.targetCalorieBurn = targetCalorieBurn
        self.targetSteps = targetSteps
        self.targetWeight = targetWeight
        self.targetProtein = targetProtein
        self.targetCarbohydrates = targetCarbohydrates
        self.targetFat = targetFat
        self.targetWaterMilliliters = targetWaterMilliliters
        self.heightMeters = heightMeters
        self.currentWeight = currentWeight
        self.onboardingCompleted = onboardingCompleted
    }

    @MainActor
    static func from(profile: Profile, overrideName: String? = nil) -> RemoteProfilePayload {
        let resolvedName = overrideName ?? (profile.nome.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? AuthManager.shared.currentUserName : profile.nome)
        return RemoteProfilePayload(
            name: resolvedName,
            goal: profile.goalRaw,
            targetCalorieIntake: profile.targetCalorieIntake,
            targetCalorieBurn: profile.targetCalorieBurn,
            targetSteps: profile.targetSteps,
            targetWeight: profile.targetWeight,
            targetProtein: profile.targetProtein,
            targetCarbohydrates: profile.targetCarbohydrates,
            targetFat: profile.targetFat,
            targetWaterMilliliters: profile.targetWaterMilliliters,
            heightMeters: profile.height,
            currentWeight: profile.currentWeight,
            onboardingCompleted: profile.onboardingCompleted
        )
    }

    public func toDictionary() -> [String: Any] {
        var dict: [String: Any] = [
            "goal": goal,
            "targetCalorieIntake": targetCalorieIntake,
            "targetCalorieBurn": targetCalorieBurn,
            "targetSteps": targetSteps,
            "targetWeight": targetWeight,
            "targetProtein": targetProtein,
            "targetCarbohydrates": targetCarbohydrates,
            "targetFat": targetFat,
            "targetWaterMilliliters": targetWaterMilliliters,
            "heightMeters": heightMeters,
            "currentWeight": currentWeight,
            "onboardingCompleted": onboardingCompleted
        ]
        if let name, !name.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            dict["name"] = name.trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return dict
    }
}

@MainActor
public final class BackendSyncManager: ObservableObject {
    public static let shared = BackendSyncManager()
    public static let activeLocalUserIdKey = "forma_active_local_user_id"

    private let lastSyncKey = "forma_last_sync_timestamp"
    private let requiresInitialPullKey = "forma_requires_initial_pull"
    private let deletedFoodIdsKey = "forma_deleted_food_ids"
    private let deletedWorkoutIdsKey = "forma_deleted_workout_ids"
    private let deletedWaterIdsKey = "forma_deleted_water_ids"
    private let deletedWeightIdsKey = "forma_deleted_weight_ids"
    private let deletedRoutineIdsKey = "forma_deleted_routine_ids"
    private var debouncedSyncTask: Task<Void, Never>? = nil

    @Published public var isSyncing: Bool = false
    @Published public var lastSyncDate: Date? = nil
    @Published public var lastSyncError: String? = nil
    @Published public var hasCompletedInitialSessionPull: Bool = false

    private static let isoFormatter: ISO8601DateFormatter = {
        let f = ISO8601DateFormatter()
        f.formatOptions = [.withInternetDateTime, .withFractionalSeconds]
        return f
    }()

    private static let fallbackIsoFormatter = ISO8601DateFormatter()

    private init() {
        if let timestamp = UserDefaults.standard.object(forKey: lastSyncKey) as? Date {
            self.lastSyncDate = timestamp
        }
    }

    public func cancelPendingSyncs() {
        debouncedSyncTask?.cancel()
        debouncedSyncTask = nil
    }

    public func markRequiresInitialPull(_ value: Bool) {
        UserDefaults.standard.set(value, forKey: requiresInitialPullKey)
    }

    // MARK: - Rastreamento de Exclusões Pendentes (Tombstones de Segurança)

    public func recordDeletedFoodIds(_ ids: [UUID]) {
        appendDeletedIds(ids, forKey: deletedFoodIdsKey)
    }

    public func recordDeletedWorkoutIds(_ ids: [UUID]) {
        appendDeletedIds(ids, forKey: deletedWorkoutIdsKey)
    }

    public func recordDeletedWaterIds(_ ids: [UUID]) {
        appendDeletedIds(ids, forKey: deletedWaterIdsKey)
    }

    public func recordDeletedWeightIds(_ ids: [UUID]) {
        appendDeletedIds(ids, forKey: deletedWeightIdsKey)
    }

    public func recordDeletedRoutineIds(_ ids: [UUID]) {
        appendDeletedIds(ids, forKey: deletedRoutineIdsKey)
    }

    public func pendingDeletedFoodIds() -> Set<String> {
        loadDeletedIdsSet(forKey: deletedFoodIdsKey)
    }

    public func pendingDeletedWorkoutIds() -> Set<String> {
        loadDeletedIdsSet(forKey: deletedWorkoutIdsKey)
    }

    public func clearAllPendingDeletions() {
        for key in [deletedFoodIdsKey, deletedWorkoutIdsKey, deletedWaterIdsKey, deletedWeightIdsKey, deletedRoutineIdsKey] {
            UserDefaults.standard.removeObject(forKey: key)
        }
    }

    private func appendDeletedIds(_ ids: [UUID], forKey key: String) {
        guard !ids.isEmpty else { return }
        var current = loadDeletedIdsSet(forKey: key)
        for id in ids {
            current.insert(id.uuidString.lowercased())
        }
        UserDefaults.standard.set(Array(current), forKey: key)
    }

    private func loadDeletedIdsSet(forKey key: String) -> Set<String> {
        let raw = UserDefaults.standard.stringArray(forKey: key) ?? []
        return Set(raw.map { $0.lowercased() })
    }

    // MARK: - Operações Atômicas Remote-First (Grava no Supabase Primeiro!)

    public func pushProfileRemoteFirst(_ payload: RemoteProfilePayload) async throws {
        guard AuthManager.shared.isAuthenticated else { return }
        _ = try await FormaAPIClient.shared.requestData(
            path: "/api/sync/profile",
            method: "PUT",
            jsonBody: payload.toDictionary(),
            authenticated: true,
            timeout: 20.0
        )
        markSyncedNow()
    }

    func pushFoodsRemoteFirst(_ entries: [FoodEntry]) async throws {
        guard AuthManager.shared.isAuthenticated, !entries.isEmpty else { return }
        let foodsArray: [[String: Any]] = entries.map { f in
            [
                "id": f.id.uuidString,
                "name": f.name,
                "brand": f.brand,
                "mealType": f.mealTypeRaw,
                "portionGrams": f.portionGrams,
                "calories": f.calories,
                "protein": f.protein,
                "carbohydrates": f.carbohydrates,
                "fat": f.fat,
                "consumedAt": Self.isoFormatter.string(from: f.date)
            ]
        }
        let logsArray: [[String: Any]] = entries.map { f in
            [
                "id": f.id.uuidString,
                "category": "meal",
                "title": "\(f.mealType.rawValue): \(f.name)",
                "isSnack": f.mealType == .snacks,
                "caloriesChange": f.calories,
                "proteinGrams": f.protein,
                "carbsGrams": f.carbohydrates,
                "fatGrams": f.fat,
                "timestamp": Self.isoFormatter.string(from: f.date)
            ]
        }
        _ = try await FormaAPIClient.shared.requestData(
            path: "/api/sync/foods",
            method: "POST",
            jsonBody: [
                "foods": foodsArray,
                "activityLogs": logsArray
            ],
            authenticated: true,
            timeout: 20.0
        )
        markSyncedNow()
    }

    public func deleteFoodsRemoteFirst(_ ids: [UUID]) async throws {
        guard AuthManager.shared.isAuthenticated, !ids.isEmpty else { return }
        if ids.count == 1, let single = ids.first {
            _ = try await FormaAPIClient.shared.requestData(
                path: "/api/sync/foods/\(single.uuidString)",
                method: "DELETE",
                authenticated: true,
                timeout: 20.0
            )
        } else {
            _ = try await FormaAPIClient.shared.requestData(
                path: "/api/sync/foods/delete-batch",
                method: "POST",
                jsonBody: ["ids": ids.map(\.uuidString)],
                authenticated: true,
                timeout: 20.0
            )
        }
        markSyncedNow()
    }

    func pushWorkoutRemoteFirst(_ entry: WorkoutEntry, updatedRoutine: WorkoutRoutine? = nil) async throws {
        guard AuthManager.shared.isAuthenticated else { return }
        let workoutDict: [String: Any] = [
            "id": entry.id.uuidString,
            "title": entry.title,
            "workoutType": entry.workoutTypeRaw,
            "durationMinutes": entry.durationMinutes,
            "caloriesBurned": entry.caloriesBurned,
            "notes": entry.notes,
            "workoutDate": Self.isoFormatter.string(from: entry.date)
        ]
        var logDict: [String: Any] = [
            "id": entry.id.uuidString,
            "category": "workout",
            "title": "\(entry.workoutTypeRaw): \(entry.title)",
            "caloriesChange": -abs(entry.caloriesBurned),
            "timestamp": Self.isoFormatter.string(from: entry.date)
        ]
        if !entry.notes.isEmpty {
            logDict["details"] = entry.notes
        }

        var body: [String: Any] = [
            "workout": workoutDict,
            "activityLog": logDict
        ]
        if let r = updatedRoutine {
            body["updatedRoutine"] = serializeRoutine(r)
        }

        _ = try await FormaAPIClient.shared.requestData(
            path: "/api/sync/workouts",
            method: "POST",
            jsonBody: body,
            authenticated: true,
            timeout: 20.0
        )
        markSyncedNow()
    }

    public func deleteWorkoutRemoteFirst(_ id: UUID) async throws {
        guard AuthManager.shared.isAuthenticated else { return }
        _ = try await FormaAPIClient.shared.requestData(
            path: "/api/sync/workouts/\(id.uuidString)",
            method: "DELETE",
            authenticated: true,
            timeout: 20.0
        )
        markSyncedNow()
    }

    func pushRoutineRemoteFirst(_ routine: WorkoutRoutine) async throws {
        guard AuthManager.shared.isAuthenticated else { return }
        _ = try await FormaAPIClient.shared.requestData(
            path: "/api/sync/routines",
            method: "POST",
            jsonBody: serializeRoutine(routine),
            authenticated: true,
            timeout: 20.0
        )
        markSyncedNow()
    }

    public func deleteRoutineRemoteFirst(_ id: UUID) async throws {
        guard AuthManager.shared.isAuthenticated else { return }
        _ = try await FormaAPIClient.shared.requestData(
            path: "/api/sync/routines/\(id.uuidString)",
            method: "DELETE",
            authenticated: true,
            timeout: 20.0
        )
        markSyncedNow()
    }

    func pushWaterRemoteFirst(_ entry: WaterEntry) async throws {
        guard AuthManager.shared.isAuthenticated else { return }
        let waterDict: [String: Any] = [
            "id": entry.id.uuidString,
            "milliliters": entry.milliliters,
            "entryDate": Self.isoFormatter.string(from: entry.date)
        ]
        let logDict: [String: Any] = [
            "id": entry.id.uuidString,
            "category": "water",
            "title": "Hidratação (+\(entry.milliliters) ml)",
            "caloriesChange": 0.0,
            "waterMilliliters": entry.milliliters,
            "timestamp": Self.isoFormatter.string(from: entry.date)
        ]
        _ = try await FormaAPIClient.shared.requestData(
            path: "/api/sync/waters",
            method: "POST",
            jsonBody: [
                "water": waterDict,
                "activityLog": logDict
            ],
            authenticated: true,
            timeout: 20.0
        )
        markSyncedNow()
    }

    public func deleteWaterRemoteFirst(_ id: UUID) async throws {
        guard AuthManager.shared.isAuthenticated else { return }
        _ = try await FormaAPIClient.shared.requestData(
            path: "/api/sync/waters/\(id.uuidString)",
            method: "DELETE",
            authenticated: true,
            timeout: 20.0
        )
        markSyncedNow()
    }

    public func pushWeightRemoteFirst(
        id: UUID,
        weight: Double,
        date: Date,
        notes: String,
        profilePayload: RemoteProfilePayload?
    ) async throws {
        guard AuthManager.shared.isAuthenticated else { return }
        let weightDict: [String: Any] = [
            "id": id.uuidString,
            "weight": weight,
            "entryDate": Self.isoFormatter.string(from: date)
        ]
        var logDict: [String: Any] = [
            "id": id.uuidString,
            "category": "weight",
            "title": String(format: "Pesagem (%.1f kg)", weight),
            "caloriesChange": 0.0,
            "weightKg": weight,
            "timestamp": Self.isoFormatter.string(from: date)
        ]
        if !notes.isEmpty {
            logDict["details"] = notes
        }
        var body: [String: Any] = [
            "weight": weightDict,
            "activityLog": logDict
        ]
        if let profilePayload {
            body["profileUpdate"] = profilePayload.toDictionary()
        }

        _ = try await FormaAPIClient.shared.requestData(
            path: "/api/sync/weights",
            method: "POST",
            jsonBody: body,
            authenticated: true,
            timeout: 20.0
        )
        markSyncedNow()
    }

    public func deleteWeightRemoteFirst(_ id: UUID, updatedProfile: RemoteProfilePayload? = nil) async throws {
        guard AuthManager.shared.isAuthenticated else { return }
        _ = try await FormaAPIClient.shared.requestData(
            path: "/api/sync/weights/\(id.uuidString)",
            method: "DELETE",
            authenticated: true,
            timeout: 20.0
        )
        if let updatedProfile {
            try await pushProfileRemoteFirst(updatedProfile)
        } else {
            markSyncedNow()
        }
    }

    public func pushAnalysisRemoteFirst(_ analysis: EvolutionAIAnalysis) async throws {
        guard AuthManager.shared.isAuthenticated else { return }
        let encoder = JSONEncoder()
        let workoutSugg = (try? String(data: encoder.encode(analysis.workoutSuggestions), encoding: .utf8)) ?? "[]"
        let nutrSugg = (try? String(data: encoder.encode(analysis.nutritionSuggestions), encoding: .utf8)) ?? "[]"
        let targetsJson = analysis.suggestedTargets != nil ? (try? String(data: encoder.encode(analysis.suggestedTargets!), encoding: .utf8)) : nil
        let routineJson = analysis.recommendedRoutine != nil ? (try? String(data: encoder.encode(analysis.recommendedRoutine!), encoding: .utf8)) : nil

        var aDict: [String: Any] = [
            "id": analysis.id.uuidString,
            "statusSummary": analysis.statusSummary,
            "calorieDiagnostic": analysis.calorieDiagnostic,
            "macroDiagnostic": analysis.macroDiagnostic,
            "hydrationDiagnostic": analysis.hydrationDiagnostic,
            "workoutSuggestionsJson": workoutSugg,
            "nutritionSuggestionsJson": nutrSugg,
            "weeklyActionPlan": analysis.weeklyActionPlan,
            "createdAt": Self.isoFormatter.string(from: analysis.generatedAt)
        ]
        if let tj = targetsJson { aDict["suggestedTargetsJson"] = tj }
        if let rj = routineJson { aDict["recommendedRoutineJson"] = rj }

        _ = try await FormaAPIClient.shared.requestData(
            path: "/api/sync/analyses",
            method: "POST",
            jsonBody: aDict,
            authenticated: true,
            timeout: 20.0
        )
        markSyncedNow()
    }

    private func serializeRoutine(_ r: WorkoutRoutine) -> [String: Any] {
        var dict: [String: Any] = [
            "id": r.id.uuidString,
            "title": r.title,
            "workoutType": r.workoutTypeRaw,
            "targetMuscles": r.targetMuscles,
            "hypertrophyFocus": r.hypertrophyFocus,
            "estimatedDurationMinutes": r.estimatedDurationMinutes,
            "estimatedCalories": r.estimatedCalories,
            "recommendedRestSeconds": r.recommendedRestSeconds,
            "isFavorite": r.isFavorite,
            "exercisesJson": r.exercisesJson,
            "notes": r.notes,
            "createdAt": Self.isoFormatter.string(from: r.createdAt)
        ]
        if let lastTrained = r.lastTrainedAt {
            dict["lastTrainedAt"] = Self.isoFormatter.string(from: lastTrained)
        }
        return dict
    }

    private func markSyncedNow() {
        let now = Date()
        self.lastSyncDate = now
        self.lastSyncError = nil
        UserDefaults.standard.set(now, forKey: lastSyncKey)
    }

    // MARK: - Pull Inicial Limpo no Login / Restauração de Sessão

    public func pullInitialStateForAuthenticatedUser(context: ModelContext) async throws {
        guard let token = AuthManager.shared.jwtToken,
              let userId = AuthManager.shared.currentUserId else {
            hasCompletedInitialSessionPull = true
            return
        }
        isSyncing = true
        defer {
            isSyncing = false
            hasCompletedInitialSessionPull = true
        }
        try await pullRemoteData(context: context, token: token, userId: userId, replaceLocalState: true)
        markRequiresInitialPull(false)
        FormaDataStore.shared.syncTodayData(skipCloudSyncTrigger: true)
        markSyncedNow()
    }

    /// Sincroniza alterações do perfil/metas no banco primeiro com pequeno debounce (ex: sliders na aba Perfil)
    public func triggerAutoSync() {
        guard AuthManager.shared.isAuthenticated else { return }

        debouncedSyncTask?.cancel()
        debouncedSyncTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: 800_000_000)
            guard !Task.isCancelled, let self else { return }
            if let profile = try? FormaDataStore.shared.mainContext.fetch(FetchDescriptor<Profile>()).first,
               profile.onboardingCompleted {
                let payload = RemoteProfilePayload.from(profile: profile)
                try? await self.pushProfileRemoteFirst(payload)
            }
        }
    }

    // MARK: - Sincronização de Sessão (Pull do Banco -> Atualiza Cache Local se Necessário)

    public func sync(context: ModelContext, forcePull: Bool = false) async {
        guard AuthManager.shared.isAuthenticated,
              let token = AuthManager.shared.jwtToken,
              let userId = AuthManager.shared.currentUserId else {
            return
        }

        let activeLocalUserId = UserDefaults.standard.string(forKey: Self.activeLocalUserIdKey)
        if activeLocalUserId != userId {
            // Conta diferente detectada: limpa cache local totalmente antes de baixar do Supabase
            clearAllPendingDeletions()
            FormaDataStore.shared.resetLocalDataForUserSwitch(
                userName: AuthManager.shared.currentUserName,
                userEmail: AuthManager.shared.currentUserEmail
            )
            UserDefaults.standard.set(userId, forKey: Self.activeLocalUserIdKey)
            markRequiresInitialPull(true)
        }

        let needsInitialPull = UserDefaults.standard.bool(forKey: requiresInitialPullKey)
        if !forcePull && !needsInitialPull && hasCompletedInitialSessionPull {
            if let last = lastSyncDate, Date().timeIntervalSince(last) < 45.0 {
                // Evita recarregar repetidamente do banco quando o cache local já está sincronizado
                return
            }
        }

        isSyncing = true
        lastSyncError = nil
        defer {
            isSyncing = false
            hasCompletedInitialSessionPull = true
        }

        do {
            try await pullRemoteData(context: context, token: token, userId: userId, replaceLocalState: true)
            markRequiresInitialPull(false)
            FormaDataStore.shared.syncTodayData(skipCloudSyncTrigger: true)
            markSyncedNow()
        } catch {
            self.lastSyncError = error.localizedDescription
            print("⚠️ [Forma Sync] Erro ao atualizar cache local a partir do Supabase: \(error.localizedDescription)")
        }
    }

    // MARK: - Pull (Supabase -> Cache SwiftData no iPhone)

    private func pullRemoteData(
        context: ModelContext,
        token: String,
        userId: String,
        replaceLocalState: Bool
    ) async throws {
        let encodedUserId = userId.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? userId
        let data: Data
        do {
            data = try await FormaAPIClient.shared.requestData(
                path: "/api/sync/pull?userId=\(encodedUserId)",
                method: "GET",
                authenticated: true,
                timeout: 25.0
            )
        } catch FormaAPIError.accountDeleted {
            return
        }

        guard let json = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return
        }

        if let remoteTier = json["accountTier"] as? String {
            AuthManager.shared.updateAccountTier(remoteTier)
        }

        let resetAckKey = "forma_reset_ack_\(userId)"
        let localAck = UserDefaults.standard.object(forKey: resetAckKey) as? Int64 ?? 0
        if let forceResetNum = json["forceResetEpochMs"] as? NSNumber {
            let serverResetEpoch = forceResetNum.int64Value
            if serverResetEpoch > localAck {
                clearAllPendingDeletions()
                FormaDataStore.shared.resetLocalDataForUserSwitch(
                    userName: AuthManager.shared.currentUserName,
                    userEmail: AuthManager.shared.currentUserEmail
                )
                UserDefaults.standard.set(serverResetEpoch, forKey: resetAckKey)
            }
        }

        // 1. Atualiza Profile com os dados reais do Supabase
        let allProfiles = (try? context.fetch(FetchDescriptor<Profile>())) ?? []
        if allProfiles.count > 1 {
            for extra in allProfiles.dropFirst() {
                context.delete(extra)
            }
        }
        let targetProfile: Profile
        if let existing = allProfiles.first {
            targetProfile = existing
        } else {
            let fresh = Profile(
                nome: AuthManager.shared.currentUserName ?? "",
                email: AuthManager.shared.currentUserEmail ?? ""
            )
            context.insert(fresh)
            targetProfile = fresh
        }

        if let authName = AuthManager.shared.currentUserName, !authName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            targetProfile.nome = authName
        }
        if let authEmail = AuthManager.shared.currentUserEmail, !authEmail.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            targetProfile.email = authEmail
        }
        if let profileDict = json["profile"] as? [String: Any] {
            if let g = profileDict["goal"] as? String { targetProfile.goalRaw = g }
            if let ci = profileDict["targetCalorieIntake"] as? Double { targetProfile.targetCalorieIntake = ci }
            if let cb = profileDict["targetCalorieBurn"] as? Double { targetProfile.targetCalorieBurn = cb }
            if let ts = profileDict["targetSteps"] as? Double { targetProfile.targetSteps = ts }
            if let tw = profileDict["targetWeight"] as? Double { targetProfile.targetWeight = tw }
            if let tp = profileDict["targetProtein"] as? Double { targetProfile.targetProtein = tp }
            if let tc = profileDict["targetCarbohydrates"] as? Double { targetProfile.targetCarbohydrates = tc }
            if let tf = profileDict["targetFat"] as? Double { targetProfile.targetFat = tf }
            if let twm = profileDict["targetWaterMilliliters"] as? Int { targetProfile.targetWaterMilliliters = twm }
            if let hm = profileDict["heightMeters"] as? Double { targetProfile.height = hm }
            if let cw = profileDict["currentWeight"] as? Double { targetProfile.currentWeight = cw }
            if let onb = profileDict["onboardingCompleted"] as? Bool {
                targetProfile.onboardingCompleted = onb
            }
        }

        // 2. Sincroniza Foods (espelho fiel do banco)
        if let foods = json["foods"] as? [[String: Any]] {
            let existingFoods = (try? context.fetch(FetchDescriptor<FoodEntry>())) ?? []
            var existingMap = Dictionary(uniqueKeysWithValues: existingFoods.map { ($0.id.uuidString.lowercased(), $0) })
            var remoteIds = Set<String>()

            for f in foods {
                guard let idStr = f["id"] as? String, let id = UUID(uuidString: idStr) else { continue }
                let key = idStr.lowercased()
                remoteIds.insert(key)

                let name = f["name"] as? String ?? "Alimento"
                let brand = f["brand"] as? String ?? ""
                let mealTypeRaw = f["mealType"] as? String ?? "Almoço"
                let portion = f["portionGrams"] as? Double ?? 100.0
                let cal = f["calories"] as? Double ?? 0.0
                let prot = f["protein"] as? Double ?? 0.0
                let carbs = f["carbohydrates"] as? Double ?? 0.0
                let fat = f["fat"] as? Double ?? 0.0
                let dateStr = f["consumedAt"] as? String ?? ""
                let date = Self.isoFormatter.date(from: dateStr) ?? Self.fallbackIsoFormatter.date(from: dateStr) ?? Date()

                if let existing = existingMap[key] {
                    existing.name = name
                    existing.brand = brand
                    existing.mealTypeRaw = mealTypeRaw
                    existing.portionGrams = portion
                    existing.calories = cal
                    existing.protein = prot
                    existing.carbohydrates = carbs
                    existing.fat = fat
                    existing.date = date
                } else {
                    let entry = FoodEntry(
                        id: id,
                        name: name,
                        brand: brand,
                        mealType: MealType(rawValue: mealTypeRaw) ?? .lunch,
                        portionGrams: portion,
                        calories: cal,
                        protein: prot,
                        carbohydrates: carbs,
                        fat: fat,
                        date: date,
                        createdAt: date
                    )
                    context.insert(entry)
                }
            }

            if replaceLocalState {
                for (key, localItem) in existingMap where !remoteIds.contains(key) {
                    context.delete(localItem)
                }
            }
        }

        // 3. Sincroniza Workouts
        if let workouts = json["workouts"] as? [[String: Any]] {
            let existingWorkouts = (try? context.fetch(FetchDescriptor<WorkoutEntry>())) ?? []
            let existingMap = Dictionary(uniqueKeysWithValues: existingWorkouts.map { ($0.id.uuidString.lowercased(), $0) })
            var remoteIds = Set<String>()

            for w in workouts {
                guard let idStr = w["id"] as? String, let id = UUID(uuidString: idStr) else { continue }
                let key = idStr.lowercased()
                remoteIds.insert(key)

                let title = w["title"] as? String ?? "Treino"
                let workoutTypeRaw = w["workoutType"] as? String ?? "Musculação"
                let duration = w["durationMinutes"] as? Int ?? 45
                let cals = w["caloriesBurned"] as? Double ?? 300.0
                let notes = w["notes"] as? String ?? ""
                let dateStr = w["workoutDate"] as? String ?? ""
                let date = Self.isoFormatter.date(from: dateStr) ?? Self.fallbackIsoFormatter.date(from: dateStr) ?? Date()

                if let existing = existingMap[key] {
                    existing.title = title
                    existing.workoutTypeRaw = workoutTypeRaw
                    existing.durationMinutes = duration
                    existing.caloriesBurned = cals
                    existing.notes = notes
                    existing.date = date
                } else {
                    let entry = WorkoutEntry(
                        id: id,
                        title: title,
                        workoutType: WorkoutType(rawValue: workoutTypeRaw) ?? .bodybuilding,
                        durationMinutes: duration,
                        caloriesBurned: cals,
                        date: date,
                        notes: notes,
                        createdAt: date
                    )
                    context.insert(entry)
                }
            }

            if replaceLocalState {
                for (key, localItem) in existingMap where !remoteIds.contains(key) {
                    context.delete(localItem)
                }
            }
        }

        // 4. Sincroniza Waters
        if let waters = json["waters"] as? [[String: Any]] {
            let existingWaters = (try? context.fetch(FetchDescriptor<WaterEntry>())) ?? []
            let existingMap = Dictionary(uniqueKeysWithValues: existingWaters.map { ($0.id.uuidString.lowercased(), $0) })
            var remoteIds = Set<String>()

            for wa in waters {
                guard let idStr = wa["id"] as? String, let id = UUID(uuidString: idStr) else { continue }
                let key = idStr.lowercased()
                remoteIds.insert(key)

                let ml = wa["milliliters"] as? Int ?? 250
                let dateStr = wa["entryDate"] as? String ?? ""
                let date = Self.isoFormatter.date(from: dateStr) ?? Self.fallbackIsoFormatter.date(from: dateStr) ?? Date()

                if let existing = existingMap[key] {
                    existing.milliliters = ml
                    existing.date = date
                } else {
                    let entry = WaterEntry(id: id, milliliters: ml, date: date, createdAt: date)
                    context.insert(entry)
                }
            }

            if replaceLocalState {
                for (key, localItem) in existingMap where !remoteIds.contains(key) {
                    context.delete(localItem)
                }
            }
        }

        // 5. Sincroniza Weights
        if let weights = json["weights"] as? [[String: Any]] {
            let existingWeights = (try? context.fetch(FetchDescriptor<WeightEntry>())) ?? []
            let existingMap = Dictionary(uniqueKeysWithValues: existingWeights.map { ($0.id.uuidString.lowercased(), $0) })
            var remoteIds = Set<String>()

            for we in weights {
                guard let idStr = we["id"] as? String, let id = UUID(uuidString: idStr) else { continue }
                let key = idStr.lowercased()
                remoteIds.insert(key)

                let weight = we["weight"] as? Double ?? 70.0
                let notes = we["notes"] as? String ?? ""
                let dateStr = we["entryDate"] as? String ?? ""
                let date = Self.isoFormatter.date(from: dateStr) ?? Self.fallbackIsoFormatter.date(from: dateStr) ?? Date()

                if let existing = existingMap[key] {
                    existing.weight = weight
                    existing.date = date
                } else {
                    let entry = WeightEntry(id: id, weight: weight, date: date, notes: notes)
                    context.insert(entry)
                }
            }

            if replaceLocalState {
                for (key, localItem) in existingMap where !remoteIds.contains(key) {
                    context.delete(localItem)
                }
            }
        }

        // 6. Sincroniza Fichas de Treino (WorkoutRoutine)
        if let routines = json["routines"] as? [[String: Any]] {
            let existingRoutines = (try? context.fetch(FetchDescriptor<WorkoutRoutine>())) ?? []
            let existingMap = Dictionary(uniqueKeysWithValues: existingRoutines.map { ($0.id.uuidString.lowercased(), $0) })
            var remoteIds = Set<String>()

            for r in routines {
                guard let idStr = r["id"] as? String, let id = UUID(uuidString: idStr) else { continue }
                let key = idStr.lowercased()
                remoteIds.insert(key)

                let title = r["title"] as? String ?? "Ficha de Treino"
                let workoutTypeRaw = r["workoutType"] as? String ?? "Musculação"
                let targetMuscles = r["targetMuscles"] as? String ?? ""
                let hypertrophyFocus = r["hypertrophyFocus"] as? String ?? ""
                let duration = r["estimatedDurationMinutes"] as? Int ?? 50
                let cals = r["estimatedCalories"] as? Double ?? 350.0
                let rest = r["recommendedRestSeconds"] as? Int ?? 90
                let isFav = r["isFavorite"] as? Bool ?? false
                let exercisesJson = r["exercisesJson"] as? String ?? "[]"
                let notes = r["notes"] as? String ?? ""
                let createdAtStr = r["createdAt"] as? String ?? ""
                let createdAt = Self.isoFormatter.date(from: createdAtStr) ?? Self.fallbackIsoFormatter.date(from: createdAtStr) ?? Date()
                let lastTrainedStr = r["lastTrainedAt"] as? String
                let lastTrained = lastTrainedStr != nil ? (Self.isoFormatter.date(from: lastTrainedStr!) ?? Self.fallbackIsoFormatter.date(from: lastTrainedStr!)) : nil

                if let existing = existingMap[key] {
                    existing.title = title
                    existing.workoutTypeRaw = workoutTypeRaw
                    existing.targetMuscles = targetMuscles
                    existing.hypertrophyFocus = hypertrophyFocus
                    existing.estimatedDurationMinutes = duration
                    existing.estimatedCalories = cals
                    existing.recommendedRestSeconds = rest
                    existing.isFavorite = isFav
                    existing.exercisesJson = exercisesJson
                    existing.notes = notes
                    existing.lastTrainedAt = lastTrained
                } else {
                    let routine = WorkoutRoutine(
                        id: id,
                        title: title,
                        workoutType: WorkoutType(rawValue: workoutTypeRaw) ?? .bodybuilding,
                        targetMuscles: targetMuscles,
                        hypertrophyFocus: hypertrophyFocus,
                        estimatedDurationMinutes: duration,
                        estimatedCalories: cals,
                        recommendedRestSeconds: rest,
                        isFavorite: isFav,
                        exercises: [],
                        notes: notes,
                        createdAt: createdAt,
                        lastTrainedAt: lastTrained
                    )
                    routine.exercisesJson = exercisesJson
                    context.insert(routine)
                }
            }

            if replaceLocalState {
                for (key, localItem) in existingMap where !remoteIds.contains(key) {
                    context.delete(localItem)
                }
            }
        }

        // 7. Sincroniza Análise de IA mais recente preservando ID único
        if let analyses = json["analyses"] as? [[String: Any]], let latest = analyses.first {
            let analysisId = (latest["id"] as? String).flatMap(UUID.init(uuidString:)) ?? UUID()
            let summary = latest["statusSummary"] as? String ?? "Diagnóstico Concluído"
            let calDiag = latest["calorieDiagnostic"] as? String ?? ""
            let macroDiag = latest["macroDiagnostic"] as? String ?? ""
            let hydDiag = latest["hydrationDiagnostic"] as? String ?? ""
            let plan = latest["weeklyActionPlan"] as? String ?? ""
            let createdStr = latest["createdAt"] as? String ?? ""
            let created = Self.isoFormatter.date(from: createdStr) ?? Self.fallbackIsoFormatter.date(from: createdStr) ?? Date()

            let decoder = JSONDecoder()
            var workoutSugg: [String] = []
            if let wj = latest["workoutSuggestionsJson"] as? String, let d = wj.data(using: .utf8) {
                workoutSugg = (try? decoder.decode([String].self, from: d)) ?? []
            }
            var nutrSugg: [String] = []
            if let nj = latest["nutritionSuggestionsJson"] as? String, let d = nj.data(using: .utf8) {
                nutrSugg = (try? decoder.decode([String].self, from: d)) ?? []
            }
            var targets: SuggestedNutritionTargets? = nil
            if let tj = latest["suggestedTargetsJson"] as? String, let d = tj.data(using: .utf8) {
                targets = try? decoder.decode(SuggestedNutritionTargets.self, from: d)
            }
            var routine: AIRecommendedRoutine? = nil
            if let rj = latest["recommendedRoutineJson"] as? String, let d = rj.data(using: .utf8) {
                routine = try? decoder.decode(AIRecommendedRoutine.self, from: d)
            }

            let restoredAnalysis = EvolutionAIAnalysis(
                id: analysisId,
                statusSummary: summary,
                calorieDiagnostic: calDiag,
                macroDiagnostic: macroDiag,
                hydrationDiagnostic: hydDiag,
                workoutSuggestions: workoutSugg,
                nutritionSuggestions: nutrSugg,
                weeklyActionPlan: plan,
                suggestedTargets: targets,
                recommendedRoutine: routine,
                generatedAt: created
            )
            EvolutionAnalysisStore.shared.save(restoredAnalysis)
        } else {
            EvolutionAnalysisStore.shared.clear()
        }

        // 8. Sincroniza Logs de Atividades
        if let activityLogs = json["activityLogs"] as? [[String: Any]] {
            if replaceLocalState {
                AppActivityLogManager.shared.clearAll()
            }
            var recordsToMerge: [ActivityLogRecord] = []
            for l in activityLogs {
                guard let id = l["id"] as? String else { continue }
                let cat = l["category"] as? String ?? "activity"
                let title = l["title"] as? String ?? ""
                let isSnack = l["isSnack"] as? Bool
                let cals = l["caloriesChange"] as? Double ?? 0.0
                let prot = l["proteinGrams"] as? Double
                let carbs = l["carbsGrams"] as? Double
                let fat = l["fatGrams"] as? Double
                let water = l["waterMilliliters"] as? Int
                let weight = l["weightKg"] as? Double
                let details = l["details"] as? String
                let timeStr = l["timestamp"] as? String ?? ""
                let timestamp = Self.isoFormatter.date(from: timeStr) ?? Self.fallbackIsoFormatter.date(from: timeStr) ?? Date()

                recordsToMerge.append(ActivityLogRecord(
                    id: id,
                    timestamp: timestamp,
                    category: cat,
                    title: title,
                    isSnack: isSnack,
                    caloriesChange: cals,
                    proteinGrams: prot,
                    carbsGrams: carbs,
                    fatGrams: fat,
                    waterMilliliters: water,
                    weightKg: weight,
                    details: details
                ))
            }
            if !recordsToMerge.isEmpty {
                AppActivityLogManager.shared.mergeRemoteRecords(recordsToMerge)
            }
        }

        try? context.save()
    }
}

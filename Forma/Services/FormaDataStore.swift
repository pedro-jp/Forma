//
//  FormaDataStore.swift
//  Forma
//
//  Gerenciador centralizado Remote-First: toda ação grava primeiro no Supabase PostgreSQL
//  e em seguida persiste no cache SwiftData local para leitura instantânea no iPhone.
//

import Foundation
import SwiftData
import WidgetKit
import HealthKit

@MainActor
public final class FormaDataStore {
    public static let shared = FormaDataStore(inMemory: false)

    public let container: ModelContainer
    private let isInMemoryStore: Bool

    public var mainContext: ModelContext {
        container.mainContext
    }

    public init(inMemory: Bool = false) {
        self.isInMemoryStore = inMemory
        let schema = Schema([
            Profile.self,
            Health.self,
            FoodEntry.self,
            WorkoutEntry.self,
            WeightEntry.self,
            WorkoutRoutine.self,
            WaterEntry.self
        ])

        if inMemory {
            let memConfig = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
            self.container = try! ModelContainer(for: schema, configurations: [memConfig])
            return
        }

        do {
            let config = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
            self.container = try ModelContainer(for: schema, configurations: [config])
        } catch {
            print("⚠️ Erro ao abrir container com schema atual: \(error). Recriando store limpo...")
            if let appSupport = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first {
                let defaultStore = appSupport.appendingPathComponent("default.store")
                let defaultShm = appSupport.appendingPathComponent("default.store-shm")
                let defaultWal = appSupport.appendingPathComponent("default.store-wal")
                try? FileManager.default.removeItem(at: defaultStore)
                try? FileManager.default.removeItem(at: defaultShm)
                try? FileManager.default.removeItem(at: defaultWal)
            }

            do {
                let freshConfig = ModelConfiguration(schema: schema, isStoredInMemoryOnly: false)
                self.container = try ModelContainer(for: schema, configurations: [freshConfig])
            } catch {
                let memConfig = ModelConfiguration(schema: schema, isStoredInMemoryOnly: true)
                self.container = try! ModelContainer(for: schema, configurations: [memConfig])
            }
        }
    }

    // MARK: - Barreira de Corte por Conta para Apple Saúde (Impede Vazamento de Treinos do Celular)

    public static func setHealthKitImportCutoff(_ date: Date, forUserId userId: String) {
        guard !userId.isEmpty else { return }
        UserDefaults.standard.set(date.timeIntervalSince1970, forKey: "forma_healthkit_cutoff_\(userId)")
    }

    public static func healthKitImportCutoff(forUserId userId: String) -> Date? {
        guard !userId.isEmpty else { return nil }
        let key = "forma_healthkit_cutoff_\(userId)"
        guard UserDefaults.standard.object(forKey: key) != nil else { return nil }
        let ts = UserDefaults.standard.double(forKey: key)
        return ts > 0 ? Date(timeIntervalSince1970: ts) : nil
    }

    /// Garante que exista exatamente um Profile no contexto, consolidando eventuais duplicatas.
    @discardableResult
    func ensureSingleProfile(in context: ModelContext? = nil) -> Profile {
        let ctx = context ?? mainContext
        let profiles = (try? ctx.fetch(FetchDescriptor<Profile>())) ?? []
        if let primary = profiles.first {
            if profiles.count > 1 {
                for duplicate in profiles.dropFirst() {
                    ctx.delete(duplicate)
                }
            }
            if !primary.autoRecalculateGoals {
                primary.autoRecalculateGoals = true
            }
            try? ctx.save()
            return primary
        }
        let fresh = Profile(
            nome: AuthManager.shared.currentUserName ?? "",
            email: AuthManager.shared.currentUserEmail ?? ""
        )
        ctx.insert(fresh)
        try? ctx.save()
        return fresh
    }

    // MARK: - 1. Configuração Inicial Obrigatória & Perfil (Remote-First)

    public func completeInitialOnboardingRemoteFirst(
        name: String,
        goal: FitnessGoal,
        currentWeight: Double,
        heightMeters: Double,
        targetWeight: Double,
        targetCalorieIntake: Double,
        targetProtein: Double,
        targetCarbohydrates: Double,
        targetFat: Double,
        targetWaterMilliliters: Int,
        targetCalorieBurn: Double,
        targetSteps: Double
    ) async throws {
        let cleanName = name.trimmingCharacters(in: .whitespacesAndNewlines)
        let now = Date()
        let initialWeightId = UUID()

        let profilePayload = RemoteProfilePayload(
            name: cleanName,
            goal: goal.rawValue,
            targetCalorieIntake: targetCalorieIntake,
            targetCalorieBurn: targetCalorieBurn,
            targetSteps: targetSteps,
            targetWeight: targetWeight,
            targetProtein: targetProtein,
            targetCarbohydrates: targetCarbohydrates,
            targetFat: targetFat,
            targetWaterMilliliters: targetWaterMilliliters,
            heightMeters: heightMeters,
            currentWeight: currentWeight,
            onboardingCompleted: true
        )

        // 1. Grava PRIMEIRO no Supabase PostgreSQL (Perfil + Pesagem Inicial)
        if !isInMemoryStore {
            try await BackendSyncManager.shared.pushProfileRemoteFirst(profilePayload)
            try await BackendSyncManager.shared.pushWeightRemoteFirst(
                id: initialWeightId,
                weight: currentWeight,
                date: now,
                notes: "Configuração inicial da conta",
                profilePayload: profilePayload
            )
        }

        // 2. Só após confirmação do banco, salva no cache SwiftData local do iPhone
        let profile = ensureSingleProfile(in: mainContext)
        profile.nome = cleanName
        if let email = AuthManager.shared.currentUserEmail, !email.isEmpty {
            profile.email = email
        }
        profile.goal = goal
        profile.currentWeight = currentWeight
        profile.height = heightMeters
        profile.targetWeight = targetWeight
        profile.targetCalorieIntake = targetCalorieIntake
        profile.targetProtein = targetProtein
        profile.targetCarbohydrates = targetCarbohydrates
        profile.targetFat = targetFat
        profile.targetWaterMilliliters = targetWaterMilliliters
        profile.targetCalorieBurn = targetCalorieBurn
        profile.targetSteps = targetSteps
        profile.onboardingCompleted = true
        profile.autoRecalculateGoals = true

        let weightEntry = WeightEntry(
            id: initialWeightId,
            weight: currentWeight,
            date: now,
            notes: "Configuração inicial da conta"
        )
        mainContext.insert(weightEntry)
        try mainContext.save()

        if !isInMemoryStore {
            AuthManager.shared.updateLocalUserName(cleanName)
            if let userId = AuthManager.shared.currentUserId,
               Self.healthKitImportCutoff(forUserId: userId) == nil {
                Self.setHealthKitImportCutoff(now, forUserId: userId)
            }
            AppActivityLogManager.shared.logWeight(
                id: initialWeightId.uuidString,
                weightKg: currentWeight,
                date: now,
                notes: "Configuração inicial da conta"
            )
            syncTodayData(skipCloudSyncTrigger: true)
        }
    }

    func saveProfileRemoteFirst(_ profile: Profile, overrideName: String? = nil) async throws {
        let payload = RemoteProfilePayload.from(profile: profile, overrideName: overrideName)
        if !isInMemoryStore {
            try await BackendSyncManager.shared.pushProfileRemoteFirst(payload)
        }
        if let overrideName, !overrideName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
            profile.nome = overrideName.trimmingCharacters(in: .whitespacesAndNewlines)
            if !isInMemoryStore {
                AuthManager.shared.updateLocalUserName(profile.nome)
            }
        }
        try mainContext.save()
        syncTodayData(skipCloudSyncTrigger: true)
    }

    // MARK: - 2. Refeições / Alimentos (Remote-First)

    func insertFoodEntriesRemoteFirst(_ entries: [FoodEntry]) async throws {
        guard !entries.isEmpty else { return }
        if !isInMemoryStore {
            try await BackendSyncManager.shared.pushFoodsRemoteFirst(entries)
        }
        for entry in entries {
            mainContext.insert(entry)
            if !isInMemoryStore {
                AppActivityLogManager.shared.logMeal(
                    id: entry.id.uuidString,
                    name: entry.name,
                    mealType: entry.mealType.rawValue,
                    calories: entry.calories,
                    protein: entry.protein,
                    carbs: entry.carbohydrates,
                    fat: entry.fat,
                    isSnack: entry.mealType == .snacks,
                    date: entry.date
                )
            }
        }
        recalculateProfileWithFullContext(in: mainContext)
        try mainContext.save()

        let last = entries.last
        syncTodayData(lastFoodName: last?.name, lastFoodCalories: last?.calories, skipCloudSyncTrigger: false)
    }

    func deleteFoodEntriesRemoteFirst(_ entries: [FoodEntry], in context: ModelContext? = nil) async throws {
        guard !entries.isEmpty else { return }
        let ctx = context ?? mainContext
        let deletedIds = entries.map(\.id)
        if !isInMemoryStore {
            try await BackendSyncManager.shared.deleteFoodsRemoteFirst(deletedIds)
            AppActivityLogManager.shared.removeRecords(ids: deletedIds.map(\.uuidString))
        }
        for entry in entries {
            ctx.delete(entry)
        }
        recalculateProfileWithFullContext(in: ctx)
        try ctx.save()
        syncTodayData(skipCloudSyncTrigger: false)
    }

    func insertFoodEntries(_ entries: [FoodEntry]) throws {
        guard !entries.isEmpty else { return }
        if isInMemoryStore || !AuthManager.shared.isAuthenticated {
            for entry in entries { mainContext.insert(entry) }
            recalculateProfileWithFullContext(in: mainContext)
            try mainContext.save()
            return
        }
        Task { @MainActor in
            try? await self.insertFoodEntriesRemoteFirst(entries)
        }
    }

    func deleteFoodEntry(_ entry: FoodEntry, in context: ModelContext? = nil) {
        deleteFoodEntries([entry], in: context)
    }

    func deleteFoodEntries(_ entries: [FoodEntry], in context: ModelContext? = nil) {
        guard !entries.isEmpty else { return }
        if isInMemoryStore || !AuthManager.shared.isAuthenticated {
            let ctx = context ?? mainContext
            for entry in entries { ctx.delete(entry) }
            recalculateProfileWithFullContext(in: ctx)
            try? ctx.save()
            return
        }
        Task { @MainActor in
            try? await self.deleteFoodEntriesRemoteFirst(entries, in: context)
        }
    }

    @discardableResult
    func updateFoodEntriesMealType(
        _ entries: [FoodEntry],
        to targetMeal: MealType,
        in context: ModelContext? = nil
    ) -> Int {
        let entriesToMove = entries.filter { $0.mealType != targetMeal }
        guard !entriesToMove.isEmpty else { return 0 }

        let ctx = context ?? mainContext
        for entry in entriesToMove {
            entry.mealType = targetMeal
            if !isInMemoryStore {
                AppActivityLogManager.shared.logMeal(
                    id: entry.id.uuidString,
                    name: entry.name,
                    mealType: targetMeal.rawValue,
                    calories: entry.calories,
                    protein: entry.protein,
                    carbs: entry.carbohydrates,
                    fat: entry.fat,
                    isSnack: targetMeal == .snacks,
                    date: entry.date
                )
            }
        }

        recalculateProfileWithFullContext(in: ctx)
        try? ctx.save()
        syncTodayData(skipCloudSyncTrigger: true)

        if !isInMemoryStore && AuthManager.shared.isAuthenticated {
            Task { @MainActor in
                try? await BackendSyncManager.shared.pushFoodsRemoteFirst(entriesToMove)
            }
        }
        return entriesToMove.count
    }

    @discardableResult
    func handleFoodDropPayloads(
        _ payloads: [String],
        to targetMeal: MealType,
        candidateEntries: [FoodEntry],
        in context: ModelContext? = nil
    ) -> Int {
        var resolvedById: [UUID: FoodEntry] = [:]
        for raw in payloads {
            guard let decoded = FoodDragTransferPayload.decode(raw) else { continue }
            switch decoded {
            case .food(let id):
                if let match = candidateEntries.first(where: { $0.id == id }) {
                    resolvedById[match.id] = match
                }
            case .mealGroup(let sourceMeal):
                guard sourceMeal != targetMeal else { continue }
                for match in candidateEntries where match.mealType == sourceMeal {
                    resolvedById[match.id] = match
                }
            }
        }

        return updateFoodEntriesMealType(Array(resolvedById.values), to: targetMeal, in: context)
    }

    // MARK: - Motor de Contexto Completo para Recálculo Fisiológico

    func buildRecalculationContext(in context: ModelContext? = nil) -> RecalculationContext {
        let ctx = context ?? mainContext
        let calendar = Calendar.current
        let now = Date()
        let sevenDaysAgo = calendar.date(byAdding: .day, value: -7, to: now) ?? now
        let fourteenDaysAgo = calendar.date(byAdding: .day, value: -14, to: now) ?? now
        let twentyOneDaysAgo = calendar.date(byAdding: .day, value: -21, to: now) ?? now

        let workoutDescriptor = FetchDescriptor<WorkoutEntry>(
            predicate: #Predicate { entry in
                entry.date >= fourteenDaysAgo
            }
        )
        let workouts14d = (try? ctx.fetch(workoutDescriptor)) ?? []
        let sum14d = workouts14d.reduce(0.0) { $0 + $1.caloriesBurned }
        let sum7d = workouts14d.filter { $0.date >= sevenDaysAgo }.reduce(0.0) { $0 + $1.caloriesBurned }
        let avgDailyWorkoutBurn = max(sum14d / 14.0, sum7d / 7.0)

        let routines = (try? ctx.fetch(FetchDescriptor<WorkoutRoutine>())) ?? []
        let routineEstimatedDailyBurn: Double
        if !routines.isEmpty {
            let avgPerSession = routines.reduce(0.0) { $0 + $1.estimatedCalories } / Double(routines.count)
            let sessionsPerWeek = Double(min(5, max(3, routines.count)))
            routineEstimatedDailyBurn = (avgPerSession * sessionsPerWeek) / 7.0
        } else {
            routineEstimatedDailyBurn = 0.0
        }

        let recentSteps = Double(WidgetDataManager.shared.loadWidgetData().stepsCount)

        let weightDescriptor = FetchDescriptor<WeightEntry>(
            predicate: #Predicate { entry in
                entry.date >= twentyOneDaysAgo
            },
            sortBy: [SortDescriptor(\.date, order: .forward)]
        )
        let recentWeights = (try? ctx.fetch(weightDescriptor)) ?? []
        var weeklyWeightTrendKg: Double? = nil
        if let first = recentWeights.first, let last = recentWeights.last, recentWeights.count >= 2 {
            let daysSpan = last.date.timeIntervalSince(first.date) / 86400.0
            if daysSpan >= 4.0 {
                weeklyWeightTrendKg = ((last.weight - first.weight) / daysSpan) * 7.0
            }
        }

        let foodDescriptor = FetchDescriptor<FoodEntry>(
            predicate: #Predicate { entry in
                entry.date >= sevenDaysAgo
            }
        )
        let recentFoods = (try? ctx.fetch(foodDescriptor)) ?? []
        var recentAvgIntake: Double? = nil
        if !recentFoods.isEmpty {
            var byDay: [Date: Double] = [:]
            for food in recentFoods {
                let day = calendar.startOfDay(for: food.date)
                byDay[day, default: 0.0] += food.calories
            }
            if !byDay.isEmpty {
                recentAvgIntake = byDay.values.reduce(0.0, +) / Double(byDay.count)
            }
        }

        return RecalculationContext(
            workoutsLast14DaysCount: workouts14d.count,
            avgDailyWorkoutBurn: avgDailyWorkoutBurn,
            routineEstimatedDailyBurn: routineEstimatedDailyBurn,
            recentAvgSteps: recentSteps,
            weeklyWeightTrendKg: weeklyWeightTrendKg,
            recentAvgCalorieIntake: recentAvgIntake
        )
    }

    func recalculateProfileWithFullContext(
        for weight: Double? = nil,
        profile overrideProfile: Profile? = nil,
        in context: ModelContext? = nil
    ) {
        let ctx = context ?? mainContext
        guard let targetProfile = overrideProfile ?? (try? ctx.fetch(FetchDescriptor<Profile>()).first),
              targetProfile.onboardingCompleted else {
            return
        }
        let recalcContext = buildRecalculationContext(in: ctx)
        targetProfile.recalculateDailyGoals(for: weight, context: recalcContext)
    }

    // MARK: - 3. Treinos Realizados (Remote-First)

    func insertWorkoutEntryRemoteFirst(
        _ entry: WorkoutEntry,
        routineTrained: WorkoutRoutine? = nil,
        saveToAppleHealth: Bool = true
    ) async throws {
        let trainedAt = Date()
        if let routine = routineTrained {
            routine.lastTrainedAt = trainedAt
        }

        if !isInMemoryStore {
            try await BackendSyncManager.shared.pushWorkoutRemoteFirst(entry, updatedRoutine: routineTrained)
        }

        mainContext.insert(entry)
        recalculateProfileWithFullContext(in: mainContext)
        try mainContext.save()

        guard !isInMemoryStore else { return }

        AppActivityLogManager.shared.logWorkout(
            id: entry.id.uuidString,
            title: entry.title,
            workoutType: entry.workoutType.rawValue,
            durationMinutes: entry.durationMinutes,
            caloriesBurned: entry.caloriesBurned,
            date: entry.date,
            details: entry.notes
        )

        if saveToAppleHealth {
            HealthManager.shared.saveWorkout(
                title: entry.title,
                workoutType: entry.workoutType,
                caloriesBurned: entry.caloriesBurned,
                durationMinutes: entry.durationMinutes,
                date: entry.date
            )
        }

        syncTodayData(skipCloudSyncTrigger: false)
    }

    func deleteWorkoutEntryRemoteFirst(_ entry: WorkoutEntry, in context: ModelContext? = nil) async throws {
        let ctx = context ?? mainContext
        let deletedId = entry.id
        if !isInMemoryStore {
            try await BackendSyncManager.shared.deleteWorkoutRemoteFirst(deletedId)
            AppActivityLogManager.shared.removeRecords(ids: [deletedId.uuidString])
        }
        ctx.delete(entry)
        recalculateProfileWithFullContext(in: ctx)
        try ctx.save()
        syncTodayData(skipCloudSyncTrigger: false)
    }

    func insertWorkoutEntry(_ entry: WorkoutEntry) throws {
        if isInMemoryStore || !AuthManager.shared.isAuthenticated {
            mainContext.insert(entry)
            recalculateProfileWithFullContext(in: mainContext)
            try mainContext.save()
            return
        }
        Task { @MainActor in
            try? await self.insertWorkoutEntryRemoteFirst(entry)
        }
    }

    func deleteWorkoutEntry(_ entry: WorkoutEntry, in context: ModelContext? = nil) {
        if isInMemoryStore || !AuthManager.shared.isAuthenticated {
            let ctx = context ?? mainContext
            ctx.delete(entry)
            recalculateProfileWithFullContext(in: ctx)
            try? ctx.save()
            return
        }
        Task { @MainActor in
            try? await self.deleteWorkoutEntryRemoteFirst(entry, in: context)
        }
    }

    // MARK: - Sincronização Protegida do Apple Saúde (Isolada por Conta Autenticada)

    func setupBackgroundHealthObserver() {
        guard !isInMemoryStore else { return }
        HealthManager.shared.requestAuthorization { success, _ in
            guard success else { return }
            Task { @MainActor in
                HealthManager.shared.startObservingExternalWorkouts {
                    Task { @MainActor in
                        FormaDataStore.shared.syncExternalHealthWorkoutsInBackground()
                    }
                }
                FormaDataStore.shared.syncExternalHealthWorkoutsInBackground()
            }
        }
    }

    func syncExternalHealthWorkoutsInBackground(days: Int = 7, in _: ModelContext? = nil) {
        guard !isInMemoryStore,
              AuthManager.shared.isAuthenticated,
              let userId = AuthManager.shared.currentUserId,
              !userId.isEmpty else {
            return
        }

        guard let profile = (try? mainContext.fetch(FetchDescriptor<Profile>()).first),
              !profile.requiresInitialSetup else {
            return
        }

        let cutoffDate = Self.healthKitImportCutoff(forUserId: userId)

        HealthManager.shared.requestAuthorization { [weak self] success, _ in
            guard success else { return }
            Task { @MainActor in
                guard let self else { return }
                HealthManager.shared.fetchExternalWorkoutSamples(days: days) { [weak self] samples in
                    guard let self,
                          !samples.isEmpty,
                          AuthManager.shared.isAuthenticated,
                          AuthManager.shared.currentUserId == userId else { return }

                    let targetContext = self.mainContext
                    let existingWorkouts = (try? targetContext.fetch(FetchDescriptor<WorkoutEntry>())) ?? []

                    // Se a conta não possui treinos e não tinha cutoff gravado, define cutoff agora para não puxar treinos antigos de terceiros no aparelho
                    let effectiveCutoff: Date?
                    if let cutoffDate {
                        effectiveCutoff = cutoffDate
                    } else if existingWorkouts.isEmpty {
                        let now = Date()
                        Self.setHealthKitImportCutoff(now, forUserId: userId)
                        effectiveCutoff = now
                    } else {
                        effectiveCutoff = nil
                    }

                    var existingKeys = Set(existingWorkouts.map { "\($0.title)_\(Int($0.date.timeIntervalSince1970))" })
                    var candidates: [WorkoutEntry] = []

                    for sample in samples {
                        if let effectiveCutoff, sample.startDate < effectiveCutoff {
                            continue
                        }
                        let uniqueKey = "\(sample.title)_\(Int(sample.startDate.timeIntervalSince1970))"
                        guard !existingKeys.contains(uniqueKey) else { continue }
                        existingKeys.insert(uniqueKey)

                        candidates.append(WorkoutEntry(
                            title: sample.title,
                            workoutType: sample.workoutType,
                            durationMinutes: sample.durationMinutes,
                            caloriesBurned: sample.caloriesBurned,
                            date: sample.startDate,
                            notes: "Sincronizado automaticamente (\(sample.sourceName))"
                        ))
                    }

                    guard !candidates.isEmpty else { return }
                    Task { @MainActor in
                        for entry in candidates {
                            try? await self.insertWorkoutEntryRemoteFirst(entry, saveToAppleHealth: false)
                        }
                    }
                }
            }
        }
    }

    // MARK: - 4. Hidratação e Pesagens (Remote-First)

    func insertWaterEntryRemoteFirst(_ entry: WaterEntry) async throws {
        if !isInMemoryStore {
            try await BackendSyncManager.shared.pushWaterRemoteFirst(entry)
        }
        mainContext.insert(entry)
        try mainContext.save()
        if !isInMemoryStore {
            AppActivityLogManager.shared.logWater(
                id: entry.id.uuidString,
                milliliters: entry.milliliters,
                date: entry.date
            )
        }
        syncTodayData(skipCloudSyncTrigger: true)
    }

    func deleteWaterEntryRemoteFirst(_ entry: WaterEntry, in context: ModelContext? = nil) async throws {
        let ctx = context ?? mainContext
        let waterId = entry.id
        if !isInMemoryStore {
            try await BackendSyncManager.shared.deleteWaterRemoteFirst(waterId)
            AppActivityLogManager.shared.removeRecords(ids: [waterId.uuidString])
        }
        ctx.delete(entry)
        try ctx.save()
        syncTodayData(skipCloudSyncTrigger: true)
    }

    public func createWeightEntryRemoteFirst(weight: Double, date: Date, notes: String) async throws {
        let entryId = UUID()
        let profile = ensureSingleProfile(in: mainContext)

        let previousWeight = profile.currentWeight
        profile.currentWeight = weight
        profile.autoRecalculateGoals = true
        recalculateProfileWithFullContext(for: weight, profile: profile, in: mainContext)
        let profilePayload = RemoteProfilePayload.from(profile: profile)

        if !isInMemoryStore {
            do {
                try await BackendSyncManager.shared.pushWeightRemoteFirst(
                    id: entryId,
                    weight: weight,
                    date: date,
                    notes: notes,
                    profilePayload: profilePayload
                )
            } catch {
                profile.currentWeight = previousWeight
                recalculateProfileWithFullContext(for: previousWeight, profile: profile, in: mainContext)
                throw error
            }
        }

        let entry = WeightEntry(id: entryId, weight: weight, date: date, notes: notes)
        mainContext.insert(entry)
        try mainContext.save()

        if !isInMemoryStore {
            AppActivityLogManager.shared.logWeight(id: entryId.uuidString, weightKg: weight, date: date, notes: notes)
            syncTodayData(skipCloudSyncTrigger: true)
        }
    }

    func deleteWeightEntryRemoteFirst(_ entry: WeightEntry, in context: ModelContext? = nil) async throws {
        let ctx = context ?? mainContext
        let weightId = entry.id

        let remainingDesc = FetchDescriptor<WeightEntry>(sortBy: [SortDescriptor(\.date, order: .reverse)])
        let remaining = (try? ctx.fetch(remainingDesc)) ?? []
        let nextLatest = remaining.first(where: { $0.id != weightId })
        let profile = (try? ctx.fetch(FetchDescriptor<Profile>()).first)

        var updatedPayload: RemoteProfilePayload? = nil
        if let profile, let nextLatest {
            let previewMetrics = Profile.computeContextualTargets(
                weight: nextLatest.weight,
                height: profile.height,
                targetWeight: profile.targetWeight,
                goal: profile.goal,
                targetCalorieBurn: profile.targetCalorieBurn,
                targetSteps: profile.targetSteps,
                context: buildRecalculationContext(in: ctx)
            )
            updatedPayload = RemoteProfilePayload(
                name: profile.nome,
                goal: profile.goalRaw,
                targetCalorieIntake: previewMetrics.suggestedCalories,
                targetCalorieBurn: profile.targetCalorieBurn,
                targetSteps: profile.targetSteps,
                targetWeight: profile.targetWeight,
                targetProtein: previewMetrics.proteinGrams,
                targetCarbohydrates: previewMetrics.carbsGrams,
                targetFat: previewMetrics.fatGrams,
                targetWaterMilliliters: previewMetrics.waterMilliliters,
                heightMeters: profile.height,
                currentWeight: nextLatest.weight,
                onboardingCompleted: profile.onboardingCompleted
            )
        }

        if !isInMemoryStore {
            try await BackendSyncManager.shared.deleteWeightRemoteFirst(weightId, updatedProfile: updatedPayload)
            AppActivityLogManager.shared.removeRecords(ids: [weightId.uuidString])
        }

        ctx.delete(entry)
        if let profile, let nextLatest {
            profile.currentWeight = nextLatest.weight
            recalculateProfileWithFullContext(for: nextLatest.weight, profile: profile, in: ctx)
        } else {
            recalculateProfileWithFullContext(in: ctx)
        }
        try ctx.save()
        syncTodayData(skipCloudSyncTrigger: true)
    }

    // MARK: - 5. Fichas de Treino & Análises de IA (Remote-First)

    func saveWorkoutRoutineRemoteFirst(_ routine: WorkoutRoutine) async throws {
        if !isInMemoryStore {
            try await BackendSyncManager.shared.pushRoutineRemoteFirst(routine)
        }
        let existing = (try? mainContext.fetch(FetchDescriptor<WorkoutRoutine>())) ?? []
        if !existing.contains(where: { $0.id == routine.id }) {
            mainContext.insert(routine)
        }
        recalculateProfileWithFullContext(in: mainContext)
        try mainContext.save()
        syncTodayData(skipCloudSyncTrigger: false)
    }

    func deleteWorkoutRoutineRemoteFirst(_ routine: WorkoutRoutine, in context: ModelContext? = nil) async throws {
        let ctx = context ?? mainContext
        if !isInMemoryStore {
            try await BackendSyncManager.shared.deleteRoutineRemoteFirst(routine.id)
        }
        ctx.delete(routine)
        recalculateProfileWithFullContext(in: ctx)
        try ctx.save()
        syncTodayData(skipCloudSyncTrigger: false)
    }

    func toggleFavoriteRoutineRemoteFirst(_ routine: WorkoutRoutine) async throws {
        routine.isFavorite.toggle()
        if !isInMemoryStore {
            do {
                try await BackendSyncManager.shared.pushRoutineRemoteFirst(routine)
            } catch {
                routine.isFavorite.toggle()
                throw error
            }
        }
        try mainContext.save()
    }

    public func saveEvolutionAnalysisRemoteFirst(_ analysis: EvolutionAIAnalysis) async throws {
        if !isInMemoryStore {
            try await BackendSyncManager.shared.pushAnalysisRemoteFirst(analysis)
        }
        EvolutionAnalysisStore.shared.save(analysis)
    }

    // MARK: - Wrappers Síncronos (Delegam para Remote-First)

    func insertWorkoutRoutine(_ routine: WorkoutRoutine) throws {
        if isInMemoryStore || !AuthManager.shared.isAuthenticated {
            mainContext.insert(routine)
            recalculateProfileWithFullContext(in: mainContext)
            try mainContext.save()
            return
        }
        Task { @MainActor in
            try? await self.saveWorkoutRoutineRemoteFirst(routine)
        }
    }

    func deleteWorkoutRoutine(_ routine: WorkoutRoutine, in context: ModelContext? = nil) throws {
        if isInMemoryStore || !AuthManager.shared.isAuthenticated {
            let ctx = context ?? mainContext
            ctx.delete(routine)
            try ctx.save()
            return
        }
        Task { @MainActor in
            try? await self.deleteWorkoutRoutineRemoteFirst(routine, in: context)
        }
    }

    func toggleFavoriteRoutine(_ routine: WorkoutRoutine) throws {
        if isInMemoryStore || !AuthManager.shared.isAuthenticated {
            routine.isFavorite.toggle()
            try mainContext.save()
            return
        }
        Task { @MainActor in
            try? await self.toggleFavoriteRoutineRemoteFirst(routine)
        }
    }

    func insertWaterEntry(_ entry: WaterEntry) throws {
        if isInMemoryStore || !AuthManager.shared.isAuthenticated {
            mainContext.insert(entry)
            try mainContext.save()
            return
        }
        Task { @MainActor in
            try? await self.insertWaterEntryRemoteFirst(entry)
        }
    }

    func deleteWaterEntry(_ entry: WaterEntry, in context: ModelContext? = nil) {
        if isInMemoryStore || !AuthManager.shared.isAuthenticated {
            let ctx = context ?? mainContext
            ctx.delete(entry)
            try? ctx.save()
            return
        }
        Task { @MainActor in
            try? await self.deleteWaterEntryRemoteFirst(entry, in: context)
        }
    }

    func deleteWeightEntry(_ entry: WeightEntry, in context: ModelContext? = nil) {
        if isInMemoryStore || !AuthManager.shared.isAuthenticated {
            let ctx = context ?? mainContext
            ctx.delete(entry)
            try? ctx.save()
            return
        }
        Task { @MainActor in
            try? await self.deleteWeightEntryRemoteFirst(entry, in: context)
        }
    }

    // MARK: - Sincronização de Água Pendente do Widget e Métricas do Dia

    func syncPendingWater() {
        guard AuthManager.shared.isAuthenticated else { return }
        let pending = WidgetDataManager.shared.drainPendingWater()
        if pending > 0 {
            let entry = WaterEntry(milliliters: pending, date: Date())
            mainContext.insert(entry)
            try? mainContext.save()
            if !isInMemoryStore {
                AppActivityLogManager.shared.logWater(
                    id: entry.id.uuidString,
                    milliliters: entry.milliliters,
                    date: entry.date
                )
                Task { @MainActor in
                    try? await BackendSyncManager.shared.pushWaterRemoteFirst(entry)
                }
            }
        }
    }

    func syncTodayData(
        lastFoodName: String? = nil,
        lastFoodCalories: Double? = nil,
        activeCaloriesOverride: Double? = nil,
        stepsOverride: Int? = nil,
        skipCloudSyncTrigger: Bool = false
    ) {
        guard !isInMemoryStore else { return }
        syncPendingWater()

        let calendar = Calendar.current
        let startOfDay = calendar.startOfDay(for: Date())
        let endOfDay = calendar.date(byAdding: .day, value: 1, to: startOfDay) ?? Date()

        let foodDescriptor = FetchDescriptor<FoodEntry>(
            predicate: #Predicate { entry in
                entry.date >= startOfDay && entry.date < endOfDay
            }
        )
        let foods = (try? mainContext.fetch(foodDescriptor)) ?? []

        let totalCalories = foods.reduce(0.0) { $0 + $1.calories }
        let totalProtein = foods.reduce(0.0) { $0 + $1.protein }
        let totalCarbs = foods.reduce(0.0) { $0 + $1.carbohydrates }
        let totalFat = foods.reduce(0.0) { $0 + $1.fat }

        let workoutDescriptor = FetchDescriptor<WorkoutEntry>(
            predicate: #Predicate { entry in
                entry.date >= startOfDay && entry.date < endOfDay
            }
        )
        let workouts = (try? mainContext.fetch(workoutDescriptor)) ?? []
        let workoutBurned = workouts.reduce(0.0) { $0 + $1.caloriesBurned }

        let waterDescriptor = FetchDescriptor<WaterEntry>(
            predicate: #Predicate { entry in
                entry.date >= startOfDay && entry.date < endOfDay
            }
        )
        let waters = (try? mainContext.fetch(waterDescriptor)) ?? []
        let waterConsumed = waters.reduce(0) { $0 + $1.milliliters }

        let profileDescriptor = FetchDescriptor<Profile>()
        let profiles = (try? mainContext.fetch(profileDescriptor)) ?? []
        let profile = profiles.first ?? Profile()

        let baseTargetCalories = profile.targetCalorieIntake > 0 ? profile.targetCalorieIntake : 2800.0
        let targetProtein = profile.targetProtein > 0 ? profile.targetProtein : 160.0
        let targetCarbs = profile.targetCarbohydrates > 0 ? profile.targetCarbohydrates : 380.0
        let targetFat = profile.targetFat > 0 ? profile.targetFat : 70.0
        let waterTarget = profile.targetWaterMilliliters > 0 ? profile.targetWaterMilliliters : 2500
        let goalRaw = profile.goalRaw

        let activeBurned: Double
        if let override = activeCaloriesOverride, override > 0 {
            activeBurned = max(workoutBurned, override)
        } else {
            activeBurned = workoutBurned
        }

        let totalBurned = profile.tmb + activeBurned
        let adjustedTargetCalories = baseTargetCalories + activeBurned

        let widgetData = WidgetCalorieData(
            caloriesConsumed: totalCalories,
            caloriesTarget: adjustedTargetCalories,
            baseCaloriesTarget: baseTargetCalories,
            caloriesBurned: totalBurned,
            activeCaloriesBurned: activeBurned,
            stepsCount: stepsOverride ?? 0,
            proteinConsumed: totalProtein,
            proteinTarget: targetProtein,
            carbsConsumed: totalCarbs,
            carbsTarget: targetCarbs,
            fatConsumed: totalFat,
            fatTarget: targetFat,
            waterConsumed: waterConsumed,
            waterTarget: waterTarget,
            goalRaw: goalRaw,
            lastUpdated: Date()
        )
        WidgetDataManager.shared.saveWidgetData(widgetData)

        if activeCaloriesOverride == nil && AuthManager.shared.isAuthenticated && !profile.requiresInitialSetup {
            HealthManager.shared.fetchTodayCalories { [weak self] healthCalories in
                HealthManager.shared.fetchTodaySteps { healthSteps in
                    Task { @MainActor in
                        guard self != nil else { return }
                        let currentActive = max(workoutBurned, healthCalories)
                        let newTotalBurned = profile.tmb + currentActive
                        let newAdjustedTarget = baseTargetCalories + currentActive

                        let updatedWidgetData = WidgetCalorieData(
                            caloriesConsumed: totalCalories,
                            caloriesTarget: newAdjustedTarget,
                            baseCaloriesTarget: baseTargetCalories,
                            caloriesBurned: newTotalBurned,
                            activeCaloriesBurned: currentActive,
                            stepsCount: Int(healthSteps),
                            proteinConsumed: totalProtein,
                            proteinTarget: targetProtein,
                            carbsConsumed: totalCarbs,
                            carbsTarget: targetCarbs,
                            fatConsumed: totalFat,
                            fatTarget: targetFat,
                            waterConsumed: waterConsumed,
                            waterTarget: waterTarget,
                            goalRaw: goalRaw,
                            lastUpdated: Date()
                        )
                        WidgetDataManager.shared.saveWidgetData(updatedWidgetData)
                    }
                }
            }
        }

        let hasBreakfast = foods.contains { $0.mealType == .breakfast }
        let hasLunch = foods.contains { $0.mealType == .lunch }
        let hasDinner = foods.contains { $0.mealType == .dinner }
        WorkoutNotificationManager.shared.scheduleDailyHydrationAndMealReminders(
            waterConsumedMl: waterConsumed,
            waterTargetMl: waterTarget,
            hasLoggedBreakfast: hasBreakfast,
            hasLoggedLunch: hasLunch,
            hasLoggedDinner: hasDinner
        )

        if !skipCloudSyncTrigger {
            Task { @MainActor in
                BackendSyncManager.shared.triggerAutoSync()
            }
        }
    }

    // MARK: - Isolamento Multi-Usuário (Limpeza Total Local na Troca de Conta / Logout)

    public func resetLocalDataForUserSwitch(userName: String?, userEmail: String?) {
        if !isInMemoryStore {
            _ = WidgetDataManager.shared.drainPendingWater()
        }

        if let foods = try? mainContext.fetch(FetchDescriptor<FoodEntry>()) {
            for item in foods { mainContext.delete(item) }
        }
        if let workouts = try? mainContext.fetch(FetchDescriptor<WorkoutEntry>()) {
            for item in workouts { mainContext.delete(item) }
        }
        if let waters = try? mainContext.fetch(FetchDescriptor<WaterEntry>()) {
            for item in waters { mainContext.delete(item) }
        }
        if let weights = try? mainContext.fetch(FetchDescriptor<WeightEntry>()) {
            for item in weights { mainContext.delete(item) }
        }
        if let routines = try? mainContext.fetch(FetchDescriptor<WorkoutRoutine>()) {
            for item in routines { mainContext.delete(item) }
        }
        if let profiles = try? mainContext.fetch(FetchDescriptor<Profile>()) {
            for item in profiles { mainContext.delete(item) }
        }
        if let healths = try? mainContext.fetch(FetchDescriptor<Health>()) {
            for item in healths { mainContext.delete(item) }
        }

        let cleanName = (userName?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false) ? userName!.trimmingCharacters(in: .whitespacesAndNewlines) : ""
        let cleanEmail = (userEmail?.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty == false) ? userEmail!.trimmingCharacters(in: .whitespacesAndNewlines) : ""
        let freshProfile = Profile(nome: cleanName, email: cleanEmail, onboardingCompleted: false)
        mainContext.insert(freshProfile)
        mainContext.insert(Health())

        try? mainContext.save()

        if !isInMemoryStore {
            EvolutionAnalysisStore.shared.clear()
            AppActivityLogManager.shared.clearAll()
            SharedWorkoutSessionManager.shared.clearSession()

            syncTodayData(skipCloudSyncTrigger: true)
            WidgetCenter.shared.reloadAllTimelines()
        }
    }
}

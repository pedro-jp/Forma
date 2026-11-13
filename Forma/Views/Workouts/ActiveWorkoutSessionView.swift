//
//  ActiveWorkoutSessionView.swift
//  Forma
//
//  Sessão imersiva de treino ao vivo sincronizada em tempo real com a Ilha Dinâmica e Apple Saúde.
//

import SwiftUI
import SwiftData
import Combine

struct ActiveWorkoutSessionView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext

    let title: String
    let workoutType: WorkoutType
    let targetMuscles: String
    let hypertrophyFocus: String
    let baseCalories: Double
    let baseDurationMinutes: Int
    let initialRestSeconds: Int
    let routineToUpdate: WorkoutRoutine?

    @State private var exercises: [WorkoutExerciseItem]
    @State private var sessionStartDate: Date = Date()
    @State private var currentExerciseIndex: Int = 0
    @State private var elapsedSeconds: Int = 0
    @State private var lastCompletedExerciseIndex: Int? = nil
    @State private var lastCompletedSetIndex: Int? = nil

    @State private var restStartDate: Date? = nil
    @State private var restEndDate: Date? = nil
    @State private var restTimeRemaining: Int = 0
    @State private var restTimeTotal: Int = 90
    @State private var isRestTimerActive: Bool = false

    @State private var showFinishConfirmation: Bool = false
    @State private var showCancelConfirmation: Bool = false

    @State private var selectedCatalogItemForDetail: ExerciseCatalogItem? = nil
    @State private var showCatalogSheet: Bool = false
    @State private var exerciseIndexToLink: Int? = nil

    init(
        title: String,
        workoutType: WorkoutType = .bodybuilding,
        targetMuscles: String = "",
        hypertrophyFocus: String = "",
        baseCalories: Double = 350,
        baseDurationMinutes: Int = 50,
        initialRestSeconds: Int = 90,
        exercises: [WorkoutExerciseItem],
        routineToUpdate: WorkoutRoutine? = nil
    ) {
        self.title = title
        self.workoutType = workoutType
        self.targetMuscles = targetMuscles
        self.hypertrophyFocus = hypertrophyFocus
        self.baseCalories = baseCalories
        self.baseDurationMinutes = baseDurationMinutes
        self.initialRestSeconds = initialRestSeconds > 0 ? initialRestSeconds : 90
        self.routineToUpdate = routineToUpdate
        _exercises = State(initialValue: exercises)
        _restTimeTotal = State(initialValue: initialRestSeconds > 0 ? initialRestSeconds : 90)
    }

    private var totalSetsCount: Int {
        exercises.reduce(0) { $0 + $1.sets }
    }

    private var completedSetsCount: Int {
        exercises.reduce(0) { $0 + $1.completedCount }
    }

    private var progressPercentage: Double {
        guard totalSetsCount > 0 else { return 0.0 }
        return Double(completedSetsCount) / Double(totalSetsCount)
    }

    private var liveEstimatedCalories: Double {
        let minutes = max(1.0, Double(elapsedSeconds) / 60.0)
        let ratePerMinute = baseCalories / max(10.0, Double(baseDurationMinutes))
        return (minutes * ratePerMinute).rounded()
    }

    private var lastCompletedSetBannerLabel: String? {
        if let exIdx = lastCompletedExerciseIndex, exIdx < exercises.count,
           let setIdx = lastCompletedSetIndex {
            let ex = exercises[exIdx]
            let w = ex.weightForSet(setIdx)
            return "Série \(setIdx + 1) feita: \(WorkoutExerciseItem.formatWeightKg(w))"
        }
        return nil
    }

    var body: some View {
        NavigationStack {
            ZStack {
                FormaTheme.Colors.pageBackground
                    .ignoresSafeArea()

                VStack(spacing: 0) {
                    sessionHeaderView

                    if isRestTimerActive {
                        ActiveRestTimerBanner(
                            restStartDate: restStartDate,
                            restEndDate: restEndDate,
                            restTimeRemaining: restTimeRemaining,
                            restTimeTotal: restTimeTotal,
                            lastSetLabel: lastCompletedSetBannerLabel,
                            onAdjustSeconds: { delta in adjustRestSeconds(delta) },
                            onAdjustLastSetWeight: { deltaKg in
                                adjustRecentSetWeight(deltaKg: deltaKg)
                            },
                            onSkip: { stopRestTimer() }
                        )
                        .padding(.horizontal, 16)
                        .padding(.top, 8)
                        .transition(.move(edge: .top).combined(with: .opacity))
                    }

                    ScrollView {
                        VStack(spacing: 16) {
                            if !hypertrophyFocus.isEmpty || !targetMuscles.isEmpty {
                                hypertrophyInfoBanner
                            }

                            ForEach(exercises.indices, id: \.self) { exIndex in
                                ActiveExerciseCard(
                                    exercise: exercises[exIndex],
                                    onOpenInfo: {
                                        let ex = exercises[exIndex]
                                        let catalogItem = ExerciseCatalog.shared.item(withId: ex.animationId)
                                            ?? ExerciseCatalog.shared.findBestMatch(for: ex.name)
                                        if let item = catalogItem {
                                            selectedCatalogItemForDetail = item
                                        } else {
                                            exerciseIndexToLink = exIndex
                                            showCatalogSheet = true
                                        }
                                    },
                                    onToggleSet: { setIndex in
                                        toggleSetCompletion(exerciseIndex: exIndex, setIndex: setIndex)
                                    },
                                    onAdjustSetWeight: { setIndex, deltaKg in
                                        adjustSetWeight(exerciseIndex: exIndex, setIndex: setIndex, deltaKg: deltaKg)
                                    }
                                )
                            }

                            finishWorkoutButton
                                .padding(.top, 8)
                                .padding(.bottom, 40)
                        }
                        .padding(.horizontal, 16)
                        .padding(.top, 16)
                    }
                    .scrollIndicators(.hidden)
                }
            }
            .navigationTitle("Treino em Andamento")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Sair") {
                        showCancelConfirmation = true
                    }
                    .foregroundStyle(.secondary)
                }

                ToolbarItem(placement: .confirmationAction) {
                    Button("Finalizar") {
                        showFinishConfirmation = true
                    }
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(FormaTheme.Colors.brand)
                }
            }
            .confirmationDialog(
                "Finalizar Treino?",
                isPresented: $showFinishConfirmation,
                titleVisibility: .visible
            ) {
                Button("Concluir e Salvar no App") {
                    finishAndSaveWorkout()
                }
                Button("Continuar Treinando", role: .cancel) {}
            } message: {
                Text("Você completou \(completedSetsCount) de \(totalSetsCount) séries em \(formatTime(elapsedSeconds)). O gasto de ~\(Int(liveEstimatedCalories)) kcal será salvo no histórico e no Apple Saúde.")
            }
            .confirmationDialog(
                "Deseja descartar este treino?",
                isPresented: $showCancelConfirmation,
                titleVisibility: .visible
            ) {
                Button("Descartar Treino", role: .destructive) {
                    stopAllTimers()
                    dismiss()
                }
                Button("Continuar Treinando", role: .cancel) {}
            } message: {
                Text("O progresso desta sessão não será salvo no histórico.")
            }
            .sheet(item: $selectedCatalogItemForDetail) { item in
                ExerciseDetailView(item: item)
            }
            .sheet(isPresented: $showCatalogSheet) {
                ExerciseCatalogSheet { selectedItem in
                    if let idx = exerciseIndexToLink, idx < exercises.count {
                        exercises[idx].animationId = selectedItem.id
                        saveCurrentStateToShared()
                    }
                    showCatalogSheet = false
                }
            }
            .onAppear {
                if let shared = SharedWorkoutSessionManager.shared.loadSession(), !shared.isFinished, shared.title == title {
                    syncFromSharedSession()
                } else {
                    startNewSession()
                }
            }
            .onReceive(NotificationCenter.default.publisher(for: UIApplication.willEnterForegroundNotification)) { _ in
                syncFromSharedSession()
            }
            .onReceive(Timer.publish(every: 1.0, on: .main, in: .common).autoconnect()) { _ in
                elapsedSeconds = max(0, Int(Date().timeIntervalSince(sessionStartDate)))

                if let shared = SharedWorkoutSessionManager.shared.loadSession() {
                    if shared.isFinished {
                        finishAndSaveWorkout()
                        return
                    }
                    if shared.completedSetsCount != completedSetsCount || shared.isResting != isRestTimerActive || shared.exercises != exercises {
                        syncFromSharedSession()
                    }
                }

                if isRestTimerActive, let end = restEndDate {
                    let remaining = Int(end.timeIntervalSinceNow)
                    if remaining <= 0 {
                        stopRestTimer()
                        WorkoutNotificationManager.shared.triggerRestFinishedHapticsAndSound()
                    } else {
                        restTimeRemaining = remaining
                    }
                }
            }
        }
    }

    private var sessionHeaderView: some View {
        VStack(spacing: 10) {
            HStack(spacing: 10) {
                VStack(alignment: .leading, spacing: 2) {
                    Text(title)
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                        .lineLimit(1)

                    HStack(spacing: 6) {
                        Label(workoutType.rawValue, systemImage: workoutType.icon)
                            .font(.system(size: 11, weight: .medium))
                            .foregroundStyle(FormaTheme.Colors.brand)

                        Text("•").font(.system(size: 11)).foregroundStyle(.secondary)

                        Text("\(completedSetsCount)/\(totalSetsCount) séries")
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(.secondary)

                        Text("•").font(.system(size: 11)).foregroundStyle(.secondary)

                        Text("~\(Int(liveEstimatedCalories)) kcal")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.energyGreen)
                    }
                }

                Spacer()

                HStack(spacing: 6) {
                    Circle()
                        .fill(FormaTheme.Colors.energyGreen)
                        .frame(width: 8, height: 8)

                    Text(sessionStartDate, style: .timer)
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                        .monospacedDigit()
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                }
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color(.systemBackground))
                .clipShape(Capsule())
                .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 1)
            }

            FormaProgressBar(value: progressPercentage, total: 1.0, color: FormaTheme.Colors.brand, height: 6)
        }
        .padding(.horizontal, 18)
        .padding(.vertical, 12)
        .background(FormaTheme.Colors.cardSurface)
        .shadow(color: Color.black.opacity(0.03), radius: 6, x: 0, y: 2)
    }

    private var hypertrophyInfoBanner: some View {
        FormaTintedCard(tint: FormaTheme.Colors.brand, padding: 12) {
            VStack(alignment: .leading, spacing: 6) {
                if !targetMuscles.isEmpty {
                    HStack(spacing: 6) {
                        Image(systemName: "figure.strengthtraining.traditional")
                            .font(.system(size: 12))
                            .foregroundStyle(FormaTheme.Colors.brand)
                        Text(targetMuscles)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                    }
                }

                if !hypertrophyFocus.isEmpty {
                    HStack(alignment: .top, spacing: 6) {
                        Image(systemName: "flame.fill")
                            .font(.system(size: 11))
                            .foregroundStyle(FormaTheme.Colors.warning)
                        Text(hypertrophyFocus)
                            .font(.system(size: 11))
                            .foregroundStyle(.secondary)
                    }
                }
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }

    private var finishWorkoutButton: some View {
        Button {
            showFinishConfirmation = true
        } label: {
            HStack(spacing: 8) {
                Image(systemName: "checkmark.circle.fill")
                    .font(.system(size: 16, weight: .bold))
                Text("Concluir Treino (\(completedSetsCount)/\(totalSetsCount) séries)")
                    .font(.system(size: 15, weight: .bold, design: .rounded))
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 15)
            .background(FormaTheme.Colors.brand)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .shadow(color: FormaTheme.Colors.brand.opacity(0.25), radius: 8, x: 0, y: 4)
        }
        .buttonStyle(.plain)
    }

    // MARK: - Ações e Sincronização com Ilha Dinâmica

    private func toggleSetCompletion(exerciseIndex: Int, setIndex: Int) {
        guard exerciseIndex < exercises.count else { return }
        currentExerciseIndex = exerciseIndex
        var currentSets = exercises[exerciseIndex].completedSets
        while currentSets.count < exercises[exerciseIndex].sets {
            currentSets.append(false)
        }

        let willBeDone = !currentSets[setIndex]
        currentSets[setIndex] = willBeDone
        exercises[exerciseIndex].completedSets = currentSets

        if willBeDone {
            lastCompletedExerciseIndex = exerciseIndex
            lastCompletedSetIndex = setIndex

            let feedback = UIImpactFeedbackGenerator(style: .medium)
            feedback.impactOccurred()

            let nextSetForRest: Int
            let nextExerciseNameForRest: String
            let nextTotalSetsForRest: Int

            if setIndex + 1 < exercises[exerciseIndex].sets {
                nextSetForRest = setIndex + 2
                nextExerciseNameForRest = exercises[exerciseIndex].name
                nextTotalSetsForRest = exercises[exerciseIndex].sets
            } else if exerciseIndex + 1 < exercises.count {
                currentExerciseIndex = exerciseIndex + 1
                nextSetForRest = 1
                nextExerciseNameForRest = exercises[exerciseIndex + 1].name
                nextTotalSetsForRest = exercises[exerciseIndex + 1].sets
            } else {
                nextSetForRest = exercises[exerciseIndex].sets
                nextExerciseNameForRest = exercises[exerciseIndex].name
                nextTotalSetsForRest = exercises[exerciseIndex].sets
            }

            let targetRest = exercises[exerciseIndex].restSeconds > 0 ? exercises[exerciseIndex].restSeconds : initialRestSeconds
            startRestTimer(seconds: targetRest, exerciseName: nextExerciseNameForRest, currentSet: nextSetForRest, totalSets: nextTotalSetsForRest)
        } else {
            if lastCompletedExerciseIndex == exerciseIndex && lastCompletedSetIndex == setIndex {
                lastCompletedExerciseIndex = nil
                lastCompletedSetIndex = nil
            }
            if isRestTimerActive {
                isRestTimerActive = false
                restStartDate = nil
                restEndDate = nil
                WorkoutNotificationManager.shared.cancelRestNotification()
            }
            saveCurrentStateToShared()
            updateLiveActivity(isResting: false)
        }
    }

    private func adjustSetWeight(exerciseIndex: Int, setIndex: Int, deltaKg: Double) {
        guard exerciseIndex >= 0, exerciseIndex < exercises.count else { return }
        var ex = exercises[exerciseIndex]
        while ex.setWeights.count < ex.sets {
            ex.setWeights.append(ex.weightForSet(ex.setWeights.count))
        }
        guard setIndex >= 0, setIndex < ex.setWeights.count else { return }
        let updated = max(0.0, ((ex.setWeights[setIndex] + deltaKg) * 2.0).rounded() / 2.0)
        ex.setWeights[setIndex] = updated

        // Propaga a nova carga para as séries seguintes ainda não concluídas deste exercício
        for idx in (setIndex + 1)..<ex.sets {
            let done = idx < ex.completedSets.count ? ex.completedSets[idx] : false
            if !done {
                ex.setWeights[idx] = updated
            }
        }
        ex.suggestedWeight = WorkoutExerciseItem.formatWeightKg(ex.averageWeightKg)
        exercises[exerciseIndex] = ex

        let feedback = UIImpactFeedbackGenerator(style: .light)
        feedback.impactOccurred()

        saveCurrentStateToShared()
        updateLiveActivity(isResting: isRestTimerActive)
    }

    private func adjustRecentSetWeight(deltaKg: Double) {
        if let exIdx = lastCompletedExerciseIndex, let setIdx = lastCompletedSetIndex {
            adjustSetWeight(exerciseIndex: exIdx, setIndex: setIdx, deltaKg: deltaKg)
        } else {
            let safeEx = min(max(0, currentExerciseIndex), max(0, exercises.count - 1))
            let safeSet = exercises.isEmpty ? 0 : min(max(0, exercises[safeEx].completedCount), max(0, exercises[safeEx].sets - 1))
            adjustSetWeight(exerciseIndex: safeEx, setIndex: safeSet, deltaKg: deltaKg)
        }
    }

    private func currentLiveWeightsInfo() -> (currentWeight: Double, lastSetNum: Int, lastWeight: Double, lastExName: String) {
        let safeIndex = min(max(0, currentExerciseIndex), max(0, exercises.count - 1))
        let currentSetIdx = exercises.isEmpty ? 0 : min(max(0, exercises[safeIndex].completedCount), max(0, exercises[safeIndex].sets - 1))
        let currentW = exercises.isEmpty ? 0.0 : exercises[safeIndex].weightForSet(currentSetIdx)

        if let lastEx = lastCompletedExerciseIndex, lastEx < exercises.count,
           let lastSet = lastCompletedSetIndex {
            let ex = exercises[lastEx]
            return (currentW, lastSet + 1, ex.weightForSet(lastSet), ex.name)
        }
        return (currentW, 0, 0.0, "")
    }

    private func saveCurrentStateToShared() {
        let isAllDone = exercises.allSatisfy { $0.isAllSetsCompleted }
        let safeIndex = min(max(0, currentExerciseIndex), max(0, exercises.count - 1))
        let currentSetIdx = exercises.isEmpty ? 0 : max(0, exercises[safeIndex].completedCount)

        let sharedState = SharedWorkoutState(
            title: title,
            workoutTypeRaw: workoutType.rawValue,
            exercises: exercises,
            currentExerciseIndex: safeIndex,
            currentSetIndex: currentSetIdx,
            sessionStartDate: sessionStartDate,
            baseCalories: baseCalories,
            baseDurationMinutes: baseDurationMinutes,
            isResting: isRestTimerActive,
            restStartDate: isRestTimerActive ? restStartDate : nil,
            restEndDate: isRestTimerActive ? restEndDate : nil,
            restDurationSeconds: restTimeTotal,
            isAllCompleted: isAllDone,
            lastCompletedExerciseIndex: lastCompletedExerciseIndex,
            lastCompletedSetIndex: lastCompletedSetIndex
        )
        SharedWorkoutSessionManager.shared.saveSession(sharedState)
    }

    private func syncFromSharedSession() {
        guard let shared = SharedWorkoutSessionManager.shared.loadSession() else { return }
        exercises = shared.exercises
        currentExerciseIndex = shared.currentExerciseIndex
        lastCompletedExerciseIndex = shared.lastCompletedExerciseIndex
        lastCompletedSetIndex = shared.lastCompletedSetIndex
        sessionStartDate = shared.sessionStartDate
        elapsedSeconds = max(0, Int(Date().timeIntervalSince(shared.sessionStartDate)))
        if shared.isResting, let end = shared.restEndDate {
            let rem = Int(end.timeIntervalSinceNow)
            if rem > 0 {
                restStartDate = shared.restStartDate
                restEndDate = shared.restEndDate
                restTimeRemaining = rem
                restTimeTotal = shared.restDurationSeconds
                withAnimation { isRestTimerActive = true }
            } else {
                withAnimation { isRestTimerActive = false }
                restStartDate = nil
                restEndDate = nil
            }
        } else {
            withAnimation { isRestTimerActive = false }
            restStartDate = nil
            restEndDate = nil
        }
    }

    private func startNewSession() {
        let userBodyWeight = (try? modelContext.fetch(FetchDescriptor<Profile>()).first?.currentWeight) ?? 75.0
        exercises = SharedWorkoutSessionManager.shared.prepareExercisesWithInitialWeights(
            exercises,
            userBodyWeightKg: userBodyWeight
        )
        sessionStartDate = Date()
        elapsedSeconds = 0
        lastCompletedExerciseIndex = nil
        lastCompletedSetIndex = nil
        saveCurrentStateToShared()

        let firstExName = exercises.first?.name ?? "Treino"
        let firstSets = exercises.first?.sets ?? 3
        let firstWeight = exercises.first?.weightForSet(0) ?? 15.0
        LiveActivityManager.shared.startWorkoutActivity(
            workoutTitle: title,
            currentExerciseName: firstExName,
            currentSetNumber: 1,
            totalSetsForExercise: firstSets,
            completedSetsTotal: completedSetsCount,
            totalSetsTotal: totalSetsCount,
            caloriesBurned: liveEstimatedCalories,
            workoutStartDate: sessionStartDate,
            isResting: false,
            restDurationSeconds: initialRestSeconds,
            isAllCompleted: false,
            currentSetWeightKg: firstWeight
        )
    }

    private func startRestTimer(seconds: Int, exerciseName: String, currentSet: Int, totalSets: Int) {
        let now = Date()
        let end = now.addingTimeInterval(Double(seconds))
        restStartDate = now
        restEndDate = end
        restTimeTotal = seconds
        restTimeRemaining = seconds
        withAnimation(.spring(duration: 0.3)) {
            isRestTimerActive = true
        }

        saveCurrentStateToShared()
        let wInfo = currentLiveWeightsInfo()

        WorkoutNotificationManager.shared.scheduleRestNotification(
            seconds: seconds,
            exerciseName: exerciseName,
            nextSet: currentSet,
            lastSetWeightKg: wInfo.lastWeight > 0 ? wInfo.lastWeight : wInfo.currentWeight
        )

        LiveActivityManager.shared.updateWorkoutActivity(
            workoutTitle: title,
            currentExerciseName: exerciseName,
            currentSetNumber: currentSet,
            totalSetsForExercise: totalSets,
            completedSetsTotal: completedSetsCount,
            totalSetsTotal: totalSetsCount,
            caloriesBurned: liveEstimatedCalories,
            workoutStartDate: sessionStartDate,
            isResting: true,
            restStartDate: now,
            restEndDate: end,
            restDurationSeconds: seconds,
            currentSetWeightKg: wInfo.currentWeight,
            lastCompletedSetNumber: wInfo.lastSetNum,
            lastCompletedSetWeightKg: wInfo.lastWeight,
            lastCompletedExerciseName: wInfo.lastExName
        )
    }

    private func stopRestTimer() {
        withAnimation(.spring(duration: 0.3)) {
            isRestTimerActive = false
        }
        restStartDate = nil
        restEndDate = nil
        WorkoutNotificationManager.shared.cancelRestNotification()
        saveCurrentStateToShared()
        updateLiveActivity(isResting: false)
    }

    private func adjustRestSeconds(_ deltaSeconds: Int) {
        guard isRestTimerActive else { return }
        let currentEnd = restEndDate ?? Date()
        let newEnd = currentEnd.addingTimeInterval(Double(deltaSeconds))
        let remaining = Int(newEnd.timeIntervalSinceNow)
        if remaining <= 1 {
            stopRestTimer()
            return
        }

        restEndDate = newEnd
        restTimeTotal = max(remaining, restTimeTotal + deltaSeconds)
        restTimeRemaining = remaining
        saveCurrentStateToShared()

        let currentEx = currentExerciseIndex < exercises.count ? exercises[currentExerciseIndex] : (exercises.first ?? WorkoutExerciseItem(name: "Treino"))
        let activeSetNumber = min(currentEx.sets, max(1, currentEx.completedCount + 1))
        let wInfo = currentLiveWeightsInfo()
        WorkoutNotificationManager.shared.scheduleRestNotification(
            seconds: remaining,
            exerciseName: currentEx.name,
            nextSet: activeSetNumber,
            lastSetWeightKg: wInfo.lastWeight > 0 ? wInfo.lastWeight : wInfo.currentWeight
        )

        LiveActivityManager.shared.updateWorkoutActivity(
            workoutTitle: title,
            currentExerciseName: currentEx.name,
            currentSetNumber: activeSetNumber,
            totalSetsForExercise: currentEx.sets,
            completedSetsTotal: completedSetsCount,
            totalSetsTotal: totalSetsCount,
            caloriesBurned: liveEstimatedCalories,
            workoutStartDate: sessionStartDate,
            isResting: true,
            restStartDate: restStartDate,
            restEndDate: newEnd,
            restDurationSeconds: restTimeTotal,
            currentSetWeightKg: wInfo.currentWeight,
            lastCompletedSetNumber: wInfo.lastSetNum,
            lastCompletedSetWeightKg: wInfo.lastWeight,
            lastCompletedExerciseName: wInfo.lastExName
        )
    }

    private func updateLiveActivity(isResting: Bool) {
        let currentEx = currentExerciseIndex < exercises.count ? exercises[currentExerciseIndex] : (exercises.first ?? WorkoutExerciseItem(name: "Treino"))
        let activeSetNumber = min(currentEx.sets, max(1, currentEx.completedCount + 1))
        let wInfo = currentLiveWeightsInfo()
        LiveActivityManager.shared.updateWorkoutActivity(
            workoutTitle: title,
            currentExerciseName: currentEx.name,
            currentSetNumber: activeSetNumber,
            totalSetsForExercise: currentEx.sets,
            completedSetsTotal: completedSetsCount,
            totalSetsTotal: totalSetsCount,
            caloriesBurned: liveEstimatedCalories,
            workoutStartDate: sessionStartDate,
            isResting: isResting,
            restStartDate: isResting ? restStartDate : nil,
            restEndDate: isResting ? restEndDate : nil,
            restDurationSeconds: restTimeTotal,
            currentSetWeightKg: wInfo.currentWeight,
            lastCompletedSetNumber: wInfo.lastSetNum,
            lastCompletedSetWeightKg: wInfo.lastWeight,
            lastCompletedExerciseName: wInfo.lastExName
        )
    }

    private func stopAllTimers() {
        isRestTimerActive = false
        restStartDate = nil
        restEndDate = nil
        WorkoutNotificationManager.shared.cancelRestNotification()
        SharedWorkoutSessionManager.shared.clearSession()
        LiveActivityManager.shared.endWorkoutActivity()
    }

    private func finishAndSaveWorkout() {
        // Registra a carga média realizada de cada exercício para iniciar com ela na próxima vez!
        SharedWorkoutSessionManager.shared.recordExerciseWeightsHistory(from: exercises)
        if let routine = routineToUpdate {
            var updatedExercises = exercises
            for idx in updatedExercises.indices {
                let avg = updatedExercises[idx].averageWeightKg
                if avg > 0 {
                    updatedExercises[idx].suggestedWeight = WorkoutExerciseItem.formatWeightKg(avg)
                }
                updatedExercises[idx].completedSets = Array(repeating: false, count: updatedExercises[idx].sets)
            }
            routine.exercises = updatedExercises
        }

        stopAllTimers()

        let finalSeconds = max(0, Int(Date().timeIntervalSince(sessionStartDate)))
        let finalDuration = max(1, finalSeconds / 60)
        let finalCalories = liveEstimatedCalories
        let weightsSummary = exercises
            .filter { $0.averageWeightKg > 0 }
            .prefix(4)
            .map { "\($0.name): \(WorkoutExerciseItem.formatWeightKg($0.averageWeightKg))" }
            .joined(separator: " • ")
        let workoutNotes = weightsSummary.isEmpty
            ? "Checklist: \(completedSetsCount) de \(totalSetsCount) séries concluídas em \(formatTime(finalSeconds))."
            : "Checklist: \(completedSetsCount)/\(totalSetsCount) séries (\(formatTime(finalSeconds))). Cargas: \(weightsSummary)"

        let entry = WorkoutEntry(
            title: title,
            workoutType: workoutType,
            durationMinutes: finalDuration,
            caloriesBurned: finalCalories,
            date: Date(),
            notes: workoutNotes
        )

        Task { @MainActor in
            do {
                try await FormaDataStore.shared.insertWorkoutEntryRemoteFirst(entry, routineTrained: routineToUpdate)
                let generator = UINotificationFeedbackGenerator()
                generator.notificationOccurred(.success)
                dismiss()
            } catch {
                // Caso sem internet no fim da sessão ao vivo, salva via wrapper para não perder a sessão concluída
                try? FormaDataStore.shared.insertWorkoutEntry(entry)
                if let routine = routineToUpdate {
                    routine.lastTrainedAt = Date()
                    try? modelContext.save()
                }
                dismiss()
            }
        }
    }

    private func formatTime(_ totalSeconds: Int) -> String {
        let mins = totalSeconds / 60
        let secs = totalSeconds % 60
        return String(format: "%02d:%02d", mins, secs)
    }
}

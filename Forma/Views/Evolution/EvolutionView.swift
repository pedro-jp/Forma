//
//  EvolutionView.swift
//  Forma
//
//  Aba de Evolução & Análise com gráficos interativos Swift Charts, sincronização Apple Saúde/Strava e Treinador IA.
//

import SwiftUI
import SwiftData

enum EvolutionSection: String, CaseIterable, Identifiable {
    case overview = "Visão Geral"
    case aiAnalysis = "Inteligência Forma"

    var id: String { rawValue }

    var icon: String {
        switch self {
        case .overview: return "chart.xyaxis.line"
        case .aiAnalysis: return "sparkles"
        }
    }
}

struct EvolutionView: View {
    @Environment(\.modelContext) private var modelContext

    @Query(sort: \WeightEntry.date, order: .reverse) private var weightEntries: [WeightEntry]
    @Query(sort: \FoodEntry.date, order: .reverse) private var foodEntries: [FoodEntry]
    @Query(sort: \WorkoutEntry.date, order: .reverse) private var workoutEntries: [WorkoutEntry]
    @Query(sort: \WaterEntry.date, order: .reverse) private var waterEntries: [WaterEntry]
    @Query private var profiles: [Profile]

    @State private var selectedSection: EvolutionSection = .overview
    @StateObject private var healthVM = HealthViewModel()
    @ObservedObject private var aiConsent = AIDataConsentManager.shared
    @ObservedObject private var authManager = AuthManager.shared

    @State private var showLogWeight: Bool = false
    @State private var showAIConsentSheet: Bool = false
    @State private var toastMessage: String? = nil

    @State private var isAnalyzingAI: Bool = false
    @State private var aiAnalysis: EvolutionAIAnalysis? = nil
    @State private var aiErrorMessage: String? = nil
    @State private var savedRoutineSuccessfully: Bool = false
    @State private var appliedTargetsSuccessfully: Bool = false
    @State private var evolutionAnalysisTask: Task<Void, Never>? = nil

    private var profile: Profile? { profiles.first }

    private var currentWeight: Double {
        if let pw = profile?.currentWeight, pw > 0 { return pw }
        return weightEntries.first?.weight ?? 72.0
    }

    private var targetWeight: Double { profile?.targetWeight ?? 80.0 }
    private var height: Double { profile?.height ?? 1.78 }
    private var goal: FitnessGoal { profile?.goal ?? .gain }

    private var stats: EvolutionStats {
        let ctx = FormaDataStore.shared.buildRecalculationContext(in: modelContext)
        return EvolutionCalculator.calculate(
            weight: currentWeight,
            height: height,
            goal: goal,
            targetWeight: targetWeight,
            targetCalorieBurn: profile?.targetCalorieBurn ?? 600.0,
            targetSteps: profile?.targetSteps ?? 8000.0,
            context: ctx
        )
    }

    private var weeklySummaries: [DayCalorieSummary] {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        let formatter = DateFormatter()
        formatter.locale = Locale(identifier: "pt_BR")
        formatter.dateFormat = "EEE"

        let proteinTarget = max(1.0, profile?.targetProtein ?? stats.suggestedProtein)
        let waterTarget = Double(max(500, profile?.targetWaterMilliliters ?? 2500))

        var summaries: [DayCalorieSummary] = []
        for i in (0..<7).reversed() {
            guard let dayStart = calendar.date(byAdding: .day, value: -i, to: today),
                  let dayEnd = calendar.date(byAdding: .day, value: 1, to: dayStart) else { continue }

            let dayFoods = foodEntries.filter { $0.date >= dayStart && $0.date < dayEnd }
            let consumed = dayFoods.reduce(0.0) { $0 + $1.calories }
            let protein = dayFoods.reduce(0.0) { $0 + $1.protein }

            let workoutsBurned = workoutEntries
                .filter { $0.date >= dayStart && $0.date < dayEnd }
                .reduce(0.0) { $0 + $1.caloriesBurned }

            let waterMl = waterEntries
                .filter { $0.date >= dayStart && $0.date < dayEnd }
                .reduce(0) { $0 + $1.milliliters }

            let activeBurned = calendar.isDateInToday(dayStart) ? max(healthVM.activeCalories, workoutsBurned) : workoutsBurned
            let dayName = i == 0 ? "Hoje" : formatter.string(from: dayStart).capitalized.replacingOccurrences(of: ".", with: "")

            summaries.append(
                DayCalorieSummary(
                    date: dayStart,
                    dayName: dayName,
                    consumed: consumed,
                    tmb: stats.tmb,
                    activeBurned: activeBurned,
                    proteinPercent: (protein / proteinTarget) * 100.0,
                    waterPercent: (Double(waterMl) / waterTarget) * 100.0
                )
            )
        }
        return summaries
    }

    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                if let toast = toastMessage {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark.circle.fill")
                            .foregroundStyle(.white)
                        Text(toast)
                            .font(.system(size: 13, weight: .bold))
                            .foregroundStyle(.white)
                    }
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
                    .frame(maxWidth: .infinity)
                    .background(FormaTheme.Colors.energyGreen)
                    .transition(.move(edge: .top).combined(with: .opacity))
                }

                topHeaderBar

                Divider().background(Color.primary.opacity(0.08))

                ScrollView {
                    VStack(spacing: FormaTheme.Spacing.sectionGap) {
                        if authManager.isPro {
                            switch selectedSection {
                            case .overview:
                                overviewSectionView
                            case .aiAnalysis:
                                EvolutionAICoachSection(
                                    analysis: aiAnalysis,
                                    isAnalyzing: isAnalyzingAI,
                                    errorMessage: aiErrorMessage,
                                    appliedTargetsSuccessfully: appliedTargetsSuccessfully,
                                    savedRoutineSuccessfully: savedRoutineSuccessfully,
                                    onGenerateAnalysis: generateAIAnalysis,
                                    onApplyTargets: applySuggestedTargets,
                                    onSaveRoutine: saveRecommendedRoutine
                                )
                            }
                        } else {
                            overviewSectionView
                        }
                    }
                    .padding(.top, 16)
                    .padding(.bottom, 88)
                }
                .scrollIndicators(.hidden)
                .background(FormaTheme.Colors.pageBackground.ignoresSafeArea())
            }
            .navigationBarHidden(true)
            .sheet(isPresented: $showLogWeight) {
                LogWeightView()
                    .environment(\.modelContext, modelContext)
            }
            .sheet(isPresented: $showAIConsentSheet) {
                AIDataConsentSheet {
                    performEvolutionAIAnalysis()
                }
            }
            .onAppear {
                healthVM.loadHealthData()
                FormaDataStore.shared.syncExternalHealthWorkoutsInBackground(in: modelContext)
                if !authManager.isPro {
                    selectedSection = .overview
                } else if aiAnalysis == nil {
                    aiAnalysis = EvolutionAnalysisStore.shared.latestAnalysis ?? EvolutionAnalysisStore.shared.loadLatest()
                }
            }
            .onChange(of: authManager.accountTier) { _, newTier in
                if newTier == .free {
                    evolutionAnalysisTask?.cancel()
                    isAnalyzingAI = false
                    showAIConsentSheet = false
                    aiErrorMessage = nil
                    withAnimation(.spring(duration: 0.3)) {
                        selectedSection = .overview
                    }
                } else if aiAnalysis == nil {
                    aiAnalysis = EvolutionAnalysisStore.shared.latestAnalysis ?? EvolutionAnalysisStore.shared.loadLatest()
                }
            }
        }
    }

    private var topHeaderBar: some View {
        VStack(spacing: 12) {
            FormaScreenHeader(
                title: authManager.isPro ? "Evolução & Análise" : "Evolução Corporal",
                subtitle: authManager.isPro ? "Seu progresso e Inteligência Forma" : "Peso, calorias e consistência semanal"
            ) {
                Button {
                    showLogWeight = true
                } label: {
                    HStack(spacing: 4) {
                        Image(systemName: "plus")
                            .font(.system(size: 11, weight: .bold))
                        Text("Peso")
                            .font(.system(size: 12, weight: .bold, design: .rounded))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(FormaTheme.Colors.brand)
                    .clipShape(Capsule())
                }
                .buttonStyle(.plain)
            }
            .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
            .padding(.top, 8)

            if authManager.isPro {
                HStack(spacing: 4) {
                    ForEach(EvolutionSection.allCases) { section in
                        Button {
                            withAnimation(.spring(response: 0.32, dampingFraction: 0.82)) {
                                selectedSection = section
                            }
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: section.icon)
                                    .font(.system(size: 11, weight: .semibold))
                                Text(section.rawValue)
                                    .font(.system(size: 12.5, weight: selectedSection == section ? .bold : .medium))
                            }
                            .foregroundStyle(selectedSection == section ? .white : .secondary)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 8)
                            .background(selectedSection == section ? FormaTheme.Colors.brand : Color.clear)
                            .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }
                }
                .padding(3)
                .background(Color.primary.opacity(0.06))
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
            }
        }
        .padding(.bottom, 12)
        .background(FormaTheme.Colors.cardSurface)
    }

    private var overviewSectionView: some View {
        VStack(spacing: FormaTheme.Spacing.sectionGap) {
            weightSummaryHeroCard
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)

            WeightTrendChartCard(
                weightEntries: weightEntries,
                currentWeight: currentWeight,
                targetWeight: targetWeight,
                onLogWeightTap: { showLogWeight = true }
            )
            .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)

            WeeklyCalorieChartCard(
                summaries: weeklySummaries,
                dailyCalorieTarget: profile?.targetCalorieIntake ?? stats.suggestedCalorieIntake
            )
            .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)

            WeeklyHabitsChartCard(summaries: weeklySummaries)
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)

            metabolismMetricsSection
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)

            weightsHistorySection
        }
    }

    private var weightSummaryHeroCard: some View {
        FormaCard(padding: 20) {
            VStack(spacing: 16) {
                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        HStack(spacing: 6) {
                            Image(systemName: goal.icon)
                                .font(.system(size: 12))
                                .foregroundStyle(FormaTheme.Colors.brand)
                            Text(goal.rawValue)
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(FormaTheme.Colors.brand)
                        }
                        Text(goal.subtitle)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Spacer()

                    VStack(alignment: .trailing, spacing: 2) {
                        Text(stats.isGoalAchieved ? "Meta Batida!" : "Faltam")
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(.secondary)
                        Text(String(format: "%.1f kg", stats.remainingWeightToGoal))
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundStyle(stats.isGoalAchieved ? FormaTheme.Colors.energyGreen : FormaTheme.Colors.brand)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(FormaTheme.Colors.brandSoft)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }

                Divider().background(Color.primary.opacity(0.06))

                HStack {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Peso Atual")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        HStack(alignment: .firstTextBaseline, spacing: 2) {
                            Text(String(format: "%.1f", currentWeight))
                                .font(.system(size: 32, weight: .bold, design: .rounded))
                                .foregroundStyle(FormaTheme.Colors.primaryText)
                            Text("kg")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }

                    Spacer()

                    Image(systemName: "arrow.right")
                        .font(.body.weight(.semibold))
                        .foregroundStyle(.secondary.opacity(0.4))

                    Spacer()

                    VStack(alignment: .trailing, spacing: 3) {
                        Text("Peso Alvo")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        HStack(alignment: .firstTextBaseline, spacing: 2) {
                            Text(String(format: "%.1f", targetWeight))
                                .font(.system(size: 32, weight: .bold, design: .rounded))
                                .foregroundStyle(FormaTheme.Colors.brand)
                            Text("kg")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                }
            }
        }
    }

    private var metabolismMetricsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Seu Corpo & Gasto Diário")
                .font(.headline)
                .foregroundStyle(FormaTheme.Colors.primaryText)

            HStack(spacing: 12) {
                FormaTintedCard(tint: FormaTheme.Colors.energyGreen, padding: 14, cornerRadius: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 5) {
                            Image(systemName: "figure.arms.open")
                                .font(.system(size: 11))
                                .foregroundStyle(FormaTheme.Colors.energyGreen)
                            Text("Relação Peso & Altura")
                                .font(.caption2.weight(.medium))
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.85)
                        }
                        Text(String(format: "%.1f", stats.imc))
                            .font(.system(size: 22, weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                        FormaBadge(text: stats.imcClassification, tint: FormaTheme.Colors.energyGreen)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }

                FormaTintedCard(tint: FormaTheme.Colors.brand, padding: 14, cornerRadius: 16) {
                    VStack(alignment: .leading, spacing: 6) {
                        HStack(spacing: 5) {
                            Image(systemName: "bolt.heart.fill")
                                .font(.system(size: 11))
                                .foregroundStyle(FormaTheme.Colors.brand)
                            Text("Gasto em Repouso")
                                .font(.caption2.weight(.medium))
                                .foregroundStyle(.secondary)
                                .lineLimit(1)
                                .minimumScaleFactor(0.85)
                        }
                        HStack(alignment: .firstTextBaseline, spacing: 3) {
                            Text("\(Int(stats.tmb))")
                                .font(.system(size: 22, weight: .bold, design: .rounded))
                                .foregroundStyle(FormaTheme.Colors.primaryText)
                            Text("kcal")
                                .font(.caption2)
                                .foregroundStyle(.secondary)
                        }
                        Text("Gasto total: \(Int(stats.tdee)) kcal/dia")
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    private var weightsHistorySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Histórico de Pesagens")
                .font(.headline)
                .foregroundStyle(FormaTheme.Colors.primaryText)
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)

            if weightEntries.isEmpty {
                FormaCard(padding: 24) {
                    FormaEmptyState(
                        icon: "scalemass.fill",
                        title: "Nenhuma pesagem registrada",
                        subtitle: "Registre seu peso regularmente para ajustar suas metas Forma.",
                        actionTitle: "+ Registrar Peso",
                        onAction: { showLogWeight = true }
                    )
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
            } else {
                VStack(spacing: 8) {
                    ForEach(weightEntries) { entry in
                        FormaCard(padding: 14, cornerRadius: 14) {
                            HStack {
                                VStack(alignment: .leading, spacing: 3) {
                                    Text(entry.date.formatted(.dateTime.day().month().year().hour().minute().locale(Locale(identifier: "pt_BR"))))
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(FormaTheme.Colors.primaryText)
                                    if !entry.notes.isEmpty {
                                        Text(entry.notes)
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                    }
                                }

                                Spacer()

                                Text(String(format: "%.1f kg", entry.weight))
                                    .font(.system(size: 16, weight: .bold, design: .rounded))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)

                                Button {
                                    deleteWeightEntry(entry)
                                } label: {
                                    Image(systemName: "trash")
                                        .font(.system(size: 12))
                                        .foregroundStyle(FormaTheme.Colors.danger.opacity(0.65))
                                        .padding(.leading, 8)
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
            }
        }
    }

    // MARK: - Lógica de Negócio

    private func deleteWeightEntry(_ entry: WeightEntry) {
        FormaDataStore.shared.deleteWeightEntry(entry, in: modelContext)
    }

    private func generateAIAnalysis() {
        guard authManager.isPro else {
            selectedSection = .overview
            return
        }
        if !aiConsent.hasGrantedConsent {
            aiConsent.setConsent(true)
        }
        performEvolutionAIAnalysis()
    }

    private func performEvolutionAIAnalysis() {
        guard authManager.isPro else { return }
        evolutionAnalysisTask?.cancel()
        isAnalyzingAI = true
        aiErrorMessage = nil
        appliedTargetsSuccessfully = false
        savedRoutineSuccessfully = false

        AppActivityLogManager.shared.syncFromDatabase(
            foods: foodEntries,
            workouts: workoutEntries,
            waters: waterEntries,
            weights: weightEntries
        )

        evolutionAnalysisTask = Task {
            do {
                let result = try await GeminiAIService.shared.analyzeEvolution(
                    foods: foodEntries,
                    workouts: workoutEntries,
                    waters: waterEntries,
                    weights: weightEntries,
                    profile: profile
                )
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    guard self.authManager.isPro else {
                        self.isAnalyzingAI = false
                        return
                    }
                    self.aiAnalysis = result
                    self.isAnalyzingAI = false
                    Task { @MainActor in
                        try? await FormaDataStore.shared.saveEvolutionAnalysisRemoteFirst(result)
                    }
                }
            } catch is CancellationError {
                await MainActor.run {
                    self.isAnalyzingAI = false
                }
            } catch {
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    self.isAnalyzingAI = false
                    guard self.authManager.isPro else { return }
                    self.aiErrorMessage = FormaAPIClient.humanizeErrorMessage(error.localizedDescription)
                }
            }
        }
    }

    private func applySuggestedTargets(_ targets: SuggestedNutritionTargets) {
        guard let p = profile else { return }
        p.targetCalorieIntake = targets.dailyCalories
        p.targetProtein = targets.proteinGrams
        p.targetCarbohydrates = targets.carbsGrams
        p.targetFat = targets.fatGrams
        p.targetWaterMilliliters = targets.waterMilliliters

        Task { @MainActor in
            do {
                try await FormaDataStore.shared.saveProfileRemoteFirst(p)
                withAnimation(.spring(duration: 0.35)) {
                    appliedTargetsSuccessfully = true
                    toastMessage = "Metas de \(Int(targets.dailyCalories)) kcal e macros aplicadas e salvas no banco!"
                }
                let generator = UINotificationFeedbackGenerator()
                generator.notificationOccurred(.success)
                try? await Task.sleep(nanoseconds: 3_500_000_000)
                withAnimation { toastMessage = nil }
            } catch {
                withAnimation {
                    toastMessage = "Erro ao sincronizar metas: \(FormaAPIClient.humanizeErrorMessage(error.localizedDescription))"
                }
            }
        }
    }

    private func saveRecommendedRoutine(_ routineData: AIRecommendedRoutine) {
        let newRoutine = WorkoutRoutine(
            title: routineData.title,
            workoutType: WorkoutType(rawValue: routineData.workoutType) ?? .bodybuilding,
            targetMuscles: routineData.targetMuscles,
            hypertrophyFocus: routineData.hypertrophyFocus,
            estimatedDurationMinutes: routineData.estimatedDurationMinutes,
            estimatedCalories: routineData.estimatedCalories,
            recommendedRestSeconds: routineData.recommendedRestSeconds,
            exercises: routineData.exercises,
            notes: "✨ Gerado pela Inteligência Forma"
        )

        Task { @MainActor in
            do {
                try await FormaDataStore.shared.saveWorkoutRoutineRemoteFirst(newRoutine)
                withAnimation(.spring(duration: 0.35)) {
                    savedRoutineSuccessfully = true
                    toastMessage = "Ficha salva no banco e adicionada na aba de Treinos!"
                }
                let generator = UINotificationFeedbackGenerator()
                generator.notificationOccurred(.success)
                try? await Task.sleep(nanoseconds: 3_500_000_000)
                withAnimation { toastMessage = nil }
            } catch {
                withAnimation {
                    toastMessage = "Erro ao sincronizar ficha: \(FormaAPIClient.humanizeErrorMessage(error.localizedDescription))"
                }
            }
        }
    }
}

#Preview {
    EvolutionView()
        .modelContainer(PreviewContainer.shared)
}

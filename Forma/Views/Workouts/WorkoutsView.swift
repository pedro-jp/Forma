//
//  WorkoutsView.swift
//  Forma
//
//  Painel de Treinos & Atividades com gráfico semanal de queima, assistente IA, fichas favoritas e histórico.
//

import SwiftUI
import SwiftData

struct WorkoutsView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \WorkoutEntry.date, order: .reverse) private var workouts: [WorkoutEntry]
    @Query(sort: \WorkoutRoutine.createdAt, order: .reverse) private var routines: [WorkoutRoutine]
    @StateObject private var healthVM = HealthViewModel()

    @State private var showAddWorkout: Bool = false
    @State private var showAIWorkout: Bool = false
    @State private var showManualRoutineBuilder: Bool = false
    @State private var showCatalogSheet: Bool = false
    @State private var selectedExerciseForDetail: ExerciseCatalogItem? = nil
    @State private var activeRoutineToTrain: WorkoutRoutine? = nil
    @ObservedObject private var authManager = AuthManager.shared

    private var sortedRoutines: [WorkoutRoutine] {
        routines.filter { $0.isFavorite } + routines.filter { !$0.isFavorite }
    }

    private var workoutsThisWeek: [WorkoutEntry] {
        var calendar = Calendar.current
        calendar.firstWeekday = 2
        guard let weekInterval = calendar.dateInterval(of: .weekOfYear, for: Date()) else { return [] }
        return workouts.filter { $0.date >= weekInterval.start && $0.date <= weekInterval.end }
    }

    private var currentWeekDays: [Date] {
        var calendar = Calendar.current
        calendar.firstWeekday = 2
        let now = Date()
        let startOfWeek = calendar.dateInterval(of: .weekOfYear, for: now)?.start ?? calendar.startOfDay(for: now)
        return (0..<7).compactMap { calendar.date(byAdding: .day, value: $0, to: startOfWeek) }
    }

    private var totalMinutesWeek: Int {
        workoutsThisWeek.reduce(0) { $0 + $1.durationMinutes }
    }

    private var totalCaloriesBurnedWeek: Double {
        workoutsThisWeek.reduce(0.0) { $0 + $1.caloriesBurned }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: FormaTheme.Spacing.sectionGap) {
                    FormaScreenHeader(
                        title: "Atividade & Treinos",
                        subtitle: "Fichas, evolução muscular e consistência"
                    ) {
                        Button {
                            showAddWorkout = true
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "plus")
                                    .font(.system(size: 13, weight: .bold))
                                Text("Manual")
                                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                            }
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .padding(.horizontal, 14)
                            .padding(.vertical, 8)
                            .background(FormaTheme.Colors.cardSurface)
                            .clipShape(Capsule())
                            .shadow(color: Color.black.opacity(0.04), radius: 4, x: 0, y: 1)
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)

                    weeklyConsistencyCard
                        .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)

                    if authManager.isPro {
                        aiAssistantBanner
                            .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
                    } else {
                        catalogQuickCard
                            .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
                    }

                    routinesSection

                    historySection
                }
                .padding(.top, 10)
                .padding(.bottom, 36)
            }
            .scrollIndicators(.hidden)
            .background(FormaTheme.Colors.pageBackground.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                healthVM.loadHealthData()
                FormaDataStore.shared.syncExternalHealthWorkoutsInBackground(in: modelContext)
                if !authManager.isPro {
                    showAIWorkout = false
                }
            }
            .onChange(of: authManager.accountTier) { _, newTier in
                if newTier == .free {
                    showAIWorkout = false
                }
            }
            .sheet(isPresented: $showAddWorkout) {
                AddWorkoutView()
                    .environment(\.modelContext, modelContext)
            }
            .sheet(isPresented: $showAIWorkout) {
                AIWorkoutLoggerView()
                    .environment(\.modelContext, modelContext)
            }
            .sheet(isPresented: $showManualRoutineBuilder) {
                ManualRoutineBuilderView()
                    .environment(\.modelContext, modelContext)
            }
            .sheet(isPresented: $showCatalogSheet) {
                ExerciseCatalogSheet()
            }
            .sheet(item: $selectedExerciseForDetail) { item in
                ExerciseDetailView(item: item)
            }
            .fullScreenCover(item: $activeRoutineToTrain) { routine in
                ActiveWorkoutSessionView(
                    title: routine.title,
                    workoutType: routine.workoutType,
                    targetMuscles: routine.targetMuscles,
                    hypertrophyFocus: routine.hypertrophyFocus,
                    baseCalories: routine.estimatedCalories,
                    baseDurationMinutes: routine.estimatedDurationMinutes,
                    initialRestSeconds: routine.recommendedRestSeconds,
                    exercises: routine.exercises,
                    routineToUpdate: routine
                )
            }
        }
    }

    private var weeklyConsistencyCard: some View {
        FormaCard(padding: 20) {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Semana Atual")
                            .font(.headline)
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                        Text("Consistência e queima de Segunda a Domingo")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    Spacer()
                    FormaBadge(text: "\(workoutsThisWeek.count) treinos", icon: "flame.fill", tint: FormaTheme.Colors.brand)
                }

                HStack(spacing: 8) {
                    let calendar = Calendar.current
                    ForEach(currentWeekDays, id: \.self) { day in
                        let hasTrained = workouts.contains { calendar.isDate($0.date, inSameDayAs: day) }
                        let isToday = calendar.isDateInToday(day)
                        let isFuture = day > Date() && !isToday
                        let dayLetter = day.formatted(.dateTime.weekday(.abbreviated).locale(Locale(identifier: "pt_BR")))
                            .replacingOccurrences(of: ".", with: "")
                            .capitalized
                        let dayNumber = day.formatted(.dateTime.day())

                        VStack(spacing: 6) {
                            Text(dayLetter)
                                .font(.system(size: 11, weight: isToday ? .bold : .medium))
                                .foregroundStyle(isToday ? FormaTheme.Colors.brand : .secondary)

                            ZStack {
                                Circle()
                                    .fill(hasTrained ? FormaTheme.Colors.brand : (isToday ? FormaTheme.Colors.brandSoft : Color.gray.opacity(0.12)))
                                    .frame(width: 36, height: 36)
                                    .overlay(
                                        Circle()
                                            .stroke(isToday ? FormaTheme.Colors.brand : Color.clear, lineWidth: 1.5)
                                    )

                                if hasTrained {
                                    Image(systemName: "checkmark")
                                        .font(.system(size: 12, weight: .bold))
                                        .foregroundStyle(.white)
                                } else {
                                    Text(dayNumber)
                                        .font(.system(size: 12, weight: isToday ? .bold : .medium, design: .rounded))
                                        .foregroundStyle(isFuture ? .secondary.opacity(0.35) : (isToday ? FormaTheme.Colors.brand : .secondary))
                                }
                            }
                        }
                        .frame(maxWidth: .infinity)
                    }
                }

                Divider().background(Color.primary.opacity(0.06))

                HStack(spacing: 8) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Tempo")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                        Text("\(totalMinutesWeek) min")
                            .font(.system(size: 17, weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    Spacer(minLength: 4)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Gasto em Treinos")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                        Text("\(Int(totalCaloriesBurnedWeek.rounded())) kcal")
                            .font(.system(size: 17, weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.energyGreen)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                    Spacer(minLength: 4)
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Ativas Hoje")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                            .minimumScaleFactor(0.85)
                        Text("\(Int(healthVM.activeCalories.rounded())) kcal")
                            .font(.system(size: 17, weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.brand)
                            .lineLimit(1)
                            .minimumScaleFactor(0.8)
                    }
                }
            }
        }
    }

    private var aiAssistantBanner: some View {
        Button {
            showAIWorkout = true
        } label: {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(FormaTheme.Colors.aiGradient)
                        .frame(width: 48, height: 48)
                    Image(systemName: "sparkles")
                        .font(.system(size: 20, weight: .semibold))
                        .foregroundStyle(.white)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text("Criar ou Calcular Treino")
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                        .lineLimit(1)
                    Text("Inteligência Forma • gasto calórico, execução e ficha interativa")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }

                Spacer(minLength: 6)

                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.secondary)
            }
            .padding(16)
            .background(FormaTheme.Colors.cardSurface)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .shadow(color: Color.black.opacity(0.03), radius: 8, x: 0, y: 2)
            .overlay(
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .stroke(FormaTheme.Colors.aiAccent.opacity(0.22), lineWidth: 1)
            )
        }
        .buttonStyle(.plain)
    }

    private var catalogQuickCard: some View {
        Button {
            showCatalogSheet = true
        } label: {
            HStack(spacing: 14) {
                ZStack {
                    Circle()
                        .fill(FormaTheme.Colors.brandSoft)
                        .frame(width: 46, height: 46)
                    Image(systemName: "figure.strengthtraining.traditional")
                        .font(.system(size: 19, weight: .semibold))
                        .foregroundStyle(FormaTheme.Colors.brand)
                }

                VStack(alignment: .leading, spacing: 3) {
                    Text("Catálogo 3D de Execução")
                        .font(.system(size: 15, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                        .lineLimit(1)
                    Text("82 vídeos anatômicos com músculo alvo destacado e guia técnico")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                        .lineLimit(2)
                }

                Spacer(minLength: 6)

                Image(systemName: "chevron.right")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.secondary)
            }
            .padding(16)
            .background(FormaTheme.Colors.cardSurface)
            .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
            .shadow(color: Color.black.opacity(0.03), radius: 8, x: 0, y: 2)
        }
        .buttonStyle(.plain)
    }

    private var routinesSection: some View {
        VStack(alignment: .leading, spacing: 14) {
            HStack {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Minhas Fichas de Treino")
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                    Text("Favoritas no topo • checklist e timer na Ilha Dinâmica")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }

                Spacer()

                if authManager.isPro {
                    Menu {
                        Button {
                            showAIWorkout = true
                        } label: {
                            Label("Criar com Inteligência Forma", systemImage: "sparkles")
                        }
                        Button {
                            showManualRoutineBuilder = true
                        } label: {
                            Label("Montar Ficha Manual", systemImage: "list.clipboard")
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "plus")
                                .font(.system(size: 11, weight: .bold))
                            Text("Nova Ficha")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                        }
                        .foregroundStyle(FormaTheme.Colors.brand)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(FormaTheme.Colors.brandSoft)
                        .clipShape(Capsule())
                    }
                } else {
                    Button {
                        showManualRoutineBuilder = true
                    } label: {
                        HStack(spacing: 4) {
                            Image(systemName: "plus")
                                .font(.system(size: 11, weight: .bold))
                            Text("Nova Ficha")
                                .font(.system(size: 12, weight: .bold, design: .rounded))
                        }
                        .foregroundStyle(FormaTheme.Colors.brand)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(FormaTheme.Colors.brandSoft)
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }
            }
            .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)

            if sortedRoutines.isEmpty {
                FormaCard(padding: 20) {
                    FormaEmptyState(
                        icon: "list.clipboard",
                        title: "Nenhuma ficha salva",
                        subtitle: authManager.isPro
                            ? "Crie sua primeira ficha com a Inteligência Forma ou monte manualmente com vídeos 3D de execução."
                            : "Monte sua ficha escolhendo exercícios do catálogo 3D para acompanhar séries, cargas e descanso na Ilha Dinâmica.",
                        actionTitle: "Criar Ficha",
                        onAction: {
                            if authManager.isPro {
                                showAIWorkout = true
                            } else {
                                showManualRoutineBuilder = true
                            }
                        }
                    )
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
            } else {
                VStack(spacing: 12) {
                    ForEach(sortedRoutines) { routine in
                        RoutineCard(
                            routine: routine,
                            onStart: { activeRoutineToTrain = routine },
                            onToggleFavorite: { try? FormaDataStore.shared.toggleFavoriteRoutine(routine) },
                            onDelete: { try? FormaDataStore.shared.deleteWorkoutRoutine(routine, in: modelContext) },
                            onShowExerciseDetail: { ex in
                                selectedExerciseForDetail = ExerciseCatalog.shared.item(withId: ex.animationId)
                                    ?? ExerciseCatalog.shared.findBestMatch(for: ex.name)
                            }
                        )
                    }
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
            }
        }
    }

    private var historySection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Histórico de Sessões")
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundStyle(FormaTheme.Colors.primaryText)
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)

            if workouts.isEmpty {
                FormaCard(padding: 20) {
                    FormaEmptyState(
                        icon: "dumbbell.fill",
                        title: "Nenhum treino registrado ainda",
                        subtitle: "Inicie uma ficha com checklist ou registre seu treino manualmente."
                    )
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
            } else {
                VStack(spacing: 10) {
                    ForEach(workouts) { workout in
                        WorkoutCard(workout: workout) {
                            FormaDataStore.shared.deleteWorkoutEntry(workout, in: modelContext)
                        }
                    }
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
            }
        }
    }
}

#Preview {
    WorkoutsView()
        .modelContainer(PreviewContainer.shared)
}

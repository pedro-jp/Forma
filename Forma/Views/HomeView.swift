//
//  HomeView.swift
//  Forma
//
//  Painel diário principal do Forma com resumo energético, hidratação, refeições, passos e treinos.
//

import SwiftUI
import SwiftData

struct HomeView: View {
    @Environment(\.modelContext) private var modelContext

    @Query private var profiles: [Profile]
    @Query private var healthRecords: [Health]
    @Query(sort: \FoodEntry.createdAt, order: .reverse) private var allFoods: [FoodEntry]
    @Query(sort: \WorkoutEntry.date, order: .reverse) private var allWorkouts: [WorkoutEntry]
    @Query(sort: \WaterEntry.date, order: .reverse) private var allWaterEntries: [WaterEntry]

    @StateObject private var healthVM = HealthViewModel()
    @ObservedObject private var router = AppNavigationRouter.shared
    @ObservedObject private var authManager = AuthManager.shared

    @State private var activeMetric: FocusMetric = .consumed
    @State private var showAddWorkoutSheet: Bool = false
    @State private var showLogWeightSheet: Bool = false
    @State private var showClearConfirmAlert: Bool = false
    @State private var openCameraInFoodSheet: Bool = false

    private var profile: Profile? { profiles.first }

    private var nomeUsuario: String {
        if let profileName = profile?.nome.trimmingCharacters(in: .whitespacesAndNewlines),
           !profileName.isEmpty {
            return profileName
        }
        if let authName = AuthManager.shared.currentUserName?.trimmingCharacters(in: .whitespacesAndNewlines),
           !authName.isEmpty {
            return authName
        }
        return "Atleta"
    }

    private var objetivo: FitnessGoal { profile?.goal ?? .gain }
    private var metaCaloriasConsumir: Double { profile?.targetCalorieIntake ?? 2800.0 }
    private var metaCaloriasGastar: Double { profile?.targetCalorieBurn ?? 600.0 }
    private var metaPassos: Double { profile?.targetSteps ?? 8000.0 }

    private var metaProtein: Double {
        if let p = profile, p.targetProtein > 0 { return p.targetProtein }
        return healthRecords.first?.protein ?? 160.0
    }

    private var metaFat: Double {
        if let p = profile, p.targetFat > 0 { return p.targetFat }
        return healthRecords.first?.fat ?? 70.0
    }

    private var metaCarbo: Double {
        if let p = profile, p.targetCarbohydrates > 0 { return p.targetCarbohydrates }
        return healthRecords.first?.carbohydrates ?? 380.0
    }

    private var foodsToday: [FoodEntry] {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        return allFoods.filter {
            calendar.isDateInToday($0.date) || calendar.startOfDay(for: $0.date) == today
        }
    }

    private var workoutsToday: [WorkoutEntry] {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        return allWorkouts.filter {
            calendar.isDateInToday($0.date) || calendar.startOfDay(for: $0.date) == today
        }
    }

    private var waterToday: [WaterEntry] {
        let calendar = Calendar.current
        let today = calendar.startOfDay(for: Date())
        return allWaterEntries.filter {
            calendar.isDateInToday($0.date) || calendar.startOfDay(for: $0.date) == today
        }
    }

    private var caloriasConsumidasHoje: Double { foodsToday.reduce(0.0) { $0 + $1.calories } }
    private var proteinasConsumidasHoje: Double { foodsToday.reduce(0.0) { $0 + $1.protein } }
    private var carbosConsumidosHoje: Double { foodsToday.reduce(0.0) { $0 + $1.carbohydrates } }
    private var gordurasConsumidasHoje: Double { foodsToday.reduce(0.0) { $0 + $1.fat } }
    private var caloriasTreinosHoje: Double { workoutsToday.reduce(0.0) { $0 + $1.caloriesBurned } }
    private var caloriasGastasAtivas: Double { max(healthVM.activeCalories, caloriasTreinosHoje) }
    private var waterConsumedToday: Int { waterToday.reduce(0) { $0 + $1.milliliters } }
    private var waterTarget: Int { profile?.targetWaterMilliliters ?? 2500 }

    private var metaCaloriasConsumirAjustada: Double {
        metaCaloriasConsumir + caloriasTreinosHoje
    }

    private var caloriasRestantesHoje: Double {
        metaCaloriasConsumirAjustada - caloriasConsumidasHoje
    }

    private func saudacao(nome: String) -> String {
        let hora = Calendar.current.component(.hour, from: .now)
        switch hora {
        case 5..<12: return "Bom dia, \(nome)"
        case 12..<18: return "Boa tarde, \(nome)"
        default: return "Boa noite, \(nome)"
        }
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: FormaTheme.Spacing.sectionGap) {
                    headerView

                    HomeEnergyHeroCard(
                        activeMetric: $activeMetric,
                        goal: objetivo,
                        caloriesConsumed: caloriasConsumidasHoje,
                        adjustedCalorieTarget: metaCaloriasConsumirAjustada,
                        activeCaloriesBurned: caloriasGastasAtivas,
                        activeBurnTarget: metaCaloriasGastar,
                        remainingCalories: caloriasRestantesHoje,
                        proteinConsumed: proteinasConsumidasHoje,
                        proteinTarget: metaProtein,
                        carbsConsumed: carbosConsumidosHoje,
                        carbsTarget: metaCarbo,
                        fatConsumed: gordurasConsumidasHoje,
                        fatTarget: metaFat
                    )

                    FormaHydrationCard(
                        consumedMl: waterConsumedToday,
                        targetMl: waterTarget,
                        onAddWater: { ml in
                            let entry = WaterEntry(milliliters: ml, date: Date())
                            try? FormaDataStore.shared.insertWaterEntry(entry)
                        }
                    )

                    HomeDailyLogsSection(
                        isPro: authManager.isPro,
                        foodsToday: foodsToday,
                        workoutsToday: workoutsToday,
                        steps: healthVM.steps,
                        stepsTarget: metaPassos,
                        distanceKm: healthVM.distanceKm,
                        onClearMealsTap: { showClearConfirmAlert = true },
                        onOpenCameraTap: {
                            openCameraInFoodSheet = true
                            router.showVoiceFoodSheet = true
                        },
                        onAddMealTap: {
                            openCameraInFoodSheet = false
                            router.showVoiceFoodSheet = true
                        },
                        onDeleteFood: { food in
                            FormaDataStore.shared.deleteFoodEntry(food, in: modelContext)
                            syncWidgetsAndLiveActivity()
                        },
                        onAddWorkoutTap: { showAddWorkoutSheet = true },
                        onDeleteWorkout: { workout in
                            FormaDataStore.shared.deleteWorkoutEntry(workout, in: modelContext)
                            syncWidgetsAndLiveActivity()
                        },
                        onLogWeightTap: { showLogWeightSheet = true }
                    )
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
                .padding(.top, 10)
            }
            .scrollIndicators(.hidden)
            .background(FormaTheme.Colors.pageBackground.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .alert("Limpar Alimentos de Hoje?", isPresented: $showClearConfirmAlert) {
                Button("Cancelar", role: .cancel) {}
                Button("Limpar Tudo", role: .destructive) {
                    FormaDataStore.shared.deleteFoodEntries(foodsToday, in: modelContext)
                    syncWidgetsAndLiveActivity()
                }
            } message: {
                Text("Isso removerá todos os alimentos registrados hoje para começar novamente.")
            }
            .onAppear {
                healthVM.loadHealthData()
                FormaDataStore.shared.syncExternalHealthWorkoutsInBackground(in: modelContext)
                if let p = profile {
                    activeMetric = p.effectiveFocus
                }
                syncWidgetsAndLiveActivity()
            }
            .onChange(of: allFoods.count) { _, _ in syncWidgetsAndLiveActivity() }
            .onChange(of: allWorkouts.count) { _, _ in syncWidgetsAndLiveActivity() }
            .onChange(of: healthVM.activeCalories) { _, _ in syncWidgetsAndLiveActivity() }
            .onChange(of: healthVM.steps) { _, _ in syncWidgetsAndLiveActivity() }
            .onChange(of: authManager.accountTier) { _, newTier in
                if newTier == .free {
                    openCameraInFoodSheet = false
                    router.cutAllAIFlowsForFreeTier()
                }
            }
            .task {
                FormaDataStore.shared.ensureSingleProfile(in: modelContext)
                if healthRecords.isEmpty {
                    modelContext.insert(Health())
                    try? modelContext.save()
                }
                syncWidgetsAndLiveActivity()
            }
            .sheet(isPresented: $router.showAddFoodSheet) {
                VoiceAddFoodView { newEntry in
                    syncWidgetsAndLiveActivity(lastAddedName: newEntry.name, lastAddedCalories: newEntry.calories)
                }
                .environment(\.modelContext, modelContext)
            }
            .sheet(isPresented: $router.showVoiceFoodSheet, onDismiss: { openCameraInFoodSheet = false }) {
                VoiceAddFoodView(initialOpenCamera: openCameraInFoodSheet) { newEntry in
                    syncWidgetsAndLiveActivity(lastAddedName: newEntry.name, lastAddedCalories: newEntry.calories)
                }
                .environment(\.modelContext, modelContext)
            }
            .sheet(isPresented: $router.showAIFoodSheet) {
                VoiceAddFoodView { newEntry in
                    syncWidgetsAndLiveActivity(lastAddedName: newEntry.name, lastAddedCalories: newEntry.calories)
                }
                .environment(\.modelContext, modelContext)
            }
            .sheet(isPresented: $showAddWorkoutSheet) {
                AddWorkoutView()
                    .environment(\.modelContext, modelContext)
            }
            .sheet(isPresented: $showLogWeightSheet) {
                LogWeightView()
                    .environment(\.modelContext, modelContext)
            }
            .sheet(isPresented: $router.showAIWorkoutSheet) {
                AIWorkoutLoggerView {
                    syncWidgetsAndLiveActivity()
                }
                .environment(\.modelContext, modelContext)
            }
        }
    }

    private var headerView: some View {
        HStack(spacing: 14) {
            Image("profile")
                .resizable()
                .scaledToFill()
                .frame(width: 48, height: 48)
                .clipShape(Circle())
                .overlay(Circle().stroke(Color.primary.opacity(0.08), lineWidth: 1.5))

            VStack(alignment: .leading, spacing: 3) {
                Text(saudacao(nome: nomeUsuario))
                    .font(.system(size: 22, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .lineLimit(1)

                Text(
                    Date.now.formatted(
                        .dateTime
                            .weekday(.wide)
                            .day()
                            .month(.wide)
                            .locale(Locale(identifier: "pt_BR"))
                    )
                    .capitalized
                )
                .font(.subheadline)
                .foregroundStyle(.secondary)
            }

            Spacer()

            VStack(alignment: .trailing, spacing: 6) {
                if authManager.isPro {
                    HStack(spacing: 4) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 9, weight: .bold))
                        Text("PRO")
                            .font(.system(size: 10, weight: .heavy, design: .rounded))
                    }
                    .foregroundStyle(.white)
                    .padding(.horizontal, 8)
                    .padding(.vertical, 3)
                    .background(FormaTheme.Colors.aiGradient)
                    .clipShape(Capsule())
                } else {
                    Text("Essencial")
                        .font(.system(size: 10, weight: .semibold, design: .rounded))
                        .foregroundStyle(.secondary)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 3)
                        .background(Color.primary.opacity(0.06))
                        .clipShape(Capsule())
                }

                FormaBadge(
                    text: objetivo == .gain ? "Ganho" : (objetivo == .lose ? "Perda" : "Manutenção"),
                    icon: objetivo.icon,
                    tint: FormaTheme.Colors.brand
                )
            }
        }
    }

    private func syncWidgetsAndLiveActivity(lastAddedName: String? = nil, lastAddedCalories: Double? = nil) {
        FormaDataStore.shared.syncTodayData(
            lastFoodName: lastAddedName,
            lastFoodCalories: lastAddedCalories,
            activeCaloriesOverride: caloriasGastasAtivas,
            stepsOverride: Int(healthVM.steps)
        )
    }
}

#Preview {
    HomeView()
        .modelContainer(PreviewContainer.shared)
}

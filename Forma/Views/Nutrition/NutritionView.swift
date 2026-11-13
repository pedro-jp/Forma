//
//  NutritionView.swift
//  Forma
//
//  Diário alimentar diário e semanal com distribuição calórica de macronutrientes, hidratação e seções por refeição.
//

import SwiftUI
import SwiftData

struct NutritionView: View {
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \FoodEntry.createdAt, order: .reverse) private var allFoodEntries: [FoodEntry]
    @Query(sort: \WorkoutEntry.date, order: .reverse) private var allWorkouts: [WorkoutEntry]
    @Query(sort: \WaterEntry.date, order: .reverse) private var allWaterEntries: [WaterEntry]
    @Query private var profiles: [Profile]
    @Query private var healthRecords: [Health]

    @State private var selectedDate: Date = Date()
    @State private var showVoiceFoodSheet: Bool = false
    @State private var openCameraDirectly: Bool = false
    @State private var mealToAdd: MealType = .lunch
    @ObservedObject private var authManager = AuthManager.shared

    private var profile: Profile? { profiles.first }

    private var currentWeekDays: [Date] {
        var calendar = Calendar.current
        calendar.firstWeekday = 2
        let now = Date()
        let startOfWeek = calendar.dateInterval(of: .weekOfYear, for: now)?.start ?? calendar.startOfDay(for: now)
        return (0..<7).compactMap { calendar.date(byAdding: .day, value: $0, to: startOfWeek) }
    }

    private func hasFoodLogged(on date: Date) -> Bool {
        let calendar = Calendar.current
        return allFoodEntries.contains { calendar.isDate($0.date, inSameDayAs: date) }
    }

    private var foodsForSelectedDate: [FoodEntry] {
        let calendar = Calendar.current
        let targetStart = calendar.startOfDay(for: selectedDate)
        return allFoodEntries.filter {
            calendar.isDate($0.date, inSameDayAs: selectedDate) ||
            calendar.startOfDay(for: $0.date) == targetStart
        }
    }

    private var workoutsForSelectedDate: [WorkoutEntry] {
        let calendar = Calendar.current
        let targetStart = calendar.startOfDay(for: selectedDate)
        return allWorkouts.filter {
            calendar.isDate($0.date, inSameDayAs: selectedDate) ||
            calendar.startOfDay(for: $0.date) == targetStart
        }
    }

    private var waterForSelectedDate: [WaterEntry] {
        let calendar = Calendar.current
        let targetStart = calendar.startOfDay(for: selectedDate)
        return allWaterEntries.filter {
            calendar.isDate($0.date, inSameDayAs: selectedDate) ||
            calendar.startOfDay(for: $0.date) == targetStart
        }
    }

    private var workoutCaloriesForSelectedDate: Double {
        workoutsForSelectedDate.reduce(0.0) { $0 + $1.caloriesBurned }
    }

    private var totalWaterMl: Int {
        waterForSelectedDate.reduce(0) { $0 + $1.milliliters }
    }

    private var targetWaterMl: Int {
        profile?.targetWaterMilliliters ?? 2500
    }

    private var totalCalories: Double { foodsForSelectedDate.reduce(0.0) { $0 + $1.calories } }
    private var totalProtein: Double { foodsForSelectedDate.reduce(0.0) { $0 + $1.protein } }
    private var totalCarbs: Double { foodsForSelectedDate.reduce(0.0) { $0 + $1.carbohydrates } }
    private var totalFat: Double { foodsForSelectedDate.reduce(0.0) { $0 + $1.fat } }

    private var targetCaloriesBase: Double { profile?.targetCalorieIntake ?? 2800.0 }
    private var targetCaloriesAdjusted: Double { targetCaloriesBase + workoutCaloriesForSelectedDate }

    private var targetProtein: Double {
        if let p = profile, p.targetProtein > 0 { return p.targetProtein }
        return healthRecords.first?.protein ?? 160.0
    }

    private var targetCarbs: Double {
        if let p = profile, p.targetCarbohydrates > 0 { return p.targetCarbohydrates }
        return healthRecords.first?.carbohydrates ?? 380.0
    }

    private var targetFat: Double {
        if let p = profile, p.targetFat > 0 { return p.targetFat }
        return healthRecords.first?.fat ?? 70.0
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: FormaTheme.Spacing.sectionGap) {
                    FormaScreenHeader(
                        title: "Diário Alimentar",
                        subtitle: authManager.isPro
                            ? "Controle diário de calorias e nutrientes"
                            : "Controle diário pela Tabela TACO e hidratação"
                    ) {
                        HStack(spacing: 8) {
                            if authManager.isPro {
                                Button {
                                    mealToAdd = currentSuggestedMeal()
                                    openCameraDirectly = true
                                    showVoiceFoodSheet = true
                                } label: {
                                    Image(systemName: "camera.fill")
                                        .font(.system(size: 14, weight: .semibold))
                                        .foregroundStyle(FormaTheme.Colors.brand)
                                        .frame(width: 36, height: 36)
                                        .background(FormaTheme.Colors.brandSoft)
                                        .clipShape(Circle())
                                }
                                .buttonStyle(.plain)
                            }

                            Button {
                                mealToAdd = currentSuggestedMeal()
                                openCameraDirectly = false
                                showVoiceFoodSheet = true
                            } label: {
                                HStack(spacing: 6) {
                                    Image(systemName: authManager.isPro ? "mic.fill" : "plus")
                                        .font(.system(size: 13, weight: .semibold))
                                    Text(authManager.isPro ? "Refeição" : "Adicionar")
                                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                                }
                                .foregroundStyle(.white)
                                .padding(.horizontal, 15)
                                .padding(.vertical, 8)
                                .background(FormaTheme.Colors.brand)
                                .clipShape(Capsule())
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    weeklyCalendarStrip
                    calorieBalanceCard

                    FormaHydrationCard(
                        consumedMl: totalWaterMl,
                        targetMl: targetWaterMl,
                        onAddWater: { ml in
                            let entry = WaterEntry(milliliters: ml, date: selectedDate)
                            try? FormaDataStore.shared.insertWaterEntry(entry)
                        }
                    )

                    VStack(spacing: 14) {
                        ForEach(MealType.allCases) { meal in
                            MealSectionCard(
                                mealType: meal,
                                entries: foodsForSelectedDate.filter { $0.mealType == meal },
                                onAddTap: {
                                    mealToAdd = meal
                                    openCameraDirectly = false
                                    showVoiceFoodSheet = true
                                },
                                onCameraTap: authManager.isPro ? {
                                    mealToAdd = meal
                                    openCameraDirectly = true
                                    showVoiceFoodSheet = true
                                } : nil,
                                onDeleteEntry: { entry in
                                    FormaDataStore.shared.deleteFoodEntry(entry, in: modelContext)
                                }
                            )
                        }
                    }
                    .padding(.bottom, 28)
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
                .padding(.top, 10)
            }
            .scrollIndicators(.hidden)
            .id(allFoodEntries.count)
            .background(FormaTheme.Colors.pageBackground.ignoresSafeArea())
            .navigationBarTitleDisplayMode(.inline)
            .onChange(of: authManager.accountTier) { _, newTier in
                if newTier == .free {
                    openCameraDirectly = false
                }
            }
            .sheet(isPresented: $showVoiceFoodSheet, onDismiss: { openCameraDirectly = false }) {
                VoiceAddFoodView(preselectedMeal: mealToAdd, initialOpenCamera: openCameraDirectly) { newEntry in
                    FormaDataStore.shared.syncTodayData(lastFoodName: newEntry.name, lastFoodCalories: newEntry.calories)
                }
                .environment(\.modelContext, modelContext)
            }
        }
    }

    private var weeklyCalendarStrip: some View {
        FormaCard(padding: 8, cornerRadius: 18) {
            HStack(spacing: 6) {
                let calendar = Calendar.current
                ForEach(currentWeekDays, id: \.self) { day in
                    let isSelected = calendar.isDate(day, inSameDayAs: selectedDate)
                    let isToday = calendar.isDateInToday(day)
                    let logged = hasFoodLogged(on: day)
                    let dayLetter = day.formatted(.dateTime.weekday(.abbreviated).locale(Locale(identifier: "pt_BR")))
                        .replacingOccurrences(of: ".", with: "")
                        .capitalized
                    let dayNumber = day.formatted(.dateTime.day())

                    Button {
                        withAnimation(.spring(duration: 0.25)) {
                            selectedDate = day
                        }
                    } label: {
                        VStack(spacing: 4) {
                            Text(dayLetter)
                                .font(.system(size: 11, weight: isSelected ? .bold : .medium))
                                .foregroundStyle(isSelected ? FormaTheme.Colors.brand : .secondary)

                            Text(dayNumber)
                                .font(.system(size: 13, weight: isSelected || isToday ? .bold : .regular, design: .rounded))
                                .foregroundStyle(isSelected ? .white : (isToday ? FormaTheme.Colors.brand : FormaTheme.Colors.primaryText))
                                .frame(width: 34, height: 34)
                                .background(
                                    Circle()
                                        .fill(isSelected ? FormaTheme.Colors.brand : (isToday ? FormaTheme.Colors.brandSoft : Color.clear))
                                )

                            Circle()
                                .fill(logged ? FormaTheme.Colors.energyGreen : Color.clear)
                                .frame(width: 5, height: 5)
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 4)
                    }
                    .buttonStyle(.plain)
                }
            }
        }
    }

    private var calorieBalanceCard: some View {
        FormaCard(padding: 20) {
            VStack(spacing: 16) {
                HStack(alignment: .top) {
                    VStack(alignment: .leading, spacing: 3) {
                        Text("Consumo do Dia")
                            .font(.subheadline)
                            .foregroundStyle(.secondary)

                        HStack(alignment: .firstTextBaseline, spacing: 4) {
                            Text("\(Int(totalCalories.rounded()))")
                                .font(.system(size: 34, weight: .bold, design: .rounded))
                                .foregroundStyle(FormaTheme.Colors.primaryText)
                                .contentTransition(.numericText())
                            Text("/ \(Int(targetCaloriesAdjusted.rounded())) kcal")
                                .font(.subheadline.weight(.semibold))
                                .foregroundStyle(.secondary)
                        }

                        let dailyTmb = profile?.tmb ?? 1800.0
                        let totalBurned = dailyTmb + workoutCaloriesForSelectedDate
                        HStack(spacing: 4) {
                            Image(systemName: "flame.fill")
                                .font(.system(size: 10))
                            Text("Gasto Total: \(Int(totalBurned.rounded())) kcal (Repouso \(Int(dailyTmb.rounded()))\(workoutCaloriesForSelectedDate > 0 ? " + \(Int(workoutCaloriesForSelectedDate.rounded())) treino" : ""))")
                                .font(.caption2.weight(.semibold))
                                .lineLimit(1)
                                .minimumScaleFactor(0.8)
                        }
                        .foregroundStyle(FormaTheme.Colors.energyGreen)
                        .padding(.top, 2)
                    }

                    Spacer(minLength: 8)

                    let remaining = targetCaloriesAdjusted - totalCalories
                    VStack(alignment: .trailing, spacing: 2) {
                        Text(remaining >= 0 ? "Disponível" : "Acima da meta")
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                        Text("\(abs(Int(remaining.rounded()))) kcal")
                            .font(.system(size: 16, weight: .bold, design: .rounded))
                            .foregroundStyle(remaining >= 0 ? FormaTheme.Colors.brand : FormaTheme.Colors.energyGreen)
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }
                    .padding(.horizontal, 12)
                    .padding(.vertical, 7)
                    .background(FormaTheme.Colors.brandSoft)
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    .fixedSize(horizontal: true, vertical: false)
                }

                FormaProgressBar(value: totalCalories, total: targetCaloriesAdjusted, color: FormaTheme.Colors.brand, height: 7)

                if totalCalories > 0 {
                    macroCaloricDistributionBar
                }

                Divider().background(Color.primary.opacity(0.06))

                FormaMacroProgressRow(
                    proteinCurrent: totalProtein,
                    proteinTarget: targetProtein,
                    carbsCurrent: totalCarbs,
                    carbsTarget: targetCarbs,
                    fatCurrent: totalFat,
                    fatTarget: targetFat
                )
            }
        }
    }

    private var macroCaloricDistributionBar: some View {
        let pKcal = max(0, totalProtein * 4.0)
        let cKcal = max(0, totalCarbs * 4.0)
        let fKcal = max(0, totalFat * 9.0)
        let sum = max(1.0, pKcal + cKcal + fKcal)
        let pPct = Int(((pKcal / sum) * 100).rounded())
        let cPct = Int(((cKcal / sum) * 100).rounded())
        let fPct = max(0, 100 - pPct - cPct)

        return VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("Divisão Calórica")
                    .font(.caption2.weight(.semibold))
                    .foregroundStyle(.secondary)
                Spacer()
                Text("P \(pPct)% • C \(cPct)% • G \(fPct)%")
                    .font(.caption2.weight(.bold))
                    .foregroundStyle(.secondary)
            }

            GeometryReader { geo in
                HStack(spacing: 2) {
                    Capsule()
                        .fill(FormaTheme.Colors.protein)
                        .frame(width: max(4, geo.size.width * (pKcal / sum) - 1))
                    Capsule()
                        .fill(FormaTheme.Colors.carbs)
                        .frame(width: max(4, geo.size.width * (cKcal / sum) - 1))
                    Capsule()
                        .fill(FormaTheme.Colors.fat)
                        .frame(width: max(4, geo.size.width * (fKcal / sum) - 1))
                }
            }
            .frame(height: 5)
        }
    }

    private func currentSuggestedMeal() -> MealType {
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5..<11: return .breakfast
        case 11..<15: return .lunch
        case 15..<19: return .snacks
        default: return .dinner
        }
    }
}

#Preview {
    NutritionView()
        .modelContainer(PreviewContainer.shared)
}

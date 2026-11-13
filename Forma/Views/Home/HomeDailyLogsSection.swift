//
//  HomeDailyLogsSection.swift
//  Forma
//
//  Seções diárias de Refeições, Passos (Apple Saúde), Treinos e Ações Rápidas na aba Início.
//

import SwiftUI

struct HomeDailyLogsSection: View {
    var isPro: Bool = false
    let foodsToday: [FoodEntry]
    let workoutsToday: [WorkoutEntry]
    let steps: Double
    let stepsTarget: Double
    let distanceKm: Double
    let onClearMealsTap: () -> Void
    let onOpenCameraTap: () -> Void
    let onAddMealTap: () -> Void
    let onDeleteFood: (FoodEntry) -> Void
    let onAddWorkoutTap: () -> Void
    var onDeleteWorkout: ((WorkoutEntry) -> Void)? = nil
    let onLogWeightTap: () -> Void

    var body: some View {
        VStack(spacing: FormaTheme.Spacing.sectionGap) {
            mealsTodayCard
            stepsCard
            workoutsTodayCard
            quickActionsRow
        }
    }

    private var mealsTodayCard: some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 14) {
                HStack(spacing: 8) {
                    Image(systemName: "fork.knife")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(FormaTheme.Colors.brand)
                    Text("Refeições (\(foodsToday.count))")
                        .font(.system(size: 17, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)

                    Spacer(minLength: 6)

                    if !foodsToday.isEmpty {
                        Button("Limpar", action: onClearMealsTap)
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(FormaTheme.Colors.danger.opacity(0.85))
                            .lineLimit(1)
                            .fixedSize(horizontal: true, vertical: false)
                    }

                    if isPro {
                        Button(action: onOpenCameraTap) {
                            Image(systemName: "camera.fill")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(FormaTheme.Colors.brand)
                                .frame(width: 30, height: 30)
                                .background(FormaTheme.Colors.brandSoft)
                                .clipShape(Circle())
                        }
                        .buttonStyle(.plain)
                    }

                    Button(action: onAddMealTap) {
                        HStack(spacing: 4) {
                            Image(systemName: isPro ? "mic.fill" : "plus")
                                .font(.system(size: 11, weight: .bold))
                            Text(isPro ? "Refeição" : "Adicionar")
                                .font(.caption.weight(.bold))
                                .lineLimit(1)
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, 11)
                        .padding(.vertical, 6)
                        .background(FormaTheme.Colors.brand)
                        .clipShape(Capsule())
                        .fixedSize(horizontal: true, vertical: false)
                    }
                    .buttonStyle(.plain)
                }

                if foodsToday.isEmpty {
                    FormaEmptyState(
                        icon: "fork.knife",
                        title: "Nenhuma refeição registrada hoje",
                        subtitle: isPro
                            ? "Fotografe seu prato, fale pelo microfone ou digite o que comeu."
                            : "Busque alimentos rapidamente com cálculo automático pela Tabela TACO.",
                        actionTitle: isPro ? "+ Registrar Refeição" : "+ Adicionar Alimento",
                        onAction: onAddMealTap
                    )
                } else {
                    VStack(spacing: 10) {
                        ForEach(foodsToday) { food in
                            HStack(alignment: .center, spacing: 8) {
                                VStack(alignment: .leading, spacing: 3) {
                                    HStack(spacing: 6) {
                                        Text(food.name)
                                            .font(.system(size: 15, weight: .semibold))
                                            .foregroundStyle(FormaTheme.Colors.primaryText)
                                            .lineLimit(2)
                                        Text(food.mealType.rawValue)
                                            .font(.caption2.weight(.semibold))
                                            .foregroundStyle(FormaTheme.Colors.brand)
                                            .padding(.horizontal, 6)
                                            .padding(.vertical, 2)
                                            .background(FormaTheme.Colors.brandSoft)
                                            .clipShape(Capsule())
                                            .lineLimit(1)
                                            .fixedSize(horizontal: true, vertical: false)
                                    }

                                    Text("\(Int(food.portionGrams))g • P \(Int(food.protein))g  C \(Int(food.carbohydrates))g  G \(Int(food.fat))g")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .lineLimit(1)
                                        .minimumScaleFactor(0.85)
                                }

                                Spacer(minLength: 6)

                                Text("\(Int(food.calories.rounded())) kcal")
                                    .font(.system(size: 14, weight: .bold, design: .rounded))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: false)

                                Button {
                                    onDeleteFood(food)
                                } label: {
                                    Image(systemName: "trash")
                                        .font(.system(size: 12))
                                        .foregroundStyle(FormaTheme.Colors.danger.opacity(0.65))
                                        .padding(4)
                                }
                                .buttonStyle(.borderless)
                            }
                            .padding(.vertical, 2)

                            if food.id != foodsToday.last?.id {
                                Divider().background(Color.primary.opacity(0.05))
                            }
                        }
                    }
                }
            }
        }
    }

    private var stepsCard: some View {
        FormaCard(padding: 18) {
            VStack(spacing: 14) {
                HStack(spacing: 12) {
                    Image(systemName: "figure.walk")
                        .font(.system(size: 18, weight: .semibold))
                        .frame(width: 44, height: 44)
                        .foregroundStyle(FormaTheme.Colors.brand)
                        .background(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .fill(FormaTheme.Colors.brandSoft)
                        )

                    VStack(alignment: .leading, spacing: 2) {
                        HStack {
                            Text("Passos de hoje")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                            Spacer()
                            Text("Meta \(Int(stepsTarget))")
                                .font(.caption.weight(.bold))
                                .foregroundStyle(.secondary)
                        }

                        HStack(alignment: .firstTextBaseline, spacing: 10) {
                            Text("\(Int(steps))")
                                .font(.system(size: 25, weight: .bold, design: .rounded))
                                .foregroundStyle(FormaTheme.Colors.primaryText)
                                .contentTransition(.numericText())

                            if distanceKm > 0 {
                                Text(String(format: "%.1f km", distanceKm))
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.secondary)
                            }
                        }
                    }
                }

                FormaProgressBar(value: steps, total: stepsTarget, color: FormaTheme.Colors.brand, height: 6)
            }
        }
    }

    private var workoutsTodayCard: some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Image(systemName: "dumbbell.fill")
                        .foregroundStyle(FormaTheme.Colors.energyGreen)
                    Text("Treinos de Hoje")
                        .font(.headline)
                        .foregroundStyle(FormaTheme.Colors.primaryText)

                    Spacer()

                    Button(action: onAddWorkoutTap) {
                        HStack(spacing: 4) {
                            Image(systemName: "plus")
                                .font(.system(size: 11, weight: .bold))
                            Text("Treino")
                                .font(.caption.weight(.bold))
                        }
                        .foregroundStyle(FormaTheme.Colors.brand)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(FormaTheme.Colors.brandSoft)
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }

                if workoutsToday.isEmpty {
                    Text("Nenhum treino registrado hoje. Registre sua sessão ou inicie uma ficha na aba Treinos.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .padding(.vertical, 2)
                } else {
                    VStack(spacing: 8) {
                        ForEach(workoutsToday) { workout in
                            HStack(spacing: 8) {
                                Image(systemName: workout.workoutType.icon)
                                    .foregroundStyle(FormaTheme.Colors.energyGreen)
                                Text(workout.title)
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                                    .lineLimit(1)
                                Spacer(minLength: 6)
                                Text("\(workout.durationMinutes) min")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: false)
                                Text("+\(Int(workout.caloriesBurned)) kcal")
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(FormaTheme.Colors.energyGreen)
                                    .lineLimit(1)
                                    .fixedSize(horizontal: true, vertical: false)
                                if let onDeleteWorkout {
                                    Button {
                                        onDeleteWorkout(workout)
                                    } label: {
                                        Image(systemName: "trash")
                                            .font(.system(size: 12))
                                            .foregroundStyle(FormaTheme.Colors.danger.opacity(0.65))
                                            .padding(4)
                                    }
                                    .buttonStyle(.borderless)
                                }
                            }
                            .padding(.vertical, 2)
                        }
                    }
                }
            }
        }
    }

    private var quickActionsRow: some View {
        HStack(spacing: 10) {
            HomeQuickActionButton(
                title: isPro ? "+ Refeição" : "+ Alimento",
                icon: isPro ? "mic.fill" : "fork.knife",
                color: FormaTheme.Colors.brand,
                action: onAddMealTap
            )
            HomeQuickActionButton(title: "+ Treino", icon: "dumbbell.fill", color: FormaTheme.Colors.energyGreen, action: onAddWorkoutTap)
            HomeQuickActionButton(title: "+ Pesagem", icon: "scalemass.fill", color: .blue, action: onLogWeightTap)
        }
        .padding(.top, 2)
        .padding(.bottom, 24)
    }
}

struct HomeQuickActionButton: View {
    let title: String
    let icon: String
    let color: Color
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: icon)
                    .font(.system(size: 13, weight: .semibold))
                Text(title)
                    .font(.system(size: 13, weight: .semibold, design: .rounded))
                    .lineLimit(1)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 13)
            .background(color)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .shadow(color: color.opacity(0.20), radius: 6, x: 0, y: 3)
        }
        .buttonStyle(.plain)
    }
}

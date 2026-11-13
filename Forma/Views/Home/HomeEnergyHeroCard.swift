//
//  HomeEnergyHeroCard.swift
//  Forma
//
//  Card principal da aba Início com anel interativo Consumidas vs Gastas, resumo de saldo e macronutrientes.
//

import SwiftUI

struct HomeEnergyHeroCard: View {
    @Binding var activeMetric: FocusMetric
    let goal: FitnessGoal
    let caloriesConsumed: Double
    let adjustedCalorieTarget: Double
    let activeCaloriesBurned: Double
    let activeBurnTarget: Double
    let remainingCalories: Double
    let proteinConsumed: Double
    let proteinTarget: Double
    let carbsConsumed: Double
    let carbsTarget: Double
    let fatConsumed: Double
    let fatTarget: Double

    private var mainPercentage: Int {
        if activeMetric == .consumed {
            guard adjustedCalorieTarget > 0 else { return 0 }
            return Int(((caloriesConsumed / adjustedCalorieTarget) * 100).rounded())
        } else {
            guard activeBurnTarget > 0 else { return 0 }
            return Int(((activeCaloriesBurned / activeBurnTarget) * 100).rounded())
        }
    }

    var body: some View {
        FormaCard(padding: 20) {
            VStack(alignment: .leading, spacing: 18) {
                HStack {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(activeMetric == .consumed ? "Balanço Calórico" : "Queima Ativa")
                            .font(.headline)
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                        Text(activeMetric == .consumed ? "Calorias do treino somadas à meta diária" : "Treinos e atividades sincronizadas")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    Spacer()

                    FormaBadge(
                        text: "\(mainPercentage)%",
                        icon: activeMetric == .consumed ? "bolt.fill" : "flame.fill",
                        tint: activeMetric == .consumed ? FormaTheme.Colors.brand : FormaTheme.Colors.energyGreen
                    )
                }

                VStack(spacing: 10) {
                    FormaDualEnergyRing(
                        activeMetric: $activeMetric,
                        consumed: caloriesConsumed,
                        consumedTarget: adjustedCalorieTarget,
                        burned: activeCaloriesBurned,
                        burnedTarget: activeBurnTarget,
                        isBulkingMode: goal == .gain
                    )

                    HStack(spacing: 16) {
                        Button {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                activeMetric = .consumed
                            }
                        } label: {
                            HStack(spacing: 5) {
                                Circle()
                                    .fill(FormaTheme.Colors.brand)
                                    .frame(width: 7, height: 7)
                                Text("Consumidas")
                                    .font(.caption2.weight(activeMetric == .consumed ? .bold : .medium))
                                    .foregroundStyle(activeMetric == .consumed ? FormaTheme.Colors.primaryText : .secondary)
                            }
                        }
                        .buttonStyle(.plain)

                        Button {
                            withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                                activeMetric = .burned
                            }
                        } label: {
                            HStack(spacing: 5) {
                                Circle()
                                    .fill(FormaTheme.Colors.energyGreen)
                                    .frame(width: 7, height: 7)
                                Text("Queima Ativa")
                                    .font(.caption2.weight(activeMetric == .burned ? .bold : .medium))
                                    .foregroundStyle(activeMetric == .burned ? FormaTheme.Colors.primaryText : .secondary)
                            }
                        }
                        .buttonStyle(.plain)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.vertical, 2)

                HStack(spacing: 10) {
                    Button {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            activeMetric = .consumed
                        }
                    } label: {
                        HomeBalanceMetricTile(
                            title: "Consumidas",
                            value: "\(Int(caloriesConsumed.rounded()))",
                            subtitle: "meta \(Int(adjustedCalorieTarget.rounded()))",
                            color: FormaTheme.Colors.brand,
                            icon: "fork.knife",
                            isSelected: activeMetric == .consumed
                        )
                    }
                    .buttonStyle(.plain)

                    Button {
                        withAnimation(.spring(response: 0.3, dampingFraction: 0.8)) {
                            activeMetric = .burned
                        }
                    } label: {
                        HomeBalanceMetricTile(
                            title: "Ativas",
                            value: "+\(Int(activeCaloriesBurned.rounded()))",
                            subtitle: "treino + saúde",
                            color: FormaTheme.Colors.energyGreen,
                            icon: "flame.fill",
                            isSelected: activeMetric == .burned
                        )
                    }
                    .buttonStyle(.plain)

                    HomeBalanceMetricTile(
                        title: remainingCalories >= 0 ? "Disponível" : "Excedente",
                        value: "\(abs(Int(remainingCalories.rounded())))",
                        subtitle: "kcal",
                        color: remainingCalories >= 0 ? FormaTheme.Colors.brand : FormaTheme.Colors.warning,
                        icon: "sparkles"
                    )
                }

                Divider()
                    .background(Color.primary.opacity(0.06))

                VStack(alignment: .leading, spacing: 10) {
                    HStack {
                        Text("Macronutrientes")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                        Spacer()
                        Text("Consumido / Meta")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }

                    FormaMacroProgressRow(
                        proteinCurrent: proteinConsumed,
                        proteinTarget: proteinTarget,
                        carbsCurrent: carbsConsumed,
                        carbsTarget: carbsTarget,
                        fatCurrent: fatConsumed,
                        fatTarget: fatTarget
                    )
                }
            }
        }
    }
}

struct HomeBalanceMetricTile: View {
    let title: String
    let value: String
    let subtitle: String
    let color: Color
    let icon: String
    var isSelected: Bool = false

    var body: some View {
        VStack(alignment: .leading, spacing: 5) {
            HStack(spacing: 4) {
                Image(systemName: icon)
                    .font(.system(size: 10, weight: .bold))
                    .foregroundStyle(color)
                Text(title)
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(FormaTheme.Colors.primaryText.opacity(0.88))
                    .lineLimit(1)
                    .minimumScaleFactor(0.75)
            }

            Text(value)
                .font(.system(size: 18, weight: .bold, design: .rounded))
                .foregroundStyle(FormaTheme.Colors.primaryText)
                .contentTransition(.numericText())
                .lineLimit(1)
                .minimumScaleFactor(0.8)

            Text(subtitle)
                .font(.system(size: 10, weight: .medium))
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, 10)
        .padding(.vertical, 10)
        .background(
            RoundedRectangle(cornerRadius: FormaTheme.Radius.inner, style: .continuous)
                .fill(color.opacity(isSelected ? 0.13 : 0.08))
        )
        .overlay(
            RoundedRectangle(cornerRadius: FormaTheme.Radius.inner, style: .continuous)
                .stroke(color.opacity(isSelected ? 0.55 : 0.18), lineWidth: isSelected ? 1.4 : 0.8)
        )
    }
}

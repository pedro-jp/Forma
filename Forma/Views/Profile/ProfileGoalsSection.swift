//
//  ProfileGoalsSection.swift
//  Forma
//
//  Seções modulares de Metas Diárias e Macronutrientes (com barra tricolor ao vivo) na aba Perfil.
//

import SwiftUI

struct ProfileDailyGoalsCard: View {
    @Bindable var profile: Profile
    let onContextSliderEnded: () -> Void

    var body: some View {
        FormaCard(padding: 20) {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Image(systemName: "flame.fill")
                        .foregroundStyle(FormaTheme.Colors.brand)
                    Text("Metas Diárias")
                        .font(.headline)
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                    Spacer()

                    FormaBadge(text: "Ajuste Forma", tint: FormaTheme.Colors.brand)
                }

                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Calorias a Consumir")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text("\(Int(profile.targetCalorieIntake)) kcal")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.brand)
                    }
                    Slider(value: $profile.targetCalorieIntake, in: 1200...4500, step: 50)
                        .tint(FormaTheme.Colors.brand)
                }

                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Gasto Ativo em Treinos")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text("\(Int(profile.targetCalorieBurn)) kcal")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.energyGreen)
                    }
                    Slider(value: $profile.targetCalorieBurn, in: 200...1500, step: 25) { editing in
                        if !editing { onContextSliderEnded() }
                    }
                    .tint(FormaTheme.Colors.energyGreen)
                }

                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Passos Diários")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text("\(Int(profile.targetSteps)) passos")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.brand)
                    }
                    Slider(value: $profile.targetSteps, in: 3000...20000, step: 500) { editing in
                        if !editing { onContextSliderEnded() }
                    }
                    .tint(FormaTheme.Colors.brand)
                }

                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        HStack(spacing: 4) {
                            Image(systemName: "drop.fill")
                                .font(.caption)
                                .foregroundStyle(FormaTheme.Colors.water)
                            Text("Meta de Água")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                        Spacer()
                        Text("\(profile.targetWaterMilliliters) ml")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.water)
                    }
                    Slider(
                        value: Binding(
                            get: { Double(profile.targetWaterMilliliters) },
                            set: { profile.targetWaterMilliliters = Int($0) }
                        ),
                        in: 1000...5000,
                        step: 250
                    )
                    .tint(FormaTheme.Colors.water)
                }
            }
        }
    }
}

struct ProfileMacroGoalsCard: View {
    @Bindable var profile: Profile
    let onSuggestTap: () -> Void

    private var proteinCalories: Double { max(0, profile.targetProtein * 4.0) }
    private var carbsCalories: Double { max(0, profile.targetCarbohydrates * 4.0) }
    private var fatCalories: Double { max(0, profile.targetFat * 9.0) }
    private var totalMacroCalories: Double { max(1.0, proteinCalories + carbsCalories + fatCalories) }

    var body: some View {
        let pPct = Int(((proteinCalories / totalMacroCalories) * 100).rounded())
        let cPct = Int(((carbsCalories / totalMacroCalories) * 100).rounded())
        let fPct = max(0, 100 - pPct - cPct)

        FormaCard(padding: 20) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Image(systemName: "chart.pie.fill")
                        .foregroundStyle(FormaTheme.Colors.brand)
                    Text("Metas de Macronutrientes")
                        .font(.headline)
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                    Spacer()

                    Button("Sugerir", action: onSuggestTap)
                        .font(.caption.weight(.bold))
                        .foregroundStyle(FormaTheme.Colors.brand)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 4)
                        .background(FormaTheme.Colors.brandSoft)
                        .clipShape(Capsule())
                        .buttonStyle(.plain)
                }

                VStack(alignment: .leading, spacing: 6) {
                    HStack {
                        Text("Divisão das Calorias")
                            .font(.caption2.weight(.semibold))
                            .foregroundStyle(.secondary)
                            .lineLimit(1)
                        Spacer()
                        HStack(spacing: 6) {
                            HStack(spacing: 3) {
                                Circle().fill(FormaTheme.Colors.protein).frame(width: 6, height: 6)
                                Text("P \(pPct)%")
                            }
                            HStack(spacing: 3) {
                                Circle().fill(FormaTheme.Colors.carbs).frame(width: 6, height: 6)
                                Text("C \(cPct)%")
                            }
                            HStack(spacing: 3) {
                                Circle().fill(FormaTheme.Colors.fat).frame(width: 6, height: 6)
                                Text("G \(fPct)%")
                            }
                            Text("(\(Int(totalMacroCalories.rounded())) kcal)")
                        }
                        .font(.caption2.weight(.bold))
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    }

                    GeometryReader { geo in
                        HStack(spacing: 2) {
                            Capsule()
                                .fill(FormaTheme.Colors.protein)
                                .frame(width: max(4, geo.size.width * (proteinCalories / totalMacroCalories) - 1))
                            Capsule()
                                .fill(FormaTheme.Colors.carbs)
                                .frame(width: max(4, geo.size.width * (carbsCalories / totalMacroCalories) - 1))
                            Capsule()
                                .fill(FormaTheme.Colors.fat)
                                .frame(width: max(4, geo.size.width * (fatCalories / totalMacroCalories) - 1))
                        }
                    }
                    .frame(height: 6)
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("Proteína")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text("\(Int(profile.targetProtein)) g")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.protein)
                    }
                    Slider(value: $profile.targetProtein, in: 50...350, step: 5)
                        .tint(FormaTheme.Colors.protein)
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("Carboidratos")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text("\(Int(profile.targetCarbohydrates)) g")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.carbs)
                    }
                    Slider(value: $profile.targetCarbohydrates, in: 50...600, step: 5)
                        .tint(FormaTheme.Colors.carbs)
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("Gorduras")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text("\(Int(profile.targetFat)) g")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.fat)
                    }
                    Slider(value: $profile.targetFat, in: 20...200, step: 2)
                        .tint(FormaTheme.Colors.fat)
                }
            }
        }
    }
}

//
//  ProfileBiometricsCard.swift
//  Forma
//
//  Seções modulares de Peso Alvo e Dados Biométricos na aba Perfil, com recálculo contextual imediato.
//

import SwiftUI
import SwiftData

struct ProfileTargetWeightCard: View {
    @Bindable var profile: Profile
    let onAdjustCurrentWeight: (Double, Bool, Bool) -> Void
    let onFinishWeightSlider: () -> Void
    let onFinishTargetSlider: () -> Void
    let onFocusWeightField: () -> Void

    var body: some View {
        FormaCard(padding: 20) {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    Image(systemName: "flag.checkered")
                        .foregroundStyle(FormaTheme.Colors.brand)
                    Text("Meta de Peso Alvo")
                        .font(.headline)
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                    Spacer()

                    let diff = profile.targetWeight - profile.currentWeight
                    let diffText = diff >= 0
                        ? "+\(String(format: "%.1f", diff)) kg"
                        : "\(String(format: "%.1f", diff)) kg"
                    FormaBadge(
                        text: abs(diff) < 0.05 ? "Na meta!" : diffText,
                        tint: abs(diff) < 0.05 ? FormaTheme.Colors.energyGreen : FormaTheme.Colors.brand
                    )
                }

                HStack(spacing: 12) {
                    Button(action: onFocusWeightField) {
                        VStack(alignment: .leading, spacing: 4) {
                            HStack(spacing: 4) {
                                Text("Peso Atual")
                                    .font(.caption2.weight(.medium))
                                    .foregroundStyle(.secondary)
                                Image(systemName: "pencil")
                                    .font(.system(size: 9, weight: .bold))
                                    .foregroundStyle(FormaTheme.Colors.brand)
                            }
                            HStack(alignment: .firstTextBaseline, spacing: 2) {
                                Text(String(format: "%.1f", profile.currentWeight))
                                    .font(.system(size: 24, weight: .bold, design: .rounded))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                                Text("kg")
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(12)
                        .background(Color.primary.opacity(0.035))
                        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .stroke(Color.primary.opacity(0.06), lineWidth: 0.5)
                        )
                    }
                    .buttonStyle(.plain)

                    Image(systemName: "arrow.right")
                        .font(.system(size: 14, weight: .bold))
                        .foregroundStyle(FormaTheme.Colors.brand.opacity(0.7))

                    VStack(alignment: .trailing, spacing: 4) {
                        Text("Peso Alvo")
                            .font(.caption2.weight(.medium))
                            .foregroundStyle(.secondary)
                        HStack(alignment: .firstTextBaseline, spacing: 2) {
                            Text(String(format: "%.1f", profile.targetWeight))
                                .font(.system(size: 24, weight: .bold, design: .rounded))
                                .foregroundStyle(FormaTheme.Colors.brand)
                            Text("kg")
                                .font(.caption)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(12)
                    .background(FormaTheme.Colors.brandSoft)
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(FormaTheme.Colors.brand.opacity(0.2), lineWidth: 0.5)
                    )
                }

                VStack(alignment: .leading, spacing: 6) {
                    HStack(spacing: 8) {
                        Text("Ajustar peso atual")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Button {
                            onAdjustCurrentWeight(profile.currentWeight - 0.5, true, true)
                        } label: {
                            Image(systemName: "minus.circle.fill")
                                .font(.system(size: 18))
                                .foregroundStyle(FormaTheme.Colors.brand)
                        }
                        .buttonStyle(.plain)

                        Text(String(format: "%.1f kg", profile.currentWeight))
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .frame(minWidth: 58, alignment: .center)

                        Button {
                            onAdjustCurrentWeight(profile.currentWeight + 0.5, true, true)
                        } label: {
                            Image(systemName: "plus.circle.fill")
                                .font(.system(size: 18))
                                .foregroundStyle(FormaTheme.Colors.brand)
                        }
                        .buttonStyle(.plain)
                    }

                    Slider(
                        value: Binding(
                            get: { min(160.0, max(35.0, profile.currentWeight)) },
                            set: { onAdjustCurrentWeight($0, true, false) }
                        ),
                        in: 35...160,
                        step: 0.1
                    ) { editing in
                        if !editing { onFinishWeightSlider() }
                    }
                    .tint(FormaTheme.Colors.energyGreen)
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack {
                        Text("Ajustar peso alvo")
                            .font(.caption)
                            .foregroundStyle(.secondary)
                        Spacer()
                        Text(String(format: "%.1f kg", profile.targetWeight))
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.brand)
                    }
                    Slider(value: $profile.targetWeight, in: 40...160, step: 0.5) { editing in
                        if !editing { onFinishTargetSlider() }
                    }
                    .tint(FormaTheme.Colors.brand)
                }
            }
        }
    }
}

struct ProfileBiometricsCard: View {
    @Bindable var profile: Profile
    @Binding var heightInputText: String
    @Binding var weightInputText: String
    var isHeightFocused: FocusState<Bool>.Binding
    var isWeightFocused: FocusState<Bool>.Binding
    let contextualTdee: Double
    let onApplyHeight: (Double, Bool) -> Void
    let onApplyWeight: (Double, Bool, Bool) -> Void
    let onRecalculateTap: () -> Void

    var body: some View {
        FormaCard(padding: 20) {
            VStack(alignment: .leading, spacing: 14) {
                HStack {
                    Image(systemName: "figure.arms.open")
                        .foregroundStyle(FormaTheme.Colors.brand)
                    Text("Medidas Corporais")
                        .font(.headline)
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                    Spacer()

                    Button(action: onRecalculateTap) {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.triangle.2.circlepath")
                                .font(.system(size: 10, weight: .bold))
                            Text("Recalcular")
                                .font(.caption2.weight(.bold))
                        }
                        .foregroundStyle(FormaTheme.Colors.brand)
                        .padding(.horizontal, 9)
                        .padding(.vertical, 4)
                        .background(FormaTheme.Colors.brandSoft)
                        .clipShape(Capsule())
                    }
                    .buttonStyle(.plain)
                }

                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 5) {
                        HStack(spacing: 5) {
                            Image(systemName: "ruler")
                                .font(.system(size: 11))
                                .foregroundStyle(.secondary)
                            Text("Altura")
                                .font(.caption2.weight(.medium))
                                .foregroundStyle(.secondary)
                        }
                        HStack(spacing: 3) {
                            TextField("1.78", text: $heightInputText)
                                .textFieldStyle(.plain)
                                .keyboardType(.decimalPad)
                                .focused(isHeightFocused)
                                .font(.system(size: 20, weight: .bold, design: .rounded))
                                .onChange(of: heightInputText) { _, newText in
                                    if let parsed = ProfileInputValidator.parseFlexibleHeight(newText) {
                                        onApplyHeight(parsed, false)
                                    }
                                }
                                .onChange(of: isHeightFocused.wrappedValue) { _, focused in
                                    if !focused {
                                        if let parsed = ProfileInputValidator.parseFlexibleHeight(heightInputText) {
                                            onApplyHeight(parsed, true)
                                        } else {
                                            heightInputText = String(format: "%.2f", profile.height)
                                        }
                                    }
                                }
                            Text("m")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(Color.primary.opacity(0.035))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(isHeightFocused.wrappedValue ? FormaTheme.Colors.brand : Color.primary.opacity(0.08), lineWidth: isHeightFocused.wrappedValue ? 1.4 : 0.8)
                    )
                    .contentShape(Rectangle())
                    .onTapGesture { isHeightFocused.wrappedValue = true }

                    VStack(alignment: .leading, spacing: 5) {
                        HStack(spacing: 5) {
                            Image(systemName: "scalemass.fill")
                                .font(.system(size: 11))
                                .foregroundStyle(FormaTheme.Colors.brand)
                            Text("Peso Atual")
                                .font(.caption2.weight(.medium))
                                .foregroundStyle(.secondary)
                        }
                        HStack(spacing: 3) {
                            TextField("72.0", text: $weightInputText)
                                .textFieldStyle(.plain)
                                .keyboardType(.decimalPad)
                                .focused(isWeightFocused)
                                .font(.system(size: 20, weight: .bold, design: .rounded))
                                .onChange(of: weightInputText) { _, newText in
                                    if let parsed = ProfileInputValidator.parseFlexibleWeight(newText) {
                                        onApplyWeight(parsed, false, false)
                                    }
                                }
                                .onChange(of: isWeightFocused.wrappedValue) { _, focused in
                                    if !focused {
                                        if let parsed = ProfileInputValidator.parseFlexibleWeight(weightInputText) {
                                            onApplyWeight(parsed, true, true)
                                        } else {
                                            weightInputText = String(format: "%.1f", profile.currentWeight)
                                        }
                                    }
                                }
                            Text("kg")
                                .font(.subheadline)
                                .foregroundStyle(.secondary)
                        }
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .padding(12)
                    .background(FormaTheme.Colors.brandSoft.opacity(0.7))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .overlay(
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .stroke(isWeightFocused.wrappedValue ? FormaTheme.Colors.brand : FormaTheme.Colors.brand.opacity(0.2), lineWidth: isWeightFocused.wrappedValue ? 1.4 : 0.8)
                    )
                    .contentShape(Rectangle())
                    .onTapGesture { isWeightFocused.wrappedValue = true }
                }

                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Image(systemName: "bolt.heart.fill")
                            .font(.system(size: 12))
                            .foregroundStyle(FormaTheme.Colors.brand)
                        Text("Gasto em Repouso: \(Int(profile.tmb)) kcal • Gasto Total: \(Int(contextualTdee)) kcal/dia")
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .lineLimit(1)
                            .minimumScaleFactor(0.82)
                    }
                    Text("Meta Forma: \(Int(profile.targetCalorieIntake)) kcal • P \(Int(profile.targetProtein))g • C \(Int(profile.targetCarbohydrates))g • G \(Int(profile.targetFat))g")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.82)

                    HStack(spacing: 10) {
                        if let mifflinURL = URL(string: "https://pubmed.ncbi.nlm.nih.gov/2305711/") {
                            Link("Fonte TMB: Mifflin-St Jeor", destination: mifflinURL)
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(FormaTheme.Colors.brand)
                        }
                        if let issnURL = URL(string: "https://pubmed.ncbi.nlm.nih.gov/28642676/") {
                            Link("Macros: ISSN (PubMed)", destination: issnURL)
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(FormaTheme.Colors.brand)
                        }
                    }
                    .padding(.top, 2)
                }
                .padding(.vertical, 2)
            }
        }
    }
}

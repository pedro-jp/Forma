//
//  FoodLoggerComponents.swift
//  Forma
//
//  Subcomponentes modulares do registrador multimodal de refeições (Foto, Voz & Texto).
//

import SwiftUI

struct CleanMacroBadge: View {
    let label: String
    let value: String
    let color: Color

    var body: some View {
        VStack(spacing: 3) {
            Text(label)
                .font(.caption2.weight(.medium))
                .foregroundStyle(.secondary)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Text(value)
                .font(.system(size: 14, weight: .bold, design: .rounded))
                .foregroundStyle(color)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(color.opacity(0.10))
        .clipShape(RoundedRectangle(cornerRadius: FormaTheme.Radius.badge, style: .continuous))
    }
}

struct MealTypeSelectMenu: View {
    let selectedMeal: MealType
    let onSelect: (MealType) -> Void

    var body: some View {
        Menu {
            ForEach(MealType.allCases) { meal in
                Button {
                    onSelect(meal)
                } label: {
                    Label(meal.rawValue, systemImage: meal.icon)
                }
            }
        } label: {
            HStack(spacing: 6) {
                Image(systemName: selectedMeal.icon)
                    .font(.system(size: 12))
                Text(selectedMeal.rawValue)
                    .font(.subheadline.weight(.semibold))
                    .lineLimit(1)
                Image(systemName: "chevron.down")
                    .font(.system(size: 10, weight: .bold))
            }
            .foregroundStyle(FormaTheme.Colors.brand)
            .padding(.horizontal, 12)
            .padding(.vertical, 7)
            .background(FormaTheme.Colors.brandSoft)
            .clipShape(Capsule())
            .fixedSize(horizontal: true, vertical: false)
        }
    }
}

struct FoodAnalysisPhaseBadge: View {
    let phase: AnalysisPhase

    private var isTacoPhase: Bool { phase == .tacoDatabase }
    private var isPhotoPhase: Bool { phase == .photoVision }
    private var phaseColor: Color {
        isTacoPhase ? FormaTheme.Colors.tacoCyan : FormaTheme.Colors.aiAccent
    }

    var body: some View {
        HStack(spacing: 6) {
            Image(systemName: isTacoPhase ? "book.closed.fill" : (isPhotoPhase ? "camera.viewfinder" : "sparkles"))
                .font(.system(size: 11, weight: .bold))
                .foregroundStyle(phaseColor)

            Text(isTacoPhase ? "Tabela TACO..." : "IA Google Gemini...")
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .foregroundStyle(phaseColor)
                .lineLimit(1)

            ProgressView()
                .scaleEffect(0.7)
                .tint(phaseColor)
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 4)
        .background(phaseColor.opacity(0.12))
        .clipShape(Capsule())
        .overlay(Capsule().stroke(phaseColor.opacity(0.28), lineWidth: 0.8))
        .fixedSize(horizontal: true, vertical: false)
    }
}

struct FoodPlateResultCard: View {
    let intent: VoiceFoodIntent
    let isAnalyzing: Bool
    let onUpdateMealType: (MealType) -> Void
    let onRecalculateAI: () -> Void
    let onAdjustPortion: (UUID, Double) -> Void
    let onEditExactPortion: (VoicePlateItem) -> Void
    let onRemoveItem: (UUID) -> Void
    let onConfirm: () -> Void

    var body: some View {
        FormaCard(padding: 20) {
            VStack(alignment: .leading, spacing: 16) {
                headerRow
                macroRow

                if !intent.aiSummary.isEmpty {
                    HStack(alignment: .top, spacing: 8) {
                        Image(systemName: "sparkles")
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(FormaTheme.Colors.aiAccent)
                        Text(intent.aiSummary)
                            .font(.caption)
                            .foregroundStyle(.secondary)
                    }
                    .padding(10)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(FormaTheme.Colors.aiAccent.opacity(0.08))
                    .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
                }

                Divider().background(Color.primary.opacity(0.06))

                VStack(alignment: .leading, spacing: 10) {
                    ForEach(intent.items) { item in
                        PlateRecognizedItemRow(
                            item: item,
                            canRemove: intent.items.count > 1,
                            onAdjustPortion: { delta in onAdjustPortion(item.id, delta) },
                            onEditExactPortion: { onEditExactPortion(item) },
                            onRemove: { onRemoveItem(item.id) }
                        )
                    }
                }

                Button(action: onConfirm) {
                    HStack(spacing: 8) {
                        Image(systemName: "checkmark")
                            .font(.system(size: 14, weight: .bold))
                        Text(intent.items.count > 1 ? "Registrar Refeição (\(intent.items.count) itens)" : "Registrar Refeição")
                            .font(.headline)
                            .lineLimit(1)
                    }
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(FormaTheme.Colors.brand)
                    .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                }
                .buttonStyle(.plain)
                .padding(.top, 4)
            }
        }
    }

    private var headerRow: some View {
        HStack(alignment: .top, spacing: 8) {
            VStack(alignment: .leading, spacing: 2) {
                Text(intent.items.count > 1 ? "Prato Consolidado (\(intent.items.count) itens)" : "Alimento Identificado")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                HStack(alignment: .firstTextBaseline, spacing: 4) {
                    Text("\(Int(intent.totalCalories.rounded()))")
                        .font(.system(size: 32, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                        .contentTransition(.numericText())
                    Text("kcal")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(.secondary)
                }
                .lineLimit(1)
            }

            Spacer(minLength: 8)

            VStack(alignment: .trailing, spacing: 8) {
                MealTypeSelectMenu(selectedMeal: intent.mealType, onSelect: onUpdateMealType)

                Button(action: onRecalculateAI) {
                    HStack(spacing: 4) {
                        Image(systemName: "arrow.triangle.2.circlepath")
                            .font(.system(size: 10, weight: .bold))
                        Text("Recalcular")
                            .font(.system(size: 11, weight: .bold, design: .rounded))
                            .lineLimit(1)
                    }
                    .foregroundStyle(FormaTheme.Colors.aiAccent)
                    .padding(.horizontal, 10)
                    .padding(.vertical, 5)
                    .background(FormaTheme.Colors.aiAccent.opacity(0.12))
                    .clipShape(Capsule())
                    .fixedSize(horizontal: true, vertical: false)
                }
                .buttonStyle(.plain)
                .disabled(isAnalyzing)
            }
        }
    }

    private var macroRow: some View {
        HStack(spacing: 8) {
            CleanMacroBadge(label: "Proteína", value: "\(Int(intent.totalProtein.rounded()))g", color: FormaTheme.Colors.protein)
            CleanMacroBadge(label: "Carboidratos", value: "\(Int(intent.totalCarbs.rounded()))g", color: FormaTheme.Colors.carbs)
            CleanMacroBadge(label: "Gordura", value: "\(Int(intent.totalFat.rounded()))g", color: FormaTheme.Colors.fat)
        }
    }
}

struct PlateRecognizedItemRow: View {
    let item: VoicePlateItem
    let canRemove: Bool
    let onAdjustPortion: (Double) -> Void
    let onEditExactPortion: () -> Void
    let onRemove: () -> Void

    private var isLiquidUnit: Bool {
        item.isLiquid || item.unit == "ml"
    }

    private var stepValue: Double {
        isLiquidUnit ? 50 : 25
    }

    private var unitSuffix: String {
        isLiquidUnit ? "ml" : "g"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(alignment: .center, spacing: 8) {
                VStack(alignment: .leading, spacing: 3) {
                    HStack(spacing: 6) {
                        Text(item.name)
                            .font(.system(size: 15, weight: .semibold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .lineLimit(2)

                        let lowerSource = item.source.lowercased()
                        let isPhotoSource = lowerSource.contains("foto")
                        let isHistorySource = lowerSource.contains("hist")
                        let isFormaSource = isPhotoSource || lowerSource.contains("ia") || lowerSource.contains("gemini") || lowerSource.contains("forma")
                        let badgeColor = isFormaSource ? FormaTheme.Colors.aiAccent : (isHistorySource ? FormaTheme.Colors.brand : FormaTheme.Colors.tacoCyan)
                        let badgeTitle = isPhotoSource ? "FOTO" : (isHistorySource ? "HÁBITO" : "FORMA")
                        let badgeIcon = isPhotoSource ? "camera.fill" : (isFormaSource ? "sparkles" : (isHistorySource ? "clock.arrow.circlepath" : "book.closed.fill"))

                        HStack(spacing: 3) {
                            Image(systemName: badgeIcon)
                                .font(.system(size: 8, weight: .bold))
                            Text(badgeTitle)
                                .font(.system(size: 9, weight: .heavy, design: .rounded))
                                .lineLimit(1)
                        }
                        .foregroundStyle(badgeColor)
                        .padding(.horizontal, 6)
                        .padding(.vertical, 2)
                        .background(badgeColor.opacity(0.12))
                        .clipShape(Capsule())
                        .fixedSize(horizontal: true, vertical: false)
                    }

                    Text("\(Int(item.calories.rounded())) kcal • \(Int(item.protein.rounded()))g P • \(Int(item.carbs.rounded()))g C • \(Int(item.fat.rounded()))g G")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                        .minimumScaleFactor(0.85)
                }

                Spacer(minLength: 4)

                if canRemove {
                    Button(action: onRemove) {
                        Image(systemName: "trash")
                            .font(.system(size: 13))
                            .foregroundStyle(FormaTheme.Colors.danger.opacity(0.75))
                            .padding(6)
                    }
                    .buttonStyle(.plain)
                }
            }

            HStack(spacing: 10) {
                Text("Quantidade")
                    .font(.caption)
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                Spacer()

                Button {
                    onAdjustPortion(-stepValue)
                } label: {
                    Text("-\(Int(stepValue)) \(unitSuffix)")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color.primary.opacity(0.06))
                        .clipShape(Capsule())
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .buttonStyle(.plain)

                Button(action: onEditExactPortion) {
                    Text("\(Int(item.portionValue.rounded())) \(unitSuffix)")
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.brand)
                        .frame(minWidth: 62)
                        .padding(.vertical, 4)
                        .padding(.horizontal, 6)
                        .background(FormaTheme.Colors.brandSoft.opacity(0.75))
                        .clipShape(RoundedRectangle(cornerRadius: 8, style: .continuous))
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .buttonStyle(.plain)

                Button {
                    onAdjustPortion(stepValue)
                } label: {
                    Text("+\(Int(stepValue)) \(unitSuffix)")
                        .font(.caption.weight(.bold))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(Color.primary.opacity(0.06))
                        .clipShape(Capsule())
                        .lineLimit(1)
                        .fixedSize(horizontal: true, vertical: false)
                }
                .buttonStyle(.plain)
            }
        }
        .padding(14)
        .background(Color(.systemBackground).opacity(0.75))
        .clipShape(RoundedRectangle(cornerRadius: FormaTheme.Radius.inner, style: .continuous))
    }
}

struct FoodQuickSuggestionsSection: View {
    let mealType: MealType
    let suggestions: [SmartFoodSuggestion]
    let currentPlateNames: Set<String>
    let onSelectSuggestion: (SmartFoodSuggestion) -> Void

    private var hasHistoryItems: Bool {
        suggestions.contains(where: \.isFromHistory)
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack {
                Text(hasHistoryItems ? "Frequentes em \(mealType.rawValue)" : "Sugestões para \(mealType.rawValue)")
                    .font(.caption.weight(.semibold))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)

                Spacer()

                if hasHistoryItems {
                    Text("Toque para adicionar")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                        .lineLimit(1)
                }
            }

            VStack(spacing: 8) {
                ForEach(suggestions) { item in
                    let isAdded = currentPlateNames.contains(item.name.lowercased())
                    Button {
                        onSelectSuggestion(item)
                    } label: {
                        HStack(spacing: 10) {
                            Image(systemName: isAdded ? "checkmark.circle.fill" : "plus.circle.fill")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(isAdded ? FormaTheme.Colors.energyGreen : FormaTheme.Colors.brand)

                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.name)
                                    .font(.system(size: 14, weight: .semibold))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                                    .lineLimit(1)

                                Text("\(Int(item.portionValue.rounded()))\(item.unit) • \(Int(item.calories.rounded())) kcal • P \(Int(item.protein.rounded()))g")
                                    .font(.caption2)
                                    .foregroundStyle(.secondary)
                                    .lineLimit(1)
                            }

                            Spacer(minLength: 6)

                            Text(item.reasonBadge)
                                .font(.system(size: 10, weight: .bold, design: .rounded))
                                .foregroundStyle(item.isFromHistory ? FormaTheme.Colors.brand : .secondary)
                                .padding(.horizontal, 8)
                                .padding(.vertical, 4)
                                .background(item.isFromHistory ? FormaTheme.Colors.brandSoft : Color.primary.opacity(0.06))
                                .clipShape(Capsule())
                                .lineLimit(1)
                                .fixedSize(horizontal: true, vertical: false)
                        }
                        .padding(.horizontal, 14)
                        .padding(.vertical, 10)
                        .background(FormaTheme.Colors.cardSurface)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .stroke(isAdded ? FormaTheme.Colors.energyGreen.opacity(0.35) : Color.primary.opacity(0.06), lineWidth: 0.7)
                        )
                    }
                    .buttonStyle(.plain)
                }
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.top, 4)
    }
}

struct FoodSavedConfirmationView: View {
    let intent: VoiceFoodIntent?
    let onDone: () -> Void

    var body: some View {
        VStack(spacing: 20) {
            Spacer()

            ZStack {
                Circle()
                    .fill(FormaTheme.Colors.energyGreen.opacity(0.15))
                    .frame(width: 90, height: 90)

                Image(systemName: "checkmark")
                    .font(.system(size: 40, weight: .bold))
                    .foregroundStyle(FormaTheme.Colors.energyGreen)
            }

            VStack(spacing: 6) {
                Text("Refeição Registrada")
                    .font(.title2.weight(.bold))
                    .foregroundStyle(FormaTheme.Colors.primaryText)

                if let intent, !intent.items.isEmpty {
                    Text("\(intent.items.count) \(intent.items.count == 1 ? "item adicionado" : "itens somados") ao \(intent.mealType.rawValue)")
                        .font(.subheadline)
                        .foregroundStyle(.secondary)

                    Text("+\(Int(intent.totalCalories.rounded())) kcal")
                        .font(.system(size: 20, weight: .bold, design: .rounded))
                        .foregroundStyle(FormaTheme.Colors.brand)
                        .padding(.top, 4)
                }
            }

            Spacer()

            Button("Concluir", action: onDone)
                .font(.headline)
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(FormaTheme.Colors.brand)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
        }
    }
}

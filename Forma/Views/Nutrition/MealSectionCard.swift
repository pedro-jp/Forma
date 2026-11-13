//
//  MealSectionCard.swift
//  Forma
//
//  Card expansível de refeição diária (Café da Manhã, Almoço, Jantar, Lanches) na aba Alimentação.
//

import SwiftUI
#if canImport(UIKit)
import UIKit
#endif

struct FoodDragPreviewCard: View {
    let title: String
    let subtitle: String
    let icon: String

    var body: some View {
        HStack(spacing: 10) {
            ZStack {
                RoundedRectangle(cornerRadius: 9, style: .continuous)
                    .fill(FormaTheme.Colors.brandSoft)
                    .frame(width: 30, height: 30)
                Image(systemName: icon)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(FormaTheme.Colors.brand)
            }

            VStack(alignment: .leading, spacing: 2) {
                Text(title)
                    .font(.system(size: 14, weight: .bold))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .lineLimit(1)
                Text(subtitle)
                    .font(.caption2.weight(.medium))
                    .foregroundStyle(.secondary)
                    .lineLimit(1)
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 14, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .stroke(FormaTheme.Colors.brand.opacity(0.35), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.12), radius: 8, x: 0, y: 4)
    }
}

struct MealQuickDropBar: View {
    let onDropPayloads: ([String], MealType) -> Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack(spacing: 5) {
                Image(systemName: "hand.draw.fill")
                    .font(.system(size: 10, weight: .semibold))
                Text("Arraste um item abaixo para mudar de refeição")
                    .font(.caption2.weight(.medium))
            }
            .foregroundStyle(.secondary)

            HStack(spacing: 6) {
                ForEach(MealType.allCases) { meal in
                    MealQuickDropPill(mealType: meal) { payloads in
                        onDropPayloads(payloads, meal)
                    }
                }
            }
        }
    }
}

private struct MealQuickDropPill: View {
    let mealType: MealType
    let onDropPayloads: ([String]) -> Bool

    @State private var isDropTargeted: Bool = false

    var body: some View {
        HStack(spacing: 4) {
            Image(systemName: mealType.icon)
                .font(.system(size: 10, weight: .semibold))
            Text(mealType.shortTitle)
                .font(.system(size: 11, weight: .bold, design: .rounded))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .foregroundStyle(isDropTargeted ? .white : FormaTheme.Colors.brand)
        .frame(maxWidth: .infinity)
        .padding(.vertical, 7)
        .padding(.horizontal, 6)
        .background(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .fill(isDropTargeted ? FormaTheme.Colors.brand : FormaTheme.Colors.brandSoft.opacity(0.75))
        )
        .overlay(
            RoundedRectangle(cornerRadius: 10, style: .continuous)
                .stroke(
                    FormaTheme.Colors.brand.opacity(isDropTargeted ? 0.9 : 0.25),
                    style: StrokeStyle(lineWidth: isDropTargeted ? 1.5 : 1, dash: isDropTargeted ? [] : [4, 3])
                )
        )
        .scaleEffect(isDropTargeted ? 1.05 : 1.0)
        .animation(.spring(response: 0.22, dampingFraction: 0.78), value: isDropTargeted)
        .dropDestination(for: String.self) { items, _ in
            let accepted = onDropPayloads(items)
            #if canImport(UIKit)
            if accepted {
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            }
            #endif
            return accepted
        } isTargeted: { targeted in
            if targeted != isDropTargeted {
                isDropTargeted = targeted
                #if canImport(UIKit)
                if targeted {
                    UISelectionFeedbackGenerator().selectionChanged()
                }
                #endif
            }
        }
    }
}

struct MealSectionCard: View {
    let mealType: MealType
    let entries: [FoodEntry]
    let onAddTap: () -> Void
    var onCameraTap: (() -> Void)? = nil
    var onDeleteEntry: ((FoodEntry) -> Void)? = nil
    var onEditEntry: ((FoodEntry) -> Void)? = nil
    var onDropPayloads: (([String]) -> Bool)? = nil

    @State private var isDropTargeted: Bool = false

    private var mealCalories: Double {
        entries.reduce(0.0) { $0 + $1.calories }
    }

    var body: some View {
        FormaCard(padding: 16, cornerRadius: 18) {
            VStack(spacing: 12) {
                HStack(spacing: 8) {
                    mealHeaderDragArea

                    if isDropTargeted {
                        HStack(spacing: 4) {
                            Image(systemName: "arrow.down.circle.fill")
                                .font(.system(size: 11, weight: .bold))
                            Text("Soltar aqui")
                                .font(.caption2.weight(.bold))
                        }
                        .foregroundStyle(.white)
                        .padding(.horizontal, 8)
                        .padding(.vertical, 4)
                        .background(FormaTheme.Colors.brand)
                        .clipShape(Capsule())
                        .transition(.scale.combined(with: .opacity))
                    }

                    if let onCameraTap {
                        Button(action: onCameraTap) {
                            Image(systemName: "camera.fill")
                                .font(.system(size: 12, weight: .semibold))
                                .foregroundStyle(FormaTheme.Colors.brand)
                                .frame(width: 28, height: 28)
                                .background(FormaTheme.Colors.brandSoft)
                                .clipShape(Circle())
                        }
                        .buttonStyle(.plain)
                    }

                    Button(action: onAddTap) {
                        Image(systemName: "plus.circle.fill")
                            .font(.system(size: 23))
                            .foregroundStyle(FormaTheme.Colors.brand)
                    }
                    .buttonStyle(.plain)
                }

                if entries.isEmpty {
                    HStack {
                        Text(isDropTargeted ? "Solte aqui para mover para \(mealType.rawValue)." : "Nenhum item registrado ainda.")
                            .font(.caption)
                            .foregroundStyle(isDropTargeted ? FormaTheme.Colors.brand : .secondary)
                        Spacer()
                        Button("Registrar", action: onAddTap)
                            .font(.caption.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.brand)
                    }
                    .padding(.vertical, 2)
                } else {
                    VStack(spacing: 8) {
                        ForEach(entries) { entry in
                            HStack(spacing: 8) {
                                HStack(spacing: 8) {
                                    Image(systemName: "line.3.horizontal")
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundStyle(.secondary.opacity(0.45))

                                    VStack(alignment: .leading, spacing: 2) {
                                        Text(entry.name)
                                            .font(.system(size: 15, weight: .medium))
                                            .foregroundStyle(FormaTheme.Colors.primaryText)
                                            .lineLimit(2)

                                        let unitLabel = entry.brand.lowercased().contains("ml") ? "ml" : "g"
                                        Text("\(Int(entry.portionGrams))\(unitLabel) • P \(Int(entry.protein))g  C \(Int(entry.carbohydrates))g  G \(Int(entry.fat))g")
                                            .font(.caption)
                                            .foregroundStyle(.secondary)
                                            .lineLimit(1)
                                            .minimumScaleFactor(0.85)
                                    }

                                    Spacer(minLength: 6)

                                    Text("\(Int(entry.calories.rounded())) kcal")
                                        .font(.system(size: 14, weight: .semibold, design: .rounded))
                                        .foregroundStyle(FormaTheme.Colors.primaryText)
                                        .lineLimit(1)
                                        .fixedSize(horizontal: true, vertical: false)

                                    Image(systemName: "chevron.right")
                                        .font(.system(size: 11, weight: .semibold))
                                        .foregroundStyle(.tertiary)
                                }
                                .contentShape(Rectangle())
                                .onTapGesture {
                                    onEditEntry?(entry)
                                }
                                .draggable(FoodDragTransferPayload.encodeFood(entry.id)) {
                                    FoodDragPreviewCard(
                                        title: entry.name,
                                        subtitle: "\(Int(entry.calories.rounded())) kcal • \(mealType.shortTitle)",
                                        icon: mealType.icon
                                    )
                                }
                            }
                            .padding(.vertical, 3)

                            if entry.id != entries.last?.id {
                                Divider()
                                    .background(Color.primary.opacity(0.05))
                            }
                        }
                    }
                }
            }
        }
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(FormaTheme.Colors.brandSoft.opacity(isDropTargeted ? 0.22 : 0))
                .allowsHitTesting(false)
        )
        .overlay(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .stroke(FormaTheme.Colors.brand.opacity(isDropTargeted ? 0.85 : 0), lineWidth: 2)
                .allowsHitTesting(false)
        )
        .scaleEffect(isDropTargeted ? 1.015 : 1.0)
        .animation(.spring(response: 0.25, dampingFraction: 0.8), value: isDropTargeted)
        .dropDestination(for: String.self) { items, _ in
            guard let onDropPayloads else { return false }
            let accepted = onDropPayloads(items)
            #if canImport(UIKit)
            if accepted {
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            }
            #endif
            return accepted
        } isTargeted: { targeted in
            if targeted != isDropTargeted {
                isDropTargeted = targeted
                #if canImport(UIKit)
                if targeted {
                    UISelectionFeedbackGenerator().selectionChanged()
                }
                #endif
            }
        }
    }

    @ViewBuilder
    private var mealHeaderDragArea: some View {
        let content = HStack(spacing: 8) {
            ZStack {
                RoundedRectangle(cornerRadius: 10, style: .continuous)
                    .fill(FormaTheme.Colors.brandSoft)
                    .frame(width: 32, height: 32)
                Image(systemName: mealType.icon)
                    .foregroundStyle(FormaTheme.Colors.brand)
                    .font(.system(size: 14, weight: .semibold))
            }

            VStack(alignment: .leading, spacing: 1) {
                Text(mealType.rawValue)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .lineLimit(1)
                if !entries.isEmpty {
                    Text("\(entries.count) \(entries.count == 1 ? "item" : "itens")")
                        .font(.caption2)
                        .foregroundStyle(.secondary)
                }
            }

            Spacer(minLength: 6)

            if mealCalories > 0 {
                Text("\(Int(mealCalories.rounded())) kcal")
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }
        }
        .contentShape(Rectangle())

        if entries.isEmpty {
            content
        } else {
            content.draggable(FoodDragTransferPayload.encodeMealGroup(mealType)) {
                FoodDragPreviewCard(
                    title: mealType.rawValue,
                    subtitle: "\(entries.count) \(entries.count == 1 ? "item" : "itens") • \(Int(mealCalories.rounded())) kcal",
                    icon: mealType.icon
                )
            }
        }
    }
}

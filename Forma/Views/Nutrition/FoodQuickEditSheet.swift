//
//  FoodQuickEditSheet.swift
//  Forma
//
//  Mini-sheet para ajuste rápido de porção (g/ml), horário da refeição ou remoção de alimento.
//

import SwiftUI

struct FoodQuickEditSheet: View {
    @Environment(\.dismiss) private var dismiss

    let entry: FoodEntry
    let onSave: (Double, MealType) -> Void
    let onDelete: () -> Void

    @State private var portionValue: Double
    @State private var portionText: String
    @State private var selectedMeal: MealType
    @FocusState private var isPortionFocused: Bool

    private let presets: [Int] = [50, 100, 150, 200, 300]

    init(
        entry: FoodEntry,
        onSave: @escaping (Double, MealType) -> Void,
        onDelete: @escaping () -> Void
    ) {
        self.entry = entry
        self.onSave = onSave
        self.onDelete = onDelete
        let initialPortion = max(1.0, entry.portionGrams)
        _portionValue = State(initialValue: initialPortion)
        _portionText = State(initialValue: "\(Int(initialPortion.rounded()))")
        _selectedMeal = State(initialValue: entry.mealType)
    }

    private var unitLabel: String {
        entry.brand.lowercased().contains("ml") ? "ml" : "g"
    }

    private var scaleFactor: Double {
        guard entry.portionGrams > 0 else { return 1.0 }
        return max(0.01, portionValue / entry.portionGrams)
    }

    private var previewCalories: Double { entry.calories * scaleFactor }
    private var previewProtein: Double { entry.protein * scaleFactor }
    private var previewCarbs: Double { entry.carbohydrates * scaleFactor }
    private var previewFat: Double { entry.fat * scaleFactor }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    summaryCard

                    VStack(alignment: .leading, spacing: 10) {
                        Text("Porção (\(unitLabel))")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)

                        HStack(spacing: 10) {
                            stepButton(delta: -25, label: "-25")

                            HStack(spacing: 4) {
                                TextField("100", text: $portionText)
                                    .keyboardType(.decimalPad)
                                    .multilineTextAlignment(.center)
                                    .font(.system(size: 20, weight: .bold, design: .rounded))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                                    .focused($isPortionFocused)
                                    .onChange(of: portionText) { _, newValue in
                                        let normalized = newValue.replacingOccurrences(of: ",", with: ".")
                                        if let parsed = Double(normalized), parsed > 0 {
                                            portionValue = min(parsed, 5000)
                                        }
                                    }
                                Text(unitLabel)
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(.secondary)
                            }
                            .padding(.vertical, 10)
                            .padding(.horizontal, 14)
                            .frame(maxWidth: .infinity)
                            .background(Color.primary.opacity(0.05))
                            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

                            stepButton(delta: 25, label: "+25")
                        }

                        HStack(spacing: 8) {
                            ForEach(presets, id: \.self) { preset in
                                let isSelected = Int(portionValue.rounded()) == preset
                                Button {
                                    applyPortion(Double(preset))
                                } label: {
                                    Text("\(preset)\(unitLabel)")
                                        .font(.caption.weight(.bold))
                                        .foregroundStyle(isSelected ? .white : FormaTheme.Colors.brand)
                                        .frame(maxWidth: .infinity)
                                        .padding(.vertical, 7)
                                        .background(isSelected ? FormaTheme.Colors.brand : FormaTheme.Colors.brandSoft)
                                        .clipShape(Capsule())
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        Text("Horário da Refeição")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)

                        HStack(spacing: 6) {
                            ForEach(MealType.allCases) { meal in
                                let isSelected = selectedMeal == meal
                                Button {
                                    selectedMeal = meal
                                } label: {
                                    VStack(spacing: 4) {
                                        Image(systemName: meal.icon)
                                            .font(.system(size: 13, weight: .semibold))
                                        Text(meal.shortTitle)
                                            .font(.system(size: 11, weight: .bold, design: .rounded))
                                            .lineLimit(1)
                                            .minimumScaleFactor(0.8)
                                    }
                                    .foregroundStyle(isSelected ? .white : FormaTheme.Colors.brand)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 9)
                                    .background(isSelected ? FormaTheme.Colors.brand : FormaTheme.Colors.brandSoft.opacity(0.85))
                                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                                }
                                .buttonStyle(.plain)
                            }
                        }
                    }

                    VStack(spacing: 10) {
                        Button {
                            onSave(portionValue, selectedMeal)
                            dismiss()
                        } label: {
                            Text("Salvar Alterações")
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 13)
                                .background(FormaTheme.Colors.brand)
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)

                        Button(role: .destructive) {
                            onDelete()
                            dismiss()
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "trash")
                                    .font(.system(size: 12, weight: .semibold))
                                Text("Remover Alimento")
                                    .font(.subheadline.weight(.semibold))
                            }
                            .foregroundStyle(FormaTheme.Colors.danger)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                            .background(FormaTheme.Colors.danger.opacity(0.10))
                            .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                    }
                    .padding(.top, 2)
                }
                .padding(.horizontal, 20)
                .padding(.bottom, 20)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .navigationTitle("Editar Alimento")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button {
                        dismiss()
                    } label: {
                        Image(systemName: "xmark.circle.fill")
                            .font(.system(size: 20))
                            .foregroundStyle(.secondary)
                    }
                    .buttonStyle(.plain)
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("Concluir") {
                        isPortionFocused = false
                    }
                    .fontWeight(.semibold)
                }
            }
        }
        .presentationDetents([.height(470), .medium])
        .presentationDragIndicator(.visible)
        .presentationCornerRadius(26)
    }

    private var summaryCard: some View {
        VStack(alignment: .leading, spacing: 10) {
            HStack(alignment: .firstTextBaseline, spacing: 8) {
                Text(entry.name)
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .lineLimit(2)

                Spacer(minLength: 6)

                Text("\(Int(previewCalories.rounded())) kcal")
                    .font(.system(size: 17, weight: .bold, design: .rounded))
                    .foregroundStyle(FormaTheme.Colors.brand)
                    .contentTransition(.numericText())
                    .lineLimit(1)
                    .fixedSize(horizontal: true, vertical: false)
            }

            HStack(spacing: 8) {
                macroPill(label: "P", value: previewProtein, color: FormaTheme.Colors.protein)
                macroPill(label: "C", value: previewCarbs, color: FormaTheme.Colors.carbs)
                macroPill(label: "G", value: previewFat, color: FormaTheme.Colors.fat)
            }
        }
        .padding(14)
        .background(Color.primary.opacity(0.04))
        .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }

    private func macroPill(label: String, value: Double, color: Color) -> some View {
        HStack(spacing: 5) {
            Circle()
                .fill(color)
                .frame(width: 7, height: 7)
            Text("\(label) \(Int(value.rounded()))g")
                .font(.caption.weight(.semibold))
                .foregroundStyle(FormaTheme.Colors.primaryText)
                .contentTransition(.numericText())
        }
        .padding(.horizontal, 10)
        .padding(.vertical, 5)
        .background(color.opacity(0.11))
        .clipShape(Capsule())
    }

    private func stepButton(delta: Double, label: String) -> some View {
        Button {
            isPortionFocused = false
            applyPortion(max(5.0, portionValue + delta))
        } label: {
            Text(label)
                .font(.system(size: 14, weight: .bold, design: .rounded))
                .foregroundStyle(FormaTheme.Colors.brand)
                .frame(width: 54, height: 42)
                .background(FormaTheme.Colors.brandSoft)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func applyPortion(_ value: Double) {
        let clamped = max(1.0, min(5000.0, value))
        withAnimation(.spring(duration: 0.2)) {
            portionValue = clamped
        }
        portionText = "\(Int(clamped.rounded()))"
    }
}

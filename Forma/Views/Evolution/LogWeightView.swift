//
//  LogWeightView.swift
//  Forma
//
//  Registro de nova pesagem corporal com recálculo contextual instantâneo de TMB, TDEE e metas.
//

import SwiftUI
import SwiftData

struct LogWeightView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @Query private var profiles: [Profile]

    private enum WeightField {
        case weight
        case notes
    }

    @State private var weight: Double = 72.0
    @State private var weightText: String = "72.0"
    @FocusState private var focusedField: WeightField?
    @State private var date: Date = Date()
    @State private var notes: String = ""
    @State private var isSaving: Bool = false
    @State private var saveErrorMessage: String? = nil

    private var previewMetrics: ContextualGoalMetrics {
        let ctx = FormaDataStore.shared.buildRecalculationContext(in: modelContext)
        if let p = profiles.first {
            return p.contextualMetrics(for: weight, context: ctx)
        }
        return Profile.computeContextualTargets(
            weight: weight,
            height: 1.78,
            targetWeight: 80.0,
            goal: .gain,
            targetCalorieBurn: 600.0,
            targetSteps: 8000.0,
            context: ctx
        )
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(spacing: FormaTheme.Spacing.sectionGap) {
                    weightInputCard
                    detailsCard
                    primarySaveButton
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
                .padding(.vertical, 16)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .dismissKeyboardOnTap()
            .withDoneKeyboardToolbar()
            .background(FormaTheme.Colors.pageBackground.ignoresSafeArea())
            .navigationTitle("Nova Pesagem")
            .navigationBarTitleDisplayMode(.inline)
            .onAppear {
                if let p = profiles.first, p.currentWeight > 0 {
                    weight = p.currentWeight
                    weightText = String(format: "%.1f", p.currentWeight)
                }
            }
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isSaving ? "Salvando..." : "Salvar") {
                        saveWeightEntry()
                    }
                    .fontWeight(.bold)
                    .disabled(isSaving)
                }
            }
            .alert(
                "Erro ao Sincronizar Pesagem",
                isPresented: Binding(
                    get: { saveErrorMessage != nil },
                    set: { if !$0 { saveErrorMessage = nil } }
                )
            ) {
                Button("OK", role: .cancel) { saveErrorMessage = nil }
            } message: {
                Text(saveErrorMessage ?? "")
            }
        }
    }

    private var weightInputCard: some View {
        FormaCard(padding: 20) {
            VStack(alignment: .leading, spacing: 16) {
                HStack {
                    HStack(spacing: 6) {
                        Image(systemName: "scalemass.fill")
                            .foregroundStyle(FormaTheme.Colors.bodyWeight)
                        Text("Peso Corporal")
                            .font(.headline)
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                    }
                    Spacer()
                    FormaBadge(text: "kg", tint: FormaTheme.Colors.bodyWeight)
                }

                HStack(spacing: 10) {
                    quickWeightStepPill(delta: -0.5, label: "-0.5")

                    Button {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        updateWeight(weight - 0.1, syncText: true)
                    } label: {
                        Image(systemName: "minus.circle.fill")
                            .font(.title2)
                            .foregroundStyle(FormaTheme.Colors.bodyWeight)
                    }
                    .buttonStyle(.plain)

                    Spacer(minLength: 4)

                    HStack(alignment: .firstTextBaseline, spacing: 3) {
                        TextField("72.0", text: $weightText)
                            .keyboardType(.decimalPad)
                            .multilineTextAlignment(.center)
                            .focused($focusedField, equals: .weight)
                            .font(.system(size: 36, weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.bodyWeight)
                            .frame(width: 110)
                            .onChange(of: weightText) { _, newValue in
                                if let parsed = ProfileInputValidator.parseFlexibleWeight(newValue) {
                                    weight = parsed
                                }
                            }
                        Text("kg")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(.secondary)
                    }

                    Spacer(minLength: 4)

                    Button {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        updateWeight(weight + 0.1, syncText: true)
                    } label: {
                        Image(systemName: "plus.circle.fill")
                            .font(.title2)
                            .foregroundStyle(FormaTheme.Colors.bodyWeight)
                    }
                    .buttonStyle(.plain)

                    quickWeightStepPill(delta: 0.5, label: "+0.5")
                }

                Slider(
                    value: Binding(
                        get: { min(160.0, max(35.0, weight)) },
                        set: { updateWeight($0, syncText: true) }
                    ),
                    in: 35...160,
                    step: 0.1
                )
                .tint(FormaTheme.Colors.bodyWeight)

                FormaTintedCard(tint: FormaTheme.Colors.brand, padding: 12) {
                    HStack(spacing: 8) {
                        Image(systemName: "bolt.heart.fill")
                            .font(.caption)
                            .foregroundStyle(FormaTheme.Colors.brand)
                        Text("Gasto em repouso: \(Int(previewMetrics.tmb)) kcal • Nova Meta Forma: \(Int(previewMetrics.suggestedCalories)) kcal/dia")
                            .font(.caption)
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }
        }
    }

    private func quickWeightStepPill(delta: Double, label: String) -> some View {
        Button {
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            updateWeight(weight + delta, syncText: true)
        } label: {
            Text(label)
                .font(.caption.weight(.bold))
                .foregroundStyle(FormaTheme.Colors.bodyWeight)
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(FormaTheme.Colors.bodyWeight.opacity(0.12))
                .clipShape(Capsule())
        }
        .buttonStyle(.plain)
    }

    private var detailsCard: some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 14) {
                DatePicker("Data da pesagem", selection: $date, displayedComponents: [.date])
                    .environment(\.locale, Locale(identifier: "pt_BR"))
                    .tint(FormaTheme.Colors.brand)
                    .font(.subheadline.weight(.medium))

                Divider().background(Color.primary.opacity(0.06))

                VStack(alignment: .leading, spacing: 8) {
                    Text("Observações")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(FormaTheme.Colors.primaryText)

                    TextField("Ex: Em jejum pela manhã, pós-treino...", text: $notes)
                        .focused($focusedField, equals: .notes)
                        .font(.subheadline)
                        .padding(13)
                        .background(Color.primary.opacity(0.04))
                        .clipShape(RoundedRectangle(cornerRadius: FormaTheme.Radius.inner, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: FormaTheme.Radius.inner, style: .continuous)
                                .stroke(
                                    focusedField == .notes ? FormaTheme.Colors.brand : Color.primary.opacity(0.08),
                                    lineWidth: focusedField == .notes ? 1.4 : 0.8
                                )
                        )
                }
            }
        }
    }

    private var primarySaveButton: some View {
        Button {
            saveWeightEntry()
        } label: {
            HStack(spacing: 8) {
                if isSaving {
                    ProgressView().tint(.white)
                } else {
                    Image(systemName: "checkmark.circle.fill")
                        .font(.system(size: 16, weight: .bold))
                }
                Text(isSaving ? "Salvando Pesagem..." : "Salvar Pesagem")
                    .font(.headline)
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .frame(height: 50)
            .background(FormaTheme.Colors.brandGradient)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .shadow(color: FormaTheme.Colors.brand.opacity(0.25), radius: 8, x: 0, y: 4)
        }
        .buttonStyle(.plain)
        .disabled(isSaving)
    }

    private func saveWeightEntry() {
        guard !isSaving else { return }
        focusedField = nil
        if let parsed = ProfileInputValidator.parseFlexibleWeight(weightText) {
            weight = parsed
        }
        let finalWeight = weight
        let finalDate = date
        let finalNotes = notes
        isSaving = true
        saveErrorMessage = nil

        Task { @MainActor in
            do {
                try await FormaDataStore.shared.createWeightEntryRemoteFirst(
                    weight: finalWeight,
                    date: finalDate,
                    notes: finalNotes
                )
                isSaving = false
                UINotificationFeedbackGenerator().notificationOccurred(.success)
                AuthManager.shared.showRealtimeToast("Pesagem registrada!")
                dismiss()
            } catch {
                isSaving = false
                saveErrorMessage = FormaAPIClient.humanizeErrorMessage(error.localizedDescription)
            }
        }
    }

    private func updateWeight(_ newValue: Double, syncText: Bool) {
        let clamped = max(30.0, min(300.0, (newValue * 10.0).rounded() / 10.0))
        weight = clamped
        if syncText {
            weightText = String(format: "%.1f", clamped)
        }
    }
}

//
//  AddWorkoutView.swift
//  Forma
//
//  Created by João Pedro on 10/27/26.
//

import SwiftUI
import SwiftData

struct AddWorkoutView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    
    @State private var title: String = ""
    @State private var selectedType: WorkoutType = .bodybuilding
    @State private var durationMinutes: Double = 50.0
    @State private var caloriesBurned: Double = 350.0
    @State private var notes: String = ""
    @State private var isSaving: Bool = false
    @State private var saveErrorMessage: String? = nil
    
    private var defaultCaloriesForType: Double {
        switch selectedType {
        case .bodybuilding: return durationMinutes * 7.0
        case .running: return durationMinutes * 11.0
        case .walking: return durationMinutes * 4.5
        case .cycling: return durationMinutes * 9.0
        case .functional: return durationMinutes * 8.5
        case .swimming: return durationMinutes * 10.0
        case .other: return durationMinutes * 6.0
        }
    }
    
    var body: some View {
        NavigationStack {
            Form {
                Section("Tipo de Treino") {
                    Picker("Modalidade", selection: $selectedType) {
                        ForEach(WorkoutType.allCases) { type in
                            Label(type.rawValue, systemImage: type.icon)
                                .tag(type)
                        }
                    }
                    .pickerStyle(.menu)
                    .onChange(of: selectedType) { _, _ in
                        caloriesBurned = defaultCaloriesForType
                    }
                    
                    TextField("Nome do Treino (ex: Peito e Tríceps)", text: $title)
                }
                
                Section("Duração e Intensidade") {
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("Duração")
                            Spacer()
                            Text("\(Int(durationMinutes)) min")
                                .fontWeight(.bold)
                                .foregroundColor(Color("AccentColor"))
                        }
                        Slider(value: $durationMinutes, in: 10...180, step: 5)
                            .tint(Color("AccentColor"))
                            .onChange(of: durationMinutes) { _, _ in
                                caloriesBurned = defaultCaloriesForType
                            }
                    }
                    .padding(.vertical, 4)
                    
                    VStack(alignment: .leading, spacing: 8) {
                        HStack {
                            Text("Calorias Estimadas")
                            Spacer()
                            Text("\(Int(caloriesBurned)) kcal")
                                .fontWeight(.bold)
                                .foregroundColor(Color("AccentGreen"))
                        }
                        Slider(value: $caloriesBurned, in: 50...1200, step: 10)
                            .tint(Color("AccentGreen"))
                    }
                    .padding(.vertical, 4)
                }
                
                Section("Observações e Cargas") {
                    TextField("Ex: Supino 4x10 (30kg cada lado), agachamento...", text: $notes, axis: .vertical)
                        .lineLimit(3...6)
                }
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .dismissKeyboardOnTap()
            .withDoneKeyboardToolbar()
            .navigationTitle("Registrar Treino")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isSaving ? "Salvando..." : "Salvar") {
                        guard !isSaving else { return }
                        let finalTitle = title.trimmingCharacters(in: .whitespaces).isEmpty ? selectedType.rawValue : title
                        let workout = WorkoutEntry(
                            title: finalTitle,
                            workoutType: selectedType,
                            durationMinutes: Int(durationMinutes),
                            caloriesBurned: caloriesBurned,
                            date: Date(),
                            notes: notes
                        )
                        isSaving = true
                        saveErrorMessage = nil
                        Task { @MainActor in
                            do {
                                try await FormaDataStore.shared.insertWorkoutEntryRemoteFirst(workout)
                                isSaving = false
                                dismiss()
                            } catch {
                                isSaving = false
                                saveErrorMessage = FormaAPIClient.humanizeErrorMessage(error.localizedDescription)
                            }
                        }
                    }
                    .disabled(isSaving)
                }
            }
            .alert(
                "Erro ao Sincronizar Treino",
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
}

/// Construtor limpo de ficha de treino manual integrado ao Catálogo 3D (usado na camada Essencial FREE e como opção rápida no PRO)
struct ManualRoutineBuilderView: View {
    @Environment(\.dismiss) private var dismiss

    @State private var routineTitle: String = ""
    @State private var selectedType: WorkoutType = .bodybuilding
    @State private var defaultRestSeconds: Int = 90
    @State private var notes: String = ""
    @State private var exercises: [WorkoutExerciseItem] = []

    @State private var showCatalogPicker: Bool = false
    @State private var previewCatalogItem: ExerciseCatalogItem? = nil
    @State private var isSaving: Bool = false
    @State private var saveErrorMessage: String? = nil

    private let restOptions: [Int] = [60, 90, 120, 150]
    private let repPresets: [String] = ["6-8", "8-10", "10-12", "12-15", "15-20", "Até a falha"]

    private var inferredTargetMuscles: String {
        var seen = Set<String>()
        var ordered: [String] = []
        for ex in exercises {
            let clean = ex.targetMuscle.trimmingCharacters(in: .whitespacesAndNewlines)
            if !clean.isEmpty && !seen.contains(clean) {
                seen.insert(clean)
                ordered.append(clean)
            }
        }
        return ordered.isEmpty ? selectedType.rawValue : ordered.joined(separator: ", ")
    }

    private var estimatedDurationMinutes: Int {
        let totalSets = exercises.reduce(0) { $0 + $1.sets }
        return max(25, min(120, totalSets * 3 + 10))
    }

    private var estimatedCalories: Double {
        Double(estimatedDurationMinutes) * 7.0
    }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    FormaCard(padding: 18) {
                        VStack(alignment: .leading, spacing: 14) {
                            HStack(spacing: 8) {
                                Image(systemName: "list.clipboard.fill")
                                    .foregroundStyle(FormaTheme.Colors.brand)
                                Text("Dados da Ficha")
                                    .font(.headline)
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                            }

                            VStack(alignment: .leading, spacing: 6) {
                                Text("Nome da Ficha")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.secondary)
                                TextField("Ex: Treino A — Peito e Tríceps", text: $routineTitle)
                                    .padding(12)
                                    .background(Color(.secondarySystemGroupedBackground))
                                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                            }

                            HStack {
                                Text("Modalidade")
                                    .font(.subheadline.weight(.medium))
                                    .foregroundStyle(FormaTheme.Colors.primaryText)
                                Spacer()
                                Picker("Modalidade", selection: $selectedType) {
                                    ForEach(WorkoutType.allCases) { type in
                                        Text(type.rawValue).tag(type)
                                    }
                                }
                                .pickerStyle(.menu)
                            }

                            VStack(alignment: .leading, spacing: 6) {
                                Text("Descanso Padrão (Ilha Dinâmica)")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.secondary)
                                Picker("Descanso", selection: $defaultRestSeconds) {
                                    ForEach(restOptions, id: \.self) { sec in
                                        Text("\(sec)s").tag(sec)
                                    }
                                }
                                .pickerStyle(.segmented)
                                .onChange(of: defaultRestSeconds) { _, newRest in
                                    for idx in exercises.indices {
                                        exercises[idx].restSeconds = newRest
                                    }
                                }
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 12) {
                        HStack {
                            Text("Exercícios (\(exercises.count))")
                                .font(.system(size: 17, weight: .bold, design: .rounded))
                                .foregroundStyle(FormaTheme.Colors.primaryText)
                            Spacer()
                            if !exercises.isEmpty {
                                Text("~\(estimatedDurationMinutes) min • \(Int(estimatedCalories)) kcal")
                                    .font(.caption.weight(.semibold))
                                    .foregroundStyle(.secondary)
                            }
                        }

                        if exercises.isEmpty {
                            FormaCard(padding: 20) {
                                VStack(spacing: 10) {
                                    Image(systemName: "figure.strengthtraining.traditional")
                                        .font(.system(size: 28))
                                        .foregroundStyle(FormaTheme.Colors.brand)
                                    Text("Adicione os exercícios da sua ficha")
                                        .font(.subheadline.weight(.semibold))
                                        .foregroundStyle(FormaTheme.Colors.primaryText)
                                    Text("Escolha no catálogo com 82 vídeos 3D de execução para acompanhar séries, cargas e descanso.")
                                        .font(.caption)
                                        .foregroundStyle(.secondary)
                                        .multilineTextAlignment(.center)
                                }
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 6)
                            }
                        } else {
                            ForEach(Array(exercises.enumerated()), id: \.element.id) { index, ex in
                                manualExerciseRow(index: index, item: ex)
                            }
                        }

                        Button {
                            showCatalogPicker = true
                        } label: {
                            HStack(spacing: 8) {
                                Image(systemName: "plus.circle.fill")
                                    .font(.system(size: 16, weight: .bold))
                                Text("Adicionar Exercício do Catálogo 3D")
                                    .font(.system(size: 14, weight: .bold, design: .rounded))
                            }
                            .foregroundStyle(FormaTheme.Colors.brand)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 13)
                            .background(FormaTheme.Colors.brandSoft)
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                        }
                        .buttonStyle(.plain)
                    }

                    FormaCard(padding: 18) {
                        VStack(alignment: .leading, spacing: 8) {
                            Text("Observações (opcional)")
                                .font(.caption.weight(.semibold))
                                .foregroundStyle(.secondary)
                            TextField("Ex: Cadência controlada, aquecer manguito antes...", text: $notes, axis: .vertical)
                                .lineLimit(2...4)
                                .font(.subheadline)
                        }
                    }
                }
                .padding(.horizontal, FormaTheme.Spacing.screenHorizontal)
                .padding(.vertical, 16)
            }
            .scrollIndicators(.hidden)
            .scrollDismissesKeyboard(.interactively)
            .dismissKeyboardOnTap()
            .withDoneKeyboardToolbar()
            .background(FormaTheme.Colors.pageBackground.ignoresSafeArea())
            .navigationTitle("Nova Ficha de Treino")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") {
                        dismiss()
                    }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(isSaving ? "Salvando..." : "Salvar Ficha") {
                        saveManualRoutine()
                    }
                    .fontWeight(.bold)
                    .disabled(exercises.isEmpty || isSaving)
                }
            }
            .sheet(isPresented: $showCatalogPicker) {
                ExerciseCatalogSheet { selectedCatalogItem in
                    appendCatalogExercise(selectedCatalogItem)
                }
            }
            .sheet(item: $previewCatalogItem) { item in
                ExerciseDetailView(item: item)
            }
            .alert(
                "Erro ao Salvar Ficha",
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

    private func manualExerciseRow(index: Int, item: WorkoutExerciseItem) -> some View {
        let currentLoad = item.setWeights.first ?? WorkoutExerciseItem.parseWeightKg(item.suggestedWeight) ?? 10.0
        return FormaCard(padding: 14) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(alignment: .top, spacing: 10) {
                    Button {
                        previewCatalogItem = ExerciseCatalog.shared.item(withId: item.animationId)
                            ?? ExerciseCatalog.shared.findBestMatch(for: item.name)
                    } label: {
                        ZStack {
                            RoundedRectangle(cornerRadius: 10, style: .continuous)
                                .fill(FormaTheme.Colors.brandSoft)
                                .frame(width: 36, height: 36)
                            Image(systemName: "play.fill")
                                .font(.system(size: 13, weight: .bold))
                                .foregroundStyle(FormaTheme.Colors.brand)
                        }
                    }
                    .buttonStyle(.plain)

                    VStack(alignment: .leading, spacing: 3) {
                        Text(item.name)
                            .font(.system(size: 15, weight: .bold, design: .rounded))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .lineLimit(2)
                        if !item.targetMuscle.isEmpty {
                            Text(item.targetMuscle)
                                .font(.caption2.weight(.semibold))
                                .foregroundStyle(FormaTheme.Colors.brand)
                        }
                    }

                    Spacer(minLength: 4)

                    Button {
                        withAnimation(.spring(duration: 0.25)) {
                            _ = exercises.remove(at: index)
                        }
                    } label: {
                        Image(systemName: "trash")
                            .font(.system(size: 13))
                            .foregroundStyle(FormaTheme.Colors.danger.opacity(0.8))
                            .padding(6)
                    }
                    .buttonStyle(.plain)
                }

                Divider().background(Color.primary.opacity(0.06))

                HStack(spacing: 10) {
                    HStack(spacing: 6) {
                        Button {
                            updateSets(at: index, newSets: max(1, item.sets - 1))
                        } label: {
                            Image(systemName: "minus.circle.fill")
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)

                        Text("\(item.sets) séries")
                            .font(.caption.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .frame(minWidth: 54)

                        Button {
                            updateSets(at: index, newSets: min(8, item.sets + 1))
                        } label: {
                            Image(systemName: "plus.circle.fill")
                                .foregroundStyle(FormaTheme.Colors.brand)
                        }
                        .buttonStyle(.plain)
                    }

                    Spacer()

                    Menu {
                        ForEach(repPresets, id: \.self) { preset in
                            Button("\(preset) reps") {
                                exercises[index].reps = preset
                            }
                        }
                    } label: {
                        HStack(spacing: 4) {
                            Text("\(item.reps) reps")
                                .font(.caption.weight(.bold))
                            Image(systemName: "chevron.down")
                                .font(.system(size: 9, weight: .bold))
                        }
                        .foregroundStyle(FormaTheme.Colors.brand)
                        .padding(.horizontal, 10)
                        .padding(.vertical, 5)
                        .background(FormaTheme.Colors.brandSoft)
                        .clipShape(Capsule())
                    }

                    Spacer()

                    HStack(spacing: 6) {
                        Button {
                            updateWeight(at: index, newKg: max(0, currentLoad - 1.0))
                        } label: {
                            Image(systemName: "minus.circle.fill")
                                .foregroundStyle(.secondary)
                        }
                        .buttonStyle(.plain)

                        Text(WorkoutExerciseItem.formatWeightKg(currentLoad))
                            .font(.caption.weight(.bold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)
                            .frame(minWidth: 52)

                        Button {
                            updateWeight(at: index, newKg: min(400, currentLoad + 1.0))
                        } label: {
                            Image(systemName: "plus.circle.fill")
                                .foregroundStyle(FormaTheme.Colors.brand)
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
        }
    }

    private func appendCatalogExercise(_ catalogItem: ExerciseCatalogItem) {
        let defaultKg = WorkoutExerciseItem.recommendedDefaultWeightKg(
            exerciseName: catalogItem.name,
            targetMuscle: catalogItem.muscleGroup
        )
        let newEx = WorkoutExerciseItem(
            name: catalogItem.name,
            sets: 3,
            reps: "10-12",
            suggestedWeight: WorkoutExerciseItem.formatWeightKg(defaultKg),
            targetMuscle: catalogItem.muscleGroup,
            executionTip: catalogItem.concentricInstruction,
            restSeconds: defaultRestSeconds,
            setWeights: Array(repeating: defaultKg, count: 3),
            animationId: catalogItem.id
        )
        withAnimation(.spring(duration: 0.25)) {
            exercises.append(newEx)
        }
    }

    private func updateSets(at index: Int, newSets: Int) {
        guard exercises.indices.contains(index) else { return }
        let baseLoad = exercises[index].setWeights.first ?? 10.0
        exercises[index].sets = newSets
        exercises[index].completedSets = Array(repeating: false, count: newSets)
        exercises[index].setWeights = Array(repeating: baseLoad, count: newSets)
    }

    private func updateWeight(at index: Int, newKg: Double) {
        guard exercises.indices.contains(index) else { return }
        let count = max(1, exercises[index].sets)
        exercises[index].setWeights = Array(repeating: newKg, count: count)
        exercises[index].suggestedWeight = WorkoutExerciseItem.formatWeightKg(newKg)
    }

    private func saveManualRoutine() {
        guard !exercises.isEmpty, !isSaving else { return }
        let cleanTitle = routineTitle.trimmingCharacters(in: .whitespacesAndNewlines)
        let finalTitle = cleanTitle.isEmpty ? "Ficha — \(inferredTargetMuscles)" : cleanTitle

        let routine = WorkoutRoutine(
            title: finalTitle,
            workoutType: selectedType,
            targetMuscles: inferredTargetMuscles,
            hypertrophyFocus: "Progressão de cargas e execução biomecânica",
            estimatedDurationMinutes: estimatedDurationMinutes,
            estimatedCalories: estimatedCalories,
            recommendedRestSeconds: defaultRestSeconds,
            isFavorite: false,
            exercises: exercises,
            notes: notes.trimmingCharacters(in: .whitespacesAndNewlines)
        )

        isSaving = true
        saveErrorMessage = nil
        Task { @MainActor in
            do {
                try await FormaDataStore.shared.saveWorkoutRoutineRemoteFirst(routine)
                isSaving = false
                dismiss()
            } catch {
                isSaving = false
                saveErrorMessage = FormaAPIClient.humanizeErrorMessage(error.localizedDescription)
            }
        }
    }
}

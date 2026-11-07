//
//  AIWorkoutLoggerView.swift
//  Forma
//
//  Created by João Pedro on 10/29/26.
//

import SwiftUI
import SwiftData

/// Assistente de Treino & Hipertrofia: descreva exercícios/treinos, calcule calorias, consulte biomecânica, salve fichas e inicie o modo ativo com checklist
struct AIWorkoutLoggerView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @Query private var profiles: [Profile]
    
    @ObservedObject private var geminiService = GeminiAIService.shared
    @ObservedObject private var voiceService = VoiceRecognitionService.shared
    @ObservedObject private var aiConsent = AIDataConsentManager.shared
    
    @State private var inputText: String = ""
    @State private var analyzedWorkout: AIExtractedWorkout? = nil
    @State private var errorMessage: String? = nil
    @State private var showAIConsentSheet: Bool = false
    
    // Estados de Ações
    @State private var isSavedAsRoutine: Bool = false
    @State private var isRoutineFavorited: Bool = true
    @State private var isLoggedToHistory: Bool = false
    @State private var showActiveSessionSheet: Bool = false
    @State private var expandedExerciseIndex: Int? = nil
    
    // Catálogo de GIFs & Biomecânica
    @State private var showCatalogSheet: Bool = false
    @State private var exerciseIndexToLink: Int? = nil
    @State private var selectedCatalogItemForDetail: ExerciseCatalogItem? = nil
    
    var onWorkoutLogged: (() -> Void)? = nil
    
    private var userWeight: Double {
        profiles.first?.currentWeight ?? 72.0
    }
    
    var body: some View {
        NavigationStack {
            ZStack {
                Color(.systemGroupedBackground)
                    .ignoresSafeArea()
                
                ScrollView {
                    VStack(spacing: 20) {
                        // Header com Resumo Fisiológico
                        headerBiometricsCard
                        
                        // Card de Entrada Livre (Texto / Descrição)
                        inputCard
                        
                        // Sugestões Rápidas de Treinos Populares
                        quickSuggestionsSection
                        
                        // Resultado da Análise de Hipertrofia e Calorias
                        if let workout = analyzedWorkout {
                            workoutAnalysisResultCard(workout: workout)
                        }
                    }
                    .padding(20)
                }
                .scrollIndicators(.hidden)
                .scrollDismissesKeyboard(.interactively)
            }
            .dismissKeyboardOnTap()
            .withDoneKeyboardToolbar()
            .navigationTitle("Criar ou Calcular Treino")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fechar") {
                        dismiss()
                    }
                }
            }
            .sheet(item: $selectedCatalogItemForDetail) { item in
                ExerciseDetailView(item: item)
            }
            .sheet(isPresented: $showCatalogSheet) {
                ExerciseCatalogSheet { selectedItem in
                    if let idx = exerciseIndexToLink, var w = analyzedWorkout, idx < w.exercises.count {
                        w.exercises[idx].animationId = selectedItem.id
                        analyzedWorkout = w
                    }
                    showCatalogSheet = false
                }
            }
            .sheet(isPresented: $showAIConsentSheet) {
                AIDataConsentSheet {
                    performWorkoutAnalysis()
                }
            }
            .onChange(of: voiceService.recognizedText) { _, newText in
                if voiceService.listeningMode == .workout && !newText.isEmpty {
                    inputText = newText
                }
            }
            .fullScreenCover(isPresented: $showActiveSessionSheet) {
                if let workout = analyzedWorkout {
                    let workoutTypeEnum = WorkoutType(rawValue: workout.workoutType) ?? .bodybuilding
                    ActiveWorkoutSessionView(
                        title: workout.title,
                        workoutType: workoutTypeEnum,
                        targetMuscles: workout.targetMuscles,
                        hypertrophyFocus: workout.hypertrophyFocus,
                        baseCalories: workout.caloriesBurned,
                        baseDurationMinutes: workout.durationMinutes,
                        initialRestSeconds: workout.recommendedRestSeconds,
                        exercises: workout.exercises
                    )
                }
            }
        }
    }
    
    // MARK: - Subviews
    
    private var headerBiometricsCard: some View {
        HStack(spacing: 12) {
            ZStack {
                RoundedRectangle(cornerRadius: 12, style: .continuous)
                    .fill(Color("AccentColor").opacity(0.12))
                    .frame(width: 44, height: 44)
                Image(systemName: "figure.strengthtraining.traditional")
                    .font(.system(size: 20))
                    .foregroundColor(Color("AccentColor"))
            }
            
            VStack(alignment: .leading, spacing: 2) {
                Text("Análise de Treino & Gasto Calórico")
                    .font(.system(size: 14, weight: .bold, design: .rounded))
                    .foregroundColor(Color("FontNormal"))
                Text("Calculado com IA Google Gemini & Compendium METs para \(Int(userWeight)) kg")
                    .font(.system(size: 11))
                    .foregroundColor(.secondary)
            }
            
            Spacer()
        }
        .padding(14)
        .background(Color("CardBackground"))
        .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
        .shadow(color: Color.black.opacity(0.03), radius: 6, x: 0, y: 2)
    }
    
    private var inputCard: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("DESCREVA SEU TREINO OU EXERCÍCIO")
                .font(.system(size: 11, weight: .bold))
                .foregroundColor(.secondary)
            
            HStack(alignment: .top, spacing: 10) {
                TextField("Ex: 'Supino reto 4x10 com 30kg/lado, crucifixo inclinado com halteres de 20kg e tríceps corda'", text: $inputText, axis: .vertical)
                    .lineLimit(3...6)
                    .textFieldStyle(.plain)
                    .font(.system(size: 15))
                
                VStack(spacing: 10) {
                    // Ícone único do microfone diretamente na caixa de texto
                    Button {
                        toggleVoiceRecording()
                    } label: {
                        if voiceService.isRecording {
                            Image(systemName: "stop.circle.fill")
                                .font(.system(size: 22))
                                .foregroundColor(.red)
                                .scaleEffect(1.0 + CGFloat(voiceService.audioLevel) * 0.4)
                                .animation(.easeInOut(duration: 0.12), value: voiceService.audioLevel)
                        } else {
                            Image(systemName: "mic.fill")
                                .font(.system(size: 20))
                                .foregroundColor(Color("AccentColor"))
                        }
                    }
                    .buttonStyle(.plain)
                    
                    if !inputText.isEmpty {
                        Button {
                            inputText = ""
                            analyzedWorkout = nil
                            isSavedAsRoutine = false
                            isLoggedToHistory = false
                        } label: {
                            Image(systemName: "xmark.circle.fill")
                                .font(.system(size: 18))
                                .foregroundColor(.gray.opacity(0.6))
                        }
                        .buttonStyle(.plain)
                    }
                }
            }
            .padding(12)
            .background(Color(.systemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            
            if let error = errorMessage {
                FormaBanner(
                    message: error,
                    style: .error,
                    retryTitle: "Repetir",
                    onRetry: !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        ? { analyzeWorkoutText() }
                        : nil
                )
            }
            
            Button {
                analyzeWorkoutText()
            } label: {
                HStack(spacing: 8) {
                    if geminiService.isLoading {
                        ProgressView()
                            .tint(.white)
                        Text("Montando seu treino...")
                    } else {
                        Image(systemName: "sparkles")
                        Text("Criar ou Calcular Treino")
                    }
                }
                .font(.system(size: 15, weight: .bold))
                .foregroundColor(.white)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 14)
                .background(inputText.trimmingCharacters(in: .whitespaces).isEmpty ? Color.gray.opacity(0.35) : Color("AccentColor"))
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            }
            .disabled(inputText.trimmingCharacters(in: .whitespaces).isEmpty || geminiService.isLoading)
        }
        .padding(16)
        .background(Color("CardBackground"))
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
        .shadow(color: Color.black.opacity(0.03), radius: 8, x: 0, y: 2)
    }
    
    private var quickSuggestionsSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            Text("Exemplos para testar:")
                .font(.system(size: 12, weight: .semibold))
                .foregroundColor(.secondary)
            
            ScrollView(.horizontal, showsIndicators: false) {
                HStack(spacing: 8) {
                    suggestionChip("Peito & Tríceps Hipertrofia 50min")
                    suggestionChip("Pernas Completo Foco Quadríceps")
                    suggestionChip("Costas & Bíceps Sobrecarga")
                    suggestionChip("Ombros e Trapézio 45min")
                    suggestionChip("Corrida na esteira 5km 30min")
                }
            }
        }
    }
    
    private func suggestionChip(_ text: String) -> some View {
        Button {
            inputText = text
            analyzeWorkoutText()
        } label: {
            Text(text)
                .font(.system(size: 12, weight: .medium))
                .foregroundColor(Color("AccentColor"))
                .padding(.horizontal, 12)
                .padding(.vertical, 8)
                .background(Color("AccentColor").opacity(0.08))
                .clipShape(Capsule())
        }
    }
    
    // MARK: - Card de Resultado da Análise
    
    private func workoutAnalysisResultCard(workout: AIExtractedWorkout) -> some View {
        VStack(alignment: .leading, spacing: 18) {
            // Header do Treino
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 4) {
                    HStack(spacing: 6) {
                        Text(workout.workoutType.uppercased())
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(Color("AccentColor"))
                            .padding(.horizontal, 8)
                            .padding(.vertical, 3)
                            .background(Color("AccentColor").opacity(0.1))
                            .clipShape(Capsule())
                        
                        Text(workout.intensity)
                            .font(.system(size: 10, weight: .semibold))
                            .foregroundColor(.secondary)
                    }
                    
                    Text(workout.title)
                        .font(.system(size: 18, weight: .bold, design: .rounded))
                        .foregroundColor(Color("FontNormal"))
                }
                
                Spacer()
                
                // Queima Calórica Destacada
                VStack(alignment: .trailing, spacing: 2) {
                    HStack(spacing: 4) {
                        Image(systemName: "flame.fill")
                            .foregroundColor(Color("AccentGreen"))
                        Text("\(Int(workout.caloriesBurned.rounded()))")
                            .font(.system(size: 26, weight: .bold, design: .rounded))
                            .foregroundColor(Color("AccentGreen"))
                    }
                    Text("kcal estimadas")
                        .font(.system(size: 11))
                        .foregroundColor(.secondary)
                }
            }
            
            // Pílulas de Métricas
            HStack(spacing: 8) {
                metricPill(title: "Duração", value: "\(workout.durationMinutes) min", icon: "clock.fill")
                metricPill(title: "Descanso", value: "\(workout.recommendedRestSeconds)s", icon: "timer")
                metricPill(title: "Exercícios", value: "\(workout.exercises.count)", icon: "list.bullet")
            }
            
            // Card de Hipertrofia & Fisiologia
            VStack(alignment: .leading, spacing: 10) {
                if !workout.targetMuscles.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("MÚSCULOS ENVOLVIDOS")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.secondary)
                        
                        Text(workout.targetMuscles)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundColor(Color("FontNormal"))
                    }
                }
                
                if !workout.hypertrophyFocus.isEmpty {
                    VStack(alignment: .leading, spacing: 4) {
                        Text("FOCO DO TREINO")
                            .font(.system(size: 10, weight: .bold))
                            .foregroundColor(.secondary)
                        
                        HStack(alignment: .top, spacing: 6) {
                            Image(systemName: "bolt.fill")
                                .font(.system(size: 12))
                                .foregroundColor(.orange)
                            Text(workout.hypertrophyFocus)
                                .font(.system(size: 12))
                                .foregroundColor(.secondary)
                        }
                    }
                }
                
                if !workout.aiFeedback.isEmpty {
                    HStack(alignment: .top, spacing: 6) {
                        Image(systemName: "lightbulb.fill")
                            .font(.system(size: 12))
                            .foregroundColor(Color("AccentColor"))
                        Text(workout.aiFeedback)
                            .font(.system(size: 12))
                            .foregroundColor(.secondary)
                    }
                    .padding(10)
                    .background(Color("AccentColor").opacity(0.06))
                    .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                }
            }
            .padding(14)
            .background(Color(.systemBackground))
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
            
            // Lista de Exercícios para Checklist com GIFs de Execução
            if !workout.exercises.isEmpty {
                VStack(alignment: .leading, spacing: 12) {
                    HStack {
                        Text("EXERCÍCIOS IDENTIFICADOS (\(workout.exercises.count))")
                            .font(.system(size: 11, weight: .bold))
                            .foregroundColor(.secondary)
                        
                        Spacer()
                        
                        Button {
                            autoAssociateAllGIFs()
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "wand.and.stars")
                                    .font(.system(size: 11))
                                Text("Vincular Exercícios")
                                    .font(.system(size: 11, weight: .bold))
                            }
                            .foregroundColor(Color("AccentColor"))
                            .padding(.horizontal, 10)
                            .padding(.vertical, 4)
                            .background(Color("AccentColor").opacity(0.1))
                            .clipShape(Capsule())
                        }
                    }
                    
                    ForEach(workout.exercises.indices, id: \.self) { index in
                        exercisePreviewRow(exercise: workout.exercises[index], index: index)
                    }
                }
            }
            
            Divider()
                .background(Color.primary.opacity(0.08))
            
            // Ações: Iniciar com Checklist, Salvar Ficha, ou Registrar
            VStack(spacing: 10) {
                // 1. Iniciar com Checklist Agora (Auxílio ao Vivo durante o treino)
                Button {
                    showActiveSessionSheet = true
                } label: {
                    HStack(spacing: 8) {
                        Image(systemName: "play.circle.fill")
                            .font(.system(size: 16, weight: .bold))
                        Text("Iniciar Treino com Checklist & Timer")
                            .font(.system(size: 15, weight: .bold))
                    }
                    .foregroundColor(.white)
                    .frame(maxWidth: .infinity)
                    .padding(.vertical, 14)
                    .background(Color("AccentColor"))
                    .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                    .shadow(color: Color("AccentColor").opacity(0.25), radius: 6, x: 0, y: 3)
                }
                
                HStack(spacing: 10) {
                    // 2. Salvar como Ficha / Rotina Salva
                    Button {
                        saveAsRoutine(workout: workout)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: isSavedAsRoutine ? "checkmark.circle.fill" : (isRoutineFavorited ? "star.fill" : "star"))
                                .foregroundColor(isSavedAsRoutine ? .green : .orange)
                            Text(isSavedAsRoutine ? "Ficha Salva!" : "Salvar Ficha")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(Color("FontNormal"))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(Color(.systemBackground))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                        .overlay(
                            RoundedRectangle(cornerRadius: 12, style: .continuous)
                                .stroke(Color.primary.opacity(0.08), lineWidth: 1)
                        )
                    }
                    
                    // 3. Registrar como Treino Já Feito Hoje
                    Button {
                        logDirectlyToHistory(workout: workout)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: isLoggedToHistory ? "checkmark" : "plus.circle")
                                .foregroundColor(isLoggedToHistory ? .white : Color("FontNormal"))
                            Text(isLoggedToHistory ? "Registrado!" : "Marcar Feito")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundColor(isLoggedToHistory ? .white : Color("FontNormal"))
                        }
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(isLoggedToHistory ? Color.green : Color.gray.opacity(0.12))
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                }
            }
        }
        .padding(18)
        .background(Color("CardBackground"))
        .clipShape(RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: Color.black.opacity(0.04), radius: 10, x: 0, y: 3)
    }
    
    private func exercisePreviewRow(exercise: WorkoutExerciseItem, index: Int) -> some View {
        let catalogItem = ExerciseCatalog.shared.item(withId: exercise.animationId) ?? ExerciseCatalog.shared.findBestMatch(for: exercise.name)
        
        return HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 4) {
                Text(exercise.name)
                    .font(.system(size: 15, weight: .bold))
                    .foregroundColor(Color("FontNormal"))
                
                HStack(spacing: 6) {
                    Text("\(exercise.sets)x \(exercise.reps)")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundColor(Color("AccentColor"))
                    
                    if !exercise.suggestedWeight.isEmpty {
                        Text("•")
                            .foregroundColor(.secondary)
                        Text(exercise.suggestedWeight)
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                    }
                    
                    if !exercise.targetMuscle.isEmpty {
                        Text("•")
                            .foregroundColor(.secondary)
                        Text(exercise.targetMuscle)
                            .font(.system(size: 11))
                            .foregroundColor(.secondary)
                    }
                }
            }
            
            Spacer()
            
            // Botão de Info do Exercício (Abre GIF em alta resolução e biomecânica)
            Button {
                if let item = catalogItem {
                    selectedCatalogItemForDetail = item
                } else {
                    exerciseIndexToLink = index
                    showCatalogSheet = true
                }
            } label: {
                HStack(spacing: 4) {
                    Image(systemName: "info.circle.fill")
                        .font(.system(size: 16))
                    Text("Info")
                        .font(.system(size: 12, weight: .bold))
                }
                .foregroundColor(Color("AccentColor"))
                .padding(.horizontal, 10)
                .padding(.vertical, 6)
                .background(Color("AccentColor").opacity(0.1))
                .clipShape(Capsule())
            }
            .buttonStyle(.plain)
        }
        .padding(12)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
    }
    
    private func metricPill(title: String, value: String, icon: String) -> some View {
        VStack(spacing: 3) {
            Image(systemName: icon)
                .font(.system(size: 11))
                .foregroundColor(Color("AccentColor"))
            Text(value)
                .font(.system(size: 13, weight: .bold, design: .rounded))
                .foregroundColor(Color("FontNormal"))
            Text(title)
                .font(.system(size: 10))
                .foregroundColor(.secondary)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 8)
        .background(Color(.systemBackground))
        .clipShape(RoundedRectangle(cornerRadius: 10, style: .continuous))
    }
    
    // MARK: - Ações
    
    private func toggleVoiceRecording() {
        if voiceService.isRecording {
            voiceService.stopListening()
        } else {
            Task {
                let granted = await voiceService.requestPermissions()
                if granted {
                    voiceService.startListening(mode: .workout)
                } else {
                    errorMessage = "Permissão de microfone necessária para ditar treino por voz."
                }
            }
        }
    }
    
    private func autoAssociateAllGIFs() {
        guard var current = analyzedWorkout else { return }
        current.exercises = ExerciseCatalog.shared.autoAssociateExercises(in: current.exercises)
        withAnimation(.spring(duration: 0.3)) {
            self.analyzedWorkout = current
        }
        let feedback = UINotificationFeedbackGenerator()
        feedback.notificationOccurred(.success)
    }
    
    private func analyzeWorkoutText() {
        guard !inputText.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        if !aiConsent.hasGrantedConsent {
            showAIConsentSheet = true
            return
        }
        performWorkoutAnalysis()
    }

    private func performWorkoutAnalysis() {
        guard !inputText.trimmingCharacters(in: .whitespaces).isEmpty else { return }
        errorMessage = nil
        isSavedAsRoutine = false
        isLoggedToHistory = false
        
        Task {
            do {
                let result = try await geminiService.analyzeWorkout(description: inputText, userWeight: userWeight)
                withAnimation(.spring(duration: 0.35)) {
                    self.analyzedWorkout = result
                }
            } catch {
                errorMessage = error.localizedDescription
            }
        }
    }
    
    private func saveAsRoutine(workout: AIExtractedWorkout) {
        let workoutTypeEnum = WorkoutType(rawValue: workout.workoutType) ?? .bodybuilding
        let routine = WorkoutRoutine(
            title: workout.title,
            workoutType: workoutTypeEnum,
            targetMuscles: workout.targetMuscles,
            hypertrophyFocus: workout.hypertrophyFocus,
            estimatedDurationMinutes: workout.durationMinutes,
            estimatedCalories: workout.caloriesBurned,
            recommendedRestSeconds: workout.recommendedRestSeconds,
            isFavorite: isRoutineFavorited,
            exercises: workout.exercises,
            notes: workout.aiFeedback
        )

        errorMessage = nil
        Task { @MainActor in
            do {
                try await FormaDataStore.shared.saveWorkoutRoutineRemoteFirst(routine)
                let feedback = UINotificationFeedbackGenerator()
                feedback.notificationOccurred(.success)
                withAnimation {
                    isSavedAsRoutine = true
                }
            } catch {
                errorMessage = FormaAPIClient.humanizeErrorMessage(error.localizedDescription)
            }
        }
    }
    
    private func logDirectlyToHistory(workout: AIExtractedWorkout) {
        let workoutTypeEnum = WorkoutType(rawValue: workout.workoutType) ?? .bodybuilding
        let entry = WorkoutEntry(
            title: workout.title,
            workoutType: workoutTypeEnum,
            durationMinutes: workout.durationMinutes,
            caloriesBurned: workout.caloriesBurned,
            date: Date(),
            notes: workout.aiFeedback
        )

        errorMessage = nil
        Task { @MainActor in
            do {
                try await FormaDataStore.shared.insertWorkoutEntryRemoteFirst(entry)
                let feedback = UINotificationFeedbackGenerator()
                feedback.notificationOccurred(.success)
                withAnimation {
                    isLoggedToHistory = true
                }
                try? await Task.sleep(nanoseconds: 900_000_000)
                onWorkoutLogged?()
                dismiss()
            } catch {
                errorMessage = FormaAPIClient.humanizeErrorMessage(error.localizedDescription)
            }
        }
    }
}

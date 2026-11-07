//
//  VoiceAddFoodView.swift
//  Forma
//
//  Registrador unificado e multimodal de refeições (Foto via Câmera/Galeria, Voz e Texto + Base TACO / Inteligência Forma).
//

import SwiftUI
import SwiftData
import PhotosUI
import UIKit

struct VoiceAddFoodView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.modelContext) private var modelContext
    @Query(sort: \FoodEntry.date, order: .reverse) private var allFoodEntries: [FoodEntry]

    @StateObject private var voiceService = VoiceRecognitionService.shared
    @ObservedObject private var aiConsent = AIDataConsentManager.shared

    var preselectedMeal: MealType? = nil
    var initialOpenCamera: Bool = false
    var onFoodSaved: ((FoodEntry) -> Void)? = nil

    @State private var inputText: String = ""
    @State private var selectedImage: UIImage? = nil
    @State private var selectedImageData: Data? = nil
    @State private var selectedPhotoPickerItem: PhotosPickerItem? = nil
    @State private var showCameraSheet: Bool = false
    @State private var showAIConsentSheet: Bool = false
    @State private var pendingAIConsentAction: (() -> Void)? = nil

    @State private var editingPortionItem: VoicePlateItem? = nil
    @State private var customPortionString: String = ""

    @State private var isSavedSuccessfully: Bool = false
    @State private var isSavingToDatabase: Bool = false
    @State private var saveErrorMessage: String? = nil
    @FocusState private var isFieldFocused: Bool
    @State private var textDebounceTask: Task<Void, Never>? = nil

    var body: some View {
        NavigationStack {
            ZStack {
                FormaTheme.Colors.pageBackground
                    .ignoresSafeArea()

                if isSavedSuccessfully {
                    FoodSavedConfirmationView(intent: voiceService.currentIntent) {
                        dismiss()
                    }
                } else {
                    mainContent
                }
            }
            .navigationTitle("Adicionar Refeição")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") {
                        textDebounceTask?.cancel()
                        voiceService.stopListening(shouldAnalyze: false)
                        dismiss()
                    }
                }
            }
            .fullScreenCover(isPresented: $showCameraSheet) {
                FoodCameraPicker(sourceType: .camera) { capturedImage in
                    handlePickedUIImage(capturedImage)
                }
                .ignoresSafeArea()
            }
            .sheet(isPresented: $showAIConsentSheet) {
                AIDataConsentSheet(
                    onAuthorized: {
                        let action = pendingAIConsentAction
                        pendingAIConsentAction = nil
                        DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                            action?()
                        }
                    },
                    onDeclined: {
                        pendingAIConsentAction = nil
                        if selectedImage != nil && (voiceService.currentIntent?.items.isEmpty ?? true) {
                            clearSelectedPhoto()
                        }
                    }
                )
            }
            .onChange(of: selectedPhotoPickerItem) { _, newItem in
                guard let newItem else { return }
                Task {
                    if let rawData = try? await newItem.loadTransferable(type: Data.self),
                       let uiImage = UIImage(data: rawData) {
                        await MainActor.run {
                            handlePickedUIImage(uiImage)
                        }
                    }
                }
            }
            .alert(
                editingPortionItem != nil ? "Ajustar \(editingPortionItem!.name)" : "Ajustar Quantidade",
                isPresented: Binding(
                    get: { editingPortionItem != nil },
                    set: { if !$0 { editingPortionItem = nil } }
                )
            ) {
                TextField(
                    editingPortionItem?.isLiquid == true ? "Quantidade em ml" : "Quantidade em g",
                    text: $customPortionString
                )
                .keyboardType(.decimalPad)

                Button("Cancelar", role: .cancel) {
                    editingPortionItem = nil
                }
                Button("Aplicar") {
                    if let item = editingPortionItem {
                        let normalized = customPortionString.replacingOccurrences(of: ",", with: ".")
                        if let val = Double(normalized), val > 0 {
                            voiceService.updateItemPortion(id: item.id, newPortion: val)
                        }
                    }
                    editingPortionItem = nil
                }
            } message: {
                if let item = editingPortionItem {
                    Text("Informe o peso ou volume exato em \(item.isLiquid || item.unit == "ml" ? "ml" : "gramas (g)"):")
                }
            }
            .alert(
                "Erro ao Sincronizar Refeição",
                isPresented: Binding(
                    get: { saveErrorMessage != nil },
                    set: { if !$0 { saveErrorMessage = nil } }
                )
            ) {
                Button("OK", role: .cancel) { saveErrorMessage = nil }
            } message: {
                Text(saveErrorMessage ?? "")
            }
            .onAppear {
                voiceService.errorMessage = nil
                let defaultMeal = preselectedMeal ?? AIFoodParserService.shared.inferPrimaryMealType()
                voiceService.updateMealType(defaultMeal)
                if initialOpenCamera {
                    voiceService.stopListening(shouldAnalyze: false)
                    DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                        runWithAIConsent {
                            showCameraSheet = true
                        }
                    }
                } else {
                    startVoiceFlow()
                }
            }
            .onDisappear {
                voiceService.stopListening(shouldAnalyze: false)
                textDebounceTask?.cancel()
            }
            .onChange(of: voiceService.recognizedText) { _, newText in
                if !newText.isEmpty && voiceService.isRecording {
                    inputText = newText
                }
            }
            .onChange(of: isFieldFocused) { wasFocused, nowFocused in
                if nowFocused && voiceService.isRecording {
                    voiceService.stopListening(shouldAnalyze: false)
                } else if wasFocused && !nowFocused && !voiceService.isRecording {
                    textDebounceTask?.cancel()
                    let trimmed = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
                    if selectedImageData == nil,
                       !trimmed.isEmpty,
                       !voiceService.isIncompleteFragment(trimmed),
                       !voiceService.hasDanglingConnectorOrNumber(trimmed) {
                        voiceService.analyzeIntent(from: trimmed, forceAI: false, allowCloudAI: true)
                    }
                }
            }
        }
    }

    private var activeMealType: MealType {
        voiceService.currentIntent?.mealType ?? voiceService.selectedMealType
    }

    private var smartSuggestions: [SmartFoodSuggestion] {
        SmartFoodSuggestionEngine.suggestions(
            from: allFoodEntries,
            for: activeMealType,
            referenceDate: Date(),
            limit: 5
        )
    }

    private var currentPlateNames: Set<String> {
        Set((voiceService.currentIntent?.items ?? []).map { $0.name.lowercased() })
    }

    private var mainContent: some View {
        ScrollView {
            VStack(spacing: FormaTheme.Spacing.sectionGap) {
                inputCard

                if let intent = voiceService.currentIntent, !intent.items.isEmpty {
                    FoodPlateResultCard(
                        intent: intent,
                        isAnalyzing: voiceService.isAnalyzing,
                        onUpdateMealType: { meal in
                            voiceService.updateMealType(meal)
                        },
                        onRecalculateAI: {
                            triggerManualAIAnalysis(forceAI: true)
                        },
                        onAdjustPortion: { id, delta in
                            voiceService.adjustItemPortion(id: id, delta: delta)
                        },
                        onEditExactPortion: { item in
                            customPortionString = "\(Int(item.portionValue.rounded()))"
                            editingPortionItem = item
                        },
                        onRemoveItem: { id in
                            withAnimation(.spring(duration: 0.25)) {
                                voiceService.removeItem(id: id)
                            }
                        },
                        onConfirm: {
                            confirmFoodEntry()
                        }
                    )
                }

                if selectedImage == nil {
                    FoodQuickSuggestionsSection(
                        mealType: activeMealType,
                        suggestions: smartSuggestions,
                        currentPlateNames: currentPlateNames
                    ) { suggestion in
                        textDebounceTask?.cancel()
                        isFieldFocused = false
                        let feedback = UIImpactFeedbackGenerator(style: .light)
                        feedback.impactOccurred()
                        withAnimation(.spring(duration: 0.25)) {
                            voiceService.addSuggestedPlateItem(suggestion.toPlateItem(), mealType: activeMealType)
                        }
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
    }

    private var inputCard: some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    Text(
                        voiceService.isRecording
                            ? "Ouvindo você..."
                            : (selectedImage != nil ? "Foto da refeição" : "O que você comeu?")
                    )
                    .font(.subheadline.weight(.semibold))
                    .foregroundStyle(voiceService.isRecording ? FormaTheme.Colors.brand : .secondary)
                    .lineLimit(1)

                    Spacer(minLength: 6)

                    if voiceService.isAnalyzing {
                        FoodAnalysisPhaseBadge(phase: voiceService.analysisPhase)
                            .animation(.easeInOut(duration: 0.3), value: voiceService.analysisPhase)
                    } else {
                        if !inputText.isEmpty && selectedImage == nil {
                            Button {
                                inputText = ""
                                voiceService.currentIntent = nil
                                voiceService.recognizedText = ""
                                voiceService.errorMessage = nil
                            } label: {
                                Image(systemName: "xmark.circle.fill")
                                    .font(.system(size: 16))
                                    .foregroundStyle(.secondary.opacity(0.6))
                            }
                            .buttonStyle(.plain)
                        }

                        if voiceService.currentIntent?.items.isEmpty ?? true {
                            MealTypeSelectMenu(selectedMeal: activeMealType) { meal in
                                voiceService.updateMealType(meal)
                            }
                        }
                    }
                }

                HStack(spacing: 10) {
                    Button {
                        textDebounceTask?.cancel()
                        isFieldFocused = false
                        voiceService.stopListening(shouldAnalyze: false)
                        runWithAIConsent {
                            showCameraSheet = true
                        }
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "camera.fill")
                                .font(.system(size: 13, weight: .semibold))
                            Text(selectedImage == nil ? "Tirar Foto" : "Nova Foto")
                                .font(.system(size: 13, weight: .semibold, design: .rounded))
                        }
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(FormaTheme.Colors.brand)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)

                    PhotosPicker(
                        selection: $selectedPhotoPickerItem,
                        matching: .images,
                        photoLibrary: .shared()
                    ) {
                        HStack(spacing: 6) {
                            Image(systemName: "photo.on.rectangle.angled")
                                .font(.system(size: 13, weight: .semibold))
                            Text("Galeria")
                                .font(.system(size: 13, weight: .semibold, design: .rounded))
                        }
                        .foregroundStyle(FormaTheme.Colors.brand)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(FormaTheme.Colors.brandSoft)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                    .simultaneousGesture(TapGesture().onEnded {
                        textDebounceTask?.cancel()
                        isFieldFocused = false
                        voiceService.stopListening(shouldAnalyze: false)
                    })
                }

                if let uiImage = selectedImage {
                    ZStack(alignment: .topTrailing) {
                        Image(uiImage: uiImage)
                            .resizable()
                            .scaledToFill()
                            .frame(maxWidth: .infinity)
                            .frame(height: 175)
                            .clipped()
                            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                            .overlay(
                                RoundedRectangle(cornerRadius: 14, style: .continuous)
                                    .stroke(FormaTheme.Colors.brand.opacity(0.25), lineWidth: 1)
                            )

                        Button {
                            withAnimation(.spring(duration: 0.25)) {
                                clearSelectedPhoto()
                            }
                        } label: {
                            HStack(spacing: 4) {
                                Image(systemName: "xmark")
                                    .font(.system(size: 10, weight: .bold))
                                Text("Remover")
                                    .font(.system(size: 11, weight: .bold))
                            }
                            .foregroundStyle(.white)
                            .padding(.horizontal, 10)
                            .padding(.vertical, 6)
                            .background(.ultraThinMaterial)
                            .background(Color.black.opacity(0.45))
                            .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)
                        .padding(10)
                    }
                }

                HStack(alignment: .top, spacing: 10) {
                    TextField(
                        selectedImage != nil
                            ? "Observação opcional (ex: arroz integral, sem óleo)..."
                            : "Fale, digite ou fotografe (ex: 4 colheres de arroz e 2 de feijão)",
                        text: $inputText,
                        axis: .vertical
                    )
                    .lineLimit(2...5)
                    .font(.system(size: 15))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .focused($isFieldFocused)
                    .submitLabel(.done)
                    .onSubmit {
                        triggerManualAIAnalysis(forceAI: false)
                    }
                    .onChange(of: inputText) { _, newValue in
                        handleTextChange(newValue)
                    }

                    Button {
                        toggleRecording()
                    } label: {
                        ZStack {
                            Circle()
                                .fill(voiceService.isRecording ? FormaTheme.Colors.brand : Color.primary.opacity(0.06))
                                .frame(width: 44, height: 44)

                            Image(systemName: voiceService.isRecording ? "waveform" : "mic.fill")
                                .font(.system(size: 18, weight: .semibold))
                                .foregroundStyle(voiceService.isRecording ? .white : FormaTheme.Colors.brand)
                        }
                    }
                    .buttonStyle(.plain)
                }
                .padding(14)
                .background(Color(.systemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 16, style: .continuous)
                        .stroke(voiceService.isRecording ? FormaTheme.Colors.brand : Color.primary.opacity(0.08), lineWidth: voiceService.isRecording ? 1.5 : 0.5)
                )

                if let errorMsg = voiceService.errorMessage, !errorMsg.isEmpty {
                    FormaBanner(
                        message: errorMsg,
                        style: .error,
                        retryTitle: "Repetir",
                        onRetry: (selectedImageData != nil || !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                            ? { triggerManualAIAnalysis(forceAI: true) }
                            : nil
                    )
                }

                if !voiceService.isAnalyzing {
                    if selectedImageData != nil {
                        aiActionButton(
                            title: inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                                ? "Identificar Prato com Inteligência Forma"
                                : "Recalcular Foto com Inteligência Forma"
                        ) {
                            triggerManualAIAnalysis(forceAI: true)
                        }
                    } else if !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                        aiActionButton(title: "Identificar Macros com Inteligência Forma") {
                            triggerManualAIAnalysis(forceAI: true)
                        }
                    }
                }

                HStack(spacing: 6) {
                    Image(systemName: selectedImage != nil ? "slider.horizontal.3" : "hand.tap")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                    Text(
                        selectedImage != nil
                            ? "Ajuste as quantidades abaixo ou descreva detalhes do preparo."
                            : "Tire uma foto do prato, fale pelo microfone ou digite o que comeu."
                    )
                    .font(.caption)
                    .foregroundStyle(.secondary)
                }
            }
        }
    }

    private func aiActionButton(title: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            HStack(spacing: 6) {
                Image(systemName: "sparkles")
                    .font(.system(size: 13, weight: .bold))
                Text(title)
                    .font(.system(size: 13, weight: .bold, design: .rounded))
            }
            .foregroundStyle(.white)
            .frame(maxWidth: .infinity)
            .padding(.vertical, 10)
            .background(FormaTheme.Colors.aiGradient)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
            .shadow(color: FormaTheme.Colors.aiAccent.opacity(0.22), radius: 6, x: 0, y: 2)
        }
        .buttonStyle(.plain)
    }

    private func runWithAIConsent(_ action: @escaping () -> Void) {
        if aiConsent.hasGrantedConsent {
            action()
        } else {
            pendingAIConsentAction = action
            showAIConsentSheet = true
        }
    }

    private func triggerManualAIAnalysis(forceAI: Bool) {
        textDebounceTask?.cancel()
        isFieldFocused = false
        voiceService.stopListening(shouldAnalyze: false)
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)

        if let imgData = selectedImageData {
            runWithAIConsent {
                voiceService.analyzePhoto(imageData: imgData, hintText: inputText)
            }
        } else {
            let trimmed = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
            let fallback = voiceService.currentIntent?.rawText ?? ""
            let query = !trimmed.isEmpty ? trimmed : fallback
            guard !query.isEmpty else { return }
            if forceAI {
                runWithAIConsent {
                    voiceService.analyzeIntent(from: query, forceAI: true, allowCloudAI: true)
                }
            } else {
                voiceService.analyzeIntent(from: query, forceAI: false, allowCloudAI: aiConsent.hasGrantedConsent)
            }
        }
    }

    private func handlePickedUIImage(_ image: UIImage) {
        textDebounceTask?.cancel()
        isFieldFocused = false
        voiceService.stopListening(shouldAnalyze: false)

        guard let compressed = image.compressedForGemini(maxDimension: 1024, quality: 0.72) else {
            voiceService.errorMessage = "Não foi possível processar a foto selecionada."
            return
        }

        selectedImage = image
        selectedImageData = compressed
        runWithAIConsent {
            voiceService.analyzePhoto(imageData: compressed, hintText: inputText)
        }
    }

    private func clearSelectedPhoto() {
        selectedImage = nil
        selectedImageData = nil
        selectedPhotoPickerItem = nil
        voiceService.errorMessage = nil
        let trimmed = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.isEmpty {
            voiceService.currentIntent = nil
        } else {
            voiceService.analyzeIntent(from: trimmed, forceAI: false, allowCloudAI: true)
        }
    }

    private func handleTextChange(_ newText: String) {
        textDebounceTask?.cancel()
        guard selectedImageData == nil else { return }

        let trimmed = newText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        if !voiceService.isRecording {
            textDebounceTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 450_000_000)
                guard !Task.isCancelled else { return }
                voiceService.analyzeIntent(from: trimmed, forceAI: false, allowCloudAI: false)
            }
        }
    }

    private func startVoiceFlow() {
        Task {
            let authorized = await voiceService.requestPermissions()
            if authorized && selectedImage == nil && !showCameraSheet {
                voiceService.startListening()
            }
        }
    }

    private func toggleRecording() {
        textDebounceTask?.cancel()
        if voiceService.isRecording {
            voiceService.stopListening(shouldAnalyze: selectedImageData == nil)
            if let imgData = selectedImageData {
                runWithAIConsent {
                    voiceService.analyzePhoto(imageData: imgData, hintText: inputText)
                }
            }
        } else {
            isFieldFocused = false
            voiceService.startListening()
        }
    }

    private func confirmFoodEntry() {
        guard !isSavingToDatabase,
              let intent = voiceService.currentIntent,
              !intent.items.isEmpty else { return }

        let now = Date()
        let entries: [FoodEntry] = intent.items.map { item in
            FoodEntry(
                name: item.name,
                brand: item.brand,
                mealType: intent.mealType,
                portionGrams: item.portionValue,
                calories: item.calories,
                protein: item.protein,
                carbohydrates: item.carbs,
                fat: item.fat,
                date: now
            )
        }

        isSavingToDatabase = true
        saveErrorMessage = nil

        Task { @MainActor in
            do {
                try await FormaDataStore.shared.insertFoodEntriesRemoteFirst(entries)
                for entry in entries {
                    onFoodSaved?(entry)
                }

                let generator = UINotificationFeedbackGenerator()
                generator.notificationOccurred(.success)
                isSavingToDatabase = false

                withAnimation(.spring(duration: 0.35)) {
                    isSavedSuccessfully = true
                }

                try? await Task.sleep(nanoseconds: 900_000_000)
                dismiss()
            } catch {
                isSavingToDatabase = false
                saveErrorMessage = FormaAPIClient.humanizeErrorMessage(error.localizedDescription)
            }
        }
    }
}

#Preview {
    VoiceAddFoodView()
        .modelContainer(PreviewContainer.shared)
}

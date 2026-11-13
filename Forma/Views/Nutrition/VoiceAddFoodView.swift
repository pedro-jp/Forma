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
    @ObservedObject private var authManager = AuthManager.shared

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
            .navigationTitle(authManager.isPro ? "Adicionar Refeição" : "Adicionar Alimento")
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
            .sheet(item: $editingPortionItem) { item in
                VoicePlateItemPortionSheet(
                    item: item,
                    onApply: { newPortion in
                        voiceService.updateItemPortion(id: item.id, newPortion: newPortion)
                    },
                    onRemove: {
                        voiceService.removeItem(id: item.id)
                    }
                )
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
                if authManager.isPro {
                    if initialOpenCamera {
                        voiceService.stopListening(shouldAnalyze: false)
                        runWithAIConsent {
                            DispatchQueue.main.asyncAfter(deadline: .now() + 0.25) {
                                if authManager.isPro {
                                    showCameraSheet = true
                                }
                            }
                        }
                    } else if aiConsent.hasGrantedConsent {
                        startVoiceFlow()
                    }
                } else {
                    voiceService.cutAllAIFlowsForFreeTier()
                }
            }
            .onDisappear {
                voiceService.stopListening(shouldAnalyze: false)
                textDebounceTask?.cancel()
            }
            .onChange(of: authManager.accountTier) { _, newTier in
                handleAccountTierChange(newTier)
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
                        voiceService.analyzeIntent(
                            from: trimmed,
                            forceAI: authManager.isPro,
                            allowCloudAI: authManager.isPro
                        )
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
                if authManager.isPro {
                    inputCard
                } else {
                    essentialInputCard
                }

                if let intent = voiceService.currentIntent, !intent.items.isEmpty {
                    FoodPlateResultCard(
                        intent: intent,
                        isAnalyzing: voiceService.isAnalyzing,
                        isPro: authManager.isPro,
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

    private var hasInterpretedItems: Bool {
        !(voiceService.currentIntent?.items.isEmpty ?? true)
    }

    private var headerStatusTitle: String {
        if voiceService.isRecording {
            return "Ouvindo por voz..."
        }
        if hasInterpretedItems {
            return "Refeição interpretada via IA"
        }
        return selectedImage != nil ? "Foto da refeição" : "O que você comeu?"
    }

    private var inputCard: some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    if voiceService.isRecording {
                        Circle()
                            .fill(Color(red: 1.0, green: 0.27, blue: 0.23))
                            .frame(width: 8, height: 8)
                            .shadow(color: Color(red: 1.0, green: 0.27, blue: 0.23).opacity(0.85), radius: 5)
                    } else if hasInterpretedItems {
                        Circle()
                            .fill(Color(red: 0.20, green: 0.86, blue: 0.52))
                            .frame(width: 8, height: 8)
                            .shadow(color: Color(red: 0.20, green: 0.86, blue: 0.52).opacity(0.85), radius: 5)
                    }

                    Text(headerStatusTitle)
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle((voiceService.isRecording || hasInterpretedItems) ? FormaTheme.Colors.primaryText : .secondary)
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

                if selectedImage == nil {
                    FormaVoiceWaveformView(voiceService: voiceService)
                        .onTapGesture {
                            toggleRecording()
                        }
                        .transition(.opacity.combined(with: .scale(scale: 0.96)))
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
                        triggerManualAIAnalysis(forceAI: true)
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

    private var essentialInputCard: some View {
        FormaCard(padding: 18) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 8) {
                    if hasInterpretedItems {
                        Circle()
                            .fill(FormaTheme.Colors.energyGreen)
                            .frame(width: 8, height: 8)
                    } else {
                        Image(systemName: "book.closed.fill")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(FormaTheme.Colors.tacoCyan)
                    }

                    Text(hasInterpretedItems ? "Alimentos calculados • Tabela TACO" : "Busca Direta • Tabela TACO")
                        .font(.subheadline.weight(.semibold))
                        .foregroundStyle(FormaTheme.Colors.primaryText)
                        .lineLimit(1)

                    Spacer(minLength: 6)

                    if !inputText.isEmpty {
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

                HStack(alignment: .center, spacing: 10) {
                    Image(systemName: "magnifyingglass")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(FormaTheme.Colors.brand)

                    TextField(
                        "Ex: 150g arroz, 120g frango, 2 ovos...",
                        text: $inputText,
                        axis: .vertical
                    )
                    .lineLimit(1...4)
                    .font(.system(size: 15))
                    .foregroundStyle(FormaTheme.Colors.primaryText)
                    .focused($isFieldFocused)
                    .submitLabel(.search)
                    .onSubmit {
                        triggerManualAIAnalysis(forceAI: false)
                    }
                    .onChange(of: inputText) { _, newValue in
                        handleTextChange(newValue)
                    }
                }
                .padding(14)
                .background(Color(.systemBackground))
                .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
                .overlay(
                    RoundedRectangle(cornerRadius: 14, style: .continuous)
                        .stroke(Color.primary.opacity(0.08), lineWidth: 0.8)
                )

                if let errorMsg = voiceService.errorMessage, !errorMsg.isEmpty {
                    FormaBanner(
                        message: errorMsg,
                        style: .error,
                        retryTitle: "Tentar novamente",
                        onRetry: !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                            ? { triggerManualAIAnalysis(forceAI: false) }
                            : nil
                    )
                }

                if !inputText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    Button {
                        triggerManualAIAnalysis(forceAI: false)
                    } label: {
                        HStack(spacing: 6) {
                            Image(systemName: "equal.circle.fill")
                                .font(.system(size: 14, weight: .bold))
                            Text("Calcular pela Tabela TACO")
                                .font(.system(size: 13, weight: .bold, design: .rounded))
                        }
                        .foregroundStyle(.white)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 10)
                        .background(FormaTheme.Colors.brand)
                        .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
                    }
                    .buttonStyle(.plain)
                }

                HStack(spacing: 6) {
                    Image(systemName: "checkmark.seal")
                        .font(.system(size: 11))
                        .foregroundStyle(.secondary)
                    Text("Digite os alimentos e porções para calcular calorias e macros na base oficial TACO.")
                        .font(.caption)
                        .foregroundStyle(.secondary)
                }
            }
        }
    }

    private func handleAccountTierChange(_ newTier: AccountTier) {
        textDebounceTask?.cancel()
        if newTier == .pro {
            showAIConsentSheet = false
            pendingAIConsentAction = nil
            voiceService.errorMessage = nil

            let trimmed = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
            if aiConsent.hasGrantedConsent {
                if let imgData = selectedImageData {
                    voiceService.analyzePhoto(imageData: imgData, hintText: inputText)
                } else if !trimmed.isEmpty {
                    voiceService.analyzeIntent(from: trimmed, forceAI: true, allowCloudAI: true)
                } else if !showCameraSheet && !isFieldFocused {
                    startVoiceFlow()
                }
            } else if !trimmed.isEmpty {
                voiceService.analyzeIntent(from: trimmed, forceAI: false, allowCloudAI: false)
            }
        } else {
            isFieldFocused = false
            showCameraSheet = false
            showAIConsentSheet = false
            pendingAIConsentAction = nil
            selectedImage = nil
            selectedImageData = nil
            selectedPhotoPickerItem = nil
            voiceService.cutAllAIFlowsForFreeTier()

            let trimmed = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty {
                voiceService.analyzeIntent(from: trimmed, forceAI: false, allowCloudAI: false)
            }
        }
    }

    private func runWithAIConsent(_ action: @escaping () -> Void) {
        guard authManager.isPro else { return }
        guard aiConsent.hasGrantedConsent else {
            pendingAIConsentAction = action
            showAIConsentSheet = true
            return
        }
        action()
    }

    private func triggerManualAIAnalysis(forceAI: Bool) {
        textDebounceTask?.cancel()
        isFieldFocused = false
        voiceService.stopListening(shouldAnalyze: false)
        UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil)

        if !authManager.isPro {
            let trimmed = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
            guard !trimmed.isEmpty else { return }
            voiceService.analyzeIntent(from: trimmed, forceAI: false, allowCloudAI: false)
            return
        }

        runWithAIConsent {
            if let imgData = selectedImageData {
                voiceService.analyzePhoto(imageData: imgData, hintText: inputText)
            } else {
                let trimmed = inputText.trimmingCharacters(in: .whitespacesAndNewlines)
                let fallback = voiceService.currentIntent?.rawText ?? ""
                let query = !trimmed.isEmpty ? trimmed : fallback
                guard !query.isEmpty else { return }
                voiceService.analyzeIntent(from: query, forceAI: true, allowCloudAI: true)
            }
        }
    }

    private func handlePickedUIImage(_ image: UIImage) {
        guard authManager.isPro else {
            clearSelectedPhoto()
            return
        }
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
            voiceService.analyzeIntent(
                from: trimmed,
                forceAI: authManager.isPro,
                allowCloudAI: authManager.isPro
            )
        }
    }

    private func handleTextChange(_ newText: String) {
        textDebounceTask?.cancel()
        guard selectedImageData == nil else { return }

        let trimmed = newText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            if !voiceService.isRecording {
                voiceService.currentIntent = nil
            }
            return
        }

        guard !voiceService.isRecording else { return }

        if authManager.isPro && aiConsent.hasGrantedConsent {
            textDebounceTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 850_000_000)
                guard !Task.isCancelled, authManager.isPro, aiConsent.hasGrantedConsent, !voiceService.isRecording else { return }
                guard !voiceService.isIncompleteFragment(trimmed),
                      !voiceService.hasDanglingConnectorOrNumber(trimmed) else { return }
                voiceService.analyzeIntent(from: trimmed, forceAI: true, allowCloudAI: true)
            }
        } else {
            textDebounceTask = Task { @MainActor in
                try? await Task.sleep(nanoseconds: 400_000_000)
                guard !Task.isCancelled else { return }
                voiceService.analyzeIntent(from: trimmed, forceAI: false, allowCloudAI: false)
            }
        }
    }

    private func startVoiceFlow() {
        guard authManager.isPro else { return }
        runWithAIConsent {
            Task {
                let authorized = await voiceService.requestPermissions()
                if authorized && authManager.isPro && aiConsent.hasGrantedConsent && selectedImage == nil && !showCameraSheet {
                    voiceService.startListening()
                }
            }
        }
    }

    private func toggleRecording() {
        textDebounceTask?.cancel()
        guard authManager.isPro else {
            voiceService.cutAllAIFlowsForFreeTier()
            return
        }
        if voiceService.isRecording {
            voiceService.stopListening(shouldAnalyze: selectedImageData == nil)
            if let imgData = selectedImageData {
                runWithAIConsent {
                    voiceService.analyzePhoto(imageData: imgData, hintText: inputText)
                }
            }
        } else {
            isFieldFocused = false
            runWithAIConsent {
                voiceService.startListening()
            }
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

private struct VoicePlateItemPortionSheet: View {
    @Environment(\.dismiss) private var dismiss

    let item: VoicePlateItem
    let onApply: (Double) -> Void
    let onRemove: () -> Void

    @State private var portionValue: Double
    @State private var portionText: String
    @FocusState private var isPortionFocused: Bool

    init(
        item: VoicePlateItem,
        onApply: @escaping (Double) -> Void,
        onRemove: @escaping () -> Void
    ) {
        self.item = item
        self.onApply = onApply
        self.onRemove = onRemove
        let initial = max(1.0, item.portionValue)
        _portionValue = State(initialValue: initial)
        _portionText = State(initialValue: "\(Int(initial.rounded()))")
    }

    private var unitLabel: String {
        (item.isLiquid || item.unit == "ml") ? "ml" : "g"
    }

    private var stepDelta: Double {
        (item.isLiquid || item.unit == "ml") ? 50 : 10
    }

    private var presets: [Int] {
        (item.isLiquid || item.unit == "ml") ? [100, 200, 250, 350, 500] : [50, 100, 150, 200, 300]
    }

    private var previewCalories: Double { item.baseCaloriesPer100 * (portionValue / 100.0) }
    private var previewProtein: Double { item.baseProteinPer100 * (portionValue / 100.0) }
    private var previewCarbs: Double { item.baseCarbsPer100 * (portionValue / 100.0) }
    private var previewFat: Double { item.baseFatPer100 * (portionValue / 100.0) }

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 18) {
                    FormaCard(padding: 16, cornerRadius: 18) {
                        VStack(alignment: .leading, spacing: 10) {
                            Text(item.name)
                                .font(.system(size: 17, weight: .bold, design: .rounded))
                                .foregroundStyle(FormaTheme.Colors.primaryText)

                            if !item.brand.isEmpty || !item.source.isEmpty {
                                Text(item.brand.isEmpty ? item.source : item.brand)
                                    .font(.caption)
                                    .foregroundStyle(.secondary)
                            }

                            HStack(spacing: 8) {
                                CleanMacroBadge(
                                    label: "Calorias",
                                    value: "\(Int(previewCalories.rounded())) kcal",
                                    color: FormaTheme.Colors.brand
                                )
                                CleanMacroBadge(
                                    label: "Proteína",
                                    value: "\(Int(previewProtein.rounded()))g",
                                    color: FormaTheme.Colors.protein
                                )
                                CleanMacroBadge(
                                    label: "Carbo",
                                    value: "\(Int(previewCarbs.rounded()))g",
                                    color: FormaTheme.Colors.carbs
                                )
                                CleanMacroBadge(
                                    label: "Gordura",
                                    value: "\(Int(previewFat.rounded()))g",
                                    color: FormaTheme.Colors.fat
                                )
                            }
                        }
                    }

                    VStack(alignment: .leading, spacing: 10) {
                        Text("Quantidade (\(unitLabel))")
                            .font(.subheadline.weight(.semibold))
                            .foregroundStyle(FormaTheme.Colors.primaryText)

                        HStack(spacing: 10) {
                            stepButton(delta: -stepDelta, label: "-\(Int(stepDelta))")

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
                            .overlay(
                                RoundedRectangle(cornerRadius: 12, style: .continuous)
                                    .stroke(
                                        isPortionFocused ? FormaTheme.Colors.brand : Color.clear,
                                        lineWidth: 1.4
                                    )
                            )

                            stepButton(delta: stepDelta, label: "+\(Int(stepDelta))")
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

                    VStack(spacing: 10) {
                        Button {
                            onApply(portionValue)
                            dismiss()
                        } label: {
                            Text("Aplicar Quantidade")
                                .font(.system(size: 15, weight: .bold, design: .rounded))
                                .foregroundStyle(.white)
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 13)
                                .background(FormaTheme.Colors.brandGradient)
                                .clipShape(Capsule())
                        }
                        .buttonStyle(.plain)

                        Button(role: .destructive) {
                            onRemove()
                            dismiss()
                        } label: {
                            HStack(spacing: 6) {
                                Image(systemName: "trash")
                                    .font(.system(size: 13, weight: .semibold))
                                Text("Remover do Prato")
                                    .font(.subheadline.weight(.semibold))
                            }
                            .foregroundStyle(FormaTheme.Colors.danger)
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 10)
                        }
                        .buttonStyle(.plain)
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
            .navigationTitle("Ajustar Quantidade")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { dismiss() }
                }
            }
        }
        .presentationDetents([.height(410), .medium])
        .presentationDragIndicator(.visible)
    }

    private func stepButton(delta: Double, label: String) -> some View {
        Button {
            applyPortion(portionValue + delta)
        } label: {
            Text(label)
                .font(.system(size: 14, weight: .bold, design: .rounded))
                .foregroundStyle(FormaTheme.Colors.brand)
                .padding(.horizontal, 14)
                .padding(.vertical, 11)
                .background(FormaTheme.Colors.brandSoft)
                .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))
        }
        .buttonStyle(.plain)
    }

    private func applyPortion(_ newValue: Double) {
        let minStep = (item.isLiquid || item.unit == "ml") ? 25.0 : 5.0
        let clamped = max(minStep, min(5000, newValue))
        portionValue = clamped
        portionText = "\(Int(clamped.rounded()))"
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}

#Preview {
    VoiceAddFoodView()
        .modelContainer(PreviewContainer.shared)
}

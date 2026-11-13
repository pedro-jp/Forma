//
//  VoiceRecognitionService.swift
//  Forma
//
//  Created by João Pedro on 10/29/26.
//

import Foundation
import Speech
import AVFoundation
import Combine
import SwiftUI

/// Item individual do prato de voz
public struct VoicePlateItem: Identifiable, Sendable, Equatable {
    public var id = UUID()
    public var name: String
    public var brand: String
    public var portionValue: Double
    public var unit: String
    public var isLiquid: Bool
    public var calories: Double
    public var protein: Double
    public var carbs: Double
    public var fat: Double
    public var source: String
    
    public var baseCaloriesPer100: Double
    public var baseProteinPer100: Double
    public var baseCarbsPer100: Double
    public var baseFatPer100: Double
    
    public init(
        id: UUID = UUID(),
        name: String,
        brand: String = "",
        portionValue: Double,
        unit: String = "g",
        isLiquid: Bool = false,
        calories: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        source: String = "TACO Brasil"
    ) {
        self.id = id
        self.name = name
        self.brand = brand
        let p = max(1.0, portionValue)
        self.portionValue = p
        self.unit = unit
        self.isLiquid = isLiquid
        self.calories = calories
        self.protein = protein
        self.carbs = carbs
        self.fat = fat
        self.source = source
        
        let factor = p / 100.0
        self.baseCaloriesPer100 = calories / factor
        self.baseProteinPer100 = protein / factor
        self.baseCarbsPer100 = carbs / factor
        self.baseFatPer100 = fat / factor
    }
    
    public mutating func updatePortion(_ newPortion: Double) {
        let stepMin = isLiquid ? 25.0 : 5.0
        let clamped = max(stepMin, newPortion)
        self.portionValue = clamped
        let factor = clamped / 100.0
        self.calories = ((baseCaloriesPer100 * factor) * 10).rounded() / 10
        self.protein = ((baseProteinPer100 * factor) * 10).rounded() / 10
        self.carbs = ((baseCarbsPer100 * factor) * 10).rounded() / 10
        self.fat = ((baseFatPer100 * factor) * 10).rounded() / 10
    }
}

/// Estrutura com os dados extraídos de uma fala em linguagem natural (suporta pratos com múltiplos itens)
public struct VoiceFoodIntent: Sendable, Equatable {
    public var rawText: String
    public var mealType: MealType
    public var items: [VoicePlateItem]
    public var aiSummary: String = ""
    
    public var totalCalories: Double {
        items.reduce(0.0) { $0 + $1.calories }
    }
    public var totalProtein: Double {
        items.reduce(0.0) { $0 + $1.protein }
    }
    public var totalCarbs: Double {
        items.reduce(0.0) { $0 + $1.carbs }
    }
    public var totalFat: Double {
        items.reduce(0.0) { $0 + $1.fat }
    }
    
    // Propriedades para compatibilidade com código existente
    public var foodQuery: String {
        items.map { $0.name }.joined(separator: ", ")
    }
    public var portionGrams: Double {
        items.first?.portionValue ?? 100.0
    }
    public var matchedFood: NutritionalFoodItem? {
        guard let first = items.first else { return nil }
        return NutritionalFoodItem(
            name: first.name,
            brand: first.brand,
            caloriesPer100g: first.baseCaloriesPer100,
            proteinPer100g: first.baseProteinPer100,
            carbsPer100g: first.baseCarbsPer100,
            fatPer100g: first.baseFatPer100
        )
    }
    public var calculatedCalories: Double { totalCalories }
    public var calculatedProtein: Double { totalProtein }
    public var calculatedCarbs: Double { totalCarbs }
    public var calculatedFat: Double { totalFat }
    
    public init(
        rawText: String,
        mealType: MealType,
        items: [VoicePlateItem] = [],
        aiSummary: String = ""
    ) {
        self.rawText = rawText
        self.mealType = mealType
        self.items = items
        self.aiSummary = aiSummary
    }
    
    public init(
        rawText: String,
        foodQuery: String,
        portionGrams: Double,
        mealType: MealType,
        matchedFood: NutritionalFoodItem? = nil,
        calculatedCalories: Double = 0,
        calculatedProtein: Double = 0,
        calculatedCarbs: Double = 0,
        calculatedFat: Double = 0
    ) {
        self.rawText = rawText
        self.mealType = mealType
        if let food = matchedFood {
            self.items = [
                VoicePlateItem(
                    name: food.name,
                    brand: food.brand,
                    portionValue: portionGrams,
                    unit: food.defaultUnit,
                    isLiquid: food.isLiquid,
                    calories: calculatedCalories,
                    protein: calculatedProtein,
                    carbs: calculatedCarbs,
                    fat: calculatedFat
                )
            ]
        } else {
            self.items = []
        }
    }
}

/// Fases de carregamento da análise nutricional com transição suave
public enum AnalysisPhase: String, Sendable, Equatable {
    case idle
    case tacoDatabase = "Base Forma"
    case artificialIntelligence = "Inteligência Forma"
    case photoVision = "Visão Forma"
}

/// Modo de escuta por voz
public enum VoiceListeningMode: Sendable {
    case food
    case workout
}

/// Estado das permissões de voz e microfone
public enum VoicePermissionStatus: Sendable {
    case notDetermined
    case authorized
    case speechDenied
    case microphoneDenied
    case restricted
}

/// Serviço de reconhecimento de voz e análise semântica de alimentos
@MainActor
public final class VoiceRecognitionService: NSObject, ObservableObject {
    public static let shared = VoiceRecognitionService()
    
    @Published public var isRecording: Bool = false
    @Published public var recognizedText: String = ""
    @Published public var audioLevel: Float = 0.0 // 0.0 a 1.0 para visualizador de ondas
    @Published public var audioSpectrum: [Float] = Array(repeating: 0.06, count: 28) // 28 barras reativas à voz em tempo real
    @Published public var permissionStatus: VoicePermissionStatus = .notDetermined
    @Published public var listeningMode: VoiceListeningMode = .food
    @Published public var currentIntent: VoiceFoodIntent? = nil
    @Published public var alternativeMatches: [NutritionalFoodItem] = []
    @Published public var isAnalyzing: Bool = false
    @Published public var analysisPhase: AnalysisPhase = .idle
    @Published public var errorMessage: String? = nil
    @Published public var selectedMealType: MealType = .lunch
    
    private var audioEngine: AVAudioEngine?
    private var speechRecognizer: SFSpeechRecognizer?
    private var recognitionRequest: SFSpeechAudioBufferRecognitionRequest?
    private var recognitionTask: SFSpeechRecognitionTask?
    
    private var silenceDetectionTask: Task<Void, Never>?
    private var parseDebounceTask: Task<Void, Never>?
    private var activeAnalysisTask: Task<Void, Never>?
    private var currentAnalysisId: Int = 0
    private var recordingSessionId: Int = 0
    private var lastAnalyzedSignature: String = ""
    private var lastSpokenTime: Date = Date()
    private var preferredMealType: MealType? = nil
    
    override public init() {
        super.init()
        let ptLocale = Locale(identifier: "pt-BR")
        if SFSpeechRecognizer.supportedLocales().contains(ptLocale) {
            self.speechRecognizer = SFSpeechRecognizer(locale: ptLocale)
        } else {
            self.speechRecognizer = SFSpeechRecognizer()
        }
    }
    
    // MARK: - Permissões
    
    public func checkPermissions() {
        let speechAuth = SFSpeechRecognizer.authorizationStatus()
        let micAuth = AVAudioApplication.shared.recordPermission
        
        switch (speechAuth, micAuth) {
        case (.authorized, .granted):
            permissionStatus = .authorized
        case (.denied, _):
            permissionStatus = .speechDenied
        case (_, .denied):
            permissionStatus = .microphoneDenied
        case (.restricted, _):
            permissionStatus = .restricted
        default:
            permissionStatus = .notDetermined
        }
    }
    
    public func requestPermissions() async -> Bool {
        let speechStatus = await withCheckedContinuation { continuation in
            SFSpeechRecognizer.requestAuthorization { status in
                continuation.resume(returning: status)
            }
        }
        
        guard speechStatus == .authorized else {
            if speechStatus == .denied {
                permissionStatus = .speechDenied
            } else {
                permissionStatus = .restricted
            }
            return false
        }
        
        let micGranted = await withCheckedContinuation { continuation in
            AVAudioApplication.requestRecordPermission { granted in
                continuation.resume(returning: granted)
            }
        }
        
        if micGranted {
            permissionStatus = .authorized
            return true
        } else {
            permissionStatus = .microphoneDenied
            return false
        }
    }
    
    // MARK: - Gravação e Reconhecimento
    
    public func startListening(mode: VoiceListeningMode = .food) {
        stopListening(shouldAnalyze: false)
        self.listeningMode = mode
        self.recordingSessionId += 1
        let sessionId = self.recordingSessionId
        
        errorMessage = nil
        recognizedText = ""
        currentIntent = nil
        alternativeMatches = []
        lastAnalyzedSignature = ""
        audioLevel = 0.0
        audioSpectrum = Array(repeating: 0.06, count: 28)
        
        let audioSession = AVAudioSession.sharedInstance()
        do {
            try audioSession.setCategory(.playAndRecord, mode: .measurement, options: [.duckOthers, .defaultToSpeaker])
            try audioSession.setActive(true, options: .notifyOthersOnDeactivation)
        } catch {
            errorMessage = "Não foi possível iniciar o microfone: \(error.localizedDescription)"
            return
        }
        
        let engine = AVAudioEngine()
        self.audioEngine = engine
        
        let request = SFSpeechAudioBufferRecognitionRequest()
        request.shouldReportPartialResults = true
        request.taskHint = .dictation
        self.recognitionRequest = request
        
        let inputNode = engine.inputNode
        let recordingFormat = inputNode.outputFormat(forBus: 0)
        
        Self.installRealtimeAudioTap(
            on: inputNode,
            format: recordingFormat,
            request: request
        ) { [weak self] targetLevel, spectrum in
            Task { @MainActor [weak self] in
                guard let self = self, self.isRecording else { return }
                self.audioLevel = targetLevel > self.audioLevel
                    ? (self.audioLevel * 0.20 + targetLevel * 0.80)
                    : (self.audioLevel * 0.55 + targetLevel * 0.45)
                if self.audioSpectrum.count == spectrum.count {
                    for idx in 0..<spectrum.count {
                        let prev = self.audioSpectrum[idx]
                        let target = spectrum[idx]
                        // Ataque rápido ao falar (~30pt na voz normal) e decaimento suave no silêncio
                        self.audioSpectrum[idx] = target > prev
                            ? (prev * 0.18 + target * 0.82)
                            : (prev * 0.58 + target * 0.42)
                    }
                } else {
                    self.audioSpectrum = spectrum
                }
            }
        }
        
        do {
            try engine.start()
            isRecording = true
            lastSpokenTime = Date()
        } catch {
            errorMessage = "Erro ao iniciar captura: \(error.localizedDescription)"
            stopListening(shouldAnalyze: false)
            return
        }
        
        recognitionTask = speechRecognizer?.recognitionTask(with: request) { [weak self] result, error in
            guard let self = self else { return }
            
            if let result = result {
                let transcription = result.bestTranscription.formattedString
                let isFinal = result.isFinal
                Task { @MainActor in
                    // Ignora callbacks atrasados de sessões já encerradas
                    guard self.recordingSessionId == sessionId, self.isRecording else { return }
                    
                    let previousText = self.recognizedText
                    self.recognizedText = transcription
                    if transcription != previousText {
                        self.lastSpokenTime = Date()
                    }
                    
                    if self.listeningMode == .food {
                        if isFinal && !transcription.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                            self.stopListening(shouldAnalyze: true)
                        } else {
                            self.scheduleSilenceDetection(for: transcription, sessionId: sessionId)
                        }
                    }
                }
                return
            }
            
            if error != nil {
                Task { @MainActor in
                    guard self.recordingSessionId == sessionId, self.isRecording else { return }
                    let hasText = !self.recognizedText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                    self.stopListening(shouldAnalyze: self.listeningMode == .food && hasText)
                }
            }
        }
    }
    
    public func stopListening(shouldAnalyze: Bool = true) {
        let wasRecording = isRecording
        recordingSessionId += 1
        
        silenceDetectionTask?.cancel()
        silenceDetectionTask = nil
        
        parseDebounceTask?.cancel()
        parseDebounceTask = nil
        
        audioEngine?.stop()
        audioEngine?.inputNode.removeTap(onBus: 0)
        audioEngine = nil
        
        recognitionRequest?.endAudio()
        recognitionRequest = nil
        
        recognitionTask?.cancel()
        recognitionTask = nil
        
        isRecording = false
        audioLevel = 0.0
        audioSpectrum = Array(repeating: 0.06, count: 28)
        
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
        
        if shouldAnalyze && wasRecording && listeningMode == .food && AuthManager.shared.isPro {
            let trimmed = recognizedText.trimmingCharacters(in: .whitespacesAndNewlines)
            if !trimmed.isEmpty && !isIncompleteFragment(trimmed) {
                analyzeIntent(from: trimmed, forceAI: true, allowCloudAI: true)
            }
        }
    }

    public func cutAllAIFlowsForFreeTier() {
        stopListening(shouldAnalyze: false)
        activeAnalysisTask?.cancel()
        activeAnalysisTask = nil
        parseDebounceTask?.cancel()
        parseDebounceTask = nil
        silenceDetectionTask?.cancel()
        silenceDetectionTask = nil
        currentAnalysisId += 1
        isAnalyzing = false
        analysisPhase = .idle
        errorMessage = nil
        lastAnalyzedSignature = ""

        if let existing = currentIntent {
            let hasPhotoItems = existing.items.contains { $0.source.lowercased().contains("foto") }
            let raw = existing.rawText.trimmingCharacters(in: .whitespacesAndNewlines)
            currentIntent = nil
            if !hasPhotoItems && !raw.isEmpty && !isIncompleteFragment(raw) {
                analyzeIntent(from: raw, forceAI: false, allowCloudAI: false)
            }
        }
    }
    
    // MARK: - Análise e Detecção de Término de Fala
    
    private func scheduleSilenceDetection(for currentText: String, sessionId: Int) {
        silenceDetectionTask?.cancel()
        let trimmed = currentText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        
        // Se a frase terminar em conector/número pendente (ex: "Cento e", "Dois pães de 76g cada com", "e 150"),
        // dá mais tempo para o usuário concluir o raciocínio antes de encerrar o microfone.
        let waitNs: UInt64 = hasDanglingConnectorOrNumber(trimmed) ? 4_000_000_000 : 2_500_000_000
        
        silenceDetectionTask = Task { @MainActor [weak self] in
            try? await Task.sleep(nanoseconds: waitNs)
            guard !Task.isCancelled,
                  let self = self,
                  self.isRecording,
                  self.recordingSessionId == sessionId else { return }
            self.stopListening(shouldAnalyze: true)
        }
    }
    
    /// Detecta se a fala atual parou no meio de uma preposição, conjunção ou numeral incompleto
    public func hasDanglingConnectorOrNumber(_ text: String) -> Bool {
        let words = text
            .lowercased()
            .components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
        guard let last = words.last else { return true }
        
        let danglingWords: Set<String> = [
            "e", "com", "de", "da", "do", "dos", "das", "no", "na", "nos", "nas",
            "um", "uma", "dois", "duas", "três", "tres", "quatro", "cinco",
            "meio", "meia", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos",
            "mais", "para", "pro", "pra", "cada"
        ]
        if danglingWords.contains(last) {
            return true
        }
        // Terminou apenas com um número solto após conector (ex: "... e 150", "... com 35")
        if Double(last.replacingOccurrences(of: ",", with: ".")) != nil {
            return true
        }
        return false
    }
    
    /// Evita enviar fragmentos curtos sem nenhum alimento real (ex: "Cento e", "Dois", "150 g de") para a IA
    public func isIncompleteFragment(_ text: String) -> Bool {
        let extracted = extractFoodQuery(from: text)
        if extracted.isEmpty || isPureMeasureQuery(extracted) {
            return true
        }
        let meaningfulWords = extracted
            .lowercased()
            .components(separatedBy: .whitespacesAndNewlines)
            .filter { !$0.isEmpty }
        let numberWords: Set<String> = [
            "cento", "cem", "duzentos", "trezentos", "quatrocentos", "quinhentos",
            "um", "uma", "dois", "duas", "três", "tres", "quatro", "cinco", "seis",
            "sete", "oito", "nove", "dez", "vinte", "trinta", "quarenta", "cinquenta",
            "sessenta", "setenta", "oitenta", "noventa"
        ]
        let nonNumericWords = meaningfulWords.filter { !numberWords.contains($0) && Double($0) == nil }
        return nonNumericWords.isEmpty
    }
    
    public func analyzeIntent(from spokenText: String, forceAI: Bool = false, allowCloudAI: Bool = true) {
        let trimmed = spokenText.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }

        let isProActive = AuthManager.shared.isPro
        let effectiveCloudAI = isProActive && (allowCloudAI || forceAI)
        let effectiveForceAI = isProActive && (forceAI || allowCloudAI)
        
        let cleanQuery = extractFoodQuery(from: trimmed)
        
        // Se a fala contém apenas quantidade/medida (ex: "100g", "200 gramas"):
        if (cleanQuery.isEmpty || isPureMeasureQuery(cleanQuery)), let first = currentIntent?.items.first {
            let portion = extractPortionGrams(from: trimmed)
            updateItemPortion(id: first.id, newPortion: portion)
            return
        }
        
        // Se não há alimento válido (apenas numeral como "Cento e"), nunca dispara análise
        if isIncompleteFragment(trimmed) {
            return
        }
        
        // Deduplicação contra chamadas idênticas consecutivas
        let signature = "\(trimmed.lowercased())|force:\(effectiveForceAI)|cloud:\(effectiveCloudAI)"
        if signature == lastAnalyzedSignature && (isAnalyzing || currentIntent?.rawText.lowercased() == trimmed.lowercased()) {
            return
        }
        lastAnalyzedSignature = signature
        
        let preservedMealType = currentIntent?.mealType
        currentAnalysisId += 1
        let analysisId = currentAnalysisId
        activeAnalysisTask?.cancel()
        errorMessage = nil
        
        if effectiveCloudAI || effectiveForceAI {
            isAnalyzing = true
            analysisPhase = .artificialIntelligence
        }
        
        activeAnalysisTask = Task { [weak self] in
            let parseResult = await AIFoodParserService.shared.parseMeal(
                description: trimmed,
                forceAI: effectiveForceAI,
                allowCloudAI: effectiveCloudAI,
                onPhaseChange: { [weak self] newPhase in
                    Task { @MainActor [weak self] in
                        guard let strongSelf = self, strongSelf.currentAnalysisId == analysisId else { return }
                        if effectiveCloudAI || effectiveForceAI {
                            strongSelf.analysisPhase = newPhase
                        }
                    }
                }
            )
            
            guard !Task.isCancelled else { return }
            
            await MainActor.run { [weak self] in
                guard let self, self.currentAnalysisId == analysisId else { return }
                if (effectiveCloudAI || effectiveForceAI) && !AuthManager.shared.isPro {
                    self.isAnalyzing = false
                    self.analysisPhase = .idle
                    return
                }
                self.isAnalyzing = false
                self.analysisPhase = .idle
                
                guard !parseResult.items.isEmpty else { return }
                
                let plateItems = parseResult.items.map { item in
                    VoicePlateItem(
                        name: item.matchedFoodName,
                        brand: item.brand,
                        portionValue: item.portionValue,
                        unit: item.unit,
                        isLiquid: item.isLiquid,
                        calories: item.calories,
                        protein: item.protein,
                        carbs: item.carbohydrates,
                        fat: item.fat,
                        source: item.source
                    )
                }
                
                self.currentIntent = VoiceFoodIntent(
                    rawText: trimmed,
                    mealType: preservedMealType ?? self.preferredMealType ?? parseResult.mealType,
                    items: plateItems,
                    aiSummary: (effectiveCloudAI || effectiveForceAI) ? parseResult.aiSummary : ""
                )
            }
        }
    }
    
    /// Analisa foto da refeição via Gemini Vision e preenche os itens ajustáveis do prato
    public func analyzePhoto(imageData: Data, hintText: String = "") {
        guard AuthManager.shared.isPro else {
            cutAllAIFlowsForFreeTier()
            return
        }
        stopListening(shouldAnalyze: false)
        activeAnalysisTask?.cancel()
        parseDebounceTask?.cancel()
        
        let preservedMealType = currentIntent?.mealType ?? preferredMealType
        currentAnalysisId += 1
        let analysisId = currentAnalysisId
        lastAnalyzedSignature = ""
        errorMessage = nil
        isAnalyzing = true
        analysisPhase = .photoVision
        
        activeAnalysisTask = Task { [weak self] in
            guard let self = self else { return }
            do {
                let extracted = try await GeminiAIService.shared.analyzeMealImage(
                    imageData: imageData,
                    mimeType: "image/jpeg",
                    additionalContext: hintText
                )
                guard !Task.isCancelled else { return }
                
                await MainActor.run {
                    guard self.currentAnalysisId == analysisId, AuthManager.shared.isPro else {
                        self.isAnalyzing = false
                        self.analysisPhase = .idle
                        return
                    }
                    self.isAnalyzing = false
                    self.analysisPhase = .idle
                    
                    guard !extracted.items.isEmpty else {
                        self.errorMessage = "Não foi possível identificar alimentos claros nesta imagem. Tente aproximar a foto ou adicionar uma observação."
                        return
                    }
                    
                    let plateItems = extracted.items.map { item in
                        VoicePlateItem(
                            name: item.name,
                            brand: "",
                            portionValue: item.portionValue,
                            unit: item.unit,
                            isLiquid: item.isLiquid,
                            calories: item.calories,
                            protein: item.protein,
                            carbs: item.carbohydrates,
                            fat: item.fat,
                            source: "Forma Foto"
                        )
                    }
                    
                    let resolvedMeal = preservedMealType ?? self.extractMealType(from: extracted.mealType)
                    let labelText = hintText.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
                        ? plateItems.map { $0.name }.joined(separator: ", ")
                        : hintText.trimmingCharacters(in: .whitespacesAndNewlines)
                    
                    self.currentIntent = VoiceFoodIntent(
                        rawText: labelText,
                        mealType: resolvedMeal,
                        items: plateItems,
                        aiSummary: extracted.aiSummary
                    )
                }
            } catch {
                guard !Task.isCancelled else { return }
                await MainActor.run {
                    guard self.currentAnalysisId == analysisId else { return }
                    self.isAnalyzing = false
                    self.analysisPhase = .idle
                    self.errorMessage = error.localizedDescription
                }
            }
        }
    }
    
    public func updateItemPortion(id: UUID, newPortion: Double) {
        guard var intent = currentIntent else { return }
        if let idx = intent.items.firstIndex(where: { $0.id == id }) {
            intent.items[idx].updatePortion(newPortion)
            self.currentIntent = intent
        }
    }
    
    public func adjustItemPortion(id: UUID, delta: Double) {
        guard var intent = currentIntent else { return }
        if let idx = intent.items.firstIndex(where: { $0.id == id }) {
            let current = intent.items[idx].portionValue
            intent.items[idx].updatePortion(current + delta)
            self.currentIntent = intent
        }
    }
    
    public func removeItem(id: UUID) {
        guard var intent = currentIntent else { return }
        intent.items.removeAll { $0.id == id }
        self.currentIntent = intent
    }
    
    public func updatePortion(_ grams: Double) {
        if let first = currentIntent?.items.first {
            updateItemPortion(id: first.id, newPortion: grams)
        }
    }
    
    public func selectAlternativeMatch(_ item: NutritionalFoodItem) {
        guard var intent = currentIntent else { return }
        let values = item.values(for: intent.portionGrams)
        if !intent.items.isEmpty {
            intent.items[0] = VoicePlateItem(
                name: item.name,
                brand: item.brand,
                portionValue: intent.portionGrams,
                unit: item.defaultUnit,
                isLiquid: item.isLiquid,
                calories: values.calories,
                protein: values.protein,
                carbs: values.carbs,
                fat: values.fat
            )
            self.currentIntent = intent
        }
    }
    
    public func updateMealType(_ meal: MealType) {
        preferredMealType = meal
        selectedMealType = meal
        currentIntent?.mealType = meal
    }

    public func addSuggestedPlateItem(_ item: VoicePlateItem, mealType: MealType) {
        stopListening(shouldAnalyze: false)
        activeAnalysisTask?.cancel()
        parseDebounceTask?.cancel()
        preferredMealType = mealType
        selectedMealType = mealType

        if var existing = currentIntent, !existing.items.isEmpty {
            existing.mealType = mealType
            if let matchIdx = existing.items.firstIndex(where: { $0.name.caseInsensitiveCompare(item.name) == .orderedSame }) {
                let currentPortion = existing.items[matchIdx].portionValue
                existing.items[matchIdx].updatePortion(currentPortion + item.portionValue)
            } else {
                existing.items.append(item)
            }
            existing.rawText = existing.items.map(\.name).joined(separator: ", ")
            self.currentIntent = existing
        } else {
            self.currentIntent = VoiceFoodIntent(
                rawText: item.name,
                mealType: mealType,
                items: [item]
            )
        }
    }
    
    // MARK: - Algoritmos de Extração Linguística em Português
    
    private func extractMealType(from text: String) -> MealType {
        let lower = text.lowercased()
        
        if lower.contains("café da manhã") || lower.contains("cafe da manha") ||
            lower.contains("desjejum") || lower.contains("no café") || lower.contains("no cafe") {
            return .breakfast
        }
        
        if lower.contains("almoço") || lower.contains("almoco") ||
            lower.contains("almocei") || lower.contains("no almoço") || lower.contains("no almoco") {
            return .lunch
        }
        
        if lower.contains("jantar") || lower.contains("janta") ||
            lower.contains("jantei") || lower.contains("no jantar") || lower.contains("na janta") {
            return .dinner
        }
        
        if lower.contains("lanche") || lower.contains("lanchei") ||
            lower.contains("merenda") || lower.contains("ceia") || lower.contains("da tarde") {
            return .snacks
        }
        
        let hour = Calendar.current.component(.hour, from: Date())
        switch hour {
        case 5..<11:
            return .breakfast
        case 11..<15:
            return .lunch
        case 15..<19:
            return .snacks
        default:
            return .dinner
        }
    }
    
    private func extractPortionGrams(from text: String) -> Double {
        let lower = text.lowercased()
        
        let gramPattern = #"(\d+(?:[.,]\d+)?)\s*(?:gramas?|g\b|gr\b)"#
        if let regex = try? NSRegularExpression(pattern: gramPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)) {
            if let range = Range(match.range(at: 1), in: lower) {
                let numStr = String(lower[range]).replacingOccurrences(of: ",", with: ".")
                if let grams = Double(numStr), grams > 0 {
                    return grams
                }
            }
        }
        
        let colherPattern = #"(\d+|uma?|duas?|três|tres)\s*colher(?:es)?(?:\s*de\s*sopa)?"#
        if let regex = try? NSRegularExpression(pattern: colherPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)) {
            if let range = Range(match.range(at: 1), in: lower) {
                let word = String(lower[range])
                let count = parseQuantityWord(word)
                return count * 15.0
            }
        }
        
        let fatiaPattern = #"(\d+|uma?|duas?|três|tres)\s*fatia[s]?"#
        if let regex = try? NSRegularExpression(pattern: fatiaPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)) {
            if let range = Range(match.range(at: 1), in: lower) {
                let word = String(lower[range])
                let count = parseQuantityWord(word)
                return count * 35.0
            }
        }
        
        let ovoPattern = #"(\d+|um|dois|três|tres|quatro)\s*ovo[s]?"#
        if let regex = try? NSRegularExpression(pattern: ovoPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)) {
            if let range = Range(match.range(at: 1), in: lower) {
                let word = String(lower[range])
                let count = parseQuantityWord(word)
                return count * 50.0
            }
        }
        
        if lower.contains("banana") {
            let count = parseQuantityWordInContext(lower, around: "banana")
            return count * 90.0
        }
        if lower.contains("maçã") || lower.contains("maca") {
            let count = parseQuantityWordInContext(lower, around: "maç")
            return count * 130.0
        }
        
        let isolatedNumberPattern = #"^(\d+)\s*(?:de\s+)?"#
        if let regex = try? NSRegularExpression(pattern: isolatedNumberPattern, options: []),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)) {
            if let range = Range(match.range(at: 1), in: lower),
               let num = Double(lower[range]), num >= 10 {
                return num
            }
        }
        
        return 100.0
    }
    
    private func parseQuantityWord(_ word: String) -> Double {
        let w = word.trimmingCharacters(in: .whitespaces).lowercased()
        if let direct = Double(w) { return direct }
        switch w {
        case "um", "uma": return 1.0
        case "dois", "duas": return 2.0
        case "três", "tres": return 3.0
        case "quatro": return 4.0
        case "cinco": return 5.0
        default: return 1.0
        }
    }
    
    private func parseQuantityWordInContext(_ text: String, around keyword: String) -> Double {
        let pattern = #"(\d+|uma?|duas?|três|tres)\s*"# + keyword
        if let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: text, options: [], range: NSRange(text.startIndex..., in: text)) {
            if let range = Range(match.range(at: 1), in: text) {
                return parseQuantityWord(String(text[range]))
            }
        }
        return 1.0
    }
    
    private func extractFoodQuery(from text: String) -> String {
        var clean = text.lowercased()
        
        let mealStopWords = [
            "no café da manhã", "no cafe da manha", "para o café da manhã", "para o cafe da manha",
            "no café", "no cafe", "de desjejum", "no almoço", "no almoco", "para o almoço", "para o almoco",
            "no jantar", "na janta", "para o jantar", "para a janta",
            "de lanche", "no lanche", "no lanchinho", "na ceia"
        ]
        for stop in mealStopWords {
            clean = clean.replacingOccurrences(of: stop, with: " ")
        }
        
        let verbStopWords = [
            "eu comi", "comi", "acabei de comer", "comemos", "consumi", "ingeri",
            "eu bebi", "bebi", "tomei", "adicione", "registre", "anote", "coloque",
            "por favor", "marca aí", "marca ai", "marca"
        ]
        for verb in verbStopWords {
            clean = clean.replacingOccurrences(of: verb, with: " ")
        }
        
        let measurePatterns = [
            #"\d+(?:[.,]\d+)?\s*(?:gramas?|g\b|gr\b|ml\b|mililitros?)"#,
            #"(?:\d+|uma?|duas?|três|tres)\s*colher(?:es)?(?:\s*de\s*sopa)?"#,
            #"(?:\d+|uma?|duas?|três|tres)\s*fatia[s]?"#,
            #"(?:\d+|uma?|duas?|três|tres)\s*unidade[s]?"#,
            #"\b\d+\s*de\b"#
        ]
        for pat in measurePatterns {
            if let regex = try? NSRegularExpression(pattern: pat, options: .caseInsensitive) {
                clean = regex.stringByReplacingMatches(in: clean, options: [], range: NSRange(clean.startIndex..., in: clean), withTemplate: " ")
            }
        }
        
        let prepositions = ["de", "da", "do", "com", "e", "um", "uma"]
        var words = clean.components(separatedBy: .whitespacesAndNewlines).filter { !$0.isEmpty }
        
        while let first = words.first, prepositions.contains(first) {
            words.removeFirst()
        }
        while let last = words.last, prepositions.contains(last) {
            words.removeLast()
        }
        
        return words.joined(separator: " ").trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    private func isPureMeasureQuery(_ text: String) -> Bool {
        let cleaned = text
            .replacingOccurrences(of: "gramas", with: "")
            .replacingOccurrences(of: "grama", with: "")
            .replacingOccurrences(of: "gr", with: "")
            .replacingOccurrences(of: "g", with: "")
            .replacingOccurrences(of: "ml", with: "")
            .replacingOccurrences(of: "mls", with: "")
            .replacingOccurrences(of: "de", with: "")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        return Double(cleaned) != nil || cleaned.isEmpty
    }

    nonisolated private static func installRealtimeAudioTap(
        on inputNode: AVAudioInputNode,
        format: AVAudioFormat,
        request: SFSpeechAudioBufferRecognitionRequest,
        onLevelUpdate: @escaping @Sendable (Float, [Float]) -> Void
    ) {
        let barCount = 28
        let tapBlock: AVAudioNodeTapBlock = { buffer, _ in
            request.append(buffer)

            guard let channelData = buffer.floatChannelData?[0] else { return }
            let frameLength = Int(buffer.frameLength)
            guard frameLength > 0 else { return }

            var sum: Float = 0.0
            for i in 0..<frameLength {
                let sample = channelData[i]
                sum += sample * sample
            }
            let rms = sqrt(sum / Float(frameLength))
            // Curva calibrada para o modo .measurement: voz normal conversacional atinge ~0.75-0.90 (~30pt nas barras)
            let normalizedVoice = powf(min(max((rms - 0.0022) * 34.0, 0.0), 1.0), 0.52)
            let level = min(max(normalizedVoice, 0.04), 1.0)

            // Calcula a energia em 28 sub-janelas do buffer com perfil de onda centralizado
            var bands = [Float](repeating: 0.05, count: barCount)
            let chunkSize = max(1, frameLength / barCount)
            for b in 0..<barCount {
                let start = b * chunkSize
                let end = min(frameLength, start + chunkSize)
                var bandSum: Float = 0.0
                if end > start {
                    for j in start..<end {
                        let s = channelData[j]
                        bandSum += s * s
                    }
                    let bandRms = sqrt(bandSum / Float(end - start))
                    let bandNorm = powf(min(max((bandRms - 0.002) * 38.0, 0.0), 1.0), 0.52)
                    // Envelope senoidal suave para valorizar o centro e manter bordas orgânicas
                    let pos = Float(b) / Float(barCount - 1)
                    let envelope = 0.48 + 0.52 * sin(pos * .pi)
                    bands[b] = min(1.0, max(0.05, bandNorm * envelope))
                }
            }
            onLevelUpdate(level, bands)
        }

        if #available(iOS 27.0, *) {
            try? inputNode.__installTap(onBus: 0, bufferSize: 1024, format: format, error: (), block: tapBlock)
        } else {
            inputNode.installTap(onBus: 0, bufferSize: 1024, format: format, block: tapBlock)
        }
    }
}

/// Visualizador de ondas sensíveis à voz em tempo real (28 cápsulas luminosas estilo IA Forma)
@MainActor
public struct FormaVoiceWaveformView: View {
    @ObservedObject var voiceService: VoiceRecognitionService
    private let barCount: Int = 28

    public init(voiceService: VoiceRecognitionService = .shared) {
        self.voiceService = voiceService
    }

    public var body: some View {
        TimelineView(.animation(minimumInterval: 1.0 / 30.0, paused: !voiceService.isRecording)) { timeline in
            let time = voiceService.isRecording ? timeline.date.timeIntervalSinceReferenceDate : 0.0
            let activeLevel = voiceService.isRecording ? CGFloat(voiceService.audioLevel) : 0.0
            let isSpeakingNow = voiceService.isRecording && activeLevel > 0.075

            ZStack {
                // Brilho atmosférico difuso atrás das ondas (violeta em repouso, violeta + verde-água ao falar)
                Ellipse()
                    .fill(
                        RadialGradient(
                            colors: [
                                (isSpeakingNow
                                    ? Color(red: 0.44, green: 0.84, blue: 0.88)
                                    : Color(red: 0.58, green: 0.47, blue: 1.0)
                                ).opacity(isSpeakingNow ? 0.36 : 0.22),
                                Color(red: 0.52, green: 0.40, blue: 0.98).opacity(0.10),
                                Color.clear
                            ],
                            center: .center,
                            startRadius: 4,
                            endRadius: 165
                        )
                    )
                    .frame(height: 44)
                    .blur(radius: 8)

                HStack(alignment: .center, spacing: 5.5) {
                    ForEach(0..<barCount, id: \.self) { index in
                        let barHeight = computeBarHeight(index: index, time: time, activeLevel: activeLevel)
                        let normalized = min(1.0, max(0.0, (barHeight - 5.2) / 30.0))

                        Capsule(style: .continuous)
                            .fill(barGradient(normalized: normalized, isSpeakingNow: isSpeakingNow))
                            .frame(width: 4.8, height: barHeight)
                            .shadow(
                                color: (normalized > 0.14
                                    ? Color(red: 0.38, green: 0.90, blue: 0.82)
                                    : Color(red: 0.62, green: 0.50, blue: 1.0)
                                ).opacity(isSpeakingNow ? 0.55 : 0.30),
                                radius: isSpeakingNow ? 4.5 : 2.5,
                                x: 0,
                                y: 0
                            )
                            .animation(.interpolatingSpring(stiffness: 270, damping: 18), value: barHeight)
                    }
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 42)
            .padding(.vertical, 4)
            .contentShape(Rectangle())
        }
    }

    private func barGradient(normalized: CGFloat, isSpeakingNow: Bool) -> LinearGradient {
        if !isSpeakingNow || normalized <= 0.08 {
            // Estado de silêncio / refeição interpretada: pontos violeta-lavanda luminosos
            return LinearGradient(
                colors: [
                    Color(red: 0.74, green: 0.66, blue: 1.0).opacity(0.88),
                    Color(red: 0.58, green: 0.48, blue: 0.98).opacity(0.82)
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        // Estado ativo falando: topo lilás -> meio ciano -> base verde-água luminoso
        return LinearGradient(
            colors: [
                Color(red: 0.72, green: 0.56, blue: 1.0).opacity(0.88 + 0.12 * normalized),
                Color(red: 0.46, green: 0.79, blue: 0.98).opacity(0.84 + 0.16 * normalized),
                Color(red: 0.36, green: 0.92, blue: 0.80).opacity(0.82 + 0.18 * normalized)
            ],
            startPoint: .top,
            endPoint: .bottom
        )
    }

    private func computeBarHeight(index: Int, time: TimeInterval, activeLevel: CGFloat) -> CGFloat {
        guard voiceService.isRecording else {
            return 5.2
        }

        let bandValue: CGFloat = index < voiceService.audioSpectrum.count
            ? CGFloat(voiceService.audioSpectrum[index])
            : activeLevel

        let pos = Double(index) / Double(max(1, barCount - 1))
        let centerEnvelope = CGFloat(0.42 + 0.58 * sin(pos * .pi))

        // Onda viajante orgânica modulada pela voz real do microfone
        let wave1 = CGFloat(sin(time * 8.5 + Double(index) * 0.55)) * 0.5 + 0.5
        let wave2 = CGFloat(cos(time * 5.2 - Double(index) * 0.35)) * 0.5 + 0.5
        let harmonicModulation = (wave1 * 0.65 + wave2 * 0.35)

        let combinedEnergy = max(bandValue, activeLevel * centerEnvelope)

        if combinedEnergy <= 0.075 {
            // Silêncio aguardando fala: linha de pontos luminosos com micro-respiração suave
            let idlePulse = CGFloat(sin(time * 3.0 + Double(index) * 0.3)) * 0.55
            return max(4.8, 5.2 + idlePulse)
        }

        // Em voz normal (~0.75-0.85), as barras centrais oscilam na faixa de ~28-32pt (teto em 36pt)
        let dynamicExcursion = combinedEnergy * (0.66 + 0.38 * harmonicModulation) * (0.48 + 0.52 * CGFloat(sin(pos * .pi)))
        return min(36.0, max(5.2, 5.2 + dynamicExcursion * 32.0))
    }
}

//
//  GeminiAIService.swift
//  Forma
//
//  Created by João Pedro on 10/29/26.
//

import Foundation
import Combine
import SwiftData

// MARK: - Modelos de Dados Estruturados da IA

public struct AIExtractedFoodItem: Codable, Sendable, Identifiable {
    public var id = UUID()
    public var name: String
    public var portionValue: Double
    public var unit: String
    public var isLiquid: Bool
    public var calories: Double
    public var protein: Double
    public var carbohydrates: Double
    public var fat: Double
    
    // Compatibilidade com código existente
    public var portionGrams: Double {
        get { portionValue }
        set { portionValue = newValue }
    }
    
    enum CodingKeys: String, CodingKey {
        case name
        case portionValue = "portion_value"
        case portionGrams = "portion_grams"
        case unit
        case isLiquid = "is_liquid"
        case calories
        case protein
        case carbohydrates
        case fat
    }
    
    public init(
        name: String,
        portionValue: Double,
        unit: String = "g",
        isLiquid: Bool = false,
        calories: Double,
        protein: Double,
        carbohydrates: Double,
        fat: Double
    ) {
        self.name = name
        self.portionValue = portionValue
        self.unit = unit
        self.isLiquid = isLiquid
        self.calories = calories
        self.protein = protein
        self.carbohydrates = carbohydrates
        self.fat = fat
    }
    
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.name = try container.decode(String.self, forKey: .name)
        
        let val1 = try container.decodeIfPresent(Double.self, forKey: .portionValue)
        let val2 = try container.decodeIfPresent(Double.self, forKey: .portionGrams)
        self.portionValue = val1 ?? val2 ?? 100.0
        
        let u = try container.decodeIfPresent(String.self, forKey: .unit)?.lowercased()
        let liq = try container.decodeIfPresent(Bool.self, forKey: .isLiquid)
        
        if let liq = liq {
            self.isLiquid = liq
            self.unit = u ?? (liq ? "ml" : "g")
        } else if let u = u, u == "ml" || u == "mls" {
            self.isLiquid = true
            self.unit = "ml"
        } else {
            let lower = name.lowercased()
            let isDrink = lower.contains("leite") || lower.contains("suco") || lower.contains("shake") || lower.contains("café") || lower.contains("cafe") || lower.contains("refrigerante") || lower.contains("água") || lower.contains("agua") || lower.contains("vitamina") || lower.contains("bebida")
            self.isLiquid = isDrink
            self.unit = isDrink ? "ml" : "g"
        }
        
        self.calories = try container.decodeIfPresent(Double.self, forKey: .calories) ?? 0.0
        self.protein = try container.decodeIfPresent(Double.self, forKey: .protein) ?? 0.0
        self.carbohydrates = try container.decodeIfPresent(Double.self, forKey: .carbohydrates) ?? 0.0
        self.fat = try container.decodeIfPresent(Double.self, forKey: .fat) ?? 0.0
    }
    
    public func encode(to encoder: Encoder) throws {
        var container = encoder.container(keyedBy: CodingKeys.self)
        try container.encode(name, forKey: .name)
        try container.encode(portionValue, forKey: .portionValue)
        try container.encode(portionValue, forKey: .portionGrams)
        try container.encode(unit, forKey: .unit)
        try container.encode(isLiquid, forKey: .isLiquid)
        try container.encode(calories, forKey: .calories)
        try container.encode(protein, forKey: .protein)
        try container.encode(carbohydrates, forKey: .carbohydrates)
        try container.encode(fat, forKey: .fat)
    }
}

public struct AIExtractedMeal: Codable, Sendable {
    public var mealType: String
    public var items: [AIExtractedFoodItem]
    public var totalCalories: Double
    public var totalProtein: Double
    public var totalCarbs: Double
    public var totalFat: Double
    public var aiSummary: String
    
    enum CodingKeys: String, CodingKey {
        case mealType = "meal_type"
        case items
        case totalCalories = "total_calories"
        case totalProtein = "total_protein"
        case totalCarbs = "total_carbs"
        case totalFat = "total_fat"
        case aiSummary = "ai_summary"
    }
    
    public init(
        mealType: String,
        items: [AIExtractedFoodItem],
        totalCalories: Double,
        totalProtein: Double,
        totalCarbs: Double,
        totalFat: Double,
        aiSummary: String = ""
    ) {
        self.mealType = mealType
        self.items = items
        self.totalCalories = totalCalories
        self.totalProtein = totalProtein
        self.totalCarbs = totalCarbs
        self.totalFat = totalFat
        self.aiSummary = aiSummary
    }
    
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        let decodedItems = try container.decodeIfPresent([AIExtractedFoodItem].self, forKey: .items) ?? []
        self.items = decodedItems
        self.mealType = try container.decodeIfPresent(String.self, forKey: .mealType) ?? "Almoço"
        self.totalCalories = try container.decodeIfPresent(Double.self, forKey: .totalCalories)
            ?? decodedItems.reduce(0.0) { $0 + $1.calories }
        self.totalProtein = try container.decodeIfPresent(Double.self, forKey: .totalProtein)
            ?? decodedItems.reduce(0.0) { $0 + $1.protein }
        self.totalCarbs = try container.decodeIfPresent(Double.self, forKey: .totalCarbs)
            ?? decodedItems.reduce(0.0) { $0 + $1.carbohydrates }
        self.totalFat = try container.decodeIfPresent(Double.self, forKey: .totalFat)
            ?? decodedItems.reduce(0.0) { $0 + $1.fat }
        self.aiSummary = try container.decodeIfPresent(String.self, forKey: .aiSummary) ?? ""
    }
}

public struct AIExtractedWorkout: Codable, Sendable {
    public var title: String
    public var workoutType: String
    public var durationMinutes: Int
    public var caloriesBurned: Double
    public var intensity: String
    public var targetMuscles: String
    public var hypertrophyFocus: String
    public var recommendedRestSeconds: Int
    public var aiFeedback: String
    public var exercises: [WorkoutExerciseItem]
    
    enum CodingKeys: String, CodingKey {
        case title
        case workoutType = "workout_type"
        case durationMinutes = "duration_minutes"
        case caloriesBurned = "calories_burned"
        case intensity
        case targetMuscles = "target_muscles"
        case hypertrophyFocus = "hypertrophy_focus"
        case recommendedRestSeconds = "recommended_rest_seconds"
        case aiFeedback = "ai_feedback"
        case exercises
    }
    
    public init(
        title: String,
        workoutType: String,
        durationMinutes: Int,
        caloriesBurned: Double,
        intensity: String,
        targetMuscles: String = "",
        hypertrophyFocus: String = "",
        recommendedRestSeconds: Int = 90,
        aiFeedback: String,
        exercises: [WorkoutExerciseItem] = []
    ) {
        self.title = title
        self.workoutType = workoutType
        self.durationMinutes = durationMinutes
        self.caloriesBurned = caloriesBurned
        self.intensity = intensity
        self.targetMuscles = targetMuscles
        self.hypertrophyFocus = hypertrophyFocus
        self.recommendedRestSeconds = recommendedRestSeconds
        self.aiFeedback = aiFeedback
        self.exercises = exercises
    }
    
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.title = (try? container.decodeIfPresent(String.self, forKey: .title)) ?? "Treino"
        self.workoutType = (try? container.decodeIfPresent(String.self, forKey: .workoutType)) ?? "Musculação"
        self.durationMinutes = (try? container.decodeIfPresent(Int.self, forKey: .durationMinutes)) ?? 45
        self.caloriesBurned = (try? container.decodeIfPresent(Double.self, forKey: .caloriesBurned)) ?? 300.0
        self.intensity = (try? container.decodeIfPresent(String.self, forKey: .intensity)) ?? "Moderada"
        self.targetMuscles = (try? container.decodeIfPresent(String.self, forKey: .targetMuscles)) ?? ""
        self.hypertrophyFocus = (try? container.decodeIfPresent(String.self, forKey: .hypertrophyFocus)) ?? ""
        self.recommendedRestSeconds = (try? container.decodeIfPresent(Int.self, forKey: .recommendedRestSeconds)) ?? 90
        self.aiFeedback = (try? container.decodeIfPresent(String.self, forKey: .aiFeedback)) ?? ""
        let rawExercises = (try? container.decodeIfPresent([WorkoutExerciseItem].self, forKey: .exercises)) ?? []
        self.exercises = ExerciseCatalog.shared.autoAssociateExercises(in: rawExercises)
    }
}

public struct SuggestedNutritionTargets: Codable, Sendable {
    public var dailyCalories: Double
    public var proteinGrams: Double
    public var carbsGrams: Double
    public var fatGrams: Double
    public var waterMilliliters: Int
    public var explanation: String
    
    enum CodingKeys: String, CodingKey {
        case dailyCalories = "daily_calories"
        case proteinGrams = "protein_grams"
        case carbsGrams = "carbs_grams"
        case fatGrams = "fat_grams"
        case waterMilliliters = "water_milliliters"
        case explanation
    }
    
    public init(
        dailyCalories: Double = 2800,
        proteinGrams: Double = 160,
        carbsGrams: Double = 380,
        fatGrams: Double = 70,
        waterMilliliters: Int = 2500,
        explanation: String = ""
    ) {
        self.dailyCalories = dailyCalories
        self.proteinGrams = proteinGrams
        self.carbsGrams = carbsGrams
        self.fatGrams = fatGrams
        self.waterMilliliters = waterMilliliters
        self.explanation = explanation
    }
    
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.dailyCalories = (try? container.decodeIfPresent(Double.self, forKey: .dailyCalories)) ?? 2800.0
        self.proteinGrams = (try? container.decodeIfPresent(Double.self, forKey: .proteinGrams)) ?? 160.0
        self.carbsGrams = (try? container.decodeIfPresent(Double.self, forKey: .carbsGrams)) ?? 380.0
        self.fatGrams = (try? container.decodeIfPresent(Double.self, forKey: .fatGrams)) ?? 70.0
        self.waterMilliliters = (try? container.decodeIfPresent(Int.self, forKey: .waterMilliliters)) ?? 2500
        self.explanation = (try? container.decodeIfPresent(String.self, forKey: .explanation)) ?? ""
    }
}

public struct AIRecommendedRoutine: Codable, Sendable {
    public var title: String
    public var workoutType: String
    public var targetMuscles: String
    public var hypertrophyFocus: String
    public var estimatedDurationMinutes: Int
    public var estimatedCalories: Double
    public var recommendedRestSeconds: Int
    public var exercises: [WorkoutExerciseItem]
    
    enum CodingKeys: String, CodingKey {
        case title
        case workoutType = "workout_type"
        case targetMuscles = "target_muscles"
        case hypertrophyFocus = "hypertrophy_focus"
        case estimatedDurationMinutes = "estimated_duration_minutes"
        case estimatedCalories = "estimated_calories"
        case recommendedRestSeconds = "recommended_rest_seconds"
        case exercises
    }
    
    public init(
        title: String = "Treino Personalizado",
        workoutType: String = "Musculação",
        targetMuscles: String = "",
        hypertrophyFocus: String = "",
        estimatedDurationMinutes: Int = 50,
        estimatedCalories: Double = 350.0,
        recommendedRestSeconds: Int = 90,
        exercises: [WorkoutExerciseItem] = []
    ) {
        self.title = title
        self.workoutType = workoutType
        self.targetMuscles = targetMuscles
        self.hypertrophyFocus = hypertrophyFocus
        self.estimatedDurationMinutes = estimatedDurationMinutes
        self.estimatedCalories = estimatedCalories
        self.recommendedRestSeconds = recommendedRestSeconds
        self.exercises = exercises
    }
    
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.title = (try? container.decodeIfPresent(String.self, forKey: .title)) ?? "Treino Personalizado"
        self.workoutType = (try? container.decodeIfPresent(String.self, forKey: .workoutType)) ?? "Musculação"
        self.targetMuscles = (try? container.decodeIfPresent(String.self, forKey: .targetMuscles)) ?? ""
        self.hypertrophyFocus = (try? container.decodeIfPresent(String.self, forKey: .hypertrophyFocus)) ?? ""
        self.estimatedDurationMinutes = (try? container.decodeIfPresent(Int.self, forKey: .estimatedDurationMinutes)) ?? 50
        self.estimatedCalories = (try? container.decodeIfPresent(Double.self, forKey: .estimatedCalories)) ?? 350.0
        self.recommendedRestSeconds = (try? container.decodeIfPresent(Int.self, forKey: .recommendedRestSeconds)) ?? 90
        let rawExercises = (try? container.decodeIfPresent([WorkoutExerciseItem].self, forKey: .exercises)) ?? []
        self.exercises = ExerciseCatalog.shared.autoAssociateExercises(in: rawExercises)
    }
}

public struct InitialGoalsAISuggestion: Codable, Sendable, Equatable {
    public var suggestedTargetWeight: Double
    public var dailyCalories: Double
    public var proteinGrams: Double
    public var carbsGrams: Double
    public var fatGrams: Double
    public var waterMilliliters: Int
    public var targetCalorieBurn: Double
    public var targetSteps: Double
    public var explanation: String

    enum CodingKeys: String, CodingKey {
        case suggestedTargetWeight = "suggested_target_weight"
        case dailyCalories = "daily_calories"
        case proteinGrams = "protein_grams"
        case carbsGrams = "carbs_grams"
        case fatGrams = "fat_grams"
        case waterMilliliters = "water_milliliters"
        case targetCalorieBurn = "target_calorie_burn"
        case targetSteps = "target_steps"
        case explanation
    }

    public init(
        suggestedTargetWeight: Double,
        dailyCalories: Double,
        proteinGrams: Double,
        carbsGrams: Double,
        fatGrams: Double,
        waterMilliliters: Int,
        targetCalorieBurn: Double,
        targetSteps: Double,
        explanation: String
    ) {
        self.suggestedTargetWeight = suggestedTargetWeight
        self.dailyCalories = dailyCalories
        self.proteinGrams = proteinGrams
        self.carbsGrams = carbsGrams
        self.fatGrams = fatGrams
        self.waterMilliliters = waterMilliliters
        self.targetCalorieBurn = targetCalorieBurn
        self.targetSteps = targetSteps
        self.explanation = explanation
    }

    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.suggestedTargetWeight = (try? container.decodeIfPresent(Double.self, forKey: .suggestedTargetWeight)) ?? 75.0
        self.dailyCalories = (try? container.decodeIfPresent(Double.self, forKey: .dailyCalories)) ?? 2400.0
        self.proteinGrams = (try? container.decodeIfPresent(Double.self, forKey: .proteinGrams)) ?? 150.0
        self.carbsGrams = (try? container.decodeIfPresent(Double.self, forKey: .carbsGrams)) ?? 280.0
        self.fatGrams = (try? container.decodeIfPresent(Double.self, forKey: .fatGrams)) ?? 70.0
        self.waterMilliliters = (try? container.decodeIfPresent(Int.self, forKey: .waterMilliliters)) ?? 2600
        self.targetCalorieBurn = (try? container.decodeIfPresent(Double.self, forKey: .targetCalorieBurn)) ?? 500.0
        self.targetSteps = (try? container.decodeIfPresent(Double.self, forKey: .targetSteps)) ?? 8500.0
        self.explanation = (try? container.decodeIfPresent(String.self, forKey: .explanation)) ?? ""
    }
}

public struct EvolutionAIAnalysis: Codable, Sendable {
    public var id: UUID
    public var statusSummary: String
    public var calorieDiagnostic: String
    public var macroDiagnostic: String
    public var hydrationDiagnostic: String
    public var workoutSuggestions: [String]
    public var nutritionSuggestions: [String]
    public var weeklyActionPlan: String
    public var suggestedTargets: SuggestedNutritionTargets?
    public var recommendedRoutine: AIRecommendedRoutine?
    public var generatedAt: Date
    
    enum CodingKeys: String, CodingKey {
        case id
        case statusSummary = "status_summary"
        case calorieDiagnostic = "calorie_diagnostic"
        case macroDiagnostic = "macro_diagnostic"
        case hydrationDiagnostic = "hydration_diagnostic"
        case workoutSuggestions = "workout_suggestions"
        case nutritionSuggestions = "nutrition_suggestions"
        case weeklyActionPlan = "weekly_action_plan"
        case suggestedTargets = "suggested_targets"
        case recommendedRoutine = "recommended_routine"
        case generatedAt = "generated_at"
    }
    
    public init(
        id: UUID = UUID(),
        statusSummary: String,
        calorieDiagnostic: String,
        macroDiagnostic: String,
        hydrationDiagnostic: String,
        workoutSuggestions: [String],
        nutritionSuggestions: [String],
        weeklyActionPlan: String,
        suggestedTargets: SuggestedNutritionTargets? = nil,
        recommendedRoutine: AIRecommendedRoutine? = nil,
        generatedAt: Date = Date()
    ) {
        self.id = id
        self.statusSummary = statusSummary
        self.calorieDiagnostic = calorieDiagnostic
        self.macroDiagnostic = macroDiagnostic
        self.hydrationDiagnostic = hydrationDiagnostic
        self.workoutSuggestions = workoutSuggestions
        self.nutritionSuggestions = nutritionSuggestions
        self.weeklyActionPlan = weeklyActionPlan
        self.suggestedTargets = suggestedTargets
        self.recommendedRoutine = recommendedRoutine
        self.generatedAt = generatedAt
    }
    
    public init(from decoder: Decoder) throws {
        let container = try decoder.container(keyedBy: CodingKeys.self)
        self.id = (try? container.decodeIfPresent(UUID.self, forKey: .id)) ?? UUID()
        self.statusSummary = (try? container.decodeIfPresent(String.self, forKey: .statusSummary)) ?? "Análise Concluída"
        self.calorieDiagnostic = (try? container.decodeIfPresent(String.self, forKey: .calorieDiagnostic)) ?? "Balanço calórico avaliado com sucesso."
        self.macroDiagnostic = (try? container.decodeIfPresent(String.self, forKey: .macroDiagnostic)) ?? "Distribuição de nutrientes equilibrada."
        self.hydrationDiagnostic = (try? container.decodeIfPresent(String.self, forKey: .hydrationDiagnostic)) ?? "Consumo de água avaliado."
        self.workoutSuggestions = (try? container.decodeIfPresent([String].self, forKey: .workoutSuggestions)) ?? []
        self.nutritionSuggestions = (try? container.decodeIfPresent([String].self, forKey: .nutritionSuggestions)) ?? []
        self.weeklyActionPlan = (try? container.decodeIfPresent(String.self, forKey: .weeklyActionPlan)) ?? "Mantenha a consistência diária."
        self.suggestedTargets = try? container.decodeIfPresent(SuggestedNutritionTargets.self, forKey: .suggestedTargets)
        self.recommendedRoutine = try? container.decodeIfPresent(AIRecommendedRoutine.self, forKey: .recommendedRoutine)
        self.generatedAt = (try? container.decodeIfPresent(Date.self, forKey: .generatedAt)) ?? Date()
    }
}

// MARK: - Gerenciador de Configuração do Google Gemini

public final class GeminiConfig: Sendable {
    public static let shared = GeminiConfig()
    private let keyStorage = "gemini_api_key"
    private let modelStorage = "gemini_model_name"
    
    private init() {
        // Remove chave legada armazenada no iPhone para garantir que todo tráfego vá pelo proxy seguro do backend
        UserDefaults.standard.removeObject(forKey: keyStorage)
    }
    
    public var apiKey: String {
        get { "" }
        set { UserDefaults.standard.removeObject(forKey: keyStorage) }
    }
    
    public var hasValidKey: Bool {
        // A chave e o modelo ficam seguros exclusivamente no Backend Java + Supabase e são configurados pelo Painel Web.
        true
    }
    
    public var preferredModel: String {
        get { "gemini-3.8-flash" }
        set { }
    }
    
    public static func normalizeModel(_ model: String) -> String {
        return "gemini-3.8-flash"
    }
}

// MARK: - Serviço Oficial de Integração com Google Gemini AI

@MainActor
public final class GeminiAIService: ObservableObject {
    public static let shared = GeminiAIService()
    
    @Published public var isLoading: Bool = false
    @Published public var lastErrorMessage: String? = nil
    @Published public var remainingRequestsInWindow: Int = 12
    
    // Controle de Rate Limit (Sliding Window de 60 segundos com teto de 12 requisições para plano Free de 15 RPM)
    private var requestTimestamps: [Date] = []
    private let maxRequestsPerMinute = 12
    private let windowSeconds: TimeInterval = 60.0
    
    // Cache em memória para evitar chamadas duplicadas
    private var mealCache: [String: AIExtractedMeal] = [:]
    private var workoutCache: [String: AIExtractedWorkout] = [:]
    private var generationEpoch: Int = 0
    
    private init() {}

    public func activateAllForProTier() {
        lastErrorMessage = nil
        if !AIDataConsentManager.isConsentGranted {
            AIDataConsentManager.shared.setConsent(true)
        }
    }

    public func cancelAndCutAllForFreeTier() {
        generationEpoch += 1
        isLoading = false
        lastErrorMessage = nil
    }
    
    // MARK: - Controle e Proteção de Rate Limit
    
    private func checkAndRecordRateLimit() async throws {
        let now = Date()
        // Remove timestamps mais antigos que 60 segundos
        requestTimestamps = requestTimestamps.filter { now.timeIntervalSince($0) < windowSeconds }
        remainingRequestsInWindow = max(0, maxRequestsPerMinute - requestTimestamps.count)
        
        if requestTimestamps.count >= maxRequestsPerMinute {
            if let oldest = requestTimestamps.first {
                let waitTime = max(1.0, windowSeconds - now.timeIntervalSince(oldest) + 0.5)
                print("⚠️ [Gemini AI] Rate limit atingido (\(maxRequestsPerMinute) req/min). Aguardando \(String(format: "%.1f", waitTime))s...")
                try await Task.sleep(nanoseconds: UInt64(waitTime * 1_000_000_000))
            }
        }
        
        requestTimestamps.append(Date())
        remainingRequestsInWindow = max(0, maxRequestsPerMinute - requestTimestamps.count)
    }
    
    // MARK: - Nome do Usuário no Contexto de IA
    
    private var currentUserName: String {
        let descriptor = FetchDescriptor<Profile>()
        if let profile = try? FormaDataStore.shared.mainContext.fetch(descriptor).first,
           !profile.nome.trimmingCharacters(in: .whitespaces).isEmpty {
            return profile.nome.trimmingCharacters(in: .whitespaces)
        }
        return "Usuário"
    }

    // MARK: - 1. Análise de Alimentos e Refeições com Gemini AI
    
    public func analyzeMeal(description: String) async throws -> AIExtractedMeal {
        let trimmed = description.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw NSError(domain: "GeminiAI", code: 400, userInfo: [NSLocalizedDescriptionKey: "Descrição vazia"])
        }
        
        let cacheKey = trimmed.lowercased()
        if let cached = mealCache[cacheKey] {
            print("⚡️ [Gemini AI] Refeição carregada do cache instantâneo!")
            return cached
        }
        
        isLoading = true
        lastErrorMessage = nil
        defer { isLoading = false }
        
        try await checkAndRecordRateLimit()
        
        let userName = currentUserName
        
        let responseJson = try await sendBackendAiRequest(
            path: "analyze-meal",
            body: [
                "description": trimmed,
                "userName": userName
            ]
        )
        
        guard let data = responseJson.data(using: .utf8) else {
            throw NSError(domain: "GeminiAI", code: 500, userInfo: [NSLocalizedDescriptionKey: "Resposta inválida da Inteligência Forma"])
        }
        
        let decoded = try JSONDecoder().decode(AIExtractedMeal.self, from: data)
        mealCache[cacheKey] = decoded
        return decoded
    }
    
    /// Analisa foto do prato, bebida ou tabela nutricional usando visão multimodal do Gemini
    public func analyzeMealImage(
        imageData: Data,
        mimeType: String = "image/jpeg",
        additionalContext: String = ""
    ) async throws -> AIExtractedMeal {
        guard !imageData.isEmpty else {
            throw NSError(domain: "GeminiAI", code: 400, userInfo: [NSLocalizedDescriptionKey: "Imagem inválida ou vazia"])
        }
        
        isLoading = true
        lastErrorMessage = nil
        defer { isLoading = false }
        
        try await checkAndRecordRateLimit()
        
        let base64String = imageData.base64EncodedString()
        let trimmedNote = additionalContext.trimmingCharacters(in: .whitespacesAndNewlines)
        
        let responseJson = try await sendBackendAiRequest(
            path: "analyze-meal",
            body: [
                "description": trimmedNote,
                "userName": currentUserName,
                "imageBase64": base64String,
                "imageMimeType": mimeType
            ]
        )
        
        guard let data = responseJson.data(using: .utf8) else {
            throw NSError(domain: "GeminiAI", code: 500, userInfo: [NSLocalizedDescriptionKey: "Resposta inválida da Inteligência Forma"])
        }
        
        return try JSONDecoder().decode(AIExtractedMeal.self, from: data)
    }
    
    // MARK: - 2. Análise de Treinos e Queima Calórica com Gemini AI
    
    public func analyzeWorkout(description: String, userWeight: Double = 75.0) async throws -> AIExtractedWorkout {
        let trimmed = description.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else {
            throw NSError(domain: "GeminiAI", code: 400, userInfo: [NSLocalizedDescriptionKey: "Descrição do treino vazia"])
        }
        
        let cacheKey = "\(trimmed.lowercased())_\(Int(userWeight))"
        if let cached = workoutCache[cacheKey] {
            print("⚡️ [Gemini AI] Treino carregado do cache instantâneo!")
            return cached
        }
        
        isLoading = true
        lastErrorMessage = nil
        defer { isLoading = false }
        
        try await checkAndRecordRateLimit()
        
        let userName = currentUserName
        
        let responseJson = try await sendBackendAiRequest(
            path: "analyze-workout",
            body: [
                "description": trimmed,
                "userWeight": userWeight,
                "userName": userName
            ]
        )
        
        guard let data = responseJson.data(using: .utf8) else {
            throw NSError(domain: "GeminiAI", code: 500, userInfo: [NSLocalizedDescriptionKey: "Resposta inválida da Inteligência Forma"])
        }
        
        var decoded = try JSONDecoder().decode(AIExtractedWorkout.self, from: data)
        // Garante a associação automática de GIFs biomecânicos para todos os exercícios
        decoded.exercises = ExerciseCatalog.shared.autoAssociateExercises(in: decoded.exercises)
        workoutCache[cacheKey] = decoded
        return decoded
    }
    
    // MARK: - Análise Inteligente de Evolução e Recomendações
    
    func analyzeEvolution(
        foods: [FoodEntry],
        workouts: [WorkoutEntry],
        waters: [WaterEntry],
        weights: [WeightEntry],
        profile: Profile?
    ) async throws -> EvolutionAIAnalysis {
        // 1. Garante que o arquivo físico de log do usuário esteja 100% sincronizado
        AppActivityLogManager.shared.syncFromDatabase(foods: foods, workouts: workouts, waters: waters, weights: weights)
        let activityLogJson = AppActivityLogManager.shared.exportRecentLogForAI(days: 14)
        
        let goalEnum = profile?.goal ?? .gain
        let userGoal = goalEnum.rawValue
        let currentWeight = (profile?.currentWeight ?? 0) > 0 ? (profile?.currentWeight ?? 72.0) : (weights.first?.weight ?? 72.0)
        let targetWeight = profile?.targetWeight ?? 80.0
        let heightMeters = profile?.height ?? 1.78
        let targetBurn = profile?.targetCalorieBurn ?? 600.0
        let targetSteps = profile?.targetSteps ?? 8000.0
        
        let recalcContext = FormaDataStore.shared.buildRecalculationContext()
        let contextualMetrics = Profile.computeContextualTargets(
            weight: currentWeight,
            height: heightMeters,
            targetWeight: targetWeight,
            goal: goalEnum,
            targetCalorieBurn: targetBurn,
            targetSteps: targetSteps,
            context: recalcContext
        )
        let targetCalories = profile?.targetCalorieIntake ?? contextualMetrics.suggestedCalories
        
        let responseJson = try await sendBackendAiRequest(
            path: "analyze-evolution",
            body: [
                "userGoal": userGoal,
                "currentWeight": currentWeight,
                "targetWeight": targetWeight,
                "heightMeters": heightMeters,
                "targetCalories": targetCalories,
                "targetCalorieBurn": targetBurn,
                "targetSteps": targetSteps,
                "calculatedTmb": contextualMetrics.tmb,
                "contextualTdee": contextualMetrics.contextualTdee,
                "suggestedBaseCalories": contextualMetrics.suggestedCalories,
                "suggestedProtein": contextualMetrics.proteinGrams,
                "suggestedCarbs": contextualMetrics.carbsGrams,
                "suggestedFat": contextualMetrics.fatGrams,
                "suggestedWaterMl": contextualMetrics.waterMilliliters,
                "avgDailyWorkoutBurn": recalcContext.avgDailyWorkoutBurn,
                "activityLogJson": activityLogJson,
                "userName": currentUserName
            ]
        )
        guard let data = responseJson.data(using: .utf8) else {
            throw NSError(domain: "GeminiAI", code: 500, userInfo: [NSLocalizedDescriptionKey: "Resposta inválida da Inteligência Forma"])
        }
        
        var decoded = try JSONDecoder().decode(EvolutionAIAnalysis.self, from: data)
        
        // Garante que os exercícios da rotina recomendada estejam associados aos GIFs biomecânicos
        if var routine = decoded.recommendedRoutine {
            routine.exercises = ExerciseCatalog.shared.autoAssociateExercises(in: routine.exercises)
            decoded.recommendedRoutine = routine
        }
        
        return decoded
    }

    // MARK: - 4. Sugestão Inicial de Metas no Onboarding Obrigatório

    public func suggestInitialGoals(
        userName: String,
        goal: FitnessGoal,
        currentWeight: Double,
        heightMeters: Double,
        targetWeight: Double?,
        activityLevel: String
    ) async -> InitialGoalsAISuggestion {
        let safeName = userName.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "Atleta" : userName.trimmingCharacters(in: .whitespacesAndNewlines)
        var body: [String: Any] = [
            "userName": safeName,
            "goal": goal.rawValue,
            "currentWeight": currentWeight,
            "heightMeters": heightMeters,
            "activityLevel": activityLevel
        ]
        if let targetWeight, targetWeight >= 30.0 {
            body["targetWeight"] = targetWeight
        }

        if AuthManager.shared.isPro {
            if !AIDataConsentManager.isConsentGranted {
                AIDataConsentManager.shared.setConsent(true)
            }
            do {
                let responseJson = try await sendBackendAiRequest(
                    path: "suggest-initial-goals",
                    body: body
                )
                if let data = responseJson.data(using: .utf8),
                   let decoded = try? JSONDecoder().decode(InitialGoalsAISuggestion.self, from: data),
                   decoded.dailyCalories >= 1000 {
                    return decoded
                }
            } catch {
                print("⚠️ [Gemini AI] Fallback fisiológico acionado para metas iniciais: \(error.localizedDescription)")
            }
        }

        // Cálculo fisiológico local equivalente (Mifflin-St Jeor + objetivo + nível de atividade) caso offline temporário
        let lowerAct = activityLevel.lowercased()
        let burn: Double = lowerAct.contains("intenso") ? 650.0 : (lowerAct.contains("leve") ? 350.0 : 500.0)
        let steps: Double = lowerAct.contains("intenso") ? 10000.0 : (lowerAct.contains("leve") ? 7000.0 : 8500.0)
        let idealBmiWeight = 22.5 * heightMeters * heightMeters
        let resolvedTargetWeight: Double
        if let targetWeight, targetWeight >= 30.0 {
            resolvedTargetWeight = (targetWeight * 10.0).rounded() / 10.0
        } else {
            switch goal {
            case .lose:
                resolvedTargetWeight = ((max(45.0, min(currentWeight - 4.0, idealBmiWeight))) * 10.0).rounded() / 10.0
            case .maintain:
                resolvedTargetWeight = (currentWeight * 10.0).rounded() / 10.0
            case .gain:
                resolvedTargetWeight = ((max(currentWeight + 5.0, idealBmiWeight)) * 10.0).rounded() / 10.0
            }
        }

        let metrics = Profile.computeContextualTargets(
            weight: currentWeight,
            height: heightMeters,
            targetWeight: resolvedTargetWeight,
            goal: goal,
            targetCalorieBurn: burn,
            targetSteps: steps
        )
        let explanation = "\(safeName), calculamos sua Taxa Basal (~\(Int(metrics.tmb)) kcal) e gasto diário (~\(Int(metrics.contextualTdee)) kcal) para \(String(format: "%.1f", currentWeight)) kg e \(String(format: "%.2f", heightMeters)) m rumo aos \(String(format: "%.1f", resolvedTargetWeight)) kg."

        return InitialGoalsAISuggestion(
            suggestedTargetWeight: resolvedTargetWeight,
            dailyCalories: metrics.suggestedCalories,
            proteinGrams: metrics.proteinGrams,
            carbsGrams: metrics.carbsGrams,
            fatGrams: metrics.fatGrams,
            waterMilliliters: metrics.waterMilliliters,
            targetCalorieBurn: burn,
            targetSteps: steps,
            explanation: explanation
        )
    }
    
    // MARK: - Comunicação HTTP Segura via Backend Java (Proxy de IA)
    
    private func sendBackendAiRequest(path: String, body: [String: Any]) async throws -> String {
        guard AuthManager.shared.isPro else {
            throw NSError(
                domain: "GeminiAI",
                code: 403,
                userInfo: [NSLocalizedDescriptionKey: "Os recursos de Inteligência Artificial são exclusivos da camada Forma Pro."]
            )
        }
        if !AIDataConsentManager.isConsentGranted {
            AIDataConsentManager.shared.setConsent(true)
        }
        let requestEpoch = self.generationEpoch
        let data = try await FormaAPIClient.shared.requestData(
            path: "/api/ai/\(path)",
            method: "POST",
            jsonBody: body,
            authenticated: true,
            timeout: 60.0
        )
        guard AuthManager.shared.isPro, self.generationEpoch == requestEpoch else {
            throw CancellationError()
        }
        guard let text = String(data: data, encoding: .utf8), !text.isEmpty else {
            throw FormaAPIError.serverError(statusCode: 500, message: "Resposta vazia da Inteligência Forma.")
        }
        return Self.stripMarkdownCodeFences(text)
    }

    public static func stripMarkdownCodeFences(_ raw: String) -> String {
        var cleanedText = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        if cleanedText.hasPrefix("```json") {
            cleanedText = String(cleanedText.dropFirst(7))
        } else if cleanedText.hasPrefix("```") {
            cleanedText = String(cleanedText.dropFirst(3))
        }
        if cleanedText.hasSuffix("```") {
            cleanedText = String(cleanedText.dropLast(3))
        }
        return cleanedText.trimmingCharacters(in: .whitespacesAndNewlines)
    }
}

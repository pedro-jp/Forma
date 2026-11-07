//
//  AIFoodParserService.swift
//  Forma
//
//  Created by João Pedro on 10/29/26.
//

import Foundation

/// Item individual identificado pelo analisador semântico
public struct ParsedFoodItem: Sendable, Identifiable {
    public var id = UUID()
    public var originalPhrase: String
    public var matchedFoodName: String
    public var brand: String
    public var portionValue: Double
    public var unit: String
    public var isLiquid: Bool
    public var calories: Double
    public var protein: Double
    public var carbohydrates: Double
    public var fat: Double
    public var confidence: Double
    public var source: String
    
    // Compatibilidade com código legado
    public var portionGrams: Double {
        get { portionValue }
        set { portionValue = newValue }
    }
    
    public init(
        originalPhrase: String,
        matchedFoodName: String,
        brand: String = "",
        portionValue: Double,
        unit: String = "g",
        isLiquid: Bool = false,
        calories: Double,
        protein: Double,
        carbohydrates: Double,
        fat: Double,
        confidence: Double = 0.9,
        source: String = "TACO Brasil"
    ) {
        self.originalPhrase = originalPhrase
        self.matchedFoodName = matchedFoodName
        self.brand = brand
        self.portionValue = portionValue
        self.unit = unit
        self.isLiquid = isLiquid
        self.calories = calories
        self.protein = protein
        self.carbohydrates = carbohydrates
        self.fat = fat
        self.confidence = confidence
        self.source = source
    }
    
    public init(
        originalPhrase: String,
        matchedFoodName: String,
        brand: String = "",
        portionGrams: Double,
        calories: Double,
        protein: Double,
        carbohydrates: Double,
        fat: Double,
        confidence: Double = 0.9,
        source: String = "TACO Brasil"
    ) {
        let isLiq = matchedFoodName.lowercased().contains("leite") || matchedFoodName.lowercased().contains("suco") || matchedFoodName.lowercased().contains("shake") || matchedFoodName.lowercased().contains("café") || matchedFoodName.lowercased().contains("refrigerante")
        self.init(
            originalPhrase: originalPhrase,
            matchedFoodName: matchedFoodName,
            brand: brand,
            portionValue: portionGrams,
            unit: isLiq ? "ml" : "g",
            isLiquid: isLiq,
            calories: calories,
            protein: protein,
            carbohydrates: carbohydrates,
            fat: fat,
            confidence: confidence,
            source: source
        )
    }
}

/// Resultado consolidado de uma refeição analisada por IA
public struct ParsedMealResult: Sendable {
    public var rawText: String
    public var mealType: MealType
    public var items: [ParsedFoodItem]
    public var totalCalories: Double
    public var totalProtein: Double
    public var totalCarbs: Double
    public var totalFat: Double
    public var overallConfidence: Double
    
    public init(
        rawText: String,
        mealType: MealType,
        items: [ParsedFoodItem],
        totalCalories: Double,
        totalProtein: Double,
        totalCarbs: Double,
        totalFat: Double,
        overallConfidence: Double
    ) {
        self.rawText = rawText
        self.mealType = mealType
        self.items = items
        self.totalCalories = totalCalories
        self.totalProtein = totalProtein
        self.totalCarbs = totalCarbs
        self.totalFat = totalFat
        self.overallConfidence = overallConfidence
    }
}

/// Serviço de inteligência artificial e processamento de linguagem natural
/// Especializado na culinária e vocabulário nutricional brasileiro
public final class AIFoodParserService: Sendable {
    public static let shared = AIFoodParserService()
    
    private init() {}
    
    // MARK: - API Principal de Parsing
    
    public func parseMeal(
        description: String,
        forceAI: Bool = false,
        allowCloudAI: Bool = true,
        onPhaseChange: (@Sendable (AnalysisPhase) -> Void)? = nil
    ) async -> ParsedMealResult {
        if allowCloudAI || forceAI {
            onPhaseChange?(forceAI ? .artificialIntelligence : .tacoDatabase)
        }
        
        let cleanedInput = description.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanedInput.isEmpty else {
            return ParsedMealResult(
                rawText: "",
                mealType: inferMealType(from: ""),
                items: [],
                totalCalories: 0,
                totalProtein: 0,
                totalCarbs: 0,
                totalFat: 0,
                overallConfidence: 0
            )
        }
        
        let mealType = inferMealType(from: cleanedInput)
        let sanitizedText = stripConversationalNoise(from: cleanedInput)
        let rawSegments = segmentDishes(from: sanitizedText)
        
        // 1. TENTA PRIMEIRO A BASE LOCAL INSTANTÂNEA (Tabela TACO Brasil - 0ms) se forceAI for false
        if !forceAI {
            var localParsedItems: [ParsedFoodItem] = []
            for segment in rawSegments {
                if let item = processLocalSegment(segment) {
                    localParsedItems.append(item)
                }
            }
            
            // Se todos os componentes foram identificados com sucesso na base local offline, retorna de imediato
            if !localParsedItems.isEmpty && localParsedItems.count >= rawSegments.count {
                let totalCal = localParsedItems.reduce(0.0) { $0 + $1.calories }
                let totalProt = localParsedItems.reduce(0.0) { $0 + $1.protein }
                let totalCarb = localParsedItems.reduce(0.0) { $0 + $1.carbohydrates }
                let totalFat = localParsedItems.reduce(0.0) { $0 + $1.fat }
                return ParsedMealResult(
                    rawText: cleanedInput,
                    mealType: mealType,
                    items: localParsedItems,
                    totalCalories: totalCal,
                    totalProtein: totalProt,
                    totalCarbs: totalCarb,
                    totalFat: totalFat,
                    overallConfidence: 0.98
                )
            }
            
            // Enquanto o usuário ainda está digitando/editando (allowCloudAI == false), NUNCA dispara requisição para a IA
            if !allowCloudAI {
                return ParsedMealResult(
                    rawText: cleanedInput,
                    mealType: mealType,
                    items: [],
                    totalCalories: 0,
                    totalProtein: 0,
                    totalCarbs: 0,
                    totalFat: 0,
                    overallConfidence: 0
                )
            }
        }
        
        // 2. ANÁLISE COM INTELIGÊNCIA ARTIFICIAL (Forçada pelo usuário ou Fallback quando item não consta na TACO após finalizar entrada)
        if AIDataConsentManager.isConsentGranted && ((allowCloudAI && GeminiConfig.shared.hasValidKey) || forceAI) {
            onPhaseChange?(.artificialIntelligence)
            // Intervalo suave para percepção visual da transição
            if !forceAI {
                try? await Task.sleep(nanoseconds: 120_000_000)
            }
            if Task.isCancelled {
                return ParsedMealResult(
                    rawText: cleanedInput,
                    mealType: mealType,
                    items: [],
                    totalCalories: 0,
                    totalProtein: 0,
                    totalCarbs: 0,
                    totalFat: 0,
                    overallConfidence: 0
                )
            }
            
            do {
                let aiMeal = try await GeminiAIService.shared.analyzeMeal(description: cleanedInput)
                let detectedMealType = MealType(rawValue: aiMeal.mealType) ?? mealType
                let items: [ParsedFoodItem] = aiMeal.items.map { item in
                    ParsedFoodItem(
                        originalPhrase: item.name,
                        matchedFoodName: item.name,
                        brand: "",
                        portionValue: item.portionValue,
                        unit: item.unit,
                        isLiquid: item.isLiquid,
                        calories: item.calories,
                        protein: item.protein,
                        carbohydrates: item.carbohydrates,
                        fat: item.fat,
                        confidence: 0.99,
                        source: "IA Gemini"
                    )
                }
                
                return ParsedMealResult(
                    rawText: cleanedInput,
                    mealType: detectedMealType,
                    items: items,
                    totalCalories: aiMeal.totalCalories,
                    totalProtein: aiMeal.totalProtein,
                    totalCarbs: aiMeal.totalCarbs,
                    totalFat: aiMeal.totalFat,
                    overallConfidence: 0.99
                )
            } catch {
                print("⚠️ [AIFoodParser] Análise IA em nuvem indisponível: \(error.localizedDescription)")
            }
        }
        
        // 3. Fallback Heurístico Local Final (Sem novas chamadas de rede!)
        var parsedItems: [ParsedFoodItem] = []
        for segment in rawSegments {
            if let item = self.processOfflineFallbackSegment(segment) {
                parsedItems.append(item)
            }
        }
        
        if parsedItems.isEmpty {
            if let singleItem = self.processOfflineFallbackSegment(sanitizedText.isEmpty ? cleanedInput : sanitizedText) {
                parsedItems.append(singleItem)
            }
        }
        
        let totalCal = parsedItems.reduce(0.0) { $0 + $1.calories }
        let totalProt = parsedItems.reduce(0.0) { $0 + $1.protein }
        let totalCarb = parsedItems.reduce(0.0) { $0 + $1.carbohydrates }
        let totalFat = parsedItems.reduce(0.0) { $0 + $1.fat }
        let avgConfidence = parsedItems.isEmpty ? 0.0 : (parsedItems.reduce(0.0) { $0 + $1.confidence } / Double(parsedItems.count))
        
        return ParsedMealResult(
            rawText: cleanedInput,
            mealType: mealType,
            items: parsedItems,
            totalCalories: totalCal,
            totalProtein: totalProt,
            totalCarbs: totalCarb,
            totalFat: totalFat,
            overallConfidence: avgConfidence
        )
    }
    
    // MARK: - Processamento Local Direto
    
    private func processLocalSegment(_ segment: String) -> ParsedFoodItem? {
        let trimmed = segment.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        
        let portion = extractGrams(from: trimmed)
        let foodQuery = extractFoodName(from: trimmed)
        let queryToUse = foodQuery.isEmpty ? trimmed : foodQuery
        
        let localMatches = NutritionService.shared.searchLocal(query: queryToUse)
        guard let best = localMatches.first else { return nil }
        
        let values = best.values(for: portion)
        return ParsedFoodItem(
            originalPhrase: trimmed,
            matchedFoodName: best.name,
            brand: best.brand,
            portionValue: portion,
            unit: best.defaultUnit,
            isLiquid: best.isLiquid,
            calories: values.calories,
            protein: values.protein,
            carbohydrates: values.carbs,
            fat: values.fat,
            confidence: 0.98,
            source: best.sourceTag
        )
    }
    
    // MARK: - Processamento de Segmento Offline (Fallback sem chamadas de IA)
    
    private func processOfflineFallbackSegment(_ segment: String) -> ParsedFoodItem? {
        let trimmed = segment.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return nil }
        
        if let local = processLocalSegment(trimmed) {
            return local
        }
        
        let portionGrams = extractGrams(from: trimmed)
        let foodQuery = extractFoodName(from: trimmed)
        let queryToUse = foodQuery.isEmpty ? trimmed : foodQuery
        
        // Fallback inteligente para estimativa aproximada offline
        let estimated = estimateNutrients(for: queryToUse, grams: portionGrams)
        return ParsedFoodItem(
            originalPhrase: trimmed,
            matchedFoodName: queryToUse.capitalized,
            brand: "Estimativa Inteligente",
            portionGrams: portionGrams,
            calories: estimated.calories,
            protein: estimated.protein,
            carbohydrates: estimated.carbs,
            fat: estimated.fat,
            confidence: 0.70,
            source: "Estimativa Heurística"
        )
    }
    
    // MARK: - Segmentação de Pratos em Português
    
    private func segmentDishes(from text: String) -> [String] {
        // Substitui quebras de linha e pontuação por delimitador padrão
        var normalized = text
            .replacingOccurrences(of: ";", with: ",")
            .replacingOccurrences(of: "\n", with: ",")
            .replacingOccurrences(of: " com mais ", with: ",")
            .replacingOccurrences(of: " acompanhado de ", with: ",")
            .replacingOccurrences(of: " junto com ", with: ",")
            .replacingOccurrences(of: " + ", with: ",")
            .replacingOccurrences(of: " mais ", with: ",")
        
        // Tratamento especial para o ' e ' / ' com '
        // Mantém apenas termos indivisíveis da culinária brasileira
        let unifiedExpressions = [
            "café com leite", "cafe com leite",
            "pão de queijo", "pao de queijo",
            "batata doce", "peito de frango",
            "queijo minas", "queijo prato"
        ]
        
        var placeholders: [String: String] = [:]
        var counter = 0
        for expr in unifiedExpressions {
            if normalized.localizedCaseInsensitiveContains(expr) {
                let tag = "__EXPR_\(counter)__"
                placeholders[tag] = expr
                let pattern = #"(?i)\b"# + NSRegularExpression.escapedPattern(for: expr) + #"\b"#
                normalized = normalized.replacingOccurrences(of: pattern, with: tag, options: .regularExpression)
                counter += 1
            }
        }
        
        // Agora que expressões compostas estão protegidas, podemos quebrar por " e " e " com "
        normalized = normalized.replacingOccurrences(of: #"\s+\be\b\s+"#, with: ",", options: .regularExpression)
        normalized = normalized.replacingOccurrences(of: #"\s+\bcom\b\s+"#, with: ",", options: .regularExpression)
        
        // Restaura as expressões compostas
        for (tag, original) in placeholders {
            normalized = normalized.replacingOccurrences(of: tag, with: original)
        }
        
        let segments = normalized.components(separatedBy: ",")
            .map { $0.trimmingCharacters(in: .whitespacesAndNewlines) }
            .filter { !$0.isEmpty && $0.count > 1 }
        
        return segments
    }
    
    // MARK: - Extração Gramas & Porções Típicas Brasileiras
    
    public func extractGrams(from text: String) -> Double {
        let lower = text.lowercased()
        
        // 0. Multiplicador com "cada" (ex: "Dois pães francês de 76 g cada" -> 2 * 76 = 152g)
        let eachGramPattern = #"^(?:(\d+|um|uma|dois|duas|três|tres|quatro|cinco)\s+.+?\s+)?(\d+(?:[.,]\d+)?)\s*(?:gramas?|g\b|gr\b)\s+cada\b"#
        if let regex = try? NSRegularExpression(pattern: eachGramPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)),
           let gramsRange = Range(match.range(at: 2), in: lower),
           let grams = Double(String(lower[gramsRange]).replacingOccurrences(of: ",", with: ".")), grams > 0 {
            if match.range(at: 1).location != NSNotFound, let qtyRange = Range(match.range(at: 1), in: lower) {
                let mult = parseQuantity(String(lower[qtyRange]))
                return mult * grams
            }
            return grams
        }
        
        // 1. Gramas explícitas (ex: 150g, 200 gramas, 350 gr)
        let gramPattern = #"(\d+(?:[.,]\d+)?)\s*(?:gramas?|g\b|gr\b)"#
        if let regex = try? NSRegularExpression(pattern: gramPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)),
           let range = Range(match.range(at: 1), in: lower) {
            let numStr = String(lower[range]).replacingOccurrences(of: ",", with: ".")
            if let grams = Double(numStr), grams > 0 {
                return grams
            }
        }
        
        // 2. Mililitros (ex: 200ml, 300 ml)
        let mlPattern = #"(\d+(?:[.,]\d+)?)\s*(?:ml\b|mls\b)"#
        if let regex = try? NSRegularExpression(pattern: mlPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)),
           let range = Range(match.range(at: 1), in: lower) {
            let numStr = String(lower[range]).replacingOccurrences(of: ",", with: ".")
            if let ml = Double(numStr), ml > 0 {
                return ml
            }
        }
        
        // 3. Pratão / Prato
        if lower.contains("pratão") || lower.contains("pratao") {
            return 250.0
        }
        if lower.contains("prato fundo") {
            return 220.0
        }
        if lower.contains("prato") || lower.contains("pratinho") {
            return 180.0
        }
        
        // 4. Concha (feijão, sopa, caldo)
        let conchaPattern = #"(\d+|uma?|duas?|três|tres)\s*concha[s]?"#
        if let regex = try? NSRegularExpression(pattern: conchaPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)),
           let range = Range(match.range(at: 1), in: lower) {
            let count = parseQuantity(String(lower[range]))
            return count * 130.0
        }
        if lower.contains("concha") {
            return 130.0
        }
        
        // 5. Escumadeira (arroz)
        let escumadeiraPattern = #"(\d+|uma?|duas?|três|tres)\s*escumadeira[s]?"#
        if let regex = try? NSRegularExpression(pattern: escumadeiraPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)),
           let range = Range(match.range(at: 1), in: lower) {
            let count = parseQuantity(String(lower[range]))
            return count * 100.0
        }
        if lower.contains("escumadeira") {
            return 100.0
        }
        
        // 6. Colheres de sopa / colheres
        let colherPattern = #"(\d+|uma?|duas?|três|tres|quatro|cinco)\s*colher(?:es)?(?:\s*de\s*sopa)?"#
        if let regex = try? NSRegularExpression(pattern: colherPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)),
           let range = Range(match.range(at: 1), in: lower) {
            let count = parseQuantity(String(lower[range]))
            // Arroz e massas: ~25g por colher; líquidos/pastas: ~15g
            if lower.contains("arroz") || lower.contains("purê") || lower.contains("pure") {
                return count * 25.0
            }
            return count * 15.0
        }
        
        // 6b. Quantidade implícita de colheres (ex: "2 de feijão", "3 de arroz")
        let implicitSpoonPattern = #"^(\d+|uma?|duas?|três|tres|quatro)\s*(?:de\s+)?(?:feij|arroz)"#
        if let regex = try? NSRegularExpression(pattern: implicitSpoonPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)) {
            if let range = Range(match.range(at: 1), in: lower) {
                let count = parseQuantity(String(lower[range]))
                return count * 25.0
            }
        }
        
        // 7. Fatias (pão, queijo, presunto, carne assada)
        let fatiaPattern = #"(\d+|uma?|duas?|três|tres|quatro)\s*fatia[s]?"#
        if let regex = try? NSRegularExpression(pattern: fatiaPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)),
           let range = Range(match.range(at: 1), in: lower) {
            let count = parseQuantity(String(lower[range]))
            return count * 35.0
        }
        
        // 8. Ovos (inteiros ou mexidos)
        let ovoPattern = #"(\d+|um|dois|duas|três|tres|quatro|cinco)\s*ovo[s]?"#
        if let regex = try? NSRegularExpression(pattern: ovoPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)),
           let range = Range(match.range(at: 1), in: lower) {
            let count = parseQuantity(String(lower[range]))
            return count * 50.0
        }
        if lower.contains("ovo") || lower.contains("ovos") {
            return 100.0 // 2 ovos padrão
        }
        
        // 9. Bife / Filé / Pedaço
        let bifePattern = #"(\d+|um|dois|duas|três|tres)\s*(?:bife|filé|file|pedaço|pedaco)[s]?"#
        if let regex = try? NSRegularExpression(pattern: bifePattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)),
           let range = Range(match.range(at: 1), in: lower) {
            let count = parseQuantity(String(lower[range]))
            return count * 150.0
        }
        if lower.contains("bife") || lower.contains("filé") || lower.contains("file") {
            return 150.0
        }
        
        // 10. Copo / Xícara
        let copoPattern = #"(\d+|um|dois|duas)\s*(?:copo|xícara|xicara|caneca)[s]?"#
        if let regex = try? NSRegularExpression(pattern: copoPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)),
           let range = Range(match.range(at: 1), in: lower) {
            let count = parseQuantity(String(lower[range]))
            return count * 200.0
        }
        
        // 11. Scoop / Medidor (Whey / Suplementos)
        let scoopPattern = #"(\d+|um|dois|duas)\s*(?:scoop|dose|medidor)[s]?"#
        if let regex = try? NSRegularExpression(pattern: scoopPattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: lower, options: [], range: NSRange(lower.startIndex..., in: lower)),
           let range = Range(match.range(at: 1), in: lower) {
            let count = parseQuantity(String(lower[range]))
            return count * 30.0
        }
        if lower.contains("whey") || lower.contains("proteína em pó") {
            return 30.0
        }
        
        // 12. Frutas específicas por unidade
        if lower.contains("banana") {
            let count = parseQuantityInContext(lower, around: "banana")
            return count * 90.0
        }
        if lower.contains("maçã") || lower.contains("maca") {
            let count = parseQuantityInContext(lower, around: "maç")
            return count * 130.0
        }
        
        // 13. Pão Francês
        if lower.contains("pão francês") || lower.contains("pao frances") || lower.contains("pão de sal") {
            let count = parseQuantityInContext(lower, around: "pão")
            return count * 50.0
        }
        
        // 14. Padrões por tipo de alimento
        if lower.contains("arroz") { return 150.0 }
        if lower.contains("feijão") || lower.contains("feijao") { return 130.0 }
        if lower.contains("frango") || lower.contains("carne") || lower.contains("peixe") { return 150.0 }
        if lower.contains("azeite") { return 10.0 }
        
        return 100.0
    }
    
    // MARK: - Limpeza e Extração do Nome do Alimento
    
    private func extractFoodName(from segment: String) -> String {
        var clean = segment.lowercased()
        
        // Remove medidas e números
        let removalPatterns = [
            #"(\d+(?:[.,]\d+)?)\s*(?:gramas?|g\b|gr\b|ml\b|mls\b)"#,
            #"(\d+|uma?|duas?|três|tres|quatro|cinco)\s*colher(?:es)?(?:\s*de\s*sopa)?"#,
            #"(\d+|uma?|duas?|três|tres)\s*concha[s]?"#,
            #"(\d+|uma?|duas?|três|tres)\s*escumadeira[s]?"#,
            #"(\d+|uma?|duas?|três|tres|quatro)\s*fatia[s]?"#,
            #"(\d+|um|dois|duas|três|tres|quatro)\s*ovo[s]?"#,
            #"(\d+|um|dois|duas|três|tres)\s*(?:bife|filé|file|pedaço|pedaco)[s]?"#,
            #"(\d+|um|dois|duas)\s*(?:copo|xícara|xicara|caneca)[s]?"#,
            #"(\d+|um|dois|duas)\s*(?:scoop|dose|medidor)[s]?"#,
            #"^um\s+pratão\s+de\s+"#,
            #"^um\s+pratao\s+de\s+"#,
            #"^um\s+prato\s+de\s+"#,
            #"^pratão\s+de\s+"#,
            #"^pratao\s+de\s+"#,
            #"^prato\s+de\s+"#,
            #"^um\s+pouco\s+de\s+"#,
            #"^uma\s+porção\s+de\s+"#,
            #"^uma\s+porcao\s+de\s+"#,
            #"^bastante\s+"#,
            #"^bastante\s+de\s+"#,
            #"^\d+\s*(?:de\s+)?"#,
            #"^(?:um|dois|duas|três|tres|quatro)\s*(?:de\s+)?"#,
            #"^dois\s+"#,
            #"^duas\s+"#,
            #"^três\s+"#,
            #"^tres\s+"#,
            #"^um\s+"#,
            #"^uma\s+"#
        ]
        
        for pattern in removalPatterns {
            clean = clean.replacingOccurrences(of: pattern, with: "", options: [.regularExpression, .caseInsensitive])
        }
        
        clean = clean
            .replacingOccurrences(of: #"\bcada(?:\s+uma?)?\b"#, with: " ", options: [.regularExpression, .caseInsensitive])
            .replacingOccurrences(of: " de ", with: " ")
            .replacingOccurrences(of: " da ", with: " ")
            .replacingOccurrences(of: " do ", with: " ")
            .trimmingCharacters(in: .whitespacesAndNewlines)
        
        return clean
    }
    
    // MARK: - Remoção de Ruído Conversacional
    
    private func stripConversationalNoise(from text: String) -> String {
        var clean = text.lowercased()
        
        let noisePhrases = [
            "comi", "acabei de comer", "registre para mim", "registre", "adicione aí", "adicione",
            "anote aí", "anote", "coloque", "manda aí", "tomei", "bebi", "consumi",
            "no café da manhã comi", "no café comi", "no almoço comi", "no jantar comi", "na janta comi",
            "para o almoço", "para o café", "para o jantar", "para a janta",
            "meu almoço foi", "meu café foi", "meu jantar foi", "minha janta foi", "meu lanche foi",
            "de sobremesa comi", "de pré treino tomei", "de pós treino tomei"
        ]
        
        for phrase in noisePhrases {
            clean = clean.replacingOccurrences(of: phrase, with: "")
        }
        
        return clean.trimmingCharacters(in: .whitespacesAndNewlines)
    }
    
    // MARK: - Inferência do Tipo de Refeição
    
    /// Retorna a refeição principal do dia com base no horário (Café da Manhã, Almoço ou Jantar)
    public func inferPrimaryMealType(for date: Date = Date()) -> MealType {
        let hour = Calendar.current.component(.hour, from: date)
        switch hour {
        case 5..<11:
            return .breakfast
        case 11..<18:
            return .lunch
        default:
            return .dinner
        }
    }
    
    public func inferMealType(from text: String) -> MealType {
        let lower = text.lowercased()
        
        if lower.contains("café da manhã") || lower.contains("cafe da manha") ||
            lower.contains("desjejum") || lower.contains("no café") || lower.contains("no cafe") ||
            lower.contains("pela manhã") {
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
            lower.contains("merenda") || lower.contains("ceia") ||
            lower.contains("da tarde") || lower.contains("pré treino") || lower.contains("pós treino") {
            return .snacks
        }
        
        // Padrão: Coloca automaticamente como uma das principais refeições pelo horário
        return inferPrimaryMealType()
    }
    
    // MARK: - Helpers Numéricos
    
    private func parseQuantity(_ word: String) -> Double {
        let w = word.trimmingCharacters(in: .whitespaces).lowercased()
        if let num = Double(w) { return num }
        switch w {
        case "meio", "meia": return 0.5
        case "um", "uma": return 1.0
        case "dois", "duas": return 2.0
        case "três", "tres": return 3.0
        case "quatro": return 4.0
        case "cinco": return 5.0
        default: return 1.0
        }
    }
    
    private func parseQuantityInContext(_ text: String, around keyword: String) -> Double {
        let pattern = #"(\d+|meio|meia|uma?|duas?|três|tres)\s*"# + keyword
        if let regex = try? NSRegularExpression(pattern: pattern, options: .caseInsensitive),
           let match = regex.firstMatch(in: text, options: [], range: NSRange(text.startIndex..., in: text)),
           let range = Range(match.range(at: 1), in: text) {
            return parseQuantity(String(text[range]))
        }
        return 1.0
    }
    
    // MARK: - Estimativa Heurística Nutricional
    
    private func estimateNutrients(for food: String, grams: Double) -> (calories: Double, protein: Double, carbs: Double, fat: Double) {
        let lower = food.lowercased()
        let factor = max(0, grams) / 100.0
        
        // Padrões proteicos
        if lower.contains("frango") || lower.contains("carne") || lower.contains("bife") || lower.contains("peixe") {
            return (calories: 180 * factor, protein: 28 * factor, carbs: 0, fat: 5 * factor)
        }
        // Padrões carboidratos
        if lower.contains("arroz") || lower.contains("macarrão") || lower.contains("batata") {
            return (calories: 130 * factor, protein: 2.5 * factor, carbs: 28 * factor, fat: 0.5 * factor)
        }
        // Leguminosas
        if lower.contains("feijão") || lower.contains("feijao") || lower.contains("lentilha") {
            return (calories: 80 * factor, protein: 5 * factor, carbs: 14 * factor, fat: 0.5 * factor)
        }
        // Frutas
        if lower.contains("banana") || lower.contains("maçã") || lower.contains("fruta") {
            return (calories: 70 * factor, protein: 1 * factor, carbs: 18 * factor, fat: 0.2 * factor)
        }
        // Pães e Massas
        if lower.contains("pão") || lower.contains("pao") || lower.contains("torrada") {
            return (calories: 270 * factor, protein: 8 * factor, carbs: 52 * factor, fat: 3 * factor)
        }
        // Gorduras
        if lower.contains("azeite") || lower.contains("óleo") || lower.contains("manteiga") {
            return (calories: 800 * factor, protein: 0, carbs: 0, fat: 90 * factor)
        }
        
        // Padrão geral
        return (calories: 120 * factor, protein: 4 * factor, carbs: 18 * factor, fat: 3 * factor)
    }
}

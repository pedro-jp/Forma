//
//  NutritionService.swift
//  Forma
//
//  Created by João Pedro on 10/27/26.
//

import Foundation

public struct NutritionalFoodItem: Identifiable, Sendable, Hashable {
    public let id: String
    public let name: String
    public let brand: String
    public let caloriesPer100g: Double
    public let proteinPer100g: Double
    public let carbsPer100g: Double
    public let fatPer100g: Double
    public let imageUrl: String?
    public let sourceTag: String
    
    public init(
        id: String = UUID().uuidString,
        name: String,
        brand: String = "",
        caloriesPer100g: Double,
        proteinPer100g: Double,
        carbsPer100g: Double,
        fatPer100g: Double,
        imageUrl: String? = nil,
        sourceTag: String = "TACO Brasil"
    ) {
        self.id = id
        self.name = name
        self.brand = brand
        
        let p = max(0, proteinPer100g)
        let c = max(0, carbsPer100g)
        let f = max(0, fatPer100g)
        
        // Se calorias forem 0 mas macros existirem, calcula pela fórmula Atwater (4-4-9)
        var cal = max(0, caloriesPer100g)
        if cal == 0 && (p > 0 || c > 0 || f > 0) {
            cal = (p * 4.0) + (c * 4.0) + (f * 9.0)
        }
        
        self.caloriesPer100g = cal
        self.proteinPer100g = p
        self.carbsPer100g = c
        self.fatPer100g = f
        self.imageUrl = imageUrl
        self.sourceTag = sourceTag
    }
    
    public var isLiquid: Bool {
        let lower = "\(name) \(brand)".lowercased()
        return lower.contains("leite") || lower.contains("suco") || lower.contains("shake") || lower.contains("café") || lower.contains("cafe") || lower.contains("refrigerante") || lower.contains("água") || lower.contains("agua") || lower.contains("vitamina") || lower.contains("bebida") || lower.contains("toddy pronto")
    }
    
    public var defaultUnit: String {
        isLiquid ? "ml" : "g"
    }
    
    public func values(for grams: Double) -> (calories: Double, protein: Double, carbs: Double, fat: Double) {
        let factor = max(0, grams) / 100.0
        return (
            calories: (caloriesPer100g * factor).rounded(1),
            protein: (proteinPer100g * factor).rounded(1),
            carbs: (carbsPer100g * factor).rounded(1),
            fat: (fatPer100g * factor).rounded(1)
        )
    }
}

private extension Double {
    func rounded(_ places: Int) -> Double {
        let divisor = pow(10.0, Double(places))
        return (self * divisor).rounded() / divisor
    }
}

public class NutritionService {
    public static let shared = NutritionService()
    
    private init() {}
    
    // BASE DE DADOS LOCAL INSTANTÂNEA (TABELA TACO BRASIL) - 100% DISPONÍVEL OFFLINE EM 0ms
    public let popularFoods: [NutritionalFoodItem] = [
        // Proteínas
        NutritionalFoodItem(name: "Peito de Frango Grelhado", brand: "Proteína Limpa", caloriesPer100g: 165, proteinPer100g: 31.0, carbsPer100g: 0.0, fatPer100g: 3.6, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Peito de Frango Cozido Desfiado", brand: "Proteína Limpa", caloriesPer100g: 159, proteinPer100g: 30.5, carbsPer100g: 0.0, fatPer100g: 3.2, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Carne Moída Patinho", brand: "Proteína Magra", caloriesPer100g: 187, proteinPer100g: 28.0, carbsPer100g: 0.0, fatPer100g: 7.5, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Bife de Alcatra Grelhado", brand: "Carne Vermelha", caloriesPer100g: 219, proteinPer100g: 31.0, carbsPer100g: 0.0, fatPer100g: 9.8, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Filé Mignon Grelhado", brand: "Carne Bovina", caloriesPer100g: 190, proteinPer100g: 32.0, carbsPer100g: 0.0, fatPer100g: 6.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Tilápia Grelhada", brand: "Peixe Branco", caloriesPer100g: 128, proteinPer100g: 26.0, carbsPer100g: 0.0, fatPer100g: 2.7, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Salmão Grelhado", brand: "Ômega 3", caloriesPer100g: 208, proteinPer100g: 20.0, carbsPer100g: 0.0, fatPer100g: 13.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Atum em Lata ao Natural", brand: "Pescado", caloriesPer100g: 116, proteinPer100g: 26.0, carbsPer100g: 0.0, fatPer100g: 0.8, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Ovo Cozido Inteiro", brand: "Proteína & Colina", caloriesPer100g: 143, proteinPer100g: 13.0, carbsPer100g: 0.7, fatPer100g: 9.5, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Clara de Ovo Cozida", brand: "Proteína Pura", caloriesPer100g: 52, proteinPer100g: 11.0, carbsPer100g: 0.7, fatPer100g: 0.2, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Ovo Frito", brand: "Ovo", caloriesPer100g: 240, proteinPer100g: 15.6, carbsPer100g: 1.2, fatPer100g: 18.6, sourceTag: "TACO Brasil"),
        
        // Carboidratos & Grãos
        NutritionalFoodItem(name: "Arroz Branco Cozido", brand: "Carboidrato", caloriesPer100g: 130, proteinPer100g: 2.7, carbsPer100g: 28.2, fatPer100g: 0.3, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Arroz Integral Cozido", brand: "Fibras", caloriesPer100g: 124, proteinPer100g: 2.6, carbsPer100g: 25.8, fatPer100g: 1.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Feijão Carioca Cozido", brand: "Leguminosa", caloriesPer100g: 76, proteinPer100g: 4.8, carbsPer100g: 13.6, fatPer100g: 0.5, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Feijão Preto Cozido", brand: "Leguminosa", caloriesPer100g: 77, proteinPer100g: 4.5, carbsPer100g: 14.0, fatPer100g: 0.5, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Batata Doce Cozida", brand: "Carboidrato Complexo", caloriesPer100g: 86, proteinPer100g: 1.6, carbsPer100g: 20.1, fatPer100g: 0.1, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Batata Inglesa Cozida", brand: "Tubérculo", caloriesPer100g: 77, proteinPer100g: 1.9, carbsPer100g: 17.5, fatPer100g: 0.1, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Mandioca / Aipim Cozido", brand: "Energia", caloriesPer100g: 125, proteinPer100g: 0.6, carbsPer100g: 30.1, fatPer100g: 0.3, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Macarrão Cozido", brand: "Massa", caloriesPer100g: 141, proteinPer100g: 5.0, carbsPer100g: 28.0, fatPer100g: 0.8, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Pão Francês", brand: "Padaria", caloriesPer100g: 290, proteinPer100g: 8.0, carbsPer100g: 58.0, fatPer100g: 3.1, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Pão de Forma Integral", brand: "Padaria", caloriesPer100g: 247, proteinPer100g: 9.4, carbsPer100g: 49.9, fatPer100g: 3.7, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Tapioca Pronta", brand: "Goma de Mandioca", caloriesPer100g: 240, proteinPer100g: 0.4, carbsPer100g: 60.0, fatPer100g: 0.1, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Aveia em Flocos", brand: "Cereal / Fibras", caloriesPer100g: 366, proteinPer100g: 14.0, carbsPer100g: 66.0, fatPer100g: 6.9, sourceTag: "TACO Brasil"),
        
        // Doces & Confeitos (incluindo M&M's)
        NutritionalFoodItem(name: "Chocolate M&M's Confeito", brand: "Mars M&M's", caloriesPer100g: 492, proteinPer100g: 4.5, carbsPer100g: 70.0, fatPer100g: 21.0, sourceTag: "Doces"),
        NutritionalFoodItem(name: "Chocolate ao Leite", brand: "Doces", caloriesPer100g: 535, proteinPer100g: 7.6, carbsPer100g: 59.4, fatPer100g: 29.7, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Chocolate Meio Amargo", brand: "Doces", caloriesPer100g: 479, proteinPer100g: 5.0, carbsPer100g: 55.0, fatPer100g: 30.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Doce de Leite", brand: "Doces", caloriesPer100g: 315, proteinPer100g: 5.5, carbsPer100g: 55.0, fatPer100g: 7.0, sourceTag: "TACO Brasil"),
        
        // Pratos Rápidos & Lanches
        NutritionalFoodItem(name: "Pizza de Muçarela", brand: "Massa", caloriesPer100g: 280, proteinPer100g: 12.0, carbsPer100g: 30.0, fatPer100g: 12.0, sourceTag: "Lanches"),
        NutritionalFoodItem(name: "Pizza de Calabresa", brand: "Massa", caloriesPer100g: 295, proteinPer100g: 13.0, carbsPer100g: 29.0, fatPer100g: 14.0, sourceTag: "Lanches"),
        NutritionalFoodItem(name: "Pão de Queijo Assado", brand: "Padaria", caloriesPer100g: 335, proteinPer100g: 6.5, carbsPer100g: 34.2, fatPer100g: 19.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Presunto Cozido Magro", brand: "Frios", caloriesPer100g: 115, proteinPer100g: 18.0, carbsPer100g: 1.5, fatPer100g: 4.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Peito de Peru Defumado", brand: "Frios", caloriesPer100g: 104, proteinPer100g: 17.5, carbsPer100g: 1.8, fatPer100g: 2.8, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Hambúrguer Bovino", brand: "Lanches", caloriesPer100g: 250, proteinPer100g: 17.0, carbsPer100g: 0.0, fatPer100g: 20.0, sourceTag: "Lanches"),
        NutritionalFoodItem(name: "Batata Frita", brand: "Lanches", caloriesPer100g: 312, proteinPer100g: 3.4, carbsPer100g: 41.0, fatPer100g: 15.0, sourceTag: "TACO Brasil"),
        
        // Frutas
        NutritionalFoodItem(name: "Banana Prata", brand: "Fruta", caloriesPer100g: 98, proteinPer100g: 1.3, carbsPer100g: 26.0, fatPer100g: 0.3, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Banana Nanica", brand: "Fruta", caloriesPer100g: 92, proteinPer100g: 1.4, carbsPer100g: 23.8, fatPer100g: 0.1, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Maçã Fuji / Gala", brand: "Fruta", caloriesPer100g: 52, proteinPer100g: 0.3, carbsPer100g: 13.8, fatPer100g: 0.2, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Abacate", brand: "Gordura Saudável", caloriesPer100g: 160, proteinPer100g: 2.0, carbsPer100g: 8.5, fatPer100g: 14.7, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Morango Fresco", brand: "Fruta Vermelha", caloriesPer100g: 30, proteinPer100g: 0.7, carbsPer100g: 7.7, fatPer100g: 0.3, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Laranja Pêra", brand: "Vitamina C", caloriesPer100g: 47, proteinPer100g: 0.9, carbsPer100g: 11.8, fatPer100g: 0.1, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Mamão Papaia", brand: "Fruta", caloriesPer100g: 45, proteinPer100g: 0.5, carbsPer100g: 11.6, fatPer100g: 0.1, sourceTag: "TACO Brasil"),
        
        // Laticínios & Bebidas
        NutritionalFoodItem(name: "Leite Integral", brand: "Laticínio", caloriesPer100g: 60, proteinPer100g: 3.2, carbsPer100g: 4.8, fatPer100g: 3.2, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Leite Desnatado", brand: "Laticínio", caloriesPer100g: 35, proteinPer100g: 3.4, carbsPer100g: 5.0, fatPer100g: 0.1, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Café com Leite", brand: "Bebida", caloriesPer100g: 45, proteinPer100g: 2.5, carbsPer100g: 3.8, fatPer100g: 2.3, sourceTag: "Bebidas"),
        NutritionalFoodItem(name: "Café Preto Sem Açúcar", brand: "Bebida", caloriesPer100g: 2, proteinPer100g: 0.1, carbsPer100g: 0.0, fatPer100g: 0.0, sourceTag: "Bebidas"),
        NutritionalFoodItem(name: "Suco de Laranja Natural", brand: "Bebida", caloriesPer100g: 45, proteinPer100g: 0.7, carbsPer100g: 10.4, fatPer100g: 0.2, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Iogurte Natural Desnatado", brand: "Probiótico", caloriesPer100g: 41, proteinPer100g: 3.8, carbsPer100g: 5.8, fatPer100g: 0.3, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Iogurte Grego Natural", brand: "Laticínio", caloriesPer100g: 110, proteinPer100g: 6.5, carbsPer100g: 4.0, fatPer100g: 7.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Queijo Minas Frescal", brand: "Queijo Branco", caloriesPer100g: 243, proteinPer100g: 16.0, carbsPer100g: 3.2, fatPer100g: 18.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Queijo Cottage", brand: "Queijo Magro", caloriesPer100g: 98, proteinPer100g: 11.0, carbsPer100g: 3.4, fatPer100g: 4.3, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Queijo Muçarela", brand: "Laticínio", caloriesPer100g: 280, proteinPer100g: 22.0, carbsPer100g: 3.0, fatPer100g: 20.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Requeijão Cremoso", brand: "Laticínio", caloriesPer100g: 250, proteinPer100g: 8.0, carbsPer100g: 3.0, fatPer100g: 23.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Azeite de Oliva Extra Virgem", brand: "Gordura Boa", caloriesPer100g: 884, proteinPer100g: 0.0, carbsPer100g: 0.0, fatPer100g: 100.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Manteiga", brand: "Laticínio", caloriesPer100g: 717, proteinPer100g: 0.9, carbsPer100g: 0.1, fatPer100g: 81.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Castanha-do-Pará", brand: "Oleaginosa", caloriesPer100g: 656, proteinPer100g: 14.3, carbsPer100g: 12.3, fatPer100g: 66.4, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Castanha de Caju", brand: "Oleaginosa", caloriesPer100g: 553, proteinPer100g: 18.2, carbsPer100g: 30.2, fatPer100g: 43.8, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Pasta de Amendoim Integral", brand: "Hipercalórico Limpo", caloriesPer100g: 588, proteinPer100g: 25.0, carbsPer100g: 20.0, fatPer100g: 50.0, sourceTag: "TACO Brasil"),
        
        // Suplementos & Ganho de Peso
        NutritionalFoodItem(name: "Whey Protein Concentrado 80%", brand: "Suplemento", caloriesPer100g: 400, proteinPer100g: 80.0, carbsPer100g: 8.0, fatPer100g: 6.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Whey Protein Isolado 90%", brand: "Suplemento", caloriesPer100g: 370, proteinPer100g: 90.0, carbsPer100g: 1.0, fatPer100g: 1.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Hipercalórico Mass Gainer", brand: "Bulking", caloriesPer100g: 380, proteinPer100g: 16.0, carbsPer100g: 74.0, fatPer100g: 2.0, sourceTag: "TACO Brasil"),
        NutritionalFoodItem(name: "Hipercalórico Growth Supplements", brand: "Growth", caloriesPer100g: 385, proteinPer100g: 20.0, carbsPer100g: 72.0, fatPer100g: 1.8, sourceTag: "Suplementos"),
        NutritionalFoodItem(name: "Achocolatado Toddy em Pó", brand: "Toddy", caloriesPer100g: 400, proteinPer100g: 2.0, carbsPer100g: 90.0, fatPer100g: 2.5, sourceTag: "Doces"),
        NutritionalFoodItem(name: "Leite com Toddy", brand: "Bebida", caloriesPer100g: 80, proteinPer100g: 3.2, carbsPer100g: 11.5, fatPer100g: 2.4, sourceTag: "Bebidas"),
        NutritionalFoodItem(name: "Creatina Monohidratada", brand: "Força / ATP", caloriesPer100g: 0, proteinPer100g: 0.0, carbsPer100g: 0.0, fatPer100g: 0.0, sourceTag: "TACO Brasil")
    ]
    
    /// Normaliza acentos, grafias equivalentes e plurais comuns em português para busca local instantânea
    private func normalizeToken(_ token: String) -> String {
        var t = token
            .folding(options: .diacriticInsensitive, locale: Locale(identifier: "pt-BR"))
            .lowercased()
            .replacingOccurrences(of: "mussarela", with: "mucarela")
        
        switch t {
        case "paes": return "pao"
        case "franceses": return "frances"
        case "integrais": return "integral"
        case "naturais": return "natural"
        default:
            if t.count > 4 && t.hasSuffix("s") && !t.hasSuffix("ss") && t != "aveias" {
                t.removeLast()
            }
            return t
        }
    }
    
    /// 1. Busca Local Instantânea (0 milissegundos)
    public func searchLocal(query: String) -> [NutritionalFoodItem] {
        let clean = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if clean.isEmpty {
            return popularFoods
        }
        
        // Normaliza apelidos comuns (ex: mm -> m&m)
        let normalized: String
        if clean == "mm" || clean == "m&m" || clean == "mms" || clean == "m&ms" {
            normalized = "m&m"
        } else {
            normalized = clean
        }
        
        let stopTokens: Set<String> = ["de", "da", "do", "com", "cada", "um", "uma"]
        let terms = normalized
            .components(separatedBy: .whitespacesAndNewlines)
            .map { normalizeToken($0) }
            .filter { !$0.isEmpty && !stopTokens.contains($0) }
        
        guard !terms.isEmpty else { return [] }
        
        return popularFoods.filter { item in
            let targetTokens = "\(item.name) \(item.brand)"
                .components(separatedBy: .whitespacesAndNewlines)
                .map { normalizeToken($0) }
                .joined(separator: " ")
            return terms.allSatisfy { targetTokens.contains($0) }
        }
    }
    
    /// 2. Busca Unificada (Local Instantânea de primeira + Nuvem como Fallback)
    /// Sem APIs legadas (USDA / Open Food Facts removidas)
    public func searchFoods(query: String) async -> [NutritionalFoodItem] {
        let clean = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !clean.isEmpty else { return [] }
        
        // Se a query for apenas número ou unidade (ex: "100g"), não busca
        if isPureMeasure(clean) {
            return []
        }
        
        // 1. Tenta correspondência local exata instantânea (0ms) - SEMPRE DE PRIMEIRA
        let local = searchLocal(query: clean)
        if !local.isEmpty {
            return local
        }
        
        // 2. FALLBACK: Se NÃO estiver na base local, a chave estiver configurada e houver consentimento de IA, consulta a base expandida na nuvem
        if GeminiConfig.shared.hasValidKey && AIDataConsentManager.isConsentGranted {
            do {
                let aiMeal = try await GeminiAIService.shared.analyzeMeal(description: clean)
                
                var results: [NutritionalFoodItem] = []
                for item in aiMeal.items {
                    let portion = max(1.0, item.portionGrams)
                    let calPer100g = (item.calories / portion) * 100.0
                    let protPer100g = (item.protein / portion) * 100.0
                    let carbPer100g = (item.carbohydrates / portion) * 100.0
                    let fatPer100g = (item.fat / portion) * 100.0
                    
                    let foodItem = NutritionalFoodItem(
                        name: item.name,
                        brand: "",
                        caloriesPer100g: calPer100g,
                        proteinPer100g: protPer100g,
                        carbsPer100g: carbPer100g,
                        fatPer100g: fatPer100g,
                        sourceTag: "Base Nutricional"
                    )
                    results.append(foodItem)
                }
                if !results.isEmpty {
                    return results
                }
            } catch {
                print("⚠️ [NutritionService] Consulta fallback falhou: \(error.localizedDescription)")
            }
        }
        
        // Se nada for encontrado, retorna vazio (nunca gera substituições aleatórias)
        return []
    }
    
    private func isPureMeasure(_ text: String) -> Bool {
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
}

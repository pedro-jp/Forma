//
//  FoodParserAndPortionTests.swift
//  FormaTests
//

import XCTest
@testable import Forma

@MainActor
final class FoodParserAndPortionTests: XCTestCase {

    func testLocalTacoDatabaseSearchAndAliases() {
        let chickenMatches = NutritionService.shared.searchLocal(query: "peito de frango grelhado")
        XCTAssertFalse(chickenMatches.isEmpty)
        XCTAssertEqual(chickenMatches.first?.name, "Peito de Frango Grelhado")
        XCTAssertEqual(chickenMatches.first?.defaultUnit, "g")

        let mmMatches = NutritionService.shared.searchLocal(query: "mms")
        XCTAssertFalse(mmMatches.isEmpty)
        XCTAssertTrue(mmMatches.first?.name.contains("M&M") == true)
    }

    func testPlateItemPortionScalingAndClampingForSolidsAndLiquids() {
        var solidChicken = VoicePlateItem(
            name: "Peito de Frango Grelhado",
            portionValue: 100.0,
            unit: "g",
            isLiquid: false,
            calories: 165.0,
            protein: 31.0,
            carbs: 0.0,
            fat: 3.6
        )

        solidChicken.updatePortion(200.0)
        XCTAssertEqual(solidChicken.portionValue, 200.0, accuracy: 0.01)
        XCTAssertEqual(solidChicken.calories, 330.0, accuracy: 0.1)
        XCTAssertEqual(solidChicken.protein, 62.0, accuracy: 0.1)
        XCTAssertEqual(solidChicken.fat, 7.2, accuracy: 0.1)

        // Sólidos têm piso mínimo de 5g
        solidChicken.updatePortion(-20.0)
        XCTAssertEqual(solidChicken.portionValue, 5.0, accuracy: 0.01)

        var liquidMilk = VoicePlateItem(
            name: "Leite Integral",
            portionValue: 200.0,
            unit: "ml",
            isLiquid: true,
            calories: 120.0,
            protein: 6.4,
            carbs: 9.6,
            fat: 6.4
        )

        liquidMilk.updatePortion(300.0)
        XCTAssertEqual(liquidMilk.portionValue, 300.0, accuracy: 0.01)
        XCTAssertEqual(liquidMilk.calories, 180.0, accuracy: 0.1)

        // Líquidos têm piso mínimo de 25ml
        liquidMilk.updatePortion(0.0)
        XCTAssertEqual(liquidMilk.portionValue, 25.0, accuracy: 0.01)
    }

    func testErrorHumanizationAndMarkdownJsonStripping() {
        let raw503 = """
        API do Gemini (gemini-3.1-flash-lite - HTTP 503): {
          "error": {
            "code": 503,
            "message": "This model is currently experiencing high demand.",
            "status": "UNAVAILABLE"
          }
        }
        """

        let humanized = FormaAPIClient.humanizeErrorMessage(raw503, statusCode: 503)
        XCTAssertFalse(humanized.contains("\"error\""))
        XCTAssertFalse(humanized.contains("UNAVAILABLE"))
        XCTAssertTrue(humanized.contains("alta demanda"))

        let legacyBrandError = FormaAPIClient.humanizeErrorMessage("Erro temporário no Forma com IA Gemini.", statusCode: 500)
        XCTAssertEqual(legacyBrandError, "Erro temporário no Forma com Inteligência Forma.")

        let fencedJson = "```json\n{\"meal_type\":\"Almoço\"}\n```"
        XCTAssertEqual(GeminiAIService.stripMarkdownCodeFences(fencedJson), "{\"meal_type\":\"Almoço\"}")
    }

    func testSmartFoodSuggestionsPrioritizeWeekdayHabitAndRecentConsumptionPerMealType() {
        var calendar = Calendar(identifier: .gregorian)
        calendar.timeZone = TimeZone(secondsFromGMT: 0)!

        // 2026-11-04 é Quarta-feira (weekday = 4)
        let wednesdayReference = calendar.date(from: DateComponents(year: 2026, month: 11, day: 4, hour: 12, minute: 30))!
        let prevWednesday1 = calendar.date(byAdding: .day, value: -7, to: wednesdayReference)!
        let prevWednesday2 = calendar.date(byAdding: .day, value: -14, to: wednesdayReference)!
        let yesterdayTuesday = calendar.date(byAdding: .day, value: -1, to: wednesdayReference)!
        let twoDaysAgoMonday = calendar.date(byAdding: .day, value: -2, to: wednesdayReference)!

        let entries: [FoodEntry] = [
            // Hábito forte de Quarta-feira no Almoço
            FoodEntry(name: "Feijoada Magra", mealType: .lunch, portionGrams: 300, calories: 420, protein: 32, carbohydrates: 38, fat: 14, date: prevWednesday1),
            FoodEntry(name: "Feijoada Magra", mealType: .lunch, portionGrams: 300, calories: 420, protein: 32, carbohydrates: 38, fat: 14, date: prevWednesday2),

            // Consumo muito recente no Almoço (Seg e Ter)
            FoodEntry(name: "Tilápia Grelhada", mealType: .lunch, portionGrams: 200, calories: 256, protein: 52, carbohydrates: 0, fat: 5, date: yesterdayTuesday),
            FoodEntry(name: "Tilápia Grelhada", mealType: .lunch, portionGrams: 200, calories: 256, protein: 52, carbohydrates: 0, fat: 5, date: twoDaysAgoMonday),

            // Café da manhã frequente NÃO deve invadir sugestões de Almoço
            FoodEntry(name: "Pão de Queijo", mealType: .breakfast, portionGrams: 100, calories: 350, protein: 8, carbohydrates: 32, fat: 21, date: wednesdayReference),
            FoodEntry(name: "Pão de Queijo", mealType: .breakfast, portionGrams: 100, calories: 350, protein: 8, carbohydrates: 32, fat: 21, date: yesterdayTuesday),
            FoodEntry(name: "Pão de Queijo", mealType: .breakfast, portionGrams: 100, calories: 350, protein: 8, carbohydrates: 32, fat: 21, date: prevWednesday1)
        ]

        let lunchSuggestions = SmartFoodSuggestionEngine.suggestions(
            from: entries,
            for: .lunch,
            referenceDate: wednesdayReference,
            calendar: calendar,
            limit: 6
        )

        XCTAssertFalse(lunchSuggestions.isEmpty)
        XCTAssertFalse(lunchSuggestions.contains { $0.name == "Pão de Queijo" })

        let topTwoNames = Array(lunchSuggestions.prefix(2).map(\.name))
        XCTAssertTrue(topTwoNames.contains("Feijoada Magra"))
        XCTAssertTrue(topTwoNames.contains("Tilápia Grelhada"))

        let feijoada = lunchSuggestions.first { $0.name == "Feijoada Magra" }
        XCTAssertNotNil(feijoada)
        XCTAssertTrue(feijoada?.reasonBadge.localizedCaseInsensitiveContains("quarta") == true)
    }

    func testAIDataConsentManagerIsolatesPerUserAndGatesExternalAI() async {
        let userDefaults = UserDefaults.standard
        let previousUserId = userDefaults.string(forKey: "forma_user_id")
        let userA = "test-consent-a-\(UUID().uuidString)"
        let userB = "test-consent-b-\(UUID().uuidString)"

        defer {
            userDefaults.removeObject(forKey: "forma_ai_third_party_consent_v1_\(userA.lowercased())")
            userDefaults.removeObject(forKey: "forma_ai_third_party_consent_v1_\(userB.lowercased())")
            if let previousUserId {
                userDefaults.set(previousUserId, forKey: "forma_user_id")
            } else {
                userDefaults.removeObject(forKey: "forma_user_id")
            }
            AIDataConsentManager.shared.refreshForCurrentUser()
        }

        userDefaults.set(userA, forKey: "forma_user_id")
        AIDataConsentManager.shared.refreshForCurrentUser()
        XCTAssertFalse(AIDataConsentManager.shared.hasGrantedConsent)
        XCTAssertFalse(AIDataConsentManager.isConsentGranted)

        // Sem consentimento, analyzeWorkout deve ser bloqueado antes de qualquer rede
        do {
            _ = try await GeminiAIService.shared.analyzeWorkout(description: "Supino reto 4x10", userWeight: 75.0)
            XCTFail("Deveria exigir consentimento explícito antes de enviar dados para IA externa")
        } catch let error as NSError {
            XCTAssertEqual(error.code, 403)
        }

        // Sem consentimento, suggestInitialGoals deve calcular localmente via Mifflin-St Jeor sem falhar
        let localGoals = await GeminiAIService.shared.suggestInitialGoals(
            userName: "Atleta",
            goal: .gain,
            currentWeight: 74.0,
            heightMeters: 1.78,
            targetWeight: nil,
            activityLevel: "Moderado (3-4x/semana)"
        )
        XCTAssertGreaterThanOrEqual(localGoals.dailyCalories, 1000)

        // Concede para User A e garante que User B começa sem consentimento
        AIDataConsentManager.shared.setConsent(true)
        XCTAssertTrue(AIDataConsentManager.shared.hasGrantedConsent)
        XCTAssertTrue(AIDataConsentManager.isConsentGranted)

        userDefaults.set(userB, forKey: "forma_user_id")
        AIDataConsentManager.shared.refreshForCurrentUser()
        XCTAssertFalse(AIDataConsentManager.shared.hasGrantedConsent)
        XCTAssertFalse(AIDataConsentManager.isConsentGranted)

        // Retorna para User A e testa revogação/limpeza
        userDefaults.set(userA, forKey: "forma_user_id")
        AIDataConsentManager.shared.refreshForCurrentUser()
        XCTAssertTrue(AIDataConsentManager.shared.hasGrantedConsent)

        AIDataConsentManager.shared.clearConsentForCurrentUser()
        XCTAssertFalse(AIDataConsentManager.shared.hasGrantedConsent)
        XCTAssertFalse(AIDataConsentManager.isConsentGranted)
    }
}

package com.forma.app.data.services

import com.forma.app.data.model.MealType
import java.util.UUID

/** Fases de carregamento da análise nutricional com transição suave (iOS: `AnalysisPhase`). */
enum class AnalysisPhase(val label: String) {
    IDLE("idle"),
    TACO_DATABASE("Base Forma"),
    ARTIFICIAL_INTELLIGENCE("Inteligência Forma"),
    PHOTO_VISION("Visão Forma")
}

/** Modo de escuta por voz. */
enum class VoiceListeningMode { FOOD, WORKOUT }

/** Estado das permissões de voz e microfone. */
enum class VoicePermissionStatus { NOT_DETERMINED, AUTHORIZED, SPEECH_DENIED, MICROPHONE_DENIED, RESTRICTED }

/**
 * Item individual do prato de voz (iOS: `VoicePlateItem`).
 * Imutável: [updatePortion] devolve uma cópia recalculada a partir dos valores por 100 g/ml.
 * Crie com [VoicePlateItem.create].
 */
data class VoicePlateItem(
    val id: String,
    val name: String,
    val brand: String,
    val portionValue: Double,
    val unit: String,
    val isLiquid: Boolean,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val source: String,
    val baseCaloriesPer100: Double,
    val baseProteinPer100: Double,
    val baseCarbsPer100: Double,
    val baseFatPer100: Double
) {
    /** Recalcula macros para a nova porção (passo mínimo: 5 g, ou 25 ml para líquidos). */
    fun updatePortion(newPortion: Double): VoicePlateItem {
        val stepMin = if (isLiquid) 25.0 else 5.0
        val clamped = maxOf(stepMin, newPortion)
        val factor = clamped / 100.0
        fun r(v: Double) = swiftRound(v * 10) / 10
        return copy(
            portionValue = clamped,
            calories = r(baseCaloriesPer100 * factor),
            protein = r(baseProteinPer100 * factor),
            carbs = r(baseCarbsPer100 * factor),
            fat = r(baseFatPer100 * factor)
        )
    }

    companion object {
        fun create(
            id: String = UUID.randomUUID().toString(),
            name: String,
            brand: String = "",
            portionValue: Double,
            unit: String = "g",
            isLiquid: Boolean = false,
            calories: Double,
            protein: Double,
            carbs: Double,
            fat: Double,
            source: String = "TACO Brasil"
        ): VoicePlateItem {
            val p = maxOf(1.0, portionValue)
            val factor = p / 100.0
            return VoicePlateItem(
                id = id, name = name, brand = brand, portionValue = p, unit = unit, isLiquid = isLiquid,
                calories = calories, protein = protein, carbs = carbs, fat = fat, source = source,
                baseCaloriesPer100 = calories / factor,
                baseProteinPer100 = protein / factor,
                baseCarbsPer100 = carbs / factor,
                baseFatPer100 = fat / factor
            )
        }
    }
}

/** Dados extraídos de uma fala/foto (suporta pratos com múltiplos itens) (iOS: `VoiceFoodIntent`). */
data class VoiceFoodIntent(
    val rawText: String,
    val mealType: MealType,
    val items: List<VoicePlateItem> = emptyList(),
    val aiSummary: String = ""
) {
    val totalCalories: Double get() = items.sumOf { it.calories }
    val totalProtein: Double get() = items.sumOf { it.protein }
    val totalCarbs: Double get() = items.sumOf { it.carbs }
    val totalFat: Double get() = items.sumOf { it.fat }

    // Propriedades para compatibilidade com código existente
    val foodQuery: String get() = items.joinToString(", ") { it.name }
    val portionGrams: Double get() = items.firstOrNull()?.portionValue ?: 100.0
    val matchedFood: NutritionalFoodItem?
        get() = items.firstOrNull()?.let {
            NutritionalFoodItem(
                name = it.name, brand = it.brand,
                caloriesPer100g = it.baseCaloriesPer100, proteinPer100g = it.baseProteinPer100,
                carbsPer100g = it.baseCarbsPer100, fatPer100g = it.baseFatPer100
            )
        }
}

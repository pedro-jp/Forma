package com.forma.app.data.services

import com.forma.app.data.model.FoodEntry
import com.forma.app.data.model.MealType
import java.text.Normalizer
import java.util.Calendar
import java.util.Date

/** Sugestão alimentar inteligente (iOS: `SmartFoodSuggestion`). */
data class SmartFoodSuggestion(
    val id: String,
    val name: String,
    val brand: String,
    val mealType: MealType,
    val portionValue: Double,
    val unit: String,
    val isLiquid: Boolean,
    val calories: Double,
    val protein: Double,
    val carbs: Double,
    val fat: Double,
    val reasonBadge: String,
    val isFromHistory: Boolean
) {
    fun toPlateItem(): VoicePlateItem = VoicePlateItem.create(
        name = name,
        brand = brand,
        portionValue = portionValue,
        unit = unit,
        isLiquid = isLiquid,
        calories = calories,
        protein = protein,
        carbs = carbs,
        fat = fat,
        source = if (isFromHistory) "Histórico" else "TACO Brasil"
    )
}

/**
 * Motor de recomendações alimentares por tipo de refeição, dia da semana e histórico recente.
 * Porta de `SmartFoodSuggestionEngine` (iOS).
 */
object SmartFoodSuggestionEngine {

    private class AggregatedFood(
        val key: String,
        var latestEntry: FoodEntry,
        var totalPortion: Double = 0.0,
        var totalCount: Int = 0,
        var weekdayCount: Int = 0,
        var last7DaysCount: Int = 0,
        var last14DaysCount: Int = 0
    )

    /**
     * Prioriza: 1) o que o usuário mais consome nesta refeição neste mesmo dia da semana;
     * 2) o que mais consome recentemente nesta refeição; 3) sugestões base quando há pouco histórico (< 4 itens).
     */
    fun suggestions(
        entries: List<FoodEntry>,
        mealType: MealType,
        referenceDate: Date = Date(),
        limit: Int = 5
    ): List<SmartFoodSuggestion> {
        val cal = Calendar.getInstance().apply { time = referenceDate }
        val targetWeekday = cal.get(Calendar.DAY_OF_WEEK)
        val weekdayTag = weekdayHabitLabel(targetWeekday)
        val mealLower = mealType.title.lowercase()

        val sevenDaysAgo = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -7) }.timeInMillis
        val fourteenDaysAgo = (cal.clone() as Calendar).apply { add(Calendar.DAY_OF_YEAR, -14) }.timeInMillis

        val matching = entries.filter { it.mealType == mealType }
        val grouped = LinkedHashMap<String, AggregatedFood>()
        val entryCal = Calendar.getInstance()

        for (entry in matching) {
            val key = normalizeKey(entry.name)
            if (key.isEmpty()) continue

            entryCal.timeInMillis = entry.consumedAt
            val isSameWeekday = entryCal.get(Calendar.DAY_OF_WEEK) == targetWeekday
            val isLast7 = entry.consumedAt >= sevenDaysAgo
            val isLast14 = entry.consumedAt >= fourteenDaysAgo

            val existing = grouped[key]
            if (existing != null) {
                if (entry.consumedAt > existing.latestEntry.consumedAt) existing.latestEntry = entry
                existing.totalPortion += maxOf(1.0, entry.portionGrams)
                existing.totalCount += 1
                if (isSameWeekday) existing.weekdayCount += 1
                if (isLast7) existing.last7DaysCount += 1
                if (isLast14) existing.last14DaysCount += 1
            } else {
                grouped[key] = AggregatedFood(
                    key = key,
                    latestEntry = entry,
                    totalPortion = maxOf(1.0, entry.portionGrams),
                    totalCount = 1,
                    weekdayCount = if (isSameWeekday) 1 else 0,
                    last7DaysCount = if (isLast7) 1 else 0,
                    last14DaysCount = if (isLast14) 1 else 0
                )
            }
        }

        val allAggregated = grouped.values.toList()

        // 1. Hábitos do dia da semana atual nesta refeição
        val weekdayFavorites = allAggregated
            .filter { it.weekdayCount > 0 }
            .sortedWith(
                compareByDescending<AggregatedFood> { it.weekdayCount }
                    .thenByDescending { it.last14DaysCount }
                    .thenByDescending { it.totalCount }
                    .thenByDescending { it.latestEntry.consumedAt }
            )

        // 2. O que mais está consumindo recentemente / com frequência nesta refeição
        val recentFavorites = allAggregated.sortedWith(
            compareByDescending<AggregatedFood> { (it.last7DaysCount * 5) + (it.last14DaysCount * 3) + it.totalCount }
                .thenByDescending { it.latestEntry.consumedAt }
        )

        val selectedKeys = HashSet<String>()
        val results = ArrayList<SmartFoodSuggestion>()

        val maxWeekdaySlots = maxOf(2, (limit + 1) / 2)
        for (item in weekdayFavorites.take(maxWeekdaySlots)) {
            if (results.size >= limit) break
            selectedKeys.add(item.key)
            results.add(buildSuggestion(item, mealType, weekdayTag))
        }

        for (item in recentFavorites) {
            if (results.size >= limit) break
            if (item.key in selectedKeys) continue
            selectedKeys.add(item.key)
            val badge = if (item.last14DaysCount > 0) "Recente no $mealLower" else "Frequente no $mealLower"
            results.add(buildSuggestion(item, mealType, badge))
        }

        // 3. Fallback contextual apenas se o usuário tiver menos de 4 itens históricos na refeição
        if (results.size < minOf(4, limit)) {
            for (fallback in defaultSuggestions(mealType)) {
                if (results.size >= minOf(4, limit)) break
                val key = normalizeKey(fallback.name)
                if (key in selectedKeys) continue
                selectedKeys.add(key)
                results.add(fallback)
            }
        }
        return results
    }

    private fun buildSuggestion(item: AggregatedFood, mealType: MealType, badge: String): SmartFoodSuggestion {
        val latest = item.latestEntry
        val avgPortion = maxOf(5.0, item.totalPortion / maxOf(1, item.totalCount))
        val roundedPortion = swiftRound(avgPortion / 5.0) * 5.0
        val finalPortion = maxOf(5.0, roundedPortion)
        val basePortion = maxOf(1.0, latest.portionGrams)
        val scale = finalPortion / basePortion
        val liquid = isLikelyLiquid(latest.name, latest.brand)
        fun r(v: Double) = swiftRound(v * 10.0) / 10.0

        return SmartFoodSuggestion(
            id = "hist_${mealType.title}_${item.key}",
            name = latest.name,
            brand = latest.brand,
            mealType = mealType,
            portionValue = finalPortion,
            unit = if (liquid) "ml" else "g",
            isLiquid = liquid,
            calories = r(latest.calories * scale),
            protein = r(latest.protein * scale),
            carbs = r(latest.carbohydrates * scale),
            fat = r(latest.fat * scale),
            reasonBadge = badge,
            isFromHistory = true
        )
    }

    private fun normalizeKey(raw: String): String =
        Normalizer.normalize(raw, Normalizer.Form.NFD)
            .replace(Regex("\\p{Mn}+"), "")
            .lowercase()
            .trim()

    /** weekday no padrão `Calendar.DAY_OF_WEEK` (1 = domingo ... 7 = sábado), igual ao Swift. */
    fun weekdayHabitLabel(weekday: Int): String = when (weekday) {
        1 -> "Comum no domingo"
        2 -> "Comum na segunda"
        3 -> "Comum na terça"
        4 -> "Comum na quarta"
        5 -> "Comum na quinta"
        6 -> "Comum na sexta"
        7 -> "Comum no sábado"
        else -> "Hábito do dia"
    }

    private fun isLikelyLiquid(name: String, brand: String): Boolean {
        val lower = "$name $brand".lowercase()
        val keywords = listOf("ml", "suco", "leite", "shake", "vitamina", "café", "cafe", "refrigerante", "água", "agua", "iogurte líquido", "whey")
        return keywords.any { lower.contains(it) }
    }

    private fun s(
        id: String, name: String, brand: String, meal: MealType, portion: Double, unit: String, liquid: Boolean,
        cal: Double, prot: Double, carbs: Double, fat: Double, badge: String
    ) = SmartFoodSuggestion(id, name, brand, meal, portion, unit, liquid, cal, prot, carbs, fat, badge, false)

    /** Sugestões base por refeição (idênticas ao iOS). */
    fun defaultSuggestions(mealType: MealType): List<SmartFoodSuggestion> = when (mealType) {
        MealType.BREAKFAST -> listOf(
            s("def_bf_1", "Ovos mexidos", "TACO", MealType.BREAKFAST, 100.0, "g", false, 148.0, 13.0, 1.2, 10.0, "Café da Manhã"),
            s("def_bf_2", "Pão francês", "TACO", MealType.BREAKFAST, 50.0, "g", false, 150.0, 4.0, 29.3, 1.5, "Café da Manhã"),
            s("def_bf_3", "Pão de queijo", "TACO", MealType.BREAKFAST, 100.0, "g", false, 350.0, 8.0, 32.0, 21.0, "Café da Manhã"),
            s("def_bf_4", "Banana prata", "TACO", MealType.BREAKFAST, 90.0, "g", false, 89.0, 1.2, 23.4, 0.1, "Café da Manhã")
        )
        MealType.LUNCH -> listOf(
            s("def_lu_1", "Arroz branco cozido", "TACO", MealType.LUNCH, 150.0, "g", false, 192.0, 3.8, 42.2, 0.3, "Almoço"),
            s("def_lu_2", "Feijão carioca cozido", "TACO", MealType.LUNCH, 100.0, "g", false, 76.0, 4.8, 13.6, 0.5, "Almoço"),
            s("def_lu_3", "Peito de frango grelhado", "TACO", MealType.LUNCH, 150.0, "g", false, 239.0, 48.0, 0.0, 3.8, "Almoço"),
            s("def_lu_4", "Patinho moído refogado", "TACO", MealType.LUNCH, 130.0, "g", false, 285.0, 46.7, 0.0, 9.5, "Almoço")
        )
        MealType.DINNER -> listOf(
            s("def_di_1", "Peito de frango grelhado", "TACO", MealType.DINNER, 150.0, "g", false, 239.0, 48.0, 0.0, 3.8, "Jantar"),
            s("def_di_2", "Arroz branco cozido", "TACO", MealType.DINNER, 120.0, "g", false, 154.0, 3.0, 33.7, 0.2, "Jantar"),
            s("def_di_3", "Omelete simples", "TACO", MealType.DINNER, 120.0, "g", false, 198.0, 15.6, 1.8, 14.2, "Jantar"),
            s("def_di_4", "Batata doce cozida", "TACO", MealType.DINNER, 130.0, "g", false, 100.0, 0.8, 23.9, 0.1, "Jantar")
        )
        MealType.SNACKS -> listOf(
            s("def_sn_1", "Whey Protein com água", "Suplemento", MealType.SNACKS, 250.0, "ml", true, 120.0, 24.0, 3.0, 1.5, "Lanche"),
            s("def_sn_2", "Iogurte natural integral", "TACO", MealType.SNACKS, 170.0, "g", false, 104.0, 6.8, 9.9, 5.1, "Lanche"),
            s("def_sn_3", "Banana com aveia", "TACO", MealType.SNACKS, 120.0, "g", false, 205.0, 5.4, 43.0, 2.3, "Lanche"),
            s("def_sn_4", "Pão de queijo", "TACO", MealType.SNACKS, 80.0, "g", false, 280.0, 6.4, 25.6, 16.8, "Lanche")
        )
    }
}

package com.forma.app.data.services

import com.forma.app.data.catalog.ExerciseCatalog
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import java.util.UUID

// region Helpers de decodificação tolerante (equivalentes aos `try? decodeIfPresent` do iOS)

internal fun JsonObject.str(key: String): String? {
    val e = get(key) ?: return null
    return if (e.isJsonPrimitive) e.asString else null
}

internal fun JsonObject.dbl(key: String): Double? {
    val e = get(key) ?: return null
    if (!e.isJsonPrimitive) return null
    return runCatching { e.asDouble }.getOrNull()
}

internal fun JsonObject.int(key: String): Int? = dbl(key)?.toInt()

internal fun JsonObject.bool(key: String): Boolean? {
    val e = get(key) ?: return null
    if (!e.isJsonPrimitive) return null
    val p = e.asJsonPrimitive
    return when {
        p.isBoolean -> p.asBoolean
        p.isString -> p.asString.lowercase().let { if (it == "true") true else if (it == "false") false else null }
        else -> null
    }
}

internal fun JsonObject.arr(key: String): JsonArray? = get(key)?.takeIf { it.isJsonArray }?.asJsonArray
internal fun JsonObject.obj(key: String): JsonObject? = get(key)?.takeIf { it.isJsonObject }?.asJsonObject

internal fun JsonElement.asObjectOrNull(): JsonObject? = if (isJsonObject) asJsonObject else null

// endregion

// region Alimentos

/**
 * Item de alimento extraído pela IA (iOS: `AIExtractedFoodItem`).
 * JSON do backend: name, portion_value | portion_grams, unit, is_liquid, calories, protein, carbohydrates, fat.
 */
data class AIExtractedFoodItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val portionValue: Double,
    val unit: String = "g",
    val isLiquid: Boolean = false,
    val calories: Double,
    val protein: Double,
    val carbohydrates: Double,
    val fat: Double
) {
    /** Compatibilidade com código existente. */
    val portionGrams: Double get() = portionValue

    companion object {
        private val DRINK_KEYWORDS = listOf(
            "leite", "suco", "shake", "café", "cafe", "refrigerante", "água", "agua", "vitamina", "bebida"
        )

        fun fromJson(o: JsonObject): AIExtractedFoodItem {
            val name = o.str("name") ?: throw AiServiceException(AiErrorKind.INVALID_RESPONSE, AiMessages.INVALID_RESPONSE, 500)
            val portion = o.dbl("portion_value") ?: o.dbl("portion_grams") ?: 100.0
            val u = o.str("unit")?.lowercase()
            val liq = o.bool("is_liquid")
            val isLiquid: Boolean
            val unit: String
            if (liq != null) {
                isLiquid = liq
                unit = u ?: if (liq) "ml" else "g"
            } else if (u != null && (u == "ml" || u == "mls")) {
                isLiquid = true
                unit = "ml"
            } else {
                val lower = name.lowercase()
                val isDrink = DRINK_KEYWORDS.any { lower.contains(it) }
                isLiquid = isDrink
                unit = if (isDrink) "ml" else "g"
            }
            return AIExtractedFoodItem(
                name = name,
                portionValue = portion,
                unit = unit,
                isLiquid = isLiquid,
                calories = o.dbl("calories") ?: 0.0,
                protein = o.dbl("protein") ?: 0.0,
                carbohydrates = o.dbl("carbohydrates") ?: 0.0,
                fat = o.dbl("fat") ?: 0.0
            )
        }
    }
}

/** Refeição extraída pela IA (iOS: `AIExtractedMeal`). `mealType` é o título pt-BR ("Almoço", "Café da Manhã"...). */
data class AIExtractedMeal(
    val mealType: String,
    val items: List<AIExtractedFoodItem>,
    val totalCalories: Double,
    val totalProtein: Double,
    val totalCarbs: Double,
    val totalFat: Double,
    val aiSummary: String = ""
) {
    companion object {
        fun fromJson(o: JsonObject): AIExtractedMeal {
            val items = o.arr("items")?.mapNotNull { it.asObjectOrNull() }?.map { AIExtractedFoodItem.fromJson(it) }
                ?: emptyList()
            return AIExtractedMeal(
                mealType = o.str("meal_type") ?: "Almoço",
                items = items,
                totalCalories = o.dbl("total_calories") ?: items.sumOf { it.calories },
                totalProtein = o.dbl("total_protein") ?: items.sumOf { it.protein },
                totalCarbs = o.dbl("total_carbs") ?: items.sumOf { it.carbohydrates },
                totalFat = o.dbl("total_fat") ?: items.sumOf { it.fat },
                aiSummary = o.str("ai_summary") ?: ""
            )
        }
    }
}

// endregion

// region Treinos

/**
 * Exercício de uma ficha/sessão (iOS: `WorkoutExerciseItem`). Mesmo JSON snake_case do iOS
 * (`exercisesJson` das rotinas), inclusive `animation_id` (id do [ExerciseCatalog]).
 */
data class WorkoutExercisePlan(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sets: Int = 3,
    val reps: String = "10-12",
    val suggestedWeight: String = "",
    val targetMuscle: String = "",
    val executionTip: String = "",
    val restSeconds: Int = 90,
    val completedSets: List<Boolean> = List(maxOf(1, sets)) { false },
    val setWeights: List<Double> = emptyList(),
    val animationId: String? = null
) {
    val isAllSetsCompleted: Boolean get() = completedSets.isNotEmpty() && completedSets.all { it }
    val completedCount: Int get() = completedSets.count { it }

    fun weightForSet(setIndex: Int): Double {
        if (setIndex in setWeights.indices && setWeights[setIndex] > 0.0) {
            return setWeights[setIndex]
        }
        val firstValid = setWeights.firstOrNull { it > 0.0 }
        if (firstValid != null) return firstValid
        return parseWeightKg(suggestedWeight) ?: recommendedDefaultWeightKg(name, targetMuscle)
    }

    val averageWeightKg: Double
        get() {
            val completedWeights = (0 until sets).mapNotNull { idx ->
                val done = completedSets.getOrElse(idx) { false }
                if (done) weightForSet(idx).takeIf { it > 0.0 } else null
            }
            if (completedWeights.isNotEmpty()) {
                val avg = completedWeights.average()
                return kotlin.math.round(avg * 2.0) / 2.0
            }
            val allWeights = (0 until sets).map { weightForSet(it) }.filter { it > 0.0 }
            if (allWeights.isNotEmpty()) {
                val avg = allWeights.average()
                return kotlin.math.round(avg * 2.0) / 2.0
            }
            return 0.0
        }

    /** Preenche `animationId`/`executionTip` via [ExerciseCatalog] quando ausentes ou inválidos (iOS: `autoAssociateExercises`). */
    fun withCatalogAssociation(): WorkoutExercisePlan {
        if (!animationId.isNullOrEmpty() && ExerciseCatalog.item(animationId) != null) return this
        val match = ExerciseCatalog.guaranteedMatch(name, targetMuscle)
        return copy(
            animationId = match.id,
            targetMuscle = targetMuscle.ifEmpty { match.muscleGroup },
            executionTip = executionTip.ifEmpty { match.concentricInstruction }
        )
    }

    fun toJson(): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("name", name)
        addProperty("sets", sets)
        addProperty("reps", reps)
        addProperty("suggested_weight", suggestedWeight)
        addProperty("target_muscle", targetMuscle)
        addProperty("execution_tip", executionTip)
        addProperty("rest_seconds", restSeconds)
        add("completed_sets", JsonArray().also { a -> completedSets.forEach { a.add(it) } })
        if (setWeights.isNotEmpty()) {
            add("set_weights", JsonArray().also { a -> setWeights.forEach { a.add(it) } })
        }
        animationId?.let { addProperty("animation_id", it) }
    }

    companion object {
        fun parseWeightKg(raw: String?): Double? {
            if (raw.isNullOrBlank()) return null
            val cleaned = raw.lowercase().replace(",", ".")
            if (cleaned.contains("peso corporal") || cleaned.contains("livre")) return null
            val match = Regex("(\\d+(?:\\.\\d+)?)").find(cleaned) ?: return null
            return match.groupValues[1].toDoubleOrNull()?.takeIf { it > 0.0 && it <= 500.0 }
        }

        fun formatWeightKg(kg: Double): String {
            val rounded = kotlin.math.round(kg * 2.0) / 2.0
            return if (rounded % 1.0 == 0.0) {
                "${rounded.toInt()} kg"
            } else {
                String.format(java.util.Locale.US, "%.1f kg", rounded).replace(".", ",")
            }
        }

        fun normalizedExerciseKey(name: String): String =
            java.text.Normalizer.normalize(name, java.text.Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase()
                .replace(Regex("[^a-z0-9]+"), "_")
                .trim('_')

        fun recommendedDefaultWeightKg(
            exerciseName: String,
            targetMuscle: String = "",
            userBodyWeightKg: Double = 75.0
        ): Double {
            val bw = userBodyWeightKg.coerceIn(45.0, 140.0)
            val norm = normalizedExerciseKey("$exerciseName $targetMuscle")
            val ratio = when {
                norm.contains("leg_press") -> 1.40
                norm.contains("agachamento") || norm.contains("terra") || norm.contains("stiff") || norm.contains("hip_thrust") || norm.contains("elevacao_pelvica") -> 0.55
                norm.contains("supino") || norm.contains("remada") || norm.contains("puxada") || norm.contains("pulldown") -> 0.42
                norm.contains("extensora") || norm.contains("flexora") || norm.contains("panturrilha") -> 0.45
                norm.contains("desenvolvimento") || norm.contains("ombro") || norm.contains("crucifixo") || norm.contains("crossover") || norm.contains("voador") -> 0.22
                norm.contains("elevacao_lateral") || norm.contains("elevacao_frontal") -> 0.11
                norm.contains("rosca") || norm.contains("biceps") || norm.contains("triceps") || norm.contains("testa") || norm.contains("corda") -> 0.18
                norm.contains("abdominal") || norm.contains("prancha") || norm.contains("flexao") -> 0.14
                else -> 0.25
            }
            val rawKg = maxOf(4.0, bw * ratio)
            return kotlin.math.round(rawKg / 2.0) * 2.0
        }

        fun fromJson(o: JsonObject): WorkoutExercisePlan {
            val sets = maxOf(1, o.int("sets") ?: 3)
            val rest = o.int("rest_seconds") ?: 90
            val completed = o.arr("completed_sets")?.map { runCatching { it.asBoolean }.getOrDefault(false) }
                ?: List(sets) { false }
            val weights = o.arr("set_weights")?.mapNotNull { runCatching { it.asDouble }.getOrNull() }
                ?: emptyList()
            return WorkoutExercisePlan(
                id = o.str("id") ?: UUID.randomUUID().toString(),
                name = o.str("name") ?: "Exercício",
                sets = sets,
                reps = (o.str("reps") ?: "10-12").ifEmpty { "10-12" },
                suggestedWeight = o.str("suggested_weight") ?: "",
                targetMuscle = o.str("target_muscle") ?: "",
                executionTip = o.str("execution_tip") ?: "",
                restSeconds = if (rest > 0) rest else 90,
                completedSets = completed,
                setWeights = weights,
                animationId = o.str("animation_id")
            ).withCatalogAssociation()
        }

        fun listFromJson(json: String?): List<WorkoutExercisePlan> {
            if (json.isNullOrBlank()) return emptyList()
            return runCatching {
                com.google.gson.JsonParser.parseString(json).asJsonArray
                    .mapNotNull { it.asObjectOrNull() }
                    .map { fromJson(it) }
            }.getOrDefault(emptyList())
        }

        fun listToJson(list: List<WorkoutExercisePlan>): String =
            JsonArray().also { a -> list.forEach { a.add(it.toJson()) } }.toString()
    }
}

/** Treino analisado pela IA (iOS: `AIExtractedWorkout`). */
data class AIExtractedWorkout(
    val title: String,
    val workoutType: String,
    val durationMinutes: Int,
    val caloriesBurned: Double,
    val intensity: String,
    val targetMuscles: String = "",
    val hypertrophyFocus: String = "",
    val recommendedRestSeconds: Int = 90,
    val aiFeedback: String,
    val exercises: List<WorkoutExercisePlan> = emptyList()
) {
    companion object {
        fun fromJson(o: JsonObject): AIExtractedWorkout = AIExtractedWorkout(
            title = o.str("title") ?: "Treino",
            workoutType = o.str("workout_type") ?: "Musculação",
            durationMinutes = o.int("duration_minutes") ?: 45,
            caloriesBurned = o.dbl("calories_burned") ?: 300.0,
            intensity = o.str("intensity") ?: "Moderada",
            targetMuscles = o.str("target_muscles") ?: "",
            hypertrophyFocus = o.str("hypertrophy_focus") ?: "",
            recommendedRestSeconds = o.int("recommended_rest_seconds") ?: 90,
            aiFeedback = o.str("ai_feedback") ?: "",
            exercises = o.arr("exercises")?.mapNotNull { it.asObjectOrNull() }?.map { WorkoutExercisePlan.fromJson(it) }
                ?: emptyList()
        )
    }
}

// endregion

// region Evolução e metas

/** Metas nutricionais sugeridas pela IA (iOS: `SuggestedNutritionTargets`). */
data class AISuggestedTargets(
    val dailyCalories: Double = 2800.0,
    val proteinGrams: Double = 160.0,
    val carbsGrams: Double = 380.0,
    val fatGrams: Double = 70.0,
    val waterMilliliters: Int = 2500,
    val explanation: String = ""
) {
    companion object {
        fun fromJson(o: JsonObject) = AISuggestedTargets(
            dailyCalories = o.dbl("daily_calories") ?: 2800.0,
            proteinGrams = o.dbl("protein_grams") ?: 160.0,
            carbsGrams = o.dbl("carbs_grams") ?: 380.0,
            fatGrams = o.dbl("fat_grams") ?: 70.0,
            waterMilliliters = o.int("water_milliliters") ?: 2500,
            explanation = o.str("explanation") ?: ""
        )
    }
}

/** Rotina recomendada pela IA (iOS: `AIRecommendedRoutine`). */
data class AIRoutineSuggestion(
    val title: String = "Treino Personalizado",
    val workoutType: String = "Musculação",
    val targetMuscles: String = "",
    val hypertrophyFocus: String = "",
    val estimatedDurationMinutes: Int = 50,
    val estimatedCalories: Double = 350.0,
    val recommendedRestSeconds: Int = 90,
    val exercises: List<WorkoutExercisePlan> = emptyList()
) {
    companion object {
        fun fromJson(o: JsonObject) = AIRoutineSuggestion(
            title = o.str("title") ?: "Treino Personalizado",
            workoutType = o.str("workout_type") ?: "Musculação",
            targetMuscles = o.str("target_muscles") ?: "",
            hypertrophyFocus = o.str("hypertrophy_focus") ?: "",
            estimatedDurationMinutes = o.int("estimated_duration_minutes") ?: 50,
            estimatedCalories = o.dbl("estimated_calories") ?: 350.0,
            recommendedRestSeconds = o.int("recommended_rest_seconds") ?: 90,
            exercises = o.arr("exercises")?.mapNotNull { it.asObjectOrNull() }?.map { WorkoutExercisePlan.fromJson(it) }
                ?: emptyList()
        )
    }
}

/** Análise de evolução gerada pela IA (iOS: `EvolutionAIAnalysis`). */
data class AIEvolutionResult(
    val id: String = UUID.randomUUID().toString(),
    val statusSummary: String,
    val calorieDiagnostic: String,
    val macroDiagnostic: String,
    val hydrationDiagnostic: String,
    val workoutSuggestions: List<String>,
    val nutritionSuggestions: List<String>,
    val weeklyActionPlan: String,
    val suggestedTargets: AISuggestedTargets? = null,
    val recommendedRoutine: AIRoutineSuggestion? = null,
    val generatedAt: Long = System.currentTimeMillis()
) {
    companion object {
        fun fromJson(o: JsonObject): AIEvolutionResult {
            fun strings(key: String): List<String> =
                o.arr(key)?.mapNotNull { e -> if (e.isJsonPrimitive) e.asString else null } ?: emptyList()
            return AIEvolutionResult(
                id = o.str("id") ?: UUID.randomUUID().toString(),
                statusSummary = o.str("status_summary") ?: "Análise Concluída",
                calorieDiagnostic = o.str("calorie_diagnostic") ?: "Balanço calórico avaliado com sucesso.",
                macroDiagnostic = o.str("macro_diagnostic") ?: "Distribuição de nutrientes equilibrada.",
                hydrationDiagnostic = o.str("hydration_diagnostic") ?: "Consumo de água avaliado.",
                workoutSuggestions = strings("workout_suggestions"),
                nutritionSuggestions = strings("nutrition_suggestions"),
                weeklyActionPlan = o.str("weekly_action_plan") ?: "Mantenha a consistência diária.",
                suggestedTargets = o.obj("suggested_targets")?.let { AISuggestedTargets.fromJson(it) },
                recommendedRoutine = o.obj("recommended_routine")?.let {
                    val r = AIRoutineSuggestion.fromJson(it)
                    // Garante que os exercícios da rotina recomendada estejam associados aos GIFs biomecânicos
                    r.copy(exercises = r.exercises.map { ex -> ex.withCatalogAssociation() })
                }
            )
        }
    }
}

/** Sugestão inicial de metas (onboarding) (iOS: `InitialGoalsAISuggestion`). */
data class InitialGoalsAISuggestion(
    val suggestedTargetWeight: Double,
    val dailyCalories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val waterMilliliters: Int,
    val targetCalorieBurn: Double,
    val targetSteps: Double,
    val explanation: String
) {
    companion object {
        fun fromJson(o: JsonObject) = InitialGoalsAISuggestion(
            suggestedTargetWeight = o.dbl("suggested_target_weight") ?: 75.0,
            dailyCalories = o.dbl("daily_calories") ?: 2400.0,
            proteinGrams = o.dbl("protein_grams") ?: 150.0,
            carbsGrams = o.dbl("carbs_grams") ?: 280.0,
            fatGrams = o.dbl("fat_grams") ?: 70.0,
            waterMilliliters = o.int("water_milliliters") ?: 2600,
            targetCalorieBurn = o.dbl("target_calorie_burn") ?: 500.0,
            targetSteps = o.dbl("target_steps") ?: 8500.0,
            explanation = o.str("explanation") ?: ""
        )
    }
}

// endregion

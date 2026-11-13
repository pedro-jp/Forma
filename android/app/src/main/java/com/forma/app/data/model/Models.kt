package com.forma.app.data.model

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

// ---------------------------------------------------------------------------------------------
// Utilitários de data/JSON compartilhados (equivalentes aos ISO8601DateFormatter do iOS)
// ---------------------------------------------------------------------------------------------

object FormaDates {
    /** ISO8601 com milissegundos (usado nos payloads enviados ao backend). Ex: 2026-11-09T16:06:45.123Z */
    fun isoFractional(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(millis))

    /** ISO8601 sem frações (ISO8601DateFormatter() padrão do iOS usado nos logs de atividade). */
    fun isoPlain(millis: Long): String =
        SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }.format(java.util.Date(millis))

    fun parseIso(raw: String?): Long? {
        if (raw.isNullOrBlank()) return null
        return try {
            java.time.Instant.parse(raw).toEpochMilli()
        } catch (_: Exception) {
            try {
                java.time.OffsetDateTime.parse(raw).toInstant().toEpochMilli()
            } catch (_: Exception) {
                null
            }
        }
    }

    fun parseIsoOrNow(raw: String?): Long = parseIso(raw) ?: System.currentTimeMillis()
}

fun JsonObject.str(vararg keys: String): String? {
    for (k in keys) {
        val e = get(k)
        if (e != null && !e.isJsonNull && e.isJsonPrimitive) return runCatching { e.asString }.getOrNull()
    }
    return null
}

fun JsonObject.dbl(vararg keys: String): Double? {
    for (k in keys) {
        val e = get(k)
        if (e != null && !e.isJsonNull && e.isJsonPrimitive) {
            val v = runCatching { e.asDouble }.getOrNull()
            if (v != null) return v
        }
    }
    return null
}

fun JsonObject.int(vararg keys: String): Int? {
    for (k in keys) {
        val e = get(k)
        if (e != null && !e.isJsonNull && e.isJsonPrimitive) {
            val v = runCatching { e.asDouble.toInt() }.getOrNull()
            if (v != null) return v
        }
    }
    return null
}

fun JsonObject.bool(vararg keys: String): Boolean? {
    for (k in keys) {
        val e = get(k)
        if (e != null && !e.isJsonNull && e.isJsonPrimitive) {
            val p = e.asJsonPrimitive
            if (p.isBoolean) return p.asBoolean
        }
    }
    return null
}

fun JsonObject.stringList(vararg keys: String): List<String>? {
    for (k in keys) {
        val e = get(k)
        if (e != null && e.isJsonArray) {
            return e.asJsonArray.mapNotNull { if (it.isJsonPrimitive) it.asString else null }
        }
    }
    return null
}

fun parseJsonObjectOrNull(raw: String?): JsonObject? {
    if (raw.isNullOrBlank()) return null
    return try {
        val el = JsonParser.parseString(raw)
        if (el.isJsonObject) el.asJsonObject else null
    } catch (_: Exception) {
        null
    }
}

// ---------------------------------------------------------------------------------------------
// Refeições
// ---------------------------------------------------------------------------------------------

enum class MealType(val title: String, val icon: String) {
    BREAKFAST("Café da Manhã", "☀️"),
    LUNCH("Almoço", "🍲"),
    DINNER("Jantar", "🌙"),
    SNACKS("Lanches", "☕");

    /** Equivalente ao rawValue do iOS (título em português). */
    val rawValue: String get() = title

    /** Nome do SF Symbol usado no iOS (referência para mapeamento de ícones Material). */
    val sfSymbol: String
        get() = when (this) {
            BREAKFAST -> "sun.horizon.fill"
            LUNCH -> "sun.max.fill"
            DINNER -> "moon.stars.fill"
            SNACKS -> "cup.and.saucer.fill"
        }

    companion object {
        /** Aceita rawValue (título) ou nome do enum; padrão Almoço como no iOS. */
        fun fromRaw(raw: String?): MealType {
            if (raw.isNullOrBlank()) return LUNCH
            return values().firstOrNull { it.title.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: LUNCH
        }
    }
}

data class FoodEntry(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val brand: String = "",
    val mealType: MealType = MealType.LUNCH,
    val portionGrams: Double = 100.0,
    val calories: Double,
    val protein: Double = 0.0,
    val carbohydrates: Double = 0.0,
    val fat: Double = 0.0,
    val consumedAt: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    /** Alias para o campo `date` do iOS. */
    val date: Long get() = consumedAt
}

// ---------------------------------------------------------------------------------------------
// Treinos
// ---------------------------------------------------------------------------------------------

enum class WorkoutType(val title: String, val sfSymbol: String) {
    BODYBUILDING("Musculação", "dumbbell.fill"),
    RUNNING("Corrida", "figure.run"),
    WALKING("Caminhada", "figure.walk"),
    CYCLING("Ciclismo", "figure.outdoor.cycle"),
    FUNCTIONAL("Funcional / HIIT", "figure.cross.training"),
    SWIMMING("Natação", "figure.pool.swim"),
    OTHER("Outro", "flame.fill");

    val rawValue: String get() = title

    companion object {
        fun fromRaw(raw: String?): WorkoutType =
            values().firstOrNull { it.title.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: BODYBUILDING
    }
}

data class WorkoutEntry(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val workoutType: String = "Musculação",
    val durationMinutes: Int = 45,
    val caloriesBurned: Double = 350.0,
    val notes: String = "",
    val workoutDate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
) {
    val workoutTypeEnum: WorkoutType get() = WorkoutType.fromRaw(workoutType)
}

data class WaterEntry(
    val id: String = UUID.randomUUID().toString(),
    val milliliters: Int = 250,
    val entryDate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis()
)

data class WeightEntry(
    val id: String = UUID.randomUUID().toString(),
    val weight: Double,
    val entryDate: Long = System.currentTimeMillis(),
    val notes: String = ""
)

// ---------------------------------------------------------------------------------------------
// Perfil
// ---------------------------------------------------------------------------------------------

enum class FitnessGoal(val title: String, val subtitle: String, val sfSymbol: String) {
    GAIN("Ganhar peso", "Ganho de massa muscular e peso", "arrow.up.circle.fill"),
    LOSE("Perder peso", "Queima de gordura e definição", "arrow.down.circle.fill"),
    MAINTAIN("Manter peso", "Manter o peso e a forma atual", "equal.circle.fill");

    /** Equivalente ao rawValue do iOS (título em português) enviado ao backend. */
    val rawValue: String get() = title

    companion object {
        /** Aceita rawValue (título) ou nome do enum; padrão Ganhar peso como no iOS. */
        fun fromRaw(raw: String?): FitnessGoal =
            values().firstOrNull { it.title.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
                ?: GAIN
    }
}

enum class FocusMetric(val title: String) {
    CONSUMED("Calorias Consumidas"),
    BURNED("Calorias Gastas");

    val rawValue: String get() = title

    companion object {
        fun fromRaw(raw: String?): FocusMetric? =
            values().firstOrNull { it.title.equals(raw, ignoreCase = true) || it.name.equals(raw, ignoreCase = true) }
    }
}

/** Contexto fisiológico e comportamental completo (treinos, rotinas, passos, tendência de peso e ingestão) */
data class RecalculationContext(
    val workoutsLast14DaysCount: Int = 0,
    val avgDailyWorkoutBurn: Double = 0.0,
    val routineEstimatedDailyBurn: Double = 0.0,
    val recentAvgSteps: Double = 0.0,
    val weeklyWeightTrendKg: Double? = null,
    val recentAvgCalorieIntake: Double? = null
)

/** Resultado completo do cálculo fisiológico contextual unificado */
data class ContextualGoalMetrics(
    val tmb: Double,
    val contextualTdee: Double,
    val dailyExerciseBurn: Double,
    val suggestedCalories: Double,
    val proteinGrams: Double,
    val carbsGrams: Double,
    val fatGrams: Double,
    val waterMilliliters: Int
)

data class Macros(
    val protein: Double = 160.0,
    val fat: Double = 70.0,
    val carbohydrates: Double = 380.0
)

/** Arredondamento half-away-from-zero (Swift `.rounded()`). */
private fun swiftRound(value: Double): Double =
    if (value >= 0) Math.floor(value + 0.5) else -Math.floor(-value + 0.5)

data class UserProfile(
    val name: String = "",
    val email: String = "",
    val goal: FitnessGoal = FitnessGoal.GAIN,
    val targetCalorieIntake: Double = 2800.0,
    val targetCalorieBurn: Double = 600.0,
    val targetSteps: Double = 8000.0,
    val currentWeight: Double = 72.0,
    val targetWeight: Double = 80.0,
    val heightMeters: Double = 1.78,
    val targetProtein: Double = 160.0,
    val targetFat: Double = 70.0,
    val targetCarbohydrates: Double = 380.0,
    val targetWaterMilliliters: Int = 2500,
    val preferredFocus: FocusMetric? = null,
    val autoRecalculateGoals: Boolean = true,
    val onboardingCompleted: Boolean = false
) {
    /** Indica se o usuário precisa preencher obrigatoriamente o modal de configuração inicial antes de usar o app */
    val requiresInitialSetup: Boolean
        get() = !onboardingCompleted ||
            currentWeight < 30.0 ||
            heightMeters < 1.0 ||
            targetWeight < 30.0 ||
            targetCalorieIntake < 1000.0 ||
            targetProtein <= 0.0 ||
            targetCarbohydrates <= 0.0 ||
            targetFat <= 0.0 ||
            targetWaterMilliliters < 500

    /** Taxa Metabólica Basal (TMB - Mifflin-St Jeor) */
    val tmb: Double
        get() {
            val validWeight = maxOf(30.0, currentWeight)
            val validHeight = maxOf(1.0, heightMeters)
            val heightCm = validHeight * 100.0
            return (10.0 * validWeight) + (6.25 * heightCm) - (5.0 * 25.0) + 5.0
        }

    val macros: Macros
        get() = Macros(protein = targetProtein, fat = targetFat, carbohydrates = targetCarbohydrates)

    val effectiveFocus: FocusMetric
        get() = preferredFocus ?: if (goal == FitnessGoal.GAIN) FocusMetric.CONSUMED else FocusMetric.BURNED

    val emailIsValid: Boolean
        get() {
            val clean = email.trim()
            return clean.contains("@") && clean.contains(".")
        }

    /** Alias usado no iOS (`height`). */
    val height: Double get() = heightMeters

    fun contextualMetrics(weight: Double? = null, context: RecalculationContext? = null): ContextualGoalMetrics =
        computeContextualTargets(
            weight = weight ?: currentWeight,
            height = heightMeters,
            targetWeight = targetWeight,
            goal = goal,
            targetCalorieBurn = targetCalorieBurn,
            targetSteps = targetSteps,
            context = context
        )

    fun contextualTdee(
        weightOverride: Double = currentWeight,
        heightOverride: Double = heightMeters,
        context: RecalculationContext? = null
    ): Double = computeContextualTargets(
        weight = weightOverride,
        height = heightOverride,
        targetWeight = targetWeight,
        goal = goal,
        targetCalorieBurn = targetCalorieBurn,
        targetSteps = targetSteps,
        context = context
    ).contextualTdee

    /**
     * Recalcula dinamicamente TMB, TDEE contextual, meta calórica, macros e água usando todo o contexto
     * (equivalente a `Profile.recalculateDailyGoals`). Retorna uma cópia atualizada.
     */
    fun recalculateDailyGoals(weight: Double? = null, context: RecalculationContext? = null): UserProfile {
        val activeWeight = weight ?: currentWeight
        if (activeWeight <= 30.0) return this
        val metrics = contextualMetrics(activeWeight, context)
        return copy(
            targetCalorieIntake = metrics.suggestedCalories,
            targetProtein = metrics.proteinGrams,
            targetFat = metrics.fatGrams,
            targetCarbohydrates = metrics.carbsGrams,
            targetWaterMilliliters = metrics.waterMilliliters
        )
    }

    /** Pré-visualiza metas aplicando peso/altura/objetivo/peso-alvo novos (não persiste). */
    fun recalculated(
        newWeight: Double = currentWeight,
        newHeight: Double = heightMeters,
        newGoal: FitnessGoal = goal,
        newTargetWeight: Double = targetWeight,
        context: RecalculationContext? = null
    ): UserProfile {
        val metrics = computeContextualTargets(
            weight = newWeight,
            height = newHeight,
            targetWeight = newTargetWeight,
            goal = newGoal,
            targetCalorieBurn = targetCalorieBurn,
            targetSteps = targetSteps,
            context = context
        )
        return copy(
            goal = newGoal,
            currentWeight = newWeight,
            targetWeight = newTargetWeight,
            heightMeters = newHeight,
            targetCalorieIntake = metrics.suggestedCalories,
            targetProtein = metrics.proteinGrams,
            targetFat = metrics.fatGrams,
            targetCarbohydrates = metrics.carbsGrams,
            targetWaterMilliliters = metrics.waterMilliliters
        )
    }

    companion object {
        /** Motor unificado de cálculo fisiológico usando todo o contexto (idêntico a Profile.computeContextualTargets do iOS) */
        fun computeContextualTargets(
            weight: Double,
            height: Double,
            targetWeight: Double,
            goal: FitnessGoal,
            targetCalorieBurn: Double,
            targetSteps: Double,
            context: RecalculationContext? = null
        ): ContextualGoalMetrics {
            val validWeight = minOf(300.0, maxOf(30.0, weight))
            val validHeight = if (height >= 100.0 && height <= 250.0) {
                minOf(2.5, maxOf(1.0, height / 100.0))
            } else {
                minOf(2.5, maxOf(1.0, height))
            }
            val heightCm = validHeight * 100.0
            val imc = validWeight / (validHeight * validHeight)
            val baseTmb = (10.0 * validWeight) + (6.25 * heightCm) - (5.0 * 25.0) + 5.0

            // 1. NEAT real (passos medidos + meta diária de passos)
            val safeTargetSteps = maxOf(3000.0, targetSteps)
            val effectiveSteps = maxOf(context?.recentAvgSteps ?: 0.0, safeTargetSteps * 0.85)
            val neatFactor = 1.25 + minOf(0.25, maxOf(0.08, (effectiveSteps / 10000.0) * 0.15))

            // 2. Gasto ativo diário amortizado (treinos reais de 7/14d, fichas salvas e meta de gasto ativo)
            val loggedWorkoutBurn = maxOf(0.0, context?.avgDailyWorkoutBurn ?: 0.0)
            val routineBurn = maxOf(0.0, context?.routineEstimatedDailyBurn ?: 0.0)
            val targetBurnBaseline = maxOf(0.0, targetCalorieBurn) * 0.65
            val dailyExerciseBurn = maxOf(loggedWorkoutBurn, maxOf(routineBurn, targetBurnBaseline))

            // 3. TDEE Contextual Real
            val contextualTdee = (baseTmb * neatFactor) + dailyExerciseBurn

            // 4. Superávit/Déficit ajustado pela distância até o peso-alvo, IMC e tendência real de evolução
            val highTrainingLoad = dailyExerciseBurn >= 320.0 || (context?.workoutsLast14DaysCount ?: 0) >= 8
            val rawCalories: Double
            val proteinFactor: Double
            val fatFactor: Double
            val minCarbs: Double

            when (goal) {
                FitnessGoal.GAIN -> {
                    val weightGap = maxOf(0.0, targetWeight - validWeight)
                    val gapBonus = minOf(150.0, maxOf(0.0, (weightGap - 4.0) * 10.0))
                    val lowBmiBonus = if (imc < 20.0) 50.0 else 0.0
                    var trendBonus = 0.0
                    context?.weeklyWeightTrendKg?.let { trend ->
                        if (trend < 0.10) trendBonus = 100.0 else if (trend > 0.75) trendBonus = -75.0
                    }
                    rawCalories = contextualTdee + 400.0 + gapBonus + lowBmiBonus + trendBonus
                    proteinFactor = if (highTrainingLoad) 2.1 else 2.0
                    fatFactor = 1.0
                    minCarbs = 120.0
                }
                FitnessGoal.LOSE -> {
                    val loseGap = maxOf(0.0, validWeight - targetWeight)
                    val gapDeficit = minOf(100.0, maxOf(0.0, (loseGap - 6.0) * 6.0))
                    var trendAdjust = 0.0
                    context?.weeklyWeightTrendKg?.let { trend ->
                        if (trend > -0.10) trendAdjust = -75.0 else if (trend < -1.0) trendAdjust = 75.0
                    }
                    val safeFloor = maxOf(1300.0, baseTmb * 1.05)
                    rawCalories = maxOf(safeFloor, contextualTdee - 450.0 - gapDeficit + trendAdjust)
                    proteinFactor = if (highTrainingLoad) 2.3 else 2.2
                    fatFactor = 0.8
                    minCarbs = 60.0
                }
                FitnessGoal.MAINTAIN -> {
                    var trendAdjust = 0.0
                    context?.weeklyWeightTrendKg?.let { trend ->
                        if (trend > 0.35) trendAdjust = -60.0 else if (trend < -0.35) trendAdjust = 60.0
                    }
                    rawCalories = maxOf(1300.0, contextualTdee + trendAdjust)
                    proteinFactor = if (highTrainingLoad) 2.0 else 1.9
                    fatFactor = 0.9
                    minCarbs = 80.0
                }
            }

            val suggestedCalories = swiftRound(rawCalories / 10.0) * 10.0
            val proteinGrams = swiftRound(validWeight * proteinFactor)
            val fatGrams = swiftRound(validWeight * fatFactor)
            val caloriesRemaining = maxOf(0.0, suggestedCalories - (proteinGrams * 4.0 + fatGrams * 9.0))
            val carbsGrams = maxOf(minCarbs, swiftRound(caloriesRemaining / 4.0))

            val stepHydrationBonus = if (effectiveSteps > 8000.0) (effectiveSteps - 8000.0) * 0.04 else 0.0
            val rawWater = (validWeight * 35.0) + (dailyExerciseBurn * 0.85) + stepHydrationBonus
            val waterMl = maxOf(1750, swiftRound(rawWater / 250.0).toInt() * 250)

            return ContextualGoalMetrics(
                tmb = baseTmb,
                contextualTdee = contextualTdee,
                dailyExerciseBurn = dailyExerciseBurn,
                suggestedCalories = suggestedCalories,
                proteinGrams = proteinGrams,
                carbsGrams = carbsGrams,
                fatGrams = fatGrams,
                waterMilliliters = waterMl
            )
        }
    }
}

// ---------------------------------------------------------------------------------------------
// Fichas de treino
// ---------------------------------------------------------------------------------------------

/**
 * Item de exercício dentro de uma ficha. O JSON persistido/sincronizado segue exatamente as chaves do iOS
 * (snake_case: suggested_weight, target_muscle, execution_tip, rest_seconds, completed_sets, animation_id).
 */
data class WorkoutExerciseItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val sets: Int = 3,
    val reps: String = "10-12",
    val suggestedWeight: String = "",
    val targetMuscle: String = "",
    val executionTip: String = "",
    val restSeconds: Int = 90,
    val completedSets: MutableList<Boolean> = MutableList(sets) { false },
    val setWeights: List<Double> = emptyList(),
    val animationId: String? = null
) {
    val isAllSetsCompleted: Boolean get() = completedSets.isNotEmpty() && completedSets.all { it }
    val completedCount: Int get() = completedSets.count { it }

    fun toJsonObject(): JsonObject = JsonObject().apply {
        addProperty("id", id.uppercase(Locale.ROOT))
        addProperty("name", name)
        addProperty("sets", sets)
        addProperty("reps", reps)
        addProperty("suggested_weight", suggestedWeight)
        addProperty("target_muscle", targetMuscle)
        addProperty("execution_tip", executionTip)
        addProperty("rest_seconds", restSeconds)
        add("completed_sets", JsonArray().also { arr -> completedSets.forEach { arr.add(it) } })
        if (setWeights.isNotEmpty()) {
            add("set_weights", JsonArray().also { arr -> setWeights.forEach { arr.add(it) } })
        }
        animationId?.let { addProperty("animation_id", it) }
    }

    companion object {
        private fun boolList(o: JsonObject, vararg keys: String): MutableList<Boolean>? {
            for (k in keys) {
                val e = o.get(k)
                if (e != null && e.isJsonArray) {
                    return e.asJsonArray.map { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean && it.asBoolean }.toMutableList()
                }
            }
            return null
        }

        private fun doubleList(o: JsonObject, vararg keys: String): List<Double> {
            for (k in keys) {
                val e = o.get(k)
                if (e != null && e.isJsonArray) {
                    return e.asJsonArray.mapNotNull { runCatching { it.asDouble }.getOrNull() }
                }
            }
            return emptyList()
        }

        fun fromJsonObject(o: JsonObject): WorkoutExerciseItem {
            val sets = maxOf(1, o.int("sets") ?: 3)
            val rawRest = o.int("rest_seconds", "restSeconds") ?: 90
            return WorkoutExerciseItem(
                id = o.str("id") ?: UUID.randomUUID().toString(),
                name = o.str("name") ?: "Exercício",
                sets = sets,
                reps = o.str("reps") ?: "10-12",
                suggestedWeight = o.str("suggested_weight", "suggestedWeight") ?: "",
                targetMuscle = o.str("target_muscle", "targetMuscle") ?: "",
                executionTip = o.str("execution_tip", "executionTip") ?: "",
                restSeconds = if (rawRest > 0) rawRest else 90,
                completedSets = boolList(o, "completed_sets", "completedSets") ?: MutableList(sets) { false },
                setWeights = doubleList(o, "set_weights", "setWeights"),
                animationId = o.str("animation_id", "animationId")
            )
        }

        fun listFromJson(json: String?): List<WorkoutExerciseItem> {
            if (json.isNullOrBlank()) return emptyList()
            return try {
                val el = JsonParser.parseString(json)
                if (!el.isJsonArray) emptyList() else listFromJsonArray(el.asJsonArray)
            } catch (_: Exception) {
                emptyList()
            }
        }

        fun listFromJsonArray(arr: JsonArray?): List<WorkoutExerciseItem> =
            arr?.mapNotNull { if (it.isJsonObject) runCatching { fromJsonObject(it.asJsonObject) }.getOrNull() else null }
                ?: emptyList()

        fun listToJsonArray(items: List<WorkoutExerciseItem>): JsonArray =
            JsonArray().also { arr -> items.forEach { arr.add(it.toJsonObject()) } }

        fun listToJson(items: List<WorkoutExerciseItem>): String = listToJsonArray(items).toString()
    }
}

data class WorkoutRoutine(
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val workoutType: String = "Musculação",
    val targetMuscles: String = "",
    val hypertrophyFocus: String = "",
    val estimatedDurationMinutes: Int = 50,
    val estimatedCalories: Double = 350.0,
    val recommendedRestSeconds: Int = 90,
    val isFavorite: Boolean = false,
    val exercises: List<WorkoutExerciseItem> = emptyList(),
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val lastTrainedAt: Long? = null
) {
    /** JSON de exercícios no formato do iOS (campo `exercisesJson` do backend). */
    val exercisesJson: String get() = WorkoutExerciseItem.listToJson(exercises)
    val workoutTypeEnum: WorkoutType get() = WorkoutType.fromRaw(workoutType)
}

// ---------------------------------------------------------------------------------------------
// IA (Metas sugeridas, rotina recomendada e análise de evolução)
// ---------------------------------------------------------------------------------------------

data class SuggestedNutritionTargets(
    val dailyCalories: Double = 2800.0,
    val proteinGrams: Double = 160.0,
    val carbsGrams: Double = 380.0,
    val fatGrams: Double = 70.0,
    val waterMilliliters: Int = 2500,
    val explanation: String = ""
) {
    fun toJsonObject(): JsonObject = JsonObject().apply {
        addProperty("daily_calories", dailyCalories)
        addProperty("protein_grams", proteinGrams)
        addProperty("carbs_grams", carbsGrams)
        addProperty("fat_grams", fatGrams)
        addProperty("water_milliliters", waterMilliliters)
        addProperty("explanation", explanation)
    }

    fun toJson(): String = toJsonObject().toString()

    companion object {
        fun fromJsonObject(o: JsonObject) = SuggestedNutritionTargets(
            dailyCalories = o.dbl("daily_calories", "dailyCalories") ?: 2800.0,
            proteinGrams = o.dbl("protein_grams", "proteinGrams") ?: 160.0,
            carbsGrams = o.dbl("carbs_grams", "carbsGrams") ?: 380.0,
            fatGrams = o.dbl("fat_grams", "fatGrams") ?: 70.0,
            waterMilliliters = o.int("water_milliliters", "waterMilliliters") ?: 2500,
            explanation = o.str("explanation") ?: ""
        )

        fun fromJson(raw: String?): SuggestedNutritionTargets? = parseJsonObjectOrNull(raw)?.let { fromJsonObject(it) }
    }
}

data class AIRecommendedRoutine(
    val title: String = "Treino Personalizado",
    val workoutType: String = "Musculação",
    val targetMuscles: String = "",
    val hypertrophyFocus: String = "",
    val estimatedDurationMinutes: Int = 50,
    val estimatedCalories: Double = 350.0,
    val recommendedRestSeconds: Int = 90,
    val exercises: List<WorkoutExerciseItem> = emptyList()
) {
    fun toJsonObject(): JsonObject = JsonObject().apply {
        addProperty("title", title)
        addProperty("workout_type", workoutType)
        addProperty("target_muscles", targetMuscles)
        addProperty("hypertrophy_focus", hypertrophyFocus)
        addProperty("estimated_duration_minutes", estimatedDurationMinutes)
        addProperty("estimated_calories", estimatedCalories)
        addProperty("recommended_rest_seconds", recommendedRestSeconds)
        add("exercises", WorkoutExerciseItem.listToJsonArray(exercises))
    }

    fun toJson(): String = toJsonObject().toString()

    companion object {
        fun fromJsonObject(o: JsonObject) = AIRecommendedRoutine(
            title = o.str("title") ?: "Treino Personalizado",
            workoutType = o.str("workout_type", "workoutType") ?: "Musculação",
            targetMuscles = o.str("target_muscles", "targetMuscles") ?: "",
            hypertrophyFocus = o.str("hypertrophy_focus", "hypertrophyFocus") ?: "",
            estimatedDurationMinutes = o.int("estimated_duration_minutes", "estimatedDurationMinutes") ?: 50,
            estimatedCalories = o.dbl("estimated_calories", "estimatedCalories") ?: 350.0,
            recommendedRestSeconds = o.int("recommended_rest_seconds", "recommendedRestSeconds") ?: 90,
            exercises = WorkoutExerciseItem.listFromJsonArray(o.get("exercises")?.takeIf { it.isJsonArray }?.asJsonArray)
        )

        fun fromJson(raw: String?): AIRecommendedRoutine? = parseJsonObjectOrNull(raw)?.let { fromJsonObject(it) }
    }
}

data class EvolutionAIAnalysis(
    val id: String = UUID.randomUUID().toString(),
    val statusSummary: String,
    val calorieDiagnostic: String,
    val macroDiagnostic: String,
    val hydrationDiagnostic: String,
    val workoutSuggestions: List<String> = emptyList(),
    val nutritionSuggestions: List<String> = emptyList(),
    val weeklyActionPlan: String,
    val suggestedTargets: SuggestedNutritionTargets? = null,
    val recommendedRoutine: AIRecommendedRoutine? = null,
    val generatedAt: Long = System.currentTimeMillis()
) {
    /** Equivalente a EvolutionAnalysisStore.sanitizeBranding (apenas trim dos textos). */
    fun sanitized(): EvolutionAIAnalysis = copy(
        statusSummary = statusSummary.trim(),
        calorieDiagnostic = calorieDiagnostic.trim(),
        macroDiagnostic = macroDiagnostic.trim(),
        hydrationDiagnostic = hydrationDiagnostic.trim(),
        workoutSuggestions = workoutSuggestions.map { it.trim() },
        nutritionSuggestions = nutritionSuggestions.map { it.trim() },
        weeklyActionPlan = weeklyActionPlan.trim(),
        suggestedTargets = suggestedTargets?.copy(explanation = suggestedTargets.explanation.trim())
    )

    companion object {
        /** Decodificação tolerante idêntica ao init(from:) do iOS (chaves snake_case, com fallback camelCase). */
        fun fromJsonObject(o: JsonObject): EvolutionAIAnalysis = EvolutionAIAnalysis(
            id = o.str("id")?.takeIf { runCatching { UUID.fromString(it) }.isSuccess } ?: UUID.randomUUID().toString(),
            statusSummary = o.str("status_summary", "statusSummary") ?: "Análise Concluída",
            calorieDiagnostic = o.str("calorie_diagnostic", "calorieDiagnostic") ?: "Balanço calórico avaliado com sucesso.",
            macroDiagnostic = o.str("macro_diagnostic", "macroDiagnostic") ?: "Distribuição de nutrientes equilibrada.",
            hydrationDiagnostic = o.str("hydration_diagnostic", "hydrationDiagnostic") ?: "Consumo de água avaliado.",
            workoutSuggestions = o.stringList("workout_suggestions", "workoutSuggestions") ?: emptyList(),
            nutritionSuggestions = o.stringList("nutrition_suggestions", "nutritionSuggestions") ?: emptyList(),
            weeklyActionPlan = o.str("weekly_action_plan", "weeklyActionPlan") ?: "Mantenha a consistência diária.",
            suggestedTargets = (o.get("suggested_targets") ?: o.get("suggestedTargets"))
                ?.takeIf { it.isJsonObject }?.let { SuggestedNutritionTargets.fromJsonObject(it.asJsonObject) },
            recommendedRoutine = (o.get("recommended_routine") ?: o.get("recommendedRoutine"))
                ?.takeIf { it.isJsonObject }?.let { AIRecommendedRoutine.fromJsonObject(it.asJsonObject) },
            generatedAt = System.currentTimeMillis()
        )

        fun fromJson(raw: String?): EvolutionAIAnalysis? = parseJsonObjectOrNull(raw)?.let { fromJsonObject(it) }
    }
}

// ---------------------------------------------------------------------------------------------
// Log de atividades (AppActivityLogManager)
// ---------------------------------------------------------------------------------------------

data class ActivityLogRecord(
    val id: String = UUID.randomUUID().toString(),
    /** ISO8601 UTC sem frações (ex: 2026-11-09T16:06:45Z), idêntico ao iOS. */
    val timestamp: String,
    val category: String, // meal, workout, water, weight
    val title: String,
    val isSnack: Boolean? = null,
    val caloriesChange: Double = 0.0, // Positivo = consumo, Negativo = gasto
    val proteinGrams: Double? = null,
    val carbsGrams: Double? = null,
    val fatGrams: Double? = null,
    val waterMilliliters: Int? = null,
    val weightKg: Double? = null,
    val details: String? = null
) {
    val timestampMillis: Long? get() = FormaDates.parseIso(timestamp)

    /** HH:mm no fuso local (formattedTime do iOS). */
    val formattedTime: String
        get() = timestampMillis?.let {
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(it))
        } ?: ""

    /** yyyy-MM-dd no fuso local (dateString do iOS). */
    val dateString: String
        get() = timestampMillis?.let {
            SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(java.util.Date(it))
        } ?: ""

    fun toJsonObject(): JsonObject = JsonObject().apply {
        addProperty("id", id)
        addProperty("timestamp", timestamp)
        addProperty("formattedTime", formattedTime)
        addProperty("dateString", dateString)
        addProperty("category", category)
        addProperty("title", title)
        isSnack?.let { addProperty("isSnack", it) }
        addProperty("caloriesChange", caloriesChange)
        proteinGrams?.let { addProperty("proteinGrams", it) }
        carbsGrams?.let { addProperty("carbsGrams", it) }
        fatGrams?.let { addProperty("fatGrams", it) }
        waterMilliliters?.let { addProperty("waterMilliliters", it) }
        weightKg?.let { addProperty("weightKg", it) }
        details?.let { addProperty("details", it) }
    }

    companion object {
        /** Construtor equivalente ao init do iOS (recebe a data em millis e formata o timestamp). */
        fun create(
            id: String = UUID.randomUUID().toString(),
            timestampMillis: Long = System.currentTimeMillis(),
            category: String,
            title: String,
            isSnack: Boolean? = null,
            caloriesChange: Double = 0.0,
            proteinGrams: Double? = null,
            carbsGrams: Double? = null,
            fatGrams: Double? = null,
            waterMilliliters: Int? = null,
            weightKg: Double? = null,
            details: String? = null
        ) = ActivityLogRecord(
            id = id,
            timestamp = FormaDates.isoPlain(timestampMillis),
            category = category,
            title = title,
            isSnack = isSnack,
            caloriesChange = caloriesChange,
            proteinGrams = proteinGrams,
            carbsGrams = carbsGrams,
            fatGrams = fatGrams,
            waterMilliliters = waterMilliliters,
            weightKg = weightKg,
            details = details
        )
    }
}

/** Payload de perfil enviado ao backend (PUT /api/sync/profile e profileUpdate); chaves idênticas ao iOS. */
data class RemoteProfilePayload(
    val name: String?,
    val goal: String,
    val targetCalorieIntake: Double,
    val targetCalorieBurn: Double,
    val targetSteps: Double,
    val targetWeight: Double,
    val targetProtein: Double,
    val targetCarbohydrates: Double,
    val targetFat: Double,
    val targetWaterMilliliters: Int,
    val heightMeters: Double,
    val currentWeight: Double,
    val onboardingCompleted: Boolean
) {
    fun toMap(): Map<String, Any> {
        val dict = linkedMapOf<String, Any>(
            "goal" to goal,
            "targetCalorieIntake" to targetCalorieIntake,
            "targetCalorieBurn" to targetCalorieBurn,
            "targetSteps" to targetSteps,
            "targetWeight" to targetWeight,
            "targetProtein" to targetProtein,
            "targetCarbohydrates" to targetCarbohydrates,
            "targetFat" to targetFat,
            "targetWaterMilliliters" to targetWaterMilliliters,
            "heightMeters" to heightMeters,
            "currentWeight" to currentWeight,
            "onboardingCompleted" to onboardingCompleted
        )
        val clean = name?.trim()
        if (!clean.isNullOrEmpty()) dict["name"] = clean
        return dict
    }

    companion object {
        fun from(profile: UserProfile, resolvedName: String?): RemoteProfilePayload = RemoteProfilePayload(
            name = resolvedName,
            goal = profile.goal.rawValue,
            targetCalorieIntake = profile.targetCalorieIntake,
            targetCalorieBurn = profile.targetCalorieBurn,
            targetSteps = profile.targetSteps,
            targetWeight = profile.targetWeight,
            targetProtein = profile.targetProtein,
            targetCarbohydrates = profile.targetCarbohydrates,
            targetFat = profile.targetFat,
            targetWaterMilliliters = profile.targetWaterMilliliters,
            heightMeters = profile.heightMeters,
            currentWeight = profile.currentWeight,
            onboardingCompleted = profile.onboardingCompleted
        )
    }
}

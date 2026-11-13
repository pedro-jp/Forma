package com.forma.app.data.services

import com.forma.app.data.model.FitnessGoal
import com.forma.app.data.model.RecalculationContext

/** Resultado completo do cálculo fisiológico contextual unificado (iOS: `ContextualGoalMetrics`). */
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

/** Arredondamento "meio para longe do zero" (igual a `Double.rounded()` do Swift). */
internal fun swiftRound(value: Double): Double =
    if (value < 0) -Math.floor(-value + 0.5) else Math.floor(value + 0.5)

/**
 * Motor unificado de cálculo fisiológico (porta de `Profile.computeContextualTargets`, iOS).
 * Usa biometria, meta de peso, passos, gasto ativo, treinos, fichas e tendência real.
 */
object ContextualTargets {
    fun compute(
        weight: Double,
        height: Double,
        targetWeight: Double,
        goal: FitnessGoal,
        targetCalorieBurn: Double,
        targetSteps: Double,
        context: RecalculationContext? = null
    ): ContextualGoalMetrics {
        val validWeight = weight.coerceIn(30.0, 300.0)
        val validHeight = if (height >= 100.0 && height <= 250.0) {
            (height / 100.0).coerceIn(1.0, 2.5)
        } else {
            height.coerceIn(1.0, 2.5)
        }
        val heightCm = validHeight * 100.0
        val imc = validWeight / (validHeight * validHeight)
        val baseTmb = (10.0 * validWeight) + (6.25 * heightCm) - (5.0 * 25.0) + 5.0

        // 1. NEAT real (passos medidos + meta diária de passos)
        val safeTargetSteps = maxOf(3000.0, targetSteps)
        val effectiveSteps = maxOf(context?.recentAvgSteps ?: 0.0, safeTargetSteps * 0.85)
        val neatFactor = 1.25 + minOf(0.25, maxOf(0.08, (effectiveSteps / 10000.0) * 0.15))

        // 2. Gasto ativo diário amortizado
        val loggedWorkoutBurn = maxOf(0.0, context?.avgDailyWorkoutBurn ?: 0.0)
        val routineBurn = maxOf(0.0, context?.routineEstimatedDailyBurn ?: 0.0)
        val targetBurnBaseline = maxOf(0.0, targetCalorieBurn) * 0.65
        val dailyExerciseBurn = maxOf(loggedWorkoutBurn, maxOf(routineBurn, targetBurnBaseline))

        // 3. TDEE contextual real
        val contextualTdee = (baseTmb * neatFactor) + dailyExerciseBurn

        // 4. Superávit/déficit ajustado por distância até o peso-alvo, IMC e tendência real
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

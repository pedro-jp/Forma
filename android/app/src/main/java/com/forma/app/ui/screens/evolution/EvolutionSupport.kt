package com.forma.app.ui.screens.evolution

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Accessibility
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.MonitorHeart
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.SportsScore
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.ui.graphics.vector.ImageVector
import com.forma.app.data.model.FitnessGoal
import com.forma.app.ui.components.sfSymbol
import com.forma.app.data.model.RecalculationContext
import com.forma.app.data.model.UserProfile
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Porte de Forma/ViewModels/EvolutionViewModel.swift (ProfileInputValidator, EvolutionCalculator, EvolutionStats)

/** `ProfileInputValidator` (iOS). */
object ProfileInputValidator {
    /** Aceita vírgula ou ponto e restringe peso humano válido entre 30 kg e 300 kg com 1 casa decimal. */
    fun parseFlexibleWeight(raw: String): Double? {
        val cleaned = raw.replace(",", ".").trim()
        if (cleaned.isEmpty()) return null
        val value = cleaned.toDoubleOrNull() ?: return null
        if (!value.isFinite() || value < 30.0 || value > 300.0) return null
        return swiftRound(value * 10.0) / 10.0
    }

    /** Aceita altura em metros (ex: "1,78") ou centímetros (ex: "178") e converte para metros [1.00, 2.50]. */
    fun parseFlexibleHeight(raw: String): Double? {
        val cleaned = raw.replace(",", ".").trim()
        if (cleaned.isEmpty()) return null
        var value = cleaned.toDoubleOrNull() ?: return null
        if (!value.isFinite()) return null
        if (value >= 100.0 && value <= 250.0) value /= 100.0
        if (value < 1.0 || value > 2.5) return null
        return swiftRound(value * 100.0) / 100.0
    }
}

/** Arredondamento half-away-from-zero (Swift `.rounded()`). */
internal fun swiftRound(value: Double): Double =
    if (value >= 0) Math.floor(value + 0.5) else -Math.floor(-value + 0.5)

/** `String(format: "%.1f", v)` com ponto decimal (as telas iOS usam o locale do aparelho; mantemos ponto). */
internal fun fmt1(v: Double): String = String.format(Locale.US, "%.1f", v)

internal fun fmt2(v: Double): String = String.format(Locale.US, "%.2f", v)

/** `EvolutionStats` (iOS). */
data class EvolutionStats(
    val imc: Double,
    val imcClassification: String,
    val tmb: Double,
    val tdee: Double,
    val suggestedCalorieIntake: Double,
    val suggestedProtein: Double,
    val remainingWeightToGoal: Double,
    val isGoalAchieved: Boolean
)

/** `EvolutionCalculator` (iOS). */
object EvolutionCalculator {
    fun classifyIMC(imc: Double): String = when {
        imc < 18.5 -> "Abaixo do peso ideal"
        imc < 25.0 -> "Peso saudável"
        imc < 30.0 -> "Sobrepeso leve"
        imc < 35.0 -> "Obesidade Grau I"
        else -> "Obesidade Grau II+"
    }

    fun calculate(
        weight: Double,
        height: Double,
        goal: FitnessGoal,
        targetWeight: Double,
        targetCalorieBurn: Double = 600.0,
        targetSteps: Double = 8000.0,
        context: RecalculationContext? = null
    ): EvolutionStats {
        val validWeight = max(30.0, weight)
        val validHeight = if (height >= 100.0 && height <= 250.0) {
            min(2.5, max(1.0, height / 100.0))
        } else {
            min(2.5, max(1.0, height))
        }
        val imc = validWeight / (validHeight * validHeight)
        val metrics = UserProfile.computeContextualTargets(
            weight = validWeight,
            height = validHeight,
            targetWeight = targetWeight,
            goal = goal,
            targetCalorieBurn = targetCalorieBurn,
            targetSteps = targetSteps,
            context = context
        )
        val remaining: Double
        val achieved: Boolean
        when (goal) {
            FitnessGoal.GAIN -> {
                remaining = max(0.0, targetWeight - validWeight)
                achieved = validWeight >= targetWeight && targetWeight > 0
            }
            FitnessGoal.LOSE -> {
                remaining = max(0.0, validWeight - targetWeight)
                achieved = validWeight <= targetWeight && targetWeight > 0
            }
            FitnessGoal.MAINTAIN -> {
                remaining = abs(targetWeight - validWeight)
                achieved = abs(targetWeight - validWeight) < 0.5
            }
        }
        return EvolutionStats(
            imc = imc,
            imcClassification = classifyIMC(imc),
            tmb = metrics.tmb,
            tdee = metrics.contextualTdee,
            suggestedCalorieIntake = metrics.suggestedCalories,
            suggestedProtein = metrics.proteinGrams,
            remainingWeightToGoal = remaining,
            isGoalAchieved = achieved
        )
    }
}

internal fun Double.toIntSafe(): Int = if (isFinite()) toInt() else 0

internal fun Double.rounded(): Int = if (isFinite()) swiftRound(this).roundToInt() else 0

/**
 * `Image(systemName:)` -> Material para os símbolos que ainda não constam em `sfSymbol(...)`
 * (delega ao mapeamento do design system para os demais).
 */
internal fun symbolFor(name: String): ImageVector = when (name) {
    "flag.fill" -> Icons.Filled.Flag
    "flag.checkered" -> Icons.Filled.SportsScore
    "bolt.heart.fill" -> Icons.Filled.MonitorHeart
    "figure.arms.open" -> Icons.Filled.Accessibility
    "slider.horizontal.3" -> Icons.Filled.Tune
    "play.circle.fill" -> Icons.Filled.PlayCircle
    "arrow.up.circle.fill" -> Icons.Filled.ArrowCircleUp
    "arrow.down.circle.fill" -> Icons.Filled.ArrowCircleDown
    "equal.circle.fill" -> Icons.Filled.DragHandle
    "chart.xyaxis.line" -> sfSymbol("chart.line.uptrend.xyaxis")
    "chart.pie.fill" -> Icons.Filled.PieChart
    "ruler" -> Icons.Filled.Straighten
    "key.fill" -> Icons.Filled.VpnKey
    "hand.raised.shield.fill" -> Icons.Filled.GppGood
    else -> sfSymbol(name)
}

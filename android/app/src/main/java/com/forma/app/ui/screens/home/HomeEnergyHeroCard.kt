package com.forma.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forma.app.data.model.FitnessGoal
import com.forma.app.data.model.FocusMetric
import com.forma.app.ui.components.FormaBadge
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaEnergyRing
import com.forma.app.ui.components.FormaMacroProgressRow
import com.forma.app.ui.components.FormaScaledText
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.sfSymbol
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlin.math.abs
import kotlin.math.roundToInt

/** Porte de `HomeEnergyHeroCard.swift`: anel interativo Consumidas vs Gastas, resumo de saldo e macronutrientes. */
@Composable
fun HomeEnergyHeroCard(
    activeMetric: FocusMetric,
    onActiveMetricChange: (FocusMetric) -> Unit,
    goal: FitnessGoal,
    caloriesConsumed: Double,
    adjustedCalorieTarget: Double,
    activeCaloriesBurned: Double,
    activeBurnTarget: Double,
    remainingCalories: Double,
    proteinConsumed: Double,
    proteinTarget: Double,
    carbsConsumed: Double,
    carbsTarget: Double,
    fatConsumed: Double,
    fatTarget: Double,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val isConsumed = activeMetric == FocusMetric.CONSUMED

    val mainPercentage: Int = if (isConsumed) {
        if (adjustedCalorieTarget > 0) ((caloriesConsumed / adjustedCalorieTarget) * 100).roundToInt() else 0
    } else {
        if (activeBurnTarget > 0) ((activeCaloriesBurned / activeBurnTarget) * 100).roundToInt() else 0
    }

    FormaCard(modifier = modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isConsumed) "Balanço Calórico" else "Queima Ativa",
                        color = colors.primaryText,
                        style = FormaTypography.headline
                    )
                    Text(
                        text = if (isConsumed) "Calorias do treino somadas à meta diária" else "Treinos e atividades sincronizadas",
                        color = colors.secondaryText,
                        style = FormaTypography.caption
                    )
                }
                FormaBadge(
                    text = "$mainPercentage%",
                    icon = sfSymbol(if (isConsumed) "bolt.fill" else "flame.fill"),
                    tint = if (isConsumed) colors.brand else colors.energyGreen
                )
            }

            HomeFocusSegmentedControl(activeMetric = activeMetric, onChange = onActiveMetricChange)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                if (isConsumed) {
                    FormaEnergyRing(
                        current = caloriesConsumed,
                        target = adjustedCalorieTarget,
                        color = colors.brand,
                        modifier = Modifier.size(174.dp),
                        lineWidth = 13.dp,
                        unitLabel = "consumidas",
                        isBulkingMode = goal == FitnessGoal.GAIN
                    )
                } else {
                    FormaEnergyRing(
                        current = activeCaloriesBurned,
                        target = activeBurnTarget,
                        color = colors.energyGreen,
                        modifier = Modifier.size(174.dp),
                        lineWidth = 13.dp,
                        unitLabel = "ativas",
                        isBulkingMode = false
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                HomeBalanceMetricTile(
                    title = "Consumidas",
                    value = "${caloriesConsumed.roundToInt()}",
                    subtitle = "meta ${adjustedCalorieTarget.roundToInt()}",
                    color = colors.brand,
                    icon = "fork.knife",
                    modifier = Modifier.weight(1f)
                )
                HomeBalanceMetricTile(
                    title = "Ativas",
                    value = "+${activeCaloriesBurned.roundToInt()}",
                    subtitle = "treino + saúde",
                    color = colors.energyGreen,
                    icon = "flame.fill",
                    modifier = Modifier.weight(1f)
                )
                HomeBalanceMetricTile(
                    title = if (remainingCalories >= 0) "Disponível" else "Excedente",
                    value = "${abs(remainingCalories.roundToInt())}",
                    subtitle = "kcal",
                    color = if (remainingCalories >= 0) colors.brand else colors.warning,
                    icon = "sparkles",
                    modifier = Modifier.weight(1f)
                )
            }

            HorizontalDivider(thickness = 1.dp, color = colors.primary.copy(alpha = 0.06f))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Macronutrientes",
                        color = colors.primaryText,
                        style = FormaTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Consumido / Meta",
                        color = colors.secondaryText,
                        style = FormaTypography.caption
                    )
                }

                FormaMacroProgressRow(
                    proteinCurrent = proteinConsumed,
                    proteinTarget = proteinTarget,
                    carbsCurrent = carbsConsumed,
                    carbsTarget = carbsTarget,
                    fatCurrent = fatConsumed,
                    fatTarget = fatTarget
                )
            }
        }
    }
}

/** `Picker("Foco") { Consumidas | Gastas }.pickerStyle(.segmented)`. */
@Composable
private fun HomeFocusSegmentedControl(
    activeMetric: FocusMetric,
    onChange: (FocusMetric) -> Unit
) {
    val colors = FormaTheme.Colors
    val trackShape = RoundedCornerShape(9.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp)
            .clip(trackShape)
            .background(colors.tertiaryFill)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        listOf(FocusMetric.CONSUMED to "Consumidas", FocusMetric.BURNED to "Gastas").forEach { (metric, label) ->
            val selected = metric == activeMetric
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(
                        if (selected) (if (colors.isDark) Color(0xFF636366) else Color.White) else Color.Transparent
                    )
                    .formaPlainClickable { onChange(metric) },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    color = colors.primaryText,
                    style = FormaTypography.system(13f, if (selected) FontWeight.SemiBold else FontWeight.Medium),
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/** `HomeBalanceMetricTile`: título + valor + subtítulo sobre fundo tingido (raio 14). */
@Composable
fun HomeBalanceMetricTile(
    title: String,
    value: String,
    subtitle: String,
    color: Color,
    icon: String,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val shape = RoundedCornerShape(FormaTheme.Radius.inner)
    Column(
        modifier = modifier
            .clip(shape)
            .background(color.copy(alpha = 0.08f))
            .border(0.8.dp, color.copy(alpha = 0.18f), shape)
            .padding(horizontal = 10.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = sfSymbol(icon),
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(10.dp)
            )
            FormaScaledText(
                text = title,
                style = FormaTypography.system(11f, FontWeight.SemiBold),
                color = colors.primaryText.copy(alpha = 0.88f),
                minScale = 0.75f
            )
        }
        FormaScaledText(
            text = value,
            style = FormaTypography.rounded(18f, FontWeight.Bold),
            color = colors.primaryText,
            minScale = 0.8f
        )
        FormaScaledText(
            text = subtitle,
            style = FormaTypography.system(10f, FontWeight.Medium),
            color = colors.secondaryText,
            minScale = 0.8f
        )
    }
}

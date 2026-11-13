package com.forma.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forma.app.data.model.UserProfile
import com.forma.app.ui.components.FormaBadge
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.screens.evolution.rounded
import com.forma.app.ui.screens.evolution.symbolFor
import com.forma.app.ui.screens.evolution.toIntSafe
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlin.math.max

// Porte de Forma/Views/Profile/ProfileGoalsSection.swift

/** `ProfileDailyGoalsCard`: Metas Diárias (calorias, gasto ativo, passos, água). */
@Composable
fun ProfileDailyGoalsCard(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit,
    onContextSliderEnded: () -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    FormaCard(modifier = modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(symbolFor("flame.fill"), null, tint = colors.brand, modifier = Modifier.size(20.dp))
                Text(
                    text = "Metas Diárias",
                    color = colors.primaryText,
                    style = FormaTypography.headline,
                    modifier = Modifier.weight(1f)
                )
                FormaBadge(text = "Ajuste Forma", tint = colors.brand)
            }

            GoalSliderRow(
                title = "Calorias a Consumir",
                valueText = "${profile.targetCalorieIntake.toIntSafe()} kcal",
                valueColor = colors.brand
            ) {
                FormaSlider(
                    value = profile.targetCalorieIntake.coerceIn(1200.0, 4500.0),
                    onValueChange = { onProfileChange(profile.copy(targetCalorieIntake = it)) },
                    range = 1200.0..4500.0,
                    step = 50.0,
                    tint = colors.brand,
                    onValueChangeFinished = onValueChangeFinished
                )
            }

            GoalSliderRow(
                title = "Gasto Ativo em Treinos",
                valueText = "${profile.targetCalorieBurn.toIntSafe()} kcal",
                valueColor = colors.energyGreen
            ) {
                FormaSlider(
                    value = profile.targetCalorieBurn.coerceIn(200.0, 1500.0),
                    onValueChange = { onProfileChange(profile.copy(targetCalorieBurn = it)) },
                    range = 200.0..1500.0,
                    step = 25.0,
                    tint = colors.energyGreen,
                    onValueChangeFinished = onContextSliderEnded
                )
            }

            GoalSliderRow(
                title = "Passos Diários",
                valueText = "${profile.targetSteps.toIntSafe()} passos",
                valueColor = colors.primaryText
            ) {
                FormaSlider(
                    value = profile.targetSteps.coerceIn(3000.0, 20000.0),
                    onValueChange = { onProfileChange(profile.copy(targetSteps = it)) },
                    range = 3000.0..20000.0,
                    step = 500.0,
                    tint = colors.blue,
                    onValueChangeFinished = onContextSliderEnded
                )
            }

            GoalSliderRow(
                title = "Meta de Água",
                titleIcon = "drop.fill",
                valueText = "${profile.targetWaterMilliliters} ml",
                valueColor = colors.water
            ) {
                FormaSlider(
                    value = profile.targetWaterMilliliters.toDouble().coerceIn(1000.0, 5000.0),
                    onValueChange = { onProfileChange(profile.copy(targetWaterMilliliters = it.toInt())) },
                    range = 1000.0..5000.0,
                    step = 250.0,
                    tint = colors.water,
                    onValueChangeFinished = onValueChangeFinished
                )
            }
        }
    }
}

@Composable
private fun GoalSliderRow(
    title: String,
    valueText: String,
    valueColor: androidx.compose.ui.graphics.Color,
    titleIcon: String? = null,
    slider: @Composable () -> Unit
) {
    val colors = FormaTheme.Colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                if (titleIcon != null) {
                    Icon(symbolFor(titleIcon), null, tint = colors.water, modifier = Modifier.size(12.dp))
                }
                Text(text = title, color = colors.secondaryText, style = FormaTypography.caption)
            }
            Text(
                text = valueText,
                color = valueColor,
                style = FormaTypography.subheadline.copy(fontWeight = FontWeight.Bold)
            )
        }
        slider()
    }
}

/** `ProfileMacroGoalsCard`: Metas de Macronutrientes com barra tricolor ao vivo. */
@Composable
fun ProfileMacroGoalsCard(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit,
    onSuggestTap: () -> Unit,
    onValueChangeFinished: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val proteinCalories = max(0.0, profile.targetProtein * 4.0)
    val carbsCalories = max(0.0, profile.targetCarbohydrates * 4.0)
    val fatCalories = max(0.0, profile.targetFat * 9.0)
    val totalMacroCalories = max(1.0, proteinCalories + carbsCalories + fatCalories)

    val pPct = ((proteinCalories / totalMacroCalories) * 100).rounded()
    val cPct = ((carbsCalories / totalMacroCalories) * 100).rounded()
    val fPct = max(0, 100 - pPct - cPct)

    FormaCard(modifier = modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(symbolFor("chart.pie.fill"), null, tint = colors.brand, modifier = Modifier.size(20.dp))
                Text(
                    text = "Metas de Macronutrientes",
                    color = colors.primaryText,
                    style = FormaTypography.headline,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "Sugerir",
                    color = colors.brand,
                    style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold),
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.brandSoft)
                        .formaPlainClickable(onClick = onSuggestTap)
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Divisão das Calorias",
                        color = colors.secondaryText,
                        style = FormaTypography.caption2.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "P $pPct% • C $cPct% • G $fPct% (${totalMacroCalories.rounded()} kcal)",
                        color = colors.secondaryText,
                        style = FormaTypography.caption2.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                }

                BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(6.dp)) {
                    val total: Dp = maxWidth
                    fun segmentWidth(fraction: Double): Dp = max(4f, (total.value * fraction.toFloat()) - 1f).dp
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        MacroCapsule(segmentWidth(proteinCalories / totalMacroCalories), colors.protein)
                        MacroCapsule(segmentWidth(carbsCalories / totalMacroCalories), colors.carbs)
                        MacroCapsule(segmentWidth(fatCalories / totalMacroCalories), colors.fat)
                    }
                }
            }

            MacroSliderRow(
                title = "Proteína",
                valueText = "${profile.targetProtein.toIntSafe()} g",
                valueColor = colors.protein
            ) {
                FormaSlider(
                    value = profile.targetProtein.coerceIn(50.0, 350.0),
                    onValueChange = { onProfileChange(profile.copy(targetProtein = it)) },
                    range = 50.0..350.0,
                    step = 5.0,
                    tint = colors.protein,
                    onValueChangeFinished = onValueChangeFinished
                )
            }

            MacroSliderRow(
                title = "Carboidratos",
                valueText = "${profile.targetCarbohydrates.toIntSafe()} g",
                valueColor = colors.carbs
            ) {
                FormaSlider(
                    value = profile.targetCarbohydrates.coerceIn(50.0, 600.0),
                    onValueChange = { onProfileChange(profile.copy(targetCarbohydrates = it)) },
                    range = 50.0..600.0,
                    step = 5.0,
                    tint = colors.carbs,
                    onValueChangeFinished = onValueChangeFinished
                )
            }

            MacroSliderRow(
                title = "Gorduras",
                valueText = "${profile.targetFat.toIntSafe()} g",
                valueColor = colors.fat
            ) {
                FormaSlider(
                    value = profile.targetFat.coerceIn(20.0, 200.0),
                    onValueChange = { onProfileChange(profile.copy(targetFat = it)) },
                    range = 20.0..200.0,
                    step = 2.0,
                    tint = colors.fat,
                    onValueChangeFinished = onValueChangeFinished
                )
            }
        }
    }
}

@Composable
private fun MacroCapsule(width: Dp, color: androidx.compose.ui.graphics.Color) {
    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .width(width)
            .height(6.dp)
            .clip(CircleShape)
            .background(color)
    )
}

@Composable
private fun MacroSliderRow(
    title: String,
    valueText: String,
    valueColor: androidx.compose.ui.graphics.Color,
    slider: @Composable () -> Unit
) {
    val colors = FormaTheme.Colors
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = title,
                color = colors.secondaryText,
                style = FormaTypography.caption,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = valueText,
                color = valueColor,
                style = FormaTypography.subheadline.copy(fontWeight = FontWeight.Bold)
            )
        }
        slider()
    }
}

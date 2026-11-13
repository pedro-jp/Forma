package com.forma.app.ui.screens.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forma.app.data.model.FoodEntry
import com.forma.app.data.model.WorkoutEntry
import com.forma.app.data.model.WorkoutType
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaEmptyState
import com.forma.app.ui.components.FormaProgressBar
import com.forma.app.ui.components.FormaScaledText
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.sfSymbol
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Símbolos do iOS usados na Home que o [sfSymbol] genérico ainda não mapeia
 * (ícones de objetivo e de tipos de treino).
 */
fun homeSymbol(name: String): ImageVector = when (name) {
    "arrow.up.circle.fill" -> Icons.Filled.ArrowCircleUp
    "arrow.down.circle.fill" -> Icons.Filled.ArrowCircleDown
    "equal.circle.fill" -> Icons.Filled.DragHandle
    "figure.outdoor.cycle" -> Icons.Filled.DirectionsBike
    "figure.cross.training" -> Icons.Filled.SportsGymnastics
    "figure.pool.swim" -> Icons.Filled.Pool
    else -> sfSymbol(name)
}

/** Ícone do tipo de treino (iOS: `WorkoutType.icon`). */
fun workoutTypeIcon(type: WorkoutType): ImageVector = homeSymbol(type.sfSymbol)

/** Porte de `HomeDailyLogsSection.swift`: Refeições, Passos, Treinos de hoje e ações rápidas. */
@Composable
fun HomeDailyLogsSection(
    foodsToday: List<FoodEntry>,
    workoutsToday: List<WorkoutEntry>,
    steps: Double,
    stepsTarget: Double,
    distanceKm: Double,
    onClearMealsTap: () -> Unit,
    onOpenCameraTap: () -> Unit,
    onAddMealTap: () -> Unit,
    onDeleteFood: (FoodEntry) -> Unit,
    onAddWorkoutTap: () -> Unit,
    onLogWeightTap: () -> Unit,
    modifier: Modifier = Modifier,
    onDeleteWorkout: ((WorkoutEntry) -> Unit)? = null
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(FormaTheme.Spacing.sectionGap)
    ) {
        MealsTodayCard(
            foodsToday = foodsToday,
            onClearMealsTap = onClearMealsTap,
            onOpenCameraTap = onOpenCameraTap,
            onAddMealTap = onAddMealTap,
            onDeleteFood = onDeleteFood
        )
        StepsCard(steps = steps, stepsTarget = stepsTarget, distanceKm = distanceKm)
        WorkoutsTodayCard(
            workoutsToday = workoutsToday,
            onAddWorkoutTap = onAddWorkoutTap,
            onDeleteWorkout = onDeleteWorkout
        )
        QuickActionsRow(
            onAddMealTap = onAddMealTap,
            onAddWorkoutTap = onAddWorkoutTap,
            onLogWeightTap = onLogWeightTap
        )
    }
}

@Composable
private fun MealsTodayCard(
    foodsToday: List<FoodEntry>,
    onClearMealsTap: () -> Unit,
    onOpenCameraTap: () -> Unit,
    onAddMealTap: () -> Unit,
    onDeleteFood: (FoodEntry) -> Unit
) {
    val colors = FormaTheme.Colors
    FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = sfSymbol("fork.knife"),
                    contentDescription = null,
                    tint = colors.brand,
                    modifier = Modifier.size(15.dp)
                )
                Box(modifier = Modifier.weight(1f)) {
                    FormaScaledText(
                        text = "Refeições (${foodsToday.size})",
                        style = FormaTypography.rounded(17f, FontWeight.Bold),
                        color = colors.primaryText,
                        minScale = 0.85f
                    )
                }

                if (foodsToday.isNotEmpty()) {
                    Text(
                        text = "Limpar",
                        color = colors.danger.copy(alpha = 0.85f),
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier.formaPlainClickable(onClick = onClearMealsTap)
                    )
                }

                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .background(colors.brandSoft)
                        .formaPlainClickable(onClick = onOpenCameraTap),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = sfSymbol("camera.fill"),
                        contentDescription = "Fotografar refeição",
                        tint = colors.brand,
                        modifier = Modifier.size(13.dp)
                    )
                }

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.brand)
                        .formaPlainClickable(onClick = onAddMealTap)
                        .padding(horizontal = 11.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = sfSymbol("mic.fill"),
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Refeição",
                        color = Color.White,
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            if (foodsToday.isEmpty()) {
                FormaEmptyState(
                    icon = sfSymbol("fork.knife"),
                    title = "Nenhuma refeição registrada hoje",
                    subtitle = "Fotografe seu prato, fale pelo microfone ou digite o que comeu.",
                    actionTitle = "+ Registrar Refeição",
                    onAction = onAddMealTap
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    foodsToday.forEachIndexed { index, food ->
                        FoodRow(food = food, onDelete = { onDeleteFood(food) })
                        if (index != foodsToday.lastIndex) {
                            HorizontalDivider(thickness = 0.6.dp, color = colors.primary.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun FoodRow(food: FoodEntry, onDelete: () -> Unit) {
    val colors = FormaTheme.Colors
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = food.name,
                    color = colors.primaryText,
                    style = FormaTypography.system(15f, FontWeight.SemiBold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                Text(
                    text = food.mealType.rawValue,
                    color = colors.brand,
                    style = FormaTypography.caption2.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    softWrap = false,
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.brandSoft)
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
            }
            FormaScaledText(
                text = "${food.portionGrams.toInt()}g • P ${food.protein.toInt()}g  C ${food.carbohydrates.toInt()}g  G ${food.fat.toInt()}g",
                style = FormaTypography.caption,
                color = colors.secondaryText,
                minScale = 0.85f
            )
        }

        Text(
            text = "${food.calories.roundToInt()} kcal",
            color = colors.primaryText,
            style = FormaTypography.rounded(14f, FontWeight.Bold),
            maxLines = 1,
            softWrap = false
        )

        Icon(
            imageVector = sfSymbol("trash"),
            contentDescription = "Remover ${food.name}",
            tint = colors.danger.copy(alpha = 0.65f),
            modifier = Modifier
                .formaPlainClickable(onClick = onDelete)
                .padding(4.dp)
                .size(14.dp)
        )
    }
}

@Composable
private fun StepsCard(steps: Double, stepsTarget: Double, distanceKm: Double) {
    val colors = FormaTheme.Colors
    FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.brandSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = sfSymbol("figure.walk"),
                        contentDescription = null,
                        tint = colors.brand,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Passos de hoje",
                            color = colors.secondaryText,
                            style = FormaTypography.caption,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "Meta ${stepsTarget.toInt()}",
                            color = colors.secondaryText,
                            style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Row(
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "${steps.toInt()}",
                            color = colors.primaryText,
                            style = FormaTypography.rounded(25f, FontWeight.Bold),
                            maxLines = 1
                        )
                        if (distanceKm > 0) {
                            Text(
                                text = String.format(Locale.US, "%.1f km", distanceKm),
                                color = colors.secondaryText,
                                style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }

            FormaProgressBar(value = steps, total = stepsTarget, color = colors.brand, height = 6.dp)
        }
    }
}

@Composable
private fun WorkoutsTodayCard(
    workoutsToday: List<WorkoutEntry>,
    onAddWorkoutTap: () -> Unit,
    onDeleteWorkout: ((WorkoutEntry) -> Unit)?
) {
    val colors = FormaTheme.Colors
    FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = sfSymbol("dumbbell.fill"),
                    contentDescription = null,
                    tint = colors.energyGreen,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Treinos de Hoje",
                    color = colors.primaryText,
                    style = FormaTypography.headline,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.brandSoft)
                        .formaPlainClickable(onClick = onAddWorkoutTap)
                        .padding(horizontal = 10.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = sfSymbol("plus"),
                        contentDescription = null,
                        tint = colors.brand,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Treino",
                        color = colors.brand,
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold),
                        maxLines = 1
                    )
                }
            }

            if (workoutsToday.isEmpty()) {
                Text(
                    text = "Nenhum treino registrado hoje. Registre sua sessão ou inicie uma ficha na aba Treinos.",
                    color = colors.secondaryText,
                    style = FormaTypography.caption,
                    modifier = Modifier.padding(vertical = 2.dp)
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    workoutsToday.forEach { workout ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 2.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = workoutTypeIcon(workout.workoutTypeEnum),
                                contentDescription = null,
                                tint = colors.energyGreen,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = workout.title,
                                color = colors.primaryText,
                                style = FormaTypography.system(14f, FontWeight.SemiBold),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Text(
                                text = "${workout.durationMinutes} min",
                                color = colors.secondaryText,
                                style = FormaTypography.caption,
                                maxLines = 1,
                                softWrap = false
                            )
                            Text(
                                text = "+${workout.caloriesBurned.toInt()} kcal",
                                color = colors.energyGreen,
                                style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold),
                                maxLines = 1,
                                softWrap = false
                            )
                            if (onDeleteWorkout != null) {
                                Icon(
                                    imageVector = sfSymbol("trash"),
                                    contentDescription = "Remover ${workout.title}",
                                    tint = colors.danger.copy(alpha = 0.65f),
                                    modifier = Modifier
                                        .formaPlainClickable { onDeleteWorkout(workout) }
                                        .padding(4.dp)
                                        .size(14.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun QuickActionsRow(
    onAddMealTap: () -> Unit,
    onAddWorkoutTap: () -> Unit,
    onLogWeightTap: () -> Unit
) {
    val colors = FormaTheme.Colors
    Column {
        Spacer(Modifier.height(2.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            HomeQuickActionButton(
                title = "+ Refeição",
                icon = "mic.fill",
                color = colors.brand,
                onClick = onAddMealTap,
                modifier = Modifier.weight(1f)
            )
            HomeQuickActionButton(
                title = "+ Treino",
                icon = "dumbbell.fill",
                color = colors.energyGreen,
                onClick = onAddWorkoutTap,
                modifier = Modifier.weight(1f)
            )
            HomeQuickActionButton(
                title = "+ Pesagem",
                icon = "scalemass.fill",
                color = colors.blue,
                onClick = onLogWeightTap,
                modifier = Modifier.weight(1f)
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** `HomeQuickActionButton`: botão cheio com cantos 16 e sombra tingida. */
@Composable
fun HomeQuickActionButton(
    title: String,
    icon: String,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = color.copy(alpha = 0.20f),
                spotColor = color.copy(alpha = 0.20f)
            )
            .clip(shape)
            .background(color)
            .formaPlainClickable(onClick = onClick)
            .padding(vertical = 13.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = sfSymbol(icon),
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(14.dp)
        )
        Text(
            text = title,
            color = Color.White,
            style = FormaTypography.rounded(13f, FontWeight.SemiBold),
            maxLines = 1,
            softWrap = false
        )
    }
}

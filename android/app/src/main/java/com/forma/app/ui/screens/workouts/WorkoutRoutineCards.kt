package com.forma.app.ui.screens.workouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forma.app.data.model.WorkoutEntry
import com.forma.app.data.model.WorkoutExerciseItem
import com.forma.app.data.model.WorkoutRoutine
import com.forma.app.ui.components.FormaBadge
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import java.time.Instant
import java.time.ZoneId
import kotlin.math.roundToInt

/** Porte de `RoutineCard` (WorkoutRoutineCard.swift): ficha de treino com expansão de exercícios, favorito e menu. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun RoutineCard(
    routine: WorkoutRoutine,
    onStart: () -> Unit,
    onToggleFavorite: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
    onShowExerciseDetail: ((WorkoutExerciseItem) -> Unit)? = null
) {
    val colors = FormaTheme.Colors
    var isExpanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val borderColor = if (routine.isFavorite) colors.warning.copy(alpha = 0.32f) else colors.primary.copy(alpha = 0.06f)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.03f), spotColor = Color.Black.copy(alpha = 0.03f))
            .clip(shape)
            .background(colors.cardSurface)
            .border(0.9.dp, borderColor, shape)
            .padding(16.dp)
            .animateContentSize(),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.brandSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(workoutTypeIcon(routine.workoutTypeEnum), contentDescription = null, tint = colors.brand, modifier = Modifier.size(22.dp))
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                if (routine.notes.contains("Análise") || routine.notes.contains("Inteligência")) {
                    FormaBadge(text = "Inteligência Forma", icon = Icons.Filled.AutoAwesome, tint = colors.aiAccent)
                }
                Text(
                    text = routine.title,
                    color = colors.primaryText,
                    style = FormaTypography.rounded(16f, FontWeight.Bold),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (routine.targetMuscles.isNotEmpty()) {
                    Text(
                        text = routine.targetMuscles,
                        color = colors.secondaryText,
                        style = FormaTypography.system(12f, FontWeight.Medium),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Icon(
                imageVector = if (routine.isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (routine.isFavorite) "Desfavoritar" else "Favoritar",
                tint = if (routine.isFavorite) colors.warning else colors.secondaryText.copy(alpha = 0.4f),
                modifier = Modifier
                    .size(30.dp)
                    .formaPlainClickable(onClick = onToggleFavorite)
                    .padding(6.dp)
            )
        }

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.systemBackground)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = colors.energyGreen, modifier = Modifier.size(12.dp))
                Text(
                    text = "~${routine.estimatedCalories.toInt()} kcal",
                    color = colors.primaryText,
                    style = FormaTypography.rounded(12f, FontWeight.Bold),
                    maxLines = 1
                )
            }
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.systemBackground)
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.Schedule, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(12.dp))
                Text(
                    text = "${routine.estimatedDurationMinutes} min",
                    color = colors.secondaryText,
                    style = FormaTypography.system(12f, FontWeight.Medium),
                    maxLines = 1
                )
            }
            Row(
                modifier = Modifier
                    .clip(CircleShape)
                    .background(colors.brandSoft)
                    .formaPlainClickable { isExpanded = !isExpanded }
                    .padding(horizontal = 9.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Icon(Icons.Filled.FormatListBulleted, contentDescription = null, tint = colors.brand, modifier = Modifier.size(12.dp))
                Text(
                    text = "${routine.exercises.size} exercícios",
                    color = colors.brand,
                    style = FormaTypography.system(12f, FontWeight.SemiBold),
                    maxLines = 1
                )
                Icon(
                    if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                    tint = colors.brand,
                    modifier = Modifier.size(11.dp)
                )
            }
        }

        AnimatedVisibility(visible = isExpanded && routine.exercises.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                routine.exercises.forEach { ex ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.systemBackground)
                            .padding(9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(ex.name, color = colors.primaryText, style = FormaTypography.system(13f, FontWeight.Bold))
                            Text(
                                text = "${ex.sets}x ${ex.reps}" + if (ex.suggestedWeight.isEmpty()) "" else " • ${ex.suggestedWeight}",
                                color = colors.secondaryText,
                                style = FormaTypography.system(11f)
                            )
                        }
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.brandSoft)
                                .formaPlainClickable { onShowExerciseDetail?.invoke(ex) }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(Icons.Filled.Info, contentDescription = null, tint = colors.brand, modifier = Modifier.size(14.dp))
                            Text("GIF", color = colors.brand, style = FormaTypography.system(11f, FontWeight.Bold))
                        }
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.primary.copy(alpha = 0.06f))
        )

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.brand)
                    .formaPlainClickable(onClick = onStart)
                    .padding(vertical = 11.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                Text(
                    text = "Iniciar Treino com Checklist",
                    color = Color.White,
                    style = FormaTypography.rounded(13f, FontWeight.Bold),
                    maxLines = 1
                )
            }

            Box {
                Box(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.primary.copy(alpha = 0.06f))
                        .formaPlainClickable { menuExpanded = true }
                        .padding(10.dp)
                ) {
                    Icon(Icons.Filled.MoreHoriz, contentDescription = "Mais opções", tint = colors.secondaryText, modifier = Modifier.size(16.dp))
                }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text("Excluir Ficha", color = colors.danger) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null, tint = colors.danger) },
                        onClick = {
                            menuExpanded = false
                            onDelete()
                        }
                    )
                }
            }
        }
    }
}

/** Porte de `WorkoutCard` (WorkoutRoutineCard.swift): item do histórico de sessões. */
@Composable
fun WorkoutCard(
    workout: WorkoutEntry,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val dateText = remember(workout.workoutDate) {
        Instant.ofEpochMilli(workout.workoutDate).atZone(ZoneId.systemDefault()).toLocalDate().format(DayMonthFormatter)
    }

    FormaCard(modifier = modifier.fillMaxWidth(), padding = 16.dp, cornerRadius = 18.dp) {
        Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.brandSoft),
                contentAlignment = Alignment.Center
            ) {
                Icon(workoutTypeIcon(workout.workoutTypeEnum), contentDescription = null, tint = colors.brand, modifier = Modifier.size(22.dp))
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = workout.title,
                        color = colors.primaryText,
                        style = FormaTypography.rounded(16f, FontWeight.Bold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = dateText,
                            color = colors.secondaryText,
                            style = FormaTypography.system(11f),
                            maxLines = 1,
                            softWrap = false
                        )
                        Icon(
                            imageVector = Icons.Filled.Delete,
                            contentDescription = "Excluir treino",
                            tint = colors.danger.copy(alpha = 0.68f),
                            modifier = Modifier
                                .size(18.dp)
                                .formaPlainClickable(onClick = onDelete)
                                .padding(1.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = workout.workoutTypeEnum.rawValue,
                        color = colors.brand,
                        style = FormaTypography.system(12f, FontWeight.SemiBold),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text("•", color = colors.secondaryText, style = FormaTypography.caption)
                    Text(
                        text = "${workout.durationMinutes} min",
                        color = colors.secondaryText,
                        style = FormaTypography.system(12f),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text("•", color = colors.secondaryText, style = FormaTypography.caption)
                    Text(
                        text = "+${workout.caloriesBurned.roundToInt()} kcal",
                        color = colors.energyGreen,
                        style = FormaTypography.rounded(12f, FontWeight.Bold),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (workout.notes.isNotEmpty()) {
                    Text(
                        text = workout.notes,
                        color = colors.secondaryText,
                        style = FormaTypography.system(12f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 1.dp)
                    )
                }
            }
        }
    }
}

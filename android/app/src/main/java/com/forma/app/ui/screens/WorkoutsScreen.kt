package com.forma.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Checklist
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.forma.app.data.catalog.ExerciseCatalog
import com.forma.app.data.catalog.ExerciseCatalogItem
import com.forma.app.data.model.WorkoutEntry
import com.forma.app.data.model.WorkoutRoutine
import com.forma.app.data.model.WorkoutType
import com.forma.app.data.repository.FormaRepository
import com.forma.app.data.services.SharedWorkoutSessionManager
import com.forma.app.ui.components.FormaBadge
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaEmptyState
import com.forma.app.ui.components.FormaScreenHeader
import com.forma.app.ui.components.FormaScrollScreen
import com.forma.app.ui.components.FormaSecondaryButton
import com.forma.app.ui.components.FormaSectionHeader
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.sfSymbol
import com.forma.app.ui.screens.workouts.AIWorkoutLoggerSheet
import com.forma.app.ui.screens.workouts.ActiveWorkoutRequest
import com.forma.app.ui.screens.workouts.ActiveWorkoutSessionDialog
import com.forma.app.ui.screens.workouts.AddWorkoutSheet
import com.forma.app.ui.screens.workouts.ExerciseDetailSheet
import com.forma.app.ui.screens.workouts.PtBr
import com.forma.app.ui.screens.workouts.RoutineCard
import com.forma.app.ui.screens.workouts.WorkoutCard
import com.forma.app.ui.screens.workouts.capitalizedPt
import com.forma.app.ui.screens.workouts.consumePendingWorkoutIfAny
import com.forma.app.ui.screens.workouts.rememberNotificationPermissionGate
import com.forma.app.ui.screens.workouts.toPlan
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/**
 * Porte de `WorkoutsView` (iOS): painel de Treinos & Atividades com consistência semanal, assistente IA, fichas
 * (favoritas no topo) e histórico de sessões.
 */
@Composable
fun WorkoutsScreen(
    repository: FormaRepository
) {
    val context = LocalContext.current
    val colors = FormaTheme.Colors
    val scope = rememberCoroutineScope()

    val workouts by repository.workoutEntries.collectAsState()
    val routines by repository.workoutRoutines.collectAsState()
    val healthActiveCalories by repository.healthActiveCalories.collectAsState()
    val routerShowAIWorkout by repository.router.showAIWorkoutSheet.collectAsState()

    var showAddWorkout by remember { mutableStateOf(false) }
    var showAIWorkout by remember { mutableStateOf(false) }
    var selectedExerciseForDetail by remember { mutableStateOf<ExerciseCatalogItem?>(null) }
    val notificationGate = rememberNotificationPermissionGate()

    // .onAppear { healthVM.loadHealthData(); syncExternalHealthWorkoutsInBackground }
    LaunchedEffect(Unit) {
        runCatching { repository.syncTodayData(skipCloudSyncTrigger = true) }
        runCatching { repository.syncExternalHealthWorkoutsInBackground() }
    }

    // Treino finalizado pela notificação enquanto o app estava em segundo plano (FormaApp.checkPendingWorkout)
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        scope.launch { runCatching { consumePendingWorkoutIfAny(context, repository) } }
    }

    val sortedRoutines = remember(routines) { routines.filter { it.isFavorite } + routines.filter { !it.isFavorite } }

    // Semana (segunda a domingo)
    val zone = remember { ZoneId.systemDefault() }
    val today = LocalDate.now(zone)
    val weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
    val weekDays = (0L until 7L).map { weekStart.plusDays(it) }
    val weekStartMillis = weekStart.atStartOfDay(zone).toInstant().toEpochMilli()
    val weekEndMillis = weekStart.plusDays(7).atStartOfDay(zone).toInstant().toEpochMilli()
    val workoutsThisWeek = workouts.filter { it.workoutDate >= weekStartMillis && it.workoutDate <= weekEndMillis }
    val totalMinutesWeek = workoutsThisWeek.sumOf { it.durationMinutes }
    val totalCaloriesBurnedWeek = workoutsThisWeek.sumOf { it.caloriesBurned }
    val trainedDays = workouts.map { Instant.ofEpochMilli(it.workoutDate).atZone(zone).toLocalDate() }.toSet()

    FormaScrollScreen(horizontalPadding = 0.dp, topPadding = 10.dp, bottomPadding = 36.dp) {
        FormaScreenHeader(
            title = "Atividade & Treinos",
            subtitle = "Fichas, evolução muscular e consistência",
            modifier = Modifier.padding(horizontal = FormaTheme.Spacing.screenHorizontal),
            trailing = {
                FormaSecondaryButton(
                    title = "Manual",
                    icon = sfSymbol("plus"),
                    onClick = { showAddWorkout = true }
                )
            }
        )

        // weeklyConsistencyCard
        FormaCard(
            modifier = Modifier
                .padding(horizontal = FormaTheme.Spacing.screenHorizontal)
                .fillMaxWidth(),
            padding = 20.dp
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("Semana Atual", color = colors.primaryText, style = FormaTypography.headline)
                        Text(
                            "Consistência e queima de Segunda a Domingo",
                            color = colors.secondaryText,
                            style = FormaTypography.caption
                        )
                    }
                    FormaBadge(
                        text = "${workoutsThisWeek.size} treinos",
                        icon = Icons.Filled.LocalFireDepartment,
                        tint = colors.brand
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    weekDays.forEach { day ->
                        val hasTrained = trainedDays.contains(day)
                        val isToday = day == today
                        val isFuture = day.isAfter(today)
                        val dayLetter = day.dayOfWeek.getDisplayName(TextStyle.SHORT, PtBr).replace(".", "").capitalizedPt()

                        Column(
                            modifier = Modifier.weight(1f),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = dayLetter,
                                color = if (isToday) colors.brand else colors.secondaryText,
                                style = FormaTypography.system(11f, if (isToday) FontWeight.Bold else FontWeight.Medium),
                                maxLines = 1
                            )
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        when {
                                            hasTrained -> colors.brand
                                            isToday -> colors.brandSoft
                                            else -> colors.gray.copy(alpha = 0.12f)
                                        }
                                    )
                                    .then(
                                        if (isToday) Modifier.border(1.5.dp, colors.brand, CircleShape) else Modifier
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (hasTrained) {
                                    Icon(Icons.Filled.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                                } else {
                                    Text(
                                        text = day.dayOfMonth.toString(),
                                        color = when {
                                            isFuture -> colors.secondaryText.copy(alpha = 0.35f)
                                            isToday -> colors.brand
                                            else -> colors.secondaryText
                                        },
                                        style = FormaTypography.rounded(12f, if (isToday) FontWeight.Bold else FontWeight.Medium)
                                    )
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

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WeekStat(title = "Tempo", value = "$totalMinutesWeek min", color = colors.primaryText, modifier = Modifier.weight(1f))
                    WeekStat(
                        title = "Gasto em Treinos",
                        value = "${totalCaloriesBurnedWeek.roundToInt()} kcal",
                        color = colors.energyGreen,
                        modifier = Modifier.weight(1.2f)
                    )
                    WeekStat(
                        title = "Ativas Hoje",
                        value = "${healthActiveCalories.roundToInt()} kcal",
                        color = colors.brand,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }

        // aiAssistantBanner
        val bannerShape = RoundedCornerShape(20.dp)
        Row(
            modifier = Modifier
                .padding(horizontal = FormaTheme.Spacing.screenHorizontal)
                .fillMaxWidth()
                .shadow(8.dp, bannerShape, ambientColor = Color.Black.copy(alpha = 0.03f), spotColor = Color.Black.copy(alpha = 0.03f))
                .clip(bannerShape)
                .background(colors.cardSurface)
                .border(1.dp, colors.aiAccent.copy(alpha = 0.22f), bannerShape)
                .formaPlainClickable { showAIWorkout = true }
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(colors.aiGradient),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
            }
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = "Criar ou Calcular Treino",
                    color = colors.primaryText,
                    style = FormaTypography.rounded(15f, FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    text = "Inteligência Forma • gasto calórico, execução e ficha interativa",
                    color = colors.secondaryText,
                    style = FormaTypography.system(11f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Icon(Icons.Filled.ChevronRight, contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(18.dp))
        }

        // routinesSection
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            FormaSectionHeader(
                title = "Minhas Fichas de Treino",
                subtitle = "Favoritas no topo • checklist, Mini-Player e timer ao vivo",
                modifier = Modifier.padding(horizontal = FormaTheme.Spacing.screenHorizontal),
                trailing = {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.brandSoft)
                            .formaPlainClickable { showAIWorkout = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(sfSymbol("plus"), contentDescription = null, tint = colors.brand, modifier = Modifier.size(12.dp))
                        Text("Nova Ficha", color = colors.brand, style = FormaTypography.rounded(12f, FontWeight.Bold), maxLines = 1)
                    }
                }
            )

            if (sortedRoutines.isEmpty()) {
                FormaCard(
                    modifier = Modifier
                        .padding(horizontal = FormaTheme.Spacing.screenHorizontal)
                        .fillMaxWidth(),
                    padding = 20.dp
                ) {
                    FormaEmptyState(
                        icon = Icons.Filled.Checklist,
                        title = "Nenhuma ficha salva",
                        subtitle = "Crie sua primeira ficha com a Inteligência Forma para acompanhar séries, descanso e GIFs de execução.",
                        actionTitle = "Criar Ficha",
                        onAction = { showAIWorkout = true }
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = FormaTheme.Spacing.screenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    sortedRoutines.forEach { routine ->
                        key(routine.id) {
                            RoutineCard(
                                routine = routine,
                                onStart = {
                                    notificationGate {
                                        val req = ActiveWorkoutRequest.fromRoutine(routine)
                                        SharedWorkoutSessionManager.get(context).resumeOrStart(
                                            title = req.title,
                                            workoutTypeRaw = req.workoutType.rawValue,
                                            exercises = req.exercises,
                                            baseCalories = req.baseCalories,
                                            baseDurationMinutes = req.baseDurationMinutes,
                                            initialRestSeconds = if (req.initialRestSeconds > 0) req.initialRestSeconds else 90,
                                            targetMuscles = req.targetMuscles,
                                            hypertrophyFocus = req.hypertrophyFocus
                                        )
                                    }
                                },
                                onToggleFavorite = { repository.toggleFavoriteRoutine(routine.id) },
                                onDelete = { repository.deleteWorkoutRoutine(routine.id) },
                                onShowExerciseDetail = { ex ->
                                    selectedExerciseForDetail = ExerciseCatalog.item(ex.animationId)
                                        ?: ExerciseCatalog.findBestMatch(ex.name)
                                }
                            )
                        }
                    }
                }
            }
        }

        // historySection
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = "Histórico de Sessões",
                color = colors.primaryText,
                style = FormaTypography.rounded(18f, FontWeight.Bold),
                modifier = Modifier.padding(horizontal = FormaTheme.Spacing.screenHorizontal)
            )

            if (workouts.isEmpty()) {
                FormaCard(
                    modifier = Modifier
                        .padding(horizontal = FormaTheme.Spacing.screenHorizontal)
                        .fillMaxWidth(),
                    padding = 20.dp
                ) {
                    FormaEmptyState(
                        icon = Icons.Filled.FitnessCenter,
                        title = "Nenhum treino registrado ainda",
                        subtitle = "Inicie uma ficha com checklist ou registre seu treino manualmente."
                    )
                }
            } else {
                Column(
                    modifier = Modifier.padding(horizontal = FormaTheme.Spacing.screenHorizontal),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    workouts.forEach { workout ->
                        key(workout.id) {
                            WorkoutCard(
                                workout = workout,
                                onDelete = { repository.deleteWorkoutEntry(workout.id) }
                            )
                        }
                    }
                }
            }
        }
    }

    if (showAddWorkout) {
        AddWorkoutSheet(onDismiss = { showAddWorkout = false })
    }

    if (showAIWorkout || routerShowAIWorkout) {
        AIWorkoutLoggerSheet(
            onDismiss = {
                showAIWorkout = false
                repository.router.setShowAIWorkoutSheet(false)
            }
        )
    }

    selectedExerciseForDetail?.let { item ->
        ExerciseDetailSheet(item = item, onDismiss = { selectedExerciseForDetail = null })
    }
}

@Composable
private fun WeekStat(title: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val colors = FormaTheme.Colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(text = title, color = colors.secondaryText, style = FormaTypography.caption, maxLines = 1)
        Text(
            text = value,
            color = color,
            style = FormaTypography.rounded(17f, FontWeight.Bold),
            maxLines = 1
        )
    }
}

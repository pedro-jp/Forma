package com.forma.app.ui.screens.workouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.forma.app.data.catalog.ExerciseCatalog
import com.forma.app.data.catalog.ExerciseCatalogItem
import com.forma.app.data.model.WorkoutType
import com.forma.app.data.services.SharedWorkoutSessionManager
import com.forma.app.data.services.WorkoutExercisePlan
import com.forma.app.ui.components.FormaProgressBar
import com.forma.app.ui.components.FormaTintedCard
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.rememberFormaHaptics
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min

/**
 * Host global (em `MainScreen`) que exibe a sessão de treino em tela cheia sempre que há uma sessão ativa e
 * `isExpandedInApp == true`, permitindo abrir o treino a partir do Mini-Player em qualquer aba do app.
 */
@Composable
fun GlobalActiveWorkoutSessionHost() {
    val context = LocalContext.current
    val manager = remember { SharedWorkoutSessionManager.get(context) }
    val repository = remember { workoutsRepository() }
    val session by manager.session.collectAsState()
    val isExpanded by manager.isExpandedInApp.collectAsState()

    val currentSession = session
    if (currentSession != null && !currentSession.isFinished && isExpanded) {
        val routine = remember(currentSession.title, repository.workoutRoutines.value) {
            repository.workoutRoutines.value.firstOrNull { it.title == currentSession.title }
        }
        val request = remember(currentSession.title, routine) {
            ActiveWorkoutRequest(
                title = currentSession.title,
                workoutType = WorkoutType.fromRaw(currentSession.workoutTypeRaw),
                targetMuscles = currentSession.targetMuscles.ifEmpty { routine?.targetMuscles.orEmpty() },
                hypertrophyFocus = currentSession.hypertrophyFocus.ifEmpty { routine?.hypertrophyFocus.orEmpty() },
                baseCalories = currentSession.baseCalories,
                baseDurationMinutes = currentSession.baseDurationMinutes,
                initialRestSeconds = currentSession.restDurationSeconds,
                exercises = currentSession.exercises,
                routineToUpdate = routine
            )
        }
        ActiveWorkoutSessionDialog(
            request = request,
            onDismiss = { manager.setExpandedInApp(false) }
        )
    }
}

/**
 * Porte de `ActiveWorkoutSessionView` (iOS): sessão imersiva de treino ao vivo em tela cheia.
 * O estado vive em [SharedWorkoutSessionManager] (notificação ongoing/descanso/Mini-Player/PiP ficam em sincronia).
 * Ao minimizar (botão "Minimizar" ou gesto/botão Voltar do Android), recolhe para o Mini-Player sobreposto.
 */
@Composable
fun ActiveWorkoutSessionDialog(
    request: ActiveWorkoutRequest,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val manager = remember { SharedWorkoutSessionManager.get(context) }
    Dialog(
        onDismissRequest = {
            manager.setExpandedInApp(false)
            onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = false,
            usePlatformDefaultWidth = false
        )
    ) {
        ActiveWorkoutSessionContent(
            request = request,
            onMinimize = {
                manager.setExpandedInApp(false)
                onDismiss()
            },
            onDismiss = onDismiss
        )
    }
}

@Composable
private fun ActiveWorkoutSessionContent(
    request: ActiveWorkoutRequest,
    onMinimize: () -> Unit,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = FormaTheme.Colors
    val scope = rememberCoroutineScope()
    val haptics = rememberFormaHaptics()
    val repository = remember { workoutsRepository() }
    val manager = remember { SharedWorkoutSessionManager.get(context) }

    var started by remember { mutableStateOf(false) }
    var closing by remember { mutableStateOf(false) }
    var showFinishConfirmation by remember { mutableStateOf(false) }
    var showCancelConfirmation by remember { mutableStateOf(false) }
    var catalogItemForDetail by remember { mutableStateOf<ExerciseCatalogItem?>(null) }
    var showCatalogSheet by remember { mutableStateOf(false) }
    var exerciseIndexToLink by remember { mutableStateOf<Int?>(null) }

    // onAppear: retoma a sessão compartilhada de mesmo título ou inicia uma nova
    LaunchedEffect(request) {
        manager.resumeOrStart(
            title = request.title,
            workoutTypeRaw = request.workoutType.rawValue,
            exercises = request.exercises,
            baseCalories = request.baseCalories,
            baseDurationMinutes = request.baseDurationMinutes,
            initialRestSeconds = if (request.initialRestSeconds > 0) request.initialRestSeconds else 90,
            targetMuscles = request.targetMuscles,
            hypertrophyFocus = request.hypertrophyFocus,
            userBodyWeightKg = repository.userProfile.value.currentWeight
        )
        started = true
    }

    val sessionState by manager.session.collectAsState()
    val elapsedSeconds by manager.elapsedSeconds.collectAsState()
    val restRemaining by manager.restRemainingSeconds.collectAsState()
    val session = sessionState

    // Treino finalizado pela notificação enquanto a tela estava aberta (iOS: `shared.isFinished` -> finishAndSaveWorkout)
    LaunchedEffect(started, session == null) {
        if (started && session == null && !closing) {
            closing = true
            val pending = manager.consumePendingWorkout()
            if (pending != null) {
                runCatching { saveFinishedWorkoutRecord(repository, pending, request.routineToUpdate) }
                haptics.success()
            }
            onDismiss()
        }
    }

    fun finishAndSave() {
        if (closing) return
        closing = true
        val currentExercises = session?.exercises
        val updatedRoutine = request.routineToUpdate?.let { r ->
            if (!currentExercises.isNullOrEmpty()) {
                val updatedItems = currentExercises.map { ex ->
                    val avg = ex.averageWeightKg
                    ex.copy(
                        suggestedWeight = if (avg > 0.0) WorkoutExercisePlan.formatWeightKg(avg) else ex.suggestedWeight,
                        completedSets = List(maxOf(1, ex.sets)) { false }
                    ).toItem()
                }
                r.copy(exercises = updatedItems)
            } else r
        }
        scope.launch {
            val record = manager.finishWorkout()
            try {
                if (record != null) saveFinishedWorkoutRecord(repository, record, updatedRoutine)
                haptics.success()
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
            }
            onDismiss()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        if (session != null) {
            val totalSets = session.totalSetsCount
            val completedSets = session.completedSetsCount
            val progress = if (totalSets > 0) completedSets.toDouble() / totalSets else 0.0
            val liveKcal = remember(elapsedSeconds, session.baseCalories, session.baseDurationMinutes) {
                val minutes = max(1.0, elapsedSeconds / 60.0)
                val rate = session.baseCalories / max(10.0, session.baseDurationMinutes.toDouble())
                Math.round(minutes * rate).toInt()
            }
            val workoutType = WorkoutType.fromRaw(session.workoutTypeRaw)

            Column(modifier = Modifier.fillMaxSize()) {
                // Barra de navegação (Minimizar -> Mini-Player / título / Finalizar)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                        Row(
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.primary.copy(alpha = 0.06f))
                                .formaPlainClickable {
                                    haptics.selection()
                                    onMinimize()
                                }
                                .padding(horizontal = 10.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.KeyboardArrowDown,
                                contentDescription = "Minimizar para Mini-Player",
                                tint = colors.primaryText,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Minimizar",
                                color = colors.primaryText,
                                style = FormaTypography.system(13f, FontWeight.SemiBold),
                                maxLines = 1
                            )
                        }
                    }
                    Text(
                        text = "Treino Ativo",
                        color = colors.primaryText,
                        style = FormaTypography.headline,
                        maxLines = 1
                    )
                    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                        Text(
                            text = "Finalizar",
                            color = colors.brand,
                            style = FormaTypography.system(15f, FontWeight.Bold),
                            modifier = Modifier.formaPlainClickable { showFinishConfirmation = true }
                        )
                    }
                }

                // Cabeçalho da sessão
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .shadow(6.dp, ambientColor = Color.Black.copy(alpha = 0.03f), spotColor = Color.Black.copy(alpha = 0.03f))
                        .background(colors.cardSurface)
                        .padding(horizontal = 18.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = session.title,
                                color = colors.primaryText,
                                style = FormaTypography.rounded(17f, FontWeight.Bold),
                                maxLines = 1
                            )
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Icon(workoutTypeIcon(workoutType), contentDescription = null, tint = colors.brand, modifier = Modifier.size(12.dp))
                                Text(
                                    text = workoutType.rawValue,
                                    color = colors.brand,
                                    style = FormaTypography.system(11f, FontWeight.Medium),
                                    maxLines = 1
                                )
                                Text("•", color = colors.secondaryText, style = FormaTypography.system(11f))
                                Text(
                                    text = "$completedSets/$totalSets séries",
                                    color = colors.secondaryText,
                                    style = FormaTypography.system(11f, FontWeight.SemiBold),
                                    maxLines = 1
                                )
                                Text("•", color = colors.secondaryText, style = FormaTypography.system(11f))
                                Text(
                                    text = "~$liveKcal kcal",
                                    color = colors.energyGreen,
                                    style = FormaTypography.rounded(11f, FontWeight.Bold),
                                    maxLines = 1
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.04f))
                                .clip(CircleShape)
                                .background(colors.systemBackground)
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(colors.energyGreen)
                            )
                            Text(
                                text = formatTimer(elapsedSeconds),
                                color = colors.primaryText,
                                style = FormaTypography.rounded(18f, FontWeight.Bold).copy(fontFamily = FontFamily.Monospace),
                                maxLines = 1
                            )
                        }
                    }
                    FormaProgressBar(value = progress, total = 1.0, color = colors.brand, height = 6.dp)
                }

                // Banner de descanso
                AnimatedVisibility(
                    visible = session.isResting,
                    enter = fadeIn() + expandVertically(),
                    exit = fadeOut() + shrinkVertically()
                ) {
                    val lastInfo = session.lastCompletedSetInfo
                    val lastSetLabel = if (lastInfo != null && lastInfo.weightKg > 0.0) {
                        "Série ${lastInfo.setNumber} feita: ${WorkoutExercisePlan.formatWeightKg(lastInfo.weightKg)}"
                    } else null
                    ActiveRestTimerBanner(
                        restTimeRemaining = restRemaining,
                        restTimeTotal = session.restDurationSeconds,
                        lastSetLabel = lastSetLabel,
                        onAdjustSeconds = { delta -> manager.addRestSeconds(delta) },
                        onAdjustLastSetWeight = { deltaKg ->
                            haptics.impactLight()
                            manager.adjustRecentSetWeight(deltaKg)
                        },
                        onSkip = { manager.skipRest() },
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp)
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, top = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    if (request.hypertrophyFocus.isNotEmpty() || request.targetMuscles.isNotEmpty()) {
                        HypertrophyInfoBanner(targetMuscles = request.targetMuscles, hypertrophyFocus = request.hypertrophyFocus)
                    }

                    session.exercises.forEachIndexed { exIndex, exercise ->
                        ActiveExerciseCard(
                            exercise = exercise,
                            onOpenInfo = {
                                val catalogItem = ExerciseCatalog.item(exercise.animationId)
                                    ?: ExerciseCatalog.findBestMatch(exercise.name)
                                if (catalogItem != null) {
                                    catalogItemForDetail = catalogItem
                                } else {
                                    exerciseIndexToLink = exIndex
                                    showCatalogSheet = true
                                }
                            },
                            onToggleSet = { setIndex ->
                                val wasDone = exercise.completedSets.getOrElse(setIndex) { false }
                                if (!wasDone) haptics.impactMedium()
                                manager.toggleSet(exIndex, setIndex)
                            },
                            onAdjustSetWeight = { setIndex, deltaKg ->
                                haptics.impactLight()
                                manager.adjustSetWeight(exIndex, setIndex, deltaKg)
                            }
                        )
                    }

                    // Concluir Treino
                    Row(
                        modifier = Modifier
                            .padding(top = 8.dp)
                            .fillMaxWidth()
                            .shadow(8.dp, RoundedCornerShape(16.dp), ambientColor = colors.brand.copy(alpha = 0.25f), spotColor = colors.brand.copy(alpha = 0.25f))
                            .clip(RoundedCornerShape(16.dp))
                            .background(colors.brand)
                            .formaPlainClickable { showFinishConfirmation = true }
                            .padding(vertical = 15.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text(
                            text = "Concluir Treino ($completedSets/$totalSets séries)",
                            color = Color.White,
                            style = FormaTypography.rounded(15f, FontWeight.Bold)
                        )
                    }

                    // Descartar Treino (sem salvar)
                    Box(
                        modifier = Modifier
                            .padding(bottom = 40.dp)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .formaPlainClickable { showCancelConfirmation = true }
                            .padding(vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Descartar treino sem salvar",
                            color = colors.danger.copy(alpha = 0.85f),
                            style = FormaTypography.system(13f, FontWeight.SemiBold)
                        )
                    }
                }
            }

            if (showFinishConfirmation) {
                AlertDialog(
                    onDismissRequest = { showFinishConfirmation = false },
                    title = { Text("Finalizar Treino?") },
                    text = {
                        Text(
                            "Você completou $completedSets de $totalSets séries em ${SharedWorkoutSessionManager.formatTime(elapsedSeconds)}. " +
                                "O gasto de ~$liveKcal kcal será salvo no histórico e no Health Connect."
                        )
                    },
                    confirmButton = {
                        TextButton(onClick = {
                            showFinishConfirmation = false
                            finishAndSave()
                        }) { Text("Concluir e Salvar no App", color = colors.brand) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showFinishConfirmation = false }) {
                            Text("Continuar Treinando", color = colors.brand)
                        }
                    }
                )
            }

            if (showCancelConfirmation) {
                AlertDialog(
                    onDismissRequest = { showCancelConfirmation = false },
                    title = { Text("Deseja descartar este treino?") },
                    text = { Text("O progresso desta sessão não será salvo no histórico.") },
                    confirmButton = {
                        TextButton(onClick = {
                            showCancelConfirmation = false
                            closing = true
                            manager.cancelSession()
                            onDismiss()
                        }) { Text("Descartar Treino", color = colors.danger) }
                    },
                    dismissButton = {
                        TextButton(onClick = { showCancelConfirmation = false }) {
                            Text("Continuar Treinando", color = colors.brand)
                        }
                    }
                )
            }
        }
    }

    catalogItemForDetail?.let { item ->
        ExerciseDetailSheet(item = item, onDismiss = { catalogItemForDetail = null })
    }

    if (showCatalogSheet) {
        ExerciseCatalogSheet(
            onDismiss = { showCatalogSheet = false },
            onSelectExercise = { selected ->
                exerciseIndexToLink?.let { idx -> manager.linkAnimation(idx, selected.id) }
                showCatalogSheet = false
            }
        )
    }
}

/** Cronômetro estilo `Text(date, style: .timer)`: m:ss / h:mm:ss. */
private fun formatTimer(totalSeconds: Int): String {
    val h = totalSeconds / 3600
    val m = (totalSeconds % 3600) / 60
    val s = totalSeconds % 60
    return if (h > 0) String.format(PtBr, "%d:%02d:%02d", h, m, s) else String.format(PtBr, "%d:%02d", m, s)
}

@Composable
private fun HypertrophyInfoBanner(targetMuscles: String, hypertrophyFocus: String) {
    val colors = FormaTheme.Colors
    FormaTintedCard(tint = colors.brand, padding = 12.dp, modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            if (targetMuscles.isNotEmpty()) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = colors.brand, modifier = Modifier.size(12.dp))
                    Text(
                        text = targetMuscles,
                        color = colors.primaryText,
                        style = FormaTypography.system(12f, FontWeight.SemiBold)
                    )
                }
            }
            if (hypertrophyFocus.isNotEmpty()) {
                Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        Icons.Filled.LocalFireDepartment,
                        contentDescription = null,
                        tint = colors.warning,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(11.dp)
                    )
                    Text(
                        text = hypertrophyFocus,
                        color = colors.secondaryText,
                        style = FormaTypography.system(11f)
                    )
                }
            }
        }
    }
}

/** Porte de `ActiveRestTimerBanner`: anel de progresso + contagem regressiva + -15s / +30s / Pular + ajuste de carga da série que passou. */
@Composable
internal fun ActiveRestTimerBanner(
    restTimeRemaining: Int,
    restTimeTotal: Int,
    lastSetLabel: String? = null,
    onAdjustSeconds: (Int) -> Unit,
    onAdjustLastSetWeight: ((Double) -> Unit)? = null,
    onSkip: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val target = if (restTimeTotal > 0) min(1f, max(0f, restTimeRemaining.toFloat() / restTimeTotal.toFloat())) else 0f
    val ringProgress by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 1000, easing = LinearEasing),
        label = "restRing"
    )
    val shape = RoundedCornerShape(16.dp)

    Column(
        modifier = modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.06f), spotColor = Color.Black.copy(alpha = 0.06f))
            .clip(shape)
            .background(colors.cardSurface)
            .border(0.8.dp, colors.energyGreen.copy(alpha = 0.25f), shape)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokePx = 3.5.dp.toPx()
                    val diameter = size.minDimension - strokePx
                    val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                    val arcSize = Size(diameter, diameter)
                    drawArc(
                        color = colors.energyGreen.copy(alpha = 0.20f),
                        startAngle = 0f, sweepAngle = 360f, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(width = strokePx)
                    )
                    drawArc(
                        color = colors.energyGreen,
                        startAngle = -90f, sweepAngle = 360f * ringProgress, useCenter = false,
                        topLeft = topLeft, size = arcSize, style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
                Icon(Icons.Filled.Timer, contentDescription = null, tint = colors.energyGreen, modifier = Modifier.size(16.dp))
            }

            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    text = "Tempo de Descanso",
                    color = colors.secondaryText,
                    style = FormaTypography.system(11f, FontWeight.Bold),
                    maxLines = 1
                )
                Text(
                    text = String.format(PtBr, "%d:%02d", restTimeRemaining / 60, restTimeRemaining % 60),
                    color = colors.energyGreen,
                    style = FormaTypography.rounded(20f, FontWeight.Bold).copy(fontFamily = FontFamily.Monospace),
                    maxLines = 1
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                RestChip(text = "-15s", color = colors.primaryText, background = colors.primary.copy(alpha = 0.07f), horizontal = 8.dp) {
                    onAdjustSeconds(-15)
                }
                RestChip(text = "+30s", color = colors.primaryText, background = colors.primary.copy(alpha = 0.07f), horizontal = 8.dp) {
                    onAdjustSeconds(30)
                }
                RestChip(text = "Pular", color = colors.danger, background = colors.danger.copy(alpha = 0.10f), horizontal = 10.dp) {
                    onSkip()
                }
            }
        }

        if (lastSetLabel != null && onAdjustLastSetWeight != null) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(0.5.dp)
                    .background(colors.primary.copy(alpha = 0.08f))
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = colors.brand, modifier = Modifier.size(12.dp))
                    Text(
                        text = lastSetLabel,
                        color = colors.primaryText,
                        style = FormaTypography.system(11f, FontWeight.SemiBold),
                        maxLines = 1
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    RestChip(text = "-2kg", color = colors.primaryText, background = colors.primary.copy(alpha = 0.07f), horizontal = 8.dp) {
                        onAdjustLastSetWeight(-2.0)
                    }
                    RestChip(text = "+2kg", color = colors.brand, background = colors.brandSoft, horizontal = 8.dp) {
                        onAdjustLastSetWeight(2.0)
                    }
                }
            }
        }
    }
}

@Composable
private fun RestChip(
    text: String,
    color: Color,
    background: Color,
    horizontal: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit
) {
    Text(
        text = text,
        color = color,
        style = FormaTypography.rounded(11f, FontWeight.Bold),
        maxLines = 1,
        modifier = Modifier
            .clip(CircleShape)
            .background(background)
            .formaPlainClickable(onClick = onClick)
            .padding(horizontal = horizontal, vertical = 6.dp)
    )
}

/** Porte de `ActiveExerciseCard`: nome, metadados, dica, botão GIF e checklist de séries com ajuste de carga por série. */
@Composable
internal fun ActiveExerciseCard(
    exercise: WorkoutExercisePlan,
    onOpenInfo: () -> Unit,
    onToggleSet: (Int) -> Unit,
    onAdjustSetWeight: ((setIndex: Int, deltaKg: Double) -> Unit)? = null
) {
    val colors = FormaTheme.Colors
    var showTip by remember { mutableStateOf(false) }
    val shape = RoundedCornerShape(18.dp)
    val borderColor = if (exercise.isAllSetsCompleted) colors.brand.copy(alpha = 0.35f) else colors.primary.copy(alpha = 0.05f)
    val avgKg = exercise.averageWeightKg

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(8.dp, shape, ambientColor = Color.Black.copy(alpha = 0.03f), spotColor = Color.Black.copy(alpha = 0.03f))
            .clip(shape)
            .background(colors.cardSurface)
            .border(1.dp, borderColor, shape)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = exercise.name,
                    color = colors.primaryText,
                    style = FormaTypography.rounded(16f, FontWeight.Bold)
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (exercise.targetMuscle.isNotEmpty()) {
                        Text(
                            text = exercise.targetMuscle,
                            color = colors.brand,
                            style = FormaTypography.system(11f, FontWeight.SemiBold),
                            maxLines = 1
                        )
                        Text("•", color = colors.secondaryText, style = FormaTypography.system(11f))
                    }
                    Text(
                        text = "${exercise.sets} séries • ${exercise.reps} reps",
                        color = colors.secondaryText,
                        style = FormaTypography.system(11f, FontWeight.Medium),
                        maxLines = 1
                    )
                    if (avgKg > 0.0) {
                        Text(
                            text = "• Média ${WorkoutExercisePlan.formatWeightKg(avgKg)}",
                            color = colors.primaryText,
                            style = FormaTypography.system(11f, FontWeight.SemiBold),
                            maxLines = 1
                        )
                    } else if (exercise.suggestedWeight.isNotEmpty()) {
                        Text(
                            text = "• ${exercise.suggestedWeight}",
                            color = colors.primaryText,
                            style = FormaTypography.system(11f, FontWeight.SemiBold),
                            maxLines = 1
                        )
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (exercise.executionTip.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.warning.copy(alpha = 0.12f))
                            .formaPlainClickable { showTip = !showTip },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Filled.Lightbulb, contentDescription = "Dica", tint = colors.warning, modifier = Modifier.size(14.dp))
                    }
                }
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.brandSoft)
                        .formaPlainClickable(onClick = onOpenInfo)
                        .padding(horizontal = 9.dp, vertical = 5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(Icons.Filled.Info, contentDescription = null, tint = colors.brand, modifier = Modifier.size(15.dp))
                    Text("GIF", color = colors.brand, style = FormaTypography.system(11f, FontWeight.Bold))
                }
            }
        }

        AnimatedVisibility(visible = showTip && exercise.executionTip.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(colors.warning.copy(alpha = 0.08f))
                    .padding(10.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = colors.warning,
                    modifier = Modifier
                        .padding(top = 2.dp)
                        .size(11.dp)
                )
                Text(
                    text = exercise.executionTip,
                    color = colors.secondaryText,
                    style = FormaTypography.caption
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.primary.copy(alpha = 0.06f))
        )

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (setIndex in 0 until exercise.sets) {
                val isDone = exercise.completedSets.getOrElse(setIndex) { false }
                val setWeightKg = exercise.weightForSet(setIndex)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isDone) colors.brand.copy(alpha = 0.07f) else colors.systemBackground)
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Série ${setIndex + 1}",
                        color = if (isDone) colors.secondaryText else colors.primaryText,
                        style = FormaTypography.system(13f, FontWeight.Bold),
                        maxLines = 1,
                        modifier = Modifier.width(56.dp)
                    )
                    Text(
                        text = exercise.reps,
                        color = colors.secondaryText,
                        style = FormaTypography.system(12f, FontWeight.Medium),
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.primary.copy(alpha = 0.05f))
                            .padding(horizontal = 4.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colors.primary.copy(alpha = 0.08f))
                                .formaPlainClickable { onAdjustSetWeight?.invoke(setIndex, -2.0) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "−",
                                color = colors.primaryText,
                                style = FormaTypography.rounded(13f, FontWeight.Bold)
                            )
                        }
                        Text(
                            text = WorkoutExercisePlan.formatWeightKg(setWeightKg),
                            color = colors.primaryText,
                            style = FormaTypography.rounded(11f, FontWeight.Bold).copy(fontFamily = FontFamily.Monospace),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier.width(46.dp)
                        )
                        Box(
                            modifier = Modifier
                                .size(24.dp)
                                .clip(CircleShape)
                                .background(colors.brandSoft)
                                .formaPlainClickable { onAdjustSetWeight?.invoke(setIndex, 2.0) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "+",
                                color = colors.brand,
                                style = FormaTypography.rounded(13f, FontWeight.Bold)
                            )
                        }
                    }
                    Spacer(Modifier.width(8.dp))
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isDone) colors.brand else colors.primary.copy(alpha = 0.08f))
                            .formaPlainClickable { onToggleSet(setIndex) },
                        contentAlignment = Alignment.Center
                    ) {
                        if (isDone) {
                            Icon(Icons.Filled.Check, contentDescription = "Série concluída", tint = Color.White, modifier = Modifier.size(16.dp))
                        } else {
                            Box(
                                modifier = Modifier
                                    .size(16.dp)
                                    .border(1.5.dp, colors.secondaryText.copy(alpha = 0.35f), CircleShape)
                            )
                        }
                    }
                }
            }
        }
    }
}

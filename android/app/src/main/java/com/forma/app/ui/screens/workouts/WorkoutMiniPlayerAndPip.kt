package com.forma.app.ui.screens.workouts

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forma.app.data.catalog.ExerciseCatalog
import com.forma.app.data.services.SharedWorkoutSessionManager
import com.forma.app.data.services.SharedWorkoutState
import com.forma.app.ui.components.FormaProgressBar
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.rememberFormaHaptics
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Mini-Player flutuante sobreposto dentro do app durante um treino ativo quando a sessão em tela cheia
 * está minimizada. Permite navegar livremente por todas as abas (Início, Alimentação, Treinos, Evolução, Perfil),
 * arrastar verticalmente na tela, acompanhar o cronômetro/descanso, concluir séries ou pular descanso com 1 toque,
 * e tocar para reabrir o treino em tela cheia.
 */
@Composable
fun WorkoutMiniPlayerOverlay(
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val manager = remember { SharedWorkoutSessionManager.get(context) }
    val session by manager.session.collectAsState()
    val isExpanded by manager.isExpandedInApp.collectAsState()
    val elapsedSeconds by manager.elapsedSeconds.collectAsState()
    val restRemaining by manager.restRemainingSeconds.collectAsState()

    val isVisible = session != null && session?.isFinished == false && !isExpanded
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    AnimatedVisibility(
        visible = isVisible,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
        modifier = modifier
            .offset { IntOffset(0, dragOffsetY.roundToInt()) }
            .pointerInput(Unit) {
                detectVerticalDragGestures { change, dragAmount ->
                    change.consume()
                    dragOffsetY = (dragOffsetY + dragAmount).coerceIn(-1400f, 40f)
                }
            }
    ) {
        val currentSession = session ?: return@AnimatedVisibility
        WorkoutMiniPlayerCard(
            session = currentSession,
            elapsedSeconds = elapsedSeconds,
            restRemaining = restRemaining,
            onExpand = { manager.setExpandedInApp(true) },
            onCompleteSet = { manager.completeCurrentSetFromNotification() },
            onSkipRest = { manager.skipRest() },
            onAddRest = { manager.addRestSeconds(30) },
            onAdjustRecentWeight = { deltaKg -> manager.adjustRecentSetWeight(deltaKg) }
        )
    }
}

@Composable
private fun WorkoutMiniPlayerCard(
    session: SharedWorkoutState,
    elapsedSeconds: Int,
    restRemaining: Int,
    onExpand: () -> Unit,
    onCompleteSet: () -> Unit,
    onSkipRest: () -> Unit,
    onAddRest: () -> Unit,
    onAdjustRecentWeight: (Double) -> Unit
) {
    val colors = FormaTheme.Colors
    val haptics = rememberFormaHaptics()
    val shape = RoundedCornerShape(20.dp)

    val curEx = session.currentExercise
    val curExName = curEx?.name ?: session.title
    val setNumber = min(session.currentSetIndex + 1, curEx?.sets ?: 3)
    val totalSetsForEx = curEx?.sets ?: 3
    val totalSets = max(1, session.totalSetsCount)
    val completedSets = session.completedSetsCount
    val progress = (completedSets.toDouble() / totalSets.toDouble()).coerceIn(0.0, 1.0)
    val liveKcal = session.liveCalories.roundToInt()
    val lastInfo = session.lastCompletedSetInfo
    val displayedWeightKg = lastInfo?.weightKg ?: session.currentSetWeightKg
    val weightBadgeLabel = if (lastInfo != null) {
        "Série ${lastInfo.setNumber} feita: ${com.forma.app.data.services.WorkoutExercisePlan.formatWeightKg(displayedWeightKg)}"
    } else {
        "Carga Série $setNumber: ${com.forma.app.data.services.WorkoutExercisePlan.formatWeightKg(displayedWeightKg)}"
    }

    val accentColor = if (session.isResting) colors.orange else colors.brand

    val infiniteTransition = rememberInfiniteTransition(label = "miniPlayerPulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1.08f,
        animationSpec = infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse),
        label = "pulseScale"
    )

    Column(
        modifier = Modifier
            .padding(horizontal = 12.dp)
            .fillMaxWidth()
            .shadow(
                elevation = 16.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.28f),
                spotColor = accentColor.copy(alpha = 0.35f)
            )
            .clip(shape)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color(0xFF0E1418),
                        Color(0xFF142126)
                    )
                )
            )
            .border(1.dp, accentColor.copy(alpha = 0.45f), shape)
            .formaPlainClickable {
                haptics.selection()
                onExpand()
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Indicador esquerdo: Anel de descanso ou ícone do exercício com pulso
            if (session.isResting) {
                val restTotal = max(1, session.restDurationSeconds)
                val ringTarget = (restRemaining.toFloat() / restTotal.toFloat()).coerceIn(0f, 1f)
                val ringProgress by animateFloatAsState(
                    targetValue = ringTarget,
                    animationSpec = tween(durationMillis = 600, easing = LinearEasing),
                    label = "miniRestRing"
                )
                Box(modifier = Modifier.size(42.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokePx = 3.5.dp.toPx()
                        val d = size.minDimension - strokePx
                        val topLeft = Offset((size.width - d) / 2f, (size.height - d) / 2f)
                        drawArc(
                            color = colors.orange.copy(alpha = 0.22f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(d, d),
                            style = Stroke(width = strokePx)
                        )
                        drawArc(
                            color = colors.orange,
                            startAngle = -90f,
                            sweepAngle = 360f * ringProgress,
                            useCenter = false,
                            topLeft = topLeft,
                            size = Size(d, d),
                            style = Stroke(width = strokePx, cap = StrokeCap.Round)
                        )
                    }
                    Text(
                        text = String.format(PtBr, "%d:%02d", restRemaining / 60, restRemaining % 60),
                        color = colors.orange,
                        style = FormaTypography.rounded(11f, FontWeight.Bold).copy(fontFamily = FontFamily.Monospace),
                        maxLines = 1
                    )
                }
            } else {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.brand.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.FitnessCenter,
                        contentDescription = null,
                        tint = colors.brand,
                        modifier = Modifier
                            .size(20.dp)
                            .scale(pulseScale)
                    )
                }
            }

            // Centro: Exercício/Descanso + Série e Tempo/Kcal
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(7.dp)
                            .clip(CircleShape)
                            .background(if (session.isResting) colors.orange else colors.energyGreen)
                    )
                    Text(
                        text = if (session.isResting) {
                            "Descanso • ${String.format(PtBr, "%02d:%02d", restRemaining / 60, restRemaining % 60)}"
                        } else {
                            curExName
                        },
                        color = Color.White,
                        style = FormaTypography.rounded(14f, FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Text(
                    text = if (session.isResting) {
                        "Próx: Série $setNumber/$totalSetsForEx • $curExName"
                    } else if (session.isAllCompleted) {
                        "Todas as $totalSets séries concluídas • ~$liveKcal kcal"
                    } else {
                        "Série $setNumber/$totalSetsForEx • ${SharedWorkoutSessionManager.formatTime(elapsedSeconds)} • ~$liveKcal kcal"
                    },
                    color = Color(0xFFB0BEC5),
                    style = FormaTypography.system(11f, FontWeight.Medium),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Direita: Ações Rápidas sem precisar abrir a tela cheia
            if (session.isResting) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.10f))
                            .formaPlainClickable {
                                haptics.impactLight()
                                onAddRest()
                            }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+30s",
                            color = Color.White,
                            style = FormaTypography.rounded(11f, FontWeight.Bold)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.orange.copy(alpha = 0.20f))
                            .formaPlainClickable {
                                haptics.impactMedium()
                                onSkipRest()
                            }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(2.dp)
                        ) {
                            Icon(Icons.Filled.SkipNext, contentDescription = null, tint = colors.orange, modifier = Modifier.size(14.dp))
                            Text(
                                text = "Pular",
                                color = colors.orange,
                                style = FormaTypography.rounded(11f, FontWeight.Bold)
                            )
                        }
                    }
                }
            } else if (!session.isAllCompleted) {
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.brand)
                        .formaPlainClickable {
                            haptics.impactMedium()
                            onCompleteSet()
                        }
                        .padding(horizontal = 11.dp, vertical = 7.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = "Concluir série",
                        tint = Color(0xFF091412),
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = "Série $setNumber",
                        color = Color(0xFF091412),
                        style = FormaTypography.rounded(11f, FontWeight.Bold)
                    )
                }
            }

            Icon(
                imageVector = Icons.Filled.KeyboardArrowUp,
                contentDescription = "Expandir treino",
                tint = Color.White.copy(alpha = 0.7f),
                modifier = Modifier.size(20.dp)
            )
        }

        // Barra interativa de carga da série que passou (-2kg / +2kg) no Mini-Player
        if (!session.isAllCompleted && displayedWeightKg > 0.0) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.White.copy(alpha = 0.05f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = weightBadgeLabel,
                    color = Color(0xFFD0D8DE),
                    style = FormaTypography.rounded(11f, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.12f))
                            .formaPlainClickable {
                                haptics.impactLight()
                                onAdjustRecentWeight(-2.0)
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "-2kg",
                            color = Color.White,
                            style = FormaTypography.rounded(11f, FontWeight.Bold)
                        )
                    }
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.brand.copy(alpha = 0.22f))
                            .formaPlainClickable {
                                haptics.impactLight()
                                onAdjustRecentWeight(2.0)
                            }
                            .padding(horizontal = 9.dp, vertical = 4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "+2kg",
                            color = colors.brand,
                            style = FormaTypography.rounded(11f, FontWeight.Bold)
                        )
                    }
                }
            }
        }

        // Barra fina de progresso de séries na base do Mini-Player
        FormaProgressBar(
            value = progress,
            total = 1.0,
            color = accentColor,
            height = 3.dp
        )
    }
}

/**
 * Interface dedicada para a janela flutuante nativa Picture-in-Picture (PiP) do Android quando o usuário
 * sai do app com um treino em andamento.
 */
@Composable
fun WorkoutPipView() {
    val context = LocalContext.current
    val colors = FormaTheme.Colors
    val manager = remember { SharedWorkoutSessionManager.get(context) }
    val session by manager.session.collectAsState()
    val elapsedSeconds by manager.elapsedSeconds.collectAsState()
    val restRemaining by manager.restRemainingSeconds.collectAsState()

    val currentSession = session
    if (currentSession == null || currentSession.isFinished) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0D1114)),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Treino Finalizado",
                color = Color.White,
                style = FormaTypography.rounded(13f, FontWeight.Bold)
            )
        }
        return
    }

    val curEx = currentSession.currentExercise
    val curExName = curEx?.name ?: currentSession.title
    val setNumber = min(currentSession.currentSetIndex + 1, curEx?.sets ?: 3)
    val totalSetsForEx = curEx?.sets ?: 3
    val totalSets = max(1, currentSession.totalSetsCount)
    val completedSets = currentSession.completedSetsCount
    val progress = (completedSets.toDouble() / totalSets.toDouble()).coerceIn(0.0, 1.0)
    val liveKcal = currentSession.liveCalories.roundToInt()
    val catalogItem = remember(curEx?.animationId, curEx?.name) {
        ExerciseCatalog.item(curEx?.animationId) ?: curEx?.name?.let { ExerciseCatalog.findBestMatch(it) }
    }

    val accentColor = if (currentSession.isResting) colors.orange else colors.brand

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color(0xFF0B1013), Color(0xFF132025))
                )
            )
            .border(1.dp, accentColor.copy(alpha = 0.45f), RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        // Topo: Status + Título + Cronômetro geral
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(if (currentSession.isResting) colors.orange else colors.energyGreen)
                )
                Text(
                    text = currentSession.title,
                    color = Color(0xFFB0BEC5),
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${SharedWorkoutSessionManager.formatTime(elapsedSeconds)} • ~$liveKcal kcal",
                color = colors.energyGreen,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                fontFamily = FontFamily.Monospace,
                maxLines = 1
            )
        }

        // Centro: Descanso em destaque OU Exercício atual + GIF
        if (currentSession.isResting) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.orange.copy(alpha = 0.18f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Filled.Timer,
                        contentDescription = null,
                        tint = colors.orange,
                        modifier = Modifier.size(18.dp)
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = String.format(PtBr, "DESCANSO %02d:%02d", restRemaining / 60, restRemaining % 60),
                        color = colors.orange,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.ExtraBold,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1
                    )
                    val lastInfo = currentSession.lastCompletedSetInfo
                    val lastWeightLabel = if (lastInfo != null && lastInfo.weightKg > 0.0) {
                        " • Série ${lastInfo.setNumber}: ${com.forma.app.data.services.WorkoutExercisePlan.formatWeightKg(lastInfo.weightKg)}"
                    } else ""
                    Text(
                        text = "Próx: Série $setNumber/$totalSetsForEx$lastWeightLabel • $curExName",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (catalogItem != null) {
                    GifPlayer(
                        gifName = catalogItem.gifName,
                        cornerRadius = 8.dp,
                        showControls = false,
                        modifier = Modifier.size(40.dp)
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.brand.copy(alpha = 0.16f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FitnessCenter,
                            contentDescription = null,
                            tint = colors.brand,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = curExName,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    val repsText = curEx?.reps?.takeIf { it.isNotEmpty() }?.let { " • $it reps" }.orEmpty()
                    val weightText = curEx?.suggestedWeight?.takeIf { it.isNotEmpty() }?.let { " • $it" }.orEmpty()
                    Text(
                        text = "Série $setNumber/$totalSetsForEx$repsText$weightText",
                        color = colors.brand,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Rodapé: Progresso geral de séries
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "$completedSets/$totalSets séries concluídas",
                    color = Color(0xFF94A3B8),
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${(progress * 100).roundToInt()}%",
                    color = accentColor,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold
                )
            }
            FormaProgressBar(
                value = progress,
                total = 1.0,
                color = accentColor,
                height = 4.dp
            )
        }
    }
}

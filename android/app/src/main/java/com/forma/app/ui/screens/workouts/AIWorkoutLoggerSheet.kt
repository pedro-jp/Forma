package com.forma.app.ui.screens.workouts

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lightbulb
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.StopCircle
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.AddCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forma.app.data.catalog.ExerciseCatalog
import com.forma.app.data.catalog.ExerciseCatalogItem
import com.forma.app.data.model.WorkoutEntry
import com.forma.app.data.model.WorkoutRoutine
import com.forma.app.data.model.WorkoutType
import com.forma.app.data.services.AIDataConsentManager
import com.forma.app.data.services.AIExtractedWorkout
import com.forma.app.data.services.GeminiAIService
import com.forma.app.data.services.VoiceListeningMode
import com.forma.app.data.services.VoicePermissionStatus
import com.forma.app.data.services.VoiceRecognitionService
import com.forma.app.data.services.WorkoutExercisePlan
import com.forma.app.ui.components.AIDataConsentSheet
import com.forma.app.ui.components.FormaBanner
import com.forma.app.ui.components.FormaBannerStyle
import com.forma.app.ui.components.FormaModalSheet
import com.forma.app.ui.components.FormaSheetTopBar
import com.forma.app.ui.components.dismissKeyboardOnTap
import com.forma.app.ui.components.formaHumanizeErrorMessage
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.rememberFormaHaptics
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Porte de `AIWorkoutLoggerView` (iOS): "Criar ou Calcular Treino" com IA — descreva o treino (texto/voz), obtenha
 * calorias (Gemini & Compendium METs), vincule GIFs, salve como ficha, registre como feito ou inicie a sessão ativa.
 */
@Composable
fun AIWorkoutLoggerSheet(
    onDismiss: () -> Unit,
    onWorkoutLogged: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val colors = FormaTheme.Colors
    val scope = rememberCoroutineScope()
    val haptics = rememberFormaHaptics()
    val repository = remember { workoutsRepository() }
    val gemini = remember { GeminiAIService.get(context) }
    val voice = remember { VoiceRecognitionService.get(context) }
    val consentManager = remember { AIDataConsentManager.get(context) }

    val profile by repository.userProfile.collectAsState()
    val isLoading by gemini.isLoading.collectAsState()
    val isRecording by voice.isRecording.collectAsState()
    val audioLevel by voice.audioLevel.collectAsState()
    val recognizedText by voice.recognizedText.collectAsState()
    val listeningMode by voice.listeningMode.collectAsState()
    val hasConsent by consentManager.hasGrantedConsent.collectAsState()

    val userWeight = profile.currentWeight

    var inputText by remember { mutableStateOf("") }
    var analyzedWorkout by remember { mutableStateOf<AIExtractedWorkout?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var showAIConsentSheet by remember { mutableStateOf(false) }

    var isSavedAsRoutine by remember { mutableStateOf(false) }
    val isRoutineFavorited = true
    var isLoggedToHistory by remember { mutableStateOf(false) }
    var activeSession by remember { mutableStateOf<ActiveWorkoutRequest?>(null) }

    var showCatalogSheet by remember { mutableStateOf(false) }
    var exerciseIndexToLink by remember { mutableStateOf<Int?>(null) }
    var catalogItemForDetail by remember { mutableStateOf<ExerciseCatalogItem?>(null) }

    val notificationGate = rememberNotificationPermissionGate()

    // .onChange(of: voiceService.recognizedText)
    LaunchedEffect(recognizedText) {
        if (listeningMode == VoiceListeningMode.WORKOUT && recognizedText.isNotEmpty()) {
            inputText = recognizedText
        }
    }
    DisposableEffect(Unit) {
        onDispose { if (voice.isRecording.value) voice.stopListening(shouldAnalyze = false) }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        voice.onPermissionResult(granted)
        if (granted) voice.startListening(VoiceListeningMode.WORKOUT)
        else errorMessage = "Permissão de microfone necessária para ditar treino por voz."
    }

    fun toggleVoiceRecording() {
        if (isRecording) {
            voice.stopListening(shouldAnalyze = false)
        } else {
            voice.checkPermissions()
            val hasMic = androidx.core.content.ContextCompat.checkSelfPermission(
                context, Manifest.permission.RECORD_AUDIO
            ) == PackageManager.PERMISSION_GRANTED
            if (hasMic && voice.permissionStatus.value != VoicePermissionStatus.RESTRICTED) {
                voice.startListening(VoiceListeningMode.WORKOUT)
            } else if (voice.permissionStatus.value == VoicePermissionStatus.RESTRICTED) {
                errorMessage = "Permissão de microfone necessária para ditar treino por voz."
            } else {
                micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
        }
    }

    fun performWorkoutAnalysis() {
        if (inputText.trim().isEmpty()) return
        errorMessage = null
        isSavedAsRoutine = false
        isLoggedToHistory = false
        val description = inputText
        scope.launch {
            try {
                analyzedWorkout = gemini.analyzeWorkout(description = description, userWeight = userWeight)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = e.message ?: "Erro ao analisar treino."
            }
        }
    }

    fun analyzeWorkoutText() {
        if (inputText.trim().isEmpty()) return
        if (!hasConsent) {
            showAIConsentSheet = true
            return
        }
        performWorkoutAnalysis()
    }

    fun autoAssociateAllGIFs() {
        val current = analyzedWorkout ?: return
        analyzedWorkout = current.copy(exercises = current.exercises.map { it.withCatalogAssociation() })
        haptics.success()
    }

    fun saveAsRoutine(workout: AIExtractedWorkout) {
        val routine = WorkoutRoutine(
            title = workout.title,
            workoutType = WorkoutType.fromRaw(workout.workoutType).rawValue,
            targetMuscles = workout.targetMuscles,
            hypertrophyFocus = workout.hypertrophyFocus,
            estimatedDurationMinutes = workout.durationMinutes,
            estimatedCalories = workout.caloriesBurned,
            recommendedRestSeconds = workout.recommendedRestSeconds,
            isFavorite = isRoutineFavorited,
            exercises = workout.exercises.map { it.toItem() },
            notes = workout.aiFeedback
        )
        errorMessage = null
        scope.launch {
            try {
                repository.saveWorkoutRoutineRemoteFirst(routine)
                haptics.success()
                isSavedAsRoutine = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = formaHumanizeErrorMessage(e.message.orEmpty())
            }
        }
    }

    fun logDirectlyToHistory(workout: AIExtractedWorkout) {
        val entry = WorkoutEntry(
            title = workout.title,
            workoutType = WorkoutType.fromRaw(workout.workoutType).rawValue,
            durationMinutes = workout.durationMinutes,
            caloriesBurned = workout.caloriesBurned,
            notes = workout.aiFeedback,
            workoutDate = System.currentTimeMillis()
        )
        errorMessage = null
        scope.launch {
            try {
                repository.insertWorkoutEntryRemoteFirst(entry)
                haptics.success()
                isLoggedToHistory = true
                delay(900)
                onWorkoutLogged?.invoke()
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                errorMessage = formaHumanizeErrorMessage(e.message.orEmpty())
            }
        }
    }

    val canAnalyze = inputText.trim().isNotEmpty()

    FormaModalSheet(onDismissRequest = onDismiss) {
        FormaSheetTopBar(
            title = "Criar ou Calcular Treino",
            cancelTitle = "Fechar",
            onCancel = onDismiss
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .dismissKeyboardOnTap()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Header com Resumo Fisiológico
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(16.dp), ambientColor = Color.Black.copy(alpha = 0.03f), spotColor = Color.Black.copy(alpha = 0.03f))
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.cardSurface)
                    .padding(14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.brand.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.FitnessCenter, contentDescription = null, tint = colors.brand, modifier = Modifier.size(22.dp))
                }
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Análise de Treino & Gasto Calórico",
                        color = colors.primaryText,
                        style = FormaTypography.rounded(14f, FontWeight.Bold)
                    )
                    Text(
                        text = "Calculado com IA Google Gemini & Compendium METs para ${userWeight.toInt()} kg",
                        color = colors.secondaryText,
                        style = FormaTypography.system(11f)
                    )
                }
            }

            // Card de Entrada Livre (Texto / Descrição)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = 0.03f), spotColor = Color.Black.copy(alpha = 0.03f))
                    .clip(RoundedCornerShape(18.dp))
                    .background(colors.cardSurface)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = "DESCREVA SEU TREINO OU EXERCÍCIO",
                    color = colors.secondaryText,
                    style = FormaTypography.system(11f, FontWeight.Bold)
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.systemBackground)
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Box(modifier = Modifier.weight(1f)) {
                        if (inputText.isEmpty()) {
                            Text(
                                text = "Ex: 'Supino reto 4x10 com 30kg/lado, crucifixo inclinado com halteres de 20kg e tríceps corda'",
                                color = colors.tertiaryText,
                                style = TextStyle(fontSize = 15.sp)
                            )
                        }
                        BasicTextField(
                            value = inputText,
                            onValueChange = { inputText = it },
                            minLines = 3,
                            maxLines = 6,
                            textStyle = TextStyle(fontSize = 15.sp, color = colors.primaryText),
                            cursorBrush = SolidColor(colors.brand),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        // Ícone único do microfone diretamente na caixa de texto
                        if (isRecording) {
                            Icon(
                                imageVector = Icons.Filled.StopCircle,
                                contentDescription = "Parar gravação",
                                tint = colors.red,
                                modifier = Modifier
                                    .size(24.dp)
                                    .scale(1f + audioLevel * 0.4f)
                                    .formaPlainClickable { toggleVoiceRecording() }
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Mic,
                                contentDescription = "Ditar por voz",
                                tint = colors.brand,
                                modifier = Modifier
                                    .size(22.dp)
                                    .formaPlainClickable { toggleVoiceRecording() }
                            )
                        }
                        if (inputText.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Filled.Cancel,
                                contentDescription = "Limpar",
                                tint = colors.gray.copy(alpha = 0.6f),
                                modifier = Modifier
                                    .size(20.dp)
                                    .formaPlainClickable {
                                        inputText = ""
                                        analyzedWorkout = null
                                        isSavedAsRoutine = false
                                        isLoggedToHistory = false
                                    }
                            )
                        }
                    }
                }

                errorMessage?.let { error ->
                    FormaBanner(
                        message = error,
                        style = FormaBannerStyle.Error,
                        retryTitle = "Repetir",
                        onRetry = if (canAnalyze) ({ analyzeWorkoutText() }) else null
                    )
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (canAnalyze) colors.brand else colors.gray.copy(alpha = 0.35f))
                        .formaPlainClickable(enabled = canAnalyze && !isLoading) { analyzeWorkoutText() }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text("Montando seu treino...", color = Color.White, style = FormaTypography.system(15f, FontWeight.Bold))
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                        Text("Criar ou Calcular Treino", color = Color.White, style = FormaTypography.system(15f, FontWeight.Bold))
                    }
                }
            }

            // Sugestões Rápidas de Treinos Populares
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Exemplos para testar:",
                    color = colors.secondaryText,
                    style = FormaTypography.system(12f, FontWeight.SemiBold)
                )
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    listOf(
                        "Peito & Tríceps Hipertrofia 50min",
                        "Pernas Completo Foco Quadríceps",
                        "Costas & Bíceps Sobrecarga",
                        "Ombros e Trapézio 45min",
                        "Corrida na esteira 5km 30min"
                    ).forEach { text ->
                        Text(
                            text = text,
                            color = colors.brand,
                            style = FormaTypography.system(12f, FontWeight.Medium),
                            maxLines = 1,
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.brand.copy(alpha = 0.08f))
                                .formaPlainClickable {
                                    inputText = text
                                    analyzeWorkoutText()
                                }
                                .padding(horizontal = 12.dp, vertical = 8.dp)
                        )
                    }
                }
            }

            // Resultado da Análise de Hipertrofia e Calorias
            analyzedWorkout?.let { workout ->
                WorkoutAnalysisResultCard(
                    workout = workout,
                    isSavedAsRoutine = isSavedAsRoutine,
                    isRoutineFavorited = isRoutineFavorited,
                    isLoggedToHistory = isLoggedToHistory,
                    onAutoAssociate = ::autoAssociateAllGIFs,
                    onOpenInfo = { index, catalogItem ->
                        if (catalogItem != null) {
                            catalogItemForDetail = catalogItem
                        } else {
                            exerciseIndexToLink = index
                            showCatalogSheet = true
                        }
                    },
                    onStartSession = {
                        notificationGate {
                            com.forma.app.data.services.SharedWorkoutSessionManager.get(context).resumeOrStart(
                                title = workout.title,
                                workoutTypeRaw = WorkoutType.fromRaw(workout.workoutType).rawValue,
                                exercises = workout.exercises,
                                baseCalories = workout.caloriesBurned,
                                baseDurationMinutes = workout.durationMinutes,
                                initialRestSeconds = if (workout.recommendedRestSeconds > 0) workout.recommendedRestSeconds else 90,
                                targetMuscles = workout.targetMuscles,
                                hypertrophyFocus = workout.hypertrophyFocus
                            )
                            onDismiss()
                        }
                    },
                    onSaveAsRoutine = { saveAsRoutine(workout) },
                    onLogToHistory = { logDirectlyToHistory(workout) }
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
                val idx = exerciseIndexToLink
                val current = analyzedWorkout
                if (idx != null && current != null && idx < current.exercises.size) {
                    val list = current.exercises.toMutableList()
                    list[idx] = list[idx].copy(animationId = selected.id)
                    analyzedWorkout = current.copy(exercises = list)
                }
                showCatalogSheet = false
            }
        )
    }

    if (showAIConsentSheet) {
        AIDataConsentSheet(
            onGranted = {
                showAIConsentSheet = false
                performWorkoutAnalysis()
            },
            onDeclined = { showAIConsentSheet = false }
        )
    }

    activeSession?.let { request ->
        ActiveWorkoutSessionDialog(request = request, onDismiss = { activeSession = null })
    }
}

@Composable
private fun WorkoutAnalysisResultCard(
    workout: AIExtractedWorkout,
    isSavedAsRoutine: Boolean,
    isRoutineFavorited: Boolean,
    isLoggedToHistory: Boolean,
    onAutoAssociate: () -> Unit,
    onOpenInfo: (index: Int, catalogItem: ExerciseCatalogItem?) -> Unit,
    onStartSession: () -> Unit,
    onSaveAsRoutine: () -> Unit,
    onLogToHistory: () -> Unit
) {
    val colors = FormaTheme.Colors
    val shape = RoundedCornerShape(20.dp)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(10.dp, shape, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.04f))
            .clip(shape)
            .background(colors.cardSurface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        // Header do Treino
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = workout.workoutType.uppercase(PtBr),
                        color = colors.brand,
                        style = FormaTypography.system(10f, FontWeight.Bold),
                        maxLines = 1,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.brand.copy(alpha = 0.1f))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                    Text(
                        text = workout.intensity,
                        color = colors.secondaryText,
                        style = FormaTypography.system(10f, FontWeight.SemiBold),
                        maxLines = 1
                    )
                }
                Text(
                    text = workout.title,
                    color = colors.primaryText,
                    style = FormaTypography.rounded(18f, FontWeight.Bold)
                )
            }

            // Queima Calórica Destacada
            Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Icon(Icons.Filled.LocalFireDepartment, contentDescription = null, tint = colors.energyGreen, modifier = Modifier.size(20.dp))
                    Text(
                        text = "${workout.caloriesBurned.roundToInt()}",
                        color = colors.energyGreen,
                        style = FormaTypography.rounded(26f, FontWeight.Bold)
                    )
                }
                Text("kcal estimadas", color = colors.secondaryText, style = FormaTypography.system(11f))
            }
        }

        // Pílulas de Métricas
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MetricPill("Duração", "${workout.durationMinutes} min", Icons.Filled.Schedule, Modifier.weight(1f))
            MetricPill("Descanso", "${workout.recommendedRestSeconds}s", Icons.Filled.Timer, Modifier.weight(1f))
            MetricPill("Exercícios", "${workout.exercises.size}", Icons.Filled.FormatListBulleted, Modifier.weight(1f))
        }

        // Card de Hipertrofia & Fisiologia
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(14.dp))
                .background(colors.systemBackground)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (workout.targetMuscles.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("MÚSCULOS ENVOLVIDOS", color = colors.secondaryText, style = FormaTypography.system(10f, FontWeight.Bold))
                    Text(workout.targetMuscles, color = colors.primaryText, style = FormaTypography.system(13f, FontWeight.SemiBold))
                }
            }
            if (workout.hypertrophyFocus.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("FOCO DO TREINO", color = colors.secondaryText, style = FormaTypography.system(10f, FontWeight.Bold))
                    Row(verticalAlignment = Alignment.Top, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(
                            Icons.Filled.Bolt,
                            contentDescription = null,
                            tint = colors.orange,
                            modifier = Modifier
                                .padding(top = 1.dp)
                                .size(13.dp)
                        )
                        Text(workout.hypertrophyFocus, color = colors.secondaryText, style = FormaTypography.system(12f))
                    }
                }
            }
            if (workout.aiFeedback.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.brand.copy(alpha = 0.06f))
                        .padding(10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Filled.Lightbulb,
                        contentDescription = null,
                        tint = colors.brand,
                        modifier = Modifier
                            .padding(top = 1.dp)
                            .size(13.dp)
                    )
                    Text(workout.aiFeedback, color = colors.secondaryText, style = FormaTypography.system(12f))
                }
            }
        }

        // Lista de Exercícios para Checklist com GIFs de Execução
        if (workout.exercises.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "EXERCÍCIOS IDENTIFICADOS (${workout.exercises.size})",
                        color = colors.secondaryText,
                        style = FormaTypography.system(11f, FontWeight.Bold),
                        modifier = Modifier.weight(1f)
                    )
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.brand.copy(alpha = 0.1f))
                            .formaPlainClickable(onClick = onAutoAssociate)
                            .padding(horizontal = 10.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = colors.brand, modifier = Modifier.size(12.dp))
                        Text("Vincular Exercícios", color = colors.brand, style = FormaTypography.system(11f, FontWeight.Bold))
                    }
                }
                workout.exercises.forEachIndexed { index, exercise ->
                    ExercisePreviewRow(exercise = exercise, onInfo = { onOpenInfo(index, it) })
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.primary.copy(alpha = 0.08f))
        )

        // Ações: Iniciar com Checklist, Salvar Ficha, ou Registrar
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            // 1. Iniciar com Checklist Agora (Auxílio ao Vivo durante o treino)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .shadow(6.dp, RoundedCornerShape(14.dp), ambientColor = colors.brand.copy(alpha = 0.25f), spotColor = colors.brand.copy(alpha = 0.25f))
                    .clip(RoundedCornerShape(14.dp))
                    .background(colors.brand)
                    .formaPlainClickable(onClick = onStartSession)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Filled.PlayCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                Text("Iniciar Treino com Checklist & Timer", color = Color.White, style = FormaTypography.system(15f, FontWeight.Bold))
            }

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                // 2. Salvar como Ficha / Rotina Salva
                val saveIcon: ImageVector = when {
                    isSavedAsRoutine -> Icons.Filled.CheckCircle
                    isRoutineFavorited -> Icons.Filled.Star
                    else -> Icons.Filled.StarBorder
                }
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.systemBackground)
                        .border(BorderStroke(1.dp, colors.primary.copy(alpha = 0.08f)), RoundedCornerShape(12.dp))
                        .formaPlainClickable(onClick = onSaveAsRoutine)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        saveIcon,
                        contentDescription = null,
                        tint = if (isSavedAsRoutine) colors.green else colors.orange,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isSavedAsRoutine) "Ficha Salva!" else "Salvar Ficha",
                        color = colors.primaryText,
                        style = FormaTypography.system(13f, FontWeight.SemiBold),
                        maxLines = 1
                    )
                }

                // 3. Registrar como Treino Já Feito Hoje
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (isLoggedToHistory) colors.green else colors.gray.copy(alpha = 0.12f))
                        .formaPlainClickable(onClick = onLogToHistory)
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        if (isLoggedToHistory) Icons.Filled.Check else Icons.Filled.AddCircleOutline,
                        contentDescription = null,
                        tint = if (isLoggedToHistory) Color.White else colors.primaryText,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = if (isLoggedToHistory) "Registrado!" else "Marcar Feito",
                        color = if (isLoggedToHistory) Color.White else colors.primaryText,
                        style = FormaTypography.system(13f, FontWeight.SemiBold),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ExercisePreviewRow(exercise: WorkoutExercisePlan, onInfo: (ExerciseCatalogItem?) -> Unit) {
    val colors = FormaTheme.Colors
    val catalogItem = ExerciseCatalog.item(exercise.animationId) ?: ExerciseCatalog.findBestMatch(exercise.name)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.systemBackground)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(exercise.name, color = colors.primaryText, style = FormaTypography.system(15f, FontWeight.Bold))
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "${exercise.sets}x ${exercise.reps}",
                    color = colors.brand,
                    style = FormaTypography.system(12f, FontWeight.SemiBold),
                    maxLines = 1
                )
                if (exercise.suggestedWeight.isNotEmpty()) {
                    Text("•", color = colors.secondaryText)
                    Text(exercise.suggestedWeight, color = colors.secondaryText, style = FormaTypography.system(11f), maxLines = 1)
                }
                if (exercise.targetMuscle.isNotEmpty()) {
                    Text("•", color = colors.secondaryText)
                    Text(exercise.targetMuscle, color = colors.secondaryText, style = FormaTypography.system(11f), maxLines = 1)
                }
            }
        }

        // Botão de Info do Exercício (Abre GIF em alta resolução e biomecânica)
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.brand.copy(alpha = 0.1f))
                .formaPlainClickable { onInfo(catalogItem) }
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(Icons.Filled.Info, contentDescription = null, tint = colors.brand, modifier = Modifier.size(17.dp))
            Text("Info", color = colors.brand, style = FormaTypography.system(12f, FontWeight.Bold))
        }
    }
}

@Composable
private fun MetricPill(title: String, value: String, icon: ImageVector, modifier: Modifier = Modifier) {
    val colors = FormaTheme.Colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(colors.systemBackground)
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Icon(icon, contentDescription = null, tint = colors.brand, modifier = Modifier.size(12.dp))
        Text(value, color = colors.primaryText, style = FormaTypography.rounded(13f, FontWeight.Bold), maxLines = 1)
        Text(title, color = colors.secondaryText, style = FormaTypography.system(10f), maxLines = 1)
    }
}

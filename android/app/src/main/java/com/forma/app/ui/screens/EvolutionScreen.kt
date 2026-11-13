package com.forma.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forma.app.data.model.AIRecommendedRoutine
import com.forma.app.data.model.EvolutionAIAnalysis
import com.forma.app.data.model.SuggestedNutritionTargets
import com.forma.app.data.model.WeightEntry
import com.forma.app.data.model.WorkoutRoutine
import com.forma.app.data.model.WorkoutType
import com.forma.app.data.repository.FormaRepository
import com.forma.app.data.services.AIDataConsentManager
import com.forma.app.data.services.AIEvolutionResult
import com.forma.app.data.services.AiServiceException
import com.forma.app.data.services.EvolutionAnalysisRequest
import com.forma.app.data.services.GeminiAIService
import com.forma.app.ui.components.AIDataConsentSheet
import com.forma.app.ui.components.FormaBadge
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaEmptyState
import com.forma.app.ui.components.FormaScreenHeader
import com.forma.app.ui.components.FormaTintedCard
import com.forma.app.ui.components.formaHumanizeErrorMessage
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.rememberFormaHaptics
import com.forma.app.ui.screens.evolution.DayCalorieSummary
import com.forma.app.ui.screens.evolution.EvolutionAICoachSection
import com.forma.app.ui.screens.evolution.EvolutionCalculator
import com.forma.app.ui.screens.evolution.LogWeightSheet
import com.forma.app.ui.screens.evolution.WeeklyCalorieChartCard
import com.forma.app.ui.screens.evolution.WeeklyHabitsChartCard
import com.forma.app.ui.screens.evolution.WeightTrendChartCard
import com.forma.app.ui.screens.evolution.fmt1
import com.forma.app.ui.screens.evolution.symbolFor
import com.forma.app.ui.screens.evolution.toIntSafe
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

// Porte de Forma/Views/Evolution/EvolutionView.swift
// Aba de Evolução & Análise com gráficos interativos (Canvas), Apple Saúde -> Health Connect e Treinador IA.

/** `EvolutionSection` (iOS). */
enum class EvolutionSection(val title: String, val icon: String) {
    OVERVIEW("Visão Geral", "chart.xyaxis.line"),
    AI_ANALYSIS("Inteligência Forma", "sparkles")
}

private val ptBR = Locale("pt", "BR")

/** Converte o resultado do `GeminiAIService` no modelo persistido (`EvolutionAIAnalysis`). */
private fun AIEvolutionResult.toAnalysis(): EvolutionAIAnalysis = EvolutionAIAnalysis(
    id = id,
    statusSummary = statusSummary,
    calorieDiagnostic = calorieDiagnostic,
    macroDiagnostic = macroDiagnostic,
    hydrationDiagnostic = hydrationDiagnostic,
    workoutSuggestions = workoutSuggestions,
    nutritionSuggestions = nutritionSuggestions,
    weeklyActionPlan = weeklyActionPlan,
    suggestedTargets = suggestedTargets?.let {
        SuggestedNutritionTargets(
            dailyCalories = it.dailyCalories,
            proteinGrams = it.proteinGrams,
            carbsGrams = it.carbsGrams,
            fatGrams = it.fatGrams,
            waterMilliliters = it.waterMilliliters,
            explanation = it.explanation
        )
    },
    recommendedRoutine = recommendedRoutine?.let { r ->
        AIRecommendedRoutine(
            title = r.title,
            workoutType = r.workoutType,
            targetMuscles = r.targetMuscles,
            hypertrophyFocus = r.hypertrophyFocus,
            estimatedDurationMinutes = r.estimatedDurationMinutes,
            estimatedCalories = r.estimatedCalories,
            recommendedRestSeconds = r.recommendedRestSeconds,
            exercises = r.exercises.map { ex ->
                com.forma.app.data.model.WorkoutExerciseItem(
                    id = ex.id,
                    name = ex.name,
                    sets = ex.sets,
                    reps = ex.reps,
                    suggestedWeight = ex.suggestedWeight,
                    targetMuscle = ex.targetMuscle,
                    executionTip = ex.executionTip,
                    restSeconds = ex.restSeconds,
                    completedSets = ex.completedSets.toMutableList(),
                    animationId = ex.animationId
                )
            }
        )
    },
    generatedAt = generatedAt
)

@Composable
fun EvolutionScreen(repository: FormaRepository) {
    val colors = FormaTheme.Colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = rememberFormaHaptics()
    val consentManager = remember { AIDataConsentManager.get(context) }

    val profile by repository.userProfile.collectAsState()
    val weightEntries by repository.weightEntries.collectAsState()
    val foodEntries by repository.foodEntries.collectAsState()
    val workoutEntries by repository.workoutEntries.collectAsState()
    val waterEntries by repository.waters.collectAsState()
    val latestAnalysis by repository.latestAnalysis.collectAsState()
    val healthActiveCalories by repository.healthActiveCalories.collectAsState()
    val hasConsent by consentManager.hasGrantedConsent.collectAsState()

    var selectedSection by remember { mutableStateOf(EvolutionSection.OVERVIEW) }
    var showLogWeight by remember { mutableStateOf(false) }
    var showAIConsentSheet by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    var isAnalyzingAI by remember { mutableStateOf(false) }
    var aiAnalysis by remember { mutableStateOf<EvolutionAIAnalysis?>(latestAnalysis) }
    var aiErrorMessage by remember { mutableStateOf<String?>(null) }
    var savedRoutineSuccessfully by remember { mutableStateOf(false) }
    var appliedTargetsSuccessfully by remember { mutableStateOf(false) }

    // .onAppear { healthVM.loadHealthData(); syncExternalHealthWorkoutsInBackground; latestAnalysis }
    LaunchedEffect(Unit) {
        runCatching { repository.syncTodayData(skipCloudSyncTrigger = true) }
        runCatching { repository.syncExternalHealthWorkoutsInBackground() }
    }
    LaunchedEffect(latestAnalysis) {
        if (aiAnalysis == null) aiAnalysis = latestAnalysis
    }

    val currentWeight = if (profile.currentWeight > 0) profile.currentWeight else (weightEntries.firstOrNull()?.weight ?: 72.0)
    val targetWeight = profile.targetWeight
    val height = profile.heightMeters
    val goal = profile.goal

    val recalcContext = remember(profile, weightEntries, workoutEntries, foodEntries) { repository.buildRecalculationContext() }
    val stats = remember(profile, currentWeight, recalcContext) {
        EvolutionCalculator.calculate(
            weight = currentWeight,
            height = height,
            goal = goal,
            targetWeight = targetWeight,
            targetCalorieBurn = profile.targetCalorieBurn,
            targetSteps = profile.targetSteps,
            context = recalcContext
        )
    }

    val weeklySummaries = remember(foodEntries, workoutEntries, waterEntries, profile, stats, healthActiveCalories) {
        buildWeeklySummaries(
            foods = foodEntries,
            workouts = workoutEntries,
            waters = waterEntries,
            proteinTarget = max(1.0, profile.targetProtein),
            waterTarget = max(500, profile.targetWaterMilliliters).toDouble(),
            tmb = stats.tmb,
            healthActiveCalories = healthActiveCalories
        )
    }

    // MARK: - Lógica de Negócio

    fun showToast(message: String) {
        toastMessage = message
    }

    fun performEvolutionAIAnalysis() {
        isAnalyzingAI = true
        aiErrorMessage = null
        appliedTargetsSuccessfully = false
        savedRoutineSuccessfully = false

        scope.launch {
            try {
                val result = withContext(Dispatchers.IO) {
                    repository.activityLog.syncFromDatabase(
                        foods = repository.foodEntries.value,
                        workouts = repository.workoutEntries.value,
                        waters = repository.waters.value,
                        weights = repository.weightEntries.value
                    )
                    val p = repository.userProfile.value
                    val request = EvolutionAnalysisRequest.build(
                        goal = p.goal,
                        currentWeight = p.currentWeight,
                        targetWeight = p.targetWeight,
                        heightMeters = p.heightMeters,
                        targetCalorieIntake = p.targetCalorieIntake,
                        targetCalorieBurn = p.targetCalorieBurn,
                        targetSteps = p.targetSteps,
                        recalcContext = repository.buildRecalculationContext(),
                        activityLogJson = repository.activityLog.exportRecentLogForAI(days = 14),
                        userName = p.name
                    )
                    GeminiAIService.get(context).analyzeEvolution(request)
                }
                val analysis = result.toAnalysis().sanitized()
                aiAnalysis = analysis
                isAnalyzingAI = false
                launch { runCatching { repository.saveEvolutionAnalysisRemoteFirst(analysis) } }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val raw = if (e is AiServiceException) e.message else (e.message ?: "")
                aiErrorMessage = formaHumanizeErrorMessage(raw)
                isAnalyzingAI = false
            }
        }
    }

    fun generateAIAnalysis() {
        if (!hasConsent) {
            showAIConsentSheet = true
            return
        }
        performEvolutionAIAnalysis()
    }

    fun applySuggestedTargets(targets: SuggestedNutritionTargets) {
        val updated = repository.userProfile.value.copy(
            targetCalorieIntake = targets.dailyCalories,
            targetProtein = targets.proteinGrams,
            targetCarbohydrates = targets.carbsGrams,
            targetFat = targets.fatGrams,
            targetWaterMilliliters = targets.waterMilliliters
        )
        scope.launch {
            try {
                repository.saveProfileRemoteFirst(updated)
                appliedTargetsSuccessfully = true
                showToast("Metas de ${targets.dailyCalories.toIntSafe()} kcal e macros aplicadas e salvas no banco!")
                haptics.success()
                delay(3500)
                toastMessage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Erro ao sincronizar metas: ${formaHumanizeErrorMessage(e.message.orEmpty())}")
                delay(3500)
                toastMessage = null
            }
        }
    }

    fun saveRecommendedRoutine(data: AIRecommendedRoutine) {
        val newRoutine = WorkoutRoutine(
            title = data.title,
            workoutType = WorkoutType.fromRaw(data.workoutType).title,
            targetMuscles = data.targetMuscles,
            hypertrophyFocus = data.hypertrophyFocus,
            estimatedDurationMinutes = data.estimatedDurationMinutes,
            estimatedCalories = data.estimatedCalories,
            recommendedRestSeconds = data.recommendedRestSeconds,
            exercises = data.exercises,
            notes = "✨ Gerado pela Inteligência Forma"
        )
        scope.launch {
            try {
                repository.saveWorkoutRoutineRemoteFirst(newRoutine)
                savedRoutineSuccessfully = true
                showToast("Ficha salva no banco e adicionada na aba de Treinos!")
                haptics.success()
                delay(3500)
                toastMessage = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Erro ao sincronizar ficha: ${formaHumanizeErrorMessage(e.message.orEmpty())}")
                delay(3500)
                toastMessage = null
            }
        }
    }

    fun deleteWeightEntry(entry: WeightEntry) {
        scope.launch {
            try {
                repository.deleteWeightEntryRemoteFirst(entry.id)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                showToast("Erro ao sincronizar pesagem: ${formaHumanizeErrorMessage(e.message.orEmpty())}")
                delay(3500)
                toastMessage = null
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
    ) {
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(colors.energyGreen)
                    .padding(horizontal = 16.dp, vertical = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(symbolFor("checkmark.circle.fill"), null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text(
                    text = toastMessage.orEmpty(),
                    color = Color.White,
                    style = FormaTypography.system(13f, FontWeight.Bold)
                )
            }
        }

        // topHeaderBar
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(colors.cardSurface)
                .padding(bottom = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            FormaScreenHeader(
                title = "Evolução & Análise",
                subtitle = "Seu progresso e Inteligência Forma",
                modifier = Modifier
                    .padding(horizontal = FormaTheme.Spacing.screenHorizontal)
                    .padding(top = 8.dp),
                trailing = {
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.brand)
                            .formaPlainClickable { showLogWeight = true }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(symbolFor("plus"), null, tint = Color.White, modifier = Modifier.size(11.dp))
                        Text(
                            text = "Peso",
                            color = Color.White,
                            style = FormaTypography.rounded(12f, FontWeight.Bold)
                        )
                    }
                }
            )

            Row(
                modifier = Modifier
                    .padding(horizontal = FormaTheme.Spacing.screenHorizontal)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.primary.copy(alpha = 0.06f))
                    .padding(3.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                EvolutionSection.values().forEach { section ->
                    val selected = selectedSection == section
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (selected) colors.brand else Color.Transparent)
                            .formaPlainClickable { selectedSection = section }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = symbolFor(section.icon),
                            contentDescription = null,
                            tint = if (selected) Color.White else colors.secondaryText,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = section.title,
                            color = if (selected) Color.White else colors.secondaryText,
                            style = FormaTypography.system(12.5f, if (selected) FontWeight.Bold else FontWeight.Medium),
                            maxLines = 1
                        )
                    }
                }
            }
        }

        HorizontalDivider(color = colors.primary.copy(alpha = 0.08f))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(top = 16.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(FormaTheme.Spacing.sectionGap)
        ) {
            when (selectedSection) {
                EvolutionSection.OVERVIEW -> {
                    val h = FormaTheme.Spacing.screenHorizontal
                    WeightSummaryHeroCard(
                        goalIcon = goal.sfSymbol,
                        goalTitle = goal.title,
                        goalSubtitle = goal.subtitle,
                        isGoalAchieved = stats.isGoalAchieved,
                        remainingKg = stats.remainingWeightToGoal,
                        currentWeight = currentWeight,
                        targetWeight = targetWeight,
                        modifier = Modifier.padding(horizontal = h)
                    )

                    WeightTrendChartCard(
                        weightEntries = weightEntries,
                        currentWeight = currentWeight,
                        targetWeight = targetWeight,
                        onLogWeightTap = { showLogWeight = true },
                        modifier = Modifier.padding(horizontal = h)
                    )

                    WeeklyCalorieChartCard(
                        summaries = weeklySummaries,
                        dailyCalorieTarget = profile.targetCalorieIntake,
                        modifier = Modifier.padding(horizontal = h)
                    )

                    WeeklyHabitsChartCard(
                        summaries = weeklySummaries,
                        modifier = Modifier.padding(horizontal = h)
                    )

                    MetabolismMetricsSection(
                        imc = stats.imc,
                        imcClassification = stats.imcClassification,
                        tmb = stats.tmb,
                        tdee = stats.tdee,
                        modifier = Modifier.padding(horizontal = h)
                    )

                    WeightsHistorySection(
                        entries = weightEntries,
                        onLogWeight = { showLogWeight = true },
                        onDelete = { deleteWeightEntry(it) }
                    )
                }

                EvolutionSection.AI_ANALYSIS -> EvolutionAICoachSection(
                    analysis = aiAnalysis,
                    isAnalyzing = isAnalyzingAI,
                    errorMessage = aiErrorMessage,
                    appliedTargetsSuccessfully = appliedTargetsSuccessfully,
                    savedRoutineSuccessfully = savedRoutineSuccessfully,
                    onGenerateAnalysis = { generateAIAnalysis() },
                    onApplyTargets = { applySuggestedTargets(it) },
                    onSaveRoutine = { saveRecommendedRoutine(it) }
                )
            }
        }
    }

    if (showLogWeight) {
        LogWeightSheet(onDismiss = { showLogWeight = false })
    }

    if (showAIConsentSheet) {
        AIDataConsentSheet(
            onGranted = {
                showAIConsentSheet = false
                performEvolutionAIAnalysis()
            },
            onDeclined = { showAIConsentSheet = false }
        )
    }
}

private fun buildWeeklySummaries(
    foods: List<com.forma.app.data.model.FoodEntry>,
    workouts: List<com.forma.app.data.model.WorkoutEntry>,
    waters: List<com.forma.app.data.model.WaterEntry>,
    proteinTarget: Double,
    waterTarget: Double,
    tmb: Double,
    healthActiveCalories: Double
): List<DayCalorieSummary> {
    val dayFormatter = SimpleDateFormat("EEE", ptBR)
    val summaries = ArrayList<DayCalorieSummary>()
    for (i in 6 downTo 0) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            add(Calendar.DAY_OF_YEAR, -i)
        }
        val dayStart = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        val dayEnd = cal.timeInMillis

        val dayFoods = foods.filter { it.consumedAt in dayStart until dayEnd }
        val consumed = dayFoods.sumOf { it.calories }
        val protein = dayFoods.sumOf { it.protein }
        val workoutsBurned = workouts.filter { it.workoutDate in dayStart until dayEnd }.sumOf { it.caloriesBurned }
        val waterMl = waters.filter { it.entryDate in dayStart until dayEnd }.sumOf { it.milliliters }

        val activeBurned = if (i == 0) max(healthActiveCalories, workoutsBurned) else workoutsBurned
        val dayName = if (i == 0) "Hoje" else {
            dayFormatter.format(Date(dayStart)).replace(".", "").trim()
                .replaceFirstChar { if (it.isLowerCase()) it.titlecase(ptBR) else it.toString() }
        }

        summaries.add(
            DayCalorieSummary(
                date = dayStart,
                dayName = dayName,
                consumed = consumed,
                tmb = tmb,
                activeBurned = activeBurned,
                proteinPercent = (protein / proteinTarget) * 100.0,
                waterPercent = (waterMl.toDouble() / waterTarget) * 100.0
            )
        )
    }
    return summaries
}

@Composable
private fun WeightSummaryHeroCard(
    goalIcon: String,
    goalTitle: String,
    goalSubtitle: String,
    isGoalAchieved: Boolean,
    remainingKg: Double,
    currentWeight: Double,
    targetWeight: Double,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    FormaCard(modifier = modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(symbolFor(goalIcon), null, tint = colors.brand, modifier = Modifier.size(12.dp))
                        Text(
                            text = goalTitle,
                            color = colors.brand,
                            style = FormaTypography.subheadline.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Text(text = goalSubtitle, color = colors.secondaryText, style = FormaTypography.caption)
                }

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.brandSoft)
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (isGoalAchieved) "Meta Batida!" else "Faltam",
                        color = colors.secondaryText,
                        style = FormaTypography.caption2.copy(fontWeight = FontWeight.Medium)
                    )
                    Text(
                        text = "${fmt1(remainingKg)} kg",
                        color = if (isGoalAchieved) colors.energyGreen else colors.brand,
                        style = FormaTypography.rounded(16f, FontWeight.Bold)
                    )
                }
            }

            HorizontalDivider(color = colors.primary.copy(alpha = 0.06f))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text("Peso Atual", color = colors.secondaryText, style = FormaTypography.caption)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = fmt1(currentWeight),
                            color = colors.primaryText,
                            style = FormaTypography.rounded(32f, FontWeight.Bold),
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            text = "kg",
                            color = colors.secondaryText,
                            style = FormaTypography.subheadline,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }

                Icon(
                    imageVector = symbolFor("arrow.right"),
                    contentDescription = null,
                    tint = colors.secondaryText.copy(alpha = 0.4f),
                    modifier = Modifier.size(18.dp)
                )

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Text("Peso Alvo", color = colors.secondaryText, style = FormaTypography.caption)
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = fmt1(targetWeight),
                            color = colors.brand,
                            style = FormaTypography.rounded(32f, FontWeight.Bold),
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            text = "kg",
                            color = colors.secondaryText,
                            style = FormaTypography.subheadline,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MetabolismMetricsSection(
    imc: Double,
    imcClassification: String,
    tmb: Double,
    tdee: Double,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Seu Corpo & Gasto Diário",
            color = colors.primaryText,
            style = FormaTypography.headline
        )

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FormaTintedCard(
                tint = colors.energyGreen,
                modifier = Modifier.weight(1f),
                padding = 14.dp,
                cornerRadius = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(symbolFor("figure.arms.open"), null, tint = colors.energyGreen, modifier = Modifier.size(11.dp))
                        Text(
                            text = "Relação Peso & Altura",
                            color = colors.secondaryText,
                            style = FormaTypography.caption2.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1
                        )
                    }
                    Text(
                        text = fmt1(imc),
                        color = colors.primaryText,
                        style = FormaTypography.rounded(22f, FontWeight.Bold)
                    )
                    FormaBadge(text = imcClassification, tint = colors.energyGreen)
                }
            }

            FormaTintedCard(
                tint = colors.brand,
                modifier = Modifier.weight(1f),
                padding = 14.dp,
                cornerRadius = 16.dp
            ) {
                Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(symbolFor("bolt.heart.fill"), null, tint = colors.brand, modifier = Modifier.size(11.dp))
                        Text(
                            text = "Gasto em Repouso",
                            color = colors.secondaryText,
                            style = FormaTypography.caption2.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            text = "${tmb.toIntSafe()}",
                            color = colors.primaryText,
                            style = FormaTypography.rounded(22f, FontWeight.Bold),
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            text = "kcal",
                            color = colors.secondaryText,
                            style = FormaTypography.caption2,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                    Text(
                        text = "Gasto total: ${tdee.toIntSafe()} kcal/dia",
                        color = colors.secondaryText,
                        style = FormaTypography.caption2.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

private fun formatWeightDate(millis: Long): String =
    SimpleDateFormat("d 'de' MMM 'de' yyyy HH:mm", ptBR).format(Date(millis))

@Composable
private fun WeightsHistorySection(
    entries: List<WeightEntry>,
    onLogWeight: () -> Unit,
    onDelete: (WeightEntry) -> Unit
) {
    val colors = FormaTheme.Colors
    val h = FormaTheme.Spacing.screenHorizontal
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Histórico de Pesagens",
            color = colors.primaryText,
            style = FormaTypography.headline,
            modifier = Modifier.padding(horizontal = h)
        )

        if (entries.isEmpty()) {
            FormaCard(modifier = Modifier.padding(horizontal = h), padding = 24.dp) {
                FormaEmptyState(
                    icon = symbolFor("scalemass.fill"),
                    title = "Nenhuma pesagem registrada",
                    subtitle = "Registre seu peso regularmente para ajustar suas metas Forma.",
                    actionTitle = "+ Registrar Peso",
                    onAction = onLogWeight
                )
            }
        } else {
            Column(
                modifier = Modifier.padding(horizontal = h),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                entries.forEach { entry ->
                    FormaCard(modifier = Modifier.fillMaxWidth(), padding = 14.dp, cornerRadius = 14.dp) {
                        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(
                                    text = formatWeightDate(entry.entryDate),
                                    color = colors.primaryText,
                                    style = FormaTypography.system(14f, FontWeight.SemiBold)
                                )
                                if (entry.notes.isNotEmpty()) {
                                    Text(text = entry.notes, color = colors.secondaryText, style = FormaTypography.caption)
                                }
                            }
                            Text(
                                text = "${fmt1(entry.weight)} kg",
                                color = colors.primaryText,
                                style = FormaTypography.rounded(16f, FontWeight.Bold)
                            )
                            Icon(
                                imageVector = symbolFor("trash"),
                                contentDescription = "Excluir pesagem",
                                tint = colors.danger.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .formaPlainClickable { onDelete(entry) }
                                    .padding(start = 16.dp, top = 8.dp, bottom = 8.dp, end = 4.dp)
                                    .size(14.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

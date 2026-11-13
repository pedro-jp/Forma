package com.forma.app.ui.screens.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Functions
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forma.app.data.model.FitnessGoal
import com.forma.app.data.repository.FormaRepository
import com.forma.app.data.services.GeminiAIService
import com.forma.app.ui.components.AIDataConsentSheet
import com.forma.app.ui.components.FormaScaledText
import com.forma.app.ui.components.dismissKeyboardOnTap
import com.forma.app.ui.components.formaHumanizeErrorMessage
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.openUrl
import com.forma.app.ui.components.rememberFormaHaptics
import com.forma.app.ui.components.rememberKeyboardDismisser
import com.forma.app.ui.components.sfSymbol
import com.forma.app.ui.screens.evolution.EvolutionCalculator
import com.forma.app.ui.screens.evolution.ProfileInputValidator
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.roundToInt

/** `InitialOnboardingModalView.ActivityLevelOption` (iOS). */
enum class ActivityLevelOption(val rawValue: String, val shortTitle: String, val subtitle: String) {
    Light("Leve (1-2x/semana)", "Leve", "1 a 2 treinos/sem"),
    Moderate("Moderado (3-4x/semana)", "Moderado", "3 a 4 treinos/sem"),
    Intense("Intenso (5-6x/semana)", "Intenso", "5+ treinos/sem")
}

private fun parseBoundedDouble(raw: String, min: Double, max: Double): Double? {
    val cleaned = raw.replace(",", ".").trim()
    if (cleaned.isEmpty()) return null
    val value = cleaned.toDoubleOrNull() ?: return null
    if (!value.isFinite() || value < min || value > max) return null
    return value
}

private fun Double.swiftRounded(): Int = if (this >= 0) Math.floor(this + 0.5).toInt() else -Math.floor(-this + 0.5).toInt()

/**
 * Modal obrigatório e inescapável de configuração inicial para novas contas
 * (porte de `InitialOnboardingModalView`). Use em `MainScreen(initialSetupContent = { InitialOnboardingModal(repository) })`.
 * Ao concluir, `repository.completeInitialOnboardingRemoteFirst` marca `onboardingCompleted` e o gate do app o fecha.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InitialOnboardingModal(repository: FormaRepository) {
    val colors = FormaTheme.Colors
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val auth = repository.auth
    val haptics = rememberFormaHaptics()
    val dismissKeyboard = rememberKeyboardDismisser()
    val aiConsent = remember { com.forma.app.data.services.AIDataConsentManager.get(context) }
    val hasConsent by aiConsent.hasGrantedConsent.collectAsState()
    val currentUserName by auth.currentUserName.collectAsState()

    // Identificação & Objetivo
    var nameText by rememberSaveable { mutableStateOf("") }
    var selectedGoal by rememberSaveable { mutableStateOf(FitnessGoal.GAIN) }
    var selectedActivity by rememberSaveable { mutableStateOf(ActivityLevelOption.Moderate) }

    // Biometria obrigatória
    var currentWeightText by rememberSaveable { mutableStateOf("") }
    var heightText by rememberSaveable { mutableStateOf("") }
    var targetWeightText by rememberSaveable { mutableStateOf("") }
    var hasManuallyEditedTargetWeight by rememberSaveable { mutableStateOf(false) }

    // Metas diárias
    var dailyCaloriesText by rememberSaveable { mutableStateOf("") }
    var proteinText by rememberSaveable { mutableStateOf("") }
    var carbsText by rememberSaveable { mutableStateOf("") }
    var fatText by rememberSaveable { mutableStateOf("") }
    var waterMlText by rememberSaveable { mutableStateOf("") }
    var calorieBurnText by rememberSaveable { mutableStateOf("") }
    var stepsText by rememberSaveable { mutableStateOf("") }

    // Estado da IA e persistência
    var isLoadingAISuggestion by remember { mutableStateOf(false) }
    var aiExplanation by rememberSaveable { mutableStateOf<String?>(null) }
    var lastAISignature by rememberSaveable { mutableStateOf("") }
    var isSavingToDatabase by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var showAIConsentSheet by remember { mutableStateOf(false) }
    val aiJobHolder = remember { arrayOfNulls<Job>(1) }

    // Parsers & validações estritas
    val validName = nameText.trim().takeIf { it.length >= 2 }
    val validCurrentWeight = ProfileInputValidator.parseFlexibleWeight(currentWeightText)
    val validHeight = ProfileInputValidator.parseFlexibleHeight(heightText)
    val validTargetWeight = ProfileInputValidator.parseFlexibleWeight(targetWeightText)
    val validCalories = parseBoundedDouble(dailyCaloriesText, 1000.0, 6000.0)
    val validProtein = parseBoundedDouble(proteinText, 30.0, 450.0)
    val validCarbs = parseBoundedDouble(carbsText, 20.0, 900.0)
    val validFat = parseBoundedDouble(fatText, 15.0, 250.0)
    val validWaterMl = parseBoundedDouble(waterMlText, 500.0, 8000.0)?.swiftRounded()
    val validCalorieBurn = parseBoundedDouble(calorieBurnText, 100.0, 3000.0)
    val validSteps = parseBoundedDouble(stepsText, 1000.0, 40000.0)

    val missingFields = buildList {
        if (validName == null) add("Nome")
        if (validCurrentWeight == null) add("Peso atual")
        if (validHeight == null) add("Altura")
        if (validTargetWeight == null) add("Meta de peso")
        if (validCalories == null) add("Calorias")
        if (validProtein == null || validCarbs == null || validFat == null) add("Macros")
        if (validWaterMl == null) add("Água")
        if (validCalorieBurn == null || validSteps == null) add("Gasto/Passos")
    }
    val isFormCompletelyValid = missingFields.isEmpty()
    val bmi: Pair<Double, String>? = if (validCurrentWeight != null && validHeight != null && validHeight > 0) {
        val v = validCurrentWeight / (validHeight * validHeight)
        v to EvolutionCalculator.classifyIMC(v)
    } else null

    fun signatureFor(weight: Double, height: Double): String =
        String.format(Locale.US, "%.1f|%.2f|%s|%s", weight, height, selectedGoal.rawValue, selectedActivity.rawValue)

    suspend fun requestAISuggestion(weight: Double, height: Double, signature: String, forceTargetWeightRefresh: Boolean) {
        isLoadingAISuggestion = true
        val suggestion = try {
            withContext(NonCancellable) {
                GeminiAIService.get(context).suggestInitialGoals(
                    userName = validName ?: currentUserName ?: "Atleta",
                    goal = selectedGoal,
                    currentWeight = weight,
                    heightMeters = height,
                    targetWeight = if (hasManuallyEditedTargetWeight) validTargetWeight else null,
                    activityLevel = selectedActivity.rawValue
                )
            }
        } catch (e: Exception) {
            isLoadingAISuggestion = false
            return
        }
        lastAISignature = signature
        if (forceTargetWeightRefresh || targetWeightText.trim().isEmpty()) {
            targetWeightText = String.format(Locale.getDefault(), "%.1f", suggestion.suggestedTargetWeight)
        }
        dailyCaloriesText = "${suggestion.dailyCalories.swiftRounded()}"
        proteinText = "${suggestion.proteinGrams.swiftRounded()}"
        carbsText = "${suggestion.carbsGrams.swiftRounded()}"
        fatText = "${suggestion.fatGrams.swiftRounded()}"
        waterMlText = "${suggestion.waterMilliliters}"
        calorieBurnText = "${suggestion.targetCalorieBurn.swiftRounded()}"
        stepsText = "${suggestion.targetSteps.swiftRounded()}"
        aiExplanation = suggestion.explanation
        isLoadingAISuggestion = false
    }

    fun scheduleAutoAISuggestion(immediate: Boolean = false) {
        aiJobHolder[0]?.cancel()
        val weight = validCurrentWeight ?: return
        val height = validHeight ?: return
        val signature = signatureFor(weight, height)
        if (signature == lastAISignature) return
        val forceRefresh = !hasManuallyEditedTargetWeight
        aiJobHolder[0] = scope.launch {
            if (!immediate) delay(650)
            requestAISuggestion(weight, height, signature, forceRefresh)
        }
    }

    fun triggerAISuggestionNow(forceTargetWeightRefresh: Boolean) {
        aiJobHolder[0]?.cancel()
        val weight = validCurrentWeight ?: return
        val height = validHeight ?: return
        val signature = signatureFor(weight, height)
        scope.launch { requestAISuggestion(weight, height, signature, forceTargetWeightRefresh) }
    }

    fun submitInitialConfiguration() {
        val name = validName ?: return
        val currentWeight = validCurrentWeight ?: return
        val height = validHeight ?: return
        val targetWeight = validTargetWeight ?: return
        val calories = validCalories ?: return
        val protein = validProtein ?: return
        val carbs = validCarbs ?: return
        val fat = validFat ?: return
        val waterMl = validWaterMl ?: return
        val burn = validCalorieBurn ?: return
        val steps = validSteps ?: return

        dismissKeyboard()
        saveErrorMessage = null
        isSavingToDatabase = true
        val goal = selectedGoal
        scope.launch {
            try {
                repository.completeInitialOnboardingRemoteFirst(
                    name = name,
                    goal = goal,
                    currentWeight = currentWeight,
                    heightMeters = height,
                    targetWeight = targetWeight,
                    targetCalorieIntake = calories,
                    targetProtein = protein,
                    targetCarbohydrates = carbs,
                    targetFat = fat,
                    targetWaterMilliliters = waterMl,
                    targetCalorieBurn = burn,
                    targetSteps = steps
                )
                haptics.success()
                isSavingToDatabase = false
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isSavingToDatabase = false
                saveErrorMessage =
                    "Não foi possível sincronizar com o banco de dados agora (${formaHumanizeErrorMessage(e.localizedMessage ?: e.toString())}). Verifique sua conexão e tente novamente."
            }
        }
    }

    // onAppear: prefillInitialNameIfAvailable
    LaunchedEffect(Unit) {
        val raw = currentUserName?.trim().orEmpty()
        if (nameText.isEmpty() && raw.isNotEmpty() && raw.lowercase() != "usuário") nameText = raw
    }
    // onChange(currentWeightText / heightText / goal / activity)
    LaunchedEffect(currentWeightText, heightText) { scheduleAutoAISuggestion() }
    LaunchedEffect(selectedGoal) {
        hasManuallyEditedTargetWeight = false
        scheduleAutoAISuggestion(immediate = true)
    }
    LaunchedEffect(selectedActivity) { scheduleAutoAISuggestion(immediate = true) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
    ) {
        // Barra de navegação inline: título central + "Trocar conta"
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            Text("Configuração Inicial", color = colors.primaryText, style = FormaTypography.headline)
            Text(
                text = "Trocar conta",
                color = colors.secondaryText,
                style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .alpha(if (isSavingToDatabase) 0.5f else 1f)
                    .formaPlainClickable(enabled = !isSavingToDatabase) {
                        aiJobHolder[0]?.cancel()
                        auth.logout()
                    }
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .dismissKeyboardOnTap()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FormaTheme.Spacing.screenHorizontal)
                .padding(top = 12.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Hero
            val cardShape = RoundedCornerShape(FormaTheme.Radius.card)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(cardShape)
                    .background(colors.cardSurface)
                    .border(1.dp, colors.aiAccent.copy(alpha = 0.25f), cardShape)
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Box(
                        modifier = Modifier.size(46.dp).clip(CircleShape).background(colors.aiGradient),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(sfSymbol("sparkles"), null, tint = Color.White, modifier = Modifier.size(20.dp))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        Text(
                            "Bem-vindo ao Forma!",
                            color = colors.primaryText,
                            style = FormaTypography.rounded(20f, FontWeight.Bold)
                        )
                        Text(
                            "Preencha todos os dados abaixo para liberar sua conta.",
                            color = colors.secondaryText,
                            style = FormaTypography.caption
                        )
                    }
                }
                Text(
                    "Assim que você informar seu peso e altura, nossa IA calculará automaticamente suas metas ideais de calorias, proteínas, carboidratos, gorduras e hidratação.",
                    color = colors.secondaryText,
                    style = FormaTypography.footnote
                )
            }

            // 1. Perfil e objetivo
            OnboardingCard {
                SectionTitle("1. Seu Perfil e Objetivo", Icons.Filled.Flag)

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        "Como quer ser chamado?",
                        color = colors.secondaryText,
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold)
                    )
                    OnboardingTextInput(
                        value = nameText,
                        onValueChange = { nameText = it },
                        placeholder = "Digite seu nome",
                        keyboardType = KeyboardType.Text,
                        capitalization = KeyboardCapitalization.Words,
                        padding = 13.dp,
                        textStyle = FormaTypography.body
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Objetivo Principal",
                        color = colors.secondaryText,
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold)
                    )
                    FitnessGoal.entries.forEach { goal ->
                        val isSelected = selectedGoal == goal
                        val shape = RoundedCornerShape(14.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(if (isSelected) colors.brandSoft.copy(alpha = 0.6f) else colors.secondaryGroupedBackground)
                                .border(1.5.dp, if (isSelected) colors.brand else Color.Transparent, shape)
                                .formaPlainClickable { selectedGoal = goal }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            GoalIcon(goal, if (isSelected) colors.brand else colors.secondaryText)
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(goal.rawValue, color = colors.primaryText, style = FormaTypography.rounded(15f, FontWeight.Bold))
                                Text(goal.subtitle, color = colors.secondaryText, style = FormaTypography.caption)
                            }
                            Icon(
                                imageVector = if (isSelected) sfSymbol("checkmark.circle.fill") else Icons.Filled.RadioButtonUnchecked,
                                contentDescription = null,
                                tint = if (isSelected) colors.brand else colors.secondaryText.copy(alpha = 0.4f),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Rotina Semanal de Treinos",
                        color = colors.secondaryText,
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ActivityLevelOption.entries.forEach { level ->
                            val isSelected = selectedActivity == level
                            Column(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(if (isSelected) colors.brand else colors.secondaryGroupedBackground)
                                    .formaPlainClickable { selectedActivity = level }
                                    .padding(vertical = 10.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Text(
                                    level.shortTitle,
                                    color = if (isSelected) Color.White else colors.primaryText,
                                    style = FormaTypography.rounded(13f, FontWeight.Bold)
                                )
                                FormaScaledText(
                                    text = level.subtitle,
                                    style = FormaTypography.system(10f, FontWeight.Medium),
                                    color = if (isSelected) Color.White.copy(alpha = 0.9f) else colors.secondaryText,
                                    minScale = 0.8f,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }

            // 2. Medidas corporais
            OnboardingCard {
                SectionTitle("2. Medidas Corporais", sfSymbol("scalemass.fill"))

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OnboardingNumberField(
                        title = "Peso Atual (kg)",
                        placeholder = "Ex: 72,5",
                        text = currentWeightText,
                        onTextChange = { currentWeightText = it },
                        isValid = validCurrentWeight != null,
                        modifier = Modifier.weight(1f)
                    )
                    OnboardingNumberField(
                        title = "Altura (m ou cm)",
                        placeholder = "Ex: 1,75 ou 175",
                        text = heightText,
                        onTextChange = { heightText = it },
                        isValid = validHeight != null,
                        modifier = Modifier.weight(1f)
                    )
                }

                OnboardingNumberField(
                    title = "Meta de Peso (kg)",
                    placeholder = "Sugerida pela IA ou digite (ex: 78,0)",
                    text = targetWeightText,
                    onTextChange = {
                        targetWeightText = it
                        hasManuallyEditedTargetWeight = it.trim().isNotEmpty()
                    },
                    isValid = validTargetWeight != null
                )

                bmi?.let { (value, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.energyGreen.copy(alpha = 0.1f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(sfSymbol("heart.fill"), null, tint = colors.energyGreen, modifier = Modifier.size(18.dp))
                        Text(
                            String.format(Locale.getDefault(), "IMC atual: %.1f • %s", value, label),
                            color = colors.primaryText,
                            style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            // 3. Metas diárias (IA)
            OnboardingCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    SectionTitle("3. Metas Diárias", sfSymbol("sparkles"))
                    val canAsk = validCurrentWeight != null && validHeight != null && !isLoadingAISuggestion
                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .then(
                                if (validCurrentWeight != null && validHeight != null) Modifier.background(colors.aiGradient)
                                else Modifier.background(Color.Gray.copy(alpha = 0.4f))
                            )
                            .formaPlainClickable(enabled = canAsk) {
                                if (hasConsent) triggerAISuggestionNow(!hasManuallyEditedTargetWeight)
                                else showAIConsentSheet = true
                            }
                            .padding(horizontal = 12.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp)
                    ) {
                        if (isLoadingAISuggestion) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 1.5.dp, modifier = Modifier.size(12.dp))
                        } else {
                            Icon(sfSymbol("sparkles"), null, tint = Color.White, modifier = Modifier.size(12.dp))
                        }
                        Text(
                            if (isLoadingAISuggestion) "Calculando..." else "IA Google Gemini",
                            color = Color.White,
                            style = FormaTypography.rounded(12f, FontWeight.Bold)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(
                        imageVector = if (hasConsent) sfSymbol("sparkles") else Icons.Filled.Functions,
                        contentDescription = null,
                        tint = if (hasConsent) colors.aiAccent else colors.brand,
                        modifier = Modifier.size(13.dp)
                    )
                    Text(
                        text = if (hasConsent) "Sugestões ativas via IA do Google Gemini (Google LLC)"
                        else "Cálculo local (Mifflin-St Jeor) • Toque em \"IA Google Gemini\" para ativar a IA na nuvem",
                        color = colors.secondaryText,
                        style = FormaTypography.caption2
                    )
                }

                if (isLoadingAISuggestion) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.aiAccent.copy(alpha = 0.08f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(color = colors.aiAccent, strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                        Text(
                            text = if (hasConsent) "A IA do Google Gemini está calculando suas metas personalizadas..."
                            else "Calculando suas metas fisiológicas iniciais...",
                            color = colors.secondaryText,
                            style = FormaTypography.caption
                        )
                    }
                } else if (!aiExplanation.isNullOrEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.aiAccent.copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            sfSymbol("sparkles"), null, tint = colors.aiAccent,
                            modifier = Modifier.padding(top = 2.dp).size(16.dp)
                        )
                        Text(aiExplanation.orEmpty(), color = colors.primaryText, style = FormaTypography.caption)
                    }
                } else if (validCurrentWeight == null || validHeight == null) {
                    Text(
                        "Informe seu peso atual e altura acima para gerar automaticamente a sugestão de metas com IA.",
                        color = colors.secondaryText,
                        style = FormaTypography.caption
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OnboardingNumberField(
                        title = "Calorias/dia (kcal)", placeholder = "Ex: 2500",
                        text = dailyCaloriesText, onTextChange = { dailyCaloriesText = it },
                        isValid = validCalories != null, keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                    OnboardingNumberField(
                        title = "Água/dia (ml)", placeholder = "Ex: 2800",
                        text = waterMlText, onTextChange = { waterMlText = it },
                        isValid = validWaterMl != null, keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OnboardingNumberField(
                        title = "Proteína (g)", placeholder = "150",
                        text = proteinText, onTextChange = { proteinText = it },
                        isValid = validProtein != null, keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                    OnboardingNumberField(
                        title = "Carbo (g)", placeholder = "280",
                        text = carbsText, onTextChange = { carbsText = it },
                        isValid = validCarbs != null, keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                    OnboardingNumberField(
                        title = "Gordura (g)", placeholder = "65",
                        text = fatText, onTextChange = { fatText = it },
                        isValid = validFat != null, keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OnboardingNumberField(
                        title = "Meta de Queima (kcal)", placeholder = "Ex: 500",
                        text = calorieBurnText, onTextChange = { calorieBurnText = it },
                        isValid = validCalorieBurn != null, keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                    OnboardingNumberField(
                        title = "Meta de Passos", placeholder = "Ex: 8000",
                        text = stepsText, onTextChange = { stepsText = it },
                        isValid = validSteps != null, keyboardType = KeyboardType.Number,
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // Validação e envio
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (missingFields.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.warning.copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            sfSymbol("lock.fill"), null, tint = colors.warning,
                            modifier = Modifier.padding(top = 2.dp).size(13.dp)
                        )
                        Text(
                            "Preencha todos os campos para liberar o app: ${missingFields.joinToString(", ")}.",
                            color = colors.secondaryText,
                            style = FormaTypography.caption
                        )
                    }
                }

                saveErrorMessage?.let { errorMsg ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.danger.copy(alpha = 0.1f))
                            .padding(12.dp),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(sfSymbol("exclamationmark.triangle.fill"), null, tint = colors.danger, modifier = Modifier.size(16.dp))
                        Text(
                            errorMsg,
                            color = colors.danger,
                            style = FormaTypography.caption.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                }

                val enabled = isFormCompletelyValid && !isSavingToDatabase
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (enabled) colors.brand else Color.Gray.copy(alpha = 0.38f))
                        .formaPlainClickable(enabled = enabled) { submitInitialConfiguration() }
                        .padding(vertical = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
                ) {
                    if (isSavingToDatabase) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                        Text("Salvando no banco de dados...", color = Color.White, style = FormaTypography.headline)
                    } else {
                        Icon(Icons.Filled.GppGood, null, tint = Color.White, modifier = Modifier.size(18.dp))
                        Text("Concluir e Entrar no Forma", color = Color.White, style = FormaTypography.headline)
                    }
                }

                Column(modifier = Modifier.padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text(
                        "Estimativas de IMC, TMB e macronutrientes têm finalidade informativa e educacional e não substituem avaliação médica ou nutricional.",
                        color = colors.secondaryText,
                        style = FormaTypography.caption2
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            "Fonte TMB: Mifflin-St Jeor (PubMed)",
                            color = colors.brand,
                            style = FormaTypography.caption2.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.formaPlainClickable { openUrl(context, "https://pubmed.ncbi.nlm.nih.gov/2305711/") }
                        )
                        Text(
                            "Fonte IMC: OMS (WHO)",
                            color = colors.brand,
                            style = FormaTypography.caption2.copy(fontWeight = FontWeight.SemiBold),
                            modifier = Modifier.formaPlainClickable {
                                openUrl(context, "https://www.who.int/news-room/fact-sheets/detail/obesity-and-overweight")
                            }
                        )
                    }
                }
            }
        }
    }

    if (showAIConsentSheet) {
        AIDataConsentSheet(
            onGranted = {
                showAIConsentSheet = false
                triggerAISuggestionNow(!hasManuallyEditedTargetWeight)
            },
            onDeclined = { showAIConsentSheet = false }
        )
    }
}

@Composable
private fun OnboardingCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FormaTheme.Radius.card))
            .background(FormaTheme.Colors.cardSurface)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) { content() }
}

@Composable
private fun SectionTitle(text: String, icon: ImageVector) {
    val colors = FormaTheme.Colors
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, null, tint = colors.brand, modifier = Modifier.size(16.dp))
        Text(text, color = colors.primaryText, style = FormaTypography.rounded(16f, FontWeight.Bold))
    }
}

@Composable
private fun GoalIcon(goal: FitnessGoal, tint: Color) {
    when (goal) {
        FitnessGoal.GAIN -> Icon(Icons.Filled.ArrowCircleUp, null, tint = tint, modifier = Modifier.size(24.dp))
        FitnessGoal.LOSE -> Icon(Icons.Filled.ArrowCircleDown, null, tint = tint, modifier = Modifier.size(24.dp))
        FitnessGoal.MAINTAIN -> Box(
            modifier = Modifier.size(22.dp).clip(CircleShape).background(tint),
            contentAlignment = Alignment.Center
        ) {
            Text("=", color = FormaTheme.Colors.cardSurface, style = FormaTypography.system(16f, FontWeight.Bold))
        }
    }
}

@Composable
private fun OnboardingTextInput(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    keyboardType: KeyboardType,
    modifier: Modifier = Modifier,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    padding: Dp = 12.dp,
    textStyle: androidx.compose.ui.text.TextStyle = FormaTypography.rounded(15f, FontWeight.SemiBold),
    borderColor: Color = Color.Transparent,
    borderWidth: Dp = 1.2.dp
) {
    val colors = FormaTheme.Colors
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(colors.secondaryGroupedBackground)
            .border(borderWidth, borderColor, shape)
            .padding(padding)
    ) {
        if (value.isEmpty()) {
            Text(placeholder, color = colors.tertiaryText, style = textStyle.copy(fontWeight = FontWeight.Normal), maxLines = 1)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            textStyle = textStyle.copy(color = colors.primaryText),
            cursorBrush = SolidColor(colors.brand),
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

@Composable
private fun OnboardingNumberField(
    title: String,
    placeholder: String,
    text: String,
    onTextChange: (String) -> Unit,
    isValid: Boolean,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Decimal
) {
    val colors = FormaTheme.Colors
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            FormaScaledText(
                text = title,
                style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                color = colors.secondaryText,
                minScale = 0.8f,
                modifier = Modifier.weight(1f)
            )
            if (text.isNotEmpty()) {
                Icon(
                    imageVector = if (isValid) sfSymbol("checkmark.circle.fill") else Icons.Filled.ErrorOutline,
                    contentDescription = null,
                    tint = if (isValid) colors.energyGreen else colors.warning,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
        OnboardingTextInput(
            value = text,
            onValueChange = onTextChange,
            placeholder = placeholder,
            keyboardType = keyboardType,
            borderColor = when {
                text.isEmpty() -> Color.Transparent
                isValid -> colors.energyGreen.copy(alpha = 0.45f)
                else -> colors.warning.copy(alpha = 0.7f)
            }
        )
    }
}

package com.forma.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forma.app.R
import com.forma.app.data.health.HealthPermissionPrompt
import com.forma.app.data.health.HealthViewModel
import com.forma.app.data.model.FitnessGoal
import com.forma.app.data.model.FocusMetric
import com.forma.app.data.repository.FormaRepository
import com.forma.app.data.services.HealthConnectManager
import com.forma.app.ui.components.FormaBadge
import com.forma.app.ui.components.FormaHydrationCard
import com.forma.app.ui.components.FormaScrollScreen
import com.forma.app.ui.screens.home.HomeDailyLogsSection
import com.forma.app.ui.screens.home.HomeEnergyHeroCard
import com.forma.app.ui.screens.home.homeSymbol
import com.forma.app.ui.screens.nutrition.VoiceAddFoodSheet
import com.forma.app.ui.screens.evolution.LogWeightSheet
import com.forma.app.ui.screens.workouts.AIWorkoutLoggerSheet
import com.forma.app.ui.screens.workouts.AddWorkoutSheet
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.max

/**
 * Painel diário principal (porte de `HomeView.swift`): cabeçalho, balanço energético, hidratação e registros
 * do dia (refeições, passos, treinos, ações rápidas).
 *
 * Os callbacks de navegação são mantidos por compatibilidade com o shell; o Início do iOS não possui banner de
 * treino nem atalhos de navegação (o treino ativo aparece na Live Activity / notificação).
 */
@Composable
fun HomeScreen(
    repository: FormaRepository,
    onNavigateToNutrition: () -> Unit = {},
    onNavigateToWorkouts: () -> Unit = {},
    onStartWorkout: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val router = repository.router

    val profile by repository.userProfile.collectAsState()
    val allFoods by repository.foodEntries.collectAsState()
    val allWorkouts by repository.workoutEntries.collectAsState()
    val allWaters by repository.waters.collectAsState()
    val authName by repository.auth.currentUserName.collectAsState()

    val showAddFoodSheet by router.showAddFoodSheet.collectAsState()
    val showVoiceFoodSheet by router.showVoiceFoodSheet.collectAsState()
    val showAIFoodSheet by router.showAIFoodSheet.collectAsState()
    val showAIWorkoutSheet by router.showAIWorkoutSheet.collectAsState()

    // Saúde (Health Connect) — equivalente ao @StateObject HealthViewModel
    val healthManager = remember { HealthConnectManager.get(context) }
    val healthVM = remember { HealthViewModel(healthManager) }
    val steps by healthVM.steps.collectAsState()
    val healthActiveCalories by healthVM.activeCalories.collectAsState()
    val distanceKm by healthVM.distanceKm.collectAsState()

    var activeMetric by remember { mutableStateOf(FocusMetric.CONSUMED) }
    var showAddWorkoutSheet by remember { mutableStateOf(false) }
    var showLogWeightSheet by remember { mutableStateOf(false) }
    var showClearConfirmAlert by remember { mutableStateOf(false) }
    var openCameraInFoodSheet by remember { mutableStateOf(false) }
    var showHealthIntro by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(remember { healthVM.permissionContract }) {
        scope.launch {
            healthVM.loadHealthData()
            runCatching { repository.syncExternalHealthWorkoutsInBackground() }
        }
    }

    // MARK: - Dados derivados (iguais ao HomeView do iOS)
    val dayRange = repository.dayRange()
    val foodsToday = allFoods
        .filter { it.date >= dayRange.start && it.date < dayRange.endExclusive }
        .sortedByDescending { it.createdAt }
    val workoutsToday = allWorkouts
        .filter { it.workoutDate >= dayRange.start && it.workoutDate < dayRange.endExclusive }
        .sortedByDescending { it.workoutDate }
    val waterToday = allWaters.filter { it.entryDate >= dayRange.start && it.entryDate < dayRange.endExclusive }

    val nomeUsuario = profile.name.trim().ifEmpty { authName?.trim().orEmpty() }.ifEmpty { "Atleta" }
    val objetivo: FitnessGoal = profile.goal
    val metaCaloriasConsumir = profile.targetCalorieIntake
    val metaCaloriasGastar = profile.targetCalorieBurn
    val metaPassos = profile.targetSteps
    val metaProtein = if (profile.targetProtein > 0) profile.targetProtein else 160.0
    val metaFat = if (profile.targetFat > 0) profile.targetFat else 70.0
    val metaCarbo = if (profile.targetCarbohydrates > 0) profile.targetCarbohydrates else 380.0

    val caloriasConsumidasHoje = foodsToday.sumOf { it.calories }
    val proteinasConsumidasHoje = foodsToday.sumOf { it.protein }
    val carbosConsumidosHoje = foodsToday.sumOf { it.carbohydrates }
    val gordurasConsumidasHoje = foodsToday.sumOf { it.fat }
    val caloriasTreinosHoje = workoutsToday.sumOf { it.caloriesBurned }
    val caloriasGastasAtivas = max(healthActiveCalories, caloriasTreinosHoje)
    val waterConsumedToday = waterToday.sumOf { it.milliliters }
    val waterTarget = profile.targetWaterMilliliters

    val metaCaloriasConsumirAjustada = metaCaloriasConsumir + caloriasTreinosHoje
    val caloriasRestantesHoje = metaCaloriasConsumirAjustada - caloriasConsumidasHoje

    val syncToday: suspend () -> Unit = {
        withContext(Dispatchers.IO) {
            repository.syncTodayData(
                activeCaloriesOverride = caloriasGastasAtivas,
                stepsOverride = steps.toInt(),
                skipCloudSyncTrigger = true
            )
        }
    }

    // onAppear + .task
    LaunchedEffect(Unit) {
        withContext(Dispatchers.IO) { runCatching { repository.ensureSingleProfile() } }
        activeMetric = repository.userProfile.value.effectiveFocus
        healthVM.loadHealthData()
        if (!HealthPermissionPrompt.wasPrompted(context) && healthVM.needsPermissionRequest()) {
            showHealthIntro = true
        }
        runCatching { repository.syncExternalHealthWorkoutsInBackground() }
    }

    // Atualização periódica das métricas de saúde enquanto a Início está visível
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000)
            healthVM.loadHealthData()
        }
    }

    // onChange(allFoods.count / allWorkouts.count / activeCalories / steps) → syncWidgetsAndLiveActivity
    LaunchedEffect(allFoods.size, allWorkouts.size, healthActiveCalories, steps) {
        syncToday()
    }

    FormaScrollScreen {
        HomeHeader(nome = nomeUsuario, goal = objetivo)

        HomeEnergyHeroCard(
            activeMetric = activeMetric,
            onActiveMetricChange = { activeMetric = it },
            goal = objetivo,
            caloriesConsumed = caloriasConsumidasHoje,
            adjustedCalorieTarget = metaCaloriasConsumirAjustada,
            activeCaloriesBurned = caloriasGastasAtivas,
            activeBurnTarget = metaCaloriasGastar,
            remainingCalories = caloriasRestantesHoje,
            proteinConsumed = proteinasConsumidasHoje,
            proteinTarget = metaProtein,
            carbsConsumed = carbosConsumidosHoje,
            carbsTarget = metaCarbo,
            fatConsumed = gordurasConsumidasHoje,
            fatTarget = metaFat
        )

        FormaHydrationCard(
            consumedMl = waterConsumedToday,
            targetMl = waterTarget,
            onAddWater = { ml -> repository.addWater(ml) }
        )

        HomeDailyLogsSection(
            foodsToday = foodsToday,
            workoutsToday = workoutsToday,
            steps = steps,
            stepsTarget = metaPassos,
            distanceKm = distanceKm,
            onClearMealsTap = { showClearConfirmAlert = true },
            onOpenCameraTap = {
                openCameraInFoodSheet = true
                router.setShowVoiceFoodSheet(true)
            },
            onAddMealTap = {
                openCameraInFoodSheet = false
                router.setShowVoiceFoodSheet(true)
            },
            onDeleteFood = { food -> repository.deleteFoodEntry(food.id) },
            onAddWorkoutTap = { showAddWorkoutSheet = true },
            onDeleteWorkout = { workout -> repository.deleteWorkoutEntry(workout.id) },
            onLogWeightTap = { showLogWeightSheet = true }
        )
    }

    // .alert("Limpar Alimentos de Hoje?")
    if (showClearConfirmAlert) {
        val colors = FormaTheme.Colors
        AlertDialog(
            onDismissRequest = { showClearConfirmAlert = false },
            containerColor = colors.cardSurface,
            title = { Text("Limpar Alimentos de Hoje?", color = colors.primaryText) },
            text = {
                Text(
                    "Isso removerá todos os alimentos registrados hoje para começar novamente.",
                    color = colors.secondaryText
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    showClearConfirmAlert = false
                    repository.deleteFoodEntries(foodsToday.map { it.id })
                }) { Text("Limpar Tudo", color = colors.danger) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmAlert = false }) {
                    Text("Cancelar", color = colors.brand)
                }
            }
        )
    }

    // Explicação (mesmo texto do Info.plist do iOS) antes do pedido de permissão do Health Connect
    if (showHealthIntro) {
        val colors = FormaTheme.Colors
        val request = {
            showHealthIntro = false
            HealthPermissionPrompt.markPrompted(context)
        }
        AlertDialog(
            onDismissRequest = { request() },
            containerColor = colors.cardSurface,
            title = { Text("Saúde", color = colors.primaryText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(HealthPermissionPrompt.SHARE_USAGE_DESCRIPTION, color = colors.secondaryText)
                    Text(HealthPermissionPrompt.UPDATE_USAGE_DESCRIPTION, color = colors.secondaryText)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    request()
                    permissionLauncher.launch(HealthConnectManager.PERMISSIONS)
                }) { Text("Continuar", color = colors.brand) }
            },
            dismissButton = {
                TextButton(onClick = { request() }) { Text("Agora não", color = colors.secondaryText) }
            }
        )
    }

    // Sheets (router + estado local), como no HomeView do iOS
    if (showAddFoodSheet) {
        VoiceAddFoodSheet(onDismiss = { router.setShowAddFoodSheet(false) })
    }
    if (showVoiceFoodSheet) {
        VoiceAddFoodSheet(
            initialOpenCamera = openCameraInFoodSheet,
            onDismiss = {
                router.setShowVoiceFoodSheet(false)
                openCameraInFoodSheet = false
            }
        )
    }
    if (showAIFoodSheet) {
        VoiceAddFoodSheet(onDismiss = { router.setShowAIFoodSheet(false) })
    }
    if (showAddWorkoutSheet) {
        AddWorkoutSheet(onDismiss = { showAddWorkoutSheet = false })
    }
    if (showLogWeightSheet) {
        LogWeightSheet(onDismiss = { showLogWeightSheet = false })
    }
    if (showAIWorkoutSheet) {
        AIWorkoutLoggerSheet(onDismiss = { router.setShowAIWorkoutSheet(false) })
    }
}

@Composable
private fun HomeHeader(nome: String, goal: FitnessGoal) {
    val colors = FormaTheme.Colors
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.profile),
            contentDescription = "Foto de perfil",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(CircleShape)
                .border(1.5.dp, colors.primary.copy(alpha = 0.08f), CircleShape)
        )

        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = homeGreeting(nome),
                color = colors.primaryText,
                style = FormaTypography.rounded(22f, FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = homeFormattedDate(),
                color = colors.secondaryText,
                style = FormaTypography.subheadline
            )
        }

        FormaBadge(
            text = when (goal) {
                FitnessGoal.GAIN -> "Ganho"
                FitnessGoal.LOSE -> "Perda"
                FitnessGoal.MAINTAIN -> "Manutenção"
            },
            icon = homeSymbol(goal.sfSymbol),
            tint = colors.brand
        )
    }
}

/** `saudacao(nome:)` do iOS: 5–11 bom dia, 12–17 boa tarde, demais boa noite. */
private fun homeGreeting(nome: String): String {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5..11 -> "Bom dia, $nome"
        in 12..17 -> "Boa tarde, $nome"
        else -> "Boa noite, $nome"
    }
}

/** `Date.now.formatted(.weekday(.wide).day().month(.wide))` em pt_BR + `.capitalized` (cada palavra). */
private fun homeFormattedDate(): String {
    val locale = Locale("pt", "BR")
    val raw = LocalDate.now().format(DateTimeFormatter.ofPattern("EEEE, d 'de' MMMM", locale))
    val sb = StringBuilder(raw.length)
    var capitalizeNext = true
    for (ch in raw) {
        if (capitalizeNext && ch.isLetter()) {
            sb.append(ch.titlecase(locale))
            capitalizeNext = false
        } else {
            sb.append(ch.lowercaseChar())
        }
        if (ch == ' ' || ch == '-') capitalizeNext = true
    }
    return sb.toString()
}

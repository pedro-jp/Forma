package com.forma.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forma.app.data.model.FoodEntry
import com.forma.app.data.model.MealType
import com.forma.app.data.model.WaterEntry
import com.forma.app.data.repository.FormaRepository
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaHydrationCard
import com.forma.app.ui.components.FormaMacroProgressRow
import com.forma.app.ui.components.FormaProgressBar
import com.forma.app.ui.components.FormaScaledText
import com.forma.app.ui.components.FormaScreenHeader
import com.forma.app.ui.components.FormaScrollScreen
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.sfSymbol
import com.forma.app.ui.screens.nutrition.FoodLoggerSheet
import com.forma.app.ui.screens.nutrition.MealSectionCard
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

private val PtBr = Locale("pt", "BR")

private fun startOfDay(millis: Long): Long = Calendar.getInstance().apply {
    timeInMillis = millis
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis

/** Segunda a domingo da semana corrente (`calendar.firstWeekday = 2`). */
private fun currentWeekDays(now: Long = System.currentTimeMillis()): List<Long> {
    val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(now) }
    val daysFromMonday = (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    cal.add(Calendar.DAY_OF_YEAR, -daysFromMonday)
    return (0 until 7).map {
        val day = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 1)
        day
    }
}

private fun currentSuggestedMeal(): MealType {
    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
    return when (hour) {
        in 5 until 11 -> MealType.BREAKFAST
        in 11 until 15 -> MealType.LUNCH
        in 15 until 19 -> MealType.SNACKS
        else -> MealType.DINNER
    }
}

/**
 * Aba Alimentação (iOS: `NutritionView`): diário alimentar diário/semanal com distribuição calórica de
 * macronutrientes, hidratação e seções por refeição.
 */
@Composable
fun NutritionScreen(
    repository: FormaRepository
) {
    val scope = rememberCoroutineScope()
    val profile by repository.userProfile.collectAsState()
    val allFoodEntries by repository.foodEntries.collectAsState()
    val allWorkouts by repository.workoutEntries.collectAsState()
    val allWaterEntries by repository.waters.collectAsState()

    var selectedDate by remember { mutableLongStateOf(System.currentTimeMillis()) }
    var showVoiceFoodSheet by remember { mutableStateOf(false) }
    var openCameraDirectly by remember { mutableStateOf(false) }
    var mealToAdd by remember { mutableStateOf(MealType.LUNCH) }

    val routerAddFood by repository.router.showAddFoodSheet.collectAsState()
    val routerVoiceFood by repository.router.showVoiceFoodSheet.collectAsState()
    val routerAIFood by repository.router.showAIFoodSheet.collectAsState()

    val weekDays = remember { currentWeekDays() }
    val targetStart = startOfDay(selectedDate)

    fun hasFoodLogged(day: Long) = allFoodEntries.any { startOfDay(it.date) == day }

    val foodsForSelectedDate = allFoodEntries.filter { startOfDay(it.date) == targetStart }
    val workoutCaloriesForSelectedDate = allWorkouts
        .filter { startOfDay(it.workoutDate) == targetStart }
        .sumOf { it.caloriesBurned }
    val totalWaterMl = allWaterEntries.filter { startOfDay(it.entryDate) == targetStart }.sumOf { it.milliliters }
    val targetWaterMl = profile.targetWaterMilliliters

    val totalCalories = foodsForSelectedDate.sumOf { it.calories }
    val totalProtein = foodsForSelectedDate.sumOf { it.protein }
    val totalCarbs = foodsForSelectedDate.sumOf { it.carbohydrates }
    val totalFat = foodsForSelectedDate.sumOf { it.fat }

    val targetCaloriesAdjusted = profile.targetCalorieIntake + workoutCaloriesForSelectedDate
    val targetProtein = if (profile.targetProtein > 0) profile.targetProtein else 160.0
    val targetCarbs = if (profile.targetCarbohydrates > 0) profile.targetCarbohydrates else 380.0
    val targetFat = if (profile.targetFat > 0) profile.targetFat else 70.0

    FormaScrollScreen {
        FormaScreenHeader(
            title = "Diário Alimentar",
            subtitle = "Controle diário de calorias e nutrientes"
        ) {
            val colors = FormaTheme.Colors
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(colors.brandSoft)
                        .formaPlainClickable {
                            mealToAdd = currentSuggestedMeal()
                            openCameraDirectly = true
                            showVoiceFoodSheet = true
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(sfSymbol("camera.fill"), contentDescription = "Foto da refeição", tint = colors.brand, modifier = Modifier.size(16.dp))
                }

                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.brand)
                        .formaPlainClickable {
                            mealToAdd = currentSuggestedMeal()
                            openCameraDirectly = false
                            showVoiceFoodSheet = true
                        }
                        .padding(horizontal = 15.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(sfSymbol("mic.fill"), contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Text(
                        text = "Refeição",
                        color = Color.White,
                        style = FormaTypography.rounded(14f, FontWeight.SemiBold),
                        maxLines = 1
                    )
                }
            }
        }

        WeeklyCalendarStrip(
            days = weekDays,
            selectedDate = selectedDate,
            hasFoodLogged = ::hasFoodLogged,
            onSelect = { selectedDate = it }
        )

        CalorieBalanceCard(
            totalCalories = totalCalories,
            targetCaloriesAdjusted = targetCaloriesAdjusted,
            dailyTmb = profile.tmb,
            workoutCalories = workoutCaloriesForSelectedDate,
            totalProtein = totalProtein,
            totalCarbs = totalCarbs,
            totalFat = totalFat,
            targetProtein = targetProtein,
            targetCarbs = targetCarbs,
            targetFat = targetFat
        )

        FormaHydrationCard(
            consumedMl = totalWaterMl,
            targetMl = targetWaterMl,
            onAddWater = { ml ->
                scope.launch {
                    runCatching {
                        repository.insertWaterEntryRemoteFirst(WaterEntry(milliliters = ml, entryDate = selectedDate))
                    }
                }
            }
        )

        Column(
            modifier = Modifier.padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            MealType.values().forEach { meal ->
                MealSectionCard(
                    mealType = meal,
                    entries = foodsForSelectedDate.filter { it.mealType == meal },
                    onAddTap = {
                        mealToAdd = meal
                        openCameraDirectly = false
                        showVoiceFoodSheet = true
                    },
                    onCameraTap = {
                        mealToAdd = meal
                        openCameraDirectly = true
                        showVoiceFoodSheet = true
                    },
                    onDeleteEntry = { entry: FoodEntry -> repository.deleteFoodEntry(entry.id) }
                )
            }
        }
    }

    if (showVoiceFoodSheet) {
        FoodLoggerSheet(
            repository = repository,
            preselectedMeal = mealToAdd,
            initialOpenCamera = openCameraDirectly,
            onDismiss = {
                showVoiceFoodSheet = false
                openCameraDirectly = false
            }
        )
    } else if (routerAddFood || routerVoiceFood || routerAIFood) {
        // Atalhos (Ações Rápidas / deep links / Ilha Dinâmica) que apontam para o registrador de refeição.
        FoodLoggerSheet(
            repository = repository,
            preselectedMeal = null,
            initialOpenCamera = false,
            onDismiss = {
                repository.router.setShowAddFoodSheet(false)
                repository.router.setShowVoiceFoodSheet(false)
                repository.router.setShowAIFoodSheet(false)
            }
        )
    }
}

@Composable
private fun WeeklyCalendarStrip(
    days: List<Long>,
    selectedDate: Long,
    hasFoodLogged: (Long) -> Boolean,
    onSelect: (Long) -> Unit
) {
    val colors = FormaTheme.Colors
    val selectedStart = startOfDay(selectedDate)
    val todayStart = startOfDay(System.currentTimeMillis())
    val dayFormat = remember { SimpleDateFormat("EEE", PtBr) }
    val numFormat = remember { SimpleDateFormat("d", PtBr) }

    FormaCard(modifier = Modifier.fillMaxWidth(), padding = 8.dp, cornerRadius = 18.dp) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            days.forEach { day ->
                val isSelected = day == selectedStart
                val isToday = day == todayStart
                val logged = hasFoodLogged(day)
                val dayLetter = dayFormat.format(java.util.Date(day)).replace(".", "")
                    .replaceFirstChar { it.titlecase(PtBr) }
                val dayNumber = numFormat.format(java.util.Date(day))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .formaPlainClickable { onSelect(day) }
                        .padding(vertical = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = dayLetter,
                        color = if (isSelected) colors.brand else colors.secondaryText,
                        style = FormaTypography.system(11f, if (isSelected) FontWeight.Bold else FontWeight.Medium),
                        maxLines = 1
                    )

                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(CircleShape)
                            .background(
                                if (isSelected) colors.brand else if (isToday) colors.brandSoft else Color.Transparent
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = dayNumber,
                            color = if (isSelected) Color.White else if (isToday) colors.brand else colors.primaryText,
                            style = FormaTypography.rounded(
                                13f,
                                if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal
                            ),
                            maxLines = 1
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(if (logged) colors.energyGreen else Color.Transparent)
                    )
                }
            }
        }
    }
}

@Composable
private fun CalorieBalanceCard(
    totalCalories: Double,
    targetCaloriesAdjusted: Double,
    dailyTmb: Double,
    workoutCalories: Double,
    totalProtein: Double,
    totalCarbs: Double,
    totalFat: Double,
    targetProtein: Double,
    targetCarbs: Double,
    targetFat: Double
) {
    val colors = FormaTheme.Colors
    val remaining = targetCaloriesAdjusted - totalCalories
    val totalBurned = dailyTmb + workoutCalories

    FormaCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Consumo do Dia",
                        color = colors.secondaryText,
                        style = FormaTypography.subheadline
                    )

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${totalCalories.roundToInt()}",
                            color = colors.primaryText,
                            style = FormaTypography.rounded(34f, FontWeight.Bold),
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            text = "/ ${targetCaloriesAdjusted.roundToInt()} kcal",
                            color = colors.secondaryText,
                            style = FormaTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline()
                        )
                    }

                    Row(
                        modifier = Modifier.padding(top = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(sfSymbol("flame.fill"), contentDescription = null, tint = colors.energyGreen, modifier = Modifier.size(11.dp))
                        FormaScaledText(
                            text = "Gasto Total: ${totalBurned.roundToInt()} kcal (Repouso ${dailyTmb.roundToInt()}" +
                                "${if (workoutCalories > 0) " + ${workoutCalories.roundToInt()} treino" else ""})",
                            style = FormaTypography.caption2.copy(fontWeight = FontWeight.SemiBold),
                            color = colors.energyGreen,
                            minScale = 0.8f
                        )
                    }
                }

                Box(modifier = Modifier.width(8.dp))

                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.brandSoft)
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Text(
                        text = if (remaining >= 0) "Disponível" else "Acima da meta",
                        color = colors.secondaryText,
                        style = FormaTypography.caption2.copy(fontWeight = FontWeight.Medium),
                        maxLines = 1,
                        softWrap = false
                    )
                    Text(
                        text = "${abs(remaining.roundToInt())} kcal",
                        color = if (remaining >= 0) colors.brand else colors.energyGreen,
                        style = FormaTypography.rounded(16f, FontWeight.Bold),
                        maxLines = 1,
                        softWrap = false
                    )
                }
            }

            FormaProgressBar(value = totalCalories, total = targetCaloriesAdjusted, color = colors.brand, height = 7.dp)

            if (totalCalories > 0) {
                MacroCaloricDistributionBar(totalProtein, totalCarbs, totalFat)
            }

            HorizontalDivider(thickness = 0.5.dp, color = colors.primary.copy(alpha = 0.06f))

            FormaMacroProgressRow(
                proteinCurrent = totalProtein,
                proteinTarget = targetProtein,
                carbsCurrent = totalCarbs,
                carbsTarget = targetCarbs,
                fatCurrent = totalFat,
                fatTarget = targetFat
            )
        }
    }
}

@Composable
private fun MacroCaloricDistributionBar(totalProtein: Double, totalCarbs: Double, totalFat: Double) {
    val colors = FormaTheme.Colors
    val pKcal = max(0.0, totalProtein * 4.0)
    val cKcal = max(0.0, totalCarbs * 4.0)
    val fKcal = max(0.0, totalFat * 9.0)
    val sum = max(1.0, pKcal + cKcal + fKcal)
    val pPct = ((pKcal / sum) * 100).roundToInt()
    val cPct = ((cKcal / sum) * 100).roundToInt()
    val fPct = max(0, 100 - pPct - cPct)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "Divisão Calórica",
                color = colors.secondaryText,
                style = FormaTypography.caption2.copy(fontWeight = FontWeight.SemiBold),
                modifier = Modifier.weight(1f)
            )
            Text(
                text = "P $pPct% • C $cPct% • G $fPct%",
                color = colors.secondaryText,
                style = FormaTypography.caption2.copy(fontWeight = FontWeight.Bold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth().height(5.dp)) {
            val totalWidth = maxWidth
            fun segment(kcal: Double) = max(4.0, totalWidth.value * (kcal / sum) - 1).dp
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                Box(Modifier.width(segment(pKcal)).height(5.dp).clip(CircleShape).background(colors.protein))
                Box(Modifier.width(segment(cKcal)).height(5.dp).clip(CircleShape).background(colors.carbs))
                Box(Modifier.width(segment(fKcal)).height(5.dp).clip(CircleShape).background(colors.fat))
            }
        }
    }
}

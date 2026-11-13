package com.forma.app.ui.screens.nutrition

import androidx.compose.animation.core.animateFloatAsState
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.CameraEnhance
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.automirrored.filled.MenuBook
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forma.app.data.model.MealType
import com.forma.app.data.services.AnalysisPhase
import com.forma.app.data.services.SmartFoodSuggestion
import com.forma.app.data.services.VoiceFoodIntent
import com.forma.app.data.services.VoicePlateItem
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.sfSymbol
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlin.math.roundToInt

// Porte de FoodLoggerComponents.swift: subcomponentes do registrador multimodal de refeições (Foto, Voz & Texto).

@Composable
fun CleanMacroBadge(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    val colors = FormaTheme.Colors
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(FormaTheme.Radius.badge))
            .background(color.copy(alpha = 0.10f))
            .padding(vertical = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        Text(
            text = label,
            color = colors.secondaryText,
            style = FormaTypography.caption2.copy(fontWeight = FontWeight.Medium),
            maxLines = 1
        )
        Text(
            text = value,
            color = color,
            style = FormaTypography.rounded(14f, FontWeight.Bold),
            maxLines = 1
        )
    }
}

@Composable
fun MealTypeSelectMenu(
    selectedMeal: MealType,
    onSelect: (MealType) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    var expanded by remember { mutableStateOf(false) }
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .clip(CircleShape)
                .background(colors.brandSoft)
                .formaPlainClickable { expanded = true }
                .padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(selectedMeal.materialIcon, contentDescription = null, tint = colors.brand, modifier = Modifier.size(14.dp))
            Text(
                text = selectedMeal.rawValue,
                color = colors.brand,
                style = FormaTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                softWrap = false
            )
            Icon(sfSymbol("chevron.down"), contentDescription = null, tint = colors.brand, modifier = Modifier.size(12.dp))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            MealType.values().forEach { meal ->
                DropdownMenuItem(
                    text = { Text(meal.rawValue, color = colors.primaryText) },
                    leadingIcon = { Icon(meal.materialIcon, contentDescription = null, tint = colors.brand) },
                    onClick = {
                        expanded = false
                        onSelect(meal)
                    }
                )
            }
        }
    }
}

@Composable
fun FoodAnalysisPhaseBadge(phase: AnalysisPhase, modifier: Modifier = Modifier) {
    val colors = FormaTheme.Colors
    val isTacoPhase = phase == AnalysisPhase.TACO_DATABASE
    val isPhotoPhase = phase == AnalysisPhase.PHOTO_VISION
    val phaseColor = if (isTacoPhase) colors.tacoCyan else colors.aiAccent
    val icon = if (isTacoPhase) Icons.AutoMirrored.Filled.MenuBook else if (isPhotoPhase) Icons.Filled.CameraEnhance else sfSymbol("sparkles")

    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(phaseColor.copy(alpha = 0.12f))
            .border(0.8.dp, phaseColor.copy(alpha = 0.28f), CircleShape)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Icon(icon, contentDescription = null, tint = phaseColor, modifier = Modifier.size(12.dp))
        Text(
            text = if (isTacoPhase) "Tabela TACO..." else "IA Google Gemini...",
            color = phaseColor,
            style = FormaTypography.rounded(11f, FontWeight.Bold),
            maxLines = 1,
            softWrap = false
        )
        CircularProgressIndicator(
            modifier = Modifier.size(11.dp),
            color = phaseColor,
            strokeWidth = 1.5.dp
        )
    }
}

@Composable
fun FoodPlateResultCard(
    intent: VoiceFoodIntent,
    isAnalyzing: Boolean,
    onUpdateMealType: (MealType) -> Unit,
    onRecalculateAI: () -> Unit,
    onAdjustPortion: (String, Double) -> Unit,
    onEditExactPortion: (VoicePlateItem) -> Unit,
    onRemoveItem: (String) -> Unit,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier,
    isSaving: Boolean = false
) {
    val colors = FormaTheme.Colors
    FormaCard(modifier = modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            // headerRow
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (intent.items.size > 1) "Prato Consolidado (${intent.items.size} itens)" else "Alimento Identificado",
                        color = colors.secondaryText,
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "${intent.totalCalories.roundToInt()}",
                            color = colors.primaryText,
                            style = FormaTypography.rounded(32f, FontWeight.Bold),
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            text = "kcal",
                            color = colors.secondaryText,
                            style = FormaTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }

                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MealTypeSelectMenu(selectedMeal = intent.mealType, onSelect = onUpdateMealType)

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(colors.aiAccent.copy(alpha = 0.12f))
                            .formaPlainClickable(enabled = !isAnalyzing, onClick = onRecalculateAI)
                            .padding(horizontal = 10.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(sfSymbol("arrow.triangle.2.circlepath"), contentDescription = null, tint = colors.aiAccent, modifier = Modifier.size(12.dp))
                        Text(
                            text = "Recalcular",
                            color = colors.aiAccent,
                            style = FormaTypography.rounded(11f, FontWeight.Bold),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }
            }

            // macroRow
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                CleanMacroBadge("Proteína", "${intent.totalProtein.roundToInt()}g", colors.protein, Modifier.weight(1f))
                CleanMacroBadge("Carboidratos", "${intent.totalCarbs.roundToInt()}g", colors.carbs, Modifier.weight(1f))
                CleanMacroBadge("Gordura", "${intent.totalFat.roundToInt()}g", colors.fat, Modifier.weight(1f))
            }

            if (intent.aiSummary.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.aiAccent.copy(alpha = 0.08f))
                        .padding(10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.Top
                ) {
                    Icon(sfSymbol("sparkles"), contentDescription = null, tint = colors.aiAccent, modifier = Modifier.size(14.dp))
                    Text(
                        text = intent.aiSummary,
                        color = colors.secondaryText,
                        style = FormaTypography.caption
                    )
                }
            }

            HorizontalDivider(thickness = 0.5.dp, color = colors.primary.copy(alpha = 0.06f))

            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                intent.items.forEach { item ->
                    PlateRecognizedItemRow(
                        item = item,
                        canRemove = intent.items.size > 1,
                        onAdjustPortion = { delta -> onAdjustPortion(item.id, delta) },
                        onEditExactPortion = { onEditExactPortion(item) },
                        onRemove = { onRemoveItem(item.id) }
                    )
                }
            }

            Row(
                modifier = Modifier
                    .padding(top = 4.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(colors.brand)
                    .formaPlainClickable(enabled = !isSaving, onClick = onConfirm)
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isSaving) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Icon(sfSymbol("checkmark"), contentDescription = null, tint = Color.White, modifier = Modifier.size(16.dp))
                }
                Text(
                    text = if (intent.items.size > 1) "Registrar Refeição (${intent.items.size} itens)" else "Registrar Refeição",
                    color = Color.White,
                    style = FormaTypography.headline,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
fun PlateRecognizedItemRow(
    item: VoicePlateItem,
    canRemove: Boolean,
    onAdjustPortion: (Double) -> Unit,
    onEditExactPortion: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val isLiquidUnit = item.isLiquid || item.unit == "ml"
    val stepValue = if (isLiquidUnit) 50.0 else 25.0
    val unitSuffix = if (isLiquidUnit) "ml" else "g"

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(FormaTheme.Radius.inner))
            .background(colors.systemBackground.copy(alpha = 0.75f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = item.name,
                        color = colors.primaryText,
                        style = FormaTypography.system(15f, FontWeight.SemiBold),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )

                    val lowerSource = item.source.lowercase()
                    val isPhotoSource = lowerSource.contains("foto")
                    val isHistorySource = lowerSource.contains("hist")
                    val isFormaSource = isPhotoSource || lowerSource.contains("ia") ||
                        lowerSource.contains("gemini") || lowerSource.contains("forma")
                    val badgeColor = if (isFormaSource) colors.aiAccent else if (isHistorySource) colors.brand else colors.tacoCyan
                    val badgeTitle = if (isPhotoSource) "FOTO" else if (isHistorySource) "HÁBITO" else "FORMA"
                    val badgeIcon: ImageVector = when {
                        isPhotoSource -> sfSymbol("camera.fill")
                        isFormaSource -> sfSymbol("sparkles")
                        isHistorySource -> Icons.Filled.History
                        else -> Icons.AutoMirrored.Filled.MenuBook
                    }

                    Row(
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(badgeColor.copy(alpha = 0.12f))
                            .padding(horizontal = 6.dp, vertical = 2.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        Icon(badgeIcon, contentDescription = null, tint = badgeColor, modifier = Modifier.size(9.dp))
                        Text(
                            text = badgeTitle,
                            color = badgeColor,
                            style = FormaTypography.rounded(9f, FontWeight.ExtraBold),
                            maxLines = 1,
                            softWrap = false
                        )
                    }
                }

                Text(
                    text = "${item.calories.roundToInt()} kcal • ${item.protein.roundToInt()}g P • ${item.carbs.roundToInt()}g C • ${item.fat.roundToInt()}g G",
                    color = colors.secondaryText,
                    style = FormaTypography.caption,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            if (canRemove) {
                Icon(
                    imageVector = sfSymbol("trash"),
                    contentDescription = "Remover item",
                    tint = colors.danger.copy(alpha = 0.75f),
                    modifier = Modifier
                        .formaPlainClickable(onClick = onRemove)
                        .padding(6.dp)
                        .size(15.dp)
                )
            }
        }

        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                text = "Quantidade",
                color = colors.secondaryText,
                style = FormaTypography.caption,
                maxLines = 1,
                modifier = Modifier.weight(1f)
            )

            StepPill(text = "-${stepValue.toInt()} $unitSuffix") { onAdjustPortion(-stepValue) }

            Text(
                text = "${item.portionValue.roundToInt()} $unitSuffix",
                color = colors.brand,
                style = FormaTypography.rounded(14f, FontWeight.Bold),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .background(colors.brandSoft.copy(alpha = 0.75f))
                    .formaPlainClickable(onClick = onEditExactPortion)
                    .widthIn(min = 62.dp)
                    .padding(vertical = 4.dp, horizontal = 6.dp)
            )

            StepPill(text = "+${stepValue.toInt()} $unitSuffix") { onAdjustPortion(stepValue) }
        }
    }
}

@Composable
private fun StepPill(text: String, onClick: () -> Unit) {
    val colors = FormaTheme.Colors
    Text(
        text = text,
        color = colors.primaryText,
        style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold),
        maxLines = 1,
        softWrap = false,
        modifier = Modifier
            .clip(CircleShape)
            .background(colors.primary.copy(alpha = 0.06f))
            .formaPlainClickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 5.dp)
    )
}

@Composable
fun FoodQuickSuggestionsSection(
    mealType: MealType,
    suggestions: List<SmartFoodSuggestion>,
    currentPlateNames: Set<String>,
    onSelectSuggestion: (SmartFoodSuggestion) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val hasHistoryItems = suggestions.any { it.isFromHistory }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (hasHistoryItems) "Frequentes em ${mealType.rawValue}" else "Sugestões para ${mealType.rawValue}",
                color = colors.secondaryText,
                style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (hasHistoryItems) {
                Text(
                    text = "Toque para adicionar",
                    color = colors.secondaryText,
                    style = FormaTypography.caption2,
                    maxLines = 1
                )
            }
        }

        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            suggestions.forEach { item ->
                val isAdded = currentPlateNames.contains(item.name.lowercase())
                val shape = RoundedCornerShape(12.dp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(shape)
                        .background(colors.cardSurface)
                        .border(
                            0.7.dp,
                            if (isAdded) colors.energyGreen.copy(alpha = 0.35f) else colors.primary.copy(alpha = 0.06f),
                            shape
                        )
                        .formaPlainClickable { onSelectSuggestion(item) }
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = if (isAdded) Icons.Filled.CheckCircle else Icons.Filled.AddCircle,
                        contentDescription = null,
                        tint = if (isAdded) colors.energyGreen else colors.brand,
                        modifier = Modifier.size(18.dp)
                    )

                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = item.name,
                            color = colors.primaryText,
                            style = FormaTypography.system(14f, FontWeight.SemiBold),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${item.portionValue.roundToInt()}${item.unit} • ${item.calories.roundToInt()} kcal • P ${item.protein.roundToInt()}g",
                            color = colors.secondaryText,
                            style = FormaTypography.caption2,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    Text(
                        text = item.reasonBadge,
                        color = if (item.isFromHistory) colors.brand else colors.secondaryText,
                        style = FormaTypography.rounded(10f, FontWeight.Bold),
                        maxLines = 1,
                        softWrap = false,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (item.isFromHistory) colors.brandSoft else colors.primary.copy(alpha = 0.06f))
                            .padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun FoodSavedConfirmationView(
    intent: VoiceFoodIntent?,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier = Modifier
                .size(90.dp)
                .clip(CircleShape)
                .background(colors.energyGreen.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(sfSymbol("checkmark"), contentDescription = null, tint = colors.energyGreen, modifier = Modifier.size(44.dp))
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "Refeição Registrada",
                color = colors.primaryText,
                style = FormaTypography.title2.copy(fontWeight = FontWeight.Bold)
            )
            if (intent != null && intent.items.isNotEmpty()) {
                Text(
                    text = "${intent.items.size} ${if (intent.items.size == 1) "item adicionado" else "itens somados"} ao ${intent.mealType.rawValue}",
                    color = colors.secondaryText,
                    style = FormaTypography.subheadline
                )
                Text(
                    text = "+${intent.totalCalories.roundToInt()} kcal",
                    color = colors.brand,
                    style = FormaTypography.rounded(20f, FontWeight.Bold),
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "Concluir",
            color = Color.White,
            style = FormaTypography.headline,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .padding(horizontal = 20.dp)
                .padding(bottom = 20.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(colors.brand)
                .formaPlainClickable(onClick = onDone)
                .padding(vertical = 14.dp)
        )
    }
}

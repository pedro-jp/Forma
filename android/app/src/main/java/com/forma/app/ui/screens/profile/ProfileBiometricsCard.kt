package com.forma.app.ui.screens.profile

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.forma.app.data.model.UserProfile
import com.forma.app.ui.components.FormaBadge
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.screens.evolution.ProfileInputValidator
import com.forma.app.ui.screens.evolution.fmt1
import com.forma.app.ui.screens.evolution.fmt2
import com.forma.app.ui.screens.evolution.symbolFor
import com.forma.app.ui.screens.evolution.toIntSafe
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlin.math.abs

// Porte de Forma/Views/Profile/ProfileBiometricsCard.swift

/** `ProfileTargetWeightCard`: Meta de Peso Alvo. */
@Composable
fun ProfileTargetWeightCard(
    profile: UserProfile,
    onProfileChange: (UserProfile) -> Unit,
    onAdjustCurrentWeight: (newWeight: Double, syncText: Boolean, recordEntry: Boolean) -> Unit,
    onFinishWeightSlider: () -> Unit,
    onFinishTargetSlider: () -> Unit,
    onFocusWeightField: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    FormaCard(modifier = modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(symbolFor("flag.checkered"), null, tint = colors.brand, modifier = Modifier.size(20.dp))
                Text(
                    text = "Meta de Peso Alvo",
                    color = colors.primaryText,
                    style = FormaTypography.headline,
                    modifier = Modifier.weight(1f)
                )
                val diff = profile.targetWeight - profile.currentWeight
                val diffText = if (diff >= 0) "+${fmt1(diff)} kg" else "${fmt1(diff)} kg"
                val onTarget = abs(diff) < 0.05
                FormaBadge(
                    text = if (onTarget) "Na meta!" else diffText,
                    tint = if (onTarget) colors.energyGreen else colors.brand
                )
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.primary.copy(alpha = 0.035f))
                        .border(0.5.dp, colors.primary.copy(alpha = 0.06f), RoundedCornerShape(14.dp))
                        .formaPlainClickable(onClick = onFocusWeightField)
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "Peso Atual",
                            color = colors.secondaryText,
                            style = FormaTypography.caption2.copy(fontWeight = FontWeight.Medium)
                        )
                        Icon(symbolFor("pencil"), null, tint = colors.brand, modifier = Modifier.size(9.dp))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = fmt1(profile.currentWeight),
                            color = colors.primaryText,
                            style = FormaTypography.rounded(24f, FontWeight.Bold),
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            text = "kg",
                            color = colors.secondaryText,
                            style = FormaTypography.caption,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }

                Icon(
                    imageVector = symbolFor("arrow.right"),
                    contentDescription = null,
                    tint = colors.brand.copy(alpha = 0.7f),
                    modifier = Modifier.size(16.dp)
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.brandSoft)
                        .border(0.5.dp, colors.brand.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                        .padding(12.dp),
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Peso Alvo",
                        color = colors.secondaryText,
                        style = FormaTypography.caption2.copy(fontWeight = FontWeight.Medium)
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = fmt1(profile.targetWeight),
                            color = colors.brand,
                            style = FormaTypography.rounded(24f, FontWeight.Bold),
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            text = "kg",
                            color = colors.secondaryText,
                            style = FormaTypography.caption,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }
            }

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = "Ajustar peso atual",
                        color = colors.secondaryText,
                        style = FormaTypography.caption,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = symbolFor("minus.circle.fill"),
                        contentDescription = "Diminuir peso",
                        tint = colors.brand,
                        modifier = Modifier
                            .size(22.dp)
                            .formaPlainClickable { onAdjustCurrentWeight(profile.currentWeight - 0.5, true, true) }
                    )
                    Text(
                        text = "${fmt1(profile.currentWeight)} kg",
                        color = colors.primaryText,
                        style = FormaTypography.subheadline.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.widthIn(min = 58.dp),
                        maxLines = 1
                    )
                    Icon(
                        imageVector = symbolFor("plus.circle.fill"),
                        contentDescription = "Aumentar peso",
                        tint = colors.brand,
                        modifier = Modifier
                            .size(22.dp)
                            .formaPlainClickable { onAdjustCurrentWeight(profile.currentWeight + 0.5, true, true) }
                    )
                }

                FormaSlider(
                    value = profile.currentWeight.coerceIn(35.0, 160.0),
                    onValueChange = { onAdjustCurrentWeight(it, true, false) },
                    range = 35.0..160.0,
                    step = 0.1,
                    tint = colors.energyGreen,
                    onValueChangeFinished = onFinishWeightSlider
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Ajustar peso alvo",
                        color = colors.secondaryText,
                        style = FormaTypography.caption,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "${fmt1(profile.targetWeight)} kg",
                        color = colors.brand,
                        style = FormaTypography.subheadline.copy(fontWeight = FontWeight.Bold)
                    )
                }
                FormaSlider(
                    value = profile.targetWeight.coerceIn(40.0, 160.0),
                    onValueChange = { onProfileChange(profile.copy(targetWeight = it)) },
                    range = 40.0..160.0,
                    step = 0.5,
                    tint = colors.brand,
                    onValueChangeFinished = onFinishTargetSlider
                )
            }
        }
    }
}

/** `ProfileBiometricsCard`: Medidas Corporais (altura/peso com validação flexível) + explicação Mifflin. */
@Composable
fun ProfileBiometricsCard(
    profile: UserProfile,
    heightInputText: String,
    onHeightInputTextChange: (String) -> Unit,
    weightInputText: String,
    onWeightInputTextChange: (String) -> Unit,
    weightFocusRequester: FocusRequester,
    contextualTdee: Double,
    onApplyHeight: (parsed: Double, syncText: Boolean) -> Unit,
    onApplyWeight: (parsed: Double, syncText: Boolean, recordEntry: Boolean) -> Unit,
    onHeightFocusChange: (Boolean) -> Unit,
    onWeightFocusChange: (Boolean) -> Unit,
    onRecalculateTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val focusManager = LocalFocusManager.current
    var heightFocused by remember { mutableStateOf(false) }
    var weightFocused by remember { mutableStateOf(false) }
    val heightFocusRequester = remember { FocusRequester() }
    val shape = RoundedCornerShape(14.dp)

    FormaCard(modifier = modifier.fillMaxWidth(), padding = 20.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(symbolFor("figure.arms.open"), null, tint = colors.brand, modifier = Modifier.size(20.dp))
                Text(
                    text = "Medidas Corporais",
                    color = colors.primaryText,
                    style = FormaTypography.headline,
                    modifier = Modifier.weight(1f)
                )
                Row(
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(colors.brandSoft)
                        .formaPlainClickable(onClick = onRecalculateTap)
                        .padding(horizontal = 9.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(symbolFor("arrow.triangle.2.circlepath"), null, tint = colors.brand, modifier = Modifier.size(10.dp))
                    Text(
                        text = "Recalcular",
                        color = colors.brand,
                        style = FormaTypography.caption2.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                // Altura
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(colors.primary.copy(alpha = 0.035f))
                        .border(
                            BorderStroke(
                                if (heightFocused) 1.4.dp else 0.8.dp,
                                if (heightFocused) colors.brand else colors.primary.copy(alpha = 0.08f)
                            ),
                            shape
                        )
                        .formaPlainClickable { heightFocusRequester.requestFocus() }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(symbolFor("ruler"), null, tint = colors.secondaryText, modifier = Modifier.size(11.dp))
                        Text(
                            text = "Altura",
                            color = colors.secondaryText,
                            style = FormaTypography.caption2.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        BasicTextField(
                            value = heightInputText,
                            onValueChange = { newText ->
                                onHeightInputTextChange(newText)
                                ProfileInputValidator.parseFlexibleHeight(newText)?.let { onApplyHeight(it, false) }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            textStyle = FormaTypography.rounded(20f, FontWeight.Bold).copy(color = colors.primaryText),
                            cursorBrush = SolidColor(colors.brand),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .widthIn(min = 40.dp)
                                .focusRequester(heightFocusRequester)
                                .onFocusChanged { state ->
                                    val was = heightFocused
                                    heightFocused = state.isFocused
                                    onHeightFocusChange(state.isFocused)
                                    if (was && !state.isFocused) {
                                        val parsed = ProfileInputValidator.parseFlexibleHeight(heightInputText)
                                        if (parsed != null) onApplyHeight(parsed, true)
                                        else onHeightInputTextChange(fmt2(profile.heightMeters))
                                    }
                                }
                        )
                        Text(
                            text = "m",
                            color = colors.secondaryText,
                            style = FormaTypography.subheadline,
                            modifier = Modifier.alignByBaseline()
                        )
                    }
                }

                // Peso
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clip(shape)
                        .background(colors.brandSoft.copy(alpha = 0.7f))
                        .border(
                            BorderStroke(
                                if (weightFocused) 1.4.dp else 0.8.dp,
                                if (weightFocused) colors.brand else colors.brand.copy(alpha = 0.2f)
                            ),
                            shape
                        )
                        .formaPlainClickable { weightFocusRequester.requestFocus() }
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                        Icon(symbolFor("scalemass.fill"), null, tint = colors.brand, modifier = Modifier.size(11.dp))
                        Text(
                            text = "Peso Atual",
                            color = colors.secondaryText,
                            style = FormaTypography.caption2.copy(fontWeight = FontWeight.Medium)
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                        BasicTextField(
                            value = weightInputText,
                            onValueChange = { newText ->
                                onWeightInputTextChange(newText)
                                ProfileInputValidator.parseFlexibleWeight(newText)?.let { onApplyWeight(it, false, false) }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                            keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus() }),
                            textStyle = FormaTypography.rounded(20f, FontWeight.Bold).copy(color = colors.primaryText),
                            cursorBrush = SolidColor(colors.brand),
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .widthIn(min = 40.dp)
                                .focusRequester(weightFocusRequester)
                                .onFocusChanged { state ->
                                    val was = weightFocused
                                    weightFocused = state.isFocused
                                    onWeightFocusChange(state.isFocused)
                                    if (was && !state.isFocused) {
                                        val parsed = ProfileInputValidator.parseFlexibleWeight(weightInputText)
                                        if (parsed != null) onApplyWeight(parsed, true, true)
                                        else onWeightInputTextChange(fmt1(profile.currentWeight))
                                    }
                                }
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

            Column(
                modifier = Modifier.padding(vertical = 2.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(symbolFor("bolt.heart.fill"), null, tint = colors.brand, modifier = Modifier.size(12.dp))
                    Text(
                        text = "Gasto em Repouso: ${profile.tmb.toIntSafe()} kcal • Gasto Total: ${contextualTdee.toIntSafe()} kcal/dia",
                        color = colors.primaryText,
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold),
                        maxLines = 1
                    )
                }
                Text(
                    text = "Meta Forma: ${profile.targetCalorieIntake.toIntSafe()} kcal • P ${profile.targetProtein.toIntSafe()}g • C ${profile.targetCarbohydrates.toIntSafe()}g • G ${profile.targetFat.toIntSafe()}g",
                    color = colors.secondaryText,
                    style = FormaTypography.caption2,
                    maxLines = 1
                )

                Row(modifier = Modifier.padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    com.forma.app.ui.screens.evolution.CitationLink(
                        "Fonte TMB: Mifflin-St Jeor",
                        "https://pubmed.ncbi.nlm.nih.gov/2305711/"
                    )
                    com.forma.app.ui.screens.evolution.CitationLink(
                        "Macros: ISSN (PubMed)",
                        "https://pubmed.ncbi.nlm.nih.gov/28642676/"
                    )
                }
            }
        }
    }
}

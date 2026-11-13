package com.forma.app.ui.screens.profile

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlin.math.roundToInt

// Controles compartilhados das abas Evolução/Perfil (Slider tingido e Picker `.segmented` do iOS).

/**
 * `Slider(value:in:step:)` do iOS com `.tint(tint)`.
 *
 * Os passos são aplicados por arredondamento (sem marcas de `steps`, como o Slider nativo do iOS).
 * [onValueChangeFinished] equivale ao callback `onEditingChanged { editing in if !editing }`.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormaSlider(
    value: Double,
    onValueChange: (Double) -> Unit,
    range: ClosedFloatingPointRange<Double>,
    step: Double,
    tint: Color,
    modifier: Modifier = Modifier,
    onValueChangeFinished: (() -> Unit)? = null
) {
    val colors = FormaTheme.Colors
    // Valor local durante o arraste (evita travar o thumb quando o pai arredonda/rejeita o valor).
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(value.toFloat()) }
    val shown = if (dragging) dragValue else value.toFloat().coerceIn(range.start.toFloat(), range.endInclusive.toFloat())
    Slider(
        value = shown,
        onValueChange = { raw ->
            dragging = true
            dragValue = raw
            val stepped = (Math.round(raw.toDouble() / step) * step)
                .coerceIn(range.start, range.endInclusive)
            onValueChange(stepped)
        },
        onValueChangeFinished = {
            dragging = false
            onValueChangeFinished?.invoke()
        },
        valueRange = range.start.toFloat()..range.endInclusive.toFloat(),
        colors = SliderDefaults.colors(
            thumbColor = Color.White,
            activeTrackColor = tint,
            inactiveTrackColor = colors.secondaryFill
        ),
        modifier = modifier.fillMaxWidth()
    )
}

/** `Picker(...).pickerStyle(.segmented)` do iOS. */
@Composable
fun FormaSegmentedPicker(
    options: List<String>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val outer = RoundedCornerShape(9.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(outer)
            .background(colors.tertiaryFill)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp)
    ) {
        options.forEachIndexed { index, title ->
            val selected = index == selectedIndex
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(7.dp))
                    .background(if (selected) (if (colors.isDark) colors.tertiaryBackground else Color.White) else Color.Transparent)
                    .formaPlainClickable { onSelect(index) }
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = title,
                    color = colors.primaryText,
                    style = FormaTypography.system(13f, if (selected) FontWeight.SemiBold else FontWeight.Normal),
                    maxLines = 1
                )
            }
        }
    }
}

internal fun Double.roundedInt(): Int = this.roundToInt()

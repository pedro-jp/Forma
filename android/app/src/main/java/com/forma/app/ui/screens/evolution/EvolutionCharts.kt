package com.forma.app.ui.screens.evolution

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forma.app.data.model.WeightEntry
import com.forma.app.ui.components.FormaBadge
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// Porte de Forma/Views/Evolution/EvolutionChartsSection.swift (Swift Charts -> Compose Canvas)

/** `DayCalorieSummary` (iOS). */
data class DayCalorieSummary(
    val date: Long,
    val dayName: String,
    val consumed: Double,
    val tmb: Double,
    val activeBurned: Double,
    val proteinPercent: Double,
    val waterPercent: Double
) {
    val totalBurned: Double get() = tmb + activeBurned
    val netBalance: Double get() = consumed - totalBurned
}

/** Cor "Repouso" do gráfico (Color(red: 0.39, green: 0.40, blue: 0.95)). */
private val RestingColor = Color(0.39f, 0.40f, 0.95f)

private val ptBR = Locale("pt", "BR")

/** `.dateTime.day().month(.abbreviated)` em pt_BR -> "9 de nov." */
private fun dayMonth(millis: Long): String = SimpleDateFormat("d 'de' MMM", ptBR).format(Date(millis))

// MARK: - Helpers de eixo

/** Ticks "agradáveis" (equivalente a `AxisMarks(values: .automatic(desiredCount:))`). */
private fun niceTicks(minValue: Double, maxValue: Double, desired: Int): List<Double> {
    val span = maxValue - minValue
    if (span <= 0.0) return listOf(minValue)
    val raw = span / desired
    val mag = 10.0.pow(floor(log10(raw)))
    val norm = raw / mag
    val step = when {
        norm <= 1.0 -> 1.0
        norm <= 2.0 -> 2.0
        norm <= 5.0 -> 5.0
        else -> 10.0
    } * mag
    val ticks = ArrayList<Double>()
    var t = ceil(minValue / step - 1e-9) * step
    while (t <= maxValue + step * 1e-6) {
        ticks.add(t)
        t += step
    }
    return ticks
}

private fun niceCeil(maxValue: Double, desired: Int): Double {
    if (maxValue <= 0.0) return 1.0
    val raw = maxValue / desired
    val mag = 10.0.pow(floor(log10(raw)))
    val norm = raw / mag
    val step = when {
        norm <= 1.0 -> 1.0
        norm <= 2.0 -> 2.0
        norm <= 5.0 -> 5.0
        else -> 10.0
    } * mag
    return ceil(maxValue / step - 1e-9) * step
}

private fun DrawScope.dashed(dashOn: Float, dashOff: Float) =
    PathEffect.dashPathEffect(floatArrayOf(dashOn.dp.toPx(), dashOff.dp.toPx()), 0f)

private fun DrawScope.label(
    measurer: TextMeasurer,
    text: String,
    style: TextStyle,
    x: Float,
    y: Float,
    align: Align
) {
    val layout = measurer.measure(text, style)
    val left = when (align) {
        Align.Start -> x
        Align.Center -> x - layout.size.width / 2f
        Align.End -> x - layout.size.width
    }
    drawText(layout, topLeft = Offset(left, y))
}

private enum class Align { Start, Center, End }

/** Curva Catmull-Rom (uniforme) convertida em Béziers cúbicas. */
private fun catmullRomPath(points: List<Offset>): Path {
    val path = Path()
    if (points.isEmpty()) return path
    path.moveTo(points[0].x, points[0].y)
    if (points.size == 1) return path
    for (i in 0 until points.size - 1) {
        val p0 = points[max(0, i - 1)]
        val p1 = points[i]
        val p2 = points[i + 1]
        val p3 = points[min(points.size - 1, i + 2)]
        val c1 = Offset(p1.x + (p2.x - p0.x) / 6f, p1.y + (p2.y - p0.y) / 6f)
        val c2 = Offset(p2.x - (p3.x - p1.x) / 6f, p2.y - (p3.y - p1.y) / 6f)
        path.cubicTo(c1.x, c1.y, c2.x, c2.y, p2.x, p2.y)
    }
    return path
}

private class PlotArea(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
}

private fun plotArea(width: Float, height: Float, density: Density, yAxisWidthDp: Float, xAxisHeightDp: Float): PlotArea =
    with(density) {
        PlotArea(
            left = yAxisWidthDp.dp.toPx(),
            top = 6.dp.toPx(),
            right = width - 4.dp.toPx(),
            bottom = height - xAxisHeightDp.dp.toPx()
        )
    }

// MARK: - 1. Gráfico Interativo de Tendência de Peso

private data class WeightPoint(val date: Long, val weight: Double)

@Composable
fun WeightTrendChartCard(
    weightEntries: List<WeightEntry>,
    currentWeight: Double,
    targetWeight: Double,
    onLogWeightTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    var selectedIndex by remember { mutableStateOf<Int?>(null) }

    val points = remember(weightEntries, currentWeight) {
        val sorted = weightEntries.sortedBy { it.entryDate }.takeLast(16).map { WeightPoint(it.entryDate, it.weight) }
        if (sorted.isNotEmpty()) sorted else listOf(WeightPoint(System.currentTimeMillis(), currentWeight))
    }
    val yDomain = remember(points, targetWeight) {
        val all = points.map { it.weight } + targetWeight
        val minVal = (all.minOrNull() ?: 60.0) - 2.5
        val maxVal = (all.maxOrNull() ?: 85.0) + 2.5
        max(25.0, minVal)..max(minVal + 5.0, maxVal)
    }
    val selectedPoint = selectedIndex?.let { points.getOrNull(it) }

    FormaCard(modifier = modifier.fillMaxWidth(), padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Curva de Peso Corporal",
                        color = colors.primaryText,
                        style = FormaTypography.rounded(16f, FontWeight.Bold)
                    )
                    if (selectedPoint != null) {
                        Text(
                            text = "${dayMonth(selectedPoint.date)} • ${fmt1(selectedPoint.weight)} kg",
                            color = colors.brand,
                            style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold)
                        )
                    } else {
                        Text(
                            text = "Toque ou arraste no gráfico para inspecionar",
                            color = colors.secondaryText,
                            style = FormaTypography.caption
                        )
                    }
                }
                FormaBadge(
                    text = "Alvo ${fmt1(targetWeight)} kg",
                    icon = Icons.Filled.Flag,
                    tint = colors.energyGreen
                )
            }

            val measurer = rememberTextMeasurer()
            val axisStyle = FormaTypography.system(10f).copy(color = colors.secondaryText)
            val metaStyle = TextStyle(fontSize = 9.sp, fontWeight = FontWeight.Bold, color = colors.energyGreen)
            val brand = colors.brand
            val grid = colors.primary.copy(alpha = 0.06f)
            val green = colors.energyGreen

            Canvas(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(175.dp)
                    .weightChartGestures(points) { idx -> selectedIndex = idx }
            ) {
                val area = plotArea(size.width, size.height, this, 34f, 18f)
                val t0 = points.first().date.toDouble()
                val t1 = points.last().date.toDouble()
                val pad = 6.dp.toPx()
                fun xOf(date: Long): Float =
                    if (points.size == 1 || t1 <= t0) (area.left + area.right) / 2f
                    else area.left + pad + ((date - t0) / (t1 - t0)).toFloat() * (area.width - 2 * pad)

                fun yOf(w: Double): Float =
                    area.bottom - ((w - yDomain.start) / (yDomain.endInclusive - yDomain.start)).toFloat() * area.height

                // Eixo Y: grade + rótulos "NNkg"
                niceTicks(yDomain.start, yDomain.endInclusive, 4).forEach { v ->
                    val y = yOf(v)
                    drawLine(grid, Offset(area.left, y), Offset(area.right, y), strokeWidth = 0.5.dp.toPx())
                    label(measurer, "${v.toInt()}kg", axisStyle, area.left - 6.dp.toPx(), y - 7.dp.toPx(), Align.End)
                }

                // Eixo X: 4 marcas de data
                val labelCount = if (points.size == 1) 1 else 4
                for (i in 0 until labelCount) {
                    val frac = if (labelCount == 1) 0.5f else i / (labelCount - 1f)
                    val date = (t0 + (t1 - t0) * frac).toLong()
                    val x = if (labelCount == 1) xOf(points[0].date) else area.left + pad + frac * (area.width - 2 * pad)
                    label(measurer, dayMonth(date), axisStyle, x, area.bottom + 4.dp.toPx(), Align.Center)
                }

                // RuleMark Meta (tracejada) + anotação "Meta"
                val yMeta = yOf(targetWeight)
                drawLine(
                    color = green.copy(alpha = 0.75f),
                    start = Offset(area.left, yMeta),
                    end = Offset(area.right, yMeta),
                    strokeWidth = 1.3.dp.toPx(),
                    pathEffect = dashed(5f, 4f)
                )
                label(measurer, "Meta", metaStyle, area.right, yMeta - 13.dp.toPx(), Align.End)

                val offsets = points.map { Offset(xOf(it.date), yOf(it.weight)) }

                // AreaMark (catmullRom, gradiente vertical)
                if (offsets.size > 1) {
                    val areaPath = catmullRomPath(offsets).apply {
                        lineTo(offsets.last().x, area.bottom)
                        lineTo(offsets.first().x, area.bottom)
                        close()
                    }
                    drawPath(
                        areaPath,
                        Brush.verticalGradient(
                            colors = listOf(brand.copy(alpha = 0.28f), brand.copy(alpha = 0.02f)),
                            startY = area.top,
                            endY = area.bottom
                        )
                    )
                    // LineMark
                    drawPath(
                        catmullRomPath(offsets),
                        brand,
                        style = Stroke(width = 2.6.dp.toPx(), cap = StrokeCap.Round)
                    )
                }

                // Linha vertical do ponto selecionado
                selectedIndex?.let { idx ->
                    offsets.getOrNull(idx)?.let { o ->
                        drawLine(
                            color = brand.copy(alpha = 0.35f),
                            start = Offset(o.x, area.top),
                            end = Offset(o.x, area.bottom),
                            strokeWidth = 1.dp.toPx(),
                            pathEffect = dashed(3f, 3f)
                        )
                    }
                }

                // PointMark (symbolSize 32 / 70)
                offsets.forEachIndexed { i, o ->
                    val r = if (selectedIndex == i) 4.2.dp.toPx() else 2.8.dp.toPx()
                    drawCircle(brand, radius = r, center = o)
                }
            }

            if (weightEntries.size < 2) {
                Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "Registre novas pesagens para visualizar sua tendência semanal.",
                        color = colors.secondaryText,
                        style = FormaTypography.caption2,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "+ Pesagem",
                        color = colors.brand,
                        style = FormaTypography.caption2.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.formaPlainClickable(onClick = onLogWeightTap)
                    )
                }
            }
        }
    }
}

/**
 * Seleção do ponto mais próximo do toque no eixo X (iOS: `chartXSelection` + `min(by: abs(date - selected))`).
 * A geometria do plot é derivada do tamanho do Canvas dentro do `pointerInput`.
 */
private fun Modifier.weightChartGestures(points: List<WeightPoint>, onSelect: (Int?) -> Unit): Modifier =
    this.pointerInput(points) {
        fun select(x: Float) {
            val area = plotArea(size.width.toFloat(), size.height.toFloat(), this, 34f, 18f)
            val pad = 6.dp.toPx()
            val t0 = points.first().date.toDouble()
            val t1 = points.last().date.toDouble()
            val xs = points.map {
                if (points.size == 1 || t1 <= t0) (area.left + area.right) / 2f
                else area.left + pad + ((it.date - t0) / (t1 - t0)).toFloat() * (area.width - 2 * pad)
            }
            onSelect(xs.indices.minByOrNull { abs(xs[it] - x) })
        }
        awaitEachGesture {
            val down = awaitFirstDown()
            select(down.position.x)
            do {
                val event = awaitPointerEvent()
                event.changes.firstOrNull()?.let { change ->
                    if (change.pressed) select(change.position.x)
                }
            } while (event.changes.any { it.pressed })
        }
    }

// MARK: - Legenda

@Composable
private fun ChartLegend(items: List<Pair<String, Color>>) {
    val colors = FormaTheme.Colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        items.forEach { (title, color) ->
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(color)
                )
                Text(text = title, color = colors.secondaryText, style = FormaTypography.system(11f))
            }
        }
    }
}

// MARK: - Barras agrupadas (base dos gráficos 2 e 3)

private class BarSeg(val value: Double, val color: Color)

/** Uma "barra" do grupo: lista de segmentos empilhados (primeiro = base). */
private class BarGroup(val segments: List<BarSeg>) {
    val total: Double get() = segments.sumOf { it.value }
}

private fun DrawScope.drawGroupedBars(
    area: PlotArea,
    dayCount: Int,
    domainMax: Double,
    barsFor: (Int) -> List<BarGroup>,
    dimmed: (Int) -> Boolean
) {
    val band = area.width / dayCount
    val groupWidth = band * 0.72f
    val gap = 2.dp.toPx()
    val radius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
    for (d in 0 until dayCount) {
        val groups = barsFor(d)
        val barW = (groupWidth - gap * (groups.size - 1)) / groups.size
        val startX = area.left + band * d + (band - groupWidth) / 2f
        val alpha = if (dimmed(d)) 0.45f else 1f
        groups.forEachIndexed { gi, g ->
            val x = startX + gi * (barW + gap)
            var base = area.bottom
            g.segments.forEach { seg ->
                val h = (seg.value / domainMax).toFloat().coerceAtLeast(0f) * area.height
                if (h > 0.5f) {
                    drawRoundRect(
                        color = seg.color.copy(alpha = alpha),
                        topLeft = Offset(x, base - h),
                        size = Size(barW, h),
                        cornerRadius = radius
                    )
                }
                base -= h
            }
        }
    }
}

/** Seleção por banda (categoria) no eixo X: toque/arraste sem consumir o scroll vertical da tela. */
private fun Modifier.bandSelection(
    key: Any?,
    count: Int,
    yAxisWidthDp: Float,
    onSelect: (index: Int, isDown: Boolean) -> Unit
): Modifier = this.pointerInput(key) {
    fun indexOf(x: Float): Int {
        val left = yAxisWidthDp.dp.toPx()
        val right = size.width - 4.dp.toPx()
        val band = (right - left) / count
        return ((x - left) / band).toInt().coerceIn(0, count - 1)
    }
    awaitEachGesture {
        val down = awaitFirstDown()
        onSelect(indexOf(down.position.x), true)
        do {
            val event = awaitPointerEvent()
            event.changes.firstOrNull()?.let { change ->
                if (change.pressed) onSelect(indexOf(change.position.x), false)
            }
        } while (event.changes.any { it.pressed })
    }
}

// MARK: - 2. Gráfico Interativo de Balanço Calórico Semanal

@Composable
fun WeeklyCalorieChartCard(
    summaries: List<DayCalorieSummary>,
    dailyCalorieTarget: Double,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    var selectedDay by remember { mutableStateOf<String?>(null) }

    val weeklyNetBalance = summaries.sumOf { it.netBalance }
    val totalConsumed = summaries.sumOf { it.consumed }
    val totalBurned = summaries.sumOf { it.totalBurned }
    val selectedSummary = selectedDay?.let { sel -> summaries.firstOrNull { it.dayName == sel } }

    val consumedColor = colors.energyGreen
    val warning = colors.warning

    FormaCard(modifier = modifier.fillMaxWidth(), padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                    Text(
                        text = "Balanço Semanal de Calorias",
                        color = colors.primaryText,
                        style = FormaTypography.rounded(16f, FontWeight.Bold)
                    )
                    if (selectedSummary != null) {
                        val net = selectedSummary.netBalance
                        Text(
                            text = "${selectedSummary.dayName}: Entrou ${selectedSummary.consumed.toIntSafe()} • Saiu ${selectedSummary.totalBurned.toIntSafe()} kcal (${if (net >= 0) "+" else ""}${net.toIntSafe()})",
                            color = colors.brand,
                            style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold)
                        )
                    } else {
                        Text(
                            text = "Comparativo dos últimos 7 dias (Entrou vs Saiu)",
                            color = colors.secondaryText,
                            style = FormaTypography.caption
                        )
                    }
                }
                FormaBadge(
                    text = "${if (weeklyNetBalance >= 0) "+" else ""}${weeklyNetBalance.rounded()} kcal",
                    tint = if (weeklyNetBalance >= 0) colors.energyGreen else colors.warning
                )
            }

            val measurer = rememberTextMeasurer()
            val axisStyle = FormaTypography.system(9f).copy(color = colors.secondaryText)
            val dayStyle = FormaTypography.system(10f).copy(color = colors.secondaryText)
            val grid = colors.primary.copy(alpha = 0.06f)
            val brand = colors.brand

            val dataMax = max(
                summaries.maxOfOrNull { max(it.consumed, it.totalBurned) } ?: 0.0,
                if (dailyCalorieTarget > 0) dailyCalorieTarget else 0.0
            )
            val domainMax = niceCeil(if (dataMax <= 0.0) 100.0 else dataMax, 4)

            Column(modifier = Modifier.fillMaxWidth().height(190.dp)) {
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .bandSelection(summaries, summaries.size, 30f) { idx, isDown ->
                            val name = summaries.getOrNull(idx)?.dayName
                            selectedDay = if (isDown && selectedDay == name) null else name
                        }
                ) {
                    if (summaries.isEmpty()) return@Canvas
                    val area = plotArea(size.width, size.height, this, 30f, 18f)
                    fun yOf(v: Double) = area.bottom - (v / domainMax).toFloat() * area.height

                    niceTicks(0.0, domainMax, 4).forEach { v ->
                        val y = yOf(v)
                        drawLine(grid, Offset(area.left, y), Offset(area.right, y), strokeWidth = 0.5.dp.toPx())
                        label(measurer, "${v.toInt()}", axisStyle, area.left - 6.dp.toPx(), y - 6.dp.toPx(), Align.End)
                    }

                    if (dailyCalorieTarget > 0) {
                        val y = yOf(dailyCalorieTarget)
                        drawLine(
                            color = brand.copy(alpha = 0.55f),
                            start = Offset(area.left, y),
                            end = Offset(area.right, y),
                            strokeWidth = 1.1.dp.toPx(),
                            pathEffect = dashed(4f, 4f)
                        )
                    }

                    drawGroupedBars(
                        area = area,
                        dayCount = summaries.size,
                        domainMax = domainMax,
                        barsFor = { d ->
                            val s = summaries[d]
                            listOf(
                                BarGroup(listOf(BarSeg(s.consumed, consumedColor))),
                                BarGroup(listOf(BarSeg(s.tmb, RestingColor), BarSeg(s.activeBurned, warning)))
                            )
                        },
                        dimmed = { d -> selectedDay != null && selectedDay != summaries[d].dayName }
                    )

                    val band = area.width / summaries.size
                    summaries.forEachIndexed { i, s ->
                        label(measurer, s.dayName, dayStyle, area.left + band * (i + 0.5f), area.bottom + 4.dp.toPx(), Align.Center)
                    }
                }
                Box(modifier = Modifier.height(6.dp))
                ChartLegend(
                    listOf(
                        "Consumidas" to colors.energyGreen,
                        "Repouso" to RestingColor,
                        "Treino/Ativas" to colors.warning
                    )
                )
            }

            HorizontalDivider(color = colors.primary.copy(alpha = 0.06f))

            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Row(modifier = Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Entrou (7d):", color = colors.secondaryText, style = FormaTypography.caption)
                    Text(
                        text = "${totalConsumed.rounded()} kcal",
                        color = colors.energyGreen,
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold)
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Saiu (7d):", color = colors.secondaryText, style = FormaTypography.caption)
                    Text(
                        text = "${totalBurned.rounded()} kcal",
                        color = colors.warning,
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold)
                    )
                }
            }
        }
    }
}

// MARK: - 3. Gráfico Semanal de Consistência de Proteína & Hidratação

@Composable
fun WeeklyHabitsChartCard(
    summaries: List<DayCalorieSummary>,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors

    val avgProtein = if (summaries.isEmpty()) 0 else
        (summaries.sumOf { min(100.0, it.proteinPercent) } / summaries.size).rounded()
    val avgWater = if (summaries.isEmpty()) 0 else
        (summaries.sumOf { min(100.0, it.waterPercent) } / summaries.size).rounded()

    FormaCard(modifier = modifier.fillMaxWidth(), padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = "Meta de Proteína & Água",
                        color = colors.primaryText,
                        style = FormaTypography.rounded(16f, FontWeight.Bold)
                    )
                    Text(
                        text = "Percentual diário da meta nos últimos 7 dias",
                        color = colors.secondaryText,
                        style = FormaTypography.caption
                    )
                }
                FormaBadge(text = "P $avgProtein% • Água $avgWater%", tint = colors.water)
            }

            val measurer = rememberTextMeasurer()
            val axisStyle = FormaTypography.system(9f).copy(color = colors.secondaryText)
            val dayStyle = FormaTypography.system(10f).copy(color = colors.secondaryText)
            val grid = colors.primary.copy(alpha = 0.06f)
            val ruleColor = colors.secondaryText.copy(alpha = 0.4f)
            val proteinColor = colors.protein
            val waterColor = colors.water

            Column(modifier = Modifier.fillMaxWidth().height(155.dp)) {
                Canvas(modifier = Modifier.fillMaxWidth().weight(1f)) {
                    if (summaries.isEmpty()) return@Canvas
                    val area = plotArea(size.width, size.height, this, 30f, 18f)
                    val domainMax = 130.0
                    fun yOf(v: Double) = area.bottom - (v / domainMax).toFloat() * area.height

                    listOf(0.0, 50.0, 100.0).forEach { v ->
                        val y = yOf(v)
                        drawLine(grid, Offset(area.left, y), Offset(area.right, y), strokeWidth = 0.5.dp.toPx())
                        label(measurer, "${v.toInt()}%", axisStyle, area.left - 6.dp.toPx(), y - 6.dp.toPx(), Align.End)
                    }

                    val y100 = yOf(100.0)
                    drawLine(
                        color = ruleColor,
                        start = Offset(area.left, y100),
                        end = Offset(area.right, y100),
                        strokeWidth = 1.dp.toPx(),
                        pathEffect = dashed(4f, 4f)
                    )

                    drawGroupedBars(
                        area = area,
                        dayCount = summaries.size,
                        domainMax = domainMax,
                        barsFor = { d ->
                            val s = summaries[d]
                            listOf(
                                BarGroup(listOf(BarSeg(min(130.0, s.proteinPercent), proteinColor))),
                                BarGroup(listOf(BarSeg(min(130.0, s.waterPercent), waterColor)))
                            )
                        },
                        dimmed = { false }
                    )

                    val band = area.width / summaries.size
                    summaries.forEachIndexed { i, s ->
                        label(measurer, s.dayName, dayStyle, area.left + band * (i + 0.5f), area.bottom + 4.dp.toPx(), Align.Center)
                    }
                }
                Box(modifier = Modifier.height(6.dp))
                ChartLegend(listOf("Proteína" to colors.protein, "Água" to colors.water))
            }
        }
    }
}

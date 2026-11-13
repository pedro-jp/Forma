package com.forma.app.ui.components

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.sp
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// Porte de Forma/DesignSystem/FormaComponents.swift
// Nomes idênticos aos do iOS (prefixo Forma). Todos os valores numéricos (dp/sp) espelham os pontos/pt do iOS.

// MARK: - Helpers de texto

/**
 * Equivalente a `.lineLimit(1).minimumScaleFactor(x)`: reduz a fonte até [minScale] quando o texto não cabe.
 */
@Composable
fun FormaScaledText(
    text: String,
    style: TextStyle,
    color: Color,
    modifier: Modifier = Modifier,
    minScale: Float = 0.8f,
    textAlign: TextAlign? = null
) {
    var scale by remember(text, style) { mutableFloatStateOf(1f) }
    val baseSize = if (style.fontSize.isSpecified) style.fontSize else 14.sp
    Text(
        text = text,
        modifier = modifier,
        color = color,
        style = style.copy(fontSize = baseSize * scale),
        maxLines = 1,
        softWrap = false,
        overflow = TextOverflow.Clip,
        textAlign = textAlign,
        onTextLayout = { result ->
            if (result.hasVisualOverflow && scale > minScale) {
                scale = max(minScale, scale - 0.05f)
            }
        }
    )
}

/** Equivalente a `Button { } .buttonStyle(.plain)`: sem ripple, com leve escurecimento ao pressionar. */
fun Modifier.formaPlainClickable(
    enabled: Boolean = true,
    onClick: () -> Unit
): Modifier = composed {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    this
        .graphicsLayer { alpha = if (pressed && enabled) 0.6f else 1f }
        .clickable(
            interactionSource = interaction,
            indication = null,
            enabled = enabled,
            onClick = onClick
        )
}

// MARK: - Containers Padronizados

/** `FormaCard`: superfície de card com raio 20, sombra suave e borda hairline. */
@Composable
fun FormaCard(
    modifier: Modifier = Modifier,
    padding: Dp = FormaTheme.Spacing.cardPadding,
    cornerRadius: Dp = FormaTheme.Radius.card,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    val colors = FormaTheme.Colors
    Column(
        modifier = modifier
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = Color.Black.copy(alpha = 0.035f),
                spotColor = Color.Black.copy(alpha = 0.035f)
            )
            .clip(shape)
            .background(colors.cardSurface)
            .border(0.6.dp, colors.primary.copy(alpha = 0.06f), shape)
            .then(if (onClick != null) Modifier.formaPlainClickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

/** `FormaTintedCard`: card translúcido tingido (raio 14). */
@Composable
fun FormaTintedCard(
    tint: Color,
    modifier: Modifier = Modifier,
    padding: Dp = 16.dp,
    cornerRadius: Dp = FormaTheme.Radius.inner,
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val shape = RoundedCornerShape(cornerRadius)
    Column(
        modifier = modifier
            .clip(shape)
            .background(tint.copy(alpha = 0.08f))
            .border(0.8.dp, tint.copy(alpha = 0.20f), shape)
            .then(if (onClick != null) Modifier.formaPlainClickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content
    )
}

// MARK: - Cabeçalho de Tela

/** `FormaScreenHeader`: título 24 bold arredondado + subtítulo `.subheadline` secundário. */
@Composable
fun FormaScreenHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    val colors = FormaTheme.Colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            FormaScaledText(
                text = title,
                style = FormaTypography.rounded(24f, FontWeight.Bold),
                color = colors.primaryText,
                minScale = 0.8f
            )
            FormaScaledText(
                text = subtitle,
                style = FormaTypography.subheadline,
                color = colors.secondaryText,
                minScale = 0.85f
            )
        }
        Spacer(Modifier.width(8.dp))
        trailing()
    }
}

// MARK: - Badges e Pílulas

/** `FormaBadge`: cápsula 11 bold arredondada com fundo `tint.opacity(0.13)`. */
@Composable
fun FormaBadge(
    text: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = FormaTheme.Colors.brand
) {
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.13f))
            .padding(horizontal = 9.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        if (icon != null) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(10.dp)
            )
        }
        Text(
            text = text,
            color = tint,
            style = FormaTypography.rounded(11f, FontWeight.Bold),
            maxLines = 1,
            softWrap = false
        )
    }
}

// MARK: - Banners Semânticos (Erros, Avisos e Sucesso com Retry)

enum class FormaBannerStyle {
    Error, Warning, Info, Success;

    val color: Color
        @Composable get() = when (this) {
            Error -> FormaTheme.Colors.danger
            Warning -> FormaTheme.Colors.warning
            Info -> FormaTheme.Colors.aiAccent
            Success -> FormaTheme.Colors.success
        }

    /** exclamationmark.triangle.fill / bolt.badge.clock.fill / sparkles / checkmark.circle.fill */
    val icon: ImageVector
        get() = when (this) {
            Error -> sfSymbol("exclamationmark.triangle.fill")
            Warning -> sfSymbol("bolt.badge.clock.fill")
            Info -> sfSymbol("sparkles")
            Success -> sfSymbol("checkmark.circle.fill")
        }
}

/** `FormaBanner`: mensagem humanizada + botão opcional de retry. */
@Composable
fun FormaBanner(
    message: String,
    modifier: Modifier = Modifier,
    style: FormaBannerStyle = FormaBannerStyle.Error,
    retryTitle: String = "Tentar novamente",
    onRetry: (() -> Unit)? = null
) {
    val colors = FormaTheme.Colors
    val styleColor = style.color
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(styleColor.copy(alpha = 0.09f))
            .border(0.8.dp, styleColor.copy(alpha = 0.22f), shape)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Icon(
            imageVector = style.icon,
            contentDescription = null,
            tint = styleColor,
            modifier = Modifier.size(15.dp)
        )
        Text(
            text = formaHumanizeErrorMessage(message),
            color = colors.primaryText,
            style = FormaTypography.caption.copy(fontWeight = FontWeight.Medium),
            modifier = Modifier.weight(1f)
        )
        if (onRetry != null) {
            Text(
                text = retryTitle,
                color = Color.White,
                style = FormaTypography.rounded(11f, FontWeight.Bold),
                modifier = Modifier
                    .clip(CircleShape)
                    .background(styleColor)
                    .formaPlainClickable(onClick = onRetry)
                    .padding(horizontal = 10.dp, vertical = 6.dp)
            )
        }
    }
}

/**
 * Porte de `FormaAPIClient.humanizeErrorMessage(_:statusCode:)` (iOS) para exibição de mensagens de erro.
 */
fun formaHumanizeErrorMessage(raw: String, statusCode: Int = 500): String {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) return formaDefaultErrorMessage(statusCode)
    if (trimmed.contains("\"error\"") && trimmed.contains("{")) {
        if (trimmed.contains("503") || trimmed.contains("high demand", ignoreCase = true) ||
            trimmed.contains("UNAVAILABLE", ignoreCase = true)
        ) {
            return "A Inteligência Forma está com alta demanda no momento. Aguarde alguns segundos e tente novamente."
        }
        if (trimmed.contains("429") || trimmed.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
            return "Limite temporário de análises atingido. Tente novamente em instantes."
        }
        return formaDefaultErrorMessage(statusCode)
    }
    return trimmed
        .replace("API do Gemini", "Inteligência Forma")
        .replace("IA Gemini", "Inteligência Forma")
        .replace("Gemini", "Inteligência Forma")
        .replace("Inteligência Artificial", "Inteligência Forma")
        .replace("com IA", "com a Inteligência Forma")
}

private fun formaDefaultErrorMessage(statusCode: Int): String = when (statusCode) {
    400 -> "Não foi possível processar os dados enviados."
    401 -> "Sessão expirada ou credenciais inválidas."
    403 -> "Operação não permitida para esta conta."
    404 -> "Recurso não encontrado no servidor."
    429 -> "Muitas solicitações em sequência. Aguarde alguns segundos."
    502, 503, 504 -> "Serviço temporariamente ocupado. Tente novamente em alguns segundos."
    else -> "Ocorreu um erro temporário no servidor ($statusCode)."
}

// MARK: - Empty State Elegante

@Composable
fun FormaEmptyState(
    icon: ImageVector,
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    actionTitle: String? = null,
    onAction: (() -> Unit)? = null
) {
    val colors = FormaTheme.Colors
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(colors.brandSoft),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.brand,
                modifier = Modifier.size(17.dp)
            )
        }
        Text(
            text = title,
            color = colors.primaryText,
            style = FormaTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
            textAlign = TextAlign.Center
        )
        Text(
            text = subtitle,
            color = colors.secondaryText,
            style = FormaTypography.caption,
            textAlign = TextAlign.Center
        )
        if (actionTitle != null && onAction != null) {
            Text(
                text = actionTitle,
                color = colors.brand,
                style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold),
                modifier = Modifier
                    .padding(top = 2.dp)
                    .clip(CircleShape)
                    .background(colors.brandSoft)
                    .formaPlainClickable(onClick = onAction)
                    .padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }
    }
}

// MARK: - Anel Principal de Energia (Apple Fitness Style)

@Composable
fun FormaEnergyRing(
    current: Double,
    target: Double,
    color: Color,
    modifier: Modifier = Modifier,
    lineWidth: Dp = 13.dp,
    unitLabel: String = "kcal",
    isBulkingMode: Boolean = true
) {
    val colors = FormaTheme.Colors
    val normalized = if (target > 0) min(max(current / target, 0.0), 1.0).toFloat() else 0f
    val progress by animateFloatAsState(
        targetValue = normalized,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMediumLow),
        label = "energyRing"
    )
    val diff = target - current

    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokePx = lineWidth.toPx()
            val diameter = min(size.width, size.height)
            val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
            val arcSize = Size(diameter, diameter)
            drawArc(
                color = color.copy(alpha = 0.16f),
                startAngle = 0f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = strokePx)
            )
            if (progress > 0f) {
                rotate(degrees = -90f, pivot = center) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            colors = listOf(color.copy(alpha = 0.85f), color, color.copy(alpha = 0.95f)),
                            center = center
                        ),
                        startAngle = 0f,
                        sweepAngle = 360f * progress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = strokePx, cap = StrokeCap.Round)
                    )
                }
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = "${current.roundToInt()}",
                color = colors.primaryText,
                style = FormaTypography.rounded(34f, FontWeight.Bold),
                maxLines = 1
            )
            Text(
                text = "de ${target.roundToInt()} $unitLabel",
                color = colors.secondaryText,
                style = FormaTypography.system(11f, FontWeight.Medium),
                maxLines = 1
            )
            Box(modifier = Modifier.padding(top = 2.dp)) {
                if (isBulkingMode) {
                    if (diff > 0) {
                        Text(
                            text = "Faltam ${diff.roundToInt()} kcal",
                            color = colors.brand,
                            style = FormaTypography.rounded(11f, FontWeight.Bold)
                        )
                    } else {
                        Text(
                            text = "Superávit batido! \uD83D\uDD25",
                            color = colors.energyGreen,
                            style = FormaTypography.rounded(11f, FontWeight.Bold)
                        )
                    }
                } else {
                    if (diff > 0) {
                        Text(
                            text = "Restam ${diff.roundToInt()} kcal",
                            color = colors.energyGreen,
                            style = FormaTypography.rounded(11f, FontWeight.SemiBold)
                        )
                    } else {
                        Text(
                            text = "Meta atingida! ⚡️",
                            color = colors.energyGreen,
                            style = FormaTypography.rounded(11f, FontWeight.Bold)
                        )
                    }
                }
            }
        }
    }
}

// MARK: - Barra Horizontal e Linha Unificada de Macronutrientes

@Composable
fun FormaProgressBar(
    value: Double,
    total: Double,
    modifier: Modifier = Modifier,
    color: Color = FormaTheme.Colors.brand,
    height: Dp = 6.dp
) {
    val target = if (total > 0) min(max(value / total, 0.0), 1.0).toFloat() else 0f
    val ratio by animateFloatAsState(
        targetValue = target,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
        label = "progressBar"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(color.copy(alpha = 0.16f))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .fillMaxWidth(ratio)
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun FormaMacroProgressRow(
    proteinCurrent: Double,
    proteinTarget: Double,
    carbsCurrent: Double,
    carbsTarget: Double,
    fatCurrent: Double,
    fatTarget: Double,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        FormaSingleMacroBar(
            title = "Proteínas",
            current = proteinCurrent,
            target = proteinTarget,
            color = colors.protein,
            modifier = Modifier.weight(1f)
        )
        FormaSingleMacroBar(
            title = "Carboidratos",
            current = carbsCurrent,
            target = carbsTarget,
            color = colors.carbs,
            modifier = Modifier.weight(1f)
        )
        FormaSingleMacroBar(
            title = "Gorduras",
            current = fatCurrent,
            target = fatTarget,
            color = colors.fat,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
fun FormaSingleMacroBar(
    title: String,
    current: Double,
    target: Double,
    color: Color,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        FormaScaledText(
            text = title,
            style = FormaTypography.system(11f, FontWeight.Medium),
            color = colors.secondaryText,
            minScale = 0.8f
        )
        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = "${current.roundToInt()}",
                color = colors.primaryText,
                style = FormaTypography.rounded(17f, FontWeight.Bold),
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
            Text(
                text = "/${target.roundToInt()}g",
                color = colors.secondaryText,
                style = FormaTypography.system(10f, FontWeight.Medium),
                maxLines = 1,
                modifier = Modifier.alignByBaseline()
            )
        }
        FormaProgressBar(value = current, total = target, color = color, height = 5.dp)
    }
}

// MARK: - Card Unificado de Hidratação (Reutilizado em Início e Alimentação)

@Composable
fun FormaHydrationCard(
    consumedMl: Int,
    targetMl: Int,
    onAddWater: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val progressTarget = if (targetMl > 0) min(1.0, consumedMl.toDouble() / targetMl.toDouble()).toFloat() else 0f
    val progress by animateFloatAsState(
        targetValue = progressTarget,
        animationSpec = spring(dampingRatio = 0.85f, stiffness = Spring.StiffnessMedium),
        label = "hydration"
    )
    val remainingMl = max(0, targetMl - consumedMl)

    FormaCard(modifier = modifier.fillMaxWidth(), padding = 16.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(modifier = Modifier.size(46.dp), contentAlignment = Alignment.Center) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val strokePx = 5.dp.toPx()
                        val diameter = min(size.width, size.height)
                        val topLeft = Offset((size.width - diameter) / 2f, (size.height - diameter) / 2f)
                        val arcSize = Size(diameter, diameter)
                        drawArc(
                            color = colors.water.copy(alpha = 0.18f),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = strokePx)
                        )
                        if (progress > 0f) {
                            drawArc(
                                color = colors.water,
                                startAngle = -90f,
                                sweepAngle = 360f * progress,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = strokePx, cap = StrokeCap.Round)
                            )
                        }
                    }
                    Icon(
                        imageVector = sfSymbol("drop.fill"),
                        contentDescription = null,
                        tint = colors.water,
                        modifier = Modifier.size(17.dp)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(3.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "Hidratação",
                            color = colors.primaryText,
                            style = FormaTypography.rounded(16f, FontWeight.Bold),
                            maxLines = 1,
                            modifier = Modifier.weight(1f)
                        )
                        FormaBadge(text = "${(progress.coerceAtMost(1f) * 100f).roundToInt()}%", tint = colors.water)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "$consumedMl",
                            color = colors.primaryText,
                            style = FormaTypography.rounded(20f, FontWeight.Bold),
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline()
                        )
                        Text(
                            text = "/ $targetMl ml",
                            color = colors.secondaryText,
                            style = FormaTypography.subheadline.copy(fontWeight = FontWeight.Medium),
                            maxLines = 1,
                            modifier = Modifier.alignByBaseline()
                        )
                    }

                    Text(
                        text = if (remainingMl > 0) "Faltam $remainingMl ml para a meta diária" else "Meta diária concluída!",
                        color = if (remainingMl > 0) colors.secondaryText else colors.water,
                        style = FormaTypography.caption,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                WaterQuickButton(ml = 200, isPrimary = false, onClick = { onAddWater(200) }, modifier = Modifier.weight(1f))
                WaterQuickButton(ml = 250, isPrimary = true, onClick = { onAddWater(250) }, modifier = Modifier.weight(1f))
                WaterQuickButton(ml = 500, isPrimary = false, onClick = { onAddWater(500) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun WaterQuickButton(
    ml: Int,
    isPrimary: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val fg = if (isPrimary) Color.White else colors.water
    Row(
        modifier = modifier
            .clip(CircleShape)
            .background(if (isPrimary) colors.water else colors.water.copy(alpha = 0.12f))
            .formaPlainClickable(onClick = onClick)
            .padding(vertical = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = sfSymbol("plus"),
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(10.dp)
        )
        Text(
            text = "$ml ml",
            color = fg,
            style = FormaTypography.rounded(12f, FontWeight.Bold),
            maxLines = 1
        )
    }
}

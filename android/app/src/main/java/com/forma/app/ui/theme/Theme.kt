package com.forma.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Tokens semânticos de cor do FormaTheme (iOS: `FormaTheme.Colors`), já resolvidos para o
 * modo claro/escuro corrente.
 */
@Immutable
class FormaColors(
    val isDark: Boolean,
    // --- FormaTheme.Colors (iOS) ---
    val brand: Color,
    val brandSoft: Color,
    val energyGreen: Color,
    val cardSurface: Color,
    val primaryText: Color,
    val pageBackground: Color,
    val protein: Color,
    val carbs: Color,
    val fat: Color,
    val water: Color,
    val aiAccent: Color,
    val tacoCyan: Color,
    val success: Color,
    val warning: Color,
    val danger: Color,
    val aiGradient: Brush,
    val brandGradient: Brush,
    // --- Cores de sistema do iOS (UIColor / ShapeStyle) ---
    /** `.secondary` */
    val secondaryText: Color,
    /** `.tertiary` */
    val tertiaryText: Color,
    /** `.quaternary` */
    val quaternaryText: Color,
    /** `Color(.systemBackground)` */
    val systemBackground: Color,
    /** `Color(.secondarySystemBackground)` */
    val secondaryBackground: Color,
    /** `Color(.tertiarySystemBackground)` */
    val tertiaryBackground: Color,
    /** `Color(.secondarySystemGroupedBackground)` */
    val secondaryGroupedBackground: Color,
    /** `Color(.separator)` */
    val separator: Color,
    /** `Color(.systemFill)` */
    val fill: Color,
    /** `Color(.secondarySystemFill)` */
    val secondaryFill: Color,
    /** `Color(.tertiarySystemFill)` */
    val tertiaryFill: Color,
    /** `Color(.systemGray)` */
    val gray: Color,
    /** `Color.orange` */
    val orange: Color,
    /** `Color.red` */
    val red: Color,
    /** `Color.blue` */
    val blue: Color,
    /** `Color.yellow` */
    val yellow: Color,
    /** `Color.green` */
    val green: Color,
    /** `Color.pink` */
    val pink: Color,
    /** `Color.purple` */
    val purple: Color,
    /** `Color.cyan` */
    val cyan: Color,
    /** `Color.primary` (preto no claro / branco no escuro). */
    val primary: Color
)

private fun diagonalGradient(from: Color, to: Color): Brush =
    // topLeading -> bottomTrailing
    Brush.linearGradient(colors = listOf(from, to), start = Offset.Zero, end = Offset.Infinite)

private fun buildFormaColors(dark: Boolean): FormaColors {
    val orange = if (dark) IosOrangeDark else IosOrangeLight
    val cyan = if (dark) IosCyanDark else IosCyanLight
    val red = if (dark) IosRedDark else IosRedLight
    val fontNormal = if (dark) FormaFontNormalDark else FormaFontNormalLight
    return FormaColors(
        isDark = dark,
        brand = FormaAccentColor,
        brandSoft = FormaAccentMinor,
        energyGreen = FormaAccentGreen,
        cardSurface = if (dark) FormaCardBackgroundDark else FormaCardBackgroundLight,
        primaryText = fontNormal,
        pageBackground = if (dark) IosSystemGroupedBackgroundDark else IosSystemGroupedBackgroundLight,
        protein = FormaAccentGreen,
        carbs = FormaAccentColor,
        fat = orange,
        water = cyan,
        aiAccent = FormaAiAccent,
        tacoCyan = FormaTacoCyan,
        success = FormaAccentGreen,
        warning = orange,
        danger = red,
        aiGradient = diagonalGradient(FormaAiGradientStart, FormaAiGradientEnd),
        brandGradient = diagonalGradient(FormaAccentColor, FormaAccentGreen),
        secondaryText = if (dark) IosSecondaryLabelDark else IosSecondaryLabelLight,
        tertiaryText = if (dark) IosTertiaryLabelDark else IosTertiaryLabelLight,
        quaternaryText = if (dark) IosQuaternaryLabelDark else IosQuaternaryLabelLight,
        systemBackground = if (dark) IosSystemBackgroundDark else IosSystemBackgroundLight,
        secondaryBackground = if (dark) IosSecondaryBackgroundDark else IosSecondaryBackgroundLight,
        tertiaryBackground = if (dark) IosTertiaryBackgroundDark else IosTertiaryBackgroundLight,
        secondaryGroupedBackground = if (dark) IosSecondaryGroupedBackgroundDark else IosSecondaryGroupedBackgroundLight,
        separator = if (dark) IosSeparatorDark else IosSeparatorLight,
        fill = if (dark) IosFillDark else IosFillLight,
        secondaryFill = if (dark) IosSecondaryFillDark else IosSecondaryFillLight,
        tertiaryFill = if (dark) IosTertiaryFillDark else IosTertiaryFillLight,
        gray = if (dark) IosGrayDark else IosGrayLight,
        orange = orange,
        red = red,
        blue = if (dark) IosBlueDark else IosBlueLight,
        yellow = if (dark) IosYellowDark else IosYellowLight,
        green = if (dark) IosGreenDark else IosGreenLight,
        pink = if (dark) IosPinkDark else IosPinkLight,
        purple = if (dark) IosPurpleDark else IosPurpleLight,
        cyan = cyan,
        primary = fontNormal
    )
}

private val LightFormaColors = buildFormaColors(dark = false)
private val DarkFormaColors = buildFormaColors(dark = true)

val LocalFormaColors = staticCompositionLocalOf { DarkFormaColors }

/**
 * Espelho de `enum FormaTheme` do iOS.
 *
 * ```
 * FormaTheme.Colors.cardSurface       // adaptativo (claro/escuro)
 * FormaTheme.Radius.card              // 20.dp
 * FormaTheme.Spacing.screenHorizontal // 20.dp
 * ```
 */
object FormaTheme {
    /** Cores semânticas resolvidas para o modo atual (somente dentro de composição). */
    val Colors: FormaColors
        @Composable
        @ReadOnlyComposable
        get() = LocalFormaColors.current

    object Radius {
        val card: Dp = 20.dp
        val inner: Dp = 14.dp
        val badge: Dp = 10.dp
    }

    object Spacing {
        val screenHorizontal: Dp = 20.dp
        val sectionGap: Dp = 18.dp
        val cardPadding: Dp = 18.dp
    }
}

private fun formaColorScheme(c: FormaColors): ColorScheme {
    val base = if (c.isDark) darkColorScheme() else lightColorScheme()
    return base.copy(
        primary = c.brand,
        onPrimary = Color.White,
        primaryContainer = c.brandSoft,
        onPrimaryContainer = c.brand,
        secondary = c.energyGreen,
        onSecondary = Color.Black,
        secondaryContainer = c.brandSoft,
        onSecondaryContainer = c.brand,
        tertiary = c.aiAccent,
        onTertiary = Color.White,
        background = c.pageBackground,
        onBackground = c.primaryText,
        surface = c.cardSurface,
        onSurface = c.primaryText,
        surfaceVariant = c.secondaryBackground,
        onSurfaceVariant = c.secondaryText,
        surfaceTint = Color.Transparent,
        surfaceContainerLowest = c.systemBackground,
        surfaceContainerLow = c.cardSurface,
        surfaceContainer = c.cardSurface,
        surfaceContainerHigh = c.secondaryGroupedBackground,
        surfaceContainerHighest = c.tertiaryBackground,
        outline = c.separator,
        outlineVariant = c.separator,
        error = c.danger,
        onError = Color.White
    )
}

/**
 * Tema do app: segue o modo claro/escuro do sistema (como no iOS).
 */
@Composable
fun FormaTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colors = if (darkTheme) DarkFormaColors else LightFormaColors
    CompositionLocalProvider(LocalFormaColors provides colors) {
        MaterialTheme(
            colorScheme = formaColorScheme(colors),
            typography = FormaMaterialTypography,
            shapes = Shapes(
                extraSmall = RoundedCornerShape(FormaTheme.Radius.badge),
                small = RoundedCornerShape(12.dp),
                medium = RoundedCornerShape(FormaTheme.Radius.inner),
                large = RoundedCornerShape(FormaTheme.Radius.card),
                extraLarge = RoundedCornerShape(28.dp)
            ),
            content = content
        )
    }
}

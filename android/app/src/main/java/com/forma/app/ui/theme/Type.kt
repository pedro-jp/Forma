package com.forma.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp

/**
 * Tipografia equivalente aos estilos de texto do iOS (Dynamic Type "Large") e ao
 * `.font(.system(size:weight:design: .rounded))` usado no Design System.
 *
 * O Android não possui a fonte SF Rounded; [Rounded] centraliza a família para que possa ser
 * trocada num único lugar caso uma fonte arredondada seja embarcada.
 */
object FormaTypography {
    /** Família usada para `design: .rounded` no iOS. */
    val Rounded: FontFamily = FontFamily.SansSerif

    val largeTitle = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.Normal)
    val title = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Normal)
    val title2 = TextStyle(fontSize = 22.sp, lineHeight = 28.sp, fontWeight = FontWeight.Normal)
    val title3 = TextStyle(fontSize = 20.sp, lineHeight = 25.sp, fontWeight = FontWeight.Normal)
    val headline = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.SemiBold)
    val body = TextStyle(fontSize = 17.sp, lineHeight = 22.sp, fontWeight = FontWeight.Normal)
    val callout = TextStyle(fontSize = 16.sp, lineHeight = 21.sp, fontWeight = FontWeight.Normal)
    val subheadline = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Normal)
    val footnote = TextStyle(fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Normal)
    val caption = TextStyle(fontSize = 12.sp, lineHeight = 16.sp, fontWeight = FontWeight.Normal)
    val caption2 = TextStyle(fontSize = 11.sp, lineHeight = 13.sp, fontWeight = FontWeight.Normal)

    /** `.font(.system(size: X, weight: W))` */
    fun system(size: Float, weight: FontWeight = FontWeight.Normal): TextStyle =
        TextStyle(fontSize = size.sp, fontWeight = weight)

    /** `.font(.system(size: X, weight: W, design: .rounded))` */
    fun rounded(size: Float, weight: FontWeight = FontWeight.Normal): TextStyle =
        TextStyle(fontSize = size.sp, fontWeight = weight, fontFamily = Rounded)

    fun rounded(size: TextUnit, weight: FontWeight = FontWeight.Normal): TextStyle =
        TextStyle(fontSize = size, fontWeight = weight, fontFamily = Rounded)
}

/** Typography do Material3 mapeada para os estilos do iOS. */
internal val FormaMaterialTypography = Typography(
    displayLarge = FormaTypography.largeTitle,
    displayMedium = FormaTypography.title,
    displaySmall = FormaTypography.title2,
    headlineLarge = FormaTypography.title,
    headlineMedium = FormaTypography.title2,
    headlineSmall = FormaTypography.title3,
    titleLarge = FormaTypography.title3,
    titleMedium = FormaTypography.headline,
    titleSmall = FormaTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
    bodyLarge = FormaTypography.body,
    bodyMedium = FormaTypography.subheadline,
    bodySmall = FormaTypography.footnote,
    labelLarge = FormaTypography.callout.copy(fontWeight = FontWeight.Medium),
    labelMedium = FormaTypography.caption,
    labelSmall = FormaTypography.caption2
)

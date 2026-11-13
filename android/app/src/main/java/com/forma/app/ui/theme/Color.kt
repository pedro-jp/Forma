package com.forma.app.ui.theme

import androidx.compose.ui.graphics.Color

// ---------------------------------------------------------------------------
// Assets.xcassets (valores reais do iOS) — fonte da verdade
// ---------------------------------------------------------------------------

/** AccentColor.colorset — #54E1C3 (claro e escuro). */
val FormaAccentColor = Color(0xFF54E1C3)

/** AccentGreen.colorset — #D4F782 (claro e escuro). */
val FormaAccentGreen = Color(0xFFD4F782)

/** AccentMinor.colorset — #183C36 (claro e escuro). */
val FormaAccentMinor = Color(0xFF183C36)

/** CardBackground.colorset — claro #FEFFFF / escuro #11161A. */
val FormaCardBackgroundLight = Color(0xFFFEFFFF)
val FormaCardBackgroundDark = Color(0xFF11161A)

/** FontNormal.colorset — claro preto / escuro branco. */
val FormaFontNormalLight = Color(0xFF000000)
val FormaFontNormalDark = Color(0xFFFFFFFF)

// ---------------------------------------------------------------------------
// Cores de sistema do iOS (UIColor) usadas pelo app
// ---------------------------------------------------------------------------

/** Color(.systemGroupedBackground) */
val IosSystemGroupedBackgroundLight = Color(0xFFF2F2F7)
val IosSystemGroupedBackgroundDark = Color(0xFF000000)

/** Color(.systemBackground) */
val IosSystemBackgroundLight = Color(0xFFFFFFFF)
val IosSystemBackgroundDark = Color(0xFF000000)

/** Color(.secondarySystemGroupedBackground) */
val IosSecondaryGroupedBackgroundLight = Color(0xFFFFFFFF)
val IosSecondaryGroupedBackgroundDark = Color(0xFF1C1C1E)

/** Color(.secondarySystemBackground) */
val IosSecondaryBackgroundLight = Color(0xFFF2F2F7)
val IosSecondaryBackgroundDark = Color(0xFF1C1C1E)

/** Color(.tertiarySystemBackground) */
val IosTertiaryBackgroundLight = Color(0xFFFFFFFF)
val IosTertiaryBackgroundDark = Color(0xFF2C2C2E)

/** Color.secondary / .secondary (label secundário: 60% opaco). */
val IosSecondaryLabelLight = Color(0x993C3C43)
val IosSecondaryLabelDark = Color(0x99EBEBF5)

/** .tertiary (label terciário: 30% opaco). */
val IosTertiaryLabelLight = Color(0x4D3C3C43)
val IosTertiaryLabelDark = Color(0x4DEBEBF5)

/** .quaternary (label quaternário: 18% opaco). */
val IosQuaternaryLabelLight = Color(0x2E3C3C43)
val IosQuaternaryLabelDark = Color(0x2EEBEBF5)

/** Color(.separator) */
val IosSeparatorLight = Color(0x4A3C3C43)
val IosSeparatorDark = Color(0x99545458)

/** Color(.systemFill) / secondarySystemFill / tertiarySystemFill. */
val IosFillLight = Color(0x33787880)
val IosFillDark = Color(0x5C787880)
val IosSecondaryFillLight = Color(0x28787880)
val IosSecondaryFillDark = Color(0x52787880)
val IosTertiaryFillLight = Color(0x1E767680)
val IosTertiaryFillDark = Color(0x3D767680)

/** Color(.systemGray) */
val IosGrayLight = Color(0xFF8E8E93)
val IosGrayDark = Color(0xFF8E8E93)

/** Color.orange / .red / .cyan / .blue / .yellow / .green / .pink / .purple (adaptativos). */
val IosOrangeLight = Color(0xFFFF9500)
val IosOrangeDark = Color(0xFFFF9F0A)
val IosRedLight = Color(0xFFFF3B30)
val IosRedDark = Color(0xFFFF453A)
val IosCyanLight = Color(0xFF32ADE6)
val IosCyanDark = Color(0xFF64D2FF)
val IosBlueLight = Color(0xFF007AFF)
val IosBlueDark = Color(0xFF0A84FF)
val IosYellowLight = Color(0xFFFFCC00)
val IosYellowDark = Color(0xFFFFD60A)
val IosGreenLight = Color(0xFF34C759)
val IosGreenDark = Color(0xFF30D158)
val IosPinkLight = Color(0xFFFF2D55)
val IosPinkDark = Color(0xFFFF375F)
val IosPurpleLight = Color(0xFFAF52DE)
val IosPurpleDark = Color(0xFFBF5AF2)

// Cores fixas (não adaptativas) do FormaTheme.Colors
val FormaAiAccent = Color(red = 0.62f, green = 0.32f, blue = 0.96f)
val FormaTacoCyan = Color(red = 0.20f, green = 0.72f, blue = 0.96f)
val FormaAiGradientStart = Color(red = 0.56f, green = 0.27f, blue = 0.95f)
val FormaAiGradientEnd = Color(red = 0.72f, green = 0.36f, blue = 0.98f)

// ---------------------------------------------------------------------------
// LEGADO — constantes dark-only do tema anterior. Mantidas apenas para que as telas ainda
// não migradas continuem compilando. Novas telas devem usar FormaTheme.Colors (adaptativo).
// ---------------------------------------------------------------------------
val AccentColor = FormaAccentColor // iOS Mint Accent
val AccentGreen = FormaAccentGreen // iOS Lime Green
val AccentMinor = FormaAccentMinor // iOS Dark Forest Badge
val AccentOrange = Color(0xFFF97316)

val BackgroundDark = Color(0xFF030712)
val CardDark = Color(0xFF111827)
val CardBorder = Color(0x1AFFFFFF)

val TextPrimary = Color(0xFFF8FAFC)
val TextSecondary = Color(0xFF94A3B8)
val TextTertiary = Color(0xFF64748B)

// Aliases
val PrimaryGreen = AccentColor
val DarkCard = Color(0xFF111827)
val DarkSurface = Color(0xFF090D16)

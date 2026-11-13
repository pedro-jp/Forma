package com.forma.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography

// Botões do padrão visual Forma (cápsulas "plain" com tint, como nas views iOS).

/** Botão principal: texto branco sobre `tint` (padrão: brand), cápsula ou raio configurável. */
@Composable
fun FormaPrimaryButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = FormaTheme.Colors.brand,
    enabled: Boolean = true,
    fillWidth: Boolean = true,
    cornerRadius: Dp? = FormaTheme.Radius.inner,
    verticalPadding: Dp = 14.dp
) {
    val shape = if (cornerRadius == null) CircleShape else RoundedCornerShape(cornerRadius)
    Row(
        modifier = modifier
            .then(if (fillWidth) Modifier.fillMaxWidth() else Modifier)
            .alpha(if (enabled) 1f else 0.5f)
            .clip(shape)
            .background(tint)
            .formaPlainClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = verticalPadding),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
        }
        Text(
            text = title,
            color = Color.White,
            style = FormaTypography.rounded(15f, FontWeight.Bold),
            maxLines = 1
        )
    }
}

/** Pílula pequena tingida (ex.: "Nova Ficha", "Manual"): texto `tint` sobre `tint.opacity(0.13)`. */
@Composable
fun FormaPillButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    tint: Color = FormaTheme.Colors.brand,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(tint.copy(alpha = 0.13f))
            .formaPlainClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(11.dp))
        }
        Text(
            text = title,
            color = tint,
            style = FormaTypography.rounded(12f, FontWeight.Bold),
            maxLines = 1
        )
    }
}

/** Botão secundário: texto primário sobre a superfície de card (cápsula com sombra leve). */
@Composable
fun FormaSecondaryButton(
    title: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true
) {
    val colors = FormaTheme.Colors
    Row(
        modifier = modifier
            .alpha(if (enabled) 1f else 0.5f)
            .clip(CircleShape)
            .background(colors.cardSurface)
            .formaPlainClickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (icon != null) {
            Icon(icon, contentDescription = null, tint = colors.primaryText, modifier = Modifier.size(13.dp))
        }
        Text(
            text = title,
            color = colors.primaryText,
            style = FormaTypography.rounded(13f, FontWeight.SemiBold),
            maxLines = 1
        )
    }
}

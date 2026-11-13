package com.forma.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GppGood
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PanTool
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.forma.app.data.services.AIConsentTexts
import com.forma.app.data.services.AIDataConsentManager
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography

/**
 * Modal "Privacidade da IA" (porte de `AIDataConsentSheet` do iOS).
 *
 * Uso: `if (show) AIDataConsentSheet(onGranted = { show = false; run() }, onDeclined = { show = false })`.
 * "Autorizar e Continuar" grava o consentimento (AIDataConsentManager.setConsent(true)) e chama [onGranted];
 * "Agora Não", "Fechar" e o gesto de dispensar chamam [onDeclined].
 */
@Composable
fun AIDataConsentSheet(
    onGranted: () -> Unit,
    onDeclined: () -> Unit
) {
    val context = LocalContext.current
    val colors = FormaTheme.Colors

    FormaModalSheet(onDismissRequest = onDeclined) {
        FormaSheetTopBar(
            title = AIConsentTexts.NAV_TITLE,
            cancelTitle = null,
            confirmTitle = AIConsentTexts.CLOSE_BUTTON,
            onConfirm = onDeclined
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FormaTheme.Spacing.screenHorizontal, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Cabeçalho
            Row(
                modifier = Modifier.padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(50.dp)
                        .clip(CircleShape)
                        .background(colors.aiGradient),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Filled.PanTool, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
                }
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        AIConsentTexts.HEADER_TITLE,
                        color = colors.primaryText,
                        style = FormaTypography.rounded(20f, FontWeight.Bold)
                    )
                    Text(
                        AIConsentTexts.HEADER_SUBTITLE,
                        color = colors.secondaryText,
                        style = FormaTypography.caption
                    )
                }
            }

            FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(sfSymbol("sparkles"), null, tint = colors.aiAccent, modifier = Modifier.size(18.dp))
                        Text(AIConsentTexts.GEMINI_CARD_TITLE, color = colors.primaryText, style = FormaTypography.headline)
                    }
                    Text(
                        text = markdownBold(AIConsentTexts.GEMINI_BODY),
                        color = colors.secondaryText,
                        style = FormaTypography.subheadline
                    )
                }
            }

            FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Description, null, tint = colors.brand, modifier = Modifier.size(18.dp))
                        Text(AIConsentTexts.DATA_CARD_TITLE, color = colors.primaryText, style = FormaTypography.headline)
                    }
                    AIConsentTexts.DATA_BULLETS.forEach { ConsentBullet(it) }

                    HorizontalDivider(modifier = Modifier.padding(vertical = 2.dp), color = colors.separator)

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
                        Icon(
                            Icons.Filled.VerifiedUser, null, tint = colors.energyGreen,
                            modifier = Modifier.padding(top = 2.dp).size(13.dp)
                        )
                        Text(
                            AIConsentTexts.NEVER_SHARED_NOTE,
                            color = colors.primaryText,
                            style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Filled.Tune, null, tint = colors.energyGreen, modifier = Modifier.size(18.dp))
                        Text(AIConsentTexts.PURPOSE_CARD_TITLE, color = colors.primaryText, style = FormaTypography.headline)
                    }
                    Text(AIConsentTexts.PURPOSE_BODY, color = colors.secondaryText, style = FormaTypography.subheadline)
                    Row(
                        modifier = Modifier
                            .padding(top = 2.dp)
                            .formaPlainClickable { openUrl(context, AIConsentTexts.PRIVACY_POLICY_URL) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(Icons.Filled.OpenInNew, null, tint = colors.brand, modifier = Modifier.size(13.dp))
                        Text(
                            AIConsentTexts.PRIVACY_POLICY_LINK_LABEL,
                            color = colors.brand,
                            style = FormaTypography.caption.copy(fontWeight = FontWeight.SemiBold)
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.aiGradient)
                        .formaPlainClickable {
                            AIDataConsentManager.get(context).setConsent(true)
                            onGranted()
                        }
                        .padding(vertical = 15.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.GppGood, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(AIConsentTexts.AUTHORIZE_BUTTON, color = Color.White, style = FormaTypography.headline)
                }

                Text(
                    text = AIConsentTexts.DECLINE_BUTTON,
                    color = colors.secondaryText,
                    style = FormaTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .formaPlainClickable(onClick = onDeclined)
                        .padding(vertical = 10.dp),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun ConsentBullet(text: String) {
    val colors = FormaTheme.Colors
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.Top) {
        Box(
            modifier = Modifier
                .padding(top = 6.dp)
                .size(6.dp)
                .clip(CircleShape)
                .background(colors.brand)
        )
        Text(text, color = colors.secondaryText, style = FormaTypography.caption)
    }
}

/** Renderiza trechos entre `**` em negrito (Markdown mínimo usado nos textos legais). */
internal fun markdownBold(raw: String): AnnotatedString = buildAnnotatedString {
    val parts = raw.split("**")
    parts.forEachIndexed { index, part ->
        if (index % 2 == 1) withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(part) } else append(part)
    }
}

internal fun openUrl(context: android.content.Context, url: String) {
    runCatching {
        val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(url))
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(intent)
    }
}

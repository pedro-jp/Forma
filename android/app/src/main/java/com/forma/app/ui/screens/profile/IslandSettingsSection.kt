package com.forma.app.ui.screens.profile

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.island.DeviceCutoutDetector
import com.forma.app.ui.island.DynamicIslandOverlayService
import com.forma.app.ui.island.DynamicIslandState
import com.forma.app.ui.island.IslandPreset
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography

/**
 * Seção exclusiva do Android ("Ilha Dinâmica (Android)"): substitui a Live Activity / Dynamic Island do iOS por
 * um overlay global ajustável ao recorte (cutout) do aparelho. Mantém todos os controles originais
 * (ativar, detectar recorte, presets, calibração fina, testar toque e overlay global).
 */
@Composable
fun IslandSettingsSection(
    islandState: DynamicIslandState,
    modifier: Modifier = Modifier
) {
    val colors = FormaTheme.Colors
    val context = LocalContext.current

    val isIslandEnabled by islandState.isEnabled.collectAsState()
    val currentPreset by islandState.preset.collectAsState()
    val customX by islandState.customXOffset.collectAsState()
    val customY by islandState.customYOffset.collectAsState()
    var statusMessage by remember { mutableStateOf<String?>(null) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            text = "Ilha Dinâmica (Android)",
            color = colors.primaryText,
            style = FormaTypography.headline
        )

        FormaCard(modifier = Modifier.fillMaxWidth(), padding = 20.dp) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                // Switch Liga/Desliga
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text(
                            text = "Ativar Ilha Dinâmica",
                            color = colors.primaryText,
                            style = FormaTypography.subheadline.copy(fontWeight = FontWeight.Bold)
                        )
                        Text(
                            text = "Exibe timer de treinos e água no notch até mesmo fora do app",
                            color = colors.secondaryText,
                            style = FormaTypography.caption
                        )
                    }
                    Switch(
                        checked = isIslandEnabled,
                        onCheckedChange = { enabled ->
                            islandState.setEnabled(enabled)
                            if (enabled && DynamicIslandOverlayService.canDrawOverlays(context)) {
                                DynamicIslandOverlayService.start(context)
                            } else if (!enabled) {
                                DynamicIslandOverlayService.stop(context)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = colors.energyGreen
                        )
                    )
                }

                HorizontalDivider(color = colors.separator)

                // Detecção automática do recorte
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.brand)
                        .formaPlainClickable {
                            val detected = DeviceCutoutDetector.detectPreset()
                            islandState.setPreset(detected)
                            statusMessage = "Modelo detectado: ${Build.MANUFACTURER} ${Build.MODEL} -> Preset: ${detected.name}"
                        }
                        .padding(vertical = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PhoneAndroid, null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Detectar Recorte do Meu Celular",
                        color = Color.White,
                        style = FormaTypography.rounded(14f, FontWeight.Bold)
                    )
                }

                statusMessage?.let {
                    Text(text = it, color = colors.secondaryText, style = FormaTypography.caption)
                }

                Text(
                    text = "Modelo / Recorte do Celular:",
                    color = colors.primaryText,
                    style = FormaTypography.footnote.copy(fontWeight = FontWeight.Bold)
                )

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    IslandPreset.ALL_PRESETS.forEach { preset ->
                        val isSelected = preset.type == currentPreset.type
                        val shape = RoundedCornerShape(10.dp)
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(shape)
                                .background(if (isSelected) colors.brandSoft else colors.secondaryBackground)
                                .then(
                                    if (isSelected) Modifier.border(BorderStroke(1.dp, colors.brand), shape) else Modifier
                                )
                                .formaPlainClickable { islandState.setPreset(preset) }
                                .padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = { islandState.setPreset(preset) },
                                colors = RadioButtonDefaults.colors(selectedColor = colors.brand)
                            )
                            Column {
                                Text(
                                    text = preset.name,
                                    color = colors.primaryText,
                                    style = FormaTypography.footnote.copy(fontWeight = FontWeight.Bold)
                                )
                                Text(
                                    text = preset.type.description,
                                    color = colors.secondaryText,
                                    style = FormaTypography.caption2
                                )
                            }
                        }
                    }
                }

                HorizontalDivider(color = colors.separator)

                Text(
                    text = "Calibração Fina de Posição (Offsets):",
                    color = colors.primaryText,
                    style = FormaTypography.footnote.copy(fontWeight = FontWeight.Bold)
                )

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Deslocamento Vertical (Y)",
                            color = colors.secondaryText,
                            style = FormaTypography.caption,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$customY dp",
                            color = colors.brand,
                            style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    FormaSlider(
                        value = customY.toDouble(),
                        onValueChange = { islandState.setCustomOffsets(customX, it.toInt()) },
                        range = -30.0..80.0,
                        step = 1.0,
                        tint = colors.brand
                    )
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "Deslocamento Horizontal (X)",
                            color = colors.secondaryText,
                            style = FormaTypography.caption,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            text = "$customX dp",
                            color = colors.brand,
                            style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    FormaSlider(
                        value = customX.toDouble(),
                        onValueChange = { islandState.setCustomOffsets(it.toInt(), customY) },
                        range = -100.0..100.0,
                        step = 1.0,
                        tint = colors.brand
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Testar Toque",
                        color = colors.brand,
                        style = FormaTypography.rounded(12f, FontWeight.Bold),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .border(BorderStroke(1.dp, colors.brand), RoundedCornerShape(10.dp))
                            .formaPlainClickable { islandState.toggleExpanded() }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                    Text(
                        text = "Overlay Global",
                        color = colors.primaryText,
                        style = FormaTypography.rounded(12f, FontWeight.Bold),
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(10.dp))
                            .background(colors.secondaryFill)
                            .formaPlainClickable {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(context)) {
                                    context.startActivity(
                                        Intent(
                                            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                            Uri.parse("package:${context.packageName}")
                                        ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    )
                                } else {
                                    DynamicIslandOverlayService.start(context)
                                }
                            }
                            .padding(vertical = 10.dp),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }
}

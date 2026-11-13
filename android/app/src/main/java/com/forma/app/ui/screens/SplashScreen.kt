package com.forma.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.forma.app.R
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.launch
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay

/**
 * Porte de Forma/Views/Auth/SplashScreenView.swift:
 *  - logo: escala 0.7→1 + opacidade 0→1 (spring response 0.6 / damping 0.7)
 *  - brilho radial: easeIn 0.5s, atraso 0.2s
 *  - textos e indicador: easeIn 0.4s, atraso 0.35s
 *  - [onFinished] após 1.6s
 */
@Composable
fun SplashScreen(
    onFinished: () -> Unit
) {
    val colors = FormaTheme.Colors
    val logoScale = remember { Animatable(0.7f) }
    val logoOpacity = remember { Animatable(0f) }
    val glowOpacity = remember { Animatable(0f) }
    val textOpacity = remember { Animatable(0f) }
    val glowRadiusPx = with(LocalDensity.current) { 260.dp.toPx() }
    val currentOnFinished by rememberUpdatedState(onFinished)

    LaunchedEffect(Unit) {
        coroutineScope {
            val springSpec = spring<Float>(dampingRatio = 0.7f, stiffness = 110f)
            launch { logoScale.animateTo(1f, springSpec) }
            launch { logoOpacity.animateTo(1f, springSpec) }
            launch {
                delay(200)
                glowOpacity.animateTo(1f, tween(500, easing = FastOutLinearInEasing))
            }
            launch {
                delay(350)
                textOpacity.animateTo(1f, tween(400, easing = FastOutLinearInEasing))
            }
            delay(1600)
            currentOnFinished()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.systemBackground)
    ) {
        // Gradiente radial sutil (accent 0.18 -> transparente; raio 20..260)
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha(glowOpacity.value)
                .background(
                    Brush.radialGradient(
                        colorStops = arrayOf(
                            0.0f to colors.brand.copy(alpha = 0.18f),
                            0.077f to colors.brand.copy(alpha = 0.18f),
                            1.0f to colors.systemBackground.copy(alpha = 0f)
                        ),
                        radius = glowRadiusPx
                    )
                )
        )

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(Modifier.size(1.dp))

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(24.dp)
            ) {
                // Logo animada do Forma
                Box(
                    modifier = Modifier
                        .scale(logoScale.value)
                        .alpha(logoOpacity.value),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .size(130.dp)
                            .background(
                                Brush.radialGradient(
                                    colorStops = arrayOf(
                                        0.0f to colors.brand.copy(alpha = 0.12f),
                                        0.7f to colors.brand.copy(alpha = 0.12f),
                                        1.0f to colors.brand.copy(alpha = 0f)
                                    )
                                )
                            )
                    )
                    Image(
                        painter = painterResource(id = R.drawable.app_logo),
                        contentDescription = "Forma",
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .size(104.dp)
                            .shadow(
                                elevation = 20.dp,
                                shape = RoundedCornerShape(24.dp),
                                ambientColor = colors.brand.copy(alpha = 0.35f),
                                spotColor = colors.brand.copy(alpha = 0.35f)
                            )
                            .clip(RoundedCornerShape(24.dp))
                    )
                }

                // Tipografia e Slogan
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.alpha(textOpacity.value)
                ) {
                    Text(
                        text = "Forma",
                        color = colors.primaryText,
                        style = FormaTypography.rounded(38f, FontWeight.ExtraBold)
                    )
                    Text(
                        text = stringResource(R.string.app_tagline),
                        color = colors.secondaryText,
                        style = FormaTypography.rounded(15f, FontWeight.Medium)
                    )
                }
            }

            // Indicador minimalista de carregamento
            Row(
                modifier = Modifier
                    .padding(bottom = 32.dp)
                    .alpha(textOpacity.value),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CircularProgressIndicator(
                    color = colors.brand,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.splash_loading),
                    color = colors.tertiaryText,
                    style = FormaTypography.caption
                )
            }
        }
    }
}

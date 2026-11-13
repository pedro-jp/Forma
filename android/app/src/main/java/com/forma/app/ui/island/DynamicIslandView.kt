package com.forma.app.ui.island

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forma.app.FormaApplication

@Composable
fun DynamicIslandView(
    modifier: Modifier = Modifier,
    isInsideOverlayService: Boolean = false,
    onQuickAddWater: (() -> Unit)? = null
) {
    val context = LocalContext.current
    val islandState = remember { DynamicIslandState.getInstance(context) }

    val isEnabled by islandState.isEnabled.collectAsState()
    if (!isEnabled) return

    val preset by islandState.preset.collectAsState()
    val customX by islandState.customXOffset.collectAsState()
    val customY by islandState.customYOffset.collectAsState()
    val isExpanded by islandState.isExpanded.collectAsState()

    val isWorkoutActive by islandState.isWorkoutActive.collectAsState()
    val workoutTitle by islandState.workoutTitle.collectAsState()
    val workoutSeconds by islandState.workoutSeconds.collectAsState()
    val workoutCalories by islandState.workoutCalories.collectAsState()

    val waterCurrent by islandState.waterCurrent.collectAsState()
    val waterTarget by islandState.waterTarget.collectAsState()
    val caloriesRemaining by islandState.caloriesRemaining.collectAsState()

    val totalXOffset = preset.xOffsetDp + customX
    val totalYOffset = preset.yOffsetDp + customY

    val targetWidth = if (isExpanded) preset.expandedWidthDp.dp else preset.compactWidthDp.dp
    val targetHeight = if (isExpanded) preset.expandedHeightDp.dp else preset.compactHeightDp.dp
    val targetRadius = if (isExpanded) 28.dp else preset.cornerRadiusDp.dp

    val density = LocalDensity.current.density

    val outerModifier = if (isInsideOverlayService) {
        modifier.fillMaxSize()
    } else {
        modifier
            .fillMaxWidth()
            .padding(top = totalYOffset.dp)
    }

    Box(
        modifier = outerModifier,
        contentAlignment = Alignment.TopCenter
    ) {
        val innerBoxModifier = if (isInsideOverlayService) {
            Modifier.fillMaxSize()
        } else {
            Modifier
                .offset { IntOffset(x = (totalXOffset * density).toInt(), y = 0) }
                .width(targetWidth)
                .height(targetHeight)
        }

        Box(
            modifier = innerBoxModifier
                .animateContentSize(
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioLowBouncy,
                        stiffness = Spring.StiffnessMedium
                    )
                )
                .shadow(
                    elevation = if (isExpanded) 16.dp else 8.dp,
                    shape = RoundedCornerShape(targetRadius),
                    spotColor = Color(0x6622C55E)
                )
                .clip(RoundedCornerShape(targetRadius))
                .background(Color(0xFF090A0F))
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color(0x3322C55E),
                            Color(0x11FFFFFF)
                        )
                    ),
                    shape = RoundedCornerShape(targetRadius)
                )
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    islandState.toggleExpanded()
                }
                .padding(horizontal = 12.dp, vertical = 6.dp),
            contentAlignment = Alignment.Center
        ) {
            if (!isExpanded) {
                // --- MODO COMPACTO (Pílula em volta da câmera) ---
                Row(
                    modifier = Modifier.fillMaxSize(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    if (isWorkoutActive) {
                        // Ícone do Treino com pulso
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = "Treino",
                                tint = Color(0xFFF97316),
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Timer do treino
                        val mins = workoutSeconds / 60
                        val secs = workoutSeconds % 60
                        Text(
                            text = String.format("%02d:%02d", mins, secs),
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        // Status Nutrição Diária (Água e Calorias)
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Default.WaterDrop,
                                contentDescription = null,
                                tint = Color(0xFF38BDF8),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "${waterCurrent}ml",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            Icon(
                                imageVector = Icons.Default.LocalFireDepartment,
                                contentDescription = null,
                                tint = Color(0xFF22C55E),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = "$caloriesRemaining",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            } else {
                // --- MODO EXPANDIDO (Card Interativo) ---
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    // Header da Ilha Expandida
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier
                                    .size(10.dp)
                                    .clip(CircleShape)
                                    .background(if (isWorkoutActive) Color(0xFFF97316) else Color(0xFF22C55E))
                            )
                            Text(
                                text = if (isWorkoutActive) workoutTitle else "Forma Live Status",
                                color = Color.White,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        IconButton(
                            onClick = { islandState.collapse() },
                            modifier = Modifier.size(24.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "Fechar",
                                tint = Color(0xFF64748B),
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // Conteúdo dinâmico
                    if (isWorkoutActive) {
                        val mins = workoutSeconds / 60
                        val secs = workoutSeconds % 60
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Tempo de Treino", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                Text(
                                    text = String.format("%02d:%02d", mins, secs),
                                    color = Color.White,
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Gasto Estimado", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                Text(
                                    text = "${workoutCalories.toInt()} kcal",
                                    color = Color(0xFF22C55E),
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Botão finalizar treino na ilha
                        Button(
                            onClick = {
                                val repo = FormaApplication.instance.repository
                                repo.stopWorkout()
                                islandState.stopWorkout()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFDC2626)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            Text("Finalizar e Registrar Treino", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        // Resumo Nutrição e Ação Rápida de Água
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Água Hoje", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                Text(
                                    text = "$waterCurrent / ${waterTarget}ml",
                                    color = Color(0xFF38BDF8),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("Restam Consumir", color = Color(0xFF94A3B8), fontSize = 11.sp)
                                Text(
                                    text = "$caloriesRemaining kcal",
                                    color = Color(0xFF22C55E),
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Botão de beber água direto da Ilha
                        Button(
                            onClick = {
                                // Um toque = exatamente uma entrada de 250 ml. Quem hospeda a Ilha pode fornecer
                                // `onQuickAddWater` (que já chama addWater); só registramos aqui como fallback para
                                // não duplicar a entrada (bug antigo: +250 virava 500). Os números da Ilha são
                                // atualizados por `repository.onTodayDataChanged` (HealthGlue).
                                val quickAdd = onQuickAddWater
                                if (quickAdd != null) {
                                    quickAdd()
                                } else {
                                    FormaApplication.instance.repository.addWater(250)
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0284C7)),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(36.dp)
                        ) {
                            Icon(Icons.Default.WaterDrop, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("+250 ml de Água", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }
}

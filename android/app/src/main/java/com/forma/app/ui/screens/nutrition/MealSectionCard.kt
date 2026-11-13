package com.forma.app.ui.screens.nutrition

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Coffee
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.forma.app.data.model.FoodEntry
import com.forma.app.data.model.MealType
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.sfSymbol
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlin.math.roundToInt

/**
 * Ícone Material equivalente ao SF Symbol `MealType.icon` do iOS:
 * sun.horizon.fill / sun.max.fill / moon.stars.fill / cup.and.saucer.fill.
 */
val MealType.materialIcon: ImageVector
    get() = when (this) {
        MealType.BREAKFAST -> Icons.Filled.WbTwilight
        MealType.LUNCH -> Icons.Filled.LightMode
        MealType.DINNER -> Icons.Filled.NightsStay
        MealType.SNACKS -> Icons.Filled.Coffee
    }

/** Card de refeição diária (Café da Manhã, Almoço, Jantar, Lanches) na aba Alimentação (iOS: `MealSectionCard`). */
@Composable
fun MealSectionCard(
    mealType: MealType,
    entries: List<FoodEntry>,
    onAddTap: () -> Unit,
    onDeleteEntry: (FoodEntry) -> Unit,
    modifier: Modifier = Modifier,
    onCameraTap: (() -> Unit)? = null
) {
    val colors = FormaTheme.Colors
    val mealCalories = entries.sumOf { it.calories }

    FormaCard(modifier = modifier.fillMaxWidth(), padding = 16.dp, cornerRadius = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(colors.brandSoft),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = mealType.materialIcon,
                        contentDescription = null,
                        tint = colors.brand,
                        modifier = Modifier.size(16.dp)
                    )
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    Text(
                        text = mealType.rawValue,
                        color = colors.primaryText,
                        style = FormaTypography.system(16f, FontWeight.Bold),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entries.isNotEmpty()) {
                        Text(
                            text = "${entries.size} ${if (entries.size == 1) "item" else "itens"}",
                            color = colors.secondaryText,
                            style = FormaTypography.caption2
                        )
                    }
                }

                if (mealCalories > 0) {
                    Text(
                        text = "${mealCalories.roundToInt()} kcal",
                        color = colors.primaryText,
                        style = FormaTypography.rounded(14f, FontWeight.Bold),
                        maxLines = 1,
                        softWrap = false
                    )
                }

                if (onCameraTap != null) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(colors.brandSoft)
                            .formaPlainClickable(onClick = onCameraTap),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = sfSymbol("camera.fill"),
                            contentDescription = "Foto da refeição",
                            tint = colors.brand,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }

                Icon(
                    imageVector = Icons.Filled.AddCircle,
                    contentDescription = "Adicionar",
                    tint = colors.brand,
                    modifier = Modifier
                        .size(26.dp)
                        .formaPlainClickable(onClick = onAddTap)
                )
            }

            if (entries.isEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Nenhum item registrado ainda.",
                        color = colors.secondaryText,
                        style = FormaTypography.caption,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "Registrar",
                        color = colors.brand,
                        style = FormaTypography.caption.copy(fontWeight = FontWeight.Bold),
                        modifier = Modifier.formaPlainClickable(onClick = onAddTap)
                    )
                }
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    entries.forEachIndexed { index, entry ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(
                                    text = entry.name,
                                    color = colors.primaryText,
                                    style = FormaTypography.system(15f, FontWeight.Medium),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                                val unitLabel = if (entry.brand.lowercase().contains("ml")) "ml" else "g"
                                Text(
                                    text = "${entry.portionGrams.toInt()}$unitLabel • P ${entry.protein.toInt()}g  C ${entry.carbohydrates.toInt()}g  G ${entry.fat.toInt()}g",
                                    color = colors.secondaryText,
                                    style = FormaTypography.caption,
                                    maxLines = 1
                                )
                            }

                            Box(modifier = Modifier.width(6.dp))

                            Text(
                                text = "${entry.calories.roundToInt()} kcal",
                                color = colors.primaryText,
                                style = FormaTypography.rounded(14f, FontWeight.SemiBold),
                                maxLines = 1,
                                softWrap = false
                            )

                            Icon(
                                imageVector = sfSymbol("trash"),
                                contentDescription = "Excluir",
                                tint = colors.danger.copy(alpha = 0.65f),
                                modifier = Modifier
                                    .formaPlainClickable { onDeleteEntry(entry) }
                                    .padding(start = 6.dp, top = 6.dp, bottom = 6.dp, end = 0.dp)
                                    .size(14.dp)
                            )
                        }

                        if (index != entries.lastIndex) {
                            HorizontalDivider(thickness = 0.5.dp, color = colors.primary.copy(alpha = 0.05f))
                        }
                    }
                }
            }
        }
    }
}

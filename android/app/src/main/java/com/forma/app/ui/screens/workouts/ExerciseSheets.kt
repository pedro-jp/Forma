package com.forma.app.ui.screens.workouts

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Air
import androidx.compose.material.icons.filled.ArrowCircleDown
import androidx.compose.material.icons.filled.ArrowCircleUp
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forma.app.data.catalog.ExerciseCatalog
import com.forma.app.data.catalog.ExerciseCatalogItem
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaModalSheet
import com.forma.app.ui.components.FormaSheetTopBar
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.sfSymbol
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography

/**
 * Porte de `ExerciseDetailView` (iOS): GIF animado com controles, músculos, ênfase e passo a passo de execução.
 * Quando [onSelect] não é nulo exibe o botão "Vincular Este GIF ao Exercício".
 */
@Composable
fun ExerciseDetailSheet(
    item: ExerciseCatalogItem,
    onDismiss: () -> Unit,
    onSelect: (() -> Unit)? = null
) {
    val colors = FormaTheme.Colors
    FormaModalSheet(onDismissRequest = onDismiss) {
        FormaSheetTopBar(
            title = "Como Executar",
            cancelTitle = "Fechar",
            onCancel = onDismiss
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // GIF Animado com Anatomia e Músculo Iluminado
            Column(
                modifier = Modifier.fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                GifPlayer(
                    gifName = item.gifName,
                    cornerRadius = 18.dp,
                    showControls = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(16f / 9f)
                        .shadow(8.dp, RoundedCornerShape(18.dp), ambientColor = Color.Black.copy(alpha = 0.12f), spotColor = Color.Black.copy(alpha = 0.12f))
                )
                Text(
                    text = "Vídeo 3D de 10s em loop • Músculo alvo destacado em vermelho",
                    color = colors.secondaryText,
                    style = FormaTypography.system(11f)
                )
            }

            // Card Principal de Músculos & Ênfase
            FormaCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp, cornerRadius = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Row(verticalAlignment = Alignment.Top) {
                        Text(
                            text = item.name,
                            color = colors.primaryText,
                            style = FormaTypography.rounded(18f, FontWeight.Bold),
                            modifier = Modifier.weight(1f)
                        )
                        Spacer(Modifier.size(8.dp))
                        Text(
                            text = item.muscleGroup,
                            color = colors.brand,
                            style = FormaTypography.system(10f, FontWeight.Bold),
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(colors.brand.copy(alpha = 0.1f))
                                .padding(horizontal = 9.dp, vertical = 4.dp)
                        )
                    }

                    // Destaque de Ênfase Muscular (Sarado & Hipertrofiado)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(colors.energyGreen.copy(alpha = 0.08f))
                            .padding(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Filled.LocalFireDepartment,
                            contentDescription = null,
                            tint = colors.energyGreen,
                            modifier = Modifier.size(15.dp)
                        )
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text(
                                text = "ÊNFASE MUSCULAR ATIVA",
                                color = colors.secondaryText,
                                style = FormaTypography.system(9f, FontWeight.Bold)
                            )
                            Text(
                                text = item.emphasisMuscle,
                                color = colors.energyGreen,
                                style = FormaTypography.rounded(13f, FontWeight.Bold)
                            )
                        }
                    }

                    // Músculos Recrutados
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text(
                            text = "MÚSCULOS RECRUTADOS",
                            color = colors.secondaryText,
                            style = FormaTypography.system(10f, FontWeight.Bold)
                        )
                        Text(
                            text = item.targetMuscles,
                            color = colors.primaryText,
                            style = FormaTypography.system(12f, FontWeight.Medium)
                        )
                    }
                }
            }

            // Passo a Passo de Execução
            FormaCard(modifier = Modifier.fillMaxWidth(), padding = 16.dp, cornerRadius = 18.dp) {
                Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                    Text(
                        text = "COMO EXECUTAR CORRETAMENTE",
                        color = colors.secondaryText,
                        style = FormaTypography.system(11f, FontWeight.Bold)
                    )
                    InstructionRow(
                        title = "Postura & Posição Inicial",
                        description = item.setupInstruction,
                        icon = Icons.Filled.DirectionsWalk,
                        accent = colors.blue
                    )
                    InstructionRow(
                        title = "Descida Controlada",
                        description = item.eccentricInstruction,
                        icon = Icons.Filled.ArrowCircleDown,
                        accent = colors.orange
                    )
                    InstructionRow(
                        title = "Subida & Contração",
                        description = item.concentricInstruction,
                        icon = Icons.Filled.ArrowCircleUp,
                        accent = colors.energyGreen
                    )
                    InstructionRow(
                        title = "Respiração & Firmeza do Abdômen",
                        description = item.breathingInstruction,
                        icon = Icons.Filled.Air,
                        accent = if (colors.isDark) Color(0xFF40C8E0) else Color(0xFF30B0C7)
                    )
                    InstructionRow(
                        title = "Erro Comum a Evitar",
                        description = item.commonMistake,
                        icon = Icons.Filled.Warning,
                        accent = colors.red
                    )
                }
            }

            if (onSelect != null) {
                Row(
                    modifier = Modifier
                        .padding(top = 4.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(colors.brand)
                        .formaPlainClickable {
                            onSelect()
                            onDismiss()
                        }
                        .padding(vertical = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color.White, modifier = Modifier.size(18.dp))
                    Text(
                        text = "Vincular Este Vídeo ao Exercício",
                        color = Color.White,
                        style = FormaTypography.system(15f, FontWeight.Bold)
                    )
                }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}

@Composable
private fun InstructionRow(
    title: String,
    description: String,
    icon: ImageVector,
    accent: Color
) {
    val colors = FormaTheme.Colors
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Box(
            modifier = Modifier
                .size(30.dp)
                .clip(CircleShape)
                .background(accent.copy(alpha = 0.12f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = title,
                color = colors.primaryText,
                style = FormaTypography.rounded(14f, FontWeight.Bold)
            )
            Text(
                text = description,
                color = colors.secondaryText,
                style = FormaTypography.system(12f)
            )
        }
    }
}

/**
 * Porte de `ExerciseCatalogSheet` (iOS): catálogo de execuções com busca e filtro por grupo muscular.
 * Com [onSelectExercise] o toque no card seleciona o exercício e fecha; sem ele abre o [ExerciseDetailSheet].
 */
@Composable
fun ExerciseCatalogSheet(
    onDismiss: () -> Unit,
    onSelectExercise: ((ExerciseCatalogItem) -> Unit)? = null
) {
    val colors = FormaTheme.Colors
    var searchText by remember { mutableStateOf("") }
    var selectedGroup by remember { mutableStateOf("Todos") }
    var itemForDetail by remember { mutableStateOf<ExerciseCatalogItem?>(null) }

    val filteredItems = remember(searchText, selectedGroup) {
        ExerciseCatalog.search(query = searchText.trim(), group = selectedGroup)
    }

    FormaModalSheet(onDismissRequest = onDismiss) {
        FormaSheetTopBar(
            title = "Catálogo de Execuções",
            cancelTitle = "Fechar",
            onCancel = onDismiss
        )

        Column(modifier = Modifier.weight(1f).fillMaxWidth()) {
            // Barra de Busca
            Row(
                modifier = Modifier
                    .padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(colors.systemBackground)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(sfSymbol("magnifyingglass"), contentDescription = null, tint = colors.secondaryText, modifier = Modifier.size(18.dp))
                Box(modifier = Modifier.weight(1f)) {
                    if (searchText.isEmpty()) {
                        Text(
                            text = "Buscar exercício, músculo ou máquina...",
                            color = colors.tertiaryText,
                            style = TextStyle(fontSize = 14.sp),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    BasicTextField(
                        value = searchText,
                        onValueChange = { searchText = it },
                        singleLine = true,
                        textStyle = TextStyle(fontSize = 14.sp, color = colors.primaryText),
                        cursorBrush = SolidColor(colors.brand),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchText.isNotEmpty()) {
                    Icon(
                        sfSymbol("xmark.circle.fill"),
                        contentDescription = "Limpar busca",
                        tint = colors.gray,
                        modifier = Modifier
                            .size(18.dp)
                            .formaPlainClickable { searchText = "" }
                    )
                }
            }

            // Chips de Filtro por Grupo Muscular
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(ExerciseCatalog.muscleGroups.size) { index ->
                    val group = ExerciseCatalog.muscleGroups[index]
                    val selected = selectedGroup == group
                    Text(
                        text = group,
                        color = if (selected) Color.White else colors.primaryText,
                        style = FormaTypography.system(12f, FontWeight.Bold),
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(if (selected) colors.brand else colors.cardSurface)
                            .formaPlainClickable { selectedGroup = group }
                            .padding(horizontal = 14.dp, vertical = 8.dp)
                    )
                }
            }

            // Grid de Cards com GIFs e Músculo Iluminado
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                contentPadding = PaddingValues(16.dp),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(filteredItems, key = { it.id }) { item ->
                    ExerciseCard(item = item) {
                        if (onSelectExercise != null) {
                            onSelectExercise(item)
                            onDismiss()
                        } else {
                            itemForDetail = item
                        }
                    }
                }
            }
        }
    }

    itemForDetail?.let { item ->
        ExerciseDetailSheet(
            item = item,
            onDismiss = { itemForDetail = null },
            onSelect = onSelectExercise?.let { select ->
                {
                    select(item)
                    onDismiss()
                }
            }
        )
    }
}

@Composable
private fun ExerciseCard(item: ExerciseCatalogItem, onClick: () -> Unit) {
    val colors = FormaTheme.Colors
    val shape = RoundedCornerShape(16.dp)
    Column(
        modifier = Modifier
            .shadow(6.dp, shape, ambientColor = Color.Black.copy(alpha = 0.04f), spotColor = Color.Black.copy(alpha = 0.04f))
            .clip(shape)
            .background(colors.cardSurface)
            .formaPlainClickable(onClick = onClick),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // GIF Preview animado
        Box {
            GifPlayer(
                gifName = item.gifName,
                cornerRadius = 14.dp,
                showControls = false,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp)
            )
            Text(
                text = item.muscleGroup,
                color = Color.White,
                style = FormaTypography.system(9f, FontWeight.ExtraBold),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(6.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 3.dp)
            )
        }

        Column(
            modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = item.name,
                color = colors.primaryText,
                style = FormaTypography.rounded(13f, FontWeight.Bold),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(colors.energyGreen)
                )
                Text(
                    text = item.emphasisMuscle,
                    color = colors.energyGreen,
                    style = FormaTypography.system(10f, FontWeight.SemiBold),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

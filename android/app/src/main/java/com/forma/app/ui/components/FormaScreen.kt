package com.forma.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography

// Helpers de layout de tela (equivalentes a ScrollView + .background(pageBackground) + sheets do iOS).

/** `FormaTheme.Colors.pageBackground.ignoresSafeArea()` como container de tela cheia. */
@Composable
fun FormaPageBackground(
    modifier: Modifier = Modifier,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(FormaTheme.Colors.pageBackground),
        content = content
    )
}

/**
 * Esqueleto padrão de uma aba/tela do iOS:
 * `ScrollView { VStack(spacing: sectionGap) { ... }.padding(.horizontal, 20).padding(.top, 10) }`
 * com fundo `pageBackground`, sem indicadores de rolagem e toque-fora fecha o teclado.
 *
 * @param horizontalPadding padding horizontal do conteúdo (20 por padrão; use 0.dp quando as seções
 *  aplicam o padding individualmente, como em Treinos).
 * @param bottomPadding espaço final (ex.: 36.dp em Treinos).
 */
@Composable
fun FormaScrollScreen(
    modifier: Modifier = Modifier,
    horizontalPadding: Dp = FormaTheme.Spacing.screenHorizontal,
    topPadding: Dp = 10.dp,
    bottomPadding: Dp = 0.dp,
    spacing: Dp = FormaTheme.Spacing.sectionGap,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(FormaTheme.Colors.pageBackground)
            .dismissKeyboardOnTap()
            .verticalScroll(rememberScrollState())
            .padding(start = horizontalPadding, end = horizontalPadding, top = topPadding, bottom = bottomPadding),
        verticalArrangement = Arrangement.spacedBy(spacing),
        content = content
    )
}

/** Título grande estilo "navigation large title" do iOS (34 bold) com ações opcionais à direita. */
@Composable
fun FormaLargeTitleHeader(
    title: String,
    modifier: Modifier = Modifier,
    trailing: @Composable () -> Unit = {}
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            color = FormaTheme.Colors.primaryText,
            style = FormaTypography.largeTitle.copy(fontWeight = FontWeight.Bold),
            maxLines = 1,
            modifier = Modifier.weight(1f)
        )
        trailing()
    }
}

/** Cabeçalho de seção: título 18 bold arredondado + subtítulo `.caption` secundário + ação à direita. */
@Composable
fun FormaSectionHeader(
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    trailing: @Composable () -> Unit = {}
) {
    val colors = FormaTheme.Colors
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = title,
                color = colors.primaryText,
                style = FormaTypography.rounded(18f, FontWeight.Bold)
            )
            if (subtitle != null) {
                Text(text = subtitle, color = colors.secondaryText, style = FormaTypography.caption)
            }
        }
        trailing()
    }
}

/**
 * Sheet modal estilo iOS (`.sheet`): ocupa quase toda a altura, fundo `pageBackground`,
 * cantos superiores arredondados. O conteúdo recebe a barra de título via [FormaSheetTopBar].
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FormaModalSheet(
    onDismissRequest: () -> Unit,
    modifier: Modifier = Modifier,
    skipPartiallyExpanded: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = skipPartiallyExpanded)
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = state,
        containerColor = FormaTheme.Colors.pageBackground,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxHeight(0.94f)
                .fillMaxWidth()
                .imePadding(),
            content = content
        )
    }
}

/**
 * Barra superior de sheet (NavigationStack inline): "Cancelar" à esquerda, título central,
 * ação de confirmação à direita (ex.: "Salvar").
 */
@Composable
fun FormaSheetTopBar(
    title: String,
    modifier: Modifier = Modifier,
    cancelTitle: String? = "Cancelar",
    onCancel: (() -> Unit)? = null,
    confirmTitle: String? = null,
    onConfirm: (() -> Unit)? = null,
    confirmEnabled: Boolean = true
) {
    val colors = FormaTheme.Colors
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
            if (cancelTitle != null && onCancel != null) {
                Text(
                    text = cancelTitle,
                    color = colors.brand,
                    style = FormaTypography.body,
                    modifier = Modifier.formaPlainClickable(onClick = onCancel)
                )
            }
        }
        Text(
            text = title,
            color = colors.primaryText,
            style = FormaTypography.headline,
            maxLines = 1
        )
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
            if (confirmTitle != null && onConfirm != null) {
                Text(
                    text = confirmTitle,
                    color = if (confirmEnabled) colors.brand else colors.tertiaryText,
                    style = FormaTypography.headline,
                    modifier = Modifier.formaPlainClickable(enabled = confirmEnabled, onClick = onConfirm)
                )
            }
        }
    }
}

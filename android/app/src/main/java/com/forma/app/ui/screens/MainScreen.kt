package com.forma.app.ui.screens

import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.isImeVisible
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.saveable.rememberSaveableStateHolder
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.forma.app.R
import com.forma.app.data.repository.FormaRepository
import com.forma.app.ui.components.sfSymbol
import com.forma.app.ui.island.DynamicIslandState
import com.forma.app.ui.screens.workouts.GlobalActiveWorkoutSessionHost
import com.forma.app.ui.screens.workouts.WorkoutMiniPlayerOverlay
import com.forma.app.ui.screens.workouts.consumePendingWorkoutIfAny
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.launch

/**
 * Abas do app (Forma/Views/TabView.swift). `value` é o identificador usado pelo
 * `AppNavigationRouter.selectedTab` do iOS ("inicio", "alimentacao", "treinos", "evolucao", "perfil").
 */
enum class FormaTab(
    val value: String,
    @StringRes val titleRes: Int,
    val icon: ImageVector
) {
    Inicio("inicio", R.string.tab_home, sfSymbol("house.fill")),
    Alimentacao("alimentacao", R.string.tab_nutrition, sfSymbol("fork.knife")),
    Treinos("treinos", R.string.tab_workouts, sfSymbol("dumbbell.fill")),
    Evolucao("evolucao", R.string.tab_evolution, sfSymbol("chart.line.uptrend.xyaxis")),
    Perfil("perfil", R.string.tab_profile, sfSymbol("person.fill"));

    companion object {
        fun fromValue(value: String?): FormaTab? = entries.firstOrNull { it.value == value }
    }
}

/**
 * Shell de navegação (equivalente a `MainView` em TabView.swift).
 *
 * @param isAwaitingFirstSessionPull exibe "Carregando sua conta..." no lugar das abas.
 * @param shouldPresentInitialSetup apresenta [initialSetupContent] em tela cheia, não dispensável
 *  (equivalente a `fullScreenCover` + `interactiveDismissDisabled`). O conteúdo já recebe insets de
 *  status bar / navigation bar / teclado.
 * @param selectedTabValue aba selecionada controlada externamente (ex.: AppNavigationRouter); `null` usa estado local.
 * @param onSelectedTabValueChange chamado quando o usuário troca de aba.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun MainScreen(
    repository: FormaRepository,
    islandState: DynamicIslandState,
    onLogout: () -> Unit,
    isAwaitingFirstSessionPull: Boolean = false,
    shouldPresentInitialSetup: Boolean = false,
    initialSetupContent: @Composable () -> Unit = {},
    selectedTabValue: String? = null,
    onSelectedTabValueChange: (String) -> Unit = {}
) {
    val context = LocalContext.current
    val colors = FormaTheme.Colors
    val coroutineScope = rememberCoroutineScope()
    var localTabValue by rememberSaveable { mutableStateOf(FormaTab.Inicio.value) }
    val selectedTab = FormaTab.fromValue(selectedTabValue ?: localTabValue) ?: FormaTab.Inicio
    val selectTab: (FormaTab) -> Unit = { tab ->
        localTabValue = tab.value
        onSelectedTabValueChange(tab.value)
    }
    val stateHolder = rememberSaveableStateHolder()

    // Consome treino finalizado pela notificação ou PiP ao retornar ao app em qualquer aba
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        coroutineScope.launch { runCatching { consumePendingWorkoutIfAny(context, repository) } }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colors.pageBackground)
    ) {
        if (isAwaitingFirstSessionPull) {
            // Estado de carregamento da primeira sincronização de sessão
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.pageBackground),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp, Alignment.CenterVertically)
            ) {
                CircularProgressIndicator(
                    color = colors.brand,
                    strokeWidth = 3.dp,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = stringResource(R.string.loading_account),
                    color = colors.secondaryText,
                    style = FormaTypography.subheadline.copy(fontWeight = FontWeight.Medium)
                )
            }
        } else {
            val imeVisible = WindowInsets.isImeVisible
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .statusBarsPadding()
                ) {
                    stateHolder.SaveableStateProvider(selectedTab.value) {
                        when (selectedTab) {
                            FormaTab.Inicio -> HomeScreen(
                                repository = repository,
                                onNavigateToNutrition = { selectTab(FormaTab.Alimentacao) },
                                onNavigateToWorkouts = { selectTab(FormaTab.Treinos) },
                                onStartWorkout = { selectTab(FormaTab.Treinos) }
                            )
                            FormaTab.Alimentacao -> NutritionScreen(repository = repository)
                            FormaTab.Treinos -> WorkoutsScreen(repository = repository)
                            FormaTab.Evolucao -> EvolutionScreen(repository = repository)
                            FormaTab.Perfil -> ProfileAndIslandScreen(
                                repository = repository,
                                islandState = islandState,
                                onLogout = onLogout
                            )
                        }
                    }
                }

                if (!imeVisible) {
                    FormaTabBar(selected = selectedTab, onSelect = selectTab)
                }
            }
        }

        // fullScreenCover { InitialOnboardingModalView().interactiveDismissDisabled(true) }
        BackHandler(enabled = shouldPresentInitialSetup) { /* não dispensável */ }
        AnimatedVisibility(
            visible = shouldPresentInitialSetup,
            enter = slideInVertically(animationSpec = tween(350)) { it },
            exit = slideOutVertically(animationSpec = tween(300)) { it }
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colors.pageBackground)
                    .pointerInput(Unit) { /* bloqueia toques atrás do cover */ }
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .imePadding()
            ) {
                initialSetupContent()
            }
        }

        // MINI-PLAYER DE TREINO SOBREPOSTO (flutua acima da barra de abas em qualquer tela quando há treino ativo minimizado)
        if (!shouldPresentInitialSetup && !isAwaitingFirstSessionPull) {
            WorkoutMiniPlayerOverlay(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(bottom = 60.dp)
            )
            GlobalActiveWorkoutSessionHost()
        }
    }
}

/** Barra de abas no estilo iOS: 49dp de altura, ícone 24dp + rótulo 10sp, tint #54E1C3 no item selecionado. */
@Composable
private fun FormaTabBar(
    selected: FormaTab,
    onSelect: (FormaTab) -> Unit
) {
    val colors = FormaTheme.Colors
    val barColor = if (colors.isDark) Color(0xF01C1C1E) else Color(0xF0F9F9F9)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(barColor)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(0.5.dp)
                .background(colors.separator)
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .height(49.dp)
        ) {
            FormaTab.entries.forEach { tab ->
                val isSelected = tab == selected
                val tint = if (isSelected) colors.brand else colors.gray
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null,
                            role = Role.Tab,
                            onClick = { onSelect(tab) }
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
                ) {
                    Icon(
                        imageVector = tab.icon,
                        contentDescription = null,
                        tint = tint,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = stringResource(tab.titleRes),
                        color = tint,
                        style = FormaTypography.system(10f, FontWeight.Medium),
                        maxLines = 1
                    )
                }
            }
        }
    }
}

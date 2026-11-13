package com.forma.app

import android.Manifest
import android.app.PictureInPictureParams
import android.app.RemoteAction
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.forma.app.data.auth.GoogleAuthService
import com.forma.app.data.repository.FormaRepository
import com.forma.app.data.services.SharedWorkoutSessionManager
import com.forma.app.data.services.SharedWorkoutState
import com.forma.app.data.services.WorkoutNotificationManager
import com.forma.app.ui.island.DynamicIslandOverlayService
import com.forma.app.ui.island.DynamicIslandState
import com.forma.app.ui.screens.LoginScreen
import com.forma.app.ui.screens.MainScreen
import com.forma.app.ui.screens.SplashScreen
import com.forma.app.ui.screens.onboarding.InitialOnboardingModal
import com.forma.app.ui.screens.workouts.WorkoutPipView
import com.forma.app.ui.theme.FormaTheme
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

private enum class RootStage { Splash, Login, Main }

class MainActivity : ComponentActivity() {

    private var isInPipModeState by mutableStateOf(false)

    private val requestNotificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { _ ->
        // Handled
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as FormaApplication
        val repository = app.repository
        val islandState = DynamicIslandState.getInstance(this)
        val workoutSessionManager = SharedWorkoutSessionManager.get(this)

        // Encerra qualquer serviço legado de overlay de Ilha se estivesse rodando
        runCatching { DynamicIslandOverlayService.stop(this) }

        // Sincroniza os parâmetros nativos do Picture-in-Picture (PiP) sempre que a sessão de treino mudar
        lifecycleScope.launch {
            workoutSessionManager.session.collectLatest { session ->
                updateWorkoutPipParams(session)
                if ((session == null || session.isFinished) && isInPipModeState) {
                    // Se o treino foi finalizado pelo botão do PiP, sai do modo PiP
                    isInPipModeState = false
                }
            }
        }

        checkNotificationPermission()

        handleIntent(intent)

        setContent {
            FormaTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = FormaTheme.Colors.pageBackground,
                    contentColor = FormaTheme.Colors.primaryText
                ) {
                    if (isInPipModeState) {
                        WorkoutPipView()
                    } else {
                        FormaRoot(
                            repository = repository,
                            islandState = islandState,
                            onLoginSuccess = {
                                lifecycleScope.launch { runCatching { repository.sync.sync() } }
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        isInPipModeState = isInPictureInPictureMode
    }

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        // Em Android 8..11 (onde setAutoEnterEnabled ainda não existia), entra em PiP ao apertar Home com treino ativo
        if (Build.VERSION.SDK_INT in Build.VERSION_CODES.O until Build.VERSION_CODES.S) {
            val session = SharedWorkoutSessionManager.get(this).session.value
            if (session != null && !session.isFinished) {
                runCatching { enterPictureInPictureMode(buildWorkoutPipParams(session)) }
            }
        }
    }

    private fun updateWorkoutPipParams(session: SharedWorkoutState?) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        runCatching { setPictureInPictureParams(buildWorkoutPipParams(session)) }
    }

    private fun buildWorkoutPipParams(session: SharedWorkoutState?): PictureInPictureParams {
        val isActive = session != null && !session.isFinished
        val notifMgr = WorkoutNotificationManager.get(this)
        val actions = mutableListOf<RemoteAction>()

        if (isActive && session != null) {
            actions.add(
                RemoteAction(
                    Icon.createWithResource(this, android.R.drawable.ic_media_previous),
                    "-2kg",
                    "Reduzir carga da série (-2kg)",
                    notifMgr.actionIntent(WorkoutNotificationManager.ACTION_DECREASE_WEIGHT, 5)
                )
            )
            actions.add(
                RemoteAction(
                    Icon.createWithResource(this, android.R.drawable.ic_input_add),
                    "+2kg",
                    "Aumentar carga da série (+2kg)",
                    notifMgr.actionIntent(WorkoutNotificationManager.ACTION_INCREASE_WEIGHT, 6)
                )
            )
            if (session.isResting) {
                actions.add(
                    RemoteAction(
                        Icon.createWithResource(this, android.R.drawable.ic_media_next),
                        "Pular",
                        "Pular descanso",
                        notifMgr.actionIntent(WorkoutNotificationManager.ACTION_SKIP_REST, 1)
                    )
                )
            } else if (!session.isAllCompleted) {
                actions.add(
                    RemoteAction(
                        Icon.createWithResource(this, android.R.drawable.checkbox_on_background),
                        "Concluir",
                        "Concluir série atual",
                        notifMgr.actionIntent(WorkoutNotificationManager.ACTION_COMPLETE_SET, 3)
                    )
                )
            } else {
                actions.add(
                    RemoteAction(
                        Icon.createWithResource(this, android.R.drawable.ic_menu_close_clear_cancel),
                        "Finalizar",
                        "Finalizar e salvar treino",
                        notifMgr.actionIntent(WorkoutNotificationManager.ACTION_FINISH, 4)
                    )
                )
            }
        }

        val builder = PictureInPictureParams.Builder()
            .setAspectRatio(Rational(16, 9))
            .setActions(actions)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            builder.setAutoEnterEnabled(isActive)
            builder.setSeamlessResizeEnabled(true)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && session != null) {
            builder.setTitle(session.title)
            val curEx = session.currentExercise
            builder.setSubtitle(
                if (session.isResting) "Descanso em andamento" else (curEx?.name ?: "Treino Ativo")
            )
        }
        return builder.build()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        // Aba do Google fechada sem redirect => cancela o login silenciosamente
        lifecycleScope.launch { GoogleAuthService.get(this@MainActivity).onActivityResumed() }
    }

    /** Redirect do Google OAuth (reverse client id) e deep links `forma://` (onOpenURL do iOS). */
    private fun handleIntent(intent: Intent?) {
        if (intent == null) return
        if (GoogleAuthService.get(this).handleRedirect(intent)) return
        val data = intent.data ?: return
        if (data.scheme?.lowercase() == "forma") {
            (application as FormaApplication).router.handleDeepLink(data)
        }
    }

    private fun checkNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val hasPermission = ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED

            if (!hasPermission) {
                requestNotificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}

/**
 * Raiz equivalente a FormaApp.swift: Splash -> (autenticado ? MainView : LoginView), com transição de opacidade.
 * Quando o estado de autenticação vira falso (logout / conta excluída) volta automaticamente ao login.
 */
@Composable
private fun FormaRoot(
    repository: FormaRepository,
    islandState: DynamicIslandState,
    onLoginSuccess: () -> Unit
) {
    var isShowingSplash by rememberSaveable { mutableStateOf(true) }
    val isAuthenticated by repository.auth.isAuthenticated.collectAsState()

    // willEnterForegroundNotification: sincroniza o dia e treinos externos (somente autenticado)
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) repository.onAppForeground()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val stage = when {
        isShowingSplash -> RootStage.Splash
        isAuthenticated -> RootStage.Main
        else -> RootStage.Login
    }

    Crossfade(
        targetState = stage,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "rootStage"
    ) { currentStage ->
        when (currentStage) {
            RootStage.Splash -> SplashScreen(onFinished = { isShowingSplash = false })
            RootStage.Login -> LoginScreen(repository = repository, onLoginSuccess = onLoginSuccess)
            RootStage.Main -> MainGate(repository = repository, islandState = islandState)
        }
    }
}

/** Equivalente a `MainView` (TabView.swift): gates de pull inicial e configuração inicial + roteador de abas. */
@Composable
private fun MainGate(
    repository: FormaRepository,
    islandState: DynamicIslandState
) {
    val auth = repository.auth
    val isAuthenticated by auth.isAuthenticated.collectAsState()
    val currentUserId by auth.currentUserId.collectAsState()
    val profile by repository.userProfile.collectAsState()
    val hasCompletedInitialSessionPull by repository.sync.hasCompletedInitialSessionPull.collectAsState()
    val selectedTab by repository.router.selectedTab.collectAsState()

    val isAwaitingFirstSessionPull = isAuthenticated &&
        !(profile.onboardingCompleted && !profile.requiresInitialSetup) &&
        !hasCompletedInitialSessionPull
    val shouldPresentInitialSetup = isAuthenticated && !isAwaitingFirstSessionPull && profile.requiresInitialSetup

    // .task(id: authManager.currentUserId): pull forçado quando a conta ainda não foi carregada
    LaunchedEffect(currentUserId) {
        if (!auth.isAuthenticated.value) return@LaunchedEffect
        if (!repository.userProfile.value.onboardingCompleted && !repository.sync.hasCompletedInitialSessionPull.value) {
            runCatching { repository.sync.sync(forcePull = true) }
        }
    }

    MainScreen(
        repository = repository,
        islandState = islandState,
        onLogout = { if (repository.isLoggedIn()) auth.logout() },
        isAwaitingFirstSessionPull = isAwaitingFirstSessionPull,
        shouldPresentInitialSetup = shouldPresentInitialSetup,
        initialSetupContent = { InitialOnboardingModal(repository = repository) },
        selectedTabValue = selectedTab,
        onSelectedTabValueChange = { repository.router.setSelectedTab(it) }
    )
}

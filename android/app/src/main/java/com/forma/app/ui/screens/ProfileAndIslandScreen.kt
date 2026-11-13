package com.forma.app.ui.screens

import androidx.compose.runtime.Composable
import com.forma.app.data.repository.FormaRepository
import com.forma.app.ui.island.DynamicIslandState
import com.forma.app.ui.screens.profile.ProfileScreen

/**
 * Aba Perfil (iOS: `ProfileView`). Wrapper fino mantido por compatibilidade com o `MainScreen`;
 * a implementação vive em `ui/screens/profile/ProfileScreen.kt`. A seção de configurações da Ilha Dinâmica
 * (exclusiva do Android) é renderizada por último em `IslandSettingsSection`.
 */
@Composable
fun ProfileAndIslandScreen(
    repository: FormaRepository,
    islandState: DynamicIslandState,
    onLogout: () -> Unit
) {
    ProfileScreen(repository = repository, islandState = islandState, onLogout = onLogout)
}

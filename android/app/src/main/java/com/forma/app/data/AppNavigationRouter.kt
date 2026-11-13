package com.forma.app.data

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Roteador de navegação profunda para suporte a atalhos, Ilha Dinâmica e Ações Rápidas
 * (AppNavigationRouter.swift). Abas: "inicio", "alimentacao", "treinos", "evolucao", "perfil".
 */
class AppNavigationRouter private constructor() {

    private val _showAddFoodSheet = MutableStateFlow(false)
    val showAddFoodSheet: StateFlow<Boolean> = _showAddFoodSheet.asStateFlow()

    private val _showVoiceFoodSheet = MutableStateFlow(false)
    val showVoiceFoodSheet: StateFlow<Boolean> = _showVoiceFoodSheet.asStateFlow()

    private val _showAIFoodSheet = MutableStateFlow(false)
    val showAIFoodSheet: StateFlow<Boolean> = _showAIFoodSheet.asStateFlow()

    private val _showAIWorkoutSheet = MutableStateFlow(false)
    val showAIWorkoutSheet: StateFlow<Boolean> = _showAIWorkoutSheet.asStateFlow()

    private val _selectedTab = MutableStateFlow(TAB_INICIO)
    val selectedTab: StateFlow<String> = _selectedTab.asStateFlow()

    fun setSelectedTab(tab: String) { _selectedTab.value = tab }
    fun setShowAddFoodSheet(value: Boolean) { _showAddFoodSheet.value = value }
    fun setShowVoiceFoodSheet(value: Boolean) { _showVoiceFoodSheet.value = value }
    fun setShowAIFoodSheet(value: Boolean) { _showAIFoodSheet.value = value }
    fun setShowAIWorkoutSheet(value: Boolean) { _showAIWorkoutSheet.value = value }

    /**
     * Trata um deep link `forma://<host>` (mesmos hosts do iOS). Aceita também um host/path simples
     * (ex: "add-food") para uso por atalhos/intents internos.
     */
    fun handleDeepLink(uri: android.net.Uri) {
        if (uri.scheme?.lowercase() != "forma") return
        val host = (uri.host ?: uri.path.orEmpty().replace("/", "")).lowercase()
        handleAction(host)
    }

    fun handleAction(host: String) {
        when (host.lowercase()) {
            "add-food", "addfood", "voice-food", "voice", "falar", "ai-food", "gemini-food" -> {
                _selectedTab.value = TAB_INICIO
                _showAddFoodSheet.value = false
                _showAIFoodSheet.value = false
                _showVoiceFoodSheet.value = true
            }
            "ai-workout", "gemini-workout", "workout" -> {
                _selectedTab.value = TAB_TREINOS
                _showAIWorkoutSheet.value = true
            }
            "open", "inicio" -> _selectedTab.value = TAB_INICIO
            else -> _selectedTab.value = TAB_INICIO
        }
    }

    companion object {
        const val TAB_INICIO = "inicio"
        const val TAB_ALIMENTACAO = "alimentacao"
        const val TAB_TREINOS = "treinos"
        const val TAB_EVOLUCAO = "evolucao"
        const val TAB_PERFIL = "perfil"

        val shared: AppNavigationRouter by lazy { AppNavigationRouter() }
    }
}

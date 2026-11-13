package com.forma.app.data.health

import android.content.Context
import androidx.health.connect.client.HealthConnectClient
import com.forma.app.data.services.HealthConnectManager
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Porte de `HealthViewModel.swift`: métricas de saúde do dia usadas na aba Início (passos, calorias ativas,
 * calorias basais, distância e minutos de exercício) vindas do Health Connect.
 *
 * Diferença intencional: o iOS preenche valores fictícios quando o HealthKit não está disponível (simulador);
 * no Android os valores ficam em 0 quando o Health Connect não está disponível ou sem permissão.
 */
class HealthViewModel(private val manager: HealthConnectManager) {

    private val _steps = MutableStateFlow(0.0)
    val steps: StateFlow<Double> = _steps.asStateFlow()

    private val _activeCalories = MutableStateFlow(0.0)
    val activeCalories: StateFlow<Double> = _activeCalories.asStateFlow()

    private val _basalCalories = MutableStateFlow(0.0)
    val basalCalories: StateFlow<Double> = _basalCalories.asStateFlow()

    private val _distanceKm = MutableStateFlow(0.0)
    val distanceKm: StateFlow<Double> = _distanceKm.asStateFlow()

    private val _exerciseMinutes = MutableStateFlow(0.0)
    val exerciseMinutes: StateFlow<Double> = _exerciseMinutes.asStateFlow()

    private val _isAuthorized = MutableStateFlow(false)
    val isAuthorized: StateFlow<Boolean> = _isAuthorized.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    /** Gasto Energético Diário = Ativa + Basal estimada (quando houver). */
    val totalCaloriesBurned: Double
        get() = if (_basalCalories.value > 0) _activeCalories.value + _basalCalories.value else _activeCalories.value

    /** Health Connect instalado/ativo neste aparelho. */
    val isHealthDataAvailable: Boolean get() = manager.isAvailable

    /** Contrato de permissão do Health Connect (para `rememberLauncherForActivityResult`). */
    val permissionContract get() = manager.permissionContract

    /** Atualiza `isAuthorized` e, se houver permissão de leitura, recarrega as métricas (iOS: `loadHealthData`). */
    suspend fun loadHealthData() {
        _isLoading.value = true
        try {
            val authorized = manager.isAvailable && manager.hasAnyReadPermission()
            _isAuthorized.value = authorized
            if (authorized) fetchMetrics()
        } finally {
            _isLoading.value = false
        }
    }

    private suspend fun fetchMetrics() = coroutineScope {
        val steps = async { manager.fetchTodaySteps() }
        val calories = async { manager.fetchTodayCalories() }
        val basal = async { manager.fetchTodayBasalCalories() }
        val distance = async { manager.fetchTodayDistance() }
        val exercise = async { manager.fetchTodayExerciseTime() }
        _steps.value = steps.await()
        _activeCalories.value = calories.await()
        _basalCalories.value = basal.await()
        _distanceKm.value = distance.await()
        _exerciseMinutes.value = exercise.await()
    }

    /** Pede a permissão apenas se o Health Connect estiver disponível e ainda faltar alguma. */
    suspend fun needsPermissionRequest(): Boolean =
        manager.sdkStatus == HealthConnectClient.SDK_AVAILABLE && !manager.hasAllPermissions()
}

/**
 * Controle de "já explicamos/pedimos a permissão do Health Connect" + textos explicativos idênticos aos
 * `NSHealthShareUsageDescription` / `NSHealthUpdateUsageDescription` do iOS.
 *
 * O Android só mostra o diálogo do sistema de permissões do Health Connect algumas vezes; por isso o pedido
 * automático acontece uma única vez (como o iOS, que só mostra o diálogo na primeira autorização).
 */
object HealthPermissionPrompt {
    const val SHARE_USAGE_DESCRIPTION = "O Forma precisa acessar seus dados do Saúde para exibir seus passos e calorias."
    const val UPDATE_USAGE_DESCRIPTION = "O Forma precisa salvar seus treinos e calorias gastas no app Saúde."

    private const val PREFS = "forma_health_prefs"
    private const val KEY_PROMPTED = "health_permission_prompted"

    fun wasPrompted(context: Context): Boolean =
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY_PROMPTED, false)

    fun markPrompted(context: Context) {
        context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit().putBoolean(KEY_PROMPTED, true).apply()
    }
}

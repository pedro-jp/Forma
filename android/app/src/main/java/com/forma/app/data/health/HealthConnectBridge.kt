package com.forma.app.data.health

import android.app.Application
import android.content.Context
import com.forma.app.data.repository.ExternalWorkoutSample
import com.forma.app.data.repository.FormaRepository
import com.forma.app.data.repository.HealthBridge
import com.forma.app.data.services.HealthConnectManager
import com.forma.app.data.services.WorkoutNotificationManager
import com.forma.app.ui.island.DynamicIslandState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Implementação do [HealthBridge] do repositório sobre o [HealthConnectManager] (equivalente ao uso do
 * `HealthManager.shared` pelo `FormaDataStore` no iOS).
 *
 * Quando o Health Connect não está disponível (ou sem permissão) as leituras devolvem 0 / lista vazia e a
 * escrita vira no-op — o app segue funcionando só com os dados locais.
 *
 * A barreira de importação por conta (cutoff) e a deduplicação ficam em
 * `FormaRepository.syncExternalHealthWorkoutsInBackground`, que usa o mesmo arquivo de preferências do manager.
 */
class HealthConnectBridge(private val manager: HealthConnectManager) : HealthBridge {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun saveWorkout(
        title: String,
        workoutType: String,
        caloriesBurned: Double,
        durationMinutes: Int,
        date: Long
    ) {
        if (!manager.isAvailable) return
        scope.launch {
            runCatching { manager.saveWorkout(title, workoutType, caloriesBurned, durationMinutes, date) }
        }
    }

    override suspend fun fetchTodayCalories(): Double = manager.fetchTodayCalories()

    override suspend fun fetchTodaySteps(): Double = manager.fetchTodaySteps()

    override suspend fun fetchExternalWorkoutSamples(days: Int): List<ExternalWorkoutSample> =
        manager.fetchExternalWorkoutSamples(days).map {
            ExternalWorkoutSample(
                title = it.title,
                workoutType = it.workoutType,
                durationMinutes = it.durationMinutes,
                caloriesBurned = it.caloriesBurned,
                startMillis = it.startDate,
                sourceName = it.sourceName
            )
        }
}

/**
 * Água registrada fora do app em primeiro plano (ex.: ação da notificação/overlay) aguardando para virar
 * `WaterEntry` (equivalente ao `WidgetDataManager.addPendingWater/drainPendingWater` do iOS).
 */
object PendingWaterStore {
    private const val PREFS = "forma_pending_water"
    private const val KEY = "pending_ml"
    private val lock = Any()

    fun add(context: Context, milliliters: Int) {
        if (milliliters <= 0) return
        synchronized(lock) {
            val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            prefs.edit().putInt(KEY, prefs.getInt(KEY, 0) + milliliters).apply()
        }
    }

    /** Devolve e zera o acumulado. */
    fun drain(context: Context): Int = synchronized(lock) {
        val prefs = context.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val pending = prefs.getInt(KEY, 0)
        if (pending > 0) prefs.edit().putInt(KEY, 0).apply()
        pending
    }
}

/**
 * Cola entre o repositório e os serviços de plataforma. Chame [install] uma vez em `FormaApplication.onCreate`:
 *  - `repository.healthBridge` → Health Connect;
 *  - `repository.drainPendingWater` → [PendingWaterStore];
 *  - `repository.onTodayDataChanged` → Ilha Dinâmica (água/kcal restantes) + lembretes diários de
 *    hidratação/refeições (o que o `syncTodayData` do iOS faz com widgets/Live Activity/notificações).
 */
object HealthGlue {

    fun install(app: Application, repository: FormaRepository) {
        val manager = HealthConnectManager.get(app)
        repository.healthBridge = HealthConnectBridge(manager)
        repository.drainPendingWater = { PendingWaterStore.drain(app) }

        val island = DynamicIslandState.getInstance(app)
        val notifications = WorkoutNotificationManager.get(app).also { it.setup() }
        repository.onTodayDataChanged = { snapshot ->
            island.updateNutritionState(
                water = snapshot.waterConsumed,
                targetWater = snapshot.waterTarget,
                remainingCalories = (snapshot.caloriesTarget - snapshot.caloriesConsumed).roundToInt().coerceAtLeast(0)
            )
            if (repository.isAuthenticated.value) {
                runCatching {
                    notifications.scheduleDailyHydrationAndMealReminders(
                        waterConsumedMl = snapshot.waterConsumed,
                        waterTargetMl = snapshot.waterTarget,
                        hasLoggedBreakfast = snapshot.hasLoggedBreakfast,
                        hasLoggedLunch = snapshot.hasLoggedLunch,
                        hasLoggedDinner = snapshot.hasLoggedDinner
                    )
                }
            }
        }
    }
}

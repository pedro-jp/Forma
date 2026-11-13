package com.forma.app.data.sync

import android.content.Context
import com.forma.app.data.auth.AuthManager
import com.forma.app.data.local.DatabaseHelper
import com.forma.app.data.model.*
import com.forma.app.data.remote.BackendApiClient
import com.forma.app.data.remote.FormaApiException
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.net.URLEncoder
import java.util.Locale

/**
 * Equivalente ao BackendSyncManager do iOS: sincronização Remote-First (backend primeiro -> cache local depois)
 * com isolamento multi-usuário estrito.
 */
class BackendSyncManager(
    context: Context,
    private val api: BackendApiClient,
    private val auth: AuthManager,
    private val db: DatabaseHelper,
    private val activityLog: ActivityLogManager
) {
    companion object {
        const val ACTIVE_LOCAL_USER_ID_KEY = "forma_active_local_user_id"
        private const val LAST_SYNC_KEY = "forma_last_sync_timestamp"
        private const val REQUIRES_INITIAL_PULL_KEY = "forma_requires_initial_pull"
        private const val DELETED_FOOD_IDS_KEY = "forma_deleted_food_ids"
        private const val DELETED_WORKOUT_IDS_KEY = "forma_deleted_workout_ids"
        private const val DELETED_WATER_IDS_KEY = "forma_deleted_water_ids"
        private const val DELETED_WEIGHT_IDS_KEY = "forma_deleted_weight_ids"
        private const val DELETED_ROUTINE_IDS_KEY = "forma_deleted_routine_ids"
        private const val RESET_ACK_PREFIX = "forma_reset_ack_"
    }

    private val prefs = context.getSharedPreferences("forma_sync_state", Context.MODE_PRIVATE)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val syncMutex = Mutex()
    private var debouncedSyncJob: Job? = null

    /** Ligado pelo FormaRepository. */
    lateinit var gateway: LocalDataGateway

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    private val _lastSyncDate = MutableStateFlow<Long?>(prefs.getLong(LAST_SYNC_KEY, 0L).takeIf { it > 0L })
    /** Epoch millis da última sincronização bem-sucedida (null se nunca). */
    val lastSyncDate: StateFlow<Long?> = _lastSyncDate.asStateFlow()

    private val _lastSyncError = MutableStateFlow<String?>(null)
    val lastSyncError: StateFlow<String?> = _lastSyncError.asStateFlow()
    /** Alias para erro de sincronização observável pela UI. */
    val errorMessage: StateFlow<String?> get() = lastSyncError

    private val _hasCompletedInitialSessionPull = MutableStateFlow(false)
    val hasCompletedInitialSessionPull: StateFlow<Boolean> = _hasCompletedInitialSessionPull.asStateFlow()

    fun setHasCompletedInitialSessionPull(value: Boolean) {
        _hasCompletedInitialSessionPull.value = value
    }

    fun activeLocalUserId(): String? = prefs.getString(ACTIVE_LOCAL_USER_ID_KEY, null)
    fun setActiveLocalUserId(userId: String?) {
        prefs.edit().apply { if (userId == null) remove(ACTIVE_LOCAL_USER_ID_KEY) else putString(ACTIVE_LOCAL_USER_ID_KEY, userId) }.apply()
    }

    /** Remove o vínculo de usuário local e o timestamp de última sincronização (logout). */
    fun clearLocalSessionState() {
        prefs.edit().remove(ACTIVE_LOCAL_USER_ID_KEY).remove(LAST_SYNC_KEY).apply()
        _lastSyncDate.value = null
    }

    fun cancelPendingSyncs() {
        debouncedSyncJob?.cancel()
        debouncedSyncJob = null
    }

    fun markRequiresInitialPull(value: Boolean) {
        prefs.edit().putBoolean(REQUIRES_INITIAL_PULL_KEY, value).apply()
    }

    fun requiresInitialPull(): Boolean = prefs.getBoolean(REQUIRES_INITIAL_PULL_KEY, false)

    // ------------------------------------------------------------------------------------------
    // Exclusões pendentes (tombstones de segurança)
    // ------------------------------------------------------------------------------------------
    fun recordDeletedFoodIds(ids: List<String>) = appendDeletedIds(ids, DELETED_FOOD_IDS_KEY)
    fun recordDeletedWorkoutIds(ids: List<String>) = appendDeletedIds(ids, DELETED_WORKOUT_IDS_KEY)
    fun recordDeletedWaterIds(ids: List<String>) = appendDeletedIds(ids, DELETED_WATER_IDS_KEY)
    fun recordDeletedWeightIds(ids: List<String>) = appendDeletedIds(ids, DELETED_WEIGHT_IDS_KEY)
    fun recordDeletedRoutineIds(ids: List<String>) = appendDeletedIds(ids, DELETED_ROUTINE_IDS_KEY)

    fun pendingDeletedFoodIds(): Set<String> = loadDeletedIds(DELETED_FOOD_IDS_KEY)
    fun pendingDeletedWorkoutIds(): Set<String> = loadDeletedIds(DELETED_WORKOUT_IDS_KEY)

    fun clearAllPendingDeletions() {
        prefs.edit().apply {
            listOf(
                DELETED_FOOD_IDS_KEY, DELETED_WORKOUT_IDS_KEY, DELETED_WATER_IDS_KEY,
                DELETED_WEIGHT_IDS_KEY, DELETED_ROUTINE_IDS_KEY
            ).forEach { remove(it) }
        }.apply()
    }

    private fun appendDeletedIds(ids: List<String>, key: String) {
        if (ids.isEmpty()) return
        val current = loadDeletedIds(key).toMutableSet()
        ids.forEach { current.add(it.lowercase(Locale.ROOT)) }
        prefs.edit().putStringSet(key, current).apply()
    }

    private fun loadDeletedIds(key: String): Set<String> =
        (prefs.getStringSet(key, emptySet()) ?: emptySet()).map { it.lowercase(Locale.ROOT) }.toSet()

    // ------------------------------------------------------------------------------------------
    // Operações atômicas Remote-First (grava no backend primeiro)
    // ------------------------------------------------------------------------------------------
    private val isAuthenticated: Boolean get() = auth.isAuthenticated.value

    private suspend fun send(path: String, method: String, body: Any? = null, timeout: Double = 20.0) {
        api.requestData(path = path, method = method, jsonBody = body, authenticated = true, timeoutSeconds = timeout)
    }

    suspend fun pushProfileRemoteFirst(payload: RemoteProfilePayload) {
        if (!isAuthenticated) return
        send("/api/sync/profile", "PUT", payload.toMap())
        markSyncedNow()
    }

    suspend fun pushFoodsRemoteFirst(entries: List<FoodEntry>) {
        if (!isAuthenticated || entries.isEmpty()) return
        val foods = entries.map { f ->
            mapOf(
                "id" to f.id.uppercase(Locale.ROOT),
                "name" to f.name,
                "brand" to f.brand,
                "mealType" to f.mealType.rawValue,
                "portionGrams" to f.portionGrams,
                "calories" to f.calories,
                "protein" to f.protein,
                "carbohydrates" to f.carbohydrates,
                "fat" to f.fat,
                "consumedAt" to FormaDates.isoFractional(f.consumedAt)
            )
        }
        val logs = entries.map { f ->
            mapOf(
                "id" to f.id.uppercase(Locale.ROOT),
                "category" to "meal",
                "title" to "${f.mealType.rawValue}: ${f.name}",
                "isSnack" to (f.mealType == MealType.SNACKS),
                "caloriesChange" to f.calories,
                "proteinGrams" to f.protein,
                "carbsGrams" to f.carbohydrates,
                "fatGrams" to f.fat,
                "timestamp" to FormaDates.isoFractional(f.consumedAt)
            )
        }
        send("/api/sync/foods", "POST", mapOf("foods" to foods, "activityLogs" to logs))
        markSyncedNow()
    }

    suspend fun deleteFoodsRemoteFirst(ids: List<String>) {
        if (!isAuthenticated || ids.isEmpty()) return
        if (ids.size == 1) {
            send("/api/sync/foods/${ids.first().uppercase(Locale.ROOT)}", "DELETE")
        } else {
            send("/api/sync/foods/delete-batch", "POST", mapOf("ids" to ids.map { it.uppercase(Locale.ROOT) }))
        }
        markSyncedNow()
    }

    suspend fun pushWorkoutRemoteFirst(entry: WorkoutEntry, updatedRoutine: WorkoutRoutine? = null) {
        if (!isAuthenticated) return
        val workout = mapOf(
            "id" to entry.id.uppercase(Locale.ROOT),
            "title" to entry.title,
            "workoutType" to entry.workoutType,
            "durationMinutes" to entry.durationMinutes,
            "caloriesBurned" to entry.caloriesBurned,
            "notes" to entry.notes,
            "workoutDate" to FormaDates.isoFractional(entry.workoutDate)
        )
        val log = linkedMapOf<String, Any>(
            "id" to entry.id.uppercase(Locale.ROOT),
            "category" to "workout",
            "title" to "${entry.workoutType}: ${entry.title}",
            "caloriesChange" to -Math.abs(entry.caloriesBurned),
            "timestamp" to FormaDates.isoFractional(entry.workoutDate)
        )
        if (entry.notes.isNotEmpty()) log["details"] = entry.notes
        val body = linkedMapOf<String, Any>("workout" to workout, "activityLog" to log)
        updatedRoutine?.let { body["updatedRoutine"] = serializeRoutine(it) }
        send("/api/sync/workouts", "POST", body)
        markSyncedNow()
    }

    suspend fun deleteWorkoutRemoteFirst(id: String) {
        if (!isAuthenticated) return
        send("/api/sync/workouts/${id.uppercase(Locale.ROOT)}", "DELETE")
        markSyncedNow()
    }

    suspend fun pushRoutineRemoteFirst(routine: WorkoutRoutine) {
        if (!isAuthenticated) return
        send("/api/sync/routines", "POST", serializeRoutine(routine))
        markSyncedNow()
    }

    suspend fun deleteRoutineRemoteFirst(id: String) {
        if (!isAuthenticated) return
        send("/api/sync/routines/${id.uppercase(Locale.ROOT)}", "DELETE")
        markSyncedNow()
    }

    suspend fun pushWaterRemoteFirst(entry: WaterEntry) {
        if (!isAuthenticated) return
        val water = mapOf(
            "id" to entry.id.uppercase(Locale.ROOT),
            "milliliters" to entry.milliliters,
            "entryDate" to FormaDates.isoFractional(entry.entryDate)
        )
        val log = mapOf(
            "id" to entry.id.uppercase(Locale.ROOT),
            "category" to "water",
            "title" to "Hidratação (+${entry.milliliters} ml)",
            "caloriesChange" to 0.0,
            "waterMilliliters" to entry.milliliters,
            "timestamp" to FormaDates.isoFractional(entry.entryDate)
        )
        send("/api/sync/waters", "POST", mapOf("water" to water, "activityLog" to log))
        markSyncedNow()
    }

    suspend fun deleteWaterRemoteFirst(id: String) {
        if (!isAuthenticated) return
        send("/api/sync/waters/${id.uppercase(Locale.ROOT)}", "DELETE")
        markSyncedNow()
    }

    suspend fun pushWeightRemoteFirst(
        id: String,
        weight: Double,
        date: Long,
        notes: String,
        profilePayload: RemoteProfilePayload?
    ) {
        if (!isAuthenticated) return
        val weightDict = mapOf(
            "id" to id.uppercase(Locale.ROOT),
            "weight" to weight,
            "entryDate" to FormaDates.isoFractional(date)
        )
        val log = linkedMapOf<String, Any>(
            "id" to id.uppercase(Locale.ROOT),
            "category" to "weight",
            "title" to String.format(Locale.US, "Pesagem (%.1f kg)", weight),
            "caloriesChange" to 0.0,
            "weightKg" to weight,
            "timestamp" to FormaDates.isoFractional(date)
        )
        if (notes.isNotEmpty()) log["details"] = notes
        val body = linkedMapOf<String, Any>("weight" to weightDict, "activityLog" to log)
        profilePayload?.let { body["profileUpdate"] = it.toMap() }
        send("/api/sync/weights", "POST", body)
        markSyncedNow()
    }

    suspend fun deleteWeightRemoteFirst(id: String, updatedProfile: RemoteProfilePayload? = null) {
        if (!isAuthenticated) return
        send("/api/sync/weights/${id.uppercase(Locale.ROOT)}", "DELETE")
        if (updatedProfile != null) pushProfileRemoteFirst(updatedProfile) else markSyncedNow()
    }

    suspend fun pushAnalysisRemoteFirst(analysis: EvolutionAIAnalysis) {
        if (!isAuthenticated) return
        val gson = com.google.gson.Gson()
        val aDict = linkedMapOf<String, Any>(
            "id" to analysis.id.uppercase(Locale.ROOT),
            "statusSummary" to analysis.statusSummary,
            "calorieDiagnostic" to analysis.calorieDiagnostic,
            "macroDiagnostic" to analysis.macroDiagnostic,
            "hydrationDiagnostic" to analysis.hydrationDiagnostic,
            "workoutSuggestionsJson" to gson.toJson(analysis.workoutSuggestions),
            "nutritionSuggestionsJson" to gson.toJson(analysis.nutritionSuggestions),
            "weeklyActionPlan" to analysis.weeklyActionPlan,
            "createdAt" to FormaDates.isoFractional(analysis.generatedAt)
        )
        analysis.suggestedTargets?.let { aDict["suggestedTargetsJson"] = it.toJson() }
        analysis.recommendedRoutine?.let { aDict["recommendedRoutineJson"] = it.toJson() }
        send("/api/sync/analyses", "POST", aDict)
        markSyncedNow()
    }

    private fun serializeRoutine(r: WorkoutRoutine): Map<String, Any> {
        val dict = linkedMapOf<String, Any>(
            "id" to r.id.uppercase(Locale.ROOT),
            "title" to r.title,
            "workoutType" to r.workoutType,
            "targetMuscles" to r.targetMuscles,
            "hypertrophyFocus" to r.hypertrophyFocus,
            "estimatedDurationMinutes" to r.estimatedDurationMinutes,
            "estimatedCalories" to r.estimatedCalories,
            "recommendedRestSeconds" to r.recommendedRestSeconds,
            "isFavorite" to r.isFavorite,
            "exercisesJson" to r.exercisesJson,
            "notes" to r.notes,
            "createdAt" to FormaDates.isoFractional(r.createdAt)
        )
        r.lastTrainedAt?.let { dict["lastTrainedAt"] = FormaDates.isoFractional(it) }
        return dict
    }

    private fun markSyncedNow() {
        val now = System.currentTimeMillis()
        _lastSyncDate.value = now
        _lastSyncError.value = null
        prefs.edit().putLong(LAST_SYNC_KEY, now).apply()
    }

    // ------------------------------------------------------------------------------------------
    // Pull inicial limpo no login / restauração de sessão
    // ------------------------------------------------------------------------------------------
    suspend fun pullInitialStateForAuthenticatedUser() {
        val userId = auth.currentUserId.value
        if (auth.jwtToken == null || userId == null) {
            _hasCompletedInitialSessionPull.value = true
            return
        }
        _isSyncing.value = true
        try {
            pullRemoteData(userId, replaceLocalState = true)
            markRequiresInitialPull(false)
            gateway.syncTodayData(skipCloudSyncTrigger = true)
            markSyncedNow()
        } finally {
            _isSyncing.value = false
            _hasCompletedInitialSessionPull.value = true
        }
    }

    /** Sincroniza alterações do perfil/metas com pequeno debounce (ex: sliders na aba Perfil). */
    fun triggerAutoSync() {
        if (!isAuthenticated) return
        debouncedSyncJob?.cancel()
        debouncedSyncJob = scope.launch {
            delay(800)
            if (!isActive) return@launch
            val payload = gateway.profilePayloadForAutoSync() ?: return@launch
            try {
                pushProfileRemoteFirst(payload)
            } catch (_: Exception) {
            }
        }
    }

    /** Sincronização de sessão (pull do banco -> atualiza cache local se necessário). */
    suspend fun sync(forcePull: Boolean = false) {
        if (!isAuthenticated) return
        val userId = auth.currentUserId.value ?: return
        if (auth.jwtToken == null) return

        syncMutex.withLock {
            if (activeLocalUserId() != userId) {
                // Conta diferente detectada: limpa cache local totalmente antes de baixar do servidor
                clearAllPendingDeletions()
                gateway.resetLocalDataForUserSwitch(auth.currentUserName.value, auth.currentUserEmail.value)
                setActiveLocalUserId(userId)
                markRequiresInitialPull(true)
            }

            val needsInitialPull = requiresInitialPull()
            if (!forcePull && !needsInitialPull && _hasCompletedInitialSessionPull.value) {
                val last = _lastSyncDate.value
                if (last != null && System.currentTimeMillis() - last < 45_000L) {
                    // Evita recarregar repetidamente quando o cache local já está sincronizado
                    return
                }
            }

            _isSyncing.value = true
            _lastSyncError.value = null
            try {
                pullRemoteData(userId, replaceLocalState = true)
                markRequiresInitialPull(false)
                gateway.syncTodayData(skipCloudSyncTrigger = true)
                markSyncedNow()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _lastSyncError.value = e.message
            } finally {
                _isSyncing.value = false
                _hasCompletedInitialSessionPull.value = true
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Pull (backend -> cache local)
    // ------------------------------------------------------------------------------------------
    private suspend fun pullRemoteData(userId: String, replaceLocalState: Boolean) {
        val encodedUserId = URLEncoder.encode(userId, "UTF-8").replace("+", "%20")
        val text = try {
            api.requestData(
                path = "/api/sync/pull?userId=$encodedUserId", method = "GET",
                authenticated = true, timeoutSeconds = 25.0
            )
        } catch (_: FormaApiException.AccountDeleted) {
            return
        }

        val json: JsonObject = runCatching {
            val el = JsonParser.parseString(text)
            if (el.isJsonObject) el.asJsonObject else null
        }.getOrNull() ?: return

        val resetAckKey = "$RESET_ACK_PREFIX$userId"
        val localAck = prefs.getLong(resetAckKey, 0L)
        val forceReset = json.get("forceResetEpochMs")?.takeIf { it.isJsonPrimitive }?.let { runCatching { it.asLong }.getOrNull() }
        if (forceReset != null && forceReset > localAck) {
            clearAllPendingDeletions()
            gateway.resetLocalDataForUserSwitch(auth.currentUserName.value, auth.currentUserEmail.value)
            prefs.edit().putLong(resetAckKey, forceReset).apply()
        }

        withContext(Dispatchers.IO) {
            RemoteSnapshotApplier(db, auth, activityLog).apply(json, replaceLocalState)
        }
        gateway.onLocalDataChanged()
    }
}

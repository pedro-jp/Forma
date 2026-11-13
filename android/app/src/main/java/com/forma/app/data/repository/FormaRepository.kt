package com.forma.app.data.repository

import android.content.Context
import com.forma.app.data.AppNavigationRouter
import com.forma.app.data.auth.AuthManager
import com.forma.app.data.local.DatabaseHelper
import com.forma.app.data.model.*
import com.forma.app.data.remote.BackendApiClient
import com.forma.app.data.sync.ActivityLogManager
import com.forma.app.data.sync.BackendSyncManager
import com.forma.app.data.sync.LocalDataGateway
import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.*

data class DailySummary(
    val totalCalories: Int = 0,
    val totalBurnedCalories: Int = 0,
    val totalWaterMl: Int = 0,
    val totalProtein: Int = 0,
    val totalCarbs: Int = 0,
    val totalFat: Int = 0
)

data class AuthUser(
    val id: String,
    val email: String,
    val name: String
)

/** Intervalo [start, endExclusive) em epoch millis. */
data class TimeRange(val start: Long, val endExclusive: Long)

/** Treino externo (Apple Saúde no iOS / Health Connect no Android). */
data class ExternalWorkoutSample(
    val title: String,
    val workoutType: String,
    val durationMinutes: Int,
    val caloriesBurned: Double,
    val startMillis: Long,
    val sourceName: String
)

/**
 * Ganchos equivalentes ao HealthManager do iOS (HealthKit). O padrão é "no-op"; o agente de Health Connect
 * atribui uma implementação em `repository.healthBridge`.
 */
interface HealthBridge {
    fun saveWorkout(title: String, workoutType: String, caloriesBurned: Double, durationMinutes: Int, date: Long) {}
    suspend fun fetchTodayCalories(): Double = 0.0
    suspend fun fetchTodaySteps(): Double = 0.0
    suspend fun fetchExternalWorkoutSamples(days: Int): List<ExternalWorkoutSample> = emptyList()
}

object NoOpHealthBridge : HealthBridge

/** Instantâneo dos números do dia (equivalente ao WidgetCalorieData + lembretes do iOS). */
data class TodaySnapshot(
    val caloriesConsumed: Double,
    val caloriesTarget: Double,
    val baseCaloriesTarget: Double,
    val caloriesBurned: Double,
    val activeCaloriesBurned: Double,
    val stepsCount: Int,
    val proteinConsumed: Double,
    val proteinTarget: Double,
    val carbsConsumed: Double,
    val carbsTarget: Double,
    val fatConsumed: Double,
    val fatTarget: Double,
    val waterConsumed: Int,
    val waterTarget: Int,
    val goalRaw: String,
    val hasLoggedBreakfast: Boolean,
    val hasLoggedLunch: Boolean,
    val hasLoggedDinner: Boolean,
    val lastUpdated: Long = System.currentTimeMillis()
)

/**
 * Repositório central (equivalente ao FormaDataStore do iOS): cache local SQLite + escrita Remote-First
 * (backend primeiro, cache depois) para refeições, treinos, água, pesagens, fichas e análises.
 */
class FormaRepository(
    private val context: Context,
    private val db: DatabaseHelper
) : LocalDataGateway {

    val backendClient = BackendApiClient(context)
    val apiClient: BackendApiClient get() = backendClient
    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())
    private val lock = Any()

    /** Equivalente ao AppActivityLogManager. */
    val activityLog = ActivityLogManager(db)

    /** Equivalente ao AuthManager. */
    val auth = AuthManager(context, backendClient)

    /** Equivalente ao BackendSyncManager. */
    val sync = BackendSyncManager(context, backendClient, auth, db, activityLog)

    /** Equivalente ao AppNavigationRouter.shared. */
    val router: AppNavigationRouter get() = AppNavigationRouter.shared

    /** Ganchos do Health Connect (no-op por padrão). */
    @Volatile
    var healthBridge: HealthBridge = NoOpHealthBridge

    /** Notifica o agente de notificações/widget/Ilha sobre os números do dia (syncTodayData do iOS). */
    @Volatile
    var onTodayDataChanged: ((TodaySnapshot) -> Unit)? = null

    /** Chamado após resetLocalDataForUserSwitch (ex: limpar sessão de treino compartilhada). */
    @Volatile
    var onLocalDataReset: (() -> Unit)? = null

    /** Fornece/zera água pendente de widgets (drainPendingWater do iOS). */
    @Volatile
    var drainPendingWater: (() -> Int)? = null

    private val healthPrefs = context.getSharedPreferences("forma_health_prefs", Context.MODE_PRIVATE)

    // Profile & Auth
    private val _userProfile = MutableStateFlow(db.getProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()
    val profile: StateFlow<UserProfile> = _userProfile

    val authUser: StateFlow<AuthUser?> get() = auth.authUser
    val isAuthenticated: StateFlow<Boolean> get() = auth.isAuthenticated
    val isSyncing: StateFlow<Boolean> get() = sync.isSyncing
    val hasCompletedInitialSessionPull: StateFlow<Boolean> get() = sync.hasCompletedInitialSessionPull
    val syncErrorMessage: StateFlow<String?> get() = sync.lastSyncError

    // Data lists
    private val _foodEntries = MutableStateFlow(db.getAllFoods())
    val foodEntries: StateFlow<List<FoodEntry>> = _foodEntries.asStateFlow()
    val foods: StateFlow<List<FoodEntry>> = _foodEntries

    private val _workoutEntries = MutableStateFlow(db.getAllWorkouts())
    val workoutEntries: StateFlow<List<WorkoutEntry>> = _workoutEntries.asStateFlow()
    val workouts: StateFlow<List<WorkoutEntry>> = _workoutEntries

    private val _waters = MutableStateFlow(db.getAllWaters())
    val waters: StateFlow<List<WaterEntry>> = _waters.asStateFlow()

    private val _weightEntries = MutableStateFlow(db.getAllWeights())
    val weightEntries: StateFlow<List<WeightEntry>> = _weightEntries.asStateFlow()
    val weights: StateFlow<List<WeightEntry>> = _weightEntries

    private val _workoutRoutines = MutableStateFlow(db.getAllRoutines())
    val workoutRoutines: StateFlow<List<WorkoutRoutine>> = _workoutRoutines.asStateFlow()
    val routines: StateFlow<List<WorkoutRoutine>> = _workoutRoutines

    // EvolutionAnalysisStore: apenas a análise mais recente
    private val _latestAnalysis = MutableStateFlow(db.getLatestAnalysis())
    val latestAnalysis: StateFlow<EvolutionAIAnalysis?> = _latestAnalysis.asStateFlow()
    private val _aiAnalyses = MutableStateFlow(_latestAnalysis.value?.let { listOf(it) } ?: emptyList())
    val aiAnalyses: StateFlow<List<EvolutionAIAnalysis>> = _aiAnalyses.asStateFlow()

    // Daily Summary
    private val _dailySummary = MutableStateFlow(DailySummary())
    val dailySummary: StateFlow<DailySummary> = _dailySummary.asStateFlow()

    // Métricas de saúde do dia (Health Connect)
    private val _stepsToday = MutableStateFlow(0)
    val stepsToday: StateFlow<Int> = _stepsToday.asStateFlow()
    private val _healthActiveCalories = MutableStateFlow(0.0)
    val healthActiveCalories: StateFlow<Double> = _healthActiveCalories.asStateFlow()

    // Último erro de operação Remote-First disparada por wrappers síncronos (a UI pode exibir)
    private val _operationError = MutableStateFlow<String?>(null)
    val operationError: StateFlow<String?> = _operationError.asStateFlow()
    fun clearOperationError() { _operationError.value = null }

    // Workout Live Activity / Island
    private val _isWorkoutActive = MutableStateFlow(false)
    val isWorkoutActive: StateFlow<Boolean> = _isWorkoutActive.asStateFlow()

    private val _workoutDurationSeconds = MutableStateFlow(0)
    val workoutDurationSeconds: StateFlow<Int> = _workoutDurationSeconds.asStateFlow()

    private val _activeWorkoutName = MutableStateFlow("Treino")
    val activeWorkoutName: StateFlow<String> = _activeWorkoutName.asStateFlow()

    private var workoutTimerJob: Job? = null

    init {
        auth.syncManager = sync
        auth.gateway = this
        sync.gateway = this
        auth.restoreSession()
        recalculateSummary()
    }

    fun isLoggedIn(): Boolean = auth.isAuthenticated.value

    /**
     * Rotina de início do app (FormaApp.task do iOS): se autenticado e ainda sem pull inicial, sincroniza.
     * Chame novamente ao voltar para o primeiro plano via [onAppForeground].
     */
    fun onAppStart() {
        if (!auth.isAuthenticated.value) return
        syncTodayData()
        if (!sync.hasCompletedInitialSessionPull.value) {
            scope.launch { sync.sync() }
        }
    }

    /** willEnterForeground do iOS: atualiza números do dia e treinos externos. */
    fun onAppForeground() {
        if (!auth.isAuthenticated.value) return
        syncTodayData()
        scope.launch { runCatching { syncExternalHealthWorkoutsInBackground() } }
    }

    fun getTodayWater(): Int = _dailySummary.value.totalWaterMl
    fun getTodayCaloriesConsumed(): Double = _dailySummary.value.totalCalories.toDouble()

    // ------------------------------------------------------------------------------------------
    // Utilitários de intervalo de datas (Home / Evolução)
    // ------------------------------------------------------------------------------------------
    fun startOfDay(millis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance().apply { timeInMillis = millis }
        cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0); cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    fun dayRange(millis: Long = System.currentTimeMillis()): TimeRange {
        val start = startOfDay(millis)
        val end = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_YEAR, 1) }.timeInMillis
        return TimeRange(start, end)
    }

    fun weekRange(millis: Long = System.currentTimeMillis()): TimeRange {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(millis) }
        val first = cal.firstDayOfWeek
        while (cal.get(Calendar.DAY_OF_WEEK) != first) cal.add(Calendar.DAY_OF_YEAR, -1)
        val start = cal.timeInMillis
        cal.add(Calendar.DAY_OF_YEAR, 7)
        return TimeRange(start, cal.timeInMillis)
    }

    fun monthRange(millis: Long = System.currentTimeMillis()): TimeRange {
        val cal = Calendar.getInstance().apply { timeInMillis = startOfDay(millis) }
        cal.set(Calendar.DAY_OF_MONTH, 1)
        val start = cal.timeInMillis
        cal.add(Calendar.MONTH, 1)
        return TimeRange(start, cal.timeInMillis)
    }

    fun foodsBetween(range: TimeRange): List<FoodEntry> = synchronized(lock) { db.getFoodsBetween(range.start, range.endExclusive) }
    fun workoutsBetween(range: TimeRange): List<WorkoutEntry> = synchronized(lock) { db.getWorkoutsBetween(range.start, range.endExclusive) }
    fun watersBetween(range: TimeRange): List<WaterEntry> = synchronized(lock) { db.getWatersBetween(range.start, range.endExclusive) }
    fun weightsBetween(range: TimeRange): List<WeightEntry> = synchronized(lock) { db.getWeightsBetween(range.start, range.endExclusive) }

    fun summaryFor(range: TimeRange): DailySummary {
        val foods = foodsBetween(range)
        val workouts = workoutsBetween(range)
        val waters = watersBetween(range)
        return DailySummary(
            totalCalories = foods.sumOf { it.calories }.toInt(),
            totalBurnedCalories = workouts.sumOf { it.caloriesBurned }.toInt(),
            totalWaterMl = waters.sumOf { it.milliliters },
            totalProtein = foods.sumOf { it.protein }.toInt(),
            totalCarbs = foods.sumOf { it.carbohydrates }.toInt(),
            totalFat = foods.sumOf { it.fat }.toInt()
        )
    }

    /** Recalcula o resumo de hoje (chame ao voltar ao primeiro plano para tratar virada de dia). */
    fun refreshToday() = recalculateSummary()

    // ------------------------------------------------------------------------------------------
    // Perfil & recálculo contextual
    // ------------------------------------------------------------------------------------------

    /** Garante que exista exatamente um Profile e que autoRecalculateGoals esteja ativo. */
    fun ensureSingleProfile(): UserProfile = synchronized(lock) {
        var p = db.getProfile()
        if (!p.autoRecalculateGoals) {
            p = p.copy(autoRecalculateGoals = true)
            db.saveProfile(p)
        }
        if (p.name.isBlank() && p.email.isBlank()) {
            val n = auth.currentUserName.value.orEmpty()
            val e = auth.currentUserEmail.value.orEmpty()
            if (n.isNotEmpty() || e.isNotEmpty()) {
                p = p.copy(name = n, email = e)
                db.saveProfile(p)
            }
        }
        _userProfile.value = p
        p
    }

    private fun persistProfile(p: UserProfile) {
        db.saveProfile(p)
        _userProfile.value = p
    }

    fun buildRecalculationContext(): RecalculationContext = synchronized(lock) {
        fun daysAgo(days: Int): Long = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -days) }.timeInMillis
        val sevenDaysAgo = daysAgo(7)
        val fourteenDaysAgo = daysAgo(14)
        val twentyOneDaysAgo = daysAgo(21)

        val workouts14d = db.getWorkoutsBetween(fourteenDaysAgo, Long.MAX_VALUE)
        val sum14d = workouts14d.sumOf { it.caloriesBurned }
        val sum7d = workouts14d.filter { it.workoutDate >= sevenDaysAgo }.sumOf { it.caloriesBurned }
        val avgDailyWorkoutBurn = maxOf(sum14d / 14.0, sum7d / 7.0)

        val routines = db.getAllRoutines()
        val routineEstimatedDailyBurn = if (routines.isNotEmpty()) {
            val avgPerSession = routines.sumOf { it.estimatedCalories } / routines.size.toDouble()
            val sessionsPerWeek = minOf(5, maxOf(3, routines.size)).toDouble()
            (avgPerSession * sessionsPerWeek) / 7.0
        } else 0.0

        val recentWeights = db.getWeightsBetween(twentyOneDaysAgo, Long.MAX_VALUE).sortedBy { it.entryDate }
        var weeklyWeightTrendKg: Double? = null
        if (recentWeights.size >= 2) {
            val first = recentWeights.first()
            val last = recentWeights.last()
            val daysSpan = (last.entryDate - first.entryDate).toDouble() / 86_400_000.0
            if (daysSpan >= 4.0) weeklyWeightTrendKg = ((last.weight - first.weight) / daysSpan) * 7.0
        }

        val recentFoods = db.getFoodsBetween(sevenDaysAgo, Long.MAX_VALUE)
        var recentAvgIntake: Double? = null
        if (recentFoods.isNotEmpty()) {
            val byDay = HashMap<Long, Double>()
            recentFoods.forEach { f -> byDay[startOfDay(f.consumedAt)] = (byDay[startOfDay(f.consumedAt)] ?: 0.0) + f.calories }
            if (byDay.isNotEmpty()) recentAvgIntake = byDay.values.sum() / byDay.size.toDouble()
        }

        RecalculationContext(
            workoutsLast14DaysCount = workouts14d.size,
            avgDailyWorkoutBurn = avgDailyWorkoutBurn,
            routineEstimatedDailyBurn = routineEstimatedDailyBurn,
            recentAvgSteps = _stepsToday.value.toDouble(),
            weeklyWeightTrendKg = weeklyWeightTrendKg,
            recentAvgCalorieIntake = recentAvgIntake
        )
    }

    fun previewRecalculatedProfile(
        newWeight: Double = _userProfile.value.currentWeight,
        newHeight: Double = _userProfile.value.heightMeters,
        newGoal: FitnessGoal = _userProfile.value.goal,
        newTargetWeight: Double = _userProfile.value.targetWeight
    ): UserProfile = _userProfile.value.recalculated(
        newWeight = newWeight,
        newHeight = newHeight,
        newGoal = newGoal,
        newTargetWeight = newTargetWeight,
        context = buildRecalculationContext()
    )

    /**
     * recalculateProfileWithFullContext do iOS: só recalcula quando o onboarding foi concluído.
     * Persiste e devolve o perfil atualizado (ou null se não aplicável).
     */
    fun recalculateProfileWithFullContext(weight: Double? = null, profile: UserProfile? = null): UserProfile? =
        synchronized(lock) {
            val target = profile ?: db.getProfile()
            if (!target.onboardingCompleted) return@synchronized null
            val updated = target.recalculateDailyGoals(weight, buildRecalculationContext())
            persistProfile(updated)
            updated
        }

    /** Atualização local do perfil + auto-sync com debounce (edição de metas na aba Perfil). */
    fun updateProfile(newProfile: UserProfile) {
        synchronized(lock) { persistProfile(newProfile) }
        recalculateSummary()
        syncTodayData(skipCloudSyncTrigger = false)
    }

    private fun resolvedRemoteName(p: UserProfile): String? =
        if (p.name.trim().isEmpty()) auth.currentUserName.value else p.name

    fun remoteProfilePayload(p: UserProfile, overrideName: String? = null): RemoteProfilePayload =
        RemoteProfilePayload.from(p, overrideName ?: resolvedRemoteName(p))

    suspend fun saveProfileRemoteFirst(newProfile: UserProfile, overrideName: String? = null) {
        val payload = remoteProfilePayload(newProfile, overrideName)
        sync.pushProfileRemoteFirst(payload)
        var p = newProfile
        val cleanOverride = overrideName?.trim()
        if (!cleanOverride.isNullOrEmpty()) {
            p = p.copy(name = cleanOverride)
            auth.updateLocalUserName(cleanOverride)
        }
        synchronized(lock) { persistProfile(p) }
        syncTodayData(skipCloudSyncTrigger = true)
    }

    /** Configuração inicial obrigatória (Remote-First: perfil + pesagem inicial). */
    suspend fun completeInitialOnboardingRemoteFirst(
        name: String,
        goal: FitnessGoal,
        currentWeight: Double,
        heightMeters: Double,
        targetWeight: Double,
        targetCalorieIntake: Double,
        targetProtein: Double,
        targetCarbohydrates: Double,
        targetFat: Double,
        targetWaterMilliliters: Int,
        targetCalorieBurn: Double,
        targetSteps: Double
    ) {
        val cleanName = name.trim()
        val now = System.currentTimeMillis()
        val initialWeightId = UUID.randomUUID().toString()
        val note = "Configuração inicial da conta"

        val payload = RemoteProfilePayload(
            name = cleanName, goal = goal.rawValue,
            targetCalorieIntake = targetCalorieIntake, targetCalorieBurn = targetCalorieBurn,
            targetSteps = targetSteps, targetWeight = targetWeight, targetProtein = targetProtein,
            targetCarbohydrates = targetCarbohydrates, targetFat = targetFat,
            targetWaterMilliliters = targetWaterMilliliters, heightMeters = heightMeters,
            currentWeight = currentWeight, onboardingCompleted = true
        )

        // 1. Grava PRIMEIRO no backend (Perfil + Pesagem Inicial)
        sync.pushProfileRemoteFirst(payload)
        sync.pushWeightRemoteFirst(initialWeightId, currentWeight, now, note, payload)

        // 2. Só após confirmação, salva no cache local
        synchronized(lock) {
            var p = ensureSingleProfile()
            p = p.copy(
                name = cleanName,
                email = auth.currentUserEmail.value?.takeIf { it.isNotEmpty() } ?: p.email,
                goal = goal, currentWeight = currentWeight, heightMeters = heightMeters, targetWeight = targetWeight,
                targetCalorieIntake = targetCalorieIntake, targetProtein = targetProtein,
                targetCarbohydrates = targetCarbohydrates, targetFat = targetFat,
                targetWaterMilliliters = targetWaterMilliliters, targetCalorieBurn = targetCalorieBurn,
                targetSteps = targetSteps, onboardingCompleted = true, autoRecalculateGoals = true
            )
            persistProfile(p)
            db.insertWeight(WeightEntry(id = initialWeightId, weight = currentWeight, entryDate = now, notes = note))
            _weightEntries.value = db.getAllWeights()
        }

        auth.updateLocalUserName(cleanName)
        auth.currentUserId.value?.let { uid ->
            if (healthImportCutoff(uid) == null) setHealthImportCutoff(uid, now)
        }
        activityLog.logWeight(id = initialWeightId, weightKg = currentWeight, date = now, notes = note)
        syncTodayData(skipCloudSyncTrigger = true)
    }

    // ------------------------------------------------------------------------------------------
    // Refeições (Remote-First)
    // ------------------------------------------------------------------------------------------
    suspend fun insertFoodEntriesRemoteFirst(entries: List<FoodEntry>) {
        if (entries.isEmpty()) return
        sync.pushFoodsRemoteFirst(entries)
        synchronized(lock) {
            for (entry in entries) {
                db.insertFood(entry)
                activityLog.logMeal(
                    id = entry.id, name = entry.name, mealType = entry.mealType.rawValue,
                    calories = entry.calories, protein = entry.protein, carbs = entry.carbohydrates, fat = entry.fat,
                    isSnack = entry.mealType == MealType.SNACKS, date = entry.consumedAt
                )
            }
            recalculateProfileWithFullContext()
            _foodEntries.value = db.getAllFoods()
        }
        recalculateSummary()
        syncTodayData(skipCloudSyncTrigger = false)
    }

    suspend fun deleteFoodEntriesRemoteFirst(ids: List<String>) {
        if (ids.isEmpty()) return
        sync.deleteFoodsRemoteFirst(ids)
        synchronized(lock) {
            activityLog.removeRecords(ids)
            ids.forEach { db.deleteFood(it) }
            recalculateProfileWithFullContext()
            _foodEntries.value = db.getAllFoods()
        }
        recalculateSummary()
        syncTodayData(skipCloudSyncTrigger = false)
    }

    fun insertFoodEntries(entries: List<FoodEntry>) {
        if (entries.isEmpty()) return
        if (!auth.isAuthenticated.value) {
            synchronized(lock) {
                entries.forEach { db.insertFood(it) }
                recalculateProfileWithFullContext()
                _foodEntries.value = db.getAllFoods()
            }
            recalculateSummary()
            return
        }
        launchRemote { insertFoodEntriesRemoteFirst(entries) }
    }

    fun addFood(food: FoodEntry) = insertFoodEntries(listOf(food))
    fun addFoods(foods: List<FoodEntry>) = insertFoodEntries(foods)

    fun deleteFoodEntries(ids: List<String>) {
        if (ids.isEmpty()) return
        if (!auth.isAuthenticated.value) {
            synchronized(lock) {
                ids.forEach { db.deleteFood(it) }
                recalculateProfileWithFullContext()
                _foodEntries.value = db.getAllFoods()
            }
            recalculateSummary()
            return
        }
        launchRemote { deleteFoodEntriesRemoteFirst(ids) }
    }

    fun deleteFoodEntry(id: String) = deleteFoodEntries(listOf(id))

    // ------------------------------------------------------------------------------------------
    // Treinos realizados (Remote-First)
    // ------------------------------------------------------------------------------------------
    suspend fun insertWorkoutEntryRemoteFirst(
        entry: WorkoutEntry,
        routineTrained: WorkoutRoutine? = null,
        saveToHealth: Boolean = true
    ) {
        val trainedAt = System.currentTimeMillis()
        val updatedRoutine = routineTrained?.copy(lastTrainedAt = trainedAt)

        sync.pushWorkoutRemoteFirst(entry, updatedRoutine)

        synchronized(lock) {
            updatedRoutine?.let { db.insertRoutine(it); _workoutRoutines.value = db.getAllRoutines() }
            db.insertWorkout(entry)
            recalculateProfileWithFullContext()
            _workoutEntries.value = db.getAllWorkouts()
        }
        recalculateSummary()

        activityLog.logWorkout(
            id = entry.id, title = entry.title, workoutType = entry.workoutType,
            durationMinutes = entry.durationMinutes, caloriesBurned = entry.caloriesBurned,
            date = entry.workoutDate, details = entry.notes
        )

        if (saveToHealth) {
            runCatching {
                healthBridge.saveWorkout(entry.title, entry.workoutType, entry.caloriesBurned, entry.durationMinutes, entry.workoutDate)
            }
        }
        syncTodayData(skipCloudSyncTrigger = false)
    }

    suspend fun deleteWorkoutEntryRemoteFirst(id: String) {
        sync.deleteWorkoutRemoteFirst(id)
        synchronized(lock) {
            activityLog.removeRecords(listOf(id))
            db.deleteWorkout(id)
            recalculateProfileWithFullContext()
            _workoutEntries.value = db.getAllWorkouts()
        }
        recalculateSummary()
        syncTodayData(skipCloudSyncTrigger = false)
    }

    fun insertWorkoutEntry(entry: WorkoutEntry, routineTrained: WorkoutRoutine? = null) {
        if (!auth.isAuthenticated.value) {
            synchronized(lock) {
                db.insertWorkout(entry)
                recalculateProfileWithFullContext()
                _workoutEntries.value = db.getAllWorkouts()
            }
            recalculateSummary()
            return
        }
        launchRemote { insertWorkoutEntryRemoteFirst(entry, routineTrained) }
    }

    fun addWorkout(workout: WorkoutEntry) = insertWorkoutEntry(workout)

    fun deleteWorkoutEntry(id: String) {
        if (!auth.isAuthenticated.value) {
            synchronized(lock) {
                db.deleteWorkout(id)
                recalculateProfileWithFullContext()
                _workoutEntries.value = db.getAllWorkouts()
            }
            recalculateSummary()
            return
        }
        launchRemote { deleteWorkoutEntryRemoteFirst(id) }
    }

    // ------------------------------------------------------------------------------------------
    // Treinos externos (Health Connect) com barreira de corte por conta
    // ------------------------------------------------------------------------------------------
    override fun setHealthImportCutoff(userId: String, millis: Long) {
        if (userId.isEmpty()) return
        healthPrefs.edit().putLong("forma_healthkit_cutoff_$userId", millis).apply()
    }

    fun healthImportCutoff(userId: String): Long? {
        if (userId.isEmpty()) return null
        val key = "forma_healthkit_cutoff_$userId"
        if (!healthPrefs.contains(key)) return null
        val ts = healthPrefs.getLong(key, 0L)
        return if (ts > 0L) ts else null
    }

    suspend fun syncExternalHealthWorkoutsInBackground(days: Int = 7) {
        if (!auth.isAuthenticated.value) return
        val userId = auth.currentUserId.value?.takeIf { it.isNotEmpty() } ?: return
        if (db.getProfile().requiresInitialSetup) return

        val cutoffDate = healthImportCutoff(userId)
        val samples = healthBridge.fetchExternalWorkoutSamples(days)
        if (samples.isEmpty() || !auth.isAuthenticated.value || auth.currentUserId.value != userId) return

        val existingWorkouts = synchronized(lock) { db.getAllWorkouts() }

        // Sem treinos e sem cutoff gravado: define cutoff agora para não puxar treinos antigos de terceiros
        val effectiveCutoff: Long? = when {
            cutoffDate != null -> cutoffDate
            existingWorkouts.isEmpty() -> System.currentTimeMillis().also { setHealthImportCutoff(userId, it) }
            else -> null
        }

        val existingKeys = existingWorkouts.map { "${it.title}_${it.workoutDate / 1000}" }.toMutableSet()
        val candidates = mutableListOf<WorkoutEntry>()
        for (sample in samples) {
            if (effectiveCutoff != null && sample.startMillis < effectiveCutoff) continue
            val uniqueKey = "${sample.title}_${sample.startMillis / 1000}"
            if (!existingKeys.add(uniqueKey)) continue
            candidates.add(
                WorkoutEntry(
                    title = sample.title,
                    workoutType = sample.workoutType,
                    durationMinutes = sample.durationMinutes,
                    caloriesBurned = sample.caloriesBurned,
                    workoutDate = sample.startMillis,
                    notes = "Sincronizado automaticamente (${sample.sourceName})"
                )
            )
        }
        for (entry in candidates) {
            runCatching { insertWorkoutEntryRemoteFirst(entry, saveToHealth = false) }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Hidratação e Pesagens (Remote-First)
    // ------------------------------------------------------------------------------------------
    suspend fun insertWaterEntryRemoteFirst(entry: WaterEntry) {
        sync.pushWaterRemoteFirst(entry)
        synchronized(lock) {
            db.insertWater(entry)
            _waters.value = db.getAllWaters()
        }
        recalculateSummary()
        activityLog.logWater(id = entry.id, milliliters = entry.milliliters, date = entry.entryDate)
        syncTodayData(skipCloudSyncTrigger = true)
    }

    suspend fun deleteWaterEntryRemoteFirst(id: String) {
        sync.deleteWaterRemoteFirst(id)
        synchronized(lock) {
            activityLog.removeRecords(listOf(id))
            db.deleteWater(id)
            _waters.value = db.getAllWaters()
        }
        recalculateSummary()
        syncTodayData(skipCloudSyncTrigger = true)
    }

    suspend fun createWeightEntryRemoteFirst(weight: Double, date: Long, notes: String) {
        val entryId = UUID.randomUUID().toString()
        val previousProfile = ensureSingleProfile()
        val withWeight = previousProfile.copy(currentWeight = weight, autoRecalculateGoals = true)
        val recalculated = synchronized(lock) {
            persistProfile(withWeight)
            recalculateProfileWithFullContext(weight, withWeight) ?: withWeight
        }
        val payload = remoteProfilePayload(recalculated)

        try {
            sync.pushWeightRemoteFirst(entryId, weight, date, notes, payload)
        } catch (e: Exception) {
            synchronized(lock) {
                persistProfile(previousProfile)
                recalculateProfileWithFullContext(previousProfile.currentWeight, previousProfile)
            }
            throw e
        }

        synchronized(lock) {
            db.insertWeight(WeightEntry(id = entryId, weight = weight, entryDate = date, notes = notes))
            _weightEntries.value = db.getAllWeights()
        }
        activityLog.logWeight(id = entryId, weightKg = weight, date = date, notes = notes)
        syncTodayData(skipCloudSyncTrigger = true)
    }

    suspend fun deleteWeightEntryRemoteFirst(id: String) {
        val (profile, nextLatest) = synchronized(lock) {
            val remaining = db.getAllWeights()
            db.getProfile() to remaining.firstOrNull { !it.id.equals(id, ignoreCase = true) }
        }

        var updatedPayload: RemoteProfilePayload? = null
        if (nextLatest != null) {
            val m = UserProfile.computeContextualTargets(
                weight = nextLatest.weight, height = profile.heightMeters, targetWeight = profile.targetWeight,
                goal = profile.goal, targetCalorieBurn = profile.targetCalorieBurn, targetSteps = profile.targetSteps,
                context = buildRecalculationContext()
            )
            updatedPayload = RemoteProfilePayload(
                name = profile.name, goal = profile.goal.rawValue,
                targetCalorieIntake = m.suggestedCalories, targetCalorieBurn = profile.targetCalorieBurn,
                targetSteps = profile.targetSteps, targetWeight = profile.targetWeight,
                targetProtein = m.proteinGrams, targetCarbohydrates = m.carbsGrams, targetFat = m.fatGrams,
                targetWaterMilliliters = m.waterMilliliters, heightMeters = profile.heightMeters,
                currentWeight = nextLatest.weight, onboardingCompleted = profile.onboardingCompleted
            )
        }

        sync.deleteWeightRemoteFirst(id, updatedPayload)

        synchronized(lock) {
            activityLog.removeRecords(listOf(id))
            db.deleteWeight(id)
            if (nextLatest != null) {
                val p = db.getProfile().copy(currentWeight = nextLatest.weight)
                persistProfile(p)
                recalculateProfileWithFullContext(nextLatest.weight, p)
            } else {
                recalculateProfileWithFullContext()
            }
            _weightEntries.value = db.getAllWeights()
        }
        syncTodayData(skipCloudSyncTrigger = true)
    }

    fun addWater(ml: Int = 250) {
        val entry = WaterEntry(milliliters = ml)
        if (!auth.isAuthenticated.value) {
            synchronized(lock) { db.insertWater(entry); _waters.value = db.getAllWaters() }
            recalculateSummary()
            return
        }
        launchRemote { insertWaterEntryRemoteFirst(entry) }
    }

    fun deleteWaterEntry(id: String) {
        if (!auth.isAuthenticated.value) {
            synchronized(lock) { db.deleteWater(id); _waters.value = db.getAllWaters() }
            recalculateSummary()
            return
        }
        launchRemote { deleteWaterEntryRemoteFirst(id) }
    }

    fun addWeight(weight: Double, notes: String = "") {
        if (!auth.isAuthenticated.value) {
            synchronized(lock) {
                val p = db.getProfile().copy(currentWeight = weight, autoRecalculateGoals = true)
                persistProfile(p)
                recalculateProfileWithFullContext(weight, p)
                db.insertWeight(WeightEntry(weight = weight, notes = notes))
                _weightEntries.value = db.getAllWeights()
            }
            return
        }
        launchRemote { createWeightEntryRemoteFirst(weight, System.currentTimeMillis(), notes) }
    }

    fun deleteWeightEntry(id: String) {
        if (!auth.isAuthenticated.value) {
            synchronized(lock) { db.deleteWeight(id); _weightEntries.value = db.getAllWeights() }
            return
        }
        launchRemote { deleteWeightEntryRemoteFirst(id) }
    }

    // ------------------------------------------------------------------------------------------
    // Fichas de treino & análises de IA (Remote-First)
    // ------------------------------------------------------------------------------------------
    suspend fun saveWorkoutRoutineRemoteFirst(routine: WorkoutRoutine) {
        sync.pushRoutineRemoteFirst(routine)
        synchronized(lock) {
            db.insertRoutine(routine)
            recalculateProfileWithFullContext()
            _workoutRoutines.value = db.getAllRoutines()
        }
        syncTodayData(skipCloudSyncTrigger = false)
    }

    suspend fun deleteWorkoutRoutineRemoteFirst(id: String) {
        sync.deleteRoutineRemoteFirst(id)
        synchronized(lock) {
            db.deleteRoutine(id)
            recalculateProfileWithFullContext()
            _workoutRoutines.value = db.getAllRoutines()
        }
        syncTodayData(skipCloudSyncTrigger = false)
    }

    suspend fun toggleFavoriteRoutineRemoteFirst(id: String) {
        val current = synchronized(lock) { db.getAllRoutines().firstOrNull { it.id.equals(id, ignoreCase = true) } } ?: return
        val toggled = current.copy(isFavorite = !current.isFavorite)
        sync.pushRoutineRemoteFirst(toggled)
        synchronized(lock) {
            db.insertRoutine(toggled)
            _workoutRoutines.value = db.getAllRoutines()
        }
    }

    suspend fun saveEvolutionAnalysisRemoteFirst(analysis: EvolutionAIAnalysis) {
        sync.pushAnalysisRemoteFirst(analysis)
        saveEvolutionAnalysis(analysis)
    }

    /** EvolutionAnalysisStore.save */
    fun saveEvolutionAnalysis(analysis: EvolutionAIAnalysis) {
        val clean = analysis.sanitized()
        synchronized(lock) { db.saveLatestAnalysis(clean) }
        _latestAnalysis.value = clean
        _aiAnalyses.value = listOf(clean)
    }

    /** EvolutionAnalysisStore.clear */
    fun clearEvolutionAnalysis() {
        synchronized(lock) { db.clearAnalyses() }
        _latestAnalysis.value = null
        _aiAnalyses.value = emptyList()
    }

    fun insertWorkoutRoutine(routine: WorkoutRoutine) {
        if (!auth.isAuthenticated.value) {
            synchronized(lock) {
                db.insertRoutine(routine)
                recalculateProfileWithFullContext()
                _workoutRoutines.value = db.getAllRoutines()
            }
            return
        }
        launchRemote { saveWorkoutRoutineRemoteFirst(routine) }
    }

    fun deleteWorkoutRoutine(id: String) {
        if (!auth.isAuthenticated.value) {
            synchronized(lock) { db.deleteRoutine(id); _workoutRoutines.value = db.getAllRoutines() }
            return
        }
        launchRemote { deleteWorkoutRoutineRemoteFirst(id) }
    }

    fun toggleFavoriteRoutine(id: String) {
        if (!auth.isAuthenticated.value) {
            synchronized(lock) {
                db.getAllRoutines().firstOrNull { it.id.equals(id, ignoreCase = true) }?.let {
                    db.insertRoutine(it.copy(isFavorite = !it.isFavorite))
                }
                _workoutRoutines.value = db.getAllRoutines()
            }
            return
        }
        launchRemote { toggleFavoriteRoutineRemoteFirst(id) }
    }

    // ------------------------------------------------------------------------------------------
    // Água pendente (widgets) e métricas do dia
    // ------------------------------------------------------------------------------------------
    fun syncPendingWater() {
        if (!auth.isAuthenticated.value) return
        val pending = drainPendingWater?.invoke() ?: 0
        if (pending > 0) {
            val entry = WaterEntry(milliliters = pending)
            synchronized(lock) { db.insertWater(entry); _waters.value = db.getAllWaters() }
            activityLog.logWater(id = entry.id, milliliters = entry.milliliters, date = entry.entryDate)
            scope.launch { runCatching { sync.pushWaterRemoteFirst(entry) } }
        }
    }

    /** Atualiza passos/calorias ativas do dia (Health Connect). */
    fun updateHealthMetrics(activeCalories: Double?, steps: Int?) {
        activeCalories?.let { _healthActiveCalories.value = it }
        steps?.let { _stepsToday.value = it }
        publishTodaySnapshot(_healthActiveCalories.value.takeIf { it > 0 })
    }

    override fun syncTodayData(skipCloudSyncTrigger: Boolean) = syncTodayData(null, null, skipCloudSyncTrigger)

    fun syncTodayData(
        activeCaloriesOverride: Double? = null,
        stepsOverride: Int? = null,
        skipCloudSyncTrigger: Boolean = false
    ) {
        syncPendingWater()
        stepsOverride?.let { _stepsToday.value = it }
        activeCaloriesOverride?.takeIf { it > 0 }?.let { _healthActiveCalories.value = it }
        recalculateSummary()
        publishTodaySnapshot(activeCaloriesOverride)

        if (activeCaloriesOverride == null && auth.isAuthenticated.value && !_userProfile.value.requiresInitialSetup) {
            scope.launch {
                val cal = runCatching { healthBridge.fetchTodayCalories() }.getOrDefault(0.0)
                val steps = runCatching { healthBridge.fetchTodaySteps() }.getOrDefault(0.0)
                if (cal > 0) _healthActiveCalories.value = cal
                if (steps > 0) _stepsToday.value = steps.toInt()
                publishTodaySnapshot(null)
            }
        }

        if (!skipCloudSyncTrigger) sync.triggerAutoSync()
    }

    private fun publishTodaySnapshot(activeCaloriesOverride: Double?) {
        val listener = onTodayDataChanged ?: return
        val range = dayRange()
        val foods = foodsBetween(range)
        val workouts = workoutsBetween(range)
        val waters = watersBetween(range)
        val p = _userProfile.value

        val baseTarget = if (p.targetCalorieIntake > 0) p.targetCalorieIntake else 2800.0
        val workoutBurned = workouts.sumOf { it.caloriesBurned }
        val healthActive = maxOf(workoutBurned, _healthActiveCalories.value)
        val activeBurned = if (activeCaloriesOverride != null && activeCaloriesOverride > 0) maxOf(workoutBurned, activeCaloriesOverride) else healthActive

        listener(
            TodaySnapshot(
                caloriesConsumed = foods.sumOf { it.calories },
                caloriesTarget = baseTarget + activeBurned,
                baseCaloriesTarget = baseTarget,
                caloriesBurned = p.tmb + activeBurned,
                activeCaloriesBurned = activeBurned,
                stepsCount = _stepsToday.value,
                proteinConsumed = foods.sumOf { it.protein },
                proteinTarget = if (p.targetProtein > 0) p.targetProtein else 160.0,
                carbsConsumed = foods.sumOf { it.carbohydrates },
                carbsTarget = if (p.targetCarbohydrates > 0) p.targetCarbohydrates else 380.0,
                fatConsumed = foods.sumOf { it.fat },
                fatTarget = if (p.targetFat > 0) p.targetFat else 70.0,
                waterConsumed = waters.sumOf { it.milliliters },
                waterTarget = if (p.targetWaterMilliliters > 0) p.targetWaterMilliliters else 2500,
                goalRaw = p.goal.rawValue,
                hasLoggedBreakfast = foods.any { it.mealType == MealType.BREAKFAST },
                hasLoggedLunch = foods.any { it.mealType == MealType.LUNCH },
                hasLoggedDinner = foods.any { it.mealType == MealType.DINNER }
            )
        )
    }

    // ------------------------------------------------------------------------------------------
    // Isolamento multi-usuário (LocalDataGateway)
    // ------------------------------------------------------------------------------------------
    override fun resetLocalDataForUserSwitch(userName: String?, userEmail: String?) {
        drainPendingWater?.invoke()
        synchronized(lock) {
            db.clearAllUserData(userName?.trim().orEmpty(), userEmail?.trim().orEmpty())
            refreshAllFlowsFromDb()
        }
        onLocalDataReset?.invoke()
        syncTodayData(skipCloudSyncTrigger = true)
    }

    override fun currentProfile(): UserProfile = synchronized(lock) { db.getProfile() }

    override fun applyAuthProfile(name: String?, email: String?, profileJson: JsonObject?, isNewRegistration: Boolean): UserProfile =
        synchronized(lock) {
            var p = ensureSingleProfile()
            if (!name.isNullOrEmpty()) p = p.copy(name = name)
            if (!email.isNullOrEmpty()) p = p.copy(email = email)
            if (profileJson != null) {
                profileJson.str("goal")?.let { p = p.copy(goal = FitnessGoal.fromRaw(it)) }
                profileJson.dbl("targetCalorieIntake")?.let { p = p.copy(targetCalorieIntake = it) }
                profileJson.dbl("targetCalorieBurn")?.let { p = p.copy(targetCalorieBurn = it) }
                profileJson.dbl("targetSteps")?.let { p = p.copy(targetSteps = it) }
                profileJson.dbl("targetWeight")?.let { p = p.copy(targetWeight = it) }
                profileJson.dbl("targetProtein")?.let { p = p.copy(targetProtein = it) }
                profileJson.dbl("targetCarbohydrates")?.let { p = p.copy(targetCarbohydrates = it) }
                profileJson.dbl("targetFat")?.let { p = p.copy(targetFat = it) }
                profileJson.int("targetWaterMilliliters")?.let { p = p.copy(targetWaterMilliliters = it) }
                profileJson.dbl("heightMeters")?.let { p = p.copy(heightMeters = it) }
                profileJson.dbl("currentWeight")?.let { p = p.copy(currentWeight = it) }
                p = p.copy(onboardingCompleted = if (isNewRegistration) false else (profileJson.bool("onboardingCompleted") ?: false))
            } else {
                p = p.copy(onboardingCompleted = false)
            }
            persistProfile(p)
            p
        }

    override fun onLocalDataChanged() {
        synchronized(lock) { refreshAllFlowsFromDb() }
    }

    override fun profilePayloadForAutoSync(): RemoteProfilePayload? {
        val p = synchronized(lock) { db.getProfile() }
        if (!p.onboardingCompleted) return null
        return remoteProfilePayload(p)
    }

    private fun refreshAllFlowsFromDb() {
        _userProfile.value = db.getProfile()
        _foodEntries.value = db.getAllFoods()
        _workoutEntries.value = db.getAllWorkouts()
        _waters.value = db.getAllWaters()
        _weightEntries.value = db.getAllWeights()
        _workoutRoutines.value = db.getAllRoutines()
        val analysis = db.getLatestAnalysis()
        _latestAnalysis.value = analysis
        _aiAnalyses.value = analysis?.let { listOf(it) } ?: emptyList()
        recalculateSummary()
    }

    private fun launchRemote(block: suspend () -> Unit) {
        scope.launch {
            try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _operationError.value = e.message
            }
        }
    }

    // ------------------------------------------------------------------------------------------
    // Live Workout Timer
    // ------------------------------------------------------------------------------------------
    fun startWorkout(title: String = "Musculação") {
        workoutTimerJob?.cancel()
        _activeWorkoutName.value = title
        _workoutDurationSeconds.value = 0
        _isWorkoutActive.value = true

        workoutTimerJob = scope.launch {
            while (_isWorkoutActive.value) {
                delay(1000)
                _workoutDurationSeconds.value += 1
            }
        }
    }

    fun stopWorkout() {
        if (!_isWorkoutActive.value) return
        val durationSecs = _workoutDurationSeconds.value
        val durationMins = (durationSecs / 60).coerceAtLeast(1)
        val burnedEst = (durationSecs * 0.12).toInt().coerceAtLeast(15)

        val workout = WorkoutEntry(
            title = _activeWorkoutName.value,
            workoutType = "Treino",
            durationMinutes = durationMins,
            caloriesBurned = burnedEst.toDouble(),
            notes = "Treino finalizado na Ilha Dinâmica ($durationMins min)"
        )
        addWorkout(workout)

        _isWorkoutActive.value = false
        workoutTimerJob?.cancel()
        workoutTimerJob = null
    }

    // ------------------------------------------------------------------------------------------
    // Resumo diário
    // ------------------------------------------------------------------------------------------
    private fun recalculateSummary() {
        _dailySummary.value = summaryFor(dayRange())
    }

    // ------------------------------------------------------------------------------------------
    // Requisições de IA via proxy do backend
    // ------------------------------------------------------------------------------------------
    suspend fun analyzeMeal(text: String, mealType: String, isSnack: Boolean): Result<FoodEntry> {
        val userPrompt = "Refeição: $text (Tipo: $mealType, Lanche: $isSnack)"
        val userName = _userProfile.value.name
        val result = backendClient.analyzeMeal(userPrompt, userName)

        return if (result.isSuccess) {
            val jsonStr = result.getOrNull() ?: ""
            try {
                val obj = gson.fromJson(cleanJson(jsonStr), JsonObject::class.java)
                val name = obj.str("name") ?: text.take(30)
                val mt = when (mealType.lowercase()) {
                    "café da manhã" -> MealType.BREAKFAST
                    "jantar" -> MealType.DINNER
                    "lanche", "café da tarde" -> MealType.SNACKS
                    else -> MealType.LUNCH
                }
                val food = FoodEntry(
                    name = name,
                    mealType = mt,
                    portionGrams = obj.dbl("portionGrams") ?: 100.0,
                    calories = obj.dbl("calories") ?: 250.0,
                    protein = obj.dbl("protein") ?: 10.0,
                    carbohydrates = obj.dbl("carbohydrates") ?: 30.0,
                    fat = obj.dbl("fat") ?: 8.0
                )
                addFood(food)
                Result.success(food)
            } catch (e: Exception) {
                val food = FoodEntry(
                    name = text.take(30), mealType = MealType.LUNCH,
                    calories = 300.0, protein = 15.0, carbohydrates = 35.0, fat = 10.0
                )
                addFood(food)
                Result.success(food)
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Falha ao analisar refeição"))
        }
    }

    suspend fun analyzeWorkout(text: String): Result<WorkoutEntry> {
        val userName = _userProfile.value.name
        val userWeight = _userProfile.value.currentWeight
        val result = backendClient.analyzeWorkout(text, userWeight, userName)

        return if (result.isSuccess) {
            val jsonStr = result.getOrNull() ?: ""
            try {
                val obj = gson.fromJson(cleanJson(jsonStr), JsonObject::class.java)
                val workout = WorkoutEntry(
                    title = obj.str("title") ?: "Treino Estimado",
                    workoutType = obj.str("workoutType") ?: "Musculação",
                    durationMinutes = obj.int("durationMinutes") ?: 45,
                    caloriesBurned = obj.dbl("caloriesBurned") ?: 320.0,
                    notes = obj.str("notes") ?: ""
                )
                addWorkout(workout)
                Result.success(workout)
            } catch (e: Exception) {
                val workout = WorkoutEntry(title = text.take(30), workoutType = "Treino", durationMinutes = 40, caloriesBurned = 280.0)
                addWorkout(workout)
                Result.success(workout)
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Falha ao analisar treino"))
        }
    }

    suspend fun analyzeEvolution(): Result<EvolutionAIAnalysis> {
        val p = _userProfile.value
        val logJson = activityLog.exportRecentLogForAI(days = 14)
        val ctx = buildRecalculationContext()
        val contextualTdee = p.contextualTdee(context = ctx)
        val contextualProfile = p.recalculated(context = ctx)

        val result = backendClient.analyzeEvolution(
            goal = p.goal.title,
            weight = p.currentWeight,
            targetWeight = p.targetWeight,
            heightMeters = p.heightMeters,
            targetCalories = p.targetCalorieIntake,
            targetCalorieBurn = p.targetCalorieBurn,
            targetSteps = p.targetSteps,
            calculatedTmb = p.tmb,
            contextualTdee = contextualTdee,
            suggestedBaseCalories = contextualProfile.targetCalorieIntake,
            avgDailyWorkoutBurn = ctx.avgDailyWorkoutBurn,
            logJson = logJson,
            userName = p.name
        )

        return if (result.isSuccess) {
            try {
                val analysis = EvolutionAIAnalysis.fromJson(cleanJson(result.getOrNull() ?: ""))
                    ?: return Result.failure(Exception("Resposta inválida da análise"))
                // Como no iOS, a tela de Evolução persiste a análise (Remote-First) após receber o resultado.
                saveEvolutionAnalysisRemoteFirst(analysis)
                Result.success(analysis.sanitized())
            } catch (e: Exception) {
                Result.failure(e)
            }
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Falha na análise da IA"))
        }
    }

    // ------------------------------------------------------------------------------------------
    // Autenticação (compatibilidade com a UI atual; use `auth` para a API completa)
    // ------------------------------------------------------------------------------------------
    suspend fun login(email: String, pass: String): Result<String> = try {
        auth.login(email, pass)
        Result.success("Login realizado com sucesso")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    suspend fun register(name: String, email: String, pass: String): Result<String> = try {
        auth.register(name, email, pass)
        Result.success("Cadastro realizado com sucesso")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Assinatura antiga (sem idToken). Prefira `auth.loginWithSocial(...)` completo. */
    suspend fun loginWithSocial(provider: String, email: String, name: String, providerId: String): Result<String> = try {
        auth.loginWithSocial(
            provider = provider, providerId = providerId, email = email, name = name, idToken = null
        )
        Result.success("Autenticado com $provider com sucesso")
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    fun logout() = auth.logout()

    // ------------------------------------------------------------------------------------------
    // Sincronização (compatibilidade)
    // ------------------------------------------------------------------------------------------

    /** Envia imediatamente o perfil/metas ao backend (equivalente ao auto-sync sem debounce). */
    suspend fun syncPush(): Result<Unit> = try {
        val payload = profilePayloadForAutoSync()
        if (payload != null) sync.pushProfileRemoteFirst(payload)
        Result.success(Unit)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        Result.failure(e)
    }

    /** Pull completo do backend (substitui o estado local). */
    suspend fun syncPull(): Result<Unit> {
        sync.sync(forcePull = true)
        val err = sync.lastSyncError.value
        return if (err == null) Result.success(Unit) else Result.failure(Exception(err))
    }

    private fun cleanJson(raw: String): String {
        var clean = raw.trim()
        if (clean.startsWith("```json")) clean = clean.removePrefix("```json")
        else if (clean.startsWith("```")) clean = clean.removePrefix("```")
        if (clean.endsWith("```")) clean = clean.removeSuffix("```")
        return clean.trim()
    }
}

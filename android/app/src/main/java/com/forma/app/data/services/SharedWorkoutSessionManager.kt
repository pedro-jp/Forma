package com.forma.app.data.services

import android.content.Context
import com.google.gson.Gson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class CompletedSetWeightInfo(
    val exerciseIndex: Int,
    val setIndex: Int,
    val setNumber: Int,
    val weightKg: Double,
    val exerciseName: String
)

/**
 * Sessão ativa de treino (iOS: `SharedWorkoutState`). Datas em epoch millis.
 * Persistida em SharedPreferences para sobreviver a processo morto e ser operada pela notificação.
 */
data class SharedWorkoutState(
    val title: String,
    val workoutTypeRaw: String = WorkoutTypes.BODYBUILDING,
    val targetMuscles: String = "",
    val hypertrophyFocus: String = "",
    val exercises: List<WorkoutExercisePlan> = emptyList(),
    val currentExerciseIndex: Int = 0,
    val currentSetIndex: Int = 0,
    val sessionStartDate: Long = System.currentTimeMillis(),
    val baseCalories: Double = 350.0,
    val baseDurationMinutes: Int = 50,
    val isResting: Boolean = false,
    val restStartDate: Long? = null,
    val restEndDate: Long? = null,
    val restDurationSeconds: Int = 90,
    val isAllCompleted: Boolean = false,
    val isFinished: Boolean = false,
    val lastCompletedExerciseIndex: Int? = null,
    val lastCompletedSetIndex: Int? = null
) {
    val totalSetsCount: Int get() = exercises.sumOf { it.sets }
    val completedSetsCount: Int get() = exercises.sumOf { it.completedCount }

    /** Calorias estimadas ao vivo: (min decorridos, mín. 1) * (baseCalories / max(10, baseDuration)), arredondado. */
    val liveCalories: Double
        get() {
            val elapsed = maxOf(1.0, (System.currentTimeMillis() - sessionStartDate) / 1000.0 / 60.0)
            val rate = baseCalories / maxOf(10.0, baseDurationMinutes.toDouble())
            return swiftRound(elapsed * rate)
        }

    val currentExercise: WorkoutExercisePlan?
        get() = if (currentExerciseIndex < exercises.size) exercises[currentExerciseIndex] else exercises.lastOrNull()

    val currentSetWeightKg: Double
        get() {
            val ex = currentExercise ?: return 0.0
            val idx = currentSetIndex.coerceIn(0, maxOf(0, ex.sets - 1))
            return ex.weightForSet(idx)
        }

    val lastCompletedSetInfo: CompletedSetWeightInfo?
        get() {
            val exIdx = lastCompletedExerciseIndex
            val sIdx = lastCompletedSetIndex
            if (exIdx != null && sIdx != null && exIdx in exercises.indices) {
                val ex = exercises[exIdx]
                val safeSet = sIdx.coerceIn(0, maxOf(0, ex.sets - 1))
                return CompletedSetWeightInfo(
                    exerciseIndex = exIdx,
                    setIndex = safeSet,
                    setNumber = safeSet + 1,
                    weightKg = ex.weightForSet(safeSet),
                    exerciseName = ex.name
                )
            }
            for (i in exercises.indices.reversed()) {
                val ex = exercises[i]
                for (j in (minOf(ex.sets, ex.completedSets.size) - 1) downTo 0) {
                    if (ex.completedSets[j]) {
                        return CompletedSetWeightInfo(
                            exerciseIndex = i,
                            setIndex = j,
                            setNumber = j + 1,
                            weightKg = ex.weightForSet(j),
                            exerciseName = ex.name
                        )
                    }
                }
            }
            return null
        }

    /** Progresso geral 0..1 (iOS: `overallProgress`). */
    val overallProgress: Double
        get() = if (totalSetsCount > 0) (completedSetsCount.toDouble() / totalSetsCount).coerceIn(0.0, 1.0) else 0.0
}

/** Treino finalizado pela notificação, aguardando inserção no histórico/Health Connect (iOS: `PendingWorkoutRecord`). */
data class PendingWorkoutRecord(
    val title: String,
    val workoutTypeRaw: String,
    val durationMinutes: Int,
    val caloriesBurned: Double,
    val notes: String,
    /** epoch millis */
    val date: Long
)

/**
 * Gerenciador da sessão de treino ao vivo (rest timers, séries, calorias). Une `SharedWorkoutSessionManager`,
 * a lógica de timers de `ActiveWorkoutSessionView` e o `LiveActivityManager` do iOS (a Live Activity vira a
 * notificação ongoing de [WorkoutNotificationManager]).
 *
 * Estados observáveis: [session], [elapsedSeconds], [restRemainingSeconds].
 * Um ticker de 1 s (thread principal) atualiza o cronômetro e encerra o descanso (com vibração/som e notificação).
 *
 * Fluxo típico da tela de treino:
 * ```
 * val mgr = SharedWorkoutSessionManager.get(ctx)
 * mgr.resumeOrStart(title, "Musculação", exercises, baseCalories, baseDurationMinutes, initialRestSeconds)
 * mgr.toggleSet(exIndex, setIndex)      // marca/desmarca série (inicia descanso ao marcar)
 * mgr.skipRest() / mgr.addRestSeconds(30) / mgr.addRestSeconds(-30)
 * mgr.linkAnimation(exIndex, catalogId) // vincula GIF do catálogo
 * val record = mgr.finishWorkout()      // encerra e devolve registro p/ salvar (histórico + Health Connect)
 * mgr.cancelSession()                   // "Sair sem salvar"
 * ```
 * Ao reabrir o app: [consumePendingWorkout] devolve treino finalizado pela notificação (iOS `consumePendingWorkout`).
 */
class SharedWorkoutSessionManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("forma_workout_session", Context.MODE_PRIVATE)
    private val gson = Gson()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val notifications get() = WorkoutNotificationManager.get(appContext)

    private val lock = Any()

    private val _session = MutableStateFlow<SharedWorkoutState?>(loadSession())
    /** Sessão atual (ou `null`). */
    val session: StateFlow<SharedWorkoutState?> = _session.asStateFlow()

    private val _elapsedSeconds = MutableStateFlow(0)
    /** Segundos desde o início do treino. */
    val elapsedSeconds: StateFlow<Int> = _elapsedSeconds.asStateFlow()

    private val _restRemainingSeconds = MutableStateFlow(0)
    /** Segundos restantes do descanso (0 quando não está descansando). */
    val restRemainingSeconds: StateFlow<Int> = _restRemainingSeconds.asStateFlow()

    private val _isExpandedInApp = MutableStateFlow(false)
    /** `true` quando a tela cheia de treino ativo está expandida; `false` exibe o Mini-Player sobreposto. */
    val isExpandedInApp: StateFlow<Boolean> = _isExpandedInApp.asStateFlow()

    fun setExpandedInApp(expanded: Boolean) {
        _isExpandedInApp.value = expanded
    }

    private var ticker: Job? = null

    init {
        if (_session.value != null) startTicker()
    }

    // region Persistência

    fun loadSession(): SharedWorkoutState? {
        val json = prefs.getString(KEY_SESSION, null) ?: return null
        return runCatching { gson.fromJson(json, SharedWorkoutState::class.java) }.getOrNull()
    }

    fun saveSession(state: SharedWorkoutState) {
        prefs.edit().putString(KEY_SESSION, gson.toJson(state)).apply()
        _session.value = state
    }

    fun clearSession() {
        prefs.edit().remove(KEY_SESSION).apply()
        _session.value = null
        _restRemainingSeconds.value = 0
        _elapsedSeconds.value = 0
        _isExpandedInApp.value = false
        ticker?.cancel(); ticker = null
    }

    /** Treino finalizado pela notificação, ainda não salvo (consome e remove). */
    fun consumePendingWorkout(): PendingWorkoutRecord? {
        val json = prefs.getString(KEY_PENDING, null) ?: return null
        prefs.edit().remove(KEY_PENDING).apply()
        return runCatching { gson.fromJson(json, PendingWorkoutRecord::class.java) }.getOrNull()
    }

    // endregion

    // region Histórico de Carga Média & Recomendação Inicial por Usuário

    fun saveUserBodyWeightKg(weightKg: Double) {
        if (weightKg > 30.0) {
            prefs.edit().putFloat(KEY_USER_BODY_WEIGHT, weightKg.toFloat()).apply()
        }
    }

    fun getUserBodyWeightKg(): Double {
        val raw = prefs.getFloat(KEY_USER_BODY_WEIGHT, 75f).toDouble()
        return if (raw > 30.0) raw else 75.0
    }

    fun lastAverageWeightKg(exerciseName: String, animationId: String? = null): Double? {
        val rawJson = prefs.getString(KEY_HISTORY_WEIGHTS, null) ?: return null
        val obj = runCatching { com.google.gson.JsonParser.parseString(rawJson).asJsonObject }.getOrNull() ?: return null
        if (!animationId.isNullOrBlank()) {
            val byId = obj.dbl("id_$animationId")
            if (byId != null && byId > 0.0) return kotlin.math.round(byId * 2.0) / 2.0
        }
        val key = WorkoutExercisePlan.normalizedExerciseKey(exerciseName)
        val byName = obj.dbl(key)
        if (byName != null && byName > 0.0) return kotlin.math.round(byName * 2.0) / 2.0
        return null
    }

    fun recordExerciseWeightsHistory(exercises: List<WorkoutExercisePlan>) {
        val rawJson = prefs.getString(KEY_HISTORY_WEIGHTS, null)
        val obj = runCatching {
            if (rawJson.isNullOrBlank()) com.google.gson.JsonObject()
            else com.google.gson.JsonParser.parseString(rawJson).asJsonObject
        }.getOrDefault(com.google.gson.JsonObject())

        for (ex in exercises) {
            val avg = ex.averageWeightKg
            if (avg <= 0.0) continue
            val key = WorkoutExercisePlan.normalizedExerciseKey(ex.name)
            if (key.isNotEmpty()) obj.addProperty(key, avg)
            if (!ex.animationId.isNullOrBlank()) obj.addProperty("id_${ex.animationId}", avg)
        }
        prefs.edit().putString(KEY_HISTORY_WEIGHTS, obj.toString()).apply()
    }

    fun prepareExercisesWithInitialWeights(
        exercises: List<WorkoutExercisePlan>,
        userBodyWeightKg: Double? = null
    ): List<WorkoutExercisePlan> {
        if (userBodyWeightKg != null && userBodyWeightKg > 30.0) {
            saveUserBodyWeightKg(userBodyWeightKg)
        }
        val bw = userBodyWeightKg?.takeIf { it > 30.0 } ?: getUserBodyWeightKg()
        return exercises.map { ex ->
            val safeSets = maxOf(1, ex.sets)
            val lastAvg = lastAverageWeightKg(ex.name, ex.animationId)
            val initialKg = when {
                lastAvg != null && lastAvg > 0.0 -> lastAvg
                else -> WorkoutExercisePlan.parseWeightKg(ex.suggestedWeight)
                    ?: WorkoutExercisePlan.recommendedDefaultWeightKg(ex.name, ex.targetMuscle, bw)
            }
            val weights = if (ex.setWeights.size == safeSets && ex.setWeights.any { it > 0.0 } && lastAvg == null) {
                ex.setWeights
            } else {
                List(safeSets) { initialKg }
            }
            ex.copy(
                setWeights = weights,
                suggestedWeight = WorkoutExercisePlan.formatWeightKg(weights.firstOrNull() ?: initialKg)
            )
        }
    }

    // endregion

    // region Ciclo de vida da sessão

    /**
     * Se já existe sessão não finalizada com o mesmo [title] retoma (iOS `syncFromSharedSession`); caso contrário
     * inicia uma nova (iOS `startNewSession`).
     */
    fun resumeOrStart(
        title: String,
        workoutTypeRaw: String,
        exercises: List<WorkoutExercisePlan>,
        baseCalories: Double = 350.0,
        baseDurationMinutes: Int = 50,
        initialRestSeconds: Int = 90,
        targetMuscles: String = "",
        hypertrophyFocus: String = "",
        userBodyWeightKg: Double? = null
    ): SharedWorkoutState {
        val existing = loadSession()
        if (existing != null && !existing.isFinished && existing.title == title) {
            val merged = existing.copy(
                targetMuscles = targetMuscles.ifEmpty { existing.targetMuscles },
                hypertrophyFocus = hypertrophyFocus.ifEmpty { existing.hypertrophyFocus }
            )
            _isExpandedInApp.value = true
            publish(merged)
            startTicker()
            return merged
        }
        return startSession(
            title = title,
            workoutTypeRaw = workoutTypeRaw,
            exercises = exercises,
            baseCalories = baseCalories,
            baseDurationMinutes = baseDurationMinutes,
            initialRestSeconds = initialRestSeconds,
            targetMuscles = targetMuscles,
            hypertrophyFocus = hypertrophyFocus,
            userBodyWeightKg = userBodyWeightKg
        )
    }

    fun startSession(
        title: String,
        workoutTypeRaw: String,
        exercises: List<WorkoutExercisePlan>,
        baseCalories: Double = 350.0,
        baseDurationMinutes: Int = 50,
        initialRestSeconds: Int = 90,
        targetMuscles: String = "",
        hypertrophyFocus: String = "",
        userBodyWeightKg: Double? = null
    ): SharedWorkoutState {
        notifications.cancelRestNotification()
        val prepared = prepareExercisesWithInitialWeights(exercises, userBodyWeightKg)
        val state = SharedWorkoutState(
            title = title,
            workoutTypeRaw = workoutTypeRaw,
            targetMuscles = targetMuscles,
            hypertrophyFocus = hypertrophyFocus,
            exercises = prepared,
            baseCalories = baseCalories,
            baseDurationMinutes = baseDurationMinutes,
            restDurationSeconds = if (initialRestSeconds > 0) initialRestSeconds else 90,
            isAllCompleted = prepared.isNotEmpty() && prepared.all { it.isAllSetsCompleted }
        )
        _isExpandedInApp.value = true
        publish(state)
        startTicker()
        return state
    }

    /** Salva, atualiza flows e notificação ongoing. */
    private fun publish(state: SharedWorkoutState) {
        saveSession(state)
        _elapsedSeconds.value = maxOf(0, ((System.currentTimeMillis() - state.sessionStartDate) / 1000).toInt())
        _restRemainingSeconds.value = remainingOf(state)
        notifications.showWorkoutOngoing(state)
    }

    private fun remainingOf(state: SharedWorkoutState): Int {
        val end = state.restEndDate
        if (!state.isResting || end == null) return 0
        return maxOf(0, ((end - System.currentTimeMillis()) / 1000).toInt())
    }

    // endregion

    // region Séries e Cargas (in-app e Notificação / Mini-Player / PiP)

    /**
     * Marca/desmarca a série (iOS `toggleSetCompletion`). Ao marcar, inicia o descanso do exercício
     * (`restSeconds` ou o descanso inicial) mostrando a próxima série/exercício e registrando a série que passou.
     */
    fun toggleSet(exerciseIndex: Int, setIndex: Int) = synchronized(lock) {
        val s = _session.value ?: return@synchronized
        if (exerciseIndex !in s.exercises.indices) return@synchronized
        val ex = s.exercises[exerciseIndex]
        val sets = ex.completedSets.toMutableList()
        while (sets.size < ex.sets) sets.add(false)
        if (setIndex !in sets.indices) return@synchronized

        val willBeDone = !sets[setIndex]
        sets[setIndex] = willBeDone
        val newExercises = s.exercises.toMutableList().also { it[exerciseIndex] = ex.copy(completedSets = sets) }
        var state = s.copy(exercises = newExercises, currentExerciseIndex = exerciseIndex)

        if (willBeDone) {
            state = state.copy(lastCompletedExerciseIndex = exerciseIndex, lastCompletedSetIndex = setIndex)
            val nextSet: Int
            val nextName: String
            if (setIndex + 1 < ex.sets) {
                nextSet = setIndex + 2; nextName = ex.name
            } else if (exerciseIndex + 1 < newExercises.size) {
                nextSet = 1; nextName = newExercises[exerciseIndex + 1].name
            } else {
                nextSet = ex.sets; nextName = ex.name
            }
            val targetRest = if (ex.restSeconds > 0) ex.restSeconds else s.restDurationSeconds
            state = withRest(state, targetRest)
            state = withProgressIndices(state)
            publish(state)
            notifications.scheduleRestNotification(targetRest, nextName, nextSet, ex.weightForSet(setIndex))
        } else {
            state = withProgressIndices(state.copy(isResting = false, restStartDate = null, restEndDate = null))
            publish(state)
            notifications.cancelRestNotification()
        }
    }

    /** Ajusta a carga (kg) de uma série específica e propaga para as séries seguintes não concluídas do exercício. */
    fun adjustSetWeight(exerciseIndex: Int, setIndex: Int, deltaKg: Double) = synchronized(lock) {
        val s = _session.value ?: return@synchronized
        if (exerciseIndex !in s.exercises.indices) return@synchronized
        val ex = s.exercises[exerciseIndex]
        val safeSets = maxOf(1, ex.sets)
        val fallback = ex.weightForSet(setIndex)
        val weights = ex.setWeights.toMutableList()
        while (weights.size < safeSets) weights.add(fallback)
        if (setIndex !in weights.indices) return@synchronized

        val updated = maxOf(0.0, kotlin.math.round((weights[setIndex] + deltaKg) * 2.0) / 2.0)
        weights[setIndex] = updated
        for (idx in (setIndex + 1) until safeSets) {
            val done = ex.completedSets.getOrElse(idx) { false }
            if (!done) weights[idx] = updated
        }
        val updatedEx = ex.copy(
            setWeights = weights,
            suggestedWeight = WorkoutExercisePlan.formatWeightKg(updated)
        )
        val newExercises = s.exercises.toMutableList().also { it[exerciseIndex] = updatedEx }
        publish(s.copy(exercises = newExercises))
    }

    /**
     * Aumenta ou reduz a carga utilizada na série que passou (ou na série atual caso nenhuma tenha sido concluída ainda).
     * Usado pelos botões `-2kg` / `+2kg` da Notificação, Mini-Player e Picture-in-Picture.
     */
    fun adjustRecentSetWeight(deltaKg: Double) = synchronized(lock) {
        val s = _session.value ?: loadSession() ?: return@synchronized
        if (s.exercises.isEmpty()) return@synchronized
        val lastInfo = s.lastCompletedSetInfo
        val targetExIdx: Int
        val targetSetIdx: Int
        if (lastInfo != null) {
            targetExIdx = lastInfo.exerciseIndex
            targetSetIdx = lastInfo.setIndex
        } else {
            targetExIdx = s.currentExerciseIndex.coerceIn(0, s.exercises.size - 1)
            val ex = s.exercises[targetExIdx]
            targetSetIdx = s.currentSetIndex.coerceIn(0, maxOf(0, ex.sets - 1))
        }
        adjustSetWeight(targetExIdx, targetSetIdx, deltaKg)
    }

    /** Vincula um exercício ao GIF do catálogo (iOS: `exercises[idx].animationId = item.id`). */
    fun linkAnimation(exerciseIndex: Int, animationId: String) = synchronized(lock) {
        val s = _session.value ?: return@synchronized
        if (exerciseIndex !in s.exercises.indices) return@synchronized
        val list = s.exercises.toMutableList()
        list[exerciseIndex] = list[exerciseIndex].copy(animationId = animationId)
        publish(s.copy(exercises = list))
    }

    private fun withRest(state: SharedWorkoutState, seconds: Int): SharedWorkoutState {
        val now = System.currentTimeMillis()
        return state.copy(
            isResting = true,
            restStartDate = now,
            restEndDate = now + seconds * 1000L,
            restDurationSeconds = seconds
        )
    }

    /** Equivale a `saveCurrentStateToShared`: índice/série atual e `isAllCompleted`. */
    private fun withProgressIndices(state: SharedWorkoutState): SharedWorkoutState {
        val isAllDone = state.exercises.all { it.isAllSetsCompleted }
        val safeIndex = minOf(maxOf(0, state.currentExerciseIndex), maxOf(0, state.exercises.size - 1))
        val currentSetIdx = if (state.exercises.isEmpty()) 0 else maxOf(0, state.exercises[safeIndex].completedCount)
        return state.copy(currentExerciseIndex = safeIndex, currentSetIndex = currentSetIdx, isAllCompleted = isAllDone)
    }

    // endregion

    // region Descanso

    /** Encerra o descanso imediatamente (iOS `stopRestTimer` / `skipRestFromLiveActivity`). */
    fun skipRest() = synchronized(lock) {
        val s = _session.value ?: return@synchronized
        notifications.cancelRestNotification()
        publish(s.copy(isResting = false, restStartDate = null, restEndDate = null))
    }

    /**
     * Ajusta o descanso em [deltaSeconds] (iOS `adjustRestSeconds`; +30 pela Live Activity). Se restar <= 1 s,
     * encerra o descanso.
     */
    fun addRestSeconds(deltaSeconds: Int = 30) = synchronized(lock) {
        val s = _session.value ?: return@synchronized
        if (!s.isResting) return@synchronized
        val currentEnd = s.restEndDate ?: System.currentTimeMillis()
        val newEnd = currentEnd + deltaSeconds * 1000L
        val remaining = ((newEnd - System.currentTimeMillis()) / 1000).toInt()
        if (remaining <= 1) {
            skipRest()
            return@synchronized
        }
        val curEx = s.currentExercise
        val activeSet = minOf(curEx?.sets ?: 3, maxOf(1, (curEx?.completedCount ?: 0) + 1))
        val lastWeight = s.lastCompletedSetInfo?.weightKg ?: s.currentSetWeightKg
        publish(
            s.copy(
                restEndDate = newEnd,
                restDurationSeconds = maxOf(remaining, s.restDurationSeconds + deltaSeconds)
            )
        )
        notifications.scheduleRestNotification(remaining, curEx?.name ?: "Treino", activeSet, lastWeight)
    }

    /** Chamado pelo alarme de descanso (app possivelmente em segundo plano). */
    internal fun onRestAlarmFired() = synchronized(lock) {
        val s = _session.value ?: return@synchronized
        if (s.isResting) publish(s.copy(isResting = false, restStartDate = null, restEndDate = null))
    }

    // endregion

    // region Ações da notificação (iOS: App Intents da Live Activity)

    /** Conclui a série atual a partir da notificação (iOS `completeCurrentSetFromLiveActivity`). */
    fun completeCurrentSetFromNotification() = synchronized(lock) {
        val s = _session.value ?: return@synchronized
        val exIndex = s.currentExerciseIndex
        if (exIndex !in s.exercises.indices) return@synchronized

        val ex = s.exercises[exIndex]
        val sets = ex.completedSets.toMutableList()
        while (sets.size < ex.sets) sets.add(false)
        val sIndex = minOf(s.currentSetIndex, ex.sets - 1)
        sets[sIndex] = true
        val newExercises = s.exercises.toMutableList().also { it[exIndex] = ex.copy(completedSets = sets) }

        val allDone = newExercises.all { it.isAllSetsCompleted }
        var state = s.copy(
            exercises = newExercises,
            isAllCompleted = allDone,
            lastCompletedExerciseIndex = exIndex,
            lastCompletedSetIndex = sIndex
        )

        if (allDone) {
            state = state.copy(isResting = false, restStartDate = null, restEndDate = null)
            notifications.cancelRestNotification()
            publish(state)
        } else {
            val targetRest = if (ex.restSeconds > 0) ex.restSeconds else s.restDurationSeconds
            state = withRest(state, targetRest)
            // Avança para próxima série ou próximo exercício
            if (sIndex + 1 < ex.sets) {
                state = state.copy(currentSetIndex = sIndex + 1)
            } else if (exIndex + 1 < newExercises.size) {
                state = state.copy(currentExerciseIndex = exIndex + 1, currentSetIndex = 0)
            }
            publish(state)
            val nextEx = state.currentExercise ?: ex
            val nextSet = minOf(state.currentSetIndex + 1, nextEx.sets)
            notifications.scheduleRestNotification(targetRest, nextEx.name, nextSet, ex.weightForSet(sIndex))
        }
    }

    // endregion

    // region Finalização

    /**
     * Finaliza o treino (iOS `finishWorkoutFromLiveActivity`/`finishAndSaveWorkout`): cancela descanso e notificação,
     * grava a carga média realizada de cada exercício no histórico local, limpa a sessão e devolve o registro.
     */
    fun finishWorkout(storePending: Boolean = false): PendingWorkoutRecord? = synchronized(lock) {
        val s = _session.value ?: loadSession() ?: return@synchronized null
        notifications.cancelRestNotification()
        recordExerciseWeightsHistory(s.exercises)

        val finalSeconds = maxOf(0, ((System.currentTimeMillis() - s.sessionStartDate) / 1000).toInt())
        val weightsSummary = s.exercises
            .filter { it.averageWeightKg > 0.0 }
            .take(4)
            .joinToString(" • ") { "${it.name}: ${WorkoutExercisePlan.formatWeightKg(it.averageWeightKg)}" }
        val notes = if (weightsSummary.isEmpty()) {
            "Checklist: ${s.completedSetsCount} de ${s.totalSetsCount} séries concluídas em ${formatTime(finalSeconds)}."
        } else {
            "Checklist: ${s.completedSetsCount}/${s.totalSetsCount} séries (${formatTime(finalSeconds)}). Cargas: $weightsSummary"
        }
        val record = PendingWorkoutRecord(
            title = s.title,
            workoutTypeRaw = s.workoutTypeRaw,
            durationMinutes = maxOf(1, finalSeconds / 60),
            caloriesBurned = s.liveCalories,
            notes = notes,
            date = System.currentTimeMillis()
        )
        if (storePending) prefs.edit().putString(KEY_PENDING, gson.toJson(record)).apply()
        clearSession()
        notifications.cancelWorkoutOngoing()
        record
    }

    /**
     * Descarta o treino sem salvar ("Sair sem salvar"): zera timers, sessão e notificação
     * (iOS `stopAllTimers`).
     */
    fun cancelSession() = synchronized(lock) {
        notifications.cancelRestNotification()
        clearSession()
        notifications.cancelWorkoutOngoing()
    }

    /** Consome o registro pendente armazenado por [finishWorkout] quando a UI já o tratou diretamente. */
    fun discardPendingWorkout() {
        prefs.edit().remove(KEY_PENDING).apply()
    }

    // endregion

    // region Ticker

    private fun startTicker() {
        if (ticker?.isActive == true) return
        ticker = scope.launch {
            while (isActive) {
                val s = _session.value
                if (s == null) break
                _elapsedSeconds.value = maxOf(0, ((System.currentTimeMillis() - s.sessionStartDate) / 1000).toInt())
                if (s.isResting && s.restEndDate != null) {
                    val remaining = remainingOf(s)
                    if (remaining <= 0) {
                        // Descanso terminou com app aberto: encerra, vibra e notifica
                        val curEx = s.currentExercise
                        val nextSet = minOf(s.currentSetIndex + 1, curEx?.sets ?: 3)
                        val lastWeight = s.lastCompletedSetInfo?.weightKg ?: s.currentSetWeightKg
                        notifications.cancelRestNotification()
                        publish(s.copy(isResting = false, restStartDate = null, restEndDate = null))
                        notifications.triggerRestFinishedHapticsAndSound()
                        notifications.showRestFinishedNotification(curEx?.name ?: "Treino", nextSet, lastWeight)
                    } else {
                        _restRemainingSeconds.value = remaining
                    }
                } else {
                    _restRemainingSeconds.value = 0
                }
                delay(1000)
            }
        }
    }

    // endregion

    companion object {
        private const val KEY_SESSION = "active_workout_session_state"
        private const val KEY_PENDING = "pending_finished_workout"
        private const val KEY_HISTORY_WEIGHTS = "exercise_last_average_weights_v1"
        private const val KEY_USER_BODY_WEIGHT = "user_body_weight_kg_v1"

        /** Formato MM:SS usado nas notas e no cronômetro. */
        fun formatTime(totalSeconds: Int): String =
            String.format(java.util.Locale.US, "%02d:%02d", totalSeconds / 60, totalSeconds % 60)

        @Volatile
        private var instance: SharedWorkoutSessionManager? = null

        fun get(context: Context): SharedWorkoutSessionManager =
            instance ?: synchronized(this) {
                instance ?: SharedWorkoutSessionManager(context).also { instance = it }
            }
    }
}

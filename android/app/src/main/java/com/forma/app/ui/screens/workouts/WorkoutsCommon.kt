package com.forma.app.ui.screens.workouts

import android.Manifest
import android.content.Context
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBike
import androidx.compose.material.icons.filled.DirectionsRun
import androidx.compose.material.icons.filled.DirectionsWalk
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.Pool
import androidx.compose.material.icons.filled.SportsGymnastics
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import com.forma.app.FormaApplication
import com.forma.app.data.model.WorkoutEntry
import com.forma.app.data.model.WorkoutExerciseItem
import com.forma.app.data.model.WorkoutRoutine
import com.forma.app.data.model.WorkoutType
import com.forma.app.data.repository.FormaRepository
import com.forma.app.data.services.PendingWorkoutRecord
import com.forma.app.data.services.SharedWorkoutSessionManager
import com.forma.app.data.services.WorkoutExercisePlan
import com.forma.app.data.services.WorkoutNotificationManager
import java.text.Normalizer
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Repositório global (as sheets de Treino podem ser hospedadas por outras telas, como o Home). */
internal fun workoutsRepository(): FormaRepository = FormaApplication.instance.repository

internal val PtBr: Locale = Locale("pt", "BR")

/** Ícone Material equivalente a `WorkoutType.icon` (SF Symbols). */
internal fun workoutTypeIcon(type: WorkoutType): ImageVector = when (type) {
    WorkoutType.BODYBUILDING -> Icons.Filled.FitnessCenter
    WorkoutType.RUNNING -> Icons.Filled.DirectionsRun
    WorkoutType.WALKING -> Icons.Filled.DirectionsWalk
    WorkoutType.CYCLING -> Icons.Filled.DirectionsBike
    WorkoutType.FUNCTIONAL -> Icons.Filled.SportsGymnastics
    WorkoutType.SWIMMING -> Icons.Filled.Pool
    WorkoutType.OTHER -> Icons.Filled.LocalFireDepartment
}

/** `Date.formatted(.dateTime.day().month())` em pt_BR (ex.: "9 de nov."). */
internal val DayMonthFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("d 'de' MMM", PtBr)

internal fun String.capitalizedPt(): String =
    lowercase(PtBr).split(" ").joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(PtBr) } }

@Suppress("unused")
internal fun String.foldedLower(): String =
    Normalizer.normalize(this, Normalizer.Form.NFD).replace(Regex("\\p{Mn}+"), "").lowercase()

internal fun WorkoutExerciseItem.toPlan(): WorkoutExercisePlan = WorkoutExercisePlan(
    id = id,
    name = name,
    sets = sets,
    reps = reps,
    suggestedWeight = suggestedWeight,
    targetMuscle = targetMuscle,
    executionTip = executionTip,
    restSeconds = restSeconds,
    completedSets = List(maxOf(1, sets)) { index -> completedSets.getOrElse(index) { false } },
    setWeights = setWeights,
    animationId = animationId
)

internal fun WorkoutExercisePlan.toItem(): WorkoutExerciseItem = WorkoutExerciseItem(
    id = id,
    name = name,
    sets = sets,
    reps = reps,
    suggestedWeight = suggestedWeight,
    targetMuscle = targetMuscle,
    executionTip = executionTip,
    restSeconds = restSeconds,
    completedSets = completedSets.toMutableList(),
    setWeights = setWeights,
    animationId = animationId
)

/** Dados para iniciar uma sessão (iOS: parâmetros do `ActiveWorkoutSessionView`). */
data class ActiveWorkoutRequest(
    val title: String,
    val workoutType: WorkoutType = WorkoutType.BODYBUILDING,
    val targetMuscles: String = "",
    val hypertrophyFocus: String = "",
    val baseCalories: Double = 350.0,
    val baseDurationMinutes: Int = 50,
    val initialRestSeconds: Int = 90,
    val exercises: List<WorkoutExercisePlan>,
    val routineToUpdate: WorkoutRoutine? = null
) {
    companion object {
        fun fromRoutine(routine: WorkoutRoutine) = ActiveWorkoutRequest(
            title = routine.title,
            workoutType = routine.workoutTypeEnum,
            targetMuscles = routine.targetMuscles,
            hypertrophyFocus = routine.hypertrophyFocus,
            baseCalories = routine.estimatedCalories,
            baseDurationMinutes = routine.estimatedDurationMinutes,
            initialRestSeconds = routine.recommendedRestSeconds,
            exercises = routine.exercises.map { it.toPlan() },
            routineToUpdate = routine
        )
    }
}

/**
 * Pede `POST_NOTIFICATIONS` (Android 13+) antes de iniciar a sessão para que a notificação ongoing e a de descanso
 * possam ser exibidas. O treino inicia mesmo se o usuário negar. Uso: `val gate = rememberNotificationPermissionGate()`;
 * `gate { startSession() }`.
 */
@Composable
fun rememberNotificationPermissionGate(): (onProceed: () -> Unit) -> Unit {
    val context = LocalContext.current
    val pending = remember { arrayOfNulls<() -> Unit>(1) }
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        val action = pending[0]
        pending[0] = null
        action?.invoke()
    }
    val currentContext by rememberUpdatedState(context)
    return remember(launcher) {
        { onProceed ->
            val manager = WorkoutNotificationManager.get(currentContext)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !manager.hasNotificationPermission()) {
                pending[0] = onProceed
                launcher.launch(Manifest.permission.POST_NOTIFICATIONS)
            } else {
                onProceed()
            }
        }
    }
}

/**
 * Equivalente a `FormaApp.checkPendingWorkout()` (iOS): se um treino foi finalizado pela notificação enquanto o app
 * estava em segundo plano, salva no histórico (+ Health Connect) e atualiza `lastTrainedAt` da ficha de mesmo título.
 * Seguro de chamar várias vezes (consome o registro uma única vez). Retorna `true` quando salvou algo.
 */
suspend fun consumePendingWorkoutIfAny(context: Context, repository: FormaRepository): Boolean {
    val pending: PendingWorkoutRecord = SharedWorkoutSessionManager.get(context).consumePendingWorkout() ?: return false
    saveFinishedWorkoutRecord(repository, pending)
    return true
}

/** Salva um [PendingWorkoutRecord] (remote-first com fallback local), atualizando a ficha treinada se existir. */
internal suspend fun saveFinishedWorkoutRecord(
    repository: FormaRepository,
    record: PendingWorkoutRecord,
    routineToUpdate: WorkoutRoutine? = null
) {
    val routine = routineToUpdate ?: repository.workoutRoutines.value.firstOrNull { it.title == record.title }
    val entry = WorkoutEntry(
        title = record.title,
        workoutType = WorkoutType.fromRaw(record.workoutTypeRaw).rawValue,
        durationMinutes = record.durationMinutes,
        caloriesBurned = record.caloriesBurned,
        notes = record.notes,
        workoutDate = record.date
    )
    try {
        repository.insertWorkoutEntryRemoteFirst(entry, routineTrained = routine)
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (_: Exception) {
        // Sem internet no fim da sessão: salva via wrapper local para não perder o treino concluído
        repository.insertWorkoutEntry(entry, routine)
    }
}

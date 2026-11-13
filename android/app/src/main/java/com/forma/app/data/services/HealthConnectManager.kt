package com.forma.app.data.services

import android.content.Context
import android.content.pm.PackageManager
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.ActiveCaloriesBurnedRecord
import androidx.health.connect.client.records.BasalMetabolicRateRecord
import androidx.health.connect.client.records.DistanceRecord
import androidx.health.connect.client.records.ExerciseSessionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.records.metadata.DataOrigin
import androidx.health.connect.client.records.metadata.Metadata
import androidx.health.connect.client.request.AggregateRequest
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import androidx.health.connect.client.units.Energy
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.reflect.KClass

/** Tipos de treino (títulos pt-BR idênticos ao enum `WorkoutType` do iOS). */
object WorkoutTypes {
    const val BODYBUILDING = "Musculação"
    const val RUNNING = "Corrida"
    const val WALKING = "Caminhada"
    const val CYCLING = "Ciclismo"
    const val FUNCTIONAL = "Funcional / HIIT"
    const val SWIMMING = "Natação"
    const val OTHER = "Outro"
}

/** Treino externo lido do Health Connect (Strava, Garmin, Samsung Health etc.) (iOS: `ExternalWorkoutSample`). */
data class ExternalWorkoutSample(
    val workoutType: String,
    val title: String,
    val sourceName: String,
    val durationMinutes: Int,
    val caloriesBurned: Double,
    /** epoch millis */
    val startDate: Long
) {
    /** Chave de deduplicação igual ao iOS: "<título>_<epoch segundos>". */
    val uniqueKey: String get() = "${title}_${startDate / 1000}"
}

/**
 * Integração com o Health Connect (equivalente do `HealthManager`/HealthKit do iOS).
 *
 * - [isAvailable] é `false` quando o Health Connect não está instalado/suportado; todas as leituras devolvem 0 /
 *   lista vazia e as escritas retornam `false` (no-op gracioso).
 * - Permissões: use `PermissionController.createRequestPermissionResultContract()` ([permissionContract]) com
 *   `launcher.launch(HealthConnectManager.PERMISSIONS)`; depois consulte [hasAllPermissions] / [hasAnyReadPermission].
 * - Treinos gravados pelo Forma ficam com o `dataOrigin` = pacote do app e são ignorados na leitura de externos
 *   (iOS: `HKMetadataKeyWorkoutBrandName == "Forma"`).
 * - Barreira de importação por conta ([setImportCutoff]/[importCutoff]) idêntica ao
 *   `FormaDataStore.setHealthKitImportCutoff`: impede importar treinos antigos do aparelho para uma conta nova.
 *   Chame `setImportCutoff(userId, now)` após cadastro/login de conta nova.
 */
class HealthConnectManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences("forma_health_prefs", Context.MODE_PRIVATE)

    /** Status do SDK (`HealthConnectClient.SDK_AVAILABLE`, `SDK_UNAVAILABLE`, `SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED`). */
    val sdkStatus: Int
        get() = runCatching { HealthConnectClient.getSdkStatus(appContext) }.getOrDefault(HealthConnectClient.SDK_UNAVAILABLE)

    /** `true` quando o Health Connect está disponível neste aparelho. */
    val isAvailable: Boolean get() = sdkStatus == HealthConnectClient.SDK_AVAILABLE

    private val client: HealthConnectClient? by lazy {
        if (isAvailable) runCatching { HealthConnectClient.getOrCreate(appContext) }.getOrNull() else null
    }

    /** Contrato para pedir permissões (`rememberLauncherForActivityResult(manager.permissionContract)`). */
    val permissionContract get() = PermissionController.createRequestPermissionResultContract()

    // region Permissões

    suspend fun grantedPermissions(): Set<String> =
        runCatching { client?.permissionController?.getGrantedPermissions() ?: emptySet() }.getOrDefault(emptySet())

    suspend fun hasAllPermissions(): Boolean = grantedPermissions().containsAll(PERMISSIONS)

    suspend fun hasAnyReadPermission(): Boolean = grantedPermissions().any { it in READ_PERMISSIONS }

    // endregion

    // region Leituras do dia

    private fun todayRange(): TimeRangeFilter {
        val start = LocalDate.now().atStartOfDay(ZoneId.systemDefault()).toInstant()
        return TimeRangeFilter.between(start, Instant.now())
    }

    /** Passos de hoje (iOS: `fetchTodaySteps`). */
    suspend fun fetchTodaySteps(): Double = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext 0.0
        runCatching {
            val r = c.aggregate(AggregateRequest(setOf(StepsRecord.COUNT_TOTAL), todayRange()))
            (r[StepsRecord.COUNT_TOTAL] ?: 0L).toDouble()
        }.getOrDefault(0.0)
    }

    /** Calorias ativas de hoje, kcal (iOS: `fetchTodayCalories`). */
    suspend fun fetchTodayCalories(): Double = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext 0.0
        runCatching {
            val r = c.aggregate(AggregateRequest(setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL), todayRange()))
            r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        }.getOrDefault(0.0)
    }

    /** Calorias basais de hoje, kcal (iOS: `fetchTodayBasalCalories`). */
    suspend fun fetchTodayBasalCalories(): Double = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext 0.0
        runCatching {
            val r = c.aggregate(AggregateRequest(setOf(BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL), todayRange()))
            r[BasalMetabolicRateRecord.BASAL_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        }.getOrDefault(0.0)
    }

    /** Distância caminhada/corrida de hoje, km (iOS: `fetchTodayDistance`). */
    suspend fun fetchTodayDistance(): Double = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext 0.0
        runCatching {
            val r = c.aggregate(AggregateRequest(setOf(DistanceRecord.DISTANCE_TOTAL), todayRange()))
            (r[DistanceRecord.DISTANCE_TOTAL]?.inMeters ?: 0.0) / 1000.0
        }.getOrDefault(0.0)
    }

    /** Minutos de exercício de hoje (iOS: `fetchTodayExerciseTime`). */
    suspend fun fetchTodayExerciseTime(): Double = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext 0.0
        runCatching {
            val r = c.aggregate(AggregateRequest(setOf(ExerciseSessionRecord.EXERCISE_DURATION_TOTAL), todayRange()))
            (r[ExerciseSessionRecord.EXERCISE_DURATION_TOTAL]?.toMinutes() ?: 0L).toDouble()
        }.getOrDefault(0.0)
    }

    // endregion

    // region Gravar treino

    /**
     * Salva o treino no Health Connect (sessão de exercício + calorias ativas), terminando em [dateMillis]
     * (iOS: `saveWorkout`). Retorna `true` em sucesso ou quando o Health Connect não está disponível
     * (no-op gracioso, igual ao iOS que devolve sucesso sem HealthKit).
     */
    suspend fun saveWorkout(
        title: String,
        workoutType: String,
        caloriesBurned: Double,
        durationMinutes: Int,
        dateMillis: Long = System.currentTimeMillis()
    ): Boolean = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext true
        runCatching {
            val validDuration = maxOf(1, durationMinutes)
            val end = Instant.ofEpochMilli(dateMillis)
            val start = end.minus(Duration.ofMinutes(validDuration.toLong()))
            val offset = ZoneId.systemDefault().rules.getOffset(end)

            val records = mutableListOf<androidx.health.connect.client.records.Record>(
                ExerciseSessionRecord(
                    startTime = start,
                    startZoneOffset = offset,
                    endTime = end,
                    endZoneOffset = offset,
                    metadata = Metadata(),
                    exerciseType = exerciseTypeFor(workoutType),
                    title = title
                )
            )
            if (caloriesBurned > 0) {
                records.add(
                    ActiveCaloriesBurnedRecord(
                        startTime = start,
                        startZoneOffset = offset,
                        endTime = end,
                        endZoneOffset = offset,
                        energy = Energy.kilocalories(caloriesBurned),
                        metadata = Metadata()
                    )
                )
            }
            c.insertRecords(records)
            true
        }.getOrDefault(false)
    }

    // endregion

    // region Treinos externos

    /**
     * Lê treinos externos dos últimos [days] dias (iOS: `fetchExternalWorkoutSamples`), ignorando os salvos
     * pelo próprio Forma.
     */
    suspend fun fetchExternalWorkoutSamples(days: Int = 7): List<ExternalWorkoutSample> = withContext(Dispatchers.IO) {
        val c = client ?: return@withContext emptyList()
        runCatching {
            val end = Instant.now()
            val start = end.minus(Duration.ofDays(days.toLong()))
            val response = c.readRecords(
                ReadRecordsRequest(
                    recordType = ExerciseSessionRecord::class,
                    timeRangeFilter = TimeRangeFilter.between(start, end),
                    ascendingOrder = false,
                    pageSize = 100
                )
            )
            response.records
                .filter { it.metadata.dataOrigin.packageName != appContext.packageName }
                .map { mapExternalSample(c, it) }
        }.getOrDefault(emptyList())
    }

    private suspend fun mapExternalSample(c: HealthConnectClient, record: ExerciseSessionRecord): ExternalWorkoutSample {
        val durationMinutes = maxOf(1, Duration.between(record.startTime, record.endTime).toMinutes().toInt())
        val origin = record.metadata.dataOrigin.packageName
        val sourceName = appLabel(origin)

        // Calorias ativas dentro da sessão (da mesma origem); fallback 8 kcal/min como no iOS
        val calories = runCatching {
            val r = c.aggregate(
                AggregateRequest(
                    metrics = setOf(ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL),
                    timeRangeFilter = TimeRangeFilter.between(record.startTime, record.endTime),
                    dataOriginFilter = setOf(DataOrigin(origin))
                )
            )
            r[ActiveCaloriesBurnedRecord.ACTIVE_CALORIES_TOTAL]?.inKilocalories ?: 0.0
        }.getOrDefault(0.0)
        val kcal = if (calories > 0) calories else (durationMinutes * 8).toDouble()

        val (type, title) = when (record.exerciseType) {
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING,
            ExerciseSessionRecord.EXERCISE_TYPE_BIKING_STATIONARY ->
                WorkoutTypes.CYCLING to "Pedalada / Bike ($sourceName)"
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING,
            ExerciseSessionRecord.EXERCISE_TYPE_RUNNING_TREADMILL ->
                WorkoutTypes.RUNNING to "Corrida ($sourceName)"
            ExerciseSessionRecord.EXERCISE_TYPE_WALKING ->
                WorkoutTypes.WALKING to "Caminhada ($sourceName)"
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL,
            ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_OPEN_WATER ->
                WorkoutTypes.SWIMMING to "Natação ($sourceName)"
            ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING,
            ExerciseSessionRecord.EXERCISE_TYPE_WEIGHTLIFTING ->
                WorkoutTypes.BODYBUILDING to "Musculação ($sourceName)"
            else -> WorkoutTypes.OTHER to "Treino ($sourceName)"
        }
        return ExternalWorkoutSample(
            workoutType = type,
            title = title,
            sourceName = sourceName,
            durationMinutes = durationMinutes,
            caloriesBurned = kcal,
            startDate = record.startTime.toEpochMilli()
        )
    }

    private fun appLabel(packageName: String): String = runCatching {
        val pm = appContext.packageManager
        pm.getApplicationLabel(pm.getApplicationInfo(packageName, 0)).toString()
    }.getOrElse { if (it is PackageManager.NameNotFoundException) packageName else packageName }

    // endregion

    // region Barreira de importação por conta

    fun setImportCutoff(userId: String, dateMillis: Long) {
        if (userId.isEmpty()) return
        prefs.edit().putLong("forma_healthkit_cutoff_$userId", dateMillis).apply()
    }

    /** Data de corte (epoch millis) ou `null` se nunca definida. */
    fun importCutoff(userId: String): Long? {
        if (userId.isEmpty()) return null
        val key = "forma_healthkit_cutoff_$userId"
        if (!prefs.contains(key)) return null
        val ts = prefs.getLong(key, 0L)
        return if (ts > 0) ts else null
    }

    /**
     * Treinos externos prontos para importar (iOS: `syncExternalHealthWorkoutsInBackground`), com a mesma lógica:
     *  - se há cutoff, descarta amostras anteriores a ele;
     *  - se não há cutoff e a conta não tem treinos, grava cutoff = agora (não importa histórico de terceiros);
     *  - se não há cutoff mas há treinos, importa tudo;
     *  - deduplica por [ExternalWorkoutSample.uniqueKey] contra [existingWorkoutKeys] ("<título>_<epoch segundos>").
     * A inserção (e a nota "Sincronizado automaticamente (<fonte>)") fica a cargo do chamador.
     */
    suspend fun importableExternalWorkouts(
        userId: String,
        existingWorkoutKeys: Set<String>,
        hasExistingWorkouts: Boolean,
        days: Int = 7
    ): List<ExternalWorkoutSample> {
        if (userId.isEmpty()) return emptyList()
        val samples = fetchExternalWorkoutSamples(days)
        if (samples.isEmpty()) return emptyList()

        val cutoff = importCutoff(userId)
        val effectiveCutoff: Long? = when {
            cutoff != null -> cutoff
            !hasExistingWorkouts -> System.currentTimeMillis().also { setImportCutoff(userId, it) }
            else -> null
        }
        val keys = existingWorkoutKeys.toMutableSet()
        val result = mutableListOf<ExternalWorkoutSample>()
        for (sample in samples) {
            if (effectiveCutoff != null && sample.startDate < effectiveCutoff) continue
            if (!keys.add(sample.uniqueKey)) continue
            result.add(sample)
        }
        return result
    }

    // endregion

    private fun exerciseTypeFor(workoutType: String): Int = when (workoutType) {
        WorkoutTypes.BODYBUILDING -> ExerciseSessionRecord.EXERCISE_TYPE_STRENGTH_TRAINING
        WorkoutTypes.RUNNING -> ExerciseSessionRecord.EXERCISE_TYPE_RUNNING
        WorkoutTypes.WALKING -> ExerciseSessionRecord.EXERCISE_TYPE_WALKING
        WorkoutTypes.CYCLING -> ExerciseSessionRecord.EXERCISE_TYPE_BIKING
        WorkoutTypes.FUNCTIONAL -> ExerciseSessionRecord.EXERCISE_TYPE_HIGH_INTENSITY_INTERVAL_TRAINING
        WorkoutTypes.SWIMMING -> ExerciseSessionRecord.EXERCISE_TYPE_SWIMMING_POOL
        else -> ExerciseSessionRecord.EXERCISE_TYPE_OTHER_WORKOUT
    }

    companion object {
        private val READ_RECORD_TYPES: List<KClass<out androidx.health.connect.client.records.Record>> = listOf(
            StepsRecord::class,
            ActiveCaloriesBurnedRecord::class,
            BasalMetabolicRateRecord::class,
            DistanceRecord::class,
            ExerciseSessionRecord::class
        )

        val READ_PERMISSIONS: Set<String> = READ_RECORD_TYPES.map { HealthPermission.getReadPermission(it) }.toSet()

        val WRITE_PERMISSIONS: Set<String> = setOf(
            HealthPermission.getWritePermission(ExerciseSessionRecord::class),
            HealthPermission.getWritePermission(ActiveCaloriesBurnedRecord::class)
        )

        /** Todas as permissões solicitadas (leitura + escrita), iguais ao `requestAuthorization` do iOS. */
        val PERMISSIONS: Set<String> = READ_PERMISSIONS + WRITE_PERMISSIONS

        @Volatile
        private var instance: HealthConnectManager? = null

        fun get(context: Context): HealthConnectManager =
            instance ?: synchronized(this) {
                instance ?: HealthConnectManager(context).also { instance = it }
            }
    }
}

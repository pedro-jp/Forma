package com.forma.app.data.services

import android.app.AlarmManager
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.media.RingtoneManager
import android.os.Build
import android.os.IBinder
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.util.Calendar

/**
 * Notificações de treino/lembretes (Android). Porta de `WorkoutNotificationManager` + `LiveActivityManager`
 * (Live Activity / Ilha Dinâmica -> notificação ongoing rica) do iOS.
 *
 * Cria seus próprios canais (não usa `service/NotificationHelper`):
 *  - [CHANNEL_LIVE]: treino ao vivo (ongoing, baixa importância, sem som);
 *  - [CHANNEL_REST]: fim do descanso (alta importância, vibra/som - equivalente à notificação Time-Sensitive);
 *  - [CHANNEL_REMINDERS]: lembretes de hidratação e refeições.
 *
 * Chame [setup] uma vez (ex.: `FormaApplication.onCreate`). A permissão `POST_NOTIFICATIONS` (Android 13+) deve ser
 * pedida pela UI ([NOTIFICATION_PERMISSION]); sem ela as chamadas viram no-op.
 *
 * Os alarmes (descanso e lembretes) usam `AlarmManager.setAndAllowWhileIdle` e são tratados por
 * [WorkoutNotificationReceiver]. Os lembretes se reagendam sozinhos para o dia seguinte; reabra
 * [scheduleDailyHydrationAndMealReminders] no início do app (não há receiver de BOOT_COMPLETED).
 */
class WorkoutNotificationManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val nm: NotificationManager =
        appContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    private val alarmManager: AlarmManager =
        appContext.getSystemService(Context.ALARM_SERVICE) as AlarmManager

    /** Cria canais de notificação. Idempotente. */
    fun setup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_LIVE, "Treino ao Vivo", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Status do treino ativo, descanso e queima calórica em tempo real na tela de bloqueio e barra de status"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_REST, "Fim do Descanso", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Avisa com vibração e som quando o descanso entre séries termina"
                    enableVibration(true)
                    vibrationPattern = longArrayOf(0, 200, 100, 200, 100, 300)
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                }
            )
            nm.createNotificationChannel(
                NotificationChannel(CHANNEL_REMINDERS, "Lembretes Forma", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Lembretes de hidratação e refeições"
                    enableVibration(true)
                }
            )
        }
    }

    /** `true` se o app pode postar notificações. */
    fun hasNotificationPermission(): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(appContext, NOTIFICATION_PERMISSION) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun contentIntent(): PendingIntent {
        val launch = appContext.packageManager.getLaunchIntentForPackage(appContext.packageName)
            ?: Intent(Intent.ACTION_MAIN).setPackage(appContext.packageName)
        launch.flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_NEW_TASK
        return PendingIntent.getActivity(
            appContext, 0, launch, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    internal fun actionIntent(action: String, requestCode: Int): PendingIntent =
        PendingIntent.getBroadcast(
            appContext, requestCode,
            Intent(appContext, WorkoutNotificationReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    // region Notificação ongoing do treino (Live Activity / Foreground Service)

    internal fun buildWorkoutNotification(state: SharedWorkoutState): Notification {
        val curEx = state.currentExercise
        val curExName = curEx?.name ?: "Treino"
        val setNumber = minOf(state.currentSetIndex + 1, curEx?.sets ?: 3)
        val totalSetsForEx = curEx?.sets ?: 3
        val kcal = state.liveCalories.toInt()
        val repsLabel = curEx?.reps?.takeIf { it.isNotBlank() }?.let { " ($it reps)" }.orEmpty()
        val lastInfo = state.lastCompletedSetInfo
        val curWeight = state.currentSetWeightKg
        val weightSummary = if (lastInfo != null) {
            "Série ${lastInfo.setNumber} feita: ${WorkoutExercisePlan.formatWeightKg(lastInfo.weightKg)}"
        } else {
            "Carga Série $setNumber: ${WorkoutExercisePlan.formatWeightKg(curWeight)}"
        }

        val titleText = if (state.isResting) {
            "⏱️ Descanso • ${state.title}"
        } else {
            "🔥 ${state.title}"
        }
        val line1 = if (state.isResting) {
            "Próx: Série $setNumber/$totalSetsForEx de $curExName • $weightSummary"
        } else {
            "$curExName • Série $setNumber/$totalSetsForEx$repsLabel • ${WorkoutExercisePlan.formatWeightKg(curWeight)}"
        }
        val line2 = "$weightSummary • ${state.completedSetsCount}/${state.totalSetsCount} séries • ~$kcal kcal"

        val builder = NotificationCompat.Builder(appContext, CHANNEL_LIVE)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle(titleText)
            .setContentText(line1)
            .setSubText("$weightSummary • ~$kcal kcal")
            .setStyle(NotificationCompat.BigTextStyle().bigText("$line1\n$line2"))
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_WORKOUT)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setColor(0xFF183C36.toInt())
            .setColorized(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(contentIntent())
            .setProgress(maxOf(1, state.totalSetsCount), state.completedSetsCount, false)
            .setUsesChronometer(true)

        if (state.isResting && state.restEndDate != null) {
            builder.setWhen(state.restEndDate!!)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setChronometerCountDown(true)
        } else {
            builder.setWhen(state.sessionStartDate)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) builder.setChronometerCountDown(false)
        }

        // O Android exibe até 3 botões de ação na notificação expandida:
        // Prioriza ajuste rápido de carga (-2kg / +2kg) da série que passou + ação principal (Pular descanso / Concluir série)
        builder.addAction(0, "-2kg", actionIntent(ACTION_DECREASE_WEIGHT, 5))
        builder.addAction(0, "+2kg", actionIntent(ACTION_INCREASE_WEIGHT, 6))
        if (state.isResting) {
            builder.addAction(0, "Pular descanso", actionIntent(ACTION_SKIP_REST, 1))
        } else if (!state.isAllCompleted) {
            builder.addAction(0, "Concluir série", actionIntent(ACTION_COMPLETE_SET, 3))
        } else {
            builder.addAction(0, "Finalizar", actionIntent(ACTION_FINISH, 4))
        }

        return builder.build()
    }

    /**
     * Exibe/atualiza a notificação ongoing do treino e mantém o Foreground Service ativo para que o sistema
     * exiba a atividade em andamento na barra de status / tela de bloqueio sem matar o timer em segundo plano.
     */
    fun showWorkoutOngoing(state: SharedWorkoutState) {
        if (!hasNotificationPermission()) return
        val notification = buildWorkoutNotification(state)
        runCatching { nm.notify(NOTIFICATION_ID_LIVE, notification) }
        runCatching { WorkoutLiveForegroundService.startOrUpdate(appContext) }
    }

    /** Remove a notificação ongoing e encerra o Foreground Service (iOS: `endWorkoutActivity`). */
    fun cancelWorkoutOngoing() {
        runCatching { WorkoutLiveForegroundService.stop(appContext) }
        nm.cancel(NOTIFICATION_ID_LIVE)
    }

    // endregion

    // region Descanso

    /** Agenda o aviso de fim do descanso (acorda o aparelho mesmo em segundo plano). */
    fun scheduleRestNotification(seconds: Int, exerciseName: String, nextSet: Int, lastSetWeightKg: Double = 0.0) {
        cancelRestNotification()
        if (seconds <= 0) return
        val pi = restAlarmIntent(exerciseName, nextSet, lastSetWeightKg)
        val triggerAt = System.currentTimeMillis() + seconds * 1000L
        runCatching { alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi) }
    }

    /** Cancela alarme pendente e notificação de descanso já entregue. */
    fun cancelRestNotification() {
        runCatching { alarmManager.cancel(restAlarmIntent("", 0, 0.0)) }
        nm.cancel(NOTIFICATION_ID_REST)
    }

    private fun restAlarmIntent(exerciseName: String, nextSet: Int, lastSetWeightKg: Double): PendingIntent =
        PendingIntent.getBroadcast(
            appContext, 100,
            Intent(appContext, WorkoutNotificationReceiver::class.java)
                .setAction(ACTION_REST_ALARM)
                .putExtra(EXTRA_EXERCISE_NAME, exerciseName)
                .putExtra(EXTRA_NEXT_SET, nextSet)
                .putExtra(EXTRA_LAST_WEIGHT_KG, lastSetWeightKg),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    /** Posta "Descanso Concluído! ⏱️" (alta prioridade, vibra e acende a tela, permitindo ajustar a carga da série que passou). */
    fun showRestFinishedNotification(exerciseName: String, nextSet: Int, lastSetWeightKg: Double = 0.0) {
        if (!hasNotificationPermission()) return
        val session = SharedWorkoutSessionManager.get(appContext).session.value
        val resolvedWeight = if (lastSetWeightKg > 0.0) {
            lastSetWeightKg
        } else {
            session?.lastCompletedSetInfo?.weightKg ?: session?.currentSetWeightKg ?: 0.0
        }
        val weightText = if (resolvedWeight > 0.0) {
            " (${WorkoutExercisePlan.formatWeightKg(resolvedWeight)})"
        } else ""
        val bodyText = "Hora da Série $nextSet de $exerciseName$weightText! Ajuste a carga da série se necessário."

        val n = NotificationCompat.Builder(appContext, CHANNEL_REST)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("Descanso Concluído! ⏱️")
            .setContentText(bodyText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bodyText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .addAction(0, "-2kg", actionIntent(ACTION_DECREASE_WEIGHT, 7))
            .addAction(0, "+2kg", actionIntent(ACTION_INCREASE_WEIGHT, 8))
            .addAction(0, "Concluir série", actionIntent(ACTION_COMPLETE_SET, 9))
            .build()
        runCatching { nm.notify(NOTIFICATION_ID_REST, n) }
    }

    /** Vibração intensa + som de campainha quando o app está aberto (iOS: `triggerRestFinishedHapticsAndSound`). */
    fun triggerRestFinishedHapticsAndSound() {
        runCatching {
            val vibrator: Vibrator? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                (appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
            }
            // Três pulsos pesados (0, 0,15 e 0,30 s) como no iOS
            vibrator?.vibrate(
                VibrationEffect.createWaveform(longArrayOf(0, 120, 30, 120, 30, 220), -1)
            )
        }
        runCatching {
            RingtoneManager.getRingtone(appContext, RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION))?.play()
        }
    }

    // endregion

    // region Lembretes insistentes (hidratação 06h–22h + refeições)

    private data class Slot(val hour: Int, val minute: Int)

    private val hydrationSlots = listOf(
        Slot(6, 30), Slot(8, 30), Slot(10, 0), Slot(11, 15), Slot(14, 0),
        Slot(15, 15), Slot(17, 0), Slot(18, 30), Slot(19, 30), Slot(21, 15)
    )

    /**
     * Agenda lembretes de água (06h–22h, pulando horários de refeição) e de refeições, recalculando quanto falta
     * para a meta sempre que o usuário registra água ou comida (mesma lógica do iOS).
     */
    fun scheduleDailyHydrationAndMealReminders(
        waterConsumedMl: Int,
        waterTargetMl: Int,
        hasLoggedBreakfast: Boolean = false,
        hasLoggedLunch: Boolean = false,
        hasLoggedDinner: Boolean = false
    ) {
        // Remove agendamentos anteriores para recalibrar
        val ids = hydrationSlots.indices.map { "forma_hydration_slot_$it" } +
            listOf("forma_meal_breakfast", "forma_meal_lunch", "forma_meal_snack", "forma_meal_dinner")
        ids.forEach { cancelReminder(it) }

        val target = maxOf(1000, waterTargetMl)
        val remainingTotal = maxOf(0, target - waterConsumedMl)
        val now = Calendar.getInstance()
        val currentMinutesOfDay = now.get(Calendar.HOUR_OF_DAY) * 60 + now.get(Calendar.MINUTE)

        val futureSlotsCount = maxOf(1, hydrationSlots.count { it.hour * 60 + it.minute > currentMinutesOfDay })
        val standardDose = maxOf(200, minOf(350, (swiftRound(target.toDouble() / hydrationSlots.size / 50.0) * 50.0).toInt()))
        val dynamicDose = if (remainingTotal > 0) {
            maxOf(200, minOf(400, (swiftRound(remainingTotal.toDouble() / futureSlotsCount / 50.0) * 50.0).toInt()))
        } else standardDose

        hydrationSlots.forEachIndexed { index, slot ->
            val slotMinutes = slot.hour * 60 + slot.minute
            val isFutureToday = slotMinutes > currentMinutesOfDay

            // Se já bateu a meta hoje, não incomoda nos horários restantes de hoje
            if (isFutureToday && remainingTotal == 0) return@forEachIndexed

            val dose = if (isFutureToday) dynamicDose else standardDose
            val remaining = if (isFutureToday) remainingTotal else maxOf(250, target - (index * standardDose))

            val (title, body) = when (index % 4) {
                0 -> "💧 Hora de tomar $dose ml de água!" to
                    "Faltam $remaining ml para sua meta de $target ml hoje. Mantenha o ritmo no Forma!"
                1 -> "👀 Sua meta de água está te esperando!" to
                    "Beba um copo de $dose ml agora. Ainda faltam $remaining ml para fechar o dia!"
                2 -> "⚡ Hidratação faz diferença no resultado!" to
                    "Pausa rápida para $dose ml de água. Faltam $remaining ml para bater os $target ml."
                else -> "🔥 Não deixe a meta de água escapar!" to
                    "Tome $dose ml agora! Só faltam $remaining ml para completar seus $target ml hoje."
            }
            scheduleReminder("forma_hydration_slot_$index", slot.hour, slot.minute, title, body)
        }

        data class Meal(val id: String, val hour: Int, val minute: Int, val logged: Boolean, val title: String, val body: String)

        val meals = listOf(
            Meal("forma_meal_breakfast", 7, 30, hasLoggedBreakfast, "🍳 Já tomou seu café da manhã?",
                "Registre por foto, voz ou um toque no Forma para começar o dia batendo sua meta!"),
            Meal("forma_meal_lunch", 12, 15, hasLoggedLunch, "🍽️ Hora do almoço! O que tem no prato hoje?",
                "Tire uma foto rápida ou escolha nos seus habituais do dia para manter sua meta Forma em dia."),
            Meal("forma_meal_snack", 16, 0, false, "🍌 Pausa para o lanche da tarde!",
                "Mantenha sua energia e proteínas em dia. Registre seu lanche no Forma!"),
            Meal("forma_meal_dinner", 20, 0, hasLoggedDinner, "🥗 Hora do jantar! Feche o dia na meta",
                "Registre seu jantar no Forma para completar suas calorias e nutrientes de hoje.")
        )
        for (meal in meals) {
            val mealMinutes = meal.hour * 60 + meal.minute
            if (mealMinutes > currentMinutesOfDay && meal.logged) continue
            scheduleReminder(meal.id, meal.hour, meal.minute, meal.title, meal.body)
        }
    }

    /** Cancela todos os lembretes diários agendados (ex.: logout). */
    fun cancelAllReminders() {
        hydrationSlots.indices.forEach { cancelReminder("forma_hydration_slot_$it") }
        listOf("forma_meal_breakfast", "forma_meal_lunch", "forma_meal_snack", "forma_meal_dinner").forEach { cancelReminder(it) }
    }

    private fun reminderIntent(id: String, hour: Int, minute: Int, title: String, body: String): PendingIntent =
        PendingIntent.getBroadcast(
            appContext, id.hashCode(),
            Intent(appContext, WorkoutNotificationReceiver::class.java)
                .setAction(ACTION_REMINDER)
                .putExtra(EXTRA_REMINDER_ID, id)
                .putExtra(EXTRA_HOUR, hour)
                .putExtra(EXTRA_MINUTE, minute)
                .putExtra(EXTRA_TITLE, title)
                .putExtra(EXTRA_BODY, body),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

    private fun cancelReminder(id: String) {
        runCatching { alarmManager.cancel(reminderIntent(id, 0, 0, "", "")) }
    }

    internal fun scheduleReminder(id: String, hour: Int, minute: Int, title: String, body: String) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, hour); set(Calendar.MINUTE, minute); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_YEAR, 1)
        }
        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, reminderIntent(id, hour, minute, title, body))
        }
    }

    internal fun showReminder(id: String, title: String, body: String) {
        if (!hasNotificationPermission()) return
        val n = NotificationCompat.Builder(appContext, CHANNEL_REMINDERS)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true)
            .setContentIntent(contentIntent())
            .build()
        runCatching { nm.notify(id.hashCode(), n) }
    }

    // endregion

    companion object {
        const val CHANNEL_LIVE = "forma_workout_live_v2"
        const val CHANNEL_REST = "forma_workout_rest"
        const val CHANNEL_REMINDERS = "forma_reminders"

        const val NOTIFICATION_ID_LIVE = 2001
        const val NOTIFICATION_ID_REST = 2002

        /** Permissão de runtime (Android 13+). */
        const val NOTIFICATION_PERMISSION = "android.permission.POST_NOTIFICATIONS"

        const val ACTION_COMPLETE_SET = "com.forma.app.action.COMPLETE_SET"
        const val ACTION_SKIP_REST = "com.forma.app.action.SKIP_REST"
        const val ACTION_ADD_REST = "com.forma.app.action.ADD_REST"
        const val ACTION_FINISH = "com.forma.app.action.FINISH_WORKOUT"
        const val ACTION_DECREASE_WEIGHT = "com.forma.app.action.DECREASE_WEIGHT"
        const val ACTION_INCREASE_WEIGHT = "com.forma.app.action.INCREASE_WEIGHT"
        internal const val ACTION_REST_ALARM = "com.forma.app.action.REST_ALARM"
        internal const val ACTION_REMINDER = "com.forma.app.action.REMINDER"

        internal const val EXTRA_EXERCISE_NAME = "exercise_name"
        internal const val EXTRA_NEXT_SET = "next_set"
        internal const val EXTRA_LAST_WEIGHT_KG = "last_weight_kg"
        internal const val EXTRA_REMINDER_ID = "reminder_id"
        internal const val EXTRA_HOUR = "hour"
        internal const val EXTRA_MINUTE = "minute"
        internal const val EXTRA_TITLE = "title"
        internal const val EXTRA_BODY = "body"

        @Volatile
        private var instance: WorkoutNotificationManager? = null

        fun get(context: Context): WorkoutNotificationManager =
            instance ?: synchronized(this) {
                instance ?: WorkoutNotificationManager(context).also { instance = it }
            }
    }
}

/**
 * Receiver das ações da notificação de treino (equivalente aos App Intents da Live Activity), do alarme de fim
 * de descanso e dos lembretes diários. Registrado no AndroidManifest.
 */
class WorkoutNotificationReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        val sessions = SharedWorkoutSessionManager.get(app)
        val notifications = WorkoutNotificationManager.get(app)
        when (intent.action) {
            WorkoutNotificationManager.ACTION_COMPLETE_SET -> sessions.completeCurrentSetFromNotification()
            WorkoutNotificationManager.ACTION_SKIP_REST -> sessions.skipRest()
            WorkoutNotificationManager.ACTION_ADD_REST -> sessions.addRestSeconds(30)
            WorkoutNotificationManager.ACTION_FINISH -> sessions.finishWorkout(storePending = true)
            WorkoutNotificationManager.ACTION_DECREASE_WEIGHT -> {
                sessions.adjustRecentSetWeight(-2.0)
                val updated = sessions.session.value
                if (updated != null && !updated.isResting) {
                    val curEx = updated.currentExercise
                    val nextSet = minOf(updated.currentSetIndex + 1, curEx?.sets ?: 3)
                    val w = updated.lastCompletedSetInfo?.weightKg ?: updated.currentSetWeightKg
                    notifications.showRestFinishedNotification(curEx?.name ?: "Treino", nextSet, w)
                }
            }
            WorkoutNotificationManager.ACTION_INCREASE_WEIGHT -> {
                sessions.adjustRecentSetWeight(2.0)
                val updated = sessions.session.value
                if (updated != null && !updated.isResting) {
                    val curEx = updated.currentExercise
                    val nextSet = minOf(updated.currentSetIndex + 1, curEx?.sets ?: 3)
                    val w = updated.lastCompletedSetInfo?.weightKg ?: updated.currentSetWeightKg
                    notifications.showRestFinishedNotification(curEx?.name ?: "Treino", nextSet, w)
                }
            }
            WorkoutNotificationManager.ACTION_REST_ALARM -> {
                val name = intent.getStringExtra(WorkoutNotificationManager.EXTRA_EXERCISE_NAME).orEmpty()
                val next = intent.getIntExtra(WorkoutNotificationManager.EXTRA_NEXT_SET, 1)
                val lastWeight = intent.getDoubleExtra(WorkoutNotificationManager.EXTRA_LAST_WEIGHT_KG, 0.0)
                notifications.showRestFinishedNotification(name, next, lastWeight)
                sessions.onRestAlarmFired()
            }
            WorkoutNotificationManager.ACTION_REMINDER -> {
                val id = intent.getStringExtra(WorkoutNotificationManager.EXTRA_REMINDER_ID) ?: return
                val title = intent.getStringExtra(WorkoutNotificationManager.EXTRA_TITLE).orEmpty()
                val body = intent.getStringExtra(WorkoutNotificationManager.EXTRA_BODY).orEmpty()
                val hour = intent.getIntExtra(WorkoutNotificationManager.EXTRA_HOUR, 0)
                val minute = intent.getIntExtra(WorkoutNotificationManager.EXTRA_MINUTE, 0)
                notifications.showReminder(id, title, body)
                notifications.scheduleReminder(id, hour, minute, title, body) // reagenda para o dia seguinte
            }
        }
    }
}

/**
 * Foreground Service leve que mantém a notificação ao vivo do treino promovida na barra de status / Now Bar /
 * tela de bloqueio e impede que o sistema encerre o cronômetro de descanso com a tela desligada.
 */
class WorkoutLiveForegroundService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val session = SharedWorkoutSessionManager.get(applicationContext).session.value
            ?: SharedWorkoutSessionManager.get(applicationContext).loadSession()
        if (session == null || session.isFinished) {
            stopForeground(STOP_FOREGROUND_REMOVE)
            stopSelf()
            return START_NOT_STICKY
        }

        val notification = WorkoutNotificationManager.get(applicationContext).buildWorkoutNotification(session)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    WorkoutNotificationManager.NOTIFICATION_ID_LIVE,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
            } else {
                startForeground(WorkoutNotificationManager.NOTIFICATION_ID_LIVE, notification)
            }
        }
        return START_STICKY
    }

    companion object {
        private const val ACTION_STOP = "com.forma.app.action.STOP_WORKOUT_FGS"

        fun startOrUpdate(context: Context) {
            val intent = Intent(context, WorkoutLiveForegroundService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        fun stop(context: Context) {
            val intent = Intent(context, WorkoutLiveForegroundService::class.java).setAction(ACTION_STOP)
            context.stopService(intent)
        }
    }
}

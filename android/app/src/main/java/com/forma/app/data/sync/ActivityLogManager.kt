package com.forma.app.data.sync

import com.forma.app.data.local.DatabaseHelper
import com.forma.app.data.model.*

/**
 * Equivalente ao AppActivityLogManager do iOS: registra todos os eventos do usuário (refeição, treino, água, pesagem)
 * para consumo cronológico e diagnóstico da IA. Persistido na tabela `activity_logs` (mantém os últimos 1000 registros).
 */
class ActivityLogManager(private val db: DatabaseHelper) {

    private val lock = Any()

    /** Registros em ordem cronológica crescente (como o arquivo user_activity_log.json do iOS). */
    fun getAllRecords(): List<ActivityLogRecord> = synchronized(lock) { db.getActivityLogsAscending() }

    /** Limpa completamente o histórico de logs de atividades locais (logout / troca de conta). */
    fun clearAll() = synchronized(lock) { db.clearActivityLogs() }

    /** Remove registros específicos pelo ID (ex: ao excluir refeição, treino ou pesagem). */
    fun removeRecords(ids: List<String>) {
        if (ids.isEmpty()) return
        synchronized(lock) { db.deleteActivityLogs(ids) }
    }

    fun mergeRemoteRecords(remoteRecords: List<ActivityLogRecord>) {
        if (remoteRecords.isEmpty()) return
        synchronized(lock) {
            db.runInTransaction {
                var didAdd = false
                for (rem in remoteRecords) {
                    if (db.insertActivityLogIfAbsent(rem)) didAdd = true
                }
                if (didAdd) db.trimActivityLogs()
            }
        }
    }

    fun appendRecord(record: ActivityLogRecord) {
        synchronized(lock) {
            db.insertActivityLog(record)
            // Mantém os últimos 1000 registros para alta performance
            db.trimActivityLogs()
        }
    }

    fun logMeal(
        id: String = java.util.UUID.randomUUID().toString(),
        name: String,
        mealType: String,
        calories: Double,
        protein: Double,
        carbs: Double,
        fat: Double,
        isSnack: Boolean = false,
        date: Long = System.currentTimeMillis()
    ) {
        appendRecord(
            ActivityLogRecord.create(
                id = id,
                timestampMillis = date,
                category = "meal",
                title = "$mealType: $name",
                isSnack = isSnack,
                caloriesChange = calories,
                proteinGrams = protein,
                carbsGrams = carbs,
                fatGrams = fat,
                details = "Refeição registrada com macros"
            )
        )
    }

    fun logWorkout(
        id: String = java.util.UUID.randomUUID().toString(),
        title: String,
        workoutType: String,
        durationMinutes: Int,
        caloriesBurned: Double,
        date: Long = System.currentTimeMillis(),
        details: String? = null
    ) {
        appendRecord(
            ActivityLogRecord.create(
                id = id,
                timestampMillis = date,
                category = "workout",
                title = "$workoutType: $title",
                caloriesChange = -Math.abs(caloriesBurned),
                details = "Duração: $durationMinutes min. ${details ?: ""}"
            )
        )
    }

    fun logWater(
        id: String = java.util.UUID.randomUUID().toString(),
        milliliters: Int,
        date: Long = System.currentTimeMillis()
    ) {
        appendRecord(
            ActivityLogRecord.create(
                id = id,
                timestampMillis = date,
                category = "water",
                title = "Ingestão de Água",
                waterMilliliters = milliliters,
                details = "$milliliters ml ingeridos"
            )
        )
    }

    fun logWeight(
        id: String = java.util.UUID.randomUUID().toString(),
        weightKg: Double,
        date: Long = System.currentTimeMillis(),
        notes: String? = null
    ) {
        appendRecord(
            ActivityLogRecord.create(
                id = id,
                timestampMillis = date,
                category = "weight",
                title = "Pesagem Corporal",
                weightKg = weightKg,
                details = notes
            )
        )
    }

    /** Consolida todas as entradas reais do banco local para o log (syncFromDatabase do iOS). */
    fun syncFromDatabase(
        foods: List<FoodEntry>,
        workouts: List<WorkoutEntry>,
        waters: List<WaterEntry>,
        weights: List<WeightEntry>
    ) {
        val all = mutableListOf<Pair<Long, ActivityLogRecord>>()
        for (f in foods) {
            all.add(
                f.consumedAt to ActivityLogRecord.create(
                    id = f.id, timestampMillis = f.consumedAt, category = "meal",
                    title = "${f.mealType.rawValue}: ${f.name}", isSnack = f.mealType == MealType.SNACKS,
                    caloriesChange = f.calories, proteinGrams = f.protein, carbsGrams = f.carbohydrates, fatGrams = f.fat,
                    details = "Porção: ${f.portionGrams.toInt()}g"
                )
            )
        }
        for (w in workouts) {
            all.add(
                w.workoutDate to ActivityLogRecord.create(
                    id = w.id, timestampMillis = w.workoutDate, category = "workout",
                    title = "${w.workoutTypeEnum.rawValue}: ${w.title}", caloriesChange = -Math.abs(w.caloriesBurned),
                    details = "${w.durationMinutes} min • ${w.notes}"
                )
            )
        }
        for (wt in waters) {
            all.add(
                wt.entryDate to ActivityLogRecord.create(
                    id = wt.id, timestampMillis = wt.entryDate, category = "water", title = "Ingestão de Água",
                    waterMilliliters = wt.milliliters, details = "${wt.milliliters} ml"
                )
            )
        }
        for (wg in weights) {
            all.add(
                wg.entryDate to ActivityLogRecord.create(
                    id = wg.id, timestampMillis = wg.entryDate, category = "weight", title = "Pesagem",
                    weightKg = wg.weight, details = wg.notes
                )
            )
        }
        val sorted = all.sortedBy { it.first }.map { it.second }
        synchronized(lock) {
            db.runInTransaction {
                db.clearActivityLogs()
                sorted.forEach { db.insertActivityLog(it) }
                db.trimActivityLogs()
            }
        }
    }

    /** Log formatado dos últimos [days] dias, pronto para ser injetado no prompt da IA (JSON). */
    fun exportRecentLogForAI(days: Int = 14): String {
        val cutoff = System.currentTimeMillis() - days * 24L * 3600L * 1000L
        val result = getAllRecords().filter { rec ->
            val d = rec.timestampMillis ?: return@filter true
            d >= cutoff
        }
        val array = com.google.gson.JsonArray()
        result.forEach { array.add(it.toJsonObject()) }
        return com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(array)
    }
}

package com.forma.app.data.sync

import com.forma.app.data.auth.AuthManager
import com.forma.app.data.local.DatabaseHelper
import com.forma.app.data.model.*
import com.google.gson.JsonObject
import java.util.Locale
import java.util.UUID

/**
 * Aplica o snapshot do `/api/sync/pull` ao cache local, espelhando `pullRemoteData` do BackendSyncManager (iOS):
 * perfil, refeições, treinos, água, pesagens, fichas, última análise de IA e logs de atividade.
 */
class RemoteSnapshotApplier(
    private val db: DatabaseHelper,
    private val auth: AuthManager,
    private val activityLog: ActivityLogManager
) {
    private fun isUuid(raw: String?): Boolean = raw != null && runCatching { UUID.fromString(raw) }.isSuccess

    private fun objects(json: JsonObject, key: String): List<JsonObject>? {
        val el = json.get(key) ?: return null
        if (!el.isJsonArray) return null
        return el.asJsonArray.mapNotNull { if (it.isJsonObject) it.asJsonObject else null }
    }

    fun apply(json: JsonObject, replaceLocalState: Boolean) {
        db.runInTransaction {
            applyProfile(json)
            applyFoods(json, replaceLocalState)
            applyWorkouts(json, replaceLocalState)
            applyWaters(json, replaceLocalState)
            applyWeights(json, replaceLocalState)
            applyRoutines(json, replaceLocalState)
            applyAnalysis(json)
        }
        applyActivityLogs(json, replaceLocalState)
    }

    private fun applyProfile(json: JsonObject) {
        var p = db.getProfile()
        auth.currentUserName.value?.takeIf { it.isNotBlank() }?.let { p = p.copy(name = it) }
        auth.currentUserEmail.value?.takeIf { it.isNotBlank() }?.let { p = p.copy(email = it) }
        json.get("profile")?.takeIf { it.isJsonObject }?.asJsonObject?.let { d ->
            d.str("goal")?.let { p = p.copy(goal = FitnessGoal.fromRaw(it)) }
            d.dbl("targetCalorieIntake")?.let { p = p.copy(targetCalorieIntake = it) }
            d.dbl("targetCalorieBurn")?.let { p = p.copy(targetCalorieBurn = it) }
            d.dbl("targetSteps")?.let { p = p.copy(targetSteps = it) }
            d.dbl("targetWeight")?.let { p = p.copy(targetWeight = it) }
            d.dbl("targetProtein")?.let { p = p.copy(targetProtein = it) }
            d.dbl("targetCarbohydrates")?.let { p = p.copy(targetCarbohydrates = it) }
            d.dbl("targetFat")?.let { p = p.copy(targetFat = it) }
            d.int("targetWaterMilliliters")?.let { p = p.copy(targetWaterMilliliters = it) }
            d.dbl("heightMeters")?.let { p = p.copy(heightMeters = it) }
            d.dbl("currentWeight")?.let { p = p.copy(currentWeight = it) }
            d.bool("onboardingCompleted")?.let { p = p.copy(onboardingCompleted = it) }
        }
        db.saveProfile(p)
    }

    private fun applyFoods(json: JsonObject, replace: Boolean) {
        val foods = objects(json, "foods") ?: return
        val existing = db.getAllFoods().associateBy { it.id.lowercase(Locale.ROOT) }
        val remoteIds = mutableSetOf<String>()
        for (f in foods) {
            val idStr = f.str("id")?.takeIf { isUuid(it) } ?: continue
            val key = idStr.lowercase(Locale.ROOT)
            remoteIds.add(key)
            val date = FormaDates.parseIsoOrNow(f.str("consumedAt"))
            val old = existing[key]
            db.insertFood(
                FoodEntry(
                    id = old?.id ?: idStr,
                    name = f.str("name") ?: "Alimento",
                    brand = f.str("brand") ?: "",
                    mealType = MealType.fromRaw(f.str("mealType") ?: "Almoço"),
                    portionGrams = f.dbl("portionGrams") ?: 100.0,
                    calories = f.dbl("calories") ?: 0.0,
                    protein = f.dbl("protein") ?: 0.0,
                    carbohydrates = f.dbl("carbohydrates") ?: 0.0,
                    fat = f.dbl("fat") ?: 0.0,
                    consumedAt = date,
                    createdAt = old?.createdAt ?: date
                )
            )
        }
        if (replace) existing.filterKeys { it !in remoteIds }.values.forEach { db.deleteFood(it.id) }
    }

    private fun applyWorkouts(json: JsonObject, replace: Boolean) {
        val workouts = objects(json, "workouts") ?: return
        val existing = db.getAllWorkouts().associateBy { it.id.lowercase(Locale.ROOT) }
        val remoteIds = mutableSetOf<String>()
        for (w in workouts) {
            val idStr = w.str("id")?.takeIf { isUuid(it) } ?: continue
            val key = idStr.lowercase(Locale.ROOT)
            remoteIds.add(key)
            val date = FormaDates.parseIsoOrNow(w.str("workoutDate"))
            val old = existing[key]
            db.insertWorkout(
                WorkoutEntry(
                    id = old?.id ?: idStr,
                    title = w.str("title") ?: "Treino",
                    workoutType = w.str("workoutType") ?: "Musculação",
                    durationMinutes = w.int("durationMinutes") ?: 45,
                    caloriesBurned = w.dbl("caloriesBurned") ?: 300.0,
                    notes = w.str("notes") ?: "",
                    workoutDate = date,
                    createdAt = old?.createdAt ?: date
                )
            )
        }
        if (replace) existing.filterKeys { it !in remoteIds }.values.forEach { db.deleteWorkout(it.id) }
    }

    private fun applyWaters(json: JsonObject, replace: Boolean) {
        val waters = objects(json, "waters") ?: return
        val existing = db.getAllWaters().associateBy { it.id.lowercase(Locale.ROOT) }
        val remoteIds = mutableSetOf<String>()
        for (w in waters) {
            val idStr = w.str("id")?.takeIf { isUuid(it) } ?: continue
            val key = idStr.lowercase(Locale.ROOT)
            remoteIds.add(key)
            val date = FormaDates.parseIsoOrNow(w.str("entryDate"))
            val old = existing[key]
            db.insertWater(
                WaterEntry(
                    id = old?.id ?: idStr,
                    milliliters = w.int("milliliters") ?: 250,
                    entryDate = date,
                    createdAt = old?.createdAt ?: date
                )
            )
        }
        if (replace) existing.filterKeys { it !in remoteIds }.values.forEach { db.deleteWater(it.id) }
    }

    private fun applyWeights(json: JsonObject, replace: Boolean) {
        val weights = objects(json, "weights") ?: return
        val existing = db.getAllWeights().associateBy { it.id.lowercase(Locale.ROOT) }
        val remoteIds = mutableSetOf<String>()
        for (w in weights) {
            val idStr = w.str("id")?.takeIf { isUuid(it) } ?: continue
            val key = idStr.lowercase(Locale.ROOT)
            remoteIds.add(key)
            val date = FormaDates.parseIsoOrNow(w.str("entryDate"))
            val old = existing[key]
            db.insertWeight(
                WeightEntry(
                    id = old?.id ?: idStr,
                    weight = w.dbl("weight") ?: 70.0,
                    entryDate = date,
                    // Pesagens existentes preservam as notas locais (o iOS não as atualiza no pull)
                    notes = old?.notes ?: (w.str("notes") ?: "")
                )
            )
        }
        if (replace) existing.filterKeys { it !in remoteIds }.values.forEach { db.deleteWeight(it.id) }
    }

    private fun applyRoutines(json: JsonObject, replace: Boolean) {
        val routines = objects(json, "routines") ?: return
        val existing = db.getAllRoutines().associateBy { it.id.lowercase(Locale.ROOT) }
        val remoteIds = mutableSetOf<String>()
        for (r in routines) {
            val idStr = r.str("id")?.takeIf { isUuid(it) } ?: continue
            val key = idStr.lowercase(Locale.ROOT)
            remoteIds.add(key)
            val rawExercises = r.str("exercisesJson") ?: "[]"
            val old = existing[key]
            val routine = WorkoutRoutine(
                id = old?.id ?: idStr,
                title = r.str("title") ?: "Ficha de Treino",
                workoutType = r.str("workoutType") ?: "Musculação",
                targetMuscles = r.str("targetMuscles") ?: "",
                hypertrophyFocus = r.str("hypertrophyFocus") ?: "",
                estimatedDurationMinutes = r.int("estimatedDurationMinutes") ?: 50,
                estimatedCalories = r.dbl("estimatedCalories") ?: 350.0,
                recommendedRestSeconds = r.int("recommendedRestSeconds") ?: 90,
                isFavorite = r.bool("isFavorite") ?: false,
                exercises = emptyList(),
                notes = r.str("notes") ?: "",
                createdAt = old?.createdAt ?: FormaDates.parseIsoOrNow(r.str("createdAt")),
                lastTrainedAt = FormaDates.parseIso(r.str("lastTrainedAt"))
            )
            db.insertRoutineRaw(routine, rawExercises)
        }
        if (replace) existing.filterKeys { it !in remoteIds }.values.forEach { db.deleteRoutine(it.id) }
    }

    private fun applyAnalysis(json: JsonObject) {
        val latest = objects(json, "analyses")?.firstOrNull()
        if (latest == null) {
            db.clearAnalyses()
            return
        }
        val analysis = EvolutionAIAnalysis(
            id = latest.str("id")?.takeIf { isUuid(it) } ?: UUID.randomUUID().toString(),
            statusSummary = latest.str("statusSummary") ?: "Diagnóstico Concluído",
            calorieDiagnostic = latest.str("calorieDiagnostic") ?: "",
            macroDiagnostic = latest.str("macroDiagnostic") ?: "",
            hydrationDiagnostic = latest.str("hydrationDiagnostic") ?: "",
            workoutSuggestions = parseStringArray(latest.str("workoutSuggestionsJson")),
            nutritionSuggestions = parseStringArray(latest.str("nutritionSuggestionsJson")),
            weeklyActionPlan = latest.str("weeklyActionPlan") ?: "",
            suggestedTargets = SuggestedNutritionTargets.fromJson(latest.str("suggestedTargetsJson")),
            recommendedRoutine = AIRecommendedRoutine.fromJson(latest.str("recommendedRoutineJson")),
            generatedAt = FormaDates.parseIsoOrNow(latest.str("createdAt"))
        ).sanitized()
        db.saveLatestAnalysis(analysis)
    }

    private fun parseStringArray(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return try {
            val el = com.google.gson.JsonParser.parseString(raw)
            if (el.isJsonArray) el.asJsonArray.mapNotNull { if (it.isJsonPrimitive) it.asString else null } else emptyList()
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun applyActivityLogs(json: JsonObject, replace: Boolean) {
        val logs = objects(json, "activityLogs") ?: return
        if (replace) activityLog.clearAll()
        val records = logs.mapNotNull { l ->
            val id = l.str("id") ?: return@mapNotNull null
            ActivityLogRecord.create(
                id = id,
                timestampMillis = FormaDates.parseIsoOrNow(l.str("timestamp")),
                category = l.str("category") ?: "activity",
                title = l.str("title") ?: "",
                isSnack = l.bool("isSnack"),
                caloriesChange = l.dbl("caloriesChange") ?: 0.0,
                proteinGrams = l.dbl("proteinGrams"),
                carbsGrams = l.dbl("carbsGrams"),
                fatGrams = l.dbl("fatGrams"),
                waterMilliliters = l.int("waterMilliliters"),
                weightKg = l.dbl("weightKg"),
                details = l.str("details")
            )
        }
        activityLog.mergeRemoteRecords(records)
    }
}

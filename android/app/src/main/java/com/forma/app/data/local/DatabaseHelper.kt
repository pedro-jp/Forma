package com.forma.app.data.local

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.forma.app.data.model.*

/**
 * Cache local (equivalente ao SwiftData do iOS). Versão 2: adiciona campos de paridade com o iOS
 * (perfil: preferredFocus/autoRecalculateGoals/onboardingCompleted; createdAt nas entradas; lastTrainedAt nas fichas).
 */
class DatabaseHelper(context: Context) : SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {

    companion object {
        private const val DATABASE_NAME = "forma.db"
        private const val DATABASE_VERSION = 2

        private const val TABLE_PROFILE = "profile"
        private const val TABLE_FOODS = "foods"
        private const val TABLE_WORKOUTS = "workouts"
        private const val TABLE_WATERS = "waters"
        private const val TABLE_WEIGHTS = "weights"
        private const val TABLE_ROUTINES = "routines"
        private const val TABLE_ANALYSES = "analyses"
        private const val TABLE_ACTIVITY_LOGS = "activity_logs"

        /** Mantém os últimos 1000 registros do log de atividades (igual ao AppActivityLogManager). */
        const val MAX_ACTIVITY_LOGS = 1000
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE $TABLE_PROFILE (
                id INTEGER PRIMARY KEY,
                name TEXT,
                email TEXT,
                goal TEXT,
                target_calories REAL,
                target_burn REAL,
                target_steps REAL,
                current_weight REAL,
                target_weight REAL,
                height REAL,
                target_protein REAL,
                target_fat REAL,
                target_carbs REAL,
                target_water INTEGER,
                preferred_focus TEXT,
                auto_recalculate INTEGER DEFAULT 1,
                onboarding_completed INTEGER DEFAULT 0
            )
            """
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_FOODS (
                id TEXT PRIMARY KEY,
                name TEXT,
                brand TEXT,
                meal_type TEXT,
                portion_grams REAL,
                calories REAL,
                protein REAL,
                carbohydrates REAL,
                fat REAL,
                consumed_at INTEGER,
                created_at INTEGER
            )
            """
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_WORKOUTS (
                id TEXT PRIMARY KEY,
                title TEXT,
                workout_type TEXT,
                duration_minutes INTEGER,
                calories_burned REAL,
                notes TEXT,
                workout_date INTEGER,
                created_at INTEGER
            )
            """
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_WATERS (
                id TEXT PRIMARY KEY,
                milliliters INTEGER,
                entry_date INTEGER,
                created_at INTEGER
            )
            """
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_WEIGHTS (
                id TEXT PRIMARY KEY,
                weight REAL,
                entry_date INTEGER,
                notes TEXT
            )
            """
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_ROUTINES (
                id TEXT PRIMARY KEY,
                title TEXT,
                workout_type TEXT,
                target_muscles TEXT,
                hypertrophy_focus TEXT,
                estimated_duration INTEGER,
                estimated_calories REAL,
                recommended_rest INTEGER,
                is_favorite INTEGER,
                exercises_json TEXT,
                notes TEXT,
                created_at INTEGER,
                last_trained_at INTEGER
            )
            """
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_ANALYSES (
                id TEXT PRIMARY KEY,
                status_summary TEXT,
                calorie_diagnostic TEXT,
                macro_diagnostic TEXT,
                hydration_diagnostic TEXT,
                workout_suggestions_json TEXT,
                nutrition_suggestions_json TEXT,
                weekly_action_plan TEXT,
                suggested_targets_json TEXT,
                recommended_routine_json TEXT,
                generated_at INTEGER
            )
            """
        )

        db.execSQL(
            """
            CREATE TABLE $TABLE_ACTIVITY_LOGS (
                id TEXT PRIMARY KEY,
                timestamp TEXT,
                category TEXT,
                title TEXT,
                is_snack INTEGER,
                calories_change REAL,
                protein_grams REAL,
                carbs_grams REAL,
                fat_grams REAL,
                water_milliliters INTEGER,
                weight_kg REAL,
                details TEXT
            )
            """
        )

        // Perfil vazio (idêntico ao Profile() do iOS: nome/e-mail vazios, onboarding pendente)
        db.insert(TABLE_PROFILE, null, profileValues(UserProfile()).apply { put("id", 1) })
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE $TABLE_PROFILE ADD COLUMN preferred_focus TEXT")
            db.execSQL("ALTER TABLE $TABLE_PROFILE ADD COLUMN auto_recalculate INTEGER DEFAULT 1")
            db.execSQL("ALTER TABLE $TABLE_PROFILE ADD COLUMN onboarding_completed INTEGER DEFAULT 0")
            db.execSQL("ALTER TABLE $TABLE_FOODS ADD COLUMN created_at INTEGER")
            db.execSQL("ALTER TABLE $TABLE_WORKOUTS ADD COLUMN created_at INTEGER")
            db.execSQL("ALTER TABLE $TABLE_WATERS ADD COLUMN created_at INTEGER")
            db.execSQL("ALTER TABLE $TABLE_ROUTINES ADD COLUMN last_trained_at INTEGER")
            db.execSQL("UPDATE $TABLE_FOODS SET created_at = consumed_at WHERE created_at IS NULL")
            db.execSQL("UPDATE $TABLE_WORKOUTS SET created_at = workout_date WHERE created_at IS NULL")
            db.execSQL("UPDATE $TABLE_WATERS SET created_at = entry_date WHERE created_at IS NULL")
            db.execSQL("UPDATE $TABLE_PROFILE SET auto_recalculate = 1, onboarding_completed = 0")
            // Remove o perfil fictício das versões antigas (dados de demonstração que não existem no iOS)
            db.execSQL(
                "UPDATE $TABLE_PROFILE SET name = '', email = '' " +
                    "WHERE name = 'João Pedro' OR email IN ('joao@email.com', 'joaopedroc035@gmail.com')"
            )
        }
    }

    // --- Utilitários ---
    private fun Cursor.string(col: String): String? = getColumnIndex(col).let { if (it < 0 || isNull(it)) null else getString(it) }
    private fun Cursor.long(col: String): Long? = getColumnIndex(col).let { if (it < 0 || isNull(it)) null else getLong(it) }
    private fun Cursor.double(col: String): Double? = getColumnIndex(col).let { if (it < 0 || isNull(it)) null else getDouble(it) }
    private fun Cursor.int(col: String): Int? = getColumnIndex(col).let { if (it < 0 || isNull(it)) null else getInt(it) }

    /** Executa um bloco em transação (usado nos merges de sincronização). */
    fun <T> runInTransaction(block: () -> T): T {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val result = block()
            db.setTransactionSuccessful()
            return result
        } finally {
            db.endTransaction()
        }
    }

    // --- Profile ---
    private fun profileValues(profile: UserProfile) = ContentValues().apply {
        put("name", profile.name)
        put("email", profile.email)
        put("goal", profile.goal.rawValue)
        put("target_calories", profile.targetCalorieIntake)
        put("target_burn", profile.targetCalorieBurn)
        put("target_steps", profile.targetSteps)
        put("current_weight", profile.currentWeight)
        put("target_weight", profile.targetWeight)
        put("height", profile.heightMeters)
        put("target_protein", profile.targetProtein)
        put("target_fat", profile.targetFat)
        put("target_carbs", profile.targetCarbohydrates)
        put("target_water", profile.targetWaterMilliliters)
        put("preferred_focus", profile.preferredFocus?.rawValue)
        put("auto_recalculate", if (profile.autoRecalculateGoals) 1 else 0)
        put("onboarding_completed", if (profile.onboardingCompleted) 1 else 0)
    }

    fun getProfile(): UserProfile {
        val db = readableDatabase
        val cursor = db.query(TABLE_PROFILE, null, "id = 1", null, null, null, null)
        cursor.use {
            if (it.moveToFirst()) {
                val defaults = UserProfile()
                return UserProfile(
                    name = it.string("name") ?: "",
                    email = it.string("email") ?: "",
                    goal = FitnessGoal.fromRaw(it.string("goal")),
                    targetCalorieIntake = it.double("target_calories") ?: defaults.targetCalorieIntake,
                    targetCalorieBurn = it.double("target_burn") ?: defaults.targetCalorieBurn,
                    targetSteps = it.double("target_steps") ?: defaults.targetSteps,
                    currentWeight = it.double("current_weight") ?: defaults.currentWeight,
                    targetWeight = it.double("target_weight") ?: defaults.targetWeight,
                    heightMeters = it.double("height") ?: defaults.heightMeters,
                    targetProtein = it.double("target_protein") ?: defaults.targetProtein,
                    targetFat = it.double("target_fat") ?: defaults.targetFat,
                    targetCarbohydrates = it.double("target_carbs") ?: defaults.targetCarbohydrates,
                    targetWaterMilliliters = it.int("target_water") ?: defaults.targetWaterMilliliters,
                    preferredFocus = FocusMetric.fromRaw(it.string("preferred_focus")),
                    autoRecalculateGoals = (it.int("auto_recalculate") ?: 1) == 1,
                    onboardingCompleted = (it.int("onboarding_completed") ?: 0) == 1
                )
            }
        }
        // Garante que exista exatamente um Profile (ensureSingleProfile do iOS)
        val fresh = UserProfile()
        writableDatabase.insertWithOnConflict(
            TABLE_PROFILE, null, profileValues(fresh).apply { put("id", 1) }, SQLiteDatabase.CONFLICT_REPLACE
        )
        return fresh
    }

    fun saveProfile(profile: UserProfile) {
        val db = writableDatabase
        val values = profileValues(profile)
        val updated = db.update(TABLE_PROFILE, values, "id = 1", null)
        if (updated == 0) {
            db.insertWithOnConflict(TABLE_PROFILE, null, values.apply { put("id", 1) }, SQLiteDatabase.CONFLICT_REPLACE)
        }
    }

    // --- Foods ---
    fun insertFood(food: FoodEntry) {
        val cv = ContentValues().apply {
            put("id", food.id)
            put("name", food.name)
            put("brand", food.brand)
            put("meal_type", food.mealType.name)
            put("portion_grams", food.portionGrams)
            put("calories", food.calories)
            put("protein", food.protein)
            put("carbohydrates", food.carbohydrates)
            put("fat", food.fat)
            put("consumed_at", food.consumedAt)
            put("created_at", food.createdAt)
        }
        writableDatabase.insertWithOnConflict(TABLE_FOODS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun readFoods(selection: String?, args: Array<String>?): List<FoodEntry> {
        val list = mutableListOf<FoodEntry>()
        readableDatabase.query(TABLE_FOODS, null, selection, args, null, null, "consumed_at DESC").use {
            while (it.moveToNext()) {
                val consumedAt = it.long("consumed_at") ?: System.currentTimeMillis()
                list.add(
                    FoodEntry(
                        id = it.string("id") ?: continue,
                        name = it.string("name") ?: "",
                        brand = it.string("brand") ?: "",
                        mealType = MealType.fromRaw(it.string("meal_type")),
                        portionGrams = it.double("portion_grams") ?: 100.0,
                        calories = it.double("calories") ?: 0.0,
                        protein = it.double("protein") ?: 0.0,
                        carbohydrates = it.double("carbohydrates") ?: 0.0,
                        fat = it.double("fat") ?: 0.0,
                        consumedAt = consumedAt,
                        createdAt = it.long("created_at") ?: consumedAt
                    )
                )
            }
        }
        return list
    }

    fun getAllFoods(): List<FoodEntry> = readFoods(null, null)

    fun getFoodsBetween(startMillis: Long, endMillisExclusive: Long): List<FoodEntry> =
        readFoods("consumed_at >= ? AND consumed_at < ?", arrayOf(startMillis.toString(), endMillisExclusive.toString()))

    fun deleteFood(id: String) {
        writableDatabase.delete(TABLE_FOODS, "lower(id) = ?", arrayOf(id.lowercase()))
    }

    fun deleteFoods(ids: Collection<String>) = ids.forEach { deleteFood(it) }

    // --- Workouts ---
    fun insertWorkout(workout: WorkoutEntry) {
        val cv = ContentValues().apply {
            put("id", workout.id)
            put("title", workout.title)
            put("workout_type", workout.workoutType)
            put("duration_minutes", workout.durationMinutes)
            put("calories_burned", workout.caloriesBurned)
            put("notes", workout.notes)
            put("workout_date", workout.workoutDate)
            put("created_at", workout.createdAt)
        }
        writableDatabase.insertWithOnConflict(TABLE_WORKOUTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun readWorkouts(selection: String?, args: Array<String>?): List<WorkoutEntry> {
        val list = mutableListOf<WorkoutEntry>()
        readableDatabase.query(TABLE_WORKOUTS, null, selection, args, null, null, "workout_date DESC").use {
            while (it.moveToNext()) {
                val date = it.long("workout_date") ?: System.currentTimeMillis()
                list.add(
                    WorkoutEntry(
                        id = it.string("id") ?: continue,
                        title = it.string("title") ?: "",
                        workoutType = it.string("workout_type") ?: "Musculação",
                        durationMinutes = it.int("duration_minutes") ?: 45,
                        caloriesBurned = it.double("calories_burned") ?: 350.0,
                        notes = it.string("notes") ?: "",
                        workoutDate = date,
                        createdAt = it.long("created_at") ?: date
                    )
                )
            }
        }
        return list
    }

    fun getAllWorkouts(): List<WorkoutEntry> = readWorkouts(null, null)

    fun getWorkoutsBetween(startMillis: Long, endMillisExclusive: Long): List<WorkoutEntry> =
        readWorkouts("workout_date >= ? AND workout_date < ?", arrayOf(startMillis.toString(), endMillisExclusive.toString()))

    fun deleteWorkout(id: String) {
        writableDatabase.delete(TABLE_WORKOUTS, "lower(id) = ?", arrayOf(id.lowercase()))
    }

    // --- Water ---
    fun insertWater(water: WaterEntry) {
        val cv = ContentValues().apply {
            put("id", water.id)
            put("milliliters", water.milliliters)
            put("entry_date", water.entryDate)
            put("created_at", water.createdAt)
        }
        writableDatabase.insertWithOnConflict(TABLE_WATERS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun readWaters(selection: String?, args: Array<String>?): List<WaterEntry> {
        val list = mutableListOf<WaterEntry>()
        readableDatabase.query(TABLE_WATERS, null, selection, args, null, null, "entry_date DESC").use {
            while (it.moveToNext()) {
                val date = it.long("entry_date") ?: System.currentTimeMillis()
                list.add(
                    WaterEntry(
                        id = it.string("id") ?: continue,
                        milliliters = it.int("milliliters") ?: 250,
                        entryDate = date,
                        createdAt = it.long("created_at") ?: date
                    )
                )
            }
        }
        return list
    }

    fun getAllWaters(): List<WaterEntry> = readWaters(null, null)

    fun getWatersBetween(startMillis: Long, endMillisExclusive: Long): List<WaterEntry> =
        readWaters("entry_date >= ? AND entry_date < ?", arrayOf(startMillis.toString(), endMillisExclusive.toString()))

    fun deleteWater(id: String) {
        writableDatabase.delete(TABLE_WATERS, "lower(id) = ?", arrayOf(id.lowercase()))
    }

    // --- Weights ---
    fun insertWeight(weight: WeightEntry) {
        val cv = ContentValues().apply {
            put("id", weight.id)
            put("weight", weight.weight)
            put("entry_date", weight.entryDate)
            put("notes", weight.notes)
        }
        writableDatabase.insertWithOnConflict(TABLE_WEIGHTS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    private fun readWeights(selection: String?, args: Array<String>?): List<WeightEntry> {
        val list = mutableListOf<WeightEntry>()
        readableDatabase.query(TABLE_WEIGHTS, null, selection, args, null, null, "entry_date DESC").use {
            while (it.moveToNext()) {
                list.add(
                    WeightEntry(
                        id = it.string("id") ?: continue,
                        weight = it.double("weight") ?: 72.0,
                        entryDate = it.long("entry_date") ?: System.currentTimeMillis(),
                        notes = it.string("notes") ?: ""
                    )
                )
            }
        }
        return list
    }

    fun getAllWeights(): List<WeightEntry> = readWeights(null, null)

    fun getWeightsBetween(startMillis: Long, endMillisExclusive: Long): List<WeightEntry> =
        readWeights("entry_date >= ? AND entry_date < ?", arrayOf(startMillis.toString(), endMillisExclusive.toString()))

    fun deleteWeight(id: String) {
        writableDatabase.delete(TABLE_WEIGHTS, "lower(id) = ?", arrayOf(id.lowercase()))
    }

    // --- Routines ---
    fun insertRoutine(routine: WorkoutRoutine) {
        val cv = ContentValues().apply {
            put("id", routine.id)
            put("title", routine.title)
            put("workout_type", routine.workoutType)
            put("target_muscles", routine.targetMuscles)
            put("hypertrophy_focus", routine.hypertrophyFocus)
            put("estimated_duration", routine.estimatedDurationMinutes)
            put("estimated_calories", routine.estimatedCalories)
            put("recommended_rest", routine.recommendedRestSeconds)
            put("is_favorite", if (routine.isFavorite) 1 else 0)
            put("exercises_json", routine.exercisesJson)
            put("notes", routine.notes)
            put("created_at", routine.createdAt)
            put("last_trained_at", routine.lastTrainedAt)
        }
        writableDatabase.insertWithOnConflict(TABLE_ROUTINES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Insere/atualiza uma ficha preservando o JSON bruto de exercícios vindo do servidor (idêntico ao iOS). */
    fun insertRoutineRaw(routine: WorkoutRoutine, rawExercisesJson: String) {
        insertRoutine(routine)
        writableDatabase.execSQL(
            "UPDATE $TABLE_ROUTINES SET exercises_json = ? WHERE id = ?",
            arrayOf(rawExercisesJson, routine.id)
        )
    }

    fun getAllRoutines(): List<WorkoutRoutine> {
        val list = mutableListOf<WorkoutRoutine>()
        readableDatabase.query(TABLE_ROUTINES, null, null, null, null, null, "created_at DESC").use {
            while (it.moveToNext()) {
                list.add(
                    WorkoutRoutine(
                        id = it.string("id") ?: continue,
                        title = it.string("title") ?: "",
                        workoutType = it.string("workout_type") ?: "Musculação",
                        targetMuscles = it.string("target_muscles") ?: "",
                        hypertrophyFocus = it.string("hypertrophy_focus") ?: "",
                        estimatedDurationMinutes = it.int("estimated_duration") ?: 50,
                        estimatedCalories = it.double("estimated_calories") ?: 350.0,
                        recommendedRestSeconds = it.int("recommended_rest") ?: 90,
                        isFavorite = (it.int("is_favorite") ?: 0) == 1,
                        exercises = WorkoutExerciseItem.listFromJson(it.string("exercises_json")),
                        notes = it.string("notes") ?: "",
                        createdAt = it.long("created_at") ?: System.currentTimeMillis(),
                        lastTrainedAt = it.long("last_trained_at")
                    )
                )
            }
        }
        return list
    }

    fun deleteRoutine(id: String) {
        writableDatabase.delete(TABLE_ROUTINES, "lower(id) = ?", arrayOf(id.lowercase()))
    }

    // --- Analyses (apenas a mais recente é mantida, como o EvolutionAnalysisStore do iOS) ---
    fun insertAnalysis(analysis: EvolutionAIAnalysis) {
        val cv = ContentValues().apply {
            put("id", analysis.id)
            put("status_summary", analysis.statusSummary)
            put("calorie_diagnostic", analysis.calorieDiagnostic)
            put("macro_diagnostic", analysis.macroDiagnostic)
            put("hydration_diagnostic", analysis.hydrationDiagnostic)
            put("workout_suggestions_json", com.google.gson.Gson().toJson(analysis.workoutSuggestions))
            put("nutrition_suggestions_json", com.google.gson.Gson().toJson(analysis.nutritionSuggestions))
            put("weekly_action_plan", analysis.weeklyActionPlan)
            put("suggested_targets_json", analysis.suggestedTargets?.toJson())
            put("recommended_routine_json", analysis.recommendedRoutine?.toJson())
            put("generated_at", analysis.generatedAt)
        }
        writableDatabase.insertWithOnConflict(TABLE_ANALYSES, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Substitui qualquer análise anterior pela mais recente (EvolutionAnalysisStore.save). */
    fun saveLatestAnalysis(analysis: EvolutionAIAnalysis) = runInTransaction {
        writableDatabase.delete(TABLE_ANALYSES, null, null)
        insertAnalysis(analysis)
    }

    fun clearAnalyses() {
        writableDatabase.delete(TABLE_ANALYSES, null, null)
    }

    fun getLatestAnalysis(): EvolutionAIAnalysis? {
        readableDatabase.query(TABLE_ANALYSES, null, null, null, null, null, "generated_at DESC", "1").use {
            if (it.moveToFirst()) {
                val gson = com.google.gson.Gson()
                val listType = object : com.google.gson.reflect.TypeToken<List<String>>() {}.type
                val wJson = it.string("workout_suggestions_json") ?: "[]"
                val nJson = it.string("nutrition_suggestions_json") ?: "[]"
                return EvolutionAIAnalysis(
                    id = it.string("id") ?: return null,
                    statusSummary = it.string("status_summary") ?: "",
                    calorieDiagnostic = it.string("calorie_diagnostic") ?: "",
                    macroDiagnostic = it.string("macro_diagnostic") ?: "",
                    hydrationDiagnostic = it.string("hydration_diagnostic") ?: "",
                    workoutSuggestions = try { gson.fromJson<List<String>>(wJson, listType) } catch (_: Exception) { emptyList() },
                    nutritionSuggestions = try { gson.fromJson<List<String>>(nJson, listType) } catch (_: Exception) { emptyList() },
                    weeklyActionPlan = it.string("weekly_action_plan") ?: "",
                    suggestedTargets = SuggestedNutritionTargets.fromJson(it.string("suggested_targets_json")),
                    recommendedRoutine = AIRecommendedRoutine.fromJson(it.string("recommended_routine_json")),
                    generatedAt = it.long("generated_at") ?: System.currentTimeMillis()
                )
            }
        }
        return null
    }

    // --- Activity Logs ---
    fun insertActivityLog(log: ActivityLogRecord) {
        val cv = ContentValues().apply {
            put("id", log.id)
            put("timestamp", log.timestamp)
            put("category", log.category)
            put("title", log.title)
            put("is_snack", log.isSnack?.let { if (it) 1 else 0 })
            put("calories_change", log.caloriesChange)
            put("protein_grams", log.proteinGrams)
            put("carbs_grams", log.carbsGrams)
            put("fat_grams", log.fatGrams)
            put("water_milliliters", log.waterMilliliters)
            put("weight_kg", log.weightKg)
            put("details", log.details)
        }
        writableDatabase.insertWithOnConflict(TABLE_ACTIVITY_LOGS, null, cv, SQLiteDatabase.CONFLICT_REPLACE)
    }

    /** Insere apenas se o id ainda não existir (mergeRemoteRecords do iOS). Retorna true se inseriu. */
    fun insertActivityLogIfAbsent(log: ActivityLogRecord): Boolean {
        val exists = readableDatabase.query(
            TABLE_ACTIVITY_LOGS, arrayOf("id"), "id = ?", arrayOf(log.id), null, null, null, "1"
        ).use { it.moveToFirst() }
        if (exists) return false
        insertActivityLog(log)
        return true
    }

    /** Mantém apenas os últimos [MAX_ACTIVITY_LOGS] registros (por timestamp). */
    fun trimActivityLogs(max: Int = MAX_ACTIVITY_LOGS) {
        writableDatabase.execSQL(
            "DELETE FROM $TABLE_ACTIVITY_LOGS WHERE id NOT IN " +
                "(SELECT id FROM $TABLE_ACTIVITY_LOGS ORDER BY timestamp DESC LIMIT $max)"
        )
    }

    fun deleteActivityLogs(ids: Collection<String>) {
        val db = writableDatabase
        ids.forEach { db.delete(TABLE_ACTIVITY_LOGS, "lower(id) = ?", arrayOf(it.lowercase())) }
    }

    fun clearActivityLogs() {
        writableDatabase.delete(TABLE_ACTIVITY_LOGS, null, null)
    }

    private fun readActivityLogs(order: String): List<ActivityLogRecord> {
        val list = mutableListOf<ActivityLogRecord>()
        readableDatabase.query(TABLE_ACTIVITY_LOGS, null, null, null, null, null, order).use {
            while (it.moveToNext()) {
                list.add(
                    ActivityLogRecord(
                        id = it.string("id") ?: continue,
                        timestamp = it.string("timestamp") ?: "",
                        category = it.string("category") ?: "activity",
                        title = it.string("title") ?: "",
                        isSnack = it.int("is_snack")?.let { v -> v == 1 },
                        caloriesChange = it.double("calories_change") ?: 0.0,
                        proteinGrams = it.double("protein_grams"),
                        carbsGrams = it.double("carbs_grams"),
                        fatGrams = it.double("fat_grams"),
                        waterMilliliters = it.int("water_milliliters"),
                        weightKg = it.double("weight_kg"),
                        details = it.string("details")
                    )
                )
            }
        }
        return list
    }

    /** Mais recentes primeiro. */
    fun getAllActivityLogs(): List<ActivityLogRecord> = readActivityLogs("timestamp DESC")

    /** Ordem cronológica crescente (como o arquivo user_activity_log.json do iOS). */
    fun getActivityLogsAscending(): List<ActivityLogRecord> = readActivityLogs("timestamp ASC")

    // --- Limpeza total (resetLocalDataForUserSwitch) ---
    fun clearAllUserData(defaultName: String = "", defaultEmail: String = "") {
        val db = writableDatabase
        db.beginTransaction()
        try {
            db.delete(TABLE_FOODS, null, null)
            db.delete(TABLE_WORKOUTS, null, null)
            db.delete(TABLE_WATERS, null, null)
            db.delete(TABLE_WEIGHTS, null, null)
            db.delete(TABLE_ROUTINES, null, null)
            db.delete(TABLE_ANALYSES, null, null)
            db.delete(TABLE_ACTIVITY_LOGS, null, null)
            db.delete(TABLE_PROFILE, null, null)
            db.insert(
                TABLE_PROFILE, null,
                profileValues(UserProfile(name = defaultName.trim(), email = defaultEmail.trim(), onboardingCompleted = false))
                    .apply { put("id", 1) }
            )
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }
}

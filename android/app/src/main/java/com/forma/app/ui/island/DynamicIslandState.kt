package com.forma.app.ui.island

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class DynamicIslandState private constructor(context: Context) {

    private val prefs = context.getSharedPreferences("dynamic_island_prefs", Context.MODE_PRIVATE)

    private val _preset = MutableStateFlow(loadPreset())
    val preset: StateFlow<IslandPreset> = _preset.asStateFlow()

    private val _customXOffset = MutableStateFlow(prefs.getInt("custom_x_offset", 0))
    val customXOffset: StateFlow<Int> = _customXOffset.asStateFlow()

    private val _customYOffset = MutableStateFlow(prefs.getInt("custom_y_offset", 0))
    val customYOffset: StateFlow<Int> = _customYOffset.asStateFlow()

    private val _isEnabled = MutableStateFlow(prefs.getBoolean("island_enabled", true))
    val isEnabled: StateFlow<Boolean> = _isEnabled.asStateFlow()

    private val _isOverlayServiceEnabled = MutableStateFlow(prefs.getBoolean("overlay_service_enabled", false))
    val isOverlayServiceEnabled: StateFlow<Boolean> = _isOverlayServiceEnabled.asStateFlow()

    private val _isExpanded = MutableStateFlow(false)
    val isExpanded: StateFlow<Boolean> = _isExpanded.asStateFlow()

    // Workout Active Live Activity State
    private val _isWorkoutActive = MutableStateFlow(false)
    val isWorkoutActive: StateFlow<Boolean> = _isWorkoutActive.asStateFlow()

    private val _workoutTitle = MutableStateFlow("Treino de Musculação")
    val workoutTitle: StateFlow<String> = _workoutTitle.asStateFlow()

    private val _workoutSeconds = MutableStateFlow(0)
    val workoutSeconds: StateFlow<Int> = _workoutSeconds.asStateFlow()

    private val _workoutCalories = MutableStateFlow(0.0)
    val workoutCalories: StateFlow<Double> = _workoutCalories.asStateFlow()

    // Water & Calorie Status
    private val _waterCurrent = MutableStateFlow(0)
    val waterCurrent: StateFlow<Int> = _waterCurrent.asStateFlow()

    private val _waterTarget = MutableStateFlow(2500)
    val waterTarget: StateFlow<Int> = _waterTarget.asStateFlow()

    private val _caloriesRemaining = MutableStateFlow(0)
    val caloriesRemaining: StateFlow<Int> = _caloriesRemaining.asStateFlow()

    private fun loadPreset(): IslandPreset {
        val typeName = prefs.getString("preset_type", CutoutType.CENTER_HOLE.name)
        val type = try { CutoutType.valueOf(typeName ?: CutoutType.CENTER_HOLE.name) } catch (_: Exception) { CutoutType.CENTER_HOLE }
        return IslandPreset.ALL_PRESETS.firstOrNull { it.type == type } ?: IslandPreset.getDefaultPreset()
    }

    fun setPreset(newPreset: IslandPreset) {
        _preset.value = newPreset
        prefs.edit().putString("preset_type", newPreset.type.name).apply()
    }

    fun setCustomOffsets(x: Int, y: Int) {
        _customXOffset.value = x
        _customYOffset.value = y
        prefs.edit().putInt("custom_x_offset", x).putInt("custom_y_offset", y).apply()
    }

    fun setEnabled(enabled: Boolean) {
        _isEnabled.value = enabled
        prefs.edit().putBoolean("island_enabled", enabled).apply()
    }

    fun setOverlayServiceEnabled(enabled: Boolean) {
        _isOverlayServiceEnabled.value = enabled
        prefs.edit().putBoolean("overlay_service_enabled", enabled).apply()
    }

    fun toggleExpanded() {
        _isExpanded.value = !_isExpanded.value
    }

    fun collapse() {
        _isExpanded.value = false
    }

    fun startWorkout(title: String = "Musculação") {
        _workoutTitle.value = title
        _workoutSeconds.value = 0
        _workoutCalories.value = 0.0
        _isWorkoutActive.value = true
        _isExpanded.value = false
    }

    fun updateWorkoutProgress(seconds: Int, calories: Double) {
        _workoutSeconds.value = seconds
        _workoutCalories.value = calories
    }

    fun stopWorkout() {
        _isWorkoutActive.value = false
        _isExpanded.value = false
    }

    fun updateNutritionState(water: Int, targetWater: Int, remainingCalories: Int) {
        _waterCurrent.value = water
        _waterTarget.value = targetWater
        _caloriesRemaining.value = remainingCalories
    }

    companion object {
        @Volatile
        private var instance: DynamicIslandState? = null

        fun getInstance(context: Context): DynamicIslandState {
            return instance ?: synchronized(this) {
                instance ?: DynamicIslandState(context.applicationContext).also { instance = it }
            }
        }
    }
}

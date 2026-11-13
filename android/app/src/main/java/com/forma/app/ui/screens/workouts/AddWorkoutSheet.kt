package com.forma.app.ui.screens.workouts

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.forma.app.data.model.WorkoutEntry
import com.forma.app.data.model.WorkoutType
import com.forma.app.ui.components.FormaModalSheet
import com.forma.app.ui.components.FormaSheetTopBar
import com.forma.app.ui.components.dismissKeyboardOnTap
import com.forma.app.ui.components.formaHumanizeErrorMessage
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/** Porte de `AddWorkoutView` (iOS): "Registrar Treino" manual (tipo, nome, duração, calorias e observações). */
@Composable
fun AddWorkoutSheet(onDismiss: () -> Unit) {
    val colors = FormaTheme.Colors
    val scope = rememberCoroutineScope()
    val repository = remember { workoutsRepository() }

    var title by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf(WorkoutType.BODYBUILDING) }
    var durationMinutes by remember { mutableDoubleStateOf(50.0) }
    var caloriesBurned by remember { mutableDoubleStateOf(350.0) }
    var notes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var typeMenuExpanded by remember { mutableStateOf(false) }

    fun defaultCalories(type: WorkoutType, minutes: Double): Double = when (type) {
        WorkoutType.BODYBUILDING -> minutes * 7.0
        WorkoutType.RUNNING -> minutes * 11.0
        WorkoutType.WALKING -> minutes * 4.5
        WorkoutType.CYCLING -> minutes * 9.0
        WorkoutType.FUNCTIONAL -> minutes * 8.5
        WorkoutType.SWIMMING -> minutes * 10.0
        WorkoutType.OTHER -> minutes * 6.0
    }

    fun save() {
        if (isSaving) return
        val finalTitle = if (title.trim().isEmpty()) selectedType.rawValue else title
        val workout = WorkoutEntry(
            title = finalTitle,
            workoutType = selectedType.rawValue,
            durationMinutes = durationMinutes.toInt(),
            caloriesBurned = caloriesBurned,
            notes = notes,
            workoutDate = System.currentTimeMillis()
        )
        isSaving = true
        saveErrorMessage = null
        scope.launch {
            try {
                repository.insertWorkoutEntryRemoteFirst(workout)
                isSaving = false
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isSaving = false
                saveErrorMessage = formaHumanizeErrorMessage(e.message.orEmpty())
            }
        }
    }

    FormaModalSheet(onDismissRequest = { if (!isSaving) onDismiss() }) {
        FormaSheetTopBar(
            title = "Registrar Treino",
            cancelTitle = "Cancelar",
            onCancel = onDismiss,
            confirmTitle = if (isSaving) "Salvando..." else "Salvar",
            onConfirm = ::save,
            confirmEnabled = !isSaving
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .dismissKeyboardOnTap()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            FormSection(title = "Tipo de Treino") {
                // Picker(.menu) — Modalidade
                Box {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .formaPlainClickable { typeMenuExpanded = true }
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Modalidade",
                            color = colors.primaryText,
                            style = FormaTypography.body,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = workoutTypeIcon(selectedType),
                            contentDescription = null,
                            tint = colors.brand,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "  ${selectedType.rawValue}",
                            color = colors.brand,
                            style = FormaTypography.body
                        )
                        Icon(Icons.Filled.ExpandMore, contentDescription = null, tint = colors.brand, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(expanded = typeMenuExpanded, onDismissRequest = { typeMenuExpanded = false }) {
                        WorkoutType.values().forEach { type ->
                            DropdownMenuItem(
                                text = { Text(type.rawValue, color = colors.primaryText) },
                                leadingIcon = { Icon(workoutTypeIcon(type), contentDescription = null, tint = colors.primaryText) },
                                onClick = {
                                    typeMenuExpanded = false
                                    selectedType = type
                                    caloriesBurned = defaultCalories(type, durationMinutes)
                                }
                            )
                        }
                    }
                }
                FormDivider()
                FormTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = "Nome do Treino (ex: Peito e Tríceps)",
                    singleLine = true
                )
            }

            FormSection(title = "Duração e Intensidade") {
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Duração", color = colors.primaryText, style = FormaTypography.body, modifier = Modifier.weight(1f))
                        Text(
                            text = "${durationMinutes.toInt()} min",
                            color = colors.brand,
                            style = FormaTypography.body.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Slider(
                        value = durationMinutes.toFloat(),
                        onValueChange = {
                            durationMinutes = it.toDouble()
                            caloriesBurned = defaultCalories(selectedType, durationMinutes)
                        },
                        valueRange = 10f..180f,
                        steps = 33,
                        colors = SliderDefaults.colors(thumbColor = colors.brand, activeTrackColor = colors.brand)
                    )
                }
                FormDivider()
                Column(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Calorias Estimadas", color = colors.primaryText, style = FormaTypography.body, modifier = Modifier.weight(1f))
                        Text(
                            text = "${caloriesBurned.toInt()} kcal",
                            color = colors.energyGreen,
                            style = FormaTypography.body.copy(fontWeight = FontWeight.Bold)
                        )
                    }
                    Slider(
                        value = caloriesBurned.toFloat().coerceIn(50f, 1200f),
                        onValueChange = { caloriesBurned = it.toDouble() },
                        valueRange = 50f..1200f,
                        steps = 114,
                        colors = SliderDefaults.colors(thumbColor = colors.energyGreen, activeTrackColor = colors.energyGreen)
                    )
                }
            }

            FormSection(title = "Observações e Cargas") {
                FormTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    placeholder = "Ex: Supino 4x10 (30kg cada lado), agachamento...",
                    singleLine = false,
                    minLines = 3
                )
            }
        }
    }

    saveErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { saveErrorMessage = null },
            title = { Text("Erro ao Sincronizar Treino") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { saveErrorMessage = null }) { Text("OK", color = colors.brand) }
            }
        )
    }
}

/** Seção estilo `Form { Section("...") }` do iOS (cabeçalho em caixa-alta secundário + linhas agrupadas). */
@Composable
internal fun FormSection(title: String, content: @Composable () -> Unit) {
    val colors = FormaTheme.Colors
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = title.uppercase(PtBr),
            color = colors.secondaryText,
            style = FormaTypography.footnote,
            modifier = Modifier.padding(start = 16.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(10.dp))
                .background(colors.secondaryGroupedBackground)
        ) {
            content()
        }
    }
}

@Composable
internal fun FormDivider() {
    Box(
        modifier = Modifier
            .padding(start = 16.dp)
            .fillMaxWidth()
            .height(0.5.dp)
            .background(FormaTheme.Colors.separator)
    )
}

@Composable
internal fun FormTextField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean,
    minLines: Int = 1
) {
    val colors = FormaTheme.Colors
    Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
        if (value.isEmpty()) {
            Text(text = placeholder, color = colors.tertiaryText, style = FormaTypography.body)
        }
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            minLines = minLines,
            maxLines = if (singleLine) 1 else 6,
            textStyle = TextStyle(fontSize = 17.sp, color = colors.primaryText),
            cursorBrush = SolidColor(colors.brand),
            keyboardOptions = KeyboardOptions(imeAction = if (singleLine) ImeAction.Done else ImeAction.Default),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

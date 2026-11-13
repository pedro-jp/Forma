package com.forma.app.ui.screens.evolution

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forma.app.FormaApplication
import com.forma.app.ui.components.FormaModalSheet
import com.forma.app.ui.components.FormaSheetTopBar
import com.forma.app.ui.components.formaHumanizeErrorMessage
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.sfSymbol
import com.forma.app.ui.screens.profile.FormaSlider
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

private val ptBR = Locale("pt", "BR")

/**
 * Porte de `LogWeightView` (iOS): registro de nova pesagem corporal com recálculo contextual instantâneo
 * de TMB, TDEE e metas. Hospedada como sheet pelas abas Início e Evolução.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LogWeightSheet(onDismiss: () -> Unit) {
    val repository = remember { FormaApplication.instance.repository }
    val scope = rememberCoroutineScope()
    val colors = FormaTheme.Colors

    var weight by remember { mutableStateOf(72.0) }
    var weightText by remember { mutableStateOf("72.0") }
    var date by remember { mutableStateOf(System.currentTimeMillis()) }
    var notes by remember { mutableStateOf("") }
    var isSaving by remember { mutableStateOf(false) }
    var saveErrorMessage by remember { mutableStateOf<String?>(null) }
    var showDatePicker by remember { mutableStateOf(false) }

    // .onAppear
    LaunchedEffect(Unit) {
        val p = repository.userProfile.value
        if (p.currentWeight > 0) {
            weight = p.currentWeight
            weightText = fmt1(p.currentWeight)
        }
    }

    fun updateWeight(newValue: Double, syncText: Boolean) {
        val clamped = maxOf(30.0, minOf(300.0, swiftRound(newValue * 10.0) / 10.0))
        weight = clamped
        if (syncText) weightText = fmt1(clamped)
    }

    val recalcContext = remember { repository.buildRecalculationContext() }
    val previewMetrics = remember(weight) {
        repository.userProfile.value.contextualMetrics(weight, recalcContext)
    }

    FormaModalSheet(onDismissRequest = onDismiss) {
        FormaSheetTopBar(
            title = "Nova Pesagem",
            onCancel = onDismiss,
            confirmTitle = if (isSaving) "Salvando..." else "Salvar",
            confirmEnabled = !isSaving,
            onConfirm = {
                if (!isSaving) {
                    ProfileInputValidator.parseFlexibleWeight(weightText)?.let { weight = it }
                    val finalWeight = weight
                    val finalDate = date
                    val finalNotes = notes
                    isSaving = true
                    saveErrorMessage = null
                    scope.launch {
                        try {
                            repository.createWeightEntryRemoteFirst(finalWeight, finalDate, finalNotes)
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
            }
        )

        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Section("Registro de Peso")
            FormSection(title = "Registro de Peso") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Peso atual",
                        color = colors.primaryText,
                        style = FormaTypography.body,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        imageVector = sfSymbol("minus.circle.fill"),
                        contentDescription = "Diminuir peso",
                        tint = colors.brand,
                        modifier = Modifier
                            .size(26.dp)
                            .formaPlainClickable { updateWeight(weight - 0.1, syncText = true) }
                    )
                    Spacer(Modifier.width(8.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        BasicTextField(
                            value = weightText,
                            onValueChange = { newValue ->
                                weightText = newValue
                                ProfileInputValidator.parseFlexibleWeight(newValue)?.let { weight = it }
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            textStyle = FormaTypography.rounded(24f, FontWeight.Bold).copy(
                                color = colors.brand,
                                textAlign = TextAlign.End
                            ),
                            cursorBrush = SolidColor(colors.brand),
                            modifier = Modifier.width(80.dp)
                        )
                        Spacer(Modifier.width(3.dp))
                        Text(
                            text = "kg",
                            color = colors.secondaryText,
                            style = FormaTypography.body,
                            modifier = Modifier.padding(bottom = 3.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    Icon(
                        imageVector = sfSymbol("plus.circle.fill"),
                        contentDescription = "Aumentar peso",
                        tint = colors.brand,
                        modifier = Modifier
                            .size(26.dp)
                            .formaPlainClickable { updateWeight(weight + 0.1, syncText = true) }
                    )
                }

                HorizontalDivider(color = colors.separator, modifier = Modifier.padding(start = 16.dp))

                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
                    FormaSlider(
                        value = weight.coerceIn(35.0, 160.0),
                        onValueChange = { updateWeight(it, syncText = true) },
                        range = 35.0..160.0,
                        step = 0.1,
                        tint = colors.brand
                    )
                }

                HorizontalDivider(color = colors.separator, modifier = Modifier.padding(start = 16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = sfSymbol("bolt.fill"),
                        contentDescription = null,
                        tint = colors.brand,
                        modifier = Modifier.size(12.dp)
                    )
                    Text(
                        text = "Gasto em repouso: ${previewMetrics.tmb.toIntSafe()} kcal • Nova Meta Forma: ${previewMetrics.suggestedCalories.toIntSafe()} kcal/dia",
                        color = colors.secondaryText,
                        style = FormaTypography.caption2
                    )
                }

                HorizontalDivider(color = colors.separator, modifier = Modifier.padding(start = 16.dp))

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Data da pesagem",
                        color = colors.primaryText,
                        style = FormaTypography.body,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = formatLongDate(date),
                        color = colors.primaryText,
                        style = FormaTypography.body,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(colors.tertiaryFill)
                            .formaPlainClickable { showDatePicker = true }
                            .padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            // Section("Observações")
            FormSection(title = "Observações") {
                Box(modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)) {
                    BasicTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        singleLine = true,
                        textStyle = FormaTypography.body.copy(color = colors.primaryText),
                        cursorBrush = SolidColor(colors.brand),
                        modifier = Modifier.fillMaxWidth(),
                        decorationBox = { inner ->
                            if (notes.isEmpty()) {
                                Text(
                                    text = "Ex: Em jejum pela manhã, pós-treino...",
                                    color = colors.tertiaryText,
                                    style = FormaTypography.body
                                )
                            }
                            inner()
                        }
                    )
                }
            }
        }
    }

    if (showDatePicker) {
        // DatePicker trabalha em UTC: converte o dia local para meia-noite UTC e de volta (preservando a hora atual).
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = localDayToUtcMidnight(date))
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { date = utcMidnightToLocalKeepingTime(it, date) }
                    showDatePicker = false
                }) { Text("OK", color = colors.brand) }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar", color = colors.brand) }
            }
        ) { DatePicker(state = pickerState) }
    }

    val err = saveErrorMessage
    if (err != null) {
        AlertDialog(
            onDismissRequest = { saveErrorMessage = null },
            title = { Text("Erro ao Sincronizar Pesagem") },
            text = { Text(err) },
            confirmButton = {
                TextButton(onClick = { saveErrorMessage = null }) { Text("OK", color = colors.brand) }
            }
        )
    }
}

/** Seção estilo `Form` agrupado do iOS: título pequeno secundário + linhas em superfície arredondada. */
@Composable
private fun FormSection(title: String, content: @Composable () -> Unit) {
    val colors = FormaTheme.Colors
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            color = colors.secondaryText,
            style = FormaTypography.footnote,
            modifier = Modifier.padding(start = 16.dp)
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(colors.secondaryGroupedBackground)
        ) { content() }
    }
}

private fun formatLongDate(millis: Long): String =
    SimpleDateFormat("d 'de' MMM 'de' yyyy", ptBR).format(Date(millis))

private fun localDayToUtcMidnight(millis: Long): Long {
    val local = Calendar.getInstance().apply { timeInMillis = millis }
    return Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
        clear()
        set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
    }.timeInMillis
}

private fun utcMidnightToLocalKeepingTime(utcMillis: Long, base: Long): Long {
    val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = utcMillis }
    val time = Calendar.getInstance().apply { timeInMillis = base }
    return Calendar.getInstance().apply {
        timeInMillis = base
        set(utc.get(Calendar.YEAR), utc.get(Calendar.MONTH), utc.get(Calendar.DAY_OF_MONTH),
            time.get(Calendar.HOUR_OF_DAY), time.get(Calendar.MINUTE), time.get(Calendar.SECOND))
    }.timeInMillis
}

package com.forma.app.ui.screens.nutrition

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.forma.app.FormaApplication
import com.forma.app.data.model.FoodEntry
import com.forma.app.data.model.MealType
import com.forma.app.data.repository.FormaRepository
import com.forma.app.data.services.AIDataConsentManager
import com.forma.app.data.services.AIFoodParserService
import com.forma.app.data.services.SmartFoodSuggestionEngine
import com.forma.app.data.services.VoiceFoodIntent
import com.forma.app.data.services.VoiceRecognitionService
import com.forma.app.ui.components.AIDataConsentSheet
import com.forma.app.ui.components.FormaBanner
import com.forma.app.ui.components.FormaBannerStyle
import com.forma.app.ui.components.FormaCard
import com.forma.app.ui.components.FormaModalSheet
import com.forma.app.ui.components.FormaSheetTopBar
import com.forma.app.ui.components.dismissKeyboardOnTap
import com.forma.app.ui.components.formaHumanizeErrorMessage
import com.forma.app.ui.components.formaPlainClickable
import com.forma.app.ui.components.rememberFormaHaptics
import com.forma.app.ui.components.sfSymbol
import com.forma.app.ui.theme.FormaTheme
import com.forma.app.ui.theme.FormaTypography
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.Date
import kotlin.math.roundToInt

/**
 * Registrador unificado e multimodal de refeições (iOS: `VoiceAddFoodView`) apresentado como sheet:
 * Foto (câmera/galeria), Voz e Texto + Base TACO / Inteligência Forma.
 *
 * Usado por Home (`router.showAddFoodSheet` / `showVoiceFoodSheet` / `showAIFoodSheet`) e pela aba Alimentação.
 *
 * @param initialOpenCamera abre a câmera automaticamente (após o consentimento de IA) em vez de iniciar a voz.
 * @param onDismiss chamado ao cancelar, concluir ou arrastar a sheet para fechar.
 */
@Composable
fun VoiceAddFoodSheet(
    initialOpenCamera: Boolean = false,
    onDismiss: () -> Unit
) {
    FoodLoggerSheet(
        repository = FormaApplication.instance.repository,
        preselectedMeal = null,
        initialOpenCamera = initialOpenCamera,
        onDismiss = onDismiss
    )
}

/** Variante com repositório e refeição pré-selecionada explícitos (aba Alimentação: `preselectedMeal`). */
@Composable
fun FoodLoggerSheet(
    repository: FormaRepository,
    preselectedMeal: MealType?,
    initialOpenCamera: Boolean,
    onDismiss: () -> Unit
) {
    FormaModalSheet(onDismissRequest = onDismiss) {
        VoiceAddFoodContent(
            repository = repository,
            preselectedMeal = preselectedMeal,
            initialOpenCamera = initialOpenCamera,
            onDismiss = onDismiss
        )
    }
}

/** Estado e ações do registrador (equivalente aos `@State` e métodos privados de `VoiceAddFoodView`). */
@Stable
private class FoodLoggerController(
    private val context: Context,
    private val repository: FormaRepository,
    private val scope: CoroutineScope,
    private val appScope: CoroutineScope,
    private val onDismiss: () -> Unit
) {
    val voice: VoiceRecognitionService = VoiceRecognitionService.get(context)
    val consent: AIDataConsentManager = AIDataConsentManager.get(context)

    var inputText by mutableStateOf("")
    var selectedImage by mutableStateOf<Bitmap?>(null)
    var selectedImageData by mutableStateOf<ByteArray?>(null)
    var showAIConsentSheet by mutableStateOf(false)
    private var pendingAIConsentAction: (() -> Unit)? = null

    var editingPortionItem by mutableStateOf<com.forma.app.data.services.VoicePlateItem?>(null)
    var customPortionString by mutableStateOf("")

    var isSavedSuccessfully by mutableStateOf(false)
    var isSavingToDatabase by mutableStateOf(false)
    var saveErrorMessage by mutableStateOf<String?>(null)

    /** Erros locais da UI (ex.: foto inválida, câmera negada); os do serviço vêm de `voice.errorMessage`. */
    var localErrorMessage by mutableStateOf<String?>(null)

    var wasFieldFocused = false
    private var textDebounceJob: Job? = null
    private var pendingMicManual = false
    private var pendingCameraUri: Uri? = null

    // Ganchos preenchidos pela composição (launchers e controles de UI)
    var requestMicPermission: () -> Unit = {}
    var requestCameraPermission: () -> Unit = {}
    var launchTakePicture: (Uri) -> Unit = {}
    var launchGallery: () -> Unit = {}
    var dismissKeyboard: () -> Unit = {}
    var hapticLight: () -> Unit = {}
    var hapticSuccess: () -> Unit = {}

    private fun hasPermission(permission: String) =
        context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED

    // region Ciclo de vida

    fun onAppear(preselectedMeal: MealType?, initialOpenCamera: Boolean) {
        voice.reset() // errorMessage = nil + estado limpo
        val defaultMeal = preselectedMeal ?: AIFoodParserService.get(context).inferPrimaryMealType()
        voice.updateMealType(defaultMeal)
        if (initialOpenCamera) {
            voice.stopListening(shouldAnalyze = false)
            scope.launch {
                delay(250)
                runWithAIConsent { openCamera() }
            }
        } else {
            startVoiceFlow()
        }
    }

    fun onDisappear() {
        voice.stopListening(shouldAnalyze = false)
        textDebounceJob?.cancel()
        voice.reset()
    }

    fun cancel() {
        textDebounceJob?.cancel()
        voice.stopListening(shouldAnalyze = false)
        onDismiss()
    }

    // endregion

    // region Consentimento de IA

    fun runWithAIConsent(action: () -> Unit) {
        if (consent.isConsentGranted()) {
            action()
        } else {
            pendingAIConsentAction = action
            showAIConsentSheet = true
        }
    }

    fun onConsentGranted() {
        showAIConsentSheet = false
        val action = pendingAIConsentAction
        pendingAIConsentAction = null
        scope.launch {
            delay(250)
            action?.invoke()
        }
    }

    fun onConsentDeclined() {
        showAIConsentSheet = false
        pendingAIConsentAction = null
        if (selectedImage != null && (voice.currentIntent.value?.items?.isEmpty() ?: true)) {
            clearSelectedPhoto()
        }
    }

    // endregion

    // region Análise

    private fun clearErrors() {
        localErrorMessage = null
        voice.clearError()
    }

    fun triggerManualAIAnalysis(forceAI: Boolean) {
        textDebounceJob?.cancel()
        dismissKeyboard()
        voice.stopListening(shouldAnalyze = false)
        localErrorMessage = null

        val imgData = selectedImageData
        if (imgData != null) {
            runWithAIConsent { voice.analyzePhoto(imageData = imgData, hintText = inputText) }
        } else {
            val trimmed = inputText.trim()
            val fallback = voice.currentIntent.value?.rawText.orEmpty()
            val query = if (trimmed.isNotEmpty()) trimmed else fallback
            if (query.isEmpty()) return
            if (forceAI) {
                runWithAIConsent { voice.analyzeIntent(query, forceAI = true, allowCloudAI = true) }
            } else {
                voice.analyzeIntent(query, forceAI = false, allowCloudAI = consent.isConsentGranted())
            }
        }
    }

    fun handleTextChange(newText: String) {
        inputText = newText
        textDebounceJob?.cancel()
        if (selectedImageData != null) return

        val trimmed = newText.trim()
        if (trimmed.isEmpty()) return

        if (!voice.isRecording.value) {
            textDebounceJob = scope.launch {
                delay(450)
                voice.analyzeIntent(trimmed, forceAI = false, allowCloudAI = false)
            }
        }
    }

    fun onFieldFocusChanged(nowFocused: Boolean) {
        val wasFocused = wasFieldFocused
        wasFieldFocused = nowFocused
        if (nowFocused && voice.isRecording.value) {
            voice.stopListening(shouldAnalyze = false)
        } else if (wasFocused && !nowFocused && !voice.isRecording.value) {
            textDebounceJob?.cancel()
            val trimmed = inputText.trim()
            if (selectedImageData == null &&
                trimmed.isNotEmpty() &&
                !voice.isIncompleteFragment(trimmed) &&
                !voice.hasDanglingConnectorOrNumber(trimmed)
            ) {
                voice.analyzeIntent(trimmed, forceAI = false, allowCloudAI = true)
            }
        }
    }

    fun clearInput() {
        inputText = ""
        localErrorMessage = null
        voice.reset()
    }

    // endregion

    // region Voz

    fun startVoiceFlow() {
        pendingMicManual = false
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
            voice.onPermissionResult(true)
            if (selectedImage == null) voice.startListening()
        } else {
            requestMicPermission()
        }
    }

    fun onMicPermissionResult(granted: Boolean) {
        voice.onPermissionResult(granted)
        if (granted) {
            if (pendingMicManual || selectedImage == null) voice.startListening()
        } else {
            // Exibe a mensagem de permissão negada do serviço no banner de erro.
            voice.startListening()
        }
        pendingMicManual = false
    }

    fun toggleRecording() {
        textDebounceJob?.cancel()
        if (voice.isRecording.value) {
            voice.stopListening(shouldAnalyze = selectedImageData == null)
            val imgData = selectedImageData
            if (imgData != null) {
                runWithAIConsent { voice.analyzePhoto(imageData = imgData, hintText = inputText) }
            }
        } else {
            dismissKeyboard()
            localErrorMessage = null
            if (hasPermission(Manifest.permission.RECORD_AUDIO)) {
                voice.onPermissionResult(true)
                voice.startListening()
            } else {
                pendingMicManual = true
                requestMicPermission()
            }
        }
    }

    // endregion

    // region Foto

    fun onCameraButton() {
        textDebounceJob?.cancel()
        dismissKeyboard()
        voice.stopListening(shouldAnalyze = false)
        runWithAIConsent { openCamera() }
    }

    fun onGalleryButton() {
        textDebounceJob?.cancel()
        dismissKeyboard()
        voice.stopListening(shouldAnalyze = false)
        launchGallery()
    }

    /** Abre a câmera (ou a galeria quando o aparelho não tem câmera, como o fallback do iOS). */
    private fun openCamera() {
        if (!context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY)) {
            launchGallery()
            return
        }
        if (hasPermission(Manifest.permission.CAMERA)) takePicture() else requestCameraPermission()
    }

    private fun takePicture() {
        val uri = createFoodCameraUri(context)
        pendingCameraUri = uri
        launchTakePicture(uri)
    }

    fun onCameraPermissionResult(granted: Boolean) {
        if (granted) {
            takePicture()
        } else {
            localErrorMessage =
                "Permissão da câmera negada. Ative o acesso à câmera nas configurações do aparelho para fotografar a refeição."
        }
    }

    fun onCameraResult(success: Boolean) {
        val uri = pendingCameraUri
        pendingCameraUri = null
        if (success && uri != null) handlePickedUri(uri)
    }

    fun handlePickedUri(uri: Uri) {
        textDebounceJob?.cancel()
        dismissKeyboard()
        voice.stopListening(shouldAnalyze = false)
        localErrorMessage = null

        scope.launch {
            val picked = loadFoodImage(context, uri)
            if (picked == null) {
                localErrorMessage = "Não foi possível processar a foto selecionada."
                return@launch
            }
            selectedImage = picked.preview
            selectedImageData = picked.jpegData
            runWithAIConsent { voice.analyzePhoto(imageData = picked.jpegData, hintText = inputText) }
        }
    }

    fun clearSelectedPhoto() {
        selectedImage = null
        selectedImageData = null
        clearErrors()
        val trimmed = inputText.trim()
        if (trimmed.isEmpty()) {
            voice.reset()
        } else {
            voice.analyzeIntent(trimmed, forceAI = false, allowCloudAI = true)
        }
    }

    // endregion

    // region Prato / confirmação

    fun editExactPortion(item: com.forma.app.data.services.VoicePlateItem) {
        customPortionString = "${item.portionValue.roundToInt()}"
        editingPortionItem = item
    }

    fun applyCustomPortion() {
        val item = editingPortionItem
        if (item != null) {
            val value = customPortionString.replace(",", ".").toDoubleOrNull()
            if (value != null && value > 0) voice.updateItemPortion(item.id, value)
        }
        editingPortionItem = null
    }

    fun addSuggestion(suggestion: com.forma.app.data.services.SmartFoodSuggestion, mealType: MealType) {
        textDebounceJob?.cancel()
        dismissKeyboard()
        hapticLight()
        voice.addSuggestedPlateItem(suggestion.toPlateItem(), mealType)
    }

    fun confirmFoodEntry() {
        val intent: VoiceFoodIntent = voice.currentIntent.value ?: return
        if (isSavingToDatabase || intent.items.isEmpty()) return

        val now = System.currentTimeMillis()
        val entries = intent.items.map { item ->
            FoodEntry(
                name = item.name,
                brand = item.brand,
                mealType = intent.mealType,
                portionGrams = item.portionValue,
                calories = item.calories,
                protein = item.protein,
                carbohydrates = item.carbs,
                fat = item.fat,
                consumedAt = now
            )
        }

        isSavingToDatabase = true
        saveErrorMessage = null

        appScope.launch {
            try {
                repository.insertFoodEntriesRemoteFirst(entries)
                hapticSuccess()
                isSavingToDatabase = false
                isSavedSuccessfully = true
                delay(900)
                onDismiss()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                isSavingToDatabase = false
                saveErrorMessage = formaHumanizeErrorMessage(e.localizedMessage ?: e.message.orEmpty())
            }
        }
    }

    // endregion
}

@Composable
private fun ColumnScope.VoiceAddFoodContent(
    repository: FormaRepository,
    preselectedMeal: MealType?,
    initialOpenCamera: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val colors = FormaTheme.Colors
    val scope = rememberCoroutineScope()
    val appScope = remember { CoroutineScope(SupervisorJob() + Dispatchers.Main) }
    val haptics = rememberFormaHaptics()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val currentOnDismiss by androidx.compose.runtime.rememberUpdatedState(onDismiss)

    val c = remember { FoodLoggerController(context, repository, scope, appScope) { currentOnDismiss() } }
    c.dismissKeyboard = {
        focusManager.clearFocus()
        keyboard?.hide()
    }
    c.hapticLight = { haptics.impactLight() }
    c.hapticSuccess = { haptics.success() }

    // Launchers (permissões, câmera e galeria)
    val micPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        c.onMicPermissionResult(it)
    }
    val cameraPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) {
        c.onCameraPermissionResult(it)
    }
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) {
        c.onCameraResult(it)
    }
    val galleryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) c.handlePickedUri(uri)
    }
    c.requestMicPermission = { micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO) }
    c.requestCameraPermission = { cameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
    c.launchTakePicture = { takePictureLauncher.launch(it) }
    c.launchGallery = {
        galleryLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
    }

    val voice = c.voice
    val isRecording by voice.isRecording.collectAsState()
    val isAnalyzing by voice.isAnalyzing.collectAsState()
    val analysisPhase by voice.analysisPhase.collectAsState()
    val currentIntent by voice.currentIntent.collectAsState()
    val recognizedText by voice.recognizedText.collectAsState()
    val serviceError by voice.errorMessage.collectAsState()
    val selectedMealType by voice.selectedMealType.collectAsState()
    val audioLevel by voice.audioLevel.collectAsState()
    val foodEntries by repository.foodEntries.collectAsState()

    DisposableEffect(Unit) {
        onDispose { c.onDisappear() }
    }
    LaunchedEffect(Unit) {
        c.onAppear(preselectedMeal, initialOpenCamera)
    }
    LaunchedEffect(recognizedText) {
        if (recognizedText.isNotEmpty() && voice.isRecording.value) c.inputText = recognizedText
    }

    val activeMealType = currentIntent?.mealType ?: selectedMealType
    val smartSuggestions = remember(foodEntries, activeMealType) {
        SmartFoodSuggestionEngine.suggestions(
            entries = foodEntries,
            mealType = activeMealType,
            referenceDate = Date(),
            limit = 5
        )
    }
    val currentPlateNames = remember(currentIntent) {
        (currentIntent?.items ?: emptyList()).map { it.name.lowercase() }.toSet()
    }

    FormaSheetTopBar(
        title = "Adicionar Refeição",
        onCancel = { c.cancel() }
    )

    if (c.isSavedSuccessfully) {
        FoodSavedConfirmationView(
            intent = currentIntent,
            onDone = { onDismiss() },
            modifier = Modifier.weight(1f)
        )
    } else {
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .dismissKeyboardOnTap()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = FormaTheme.Spacing.screenHorizontal, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(FormaTheme.Spacing.sectionGap)
        ) {
            InputCard(
                c = c,
                isRecording = isRecording,
                isAnalyzing = isAnalyzing,
                analysisPhase = analysisPhase,
                currentIntent = currentIntent,
                activeMealType = activeMealType,
                audioLevel = audioLevel,
                errorMessage = c.localErrorMessage ?: serviceError
            )

            val intent = currentIntent
            if (intent != null && intent.items.isNotEmpty()) {
                FoodPlateResultCard(
                    intent = intent,
                    isAnalyzing = isAnalyzing,
                    isSaving = c.isSavingToDatabase,
                    onUpdateMealType = { voice.updateMealType(it) },
                    onRecalculateAI = { c.triggerManualAIAnalysis(forceAI = true) },
                    onAdjustPortion = { id, delta -> voice.adjustItemPortion(id, delta) },
                    onEditExactPortion = { c.editExactPortion(it) },
                    onRemoveItem = { voice.removeItem(it) },
                    onConfirm = { c.confirmFoodEntry() }
                )
            }

            if (c.selectedImage == null) {
                FoodQuickSuggestionsSection(
                    mealType = activeMealType,
                    suggestions = smartSuggestions,
                    currentPlateNames = currentPlateNames,
                    onSelectSuggestion = { c.addSuggestion(it, activeMealType) }
                )
            }
        }
    }

    // Consentimento de IA
    if (c.showAIConsentSheet) {
        AIDataConsentSheet(
            onGranted = { c.onConsentGranted() },
            onDeclined = { c.onConsentDeclined() }
        )
    }

    // Alert: Ajustar Quantidade
    c.editingPortionItem?.let { item ->
        val isLiquid = item.isLiquid || item.unit == "ml"
        AlertDialog(
            onDismissRequest = { c.editingPortionItem = null },
            containerColor = colors.cardSurface,
            title = { Text("Ajustar ${item.name}", color = colors.primaryText) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Informe o peso ou volume exato em ${if (isLiquid) "ml" else "gramas (g)"}:",
                        color = colors.secondaryText,
                        style = FormaTypography.subheadline
                    )
                    OutlinedTextField(
                        value = c.customPortionString,
                        onValueChange = { c.customPortionString = it },
                        placeholder = { Text(if (item.isLiquid) "Quantidade em ml" else "Quantidade em g") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal)
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { c.applyCustomPortion() }) { Text("Aplicar", color = colors.brand) }
            },
            dismissButton = {
                TextButton(onClick = { c.editingPortionItem = null }) { Text("Cancelar", color = colors.brand) }
            }
        )
    }

    // Alert: Erro ao Sincronizar Refeição
    c.saveErrorMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { c.saveErrorMessage = null },
            containerColor = colors.cardSurface,
            title = { Text("Erro ao Sincronizar Refeição", color = colors.primaryText) },
            text = { Text(message, color = colors.secondaryText, style = FormaTypography.subheadline) },
            confirmButton = {
                TextButton(onClick = { c.saveErrorMessage = null }) { Text("OK", color = colors.brand) }
            }
        )
    }
}

@Composable
private fun InputCard(
    c: FoodLoggerController,
    isRecording: Boolean,
    isAnalyzing: Boolean,
    analysisPhase: com.forma.app.data.services.AnalysisPhase,
    currentIntent: VoiceFoodIntent?,
    activeMealType: MealType,
    audioLevel: Float,
    errorMessage: String?
) {
    val colors = FormaTheme.Colors
    val voice = c.voice
    val image = c.selectedImage
    val hasImage = image != null
    val trimmedInput = c.inputText.trim()

    FormaCard(modifier = Modifier.fillMaxWidth(), padding = 18.dp) {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // Cabeçalho do card
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = if (isRecording) "Ouvindo você..." else if (hasImage) "Foto da refeição" else "O que você comeu?",
                    color = if (isRecording) colors.brand else colors.secondaryText,
                    style = FormaTypography.subheadline.copy(fontWeight = FontWeight.SemiBold),
                    maxLines = 1,
                    modifier = Modifier.weight(1f)
                )

                if (isAnalyzing) {
                    FoodAnalysisPhaseBadge(phase = analysisPhase)
                } else {
                    if (c.inputText.isNotEmpty() && !hasImage) {
                        Icon(
                            imageVector = sfSymbol("xmark.circle.fill"),
                            contentDescription = "Limpar",
                            tint = colors.secondaryText.copy(alpha = 0.6f),
                            modifier = Modifier
                                .formaPlainClickable { c.clearInput() }
                                .size(18.dp)
                        )
                    }
                    if (currentIntent?.items?.isEmpty() ?: true) {
                        MealTypeSelectMenu(selectedMeal = activeMealType, onSelect = { voice.updateMealType(it) })
                    }
                }
            }

            // Foto: câmera + galeria
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.brand)
                        .formaPlainClickable { c.onCameraButton() }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(sfSymbol("camera.fill"), contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
                    Text(
                        text = if (!hasImage) "Tirar Foto" else "Nova Foto",
                        color = Color.White,
                        style = FormaTypography.rounded(13f, FontWeight.SemiBold),
                        maxLines = 1
                    )
                }

                Row(
                    modifier = Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(12.dp))
                        .background(colors.brandSoft)
                        .formaPlainClickable { c.onGalleryButton() }
                        .padding(vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Filled.PhotoLibrary, contentDescription = null, tint = colors.brand, modifier = Modifier.size(15.dp))
                    Text(
                        text = "Galeria",
                        color = colors.brand,
                        style = FormaTypography.rounded(13f, FontWeight.SemiBold),
                        maxLines = 1
                    )
                }
            }

            // Pré-visualização da foto
            if (image != null) {
                Box(modifier = Modifier.fillMaxWidth()) {
                    val shape = RoundedCornerShape(14.dp)
                    val bitmap = remember(image) { image.asImageBitmap() }
                    Image(
                        bitmap = bitmap,
                        contentDescription = "Foto da refeição",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(175.dp)
                            .clip(shape)
                            .border(1.dp, colors.brand.copy(alpha = 0.25f), shape)
                    )
                    Row(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(10.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.45f))
                            .formaPlainClickable { c.clearSelectedPhoto() }
                            .padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(sfSymbol("xmark"), contentDescription = null, tint = Color.White, modifier = Modifier.size(11.dp))
                        Text(
                            text = "Remover",
                            color = Color.White,
                            style = FormaTypography.system(11f, FontWeight.Bold)
                        )
                    }
                }
            }

            // Campo de texto + microfone
            val fieldShape = RoundedCornerShape(16.dp)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(fieldShape)
                    .background(colors.systemBackground)
                    .border(
                        width = if (isRecording) 1.5.dp else 0.5.dp,
                        color = if (isRecording) colors.brand else colors.primary.copy(alpha = 0.08f),
                        shape = fieldShape
                    )
                    .padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.Top
            ) {
                BasicTextField(
                    value = c.inputText,
                    onValueChange = { c.handleTextChange(it) },
                    minLines = 2,
                    maxLines = 5,
                    textStyle = FormaTypography.system(15f).copy(color = colors.primaryText),
                    cursorBrush = SolidColor(colors.brand),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { c.triggerManualAIAnalysis(forceAI = false) }),
                    modifier = Modifier
                        .weight(1f)
                        .onFocusChanged { c.onFieldFocusChanged(it.isFocused) },
                    decorationBox = { inner ->
                        Box {
                            if (c.inputText.isEmpty()) {
                                Text(
                                    text = if (hasImage) "Observação opcional (ex: arroz integral, sem óleo)..."
                                    else "Fale, digite ou fotografe (ex: 4 colheres de arroz e 2 de feijão)",
                                    color = colors.tertiaryText,
                                    style = FormaTypography.system(15f)
                                )
                            }
                            inner()
                        }
                    }
                )

                // Botão do microfone com pulso conforme o nível de áudio
                val pulse by animateFloatAsState(
                    targetValue = if (isRecording) 1f + audioLevel.coerceIn(0f, 1f) * 0.55f else 1f,
                    label = "micPulse"
                )
                Box(
                    modifier = Modifier
                        .size(44.dp)
                        .formaPlainClickable { c.toggleRecording() },
                    contentAlignment = Alignment.Center
                ) {
                    if (isRecording) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .graphicsLayer { scaleX = pulse; scaleY = pulse }
                                .clip(CircleShape)
                                .background(colors.brand.copy(alpha = 0.18f))
                        )
                    }
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(if (isRecording) colors.brand else colors.primary.copy(alpha = 0.06f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isRecording) Icons.Filled.GraphicEq else sfSymbol("mic.fill"),
                            contentDescription = if (isRecording) "Parar gravação" else "Gravar por voz",
                            tint = if (isRecording) Color.White else colors.brand,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            if (!errorMessage.isNullOrEmpty()) {
                FormaBanner(
                    message = errorMessage,
                    style = FormaBannerStyle.Error,
                    retryTitle = "Repetir",
                    onRetry = if (c.selectedImageData != null || trimmedInput.isNotEmpty()) {
                        { c.triggerManualAIAnalysis(forceAI = true) }
                    } else null
                )
            }

            if (!isAnalyzing) {
                if (c.selectedImageData != null) {
                    AIActionButton(
                        title = if (trimmedInput.isEmpty()) "Identificar Prato com Inteligência Forma"
                        else "Recalcular Foto com Inteligência Forma"
                    ) { c.triggerManualAIAnalysis(forceAI = true) }
                } else if (trimmedInput.isNotEmpty()) {
                    AIActionButton(title = "Identificar Macros com Inteligência Forma") {
                        c.triggerManualAIAnalysis(forceAI = true)
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(
                    imageVector = if (hasImage) Icons.Filled.Tune else Icons.Filled.TouchApp,
                    contentDescription = null,
                    tint = colors.secondaryText,
                    modifier = Modifier.size(13.dp)
                )
                Text(
                    text = if (hasImage) "Ajuste as quantidades abaixo ou descreva detalhes do preparo."
                    else "Tire uma foto do prato, fale pelo microfone ou digite o que comeu.",
                    color = colors.secondaryText,
                    style = FormaTypography.caption,
                    textAlign = TextAlign.Start
                )
            }
        }
    }
}

@Composable
private fun AIActionButton(title: String, onClick: () -> Unit) {
    val colors = FormaTheme.Colors
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .shadow(
                elevation = 6.dp,
                shape = shape,
                ambientColor = colors.aiAccent.copy(alpha = 0.22f),
                spotColor = colors.aiAccent.copy(alpha = 0.22f)
            )
            .clip(shape)
            .background(colors.aiGradient)
            .formaPlainClickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(sfSymbol("sparkles"), contentDescription = null, tint = Color.White, modifier = Modifier.size(15.dp))
        Text(
            text = title,
            color = Color.White,
            style = FormaTypography.rounded(13f, FontWeight.Bold),
            maxLines = 1
        )
    }
}

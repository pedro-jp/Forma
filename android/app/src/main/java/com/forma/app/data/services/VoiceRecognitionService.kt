package com.forma.app.data.services

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.core.content.ContextCompat
import com.forma.app.data.model.MealType
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.Calendar
import java.util.Locale

/**
 * Reconhecimento de voz (pt-BR) + análise semântica de alimentos. Porta de `VoiceRecognitionService` (iOS),
 * usando `android.speech.SpeechRecognizer`.
 *
 * **Threading:** chame [startListening]/[stopListening] na thread principal. Todos os estados são `StateFlow`.
 *
 * **Permissão:** o app precisa pedir `Manifest.permission.RECORD_AUDIO` (use [REQUIRED_PERMISSION] com
 * `rememberLauncherForActivityResult(RequestPermission())` e repasse o resultado em [onPermissionResult]).
 * [checkPermissions] sincroniza [permissionStatus] com o sistema.
 *
 * **Fluxo (modo FOOD):** [startListening] -> [isRecording] = true -> [recognizedText] (parcial/final) ->
 * silêncio (2,5 s, ou 4 s se a frase termina em conector/numeral pendente) ou resultado final ->
 * [stopListening] com análise -> [analyzeIntent] -> [isAnalyzing]/[analysisPhase] -> [currentIntent].
 * No modo WORKOUT apenas transcreve (o recognizer é reiniciado automaticamente até [stopListening]).
 */
class VoiceRecognitionService private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val parser get() = AIFoodParserService.get(appContext)

    private val _isRecording = MutableStateFlow(false)
    val isRecording: StateFlow<Boolean> = _isRecording.asStateFlow()

    private val _recognizedText = MutableStateFlow("")
    val recognizedText: StateFlow<String> = _recognizedText.asStateFlow()

    /** 0.0 a 1.0 para o visualizador de ondas. */
    private val _audioLevel = MutableStateFlow(0f)
    val audioLevel: StateFlow<Float> = _audioLevel.asStateFlow()

    private val _permissionStatus = MutableStateFlow(VoicePermissionStatus.NOT_DETERMINED)
    val permissionStatus: StateFlow<VoicePermissionStatus> = _permissionStatus.asStateFlow()

    private val _listeningMode = MutableStateFlow(VoiceListeningMode.FOOD)
    val listeningMode: StateFlow<VoiceListeningMode> = _listeningMode.asStateFlow()

    private val _currentIntent = MutableStateFlow<VoiceFoodIntent?>(null)
    val currentIntent: StateFlow<VoiceFoodIntent?> = _currentIntent.asStateFlow()

    private val _alternativeMatches = MutableStateFlow<List<NutritionalFoodItem>>(emptyList())
    val alternativeMatches: StateFlow<List<NutritionalFoodItem>> = _alternativeMatches.asStateFlow()

    private val _isAnalyzing = MutableStateFlow(false)
    val isAnalyzing: StateFlow<Boolean> = _isAnalyzing.asStateFlow()

    private val _analysisPhase = MutableStateFlow(AnalysisPhase.IDLE)
    val analysisPhase: StateFlow<AnalysisPhase> = _analysisPhase.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _selectedMealType = MutableStateFlow(MealType.LUNCH)
    val selectedMealType: StateFlow<MealType> = _selectedMealType.asStateFlow()

    private var recognizer: SpeechRecognizer? = null
    private var silenceDetectionJob: Job? = null
    private var activeAnalysisJob: Job? = null
    private var restartJob: Job? = null
    private var currentAnalysisId = 0
    private var recordingSessionId = 0
    private var lastAnalyzedSignature = ""
    private var preferredMealType: MealType? = null

    /** Texto já consolidado de segmentos anteriores da mesma sessão (o recognizer do Android encerra a cada pausa). */
    private var committedText = ""

    // region Permissões

    fun checkPermissions() {
        val granted = ContextCompat.checkSelfPermission(appContext, REQUIRED_PERMISSION) == PackageManager.PERMISSION_GRANTED
        _permissionStatus.value = when {
            granted -> VoicePermissionStatus.AUTHORIZED
            _permissionStatus.value == VoicePermissionStatus.MICROPHONE_DENIED -> VoicePermissionStatus.MICROPHONE_DENIED
            else -> VoicePermissionStatus.NOT_DETERMINED
        }
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            _permissionStatus.value = VoicePermissionStatus.RESTRICTED
        }
    }

    /** Repasse o resultado do pedido de permissão RECORD_AUDIO. */
    fun onPermissionResult(granted: Boolean) {
        _permissionStatus.value = if (granted) VoicePermissionStatus.AUTHORIZED else VoicePermissionStatus.MICROPHONE_DENIED
    }

    // endregion

    // region Gravação e reconhecimento

    fun startListening(mode: VoiceListeningMode = VoiceListeningMode.FOOD) {
        stopListening(shouldAnalyze = false)
        _listeningMode.value = mode
        recordingSessionId += 1
        val sessionId = recordingSessionId

        _errorMessage.value = null
        _recognizedText.value = ""
        committedText = ""
        _currentIntent.value = null
        _alternativeMatches.value = emptyList()
        lastAnalyzedSignature = ""
        _audioLevel.value = 0f

        checkPermissions()
        if (_permissionStatus.value == VoicePermissionStatus.RESTRICTED) {
            _errorMessage.value = "Reconhecimento de voz indisponível neste dispositivo."
            return
        }
        if (_permissionStatus.value != VoicePermissionStatus.AUTHORIZED) {
            _errorMessage.value = "Não foi possível iniciar o microfone: permissão de microfone necessária."
            return
        }

        if (!beginRecognizerSession(sessionId)) return
        _isRecording.value = true
    }

    private fun beginRecognizerSession(sessionId: Int): Boolean {
        return try {
            recognizer?.destroy()
            val r = SpeechRecognizer.createSpeechRecognizer(appContext)
            r.setRecognitionListener(listenerFor(sessionId))
            recognizer = r
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "pt-BR")
                putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
                putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
                putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
                putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 2500L)
            }
            r.startListening(intent)
            true
        } catch (e: Exception) {
            _errorMessage.value = "Erro ao iniciar captura: ${e.localizedMessage ?: e.javaClass.simpleName}"
            stopListening(shouldAnalyze = false)
            false
        }
    }

    private fun joinText(base: String, extra: String): String =
        listOf(base.trim(), extra.trim()).filter { it.isNotEmpty() }.joinToString(" ")

    private fun listenerFor(sessionId: Int) = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {}
        override fun onBeginningOfSpeech() {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onEvent(eventType: Int, params: Bundle?) {}

        override fun onRmsChanged(rmsdB: Float) {
            if (recordingSessionId != sessionId || !_isRecording.value) return
            // rmsdB ~ -2..10 -> 0.05..1.0
            _audioLevel.value = ((rmsdB + 2f) / 12f).coerceIn(0.05f, 1f)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            if (recordingSessionId != sessionId || !_isRecording.value) return
            val partial = partialResults?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            if (partial.isBlank()) return
            val transcription = joinText(committedText, partial)
            _recognizedText.value = transcription
            if (_listeningMode.value == VoiceListeningMode.FOOD) {
                scheduleSilenceDetection(transcription, sessionId)
            }
        }

        override fun onResults(results: Bundle?) {
            if (recordingSessionId != sessionId || !_isRecording.value) return
            val finalText = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull().orEmpty()
            val transcription = joinText(committedText, finalText)
            _recognizedText.value = transcription

            if (_listeningMode.value == VoiceListeningMode.FOOD) {
                if (transcription.isBlank()) {
                    stopListening(shouldAnalyze = false)
                } else if (hasDanglingConnectorOrNumber(transcription)) {
                    // Frase parou em conector/número pendente: dá mais tempo para o usuário concluir o raciocínio
                    committedText = transcription
                    restartRecognizer(sessionId)
                } else {
                    stopListening(shouldAnalyze = true)
                }
            } else {
                // Modo treino: continua ouvindo até o usuário parar
                committedText = transcription
                restartRecognizer(sessionId)
            }
        }

        override fun onError(error: Int) {
            if (recordingSessionId != sessionId || !_isRecording.value) return
            val hasText = _recognizedText.value.trim().isNotEmpty()
            when (error) {
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    _permissionStatus.value = VoicePermissionStatus.MICROPHONE_DENIED
                    _errorMessage.value = "Não foi possível iniciar o microfone: permissão de microfone negada."
                    stopListening(shouldAnalyze = false)
                }
                SpeechRecognizer.ERROR_NETWORK,
                SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
                SpeechRecognizer.ERROR_SERVER,
                SpeechRecognizer.ERROR_AUDIO -> {
                    if (!hasText) {
                        _errorMessage.value = "Erro ao iniciar captura: reconhecimento de voz indisponível no momento."
                    }
                    stopListening(shouldAnalyze = _listeningMode.value == VoiceListeningMode.FOOD && hasText)
                }
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    if (_listeningMode.value == VoiceListeningMode.WORKOUT && !hasText) {
                        // Silêncio inicial no modo treino: segue ouvindo
                        restartRecognizer(sessionId)
                    } else {
                        stopListening(shouldAnalyze = _listeningMode.value == VoiceListeningMode.FOOD && hasText)
                    }
                }
                else -> stopListening(shouldAnalyze = _listeningMode.value == VoiceListeningMode.FOOD && hasText)
            }
        }
    }

    private fun restartRecognizer(sessionId: Int) {
        restartJob?.cancel()
        restartJob = scope.launch {
            delay(150)
            if (recordingSessionId == sessionId && _isRecording.value) {
                beginRecognizerSession(sessionId)
            }
        }
    }

    fun stopListening(shouldAnalyze: Boolean = true) {
        val wasRecording = _isRecording.value
        recordingSessionId += 1

        silenceDetectionJob?.cancel(); silenceDetectionJob = null
        restartJob?.cancel(); restartJob = null

        recognizer?.let {
            runCatching { it.stopListening() }
            runCatching { it.cancel() }
            runCatching { it.destroy() }
        }
        recognizer = null

        _isRecording.value = false
        _audioLevel.value = 0f

        if (shouldAnalyze && wasRecording && _listeningMode.value == VoiceListeningMode.FOOD) {
            val trimmed = _recognizedText.value.trim()
            if (trimmed.isNotEmpty() && !isIncompleteFragment(trimmed)) {
                analyzeIntent(trimmed, forceAI = false, allowCloudAI = true)
            }
        }
    }

    // endregion

    // region Análise e detecção de término de fala

    private fun scheduleSilenceDetection(currentText: String, sessionId: Int) {
        silenceDetectionJob?.cancel()
        val trimmed = currentText.trim()
        if (trimmed.isEmpty()) return

        // Se a frase terminar em conector/número pendente, dá mais tempo antes de encerrar o microfone.
        val waitMs = if (hasDanglingConnectorOrNumber(trimmed)) 4_000L else 2_500L

        silenceDetectionJob = scope.launch {
            delay(waitMs)
            if (!isActive || !_isRecording.value || recordingSessionId != sessionId) return@launch
            stopListening(shouldAnalyze = true)
        }
    }

    /** Detecta se a fala atual parou no meio de uma preposição, conjunção ou numeral incompleto. */
    fun hasDanglingConnectorOrNumber(text: String): Boolean {
        val words = text.lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
        val last = words.lastOrNull() ?: return true
        val danglingWords = setOf(
            "e", "com", "de", "da", "do", "dos", "das", "no", "na", "nos", "nas",
            "um", "uma", "dois", "duas", "três", "tres", "quatro", "cinco",
            "meio", "meia", "cento", "duzentos", "trezentos", "quatrocentos", "quinhentos",
            "mais", "para", "pro", "pra", "cada"
        )
        if (last in danglingWords) return true
        // Terminou apenas com um número solto após conector (ex: "... e 150", "... com 35")
        if (last.replace(",", ".").toDoubleOrNull() != null) return true
        return false
    }

    /** Evita enviar fragmentos curtos sem nenhum alimento real (ex: "Cento e", "Dois", "150 g de") para a IA. */
    fun isIncompleteFragment(text: String): Boolean {
        val extracted = extractFoodQuery(text)
        if (extracted.isEmpty() || isPureMeasureQuery(extracted)) return true
        val meaningfulWords = extracted.lowercase().split(WHITESPACE).filter { it.isNotEmpty() }
        val numberWords = setOf(
            "cento", "cem", "duzentos", "trezentos", "quatrocentos", "quinhentos",
            "um", "uma", "dois", "duas", "três", "tres", "quatro", "cinco", "seis",
            "sete", "oito", "nove", "dez", "vinte", "trinta", "quarenta", "cinquenta",
            "sessenta", "setenta", "oitenta", "noventa"
        )
        val nonNumericWords = meaningfulWords.filter { it !in numberWords && it.toDoubleOrNull() == null }
        return nonNumericWords.isEmpty()
    }

    fun analyzeIntent(spokenText: String, forceAI: Boolean = false, allowCloudAI: Boolean = true) {
        val trimmed = spokenText.trim()
        if (trimmed.isEmpty()) return

        if (!forceAI) {
            val cleanQuery = extractFoodQuery(trimmed)

            // Fala com apenas quantidade/medida (ex: "100g", "200 gramas"): ajusta o primeiro item
            if (cleanQuery.isEmpty() || isPureMeasureQuery(cleanQuery)) {
                val portion = extractPortionGrams(trimmed)
                _currentIntent.value?.items?.firstOrNull()?.let { updateItemPortion(it.id, portion) }
                return
            }

            // Se não há alimento válido (apenas numeral como "Cento e"), nunca dispara IA
            if (isIncompleteFragment(trimmed)) return
        }

        // Deduplicação contra chamadas idênticas consecutivas
        val signature = "${trimmed.lowercase()}|force:$forceAI|cloud:$allowCloudAI"
        if (signature == lastAnalyzedSignature &&
            (_isAnalyzing.value || _currentIntent.value?.rawText?.lowercase() == trimmed.lowercase())
        ) return
        lastAnalyzedSignature = signature

        val preservedMealType = _currentIntent.value?.mealType
        currentAnalysisId += 1
        val analysisId = currentAnalysisId
        activeAnalysisJob?.cancel()

        if (allowCloudAI || forceAI) {
            _isAnalyzing.value = true
            _analysisPhase.value = if (forceAI) AnalysisPhase.ARTIFICIAL_INTELLIGENCE else AnalysisPhase.TACO_DATABASE
        }

        activeAnalysisJob = scope.launch {
            val parseResult = parser.parseMeal(
                description = trimmed,
                forceAI = forceAI,
                allowCloudAI = allowCloudAI,
                onPhaseChange = { newPhase ->
                    scope.launch {
                        if (currentAnalysisId == analysisId && (allowCloudAI || forceAI)) {
                            _analysisPhase.value = newPhase
                        }
                    }
                }
            )
            if (!isActive) return@launch
            if (currentAnalysisId != analysisId) return@launch
            _isAnalyzing.value = false
            _analysisPhase.value = AnalysisPhase.IDLE

            if (parseResult.items.isEmpty()) return@launch

            val plateItems = parseResult.items.map { item ->
                VoicePlateItem.create(
                    name = item.matchedFoodName,
                    brand = item.brand,
                    portionValue = item.portionValue,
                    unit = item.unit,
                    isLiquid = item.isLiquid,
                    calories = item.calories,
                    protein = item.protein,
                    carbs = item.carbohydrates,
                    fat = item.fat,
                    source = item.source
                )
            }
            _currentIntent.value = VoiceFoodIntent(
                rawText = trimmed,
                mealType = preservedMealType ?: preferredMealType ?: parseResult.mealType,
                items = plateItems
            )
        }
    }

    /** Analisa foto da refeição (visão multimodal) e preenche os itens ajustáveis do prato. */
    fun analyzePhoto(imageData: ByteArray, hintText: String = "") {
        stopListening(shouldAnalyze = false)
        activeAnalysisJob?.cancel()

        val preservedMealType = _currentIntent.value?.mealType ?: preferredMealType
        currentAnalysisId += 1
        val analysisId = currentAnalysisId
        lastAnalyzedSignature = ""
        _errorMessage.value = null
        _isAnalyzing.value = true
        _analysisPhase.value = AnalysisPhase.PHOTO_VISION

        activeAnalysisJob = scope.launch {
            try {
                val extracted = GeminiAIService.get(appContext).analyzeMealImage(
                    imageData = imageData,
                    mimeType = "image/jpeg",
                    additionalContext = hintText
                )
                if (!isActive || currentAnalysisId != analysisId) return@launch
                _isAnalyzing.value = false
                _analysisPhase.value = AnalysisPhase.IDLE

                if (extracted.items.isEmpty()) {
                    _errorMessage.value =
                        "Não foi possível identificar alimentos claros nesta imagem. Tente aproximar a foto ou adicionar uma observação."
                    return@launch
                }

                val plateItems = extracted.items.map { item ->
                    VoicePlateItem.create(
                        name = item.name,
                        brand = "",
                        portionValue = item.portionValue,
                        unit = item.unit,
                        isLiquid = item.isLiquid,
                        calories = item.calories,
                        protein = item.protein,
                        carbs = item.carbohydrates,
                        fat = item.fat,
                        source = "Forma Foto"
                    )
                }
                val resolvedMeal = preservedMealType ?: extractMealType(extracted.mealType)
                val trimmedHint = hintText.trim()
                val labelText = if (trimmedHint.isEmpty()) plateItems.joinToString(", ") { it.name } else trimmedHint
                _currentIntent.value = VoiceFoodIntent(
                    rawText = labelText,
                    mealType = resolvedMeal,
                    items = plateItems,
                    aiSummary = extracted.aiSummary
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (e: Exception) {
                if (currentAnalysisId != analysisId) return@launch
                _isAnalyzing.value = false
                _analysisPhase.value = AnalysisPhase.IDLE
                _errorMessage.value = e.message ?: e.localizedMessage
            }
        }
    }

    // endregion

    // region Edição do prato

    fun updateItemPortion(id: String, newPortion: Double) {
        _currentIntent.update { intent ->
            intent?.copy(items = intent.items.map { if (it.id == id) it.updatePortion(newPortion) else it })
        }
    }

    fun adjustItemPortion(id: String, delta: Double) {
        _currentIntent.update { intent ->
            intent?.copy(items = intent.items.map { if (it.id == id) it.updatePortion(it.portionValue + delta) else it })
        }
    }

    fun removeItem(id: String) {
        _currentIntent.update { intent -> intent?.copy(items = intent.items.filter { it.id != id }) }
    }

    fun updatePortion(grams: Double) {
        _currentIntent.value?.items?.firstOrNull()?.let { updateItemPortion(it.id, grams) }
    }

    fun selectAlternativeMatch(item: NutritionalFoodItem) {
        val intent = _currentIntent.value ?: return
        if (intent.items.isEmpty()) return
        val values = item.values(intent.portionGrams)
        val replaced = VoicePlateItem.create(
            name = item.name,
            brand = item.brand,
            portionValue = intent.portionGrams,
            unit = item.defaultUnit,
            isLiquid = item.isLiquid,
            calories = values.calories,
            protein = values.protein,
            carbs = values.carbs,
            fat = values.fat
        )
        _currentIntent.value = intent.copy(items = listOf(replaced) + intent.items.drop(1))
    }

    fun updateMealType(meal: MealType) {
        preferredMealType = meal
        _selectedMealType.value = meal
        _currentIntent.update { it?.copy(mealType = meal) }
    }

    /** Adiciona uma sugestão inteligente ao prato (soma a porção se o alimento já existir). */
    fun addSuggestedPlateItem(item: VoicePlateItem, mealType: MealType) {
        stopListening(shouldAnalyze = false)
        activeAnalysisJob?.cancel()
        preferredMealType = mealType
        _selectedMealType.value = mealType

        val existing = _currentIntent.value
        if (existing != null && existing.items.isNotEmpty()) {
            val matchIdx = existing.items.indexOfFirst { it.name.equals(item.name, ignoreCase = true) }
            val newItems = existing.items.toMutableList()
            if (matchIdx >= 0) {
                newItems[matchIdx] = newItems[matchIdx].updatePortion(newItems[matchIdx].portionValue + item.portionValue)
            } else {
                newItems.add(item)
            }
            _currentIntent.value = existing.copy(
                mealType = mealType,
                items = newItems,
                rawText = newItems.joinToString(", ") { it.name }
            )
        } else {
            _currentIntent.value = VoiceFoodIntent(rawText = item.name, mealType = mealType, items = listOf(item))
        }
    }

    /** Limpa o estado de análise (útil ao fechar a folha de voz). */
    fun reset() {
        stopListening(shouldAnalyze = false)
        activeAnalysisJob?.cancel()
        currentAnalysisId += 1
        _recognizedText.value = ""
        committedText = ""
        _currentIntent.value = null
        _alternativeMatches.value = emptyList()
        _isAnalyzing.value = false
        _analysisPhase.value = AnalysisPhase.IDLE
        _errorMessage.value = null
        lastAnalyzedSignature = ""
    }

    fun clearError() {
        _errorMessage.value = null
    }

    // endregion

    // region Algoritmos de extração linguística em português

    fun extractMealType(text: String): MealType {
        val lower = text.lowercase()

        if (lower.contains("café da manhã") || lower.contains("cafe da manha") ||
            lower.contains("desjejum") || lower.contains("no café") || lower.contains("no cafe")
        ) return MealType.BREAKFAST

        if (lower.contains("almoço") || lower.contains("almoco") ||
            lower.contains("almocei") || lower.contains("no almoço") || lower.contains("no almoco")
        ) return MealType.LUNCH

        if (lower.contains("jantar") || lower.contains("janta") ||
            lower.contains("jantei") || lower.contains("no jantar") || lower.contains("na janta")
        ) return MealType.DINNER

        if (lower.contains("lanche") || lower.contains("lanchei") ||
            lower.contains("merenda") || lower.contains("ceia") || lower.contains("da tarde")
        ) return MealType.SNACKS

        return when (Calendar.getInstance().get(Calendar.HOUR_OF_DAY)) {
            in 5 until 11 -> MealType.BREAKFAST
            in 11 until 15 -> MealType.LUNCH
            in 15 until 19 -> MealType.SNACKS
            else -> MealType.DINNER
        }
    }

    fun extractPortionGrams(text: String): Double {
        val lower = text.lowercase()

        RX_GRAM.find(lower)?.let { m ->
            val grams = m.groupValues[1].replace(",", ".").toDoubleOrNull()
            if (grams != null && grams > 0) return grams
        }
        RX_COLHER.find(lower)?.let { return parseQuantityWord(it.groupValues[1]) * 15.0 }
        RX_FATIA.find(lower)?.let { return parseQuantityWord(it.groupValues[1]) * 35.0 }
        RX_OVO.find(lower)?.let { return parseQuantityWord(it.groupValues[1]) * 50.0 }

        if (lower.contains("banana")) return parseQuantityWordInContext(lower, "banana") * 90.0
        if (lower.contains("maçã") || lower.contains("maca")) return parseQuantityWordInContext(lower, "maç") * 130.0

        RX_ISOLATED_NUMBER.find(lower)?.let { m ->
            val num = m.groupValues[1].toDoubleOrNull()
            if (num != null && num >= 10) return num
        }
        return 100.0
    }

    private fun parseQuantityWord(word: String): Double {
        val w = word.trim().lowercase()
        w.toDoubleOrNull()?.let { return it }
        return when (w) {
            "um", "uma" -> 1.0
            "dois", "duas" -> 2.0
            "três", "tres" -> 3.0
            "quatro" -> 4.0
            "cinco" -> 5.0
            else -> 1.0
        }
    }

    private fun parseQuantityWordInContext(text: String, keyword: String): Double {
        val regex = Regex("""(\d+|uma?|duas?|três|tres)\s*""" + keyword, RegexOption.IGNORE_CASE)
        regex.find(text)?.let { return parseQuantityWord(it.groupValues[1]) }
        return 1.0
    }

    private fun extractFoodQuery(text: String): String {
        var clean = text.lowercase()

        val mealStopWords = listOf(
            "no café da manhã", "no cafe da manha", "para o café da manhã", "para o cafe da manha",
            "no café", "no cafe", "de desjejum", "no almoço", "no almoco", "para o almoço", "para o almoco",
            "no jantar", "na janta", "para o jantar", "para a janta",
            "de lanche", "no lanche", "no lanchinho", "na ceia"
        )
        for (stop in mealStopWords) clean = clean.replace(stop, " ")

        val verbStopWords = listOf(
            "eu comi", "comi", "acabei de comer", "comemos", "consumi", "ingeri",
            "eu bebi", "bebi", "tomei", "adicione", "registre", "anote", "coloque",
            "por favor", "marca aí", "marca ai", "marca"
        )
        for (verb in verbStopWords) clean = clean.replace(verb, " ")

        for (rx in RX_MEASURES) clean = clean.replace(rx, " ")

        val prepositions = setOf("de", "da", "do", "com", "e", "um", "uma")
        val words = clean.split(WHITESPACE).filter { it.isNotEmpty() }.toMutableList()
        while (words.isNotEmpty() && words.first() in prepositions) words.removeAt(0)
        while (words.isNotEmpty() && words.last() in prepositions) words.removeAt(words.size - 1)
        return words.joinToString(" ").trim()
    }

    private fun isPureMeasureQuery(text: String): Boolean {
        val cleaned = text
            .replace("gramas", "")
            .replace("grama", "")
            .replace("gr", "")
            .replace("g", "")
            .replace("ml", "")
            .replace("mls", "")
            .replace("de", "")
            .trim()
        return cleaned.toDoubleOrNull() != null || cleaned.isEmpty()
    }

    // endregion

    companion object {
        /** Permissão de runtime necessária para o microfone. */
        const val REQUIRED_PERMISSION = Manifest.permission.RECORD_AUDIO

        /** Locale usado no reconhecimento. */
        val RECOGNITION_LOCALE: Locale = Locale("pt", "BR")

        private val WHITESPACE = Regex("\\s+")
        private val IC = setOf(RegexOption.IGNORE_CASE)
        private val RX_GRAM = Regex("""(\d+(?:[.,]\d+)?)\s*(?:gramas?|g\b|gr\b)""", IC)
        private val RX_COLHER = Regex("""(\d+|uma?|duas?|três|tres)\s*colher(?:es)?(?:\s*de\s*sopa)?""", IC)
        private val RX_FATIA = Regex("""(\d+|uma?|duas?|três|tres)\s*fatia[s]?""", IC)
        private val RX_OVO = Regex("""(\d+|um|dois|três|tres|quatro)\s*ovo[s]?""", IC)
        private val RX_ISOLATED_NUMBER = Regex("""^(\d+)\s*(?:de\s+)?""")
        private val RX_MEASURES = listOf(
            """\d+(?:[.,]\d+)?\s*(?:gramas?|g\b|gr\b|ml\b|mililitros?)""",
            """(?:\d+|uma?|duas?|três|tres)\s*colher(?:es)?(?:\s*de\s*sopa)?""",
            """(?:\d+|uma?|duas?|três|tres)\s*fatia[s]?""",
            """(?:\d+|uma?|duas?|três|tres)\s*unidade[s]?""",
            """\b\d+\s*de\b"""
        ).map { Regex(it, IC) }

        @Volatile
        private var instance: VoiceRecognitionService? = null

        fun get(context: Context): VoiceRecognitionService =
            instance ?: synchronized(this) {
                instance ?: VoiceRecognitionService(context).also { instance = it }
            }
    }
}

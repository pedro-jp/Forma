package com.forma.app.data.services

import android.content.Context
import android.util.Base64
import com.forma.app.R
import com.forma.app.data.model.FitnessGoal
import com.forma.app.data.model.RecalculationContext
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

/** Tipos de falha de IA / backend (iOS: `FormaAPIError` + erros `GeminiAI`). */
enum class AiErrorKind {
    CONSENT_REQUIRED,   // consentimento de IA externa ausente (HTTP 403 local)
    AI_BLOCKED,         // IA bloqueada para o usuário (403 aiBlockedForUser / 503 blocked)
    RATE_LIMITED,       // 429
    UNAUTHORIZED,       // 401
    ACCOUNT_DELETED,    // 401 accountDeleted
    ACCOUNT_PAUSED,     // 403 accountPaused
    UNREACHABLE,        // servidor inacessível
    INVALID_RESPONSE,   // JSON inválido / vazio
    EMPTY_INPUT,        // descrição vazia
    SERVER              // demais erros HTTP
}

/**
 * Exceção de IA. `message` já é a mensagem amigável em pt-BR (igual ao `localizedDescription` do iOS)
 * e pode ser mostrada diretamente ao usuário.
 */
class AiServiceException(
    val kind: AiErrorKind,
    override val message: String,
    val statusCode: Int = 0
) : Exception(message)

/** Mensagens de erro (idênticas às do iOS). */
object AiMessages {
    const val INVALID_RESPONSE = "Resposta inválida da Inteligência Forma"
    const val EMPTY_RESPONSE = "Resposta vazia da Inteligência Forma."
    const val EMPTY_MEAL = "Descrição vazia"
    const val EMPTY_WORKOUT = "Descrição do treino vazia"
    const val EMPTY_IMAGE = "Imagem inválida ou vazia"
    const val UNREACHABLE =
        "Não foi possível conectar ao servidor Forma. Verifique sua conexão Wi-Fi ou se o servidor está ativo."
    const val HIGH_DEMAND =
        "A Inteligência Forma está com alta demanda no momento. Aguarde alguns segundos e tente novamente."
    const val LIMIT_REACHED = "Limite temporário de análises atingido. Tente novamente em instantes."

    fun defaultFor(statusCode: Int): String = when (statusCode) {
        400 -> "Não foi possível processar os dados enviados."
        401 -> "Sessão expirada ou credenciais inválidas."
        403 -> "Operação não permitida para esta conta."
        404 -> "Recurso não encontrado no servidor."
        429 -> "Muitas solicitações em sequência. Aguarde alguns segundos."
        502, 503, 504 -> "Serviço temporariamente ocupado. Tente novamente em alguns segundos."
        else -> "Ocorreu um erro temporário no servidor ($statusCode)."
    }

    /** Porta de `FormaAPIClient.humanizeErrorMessage`. */
    fun humanize(raw: String, statusCode: Int = 500): String {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return defaultFor(statusCode)
        if (trimmed.contains("\"error\"") && trimmed.contains("{")) {
            if (trimmed.contains("503") || trimmed.contains("high demand", ignoreCase = true) ||
                trimmed.contains("UNAVAILABLE", ignoreCase = true)
            ) return HIGH_DEMAND
            if (trimmed.contains("429") || trimmed.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) return LIMIT_REACHED
            return defaultFor(statusCode)
        }
        return trimmed
            .replace("API do Gemini", "Inteligência Forma")
            .replace("IA Gemini", "Inteligência Forma")
            .replace("Gemini", "Inteligência Forma")
            .replace("Inteligência Artificial", "Inteligência Forma")
            .replace("com IA", "com a Inteligência Forma")
    }
}

/**
 * Payload da análise de evolução (`/api/ai/analyze-evolution`), idêntico ao dicionário montado pelo iOS em
 * `GeminiAIService.analyzeEvolution`. Calcule as métricas com [ContextualTargets.compute] e o log de
 * atividades dos últimos 14 dias (`AppActivityLogManager.exportRecentLogForAI(days: 14)`).
 */
data class EvolutionAnalysisRequest(
    val userGoal: String,
    val currentWeight: Double,
    val targetWeight: Double,
    val heightMeters: Double,
    val targetCalories: Double,
    val targetCalorieBurn: Double,
    val targetSteps: Double,
    val calculatedTmb: Double,
    val contextualTdee: Double,
    val suggestedBaseCalories: Double,
    val suggestedProtein: Double,
    val suggestedCarbs: Double,
    val suggestedFat: Double,
    val suggestedWaterMl: Int,
    val avgDailyWorkoutBurn: Double,
    val activityLogJson: String,
    val userName: String? = null
) {
    companion object {
        /** Monta o request como o iOS: padrões 72kg/80kg/1.78m/600kcal/8000 passos quando ausentes. */
        fun build(
            goal: FitnessGoal,
            currentWeight: Double,
            targetWeight: Double,
            heightMeters: Double,
            targetCalorieIntake: Double?,
            targetCalorieBurn: Double,
            targetSteps: Double,
            recalcContext: RecalculationContext,
            activityLogJson: String,
            userName: String? = null
        ): EvolutionAnalysisRequest {
            val metrics = ContextualTargets.compute(
                weight = currentWeight,
                height = heightMeters,
                targetWeight = targetWeight,
                goal = goal,
                targetCalorieBurn = targetCalorieBurn,
                targetSteps = targetSteps,
                context = recalcContext
            )
            return EvolutionAnalysisRequest(
                userGoal = goal.title,
                currentWeight = currentWeight,
                targetWeight = targetWeight,
                heightMeters = heightMeters,
                targetCalories = targetCalorieIntake ?: metrics.suggestedCalories,
                targetCalorieBurn = targetCalorieBurn,
                targetSteps = targetSteps,
                calculatedTmb = metrics.tmb,
                contextualTdee = metrics.contextualTdee,
                suggestedBaseCalories = metrics.suggestedCalories,
                suggestedProtein = metrics.proteinGrams,
                suggestedCarbs = metrics.carbsGrams,
                suggestedFat = metrics.fatGrams,
                suggestedWaterMl = metrics.waterMilliliters,
                avgDailyWorkoutBurn = recalcContext.avgDailyWorkoutBurn,
                activityLogJson = activityLogJson,
                userName = userName
            )
        }
    }
}

/**
 * Serviço de integração com a IA (Google Gemini) via proxy seguro do backend. Porta de `GeminiAIService` (iOS).
 *
 * - Todo tráfego de IA vai para `POST {baseUrl}/api/ai/{analyze-meal|analyze-workout|analyze-evolution|suggest-initial-goals}`
 *   com `Authorization: Bearer <jwt>` e `X-User-Id` (lidos de SharedPreferences `forma_prefs`: `jwt_token`, `user_id`,
 *   `server_base_url`, como no BackendApiClient). Nunca usa chave do Gemini no app.
 * - Exige consentimento ([AIDataConsentManager]); sem ele lança [AiServiceException] `CONSENT_REQUIRED`
 *   com a mensagem "Autorize o uso da IA externa nas configurações de Privacidade & Saúde para continuar.".
 * - Rate limit local: janela deslizante de 60s com 12 requisições (aguarda quando estourar).
 * - Cache em memória de refeições e treinos.
 * - Todas as funções são `suspend` e lançam [AiServiceException].
 *
 * Nome do usuário enviado à IA: [userNameProvider] (ex.: nome do perfil) -> `user_name` das prefs -> "Usuário".
 */
class GeminiAIService private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs by lazy { appContext.getSharedPreferences("forma_prefs", Context.MODE_PRIVATE) }
    private val gson = com.google.gson.Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _lastErrorMessage = MutableStateFlow<String?>(null)
    val lastErrorMessage: StateFlow<String?> = _lastErrorMessage.asStateFlow()

    private val _remainingRequestsInWindow = MutableStateFlow(MAX_REQUESTS_PER_MINUTE)
    val remainingRequestsInWindow: StateFlow<Int> = _remainingRequestsInWindow.asStateFlow()

    /** Fornece o nome do perfil (iOS: `Profile.nome`). Se nulo/vazio usa "Usuário". */
    @Volatile
    var userNameProvider: (() -> String?)? = null

    private val requestTimestamps = ArrayList<Long>()
    private val mealCache = ConcurrentHashMap<String, AIExtractedMeal>()
    private val workoutCache = ConcurrentHashMap<String, AIExtractedWorkout>()

    private val currentUserName: String
        get() {
            val fromProvider = runCatching { userNameProvider?.invoke() }.getOrNull()?.trim()
            if (!fromProvider.isNullOrEmpty()) return fromProvider
            val saved = prefs.getString("user_name", null)?.trim()
            return if (saved.isNullOrEmpty()) "Usuário" else saved
        }

    // region Rate limit

    private suspend fun checkAndRecordRateLimit() {
        val waitMs: Long
        synchronized(requestTimestamps) {
            val now = System.currentTimeMillis()
            requestTimestamps.removeAll { now - it >= WINDOW_MS }
            _remainingRequestsInWindow.value = maxOf(0, MAX_REQUESTS_PER_MINUTE - requestTimestamps.size)
            waitMs = if (requestTimestamps.size >= MAX_REQUESTS_PER_MINUTE) {
                val oldest = requestTimestamps.first()
                maxOf(1000L, WINDOW_MS - (now - oldest) + 500L)
            } else 0L
        }
        if (waitMs > 0) delay(waitMs)
        synchronized(requestTimestamps) {
            requestTimestamps.add(System.currentTimeMillis())
            _remainingRequestsInWindow.value = maxOf(0, MAX_REQUESTS_PER_MINUTE - requestTimestamps.size)
        }
    }

    // endregion

    // region 1. Refeições

    /** Analisa descrição textual de refeição (cache por texto minúsculo). */
    suspend fun analyzeMeal(description: String): AIExtractedMeal {
        val trimmed = description.trim()
        if (trimmed.isEmpty()) throw AiServiceException(AiErrorKind.EMPTY_INPUT, AiMessages.EMPTY_MEAL, 400)

        val cacheKey = trimmed.lowercase()
        mealCache[cacheKey]?.let { return it }

        return trackLoading {
            checkAndRecordRateLimit()
            val body = JsonObject().apply {
                addProperty("description", trimmed)
                addProperty("userName", currentUserName)
            }
            val decoded = AIExtractedMeal.fromJson(parseObject(sendBackendAiRequest("analyze-meal", body)))
            mealCache[cacheKey] = decoded
            decoded
        }
    }

    /** Analisa foto do prato, bebida ou tabela nutricional (visão multimodal). */
    suspend fun analyzeMealImage(
        imageData: ByteArray,
        mimeType: String = "image/jpeg",
        additionalContext: String = ""
    ): AIExtractedMeal {
        if (imageData.isEmpty()) throw AiServiceException(AiErrorKind.EMPTY_INPUT, AiMessages.EMPTY_IMAGE, 400)
        return trackLoading {
            checkAndRecordRateLimit()
            val body = JsonObject().apply {
                addProperty("description", additionalContext.trim())
                addProperty("userName", currentUserName)
                addProperty("imageBase64", Base64.encodeToString(imageData, Base64.NO_WRAP))
                addProperty("imageMimeType", mimeType)
            }
            AIExtractedMeal.fromJson(parseObject(sendBackendAiRequest("analyze-meal", body)))
        }
    }

    // endregion

    // region 2. Treinos

    /** Analisa treino e queima calórica; associa automaticamente GIFs do catálogo aos exercícios. */
    suspend fun analyzeWorkout(description: String, userWeight: Double = 75.0): AIExtractedWorkout {
        val trimmed = description.trim()
        if (trimmed.isEmpty()) throw AiServiceException(AiErrorKind.EMPTY_INPUT, AiMessages.EMPTY_WORKOUT, 400)

        val cacheKey = "${trimmed.lowercase()}_${userWeight.toInt()}"
        workoutCache[cacheKey]?.let { return it }

        return trackLoading {
            checkAndRecordRateLimit()
            val body = JsonObject().apply {
                addProperty("description", trimmed)
                addProperty("userWeight", userWeight)
                addProperty("userName", currentUserName)
            }
            val decoded = AIExtractedWorkout.fromJson(parseObject(sendBackendAiRequest("analyze-workout", body)))
            val withGifs = decoded.copy(exercises = decoded.exercises.map { it.withCatalogAssociation() })
            workoutCache[cacheKey] = withGifs
            withGifs
        }
    }

    // endregion

    // region 3. Evolução

    /** Análise inteligente de evolução e recomendações (sem rate limit local, como no iOS). */
    suspend fun analyzeEvolution(request: EvolutionAnalysisRequest): AIEvolutionResult {
        val body = JsonObject().apply {
            addProperty("userGoal", request.userGoal)
            addProperty("currentWeight", request.currentWeight)
            addProperty("targetWeight", request.targetWeight)
            addProperty("heightMeters", request.heightMeters)
            addProperty("targetCalories", request.targetCalories)
            addProperty("targetCalorieBurn", request.targetCalorieBurn)
            addProperty("targetSteps", request.targetSteps)
            addProperty("calculatedTmb", request.calculatedTmb)
            addProperty("contextualTdee", request.contextualTdee)
            addProperty("suggestedBaseCalories", request.suggestedBaseCalories)
            addProperty("suggestedProtein", request.suggestedProtein)
            addProperty("suggestedCarbs", request.suggestedCarbs)
            addProperty("suggestedFat", request.suggestedFat)
            addProperty("suggestedWaterMl", request.suggestedWaterMl)
            addProperty("avgDailyWorkoutBurn", request.avgDailyWorkoutBurn)
            addProperty("activityLogJson", request.activityLogJson)
            addProperty("userName", request.userName?.takeIf { it.isNotBlank() } ?: currentUserName)
        }
        return AIEvolutionResult.fromJson(parseObject(sendBackendAiRequest("analyze-evolution", body)))
    }

    // endregion

    // region 4. Metas iniciais (onboarding)

    /**
     * Sugere metas iniciais. Usa a IA quando há consentimento; em qualquer falha cai no cálculo fisiológico
     * local (Mifflin-St Jeor + objetivo + nível de atividade) - nunca lança exceção.
     * [activityLevel] é o texto do nível ("Leve", "Moderado", "Intenso"...).
     */
    suspend fun suggestInitialGoals(
        userName: String,
        goal: FitnessGoal,
        currentWeight: Double,
        heightMeters: Double,
        targetWeight: Double?,
        activityLevel: String
    ): InitialGoalsAISuggestion {
        val safeName = userName.trim().ifEmpty { "Atleta" }
        val body = JsonObject().apply {
            addProperty("userName", safeName)
            addProperty("goal", goal.title)
            addProperty("currentWeight", currentWeight)
            addProperty("heightMeters", heightMeters)
            addProperty("activityLevel", activityLevel)
            if (targetWeight != null && targetWeight >= 30.0) addProperty("targetWeight", targetWeight)
        }

        if (AIDataConsentManager.isConsentGranted(appContext)) {
            try {
                val decoded = InitialGoalsAISuggestion.fromJson(
                    parseObject(sendBackendAiRequest("suggest-initial-goals", body))
                )
                if (decoded.dailyCalories >= 1000) return decoded
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Fallback fisiológico acionado para metas iniciais
            }
        }

        val lowerAct = activityLevel.lowercase()
        val burn = if (lowerAct.contains("intenso")) 650.0 else if (lowerAct.contains("leve")) 350.0 else 500.0
        val steps = if (lowerAct.contains("intenso")) 10000.0 else if (lowerAct.contains("leve")) 7000.0 else 8500.0
        val idealBmiWeight = 22.5 * heightMeters * heightMeters
        fun round1(v: Double) = swiftRound(v * 10.0) / 10.0
        val resolvedTargetWeight = if (targetWeight != null && targetWeight >= 30.0) {
            round1(targetWeight)
        } else when (goal) {
            FitnessGoal.LOSE -> round1(maxOf(45.0, minOf(currentWeight - 4.0, idealBmiWeight)))
            FitnessGoal.MAINTAIN -> round1(currentWeight)
            FitnessGoal.GAIN -> round1(maxOf(currentWeight + 5.0, idealBmiWeight))
        }

        val metrics = ContextualTargets.compute(
            weight = currentWeight,
            height = heightMeters,
            targetWeight = resolvedTargetWeight,
            goal = goal,
            targetCalorieBurn = burn,
            targetSteps = steps
        )
        val explanation = "$safeName, calculamos sua Taxa Basal (~${metrics.tmb.toInt()} kcal) e gasto diário " +
            "(~${metrics.contextualTdee.toInt()} kcal) para ${String.format(Locale.US, "%.1f", currentWeight)} kg e " +
            "${String.format(Locale.US, "%.2f", heightMeters)} m rumo aos " +
            "${String.format(Locale.US, "%.1f", resolvedTargetWeight)} kg."

        return InitialGoalsAISuggestion(
            suggestedTargetWeight = resolvedTargetWeight,
            dailyCalories = metrics.suggestedCalories,
            proteinGrams = metrics.proteinGrams,
            carbsGrams = metrics.carbsGrams,
            fatGrams = metrics.fatGrams,
            waterMilliliters = metrics.waterMilliliters,
            targetCalorieBurn = burn,
            targetSteps = steps,
            explanation = explanation
        )
    }

    // endregion

    // region HTTP (proxy do backend)

    private suspend fun <T> trackLoading(block: suspend () -> T): T {
        _isLoading.value = true
        _lastErrorMessage.value = null
        try {
            return block()
        } catch (e: AiServiceException) {
            _lastErrorMessage.value = e.message
            throw e
        } finally {
            _isLoading.value = false
        }
    }

    private fun parseObject(json: String): JsonObject =
        try {
            JsonParser.parseString(json).asJsonObject
        } catch (e: AiServiceException) {
            throw e
        } catch (e: Exception) {
            throw AiServiceException(AiErrorKind.INVALID_RESPONSE, AiMessages.INVALID_RESPONSE, 500)
        }

    private val defaultBaseUrl: String by lazy {
        runCatching { appContext.getString(R.string.backend_base_url).trimEnd('/') }
            .getOrDefault("https://forma-94vp.onrender.com")
    }

    private val baseUrl: String
        get() {
            val saved = prefs.getString("server_base_url", null)?.trimEnd('/')
            if (saved.isNullOrBlank()) return defaultBaseUrl
            if (defaultBaseUrl.startsWith("https://") && saved.startsWith("http://")) return defaultBaseUrl
            return saved
        }

    private suspend fun sendBackendAiRequest(path: String, body: JsonObject): String {
        if (!AIDataConsentManager.isConsentGranted(appContext)) {
            throw AiServiceException(AiErrorKind.CONSENT_REQUIRED, AIConsentTexts.CONSENT_REQUIRED_MESSAGE, 403)
        }
        val text = withContext(Dispatchers.IO) {
            val builder = Request.Builder()
                .url("$baseUrl/api/ai/$path")
                .post(body.toString().toRequestBody(jsonMediaType))
            prefs.getString("jwt_token", null)?.takeIf { it.isNotEmpty() }?.let {
                builder.header("Authorization", "Bearer $it")
            }
            prefs.getString("user_id", null)?.takeIf { it.isNotEmpty() }?.let {
                builder.header("X-User-Id", it)
            }
            try {
                client.newCall(builder.build()).execute().use { res ->
                    val raw = res.body?.string().orEmpty()
                    if (!res.isSuccessful) throw mapHttpError(res.code, raw)
                    raw
                }
            } catch (e: IOException) {
                throw AiServiceException(AiErrorKind.UNREACHABLE, AiMessages.UNREACHABLE, 0)
            }
        }
        currentCoroutineContext().ensureActive()
        if (text.isEmpty()) throw AiServiceException(AiErrorKind.SERVER, AiMessages.EMPTY_RESPONSE, 500)
        return stripMarkdownCodeFences(text)
    }

    /** Porta de `FormaAPIClient.mapHttpError`. */
    private fun mapHttpError(statusCode: Int, raw: String): AiServiceException {
        val json: JsonObject? = runCatching { JsonParser.parseString(raw).asJsonObject }.getOrNull()
        val rawMessage = json?.str("error")
            ?: json?.str("message")
            ?: sanitizeRawErrorText(raw, statusCode)
        val clean = AiMessages.humanize(rawMessage, statusCode)

        when (statusCode) {
            401 -> return if (json?.bool("accountDeleted") == true)
                AiServiceException(AiErrorKind.ACCOUNT_DELETED, clean, 401)
            else AiServiceException(AiErrorKind.UNAUTHORIZED, clean, 401)
            403 -> {
                if (json?.bool("accountPaused") == true) return AiServiceException(AiErrorKind.ACCOUNT_PAUSED, clean, 403)
                if (json?.bool("aiBlockedForUser") == true) return AiServiceException(AiErrorKind.AI_BLOCKED, clean, 403)
                return AiServiceException(AiErrorKind.SERVER, clean, 403)
            }
            429 -> return AiServiceException(AiErrorKind.RATE_LIMITED, clean, 429)
            503 -> if (json?.bool("blocked") == true) return AiServiceException(AiErrorKind.AI_BLOCKED, clean, 503)
        }
        return AiServiceException(AiErrorKind.SERVER, clean, statusCode)
    }

    private fun sanitizeRawErrorText(raw: String?, statusCode: Int): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return AiMessages.defaultFor(statusCode)
        if (trimmed.startsWith("{") || trimmed.startsWith("<")) return AiMessages.defaultFor(statusCode)
        return trimmed
    }

    // endregion

    companion object {
        private const val MAX_REQUESTS_PER_MINUTE = 12
        private const val WINDOW_MS = 60_000L

        @Volatile
        private var instance: GeminiAIService? = null

        fun get(context: Context): GeminiAIService =
            instance ?: synchronized(this) {
                instance ?: GeminiAIService(context).also { instance = it }
            }

        /** Remove cercas de código Markdown (```json ... ```) de respostas da IA. */
        fun stripMarkdownCodeFences(raw: String): String {
            var cleaned = raw.trim()
            cleaned = when {
                cleaned.startsWith("```json") -> cleaned.drop(7)
                cleaned.startsWith("```") -> cleaned.drop(3)
                else -> cleaned
            }
            if (cleaned.endsWith("```")) cleaned = cleaned.dropLast(3)
            return cleaned.trim()
        }
    }
}

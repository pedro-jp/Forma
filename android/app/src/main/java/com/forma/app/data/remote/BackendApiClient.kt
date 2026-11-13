package com.forma.app.data.remote

import android.content.Context
import com.forma.app.R
import com.forma.app.data.auth.AuthManager
import com.forma.app.data.auth.SecureStore
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Erros tipados da API (FormaAPIError do iOS). `message` equivale a `errorDescription`. */
sealed class FormaApiException(message: String) : Exception(message) {
    class InvalidUrl : FormaApiException("Endereço de conexão inválido.")
    class UnreachableServer : FormaApiException(
        "Não foi possível conectar ao servidor Forma. Verifique sua conexão Wi-Fi ou se o servidor está ativo."
    )
    class Unauthorized(message: String) : FormaApiException(message)
    class AccountDeleted(message: String) : FormaApiException(message)
    class AccountPaused(message: String) : FormaApiException(message)

    /** HTTP 409 com `accountExists`: carrega o e-mail (e nome) da conta existente. */
    class AccountAlreadyExists(message: String, val email: String, val existingName: String?) : FormaApiException(message)
    class AiBlocked(message: String) : FormaApiException(message)
    class RateLimited(message: String) : FormaApiException(message)
    class ServerError(val statusCode: Int, message: String) : FormaApiException(message)
}

/**
 * Cliente HTTP centralizado (FormaAPIClient + armazenamento de sessão do AuthManager no iOS):
 * injeção de JWT/X-User-Id, interceptação de conta excluída, humanização de erros, cold-start >= 45s em HTTPS.
 */
class BackendApiClient(private val context: Context) {

    private val gson = Gson()
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()
    private val baseClient = OkHttpClient.Builder().build()
    private val secure = SecureStore.get(context)

    private val prefs by lazy { context.getSharedPreferences("forma_prefs", Context.MODE_PRIVATE) }

    /** Definido pelo AuthManager: executado quando o servidor responde 401 accountDeleted (logout automático). */
    @Volatile
    var onAccountDeleted: (() -> Unit)? = null

    private val defaultBaseUrl: String by lazy {
        runCatching { context.getString(R.string.backend_base_url).trim().trimEnd('/') }
            .getOrNull()?.takeIf { it.isNotBlank() } ?: PRODUCTION_URL
    }

    val isConfiguredRemoteHttps: Boolean get() = defaultBaseUrl.lowercase(Locale.ROOT).startsWith("https://")

    var baseUrl: String
        get() {
            val saved = prefs.getString("server_base_url", null)?.trim()?.trimEnd('/')
            if (saved.isNullOrBlank()) return defaultBaseUrl
            if (isConfiguredRemoteHttps && saved.startsWith("http://")) return defaultBaseUrl
            return saved
        }
        set(value) = prefs.edit().putString("server_base_url", value.trim().trimEnd('/')).apply()

    init {
        migrateLegacySession()
        bindServerOrigin()
    }

    // ------------------------------------------------------------------------------------------
    // Sessão (JWT + dados do usuário) em armazenamento seguro
    // ------------------------------------------------------------------------------------------

    var jwtToken: String?
        get() = secure.getString(AuthManager.KEY_TOKEN)
        set(value) = secure.putString(AuthManager.KEY_TOKEN, value)

    var userId: String?
        get() = secure.getString(AuthManager.KEY_USER_ID)
        set(value) = secure.putString(AuthManager.KEY_USER_ID, value)

    var userEmail: String?
        get() = secure.getString(AuthManager.KEY_USER_EMAIL)
        set(value) = secure.putString(AuthManager.KEY_USER_EMAIL, value)

    var userName: String?
        get() = secure.getString(AuthManager.KEY_USER_NAME)
        set(value) = secure.putString(AuthManager.KEY_USER_NAME, value)

    var authProvider: String?
        get() = secure.getString(AuthManager.KEY_AUTH_PROVIDER)
        set(value) = secure.putString(AuthManager.KEY_AUTH_PROVIDER, value)

    var socialProviderId: String?
        get() = secure.getString(AuthManager.KEY_SOCIAL_PROVIDER_ID)
        set(value) = secure.putString(AuthManager.KEY_SOCIAL_PROVIDER_ID, value)

    /** O iOS não possui modo convidado; mantido apenas por compatibilidade (sempre falso). */
    var isGuest: Boolean
        get() = false
        set(@Suppress("UNUSED_PARAMETER") value) {}

    val isAuthenticated: Boolean
        get() = !jwtToken.isNullOrBlank() && !userId.isNullOrBlank()

    /** Apaga as credenciais da sessão (o AuthManager cuida do restante da limpeza). */
    fun clearSession() {
        listOf(
            AuthManager.KEY_TOKEN, AuthManager.KEY_USER_ID, AuthManager.KEY_USER_EMAIL,
            AuthManager.KEY_USER_NAME, AuthManager.KEY_AUTH_PROVIDER, AuthManager.KEY_SOCIAL_PROVIDER_ID
        ).forEach { secure.remove(it) }
    }

    /** Compatibilidade: antigo logout do cliente (apenas credenciais). Prefira AuthManager.logout(). */
    fun logout() = clearSession()

    private fun migrateLegacySession() {
        // Sessões gravadas por versões antigas em SharedPreferences simples "forma_prefs"
        val legacyToken = prefs.getString("jwt_token", null)
        val legacyUser = prefs.getString("user_id", null)
        if (!legacyToken.isNullOrBlank() && !legacyUser.isNullOrBlank() && jwtToken.isNullOrBlank()) {
            jwtToken = legacyToken
            userId = legacyUser
            userEmail = prefs.getString("user_email", null)
            userName = prefs.getString("user_name", null)
        }
        prefs.edit().remove("jwt_token").remove("user_id").remove("user_email").remove("user_name").remove("is_guest").apply()
    }

    private fun bindServerOrigin() {
        val previous = prefs.getString(KEY_BOUND_ORIGIN, null)
        if (isConfiguredRemoteHttps && previous != null && previous != defaultBaseUrl) {
            // Trocou de servidor: invalida apenas o JWT antigo sem apagar os dados locais
            prefs.edit().putString(KEY_BOUND_ORIGIN, defaultBaseUrl).apply()
            jwtToken = null
        } else if (previous == null) {
            prefs.edit().putString(KEY_BOUND_ORIGIN, defaultBaseUrl).apply()
        }
    }

    /** URLs candidatas (apenas a de produção quando HTTPS na nuvem; fallbacks locais caso contrário). */
    val candidateServerUrls: List<String>
        get() {
            if (isConfiguredRemoteHttps) return listOf(defaultBaseUrl)
            val list = mutableListOf<String>()
            list.add(defaultBaseUrl)
            val current = baseUrl
            if (current.isNotBlank()) list.add(current)
            list.addAll(listOf("http://10.0.2.2:8085", "http://localhost:8085", "http://127.0.0.1:8085"))
            return list.map { it.trim().trimEnd('/') }.distinct()
        }

    private fun markWorkingServerUrl(url: String) {
        if (!isConfiguredRemoteHttps && baseUrl != url) baseUrl = url
    }

    // ------------------------------------------------------------------------------------------
    // Requisições
    // ------------------------------------------------------------------------------------------

    /**
     * Executa uma requisição HTTP percorrendo os hosts candidatos (FormaAPIClient.requestData).
     * Retorna o corpo da resposta (2xx) ou lança [FormaApiException].
     */
    suspend fun requestData(
        path: String,
        method: String = "GET",
        jsonBody: Any? = null,
        authenticated: Boolean = true,
        timeoutSeconds: Double = 14.0,
        interceptAccountStatus: Boolean = true
    ): String = withContext(Dispatchers.IO) {
        val normalizedPath = if (path.startsWith("/")) path else "/$path"
        val candidates = candidateServerUrls
        val serializedBody: String? = jsonBody?.let { gson.toJsonTree(it).toString() }

        for ((index, base) in candidates.withIndex()) {
            val cleanBase = base.trim().trimEnd('/')
            val url = "$cleanBase$normalizedPath"
            val isLast = index == candidates.size - 1
            val timeout = if (cleanBase.lowercase(Locale.ROOT).startsWith("https://")) {
                // Em servidores cloud (ex: Render Free Tier), aguarda eventual cold-start do container
                maxOf(timeoutSeconds, 45.0)
            } else {
                if (isLast) timeoutSeconds else minOf(timeoutSeconds, 2.5)
            }

            val builder = try {
                Request.Builder().url(url)
            } catch (_: IllegalArgumentException) {
                continue
            }
            val body = serializedBody?.toRequestBody(jsonMediaType)
            when (method.uppercase(Locale.ROOT)) {
                "GET" -> builder.get()
                "DELETE" -> if (body != null) builder.delete(body) else builder.delete()
                "PUT" -> builder.put(body ?: "".toRequestBody(jsonMediaType))
                "POST" -> builder.post(body ?: "".toRequestBody(jsonMediaType))
                else -> builder.method(method.uppercase(Locale.ROOT), body)
            }
            if (authenticated) {
                jwtToken?.takeIf { it.isNotEmpty() }?.let { builder.header("Authorization", "Bearer $it") }
                userId?.takeIf { it.isNotEmpty() }?.let { builder.header("X-User-Id", it) }
            }

            val timeoutMs = (timeout * 1000).toLong()
            val client = baseClient.newBuilder()
                .connectTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .callTimeout(timeoutMs + 5_000, TimeUnit.MILLISECONDS)
                .build()

            val (code, text) = try {
                client.newCall(builder.build()).execute().use { res ->
                    res.code to res.body?.string().orEmpty()
                }
            } catch (_: IOException) {
                // Host inacessível -> tenta próximo candidato
                continue
            }

            markWorkingServerUrl(cleanBase)

            if (code in 200..299) return@withContext text
            throw mapHttpError(code, text, interceptAccountStatus)
        }
        throw FormaApiException.UnreachableServer()
    }

    private fun mapHttpError(statusCode: Int, text: String, interceptAccountStatus: Boolean): FormaApiException {
        val json: JsonObject? = runCatching {
            val el = JsonParser.parseString(text)
            if (el.isJsonObject) el.asJsonObject else null
        }.getOrNull()

        fun JsonObject.s(key: String): String? = get(key)?.takeIf { it.isJsonPrimitive }?.asString
        fun JsonObject.b(key: String): Boolean? = get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isBoolean }?.asBoolean

        val rawMessage = json?.s("error") ?: json?.s("message") ?: sanitizeRawErrorText(text, statusCode)
        val cleanMessage = humanizeErrorMessage(rawMessage, statusCode)

        if (statusCode == 401) {
            if (json?.b("accountDeleted") == true) {
                if (interceptAccountStatus) onAccountDeleted?.invoke()
                return FormaApiException.AccountDeleted(cleanMessage)
            }
            return FormaApiException.Unauthorized(cleanMessage)
        }
        if (statusCode == 403) {
            if (json?.b("accountPaused") == true) return FormaApiException.AccountPaused(cleanMessage)
            if (json?.b("aiBlockedForUser") == true) return FormaApiException.AiBlocked(cleanMessage)
            return FormaApiException.ServerError(403, cleanMessage)
        }
        if (statusCode == 409 && json?.b("accountExists") == true) {
            return FormaApiException.AccountAlreadyExists(
                message = cleanMessage,
                email = json.s("email") ?: "",
                existingName = json.s("existingName")
            )
        }
        if (statusCode == 429) return FormaApiException.RateLimited(cleanMessage)
        if (statusCode == 503 && json?.b("blocked") == true) return FormaApiException.AiBlocked(cleanMessage)
        return FormaApiException.ServerError(statusCode, cleanMessage)
    }

    private fun sanitizeRawErrorText(raw: String?, statusCode: Int): String {
        val trimmed = raw?.trim().orEmpty()
        if (trimmed.isEmpty()) return defaultMessage(statusCode)
        if (trimmed.startsWith("{") || trimmed.startsWith("<")) return defaultMessage(statusCode)
        return trimmed
    }

    // ------------------------------------------------------------------------------------------
    // Sincronização (pull completo) e proxy de IA
    // ------------------------------------------------------------------------------------------

    suspend fun pullSyncData(): Result<String> = runCatching {
        val uid = java.net.URLEncoder.encode(userId.orEmpty(), "UTF-8")
        requestData(path = "/api/sync/pull?userId=$uid", method = "GET", authenticated = true, timeoutSeconds = 25.0)
    }

    suspend fun analyzeMeal(description: String, userName: String): Result<String> = runCatching {
        requestData(
            path = "/api/ai/analyze-meal", method = "POST",
            jsonBody = mapOf("description" to description, "userName" to userName),
            authenticated = true, timeoutSeconds = 60.0
        )
    }

    suspend fun analyzeWorkout(description: String, userWeight: Double, userName: String): Result<String> = runCatching {
        requestData(
            path = "/api/ai/analyze-workout", method = "POST",
            jsonBody = mapOf("description" to description, "userWeight" to userWeight, "userName" to userName),
            authenticated = true, timeoutSeconds = 60.0
        )
    }

    suspend fun analyzeEvolution(
        goal: String,
        weight: Double,
        targetWeight: Double,
        heightMeters: Double,
        targetCalories: Double,
        targetCalorieBurn: Double,
        targetSteps: Double,
        calculatedTmb: Double,
        contextualTdee: Double,
        suggestedBaseCalories: Double,
        avgDailyWorkoutBurn: Double,
        logJson: String,
        userName: String
    ): Result<String> = runCatching {
        requestData(
            path = "/api/ai/analyze-evolution", method = "POST",
            jsonBody = mapOf(
                "userGoal" to goal,
                "currentWeight" to weight,
                "targetWeight" to targetWeight,
                "heightMeters" to heightMeters,
                "targetCalories" to targetCalories,
                "targetCalorieBurn" to targetCalorieBurn,
                "targetSteps" to targetSteps,
                "calculatedTmb" to calculatedTmb,
                "contextualTdee" to contextualTdee,
                "suggestedBaseCalories" to suggestedBaseCalories,
                "avgDailyWorkoutBurn" to avgDailyWorkoutBurn,
                "activityLogJson" to logJson,
                "userName" to userName
            ),
            authenticated = true, timeoutSeconds = 90.0
        )
    }

    companion object {
        const val PRODUCTION_URL = "https://forma-94vp.onrender.com"
        private const val KEY_BOUND_ORIGIN = "forma_bound_server_origin"

        /** Equivalente a FormaAPIClient.humanizeErrorMessage. */
        fun humanizeErrorMessage(raw: String, statusCode: Int = 500): String {
            val trimmed = raw.trim()
            if (trimmed.isEmpty()) return defaultMessage(statusCode)

            if (trimmed.contains("\"error\"") && trimmed.contains("{")) {
                if (trimmed.contains("503") || trimmed.contains("high demand", ignoreCase = true) ||
                    trimmed.contains("UNAVAILABLE", ignoreCase = true)
                ) {
                    return "A Inteligência Forma está com alta demanda no momento. Aguarde alguns segundos e tente novamente."
                }
                if (trimmed.contains("429") || trimmed.contains("RESOURCE_EXHAUSTED", ignoreCase = true)) {
                    return "Limite temporário de análises atingido. Tente novamente em instantes."
                }
                return defaultMessage(statusCode)
            }

            return trimmed
                .replace("API do Gemini", "Inteligência Forma")
                .replace("IA Gemini", "Inteligência Forma")
                .replace("Gemini", "Inteligência Forma")
                .replace("Inteligência Artificial", "Inteligência Forma")
                .replace("com IA", "com a Inteligência Forma")
        }

        fun defaultMessage(statusCode: Int): String = when (statusCode) {
            400 -> "Não foi possível processar os dados enviados."
            401 -> "Sessão expirada ou credenciais inválidas."
            403 -> "Operação não permitida para esta conta."
            404 -> "Recurso não encontrado no servidor."
            429 -> "Muitas solicitações em sequência. Aguarde alguns segundos."
            502, 503, 504 -> "Serviço temporariamente ocupado. Tente novamente em alguns segundos."
            else -> "Ocorreu um erro temporário no servidor ($statusCode)."
        }
    }
}

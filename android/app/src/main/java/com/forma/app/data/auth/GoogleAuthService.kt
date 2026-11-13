package com.forma.app.data.auth

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Base64
import androidx.browser.customtabs.CustomTabsIntent
import com.forma.app.R
import com.forma.app.data.remote.BackendApiClient
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import java.security.MessageDigest
import java.security.SecureRandom

/** Resultado do login Google (equivalente a `GoogleAuthService.GoogleUserResult` do iOS). */
data class GoogleUserResult(
    val idToken: String,
    val refreshToken: String? = null,
    val accessToken: String? = null,
    val sub: String? = null,
    val email: String? = null,
    val name: String? = null
)

/** Erro de autenticação Google com mensagem em português (equivalente ao NSError "GoogleAuth"). */
class GoogleAuthException(message: String) : Exception(message)

/**
 * Google OAuth 2.0 + PKCE (porte de `GoogleAuthService.swift`).
 *
 * Fluxo: abre `accounts.google.com/o/oauth2/v2/auth` em Chrome Custom Tabs com redirect
 * `com.googleusercontent.apps.<id>:/oauth2redirect`; o retorno chega à MainActivity por intent-filter
 * (`MainActivity.onNewIntent` -> [handleRedirect]) e o `code` é trocado por tokens em
 * `https://oauth2.googleapis.com/token` (sem client_secret). Cancelamento do usuário lança [CancellationException].
 *
 * A MainActivity deve chamar [handleRedirect] em onCreate/onNewIntent e [onActivityResumed] em onResume.
 */
class GoogleAuthService private constructor(private val appContext: Context) {

    private val tokenStore = GoogleOAuthTokenStore.from(appContext)
    private val httpClient = OkHttpClient()
    private val random = SecureRandom()

    @Volatile
    private var pending: CompletableDeferred<Uri?>? = null

    @Volatile
    private var redirectScheme: String = ""

    val storedOAuthToken: String? get() = tokenStore.storedOAuthToken

    fun clearStoredOAuthToken() = tokenStore.clear()

    /** Busca a configuração pública (GOOGLE_CLIENT_ID) do backend; falhas são ignoradas silenciosamente. */
    suspend fun fetchConfigFromBackend(api: BackendApiClient) {
        try {
            val data = api.requestData(
                path = "/api/auth/config",
                method = "GET",
                authenticated = false,
                timeoutSeconds = 4.0,
                interceptAccountStatus = false
            )
            val json: JsonObject = JsonParser.parseString(data).asJsonObject
            val id = json.get("googleClientId")?.takeIf { it.isJsonPrimitive }?.asString?.trim()
            if (!id.isNullOrEmpty()) cachedClientId = id
        } catch (_: Exception) {
            // Ignora: permite fallback para o valor embutido no build
        }
    }

    /** Inicia o fluxo completo (PKCE). Lança [CancellationException] se o usuário fechar a aba do Google. */
    suspend fun startSignIn(activity: Activity, api: BackendApiClient, rawNonce: String = ""): GoogleUserResult {
        if (clientId(appContext).isEmpty()) fetchConfigFromBackend(api)

        val clientId = clientId(appContext)
        if (clientId.isEmpty()) {
            throw GoogleAuthException(
                "O Client ID do Google não foi configurado. Defina a variável GOOGLE_CLIENT_ID no arquivo .env do backend ou em gradle.properties."
            )
        }

        // 1. PKCE (RFC 7636)
        val codeVerifier = generateCodeVerifier()
        val codeChallenge = generateCodeChallenge(codeVerifier)

        val scheme = reverseClientIdScheme(appContext)
        val redirectUri = "$scheme:/oauth2redirect"
        redirectScheme = scheme

        val authUri = Uri.parse("https://accounts.google.com/o/oauth2/v2/auth").buildUpon()
            .appendQueryParameter("client_id", clientId)
            .appendQueryParameter("redirect_uri", redirectUri)
            .appendQueryParameter("response_type", "code")
            .appendQueryParameter("code_challenge", codeChallenge)
            .appendQueryParameter("code_challenge_method", "S256")
            .appendQueryParameter("scope", "openid profile email")
            .appendQueryParameter("prompt", "select_account")
            .build()

        val deferred = CompletableDeferred<Uri?>()
        pending?.cancel()
        pending = deferred

        try {
            try {
                CustomTabsIntent.Builder().setShowTitle(true).build().launchUrl(activity, authUri)
            } catch (_: Exception) {
                throw GoogleAuthException("Não foi possível abrir a autenticação do Google.")
            }

            val callback = deferred.await()
            // Aguarda o fechamento da aba (mesma folga de 150ms do iOS)
            delay(150)
            val params = parseCallbackParams(callback ?: throw GoogleAuthException("Nenhum retorno recebido do Google."))
            params["error"]?.let { throw GoogleAuthException("Erro de autorização do Google: $it") }
            val code = params["code"]?.takeIf { it.isNotEmpty() }
                ?: throw GoogleAuthException("Código de autorização não recebido do Google.")

            // 2. Troca do código pelos tokens (PKCE, sem client_secret)
            return exchangeCodeForTokens(code, codeVerifier, clientId, redirectUri)
        } finally {
            if (pending === deferred) pending = null
        }
    }

    /** Entrega o redirect OAuth recebido pela MainActivity. Retorna true se a intent era do redirect do Google. */
    fun handleRedirect(intent: Intent?): Boolean {
        val uri = intent?.data ?: return false
        val scheme = uri.scheme?.lowercase() ?: return false
        if (!scheme.startsWith(REDIRECT_SCHEME_PREFIX)) return false
        val deferred = pending
        if (deferred != null) {
            deferred.complete(uri)
        }
        // Evita reprocessar a mesma intent após recriação da Activity
        intent.data = null
        return true
    }

    /**
     * Chamado em `onResume`: se a aba do Google foi fechada sem redirect, cancela o fluxo silenciosamente
     * (equivalente a `ASWebAuthenticationSessionError.canceledLogin`).
     */
    suspend fun onActivityResumed() {
        val deferred = pending ?: return
        delay(600)
        if (pending === deferred && !deferred.isCompleted) {
            deferred.cancel(CancellationException("Login Google cancelado pelo usuário."))
        }
    }

    private suspend fun exchangeCodeForTokens(
        code: String,
        codeVerifier: String,
        clientId: String,
        redirectUri: String
    ): GoogleUserResult = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("code", code)
            .add("code_verifier", codeVerifier)
            .add("grant_type", "authorization_code")
            .add("redirect_uri", redirectUri)
            .build()
        val request = Request.Builder().url("https://oauth2.googleapis.com/token").post(body).build()

        val (status, payload) = try {
            httpClient.newCall(request).execute().use { it.code to it.body?.string().orEmpty() }
        } catch (e: java.io.IOException) {
            throw GoogleAuthException("Erro ao trocar código por token com Google.")
        }

        val json: JsonObject? = runCatching { JsonParser.parseString(payload).asJsonObject }.getOrNull()
        if (status != 200) {
            val desc = json?.get("error_description")?.takeIf { it.isJsonPrimitive }?.asString
                ?: "Erro ao trocar código por token com Google."
            throw GoogleAuthException(desc)
        }
        fun str(key: String): String? = json?.get(key)?.takeIf { it.isJsonPrimitive }?.asString

        val idToken = str("id_token")?.takeIf { it.isNotEmpty() }
            ?: throw GoogleAuthException("ID Token não retornado pelo Google.")
        val refreshToken = str("refresh_token")?.trim()
        val accessToken = str("access_token")?.trim()
        val revocable = if (!refreshToken.isNullOrEmpty()) refreshToken else accessToken
        if (!revocable.isNullOrEmpty()) tokenStore.save(revocable)

        val claims = decodeJwtClaims(idToken)
        fun claim(key: String): String? = claims?.get(key)?.takeIf { it.isJsonPrimitive }?.asString

        GoogleUserResult(
            idToken = idToken,
            refreshToken = refreshToken,
            accessToken = accessToken,
            sub = claim("sub"),
            email = claim("email"),
            name = claim("name")
        )
    }

    // PKCE ---------------------------------------------------------------------------------------
    private fun generateCodeVerifier(): String {
        val bytes = ByteArray(32).also { random.nextBytes(it) }
        return base64Url(bytes)
    }

    private fun generateCodeChallenge(verifier: String): String =
        base64Url(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.UTF_8)))

    private fun base64Url(bytes: ByteArray): String =
        Base64.encodeToString(bytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)

    private fun parseCallbackParams(uri: Uri): Map<String, String> {
        val params = linkedMapOf<String, String>()
        fun parse(raw: String?) {
            if (raw.isNullOrEmpty()) return
            for (item in raw.split("&")) {
                val parts = item.split("=", limit = 2)
                if (parts.size == 2) {
                    params[Uri.decode(parts[0])] = Uri.decode(parts[1].replace("+", " "))
                }
            }
        }
        parse(uri.encodedQuery)
        parse(uri.encodedFragment)
        return params
    }

    private fun decodeJwtClaims(jwt: String): JsonObject? {
        val parts = jwt.split(".")
        if (parts.size < 2) return null
        return runCatching {
            val decoded = Base64.decode(parts[1], Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            JsonParser.parseString(String(decoded, Charsets.UTF_8)).asJsonObject
        }.getOrNull()
    }

    companion object {
        private const val REDIRECT_SCHEME_PREFIX = "com.googleusercontent.apps."
        private const val SUFFIX = ".apps.googleusercontent.com"

        @Volatile
        private var cachedClientId: String? = null

        @Volatile
        private var instance: GoogleAuthService? = null

        fun get(context: Context): GoogleAuthService =
            instance ?: synchronized(this) {
                instance ?: GoogleAuthService(context.applicationContext).also { instance = it }
            }

        /**
         * Ordem de resolução (como no iOS): 1) valor embutido no build (gradle.properties GOOGLE_CLIENT_ID ->
         * recurso `google_client_id`), 2) configuração carregada do backend (`/api/auth/config`).
         */
        fun clientId(context: Context): String {
            val embedded = runCatching { context.getString(R.string.google_client_id).trim() }.getOrDefault("")
            if (embedded.isNotEmpty()) return embedded
            return cachedClientId?.trim().orEmpty()
        }

        /** URL scheme reverso exigido pelo Google OAuth (ex.: com.googleusercontent.apps.12345). */
        fun reverseClientIdScheme(context: Context): String {
            val id = clientId(context)
            if (id.contains(SUFFIX)) return REDIRECT_SCHEME_PREFIX + id.replace(SUFFIX, "")
            return "forma"
        }
    }
}

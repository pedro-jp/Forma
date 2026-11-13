package com.forma.app.data.auth

import android.content.Context
import com.forma.app.data.remote.BackendApiClient
import com.forma.app.data.remote.FormaApiException
import com.forma.app.data.repository.AuthUser
import com.forma.app.data.sync.BackendSyncManager
import com.forma.app.data.sync.LocalDataGateway
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * Equivalente ao AuthManager do iOS: estado de sessão observável + fluxos de login, cadastro, login social,
 * recuperação/alteração de senha, exclusão de conta e logout com isolamento total multi-usuário.
 */
class AuthManager(
    private val context: Context,
    val api: BackendApiClient
) {
    companion object {
        // Chaves de armazenamento seguro (idênticas ao iOS para compatibilidade de sessão)
        const val KEY_TOKEN = "forma_jwt_token"
        const val KEY_USER_ID = "forma_user_id"
        const val KEY_USER_EMAIL = "forma_user_email"
        const val KEY_USER_NAME = "forma_user_name"
        const val KEY_AUTH_PROVIDER = "forma_auth_provider"
        const val KEY_SOCIAL_PROVIDER_ID = "forma_social_provider_id"
    }

    private val secure = SecureStore.get(context)
    private val googleTokenStore = GoogleOAuthTokenStore.from(context)

    /** Ligados pelo FormaRepository logo após a criação. */
    lateinit var syncManager: BackendSyncManager
    lateinit var gateway: LocalDataGateway

    private val _isAuthenticated = MutableStateFlow(false)
    val isAuthenticated: StateFlow<Boolean> = _isAuthenticated.asStateFlow()

    private val _currentUserId = MutableStateFlow<String?>(null)
    val currentUserId: StateFlow<String?> = _currentUserId.asStateFlow()

    private val _currentUserEmail = MutableStateFlow<String?>(null)
    val currentUserEmail: StateFlow<String?> = _currentUserEmail.asStateFlow()

    private val _currentUserName = MutableStateFlow<String?>(null)
    val currentUserName: StateFlow<String?> = _currentUserName.asStateFlow()

    private val _currentAuthProvider = MutableStateFlow<String?>(null)
    val currentAuthProvider: StateFlow<String?> = _currentAuthProvider.asStateFlow()

    private val _jwtToken = MutableStateFlow<String?>(null)
    val jwtTokenFlow: StateFlow<String?> = _jwtToken.asStateFlow()

    private val _isAuthenticating = MutableStateFlow(false)
    val isAuthenticating: StateFlow<Boolean> = _isAuthenticating.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    private val _serverBaseUrl = MutableStateFlow(api.baseUrl)
    val serverBaseUrl: StateFlow<String> = _serverBaseUrl.asStateFlow()

    private val _authUser = MutableStateFlow<AuthUser?>(null)
    val authUser: StateFlow<AuthUser?> = _authUser.asStateFlow()

    val jwtToken: String? get() = _jwtToken.value
    val isLoggedIn: Boolean get() = _isAuthenticated.value

    init {
        // Interceptação 401 accountDeleted -> logout (FormaAPIClient.mapHttpError)
        api.onAccountDeleted = { logout() }
    }

    fun clearError() {
        _errorMessage.value = null
    }

    fun setServerBaseUrl(url: String) {
        api.baseUrl = url
        _serverBaseUrl.value = api.baseUrl
    }

    private fun publishAuthUser() {
        val id = _currentUserId.value
        _authUser.value = if (_isAuthenticated.value && !id.isNullOrBlank()) {
            AuthUser(id = id, email = _currentUserEmail.value.orEmpty(), name = _currentUserName.value.orEmpty())
        } else null
    }

    // ------------------------------------------------------------------------------------------
    // Restauração de sessão segura
    // ------------------------------------------------------------------------------------------
    fun restoreSession() {
        val token = api.jwtToken
        val userId = api.userId
        if (!token.isNullOrEmpty() && !userId.isNullOrEmpty()) {
            _jwtToken.value = token
            _currentUserId.value = userId
            _currentUserEmail.value = api.userEmail
            _currentUserName.value = api.userName
            _currentAuthProvider.value = api.authProvider

            val activeLocalId = syncManager.activeLocalUserId()
            if (activeLocalId != userId) {
                syncManager.markRequiresInitialPull(true)
                syncManager.setHasCompletedInitialSessionPull(false)
            } else if (gateway.currentProfile().onboardingCompleted) {
                syncManager.setHasCompletedInitialSessionPull(true)
            }
            _isAuthenticated.value = true
        } else {
            _jwtToken.value = null
            _currentUserId.value = null
            _currentUserEmail.value = null
            _currentUserName.value = null
            _currentAuthProvider.value = null
            _isAuthenticated.value = false
        }
        publishAuthUser()
    }

    private fun validation(message: String): Nothing = throw FormaApiException.ServerError(400, message)

    // ------------------------------------------------------------------------------------------
    // Login / Cadastro
    // ------------------------------------------------------------------------------------------
    suspend fun login(email: String, password: String) {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val cleanPassword = password.trim()
        if (cleanEmail.isEmpty() || cleanPassword.isEmpty()) validation("Preencha e-mail e senha.")

        _isAuthenticating.value = true
        _errorMessage.value = null
        try {
            val data = api.requestData(
                path = "/api/auth/login", method = "POST",
                jsonBody = mapOf("email" to cleanEmail, "password" to cleanPassword),
                authenticated = false, timeoutSeconds = 12.0, interceptAccountStatus = false
            )
            handleAuthSuccess(data, defaultProvider = "EMAIL", isNewRegistration = false)
        } finally {
            _isAuthenticating.value = false
        }
    }

    suspend fun register(name: String, email: String, password: String) {
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val cleanPassword = password.trim()
        if (cleanName.isEmpty() || cleanEmail.isEmpty() || cleanPassword.isEmpty()) validation("Preencha todos os campos.")
        if (cleanPassword.length < 6) validation("A senha deve ter pelo menos 6 caracteres.")

        _isAuthenticating.value = true
        _errorMessage.value = null
        try {
            val data = api.requestData(
                path = "/api/auth/register", method = "POST",
                jsonBody = mapOf("name" to cleanName, "email" to cleanEmail, "password" to cleanPassword),
                authenticated = false, timeoutSeconds = 12.0, interceptAccountStatus = false
            )
            handleAuthSuccess(data, defaultProvider = "EMAIL", isNewRegistration = true)
        } finally {
            _isAuthenticating.value = false
        }
    }

    /**
     * Login social (Google; Apple não existe no Android). HTTP 409 accountExists lança
     * [FormaApiException.AccountAlreadyExists] (com e-mail/nome da conta existente) para a UI pedir confirmação
     * e repetir a chamada com `confirmExisting = true`.
     */
    suspend fun loginWithSocial(
        provider: String,
        providerId: String,
        email: String?,
        name: String?,
        idToken: String?,
        nonce: String? = null,
        isRegisterIntent: Boolean = false,
        confirmExisting: Boolean = true,
        authorizationCode: String? = null,
        oauthRefreshToken: String? = null
    ) {
        _isAuthenticating.value = true
        _errorMessage.value = null
        try {
            val body = linkedMapOf<String, Any>(
                "provider" to provider.uppercase(Locale.ROOT),
                "providerId" to providerId,
                "isRegisterIntent" to isRegisterIntent,
                "confirmExisting" to confirmExisting
            )
            if (!email.isNullOrEmpty()) body["email"] = email
            if (!name.isNullOrEmpty()) body["name"] = name
            if (!idToken.isNullOrEmpty()) body["idToken"] = idToken
            if (!nonce.isNullOrEmpty()) body["nonce"] = nonce
            if (!authorizationCode.isNullOrEmpty()) body["authorizationCode"] = authorizationCode
            if (!oauthRefreshToken.isNullOrEmpty()) body["oauthRefreshToken"] = oauthRefreshToken

            val data = api.requestData(
                path = "/api/auth/social", method = "POST", jsonBody = body,
                authenticated = false, timeoutSeconds = 14.0, interceptAccountStatus = false
            )
            api.socialProviderId = providerId
            handleAuthSuccess(data, defaultProvider = provider.uppercase(Locale.ROOT), isNewRegistration = false)
        } finally {
            _isAuthenticating.value = false
        }
    }

    // ------------------------------------------------------------------------------------------
    // Recuperação, alteração de senha e exclusão de conta
    // ------------------------------------------------------------------------------------------
    suspend fun requestPasswordReset(email: String): String {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        if (cleanEmail.isEmpty()) validation("Informe o e-mail da sua conta.")

        _isAuthenticating.value = true
        _errorMessage.value = null
        try {
            val data = api.requestData(
                path = "/api/auth/forgot-password", method = "POST",
                jsonBody = mapOf("email" to cleanEmail),
                authenticated = false, timeoutSeconds = 10.0, interceptAccountStatus = false
            )
            val msg = runCatching { JsonParser.parseString(data).asJsonObject.get("message")?.asString }.getOrNull()
            return if (!msg.isNullOrEmpty()) msg
            else "Se o e-mail estiver cadastrado, enviamos o código de 6 dígitos para redefinir sua senha."
        } finally {
            _isAuthenticating.value = false
        }
    }

    suspend fun resetPassword(email: String, code: String, newPassword: String) {
        val cleanEmail = email.trim().lowercase(Locale.ROOT)
        val cleanCode = code.trim()
        val cleanPassword = newPassword.trim()
        if (cleanEmail.isEmpty() || cleanCode.length != 6) validation("Informe o e-mail e o código de 6 dígitos.")
        if (cleanPassword.length < 6) validation("A nova senha deve ter pelo menos 6 caracteres.")

        _isAuthenticating.value = true
        _errorMessage.value = null
        try {
            val data = api.requestData(
                path = "/api/auth/reset-password", method = "POST",
                jsonBody = mapOf("email" to cleanEmail, "code" to cleanCode, "newPassword" to cleanPassword),
                authenticated = false, timeoutSeconds = 12.0, interceptAccountStatus = false
            )
            handleAuthSuccess(data, defaultProvider = "EMAIL", isNewRegistration = false)
        } finally {
            _isAuthenticating.value = false
        }
    }

    suspend fun changePassword(currentPassword: String, newPassword: String) {
        val cleanCurrent = currentPassword.trim()
        val cleanNew = newPassword.trim()
        if (cleanNew.length < 6) validation("A nova senha deve ter pelo menos 6 caracteres.")
        api.requestData(
            path = "/api/auth/change-password", method = "POST",
            jsonBody = mapOf("currentPassword" to cleanCurrent, "newPassword" to cleanNew),
            authenticated = true, timeoutSeconds = 10.0
        )
    }

    /**
     * DELETE /api/auth/account com corpo opcional {authorizationCode?, googleToken?}. Após o sucesso limpa tokens,
     * token OAuth do Google, banco local e consentimento (logout()). [appleAuthorizationCode] não se aplica ao Android.
     */
    suspend fun deleteAccount(appleAuthorizationCode: String? = null) {
        val deleteBody = linkedMapOf<String, Any>()
        if (!appleAuthorizationCode.isNullOrEmpty()) deleteBody["authorizationCode"] = appleAuthorizationCode
        googleTokenStore.storedOAuthToken?.takeIf { it.isNotEmpty() }?.let { deleteBody["googleToken"] = it }

        syncManager.cancelPendingSyncs()
        api.requestData(
            path = "/api/auth/account", method = "DELETE",
            jsonBody = if (deleteBody.isEmpty()) null else deleteBody,
            authenticated = true, timeoutSeconds = 20.0, interceptAccountStatus = false
        )

        api.socialProviderId?.takeIf { it.isNotEmpty() }?.let {
            secure.remove("apple_name_$it")
            secure.remove("apple_email_$it")
        }
        api.socialProviderId = null
        googleTokenStore.clear()
        AiConsentStore.clearConsentForCurrentUser(context)
        logout()
    }

    // ------------------------------------------------------------------------------------------
    // Logout seguro
    // ------------------------------------------------------------------------------------------
    fun logout() {
        _isAuthenticated.value = false
        _jwtToken.value = null
        _currentUserId.value = null
        _currentUserEmail.value = null
        _currentUserName.value = null
        _currentAuthProvider.value = null
        publishAuthUser()

        api.clearSession()
        googleTokenStore.clear()

        syncManager.clearLocalSessionState()
        syncManager.cancelPendingSyncs()
        syncManager.clearAllPendingDeletions()
        syncManager.markRequiresInitialPull(false)
        syncManager.setHasCompletedInitialSessionPull(false)
        gateway.resetLocalDataForUserSwitch(null, null)
    }

    fun updateLocalUserName(newName: String) {
        val trimmed = newName.trim()
        if (trimmed.isEmpty()) return
        _currentUserName.value = trimmed
        api.userName = trimmed
        publishAuthUser()
    }

    // ------------------------------------------------------------------------------------------
    // Sucesso de autenticação & isolamento total multi-usuário
    // ------------------------------------------------------------------------------------------
    private suspend fun handleAuthSuccess(data: String, defaultProvider: String, isNewRegistration: Boolean) {
        val root: JsonObject = JsonParser.parseString(data).asJsonObject
        fun str(key: String): String? = root.get(key)?.takeIf { it.isJsonPrimitive }?.asString
        val token = str("token") ?: throw FormaApiException.ServerError(500, BackendApiClient.defaultMessage(500))
        val userId = str("userId") ?: throw FormaApiException.ServerError(500, BackendApiClient.defaultMessage(500))
        val email = str("email")
        val name = str("name")
        val provider = str("authProvider") ?: defaultProvider
        val profileJson = root.get("profile")?.takeIf { it.isJsonObject }?.asJsonObject

        syncManager.cancelPendingSyncs()
        syncManager.clearAllPendingDeletions()
        syncManager.setHasCompletedInitialSessionPull(false)

        // 1. Sempre limpa 100% do cache local antes de iniciar a sessão da conta autenticada
        gateway.resetLocalDataForUserSwitch(name, email)
        syncManager.setActiveLocalUserId(userId)

        // 2. Grava credenciais em memória e no armazenamento seguro antes do pull inicial
        _jwtToken.value = token
        _currentUserId.value = userId
        _currentUserEmail.value = email
        _currentUserName.value = name
        _currentAuthProvider.value = provider

        api.jwtToken = token
        api.userId = userId
        if (!email.isNullOrEmpty()) api.userEmail = email
        if (!name.isNullOrEmpty()) api.userName = name
        if (provider.isNotEmpty()) api.authProvider = provider

        // 3. Aplica dados imediatos do perfil retornados pelo servidor
        val localProfile = gateway.applyAuthProfile(name, email, profileJson, isNewRegistration)

        // 4. Contas novas (ou sem onboarding concluído) bloqueiam importação retroativa de treinos do Health Connect
        if (isNewRegistration || !localProfile.onboardingCompleted) {
            gateway.setHealthImportCutoff(userId, System.currentTimeMillis())
        }

        // 5. Em login de conta existente, baixa os dados uma única vez antes de abrir a tela
        if (isNewRegistration) {
            syncManager.markRequiresInitialPull(false)
            syncManager.setHasCompletedInitialSessionPull(true)
        } else {
            runCatching { syncManager.pullInitialStateForAuthenticatedUser() }
        }

        _isAuthenticated.value = true
        publishAuthUser()
    }
}

package com.forma.app.data.auth

import android.content.Context

/**
 * Guarda o refresh/access token OAuth do Google usado para revogação na exclusão de conta
 * (equivalente a GoogleAuthService.storedOAuthToken / clearStoredOAuthToken do iOS).
 * Chave: `forma_google_oauth_token`.
 */
class GoogleOAuthTokenStore(context: Context) {

    private val store = SecureStore.get(context)

    /** Token atualmente guardado (refresh token preferencialmente, ou access token). */
    val storedOAuthToken: String?
        get() = load()

    fun save(token: String?) {
        val clean = token?.trim()
        if (clean.isNullOrEmpty()) return
        store.putString(KEY, clean)
    }

    fun load(): String? = store.getString(KEY)?.takeIf { it.isNotBlank() }

    fun clear() {
        store.remove(KEY)
    }

    fun clearStoredOAuthToken() = clear()

    companion object {
        const val KEY = "forma_google_oauth_token"

        fun from(context: Context) = GoogleOAuthTokenStore(context.applicationContext)
    }
}

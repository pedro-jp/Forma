package com.forma.app.data.auth

import android.content.Context

/**
 * Consentimento de uso da IA externa, isolado por usuário (AIDataConsentManager.swift).
 * Chave: `forma_ai_third_party_consent_v1_<userId em minúsculas | guest>` em SharedPreferences "forma_prefs".
 */
object AiConsentStore {
    private const val KEY_PREFIX = "forma_ai_third_party_consent_v1_"
    private const val PREFS = "forma_prefs"

    private fun resolvedKey(context: Context): String {
        val uid = SecureStore.get(context).getString(AuthManager.KEY_USER_ID)?.trim().orEmpty()
        val scope = if (uid.isEmpty()) "guest" else uid.lowercase()
        return "$KEY_PREFIX$scope"
    }

    fun isConsentGranted(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(resolvedKey(context), false)

    fun setConsent(context: Context, granted: Boolean) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(resolvedKey(context), granted).apply()
    }

    fun clearConsentForCurrentUser(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(resolvedKey(context)).apply()
    }
}

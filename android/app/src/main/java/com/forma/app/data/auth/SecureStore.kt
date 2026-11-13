package com.forma.app.data.auth

import android.content.Context
import android.content.SharedPreferences
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Armazenamento seguro de chave/valor (equivalente ao KeychainHelper do iOS).
 * Usa EncryptedSharedPreferences; se o Keystore do aparelho falhar, recua para SharedPreferences privado.
 */
class SecureStore private constructor(context: Context) {

    private val prefs: SharedPreferences = createPrefs(context.applicationContext)

    fun getString(key: String): String? = prefs.getString(key, null)

    fun putString(key: String, value: String?) {
        if (value == null) remove(key) else prefs.edit().putString(key, value).apply()
    }

    fun remove(key: String) {
        prefs.edit().remove(key).apply()
    }

    companion object {
        private const val FILE_NAME = "forma_secure_prefs"
        private const val FALLBACK_FILE_NAME = "forma_secure_prefs_fallback"

        @Volatile
        private var instance: SecureStore? = null

        fun get(context: Context): SecureStore =
            instance ?: synchronized(this) {
                instance ?: SecureStore(context.applicationContext).also { instance = it }
            }

        private fun createPrefs(context: Context): SharedPreferences {
            return try {
                val masterKey = MasterKey.Builder(context)
                    .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                    .build()
                EncryptedSharedPreferences.create(
                    context,
                    FILE_NAME,
                    masterKey,
                    EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                    EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
                )
            } catch (_: Throwable) {
                context.getSharedPreferences(FALLBACK_FILE_NAME, Context.MODE_PRIVATE)
            }
        }
    }
}

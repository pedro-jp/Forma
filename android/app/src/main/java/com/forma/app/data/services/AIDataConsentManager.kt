package com.forma.app.data.services

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Textos do modal "Privacidade da IA" (idênticos a `AIDataConsentSheet` do iOS) para a UI reutilizar.
 *
 * Observação: [GEMINI_BODY] contém `**Google Gemini (fornecida pela Google LLC)**` em Markdown (negrito),
 * como no iOS; a UI deve renderizar o trecho entre `**` em negrito.
 */
object AIConsentTexts {
    const val NAV_TITLE = "Privacidade da IA"
    const val CLOSE_BUTTON = "Fechar"

    const val HEADER_TITLE = "IA do Google Gemini"
    const val HEADER_SUBTITLE = "Autorização para uso da IA externa da Google LLC"

    const val GEMINI_CARD_TITLE = "Utilizamos a IA do Google Gemini"
    const val GEMINI_BODY =
        "Para analisar fotos de pratos, alimentos fora da base offline, fichas personalizadas e análises de evolução, " +
            "o Forma utiliza a inteligência artificial **Google Gemini (fornecida pela Google LLC)**, enviando os dados " +
            "da solicitação sob demanda por conexão criptografada (HTTPS/TLS)."

    const val DATA_CARD_TITLE = "Quais dados são compartilhados?"
    val DATA_BULLETS: List<String> = listOf(
        "Fotos ou textos de refeições enviados por você para estimativa de calorias e macronutrientes.",
        "Descrições de exercícios/treinos e seu peso corporal para cálculo de gasto energético.",
        "Nome de exibição, medidas corporais (peso, altura e objetivo) e histórico recente de refeições, água, treinos e pesagens ao gerar metas ou análises de progresso."
    )
    const val NEVER_SHARED_NOTE =
        "Seu e-mail, senha e credenciais Apple/Google nunca são enviados para a IA do Google Gemini."

    const val PURPOSE_CARD_TITLE = "Finalidade, LGPD e Controle"
    const val PURPOSE_BODY =
        "Os dados são processados pelo Google Gemini exclusivamente para retornar suas estimativas nutricionais e de treino no Forma, " +
            "sem venda a terceiros e sem uso publicitário. Você pode revogar esta permissão a qualquer momento em " +
            "Perfil > Privacidade, Saúde & Termos de Uso."
    const val PRIVACY_POLICY_URL = "https://forma-94vp.onrender.com/privacy.html"
    const val PRIVACY_POLICY_LINK_LABEL = "Ler Política de Privacidade Completa (LGPD)"

    const val AUTHORIZE_BUTTON = "Autorizar e Continuar"
    const val DECLINE_BUTTON = "Agora Não (usar apenas base local)"

    /** Mensagem exibida quando uma chamada de IA em nuvem é tentada sem consentimento. */
    const val CONSENT_REQUIRED_MESSAGE =
        "Autorize o uso da IA externa nas configurações de Privacidade & Saúde para continuar."
}

/**
 * Consentimento explícito e revogável para envio de dados a provedor externo de IA
 * (Google Gemini API - App Store Review Guideline 5.1.2(i)). Porta de `AIDataConsentManager` (iOS).
 *
 * O consentimento é salvo por usuário (`forma_ai_third_party_consent_v1_<userId>`; `guest` quando não há conta)
 * em um arquivo de preferências próprio ("forma_ai_consent"), de modo que sobrevive ao logout, como no iOS
 * (UserDefaults por usuário). O userId ativo é lido de `forma_prefs` -> `user_id` (mesma chave do BackendApiClient).
 *
 * Uso:
 * ```
 * val consent = AIDataConsentManager.get(context)
 * val granted by consent.hasGrantedConsent.collectAsState()
 * consent.setConsent(true)           // "Autorizar e Continuar"
 * consent.clearConsentForCurrentUser() // revogar em Perfil > Privacidade
 * consent.refreshForCurrentUser()    // chamar após login/logout/troca de conta
 * ```
 * Leitura síncrona para serviços: [AIDataConsentManager.isConsentGranted].
 */
class AIDataConsentManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val consentPrefs: SharedPreferences =
        appContext.getSharedPreferences(CONSENT_PREFS, Context.MODE_PRIVATE)

    private val _hasGrantedConsent = MutableStateFlow(isConsentGranted())

    /** Estado observável do consentimento do usuário atual. */
    val hasGrantedConsent: StateFlow<Boolean> = _hasGrantedConsent.asStateFlow()

    /** Chave isolada por usuário autenticado (ou escopo `guest` quando sem conta ativa). */
    val resolvedStorageKey: String
        get() = storageKeyFor(appContext)

    /** Leitura síncrona e thread-safe (equivalente a `AIDataConsentManager.isConsentGranted`). */
    fun isConsentGranted(): Boolean = consentPrefs.getBoolean(resolvedStorageKey, false)

    fun setConsent(granted: Boolean) {
        consentPrefs.edit().putBoolean(resolvedStorageKey, granted).apply()
        _hasGrantedConsent.value = granted
    }

    fun refreshForCurrentUser() {
        _hasGrantedConsent.value = isConsentGranted()
    }

    fun clearConsentForCurrentUser() {
        consentPrefs.edit().remove(resolvedStorageKey).apply()
        _hasGrantedConsent.value = false
    }

    companion object {
        private const val CONSENT_PREFS = "forma_ai_consent"
        private const val KEY_PREFIX = "forma_ai_third_party_consent_v1_"

        @Volatile
        private var instance: AIDataConsentManager? = null

        fun get(context: Context): AIDataConsentManager =
            instance ?: synchronized(this) {
                instance ?: AIDataConsentManager(context).also { instance = it }
            }

        internal fun storageKeyFor(context: Context): String {
            val rawUid = context.applicationContext
                .getSharedPreferences("forma_prefs", Context.MODE_PRIVATE)
                .getString("user_id", null)
                ?.trim()
                .orEmpty()
            val scope = if (rawUid.isEmpty()) "guest" else rawUid.lowercase()
            return "$KEY_PREFIX$scope"
        }

        /** Leitura síncrona estática (equivalente ao `nonisolated static var isConsentGranted` do iOS). */
        fun isConsentGranted(context: Context): Boolean =
            context.applicationContext
                .getSharedPreferences(CONSENT_PREFS, Context.MODE_PRIVATE)
                .getBoolean(storageKeyFor(context), false)
    }
}

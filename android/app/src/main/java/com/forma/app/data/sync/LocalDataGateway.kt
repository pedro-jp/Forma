package com.forma.app.data.sync

import com.forma.app.data.model.RemoteProfilePayload
import com.forma.app.data.model.UserProfile
import com.google.gson.JsonObject

/**
 * Ponte entre os gerenciadores (Auth/Sync) e o armazenamento local (o "FormaDataStore" do iOS, implementado
 * por FormaRepository). Evita dependência circular entre as classes.
 */
interface LocalDataGateway {
    /** Limpa 100% dos dados locais e cria um perfil novo (resetLocalDataForUserSwitch). */
    fun resetLocalDataForUserSwitch(userName: String?, userEmail: String?)

    /** Perfil local atual. */
    fun currentProfile(): UserProfile

    /**
     * Aplica ao perfil local os dados imediatos retornados pela autenticação (nome, e-mail e `profile` do servidor).
     * Retorna o perfil resultante.
     */
    fun applyAuthProfile(name: String?, email: String?, profileJson: JsonObject?, isNewRegistration: Boolean): UserProfile

    /** Notifica que os dados locais mudaram (recarrega StateFlows). */
    fun onLocalDataChanged()

    /** Equivalente a syncTodayData(skipCloudSyncTrigger:). */
    fun syncTodayData(skipCloudSyncTrigger: Boolean)

    /** Payload do perfil local para o auto-sync com debounce (null se onboarding incompleto). */
    fun profilePayloadForAutoSync(): RemoteProfilePayload?

    /** Barreira de corte para importação de treinos do Health Connect por conta. */
    fun setHealthImportCutoff(userId: String, millis: Long)
}

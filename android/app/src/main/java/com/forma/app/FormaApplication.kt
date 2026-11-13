package com.forma.app

import android.app.Application
import com.forma.app.data.AppNavigationRouter
import com.forma.app.data.auth.AuthManager
import com.forma.app.data.health.HealthGlue
import com.forma.app.data.local.DatabaseHelper
import com.forma.app.data.repository.FormaRepository
import com.forma.app.data.sync.BackendSyncManager
import com.forma.app.service.NotificationHelper

class FormaApplication : Application() {

    lateinit var database: DatabaseHelper
    lateinit var repository: FormaRepository

    /** Atalhos para os gerenciadores (equivalentes aos singletons do iOS). */
    val authManager: AuthManager get() = repository.auth
    val syncManager: BackendSyncManager get() = repository.sync
    val router: AppNavigationRouter get() = AppNavigationRouter.shared

    companion object {
        lateinit var instance: FormaApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        database = DatabaseHelper(this)
        repository = FormaRepository(this, database)
        NotificationHelper.createNotificationChannels(this)
        // Health Connect + água pendente + Ilha/lembretes (ganchos do repositório) antes do primeiro sync do dia
        HealthGlue.install(this, repository)
        // Restaura sessão/sincroniza dados do dia e dispara o pull inicial quando necessário (FormaApp.task do iOS)
        repository.onAppStart()
    }
}

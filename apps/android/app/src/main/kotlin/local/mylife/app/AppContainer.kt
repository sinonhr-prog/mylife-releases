package local.mylife.app

import android.content.Context
import local.mylife.app.feature.settings.updater.DefaultUpdateRepository
import local.mylife.app.feature.settings.updater.UpdateRepository
import local.mylife.core.model.DashboardRepository
import local.mylife.core.model.TaskRepository
import local.mylife.core.security.DefaultSessionManager
import local.mylife.core.security.SessionManager
import local.mylife.core.storage.InMemoryTaskRepository

import java.io.File
import local.mylife.core.security.crypto.AndroidDeviceKeyProvider
import local.mylife.core.security.crypto.KeyEnvelopeManager

interface AppContainer {
    val taskRepository: TaskRepository
    val dashboardRepository: DashboardRepository
    val sessionManager: SessionManager
    val updateRepository: UpdateRepository
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    private val inMemoryRepo = InMemoryTaskRepository()

    override val taskRepository: TaskRepository = inMemoryRepo
    override val dashboardRepository: DashboardRepository = inMemoryRepo
    override val sessionManager: SessionManager by lazy {
        val securityDir = File(context.filesDir, "security")
        val envelopeManager = KeyEnvelopeManager(securityDir, AndroidDeviceKeyProvider())
        DefaultSessionManager(envelopeManager)
    }
    override val updateRepository: UpdateRepository by lazy {
        DefaultUpdateRepository(context)
    }
}


package local.mylife.app

import android.content.Context
import local.mylife.app.feature.settings.updater.DefaultUpdateRepository
import local.mylife.app.feature.settings.updater.UpdateRepository
import local.mylife.core.security.DefaultSessionManager
import local.mylife.core.security.SessionManager
import local.mylife.core.security.crypto.AndroidDeviceKeyProvider
import local.mylife.core.security.crypto.KeyEnvelopeManager
import local.mylife.core.storage.StorageSession
import java.io.File
import java.util.Arrays

interface AppContainer {
    val sessionManager: SessionManager
    val updateRepository: UpdateRepository
    fun getStorageSession(mainDbKey: ByteArray): StorageSession
    fun closeStorageSession()
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val sessionManager: SessionManager by lazy {
        val securityDir = File(context.filesDir, "security")
        val envelopeManager = KeyEnvelopeManager(securityDir, AndroidDeviceKeyProvider())
        DefaultSessionManager(envelopeManager)
    }

    override val updateRepository: UpdateRepository by lazy {
        DefaultUpdateRepository(context)
    }

    private var activeSession: StorageSession? = null
    private var activeKey: ByteArray? = null

    @Synchronized
    override fun getStorageSession(mainDbKey: ByteArray): StorageSession {
        val current = activeSession
        if (current != null && activeKey != null && activeKey!!.contentEquals(mainDbKey)) {
            return current
        }
        closeStorageSession()
        val session = StorageSession(context, mainDbKey)
        activeSession = session
        activeKey = mainDbKey.clone()
        return session
    }

    @Synchronized
    override fun closeStorageSession() {
        activeSession?.close()
        activeSession = null
        activeKey?.let { Arrays.fill(it, 0.toByte()) }
        activeKey = null
    }
}

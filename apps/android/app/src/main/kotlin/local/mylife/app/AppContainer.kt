package local.mylife.app

import android.content.Context
import kotlinx.coroutines.launch
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

    fun peekSessionManager(): SessionManager? = null

    val keyEnvelopeManager: KeyEnvelopeManager
    val updateRepository: UpdateRepository

    fun getStorageSession(mainDbKey: ByteArray): StorageSession

    fun closeStorageSession()

    fun getVaultStorageSession(vaultDbKey: ByteArray): local.mylife.core.storage.VaultStorageSession

    fun closeVaultStorageSession()
}

class DefaultAppContainer(private val context: Context) : AppContainer {
    override val keyEnvelopeManager: KeyEnvelopeManager by lazy {
        val securityDir = File(context.filesDir, "security")
        KeyEnvelopeManager(securityDir, AndroidDeviceKeyProvider(), transactionRoot = context.dataDir)
    }

    private val sessionManagerDelegate =
        lazy {
            DefaultSessionManager(keyEnvelopeManager) { all ->
                closeVaultStorageSession()
                if (all) closeStorageSession()
            }
        }

    override val sessionManager: SessionManager get() = sessionManagerDelegate.value

    override fun peekSessionManager(): SessionManager? = if (sessionManagerDelegate.isInitialized()) sessionManagerDelegate.value else null

    override val updateRepository: UpdateRepository by lazy {
        DefaultUpdateRepository(context)
    }

    private val storageCloseScope =
        kotlinx.coroutines.CoroutineScope(
            kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO,
        )

    private fun closeOffMain(close: () -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            storageCloseScope.launch { close() }
        } else {
            close()
        }
    }

    private var activeSession: StorageSession? = null
    private var activeKey: ByteArray? = null

    private var activeVaultSession: local.mylife.core.storage.VaultStorageSession? = null
    private var activeVaultKey: ByteArray? = null

    @Synchronized
    override fun getStorageSession(mainDbKey: ByteArray): StorageSession {
        val current = activeSession
        if (current != null && activeKey != null && activeKey!!.contentEquals(mainDbKey)) {
            return current
        }
        closeStorageSession()
        val schedulerPort = local.mylife.app.feature.reminders.AndroidAlarmScheduler(context)
        val session = StorageSession(context, mainDbKey, schedulerPort)
        activeSession = session
        activeKey = mainDbKey.clone()
        return session
    }

    @Synchronized
    override fun closeStorageSession() {
        val closing = activeSession
        activeSession = null
        if (closing != null) closeOffMain { closing.close() }
        activeKey?.let { Arrays.fill(it, 0.toByte()) }
        activeKey = null
    }

    @Synchronized
    override fun getVaultStorageSession(vaultDbKey: ByteArray): local.mylife.core.storage.VaultStorageSession {
        val current = activeVaultSession
        if (current != null && activeVaultKey != null && activeVaultKey!!.contentEquals(vaultDbKey)) {
            return current
        }
        closeVaultStorageSession()
        val session = local.mylife.core.storage.VaultStorageSession(context, vaultDbKey)
        activeVaultSession = session
        activeVaultKey = vaultDbKey.clone()
        return session
    }

    @Synchronized
    override fun closeVaultStorageSession() {
        val closing = activeVaultSession
        activeVaultSession = null
        if (closing != null) closeOffMain { closing.close() }
        activeVaultKey?.let { Arrays.fill(it, 0.toByte()) }
        activeVaultKey = null
    }
}

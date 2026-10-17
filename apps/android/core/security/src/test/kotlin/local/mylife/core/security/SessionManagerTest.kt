package local.mylife.core.security

import local.mylife.core.security.crypto.InMemoryDeviceKeyProvider
import local.mylife.core.security.crypto.KeyEnvelopeManager
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class SessionManagerTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var manager: DefaultSessionManager

    @Before
    fun setUp() {
        val storageDir = tempFolder.newFolder("session_test")
        val envelopeManager = KeyEnvelopeManager(storageDir, InMemoryDeviceKeyProvider())
        manager = DefaultSessionManager(envelopeManager)
    }

    @Test
    fun testDefaultStateIsLocked() {
        assertEquals(SessionState.Locked, manager.getSessionState())
    }

    @Test
    fun testLockAllSetsLocked() {
        manager.lockAll()
        assertEquals(SessionState.Locked, manager.getSessionState())
    }
}

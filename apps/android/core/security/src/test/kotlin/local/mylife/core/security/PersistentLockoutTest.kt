package local.mylife.core.security

import local.mylife.core.security.crypto.InMemoryDeviceKeyProvider
import local.mylife.core.security.crypto.KeyEnvelopeManager
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class PersistentLockoutTest {
    @get:Rule val folder = TemporaryFolder()

    @Test fun weakNewPinRejectedWithoutChangingExistingKeys() {
        val manager = KeyEnvelopeManager(File(folder.root, "keys"), InMemoryDeviceKeyProvider())
        val original = manager.setupNewPin("583926").first
        try {
            manager.changePin("583926", "123456")
            org.junit.Assert.fail()
        } catch (_: IllegalArgumentException) {
        }
        assertArrayEquals(original, manager.unlockScope(local.mylife.core.security.crypto.KeyScope.MAIN, "583926"))
        assertTrue(local.mylife.core.security.crypto.PinPolicy.isValidPin("123456"))
        assertFalse(local.mylife.core.security.crypto.PinPolicy.isValidNewPin("123456"))
    }

    @Test fun interruptedPinChangeRestoresBothOldEnvelopesOnRestart() {
        val path = File(folder.root, "keys")
        val provider = InMemoryDeviceKeyProvider()
        var interrupt = false
        val manager =
            KeyEnvelopeManager(path, provider, transactionObserver = {
                if (interrupt && it == 0) throw AssertionError("synthetic process kill")
            })
        val original = manager.setupNewPin("583926")
        interrupt = true
        try {
            manager.changePin("583926", "729385")
            org.junit.Assert.fail()
        } catch (_: AssertionError) {
        }
        val recovered = KeyEnvelopeManager(path, provider)
        assertArrayEquals(original.first, recovered.unlockScope(local.mylife.core.security.crypto.KeyScope.MAIN, "583926"))
        assertArrayEquals(original.second, recovered.unlockScope(local.mylife.core.security.crypto.KeyScope.VAULT, "583926"))
    }

    @Test fun lockoutSurvivesManagerRecreationAndPublishesLock() {
        val path = File(folder.root, "keys")
        val provider = InMemoryDeviceKeyProvider()
        val first = DefaultSessionManager(KeyEnvelopeManager(path, provider))
        assertTrue(first.setupPin("583926"))
        first.lockAll()
        repeat(3) { assertFalse(first.unlockMain("111222")) }
        val second = DefaultSessionManager(KeyEnvelopeManager(path, provider))
        assertEquals(3, second.getFailedAttempts())
        assertTrue(second.getLockoutDelayRemainingMs() > 0)
        assertEquals(SessionState.Locked, second.sessionState.value)
    }

    @Test fun vaultLockAndFullLockNotifyStorageOwner() {
        val events = mutableListOf<Boolean>()
        val session = DefaultSessionManager(KeyEnvelopeManager(File(folder.root, "keys"), InMemoryDeviceKeyProvider())) { events.add(it) }
        session.setupPin("583926")
        session.unlockVault("583926")
        session.lockVault()
        assertEquals(listOf(false), events)
        assertTrue(session.sessionState.value is SessionState.MainUnlocked)
        session.lockAll()
        assertEquals(listOf(false, true), events)
        assertEquals(SessionState.Locked, session.sessionState.value)
    }
}

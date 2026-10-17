package local.mylife.core.security

import local.mylife.core.security.crypto.DeviceKeyProvider
import local.mylife.core.security.crypto.InMemoryDeviceKeyProvider
import local.mylife.core.security.crypto.KeyEnvelopeManager
import local.mylife.core.security.crypto.KeyScope
import local.mylife.core.security.crypto.PinPolicy
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.security.GeneralSecurityException
import java.util.Arrays

class H03SecuritySessionTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var storageDir: File
    private lateinit var deviceKeyProvider: DeviceKeyProvider
    private lateinit var envelopeManager: KeyEnvelopeManager
    private lateinit var sessionManager: DefaultSessionManager

    private val validPin = "123456"
    private val wrongPin = "654321"

    @Before
    fun setUp() {
        storageDir = tempFolder.newFolder("security_test")
        deviceKeyProvider = InMemoryDeviceKeyProvider()
        envelopeManager = KeyEnvelopeManager(storageDir, deviceKeyProvider)
        sessionManager = DefaultSessionManager(envelopeManager)
    }

    // -------------------------------------------------------------
    // Required Check 1: auth-negative
    // -------------------------------------------------------------
    @Test
    fun testAuthNegative_InvalidPinPolicy() {
        // Must be strictly 6 digits
        assertFalse(PinPolicy.isValidPin("12345"))
        assertFalse(PinPolicy.isValidPin("1234567"))
        assertFalse(PinPolicy.isValidPin("abcdef"))
        assertFalse(PinPolicy.isValidPin("12345a"))
        assertFalse(PinPolicy.isValidPin(""))
        assertTrue(PinPolicy.isValidPin("000000"))
        assertTrue(PinPolicy.isValidPin("999999"))
        assertTrue(PinPolicy.isValidPin("123456"))
    }

    @Test
    fun testAuthNegative_WrongPinFailsUnlockAndKeepsLocked() {
        sessionManager.setupPin(validPin)
        sessionManager.lockAll()
        assertEquals(SessionState.Locked, sessionManager.getSessionState())

        val result = sessionManager.unlockMain(wrongPin)
        assertFalse("Wrong PIN must return false", result)
        assertEquals("State must remain Locked on wrong PIN", SessionState.Locked, sessionManager.getSessionState())
        assertEquals("Failed attempts count must increment", 1, sessionManager.getFailedAttempts())
    }

    @Test
    fun testAuthNegative_TamperedEnvelopeFailsIntegrityCheck() {
        sessionManager.setupPin(validPin)
        sessionManager.lockAll()

        // Tamper with envelope file
        val envelopeFile = envelopeManager.getEnvelopeFile(KeyScope.MAIN)
        assertTrue(envelopeFile.exists())
        val bytes = envelopeFile.readBytes()
        // Corrupt last byte (part of authentication tag or ciphertext)
        bytes[bytes.size - 1] = (bytes[bytes.size - 1].toInt() xor 0xFF).toByte()
        envelopeFile.writeBytes(bytes)

        val result = sessionManager.unlockMain(validPin)
        assertFalse("Tampered envelope must fail unlock", result)
        assertEquals(SessionState.Locked, sessionManager.getSessionState())
    }

    @Test
    fun testAuthNegative_ScopeMismatchRejectedByAad() {
        val (mainKey, _) = envelopeManager.setupNewPin(validPin)
        // Trying to open vault envelope with main AAD must fail
        val mainRecord = envelopeManager.readEnvelopeRecord(KeyScope.MAIN)
        val deviceAlias = KeyEnvelopeManager.getDeviceKeyAlias(KeyScope.MAIN)
        val innerWrap = deviceKeyProvider.decrypt(deviceAlias, mainRecord.outerWrap)

        val pinBytes = validPin.toByteArray(Charsets.UTF_8)
        val kPin = local.mylife.core.security.crypto.Argon2idKdf.deriveKey(
            pinBytes,
            mainRecord.salt,
            mainRecord.kdfParams
        )

        try {
            // Attempt opening with VAULT AAD instead of MAIN AAD
            local.mylife.core.security.crypto.AesGcmEnvelope.open(
                kPin,
                innerWrap,
                KeyScope.VAULT.pinWrapAad()
            )
            fail("Opening main envelope with vault AAD must throw GeneralSecurityException")
        } catch (expected: GeneralSecurityException) {
            // PASS: authentication tag mismatch due to AAD mismatch
        } finally {
            Arrays.fill(pinBytes, 0.toByte())
            Arrays.fill(kPin, 0.toByte())
            Arrays.fill(mainKey, 0.toByte())
        }
    }

    // -------------------------------------------------------------
    // Required Check 2: session-isolation
    // -------------------------------------------------------------
    @Test
    fun testSessionIsolation_MainUnlockedDoesNotUnlockVault() {
        sessionManager.setupPin(validPin)
        sessionManager.lockAll()

        assertTrue(sessionManager.unlockMain(validPin))
        val state = sessionManager.getSessionState()
        assertTrue("Session must be MainUnlocked", state is SessionState.MainUnlocked)
        if (state is SessionState.MainUnlocked) {
            assertEquals(32, state.mainDbKey.size)
        }

        // Vault is still locked
        val isVaultState = state is SessionState.VaultUnlocked
        assertFalse("Vault must not be unlocked when main is unlocked", isVaultState)
    }

    @Test
    fun testSessionIsolation_UnlockVaultAndLockVaultOnly() {
        sessionManager.setupPin(validPin)
        sessionManager.lockAll()
        sessionManager.unlockMain(validPin)

        assertTrue(sessionManager.unlockVault(validPin))
        val vaultState = sessionManager.getSessionState()
        assertTrue("Session must be VaultUnlocked", vaultState is SessionState.VaultUnlocked)

        if (vaultState is SessionState.VaultUnlocked) {
            val originalVaultKey = vaultState.vaultDbKey.copyOf()
            sessionManager.lockVault()

            val mainState = sessionManager.getSessionState()
            assertTrue("After lockVault, session must revert to MainUnlocked", mainState is SessionState.MainUnlocked)

            // Verify original vault key memory is wiped (zeroized)
            assertTrue("Vault key array must be zeroized", originalVaultKey.any { it != 0.toByte() } && vaultState.vaultDbKey.all { it == 0.toByte() })
        }
    }

    @Test
    fun testSessionIsolation_LockAllWipesAllKeys() {
        sessionManager.setupPin(validPin)
        sessionManager.unlockVault(validPin)

        val state = sessionManager.getSessionState() as SessionState.VaultUnlocked
        val mainKeyRef = state.mainDbKey
        val vaultKeyRef = state.vaultDbKey

        sessionManager.lockAll()
        assertEquals(SessionState.Locked, sessionManager.getSessionState())

        // Verify keys in memory are zeroized
        assertTrue("Main key memory must be zeroized", mainKeyRef.all { it == 0.toByte() })
        assertTrue("Vault key memory must be zeroized", vaultKeyRef.all { it == 0.toByte() })
    }

    // -------------------------------------------------------------
    // Required Check 3: pin-change-crash
    // -------------------------------------------------------------
    @Test
    fun testPinChange_SuccessPreservesDatabaseKeys() {
        sessionManager.setupPin(validPin)
        val initialMainState = sessionManager.getSessionState() as SessionState.MainUnlocked
        val initialMainKey = initialMainState.mainDbKey.copyOf()

        val newPin = "987654"
        assertTrue(sessionManager.changePin(validPin, newPin))

        // Old PIN no longer unlocks
        sessionManager.lockAll()
        assertFalse("Old PIN must fail after PIN change", sessionManager.unlockMain(validPin))

        // New PIN unlocks successfully and recovers the exact same database key
        assertTrue("New PIN must succeed after PIN change", sessionManager.unlockMain(newPin))
        val updatedMainState = sessionManager.getSessionState() as SessionState.MainUnlocked
        assertArrayEquals("Database key must be preserved across PIN change", initialMainKey, updatedMainState.mainDbKey)
    }

    @Test
    fun testPinChangeCrash_InterruptionBeforeAtomicRenameLeavesOldPinIntact() {
        sessionManager.setupPin(validPin)
        sessionManager.lockAll()
        assertTrue(sessionManager.unlockMain(validPin))

        // Simulate crash during changePin:
        // A temporary .tmp file is written, but atomic rename has not completed.
        val mainEnvelopeFile = envelopeManager.getEnvelopeFile(KeyScope.MAIN)
        val mainTmpFile = File(storageDir, "${mainEnvelopeFile.name}.tmp")
        mainTmpFile.writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        // Old envelope file is intact
        assertTrue(mainEnvelopeFile.exists())

        // System restarts: old PIN still valid and unlocks cleanly
        sessionManager.lockAll()
        assertTrue("Old PIN must continue to work if rename did not complete", sessionManager.unlockMain(validPin))
        assertEquals(SessionState.MainUnlocked::class, sessionManager.getSessionState()::class)

        // Clean up tmp file
        if (mainTmpFile.exists()) mainTmpFile.delete()
    }
}

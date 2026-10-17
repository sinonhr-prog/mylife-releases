package local.mylife.core.storage.backup

import local.mylife.core.security.crypto.DeviceKeyProvider
import local.mylife.core.security.crypto.InMemoryDeviceKeyProvider
import local.mylife.core.security.crypto.KeyEnvelopeManager
import local.mylife.core.security.crypto.KeyScope
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.security.GeneralSecurityException

class LifeVaultArchiveServiceTest {
    @Test
    fun testExportNeverCreatesPlaintextStagingWhileStreaming() {
        val (main, vault) = deviceAEnvelopeManager.setupNewPin(deviceAPin)
        File(deviceADatabasesDir, "main.db").writeText("SYNTHETIC_DATABASE")
        val output =
            object : ByteArrayOutputStream() {
                override fun write(
                    bytes: ByteArray,
                    offset: Int,
                    length: Int,
                ) {
                    assertTrue("No temporary archive or raw keys may reach disk", deviceACacheDir.walkTopDown().none { it.isFile })
                    super.write(bytes, offset, length)
                }
            }
        deviceAService.exportArchive(validPassphrase, output, main, vault)
        assertTrue(deviceAService.inspectArchive(validPassphrase, output.toByteArray().inputStream()).files.containsKey("keys/keys.bin"))
    }

    @Test
    fun testLateRestoreFailureRollsBackDatabasesAndBothKeyEnvelopes() {
        val (main, vault) = deviceAEnvelopeManager.setupNewPin(deviceAPin)
        File(deviceADatabasesDir, "main.db").writeText("NEW_MAIN")
        File(deviceADatabasesDir, "vault.db").writeText("NEW_VAULT")
        val output = ByteArrayOutputStream()
        deviceAService.exportArchive(validPassphrase, output, main, vault)
        val oldKeys = deviceBEnvelopeManager.setupNewPin(deviceBPin)
        File(deviceBDatabasesDir, "main.db").writeText("OLD_MAIN")
        File(deviceBDatabasesDir, "vault.db").writeText("OLD_VAULT")
        val failingManager =
            KeyEnvelopeManager(deviceBKeyDir, deviceBKeyProvider, transactionObserver = {
                if (it == 1) throw java.io.IOException("synthetic late write failure")
            })
        try {
            deviceBService.restoreArchive(validPassphrase, output.toByteArray().inputStream(), "729385", failingManager)
            fail("Late write must fail")
        } catch (_: java.io.IOException) {
        }
        assertEquals("OLD_MAIN", File(deviceBDatabasesDir, "main.db").readText())
        assertEquals("OLD_VAULT", File(deviceBDatabasesDir, "vault.db").readText())
        assertArrayEquals(oldKeys.first, deviceBEnvelopeManager.unlockScope(KeyScope.MAIN, deviceBPin))
        assertArrayEquals(oldKeys.second, deviceBEnvelopeManager.unlockScope(KeyScope.VAULT, deviceBPin))
    }

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var deviceAFilesDir: File
    private lateinit var deviceADatabasesDir: File
    private lateinit var deviceACacheDir: File
    private lateinit var deviceAKeyDir: File
    private lateinit var deviceAKeyProvider: DeviceKeyProvider
    private lateinit var deviceAEnvelopeManager: KeyEnvelopeManager
    private lateinit var deviceAService: LifeVaultArchiveService

    private lateinit var deviceBFilesDir: File
    private lateinit var deviceBDatabasesDir: File
    private lateinit var deviceBCacheDir: File
    private lateinit var deviceBKeyDir: File
    private lateinit var deviceBKeyProvider: DeviceKeyProvider
    private lateinit var deviceBEnvelopeManager: KeyEnvelopeManager
    private lateinit var deviceBService: LifeVaultArchiveService

    private val validPassphrase = "CorrectHorseBatteryStaple2026!"
    private val deviceAPin = "112233"
    private val deviceBPin = "445566"

    @Before
    fun setUp() {
        val rootA = tempFolder.newFolder("device_A")
        deviceAFilesDir = File(rootA, "files").apply { mkdirs() }
        deviceADatabasesDir = File(rootA, "databases").apply { mkdirs() }
        deviceACacheDir = File(rootA, "cache").apply { mkdirs() }
        deviceAKeyDir = File(rootA, "keys").apply { mkdirs() }
        deviceAKeyProvider = InMemoryDeviceKeyProvider()
        deviceAEnvelopeManager = KeyEnvelopeManager(deviceAKeyDir, deviceAKeyProvider)
        deviceAService =
            LifeVaultArchiveService(
                appFilesDir = deviceAFilesDir,
                appDatabasesDir = deviceADatabasesDir,
                cacheDir = deviceACacheDir,
            )

        val rootB = tempFolder.newFolder("device_B")
        deviceBFilesDir = File(rootB, "files").apply { mkdirs() }
        deviceBDatabasesDir = File(rootB, "databases").apply { mkdirs() }
        deviceBCacheDir = File(rootB, "cache").apply { mkdirs() }
        deviceBKeyDir = File(rootB, "keys").apply { mkdirs() }
        deviceBKeyProvider = InMemoryDeviceKeyProvider()
        deviceBEnvelopeManager = KeyEnvelopeManager(deviceBKeyDir, deviceBKeyProvider)
        deviceBService =
            LifeVaultArchiveService(
                appFilesDir = deviceBFilesDir,
                appDatabasesDir = deviceBDatabasesDir,
                cacheDir = deviceBCacheDir,
            )
    }

    // =========================================================================
    // 1. archive-roundtrip: Full synthetic export -> restore on clean device B
    // =========================================================================
    @Test
    fun testArchiveRoundtrip_FullExportAndRestoreOnCleanDevice() {
        // Setup initial databases & attachments on Device A
        val (originalMainKey, originalVaultKey) = deviceAEnvelopeManager.setupNewPin(deviceAPin)

        val mainDb =
            File(deviceADatabasesDir, "main.db").apply {
                writeBytes("SYNTHETIC_SQLCIPHER_MAIN_DATABASE_PAYLOAD_V1".toByteArray())
            }
        val vaultDb =
            File(deviceADatabasesDir, "vault.db").apply {
                writeBytes("SYNTHETIC_SQLCIPHER_VAULT_DATABASE_PAYLOAD_V1".toByteArray())
            }

        val attachmentsDir = File(deviceAFilesDir, "attachments").apply { mkdirs() }
        val att1 = File(attachmentsDir, "img1.bin").apply { writeBytes("ENCRYPTED_ATTACHMENT_DATA_1".toByteArray()) }
        val att2 = File(attachmentsDir, "img2.bin").apply { writeBytes("ENCRYPTED_ATTACHMENT_DATA_2".toByteArray()) }

        val thumbsDir = File(deviceAFilesDir, "thumbnails").apply { mkdirs() }
        val thumb1 = File(thumbsDir, "img1_thumb.bin").apply { writeBytes("ENCRYPTED_THUMBNAIL_DATA_1".toByteArray()) }

        // Export archive to memory
        val archiveBytes =
            ByteArrayOutputStream().use { baos ->
                deviceAService.exportArchive(
                    passphrase = validPassphrase,
                    outputStream = baos,
                    mainDbKey = originalMainKey,
                    vaultDbKey = originalVaultKey,
                )
                baos.toByteArray()
            }

        // Validate archive header
        assertTrue(archiveBytes.size > 64)
        val magic = archiveBytes.copyOfRange(0, 7)
        assertArrayEquals(LifeVaultArchiveService.MAGIC_BYTES, magic)
        assertEquals(LifeVaultArchiveService.FORMAT_VERSION, archiveBytes[7])

        // Verify Device B is clean initially
        assertFalse(File(deviceBDatabasesDir, "main.db").exists())
        assertFalse(File(deviceBDatabasesDir, "vault.db").exists())
        assertFalse(deviceBEnvelopeManager.isConfigured())

        // Restore on clean Device B with brand new PIN
        ByteArrayInputStream(archiveBytes).use { bais ->
            deviceBService.restoreArchive(
                passphrase = validPassphrase,
                inputStream = bais,
                targetPin = deviceBPin,
                targetKeyEnvelopeManager = deviceBEnvelopeManager,
            )
        }

        // Verify Device B files are restored and match Device A exactly
        val restoredMainDb = File(deviceBDatabasesDir, "main.db")
        val restoredVaultDb = File(deviceBDatabasesDir, "vault.db")
        assertTrue(restoredMainDb.exists())
        assertTrue(restoredVaultDb.exists())
        assertArrayEquals(mainDb.readBytes(), restoredMainDb.readBytes())
        assertArrayEquals(vaultDb.readBytes(), restoredVaultDb.readBytes())

        val restoredAtt1 = File(deviceBFilesDir, "attachments/img1.bin")
        val restoredAtt2 = File(deviceBFilesDir, "attachments/img2.bin")
        val restoredThumb1 = File(deviceBFilesDir, "thumbnails/img1_thumb.bin")
        assertTrue(restoredAtt1.exists())
        assertTrue(restoredAtt2.exists())
        assertTrue(restoredThumb1.exists())
        assertArrayEquals(att1.readBytes(), restoredAtt1.readBytes())
        assertArrayEquals(att2.readBytes(), restoredAtt2.readBytes())
        assertArrayEquals(thumb1.readBytes(), restoredThumb1.readBytes())

        // Verify Device B key envelopes work with deviceBPin
        assertTrue(deviceBEnvelopeManager.isConfigured())
        val unlockedMainKey = deviceBEnvelopeManager.unlockScope(KeyScope.MAIN, deviceBPin)
        val unlockedVaultKey = deviceBEnvelopeManager.unlockScope(KeyScope.VAULT, deviceBPin)
        assertArrayEquals(originalMainKey, unlockedMainKey)
        assertArrayEquals(originalVaultKey, unlockedVaultKey)

        // Verify old PIN cannot unlock on Device B
        try {
            deviceBEnvelopeManager.unlockScope(KeyScope.MAIN, deviceAPin)
            fail("Expected failure when unlocking Device B with Device A's PIN")
        } catch (expected: GeneralSecurityException) {
            // Expected
        }
    }

    // =========================================================================
    // 2. archive-negative: Rejection of corruptions, wrong phrase, bad params
    // =========================================================================
    @Test
    fun testArchiveNegative_WrongPassphrase() {
        val (mainKey, vaultKey) = deviceAEnvelopeManager.setupNewPin(deviceAPin)
        File(deviceADatabasesDir, "main.db").writeBytes(byteArrayOf(1, 2, 3))

        val archiveBytes =
            ByteArrayOutputStream().use { baos ->
                deviceAService.exportArchive(
                    passphrase = validPassphrase,
                    outputStream = baos,
                    mainDbKey = mainKey,
                    vaultDbKey = vaultKey,
                )
                baos.toByteArray()
            }

        try {
            ByteArrayInputStream(archiveBytes).use { bais ->
                deviceBService.restoreArchive(
                    passphrase = "WrongPassphrase123!",
                    inputStream = bais,
                    targetPin = deviceBPin,
                    targetKeyEnvelopeManager = deviceBEnvelopeManager,
                )
            }
            fail("Expected restore failure with wrong passphrase")
        } catch (expected: GeneralSecurityException) {
            // Expected BadTag
        }
    }

    @Test
    fun testArchiveNegative_TamperedCiphertext() {
        val (mainKey, vaultKey) = deviceAEnvelopeManager.setupNewPin(deviceAPin)
        File(deviceADatabasesDir, "main.db").writeBytes(byteArrayOf(1, 2, 3))

        val archiveBytes =
            ByteArrayOutputStream().use { baos ->
                deviceAService.exportArchive(
                    passphrase = validPassphrase,
                    outputStream = baos,
                    mainDbKey = mainKey,
                    vaultDbKey = vaultKey,
                )
                baos.toByteArray()
            }

        // Flip byte in encrypted payload body
        archiveBytes[archiveBytes.size - 10] = (archiveBytes[archiveBytes.size - 10].toInt() xor 0xFF).toByte()

        try {
            ByteArrayInputStream(archiveBytes).use { bais ->
                deviceBService.restoreArchive(
                    passphrase = validPassphrase,
                    inputStream = bais,
                    targetPin = deviceBPin,
                    targetKeyEnvelopeManager = deviceBEnvelopeManager,
                )
            }
            fail("Expected restore failure with tampered ciphertext")
        } catch (expected: Exception) {
            // Tink Streaming AEAD throws GeneralSecurityException or IOException on tag mismatch
            assertTrue(expected is GeneralSecurityException || expected is java.io.IOException)
        }
    }

    @Test
    fun testArchiveNegative_TruncatedArchive() {
        val (mainKey, vaultKey) = deviceAEnvelopeManager.setupNewPin(deviceAPin)
        File(deviceADatabasesDir, "main.db").writeBytes(byteArrayOf(1, 2, 3, 4, 5))

        val archiveBytes =
            ByteArrayOutputStream().use { baos ->
                deviceAService.exportArchive(
                    passphrase = validPassphrase,
                    outputStream = baos,
                    mainDbKey = mainKey,
                    vaultDbKey = vaultKey,
                )
                baos.toByteArray()
            }

        // Truncate to half
        val truncated = archiveBytes.copyOfRange(0, archiveBytes.size / 2)

        try {
            ByteArrayInputStream(truncated).use { bais ->
                deviceBService.restoreArchive(
                    passphrase = validPassphrase,
                    inputStream = bais,
                    targetPin = deviceBPin,
                    targetKeyEnvelopeManager = deviceBEnvelopeManager,
                )
            }
            fail("Expected restore failure with truncated archive")
        } catch (expected: Exception) {
            // Expected EOF / GSE
        }
    }

    @Test
    fun testArchiveNegative_InvalidHeaderAndKdfBounds() {
        // Construct archive with corrupted magic header
        val badHeader = ByteArray(100)
        System.arraycopy("BADMAG\u0000".toByteArray(), 0, badHeader, 0, 7)

        try {
            ByteArrayInputStream(badHeader).use { bais ->
                deviceBService.restoreArchive(
                    passphrase = validPassphrase,
                    inputStream = bais,
                    targetPin = deviceBPin,
                    targetKeyEnvelopeManager = deviceBEnvelopeManager,
                )
            }
            fail("Expected restore failure with bad magic")
        } catch (expected: GeneralSecurityException) {
            assertTrue(expected.message!!.contains("magic"))
        }

        // Construct archive with excessive KDF parameters (DoS attempt)
        val dosHeaderBaos = ByteArrayOutputStream()
        val dos = DataOutputStream(dosHeaderBaos)
        dos.write(LifeVaultArchiveService.MAGIC_BYTES)
        dos.writeByte(LifeVaultArchiveService.FORMAT_VERSION.toInt())
        dos.write(ByteArray(16)) // salt
        dos.writeInt(1024 * 1024 * 10) // 10 GiB memory request!
        dos.writeInt(100) // 100 iterations
        dos.writeShort(16) // parallelism 16
        dos.writeShort(32) // sealed key size
        dos.write(ByteArray(32))
        dos.flush()

        try {
            ByteArrayInputStream(dosHeaderBaos.toByteArray()).use { bais ->
                deviceBService.restoreArchive(
                    passphrase = validPassphrase,
                    inputStream = bais,
                    targetPin = deviceBPin,
                    targetKeyEnvelopeManager = deviceBEnvelopeManager,
                )
            }
            fail("Expected restore failure with DoS KDF bounds")
        } catch (expected: GeneralSecurityException) {
            assertTrue(expected.message!!.contains("exceed secure bounds"))
        }
    }

    // =========================================================================
    // 3. restore-atomic: Crash/failure does not corrupt existing target state
    // =========================================================================
    @Test
    fun testRestoreAtomic_FailurePreservesExistingData() {
        // Device B already has existing active data
        val (bMainKey, bVaultKey) = deviceBEnvelopeManager.setupNewPin(deviceBPin)
        val existingDb =
            File(deviceBDatabasesDir, "main.db").apply {
                writeBytes("EXISTING_UNTOUCHED_PRODUCTION_DATA".toByteArray())
            }

        // Construct a broken archive stream
        val brokenArchive = "NOT_A_VALID_ARCHIVE_DATA".toByteArray()

        try {
            ByteArrayInputStream(brokenArchive).use { bais ->
                deviceBService.restoreArchive(
                    passphrase = validPassphrase,
                    inputStream = bais,
                    targetPin = "999888",
                    targetKeyEnvelopeManager = deviceBEnvelopeManager,
                )
            }
            fail("Expected failure on broken archive")
        } catch (expected: Exception) {
            // Expected
        }

        // Verify Device B's existing database remains 100% intact
        assertTrue(existingDb.exists())
        assertEquals("EXISTING_UNTOUCHED_PRODUCTION_DATA", existingDb.readText())

        // Verify Device B's envelopes and PIN still work with existing deviceBPin
        val mainKey = deviceBEnvelopeManager.unlockScope(KeyScope.MAIN, deviceBPin)
        assertArrayEquals(bMainKey, mainKey)
    }

    // =========================================================================
    // 4. archive-private: No plaintext leaks in cache, zeroization
    // =========================================================================
    @Test
    fun testArchivePrivate_NoPlaintextLeaksInCache() {
        val (mainKey, vaultKey) = deviceAEnvelopeManager.setupNewPin(deviceAPin)
        File(deviceADatabasesDir, "main.db").writeBytes("SECRET_MAIN_DATA".toByteArray())

        val archiveBytes =
            ByteArrayOutputStream().use { baos ->
                deviceAService.exportArchive(
                    passphrase = validPassphrase,
                    outputStream = baos,
                    mainDbKey = mainKey,
                    vaultDbKey = vaultKey,
                )
                baos.toByteArray()
            }

        // Check Device A cache: must be completely empty or contain no unencrypted files
        val cacheAFiles = deviceACacheDir.listFiles() ?: emptyArray()
        assertEquals("Device A cache must be empty after export", 0, cacheAFiles.size)

        // Restore on Device B
        ByteArrayInputStream(archiveBytes).use { bais ->
            deviceBService.restoreArchive(
                passphrase = validPassphrase,
                inputStream = bais,
                targetPin = deviceBPin,
                targetKeyEnvelopeManager = deviceBEnvelopeManager,
            )
        }

        // Check Device B cache: staging directory must be cleanly removed
        val cacheBFiles = deviceBCacheDir.listFiles() ?: emptyArray()
        assertEquals("Device B cache must be empty after restore", 0, cacheBFiles.size)
    }
}

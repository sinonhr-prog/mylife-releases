package local.mylife.app.feature.settings.backup

import local.mylife.core.security.crypto.InMemoryDeviceKeyProvider
import local.mylife.core.security.crypto.KeyEnvelopeManager
import local.mylife.core.storage.backup.LifeVaultArchiveService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.security.GeneralSecurityException

/**
 * Тесты UX и бизнес-логики экспорта и восстановления резервных копий (H09: backup-ux & restore-ux)
 */
class BackupRestoreFlowTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var filesDir: File
    private lateinit var databasesDir: File
    private lateinit var cacheDir: File
    private lateinit var keyDir: File
    private lateinit var envelopeManager: KeyEnvelopeManager
    private lateinit var archiveService: LifeVaultArchiveService

    private val mainDbKey = ByteArray(32) { 1 }
    private val vaultDbKey = ByteArray(32) { 2 }
    private val validPassphrase = "ValidSecurePassphrase123!"

    @Before
    fun setUp() {
        val root = tempFolder.newFolder("backup_test_root")
        filesDir = File(root, "files").apply { mkdirs() }
        databasesDir = File(root, "databases").apply { mkdirs() }
        cacheDir = File(root, "cache").apply { mkdirs() }
        keyDir = File(root, "keys").apply { mkdirs() }

        envelopeManager = KeyEnvelopeManager(keyDir, InMemoryDeviceKeyProvider())
        envelopeManager.setupNewPin("123456")

        archiveService = LifeVaultArchiveService(
            appFilesDir = filesDir,
            appDatabasesDir = databasesDir,
            cacheDir = cacheDir,
        )

        // Подготовим фиктивные базы и файлы
        File(databasesDir, "main.db").writeText("SQLite format 3 (Mock Main DB Content)")
        File(databasesDir, "vault.db").writeText("SQLite format 3 (Mock Vault DB Content)")
        val attachDir = File(filesDir, "attachments").apply { mkdirs() }
        File(attachDir, "photo.bin").writeBytes(byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D))
    }

    @Test
    fun testBackupExport_ValidationAndIntegrity() {
        // 1. Попытка создания с короткой фразой (< 8 символов) должна завершиться исключением
        val outStream = ByteArrayOutputStream()
        try {
            archiveService.exportArchive(
                passphrase = "short",
                outputStream = outStream,
                mainDbKey = mainDbKey,
                vaultDbKey = vaultDbKey,
            )
            org.junit.Assert.fail("Ожидалось исключение для короткой ключевой фразы")
        } catch (e: IllegalArgumentException) {
            assertTrue(e.message?.contains("at least 8") == true)
        }

        // 2. Успешный экспорт с валидной фразой
        val validOut = ByteArrayOutputStream()
        archiveService.exportArchive(
            passphrase = validPassphrase,
            outputStream = validOut,
            mainDbKey = mainDbKey,
            vaultDbKey = vaultDbKey,
        )

        val archiveBytes = validOut.toByteArray()
        assertTrue("Размер архива должен быть > 0", archiveBytes.isNotEmpty())

        // Проверяем сигнатуру заголовка LIFEV1\0
        val magic = archiveBytes.take(7).toByteArray()
        assertTrue(magic.contentEquals(LifeVaultArchiveService.MAGIC_BYTES))
    }

    @Test
    fun testBackupInspect_ManifestPreview() {
        // Создаем архив
        val archiveOut = ByteArrayOutputStream()
        archiveService.exportArchive(
            passphrase = validPassphrase,
            outputStream = archiveOut,
            mainDbKey = mainDbKey,
            vaultDbKey = vaultDbKey,
        )
        val archiveBytes = archiveOut.toByteArray()

        // 1. Ошибка инспекции при неверной фразе
        try {
            archiveService.inspectArchive(
                passphrase = "WrongPassphraseHere!",
                inputStream = ByteArrayInputStream(archiveBytes),
            )
            org.junit.Assert.fail("Ожидалась ошибка инспекции при неверной фразе")
        } catch (e: GeneralSecurityException) {
            // Expected AEAD decryption failure
        }

        // 2. Успешная инспекция и получение манифеста с валидной фразой
        val manifest = archiveService.inspectArchive(
            passphrase = validPassphrase,
            inputStream = ByteArrayInputStream(archiveBytes),
        )

        assertNotNull(manifest)
        assertEquals(1, manifest.formatVersion)
        assertTrue(manifest.exportedAtEpochMs > 0)
        assertTrue("Манифест должен содержать main.db", manifest.files.containsKey("databases/main.db"))
        assertTrue("Манифест должен содержать vault.db", manifest.files.containsKey("databases/vault.db"))
        assertTrue("Манифест должен содержать вложение", manifest.files.containsKey("attachments/photo.bin"))
        assertTrue("Манифест должен содержать ключи", manifest.files.containsKey("keys/keys.bin"))
    }

    @Test
    fun testBackupRestore_SwapDataAndChangePin() {
        // Создаем исходный архив
        val archiveOut = ByteArrayOutputStream()
        archiveService.exportArchive(
            passphrase = validPassphrase,
            outputStream = archiveOut,
            mainDbKey = mainDbKey,
            vaultDbKey = vaultDbKey,
        )
        val archiveBytes = archiveOut.toByteArray()

        // Меняем локальные файлы на устройстве
        File(databasesDir, "main.db").writeText("MODIFIED DATA BEFORE RESTORE")
        File(filesDir, "attachments/photo.bin").writeBytes(byteArrayOf(0x99.toByte()))

        // Создаем новое чистое целевое окружение устройства (новый PIN)
        val targetKeyDir = tempFolder.newFolder("target_device_keys")
        val targetEnvelopeManager = KeyEnvelopeManager(targetKeyDir, InMemoryDeviceKeyProvider())
        val newTargetPin = "654321"

        // Восстанавливаем данные
        archiveService.restoreArchive(
            passphrase = validPassphrase,
            inputStream = ByteArrayInputStream(archiveBytes),
            targetPin = newTargetPin,
            targetKeyEnvelopeManager = targetEnvelopeManager,
        )

        // Проверяем, что файлы баз и вложений успешно восстановились из архива
        val restoredMainDbText = File(databasesDir, "main.db").readText()
        assertEquals("SQLite format 3 (Mock Main DB Content)", restoredMainDbText)

        val restoredPhotoBytes = File(filesDir, "attachments/photo.bin").readBytes()
        assertTrue(byteArrayOf(0x0A, 0x0B, 0x0C, 0x0D).contentEquals(restoredPhotoBytes))

        // Проверяем, что новые ключи успешно разблокируются новым PIN
        val unsealedMainKey = targetEnvelopeManager.unlockScope(local.mylife.core.security.crypto.KeyScope.MAIN, newTargetPin)
        assertTrue("Основной ключ БД должен совпасть с исходным", mainDbKey.contentEquals(unsealedMainKey))

        val unsealedVaultKey = targetEnvelopeManager.unlockScope(local.mylife.core.security.crypto.KeyScope.VAULT, newTargetPin)
        assertTrue("Ключ Сейфа должен совпасть с исходным", vaultDbKey.contentEquals(unsealedVaultKey))
    }
}

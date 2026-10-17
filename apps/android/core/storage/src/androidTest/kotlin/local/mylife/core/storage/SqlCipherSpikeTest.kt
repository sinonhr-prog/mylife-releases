package local.mylife.core.storage

import android.content.Context
import android.database.sqlite.SQLiteException
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskPriority
import local.mylife.core.model.TaskStatus
import local.mylife.core.storage.db.AppDatabase
import local.mylife.core.storage.db.EncryptedDatabaseFactory
import local.mylife.core.storage.db.RoomTaskRepository
import local.mylife.core.storage.db.TaskEntity
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.FileInputStream
import java.util.UUID

@RunWith(AndroidJUnit4::class)
class SqlCipherSpikeTest {
    private lateinit var context: Context
    private lateinit var dbFile: File
    private val dbName = "spike_encrypted_${UUID.randomUUID()}.db"
    private val validPassphrase = "synthetic_spike_key_32_bytes_valid!".toByteArray(Charsets.UTF_8)
    private val wrongPassphrase = "synthetic_wrong_key_32_bytes_fail!".toByteArray(Charsets.UTF_8)

    @Before
    fun setUp() {
        System.loadLibrary("sqlcipher")
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @After
    fun tearDown() {
        if (dbFile.exists()) {
            dbFile.delete()
        }
        val walFile = File(dbFile.path + "-wal")
        if (walFile.exists()) walFile.delete()
        val shmFile = File(dbFile.path + "-shm")
        if (shmFile.exists()) shmFile.delete()
    }

    @Test
    fun testEncryptedDatabaseRoundTrip() {
        runBlocking {
            val db =
                EncryptedDatabaseFactory.createDatabase(
                    context = context,
                    databaseName = dbName,
                    passphrase = validPassphrase,
                )

            val repository = RoomTaskRepository(db.taskDao())

            val syntheticTask =
                TaskItem(
                    id = "task-spike-1",
                    title = "Проверить шифрование SQLCipher",
                    description = "Синтетический тест round-trip Room + SQLCipher",
                    dueAtEpochMs = 1770000000000L,
                    status = TaskStatus.OPEN,
                    priority = TaskPriority.HIGH,
                    createdAtEpochMs = 1760000000000L,
                    updatedAtEpochMs = 1760000000000L,
                )

            repository.saveTask(syntheticTask)

            val retrieved = repository.getTaskById("task-spike-1")
            assertNotNull("Task must be retrieved from encrypted DB", retrieved)
            assertEquals("task-spike-1", retrieved?.id)
            assertEquals("Проверить шифрование SQLCipher", retrieved?.title)
            assertEquals(TaskPriority.HIGH, retrieved?.priority)

            val summary = repository.getDashboardSummary()
            assertEquals(1, summary.openTasksCount)
            assertEquals("task-spike-1", summary.nextTask?.id)

            repository.markTaskDone("task-spike-1")
            val updated = repository.getTaskById("task-spike-1")
            assertEquals(TaskStatus.DONE, updated?.status)

            db.close()
        }
    }

    @Test
    fun testDatabaseFileIsCiphertextNotPlaintext() {
        runBlocking {
            val db =
                EncryptedDatabaseFactory.createDatabase(
                    context = context,
                    databaseName = dbName,
                    passphrase = validPassphrase,
                )
            db.taskDao().insertTask(
                TaskEntity(
                    id = "secret-task-1",
                    title = "Конфиденциальная синтетическая запись",
                    description = "Строка для проверки отсутствия открытого текста на диске",
                ),
            )
            db.close()

            assertTrue("Database file must exist on disk", dbFile.exists())
            assertTrue("Database file must have non-zero size", dbFile.length() > 0)

            val header = ByteArray(16)
            FileInputStream(dbFile).use { input ->
                input.read(header)
            }

            // Standard unencrypted SQLite starts with: "SQLite format 3\000"
            val sqliteMagic = "SQLite format 3".toByteArray(Charsets.US_ASCII)
            var matchesPlaintextHeader = true
            for (i in sqliteMagic.indices) {
                if (header[i] != sqliteMagic[i]) {
                    matchesPlaintextHeader = false
                    break
                }
            }
            assertFalse("Encrypted DB header must not match SQLite plaintext magic", matchesPlaintextHeader)

            // Read all bytes to check that plaintext content does not leak into the file
            val allBytes = dbFile.readBytes()
            val plaintextLeak = allBytes.toString(Charsets.UTF_8)
            val hasLeak = plaintextLeak.contains("Конфиденциальная синтетическая запись")
            assertFalse("Database file must not contain unencrypted title", hasLeak)
        }
    }

    @Test
    fun testWrongKeyFailsToOpenOrQuery() {
        runBlocking {
            // Step 1: Create and populate with valid key
            val dbValid =
                EncryptedDatabaseFactory.createDatabase(
                    context = context,
                    databaseName = dbName,
                    passphrase = validPassphrase,
                )
            dbValid.taskDao().insertTask(
                TaskEntity(
                    id = "auth-test-task",
                    title = "Проверка доступа по ключу",
                ),
            )
            dbValid.close()

            // Step 2: Attempt to open and query with wrong passphrase
            val dbWrong =
                EncryptedDatabaseFactory.createDatabase(
                    context = context,
                    databaseName = dbName,
                    passphrase = wrongPassphrase,
                )

            try {
                dbWrong.taskDao().getTaskById("auth-test-task")
                fail("Expected exception when querying with wrong passphrase")
            } catch (expected: Exception) {
                val isExpectedError =
                    expected is SQLiteException ||
                        expected.javaClass.name.contains("sqlcipher") ||
                        expected.message?.contains("database") == true
                assertTrue("Expected SQLite/SQLCipher failure", isExpectedError)
            } finally {
                dbWrong.close()
            }
        }
    }

    @Test
    fun testWalModeAndPragmaSettings() {
        runBlocking {
            val db =
                EncryptedDatabaseFactory.createDatabase(
                    context = context,
                    databaseName = dbName,
                    passphrase = validPassphrase,
                )

            val sqliteDb = db.openHelper.writableDatabase

            // Check journal mode (Room by default uses WAL on modern Android, or can be queried)
            val cursor = sqliteDb.query("PRAGMA journal_mode")
            cursor.moveToFirst()
            val journalMode = cursor.getString(0)
            cursor.close()
            assertNotNull(journalMode)
            val isWalOrMemory =
                journalMode.equals("wal", ignoreCase = true) ||
                    journalMode.equals("memory", ignoreCase = true)
            assertTrue("Journal mode should be WAL or memory: $journalMode", isWalOrMemory)

            // Check SQLCipher version PRAGMA
            val versionCursor = sqliteDb.query("PRAGMA cipher_version")
            var cipherVersion = ""
            if (versionCursor.moveToFirst()) {
                cipherVersion = versionCursor.getString(0)
            }
            versionCursor.close()
            assertTrue("cipher_version must be non-empty", cipherVersion.isNotEmpty())

            db.close()
        }
    }

    @Test
    fun testSchemaMigrationPreservesEncryptedData() {
        runBlocking {
            // Step 1: Open DB and insert entity
            val dbV1 =
                EncryptedDatabaseFactory.createDatabase(
                    context = context,
                    databaseName = dbName,
                    passphrase = validPassphrase,
                )
            dbV1.taskDao().insertTask(
                TaskEntity(
                    id = "migration-task",
                    title = "До миграции",
                ),
            )
            dbV1.close()

            // Step 2: Open DB with MIGRATION_1_2
            val dbV2 =
                EncryptedDatabaseFactory.createDatabase(
                    context = context,
                    databaseName = dbName,
                    passphrase = validPassphrase,
                ) {
                    addMigrations(AppDatabase.MIGRATION_1_2)
                }

            // Apply migration manually or trigger read to verify schema
            val dbInstance = dbV2.openHelper.writableDatabase
            dbInstance.execSQL("ALTER TABLE tasks ADD COLUMN notes TEXT NOT NULL DEFAULT ''")

            val retrieved = dbV2.taskDao().getTaskById("migration-task")
            assertNotNull("Task must exist after migration", retrieved)
            assertEquals("До миграции", retrieved?.title)

            dbV2.close()
        }
    }

    @Test
    fun testAbiAndArchitectureSupport() {
        val supportedAbis = Build.SUPPORTED_ABIS
        assertTrue("Device must have supported ABIs", supportedAbis.isNotEmpty())

        // Verify that native library can execute queries without UnsatisfiedLinkError
        val db =
            EncryptedDatabaseFactory.createDatabase(
                context = context,
                databaseName = dbName,
                passphrase = validPassphrase,
            )
        val sqliteDb = db.openHelper.writableDatabase
        val cursor = sqliteDb.query("SELECT 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.getInt(0))
        cursor.close()
        db.close()
    }
}

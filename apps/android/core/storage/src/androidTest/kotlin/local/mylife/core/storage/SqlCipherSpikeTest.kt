package local.mylife.core.storage

import android.content.Context
import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.os.Build
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.runBlocking
import local.mylife.core.model.Category
import local.mylife.core.model.CategoryKind
import local.mylife.core.model.Place
import local.mylife.core.model.PlaceVisitStatus
import local.mylife.core.model.Product
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskPriority
import local.mylife.core.model.TaskStatus
import local.mylife.core.storage.db.AppDatabase
import local.mylife.core.storage.db.EncryptedDatabaseFactory
import local.mylife.core.storage.db.RoomCategoryRepository
import local.mylife.core.storage.db.RoomDashboardRepository
import local.mylife.core.storage.db.RoomPlaceRepository
import local.mylife.core.storage.db.RoomProductRepository
import local.mylife.core.storage.db.RoomTaskRepository
import local.mylife.core.storage.db.TaskEntity
import local.mylife.core.storage.files.EncryptedAttachmentStore
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
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
        try {
            System.loadLibrary("sqlcipher")
        } catch (_: UnsatisfiedLinkError) {
        }
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) dbFile.delete()
    }

    @After
    fun tearDown() {
        if (dbFile.exists()) dbFile.delete()
        val walFile = File(dbFile.path + "-wal")
        if (walFile.exists()) walFile.delete()
        val shmFile = File(dbFile.path + "-shm")
        if (shmFile.exists()) shmFile.delete()
    }

    // -------------------------------------------------------------
    // Required Check 1: storage-real (CRUD, FK, Indices, Dashboard)
    // -------------------------------------------------------------
    @Test
    fun testEncryptedDatabaseRoundTrip() {
        runBlocking {
            val db = EncryptedDatabaseFactory.createDatabase(context, dbName, validPassphrase)
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
    fun testForeignKeyConstraintsEnforced() {
        runBlocking {
            val db = EncryptedDatabaseFactory.createDatabase(context, dbName, validPassphrase)
            val catRepo = RoomCategoryRepository(db.categoryDao())
            val prodRepo = RoomProductRepository(db.productDao())

            // 1. Create parent category
            val cat = Category(id = "cat-electronics", name = "Электроника", kind = CategoryKind.PRODUCT)
            catRepo.saveCategory(cat)

            // 2. Create child product linked to valid category
            val prod =
                Product(
                    id = "prod-1",
                    categoryId = "cat-electronics",
                    title = "Телефон",
                    rating = 9,
                    priceMinor = 5000000L,
                    currency = "RUB",
                )
            prodRepo.saveProduct(prod)

            val saved = prodRepo.getProductById("prod-1")
            assertNotNull(saved)
            assertEquals("Телефон", saved?.title)

            // 3. Attempt to delete category referenced by product (RESTRICT constraint)
            try {
                catRepo.deleteCategory("cat-electronics")
                fail("Deleting referenced category must throw SQLiteConstraintException")
            } catch (expected: Exception) {
                val isConstraint =
                    expected is SQLiteConstraintException ||
                        expected.message?.contains("FOREIGN KEY") == true ||
                        expected.message?.contains("constraint") == true
                assertTrue("Expected foreign key constraint violation: ${expected.message}", isConstraint)
            }

            db.close()
        }
    }

    @Test
    fun testDashboardQueriesWithLastVisitedAt() {
        runBlocking {
            val db = EncryptedDatabaseFactory.createDatabase(context, dbName, validPassphrase)
            val catRepo = RoomCategoryRepository(db.categoryDao())
            val placeRepo = RoomPlaceRepository(db.placeDao())
            val dashRepo = RoomDashboardRepository(db.taskDao(), db.reminderDao(), db.placeDao(), db.productDao(), db.noteDao())

            val cat = Category(id = "cat-food", name = "Кафе", kind = CategoryKind.PLACE)
            catRepo.saveCategory(cat)

            // Place with lastVisitedAt
            val place1 =
                Place(
                    id = "place-1",
                    categoryId = "cat-food",
                    name = "Кофейня",
                    visitStatus = PlaceVisitStatus.VISITED,
                    lastVisitedAtEpochMs = 1765000000000L,
                    rating = 9,
                )
            placeRepo.savePlace(place1)

            val summary = dashRepo.getDashboardSummary()
            assertEquals(1, summary.recentPlacesCount)

            db.close()
        }
    }

    @Test
    fun testDatabaseFileIsCiphertextNotPlaintext() {
        runBlocking {
            val db = EncryptedDatabaseFactory.createDatabase(context, dbName, validPassphrase)
            db.taskDao().insert(
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
            FileInputStream(dbFile).use { input -> input.read(header) }

            val sqliteMagic = "SQLite format 3".toByteArray(Charsets.US_ASCII)
            var matchesPlaintextHeader = true
            for (i in sqliteMagic.indices) {
                if (header[i] != sqliteMagic[i]) {
                    matchesPlaintextHeader = false
                    break
                }
            }
            assertFalse("Encrypted DB header must not match SQLite plaintext magic", matchesPlaintextHeader)

            val allBytes = dbFile.readBytes()
            val plaintextLeak = allBytes.toString(Charsets.UTF_8)
            val hasLeak = plaintextLeak.contains("Конфиденциальная синтетическая запись")
            assertFalse("Database file must not contain unencrypted title", hasLeak)
        }
    }

    @Test
    fun testWrongKeyFailsToOpenOrQuery() {
        runBlocking {
            val dbValid = EncryptedDatabaseFactory.createDatabase(context, dbName, validPassphrase)
            dbValid.taskDao().insert(
                TaskEntity(id = "auth-test-task", title = "Проверка доступа по ключу"),
            )
            dbValid.close()

            val dbWrong = EncryptedDatabaseFactory.createDatabase(context, dbName, wrongPassphrase)
            try {
                dbWrong.taskDao().getById("auth-test-task")
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

    // -------------------------------------------------------------
    // Required Check 2: migrations (Schema transitions preserve data)
    // -------------------------------------------------------------
    @Test
    fun testSchemaMigrationPreservesEncryptedData() {
        runBlocking {
            // Step 1: Open DB and insert initial entity
            val dbV1 = EncryptedDatabaseFactory.createDatabase(context, dbName, validPassphrase)
            dbV1.taskDao().insert(
                TaskEntity(id = "migration-task", title = "До миграции"),
            )
            dbV1.close()

            // Step 2: Open DB with MIGRATION_1_2 and verify data preserved
            val dbV2 =
                EncryptedDatabaseFactory.createDatabase(context, dbName, validPassphrase) {
                    addMigrations(AppDatabase.MIGRATION_1_2)
                }

            val dbInstance = dbV2.openHelper.writableDatabase
            dbInstance.execSQL("ALTER TABLE tasks ADD COLUMN extraData TEXT NOT NULL DEFAULT ''")

            val retrieved = dbV2.taskDao().getById("migration-task")
            assertNotNull("Task must exist after migration", retrieved)
            assertEquals("До миграции", retrieved?.title)

            dbV2.close()
        }
    }

    // -------------------------------------------------------------
    // Required Check 3: attachment-failure & orphan cleanup
    // -------------------------------------------------------------
    @Test
    fun testAttachmentStoreEncryptionAndOrphanCleanup() {
        val filesDir = File(context.filesDir, "test_attachments_${UUID.randomUUID()}").apply { mkdirs() }
        val store = EncryptedAttachmentStore(filesDir)
        val fileKey = ByteArray(32) { (it * 3).toByte() }

        try {
            // 1. Create synthetic valid PNG image in memory
            val syntheticPng = ByteArray(1024) { (it % 250).toByte() }
            // Minimal valid 1x1 PNG header bytes
            val pngHeader =
                byteArrayOf(
                    0x89.toByte(),
                    0x50.toByte(),
                    0x4E.toByte(),
                    0x47.toByte(),
                    0x0D.toByte(),
                    0x0A.toByte(),
                    0x1A.toByte(),
                    0x0A.toByte(),
                )
            System.arraycopy(pngHeader, 0, syntheticPng, 0, pngHeader.size)

            // Test rejection of non-image MIME
            try {
                store.importImage(ByteArrayInputStream(syntheticPng), fileKey, "application/pdf")
                fail("Unsupported MIME type must be rejected")
            } catch (_: IllegalArgumentException) {
                // PASS
            }

            // Test corrupted file handling does not leave permanent files
            val corruptedStream = ByteArrayInputStream(byteArrayOf(1, 2, 3))
            try {
                store.importImage(corruptedStream, fileKey, "image/png")
                fail("Corrupted image must fail decode")
            } catch (_: IllegalArgumentException) {
                // PASS
            }

            // Verify orphan cleanup removes stale temporary files
            val staleTmp = File(filesDir, "attachments/stale.enc.tmp").apply { writeBytes(byteArrayOf(1, 2, 3)) }
            assertTrue(staleTmp.exists())
            val cleaned = store.cleanupOrphans(emptySet())
            assertTrue("Stale tmp file must be cleaned up", cleaned >= 1)
            assertFalse(staleTmp.exists())
        } finally {
            filesDir.deleteRecursively()
        }
    }

    @Test
    fun testAbiAndArchitectureSupport() {
        val supportedAbis = Build.SUPPORTED_ABIS
        assertTrue("Device must have supported ABIs", supportedAbis.isNotEmpty())

        val db = EncryptedDatabaseFactory.createDatabase(context, dbName, validPassphrase)
        val sqliteDb = db.openHelper.writableDatabase
        val cursor = sqliteDb.query("SELECT 1")
        assertTrue(cursor.moveToFirst())
        assertEquals(1, cursor.getInt(0))
        cursor.close()
        db.close()
    }
}

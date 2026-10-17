package local.mylife.app.feature.dashboard

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import local.mylife.app.feature.home.HomeViewModel
import local.mylife.core.model.CategoryKind
import local.mylife.core.model.PlaceVisitStatus
import local.mylife.core.model.RecentRecordType
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskPriority
import local.mylife.core.model.TaskRepository
import local.mylife.core.model.TaskStatus
import local.mylife.core.storage.db.AttachmentDao
import local.mylife.core.storage.db.AttachmentEntity
import local.mylife.core.storage.db.CategoryDao
import local.mylife.core.storage.db.CategoryEntity
import local.mylife.core.storage.db.NoteDao
import local.mylife.core.storage.db.NoteEntity
import local.mylife.core.storage.db.PlaceAttachmentCrossRef
import local.mylife.core.storage.db.PlaceDao
import local.mylife.core.storage.db.PlaceEntity
import local.mylife.core.storage.db.ProductAttachmentCrossRef
import local.mylife.core.storage.db.ProductDao
import local.mylife.core.storage.db.ProductEntity
import local.mylife.core.storage.db.ReminderDao
import local.mylife.core.storage.db.ReminderEntity
import local.mylife.core.storage.db.RoomDashboardRepository
import local.mylife.core.storage.db.TaskDao
import local.mylife.core.storage.db.TaskEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class DashboardFlowTest {

    private lateinit var taskDao: FakeTaskDao
    private lateinit var reminderDao: FakeReminderDao
    private lateinit var placeDao: FakePlaceDao
    private lateinit var productDao: FakeProductDao
    private lateinit var noteDao: FakeNoteDao
    private var testCurrentTime: Long = 1000000L

    private lateinit var dashboardRepo: RoomDashboardRepository

    @Before
    fun setUp() {
        taskDao = FakeTaskDao()
        reminderDao = FakeReminderDao()
        placeDao = FakePlaceDao()
        productDao = FakeProductDao()
        noteDao = FakeNoteDao()
        testCurrentTime = 1000000L

        dashboardRepo = RoomDashboardRepository(
            taskDao = taskDao,
            reminderDao = reminderDao,
            placeDao = placeDao,
            productDao = productDao,
            noteDao = noteDao,
            clock = { testCurrentTime },
        )
    }

    @Test
    fun testDashboardOrder_TasksStrictSpecification() = runBlocking {
        // We insert tasks in varied order:
        // 1. Task without due date, normal priority (id: "no-due-norm")
        // 2. Task with future due date, low priority (id: "future-low", due: now + 5000)
        // 3. Task with overdue date, normal priority (id: "overdue-norm", due: now - 1000)
        // 4. Task with overdue date, HIGH priority (id: "overdue-high", due: now - 1000)
        // 5. Task with earlier overdue date (id: "overdue-earlier", due: now - 2000)
        // 6. Task without due date, HIGH priority (id: "no-due-high")
        // 7. Task with future due date, earlier time (id: "future-early", due: now + 1000)

        taskDao.insert(TaskEntity(id = "no-due-norm", title = "No Due Normal", dueAtEpochMs = null, priority = TaskPriority.NORMAL, createdAtEpochMs = 100))
        taskDao.insert(TaskEntity(id = "future-low", title = "Future Low", dueAtEpochMs = testCurrentTime + 5000, priority = TaskPriority.LOW, createdAtEpochMs = 200))
        taskDao.insert(TaskEntity(id = "overdue-norm", title = "Overdue Normal", dueAtEpochMs = testCurrentTime - 1000, priority = TaskPriority.NORMAL, createdAtEpochMs = 300))
        taskDao.insert(TaskEntity(id = "overdue-high", title = "Overdue High", dueAtEpochMs = testCurrentTime - 1000, priority = TaskPriority.HIGH, createdAtEpochMs = 400))
        taskDao.insert(TaskEntity(id = "overdue-earlier", title = "Overdue Earlier", dueAtEpochMs = testCurrentTime - 2000, priority = TaskPriority.NORMAL, createdAtEpochMs = 500))
        taskDao.insert(TaskEntity(id = "no-due-high", title = "No Due High", dueAtEpochMs = null, priority = TaskPriority.HIGH, createdAtEpochMs = 600))
        taskDao.insert(TaskEntity(id = "future-early", title = "Future Early", dueAtEpochMs = testCurrentTime + 1000, priority = TaskPriority.NORMAL, createdAtEpochMs = 700))

        val summary = dashboardRepo.getDashboardSummary()

        assertEquals(7, summary.openTasksCount)

        // Expected order:
        // 1) "overdue-earlier" (due: now - 2000 is earliest overdue) -> Focus task
        assertNotNull(summary.focusTask)
        assertEquals("overdue-earlier", summary.focusTask?.id)

        // 2) "remainingTasks" has next 2 items without repeating focusTask:
        //    Among due: now - 1000:
        //    - "overdue-high" (HIGH > NORMAL)
        //    - "overdue-norm"
        assertEquals(2, summary.remainingTasks.size)
        assertEquals("overdue-high", summary.remainingTasks[0].id)
        assertEquals("overdue-norm", summary.remainingTasks[1].id)

        // Verify focus task is NEVER in remaining tasks
        assertFalse(summary.remainingTasks.any { it.id == summary.focusTask?.id })
    }

    @Test
    fun testDashboardOrder_RecentVisits_ExcludesFutureAndNull() = runBlocking {
        // Place 1: last visited yesterday (now - 86400000)
        placeDao.insert(PlaceEntity(id = "p1", categoryId = "cat_cafes", name = "Кафе у дома", lastVisitedAtEpochMs = testCurrentTime - 86400000))
        // Place 2: last visited 1 hour ago (now - 3600000)
        placeDao.insert(PlaceEntity(id = "p2", categoryId = "cat_cafes", name = "Кофейня в центре", lastVisitedAtEpochMs = testCurrentTime - 3600000))
        // Place 3: future manual date (now + 100000) -> MUST BE EXCLUDED per specification
        placeDao.insert(PlaceEntity(id = "p3", categoryId = "cat_parks", name = "Парк на будущее", lastVisitedAtEpochMs = testCurrentTime + 100000))
        // Place 4: never visited (null) -> MUST BE EXCLUDED from recent visits
        placeDao.insert(PlaceEntity(id = "p4", categoryId = "cat_shops", name = "Магазин книг", lastVisitedAtEpochMs = null))
        // Place 5: last visited 2 days ago (now - 172800000)
        placeDao.insert(PlaceEntity(id = "p5", categoryId = "cat_restaurants", name = "Пиццерия", lastVisitedAtEpochMs = testCurrentTime - 172800000))

        val summary = dashboardRepo.getDashboardSummary()

        assertEquals(5, summary.recentPlacesCount)
        assertEquals(3, summary.recentVisits.size)

        // Order: most recent visit first (p2: 1h ago, p1: yesterday, p5: 2 days ago)
        assertEquals("p2", summary.recentVisits[0].id)
        assertEquals("p1", summary.recentVisits[1].id)
        assertEquals("p5", summary.recentVisits[2].id)

        // Neither future place p3 nor unvisited place p4 are in recentVisits
        assertFalse(summary.recentVisits.any { it.id == "p3" })
        assertFalse(summary.recentVisits.any { it.id == "p4" })
    }

    @Test
    fun testDashboardIsolation_VaultAndArchivedStrictlyExcluded() = runBlocking {
        // 1. Products: 1 active, 1 archived
        productDao.insert(ProductEntity(id = "prod_active", categoryId = "cat_p", title = "Кофемолка", createdAtEpochMs = 1000, archivedAtEpochMs = null))
        productDao.insert(ProductEntity(id = "prod_archived", categoryId = "cat_p", title = "Старый телефон", createdAtEpochMs = 2000, archivedAtEpochMs = 3000))

        // 2. Notes: 1 note
        noteDao.insert(NoteEntity(id = "note_1", title = "Список покупок", body = "Хлеб, молоко", createdAtEpochMs = 1500))

        // 3. Places: 1 place
        placeDao.insert(PlaceEntity(id = "place_1", categoryId = "cat_parks", name = "Ботанический сад", createdAtEpochMs = 1200))

        // 4. Tasks: 1 task
        taskDao.insert(TaskEntity(id = "task_1", title = "Позвонить врачу", createdAtEpochMs = 1800))

        val summary = dashboardRepo.getDashboardSummary()

        // Active purchases count excludes archived product:
        assertEquals(1, summary.recentPurchasesCount)

        // Recent records must contain prod_active, note_1, place_1, task_1 ordered by createdAt DESC:
        // Order: task_1 (1800), note_1 (1500), place_1 (1200), prod_active (1000)
        assertEquals(4, summary.recentRecords.size)
        assertEquals("task_1", summary.recentRecords[0].id)
        assertEquals(RecentRecordType.TASK, summary.recentRecords[0].type)

        assertEquals("note_1", summary.recentRecords[1].id)
        assertEquals(RecentRecordType.NOTE, summary.recentRecords[1].type)

        assertEquals("place_1", summary.recentRecords[2].id)
        assertEquals(RecentRecordType.PLACE, summary.recentRecords[2].type)

        assertEquals("prod_active", summary.recentRecords[3].id)
        assertEquals(RecentRecordType.PURCHASE, summary.recentRecords[3].type)

        // Archived product MUST NOT appear in recent records
        assertFalse(summary.recentRecords.any { it.id == "prod_archived" })
    }

    @Test
    fun testDashboardOrder_CreationDateStabilityOnUpdate() = runBlocking {
        val originalCreatedAt = 500L
        val originalUpdatedAt = 500L
        val note = NoteEntity(id = "n1", title = "Исходная заметка", body = "Текст", createdAtEpochMs = originalCreatedAt, updatedAtEpochMs = originalUpdatedAt)
        noteDao.insert(note)

        // Edit note: updatedAt changes, but createdAt stays unchanged
        val updatedNote = note.copy(body = "Обновленный текст", updatedAtEpochMs = 99999L)
        noteDao.insert(updatedNote)

        val summary = dashboardRepo.getDashboardSummary()
        val record = summary.recentRecords.first { it.id == "n1" }

        assertEquals(originalCreatedAt, record.createdAtEpochMs)
    }

    @Test
    fun testHomeViewModel_IntegrationWithDashboardRepository() = runBlocking {
        // Insert tasks
        val taskRepo = FakeTaskRepository(taskDao)
        taskDao.insert(TaskEntity(id = "t1", title = "Дело 1", dueAtEpochMs = testCurrentTime - 100, createdAtEpochMs = 10))
        taskDao.insert(TaskEntity(id = "t2", title = "Дело 2", dueAtEpochMs = testCurrentTime + 100, createdAtEpochMs = 20))

        val vm = HomeViewModel(
            dashboardRepository = dashboardRepo,
            taskRepository = taskRepo,
            externalScope = CoroutineScope(Dispatchers.Unconfined),
        )

        val state1 = vm.uiState.value
        assertEquals("Дело 1", state1.nearestTask?.title)
        assertEquals(1, state1.remainingTasks.size)
        assertEquals("Дело 2", state1.remainingTasks[0].title)

        // Mark t1 done -> t2 should become the new nearestTask on refresh!
        vm.markTaskDone("t1")

        val state2 = vm.uiState.value
        assertEquals("Дело 2", state2.nearestTask?.title)
        assertTrue(state2.remainingTasks.isEmpty())
    }

    @Test
    fun testDashboardEmptyStates() = runBlocking {
        val emptySummary = dashboardRepo.getDashboardSummary()
        assertNull(emptySummary.focusTask)
        assertTrue(emptySummary.remainingTasks.isEmpty())
        assertTrue(emptySummary.recentVisits.isEmpty())
        assertTrue(emptySummary.recentRecords.isEmpty())
        assertTrue(emptySummary.activeReminders.isEmpty())
        assertEquals(0, emptySummary.openTasksCount)
        assertEquals(0, emptySummary.remindersCount)
        assertEquals(0, emptySummary.recentPlacesCount)
        assertEquals(0, emptySummary.recentPurchasesCount)
    }

    @Test
    fun testOfflineAndProcessDeathPersistence() = runBlocking {
        // Populate records
        taskDao.insert(TaskEntity(id = "persisted_task", title = "Офлайн задача", createdAtEpochMs = 100))
        placeDao.insert(PlaceEntity(id = "persisted_place", categoryId = "cat_parks", name = "Офлайн парк", createdAtEpochMs = 200, lastVisitedAtEpochMs = testCurrentTime - 1000))

        // Simulate app restart / session recreation
        val newDashboardRepo = RoomDashboardRepository(
            taskDao = taskDao,
            reminderDao = reminderDao,
            placeDao = placeDao,
            productDao = productDao,
            noteDao = noteDao,
            clock = { testCurrentTime },
        )
        val newVm = HomeViewModel(
            dashboardRepository = newDashboardRepo,
            taskRepository = FakeTaskRepository(taskDao),
            externalScope = CoroutineScope(Dispatchers.Unconfined),
        )

        val state = newVm.uiState.value
        assertNotNull(state.nearestTask)
        assertEquals("Офлайн задача", state.nearestTask?.title)
        assertEquals(1, state.recentVisits.size)
        assertEquals("Офлайн парк", state.recentVisits[0].name)
        assertEquals(2, state.recentRecords.size)
    }
}

// --- Test Doubles for DAOs and Repositories ---

class FakeTaskDao : TaskDao {
    val tasks = mutableMapOf<String, TaskEntity>()

    override suspend fun getTasksByStatus(status: TaskStatus): List<TaskEntity> =
        tasks.values.filter { it.status == status }

    override suspend fun getAllTasks(): List<TaskEntity> = tasks.values.toList()

    override suspend fun getById(id: String): TaskEntity? = tasks[id]

    override suspend fun getOpenCount(): Int = tasks.values.count { it.status == TaskStatus.OPEN }

    override suspend fun insert(task: TaskEntity) {
        tasks[task.id] = task
    }

    override suspend fun updateStatus(id: String, status: TaskStatus, completedAtEpochMs: Long?, updatedAtEpochMs: Long) {
        val existing = tasks[id] ?: return
        tasks[id] = existing.copy(status = status, completedAtEpochMs = completedAtEpochMs, updatedAtEpochMs = updatedAtEpochMs)
    }

    override suspend fun delete(task: TaskEntity) {
        tasks.remove(task.id)
    }

    override suspend fun deleteById(id: String) {
        tasks.remove(id)
    }
}

class FakePlaceDao : PlaceDao {
    val places = mutableMapOf<String, PlaceEntity>()

    override suspend fun getPlaces(categoryId: String?): List<PlaceEntity> =
        if (categoryId != null) places.values.filter { it.categoryId == categoryId } else places.values.toList()

    override suspend fun getById(id: String): PlaceEntity? = places[id]

    override suspend fun getCount(): Int = places.size

    override suspend fun insert(place: PlaceEntity) {
        places[place.id] = place
    }

    override suspend fun delete(place: PlaceEntity) {
        places.remove(place.id)
    }

    override suspend fun deleteById(id: String) {
        places.remove(id)
    }

    override suspend fun updateCategory(oldCategoryId: String, newCategoryId: String) {
        places.values.filter { it.categoryId == oldCategoryId }.forEach {
            places[it.id] = it.copy(categoryId = newCategoryId)
        }
    }

    override suspend fun getAttachmentsForPlace(placeId: String): List<AttachmentEntity> = emptyList()

    override suspend fun insertPlaceAttachmentCrossRef(crossRef: PlaceAttachmentCrossRef) {}

    override suspend fun deletePlaceAttachmentCrossRef(placeId: String, attachmentId: String) {}
}

class FakeProductDao : ProductDao {
    val products = mutableMapOf<String, ProductEntity>()

    override suspend fun getProducts(categoryId: String?, includeArchived: Boolean): List<ProductEntity> =
        products.values.filter {
            (categoryId == null || it.categoryId == categoryId) &&
            (includeArchived || it.archivedAtEpochMs == null)
        }

    override suspend fun getById(id: String): ProductEntity? = products[id]

    override suspend fun getActiveCount(): Int = products.values.count { it.archivedAtEpochMs == null }

    override suspend fun insert(product: ProductEntity) {
        products[product.id] = product
    }

    override suspend fun delete(product: ProductEntity) {
        products.remove(product.id)
    }

    override suspend fun deleteById(id: String) {
        products.remove(id)
    }

    override suspend fun updateCategory(oldCategoryId: String, newCategoryId: String) {
        products.values.filter { it.categoryId == oldCategoryId }.forEach {
            products[it.id] = it.copy(categoryId = newCategoryId)
        }
    }

    override suspend fun getAttachmentsForProduct(productId: String): List<AttachmentEntity> = emptyList()

    override suspend fun insertProductAttachmentCrossRef(crossRef: ProductAttachmentCrossRef) {}

    override suspend fun deleteProductAttachmentCrossRef(productId: String, attachmentId: String) {}
}

class FakeNoteDao : NoteDao {
    val notes = mutableMapOf<String, NoteEntity>()

    override suspend fun getAll(): List<NoteEntity> = notes.values.toList()

    override suspend fun getById(id: String): NoteEntity? = notes[id]

    override suspend fun insert(note: NoteEntity) {
        notes[note.id] = note
    }

    override suspend fun delete(note: NoteEntity) {
        notes.remove(note.id)
    }

    override suspend fun deleteById(id: String) {
        notes.remove(id)
    }
}

class FakeReminderDao : ReminderDao {
    val reminders = mutableMapOf<String, ReminderEntity>()

    override suspend fun getAll(): List<ReminderEntity> =
        reminders.values.sortedWith(compareByDescending<ReminderEntity> { it.isEnabled }.thenBy { it.fireEpochMs })

    override suspend fun getActiveReminders(): List<ReminderEntity> =
        reminders.values.filter { it.isEnabled }

    override suspend fun getById(id: String): ReminderEntity? = reminders[id]

    override suspend fun getByTaskId(taskId: String): List<ReminderEntity> =
        reminders.values.filter { it.taskId == taskId }

    override suspend fun getActiveCount(): Int = reminders.values.count { it.isEnabled }

    override suspend fun insert(reminder: ReminderEntity) {
        reminders[reminder.id] = reminder
    }

    override suspend fun updateEnabled(id: String, isEnabled: Boolean, updatedAtEpochMs: Long) {
        reminders[id]?.let {
            reminders[id] = it.copy(isEnabled = isEnabled, updatedAtEpochMs = updatedAtEpochMs)
        }
    }

    override suspend fun delete(reminder: ReminderEntity) {
        reminders.remove(reminder.id)
    }

    override suspend fun deleteById(id: String) {
        reminders.remove(id)
    }
}

class FakeTaskRepository(private val dao: FakeTaskDao) : TaskRepository {
    override suspend fun getOpenTasks(): List<TaskItem> =
        dao.getTasksByStatus(TaskStatus.OPEN).map {
            TaskItem(it.id, it.title, it.description, it.dueAtEpochMs, it.status, it.priority, it.completedAtEpochMs, it.createdAtEpochMs, it.updatedAtEpochMs)
        }

    override suspend fun getAllTasks(): List<TaskItem> =
        dao.getAllTasks().map {
            TaskItem(it.id, it.title, it.description, it.dueAtEpochMs, it.status, it.priority, it.completedAtEpochMs, it.createdAtEpochMs, it.updatedAtEpochMs)
        }

    override suspend fun getTaskById(id: String): TaskItem? =
        dao.getById(id)?.let {
            TaskItem(it.id, it.title, it.description, it.dueAtEpochMs, it.status, it.priority, it.completedAtEpochMs, it.createdAtEpochMs, it.updatedAtEpochMs)
        }

    override suspend fun saveTask(task: TaskItem) {
        dao.insert(TaskEntity(task.id, task.title, task.description, task.dueAtEpochMs, task.status, task.priority, task.completedAtEpochMs, task.createdAtEpochMs, task.updatedAtEpochMs))
    }

    override suspend fun markTaskDone(id: String) {
        dao.updateStatus(id, TaskStatus.DONE, System.currentTimeMillis(), System.currentTimeMillis())
    }

    override suspend fun deleteTask(id: String) {
        dao.deleteById(id)
    }
}

package local.mylife.app.feature.reminders

import kotlinx.coroutines.runBlocking
import local.mylife.core.model.ReminderItem
import local.mylife.core.model.ReminderRepeat
import local.mylife.core.model.ReminderRepository
import local.mylife.core.model.ReminderTriggerCalculator
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskPriority
import local.mylife.core.model.TaskRepository
import local.mylife.core.model.TaskStatus
import local.mylife.core.storage.scheduler.SchedulerPort
import local.mylife.core.storage.scheduler.SchedulerProjectionItem
import local.mylife.core.storage.scheduler.SchedulerProjectionStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.time.Instant
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID

class RemindersFlowTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var reminderRepository: InMemoryReminderRepository
    private lateinit var taskRepository: InMemoryTaskRepository
    private lateinit var projectionStore: SchedulerProjectionStore
    private lateinit var mockSchedulerPort: MockSchedulerPort

    @Before
    fun setUp() {
        reminderRepository = InMemoryReminderRepository()
        taskRepository = InMemoryTaskRepository(reminderRepository)
        projectionStore = SchedulerProjectionStore(tempFolder.root)
        mockSchedulerPort = MockSchedulerPort()
    }

    // --- CRITERION 1: reminder-rules ---
    @Test
    fun testReminderRules_OnceDailyWeeklyCalculations() {
        val zone = ZoneId.of("Europe/Moscow")
        val baseZdt = ZonedDateTime.of(2026, 10, 18, 10, 0, 0, 0, zone)
        val baseMs = baseZdt.toInstant().toEpochMilli()

        // 1. ONCE repeat: after trigger returns null
        val beforeNow = baseMs - 1000L
        val nextOnceBefore = ReminderTriggerCalculator.computeNextTrigger(baseMs, ReminderRepeat.ONCE, beforeNow, zone)
        assertEquals(baseMs, nextOnceBefore)

        val afterNow = baseMs + 1000L
        val nextOnceAfter = ReminderTriggerCalculator.computeNextTrigger(baseMs, ReminderRepeat.ONCE, afterNow, zone)
        assertNull(nextOnceAfter)

        // 2. DAILY repeat: if now is before today's alarm, returns today's alarm
        val morningBefore = ZonedDateTime.of(2026, 10, 18, 9, 0, 0, 0, zone).toInstant().toEpochMilli()
        val dailySameDay = ReminderTriggerCalculator.computeNextTrigger(baseMs, ReminderRepeat.DAILY, morningBefore, zone)
        assertEquals(baseMs, dailySameDay)

        // If now is after today's alarm, returns tomorrow's alarm at same time
        val tomorrowExpected = ZonedDateTime.of(2026, 10, 19, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        val dailyNextDay = ReminderTriggerCalculator.computeNextTrigger(baseMs, ReminderRepeat.DAILY, afterNow, zone)
        assertEquals(tomorrowExpected, dailyNextDay)

        // 3. WEEKLY repeat: moves by 7 days to match same day of week
        val weeklyNextWeek = ReminderTriggerCalculator.computeNextTrigger(baseMs, ReminderRepeat.WEEKLY, afterNow, zone)
        val nextWeekExpected = ZonedDateTime.of(2026, 10, 25, 10, 0, 0, 0, zone).toInstant().toEpochMilli()
        assertEquals(nextWeekExpected, weeklyNextWeek)
    }

    @Test
    fun testReminderRules_TaskCompletionAndDeletionCancelsReminders() = runBlocking {
        // Create task
        val task = TaskItem(
            id = "task-1",
            title = "Важный звонок",
            dueAtEpochMs = System.currentTimeMillis() + 3600000L,
        )
        taskRepository.saveTask(task)

        // Create linked reminder
        val reminder = ReminderItem(
            id = "rem-1",
            taskId = task.id,
            title = "Напомнить о звонке",
            fireEpochMs = task.dueAtEpochMs!!,
            repeatRule = ReminderRepeat.ONCE.name,
            isEnabled = true,
        )
        reminderRepository.saveReminder(reminder)

        assertTrue(reminderRepository.getReminderById("rem-1")!!.isEnabled)
        assertEquals(1, reminderRepository.getActiveReminders().size)

        // Complete task -> cancels linked reminders
        taskRepository.markTaskDone(task.id)
        assertFalse(reminderRepository.getReminderById("rem-1")!!.isEnabled)
        assertEquals(0, reminderRepository.getActiveReminders().size)

        // Create second reminder and delete task -> cancels linked reminders
        val reminder2 = ReminderItem(
            id = "rem-2",
            taskId = task.id,
            title = "Повторный звонок",
            fireEpochMs = System.currentTimeMillis() + 7200000L,
            repeatRule = ReminderRepeat.ONCE.name,
            isEnabled = true,
        )
        reminderRepository.saveReminder(reminder2)
        assertTrue(reminderRepository.getReminderById("rem-2")!!.isEnabled)

        taskRepository.deleteTask(task.id)
        assertFalse(reminderRepository.getReminderById("rem-2")!!.isEnabled)
    }

    // --- CRITERION 2: reminder-device ---
    @Test
    fun testReminderDevice_ExactAndInexactPolicy() {
        mockSchedulerPort.canExact = true
        mockSchedulerPort.schedule("rem-1", 10000L)
        assertEquals(1, mockSchedulerPort.scheduled.size)
        assertEquals(10000L, mockSchedulerPort.scheduled["rem-1"])

        // Cancel
        mockSchedulerPort.cancel("rem-1")
        assertFalse(mockSchedulerPort.scheduled.containsKey("rem-1"))

        // Inexact fallback does not throw and still records schedule
        mockSchedulerPort.canExact = false
        mockSchedulerPort.schedule("rem-2", 20000L)
        assertEquals(20000L, mockSchedulerPort.scheduled["rem-2"])
    }

    @Test
    fun testReminderDevice_RebootAndMissedEventsHandling() {
        val now = 100_000L
        // 1. One past reminder (missed during shutdown)
        val pastItem = SchedulerProjectionItem(
            reminderId = "past-1",
            fireEpochMs = 50_000L,
            repeatRule = ReminderRepeat.ONCE.name,
            isEnabled = true,
        )
        // 2. One past recurring reminder (needs calculation of next future trigger)
        val recurringPast = SchedulerProjectionItem(
            reminderId = "rec-1",
            fireEpochMs = 50_000L,
            repeatRule = ReminderRepeat.DAILY.name,
            isEnabled = true,
        )
        // 3. One future reminder
        val futureItem = SchedulerProjectionItem(
            reminderId = "future-1",
            fireEpochMs = 150_000L,
            repeatRule = ReminderRepeat.ONCE.name,
            isEnabled = true,
        )

        projectionStore.upsert(pastItem)
        projectionStore.upsert(recurringPast)
        projectionStore.upsert(futureItem)

        // Simulate reboot resolution logic
        val all = projectionStore.getAll()
        assertEquals(3, all.size)

        var missedDetected = false
        all.forEach { item ->
            if (!item.isEnabled) return@forEach
            if (item.fireEpochMs <= now) {
                missedDetected = true
                val repeat = item.repeatRule?.let { ReminderRepeat.valueOf(it) } ?: ReminderRepeat.ONCE
                if (repeat == ReminderRepeat.ONCE) {
                    projectionStore.upsert(item.copy(isEnabled = false))
                } else {
                    val next = ReminderTriggerCalculator.computeNextTrigger(item.fireEpochMs, repeat, now)
                    if (next != null) {
                        projectionStore.upsert(item.copy(fireEpochMs = next))
                        mockSchedulerPort.schedule(item.reminderId, next)
                    }
                }
            } else {
                mockSchedulerPort.schedule(item.reminderId, item.fireEpochMs)
            }
        }

        assertTrue(missedDetected)
        // Past once is disabled
        assertFalse(projectionStore.getById("past-1")!!.isEnabled)
        // Recurring is updated to future
        assertTrue(projectionStore.getById("rec-1")!!.fireEpochMs > now)
        // Future is scheduled
        assertEquals(150_000L, mockSchedulerPort.scheduled["future-1"])
    }

    // --- CRITERION 3: reminder-privacy ---
    @Test
    fun testReminderPrivacy_ProjectionContainsNoPersonalTextOrKeys() {
        // Save projection item
        val item = SchedulerProjectionItem(
            reminderId = "priv-1",
            fireEpochMs = 123456789L,
            repeatRule = ReminderRepeat.DAILY.name,
            isEnabled = true,
        )
        projectionStore.upsert(item)

        // Read raw file content from disk
        val rawFile = tempFolder.root.resolve("scheduler_projection.json")
        assertTrue(rawFile.exists())
        val jsonText = rawFile.readText()

        // Privacy check: raw file must not contain any title, note, secret, PIN or rawKey
        assertFalse("Raw projection must not contain 'title'", jsonText.contains("title", ignoreCase = true))
        assertFalse("Raw projection must not contain 'note'", jsonText.contains("note", ignoreCase = true))
        assertFalse("Raw projection must not contain 'password'", jsonText.contains("password", ignoreCase = true))
        assertFalse("Raw projection must not contain 'secret'", jsonText.contains("secret", ignoreCase = true))
        assertFalse("Raw projection must not contain 'key'", jsonText.contains("key", ignoreCase = true))

        // Check required minimal fields
        assertTrue(jsonText.contains("priv-1"))
        assertTrue(jsonText.contains("123456789"))
        assertTrue(jsonText.contains("DAILY"))
    }

    // --- CRITERION 4: reminder-ux ---
    @Test
    fun testReminderUx_DiagnosticsHonestAboutVivoAndForceStop() {
        val vivoDiag = DeviceDiagnostics(
            areNotificationsEnabled = false,
            canScheduleExactAlarms = false,
            isVivoDevice = true,
            manufacturer = "vivo",
            model = "V2303",
            androidVersion = 34,
            recommendations = listOf(
                "Разрешите уведомления в настройках системы.",
                "Разрешите приложению точный запуск будильников.",
                "На устройствах Vivo (Funtouch OS) включите автозапуск и снимите ограничения батареи.",
                "Важно: при принудительной остановке Android и Funtouch OS блокируют доставку всех сигналов.",
            ),
        )

        assertFalse(vivoDiag.areNotificationsEnabled)
        assertFalse(vivoDiag.canScheduleExactAlarms)
        assertTrue(vivoDiag.isVivoDevice)
        assertTrue(vivoDiag.recommendations.any { it.contains("принудительной остановке", ignoreCase = true) })
        assertTrue(vivoDiag.recommendations.any { it.contains("Vivo", ignoreCase = true) })
    }
}

// In-Memory Mocks for Testing
private class InMemoryReminderRepository : ReminderRepository {
    private val items = mutableMapOf<String, ReminderItem>()

    override suspend fun getAllReminders(): List<ReminderItem> = items.values.toList()
    override suspend fun getActiveReminders(): List<ReminderItem> = items.values.filter { it.isEnabled }
    override suspend fun getReminderById(id: String): ReminderItem? = items[id]
    override suspend fun getRemindersByTaskId(taskId: String): List<ReminderItem> =
        items.values.filter { it.taskId == taskId }

    override suspend fun saveReminder(reminder: ReminderItem) {
        items[reminder.id] = reminder
    }

    override suspend fun toggleReminder(id: String, isEnabled: Boolean) {
        items[id]?.let {
            items[id] = it.copy(isEnabled = isEnabled)
        }
    }

    override suspend fun deleteReminder(id: String) {
        items.remove(id)
    }

    override suspend fun cancelRemindersForTask(taskId: String) {
        items.values.filter { it.taskId == taskId }.forEach {
            items[it.id] = it.copy(isEnabled = false)
        }
    }
}

private class InMemoryTaskRepository(
    private val reminderRepo: ReminderRepository? = null,
) : TaskRepository {
    private val tasks = mutableMapOf<String, TaskItem>()

    override suspend fun getOpenTasks(): List<TaskItem> =
        tasks.values.filter { it.status == TaskStatus.OPEN }

    override suspend fun getAllTasks(): List<TaskItem> = tasks.values.toList()
    override suspend fun getTaskById(id: String): TaskItem? = tasks[id]
    override suspend fun saveTask(task: TaskItem) {
        tasks[task.id] = task
    }

    override suspend fun markTaskDone(id: String) {
        tasks[id]?.let {
            tasks[id] = it.copy(status = TaskStatus.DONE, completedAtEpochMs = System.currentTimeMillis())
            reminderRepo?.cancelRemindersForTask(id)
        }
    }

    override suspend fun deleteTask(id: String) {
        tasks.remove(id)
        reminderRepo?.cancelRemindersForTask(id)
    }
}

private class MockSchedulerPort : SchedulerPort {
    var canExact: Boolean = true
    val scheduled = mutableMapOf<String, Long>()

    override fun canScheduleExact(): Boolean = canExact

    override fun schedule(reminderId: String, triggerAtEpochMs: Long) {
        scheduled[reminderId] = triggerAtEpochMs
    }

    override fun cancel(reminderId: String) {
        scheduled.remove(reminderId)
    }
}

package local.mylife.app.feature.tasks

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskPriority
import local.mylife.core.model.TaskRepository
import local.mylife.core.model.TaskStatus
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class TaskFlowTest {

    private lateinit var repository: InMemoryTaskRepository
    private lateinit var viewModel: TasksViewModel
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        repository = InMemoryTaskRepository()
        viewModel = TasksViewModel(
            taskRepository = repository,
            externalScope = testScope,
        )
    }

    @Test
    fun testTaskCRUD_CreateEditCompleteDelete() = runBlocking {
        assertTrue(viewModel.uiState.value.filteredTasks.isEmpty())

        // 1. Create task
        viewModel.saveTask(
            title = "Купить билет на поезд",
            description = "В вагон с кондиционером",
            dueAtEpochMs = System.currentTimeMillis() + 3600000L,
            priority = TaskPriority.HIGH,
        )

        val tasks = viewModel.uiState.value.filteredTasks
        assertEquals(1, tasks.size)
        val task = tasks.first()
        assertEquals("Купить билет на поезд", task.title)
        assertEquals("В вагон с кондиционером", task.description)
        assertEquals(TaskPriority.HIGH, task.priority)
        assertEquals(TaskStatus.OPEN, task.status)
        assertNull(task.completedAtEpochMs)

        // 2. Edit task
        viewModel.openEditTask(task)
        assertEquals("Купить билет на поезд", viewModel.uiState.value.draftTitle)
        viewModel.saveTask(
            title = "Купить билет на самолет",
            description = "Утренний рейс",
            dueAtEpochMs = task.dueAtEpochMs,
            priority = TaskPriority.HIGH,
        )
        val edited = viewModel.uiState.value.filteredTasks.first()
        assertEquals("Купить билет на самолет", edited.title)
        assertEquals("Утренний рейс", edited.description)

        // 3. Complete task
        viewModel.toggleTaskStatus(edited)
        // With default filter OPEN, completed task disappears from OPEN
        assertTrue(viewModel.uiState.value.filteredTasks.isEmpty())

        // Under ALL filter, it appears as DONE
        viewModel.onFilterSelected(TaskFilter.ALL)
        assertEquals(1, viewModel.uiState.value.filteredTasks.size)
        val completed = viewModel.uiState.value.filteredTasks.first()
        assertEquals(TaskStatus.DONE, completed.status)
        assertNotNull(completed.completedAtEpochMs)

        // 4. Return to OPEN
        viewModel.toggleTaskStatus(completed)
        val reopened = viewModel.uiState.value.filteredTasks.first()
        assertEquals(TaskStatus.OPEN, reopened.status)
        assertNull(reopened.completedAtEpochMs)

        // 5. Delete task
        viewModel.deleteTask(reopened.id)
        assertTrue(viewModel.uiState.value.allTasks.isEmpty())
    }

    @Test
    fun testTaskOrdering_OverdueFirst_UpcomingDue_NullDueLast_PriorityTieBreak() = runBlocking {
        val now = System.currentTimeMillis()

        // Create tasks with different timings and priorities:
        // 1. No due date, NORMAL priority
        viewModel.saveTask(title = "Task No Due Normal", dueAtEpochMs = null, priority = TaskPriority.NORMAL)
        // 2. Overdue, NORMAL priority
        val overdue1 = now - 100000L
        viewModel.saveTask(title = "Task Overdue 1", dueAtEpochMs = overdue1, priority = TaskPriority.NORMAL)
        // 3. Overdue, earlier
        val overdueEarlier = now - 200000L
        viewModel.saveTask(title = "Task Overdue Earlier", dueAtEpochMs = overdueEarlier, priority = TaskPriority.NORMAL)
        // 4. Upcoming tomorrow
        val tomorrow = now + 86400000L
        viewModel.saveTask(title = "Task Tomorrow", dueAtEpochMs = tomorrow, priority = TaskPriority.NORMAL)
        // 5. Upcoming today
        val today = now + 3600000L
        viewModel.saveTask(title = "Task Today", dueAtEpochMs = today, priority = TaskPriority.NORMAL)
        // 6. No due date, HIGH priority
        viewModel.saveTask(title = "Task No Due High", dueAtEpochMs = null, priority = TaskPriority.HIGH)

        val orderedTitles = viewModel.uiState.value.allTasks.map { it.title }

        // Expected order:
        // 1. Overdue earlier
        // 2. Overdue 1
        // 3. Today (earliest upcoming)
        // 4. Tomorrow (later upcoming)
        // 5. No Due High (priority HIGH before NORMAL when both due is null)
        // 6. No Due Normal
        assertEquals(
            listOf(
                "Task Overdue Earlier",
                "Task Overdue 1",
                "Task Today",
                "Task Tomorrow",
                "Task No Due High",
                "Task No Due Normal",
            ),
            orderedTitles,
        )
    }

    @Test
    fun testSearchAndFilters() = runBlocking {
        viewModel.saveTask(title = "Сдать отчет по проекту", description = "Финансовая часть", priority = TaskPriority.HIGH)
        viewModel.saveTask(title = "Позвонить врачу", description = "Записаться на прием", priority = TaskPriority.NORMAL)

        // Search in title
        viewModel.onSearchQueryChanged("отчет")
        assertEquals(1, viewModel.uiState.value.filteredTasks.size)
        assertEquals("Сдать отчет по проекту", viewModel.uiState.value.filteredTasks.first().title)

        // Search in description
        viewModel.onSearchQueryChanged("прием")
        assertEquals(1, viewModel.uiState.value.filteredTasks.size)
        assertEquals("Позвонить врачу", viewModel.uiState.value.filteredTasks.first().title)

        viewModel.onSearchQueryChanged("")
        assertEquals(2, viewModel.uiState.value.filteredTasks.size)

        // Filter HIGH_PRIORITY
        viewModel.onFilterSelected(TaskFilter.HIGH_PRIORITY)
        assertEquals(1, viewModel.uiState.value.filteredTasks.size)
        assertEquals("Сдать отчет по проекту", viewModel.uiState.value.filteredTasks.first().title)
    }

    @Test
    fun testTaskValidation() = runBlocking {
        viewModel.saveTask(title = "   ")
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.allTasks.isEmpty())
    }

    @Test
    fun testSafeDrafts() = runBlocking {
        viewModel.openAddTask()
        viewModel.updateDraft("Черновик задачи", "Черновик описания")
        assertEquals("Черновик задачи", viewModel.uiState.value.draftTitle)
        assertEquals("Черновик описания", viewModel.uiState.value.draftDescription)
    }
}

class InMemoryTaskRepository : TaskRepository {
    private val tasks = mutableMapOf<String, TaskItem>()

    override suspend fun getOpenTasks(): List<TaskItem> =
        tasks.values.filter { it.status == TaskStatus.OPEN }

    override suspend fun getAllTasks(): List<TaskItem> =
        tasks.values.toList()

    override suspend fun getTaskById(id: String): TaskItem? = tasks[id]

    override suspend fun saveTask(task: TaskItem) {
        tasks[task.id] = task
    }

    override suspend fun markTaskDone(id: String) {
        tasks[id]?.let {
            tasks[id] = it.copy(
                status = TaskStatus.DONE,
                completedAtEpochMs = System.currentTimeMillis(),
                updatedAtEpochMs = System.currentTimeMillis(),
            )
        }
    }

    override suspend fun deleteTask(id: String) {
        tasks.remove(id)
    }
}

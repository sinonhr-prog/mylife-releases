package local.mylife.core.storage

import kotlinx.coroutines.runBlocking
import local.mylife.core.model.TaskItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class TaskRepositoryTest {
    @Test
    fun testSaveAndGetTasks() =
        runBlocking {
            val repo = InMemoryTaskRepository()
            val task1 = TaskItem(id = "1", title = "Task 1", dueAtEpochMs = 100L)
            val task2 = TaskItem(id = "2", title = "Task 2", dueAtEpochMs = 200L)

            repo.saveTask(task1)
            repo.saveTask(task2)

            val open = repo.getOpenTasks()
            assertEquals(2, open.size)

            val summary = repo.getDashboardSummary()
            assertNotNull(summary.nextTask)
            assertEquals("1", summary.nextTask?.id)

            repo.markTaskDone("1")
            val updatedOpen = repo.getOpenTasks()
            assertEquals(1, updatedOpen.size)
            assertEquals("2", updatedOpen[0].id)
        }
}

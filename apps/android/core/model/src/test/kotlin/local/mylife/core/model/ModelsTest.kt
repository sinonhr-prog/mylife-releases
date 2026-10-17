package local.mylife.core.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

class ModelsTest {
    @Test
    fun testTaskItemCreationAndDefaults() {
        val task =
            TaskItem(
                id = "task-1",
                title = "Тестовая задача",
                priority = TaskPriority.HIGH,
            )

        assertEquals("task-1", task.id)
        assertEquals("Тестовая задача", task.title)
        assertEquals(TaskStatus.OPEN, task.status)
        assertEquals(TaskPriority.HIGH, task.priority)
    }

    @Test
    fun testDashboardSummaryCreation() {
        val task = TaskItem(id = "next-1", title = "Ближайшее дело")
        val summary =
            DashboardSummary(
                nextTask = task,
                openTasksCount = 5,
                remindersCount = 2,
            )

        assertNotNull(summary.nextTask)
        assertEquals("next-1", summary.nextTask?.id)
        assertEquals(5, summary.openTasksCount)
        assertEquals(2, summary.remindersCount)
    }
}

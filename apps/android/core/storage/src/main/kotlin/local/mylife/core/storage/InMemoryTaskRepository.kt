package local.mylife.core.storage

import local.mylife.core.model.DashboardRepository
import local.mylife.core.model.DashboardSummary
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskRepository
import local.mylife.core.model.TaskStatus

class InMemoryTaskRepository : TaskRepository, DashboardRepository {
    private val tasks = mutableMapOf<String, TaskItem>()

    override suspend fun getOpenTasks(): List<TaskItem> {
        return tasks.values.filter { it.status == TaskStatus.OPEN }
    }

    override suspend fun getAllTasks(): List<TaskItem> {
        return tasks.values.toList()
    }

    override suspend fun getTaskById(id: String): TaskItem? {
        return tasks[id]
    }

    override suspend fun saveTask(task: TaskItem) {
        tasks[task.id] = task
    }

    override suspend fun markTaskDone(id: String) {
        val existing = tasks[id] ?: return
        tasks[id] = existing.copy(status = TaskStatus.DONE)
    }

    override suspend fun deleteTask(id: String) {
        tasks.remove(id)
    }

    override suspend fun getDashboardSummary(): DashboardSummary {
        val open = getOpenTasks()
        val next = open.minByOrNull { it.dueAtEpochMs ?: Long.MAX_VALUE }
        return DashboardSummary(
            focusTask = next,
            nextTask = next,
            openTasksCount = open.size,
        )
    }
}

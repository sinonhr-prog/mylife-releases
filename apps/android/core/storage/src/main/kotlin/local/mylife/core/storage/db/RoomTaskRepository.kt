package local.mylife.core.storage.db

import local.mylife.core.model.DashboardRepository
import local.mylife.core.model.DashboardSummary
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskRepository
import local.mylife.core.model.TaskStatus

class RoomTaskRepository(
    private val taskDao: TaskDao,
) : TaskRepository, DashboardRepository {
    override suspend fun getOpenTasks(): List<TaskItem> {
        return taskDao.getOpenTasks().map { it.toDomain() }
    }

    override suspend fun getTaskById(id: String): TaskItem? {
        return taskDao.getTaskById(id)?.toDomain()
    }

    override suspend fun saveTask(task: TaskItem) {
        taskDao.insertTask(TaskEntity.fromDomain(task))
    }

    override suspend fun markTaskDone(id: String) {
        taskDao.updateTaskStatus(id, TaskStatus.DONE.name, System.currentTimeMillis())
    }

    override suspend fun getDashboardSummary(): DashboardSummary {
        val openTasks = getOpenTasks()
        val next = openTasks.minByOrNull { it.dueAtEpochMs ?: Long.MAX_VALUE }
        return DashboardSummary(
            nextTask = next,
            openTasksCount = openTasks.size,
        )
    }
}

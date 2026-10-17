package local.mylife.core.storage.db

import local.mylife.core.model.DashboardRepository
import local.mylife.core.model.DashboardSummary
import local.mylife.core.model.ReminderRepository
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskRepository
import local.mylife.core.model.TaskStatus

class RoomTaskRepository(
    private val taskDao: TaskDao,
    private val reminderRepository: ReminderRepository? = null,
) : TaskRepository, DashboardRepository {
    override suspend fun getOpenTasks(): List<TaskItem> {
        return taskDao.getTasksByStatus(TaskStatus.OPEN).map { it.toDomain() }
    }

    override suspend fun getAllTasks(): List<TaskItem> {
        return taskDao.getAllTasks().map { it.toDomain() }
    }

    override suspend fun getTaskById(id: String): TaskItem? {
        return taskDao.getById(id)?.toDomain()
    }

    override suspend fun saveTask(task: TaskItem) {
        taskDao.insert(task.toEntity())
    }

    override suspend fun markTaskDone(id: String) {
        val now = System.currentTimeMillis()
        taskDao.updateStatus(id, TaskStatus.DONE, now, now)
        reminderRepository?.cancelRemindersForTask(id)
    }

    override suspend fun deleteTask(id: String) {
        taskDao.deleteById(id)
        reminderRepository?.cancelRemindersForTask(id)
    }

    override suspend fun getDashboardSummary(): DashboardSummary {
        val openTasks = getOpenTasks()
        val next = openTasks.minByOrNull { it.dueAtEpochMs ?: Long.MAX_VALUE }
        return DashboardSummary(
            focusTask = next,
            nextTask = next,
            openTasksCount = openTasks.size,
        )
    }

    private fun TaskEntity.toDomain() = TaskItem(
        id = id,
        title = title,
        description = description,
        dueAtEpochMs = dueAtEpochMs,
        status = status,
        priority = priority,
        completedAtEpochMs = completedAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun TaskItem.toEntity() = TaskEntity(
        id = id,
        title = title,
        description = description,
        dueAtEpochMs = dueAtEpochMs,
        status = status,
        priority = priority,
        completedAtEpochMs = completedAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

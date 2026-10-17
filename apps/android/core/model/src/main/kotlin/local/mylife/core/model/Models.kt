package local.mylife.core.model

import kotlinx.serialization.Serializable

@Serializable
enum class TaskStatus {
    OPEN,
    DONE,
}

@Serializable
enum class TaskPriority {
    LOW,
    NORMAL,
    HIGH,
}

@Serializable
data class TaskItem(
    val id: String,
    val title: String,
    val description: String = "",
    val dueAtEpochMs: Long? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

data class DashboardSummary(
    val nextTask: TaskItem? = null,
    val openTasksCount: Int = 0,
    val remindersCount: Int = 0,
    val recentPlacesCount: Int = 0,
    val recentPurchasesCount: Int = 0,
)

interface TaskRepository {
    suspend fun getOpenTasks(): List<TaskItem>

    suspend fun getTaskById(id: String): TaskItem?

    suspend fun saveTask(task: TaskItem)

    suspend fun markTaskDone(id: String)
}

interface DashboardRepository {
    suspend fun getDashboardSummary(): DashboardSummary
}

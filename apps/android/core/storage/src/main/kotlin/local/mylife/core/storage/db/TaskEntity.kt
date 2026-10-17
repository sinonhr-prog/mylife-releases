package local.mylife.core.storage.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskPriority
import local.mylife.core.model.TaskStatus

@Entity(tableName = "tasks")
data class TaskEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: String,
    @ColumnInfo(name = "title")
    val title: String,
    @ColumnInfo(name = "description")
    val description: String = "",
    @ColumnInfo(name = "due_at_epoch_ms")
    val dueAtEpochMs: Long? = null,
    @ColumnInfo(name = "status")
    val status: String = TaskStatus.OPEN.name,
    @ColumnInfo(name = "priority")
    val priority: String = TaskPriority.NORMAL.name,
    @ColumnInfo(name = "created_at_epoch_ms")
    val createdAtEpochMs: Long = 0L,
    @ColumnInfo(name = "updated_at_epoch_ms")
    val updatedAtEpochMs: Long = 0L,
) {
    fun toDomain(): TaskItem {
        return TaskItem(
            id = id,
            title = title,
            description = description,
            dueAtEpochMs = dueAtEpochMs,
            status = runCatching { TaskStatus.valueOf(status) }.getOrDefault(TaskStatus.OPEN),
            priority = runCatching { TaskPriority.valueOf(priority) }.getOrDefault(TaskPriority.NORMAL),
            createdAtEpochMs = createdAtEpochMs,
            updatedAtEpochMs = updatedAtEpochMs,
        )
    }

    companion object {
        fun fromDomain(item: TaskItem): TaskEntity {
            return TaskEntity(
                id = item.id,
                title = item.title,
                description = item.description,
                dueAtEpochMs = item.dueAtEpochMs,
                status = item.status.name,
                priority = item.priority.name,
                createdAtEpochMs = item.createdAtEpochMs,
                updatedAtEpochMs = item.updatedAtEpochMs,
            )
        }
    }
}

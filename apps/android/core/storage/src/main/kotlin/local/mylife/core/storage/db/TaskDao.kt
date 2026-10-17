package local.mylife.core.storage.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE status = 'OPEN' ORDER BY due_at_epoch_ms ASC")
    suspend fun getOpenTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getTaskById(id: String): TaskEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTask(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, updated_at_epoch_ms = :updatedAt WHERE id = :id")
    suspend fun updateTaskStatus(
        id: String,
        status: String,
        updatedAt: Long,
    )

    @Query("SELECT count(*) FROM tasks WHERE status = 'OPEN'")
    suspend fun countOpenTasks(): Int

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteTask(id: String)
}

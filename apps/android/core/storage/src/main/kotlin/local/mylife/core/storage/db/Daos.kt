package local.mylife.core.storage.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import local.mylife.core.model.CategoryKind
import local.mylife.core.model.TaskStatus

@Dao
interface CategoryDao {
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, name ASC")
    suspend fun getAll(): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE kind = :kind ORDER BY sortOrder ASC, name ASC")
    suspend fun getByKind(kind: CategoryKind): List<CategoryEntity>

    @Query("SELECT * FROM categories WHERE id = :id")
    suspend fun getById(id: String): CategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: CategoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertAll(categories: List<CategoryEntity>)

    @Delete
    suspend fun delete(category: CategoryEntity)

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ProductDao {
    @Query("SELECT * FROM products WHERE (:categoryId IS NULL OR categoryId = :categoryId) AND (:includeArchived = 1 OR archivedAtEpochMs IS NULL) ORDER BY isFavorite DESC, updatedAtEpochMs DESC")
    suspend fun getProducts(categoryId: String?, includeArchived: Boolean): List<ProductEntity>

    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: String): ProductEntity?

    @Query("SELECT COUNT(*) FROM products WHERE archivedAtEpochMs IS NULL")
    suspend fun getActiveCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity)

    @Delete
    suspend fun delete(product: ProductEntity)

    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE products SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun updateCategory(oldCategoryId: String, newCategoryId: String)

    @Query("SELECT a.* FROM attachments a INNER JOIN product_attachments pa ON a.id = pa.attachmentId WHERE pa.productId = :productId ORDER BY pa.position ASC")
    suspend fun getAttachmentsForProduct(productId: String): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProductAttachmentCrossRef(crossRef: ProductAttachmentCrossRef)

    @Query("DELETE FROM product_attachments WHERE productId = :productId AND attachmentId = :attachmentId")
    suspend fun deleteProductAttachmentCrossRef(productId: String, attachmentId: String)
}

@Dao
interface PlaceDao {
    @Query("SELECT * FROM places WHERE (:categoryId IS NULL OR categoryId = :categoryId) ORDER BY isFavorite DESC, lastVisitedAtEpochMs DESC, updatedAtEpochMs DESC")
    suspend fun getPlaces(categoryId: String?): List<PlaceEntity>

    @Query("SELECT * FROM places WHERE id = :id")
    suspend fun getById(id: String): PlaceEntity?

    @Query("SELECT COUNT(*) FROM places")
    suspend fun getCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(place: PlaceEntity)

    @Delete
    suspend fun delete(place: PlaceEntity)

    @Query("DELETE FROM places WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("UPDATE places SET categoryId = :newCategoryId WHERE categoryId = :oldCategoryId")
    suspend fun updateCategory(oldCategoryId: String, newCategoryId: String)

    @Query("SELECT a.* FROM attachments a INNER JOIN place_attachments pa ON a.id = pa.attachmentId WHERE pa.placeId = :placeId ORDER BY pa.position ASC")
    suspend fun getAttachmentsForPlace(placeId: String): List<AttachmentEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaceAttachmentCrossRef(crossRef: PlaceAttachmentCrossRef)

    @Query("DELETE FROM place_attachments WHERE placeId = :placeId AND attachmentId = :attachmentId")
    suspend fun deletePlaceAttachmentCrossRef(placeId: String, attachmentId: String)
}

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes ORDER BY isFavorite DESC, updatedAtEpochMs DESC")
    suspend fun getAll(): List<NoteEntity>

    @Query("SELECT * FROM notes WHERE id = :id")
    suspend fun getById(id: String): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(note: NoteEntity)

    @Delete
    suspend fun delete(note: NoteEntity)

    @Query("DELETE FROM notes WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface TaskDao {
    @Query("SELECT * FROM tasks WHERE status = :status ORDER BY CASE WHEN dueAtEpochMs IS NULL THEN 1 ELSE 0 END, dueAtEpochMs ASC, createdAtEpochMs ASC")
    suspend fun getTasksByStatus(status: TaskStatus): List<TaskEntity>

    @Query("SELECT * FROM tasks ORDER BY status ASC, CASE WHEN dueAtEpochMs IS NULL THEN 1 ELSE 0 END, dueAtEpochMs ASC")
    suspend fun getAllTasks(): List<TaskEntity>

    @Query("SELECT * FROM tasks WHERE id = :id")
    suspend fun getById(id: String): TaskEntity?

    @Query("SELECT COUNT(*) FROM tasks WHERE status = 'OPEN'")
    suspend fun getOpenCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(task: TaskEntity)

    @Query("UPDATE tasks SET status = :status, completedAtEpochMs = :completedAtEpochMs, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun updateStatus(id: String, status: TaskStatus, completedAtEpochMs: Long?, updatedAtEpochMs: Long)

    @Delete
    suspend fun delete(task: TaskEntity)

    @Query("DELETE FROM tasks WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ReminderDao {
    @Query("SELECT * FROM reminders ORDER BY isEnabled DESC, fireEpochMs ASC")
    suspend fun getAll(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE isEnabled = 1 ORDER BY fireEpochMs ASC")
    suspend fun getActiveReminders(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id")
    suspend fun getById(id: String): ReminderEntity?

    @Query("SELECT * FROM reminders WHERE taskId = :taskId")
    suspend fun getByTaskId(taskId: String): List<ReminderEntity>

    @Query("SELECT COUNT(*) FROM reminders WHERE isEnabled = 1")
    suspend fun getActiveCount(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(reminder: ReminderEntity)

    @Query("UPDATE reminders SET isEnabled = :isEnabled, updatedAtEpochMs = :updatedAtEpochMs WHERE id = :id")
    suspend fun updateEnabled(id: String, isEnabled: Boolean, updatedAtEpochMs: Long)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface ScheduleOutboxDao {
    @Query("SELECT * FROM schedule_outbox WHERE processedAtEpochMs IS NULL ORDER BY revision ASC")
    suspend fun getPending(): List<ScheduleOutboxEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: ScheduleOutboxEntity)

    @Query("UPDATE schedule_outbox SET processedAtEpochMs = :processedAtEpochMs WHERE id = :id")
    suspend fun markProcessed(id: String, processedAtEpochMs: Long)

    @Query("DELETE FROM schedule_outbox WHERE processedAtEpochMs IS NOT NULL AND processedAtEpochMs < :thresholdEpochMs")
    suspend fun deleteProcessedBefore(thresholdEpochMs: Long)

    @Query("DELETE FROM schedule_outbox WHERE reminderId = :reminderId")
    suspend fun deleteForReminder(reminderId: String)
}

@Dao
interface AttachmentDao {
    @Query("SELECT * FROM attachments WHERE id = :id")
    suspend fun getById(id: String): AttachmentEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(attachment: AttachmentEntity)

    @Delete
    suspend fun delete(attachment: AttachmentEntity)

    @Query("DELETE FROM attachments WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface VaultDao {
    @Query("SELECT * FROM vault_entries ORDER BY title ASC")
    suspend fun getAll(): List<VaultEntryEntity>

    @Query("SELECT * FROM vault_entries WHERE id = :id")
    suspend fun getById(id: String): VaultEntryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entry: VaultEntryEntity)

    @Delete
    suspend fun delete(entry: VaultEntryEntity)

    @Query("DELETE FROM vault_entries WHERE id = :id")
    suspend fun deleteById(id: String)
}

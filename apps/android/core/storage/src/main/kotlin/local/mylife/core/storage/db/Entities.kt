package local.mylife.core.storage.db

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import local.mylife.core.model.CategoryKind
import local.mylife.core.model.PlaceVisitStatus
import local.mylife.core.model.TaskPriority
import local.mylife.core.model.TaskStatus

@Entity(
    tableName = "categories",
    indices = [Index("kind"), Index("sortOrder")],
)
data class CategoryEntity(
    @PrimaryKey val id: String,
    val name: String,
    val kind: CategoryKind,
    val iconKey: String = "default",
    val sortOrder: Int = 0,
)

@Entity(
    tableName = "products",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId"), Index("rating"), Index("isFavorite")],
)
data class ProductEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val title: String,
    val description: String = "",
    val purchaseUrl: String? = null,
    val rating: Int? = null,
    val purchasedOnEpochDay: Long? = null,
    val priceMinor: Long? = null,
    val currency: String? = null,
    val isFavorite: Boolean = false,
    val archivedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

@Entity(
    tableName = "places",
    foreignKeys = [
        ForeignKey(
            entity = CategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.RESTRICT,
        ),
    ],
    indices = [Index("categoryId"), Index("lastVisitedAtEpochMs"), Index("rating"), Index("isFavorite")],
)
data class PlaceEntity(
    @PrimaryKey val id: String,
    val categoryId: String,
    val name: String,
    val addressText: String = "",
    val description: String = "",
    val visitStatus: PlaceVisitStatus = PlaceVisitStatus.WANT,
    val lastVisitedAtEpochMs: Long? = null,
    val isFavorite: Boolean = false,
    val rating: Int? = null,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

@Entity(
    tableName = "notes",
    indices = [Index("isFavorite"), Index("createdAtEpochMs")],
)
data class NoteEntity(
    @PrimaryKey val id: String,
    val title: String,
    val body: String = "",
    val isFavorite: Boolean = false,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

@Entity(
    tableName = "tasks",
    indices = [Index("status"), Index("dueAtEpochMs"), Index("priority")],
)
data class TaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val dueAtEpochMs: Long? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val completedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = TaskEntity::class,
            parentColumns = ["id"],
            childColumns = ["taskId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [Index("taskId"), Index("fireEpochMs"), Index("isEnabled")],
)
data class ReminderEntity(
    @PrimaryKey val id: String,
    val taskId: String? = null,
    val title: String,
    val fireEpochMs: Long,
    val repeatRule: String? = null,
    val isEnabled: Boolean = true,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

@Entity(
    tableName = "attachments",
    indices = [Index("mimeType")],
)
data class AttachmentEntity(
    @PrimaryKey val id: String,
    val encryptedRelativePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val encryptionFormatVersion: Int = 1,
    val thumbnailRelativePath: String? = null,
)

@Entity(
    tableName = "product_attachments",
    primaryKeys = ["productId", "attachmentId"],
    foreignKeys = [
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["productId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AttachmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["attachmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("attachmentId")],
)
data class ProductAttachmentCrossRef(
    val productId: String,
    val attachmentId: String,
    val position: Int = 0,
)

@Entity(
    tableName = "place_attachments",
    primaryKeys = ["placeId", "attachmentId"],
    foreignKeys = [
        ForeignKey(
            entity = PlaceEntity::class,
            parentColumns = ["id"],
            childColumns = ["placeId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = AttachmentEntity::class,
            parentColumns = ["id"],
            childColumns = ["attachmentId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("attachmentId")],
)
data class PlaceAttachmentCrossRef(
    val placeId: String,
    val attachmentId: String,
    val position: Int = 0,
)

@Entity(
    tableName = "schedule_outbox",
    indices = [Index("processedAtEpochMs")],
)
data class ScheduleOutboxEntity(
    @PrimaryKey val id: String,
    val reminderId: String,
    val revision: Long,
    val operation: String, // "UPSERT" or "CANCEL"
    val processedAtEpochMs: Long? = null,
)

@Entity(
    tableName = "vault_entries",
    indices = [Index("title"), Index("createdAtEpochMs")],
)
data class VaultEntryEntity(
    @PrimaryKey val id: String,
    val title: String,
    val website: String = "",
    val username: String = "",
    val passwordCiphertext: String = "",
    val secretNoteCiphertext: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
)

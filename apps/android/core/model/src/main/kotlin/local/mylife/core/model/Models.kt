package local.mylife.core.model

import kotlinx.serialization.Serializable

// --- Enums ---

@Serializable
enum class CategoryKind {
    PRODUCT,
    PLACE,
    NOTE,
    TASK,
}

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
enum class PlaceVisitStatus {
    WANT,
    VISITED,
}

// --- Domain Models ---

@Serializable
data class Category(
    val id: String,
    val name: String,
    val kind: CategoryKind,
    val iconKey: String = "default",
    val sortOrder: Int = 0,
)

@Serializable
data class Product(
    val id: String,
    val categoryId: String,
    val title: String,
    val description: String = "",
    val purchaseUrl: String? = null,
    val rating: Int? = null, // 1..10
    val purchasedOnEpochDay: Long? = null,
    val priceMinor: Long? = null, // In cents/kopecks, non-negative
    val currency: String? = null, // ISO-4217 code (RUB, USD, etc.)
    val isFavorite: Boolean = false,
    val archivedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
) {
    init {
        require(title.isNotBlank() && title.length <= 160) { "Product title must be 1..160 chars" }
        require(description.length <= 20000) { "Description must be <= 20000 chars" }
        purchaseUrl?.let {
            require(it.length <= 2048) { "URL must be <= 2048 chars" }
            require(it.startsWith("http://") || it.startsWith("https://")) { "Only http/https URLs allowed" }
        }
        rating?.let { require(it in 1..10) { "Rating must be between 1 and 10" } }
        priceMinor?.let {
            require(it >= 0) { "Price cannot be negative" }
            require(!currency.isNullOrBlank()) { "Currency required when price is set" }
        }
    }
}

@Serializable
data class Place(
    val id: String,
    val categoryId: String,
    val name: String,
    val addressText: String = "",
    val description: String = "",
    val visitStatus: PlaceVisitStatus = PlaceVisitStatus.WANT,
    val lastVisitedAtEpochMs: Long? = null,
    val isFavorite: Boolean = false,
    val rating: Int? = null, // 1..10
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
) {
    init {
        require(name.isNotBlank() && name.length <= 160) { "Place name must be 1..160 chars" }
        require(description.length <= 20000) { "Description must be <= 20000 chars" }
        rating?.let { require(it in 1..10) { "Rating must be between 1 and 10" } }
    }
}

@Serializable
data class Note(
    val id: String,
    val title: String,
    val body: String = "",
    val isFavorite: Boolean = false,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
) {
    init {
        require(title.isNotBlank() && title.length <= 160) { "Note title must be 1..160 chars" }
        require(body.length <= 100000) { "Note body must be <= 100000 chars" }
    }
}

@Serializable
data class TaskItem(
    val id: String,
    val title: String,
    val description: String = "",
    val dueAtEpochMs: Long? = null,
    val status: TaskStatus = TaskStatus.OPEN,
    val priority: TaskPriority = TaskPriority.NORMAL,
    val completedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
) {
    init {
        require(title.isNotBlank() && title.length <= 160) { "Task title must be 1..160 chars" }
    }
}

@Serializable
data class ReminderItem(
    val id: String,
    val taskId: String? = null,
    val title: String,
    val fireEpochMs: Long,
    val repeatRule: String? = null,
    val isEnabled: Boolean = true,
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
) {
    init {
        require(title.isNotBlank() && title.length <= 160) { "Reminder title must be 1..160 chars" }
        require(fireEpochMs > 0) { "Fire instant must be positive" }
    }
}

@Serializable
data class AttachmentMeta(
    val id: String,
    val encryptedRelativePath: String,
    val mimeType: String,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val encryptionFormatVersion: Int = 1,
    val thumbnailRelativePath: String? = null,
)

@Serializable
data class VaultEntry(
    val id: String,
    val title: String,
    val website: String = "",
    val username: String = "",
    val password: String = "",
    val secretNote: String = "",
    val createdAtEpochMs: Long = 0L,
    val updatedAtEpochMs: Long = 0L,
) {
    init {
        require(title.isNotBlank() && title.length <= 160) { "Vault entry title must be 1..160 chars" }
    }
}

@Serializable
enum class RecentRecordType {
    PURCHASE,
    PLACE,
    NOTE,
    TASK,
}

@Serializable
data class RecentRecord(
    val id: String,
    val title: String,
    val type: RecentRecordType,
    val createdAtEpochMs: Long,
)

data class DashboardSummary(
    val focusTask: TaskItem? = null,
    val remainingTasks: List<TaskItem> = emptyList(),
    val recentVisits: List<Place> = emptyList(),
    val recentRecords: List<RecentRecord> = emptyList(),
    val activeReminders: List<ReminderItem> = emptyList(),
    val openTasksCount: Int = 0,
    val remindersCount: Int = 0,
    val recentPlacesCount: Int = 0,
    val recentPurchasesCount: Int = 0,
    val nextTask: TaskItem? = focusTask,
)

object DefaultCategories {
    val SHOPS = Category("cat_shops", "Магазины", CategoryKind.PLACE, "store", 1)
    val PARKS = Category("cat_parks", "Парки", CategoryKind.PLACE, "tree", 2)
    val CAFES = Category("cat_cafes", "Кафе", CategoryKind.PLACE, "coffee", 3)
    val RESTAURANTS = Category("cat_restaurants", "Рестораны", CategoryKind.PLACE, "meal", 4)
    val PURCHASES = Category("cat_purchases", "Покупки", CategoryKind.PRODUCT, "bag", 0)
    val DEFAULT_PURCHASES = Category("default_purchases", "Покупки", CategoryKind.PRODUCT, "bag", 0)

    val PRESET_CATEGORIES = listOf(SHOPS, PARKS, CAFES, RESTAURANTS, PURCHASES, DEFAULT_PURCHASES)
}

// --- Repository Interfaces ---

interface CategoryRepository {
    suspend fun getCategories(kind: CategoryKind? = null): List<Category>
    suspend fun getCategoryById(id: String): Category?
    suspend fun saveCategory(category: Category)
    suspend fun deleteCategory(id: String)
    suspend fun ensureDefaultCategories() {}
}

interface ProductRepository {
    suspend fun getProducts(categoryId: String? = null, includeArchived: Boolean = false): List<Product>
    suspend fun getProductById(id: String): Product?
    suspend fun saveProduct(product: Product)
    suspend fun deleteProduct(id: String)
    suspend fun getProductAttachments(productId: String): List<AttachmentMeta> = emptyList()
    suspend fun addProductAttachment(productId: String, attachment: AttachmentMeta) {}
    suspend fun updateCategoryForProducts(oldCategoryId: String, newCategoryId: String) {}
}

interface PlaceRepository {
    suspend fun getPlaces(categoryId: String? = null): List<Place>
    suspend fun getPlaceById(id: String): Place?
    suspend fun savePlace(place: Place)
    suspend fun deletePlace(id: String)
    suspend fun getPlaceAttachments(placeId: String): List<AttachmentMeta> = emptyList()
    suspend fun addPlaceAttachment(placeId: String, attachment: AttachmentMeta) {}
    suspend fun updateCategoryForPlaces(oldCategoryId: String, newCategoryId: String) {}
}

interface NoteRepository {
    suspend fun getNotes(): List<Note>
    suspend fun getNoteById(id: String): Note?
    suspend fun saveNote(note: Note)
    suspend fun deleteNote(id: String)
}

interface TaskRepository {
    suspend fun getOpenTasks(): List<TaskItem>
    suspend fun getAllTasks(): List<TaskItem>
    suspend fun getTaskById(id: String): TaskItem?
    suspend fun saveTask(task: TaskItem)
    suspend fun markTaskDone(id: String)
    suspend fun deleteTask(id: String)
}

interface ReminderRepository {
    suspend fun getActiveReminders(): List<ReminderItem>
    suspend fun getReminderById(id: String): ReminderItem?
    suspend fun saveReminder(reminder: ReminderItem)
    suspend fun deleteReminder(id: String)
}

interface VaultRepository {
    suspend fun getVaultEntries(): List<VaultEntry>
    suspend fun getVaultEntryById(id: String): VaultEntry?
    suspend fun saveVaultEntry(entry: VaultEntry)
    suspend fun deleteVaultEntry(id: String)
}

interface DashboardRepository {
    suspend fun getDashboardSummary(): DashboardSummary
}

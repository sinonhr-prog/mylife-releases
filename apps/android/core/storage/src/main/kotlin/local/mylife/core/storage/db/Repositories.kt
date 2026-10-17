package local.mylife.core.storage.db

import local.mylife.core.model.AttachmentMeta
import local.mylife.core.model.Category
import local.mylife.core.model.CategoryKind
import local.mylife.core.model.CategoryRepository
import local.mylife.core.model.DashboardRepository
import local.mylife.core.model.DashboardSummary
import local.mylife.core.model.Note
import local.mylife.core.model.NoteRepository
import local.mylife.core.model.Place
import local.mylife.core.model.PlaceRepository
import local.mylife.core.model.Product
import local.mylife.core.model.ProductRepository
import local.mylife.core.model.ReminderItem
import local.mylife.core.model.ReminderRepository
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskRepository
import local.mylife.core.model.TaskStatus
import local.mylife.core.model.VaultEntry
import local.mylife.core.model.VaultRepository

class RoomCategoryRepository(private val dao: CategoryDao) : CategoryRepository {
    override suspend fun getCategories(kind: CategoryKind?): List<Category> {
        val entities = if (kind != null) dao.getByKind(kind) else dao.getAll()
        return entities.map { it.toDomain() }
    }

    override suspend fun getCategoryById(id: String): Category? = dao.getById(id)?.toDomain()

    override suspend fun saveCategory(category: Category) = dao.insert(category.toEntity())

    override suspend fun deleteCategory(id: String) = dao.deleteById(id)

    private fun CategoryEntity.toDomain() = Category(id, name, kind, iconKey, sortOrder)
    private fun Category.toEntity() = CategoryEntity(id, name, kind, iconKey, sortOrder)
}

class RoomProductRepository(
    private val dao: ProductDao,
    private val attachmentDao: AttachmentDao? = null,
) : ProductRepository {
    override suspend fun getProducts(categoryId: String?, includeArchived: Boolean): List<Product> =
        dao.getProducts(categoryId, includeArchived).map { it.toDomain() }

    override suspend fun getProductById(id: String): Product? = dao.getById(id)?.toDomain()

    override suspend fun saveProduct(product: Product) = dao.insert(product.toEntity())

    override suspend fun deleteProduct(id: String) = dao.deleteById(id)

    override suspend fun getProductAttachments(productId: String): List<AttachmentMeta> =
        dao.getAttachmentsForProduct(productId).map { it.toDomain() }

    override suspend fun addProductAttachment(productId: String, attachment: AttachmentMeta) {
        attachmentDao?.insert(attachment.toEntity())
        dao.insertProductAttachmentCrossRef(ProductAttachmentCrossRef(productId = productId, attachmentId = attachment.id))
    }

    private fun ProductEntity.toDomain() = Product(
        id = id,
        categoryId = categoryId,
        title = title,
        description = description,
        purchaseUrl = purchaseUrl,
        rating = rating,
        purchasedOnEpochDay = purchasedOnEpochDay,
        priceMinor = priceMinor,
        currency = currency,
        isFavorite = isFavorite,
        archivedAtEpochMs = archivedAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun Product.toEntity() = ProductEntity(
        id = id,
        categoryId = categoryId,
        title = title,
        description = description,
        purchaseUrl = purchaseUrl,
        rating = rating,
        purchasedOnEpochDay = purchasedOnEpochDay,
        priceMinor = priceMinor,
        currency = currency,
        isFavorite = isFavorite,
        archivedAtEpochMs = archivedAtEpochMs,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun AttachmentEntity.toDomain() = AttachmentMeta(
        id = id,
        encryptedRelativePath = encryptedRelativePath,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        encryptionFormatVersion = encryptionFormatVersion,
        thumbnailRelativePath = thumbnailRelativePath,
    )

    private fun AttachmentMeta.toEntity() = AttachmentEntity(
        id = id,
        encryptedRelativePath = encryptedRelativePath,
        mimeType = mimeType,
        sizeBytes = sizeBytes,
        width = width,
        height = height,
        encryptionFormatVersion = encryptionFormatVersion,
        thumbnailRelativePath = thumbnailRelativePath,
    )
}

class RoomPlaceRepository(private val dao: PlaceDao) : PlaceRepository {
    override suspend fun getPlaces(categoryId: String?): List<Place> =
        dao.getPlaces(categoryId).map { it.toDomain() }

    override suspend fun getPlaceById(id: String): Place? = dao.getById(id)?.toDomain()

    override suspend fun savePlace(place: Place) = dao.insert(place.toEntity())

    override suspend fun deletePlace(id: String) = dao.deleteById(id)

    private fun PlaceEntity.toDomain() = Place(
        id = id,
        categoryId = categoryId,
        name = name,
        addressText = addressText,
        description = description,
        visitStatus = visitStatus,
        lastVisitedAtEpochMs = lastVisitedAtEpochMs,
        isFavorite = isFavorite,
        rating = rating,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun Place.toEntity() = PlaceEntity(
        id = id,
        categoryId = categoryId,
        name = name,
        addressText = addressText,
        description = description,
        visitStatus = visitStatus,
        lastVisitedAtEpochMs = lastVisitedAtEpochMs,
        isFavorite = isFavorite,
        rating = rating,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

class RoomNoteRepository(private val dao: NoteDao) : NoteRepository {
    override suspend fun getNotes(): List<Note> = dao.getAll().map { it.toDomain() }

    override suspend fun getNoteById(id: String): Note? = dao.getById(id)?.toDomain()

    override suspend fun saveNote(note: Note) = dao.insert(note.toEntity())

    override suspend fun deleteNote(id: String) = dao.deleteById(id)

    private fun NoteEntity.toDomain() = Note(id, title, body, isFavorite, createdAtEpochMs, updatedAtEpochMs)
    private fun Note.toEntity() = NoteEntity(id, title, body, isFavorite, createdAtEpochMs, updatedAtEpochMs)
}

class RoomReminderRepository(private val dao: ReminderDao) : ReminderRepository {
    override suspend fun getActiveReminders(): List<ReminderItem> = dao.getActiveReminders().map { it.toDomain() }

    override suspend fun getReminderById(id: String): ReminderItem? = dao.getById(id)?.toDomain()

    override suspend fun saveReminder(reminder: ReminderItem) = dao.insert(reminder.toEntity())

    override suspend fun deleteReminder(id: String) = dao.deleteById(id)

    private fun ReminderEntity.toDomain() = ReminderItem(
        id = id,
        taskId = taskId,
        title = title,
        fireEpochMs = fireEpochMs,
        repeatRule = repeatRule,
        isEnabled = isEnabled,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun ReminderItem.toEntity() = ReminderEntity(
        id = id,
        taskId = taskId,
        title = title,
        fireEpochMs = fireEpochMs,
        repeatRule = repeatRule,
        isEnabled = isEnabled,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

class RoomVaultRepository(private val dao: VaultDao) : VaultRepository {
    override suspend fun getVaultEntries(): List<VaultEntry> = dao.getAll().map { it.toDomain() }

    override suspend fun getVaultEntryById(id: String): VaultEntry? = dao.getById(id)?.toDomain()

    override suspend fun saveVaultEntry(entry: VaultEntry) = dao.insert(entry.toEntity())

    override suspend fun deleteVaultEntry(id: String) = dao.deleteById(id)

    private fun VaultEntryEntity.toDomain() = VaultEntry(
        id = id,
        title = title,
        website = website,
        username = username,
        password = passwordCiphertext,
        secretNote = secretNoteCiphertext,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )

    private fun VaultEntry.toEntity() = VaultEntryEntity(
        id = id,
        title = title,
        website = website,
        username = username,
        passwordCiphertext = password,
        secretNoteCiphertext = secretNote,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

class RoomDashboardRepository(
    private val taskDao: TaskDao,
    private val reminderDao: ReminderDao,
    private val placeDao: PlaceDao,
    private val productDao: ProductDao,
) : DashboardRepository {
    override suspend fun getDashboardSummary(): DashboardSummary {
        val openTasks = taskDao.getTasksByStatus(TaskStatus.OPEN)
        val nextTask = openTasks.firstOrNull()?.let {
            TaskItem(
                id = it.id,
                title = it.title,
                description = it.description,
                dueAtEpochMs = it.dueAtEpochMs,
                status = it.status,
                priority = it.priority,
                completedAtEpochMs = it.completedAtEpochMs,
                createdAtEpochMs = it.createdAtEpochMs,
                updatedAtEpochMs = it.updatedAtEpochMs,
            )
        }

        return DashboardSummary(
            nextTask = nextTask,
            openTasksCount = taskDao.getOpenCount(),
            remindersCount = reminderDao.getActiveCount(),
            recentPlacesCount = placeDao.getCount(),
            recentPurchasesCount = productDao.getActiveCount(),
        )
    }
}

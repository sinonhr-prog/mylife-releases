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
import local.mylife.core.model.RecentRecord
import local.mylife.core.model.RecentRecordType
import local.mylife.core.model.ReminderItem
import local.mylife.core.model.ReminderRepository
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskPriority
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

    override suspend fun ensureDefaultCategories() {
        val defaultEntities = local.mylife.core.model.DefaultCategories.PRESET_CATEGORIES.map { it.toEntity() }
        dao.insertAll(defaultEntities)
    }

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

    override suspend fun updateCategoryForProducts(oldCategoryId: String, newCategoryId: String) {
        dao.updateCategory(oldCategoryId, newCategoryId)
    }

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

class RoomPlaceRepository(
    private val dao: PlaceDao,
    private val attachmentDao: AttachmentDao? = null,
) : PlaceRepository {
    override suspend fun getPlaces(categoryId: String?): List<Place> =
        dao.getPlaces(categoryId).map { it.toDomain() }

    override suspend fun getPlaceById(id: String): Place? = dao.getById(id)?.toDomain()

    override suspend fun savePlace(place: Place) = dao.insert(place.toEntity())

    override suspend fun deletePlace(id: String) = dao.deleteById(id)

    override suspend fun getPlaceAttachments(placeId: String): List<AttachmentMeta> =
        dao.getAttachmentsForPlace(placeId).map { it.toDomain() }

    override suspend fun addPlaceAttachment(placeId: String, attachment: AttachmentMeta) {
        attachmentDao?.insert(attachment.toEntity())
        dao.insertPlaceAttachmentCrossRef(PlaceAttachmentCrossRef(placeId = placeId, attachmentId = attachment.id))
    }

    override suspend fun updateCategoryForPlaces(oldCategoryId: String, newCategoryId: String) {
        dao.updateCategory(oldCategoryId, newCategoryId)
    }

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
    private val noteDao: NoteDao,
    private val clock: () -> Long = { System.currentTimeMillis() },
) : DashboardRepository {
    override suspend fun getDashboardSummary(): DashboardSummary {
        val now = clock()
        val openEntities = taskDao.getTasksByStatus(TaskStatus.OPEN)
        val sortedOpenTasks = openEntities.map { it.toDomain() }.sortedWith(
            Comparator { a, b ->
                val aDue = a.dueAtEpochMs
                val bDue = b.dueAtEpochMs
                val aOverdue = aDue != null && aDue < now
                val bOverdue = bDue != null && bDue < now

                when {
                    aOverdue && !bOverdue -> -1
                    !aOverdue && bOverdue -> 1
                    aDue != null && bDue != null -> {
                        val dueCmp = aDue.compareTo(bDue)
                        if (dueCmp != 0) dueCmp
                        else compareTieBreak(a, b)
                    }
                    aDue != null && bDue == null -> -1
                    aDue == null && bDue != null -> 1
                    else -> compareTieBreak(a, b)
                }
            }
        )

        val focusTask = sortedOpenTasks.firstOrNull()
        val remainingTasks = sortedOpenTasks.drop(1).take(2)

        val places = placeDao.getPlaces(null).map { it.toDomain() }
        val recentVisits = places
            .filter {
                val visited = it.lastVisitedAtEpochMs
                visited != null && visited <= now
            }
            .sortedWith(
                compareByDescending<Place> { it.lastVisitedAtEpochMs }
                    .thenByDescending { it.updatedAtEpochMs }
                    .thenBy { it.id }
            )
            .take(3)

        val products = productDao.getProducts(null, includeArchived = false).map { it.toDomain() }
        val notes = noteDao.getAll().map { it.toDomain() }
        val allTasks = taskDao.getAllTasks().map { it.toDomain() }

        val recentRecords = buildList {
            products.forEach { add(RecentRecord(it.id, it.title, RecentRecordType.PURCHASE, it.createdAtEpochMs)) }
            places.forEach { add(RecentRecord(it.id, it.name, RecentRecordType.PLACE, it.createdAtEpochMs)) }
            notes.forEach { add(RecentRecord(it.id, it.title, RecentRecordType.NOTE, it.createdAtEpochMs)) }
            allTasks.forEach { add(RecentRecord(it.id, it.title, RecentRecordType.TASK, it.createdAtEpochMs)) }
        }.sortedWith(
            compareByDescending<RecentRecord> { it.createdAtEpochMs }.thenBy { it.id }
        ).take(5)

        val activeReminders = reminderDao.getActiveReminders()
            .map { it.toDomain() }
            .sortedWith(compareBy<ReminderItem> { it.fireEpochMs }.thenBy { it.id })
            .take(3)

        return DashboardSummary(
            focusTask = focusTask,
            remainingTasks = remainingTasks,
            recentVisits = recentVisits,
            recentRecords = recentRecords,
            activeReminders = activeReminders,
            openTasksCount = taskDao.getOpenCount(),
            remindersCount = reminderDao.getActiveCount(),
            recentPlacesCount = placeDao.getCount(),
            recentPurchasesCount = productDao.getActiveCount(),
        )
    }

    private fun compareTieBreak(a: TaskItem, b: TaskItem): Int {
        fun rank(p: TaskPriority) = when (p) {
            TaskPriority.HIGH -> 3
            TaskPriority.NORMAL -> 2
            TaskPriority.LOW -> 1
        }
        val priorityCmp = rank(b.priority).compareTo(rank(a.priority))
        if (priorityCmp != 0) return priorityCmp
        val createdCmp = a.createdAtEpochMs.compareTo(b.createdAtEpochMs)
        if (createdCmp != 0) return createdCmp
        return a.id.compareTo(b.id)
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

    private fun NoteEntity.toDomain() = Note(id, title, body, isFavorite, createdAtEpochMs, updatedAtEpochMs)

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
}

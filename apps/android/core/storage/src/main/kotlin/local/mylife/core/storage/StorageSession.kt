package local.mylife.core.storage

import android.content.Context
import local.mylife.core.model.CategoryRepository
import local.mylife.core.model.DashboardRepository
import local.mylife.core.model.NoteRepository
import local.mylife.core.model.PlaceRepository
import local.mylife.core.model.ProductRepository
import local.mylife.core.model.TaskRepository
import local.mylife.core.storage.db.EncryptedDatabaseFactory
import local.mylife.core.storage.db.RoomCategoryRepository
import local.mylife.core.storage.db.RoomDashboardRepository
import local.mylife.core.storage.db.RoomNoteRepository
import local.mylife.core.storage.db.RoomPlaceRepository
import local.mylife.core.storage.db.RoomProductRepository
import local.mylife.core.storage.db.RoomTaskRepository
import local.mylife.core.storage.files.EncryptedAttachmentStore
import java.util.Arrays

class StorageSession(
    context: Context,
    private val rawKey: ByteArray,
    private val schedulerPort: local.mylife.core.storage.scheduler.SchedulerPort? = null,
) {
    private val db = EncryptedDatabaseFactory.createDatabase(context, "main.db", rawKey)

    val categoryRepository: CategoryRepository = RoomCategoryRepository(db.categoryDao())
    val productRepository: ProductRepository = RoomProductRepository(db.productDao(), db.attachmentDao())
    val placeRepository: PlaceRepository = RoomPlaceRepository(db.placeDao(), db.attachmentDao())
    val noteRepository: NoteRepository = RoomNoteRepository(db.noteDao())
    val reminderRepository: local.mylife.core.model.ReminderRepository = local.mylife.core.storage.db.RoomReminderRepository(db.reminderDao(), db.scheduleOutboxDao())
    val taskRepository: TaskRepository = RoomTaskRepository(db.taskDao(), reminderRepository)
    val dashboardRepository: DashboardRepository = RoomDashboardRepository(
        db.taskDao(),
        db.reminderDao(),
        db.placeDao(),
        db.productDao(),
        db.noteDao(),
    )
    val attachmentStore: EncryptedAttachmentStore = EncryptedAttachmentStore(context.filesDir)

    val schedulerProjectionStore: local.mylife.core.storage.scheduler.SchedulerProjectionStore =
        local.mylife.core.storage.scheduler.SchedulerProjectionStore(context.filesDir)

    val schedulerReconciler: local.mylife.core.storage.scheduler.SchedulerReconciler =
        local.mylife.core.storage.scheduler.SchedulerReconciler(
            db.scheduleOutboxDao(),
            db.reminderDao(),
            schedulerProjectionStore,
            schedulerPort ?: object : local.mylife.core.storage.scheduler.SchedulerPort {
                override fun schedule(reminderId: String, triggerAtEpochMs: Long) {}
                override fun cancel(reminderId: String) {}
            },
        )

    fun close() {
        db.close()
    }
}

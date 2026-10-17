package local.mylife.core.storage

import android.content.Context
import local.mylife.core.model.DashboardRepository
import local.mylife.core.model.ProductRepository
import local.mylife.core.model.TaskRepository
import local.mylife.core.storage.db.EncryptedDatabaseFactory
import local.mylife.core.storage.db.RoomDashboardRepository
import local.mylife.core.storage.db.RoomProductRepository
import local.mylife.core.storage.db.RoomTaskRepository
import local.mylife.core.storage.files.EncryptedAttachmentStore
import java.util.Arrays

class StorageSession(
    context: Context,
    private val rawKey: ByteArray,
) {
    private val db = EncryptedDatabaseFactory.createDatabase(context, "main.db", rawKey)

    val productRepository: ProductRepository = RoomProductRepository(db.productDao(), db.attachmentDao())
    val taskRepository: TaskRepository = RoomTaskRepository(db.taskDao())
    val dashboardRepository: DashboardRepository = RoomDashboardRepository(
        db.taskDao(),
        db.reminderDao(),
        db.placeDao(),
        db.productDao(),
    )
    val attachmentStore: EncryptedAttachmentStore = EncryptedAttachmentStore(context.filesDir)

    fun close() {
        db.close()
    }
}

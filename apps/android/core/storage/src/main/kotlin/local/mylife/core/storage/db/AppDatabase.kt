package local.mylife.core.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        CategoryEntity::class,
        ProductEntity::class,
        PlaceEntity::class,
        NoteEntity::class,
        TaskEntity::class,
        ReminderEntity::class,
        AttachmentEntity::class,
        ProductAttachmentCrossRef::class,
        PlaceAttachmentCrossRef::class,
        ScheduleOutboxEntity::class,
    ],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun placeDao(): PlaceDao
    abstract fun noteDao(): NoteDao
    abstract fun taskDao(): TaskDao
    abstract fun reminderDao(): ReminderDao
    abstract fun scheduleOutboxDao(): ScheduleOutboxDao
    abstract fun attachmentDao(): AttachmentDao

    companion object {
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Verified safe non-destructive migration transition
                db.execSQL("ALTER TABLE tasks ADD COLUMN extraData TEXT NOT NULL DEFAULT ''")
            }
        }
    }
}

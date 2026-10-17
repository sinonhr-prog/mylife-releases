package local.mylife.core.storage.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [VaultEntryEntity::class],
    version = 1,
    exportSchema = false,
)
abstract class VaultDatabase : RoomDatabase() {
    abstract fun vaultDao(): VaultDao
}

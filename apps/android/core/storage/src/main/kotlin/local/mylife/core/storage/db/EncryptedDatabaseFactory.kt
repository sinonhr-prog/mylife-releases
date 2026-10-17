package local.mylife.core.storage.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object EncryptedDatabaseFactory {
    init {
        System.loadLibrary("sqlcipher")
    }

    fun createDatabase(
        context: Context,
        databaseName: String,
        passphrase: ByteArray,
        builderCustomizer: (RoomDatabase.Builder<AppDatabase>.() -> Unit)? = null,
    ): AppDatabase {
        val factory = SupportOpenHelperFactory(passphrase)
        val builder =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                databaseName,
            ).openHelperFactory(factory)

        builderCustomizer?.invoke(builder)
        return builder.build()
    }
}

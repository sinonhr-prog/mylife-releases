package local.mylife.core.storage.db

import android.content.Context
import androidx.room.Room
import androidx.room.RoomDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

object EncryptedDatabaseFactory {
    init {
        try {
            System.loadLibrary("sqlcipher")
        } catch (_: UnsatisfiedLinkError) {
            // Handled when running on desktop JVM / test harnesses
        }
    }

    fun createDatabase(
        context: Context,
        databaseName: String,
        passphrase: ByteArray,
        builderCustomizer: (RoomDatabase.Builder<AppDatabase>.() -> Unit)? = null,
    ): AppDatabase {
        val factory = SupportOpenHelperFactory(passphrase.clone())
        val builder =
            Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                databaseName,
            ).openHelperFactory(factory)

        builderCustomizer?.invoke(builder)
        return builder.build()
    }

    fun createVaultDatabase(
        context: Context,
        databaseName: String,
        passphrase: ByteArray,
        builderCustomizer: (RoomDatabase.Builder<VaultDatabase>.() -> Unit)? = null,
    ): VaultDatabase {
        val factory = SupportOpenHelperFactory(passphrase.clone())
        val builder =
            Room.databaseBuilder(
                context.applicationContext,
                VaultDatabase::class.java,
                databaseName,
            ).openHelperFactory(factory)

        builderCustomizer?.invoke(builder)
        return builder.build()
    }
}

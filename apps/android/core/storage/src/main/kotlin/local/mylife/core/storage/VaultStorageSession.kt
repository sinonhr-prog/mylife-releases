package local.mylife.core.storage

import android.content.Context
import local.mylife.core.model.VaultRepository
import local.mylife.core.storage.db.EncryptedDatabaseFactory
import local.mylife.core.storage.db.RoomVaultRepository
import local.mylife.core.storage.db.VaultDatabase

class VaultStorageSession(
    context: Context,
    rawKey: ByteArray,
) {
    private val db: VaultDatabase = EncryptedDatabaseFactory.createVaultDatabase(context, "vault.db", rawKey)

    val vaultRepository: VaultRepository = RoomVaultRepository(db.vaultDao())

    fun close() {
        db.close()
    }
}

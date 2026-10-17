package local.mylife.core.security

import java.util.Arrays

sealed interface SessionState {
    data object Locked : SessionState

    class MainUnlocked(
        val mainDbKey: ByteArray,
    ) : SessionState {
        fun zeroize() {
            Arrays.fill(mainDbKey, 0.toByte())
        }
    }

    class VaultUnlocked(
        val mainDbKey: ByteArray,
        val vaultDbKey: ByteArray,
    ) : SessionState {
        fun zeroize() {
            Arrays.fill(mainDbKey, 0.toByte())
            Arrays.fill(vaultDbKey, 0.toByte())
        }

        fun zeroizeVaultOnly() {
            Arrays.fill(vaultDbKey, 0.toByte())
        }
    }
}

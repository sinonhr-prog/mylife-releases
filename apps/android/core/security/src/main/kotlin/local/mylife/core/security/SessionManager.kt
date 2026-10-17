package local.mylife.core.security

import local.mylife.core.security.crypto.KeyEnvelopeManager
import local.mylife.core.security.crypto.KeyScope
import local.mylife.core.security.crypto.PinPolicy

interface SessionManager {
    fun getSessionState(): SessionState
    fun isPinConfigured(): Boolean
    fun setupPin(pin: String): Boolean
    fun unlockMain(pin: String): Boolean
    fun unlockVault(pin: String): Boolean
    fun lockVault()
    fun lockAll()
    fun changePin(oldPin: String, newPin: String): Boolean
    fun getFailedAttempts(): Int
    fun getLockoutDelayRemainingMs(): Long
}

class DefaultSessionManager(
    private val keyEnvelopeManager: KeyEnvelopeManager,
) : SessionManager {

    private var currentState: SessionState = SessionState.Locked
    private var failedAttempts: Int = 0
    private var lastFailedAttemptEpochMs: Long = 0L

    @Synchronized
    override fun getSessionState(): SessionState = currentState

    @Synchronized
    override fun isPinConfigured(): Boolean = keyEnvelopeManager.isConfigured()

    @Synchronized
    override fun getFailedAttempts(): Int = failedAttempts

    @Synchronized
    override fun getLockoutDelayRemainingMs(): Long {
        if (failedAttempts == 0) return 0L
        val requiredDelay = PinPolicy.calculateDelayMs(failedAttempts)
        val elapsed = System.currentTimeMillis() - lastFailedAttemptEpochMs
        val remaining = requiredDelay - elapsed
        return if (remaining > 0L) remaining else 0L
    }

    @Synchronized
    override fun setupPin(pin: String): Boolean {
        if (!PinPolicy.isValidPin(pin)) return false
        val (mainKey, _) = keyEnvelopeManager.setupNewPin(pin)
        // Automatically unlock main on first setup
        currentState = SessionState.MainUnlocked(mainKey)
        failedAttempts = 0
        lastFailedAttemptEpochMs = 0L
        return true
    }

    @Synchronized
    override fun unlockMain(pin: String): Boolean {
        if (getLockoutDelayRemainingMs() > 0L) {
            return false
        }
        if (!PinPolicy.isValidPin(pin)) {
            recordFailure()
            return false
        }

        return try {
            val mainKey = keyEnvelopeManager.unlockScope(KeyScope.MAIN, pin)
            // If already in Vault, keep vault; otherwise transition to MainUnlocked
            val oldState = currentState
            currentState = SessionState.MainUnlocked(mainKey)
            if (oldState is SessionState.MainUnlocked) {
                oldState.zeroize()
            } else if (oldState is SessionState.VaultUnlocked) {
                oldState.zeroize()
            }
            failedAttempts = 0
            lastFailedAttemptEpochMs = 0L
            true
        } catch (_: Exception) {
            recordFailure()
            false
        }
    }

    @Synchronized
    override fun unlockVault(pin: String): Boolean {
        if (getLockoutDelayRemainingMs() > 0L) {
            return false
        }
        if (!PinPolicy.isValidPin(pin)) {
            recordFailure()
            return false
        }

        val activeMainKey = when (val state = currentState) {
            is SessionState.MainUnlocked -> state.mainDbKey
            is SessionState.VaultUnlocked -> state.mainDbKey
            SessionState.Locked -> {
                // Cannot unlock vault if main is completely locked without unlocking main first
                try {
                    keyEnvelopeManager.unlockScope(KeyScope.MAIN, pin)
                } catch (_: Exception) {
                    recordFailure()
                    return false
                }
            }
        }

        return try {
            val vaultKey = keyEnvelopeManager.unlockScope(KeyScope.VAULT, pin)
            currentState = SessionState.VaultUnlocked(activeMainKey, vaultKey)
            failedAttempts = 0
            lastFailedAttemptEpochMs = 0L
            true
        } catch (_: Exception) {
            recordFailure()
            false
        }
    }

    @Synchronized
    override fun lockVault() {
        val state = currentState
        if (state is SessionState.VaultUnlocked) {
            val mainKey = state.mainDbKey
            state.zeroizeVaultOnly()
            currentState = SessionState.MainUnlocked(mainKey)
        }
    }

    @Synchronized
    override fun lockAll() {
        when (val state = currentState) {
            is SessionState.MainUnlocked -> state.zeroize()
            is SessionState.VaultUnlocked -> state.zeroize()
            SessionState.Locked -> Unit
        }
        currentState = SessionState.Locked
    }

    @Synchronized
    override fun changePin(oldPin: String, newPin: String): Boolean {
        if (!PinPolicy.isValidPin(newPin)) return false
        val success = try {
            keyEnvelopeManager.changePin(oldPin, newPin)
        } catch (_: Exception) {
            recordFailure()
            false
        }
        if (success) {
            // Re-unlock main with new PIN to refresh keys cleanly
            unlockMain(newPin)
        }
        return success
    }

    private fun recordFailure() {
        failedAttempts++
        lastFailedAttemptEpochMs = System.currentTimeMillis()
    }
}

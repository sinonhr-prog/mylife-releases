package local.mylife.app.feature.auth

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.mylife.core.security.SessionManager
import local.mylife.core.security.SessionState
import local.mylife.core.security.crypto.PinPolicy

data class PinUiState(
    val isSetupRequired: Boolean = false,
    val enteredDigits: String = "",
    val firstEnteredDigits: String = "",
    val isConfirmStep: Boolean = false,
    val errorMessage: String? = null,
    val lockoutRemainingSeconds: Int = 0,
    val isUnlocked: Boolean = false,
    val targetScope: String = "main", // "main" or "vault"
)

class PinViewModel(
    private val sessionManager: SessionManager,
    private val targetScope: String = "main",
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        PinUiState(
            isSetupRequired = !sessionManager.isPinConfigured(),
            targetScope = targetScope,
            isUnlocked = when (targetScope) {
                "vault" -> sessionManager.getSessionState() is SessionState.VaultUnlocked
                else -> sessionManager.getSessionState() !is SessionState.Locked
            }
        )
    )
    val uiState: StateFlow<PinUiState> = _uiState.asStateFlow()

    private var countdownJob: Job? = null

    init {
        checkLockout()
    }

    private fun checkLockout() {
        val delayRemainingMs = sessionManager.getLockoutDelayRemainingMs()
        if (delayRemainingMs > 0L) {
            val seconds = ((delayRemainingMs + 999L) / 1000L).toInt()
            _uiState.update { it.copy(lockoutRemainingSeconds = seconds, errorMessage = "Слишком много попыток. Подождите $seconds с") }
            startCountdown(seconds)
        } else {
            _uiState.update { it.copy(lockoutRemainingSeconds = 0) }
        }
    }

    private fun startCountdown(initialSeconds: Int) {
        countdownJob?.cancel()
        countdownJob = viewModelScope.launch {
            var s = initialSeconds
            while (s > 0) {
                delay(1000L)
                s--
                _uiState.update {
                    if (s > 0) {
                        it.copy(lockoutRemainingSeconds = s, errorMessage = "Слишком много попыток. Подождите $s с")
                    } else {
                        it.copy(lockoutRemainingSeconds = 0, errorMessage = null)
                    }
                }
            }
        }
    }

    fun onDigitClick(digit: Char) {
        if (_uiState.value.lockoutRemainingSeconds > 0) return
        if (_uiState.value.enteredDigits.length >= PinPolicy.PIN_LENGTH) return

        val newDigits = _uiState.value.enteredDigits + digit
        _uiState.update { it.copy(enteredDigits = newDigits, errorMessage = null) }

        if (newDigits.length == PinPolicy.PIN_LENGTH) {
            handleCompletePin(newDigits)
        }
    }

    fun onDeleteClick() {
        if (_uiState.value.lockoutRemainingSeconds > 0) return
        val digits = _uiState.value.enteredDigits
        if (digits.isNotEmpty()) {
            _uiState.update { it.copy(enteredDigits = digits.dropLast(1), errorMessage = null) }
        }
    }

    fun onClear() {
        _uiState.update { it.copy(enteredDigits = "", errorMessage = null) }
    }

    private fun handleCompletePin(pin: String) {
        val state = _uiState.value
        if (state.isSetupRequired) {
            if (!state.isConfirmStep) {
                // First step of setup complete: transition to confirmation step
                _uiState.update {
                    it.copy(
                        firstEnteredDigits = pin,
                        enteredDigits = "",
                        isConfirmStep = true,
                        errorMessage = null,
                    )
                }
            } else {
                // Confirmation step
                if (pin == state.firstEnteredDigits) {
                    val success = sessionManager.setupPin(pin)
                    if (success) {
                        _uiState.update { it.copy(isUnlocked = true, isSetupRequired = false) }
                    } else {
                        _uiState.update {
                            it.copy(
                                enteredDigits = "",
                                firstEnteredDigits = "",
                                isConfirmStep = false,
                                errorMessage = "Ошибка сохранения PIN",
                            )
                        }
                    }
                } else {
                    _uiState.update {
                        it.copy(
                            enteredDigits = "",
                            firstEnteredDigits = "",
                            isConfirmStep = false,
                            errorMessage = "PIN-коды не совпадают. Введите заново",
                        )
                    }
                }
            }
        } else {
            // Unlock existing PIN
            val success = if (targetScope == "vault") {
                sessionManager.unlockVault(pin)
            } else {
                sessionManager.unlockMain(pin)
            }

            if (success) {
                _uiState.update { it.copy(isUnlocked = true, enteredDigits = "", errorMessage = null) }
            } else {
                _uiState.update { it.copy(enteredDigits = "", errorMessage = "Неверный PIN-код") }
                checkLockout()
            }
        }
    }
}

package local.mylife.core.security.crypto

object PinPolicy {
    const val PIN_LENGTH = 6
    private val PIN_REGEX = Regex("^[0-9]{6}$")

    fun isValidPin(pin: String): Boolean {
        return PIN_REGEX.matches(pin)
    }

    fun isValidNewPin(pin: String): Boolean =
        isValidPin(pin) &&
            pin.toSet().size > 1 && pin !in setOf("123456", "654321", "012345", "543210", "234567", "345678", "456789", "987654", "876543", "765432")

    fun calculateDelayMs(failedAttempts: Int): Long {
        return when {
            failedAttempts <= 2 -> 0L
            failedAttempts == 3 -> 1_000L
            failedAttempts == 4 -> 3_000L
            failedAttempts == 5 -> 10_000L
            failedAttempts == 6 -> 30_000L
            else -> 60_000L
        }
    }
}

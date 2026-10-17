package local.mylife.app.feature.vault

import java.security.SecureRandom

object PasswordGenerator {
    private const val LOWER = "abcdefghjkmnpqrstuvwxyz"
    private const val UPPER = "ABCDEFGHJKMNPQRSTUVWXYZ"
    private const val DIGITS = "23456789"
    private const val SYMBOLS = "!@#$%^&*()-_=+[]{}<>"

    private val secureRandom = SecureRandom()

    fun generatePassword(
        length: Int = 16,
        includeUpper: Boolean = true,
        includeDigits: Boolean = true,
        includeSymbols: Boolean = true,
    ): String {
        require(length in 8..64) { "Password length must be between 8 and 64" }

        val pool = StringBuilder(LOWER)
        val result = StringBuilder()

        if (includeUpper) {
            pool.append(UPPER)
            result.append(UPPER[secureRandom.nextInt(UPPER.length)])
        }
        if (includeDigits) {
            pool.append(DIGITS)
            result.append(DIGITS[secureRandom.nextInt(DIGITS.length)])
        }
        if (includeSymbols) {
            pool.append(SYMBOLS)
            result.append(SYMBOLS[secureRandom.nextInt(SYMBOLS.length)])
        }
        result.append(LOWER[secureRandom.nextInt(LOWER.length)])

        val allChars = pool.toString()
        while (result.length < length) {
            result.append(allChars[secureRandom.nextInt(allChars.length)])
        }

        // Shuffle
        val chars = result.toString().toCharArray()
        for (i in chars.indices.reversed()) {
            val j = secureRandom.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }

        return String(chars)
    }
}

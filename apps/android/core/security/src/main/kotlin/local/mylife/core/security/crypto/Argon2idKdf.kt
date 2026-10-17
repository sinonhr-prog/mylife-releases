package local.mylife.core.security.crypto

import org.bouncycastle.crypto.generators.Argon2BytesGenerator
import org.bouncycastle.crypto.params.Argon2Parameters

data class Argon2Params(
    val memoryKiB: Int = 65536,
    val iterations: Int = 3,
    val parallelism: Int = 1,
    val outputLengthBytes: Int = 32,
    val version: Int = Argon2Parameters.ARGON2_VERSION_13,
)

object Argon2idKdf {
    fun deriveKey(
        password: ByteArray,
        salt: ByteArray,
        params: Argon2Params = Argon2Params(),
        secret: ByteArray? = null,
        additionalData: ByteArray? = null,
    ): ByteArray {
        require(password.isNotEmpty()) { "Password must not be empty" }
        require(salt.size >= 16) { "Salt must be at least 16 bytes" }

        val builder =
            Argon2Parameters.Builder(Argon2Parameters.ARGON2_id)
                .withVersion(params.version)
                .withMemoryAsKB(params.memoryKiB)
                .withIterations(params.iterations)
                .withParallelism(params.parallelism)
                .withSalt(salt)

        secret?.let { builder.withSecret(it) }
        additionalData?.let { builder.withAdditional(it) }

        val generator = Argon2BytesGenerator()
        generator.init(builder.build())

        val result = ByteArray(params.outputLengthBytes)
        generator.generateBytes(password, result, 0, result.size)
        return result
    }
}

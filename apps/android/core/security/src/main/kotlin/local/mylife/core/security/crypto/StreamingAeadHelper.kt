package local.mylife.core.security.crypto

import com.google.crypto.tink.subtle.AesGcmHkdfStreaming
import java.io.InputStream
import java.io.OutputStream

object StreamingAeadHelper {
    fun openEncryptingStream(
        rawKey: ByteArray,
        output: OutputStream,
        aad: ByteArray,
    ): OutputStream = createStreamingCipher(rawKey).newEncryptingStream(output, aad)

    fun openDecryptingStream(
        rawKey: ByteArray,
        input: InputStream,
        aad: ByteArray,
    ): InputStream = createStreamingCipher(rawKey).newDecryptingStream(input, aad)

    private const val HKDF_HASH = "HmacSha256"
    private const val KEY_SIZE_BYTES = 32
    private const val CHUNK_SIZE_BYTES = 4096
    private const val FIRST_SEGMENT_OFFSET = 0

    fun createStreamingCipher(rawKey: ByteArray): AesGcmHkdfStreaming {
        require(rawKey.size == KEY_SIZE_BYTES) { "Raw key must be 32 bytes" }
        return AesGcmHkdfStreaming(
            rawKey,
            HKDF_HASH,
            KEY_SIZE_BYTES,
            CHUNK_SIZE_BYTES,
            FIRST_SEGMENT_OFFSET,
        )
    }

    fun encryptStream(
        rawKey: ByteArray,
        input: InputStream,
        output: OutputStream,
        associatedData: ByteArray,
    ) {
        val streamingCipher = createStreamingCipher(rawKey)
        val encryptingStream = streamingCipher.newEncryptingStream(output, associatedData)
        try {
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (input.read(buffer).also { bytesRead = it } != -1) {
                encryptingStream.write(buffer, 0, bytesRead)
            }
        } finally {
            encryptingStream.close()
        }
    }

    fun decryptStream(
        rawKey: ByteArray,
        input: InputStream,
        output: OutputStream,
        associatedData: ByteArray,
    ) {
        val streamingCipher = createStreamingCipher(rawKey)
        val decryptingStream = streamingCipher.newDecryptingStream(input, associatedData)
        try {
            val buffer = ByteArray(8192)
            var bytesRead: Int
            while (decryptingStream.read(buffer).also { bytesRead = it } != -1) {
                output.write(buffer, 0, bytesRead)
            }
        } finally {
            decryptingStream.close()
        }
    }
}

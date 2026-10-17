package local.mylife.core.security.crypto

import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

object AesGcmEnvelope {
    private const val ALGORITHM = "AES/GCM/NoPadding"
    private const val NONCE_LENGTH_BYTES = 12
    private const val TAG_LENGTH_BITS = 128

    fun seal(
        keyBytes: ByteArray,
        plaintext: ByteArray,
        associatedData: ByteArray,
        nonceOverride: ByteArray? = null,
    ): ByteArray {
        require(keyBytes.size == 32) { "Key must be 256 bits (32 bytes)" }

        val nonce =
            nonceOverride ?: ByteArray(NONCE_LENGTH_BYTES).also {
                SecureRandom().nextBytes(it)
            }
        require(nonce.size == NONCE_LENGTH_BYTES) { "Nonce must be 12 bytes" }

        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        if (associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        val ciphertextWithTag = cipher.doFinal(plaintext)
        val envelope = ByteArray(nonce.size + ciphertextWithTag.size)
        System.arraycopy(nonce, 0, envelope, 0, nonce.size)
        System.arraycopy(ciphertextWithTag, 0, envelope, nonce.size, ciphertextWithTag.size)
        return envelope
    }

    fun open(
        keyBytes: ByteArray,
        envelope: ByteArray,
        associatedData: ByteArray,
    ): ByteArray {
        require(keyBytes.size == 32) { "Key must be 256 bits (32 bytes)" }
        require(envelope.size >= NONCE_LENGTH_BYTES + (TAG_LENGTH_BITS / 8)) {
            "Envelope is too short to contain valid nonce and tag"
        }

        val nonce = ByteArray(NONCE_LENGTH_BYTES)
        System.arraycopy(envelope, 0, nonce, 0, NONCE_LENGTH_BYTES)

        val ciphertextWithTagLength = envelope.size - NONCE_LENGTH_BYTES
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(TAG_LENGTH_BITS, nonce))
        if (associatedData.isNotEmpty()) {
            cipher.updateAAD(associatedData)
        }

        return cipher.doFinal(envelope, NONCE_LENGTH_BYTES, ciphertextWithTagLength)
    }
}

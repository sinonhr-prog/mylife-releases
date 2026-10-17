package local.mylife.core.security.crypto

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.security.GeneralSecurityException

class CryptoVectorsTest {
    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testRfc9106Argon2idOfficialVector() {
        // RFC 9106 Section 5.3: Argon2id test vector
        val password = ByteArray(32) { 0x01 }
        val salt = ByteArray(16) { 0x02 }
        val secret = ByteArray(8) { 0x03 }
        val additionalData = ByteArray(12) { 0x04 }

        val params =
            Argon2Params(
                memoryKiB = 32,
                iterations = 3,
                parallelism = 4,
                outputLengthBytes = 32,
            )

        val derived =
            Argon2idKdf.deriveKey(
                password = password,
                salt = salt,
                params = params,
                secret = secret,
                additionalData = additionalData,
            )

        val expectedHex = "0d640df58d78766c08c037a34a8b53c9d01ef0452d75b65eb52520e96b01e659"
        assertEquals(expectedHex, derived.toHexString(HexFormat.Default))
    }

    @Test
    fun testArgon2idDeterministicOutput() {
        val password = "sample_test_pin_12345678".toByteArray(Charsets.UTF_8)
        val salt = "synthetic_salt_16_bytes!".toByteArray(Charsets.UTF_8)

        val params =
            Argon2Params(
                memoryKiB = 1024,
                iterations = 2,
                parallelism = 1,
                outputLengthBytes = 32,
            )

        val run1 = Argon2idKdf.deriveKey(password, salt, params)
        val run2 = Argon2idKdf.deriveKey(password, salt, params)

        assertArrayEquals(run1, run2)
        assertEquals(32, run1.size)

        // Different salt produces different output
        val differentSalt = "different_salt_16_bytes!".toByteArray(Charsets.UTF_8)
        val run3 = Argon2idKdf.deriveKey(password, differentSalt, params)
        assertFalse(run1.contentEquals(run3))
    }

    @OptIn(ExperimentalStdlibApi::class)
    @Test
    fun testNistCavpAes256GcmVector() {
        // NIST SP 800-38D CAVP test vector (Case 16: 256-bit key, 96-bit IV)
        val key = "feffe9928665731c6d6a8f9467308308feffe9928665731c6d6a8f9467308308".hexToByteArray()
        val iv = "cafebabefacedbaddecaf888".hexToByteArray()
        val plaintext = "d9313225f88406e5a55909c5aff5269a".hexToByteArray()
        val expectedCiphertext = "522dc1f099567d07f47f37a32a84427d".hexToByteArray()
        val expectedTag = "7ea353da7e9241a1d90d693a4954186b".hexToByteArray()

        val envelope =
            AesGcmEnvelope.seal(
                keyBytes = key,
                plaintext = plaintext,
                associatedData = ByteArray(0),
                nonceOverride = iv,
            )

        // Verify nonce prefix matches
        val actualNonce = envelope.copyOfRange(0, 12)
        assertArrayEquals(iv, actualNonce)

        // Verify ciphertext matches
        val actualCiphertext = envelope.copyOfRange(12, 12 + plaintext.size)
        assertArrayEquals(expectedCiphertext, actualCiphertext)

        // Verify tag matches
        val actualTag = envelope.copyOfRange(12 + plaintext.size, envelope.size)
        assertArrayEquals(expectedTag, actualTag)

        // Verify round-trip opening
        val opened = AesGcmEnvelope.open(key, envelope, ByteArray(0))
        assertArrayEquals(plaintext, opened)
    }

    @Test
    fun testAesGcmEnvelopeWithAadTamperDetection() {
        val key = ByteArray(32) { (it * 7).toByte() }
        val plaintext = "Секретный ключ основной базы".toByteArray(Charsets.UTF_8)
        val aad = "version=1;purpose=db-key;id=main".toByteArray(Charsets.UTF_8)

        val envelope = AesGcmEnvelope.seal(key, plaintext, aad)
        val opened = AesGcmEnvelope.open(key, envelope, aad)
        assertArrayEquals(plaintext, opened)

        // Tamper with AAD: must fail
        val corruptedAad = "version=1;purpose=vault-key;id=main".toByteArray(Charsets.UTF_8)
        try {
            AesGcmEnvelope.open(key, envelope, corruptedAad)
            fail("Expected AEAD authentication failure on modified AAD")
        } catch (expected: GeneralSecurityException) {
            assertTrue(
                expected.message?.contains("Tag mismatch", ignoreCase = true) == true ||
                    expected.javaClass.name.contains("AEAD") ||
                    expected.javaClass.name.contains("BadPadding"),
            )
        }

        // Tamper with ciphertext byte: must fail
        val corruptedEnvelope = envelope.clone()
        val lastIdx = corruptedEnvelope.size - 1
        corruptedEnvelope[lastIdx] = (corruptedEnvelope[lastIdx].toInt() xor 0x01).toByte()
        try {
            AesGcmEnvelope.open(key, corruptedEnvelope, aad)
            fail("Expected AEAD authentication failure on corrupted ciphertext")
        } catch (expected: GeneralSecurityException) {
            assertTrue(true)
        }
    }

    @Test
    fun testStreamingAeadMultiChunkRoundTripAndTamperDetection() {
        val key = ByteArray(32) { (it * 13).toByte() }
        val aad = "archive-header-v1".toByteArray(Charsets.UTF_8)

        // Create 25 KB multi-chunk synthetic payload (exceeds 4 KB chunk size)
        val originalPayload = ByteArray(25 * 1024) { (it % 251).toByte() }

        val encryptedOut = ByteArrayOutputStream()
        StreamingAeadHelper.encryptStream(
            rawKey = key,
            input = ByteArrayInputStream(originalPayload),
            output = encryptedOut,
            associatedData = aad,
        )

        val ciphertext = encryptedOut.toByteArray()
        assertTrue("Ciphertext should include overhead for chunk tags", ciphertext.size > originalPayload.size)

        // Successful decryption
        val decryptedOut = ByteArrayOutputStream()
        StreamingAeadHelper.decryptStream(
            rawKey = key,
            input = ByteArrayInputStream(ciphertext),
            output = decryptedOut,
            associatedData = aad,
        )
        assertArrayEquals(originalPayload, decryptedOut.toByteArray())

        // Tamper test: modify byte in middle chunk (e.g. at 10 KB offset)
        val tamperedCiphertext = ciphertext.clone()
        tamperedCiphertext[10240] = (tamperedCiphertext[10240].toInt() xor 0x05).toByte()

        val tamperedOut = ByteArrayOutputStream()
        try {
            StreamingAeadHelper.decryptStream(
                rawKey = key,
                input = ByteArrayInputStream(tamperedCiphertext),
                output = tamperedOut,
                associatedData = aad,
            )
            fail("Expected exception when decrypting tampered stream")
        } catch (expected: IOException) {
            assertTrue("Tampered stream must throw IOException", true)
        }

        // Truncation test: remove last 20 bytes
        val truncatedCiphertext = ciphertext.copyOf(ciphertext.size - 20)
        val truncatedOut = ByteArrayOutputStream()
        try {
            StreamingAeadHelper.decryptStream(
                rawKey = key,
                input = ByteArrayInputStream(truncatedCiphertext),
                output = truncatedOut,
                associatedData = aad,
            )
            fail("Expected exception when decrypting truncated stream")
        } catch (expected: IOException) {
            assertTrue("Truncated stream must throw IOException", true)
        }
    }
}

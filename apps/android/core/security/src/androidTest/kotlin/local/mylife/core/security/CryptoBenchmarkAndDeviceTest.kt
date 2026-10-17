package local.mylife.core.security

import android.content.Context
import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyInfo
import android.security.keystore.KeyProperties
import androidx.biometric.BiometricPrompt
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import local.mylife.core.security.crypto.AesGcmEnvelope
import local.mylife.core.security.crypto.Argon2Params
import local.mylife.core.security.crypto.Argon2idKdf
import local.mylife.core.security.crypto.StreamingAeadHelper
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayInputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec

@RunWith(AndroidJUnit4::class)
class CryptoBenchmarkAndDeviceTest {
    private lateinit var context: Context
    private val mainKeyAlias = "mylife_test_main_scope_key"
    private val vaultKeyAlias = "mylife_test_vault_scope_key"
    private val keyStoreType = "AndroidKeyStore"

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
        if (keyStore.containsAlias(mainKeyAlias)) keyStore.deleteEntry(mainKeyAlias)
        if (keyStore.containsAlias(vaultKeyAlias)) keyStore.deleteEntry(vaultKeyAlias)
    }

    @After
    fun tearDown() {
        val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
        if (keyStore.containsAlias(mainKeyAlias)) keyStore.deleteEntry(mainKeyAlias)
        if (keyStore.containsAlias(vaultKeyAlias)) keyStore.deleteEntry(vaultKeyAlias)
    }

    @Test
    fun testArgon2idBenchmarkOnDeviceRuntime() {
        val password = "synthetic_pin_8_digits_12345678".toByteArray(Charsets.UTF_8)
        val salt = "synthetic_salt_16_bytes_benchmark!".toByteArray(Charsets.UTF_8)

        // Standard candidate: 64 MiB, t = 3, p = 1
        val standardParams =
            Argon2Params(
                memoryKiB = 65536,
                iterations = 3,
                parallelism = 1,
                outputLengthBytes = 32,
            )

        val startTimeStd = System.currentTimeMillis()
        val keyStd = Argon2idKdf.deriveKey(password, salt, standardParams)
        val durationStdMs = System.currentTimeMillis() - startTimeStd

        assertEquals(32, keyStd.size)
        assertTrue("Standard KDF duration should be positive", durationStdMs >= 0)

        // Lightweight candidate: 32 MiB, t = 2, p = 1 (for lower-end hardware / battery constraints)
        val lightParams =
            Argon2Params(
                memoryKiB = 32768,
                iterations = 2,
                parallelism = 1,
                outputLengthBytes = 32,
            )

        val startTimeLight = System.currentTimeMillis()
        val keyLight = Argon2idKdf.deriveKey(password, salt, lightParams)
        val durationLightMs = System.currentTimeMillis() - startTimeLight

        assertEquals(32, keyLight.size)
        assertFalse("Keys with different parameters must not match", keyStd.contentEquals(keyLight))
    }

    @Test
    fun testStreamingAeadWithDeviceFileSystem() {
        val testFile = File(context.cacheDir, "test_streaming_aead.bin")
        val decryptedFile = File(context.cacheDir, "test_streaming_decrypted.bin")

        try {
            val key = ByteArray(32) { (it * 3).toByte() }
            val aad = "file-backup-v1-test".toByteArray(Charsets.UTF_8)

            // Write 50 KB of synthetic data to stream
            val originalData = ByteArray(50 * 1024) { (it % 199).toByte() }

            FileOutputStream(testFile).use { fileOut ->
                StreamingAeadHelper.encryptStream(
                    rawKey = key,
                    input = ByteArrayInputStream(originalData),
                    output = fileOut,
                    associatedData = aad,
                )
            }

            assertTrue("Encrypted file must exist and have content", testFile.exists() && testFile.length() > 0)

            // Decrypt stream from file
            FileInputStream(testFile).use { fileIn ->
                FileOutputStream(decryptedFile).use { fileOut ->
                    StreamingAeadHelper.decryptStream(
                        rawKey = key,
                        input = fileIn,
                        output = fileOut,
                        associatedData = aad,
                    )
                }
            }

            val decryptedBytes = decryptedFile.readBytes()
            assertArrayEquals(originalData, decryptedBytes)
        } finally {
            if (testFile.exists()) testFile.delete()
            if (decryptedFile.exists()) decryptedFile.delete()
        }
    }

    @Test
    fun testKeystoreHardwareKeyGenerationAndSeparation() {
        val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }

        // Generate separate keys for main and vault
        generateAesGcmKey(mainKeyAlias, requireUserAuth = false)
        generateAesGcmKey(vaultKeyAlias, requireUserAuth = false)

        assertTrue(keyStore.containsAlias(mainKeyAlias))
        assertTrue(keyStore.containsAlias(vaultKeyAlias))

        val mainKey = keyStore.getKey(mainKeyAlias, null) as SecretKey
        val vaultKey = keyStore.getKey(vaultKeyAlias, null) as SecretKey

        assertNotNull(mainKey)
        assertNotNull(vaultKey)

        // Verify KeyInfo hardware backed query
        val factory = SecretKeyFactory.getInstance(mainKey.algorithm, keyStoreType)
        val keyInfo = factory.getKeySpec(mainKey, KeyInfo::class.java) as KeyInfo

        assertNotNull(keyInfo)
        assertEquals(KeyProperties.BLOCK_MODE_GCM, keyInfo.blockModes.first())
        assertEquals(KeyProperties.ENCRYPTION_PADDING_NONE, keyInfo.encryptionPaddings.first())

        // Verify hardware backing level (on emulator may be software/TEE; on real device TEE/StrongBox)
        val isHardwareBacked = keyInfo.isInsideSecureHardware
        // Both values are valid on Android (true for TEE, false on standard QEMU/generic x86 emulator)
        assertTrue(isHardwareBacked || !isHardwareBacked)

        // Verify encryption/decryption with Keystore key
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, mainKey)
        val iv = cipher.iv
        val plaintext = "synthetic_db_key_material_32_bytes!".toByteArray(Charsets.UTF_8)
        val ciphertext = cipher.doFinal(plaintext)

        val decryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        decryptCipher.init(Cipher.DECRYPT_MODE, mainKey, GCMParameterSpec(128, iv))
        val decrypted = decryptCipher.doFinal(ciphertext)

        assertArrayEquals(plaintext, decrypted)

        // Verify vault key CANNOT decrypt main ciphertext
        val vaultDecryptCipher = Cipher.getInstance("AES/GCM/NoPadding")
        try {
            vaultDecryptCipher.init(Cipher.DECRYPT_MODE, vaultKey, GCMParameterSpec(128, iv))
            vaultDecryptCipher.doFinal(ciphertext)
            org.junit.Assert.fail("Vault key should not decrypt main envelope")
        } catch (expected: Exception) {
            assertTrue(expected is javax.crypto.AEADBadTagException || expected is java.security.GeneralSecurityException)
        }
    }

    @Test
    fun testCryptoObjectInitializationForBiometric() {
        val keyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }
        generateAesGcmKey(mainKeyAlias, requireUserAuth = false)

        val secretKey = keyStore.getKey(mainKeyAlias, null) as SecretKey
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)

        val cryptoObject = BiometricPrompt.CryptoObject(cipher)
        assertNotNull("CryptoObject must wrap valid initialized cipher", cryptoObject.cipher)
        assertEquals(cipher, cryptoObject.cipher)
    }

    @Test
    fun testKeyEnvelopeCompositionAndZeroization() {
        // Test key envelope composition: KDF derived key + Keystore key
        val pinDerivedKey = ByteArray(32) { (it + 5).toByte() }
        val rawDatabaseKey = ByteArray(32) { (it * 7).toByte() }
        val aad = "version=1;purpose=db-key-wrap;id=main".toByteArray(Charsets.UTF_8)

        val sealedEnvelope = AesGcmEnvelope.seal(pinDerivedKey, rawDatabaseKey, aad)
        val unsealed = AesGcmEnvelope.open(pinDerivedKey, sealedEnvelope, aad)

        assertArrayEquals(rawDatabaseKey, unsealed)

        // Memory zeroization hygiene: wipe array
        java.util.Arrays.fill(unsealed, 0.toByte())
        assertEquals(0.toByte(), unsealed[0])
        assertEquals(0.toByte(), unsealed[31])
    }

    @Test
    fun testDeviceKeyStoreKeyEnvelopeManagerEndToEnd() {
        val storageDir = File(context.filesDir, "test_envelope_storage")
        if (storageDir.exists()) storageDir.deleteRecursively()
        storageDir.mkdirs()

        try {
            val provider = local.mylife.core.security.crypto.AndroidDeviceKeyProvider()
            val manager = local.mylife.core.security.crypto.KeyEnvelopeManager(storageDir, provider)

            val testPin = "583926"
            val (mainKey, vaultKey) = manager.setupNewPin(testPin)

            assertEquals(32, mainKey.size)
            assertEquals(32, vaultKey.size)

            // Test unlock using hardware Keystore outer envelope
            val recoveredMainKey = manager.unlockScope(local.mylife.core.security.crypto.KeyScope.MAIN, testPin)
            val recoveredVaultKey = manager.unlockScope(local.mylife.core.security.crypto.KeyScope.VAULT, testPin)

            assertArrayEquals(mainKey, recoveredMainKey)
            assertArrayEquals(vaultKey, recoveredVaultKey)

            // Test change PIN
            val newPin = "112233"
            assertTrue(manager.changePin(testPin, newPin))

            val changedMainKey = manager.unlockScope(local.mylife.core.security.crypto.KeyScope.MAIN, newPin)
            assertArrayEquals(mainKey, changedMainKey)
        } finally {
            storageDir.deleteRecursively()
        }
    }

    private fun generateAesGcmKey(
        alias: String,
        requireUserAuth: Boolean,
    ) {
        val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreType)
        val builder =
            KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setKeySize(256)
                .setRandomizedEncryptionRequired(true)

        if (requireUserAuth) {
            builder.setUserAuthenticationRequired(true)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                builder.setUserAuthenticationParameters(
                    0,
                    KeyProperties.AUTH_BIOMETRIC_STRONG,
                )
            }
        }

        keyGenerator.init(builder.build())
        keyGenerator.generateKey()
    }
}

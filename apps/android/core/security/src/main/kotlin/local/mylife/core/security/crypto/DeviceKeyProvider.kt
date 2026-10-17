package local.mylife.core.security.crypto

import android.os.Build
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

interface DeviceKeyProvider {
    fun encrypt(alias: String, plaintext: ByteArray): ByteArray
    fun decrypt(alias: String, ciphertext: ByteArray): ByteArray
    fun getCipherForBiometricAuth(alias: String): Cipher
    fun deleteKey(alias: String)
    fun hasKey(alias: String): Boolean
}

class AndroidDeviceKeyProvider(
    private val keyStoreType: String = "AndroidKeyStore"
) : DeviceKeyProvider {

    private val keyStore: KeyStore = KeyStore.getInstance(keyStoreType).apply { load(null) }

    private fun getOrCreateKey(alias: String, requireUserAuth: Boolean): SecretKey {
        if (!keyStore.containsAlias(alias)) {
            val keyGenerator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, keyStoreType)
            val builder = KeyGenParameterSpec.Builder(
                alias,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
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
                        KeyProperties.AUTH_BIOMETRIC_STRONG
                    )
                }
            }

            keyGenerator.init(builder.build())
            keyGenerator.generateKey()
        }
        return keyStore.getKey(alias, null) as SecretKey
    }

    override fun encrypt(alias: String, plaintext: ByteArray): ByteArray {
        val secretKey = getOrCreateKey(alias, requireUserAuth = false)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        val result = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(ciphertext, 0, result, iv.size, ciphertext.size)
        return result
    }

    override fun decrypt(alias: String, ciphertext: ByteArray): ByteArray {
        val secretKey = getOrCreateKey(alias, requireUserAuth = false)
        val iv = ByteArray(12)
        val encryptedData = ByteArray(ciphertext.size - 12)
        System.arraycopy(ciphertext, 0, iv, 0, 12)
        System.arraycopy(ciphertext, 12, encryptedData, 0, encryptedData.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(128, iv))
        return cipher.doFinal(encryptedData)
    }

    override fun getCipherForBiometricAuth(alias: String): Cipher {
        val secretKey = getOrCreateKey(alias, requireUserAuth = true)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, secretKey)
        return cipher
    }

    override fun deleteKey(alias: String) {
        if (keyStore.containsAlias(alias)) {
            keyStore.deleteEntry(alias)
        }
    }

    override fun hasKey(alias: String): Boolean {
        return keyStore.containsAlias(alias)
    }
}

class InMemoryDeviceKeyProvider : DeviceKeyProvider {
    private val keys = mutableMapOf<String, SecretKey>()

    private fun getOrCreateKey(alias: String): SecretKey {
        return keys.getOrPut(alias) {
            val raw = ByteArray(32) { (it * 17 + alias.hashCode()).toByte() }
            SecretKeySpec(raw, "AES")
        }
    }

    override fun encrypt(alias: String, plaintext: ByteArray): ByteArray {
        val key = getOrCreateKey(alias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(plaintext)
        val result = ByteArray(iv.size + ciphertext.size)
        System.arraycopy(iv, 0, result, 0, iv.size)
        System.arraycopy(ciphertext, 0, result, iv.size, ciphertext.size)
        return result
    }

    override fun decrypt(alias: String, ciphertext: ByteArray): ByteArray {
        val key = getOrCreateKey(alias)
        val iv = ByteArray(12)
        val encryptedData = ByteArray(ciphertext.size - 12)
        System.arraycopy(ciphertext, 0, iv, 0, 12)
        System.arraycopy(ciphertext, 12, encryptedData, 0, encryptedData.size)

        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key, GCMParameterSpec(128, iv))
        return cipher.doFinal(encryptedData)
    }

    override fun getCipherForBiometricAuth(alias: String): Cipher {
        val key = getOrCreateKey(alias)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key)
        return cipher
    }

    override fun deleteKey(alias: String) {
        keys.remove(alias)
    }

    override fun hasKey(alias: String): Boolean {
        return keys.containsKey(alias)
    }
}

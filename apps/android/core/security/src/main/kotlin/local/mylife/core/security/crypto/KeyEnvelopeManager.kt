package local.mylife.core.security.crypto

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.security.GeneralSecurityException
import java.security.SecureRandom
import java.util.Arrays

data class KeyEnvelopeRecord(
    val version: Int = 1,
    val scope: KeyScope,
    val salt: ByteArray,
    val kdfParams: Argon2Params,
    val outerWrap: ByteArray,
    val bioWrap: ByteArray? = null,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as KeyEnvelopeRecord
        if (version != other.version) return false
        if (scope != other.scope) return false
        if (!salt.contentEquals(other.salt)) return false
        if (kdfParams != other.kdfParams) return false
        if (!outerWrap.contentEquals(other.outerWrap)) return false
        if (bioWrap != null) {
            if (other.bioWrap == null) return false
            if (!bioWrap.contentEquals(other.bioWrap)) return false
        } else if (other.bioWrap != null) return false
        return true
    }

    override fun hashCode(): Int {
        var result = version
        result = 31 * result + scope.hashCode()
        result = 31 * result + salt.contentHashCode()
        result = 31 * result + kdfParams.hashCode()
        result = 31 * result + outerWrap.contentHashCode()
        result = 31 * result + (bioWrap?.contentHashCode() ?: 0)
        return result
    }
}

class KeyEnvelopeManager(
    private val storageDir: File,
    private val deviceKeyProvider: DeviceKeyProvider,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    companion object {
        private const val MAGIC_HEADER = 0x4D4C4B45 // "MLKE"
        private const val CURRENT_VERSION = 1
        private const val SALT_LENGTH = 16

        val DEFAULT_KDF_PARAMS = Argon2Params(
            memoryKiB = 65536,
            iterations = 3,
            parallelism = 1,
            outputLengthBytes = 32,
        )

        fun getDeviceKeyAlias(scope: KeyScope): String = "mylife_device_key_${scope.id}"
        fun getBioKeyAlias(scope: KeyScope): String = "mylife_bio_key_${scope.id}"
    }

    init {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
    }

    fun isConfigured(): Boolean {
        return getEnvelopeFile(KeyScope.MAIN).exists()
    }

    fun getEnvelopeFile(scope: KeyScope): File = File(storageDir, "envelope_${scope.id}.bin")

    fun setupNewPin(pin: String): Pair<ByteArray, ByteArray> {
        require(PinPolicy.isValidPin(pin)) { "PIN must be exactly 6 digits" }

        val mainDbKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val vaultDbKey = ByteArray(32).also { secureRandom.nextBytes(it) }

        createAndSaveEnvelope(KeyScope.MAIN, pin, mainDbKey)
        createAndSaveEnvelope(KeyScope.VAULT, pin, vaultDbKey)

        return Pair(mainDbKey, vaultDbKey)
    }

    fun restoreKeysWithNewPin(pin: String, mainDbKey: ByteArray, vaultDbKey: ByteArray) {
        require(PinPolicy.isValidPin(pin)) { "PIN must be exactly 6 digits" }
        require(mainDbKey.size == 32) { "Invalid main DB key length: ${mainDbKey.size}" }
        require(vaultDbKey.size == 32) { "Invalid vault DB key length: ${vaultDbKey.size}" }

        createAndSaveEnvelope(KeyScope.MAIN, pin, mainDbKey)
        createAndSaveEnvelope(KeyScope.VAULT, pin, vaultDbKey)
    }

    fun unlockScope(scope: KeyScope, pin: String): ByteArray {
        require(PinPolicy.isValidPin(pin)) { "PIN must be exactly 6 digits" }

        val record = readEnvelopeRecord(scope)
        val deviceAlias = getDeviceKeyAlias(scope)

        // 1. Unwrap outer envelope using device-bound master key
        val innerWrap = deviceKeyProvider.decrypt(deviceAlias, record.outerWrap)

        // 2. Derive K_pin using Argon2id
        val pinBytes = pin.toByteArray(Charsets.UTF_8)
        val kPin = try {
            Argon2idKdf.deriveKey(pinBytes, record.salt, record.kdfParams)
        } finally {
            Arrays.fill(pinBytes, 0.toByte())
        }

        // 3. Unwrap inner envelope using K_pin and authenticated AAD
        return try {
            AesGcmEnvelope.open(kPin, innerWrap, scope.pinWrapAad())
        } finally {
            Arrays.fill(kPin, 0.toByte())
        }
    }

    fun changePin(oldPin: String, newPin: String): Boolean {
        require(PinPolicy.isValidPin(newPin)) { "New PIN must be exactly 6 digits" }

        val mainKey = unlockScope(KeyScope.MAIN, oldPin)
        val vaultKey = unlockScope(KeyScope.VAULT, oldPin)

        try {
            // Write both new envelopes atomically
            createAndSaveEnvelope(KeyScope.MAIN, newPin, mainKey)
            createAndSaveEnvelope(KeyScope.VAULT, newPin, vaultKey)
            return true
        } finally {
            Arrays.fill(mainKey, 0.toByte())
            Arrays.fill(vaultKey, 0.toByte())
        }
    }

    private fun createAndSaveEnvelope(scope: KeyScope, pin: String, dbKey: ByteArray) {
        val salt = ByteArray(SALT_LENGTH).also { secureRandom.nextBytes(it) }
        val pinBytes = pin.toByteArray(Charsets.UTF_8)
        val kPin = try {
            Argon2idKdf.deriveKey(pinBytes, salt, DEFAULT_KDF_PARAMS)
        } finally {
            Arrays.fill(pinBytes, 0.toByte())
        }

        val innerWrap = try {
            AesGcmEnvelope.seal(kPin, dbKey, scope.pinWrapAad())
        } finally {
            Arrays.fill(kPin, 0.toByte())
        }

        val deviceAlias = getDeviceKeyAlias(scope)
        val outerWrap = deviceKeyProvider.encrypt(deviceAlias, innerWrap)

        val record = KeyEnvelopeRecord(
            version = CURRENT_VERSION,
            scope = scope,
            salt = salt,
            kdfParams = DEFAULT_KDF_PARAMS,
            outerWrap = outerWrap,
            bioWrap = null,
        )

        saveEnvelopeRecordAtomically(record)
    }

    fun saveEnvelopeRecordAtomically(record: KeyEnvelopeRecord) {
        val finalFile = getEnvelopeFile(record.scope)
        val tmpFile = File(storageDir, "${finalFile.name}.tmp")

        FileOutputStream(tmpFile).use { fos ->
            writeRecordToStream(record, fos)
            fos.fd.sync()
        }

        if (!tmpFile.renameTo(finalFile)) {
            // Fallback for atomic replace on some filesystems
            if (finalFile.exists()) {
                finalFile.delete()
            }
            if (!tmpFile.renameTo(finalFile)) {
                throw IOException("Failed to rename temporary envelope file to ${finalFile.absolutePath}")
            }
        }
    }

    fun readEnvelopeRecord(scope: KeyScope): KeyEnvelopeRecord {
        val file = getEnvelopeFile(scope)
        if (!file.exists()) {
            throw IOException("Envelope file not found: ${file.absolutePath}")
        }
        val bytes = file.readBytes()
        return readRecordFromStream(ByteArrayInputStream(bytes))
    }

    private fun writeRecordToStream(record: KeyEnvelopeRecord, out: java.io.OutputStream) {
        val dos = DataOutputStream(out)
        dos.writeInt(MAGIC_HEADER)
        dos.writeByte(record.version)
        dos.writeUTF(record.scope.id)

        dos.writeShort(record.salt.size)
        dos.write(record.salt)

        dos.writeInt(record.kdfParams.memoryKiB)
        dos.writeInt(record.kdfParams.iterations)
        dos.writeInt(record.kdfParams.parallelism)
        dos.writeInt(record.kdfParams.outputLengthBytes)

        dos.writeInt(record.outerWrap.size)
        dos.write(record.outerWrap)

        if (record.bioWrap != null) {
            dos.writeBoolean(true)
            dos.writeInt(record.bioWrap.size)
            dos.write(record.bioWrap)
        } else {
            dos.writeBoolean(false)
        }
        dos.flush()
    }

    private fun readRecordFromStream(input: java.io.InputStream): KeyEnvelopeRecord {
        val dis = DataInputStream(input)
        val magic = dis.readInt()
        if (magic != MAGIC_HEADER) {
            throw GeneralSecurityException("Invalid envelope magic header: $magic")
        }
        val version = dis.readByte().toInt()
        if (version != CURRENT_VERSION) {
            throw GeneralSecurityException("Unsupported envelope version: $version")
        }
        val scopeId = dis.readUTF()
        val scope = KeyScope.values().firstOrNull { it.id == scopeId }
            ?: throw GeneralSecurityException("Unknown scope ID in envelope: $scopeId")

        val saltSize = dis.readShort().toInt()
        val salt = ByteArray(saltSize)
        dis.readFully(salt)

        val memoryKiB = dis.readInt()
        val iterations = dis.readInt()
        val parallelism = dis.readInt()
        val outputLength = dis.readInt()
        val kdfParams = Argon2Params(memoryKiB, iterations, parallelism, outputLength)

        val outerSize = dis.readInt()
        val outerWrap = ByteArray(outerSize)
        dis.readFully(outerWrap)

        val hasBio = dis.readBoolean()
        val bioWrap = if (hasBio) {
            val bioSize = dis.readInt()
            val bioBytes = ByteArray(bioSize)
            dis.readFully(bioBytes)
            bioBytes
        } else {
            null
        }

        return KeyEnvelopeRecord(
            version = version,
            scope = scope,
            salt = salt,
            kdfParams = kdfParams,
            outerWrap = outerWrap,
            bioWrap = bioWrap,
        )
    }
}

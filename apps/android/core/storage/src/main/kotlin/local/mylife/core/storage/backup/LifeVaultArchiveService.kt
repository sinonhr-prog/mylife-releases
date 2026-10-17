package local.mylife.core.storage.backup

import local.mylife.core.security.crypto.AesGcmEnvelope
import local.mylife.core.security.crypto.Argon2Params
import local.mylife.core.security.crypto.Argon2idKdf
import local.mylife.core.security.crypto.KeyEnvelopeManager
import local.mylife.core.security.crypto.PinPolicy
import local.mylife.core.security.crypto.StreamingAeadHelper
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.security.GeneralSecurityException
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Arrays
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ArchiveManifest(
    val formatVersion: Int = 1,
    val exportedAtEpochMs: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0.0",
    val files: Map<String, String>, // relative path -> SHA-256
)

class LifeVaultArchiveService(
    private val appFilesDir: File,
    private val appDatabasesDir: File,
    private val cacheDir: File,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    companion object {
        val MAGIC_BYTES = byteArrayOf(0x4C, 0x49, 0x46, 0x45, 0x56, 0x31, 0x00) // "LIFEV1\0"
        const val FORMAT_VERSION: Byte = 0x01
        private val ARCHIVE_AAD = "v=1;p=archive-key;m=LIFEV1".toByteArray(Charsets.UTF_8)
        private val STREAM_AAD = "v=1;p=archive-body".toByteArray(Charsets.UTF_8)

        // Strict security limits to prevent memory exhaustion (DoS) attacks
        const val MAX_KDF_MEMORY_KIB = 131072 // 128 MiB
        const val MAX_KDF_ITERATIONS = 10
        const val MAX_KDF_PARALLELISM = 4
        const val MAX_ARCHIVE_ENTRIES = 50000
        const val MAX_UNCOMPRESSED_SIZE_BYTES = 500L * 1024L * 1024L // 500 MiB

        val DEFAULT_ARCHIVE_KDF = Argon2Params(
            memoryKiB = 65536, // 64 MiB
            iterations = 3,
            parallelism = 1,
            outputLengthBytes = 32,
        )
    }

    /**
     * Exports databases and encrypted attachments into a portable .lifevault archive.
     */
    fun exportArchive(
        passphrase: String,
        outputStream: OutputStream,
        mainDbKey: ByteArray,
        vaultDbKey: ByteArray,
    ) {
        require(passphrase.length >= 8) { "Archive passphrase must be at least 8 characters" }
        require(mainDbKey.size == 32) { "Invalid main DB key length" }
        require(vaultDbKey.size == 32) { "Invalid vault DB key length" }

        // 1. Generate random archive master key and salt
        val archiveMasterKey = ByteArray(32).also { secureRandom.nextBytes(it) }
        val salt = ByteArray(16).also { secureRandom.nextBytes(it) }

        // 2. Derive key from passphrase
        val passBytes = passphrase.toByteArray(Charsets.UTF_8)
        val kdfKey = try {
            Argon2idKdf.deriveKey(passBytes, salt, DEFAULT_ARCHIVE_KDF)
        } finally {
            Arrays.fill(passBytes, 0.toByte())
        }

        // 3. Seal archive master key with AES-GCM
        val sealedMasterKey = try {
            AesGcmEnvelope.seal(kdfKey, archiveMasterKey, ARCHIVE_AAD)
        } finally {
            Arrays.fill(kdfKey, 0.toByte())
        }

        // 4. Write Header to output stream
        val dos = DataOutputStream(outputStream)
        dos.write(MAGIC_BYTES)
        dos.writeByte(FORMAT_VERSION.toInt())
        dos.write(salt)
        dos.writeInt(DEFAULT_ARCHIVE_KDF.memoryKiB)
        dos.writeInt(DEFAULT_ARCHIVE_KDF.iterations)
        dos.writeShort(DEFAULT_ARCHIVE_KDF.parallelism)
        dos.writeShort(sealedMasterKey.size)
        dos.write(sealedMasterKey)
        dos.flush()

        // 5. Build canonical ZIP in memory / temporary staging
        val stagingZipFile = File(cacheDir, "staging_export_${UUID.randomUUID()}.zip")
        try {
            val fileHashes = mutableMapOf<String, String>()

            ZipOutputStream(BufferedOutputStream(FileOutputStream(stagingZipFile))).use { zos ->
                // Add encryption keys securely into archive zip
                val keysEntry = ZipEntry("keys/keys.bin")
                zos.putNextEntry(keysEntry)
                val keysBytes = ByteArray(64)
                System.arraycopy(mainDbKey, 0, keysBytes, 0, 32)
                System.arraycopy(vaultDbKey, 0, keysBytes, 32, 32)
                zos.write(keysBytes)
                zos.closeEntry()
                val keysSha = MessageDigest.getInstance("SHA-256").digest(keysBytes).joinToString("") { "%02x".format(it) }
                fileHashes["keys/keys.bin"] = keysSha
                Arrays.fill(keysBytes, 0.toByte())

                // Add databases
                addFileToZip(zos, File(appDatabasesDir, "main.db"), "databases/main.db", fileHashes)
                val vaultDb = File(appDatabasesDir, "vault.db")
                if (vaultDb.exists()) {
                    addFileToZip(zos, vaultDb, "databases/vault.db", fileHashes)
                }

                // Add attachments
                val attachmentsDir = File(appFilesDir, "attachments")
                attachmentsDir.listFiles()?.forEach { file ->
                    if (file.isFile && !file.name.endsWith(".tmp")) {
                        addFileToZip(zos, file, "attachments/${file.name}", fileHashes)
                    }
                }

                val thumbnailsDir = File(appFilesDir, "thumbnails")
                thumbnailsDir.listFiles()?.forEach { file ->
                    if (file.isFile && !file.name.endsWith(".tmp")) {
                        addFileToZip(zos, file, "thumbnails/${file.name}", fileHashes)
                    }
                }

                // Add manifest entry
                val manifestEntry = ZipEntry("manifest.txt")
                zos.putNextEntry(manifestEntry)
                val manifestContent = buildString {
                    appendLine("version:1")
                    appendLine("timestamp:${System.currentTimeMillis()}")
                    fileHashes.forEach { (rel, hash) ->
                        appendLine("$hash  $rel")
                    }
                }
                zos.write(manifestContent.toByteArray(Charsets.UTF_8))
                zos.closeEntry()
            }

            // 6. Encrypt ZIP stream into archive output via Tink Streaming AEAD
            FileInputStream(stagingZipFile).use { zipIn ->
                StreamingAeadHelper.encryptStream(
                    rawKey = archiveMasterKey,
                    input = zipIn,
                    output = outputStream,
                    associatedData = STREAM_AAD,
                )
            }
        } finally {
            Arrays.fill(archiveMasterKey, 0.toByte())
            if (stagingZipFile.exists()) stagingZipFile.delete()
        }
    }

    /**
     * Inspects archive header, unseals master key and reads manifest.txt without applying changes.
     */
    fun inspectArchive(
        passphrase: String,
        inputStream: InputStream,
    ): ArchiveManifest {
        require(passphrase.isNotBlank()) { "Archive passphrase cannot be empty" }

        val dis = DataInputStream(BufferedInputStream(inputStream))

        // 1. Validate magic header
        val magic = ByteArray(MAGIC_BYTES.size)
        dis.readFully(magic)
        if (!magic.contentEquals(MAGIC_BYTES)) {
            throw GeneralSecurityException("Invalid archive magic header")
        }

        // 2. Validate format version
        val version = dis.readByte()
        if (version != FORMAT_VERSION) {
            throw GeneralSecurityException("Unsupported archive version: $version")
        }

        // 3. Read salt & validate KDF parameters against DoS bounds
        val salt = ByteArray(16)
        dis.readFully(salt)

        val memoryKiB = dis.readInt()
        val iterations = dis.readInt()
        val parallelism = dis.readShort().toInt()

        if (memoryKiB <= 0 || memoryKiB > MAX_KDF_MEMORY_KIB ||
            iterations <= 0 || iterations > MAX_KDF_ITERATIONS ||
            parallelism <= 0 || parallelism > MAX_KDF_PARALLELISM
        ) {
            throw GeneralSecurityException("Archive KDF parameters exceed secure bounds")
        }

        val kdfParams = Argon2Params(memoryKiB, iterations, parallelism, 32)

        // 4. Read sealed master key
        val sealedKeySize = dis.readShort().toInt()
        val sealedKey = ByteArray(sealedKeySize)
        dis.readFully(sealedKey)

        // 5. Derive key and unwrap master key
        val passBytes = passphrase.toByteArray(Charsets.UTF_8)
        val kdfKey = try {
            Argon2idKdf.deriveKey(passBytes, salt, kdfParams)
        } finally {
            Arrays.fill(passBytes, 0.toByte())
        }

        val archiveMasterKey = try {
            AesGcmEnvelope.open(kdfKey, sealedKey, ARCHIVE_AAD)
        } finally {
            Arrays.fill(kdfKey, 0.toByte())
        }

        val stagingDir = File(cacheDir, "staging_inspect_${UUID.randomUUID()}").apply { mkdirs() }
        val stagingZipFile = File(stagingDir, "decrypted.zip")

        try {
            FileOutputStream(stagingZipFile).use { zipOut ->
                StreamingAeadHelper.decryptStream(
                    rawKey = archiveMasterKey,
                    input = dis,
                    output = zipOut,
                    associatedData = STREAM_AAD,
                )
            }

            val filesMap = mutableMapOf<String, String>()
            var exportedAt = 0L

            ZipInputStream(BufferedInputStream(FileInputStream(stagingZipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    val name = entry.name
                    if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                        throw GeneralSecurityException("Malicious path in archive: $name")
                    }
                    if (name == "manifest.txt") {
                        val manifestBytes = zis.readBytes()
                        val lines = String(manifestBytes, Charsets.UTF_8).lines()
                        for (line in lines) {
                            if (line.startsWith("timestamp:")) {
                                exportedAt = line.substringAfter("timestamp:").toLongOrNull() ?: 0L
                            } else {
                                val parts = line.split("  ", limit = 2)
                                if (parts.size == 2) {
                                    filesMap[parts[1]] = parts[0]
                                }
                            }
                        }
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            return ArchiveManifest(
                formatVersion = version.toInt(),
                exportedAtEpochMs = if (exportedAt > 0L) exportedAt else System.currentTimeMillis(),
                appVersion = "1.0.0",
                files = filesMap,
            )
        } finally {
            Arrays.fill(archiveMasterKey, 0.toByte())
            stagingDir.deleteRecursively()
        }
    }

    /**
     * Inspects and securely restores an archive into clean staging, then atomically applies it.
     */
    fun restoreArchive(
        passphrase: String,
        inputStream: InputStream,
        targetPin: String,
        targetKeyEnvelopeManager: KeyEnvelopeManager,
    ) {
        require(PinPolicy.isValidPin(targetPin)) { "Target PIN must be strictly 6 digits" }
        require(passphrase.isNotBlank()) { "Archive passphrase cannot be empty" }

        val dis = DataInputStream(BufferedInputStream(inputStream))

        // 1. Validate magic header
        val magic = ByteArray(MAGIC_BYTES.size)
        dis.readFully(magic)
        if (!magic.contentEquals(MAGIC_BYTES)) {
            throw GeneralSecurityException("Invalid archive magic header")
        }

        // 2. Validate format version
        val version = dis.readByte()
        if (version != FORMAT_VERSION) {
            throw GeneralSecurityException("Unsupported archive version: $version")
        }

        // 3. Read salt & validate KDF parameters against DoS bounds
        val salt = ByteArray(16)
        dis.readFully(salt)

        val memoryKiB = dis.readInt()
        val iterations = dis.readInt()
        val parallelism = dis.readShort().toInt()

        if (memoryKiB <= 0 || memoryKiB > MAX_KDF_MEMORY_KIB ||
            iterations <= 0 || iterations > MAX_KDF_ITERATIONS ||
            parallelism <= 0 || parallelism > MAX_KDF_PARALLELISM
        ) {
            throw GeneralSecurityException("Archive KDF parameters exceed secure bounds: mem=$memoryKiB, iter=$iterations, p=$parallelism")
        }

        val kdfParams = Argon2Params(memoryKiB, iterations, parallelism, 32)

        // 4. Read sealed master key
        val sealedKeySize = dis.readShort().toInt()
        val sealedKey = ByteArray(sealedKeySize)
        dis.readFully(sealedKey)

        // 5. Derive key and unwrap master key
        val passBytes = passphrase.toByteArray(Charsets.UTF_8)
        val kdfKey = try {
            Argon2idKdf.deriveKey(passBytes, salt, kdfParams)
        } finally {
            Arrays.fill(passBytes, 0.toByte())
        }

        val archiveMasterKey = try {
            AesGcmEnvelope.open(kdfKey, sealedKey, ARCHIVE_AAD)
        } finally {
            Arrays.fill(kdfKey, 0.toByte())
        }

        // 6. Decrypt stream to staging ZIP file
        val stagingDir = File(cacheDir, "staging_restore_${UUID.randomUUID()}").apply { mkdirs() }
        val stagingZipFile = File(stagingDir, "decrypted.zip")

        try {
            FileOutputStream(stagingZipFile).use { zipOut ->
                StreamingAeadHelper.decryptStream(
                    rawKey = archiveMasterKey,
                    input = dis,
                    output = zipOut,
                    associatedData = STREAM_AAD,
                )
            }

            // 7. Unpack ZIP and verify path traversal security (Zip Slip protection) & decompression bounds
            val extractedFiles = mutableMapOf<String, File>()
            var totalEntries = 0
            var totalBytesUncompressed = 0L

            ZipInputStream(BufferedInputStream(FileInputStream(stagingZipFile))).use { zis ->
                var entry = zis.nextEntry
                while (entry != null) {
                    totalEntries++
                    if (totalEntries > MAX_ARCHIVE_ENTRIES) {
                        throw GeneralSecurityException("Archive exceeds maximum entry count: $totalEntries")
                    }

                    val name = entry.name
                    // Strict anti-path-traversal check
                    if (name.contains("..") || name.startsWith("/") || name.startsWith("\\")) {
                        throw GeneralSecurityException("Malicious path in archive: $name")
                    }

                    val destFile = File(stagingDir, name)
                    // Ensure canonical path stays within staging directory
                    if (!destFile.canonicalPath.startsWith(stagingDir.canonicalPath + File.separator)) {
                        throw GeneralSecurityException("Archive entry escapes destination: $name")
                    }

                    if (entry.isDirectory) {
                        destFile.mkdirs()
                    } else {
                        destFile.parentFile?.mkdirs()
                        FileOutputStream(destFile).use { fileOut ->
                            val buf = ByteArray(8192)
                            var len: Int
                            while (zis.read(buf).also { len = it } != -1) {
                                totalBytesUncompressed += len
                                if (totalBytesUncompressed > MAX_UNCOMPRESSED_SIZE_BYTES) {
                                    throw GeneralSecurityException("Archive exceeds maximum uncompressed size: $totalBytesUncompressed")
                                }
                                fileOut.write(buf, 0, len)
                            }
                        }
                        extractedFiles[name] = destFile
                    }
                    zis.closeEntry()
                    entry = zis.nextEntry
                }
            }

            // 8. Validate manifest
            val manifestFile = extractedFiles["manifest.txt"]
                ?: throw GeneralSecurityException("Missing manifest in archive")
            val manifestLines = manifestFile.readLines()
            for (line in manifestLines) {
                val parts = line.split("  ", limit = 2)
                if (parts.size == 2) {
                    val expectedSha256 = parts[0]
                    val relPath = parts[1]
                    val actualFile = extractedFiles[relPath]
                        ?: throw GeneralSecurityException("Missing declared file in archive: $relPath")
                    val actualSha256 = sha256(actualFile)
                    if (!actualSha256.equals(expectedSha256, ignoreCase = true)) {
                        throw GeneralSecurityException("Integrity check failed for $relPath: expected $expectedSha256 but got $actualSha256")
                    }
                }
            }

            // 9. Read restored database encryption keys
            val keysFile = extractedFiles["keys/keys.bin"]
                ?: throw GeneralSecurityException("Missing keys entry in archive")
            val keysBytes = keysFile.readBytes()
            if (keysBytes.size != 64) {
                throw GeneralSecurityException("Invalid keys data size in archive: ${keysBytes.size}")
            }
            val restoredMainKey = ByteArray(32)
            val restoredVaultKey = ByteArray(32)
            System.arraycopy(keysBytes, 0, restoredMainKey, 0, 32)
            System.arraycopy(keysBytes, 32, restoredVaultKey, 0, 32)
            Arrays.fill(keysBytes, 0.toByte())

            // 10. Atomically swap files into place:
            // Staging databases -> production databases
            val restoredMainDb = extractedFiles["databases/main.db"]
            if (restoredMainDb != null) {
                val targetMain = File(appDatabasesDir, "main.db")
                if (!appDatabasesDir.exists()) appDatabasesDir.mkdirs()
                copyOrMoveFile(restoredMainDb, targetMain)
            }

            val restoredVaultDb = extractedFiles["databases/vault.db"]
            if (restoredVaultDb != null) {
                val targetVault = File(appDatabasesDir, "vault.db")
                copyOrMoveFile(restoredVaultDb, targetVault)
            }

            // Staging attachments -> production attachments
            extractedFiles.forEach { (rel, file) ->
                if (rel.startsWith("attachments/") || rel.startsWith("thumbnails/")) {
                    val target = File(appFilesDir, rel)
                    target.parentFile?.mkdirs()
                    copyOrMoveFile(file, target)
                }
            }

            // 11. Generate fresh local KeyStore envelopes on target device using user's new PIN and restored keys
            try {
                targetKeyEnvelopeManager.restoreKeysWithNewPin(targetPin, restoredMainKey, restoredVaultKey)
            } finally {
                Arrays.fill(restoredMainKey, 0.toByte())
                Arrays.fill(restoredVaultKey, 0.toByte())
            }
        } finally {
            Arrays.fill(archiveMasterKey, 0.toByte())
            stagingDir.deleteRecursively()
        }
    }

    private fun addFileToZip(
        zos: ZipOutputStream,
        file: File,
        entryName: String,
        hashes: MutableMap<String, String>,
    ) {
        if (!file.exists()) return
        val entry = ZipEntry(entryName)
        zos.putNextEntry(entry)
        val hash = FileInputStream(file).use { input ->
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(8192)
            var len: Int
            while (input.read(buf).also { len = it } != -1) {
                zos.write(buf, 0, len)
                md.update(buf, 0, len)
            }
            md.digest().joinToString("") { "%02x".format(it) }
        }
        zos.closeEntry()
        hashes[entryName] = hash
    }

    private fun sha256(file: File): String {
        val md = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buf = ByteArray(8192)
            var len: Int
            while (input.read(buf).also { len = it } != -1) {
                md.update(buf, 0, len)
            }
        }
        return md.digest().joinToString("") { "%02x".format(it) }
    }

    private fun copyOrMoveFile(src: File, dst: File) {
        val tmpDst = File(dst.parentFile, "${dst.name}.tmp")
        src.copyTo(tmpDst, overwrite = true)
        if (!tmpDst.renameTo(dst)) {
            if (dst.exists()) dst.delete()
            if (!tmpDst.renameTo(dst)) {
                src.copyTo(dst, overwrite = true)
                tmpDst.delete()
            }
        }
    }
}

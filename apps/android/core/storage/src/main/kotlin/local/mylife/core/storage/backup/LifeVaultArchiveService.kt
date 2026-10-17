package local.mylife.core.storage.backup

import local.mylife.core.security.crypto.AesGcmEnvelope
import local.mylife.core.security.crypto.Argon2Params
import local.mylife.core.security.crypto.Argon2idKdf
import local.mylife.core.security.crypto.KeyEnvelopeManager
import local.mylife.core.security.crypto.PinPolicy
import local.mylife.core.security.crypto.StreamingAeadHelper
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileOutputStream
import java.io.FilterInputStream
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
    val files: Map<String, String>,
)

/** Compatible v1 archive. ZIP and raw keys exist only inside authenticated streams/in memory. */
class LifeVaultArchiveService(
    private val appFilesDir: File,
    private val appDatabasesDir: File,
    private val cacheDir: File,
    private val secureRandom: SecureRandom = SecureRandom(),
) {
    companion object {
        val MAGIC_BYTES = byteArrayOf(0x4C, 0x49, 0x46, 0x45, 0x56, 0x31, 0x00)
        const val FORMAT_VERSION: Byte = 0x01
        private val ARCHIVE_AAD = "v=1;p=archive-key;m=LIFEV1".toByteArray()
        private val STREAM_AAD = "v=1;p=archive-body".toByteArray()
        const val MAX_KDF_MEMORY_KIB = 131072
        const val MAX_KDF_ITERATIONS = 10
        const val MAX_KDF_PARALLELISM = 4
        const val MAX_ARCHIVE_ENTRIES = 50000
        const val MAX_UNCOMPRESSED_SIZE_BYTES = 1024L * 1024 * 1024
        private const val MAX_MANIFEST_BYTES = 8 * 1024 * 1024
        private const val MAX_STREAM_BYTES = MAX_UNCOMPRESSED_SIZE_BYTES + 32 * 1024 * 1024
        val DEFAULT_ARCHIVE_KDF = Argon2Params(65536, 3, 1, 32)
    }

    fun exportArchive(
        passphrase: String,
        outputStream: OutputStream,
        mainDbKey: ByteArray,
        vaultDbKey: ByteArray,
    ) {
        require(passphrase.length >= 8) { "Archive passphrase must be at least 8 characters" }
        require(mainDbKey.size == 32 && vaultDbKey.size == 32)
        // Caller closes both storage sessions first. Refuse an inconsistent SQLite snapshot.
        listOf("main.db", "vault.db").forEach { name ->
            require(!File(appDatabasesDir, "$name-wal").let { it.exists() && it.length() > 0 }) { "Close databases before export" }
        }
        require(File(appDatabasesDir, "main.db").isFile) { "Main database missing" }
        val master = ByteArray(32).also(secureRandom::nextBytes)
        val salt = ByteArray(16).also(secureRandom::nextBytes)
        try {
            val kdf = derive(passphrase, salt, DEFAULT_ARCHIVE_KDF)
            val sealed =
                try {
                    AesGcmEnvelope.seal(kdf, master, ARCHIVE_AAD)
                } finally {
                    kdf.fill(0)
                }
            val header = DataOutputStream(outputStream)
            header.write(MAGIC_BYTES)
            header.writeByte(FORMAT_VERSION.toInt())
            header.write(salt)
            header.writeInt(DEFAULT_ARCHIVE_KDF.memoryKiB)
            header.writeInt(DEFAULT_ARCHIVE_KDF.iterations)
            header.writeShort(DEFAULT_ARCHIVE_KDF.parallelism)
            header.writeShort(sealed.size)
            header.write(sealed)
            header.flush()
            val encrypted = StreamingAeadHelper.openEncryptingStream(master, outputStream, STREAM_AAD)
            ZipOutputStream(BufferedOutputStream(encrypted)).use { zip ->
                val hashes = linkedMapOf<String, String>()
                val keys = mainDbKey + vaultDbKey
                try {
                    zip.putNextEntry(ZipEntry("keys/keys.bin"))
                    zip.write(keys)
                    zip.closeEntry()
                    hashes["keys/keys.bin"] = digest(keys)
                } finally {
                    keys.fill(0)
                }
                var total = 64L

                fun add(
                    file: File,
                    path: String,
                ) {
                    check(hashes.size < MAX_ARCHIVE_ENTRIES - 1)
                    total += file.length()
                    require(total <= MAX_UNCOMPRESSED_SIZE_BYTES)
                    zip.putNextEntry(ZipEntry(path))
                    val hash = MessageDigest.getInstance("SHA-256")
                    file.inputStream().use { input ->
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            hash.update(buffer, 0, n)
                            zip.write(buffer, 0, n)
                        }
                    }
                    zip.closeEntry()
                    hashes[path] = hex(hash.digest())
                }
                listOf("main.db", "vault.db").forEach {
                        name ->
                    File(appDatabasesDir, name).takeIf { it.isFile }?.let { add(it, "databases/$name") }
                }
                listOf("attachments", "thumbnails").forEach { folder ->
                    File(
                        appFilesDir,
                        folder,
                    ).listFiles()?.sortedBy { it.name }?.filter { it.isFile && !it.name.endsWith(".tmp") }?.forEach {
                        require(validPath("$folder/${it.name}"))
                        add(it, "$folder/${it.name}")
                    }
                }
                val manifest =
                    buildString {
                        appendLine("version:1")
                        appendLine("timestamp:${System.currentTimeMillis()}")
                        hashes.forEach { (path, hash) -> appendLine("$hash  $path") }
                    }.toByteArray()
                require(manifest.size <= MAX_MANIFEST_BYTES)
                zip.putNextEntry(ZipEntry("manifest.txt"))
                zip.write(manifest)
                zip.closeEntry()
            }
        } finally {
            master.fill(0)
        }
    }

    fun inspectArchive(
        passphrase: String,
        inputStream: InputStream,
    ): ArchiveManifest = readArchive(passphrase, inputStream, null) { manifest, _, _ -> manifest }

    fun restoreArchive(
        passphrase: String,
        inputStream: InputStream,
        targetPin: String,
        targetKeyEnvelopeManager: KeyEnvelopeManager,
    ) {
        require(PinPolicy.isValidPin(targetPin))
        val stage = File(cacheDir, "restore-${UUID.randomUUID()}")
        check(stage.mkdirs())
        try {
            readArchive(passphrase, inputStream, stage) { _, keys, files ->
                val mainKey = keys.copyOfRange(0, 32)
                val vaultKey = keys.copyOfRange(32, 64)
                try {
                    val replacements = linkedMapOf<File, File?>()
                    listOf("main.db", "vault.db").forEach { name ->
                        replacements[File(appDatabasesDir, name)] = files["databases/$name"]
                        replacements[File(appDatabasesDir, "$name-wal")] = null
                        replacements[File(appDatabasesDir, "$name-shm")] = null
                        replacements[File(appDatabasesDir, "$name-journal")] = null
                    }
                    listOf("attachments", "thumbnails").forEach { folder ->
                        File(appFilesDir, folder).listFiles()?.filter { it.isFile }?.forEach { replacements[it] = null }
                    }
                    files.filterKeys { it.startsWith("attachments/") || it.startsWith("thumbnails/") }.forEach { (path, source) ->
                        replacements[File(appFilesDir, path)] = source
                    }
                    targetKeyEnvelopeManager.applyFilesWithNewPin(targetPin, mainKey, vaultKey, replacements)
                } finally {
                    mainKey.fill(0)
                    vaultKey.fill(0)
                }
            }
        } finally {
            stage.deleteRecursively()
        }
    }

    private fun <T> readArchive(
        passphrase: String,
        input: InputStream,
        stage: File?,
        action: (ArchiveManifest, ByteArray, Map<String, File>) -> T,
    ): T {
        require(passphrase.isNotBlank())
        val header = DataInputStream(BufferedInputStream(input))
        val magic = ByteArray(MAGIC_BYTES.size)
        header.readFully(magic)
        if (!magic.contentEquals(MAGIC_BYTES) || header.readByte() != FORMAT_VERSION) {
            throw GeneralSecurityException(
                "Invalid archive magic header or version",
            )
        }
        val salt = ByteArray(16)
        header.readFully(salt)
        val memory = header.readInt()
        val iterations = header.readInt()
        val parallelism = header.readUnsignedShort()
        if (memory !in 8192..MAX_KDF_MEMORY_KIB || iterations !in 1..MAX_KDF_ITERATIONS || parallelism !in 1..MAX_KDF_PARALLELISM) {
            throw GeneralSecurityException(
                "Archive KDF parameters exceed secure bounds",
            )
        }
        val sealedSize = header.readUnsignedShort()
        if (sealedSize != 60) throw GeneralSecurityException("Invalid sealed key size")
        val sealed = ByteArray(sealedSize)
        header.readFully(sealed)
        val kdf = derive(passphrase, salt, Argon2Params(memory, iterations, parallelism, 32))
        val master =
            try {
                AesGcmEnvelope.open(kdf, sealed, ARCHIVE_AAD)
            } finally {
                kdf.fill(0)
            }
        var keys = ByteArray(0)
        try {
            val decrypting = StreamingAeadHelper.openDecryptingStream(master, header, STREAM_AAD)
            decrypting.use { raw ->
                val bounded = LimitedInput(raw, MAX_STREAM_BYTES)
                // ZipInputStream may stop before AEAD's final segment. Drain the authenticated stream below.
                val zip =
                    ZipInputStream(
                        BufferedInputStream(
                            object : FilterInputStream(bounded) {
                                override fun close() {}
                            },
                        ),
                    )
                val hashes = linkedMapOf<String, String>()
                val files = linkedMapOf<String, File>()
                val names = mutableSetOf<String>()
                var manifestBytes: ByteArray? = null
                var total = 0L
                while (true) {
                    val entry = zip.nextEntry ?: break
                    val path = entry.name
                    if (!validPath(path) || entry.isDirectory || !names.add(path) || names.size > MAX_ARCHIVE_ENTRIES) {
                        throw GeneralSecurityException(
                            "Invalid archive entry",
                        )
                    }
                    val memoryOut = if (path == "keys/keys.bin" || path == "manifest.txt") ByteArrayOutputStream() else null
                    val target = if (stage != null && memoryOut == null) File(stage, "entry-${names.size}") else null
                    val diskOut = target?.let { FileOutputStream(it) }
                    val hash = MessageDigest.getInstance("SHA-256")
                    var size = 0L
                    try {
                        val buffer = ByteArray(8192)
                        while (true) {
                            val n = zip.read(buffer)
                            if (n < 0) break
                            size += n
                            total += n
                            if (total > MAX_UNCOMPRESSED_SIZE_BYTES || (path == "keys/keys.bin" && size > 64) || (path == "manifest.txt" && size > MAX_MANIFEST_BYTES)) {
                                throw GeneralSecurityException(
                                    "Archive size limit",
                                )
                            }
                            hash.update(buffer, 0, n)
                            memoryOut?.write(buffer, 0, n)
                            diskOut?.write(buffer, 0, n)
                        }
                    } finally {
                        diskOut?.close()
                    }
                    when (path) {
                        "keys/keys.bin" -> {
                            keys = memoryOut!!.toByteArray()
                            if (keys.size != 64) throw GeneralSecurityException("Invalid keys")
                        }
                        "manifest.txt" -> manifestBytes = memoryOut!!.toByteArray()
                        else -> if (target != null) files[path] = target
                    }
                    if (path != "manifest.txt") hashes[path] = hex(hash.digest())
                    zip.closeEntry()
                }
                // Buffered ZIP may have prefetched authenticated bytes; consume its backing stream to EOF.
                val drain = ByteArray(8192)
                while (bounded.read(drain) != -1) { }
                zip.close()
                val text = manifestBytes?.toString(Charsets.UTF_8) ?: throw GeneralSecurityException("Missing manifest")
                val expected = linkedMapOf<String, String>()
                var timestamp = 0L
                var version = false
                text.lineSequence().filter { it.isNotEmpty() }.forEach { line ->
                    when {
                        line == "version:1" -> version = true
                        line.startsWith(
                            "timestamp:",
                        ) -> timestamp = line.substringAfter(':').toLongOrNull() ?: throw GeneralSecurityException("Invalid timestamp")
                        else -> {
                            val parts = line.split("  ", limit = 2)
                            if (parts.size != 2 || !parts[0].matches(Regex("[a-fA-F0-9]{64}")) || !validPath(parts[1]) || expected.put(parts[1], parts[0].lowercase()) != null) {
                                throw GeneralSecurityException(
                                    "Invalid manifest",
                                )
                            }
                        }
                    }
                }
                if (!version || keys.size != 64 || !hashes.containsKey("databases/main.db") || expected != hashes) {
                    throw GeneralSecurityException(
                        "Archive integrity mismatch",
                    )
                }
                return action(ArchiveManifest(exportedAtEpochMs = timestamp, files = expected), keys, files)
            }
        } finally {
            keys.fill(0)
            master.fill(0)
        }
    }

    private fun validPath(path: String): Boolean =
        path == "manifest.txt" || path == "keys/keys.bin" || path == "databases/main.db" || path == "databases/vault.db" ||
            path.matches(Regex("(attachments|thumbnails)/[A-Za-z0-9_-][A-Za-z0-9_.-]{0,199}")) && !path.contains("..")

    private fun derive(
        phrase: String,
        salt: ByteArray,
        params: Argon2Params,
    ): ByteArray {
        val bytes = phrase.toByteArray(Charsets.UTF_8)
        return try {
            Argon2idKdf.deriveKey(bytes, salt, params)
        } finally {
            Arrays.fill(bytes, 0.toByte())
        }
    }

    private fun digest(bytes: ByteArray): String = hex(MessageDigest.getInstance("SHA-256").digest(bytes))

    private fun hex(bytes: ByteArray): String = bytes.joinToString("") { "%02x".format(it) }

    private class LimitedInput(input: InputStream, private val limit: Long) : FilterInputStream(input) {
        private var count = 0L

        override fun read(): Int {
            val value = super.read()
            if (value >= 0) checkLimit(1)
            return value
        }

        override fun read(
            buffer: ByteArray,
            offset: Int,
            length: Int,
        ): Int {
            val n = `in`.read(buffer, offset, length)
            if (n > 0) checkLimit(n)
            return n
        }

        private fun checkLimit(n: Int) {
            count += n
            if (count > limit) throw GeneralSecurityException("Archive stream size limit")
        }
    }
}

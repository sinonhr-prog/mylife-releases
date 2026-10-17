package local.mylife.core.security.crypto

import java.io.DataInputStream
import java.io.DataOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Journal contains only paths and copies of already encrypted files. Call before opening storage. */
class RecoverableFileBatch(private val journalDir: File, private val allowedRoot: File) {
    private val ready get() = File(journalDir, "ready")
    private val committed get() = File(journalDir, "committed")

    @Synchronized
    fun recover() {
        if (!journalDir.exists()) return
        if (ready.isFile && !committed.isFile) {
            val records =
                DataInputStream(FileInputStream(ready)).use { input ->
                    require(input.readInt() == 1) { "Unsupported file journal" }
                    val count = input.readInt()
                    require(count in 0..50010)
                    List(count) { checked(File(input.readUTF())) to input.readBoolean() }
                }
            records.forEachIndexed { index, (target, existed) ->
                if (existed) {
                    replace(File(journalDir, "old-$index"), target)
                } else {
                    Files.deleteIfExists(target.toPath())
                }
            }
        }
        val retired = File(journalDir.parentFile, "completed-batch-" + java.util.UUID.randomUUID())
        Files.move(journalDir.toPath(), retired.toPath(), StandardCopyOption.ATOMIC_MOVE)
        check(retired.deleteRecursively()) { "Cannot clear completed journal" }
    }

    @Synchronized
    fun apply(
        replacements: Map<File, File?>,
        afterReplace: (Int) -> Unit = {},
    ) {
        recover()
        check(journalDir.mkdirs())
        val entries = replacements.entries.map { checked(it.key) to it.value }
        try {
            entries.forEachIndexed { index, (target, source) ->
                if (target.exists()) copySynced(target, File(journalDir, "old-$index"))
                if (source != null) copySynced(source, File(journalDir, "new-$index"))
            }
            FileOutputStream(File(journalDir, "ready.tmp")).use { stream ->
                val out = DataOutputStream(stream)
                out.writeInt(1)
                out.writeInt(entries.size)
                entries.forEach { (target, _) ->
                    out.writeUTF(target.path)
                    out.writeBoolean(target.exists())
                }
                out.flush()
                stream.fd.sync()
            }
            Files.move(File(journalDir, "ready.tmp").toPath(), ready.toPath(), StandardCopyOption.ATOMIC_MOVE)
            entries.forEachIndexed { index, (target, source) ->
                if (source == null) {
                    Files.deleteIfExists(target.toPath())
                } else {
                    replace(File(journalDir, "new-$index"), target)
                }
                afterReplace(index)
            }
            FileOutputStream(committed).use {
                it.write(1)
                it.fd.sync()
            }
        } catch (failure: Exception) {
            try {
                recover()
            } catch (rollback: Exception) {
                failure.addSuppressed(rollback)
            }
            throw failure
        }
        recover()
    }

    private fun checked(file: File): File {
        val result = file.canonicalFile
        require(result.path.startsWith(allowedRoot.canonicalPath + File.separator)) { "Target outside private storage" }
        require(!result.path.startsWith(journalDir.canonicalPath + File.separator))
        return result
    }

    private fun copySynced(
        source: File,
        target: File,
    ) {
        target.parentFile?.mkdirs()
        FileInputStream(source).use { input ->
            FileOutputStream(target).use { output ->
                input.copyTo(output)
                output.fd.sync()
            }
        }
    }

    private fun replace(
        source: File,
        target: File,
    ) {
        val temporary = File(target.parentFile, "${target.name}.batch-tmp")
        copySynced(source, temporary)
        Files.move(temporary.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE)
    }
}

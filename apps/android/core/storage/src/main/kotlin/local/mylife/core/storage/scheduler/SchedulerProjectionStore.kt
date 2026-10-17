package local.mylife.core.storage.scheduler

import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

class SchedulerProjectionStore(
    private val storageDir: File,
    private val fileName: String = "scheduler_projection.json",
) {
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val file: File get() = File(storageDir, fileName)
    private val lock = Any()

    fun getAll(): List<SchedulerProjectionItem> = synchronized(lock) {
        if (!file.exists()) return emptyList()
        return try {
            val content = file.readText()
            if (content.isBlank()) emptyList()
            else json.decodeFromString(ListSerializer(SchedulerProjectionItem.serializer()), content)
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getActive(): List<SchedulerProjectionItem> = getAll().filter { it.isEnabled }

    fun getById(reminderId: String): SchedulerProjectionItem? =
        getAll().firstOrNull { it.reminderId == reminderId }

    fun upsert(item: SchedulerProjectionItem) = synchronized(lock) {
        val current = getAll().toMutableList()
        val index = current.indexOfFirst { it.reminderId == item.reminderId }
        if (index >= 0) {
            current[index] = item
        } else {
            current.add(item)
        }
        saveAll(current)
    }

    fun remove(reminderId: String) = synchronized(lock) {
        val current = getAll().toMutableList()
        val removed = current.removeAll { it.reminderId == reminderId }
        if (removed) {
            saveAll(current)
        }
    }

    fun clear() = synchronized(lock) {
        if (file.exists()) {
            file.delete()
        }
    }

    private fun saveAll(items: List<SchedulerProjectionItem>) {
        if (!storageDir.exists()) {
            storageDir.mkdirs()
        }
        val content = json.encodeToString(ListSerializer(SchedulerProjectionItem.serializer()), items)
        val tempFile = File(storageDir, "$fileName.tmp")
        tempFile.writeText(content)
        if (file.exists()) {
            file.delete()
        }
        tempFile.renameTo(file)
    }
}

package local.mylife.core.storage.scheduler

import local.mylife.core.model.ReminderTriggerCalculator
import local.mylife.core.storage.db.ReminderDao
import local.mylife.core.storage.db.ScheduleOutboxDao

class SchedulerReconciler(
    private val outboxDao: ScheduleOutboxDao,
    private val reminderDao: ReminderDao,
    private val projectionStore: SchedulerProjectionStore,
    private val schedulerPort: SchedulerPort,
) {
    suspend fun reconcile(now: Long = System.currentTimeMillis()): Int {
        val pending = outboxDao.getPending()
        var processedCount = 0

        for (item in pending) {
            when (item.operation) {
                "UPSERT" -> {
                    val reminder = reminderDao.getById(item.reminderId)
                    if (reminder != null && reminder.isEnabled) {
                        val nextTrigger = ReminderTriggerCalculator.computeNextTrigger(
                            baseFireEpochMs = reminder.fireEpochMs,
                            repeat = reminder.toDomain().repeat,
                            now = now,
                        ) ?: reminder.fireEpochMs

                        val projectionItem = SchedulerProjectionItem(
                            reminderId = reminder.id,
                            fireEpochMs = nextTrigger,
                            repeatRule = reminder.repeatRule,
                            isEnabled = true,
                        )
                        projectionStore.upsert(projectionItem)
                        schedulerPort.schedule(reminder.id, nextTrigger)
                    } else {
                        projectionStore.remove(item.reminderId)
                        schedulerPort.cancel(item.reminderId)
                    }
                }
                "CANCEL" -> {
                    projectionStore.remove(item.reminderId)
                    schedulerPort.cancel(item.reminderId)
                }
            }
            outboxDao.markProcessed(item.id, now)
            processedCount++
        }

        // Prune outbox entries older than 7 days
        val threshold = now - (7 * 24 * 60 * 60 * 1000L)
        outboxDao.deleteProcessedBefore(threshold)

        return processedCount
    }

    private fun local.mylife.core.storage.db.ReminderEntity.toDomain() = local.mylife.core.model.ReminderItem(
        id = id,
        taskId = taskId,
        title = title,
        fireEpochMs = fireEpochMs,
        repeatRule = repeatRule,
        isEnabled = isEnabled,
        createdAtEpochMs = createdAtEpochMs,
        updatedAtEpochMs = updatedAtEpochMs,
    )
}

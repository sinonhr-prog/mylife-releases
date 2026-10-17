package local.mylife.app.feature.reminders

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import local.mylife.core.model.ReminderRepeat
import local.mylife.core.model.ReminderTriggerCalculator
import local.mylife.core.storage.scheduler.SchedulerProjectionStore

class ReminderAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent == null || intent.action != ACTION_FIRE_REMINDER) return

        val reminderId = intent.getStringExtra(EXTRA_REMINDER_ID) ?: return

        // 1. Показываем нейтральное уведомление (строго без приватных данных)
        ReminderNotificationHelper.showNeutralNotification(context, reminderId)

        // 2. Обрабатываем проекцию (если повторяющееся напоминание - рассчитываем следующий trigger, если разовое - помечаем disabled)
        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val projectionStore = SchedulerProjectionStore(context.filesDir)
                val item = projectionStore.getById(reminderId)
                if (item != null) {
                    val repeat = try {
                        item.repeatRule?.let { ReminderRepeat.valueOf(it) } ?: ReminderRepeat.ONCE
                    } catch (e: Exception) {
                        ReminderRepeat.ONCE
                    }

                    if (repeat != ReminderRepeat.ONCE) {
                        val nextTrigger = ReminderTriggerCalculator.computeNextTrigger(
                            baseFireEpochMs = item.fireEpochMs,
                            repeat = repeat,
                            now = System.currentTimeMillis(),
                        )
                        if (nextTrigger != null) {
                            val updatedItem = item.copy(fireEpochMs = nextTrigger)
                            projectionStore.upsert(updatedItem)
                            val scheduler = AndroidAlarmScheduler(context)
                            scheduler.schedule(reminderId, nextTrigger)
                        } else {
                            projectionStore.remove(reminderId)
                        }
                    } else {
                        // Разовое напоминание сработало - выключаем в проекции
                        projectionStore.upsert(item.copy(isEnabled = false))
                    }
                }
            } catch (e: Exception) {
                // Ignore background projection error
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE_REMINDER = "local.mylife.app.action.FIRE_REMINDER"
        const val EXTRA_REMINDER_ID = "extra_reminder_id"
    }
}

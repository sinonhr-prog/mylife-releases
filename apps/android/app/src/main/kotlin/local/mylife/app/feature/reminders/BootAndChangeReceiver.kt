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

class BootAndChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        val action = intent?.action ?: return
        if (action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_TIME_CHANGED &&
            action != Intent.ACTION_TIMEZONE_CHANGED
        ) {
            return
        }

        val pendingResult = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                rescheduleAll(context)
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        suspend fun rescheduleAll(context: Context) {
            val projectionStore = SchedulerProjectionStore(context.filesDir)
            val scheduler = AndroidAlarmScheduler(context)
            val now = System.currentTimeMillis()
            val allItems = projectionStore.getAll()

            var hasMissedEvents = false

            allItems.forEach { item ->
                if (!item.isEnabled) return@forEach

                val repeat = try {
                    item.repeatRule?.let { ReminderRepeat.valueOf(it) } ?: ReminderRepeat.ONCE
                } catch (e: Exception) {
                    ReminderRepeat.ONCE
                }

                if (item.fireEpochMs <= now) {
                    // Сигнал уже в прошлом (телефон был выключен / сменилось время)
                    hasMissedEvents = true
                    if (repeat != ReminderRepeat.ONCE) {
                        // Для повторяющегося вычисляем следующий будущий срок
                        val nextTrigger = ReminderTriggerCalculator.computeNextTrigger(
                            baseFireEpochMs = item.fireEpochMs,
                            repeat = repeat,
                            now = now,
                        )
                        if (nextTrigger != null) {
                            val updated = item.copy(fireEpochMs = nextTrigger)
                            projectionStore.upsert(updated)
                            scheduler.schedule(item.reminderId, nextTrigger)
                        }
                    } else {
                        // Разовое пропущенное напоминание: деактивируем
                        projectionStore.upsert(item.copy(isEnabled = false))
                    }
                } else {
                    // Будущий сигнал — планируем в системе
                    scheduler.schedule(item.reminderId, item.fireEpochMs)
                }
            }

            // Если были пропущенные события за время выключения, выдаем одно сгруппированное нейтральное уведомление
            if (hasMissedEvents) {
                ReminderNotificationHelper.showNeutralNotification(context, "missed_events_summary")
            }
        }
    }
}

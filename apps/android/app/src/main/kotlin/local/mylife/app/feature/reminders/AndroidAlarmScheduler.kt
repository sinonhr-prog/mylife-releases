package local.mylife.app.feature.reminders

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import local.mylife.core.storage.scheduler.SchedulerPort

class AndroidAlarmScheduler(
    private val context: Context,
) : SchedulerPort {

    private val alarmManager: AlarmManager? =
        context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager

    override fun canScheduleExact(): Boolean {
        if (alarmManager == null) return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    override fun schedule(reminderId: String, triggerAtEpochMs: Long) {
        if (alarmManager == null) return

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_FIRE_REMINDER
            putExtra(ReminderAlarmReceiver.EXTRA_REMINDER_ID, reminderId)
        }

        val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            flags,
        )

        val canExact = canScheduleExact()
        if (canExact) {
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtEpochMs,
                        pendingIntent,
                    )
                } else {
                    alarmManager.setExact(
                        AlarmManager.RTC_WAKEUP,
                        triggerAtEpochMs,
                        pendingIntent,
                    )
                }
            } catch (e: SecurityException) {
                // Fallback to inexact if exact permission was revoked or denied
                alarmManager.set(
                    AlarmManager.RTC_WAKEUP,
                    triggerAtEpochMs,
                    pendingIntent,
                )
            }
        } else {
            // Inexact fallback policy
            alarmManager.set(
                AlarmManager.RTC_WAKEUP,
                triggerAtEpochMs,
                pendingIntent,
            )
        }
    }

    override fun cancel(reminderId: String) {
        if (alarmManager == null) return

        val intent = Intent(context, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_FIRE_REMINDER
        }
        val flags = PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        val pendingIntent = PendingIntent.getBroadcast(
            context,
            reminderId.hashCode(),
            intent,
            flags,
        )

        if (pendingIntent != null) {
            alarmManager.cancel(pendingIntent)
            pendingIntent.cancel()
        }
    }
}

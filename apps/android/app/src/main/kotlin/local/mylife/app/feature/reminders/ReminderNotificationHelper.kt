package local.mylife.app.feature.reminders

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import local.mylife.MainActivity

object ReminderNotificationHelper {
    const val CHANNEL_ID = "mylife_reminders_channel"
    private const val CHANNEL_NAME = "Напоминания"
    private const val CHANNEL_DESC = "Уведомления о запланированных делах"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return
            val existing = manager.getNotificationChannel(CHANNEL_ID)
            if (existing == null) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    CHANNEL_NAME,
                    NotificationManager.IMPORTANCE_HIGH,
                ).apply {
                    description = CHANNEL_DESC
                    enableVibration(true)
                    lockscreenVisibility = NotificationCompat.VISIBILITY_PRIVATE
                }
                manager.createNotificationChannel(channel)
            }
        }
    }

    /**
     * Показывает нейтральное уведомление.
     * Строго соблюдается приватность: без персонального текста, заметок, названий дел или ключей.
     */
    fun showNeutralNotification(context: Context, reminderId: String) {
        ensureChannel(context)

        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
                ?: return

        val launchIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            launchIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        // Lockscreen и общее уведомление строго нейтральны
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Напоминание")
            .setContentText("У вас запланировано дело. Откройте приложение для просмотра.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("У вас запланировано дело. Откройте приложение для просмотра."))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        notificationManager.notify(reminderId.hashCode(), notification)
    }
}

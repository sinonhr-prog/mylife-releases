package local.mylife.app.feature.reminders

import android.app.AlarmManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationManagerCompat

data class DeviceDiagnostics(
    val areNotificationsEnabled: Boolean,
    val canScheduleExactAlarms: Boolean,
    val isVivoDevice: Boolean,
    val manufacturer: String,
    val model: String,
    val androidVersion: Int,
    val recommendations: List<String>,
)

object DeviceDiagnosticsHelper {

    fun checkDiagnostics(context: Context): DeviceDiagnostics {
        val notificationsEnabled = NotificationManagerCompat.from(context).areNotificationsEnabled()

        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager?.canScheduleExactAlarms() ?: false
        } else {
            true
        }

        val manufacturer = Build.MANUFACTURER.orEmpty()
        val isVivo = manufacturer.contains("vivo", ignoreCase = true) ||
                Build.BRAND.contains("vivo", ignoreCase = true)

        val recommendations = mutableListOf<String>()

        if (!notificationsEnabled) {
            recommendations.add("Разрешите уведомления в настройках системы, иначе напоминания не смогут отображаться.")
        }

        if (!canExact) {
            recommendations.add("Разрешите приложению «Будильники и напоминания» (точный запуск), иначе сигналы будут доставляться с задержкой системы.")
        }

        if (isVivo) {
            recommendations.add("На устройствах Vivo (Funtouch OS) для стабильной работы включите «Автозапуск» и снимите ограничения батареи («Без ограничений») в настройках приложения.")
        }

        recommendations.add("Важно: при принудительной остановке («Остановить») Android и Funtouch OS блокируют доставку всех сигналов до следующего ручного открытия.")

        return DeviceDiagnostics(
            areNotificationsEnabled = notificationsEnabled,
            canScheduleExactAlarms = canExact,
            isVivoDevice = isVivo,
            manufacturer = manufacturer,
            model = Build.MODEL.orEmpty(),
            androidVersion = Build.VERSION.SDK_INT,
            recommendations = recommendations,
        )
    }
}

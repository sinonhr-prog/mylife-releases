package local.mylife.app.feature.settings.updater

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageInstaller
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileInputStream

/**
 * Интеграция с Android PackageInstaller API для установки обновлений поверх текущей версии.
 *
 * Особенности:
 * - Требует явного подтверждения пользователя (STATUS_PENDING_USER_ACTION).
 * - Проверяет canRequestPackageInstalls().
 * - Записывает APK потоком в изолированную сессию PackageInstaller.
 * - Очищает временные файлы после завершения.
 */
class PackageInstallerHelper(private val context: Context) {

    companion object {
        const val ACTION_INSTALL_STATUS = "local.mylife.action.INSTALL_STATUS"
        const val EXTRA_SESSION_ID = "local.mylife.extra.SESSION_ID"
    }

    /**
     * Проверка наличия системного разрешения на установку APK.
     */
    fun canInstallPackages(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            true
        }
    }

    /**
     * Создание Intent для перехода в системные настройки выдачи разрешения.
     */
    fun createManageUnknownSourcesIntent(): Intent {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        } else {
            @Suppress("DEPRECATION")
            Intent(Settings.ACTION_SECURITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
        }
    }

    /**
     * Запуск сессии установки APK через PackageInstaller.
     */
    fun startInstallSession(
        apkFile: File,
        onStatus: (status: Int, message: String?, userActionIntent: Intent?) -> Unit,
    ): Result<Int> {
        return runCatching {
            if (!canInstallPackages()) {
                throw SecurityException("Приложению не предоставлено разрешение на установку обновлений")
            }

            val packageInstaller = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                setAppPackageName(context.packageName)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_REQUIRED)
                }
            }

            val sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)

            session.use { activeSession ->
                activeSession.openWrite("mylife_update", 0, apkFile.length()).use { output ->
                    FileInputStream(apkFile).use { input ->
                        input.copyTo(output)
                    }
                    activeSession.fsync(output)
                }

                // Регистрируем локальный receiver для отслеживания статуса
                registerStatusReceiver(context, sessionId, onStatus)

                val intent = Intent(ACTION_INSTALL_STATUS).apply {
                    setPackage(context.packageName)
                    putExtra(EXTRA_SESSION_ID, sessionId)
                }

                val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }

                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    flags,
                )

                activeSession.commit(pendingIntent.intentSender)
            }

            sessionId
        }
    }

    private fun registerStatusReceiver(
        context: Context,
        targetSessionId: Int,
        onStatus: (status: Int, message: String?, userActionIntent: Intent?) -> Unit,
    ) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context?, intent: Intent?) {
                if (intent == null || intent.action != ACTION_INSTALL_STATUS) return
                val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID, -1)
                if (sessionId != targetSessionId) return

                val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
                val message = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)

                when (status) {
                    PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                        val confirmIntent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
                        } else {
                            @Suppress("DEPRECATION")
                            intent.getParcelableExtra(Intent.EXTRA_INTENT)
                        }
                        if (confirmIntent != null) {
                            confirmIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            context.startActivity(confirmIntent)
                        }
                        onStatus(status, message, confirmIntent)
                    }
                    PackageInstaller.STATUS_SUCCESS -> {
                        context.unregisterReceiver(this)
                        onStatus(status, "Обновление успешно установлено", null)
                    }
                    else -> {
                        context.unregisterReceiver(this)
                        onStatus(status, message ?: "Сбой установки (код $status)", null)
                    }
                }
            }
        }

        ContextCompat.registerReceiver(
            context,
            receiver,
            IntentFilter(ACTION_INSTALL_STATUS),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
    }
}

package local.mylife.app.feature.vault

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

object SafeClipboardHelper {

    /**
     * Копирует конфиденциальное значение (пароль) с установкой системного флага IS_SENSITIVE (Android 13+)
     * и запускает автоматическую очистку буфера через [clearDelayMs] миллисекунд (по умолчанию 30 сек).
     */
    fun copySensitive(
        context: Context,
        label: String,
        sensitiveText: String,
        clearDelayMs: Long = 30000L,
    ) {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        val clip = ClipData.newPlainText(label, sensitiveText)

        // Android 13+ (API 33): флаг EXTRA_IS_SENSITIVE скрывает превью пароля во всплывающем системном оверлее
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            clip.description.extras = PersistableBundle().apply {
                putBoolean("android.content.extra.IS_SENSITIVE", true)
            }
        }

        clipboard.setPrimaryClip(clip)

        // Best effort auto-clear
        CoroutineScope(Dispatchers.Main).launch {
            delay(clearDelayMs)
            try {
                // Очищаем, только если в буфере все еще наш клип
                if (clipboard.hasPrimaryClip()) {
                    val currentClip = clipboard.primaryClip
                    if (currentClip != null && currentClip.itemCount > 0) {
                        val currentText = currentClip.getItemAt(0).text?.toString()
                        if (currentText == sensitiveText) {
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                                clipboard.clearPrimaryClip()
                            } else {
                                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                // Ignore clipboard cleanup failure
            }
        }
    }
}

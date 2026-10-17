package local.mylife.app.feature.settings.backup

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import local.mylife.core.storage.backup.ArchiveManifest
import local.mylife.core.storage.backup.LifeVaultArchiveService
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

sealed class BackupUiState {
    object Idle : BackupUiState()
    object InProgress : BackupUiState()
    data class ExportSuccess(val filePath: String, val dateText: String) : BackupUiState()
    data class InspectSuccess(val manifest: ArchiveManifest) : BackupUiState()
    object RestoreSuccess : BackupUiState()
    data class Error(val message: String) : BackupUiState()
}

class BackupViewModel(
    private val context: Context,
    private val mainDbKey: ByteArray,
    private val vaultDbKey: ByteArray,
    private val keyEnvelopeManager: local.mylife.core.security.crypto.KeyEnvelopeManager? = null,
    private val onRestoreCompleted: () -> Unit = {},
) : ViewModel() {

    private val _state = MutableStateFlow<BackupUiState>(BackupUiState.Idle)
    val state: StateFlow<BackupUiState> = _state.asStateFlow()

    private val archiveService = LifeVaultArchiveService(
        appFilesDir = context.filesDir,
        appDatabasesDir = context.getDatabasePath("main.db").parentFile ?: context.filesDir,
        cacheDir = context.cacheDir,
    )

    fun resetState() {
        _state.value = BackupUiState.Idle
    }

    /**
     * Создает зашифрованный .lifevault архив с пользовательской ключевой фразой
     */
    fun createBackup(passphrase: String, targetUri: Uri? = null) {
        if (passphrase.length < 8) {
            _state.value = BackupUiState.Error("Ключевая фраза должна содержать не менее 8 символов")
            return
        }

        viewModelScope.launch {
            _state.value = BackupUiState.InProgress
            withContext(Dispatchers.IO) {
                try {
                    val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
                    val backupFile = File(context.cacheDir, "mylife_backup_$timestamp.lifevault")

                    FileOutputStream(backupFile).use { fos ->
                        archiveService.exportArchive(
                            passphrase = passphrase,
                            outputStream = fos,
                            mainDbKey = mainDbKey,
                            vaultDbKey = vaultDbKey,
                        )
                    }

                    // Если указан URI от SAF (USB/диск) — копируем туда
                    if (targetUri != null) {
                        context.contentResolver.openOutputStream(targetUri)?.use { outStream ->
                            backupFile.inputStream().use { inStream ->
                                inStream.copyTo(outStream)
                            }
                        }
                    }

                    val dateText = SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")).format(Date())
                    _state.value = BackupUiState.ExportSuccess(backupFile.absolutePath, dateText)
                } catch (e: Exception) {
                    _state.value = BackupUiState.Error("Ошибка создания архива: ${e.message}")
                }
            }
        }
    }

    /**
     * Проверяет и считывает манифест архива для подтверждения перед заменой данных
     */
    fun inspectArchive(archiveUri: Uri, passphrase: String) {
        if (passphrase.length < 8) {
            _state.value = BackupUiState.Error("Ключевая фраза должна содержать не менее 8 символов")
            return
        }

        viewModelScope.launch {
            _state.value = BackupUiState.InProgress
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(archiveUri)
                        ?: throw IllegalStateException("Не удалось открыть файл архива")

                    val manifest = inputStream.use { stream ->
                        archiveService.inspectArchive(passphrase, stream)
                    }

                    _state.value = BackupUiState.InspectSuccess(manifest)
                } catch (e: Exception) {
                    _state.value = BackupUiState.Error("Ошибка проверки архива (неверная фраза или повреждённый файл): ${e.message}")
                }
            }
        }
    }

    /**
     * Выполняет восстановление с заменой данных из архива (требует подтверждения пользователя)
     */
    fun restoreArchive(
        archiveUri: Uri,
        passphrase: String,
        newPin: String,
        newVaultPin: String,
    ) {
        val envelopeManager = keyEnvelopeManager
            ?: (context.applicationContext as? local.mylife.app.MyLifeApp)?.container?.keyEnvelopeManager
            ?: throw IllegalStateException("KeyEnvelopeManager недоступен для восстановления")

        viewModelScope.launch {
            _state.value = BackupUiState.InProgress
            withContext(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(archiveUri)
                        ?: throw IllegalStateException("Не удалось открыть файл архива")

                    inputStream.use { stream ->
                        archiveService.restoreArchive(
                            passphrase = passphrase,
                            inputStream = stream,
                            targetPin = newPin,
                            targetKeyEnvelopeManager = envelopeManager,
                        )
                    }

                    // Пересоздаем проекцию напоминаний после восстановления
                    local.mylife.app.feature.reminders.BootAndChangeReceiver.rescheduleAll(context)

                    _state.value = BackupUiState.RestoreSuccess
                    withContext(Dispatchers.Main) {
                        onRestoreCompleted()
                    }
                } catch (e: Exception) {
                    _state.value = BackupUiState.Error("Ошибка восстановления: ${e.message}")
                }
            }
        }
    }
}

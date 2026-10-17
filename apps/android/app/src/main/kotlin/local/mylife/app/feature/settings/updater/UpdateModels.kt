package local.mylife.app.feature.settings.updater

import java.io.File

/**
 * Описание доступного официального релиза «Моя жизнь».
 */
data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val releaseNotes: String,
    val apkUrl: String,
    val sha256: String,
    val sizeBytes: Long,
    val channel: String = "stable",
    val publishedAt: String = "",
)

/**
 * Состояние процесса проверки, загрузки и установки обновлений.
 */
sealed interface UpdateState {
    /** Исходное состояние */
    object Idle : UpdateState

    /** Выполняется проверка наличия новых версий */
    object Checking : UpdateState

    /** Найдена новая проверенная версия */
    data class UpdateAvailable(val info: UpdateInfo) : UpdateState

    /** Установлена актуальная версия */
    data class UpToDate(val currentVersionCode: Int, val versionName: String) : UpdateState

    /** Процесс скачивания файла APK */
    data class Downloading(
        val progressPercent: Int,
        val downloadedBytes: Long,
        val totalBytes: Long,
    ) : UpdateState

    /** APK скачан, хэш SHA-256 и подпись проверены, готов к установке */
    data class ReadyToInstall(
        val apkFile: File,
        val info: UpdateInfo,
    ) : UpdateState

    /** Передано в системный PackageInstaller Android */
    object Installing : UpdateState

    /** Требуется предоставить системное разрешение на установку из неизвестных источников */
    object PermissionRequired : UpdateState

    /** Ошибка с понятным описанием */
    data class Error(
        val message: String,
        val canRetry: Boolean = true,
    ) : UpdateState
}

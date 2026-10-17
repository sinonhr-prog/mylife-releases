package local.mylife.app.feature.settings.updater

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File

/**
 * Публичный интерфейс репозитория управления обновлениями.
 */
interface UpdateRepository {
    val state: StateFlow<UpdateState>

    fun getCurrentVersionCode(): Int

    fun getCurrentVersionName(): String

    suspend fun checkForUpdates(): Result<UpdateInfo?>

    suspend fun downloadUpdate(info: UpdateInfo): Result<File>

    fun installUpdate(
        apkFile: File,
        info: UpdateInfo,
    ): Result<Unit>

    fun refreshInstallPermission() {}

    fun cancel()

    fun dismissError()
}

/**
 * Реализация UpdateRepository, объединяющая GitHubReleaseClient, ApkVerifier и PackageInstallerHelper.
 */
class DefaultUpdateRepository(
    private val context: Context,
    private val releaseClient: GitHubReleaseClient = GitHubReleaseClient(),
    private val verifier: ApkVerifier = ApkVerifier(context),
    private val installerHelper: PackageInstallerHelper = PackageInstallerHelper(context),
) : UpdateRepository {
    private var pendingInstall: UpdateState.ReadyToInstall? = null
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    override val state: StateFlow<UpdateState> = _state.asStateFlow()

    override fun getCurrentVersionCode(): Int {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            pi.longVersionCode.toInt()
        } else {
            @Suppress("DEPRECATION")
            pi.versionCode
        }
    }

    override fun getCurrentVersionName(): String {
        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
        return pi.versionName ?: "0.1.0"
    }

    override suspend fun checkForUpdates(): Result<UpdateInfo?> {
        _state.value = UpdateState.Checking
        val currentCode = getCurrentVersionCode()
        val currentName = getCurrentVersionName()

        val result = releaseClient.getLatestRelease()
        return result.fold(
            onSuccess = { info ->
                if (info == null || info.versionCode <= currentCode) {
                    _state.value = UpdateState.UpToDate(currentCode, currentName)
                    Result.success(null)
                } else {
                    _state.value = UpdateState.UpdateAvailable(info)
                    Result.success(info)
                }
            },
            onFailure = { error ->
                _state.value =
                    UpdateState.Error(
                        message = error.message ?: "Не удалось проверить обновления",
                        canRetry = true,
                    )
                Result.failure(error)
            },
        )
    }

    override suspend fun downloadUpdate(info: UpdateInfo): Result<File> {
        _state.value =
            UpdateState.Downloading(
                progressPercent = 0,
                downloadedBytes = 0,
                totalBytes = info.sizeBytes,
            )

        val updatesDir = File(context.cacheDir, "updates").apply { mkdirs() }
        val targetApk = File(updatesDir, "mylife-${info.versionCode}.apk")

        val downloadResult =
            releaseClient.downloadApk(
                apkUrl = info.apkUrl,
                targetFile = targetApk,
                expectedSha256 = info.sha256,
                expectedSize = info.sizeBytes,
                onProgress = { downloaded, total ->
                    val percent = if (total > 0) ((downloaded * 100) / total).toInt().coerceIn(0, 100) else 0
                    _state.value =
                        UpdateState.Downloading(
                            progressPercent = percent,
                            downloadedBytes = downloaded,
                            totalBytes = total,
                        )
                },
            )

        return downloadResult.fold(
            onSuccess = { file ->
                // Верификация скачанного APK
                val verification = verifier.verify(file, expectedVersionCode = info.versionCode)
                if (!verification.isValid) {
                    file.delete()
                    val errorMsg = verification.errorMessage ?: "Проверка безопасности APK не пройдена"
                    _state.value = UpdateState.Error(errorMsg, canRetry = false)
                    Result.failure(SecurityException(errorMsg))
                } else {
                    _state.value = UpdateState.ReadyToInstall(file, info)
                    Result.success(file)
                }
            },
            onFailure = { error ->
                _state.value =
                    UpdateState.Error(
                        message = error.message ?: "Ошибка скачивания файла обновления",
                        canRetry = true,
                    )
                Result.failure(error)
            },
        )
    }

    override fun installUpdate(
        apkFile: File,
        info: UpdateInfo,
    ): Result<Unit> {
        pendingInstall = UpdateState.ReadyToInstall(apkFile, info)
        if (!installerHelper.canInstallPackages()) {
            _state.value = UpdateState.PermissionRequired
            return Result.failure(SecurityException("Требуется разрешение на установку из неизвестных источников"))
        }

        _state.value = UpdateState.Installing
        val sessionResult =
            installerHelper.startInstallSession(
                apkFile = apkFile,
                onStatus = { status, message, userActionIntent ->
                    when (status) {
                        PackageInstaller.STATUS_PENDING_USER_ACTION -> {
                            // Ожидание действия пользователя в системном окне
                        }
                        PackageInstaller.STATUS_SUCCESS -> {
                            _state.value = UpdateState.Idle
                            // Очистка временного файла
                            apkFile.delete()
                        }
                        else -> {
                            _state.value =
                                UpdateState.Error(
                                    message = message ?: "Установка отменена или завершилась с ошибкой",
                                    canRetry = true,
                                )
                        }
                    }
                },
            )

        return sessionResult.map { Unit }
    }

    override fun refreshInstallPermission() {
        val current = _state.value
        if (current is UpdateState.PermissionRequired && installerHelper.canInstallPackages()) {
            pendingInstall?.let { ready ->
                if (ready.apkFile.isFile) _state.value = ready else _state.value = UpdateState.Idle
            }
        }
    }

    override fun cancel() {
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Checking) {
            _state.value = UpdateState.Idle
        }
    }

    override fun dismissError() {
        _state.value = UpdateState.Idle
    }
}

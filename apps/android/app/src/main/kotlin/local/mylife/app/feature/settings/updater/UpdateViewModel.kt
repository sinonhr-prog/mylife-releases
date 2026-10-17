package local.mylife.app.feature.settings.updater

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import java.io.File

/**
 * ViewModel для экрана «Настройки → Обновления».
 */
class UpdateViewModel(
    private val repository: UpdateRepository,
) : ViewModel() {

    val state: StateFlow<UpdateState> = repository.state

    val currentVersionCode: Int = repository.getCurrentVersionCode()
    val currentVersionName: String = repository.getCurrentVersionName()

    fun checkForUpdates() {
        viewModelScope.launch {
            repository.checkForUpdates()
        }
    }

    fun downloadUpdate(info: UpdateInfo) {
        viewModelScope.launch {
            repository.downloadUpdate(info)
        }
    }

    fun installUpdate(apkFile: File, info: UpdateInfo) {
        repository.installUpdate(apkFile, info)
    }

    fun cancel() {
        repository.cancel()
    }

    fun dismissError() {
        repository.dismissError()
    }
}

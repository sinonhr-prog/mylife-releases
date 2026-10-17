package local.mylife.app.feature.reminders

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import local.mylife.core.model.ReminderItem
import local.mylife.core.model.ReminderRepeat
import local.mylife.core.model.ReminderRepository
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskRepository
import local.mylife.core.storage.scheduler.SchedulerReconciler
import java.util.UUID

// An unfinished form is not a persisted ReminderItem: its title may be empty.
data class ReminderDraft(
    val id: String = UUID.randomUUID().toString(),
    val taskId: String? = null,
    val title: String = "",
    val fireEpochMs: Long = System.currentTimeMillis() + 3600000L,
    val repeat: ReminderRepeat = ReminderRepeat.ONCE,
    val isEnabled: Boolean = true,
    val createdAtEpochMs: Long = System.currentTimeMillis(),
)

data class RemindersUiState(
    val reminders: List<ReminderItem> = emptyList(),
    val availableTasks: List<TaskItem> = emptyList(),
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showAddDialog: Boolean = false,
    val editingReminder: ReminderDraft? = null,
    val diagnostics: DeviceDiagnostics? = null,
)

class RemindersViewModel(
    private val reminderRepository: ReminderRepository,
    private val taskRepository: TaskRepository? = null,
    private val schedulerReconciler: SchedulerReconciler? = null,
) : ViewModel() {

    private val _uiState = MutableStateFlow(RemindersUiState())
    val uiState: StateFlow<RemindersUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val list = reminderRepository.getAllReminders()
                val tasks = taskRepository?.getOpenTasks() ?: emptyList()
                // Reconcile outbox if reconciler provided
                schedulerReconciler?.reconcile()
                _uiState.value = _uiState.value.copy(
                    reminders = list,
                    availableTasks = tasks,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Не удалось загрузить напоминания: ${e.message}",
                )
            }
        }
    }

    fun setDiagnostics(diagnostics: DeviceDiagnostics) {
        _uiState.value = _uiState.value.copy(diagnostics = diagnostics)
    }

    fun openAddReminder(linkedTaskId: String? = null) {
        val now = System.currentTimeMillis()
        val defaultFire = now + (3600 * 1000L) // +1 час по умолчанию
        val newReminder = ReminderDraft(
            id = UUID.randomUUID().toString(),
            taskId = linkedTaskId,
            title = "",
            fireEpochMs = defaultFire,
            repeat = ReminderRepeat.ONCE,
            isEnabled = true,
            createdAtEpochMs = now,
        )
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            editingReminder = newReminder,
            errorMessage = null,
        )
    }

    fun openEditReminder(reminder: ReminderItem) {
        _uiState.value = _uiState.value.copy(
            showAddDialog = true,
            editingReminder = ReminderDraft(
                id = reminder.id,
                taskId = reminder.taskId,
                title = reminder.title,
                fireEpochMs = reminder.fireEpochMs,
                repeat = reminder.repeat,
                isEnabled = reminder.isEnabled,
                createdAtEpochMs = reminder.createdAtEpochMs,
            ),
            errorMessage = null,
        )
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(
            showAddDialog = false,
            editingReminder = null,
        )
    }

    fun saveReminder(
        title: String,
        fireEpochMs: Long,
        repeat: ReminderRepeat,
        linkedTaskId: String?,
    ) {
        val current = _uiState.value.editingReminder ?: return
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Название напоминания не может быть пустым")
            return
        }

        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val updated = ReminderItem(
                    id = current.id,
                    isEnabled = current.isEnabled,
                    createdAtEpochMs = current.createdAtEpochMs,
                    title = trimmedTitle,
                    fireEpochMs = fireEpochMs,
                    repeatRule = repeat.name,
                    taskId = linkedTaskId,
                    updatedAtEpochMs = now,
                )
                reminderRepository.saveReminder(updated)
                schedulerReconciler?.reconcile(now)
                _uiState.value = _uiState.value.copy(
                    showAddDialog = false,
                    editingReminder = null,
                )
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ошибка сохранения напоминания: ${e.message}",
                )
            }
        }
    }

    fun toggleReminder(reminder: ReminderItem) {
        viewModelScope.launch {
            try {
                val newEnabled = !reminder.isEnabled
                reminderRepository.toggleReminder(reminder.id, newEnabled)
                schedulerReconciler?.reconcile()
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ошибка изменения статуса: ${e.message}",
                )
            }
        }
    }

    fun deleteReminder(id: String) {
        viewModelScope.launch {
            try {
                reminderRepository.deleteReminder(id)
                schedulerReconciler?.reconcile()
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ошибка удаления: ${e.message}",
                )
            }
        }
    }
}

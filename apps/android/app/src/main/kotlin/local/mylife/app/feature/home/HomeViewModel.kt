package local.mylife.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.mylife.core.model.DashboardRepository
import local.mylife.core.model.Place
import local.mylife.core.model.Product
import local.mylife.core.model.RecentRecord
import local.mylife.core.model.ReminderItem
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeUiState(
    val nearestTask: TaskItem? = null,
    val remainingTasks: List<TaskItem> = emptyList(),
    val recentPurchases: List<Product> = emptyList(),
    val recentVisits: List<Place> = emptyList(),
    val recentRecords: List<RecentRecord> = emptyList(),
    val activeReminders: List<ReminderItem> = emptyList(),
    val currentDateText: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class HomeViewModel(
    private val dashboardRepository: DashboardRepository,
    private val taskRepository: TaskRepository,
    private val externalScope: kotlinx.coroutines.CoroutineScope? = null,
) : ViewModel() {

    private val scope get() = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(HomeUiState())
    val uiState: StateFlow<HomeUiState> = _uiState.asStateFlow()

    init {
        updateDateText()
        loadDashboardData()
    }

    fun updateDateText() {
        val sdf = SimpleDateFormat("d MMMM · EEEE", Locale("ru"))
        val dateStr = sdf.format(Date())
        _uiState.update { it.copy(currentDateText = dateStr) }
    }

    fun loadDashboardData() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val summary = dashboardRepository.getDashboardSummary()
                _uiState.update {
                    it.copy(
                        nearestTask = summary.focusTask,
                        remainingTasks = summary.remainingTasks,
                        recentVisits = summary.recentVisits,
                        recentRecords = summary.recentRecords,
                        activeReminders = summary.activeReminders,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun markTaskDone(taskId: String) {
        scope.launch {
            try {
                taskRepository.markTaskDone(taskId)
                loadDashboardData()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun createQuickTask(title: String, description: String = "") {
        scope.launch {
            try {
                val task = TaskItem(
                    id = java.util.UUID.randomUUID().toString(),
                    title = title.trim(),
                    description = description.trim(),
                    createdAtEpochMs = System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
                taskRepository.saveTask(task)
                loadDashboardData()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }
}

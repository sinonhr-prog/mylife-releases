package local.mylife.app.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.mylife.core.model.Product
import local.mylife.core.model.ProductRepository
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class HomeUiState(
    val nearestTask: TaskItem? = null,
    val remainingTasks: List<TaskItem> = emptyList(),
    val recentPurchases: List<Product> = emptyList(),
    val currentDateText: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class HomeViewModel(
    private val taskRepository: TaskRepository,
    private val productRepository: ProductRepository,
) : ViewModel() {

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
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val openTasks = taskRepository.getOpenTasks()
                val sortedTasks = openTasks.sortedWith(
                    compareBy<TaskItem> { it.dueAtEpochMs ?: Long.MAX_VALUE }
                        .thenByDescending { it.priority.ordinal },
                )

                val nearest = sortedTasks.firstOrNull()
                val remaining = if (sortedTasks.size > 1) sortedTasks.drop(1) else emptyList()

                val products = productRepository.getProducts(includeArchived = false)
                val recentProducts = products.take(3)

                _uiState.update {
                    it.copy(
                        nearestTask = nearest,
                        remainingTasks = remaining,
                        recentPurchases = recentProducts,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message) }
            }
        }
    }

    fun markTaskDone(taskId: String) {
        viewModelScope.launch {
            try {
                taskRepository.markTaskDone(taskId)
                loadDashboardData()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun createQuickTask(title: String, description: String = "") {
        viewModelScope.launch {
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

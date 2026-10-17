package local.mylife.app.feature.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskPriority
import local.mylife.core.model.TaskRepository
import local.mylife.core.model.TaskStatus
import java.util.UUID

enum class TaskFilter {
    OPEN,
    ALL,
    DONE,
    HIGH_PRIORITY,
}

data class TasksUiState(
    val searchQuery: String = "",
    val filter: TaskFilter = TaskFilter.OPEN,
    val allTasks: List<TaskItem> = emptyList(),
    val filteredTasks: List<TaskItem> = emptyList(),
    val selectedTask: TaskItem? = null,
    val isAddEditSheetOpen: Boolean = false,
    val taskToEdit: TaskItem? = null,
    val draftTitle: String = "",
    val draftDescription: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class TasksViewModel(
    private val taskRepository: TaskRepository,
    private val externalScope: kotlinx.coroutines.CoroutineScope? = null,
) : ViewModel() {

    private val scope get() = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(TasksUiState())
    val uiState: StateFlow<TasksUiState> = _uiState.asStateFlow()

    init {
        loadTasks()
    }

    fun loadTasks() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val tasks = taskRepository.getAllTasks()
                val sorted = sortTasks(tasks)
                _uiState.update { current ->
                    val filtered = applyFilter(sorted, current.searchQuery, current.filter)
                    current.copy(
                        allTasks = sorted,
                        filteredTasks = filtered,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки задач") }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val filtered = applyFilter(current.allTasks, query, current.filter)
            current.copy(searchQuery = query, filteredTasks = filtered)
        }
    }

    fun onFilterSelected(filter: TaskFilter) {
        _uiState.update { current ->
            val filtered = applyFilter(current.allTasks, current.searchQuery, filter)
            current.copy(filter = filter, filteredTasks = filtered)
        }
    }

    fun openTaskDetails(task: TaskItem) {
        _uiState.update { it.copy(selectedTask = task) }
    }

    fun closeTaskDetails() {
        _uiState.update { it.copy(selectedTask = null) }
    }

    fun openAddTask() {
        _uiState.update {
            it.copy(
                isAddEditSheetOpen = true,
                taskToEdit = null,
                draftTitle = "",
                draftDescription = "",
            )
        }
    }

    fun openEditTask(task: TaskItem) {
        _uiState.update {
            it.copy(
                isAddEditSheetOpen = true,
                taskToEdit = task,
                draftTitle = task.title,
                draftDescription = task.description,
            )
        }
    }

    fun updateDraft(title: String, description: String) {
        _uiState.update { it.copy(draftTitle = title, draftDescription = description) }
    }

    fun closeAddEditSheet() {
        _uiState.update { it.copy(isAddEditSheetOpen = false, taskToEdit = null) }
    }

    fun saveTask(
        title: String,
        description: String = "",
        dueAtEpochMs: Long? = null,
        priority: TaskPriority = TaskPriority.NORMAL,
    ) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val cleanTitle = title.trim()
                require(cleanTitle.isNotBlank()) { "Заголовок задачи обязателен" }
                require(cleanTitle.length <= 160) { "Заголовок не может превышать 160 символов" }

                val currentToEdit = _uiState.value.taskToEdit
                val taskId = currentToEdit?.id ?: UUID.randomUUID().toString()

                val task = TaskItem(
                    id = taskId,
                    title = cleanTitle,
                    description = description.trim(),
                    dueAtEpochMs = dueAtEpochMs,
                    status = currentToEdit?.status ?: TaskStatus.OPEN,
                    priority = priority,
                    completedAtEpochMs = currentToEdit?.completedAtEpochMs,
                    createdAtEpochMs = currentToEdit?.createdAtEpochMs ?: System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                )

                taskRepository.saveTask(task)
                _uiState.update {
                    it.copy(
                        isAddEditSheetOpen = false,
                        taskToEdit = null,
                        draftTitle = "",
                        draftDescription = "",
                    )
                }
                loadTasks()

                if (_uiState.value.selectedTask?.id == taskId) {
                    _uiState.update { it.copy(selectedTask = task) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка сохранения задачи") }
            }
        }
    }

    fun toggleTaskStatus(task: TaskItem) {
        scope.launch {
            try {
                if (task.status == TaskStatus.OPEN) {
                    taskRepository.markTaskDone(task.id)
                } else {
                    val updated = task.copy(
                        status = TaskStatus.OPEN,
                        completedAtEpochMs = null,
                        updatedAtEpochMs = System.currentTimeMillis(),
                    )
                    taskRepository.saveTask(updated)
                }
                loadTasks()
                if (_uiState.value.selectedTask?.id == task.id) {
                    val fresh = taskRepository.getTaskById(task.id)
                    _uiState.update { it.copy(selectedTask = fresh) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun deleteTask(taskId: String) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                taskRepository.deleteTask(taskId)
                if (_uiState.value.selectedTask?.id == taskId) {
                    _uiState.update { it.copy(selectedTask = null) }
                }
                loadTasks()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка удаления задачи") }
            }
        }
    }

    private fun sortTasks(tasks: List<TaskItem>): List<TaskItem> {
        val now = System.currentTimeMillis()
        return tasks.sortedWith { a, b ->
            // 1. Status: OPEN before DONE
            if (a.status != b.status) {
                return@sortedWith if (a.status == TaskStatus.OPEN) -1 else 1
            }

            // If both OPEN:
            if (a.status == TaskStatus.OPEN) {
                val aDue = a.dueAtEpochMs
                val bDue = b.dueAtEpochMs
                val aOverdue = aDue != null && aDue < now
                val bOverdue = bDue != null && bDue < now

                // Overdue first
                if (aOverdue != bOverdue) {
                    return@sortedWith if (aOverdue) -1 else 1
                }

                // Has due date before null due date
                val aHasDue = aDue != null
                val bHasDue = bDue != null
                if (aHasDue != bHasDue) {
                    return@sortedWith if (aHasDue) -1 else 1
                }

                // Compare due dates ascending
                if (aDue != null && bDue != null && aDue != bDue) {
                    return@sortedWith aDue.compareTo(bDue)
                }

                // Priority tie-breaker: HIGH > NORMAL > LOW
                if (a.priority != b.priority) {
                    return@sortedWith b.priority.ordinal.compareTo(a.priority.ordinal)
                }

                // Stable tie breaker: createdAt, then id
                val createdCmp = a.createdAtEpochMs.compareTo(b.createdAtEpochMs)
                if (createdCmp != 0) createdCmp else a.id.compareTo(b.id)
            } else {
                // If both DONE: completedAt descending
                val aComp = a.completedAtEpochMs ?: 0L
                val bComp = b.completedAtEpochMs ?: 0L
                bComp.compareTo(aComp)
            }
        }
    }

    private fun applyFilter(tasks: List<TaskItem>, query: String, filter: TaskFilter): List<TaskItem> {
        val q = query.trim().lowercase()
        return tasks.filter { task ->
            val matchesFilter = when (filter) {
                TaskFilter.OPEN -> task.status == TaskStatus.OPEN
                TaskFilter.ALL -> true
                TaskFilter.DONE -> task.status == TaskStatus.DONE
                TaskFilter.HIGH_PRIORITY -> task.priority == TaskPriority.HIGH
            }
            val matchesQuery = q.isBlank() ||
                task.title.lowercase().contains(q) ||
                task.description.lowercase().contains(q)

            matchesFilter && matchesQuery
        }
    }
}

package local.mylife.app.feature.tasks

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.mylife.core.designsystem.components.FocusEmptyState
import local.mylife.core.designsystem.theme.PaperBackground
import local.mylife.core.designsystem.theme.PaperForeground
import local.mylife.core.designsystem.theme.PaperOnSurfaceVariant
import local.mylife.core.designsystem.theme.PaperOutline
import local.mylife.core.designsystem.theme.PaperSurface
import local.mylife.core.model.TaskItem
import local.mylife.core.model.TaskPriority
import local.mylife.core.model.TaskStatus
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    viewModel: TasksViewModel,
    modifier: Modifier = Modifier,
    onAddNewRequested: () -> Unit = { viewModel.openAddTask() },
) {
    val state by viewModel.uiState.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PaperBackground),
    ) {
        // 1. Search Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("Поиск задач...", color = PaperOnSurfaceVariant) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = PaperForeground,
                    unfocusedBorderColor = PaperOutline,
                    focusedTextColor = PaperForeground,
                    unfocusedTextColor = PaperForeground,
                ),
                trailingIcon = {
                    if (state.searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onSearchQueryChanged("") }) {
                            Text("✕", color = PaperOnSurfaceVariant, fontSize = 16.sp)
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { contentDescription = "Поле поиска задач" },
            )
        }

        // 2. Filter Chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val filters = listOf(
                TaskFilter.OPEN to "Текущие",
                TaskFilter.ALL to "Все",
                TaskFilter.DONE to "Выполненные",
                TaskFilter.HIGH_PRIORITY to "Важные",
            )
            filters.forEach { (filter, title) ->
                FilterChip(
                    selected = state.filter == filter,
                    onClick = { viewModel.onFilterSelected(filter) },
                    label = { Text(title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PaperForeground,
                        selectedLabelColor = Color.White,
                        containerColor = PaperSurface,
                        labelColor = PaperForeground,
                    ),
                    border = BorderStroke(1.dp, if (state.filter == filter) PaperForeground else PaperOutline),
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }

        // 3. List or Empty State
        if (state.isLoading && state.allTasks.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PaperForeground)
            }
        } else if (state.filteredTasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                val emptyDesc = if (state.searchQuery.isNotBlank()) {
                    "По запросу «${state.searchQuery}» ничего не найдено."
                } else if (state.filter == TaskFilter.OPEN) {
                    "Все текущие дела выполнены. Спокойный день!"
                } else {
                    "Список задач пуст."
                }
                FocusEmptyState(
                    title = if (state.searchQuery.isNotBlank()) "Ничего не найдено" else "Нет задач",
                    description = emptyDesc,
                    actionText = "+ Добавить задачу",
                    onActionClick = onAddNewRequested,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
            ) {
                items(state.filteredTasks, key = { it.id }) { task ->
                    TaskCard(
                        task = task,
                        onClick = { viewModel.openTaskDetails(task) },
                        onToggleDone = { viewModel.toggleTaskStatus(task) },
                    )
                }
            }
        }
    }

    // Task Details Dialog
    state.selectedTask?.let { task ->
        TaskDetailsDialog(
            task = task,
            onDismiss = { viewModel.closeTaskDetails() },
            onToggleStatus = { viewModel.toggleTaskStatus(task) },
            onEdit = {
                viewModel.closeTaskDetails()
                viewModel.openEditTask(task)
            },
            onDelete = {
                viewModel.deleteTask(task.id)
            },
        )
    }

    // Add / Edit Dialog with Safe Drafts
    if (state.isAddEditSheetOpen) {
        AddEditTaskDialog(
            taskToEdit = state.taskToEdit,
            initialTitle = state.draftTitle,
            initialDescription = state.draftDescription,
            onDraftChanged = { title, desc -> viewModel.updateDraft(title, desc) },
            onDismiss = { viewModel.closeAddEditSheet() },
            onSave = { title, desc, dueAt, priority ->
                viewModel.saveTask(title, desc, dueAt, priority)
            },
        )
    }
}

@Composable
fun TaskCard(
    task: TaskItem,
    onClick: () -> Unit,
    onToggleDone: () -> Unit,
) {
    val isDone = task.status == TaskStatus.DONE
    val now = System.currentTimeMillis()
    val dueAt = task.dueAtEpochMs
    val isOverdue = !isDone && dueAt != null && dueAt < now

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PaperSurface),
        border = BorderStroke(1.dp, if (isOverdue) Color(0xFFD32F2F) else PaperOutline),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Status checkbox/circle button
            Surface(
                onClick = onToggleDone,
                shape = CircleShape,
                color = if (isDone) PaperForeground else Color.Transparent,
                border = BorderStroke(2.dp, if (isDone) PaperForeground else PaperOnSurfaceVariant),
                modifier = Modifier.size(24.dp),
            ) {
                if (isDone) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("✓", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isDone) PaperOnSurfaceVariant else PaperForeground,
                        textDecoration = if (isDone) TextDecoration.LineThrough else null,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )

                    if (task.priority == TaskPriority.HIGH && !isDone) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Surface(
                            shape = RoundedCornerShape(4.dp),
                            color = PaperForeground,
                        ) {
                            Text(
                                text = "ВАЖНО",
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
                            )
                        }
                    }
                }

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = task.description,
                        fontSize = 12.sp,
                        color = PaperOnSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                if (dueAt != null) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                    val dueText = (if (isOverdue) "Просрочено: " else "Срок: ") + sdf.format(Date(dueAt))
                    Text(
                        text = dueText,
                        fontSize = 11.sp,
                        fontWeight = if (isOverdue) FontWeight.Bold else FontWeight.Normal,
                        color = if (isOverdue) Color(0xFFD32F2F) else PaperOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun TaskDetailsDialog(
    task: TaskItem,
    onDismiss: () -> Unit,
    onToggleStatus: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val isDone = task.status == TaskStatus.DONE

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = task.title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PaperForeground,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Статус:", fontSize = 13.sp, color = PaperOnSurfaceVariant)
                    Text(
                        text = if (isDone) "Выполнено" else "К выполнению",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaperForeground,
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("Приоритет:", fontSize = 13.sp, color = PaperOnSurfaceVariant)
                    val prioText = when (task.priority) {
                        TaskPriority.HIGH -> "Высокий"
                        TaskPriority.NORMAL -> "Обычный"
                        TaskPriority.LOW -> "Низкий"
                    }
                    Text(prioText, fontSize = 13.sp, color = PaperForeground)
                }

                task.dueAtEpochMs?.let { due ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text("Срок:", fontSize = 13.sp, color = PaperOnSurfaceVariant)
                        val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                        Text(sdf.format(Date(due)), fontSize = 13.sp, color = PaperForeground)
                    }
                }

                if (task.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text("Описание:", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PaperOnSurfaceVariant)
                    Text(task.description, fontSize = 14.sp, color = PaperForeground)
                }

                Spacer(modifier = Modifier.height(10.dp))
                OutlinedButton(
                    onClick = onToggleStatus,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(10.dp),
                    border = BorderStroke(1.dp, PaperForeground),
                ) {
                    Text(if (isDone) "Вернуть в работу" else "Отметить выполненной", color = PaperForeground)
                }
            }
        },
        confirmButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showDeleteConfirm = true },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color(0xFFD32F2F)),
                    border = BorderStroke(1.dp, Color(0xFFD32F2F)),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Удалить")
                }
                Button(
                    onClick = onEdit,
                    colors = ButtonDefaults.buttonColors(containerColor = PaperForeground),
                    shape = RoundedCornerShape(10.dp),
                ) {
                    Text("Изменить", color = Color.White)
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = PaperForeground)
            }
        },
        containerColor = PaperBackground,
    )

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("Удалить задачу?") },
            text = { Text("Задача будет удалена без возможности восстановления.") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirm = false
                        onDismiss()
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                ) {
                    Text("Удалить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Отмена", color = PaperForeground)
                }
            },
            containerColor = PaperBackground,
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditTaskDialog(
    taskToEdit: TaskItem?,
    initialTitle: String,
    initialDescription: String,
    onDraftChanged: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (title: String, desc: String, dueAt: Long?, priority: TaskPriority) -> Unit,
) {
    var title by remember { mutableStateOf(if (taskToEdit != null) taskToEdit.title else initialTitle) }
    var description by remember { mutableStateOf(if (taskToEdit != null) taskToEdit.description else initialDescription) }
    var priority by remember { mutableStateOf(taskToEdit?.priority ?: TaskPriority.NORMAL) }
    var dueAtEpochMs by remember { mutableStateOf(taskToEdit?.dueAtEpochMs) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (taskToEdit == null) "Новая задача" else "Редактировать задачу",
                fontWeight = FontWeight.Bold,
                color = PaperForeground,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                error?.let { Text(it, color = Color(0xFFD32F2F), fontSize = 12.sp) }

                // Title
                OutlinedTextField(
                    value = title,
                    onValueChange = {
                        title = it
                        onDraftChanged(it, description)
                    },
                    label = { Text("Заголовок задачи *") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PaperForeground,
                        unfocusedBorderColor = PaperOutline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = {
                        description = it
                        onDraftChanged(title, it)
                    },
                    label = { Text("Описание (необязательно)") },
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PaperForeground,
                        unfocusedBorderColor = PaperOutline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Priority
                Text("Приоритет", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PaperOnSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val priorities = listOf(
                        TaskPriority.LOW to "Низкий",
                        TaskPriority.NORMAL to "Обычный",
                        TaskPriority.HIGH to "Высокий",
                    )
                    priorities.forEach { (prio, label) ->
                        FilterChip(
                            selected = priority == prio,
                            onClick = { priority = prio },
                            label = { Text(label, fontSize = 12.sp) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PaperForeground,
                                selectedLabelColor = Color.White,
                            ),
                            border = BorderStroke(1.dp, if (priority == prio) PaperForeground else PaperOutline),
                        )
                    }
                }

                // Due Date presets
                Text("Срок выполнения", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PaperOnSurfaceVariant)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    FilterChip(
                        selected = dueAtEpochMs == null,
                        onClick = { dueAtEpochMs = null },
                        label = { Text("Без срока", fontSize = 12.sp) },
                    )

                    FilterChip(
                        selected = dueAtEpochMs != null && isToday(dueAtEpochMs!!),
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                set(Calendar.HOUR_OF_DAY, 23)
                                set(Calendar.MINUTE, 59)
                                set(Calendar.SECOND, 59)
                            }
                            dueAtEpochMs = cal.timeInMillis
                        },
                        label = { Text("Сегодня", fontSize = 12.sp) },
                    )

                    FilterChip(
                        selected = dueAtEpochMs != null && isTomorrow(dueAtEpochMs!!),
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, 1)
                                set(Calendar.HOUR_OF_DAY, 23)
                                set(Calendar.MINUTE, 59)
                                set(Calendar.SECOND, 59)
                            }
                            dueAtEpochMs = cal.timeInMillis
                        },
                        label = { Text("Завтра", fontSize = 12.sp) },
                    )

                    FilterChip(
                        selected = dueAtEpochMs != null && !isToday(dueAtEpochMs!!) && !isTomorrow(dueAtEpochMs!!),
                        onClick = {
                            val cal = Calendar.getInstance().apply {
                                add(Calendar.DAY_OF_YEAR, 7)
                                set(Calendar.HOUR_OF_DAY, 23)
                                set(Calendar.MINUTE, 59)
                                set(Calendar.SECOND, 59)
                            }
                            dueAtEpochMs = cal.timeInMillis
                        },
                        label = { Text("Через неделю", fontSize = 12.sp) },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        error = "Заголовок задачи обязателен"
                    } else {
                        onSave(title.trim(), description.trim(), dueAtEpochMs, priority)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PaperForeground),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Сохранить", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Отмена", color = PaperForeground)
            }
        },
        containerColor = PaperBackground,
    )
}

private fun isToday(epochMs: Long): Boolean {
    val cal1 = Calendar.getInstance()
    val cal2 = Calendar.getInstance().apply { timeInMillis = epochMs }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
        cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

private fun isTomorrow(epochMs: Long): Boolean {
    val cal1 = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, 1) }
    val cal2 = Calendar.getInstance().apply { timeInMillis = epochMs }
    return cal1.get(Calendar.YEAR) == cal2.get(Calendar.YEAR) &&
        cal1.get(Calendar.DAY_OF_YEAR) == cal2.get(Calendar.DAY_OF_YEAR)
}

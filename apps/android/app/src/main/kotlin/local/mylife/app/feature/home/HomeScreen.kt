package local.mylife.app.feature.home

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.mylife.core.designsystem.components.FocusCard
import local.mylife.core.designsystem.components.FocusEmptyState
import local.mylife.core.designsystem.components.SectionHeader
import local.mylife.core.designsystem.theme.PaperBackground
import local.mylife.core.designsystem.theme.PaperForeground
import local.mylife.core.designsystem.theme.PaperOnSurfaceVariant
import local.mylife.core.designsystem.theme.PaperOutline
import local.mylife.core.designsystem.theme.PaperSurface
import local.mylife.core.model.Place
import local.mylife.core.model.RecentRecord
import local.mylife.core.model.RecentRecordType
import local.mylife.core.model.ReminderItem
import local.mylife.core.model.TaskItem
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun HomeScreen(
    viewModel: HomeViewModel,
    onNavigateToCategory: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    var showAddTaskDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PaperBackground)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        // 1. Intro Header
        if (state.currentDateText.isNotBlank()) {
            Text(
                text = state.currentDateText,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = PaperOnSurfaceVariant,
            )
            Spacer(modifier = Modifier.height(4.dp))
        }

        Text(
            text = "Сначала главное",
            fontSize = 30.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = (-1).sp,
            color = PaperForeground,
        )

        Text(
            text = "Остальное успеется.",
            fontSize = 13.sp,
            color = PaperOnSurfaceVariant,
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 2. Focus Card (Nearest Task)
        if (state.isLoading && state.nearestTask == null) {
            CircularProgressIndicator(
                color = PaperForeground,
                modifier = Modifier.align(Alignment.CenterHorizontally),
            )
        } else if (state.nearestTask != null) {
            val task = state.nearestTask!!
            val dueStr = task.dueAtEpochMs?.let {
                val sdf = SimpleDateFormat("dd MMMM · HH:mm", Locale("ru"))
                sdf.format(Date(it))
            } ?: "Без срока"

            FocusCard(
                title = task.title,
                details = task.description.ifBlank { null },
                dueTimeText = dueStr,
                actionText = "Выполнить",
                onActionClick = { viewModel.markTaskDone(task.id) },
                onCardClick = { onNavigateToCategory("Задачи") },
            )
        } else {
            FocusEmptyState(
                title = "Нет текущих дел",
                description = "Все запланированные дела выполнены. Наслаждайтесь моментом.",
                actionText = "+ Добавить задачу",
                onActionClick = { showAddTaskDialog = true },
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // 3. Section: Ещё в планах
        if (state.remainingTasks.isNotEmpty()) {
            SectionHeader(
                title = "Ещё в планах",
                actionText = "Все",
                onActionClick = { onNavigateToCategory("Задачи") },
            )

            state.remainingTasks.forEach { task ->
                TaskLineItem(
                    task = task,
                    onDoneClick = { viewModel.markTaskDone(task.id) },
                )
                Spacer(modifier = Modifier.height(6.dp))
            }

            Spacer(modifier = Modifier.height(20.dp))
        }

        // 4. Section: Напоминания
        SectionHeader(
            title = "Напоминания",
            actionText = "Все",
            onActionClick = { onNavigateToCategory("Напоминания") },
        )

        if (state.activeReminders.isNotEmpty()) {
            state.activeReminders.forEach { reminder ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PaperOutline),
                    colors = CardDefaults.cardColors(containerColor = PaperSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCategory("Напоминания") },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("🔔", fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = reminder.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = PaperForeground,
                            )
                            val fireStr = SimpleDateFormat("d MMMM · HH:mm", Locale("ru")).format(Date(reminder.fireEpochMs))
                            Text(
                                text = fireStr,
                                fontSize = 11.sp,
                                color = PaperOnSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        } else {
            FocusEmptyState(
                title = "Нет активных напоминаний",
                description = "Локальные напоминания без облачных сервисов.",
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 5. Section: Недавно посетил
        SectionHeader(
            title = "Недавно посетил",
            actionText = "Все",
            onActionClick = { onNavigateToCategory("Места") },
        )

        if (state.recentVisits.isNotEmpty()) {
            state.recentVisits.forEach { place ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PaperOutline),
                    colors = CardDefaults.cardColors(containerColor = PaperSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onNavigateToCategory("Места") },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text("📍", fontSize = 20.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = place.name,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = PaperForeground,
                            )
                            val visitedText = place.lastVisitedAtEpochMs?.let {
                                "Посетил " + SimpleDateFormat("d MMMM", Locale("ru")).format(Date(it))
                            } ?: place.addressText.ifBlank { "Без адреса" }
                            Text(
                                text = visitedText,
                                fontSize = 12.sp,
                                color = PaperOnSurfaceVariant,
                            )
                        }
                        place.rating?.let { r ->
                            Text(
                                text = "★ $r/10",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                color = PaperForeground,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        } else {
            FocusEmptyState(
                title = "Места пока не добавлены",
                description = "Отмечайте любимые парки, кафе и заведения вручную.",
                actionText = "+ Добавить место",
                onActionClick = { onNavigateToCategory("Места") },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 6. Section: Последние записи
        SectionHeader(
            title = "Последние записи",
            actionText = "Все",
            onActionClick = { onNavigateToCategory("Записи") },
        )

        if (state.recentRecords.isNotEmpty()) {
            state.recentRecords.forEach { record ->
                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, PaperOutline),
                    colors = CardDefaults.cardColors(containerColor = PaperSurface),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            val route = when (record.type) {
                                RecentRecordType.PURCHASE -> "Покупки"
                                RecentRecordType.PLACE -> "Места"
                                RecentRecordType.NOTE -> "Заметки"
                                RecentRecordType.TASK -> "Задачи"
                            }
                            onNavigateToCategory(route)
                        },
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        val (icon, typeLabel) = when (record.type) {
                            RecentRecordType.PURCHASE -> "🛍" to "Покупка"
                            RecentRecordType.PLACE -> "📍" to "Место"
                            RecentRecordType.NOTE -> "✎" to "Заметка"
                            RecentRecordType.TASK -> "✓" to "Задача"
                        }
                        Text(icon, fontSize = 18.sp)
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = record.title,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                                color = PaperForeground,
                            )
                            val dateStr = SimpleDateFormat("d MMMM · HH:mm", Locale("ru")).format(Date(record.createdAtEpochMs))
                            Text(
                                text = "$typeLabel · $dateStr",
                                fontSize = 11.sp,
                                color = PaperOnSurfaceVariant,
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(6.dp))
            }
        } else {
            FocusEmptyState(
                title = "Записей пока нет",
                description = "Создавайте заметки, задачи, места и сохраняйте покупки.",
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }

    if (showAddTaskDialog) {
        var newTaskTitle by remember { mutableStateOf("") }
        var newTaskDesc by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showAddTaskDialog = false },
            title = { Text("Новая задача", color = PaperForeground, fontWeight = FontWeight.Bold) },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTaskTitle,
                        onValueChange = { if (it.length <= 160) newTaskTitle = it },
                        label = { Text("Название дела *") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PaperForeground,
                            unfocusedBorderColor = PaperOutline,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTaskDesc,
                        onValueChange = { newTaskDesc = it },
                        label = { Text("Описание (необязательно)") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PaperForeground,
                            unfocusedBorderColor = PaperOutline,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (newTaskTitle.isNotBlank()) {
                            viewModel.createQuickTask(newTaskTitle, newTaskDesc)
                            showAddTaskDialog = false
                        }
                    },
                    enabled = newTaskTitle.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = PaperForeground),
                ) {
                    Text("Создать", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddTaskDialog = false }) {
                    Text("Отмена", color = PaperForeground)
                }
            },
            containerColor = PaperBackground,
        )
    }
}

@Composable
fun TaskLineItem(
    task: TaskItem,
    onDoneClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, PaperOutline),
        colors = CardDefaults.cardColors(containerColor = PaperSurface),
        modifier = modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = task.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = PaperForeground,
                )
                if (task.description.isNotBlank()) {
                    Text(
                        text = task.description,
                        fontSize = 11.sp,
                        color = PaperOnSurfaceVariant,
                    )
                }
            }

            TextButton(
                onClick = onDoneClick,
                colors = ButtonDefaults.textButtonColors(contentColor = PaperForeground),
            ) {
                Text("✓ Выполнить", fontSize = 12.sp)
            }
        }
    }
}

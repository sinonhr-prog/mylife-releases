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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.ui.graphics.Color
import local.mylife.app.feature.purchases.ProductCard
import local.mylife.core.designsystem.components.FocusCard
import local.mylife.core.designsystem.components.FocusEmptyState
import local.mylife.core.designsystem.components.SectionHeader
import local.mylife.core.designsystem.theme.PaperBackground
import local.mylife.core.designsystem.theme.PaperForeground
import local.mylife.core.designsystem.theme.PaperOnSurfaceVariant
import local.mylife.core.designsystem.theme.PaperOutline
import local.mylife.core.designsystem.theme.PaperSurface
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

        // 4. Section: Покупки
        SectionHeader(
            title = "Покупки",
            actionText = "Все",
            onActionClick = { onNavigateToCategory("Покупки") },
        )

        if (state.recentPurchases.isNotEmpty()) {
            state.recentPurchases.forEach { product ->
                ProductCard(
                    product = product,
                    onClick = { onNavigateToCategory("Покупки") },
                    onToggleFavorite = {},
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
        } else {
            FocusEmptyState(
                title = "Покупок пока нет",
                description = "Сохраняйте покупки с фотографиями и оценкой эксплуатации.",
                actionText = "+ Добавить покупку",
                onActionClick = { onNavigateToCategory("Покупки") },
            )
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 5. Section: Напоминания (honestly empty until reminders phase)
        SectionHeader(
            title = "Напоминания",
            actionText = "Все",
            onActionClick = { onNavigateToCategory("Напоминания") },
        )
        FocusEmptyState(
            title = "Нет активных напоминаний",
            description = "Локальные напоминания без облачных сервисов.",
        )

        Spacer(modifier = Modifier.height(20.dp))

        // 6. Section: Недавно посетил (honestly empty until places phase)
        SectionHeader(
            title = "Недавно посетил",
            actionText = "Все",
            onActionClick = { onNavigateToCategory("Места") },
        )
        FocusEmptyState(
            title = "Места пока не добавлены",
            description = "Отмечайте любимые парки, кафе и заведения вручную.",
        )

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

package local.mylife.app.feature.reminders

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.mylife.core.designsystem.components.FocusEmptyState
import local.mylife.core.designsystem.theme.PaperBackground
import local.mylife.core.designsystem.theme.PaperForeground
import local.mylife.core.designsystem.theme.PaperOnSurfaceVariant
import local.mylife.core.designsystem.theme.PaperOutline
import local.mylife.core.designsystem.theme.PaperSurface
import local.mylife.core.model.ReminderItem
import local.mylife.core.model.ReminderRepeat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun RemindersScreen(
    viewModel: RemindersViewModel,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(PaperBackground),
    ) {
        if (state.isLoading && state.reminders.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                CircularProgressIndicator(color = PaperForeground)
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "НАПОМИНАНИЯ",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PaperOnSurfaceVariant,
                        letterSpacing = 1.sp,
                    )
                }

                // Vivo & Device Diagnostics Banner
                state.diagnostics?.let { diag ->
                    item {
                        DeviceDiagnosticsCard(diag)
                    }
                }

                if (state.reminders.isEmpty()) {
                    item {
                        FocusEmptyState(
                            title = "Напоминаний нет",
                            description = "Создайте сигнал для важного дела или встречи. Доставка зависит от разрешений и энергосбережения телефона.",
                            actionText = "+ Запланировать напоминание",
                            onActionClick = { viewModel.openAddReminder() },
                            modifier = Modifier.padding(vertical = 32.dp),
                        )
                    }
                } else {
                    items(state.reminders, key = { it.id }) { reminder ->
                        ReminderCardItem(
                            reminder = reminder,
                            onToggle = { viewModel.toggleReminder(reminder) },
                            onEdit = { viewModel.openEditReminder(reminder) },
                            onDelete = { viewModel.deleteReminder(reminder.id) },
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp)) // padding for bottom bar
                }
            }
        }

        // Add/Edit Dialog
        if (state.showAddDialog && state.editingReminder != null) {
            ReminderEditDialog(
                initialReminder = state.editingReminder!!,
                availableTasks = state.availableTasks,
                errorMessage = state.errorMessage,
                onDismiss = { viewModel.dismissDialog() },
                onSave = { title, fireEpochMs, repeat, linkedTaskId ->
                    viewModel.saveReminder(title, fireEpochMs, repeat, linkedTaskId)
                },
            )
        }
    }
}

@Composable
fun DeviceDiagnosticsCard(diag: DeviceDiagnostics) {
    Card(
        colors = CardDefaults.cardColors(containerColor = PaperSurface),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, PaperOutline),
        modifier = Modifier.fillMaxWidth().testTag("diagnostics_card"),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "ДИАГНОСТИКА УСТРОЙСТВА",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaperOnSurfaceVariant,
                    letterSpacing = 0.5.sp,
                )
                if (diag.isVivoDevice) {
                    Text(
                        text = "Vivo / Funtouch OS",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PaperForeground,
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (diag.areNotificationsEnabled) "✓ Уведомления включены" else "⚠ Уведомления запрещены",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (diag.areNotificationsEnabled) PaperForeground else Color(0xFFC0392B),
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (diag.canScheduleExactAlarms) "✓ Точный запуск разрешён" else "⚠ Неточный запуск (Doze/Inexact)",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (diag.canScheduleExactAlarms) PaperForeground else Color(0xFFD35400),
                )
            }

            if (diag.recommendations.isNotEmpty()) {
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider(color = PaperOutline)
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Рекомендации фоновой работы:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PaperForeground,
                )
                Spacer(modifier = Modifier.height(4.dp))
                diag.recommendations.forEach { rec ->
                    Text(
                        text = "• $rec",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = PaperOnSurfaceVariant,
                        modifier = Modifier.padding(vertical = 1.dp),
                    )
                }
            }
        }
    }
}

@Composable
fun ReminderCardItem(
    reminder: ReminderItem,
    onToggle: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val dateFormat = remember { SimpleDateFormat("d MMMM yyyy, HH:mm", Locale("ru")) }
    val formattedDate =
        remember(reminder.fireEpochMs) {
            dateFormat.format(Date(reminder.fireEpochMs))
        }

    val repeatText =
        when (reminder.repeat) {
            ReminderRepeat.ONCE -> "Однократно"
            ReminderRepeat.DAILY -> "Каждый день"
            ReminderRepeat.WEEKLY -> "Каждую неделю"
        }

    val isPast = reminder.fireEpochMs < System.currentTimeMillis() && reminder.repeat == ReminderRepeat.ONCE

    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PaperSurface),
        border = BorderStroke(1.dp, PaperOutline),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .testTag("reminder_item_${reminder.id}"),
    ) {
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = reminder.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (reminder.isEnabled && !isPast) PaperForeground else PaperOnSurfaceVariant,
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "🔔 $formattedDate",
                        fontSize = 12.sp,
                        color = if (isPast) Color(0xFFC0392B) else PaperForeground,
                        fontWeight = if (isPast) FontWeight.Medium else FontWeight.Normal,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "• $repeatText",
                        fontSize = 12.sp,
                        color = PaperOnSurfaceVariant,
                    )
                }

                if (reminder.taskId != null) {
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Связано с задачей",
                        fontSize = 11.sp,
                        color = PaperOnSurfaceVariant,
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(
                    checked = reminder.isEnabled && !isPast,
                    onCheckedChange = { onToggle() },
                    colors =
                        SwitchDefaults.colors(
                            checkedThumbColor = Color.White,
                            checkedTrackColor = PaperForeground,
                            uncheckedThumbColor = PaperOnSurfaceVariant,
                            uncheckedTrackColor = PaperOutline,
                        ),
                    modifier = Modifier.testTag("reminder_switch_${reminder.id}"),
                )

                TextButton(onClick = onDelete) {
                    Text("✕", fontSize = 16.sp, color = PaperOnSurfaceVariant)
                }
            }
        }
    }
}

@Composable
fun ReminderEditDialog(
    initialReminder: ReminderDraft,
    availableTasks: List<local.mylife.core.model.TaskItem>,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, fireEpochMs: Long, repeat: ReminderRepeat, linkedTaskId: String?) -> Unit,
) {
    var title by remember { mutableStateOf(initialReminder.title) }
    var repeat by remember { mutableStateOf(initialReminder.repeat) }
    var selectedTaskId by remember { mutableStateOf(initialReminder.taskId) }

    // Date/Time input
    val sdf = remember { SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.US) }
    var dateString by remember {
        mutableStateOf(sdf.format(Date(initialReminder.fireEpochMs)))
    }
    var parseError by remember { mutableStateOf<String?>(null) }
    var showTaskDropdown by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (initialReminder.title.isEmpty()) "Новое напоминание" else "Редактировать напоминание",
                fontWeight = FontWeight.Bold,
                color = PaperForeground,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (errorMessage != null) {
                    Text(errorMessage, color = Color(0xFFC0392B), fontSize = 12.sp)
                }
                if (parseError != null) {
                    Text(parseError!!, color = Color(0xFFC0392B), fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название напоминания") },
                    modifier = Modifier.fillMaxWidth().testTag("reminder_title_field"),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = dateString,
                    onValueChange = {
                        dateString = it
                        parseError = null
                    },
                    label = { Text("Дата и время (ГГГГ-ММ-ДД ЧЧ:ММ)") },
                    modifier = Modifier.fillMaxWidth().testTag("reminder_time_field"),
                    singleLine = true,
                )

                // Repeat rule selection
                Text(
                    text = "Повтор сигнала:",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PaperOnSurfaceVariant,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ReminderRepeat.values().forEach { r ->
                        val isSelected = repeat == r
                        OutlinedButton(
                            onClick = { repeat = r },
                            colors =
                                ButtonDefaults.outlinedButtonColors(
                                    containerColor = if (isSelected) PaperForeground else PaperSurface,
                                    contentColor = if (isSelected) Color.White else PaperForeground,
                                ),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, if (isSelected) PaperForeground else PaperOutline),
                        ) {
                            Text(
                                text =
                                    when (r) {
                                        ReminderRepeat.ONCE -> "1 раз"
                                        ReminderRepeat.DAILY -> "Каждый день"
                                        ReminderRepeat.WEEKLY -> "Каждую неделю"
                                    },
                                fontSize = 11.sp,
                            )
                        }
                    }
                }

                // Link to task
                if (availableTasks.isNotEmpty()) {
                    Box {
                        OutlinedButton(
                            onClick = { showTaskDropdown = true },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, PaperOutline),
                        ) {
                            val linkedTask = availableTasks.find { it.id == selectedTaskId }
                            Text(
                                text = if (linkedTask != null) "Задача: ${linkedTask.title}" else "Привязать к задаче (нет)",
                                color = PaperForeground,
                                fontSize = 12.sp,
                            )
                        }
                        DropdownMenu(
                            expanded = showTaskDropdown,
                            onDismissRequest = { showTaskDropdown = false },
                        ) {
                            DropdownMenuItem(
                                text = { Text("Без привязки к задаче") },
                                onClick = {
                                    selectedTaskId = null
                                    showTaskDropdown = false
                                },
                            )
                            availableTasks.forEach { task ->
                                DropdownMenuItem(
                                    text = { Text(task.title) },
                                    onClick = {
                                        selectedTaskId = task.id
                                        showTaskDropdown = false
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedDate =
                        try {
                            sdf.parse(dateString.trim())
                        } catch (e: Exception) {
                            null
                        }
                    if (parsedDate == null) {
                        parseError = "Неверный формат даты. Пример: 2026-10-18 14:30"
                        return@Button
                    }
                    onSave(title, parsedDate.time, repeat, selectedTaskId)
                },
                colors = ButtonDefaults.buttonColors(containerColor = PaperForeground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_reminder_button"),
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

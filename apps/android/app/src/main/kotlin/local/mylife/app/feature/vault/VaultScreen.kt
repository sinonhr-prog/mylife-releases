package local.mylife.app.feature.vault

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
import androidx.compose.foundation.layout.heightIn
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import local.mylife.core.designsystem.components.FocusEmptyState
import local.mylife.core.designsystem.theme.PaperBackground
import local.mylife.core.designsystem.theme.PaperForeground
import local.mylife.core.designsystem.theme.PaperOnSurfaceVariant
import local.mylife.core.designsystem.theme.PaperOutline
import local.mylife.core.designsystem.theme.PaperSurface
import local.mylife.core.model.VaultEntry

@Composable
fun VaultScreen(
    viewModel: VaultViewModel,
    onLockVault: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingDelete by remember { mutableStateOf<VaultEntry?>(null) }

    val filteredEntries =
        remember(state.entries, state.searchQuery) {
            val q = state.searchQuery.trim().lowercase()
            if (q.isEmpty()) {
                state.entries
            } else {
                state.entries.filter {
                    it.title.lowercase().contains(q) ||
                        it.username.lowercase().contains(q) ||
                        it.website.lowercase().contains(q)
                }
            }
        }

    Box(
        modifier =
            modifier
                .fillMaxSize()
                .background(PaperBackground),
    ) {
        if (state.isLoading && state.entries.isEmpty()) {
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
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "Ваши пароли",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PaperForeground,
                        )
                        Text("Выход закроет сейф. Для следующего входа понадобится PIN.", color = PaperOnSurfaceVariant)
                        OutlinedButton(
                            onClick = onLockVault,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, PaperForeground),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("lock_vault_button"),
                        ) {
                            Text("Закрыть сейф", color = PaperForeground)
                        }
                    }
                }

                item {
                    OutlinedTextField(
                        value = state.searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        label = { Text("Поиск в сейфе...") },
                        modifier = Modifier.fillMaxWidth().testTag("vault_search_field"),
                        singleLine = true,
                    )
                }

                if (filteredEntries.isEmpty()) {
                    item {
                        FocusEmptyState(
                            title = if (state.searchQuery.isEmpty()) "Сейф пуст" else "Ничего не найдено",
                            description = if (state.searchQuery.isEmpty()) "Добавьте первую запись: сервис, логин и пароль." else "Попробуйте другое название, логин или сайт.",
                            actionText = if (state.searchQuery.isEmpty()) "+ Добавить пароль" else null,
                            onActionClick = { viewModel.openAddEntry() },
                            modifier = Modifier.padding(vertical = 32.dp),
                        )
                    }
                } else {
                    items(filteredEntries, key = { it.id }) { entry ->
                        val isRevealed = state.revealedPasswordIds.contains(entry.id)
                        VaultEntryCard(
                            entry = entry,
                            isPasswordRevealed = isRevealed,
                            onToggleVisibility = { viewModel.togglePasswordVisibility(entry.id) },
                            onCopyPassword = {
                                SafeClipboardHelper.copySensitive(context, "Пароль", entry.password)
                            },
                            onCopyUsername = {
                                SafeClipboardHelper.copySensitive(context, "Логин", entry.username)
                            },
                            onEdit = { viewModel.openEditEntry(entry) },
                            onDelete = { pendingDelete = entry },
                        )
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(80.dp))
                }
            }
        }

        pendingDelete?.let { entry ->
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text("Удалить запись?") },
                text = { Text("Запись «" + entry.title + "» будет удалена из сейфа.") },
                confirmButton = {
                    TextButton(onClick = {
                        viewModel.deleteEntry(entry.id)
                        pendingDelete = null
                    }) { Text("Удалить") }
                },
                dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text("Отмена") } },
            )
        }

        // Add / Edit Dialog
        if (state.showAddEditDialog) {
            VaultEntryDialog(
                initialTitle = state.draftTitle,
                initialWebsite = state.draftWebsite,
                initialUsername = state.draftUsername,
                initialPassword = state.draftPassword,
                initialSecretNote = state.draftSecretNote,
                isEditing = state.editingEntryId != null,
                errorMessage = state.errorMessage,
                onDismiss = { viewModel.dismissDialog() },
                onSave = { title, website, username, password, note ->
                    coroutineScope.launch {
                        viewModel.saveEntry(title, website, username, password, note)
                    }
                },
            )
        }
    }
}

@Composable
fun VaultEntryCard(
    entry: VaultEntry,
    isPasswordRevealed: Boolean,
    onToggleVisibility: () -> Unit,
    onCopyPassword: () -> Unit,
    onCopyUsername: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PaperSurface),
        border = BorderStroke(1.dp, PaperOutline),
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onEdit)
                .testTag("vault_entry_${entry.id}"),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = entry.title,
                    modifier = Modifier.weight(1f),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PaperForeground,
                )
                TextButton(onClick = onDelete) {
                    Text("Удалить", color = PaperOnSurfaceVariant)
                }
            }

            if (entry.website.isNotBlank()) {
                Text(
                    text = "🌐 ${entry.website}",
                    fontSize = 12.sp,
                    color = PaperOnSurfaceVariant,
                )
                Spacer(modifier = Modifier.height(4.dp))
            }

            if (entry.username.isNotBlank()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        text = "Логин: ${entry.username}",
                        modifier = Modifier.weight(1f),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = PaperForeground,
                    )
                    Text(
                        text = "Копировать",
                        fontSize = 11.sp,
                        color = PaperOnSurfaceVariant,
                        modifier = Modifier.clickable { onCopyUsername() },
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
            }

            // Password row
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = if (isPasswordRevealed) entry.password else "••••••••••••",
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                        color = PaperForeground,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = if (isPasswordRevealed) "Скрыть" else "Показать",
                        fontSize = 11.sp,
                        color = PaperOnSurfaceVariant,
                        modifier =
                            Modifier
                                .heightIn(min = 48.dp)
                                .padding(vertical = 12.dp)
                                .clickable { onToggleVisibility() }
                                .testTag("toggle_pass_${entry.id}"),
                    )
                }

                OutlinedButton(
                    onClick = onCopyPassword,
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, PaperOutline),
                    modifier = Modifier.testTag("copy_pass_${entry.id}"),
                ) {
                    Text("Копировать", fontSize = 11.sp, color = PaperForeground)
                }
            }

            if (entry.secretNote.isNotBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Заметка: ${entry.secretNote}",
                    fontSize = 12.sp,
                    color = PaperOnSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun VaultEntryDialog(
    initialTitle: String,
    initialWebsite: String,
    initialUsername: String,
    initialPassword: String,
    initialSecretNote: String,
    isEditing: Boolean,
    errorMessage: String?,
    onDismiss: () -> Unit,
    onSave: (title: String, website: String, username: String, password: String, note: String) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var website by remember { mutableStateOf(initialWebsite) }
    var username by remember { mutableStateOf(initialUsername) }
    var password by remember { mutableStateOf(initialPassword) }
    var secretNote by remember { mutableStateOf(initialSecretNote) }
    var showPassword by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (isEditing) "Редактировать запись" else "Новый пароль",
                fontWeight = FontWeight.Bold,
                color = PaperForeground,
            )
        },
        text = {
            Column(modifier = Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (errorMessage != null) {
                    Text(errorMessage, color = Color(0xFFC0392B), fontSize = 12.sp)
                }

                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Название сервиса (например, Почта)") },
                    modifier = Modifier.fillMaxWidth().testTag("vault_input_title"),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = website,
                    onValueChange = { website = it },
                    label = { Text("Сайт или приложение (опционально)") },
                    modifier = Modifier.fillMaxWidth().testTag("vault_input_website"),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Логин / Email / Телефон") },
                    modifier = Modifier.fillMaxWidth().testTag("vault_input_username"),
                    singleLine = true,
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Пароль") },
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth().testTag("vault_input_password"),
                    singleLine = true,
                )
                TextButton(onClick = { showPassword = !showPassword }) {
                    Text(if (showPassword) "Скрыть пароль" else "Показать пароль", color = PaperForeground)
                }
                OutlinedButton(
                    onClick = {
                        password = PasswordGenerator.generatePassword(16)
                        showPassword = true
                    },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("generate_password_button"),
                ) { Text("Сгенерировать пароль", color = PaperForeground) }

                OutlinedTextField(
                    value = secretNote,
                    onValueChange = { secretNote = it },
                    label = { Text("Секретная заметка (PIN, вопросы)") },
                    modifier = Modifier.fillMaxWidth(),
                    maxLines = 3,
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSave(title, website, username, password, secretNote) },
                colors = ButtonDefaults.buttonColors(containerColor = PaperForeground),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.testTag("save_vault_entry_button"),
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

package local.mylife.app.feature.notes

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
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.mylife.core.designsystem.components.FocusEmptyState
import local.mylife.core.designsystem.theme.PaperBackground
import local.mylife.core.designsystem.theme.PaperForeground
import local.mylife.core.designsystem.theme.PaperOnSurfaceVariant
import local.mylife.core.designsystem.theme.PaperOutline
import local.mylife.core.designsystem.theme.PaperSurface
import local.mylife.core.model.Note
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NotesScreen(
    viewModel: NotesViewModel,
    modifier: Modifier = Modifier,
    onAddNewRequested: () -> Unit = { viewModel.openAddNote() },
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
                placeholder = { Text("Поиск заметок...", color = PaperOnSurfaceVariant) },
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
                    .semantics { contentDescription = "Поле поиска заметок" },
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
                NoteFilter.ALL to "Все",
                NoteFilter.FAVORITES to "★ Избранное",
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
        if (state.isLoading && state.allNotes.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PaperForeground)
            }
        } else if (state.filteredNotes.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                val emptyDesc = if (state.searchQuery.isNotBlank()) {
                    "По запросу «${state.searchQuery}» ничего не найдено."
                } else if (state.filter == NoteFilter.FAVORITES) {
                    "Нет избранных заметок."
                } else {
                    "Здесь будут мысли, списки и важные тексты."
                }
                FocusEmptyState(
                    title = if (state.searchQuery.isNotBlank()) "Ничего не найдено" else "Заметок нет",
                    description = emptyDesc,
                    actionText = "+ Добавить заметку",
                    onActionClick = onAddNewRequested,
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
            ) {
                items(state.filteredNotes, key = { it.id }) { note ->
                    NoteCard(
                        note = note,
                        onClick = { viewModel.openNoteDetails(note) },
                        onFavoriteClick = { viewModel.toggleFavorite(note) },
                    )
                }
            }
        }
    }

    // Details Dialog
    state.selectedNote?.let { note ->
        NoteDetailsDialog(
            note = note,
            onDismiss = { viewModel.closeNoteDetails() },
            onToggleFavorite = { viewModel.toggleFavorite(note) },
            onEdit = {
                viewModel.closeNoteDetails()
                viewModel.openEditNote(note)
            },
            onDelete = {
                viewModel.deleteNote(note.id)
            },
        )
    }

    // Add / Edit Dialog with Safe Drafts
    if (state.isAddEditSheetOpen) {
        AddEditNoteDialog(
            noteToEdit = state.noteToEdit,
            initialTitle = state.draftTitle,
            initialBody = state.draftBody,
            onDraftChanged = { title, body -> viewModel.updateDraft(title, body) },
            onDismiss = { viewModel.closeAddEditSheet() },
            onSave = { title, body, isFav ->
                viewModel.saveNote(title, body, isFav)
            },
        )
    }
}

@Composable
fun NoteCard(
    note: Note,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = PaperSurface),
        border = BorderStroke(1.dp, PaperOutline),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = note.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaperForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.size(32.dp),
                ) {
                    Text(
                        text = if (note.isFavorite) "★" else "☆",
                        fontSize = 20.sp,
                        color = if (note.isFavorite) PaperForeground else PaperOnSurfaceVariant,
                    )
                }
            }

            if (note.body.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = note.body,
                    fontSize = 13.sp,
                    color = PaperOnSurfaceVariant,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())
            Text(
                text = sdf.format(Date(note.updatedAtEpochMs.takeIf { it > 0 } ?: note.createdAtEpochMs)),
                fontSize = 11.sp,
                color = PaperOnSurfaceVariant,
            )
        }
    }
}

@Composable
fun NoteDetailsDialog(
    note: Note,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = note.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaperForeground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleFavorite) {
                    Text(
                        text = if (note.isFavorite) "★" else "☆",
                        fontSize = 24.sp,
                        color = if (note.isFavorite) PaperForeground else PaperOnSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                if (note.body.isNotBlank()) {
                    Text(
                        text = note.body,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        color = PaperForeground,
                    )
                } else {
                    Text(
                        text = "Текст заметки пуст",
                        fontSize = 13.sp,
                        color = PaperOnSurfaceVariant,
                    )
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
            title = { Text("Удалить заметку?") },
            text = { Text("Заметка будет удалена без возможности восстановления.") },
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
fun AddEditNoteDialog(
    noteToEdit: Note?,
    initialTitle: String,
    initialBody: String,
    onDraftChanged: (String, String) -> Unit,
    onDismiss: () -> Unit,
    onSave: (title: String, body: String, isFavorite: Boolean) -> Unit,
) {
    var title by remember { mutableStateOf(if (noteToEdit != null) noteToEdit.title else initialTitle) }
    var body by remember { mutableStateOf(if (noteToEdit != null) noteToEdit.body else initialBody) }
    var isFavorite by remember { mutableStateOf(noteToEdit?.isFavorite ?: false) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (noteToEdit == null) "Новая заметка" else "Редактировать заметку",
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
                        onDraftChanged(it, body)
                    },
                    label = { Text("Заголовок *") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PaperForeground,
                        unfocusedBorderColor = PaperOutline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Body
                OutlinedTextField(
                    value = body,
                    onValueChange = {
                        body = it
                        onDraftChanged(title, it)
                    },
                    label = { Text("Текст заметки") },
                    minLines = 6,
                    maxLines = 15,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PaperForeground,
                        unfocusedBorderColor = PaperOutline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Favorite
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("В избранное", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    FilterChip(
                        selected = isFavorite,
                        onClick = { isFavorite = !isFavorite },
                        label = { Text(if (isFavorite) "★ Да" else "☆ Нет") },
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (title.isBlank()) {
                        error = "Заголовок заметки обязателен"
                    } else {
                        onSave(title.trim(), body, isFavorite)
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

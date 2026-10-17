package local.mylife.app.feature.notes

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.mylife.core.model.Note
import local.mylife.core.model.NoteRepository
import java.util.UUID

enum class NoteFilter {
    ALL,
    FAVORITES,
}

data class NotesUiState(
    val searchQuery: String = "",
    val filter: NoteFilter = NoteFilter.ALL,
    val allNotes: List<Note> = emptyList(),
    val filteredNotes: List<Note> = emptyList(),
    val selectedNote: Note? = null,
    val isAddEditSheetOpen: Boolean = false,
    val noteToEdit: Note? = null,
    val draftTitle: String = "",
    val draftBody: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
)

class NotesViewModel(
    private val noteRepository: NoteRepository,
    private val externalScope: kotlinx.coroutines.CoroutineScope? = null,
) : ViewModel() {

    private val scope get() = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(NotesUiState())
    val uiState: StateFlow<NotesUiState> = _uiState.asStateFlow()

    init {
        loadNotes()
    }

    fun loadNotes() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val notes = noteRepository.getNotes()
                _uiState.update { current ->
                    val filtered = applyFilter(notes, current.searchQuery, current.filter)
                    current.copy(
                        allNotes = notes,
                        filteredNotes = filtered,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки заметок") }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val filtered = applyFilter(current.allNotes, query, current.filter)
            current.copy(searchQuery = query, filteredNotes = filtered)
        }
    }

    fun onFilterSelected(filter: NoteFilter) {
        _uiState.update { current ->
            val filtered = applyFilter(current.allNotes, current.searchQuery, filter)
            current.copy(filter = filter, filteredNotes = filtered)
        }
    }

    fun openNoteDetails(note: Note) {
        _uiState.update { it.copy(selectedNote = note) }
    }

    fun closeNoteDetails() {
        _uiState.update { it.copy(selectedNote = null) }
    }

    fun openAddNote() {
        _uiState.update {
            it.copy(
                isAddEditSheetOpen = true,
                noteToEdit = null,
                draftTitle = "",
                draftBody = "",
            )
        }
    }

    fun openEditNote(note: Note) {
        _uiState.update {
            it.copy(
                isAddEditSheetOpen = true,
                noteToEdit = note,
                draftTitle = note.title,
                draftBody = note.body,
            )
        }
    }

    fun updateDraft(title: String, body: String) {
        _uiState.update { it.copy(draftTitle = title, draftBody = body) }
    }

    fun closeAddEditSheet() {
        _uiState.update { it.copy(isAddEditSheetOpen = false, noteToEdit = null) }
    }

    fun saveNote(
        title: String,
        body: String = "",
        isFavorite: Boolean = false,
    ) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val cleanTitle = title.trim()
                require(cleanTitle.isNotBlank()) { "Заголовок заметки обязателен" }
                require(cleanTitle.length <= 160) { "Заголовок не может превышать 160 символов" }
                require(body.length <= 100000) { "Текст заметки не может превышать 100 000 символов" }

                val currentToEdit = _uiState.value.noteToEdit
                val noteId = currentToEdit?.id ?: UUID.randomUUID().toString()

                val note = Note(
                    id = noteId,
                    title = cleanTitle,
                    body = body,
                    isFavorite = isFavorite,
                    createdAtEpochMs = currentToEdit?.createdAtEpochMs ?: System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                )

                noteRepository.saveNote(note)
                _uiState.update {
                    it.copy(
                        isAddEditSheetOpen = false,
                        noteToEdit = null,
                        draftTitle = "",
                        draftBody = "",
                    )
                }
                loadNotes()

                if (_uiState.value.selectedNote?.id == noteId) {
                    _uiState.update { it.copy(selectedNote = note) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка сохранения заметки") }
            }
        }
    }

    fun toggleFavorite(note: Note) {
        scope.launch {
            try {
                val updated = note.copy(
                    isFavorite = !note.isFavorite,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
                noteRepository.saveNote(updated)
                loadNotes()
                if (_uiState.value.selectedNote?.id == note.id) {
                    _uiState.update { it.copy(selectedNote = updated) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun deleteNote(noteId: String) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                noteRepository.deleteNote(noteId)
                if (_uiState.value.selectedNote?.id == noteId) {
                    _uiState.update { it.copy(selectedNote = null) }
                }
                loadNotes()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка удаления заметки") }
            }
        }
    }

    private fun applyFilter(notes: List<Note>, query: String, filter: NoteFilter): List<Note> {
        val q = query.trim().lowercase()
        return notes.filter { note ->
            val matchesFilter = when (filter) {
                NoteFilter.ALL -> true
                NoteFilter.FAVORITES -> note.isFavorite
            }
            val matchesQuery = q.isBlank() ||
                note.title.lowercase().contains(q) ||
                note.body.lowercase().contains(q)

            matchesFilter && matchesQuery
        }
    }
}

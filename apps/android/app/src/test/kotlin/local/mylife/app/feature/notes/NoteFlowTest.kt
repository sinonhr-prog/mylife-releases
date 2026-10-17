package local.mylife.app.feature.notes

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import local.mylife.core.model.Note
import local.mylife.core.model.NoteRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class NoteFlowTest {

    private lateinit var repository: InMemoryNoteRepository
    private lateinit var viewModel: NotesViewModel
    private val testScope = CoroutineScope(Dispatchers.Unconfined)

    @Before
    fun setUp() {
        repository = InMemoryNoteRepository()
        viewModel = NotesViewModel(
            noteRepository = repository,
            externalScope = testScope,
        )
    }

    @Test
    fun testNoteCRUD_CreateEditFavoriteDelete() = runBlocking {
        assertTrue(viewModel.uiState.value.filteredNotes.isEmpty())

        // 1. Create note
        viewModel.saveNote(
            title = "Планы на отпуск",
            body = "1. Забронировать гостиницу\n2. Собрать чемодан",
            isFavorite = true,
        )

        val notes = viewModel.uiState.value.filteredNotes
        assertEquals(1, notes.size)
        val created = notes.first()
        assertEquals("Планы на отпуск", created.title)
        assertEquals("1. Забронировать гостиницу\n2. Собрать чемодан", created.body)
        assertTrue(created.isFavorite)

        // 2. Edit note
        viewModel.openEditNote(created)
        assertEquals("Планы на отпуск", viewModel.uiState.value.draftTitle)
        viewModel.saveNote(
            title = "Планы на летний отпуск",
            body = "1. Забронировать гостиницу\n2. Собрать чемодан\n3. Купить страховку",
            isFavorite = true,
        )
        val edited = viewModel.uiState.value.filteredNotes.first()
        assertEquals("Планы на летний отпуск", edited.title)
        assertTrue(edited.body.contains("Купить страховку"))

        // 3. Toggle favorite
        viewModel.toggleFavorite(edited)
        assertFalse(viewModel.uiState.value.filteredNotes.first().isFavorite)

        // 4. Delete note
        viewModel.deleteNote(edited.id)
        assertTrue(viewModel.uiState.value.allNotes.isEmpty())
    }

    @Test
    fun testNoteSearchAndFavoriteFilter() = runBlocking {
        viewModel.saveNote(title = "Рецепт пирога", body = "Мука, сахар, яблоки", isFavorite = true)
        viewModel.saveNote(title = "Список книг", body = "Алгоритмы и структуры данных", isFavorite = false)

        // Search by title
        viewModel.onSearchQueryChanged("пирог")
        assertEquals(1, viewModel.uiState.value.filteredNotes.size)
        assertEquals("Рецепт пирога", viewModel.uiState.value.filteredNotes.first().title)

        // Search by body
        viewModel.onSearchQueryChanged("яблоки")
        assertEquals(1, viewModel.uiState.value.filteredNotes.size)
        assertEquals("Рецепт пирога", viewModel.uiState.value.filteredNotes.first().title)

        viewModel.onSearchQueryChanged("структуры")
        assertEquals(1, viewModel.uiState.value.filteredNotes.size)
        assertEquals("Список книг", viewModel.uiState.value.filteredNotes.first().title)

        viewModel.onSearchQueryChanged("")
        assertEquals(2, viewModel.uiState.value.filteredNotes.size)

        // Filter favorites
        viewModel.onFilterSelected(NoteFilter.FAVORITES)
        assertEquals(1, viewModel.uiState.value.filteredNotes.size)
        assertEquals("Рецепт пирога", viewModel.uiState.value.filteredNotes.first().title)

        viewModel.onFilterSelected(NoteFilter.ALL)
        assertEquals(2, viewModel.uiState.value.filteredNotes.size)
    }

    @Test
    fun testNoteValidation() = runBlocking {
        viewModel.saveNote(title = "   ")
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.allNotes.isEmpty())
    }

    @Test
    fun testNoteSafeDrafts() = runBlocking {
        viewModel.openAddNote()
        viewModel.updateDraft("Черновик заголовка", "Черновик тела заметки")
        assertEquals("Черновик заголовка", viewModel.uiState.value.draftTitle)
        assertEquals("Черновик тела заметки", viewModel.uiState.value.draftBody)
    }
}

class InMemoryNoteRepository : NoteRepository {
    private val notes = mutableMapOf<String, Note>()

    override suspend fun getNotes(): List<Note> =
        notes.values.sortedWith(compareByDescending<Note> { it.isFavorite }.thenByDescending { it.updatedAtEpochMs })

    override suspend fun getNoteById(id: String): Note? = notes[id]

    override suspend fun saveNote(note: Note) {
        notes[note.id] = note
    }

    override suspend fun deleteNote(id: String) {
        notes.remove(id)
    }
}

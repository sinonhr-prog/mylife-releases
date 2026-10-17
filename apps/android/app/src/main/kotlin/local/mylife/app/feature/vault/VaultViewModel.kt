package local.mylife.app.feature.vault

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import local.mylife.core.model.VaultEntry
import local.mylife.core.model.VaultRepository
import java.util.UUID

data class VaultUiState(
    val entries: List<VaultEntry> = emptyList(),
    val searchQuery: String = "",
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val showAddEditDialog: Boolean = false,
    val editingEntryId: String? = null,
    val draftTitle: String = "",
    val draftWebsite: String = "",
    val draftUsername: String = "",
    val draftPassword: String = "",
    val draftSecretNote: String = "",
    val revealedPasswordIds: Set<String> = emptySet(),
)

class VaultViewModel(
    private val vaultRepository: VaultRepository,
    private val externalScope: kotlinx.coroutines.CoroutineScope? = null,
) : ViewModel() {

    private val scope get() = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(VaultUiState())
    val uiState: StateFlow<VaultUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        scope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, errorMessage = null)
            try {
                val list = vaultRepository.getVaultEntries()
                _uiState.value = _uiState.value.copy(
                    entries = list,
                    isLoading = false,
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    errorMessage = "Не удалось загрузить записи сейфа: ${e.message}",
                )
            }
        }
    }

    fun setSearchQuery(query: String) {
        _uiState.value = _uiState.value.copy(searchQuery = query)
    }

    fun togglePasswordVisibility(entryId: String) {
        val current = _uiState.value.revealedPasswordIds
        val updated = if (current.contains(entryId)) {
            current - entryId
        } else {
            current + entryId
        }
        _uiState.value = _uiState.value.copy(revealedPasswordIds = updated)
    }

    fun openAddEntry() {
        _uiState.value = _uiState.value.copy(
            showAddEditDialog = true,
            editingEntryId = null,
            draftTitle = "",
            draftWebsite = "",
            draftUsername = "",
            draftPassword = "",
            draftSecretNote = "",
        )
    }

    fun openEditEntry(entry: VaultEntry) {
        _uiState.value = _uiState.value.copy(
            showAddEditDialog = true,
            editingEntryId = entry.id,
            draftTitle = entry.title,
            draftWebsite = entry.website,
            draftUsername = entry.username,
            draftPassword = entry.password,
            draftSecretNote = entry.secretNote,
        )
    }

    fun dismissDialog() {
        _uiState.value = _uiState.value.copy(
            showAddEditDialog = false,
            editingEntryId = null,
        )
    }

    suspend fun saveEntry(
        title: String,
        website: String,
        username: String,
        password: String,
        secretNote: String,
    ) {
        val trimmedTitle = title.trim()
        if (trimmedTitle.isEmpty()) {
            _uiState.value = _uiState.value.copy(errorMessage = "Название не может быть пустым")
            return
        }

        val existingId = _uiState.value.editingEntryId
        try {
            val now = System.currentTimeMillis()
            val entryToSave = if (existingId != null) {
                val existing = vaultRepository.getVaultEntryById(existingId)
                val created = existing?.createdAtEpochMs ?: now
                VaultEntry(
                    id = existingId,
                    title = trimmedTitle,
                    website = website.trim(),
                    username = username.trim(),
                    password = password,
                    secretNote = secretNote.trim(),
                    createdAtEpochMs = created,
                    updatedAtEpochMs = now,
                )
            } else {
                VaultEntry(
                    id = UUID.randomUUID().toString(),
                    title = trimmedTitle,
                    website = website.trim(),
                    username = username.trim(),
                    password = password,
                    secretNote = secretNote.trim(),
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                )
            }

            vaultRepository.saveVaultEntry(entryToSave)
            _uiState.value = _uiState.value.copy(
                showAddEditDialog = false,
                editingEntryId = null,
            )
            loadData()
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                errorMessage = "Ошибка сохранения: ${e.message}",
            )
        }
    }

    fun deleteEntry(id: String) {
        scope.launch {
            try {
                vaultRepository.deleteVaultEntry(id)
                loadData()
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(
                    errorMessage = "Ошибка удаления: ${e.message}",
                )
            }
        }
    }
}

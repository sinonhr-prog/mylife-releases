package local.mylife.app.feature.places

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.mylife.core.model.AttachmentMeta
import local.mylife.core.model.Category
import local.mylife.core.model.CategoryKind
import local.mylife.core.model.CategoryRepository
import local.mylife.core.model.DefaultCategories
import local.mylife.core.model.Place
import local.mylife.core.model.PlaceRepository
import local.mylife.core.model.PlaceVisitStatus
import local.mylife.core.storage.files.EncryptedAttachmentStore
import java.io.ByteArrayInputStream
import java.util.UUID

enum class PlaceStatusFilter {
    ALL,
    WANT,
    VISITED,
    FAVORITE,
}

data class PlacesUiState(
    val searchQuery: String = "",
    val selectedCategoryId: String? = null,
    val statusFilter: PlaceStatusFilter = PlaceStatusFilter.ALL,
    val allPlaces: List<Place> = emptyList(),
    val filteredPlaces: List<Place> = emptyList(),
    val categories: List<Category> = emptyList(),
    val selectedPlace: Place? = null,
    val selectedPlaceAttachments: List<AttachmentMeta> = emptyList(),
    val selectedPlacePhotoBytes: ByteArray? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAddEditSheetOpen: Boolean = false,
    val placeToEdit: Place? = null,
    val isAddCategoryDialogOpen: Boolean = false,
    val categoryToDelete: Category? = null,
)

class PlacesViewModel(
    private val placeRepository: PlaceRepository,
    private val categoryRepository: CategoryRepository,
    private val attachmentStore: EncryptedAttachmentStore,
    private val mainDbKey: ByteArray,
    private val externalScope: kotlinx.coroutines.CoroutineScope? = null,
) : ViewModel() {

    private val scope get() = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(PlacesUiState())
    val uiState: StateFlow<PlacesUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    fun loadData() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                categoryRepository.ensureDefaultCategories()
                val categories = categoryRepository.getCategories(CategoryKind.PLACE)
                val places = placeRepository.getPlaces()
                _uiState.update { current ->
                    val filtered = applyFilter(places, current.searchQuery, current.selectedCategoryId, current.statusFilter)
                    current.copy(
                        categories = categories,
                        allPlaces = places,
                        filteredPlaces = filtered,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки мест") }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val filtered = applyFilter(current.allPlaces, query, current.selectedCategoryId, current.statusFilter)
            current.copy(searchQuery = query, filteredPlaces = filtered)
        }
    }

    fun selectCategory(categoryId: String?) {
        _uiState.update { current ->
            val filtered = applyFilter(current.allPlaces, current.searchQuery, categoryId, current.statusFilter)
            current.copy(selectedCategoryId = categoryId, filteredPlaces = filtered)
        }
    }

    fun selectCategoryByName(name: String) {
        val cat = _uiState.value.categories.firstOrNull { it.name.equals(name, ignoreCase = true) }
        selectCategory(cat?.id)
    }

    fun onStatusFilterSelected(filter: PlaceStatusFilter) {
        _uiState.update { current ->
            val filtered = applyFilter(current.allPlaces, current.searchQuery, current.selectedCategoryId, filter)
            current.copy(statusFilter = filter, filteredPlaces = filtered)
        }
    }

    fun openPlaceDetails(place: Place) {
        scope.launch {
            _uiState.update { it.copy(selectedPlace = place, selectedPlacePhotoBytes = null) }
            try {
                val attachments = placeRepository.getPlaceAttachments(place.id)
                val photoBytes = attachments.firstOrNull()?.let { att ->
                    try {
                        attachmentStore.loadDecryptedBytes(
                            att.thumbnailRelativePath ?: att.encryptedRelativePath,
                            mainDbKey,
                        )
                    } catch (_: Exception) {
                        null
                    }
                }
                _uiState.update {
                    it.copy(
                        selectedPlaceAttachments = attachments,
                        selectedPlacePhotoBytes = photoBytes,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun closePlaceDetails() {
        _uiState.update {
            it.copy(
                selectedPlace = null,
                selectedPlaceAttachments = emptyList(),
                selectedPlacePhotoBytes = null,
            )
        }
    }

    fun openAddPlace(preselectedCategoryId: String? = null) {
        val catId = preselectedCategoryId
            ?: _uiState.value.selectedCategoryId
            ?: _uiState.value.categories.firstOrNull()?.id
            ?: DefaultCategories.SHOPS.id
        _uiState.update { it.copy(isAddEditSheetOpen = true, placeToEdit = null, selectedCategoryId = catId) }
    }

    fun openEditPlace(place: Place) {
        _uiState.update { it.copy(isAddEditSheetOpen = true, placeToEdit = place) }
    }

    fun closeAddEditSheet() {
        _uiState.update { it.copy(isAddEditSheetOpen = false, placeToEdit = null) }
    }

    fun savePlace(
        name: String,
        categoryId: String,
        addressText: String = "",
        description: String = "",
        visitStatus: PlaceVisitStatus = PlaceVisitStatus.WANT,
        lastVisitedAtEpochMs: Long? = null,
        rating: Int? = null,
        isFavorite: Boolean = false,
        imageBytes: ByteArray? = null,
        imageMimeType: String? = null,
    ) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val cleanName = name.trim()
                require(cleanName.isNotBlank()) { "Название места обязательно" }
                require(cleanName.length <= 160) { "Название не может превышать 160 символов" }
                require(description.length <= 20000) { "Описание не может превышать 20 000 символов" }
                rating?.let { require(it in 1..10) { "Оценка должна быть от 1 до 10" } }
                lastVisitedAtEpochMs?.let {
                    require(it <= System.currentTimeMillis() + 60_000L) { "Дата последнего посещения не может быть в будущем" }
                }

                val currentToEdit = _uiState.value.placeToEdit
                val placeId = currentToEdit?.id ?: UUID.randomUUID().toString()

                val place = Place(
                    id = placeId,
                    categoryId = categoryId,
                    name = cleanName,
                    addressText = addressText.trim(),
                    description = description.trim(),
                    visitStatus = visitStatus,
                    lastVisitedAtEpochMs = lastVisitedAtEpochMs,
                    isFavorite = isFavorite,
                    rating = rating,
                    createdAtEpochMs = currentToEdit?.createdAtEpochMs ?: System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                )

                placeRepository.savePlace(place)

                if (imageBytes != null && imageMimeType != null) {
                    val meta = ByteArrayInputStream(imageBytes).use { input ->
                        attachmentStore.importImage(
                            inputStream = input,
                            rawFileEncryptionKey = mainDbKey,
                            mimeType = imageMimeType,
                        )
                    }
                    placeRepository.addPlaceAttachment(placeId, meta)
                }

                _uiState.update { it.copy(isAddEditSheetOpen = false, placeToEdit = null) }
                loadData()

                if (_uiState.value.selectedPlace?.id == placeId) {
                    openPlaceDetails(place)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка сохранения места") }
            }
        }
    }

    fun deletePlace(placeId: String) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                placeRepository.deletePlace(placeId)
                if (_uiState.value.selectedPlace?.id == placeId) {
                    _uiState.update { it.copy(selectedPlace = null) }
                }
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка удаления") }
            }
        }
    }

    fun toggleFavorite(place: Place) {
        scope.launch {
            try {
                val updated = place.copy(
                    isFavorite = !place.isFavorite,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
                placeRepository.savePlace(updated)
                loadData()
                if (_uiState.value.selectedPlace?.id == place.id) {
                    _uiState.update { it.copy(selectedPlace = updated) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun markVisited(place: Place, visitEpochMs: Long = System.currentTimeMillis()) {
        scope.launch {
            try {
                require(visitEpochMs <= System.currentTimeMillis() + 60_000L) { "Дата посещения не может быть в будущем" }
                val updated = place.copy(
                    visitStatus = PlaceVisitStatus.VISITED,
                    lastVisitedAtEpochMs = visitEpochMs,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
                placeRepository.savePlace(updated)
                loadData()
                if (_uiState.value.selectedPlace?.id == place.id) {
                    _uiState.update { it.copy(selectedPlace = updated) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun openAddCategoryDialog() {
        _uiState.update { it.copy(isAddCategoryDialogOpen = true) }
    }

    fun closeAddCategoryDialog() {
        _uiState.update { it.copy(isAddCategoryDialogOpen = false) }
    }

    fun addCustomCategory(name: String, iconKey: String = "pin") {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val cleanName = name.trim()
                require(cleanName.isNotBlank()) { "Название категории обязательно" }
                val category = Category(
                    id = "custom_${UUID.randomUUID().toString().take(8)}",
                    name = cleanName,
                    kind = CategoryKind.PLACE,
                    iconKey = iconKey,
                    sortOrder = 10,
                )
                categoryRepository.saveCategory(category)
                _uiState.update { it.copy(isAddCategoryDialogOpen = false) }
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка добавления категории") }
            }
        }
    }

    fun deleteCustomCategory(categoryId: String, fallbackCategoryId: String) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // Reassign places to fallback category to maintain category integrity
                placeRepository.updateCategoryForPlaces(categoryId, fallbackCategoryId)
                categoryRepository.deleteCategory(categoryId)
                _uiState.update { it.copy(categoryToDelete = null) }
                loadData()
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка удаления категории") }
            }
        }
    }

    private fun applyFilter(
        places: List<Place>,
        searchQuery: String,
        categoryId: String?,
        statusFilter: PlaceStatusFilter,
    ): List<Place> {
        val query = searchQuery.trim().lowercase()
        return places.filter { place ->
            val matchesCategory = categoryId == null || place.categoryId == categoryId
            val matchesStatus = when (statusFilter) {
                PlaceStatusFilter.ALL -> true
                PlaceStatusFilter.WANT -> place.visitStatus == PlaceVisitStatus.WANT
                PlaceStatusFilter.VISITED -> place.visitStatus == PlaceVisitStatus.VISITED
                PlaceStatusFilter.FAVORITE -> place.isFavorite
            }
            val matchesSearch = query.isBlank() ||
                place.name.lowercase().contains(query) ||
                place.addressText.lowercase().contains(query) ||
                place.description.lowercase().contains(query)

            matchesCategory && matchesStatus && matchesSearch
        }
    }
}

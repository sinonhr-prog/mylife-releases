package local.mylife.app.feature.places

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import local.mylife.core.model.AttachmentMeta
import local.mylife.core.model.Category
import local.mylife.core.model.CategoryKind
import local.mylife.core.model.CategoryRepository
import local.mylife.core.model.DefaultCategories
import local.mylife.core.model.Place
import local.mylife.core.model.PlaceRepository
import local.mylife.core.model.PlaceVisitStatus
import local.mylife.core.storage.files.EncryptedAttachmentStore
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.ByteArrayInputStream

class PlaceFlowTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var placeRepo: InMemoryPlaceRepository
    private lateinit var categoryRepo: InMemoryCategoryRepository
    private lateinit var attachmentStore: EncryptedAttachmentStore
    private lateinit var viewModel: PlacesViewModel
    private val testScope = CoroutineScope(Dispatchers.Unconfined)
    private val rawKey = ByteArray(32) { 0x55 }

    @Before
    fun setUp() {
        placeRepo = InMemoryPlaceRepository()
        categoryRepo = InMemoryCategoryRepository()
        attachmentStore = EncryptedAttachmentStore(tempFolder.newFolder("files"))
        viewModel = PlacesViewModel(
            placeRepository = placeRepo,
            categoryRepository = categoryRepo,
            attachmentStore = attachmentStore,
            mainDbKey = rawKey,
            externalScope = testScope,
        )
    }

    @Test
    fun testPredefinedCategories_LoadedOnInit() = runBlocking {
        val categories = viewModel.uiState.value.categories
        assertTrue("Categories must be loaded", categories.isNotEmpty())
        val names = categories.map { it.name }
        assertTrue(names.contains("Магазины"))
        assertTrue(names.contains("Парки"))
        assertTrue(names.contains("Кафе"))
        assertTrue(names.contains("Рестораны"))
    }

    @Test
    fun testPlaceCRUD_CreateSearchEditVisitDelete() = runBlocking {
        assertTrue(viewModel.uiState.value.filteredPlaces.isEmpty())

        val shopCat = viewModel.uiState.value.categories.first { it.name == "Магазины" }

        // 1. Create a place with WANT status, rating 9, address, and favorite
        viewModel.savePlace(
            name = "Книжный магазин «Букинист»",
            categoryId = shopCat.id,
            addressText = "ул. Ленина, д. 42",
            description = "Редкие издания и альбомы по искусству",
            visitStatus = PlaceVisitStatus.WANT,
            lastVisitedAtEpochMs = null,
            rating = 9,
            isFavorite = true,
        )

        val places = viewModel.uiState.value.filteredPlaces
        assertEquals(1, places.size)
        val created = places.first()
        assertEquals("Книжный магазин «Букинист»", created.name)
        assertEquals("ул. Ленина, д. 42", created.addressText)
        assertEquals("Редкие издания и альбомы по искусству", created.description)
        assertEquals(PlaceVisitStatus.WANT, created.visitStatus)
        assertNull(created.lastVisitedAtEpochMs)
        assertEquals(9, created.rating)
        assertTrue(created.isFavorite)

        // 2. Create second place in Parks
        val parkCat = viewModel.uiState.value.categories.first { it.name == "Парки" }
        val visitTime = System.currentTimeMillis() - 86400000L
        viewModel.savePlace(
            name = "Центральный парк культуры",
            categoryId = parkCat.id,
            addressText = "Парковая аллея, 1",
            description = "Тенистые дорожки у пруда",
            visitStatus = PlaceVisitStatus.VISITED,
            lastVisitedAtEpochMs = visitTime,
            rating = 10,
            isFavorite = false,
        )

        assertEquals(2, viewModel.uiState.value.allPlaces.size)

        // 3. Search by name, address, description
        viewModel.onSearchQueryChanged("Букинист")
        assertEquals(1, viewModel.uiState.value.filteredPlaces.size)
        assertEquals("Книжный магазин «Букинист»", viewModel.uiState.value.filteredPlaces.first().name)

        viewModel.onSearchQueryChanged("Парковая") // Search by address
        assertEquals(1, viewModel.uiState.value.filteredPlaces.size)
        assertEquals("Центральный парк культуры", viewModel.uiState.value.filteredPlaces.first().name)

        viewModel.onSearchQueryChanged("альбомы") // Search by description
        assertEquals(1, viewModel.uiState.value.filteredPlaces.size)

        viewModel.onSearchQueryChanged("")
        assertEquals(2, viewModel.uiState.value.filteredPlaces.size)

        // 4. Filter by Category
        viewModel.selectCategory(shopCat.id)
        assertEquals(1, viewModel.uiState.value.filteredPlaces.size)
        assertEquals("Книжный магазин «Букинист»", viewModel.uiState.value.filteredPlaces.first().name)

        viewModel.selectCategory(parkCat.id)
        assertEquals(1, viewModel.uiState.value.filteredPlaces.size)
        assertEquals("Центральный парк культуры", viewModel.uiState.value.filteredPlaces.first().name)

        viewModel.selectCategory(null) // All
        assertEquals(2, viewModel.uiState.value.filteredPlaces.size)

        // 5. Filter by Status (WANT, VISITED, FAVORITE)
        viewModel.onStatusFilterSelected(PlaceStatusFilter.WANT)
        assertEquals(1, viewModel.uiState.value.filteredPlaces.size)
        assertEquals("Книжный магазин «Букинист»", viewModel.uiState.value.filteredPlaces.first().name)

        viewModel.onStatusFilterSelected(PlaceStatusFilter.VISITED)
        assertEquals(1, viewModel.uiState.value.filteredPlaces.size)
        assertEquals("Центральный парк культуры", viewModel.uiState.value.filteredPlaces.first().name)

        viewModel.onStatusFilterSelected(PlaceStatusFilter.FAVORITE)
        assertEquals(1, viewModel.uiState.value.filteredPlaces.size)
        assertEquals("Книжный магазин «Букинист»", viewModel.uiState.value.filteredPlaces.first().name)

        viewModel.onStatusFilterSelected(PlaceStatusFilter.ALL)
        assertEquals(2, viewModel.uiState.value.filteredPlaces.size)

        // 6. Mark visited
        val bookStore = viewModel.uiState.value.allPlaces.first { it.id == created.id }
        val now = System.currentTimeMillis()
        viewModel.markVisited(bookStore, now)
        val updatedBookStore = viewModel.uiState.value.allPlaces.first { it.id == created.id }
        assertEquals(PlaceVisitStatus.VISITED, updatedBookStore.visitStatus)
        assertEquals(now, updatedBookStore.lastVisitedAtEpochMs)

        // 7. Delete place
        viewModel.deletePlace(updatedBookStore.id)
        assertEquals(1, viewModel.uiState.value.allPlaces.size)
        assertEquals("Центральный парк культуры", viewModel.uiState.value.allPlaces.first().name)
    }

    @Test
    fun testCustomCategoryAndIntegrity() = runBlocking {
        // 1. Add custom category
        viewModel.addCustomCategory("Выставки")
        val customCat = viewModel.uiState.value.categories.firstOrNull { it.name == "Выставки" }
        assertNotNull(customCat)

        // 2. Add place in custom category
        viewModel.savePlace(
            name = "Городской музей",
            categoryId = customCat!!.id,
            addressText = "Площадь искусств, 3",
            description = "Временные экспозиции",
        )
        assertEquals(1, viewModel.uiState.value.allPlaces.size)

        // 3. Delete custom category with transfer to fallback category ("cat_shops")
        val fallbackCat = viewModel.uiState.value.categories.first { it.name == "Магазины" }
        viewModel.deleteCustomCategory(customCat.id, fallbackCat.id)

        // Category deleted
        assertNull(viewModel.uiState.value.categories.firstOrNull { it.id == customCat.id })
        // Place preserved and safely transferred
        val place = viewModel.uiState.value.allPlaces.first()
        assertEquals(fallbackCat.id, place.categoryId)
    }

    @Test
    fun testPlaceValidation_FutureDateAndRating() = runBlocking {
        val cat = viewModel.uiState.value.categories.first()

        // Blank name error
        viewModel.savePlace(
            name = "   ",
            categoryId = cat.id,
        )
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.allPlaces.isEmpty())

        // Future lastVisitedAt error
        val futureTime = System.currentTimeMillis() + 864000000L
        viewModel.savePlace(
            name = "Кафе Будущее",
            categoryId = cat.id,
            lastVisitedAtEpochMs = futureTime,
        )
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.allPlaces.isEmpty())

        // Invalid rating (11) error
        viewModel.savePlace(
            name = "Кафе Звезда",
            categoryId = cat.id,
            rating = 11,
        )
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.allPlaces.isEmpty())
    }

    @Test
    fun testPlacePhotoAttachment_EncryptedStorage() = runBlocking {
        val cat = viewModel.uiState.value.categories.first()

        viewModel.savePlace(
            name = "Уютное кафе",
            categoryId = cat.id,
            rating = 9,
        )

        val place = viewModel.uiState.value.allPlaces.first()
        val dummyMeta = AttachmentMeta(
            id = "att_test_1",
            encryptedRelativePath = "attachments/att_test_1.enc",
            mimeType = "image/jpeg",
            sizeBytes = 1024,
            width = 800,
            height = 600,
        )
        placeRepo.addPlaceAttachment(place.id, dummyMeta)

        viewModel.openPlaceDetails(place)

        val attachments = viewModel.uiState.value.selectedPlaceAttachments
        assertEquals(1, attachments.size)
        assertEquals("att_test_1", attachments.first().id)
        assertEquals("image/jpeg", attachments.first().mimeType)
    }
}

// In-Memory test doubles
class InMemoryCategoryRepository : CategoryRepository {
    private val categories = mutableListOf<Category>()

    override suspend fun getCategories(kind: CategoryKind?): List<Category> =
        if (kind != null) categories.filter { it.kind == kind } else categories.toList()

    override suspend fun getCategoryById(id: String): Category? =
        categories.firstOrNull { it.id == id }

    override suspend fun saveCategory(category: Category) {
        categories.removeAll { it.id == category.id }
        categories.add(category)
    }

    override suspend fun deleteCategory(id: String) {
        categories.removeAll { it.id == id }
    }

    override suspend fun ensureDefaultCategories() {
        DefaultCategories.PRESET_CATEGORIES.forEach { preset ->
            if (categories.none { it.id == preset.id }) {
                categories.add(preset)
            }
        }
    }
}

class InMemoryPlaceRepository : PlaceRepository {
    private val places = mutableListOf<Place>()
    private val attachments = mutableMapOf<String, MutableList<AttachmentMeta>>()

    override suspend fun getPlaces(categoryId: String?): List<Place> {
        val filtered = if (categoryId != null) places.filter { it.categoryId == categoryId } else places
        return filtered.sortedWith(
            compareByDescending<Place> { it.isFavorite }
                .thenByDescending { it.lastVisitedAtEpochMs ?: 0L }
                .thenByDescending { it.updatedAtEpochMs },
        )
    }

    override suspend fun getPlaceById(id: String): Place? = places.firstOrNull { it.id == id }

    override suspend fun savePlace(place: Place) {
        places.removeAll { it.id == place.id }
        places.add(place)
    }

    override suspend fun deletePlace(id: String) {
        places.removeAll { it.id == id }
        attachments.remove(id)
    }

    override suspend fun getPlaceAttachments(placeId: String): List<AttachmentMeta> =
        attachments[placeId] ?: emptyList()

    override suspend fun addPlaceAttachment(placeId: String, attachment: AttachmentMeta) {
        attachments.getOrPut(placeId) { mutableListOf() }.add(attachment)
    }

    override suspend fun updateCategoryForPlaces(oldCategoryId: String, newCategoryId: String) {
        val updated = places.map {
            if (it.categoryId == oldCategoryId) it.copy(categoryId = newCategoryId) else it
        }
        places.clear()
        places.addAll(updated)
    }
}

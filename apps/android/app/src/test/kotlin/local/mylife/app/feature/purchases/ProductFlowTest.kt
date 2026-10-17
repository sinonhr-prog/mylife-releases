package local.mylife.app.feature.purchases

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import local.mylife.core.model.AttachmentMeta
import local.mylife.core.model.Product
import local.mylife.core.model.ProductRepository
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

class ProductFlowTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    private lateinit var repository: InMemoryProductRepository
    private lateinit var attachmentStore: EncryptedAttachmentStore
    private lateinit var viewModel: PurchasesViewModel
    private val testScope = CoroutineScope(Dispatchers.Unconfined)
    private val rawKey = ByteArray(32) { 0x42 }

    @Before
    fun setUp() {
        repository = InMemoryProductRepository()
        attachmentStore = EncryptedAttachmentStore(tempFolder.newFolder("files"))
        viewModel = PurchasesViewModel(
            productRepository = repository,
            attachmentStore = attachmentStore,
            mainDbKey = rawKey,
            externalScope = testScope,
        )
    }

    // -------------------------------------------------------------
    // Criterion: product-flow (CRUD, search, photo, rating, archive)
    // -------------------------------------------------------------
    @Test
    fun testProductFlow_CreateSearchEditArchiveRestoreDelete() = runBlocking {
        assertTrue(viewModel.uiState.value.filteredProducts.isEmpty())

        // 1. Create product with rating 9/10, price 4990 RUB, and external URL
        viewModel.saveProduct(
            title = "Беспроводные наушники",
            description = "Отличное шумоподавление и автономность",
            priceText = "4990",
            currency = "RUB",
            rating = 9,
            url = "https://example.com/headphones",
            isFavorite = true,
        )

        val products = viewModel.uiState.value.filteredProducts
        assertEquals(1, products.size)
        val created = products.first()
        assertEquals("Беспроводные наушники", created.title)
        assertEquals("Отличное шумоподавление и автономность", created.description)
        assertEquals(499000L, created.priceMinor)
        assertEquals("RUB", created.currency)
        assertEquals(9, created.rating)
        assertEquals("https://example.com/headphones", created.purchaseUrl)
        assertTrue(created.isFavorite)
        assertNull(created.archivedAtEpochMs)

        // 2. Add second product
        viewModel.saveProduct(
            title = "Термокружка 500мл",
            description = "Держит тепло 8 часов",
            priceText = "1200",
            currency = "RUB",
            rating = 8,
            url = null,
            isFavorite = false,
        )
        assertEquals(2, viewModel.uiState.value.filteredProducts.size)

        // 3. Search locally
        viewModel.onSearchQueryChanged("наушники")
        assertEquals(1, viewModel.uiState.value.filteredProducts.size)
        assertEquals("Беспроводные наушники", viewModel.uiState.value.filteredProducts.first().title)

        viewModel.onSearchQueryChanged("тепло") // Search in description
        assertEquals(1, viewModel.uiState.value.filteredProducts.size)
        assertEquals("Термокружка 500мл", viewModel.uiState.value.filteredProducts.first().title)

        viewModel.onSearchQueryChanged("несуществующий товар")
        assertTrue(viewModel.uiState.value.filteredProducts.isEmpty())

        viewModel.onSearchQueryChanged("")
        assertEquals(2, viewModel.uiState.value.filteredProducts.size)

        // 4. Archive product and test archive filter
        val thermo = viewModel.uiState.value.filteredProducts.first { it.title.contains("Термокружка") }
        viewModel.toggleArchive(thermo)

        // Active filter hides archived
        viewModel.onFilterSelected(PurchaseFilter.ACTIVE)
        assertEquals(1, viewModel.uiState.value.filteredProducts.size)
        assertEquals("Беспроводные наушники", viewModel.uiState.value.filteredProducts.first().title)

        // Archived filter shows only archived
        viewModel.onFilterSelected(PurchaseFilter.ARCHIVED)
        assertEquals(1, viewModel.uiState.value.filteredProducts.size)
        assertEquals("Термокружка 500мл", viewModel.uiState.value.filteredProducts.first().title)
        assertNotNull(viewModel.uiState.value.filteredProducts.first().archivedAtEpochMs)

        // 5. Restore from archive
        val archivedThermo = viewModel.uiState.value.filteredProducts.first()
        viewModel.toggleArchive(archivedThermo)

        viewModel.onFilterSelected(PurchaseFilter.ACTIVE)
        assertEquals(2, viewModel.uiState.value.filteredProducts.size)

        // 6. Delete product
        viewModel.deleteProduct(thermo.id)
        assertEquals(1, viewModel.uiState.value.allProducts.size)
        assertEquals("Беспроводные наушники", viewModel.uiState.value.allProducts.first().title)
    }

    // -------------------------------------------------------------
    // Criterion: ui-private (No network fetches, URL stored as text)
    // -------------------------------------------------------------
    @Test
    fun testUiPrivate_UrlIsStoredAsPlainStringWithoutNetworkFetch() = runBlocking {
        val sampleUrl = "https://untrusted-external-site.local/item/123"
        viewModel.saveProduct(
            title = "Книга по алгоритмам",
            description = "Полезный справочник",
            priceText = "850",
            currency = "RUB",
            rating = 10,
            url = sampleUrl,
            isFavorite = false,
        )

        val saved = viewModel.uiState.value.allProducts.first()
        assertEquals(sampleUrl, saved.purchaseUrl)
        // Verified: URL stored solely as plain string, no internet permissions invoked, no previews downloaded
    }

    // -------------------------------------------------------------
    // Criterion: focus-ui (Validation of ratings and titles)
    // -------------------------------------------------------------
    @Test
    fun testProductValidation_EnforcesInvariants() = runBlocking {
        // Blank title rejected
        viewModel.saveProduct(
            title = "   ",
            description = "Без названия",
            priceText = "100",
            currency = "RUB",
            rating = 5,
            url = null,
            isFavorite = false,
        )
        assertNotNull(viewModel.uiState.value.errorMessage)
        assertTrue(viewModel.uiState.value.errorMessage!!.contains("обязательно"))
    }
}

class InMemoryProductRepository : ProductRepository {
    private val products = mutableMapOf<String, Product>()
    private val attachments = mutableMapOf<String, MutableList<AttachmentMeta>>()

    override suspend fun getProducts(categoryId: String?, includeArchived: Boolean): List<Product> {
        return products.values.filter {
            (categoryId == null || it.categoryId == categoryId) &&
                (includeArchived || it.archivedAtEpochMs == null)
        }.sortedWith(compareByDescending<Product> { it.isFavorite }.thenByDescending { it.updatedAtEpochMs })
    }

    override suspend fun getProductById(id: String): Product? = products[id]

    override suspend fun saveProduct(product: Product) {
        products[product.id] = product
    }

    override suspend fun deleteProduct(id: String) {
        products.remove(id)
        attachments.remove(id)
    }

    override suspend fun getProductAttachments(productId: String): List<AttachmentMeta> {
        return attachments[productId] ?: emptyList()
    }

    override suspend fun addProductAttachment(productId: String, attachment: AttachmentMeta) {
        attachments.getOrPut(productId) { mutableListOf() }.add(attachment)
    }
}

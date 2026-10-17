package local.mylife.app.feature.purchases

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import local.mylife.core.model.AttachmentMeta
import local.mylife.core.model.Product
import local.mylife.core.model.ProductRepository
import local.mylife.core.storage.files.EncryptedAttachmentStore
import java.io.ByteArrayInputStream
import java.util.UUID

enum class PurchaseFilter {
    ALL,
    ACTIVE,
    FAVORITE,
    ARCHIVED,
}

data class PurchasesUiState(
    val searchQuery: String = "",
    val filter: PurchaseFilter = PurchaseFilter.ALL,
    val allProducts: List<Product> = emptyList(),
    val filteredProducts: List<Product> = emptyList(),
    val selectedProduct: Product? = null,
    val selectedProductAttachments: List<AttachmentMeta> = emptyList(),
    val selectedProductPhotoBytes: ByteArray? = null,
    val isLoading: Boolean = false,
    val errorMessage: String? = null,
    val isAddEditSheetOpen: Boolean = false,
    val productToEdit: Product? = null,
)

class PurchasesViewModel(
    private val productRepository: ProductRepository,
    private val attachmentStore: EncryptedAttachmentStore,
    private val mainDbKey: ByteArray,
    private val externalScope: kotlinx.coroutines.CoroutineScope? = null,
) : ViewModel() {

    private val scope get() = externalScope ?: viewModelScope

    private val _uiState = MutableStateFlow(PurchasesUiState())
    val uiState: StateFlow<PurchasesUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    fun loadProducts() {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                // Include archived to allow switching between views
                val products = productRepository.getProducts(includeArchived = true)
                _uiState.update { current ->
                    val filtered = applyFilter(products, current.searchQuery, current.filter)
                    current.copy(
                        allProducts = products,
                        filteredProducts = filtered,
                        isLoading = false,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка загрузки") }
            }
        }
    }

    fun onSearchQueryChanged(query: String) {
        _uiState.update { current ->
            val filtered = applyFilter(current.allProducts, query, current.filter)
            current.copy(searchQuery = query, filteredProducts = filtered)
        }
    }

    fun onFilterSelected(filter: PurchaseFilter) {
        _uiState.update { current ->
            val filtered = applyFilter(current.allProducts, current.searchQuery, filter)
            current.copy(filter = filter, filteredProducts = filtered)
        }
    }

    fun openProductDetails(product: Product) {
        scope.launch {
            _uiState.update { it.copy(selectedProduct = product, selectedProductPhotoBytes = null) }
            try {
                val attachments = productRepository.getProductAttachments(product.id)
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
                        selectedProductAttachments = attachments,
                        selectedProductPhotoBytes = photoBytes,
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun closeProductDetails() {
        _uiState.update {
            it.copy(
                selectedProduct = null,
                selectedProductAttachments = emptyList(),
                selectedProductPhotoBytes = null,
            )
        }
    }

    fun openAddProduct() {
        _uiState.update { it.copy(isAddEditSheetOpen = true, productToEdit = null) }
    }

    fun openEditProduct(product: Product) {
        _uiState.update { it.copy(isAddEditSheetOpen = true, productToEdit = product) }
    }

    fun closeAddEditSheet() {
        _uiState.update { it.copy(isAddEditSheetOpen = false, productToEdit = null) }
    }

    fun saveProduct(
        title: String,
        description: String?,
        priceText: String?,
        currency: String?,
        rating: Int?,
        url: String?,
        isFavorite: Boolean,
        imageBytes: ByteArray? = null,
        imageMimeType: String? = null,
    ) {
        scope.launch {
            _uiState.update { it.copy(isLoading = true, errorMessage = null) }
            try {
                val cleanTitle = title.trim()
                require(cleanTitle.isNotBlank()) { "Название покупки обязательно" }
                require(cleanTitle.length <= 160) { "Название не может превышать 160 символов" }

                val priceMinor = if (!priceText.isNullOrBlank()) {
                    val cleanPrice = priceText.replace(",", ".").toDoubleOrNull()
                        ?: throw IllegalArgumentException("Некорректная цена")
                    require(cleanPrice >= 0) { "Цена не может быть отрицательной" }
                    (cleanPrice * 100).toLong()
                } else null

                val currentToEdit = _uiState.value.productToEdit
                val productId = currentToEdit?.id ?: UUID.randomUUID().toString()

                val product = Product(
                    id = productId,
                    categoryId = currentToEdit?.categoryId ?: "default_purchases",
                    title = cleanTitle,
                    description = description?.trim() ?: "",
                    purchaseUrl = url?.trim()?.ifBlank { null },
                    rating = rating,
                    purchasedOnEpochDay = currentToEdit?.purchasedOnEpochDay,
                    priceMinor = priceMinor,
                    currency = if (priceMinor != null) (currency?.ifBlank { "RUB" } ?: "RUB") else null,
                    isFavorite = isFavorite,
                    archivedAtEpochMs = currentToEdit?.archivedAtEpochMs,
                    createdAtEpochMs = currentToEdit?.createdAtEpochMs ?: System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                )

                productRepository.saveProduct(product)

                // If user provided a new image, import and encrypt it
                if (imageBytes != null && imageMimeType != null) {
                    val meta = ByteArrayInputStream(imageBytes).use { input ->
                        attachmentStore.importImage(
                            inputStream = input,
                            rawFileEncryptionKey = mainDbKey,
                            mimeType = imageMimeType,
                        )
                    }
                    productRepository.addProductAttachment(productId, meta)
                }

                _uiState.update { it.copy(isAddEditSheetOpen = false, productToEdit = null) }
                loadProducts()

                // If currently viewing details, update selected product
                if (_uiState.value.selectedProduct?.id == productId) {
                    openProductDetails(product)
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, errorMessage = e.message ?: "Ошибка сохранения") }
            }
        }
    }

    fun toggleFavorite(product: Product) {
        scope.launch {
            try {
                val updated = product.copy(
                    isFavorite = !product.isFavorite,
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
                productRepository.saveProduct(updated)
                loadProducts()
                if (_uiState.value.selectedProduct?.id == product.id) {
                    _uiState.update { it.copy(selectedProduct = updated) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun toggleArchive(product: Product) {
        scope.launch {
            try {
                val isArchived = product.archivedAtEpochMs != null
                val updated = product.copy(
                    archivedAtEpochMs = if (isArchived) null else System.currentTimeMillis(),
                    updatedAtEpochMs = System.currentTimeMillis(),
                )
                productRepository.saveProduct(updated)
                loadProducts()
                if (_uiState.value.selectedProduct?.id == product.id) {
                    _uiState.update { it.copy(selectedProduct = updated) }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    fun deleteProduct(productId: String) {
        scope.launch {
            try {
                val attachments = productRepository.getProductAttachments(productId)
                for (att in attachments) {
                    attachmentStore.deleteAttachmentFiles(att)
                }
                productRepository.deleteProduct(productId)
                _uiState.update { it.copy(selectedProduct = null) }
                loadProducts()
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = e.message) }
            }
        }
    }

    private fun applyFilter(
        products: List<Product>,
        query: String,
        filter: PurchaseFilter,
    ): List<Product> {
        val filteredByQuery = if (query.isBlank()) {
            products
        } else {
            val q = query.trim().lowercase()
            products.filter { p ->
                p.title.lowercase().contains(q) ||
                    (p.description?.lowercase()?.contains(q) == true)
            }
        }

        return when (filter) {
            PurchaseFilter.ALL -> filteredByQuery
            PurchaseFilter.ACTIVE -> filteredByQuery.filter { it.archivedAtEpochMs == null }
            PurchaseFilter.FAVORITE -> filteredByQuery.filter { it.isFavorite && it.archivedAtEpochMs == null }
            PurchaseFilter.ARCHIVED -> filteredByQuery.filter { it.archivedAtEpochMs != null }
        }
    }
}

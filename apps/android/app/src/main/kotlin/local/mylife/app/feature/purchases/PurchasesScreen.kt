package local.mylife.app.feature.purchases

import android.content.Intent
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.mylife.core.designsystem.components.FocusEmptyState
import local.mylife.core.designsystem.components.RatingBadge
import local.mylife.core.designsystem.theme.PaperBackground
import local.mylife.core.designsystem.theme.PaperForeground
import local.mylife.core.designsystem.theme.PaperOnSurfaceVariant
import local.mylife.core.designsystem.theme.PaperOutline
import local.mylife.core.designsystem.theme.PaperSurface
import local.mylife.core.model.Product

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchasesScreen(
    viewModel: PurchasesViewModel,
    modifier: Modifier = Modifier,
    onAddNewRequested: () -> Unit = { viewModel.openAddProduct() },
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

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
                placeholder = { Text("Поиск покупок...", color = PaperOnSurfaceVariant) },
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
                modifier = Modifier.fillMaxWidth(),
            )
        }

        // 2. Filter Pills
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilterChip(
                selected = state.filter == PurchaseFilter.ALL,
                onClick = { viewModel.onFilterSelected(PurchaseFilter.ALL) },
                label = { Text("Все") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PaperForeground,
                    selectedLabelColor = Color.White,
                    containerColor = PaperSurface,
                    labelColor = PaperForeground,
                ),
                border = BorderStroke(1.dp, if (state.filter == PurchaseFilter.ALL) PaperForeground else PaperOutline),
            )
            FilterChip(
                selected = state.filter == PurchaseFilter.ACTIVE,
                onClick = { viewModel.onFilterSelected(PurchaseFilter.ACTIVE) },
                label = { Text("Активные") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PaperForeground,
                    selectedLabelColor = Color.White,
                    containerColor = PaperSurface,
                    labelColor = PaperForeground,
                ),
                border = BorderStroke(1.dp, if (state.filter == PurchaseFilter.ACTIVE) PaperForeground else PaperOutline),
            )
            FilterChip(
                selected = state.filter == PurchaseFilter.FAVORITE,
                onClick = { viewModel.onFilterSelected(PurchaseFilter.FAVORITE) },
                label = { Text("★ Избранное") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PaperForeground,
                    selectedLabelColor = Color.White,
                    containerColor = PaperSurface,
                    labelColor = PaperForeground,
                ),
                border = BorderStroke(1.dp, if (state.filter == PurchaseFilter.FAVORITE) PaperForeground else PaperOutline),
            )
            FilterChip(
                selected = state.filter == PurchaseFilter.ARCHIVED,
                onClick = { viewModel.onFilterSelected(PurchaseFilter.ARCHIVED) },
                label = { Text("В архиве") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = PaperForeground,
                    selectedLabelColor = Color.White,
                    containerColor = PaperSurface,
                    labelColor = PaperForeground,
                ),
                border = BorderStroke(1.dp, if (state.filter == PurchaseFilter.ARCHIVED) PaperForeground else PaperOutline),
            )
        }

        Spacer(modifier = Modifier.height(4.dp))

        // 3. Product List / Empty State / Loading
        if (state.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PaperForeground)
            }
        } else if (state.filteredProducts.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                FocusEmptyState(
                    title = if (state.searchQuery.isNotEmpty()) "Ничего не найдено" else "Покупок пока нет",
                    description = if (state.searchQuery.isNotEmpty()) {
                        "Попробуйте изменить поисковый запрос."
                    } else {
                        "Добавьте первую покупку, чтобы сохранить оценку эксплуатации, фото и детали."
                    },
                    actionText = if (state.searchQuery.isEmpty()) "+ Добавить покупку" else null,
                    onActionClick = { viewModel.openAddProduct() },
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                items(state.filteredProducts, key = { it.id }) { product ->
                    ProductCard(
                        product = product,
                        onClick = { viewModel.openProductDetails(product) },
                        onToggleFavorite = { viewModel.toggleFavorite(product) },
                    )
                }
            }
        }
    }

    // 4. Details Dialog
    if (state.selectedProduct != null) {
        val prod = state.selectedProduct!!
        ProductDetailsDialog(
            product = prod,
            photoBytes = state.selectedProductPhotoBytes,
            onDismiss = { viewModel.closeProductDetails() },
            onEdit = {
                viewModel.closeProductDetails()
                viewModel.openEditProduct(prod)
            },
            onToggleArchive = { viewModel.toggleArchive(prod) },
            onDelete = { viewModel.deleteProduct(prod.id) },
            onOpenUrl = { url ->
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                    context.startActivity(intent)
                } catch (_: Exception) {}
            },
        )
    }

    // 5. Add / Edit Dialog
    if (state.isAddEditSheetOpen) {
        AddEditProductDialog(
            productToEdit = state.productToEdit,
            onDismiss = { viewModel.closeAddEditSheet() },
            onSave = { title, desc, price, curr, rating, url, fav, imgBytes, imgMime ->
                viewModel.saveProduct(title, desc, price, curr, rating, url, fav, imgBytes, imgMime)
            },
        )
    }
}

@Composable
fun ProductCard(
    product: Product,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, PaperOutline),
        colors = CardDefaults.cardColors(containerColor = PaperSurface),
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = product.title,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PaperForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )

                IconButton(
                    onClick = onToggleFavorite,
                    modifier = Modifier.size(36.dp),
                ) {
                    Text(
                        text = if (product.isFavorite) "★" else "☆",
                        fontSize = 20.sp,
                        color = if (product.isFavorite) PaperForeground else PaperOnSurfaceVariant,
                    )
                }
            }

            if (!product.description.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = product.description!!,
                    fontSize = 12.sp,
                    lineHeight = 16.sp,
                    color = PaperOnSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val priceFormatted = product.priceMinor?.let { minor ->
                    val main = minor / 100
                    val kopeks = minor % 100
                    val curr = product.currency ?: "RUB"
                    if (kopeks > 0) "$main.${"%02d".format(kopeks)} $curr" else "$main $curr"
                } ?: "Цена не указана"

                Text(
                    text = priceFormatted,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = PaperForeground,
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (product.archivedAtEpochMs != null) {
                        Surface(
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, PaperOutline),
                            color = PaperBackground,
                        ) {
                            Text(
                                text = "Архив",
                                fontSize = 10.sp,
                                color = PaperOnSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                            )
                        }
                    }

                    if (product.rating != null) {
                        RatingBadge(rating = product.rating!!)
                    }
                }
            }
        }
    }
}

@Composable
fun ProductDetailsDialog(
    product: Product,
    photoBytes: ByteArray?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onToggleArchive: () -> Unit,
    onDelete: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    var showDeleteConfirmation by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = product.title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaperForeground,
                    modifier = Modifier.weight(1f),
                )
                if (product.isFavorite) {
                    Text(text = "★", fontSize = 18.sp, color = PaperForeground)
                }
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                // Photo preview if available
                if (photoBytes != null) {
                    val bitmap = remember(photoBytes) {
                        BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)?.asImageBitmap()
                    }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Зашифрованное фото покупки",
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(180.dp)
                                .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }

                if (product.rating != null) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Оценка эксплуатации: ", fontSize = 13.sp, color = PaperOnSurfaceVariant)
                        RatingBadge(rating = product.rating!!)
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                }

                product.priceMinor?.let { minor ->
                    val main = minor / 100
                    val kopeks = minor % 100
                    val curr = product.currency ?: "RUB"
                    val priceStr = if (kopeks > 0) "$main.${"%02d".format(kopeks)} $curr" else "$main $curr"
                    Text("Стоимость: $priceStr", fontSize = 13.sp, color = PaperForeground)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (!product.description.isNullOrBlank()) {
                    Text("Описание:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PaperForeground)
                    Text(product.description!!, fontSize = 13.sp, lineHeight = 18.sp, color = PaperForeground)
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (!product.purchaseUrl.isNullOrBlank()) {
                    Text("Ссылка:", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = PaperForeground)
                    Text(
                        text = product.purchaseUrl!!,
                        fontSize = 12.sp,
                        color = Color(0xFF1976D2),
                        modifier = Modifier.clickable { onOpenUrl(product.purchaseUrl!!) },
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (product.archivedAtEpochMs != null) {
                    Text("Статус: В архиве", fontSize = 12.sp, color = PaperOnSurfaceVariant)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onEdit) {
                Text("Изменить", color = PaperForeground)
            }
        },
        dismissButton = {
            Row {
                TextButton(onClick = { showDeleteConfirmation = true }) {
                    Text("Удалить", color = Color(0xFFD32F2F))
                }
                TextButton(onClick = onToggleArchive) {
                    Text(if (product.archivedAtEpochMs != null) "Восстановить" else "В архив", color = PaperForeground)
                }
                TextButton(onClick = onDismiss) {
                    Text("Закрыть", color = PaperForeground)
                }
            }
        },
        containerColor = PaperBackground,
    )

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = { Text("Удалить покупку?", color = PaperForeground) },
            text = { Text("Запись и прикреплённые зашифрованные файлы будут безвозвратно удалены.", color = PaperOnSurfaceVariant) },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFD32F2F)),
                ) {
                    Text("Удалить", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text("Отмена", color = PaperForeground)
                }
            },
            containerColor = PaperBackground,
        )
    }
}

@Composable
fun AddEditProductDialog(
    productToEdit: Product?,
    onDismiss: () -> Unit,
    onSave: (
        title: String,
        description: String?,
        priceText: String?,
        currency: String?,
        rating: Int?,
        url: String?,
        isFavorite: Boolean,
        imageBytes: ByteArray?,
        imageMimeType: String?,
    ) -> Unit,
) {
    var title by remember { mutableStateOf(productToEdit?.title ?: "") }
    var description by remember { mutableStateOf(productToEdit?.description ?: "") }
    var priceText by remember {
        mutableStateOf(productToEdit?.priceMinor?.let { (it / 100).toString() } ?: "")
    }
    var currency by remember { mutableStateOf(productToEdit?.currency ?: "RUB") }
    var rating by remember { mutableFloatStateOf(productToEdit?.rating?.toFloat() ?: 8f) }
    var hasRating by remember { mutableStateOf(productToEdit?.rating != null) }
    var url by remember { mutableStateOf(productToEdit?.purchaseUrl ?: "") }
    var isFavorite by remember { mutableStateOf(productToEdit?.isFavorite ?: false) }

    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedImageMimeType by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                context.contentResolver.openInputStream(uri)?.use { stream ->
                    selectedImageBytes = stream.readBytes()
                    selectedImageMimeType = mime
                }
            } catch (_: Exception) {}
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (productToEdit != null) "Редактировать покупку" else "Новая покупка",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = PaperForeground,
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { if (it.length <= 160) title = it },
                    label = { Text("Название *") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PaperForeground,
                        unfocusedBorderColor = PaperOutline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Описание и опыт использования") },
                    minLines = 2,
                    maxLines = 4,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PaperForeground,
                        unfocusedBorderColor = PaperOutline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = priceText,
                        onValueChange = { priceText = it },
                        label = { Text("Цена") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PaperForeground,
                            unfocusedBorderColor = PaperOutline,
                        ),
                        modifier = Modifier.weight(1.5f),
                    )

                    OutlinedTextField(
                        value = currency,
                        onValueChange = { currency = it.uppercase() },
                        label = { Text("Валюта") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PaperForeground,
                            unfocusedBorderColor = PaperOutline,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }
                Spacer(modifier = Modifier.height(12.dp))

                // Rating slider 1..10
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (hasRating) "Рейтинг: ${rating.toInt()} / 10" else "Рейтинг: не указан",
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        color = PaperForeground,
                    )
                    TextButton(onClick = { hasRating = !hasRating }) {
                        Text(if (hasRating) "Убрать" else "Указать", fontSize = 11.sp, color = PaperForeground)
                    }
                }
                if (hasRating) {
                    Slider(
                        value = rating,
                        onValueChange = { rating = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors = SliderDefaults.colors(
                            thumbColor = PaperForeground,
                            activeTrackColor = PaperForeground,
                        ),
                    )
                }
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = url,
                    onValueChange = { url = it },
                    label = { Text("Ссылка на товар (http/https)") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = PaperForeground,
                        unfocusedBorderColor = PaperOutline,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(modifier = Modifier.height(12.dp))

                // Photo selection
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedButton(
                        onClick = { imagePickerLauncher.launch("image/*") },
                        border = BorderStroke(1.dp, PaperOutline),
                    ) {
                        Text(if (selectedImageBytes != null) "Фото выбрано ✓" else "Прикрепить фото", color = PaperForeground)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("Избранное", fontSize = 13.sp, color = PaperForeground)
                        IconButton(onClick = { isFavorite = !isFavorite }) {
                            Text(if (isFavorite) "★" else "☆", fontSize = 20.sp, color = PaperForeground)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onSave(
                        title,
                        description,
                        priceText,
                        currency,
                        if (hasRating) rating.toInt() else null,
                        url,
                        isFavorite,
                        selectedImageBytes,
                        selectedImageMimeType,
                    )
                },
                enabled = title.isNotBlank(),
                colors = ButtonDefaults.buttonColors(containerColor = PaperForeground),
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

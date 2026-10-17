package local.mylife.app.feature.places

import android.graphics.BitmapFactory
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
import local.mylife.core.model.Category
import local.mylife.core.model.Place
import local.mylife.core.model.PlaceVisitStatus
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlacesScreen(
    viewModel: PlacesViewModel,
    modifier: Modifier = Modifier,
    onAddNewRequested: () -> Unit = { viewModel.openAddPlace() },
) {
    val state by viewModel.uiState.collectAsState()
    val context = LocalContext.current

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(PaperBackground),
    ) {
        // 1. Search Bar
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = state.searchQuery,
                onValueChange = viewModel::onSearchQueryChanged,
                placeholder = { Text("Поиск мест...", color = PaperOnSurfaceVariant) },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                colors =
                    OutlinedTextFieldDefaults.colors(
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
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "Поле поиска мест" },
            )
        }

        // 2. Category Chips
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FilterChip(
                selected = state.selectedCategoryId == null,
                onClick = { viewModel.selectCategory(null) },
                label = { Text("Все места") },
                colors =
                    FilterChipDefaults.filterChipColors(
                        selectedContainerColor = PaperForeground,
                        selectedLabelColor = Color.White,
                        containerColor = PaperSurface,
                        labelColor = PaperForeground,
                    ),
                border = BorderStroke(1.dp, if (state.selectedCategoryId == null) PaperForeground else PaperOutline),
                shape = RoundedCornerShape(16.dp),
            )

            state.categories.forEach { category ->
                FilterChip(
                    selected = state.selectedCategoryId == category.id,
                    onClick = { viewModel.selectCategory(category.id) },
                    label = { Text(category.name) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PaperForeground,
                            selectedLabelColor = Color.White,
                            containerColor = PaperSurface,
                            labelColor = PaperForeground,
                        ),
                    border = BorderStroke(1.dp, if (state.selectedCategoryId == category.id) PaperForeground else PaperOutline),
                    shape = RoundedCornerShape(16.dp),
                )
            }

            // Button to add custom category
            OutlinedButton(
                onClick = { viewModel.openAddCategoryDialog() },
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, PaperOutline),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                modifier = Modifier.defaultMinSize(minHeight = 48.dp),
            ) {
                Text("+ Категория", fontSize = 12.sp, color = PaperForeground)
            }
        }

        // 3. Status Filters
        Row(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val filters =
                listOf(
                    PlaceStatusFilter.ALL to "Все",
                    PlaceStatusFilter.WANT to "Хочу посетить",
                    PlaceStatusFilter.VISITED to "Посетил",
                    PlaceStatusFilter.FAVORITE to "★ Избранное",
                )
            filters.forEach { (filter, title) ->
                FilterChip(
                    selected = state.statusFilter == filter,
                    onClick = { viewModel.onStatusFilterSelected(filter) },
                    label = { Text(title) },
                    colors =
                        FilterChipDefaults.filterChipColors(
                            selectedContainerColor = PaperForeground,
                            selectedLabelColor = Color.White,
                            containerColor = PaperSurface,
                            labelColor = PaperForeground,
                        ),
                    border = BorderStroke(1.dp, if (state.statusFilter == filter) PaperForeground else PaperOutline),
                    shape = RoundedCornerShape(16.dp),
                )
            }
        }

        // 4. Main List or Empty State
        if (state.isLoading && state.allPlaces.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = PaperForeground)
            }
        } else if (state.filteredPlaces.isEmpty()) {
            Box(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                val emptyDesc =
                    if (state.searchQuery.isNotBlank()) {
                        "По запросу «${state.searchQuery}» ничего не найдено."
                    } else if (state.statusFilter != PlaceStatusFilter.ALL) {
                        "В выбранном фильтре нет мест."
                    } else {
                        "Здесь будут магазины, парки, кафе и другие места, куда хочется возвращаться."
                    }
                FocusEmptyState(
                    title = if (state.searchQuery.isNotBlank()) "Ничего не найдено" else "Список мест пуст",
                    description = emptyDesc,
                    actionText = "+ Добавить место",
                    onActionClick = onAddNewRequested,
                )
            }
        } else {
            LazyColumn(
                modifier =
                    Modifier
                        .fillMaxSize()
                        .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp),
            ) {
                items(state.filteredPlaces, key = { it.id }) { place ->
                    PlaceCard(
                        place = place,
                        categories = state.categories,
                        onClick = { viewModel.openPlaceDetails(place) },
                        onFavoriteClick = { viewModel.toggleFavorite(place) },
                    )
                }
            }
        }
    }

    // Details Dialog
    state.selectedPlace?.let { place ->
        PlaceDetailsDialog(
            place = place,
            categories = state.categories,
            photoBytes = state.selectedPlacePhotoBytes,
            onDismiss = { viewModel.closePlaceDetails() },
            onEdit = {
                viewModel.closePlaceDetails()
                viewModel.openEditPlace(place)
            },
            onDelete = {
                viewModel.deletePlace(place.id)
            },
            onToggleFavorite = {
                viewModel.toggleFavorite(place)
            },
            onMarkVisited = {
                viewModel.markVisited(place)
            },
        )
    }

    // Add / Edit Sheet
    if (state.isAddEditSheetOpen) {
        AddEditPlaceDialog(
            placeToEdit = state.placeToEdit,
            categories = state.categories,
            defaultCategoryId = state.selectedCategoryId ?: state.categories.firstOrNull()?.id ?: "",
            onDismiss = { viewModel.closeAddEditSheet() },
            onSave = { name, categoryId, address, desc, status, lastVisited, rating, isFav, bytes, mime ->
                viewModel.savePlace(
                    name = name,
                    categoryId = categoryId,
                    addressText = address,
                    description = desc,
                    visitStatus = status,
                    lastVisitedAtEpochMs = lastVisited,
                    rating = rating,
                    isFavorite = isFav,
                    imageBytes = bytes,
                    imageMimeType = mime,
                )
            },
        )
    }

    // Add Custom Category Dialog
    if (state.isAddCategoryDialogOpen) {
        AddCategoryDialog(
            onDismiss = { viewModel.closeAddCategoryDialog() },
            onSave = { name -> viewModel.addCustomCategory(name) },
        )
    }
}

@Composable
fun PlaceCard(
    place: Place,
    categories: List<Category>,
    onClick: () -> Unit,
    onFavoriteClick: () -> Unit,
) {
    val categoryName = categories.firstOrNull { it.id == place.categoryId }?.name ?: "Место"

    Card(
        modifier =
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = PaperSurface),
        border = BorderStroke(1.dp, PaperOutline),
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Category badge
                Surface(
                    shape = RoundedCornerShape(6.dp),
                    color = PaperBackground,
                    border = BorderStroke(1.dp, PaperOutline),
                ) {
                    Text(
                        text = categoryName.uppercase(),
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaperOnSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp),
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    place.rating?.let { rating ->
                        RatingBadge(rating = rating)
                        Spacer(modifier = Modifier.width(8.dp))
                    }
                    IconButton(
                        onClick = onFavoriteClick,
                        modifier = Modifier.size(48.dp),
                    ) {
                        Text(
                            text = if (place.isFavorite) "★" else "☆",
                            fontSize = 20.sp,
                            color = if (place.isFavorite) PaperForeground else PaperOnSurfaceVariant,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = place.name,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                color = PaperForeground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )

            if (place.addressText.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "📍 ${place.addressText}",
                    fontSize = 13.sp,
                    color = PaperForeground,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            if (place.description.isNotBlank()) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = place.description,
                    fontSize = 12.sp,
                    color = PaperOnSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Visit status line
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (place.visitStatus == PlaceVisitStatus.VISITED) {
                    val dateStr =
                        place.lastVisitedAtEpochMs?.let {
                            val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                            " · " + sdf.format(Date(it))
                        } ?: ""
                    Text(
                        text = "✓ Посетил$dateStr",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = PaperForeground,
                    )
                } else {
                    Text(
                        text = "Хочу посетить",
                        fontSize = 12.sp,
                        color = PaperOnSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
fun PlaceDetailsDialog(
    place: Place,
    categories: List<Category>,
    photoBytes: ByteArray?,
    onDismiss: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onToggleFavorite: () -> Unit,
    onMarkVisited: () -> Unit,
) {
    var showDeleteConfirm by remember { mutableStateOf(false) }
    val categoryName = categories.firstOrNull { it.id == place.categoryId }?.name ?: "Место"

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = place.name,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaperForeground,
                    modifier = Modifier.weight(1f),
                )
                IconButton(onClick = onToggleFavorite) {
                    Text(
                        text = if (place.isFavorite) "★" else "☆",
                        fontSize = 24.sp,
                        color = if (place.isFavorite) PaperForeground else PaperOnSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
            ) {
                // Category & Rating
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Категория: $categoryName", fontSize = 13.sp, color = PaperOnSurfaceVariant)
                    place.rating?.let { RatingBadge(rating = it) }
                }

                if (photoBytes != null) {
                    Spacer(modifier = Modifier.height(12.dp))
                    val bitmap =
                        remember(photoBytes) {
                            BitmapFactory.decodeByteArray(photoBytes, 0, photoBytes.size)?.asImageBitmap()
                        }
                    if (bitmap != null) {
                        Image(
                            bitmap = bitmap,
                            contentDescription = "Фото места",
                            modifier =
                                Modifier
                                    .fillMaxWidth()
                                    .height(200.dp)
                                    .clip(RoundedCornerShape(12.dp)),
                            contentScale = ContentScale.Crop,
                        )
                    }
                }

                if (place.addressText.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Адрес", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PaperOnSurfaceVariant)
                    Text(place.addressText, fontSize = 14.sp, color = PaperForeground)
                }

                if (place.description.isNotBlank()) {
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Описание", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PaperOnSurfaceVariant)
                    Text(place.description, fontSize = 14.sp, color = PaperForeground)
                }

                Spacer(modifier = Modifier.height(12.dp))
                Text("Статус", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PaperOnSurfaceVariant)
                if (place.visitStatus == PlaceVisitStatus.VISITED) {
                    val dateStr =
                        place.lastVisitedAtEpochMs?.let {
                            val sdf = SimpleDateFormat("dd MMMM yyyy", Locale.getDefault())
                            sdf.format(Date(it))
                        } ?: "Дата не указана"
                    Text("Посещено ($dateStr)", fontSize = 14.sp, color = PaperForeground)
                } else {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text("Хочу посетить", fontSize = 14.sp, color = PaperForeground)
                        OutlinedButton(
                            onClick = onMarkVisited,
                            shape = RoundedCornerShape(8.dp),
                            border = BorderStroke(1.dp, PaperForeground),
                            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        ) {
                            Text("Отметить визит", fontSize = 12.sp, color = PaperForeground)
                        }
                    }
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
            title = { Text("Удалить место?") },
            text = { Text("Запись о месте будет удалена без возможности восстановления.") },
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
fun AddEditPlaceDialog(
    placeToEdit: Place?,
    categories: List<Category>,
    defaultCategoryId: String,
    onDismiss: () -> Unit,
    onSave: (
        name: String,
        categoryId: String,
        address: String,
        desc: String,
        status: PlaceVisitStatus,
        lastVisited: Long?,
        rating: Int?,
        isFav: Boolean,
        imageBytes: ByteArray?,
        imageMime: String?,
    ) -> Unit,
) {
    val context = LocalContext.current
    var name by remember { mutableStateOf(placeToEdit?.name ?: "") }
    var selectedCategoryId by remember { mutableStateOf(placeToEdit?.categoryId ?: defaultCategoryId) }
    var address by remember { mutableStateOf(placeToEdit?.addressText ?: "") }
    var description by remember { mutableStateOf(placeToEdit?.description ?: "") }
    var visitStatus by remember { mutableStateOf(placeToEdit?.visitStatus ?: PlaceVisitStatus.WANT) }
    var isFavorite by remember { mutableStateOf(placeToEdit?.isFavorite ?: false) }

    var hasRating by remember { mutableStateOf(placeToEdit?.rating != null) }
    var ratingValue by remember { mutableFloatStateOf((placeToEdit?.rating ?: 8).toFloat()) }

    var selectedImageBytes by remember { mutableStateOf<ByteArray?>(null) }
    var selectedImageMime by remember { mutableStateOf<String?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val photoPickerLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.GetContent(),
        ) { uri ->
            if (uri != null) {
                try {
                    val mime = context.contentResolver.getType(uri) ?: "image/jpeg"
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                    if (bytes != null) {
                        selectedImageBytes = bytes
                        selectedImageMime = mime
                    }
                } catch (e: Exception) {
                    errorMessage = "Ошибка выбора фото: ${e.message}"
                }
            }
        }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = if (placeToEdit == null) "Новое место" else "Редактировать место",
                fontWeight = FontWeight.Bold,
                color = PaperForeground,
            )
        },
        text = {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                errorMessage?.let {
                    Text(it, color = Color(0xFFD32F2F), fontSize = 12.sp)
                }

                // Name
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Название *") },
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PaperForeground,
                            unfocusedBorderColor = PaperOutline,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Category selector
                Text("Категория", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PaperOnSurfaceVariant)
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    categories.forEach { cat ->
                        FilterChip(
                            selected = selectedCategoryId == cat.id,
                            onClick = { selectedCategoryId = cat.id },
                            label = { Text(cat.name, fontSize = 12.sp) },
                            colors =
                                FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = PaperForeground,
                                    selectedLabelColor = Color.White,
                                    containerColor = PaperSurface,
                                    labelColor = PaperForeground,
                                ),
                            border = BorderStroke(1.dp, if (selectedCategoryId == cat.id) PaperForeground else PaperOutline),
                            shape = RoundedCornerShape(12.dp),
                        )
                    }
                }

                // Address
                OutlinedTextField(
                    value = address,
                    onValueChange = { address = it },
                    label = { Text("Адрес (вручную)") },
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PaperForeground,
                            unfocusedBorderColor = PaperOutline,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Description
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Заметки / Описание") },
                    maxLines = 4,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PaperForeground,
                            unfocusedBorderColor = PaperOutline,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Visit Status
                Text("Статус визита", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = PaperOnSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = visitStatus == PlaceVisitStatus.WANT,
                        onClick = { visitStatus = PlaceVisitStatus.WANT },
                        label = { Text("Хочу посетить") },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PaperForeground,
                                selectedLabelColor = Color.White,
                            ),
                        border = BorderStroke(1.dp, if (visitStatus == PlaceVisitStatus.WANT) PaperForeground else PaperOutline),
                    )
                    FilterChip(
                        selected = visitStatus == PlaceVisitStatus.VISITED,
                        onClick = { visitStatus = PlaceVisitStatus.VISITED },
                        label = { Text("Посетил") },
                        colors =
                            FilterChipDefaults.filterChipColors(
                                selectedContainerColor = PaperForeground,
                                selectedLabelColor = Color.White,
                            ),
                        border = BorderStroke(1.dp, if (visitStatus == PlaceVisitStatus.VISITED) PaperForeground else PaperOutline),
                    )
                }

                // Rating
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Оценка впечатлений", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    FilterChip(
                        selected = hasRating,
                        onClick = { hasRating = !hasRating },
                        label = { Text(if (hasRating) "${ratingValue.toInt()}/10" else "Без оценки") },
                    )
                }
                if (hasRating) {
                    Slider(
                        value = ratingValue,
                        onValueChange = { ratingValue = it },
                        valueRange = 1f..10f,
                        steps = 8,
                        colors =
                            SliderDefaults.colors(
                                thumbColor = PaperForeground,
                                activeTrackColor = PaperForeground,
                                inactiveTrackColor = PaperOutline,
                            ),
                    )
                }

                // Photo Picker
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (selectedImageBytes != null) "Фото выбрано" else "Добавить фото",
                        fontSize = 13.sp,
                        color = PaperOnSurfaceVariant,
                    )
                    OutlinedButton(
                        onClick = { photoPickerLauncher.launch("image/*") },
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, PaperOutline),
                    ) {
                        Text("Выбрать фото", fontSize = 12.sp, color = PaperForeground)
                    }
                }

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
                    if (name.isBlank()) {
                        errorMessage = "Название обязательно"
                        return@Button
                    }
                    val lastVisited =
                        if (visitStatus == PlaceVisitStatus.VISITED) {
                            placeToEdit?.lastVisitedAtEpochMs ?: System.currentTimeMillis()
                        } else {
                            null
                        }

                    onSave(
                        name.trim(),
                        selectedCategoryId,
                        address.trim(),
                        description.trim(),
                        visitStatus,
                        lastVisited,
                        if (hasRating) ratingValue.toInt() else null,
                        isFavorite,
                        selectedImageBytes,
                        selectedImageMime,
                    )
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

@Composable
fun AddCategoryDialog(
    onDismiss: () -> Unit,
    onSave: (name: String) -> Unit,
) {
    var categoryName by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Новая категория мест", fontWeight = FontWeight.Bold, color = PaperForeground) },
        text = {
            Column {
                error?.let { Text(it, color = Color(0xFFD32F2F), fontSize = 12.sp) }
                OutlinedTextField(
                    value = categoryName,
                    onValueChange = { categoryName = it },
                    label = { Text("Название категории") },
                    singleLine = true,
                    colors =
                        OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = PaperForeground,
                            unfocusedBorderColor = PaperOutline,
                        ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (categoryName.isBlank()) {
                        error = "Название не может быть пустым"
                    } else {
                        onSave(categoryName.trim())
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = PaperForeground),
                shape = RoundedCornerShape(10.dp),
            ) {
                Text("Создать", color = Color.White)
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

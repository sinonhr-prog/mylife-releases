package local.mylife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import local.mylife.app.MyLifeApp
import local.mylife.app.feature.auth.PinScreen
import local.mylife.app.feature.auth.PinViewModel
import local.mylife.app.feature.home.HomeScreen
import local.mylife.app.feature.home.HomeViewModel
import local.mylife.app.feature.notes.NotesScreen
import local.mylife.app.feature.notes.NotesViewModel
import local.mylife.app.feature.places.PlacesScreen
import local.mylife.app.feature.places.PlacesViewModel
import local.mylife.app.feature.purchases.PurchasesScreen
import local.mylife.app.feature.purchases.PurchasesViewModel
import local.mylife.app.feature.tasks.TasksScreen
import local.mylife.app.feature.tasks.TasksViewModel
import local.mylife.app.feature.reminders.RemindersScreen
import local.mylife.app.feature.reminders.RemindersViewModel
import local.mylife.app.feature.settings.updater.UpdateScreen
import local.mylife.app.feature.settings.updater.UpdateViewModel
import local.mylife.core.designsystem.components.FocusEmptyState
import local.mylife.core.designsystem.theme.MyLifeTheme
import local.mylife.core.designsystem.theme.PaperBackground
import local.mylife.core.designsystem.theme.PaperForeground
import local.mylife.core.designsystem.theme.PaperOnSurfaceVariant
import local.mylife.core.designsystem.theme.PaperOutline
import local.mylife.core.designsystem.theme.PaperSurface
import local.mylife.core.security.SessionManager
import local.mylife.core.security.SessionState

data class DrawerItem(
    val id: String,
    val title: String,
    val iconSymbol: String,
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as? MyLifeApp
        val sessionManager = app?.container?.sessionManager
        val updateViewModel = app?.container?.updateRepository?.let { repo ->
            UpdateViewModel(repo)
        }

        setContent {
            MyLifeTheme {
                if (sessionManager != null) {
                    var currentSessionState by remember { mutableStateOf(sessionManager.getSessionState()) }

                    if (currentSessionState is SessionState.Locked) {
                        app.sessionLifecycleManager.enableWindowSecurity(this)
                        val pinViewModel = remember { PinViewModel(sessionManager, "main") }
                        PinScreen(
                            viewModel = pinViewModel,
                            onSuccess = {
                                app.sessionLifecycleManager.disableWindowSecurity(this)
                                currentSessionState = sessionManager.getSessionState()
                            },
                        )
                    } else {
                        val mainDbKey = when (val state = currentSessionState) {
                            is SessionState.MainUnlocked -> state.mainDbKey
                            is SessionState.VaultUnlocked -> state.mainDbKey
                            else -> null
                        }

                        if (mainDbKey != null) {
                            MainDashboardShell(
                                mainDbKey = mainDbKey,
                                app = app,
                                sessionManager = sessionManager,
                                updateViewModel = updateViewModel,
                                onSessionChanged = { currentSessionState = it },
                                onEnableWindowSecurity = { app.sessionLifecycleManager.enableWindowSecurity(this) },
                                onDisableWindowSecurity = { app.sessionLifecycleManager.disableWindowSecurity(this) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainDashboardShell(
    mainDbKey: ByteArray,
    app: MyLifeApp,
    sessionManager: SessionManager,
    updateViewModel: UpdateViewModel? = null,
    onSessionChanged: (SessionState) -> Unit = {},
    onEnableWindowSecurity: () -> Unit = {},
    onDisableWindowSecurity: () -> Unit = {},
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("Главная") }
    var currentSessionState by remember { mutableStateOf(sessionManager.getSessionState()) }
    var showQuickAddDialog by remember { mutableStateOf(false) }

    // Repositories & ViewModels
    val storageSession = remember(mainDbKey) { app.container.getStorageSession(mainDbKey) }
    val productRepo = storageSession.productRepository
    val categoryRepo = storageSession.categoryRepository
    val placeRepo = storageSession.placeRepository
    val taskRepo = storageSession.taskRepository
    val noteRepo = storageSession.noteRepository
    val attachmentStore = storageSession.attachmentStore

    val dashboardRepo = storageSession.dashboardRepository

    val remindersRepo = storageSession.reminderRepository
    val schedulerReconciler = storageSession.schedulerReconciler

    val remindersViewModel = remember(remindersRepo, taskRepo, schedulerReconciler) {
        RemindersViewModel(remindersRepo, taskRepo, schedulerReconciler).apply {
            setDiagnostics(local.mylife.app.feature.reminders.DeviceDiagnosticsHelper.checkDiagnostics(app))
        }
    }

    val homeViewModel = remember(dashboardRepo, taskRepo) {
        HomeViewModel(dashboardRepo, taskRepo)
    }
    val purchasesViewModel = remember(productRepo, attachmentStore, mainDbKey) {
        PurchasesViewModel(productRepo, attachmentStore, mainDbKey)
    }
    val placesViewModel = remember(placeRepo, categoryRepo, attachmentStore, mainDbKey) {
        PlacesViewModel(placeRepo, categoryRepo, attachmentStore, mainDbKey)
    }
    val tasksViewModel = remember(taskRepo) {
        TasksViewModel(taskRepo)
    }
    val notesViewModel = remember(noteRepo) {
        NotesViewModel(noteRepo)
    }

    // Handle Android system Back key
    BackHandler(enabled = drawerState.isOpen || selectedCategory != "Главная") {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            selectedCategory = "Главная"
        }
    }

    val primaryDrawerItems = listOf(
        DrawerItem("Главная", "Главная", "⌂"),
        DrawerItem("Покупки", "Покупки", "🛍"),
        DrawerItem("Магазины", "Магазины", "🏪"),
        DrawerItem("Парки", "Парки", "🌲"),
        DrawerItem("Кафе", "Кафе", "☕"),
        DrawerItem("Рестораны", "Рестораны", "🍴"),
        DrawerItem("Задачи", "Задачи", "✓"),
        DrawerItem("Заметки", "Заметки", "✎"),
        DrawerItem("Напоминания", "Напоминания", "🔔"),
        DrawerItem("Сейф", "Пароли (Сейф)", "🔒"),
    )

    val secondaryDrawerItems = listOf(
        DrawerItem("Настройки", "Настройки", "⚙"),
    )

    // Window security: only active in Vault or PIN
    if (selectedCategory == "Сейф") {
        if (currentSessionState !is SessionState.VaultUnlocked) {
            onEnableWindowSecurity()
            val vaultPinViewModel = remember { PinViewModel(sessionManager, "vault") }
            PinScreen(
                viewModel = vaultPinViewModel,
                onSuccess = {
                    currentSessionState = sessionManager.getSessionState()
                    onSessionChanged(currentSessionState)
                },
            )
            return
        }
    } else {
        onDisableWindowSecurity()
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = PaperBackground,
                drawerContentColor = PaperForeground,
                modifier = Modifier.width(300.dp),
            ) {
                // Drawer Head
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 24.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Моя жизнь",
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = (-0.5).sp,
                        color = PaperForeground,
                    )
                    IconButton(onClick = { scope.launch { drawerState.close() } }) {
                        Text("✕", fontSize = 18.sp, color = PaperForeground)
                    }
                }

                HorizontalDivider(color = PaperOutline)
                Spacer(modifier = Modifier.height(8.dp))

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState()),
                ) {
                    primaryDrawerItems.forEach { item ->
                        NavigationDrawerItem(
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.iconSymbol, fontSize = 16.sp, modifier = Modifier.padding(end = 12.dp))
                                    Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            },
                            selected = selectedCategory == item.id,
                            onClick = {
                                if (selectedCategory == "Сейф" && item.id != "Сейф") {
                                    // Security rule: leaving vault closes vault session
                                    sessionManager.lockVault()
                                    currentSessionState = sessionManager.getSessionState()
                                    onSessionChanged(currentSessionState)
                                }
                                selectedCategory = item.id
                                if (item.id in listOf("Магазины", "Парки", "Кафе", "Рестораны")) {
                                    placesViewModel.selectCategoryByName(item.id)
                                }
                                scope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = PaperForeground,
                                selectedTextColor = Color.White,
                                unselectedContainerColor = PaperBackground,
                                unselectedTextColor = PaperForeground,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider(color = PaperOutline, modifier = Modifier.padding(horizontal = 16.dp))
                    Spacer(modifier = Modifier.height(8.dp))

                    secondaryDrawerItems.forEach { item ->
                        NavigationDrawerItem(
                            label = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(item.iconSymbol, fontSize = 16.sp, modifier = Modifier.padding(end = 12.dp))
                                    Text(item.title, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                                }
                            },
                            selected = selectedCategory == item.id,
                            onClick = {
                                if (selectedCategory == "Сейф") {
                                    sessionManager.lockVault()
                                    currentSessionState = sessionManager.getSessionState()
                                    onSessionChanged(currentSessionState)
                                }
                                selectedCategory = item.id
                                scope.launch { drawerState.close() }
                            },
                            colors = NavigationDrawerItemDefaults.colors(
                                selectedContainerColor = PaperForeground,
                                selectedTextColor = Color.White,
                                unselectedContainerColor = PaperBackground,
                                unselectedTextColor = PaperForeground,
                            ),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .testTag("drawer_item_${item.id}"),
                        )
                    }
                }

                // Drawer Foot
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                ) {
                    HorizontalDivider(color = PaperOutline)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Ваше личное пространство.\nДанные хранятся на телефоне.",
                        fontSize = 11.sp,
                        lineHeight = 15.sp,
                        color = PaperOnSurfaceVariant,
                    )
                }
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = {
                        Text(
                            text = if (selectedCategory == "Главная") "Моя жизнь" else selectedCategory,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaperForeground,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (selectedCategory == "Сейф") {
                                    sessionManager.lockVault()
                                    currentSessionState = sessionManager.getSessionState()
                                    onSessionChanged(currentSessionState)
                                }
                                scope.launch { drawerState.open() }
                            },
                        ) {
                            Text(
                                text = "☰",
                                fontSize = 24.sp,
                                color = PaperForeground,
                            )
                        }
                    },
                    actions = {
                        if (selectedCategory == "Покупки") {
                            IconButton(onClick = { purchasesViewModel.openAddProduct() }) {
                                Text("+", fontSize = 24.sp, color = PaperForeground)
                            }
                        } else if (selectedCategory in listOf("Магазины", "Парки", "Кафе", "Рестораны")) {
                            IconButton(onClick = { placesViewModel.openAddPlace() }) {
                                Text("+", fontSize = 24.sp, color = PaperForeground)
                            }
                        } else if (selectedCategory == "Задачи") {
                            IconButton(onClick = { tasksViewModel.openAddTask() }) {
                                Text("+", fontSize = 24.sp, color = PaperForeground)
                            }
                        } else if (selectedCategory == "Заметки") {
                            IconButton(onClick = { notesViewModel.openAddNote() }) {
                                Text("+", fontSize = 24.sp, color = PaperForeground)
                            }
                        } else if (selectedCategory == "Напоминания") {
                            IconButton(onClick = { remindersViewModel.openAddReminder() }) {
                                Text("+", fontSize = 24.sp, color = PaperForeground)
                            }
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PaperBackground,
                        titleContentColor = PaperForeground,
                        navigationIconContentColor = PaperForeground,
                    ),
                )
            },
            bottomBar = {
                // Persistent bottom action bar in Paper design (NO TABS!)
                Surface(
                    color = PaperBackground,
                    border = BorderStroke(1.dp, PaperOutline),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                    ) {
                        Button(
                            onClick = {
                                when (selectedCategory) {
                                    "Покупки" -> purchasesViewModel.openAddProduct()
                                    in listOf("Магазины", "Парки", "Кафе", "Рестораны") -> placesViewModel.openAddPlace()
                                    "Задачи" -> tasksViewModel.openAddTask()
                                    "Заметки" -> notesViewModel.openAddNote()
                                    "Напоминания" -> remindersViewModel.openAddReminder()
                                    else -> showQuickAddDialog = true
                                }
                            },
                            shape = RoundedCornerShape(13.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = PaperForeground),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                        ) {
                            Text(
                                text = "+ Добавить запись",
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = Color.White,
                            )
                        }
                    }
                }
            },
            containerColor = PaperBackground,
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                when (selectedCategory) {
                    "Главная" -> {
                        HomeScreen(
                            viewModel = homeViewModel,
                            onNavigateToCategory = { selectedCategory = it },
                        )
                    }

                    "Покупки" -> {
                        PurchasesScreen(
                            viewModel = purchasesViewModel,
                        )
                    }

                    "Магазины", "Парки", "Кафе", "Рестораны" -> {
                        PlacesScreen(
                            viewModel = placesViewModel,
                        )
                    }

                    "Задачи" -> {
                        TasksScreen(
                            viewModel = tasksViewModel,
                        )
                    }

                    "Заметки" -> {
                        NotesScreen(
                            viewModel = notesViewModel,
                        )
                    }

                    "Напоминания" -> {
                        local.mylife.app.feature.reminders.RemindersScreen(
                            viewModel = remindersViewModel,
                        )
                    }

                    "Настройки" -> {
                        if (updateViewModel != null) {
                            UpdateScreen(
                                viewModel = updateViewModel,
                                onBack = { selectedCategory = "Главная" },
                            )
                        }
                    }

                    "Сейф" -> {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                        ) {
                            Text(
                                text = "СЕЙФ (ИЗОЛИРОВАННАЯ СЕССИЯ)",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = PaperOnSurfaceVariant,
                                letterSpacing = 1.sp,
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Card(
                                colors = CardDefaults.cardColors(containerColor = PaperSurface),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = "Сейф разблокирован",
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.SemiBold,
                                        color = PaperForeground,
                                    )
                                    Spacer(modifier = Modifier.height(6.dp))
                                    Text(
                                        text = "Отдельная база данных и сессия активны. При выходе в меню Сейф будет автоматически заблокирован.",
                                        fontSize = 12.sp,
                                        color = PaperOnSurfaceVariant,
                                    )
                                    Spacer(modifier = Modifier.height(14.dp))
                                    OutlinedButton(
                                        onClick = {
                                            sessionManager.lockVault()
                                            currentSessionState = sessionManager.getSessionState()
                                            onSessionChanged(currentSessionState)
                                            selectedCategory = "Главная"
                                        },
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(1.dp, PaperForeground),
                                    ) {
                                        Text("Заблокировать сейф", color = PaperForeground)
                                    }
                                }
                            }
                        }
                    }

                    else -> {
                        // Empty states for categories in progress
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            FocusEmptyState(
                                title = selectedCategory,
                                description = "Раздел находится в разработке и будет доступен в следующих этапах.",
                                actionText = "На главную",
                                onActionClick = { selectedCategory = "Главная" },
                            )
                        }
                    }
                }
            }
        }
    }

    // Quick Add Dialog
    if (showQuickAddDialog) {
        AlertDialog(
            onDismissRequest = { showQuickAddDialog = false },
            title = { Text("Добавить запись", fontWeight = FontWeight.Bold, color = PaperForeground) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = {
                            showQuickAddDialog = false
                            selectedCategory = "Покупки"
                            purchasesViewModel.openAddProduct()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PaperSurface),
                        border = BorderStroke(1.dp, PaperOutline),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("🛍 Покупка с оценкой и фото", color = PaperForeground)
                    }

                    Button(
                        onClick = {
                            showQuickAddDialog = false
                            selectedCategory = "Магазины"
                            placesViewModel.openAddPlace()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PaperSurface),
                        border = BorderStroke(1.dp, PaperOutline),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("📍 Место (магазин, парк, кафе...)", color = PaperForeground)
                    }

                    Button(
                        onClick = {
                            showQuickAddDialog = false
                            selectedCategory = "Задачи"
                            tasksViewModel.openAddTask()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PaperSurface),
                        border = BorderStroke(1.dp, PaperOutline),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("✓ Дело в план на день", color = PaperForeground)
                    }

                    Button(
                        onClick = {
                            showQuickAddDialog = false
                            selectedCategory = "Заметки"
                            notesViewModel.openAddNote()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PaperSurface),
                        border = BorderStroke(1.dp, PaperOutline),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("✎ Текстовая заметка", color = PaperForeground)
                    }

                    Button(
                        onClick = {
                            showQuickAddDialog = false
                            selectedCategory = "Напоминания"
                            remindersViewModel.openAddReminder()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = PaperSurface),
                        border = BorderStroke(1.dp, PaperOutline),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("🔔 Напоминание / будильник", color = PaperForeground)
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showQuickAddDialog = false }) {
                    Text("Отмена", color = PaperForeground)
                }
            },
            containerColor = PaperBackground,
        )
    }
}

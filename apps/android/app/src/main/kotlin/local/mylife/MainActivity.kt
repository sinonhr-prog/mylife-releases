package local.mylife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import local.mylife.app.feature.reminders.RemindersScreen
import local.mylife.app.feature.reminders.RemindersViewModel
import local.mylife.app.feature.settings.backup.BackupScreen
import local.mylife.app.feature.settings.backup.BackupViewModel
import local.mylife.app.feature.settings.updater.UpdateScreen
import local.mylife.app.feature.settings.updater.UpdateViewModel
import local.mylife.app.feature.tasks.TasksScreen
import local.mylife.app.feature.tasks.TasksViewModel
import local.mylife.app.feature.vault.VaultScreen
import local.mylife.app.feature.vault.VaultViewModel
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
        window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SECURE)
        val app = application as? MyLifeApp
        val updateViewModel =
            app?.container?.updateRepository?.let { repo ->
                UpdateViewModel(repo)
            }

        setContent {
            MyLifeTheme {
                var sessionManager by remember(app) { mutableStateOf<SessionManager?>(null) }
                androidx.compose.runtime.LaunchedEffect(app) {
                    sessionManager = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) { app?.container?.sessionManager }
                }
                val readySession = sessionManager
                if (readySession != null && app != null) {
                    val currentSessionState by readySession.sessionState.collectAsState()

                    if (currentSessionState is SessionState.Locked) {
                        app.sessionLifecycleManager.enableWindowSecurity(this)
                        val pinViewModel = remember { PinViewModel(readySession, "main") }
                        PinScreen(
                            viewModel = pinViewModel,
                            onSuccess = {
                            },
                        )
                    } else {
                        val mainDbKey =
                            when (val state = currentSessionState) {
                                is SessionState.MainUnlocked -> state.mainDbKey
                                is SessionState.VaultUnlocked -> state.mainDbKey
                                else -> null
                            }

                        if (mainDbKey != null) {
                            MainDashboardShell(
                                mainDbKey = mainDbKey,
                                app = app,
                                sessionManager = readySession,
                                updateViewModel = updateViewModel,
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
) {
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    var selectedCategory by remember { mutableStateOf("Главная") }
    val currentSessionState by sessionManager.sessionState.collectAsState()
    var showQuickAddDialog by remember { mutableStateOf(false) }

    // Repositories & ViewModels
    val storageSession = remember(mainDbKey, selectedCategory) { app.container.getStorageSession(mainDbKey) }
    val productRepo = storageSession.productRepository
    val categoryRepo = storageSession.categoryRepository
    val placeRepo = storageSession.placeRepository
    val taskRepo = storageSession.taskRepository
    val noteRepo = storageSession.noteRepository
    val attachmentStore = storageSession.attachmentStore

    val dashboardRepo = storageSession.dashboardRepository

    val remindersRepo = storageSession.reminderRepository
    val schedulerReconciler = storageSession.schedulerReconciler

    val remindersViewModel =
        remember(remindersRepo, taskRepo, schedulerReconciler) {
            RemindersViewModel(remindersRepo, taskRepo, schedulerReconciler).apply {
                setDiagnostics(local.mylife.app.feature.reminders.DeviceDiagnosticsHelper.checkDiagnostics(app))
            }
        }

    val homeViewModel =
        remember(dashboardRepo, taskRepo) {
            HomeViewModel(dashboardRepo, taskRepo)
        }
    val purchasesViewModel =
        remember(productRepo, attachmentStore, mainDbKey) {
            PurchasesViewModel(productRepo, attachmentStore, mainDbKey)
        }
    val placesViewModel =
        remember(placeRepo, categoryRepo, attachmentStore, mainDbKey) {
            PlacesViewModel(placeRepo, categoryRepo, attachmentStore, mainDbKey)
        }
    val tasksViewModel =
        remember(taskRepo) {
            TasksViewModel(taskRepo)
        }
    val notesViewModel =
        remember(noteRepo) {
            NotesViewModel(noteRepo)
        }

    // Handle Android system Back key
    BackHandler(enabled = drawerState.isOpen || selectedCategory != "Главная") {
        if (drawerState.isOpen) {
            scope.launch { drawerState.close() }
        } else {
            if (selectedCategory == "Сейф" || selectedCategory == "Резервные копии") sessionManager.lockVault()
            selectedCategory = "Главная"
        }
    }

    val primaryDrawerItems =
        listOf(
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

    val secondaryDrawerItems =
        listOf(
            DrawerItem("Резервные копии", "Резервные копии", "💾"),
            DrawerItem("Настройки", "Настройки", "⚙"),
        )

    // Vault and archive operations require a separate authorization. All private windows stay secure.
    if (selectedCategory == "Сейф" || selectedCategory == "Резервные копии") {
        if (currentSessionState !is SessionState.VaultUnlocked) {
            val vaultPinViewModel = remember { PinViewModel(sessionManager, "vault") }
            PinScreen(
                viewModel = vaultPinViewModel,
                onSuccess = {
                },
            )
            return
        }
    }

    androidx.compose.runtime.LaunchedEffect(drawerState.targetValue) {
        if (drawerState.targetValue == DrawerValue.Open && (selectedCategory == "Сейф" || selectedCategory == "Резервные копии")) {
            selectedCategory = "Главная"
            sessionManager.lockVault()
        }
    }
    val vaultKey = (currentSessionState as? SessionState.VaultUnlocked)?.vaultDbKey
    val sharedVaultViewModel =
        if (selectedCategory == "Сейф" && vaultKey != null) {
            remember(vaultKey) { VaultViewModel(app.container.getVaultStorageSession(vaultKey).vaultRepository) }
        } else {
            null
        }
    DisposableEffect(sharedVaultViewModel) {
        onDispose { sharedVaultViewModel?.clearSensitiveState() }
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
                    modifier =
                        Modifier
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
                    modifier =
                        Modifier
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
                                }
                                selectedCategory = item.id
                                if (item.id in listOf("Магазины", "Парки", "Кафе", "Рестораны")) {
                                    placesViewModel.selectCategoryByName(item.id)
                                }
                                scope.launch { drawerState.close() }
                            },
                            colors =
                                NavigationDrawerItemDefaults.colors(
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
                                if (selectedCategory == "Сейф" || selectedCategory == "Резервные копии") {
                                    selectedCategory = "Главная"
                                    sessionManager.lockVault()
                                }
                                selectedCategory = item.id
                                scope.launch { drawerState.close() }
                            },
                            colors =
                                NavigationDrawerItemDefaults.colors(
                                    selectedContainerColor = PaperForeground,
                                    selectedTextColor = Color.White,
                                    unselectedContainerColor = PaperBackground,
                                    unselectedTextColor = PaperForeground,
                                ),
                            shape = RoundedCornerShape(10.dp),
                            modifier =
                                Modifier
                                    .padding(horizontal = 12.dp, vertical = 2.dp)
                                    .testTag("drawer_item_${item.id}"),
                        )
                    }
                }

                // Drawer Foot
                Column(
                    modifier =
                        Modifier
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
                            modifier = Modifier.semantics { contentDescription = "Открыть меню" },
                            onClick = {
                                if (selectedCategory == "Сейф" || selectedCategory == "Резервные копии") {
                                    sessionManager.lockVault()
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
                    actions = {},
                    colors =
                        TopAppBarDefaults.topAppBarColors(
                            containerColor = PaperBackground,
                            titleContentColor = PaperForeground,
                            navigationIconContentColor = PaperForeground,
                        ),
                )
            },
            bottomBar = {
                if (selectedCategory !in listOf("Настройки", "Резервные копии")) {
                    // Persistent bottom action bar in Paper design (NO TABS!)
                    Surface(
                        color = PaperBackground,
                        border = BorderStroke(1.dp, PaperOutline),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Box(
                            modifier =
                                Modifier
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
                                        "Сейф" -> {
                                            val vaultKey = (currentSessionState as? SessionState.VaultUnlocked)?.vaultDbKey
                                            if (vaultKey != null) {
                                                val vaultSession = app.container.getVaultStorageSession(vaultKey)
                                                val vaultVm = sharedVaultViewModel!!
                                                vaultVm.openAddEntry()
                                            }
                                        }
                                        else -> showQuickAddDialog = true
                                    }
                                },
                                shape = RoundedCornerShape(13.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = PaperForeground),
                                modifier =
                                    Modifier
                                        .fillMaxWidth()
                                        .heightIn(min = 48.dp),
                            ) {
                                Text(
                                    text =
                                        when (selectedCategory) {
                                            "Сейф" -> "Добавить пароль"
                                            "Покупки" -> "Добавить покупку"
                                            "Задачи" -> "Добавить дело"
                                            "Заметки" -> "Добавить заметку"
                                            "Напоминания" -> "Добавить напоминание"
                                            else -> "Добавить запись"
                                        },
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                )
                            }
                        }
                    }
                }
            },
            containerColor = PaperBackground,
        ) { innerPadding ->
            Box(
                modifier =
                    Modifier
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

                    "Резервные копии" -> {
                        val vaultKey =
                            (currentSessionState as? SessionState.VaultUnlocked)?.vaultDbKey
                                ?: error("Backup requires vault authorization")
                        val backupViewModel =
                            remember(mainDbKey, vaultKey) {
                                BackupViewModel(
                                    context = app,
                                    mainDbKey = mainDbKey,
                                    vaultDbKey = vaultKey,
                                    beforeStorageOperation = {
                                        app.container.closeVaultStorageSession()
                                        app.container.closeStorageSession()
                                    },
                                    onRestoreCompleted = {
                                        sessionManager.lockAll()
                                    },
                                )
                            }
                        BackupScreen(
                            viewModel = backupViewModel,
                            onBack = {
                                sessionManager.lockVault()
                                selectedCategory = "Главная"
                            },
                        )
                    }

                    "Сейф" -> {
                        val vaultKey = (currentSessionState as? SessionState.VaultUnlocked)?.vaultDbKey
                        if (vaultKey != null) {
                            val vaultSession = remember(vaultKey) { app.container.getVaultStorageSession(vaultKey) }
                            val vaultViewModel = sharedVaultViewModel!!
                            VaultScreen(
                                viewModel = vaultViewModel,
                                onLockVault = {
                                    sessionManager.lockVault()
                                    selectedCategory = "Главная"
                                },
                            )
                        } else {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(24.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                FocusEmptyState(
                                    title = "Сейф заблокирован",
                                    description = "Для доступа к паролям требуется отдельная авторизация.",
                                    actionText = "Разблокировать",
                                    onActionClick = {
                                        // Trigger vault pin dialog
                                        selectedCategory = "Сейф"
                                    },
                                )
                            }
                        }
                    }

                    else -> {
                        // Empty states for categories in progress
                        Box(
                            modifier =
                                Modifier
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

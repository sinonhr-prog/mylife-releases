package local.mylife

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import local.mylife.app.MyLifeApp
import local.mylife.app.feature.auth.PinScreen
import local.mylife.app.feature.auth.PinViewModel
import local.mylife.app.feature.settings.updater.UpdateScreen
import local.mylife.app.feature.settings.updater.UpdateViewModel
import local.mylife.core.designsystem.theme.MyLifeTheme
import local.mylife.core.designsystem.theme.PaperBackground
import local.mylife.core.designsystem.theme.PaperForeground
import local.mylife.core.designsystem.theme.PaperOnSurfaceVariant
import local.mylife.core.designsystem.theme.PaperOutline
import local.mylife.core.designsystem.theme.PaperSurface
import local.mylife.core.security.SessionManager
import local.mylife.core.security.SessionState

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
                        MainDashboardScreen(
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
@Suppress("FunctionName")
fun MainDashboardScreen(
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

    // Screen: Настройки -> Обновления
    if (selectedCategory == "Настройки" && updateViewModel != null) {
        UpdateScreen(
            viewModel = updateViewModel,
            onBack = { selectedCategory = "Главная" },
        )
        return
    }

    // Screen: Сейф (requires VaultUnlocked)
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
        // Leaving Vault or in another screen -> clear FLAG_SECURE
        onDisableWindowSecurity()
    }

    val categories = listOf(
        "Главная",
        "Покупки",
        "Места",
        "Заметки",
        "Задачи",
        "Напоминания",
        "Сейф",
        "Настройки",
    )

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = PaperBackground,
                drawerContentColor = PaperForeground,
            ) {
                Text(
                    text = "Моя жизнь",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaperForeground,
                    modifier = Modifier.padding(16.dp),
                )
                HorizontalDivider(color = PaperOutline)
                Spacer(modifier = Modifier.height(8.dp))
                categories.forEach { category ->
                    NavigationDrawerItem(
                        label = { Text(category, color = PaperForeground) },
                        selected = selectedCategory == category,
                        onClick = {
                            if (selectedCategory == "Сейф" && category != "Сейф") {
                                // Rule: opening menu / leaving vault closes vault session immediately
                                sessionManager.lockVault()
                                currentSessionState = sessionManager.getSessionState()
                                onSessionChanged(currentSessionState)
                            }
                            selectedCategory = category
                            scope.launch { drawerState.close() }
                        },
                        colors = NavigationDrawerItemDefaults.colors(
                            unselectedContainerColor = PaperBackground,
                            selectedContainerColor = PaperSurface,
                        ),
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp),
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
                            text = selectedCategory,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaperForeground,
                        )
                    },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                // Rule: opening menu from vault closes vault session
                                if (selectedCategory == "Сейф") {
                                    sessionManager.lockVault()
                                    currentSessionState = sessionManager.getSessionState()
                                    onSessionChanged(currentSessionState)
                                }
                                scope.launch { drawerState.open() }
                            }
                        ) {
                            Text(
                                text = "☰",
                                fontSize = 24.sp,
                                color = PaperForeground,
                            )
                        }
                    },
                    colors = TopAppBarDefaults.topAppBarColors(
                        containerColor = PaperBackground,
                        titleContentColor = PaperForeground,
                        navigationIconContentColor = PaperForeground,
                    ),
                )
            },
            containerColor = PaperBackground,
        ) { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Spacer(modifier = Modifier.height(8.dp))

                if (selectedCategory == "Сейф") {
                    Text(
                        text = "СЕЙФ (ИЗОЛИРОВАННАЯ СЕССИЯ)",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PaperOnSurfaceVariant,
                        letterSpacing = 1.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PaperSurface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Сейф разблокирован",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaperForeground,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Секреты доступны до закрытия сессии или открытия меню ☰",
                                fontSize = 14.sp,
                                color = PaperOnSurfaceVariant,
                            )
                        }
                    }
                } else {
                    // Выделенная карточка: «Ближайшее дело» (Компоновка «Фокус»)
                    Text(
                        text = "БЛИЖАЙШЕЕ ДЕЛО",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PaperOnSurfaceVariant,
                        letterSpacing = 1.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PaperSurface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Все дела завершены",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = PaperForeground,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Новых запланированных дел нет",
                                fontSize = 14.sp,
                                color = PaperOnSurfaceVariant,
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Сводка дашборда
                    Text(
                        text = "ОБЗОР",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = PaperOnSurfaceVariant,
                        letterSpacing = 1.sp,
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PaperSurface),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text(
                                text = "Задачи: 0 активных",
                                fontSize = 14.sp,
                                color = PaperForeground,
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                text = "Напоминания: нет сигналов",
                                fontSize = 14.sp,
                                color = PaperForeground,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}

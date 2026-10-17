package local.mylife.app.feature.settings.updater

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.mylife.core.designsystem.theme.PaperAccent
import local.mylife.core.designsystem.theme.PaperBackground
import local.mylife.core.designsystem.theme.PaperForeground
import local.mylife.core.designsystem.theme.PaperOnSurfaceVariant
import local.mylife.core.designsystem.theme.PaperOutline
import local.mylife.core.designsystem.theme.PaperSurface

/**
 * Экран «Настройки → Обновления».
 * Реализован в утверждённом проектном дизайне «Бумага / Фокус» (белый / графитовый).
 */
@Composable
fun UpdateScreen(
    viewModel: UpdateViewModel,
    onBack: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    androidx.compose.runtime.DisposableEffect(lifecycleOwner, viewModel) {
        val observer =
            androidx.lifecycle.LifecycleEventObserver { _, event ->
                if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) viewModel.refreshInstallPermission()
            }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier =
            modifier
                .fillMaxSize()
                .background(PaperBackground)
                .padding(24.dp)
                .verticalScroll(scrollState),
    ) {
        // Шапка раздела
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth(),
        ) {
            OutlinedButton(
                onClick = onBack,
                shape = RoundedCornerShape(8.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, PaperOutline),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp, vertical = 6.dp),
            ) {
                Text("← Назад", color = PaperForeground, fontSize = 14.sp)
            }
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Обновления",
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = PaperForeground,
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Карточка текущей версии приложения (Бумага / Фокус)
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(PaperSurface)
                    .border(1.dp, PaperOutline, RoundedCornerShape(12.dp))
                    .padding(20.dp),
        ) {
            Column {
                Text(
                    text = "ТЕКУЩАЯ ВЕРСИЯ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PaperOnSurfaceVariant,
                    letterSpacing = 1.sp,
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = "v${viewModel.currentVersionName}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaperForeground,
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "(сборка ${viewModel.currentVersionCode})",
                        fontSize = 14.sp,
                        color = PaperOnSurfaceVariant,
                        modifier = Modifier.padding(bottom = 3.dp),
                    )
                }
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Канал выпуска: GitHub Releases (sinonhr-prog/mylife-releases)",
                    fontSize = 12.sp,
                    color = PaperOnSurfaceVariant,
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Контекстный блок в зависимости от состояния
        when (val currentState = state) {
            is UpdateState.Idle -> {
                Button(
                    onClick = { viewModel.checkForUpdates() },
                    shape = RoundedCornerShape(8.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = PaperAccent,
                            contentColor = Color.White,
                        ),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Проверить обновления", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                }
            }

            is UpdateState.Checking -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PaperSurface)
                            .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = PaperAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Проверка обновлений на GitHub...",
                            fontSize = 14.sp,
                            color = PaperForeground,
                        )
                    }
                }
            }

            is UpdateState.UpToDate -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PaperSurface)
                            .border(1.dp, PaperOutline, RoundedCornerShape(8.dp))
                            .padding(16.dp),
                ) {
                    Column {
                        Text(
                            text = "✓ Установлена актуальная версия",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PaperForeground,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Обновлений на GitHub не обнаружено.",
                            fontSize = 13.sp,
                            color = PaperOnSurfaceVariant,
                        )
                    }
                }
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedButton(
                    onClick = { viewModel.checkForUpdates() },
                    shape = RoundedCornerShape(8.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, PaperOutline),
                    modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                ) {
                    Text("Проверить снова", color = PaperForeground, fontSize = 14.sp)
                }
            }

            is UpdateState.UpdateAvailable -> {
                val info = currentState.info
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PaperForeground)
                            .padding(20.dp),
                ) {
                    Column {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "ДОСТУПНО ОБНОВЛЕНИЕ",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White.copy(alpha = 0.8f),
                                letterSpacing = 1.sp,
                            )
                            Text(
                                text = "v${info.versionName}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                            )
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (info.sizeBytes > 0) {
                            val sizeMb = String.format("%.1f", info.sizeBytes / (1024.0 * 1024.0))
                            Text(
                                text = "Размер файла: $sizeMb МБ",
                                fontSize = 13.sp,
                                color = Color.White.copy(alpha = 0.9f),
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }

                        Text(
                            text = "Что нового:",
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color.White,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = info.releaseNotes,
                            fontSize = 13.sp,
                            color = Color.White.copy(alpha = 0.85f),
                            fontFamily = FontFamily.Default,
                            lineHeight = 18.sp,
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Button(
                            onClick = { viewModel.downloadUpdate(info) },
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = Color.White,
                                    contentColor = PaperForeground,
                                ),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text("Скачать обновление", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        }
                    }
                }
            }

            is UpdateState.Downloading -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PaperSurface)
                            .border(1.dp, PaperOutline, RoundedCornerShape(12.dp))
                            .padding(20.dp),
                ) {
                    Column {
                        Text(
                            text = "Загрузка обновления...",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = PaperForeground,
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        LinearProgressIndicator(
                            progress = { currentState.progressPercent / 100f },
                            color = PaperAccent,
                            trackColor = PaperOutline,
                            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                text = "${currentState.progressPercent}%",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                color = PaperForeground,
                            )
                            val downloadedMb = String.format("%.1f", currentState.downloadedBytes / (1024.0 * 1024.0))
                            val totalMb = String.format("%.1f", currentState.totalBytes / (1024.0 * 1024.0))
                            Text(
                                text = "$downloadedMb / $totalMb МБ",
                                fontSize = 13.sp,
                                color = PaperOnSurfaceVariant,
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        OutlinedButton(
                            onClick = { viewModel.cancel() },
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, PaperOutline),
                            modifier = Modifier.fillMaxWidth(),
                        ) {
                            Text("Отмена", color = PaperForeground)
                        }
                    }
                }
            }

            is UpdateState.ReadyToInstall -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PaperSurface)
                            .border(1.dp, PaperOutline, RoundedCornerShape(12.dp))
                            .padding(20.dp),
                ) {
                    Column {
                        Text(
                            text = "✓ Файл загружен и проверен",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaperForeground,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Контрольная сумма SHA-256 и сертификат подписи разработчика подтверждены. Файл готов к установке поверх текущей версии.",
                            fontSize = 13.sp,
                            color = PaperOnSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { viewModel.installUpdate(currentState.apkFile, currentState.info) },
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = PaperAccent,
                                    contentColor = Color.White,
                                ),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text("Установить обновление", fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            is UpdateState.Installing -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(PaperSurface)
                            .padding(20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CircularProgressIndicator(
                            color = PaperAccent,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Text(
                            text = "Ожидание подтверждения установки Android...",
                            fontSize = 14.sp,
                            color = PaperForeground,
                        )
                    }
                }
            }

            is UpdateState.PermissionRequired -> {
                val helper = PackageInstallerHelper(context)
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PaperSurface)
                            .border(1.dp, PaperOutline, RoundedCornerShape(12.dp))
                            .padding(20.dp),
                ) {
                    Column {
                        Text(
                            text = "Требуется системное разрешение",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaperForeground,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Для завершения установки необходимо разрешить приложению «Моя жизнь» установку из неизвестных источников в настройках Android.",
                            fontSize = 13.sp,
                            color = PaperOnSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                context.startActivity(helper.createManageUnknownSourcesIntent())
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors =
                                ButtonDefaults.buttonColors(
                                    containerColor = PaperAccent,
                                    contentColor = Color.White,
                                ),
                            modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                        ) {
                            Text("Открыть настройки Android", fontSize = 14.sp)
                        }
                    }
                }
            }

            is UpdateState.Error -> {
                Box(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(PaperSurface)
                            .border(1.dp, PaperOutline, RoundedCornerShape(12.dp))
                            .padding(20.dp),
                ) {
                    Column {
                        Text(
                            text = "Не удалось выполнить операцию",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = PaperForeground,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = currentState.message,
                            fontSize = 13.sp,
                            color = PaperOnSurfaceVariant,
                            lineHeight = 18.sp,
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Row {
                            if (currentState.canRetry) {
                                Button(
                                    onClick = { viewModel.checkForUpdates() },
                                    shape = RoundedCornerShape(8.dp),
                                    colors =
                                        ButtonDefaults.buttonColors(
                                            containerColor = PaperAccent,
                                            contentColor = Color.White,
                                        ),
                                    modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                                ) {
                                    Text("Повторить", fontSize = 14.sp)
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                            }
                            OutlinedButton(
                                onClick = { viewModel.dismissError() },
                                shape = RoundedCornerShape(8.dp),
                                border = androidx.compose.foundation.BorderStroke(1.dp, PaperOutline),
                                modifier = Modifier.weight(1f).heightIn(min = 48.dp),
                            ) {
                                Text("Закрыть", color = PaperForeground, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Блок информации о безопасности и локальности данных
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(PaperSurface)
                    .padding(16.dp),
        ) {
            Column {
                Text(
                    text = "БЕЗОПАСНОСТЬ И КОНФИДЕНЦИАЛЬНОСТЬ",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = PaperOnSurfaceVariant,
                    letterSpacing = 0.8.sp,
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Все ваши личные записи, покупки, заметки и сейф хранятся исключительно на этом устройстве в зашифрованном виде. Обновление не передаёт личные данные на сервер и не сбрасывает базу данных.",
                    fontSize = 12.sp,
                    color = PaperOnSurfaceVariant,
                    lineHeight = 16.sp,
                )
            }
        }
    }
}

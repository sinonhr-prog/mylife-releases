package local.mylife.app.feature.settings.backup

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.mylife.core.designsystem.theme.PaperBackground
import local.mylife.core.designsystem.theme.PaperForeground
import local.mylife.core.designsystem.theme.PaperOnSurfaceVariant
import local.mylife.core.designsystem.theme.PaperOutline
import local.mylife.core.designsystem.theme.PaperSurface

@Composable
fun BackupScreen(
    viewModel: BackupViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.state.collectAsState()
    var exportPassphrase by remember { mutableStateOf("") }
    var restorePassphrase by remember { mutableStateOf("") }
    var selectedRestoreUri by remember { mutableStateOf<Uri?>(null) }

    var showConfirmRestoreDialog by remember { mutableStateOf(false) }
    var newPinForRestore by remember { mutableStateOf("123456") }
    var newVaultPinForRestore by remember { mutableStateOf("654321") }

    // SAF Launchers
    val createDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null) {
            viewModel.createBackup(exportPassphrase, uri)
        }
    }

    val openDocLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            selectedRestoreUri = uri
            viewModel.inspectArchive(uri, restorePassphrase)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(PaperBackground),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(20.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "РЕЗЕРВНОЕ КОПИРОВАНИЕ",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = PaperOnSurfaceVariant,
                    letterSpacing = 1.sp,
                )
                TextButton(onClick = onBack) {
                    Text("Назад", color = PaperForeground)
                }
            }

            // Info Card
            Card(
                colors = CardDefaults.cardColors(containerColor = PaperSurface),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, PaperOutline),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "Автономность и переносимость",
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold,
                        color = PaperForeground,
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "Приложение не отправляет данные в облако. Ваши данные (покупки, места, задачи, заметки, напоминания и сейф) экспортируются только в зашифрованный файл .lifevault с вашей ключевой фразой.",
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        color = PaperOnSurfaceVariant,
                    )
                }
            }

            // State Messages
            when (val s = state) {
                is BackupUiState.InProgress -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(vertical = 8.dp),
                    ) {
                        CircularProgressIndicator(color = PaperForeground)
                        Text("Выполняется операция с архивом...", fontSize = 13.sp, color = PaperForeground)
                    }
                }
                is BackupUiState.Error -> {
                    Text("⚠ ${s.message}", color = Color(0xFFC0392B), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
                is BackupUiState.ExportSuccess -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PaperSurface),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, Color(0xFF27AE60)),
                        modifier = Modifier.fillMaxWidth().testTag("export_success_card"),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("✓ Архив успешно сохранён", color = Color(0xFF27AE60), fontWeight = FontWeight.Bold)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Дата экспорта: ${s.dateText}", fontSize = 12.sp, color = PaperForeground)
                            Text("Путь: ${s.filePath}", fontSize = 11.sp, color = PaperOnSurfaceVariant)
                        }
                    }
                }
                is BackupUiState.InspectSuccess -> {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = PaperSurface),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, PaperForeground),
                        modifier = Modifier.fillMaxWidth().testTag("inspect_success_card"),
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("✓ Архив проверен и готов к восстановлению", fontWeight = FontWeight.Bold, color = PaperForeground)
                            Spacer(modifier = Modifier.height(4.dp))
                            Text("Версия формата: ${s.manifest.formatVersion}", fontSize = 12.sp, color = PaperOnSurfaceVariant)
                            Text("Файлов в архиве: ${s.manifest.files.size}", fontSize = 12.sp, color = PaperForeground)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = { showConfirmRestoreDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.testTag("proceed_restore_button"),
                            ) {
                                Text("Восстановить с заменой данных", color = Color.White)
                            }
                        }
                    }
                }
                is BackupUiState.RestoreSuccess -> {
                    Text("✓ Данные успешно восстановлены из архива!", color = Color(0xFF27AE60), fontWeight = FontWeight.Bold)
                }
                else -> {}
            }

            // EXPORT SECTION
            Text(
                text = "ЭКСПОРТ (СОХРАНЕНИЕ АРХИВА)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PaperOnSurfaceVariant,
                letterSpacing = 0.5.sp,
            )

            OutlinedTextField(
                value = exportPassphrase,
                onValueChange = { exportPassphrase = it },
                label = { Text("Ключевая фраза архива (мин. 8 знаков)") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("export_passphrase_input"),
                singleLine = true,
            )

            Button(
                onClick = {
                    val name = "mylife_backup.lifevault"
                    createDocLauncher.launch(name)
                },
                enabled = exportPassphrase.length >= 8 && state !is BackupUiState.InProgress,
                colors = ButtonDefaults.buttonColors(containerColor = PaperForeground),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("start_export_button"),
            ) {
                Text("Сохранить в файл (USB / Диск)", color = Color.White)
            }

            Spacer(modifier = Modifier.height(8.dp))
            HorizontalDivider(color = PaperOutline)
            Spacer(modifier = Modifier.height(8.dp))

            // RESTORE SECTION
            Text(
                text = "ИМПОРТ (ВОССТАНОВЛЕНИЕ ДАННЫХ)",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = PaperOnSurfaceVariant,
                letterSpacing = 0.5.sp,
            )

            OutlinedTextField(
                value = restorePassphrase,
                onValueChange = { restorePassphrase = it },
                label = { Text("Ключевая фраза архива для открытия") },
                visualTransformation = PasswordVisualTransformation(),
                modifier = Modifier.fillMaxWidth().testTag("restore_passphrase_input"),
                singleLine = true,
            )

            Button(
                onClick = {
                    openDocLauncher.launch(arrayOf("*/*"))
                },
                enabled = restorePassphrase.length >= 8 && state !is BackupUiState.InProgress,
                colors = ButtonDefaults.buttonColors(containerColor = PaperForeground),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.fillMaxWidth().testTag("start_restore_button"),
            ) {
                Text("Выбрать файл .lifevault для проверки", color = Color.White)
            }
        }

        // CONFIRM RESTORE DIALOG
        if (showConfirmRestoreDialog && selectedRestoreUri != null) {
            AlertDialog(
                onDismissRequest = { showConfirmRestoreDialog = false },
                title = { Text("ВНИМАНИЕ: Замена данных", color = Color(0xFFC0392B), fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Восстановление полностью заменит текущие базы данных и вложения на телефоне данными из архива.",
                            fontSize = 13.sp,
                            color = PaperForeground,
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Установите новый 6-значный PIN приложения после восстановления:",
                            fontSize = 12.sp,
                            color = PaperOnSurfaceVariant,
                        )
                        OutlinedTextField(
                            value = newPinForRestore,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newPinForRestore = it },
                            label = { Text("PIN приложения (6 цифр)") },
                            singleLine = true,
                        )
                        OutlinedTextField(
                            value = newVaultPinForRestore,
                            onValueChange = { if (it.length <= 6 && it.all { c -> c.isDigit() }) newVaultPinForRestore = it },
                            label = { Text("PIN сейфа (6 цифр)") },
                            singleLine = true,
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            showConfirmRestoreDialog = false
                            viewModel.restoreArchive(
                                archiveUri = selectedRestoreUri!!,
                                passphrase = restorePassphrase,
                                newPin = newPinForRestore,
                                newVaultPin = newVaultPinForRestore,
                            )
                        },
                        enabled = newPinForRestore.length == 6 && newVaultPinForRestore.length == 6,
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFC0392B)),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.testTag("confirm_restore_final_button"),
                    ) {
                        Text("Подтверждаю замену", color = Color.White)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfirmRestoreDialog = false }) {
                        Text("Отмена", color = PaperForeground)
                    }
                },
                containerColor = PaperBackground,
            )
        }
    }
}

package local.mylife.app.feature.auth

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import local.mylife.core.designsystem.theme.PaperBackground
import local.mylife.core.designsystem.theme.PaperForeground
import local.mylife.core.designsystem.theme.PaperOnSurfaceVariant
import local.mylife.core.designsystem.theme.PaperOutline
import local.mylife.core.designsystem.theme.PaperSurface

@Composable
fun PinScreen(
    viewModel: PinViewModel,
    onSuccess: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val state by viewModel.uiState.collectAsState()

    if (state.isUnlocked) {
        onSuccess()
        return
    }

    val title = when {
        state.isSetupRequired && !state.isConfirmStep -> "Придумайте PIN-код"
        state.isSetupRequired && state.isConfirmStep -> "Повторите PIN-код"
        state.targetScope == "vault" -> "Вход в Сейф"
        else -> "Вход в приложение"
    }

    val subtitle = when {
        state.isSetupRequired && !state.isConfirmStep -> "PIN состоит строго из 6 цифр для защиты ваших данных"
        state.isSetupRequired && state.isConfirmStep -> "Введите тот же PIN-код для подтверждения"
        else -> "Введите 6-значный PIN-код"
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(PaperBackground)
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        // Header Section
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(top = 48.dp),
        ) {
            Text(
                text = title,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = PaperForeground,
                textAlign = TextAlign.Center,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = subtitle,
                fontSize = 14.sp,
                color = PaperOnSurfaceVariant,
                textAlign = TextAlign.Center,
            )

            Spacer(modifier = Modifier.height(32.dp))

            // 6-dot indicator
            Row(
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for (i in 0 until 6) {
                    val isFilled = i < state.enteredDigits.length
                    Box(
                        modifier = Modifier
                            .size(16.dp)
                            .clip(CircleShape)
                            .background(if (isFilled) PaperForeground else PaperBackground)
                            .border(1.5.dp, if (isFilled) PaperForeground else PaperOutline, CircleShape)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (state.errorMessage != null) {
                Text(
                    text = state.errorMessage ?: "",
                    fontSize = 13.sp,
                    color = PaperForeground,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )
            }
        }

        // Keypad Section
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            val rows = listOf(
                listOf('1', '2', '3'),
                listOf('4', '5', '6'),
                listOf('7', '8', '9'),
                listOf(null, '0', '⌫'),
            )

            for (row in rows) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                ) {
                    for (item in row) {
                        if (item == null) {
                            Box(modifier = Modifier.size(72.dp))
                        } else if (item == '⌫') {
                            KeypadButton(
                                text = "⌫",
                                isAction = true,
                                onClick = { viewModel.onDeleteClick() },
                            )
                        } else {
                            KeypadButton(
                                text = item.toString(),
                                isAction = false,
                                onClick = { viewModel.onDigitClick(item) },
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(
    text: String,
    isAction: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(if (isAction) PaperBackground else PaperSurface)
            .border(1.dp, PaperOutline, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            fontSize = if (isAction) 20.sp else 24.sp,
            fontWeight = FontWeight.Medium,
            color = PaperForeground,
        )
    }
}

package local.mylife.core.designsystem.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Design Tokens: Тема «Бумага» (Paper Focus)
val PaperBackground = Color(0xFFFFFFFF)
val PaperForeground = Color(0xFF202020)
val PaperAccent = Color(0xFF202020)
val PaperSurface = Color(0xFFF9F9F9)
val PaperOutline = Color(0xFFE0E0E0)
val PaperOnSurfaceVariant = Color(0xFF666666)

private val PaperColorScheme =
    lightColorScheme(
        primary = PaperAccent,
        onPrimary = Color.White,
        background = PaperBackground,
        onBackground = PaperForeground,
        surface = PaperSurface,
        onSurface = PaperForeground,
        surfaceVariant = PaperSurface,
        onSurfaceVariant = PaperOnSurfaceVariant,
        outline = PaperOutline,
    )

@Composable
@Suppress("FunctionName")
fun MyLifeTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PaperColorScheme,
        content = content,
    )
}

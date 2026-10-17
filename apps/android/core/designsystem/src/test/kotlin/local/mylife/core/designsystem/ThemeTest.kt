package local.mylife.core.designsystem

import androidx.compose.ui.graphics.toArgb
import local.mylife.core.designsystem.theme.PaperBackground
import local.mylife.core.designsystem.theme.PaperForeground
import org.junit.Assert.assertEquals
import org.junit.Test

class ThemeTest {
    @Test
    fun testPaperTokensValues() {
        assertEquals(0xFFFFFFFF.toInt(), PaperBackground.toArgb())
        assertEquals(0xFF202020.toInt(), PaperForeground.toArgb())
    }
}

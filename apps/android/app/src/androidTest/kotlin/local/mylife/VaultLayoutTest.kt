package local.mylife

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import local.mylife.app.feature.vault.VaultScreen
import local.mylife.app.feature.vault.VaultViewModel
import local.mylife.core.designsystem.theme.MyLifeTheme
import local.mylife.core.model.VaultEntry
import local.mylife.core.model.VaultRepository
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

/** Synthetic screen only: never opens the device's real application storage. */
class VaultLayoutTest {
    @get:Rule val compose = createComposeRule()
    private val repository =
        object : VaultRepository {
            override suspend fun getVaultEntries() = emptyList<VaultEntry>()

            override suspend fun getVaultEntryById(id: String): VaultEntry? = null

            override suspend fun saveVaultEntry(entry: VaultEntry) {}

            override suspend fun deleteVaultEntry(id: String) {}
        }

    private fun verify(fontScale: Float) {
        var locked = false
        val vm = VaultViewModel(repository, CoroutineScope(Dispatchers.Main))
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, fontScale)) {
                MyLifeTheme {
                    Box(Modifier.width(320.dp)) {
                        VaultScreen(vm, onLockVault = { locked = true })
                    }
                }
            }
        }
        compose.onNodeWithTag("lock_vault_button").assertIsDisplayed().assertHeightIsAtLeast(48.dp)
        compose.onNodeWithText("Закрыть сейф").assertIsDisplayed()
        val screenshot = compose.onRoot().captureToImage().asAndroidBitmap()
        val context = androidx.test.core.app.ApplicationProvider.getApplicationContext<android.content.Context>()
        val screenshotFile = java.io.File(context.getExternalFilesDir(null), "vault-" + fontScale + ".png")
        screenshotFile.outputStream().use {
            screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it)
        }
        androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().uiAutomation
            .executeShellCommand("cp " + screenshotFile.absolutePath + " /data/local/tmp/mylife-vault-" + fontScale + ".png")
            .use { descriptor -> java.io.FileInputStream(descriptor.fileDescriptor).use { it.readBytes() } }
        compose.onNodeWithTag("lock_vault_button").performClick()
        compose.runOnIdle { assertTrue(locked) }
    }

    @Test fun labelledLockAt320dp() = verify(1f)

    @Test fun labelledLockAt320dpAndLargeFont() = verify(2f)
}

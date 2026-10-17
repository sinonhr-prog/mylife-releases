package local.mylife

import android.content.Context
import android.content.ContextWrapper
import androidx.test.core.app.ApplicationProvider
import local.mylife.app.feature.settings.updater.ApkVerifier
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class UpdaterPackageBoundaryTest {
    @Test fun signedApkForNeighborPackageIsRejectedBeforeVersionOrInstall() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val neighbor =
            object : ContextWrapper(context) {
                override fun getPackageName() = "local.mylife.neighbor"
            }
        // Actual synthetic QA APK parsed by Android, without downloading a fixture.
        val result = ApkVerifier(neighbor).verify(File(context.applicationInfo.sourceDir))
        assertFalse(result.isValid)
        assertEquals(context.packageName, result.archivePackageName)
        assertTrue(result.errorMessage.orEmpty().startsWith("Несовпадение пакета:"))
    }
}

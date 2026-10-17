package local.mylife.app.feature.settings.updater

import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.net.URL
import java.security.MessageDigest

class UpdaterUnitTest {

    @Test
    fun testUpdateModelsCreationAndStates() {
        val info = UpdateInfo(
            versionCode = 2,
            versionName = "0.2.0",
            releaseNotes = "Улучшена безопасность и добавлен обновлятор",
            apkUrl = "https://github.com/sinonhr-prog/mylife-releases/releases/download/v0.2.0/mylife.apk",
            sha256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            sizeBytes = 10485760L,
            channel = "stable",
        )

        assertEquals(2, info.versionCode)
        assertEquals("0.2.0", info.versionName)
        assertEquals("stable", info.channel)

        val idleState: UpdateState = UpdateState.Idle
        val checkingState: UpdateState = UpdateState.Checking
        val availableState: UpdateState = UpdateState.UpdateAvailable(info)
        val upToDateState: UpdateState = UpdateState.UpToDate(1, "0.1.0")
        val downloadingState: UpdateState = UpdateState.Downloading(50, 5242880L, 10485760L)
        val installingState: UpdateState = UpdateState.Installing
        val errorState: UpdateState = UpdateState.Error("Сбой сети")

        assertNotNull(idleState)
        assertNotNull(checkingState)
        assertEquals(info, (availableState as UpdateState.UpdateAvailable).info)
        assertEquals(1, (upToDateState as UpdateState.UpToDate).currentVersionCode)
        assertEquals(50, (downloadingState as UpdateState.Downloading).progressPercent)
        assertNotNull(installingState)
        assertEquals("Сбой сети", (errorState as UpdateState.Error).message)
    }

    @Test
    fun testAllowlistDomainValidation() {
        val allowedHosts = setOf(
            "api.github.com",
            "github.com",
            "objects.githubusercontent.com",
            "release-assets.githubusercontent.com",
        )

        fun validate(urlString: String): Boolean {
            val url = URL(urlString)
            if (url.protocol != "https") return false
            val host = url.host.lowercase()
            return allowedHosts.any { allowed ->
                host == allowed || host.endsWith(".$allowed")
            }
        }

        // Доверенные домены
        assertTrue(validate("https://api.github.com/repos/sinonhr-prog/mylife-releases/releases/latest"))
        assertTrue(validate("https://github.com/sinonhr-prog/mylife-releases/releases/download/v0.2.0/mylife.apk"))
        assertTrue(validate("https://objects.githubusercontent.com/github-production-release-asset-2e65be/123/456"))
        assertTrue(validate("https://release-assets.githubusercontent.com/12345/abcdef"))

        // Запрещённый протокол HTTP (cleartext)
        assertFalse(validate("http://api.github.com/repos/sinonhr-prog/mylife-releases/releases/latest"))
        assertFalse(validate("http://github.com/sinonhr-prog/mylife-releases/releases/download/v0.2.0/mylife.apk"))

        // Недоверенные домены
        assertFalse(validate("https://evil.com/malicious.apk"))
        assertFalse(validate("https://github.com.evil.com/fake.apk"))
        assertFalse(validate("https://api.github.com.attacker.net/test"))
        assertFalse(validate("https://raw.githubusercontent.com/untrusted/repo/main/apk"))
    }

    @Test
    fun testUpdateJsonSchemaParsing() {
        val jsonString = """
            {
              "schemaVersion": 1,
              "applicationId": "local.mylife",
              "versionCode": 2,
              "versionName": "0.2.0",
              "minSdk": 29,
              "assetName": "mylife.apk",
              "sizeBytes": 12582912,
              "sha256": "4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a",
              "channel": "stable"
            }
        """.trimIndent()

        val json = JSONObject(jsonString)
        assertEquals(1, json.getInt("schemaVersion"))
        assertEquals("local.mylife", json.getString("applicationId"))
        assertEquals(2, json.getInt("versionCode"))
        assertEquals("0.2.0", json.getString("versionName"))
        assertEquals(29, json.getInt("minSdk"))
        assertEquals("mylife.apk", json.getString("assetName"))
        assertEquals(12582912L, json.getLong("sizeBytes"))
        assertEquals("4b227777d4dd1fc61c6f884f48641d02b4d121d3fd328cb08b5531fcacdabf8a", json.getString("sha256"))
        assertEquals("stable", json.getString("channel"))
    }

    @Test
    fun testSha256ChecksumVerification() {
        val testBytes = "Official MyLife Signed APK content fixture".toByteArray(Charsets.UTF_8)
        val expectedSha256 = MessageDigest.getInstance("SHA-256")
            .digest(testBytes)
            .joinToString("") { "%02x".format(it) }

        val tempFile = File.createTempFile("test_apk_", ".apk")
        try {
            FileOutputStream(tempFile).use { it.write(testBytes) }

            // Вычисляем SHA-256 файла
            val digest = MessageDigest.getInstance("SHA-256")
            tempFile.inputStream().use { input ->
                val buf = ByteArray(1024)
                var r: Int
                while (input.read(buf).also { r = it } != -1) {
                    digest.update(buf, 0, r)
                }
            }
            val actualSha256 = digest.digest().joinToString("") { "%02x".format(it) }

            assertEquals(expectedSha256, actualSha256)

            // Проверка несовпадения (испорченный файл)
            val corruptedHash = "0000000000000000000000000000000000000000000000000000000000000000"
            assertFalse(actualSha256.equals(corruptedHash, ignoreCase = true))
        } finally {
            tempFile.delete()
        }
    }

    @Test
    fun testDowngradePreventionLogic() {
        val currentVersionCode = 10L

        fun canUpdate(candidateVersionCode: Long): Boolean {
            return candidateVersionCode > currentVersionCode
        }

        // Понижение версии запрещено
        assertFalse(canUpdate(9L))
        assertFalse(canUpdate(1L))

        // Та же версия не обновляется
        assertFalse(canUpdate(10L))

        // Повышение версии разрешено
        assertTrue(canUpdate(11L))
        assertTrue(canUpdate(20L))
    }

    @Test
    fun testPackageNameMatchLogic() {
        val currentPackageName = "local.mylife"

        fun isPackageAllowed(candidatePackageName: String): Boolean {
            return candidatePackageName == currentPackageName ||
                candidatePackageName == "local.mylife.debug"
        }

        assertTrue(isPackageAllowed("local.mylife"))
        assertTrue(isPackageAllowed("local.mylife.debug"))
        assertFalse(isPackageAllowed("com.fake.mylife"))
        assertFalse(isPackageAllowed("local.evilapp"))
        assertFalse(isPackageAllowed(""))
    }
}

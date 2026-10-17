package local.mylife.app.feature.settings.updater

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest
import javax.net.ssl.HttpsURLConnection

/**
 * HTTP-клиент для взаимодействия с публичным каналом релизов GitHub.
 *
 * Безопасность:
 * - Только HTTPS.
 * - Строгий allowlist доверенных доменов (api.github.com, github.com, objects.githubusercontent.com).
 * - Запрет cleartext HTTP и сторонних редиректов.
 * - Проверка хэша SHA-256 скачанного APK в потоке.
 */
class GitHubReleaseClient(
    private val owner: String = "sinonhr-prog",
    private val repo: String = "mylife-releases",
    private val connectTimeoutMs: Int = 15000,
    private val readTimeoutMs: Int = 30000,
) {

    private val allowedHosts = setOf(
        "api.github.com",
        "github.com",
        "objects.githubusercontent.com",
    )

    private fun validateHost(urlString: String): URL {
        val url = URL(urlString)
        if (url.protocol != "https") {
            throw SecurityException("Запрещён незашифрованный протокол: ${url.protocol}")
        }
        val host = url.host.lowercase()
        val isAllowed = allowedHosts.any { allowed ->
            host == allowed || host.endsWith(".$allowed")
        }
        if (!isAllowed) {
            throw SecurityException("Хост не входит в доверенный allowlist: $host")
        }
        return url
    }

    /**
     * Запрос последнего релиза из GitHub Releases API.
     */
    suspend fun getLatestRelease(): Result<UpdateInfo?> = withContext(Dispatchers.IO) {
        runCatching {
            val apiUrl = "https://api.github.com/repos/$owner/$repo/releases/latest"
            val url = validateHost(apiUrl)

            val connection = (url.openConnection() as HttpsURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                setRequestProperty("User-Agent", "MyLife-Android-Updater")
                setRequestProperty("Accept", "application/vnd.github.v3+json")
            }

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_NOT_FOUND) {
                // Релизов в репозитории пока нет
                return@runCatching null
            }
            if (responseCode == 403 || responseCode == 429) {
                throw IllegalStateException("Превышен лимит запросов GitHub API (HTTP $responseCode). Попробуйте позже.")
            }
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Ошибка запроса GitHub API: HTTP $responseCode")
            }

            val responseText = connection.inputStream.bufferedReader().use { it.readText() }
            val releaseJson = JSONObject(responseText)

            val isDraft = releaseJson.optBoolean("draft", false)
            val isPrerelease = releaseJson.optBoolean("prerelease", false)
            if (isDraft) return@runCatching null

            val tagName = releaseJson.optString("tag_name", "")
            val body = releaseJson.optString("body", "Новая версия приложения")
            val publishedAt = releaseJson.optString("published_at", "")
            val assets = releaseJson.optJSONArray("assets") ?: JSONArray()

            // Ищем update.json среди assets для получения точной спецификации релиза
            var updateJsonUrl: String? = null
            var apkDownloadUrl: String? = null
            var apkAssetSize: Long = 0L

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.optString("name", "")
                val downloadUrl = asset.optString("browser_download_url", "")
                val size = asset.optLong("size", 0L)

                if (name == "update.json") {
                    updateJsonUrl = downloadUrl
                } else if (name.endsWith(".apk")) {
                    apkDownloadUrl = downloadUrl
                    apkAssetSize = size
                }
            }

            if (updateJsonUrl != null) {
                // Скачиваем и парсим update.json (схема v1)
                val updateJsonContent = downloadText(updateJsonUrl)
                val updateMeta = JSONObject(updateJsonContent)
                val schemaVersion = updateMeta.optInt("schemaVersion", 1)
                val versionCode = updateMeta.getInt("versionCode")
                val versionName = updateMeta.optString("versionName", tagName.removePrefix("v"))
                val assetName = updateMeta.optString("assetName", "mylife.apk")
                val sha256 = updateMeta.getString("sha256")
                val sizeBytes = updateMeta.optLong("sizeBytes", apkAssetSize)
                val channel = updateMeta.optString("channel", if (isPrerelease) "preview" else "stable")

                // Находим URL соответствующего APK asset
                var matchedApkUrl = apkDownloadUrl
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    if (asset.optString("name") == assetName) {
                        matchedApkUrl = asset.optString("browser_download_url")
                        break
                    }
                }

                if (matchedApkUrl == null) {
                    throw IllegalStateException("В релизе $tagName отсутствует файл APK ($assetName)")
                }

                UpdateInfo(
                    versionCode = versionCode,
                    versionName = versionName,
                    releaseNotes = body,
                    apkUrl = matchedApkUrl,
                    sha256 = sha256,
                    sizeBytes = sizeBytes,
                    channel = channel,
                    publishedAt = publishedAt,
                )
            } else if (apkDownloadUrl != null) {
                // Запасной путь, если update.json не прикреплён, но есть APK
                val versionCode = parseVersionCodeFromTag(tagName)
                UpdateInfo(
                    versionCode = versionCode,
                    versionName = tagName.removePrefix("v"),
                    releaseNotes = body,
                    apkUrl = apkDownloadUrl,
                    sha256 = "", // Требует отдельного вычисления
                    sizeBytes = apkAssetSize,
                    channel = if (isPrerelease) "preview" else "stable",
                    publishedAt = publishedAt,
                )
            } else {
                null
            }
        }
    }

    private fun parseVersionCodeFromTag(tag: String): Int {
        val clean = tag.removePrefix("v").trim()
        val parts = clean.split(".")
        return runCatching {
            when (parts.size) {
                1 -> parts[0].toInt()
                2 -> parts[0].toInt() * 1000 + parts[1].toInt()
                3 -> parts[0].toInt() * 10000 + parts[1].toInt() * 100 + parts[2].toInt()
                else -> 1
            }
        }.getOrDefault(1)
    }

    private fun downloadText(urlString: String): String {
        val url = validateHost(urlString)
        val conn = (url.openConnection() as HttpsURLConnection).apply {
            connectTimeout = connectTimeoutMs
            readTimeout = readTimeoutMs
            setRequestProperty("User-Agent", "MyLife-Android-Updater")
        }
        return conn.inputStream.bufferedReader().use { it.readText() }
    }

    /**
     * Потоковое скачивание APK с вычислением SHA-256 и сверкой контрольной суммы.
     */
    suspend fun downloadApk(
        apkUrl: String,
        targetFile: File,
        expectedSha256: String,
        expectedSize: Long,
        onProgress: (downloadedBytes: Long, totalBytes: Long) -> Unit,
    ): Result<File> = withContext(Dispatchers.IO) {
        runCatching {
            val validatedUrl = validateHost(apkUrl)
            val tempFile = File(targetFile.parentFile, "${targetFile.name}.downloading")
            if (tempFile.exists()) tempFile.delete()
            targetFile.parentFile?.mkdirs()

            var currentConnection = openRedirectingConnection(validatedUrl)
            val totalBytes = if (currentConnection.contentLengthLong > 0) {
                currentConnection.contentLengthLong
            } else {
                expectedSize
            }

            val digest = MessageDigest.getInstance("SHA-256")
            var downloaded = 0L

            currentConnection.inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    val buffer = ByteArray(16 * 1024)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                        downloaded += read
                        onProgress(downloaded, totalBytes)
                    }
                    output.flush()
                }
            }

            // Проверка контрольной суммы SHA-256
            val actualHash = digest.digest().joinToString("") { "%02x".format(it) }
            if (expectedSha256.isNotBlank() && !actualHash.equals(expectedSha256.trim(), ignoreCase = true)) {
                tempFile.delete()
                throw SecurityException(
                    "Нарушена целостность APK: не совпадает контрольная сумма SHA-256. " +
                        "Ожидалось: $expectedSha256, фактически: $actualHash"
                )
            }

            // Проверка размера файла
            if (expectedSize > 0 && tempFile.length() != expectedSize && totalBytes > 0 && tempFile.length() != totalBytes) {
                tempFile.delete()
                throw IllegalStateException("Размер скачанного файла (${tempFile.length()} байт) не соответствует ожидаемому ($expectedSize байт)")
            }

            if (targetFile.exists()) targetFile.delete()
            if (!tempFile.renameTo(targetFile)) {
                throw IllegalStateException("Не удалось сохранить итоговый файл обновления")
            }

            targetFile
        }
    }

    private fun openRedirectingConnection(initialUrl: URL, maxRedirects: Int = 5): HttpsURLConnection {
        var url = initialUrl
        var redirects = 0
        while (redirects < maxRedirects) {
            val conn = (url.openConnection() as HttpsURLConnection).apply {
                connectTimeout = connectTimeoutMs
                readTimeout = readTimeoutMs
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", "MyLife-Android-Updater")
            }

            val code = conn.responseCode
            if (code in listOf(HttpURLConnection.HTTP_MOVED_PERM, HttpURLConnection.HTTP_MOVED_TEMP, 307, 308)) {
                val location = conn.getHeaderField("Location")
                    ?: throw IllegalStateException("HTTP $code без заголовка Location")
                url = validateHost(location)
                redirects++
                continue
            }

            if (code != HttpURLConnection.HTTP_OK) {
                throw IllegalStateException("Ошибка загрузки файла: HTTP $code")
            }
            return conn
        }
        throw IllegalStateException("Превышено максимальное число перенаправлений ($maxRedirects)")
    }
}

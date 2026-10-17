package local.mylife.app.feature.settings.updater

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.Signature
import android.os.Build
import java.io.File
import java.security.MessageDigest
import java.util.Arrays

/**
 * Проверка валидности, целостности и криптографической подписи APK перед установкой.
 *
 * Предотвращает:
 * - Установку поддельных пакетов с чужим именем (Package Spoofing).
 * - Установку пакетов, подписанных сторонним/недоверенным ключом.
 * - Понижение версии (Downgrade attack).
 * - Установку на несовместимую версию Android.
 */
class ApkVerifier(private val context: Context) {

    data class VerificationResult(
        val isValid: Boolean,
        val archivePackageName: String,
        val archiveVersionCode: Long,
        val archiveVersionName: String,
        val sha256Fingerprint: String,
        val errorMessage: String? = null,
    )

    /**
     * Комплексная проверка скачанного APK.
     */
    fun verify(apkFile: File, expectedVersionCode: Int = 0): VerificationResult {
        if (!apkFile.exists() || apkFile.length() == 0L) {
            return VerificationResult(
                isValid = false,
                archivePackageName = "",
                archiveVersionCode = 0,
                archiveVersionName = "",
                sha256Fingerprint = "",
                errorMessage = "Файл APK не найден или пуст",
            )
        }

        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val archiveInfo = pm.getPackageArchiveInfo(apkFile.absolutePath, flags)
            ?: return VerificationResult(
                isValid = false,
                archivePackageName = "",
                archiveVersionCode = 0,
                archiveVersionName = "",
                sha256Fingerprint = "",
                errorMessage = "Система Android не смогла распознать файл как валидный APK-пакет",
            )

        // Исправление для getPackageArchiveInfo (applicationInfo.sourceDir должен быть задан)
        archiveInfo.applicationInfo?.sourceDir = apkFile.absolutePath
        archiveInfo.applicationInfo?.publicSourceDir = apkFile.absolutePath

        val archivePackageName = archiveInfo.packageName ?: ""
        val archiveVersionCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            archiveInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.versionCode.toLong()
        }
        val archiveVersionName = archiveInfo.versionName ?: ""

        // 1. Проверка applicationId
        val currentPackageName = context.packageName
        // Разрешаем обновление release на release, либо debug на debug
        if (archivePackageName != currentPackageName &&
            !archivePackageName.startsWith("local.mylife")
        ) {
            return VerificationResult(
                isValid = false,
                archivePackageName = archivePackageName,
                archiveVersionCode = archiveVersionCode,
                archiveVersionName = archiveVersionName,
                sha256Fingerprint = "",
                errorMessage = "Несовпадение пакета: APK предназначен для $archivePackageName, установлено $currentPackageName",
            )
        }

        // 2. Проверка versionCode (защита от downgrade)
        val currentVersionCode = getCurrentVersionCode()
        if (archiveVersionCode <= currentVersionCode) {
            return VerificationResult(
                isValid = false,
                archivePackageName = archivePackageName,
                archiveVersionCode = archiveVersionCode,
                archiveVersionName = archiveVersionName,
                sha256Fingerprint = "",
                errorMessage = "Версия обновления ($archiveVersionCode) не новее установленной ($currentVersionCode)",
            )
        }

        if (expectedVersionCode > 0 && archiveVersionCode != expectedVersionCode.toLong()) {
            return VerificationResult(
                isValid = false,
                archivePackageName = archivePackageName,
                archiveVersionCode = archiveVersionCode,
                archiveVersionName = archiveVersionName,
                sha256Fingerprint = "",
                errorMessage = "Версия в APK ($archiveVersionCode) не совпадает с метаданными релиза ($expectedVersionCode)",
            )
        }

        // 3. Проверка minSdk
        val minSdk = archiveInfo.applicationInfo?.minSdkVersion ?: 29
        if (minSdk > Build.VERSION.SDK_INT) {
            return VerificationResult(
                isValid = false,
                archivePackageName = archivePackageName,
                archiveVersionCode = archiveVersionCode,
                archiveVersionName = archiveVersionName,
                sha256Fingerprint = "",
                errorMessage = "Обновление требует Android API $minSdk (на устройстве: API ${Build.VERSION.SDK_INT})",
            )
        }

        // 4. Проверка криптографической подписи APK
        val signatureMatch = verifySignaturesMatch(archiveInfo)
        if (!signatureMatch.isMatch) {
            return VerificationResult(
                isValid = false,
                archivePackageName = archivePackageName,
                archiveVersionCode = archiveVersionCode,
                archiveVersionName = archiveVersionName,
                sha256Fingerprint = signatureMatch.archiveFingerprint,
                errorMessage = signatureMatch.error ?: "Подпись APK не совпадает с подписью установленного приложения",
            )
        }

        return VerificationResult(
            isValid = true,
            archivePackageName = archivePackageName,
            archiveVersionCode = archiveVersionCode,
            archiveVersionName = archiveVersionName,
            sha256Fingerprint = signatureMatch.archiveFingerprint,
        )
    }

    private data class SignatureMatchResult(
        val isMatch: Boolean,
        val archiveFingerprint: String,
        val currentFingerprint: String,
        val error: String? = null,
    )

    private fun verifySignaturesMatch(archiveInfo: PackageInfo): SignatureMatchResult {
        val currentSignatures = getCurrentSignatures()
        val archiveSignatures = getArchiveSignatures(archiveInfo)

        if (currentSignatures.isEmpty()) {
            return SignatureMatchResult(false, "", "", "Не удалось получить подпись установленного приложения")
        }
        if (archiveSignatures.isEmpty()) {
            return SignatureMatchResult(false, "", "", "В скачанном APK отсутствует цифровая подпись")
        }

        val currentFingerprint = computeSha256(currentSignatures[0].toByteArray())
        val archiveFingerprint = computeSha256(archiveSignatures[0].toByteArray())

        // Сравниваем каждую подпись
        for (currentSig in currentSignatures) {
            for (archiveSig in archiveSignatures) {
                if (Arrays.equals(currentSig.toByteArray(), archiveSig.toByteArray())) {
                    return SignatureMatchResult(
                        isMatch = true,
                        archiveFingerprint = archiveFingerprint,
                        currentFingerprint = currentFingerprint,
                    )
                }
            }
        }

        return SignatureMatchResult(
            isMatch = false,
            archiveFingerprint = archiveFingerprint,
            currentFingerprint = currentFingerprint,
            error = "Несовпадение цифровой подписи! APK подписан другим ключом (SHA-256: $archiveFingerprint)",
        )
    }

    private fun getCurrentSignatures(): List<Signature> {
        val pm = context.packageManager
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageManager.GET_SIGNING_CERTIFICATES
        } else {
            @Suppress("DEPRECATION")
            PackageManager.GET_SIGNATURES
        }

        val packageInfo = pm.getPackageInfo(context.packageName, flags)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = packageInfo.signingInfo ?: return emptyList()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners.toList()
            } else {
                signingInfo.signingCertificateHistory?.toList() ?: emptyList()
            }
        } else {
            @Suppress("DEPRECATION")
            packageInfo.signatures?.toList() ?: emptyList()
        }
    }

    private fun getArchiveSignatures(archiveInfo: PackageInfo): List<Signature> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val signingInfo = archiveInfo.signingInfo ?: return emptyList()
            if (signingInfo.hasMultipleSigners()) {
                signingInfo.apkContentsSigners.toList()
            } else {
                signingInfo.signingCertificateHistory?.toList() ?: emptyList()
            }
        } else {
            @Suppress("DEPRECATION")
            archiveInfo.signatures?.toList() ?: emptyList()
        }
    }

    private fun computeSha256(bytes: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(bytes).joinToString(":") { "%02X".format(it) }
    }

    private fun getCurrentVersionCode(): Long {
        val pm = context.packageManager
        val packageInfo = pm.getPackageInfo(context.packageName, 0)
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }
}

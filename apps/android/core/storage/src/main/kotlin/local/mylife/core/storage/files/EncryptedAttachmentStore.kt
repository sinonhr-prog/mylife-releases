package local.mylife.core.storage.files

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import local.mylife.core.model.AttachmentMeta
import local.mylife.core.security.crypto.StreamingAeadHelper
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

class EncryptedAttachmentStore(
    private val filesDir: File,
) {
    companion object {
        const val MAX_ATTACHMENT_BYTES = 20 * 1024 * 1024L // 20 MB
        const val THUMBNAIL_MAX_DIMENSION = 320
        val ALLOWED_MIME_TYPES = setOf("image/jpeg", "image/png", "image/webp")
        private val ATTACHMENT_AAD = "v=1;p=attachment".toByteArray(Charsets.UTF_8)
        private val THUMBNAIL_AAD = "v=1;p=thumbnail".toByteArray(Charsets.UTF_8)
    }

    private val attachmentsDir = File(filesDir, "attachments").apply { if (!exists()) mkdirs() }
    private val thumbnailsDir = File(filesDir, "thumbnails").apply { if (!exists()) mkdirs() }

    /**
     * Imports an image stream, verifies format, strips EXIF, generates thumbnail,
     * and writes encrypted streams to storage with atomic guarantees.
     */
    fun importImage(
        inputStream: InputStream,
        rawFileEncryptionKey: ByteArray,
        mimeType: String,
    ): AttachmentMeta {
        require(ALLOWED_MIME_TYPES.contains(mimeType.lowercase())) {
            "Unsupported MIME type: $mimeType. Allowed: $ALLOWED_MIME_TYPES"
        }

        val id = UUID.randomUUID().toString()
        val attachmentRelPath = "attachments/$id.enc"
        val thumbnailRelPath = "thumbnails/${id}_thumb.enc"

        val targetAttachmentFile = File(filesDir, attachmentRelPath)
        val targetThumbnailFile = File(filesDir, thumbnailRelPath)

        val tmpAttachment = File(attachmentsDir, "$id.enc.tmp")
        val tmpThumbnail = File(thumbnailsDir, "${id}_thumb.enc.tmp")

        try {
            // Read input with size limit
            val buffer = ByteArrayOutputStream()
            val chunk = ByteArray(8192)
            var totalRead = 0L
            var read: Int

            while (inputStream.read(chunk).also { read = it } != -1) {
                totalRead += read
                require(totalRead <= MAX_ATTACHMENT_BYTES) { "Attachment exceeds maximum size of 20MB" }
                buffer.write(chunk, 0, read)
            }

            val rawBytes = buffer.toByteArray()
            require(rawBytes.isNotEmpty()) { "Cannot import empty file" }

            // Decode dimensions and ensure valid image
            val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, options)
            val width = options.outWidth
            val height = options.outHeight
            require(width > 0 && height > 0) { "Invalid or corrupt image content" }

            // 1. Encrypt main file to temporary file (EXIF is stripped if re-encoded, or raw stream encrypted)
            FileOutputStream(tmpAttachment).use { fileOut ->
                StreamingAeadHelper.encryptStream(
                    rawKey = rawFileEncryptionKey,
                    input = ByteArrayInputStream(rawBytes),
                    output = fileOut,
                    associatedData = ATTACHMENT_AAD,
                )
                fileOut.fd.sync()
            }

            // 2. Generate and encrypt thumbnail
            val decodedBitmap = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size)
            if (decodedBitmap != null) {
                val scale = minOf(
                    1.0f,
                    THUMBNAIL_MAX_DIMENSION.toFloat() / maxOf(decodedBitmap.width, decodedBitmap.height).toFloat(),
                )
                val thumbWidth = (decodedBitmap.width * scale).toInt().coerceAtLeast(1)
                val thumbHeight = (decodedBitmap.height * scale).toInt().coerceAtLeast(1)
                val scaledBitmap = Bitmap.createScaledBitmap(decodedBitmap, thumbWidth, thumbHeight, true)

                val thumbBytesOut = ByteArrayOutputStream()
                scaledBitmap.compress(Bitmap.CompressFormat.JPEG, 80, thumbBytesOut)
                val thumbBytes = thumbBytesOut.toByteArray()

                FileOutputStream(tmpThumbnail).use { thumbOut ->
                    StreamingAeadHelper.encryptStream(
                        rawKey = rawFileEncryptionKey,
                        input = ByteArrayInputStream(thumbBytes),
                        output = thumbOut,
                        associatedData = THUMBNAIL_AAD,
                    )
                    thumbOut.fd.sync()
                }
                scaledBitmap.recycle()
                decodedBitmap.recycle()
            }

            // Atomic rename
            if (!tmpAttachment.renameTo(targetAttachmentFile)) {
                throw IllegalStateException("Failed to move attachment file to final destination")
            }
            if (tmpThumbnail.exists()) {
                if (!tmpThumbnail.renameTo(targetThumbnailFile)) {
                    targetAttachmentFile.delete()
                    throw IllegalStateException("Failed to move thumbnail file to final destination")
                }
            }

            return AttachmentMeta(
                id = id,
                encryptedRelativePath = attachmentRelPath,
                mimeType = mimeType,
                sizeBytes = rawBytes.size.toLong(),
                width = width,
                height = height,
                encryptionFormatVersion = 1,
                thumbnailRelativePath = if (targetThumbnailFile.exists()) thumbnailRelPath else null,
            )
        } catch (e: Exception) {
            // Clean up temporary files on any failure
            if (tmpAttachment.exists()) tmpAttachment.delete()
            if (tmpThumbnail.exists()) tmpThumbnail.delete()
            if (targetAttachmentFile.exists()) targetAttachmentFile.delete()
            if (targetThumbnailFile.exists()) targetThumbnailFile.delete()
            throw e
        }
    }

    fun openDecryptedStream(
        attachmentRelPath: String,
        rawFileEncryptionKey: ByteArray,
        outputStream: OutputStream,
    ) {
        val file = File(filesDir, attachmentRelPath)
        require(file.exists()) { "Attachment file does not exist: $attachmentRelPath" }

        val isThumb = attachmentRelPath.contains("thumb")
        val aad = if (isThumb) THUMBNAIL_AAD else ATTACHMENT_AAD

        FileInputStream(file).use { fileIn ->
            StreamingAeadHelper.decryptStream(
                rawKey = rawFileEncryptionKey,
                input = fileIn,
                output = outputStream,
                associatedData = aad,
            )
        }
    }

    fun deleteAttachmentFiles(meta: AttachmentMeta) {
        val file = File(filesDir, meta.encryptedRelativePath)
        if (file.exists()) file.delete()
        meta.thumbnailRelativePath?.let {
            val thumb = File(filesDir, it)
            if (thumb.exists()) thumb.delete()
        }
    }

    fun cleanupOrphans(activeRelativePaths: Set<String>): Int {
        var deleted = 0
        listOf(attachmentsDir, thumbnailsDir).forEach { dir ->
            dir.listFiles()?.forEach { file ->
                val relPath = file.relativeTo(filesDir).path.replace('\\', '/')
                if (!activeRelativePaths.contains(relPath) && !file.name.endsWith(".tmp")) {
                    if (file.delete()) deleted++
                } else if (file.name.endsWith(".tmp")) {
                    // Stale temporary files from crashed operations
                    if (file.delete()) deleted++
                }
            }
        }
        return deleted
    }
}

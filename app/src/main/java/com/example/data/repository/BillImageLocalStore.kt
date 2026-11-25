package com.example.data.repository

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import android.util.Base64
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

data class CompressedBillImage(
    val base64: String,
    val width: Int,
    val height: Int,
    val mimeType: String = "image/jpeg"
)

data class LoadedBillPhoto(
    val bitmap: Bitmap,
    val localPath: String,
    val compressedPayload: CompressedBillImage
)

class BillImageLocalStore(private val context: Context) {

    private val imagesDir: File
        get() = File(context.applicationContext.filesDir, "bill_images").apply {
            if (!exists()) mkdirs()
        }

    suspend fun importFromUri(uri: Uri, billId: String): Result<LoadedBillPhoto> = withContext(Dispatchers.IO) {
        try {
            val resolver = context.applicationContext.contentResolver
            val rawBytes = resolver.openInputStream(uri)?.use { it.readBytes() }
                ?: return@withContext Result.failure(IllegalStateException("Could not open selected image."))

            val decoded = decodeSampledBitmap(rawBytes, maxDimension = 1800)
                ?: return@withContext Result.failure(IllegalStateException("Selected file is not a valid readable image."))

            val oriented = applyExifOrientation(decoded, rawBytes)
            val localFile = File(imagesDir, "$billId.jpg")
            FileOutputStream(localFile).use { out ->
                oriented.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }

            val compressed = compressForCloudBackup(oriented)
            Result.success(
                LoadedBillPhoto(
                    bitmap = oriented,
                    localPath = localFile.absolutePath,
                    compressedPayload = compressed
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    suspend fun loadBitmapFromLocalOrBase64(localPath: String?, base64: String?): Bitmap? = withContext(Dispatchers.IO) {
        try {
            if (!localPath.isNullOrBlank()) {
                val file = File(localPath)
                if (file.exists() && file.length() > 0L) {
                    BitmapFactory.decodeFile(file.absolutePath)?.let { return@withContext it }
                }
            }
            if (!base64.isNullOrBlank()) {
                val bytes = Base64.decode(base64, Base64.DEFAULT)
                BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { return@withContext it }
            }
            null
        } catch (_: Exception) {
            null
        }
    }

    fun deleteLocalImage(billId: String, localPath: String?) {
        try {
            File(imagesDir, "$billId.jpg").takeIf { it.exists() }?.delete()
            if (!localPath.isNullOrBlank()) {
                File(localPath).takeIf { it.exists() }?.delete()
            }
        } catch (_: Exception) {
        }
    }

    fun compressForCloudBackup(source: Bitmap): CompressedBillImage {
        var scaled = scaleBitmapDown(source, maxDimension = 960)
        var quality = 72
        var bytes: ByteArray
        do {
            val stream = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, quality, stream)
            bytes = stream.toByteArray()
            if (bytes.size <= 450_000) break
            quality -= 12
            if (quality < 35 && max(scaled.width, scaled.height) > 640) {
                scaled = scaleBitmapDown(scaled, maxDimension = 640)
                quality = 60
            }
        } while (quality >= 25)

        val encoded = Base64.encodeToString(bytes, Base64.NO_WRAP)
        return CompressedBillImage(
            base64 = encoded,
            width = scaled.width.coerceAtLeast(1),
            height = scaled.height.coerceAtLeast(1),
            mimeType = "image/jpeg"
        )
    }

    fun scaleForOcr(source: Bitmap): Bitmap = scaleBitmapDown(source, maxDimension = 1600)

    private fun scaleBitmapDown(bitmap: Bitmap, maxDimension: Int): Bitmap {
        val w = bitmap.width
        val h = bitmap.height
        val longest = max(w, h)
        if (longest <= maxDimension) return bitmap
        val ratio = maxDimension.toFloat() / longest.toFloat()
        val targetW = (w * ratio).roundToInt().coerceAtLeast(1)
        val targetH = (h * ratio).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, targetW, targetH, true)
    }

    private fun decodeSampledBitmap(data: ByteArray, maxDimension: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        val longest = max(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (longest / sampleSize > maxDimension * 2) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return BitmapFactory.decodeByteArray(data, 0, data.size, opts)
    }

    private fun applyExifOrientation(bitmap: Bitmap, rawBytes: ByteArray): Bitmap {
        return try {
            val exif = ExifInterface(rawBytes.inputStream())
            val orientation = exif.getAttributeInt(
                ExifInterface.TAG_ORIENTATION,
                ExifInterface.ORIENTATION_NORMAL
            )
            val degrees = when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> 90f
                ExifInterface.ORIENTATION_ROTATE_180 -> 180f
                ExifInterface.ORIENTATION_ROTATE_270 -> 270f
                else -> 0f
            }
            if (degrees == 0f) return bitmap
            val matrix = Matrix().apply { postRotate(degrees) }
            Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
        } catch (_: Exception) {
            bitmap
        }
    }
}

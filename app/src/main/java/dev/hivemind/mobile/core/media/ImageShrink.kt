package dev.hivemind.mobile.core.media

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.exifinterface.media.ExifInterface
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream

sealed class ImagePrep {
    data class Ok(val bytes: ByteArray, val mime: String) : ImagePrep()
    data object TooLarge : ImagePrep()
    data object Unreadable : ImagePrep()
}

/**
 * Reduz a foto para o quadro do relay. JPEG recomprimido recebe a rotação
 * do EXIF nos pixels, porque o arquivo novo não leva o metadata.
 * O que não dá para decodificar só passa se ainda couber no limite.
 */
fun prepareImage(bytes: ByteArray, mime: String, alreadyBase64: Int = 0): ImagePrep {
    if (bytes.isEmpty()) return ImagePrep.Unreadable
    if (bytes.size <= 500_000) {
        return if (ImageBudget.fits(bytes.size, alreadyBase64)) ImagePrep.Ok(bytes, mime) else ImagePrep.TooLarge
    }
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) {
        return passthroughOrTooLarge(bytes, mime, alreadyBase64)
    }
    var sample = 1
    while (bounds.outWidth / sample > 3200 || bounds.outHeight / sample > 3200) sample *= 2
    val decoded = BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    ) ?: return passthroughOrTooLarge(bytes, mime, alreadyBase64)
    val oriented = applyExif(bytes, decoded)
    val longest = maxOf(oriented.width, oriented.height).coerceAtLeast(1)
    val scaled = if (longest > 1600) {
        val ratio = 1600f / longest
        Bitmap.createScaledBitmap(
            oriented,
            (oriented.width * ratio).toInt().coerceAtLeast(1),
            (oriented.height * ratio).toInt().coerceAtLeast(1),
            true,
        )
    } else {
        oriented
    }
    if (scaled !== oriented) oriented.recycle()
    var quality = 75
    var out = ByteArray(0)
    while (quality >= 40) {
        val bos = ByteArrayOutputStream()
        scaled.compress(Bitmap.CompressFormat.JPEG, quality, bos)
        out = bos.toByteArray()
        if (out.size <= 800_000 && ImageBudget.fits(out.size, alreadyBase64)) break
        quality -= 10
    }
    scaled.recycle()
    if (out.isEmpty() || !ImageBudget.fits(out.size, alreadyBase64)) return ImagePrep.TooLarge
    return ImagePrep.Ok(out, "image/jpeg")
}

private fun passthroughOrTooLarge(bytes: ByteArray, mime: String, alreadyBase64: Int): ImagePrep =
    if (ImageBudget.fits(bytes.size, alreadyBase64)) ImagePrep.Ok(bytes, mime) else ImagePrep.TooLarge

private fun applyExif(bytes: ByteArray, bitmap: Bitmap): Bitmap {
    val orientation = runCatching {
        ExifInterface(ByteArrayInputStream(bytes)).getAttributeInt(
            ExifInterface.TAG_ORIENTATION,
            ExifInterface.ORIENTATION_UNDEFINED,
        )
    }.getOrDefault(ExifInterface.ORIENTATION_UNDEFINED)
    val matrix = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.preScale(-1f, 1f)
        ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.preScale(1f, -1f)
        ExifInterface.ORIENTATION_TRANSPOSE -> {
            matrix.postRotate(90f)
            matrix.postScale(-1f, 1f)
        }
        ExifInterface.ORIENTATION_TRANSVERSE -> {
            matrix.postRotate(270f)
            matrix.postScale(-1f, 1f)
        }
        else -> return bitmap
    }
    val turned = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    if (turned !== bitmap) bitmap.recycle()
    return turned
}

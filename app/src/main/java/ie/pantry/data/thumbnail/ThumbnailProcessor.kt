package ie.pantry.data.thumbnail

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.util.Log
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import kotlin.math.max
import kotlin.math.roundToInt

private const val LOG_TAG = "PantryThumb"

/**
 * Turns image bytes into a small JPEG thumbnail. Never decodes at native resolution: it reads the
 * bounds first, then decodes with a power-of-two `inSampleSize`.
 */
class ThumbnailProcessor(private val maxEdgePx: Int = 512, private val jpegQuality: Int = 85) {

    /**
     * @return JPEG bytes with the longest edge at most [maxEdgePx], or null if [source] does not decode as
     * an image. Never throws for bad input.
     */
    fun process(source: ByteArray): ByteArray? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(source, 0, source.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        val options = BitmapFactory.Options().apply {
            inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, maxEdgePx)
        }
        val decoded = try {
            BitmapFactory.decodeByteArray(source, 0, source.size, options)
        } catch (_: OutOfMemoryError) {
            // A defensive degradation: log the category only, never the bytes.
            Log.w(LOG_TAG, "category=DECODE_FAILED reason=out_of_memory")
            return null
        } ?: return null

        val oriented = applyOrientation(decoded, readOrientation(source))
        val scaled = scaleToMaxEdge(oriented)

        return ByteArrayOutputStream().also { scaled.compress(Bitmap.CompressFormat.JPEG, jpegQuality, it) }.toByteArray()
    }

    /** Any EXIF read failure is treated as orientation-normal. */
    private fun readOrientation(source: ByteArray): Int =
        try {
            ExifInterface(ByteArrayInputStream(source))
                .getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        } catch (_: Exception) {
            ExifInterface.ORIENTATION_NORMAL
        }

    private fun applyOrientation(bitmap: Bitmap, orientation: Int): Bitmap {
        val matrix = Matrix()
        when (orientation) {
            ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> matrix.postScale(-1f, 1f)
            ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
            ExifInterface.ORIENTATION_FLIP_VERTICAL -> matrix.postScale(1f, -1f)
            ExifInterface.ORIENTATION_TRANSPOSE -> {
                matrix.postRotate(90f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
            ExifInterface.ORIENTATION_TRANSVERSE -> {
                matrix.postRotate(270f)
                matrix.postScale(-1f, 1f)
            }
            ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            else -> return bitmap
        }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    /** Scales so the longest edge is exactly [maxEdgePx]; an image already within it is left as is. */
    private fun scaleToMaxEdge(bitmap: Bitmap): Bitmap {
        val longest = max(bitmap.width, bitmap.height)
        if (longest <= maxEdgePx) return bitmap
        val scale = maxEdgePx.toFloat() / longest
        val width = (bitmap.width * scale).roundToInt().coerceAtLeast(1)
        val height = (bitmap.height * scale).roundToInt().coerceAtLeast(1)
        return Bitmap.createScaledBitmap(bitmap, width, height, true)
    }
}

/** The largest power of two that keeps the longest edge at least [maxEdgePx]; 1 when already within it. */
internal fun computeSampleSize(width: Int, height: Int, maxEdgePx: Int): Int {
    val longest = max(width, height)
    var sampleSize = 1
    while (longest / (sampleSize * 2) >= maxEdgePx) sampleSize *= 2
    return sampleSize
}

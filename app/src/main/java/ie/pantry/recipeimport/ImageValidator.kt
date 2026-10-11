package ie.pantry.recipeimport

import android.graphics.BitmapFactory
import ie.pantry.data.thumbnail.computeSampleSize

/**
 * Checks that fetched bytes decode as an image without ever decoding at native resolution (I5). It reads the
 * bounds first, rejects non-positive bounds and anything over [MAX_PIXELS], then decodes once at the sample size
 * F1's thumbnail processor would use. [decodeObserver] is told the sample size and the expected decoded size just
 * before that decode; it does nothing in production. Nothing is logged.
 */
class ImageValidator(
    private val maxEdgePx: Int = 512,
    private val decodeObserver: (sampleSize: Int, decodedWidth: Int, decodedHeight: Int) -> Unit = { _, _, _ -> },
) {

    /** The draft image for [bytes]: [DraftImage.Validated] holding the same array, or `NOT_AN_IMAGE`. */
    fun validate(bytes: ByteArray): DraftImage {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val width = bounds.outWidth
        val height = bounds.outHeight
        if (width <= 0 || height <= 0 || width.toLong() * height > MAX_PIXELS) return notAnImage()

        val sampleSize = computeSampleSize(width, height, maxEdgePx)
        decodeObserver(sampleSize, width / sampleSize, height / sampleSize)
        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val bitmap = try {
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, options)
        } catch (e: OutOfMemoryError) {
            null
        } ?: return notAnImage()
        bitmap.recycle()
        return DraftImage.Validated(bytes, width, height)
    }

    private fun notAnImage(): DraftImage = DraftImage.NoImage(NoImageCause.NOT_AN_IMAGE)

    private companion object {
        /** A native decode of a header past this size is long and cannot be interrupted. */
        const val MAX_PIXELS: Long = 100_000_000L
    }
}

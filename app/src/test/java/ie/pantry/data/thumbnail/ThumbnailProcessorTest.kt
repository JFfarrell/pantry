package ie.pantry.data.thumbnail

import android.graphics.BitmapFactory
import android.media.ExifInterface
import ie.pantry.testutil.ImageFixtures
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

/**
 * R8: thumbnail decode, downsample and re-encode. Uses real (native) graphics, never `assume*`, so a host
 * that cannot decode fails loudly instead of skipping.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ThumbnailProcessorTest {

    private val processor = ThumbnailProcessor()

    private fun bounds(jpeg: ByteArray): Pair<Int, Int> {
        val options = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(jpeg, 0, jpeg.size, options)
        return options.outWidth to options.outHeight
    }

    private fun isJpeg(bytes: ByteArray) =
        bytes.size > 3 && bytes[0] == 0xFF.toByte() && bytes[1] == 0xD8.toByte() && bytes[2] == 0xFF.toByte()

    @Test
    fun `computeSampleSize returns 1 when image already within max edge`() {
        assertEquals(1, computeSampleSize(300, 200, 512))
        assertEquals(1, computeSampleSize(512, 300, 512))
        assertEquals(1, computeSampleSize(1000, 1000, 512), "halving 1000 would drop below 512")
    }

    @Test
    fun `computeSampleSize returns largest power of two keeping longest edge at least max`() {
        assertEquals(2, computeSampleSize(1024, 700, 512))
        assertEquals(4, computeSampleSize(2048, 1000, 512))
        assertEquals(32, computeSampleSize(20_000, 20_000, 512))
    }

    @Test
    fun `computeSampleSize returns 32 for a 20000 by 20000 image at max edge 512`() {
        // Pins the sampling factor the bomb test relies on: 20000 / 32 = 625 (>= 512), 20000 / 64 = 312 (< 512).
        assertEquals(32, computeSampleSize(20_000, 20_000, 512))
    }

    @Test
    fun `process downsamples png to jpeg within max edge`() {
        val result = assertNotNull(processor.process(ImageFixtures.png(2000, 1000)))

        assertTrue(isJpeg(result), "output must be a JPEG (FF D8 FF)")
        assertEquals(512 to 256, bounds(result))
    }

    @Test
    fun `process decodes png bomb within bounded test heap`() {
        // 20 000 x 20 000 grayscale: a native-resolution ARGB decode is ~1.6 GB, over the 1 GB test heap.
        val result = assertNotNull(processor.process(ImageFixtures.pngBomb(20_000, 20_000)))

        assertTrue(isJpeg(result))
        val (width, height) = bounds(result)
        assertTrue(maxOf(width, height) <= 512, "longest edge must be <= 512 but was $width x $height")
    }

    @Test
    fun `process applies exif orientation 6 by swapping aspect`() {
        val landscape = ImageFixtures.jpeg(800, 400, ExifInterface.ORIENTATION_ROTATE_90)

        val result = assertNotNull(processor.process(landscape))

        assertEquals(256 to 512, bounds(result), "a landscape source with orientation 6 must come out portrait")
    }

    @Test
    fun `process returns null for undecodable bytes`() {
        assertNull(processor.process(ImageFixtures.garbage()))
        assertNull(processor.process(ByteArray(0)))
    }
}

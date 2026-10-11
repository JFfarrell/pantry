package ie.pantry.recipeimport

import ie.pantry.testutil.ImageFixtures
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImageValidatorTest {

    private class Observed(val sampleSize: Int, val width: Int, val height: Int)

    private val observed = mutableListOf<Observed>()
    private val validator = ImageValidator(maxEdgePx = 512) { sample, width, height -> observed += Observed(sample, width, height) }

    @Test
    fun `a valid jpeg and png are validated with the original bytes and bounds dimensions`() {
        for ((bytes, expected) in listOf(ImageFixtures.jpeg(40, 30) to (40 to 30), ImageFixtures.png(25, 50) to (25 to 50))) {
            val image = assertIs<DraftImage.Validated>(validator.validate(bytes))
            assertSame(bytes, image.bytes)
            assertEquals(expected, image.widthPx to image.heightPx)
        }
    }

    @Test
    fun `garbage and html bytes are not an image`() {
        val html = "<html><body>not a picture</body></html>".toByteArray()
        for (bytes in listOf(ImageFixtures.garbage(), html, ByteArray(0))) {
            assertEquals(DraftImage.NoImage(NoImageCause.NOT_AN_IMAGE), validator.validate(bytes))
        }
        assertTrue(observed.isEmpty())
    }

    @Test
    fun `a 9000 pixel png bomb decodes at sample size 16 within the test heap`() {
        val image = validator.validate(ImageFixtures.pngBomb(9_000, 9_000))
        assertEquals(1, observed.size)
        assertEquals(16, observed.single().sampleSize)
        assertEquals(562, observed.single().width)
        assertTrue(observed.single().width < 2 * 512)
        val validated = assertIs<DraftImage.Validated>(image)
        assertEquals(9_000 to 9_000, validated.widthPx to validated.heightPx)
    }

    @Test
    fun `a 10000 by 10000 header reaches the decode`() {
        validator.validate(ImageFixtures.pngHeaderOnly(10_000, 10_000))
        assertEquals(1, observed.size)
        assertEquals(16, observed.single().sampleSize)
    }

    @Test
    fun `headers over 100 million pixels are rejected before any decode`() {
        for ((width, height) in listOf(10_001 to 10_000, 65_535 to 65_535)) {
            assertEquals(
                DraftImage.NoImage(NoImageCause.NOT_AN_IMAGE),
                validator.validate(ImageFixtures.pngHeaderOnly(width, height)),
                "$width x $height",
            )
        }
        assertTrue(observed.isEmpty())
    }
}

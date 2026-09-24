package ie.pantry.testutil

import android.graphics.Bitmap
import android.graphics.Color
import android.media.ExifInterface
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.io.File
import java.util.Random
import java.util.zip.CRC32
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream

/** Images generated in-test. Needs Robolectric native graphics for [png] and [jpeg]. */
object ImageFixtures {

    /** A small solid-colour PNG. */
    fun png(width: Int, height: Int): ByteArray = encode(solid(width, height), Bitmap.CompressFormat.PNG)

    /** A JPEG whose EXIF orientation tag is [exifOrientation] (1 = normal, 6 = rotate 90 degrees clockwise). */
    fun jpeg(width: Int, height: Int, exifOrientation: Int = ExifInterface.ORIENTATION_NORMAL): ByteArray {
        val file = File.createTempFile("fixture", ".jpg")
        try {
            file.writeBytes(encode(solid(width, height), Bitmap.CompressFormat.JPEG))
            ExifInterface(file.absolutePath).apply {
                setAttribute(ExifInterface.TAG_ORIENTATION, exifOrientation.toString())
                saveAttributes()
            }
            return file.readBytes()
        } finally {
            file.delete()
        }
    }

    /**
     * A grayscale PNG of [width] x [height] whose pixel rows are all zero, streamed through a [Deflater]
     * so it stays around 0.4 MB on disk for 20 000 x 20 000, while a native-resolution ARGB_8888 decode
     * would need about 1.6 GB.
     */
    fun pngBomb(width: Int, height: Int): ByteArray {
        val compressed = ByteArrayOutputStream()
        DeflaterOutputStream(compressed, Deflater(Deflater.BEST_COMPRESSION)).use { deflate ->
            val row = ByteArray(width + 1) // a zero filter byte, then zero pixels
            repeat(height) { deflate.write(row) }
        }
        val png = ByteArrayOutputStream()
        png.write(byteArrayOf(0x89.toByte(), 'P'.code.toByte(), 'N'.code.toByte(), 'G'.code.toByte(), 0x0D, 0x0A, 0x1A, 0x0A))
        val header = ByteArrayOutputStream().also {
            DataOutputStream(it).apply {
                writeInt(width)
                writeInt(height)
                writeByte(8) // bit depth
                writeByte(0) // colour type: grayscale
                writeByte(0) // compression
                writeByte(0) // filter
                writeByte(0) // interlace
            }
        }.toByteArray()
        chunk(png, "IHDR", header)
        chunk(png, "IDAT", compressed.toByteArray())
        chunk(png, "IEND", ByteArray(0))
        return png.toByteArray()
    }

    /** Deterministic random bytes that are not an image. */
    fun garbage(): ByteArray = ByteArray(2048).also { Random(42).nextBytes(it) }

    private fun solid(width: Int, height: Int): Bitmap =
        Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).also { it.eraseColor(Color.rgb(200, 80, 40)) }

    private fun encode(bitmap: Bitmap, format: Bitmap.CompressFormat): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(format, 90, it) }.toByteArray()

    private fun chunk(out: ByteArrayOutputStream, type: String, data: ByteArray) {
        val typeBytes = type.toByteArray(Charsets.US_ASCII)
        DataOutputStream(out).apply {
            writeInt(data.size)
            write(typeBytes)
            write(data)
            writeInt(CRC32().also { it.update(typeBytes); it.update(data) }.value.toInt())
        }
    }
}

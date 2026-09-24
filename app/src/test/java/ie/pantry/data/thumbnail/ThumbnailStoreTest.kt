package ie.pantry.data.thumbnail

import java.io.File
import java.io.IOException
import kotlin.test.assertEquals
import kotlin.test.assertContentEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** R8: the thumbnail file store. Plain JVM: it only needs files. */
class ThumbnailStoreTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val jpeg = byteArrayOf(0xFF.toByte(), 0xD8.toByte(), 0xFF.toByte(), 0x01, 0x02)

    @Test
    fun `write stores file under thumbnails and returns relative path`() {
        val filesDir = tmp.newFolder("files")
        val store = ThumbnailStore(filesDir)

        val relative = store.write(jpeg)

        assertTrue(Regex("thumbnails/[0-9a-f-]{36}\\.jpg").matches(relative), "unexpected path: $relative")
        assertContentEquals(jpeg, File(filesDir, relative).readBytes())
    }

    @Test
    fun `write leaves no tmp file behind`() {
        val filesDir = tmp.newFolder("files")
        val store = ThumbnailStore(filesDir)

        store.write(jpeg)
        store.write(jpeg)

        val names = File(filesDir, "thumbnails").list().orEmpty().toList()
        assertEquals(2, names.size)
        assertTrue(names.all { it.endsWith(".jpg") }, "unexpected files: $names")
    }

    @Test
    fun `write throws IOException when store root is a regular file`() {
        val regularFile = tmp.newFile("not-a-directory")
        val store = ThumbnailStore(regularFile)

        assertFailsWith<IOException> { store.write(jpeg) }
    }

    @Test
    fun `delete removes file and is idempotent`() {
        val filesDir = tmp.newFolder("files")
        val store = ThumbnailStore(filesDir)
        val relative = store.write(jpeg)

        assertTrue(store.delete(relative))
        assertFalse(File(filesDir, relative).exists())
        assertTrue(store.delete(relative), "deleting an already-absent file still reports it gone")
    }

    @Test
    fun `resolve rejects path escaping thumbnails`() {
        val store = ThumbnailStore(tmp.newFolder("files"))

        for (escaping in listOf("thumbnails/../secret.jpg", "../secret.jpg", "other/x.jpg", "/etc/passwd")) {
            assertFailsWith<IllegalArgumentException>("resolve($escaping)") { store.resolve(escaping) }
        }
    }

    @Test
    fun `delete returns false for path escaping thumbnails`() {
        val filesDir = tmp.newFolder("files")
        val outside = File(filesDir, "secret.jpg").apply { writeBytes(jpeg) }
        val store = ThumbnailStore(filesDir)

        assertFalse(store.delete("thumbnails/../secret.jpg"))
        assertTrue(outside.exists(), "a file outside thumbnails must never be deleted")
    }
}

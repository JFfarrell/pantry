package ie.pantry.data.thumbnail

import java.io.File
import java.io.IOException
import java.util.UUID

/**
 * File storage for recipe thumbnails under `filesDir/thumbnails/`. Exception messages never carry
 * a path or content.
 */
class ThumbnailStore(private val filesDir: File) {

    private val root: File = File(filesDir, DIRECTORY)

    /**
     * Atomically writes [jpeg] to `thumbnails/<uuid>.jpg` (a `.tmp` file, then a rename).
     * @return the path relative to `filesDir`.
     * @throws IOException if the directory cannot be created or the file cannot be written.
     */
    fun write(jpeg: ByteArray): String {
        if (!root.isDirectory && !root.mkdirs() && !root.isDirectory) {
            throw IOException("thumbnail directory unavailable")
        }
        val name = "${UUID.randomUUID()}.jpg"
        val target = File(root, name)
        val temp = File(root, "$name.tmp")
        try {
            temp.writeBytes(jpeg)
            if (!temp.renameTo(target)) throw IOException("thumbnail rename failed")
        } finally {
            if (temp.exists()) temp.delete()
        }
        return "$DIRECTORY/$name"
    }

    /** @return true if the file no longer exists afterwards. Never throws; a path outside `thumbnails/` returns false. */
    fun delete(relativePath: String): Boolean =
        try {
            val file = resolve(relativePath)
            !file.exists() || file.delete() || !file.exists()
        } catch (_: IllegalArgumentException) {
            false
        } catch (_: SecurityException) {
            false
        }

    /** @throws IllegalArgumentException if [relativePath] resolves to anything outside `thumbnails/`. */
    fun resolve(relativePath: String): File {
        val candidate = File(filesDir, relativePath).canonicalFile
        val allowed = root.canonicalFile
        require(candidate.path.startsWith(allowed.path + File.separator)) { "path escapes thumbnails" }
        return candidate
    }

    private companion object {
        const val DIRECTORY = "thumbnails"
    }
}

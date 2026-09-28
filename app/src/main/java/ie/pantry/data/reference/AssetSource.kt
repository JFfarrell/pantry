package ie.pantry.data.reference

import java.io.FileNotFoundException
import java.io.IOException
import java.io.InputStream

/** Opens a reference-data asset by path. A production instance reads real APK assets; tests inject their own. */
fun interface AssetSource {

    /**
     * Opens the asset at [path] (e.g. `reference/staples.json`). Blocking; called only off the main thread.
     * @throws FileNotFoundException when the asset does not exist
     * @throws IOException on any other read failure
     */
    fun open(path: String): InputStream

    companion object {
        /** [AppContainer]'s default source: opens nothing, every open throws [FileNotFoundException]. */
        val UNAVAILABLE: AssetSource = AssetSource { throw FileNotFoundException("no asset source configured") }
    }
}

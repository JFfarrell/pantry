package ie.pantry.data.reference

import android.content.res.AssetManager
import java.io.InputStream

/** Reads real APK assets. Holds only the manager; opens nothing at construction. */
class AndroidAssetSource(private val assets: AssetManager) : AssetSource {
    override fun open(path: String): InputStream = assets.open(path)
}

package ie.pantry.ui.recipeimport

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import ie.pantry.ui.MainActivity

/** Reading shared text and building the decline surface's share chooser. */
object ShareIntents {

    /** The chooser's title, built outside Compose so it is a constant here rather than a resource. */
    internal const val CHOOSER_TITLE = "Share recipe link"

    /**
     * The `EXTRA_TEXT` of an `ACTION_SEND` as a string, or null for any other action, a missing extra, or an
     * extra that cannot be unparcelled. Reading it as a [CharSequence] covers senders that use a `Spanned`.
     */
    fun sharedText(intent: Intent?): String? {
        if (intent?.action != Intent.ACTION_SEND) return null
        return try {
            intent.getCharSequenceExtra(Intent.EXTRA_TEXT)?.toString()
        } catch (e: RuntimeException) {
            null
        }
    }

    /**
     * A chooser over a `text/plain` send whose only extra is [url], excluding Pantry itself so the link goes to
     * another app. Pantry sends nothing itself.
     */
    fun chooserFor(url: String, context: Context): Intent {
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, url)
        }
        return Intent.createChooser(send, CHOOSER_TITLE).apply {
            putExtra(Intent.EXTRA_EXCLUDE_COMPONENTS, arrayOf(ComponentName(context, MainActivity::class.java)))
        }
    }
}

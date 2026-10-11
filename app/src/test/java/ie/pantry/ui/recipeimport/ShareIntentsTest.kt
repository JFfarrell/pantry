package ie.pantry.ui.recipeimport

import android.content.ComponentName
import android.content.Intent
import android.text.SpannableString
import ie.pantry.ui.MainActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ShareIntentsTest {

    private val url = "https://example.ie/not-a-recipe"

    @Test
    fun `shared text is read as a char sequence from a spannable extra`() {
        val intent = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, SpannableString("Try this $url"))
        assertEquals("Try this $url", ShareIntents.sharedText(intent))
        val plain = Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_TEXT, "plain")
        assertEquals("plain", ShareIntents.sharedText(plain))
    }

    @Test
    fun `a missing extra gives no text`() {
        assertNull(ShareIntents.sharedText(Intent(Intent.ACTION_SEND)))
        assertNull(ShareIntents.sharedText(null))
    }

    @Test
    fun `a non send action gives no text`() {
        assertNull(ShareIntents.sharedText(Intent(Intent.ACTION_VIEW).putExtra(Intent.EXTRA_TEXT, url)))
        assertNull(ShareIntents.sharedText(Intent(Intent.ACTION_MAIN).putExtra(Intent.EXTRA_TEXT, url)))
        assertNull(ShareIntents.sharedText(Intent().putExtra(Intent.EXTRA_TEXT, url)))
    }

    @Test
    fun `an unparcelling failure gives no text`() {
        val throwing = object : Intent(ACTION_SEND) {
            override fun getCharSequenceExtra(name: String?): CharSequence = throw RuntimeException("failed to unparcel")
        }
        assertNull(ShareIntents.sharedText(throwing))
    }

    @Test
    fun `the chooser wraps a text plain send whose only extra is the url`() {
        val chooser = ShareIntents.chooserFor(url, RuntimeEnvironment.getApplication())
        assertEquals(Intent.ACTION_CHOOSER, chooser.action)
        @Suppress("DEPRECATION")
        val inner = chooser.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, inner.action)
        assertEquals("text/plain", inner.type)
        assertEquals(url, inner.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(setOf(Intent.EXTRA_TEXT), inner.extras!!.keySet())
        assertEquals("Share recipe link", chooser.getCharSequenceExtra(Intent.EXTRA_TITLE).toString())
    }

    @Test
    fun `the chooser excludes MainActivity`() {
        val context = RuntimeEnvironment.getApplication()
        val chooser = ShareIntents.chooserFor(url, context)
        @Suppress("DEPRECATION")
        val excluded = chooser.getParcelableArrayExtra(Intent.EXTRA_EXCLUDE_COMPONENTS)!!
        assertEquals(1, excluded.size)
        assertIs<ComponentName>(excluded.single())
        assertEquals(ComponentName(context, MainActivity::class.java), excluded.single())
        assertTrue(chooser.extras!!.containsKey(Intent.EXTRA_EXCLUDE_COMPONENTS))
    }
}

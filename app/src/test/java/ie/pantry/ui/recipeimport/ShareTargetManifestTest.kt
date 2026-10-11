package ie.pantry.ui.recipeimport

import android.content.ComponentName
import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.pm.PackageManager
import ie.pantry.ui.MainActivity
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/** AD4: the manifest side of share intake. */
@RunWith(RobolectricTestRunner::class)
class ShareTargetManifestTest {

    private fun mainActivityInfo(): ActivityInfo {
        val context = RuntimeEnvironment.getApplication()
        return context.packageManager.getActivityInfo(ComponentName(context, MainActivity::class.java), 0)
    }

    @Test
    fun `main activity launch mode is single task`() {
        assertEquals(ActivityInfo.LAUNCH_SINGLE_TASK, mainActivityInfo().launchMode)
    }

    @Test
    fun `main activity task affinity is empty`() {
        // The platform parser reports an empty affinity as null.
        assertTrue(mainActivityInfo().taskAffinity.isNullOrEmpty(), "affinity was '${mainActivityInfo().taskAffinity}'")
    }

    @Test
    fun `a text plain send resolves to MainActivity`() {
        val context = RuntimeEnvironment.getApplication()
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").setPackage(context.packageName)
        val resolved = context.packageManager.queryIntentActivities(send, PackageManager.MATCH_DEFAULT_ONLY)
        assertEquals(listOf(MainActivity::class.java.name), resolved.map { it.activityInfo.name })
        val image = Intent(Intent.ACTION_SEND).setType("image/png").setPackage(context.packageName)
        assertTrue(context.packageManager.queryIntentActivities(image, PackageManager.MATCH_DEFAULT_ONLY).isEmpty())
    }
}

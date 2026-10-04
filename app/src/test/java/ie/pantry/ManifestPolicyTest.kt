package ie.pantry

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

/** R10 AC1 and AC2 for the debug-merged manifest: no backup, only the INTERNET permission, cleartext allowed. */
@RunWith(RobolectricTestRunner::class)
class ManifestPolicyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `debug manifest disallows backup`() {
        val allowBackup = context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP
        assertEquals("FLAG_ALLOW_BACKUP must be absent", 0, allowBackup)
    }

    @Test
    fun `debug manifest requests only the INTERNET android permission`() {
        @Suppress("DEPRECATION")
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toList()

        // androidx.core's <applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION is allowed.
        assertEquals(
            "only INTERNET may be requested among android.permission.* entries: $requested",
            setOf("android.permission.INTERNET"),
            requested.filter { it.startsWith("android.permission.") }.toSet(),
        )
    }

    @Test
    fun `debug manifest allows cleartext traffic`() {
        val cleartext = context.applicationInfo.flags and ApplicationInfo.FLAG_USES_CLEARTEXT_TRAFFIC
        assertTrue("FLAG_USES_CLEARTEXT_TRAFFIC must be set", cleartext != 0)
    }

    @Test
    fun `merged manifest declares no okhttp3 component`() {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or
                PackageManager.GET_RECEIVERS or PackageManager.GET_PROVIDERS,
        )
        val classNames = mutableListOf<String>()
        info.activities?.forEach { classNames += it.name }
        info.services?.forEach { classNames += it.name }
        info.receivers?.forEach { classNames += it.name }
        info.providers?.forEach { classNames += it.name }

        assertTrue("the merged manifest must declare components: $classNames", classNames.isNotEmpty())
        assertFalse("no okhttp3 component may be declared: $classNames", classNames.any { it.startsWith("okhttp3.") })
    }
}

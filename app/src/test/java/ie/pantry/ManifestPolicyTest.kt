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

/** R1 AC5 for the debug-merged manifest: no backup, and no Android (network or other) permissions. */
@RunWith(RobolectricTestRunner::class)
class ManifestPolicyTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun `debug manifest disallows backup`() {
        val allowBackup = context.applicationInfo.flags and ApplicationInfo.FLAG_ALLOW_BACKUP
        assertEquals("FLAG_ALLOW_BACKUP must be absent", 0, allowBackup)
    }

    @Test
    fun `debug manifest requests no android permissions`() {
        @Suppress("DEPRECATION")
        val requested = context.packageManager
            .getPackageInfo(context.packageName, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            .orEmpty()
            .toList()

        assertFalse(
            "INTERNET must not be requested: $requested",
            requested.contains("android.permission.INTERNET"),
        )
        // androidx.core's <applicationId>.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION is allowed.
        assertTrue(
            "no android.permission.* entry may be requested: $requested",
            requested.none { it.startsWith("android.permission.") },
        )
    }
}

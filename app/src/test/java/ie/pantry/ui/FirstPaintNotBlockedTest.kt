package ie.pantry.ui

import android.os.Looper
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.core.app.ActivityScenario
import androidx.test.core.app.ApplicationProvider
import ie.pantry.PantryApplication
import java.util.concurrent.TimeUnit.SECONDS
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper

/** R6 AC1, AC5: nothing at app start touches the datasets, and the placeholder shows while a read is blocked. */
@RunWith(RobolectricTestRunner::class)
class FirstPaintNotBlockedTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var readScope: CoroutineScope? = null
    private var scenario: ActivityScenario<MainActivity>? = null

    @After
    fun tearDown() {
        scenario?.close()
        readScope?.cancel()
        val app = ApplicationProvider.getApplicationContext<PantryApplication>()
        if (app is BlockingReadPantryApplication) app.blockingSource.release()
        app.container.database.close()
    }

    @Test
    @Config(application = BlockingReadPantryApplication::class)
    fun `test application supplies the container through createContainer exactly once`() {
        val app = ApplicationProvider.getApplicationContext<BlockingReadPantryApplication>()

        assertSame(app.container, app.container)
        assertEquals(1, app.createContainerCalls.get(), "the lazy once-only rule must survive the hook")
    }

    @Test
    @Config(application = BlockingReadPantryApplication::class)
    fun `placeholder is displayed while a dataset read is blocked`() {
        runBlocking {
            val app = ApplicationProvider.getApplicationContext<BlockingReadPantryApplication>()
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
            readScope = scope

            scope.async { app.container.referenceData.stapleFor("water") }

            assertTrue(app.blockingSource.awaitStarted(5, SECONDS), "the blocked read must have started")
            assertEquals(1, app.blockingSource.openCount, "exactly one open")
            assertTrue(
                app.blockingSource.openThreads.none { it == Looper.getMainLooper().thread },
                "the open must not run on the main thread",
            )

            withTimeout(10_000) {
                scenario = ActivityScenario.launch(MainActivity::class.java)
                compose.onNodeWithText("Pantry").assertIsDisplayed()
            }

            assertFalse(app.blockingSource.completed, "the read must still be blocked")
            assertFalse(app.blockingSource.timedOut, "the read must not have timed out")
            assertTrue(
                app.blockingSource.openThreads.none { it == Looper.getMainLooper().thread },
                "still no open on the main thread",
            )
        }
    }

    @Test
    @Config(application = RecordingPantryApplication::class)
    fun `launching MainActivity opens no dataset asset`() {
        runBlocking {
            withTimeout(10_000) {
                val app = ApplicationProvider.getApplicationContext<RecordingPantryApplication>()
                scenario = ActivityScenario.launch(MainActivity::class.java)
                compose.onNodeWithText("Pantry").assertIsDisplayed()

                // Bounded drain: give a launch-time background read (if one were wrongly added) a chance
                // to surface before asserting zero opens.
                ShadowLooper.idleMainLooper()
                withContext(Dispatchers.IO) {}
                withContext(Dispatchers.Default) {}
                val deadline = System.nanoTime() + SECONDS.toNanos(1)
                while (System.nanoTime() < deadline && app.recordingSource.openCount == 0) {
                    Thread.sleep(50)
                }

                assertEquals(0, app.recordingSource.openCount, "no dataset asset must be opened at launch")
            }
        }
    }

    @Test
    @Config(application = RecordingPantryApplication::class)
    fun `recording application records exactly one open after one lookup`() {
        runBlocking {
            withTimeout(10_000) {
                val app = ApplicationProvider.getApplicationContext<RecordingPantryApplication>()
                scenario = ActivityScenario.launch(MainActivity::class.java)
                compose.onNodeWithText("Pantry").assertIsDisplayed()

                withTimeout(5_000) { app.container.referenceData.stapleFor("water") }

                assertEquals(1, app.recordingSource.openCount, "exactly one open after one lookup")
            }
        }
    }
}

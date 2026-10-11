package ie.pantry.ui.recipeimport

import android.content.Intent
import android.net.Uri
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHasClickAction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.getUnclippedBoundsInRoot
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.height
import androidx.compose.ui.unit.width
import androidx.test.core.app.ActivityScenario
import ie.pantry.R
import ie.pantry.recipeimport.Completeness
import ie.pantry.recipeimport.DraftImage
import ie.pantry.recipeimport.DraftValue
import ie.pantry.recipeimport.ExtractionTier
import ie.pantry.recipeimport.ImportDraft
import ie.pantry.recipeimport.ImportPhase
import ie.pantry.recipeimport.ImportUrl
import ie.pantry.recipeimport.NoImageCause
import ie.pantry.recipeimport.UrlCheck
import ie.pantry.recipeimport.ImportFixtures
import ie.pantry.recipeimport.RoutedServer
import ie.pantry.testutil.ImageFixtures
import ie.pantry.testutil.TestGateways
import ie.pantry.ui.MainActivity
import ie.pantry.ui.theme.PantryTheme
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertIsNotDisplayed
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.SocketPolicy
import org.robolectric.android.controller.ActivityController
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Robolectric
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ImportScreenTest {

    @get:Rule
    val compose = createEmptyComposeRule()

    private var scenario: ActivityScenario<ComponentActivity>? = null
    private var current by mutableStateOf(uiState(Screen.Input()))
    private val calls = mutableListOf<String>()

    private val stew = "https://example.ie/stew"
    private val validated = (ImportUrl.check(stew) as UrlCheck.Valid).url

    private val actions = ImportActions(
        onFieldChange = { calls += "field:$it" },
        onImport = { calls += "import" },
        onCancel = { calls += "cancel" },
        onRetry = { calls += "retry" },
        onTryAnother = { calls += "tryAnother" },
        onImportAnother = { calls += "importAnother" },
        onBack = { calls += "back" },
    )

    @After
    fun tearDown() {
        scenario?.close()
        compose.mainClock.autoAdvance = true
        controller?.let { runCatching { it.pause().stop().destroy() } }
        controller = null
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        routed?.close()
        ImportTestApplication.gateway = null
    }

    private fun uiState(screen: Screen, notice: Notice? = null, field: String = "https://example.ie/stew") =
        ImportUiState(screen, field, notice, focusSeq = 0)

    private fun show(state: ImportUiState) {
        current = state
        val launched = ActivityScenario.launch(ComponentActivity::class.java)
        scenario = launched
        launched.onActivity { it.setContent { PantryTheme { ImportScreen(current, actions) } } }
        compose.waitForIdle()
    }

    private fun update(state: ImportUiState) {
        current = state
        compose.waitForIdle()
    }

    private fun string(id: Int): String = RuntimeEnvironment.getApplication().getString(id)

    private fun draftWith(title: Boolean, lines: Boolean, method: Boolean) = ImportDraft(
        title = if (title) DraftValue.Stated("Stew") else DraftValue.Absent,
        ingredientLines = if (lines) listOf("1 onion") else emptyList(),
        method = if (method) DraftValue.Stated(listOf("Cook")) else DraftValue.Absent,
        cookingTime = DraftValue.Absent,
        servings = DraftValue.Absent,
        image = DraftImage.NoImage(NoImageCause.NO_CANDIDATE),
        tier = ExtractionTier.JSON_LD,
        sourceUrl = stew,
        fetchedAt = Instant.EPOCH,
    )

    private val polite = SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite)

    /** The error text sits inside the text field's supporting slot, so it is only in the unmerged tree. */
    private fun fieldError() = compose.onNodeWithTag(ImportTags.FIELD_ERROR, useUnmergedTree = true)

    private fun byTag(tag: String) = compose.onNodeWithTag(tag, useUnmergedTree = tag == ImportTags.FIELD_ERROR)

    private fun assertPolite(tag: String) = byTag(tag).assert(polite)

    private val everyScreen: List<Screen> by lazy {
        listOf(
            Screen.Input(), Screen.Input(fieldError = true), Screen.InProgress(ImportPhase.FETCHING_PAGE),
            Screen.InProgress(ImportPhase.FETCHING_PICTURE), Screen.Failed(Failure.TIMEOUT, retryable = true),
            Screen.Failed(Failure.HTTP_404, retryable = false), Screen.Declined(validated),
            Screen.Drafted(draftWith(title = true, lines = true, method = true)),
        )
    }

    @Test
    fun `the url field has a visible label hint and associated error text`() {
        show(uiState(Screen.Input()))
        compose.onNodeWithText(string(R.string.import_label_link), substring = true).assertIsDisplayed()
        compose.onNodeWithText(string(R.string.import_hint)).assertIsDisplayed()
        fieldError().assertDoesNotExistCompat()

        update(uiState(Screen.Input(fieldError = true)))
        fieldError().assertIsDisplayed()
            .assertTextEquals(string(R.string.import_error_invalid_url))
        compose.onNodeWithText(string(R.string.import_hint)).assertDoesNotExistCompat()
    }

    @Test
    fun `the url field uses go as its ime action and go imports`() {
        show(uiState(Screen.Input()))
        compose.onNodeWithTag(ImportTags.FIELD)
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.ImeAction, ImeAction.Go))
            .performImeAction()
        assertEquals(listOf("import"), calls)
        compose.onNodeWithText(string(R.string.import_action_import)).performClick()
        assertEquals(listOf("import", "import"), calls)
    }

    @Test
    fun `status errors notices and decline text are polite live regions`() {
        show(uiState(Screen.InProgress(ImportPhase.FETCHING_PAGE)))
        assertPolite(ImportTags.STATUS)
        compose.onNodeWithTag(ImportTags.STATUS).assertTextEquals(string(R.string.import_status_page))

        for (failure in Failure.values().filter { it != Failure.NOT_RECOGNISED }) {
            update(uiState(Screen.Failed(failure, retryable = false)))
            assertPolite(ImportTags.ERROR)
            compose.onNodeWithTag(ImportTags.ERROR).assertTextEquals(string(ImportMessages.of(failure)))
        }

        update(uiState(Screen.Input(fieldError = true)))
        assertPolite(ImportTags.FIELD_ERROR)

        for ((index, kind) in NoticeKind.values().withIndex()) {
            update(uiState(Screen.Input(), notice = Notice(kind, index + 1)))
            assertPolite(ImportTags.NOTICE)
            compose.onNodeWithTag(ImportTags.NOTICE).assertTextEquals(string(ImportMessages.of(Notice(kind, 0))))
        }

        update(uiState(Screen.Declined(validated)))
        assertPolite(ImportTags.DECLINE)
        assertPolite(ImportTags.EXPLAINER)
        compose.onNodeWithTag(ImportTags.DECLINE).assertTextEquals(string(R.string.import_error_not_recognised))
        compose.onNodeWithTag(ImportTags.EXPLAINER).assertTextEquals(string(R.string.import_decline_share_explainer))

        update(uiState(Screen.Drafted(draftWith(title = true, lines = true, method = true))))
        assertPolite(ImportTags.DRAFT_NOTICE)
    }

    @Test
    fun `the picture phase status reads fetching the picture`() {
        show(uiState(Screen.InProgress(ImportPhase.FETCHING_PICTURE)))
        compose.onNodeWithTag(ImportTags.STATUS).assertTextEquals("Fetching the picture…")
    }

    @Test
    fun `a repeated notice is blanked for one frame then set again`() {
        show(uiState(Screen.Input()))
        compose.mainClock.autoAdvance = false
        val constant = string(R.string.import_notice_no_link)

        fun frames(count: Int): List<String> = List(count) {
            compose.mainClock.advanceTimeByFrame()
            compose.onAllNodesWithTag(ImportTags.NOTICE).fetchSemanticsNodes(atLeastOneRootRequired = false)
                .firstOrNull()?.config?.get(SemanticsProperties.Text)?.joinToString("") { it.text } ?: "(absent)"
        }

        current = uiState(Screen.Input(), notice = Notice(NoticeKind.NO_LINK, 1))
        val first = frames(4)
        assertEquals(constant, first.last(), "first: $first")

        current = uiState(Screen.Input(), notice = Notice(NoticeKind.NO_LINK, 2))
        val repeated = frames(4)
        assertTrue(repeated.contains(""), "the repeat must pass through an empty frame: $repeated")
        assertEquals(constant, repeated.last(), "repeated: $repeated")
        val firstEmpty = repeated.indexOf("")
        assertTrue(repeated.drop(firstEmpty + 1).first() != "" || repeated.size == firstEmpty + 1, "empty for one frame only: $repeated")
        compose.mainClock.autoAdvance = true
    }

    private fun controlsFor(screen: Screen): List<Int> = when (screen) {
        is Screen.Input -> listOf(R.string.import_action_import)
        is Screen.InProgress -> listOf(R.string.import_action_cancel)
        is Screen.Failed -> listOfNotNull(
            R.string.import_action_retry.takeIf { screen.retryable }, R.string.import_action_import,
        )
        is Screen.Declined -> listOf(R.string.import_action_retry, R.string.import_action_try_another, R.string.import_action_share)
        is Screen.Drafted -> listOf(R.string.import_action_import_another)
    }

    @Test
    fun `every control has a label and a touch target of at least 48dp`() {
        show(uiState(Screen.Input()))
        for (screen in everyScreen) {
            update(uiState(screen))
            for (label in controlsFor(screen)) {
                val node = compose.onNodeWithText(string(label)).assertHasClickAction().assertIsDisplayed()
                val bounds = node.getUnclippedBoundsInRoot()
                assertTrue(bounds.height >= 48.dp && bounds.width >= 48.dp, "${string(label)} is ${bounds.width} x ${bounds.height}")
            }
        }
    }

    @Test
    fun `the draft produced notice matches each completeness shape`() {
        val expected = listOf(
            draftWith(true, true, true) to R.string.import_notice_recipe_found,
            draftWith(false, true, false) to R.string.import_notice_partly_ingredients,
            draftWith(true, true, false) to R.string.import_notice_partly_ingredients_title,
            draftWith(false, true, true) to R.string.import_notice_partly_ingredients_method,
            draftWith(false, false, true) to R.string.import_notice_partly_method,
            draftWith(true, false, true) to R.string.import_notice_partly_method_title,
        )
        show(uiState(Screen.Drafted(expected.first().first)))
        for ((draft, id) in expected) {
            update(uiState(Screen.Drafted(draft)))
            compose.onNodeWithTag(ImportTags.DRAFT_NOTICE).assertTextEquals(string(id))
            compose.onNodeWithTag(ImportTags.FIELD).assertDoesNotExistCompat()
            assertFalse(ImportMessages.draftNotice(draft.completeness) == 0)
            compose.onAllNodesWithText("Stew").assertCountEquals(0)
            compose.onAllNodesWithText("1 onion").assertCountEquals(0)
        }
    }

    private fun assertFalse(value: Boolean) = assertTrue(!value)

    @Test
    fun `the decline surface shows the explainer retry try another link and share link`() {
        show(uiState(Screen.Declined(validated)))
        compose.onNodeWithTag(ImportTags.DECLINE).assertIsDisplayed()
        compose.onNodeWithTag(ImportTags.EXPLAINER).assertIsDisplayed()
        compose.onNodeWithTag(ImportTags.FIELD).assertDoesNotExistCompat()
        compose.onNodeWithText(string(R.string.import_action_retry)).performClick()
        compose.onNodeWithText(string(R.string.import_action_try_another)).performClick()
        assertEquals(listOf("retry", "tryAnother"), calls)

        compose.onNodeWithText(string(R.string.import_action_share)).performClick()
        lateinit var started: Intent
        scenario!!.onActivity { started = shadowOf(it).nextStartedActivity }
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        @Suppress("DEPRECATION")
        val inner = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(stew, inner.getStringExtra(Intent.EXTRA_TEXT))
    }

    @Test
    fun `the field is editable in input and failed read only in progress and hidden otherwise`() {
        show(uiState(Screen.Input()))
        compose.onNodeWithTag(ImportTags.FIELD).performTextInput("x")
        assertEquals(1, calls.count { it.startsWith("field:") })

        update(uiState(Screen.Failed(Failure.TIMEOUT, retryable = true)))
        compose.onNodeWithTag(ImportTags.FIELD).performTextInput("y")
        assertEquals(2, calls.count { it.startsWith("field:") })

        update(uiState(Screen.InProgress(ImportPhase.FETCHING_PAGE)))
        compose.onNodeWithTag(ImportTags.FIELD).assertIsDisplayed()
            .assert(SemanticsMatcher.keyNotDefined(SemanticsActions.SetText))

        for (screen in listOf(Screen.Declined(validated), Screen.Drafted(draftWith(true, true, true)))) {
            update(uiState(screen))
            compose.onNodeWithTag(ImportTags.FIELD).assertDoesNotExistCompat()
        }
    }

    private fun reachableTargets(screen: Screen): List<String> = when (screen) {
        is Screen.Input -> listOf(ImportTags.FIELD, ImportTags.FIELD_ERROR)
        is Screen.InProgress -> listOf(ImportTags.FIELD, ImportTags.STATUS, string(R.string.import_action_cancel))
        is Screen.Failed -> listOf(ImportTags.FIELD, ImportTags.ERROR, string(R.string.import_action_retry))
        is Screen.Declined -> listOf(
            ImportTags.DECLINE, ImportTags.EXPLAINER, string(R.string.import_action_retry),
            string(R.string.import_action_try_another), string(R.string.import_action_share),
        )
        is Screen.Drafted -> listOf(ImportTags.DRAFT_NOTICE, string(R.string.import_action_import_another))
    }

    private fun assertEveryTargetReachableAndUnclipped() {
        val states = listOf(
            Screen.Input(fieldError = true), Screen.InProgress(ImportPhase.FETCHING_PICTURE),
            Screen.Failed(Failure.CONNECTION, retryable = true), Screen.Declined(validated),
            Screen.Drafted(draftWith(title = false, lines = true, method = true)),
        )
        for (screen in states) {
            update(uiState(screen))
            val viewport = compose.onRoot().getUnclippedBoundsInRoot()
            for (target in reachableTargets(screen)) {
                val node = if (target.startsWith("import")) byTag(target) else compose.onNodeWithText(target)
                node.performScrollTo().assertIsDisplayed()
                val bounds = node.getUnclippedBoundsInRoot()
                assertTrue(
                    bounds.left >= viewport.left && bounds.right <= viewport.right && bounds.top >= viewport.top && bounds.bottom <= viewport.bottom,
                    "$target at $bounds outside $viewport on $screen",
                )
                val results = mutableListOf<TextLayoutResult>()
                val config = node.fetchSemanticsNode().config
                // Only messages are checked for truncation; a button label is clipped by its own button, never by the page.
                if (target != ImportTags.FIELD && target.startsWith("import") && config.contains(SemanticsActions.GetTextLayoutResult)) {
                    config[SemanticsActions.GetTextLayoutResult].action?.invoke(results)
                    assertTrue(results.none { it.hasVisualOverflow }, "$target is truncated on $screen: " + results.joinToString { "size=${it.size} lines=${it.lineCount} w=${it.didOverflowWidth} h=${it.didOverflowHeight} c=${it.layoutInput.constraints} maxLines=${it.layoutInput.maxLines}" })
                }
            }
        }
    }

    @Test
    @Config(qualifiers = "land")
    fun `controls stay reachable and unclipped in landscape`() {
        show(uiState(Screen.Input()))
        assertEveryTargetReachableAndUnclipped()
    }

    @Test
    fun `controls stay reachable and unclipped at 200 percent font scale`() {
        RuntimeEnvironment.setFontScale(2.0f)
        show(uiState(Screen.Input()))
        assertEveryTargetReachableAndUnclipped()
    }

    @Test
    fun `back handling is enabled only in progress failed and declined`() {
        show(uiState(Screen.Input()))
        for (screen in everyScreen) {
            update(uiState(screen))
            var enabled = false
            scenario!!.onActivity { enabled = it.onBackPressedDispatcher.hasEnabledCallbacks() }
            val expected = screen is Screen.InProgress || screen is Screen.Failed || screen is Screen.Declined
            assertEquals(expected, enabled, screen.toString())
        }
    }

    @Test
    fun `the app name is shown once`() {
        show(uiState(Screen.Input()))
        for (screen in everyScreen) {
            update(uiState(screen))
            compose.onAllNodesWithText(string(R.string.app_name)).assertCountEquals(1)
        }
    }

    // ---- T31: the real activity, its share intake and the follow-up flows ----

    private var controller: ActivityController<MainActivity>? = null
    private var routed: RoutedServer? = null

    private fun server(): RoutedServer = RoutedServer().also {
        routed = it
        ImportTestApplication.gateway = TestGateways.against(it.server)
    }

    private fun share(text: String?): Intent = Intent(Intent.ACTION_SEND).setType("text/plain").apply {
        if (text != null) putExtra(Intent.EXTRA_TEXT, text)
    }

    /**
     * Lets the main looper and a few frames run. `waitForIdle` can spin when other tests have run first in the
     * same JVM, so the activity-level tests settle by advancing frames instead.
     */
    private fun settle() {
        org.robolectric.shadows.ShadowLooper.idleMainLooper()
        repeat(3) { compose.mainClock.advanceTimeByFrame() }
    }

    private fun launch(intent: Intent): MainActivity {
        val built = Robolectric.buildActivity(MainActivity::class.java, intent).setup()
        controller = built
        settle()
        return built.get()
    }

    private fun fieldText() = compose.onNodeWithTag(ImportTags.FIELD)

    private fun noticeNodes() = compose.onAllNodesWithTag(ImportTags.NOTICE).fetchSemanticsNodes(atLeastOneRootRequired = false)

    private fun waitForText(tag: String, text: String) {
        compose.waitUntil(10_000) {
            compose.onAllNodesWithTag(tag).fetchSemanticsNodes(atLeastOneRootRequired = false).any { node ->
                node.config.getOrNull(SemanticsProperties.Text)?.joinToString("") { it.text } == text
            }
        }
    }

    private fun linkReceivedText(): String = string(R.string.import_notice_link_received)

    @Test
    @Config(application = ImportTestApplication::class)
    fun `a cold start share fills the field and makes no request`() {
        val s = server()
        launch(share("Try this ${s.absolute("/stew")} so good"))
        fieldText().assertTextContains(s.absolute("/stew"))
        waitForText(ImportTags.NOTICE, linkReceivedText())
        assertEquals(0, s.server.requestCount)
    }

    @Test
    @Config(application = ImportTestApplication::class)
    fun `a share through onNewIntent fills the field and makes no request`() {
        val s = server()
        launch(Intent(Intent.ACTION_MAIN))
        assertTrue(noticeNodes().isEmpty())
        controller!!.newIntent(share("Try this ${s.absolute("/stew")} so good"))
        settle()
        fieldText().assertTextContains(s.absolute("/stew"))
        waitForText(ImportTags.NOTICE, linkReceivedText())
        assertEquals(0, s.server.requestCount)
    }

    @Test
    @Config(application = ImportTestApplication::class)
    fun `import after a share imports exactly the shared url`() {
        val s = server()
        s.page("/stew", ImportFixtures.page("heuristic.html"))
        launch(share("Try this ${s.absolute("/stew")} so good"))
        compose.onNodeWithText(string(R.string.import_action_import)).performClick()
        waitForText(ImportTags.DRAFT_NOTICE, string(R.string.import_notice_recipe_found))
        assertEquals(1, s.requests("/stew"))
        assertEquals(1, s.totalRequests)
    }

    @Test
    @Config(application = ImportTestApplication::class)
    fun `a share with no link shows the no link message and makes no request`() {
        val s = server()
        launch(share("nothing to see here"))
        waitForText(ImportTags.NOTICE, string(R.string.import_notice_no_link))
        controller!!.newIntent(share(null))
        settle()
        waitForText(ImportTags.NOTICE, string(R.string.import_notice_no_link))
        assertEquals(0, s.server.requestCount)
    }

    @Test
    @Config(application = ImportTestApplication::class)
    fun `rotation keeps the state and does not replay the share`() {
        val s = server()
        launch(share(s.absolute("/stew")))
        waitForText(ImportTags.NOTICE, linkReceivedText())
        fieldText().performTextReplacement("edited by the user")
        controller!!.recreate()
        settle()
        fieldText().assertTextContains("edited by the user")
        assertEquals(0, s.server.requestCount)
    }

    @Test
    @Config(application = ImportTestApplication::class)
    fun `a launcher start shows no notice`() {
        server()
        launch(Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER))
        assertTrue(noticeNodes().isEmpty())
        fieldText().assertTextContains("")
    }

    @Test
    @Config(application = ImportTestApplication::class)
    fun `a start from history skips the share read`() {
        val s = server()
        launch(share(s.absolute("/stew")).addFlags(Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY))
        assertTrue(noticeNodes().isEmpty())
        assertTrue(compose.onAllNodesWithText(s.absolute("/stew")).fetchSemanticsNodes(atLeastOneRootRequired = false).isEmpty())
    }

    @Test
    @Config(application = ImportTestApplication::class)
    fun `a non send intent through onNewIntent changes nothing`() {
        val s = server()
        launch(Intent(Intent.ACTION_MAIN))
        controller!!.newIntent(Intent(Intent.ACTION_VIEW, Uri.parse(s.absolute("/stew"))).putExtra(Intent.EXTRA_TEXT, s.absolute("/stew")))
        settle()
        assertTrue(noticeNodes().isEmpty())
        assertEquals(0, s.server.requestCount)
    }

    @Test
    @Config(application = ImportTestApplication::class)
    fun `cancel against a silent server returns to input with the url`() {
        val s = server()
        s.route("/silent") { MockResponse().setSocketPolicy(SocketPolicy.NO_RESPONSE) }
        launch(share(s.absolute("/silent")))
        compose.onNodeWithText(string(R.string.import_action_import)).performClick()
        compose.waitUntil(10_000) { s.requests("/silent") >= 1 }
        compose.onNodeWithText(string(R.string.import_action_cancel)).performClick()
        settle()
        fieldText().assertTextContains(s.absolute("/silent"))
        compose.onNodeWithText(string(R.string.import_action_import)).assertIsDisplayed()
        compose.onAllNodesWithTag(ImportTags.ERROR).assertCountEquals(0)
        compose.onAllNodesWithTag(ImportTags.DRAFT_NOTICE).assertCountEquals(0)
        waitForText(ImportTags.NOTICE, string(R.string.import_notice_cancelled))
    }

    @Test
    @Config(application = ImportTestApplication::class)
    fun `the next import after decline or draft runs in the same activity instance`() {
        val s = server()
        s.page("/nothing", ImportFixtures.page("nothing-extracted.html"))
        s.page("/stew", ImportFixtures.page("heuristic.html"))
        val activity = launch(share(s.absolute("/nothing")))
        compose.onNodeWithText(string(R.string.import_action_import)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(ImportTags.DECLINE).fetchSemanticsNodes(false).isNotEmpty() }

        compose.onNodeWithText(string(R.string.import_action_try_another)).performClick()
        settle()
        fieldText().performTextInput(s.absolute("/stew"))
        compose.onNodeWithText(string(R.string.import_action_import)).performClick()
        waitForText(ImportTags.DRAFT_NOTICE, string(R.string.import_notice_recipe_found))
        assertSame(activity, controller!!.get())

        compose.onNodeWithText(string(R.string.import_action_import_another)).performClick()
        settle()
        fieldText().performTextInput(s.absolute("/nothing"))
        compose.onNodeWithText(string(R.string.import_action_import)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(ImportTags.DECLINE).fetchSemanticsNodes(false).isNotEmpty() }
        assertSame(activity, controller!!.get())
        assertEquals(2, s.requests("/nothing"))
        assertEquals(1, s.requests("/stew"))
    }

    @Test
    @Config(application = ImportTestApplication::class)
    fun `share link starts a chooser over the declined url`() {
        val s = server()
        s.page("/not-a-recipe", ImportFixtures.page("nothing-extracted.html"))
        val activity = launch(share(s.absolute("/not-a-recipe")))
        compose.onNodeWithText(string(R.string.import_action_import)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(ImportTags.DECLINE).fetchSemanticsNodes(false).isNotEmpty() }
        assertNull(shadowOf(activity).nextStartedActivity, "nothing starts before the tap")
        assertEquals(1, s.totalRequests)

        compose.onNodeWithText(string(R.string.import_action_share)).performClick()
        val started = shadowOf(activity).nextStartedActivity
        assertEquals(Intent.ACTION_CHOOSER, started.action)
        @Suppress("DEPRECATION")
        val inner = started.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)!!
        assertEquals(Intent.ACTION_SEND, inner.action)
        assertEquals("text/plain", inner.type)
        assertEquals(s.absolute("/not-a-recipe"), inner.getStringExtra(Intent.EXTRA_TEXT))
        assertEquals(1, s.totalRequests)
    }

    @Test
    @Config(application = ImportTestApplication::class)
    fun `the decline itself starts nothing and sends nothing`() {
        val s = server()
        s.page("/nothing", ImportFixtures.page("nothing-extracted.html"))
        val activity = launch(share(s.absolute("/nothing")))
        compose.onNodeWithText(string(R.string.import_action_import)).performClick()
        compose.waitUntil(10_000) { compose.onAllNodesWithTag(ImportTags.DECLINE).fetchSemanticsNodes(false).isNotEmpty() }
        assertNull(shadowOf(activity).nextStartedActivity)
        assertEquals(1, s.totalRequests)
        assertTrue(ImageFixtures.garbage().isNotEmpty())
    }
}

private fun <T> assertSame(expected: T, actual: T) = kotlin.test.assertSame(expected, actual)
private fun assertNull(actual: Any?, message: String? = null) = kotlin.test.assertNull(actual, message)

private fun androidx.compose.ui.test.SemanticsNodeInteraction.assertDoesNotExistCompat() {
    this.assertDoesNotExist()
}

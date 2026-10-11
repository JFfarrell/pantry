package ie.pantry.ui.recipeimport

import ie.pantry.data.gateway.GatewayException.Category
import ie.pantry.recipeimport.DraftImage
import ie.pantry.recipeimport.DraftValue
import ie.pantry.recipeimport.ExtractionTier
import ie.pantry.recipeimport.ImportDraft
import ie.pantry.recipeimport.ImportException
import ie.pantry.recipeimport.ImportException.Kind
import ie.pantry.recipeimport.ImportPhase
import ie.pantry.recipeimport.ImportResult
import ie.pantry.recipeimport.ImportUrl
import ie.pantry.recipeimport.NoImageCause
import ie.pantry.recipeimport.UrlCheck
import ie.pantry.recipeimport.ValidatedUrl
import ie.pantry.testutil.Sentinels
import java.time.Instant
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class ImportViewModelTest {

    private val dispatcher = StandardTestDispatcher()

    @Before
    fun setUp() = Dispatchers.setMain(dispatcher)

    @After
    fun tearDown() = Dispatchers.resetMain()

    private fun idle() = dispatcher.scheduler.runCurrent()

    private fun viewModel(pipeline: FakePipeline = FakePipeline()) = ImportViewModel(pipeline)

    private val ImportViewModel.screen get() = state.value.screen

    private fun ImportViewModel.type(text: String) = onFieldChange(text)

    private fun ImportViewModel.import(text: String) {
        onFieldChange(text)
        onImport()
        idle()
    }

    private fun url(text: String): ValidatedUrl = (ImportUrl.check(text) as UrlCheck.Valid).url

    private fun draft() = ImportDraft(
        title = DraftValue.Stated("Stew"),
        ingredientLines = listOf("1 onion"),
        method = DraftValue.Absent,
        cookingTime = DraftValue.Absent,
        servings = DraftValue.Absent,
        image = DraftImage.NoImage(NoImageCause.NO_CANDIDATE),
        tier = ExtractionTier.JSON_LD,
        sourceUrl = "https://example.ie/stew",
        fetchedAt = Instant.EPOCH,
    )

    private fun fetchFailed(category: Category, status: Int? = null) =
        ImportResult.Failed(ImportException(Kind.FETCH_FAILED, category, status, null))

    private fun failedKind(kind: Kind) = ImportResult.Failed(ImportException(kind, null, null, null))

    private val stew = "https://example.ie/stew"

    @Test
    fun `import with a padded url calls the pipeline once with the trimmed url`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.import("  $stew  ")
        assertEquals(listOf(stew), pipeline.urls)
        assertEquals(stew, vm.state.value.field)
        assertEquals(Screen.InProgress(ImportPhase.FETCHING_PAGE), vm.screen)
    }

    @Test
    fun `import with each rejected input sets the field error and calls nothing`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        val rejected = listOf(
            "ftp://example.ie/a", "intent://x#Intent;end", "content://x/y", "javascript:alert(1)",
            "www.example.ie/stew", "   ", "https://", "https://user:pw@example.ie/a",
        )
        for (input in rejected) {
            vm.import(input)
            assertEquals(Screen.Input(fieldError = true), vm.screen, input)
            assertEquals(input, vm.state.value.field, input)
            assertNull(vm.state.value.notice, input)
        }
        assertTrue(pipeline.calls.isEmpty())
    }

    @Test
    fun `import with no url match shows the invalid url error not the no link notice`() {
        val vm = viewModel()
        vm.import("just some words")
        assertEquals(Screen.Input(fieldError = true), vm.screen)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun `a successful import tap replaces the field with the picked url`() {
        val vm = viewModel()
        vm.import("Try this $stew so good")
        assertEquals(stew, vm.state.value.field)
        assertIs<Screen.InProgress>(vm.screen)
    }

    @Test
    fun `a second import tap while running starts no second import`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.import(stew)
        vm.onImport()
        vm.onImport()
        idle()
        assertEquals(1, pipeline.calls.size)
    }

    @Test
    fun `the picture phase is shown when the pipeline reports it`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.import(stew)
        pipeline.calls.single().onPhase(ImportPhase.FETCHING_PICTURE)
        assertEquals(Screen.InProgress(ImportPhase.FETCHING_PICTURE), vm.screen)
    }

    @Test
    fun `a drafted result shows the drafted screen holding the draft`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.import(stew)
        val result = draft()
        pipeline.complete(ImportResult.Drafted(result))
        idle()
        assertSame(result, assertIs<Screen.Drafted>(vm.screen).draft)
    }

    @Test
    fun `not recognised shows the declined screen with the url`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.import(stew)
        pipeline.complete(failedKind(Kind.NOT_RECOGNISED))
        idle()
        assertEquals(url(stew), assertIs<Screen.Declined>(vm.screen).url)
    }

    @Test
    fun `retry is offered exactly for timeout connection failure and 500 to 599`() {
        val cases = listOf(
            fetchFailed(Category.TIMEOUT) to true,
            fetchFailed(Category.CONNECTION_FAILED) to true,
            fetchFailed(Category.ADDRESS_REFUSED) to false,
            fetchFailed(Category.HTTP_STATUS, 401) to false,
            fetchFailed(Category.HTTP_STATUS, 403) to false,
            fetchFailed(Category.HTTP_STATUS, 404) to false,
            fetchFailed(Category.HTTP_STATUS, 499) to false,
            fetchFailed(Category.HTTP_STATUS, 500) to true,
            fetchFailed(Category.HTTP_STATUS, 599) to true,
            fetchFailed(Category.HTTP_STATUS, 600) to false,
            fetchFailed(Category.RESPONSE_TOO_LARGE) to false,
            failedKind(Kind.NOT_HTML) to false,
            failedKind(Kind.UNSUPPORTED_ENCODING) to false,
            failedKind(Kind.UNREADABLE_CONTENT) to false,
            failedKind(Kind.CONTENT_TOO_LARGE) to false,
        )
        for ((result, retryable) in cases) {
            val pipeline = FakePipeline()
            val vm = viewModel(pipeline)
            vm.import(stew)
            pipeline.complete(result)
            idle()
            val failed = assertIs<Screen.Failed>(vm.screen, result.toString())
            assertEquals(retryable, failed.retryable, result.error.toString())
        }
    }

    @Test
    fun `retry from failed imports the same url once more`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.import(stew)
        pipeline.complete(fetchFailed(Category.TIMEOUT))
        idle()
        vm.onRetry()
        idle()
        assertEquals(listOf(stew, stew), pipeline.urls)
        assertIs<Screen.InProgress>(vm.screen)
    }

    @Test
    fun `retry from declined imports the same url once more`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.import(stew)
        pipeline.complete(failedKind(Kind.NOT_RECOGNISED))
        idle()
        vm.onRetry()
        idle()
        assertEquals(listOf(stew, stew), pipeline.urls)
        assertIs<Screen.InProgress>(vm.screen)
    }

    @Test
    fun `retry is ignored when the failure is not retryable`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.import(stew)
        pipeline.complete(fetchFailed(Category.HTTP_STATUS, 404))
        idle()
        vm.onRetry()
        idle()
        assertEquals(1, pipeline.calls.size)
        assertIs<Screen.Failed>(vm.screen)
    }

    @Test
    fun `cancel returns to input at once with the url and the cancelled notice`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.import(stew)
        vm.onCancel()
        assertEquals(Screen.Input(), vm.screen)
        assertEquals(stew, vm.state.value.field)
        assertEquals(NoticeKind.IMPORT_CANCELLED, vm.state.value.notice?.kind)
        idle()
        assertEquals(Screen.Input(), vm.screen)
    }

    @Test
    fun `cancel during the picture phase cancels the whole import`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.import(stew)
        pipeline.calls.single().onPhase(ImportPhase.FETCHING_PICTURE)
        vm.onCancel()
        idle()
        assertEquals(Screen.Input(), vm.screen)
        assertEquals(NoticeKind.IMPORT_CANCELLED, vm.state.value.notice?.kind)
        assertTrue(pipeline.calls.single().ended)
    }

    @Test
    fun `a late result after cancel is discarded`() {
        val pipeline = FakePipeline(nonCancellable = true)
        val vm = viewModel(pipeline)
        vm.import(stew)
        vm.onCancel()
        assertEquals(Screen.Input(), vm.screen)
        pipeline.complete(ImportResult.Drafted(draft()))
        idle()
        assertEquals(Screen.Input(), vm.screen)
        assertEquals(NoticeKind.IMPORT_CANCELLED, vm.state.value.notice?.kind)
    }

    @Test
    fun `a new import waits for the cancelled job to end`() {
        val pipeline = FakePipeline(nonCancellable = true)
        val vm = viewModel(pipeline)
        vm.import(stew)
        vm.onCancel()
        vm.onImport()
        idle()
        assertEquals(1, pipeline.calls.size, "the second job must not start while the first is still running")
        assertIs<Screen.InProgress>(vm.screen)
        pipeline.complete(failedKind(Kind.NOT_HTML), pipeline.calls[0])
        idle()
        assertEquals(2, pipeline.calls.size)
        pipeline.complete(ImportResult.Drafted(draft()), pipeline.calls[1])
        idle()
        assertIs<Screen.Drafted>(vm.screen)
    }

    @Test
    fun `a cancel import cancel import chain serialises on the tail`() {
        val pipeline = FakePipeline(nonCancellable = true)
        val vm = viewModel(pipeline)
        vm.import(stew)
        vm.onCancel()
        vm.onImport()
        vm.onCancel()
        vm.onImport()
        idle()
        assertEquals(1, pipeline.calls.size)
        pipeline.complete(failedKind(Kind.NOT_HTML), pipeline.calls[0])
        idle()
        assertEquals(2, pipeline.calls.size, "only the first and the last job call the pipeline; the cancelled middle one never does")
        assertIs<Screen.InProgress>(vm.screen)
    }

    @Test
    fun `import from failed behaves as from input`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.import(stew)
        pipeline.complete(fetchFailed(Category.HTTP_STATUS, 404))
        idle()
        vm.onImport()
        idle()
        assertEquals(listOf(stew, stew), pipeline.urls)
        assertIs<Screen.InProgress>(vm.screen)
    }

    @Test
    fun `back is consumed in progress failed and declined only`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        assertFalse(vm.onBack())
        vm.import(stew)
        assertTrue(vm.onBack())
        assertEquals(Screen.Input(), vm.screen)
        assertEquals(NoticeKind.IMPORT_CANCELLED, vm.state.value.notice?.kind)
        assertEquals(stew, vm.state.value.field)

        vm.onImport()
        idle()
        pipeline.complete(fetchFailed(Category.HTTP_STATUS, 404))
        idle()
        assertTrue(vm.onBack())
        assertEquals(Screen.Input(), vm.screen)
        assertEquals(stew, vm.state.value.field)

        vm.onImport()
        idle()
        pipeline.complete(failedKind(Kind.NOT_RECOGNISED))
        idle()
        assertTrue(vm.onBack())
        assertEquals(Screen.Input(), vm.screen)
        assertEquals(stew, vm.state.value.field)

        vm.onImport()
        idle()
        pipeline.complete(ImportResult.Drafted(draft()))
        idle()
        assertFalse(vm.onBack())
        assertIs<Screen.Drafted>(vm.screen)
    }

    @Test
    fun `try another link clears the field and requests focus`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.import(stew)
        pipeline.complete(failedKind(Kind.NOT_RECOGNISED))
        idle()
        val before = vm.state.value.focusSeq
        vm.onTryAnother()
        assertEquals(Screen.Input(), vm.screen)
        assertEquals("", vm.state.value.field)
        assertEquals(before + 1, vm.state.value.focusSeq)
    }

    @Test
    fun `import another discards the draft clears the field and requests focus`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.import(stew)
        pipeline.complete(ImportResult.Drafted(draft()))
        idle()
        val before = vm.state.value.focusSeq
        vm.onImportAnother()
        assertEquals(Screen.Input(), vm.screen)
        assertEquals("", vm.state.value.field)
        assertEquals(before + 1, vm.state.value.focusSeq)
    }

    @Test
    fun `a field edit in failed returns to input and clears error and retry`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.import(stew)
        pipeline.complete(fetchFailed(Category.TIMEOUT))
        idle()
        vm.onFieldChange("https://example.ie/other  ")
        assertEquals(Screen.Input(), vm.screen)
        assertEquals("https://example.ie/other  ", vm.state.value.field)
        assertNull(vm.state.value.notice)
        vm.onRetry()
        idle()
        assertEquals(1, pipeline.calls.size, "Retry can never import a url the field no longer shows")
    }

    @Test
    fun `a field edit in input keeps the text verbatim and clears error and notice`() {
        val vm = viewModel()
        vm.import("nonsense")
        assertEquals(Screen.Input(fieldError = true), vm.screen)
        vm.onFieldChange("  https://example.ie/x  ")
        assertEquals(Screen.Input(fieldError = false), vm.screen)
        assertEquals("  https://example.ie/x  ", vm.state.value.field)
        assertNull(vm.state.value.notice)
    }

    @Test
    fun `ui state types print no url or draft content`() {
        val sentinelUrl = url(Sentinels.URL)
        val withDraft = draft().let {
            ImportDraft(
                DraftValue.Stated(Sentinels.TITLE), listOf(Sentinels.INGREDIENT), DraftValue.Absent, DraftValue.Absent,
                DraftValue.Absent, DraftImage.NoImage(NoImageCause.NO_CANDIDATE), ExtractionTier.JSON_LD, Sentinels.URL, Instant.EPOCH,
            )
        }
        val texts = listOf(
            Screen.Declined(sentinelUrl).toString(),
            Screen.Drafted(withDraft).toString(),
            ImportUiState(Screen.Declined(sentinelUrl), Sentinels.URL, null, 0).toString(),
            ImportUiState(Screen.Drafted(withDraft), Sentinels.TITLE, Notice(NoticeKind.NO_LINK, 1), 0).toString(),
        )
        for (text in texts) {
            assertFalse(text.contains("SENTINEL"), text)
            assertFalse(text.lowercase().contains("sentinel"), text)
        }
    }

    // ---- T24: share events and notices ----

    private val shared = "Try this $stew so good"

    private enum class Start { INPUT, IN_PROGRESS, FAILED, DECLINED, DRAFTED }

    private fun reach(start: Start, pipeline: FakePipeline): ImportViewModel {
        val vm = viewModel(pipeline)
        if (start == Start.INPUT) return vm
        vm.import(stew)
        when (start) {
            Start.FAILED -> pipeline.complete(fetchFailed(Category.TIMEOUT))
            Start.DECLINED -> pipeline.complete(failedKind(Kind.NOT_RECOGNISED))
            Start.DRAFTED -> pipeline.complete(ImportResult.Drafted(draft()))
            else -> Unit
        }
        idle()
        return vm
    }

    @Test
    fun `a link share in input fills the field and announces link received`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        vm.onShareReceived(shared)
        assertEquals(Screen.Input(), vm.screen)
        assertEquals(stew, vm.state.value.field)
        assertEquals(NoticeKind.LINK_RECEIVED, vm.state.value.notice?.kind)
        idle()
        assertTrue(pipeline.calls.isEmpty())
    }

    @Test
    fun `a link share in progress cancels the import and announces the combined notice`() {
        val pipeline = FakePipeline()
        val vm = reach(Start.IN_PROGRESS, pipeline)
        vm.onShareReceived("https://example.ie/other")
        assertEquals(Screen.Input(), vm.screen)
        assertEquals("https://example.ie/other", vm.state.value.field)
        assertEquals(NoticeKind.LINK_RECEIVED_IMPORT_CANCELLED, vm.state.value.notice?.kind)
        idle()
        assertTrue(pipeline.calls.single().ended)
        assertEquals(1, pipeline.calls.size)
        pipeline.complete(ImportResult.Drafted(draft()), pipeline.calls.single())
        idle()
        assertEquals(Screen.Input(), vm.screen)
    }

    @Test
    fun `a link share in failed clears the error and retry and returns to input`() {
        val pipeline = FakePipeline()
        val vm = reach(Start.FAILED, pipeline)
        assertTrue((vm.screen as Screen.Failed).retryable)
        vm.onShareReceived(shared)
        assertEquals(Screen.Input(), vm.screen)
        assertEquals(stew, vm.state.value.field)
        assertEquals(NoticeKind.LINK_RECEIVED, vm.state.value.notice?.kind)
        vm.onRetry()
        idle()
        assertEquals(1, pipeline.calls.size)
    }

    @Test
    fun `a link share in declined returns to input with the picked url`() {
        val vm = reach(Start.DECLINED, FakePipeline())
        vm.onShareReceived("https://example.ie/new")
        assertEquals(Screen.Input(), vm.screen)
        assertEquals("https://example.ie/new", vm.state.value.field)
        assertEquals(NoticeKind.LINK_RECEIVED, vm.state.value.notice?.kind)
    }

    @Test
    fun `a link share in drafted discards the draft and returns to input`() {
        val vm = reach(Start.DRAFTED, FakePipeline())
        vm.onShareReceived("https://example.ie/new")
        assertEquals(Screen.Input(), vm.screen)
        assertEquals("https://example.ie/new", vm.state.value.field)
        assertEquals(NoticeKind.LINK_RECEIVED, vm.state.value.notice?.kind)
    }

    @Test
    fun `a no link share in input shows the no link notice and keeps the field and error`() {
        val vm = viewModel()
        vm.import("nonsense")
        vm.onShareReceived("nothing to see")
        assertEquals(Screen.Input(fieldError = true), vm.screen)
        assertEquals("nonsense", vm.state.value.field)
        assertEquals(NoticeKind.NO_LINK, vm.state.value.notice?.kind)
    }

    @Test
    fun `a no link share in progress keeps the import running`() {
        val pipeline = FakePipeline()
        val vm = reach(Start.IN_PROGRESS, pipeline)
        vm.onShareReceived("nothing to see")
        assertEquals(Screen.InProgress(ImportPhase.FETCHING_PAGE), vm.screen)
        assertEquals(NoticeKind.NO_LINK, vm.state.value.notice?.kind)
        pipeline.complete(ImportResult.Drafted(draft()))
        idle()
        assertIs<Screen.Drafted>(vm.screen)
    }

    @Test
    fun `a no link share in failed keeps the error and retry`() {
        val pipeline = FakePipeline()
        val vm = reach(Start.FAILED, pipeline)
        val before = vm.screen
        vm.onShareReceived("nothing to see")
        assertEquals(before, vm.screen)
        assertEquals(stew, vm.state.value.field)
        assertEquals(NoticeKind.NO_LINK, vm.state.value.notice?.kind)
        vm.onRetry()
        idle()
        assertEquals(2, pipeline.calls.size)
    }

    @Test
    fun `a no link share in declined or drafted changes nothing but the notice`() {
        for (start in listOf(Start.DECLINED, Start.DRAFTED)) {
            val vm = reach(start, FakePipeline())
            val before = vm.state.value
            vm.onShareReceived("nothing to see")
            assertSame(before.screen, vm.screen, start.name)
            assertEquals(before.field, vm.state.value.field, start.name)
            assertEquals(NoticeKind.NO_LINK, vm.state.value.notice?.kind, start.name)
        }
    }

    @Test
    fun `a missing shared text is treated as no link`() {
        val vm = viewModel()
        vm.onShareReceived(null)
        assertEquals(NoticeKind.NO_LINK, vm.state.value.notice?.kind)
        assertEquals("", vm.state.value.field)
        assertEquals(Screen.Input(), vm.screen)
    }

    @Test
    fun `no share calls the pipeline`() {
        val pipeline = FakePipeline()
        for (start in Start.values()) {
            val vm = reach(start, pipeline)
            val before = pipeline.calls.size
            vm.onShareReceived(shared)
            vm.onShareReceived("no link")
            vm.onShareReceived(null)
            idle()
            assertEquals(before, pipeline.calls.size, start.name)
        }
    }

    @Test
    fun `an over limit or userinfo shared url fills the field and is rejected only at import`() {
        val pipeline = FakePipeline()
        val vm = viewModel(pipeline)
        val overLimit = "https://example.ie/" + "a".repeat(3000)
        vm.onShareReceived(overLimit)
        assertEquals(overLimit, vm.state.value.field)
        assertEquals(Screen.Input(fieldError = false), vm.screen)
        vm.onImport()
        assertEquals(Screen.Input(fieldError = true), vm.screen)

        val userinfo = "https://user:pw@example.ie/a"
        vm.onShareReceived("see $userinfo")
        assertEquals(userinfo, vm.state.value.field)
        assertEquals(Screen.Input(fieldError = false), vm.screen)
        vm.onImport()
        assertEquals(Screen.Input(fieldError = true), vm.screen)
        idle()
        assertTrue(pipeline.calls.isEmpty())
    }

    @Test
    fun `a repeated share bumps the notice sequence`() {
        val vm = viewModel()
        vm.onShareReceived(shared)
        val first = vm.state.value.notice!!
        vm.onShareReceived(shared)
        val second = vm.state.value.notice!!
        assertEquals(first.kind, second.kind)
        assertTrue(second.seq > first.seq)
        vm.onShareReceived("no link")
        val firstNoLink = vm.state.value.notice!!
        vm.onShareReceived("no link")
        assertTrue(vm.state.value.notice!!.seq > firstNoLink.seq)
    }

    @Test
    fun `the next event clears the notice`() {
        val vm = viewModel()
        vm.onShareReceived(shared)
        vm.onFieldChange("x")
        assertNull(vm.state.value.notice)

        vm.onShareReceived(shared)
        vm.onImport()
        idle()
        assertNull(vm.state.value.notice)
        assertIs<Screen.InProgress>(vm.screen)
    }
}

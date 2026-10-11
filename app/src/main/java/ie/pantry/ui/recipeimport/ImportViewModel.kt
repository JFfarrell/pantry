package ie.pantry.ui.recipeimport

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import ie.pantry.recipeimport.ImportPhase
import ie.pantry.recipeimport.ImportPipeline
import ie.pantry.recipeimport.ImportResult
import ie.pantry.recipeimport.ImportUrl
import ie.pantry.recipeimport.UrlCheck
import ie.pantry.recipeimport.ValidatedUrl
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * The import screen's state machine (FC8). Every method is main-thread only. One import job runs at a time: each
 * new job first waits, uncancellably, for the previous one to end, so a cancel-then-import never holds two large
 * parsed pages at once. A result is applied only while its generation is still the current one.
 */
class ImportViewModel(private val pipeline: ImportPipeline) : ViewModel() {

    private val _state = MutableStateFlow(ImportUiState(Screen.Input(), field = "", notice = null, focusSeq = 0))
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var importJob: Job? = null
    private var generation = 0
    private var lastUrl: ValidatedUrl? = null
    private var noticeSeq = 0

    fun onFieldChange(text: String) {
        val screen = _state.value.screen
        if (screen is Screen.Input || screen is Screen.Failed) {
            _state.update { it.copy(screen = Screen.Input(), field = text, notice = null) }
        }
    }

    fun onImport() {
        val screen = _state.value.screen
        if (screen !is Screen.Input && screen !is Screen.Failed) return
        when (val check = ImportUrl.check(_state.value.field)) {
            is UrlCheck.Invalid -> _state.update { it.copy(screen = Screen.Input(fieldError = true), notice = null) }
            is UrlCheck.Valid -> {
                _state.update { it.copy(field = check.url.value) }
                start(check.url)
            }
        }
    }

    fun onCancel() {
        if (_state.value.screen is Screen.InProgress) cancelToInput(NoticeKind.IMPORT_CANCELLED)
    }

    fun onRetry() {
        val screen = _state.value.screen
        val url = lastUrl ?: return
        if ((screen is Screen.Failed && screen.retryable) || screen is Screen.Declined) start(url)
    }

    fun onTryAnother() {
        if (_state.value.screen is Screen.Declined) toEmptyInput()
    }

    fun onImportAnother() {
        if (_state.value.screen is Screen.Drafted) toEmptyInput()
    }

    /**
     * A shared text. A text with a link always replaces the screen with Input holding the picked link (a running
     * import is cancelled and a draft or error is discarded) and starts no fetch; the link is only picked here and
     * validated when Import is tapped. A text with no link, or none, only sets the notice.
     */
    fun onShareReceived(text: String?) {
        val picked = text?.let(ImportUrl::pick)
        if (picked == null) {
            _state.update { it.copy(notice = notice(NoticeKind.NO_LINK)) }
            return
        }
        val running = _state.value.screen is Screen.InProgress
        if (running) {
            generation++
            importJob?.cancel()
        }
        val kind = if (running) NoticeKind.LINK_RECEIVED_IMPORT_CANCELLED else NoticeKind.LINK_RECEIVED
        _state.update { it.copy(screen = Screen.Input(), field = picked.text, notice = notice(kind)) }
    }

    /** @return true when Back was consumed (In progress, Failed, Declined). */
    fun onBack(): Boolean {
        when (val screen = _state.value.screen) {
            is Screen.InProgress -> cancelToInput(NoticeKind.IMPORT_CANCELLED)
            is Screen.Failed -> _state.update { it.copy(screen = Screen.Input(), notice = null) }
            is Screen.Declined -> _state.update { it.copy(screen = Screen.Input(), field = screen.url.value, notice = null) }
            is Screen.Input, is Screen.Drafted -> return false
        }
        return true
    }

    private fun toEmptyInput() {
        _state.update { it.copy(screen = Screen.Input(), field = "", notice = null, focusSeq = it.focusSeq + 1) }
    }

    private fun notice(kind: NoticeKind): Notice = Notice(kind, ++noticeSeq)

    /** Invalidates the running job's result, cancels it, and shows Input at once, before the job has finished. */
    private fun cancelToInput(kind: NoticeKind) {
        generation++
        importJob?.cancel()
        _state.update { it.copy(screen = Screen.Input(), notice = notice(kind)) }
    }

    private fun start(url: ValidatedUrl) {
        lastUrl = url
        val mine = ++generation
        val previous = importJob
        _state.update { it.copy(screen = Screen.InProgress(ImportPhase.FETCHING_PAGE), notice = null) }
        // ATOMIC: a job cancelled before it first runs still starts, so it still waits for its predecessor and the
        // chain of waits cannot be shortened by a cancel.
        importJob = viewModelScope.launch(start = CoroutineStart.ATOMIC) {
            if (previous != null) withContext(NonCancellable) { previous.join() }
            ensureActive()
            val result = pipeline.importRecipe(url) { phase ->
                if (mine == generation) {
                    _state.update { if (it.screen is Screen.InProgress) it.copy(screen = Screen.InProgress(phase)) else it }
                }
            }
            if (mine == generation) apply(result, url)
        }
    }

    private fun apply(result: ImportResult, url: ValidatedUrl) {
        val screen: Screen = when (result) {
            is ImportResult.Drafted -> Screen.Drafted(result.draft)
            is ImportResult.Failed -> when (val failure = failureOf(result.error)) {
                Failure.NOT_RECOGNISED -> Screen.Declined(url)
                else -> Screen.Failed(failure, isRetryable(result.error))
            }
        }
        _state.update { it.copy(screen = screen) }
    }
}

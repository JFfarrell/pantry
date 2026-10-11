package ie.pantry.ui.recipeimport

import ie.pantry.recipeimport.ImportPhase
import ie.pantry.recipeimport.ImportPipeline
import ie.pantry.recipeimport.ImportResult
import ie.pantry.recipeimport.ValidatedUrl
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.withContext

/**
 * Records each [ValidatedUrl] it is given and suspends on a [CompletableDeferred] per call, so a test decides when
 * and how each import ends. In [nonCancellable] mode a cancelled caller keeps waiting, like a parse that is still
 * finishing, so the ViewModel's ordering of overlapping jobs can be observed.
 */
class FakePipeline(private val nonCancellable: Boolean = false) : ImportPipeline {

    class Call(val url: ValidatedUrl, val onPhase: (ImportPhase) -> Unit) {
        val result = CompletableDeferred<ImportResult>()
        var ended = false
    }

    val calls = mutableListOf<Call>()

    val urls: List<String> get() = calls.map { it.url.value }

    override suspend fun importRecipe(url: ValidatedUrl, onPhase: (ImportPhase) -> Unit): ImportResult {
        val call = Call(url, onPhase)
        calls += call
        try {
            return if (nonCancellable) withContext(NonCancellable) { call.result.await() } else call.result.await()
        } finally {
            call.ended = true
        }
    }

    fun complete(result: ImportResult, call: Call = calls.last()) {
        call.result.complete(result)
    }
}

package ie.pantry.recipeimport

import ie.pantry.data.gateway.BodyEncoding
import ie.pantry.data.gateway.ExternalDataGateway
import ie.pantry.data.gateway.GatewayResult
import java.time.Clock
import java.time.Instant
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.withContext

/** Where an import is, so the screen can say what it is waiting for. */
enum class ImportPhase { FETCHING_PAGE, FETCHING_PICTURE }

/** The one thing the screen's ViewModel depends on: one import from a validated URL to a result. */
fun interface ImportPipeline {
    /**
     * Returns [ImportResult.Drafted] or [ImportResult.Failed]; never throws except [CancellationException]. [onPhase]
     * is called on the caller's context, `FETCHING_PAGE` first.
     */
    suspend fun importRecipe(url: ValidatedUrl, onPhase: (ImportPhase) -> Unit): ImportResult
}

/** Test hooks: [onParse] runs just before the page parse, [onStream] sees each capped stream (page, then image). */
internal class ImportSeams(val onParse: () -> Unit, val onStream: (CappedInputStream) -> Unit) {
    companion object {
        val PRODUCTION = ImportSeams(onParse = {}, onStream = {})
    }
}

/**
 * Runs one import: one page fetch, the media-type and encoding gates, a bounded parse, extraction, and (in a later
 * step) one picture fetch. Its only side effects are the two gateway calls: no file, no log, no state.
 */
class RecipeImporter internal constructor(
    internal val gateway: ExternalDataGateway,
    private val clock: Clock,
    private val parseDispatcher: CoroutineDispatcher,
    private val imageCheck: (ByteArray) -> DraftImage,
    private val seams: ImportSeams,
) : ImportPipeline {

    constructor(gateway: ExternalDataGateway, clock: Clock) :
        this(gateway, clock, Dispatchers.Default, ImageValidator()::validate, ImportSeams.PRODUCTION)

    override suspend fun importRecipe(url: ValidatedUrl, onPhase: (ImportPhase) -> Unit): ImportResult = try {
        run(url, onPhase)
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        ImportResult.Failed(importFailure(e))
    }

    private suspend fun run(url: ValidatedUrl, onPhase: (ImportPhase) -> Unit): ImportResult {
        onPhase(ImportPhase.FETCHING_PAGE)
        val body = when (val fetched = gateway.fetchPage(url.value)) {
            is GatewayResult.Failed -> return ImportResult.Failed(fetchFailure(fetched.error))
            is GatewayResult.Fetched -> fetched.body
        }
        val fetchedAt = clock.instant()
        if (!BoundedHtmlReader.isHtml(body.mediaType)) return failed(ImportException.Kind.NOT_HTML)
        if (body.encoding == BodyEncoding.OTHER) return failed(ImportException.Kind.UNSUPPORTED_ENCODING)

        // The parsed document goes out of scope when this block returns; only the extraction and candidate stay.
        val (extraction, candidate) = withContext(parseDispatcher) {
            val job = coroutineContext.job
            seams.onParse()
            val document = BoundedHtmlReader.parse(body, url.value, DECOMPRESSED_CAP_BYTES, job, seams.onStream)
            val extraction = RecipeExtractor.extract(document, job) ?: return@withContext null
            extraction to ImageCandidateSelector.select(document, extraction.jsonLd)
        } ?: return failed(ImportException.Kind.NOT_RECOGNISED)

        return ImportResult.Drafted(draft(url, extraction, imageFor(candidate, onPhase), fetchedAt))
    }

    /** The image phase. An image failure is never an error: it is an explicit no-image state on the draft. */
    private suspend fun imageFor(candidate: Candidate, onPhase: (ImportPhase) -> Unit): DraftImage = when (candidate) {
        is Candidate.Url -> fetchPicture(candidate.value, onPhase)
        Candidate.NotHttp -> DraftImage.NoImage(NoImageCause.NOT_HTTP)
        Candidate.BadUrl -> DraftImage.NoImage(NoImageCause.BAD_URL)
        Candidate.None -> DraftImage.NoImage(NoImageCause.NO_CANDIDATE)
    }

    private suspend fun fetchPicture(url: String, onPhase: (ImportPhase) -> Unit): DraftImage {
        onPhase(ImportPhase.FETCHING_PICTURE)
        val body = when (val fetched = gateway.fetchImage(url)) {
            is GatewayResult.Failed -> return DraftImage.NoImage(NoImageCause.FETCH_FAILED)
            is GatewayResult.Fetched -> fetched.body
        }
        if (body.encoding == BodyEncoding.OTHER) return DraftImage.NoImage(NoImageCause.UNREADABLE)
        return withContext(parseDispatcher) {
            val job = coroutineContext.job
            try {
                // The image path has no markup-density bound: a valid low-entropy image is never refused for its bytes.
                val stream = openBounded(body.bytes, body.encoding, DECOMPRESSED_CAP_BYTES, Long.MAX_VALUE, job)
                seams.onStream(stream)
                imageCheck(stream.use { it.readBytes() })
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                DraftImage.NoImage(NoImageCause.UNREADABLE)
            }
        }
    }

    private fun failed(kind: ImportException.Kind): ImportResult = ImportResult.Failed(importError(kind))

    private fun draft(url: ValidatedUrl, extraction: Extraction, image: DraftImage, fetchedAt: Instant): ImportDraft {
        val recipe = extraction.recipe
        return ImportDraft(
            title = recipe.title,
            ingredientLines = recipe.lines,
            method = if (recipe.steps.isEmpty()) DraftValue.Absent else DraftValue.Stated(recipe.steps),
            cookingTime = recipe.cookingTime,
            servings = recipe.servings,
            image = image,
            tier = extraction.tier,
            sourceUrl = url.value,
            fetchedAt = fetchedAt,
        )
    }
}

package ie.pantry.data.reference

import android.util.Log
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async

private const val LOG_TAG = "PantryRef"

/**
 * Lazy, off-main-thread, load-once access to the four reference datasets. Construction opens no asset.
 * Each dataset's load starts on its first access and is shared by every later caller, success or failure
 * alike, for the life of this store. The scope is never cancelled: a caller that is itself cancelled stops
 * only its own `await`, and the load still finishes and is cached (AD6).
 */
class ReferenceDataStore(
    source: AssetSource,
    ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
) {
    private val scope = CoroutineScope(SupervisorJob() + ioDispatcher)

    private val staplesDeferred: Deferred<LoadResult<StaplesTable>> =
        scope.async(start = CoroutineStart.LAZY) { loadAndLog(source, ReferenceDataset.STAPLES, DatasetParser::parseStaples) }
    private val aliasesDeferred: Deferred<LoadResult<AliasTable>> =
        scope.async(start = CoroutineStart.LAZY) { loadAndLog(source, ReferenceDataset.ALIASES, DatasetParser::parseAliases) }
    private val seasonalityDeferred: Deferred<LoadResult<SeasonalityTable>> =
        scope.async(start = CoroutineStart.LAZY) { loadAndLog(source, ReferenceDataset.SEASONALITY, DatasetParser::parseSeasonality) }
    private val sectionOrderDeferred: Deferred<LoadResult<SectionOrderTable>> =
        scope.async(start = CoroutineStart.LAZY) { loadAndLog(source, ReferenceDataset.SECTION_ORDER, DatasetParser::parseSectionOrder) }

    suspend fun staples(): LoadResult<StaplesTable> = staplesDeferred.await()
    suspend fun aliases(): LoadResult<AliasTable> = aliasesDeferred.await()
    suspend fun seasonality(): LoadResult<SeasonalityTable> = seasonalityDeferred.await()
    suspend fun sectionOrder(): LoadResult<SectionOrderTable> = sectionOrderDeferred.await()

    suspend fun stapleFor(key: String): LookupResult<StaplesEntry> = unwrap(staples()) { it.lookup(key) }
    suspend fun canonicalKeyForVariant(variant: String): LookupResult<String> = unwrap(aliases()) { it.lookup(variant) }
    suspend fun seasonalityFor(key: String): LookupResult<SeasonalityEntry> = unwrap(seasonality()) { it.lookup(key) }
    suspend fun sectionFor(key: String): LookupResult<SectionOrderEntry> = unwrap(sectionOrder()) { it.sectionFor(key) }

    private inline fun <T : Any, R> unwrap(result: LoadResult<T>, lookup: (T) -> Lookup<R>): LookupResult<R> =
        when (result) {
            is LoadResult.Ready -> lookup(result.table)
            is LoadFailed -> result
        }

    /** Calls [loadDataset], which never throws, and writes exactly one content-free log line on failure. */
    private fun <T : Any> loadAndLog(source: AssetSource, dataset: ReferenceDataset, parse: (ByteArray) -> LoadResult<T>): LoadResult<T> {
        val result = loadDataset(source, dataset, parse)
        if (result is LoadFailed) {
            Log.w(LOG_TAG, "dataset=${dataset.name} category=${result.failure.category.name}")
        }
        return result
    }
}

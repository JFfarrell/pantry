package ie.pantry.data.reference

import java.io.FileNotFoundException
import java.io.IOException
import kotlinx.coroutines.CancellationException

/**
 * Opens [dataset]'s asset through [source], reads it fully and hands the bytes to [parse]. The single catch
 * site for I/O and parse failures: nothing but a [LoadFailure] built from constants and positions ever
 * escapes, so no message or cause is kept, logged or chained. Blocking; call only off the main thread.
 */
internal fun <T : Any> loadDataset(source: AssetSource, dataset: ReferenceDataset, parse: (ByteArray) -> LoadResult<T>): LoadResult<T> =
    try {
        val bytes = source.open(dataset.assetPath).use { it.readBytes() }
        parse(bytes)
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: FileNotFoundException) {
        LoadFailed(LoadFailure(dataset, LoadFailure.Category.ASSET_ABSENT, null, null, null))
    } catch (_: IOException) {
        LoadFailed(LoadFailure(dataset, LoadFailure.Category.ASSET_UNREADABLE, null, null, null))
    } catch (_: Exception) {
        LoadFailed(LoadFailure(dataset, LoadFailure.Category.INTERNAL_ERROR, null, null, null))
    }

/** Namespacing object so tests can call `DatasetLoader.loadDataset(...)`. */
internal object DatasetLoader {
    fun <T : Any> loadDataset(source: AssetSource, dataset: ReferenceDataset, parse: (ByteArray) -> LoadResult<T>): LoadResult<T> =
        ie.pantry.data.reference.loadDataset(source, dataset, parse)
}

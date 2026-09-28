package ie.pantry.data.reference

/** Three-way result of a store lookup: [Lookup.Found], [Lookup.Absent] or [LoadFailed]. */
sealed interface LookupResult<out T>

/** Two-way result of querying a loaded table: found, or explicitly absent. A miss is never a default value. */
sealed interface Lookup<out T> : LookupResult<T> {
    data class Found<out T>(val value: T) : Lookup<T>

    data object Absent : Lookup<Nothing>
}

/** The outcome of loading one dataset: its table, or a [LoadFailed]. */
sealed interface LoadResult<out T> {
    data class Ready<out T>(val table: T) : LoadResult<T>
}

/** A dataset that could not be loaded. It is both a load result and a store-lookup result. */
data class LoadFailed(val failure: LoadFailure) : LoadResult<Nothing>, LookupResult<Nothing>

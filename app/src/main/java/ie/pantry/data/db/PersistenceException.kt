package ie.pantry.data.db

import android.database.sqlite.SQLiteConstraintException
import android.database.sqlite.SQLiteException
import android.util.Log
import java.io.IOException

/**
 * The persistence layer's own failure type. Its message is assembled only from an enum, a constant
 * operation name and a class simple-name, so no argument, entity field or cause message can reach it.
 * Build it only through [persistenceFailure].
 */
class PersistenceException(
    val category: Category,
    /** A compile-time constant operation name, e.g. `saveNewRecipe`. */
    val operation: String,
    /** The cause's class simple-name only, e.g. `SQLiteConstraintException`. */
    val causeType: String?,
) : RuntimeException("Persistence failure: category=$category operation=$operation cause=$causeType") {

    enum class Category { CONSTRAINT, DATABASE_OPEN, MIGRATION, IO, UNKNOWN }
}

private const val LOG_TAG = "PantryDb"

/**
 * Converts [cause] into a content-free [PersistenceException]. The cause is not chained (its message
 * may carry content); its stack frames are copied so the failure site is still traceable. Logs one
 * warning of `key=value` fields only.
 */
internal fun persistenceFailure(operation: String, cause: Throwable): PersistenceException {
    val category = when (cause) {
        is SQLiteConstraintException -> PersistenceException.Category.CONSTRAINT
        is SQLiteException, is IllegalStateException -> PersistenceException.Category.DATABASE_OPEN
        is IOException -> PersistenceException.Category.IO
        else -> PersistenceException.Category.UNKNOWN
    }
    val causeType = cause.javaClass.simpleName
    Log.w(LOG_TAG, "category=$category operation=$operation causeType=$causeType")
    return PersistenceException(category, operation, causeType).also { it.stackTrace = cause.stackTrace }
}

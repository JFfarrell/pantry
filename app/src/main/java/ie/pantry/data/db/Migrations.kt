package ie.pantry.data.db

import androidx.room.migration.Migration

/**
 * Every explicit schema migration, in order. Empty at version 1. Used by both database factories
 * and by the migration harness, so a migration cannot be registered in one place and forgotten in another.
 * See `docs/migrations.md` for the procedure.
 */
val ALL_MIGRATIONS: Array<Migration> = emptyArray()

# Database migrations

Pantry's Room database is versioned (`PantryDatabase.VERSION`) and its schema is exported as JSON
under `app/schemas/ie.pantry.data.db.PantryDatabase/<version>.json`. Data on a user's device must
survive every schema change, so every change follows this procedure.

1. Bump `PantryDatabase.VERSION`.
2. Build, and commit the new `app/schemas/.../<n>.json` without hand-editing any schema JSON.
3. Add an explicit `Migration(n-1, n)` to `ALL_MIGRATIONS` (in `Migrations.kt`). It may throw only
   `PersistenceException(MIGRATION, "migrate_<from>_<to>", ...)`, and it must never interpolate row
   data into SQL.
4. Extend the harness: seed any new columns in a new `FixtureVn`, and assert the migrated
   `FixtureV1` rows field by field in `MigrationHarnessTest`.
5. Run `./gradlew testDebugUnitTest`.
6. Never add `fallbackToDestructiveMigration*` (in any form). A missing migration must fail loudly on
   first access and must never wipe data.

## Rule for new recipe-backed queries

Every new query that reads recipe rows, or rows joined to them, must keep
`pendingDeletionAt IS NULL` in its `WHERE` clause, so a recipe pending deletion never appears in the
catalogue or in any list built from it.

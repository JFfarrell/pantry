## Machine findings

- [MED] T21 case "repository save after close throws PersistenceException" may not fail as asserted — Room/SQLiteOpenHelper can lazily reopen after `close()` (on-disk DB would just succeed), so the test could be unwritable or vacuous; task gives no fallback trigger (e.g. force failure via an already-closed underlying `openHelper.writableDatabase` or a downgraded file).
- [MED] T17 PNG-bomb test does not discriminate sampled vs native decode under native graphics — the 1g heap cap is not consumed by native bitmaps; the added `computeSampleSize == 32` test pins the helper only, not that `process` actually decodes with that `inSampleSize`; add an assertion on decoded intermediate size (e.g. injectable decode seam or Options capture).
- [MED] T20 tests cover only the undecodable-bytes replace failure; the replace WRITE_FAILED path, the old-file-delete-fails log (DR6, category-only) and `removeThumbnail` on a missing/absent path have no test despite the design's "happy + error test per public function" expectation.
- [MED] T26 "fresh clone" precondition and T2 README state only the JDK 17 prerequisite; a fresh clone also needs an Android SDK location (ANDROID_HOME or a git-ignored local.properties), so R1 AC1's clean-checkout claim is unverifiable from the documented prerequisites.
- [MED] T2 Verification does not check the "no `proguardFiles`" half of its own AC (R1 AC4); a `grep -c proguardFiles app/build.gradle.kts` printing 0 would close it.
- [MED] T1 depends on a local Gradle install or existing wrapper to generate the wrapper (flagged ASSUMPTION) with no stated fallback if neither exists (e.g. fetch the wrapper from a known Gradle distribution or ask the user to run `gradle wrapper`).
- [LOW] T25 `grep -cE '^[1-6]\. '` prints 6 only if no other numbered list in the doc uses 1-6; brittle counter, could scope to the procedure section.
- [LOW] T3 sdk=34 fallback (likely needed: Robolectric SDK 35 wants a newer JVM than 17) has no recorded trigger to log it in Implementation Deviations; a one-line note would keep T3/T17 verification honest.
- [LOW] T9 Parallel list names T11/T17/T18 but Implementation Order step 6 groups only T9/T10/T15/T25; harmless but inconsistent.

## Assessment (human)

Risk assessment. I read the whole artifact fresh, plus spec, design (both halves) and the CFC section of PLAN. The pass-1 edits hold up. The T3/T26 permission assertions match the corrected `ManifestPolicyTest` row: no `android.permission.*` entry, and androidx.core's `DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION` is tolerated. The T26 release-manifest grep matches the merged-manifest formatting (single-attribute `<uses-permission android:name=...>` lines). The T2/T4 assemble split is coherent, because `processDebugResources` does not validate class references while the full assemble waits for T3/T4. I found no dependency-graph errors: DAO tasks are serialised on the shared `PantryDatabase.kt`, T16 and T21 are serial on the hygiene test, and T22 needs T19.

CFC-4 compliance holds. F1 is a participant but is not named in the Enforcement prose, which names F4 and F15. T15, T16 and T21 are tagged [CFC-4], deliver `Sentinels`, `PersistenceException` and the hygiene test, and cover the design's six forced-failure cases. CFC-2 does not list F1, so nothing is owed there. No exposure-sequencing problem: the manifest ships with no permissions from T2, and the guard test lands in T3.

Spec traceability is complete. R3 AC7 (sweep) is in T11, R3 AC6 in T14, R4 AC5 across T10/T11/T12, R5 AC1-5 in T10/T11, R6 in T8/T9/T24/T25, R7 in T23 and R8 AC1-4 in T17-T21. No Boundaries "Never Do" is violated. The mutating-git rule is respected: T8 and T26 leave committing to the user.

Residual risks are all verification-strength issues, not wrong-as-written defects:
- **Post-close save (T21).** Room can reopen after `close()`, so the "save after close" case may not produce the asserted exception.
- **Bomb test (T17).** It is only weakly discriminating under native graphics, and the pinned sample-size test covers the helper, not `process`'s use of it.
- **Replace failure (T20).** The WRITE_FAILED replace path is untested.
- **Prerequisites.** The Android SDK location is not in the documented prerequisites.

Top 3 recommendations:
1. In T21, specify how the "closed" failure is forced so it cannot pass vacuously or be unwritable, for example by asserting the exception is thrown and by falling back to the downgraded-file case if reopen succeeds.
2. In T17, add a seam or assertion showing `process` decodes with the computed `inSampleSize`, for example by capturing the `BitmapFactory.Options` or asserting the intermediate decoded dimensions.
3. Add a README and T26 note for the Android SDK prerequisite, and a T20 test for replace WRITE_FAILED leaving the old thumbnail intact.

Sealed dispositions were not re-raised.

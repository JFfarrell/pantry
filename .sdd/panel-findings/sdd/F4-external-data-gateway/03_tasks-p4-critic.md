## Machine findings

- [MED] T13's acceptance criterion and test row still say page/image `requestUrl` equals the parsed supplied URL, but the test asserts `path` and `Host` — the same MockWebServer `requestUrl`-host limitation is ledgered only for T11, so the ledger is incomplete and T13 contradicts it.
- [MED] Accepted Divergences row 3 is scoped "Design / T11" although design's `GatewayRequestInspectionTest` row (R8 AC1: "`requestUrl` and the recorded `Host` equal the supplied URL") carries the same unsatisfiable assertion and T13 implements the workaround — the divergence record under-reports its reach.
- [MED] T10's description still says every test except the non-retryable table is red against T8's "uncancelled OkHttp call", and T8's description still says T10 adds `invokeOnCancellation`; the as-built T8 already has both (Implementation Deviations row 2), so the task bodies assert a code state that was never true.
- [LOW] T8's stalled-body assumption still says the 3 s fixture is "logged as a pending row in `## Implementation Deviations`", but that row is now `declined` and mirrored in Accepted Divergences — stale status wording.
- [LOW] T18's completion notes record "0 skipped"; T8's `[::1]` test is `Assume`-gated, so the figure is environment-specific (true in this run, verified against the surefire XML: 479 tests, 0 skipped, 0 failures) and not a property of the task.
- [LOW] T18's "lintDebug passes" is true (warnings only in `lint-results-debug.txt`) but the notes do not say the result is warnings-only, so a reader cannot tell a clean lint from a tolerated one.

## Assessment (human)

**Risk assessment.** I checked the ticks, ledgers and completion notes against the working tree rather than taking them at face value, and they are largely truthful. All 118 backtick-quoted test names that the task list prescribes exist verbatim in the test sources. The surefire XML shows 479 tests, 0 skipped and 0 failures, with results timestamped after the last main-source edit, so T18's headline figure holds. The release merged manifest, normalised as T18 specifies, is identical to `.toolchain/f4-baseline/release-AndroidManifest.xml`, and it holds exactly one `android.permission` entry. `base-commit.txt` equals HEAD (`61c1da7`, which contains F3), and `git status --porcelain` is empty for every protected path. The gradle, manifest and `AppContainer` diffs match T1, T15 and T16 as written. `git diff` against HEAD shows the only change to `03_tasks.md` content is the ticks, T18's completion notes and the two new ledgers, so nothing substantive was rewritten after approval.

The residual risk is in the ledgers' completeness, not in the work. The Accepted Divergences section records declined deviations "each with its rationale", and a later reader (F5, F16, or a template reuse) will treat that table as the full list of places where tasks and design diverge. It is missing one place. T13's AC and design's `GatewayRequestInspectionTest` row both assert on `requestUrl`, which the T11 row itself says cannot show the literal address under MockWebServer 4.12. The T13 tests quietly assert `path` plus `Host` instead. The coverage is fine, since request target and Host are what R8 AC1 actually needs. But the record makes it look as if only T11 diverged.

Because the task bodies were not touched after implementation, T8 and T10 still describe a split (cancellation arriving in T10, with a red test) that the ledger says did not happen. The ledger discloses this, so it is not misleading in aggregate. A reader of T10 alone would still be misled about what its cancellation test proved at the time.

**CFC check.** F4 participates only in CFC-4. Its Enforcement prose names F4 as owner of the typed-error surface and re-wrapping rule. T3 is tagged [CFC-4] and delivers exactly that artifact (`GatewayException`, `gatewayError`, `gatewayFailure` with KDoc rules (a) to (e)). T14 is tagged [CFC-4] and supplies the per-feature content-bearing-fixture tests. Nothing in the task list contradicts the CFC-4 Contract or Per-feature AC. Spec Never Do forbids CFC-1/2/3 tags and none appear. No CFC-related HIGH.

**Hidden assumptions.**
- The TDD Exceptions table records skips for T4, T5, T6, T12–T15 and T17. For T1–T3, T9–T11 and T16 the list is silent, which asserts a red step was observed. I cannot verify that from artifacts, and the ledger is the only evidence.
- Declining every deviation relies on the claim that MockWebServer 4.12 builds `requestUrl` from `localAddress.hostName`. The T11 test comment repeats it, and it matches my recollection of the 4.x source, but I did not re-derive it from the jar.

**Failure scenarios.** The realistic failure is a future reader treating Accepted Divergences as exhaustive, copying T13's `requestUrl`-equality assertion into an F5 or F16 test against `TestGateways`, and hitting the `localhost` rebuild. Mitigation: widen row 3's scope, or add a T13 row.

**Seals.** No seal looks wrong. SEAL-11 (answerEvery) is consistent with the shipped `answerEvery`, which overrides both `dispatch` and `peek`. SEAL-15 (the `[::1]` rule) is consistent with the test not being skipped in this run.

**Top 3 recommendations.**
1. Extend Accepted Divergences row 3 to name T13 and design's `GatewayRequestInspectionTest` row, or add a fourth row for T13's `requestUrl` to `path` + `Host` substitution (removes the only ledger omission).
2. Amend T10's "red against T8" sentence and T8's "T10 adds `invokeOnCancellation`" sentence with a pointer to Implementation Deviations row 2 (stops the task bodies asserting a state that was never true).
3. Update T8's "pending row" wording to "declined; see Accepted Divergences", and note in T18's completion notes that lint is warnings-only and that "0 skipped" is for the run where `::1` bound.

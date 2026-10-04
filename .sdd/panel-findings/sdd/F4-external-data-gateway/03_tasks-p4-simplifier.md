## Machine findings

- [MED] Task bodies for T8 and T10 still describe the T8/T10 split that implementation did not follow — T10's description says every test except the non-retryable table is red against T8's code and that T10 adds `invokeOnCancellation`, but `ExternalDataGateway.kt` registers `invokeOnCancellation` in T8's bridge and the cancellation test was green; only the ledger rows record this, so a ticked task reads false on its own
- [MED] `## Implementation Deviations` and `## Accepted Divergences` restate the same three rows almost verbatim — the second table adds only a "re-evaluate trigger" column, so the closeout carries duplicate prose that must be kept in sync
- [MED] T11's acceptance criterion still demands the second recorded `requestUrl` equal `http://127.0.0.1:<exemptPort>/recipe/1`, while T8's equivalent fixture change was amended inline; the shipped test asserts `path` and `Host` instead, so a ticked task carries an AC its test does not meet and the divergence is visible only in the ledger
- [LOW] T8's Tests list omits `a response without a content type has a null media type`, which exists in `GatewayFailureMappingTest.kt` (the file has 21 tests; the lists in T8, T9 and T11 sum to 20)
- [LOW] T14's Tests list names `sentinel errors carry no cause and no suppressed exceptions` (plural) while the code and T14's mutation check use `...no suppressed exception` (singular)
- [LOW] T7 says `trustingTestCert()` returns a plain `SSLSocketFactory` / `X509TrustManager` pair; the code returns a `TestTls` wrapper class, a harmless drift from the description
- [LOW] Four `## TDD Exceptions` rows (T4, T5, T6, T15) carry identical boilerplate and the table is out of task order; one grouped row would say the same thing
- [LOW] T17's second acceptance criterion enumerates every Kotlin-`internal` declaration excluded from the reflection check, which is a long exclusion list for a test that inspects a fixed allow-list of eleven classes

## Assessment (human)

**Verdict.** The task list is internally consistent and truthful in every claim I could check. I found no HIGH.

**What I verified against the code and the build output**
- T18's completion notes say "479 tests, 0 failures, 0 skipped". The test-result XML under `app/build/test-results/testDebugUnitTest` sums to exactly 479 tests, 0 skipped, 0 failures, 0 errors.
- The `[DEF-18]` `::1` case did not skip. The `Assume.assumeTrue(boundOk)` rule exists in `GatewayTargetValidationTest.kt`, and 0 skipped means `::1` bound.
- Per-class `@Test` counts reconcile with the task lists:
  - ConnectionTargetPolicyTest: T2 lists 12, matches.
  - GatewayErrorHygieneTest: T3's 14 plus T14's 3 gives 17, matches.
  - GatewayTransportTest: T5's 8 plus T6's 7 gives 15, matches.
  - GatewayRequestInspectionTest: T4's 4 plus T13's 6 gives 10, matches.
  - GatewayRetryTest: T10's 7 plus T11's 2 gives 9, matches.
  - GatewayTargetValidationTest: T8's 5, T11's 10 and T12's 4 give 19, matches.
  - SingleCallSiteTest: T17's 6, matches.
  - GatewayFailureMappingTest has one extra test beyond the lists (see the LOW finding).
- Every production and test file the tasks name exists. The two baseline files under `.toolchain/f4-baseline` exist.
- `git status` shows changes only in the expected places, which is consistent with T18's "protected paths unchanged".
- The modelled artefacts match the code: `BodyEncoding.of`, `TestGateways.against`/`throwingInterceptor`/`answerEvery` (overriding both `dispatch` and `peek`), and `invokeOnCancellation` in the hop bridge.
- No `TestGateways` identifier contains `SENTINEL`.
- The T8 stalled-body fixture (`throttleBody(1024, 3, SECONDS)`) and the T11 `path`/`Host` assertion are both in the code as the ledger describes.

**CFC-4.**
- PLAN CFC-4's Enforcement names F4 as owner of the typed-error surface and its re-wrapping rule.
- T3 is `[CFC-4]`-tagged and delivers exactly that artifact: `GatewayException`, `gatewayFailure`, and KDoc stating rules (a)–(e).
- T14 is also tagged.
- Nothing in the tasks contradicts CFC-4's Contract or Per-feature AC.
- F4 is not named in any other CFC, and the spec's Never Do correctly forbids tagging CFC-1, CFC-2 or CFC-3.

**Simplifier observations (none blocking)**
- The main complexity cost is documentary. Implementation Deviations, Accepted Divergences and the Phase-4 completion notes record the same three events three times.
- The ticked T8, T10 and T11 bodies were left describing the plan, not what shipped. Fixing this properly would mean editing ticked task text, which is arguably worse than keeping the ledger as the single place divergences live. I therefore rate it MED, not HIGH: the ledgers do disclose each divergence.
- The cheapest improvement is a one-line pointer in the affected task bodies ("see Accepted Divergences row N"). Alternatively, merge the two ledger tables.
- I raise no new structural removals. The Sealed dispositions already cover T4's separateness, T11's size, T8's size, the mutation checks and the duplicate greps.
- I am not contesting any seal.

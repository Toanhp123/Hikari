# Adaptive Reader Continuity / HES-v1 Final Checkpoint

Date: 2026-08-25
Updated: 2026-08-26 (developer-host verification)

Status: **M0–M7 VERIFIED/CLOSED; HES-v1 FROZEN; M7.1 READY/UNBLOCKED**

Follow-up: **M7.1 DETEKT DEBT CLOSURE IS THE NEXT QUALITY MINI-PHASE; WAVE 10 FINAL HOST ACCEPTANCE REMAINS OPEN UNTIL DETEKT + THE UNCHANGED COMBINED GATE ARE GREEN**

## Scope

M7 implements Tasks 31–34 from
`../../superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md` on top of the accepted
M6 boundary. This checkpoint is deliberately evidence-driven. Initial sandbox-only evidence is retained,
but the status below is rebased to the fresh developer-host Gradle output supplied on 2026-08-26.
Commands are recorded from the strongest available evidence. The focused/broad host gates and
instrumentation compile below come from supplied developer-host output. Final API 26/API 37 completion is
recorded from the developer's 2026-08-26 confirmation; the raw device-model/test-count transcript was not
attached to this closure update, so no counts are invented here. The repository-wide Detekt cleanup exposed
by the broad host gate is intentionally split into M7.1; this checkpoint does not suppress, baseline, or
relabel those findings as passing.

The final source tree preserves the HES-v1 constitutional boundary:

- 17 production modules plus the `:benchmark` android-test module;
- Room schema 11, with `MIGRATION_10_11` still registered and no `MIGRATION_11_12`;
- JVM-only `:reader:engine` with exact production dependency `:core:common`;
- `:reader` consumes `:reader:engine` with `implementation`, never `api`;
- no production HES engine imports outside `:reader` / `:reader:engine`;
- no `:reader -> :settings` dependency;
- `ReaderPreferencesPort` and `ReaderSourceAvailability` ownership remain unchanged;
- production `sourceGroupKey` remains absent/null by design and production completeness remains
  10,000 basis points;
- source health remains process-memory state only.

## Task 31 — G01–G26 and L1–L6 freeze

Added pure HES verification suites:

- `ReaderGoldenScenariosTest` — exact G01–G26 registry, focused pure goldens, and explicit mapping
  for runtime-owned scenarios;
- `ReaderPermutationPropertyTest` — 1,000 seeded routing inputs, replay equality, permutation
  equality, route invariants, explicit-selection properties, OPEN/local behavior, and stable tie
  ordering;
- `ReaderMetamorphicTest` — disabled-candidate, reliability monotonicity, rejected-candidate
  permutation, hysteresis, unavailable-path removal, and unrelated historical-fingerprint
  transformations;
- `ReaderGoldenRuntimeEvidenceTest` — compile/runtime link from every runtime-owned golden ID to the
  existing focused test class/method, preventing a stale string-only evidence registry;
- `ReaderRouteEngineDifferentialTest` — the final L6 overlap envelope is retained as a test-only
  explicit-selection oracle after removal of the production legacy comparator.

The standalone Kotlin compiler harness in this sandbox executed all 15 engine test classes and
reported **95 PASS / 0 FAIL**. The M7-specific subset is:

```text
ReaderGoldenScenariosTest             13 PASS
ReaderPermutationPropertyTest          4 PASS
ReaderMetamorphicTest                   6 PASS
ReaderRouteEngineStressTest             3 PASS
M7 SUBSET                              26 PASS / 0 FAIL
FULL ENGINE                            95 PASS / 0 FAIL
```

A second compiler harness compiled 27 Reader production-core Kotlin files after the legacy cutover
and executed the non-virtual-time executor/mapper/differential target suites with **13 PASS / 0 FAIL**.
That harness substitutes `runBlocking` for `runTest` only for tests that do not depend on virtual
time. Both harnesses are supplementary evidence only; the canonical repository gate remains Gradle
`:reader:engine:test` plus `:reader:testDebugUnitTest`.

## Task 32 — deterministic scale and complexity contracts

Added:

- `ReaderRouteEngineStressTest` with 50 sources, 500 releases, 1,000 replans, bounded health history,
  bounded route shape, deterministic replay/permutation sampling, and no all-pairs trace materialization;
- `ReaderRuntimeStressTest` covering rapid generations/hard replans, multiple sessions, process-shared
  health with isolated execution state, serialized per-source remote lanes, and repeated
  foreground/prefetch cancellation.

The scale review found one real asymptotic leak in language evaluation: each candidate performed a
linear `languageOrder.indexOf(...)` / membership scan. `ReaderRoutingPolicy` now materializes one
normalized language-rank map per policy, and both eligibility and score evaluation use O(1) lookups.
The public HES policy API and decision semantics are unchanged.

## Task 33 — legacy release-selection retirement

Production Feature Reader was already exclusively session-routed through `ReaderRouteSessionFactory`
and `ReaderRouteSession`. M7 therefore removed the now-unreferenced second production ranking path:

- deleted `ReleaseSelector`, `ReleaseSelectionPolicy`, `ReleaseSelectionResult`, `ReleaseCandidate`,
  `ReleaseHealth`, and `SelectionReason` production contracts;
- deleted `ReaderDocumentRepository` and its Hilt provider;
- deleted `LegacyReaderRoutingAdapter` after moving the only production chapter-to-engine mapping to
  `ReaderRoutingCandidateMapper`;
- removed `ReaderRouteExecutor.executeCompatibility` and changed HES execution to carry the owning
  `ChapterRelease` directly;
- retained `ReaderLoadFailure` as the public Reader failure surface and kept `ReaderLoadResult`
  internal to `:reader`;
- deleted migration/selector compatibility tests that no longer represent a production surface,
  while retaining the final L6 differential oracle in test scope.

Final production source scan contains no legacy Reader ranking/repository symbols.

## Task 34 — constitutional/governance freeze

Strengthened:

- `ModuleGraphTest` with final HES module/dependency/persistence/import assertions;
- `ArchitectureSmokeTest` with the session-only production Reader and legacy-selector-deletion contract;
- `verify-current-architecture.sh` with exact HES module counts, JVM/exact engine dependency,
  `implementation` visibility, no Reader->Settings edge, Room-11 freeze, retained 10->11 migration,
  and no 11->12 migration;
- `verify-package-boundaries.sh` with a production-source ban on the retired Reader ranking/repository
  contracts;
- both shell verifier contract suites with negative mutation cases.

## Verification ledger

| Evidence | Result | Notes |
|---|---|---|
| Full engine Kotlin compiler harness | **PASS** | 95/95 engine tests; M7 subset 26/26 |
| Reader core compiler/target harness | **PASS** | 27 production-core files compile; 13/13 executor/mapper/differential tests |
| Focused M7 engine Gradle suite | **PASS** | developer host; `BUILD SUCCESSFUL in 30s` |
| Focused M7 Reader/runtime Gradle suite | **PASS** | developer host; `BUILD SUCCESSFUL in 48s` |
| Reader + Feature + Downloads + App + build-logic regression suite | **PASS** | developer host; `BUILD SUCCESSFUL in 1m 47s` |
| `:app:compileDebugKotlin` | **PASS** | developer host; `BUILD SUCCESSFUL in 8s`; two unrelated compiler warnings remain non-blocking |
| `./gradlew verifyArchitecture --no-daemon` | **PASS** | developer host; 18 modules verified |
| package/current-architecture mutation contracts | **PASS** | developer host; 17 production + 1 android-test; Room 1..11 |
| performance lifecycle policy | **PASS** | developer host |
| Wave 10 production policy | **PASS** | developer host |
| `./gradlew test testDebugUnitTest lintDebug detekt :app:assembleDebug --no-daemon` | **FAIL** | developer host; build stops at `:detekt` with `Analysis failed with 74 issues` |
| standalone `lintDebug` + `:app:assembleDebug` without Detekt | **PASS** | developer host; `BUILD SUCCESSFUL in 2m 42s` with `test` + `testDebugUnitTest` |
| instrumentation compile | **PASS** | developer host; `:storage:room:compileDebugAndroidTestKotlin` + `:app:compileDebugAndroidTestKotlin`; `BUILD SUCCESSFUL in 9s` |
| exploratory connected aggregate on Redmi Note 9S / API 35 | **PASS AFTER TEST-ONLY REPAIR** | developer-confirmed rerun after selecting MyAnimeList by plugin id; diagnostic only, not a substitute for boundary devices |
| API 26 connected Reader/cache/Room/Wave-10 matrix | **PASS** | developer-confirmed final boundary run; exact device model/test count not reproduced in this checkpoint |
| API 37 connected Reader/cache/Room/Wave-10 matrix | **PASS** | developer-confirmed final boundary run; exact device model/test count not reproduced in this checkpoint |

The broad host failure is a real acceptance failure, not an environment blocker. Review against the
pre-M7 tree showed the blocking Detekt set is repository debt rather than a behavioral regression that
should be refactored opportunistically inside the HES freeze patch. The cleanup is therefore assigned to
**M7.1 — Detekt Debt Closure**, a separate docs/quality phase. M7.1 must restore repository-wide Detekt
GREEN without adding suppressions or expanding a Detekt baseline, then rerun the original combined host
acceptance command before Wave 10 can close.

### API 35 verification-harness defect found during final device probing

The 2026-08-26 connected aggregate on the existing Redmi Note 9S / API 35 device reached the app
instrumentation suite and exposed one deterministic test defect:

```text
MyAnimeListCatalogContractIntegrationTest.bundledPluginLoadsAndExecutesVnextCatalogOperation
-> IllegalArgumentException: List has more than one element
-> runtime.enabled(PluginService.CATALOG).single()
```

This is not an HES/Reader behavior regression. The same pre-M7 tree already bundles both
`org.openstory.catalog.myanimelist` and `org.openstory.catalog.mangaupdates` as `CATALOG` providers,
while `PluginRuntime.enabled(CATALOG)` is defined to return every enabled provider for that service. The
contract test incorrectly assumed the service had exactly one provider. The verification-harness repair is
test-only: select exactly the MyAnimeList entry by `PluginId` and keep the production runtime unchanged.
The repaired contract and connected aggregate were subsequently developer-confirmed GREEN. The API 35
result remains diagnostic only; M7 closure is based on the required API 26/API 37 boundary runs below.

## Golden status

The exact G01–G26 names are frozen. Both the focused pure-engine suite and the focused Reader/runtime
suite now pass through the canonical Gradle tasks on the developer host. This supersedes the earlier
sandbox-only limitation for the M7 functional golden boundary.

## Deferred mini-phase M7.1 — Detekt Debt Closure

M7.1 is intentionally **not** part of the HES behavior/architecture patch. It owns only the repository
quality debt exposed by the 2026-08-26 developer-host run:

```text
./gradlew detekt --no-daemon
-> FAIL: Analysis failed with 74 issues
```

M7.1 rules:

- no HES behavior change unless a Detekt-safe refactor requires it and existing tests prove equivalence;
- no blanket `@Suppress`, config weakening, or Detekt-baseline growth merely to make the task green;
- fix findings in small responsibility-preserving groups and rerun affected focused tests after each group;
- finish with standalone `detekt` GREEN and then rerun the repository's original full host acceptance command;
- keep API 26/API 37 work separate from this static-quality mini-phase.

M7.1 is now **READY/UNBLOCKED** and should be implemented/reviewed as its own patch/commit.

## Wave 10 acceptance-rebase status

R0 selected the explicit acceptance-rebase path. On the final HES tree, developer-host evidence is now
GREEN for the focused M7 suites, Reader/Feature/Downloads/App/build-logic regression suite,
`:app:compileDebugKotlin`, standalone test/lint/assembly, instrumentation compile, `verifyArchitecture`,
package/current-architecture mutation contracts, performance lifecycle policy, and Wave 10 production policy.

The original Wave 10 combined host acceptance gate is **not green** because `:detekt` fails with 74
repository-wide issues. Detekt cleanup is deferred to M7.1, after which the **original combined gate must
be rerun unchanged**; this carve-out does not silently weaken Wave 10 acceptance. The required API 26 and
API 37 connected acceptance matrix is now developer-confirmed GREEN. Therefore **Wave 10 remains FINAL
ACCEPTANCE OPEN only on M7.1 Detekt cleanup + the unchanged combined host rerun**, and **Wave 11 remains
blocked** until that host boundary closes.

## Closure decision

M7 Tasks 31–34 are implementation-complete and the required functional, architecture, lint/assembly,
instrumentation-compile, and API 26/API 37 boundary verification is now green from the available developer
evidence. The pre-M7 MyAnimeList device-harness defect was repaired test-only and the rerun was
developer-confirmed GREEN. Detekt debt is explicitly moved to M7.1 rather than repaired in this HES freeze
patch.

Therefore **M7 is VERIFIED/CLOSED and HES-v1 is frozen at this boundary**. M7.1 is **READY/UNBLOCKED** as
the next quality-debt mini-phase. This M7 closure does not close Wave 10: Wave 10 still requires M7.1 to
restore standalone Detekt GREEN followed by a fresh run of the original unchanged combined host gate.

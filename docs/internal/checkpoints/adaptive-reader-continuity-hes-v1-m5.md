# Adaptive Reader Continuity / HES-v1 — M5 Checkpoint

Date: 2026-08-25
Status: **VERIFIED / CLOSED**
Next: **M6 READY / UNBLOCKED** at the HES milestone boundary. Wave 10 final host/API 26/API 37 acceptance remains independently open.

## Scope

M5 implements Tasks 25–26 from the rebased HES-v1 plan without enabling M6 hedging:

- Feature Reader uses one `ReaderRouteSession` for the ViewModel lifetime;
- exactly one persisted `ReaderPreferencesPort.preferences` collection and one `ChapterRepository.observe(storyId)` collection feed initial/session facts;
- initial foreground load waits for both first emissions;
- committed chapter/release/document, saved keys and reading-progress ownership remain authoritative while another target loads;
- target failure is non-destructive and can retry the failed target; rapid/stale completions cannot replace newer committed state;
- the visible Reader prefers an existing committed document over the initial-loading branch, removing transition blanking;
- reactive graph emissions preserve the legacy tombstone exclusion and refresh committed previous/next navigation without a full per-navigation snapshot;
- N+1 prefetch is session-owned, uses the same HES route engine with `RoutingIntent.PREFETCH`, and never enters the visible foreground commit gate;
- proactive remote prefetch is allowed only on `UNMETERED`; LOCAL prefetch remains eligible on every network class;
- network/source/health facts are revalidated immediately before every prefetch REMOTE effect;
- process-wide Reader remote-prefetch concurrency is one, still under the per-source limiter, and same-source foreground work preempts prefetch;
- graph changes that remove/reorder N+1 or change its release set cancel/replace stale prefetch ownership;
- valid persistable remote prefetch uses existing Reader store rules; non-persistable image documents do not create reusable local-cache bytes;
- foreground navigation after prefetch always creates a fresh FOREGROUND snapshot;
- hedging remains disabled until M6.

Room remains schema 11. M5 adds no entity, DAO, index or migration and does not touch `MIGRATION_10_11`.

## Plan/design contradictions found and resolved

1. The original Task-26 file sketch named `RoutingIntent.PREFETCH` but did not include the production seams that actually make intent affect eligibility/execution. M5 now carries the intent through snapshot assembly, pure eligibility and remote execution priority instead of duplicating a second selector/policy in the effect layer.
2. `RoomChapterRepository.observe(storyId)` can emit tombstoned canonical history, while the replaced snapshot projection filtered tombstones. The reactive Feature Reader boundary now keeps that semantic equivalence so tombstones cannot become previous/next/prefetch targets.
3. A committed screen could receive a later graph emission while `ReaderUiState.previousChapterId/nextChapterId` remained frozen at the commit-time graph. The ViewModel now stores only the active chapter-ID order projection and refreshes navigation reactively without reloading the document.
4. An early M5 implementation used a ViewModel-local monotonic request counter in addition to the session generation. That duplicated identity semantics forbidden by the R2 constitution. It was removed; the session remains the generation/plan-revision authority and the ViewModel uses target-object ownership only to ignore stale local callbacks.
5. Prefetch assembled under `UNMETERED` could otherwise start a later REMOTE fallback after network state became METERED. REMOTE prefetch now revalidates network state immediately before every remote effect.
6. When the committed chapter disappeared from a graph, `index == -1` could accidentally wrap prefetch selection to the first group. Missing committed identity now cancels prefetch.
7. Same canonical N+1 with a changed release set originally looked like the same target by chapter ID alone. Session ownership now includes the actual target group so the obsolete job is cancelled/replaced.
8. The coordinator KDoc still described prefetch as disabled after M5 wiring. Documentation was corrected to the M5/M6 boundary.

## TDD / behavioral evidence

The sandbox cannot execute the Gradle test tasks because Gradle 9.5.0 is not cached and outbound resolution of `services.gradle.org` is unavailable. M5 therefore used test-first source changes plus executable Kotlin harnesses against freshly type-checked production jars. Important regressions were observed RED before their fixes:

```text
reactive tombstone navigation RED:
  tombstoned chapter leaked into navigation: chapter-2

reactive committed navigation RED:
  reactive navigation stayed stale: chapter-2

post-fix behavioral harnesses:
  M5_VM_CONTINUITY_HARNESS_GREEN zeroBlank progressOwnership savedCommit staleGeneration observeOnce
  M5_TOMBSTONE_GREEN
  M5_REACTIVE_NAV_GREEN
  NETWORK_REVALIDATION_GREEN [release-a]
  SAME_TARGET_GRAPH_GREEN [release-a, release-b1, release-b2]
  M5_LIMITER_HARNESS_GREEN maxPrefetch=1 preemption=typed-non-penalizing
```

Fresh Kotlin type-checks on the final production sources are GREEN for:

```text
:reader:engine production source
M5 :reader routing production source
ReaderViewModel + ReaderUiState against lifecycle/DI stubs and the freshly compiled routing jars
```

These checks are useful sandbox evidence, not a substitute for the repository Gradle gate below.

## Static architecture/performance evidence

Fresh final-tree checks:

```text
bash scripts/tests/performance-lifecycle-policy-test.sh
  -> Performance lifecycle policy verified.

bash scripts/tests/verify-package-boundaries-test.sh
  -> verify-package-boundaries.sh contract verified.
bash scripts/verify-package-boundaries.sh
  -> Package boundary policy verified.

bash scripts/tests/verify-current-architecture-test.sh
  -> verify-current-architecture.sh contract verified.
bash scripts/verify-current-architecture.sh
  -> Current architecture verified: 17 production modules, 1 android-test module, Room schema 1..11.
```

## Developer-host Gradle gate — GREEN / CLOSURE EVIDENCE

The repository owner applied the M5 implementation plus the host-only test-fixture compile fix on branch `feature/adaptive-reader-continuity` and ran the required matrix with Gradle 9.5.0 available. The focused gates are GREEN:

```bash
./gradlew :reader:engine:test \
  --tests '*EligibilityEvaluatorTest*' \
  --no-daemon

./gradlew :reader:testDebugUnitTest \
  --tests '*PrefetchCoordinatorTest*' \
  --tests '*ReaderSourceExecutionLimiterTest*' \
  --no-daemon

./gradlew :feature:reader:testDebugUnitTest \
  --tests '*ReaderViewModelContinuityTest*' \
  --tests '*ReaderViewModelTest*' \
  --no-daemon

./gradlew :reader:engine:test \
  :reader:testDebugUnitTest \
  :feature:reader:testDebugUnitTest \
  :downloads:testDebugUnitTest \
  :app:testDebugUnitTest \
  :app:compileDebugKotlin \
  verifyArchitecture \
  --no-daemon

bash scripts/tests/performance-lifecycle-policy-test.sh
bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/tests/verify-current-architecture-test.sh
bash scripts/verify-current-architecture.sh
```

Developer-host results:

```text
EligibilityEvaluatorTest focused gate:
  BUILD SUCCESSFUL in 6s
  6 actionable tasks: 6 up-to-date

PrefetchCoordinatorTest + ReaderSourceExecutionLimiterTest focused gate:
  BUILD SUCCESSFUL in 27s
  61 actionable tasks: 3 executed, 58 up-to-date

ReaderViewModelContinuityTest + ReaderViewModelTest focused gate:
  BUILD SUCCESSFUL in 28s
  154 actionable tasks: 5 executed, 149 up-to-date

Full Reader/Feature/Downloads/App/architecture regression gate:
  BUILD SUCCESSFUL in 1m 22s
  299 actionable tasks: 23 executed, 4 from cache, 272 up-to-date
  verifyApplicationIdentity -> app.openstory
  verifyModuleBoundaries -> 18 modules

Static policy/architecture gates:
  Performance lifecycle policy verified.
  verify-package-boundaries.sh contract verified.
  Package boundary policy verified.
  verify-current-architecture.sh contract verified.
  Current architecture verified: 17 production modules, 1 android-test module, Room schema 1..11.
```

The only host-only regression discovered before the GREEN rerun was a Kotlin test-source namespace collision: the new M5 tests introduced top-level fixtures named `RecordingStore` and `NoOpReaderDocumentStore` in packages that already contained types with those names. Kotlin reported redeclarations and then cascaded misleading member/constructor errors in neighboring tests. The fix was test-only and renamed the M5 fixtures to `PrefetchRecordingStore` and `ContinuityNoOpReaderDocumentStore`; production code was unchanged.

The remaining warning in `ReaderScreenshotTest.kt` is a pre-existing Java deprecation warning for `Resources.updateConfiguration`; it does not fail compilation or an M5 acceptance gate.

No connected Room/device gate is added by M5 because no Room behavior/schema changes. The independently open Wave 10 API 26/API 37 matrix remains governed by its own acceptance-rebase.

## Closure

M5 Tasks 25–26 are **VERIFIED/CLOSED** on the 17-production-module, Room-schema-11 HES tree. The developer-host focused tests, broad Gradle regression/compile gate, `verifyArchitecture`, and all M5 static policy/architecture gates are GREEN after the test-only fixture namespace fix. **M6 is READY / UNBLOCKED** at the HES milestone boundary. Hedged foreground execution remains disabled until M6 implements its owning contracts. Wave 10 final host/API 26/API 37 acceptance remains independently open, so this HES milestone closure does not close Wave 10 or unblock Wave 11.

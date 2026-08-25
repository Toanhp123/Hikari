# Adaptive Reader Continuity / HES-v1 — M1 + M2 Compatibility Boundary Checkpoint

**Date:** 2026-08-25
**Status:** **VERIFIED / CLOSED**
**Scope:** HES-v1 M1 Tasks 6–8 and M2 Tasks 9–11.
**Next:** M3 — Typed Observations, Validation, and Process Health, Tasks 12–16.
**Wave 10 relationship:** HES continues under the explicit Wave 10 acceptance-rebase. This checkpoint does not close the still-open Wave 10 final host/API 26/API 37 acceptance matrix.

## Accepted M1 Boundary

M1 closes the legacy-compatible pure reasoning envelope without enabling adaptive routing behavior.

Accepted facts:

- `:reader` owns `LegacyReaderRoutingAdapter`; engine DTOs remain internal to Reader implementation boundaries.
- Production legacy mapping keeps `sourceGroupKey = null` and `completeness = BasisPoints(10_000)` until trusted production facts exist.
- `ReaderRouteEngine.v1()` provides deterministic compatibility planning over the representable legacy envelope only.
- Compatibility planning canonicalizes/stabilizes candidate order and emits deterministic REMOTE-only attempts for M1 fixtures.
- M1 does not activate local-locator ranking, source-operation health, network eligibility, hysteresis, prefetch, hedging, or process health behavior owned by later milestones.
- Differential coverage compares the legacy `ReleaseSelector` with the compatibility engine across 250 seeded candidate sets, including explicit release, target resume, source/group continuity fixtures, language order, fixture completeness, publication recency, and stable source/release tie-breaks.
- The legacy selector remains available; M1 proves the overlap envelope before later milestones intentionally diverge.

## Accepted M2 Boundary

M2 closes the effect-layer session/coordinator compatibility boundary while deliberately preserving the current production Reader facade.

Accepted facts:

- One `ReaderRouteSession` owns one `StoryId`, a unique `ReaderSessionId`, foreground `ReaderGenerationId`, engine `ReaderPlanRevision`, chapter-graph revision, latest chapter graph, routing preferences, committed identity, and semantic execution state.
- Every foreground user intent starts a new generation. Hard invalidation of the same active uncommitted intent increments only the plan revision.
- Initial execution waits for both the first chapter-graph fact and the first routing-preference fact rather than synthesizing a failure from missing startup state.
- `ReaderForegroundIntent` carries only the real `CanonicalChapterId` target plus optional explicit release. Routing preferences remain session-owned and are updated through `updateRoutingPreferences(...)`.
- `ReaderRouteExecutor` owns the extracted compatibility local-first/sequential attempt loop while `ReaderDocumentRepository.load(ReaderLoadRequest)` remains the legacy selector/facade owner.
- Compatibility extraction preserves legacy cache/source attempt order, cancellation propagation, lazy source enumeration, persistence behavior, and `ReaderLoadFailure` surface. M3 owns later local-I/O/quarantine semantic corrections.
- `ReaderRouteCoordinator`, `RouteSnapshotAssembler`, `ReaderRouteSessionFactory`, and DI provide a real-target session API without cutting Feature Reader over to it yet.
- Graph/preference updates in M2 record new facts only; they do not self-classify M4 hard/soft invalidations.
- Session completion uses one atomic stale/replan/commit gate so a plan-revision change cannot interleave between a stale check and semantic commit, and cancellation closes the current generation even after a revision advance.
- No process-global active generation, process health registry, hysteresis, prefetch, hedge, or adaptive access ranking is activated in M2.

## Developer-Host Verification Evidence

All commands below were reported from branch `feature/adaptive-reader-continuity` using Gradle 9.5.0 after applying M1 and M2.

### M1 targeted Reader compatibility tests

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*LegacyReaderRoutingAdapterTest*' \
  --tests '*ReleaseSelectorTest*' \
  --tests '*ReaderRouteEngineDifferentialTest*' \
  --no-daemon
```

Result:

```text
BUILD SUCCESSFUL in 29s
65 actionable tasks: 9 executed, 56 up-to-date
Configuration cache entry stored.
```

### M1 pure engine and architecture gates

```bash
./gradlew :reader:engine:test --no-daemon
./gradlew verifyArchitecture --no-daemon
bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/verify-current-architecture.sh
```

Result:

```text
:reader:engine:test — BUILD SUCCESSFUL in 23s
verifyArchitecture — BUILD SUCCESSFUL in 19s
Application identity verified as app.openstory.
Module architecture verified for 18 modules.
verify-package-boundaries.sh contract verified.
Package boundary policy verified.
Current architecture verified: 17 production modules, 1 android-test module, Room schema 1..11.
```

### M2 targeted session/executor/coordinator tests

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderRouteSessionStateTest*' \
  --tests '*ReaderRouteExecutorCompatibilityTest*' \
  --tests '*ReaderRouteCoordinatorCompatibilityTest*' \
  --tests '*ReaderDocumentRepositoryTest*' \
  --no-daemon
```

Result:

```text
BUILD SUCCESSFUL in 32s
65 actionable tasks: 6 executed, 59 up-to-date
Configuration cache entry stored.
```

That successful run exposed one non-failing test-only warning: `runCurrent()` requires `ExperimentalCoroutinesApi` opt-in. The M1/M2 closeout patch adds the same explicit class-level opt-in already used by other coroutine-test suites in this repository; no production code is changed by that cleanup.

### M2 Reader / Feature Reader / app / engine regressions

```bash
./gradlew :reader:testDebugUnitTest \
  :feature:reader:testDebugUnitTest \
  --no-daemon

./gradlew :app:compileDebugKotlin --no-daemon
./gradlew :reader:engine:test --no-daemon
./gradlew verifyArchitecture --no-daemon

bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/verify-current-architecture.sh
```

Result:

```text
Reader + Feature Reader unit suites — BUILD SUCCESSFUL in 1m 9s
163 actionable tasks: 10 executed, 153 up-to-date

:app:compileDebugKotlin — BUILD SUCCESSFUL in 1m
142 actionable tasks: 8 executed, 134 up-to-date

:reader:engine:test — BUILD SUCCESSFUL in 7s
6 actionable tasks: 6 up-to-date

verifyArchitecture — BUILD SUCCESSFUL in 20s
Application identity verified as app.openstory.
Module architecture verified for 18 modules.

verify-package-boundaries.sh contract verified.
Package boundary policy verified.
Current architecture verified: 17 production modules, 1 android-test module, Room schema 1..11.
```

## M1 Task Closure

### Task 6 — Reader-internal legacy adapter

**CLOSED.** Legacy Reader facts map to pure engine facts without exposing engine DTOs downstream, and production mapping stays on the locked source-group/completeness defaults.

### Task 7 — Deterministic compatibility planner

**CLOSED.** The pure compatibility planner is deterministic, stable under input reversal/replay, and restricted to the representable legacy REMOTE compatibility envelope.

### Task 8 — Differential legacy overlap

**CLOSED.** Legacy selector and engine overlap behavior is locked by deterministic differential fixtures/generation before adaptive divergence begins.

## M2 Task Closure

### Task 9 — Session and execution identities

**CLOSED.** Session/generation/revision ownership, initial-fact waiting, semantic states, cancellation closure, and same-generation hard-replan semantics are implemented and tested.

### Task 10 — Compatibility executor extraction

**CLOSED.** The legacy sequential/local-first execution loop is reusable behind `ReaderRouteExecutor` while the legacy repository facade and behavior remain intact.

### Task 11 — Real-target coordinator/session API

**CLOSED.** The explicit real-target coordinator, snapshot assembler, session factory, and DI wiring exist behind a non-production-cutover boundary. Feature Reader cutover remains owned by M5.

## Self-Review / Contradiction Closure

The M1/M2 implementation and plan were reread against the R2 design and current source tree before this checkpoint. The following gaps were corrected before closure:

1. M1 initially treated `SOURCE_UNAVAILABLE` as if M4 access eligibility already existed. M1 now stays inside its REMOTE compatibility envelope and does not own later availability policy.
2. M1 trace continuity initially risked labeling source/group continuity as an incumbent before hysteresis exists. Incumbent semantics remain limited to facts representable at this milestone.
3. Task 11's example intent still carried `ReaderPreferences` even though Task 9 and the design assign preferences to the session. The plan example is corrected to target plus optional explicit release only.
4. M2 graph/preference updates do not automatically trigger hard replans; hard/soft invalidation classification remains M4 ownership.
5. Cancellation after a plan-revision advance closes the actual current generation/revision instead of leaving the session in `Planning`.
6. Completion stale-check, replan decision, and semantic commit are one atomic state transition so revision invalidation cannot interleave between validation and commit.
7. A test that required committed-source continuity to influence the next target was removed from M2 because production consumption of committed continuity belongs to M4.
8. No engine DTO leaks into `:app` or Feature Reader transport contracts, no additional module is introduced after M0, and Room remains schema 11.
9. The only M2 host compiler warning was a missing coroutine-test experimental opt-in; the closeout patch adds the repository-standard explicit annotation.

No known M1/M2 contradiction remains between the R2 design, implementation plan, verified source boundary, and host verification evidence.

## Commit Consolidation

The implementation plan lists a logical commit after each Task 6–11. Those individual commits were intentionally not made because the repository owner requested one combined M1+M2 commit after both milestones were complete. This checkpoint records the completed task/test boundaries without falsely claiming those individual commit commands were executed.

## Remaining External Boundary

Wave 10 final acceptance remains **OPEN**. The explicit acceptance-rebase still requires the complete Wave 10 host and API 26/API 37 matrix to be rerun on the HES-containing final tree before Wave 10 itself may be marked accepted/closed. M1/M2 verification does not substitute for that matrix.

## Decision

**M1 and M2 are VERIFIED/CLOSED.** After applying the closeout patch and making the requested combined commit, the repository may proceed to **M3 Tasks 12–16** without enabling M4+ adaptive Reader behavior early.

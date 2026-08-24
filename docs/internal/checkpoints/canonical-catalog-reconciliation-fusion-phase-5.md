# Canonical Catalog Reconciliation & Fusion — Phase 5 Checkpoint

Date: 2026-08-22
Status: **VERIFIED — PHASE 5 CLOSED**
Scope: implementation-plan Tasks 33–35; Room schema 9 remains current.

Normative design: `../../superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md`
Implementation plan: `../../superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md`
Prior accepted evidence: `canonical-catalog-reconciliation-fusion-phase-4.md`

## Represented implementation boundary

### Task 33 — one durable review-resolution service

- `ReconciliationReviewService` is the application-level command/result boundary for `MERGE`, `KEEP_SEPARATE`, `DEFER`, stale-case rejection, invariant blocking, domain-state review, and protected-content-mapping resolution.
- User-approved `MERGE` uses the existing Phase-4 `StoryMergeExecutor`; no second destructive merge route was introduced.
- `KEEP_SEPARATE` is durable and idempotent. `DEFER` leaves the case `PENDING` and monotonically advances only `contextualPromptSuppressedUntilEpochMillis` for that case revision.
- Protected mapping resolution is restricted to candidates produced by the domain merge result; free-form source IDs are not accepted.

### Task 34 — Review Queue and exact-case navigation

- The queue is derived from durable pending reconciliation cases plus local canonical projections and a read-only meaningful-user-state footprint.
- Ranking is presentation-only and deterministic: confidence, meaningful user-state impact, evidence recency, pending age, then stable `caseId`.
- Merge/keep-separate/defer actions delegate to Task 33. Invariant-blocked and non-resolvable domain conflicts remain explanatory and non-destructive.
- `AppRoute.ReconciliationReview(caseId: String? = null)` supports both the queue and exact-case handoff. `Review duplicates` is exposed from the utility sheet and does not become a top-level navigation destination.
- Queue title/cover projection is local-only; no Details/network fallback was added to repair missing presentation metadata.

### Task 35 — contextual Story review over the same case

- Story observes reconciliation cases by canonical `StoryId` and carries the durable `caseId + caseRevision` into UI state.
- Contextual eligibility uses a feature-owned confidence threshold plus persisted suppression; low-confidence cases remain queue-only.
- `DEFER` suppresses contextual prompting without removing the queue item and can reappear after expiry while the Story remains open. `KEEP_SEPARATE` disappears through durable case resolution.
- Invariant-blocked prompts are explanatory with no Merge action. Protected mapping conflicts hand off to the shared exact-case review destination rather than creating a second conflict-resolution model.
- `StoryScreen` remains orchestration-focused; prompt layout/spacing is hosted by `StoryReconciliationPrompt.kt`.

## Verification evidence

Task-33 acceptance had already been recorded green for its focused service test, feature/app compilation, 20/20 selected Room connected tests on Redmi Note 9S - 15, and canonical `./scripts/verify.sh`.

Tasks 34–35 then supplied the following developer-checkout evidence during Phase-5 acceptance:

```text
:app:testDebugUnitTest
  GREEN

:app:compileDebugAndroidTestKotlin
  GREEN

AppNavigationTest
  10/10 tests PASS

RoomStoryGraphMergeCoordinatorTest
  11/11 tests PASS
```

The combined Story/review feature run isolated two failures to `ReconciliationReviewViewModelTest`; Story-side tests were otherwise green. Repeated scheduler/state hypotheses were rejected. Boundary diagnostics eventually exposed the real test-fixture failure:

```text
java.lang.NoSuchMethodError: 'java.lang.Object java.util.List.removeFirst()'
  at app.openstory.catalog.ui.review.SequenceMergeExecutor.execute(...)
```

The test fixture had compiled `MutableList.removeFirst()` to the Java-21 `java.util.List.removeFirst()` API while the Gradle unit-test worker runs Java 17. Replacing the fixture call with `removeAt(0)` removed that runtime incompatibility without changing production reconciliation behavior. The focused review suite was then reported green:

```text
ReconciliationReviewViewModelTest
  6/6 tests PASS
```

The final feature compile was supplied as:

```text
./gradlew :feature:catalog:compileDebugKotlin
  BUILD SUCCESSFUL
```

The first final repository verify run then exposed only Phase-5 structural/style blockers: `StoryScreen.kt` exceeded the orchestration import gate, followed by one Review-screen magic number and two max-line-length Detekt errors. These were removed by moving prompt host layout into `StoryReconciliationPrompt.kt`, naming the presentation threshold, and formatting the long calls; no suppression or threshold relaxation was added. The developer then reported the canonical gate green on the resulting tree:

```text
./scripts/verify.sh
  GREEN
```

No Room migration was added; schema 9 remains current. Phase 5 is accepted and closed. Phase 6 may begin at Task 36 (evidence-change events and the durable engine-work orchestrator). Task 37 remains the owner of operation-level Full metadata fallback.

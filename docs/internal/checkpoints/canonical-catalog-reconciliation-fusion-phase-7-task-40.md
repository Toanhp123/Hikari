# Canonical Catalog Reconciliation & Fusion — Phase 7 Task 40 Checkpoint

Date: 2026-08-22
Status: **VERIFIED — TASK 40 CLOSED; PHASE 7 REMAINS OPEN**
Scope: implementation-plan Task 40 only; Room schema 9 remains current.

Normative design: `../../superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md`
Implementation plan: `../../superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md`
Prior Task-39 evidence: `canonical-catalog-reconciliation-fusion-phase-7-task-39.md`

## Accepted implementation boundary

### Controlled reversal planning

- `StoryMergeReversalPlanner` and `StoryMergeReversalExecutor` are separate domain boundaries. Review/UI can ask whether a historical merge is reversible before any write occurs.
- Reversal remains intentionally fail-closed. It is available only when schema-9 merge/reversal audit history proves the historical source sides and every restorable/user-owned domain still corresponds to a safe split.
- Exact correction `caseId`/revision and survivor identity revision are revalidated. Stale review or stale identity plans do not mutate the graph.
- Identity evidence may legitimately change after merge because Task 38 contradictory evidence is what creates a correction review. The complete post-merge fingerprint therefore remains audit evidence rather than the sole reversal gate.
- Source ownership/preference, Library, mapping/rejection, chapter/release partition plus overrides/sync, and reading progress must still be provably restorable. New/unowned sources, protected-target ambiguity, user edits, cross-side chapter reassociation, unsafe progress drift, nested redirect lineage, degraded/parked canonical state, unsupported future policy, or an audit already requiring review all block automatic reversal.

### Atomic split and audit

- The Room reversal coordinator performs one all-or-nothing split transaction on schema 9; no migration or full-database snapshot was added.
- The transaction resurrects the retired Story, restores provable historical ownership/state, removes only the exact forward redirect, detaches both active canonical generations, and advances identity/preference revisions monotonically rather than rolling them back to historical numbers.
- The originating correction case is resolved to user-owned `RESOLVED_SEPARATE` inside the same transaction, closing the process-death window between graph split and review resolution.
- Exactly one `story_merge_reversal_events` row is written per merge. Repeated reversal is deterministic/idempotent.
- Both restored Stories are marked dirty for `FUSION_REBUILD` and `RECONCILIATION_REEVALUATION` in the same transaction so neither side can remain on the merged canonical generation.

### Review UX boundary

- Post-merge correction reviews use the full Review Queue. When planner assessment is reversible, the user is offered **Reverse safely**; otherwise the review exposes blockers/manual-review state.
- Correction reviews do not present `Keep separate` as a fake undo, and they are hidden from the contextual Story prompt surface because that surface has no reversal affordance.
- Normal pre-merge duplicate reviews retain their existing Merge / Keep separate / Later flow.

## Verification evidence

The developer checkout passed the focused Task-40 gates on 2026-08-22:

```text
:catalog:testDebugUnitTest
  ReconciliationReviewServiceTest
  BUILD SUCCESSFUL

:library:testDebugUnitTest
  LibraryStoryMergePolicyTest
  ContentMappingStoryMergePolicyTest
  BUILD SUCCESSFUL

:chapters:testDebugUnitTest
  ChapterStoryMergePolicyTest
  BUILD SUCCESSFUL

:reader:testDebugUnitTest
  ReadingProgressMergePolicyTest
  BUILD SUCCESSFUL

:feature:catalog:testDebugUnitTest
  ReconciliationReviewViewModelTest
  ReconciliationReviewScreenTest
  BUILD SUCCESSFUL

:storage:room:connectedDebugAndroidTest
  RoomStoryMergeReversalCoordinatorTest
  17/17 tests PASS
  device: Redmi Note 9S - 15
  BUILD SUCCESSFUL
```

The canonical repository gate was then rerun after the final Task-40 Detekt-only cleanup and reported green on the developer checkout:

```text
./scripts/verify.sh
  canonical/source/package/architecture policies PASS
  Detekt PASS after Task-40 lint cleanup
  module-boundary/lint verification PASS
  Room schema remains 1..9 / current schema 9
  BUILD SUCCESSFUL
```

No Detekt suppression or threshold relaxation was added for Task 40. Structural-review size/length warnings remain review candidates rather than hard-policy failures.

## Verification-fix history

The first Room instrumentation compile exposed two test-only defects: the reversal audit lookup used `catalogDao()` instead of `canonicalCatalogDao()`, and a mixed `execSQL` bind array needed an explicit `Any?` type. After that compile fix, two tests exposed an invalid fixture value `LibraryStatus.PLANNED`; the fixture was corrected to the real `WANT_TO_READ` enum without changing production parsing.

The next connected run reached reversal semantics and showed one fixture had made Story B the deterministic forward-merge survivor while the test intended B to be the retired Story. Pinning A's primary source balanced the user-state footprint and made A the deterministic survivor through the existing age tie-break; production survivor selection and reversal behavior were not changed. The final connected run then passed all 17 reversal tests.

The subsequent canonical gate found eight Task-40 Detekt errors: seven line-length findings and one complex condition in `ChapterStoryMergePolicy`. They were removed by formatting and behavior-preserving boolean extraction only; the reversal semantics were unchanged.

## Accepted residual boundary

Task 40 does not attempt a universal historical unmerge. Ambiguous protected-mapping resolution, nested redirect lineage, post-merge user/domain mutation, unsupported future policy, parked/degraded canonical state, or any state that cannot be proven lossless remains `REQUIRES_REVIEW_TO_REVERSE` / manual repair territory. Task 40 also does not add persistent diagnostics or a second observability truth store; that remains Task 41.

## Exit

Task 40 is accepted. **Phase 7 remains open.** Room schema 9 remains current. The active next canonical-engine step is **Task 41: structured decision traces and invariant diagnostics without creating a second truth store**.

# Canonical Catalog Reconciliation & Fusion — Phase 4 Checkpoint

Date: 2026-08-21
Status: **VERIFIED — PHASE 4 CLOSED**
Scope: implementation-plan Tasks 26–32; Room schema 9 remains current.

Normative design: `../../superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md`
Implementation plan: `../../superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md`
Prior accepted evidence: `canonical-catalog-reconciliation-fusion-phase-3.md`

## Represented implementation boundary

### Tasks 26–29 — pure merge policy

- Survivor selection is deterministic and provider-neutral: meaningful user-state domains, total meaningful state, trustworthy creation age only when both timestamps exist, then lexical `StoryId`.
- Library, source preference, protected content mappings, mapping rejections, chapter graph/sync/overrides, and Reader progress are merged by conservative typed policies.
- Stable chapter/release/download identifiers are preserved; protected or incomparable state becomes typed review instead of heuristic mutation.

### Task 30 — read-only preparation and stale-plan protection

- `StoryGraphVersion` fingerprints authoritative graph inputs rather than relying only on identity revision.
- Source identity evidence participates in the fingerprint so a prepared merge cannot commit after evidence changes but before a separate revision bump.
- The Room planner prepares the complete merge before mutation and can return typed conflicts without opening a transaction.

### Task 31 — one atomic Story graph writer

- `RoomStoryGraphMergeCoordinator` is the single authoritative mutation path.
- The writer revalidates prepared graph versions and the authorizing reconciliation case before the first destructive write.
- Story ownership, Library/mappings/rejections, chapters/releases/overrides/sync, Reader progress, redirects, reconciliation cases/work, and audit records are updated atomically.
- Redirects flatten historical IDs to the current survivor; retries are idempotent; resolved historical reconciliation cases are not reopened by pending-case re-keying.
- Required `FUSION_REBUILD` and `POST_MERGE_DERIVED` work is persisted transactionally.

### Task 32 — reconciliation integration

- `CatalogReconciliationService` can map eligible `SAME_WORK` decisions to the same `StoryMergeExecutor`; REVIEW/SEPARATE/NO_MATCH never use the destructive path.
- Protected merge conflicts return to durable review; stale prepared plans schedule reevaluation instead of forcing mutation.
- The mandatory pre-enable and post-enable gates were reported green on the final trimmed/enabled tree. Production wiring uses `ReconciliationExecutionMode.APPLY_ELIGIBLE_AUTO_MERGES`, with the same guarded service path and `RoomStoryGraphMergeCoordinator` as the only automatic destructive merge route.

## Narrow Discover migration repair retained beside Phase 4

A migration-specific Discover defect was found during manual validation: persisted Home rows could exist while their schema-9 canonical state remained `Preparing`, so canonical-only Discover projection could render no cards even though the plugin Home operation was healthy.

The retained fix is intentionally narrow:

- on initial Discover entry with an existing Home cache, derive only the bounded Story IDs already eligible for the visible Popular/Latest/Top-Rated sections;
- best-effort `CanonicalBootstrapUseCase.ensureReady()` from persisted local evidence for those IDs;
- do not call `details()`, do not bypass canonical presentation with raw Home rows, and do not add refresh orchestration.

The earlier experimental loading/presentation masking is explicitly not part of the retained fix. Force-refresh churn/section replacement remains Phase-6 Task 36 orchestration work. Story-detail automatic Full enrichment remains Phase-6 Task 37.

## Verification evidence

Developer-checkout evidence supplied for the Phase-4 implementation before final enablement:

```text
./gradlew :catalog:testDebugUnitTest --tests 'app.openstory.catalog.reconciliation.*'
  BUILD SUCCESSFUL

./gradlew :catalog:testDebugUnitTest :library:testDebugUnitTest :chapters:testDebugUnitTest :reader:testDebugUnitTest
  BUILD SUCCESSFUL

./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.merge.RoomStoryGraphMergeCoordinatorTest
  9/9 tests PASS on Redmi Note 9S - 15

./gradlew :storage:room:connectedDebugAndroidTest
  86/86 tests PASS on Redmi Note 9S - 15
```

The first full `./scripts/verify.sh` run reached Detekt and failed on 18 Phase-4 style findings. A behavior-preserving cleanup patch was then applied without suppressions or threshold changes. The mandatory pre-enable catalog/Room/verify gate was reported green on the final trimmed tree, authorizing the Task-32 production-mode flip.

The developer then reported the required post-enable gate green on the exact enabled tree:

```bash
./gradlew :catalog:testDebugUnitTest :library:testDebugUnitTest :chapters:testDebugUnitTest :reader:testDebugUnitTest
./gradlew :storage:room:connectedDebugAndroidTest
./scripts/verify.sh
```

No Room migration was added; schema 9 remains current. With the guarded production path enabled and the post-enable gate green, Phase 4 is accepted and closed. Phase 5 may begin at Task 33 (`ReconciliationReviewService`).

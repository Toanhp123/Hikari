# Canonical Catalog Reconciliation & Fusion — Phase 6 Task 36 Checkpoint

Date: 2026-08-22
Status: **VERIFIED — TASK 36 CLOSED; PHASE 6 CONTINUES AT TASK 37**
Scope: implementation-plan Task 36 only; Room schema 9 remains current.

Normative design: `../../superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md`
Implementation plan: `../../superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md`
Task-36 execution plan: `../../superpowers/plans/2026-08-22-phase-6-task-36-canonical-engine-orchestration.md`
Prior accepted evidence: `canonical-catalog-reconciliation-fusion-phase-5.md`

## Represented implementation boundary

### One canonical evidence-change orchestration boundary

- `CanonicalEngineOrchestrator` is the single runtime reasoning boundary for committed catalog evidence changes, source-preference changes, and post-merge canonical maintenance.
- Home, Details, and Search persist provider facts first and then emit narrow `CatalogEvidenceChange` facts. They no longer choose reconciliation/Fusion ordering themselves.
- Repository implementations remain persistence/fact-reporting boundaries; they do not invoke the orchestrator and no longer create the normal runtime Fusion work rows that Task 36 moved to orchestration.
- `CatalogEvidenceChange.level` is host-only (`SUMMARY` / `FULL`) and selects stable durable work reasons; it is not provider-selection, quality, or trust policy.

### Ordering, coalescing, and ownership safety

- Identity-only evidence runs reconciliation. Fusion-only evidence rebuilds Fusion. When both change, reconciliation runs first, canonical ownership is resolved again, and Fusion runs once for the resolved Story.
- Availability-only changes remain Fusion concerns and do not manufacture identity evidence.
- Durable engine work coalesces on `(storyId, type)` rather than appending duplicate rows.
- Source-preference changes route through `onSourcePreferenceChanged`; Story UI no longer owns a direct Fusion-rebuild bypass.
- Explicit Story Full refresh relies on `CatalogMetadataCoordinator -> CatalogDetailsLoader -> onEvidenceChanged`; it does not issue a second feature-owned Fusion rebuild.

### Merge and membership semantics retained from Phase 4/5

- User-approved `MERGE` / `AlreadyMerged` notifies `onStoryMerged` only after the authoritative Room merge reports durable success.
- Post-commit orchestration failure cannot rewrite a durable merge success into a false user-visible merge failure.
- `onStoryMerged` invalidates reconciliation candidate state and drains/coalesces survivor Fusion work only. Conditional `POST_MERGE_DERIVED` remains owned by the authoritative Room merge transaction, where domain invalidation facts are atomic.
- The current baseline has no standalone source attach/detach command. Newly observed sources enter through committed evidence and merge re-keying enters through `onStoryMerged`; explicit `onSourceLinked` / `onSourceUnlinked` hooks remain the contract for future independent membership mutations rather than being wired to a fictitious command path.

### Failure semantics and explicit residual

- Home/Details/Search orchestration is post-commit. A non-cancellation orchestration failure is recoverable engine-work failure and must not be reported as if provider persistence failed after it already committed.
- Source-preference orchestration still returns an explicit failure to its caller because that user action needs visible completion semantics.
- Task 36 deliberately does not add a transactional outbox or Room migration. A narrow process-death window remains between a successful provider/preference commit and its post-commit orchestration callback; durable recovery/outbox hardening remains later work rather than silently expanding Task 36 or schema 9.

## Verification evidence

The developer checkout supplied the focused Catalog and feature unit gates after the lifecycle-test helper hotfix:

```text
./gradlew :catalog:testDebugUnitTest \
  --tests 'app.openstory.catalog.orchestration.*' \
  --tests app.openstory.catalog.home.CatalogRefreshServiceTest \
  --tests app.openstory.catalog.details.CatalogDetailsLoaderTest \
  --tests app.openstory.catalog.search.CatalogSearchServiceTest \
  --tests app.openstory.catalog.reconciliation.ReconciliationReviewServiceTest

BUILD SUCCESSFUL in 6s

./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.story.StoryViewModelTest \
  --tests app.openstory.catalog.ui.review.ReconciliationReviewViewModelTest

BUILD SUCCESSFUL in 1s
```

Selected Room instrumentation then passed on Redmi Note 9S - 15:

```text
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCanonicalCatalogRepositoryTest,app.openstory.storage.room.catalog.RoomCanonicalEngineStateTest,app.openstory.storage.room.catalog.RoomCatalogRepositoryTest,app.openstory.storage.room.merge.RoomStoryGraphMergeCoordinatorTest

Starting 47 tests on Redmi Note 9S - 15
Finished 47 tests on Redmi Note 9S - 15
BUILD SUCCESSFUL in 1m 11s
```

The final canonical repository gate was then reported green on the same tree:

```text
./scripts/verify.sh

Canonical engine orchestration policy verified.
Catalog metadata lifecycle policy verified.
Structural hard policies verified.
Current architecture verified: 14 production modules, 1 android-test module, Room schema 1..9.
BUILD SUCCESSFUL in 2m 10s
Room schema export remained stable during verification.
```

The structural/Detekt output still lists existing review candidates and warnings. They are non-blocking in the accepted baseline; no Task-36 suppression or threshold relaxation was introduced, and `CatalogModule` remains at its existing 15-provider structural boundary.

No Room migration was added; schema 9 remains current. Task 36 is accepted and closed. **Phase 6 continues at Task 37: one operation-level Full-metadata fallback service for Story AUTO lifecycle.**

# Canonical Catalog Reconciliation & Fusion — Phase 6 Checkpoint

Date: 2026-08-22
Status: **VERIFIED — PHASE 6 CLOSED**
Scope: implementation-plan Tasks 36–38; Room schema 9 remains current.

Normative design: `../../superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md`
Implementation plan: `../../superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md`
Task-36 accepted evidence: `canonical-catalog-reconciliation-fusion-phase-6-task-36.md`
Prior phase evidence: `canonical-catalog-reconciliation-fusion-phase-5.md`

## Represented implementation boundary

### Task 36 — one committed-evidence orchestration boundary

- Committed Home, Details, and Search evidence routes through one `CanonicalEngineOrchestrator`; persistence adapters report facts rather than owning reconciliation/Fusion policy.
- When identity and presentation both change, reconciliation runs first, the current canonical owner is resolved again, and Fusion rebuilds once for the resolved survivor.
- Story source-preference changes and explicit Full-refresh consequences use the same boundary; user-approved merge reports `onStoryMerged` only after authoritative Room success.
- Candidate-index invalidation and durable work coalescing remain centralized while conditional `POST_MERGE_DERIVED` stays inside the atomic Room merge transaction.

### Task 37 — operation-level Full metadata fallback for Story AUTO lifecycle

- `CatalogFullMetadataFallbackService` owns Story AUTO Full fallback after canonical bootstrap; Search remains navigation-only and Full/Details-network-free.
- Candidate ordering reuses `CatalogFusionEngine.rankedEligibleSourceKeys(...)`, including effective-primary, eligibility, preference, and hysteresis semantics; no provider-specific fallback ranking was introduced.
- Fallback occurs only after an operation failure/unavailability. Any `Ready` Full result ends the operation even when optional fields are sparse; missing description/authors/cover/score never triggers field-healing from another provider.
- Explicit Story source refresh remains source-specific. AUTO enrichment failure is best-effort and cannot rewrite a successful canonical bootstrap into failure.
- Story identity is resolved before source lookup and again after successful Full persistence so a Full-triggered merge cannot leak a retired StoryId as the operation result.

### Task 38 — retroactive reconciliation and post-merge correction review

- Persisted identity-fingerprint changes continue to enter the Task-36 reconciliation-before-Fusion route even when a source already has durable Story ownership.
- `StoryMergeLineageReader` is a read-only domain boundary; the Room adapter recovers historical source sides from existing schema-9 `story_merge_events.reversal_payload` data. No migration or second lineage store was added.
- Candidate-index state is refreshed before correction detection. A hard contradiction discovered after prior A+B merge reopens/records the historical A/B reconciliation case as `REVIEW + INVARIANT_BLOCKED`.
- Post-merge correction never auto-detaches a source, auto-splits a Story, or auto-reverses a merge. Controlled reversal remains Phase 7 work.
- Malformed/unsupported authoritative lineage fails closed instead of being interpreted as “no lineage”, preserving durable reevaluation/recovery behavior.

## Verification evidence

Task 36 was already accepted independently with focused Catalog/feature tests, 47/47 selected Room connected tests, and canonical `./scripts/verify.sh`; see its dedicated checkpoint.

Tasks 37–38 were then verified on the developer checkout in several passes. The first focused Catalog run exposed a Kotlin secondary-constructor delegation cycle in `CatalogFullMetadataFallbackService`; the constructor delegation was made explicit through the `CatalogMetadataAccess` interface without changing DI semantics. The focused Catalog and feature gates then passed:

```text
:catalog:testDebugUnitTest
  CatalogFullMetadataFallbackServiceTest
  CatalogFusionEnginePrimaryTest
  RetroactiveReconciliationTest
  CatalogDetailsLoaderTest
  app.openstory.catalog.orchestration.*
  BUILD SUCCESSFUL

:feature:catalog:testDebugUnitTest
  StoryViewModelTest
  ReconciliationReviewViewModelTest
  BUILD SUCCESSFUL
```

The first selected Room lineage run exposed test-fixture rather than production behavior: the lineage test supplied a synthetic reconciliation case ID without seeding the authorizing durable case, so the merge correctly returned `StalePlan`. The fixture was corrected to create a real merge-authorizing reconciliation case and reuse its case ID/evidence fingerprint. A separately requested non-existent `RoomReconciliationCaseRepositoryTest` class was removed from the verification command rather than inventing a test solely for the command. The final selected Room run was:

```text
RoomStoryMergeLineageReaderTest
RoomStoryGraphMergeCoordinatorTest
  13/13 tests PASS
  BUILD SUCCESSFUL
  device: Redmi Note 9S - 15
```

The first full repository gate after that runtime verification exposed five Task-37/38 Detekt errors only: four `ReturnCount` findings and one `MaxLineLength`. They were removed by behavior-preserving control-flow extraction/formatting with no suppression or threshold change. Focused Catalog tests were rerun successfully on that final production tree, then the canonical full gate passed:

```text
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.details.CatalogFullMetadataFallbackServiceTest \
  --tests app.openstory.catalog.fusion.CatalogFusionEnginePrimaryTest \
  --tests app.openstory.catalog.reconciliation.RetroactiveReconciliationTest \
  --tests app.openstory.catalog.details.CatalogDetailsLoaderTest \
  --tests 'app.openstory.catalog.orchestration.*'
  BUILD SUCCESSFUL

./scripts/verify.sh
  BUILD SUCCESSFUL
  628 actionable tasks
  Room schema export remained stable during verification
```

The structural report still identifies pre-existing review candidates and now reports `CatalogFusionEngine.kt` above 300 lines, but the canonical hard gates pass, no new suppression was added, `CatalogModule` remains at 15 public provider methods, the production graph remains 14 modules plus one `android-test` benchmark module, and Room remains schema 9.

## Accepted residual boundary

Phase 6 deliberately does not add a transactional outbox or new schema to close the narrow process-death window between an already committed provider/preference fact and its post-commit orchestration callback. Durable engine work/recovery draining and broader safety passes begin in Phase 7 Task 39. This residual was explicit in the Task-36 boundary and is not silently expanded here.

## Exit

Tasks 36–38 are accepted. **Phase 6 is VERIFIED and CLOSED.** Room schema 9 remains current. The active next canonical-engine step is **Phase 7 Task 39: app-owned WorkManager draining of durable engine work and policy-reevaluation safety passes**.

# Phase 6 Task 36 Canonical Engine Orchestration Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:executing-plans and superpowers:test-driven-development. Steps use checkbox syntax for tracking.

**Execution status — 2026-08-22:** **VERIFIED / CLOSED.** The developer checkout passed the focused Catalog/feature unit gates, 47/47 selected Room connected tests, and canonical `./scripts/verify.sh`; accepted evidence is `docs/internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-6-task-36.md`. Room remains schema 9 and Task 37 is next.

**Goal:** Replace Home/Details/Search and Story UI direct reconciliation/Fusion routing with one narrow event-driven canonical-engine orchestrator while preserving Phase-4 atomic post-merge work.

**Architecture:** Repository commits remain authoritative persistence boundaries and return semantic change facts. `CanonicalEngineOrchestrator` consumes those facts after commit, routes identity changes to reconciliation, resolves redirects before Fusion, coalesces durable Fusion/retry work, and owns candidate-index invalidation for membership/merge events. Story source preference and explicit Full refresh must not call Fusion directly. The Room Story merge transaction remains the sole owner of conditional durable post-merge reconstruction work so crash atomicity is not weakened.

**Tech Stack:** Kotlin, coroutines, Room, Hilt, kotlin.test, Android instrumentation tests.

**Spec:** `docs/superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md`

## Global Constraints

- No network fetch from Reconciliation or Fusion.
- No provider-specific priority/reputation policy.
- UI reads canonical generations; source inspection remains raw-source only.
- No new production module in v1.
- Room schema remains 9.
- Story graph merge remains one atomic Room transaction.
- Existing `FUSION_REBUILD`, `RECONCILIATION_REEVALUATION`, and conditional `POST_MERGE_DERIVED` rows created by the merge transaction are not moved outside that transaction.

## Known Residual / Explicit Non-Goal

Task 36 deliberately preserves the plan's post-commit fact-routing contract: normal Home/Details/Search and source-preference persistence commits first, then calls the orchestrator. That leaves a narrow process-death window between the durable commit and `onEvidenceChanged`/`onSourcePreferenceChanged`; closing that window completely requires an atomic outbox or equivalent durable scanner/recovery contract, which would add persistence/schema scope not authorized by Task 36. Task 36 therefore makes engine dirty work durable once orchestration starts, keeps merge-derived work atomic inside the merge transaction, and records the remaining post-commit delivery gap for Phase-7 maintenance/recovery hardening instead of introducing a hidden schema change.

The current baseline has no standalone source attach/detach command. Newly observed provider sources are committed through Home/Search/Details and therefore use `onEvidenceChanged`, whose reconciliation path upserts the changed source into the candidate index; merge-owned source re-keying is followed by `onStoryMerged`. `onSourceLinked`/`onSourceUnlinked` remain explicit tested hooks for future independent membership mutations and must be used when such a command is introduced rather than inventing a second routing path.

---

### Task 1: Add the orchestration contract and routing engine

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CatalogEvidenceChange.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineWork.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestrator.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationService.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestratorTest.kt`

**Interfaces:**
- `CatalogEvidenceChange` carries durable Story/source ownership plus identity/fusion/availability change facts and evidence level (`SUMMARY` or `FULL`) solely to select a stable work reason.
- `CatalogReconciliationRunner` exposes only `reconcile(SourceKey)` and `invalidateCandidateIndex()` to orchestration.
- `CanonicalEngineOrchestrator` depends on `CatalogReconciliationRunner`, `CanonicalGenerationRebuilder`, `CanonicalEngineWorkRepository`, and `StoryIdentityRepository`.
- Fusion dirty work is marked before foreground rebuild; successful Fusion supersedes the row through `CanonicalFusionService`; failures leave one coalesced durable row.
- Reconciliation exceptions schedule one `RECONCILIATION_REEVALUATION` row for the currently resolved owner without converting an already-committed catalog refresh into a source/store failure.
- For an event with both identity and fusion changes, reconciliation runs first, then Story identity is resolved again, then Fusion runs once for the current owner.

- [x] **Step 1: Write failing routing tests** for identity-only, fusion-only, both, neither, availability-only, source link, source unlink, source preference, reconciliation failure, redirect-after-reconciliation, and story-merge invalidation.
- [x] **Step 2: Run the focused RED gate.** If Gradle bootstrap is unavailable in the sandbox, record the environmental blocker and continue with the tests still written before production code.
- [x] **Step 3: Implement the minimal orchestration contract and runner adapter.**
- [x] **Step 4: Re-run the focused gate or static compilation checks available in the environment.**

### Task 2: Route persisted Home, Details, and Search facts through the orchestrator

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/home/CatalogRefreshService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/details/CatalogDetailsLoader.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt`
- Modify tests for those services.

**Interfaces:**
- Home and Search convert `CatalogCommitChange` to `CatalogEvidenceChange(level = SUMMARY)`.
- Details converts to `CatalogEvidenceChange(level = FULL)`.
- No service retains direct `CatalogReconciliationService` or `CanonicalGenerationRebuilder` dependencies for post-commit reasoning.

- [x] **Step 1: Update tests first** so exact committed changes are observed through a recording orchestrator/recording gateway rather than direct Fusion/reconciliation fakes.
- [x] **Step 2: Remove direct reasoning calls and route each committed change once.**
- [x] **Step 3: Add/retain churn coverage proving timestamp-only Summary refresh emits no engine routing work.**

### Task 3: Remove persistence-layer duplicate dirty marking while preserving merge atomicity

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepository.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepositoryTest.kt`
- Modify/add: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalEngineStateTest.kt`

**Interfaces:**
- Normal catalog/preference persistence writes state and reports facts only; orchestration owns new foreground dirty marks.
- Schema-9 bootstrap work and Room Story merge transaction work are unchanged.
- Ten marks of the same `(storyId, workType)` coalesce into one row containing the last reason/policy version.

- [x] **Step 1: Change instrumentation expectations first** so new Home/Search/Details/preference writes do not assert repository-owned runtime work rows.
- [x] **Step 2: Remove `search-summary-changed`, `story-created`, and source-preference work writes from normal repositories.**
- [x] **Step 3: Add coalescing coverage and retain merge re-key/orphan coverage.**

### Task 4: Close Story UI and user-review orchestration bypasses

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/ReconciliationReviewService.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/ReconciliationReviewServiceTest.kt`

**Interfaces:**
- Source preference persists first, then calls `orchestrator.onSourcePreferenceChanged`; Story no longer invokes `CanonicalBootstrapUseCase.rebuild(SOURCE_PREFERENCE_CHANGED)` directly.
- Explicit Full refresh relies on `CatalogMetadataCoordinator -> CatalogDetailsLoader -> orchestrator`; Story does not issue a second Fusion rebuild.
- Successful/AlreadyMerged user-review merge calls `orchestrator.onStoryMerged(survivorStoryId)` to invalidate the in-memory candidate index and opportunistically drain the already-atomic Fusion work. It must not recreate conditional `POST_MERGE_DERIVED` scheduling outside Room.

- [x] **Step 1: Update Story tests first** to assert preference orchestration and exactly one Full-refresh engine route.
- [x] **Step 2: Update review-service tests first** to assert merge notification only after successful/idempotent merge.
- [x] **Step 3: Implement the production changes.**

### Task 5: Wire DI, add static bypass gates, and verify the patch

**Files:**
- Modify: `app/src/main/kotlin/app/openstory/di/ReconciliationModule.kt`
- Add/modify static policy test under `scripts/tests/` if needed.
- Update the main canonical implementation plan Task 36 status only after developer verification evidence exists; do not close Phase 6 before Tasks 37–38.

- [x] **Step 1: Bind `CatalogReconciliationRunner` and `CanonicalEngineEventSink` in `ReconciliationModule`; keep `CatalogModule` at its existing 15-provider Detekt boundary.**
- [x] **Step 2: Add static search assertions** that Home/Details/Search/Story no longer call reconciliation/Fusion directly for Task-36 event paths.
- [x] **Step 3: Run focused Catalog/feature unit gates, Room connected tests, Detekt, and `./scripts/verify.sh` where the environment permits.**
- [x] **Step 4: Review `git diff --check`, source-boundary grep, and patch contents; package one apply-ready `.patch` without generated/build files.**

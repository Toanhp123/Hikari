# Canonical Catalog Reconciliation & Fusion Engine Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Build Hikari's provider-agnostic canonical Story identity, multi-catalog metadata fusion, durable reconciliation/review, atomic Story graph merge, redirect lineage, and background safety workflow without allowing plugins or UI to become alternate authorities for canonical truth.

**Architecture:** Catalog plugins continue to provide bounded facts only. `:catalog` owns evidence normalization, reconciliation, canonical identity contracts, and pure fusion; `:library`, `:chapters`, and `:reader` own their merge semantics; `:storage:room` persists canonical state and executes one all-or-nothing graph merge transaction; `:feature:catalog` consumes canonical read models and presents source inspection/review; WorkManager scheduling stays in `:app`. Runtime fetching stays separate from engine reasoning.

**Tech Stack:** Kotlin, coroutines/Flow, Hilt, Room schema migration, kotlinx.serialization for bounded structured persistence where selected, Jetpack Compose, WorkManager in `:app`, Robolectric Compose tests, Android instrumentation tests, existing project verification scripts.

**Spec:** `docs/superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md`

**Execution checkpoint — 2026-08-21:** Phase 0 Tasks 1–4 and Phase 1 Tasks 5–11 are **VERIFIED** on the developer checkout. Phase 1 established the schema-9 canonical persistence foundation, lossless external-identifier/source-record persistence, canonical generation/redirect repositories, durable work/audit foundations, representative graph migration coverage, and local-only bootstrap contracts. Final developer evidence is green: `:catalog:testDebugUnitTest`; `:storage:room:assembleDebug` with no unexpected schema-9 export diff; the selected 27-test Room migration/repository connected suite (27/27); `CompositionPolicyTest`; and the canonical `./scripts/verify.sh` gate after fixing the observer-test subscription race and Phase-1 Detekt line-length findings. Runtime RED observations that were unavailable in the offline implementation sandbox remain intentionally unchecked rather than being retroactively falsified, and per-task commit checkboxes remain open because Tasks 5–11 are being closed as one Phase-1 checkpoint commit. Phase 2 now starts at Task 12.

## Global Constraints

- Room schema is **8 at plan entry**. If this engine is implemented before Wave 10, this plan owns **`MIGRATION_8_9`** and current Wave-10 notification persistence must be rebased to **`9 -> 10`** in current normative docs. Never create two meanings for `MIGRATION_8_9`.
- `CatalogLatestUpdateDto.releaseLabel` is an opaque, complete provider-formatted label. Host/UI must not prepend `"Ch. "` or parse chapter semantics from it.
- Core policy contains **no provider-ID priority branches**, bundled-provider presets, plugin-declared confidence, provider quality, or trust weights.
- Reconciliation/fusion are pure reasoning over persisted/current evidence. They **never call Home/Search/Details/network** to repair missing evidence.
- Missing optional metadata is valid provider output. Operation-level Full fallback is allowed only after operation failure/unavailability, not because a successful payload omitted an optional field.
- Canonical identity means creative work plus compatible medium/adaptation lineage. Similar title alone can never auto-merge.
- Strong identifiers are strong evidence but do not bypass hard content-type/lineage or mutually exclusive identifier conflicts.
- `REVIEW` and `SEPARATE` are durable and evidence/policy-version aware. Identical evidence refresh must not recreate or reopen a resolved case.
- Canonical presentation is materialized as immutable generations. UI sees either the previous active generation or a newly validated/promoted generation, never a partially written generation.
- UI never reconstructs canonical metadata from raw `CatalogEntry` ordering and never uses raw-source fallback as a hidden repair path.
- User Story-level source pin controls primary/default presentation but does not disable field-specific fusion.
- Primary AUTO selection follows the provider-agnostic v1 categorical hysteresis rules from spec §16.4. Changing class order, the two-field coverage margin, or switch conditions requires a primary-selection policy-version bump.
- Latest-update timestamp and release label always come from one coherent source object.
- Canonical score v1 is the unweighted arithmetic mean of normalized usable source values (`value / scale`); no provider weighting.
- Raw provider facts/source records remain lossless. Fusion never overwrites raw source values.
- Destructive `AUTO_MERGE` stays disabled until observe-only reconciliation fixtures and graph-merge integration gates pass.
- Story merge is all-or-nothing across authoritative Story-owned state. Stable canonical chapter IDs and chapter release IDs are never regenerated during Story merge.
- Protected content mapping origins remain `USER_APPROVED` and `USER_URL`; conflicting protected mappings force review unless explicitly resolved by the user.
- Domain semantics remain in their owners. Do not move Library/Mapping/Chapter/Reader merge policy into Catalog or ad-hoc Room SQL.
- Redirect resolution is centralized below features. Feature/navigation code does not implement redirect chains.
- Derived post-merge work is retryable and may fail independently; a valid authoritative merge is not rolled back because a derived rebuild failed.
- WorkManager/platform scheduling remains in `:app`; domain modules continue to forbid `androidx.work`.
- Current `config/architecture/module-boundaries.json` remains authoritative; no new module is introduced in v1.
- Every pure policy, persistence migration, merge behavior, and feature cutover follows TDD: RED test, verify RED, minimal implementation, verify GREEN, focused module gate, commit.
- Run `./scripts/verify.sh` at each phase boundary and before final completion. Run connected tests/macrobenchmarks only where this plan changes those runtime paths.

---

## File/Responsibility Map

The following names are fixed by this implementation plan so separate task executors do not invent competing contracts.

### `:plugins:api`

- `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/catalog/CatalogProtocol.kt`
  - Add `WireCatalogIdentifierScope`, `CatalogExternalIdentifierDto`, bounded identifier collections on `CatalogItemDto` and `CatalogDetailsOutputDto`, and KDoc that makes `releaseLabel` opaque/complete.
- `plugins/api/src/test/kotlin/app/openstory/plugins/api/protocol/catalog/CatalogProtocolTest.kt`
  - Wire validation/serialization tests.
- `docs/plugin-sdk/catalog-protocol.md`
  - Stable catalog protocol documentation for identifiers, score scale, optional metadata, and opaque latest-update labels.

### `:catalog` identity/evidence

- `catalog/src/main/kotlin/app/openstory/catalog/identity/SourceKey.kt`
  - Shared provider-source identity, moved out of matcher-internal models.
- `catalog/src/main/kotlin/app/openstory/catalog/identity/ExternalIdentifier.kt`
  - Host `ExternalIdentifierScope` and `ExternalIdentifier`.
- `catalog/src/main/kotlin/app/openstory/catalog/evidence/CatalogSourceRecord.kt`
  - Lossless persisted source-evidence contract with Summary/Full provenance and identity/fusion fingerprints.
- `catalog/src/main/kotlin/app/openstory/catalog/evidence/CatalogEvidenceFingerprints.kt`
  - Deterministic identity/fusion fingerprint computation.
- `catalog/src/main/kotlin/app/openstory/catalog/evidence/CatalogEvidenceNormalizer.kt`
  - Display-vs-comparison normalization for title/alias/author and identifier canonical ordering.
- `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/ReconciliationEvidenceFactory.kt`
  - Converts persisted `CatalogSourceRecord` or incoming Source Summary/Full facts into provider-agnostic `ReconciliationEvidence`; no fetching.
- `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogSearchSummaryMutation.kt`
  - Search Summary persistence mutation/result so Search facts are durable before canonical card projection; no Home rows and no Details fetch.

### `:catalog` canonical/fusion

- `catalog/src/main/kotlin/app/openstory/catalog/canonical/CanonicalModels.kt`
  - Canonical health, source preference, normalized canonical score, metadata, field provenance, generation, source inspection summary, and `CanonicalStoryState`.
- `catalog/src/main/kotlin/app/openstory/catalog/canonical/CanonicalCatalogRepository.kt`
  - Persistence/read contract implemented by Room; no Room types leak through it.
- `catalog/src/main/kotlin/app/openstory/catalog/fusion/FusionPolicy.kt`
  - `FUSION_POLICY_VERSION`, `PRIMARY_SELECTION_POLICY_VERSION`, source usability/freshness classes, v1 primary quality vector.
- `catalog/src/main/kotlin/app/openstory/catalog/fusion/CatalogFusionEngine.kt`
  - Pure primary/hysteresis/field-fusion candidate generation.
- `catalog/src/main/kotlin/app/openstory/catalog/fusion/CanonicalGenerationValidator.kt`
  - Pure generation invariant validator.
- `catalog/src/main/kotlin/app/openstory/catalog/fusion/CanonicalFusionContract.kt`
  - Early Android/network-free rebuild reason/result/port used by bootstrap before the concrete Fusion service exists.
- `catalog/src/main/kotlin/app/openstory/catalog/fusion/CanonicalFusionService.kt`
  - Reads local source records/current state, invokes pure engine, persists/promotes candidate; never fetches network.
- `catalog/src/main/kotlin/app/openstory/catalog/fusion/CatalogSourceAvailabilityResolver.kt`
  - Runtime-facing adapter producing host availability facts without provider quality scores.

### `:catalog` reconciliation/identity

- `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/ReconciliationPolicy.kt`
  - Versioned thresholds and hard-gate configuration.
- `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/ReconciliationModels.kt`
  - Assessments, reason codes, hard-conflict codes, durable case domain models, unordered pair key.
- `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogCandidateIndex.kt`
  - Direct source identity + title-token shortlist; no policy-decision ownership.
- `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogIngestReconciliationIndex.kt`
  - Forkable one-ingest-session resolver: exact SourceKey owner first, otherwise new-source `AUTO_LINK`/create using the new engine while preserving atomic page projection.
- `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationEngine.kt`
  - Pure symmetric pair assessment/ranking and candidate lead.
- `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/ReconciliationCaseRepository.kt`
  - Durable case/revision contract implemented by Room.
- `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationService.kt`
  - Observe-only first; later delegates eligible auto-merge to `StoryMergeExecutor`.
- `catalog/src/main/kotlin/app/openstory/catalog/identity/StoryIdentityRepository.kt`
  - Redirect resolution and canonical identity-revision read contract.
- `catalog/src/main/kotlin/app/openstory/catalog/identity/StoryMergeModels.kt`
  - `StoryMergeRequest`, origin/result/reversibility types, bounded protected-mapping conflict/resolution transport, expected identity revisions.
- `catalog/src/main/kotlin/app/openstory/catalog/identity/StoryMergeExecutor.kt`
  - Storage-owned execution interface.
- `catalog/src/main/kotlin/app/openstory/catalog/identity/StorySurvivorSelector.kt`
  - Pure meaningful-user-state/age/stable-ID survivor selection.
- `catalog/src/main/kotlin/app/openstory/catalog/identity/CatalogStoryIdFactory.kt`
  - Deterministic host StoryId creation for an unlinked incoming source, preserving the legacy semantic hash inputs without using provider priority.

### Domain-owned merge policies

- `library/src/main/kotlin/app/openstory/library/merge/LibraryStoryMergePolicy.kt`
  - Library membership merge.
- `library/src/main/kotlin/app/openstory/library/merge/ContentMappingStoryMergePolicy.kt`
  - Mapping and rejection merge/protected-conflict semantics.
- `chapters/src/main/kotlin/app/openstory/chapters/merge/ChapterStoryMergePolicy.kt`
  - Lossless chapter/release/override/sync-state move plan.
- `reader/src/main/kotlin/app/openstory/reader/progress/ReadingProgressMergePolicy.kt`
  - Duplicate progress conflict/safe-selection rules.

### `:storage:room`

- `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogEntities.kt`
  - Identifier, canonical state, generations, field provenance, redirects, reconciliation cases/revisions, merge events, reversal audit events, dirty work.
- `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogDao.kt`
  - Canonical read/write/atomic-promotion/identity-revision operations.
- `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepository.kt`
  - `CanonicalCatalogRepository` implementation.
- `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomReconciliationCaseRepository.kt`
  - Durable case/revision implementation.
- `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomStoryIdentityResolver.kt`
  - Central redirect resolution.
- `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryGraphMergePlanner.kt`
  - Read-only redirect resolution, survivor selection, domain prepare/validate, and immutable `PreparedStoryGraphMerge`.
- `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryGraphMergeCoordinator.kt`
  - `StoryMergeExecutor`; consumes the planner, enforces stale-plan guards, and delegates one Room transaction to the writer.
- `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryMergeReaders.kt`
  - DAO-to-domain snapshot adapters used by the coordinator.
- `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryMergeWriter.kt`
  - Transaction-local deterministic graph writes.
- Existing DAOs/entities gain narrow queries required for merge and redirect-aware reads; domain interfaces do not gain Room types.

### Runtime orchestration/background

- `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineWork.kt`
  - Work type/reason/retry model and repository contract.
- `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CatalogEvidenceChange.kt`
  - Commit change report with independent identity/fusion flags.
- `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestrator.kt`
  - Change-to-use-case mapping; no fetch decisions.
- `catalog/src/main/kotlin/app/openstory/catalog/details/CatalogFullMetadataFallbackService.kt`
  - Operation-level ordered Full fallback; successful sparse Full stops fallback.
- `app/src/main/kotlin/app/openstory/work/CanonicalEngineWorker.kt`
  - WorkManager drain of persisted engine work using the same use cases.
- `app/src/main/kotlin/app/openstory/work/WorkManagerCanonicalEngineWorkScheduler.kt`
  - App-owned WorkManager implementation of the Android-free `CanonicalEngineWorkScheduler` port.
- `app/src/main/kotlin/app/openstory/di/CanonicalEngineEntryPoint.kt`
  - Worker entry point.

### `:feature:catalog`

- Existing Story/Search/Discover/Library ViewModels/projectors move to canonical read state.
- `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review/ReconciliationReviewUiState.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review/ReconciliationReviewViewModel.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review/ReconciliationReviewScreen.kt`
- `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryReconciliationPrompt.kt`
  - Review Queue and contextual review are two surfaces over the same durable case.

---

## Phase 0 — Contract Hardening and Regression Characterization

### Task 1: Lock the opaque latest-update label contract and remove `Ch. Ch. 56`
**Patch status:** **VERIFIED.** Focused protocol/Discover tests and the Phase-0 repository gate passed on the developer checkout.


**Files:**
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/catalog/CatalogProtocol.kt`
- Modify: `plugins/api/src/test/kotlin/app/openstory/plugins/api/protocol/catalog/CatalogProtocolTest.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverLatestCard.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverSemanticsTest.kt`
- Create: `docs/plugin-sdk/catalog-protocol.md`

**Interfaces:**
- Consumes: existing `CatalogLatestUpdateDto`, `CatalogLatestUpdate`, `DiscoverStoryItem`.
- Produces: stable contract that `releaseLabel` is complete provider-formatted display text and the UI renders it unchanged.

- [x] **Step 1: Write RED protocol and UI tests**

Add to `CatalogProtocolTest.kt`:

```kotlin
@Test
fun latestUpdateReleaseLabelIsOpaqueCompleteText() {
    val dto = CatalogLatestUpdateDto(
        atEpochMillis = 1234L,
        releaseLabel = "Vol. 4 Ch. 56",
    )

    assertEquals("Vol. 4 Ch. 56", dto.releaseLabel)
}
```

Add to `DiscoverSemanticsTest.kt`:

```kotlin
@Test
fun latestCardRendersProviderFormattedReleaseLabelWithoutPrefixing() {
    compose.setContent {
        HikariTheme {
            DiscoverLatestCard(
                item = story(1).copy(
                    latestUpdate = CatalogLatestUpdate(
                        atEpochMillis = 10L,
                        releaseLabel = "Ch. 56",
                    ),
                ),
                onSelected = {},
            )
        }
    }

    compose.onNodeWithText("Ch. 56").assertIsDisplayed()
    compose.onNodeWithText("Ch. Ch. 56").assertDoesNotExist()
}
```

- [ ] **Step 2: Run the focused tests and confirm the UI test is RED**

Run:

```bash
./gradlew :plugins:api:test \
  --tests app.openstory.plugins.api.protocol.catalog.CatalogProtocolTest
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.discover.DiscoverSemanticsTest
```

Expected: protocol test passes; `latestCardRendersProviderFormattedReleaseLabelWithoutPrefixing` fails because `DiscoverLatestCard` prepends `"Ch. "`.

- [x] **Step 3: Make the smallest production fix**

Change:

```kotlin
val updateLabel = item.latestUpdate?.releaseLabel?.let { label -> "Ch. $label" }
```

to:

```kotlin
val updateLabel = item.latestUpdate?.releaseLabel
```

Add KDoc above `CatalogLatestUpdateDto` stating that `releaseLabel` is opaque, complete presentation text and must not be parsed or prefixed by the host.

- [x] **Step 4: Create the catalog protocol SDK document**

`docs/plugin-sdk/catalog-protocol.md` must explicitly document:

```text
catalog.home/catalog.search:
- required sourceId/title/contentType
- optional fields may be absent
- host does not call details() merely to fill optional listing fields

catalog.details:
- Full is a metadata level, not a completeness promise for every optional field

latestUpdate.releaseLabel:
- complete provider-formatted display label
- may be "56", "Ch. 56", "Vol. 4 Ch. 56", or another bounded provider label
- host does not prepend chapter syntax or parse numeric identity from it
```

- [x] **Step 5: Verify GREEN**

Run the same focused command. Expected: all selected tests pass.

- [x] **Step 6: Commit**

```bash
git add \
  plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/catalog/CatalogProtocol.kt \
  plugins/api/src/test/kotlin/app/openstory/plugins/api/protocol/catalog/CatalogProtocolTest.kt \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverLatestCard.kt \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverSemanticsTest.kt \
  docs/plugin-sdk/catalog-protocol.md
git commit -m "catalog: lock opaque latest update labels"
```

---

### Task 2: Add bounded external-identifier wire and host contracts
**Patch status:** **VERIFIED.** Wire/host/source/domain contracts pass the affected-module gate. Identifier semantics remain deliberately uninterpreted by the legacy matcher, and schema-8 Room persistence remains deferred to Task 7.


**Files:**
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/catalog/CatalogProtocol.kt`
- Modify: `plugins/api/src/test/kotlin/app/openstory/plugins/api/protocol/catalog/CatalogProtocolTest.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/identity/SourceKey.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/identity/ExternalIdentifier.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/matching/MatchResult.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/matching/CatalogMatchIndex.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/matching/StoryMatcher.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/home/CatalogRefreshService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/details/CatalogDetailsLoader.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/source/CatalogSourceModels.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/source/PluginCatalogSource.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/source/PluginCatalogSourceTest.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/model/CatalogEntry.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/model/CatalogModelsTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/matching/CatalogMatchIndexTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/matching/StoryMatcherTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/details/CatalogDetailsLoaderTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogEntityMapper.kt`
- Modify: `docs/plugin-sdk/catalog-protocol.md`

**Interfaces:**
- Produces:

```kotlin
enum class WireCatalogIdentifierScope { WORK, PUBLICATION, EDITION, PROVIDER_RECORD }

@Serializable
data class CatalogExternalIdentifierDto(
    val namespace: String,
    val value: String,
    val scope: WireCatalogIdentifierScope,
)

data class SourceKey(
    val pluginId: PluginId,
    val sourceId: String,
)

enum class ExternalIdentifierScope { WORK, PUBLICATION, EDITION, PROVIDER_RECORD }

data class ExternalIdentifier(
    val namespace: String,
    val value: String,
    val scope: ExternalIdentifierScope,
)
```

- `CatalogItemDto` and `CatalogDetailsOutputDto` gain `externalIdentifiers: Set<CatalogExternalIdentifierDto> = emptySet()`.
- `SourceItem`, `SourceDetails`, and `CatalogEntry` carry the mapped host identifiers.
- `CatalogMatchEvidence`/`CatalogMatchCandidate` temporarily carry the same identifier set so the legacy ingest characterization remains lossless until Task 25 cuts production ingest over to the reconciliation index.
- Removes the duplicate `SourceKey` declaration from matcher-internal `MatchResult.kt`.

- [x] **Step 1: Write RED protocol-boundary tests**

Add tests covering all four scopes, empty collections, invalid blank namespace/value, bounded text, and round-trip serialization:

```kotlin
@Test
fun externalIdentifierRequiresBoundedStableNamespaceAndValue() {
    assertFailsWith<IllegalArgumentException> {
        CatalogExternalIdentifierDto(
            namespace = " ",
            value = "123",
            scope = WireCatalogIdentifierScope.WORK,
        )
    }
}

@Test
fun catalogItemCarriesBoundedExternalIdentifiers() {
    val identifier = CatalogExternalIdentifierDto(
        namespace = "isbn",
        value = "9780000000000",
        scope = WireCatalogIdentifierScope.EDITION,
    )
    val item = CatalogItemDto(
        sourceId = "1",
        title = "One",
        contentType = WireContentType.MANGA,
        externalIdentifiers = setOf(identifier),
    )

    assertEquals(setOf(identifier), item.externalIdentifiers)
}
```

Use a fixed maximum collection size constant in `CatalogProtocol.kt`; choose `MAX_EXTERNAL_IDENTIFIERS = 32` so a malicious provider cannot send an unbounded identifier set.

- [ ] **Step 2: Run protocol tests and confirm RED**

```bash
./gradlew :plugins:api:test \
  --tests app.openstory.plugins.api.protocol.catalog.CatalogProtocolTest
```

Expected: compile failure because the identifier DTO/scope/fields do not exist.

- [x] **Step 3: Add wire types and validation**

Add the exact types above. Validation must call existing bounded/stable text helpers, enforce collection size, and validate every identifier.

Do not add `confidence`, `quality`, `priority`, or weight fields.

- [x] **Step 4: Add RED catalog mapping tests**

In `PluginCatalogSourceTest.kt`, construct wire output with:

```kotlin
externalIdentifiers = setOf(
    CatalogExternalIdentifierDto(
        namespace = "openlibrary.work",
        value = "OL123W",
        scope = WireCatalogIdentifierScope.WORK,
    ),
)
```

Assert the resulting `SourceItem`/`SourceDetails` carries the equivalent host source identifier.

In `CatalogModelsTest.kt`, assert `CatalogEntry.externalIdentifiers` defaults to empty and preserves a populated set.

- [x] **Step 5: Add host identity types and mapping**

Move `SourceKey` into `app.openstory.catalog.identity`; update imports in matcher/home/search/tests.

Add `ExternalIdentifier.kt` with strict `init` validation:

```kotlin
data class ExternalIdentifier(
    val namespace: String,
    val value: String,
    val scope: ExternalIdentifierScope,
) {
    init {
        require(namespace.isNotBlank())
        require(value.isNotBlank())
        require(namespace.length <= 128)
        require(value.length <= 256)
        require(namespace.none(Char::isISOControl))
        require(value.none(Char::isISOControl))
    }
}
```

Add source-side identifier mapping and add `externalIdentifiers` to `CatalogEntry`, `CatalogMatchEvidence`, and `CatalogMatchCandidate`. The legacy `StoryMatcher` must ignore the new identifier field; only the reconciliation engine introduced in Phase 3 may interpret identifier semantics.

- [x] **Step 6: Verify all affected modules**

```bash
./gradlew \
  :plugins:api:test \
  :catalog:testDebugUnitTest \
  :feature:catalog:testDebugUnitTest \
  :storage:room:testDebugUnitTest
```

Expected: GREEN.

- [x] **Step 7: Update SDK semantics**

Document that:

```text
WORK            -> strong work-level evidence
PUBLICATION     -> publication identity evidence
EDITION         -> edition/re-release evidence
PROVIDER_RECORD -> provider-record identity; not cross-provider work proof
```

State that identifiers are optional and plugin-provided confidence is not accepted.

- [x] **Step 8: Commit**

```bash
git add plugins/api catalog \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt \
  storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogEntityMapper.kt \
  docs/plugin-sdk/catalog-protocol.md
git commit -m "catalog: add external identifier contract"
```

---

### Task 3: Establish normalized evidence and independent identity/fusion fingerprints
**Patch status:** **VERIFIED.** Fingerprint tests and module regression pass; the Detekt `MagicNumber` finding in hex encoding was resolved with named constants without changing fingerprint semantics.


**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/evidence/CatalogSourceRecord.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/evidence/CatalogEvidenceNormalizer.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/evidence/CatalogEvidenceFingerprints.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/evidence/CatalogEvidenceFingerprintsTest.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/metadata/CatalogMetadata.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/metadata/CatalogMetadataPolicyTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class CatalogSourceRecord(
    val key: SourceKey,
    val storyId: StoryId,
    val entry: CatalogEntry,
    val summary: CatalogMetadataStamp,
    val full: CatalogMetadataStamp?,
    val identityFingerprint: String,
    val fusionFingerprint: String,
)

object CatalogEvidenceNormalizer {
    fun comparisonKey(value: String): String
}

object CatalogEvidenceFingerprints {
    fun identity(entry: CatalogEntry): String
    fun fusion(snapshot: CatalogMetadataSnapshot): String
}
```

Fingerprint output is lowercase SHA-256 hex over deterministic, delimiter-safe canonical encoding.

- [x] **Step 1: Write RED identity-fingerprint tests**

Cover all normative changes/non-changes:

```kotlin
@Test
fun identityFingerprintIgnoresPresentationOnlyChanges() {
    val base = entry(
        title = "Berserk",
        coverUrl = "https://example.test/a.jpg",
        score = Score(8.0, 10.0),
        latestUpdate = CatalogLatestUpdate(100L, "Ch. 1"),
    )
    val changed = base.copy(
        coverUrl = "https://example.test/b.jpg",
        score = Score(9.0, 10.0),
        latestUpdate = CatalogLatestUpdate(200L, "Ch. 2"),
    )

    assertEquals(
        CatalogEvidenceFingerprints.identity(base),
        CatalogEvidenceFingerprints.identity(changed),
    )
}

@Test
fun identityFingerprintChangesForAliasAuthorTypeOrIdentifier() {
    val base = entry(title = "One")
    assertNotEquals(
        CatalogEvidenceFingerprints.identity(base),
        CatalogEvidenceFingerprints.identity(base.copy(aliases = setOf("Uno"))),
    )
    assertNotEquals(
        CatalogEvidenceFingerprints.identity(base),
        CatalogEvidenceFingerprints.identity(base.copy(authors = setOf("Author"))),
    )
}
```

Add a separate test proving order of sets/identifiers does not change the fingerprint.

- [x] **Step 2: Write RED fusion-fingerprint tests**

Prove fusion fingerprint changes for title, description, cover, aliases/authors/genres/language tags, score, popularity, status, latest update, Summary/Full provenance, and does not change because an input collection was iterated in a different order.

- [ ] **Step 3: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.evidence.CatalogEvidenceFingerprintsTest
```

Expected: compile failure because evidence classes do not exist.

- [x] **Step 4: Implement deterministic normalization and encoding**

`comparisonKey()` must perform:

```text
Unicode NFKC normalization
trim
collapse runs of whitespace to one ASCII space
Locale.ROOT lowercase
```

Do not fuzzy-merge storage keys.

Canonical fingerprint encoding must sort:

```text
aliases/authors/genres/language tags
identifiers by namespace, scope, value
```

and length-prefix each component or use another unambiguous deterministic encoding; never use raw `joinToString("#")` without escaping/length protection.

- [x] **Step 5: Build `CatalogSourceRecord` from metadata snapshots**

Add a small factory/extension in `CatalogSourceRecord.kt`:

```kotlin
fun CatalogMetadataSnapshot.toSourceRecord(): CatalogSourceRecord =
    CatalogSourceRecord(
        key = SourceKey(entry.pluginId, entry.sourceId),
        storyId = entry.storyId,
        entry = entry,
        summary = summary,
        full = full,
        identityFingerprint = CatalogEvidenceFingerprints.identity(entry),
        fusionFingerprint = CatalogEvidenceFingerprints.fusion(this),
    )
```

- [x] **Step 6: Verify GREEN and module regression**

```bash
./gradlew :catalog:testDebugUnitTest
```

Expected: GREEN.

- [x] **Step 7: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/evidence \
  catalog/src/test/kotlin/app/openstory/catalog/evidence \
  catalog/src/main/kotlin/app/openstory/catalog/metadata \
  catalog/src/test/kotlin/app/openstory/catalog/metadata
git commit -m "catalog: define canonical evidence fingerprints"
```

---

### Task 4: Characterize current source-selection behavior before replacing it
**Patch status:** **VERIFIED.** Characterization tests pass and the Phase-0 full repository gate is green; the tests remain explicitly marked for Phase-2 replacement.


**Files:**
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/search/CatalogSearchServiceTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverProjectionTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/projection/CatalogStoryProjectionTest.kt`

**Interfaces:**
- Consumes existing behavior only.
- Produces characterization tests with names that clearly identify behavior slated for replacement in Phase 2.

- [x] **Step 1: Add a Search characterization test**

Add:

```kotlin
@Test
fun selectionCurrentlyRequestsOnlyTheFirstSearchSource() = runTest {
    val repository = FakeRepository()
    val first = Source("a", page(item("a-source", "Same", setOf("Author"))))
    val second = Source("b", page(item("b-source", "Same", setOf("Author"))))
    val service = service(Registry(listOf(first, second)), repository)
    val story = service.search(CatalogSearchRequest("same")).stories.single()

    service.select(story)

    assertEquals(1, first.detailsCalls)
    assertEquals(0, second.detailsCalls)
}
```

The test must document this as **current behavior**, not desired final behavior.

- [x] **Step 2: Add a Story characterization test**

Add a test that gives `StoryViewModel` two `CatalogEntry` values whose plugin IDs sort differently and asserts current AUTO presentation/initial Full request follows the existing alphabetical source order.

- [x] **Step 3: Add Discover/projection characterization tests**

Lock the existing feature-local rules:

```text
cover/genre/status/score/latest availability affects presentation order
latestUpdate picks newest coherent CatalogEntry object
score currently picks max normalized score
```

Also lock that `CatalogStoryProjection` currently chooses first sorted entry for title/cover.

- [x] **Step 4: Run characterization suite**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.search.CatalogSearchServiceTest \
  --tests app.openstory.catalog.projection.CatalogStoryProjectionTest
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.story.StoryViewModelTest \
  --tests app.openstory.catalog.ui.discover.DiscoverProjectionTest
```

Expected: GREEN against current behavior.

- [x] **Step 5: Add comments naming the replacement phase**

Each characterization test that encodes undesired legacy source choice must contain one concise comment:

```kotlin
// Characterization only: Phase 2 replaces this with CanonicalGeneration policy.
```

This is not a deferred implementation instruction; it prevents future readers from treating the legacy behavior as a normative product rule.

- [x] **Step 6: Commit**

```bash
git add \
  catalog/src/test/kotlin/app/openstory/catalog/search/CatalogSearchServiceTest.kt \
  catalog/src/test/kotlin/app/openstory/catalog/projection/CatalogStoryProjectionTest.kt \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverProjectionTest.kt
git commit -m "test: characterize legacy catalog source selection"
```

- [x] **Step 7: Phase-0 gate**

```bash
./gradlew :plugins:api:test :catalog:testDebugUnitTest :feature:catalog:testDebugUnitTest
./scripts/verify.sh
```

Expected: both commands succeed before schema work begins.

---

### Phase-0 verification note

Developer-machine acceptance evidence on 2026-08-21:

- focused `CatalogProtocolTest`: `BUILD SUCCESSFUL`;
- focused `CatalogEvidenceFingerprintsTest`, `CatalogSearchServiceTest`, and `CatalogStoryProjectionTest`: `BUILD SUCCESSFUL`;
- focused `DiscoverSemanticsTest`, `DiscoverProjectionTest`, and `StoryViewModelTest`: `BUILD SUCCESSFUL`;
- combined `:plugins:api:test :catalog:testDebugUnitTest :feature:catalog:testDebugUnitTest :storage:room:testDebugUnitTest`: `BUILD SUCCESSFUL`;
- `./scripts/verify.sh`: PASS after replacing two fingerprint hex-encoding magic literals with named constants;
- Room remains schema 8 and no Phase-1 persistence work is included.

The prepared patch workflow did not preserve executable pre-fix RED runs, so the RED-run steps above remain unchecked. This is recorded as a process-evidence limitation, not rewritten as a false TDD observation.

---

## Phase 1 — Schema and Canonical-Generation Foundation

### Task 5: Define canonical domain/read contracts before Room persistence

**Patch status (2026-08-21): VERIFIED on the developer checkout; Phase-1 acceptance gates are green. Per-task commit checkboxes remain open because Tasks 5–11 are closed as one Phase-1 checkpoint commit.**

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/canonical/CanonicalModels.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/canonical/CanonicalCatalogRepository.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/identity/StoryIdentityRepository.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/canonical/CanonicalModelsTest.kt`

**Interfaces:**
- Produces these exact public/domain shapes:

```kotlin
enum class CanonicalHealth { FRESH, STALE, REEVALUATING, DEGRADED }

enum class CanonicalSourcePreferenceMode { AUTO, PINNED }

data class CanonicalSourcePreference(
    val storyId: StoryId,
    val mode: CanonicalSourcePreferenceMode,
    val pinnedSource: SourceKey?,
    val revision: Long,
)

data class CanonicalScore(
    val normalizedValue: Double,
    val contributorCount: Int,
)

enum class CanonicalFieldKey {
    TITLE,
    DESCRIPTION,
    COVER_URL,
    SOURCE_URL,
    POPULARITY_RANK,
    ALIASES,
    AUTHORS,
    GENRES,
    LANGUAGE_TAGS,
    PUBLICATION_STATUS,
    LATEST_UPDATE,
    SCORE,
}

enum class CanonicalFieldStrategy {
    PRIMARY_WITH_FALLBACK,
    NORMALIZED_UNION,
    FRESHEST_QUALIFIED_VALUE,
    FRESHEST_COHERENT_OBJECT,
    NORMALIZED_MEAN,
}

data class CanonicalFieldContributor(
    val sourceKey: SourceKey,
    val fusionFingerprint: String,
    val metadataLevel: CatalogMetadataLevel,
)

data class CanonicalFieldProvenance(
    val field: CanonicalFieldKey,
    val strategy: CanonicalFieldStrategy,
    val contributors: List<CanonicalFieldContributor>,
    val reasonCodes: List<String>,
    val policyVersion: Int,
)

data class CanonicalMetadata(
    val title: String,
    val description: String?,
    val coverUrl: String?,
    val sourceUrl: String?,
    val popularityRank: Long?,
    val aliases: List<String>,
    val authors: List<String>,
    val genres: List<String>,
    val languageTags: List<String>,
    val publicationStatus: PublicationStatus?,
    val latestUpdate: CatalogLatestUpdate?,
    val score: CanonicalScore?,
)

data class CanonicalGeneration(
    val id: String,
    val storyId: StoryId,
    val fusionPolicyVersion: Int,
    val primarySelectionPolicyVersion: Int,
    val fusionFingerprint: String,
    val effectivePrimary: SourceKey,
    val metadata: CanonicalMetadata,
    val health: CanonicalHealth,
    val provenance: Map<CanonicalFieldKey, CanonicalFieldProvenance>,
    val createdAtEpochMillis: Long,
)

data class CanonicalSourceSummary(
    val sourceKey: SourceKey,
    val entry: CatalogEntry,
    val summary: CatalogMetadataStamp,
    val full: CatalogMetadataStamp?,
    val identityFingerprint: String,
    val fusionFingerprint: String,
)

sealed interface CanonicalStoryState {
    val story: Story
    val health: CanonicalHealth
    val preference: CanonicalSourcePreference
    val sources: List<CanonicalSourceSummary>

    data class Preparing(
        override val story: Story,
        override val health: CanonicalHealth,
        override val preference: CanonicalSourcePreference,
        override val sources: List<CanonicalSourceSummary>,
    ) : CanonicalStoryState

    data class Ready(
        override val story: Story,
        override val health: CanonicalHealth,
        override val preference: CanonicalSourcePreference,
        override val sources: List<CanonicalSourceSummary>,
        val generation: CanonicalGeneration,
    ) : CanonicalStoryState
}
```

`CanonicalScore.normalizedValue` is constrained to `0.0..1.0`, and `contributorCount > 0`.

- [x] **Step 1: Write RED model-invariant tests**

Add tests for:
- PINNED requires a non-null source; AUTO requires null pinned source.
- canonical score bounds/count.
- generation StoryId must be nonblank via existing `StoryId`.
- provenance contributor list must be nonempty.
- `CanonicalStoryState.Ready.generation.storyId == story.id`.

- [ ] **Step 2: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.canonical.CanonicalModelsTest
```

Expected: compile failure because canonical types do not exist.

- [x] **Step 3: Implement canonical models and invariants**

Keep models Android-free and Room-free. Use only Catalog/core/common types.

- [x] **Step 4: Define repository contracts**

`CanonicalCatalogRepository.kt`:

```kotlin
interface CanonicalCatalogRepository {
    fun observeStory(storyId: StoryId): Flow<CanonicalStoryState?>
    fun observeReadyStories(): Flow<List<CanonicalStoryState.Ready>>

    suspend fun state(storyId: StoryId): CanonicalStoryState?
    suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord>
    suspend fun activeGeneration(storyId: StoryId): CanonicalGeneration?
    suspend fun sourcePreference(storyId: StoryId): CanonicalSourcePreference

    suspend fun setSourcePreference(preference: CanonicalSourcePreference)

    suspend fun persistCandidate(
        candidate: CanonicalGeneration,
        expectedActiveGenerationId: String?,
    ): Boolean

    suspend fun markHealth(storyId: StoryId, health: CanonicalHealth)
    suspend fun cleanupObsoleteGenerations(storyId: StoryId)
}
```

`StoryIdentityRepository.kt`:

```kotlin
data class CanonicalIdentityState(
    val storyId: StoryId,
    val identityRevision: Long,
    val createdAtEpochMillis: Long?,
)

interface StoryIdentityRepository {
    fun observeResolved(storyId: StoryId): Flow<StoryId>
    suspend fun resolve(storyId: StoryId): StoryId
    suspend fun identityState(storyId: StoryId): CanonicalIdentityState?
}
```

- [ ] **Step 5: Verify GREEN**

```bash
./gradlew :catalog:testDebugUnitTest
```

- [ ] **Step 6: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/canonical \
  catalog/src/main/kotlin/app/openstory/catalog/identity/StoryIdentityRepository.kt \
  catalog/src/test/kotlin/app/openstory/catalog/canonical
git commit -m "catalog: define canonical read and identity contracts"
```

---

### Task 6: Add the complete Room schema-9 foundation and rebase current roadmap governance

**Patch status (2026-08-21): VERIFIED on the developer checkout; Phase-1 acceptance gates are green. Per-task commit checkboxes remain open because Tasks 5–11 are closed as one Phase-1 checkpoint commit.**

**Files:**
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogEntities.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/CanonicalEngineMigrationTest.kt`
- Create: `storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/9.json`
- Modify: `docs/PROJECT-HANDBOOK.md`
- Modify: `docs/implementation/current-roadmap.md`
- Modify: `docs/implementation/waves/wave-10-background-sync-auth-and-notifications.md`
- Modify: `docs/implementation/waves/wave-11-hardening-open-source-release.md`
- Modify: `docs/project/current-state.md`
- Modify: `docs/project/requirement-coverage.md`
- Modify: `docs/project/document-governance.md`
- Modify: `docs/README.md`
- Modify: `scripts/tests/post-baseline-wave-roadmap-test.sh`

**Interfaces:**
- Produces schema 9 with one foundation migration `MIGRATION_8_9`.
- Produces these tables:
  - `catalog_entry_identifiers`
  - `story_canonical_state`
  - `canonical_generations`
  - `canonical_field_provenance`
  - `reconciliation_cases`
  - `reconciliation_case_revisions`
  - `story_merge_events`
  - `story_merge_reversal_events`
  - `story_redirects`
  - `canonical_engine_work`

**Concrete entity keys/columns:**

```text
catalog_entry_identifiers
  PK(plugin_id, source_id, namespace, value, scope)
  FK(plugin_id, source_id) -> catalog_entries ON DELETE CASCADE
  index(namespace, value, scope)
  index(plugin_id, source_id)

story_canonical_state
  story_id PK/FK -> stories ON DELETE CASCADE
  active_generation_id nullable
  health NOT NULL
  preference_mode NOT NULL
  pinned_plugin_id nullable
  pinned_source_id nullable
  preference_revision NOT NULL
  identity_revision NOT NULL
  created_at_epoch_millis nullable
  CHECK AUTO => pinned columns null
  CHECK PINNED => both pinned columns non-null

canonical_generations
  generation_id PK
  story_id FK -> stories ON DELETE CASCADE
  fusion_policy_version
  primary_policy_version
  fusion_fingerprint
  primary_plugin_id
  primary_source_id
  title
  description nullable
  cover_url nullable
  source_url nullable
  popularity_rank nullable
  aliases
  authors
  genres
  language_tags
  publication_status nullable
  latest_update_at_epoch_millis nullable
  latest_update_release_label nullable
  score_normalized_value nullable
  score_contributor_count nullable
  health
  created_at_epoch_millis
  valid INTEGER
  index(story_id, created_at_epoch_millis)

canonical_field_provenance
  PK(generation_id, field_key, contributor_plugin_id, contributor_source_id)
  FK generation_id -> canonical_generations ON DELETE CASCADE
  strategy
  contributor_fusion_fingerprint
  metadata_level
  reason_codes
  policy_version

reconciliation_cases
  case_id PK
  left_story_id
  right_story_id
  status
  current_revision_id nullable
  contextual_deferred_at_epoch_millis nullable
  created_at_epoch_millis
  updated_at_epoch_millis
  UNIQUE(left_story_id, right_story_id)
  CHECK(left_story_id < right_story_id)

reconciliation_case_revisions
  revision_id PK
  case_id FK -> reconciliation_cases ON DELETE CASCADE
  left_story_id historical text
  right_story_id historical text
  decision
  identity_fingerprint
  policy_version
  score
  title_similarity nullable
  author_similarity nullable
  reason_codes
  hard_conflicts
  resolution_origin nullable
  evaluated_at_epoch_millis

story_merge_events
  merge_event_id PK
  survivor_story_id historical text
  retired_story_id historical text
  origin
  reconciliation_case_id nullable
  evidence_fingerprint
  policy_version
  merged_at_epoch_millis
  reversibility_state
  reversal_payload_version
  reversal_payload
  UNIQUE(survivor_story_id, retired_story_id, evidence_fingerprint, policy_version)

story_merge_reversal_events
  reversal_event_id PK
  merge_event_id FK -> story_merge_events ON DELETE RESTRICT
  restored_story_id historical text
  surviving_story_id historical text
  origin
  reason_codes
  reversed_at_epoch_millis
  UNIQUE(merge_event_id)

story_redirects
  retired_story_id PK historical text
  canonical_story_id FK -> stories ON DELETE RESTRICT
  merge_event_id FK -> story_merge_events ON DELETE RESTRICT
  created_at_epoch_millis
  CHECK(retired_story_id != canonical_story_id)
  index(canonical_story_id)

canonical_engine_work
  PK(story_id, work_type)
  story_id FK -> stories ON DELETE CASCADE
  reason
  attempt_count
  next_attempt_at_epoch_millis
  last_error_code nullable
  required_policy_version nullable
```

`canonical_field_provenance.reason_codes` and reconciliation reason/conflict collections may use the repository's existing deterministic Set converter format; they must remain bounded host-owned strings, never arbitrary plugin JSON.

- [x] **Step 1: Write RED migration test first**

Create a schema-8 database using Room's migration-test helper, insert at least one Story and one `catalog_entries` row, then migrate with `MIGRATION_8_9`.

Initial RED assertions:

```kotlin
database.query("SELECT COUNT(*) FROM story_canonical_state").use {
    assertTrue(it.moveToFirst())
    assertEquals(1, it.getInt(0))
}
database.query(
    "SELECT health, preference_mode, active_generation_id " +
        "FROM story_canonical_state WHERE story_id = 'story-1'",
).use {
    assertTrue(it.moveToFirst())
    assertEquals("REEVALUATING", it.getString(0))
    assertEquals("AUTO", it.getString(1))
    assertTrue(it.isNull(2))
}
```

Also assert `canonical_engine_work` contains one `FUSION_REBUILD` row for that Story.

- [ ] **Step 2: Run RED migration test**

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.CanonicalEngineMigrationTest
```

Expected: compile failure because schema 9/migration do not exist.

- [x] **Step 3: Add Room entities and DAO declarations**

Use Room entities matching the exact table shapes above. `StoryCanonicalStateEntity.activeGenerationId` intentionally has no circular Room FK to `canonical_generations`; promotion logic validates the referenced valid generation transactionally.

Expose `canonicalCatalogDao()` from `OpenStoryDatabase`.

- [x] **Step 4: Implement one `MIGRATION_8_9`**

Migration must:
1. create all ten foundation tables/indexes;
2. insert one `story_canonical_state` row per existing Story with:
   - `health='REEVALUATING'`
   - `preference_mode='AUTO'`
   - null active generation/pin/legacy creation time
   - `preference_revision=0`
   - `identity_revision=0`;
3. insert one coalesced `FUSION_REBUILD` dirty-work row per existing Story;
4. not run matching/fusion SQL;
5. not alter existing Story IDs or existing catalog/domain rows.

- [x] **Step 5: Bump database version and migration registration**

Change:

```kotlin
version = 9
```

and append:

```kotlin
RoomMigrations.MIGRATION_8_9
```

to `OpenStoryDatabase.open()`.

- [x] **Step 6: Rebase every current normative schema reference and its static contract test in the same commit**

Update these current authorities together:

```text
docs/PROJECT-HANDBOOK.md
docs/implementation/current-roadmap.md
docs/implementation/waves/wave-10-background-sync-auth-and-notifications.md
docs/implementation/waves/wave-11-hardening-open-source-release.md
docs/project/current-state.md
docs/project/requirement-coverage.md
docs/project/document-governance.md
docs/README.md
scripts/tests/post-baseline-wave-roadmap-test.sh
```

They must agree on:

```text
Current schema after canonical-engine foundation: 9
Canonical engine consumed 8 -> 9
Wave 10 notification persistence, when implemented, must use 9 -> 10
Wave 11 enters on schema 10 unless another separately reviewed migration intervenes
```

Update `post-baseline-wave-roadmap-test.sh` so its schema assertions require Wave 10 `9 -> 10` and Wave 11 schema 10 stability. Add a scoped supersession entry in `document-governance.md` pointing to this canonical-engine design. Do not rewrite archived plans/checkpoints or historical checkpoint evidence.

- [ ] **Step 7: Run migration test, schema export, and roadmap-governance contract**

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.CanonicalEngineMigrationTest
./gradlew :storage:room:assembleDebug
./scripts/tests/post-baseline-wave-roadmap-test.sh
```

Confirm `storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/9.json` exists, schema 8 remains untouched, and the roadmap contract test is GREEN under the rebased numbering.

- [ ] **Step 8: Run database baseline gate**

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.DatabaseBaselineTest
```

Expected: GREEN with schema 9 as current.

- [ ] **Step 9: Commit**

```bash
git add storage/room docs scripts/tests/post-baseline-wave-roadmap-test.sh
git commit -m "storage: add canonical engine schema foundation"
```

---

### Task 7: Persist external identifiers and expose lossless `CatalogSourceRecord` reads

**Patch status (2026-08-21): VERIFIED on the developer checkout; Phase-1 acceptance gates are green. Per-task commit checkboxes remain open because Tasks 5–11 are closed as one Phase-1 checkpoint commit.**

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogEntityMapper.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogRepository.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogHomeMutation.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogDetailsMutation.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/repository/CatalogRepositoryContractTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/metadata/CatalogMetadataCoordinatorTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/home/CatalogRefreshServiceTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/search/CatalogSearchServiceTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/details/CatalogDetailsLoaderTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/search/SearchViewModelTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`

**Interfaces:**
- `CatalogRepository` gains:

```kotlin
suspend fun sourceRecord(key: CatalogMetadataKey): CatalogSourceRecord?
suspend fun sourceRecords(storyId: StoryId): List<CatalogSourceRecord>
suspend fun sourceRecords(): List<CatalogSourceRecord>
```

Existing `metadataSnapshot()` remains while metadata lifecycle callers still use it.

- [x] **Step 1: Write RED Room repository test**

Commit one Home entry carrying two identifiers, then assert:
- identifiers round-trip;
- source record includes Summary provenance;
- Full provenance is null;
- identity/fusion fingerprints equal `CatalogEvidenceFingerprints` output.

Then commit Details for the same source with a changed identifier set and assert the child rows reflect the current raw source facts exactly.

- [ ] **Step 2: Run RED**

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCatalogRepositoryTest
```

Expected: compile failure because repository methods/identifier persistence are missing.

- [x] **Step 3: Add DAO identifier operations**

Add:

```kotlin
@Query("SELECT * FROM catalog_entry_identifiers WHERE plugin_id = :pluginId AND source_id = :sourceId")
suspend fun identifiers(pluginId: String, sourceId: String): List<CatalogEntryIdentifierEntity>

@Insert(onConflict = OnConflictStrategy.REPLACE)
suspend fun insertIdentifiers(identifiers: List<CatalogEntryIdentifierEntity>)

@Query("DELETE FROM catalog_entry_identifiers WHERE plugin_id = :pluginId AND source_id = :sourceId")
suspend fun deleteIdentifiers(pluginId: String, sourceId: String)
```

Add these exact entry reads for `sourceRecords`:

```kotlin
@Query("SELECT * FROM catalog_entries WHERE story_id = :storyId ORDER BY plugin_id, source_id")
suspend fun entriesForStory(storyId: String): List<CatalogEntryEntity>

@Query("SELECT * FROM catalog_entries ORDER BY story_id, plugin_id, source_id")
suspend fun allEntries(): List<CatalogEntryEntity>
```

Load identifier rows for each returned SourceKey in deterministic order. Keep this implementation simple in Phase 1; Task 42 performance gates decide whether batching is necessary without changing the repository contract.

- [x] **Step 4: Persist identifiers in the same existing Home/Details transaction**

For every committed source record:
1. upsert raw entry;
2. delete previous identifier child rows for that exact SourceKey;
3. insert the incoming current identifier set.

Never union stale identifiers from an older payload when the newer payload explicitly omits them; raw source facts must reflect the latest valid payload at that metadata level.

- [x] **Step 5: Build `CatalogSourceRecord` from Room**

The mapper must use the already-persisted Summary/Full provenance columns plus identifier rows, then compute fingerprints through the Catalog pure helper. Do not persist a second conflicting fingerprint copy in `catalog_entries`.

- [x] **Step 6: Update fake repositories and contract tests**

Every `CatalogRepository` fake in Catalog/feature tests must implement the new methods using deterministic in-memory data. Do not return empty records silently where the test expects source evidence.

- [ ] **Step 7: Verify**

```bash
./gradlew :catalog:testDebugUnitTest :feature:catalog:testDebugUnitTest
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCatalogRepositoryTest
```

- [ ] **Step 8: Commit**

```bash
git add catalog storage/room feature/catalog/src/test
git commit -m "catalog: persist source identifiers and evidence"
```

---

### Task 8: Implement canonical state, generation persistence, atomic promotion, and redirect resolution

**Patch status (2026-08-21): VERIFIED on the developer checkout; Phase-1 acceptance gates are green. Per-task commit checkboxes remain open because Tasks 5–11 are closed as one Phase-1 checkpoint commit.**

**Files:**
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepository.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomStoryIdentityResolver.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogDao.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepositoryTest.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomStoryIdentityResolverTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/StorageModule.kt`

**Interfaces:**
- Implements `CanonicalCatalogRepository`.
- Implements `StoryIdentityRepository` through `RoomStoryIdentityResolver`.

- [x] **Step 1: Write RED generation-visibility test**

Test sequence:

```text
insert Story/source
canonical state has no active generation
persist candidate generation with valid=0
observe/state -> Preparing, candidate not visible
persist candidate's provenance
mark candidate valid and active in one transaction
observe/state -> Ready with that generation
```

The test must assert no observer-visible active pointer can reference `valid=0`.

- [x] **Step 2: Write RED failed-promotion and retention tests**

Cover:
- wrong expected active generation ID -> `persistCandidate()` returns false and active generation remains unchanged;
- new successful promotion retains active + immediately previous successful generation;
- third older successful generation becomes cleanup-eligible;
- provenance contributors map to known SourceKeys owned by the Story.

- [x] **Step 3: Write RED redirect tests**

Insert:

```text
B -> A
A active
```

Assert `resolve(B) == A`.

Insert a deliberately invalid chain through direct DAO fixture only for test setup:

```text
B -> A
A -> C
```

Assert resolver follows to `C`, while production merge code in Phase 4 will flatten at write time. Add a cycle fixture and assert resolver fails with a typed invariant exception/result rather than looping. Also subscribe to `observeResolved(B)` before replacing/flattening B's target and assert the Flow emits the new canonical target once, proving redirect-aware observers can follow a later merge.

- [x] **Step 4: Implement DAO queries and repository mapper**

Use a `@Transaction` Room DAO read to fetch:
- active Story;
- canonical state;
- active generation;
- field provenance;
- raw source records/summaries.

`RoomStoryIdentityResolver.observeResolved(storyId)` observes the redirect rows needed for that requested historical ID and emits the currently resolved active StoryId with `distinctUntilChanged()`. `RoomCanonicalCatalogRepository.observeStory(storyId)` must implement redirected observation exactly as `identity.observeResolved(storyId).flatMapLatest { canonicalId -> dao.observeCanonicalStory(canonicalId) }`, so an observer opened before a later merge follows the survivor without feature-side resubscription.

- [x] **Step 5: Implement atomic promotion**

Use `database.withTransaction`:
1. read current active ID;
2. compare to `expectedActiveGenerationId`;
3. insert generation and provenance;
4. validate source ownership using DAO queries;
5. mark generation valid;
6. update `story_canonical_state.active_generation_id`, health, and identity-safe fields;
7. return success.

A failure before commit leaves the previous active generation unchanged.

- [x] **Step 6: Bind repositories in Hilt**

Add providers in `StorageModule.kt` for:
- `CanonicalCatalogRepository`
- `StoryIdentityRepository`

Do not expose concrete Room types to feature constructors.

- [ ] **Step 7: Verify**

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCanonicalCatalogRepositoryTest,app.openstory.storage.room.catalog.RoomStoryIdentityResolverTest
./gradlew :app:testDebugUnitTest \
  --tests app.openstory.di.CompositionPolicyTest
```

- [ ] **Step 8: Commit**

```bash
git add storage/room app/src/main/kotlin/app/openstory/di/StorageModule.kt
git commit -m "storage: persist canonical generations and redirects"
```

---

### Task 9: Implement durable reconciliation cases, merge-audit foundation, and dirty-work persistence

**Patch status (2026-08-21): VERIFIED on the developer checkout; Phase-1 acceptance gates are green. Per-task commit checkboxes remain open because Tasks 5–11 are closed as one Phase-1 checkpoint commit.**

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineWork.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCanonicalEngineWorkRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogDao.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalEngineStateTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/StorageModule.kt`

**Interfaces:**
- Case/audit tables stay DAO-level in this foundation task; `RoomReconciliationCaseRepository` is created in Task 24 after the reconciliation domain contract exists.
- Defines and implements durable work early because schema migration/bootstrap already needs to enqueue work:

```kotlin
enum class CanonicalEngineWorkType {
    FUSION_REBUILD,
    RECONCILIATION_REEVALUATION,
    POST_MERGE_DERIVED,
    POLICY_REEVALUATION,
}

data class CanonicalEngineWorkItem(
    val storyId: StoryId,
    val type: CanonicalEngineWorkType,
    val reason: String,
    val requiredPolicyVersion: Int?,
    val attemptCount: Int,
    val nextAttemptAtEpochMillis: Long,
    val lastFailureCode: String?,
)

interface CanonicalEngineWorkRepository {
    suspend fun markDirty(
        storyId: StoryId,
        type: CanonicalEngineWorkType,
        reason: String,
        requiredPolicyVersion: Int? = null,
    )
    suspend fun claimReady(nowEpochMillis: Long, limit: Int): List<CanonicalEngineWorkItem>
    suspend fun complete(item: CanonicalEngineWorkItem)
    suspend fun retry(
        item: CanonicalEngineWorkItem,
        failureCode: String,
        nextAttemptAtEpochMillis: Long,
    )
    suspend fun supersede(storyId: StoryId, type: CanonicalEngineWorkType)
}
```

- [x] **Step 1: Write RED dirty-work coalescing tests**

Assert:

```text
markDirty(story-1, FUSION_REBUILD, "summary")
markDirty(story-1, FUSION_REBUILD, "full")
```

leaves exactly one row for `(story-1, FUSION_REBUILD)`, resets it to executable-now semantics, and records the latest deterministic reason.

Assert `retry()` updates attempt/next-run/error without creating a duplicate.

- [x] **Step 2: Write RED case-history persistence test using a local test entity fixture**

Until Phase 3 domain types arrive, test DAO-level invariants:
- unordered pair stored as lexical `left < right`;
- unique current case per pair;
- multiple revisions for one case;
- historical revision Story IDs remain after an active Story row is removed in a transaction that first moves children;
- current case can later be re-keyed.

- [x] **Step 3: Write RED merge-audit durability test**

Insert a merge event whose survivor later becomes historical text. Confirm merge-event history is not cascade-deleted when active Story rows change; only redirect target retains a live Story FK.

- [x] **Step 4: Implement work repository**

`claimReady(nowEpochMillis, limit)` orders by:

```text
next_attempt_at_epoch_millis ASC
story_id ASC
work_type ASC
```

to keep worker behavior deterministic.

- [x] **Step 5: Add narrow DAO methods for future Phase-4 audit**

Add:
- insert merge event;
- insert merge-reversal event and query it by `merge_event_id`;
- insert/update redirect;
- update reversibility state/payload;
- query merge events by retired/survivor historical ID;
- re-key case/work rows from retired ID to survivor with deterministic conflict coalescing.

The reversal DAO is foundation-only here; no reverse behavior is enabled until Task 40. Keeping it in schema 9 avoids burning a second Room version merely to make the spec's controlled reversal auditable.

Do not execute graph merge yet.

- [x] **Step 6: Bind work repository**

Provide `CanonicalEngineWorkRepository` in `StorageModule.kt`.

- [ ] **Step 7: Verify**

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCanonicalEngineStateTest
```

- [ ] **Step 8: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineWork.kt \
  storage/room app/src/main/kotlin/app/openstory/di/StorageModule.kt
git commit -m "storage: persist canonical engine work and audit state"
```

---

### Task 10: Build a representative schema-8 graph migration fixture and run FK integrity checks

**Patch status (2026-08-21): VERIFIED on the developer checkout; Phase-1 acceptance gates are green. Per-task commit checkboxes remain open because Tasks 5–11 are closed as one Phase-1 checkpoint commit.**

**Files:**
- Expand: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/CanonicalEngineMigrationTest.kt`

**Interfaces:**
- Produces one migration fixture proving schema-8 graph survival across all Story-owned domains.
- Reuse the schema-8 fixture patterns already present in `CatalogMigrationTest.kt`, `LibraryMigrationTest.kt`, `ContentMappingMigrationTest.kt`, `ChapterMigrationTest.kt`, `ReadingProgressMigrationTest.kt`, and `DownloadMigrationTest.kt`; those reference tests are not modified by this task.

- [x] **Step 1: Insert a complete schema-8 fixture**

Fixture must contain:
- two Stories;
- Catalog entries and Home section/item references;
- Library membership;
- one protected and one automated content mapping;
- mapping rejection;
- canonical chapter;
- chapter release;
- aggregation override;
- chapter sync state;
- reading progress;
- chapter-storage row for the release.

Use stable literal IDs so post-migration assertions can query every row.

- [x] **Step 2: Migrate 8 -> 9**

Use only `RoomMigrations.MIGRATION_8_9`.

- [x] **Step 3: Assert every preexisting row is unchanged**

Assert exact Story IDs, SourceKeys, chapter/release IDs, mapping origins, progress location, and Home links remain.

- [x] **Step 4: Assert canonical bootstrap foundation**

For each Story:
- canonical state row exists;
- active generation is null;
- health is `REEVALUATING`;
- AUTO preference;
- legacy `created_at_epoch_millis` is null;
- exactly one coalesced FUSION rebuild work item.

Also assert migration does **not** fabricate evidence/history: `catalog_entry_identifiers`, `reconciliation_cases`, `reconciliation_case_revisions`, `story_merge_events`, `story_merge_reversal_events`, and `story_redirects` all start empty for the schema-8 fixture.

- [x] **Step 5: Run SQLite FK check**

Execute:

```sql
PRAGMA foreign_key_check
```

and assert zero rows.

- [ ] **Step 6: Verify all Room migration suites**

```bash
./gradlew :storage:room:connectedDebugAndroidTest
```

Expected: all migration/repository instrumentation tests GREEN.

- [ ] **Step 7: Commit**

```bash
git add storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/CanonicalEngineMigrationTest.kt
git commit -m "test: cover canonical schema graph migration"
```

---

### Task 11: Add local-only canonical bootstrap and one-Story priority build

**Patch status (2026-08-21): VERIFIED on the developer checkout; Phase-1 acceptance gates are green. Per-task commit checkboxes remain open because Tasks 5–11 are closed as one Phase-1 checkpoint commit.**

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/fusion/CanonicalFusionContract.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/canonical/CanonicalBootstrapUseCase.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/canonical/CanonicalBootstrapUseCaseTest.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt`

**Interfaces:**
- Produces the rebuild contract before the concrete Phase-2 Fusion implementation so Phase 1 remains compilable:

```kotlin
enum class CanonicalFusionReason {
    BOOTSTRAP,
    SOURCE_EVIDENCE_CHANGED,
    SOURCE_AVAILABILITY_CHANGED,
    SOURCE_PREFERENCE_CHANGED,
    POLICY_REEVALUATION,
    POST_MERGE,
}

sealed interface CanonicalFusionResult {
    data class Promoted(val generation: CanonicalGeneration) : CanonicalFusionResult
    data class Unchanged(val active: CanonicalGeneration) : CanonicalFusionResult
    data class Preparing(val storyId: StoryId) : CanonicalFusionResult
    data class Failed(val storyId: StoryId, val code: String, val retryable: Boolean) : CanonicalFusionResult
}

fun interface CanonicalGenerationRebuilder {
    suspend fun rebuild(storyId: StoryId, reason: CanonicalFusionReason): CanonicalFusionResult
}

class CanonicalBootstrapUseCase(
    private val canonical: CanonicalCatalogRepository,
    private val rebuilder: CanonicalGenerationRebuilder,
) {
    suspend fun ensureReady(storyId: StoryId): CanonicalStoryState
    suspend fun prewarm(storyIds: List<StoryId>, limit: Int)
}
```

`ensureReady` reads only persisted local evidence and never asks any catalog source for network metadata.

- [x] **Step 1: Write RED bootstrap tests with a fake canonical store/fusion service**

Required scenarios:

```text
already Ready -> no rebuild
Preparing + local evidence -> one fusion rebuild -> Ready
Preparing + no source evidence -> remains Preparing/DEGRADED, no fetch
prewarm respects ordered Story list and limit
```

Use a fake `CanonicalGenerationRebuilder` that records StoryIds/reasons. Assert no `CatalogSourceRegistry` or `CatalogMetadataCoordinator` dependency exists in the constructor via reflection:

```kotlin
assertFalse(
    CanonicalBootstrapUseCase::class.java.declaredConstructors
        .flatMap { it.parameterTypes.toList() }
        .any { it.name.contains("CatalogSourceRegistry") || it.name.contains("CatalogMetadataCoordinator") },
)
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.canonical.CanonicalBootstrapUseCaseTest
```

Expected: compile failure before use case exists.

- [x] **Step 3: Implement local-only bootstrap**

`ensureReady`:
1. resolve canonical state through repository;
2. if Ready, return it;
3. call `rebuilder.rebuild(storyId, CanonicalFusionReason.BOOTSTRAP)`;
4. reread state;
5. return Ready or Preparing with updated health.

No network dependency is added.

- [x] **Step 4: Add canonical-state creation for newly created Stories**

When `RoomCatalogRepository` inserts a genuinely new Story after schema 9, insert `story_canonical_state` in the same transaction with:
- AUTO preference;
- `identity_revision=0`;
- `created_at_epoch_millis` equal to the host mutation time (`refreshedAtEpochMillis` for Home-created Story, `resolvedAtEpochMillis` for Details-created Story);
- mark FUSION rebuild dirty.

Migrated legacy rows keep null creation time.

- [ ] **Step 5: Verify Catalog + Room regression**

```bash
./gradlew :catalog:testDebugUnitTest
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCatalogRepositoryTest,app.openstory.storage.room.catalog.RoomCanonicalCatalogRepositoryTest
```

- [ ] **Step 6: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/fusion/CanonicalFusionContract.kt \
  catalog/src/main/kotlin/app/openstory/catalog/canonical/CanonicalBootstrapUseCase.kt \
  catalog/src/test/kotlin/app/openstory/catalog/canonical/CanonicalBootstrapUseCaseTest.kt \
  storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt \
  storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt
git commit -m "catalog: add local canonical bootstrap"
```

- [x] **Step 7: Phase-1 gate**

```bash
./scripts/verify.sh
```

Developer checkout: PASS. Room schema 9 is the single current export, current docs assign `8 -> 9` exclusively to the canonical-engine foundation, and Wave 10 is rebased to `9 -> 10`.

---

## Phase 2 — Metadata Fusion Engine and Canonical Read-Path Cutover

### Task 12: Define source usability/freshness and versioned fusion policy facts

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/fusion/FusionPolicy.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/fusion/CatalogSourceAvailabilityResolver.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/fusion/FusionPolicyTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/CatalogModule.kt`

**Interfaces:**
- Produces:

```kotlin
const val FUSION_POLICY_VERSION: Int = 1
const val PRIMARY_SELECTION_POLICY_VERSION: Int = 1

enum class CatalogSourceUsability {
    ACTIVE,
    STALE,
    TEMPORARILY_UNAVAILABLE,
    UNAVAILABLE,
    RETIRED,
}

enum class CatalogSourceFreshness {
    FRESH,
    STALE,
    UNKNOWN,
}

data class FusionSource(
    val record: CatalogSourceRecord,
    val usability: CatalogSourceUsability,
    val freshness: CatalogSourceFreshness,
)

data class PrimaryQuality(
    val usability: CatalogSourceUsability,
    val metadataLevel: CatalogMetadataLevel,
    val freshness: CatalogSourceFreshness,
    val primaryFieldCoverage: Int,
    val stableSourceKey: SourceKey,
)
```

Descending quality orders are exactly:

```text
ACTIVE > STALE > TEMPORARILY_UNAVAILABLE > UNAVAILABLE > RETIRED
FULL > SUMMARY
FRESH > STALE > UNKNOWN
```

Primary coverage fields are exactly:

```text
description
coverUrl
sourceUrl
authors
aliases
genres
publicationStatus
latestUpdate
score
```

- [ ] **Step 1: Write RED quality-vector tests**

Add tests asserting:
- exact enum ordering through explicit comparator functions, not enum ordinal;
- Full outranks Summary;
- nine coverage fields are counted and title is not;
- plugin ID only participates as final deterministic SourceKey tie-break;
- no field named confidence/quality weight exists in `FusionSource`.

- [ ] **Step 2: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.fusion.FusionPolicyTest
```

Expected: compile failure.

- [ ] **Step 3: Implement explicit comparators**

Do not depend on enum declaration ordinal. Add functions:

```kotlin
fun CatalogSourceUsability.rank(): Int = when (this) {
    CatalogSourceUsability.ACTIVE -> 5
    CatalogSourceUsability.STALE -> 4
    CatalogSourceUsability.TEMPORARILY_UNAVAILABLE -> 3
    CatalogSourceUsability.UNAVAILABLE -> 2
    CatalogSourceUsability.RETIRED -> 1
}

fun CatalogMetadataLevel.rank(): Int = when (this) {
    CatalogMetadataLevel.Full -> 2
    CatalogMetadataLevel.Summary -> 1
}

fun CatalogSourceFreshness.rank(): Int = when (this) {
    CatalogSourceFreshness.FRESH -> 3
    CatalogSourceFreshness.STALE -> 2
    CatalogSourceFreshness.UNKNOWN -> 1
}
```

- [ ] **Step 4: Implement runtime availability adapter without provider-specific rules**

`CatalogSourceAvailabilityResolver` consumes:
- `CatalogSourceRegistry` enabled/source availability;
- existing metadata freshness policy/provenance;
- current operation failure classification supplied by callers when available.

It maps objective facts only. It does not contain plugin IDs.

- [ ] **Step 5: Verify**

```bash
./gradlew :catalog:testDebugUnitTest
```

- [ ] **Step 6: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/fusion \
  catalog/src/test/kotlin/app/openstory/catalog/fusion \
  app/src/main/kotlin/app/openstory/di/CatalogModule.kt
git commit -m "catalog: define provider agnostic fusion quality"
```

---

### Task 13: Implement automatic primary selection, hysteresis, and Story-level source pin

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/fusion/CatalogFusionEngine.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/fusion/CatalogFusionEnginePrimaryTest.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/canonical/CanonicalModels.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepository.kt`
- Expand: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepositoryTest.kt`

**Interfaces:**
- Produces:

```kotlin
data class FusionInput(
    val story: Story,
    val sources: List<FusionSource>,
    val previousGeneration: CanonicalGeneration?,
    val preference: CanonicalSourcePreference,
    val evaluatedAtEpochMillis: Long,
)

data class CanonicalGenerationCandidate(
    val storyId: StoryId,
    val fusionPolicyVersion: Int,
    val primarySelectionPolicyVersion: Int,
    val fusionFingerprint: String,
    val effectivePrimary: SourceKey,
    val metadata: CanonicalMetadata,
    val health: CanonicalHealth,
    val provenance: Map<CanonicalFieldKey, CanonicalFieldProvenance>,
    val createdAtEpochMillis: Long,
)

class CatalogFusionEngine {
    fun fuse(input: FusionInput): CanonicalGenerationCandidate
}
```

- [ ] **Step 1: Write RED initial-primary tests**

Required exact cases:
- no prior primary/pin -> highest `PrimaryQuality` vector wins;
- equal vectors -> stable `SourceKey(pluginId.value, sourceId)` ascending wins;
- provider list order does not change the result.

- [ ] **Step 2: Write RED hysteresis tests**

Encode all five spec switch rules:

```text
1 challenger strictly better usability -> switch
2 equal usability + better metadata level -> switch
3 equal usability/metadata + better freshness and no lower coverage -> switch
4 equal usability/metadata/freshness + coverage advantage >= 2 -> switch
5 previous primary no longer eligible -> switch
```

And negative cases:
- one-field coverage advantage -> keep current;
- fresher challenger with lower coverage -> keep current;
- fresh-selection tie would prefer challenger by SourceKey but current remains due hysteresis.

- [ ] **Step 3: Write RED pin tests**

Required:
- usable pinned source becomes effective primary;
- unavailable pin remains persisted but engine chooses temporary effective fallback;
- returning pinned source becomes primary again;
- AUTO mode ignores stale pinned columns through model invariant;
- pin does not alter fusion contributors for collection/latest fields in later field tests.

- [ ] **Step 4: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.fusion.CatalogFusionEnginePrimaryTest
```

Expected: compile failure before engine exists.

- [ ] **Step 5: Implement primary selection before field fusion**

Implementation sequence inside `fuse()`:

```kotlin
val ranked = input.sources.sortedWith(primaryQualityComparator)
val effectivePrimary = selectPinnedPrimary(input, ranked)
    ?: selectAutomaticPrimary(input.previousGeneration, ranked)
    ?: error("Canonical fusion requires at least one source")
```

`selectAutomaticPrimary` must compare the current primary against the best challenger using the five explicit hysteresis rules, not a floating aggregate provider score.

- [ ] **Step 6: Implement persistence of preference revisions**

`setSourcePreference()` must:
- resolve StoryId first;
- increment `preference_revision`;
- preserve pin when source is unavailable;
- mark one `FUSION_REBUILD` work item dirty.

Pin writes never edit raw `catalog_entries`.

- [ ] **Step 7: Verify Catalog + Room**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.fusion.CatalogFusionEnginePrimaryTest
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCanonicalCatalogRepositoryTest
```

- [ ] **Step 8: Commit**

```bash
git add catalog storage/room
git commit -m "catalog: add stable primary selection and source pin"
```

---

### Task 14: Implement field-specific fusion and immutable provenance

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/fusion/CatalogFusionEngine.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/fusion/CatalogFusionEngineFieldsTest.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/canonical/CanonicalModels.kt`

**Interfaces:**
- Consumes `FusionInput` and selected effective primary.
- Produces complete `CanonicalMetadata` plus field-level provenance.

- [ ] **Step 1: Write RED primary-oriented scalar tests**

Test `TITLE`, `DESCRIPTION`, `SOURCE_URL`, `POPULARITY_RANK`:
- qualified effective-primary value wins;
- missing primary value falls back to best qualified ranked source;
- contributor/provenance points to the actual fallback source.

- [ ] **Step 2: Write RED cover tests**

Cover:
- valid primary cover wins;
- absent primary cover -> best qualified source with cover;
- provider ordering does not affect outcome except stable final tie-break;
- no provider-ID artwork preset.

- [ ] **Step 3: Write RED normalized-union tests**

Aliases:
- include each source title plus explicit aliases;
- remove canonical title by normalized-key equality;
- deterministic order by normalized key then display value.

Authors/genres/language tags:
- trim/normalize key;
- exact normalized-key dedupe;
- do not fuzzy-collapse distinct authors such as `"John Smith"` vs `"Jon Smith"`;
- provenance includes all contributors.

- [ ] **Step 4: Write RED status tests**

Exact precedence:
1. currently usable Full;
2. equal metadata level -> fresher;
3. equal -> effective primary;
4. equal -> stable SourceKey.

Stale-only/historical values may remain while generation health becomes stale/degraded.

- [ ] **Step 5: Write RED latest-update coherence tests**

Use test-local builders `source(pluginId: String, latestUpdate: CatalogLatestUpdate?)` and `input(sources: List<FusionSource>, primary: SourceKey)` in this test file, then add:

```kotlin
@Test
fun latestUpdateKeepsTimestampAndLabelFromTheSameSource() {
    val a = source("provider.a", CatalogLatestUpdate(200L, "Ch. 20"))
    val b = source("provider.b", CatalogLatestUpdate(100L, "Vol. 2 Ch. 10"))
    val candidate = CatalogFusionEngine().fuse(input(listOf(a, b), a.sourceKey))

    assertEquals(CatalogLatestUpdate(200L, "Ch. 20"), candidate.metadata.latestUpdate)
    assertEquals(
        listOf(a.sourceKey),
        candidate.provenance.getValue(CanonicalFieldKey.LATEST_UPDATE)
            .contributors.map(CanonicalFieldContributor::sourceKey),
    )
}
```

Also:
- newest qualified timestamp wins;
- equal timestamp -> effective primary -> SourceKey;
- output label remains opaque.

- [ ] **Step 6: Write RED canonical-score tests**

Use:
- `8/10 -> 0.8`
- `4/5 -> 0.8`
- `90/100 -> 0.9`

Expected mean:

```text
(0.8 + 0.8 + 0.9) / 3 = 5.0 / 6.0
assert with tolerance `1e-12`; contributorCount = 3
```

Filter invalid/unusable score sources according to `FusionSource` qualification. No provider weights.

- [ ] **Step 7: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.fusion.CatalogFusionEngineFieldsTest
```

- [ ] **Step 8: Implement each field strategy as a focused private function**

Required private-function split is exactly `selectPrimaryScalar`, `selectCover`, `unionTextCollection`, `selectPublicationStatus`, `selectLatestUpdate`, and `aggregateScore`. Each function receives only the ranked/qualified `FusionSource` data and effective-primary context needed for its field and returns both selected value/contributors through a file-private field-selection helper; do not build one monolithic `fuse()` body.

- [ ] **Step 9: Build provenance with source revision fingerprints**

Every contributor stores the contributor's **current `fusionFingerprint` used by this candidate**, not a pointer that later resolves to a newer raw state.

- [ ] **Step 10: Verify all pure fusion tests**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests 'app.openstory.catalog.fusion.*'
```

- [ ] **Step 11: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/fusion \
  catalog/src/main/kotlin/app/openstory/catalog/canonical/CanonicalModels.kt \
  catalog/src/test/kotlin/app/openstory/catalog/fusion
git commit -m "catalog: fuse canonical metadata by field policy"
```

---

### Task 15: Validate, persist, promote, retain, and recover canonical generations

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/fusion/CanonicalGenerationValidator.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/fusion/CanonicalFusionService.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/fusion/CanonicalGenerationValidatorTest.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/fusion/CanonicalFusionServiceTest.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepository.kt`
- Expand: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepositoryTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/CatalogModule.kt`

**Interfaces:**
- Consumes `CanonicalFusionReason`, `CanonicalFusionResult`, and `CanonicalGenerationRebuilder` from Task 11.
- Produces the concrete local-evidence Fusion implementation:

```kotlin
class CanonicalGenerationValidator {
    fun validate(
        story: Story,
        ownedSources: Set<SourceKey>,
        candidate: CanonicalGenerationCandidate,
    ): List<String>
}

class CanonicalFusionService(
    // repository/engine/validator/availability dependencies from Tasks 8, 12–14
) : CanonicalGenerationRebuilder {
    override suspend fun rebuild(storyId: StoryId, reason: CanonicalFusionReason): CanonicalFusionResult
}
```

- [ ] **Step 1: Write RED validation tests**

Validator rejects:
- candidate StoryId mismatch;
- effective primary not owned by Story;
- any provenance contributor not owned;
- `LATEST_UPDATE` provenance with more than one source;
- content-type contradiction;
- canonical score out of range/count zero;
- missing TITLE provenance.

- [ ] **Step 2: Write RED service tests**

Required:
- no local sources -> mark DEGRADED/Preparing, no fetch;
- candidate validation failure -> keep old generation, return failure;
- same meaningful canonical result/policies/provenance/health -> no new visible generation;
- changed meaningful state -> persist/promote;
- repository promotion race returns false -> reread/retry once with current state, otherwise return retryable failure;
- failed candidate leaves old generation active.

- [ ] **Step 3: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.fusion.CanonicalGenerationValidatorTest \
  --tests app.openstory.catalog.fusion.CanonicalFusionServiceTest
```

- [ ] **Step 4: Implement deterministic generation ID**

Use a host-generated stable unique ID such as:

```kotlin
"gen:${storyId.value}:${createdAtEpochMillis}:${candidate.fusionFingerprint.take(16)}"
```

If the same ID already exists from a crash/retry, repository insertion must be idempotent and promotion must reuse/validate it rather than create a duplicate.

- [ ] **Step 5: Implement meaningful-change suppression**

Compare:
- canonical metadata;
- effective primary;
- field provenance strategy/contributors/revision fingerprints/reasons;
- policy versions;
- health.

If all are semantically equal, complete dirty work without promoting a new generation.

- [ ] **Step 6: Implement retention/recovery**

After promotion:
- keep active successful generation;
- keep immediately previous successful generation;
- remove older successful generations not required by in-progress recovery.

On load, a persisted invalid/in-progress candidate never becomes active; it may be deleted/rebuilt.

- [ ] **Step 7: Bind the concrete rebuilder and bootstrap in app DI**

In `CatalogModule.kt`, bind `CanonicalGenerationRebuilder` to the singleton `CanonicalFusionService`. Provide/inject `CanonicalBootstrapUseCase` from `CanonicalCatalogRepository + CanonicalGenerationRebuilder`. No bootstrap binding exists before this task, so Phase 1 has no fake production implementation.

- [ ] **Step 8: Verify Room atomicity**

Add an instrumentation test that intentionally throws between candidate insert and promotion inside a test-only transaction hook; assert active pointer remains old and `PRAGMA foreign_key_check` remains empty.

- [ ] **Step 9: Verify**

```bash
./gradlew :catalog:testDebugUnitTest
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCanonicalCatalogRepositoryTest
```

- [ ] **Step 10: Commit**

```bash
git add catalog storage/room app/src/main/kotlin/app/openstory/di/CatalogModule.kt
git commit -m "catalog: atomically build canonical generations"
```

---

### Task 16: Cut a canonical projection/read API for multi-Story consumers

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/projection/CatalogStoryProjection.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/projection/CatalogStoryProjectionRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogStoryProjectionRepository.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/projection/CatalogStoryProjectionTest.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt`

**Interfaces:**
- Final projection is canonical-generation based:

```kotlin
data class CatalogStoryProjection(
    val storyId: StoryId,
    val title: String,
    val contentType: ContentType,
    val coverUrl: String?,
    val aliases: Set<String>,
    val authors: Set<String>,
    val publicationStatus: PublicationStatus?,
    val latestUpdate: CatalogLatestUpdate?,
    val score: CanonicalScore?,
    val health: CanonicalHealth,
)

interface CatalogStoryProjectionRepository {
    fun observe(): Flow<List<CatalogStoryProjection>>
    fun observeForStories(storyIds: Set<StoryId>): Flow<List<CatalogStoryProjection>>
}
```

- [ ] **Step 1: Replace legacy projection characterization with RED canonical expectations**

Build two raw source entries where alphabetical first differs from canonical generation. Assert projection follows the active generation, not raw entry sort.

- [ ] **Step 2: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.projection.CatalogStoryProjectionTest
```

- [ ] **Step 3: Reimplement projection from `CanonicalStoryState.Ready`**

`projectCatalogStory(story, entries)` must no longer be the authoritative production path. Remove it once all compile-time callers are migrated in this task; if tests still require a pure helper, replace it with:

```kotlin
fun CanonicalStoryState.Ready.toProjection(): CatalogStoryProjection
```

- [ ] **Step 4: Reimplement Room projection repository**

Join/read canonical active generations through `CanonicalCatalogDao`, not `catalog_entries` alphabetical ordering.

Preparing Stories are omitted from projection lists until their priority/background bootstrap creates a generation; caller ViewModels may represent preparing counts separately if product-relevant.

- [ ] **Step 5: Verify**

```bash
./gradlew :catalog:testDebugUnitTest
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCatalogRepositoryTest
```

- [ ] **Step 6: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/projection \
  catalog/src/test/kotlin/app/openstory/catalog/projection \
  storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogStoryProjectionRepository.kt \
  storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt
git commit -m "catalog: project stories from canonical generations"
```

---

### Task 17: Migrate Story AUTO presentation to canonical state while preserving raw source inspection

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryUiState.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySources.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryScreenshotTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt`

**Interfaces:**
- `StoryViewModel` consumes `CanonicalCatalogRepository` and `CanonicalBootstrapUseCase`.
- AUTO presentation reads `CanonicalStoryState.Ready.generation.metadata`.
- Source inspection reads `CanonicalStoryState.sources` raw values.
- User actions:

```kotlin
fun selectInspectionSource(sourceKey: SourceKey?)
fun pinPrimary(sourceKey: SourceKey)
fun useAutomaticPrimary()
```

Inspection selection is not the same as source preference.

- [ ] **Step 1: Rewrite Story ViewModel tests to RED canonical behavior**

Required:
- two source entries + active canonical generation -> title/description/score/cover from generation;
- AUTO does not alphabetically choose raw source;
- source inspection selection shows raw provider value without changing canonical AUTO generation;
- pin action writes preference and triggers canonical rebuild;
- unpin returns AUTO;
- pinned primary does not suppress latest-update/collection contributors from another source;
- Preparing state calls local `ensureReady()` once and does not expose raw source as canonical fallback;
- retired StoryId supplied in assisted args resolves to survivor through repository.

- [ ] **Step 2: Run RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.story.StoryViewModelTest
```

- [ ] **Step 3: Replace legacy source ordering**

Delete logic equivalent to:

```kotlin
compareBy<CatalogEntry> { it.pluginId.value }.thenBy { it.sourceId }
```

for canonical AUTO metadata choice.

Keep a stable SourceKey order only for the **inspection list**, not canonical truth.

- [ ] **Step 4: Add explicit source-inspection/pin UI semantics**

Story Sources UI must distinguish:
- `Automatic` canonical mode;
- effective primary badge;
- user-pinned indicator;
- raw source rows for inspection;
- action to pin selected source;
- action to return to AUTO.

Do not add per-field pin UI.

- [ ] **Step 5: Keep explicit refresh behavior source-scoped**

Refreshing a selected raw source may call the existing metadata lifecycle for that source. AUTO display does not fan out network calls merely because a canonical field is absent.

- [ ] **Step 6: Update snapshots/semantics and verify**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.story.StoryViewModelTest \
  --tests app.openstory.catalog.ui.story.StoryScreenshotTest
```

If intentional visuals change, record only the affected Story snapshots using the repository's existing Roborazzi workflow.

- [ ] **Step 7: Commit**

```bash
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story \
  app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt
git commit -m "story: consume canonical catalog presentation"
```

---

### Task 18: Persist Search Summary facts, build canonical cards, and make selection navigation-only

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogSearchSummaryMutation.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchModels.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/search/CatalogSearchServiceTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/repository/CatalogRepositoryContractTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/metadata/CatalogMetadataCoordinatorTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/home/CatalogRefreshServiceTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/details/CatalogDetailsLoaderTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchUiState.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchViewModel.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchResultCard.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/search/SearchViewModelTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/search/SearchScreenshotTest.kt`

**Interfaces:**
- Search still queries every enabled provider and preserves provider/source cards for inspection/debug.
- Search Summary facts are persisted before they are presented as canonical Stories.
- New repository contract:

```kotlin
data class CatalogSearchSummaryMutation(
    val pluginId: PluginId,
    val pluginVersion: String,
    val resolvedAtEpochMillis: Long,
    val stories: List<Story>,
    val entries: List<CatalogEntry>,
)

data class CatalogSearchSummaryCommitResult(
    val sourceStoryIds: Map<SourceKey, StoryId>,
)

interface CatalogRepository {
    suspend fun commitSearchSummaries(
        mutation: CatalogSearchSummaryMutation,
    ): Outcome<CatalogSearchSummaryCommitResult, CatalogStoreFailure>
}
```

- `CatalogSearchStory` carries canonical presentation, not a provider-selected title/cover/score:

```kotlin
data class CatalogSearchStory(
    val story: Story,
    val presentation: CatalogStoryProjection,
    val sources: List<CatalogSearchSourceCard>,
)
```

- `CatalogSearchService` additionally consumes `Clock`, `CanonicalBootstrapUseCase`, and the canonical projection/state types from Tasks 11/16.
- `select(story)` is navigation-only and returns the already canonical `StoryId`; it performs no Details/Full request.

- [ ] **Step 1: Write RED Room tests for Search Summary persistence**

Add exact repository cases:

```text
new Search source -> Story + catalog entry persisted with Summary provenance
existing SourceKey -> durable existing StoryId wins over a new proposed StoryId
Search Summary refresh -> updates Summary facts without clearing existing Full provenance
external identifiers persist with the source
new Story -> story_canonical_state created once and FUSION_REBUILD marked dirty
commit result returns authoritative SourceKey -> StoryId mapping
```

Use two providers whose proposed Story IDs converge through the existing host matcher and assert the committed source-owner mapping is deterministic.

- [ ] **Step 2: Write RED Search service tests for canonical cards**

Required cases:

```text
provider Summary results are committed before card construction
canonical bootstrap runs only from persisted local evidence
persisted existing Story -> active canonical generation owns title/cover/score
new Search Story -> local Summary commit + priority canonical build -> canonical card
provider result order does not change grouped canonical Story/order tie rules
canonical build failure/Preparing -> do not use raw first-source values as emergency canonical presentation
```

Also assert Search itself never invokes:

```text
CatalogMetadataCoordinator.require(key, CatalogMetadataLevel.Full)
CatalogDetailsLoader
```

for card decoration.

- [ ] **Step 3: Write RED selection test that forbids network enrichment**

Given a ready `CatalogSearchStory`:

```kotlin
val result = service.select(story)
assertEquals(CatalogSearchSelectionResult.Success(story.story.id), result)
assertEquals(0, metadataFullCalls)
```

Delete the old expectation that first-source failure/second-source success is part of Search selection; Full fallback belongs to Story-detail lifecycle in Task 37.

- [ ] **Step 4: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.search.CatalogSearchServiceTest
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCatalogRepositoryTest
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.search.SearchViewModelTest
```

- [ ] **Step 5: Implement `commitSearchSummaries()` by reusing Summary merge semantics**

Do not copy a second metadata merge policy into Search SQL. Extract/reuse the same source-entry Summary merge primitive already used by Home so:

1. an existing `(pluginId, sourceId)` keeps its durable current Story ownership;
2. a new source uses the host-proposed StoryId;
3. Summary refresh does not erase richer Full provenance/value state contrary to the metadata-lifecycle contract;
4. identifiers are replaced/upserted deterministically for the committed source revision;
5. canonical state exists for every newly created Story;
6. affected canonical Stories get coalesced Fusion dirty work;
7. the transaction returns authoritative `SourceKey -> StoryId` ownership.

No Home section/snapshot row is created by Search.

Update every existing `CatalogRepository` test fake (`CatalogMetadataCoordinatorTest`, Home/Details/Search service tests, `CatalogRepositoryContractTest`, and Discover/Search/Story ViewModel tests) with a deterministic `commitSearchSummaries()` implementation. Fakes not exercising Search return an explicit bounded test failure/empty mutation result chosen by that test fixture; none may silently skip required owner mapping in a test that calls Search.

- [ ] **Step 6: Rework Search projection around the committed canonical IDs**

For each provider page, keep the existing deterministic host matching/index flow to form proposed `Story`/`CatalogEntry` facts, commit those facts, then regroup source cards using the commit result's durable Story IDs.

For each unique visible StoryId:

```text
CanonicalBootstrapUseCase.ensureReady(storyId)
    -> Ready: use Ready.toProjection()
    -> Preparing/DEGRADED without a valid generation: omit card and record bounded canonicalization failure
```

Do not build a feature-local transient provider-first presentation and do not call Details to make a card prettier.

- [ ] **Step 7: Make selection navigation-only**

Replace the old `story.sources.firstOrNull() -> metadata.require(Full)` path with:

```kotlin
suspend fun select(story: CatalogSearchStory): CatalogSearchSelectionResult =
    CatalogSearchSelectionResult.Success(story.story.id)
```

The Story route/lifecycle owns any explicit Full requirement after navigation.

- [ ] **Step 8: Make SearchResultCard consume only canonical presentation**

Title, cover, score, status, and other canonical fields come from `CatalogStoryProjection`. Raw `sources` remain available only for source inspection/debug semantics; their list order is not presentation truth.

- [ ] **Step 9: Verify Catalog + Room + feature**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.search.CatalogSearchServiceTest
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCatalogRepositoryTest
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.search.SearchViewModelTest \
  --tests app.openstory.catalog.ui.search.SearchScreenshotTest
```

- [ ] **Step 10: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/repository \
  catalog/src/main/kotlin/app/openstory/catalog/search \
  catalog/src/test/kotlin/app/openstory/catalog/search \
  storage/room/src/main/kotlin/app/openstory/storage/room/catalog \
  storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/search \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt
git commit -m "search: persist summaries and navigate canonical stories"
```

---

### Task 19: Replace Discover's feature-local fusion with canonical projections

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverSemanticContent.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverProjectionPipeline.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverProjectionTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverProjectionPipelineTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt`

**Interfaces:**
- Discover feed semantics/ranking still originate from Home Summary/cache feed kinds.
- Presentation metadata for Story IDs comes from canonical projection/generation.
- Discover never invokes Details to repair cards.

- [ ] **Step 1: Write RED test showing local `presentationOrder` no longer owns title/cover/status/score**

Construct Home contributions where local legacy order would pick source A but canonical projection says source B/title and aggregated score. Expected Discover item uses canonical projection.

- [ ] **Step 2: Write RED test preserving feed semantics**

Verify:
- POPULAR ranking still uses Home feed/popularity semantics;
- LATEST order still uses feed latest-update contribution timestamps;
- TOP_RATED still uses intended aggregate ranking feed behavior where required;
- only the **presentation fields** stop being locally fused.

This prevents accidental loss of Discover semantic feed behavior while removing provider truth duplication.

- [ ] **Step 3: Run RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.discover.DiscoverProjectionTest \
  --tests app.openstory.catalog.ui.discover.DiscoverProjectionPipelineTest
```

- [ ] **Step 4: Remove local field fusion**

Delete/reduce:
- `presentationOrder` as canonical metadata selector;
- `List<CatalogEntry>.toPresentationItem()` field-picking logic;
- local highest-score/status/cover choice.

Keep only feed contribution selection/ranking and join with canonical projections keyed by `StoryId`.

- [ ] **Step 5: Assert no Details enrichment dependency**

Add a constructor-boundary test in `DiscoverViewModelTest` that inspects `DiscoverViewModel::class.java.declaredConstructors` and fails if any parameter type is `CatalogMetadataCoordinator`, `CatalogDetailsLoader`, `CatalogFusionEngine`, or `CanonicalFusionService`. The production Discover pipeline must receive canonical projections only; it cannot own a Full/fusion dependency to fill card fields.

- [ ] **Step 6: Verify visual/semantic regression**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests 'app.openstory.catalog.ui.discover.*'
```

If canonical score presentation changes because `CanonicalScore` is normalized, update the UI formatter to display the host canonical normalized value on a stable `/10` presentation scale **only in presentation formatting**, without changing stored canonical normalized semantics.

- [ ] **Step 7: Commit**

```bash
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover
git commit -m "discover: read canonical story presentation"
```

---

### Task 20: Migrate Library/Home/Downloads shared projections to canonical generation truth

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryViewModel.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/library/LibraryViewModelTest.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardProjector.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardViewModel.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardProjectorTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardViewModelTest.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsViewModel.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/downloads/DownloadsViewModelTest.kt`

**Interfaces:**
- Consumers continue to use `CatalogStoryProjectionRepository`; Task 16 has made it canonical-generation based.
- No feature imports raw Room entities or reconstructs source choice.

- [ ] **Step 1: Add RED cross-source Library test**

Library contains Story A; raw source order says title A1/cover A1 while canonical generation says title A2/cover A2/status. Expected Library UI uses canonical projection.

- [ ] **Step 2: Audit shared projection consumers**

Run:

```bash
rg -n "CatalogStoryProjectionRepository|CatalogStoryProjection|projectCatalogStory|\\.entries" \
  feature/catalog/src/main/kotlin
```

For each consumer:
- keep canonical projection use;
- remove any subsequent provider-source recomputation.

- [ ] **Step 3: Update canonical score formatting**

Where projection consumers need a score, use `CanonicalScore.normalizedValue`; presentation may format `normalizedValue * 10.0` as `/10` consistently. The domain value remains normalized.

- [ ] **Step 4: Verify**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.library.LibraryViewModelTest \
  --tests app.openstory.catalog.ui.dashboard.HomeDashboardViewModelTest \
  --tests app.openstory.catalog.ui.downloads.DownloadsViewModelTest
```

- [ ] **Step 5: Commit**

```bash
git add feature/catalog
git commit -m "catalog-ui: unify shared canonical projections"
```

---

### Task 21: Add phase-2 canonical consistency and UI-read-path performance gates

**Files:**
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/CanonicalPresentationConsistencyTest.kt`

**Interfaces:**
- Produces acceptance gate that one active generation yields the same title/cover/status/score semantics across Story/Search/Discover/Library.

- [ ] **Step 1: Write a pure/feature consistency test**

Build one canonical fixture and feed it through the four feature projectors/ViewModels. Assert identical canonical presentation fields.

Source inspection is explicitly excluded from this equality because it intentionally shows raw provider values.

- [ ] **Step 2: Add a UI-read-path guard**

In `CanonicalPresentationConsistencyTest`, reflect over constructors of the Story/Search/Discover/Library ViewModels/projectors participating in the fixture and fail if any parameter type is `CatalogFusionEngine` or `CanonicalFusionService`. Then build the fixture with only `CatalogStoryProjection`/`CanonicalStoryState.Ready` inputs; if a projector tries to require raw source records for presentation, the test setup cannot satisfy it.

The steady read path must be:

```text
UI -> active canonical generation/projection
```

not:

```text
UI -> all source entries -> fuse
```

- [ ] **Step 3: Run full Phase-2 unit gates**

```bash
./gradlew \
  :catalog:testDebugUnitTest \
  :feature:catalog:testDebugUnitTest \
  :app:testDebugUnitTest
```

- [ ] **Step 4: Run connected navigation smoke**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.AppLaunchSmokeTest,app.openstory.navigation.AppNavigationTest \
  --stacktrace
```

- [ ] **Step 5: Run the two existing macrobenchmarks whose read paths changed in Phase 2**

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#homeDiscoverHome' \
  --stacktrace
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#storyTabs' \
  --stacktrace
```

Record the benchmark JSON/trace locations with phase verification evidence. If results regress beyond normal device variance, inspect the trace for repeated source reads/fusion before changing correctness policy.

- [ ] **Step 6: Phase-2 gate**

```bash
./scripts/verify.sh
```

Expected: GREEN before reconciliation implementation begins.

- [ ] **Step 7: Commit**

```bash
git add feature/catalog/src/test/kotlin/app/openstory/catalog/ui/CanonicalPresentationConsistencyTest.kt
git commit -m "test: gate canonical presentation consistency"
```

---

## Phase 3 — Reconciliation Engine in Observe-Only Mode

### Task 22: Define versioned reconciliation policy, symmetric evidence, decisions, and reason codes

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/ReconciliationPolicy.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/ReconciliationModels.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/ReconciliationEvidenceFactory.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/identity/CatalogStoryIdFactory.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/ReconciliationModelsTest.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/identity/CatalogStoryIdFactoryTest.kt`

**Interfaces:**
- Consumes: `CatalogSourceRecord`, `SourceKey`, `ExternalIdentifier`, normalized comparison values from Tasks 2–3.
- Produces: `ReconciliationPolicy`, `ReconciliationEvidence`, `ReconciliationAssessment`, `ReconciliationCaseKey`, semantic/merge-eligibility enums, and reason-code vocabulary consumed by Tasks 23–25 and review UI.

The v1 policy constants are fixed by the spec and are not silently retuned during this task:

```kotlin
const val RECONCILIATION_POLICY_VERSION = 1

data class ReconciliationPolicy(
    val version: Int = RECONCILIATION_POLICY_VERSION,
    val autoTitleSimilarityAt: Double = 0.92,
    val reviewTitleSimilarityAt: Double = 0.75,
    val autoAuthorSimilarityAt: Double = 0.50,
    val minimumWinningLead: Double = 0.05,
)
```

Define the domain vocabulary explicitly:

```kotlin
enum class ReconciliationSemanticDecision {
    SAME_WORK,
    REVIEW,
    DIFFERENT_WORK,
    NO_MATCH,
}

enum class ReconciliationMergeEligibility {
    MERGEABLE,
    INVARIANT_BLOCKED,
}

enum class ReconciliationReasonCode {
    DIRECT_SOURCE_OWNER,
    WORK_IDENTIFIER_MATCH,
    WORK_IDENTIFIER_CONFLICT,
    CONTENT_TYPE_MATCH,
    CONTENT_TYPE_CONFLICT,
    LINEAGE_COMPATIBLE,
    LINEAGE_CONFLICT,
    TITLE_EXACT,
    TITLE_SIMILAR,
    AUTHOR_MATCH,
    AUTHOR_MISSING,
    AUTHOR_CONFLICT,
    WINNING_LEAD_TOO_SMALL,
    TITLE_ONLY_NOT_AUTO,
    DURABLE_SEPARATION_BLOCK,
}

data class ReconciliationEvidence(
    val sourceKey: SourceKey,
    val currentStoryId: StoryId?,
    val contentType: ContentType,
    val comparisonTitles: Set<String>,
    val comparisonAuthors: Set<String>,
    val identifiers: Set<ExternalIdentifier>,
    val lineageTokens: Set<String>,
    val identityEvidenceFingerprint: String,
)

data class ReconciliationAssessment(
    val policyVersion: Int,
    val semanticDecision: ReconciliationSemanticDecision,
    val mergeEligibility: ReconciliationMergeEligibility,
    val confidence: Double,
    val titleSimilarity: Double?,
    val authorSimilarity: Double?,
    val winningLead: Double?,
    val matchedIdentifiers: Set<ExternalIdentifier>,
    val conflictingIdentifiers: Set<ExternalIdentifier>,
    val reasons: Set<ReconciliationReasonCode>,
    val identityEvidenceFingerprint: String,
)
```

`lineageTokens` is intentionally empty for current providers unless the host protocol later contains explicit lineage evidence. Do not infer sequel/adaptation lineage from title words in v1.

Define one conversion boundary and one deterministic Story factory:

```kotlin
object ReconciliationEvidenceFactory {
    fun fromRecord(record: CatalogSourceRecord): ReconciliationEvidence

    fun incoming(
        sourceKey: SourceKey,
        contentType: ContentType,
        titles: Set<String>,
        authors: Set<String>,
        identifiers: Set<ExternalIdentifier>,
        lineageTokens: Set<String> = emptySet(),
    ): ReconciliationEvidence
}

class CatalogStoryIdFactory {
    fun create(
        evidence: ReconciliationEvidence,
        existingStoryIds: Set<StoryId>,
    ): Story
}
```

`CatalogStoryIdFactory` keeps the current SHA-256/8-byte deterministic semantic hash inputs—content type, normalized title signature, normalized author signature, and sorted SourceKey identity—so this architecture change does not casually churn unlinked Story IDs. It adds numeric suffixes (`:2`, then `:3`, continuing monotonically) only on an actual ID collision.

- [ ] **Step 1: Write RED model-invariant tests**

Add tests proving:

```kotlin
@Test
fun unorderedCaseKeyCanonicalizesStoryOrder() {
    val left = StoryId("story-b")
    val right = StoryId("story-a")
    assertEquals(
        ReconciliationCaseKey(StoryId("story-a"), StoryId("story-b")),
        ReconciliationCaseKey.of(left, right),
    )
}

@Test
fun policyKeepsExistingMatcherThresholds() {
    val policy = ReconciliationPolicy()
    assertEquals(0.92, policy.autoTitleSimilarityAt)
    assertEquals(0.75, policy.reviewTitleSimilarityAt)
    assertEquals(0.50, policy.autoAuthorSimilarityAt)
    assertEquals(0.05, policy.minimumWinningLead)
}
```

Also require `ReconciliationCaseKey.of(a, a)` to fail because self-pairs are not review cases.

- [ ] **Step 2: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.reconciliation.ReconciliationModelsTest
```

Expected: FAIL because reconciliation contracts do not exist.

- [ ] **Step 3: Write RED deterministic StoryId factory tests**

Use the same content type/title/author/SourceKey fixture currently covered by `StoryMatcher` and assert the new factory returns the same base `catalog:<16-hex>` ID; pre-populate that ID and assert the next result is `catalog:<16-hex>:2`. Also assert provider-input ordering does not alter the ID.

- [ ] **Step 4: Implement contracts, evidence conversion, and StoryId creation while keeping normalization helpers in their current package**

Reuse `app.openstory.catalog.matching.TitleNormalizer` from the new reconciliation code; do not move it. `ReconciliationEvidenceFactory.fromRecord()` must use the persisted comparison values/fingerprints from `CatalogSourceRecord`, while `incoming()` normalizes only the supplied incoming facts. Leave `StoryMatcher.kt` production behavior unchanged in this task; it remains characterization-only until Task 25 cuts the runtime ingest path over.

- [ ] **Step 5: Run GREEN plus legacy matcher tests**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.reconciliation.ReconciliationModelsTest \
  --tests app.openstory.catalog.identity.CatalogStoryIdFactoryTest \
  --tests app.openstory.catalog.matching.StoryMatcherTest
```

Expected: PASS; thresholds and unlinked StoryId semantics remain unchanged.

- [ ] **Step 6: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/reconciliation \
  catalog/src/main/kotlin/app/openstory/catalog/identity/CatalogStoryIdFactory.kt \
  catalog/src/test/kotlin/app/openstory/catalog/reconciliation \
  catalog/src/test/kotlin/app/openstory/catalog/identity/CatalogStoryIdFactoryTest.kt
git commit -m "feat: define canonical reconciliation contracts"
```

---

### Task 23: Implement candidate discovery, hard conflict gates, symmetric assessment, and winning-candidate lead

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogCandidateIndex.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationEngine.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/CatalogCandidateIndexTest.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationEngineTest.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogIngestReconciliationIndex.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/CatalogIngestReconciliationIndexTest.kt`

**Interfaces:**
- Consumes: Task-22 models and Task-3 normalized evidence.
- Produces:

```kotlin
interface CatalogCandidateIndex {
    fun rebuild(records: Collection<ReconciliationEvidence>)
    fun upsert(record: ReconciliationEvidence)
    fun remove(sourceKey: SourceKey)
    fun candidatesFor(incoming: ReconciliationEvidence): List<StoryId>
}

enum class IncomingSourceAction { DIRECT_OWNER, AUTO_LINK, CREATE_SEPARATE, CREATE_FOR_REVIEW }

sealed interface IncomingSourceResolution {
    val action: IncomingSourceAction

    data class Existing(
        val storyId: StoryId,
        override val action: IncomingSourceAction,
        val assessment: ReconciliationAssessment?,
    ) : IncomingSourceResolution

    data class Create(
        val story: Story,
        override val action: IncomingSourceAction,
        val reviewCandidateStoryId: StoryId?,
        val assessment: ReconciliationAssessment?,
    ) : IncomingSourceResolution
}

class CatalogIngestReconciliationIndex(
    engine: CatalogReconciliationEngine,
    storyIdFactory: CatalogStoryIdFactory,
    records: List<ReconciliationEvidence>,
) {
    fun resolve(incoming: ReconciliationEvidence): IncomingSourceResolution
    fun fork(): CatalogIngestReconciliationIndex
}

class CatalogReconciliationEngine(
    private val policy: ReconciliationPolicy,
) {
    fun assessPair(
        left: ReconciliationEvidence,
        right: ReconciliationEvidence,
    ): ReconciliationAssessment

    fun rankCandidates(
        incoming: ReconciliationEvidence,
        candidates: List<ReconciliationEvidence>,
    ): ReconciliationCandidateSelection
}

data class RankedReconciliationCandidate(
    val storyId: StoryId,
    val assessment: ReconciliationAssessment,
)

data class ReconciliationCandidateSelection(
    val ranked: List<RankedReconciliationCandidate>,
    val semanticDecision: ReconciliationSemanticDecision,
    val mergeEligibility: ReconciliationMergeEligibility,
    val winningLead: Double?,
    val reasons: Set<ReconciliationReasonCode>,
)
```

Candidate discovery is high recall only. `CatalogCandidateIndex` is keyed by `SourceKey`, so `upsert()` replaces stale tokens/identifiers for one source rather than accumulating old evidence; `remove()` removes that source's index contribution. Exact `SourceKey` ownership is resolved before semantic matching. `CatalogIngestReconciliationIndex.resolve()` mutates only that in-memory ingest session: every unowned incoming source is registered under the returned existing/new Story before the method returns, so later items in the same fork can see it. Discarding the fork discards those registrations.

`CatalogReconciliationEngine.rankCandidates()` must collapse multiple source-level evidence records that currently resolve to the same candidate Story into **one** `RankedReconciliationCandidate` for that Story. It assesses all incoming↔candidate-source pairs, keeps the strongest deterministic assessment for that Story, then applies best-vs-runner-up lead across distinct Story IDs. A Story with three catalog sources must not appear as three competing candidates and artificially reduce the winning lead.

- [ ] **Step 1: Write RED candidate-index tests**

Cover:

```text
same WORK identifier -> candidate
title/alias token overlap -> candidate
author-only weak overlap may shortlist but cannot decide
unrelated source -> absent
upsert of the same SourceKey replaces old title/identifier tokens
remove(SourceKey) removes only that source's contribution
candidate ordering is stable by StoryId after equal retrieval strength
```

Do not assert semantic merge decisions in candidate-index tests.

Also write `CatalogIngestReconciliationIndexTest` proving:

```text
existing SourceKey -> DIRECT_OWNER before semantic matching
unowned source + eligible SAME_WORK candidate -> AUTO_LINK existing Story, no temporary Story
unowned source + REVIEW -> CREATE_FOR_REVIEW with a new deterministic Story
unowned source + NO_MATCH/DIFFERENT_WORK -> CREATE_SEPARATE
fork + resolve in one page lets a later item see the earlier local resolution
discarding a fork leaves the parent index unchanged
```

- [ ] **Step 2: Write RED engine fixtures for hard gates and title-only safety**

At minimum:

```kotlin
@Test
fun titleOnlyExactMatchNeverAutoMerges() { /* expect REVIEW + MERGEABLE */ }

@Test
fun compatibleWorkIdentifierCanAutoWithoutAuthor() { /* SAME_WORK */ }

@Test
fun incompatibleContentTypeBlocksStrongIdentifier() { /* REVIEW + INVARIANT_BLOCKED */ }

@Test
fun contradictoryWorkIdentifiersCannotBeOutvotedByTitleAndAuthor() { /* blocked review */ }

@Test
fun clearDifferentContentTypeWithoutPositiveIdentityEvidenceSeparates() { /* DIFFERENT_WORK */ }
```

- [ ] **Step 3: Add symmetry and provider-order invariance tests**

```kotlin
@Test
fun pairAssessmentIsSymmetric() {
    val ab = engine.assessPair(a, b)
    val ba = engine.assessPair(b, a)
    assertEquals(ab.semanticDecision, ba.semanticDecision)
    assertEquals(ab.mergeEligibility, ba.mergeEligibility)
    assertEquals(ab.confidence, ba.confidence)
    assertEquals(ab.reasons, ba.reasons)
}
```

The test fixture must use different plugin IDs and then swap them; provider names must never affect result.

- [ ] **Step 4: Add winning-lead tests**

Construct incoming X with:

```text
candidate A confidence 0.95
candidate B confidence 0.94
```

and require `ReconciliationCandidateSelection` to downgrade to semantic `REVIEW` + `MERGEABLE` because lead `< 0.05`.

Also test a clear lead such as 0.96 vs 0.80 preserves an otherwise eligible auto path. Add a multi-source candidate fixture where two evidence rows belong to Story A and one to Story B; assert `rankCandidates()` emits A exactly once and computes winning lead against B, not against A's second source row.

- [ ] **Step 5: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.reconciliation.CatalogCandidateIndexTest \
  --tests app.openstory.catalog.reconciliation.CatalogIngestReconciliationIndexTest \
  --tests app.openstory.catalog.reconciliation.CatalogReconciliationEngineTest
```

Expected: FAIL.

- [ ] **Step 6: Implement minimal deterministic index and engine**

Use the current title similarity implementation as the fallback baseline instead of inventing a new metric. Required decision ordering is:

```text
1. evaluate hard semantic conflicts
2. evaluate compatible/conflicting WORK identifiers
3. evaluate content type / explicit lineage facts
4. evaluate title/alias similarity
5. evaluate author similarity when present
6. calculate confidence/ranking
7. apply winning-lead rule to `ReconciliationCandidateSelection`
8. return semantic result only; `CatalogReconciliationService` later maps persistence action (`AUTO_LINK` versus `AUTO_MERGE`)
```

Tier-4 metadata such as genres must not appear in an auto predicate.

- [ ] **Step 7: Run GREEN and legacy index tests**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.reconciliation.CatalogCandidateIndexTest \
  --tests app.openstory.catalog.reconciliation.CatalogIngestReconciliationIndexTest \
  --tests app.openstory.catalog.reconciliation.CatalogReconciliationEngineTest \
  --tests app.openstory.catalog.matching.CatalogMatchIndexTest \
  --tests app.openstory.catalog.matching.StoryMatcherTest
```

- [ ] **Step 8: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/reconciliation \
  catalog/src/test/kotlin/app/openstory/catalog/reconciliation
git commit -m "feat: add deterministic catalog reconciliation engine"
```

---

### Task 24: Add durable case/revision semantics and an observe-only reconciliation service

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/ReconciliationCaseRepository.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationService.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationServiceTest.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomReconciliationCaseRepository.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepositoryTest.kt`

**Interfaces:**
- Consumes: Room case schema from Task 9, engine/index/evidence factory from Tasks 22–23, `CatalogRepository` source records, and `StoryIdentityRepository` from Phase 1.
- Produces exact application contract:

```kotlin
enum class ReconciliationCaseStatus {
    PENDING,
    RESOLVED_MERGED,
    RESOLVED_SEPARATE,
    SUPERSEDED,
}

enum class ReconciliationResolutionOrigin { ENGINE, USER }

data class ReconciliationCase(
    val id: String,
    val key: ReconciliationCaseKey,
    val status: ReconciliationCaseStatus,
    val assessment: ReconciliationAssessment,
    val evidenceFingerprint: String,
    val policyVersion: Int,
    val resolutionOrigin: ReconciliationResolutionOrigin?,
    val contextualPromptSuppressedUntilEpochMillis: Long?,
    val revision: Long,
)

interface ReconciliationCaseRepository {
    fun observePending(): Flow<List<ReconciliationCase>>
    fun observeForStory(storyId: StoryId): Flow<List<ReconciliationCase>>
    suspend fun findActive(key: ReconciliationCaseKey): ReconciliationCase?
    suspend fun recordAssessment(
        key: ReconciliationCaseKey,
        assessment: ReconciliationAssessment,
        evaluatedAtEpochMillis: Long,
    ): ReconciliationCase?
    suspend fun resolveSeparate(
        caseId: String,
        expectedRevision: Long,
        origin: ReconciliationResolutionOrigin,
        resolvedAtEpochMillis: Long,
    ): Boolean
    suspend fun defer(
        caseId: String,
        expectedRevision: Long,
        suppressUntilEpochMillis: Long,
    ): Boolean
}

sealed interface ReconciliationRunResult {
    data object NoIdentityChange : ReconciliationRunResult
    data class AutoMergeObserved(val left: StoryId, val right: StoryId) : ReconciliationRunResult
    data class ReviewRecorded(val caseId: String) : ReconciliationRunResult
    data object Separated : ReconciliationRunResult
}

class CatalogReconciliationService(
    private val catalog: CatalogRepository,
    private val identity: StoryIdentityRepository,
    private val candidateIndex: CatalogCandidateIndex,
    private val engine: CatalogReconciliationEngine,
    private val cases: ReconciliationCaseRepository,
    private val clock: Clock,
) {
    suspend fun reconcile(sourceKey: SourceKey): ReconciliationRunResult
    suspend fun reevaluateStory(storyId: StoryId): List<ReconciliationRunResult>
    suspend fun invalidateCandidateIndex()
}
```

Incoming/unowned source `AUTO_LINK` is handled before persistence by `CatalogIngestReconciliationIndex`; this service handles **already persisted source evidence** and therefore observes `AUTO_MERGE` between two existing Stories. In this phase that merge action is observe-only: the service records diagnostics/case state but never invokes destructive merge.

- [ ] **Step 1: Write RED persistence-semantic tests in service fakes**

Required cases:

```text
same pair + same fingerprint + same policy -> no new case revision
KEEP_SEPARATE + same fingerprint refresh -> remains resolved, does not reopen
new identity fingerprint -> new assessment revision and may reopen
fusion-only fingerprint change -> service is not invoked by caller; no case mutation
policy version change -> reevaluate identity case
NO_MATCH -> no durable new case
engine-confirmed DIFFERENT_WORK -> durable RESOLVED_SEPARATE/engine revision
REVIEW -> exactly one active case
first reconcile after process start builds candidate index once from persisted records
second reconcile upserts changed evidence and does not call global `sourceRecords()` again
invalidating index causes exactly one lazy rebuild on the next reconciliation
multiple source records resolving to one candidate Story -> one ranked candidate Story
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.reconciliation.CatalogReconciliationServiceTest
```

- [ ] **Step 3: Implement service without a merge executor path**

Pseudo-flow must remain explicit:

```kotlin
private val candidateIndexMutex = Mutex()
private var candidateIndexInitialized = false

suspend fun reconcile(sourceKey: SourceKey): ReconciliationRunResult {
    val changedRecord = catalog.sourceRecord(CatalogMetadataKey(sourceKey.pluginId, sourceKey.sourceId))
        ?: return ReconciliationRunResult.NoIdentityChange
    val canonicalStoryId = identity.resolve(changedRecord.entry.storyId)
    val incoming = ReconciliationEvidenceFactory.fromRecord(changedRecord)

    val candidateStoryIds = candidateIndexMutex.withLock {
        if (!candidateIndexInitialized) {
            candidateIndex.rebuild(
                catalog.sourceRecords().map(ReconciliationEvidenceFactory::fromRecord),
            )
            candidateIndexInitialized = true
        } else {
            candidateIndex.upsert(incoming)
        }
        candidateIndex.candidatesFor(incoming)
    }

    val resolvedCandidateStoryIds = mutableSetOf<StoryId>()
    for (candidateStoryId in candidateStoryIds) {
        val resolved = identity.resolve(candidateStoryId)
        if (resolved != canonicalStoryId) resolvedCandidateStoryIds += resolved
    }

    val candidates = mutableListOf<ReconciliationEvidence>()
    for (candidateStoryId in resolvedCandidateStoryIds.sortedBy(StoryId::value)) {
        for (record in catalog.sourceRecords(candidateStoryId)) {
            candidates += ReconciliationEvidenceFactory.fromRecord(record)
        }
    }

    val ranked = engine.rankCandidates(incoming, candidates)
    return persistObserveOnlyDecision(canonicalStoryId, ranked)
}

suspend fun invalidateCandidateIndex() {
    candidateIndexMutex.withLock {
        candidateIndexInitialized = false
    }
}
```

Do not call Details/Home/Search from this service. The global `CatalogRepository.sourceRecords()` call is allowed only for the lazy in-memory candidate-index rebuild after process start or explicit invalidation; normal evidence events shortlist through `CatalogCandidateIndex` and then load records only for shortlisted canonical Story IDs. `SourceUnlinked` and `StoryMerged` invalidation wiring is added in Tasks 36 and 38.

- [ ] **Step 4: Implement Room revision behavior and duplicate suppression**

Room writes must use the normalized unordered pair key. Historical revisions preserve the historical pair and assessment; active pair state can later be re-keyed during merge.

- [ ] **Step 5: Run unit + Room GREEN**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.reconciliation.CatalogReconciliationServiceTest
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCanonicalCatalogRepositoryTest
```

- [ ] **Step 6: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/reconciliation \
  catalog/src/test/kotlin/app/openstory/catalog/reconciliation \
  storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomReconciliationCaseRepository.kt \
  storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepositoryTest.kt
git commit -m "feat: persist observe-only reconciliation cases"
```

---

### Task 25: Route Summary/Full evidence revisions through observe-only reconciliation and prove adversarial safety

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/home/CatalogRefreshService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/details/CatalogDetailsLoader.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogRepository.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogCommitChange.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogSearchSummaryMutation.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/ReconciliationAdversarialFixtureTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/home/CatalogRefreshServiceTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/details/CatalogDetailsLoaderTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/search/CatalogSearchServiceTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/repository/CatalogRepositoryContractTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/metadata/CatalogMetadataCoordinatorTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/search/SearchViewModelTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt`

**Interfaces:**
- Consumes: identity/fusion fingerprints from Task 3 and observe-only reconciliation service from Task 24.
- Produces repository commit reports that expose persisted evidence-change facts without embedding orchestration policy:

```kotlin
data class CatalogCommitChange(
    val storyId: StoryId,
    val sourceKey: SourceKey,
    val identityFingerprintChanged: Boolean,
    val fusionFingerprintChanged: Boolean,
)

data class CatalogHomeCommitResult(
    val changes: List<CatalogCommitChange>,
)

data class CatalogDetailsCommitResult(
    val storyId: StoryId,
    val changes: List<CatalogCommitChange>,
)

data class CatalogSearchSummaryCommitResult(
    val sourceStoryIds: Map<SourceKey, StoryId>,
    val changes: List<CatalogCommitChange>,
)

interface CatalogRepository {
    suspend fun commitHomeRefresh(
        mutation: CatalogHomeMutation,
    ): Outcome<CatalogHomeCommitResult, CatalogStoreFailure>

    suspend fun commitDetails(
        mutation: CatalogDetailsMutation,
    ): Outcome<CatalogDetailsCommitResult, CatalogStoreFailure>

    suspend fun commitSearchSummaries(
        mutation: CatalogSearchSummaryMutation,
    ): Outcome<CatalogSearchSummaryCommitResult, CatalogStoreFailure>
}
```

Home, Details, and Search services consume these reports. In this task they temporarily route the same persisted facts directly to observe-only reconciliation/Fusion; Task 36 replaces that direct routing with the shared orchestrator without changing repository semantics. Because the existing `CatalogRepository` return types change in this task, update every repository fake listed in **Files** to return the new result wrappers; tests that do not exercise a commit path still return deterministic empty `changes`, while tests that do exercise it must report the exact persisted Story/source change facts.

Production **incoming source ownership** also cuts over in this task: Home, Search, and the unowned-Details path construct incoming evidence through `ReconciliationEvidenceFactory.incoming` and resolve it through a forked `CatalogIngestReconciliationIndex`. `DIRECT_OWNER`/`AUTO_LINK` reuse an existing Story immediately; `CREATE_FOR_REVIEW`/`CREATE_SEPARATE` create the deterministic host Story before persistence. The old `StoryMatcher`/`CatalogMatchIndex` may remain only as compatibility/characterization code after this cutover and must have zero runtime call sites in these three services.

- [ ] **Step 1: Extend repository-contract RED tests for independent fingerprint change reporting**

Verify:

```text
score/cover/status/latestUpdate only -> fusion=true, identity=false
alias/author/WORK identifier/contentType identity evidence -> identity=true
same semantic evidence with later fetchedAt -> both false unless freshness boundary changes fusion semantics
existing source receiving richer Full metadata can report identity=true even though it already owns StoryId
Search Summary commit reports the same change facts as Home Summary commit
```


- [ ] **Step 2: Write RED incoming-source ownership tests that enforce §14.7**

In Home/Search/Details tests, use synthetic providers and assert:

```text
known SourceKey -> current durable owner; no semantic rematch can create another owner
unowned source + compatible WORK identifier candidate -> direct AUTO_LINK to existing Story before commit
unowned title/author candidate meeting legacy v1 auto thresholds -> direct AUTO_LINK before commit
unowned REVIEW candidate -> new deterministic Story is committed, then one durable review case is recorded against the candidate
unowned NO_MATCH/DIFFERENT_WORK -> new deterministic Story, no graph merge request
two same-work items resolved inside one forked source page see the local first resolution
invalid page/failed commit discards the fork and does not poison the parent ingest index
```

Add a source-call-site guard in the same tests so `CatalogRefreshService`, `CatalogSearchService`, and the unowned branch of `CatalogDetailsLoader` no longer depend on `StoryMatcher.resolve()`/legacy `CatalogMatchIndex.resolve()`. This is the gate that prevents creating a temporary duplicate Story merely to merge it later.

- [ ] **Step 3: Add Details regression test for the current retroactive-link bug**

Create two persistent Stories that were split on sparse Summary. Persist richer Full metadata for one. Assert observe-only reconciliation reconciles the changed source under its **existing owner** and records `AutoMergeObserved`/review instead of bypassing matching because `metadataSnapshot(key)` exists.

No graph merge should happen in this task.

- [ ] **Step 4: Add adversarial reconciliation fixtures**

Cover at least:

```text
same title, different content type
sequel-like near-identical title
alternate edition same work identifier
transliteration differences
same author with unrelated title
missing authors with exact title only
same compatible WORK identifier without authors
conflicting WORK identifiers
same provider-record identifier value in different namespaces/scopes
two close candidates inside 0.05 lead
provider-order permutation
```

For every fixture assert semantic decision, merge eligibility, winning lead where relevant, and reason codes—not just a floating score.

- [ ] **Step 5: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.details.CatalogDetailsLoaderTest \
  --tests app.openstory.catalog.repository.CatalogRepositoryContractTest \
  --tests app.openstory.catalog.reconciliation.ReconciliationAdversarialFixtureTest
```

- [ ] **Step 6: Implement incoming ownership cutover plus persisted change reporting for Home, Details, and Search**

First replace runtime incoming ownership in Home/Search/unowned Details with `CatalogIngestReconciliationIndex`; preserve the existing fork/commit discipline so a failed provider page cannot mutate the parent ingest session. `CatalogMatchIndex`/`StoryMatcher` stay only for legacy characterization tests after runtime call sites are removed.

Then make `RoomCatalogRepository` compute fingerprints from the committed before/after source state and return `CatalogCommitChange`; service code must not reconstruct fingerprints from provider DTOs. For every change returned by `commitHomeRefresh()`, `commitDetails()`, or `commitSearchSummaries()`, the temporary runtime rule must read as:

```kotlin
if (change.identityFingerprintChanged) {
    reconciliation.reconcile(change.sourceKey)
}
if (change.fusionFingerprintChanged) {
    fusion.rebuild(change.storyId, CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED)
}
```

This temporary direct routing is replaced by Task 36's orchestrator; do not make it fetch anything.

- [ ] **Step 7: Run Phase-3 catalog gate**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.home.CatalogRefreshServiceTest \
  --tests app.openstory.catalog.details.CatalogDetailsLoaderTest \
  --tests app.openstory.catalog.search.CatalogSearchServiceTest \
  --tests app.openstory.catalog.reconciliation.*
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.discover.DiscoverViewModelTest \
  --tests app.openstory.catalog.ui.search.SearchViewModelTest \
  --tests app.openstory.catalog.ui.story.StoryViewModelTest
```

Expected: observe-only decisions are durable; no destructive Story merge exists yet.

- [ ] **Step 8: Run full phase gate**

```bash
./scripts/verify.sh
```

- [ ] **Step 9: Commit**

```bash
git add catalog/src/main catalog/src/test storage/room/src/main storage/room/src/androidTest \
  feature/catalog/src/test
git commit -m "feat: observe retroactive catalog reconciliation"
```

---

## Phase 4 — Atomic Canonical Story Graph Merge

### Task 26: Define meaningful user-state footprint and deterministic survivor selection

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/identity/StoryMergeModels.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/identity/StoryMergeExecutor.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/identity/StorySurvivorSelector.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/identity/StorySurvivorSelectorTest.kt`

**Interfaces:**
- Consumes: `StoryId`, canonical creation timestamp/state, domain footprint facts.
- Produces:

```kotlin
data class UserStateFootprint(
    val hasLibraryMembership: Boolean,
    val readingProgressCount: Int,
    val protectedContentMappingCount: Int,
    val hasPinnedPrimary: Boolean,
    val manualChapterOverrideCount: Int,
) {
    val meaningfulDomainCount: Int
        get() = listOf(
            hasLibraryMembership,
            readingProgressCount > 0,
            protectedContentMappingCount > 0,
            hasPinnedPrimary,
            manualChapterOverrideCount > 0,
        ).count { it }

    val meaningfulStateTotal: Int
        get() = (if (hasLibraryMembership) 1 else 0) +
            readingProgressCount +
            protectedContentMappingCount +
            (if (hasPinnedPrimary) 1 else 0) +
            manualChapterOverrideCount
}

enum class StoryMergeOrigin {
    AUTO_RECONCILIATION,
    USER_REVIEW_APPROVAL,
    MANUAL_MAINTENANCE,
}

data class ProtectedContentMappingConflict(
    val pluginId: PluginId,
    val candidateSourceStoryIds: Set<String>,
)

sealed interface StoryMergeResolution {
    data class ContentMappingTarget(
        val pluginId: PluginId,
        val sourceStoryId: String,
    ) : StoryMergeResolution
}

data class StoryMergeRequest(
    val requestId: String,
    val leftStoryId: StoryId,
    val rightStoryId: StoryId,
    val origin: StoryMergeOrigin,
    val reconciliationCaseId: String?,
    val evidenceFingerprint: String,
    val reconciliationPolicyVersion: Int,
    val resolutions: List<StoryMergeResolution> = emptyList(),
)

data class StoryMergeCandidate(
    val storyId: StoryId,
    val identityRevision: Long,
    val createdAtEpochMillis: Long?,
    val footprint: UserStateFootprint,
)

data class StoryMergeSelection(
    val survivor: StoryMergeCandidate,
    val retired: StoryMergeCandidate,
)

sealed interface StoryMergeResult {
    data class Merged(val survivorStoryId: StoryId, val mergeEventId: String) : StoryMergeResult
    data class AlreadyMerged(val survivorStoryId: StoryId) : StoryMergeResult
    data class ReviewRequired(
        val reasons: Set<String>,
        val protectedContentMappingConflicts: List<ProtectedContentMappingConflict> = emptyList(),
    ) : StoryMergeResult
    data class StalePlan(val currentStoryIds: Set<StoryId>) : StoryMergeResult
}

fun interface StoryMergeExecutor {
    suspend fun execute(request: StoryMergeRequest): StoryMergeResult
}
```

- [ ] **Step 1: Write RED survivor tests**

Require lexicographic priority:

```text
1. higher `meaningfulDomainCount`
2. if equal, higher `meaningfulStateTotal`
3. if still equal and both creation timestamps are trustworthy/non-null, older `createdAtEpochMillis`
4. if either creation time is unknown, skip age comparison entirely
5. stable lexical `StoryId.value` tie-break
```

Do not use provider count, metadata completeness, catalog score, or number of raw catalog rows.

- [ ] **Step 2: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.identity.StorySurvivorSelectorTest
```

- [ ] **Step 3: Implement pure selector**

Use one explicit comparator; do not spread survivor rules into Room coordinator.

- [ ] **Step 4: Run GREEN**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.identity.StorySurvivorSelectorTest
```

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/identity \
  catalog/src/test/kotlin/app/openstory/catalog/identity
git commit -m "feat: add deterministic canonical story survivor policy"
```

---

### Task 27: Implement Library and content-mapping merge policies, including conflicting user pins

**Files:**
- Create: `core/common/src/main/kotlin/app/openstory/common/merge/DomainMergeDecision.kt`
- Create: `library/src/main/kotlin/app/openstory/library/merge/LibraryStoryMergePolicy.kt`
- Create: `library/src/main/kotlin/app/openstory/library/merge/ContentMappingStoryMergePolicy.kt`
- Create: `library/src/test/kotlin/app/openstory/library/merge/LibraryStoryMergePolicyTest.kt`
- Create: `library/src/test/kotlin/app/openstory/library/merge/ContentMappingStoryMergePolicyTest.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/canonical/CanonicalSourcePreferenceMergePolicy.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/canonical/CanonicalSourcePreferenceMergePolicyTest.kt`

**Interfaces:**
- Produces pure plans only; no Room dependency.
- `DomainMergeDecision` is owned by `:core:common` so domain modules share prepare semantics without cross-domain dependencies:

```kotlin
package app.openstory.common.merge

sealed interface DomainMergeDecision<out T> {
    data class Ready<T>(val value: T) : DomainMergeDecision<T>
    data class RequiresReview(val reasons: Set<String>) : DomainMergeDecision<Nothing>
}

data class LibraryMergePlan(val entry: LibraryEntry?)

class LibraryStoryMergePolicy {
    fun plan(
        survivorId: StoryId,
        left: LibraryEntry?,
        right: LibraryEntry?,
    ): LibraryMergePlan
}

data class ContentMappingMergePlan(
    val mappings: List<ContentMapping>,
    val rejections: List<ContentMappingRejection>,
    val pluginsToRecompute: Set<PluginId>,
)

data class ContentMappingMergeResolution(
    val pluginId: PluginId,
    val sourceStoryId: String,
)

data class ContentMappingProtectedConflict(
    val pluginId: PluginId,
    val candidateSourceStoryIds: Set<String>,
)

sealed interface ContentMappingMergeDecision {
    data class Ready(val plan: ContentMappingMergePlan) : ContentMappingMergeDecision
    data class RequiresReview(
        val reasons: Set<String>,
        val protectedConflicts: List<ContentMappingProtectedConflict>,
    ) : ContentMappingMergeDecision
}

class ContentMappingStoryMergePolicy {
    fun plan(
        survivorId: StoryId,
        leftMappings: List<ContentMapping>,
        rightMappings: List<ContentMapping>,
        leftRejections: List<ContentMappingRejection>,
        rightRejections: List<ContentMappingRejection>,
        resolutions: List<ContentMappingMergeResolution> = emptyList(),
    ): ContentMappingMergeDecision
}
```

`CanonicalSourcePreferenceMergePolicy` has these exact outcomes:

```text
AUTO + AUTO -> AUTO
PINNED(X) + AUTO -> PINNED(X)
PINNED(X) + PINNED(X) -> PINNED(X)
PINNED(X) + PINNED(Y) -> REQUIRES_REVIEW
```

- [ ] **Step 1: Write RED Library tests**

Assert:

```text
one entry -> move to survivor
two entries -> earliest addedAt, status from latest updatedAt, latest updatedAt
timestamp tie with different status -> stable deterministic tie-break independent of argument order
```

- [ ] **Step 2: Write RED mapping tests**

Cover all normative cases:

```text
same target automated+protected -> one protected target
protected X vs automated Y -> protected X
automated X vs automated Y -> no arbitrary winner; mark plugin recompute
protected X vs protected Y -> RequiresReview with one typed conflict containing exactly {X, Y}
protected X vs protected Y + explicit resolution X -> Ready with X preserved
protected X vs protected Y + resolution Z not in conflict candidates -> RequiresReview; never invent/accept Z
rejections -> set union/coalesce by plugin/source/policy
argument order -> same plan/typed conflict after stable sorting
```

- [ ] **Step 3: Write RED primary-pin conflict tests**

Conflicting explicit pins must return review rather than letting survivor or quality decide.

- [ ] **Step 4: Run RED**

```bash
./gradlew :library:testDebugUnitTest \
  --tests app.openstory.library.merge.LibraryStoryMergePolicyTest \
  --tests app.openstory.library.merge.ContentMappingStoryMergePolicyTest
./gradlew :catalog:testDebugUnitTest \
  --tests '*CanonicalSourcePreference*'
```

- [ ] **Step 5: Implement pure policies**

Do not add Room queries or identity decisions here. `ContentMappingStoryMergePolicy` owns validation of explicit mapping-target resolutions; the Room layer may translate transport DTOs into `ContentMappingMergeResolution` but may not validate/select the winning target itself.

- [ ] **Step 6: Run GREEN**

```bash
./gradlew :library:testDebugUnitTest \
  --tests app.openstory.library.merge.*
./gradlew :catalog:testDebugUnitTest
```

- [ ] **Step 7: Commit**

```bash
git add core/common/src/main/kotlin/app/openstory/common/merge/DomainMergeDecision.kt \
  library/src/main/kotlin/app/openstory/library/merge \
  library/src/test/kotlin/app/openstory/library/merge \
  catalog/src/main/kotlin/app/openstory/catalog/canonical/CanonicalSourcePreferenceMergePolicy.kt \
  catalog/src/test/kotlin/app/openstory/catalog/canonical/CanonicalSourcePreferenceMergePolicyTest.kt
git commit -m "feat: define user-state merge policies"
```

---

### Task 28: Implement conservative Chapter graph merge policy with stable IDs and resync semantics

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/merge/ChapterStoryMergePolicy.kt`
- Create: `chapters/src/test/kotlin/app/openstory/chapters/merge/ChapterStoryMergePolicyTest.kt`

**Interfaces:**

```kotlin
data class ChapterStoryMergeInput(
    val survivorStoryId: StoryId,
    val retiredStoryId: StoryId,
    val survivorGraph: ChapterGraphSnapshot,
    val retiredGraph: ChapterGraphSnapshot,
    val syncStates: List<ChapterSyncState>,
)

data class ChapterStoryMergePlan(
    val movedCanonicalChapterIds: Set<CanonicalChapterId>,
    val movedReleaseIds: Set<ChapterReleaseId>,
    val preservedOverrides: List<ChapterAggregationOverride>,
    val syncStatesToMove: List<ChapterSyncState>,
    val syncKeysToInvalidate: Set<ChapterSyncKey>,
    val requiresDerivedReaggregation: Boolean,
)

data class ChapterSyncKey(
    val pluginId: PluginId,
    val sourceStoryId: String,
)

class ChapterStoryMergePolicy {
    fun plan(input: ChapterStoryMergeInput): DomainMergeDecision<ChapterStoryMergePlan>
}
```

Import `app.openstory.common.merge.DomainMergeDecision` created in Task 27. Keep the existing `:chapters -> :library` dependency unchanged; do not introduce a new merge-only dependency from `:chapters` to `:catalog`, `:reader`, or Room.

- [ ] **Step 1: Write RED stable-ID tests**

Assert every retired chapter/release ID appears unchanged in the plan. Two canonical chapters both labeled `Chapter 10` remain two IDs; Story merge does not deduplicate them.

- [ ] **Step 2: Write RED override/sync collision tests**

```text
manual FORCE_LINK/FORCE_SEPARATE override is preserved
impossible override collision -> RequiresReview
non-colliding sync state -> move
same post-merge sync key from both sides -> invalidate key, do not invent merged cursor
collision marks derived reaggregation/resync work
```

- [ ] **Step 3: Run RED**

```bash
./gradlew :chapters:testDebugUnitTest \
  --tests app.openstory.chapters.merge.ChapterStoryMergePolicyTest
```

- [ ] **Step 4: Implement lossless policy**

The implementation must not invoke `ChapterAggregationEngine`; it describes authoritative ownership moves and derived work required after commit.

- [ ] **Step 5: Run GREEN plus chapter aggregation regression**

```bash
./gradlew :chapters:testDebugUnitTest \
  --tests app.openstory.chapters.merge.ChapterStoryMergePolicyTest \
  --tests app.openstory.chapters.aggregation.ChapterAggregationEngineTest
```

- [ ] **Step 6: Commit**

```bash
git add chapters/src/main/kotlin/app/openstory/chapters/merge \
  chapters/src/test/kotlin/app/openstory/chapters/merge
git commit -m "feat: preserve chapter graph across story merge"
```

---

### Task 29: Implement Reader progress merge policy without unsafe cross-content comparison

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/progress/ReadingProgressMergePolicy.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/progress/ReadingProgressMergePolicyTest.kt`

**Interfaces:**

```kotlin
data class ReadingProgressMergePlan(
    val progressRows: List<ReadingProgress>,
)

class ReadingProgressMergePolicy {
    fun plan(
        survivorStoryId: StoryId,
        left: List<ReadingProgress>,
        right: List<ReadingProgress>,
    ): DomainMergeDecision<ReadingProgressMergePlan>
}
```

For duplicates of the same `canonicalChapterId`:
- same `contentFingerprint` => choose furthest comparable position/completion, with latest timestamp as deterministic metadata tie-break;
- different fingerprints but same release and clearly comparable current contract => preserve the safer/furthest only if the domain can prove comparability;
- otherwise => `RequiresReview`.

- [ ] **Step 1: Write RED tests**

Cover:

```text
rows for different canonicalChapterIds all survive
same chapter/same fingerprint chooses completed over incomplete
same chapter/same fingerprint chooses larger fraction if neither completed
same chapter/different unsafe fingerprints -> RequiresReview
result StoryId is survivor, chapter/release IDs unchanged
argument order is irrelevant
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests app.openstory.reader.progress.ReadingProgressMergePolicyTest
```

- [ ] **Step 3: Implement minimal safe comparison**

Do not compare fractions across different canonical chapters. Do not reinterpret chapter order here.

- [ ] **Step 4: Run GREEN**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests app.openstory.reader.progress.ReadingProgressMergePolicyTest \
  --tests app.openstory.reader.progress.ReadingProgressServiceTest
```

- [ ] **Step 5: Commit**

```bash
git add reader/src/main/kotlin/app/openstory/reader/progress/ReadingProgressMergePolicy.kt \
  reader/src/test/kotlin/app/openstory/reader/progress/ReadingProgressMergePolicyTest.kt
git commit -m "feat: define safe reading progress merge policy"
```

---
### Task 30: Build Room merge snapshots and prepare/validate every authoritative domain before any write

**Files:**
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryMergeReaders.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryGraphMergePlanner.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/library/LibraryDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/reader/ReadingProgressDao.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/merge/RoomStoryGraphMergePlannerTest.kt`

**Interfaces:**
- Consumes: survivor selector, domain merge policies from Tasks 26–29, and canonical source-preference merge policy.
- Produces an immutable preparation result with no write side effects:

```kotlin
data class StoryGraphVersion(
    val survivorIdentityRevision: Long,
    val retiredIdentityRevision: Long,
)

data class PreparedStoryGraphMerge(
    val request: StoryMergeRequest,
    val survivorStoryId: StoryId,
    val retiredStoryId: StoryId,
    val expectedVersion: StoryGraphVersion,
    val sourceKeysToMove: Set<SourceKey>,
    val sourcePreference: CanonicalSourcePreference,
    val libraryPlan: LibraryMergePlan,
    val mappingPlan: ContentMappingMergePlan,
    val chapterPlan: ChapterStoryMergePlan,
    val progressPlan: ReadingProgressMergePlan,
    val footprintBeforeMerge: Map<StoryId, UserStateFootprint>,
)

sealed interface StoryGraphMergePreparation {
    data class Ready(val plan: PreparedStoryGraphMerge) : StoryGraphMergePreparation
    data class ReviewRequired(
        val reasons: Set<String>,
        val protectedContentMappingConflicts: List<ProtectedContentMappingConflict> = emptyList(),
    ) : StoryGraphMergePreparation
    data class AlreadyCanonical(val survivorStoryId: StoryId) : StoryGraphMergePreparation
}

class RoomStoryGraphMergePlanner {
    suspend fun prepare(request: StoryMergeRequest): StoryGraphMergePreparation
}
```

`RoomStoryMergeReaders` converts DAO rows into domain snapshots only. It does not choose winners, perform merge semantics, mutate rows, or open its own transaction.

- [ ] **Step 1: Write RED preparation test for a catalog-only merge**

Fixture:

```text
A owns source p1/a
B owns source p2/b
no Library/mappings/chapters/progress
```

Call `planner.prepare(request)`. Assert `Ready.plan` selects the deterministic survivor, captures both identity revisions, and includes the retired source membership. Query every involved table before/after `prepare()` and assert no row changed.

- [ ] **Step 2: Write RED `ReviewRequired` preparation tests**

At minimum:

```text
protected mapping X vs protected mapping Y -> ReviewRequired with typed `ProtectedContentMappingConflict(plugin, {X, Y})`
protected mapping X vs protected mapping Y + valid `StoryMergeResolution.ContentMappingTarget(plugin, X)` -> Ready mapping plan
invalid/duplicate content-mapping resolution -> ReviewRequired, no arbitrary winner
PINNED(source A) vs PINNED(source B) -> ReviewRequired
unsafe progress conflict -> ReviewRequired
manual chapter override invariant conflict -> ReviewRequired
```

After every preparation attempt, query the involved tables and assert domain-equivalent pre-state. Planning is read-only even when the result is blocked.

- [ ] **Step 3: Write RED preparation-version and idempotence tests**

Prepare the same request twice over unchanged data and assert equal survivor/retired IDs, equal domain plans, and equal captured `StoryGraphVersion`.

Then mutate only one Story's `identity_revision` and prepare again. Assert the new plan captures the new revision. Do **not** attempt commit in this task; stale-plan rejection belongs to Task 31 where the writer exists.

Also resolve inputs before planning:

```text
request(B, C)
B already redirects to A
-> planner reasons over A and C

request(B, A)
B already redirects to A
-> AlreadyCanonical(A)
```

- [ ] **Step 4: Run RED instrumentation test**

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.merge.RoomStoryGraphMergePlannerTest
```

Expected: compile failure before readers/planner exist.

- [ ] **Step 5: Implement readers and the read-only planner**

Reader queries must cover:

```text
catalog_entries + identifiers
canonical source preference/state
library_entries
content_mappings + rejections
canonical_chapters + chapter_releases + overrides + sync states
reading_progress
```

The planner:
1. resolves redirects through `RoomStoryIdentityResolver`;
2. short-circuits `AlreadyCanonical` when both requested IDs resolve to one Story;
3. reads both identity revisions and meaningful user-state footprints;
4. selects survivor with `StorySurvivorSelector`;
5. maps `StoryMergeResolution.ContentMappingTarget` transport values to Library-owned `ContentMappingMergeResolution` and invokes every domain-owned merge policy;
6. maps Library-owned `ContentMappingProtectedConflict` values back to host-owned `ProtectedContentMappingConflict` only for transport/review presentation; it does not choose or validate targets in Room;
7. returns `ReviewRequired` if any participant blocks;
8. otherwise returns one immutable `PreparedStoryGraphMerge`.

Do not read download chapter storage for ownership planning because it follows stable `ChapterReleaseId`; Task 31 still verifies those rows remain valid after commit.

- [ ] **Step 6: Run GREEN for preparation tests**

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.merge.RoomStoryGraphMergePlannerTest
```

- [ ] **Step 7: Commit**

```bash
git add storage/room/src/main/kotlin/app/openstory/storage/room/merge \
  storage/room/src/main/kotlin/app/openstory/storage/room/catalog \
  storage/room/src/main/kotlin/app/openstory/storage/room/library \
  storage/room/src/main/kotlin/app/openstory/storage/room/chapters \
  storage/room/src/main/kotlin/app/openstory/storage/room/reader \
  storage/room/src/androidTest/kotlin/app/openstory/storage/room/merge/RoomStoryGraphMergePlannerTest.kt
git commit -m "feat: prepare canonical story graph merges"
```

---

### Task 31: Commit Story graph merge atomically with redirects, audit, case/work re-keying, and idempotent concurrency guards

**Files:**
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryGraphMergeCoordinator.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryMergeWriter.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CanonicalCatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/library/LibraryDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/reader/ReadingProgressDao.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/merge/RoomStoryGraphMergeCoordinatorTest.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/DatabaseBaselineTest.kt`

**Interfaces:**
- Consumes: `RoomStoryGraphMergePlanner` / `PreparedStoryGraphMerge` from Task 30 and implements `StoryMergeExecutor`.
- Produces actual `StoryMergeResult.Merged`/`AlreadyMerged` carrying the durable `mergeEventId` defined in Task 26; the audit row remains persistence-owned (`story_merge_events`) and no competing `StoryMergeEvent` domain type is introduced. Redirect lineage is persisted by the same transaction.

```kotlin
class RoomStoryGraphMergeCoordinator(
    private val planner: RoomStoryGraphMergePlanner,
    private val writer: RoomStoryMergeWriter,
) : StoryMergeExecutor {
    override suspend fun execute(request: StoryMergeRequest): StoryMergeResult
}
```

`StoryGraphMergePreparation.ReviewRequired` is translated losslessly to `StoryMergeResult.ReviewRequired`: preserve both stable reason codes and any `ProtectedContentMappingConflict` entries. A request carrying `StoryMergeResolution` is always re-planned from current rows; the writer never applies a resolution that was not validated by the domain policy during that same preparation.

The authoritative transaction ordering is fixed semantically:

```text
1. resolve current IDs and re-check identity revisions
2. apply prepared conflict-resolution deletes/coalesces
3. move catalog source ownership
4. merge/move canonical source preference
5. merge/move Library
6. merge/move mappings + rejections
7. move chapters/releases with stable IDs
8. move overrides/sync state according to plan
9. move/reconcile reading progress
10. validate authoritative post-move invariants
11. write merge audit + per-domain audit payload
12. flatten/create redirects to survivor
13. re-key/coalesce active reconciliation cases
14. re-key/coalesce dirty work and mark survivor Fusion dirty
15. retire losing Story/canonical state
16. bump survivor identity revision
17. commit
```

SQL statement order may differ to satisfy FKs, but no externally valid redirect may survive a rolled-back graph.

- [ ] **Step 1: Write RED full-graph success test**

Create A and B with representative rows in every Story-owned table from schema 8/9. Include chapter storage linked to a release. Execute merge and assert:

```text
one active Story remains
all Catalog SourceKeys belong to survivor
Library policy result is exact
mapping/rejection policy result is exact
canonical chapter IDs unchanged
chapter release IDs unchanged
chapter_storage_entries still resolve release IDs
progress keeps chapter/release IDs
redirect retired -> survivor exists
merge event contains historical A/B text IDs and origin/evidence/policy
reversal payload records retired Story content type, trustworthy creation time when known, original source membership, and only domain-owned pre-merge facts needed for safe reconstruction
retired Story owns no active authoritative state
survivor identity revision incremented
Fusion dirty work exists once for survivor
PRAGMA foreign_key_check returns zero rows
```

- [ ] **Step 2: Write RED rollback test with an injected failure near transaction end**

Add a test-only writer hook or use a fake transaction participant to throw after chapter/progress moves but before redirect/audit completion. After failure, assert **all tables** match pre-merge state and no merge event/redirect exists.

- [ ] **Step 3: Write RED redirect flattening and old-ID tests**

Scenario:

```text
B -> A
then A -> C
```

Expected:

```text
B -> C
A -> C
resolve(B) == C
resolve(A) == C
```

Audit retains the historical first `B -> A` event and second `A -> C` event.

- [ ] **Step 4: Write RED case/work re-key tests**

Before merge:

```text
active case (B, X)
active case (A, X)
pending FUSION work for B
pending RECONCILIATION work for B
```

After B→A:

```text
merge-authorizing case RESOLVED_MERGED
historical case revisions keep original IDs
active relation normalized to (A, X) exactly once
self-pair cases superseded
work remapped/coalesced to A
obsolete B work cannot retry forever
```

- [ ] **Step 5: Write RED idempotency/concurrency tests**

```text
execute same request twice -> second AlreadyMerged, no second audit
execute reversed IDs after first merge -> AlreadyMerged survivor
A+B plan prepared; B+C commits first; stale A+B cannot commit
attempt redirect cycle -> invariant failure, no write
```

- [ ] **Step 6: Run RED**

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.merge.RoomStoryGraphMergeCoordinatorTest
```

- [ ] **Step 7: Implement `RoomStoryMergeWriter` inside `withTransaction`**

Do not embed domain choice logic in SQL. The writer applies already-prepared values and validates uniqueness/FKs/current revisions.

Use one merge-event ID generated before redirect insertion because redirect rows reference that event, but insert event + redirect within the same transaction so rollback removes both.

- [ ] **Step 8: Make `RoomStoryIdentityResolver` redirect-aware for all Story repository boundaries touched by old IDs**

At minimum verify `RoomCatalogRepository`, `RoomCanonicalCatalogRepository`, `RoomLibraryRepository`, `RoomContentMappingRepository`, `RoomChapterRepository`, and `RoomReadingProgressRepository` resolve input StoryId through the centralized resolver before Story-keyed reads/writes. Do not add redirect logic to feature ViewModels.

- [ ] **Step 9: Run GREEN across the complete Room instrumentation suite**

```bash
./gradlew :storage:room:connectedDebugAndroidTest
```

- [ ] **Step 10: Run architecture/static gate**

```bash
./scripts/verify.sh
```

Expected: no Room types leak into domain interfaces and no package-boundary violation.

- [ ] **Step 11: Commit**

```bash
git add storage/room/src/main storage/room/src/androidTest
git commit -m "feat: merge canonical story graph atomically"
```

---

### Task 32: Connect reconciliation to the merge executor and enable destructive `AUTO_MERGE` only behind proven gates

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/identity/StoryMergeExecutor.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationServiceTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/StorageModule.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/CatalogModule.kt`

**Interfaces:**
- Consumes: observe-only reconciliation service and tested Room executor.
- Produces an explicit runtime mode:

```kotlin
enum class ReconciliationExecutionMode {
    OBSERVE_ONLY,
    APPLY_ELIGIBLE_AUTO_MERGES,
}
```

Do not use a provider/config-specific hidden flag. Production wiring moves to `APPLY_ELIGIBLE_AUTO_MERGES` only in the final step of this task after all listed gates pass.

- [ ] **Step 1: Write RED service tests for action mapping**

Required:

```text
existing Story A + existing Story B + eligible SAME_WORK -> executor called exactly once in apply mode
Task-25 incoming AUTO_LINK path remains direct ownership assignment and never calls the Story merge executor
same assessment in observe mode -> executor never called
REVIEW/SEPARATE/NO_MATCH -> executor never called
protected conflict returned by executor -> case becomes pending review, not retry loop
stale merge plan -> reconciliation reschedules reevaluation instead of forcing old plan
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.reconciliation.CatalogReconciliationServiceTest
```

- [ ] **Step 3: Implement action mapping and Hilt wiring with `OBSERVE_ONLY` default**

The service only receives already-persisted source changes, so destructive action here is `AUTO_MERGE` between existing Stories. Keep Task-25 `AUTO_LINK` in the ingest resolver; do not route a new/unowned source through the graph merge executor.

- [ ] **Step 4: Run the mandatory pre-enable gate**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.reconciliation.*
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.merge.RoomStoryGraphMergeCoordinatorTest
./scripts/verify.sh
```

Expected: all GREEN. If any gate fails, **stop with `OBSERVE_ONLY`**; do not enable destructive auto merge.

- [ ] **Step 5: Flip production wiring to `APPLY_ELIGIBLE_AUTO_MERGES` only after Step 4 is green**

Keep a test binding for observe-only fixtures. This is the first task in the plan allowed to execute automatic destructive identity merge.

- [ ] **Step 6: Add one integration test proving a real eligible pair merges through the service and a protected-conflict pair does not**

The test must assert the service reaches the same `RoomStoryGraphMergeCoordinator`; there is no second manual merge implementation.

- [ ] **Step 7: Run Phase-4 gate**

```bash
./gradlew :catalog:testDebugUnitTest :library:testDebugUnitTest :chapters:testDebugUnitTest :reader:testDebugUnitTest
./gradlew :storage:room:connectedDebugAndroidTest
./scripts/verify.sh
```

- [ ] **Step 8: Commit**

```bash
git add catalog/src/main catalog/src/test app/src/main
git commit -m "feat: enable guarded canonical auto merge"
```

---
## Phase 5 — Durable Review Queue and Contextual Resolution

### Task 33: Add one review-resolution application service for MERGE, KEEP_SEPARATE, DEFER, and protected-conflict resolution

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/ReconciliationReviewService.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/ReconciliationReviewServiceTest.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/ReconciliationCaseRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomReconciliationCaseRepository.kt`

**Interfaces:**

```kotlin
enum class ReconciliationReviewAction {
    MERGE,
    KEEP_SEPARATE,
    DEFER,
}

data class ProtectedMappingResolution(
    val pluginId: PluginId,
    val sourceStoryId: String,
)

data class ReconciliationReviewCommand(
    val caseId: String,
    val expectedCaseRevision: Long,
    val action: ReconciliationReviewAction,
    val protectedMappingResolutions: List<ProtectedMappingResolution> = emptyList(),
)

sealed interface ReconciliationReviewResult {
    data class Merged(val survivorStoryId: StoryId) : ReconciliationReviewResult
    data object KeptSeparate : ReconciliationReviewResult
    data class Deferred(val untilEpochMillis: Long) : ReconciliationReviewResult
    data class ConflictResolutionRequired(
        val conflicts: List<ProtectedContentMappingConflict>,
    ) : ReconciliationReviewResult
    data class DomainStateChangeRequired(
        val reasonCodes: Set<String>,
    ) : ReconciliationReviewResult
    data object InvariantBlocked : ReconciliationReviewResult
    data object StaleCase : ReconciliationReviewResult
}

class ReconciliationReviewService(
    private val cases: ReconciliationCaseRepository,
    private val mergeExecutor: StoryMergeExecutor,
    private val clock: Clock,
) {
    suspend fun resolve(command: ReconciliationReviewCommand): ReconciliationReviewResult
}
```

Use the project's existing `Clock` abstraction, not `System.currentTimeMillis()` in domain code.

- [ ] **Step 1: Write RED tests for action semantics**

Required:

```text
MERGE on REVIEW_MERGEABLE -> same StoryMergeExecutor used by auto merge
MERGE on REVIEW_INVARIANT_BLOCKED -> InvariantBlocked, executor not called
MERGE encountering conflicting protected mappings -> ConflictResolutionRequired with exact plugin/target choices, case remains pending
MERGE with explicit valid protected mapping resolution -> executor called with resolution payload/plan context
MERGE blocked by another domain-owned conflict (for example unsafe progress or conflicting primary pins) -> DomainStateChangeRequired with stable reason codes; case remains pending
KEEP_SEPARATE -> durable RESOLVED_SEPARATE with USER origin and current fingerprint/policy
DEFER -> case remains PENDING, suppression timestamp changes, and it remains returned by Review Queue query
stale expectedCaseRevision -> StaleCase, no side effect
repeated completed command -> idempotent result/no duplicate merge event
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.reconciliation.ReconciliationReviewServiceTest
```

- [ ] **Step 3: Map review choices into the already-defined merge-resolution contract**

Task 26 already owns `StoryMergeResolution.ContentMappingTarget`, Task 27 validates it in `ContentMappingStoryMergePolicy`, and Tasks 30–31 transport structured conflicts/results. `ReconciliationReviewService` only translates each `ProtectedMappingResolution` into the corresponding `StoryMergeResolution.ContentMappingTarget` when constructing the `StoryMergeRequest`; it must not import Library merge types or validate target ownership itself.

Before calling the executor, reject duplicate review selections for the same plugin as `StaleCase`/invalid command semantics rather than relying on list order. A target that is not one of the current domain conflict candidates is rejected by the domain policy during re-plan and returns `ConflictResolutionRequired` again; it never reaches the writer.

- [ ] **Step 4: Implement service and atomic case-state transitions**

`KEEP_SEPARATE` changes durable resolution. `DEFER` leaves status `PENDING` and only advances contextual suppression. `MERGE` updates `RESOLVED_MERGED` inside/coupled to the merge transaction as defined in Task 31; the service must not pre-mark merge success before executor commit. A `StoryMergeResult.ReviewRequired` with structured protected mapping conflicts becomes `ConflictResolutionRequired`; one with only non-resolvable domain reason codes becomes `DomainStateChangeRequired`. Neither path resolves the case.

- [ ] **Step 5: Run GREEN plus merge regression**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.reconciliation.ReconciliationReviewServiceTest
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.merge.RoomStoryGraphMergeCoordinatorTest
```

- [ ] **Step 6: Commit**

```bash
git add catalog/src/main catalog/src/test \
  storage/room/src/main/kotlin/app/openstory/storage/room/catalog
git commit -m "feat: resolve canonical reconciliation reviews"
```

---

### Task 34: Add Review Queue UI and navigation over the durable case repository

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review/ReconciliationReviewUiState.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review/ReconciliationReviewViewModel.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review/ReconciliationReviewScreen.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/review/ReconciliationReviewViewModelTest.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/review/ReconciliationReviewScreenTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppRoute.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`
- Modify: `app/src/main/kotlin/app/openstory/ui/HikariUtilitySheet.kt`

**Interfaces:**
- Consumes: `ReconciliationCaseRepository.observePending()`, canonical projection/read state, `ReconciliationReviewService`.
- `HikariUtilitySheet.utilityDestinations` adds a non-top-level `AppRoute.ReconciliationReview` entry labeled `Review duplicates`; it does not enter `topLevelDestinations` or the floating bottom navigation.
- Produces UI models that carry IDs/revisions rather than raw Room entities:

```kotlin
data class ReconciliationReviewItemUiModel(
    val caseId: String,
    val caseRevision: Long,
    val leftStoryId: StoryId,
    val rightStoryId: StoryId,
    val leftTitle: String,
    val rightTitle: String,
    val confidence: Double,
    val reasonLabels: List<String>,
    val mergeAllowed: Boolean,
    val userStateImpact: Int,
)

data class ProtectedMappingConflictUiModel(
    val pluginId: PluginId,
    val candidateSourceStoryIds: List<String>,
    val selectedSourceStoryId: String? = null,
)

data class ProtectedConflictUiModel(
    val caseId: String,
    val expectedCaseRevision: Long,
    val conflicts: List<ProtectedMappingConflictUiModel>,
)

data class ReconciliationReviewUiState(
    val items: List<ReconciliationReviewItemUiModel> = emptyList(),
    val resolvingCaseId: String? = null,
    val protectedConflict: ProtectedConflictUiModel? = null,
    val domainConflictReasonLabels: List<String> = emptyList(),
    val failureMessage: String? = null,
)
```

`candidateSourceStoryIds` is sorted lexicographically when mapped into UI state so tests/rendering do not depend on Set iteration order. The ViewModel may only submit a protected mapping resolution when every conflict has exactly one selected candidate that came from the corresponding domain result; it must not accept free-form source IDs.

- [ ] **Step 1: Write RED ViewModel ranking/state tests**

Ranking is presentation-only. Assert deterministic order using:

```text
higher ambiguity confidence
then higher meaningful user-state impact
then more recently changed evidence
then older pending case
then stable caseId
```

Changing queue order must not mutate reconciliation assessment.

- [ ] **Step 2: Write RED action tests**

Verify ViewModel sends exact case revision and:

```text
mergeable item exposes MERGE
invariant-blocked item does not expose MERGE
KEEP_SEPARATE removes/resolves item after repository update
DEFER removes contextual urgency but item remains available in queue according to repository query semantics
protected conflict opens second explicit selection state, then resubmits MERGE with resolution
non-resolvable domain conflict populates `domainConflictReasonLabels`, leaves the case pending, and does not fabricate a protected-mapping choice
```

- [ ] **Step 3: Run RED ViewModel tests**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.review.ReconciliationReviewViewModelTest
```

- [ ] **Step 4: Implement screen using existing design tokens/shared components only**

Each item should show enough explainability to act safely: both canonical titles/covers when available, high-level positive evidence, blocking conflict when present, and explicit actions. Do not expose raw numeric internals as the only explanation.

No hard-coded visual values outside existing design-system tokens.

- [ ] **Step 5: Add navigation route**

Add a serializable `AppRoute.ReconciliationReview` following existing `AppRoute` conventions and an `entry<AppRoute.ReconciliationReview>` in `AppNavHost`. Destination owns Hilt ViewModel collection just like Search/Story destinations.

- [ ] **Step 6: Run UI unit tests and app navigation compile/tests**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.review.*
./gradlew :app:testDebugUnitTest
./gradlew :app:compileDebugAndroidTestKotlin
```

- [ ] **Step 7: Commit**

```bash
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/review \
  app/src/main/kotlin/app/openstory/navigation
git commit -m "feat: add canonical reconciliation review queue"
```

---

### Task 35: Add contextual Story review prompt sharing the same durable case and DEFER suppression

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryReconciliationPrompt.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryUiState.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`

**Interfaces:**
- Consumes: same `ReconciliationCaseRepository` and `ReconciliationReviewService` as Task 34.
- Adds to Story UI state:

```kotlin
data class StoryReconciliationPromptUiModel(
    val caseId: String,
    val caseRevision: Long,
    val otherStoryId: StoryId,
    val otherStoryTitle: String,
    val confidence: Double,
    val mergeAllowed: Boolean,
    val reasonLabels: List<String>,
)
```

Prompt eligibility must be a pure projection:

```text
case pending/reviewable
AND this Story participates
AND confidence >= contextual prompt threshold owned by feature presentation policy
AND now >= suppressionUntil
AND not already being resolved
```

The contextual threshold is presentation-only and cannot change matcher decisions.

- [ ] **Step 1: Write RED prompt-eligibility tests**

Cover:

```text
pending high-confidence case -> prompt
low-confidence queue case -> no contextual prompt but remains queue item
DEFER -> no prompt until suppression expires
KEEP_SEPARATE -> no prompt
same case observed from both surfaces -> same caseId/revision
invariant-blocked -> explanatory prompt may appear but no merge action
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.story.StoryViewModelTest
```

- [ ] **Step 3: Implement prompt as a focused composable**

Use the existing Story layout hierarchy; do not create a second full-screen review state. Actions delegate to ViewModel/service.

- [ ] **Step 4: Add DEFER action and protected-conflict handoff**

If contextual MERGE needs mapping conflict resolution, navigate/open the shared review flow for that exact `caseId`; do not build another conflict-resolution data model in Story UI.

- [ ] **Step 5: Run feature + connected navigation gate**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.story.StoryViewModelTest \
  --tests app.openstory.catalog.ui.review.*
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.navigation.AppNavigationTest \
  --stacktrace
```

- [ ] **Step 6: Phase-5 verification**

```bash
./scripts/verify.sh
```

- [ ] **Step 7: Commit**

```bash
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story \
  app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt
git commit -m "feat: surface contextual canonical merge review"
```

---
## Phase 6 — Event-Driven Orchestration, Shared Full Fallback, and Retroactive Runtime Reconciliation

### Task 36: Replace direct feature/service routing with narrow evidence-change events and one durable engine-work orchestrator

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CatalogEvidenceChange.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineWork.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestrator.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestratorTest.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepository.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/home/CatalogRefreshService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/details/CatalogDetailsLoader.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt`

**Interfaces:**

```kotlin
data class CatalogEvidenceChange(
    val storyId: StoryId,
    val sourceKey: SourceKey,
    val identityFingerprintChanged: Boolean,
    val fusionFingerprintChanged: Boolean,
    val availabilityChanged: Boolean = false,
)

object CanonicalEngineWorkReasons {
    const val SOURCE_SUMMARY_CHANGED = "source_summary_changed"
    const val SOURCE_FULL_CHANGED = "source_full_changed"
    const val SOURCE_AVAILABILITY_CHANGED = "source_availability_changed"
    const val SOURCE_LINKED = "source_linked"
    const val SOURCE_UNLINKED = "source_unlinked"
    const val SOURCE_PREFERENCE_CHANGED = "source_preference_changed"
    const val REVIEW_RESOLVED = "review_resolved"
    const val STORY_MERGED = "story_merged"
    const val POLICY_VERSION_CHANGED = "policy_version_changed"
    const val RETRY = "retry"
}

class CanonicalEngineOrchestrator(
    private val reconciliation: CatalogReconciliationService,
    private val fusion: CanonicalFusionService,
    private val work: CanonicalEngineWorkRepository,
) {
    suspend fun onEvidenceChanged(change: CatalogEvidenceChange)
    suspend fun onSourceLinked(storyId: StoryId, sourceKey: SourceKey)
    suspend fun onSourceUnlinked(storyId: StoryId, sourceKey: SourceKey)
    suspend fun onSourcePreferenceChanged(storyId: StoryId)
    suspend fun onStoryMerged(storyId: StoryId)
}
```

The persisted work key is `(storyId, type)`. A later event updates/coalesces reason/version instead of appending duplicate rows.

`CatalogSearchService` must route every `CatalogSearchSummaryCommitResult.changes` item through this same orchestrator after persistence, exactly like Home and Details. Search must not retain the temporary direct Fusion dirty-mark/reconciliation calls introduced in Tasks 18/25. Repository commit methods continue to report facts only; they never invoke the orchestrator themselves.

- [ ] **Step 1: Write RED orchestration-routing tests**

Assert exact routing:

```text
identity=true, fusion=false -> call reconciliation for `change.sourceKey`; on transient failure persist one RECONCILIATION_REEVALUATION item for the resolved owner
identity=false, fusion=true -> fusion only
both true -> run reconciliation first, resolve the current canonical StoryId, then rebuild Fusion once for that resolved Story; dirty rows coalesce
neither -> no engine work
availability change -> fusion; identity remains untouched
source linked -> reconcile that source + Fusion for the resolved owner
source unlinked -> invalidate the in-memory candidate index, mark reconciliation reevaluation/Fusion for the resolved owner; no full pair scan in the foreground call
source preference change -> fusion only
StoryMerged -> invalidate the in-memory candidate index, fusion dirty + POST_MERGE_DERIVED dirty
Search Summary commit with N change facts -> each fact routes through the same evidence path; zero Search-specific reasoning branch
```

Use counters/fakes to prove orchestrator never invokes CatalogSource/Home/Search/Details.

- [ ] **Step 2: Write RED coalescing/retry repository tests**

Room instrumentation must prove ten `FUSION_REBUILD` marks for one Story create one ready row with current reason/version; re-keyed retired Story work from Task 31 does not survive as orphaned work.

- [ ] **Step 3: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.orchestration.CanonicalEngineOrchestratorTest
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCanonicalCatalogRepositoryTest
```

- [ ] **Step 4: Implement orchestrator and remove all temporary direct reasoning calls from Home, Details, and Search**

Persistence layers report facts only. `CatalogRefreshService`, `CatalogDetailsLoader`, and `CatalogSearchService` pass each committed `CatalogCommitChange` to `CanonicalEngineOrchestrator.onEvidenceChanged()`. Source membership mutations call the explicit `onSourceLinked`/`onSourceUnlinked` hooks, and committed Story merge calls `onStoryMerged`; those hooks invalidate the reconciliation candidate index only when membership can make its Story ownership stale. The service boundary decides which reason to attach; event payloads must never say “fetch source X” or “prefer plugin Y”. Remove direct `reconciliation.reconcile(change.sourceKey)`, `fusion.rebuild(change.storyId, CanonicalFusionReason.SOURCE_EVIDENCE_CHANGED)`, and direct Search `FUSION_REBUILD` marking that bypasses this path.

- [ ] **Step 5: Add a test proving refresh churn is suppressed**

Persist the same semantic Summary twice with only resolve time changed. Assert no reconciliation work; assert Fusion only if the persisted freshness classification crossed a policy boundary. This test protects the “refresh != recompute everything” invariant.

- [ ] **Step 6: Run GREEN**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.orchestration.* \
  --tests app.openstory.catalog.home.CatalogRefreshServiceTest \
  --tests app.openstory.catalog.details.CatalogDetailsLoaderTest \
  --tests app.openstory.catalog.search.CatalogSearchServiceTest
```

- [ ] **Step 7: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/orchestration \
  catalog/src/test/kotlin/app/openstory/catalog/orchestration \
  catalog/src/main/kotlin/app/openstory/catalog/home \
  catalog/src/main/kotlin/app/openstory/catalog/details \
  catalog/src/main/kotlin/app/openstory/catalog/search \
  catalog/src/main/kotlin/app/openstory/catalog/repository \
  storage/room/src/main/kotlin/app/openstory/storage/room/catalog
git commit -m "feat: orchestrate canonical engine from evidence changes"
```

---

### Task 37: Extract one operation-level Full-metadata fallback service for Story AUTO lifecycle

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/details/CatalogFullMetadataFallbackService.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/details/CatalogFullMetadataFallbackServiceTest.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/fusion/CatalogFusionEngine.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/metadata/CatalogMetadataCoordinator.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/search/CatalogSearchServiceTest.kt`

**Interfaces:**
- Consumes: persisted canonical source records, effective primary, provider-agnostic ranking facts, existing metadata coordinator.
- Search selection remains navigation-only from Task 18. This service exists for Story-detail AUTO enrichment after canonical navigation.
- Extend fusion/ranking API with one pure reusable order function rather than duplicating ordering in Story:

```kotlin
class CatalogFusionEngine {
    fun rankedEligibleSourceKeys(input: FusionInput): List<SourceKey>
    fun fuse(input: FusionInput): CanonicalGenerationCandidate
}

sealed interface CatalogFullFallbackResult {
    data class Ready(
        val storyId: StoryId,
        val sourceKey: SourceKey,
        val entry: CatalogEntry,
    ) : CatalogFullFallbackResult

    data class Failure(
        val attempts: List<CatalogFullAttemptFailure>,
    ) : CatalogFullFallbackResult
}

data class CatalogFullAttemptFailure(
    val sourceKey: SourceKey,
    val failure: CatalogMetadataFailure,
)

class CatalogFullMetadataFallbackService(
    private val canonical: CanonicalCatalogRepository,
    private val metadata: CatalogMetadataCoordinator,
    private val fusion: CatalogFusionEngine,
    private val identity: StoryIdentityRepository,
) {
    suspend fun requireFull(storyId: StoryId): CatalogFullFallbackResult
}
```

- [ ] **Step 1: Write RED fallback-order tests**

Required:

```text
retired StoryId is resolved before source lookup
effective primary attempted first
primary operation failure -> next ranked eligible source attempted
first success stops attempts
successful Full with description=null/authors empty is SUCCESS and stops fallback
SourceUnavailable counts as operation failure and falls through
SourceIdMismatch/store failure is recorded and the next eligible source may be attempted
all attempts fail -> aggregate ordered failure list
no usable source -> bounded Failure with zero network guessing
provider ID only breaks an exact generic quality tie; no named-provider preset
```

Also prove the service never retries another provider merely because a `Ready` payload omitted an optional field.

- [ ] **Step 2: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.details.CatalogFullMetadataFallbackServiceTest
```

Expected: compile failure before the service exists.

- [ ] **Step 3: Implement one persisted-story eligible-source ranking path**

Refactor Task-13 quality ordering so `fuse()` and Story Full fallback consume the same generic quality vector and eligibility semantics.

`requireFull(storyId)` must:
1. resolve redirects;
2. read the current canonical state/source records locally;
3. reconstruct the ranking input from persisted facts and current source preference;
4. return ordered `SourceKey`s with effective primary first;
5. never fetch to discover ranking facts.

Do not expose a provider trust score; the reusable API returns ordered source keys only.

- [ ] **Step 4: Implement the operation-level fallback loop**

Pseudo-code:

```kotlin
for (sourceKey in rankedSourceKeys) {
    when (val result = metadata.require(sourceKey.toMetadataKey(), CatalogMetadataLevel.Full)) {
        is CatalogMetadataResult.Ready ->
            return CatalogFullFallbackResult.Ready(result.storyId, sourceKey, result.entry)

        is CatalogMetadataResult.Failure ->
            failures += CatalogFullAttemptFailure(sourceKey, result.failure)

        CatalogMetadataResult.Missing ->
            failures += missingFailure(sourceKey)
    }
}

return CatalogFullFallbackResult.Failure(failures)
```

A `Ready` result is success regardless of optional field presence. This service never asks for another provider to fill `description`, `authors`, cover, score, or any other optional field after a successful Full operation.

- [ ] **Step 5: Replace Story AUTO Full request with this service**

In `StoryViewModel`:

```text
AUTO/default initial enrichment -> requireFull(storyId)
explicit raw-source inspection request -> metadata.require(that SourceKey, Full)
explicit refresh of selected raw source -> metadata.refresh(that SourceKey, Full)
```

A failure from one AUTO source may fall through inside the shared service. A successful sparse Full stops the operation.

Canonical presentation still comes from the active generation. The ViewModel never applies the returned `CatalogEntry` directly as canonical UI truth; persistence/evidence events trigger Fusion.

- [ ] **Step 6: Keep Search selection network-free**

Retain Task-18 regression behavior:

```text
SearchService.select(canonicalSearchStory)
    -> Success(canonical StoryId)
    -> zero Full/Details calls
```

Run the Search service test in this task specifically to prevent future reuse of the Story fallback service from migrating back into Search selection.

- [ ] **Step 7: Run targeted GREEN**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.details.CatalogFullMetadataFallbackServiceTest \
  --tests app.openstory.catalog.search.CatalogSearchServiceTest
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.story.StoryViewModelTest
```

- [ ] **Step 8: Add architectural guard assertions**

Prove:
- Search selection contains no `metadata.require(Full)`/Details call;
- Story AUTO enrichment has one path through `CatalogFullMetadataFallbackService`;
- explicit source inspection/refresh may still address the selected source directly;
- no path treats optional-field absence as operation failure;
- no `firstOrNull()` over provider order chooses AUTO Full source.

- [ ] **Step 9: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/details \
  catalog/src/main/kotlin/app/openstory/catalog/fusion \
  catalog/src/main/kotlin/app/openstory/catalog/metadata \
  catalog/src/test/kotlin/app/openstory/catalog/details \
  catalog/src/test/kotlin/app/openstory/catalog/search/CatalogSearchServiceTest.kt \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story
git commit -m "feat: add story full metadata source fallback"
```

---

### Task 38: Complete retroactive reconciliation after persisted evidence changes and schedule post-merge derived reconstruction

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/details/CatalogDetailsLoader.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/home/CatalogRefreshService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestrator.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryGraphMergeCoordinator.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/details/CatalogDetailsLoaderTest.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/reconciliation/RetroactiveReconciliationTest.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/merge/RoomStoryGraphMergeCoordinatorTest.kt`

**Interfaces:**
- Consumes: Tasks 32, 36, 37.
- Produces final foreground flow:

```text
persist Summary/Full facts
  -> CatalogEvidenceChange
  -> identity changed? reconciliation
  -> eligible existing A+B? atomic merge
  -> merge committed? StoryMerged event/work
  -> survivor Fusion rebuild
  -> derived Chapter/mapping work retryably scheduled
```

- [ ] **Step 1: Write RED end-to-end domain test for the original split-then-Full scenario**

Fixture:

```text
MAL-like Summary -> Story A
MU-like Summary  -> Story B
initial evidence -> REVIEW/separate-for-now
Full for B adds compatible author/alias/WORK identifier
```

Expected after persisted Full event:

```text
reconciliation reruns even though B source already has durable StoryId
assessment becomes eligible SAME_WORK
Room executor merges A/B atomically
old retired ID resolves survivor
all sources preserved
survivor Fusion marked dirty/rebuilt
```

Use synthetic plugin IDs (`provider.one`, `provider.two`) so test cannot encode bundled-provider policy.

- [ ] **Step 2: Write RED negative retroactive tests**

```text
Full only changes score/cover -> Fusion rebuild, zero reconciliation
Full adds contradictory identifier/content type -> correction REVIEW/SEPARATE, no merge
protected mapping conflict -> review, no partial merge
same fingerprint Full replay -> no duplicate case/merge/work churn
```

- [ ] **Step 3: Write RED post-merge contradictory-evidence test**

After A+B already merged, change one source's identity evidence to create a hard contradiction. Expected:

```text
no automatic source detach
no automatic split
correction review linked to merge lineage/new fingerprint
controlled reversal may be offered later
```

- [ ] **Step 4: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.reconciliation.RetroactiveReconciliationTest \
  --tests app.openstory.catalog.details.CatalogDetailsLoaderTest
```

Run the Room integration leg in the same RED/GREEN cycle:

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.merge.RoomStoryGraphMergeCoordinatorTest
```

- [ ] **Step 5: Implement correction-case behavior for intra-Story contradiction**

Do not detach the offending source automatically. Persist a reconciliation/correction revision that references current source membership and merge lineage; action remains review/manual reverse path.

- [ ] **Step 6: Implement post-merge derived-work marks**

At minimum:

```text
invalidate reconciliation candidate index after committed ownership move
FUSION_REBUILD always
POST_MERGE_DERIVED when chapter reaggregation/resync or automated mapping recompute requested by domain plans
```

Candidate-index invalidation happens after the authoritative merge commit; the next reconciliation lazily rebuilds from current persisted ownership once, rather than allowing stale retired Story IDs to accumulate. Derived failure cannot roll back the already committed identity merge.

- [ ] **Step 7: Run Phase-6 unit/integration gates**

```bash
./gradlew :catalog:testDebugUnitTest :library:testDebugUnitTest :chapters:testDebugUnitTest :reader:testDebugUnitTest
./gradlew :storage:room:connectedDebugAndroidTest
./scripts/verify.sh
```

- [ ] **Step 8: Commit**

```bash
git add catalog/src/main catalog/src/test storage/room/src/main storage/room/src/androidTest
git commit -m "feat: complete retroactive canonical reconciliation"
```

---
## Phase 7 — Background Safety, Controlled Reversal, Observability, and Final Hardening

### Task 39: Drain durable engine work with app-owned WorkManager scheduling and policy-reevaluation safety passes

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineMaintenanceService.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/orchestration/CanonicalEngineMaintenanceServiceTest.kt`
- Create: `app/src/main/kotlin/app/openstory/work/CanonicalEngineWorker.kt`
- Create: `app/src/main/kotlin/app/openstory/work/WorkManagerCanonicalEngineWorkScheduler.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineWork.kt`
- Create: `app/src/main/kotlin/app/openstory/di/CanonicalEngineEntryPoint.kt`
- Create: `app/src/test/kotlin/app/openstory/work/CanonicalEngineWorkerTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/OpenStoryApplication.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestrator.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/CatalogModule.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCanonicalCatalogRepository.kt`

**Interfaces:**

```kotlin
enum class CanonicalWorkRunDecision {
    SUCCESS,
    RETRY,
    FAILURE,
}

data class CanonicalMaintenanceReport(
    val processed: Int,
    val succeeded: Int,
    val retried: Int,
    val failedInvariant: Int,
)

fun interface CanonicalEngineWorkScheduler {
    fun scheduleDrain()
}

class CanonicalEngineMaintenanceService(
    private val work: CanonicalEngineWorkRepository,
    private val reconciliation: CatalogReconciliationService,
    private val fusion: CanonicalFusionService,
    private val identity: StoryIdentityRepository,
    private val clock: Clock,
) {
    suspend fun drainReady(limit: Int): CanonicalMaintenanceReport
    suspend fun enqueuePolicyReevaluationIfNeeded(limit: Int): Int
    suspend fun runConsistencySafetyPass(limit: Int): CanonicalMaintenanceReport
}
```

`CanonicalEngineWorker` follows the repository's existing `EntryPointAccessors` worker pattern and depends only on the maintenance service. Domain modules do not import `androidx.work`.

- [ ] **Step 1: Write RED maintenance tests for work-type dispatch**

Required:

```text
FUSION_REBUILD -> CanonicalFusionService
RECONCILIATION_REEVALUATION -> `CatalogReconciliationService.reevaluateStory(work.storyId)` so every current source on that Story is rechecked
POLICY_REEVALUATION -> current policy comparison then correct use case(s)
POST_MERGE_DERIVED -> bounded derived-work dispatcher, not identity rollback
successful item -> complete
transient failure -> retry with bounded backoff/attempt increment
semantic REVIEW result -> complete work because review is durable, no retry loop
invariant violation -> record failure/degraded diagnostic and stop automatic mutation
```

Use a deterministic Clock to assert `nextAttemptAtEpochMillis`.

- [ ] **Step 2: Write RED policy-version backlog tests**

Fixtures include generations/cases on old independent versions. Assert:

```text
fusion policy old only -> FUSION_REBUILD
primary policy old only -> FUSION_REBUILD
reconciliation policy old -> RECONCILIATION_REEVALUATION
score-only fusion policy change does not reopen KEEP_SEPARATE case
same current versions -> no work
```

- [ ] **Step 3: Write RED maintenance-safety-pass tests**

The safety pass may find bounded categories only:

```text
orphaned/retryable dirty work
stale-policy canonical state
redirect target inconsistency
pending case whose recorded evidence fingerprint no longer matches current evidence
```

It must **not** compare every Story pair. Test a large fake story set and assert candidate reconciliation is invoked only for marked/stale stories.

- [ ] **Step 4: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.orchestration.CanonicalEngineMaintenanceServiceTest
```

- [ ] **Step 5: Implement maintenance service and bounded backoff**

Keep retry policy explicit in host code with `BASE_RETRY_DELAY_MILLIS = 5 * 60 * 1000L` and `MAX_RETRY_DELAY_MILLIS = 6 * 60 * 60 * 1000L`. For retry attempt `n >= 1`, compute `min(BASE_RETRY_DELAY_MILLIS * 2^(n - 1), MAX_RETRY_DELAY_MILLIS)` using overflow-safe capped arithmetic, then set `nextAttemptAtEpochMillis = clock.now + delay`. Persist failure class/code; do not persist exception stack traces as domain state. Unit tests must assert attempts 1/2/3 produce 5/10/20 minutes and sufficiently high attempts cap at 6 hours.

- [ ] **Step 6: Write and implement worker decision helper**

Mirror existing worker style:

```kotlin
internal fun decideCanonicalWorkerResult(report: CanonicalMaintenanceReport): CanonicalWorkRunDecision = when {
    report.retried > 0 -> CanonicalWorkRunDecision.RETRY
    report.failedInvariant > 0 -> CanonicalWorkRunDecision.FAILURE
    else -> CanonicalWorkRunDecision.SUCCESS
}
```

The worker converts that to WorkManager `Result.success/retry/failure`.

- [ ] **Step 7: Implement one-time drain plus a daily app-owned safety trigger**

Add the Android-free `CanonicalEngineWorkScheduler` port to `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineWork.kt` and implement it as `WorkManagerCanonicalEngineWorkScheduler` in `app/src/main/kotlin/app/openstory/work/WorkManagerCanonicalEngineWorkScheduler.kt` with:

```text
one-time unique work: canonical-engine-drain
  ExistingWorkPolicy.KEEP
  no network constraint

daily periodic unique work: canonical-engine-safety
  24-hour interval
  ExistingPeriodicWorkPolicy.KEEP
  no network constraint
```

`OpenStoryApplication.onCreate()` schedules one drain and ensures the daily safety work exists so schema-9 bootstrap rows are eventually processed even before another catalog event occurs. `CanonicalEngineOrchestrator` calls `scheduleDrain()` whenever it persists retryable/background work. Network-requiring derived jobs keep their own specialized schedulers; the canonical worker itself remains local-evidence driven.

- [ ] **Step 8: Run unit + architecture tests**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.orchestration.CanonicalEngineMaintenanceServiceTest
./gradlew :app:testDebugUnitTest \
  --tests app.openstory.work.CanonicalEngineWorkerTest
./scripts/verify.sh
```

Specifically inspect static boundary output to confirm `androidx.work` did not enter `:catalog`, `:library`, `:chapters`, or `:reader`.

- [ ] **Step 9: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/orchestration \
  catalog/src/test/kotlin/app/openstory/catalog/orchestration \
  app/src/main/kotlin/app/openstory/work \
  app/src/main/kotlin/app/openstory/di/CanonicalEngineEntryPoint.kt \
  app/src/main/kotlin/app/openstory/OpenStoryApplication.kt \
  catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineOrchestrator.kt \
  storage/room/src/main/kotlin/app/openstory/storage/room/catalog
git commit -m "feat: add canonical engine background safety work"
```

---

### Task 40: Add controlled reverse planning and atomic split for provably safe historical merges

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/identity/StoryMergeReversalModels.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/identity/StoryMergeReversalExecutor.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryMergeReversalCoordinator.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/merge/RoomStoryMergeReversalCoordinatorTest.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryMergeWriter.kt`
- Modify: `library/src/main/kotlin/app/openstory/library/merge/LibraryStoryMergePolicy.kt`
- Modify: `library/src/main/kotlin/app/openstory/library/merge/ContentMappingStoryMergePolicy.kt`
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/merge/ChapterStoryMergePolicy.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/progress/ReadingProgressMergePolicy.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review/ReconciliationReviewViewModel.kt`

**Interfaces:**

```kotlin
enum class StoryMergeReversibility {
    REVERSIBLE,
    REQUIRES_REVIEW_TO_REVERSE,
    NOT_AUTOMATICALLY_REVERSIBLE,
}

data class StoryMergeReverseRequest(
    val mergeEventId: String,
    val expectedSurvivorIdentityRevision: Long,
)

sealed interface StoryMergeReverseResult {
    data class Reversed(
        val restoredStoryId: StoryId,
        val survivingStoryId: StoryId,
        val reversalEventId: String,
    ) : StoryMergeReverseResult
    data class ReviewRequired(val reasons: Set<String>) : StoryMergeReverseResult
    data object NotAutomaticallyReversible : StoryMergeReverseResult
    data object StalePlan : StoryMergeReverseResult
}

fun interface StoryMergeReversalExecutor {
    suspend fun reverse(request: StoryMergeReverseRequest): StoryMergeReverseResult
}
```

Audit payload from Task 31 must retain enough original ownership/state summary to reconstruct only what is provable; this task does not add full DB snapshots. Each domain policy exposes only the reversal-safety validation for state it owns. `ReconciliationReviewViewModel` surfaces reversal only for a correction review tied to a merge event whose current reversibility assessment allows it; no global/blind Undo action is added.

- [ ] **Step 1: Write RED simple-lossless reversal test**

Scenario:

```text
A owns source a, no user state
B owns source b, no user state
B merged into A
no state changes afterward
```

Reverse should atomically restore B from the bounded historical identity facts stored in the merge reversal payload, move original B source membership back, remove/replace current redirect consistently, bump identity revisions, preserve the historical merge event, insert exactly one `story_merge_reversal_events` row referencing that merge event, and mark both Stories for Fusion reevaluation. Assert the returned `reversalEventId` matches the inserted audit row.

- [ ] **Step 2: Write RED review-required post-merge mutation tests**

At least:

```text
new protected mapping created after merge
new source linked after merge with no historical owner
Library state edited after two Library rows were coalesced
chapter graph changed in a way original ownership cannot be proven
new progress on combined graph cannot be partitioned safely
```

Each returns `ReviewRequired`; no table changes.

- [ ] **Step 3: Write RED stale/concurrent reversal tests**

If survivor merged again or identity revision changed after reverse plan preparation, result is `StalePlan`; do not resurrect an obsolete intermediate canonical identity blindly.

- [ ] **Step 4: Run RED**

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.merge.RoomStoryMergeReversalCoordinatorTest
```

- [ ] **Step 5: Implement per-domain reversal safety checks**

Each domain answers whether current state still corresponds to the merge audit's reversible footprint. Identity Core does not infer Library/mapping/progress ownership itself.

- [ ] **Step 6: Implement one atomic split transaction**

The split must follow the same all-or-nothing rule as forward merge. It restores historical active Story identity only when every participant is READY. Within the same transaction, restore/move authoritative rows first, validate both active graphs, remove/replace the forward redirect, insert the `story_merge_reversal_events` audit row, bump identity revisions, and only then commit. Any failure rolls back the restored Story and reversal audit together. It never auto-splits solely because a newer reconciliation policy would make a different decision today.

- [ ] **Step 7: Add correction-review integration**

When Task-38 post-merge contradictory evidence creates a correction review, expose “reverse safely” only if reversal planning says `REVERSIBLE`. Otherwise route to review/manual repair state and explain the blockers.

- [ ] **Step 8: Run GREEN and forward-merge regressions**

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.merge.RoomStoryMergeReversalCoordinatorTest,app.openstory.storage.room.merge.RoomStoryGraphMergeCoordinatorTest
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.review.ReconciliationReviewViewModelTest
```

- [ ] **Step 9: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/identity \
  storage/room/src/main/kotlin/app/openstory/storage/room/merge \
  storage/room/src/androidTest/kotlin/app/openstory/storage/room/merge \
  library/src/main/kotlin/app/openstory/library/merge \
  chapters/src/main/kotlin/app/openstory/chapters/merge \
  reader/src/main/kotlin/app/openstory/reader/progress \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui/review
git commit -m "feat: add controlled canonical merge reversal"
```

---

### Task 41: Add structured decision traces and invariant diagnostics without creating a second truth store

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/diagnostics/CanonicalDecisionTrace.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/diagnostics/CanonicalDiagnostics.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/diagnostics/CanonicalDiagnosticsTest.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/reconciliation/CatalogReconciliationEngine.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/fusion/CatalogFusionEngine.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/merge/RoomStoryGraphMergeCoordinator.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/orchestration/CanonicalEngineMaintenanceService.kt`

**Interfaces:**

```kotlin
enum class CanonicalTraceKind {
    RECONCILIATION,
    PRIMARY_SELECTION,
    FIELD_FUSION,
    MERGE_BLOCKED,
    MERGE_COMMITTED,
    GENERATION_FAILED,
    CASE_REOPENED,
    INVARIANT_VIOLATION,
}

data class CanonicalDecisionTrace(
    val kind: CanonicalTraceKind,
    val storyIds: Set<StoryId>,
    val sourceKeys: Set<SourceKey>,
    val policyVersions: Map<String, Int>,
    val reasonCodes: List<String>,
    val evidenceFingerprints: List<String>,
)

fun interface CanonicalDiagnosticsSink {
    fun record(trace: CanonicalDecisionTrace)
}
```

The persistent domain audit remains cases/generations/merge events. Diagnostics is observability, not an event-sourced canonical state.

- [ ] **Step 1: Write RED trace-content tests**

Assert traces can answer:

```text
why candidate was considered/decided REVIEW or AUTO
why primary stayed despite challenger
why a field selected source X or unioned contributors
why merge was blocked by protected state
why generation failed validation
why a resolved case reopened
```

Use reason codes and IDs/fingerprints; do not require raw descriptions, complete progress payloads, or dumped plugin JSON.

- [ ] **Step 2: Write RED privacy/boundedness test**

Construct metadata with a long description and mapping/progress values. Assert diagnostic trace does not contain description body or a serialization of full user state. The trace may contain bounded IDs, policy versions, fingerprints, reason codes, and counts.

- [ ] **Step 3: Run RED**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.diagnostics.CanonicalDiagnosticsTest
```

- [ ] **Step 4: Implement no-op production sink plus debug/log sink wiring following existing diagnostics patterns**

Do not make policy depend on whether diagnostics recording succeeds. A sink exception must not alter merge/fusion result.

- [ ] **Step 5: Add invariant-check maintenance diagnostics**

Emit bounded diagnostics for:

```text
redirect cycle/invalid target
source without valid active owner
duplicate impossible source ownership
field provenance pointing outside Story source set
orphaned dirty work after redirect resolution
```

Automatic repair is only performed where an existing deterministic transaction already owns it; otherwise mark degraded/report.

- [ ] **Step 6: Run GREEN**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.diagnostics.CanonicalDiagnosticsTest \
  --tests app.openstory.catalog.reconciliation.* \
  --tests app.openstory.catalog.fusion.*
```

- [ ] **Step 7: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/diagnostics \
  catalog/src/test/kotlin/app/openstory/catalog/diagnostics \
  catalog/src/main/kotlin/app/openstory/catalog/reconciliation \
  catalog/src/main/kotlin/app/openstory/catalog/fusion \
  catalog/src/main/kotlin/app/openstory/catalog/orchestration \
  storage/room/src/main/kotlin/app/openstory/storage/room/merge
git commit -m "feat: add canonical engine decision diagnostics"
```

---

### Task 42: Update governance/docs and run the final acceptance, migration, UI, and performance matrix

**Files:**
- Modify: `docs/README.md`
- Modify: `docs/PROJECT-HANDBOOK.md`
- Modify: `docs/project/current-state.md`
- Modify: `docs/project/requirement-coverage.md`
- Modify: `docs/project/document-governance.md`
- Modify: `docs/implementation/current-roadmap.md`
- Modify: `docs/implementation/waves/wave-10-background-sync-auth-and-notifications.md`
- Modify: `docs/implementation/waves/wave-11-hardening-open-source-release.md`
- Modify: `docs/plugin-sdk/catalog-protocol.md`
- Add/update: `storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/9.json`

**Interfaces:**
- Consumes every previous task.
- Produces the final documented, verified baseline. No new feature behavior is introduced here.

- [ ] **Step 1: Update normative current docs, not historical checkpoints**

Document explicitly:

```text
canonical identity/fusion design spec path
schema 9 ownership by canonical engine
Wave 10 notification persistence rebased from planned 8->9 to 9->10
Wave 11 entry schema becomes 10 unless another approved migration intervenes
plugins provide facts; host owns identity/fusion policy
Review and controlled reversal status
background work ownership remains app layer
```

Do not edit archived checkpoints to pretend they contained this engine.

- [ ] **Step 2: Regenerate and verify the schema export is contiguous and deterministic**

Run the same Room compiler path already used by this repository to export schemas, then inspect:

```bash
./gradlew :storage:room:compileDebugKotlin --no-configuration-cache --stacktrace
ls storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase
```

Expected: schema `8.json` remains and `9.json` is present; no gap or overwritten schema-8 baseline.

Run repository schema-stability gate:

```bash
./scripts/verify-room-schema-stability.sh
```

- [ ] **Step 3: Run all pure/module tests**

```bash
./gradlew \
  :plugins:api:test \
  :catalog:testDebugUnitTest \
  :library:testDebugUnitTest \
  :chapters:testDebugUnitTest \
  :reader:testDebugUnitTest \
  :feature:catalog:testDebugUnitTest \
  :app:testDebugUnitTest
```

Expected: GREEN.

- [ ] **Step 4: Run the complete Room instrumentation suite**

```bash
./gradlew :storage:room:connectedDebugAndroidTest --stacktrace
```

Required evidence includes schema-8→9 representative graph migration, FK checks, forward merge rollback/idempotency/flattening, controlled reversal tests, and durable one-per-merge reversal audit linkage.

- [ ] **Step 5: Run connected app navigation/smoke**

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.AppLaunchSmokeTest,app.openstory.navigation.AppNavigationTest \
  --stacktrace
```

Verify Review Queue route, old StoryId redirect navigation, Story canonical presentation, and normal top-level navigation.

- [ ] **Step 6: Audit the canonical acceptance matrix against the named owning tests**

Before performance testing, confirm the following already-created tests from earlier tasks are present and GREEN; Task 42 does not invent late feature behavior:

```text
CatalogReconciliationEngineTest / ReconciliationAdversarialFixtureTest
  -> pair symmetry, title-only safety, strong-identifier conflict gates, candidate lead
CatalogReconciliationServiceTest / RetroactiveReconciliationTest
  -> durable rejection/reopen rules, new-evidence reassessment, post-merge correction review
CatalogFusionEnginePrimaryTest / CatalogFusionEngineFieldsTest
  -> hysteresis, pin semantics, field fusion, coherent latestUpdate, raw-fact preservation
CanonicalGenerationValidatorTest / CanonicalFusionServiceTest
  -> atomic generation validation/promotion and provider-unavailable historical continuity
RoomStoryGraphMergeCoordinatorTest
  -> one active owner, protected conflict rollback, stable chapter/release IDs, idempotent retry, stale-plan rejection, redirect flattening
RoomStoryIdentityResolverTest
  -> retired StoryId resolves deterministic survivor
CatalogFullMetadataFallbackServiceTest / CatalogSearchServiceTest
  -> policy reevaluation/fallback does not make Search fetch Full or make optional absence trigger provider hopping
CanonicalPresentationConsistencyTest
  -> Search/Discover/Story/Library consume canonical presentation and never raw-fallback it
```

Run the focused acceptance set explicitly:

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests app.openstory.catalog.reconciliation.CatalogReconciliationEngineTest \
  --tests app.openstory.catalog.reconciliation.ReconciliationAdversarialFixtureTest \
  --tests app.openstory.catalog.reconciliation.CatalogReconciliationServiceTest \
  --tests app.openstory.catalog.reconciliation.RetroactiveReconciliationTest \
  --tests app.openstory.catalog.fusion.CatalogFusionEnginePrimaryTest \
  --tests app.openstory.catalog.fusion.CatalogFusionEngineFieldsTest \
  --tests app.openstory.catalog.fusion.CanonicalGenerationValidatorTest \
  --tests app.openstory.catalog.fusion.CanonicalFusionServiceTest \
  --tests app.openstory.catalog.details.CatalogFullMetadataFallbackServiceTest \
  --tests app.openstory.catalog.search.CatalogSearchServiceTest
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.CanonicalPresentationConsistencyTest
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.merge.RoomStoryGraphMergeCoordinatorTest,app.openstory.storage.room.catalog.RoomStoryIdentityResolverTest \
  --stacktrace
```

If any named class is missing or red, stop Task 42 and return to the owning earlier task; do not patch missing semantics into this final verification task.

- [ ] **Step 7: Run the exact existing macrobenchmark journeys affected by canonical observation**

Run the repository's current benchmark methods by their existing names:

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#coldStartup' \
  --stacktrace
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#homeDiscoverWarm' \
  --stacktrace
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#discoverScroll' \
  --stacktrace
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#storyTabs' \
  --stacktrace
```

Record startup plus Discover warm-navigation/scroll and Story-tab results. The architectural pass criterion is no per-recomposition source fusion and no obvious regression outside normal device noise; if a measurable regression appears, inspect the generated trace before changing correctness policy.

- [ ] **Step 8: Regenerate the Baseline/Startup Profile after the canonical read-path rollout**

Tasks 17–21 change hot Discover/Story/Search read paths, so regenerate using the repository's existing task rather than carrying forward a profile generated from the old source-selection path:

```bash
./gradlew :app:generateBaselineProfile --stacktrace
```

Then run the existing profile/static verification through the final repository gate in Step 9.

- [ ] **Step 9: Run the repository-wide final gate**

```bash
./scripts/verify.sh
```

Expected: GREEN with architecture, package-boundary, source-layout, static, Room schema, and structural checks intact.

- [ ] **Step 10: Manual debug trace sanity check**

Using synthetic/non-provider-specific fixture data, confirm diagnostics can explain one AUTO merge, one REVIEW, one primary stay due hysteresis, one fused latestUpdate source, and one blocked protected merge without dumping raw user data.

- [ ] **Step 11: Commit final docs/schema/verification evidence**

```bash
git add docs storage/room/schemas config benchmark app feature/catalog catalog library chapters reader plugins/api
git commit -m "docs: finalize canonical catalog engine rollout"
```

---

## Spec Coverage Matrix

The plan is deliberately traceable to every major design-spec section rather than only to the rollout section.

| Spec section | Primary implementation tasks |
| --- | --- |
| §1 Purpose | 1–42 collectively; final acceptance in 42 |
| §2 Relationship to existing architecture | 4, 6, 16–21, 42 |
| §3 Current repository problems | 1, 4, 17–21, 25, 37–38 |
| §4 Goals | 5–42 |
| §5 Non-goals | Global Constraints; 23, 28, 39–42 |
| §6 Terminology | 2–5, 12, 22, 26, 36 |
| §7 Normative decisions | Global Constraints; 12–40 |
| §8 Top-level architecture | File/Responsibility Map; 5–42 |
| §9 Module ownership/boundaries | File map; 5–9, 27–31, 39, 42 |
| §10 Fetching vs reasoning | 15, 18–19, 22–25, 36–38 |
| §11 Raw source/evidence model | 2–3, 7, 22, 25 |
| §12 Canonical identity model | 5–9, 26, 30–32 |
| §13 Durable reconciliation cases | 9, 24–25, 31, 33–35 |
| §14 Reconciliation engine | 22–25, 32, 38 |
| §15 Survivor selection | 6, 26, 30–31 |
| §16 Metadata Fusion Engine | 12–15, 37 |
| §17 Canonical Generation Model | 5–8, 14–15 |
| §18 Canonical Read Model | 8, 11, 16–21 |
| §19 Atomic Story Graph Merge | 26–32 |
| §20 Domain Merge Semantics | 27–31 |
| §21 Merge Transaction Ordering | 30–31 |
| §22 Merge Audit/controlled reversal | 9, 31, 38, 40 |
| §23 Review Workflow | 24, 33–35, 40 |
| §24 Event-driven orchestration | 25, 36, 38 |
| §25 Background safety work | 9, 31, 36, 39 |
| §26 Policy versioning | 12, 22, 39 |
| §27 Policy-change reevaluation | 15, 24, 39 |
| §28 Source availability/staleness | 12–15, 37, 39 |
| §29 Search integration | 18, 21 |
| §30 Story integration | 17, 21, 35, 37 |
| §31 Discover integration | 1, 19, 21 |
| §32 Library integration | 20, 27, 31 |
| §33 Chapter/Reader integration | 28–31, 38, 40 |
| §34 Required persistent concepts | 6–10 |
| §35 Room schema/versioning | 6, 10, 42 |
| §36 Concurrency/idempotency/atomicity | 8–10, 24, 30–32, 36, 40 |
| §37 Failure/recovery | 11, 15, 24, 31, 36, 39–40 |
| §38 Observability/explainability | 14, 22–24, 31, 41 |
| §39 Performance requirements | 3, 11, 15–16, 19, 21, 36, 39, 42 |
| §40 Rollout phases | Phases 0–7 / Tasks 1–42 |
| §41 Test strategy | TDD in every task; phase gates 21, 25, 32, 35, 38, 42 |
| §42 Acceptance invariants | 23–24, 31–32, 37–42 |
| §43 Documentation/governance | 1–2, 6, 42 |
| §44 Implementation constraints | Global Constraints and task ordering |
| §45 Deferred extensions | Global Constraints; explicit exclusions in 23, 28, 40, 42 |
| §46 Final architecture summary | Full task dependency chain; verified in 42 |


## Normative Decision Coverage Matrix

This table is the direct trace from the design interview's 25 locked decisions to executable tasks. A task may refine mechanics, but none may weaken the decision it implements.

| Design decision | Required implementation tasks |
| --- | --- |
| `DECISION-CANONICAL-001` — one canonical Story; provider sources remain inspectable | 5, 8, 16–21 |
| `DECISION-CANONICAL-002` — primary source plus field-specific fusion | 12–21 |
| `DECISION-CANONICAL-003` — balanced AUTO/REVIEW/SEPARATE reconciliation | 22–25, 32–35 |
| `DECISION-CANONICAL-004` — survivor favors meaningful user state | 26, 30–31 |
| `DECISION-CANONICAL-005` — domain-safe auto merge; strong user conflict requires review | 27–31, 33 |
| `DECISION-CANONICAL-006` — dynamic primary with categorical hysteresis | 12–13, 39 |
| `DECISION-CANONICAL-007` — canonical presentation is materialized | 5–16, 21 |
| `DECISION-CANONICAL-008` — event-driven reconciliation plus background safety | 25, 36, 38–39 |
| `DECISION-CANONICAL-009` — queued and contextual review share one durable case | 24, 33–35 |
| `DECISION-CANONICAL-010` — auditable merge with controlled reversibility | 9, 31, 40–41 |
| `DECISION-CANONICAL-011` — field provenance and decision trace | 5, 14–15, 41 |
| `DECISION-CANONICAL-012` — host owns generic policy; plugins provide facts | 1–3, 12–14, 22–23, 42 |
| `DECISION-CANONICAL-013` — source disappearance preserves Story history | 12–15, 37, 39 |
| `DECISION-CANONICAL-014` — Story-level user primary pin | 5, 13, 17, 27 |
| `DECISION-CANONICAL-015` — identity is creative work + compatible medium/lineage | 2–3, 22–25 |
| `DECISION-CANONICAL-016` — durable evidence/version-aware separation | 9, 24–25, 33, 39 |
| `DECISION-CANONICAL-017` — standardized external identifiers | 2–3, 7, 22–23 |
| `DECISION-CANONICAL-018` — strong identifiers do not bypass hard conflicts | 22–25 |
| `DECISION-CANONICAL-019` — plugins do not self-score quality/trust | 2, 12–14, 41–42 |
| `DECISION-CANONICAL-020` — validate then atomically promote generations | 5, 8, 15 |
| `DECISION-CANONICAL-021` — hybrid policy-change reevaluation | 9, 15, 24, 36, 39 |
| `DECISION-CANONICAL-022` — Identity Core coordinates; domains own merge semantics | 26–31 |
| `DECISION-CANONICAL-023` — authoritative merge is all-or-nothing | 30–32 |
| `DECISION-CANONICAL-024` — UI reads last valid generation + health, never raw fallback | 8, 11, 15–21, 39 |
| `DECISION-CANONICAL-025` — complete architecture, phased verified rollout | Phases 0–7 / Tasks 1–42 |

## Plan Self-Review Record

The plan was re-read against the approved design and the current schema-8 repository layout before handoff.

- **Spec coverage:** all 46 major design sections are mapped above; all 25 `DECISION-CANONICAL-*` decisions have explicit task coverage.
- **Repository-path audit:** every `Modify`/`Expand` path exists in the current baseline or is created by an earlier task; no file is independently `Create`d twice.
- **Task-boundary audit:** premature empty `ReconciliationCaseRepository`/work contracts were removed from Task 5; Task 9 and Task 24 now create their final first usable contracts when needed.
- **Atomic-merge audit:** Task 30 is a read-only planner with a complete test cycle; Task 31 separately creates the destructive coordinator/writer, so no task ends with a deliberately incomplete executor.
- **Schema-governance audit:** Task 6 updates every current schema-number authority plus `scripts/tests/post-baseline-wave-roadmap-test.sh`; historical checkpoint evidence is not rewritten.
- **Type/signature audit:** repeated new public types have one defining task; the later `CatalogFusionEngine` declaration in Task 37 is an intentional API extension of Task 13, not a competing definition.
- **Platform-boundary audit:** the scheduling port stays Android-free in `:catalog`; only `WorkManagerCanonicalEngineWorkScheduler` and the worker live in `:app`; existing module dependencies remain unchanged.
- **Command audit:** JVM `:plugins:api` uses `:plugins:api:test`, while Android libraries use their `testDebugUnitTest`/connected tasks.
- **Placeholder audit:** no `TBD`, `TODO`, `FIXME`, "implement later", or unnamed edge-case/test placeholders remain.
- **Safety-gate audit:** observe-only reconciliation precedes any graph mutation, and Task 32 remains the only enablement point for destructive `AUTO_MERGE`.
- **YAGNI audit:** no provider-specific quality preset, field-level user pin, event-sourced database, full chapter dedupe, or unconditional automatic split was added beyond the spec.


---

## Final Task Dependency and Enablement Rules

Executors must preserve these ordering constraints even if independent tasks are parallelized internally:

```text
Tasks 1–4   contract/regression baseline
    ↓
Tasks 5–11  schema + canonical generation foundation
    ↓
Tasks 12–21 fusion + canonical UI read-path
    ↓
Tasks 22–25 observe-only reconciliation
    ↓
Tasks 26–31 graph merge engine (still not automatically invoked)
    ↓
Task 32     only point where destructive AUTO_MERGE may be enabled
    ↓
Tasks 33–35 durable user review
    ↓
Tasks 36–38 full event-driven retroactive runtime
    ↓
Tasks 39–41 safety/reversal/observability
    ↓
Task 42     final governance + acceptance
```

The following shortcuts are forbidden during execution:

```text
Do not enable auto merge before Task 32's green gate.
Do not let UI read raw sources because a generation is still preparing.
Do not fetch Details from reconciliation/fusion/orchestration reasoning.
Do not resolve protected conflicts inside Room SQL.
Do not regenerate chapter/release IDs during merge.
Do not auto-split a committed merge because evidence/policy later changes.
Do not add provider-specific priority to get a test passing.
Do not introduce WorkManager below :app.
Do not change schema-9 ownership without updating current roadmap docs in the same change.
```


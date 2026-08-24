# Canonical Catalog Reconciliation & Fusion Engine — Phase 1 Checkpoint

Date: 2026-08-21
Status: **VERIFIED — PHASE 1 CLOSED**
Scope: implementation-plan Tasks 5–11 only. Phase 2 begins at Task 12.

## Normative sources

- Design: `../../superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md`
- Plan: `../../superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md`
- Prior accepted evidence: `canonical-catalog-reconciliation-fusion-phase-0.md`

## Accepted implementation boundary

### Task 5 — canonical contracts

- Android-free/Room-free canonical health, source preference, canonical metadata/score, field
  provenance, generation, source-summary, and `CanonicalStoryState` models are present.
- `CanonicalCatalogRepository` and redirect-aware `StoryIdentityRepository` contracts are present.
- Model invariants cover AUTO/PINNED preference, score bounds, provenance contributors, and
  Story/generation ownership.

### Task 6 — schema-9 foundation

- `OpenStoryDatabase` is schema **9** with one canonical-engine `MIGRATION_8_9`.
- The ten approved foundation tables are present: `catalog_entry_identifiers`,
  `story_canonical_state`, `canonical_generations`, `canonical_field_provenance`,
  `reconciliation_cases`, `reconciliation_case_revisions`, `story_merge_events`,
  `story_merge_reversal_events`, `story_redirects`, and `canonical_engine_work`.
- Migration bootstraps existing Stories to AUTO + `REEVALUATING`, unknown legacy creation time, and
  one coalesced `FUSION_REBUILD` work row without running reconciliation or fusion in SQL.
- Room compiler/build validation accepts the checked-in schema-9 export without an unexpected diff.
- Current roadmap governance assigns `8 -> 9` exclusively to the canonical-engine foundation; Wave 10
  notification persistence is rebased to `9 -> 10`.

### Task 7 — lossless source evidence persistence

- External identifiers persist losslessly beneath each `(pluginId, sourceId)` source record.
- Home/Details commits replace current identifier facts atomically while preserving Summary/Full
  provenance.
- Room reconstructs `CatalogSourceRecord` and derives identity/fusion fingerprints through Catalog
  pure helpers; the legacy matcher still does not interpret identifier semantics.

### Task 8 — canonical generations and redirects

- Room-backed `CanonicalCatalogRepository` and centralized `RoomStoryIdentityResolver` are present.
- Historical Story observation follows redirect changes below feature code.
- Candidate generation persistence validates source ownership and atomically promotes only a valid
  generation; stale expected-generation guards protect the active pointer.
- The connected observer test was stabilized to use one continuous subscription and explicitly prove
  the transition from the original Story to the redirected canonical Story.

### Task 9 — durable cases, audit, and dirty-work foundation

- Android-free canonical-engine work types/repository contract and Room persistence are present.
- Work coalesces by `(StoryId, workType)` with deterministic claim/retry ordering.
- Reconciliation case/revision, merge/reversal audit, redirect, and retired-Story re-key operations
  exist as persistence foundation only; no reconciliation or merge executor is enabled.

### Task 10 — representative 8 -> 9 graph migration coverage

- The migration fixture covers Catalog/Home, Library, protected/automated mappings, mapping
  rejection, canonical chapter/release, aggregation override, sync state, reading progress, and
  chapter storage.
- Existing Story/chapter/release/mapping/progress identities survive migration and foreign-key
  integrity is checked.

### Task 11 — local-only canonical bootstrap

- `CanonicalGenerationRebuilder` plus bounded rebuild reason/result types are present.
- `CanonicalBootstrapUseCase` priority-builds one Story or prewarms an ordered bounded list from local
  canonical persistence/rebuild contracts only; it has no Details/network dependency.
- Newly created schema-9 Stories receive AUTO canonical state, trustworthy host creation time, and
  coalesced Fusion rebuild work in the same Catalog persistence transaction. Migrated schema-8
  Stories retain unknown creation time.

## Developer-checkout verification evidence

The final developer checkout reported all Phase-1 acceptance gates green. The reviewed command
evidence is:

```text
./gradlew :catalog:testDebugUnitTest
  BUILD SUCCESSFUL

./gradlew :storage:room:assembleDebug
  BUILD SUCCESSFUL
  schema 9 export produced no unexpected git diff

./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.CanonicalEngineMigrationTest,app.openstory.storage.room.catalog.RoomCatalogRepositoryTest,app.openstory.storage.room.catalog.RoomCanonicalCatalogRepositoryTest,app.openstory.storage.room.catalog.RoomStoryIdentityResolverTest,app.openstory.storage.room.catalog.RoomCanonicalEngineStateTest \
  --stacktrace
  27/27 tests completed
  BUILD SUCCESSFUL

./gradlew :app:testDebugUnitTest \
  --tests app.openstory.di.CompositionPolicyTest
  BUILD SUCCESSFUL

./scripts/verify.sh
  PASS after the Phase-1 Detekt MaxLineLength cleanup
```

The first connected run exposed a race in the observer test rather than a proven production Flow
defect: a second subscription could begin after the redirect update and then `drop(1)` discarded the
only relevant emission. The test was changed to one continuous subscription synchronized on the first
emission; the full selected Room suite then passed 27/27.

The final Detekt failure consisted only of 13 Phase-1 `MaxLineLength` findings. Those lines were
wrapped without changing SQL/query runtime strings; the canonical `./scripts/verify.sh` gate then
passed. Existing structural-review warnings remain review output rather than Phase-1 gate failures.

Supplementary offline implementation evidence also included pure Kotlin/stub compilation of the new
Catalog/Room boundaries and exact replay of the 23 `MIGRATION_8_9` SQL statements against a frozen
schema-8 SQLite fixture with `PRAGMA foreign_key_check = 0`. This remains supplementary evidence; the
developer Gradle/connected results above are the acceptance evidence.

## Closure

Phase 1 is **VERIFIED and CLOSED**. Its accepted baseline is:

```text
Room schema: 9
MIGRATION_8_9 owner: Canonical Catalog Reconciliation & Fusion Engine
Wave 10 notification migration: 9 -> 10
Destructive reconciliation/merge: disabled
Canonical feature read-path cutover: not started
Next implementation step: Phase 2 / Task 12
```

Do not reopen Tasks 5–11 merely to begin Fusion. Task 12 must consume these accepted contracts and
persistence boundaries. Any later correction to the schema-9 foundation requires its own reviewed
change and contiguous schema-governance update.

<!--
DOCUMENT LIFECYCLE
Status: ACTIVE WAVE / TASKS 01-06 IMPLEMENTATION PRESENT / TASKS 02-05 VERIFIED / TASK 06 VERIFICATION OPEN
Current repository note: Tasks 01-06 have implementation present and Tasks 02-05 are verified. Task 06 implementation is present with verification still open.
Canonical execution status: ../../project/current-state.md
Planning note: Task 01 and its dependent Wave 05 interfaces were rebased on 2026-08-08 against the accepted Wave 04 source and `../../superpowers/specs/2026-08-08-wave-05-catalog-home-and-discovery-design.md`. Earlier archived planning remains historical evidence.
-->

# Wave 05 — Catalog Home and Discovery Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Deliver a useful catalog-driven Home, search, rankings, and story detail experience from bundled and community catalog plugins.

**Architecture:** Catalog plugins are normalized into platform-neutral, source-preserving snapshots before Room. Task 01 persists plugin-local cached Home atomically behind an injected canonical resolver port; Task 03 replaces the temporary source-isolated resolver with deterministic cross-catalog matching/ranking, and Task 04 combines cached source views resiliently without making Compose call plugins or Room directly.

**Approved Wave 05 design:** `../../superpowers/specs/2026-08-08-wave-05-catalog-home-and-discovery-design.md`.

**Tech Stack:** Compose, Navigation 3, plugin host, Room/Flow, coroutines, paging primitives, image loading adapter, UI tests.

## Global Constraints

- Android-only MVP; no account, cloud sync, remote chapter service, or push backend.
- Package namespace: `app.openstory`.
- Minimum SDK: 26. Compile and target SDK: 37.
- Build runtime: JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0.
- Language/UI: Kotlin 2.4.10, Jetpack Compose BOM 2026.06.00, Navigation 3 version 1.1.4.
- Persistence/background: Room 2.8.4 and WorkManager 2.11.2.
- Concurrency/serialization: Kotlin coroutines 1.11.0 and kotlinx.serialization 1.11.0.
- Dependency injection: Hilt 2.60.1.
- JavaScript plugins execute only through AndroidX JavaScriptEngine 1.1.0 with host-controlled capabilities.
- Catalog metadata and readable content remain separate plugin responsibilities.
- Reading progress belongs to `CanonicalChapter`; exact `ChapterRelease` and reader position are also retained.
- No native-code plugins, unrestricted filesystem access, arbitrary Android APIs, or undeclared network domains.
- Every persistence change needs a migration test except the explicit pre-MVP Wave 05 Task 01 schema-one planning correction documented in the approved Wave 05 design; Task 01 keeps Room at version 1, regenerates the single committed `1.json`, and uses fresh-schema integrity tests instead of a `1 -> 2` migration. Every plugin contract needs deterministic fixtures.
- TDD is mandatory: demonstrate the focused failure, implement the smallest behavior, run focused tests, then run the module suite.
- Commit after each task. Do not combine tasks across checkpoints.
- Any deterministic `*Fixture`, fake, or test assertion helper shown in a test block is created in that task’s listed test file or `:test:fixtures`; it must not call live websites.


## Role of This Wave

This wave proves the catalog half of the product and gives a clean install immediate value. It deliberately does not fetch readable chapters or make Library add wait for content sources.

## Entry Dependencies

- Wave 04 checkpoint is approved.
- Bundled plugin package can execute through the secure host.
- Catalog DTOs and Room story/catalog tables are stable.
- The pre-Wave-05 catalog contract remediation is accepted: `CatalogCard` carries explicit `ContentType` through Kotlin, Selector Schema 1, and JavaScript decoding.
- Developers may clear app data while moving between incomplete pre-MVP Wave 05 tasks; no compatibility promise exists for Task-01-only development databases.

## Exit Deliverables

- Bundled default catalog.
- Combined and catalog-specific Home.
- Deterministic cross-catalog dedupe and aggregate ranking.
- Search/filter flows.
- Catalog-preserving story detail.
- Resilient partial refresh behavior.

## File/Module Boundary

Each path listed in a task owns one responsibility. Do not move business rules into Compose screens, Room entities, JavaScript snippets, or WorkManager classes. Domain interfaces are the dependency boundary; Android adapters implement them.

---

### Task 1: Create source-preserving catalog ingestion and cached Home persistence

**Files:**
- Modify: `core/model/src/main/kotlin/app/openstory/model/CatalogEntry.kt`
- Create: `core/model/src/main/kotlin/app/openstory/model/CatalogSnapshot.kt`
- Create: `core/model/src/main/kotlin/app/openstory/model/CatalogSourceMetadata.kt`
- Create: `core/model/src/main/kotlin/app/openstory/model/CatalogHome.kt`
- Create: `core/model/src/main/kotlin/app/openstory/model/CatalogCanonicalResolver.kt`
- Modify: `core/model/src/test/kotlin/app/openstory/model/CatalogEntryTest.kt`
- Modify: `core/model/src/test/kotlin/app/openstory/model/CanonicalStoryTest.kt`
- Modify: `core/database/src/main/kotlin/app/openstory/database/entity/StoryEntities.kt`
- Create: `core/database/src/main/kotlin/app/openstory/database/entity/CatalogHomeEntities.kt`
- Create: `core/database/src/main/kotlin/app/openstory/database/dao/CatalogDao.kt`
- Modify: `core/database/src/main/kotlin/app/openstory/database/OpenStoryDatabase.kt`
- Modify: `core/database/src/main/kotlin/app/openstory/database/mapping/StoryEntityMapper.kt`
- Create: `core/database/src/main/kotlin/app/openstory/database/mapping/CatalogMapper.kt`
- Create: `core/database/src/main/kotlin/app/openstory/database/repository/CatalogRepository.kt`
- Create: `core/database/src/main/kotlin/app/openstory/database/repository/RoomCatalogRepository.kt`
- Create: `core/database/src/main/kotlin/app/openstory/database/repository/SourceIsolatedCatalogResolver.kt`
- Modify: `core/database/src/androidTest/kotlin/app/openstory/database/DatabaseBaselineTest.kt`
- Modify: `core/database/src/test/kotlin/app/openstory/database/SchemaPolicyTest.kt`
- Modify: `core/database/src/androidTest/kotlin/app/openstory/database/repository/CatalogMetadataRepositoryTest.kt`
- Modify: `core/database/src/androidTest/kotlin/app/openstory/database/repository/StoryPurgeRepositoryTest.kt`
- Create: `core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomCatalogRepositoryTest.kt`
- Replace: `core/database/schemas/app.openstory.database.OpenStoryDatabase/1.json`

**Interfaces:**
- Consumes: normalized catalog snapshot models, existing canonical story/catalog tables, a pure `CatalogCanonicalResolver`, Room transactions, and injected `Clock`.
- Produces: one atomic plugin-local Home ingest plus source-preserving cached reads for later matching/Home use cases.

The Task 01 domain boundary is:

```kotlin
package app.openstory.model

data class CatalogSnapshot(
    val pluginId: PluginId,
    val pluginVersion: String,
    val sections: List<CatalogSnapshotSection>,
)

data class CatalogSnapshotSection(
    val sourceId: String,
    val title: String,
    val items: List<CatalogSnapshotItem>,
)

data class CatalogSnapshotItem(
    val sourceId: String,
    val title: String,
    val contentType: ContentType,
    val authors: List<String>,
    val coverReference: String?,
    val score: Double?,
    val scoreScale: Double?,
)

data class CatalogSourceMetadata(
    val sourceId: String,
    val sourceUrl: String?,
    val title: String,
    val aliases: Set<String>,
    val authors: Set<String>,
    val description: String?,
    val genres: Set<String>,
    val contentType: ContentType,
    val languageTags: Set<LanguageTag>,
    val coverReference: String?,
    val publicationStatus: String?,
    val score: Double?,
    val scoreScale: Double?,
    val popularityRank: Long?,
)

sealed interface CatalogCanonicalResolution {
    val storyId: StoryId

    data class Existing(
        override val storyId: StoryId,
    ) : CatalogCanonicalResolution

    data class Create(
        override val storyId: StoryId,
    ) : CatalogCanonicalResolution
}

fun interface CatalogCanonicalResolver {
    fun resolve(
        pluginId: PluginId,
        source: CatalogSnapshotItem,
        candidates: List<CanonicalStory>,
    ): CatalogCanonicalResolution
}
```

`CatalogEntry` is extended now with the current Catalog API metadata needed by later Wave 05 tasks: `contentType`, `aliases`, `languageTags`, `popularityRank`, `pluginVersion`, and `fetchedAtEpochMillis`. Do not invent rating-count/raw-metadata-version values that the current plugin contract does not expose.

The repository contract is:

```kotlin
package app.openstory.database.repository

import app.openstory.common.AppResult
import app.openstory.model.CatalogEntryWithStory
import app.openstory.model.CatalogHomeSnapshot
import app.openstory.model.CatalogSnapshot
import app.openstory.model.CatalogSourceMetadata
import app.openstory.model.PluginId
import kotlinx.coroutines.flow.Flow

interface CatalogRepository {
    suspend fun ingest(snapshot: CatalogSnapshot): AppResult<Unit>

    suspend fun upsertSourceMetadata(
        pluginId: PluginId,
        pluginVersion: String,
        metadata: CatalogSourceMetadata,
    ): AppResult<CatalogEntryWithStory>

    suspend fun catalogEntry(
        pluginId: PluginId,
        sourceId: String,
    ): AppResult<CatalogEntryWithStory?>

    fun observeCatalogHome(
        pluginId: PluginId,
    ): Flow<CatalogHomeSnapshot?>

    fun observeCatalogHomes(): Flow<List<CatalogHomeSnapshot>>
}
```

The cached read model is exact and storage-neutral:

```kotlin
data class CatalogEntryWithStory(
    val storyId: StoryId,
    val entry: CatalogEntry,
)

data class CatalogHomeSnapshot(
    val pluginId: PluginId,
    val pluginVersion: String,
    val refreshedAtEpochMillis: Long,
    val sections: List<CatalogHomeSection>,
)

data class CatalogHomeSection(
    val sourceId: String,
    val title: String,
    val items: List<CatalogEntryWithStory>,
)
```

This is a storage/domain projection, not a Compose/UI model.

**Persistence shape:**

- `catalog_entries` keeps `catalog_entry_id` as its primary key and adds a **unique** index on `(catalog_plugin_id, external_story_id)`.
- `catalog_entries` gains durable source fields for `content_type`, `aliases_json`, `language_tags_json`, `popularity_rank`, `plugin_version`, and `fetched_at_epoch_millis`.
- `catalog_home_snapshots` is keyed by `catalog_plugin_id` and stores the last successful `plugin_version` and `refreshed_at_epoch_millis`.
- `catalog_home_sections` is keyed by `(catalog_plugin_id, section_source_id)` and stores `section_position`; `(catalog_plugin_id, section_position)` is unique.
- `catalog_home_items` is keyed by `(catalog_plugin_id, section_source_id, catalog_entry_id)`, stores `item_position`, has a composite foreign key to its section plus a foreign key to `catalog_entries`, and uniquely enforces one position per section.
- Home-section deletion cascades only to Home item memberships. No Home foreign key may cascade into `canonical_stories` or delete `catalog_entries` merely because a card disappeared.

**Sparse-card merge rule:** Home cards own only title, content type, authors, cover reference, score/scale, plugin version, and fetch timestamp. Refreshing Home must not clear richer source URL/aliases/description/genres/language/popularity/publication metadata already stored for the same source entry.

**Acceptance:**
- Same `(pluginId, sourceId)` refresh updates one source entry; same `sourceId` from another plugin remains a separate entry.
- Database identity is enforced by the unique composite catalog-source index, not only by mapper convention.
- Refreshing catalog A does not mutate catalog B metadata, Home sections, or freshness.
- Section order and item order round-trip exactly.
- Removing a card from a new Home snapshot removes only its Home membership and leaves its `CatalogEntry` and `CanonicalStory` intact.
- One ingest captures one clock value and retains that timestamp plus the hosted plugin version for diagnostics/stale UI.
- Home refresh preserves richer details already stored for the same catalog entry.
- `upsertSourceMetadata(...)` enriches source metadata/provenance without adding, removing, or reordering Home memberships.
- Discovery ingest never creates `library_entries`.
- Any failure after transaction start rolls back source metadata, canonical links, Home memberships, and snapshot freshness together, so the previous complete snapshot remains readable.
- The temporary resolver creates one stable source-isolated canonical story for each new source identity. A previously linked source identity keeps its existing canonical link.
- Fresh Room creation contains the new Home tables/indexes with zero `PRAGMA foreign_key_check` violations.
- Room remains `version = 1`; exactly one schema file, `1.json`, remains committed. No `MIGRATION_1_2`, migration fixture, or compatibility adapter is added.

- [ ] **Step 1: Write the failing domain tests**

Extend `CatalogEntryTest.kt` with explicit source/provenance metadata and add snapshot invariant coverage:

```kotlin
@Test
fun catalogEntryRetainsWave05SourceMetadataAndProvenance() {
    val entry = CatalogEntry(
        id = CatalogEntryId("catalog.example:story-1"),
        catalogPluginId = PluginId("catalog.example"),
        externalStoryId = "story-1",
        sourceUrl = null,
        title = "Example",
        aliases = setOf("Example Alias"),
        authors = setOf("Author"),
        description = null,
        genres = emptySet(),
        contentType = ContentType.WEB_NOVEL,
        languageTags = setOf(LanguageTag("en")),
        coverReference = "https://catalog.example/cover.jpg",
        publicationStatus = null,
        score = 8.4,
        scoreScale = 10.0,
        popularityRank = 12,
        pluginVersion = "1.2.3",
        fetchedAtEpochMillis = 1234,
    )

    assertEquals(ContentType.WEB_NOVEL, entry.contentType)
    assertEquals(setOf("Example Alias"), entry.aliases)
    assertEquals("1.2.3", entry.pluginVersion)
    assertEquals(1234, entry.fetchedAtEpochMillis)
}
```

Add focused `CatalogSnapshot` tests proving blank plugin versions, duplicate section IDs, duplicate item source IDs within one section, invalid score/scale pairs, and blank section/item IDs are rejected.

- [ ] **Step 2: Run the model RED gate**

```bash
./gradlew :core:model:test --tests app.openstory.model.CatalogEntryTest --stacktrace
```

Expected: **FAIL** because the Wave 05 source/provenance fields and snapshot models do not exist.

- [ ] **Step 3: Add the minimal domain boundary and make model tests green**

Create the snapshot/source-metadata/Home/read-model types and resolver contract shown above. Implement `SourceIsolatedCatalogResolver` with a deterministic namespaced story ID for new identities, for example `StoryId("catalog:${pluginId.value}:${source.sourceId}")`; it ignores cross-catalog candidates in Task 01. Update the existing direct `CatalogEntry(...)` constructors in model/database tests and `StoryEntityMapper` to carry the new fields losslessly.

```bash
./gradlew :core:model:test --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 4: Write the failing Room ingestion and schema tests**

Create `RoomCatalogRepositoryTest.kt` with deterministic fixture methods and these exact behavioral tests:

```text
refreshPreservesOtherCatalogMetadata
samePluginAndSourceUpdatesWithoutDuplicateRow
sameSourceIdFromDifferentPluginsRemainsDistinct
sectionAndItemOrderRoundTrips
removedHomeCardKeepsCatalogEntryAndCanonicalStory
pluginVersionAndSingleRefreshTimestampAreRetained
homeRefreshDoesNotEraseRicherCatalogDetails
sourceMetadataUpsertDoesNotChangeHomeMembership
discoveryIngestDoesNotCreateLibraryMembership
failedTransactionKeepsPreviousCompleteSnapshot
```

Also update `DatabaseBaselineTest.freshDatabaseContainsCurrentBaselineTables()` to require `catalog_home_snapshots`, `catalog_home_sections`, and `catalog_home_items`, and add this unit-level schema policy assertion:

```kotlin
@Test
fun schemaOneContainsWave05CatalogHomePersistence() {
    val schema = findRepositoryRoot().resolve(
        "core/database/schemas/app.openstory.database.OpenStoryDatabase/1.json",
    ).readText()

    listOf(
        "catalog_home_snapshots",
        "catalog_home_sections",
        "catalog_home_items",
        "index_catalog_entries_plugin_external_story",
    ).forEach { required ->
        assertTrue(required in schema, "Missing Wave 05 schema object: $required")
    }
}
```

- [ ] **Step 5: Run the persistence RED gate**

```bash
./gradlew :core:database:testDebugUnitTest \
  --tests app.openstory.database.SchemaPolicyTest.schemaOneContainsWave05CatalogHomePersistence \
  --stacktrace
./gradlew :core:database:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.database.repository.RoomCatalogRepositoryTest \
  --stacktrace
```

Expected: **FAIL** because Home entities/DAO/repository/indexes and schema export do not exist.

- [ ] **Step 6: Implement one atomic plugin-local ingest and complete schema 1 in place**

Implement `CatalogDao`/`RoomCatalogRepository` so one `OpenStoryDatabase.withTransaction` call:

```text
now = clock.nowEpochMillis()
load/retain existing source identity links
load canonical candidates once for new source identities
upsert only card-owned CatalogEntry fields + provenance
create/link canonical story only when the source identity is new
replace only snapshot.pluginId Home sections/items
upsert snapshot.pluginVersion + now
commit
```

Use conflict/update SQL that preserves detail-owned columns when the sparse card has no value for them. Replace the current schema-one JSON with the Room compiler export; do not add a migration object or second schema JSON.

- [ ] **Step 7: Re-run focused persistence and fresh-schema verification**

```bash
./gradlew :core:database:testDebugUnitTest --stacktrace
./gradlew :core:database:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.database.repository.RoomCatalogRepositoryTest,app.openstory.database.DatabaseBaselineTest \
  --stacktrace
./scripts/verify-room-schema-stability.sh
```

Expected: unit/instrumentation tests finish with **BUILD SUCCESSFUL**, `DatabaseBaselineTest` reports no foreign-key violation, and schema stability prints the committed schema fingerprint.

- [ ] **Step 8: Run the affected module gate and commit**

```bash
./gradlew :core:model:test :core:database:testDebugUnitTest --stacktrace
./scripts/check-module-dependencies.sh

git add \
  core/model/src/main/kotlin/app/openstory/model/CatalogEntry.kt \
  core/model/src/main/kotlin/app/openstory/model/CatalogSnapshot.kt \
  core/model/src/main/kotlin/app/openstory/model/CatalogSourceMetadata.kt \
  core/model/src/main/kotlin/app/openstory/model/CatalogHome.kt \
  core/model/src/main/kotlin/app/openstory/model/CatalogCanonicalResolver.kt \
  core/model/src/test/kotlin/app/openstory/model/CatalogEntryTest.kt \
  core/model/src/test/kotlin/app/openstory/model/CanonicalStoryTest.kt \
  core/database/src/main/kotlin/app/openstory/database/entity/StoryEntities.kt \
  core/database/src/main/kotlin/app/openstory/database/entity/CatalogHomeEntities.kt \
  core/database/src/main/kotlin/app/openstory/database/dao/CatalogDao.kt \
  core/database/src/main/kotlin/app/openstory/database/OpenStoryDatabase.kt \
  core/database/src/main/kotlin/app/openstory/database/mapping/StoryEntityMapper.kt \
  core/database/src/main/kotlin/app/openstory/database/mapping/CatalogMapper.kt \
  core/database/src/main/kotlin/app/openstory/database/repository/CatalogRepository.kt \
  core/database/src/main/kotlin/app/openstory/database/repository/RoomCatalogRepository.kt \
  core/database/src/main/kotlin/app/openstory/database/repository/SourceIsolatedCatalogResolver.kt \
  core/database/src/androidTest/kotlin/app/openstory/database/DatabaseBaselineTest.kt \
  core/database/src/test/kotlin/app/openstory/database/SchemaPolicyTest.kt \
  core/database/src/androidTest/kotlin/app/openstory/database/repository/CatalogMetadataRepositoryTest.kt \
  core/database/src/androidTest/kotlin/app/openstory/database/repository/StoryPurgeRepositoryTest.kt \
  core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomCatalogRepositoryTest.kt \
  core/database/schemas/app.openstory.database.OpenStoryDatabase/1.json

git diff --cached --check
git commit -m "catalog: persist source-owned home snapshots"
```

### Task 2: Bundle a deterministic default catalog plugin

**Files:**
- Create: bundled-plugins/default-catalog/manifest.json
- Create: bundled-plugins/default-catalog/selector.json
- Create: bundled-plugins/default-catalog/fixtures/home.html
- Create: bundled-plugins/default-catalog/fixtures/search.html
- Create: app/src/main/assets/plugins/default-catalog.osp
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/BundledPluginBootstrapper.kt
- Test: core/plugin-host/src/androidTest/kotlin/app/openstory/plugin/host/install/BundledPluginBootstrapperTest.kt

**Interfaces:**
- Consumes: Package format, selector runtime, installer, registry, and catalog contract suite.
- Produces: A signed/trusted bundled catalog package installed idempotently on first launch and upgradable through the same version machinery as community plugins.

**Acceptance:**
- Clean install has one enabled catalog without network-time installation.
- Bundled package still passes checksum/layout/contract validation.
- User disable state survives app upgrade.
- A newer bundled version upgrades only under normal capability-diff rules.

**Implementation notes:**
- Use a legally permitted/open metadata endpoint or static development fixture until a production catalog integration is selected.
- Never embed third-party credentials in the package.
- Document attribution/license in the plugin package and app About screen.

- [ ] **Step 1: Write the failing test**

Create `core/plugin-host/src/androidTest/kotlin/app/openstory/plugin/host/install/BundledPluginBootstrapperTest.kt`:

```kotlin
package app.openstory.plugin.host.install

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertFalse

class BundledPluginBootstrapperTest {
    @Test fun bootstrapIsIdempotentAndPreservesDisabledState() = runTest {
        val fixture = bundledBootstrapFixture()
        fixture.bootstrapper.ensureInstalled()
        fixture.registry.setEnabled("org.openstory.catalog.default", false)
        fixture.bootstrapper.ensureInstalled()
        assertFalse(fixture.registry.find("org.openstory.catalog.default")!!.enabled)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:plugin-host:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.plugin.host.install.BundledPluginBootstrapperTest
```

Expected: **FAIL** because no bundled package or bootstrap policy exists.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/BundledPluginBootstrapper.kt`:

```kotlin
package app.openstory.plugin.host.install

class BundledPluginBootstrapper(
    private val assets: BundledPluginAssets,
    private val registry: PluginRegistry,
    private val installer: PluginInstaller,
) {
    suspend fun ensureInstalled() {
        assets.packages().forEach { pkg ->
            val current = registry.find(pkg.pluginId)
            if (current == null || pkg.version.isNewerThan(current.activeVersion)) installer.install(pkg.request())
        }
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:plugin-host:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.plugin.host.install.BundledPluginBootstrapperTest
./gradlew :core:plugin-host:testDebugUnitTest :core:plugin-host:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add bundled-plugins/default-catalog/manifest.json bundled-plugins/default-catalog/selector.json bundled-plugins/default-catalog/fixtures/home.html bundled-plugins/default-catalog/fixtures/search.html app/src/main/assets/plugins/default-catalog.osp core/plugin-host/src/main/kotlin/app/openstory/plugin/host/install/BundledPluginBootstrapper.kt core/plugin-host/src/androidTest/kotlin/app/openstory/plugin/host/install/BundledPluginBootstrapperTest.kt
git commit -m "catalog: ship default bundled catalog plugin"
```

### Task 3: Add deterministic catalog matching and aggregate ranking

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `README.md`
- Create: `core/matching/build.gradle.kts`
- Create: `core/matching/src/main/kotlin/app/openstory/matching/TitleNormalizer.kt`
- Create: `core/matching/src/main/kotlin/app/openstory/matching/CatalogStoryResolver.kt`
- Create: `core/matching/src/main/kotlin/app/openstory/matching/CatalogMatchPolicy.kt`
- Create: `core/matching/src/main/kotlin/app/openstory/matching/CatalogMatchResult.kt`
- Create: `core/matching/src/main/kotlin/app/openstory/matching/AggregateRanking.kt`
- Test: `core/matching/src/test/kotlin/app/openstory/matching/CatalogStoryResolverTest.kt`
- Test: `core/matching/src/test/kotlin/app/openstory/matching/AggregateRankingTest.kt`

**Interfaces:**
- Consumes: Task 01 `CatalogCanonicalResolver`, normalized source/canonical metadata, and source-specific scores.
- Produces: a pure deterministic `CatalogStoryResolver : CatalogCanonicalResolver` plus transparent aggregate ranking; Task 01 Room persistence remains unchanged.

**Module boundary:** `:core:matching` is a Kotlin/JVM module. Its direct production project dependencies are `:core:common` and `:core:model`; it must not depend on Android, Room, plugin host, feature modules, or plugin wire DTOs. Add the module atomically per `docs/contributing/adding-a-module.md`.

**Acceptance:**
- Exact trusted external identity within an approved direct mapping is highest-confidence evidence but content type conflicts still block auto-link.
- High-confidence normalized title + compatible author evidence may return `CatalogCanonicalResolution.Existing`.
- Same-title author conflicts and other ambiguous candidates remain separate; the resolver returns the deterministic source-isolated `Create` ID and emits a review explanation rather than destructively merging.
- Missing optional metadata is neutral rather than negative evidence.
- Resolver output and aggregate ranking are independent of input ordering.
- Aggregate score normalizes only for ordering, preserves original source score/scale, weights by configured catalog priority, and uses stable tie-breakers.
- The implementation plugs into the Task 01 resolver port without adding or changing Room tables.
- Development data produced by the temporary Task 01 resolver may be cleared before exercising the Task 03 resolver; no runtime DB migration/reconciliation layer is created for incomplete pre-MVP Task-01-only data.

- [ ] **Step 1: Add the module gate as part of the first failing behavior**

Create the module build file with the JVM convention plugin and declare only `:core:common`/`:core:model`. Add `include(":core:matching")`, the matching architecture-policy entry, and the README graph node in the same change as the tests below.

Create `CatalogStoryResolverTest.kt`:

```kotlin
@Test
fun sameTitleDifferentAuthorDoesNotAutoMerge() {
    val resolver = CatalogStoryResolver(defaultCatalogMatchPolicy())
    val result = resolver.compare(
        catalogCandidate(title = "Reborn", authors = setOf("Author A")),
        canonicalCandidate(title = "Reborn", authors = setOf("Author B")),
    )

    assertEquals(MergeDecision.REVIEW, result.decision)
    assertEquals(true, result.explanation.authorConflict)
}
```

Also add a resolver-port test proving a high-confidence candidate returns `CatalogCanonicalResolution.Existing` and an ambiguous candidate returns the same deterministic `Create` ID regardless of candidate ordering.

- [ ] **Step 2: Run the RED and architecture gates**

```bash
./gradlew :core:matching:test \
  --tests app.openstory.matching.CatalogStoryResolverTest.sameTitleDifferentAuthorDoesNotAutoMerge \
  --stacktrace
./scripts/check-module-dependencies.sh
```

Expected: focused test **FAILS** because matching logic is absent; the module-dependency gate passes only if the new module declaration/policy is complete and exact.

- [ ] **Step 3: Add the minimal normalizer/resolver/ranking implementation**

Create `TitleNormalizer` as locale-independent NFKC/lowercase/token cleanup. Implement `CatalogStoryResolver` as a pure scorer/decision engine that implements the Task 01 port; it never writes Room itself. Implement `AggregateRanking` so source scores are normalized only into a derived ordering value while the source `CatalogEntry` remains unchanged.

```kotlin
class CatalogStoryResolver(
    private val policy: CatalogMatchPolicy,
) : CatalogCanonicalResolver {
    override fun resolve(
        pluginId: PluginId,
        source: CatalogSnapshotItem,
        candidates: List<CanonicalStory>,
    ): CatalogCanonicalResolution {
        val ranked = candidates
            .map { candidate -> compare(source, candidate) }
            .sortedWith(matchResultOrdering())
        val best = ranked.firstOrNull()

        return if (best != null && best.decision == MergeDecision.AUTO_LINK) {
            CatalogCanonicalResolution.Existing(best.storyId)
        } else {
            CatalogCanonicalResolution.Create(
                StoryId("catalog:${pluginId.value}:${source.sourceId}"),
            )
        }
    }
}
```

- [ ] **Step 4: Re-run focused/module tests and architecture policy**

```bash
./gradlew :core:matching:test --stacktrace
./scripts/check-module-dependencies.sh
```

Expected: **BUILD SUCCESSFUL** and zero architecture-policy violations.

- [ ] **Step 5: Commit the independently reviewable module**

```bash
git add \
  settings.gradle.kts \
  config/architecture/module-boundaries.json \
  README.md \
  core/matching/build.gradle.kts \
  core/matching/src/main/kotlin/app/openstory/matching/TitleNormalizer.kt \
  core/matching/src/main/kotlin/app/openstory/matching/CatalogStoryResolver.kt \
  core/matching/src/main/kotlin/app/openstory/matching/CatalogMatchPolicy.kt \
  core/matching/src/main/kotlin/app/openstory/matching/CatalogMatchResult.kt \
  core/matching/src/main/kotlin/app/openstory/matching/AggregateRanking.kt \
  core/matching/src/test/kotlin/app/openstory/matching/CatalogStoryResolverTest.kt \
  core/matching/src/test/kotlin/app/openstory/matching/AggregateRankingTest.kt

git diff --cached --check
git commit -m "matching: deduplicate catalog stories and rank home"
```

### Task 4: Add Home use cases and resilient multi-catalog refresh

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `README.md`
- Create: `feature/home/build.gradle.kts`
- Create: `feature/home/src/main/kotlin/app/openstory/home/domain/CatalogSnapshotMapper.kt`
- Create: `feature/home/src/main/kotlin/app/openstory/home/domain/ObserveCombinedHome.kt`
- Create: `feature/home/src/main/kotlin/app/openstory/home/domain/RefreshHome.kt`
- Create: `feature/home/src/main/kotlin/app/openstory/home/model/HomeUiModel.kt`
- Test: `feature/home/src/test/kotlin/app/openstory/home/domain/CatalogSnapshotMapperTest.kt`
- Test: `feature/home/src/test/kotlin/app/openstory/home/domain/RefreshHomeTest.kt`

**Interfaces:**
- Consumes: `PluginHost.enabledCatalogs()`, pinned `HostedPlugin.id/version`, repaired Catalog Home DTOs, Task 01 `CatalogRepository`, Task 03 resolver/ranking, dispatchers, and typed results.
- Produces: independent per-plugin refresh plus combined/source-specific cached Home flows. Plugin DTO conversion ends in `CatalogSnapshotMapper`; repository/Room never receives plugin wire DTOs.

**Module boundary:** `:feature:home` is an Android library introduced atomically per `docs/contributing/adding-a-module.md`. Its reviewed direct production project dependencies are `:core:common`, `:core:model`, `:core:database`, `:core:plugin-host`, and `:core:matching`; test-only fixture dependencies stay test-scoped.

**Acceptance:**
- `CatalogSnapshotMapper` copies `HostedPlugin.id` and `HostedPlugin.version` into every normalized snapshot and maps card `contentType` without fallback/default guessing.
- One failing catalog does not erase, block, or rewrite successful catalogs.
- A successful plugin response calls `repository.ingest(snapshot)` exactly for that plugin; a failed plugin response leaves its previous cached snapshot untouched.
- Refresh reports partial success plus cached `refreshedAtEpochMillis`/stale state per plugin.
- `ObserveCombinedHome` subscribes to Task 01 cached repository flows, so cached Home is available before network refresh completes.
- Combined cards dedupe by canonical story ID using Task 03 matching/ranking while source-owned section labels and source score/scale remain visible.
- Refresh concurrency is bounded and cancellation propagates; one child failure is isolated with `supervisorScope` rather than cancelling siblings.

- [ ] **Step 1: Add the module boundary and failing snapshot-mapper test**

Create `CatalogSnapshotMapperTest.kt`:

```kotlin
@Test
fun hostedVersionAndCardContentTypeSurviveNormalization() {
    val hosted = hostedCatalog(
        id = "catalog.a",
        version = "2.3.4",
    )
    val sections = listOf(
        catalogSection(
            sourceId = "trending",
            card = catalogCard(
                sourceId = "story-1",
                contentType = ContentType.WEB_NOVEL,
            ),
        ),
    )

    val snapshot = CatalogSnapshotMapper().map(hosted, sections)

    assertEquals(PluginId("catalog.a"), snapshot.pluginId)
    assertEquals("2.3.4", snapshot.pluginVersion)
    assertEquals(ContentType.WEB_NOVEL, snapshot.sections.single().items.single().contentType)
}
```

Add `:feature:home` to settings, architecture policy, and README in the same task.

- [ ] **Step 2: Run mapper RED plus architecture gate**

```bash
./gradlew :feature:home:testDebugUnitTest \
  --tests app.openstory.home.domain.CatalogSnapshotMapperTest.hostedVersionAndCardContentTypeSurviveNormalization \
  --stacktrace
./scripts/check-module-dependencies.sh
```

Expected: mapper test **FAILS** because the Home application module/mapper is absent; architecture gate passes only with the exact new-module declaration.

- [ ] **Step 3: Write the failing partial-refresh test**

Create `RefreshHomeTest.kt`:

```kotlin
@Test
fun oneCatalogFailureStillPersistsSuccessfulCatalog() = runTest {
    val fixture = homeRefreshFixture(
        successful = setOf("catalog.a"),
        failing = setOf("catalog.b"),
    )

    val report = fixture.useCase()

    assertEquals(setOf("catalog.a"), report.succeeded.map { it.value }.toSet())
    assertEquals(setOf("catalog.b"), report.failed.keys.map { it.value }.toSet())
    assertEquals(listOf("catalog.a"), fixture.repository.ingestedPluginIds())
    assertEquals("2.3.4", fixture.repository.savedSnapshots.single().pluginVersion)
}
```

Also add a cached-flow test showing `ObserveCombinedHome` emits stored sections before the test completes a suspended refresh call.

- [ ] **Step 4: Implement the minimal mapper/refresh/observe use cases**

`RefreshHome` pins each hosted plugin identity/version for the operation and maps successful DTOs before persistence:

```kotlin
class RefreshHome(
    private val host: PluginHost,
    private val mapper: CatalogSnapshotMapper,
    private val repository: CatalogRepository,
    private val dispatchers: AppDispatchers,
) {
    suspend operator fun invoke(): HomeRefreshReport = supervisorScope {
        host.enabledCatalogs()
            .map { hosted ->
                async(dispatchers.io) {
                    val result = hosted.instance.home(defaultHomeRequest())
                    hosted to result
                }
            }
            .awaitAll()
            .fold(HomeRefreshReport()) { report, (hosted, result) ->
                when (result) {
                    is AppResult.Success -> {
                        val snapshot = mapper.map(hosted, result.value)
                        report.record(hosted.id, repository.ingest(snapshot))
                    }
                    is AppResult.Failure -> report.record(hosted.id, result)
                }
            }
    }
}
```

`ObserveCombinedHome` reads `repository.observeCatalogHomes()` and derives combined/source-specific models with Task 03 ranking; it does not invoke plugins.

- [ ] **Step 5: Re-run the Home module and architecture gates**

```bash
./gradlew :feature:home:testDebugUnitTest --stacktrace
./scripts/check-module-dependencies.sh
```

Expected: **BUILD SUCCESSFUL** and zero module-boundary violations.

- [ ] **Step 6: Commit the independently reviewable Home application boundary**

```bash
git add \
  settings.gradle.kts \
  config/architecture/module-boundaries.json \
  README.md \
  feature/home/build.gradle.kts \
  feature/home/src/main/kotlin/app/openstory/home/domain/CatalogSnapshotMapper.kt \
  feature/home/src/main/kotlin/app/openstory/home/domain/ObserveCombinedHome.kt \
  feature/home/src/main/kotlin/app/openstory/home/domain/RefreshHome.kt \
  feature/home/src/main/kotlin/app/openstory/home/model/HomeUiModel.kt \
  feature/home/src/test/kotlin/app/openstory/home/domain/CatalogSnapshotMapperTest.kt \
  feature/home/src/test/kotlin/app/openstory/home/domain/RefreshHomeTest.kt

git diff --cached --check
git commit -m "home: refresh catalogs independently"
```

### Task 5: Build combined and catalog-specific Compose Home screens

**Files:**
- Create: feature/home/src/main/kotlin/app/openstory/home/ui/HomeRoute.kt
- Create: feature/home/src/main/kotlin/app/openstory/home/ui/HomeViewModel.kt
- Create: feature/home/src/main/kotlin/app/openstory/home/ui/HomeScreen.kt
- Create: feature/home/src/main/kotlin/app/openstory/home/ui/CatalogHomeScreen.kt
- Create: feature/home/src/main/kotlin/app/openstory/home/ui/HomeCard.kt
- Test: feature/home/src/test/kotlin/app/openstory/home/ui/HomeViewModelTest.kt
- Test: feature/home/src/androidTest/kotlin/app/openstory/home/ui/HomeScreenTest.kt

**Interfaces:**
- Consumes: Home use cases/UI model, image loader adapter, app navigation route.
- Produces: Accessible Home UI with combined sections, per-catalog selection, loading/stale/error states, and canonical story navigation.

**Acceptance:**
- First cached content is visible while refresh runs.
- Partial failures show non-blocking source-specific status.
- Cards identify score source/scale and content type.
- Semantics expose story title, source section, and score without relying on color.

**Implementation notes:**
- Use lazy rows/columns with stable keys from canonical story IDs.
- Avoid nested uncontrolled scrolling; section rows use fixed card dimensions.
- Expose a source switcher/dropdown and a dedicated route for catalog-specific Home.

- [ ] **Step 1: Write the failing test**

Create `feature/home/src/test/kotlin/app/openstory/home/ui/HomeViewModelTest.kt`:

```kotlin
package app.openstory.home.ui

import app.cash.turbine.test
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class HomeViewModelTest {
    @Test fun cachedSectionsRemainVisibleDuringRefresh() = runTest {
        val fixture = homeViewModelFixtureWithCachedSection()
        fixture.viewModel.state.test {
            assertTrue(awaitItem().sections.isNotEmpty())
            fixture.viewModel.refresh()
            val refreshing = awaitItem()
            assertTrue(refreshing.refreshing && refreshing.sections.isNotEmpty())
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:home:testDebugUnitTest --tests app.openstory.home.ui.HomeViewModelTest.cachedSectionsRemainVisibleDuringRefresh
```

Expected: **FAIL** because Home ViewModel and state behavior are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/home/src/main/kotlin/app/openstory/home/ui/HomeViewModel.kt`:

```kotlin
package app.openstory.home.ui

@HiltViewModel
class HomeViewModel @Inject constructor(
    observeHome: ObserveCombinedHome,
    private val refreshHome: RefreshHome,
) : ViewModel() {
    private val refreshing = MutableStateFlow(false)
    val state = combine(observeHome(), refreshing) { home, busy -> home.copy(refreshing = busy) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeUiModel.Empty)
    fun refresh() = viewModelScope.launch { refreshing.value = true; try { refreshHome() } finally { refreshing.value = false } }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:home:testDebugUnitTest --tests app.openstory.home.ui.HomeViewModelTest.cachedSectionsRemainVisibleDuringRefresh
./gradlew :feature:home:testDebugUnitTest :feature:home:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/home/src/main/kotlin/app/openstory/home/ui/HomeRoute.kt feature/home/src/main/kotlin/app/openstory/home/ui/HomeViewModel.kt feature/home/src/main/kotlin/app/openstory/home/ui/HomeScreen.kt feature/home/src/main/kotlin/app/openstory/home/ui/CatalogHomeScreen.kt feature/home/src/main/kotlin/app/openstory/home/ui/HomeCard.kt feature/home/src/test/kotlin/app/openstory/home/ui/HomeViewModelTest.kt feature/home/src/androidTest/kotlin/app/openstory/home/ui/HomeScreenTest.kt
git commit -m "home: add combined and per-catalog compose screens"
```

### Task 6: Implement catalog search, filters, and source-preserving story detail

**Files:**
- Modify: `settings.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `README.md`
- Create: `feature/home/src/main/kotlin/app/openstory/home/domain/SearchCatalogs.kt`
- Create: `feature/home/src/main/kotlin/app/openstory/home/ui/SearchViewModel.kt`
- Create: `feature/home/src/main/kotlin/app/openstory/home/ui/SearchScreen.kt`
- Create: `feature/story/build.gradle.kts`
- Create: `feature/story/src/main/kotlin/app/openstory/story/domain/CatalogDetailsMapper.kt`
- Create: `feature/story/src/main/kotlin/app/openstory/story/ui/StoryDetailViewModel.kt`
- Create: `feature/story/src/main/kotlin/app/openstory/story/ui/StoryDetailScreen.kt`
- Test: `feature/home/src/test/kotlin/app/openstory/home/domain/SearchCatalogsTest.kt`
- Test: `feature/story/src/test/kotlin/app/openstory/story/domain/CatalogDetailsMapperTest.kt`
- Test: `feature/story/src/test/kotlin/app/openstory/story/ui/StoryDetailViewModelTest.kt`

**Interfaces:**
- Consumes: Catalog plugin search/filter/details contracts, Task 01 `CatalogRepository` + `CatalogSourceMetadata`, Task 03 resolver, cached canonical candidates, and Navigation story/source IDs.
- Produces: cancellable multi-catalog search plus story detail that enriches the existing source entry through `upsertSourceMetadata(...)` while keeping every catalog's ratings/descriptions/provenance distinct.

**Module boundary:** `:feature:story` is an Android library introduced atomically per `docs/contributing/adding-a-module.md`. Its direct production project dependencies are `:core:common`, `:core:model`, `:core:database`, `:core:plugin-host`, and `:core:matching`; test-only fixture dependencies remain test-scoped. It must not expose Room entities or plugin DTOs from its public UI state.

**Acceptance:**
- Blank queries do not invoke plugins.
- Search cancellation prevents stale results replacing newer query results.
- Search results are canonicalized for display using Task 03 matching without flattening source badges or source scores.
- `CatalogDetailsMapper` copies hosted plugin ID/version plus details fields into `CatalogSourceMetadata`; no content type, score scale, URL, alias, language, or popularity value is guessed.
- Opening/enriching details calls Task 01 `upsertSourceMetadata(...)`; it does not add/remove/reorder Home membership and does not create Library membership.
- Story detail renders each linked catalog score with its own raw value, scale, source label, and `fetchedAtEpochMillis`.
- A later sparse Home refresh cannot erase the richer detail metadata persisted here because Task 01 already enforces sparse-card merge semantics.
- Recent searches remain in memory for MVP; no new persistence table is added in Task 06.

- [ ] **Step 1: Add the story module boundary and write the failing detail-mapper test**

Create `CatalogDetailsMapperTest.kt`:

```kotlin
@Test
fun detailsMapToRichSourceMetadataWithHostedVersion() {
    val hosted = hostedCatalog(id = "catalog.a", version = "4.5.6")
    val details = catalogDetails(
        sourceId = "story-1",
        contentType = ContentType.WEB_NOVEL,
        aliases = listOf("Alias"),
        languages = setOf("en"),
        popularityRank = 7,
        score = CatalogScore(8.8, 10.0),
    )

    val mapped = CatalogDetailsMapper().map(hosted, details)

    assertEquals(PluginId("catalog.a"), mapped.pluginId)
    assertEquals("4.5.6", mapped.pluginVersion)
    assertEquals(setOf("Alias"), mapped.metadata.aliases)
    assertEquals(7, mapped.metadata.popularityRank)
    assertEquals(10.0, mapped.metadata.scoreScale)
}
```

Add `:feature:story` to settings, architecture policy, and README in this same task.

- [ ] **Step 2: Run detail mapper RED plus architecture gate**

```bash
./gradlew :feature:story:testDebugUnitTest \
  --tests app.openstory.story.domain.CatalogDetailsMapperTest.detailsMapToRichSourceMetadataWithHostedVersion \
  --stacktrace
./scripts/check-module-dependencies.sh
```

Expected: mapper test **FAILS** because the story feature/detail mapper is absent; architecture gate passes only with the complete new-module declaration.

- [ ] **Step 3: Write the failing search cancellation test**

Create `SearchCatalogsTest.kt`:

```kotlin
@Test
fun lateOldQueryCannotReplaceNewQuery() = runTest {
    val fixture = searchFixture(oldDelayMs = 1_000, newDelayMs = 10)

    fixture.controller.submit("old")
    fixture.controller.submit("new")
    fixture.advanceUntilIdle()

    assertEquals("new", fixture.controller.state.value.query)
    assertEquals(
        listOf("new-result"),
        fixture.controller.state.value.results.map { it.title },
    )
}
```

- [ ] **Step 4: Add the minimal search/detail implementation**

`SearchCatalogs` uses debounce + `flatMapLatest`, pins each hosted plugin during one request, and derives canonical display IDs with Task 03 matching against cached canonical candidates. It does not write transient search pages to Room.

`StoryDetailViewModel` fetches exact source details through the pinned hosted catalog, maps them to `CatalogSourceMetadata`, then persists through the Task 01 repository:

```kotlin
val hosted = host.catalog(pluginId).value()
val details = hosted.instance.details(sourceId).value()
val mapped = detailsMapper.map(hosted, details)
repository.upsertSourceMetadata(
    pluginId = mapped.pluginId,
    pluginVersion = mapped.pluginVersion,
    metadata = mapped.metadata,
)
```

The resulting UI projection reads canonical/source-owned metadata from repositories; plugin DTOs do not escape the domain/application boundary.

- [ ] **Step 5: Re-run feature tests and architecture policy**

```bash
./gradlew :feature:home:testDebugUnitTest :feature:story:testDebugUnitTest --stacktrace
./gradlew :feature:home:connectedDebugAndroidTest --stacktrace
./scripts/check-module-dependencies.sh
```

Expected: **BUILD SUCCESSFUL** and zero architecture-policy violations.

- [ ] **Step 6: Commit the independently reviewable search/detail change**

```bash
git add \
  settings.gradle.kts \
  config/architecture/module-boundaries.json \
  README.md \
  feature/home/src/main/kotlin/app/openstory/home/domain/SearchCatalogs.kt \
  feature/home/src/main/kotlin/app/openstory/home/ui/SearchViewModel.kt \
  feature/home/src/main/kotlin/app/openstory/home/ui/SearchScreen.kt \
  feature/story/build.gradle.kts \
  feature/story/src/main/kotlin/app/openstory/story/domain/CatalogDetailsMapper.kt \
  feature/story/src/main/kotlin/app/openstory/story/ui/StoryDetailViewModel.kt \
  feature/story/src/main/kotlin/app/openstory/story/ui/StoryDetailScreen.kt \
  feature/home/src/test/kotlin/app/openstory/home/domain/SearchCatalogsTest.kt \
  feature/story/src/test/kotlin/app/openstory/story/domain/CatalogDetailsMapperTest.kt \
  feature/story/src/test/kotlin/app/openstory/story/ui/StoryDetailViewModelTest.kt

git diff --cached --check
git commit -m "catalog: add search filters and metadata detail"
```

## Wave Checkpoint

Do not begin `wave-06-library-and-story-matching.md` until every item below is demonstrated on a clean checkout:

- [ ] Room is still `version = 1`, the schema directory still contains exactly `1.json`, and fresh creation contains the three catalog Home tables plus the unique `(catalog_plugin_id, external_story_id)` identity index.
- [ ] A successful catalog refresh preserves section/item ordering, plugin version, refresh timestamp, and richer previously fetched source details.
- [ ] Failure of one catalog leaves its previous cached snapshot visible and does not blank or mutate successful catalogs.
- [ ] Removing/disabling one catalog removes/hides its Home memberships without deleting canonical stories, Library state, or source metadata owned by other catalogs.
- [ ] Two catalog records resolved to the same work show one canonical card while retaining separate source scores/scales and source labels.
- [ ] Search cancellation prevents stale results replacing a newer query, and detail enrichment does not modify Home membership.
- [ ] Home/story accessibility tests expose title, source section/catalog, score/scale, stale/error state, and primary actions without relying on color alone.
- [ ] `scripts/check-module-dependencies.sh` accepts the new `:core:matching`, `:feature:home`, and `:feature:story` module graph with no stale permissions.

## Full Verification

Run the current repository gates rather than the historical generic Gradle aliases:

```bash
./scripts/check-module-dependencies.sh
./scripts/verify.sh
./gradlew :core:model:test \
  :core:database:testDebugUnitTest \
  :core:plugin-api:test \
  :core:plugin-host:testDebugUnitTest \
  :core:matching:test \
  :feature:home:testDebugUnitTest \
  :feature:story:testDebugUnitTest \
  :app:assembleDebug \
  --stacktrace
```

When API 26 and API 37 emulators/devices are available, run the database checkpoint because Task 01 changes the fresh Room schema-one shape:

```bash
ANDROID_SERIAL_API_26=<api26-serial> \
ANDROID_SERIAL_API_37=<api37-serial> \
  ./scripts/checkpoints/database.sh
```

Expected: all available gates pass, `scripts/verify-room-schema-stability.sh` detects no build-time drift from the committed schema-one export, no ignored failing tests remain, and no unresolved lint/detekt/module-boundary errors remain.

## Review Packet

Attach to the checkpoint review:

- Commit range for this wave.
- Focused test output for every task.
- Full verification output.
- Any deliberate deviations from the approved design, with rationale and updated spec text.
- Screenshots or screen recordings only when the wave changes visible UI.

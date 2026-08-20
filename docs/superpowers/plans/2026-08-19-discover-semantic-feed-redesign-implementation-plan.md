# Discover Semantic Feed Redesign Implementation Plan

Status: **COMPLETED — implementation record**
Acceptance: `docs/internal/checkpoints/discover-semantic-feed-redesign.md`

> The checklist below is preserved as the execution plan that produced the accepted implementation.
> It is not the current execution entry point; use `docs/implementation/current-roadmap.md` for what comes next.


> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the source/category-driven Discover screen with a source-agnostic semantic experience: a 5-story Popular hero pager, a full-width Manga/Light Novel selector, a 9-item Latest Updates grid, and a 5-item Top Rated ranking list.

**Architecture:** Extend the generic catalog wire/source/domain/cache contracts with semantic feed kind, publication status, and coherent latest-update metadata. Persist those fields in Room 7, aggregate and deduplicate semantic feeds on the existing Default-dispatcher projection boundary, and expose only presentation-ready Discover state to Compose. Keep provider adapters out of scope; tests and benchmark fixtures supply rich normalized data.

**Tech Stack:** Kotlin, kotlinx.serialization, coroutines/Flow, Hilt ViewModel, Room 6->7 migration, Jetpack Compose Material 3/Foundation Pager, Compose UI tests, Robolectric/Roborazzi, Android instrumentation tests, Macrobenchmark.

**Spec:** `docs/superpowers/specs/2026-08-19-discover-semantic-feed-redesign-design.md`

## Global Constraints

- Discover remains generic across catalog plugins; production Discover code must not branch on MyAnimeList, MangaUpdates, MangaDex, or any provider/source ID.
- User-facing order is fixed: Search -> Popular -> Media selector -> Latest Updates -> Top Rated.
- Use the existing domain `ContentType`; Manga is enabled/selected by default and Light Novel is visible but disabled for this delivery.
- Popular is manual only, full-width, maximum 5, no auto-slide, no next-card peek, with non-interactive page dots only for 2+ pages.
- Latest Updates is maximum 9, three columns, one outer vertical scroll owner, ordered by source-reported latest-update time only.
- Top Rated is maximum 5, one full-width ranked row per story, with rank, cover, title, rating, genres, and optional publication status.
- Missing latest-update timestamp excludes a story from Latest; missing score excludes it from Top Rated; missing genre/status is omitted rather than rendered as `Unknown`.
- Semantic feed identity must be explicit; Discover must never infer feed meaning from section titles or provider/source IDs.
- New provider metadata is optional and backward-compatible on the wire/source path.
- Discover must never issue per-card `details()` calls; it projects cached Home data only.
- Room migration is 6 -> 7 and must preserve all existing data; no destructive migration or app-data wipe.
- Sparse Home updates must preserve richer cached metadata, and latest timestamp + release label must move together as one value.
- Aggregation/deduplication ends before Compose and is deterministic by canonical `StoryId`.
- The outer Discover `LazyColumn` remains the only vertical scroll owner.
- Existing Hikari design/theme tokens and shared components remain authoritative; no Discover-local colors, radii, shadows, typography values, or magic visual dimensions.
- `See all` remains visually/behaviorally absent until a destination is separately designed; do not ship a dead clickable action.
- Search, Story navigation, pull-to-refresh, cached refresh behavior, top-level retained composition, artwork/backdrop infrastructure, and app navigation architecture remain intact.

## File Structure Locked By This Plan

### Generic catalog contract

- Create `catalog/src/main/kotlin/app/openstory/catalog/model/CatalogFeedKind.kt` — semantic Home feed identity.
- Create `catalog/src/main/kotlin/app/openstory/catalog/model/CatalogLatestUpdate.kt` — coherent source update timestamp + release label.
- Create `catalog/src/main/kotlin/app/openstory/catalog/model/PublicationStatus.kt` — normalized publishing state.
- Modify `catalog/src/main/kotlin/app/openstory/catalog/model/CatalogEntry.kt` — attach optional status/latest-update metadata.
- Modify `catalog/src/main/kotlin/app/openstory/catalog/model/CatalogHome.kt` — attach `CatalogFeedKind` to Home sections.
- Modify `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/catalog/CatalogProtocol.kt` — backward-compatible wire enums/DTO fields.
- Modify `catalog/src/main/kotlin/app/openstory/catalog/source/CatalogSourceModels.kt` and `catalog/src/main/kotlin/app/openstory/catalog/source/PluginCatalogSource.kt` — wire-to-source mapping.

### Persistence

- Modify `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogEntities.kt` — Room columns for feed/status/latest update.
- Modify `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogEntityMapper.kt` — entity/domain round trip.
- Modify `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt` — sparse merge and coherent latest-update merge.
- Modify `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt` — `MIGRATION_6_7`.
- Modify `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt` — schema version 7 + migration registration.
- Generate `storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/7.json`.
- Create `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/CatalogMigrationTest.kt`.

### Discover projection/state

- Create `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverStoryItem.kt` — source-agnostic presentation item and media option.
- Create `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverSemanticProjection.kt` — pure feed extraction/dedupe/order/merge logic.
- Rewrite `DiscoverUiState.kt` — semantic state only.
- Simplify `DiscoverProjectionPipeline.kt` — Default-dispatcher wrapper around semantic projection.
- Rewrite `DiscoverViewModel.kt` selection/bootstrap wiring around `ContentType`.

### Shared UI primitives

- Create `core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariSegmentedControl.kt`.
- Create `core/designsystem/src/main/kotlin/app/openstory/designsystem/state/HikariSkeleton.kt`.

### Discover Compose

- Create `DiscoverPopularPager.kt`.
- Modify `DiscoverHero.kt` to consume `DiscoverStoryItem` and remove provider branding.
- Create `DiscoverMediaTypeSelector.kt`.
- Create `DiscoverLatestGrid.kt` and `DiscoverLatestCard.kt`.
- Create `DiscoverTopRatedList.kt` and `DiscoverTopRatedRow.kt`.
- Create `DiscoverLoadingContent.kt`.
- Rewrite `DiscoverScreen.kt` around the new hierarchy.
- Remove `DiscoverCategoryCard.kt`, `DiscoverCategoryStrip.kt`, and `DiscoverSourceFilters.kt` after all callers/tests are migrated.

### App/test/benchmark integration

- Modify `app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt` and `AppNavHost.kt` for the new media focus/callback contract.
- Update Discover unit/Compose/screenshot tests and app-shell screenshot fixtures.
- Modify `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkFixtureActivity.kt` so `discoverScroll` has semantic Popular/Latest/Top Rated fixture data without depending on a real plugin adapter.

---

### Task 1: Extend the generic wire, source, and domain contracts

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/model/CatalogFeedKind.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/model/CatalogLatestUpdate.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/model/PublicationStatus.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/model/CatalogEntry.kt:5-24`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/model/CatalogHome.kt:10-19`
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/catalog/CatalogProtocol.kt:7-142`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/source/CatalogSourceModels.kt:1-50`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/source/PluginCatalogSource.kt:65-113`
- Test: `plugins/api/src/test/kotlin/app/openstory/plugins/api/protocol/catalog/CatalogProtocolTest.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/source/PluginCatalogSourceTest.kt`

**Interfaces:**
- Produces: `CatalogFeedKind`, `CatalogLatestUpdate`, `PublicationStatus`.
- Produces: wire equivalents `WireCatalogFeedKind`, `CatalogLatestUpdateDto`, `WirePublicationStatus`.
- Produces: `SourceFeedKind`, `SourceLatestUpdate`, `SourcePublicationStatus` for the source boundary.
- All new wire fields have defaults so old plugin payloads still decode.

- [ ] **Step 1: Add protocol tests proving old payloads decode with semantic defaults and rich payloads validate.**

Add these protocol test cases:

```kotlin
@Test
fun oldHomeSectionDefaultsToOtherAndMissingMetadata() {
    val section = json.decodeFromString<CatalogSectionDto>(
        """{"sourceId":"top","title":"Top","items":[{"sourceId":"1","title":"One","contentType":"MANGA"}]}""",
    )

    assertEquals(WireCatalogFeedKind.OTHER, section.kind)
    assertTrue(section.items.single().genres.isEmpty())
    assertNull(section.items.single().popularityRank)
    assertNull(section.items.single().publicationStatus)
    assertNull(section.items.single().latestUpdate)
}

@Test
fun richHomeMetadataRoundTrips() {
    val item = CatalogItemDto(
        sourceId = "manga-1",
        title = "Manga One",
        contentType = WireContentType.MANGA,
        genres = setOf("Action", "Fantasy"),
        popularityRank = 3,
        publicationStatus = WirePublicationStatus.ONGOING,
        latestUpdate = CatalogLatestUpdateDto(1234L, "128"),
    )
    val section = CatalogSectionDto(
        sourceId = "popular",
        title = "Popular",
        items = listOf(item),
        kind = WireCatalogFeedKind.POPULAR,
    )

    assertEquals(section, json.decodeFromString(json.encodeToString(section)))
}
```

Also add constructor validation tests for `CatalogLatestUpdateDto(-1L, "128")` and a blank non-null release label.

- [ ] **Step 2: Run the focused protocol test and confirm it fails because the new types/fields do not exist.**

Run:

```bash
./gradlew :plugins:api:test --tests '*CatalogProtocolTest*'
```

Expected: compile/test failure referencing `WireCatalogFeedKind`, `WirePublicationStatus`, or `CatalogLatestUpdateDto`.

- [ ] **Step 3: Add the domain model types and fields.**

Use these exact domain semantics:

```kotlin
enum class CatalogFeedKind {
    POPULAR,
    LATEST_UPDATES,
    TOP_RATED,
    OTHER,
}
```

```kotlin
data class CatalogLatestUpdate(
    val atEpochMillis: Long,
    val releaseLabel: String?,
) {
    init {
        require(atEpochMillis >= 0L) { "Latest update time must not be negative" }
        require(releaseLabel == null || releaseLabel.isNotBlank()) {
            "Latest update release label must be null or non-blank"
        }
    }
}
```

```kotlin
enum class PublicationStatus {
    ONGOING,
    COMPLETED,
    HIATUS,
    CANCELLED,
    UPCOMING,
}
```

Extend `CatalogEntry` with:

```kotlin
val publicationStatus: PublicationStatus? = null,
val latestUpdate: CatalogLatestUpdate? = null,
```

Extend `CatalogHomeSection` with a trailing default:

```kotlin
val kind: CatalogFeedKind = CatalogFeedKind.OTHER,
```

The trailing default keeps current Kotlin call sites source-compatible until later tasks update them.

- [ ] **Step 4: Add backward-compatible wire declarations and optional Home/details fields.**

In `CatalogProtocol.kt`, add:

```kotlin
@Serializable
enum class WireCatalogFeedKind { POPULAR, LATEST_UPDATES, TOP_RATED, OTHER }

@Serializable
enum class WirePublicationStatus { ONGOING, COMPLETED, HIATUS, CANCELLED, UPCOMING }

@Serializable
data class CatalogLatestUpdateDto(
    val atEpochMillis: Long,
    val releaseLabel: String? = null,
) {
    init {
        require(atEpochMillis >= 0L) { "Latest update time must not be negative" }
        require(releaseLabel == null || releaseLabel.isNotBlank()) {
            "Latest update release label must be null or non-blank"
        }
        releaseLabel?.let { requireBoundedText(it, "latest update release label") }
    }
}
```

Extend `CatalogItemDto` with trailing defaults:

```kotlin
val genres: Set<String> = emptySet(),
val popularityRank: Long? = null,
val publicationStatus: WirePublicationStatus? = null,
val latestUpdate: CatalogLatestUpdateDto? = null,
```

Validate `genres` with `requireBoundedCollection` and `popularityRank` with the existing positive-rank rule.

Extend `CatalogSectionDto` with:

```kotlin
val kind: WireCatalogFeedKind = WireCatalogFeedKind.OTHER,
```

Extend `CatalogDetailsOutputDto` with trailing nullable fields:

```kotlin
val publicationStatus: WirePublicationStatus? = null,
val latestUpdate: CatalogLatestUpdateDto? = null,
```

- [ ] **Step 5: Mirror the new semantics at the catalog-source boundary and map wire values in `PluginCatalogSource`.**

Add to `CatalogSourceModels.kt`:

```kotlin
enum class SourceFeedKind { POPULAR, LATEST_UPDATES, TOP_RATED, OTHER }
enum class SourcePublicationStatus { ONGOING, COMPLETED, HIATUS, CANCELLED, UPCOMING }

data class SourceLatestUpdate(
    val atEpochMillis: Long,
    val releaseLabel: String?,
)
```

Extend source models as follows:

```kotlin
data class SourceSection(
    val sourceId: String,
    val title: String,
    val items: List<SourceItem>,
    val kind: SourceFeedKind = SourceFeedKind.OTHER,
)
```

```kotlin
data class SourceItem(
    val sourceId: String,
    val title: String,
    val contentType: SourceContentType,
    val authors: Set<String>,
    val coverUrl: String?,
    val scoreValue: Double?,
    val scoreScale: Double?,
    val genres: Set<String> = emptySet(),
    val popularityRank: Long? = null,
    val publicationStatus: SourcePublicationStatus? = null,
    val latestUpdate: SourceLatestUpdate? = null,
)
```

Append `publicationStatus` and `latestUpdate` to `SourceDetails` with null defaults. Map every wire enum exhaustively and map `CatalogLatestUpdateDto` as one object; do not split its fields into separate transforms.

- [ ] **Step 6: Update `PluginCatalogSourceTest` to assert `kind`, genres, popularity, status, and latest-update mapping, then run both focused suites.**

Run:

```bash
./gradlew :plugins:api:test :catalog:testDebugUnitTest \
  --tests '*CatalogProtocolTest*' \
  --tests '*PluginCatalogSourceTest*'
```

Expected: PASS.

- [ ] **Step 7: Commit the generic contract.**

```bash
git add plugins/api catalog/src/main catalog/src/test plugins/api/src/test
git commit -m "feat(catalog): add semantic home feed metadata"
```

---

### Task 2: Carry semantic Home metadata through refresh and details ingest

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/home/CatalogRefreshService.kt:95-208`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/details/CatalogDetailsService.kt:170-195`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/home/CatalogRefreshServiceTest.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/details/CatalogDetailsServiceTest.kt`

**Interfaces:**
- Consumes: `SourceFeedKind`, `SourcePublicationStatus`, `SourceLatestUpdate` from Task 1.
- Produces: `CatalogHomeMutation.sections[*].kind` and rich `CatalogEntry` values for Home/details commits.

- [ ] **Step 1: Add a failing Home-ingest test that records the exact rich mutation.**

Add a test using the existing `RecordingRepository` pattern:

```kotlin
@Test
fun semanticHomeMetadataIsCommittedWithoutDetailsFetch() = runTest {
    val repository = RecordingRepository()
    val sourceItem = SourceItem(
        sourceId = "manga-1",
        title = "Manga One",
        contentType = SourceContentType.MANGA,
        authors = emptySet(),
        coverUrl = "https://example.test/one.jpg",
        scoreValue = 9.2,
        scoreScale = 10.0,
        genres = setOf("Action", "Fantasy"),
        popularityRank = 2,
        publicationStatus = SourcePublicationStatus.ONGOING,
        latestUpdate = SourceLatestUpdate(500L, "128"),
    )
    val registry = Registry(
        listOf(
            Source(
                "a",
                CatalogSourceResult.Success(
                    listOf(SourceSection("popular", "Popular", listOf(sourceItem), SourceFeedKind.POPULAR)),
                ),
            ),
        ),
    )

    service(registry, repository, 999L).refresh()

    val mutation = repository.mutations.single()
    assertEquals(CatalogFeedKind.POPULAR, mutation.sections.single().kind)
    assertEquals(setOf("Action", "Fantasy"), mutation.entries.single().genres)
    assertEquals(2, mutation.entries.single().popularityRank)
    assertEquals(PublicationStatus.ONGOING, mutation.entries.single().publicationStatus)
    assertEquals(CatalogLatestUpdate(500L, "128"), mutation.entries.single().latestUpdate)
}
```

- [ ] **Step 2: Run the refresh-service test and verify it fails on missing mapping.**

```bash
./gradlew :catalog:testDebugUnitTest --tests '*CatalogRefreshServiceTest*'
```

Expected: FAIL in semantic metadata assertions.

- [ ] **Step 3: Map source section/item values in `CatalogRefreshService`.**

Change `toCatalogSections` to pass semantic kind:

```kotlin
CatalogHomeSection(
    sourceId = section.sourceId,
    title = section.title,
    items = section.items.map { resolved.getValue(it.sourceId) },
    kind = section.kind.toModel(),
)
```

Extend `SourceItem.toEntry`:

```kotlin
genres = genres,
popularityRank = popularityRank,
publicationStatus = publicationStatus?.toModel(),
latestUpdate = latestUpdate?.let { CatalogLatestUpdate(it.atEpochMillis, it.releaseLabel) },
```

Add exhaustive `SourceFeedKind.toModel()` and `SourcePublicationStatus.toModel()` mappings beside the existing content-type mapping.

- [ ] **Step 4: Add details enrichment coverage and map status/latest update in `CatalogDetailsService`.**

The details test must assert that a `SourceDetails` carrying `SourcePublicationStatus.COMPLETED` and `SourceLatestUpdate(700L, "200")` becomes a `CatalogEntry` with matching domain values. Extend `SourceDetails.toEntry` with the same coherent-object mapping used by Home ingest.

- [ ] **Step 5: Run focused catalog tests.**

```bash
./gradlew :catalog:testDebugUnitTest \
  --tests '*CatalogRefreshServiceTest*' \
  --tests '*CatalogDetailsServiceTest*'
```

Expected: PASS, including existing match-resolution/order tests.

- [ ] **Step 6: Commit the ingest path.**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/home \
        catalog/src/main/kotlin/app/openstory/catalog/details \
        catalog/src/test/kotlin/app/openstory/catalog/home \
        catalog/src/test/kotlin/app/openstory/catalog/details
git commit -m "feat(catalog): ingest semantic discover metadata"
```

---

### Task 3: Persist semantic metadata with Room schema 7 and preserve rich cache values

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogEntities.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogEntityMapper.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt:108-134`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt:35-83`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/CatalogMigrationTest.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt`
- Generate: `storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/7.json`

**Interfaces:**
- `CatalogHomeSectionEntity.feedKind: String`
- `CatalogEntryEntity.publicationStatus: String?`
- `CatalogEntryEntity.latestUpdateAtEpochMillis: Long?`
- `CatalogEntryEntity.latestUpdateReleaseLabel: String?`
- `RoomMigrations.MIGRATION_6_7`

- [ ] **Step 1: Add a migration test that seeds schema 6 and verifies defaults after 6 -> 7.**

Create `CatalogMigrationTest.kt` using `MigrationTestHelper`. Seed a story, catalog entry, Home snapshot, section, and Home item under schema 6; then validate:

```kotlin
assertEquals("OTHER", sectionFeedKind)
assertNull(publicationStatus)
assertNull(latestUpdateAt)
assertNull(latestUpdateLabel)
assertEquals("Existing title", preservedTitle)
```

Run exactly `helper.runMigrationsAndValidate(TEST_DATABASE, 7, true, RoomMigrations.MIGRATION_6_7)` in the migration test.

- [ ] **Step 2: Add repository tests for rich round-trip and sparse/latest merge rules.**

Cover these exact cases:

```text
existing status=ONGOING + incoming status=null       -> ONGOING
existing update=(500,"128") + incoming=null         -> (500,"128")
existing update=(500,"128") + incoming=(400,"120") -> (500,"128")
existing update=(500,"128") + incoming=(600,"130") -> (600,"130")
```

Also assert `CatalogFeedKind.TOP_RATED` survives commit -> observeHomes round-trip.

- [ ] **Step 3: Run the new instrumentation tests and confirm schema/type failures.**

With an emulator/device connected:

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.CatalogMigrationTest,app.openstory.storage.room.catalog.RoomCatalogRepositoryTest
```

Expected before implementation: migration/schema assertion failure or compile failure for new entity fields.

- [ ] **Step 4: Add Room columns and mapper round-trip.**

Extend `CatalogEntryEntity`:

```kotlin
@ColumnInfo(name = "publication_status") val publicationStatus: String?,
@ColumnInfo(name = "latest_update_at_epoch_millis") val latestUpdateAtEpochMillis: Long?,
@ColumnInfo(name = "latest_update_release_label") val latestUpdateReleaseLabel: String?,
```

Extend `CatalogHomeSectionEntity`:

```kotlin
@ColumnInfo(name = "feed_kind") val feedKind: String,
```

Map domain -> entity using enum `.name`. Map entity -> domain with `PublicationStatus.valueOf`, `CatalogFeedKind.valueOf`, and reconstruct `CatalogLatestUpdate` only when the timestamp exists:

```kotlin
latestUpdate = latestUpdateAtEpochMillis?.let { at ->
    CatalogLatestUpdate(at, latestUpdateReleaseLabel)
}
```

- [ ] **Step 5: Implement coherent sparse merge in `RoomCatalogRepository`.**

Add a helper that returns the incoming latest update only when it is newer:

```kotlin
private fun mergeLatestUpdate(
    existingAt: Long?,
    existingLabel: String?,
    incomingAt: Long?,
    incomingLabel: String?,
): Pair<Long?, String?> = when {
    incomingAt == null -> existingAt to existingLabel
    existingAt == null -> incomingAt to incomingLabel
    incomingAt > existingAt -> incomingAt to incomingLabel
    else -> existingAt to existingLabel
}
```

Use the returned pair in both Home and details `merge` functions. For publication status use `incoming.publicationStatus ?: existing.publicationStatus`. Keep current `ifEmpty`/nullable preservation for genres, score, popularity, cover, aliases, authors, description, source URL, and language tags.

When inserting Home sections, write `section.kind.name` into `feed_kind`.

- [ ] **Step 6: Add migration 6 -> 7 and register schema version 7.**

Add:

```kotlin
val MIGRATION_6_7: Migration = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE `catalog_home_sections` ADD COLUMN `feed_kind` TEXT NOT NULL DEFAULT 'OTHER'",
        )
        db.execSQL("ALTER TABLE `catalog_entries` ADD COLUMN `publication_status` TEXT")
        db.execSQL("ALTER TABLE `catalog_entries` ADD COLUMN `latest_update_at_epoch_millis` INTEGER")
        db.execSQL("ALTER TABLE `catalog_entries` ADD COLUMN `latest_update_release_label` TEXT")
    }
}
```

Change `OpenStoryDatabase` to `version = 7` and append `RoomMigrations.MIGRATION_6_7` after `RoomMigrations.MIGRATION_5_6` in the existing `.addMigrations` call.

- [ ] **Step 7: Generate schema 7 and rerun migration/repository tests.**

```bash
./gradlew :storage:room:assembleDebug
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.CatalogMigrationTest,app.openstory.storage.room.catalog.RoomCatalogRepositoryTest,app.openstory.storage.room.DatabaseBaselineTest
```

Expected: `7.json` exists, migration validates, round-trip/merge tests pass, `PRAGMA foreign_key_check` remains empty.

- [ ] **Step 8: Commit persistence.**

```bash
git add storage/room
git commit -m "feat(storage): persist semantic catalog feeds"
```

---

### Task 4: Replace source-centric projection with deterministic semantic projection

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverStoryItem.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverSemanticProjection.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverUiState.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverProjectionPipeline.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverProjectionTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverProjectionPipelineTest.kt`

**Interfaces:**
- Produces `DiscoverMediaTypeOption(contentType: ContentType, enabled: Boolean)`.
- Produces `DiscoverStoryItem(storyId, title, coverUrl, contentType, score, genres, publicationStatus, latestUpdate)`.
- Produces semantic `DiscoverUiState` with `popular`, `latestUpdates`, `topRated`, `selectedContentType`, `mediaTypeOptions`, `loading`, refresh/failure fields.
- `DiscoverProjectionPipeline.project(content, selectedContentType, loading, refreshing, refreshReport)` runs on `AppDispatchers.default`.

- [ ] **Step 1: Replace old projection tests with failing semantic contract tests.**

Create fixtures with explicit `CatalogFeedKind`. Cover at minimum:

```kotlin
assertEquals(5, state.popular.size)
assertEquals(9, state.latestUpdates.size)
assertEquals(5, state.topRated.size)
assertEquals(ContentType.MANGA, state.selectedContentType)
assertEquals(listOf(ContentType.MANGA, ContentType.LIGHT_NOVEL), state.mediaTypeOptions.map { it.contentType })
assertTrue(state.mediaTypeOptions.single { it.contentType == ContentType.MANGA }.enabled)
assertFalse(state.mediaTypeOptions.single { it.contentType == ContentType.LIGHT_NOVEL }.enabled)
```

Add independent tests proving:

```text
OTHER feed does not leak into any semantic section
Latest excludes an item with latestUpdate=null
Latest sorts 900, 800, 700 regardless of cache refreshedAt
Top Rated excludes score=null and compares value/scale
Popular order uses popularityRank when present and feed position otherwise
same StoryId from two plugins appears once
conflicting metadata resolves deterministically regardless of input snapshot order
partial feeds do not fabricate missing sections
```

- [ ] **Step 2: Run projection tests and confirm old source-centric state fails the new expectations.**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*DiscoverProjectionTest*' \
  --tests '*DiscoverProjectionPipelineTest*'
```

Expected: compile/test failure because semantic fields/projector do not exist.

- [ ] **Step 3: Introduce the presentation models and semantic UI state.**

Use this state shape:

```kotlin
data class DiscoverMediaTypeOption(
    val contentType: ContentType,
    val enabled: Boolean,
)

data class DiscoverStoryItem(
    val storyId: StoryId,
    val title: String,
    val coverUrl: String?,
    val contentType: ContentType,
    val score: Score?,
    val genres: List<String>,
    val publicationStatus: PublicationStatus?,
    val latestUpdate: CatalogLatestUpdate?,
)
```

```kotlin
data class DiscoverUiState(
    val selectedContentType: ContentType = ContentType.MANGA,
    val mediaTypeOptions: List<DiscoverMediaTypeOption> = defaultDiscoverMediaTypeOptions,
    val popular: List<DiscoverStoryItem> = emptyList(),
    val latestUpdates: List<DiscoverStoryItem> = emptyList(),
    val topRated: List<DiscoverStoryItem> = emptyList(),
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val refreshReport: DiscoverRefreshReport? = null,
    val observationFailure: DiscoverUiFailure? = null,
    val refreshFailure: DiscoverUiFailure? = null,
)
```

Define the default option list in the same file so the state constructor has no hidden dependency:

```kotlin
internal val defaultDiscoverMediaTypeOptions = listOf(
    DiscoverMediaTypeOption(ContentType.MANGA, enabled = true),
    DiscoverMediaTypeOption(ContentType.LIGHT_NOVEL, enabled = false),
)
```

Keep `DiscoverRefreshReport`, `DiscoverUiFailure`, and `globalFailure`. Remove `DiscoverQuickCategory`, `DiscoverShelf`, `selectedCatalogId`, `selectedSourceId`, `catalogs`, `rankedStories`, and `featured` from the public screen state.

- [ ] **Step 4: Implement a pure deterministic semantic projector.**

Index all selected-type entries by canonical `StoryId`, deduplicating repeated `(pluginId, sourceId)` entries before grouping. Feed membership comes only from sections whose `kind` equals the requested semantic kind.

Use deterministic presentation precedence:

```kotlin
private val presentationOrder =
    compareByDescending<CatalogEntry> { !it.coverUrl.isNullOrBlank() }
        .thenByDescending { it.genres.isNotEmpty() }
        .thenByDescending { it.publicationStatus != null }
        .thenByDescending { it.score != null }
        .thenByDescending { it.latestUpdate != null }
        .thenBy { it.pluginId.value }
        .thenBy { it.sourceId }
```

Build one `DiscoverStoryItem` per StoryId using:

```text
title/cover/contentType -> first entry by presentationOrder
genres                  -> first non-empty genres by presentationOrder, sorted, take 3
publicationStatus       -> first non-null status by presentationOrder
latestUpdate            -> max atEpochMillis; preserve that same object's releaseLabel
score                    -> highest normalized valid score, tie pluginId/sourceId
```

Popular ordering key per feed contribution is `entry.popularityRank ?: (itemIndex + 1).toLong()`. For each StoryId use the minimum key, then tie by `storyId.value`, take 5.

Latest uses only `LATEST_UPDATES` feed members with a non-null latest update; choose the newest update per StoryId, sort timestamp descending then `storyId.value`, take 9.

Top Rated uses only `TOP_RATED` feed members with score. Reuse `AggregateRanking` on those contributions so score scales are normalized and multi-plugin ratings aggregate deterministically; sort its output order and take 5.

- [ ] **Step 5: Simplify `DiscoverProjectionPipeline`.**

`DiscoverPreparedContent` now only needs the cached homes required by the semantic projector:

```kotlin
internal data class DiscoverPreparedContent(
    val homes: List<CatalogHomeSnapshot>,
)
```

Remove the `CatalogHomeQuery` dependency from the pipeline constructor. Keep both `prepare` and `project` wrapped in `withContext(dispatchers.default)` so feed flattening, dedupe, sort, and presentation merge stay off the main dispatcher.

- [ ] **Step 6: Run semantic projection tests.**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*DiscoverProjectionTest*' \
  --tests '*DiscoverProjectionPipelineTest*'
```

Expected: PASS and deterministic-order tests pass when fixture input order is reversed.

- [ ] **Step 7: Commit semantic projection.**

```bash
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover \
        feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover
git commit -m "feat(discover): project semantic catalog feeds"
```

---

### Task 5: Rewire `DiscoverViewModel` around media type and correct bootstrap loading

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt:24-171`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt`

**Interfaces:**
- `fun selectContentType(contentType: ContentType)` replaces `selectCatalog`, `selectCategory`, and `selectCombined`.
- Manga remains the only enabled selection for this delivery.
- `state.loading` is true only while initial empty-cache bootstrap has not completed; later refreshes with cache never replace content with skeletons.

- [ ] **Step 1: Replace source-selection tests with media-selection/bootstrap tests.**

Keep the existing tests for one repository observation, empty-cache bootstrap once, cached content before refresh, and observation/refresh failure preservation. Replace catalog/category tests with:

```kotlin
@Test
fun mangaIsDefaultAndLightNovelSelectionIsIgnoredWhileDisabled() = runTest {
    val viewModel = viewModel(repositoryWithSemanticManga(), source())
    viewModel.state.test {
        val initial = awaitItem()
        assertEquals(ContentType.MANGA, initial.selectedContentType)
        viewModel.selectContentType(ContentType.LIGHT_NOVEL)
        assertEquals(ContentType.MANGA, viewModel.state.value.selectedContentType)
    }
}
```

Add a bootstrap test asserting:

```text
empty cache + refresh in flight -> loading=true
refresh finishes with no semantic data -> loading=false and semantic lists empty
cached semantic content + manual refresh -> loading=false while refreshing=true
```

- [ ] **Step 2: Run the ViewModel test and verify old catalog/source APIs fail the new contract.**

```bash
./gradlew :feature:catalog:testDebugUnitTest --tests '*DiscoverViewModelTest*'
```

- [ ] **Step 3: Replace catalog/source selection flows with `selectedContentType`.**

Use:

```kotlin
private val selectedContentType = MutableStateFlow(ContentType.MANGA)
private val initialLoading = MutableStateFlow(true)
```

Combine `dependencies.content`, `selectedContentType`, `initialLoading`, `refreshing`, and `refreshReport`, then call `projection.project(content, selectedType, loading, busy, report)`.

Implement current delivery selection policy without provider checks:

```kotlin
fun selectContentType(contentType: ContentType) {
    if (contentType != ContentType.MANGA) return
    selectedContentType.value = contentType
}
```

The UI state still exposes Light Novel as disabled, so this guard is defense-in-depth rather than the source of presentation state.

- [ ] **Step 4: Make bootstrap await the initial refresh attempt before clearing skeleton state.**

Extract the current refresh body into a private suspend function:

```kotlin
private suspend fun performRefresh() {
    if (refreshing.value) return
    refreshing.value = true
    try {
        refreshReport.value = dependencies.refresh()
        refreshFailure.value = null
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (_: Exception) {
        refreshFailure.value = DiscoverUiFailure(REFRESH_EXCEPTION_CODE, retryable = true)
    } finally {
        refreshing.value = false
    }
}
```

Then:

```kotlin
private fun bootstrapEmptyCache() {
    viewModelScope.launch {
        if (bootstrapAttempted) return@launch
        bootstrapAttempted = true
        val cached = dependencies.homes.first()
        if (cached.isEmpty() && observationFailure.value == null) {
            performRefresh()
        }
        initialLoading.value = false
    }
}

fun refresh() {
    viewModelScope.launch { performRefresh() }
}
```

This eliminates the initial `Search + empty body` flash while still allowing a true empty state after the first attempt finishes.

- [ ] **Step 5: Run the complete ViewModel/projection suite.**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*DiscoverViewModelTest*' \
  --tests '*DiscoverProjection*'
```

Expected: PASS.

- [ ] **Step 6: Commit state/lifecycle changes.**

```bash
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt \
        feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt
git commit -m "refactor(discover): make media type the primary state"
```

---

### Task 6: Add shared full-width segmented control and static skeleton primitive

**Files:**
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariSegmentedControl.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/state/HikariSkeleton.kt`
- Modify/Test: `core/designsystem/src/test/kotlin/app/openstory/designsystem/HikariProductPrimitivesTest.kt`
- Test: `core/designsystem/src/androidTest/kotlin/app/openstory/designsystem/HikariStateComponentsTest.kt`

**Interfaces:**
- `data class HikariSegmentedOption<T>(val key: T, val label: String, val enabled: Boolean = true)`
- `@Composable fun <T> HikariSegmentedControl(options, selectedKey, onSelected, modifier)`
- `@Composable fun HikariSkeleton(modifier, shape)`

- [ ] **Step 1: Add tests for equal-width segments, selected/disabled semantics, minimum touch target, and non-actionable skeleton semantics.**

The segmented-control test should render two options and assert both occupy the same measured width, Manga is selected, and Light Novel is disabled. The state-component test should assert `HikariSkeleton` does not expose clickable text/actions.

- [ ] **Step 2: Run design-system tests and verify the primitives are absent.**

```bash
./gradlew :core:designsystem:testDebugUnitTest :core:designsystem:connectedDebugAndroidTest
```

- [ ] **Step 3: Implement `HikariSegmentedControl` with Material 3 segmented buttons and Hikari sizing.**

Use `SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth())`; each `SegmentedButton` receives `Modifier.weight(1f).heightIn(min = MaterialTheme.hikariDimensions.minimumTouchTarget)`. Use `SegmentedButtonDefaults.itemShape(index, options.size)` and Material theme colors rather than local color constants. Require non-empty options and exactly one option matching `selectedKey`.

The control owns only visual/selection mechanics; it does not know `ContentType`.

- [ ] **Step 4: Implement a static `HikariSkeleton` using shared surface/color/shape tokens.**

Use a clipped/background `Box` with `MaterialTheme.colorScheme.surfaceContainerHigh` and a caller-supplied Hikari shape. Do not add shimmer/continuous animation in this delivery.

- [ ] **Step 5: Run design-system tests and UI policy gates.**

```bash
./gradlew :core:designsystem:testDebugUnitTest
./scripts/tests/ui-target-pack-test.sh
```

If the repo checkout is being verified from Windows PowerShell, also run:

```powershell
./scripts/tests/ui-target-pack-test.ps1
```

Expected: token/shared-component policy remains green.

- [ ] **Step 6: Commit shared primitives.**

```bash
git add core/designsystem scripts/tests
git commit -m "feat(designsystem): add segmented control and skeleton"
```

---

### Task 7: Build the new Discover presentation components and replace the screen hierarchy

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverPopularPager.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverHero.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverMediaTypeSelector.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverLatestGrid.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverLatestCard.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverTopRatedList.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverTopRatedRow.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverLoadingContent.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt:31-141`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverFeedback.kt`
- Remove after migration: `DiscoverCategoryCard.kt`, `DiscoverCategoryStrip.kt`, `DiscoverSourceFilters.kt`
- Test: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/discover/DiscoverScreenTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverSemanticsTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverTopLevelChromeTest.kt`

**Interfaces:**
- `DiscoverScreen(state, onRefresh, onSearch, onStorySelected, onContentTypeSelected, searchFocusRequester, searchNextFocusRequester, mediaTypeFocusRequester, onUtilityRequested, utilityFocusRequester, utilityNextFocusRequester, modifier, contentPadding)`.
- `DiscoverHero(item: DiscoverStoryItem, onSelected: (StoryId) -> Unit, modifier: Modifier = Modifier)`.
- `DiscoverPopularPager(stories: List<DiscoverStoryItem>, selectedContentType: ContentType, onSelected: (StoryId) -> Unit, modifier: Modifier = Modifier)`.
- Popular pager consumes at most 5 `DiscoverStoryItem` values and owns local pager state only.
- Latest grid consumes at most 9 items and creates three equal-width slots per row without nested vertical scrolling.
- Top Rated consumes at most 5 items and exposes one coherent semantics node per ranked row.

- [ ] **Step 1: Replace obsolete Category/Catalog UI tests with the new hierarchy/limits/semantics tests.**

Add coverage for these tags/semantics:

```text
discover-popular-pager
discover-popular-page-indicator
discover-media-selector
discover-latest-grid
discover-latest-item-<storyId>
discover-top-rated
discover-top-rated-rank-1 through discover-top-rated-rank-5
discover-loading
```

Required assertions:

```text
5 Popular items -> 5 pager pages + page dots
1 Popular item  -> no page indicator
pager swipe changes current-page semantics manually
Manga and Light Novel share the selector width equally
Light Novel has disabled semantics and does not invoke callback
Latest >9 input renders only first 9 projected items in 3 slots per row
Latest partial final row keeps normal item width
Top Rated >5 input renders only 5 full-width rows
Top row semantics includes rank/title/rating as one merged item
screen with no sections and loading=false renders shared empty state
loading=true renders layout-shaped skeleton content
refreshing=true with cached content keeps real content visible
pull-to-refresh and safe top inset remain present
```

- [ ] **Step 2: Run focused Compose/Robolectric tests and verify old screen contract fails.**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*DiscoverSemanticsTest*' \
  --tests '*DiscoverTopLevelChromeTest*'
```

With device/emulator:

```bash
./gradlew :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.ui.discover.DiscoverScreenTest
```

- [ ] **Step 3: Convert `DiscoverHero` to the source-agnostic presentation item.**

Change the input from `CatalogEntry` to `DiscoverStoryItem`. Keep current responsive hero height/poster tokens, artwork/backdrop behavior, compact whole-hero click, expanded `Open story` CTA, and title/score/genre presentation.

Remove:

```text
catalogDisplayName()
provider metadata badge
language/provider badge row
```

Remove the existing provider-based semantics from `DiscoverHero`. `DiscoverPopularPager` wraps each page in `clearAndSetSemantics` and supplies page-aware text such as `Popular story 2 of 5: <title>`, so TalkBack gets one coherent node per slide and no duplicate Hero semantics.

- [ ] **Step 4: Implement `DiscoverPopularPager` with full-page `HorizontalPager`.**

Use:

```kotlin
val pagerState = rememberPagerState(pageCount = { stories.size })
HorizontalPager(
    state = pagerState,
    modifier = Modifier.fillMaxWidth().testTag("discover-popular-pager"),
) { page ->
    DiscoverHero(stories[page], onSelected)
}
```

Do not pass horizontal content padding and do not use a fixed `PageSize`; the default fill page is the no-peek contract.

Track the visible `StoryId` locally. When `stories` changes for the same `selectedContentType`, find that StoryId in the new list and `scrollToPage` its new index; when media type changes, `scrollToPage(0)`. Clamp to a valid index if the story disappeared.

Overlay page dots inside the hero `Box` at `Alignment.BottomEnd` using Hikari spacing/shape/color tokens. Dots are plain visual `Box` nodes, not clickable controls.

- [ ] **Step 5: Implement the full-width media selector wrapper.**

`DiscoverMediaTypeSelector` maps the presentation options supplied by state to `HikariSegmentedControl`:

```kotlin
val options = mediaTypeOptions.map { option ->
    HikariSegmentedOption(
        key = option.contentType,
        label = when (option.contentType) {
            ContentType.MANGA -> "Manga"
            ContentType.LIGHT_NOVEL -> "Light Novel"
            ContentType.WEB_NOVEL -> "Web Novel"
            ContentType.ANIME -> "Anime"
        },
        enabled = option.enabled,
    )
}
```

For this delivery the state list contains only Manga and Light Novel. Use the enablement values from `state.mediaTypeOptions`, never plugin capability checks. Apply the optional `FocusRequester` to the Manga segment/control entry point and keep the disabled Light Novel option perceivable but non-actionable.

- [ ] **Step 6: Implement Latest Updates as three non-scrolling rows of three.**

`DiscoverLatestGrid` takes `items.take(9).chunked(3)`. Each row uses `Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(MaterialTheme.hikariSpacing.itemGap))`. Each actual card uses `Modifier.weight(1f)`; for missing slots in the final row insert `Spacer(Modifier.weight(1f))` so a single final item does not stretch.

`DiscoverLatestCard` renders only:

```text
cover
2-line title
1-line latest release label when non-null
```

Use existing Hikari artwork fallback and content-card shape. The whole card is one click target with the shared minimum touch-target policy. Do not render score, genres, status, provider, source, or chips.

- [ ] **Step 7: Implement Top Rated as a five-row vertical ranking block.**

`DiscoverTopRatedList` renders `items.take(5)` in a `Column`; each `DiscoverTopRatedRow` is full width and receives `rank = index + 1`.

The row layout is:

```text
rank column | cover | title + score + genres + optional status
```

Format rank with `rank.toString().padStart(2, '0')`. Genres use `item.genres.take(3).joinToString(" · ")`, one line, ellipsis. Status maps the enum to title case. Do not render author/provider/source/language.

Apply `semantics(mergeDescendants = true)` with a coherent content description containing rank, title, rating, genre text when present, and status when present.

- [ ] **Step 8: Implement layout-shaped loading and final `DiscoverScreen` orchestration.**

Keep `HikariDestinationScaffold`, atmosphere background, `HikariTopLevelScaffold`, search header, retained `rememberLazyListState`, back-to-top threshold, `HikariPullToRefresh`, and `bodyPadding.withScreenContentInsets()`.

Inside `HikariPullToRefresh`, center the outer list and cap only medium/wider content using the existing breakpoint token rather than a Discover-local width:

```kotlin
val listModifier = Modifier
    .fillMaxHeight()
    .widthIn(max = MaterialTheme.hikariBreakpoints.medium)
    .align(Alignment.TopCenter)
    .testTag("discover-list")
```

At compact/large-phone widths this cap is above the viewport and changes nothing; wider windows stop stretching Hero/grid/ranking content past the existing shared medium breakpoint. The Latest grid stays exactly three columns on every window class.

Inside the outer `LazyColumn` use the fixed hierarchy:

```text
Popular header + pager when popular is non-empty
Media selector always
Latest header + grid when latestUpdates is non-empty
Top Rated header + ranking list when topRated is non-empty
failure feedback
```

When `state.loading` is true, render `DiscoverLoadingContent` with hero skeleton, selector geometry, nine Latest skeleton cards, and five Top Rated skeleton rows. When loading is false and all three semantic lists are empty, render `HikariEmptyState` for Discover. When cached content exists and `state.refreshing` is true, render real content unchanged.

Use `HikariSectionHeader` for `POPULAR`, `LATEST UPDATES`, and `TOP RATED`; do not pass an action because `See all` is deferred.

- [ ] **Step 9: Remove obsolete source/category components and clean imports/callers inside the feature.**

Delete the three old files only after `rg` confirms no production references:

```bash
rg -n 'DiscoverQuickCategory|DiscoverShelf|quickCategoryItem|sourceFilterItem|DiscoverCategoryCard' feature/catalog/src/main
```

Expected after cleanup: no matches for obsolete presentation APIs.

- [ ] **Step 10: Run focused Discover UI tests.**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*DiscoverSemanticsTest*' \
  --tests '*DiscoverTopLevelChromeTest*'
./gradlew :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.ui.discover.DiscoverScreenTest
```

Expected: PASS.

- [ ] **Step 11: Commit the screen redesign.**

```bash
git add feature/catalog/src/main feature/catalog/src/test feature/catalog/src/androidTest
git commit -m "feat(discover): redesign semantic discovery screen"
```

---

### Task 8: Update app-shell focus/navigation wiring to the media selector contract

**Files:**
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt:93-125`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt:37-52,95-107,217-229`
- Modify: `app/src/test/kotlin/app/openstory/navigation/AppShellScreenshotTest.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/navigation/AppNavigationTest.kt`

**Interfaces:**
- `DiscoverDestination` forwards only `onContentTypeSelected = viewModel::selectContentType` for Discover filtering.
- `AppNavFocus.discoverMediaType` replaces `discoverCategory` and `discoverCatalog`.
- Search -> utility -> media selector remains the explicit top-level focus chain; subsequent story cards can use normal traversal order.

- [ ] **Step 1: Update app navigation tests/fixtures to compile against the new `DiscoverScreen` signature and focus semantics.**

Replace the old expected sequence `Search -> utility -> Category -> All sources` with `Search -> utility -> Manga`. Assert Light Novel is disabled and does not become an actionable focus target.

- [ ] **Step 2: Run app tests and verify old callback/focus symbols fail.**

```bash
./gradlew :app:testDebugUnitTest
```

- [ ] **Step 3: Rewire `DiscoverDestination`.**

Remove `onCatalogSelected`, `onCategorySelected`, and `onCombinedSelected`. Pass:

```kotlin
onContentTypeSelected = viewModel::selectContentType,
mediaTypeFocusRequester = mediaTypeFocusRequester,
```

Keep search, story navigation, utility action, content padding, and lifecycle collection unchanged.

- [ ] **Step 4: Collapse Discover focus requesters in `AppNavHost`.**

Replace the two requesters:

```text
discoverCategoryFocus
discoverCatalogFocus
```

with:

```kotlin
val discoverMediaTypeFocus = remember { FocusRequester() }
```

Rename the `AppNavFocus` field to `discoverMediaType`; make `utilityNext` return it for Discover/other current fallback cases that previously used `discoverCategory`. Pass it to `DiscoverDestination`.

- [ ] **Step 5: Run app navigation tests.**

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.navigation.AppNavigationTest,app.openstory.AppLaunchSmokeTest
```

Expected: PASS; top-level navigation still retains Discover and Story/Search navigation still works.

- [ ] **Step 6: Commit navigation wiring.**

```bash
git add app/src/main app/src/test app/src/androidTest
git commit -m "refactor(app): wire discover media selection"
```

---

### Task 9: Refresh visual fixtures, benchmark seed, and run repository acceptance gates

**Files:**
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverScreenshotTest.kt`
- Regenerate: `feature/catalog/src/test/snapshots/discover/compact-dark.png`
- Regenerate: `feature/catalog/src/test/snapshots/discover/large-phone-dark.png`
- Regenerate: `feature/catalog/src/test/snapshots/discover/medium-dark.png`
- Regenerate: `feature/catalog/src/test/snapshots/discover/compact-light.png`
- Modify/regenerate relevant Discover fixture in `app/src/test/kotlin/app/openstory/navigation/AppShellScreenshotTest.kt` and `app/src/test/snapshots/app-shell/discover-light.png`
- Modify: `app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkFixtureActivity.kt:135-179`

**Interfaces:**
- Screenshot fixture contains exactly enough generic normalized data to exercise 5 Popular, 9 Latest, and 5 Top Rated without any provider-specific UI.
- Benchmark seed writes semantic Home sections directly through `CatalogHomeMutation`; it does not add or modify a real catalog plugin adapter.

- [ ] **Step 1: Rewrite screenshot fixtures with semantic data and stable artwork.**

Build 12 Manga `CatalogEntry` values. Reuse `fixture-story-1` and `fixture-story-2` across two fixture plugin IDs to exercise provider-agnostic dedupe while keeping the remaining StoryIds unique. Assign:

```text
Popular section       -> kind=POPULAR, at least 5 items, popularityRank 1..5
Latest section        -> kind=LATEST_UPDATES, at least 9 items, latestUpdate timestamps descending
Top Rated section     -> kind=TOP_RATED, at least 5 items, valid scores + genres + statuses
```

Use neutral fixture plugin IDs such as `catalog.fixture.a` / `catalog.fixture.b`; no screenshot text should expose those IDs.

- [ ] **Step 2: Run screenshot comparison first and inspect intentional diffs.**

```bash
./gradlew :feature:catalog:compareRoborazziDebug :app:compareRoborazziDebug
```

Expected before recording: Discover snapshots differ substantially and only in the redesigned areas.

- [ ] **Step 3: Record and verify reviewed screenshots.**

```bash
./gradlew :feature:catalog:recordRoborazziDebug :app:recordRoborazziDebug
./gradlew :feature:catalog:verifyRoborazziDebug :app:verifyRoborazziDebug
```

Review specifically: no pager peek, page dots sit inside Hero, selector fills content width, Latest is 3x3 on compact, Top Rated is one story per row, and medium width remains bounded by existing shared layout behavior.

- [ ] **Step 4: Update benchmark browse seed to semantic sections.**

Extend all 30 benchmark entries with deterministic `score`, `genres`, `publicationStatus`, and `latestUpdate`; keep artwork on the existing deterministic artwork path rather than introducing network-backed covers. Replace generic `Benchmark Shelf` sections with three sections:

```kotlin
CatalogHomeSection(
    sourceId = "benchmark-popular",
    title = "Popular",
    items = entries.take(5),
    kind = CatalogFeedKind.POPULAR,
)
CatalogHomeSection(
    sourceId = "benchmark-latest",
    title = "Latest Updates",
    items = entries.take(9),
    kind = CatalogFeedKind.LATEST_UPDATES,
)
CatalogHomeSection(
    sourceId = "benchmark-top-rated",
    title = "Top Rated",
    items = entries.take(5),
    kind = CatalogFeedKind.TOP_RATED,
)
```

Use positive popularity ranks and distinct latest timestamps so `discoverScroll` exercises real projected content.

- [ ] **Step 5: Run static/unit policy gates before connected/benchmark work.**

```bash
./gradlew :plugins:api:test \
  :catalog:testDebugUnitTest \
  :core:designsystem:testDebugUnitTest \
  :feature:catalog:testDebugUnitTest \
  :app:testDebugUnitTest
./scripts/tests/ui-target-pack-test.sh
./scripts/verify.sh
```

Expected: all green; UI token/shared-component and architecture/package-boundary gates remain green.

- [ ] **Step 6: Run connected acceptance tests.**

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.CatalogMigrationTest,app.openstory.storage.room.catalog.RoomCatalogRepositoryTest,app.openstory.storage.room.DatabaseBaselineTest

./gradlew :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.ui.discover.DiscoverScreenTest

./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.AppLaunchSmokeTest,app.openstory.navigation.AppNavigationTest
```

Expected: PASS. If the known UTP `AndroidTestApkInstallerPlugin.afterAll` environment issue appears after tests have actually passed, capture the test-result XML separately before classifying it; do not change product code to work around a host-tooling failure.

- [ ] **Step 7: Run the Discover scroll macrobenchmark against the semantic benchmark seed.**

On the benchmark device:

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#discoverScroll' \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark \
  --stacktrace
```

Expected: benchmark completes and `discover-list` remains the scroll owner. Preserve the generated benchmark JSON/Perfetto trace as review evidence rather than changing thresholds in this task.

- [ ] **Step 8: Final diff hygiene and commit.**

```bash
git diff --check
git status --short
git add feature/catalog/src/test/snapshots \
        app/src/test \
        app/src/benchmarkRelease \
        storage/room/schemas
git commit -m "test(discover): refresh semantic ui evidence"
```

---

## Acceptance Checklist

- [ ] Wire/source/domain contracts decode old plugin payloads and carry explicit semantic feed kind plus optional status/latest metadata.
- [ ] No provider/source ID or section-title substring is used to infer Popular/Latest/Top Rated.
- [ ] Room is version 7, migration 6 -> 7 is non-destructive, schema 7 is exported, and sparse merge preserves coherent latest-update metadata.
- [ ] Discover projection filters by `ContentType.MANGA`, deduplicates by canonical StoryId, and deterministically produces Popular <=5, Latest <=9, Top Rated <=5.
- [ ] Manga is selected/enabled; Light Novel is visible/disabled without changing layout width.
- [ ] Hero is manual, full-width, non-peeking, non-auto-paging, with non-interactive dots for multiple pages.
- [ ] Latest is three columns and never owns a nested vertical scroll.
- [ ] Top Rated is five full-width ranked rows with only rank/cover/title/rating/genres/status.
- [ ] Initial empty-cache bootstrap uses layout-shaped skeletons; cached refresh keeps content visible; completed all-empty state is explicit.
- [ ] Search, pull-to-refresh, safe insets, back-to-top, top-level retention, Story navigation, and utility action remain intact.
- [ ] Obsolete quick-category/catalog-source Discover UI code and semantics are removed.
- [ ] Roborazzi snapshots are deliberately regenerated from the new composition.
- [ ] Unit, connected, UI policy, verify, and Discover macrobenchmark gates complete without product regressions.

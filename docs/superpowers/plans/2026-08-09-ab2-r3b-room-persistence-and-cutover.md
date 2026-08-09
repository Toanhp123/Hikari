# Architecture Baseline 2 R3B - Room Persistence and Catalog Cutover Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the fresh Room schema 1 and semantic storage adapters, switch current Home/Search/Story flows to the R3A catalog boundary, and delete the legacy model/matching/database modules.

**Architecture:** `:storage:room` privately owns entities/DAOs/transactions and implements R3A catalog plus R2 runtime persistence contracts. Current Wave 05 presentation modules are temporarily ported to new catalog types so old core modules can be deleted before R4 replaces presentation itself.

**Tech Stack:** Room 2.8.4, Kotlin serialization 1.11.0, Android instrumentation, Gradle architecture verification.

## Global Constraints

- Architecture source of truth: `docs/superpowers/specs/2026-08-09-architecture-baseline-2-design.md`.
- This work is pre-Wave-06; do not implement Library, chapter sync, Reader, downloads, background sync, authentication, notifications, or release-hardening behavior.
- Android-native Kotlin remains fixed.
- Package namespace/application ID remains `app.openstory`.
- Minimum SDK remains 26; compile/target SDK remain 37 unless a dedicated architecture decision changes them.
- Build runtime remains JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0, Kotlin 2.4.10.
- Current retained libraries may be replaced only when a plan task explicitly does so; do not change versions opportunistically during this reset.
- Pre-MVP compatibility is intentionally breakable. Do not add permanent `Legacy*`, `Compat*`, `V1/V2` adapters, dual mappers, or Room migrations merely to preserve development-only contracts.
- Temporary migration-scoped bridges are allowed only when this plan names the bridge and its deletion task explicitly.
- Package-first, Gradle-module-second: do not create extra production modules beyond the approved target graph without a new architecture decision.
- TDD is mandatory for behavior changes: focused RED -> smallest GREEN -> affected module suite -> commit.
- Every task ends in a buildable, testable, independently reviewable repository state.
- Do not make a checkpoint green with `TODO()`, `error("not implemented")`, unconditional empty production results, or broad structural suppressions.
- Tests protect revalidated product/security invariants, not historical class shapes.
- Production Room entities/DAOs stay private to the storage adapter.
- Production plugin JavaScript receives only host-controlled capabilities and never Android `Context`, Room, filesystem paths, raw OkHttp, reflection, or plaintext managed credentials.

---
## R3 Closing Contract

Entry: R3A accepted.

R3 closes with:

```text
StoryId/PluginId     -> :core:common
Story/CatalogEntry   -> :catalog
matching/ranking     -> :catalog
CatalogRepository    -> :catalog
Room implementation  -> :storage:room
plugin state storage -> :storage:room
```

and these legacy production modules are deleted:

```text
:core:model
:core:matching
:core:database
```

No speculative Library/chapter/release/progress Room tables are carried into the new baseline.

### Task 1: Create the new Room schema 1 with only current owned durable state

**Files:**
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/DatabaseConverters.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogEntities.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogDao.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/plugins/PluginStateEntity.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/plugins/PluginVersionEntity.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/plugins/PluginDiagnosticEntity.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/plugins/PluginStateDao.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/plugins/PluginDiagnosticDao.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/plugins/RoomPluginStateStore.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/plugins/RoomPluginDiagnosticsSink.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/DatabaseBaselineTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/plugins/RoomPluginStateStoreTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/plugins/RoomPluginDiagnosticsSinkTest.kt`
- Create/replace: `storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/1.json`

**Interfaces:**
- New schema tables only for current durable ownership:

```text
stories
catalog_entries
catalog_home_snapshots
catalog_home_sections
catalog_home_items
plugin_state
plugin_versions
plugin_diagnostics
```

`plugin_state` stores enabled/current-version selection and accepted capability state. `plugin_versions` stores immutable installed-version provenance needed for update/rollback. `plugin_diagnostics` stores only redacted `PluginDiagnosticEvent` fields. No Library, content mapping, canonical chapter, chapter release, reading progress, download, sync cursor, or notification tables.

- [ ] **Step 1: Write fresh-schema RED tests**

Create `DatabaseBaselineTest.kt`:

```kotlin
@RunWith(AndroidJUnit4::class)
class DatabaseBaselineTest {
    @Test fun freshDatabaseContainsOnlyBaselineTwoTables() {
        val db = createInMemoryDatabase()
        val names = db.openHelper.readableDatabase.query(
            "SELECT name FROM sqlite_master WHERE type='table' AND name NOT LIKE 'android_%' AND name != 'room_master_table'"
        ).use { cursor -> buildSet { while (cursor.moveToNext()) add(cursor.getString(0)) } }
        assertEquals(setOf(
            "stories", "catalog_entries", "catalog_home_snapshots",
            "catalog_home_sections", "catalog_home_items",
            "plugin_state", "plugin_versions", "plugin_diagnostics",
        ), names)
        db.openHelper.readableDatabase.query("PRAGMA foreign_key_check").use { assertEquals(0, it.count) }
        listOf("library_entries", "content_mappings", "canonical_chapters", "chapter_releases", "reading_progress", "downloads")
            .forEach { assertFalse(it in names, "Speculative table present: $it") }
    }
}
```

Add a second test that reads `PRAGMA index_list('catalog_entries')` and the Home tables' foreign keys to prove source identity/order constraints are database-enforced rather than mapper-only.

- [ ] **Step 2: Write plugin persistence SPI RED tests**

Create target-storage contract tests:

```kotlin
@Test fun pluginStateAndVersionProvenanceRoundTrip() = runTest {
    val store = roomPluginStateStore(database)
    val expected = storedPluginState(active = "2.0.0", previous = "1.0.0", enabled = true)
    store.replace(expected)
    assertEquals(expected, store.find(expected.pluginId))
}

@Test fun diagnosticsAreNewestFirstBoundedAndRedacted() = runTest {
    val sink = roomPluginDiagnosticsSink(database)
    sink.record(diagnostic(at = 1, safeDetail = "first"))
    sink.record(diagnostic(at = 2, safeDetail = "second"))
    assertEquals(listOf("second"), sink.recent(testPluginId, limit = 1).map { it.safeDetail })
    assertFalse(sink.recent(testPluginId, 10).joinToString().contains("marker-secret"))
}
```

The plugin persistence implementation files must import only `app.openstory.plugins.runtime.persistence.*` from runtime.

- [ ] **Step 3: Verify RED on Android**

```bash
./gradlew :storage:room:connectedDebugAndroidTest   -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.DatabaseBaselineTest,app.openstory.storage.room.plugins.RoomPluginStateStoreTest,app.openstory.storage.room.plugins.RoomPluginDiagnosticsSinkTest   --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 4: Implement schema version 1 and export compiler JSON**

Use Room `version = 1`. Do not create any migration. Physically isolate Room packages from runtime internals; `RoomPluginStateStore` and `RoomPluginDiagnosticsSink` import only `app.openstory.plugins.runtime.persistence.*`. Add foreign keys/unique constraints so one plugin/version provenance row is immutable by `(plugin_id, version)` and diagnostics cannot cascade-delete unrelated catalog data.

- [ ] **Step 5: Run storage tests**

```bash
./gradlew :storage:room:testDebugUnitTest :storage:room:connectedDebugAndroidTest --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 6: Commit**

```bash
git add storage/room/src storage/room/schemas
git commit -m "storage: establish baseline two room schema"
```

### Task 2: Implement `RoomCatalogRepository` semantic commits

**Files:**
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogEntityMapper.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt`

**Interfaces:**
- Implements exactly `CatalogRepository`.
- Owns `withTransaction`.
- Returns catalog models only; never exposes entity/DAO.

- [ ] **Step 1: Port RED persistence invariants**

Required tests:

```text
same source identity updates one entry
same sourceId from different plugins remains separate
section/item order round-trips
removed Home card keeps entry/story
refresh A does not mutate B
failed transaction keeps previous complete source snapshot and freshness
details enrichment does not alter Home membership
one mutation timestamp/version persists consistently
```

- [ ] **Step 2: Verify RED**

Run focused Room test; expected **FAIL**.

- [ ] **Step 3: Implement repository transaction**

One `commitHomeRefresh` transaction:
1. upserts resolved stories;
2. upserts sparse card-owned entry fields without clearing richer details;
3. replaces only the source's Home sections/items;
4. updates source snapshot provenance;
5. commits atomically.

`commitDetails` enriches entry/source metadata only.

- [ ] **Step 4: Run Room + catalog contract suites**

```bash
./gradlew :catalog:test :storage:room:connectedDebugAndroidTest --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add storage/room/src/main/kotlin/app/openstory/storage/room/catalog   storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog
git commit -m "storage: implement catalog semantic commits"
```

### Task 3: Switch current Wave 05 screens to new catalog models/services, then delete old core modules

**Files:**
- Modify: `feature/home/build.gradle.kts`
- Modify: `feature/story/build.gradle.kts`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/domain/CanonicalStoryCandidates.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/domain/CatalogSnapshotMapper.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/domain/ObserveCombinedHome.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/domain/RefreshHome.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/domain/SearchCanonicalizer.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/domain/SearchCatalogs.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/domain/SearchModels.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/model/HomeUiModel.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/ui/HomeActions.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/ui/HomeCard.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/ui/HomeScreen.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/ui/HomeViewModel.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/ui/SearchScreen.kt`
- Modify: `feature/home/src/main/kotlin/app/openstory/home/ui/SearchViewModel.kt`
- Modify: `feature/story/src/main/kotlin/app/openstory/story/domain/CatalogDetailsMapper.kt`
- Modify: `feature/story/src/main/kotlin/app/openstory/story/ui/StoryDetailViewModel.kt`
- Modify: `feature/home/src/test/kotlin/app/openstory/home/domain/CatalogSnapshotMapperTest.kt`
- Modify: `feature/home/src/test/kotlin/app/openstory/home/domain/RefreshHomeTest.kt`
- Modify: `feature/home/src/test/kotlin/app/openstory/home/domain/SearchCatalogsTest.kt`
- Modify: `feature/home/src/test/kotlin/app/openstory/home/ui/HomeViewModelTest.kt`
- Modify: `feature/story/src/test/kotlin/app/openstory/story/domain/CatalogDetailsMapperTest.kt`
- Modify: `feature/story/src/test/kotlin/app/openstory/story/ui/StoryDetailViewModelTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/OpenStoryAppGraph.kt`
- Modify: `app/build.gradle.kts`
- Modify: `test/fixtures/build.gradle.kts`
- Delete: `core/model/`
- Delete: `core/matching/`
- Delete: `core/database/`
- Modify: `settings.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `scripts/verify-room-schema-stability.sh`
- Modify: `scripts/instrumentation/database.sh`
- Modify: `scripts/checkpoints/database.sh`
- Modify: `scripts/verify-baseline-architecture.sh`

**Interfaces:**
- Temporary presentation modules now depend only on `:core:common` + `:catalog` plus UI libraries.
- `OpenStoryAppGraph` temporarily constructs/binds `storage.room.OpenStoryDatabase`, `RoomCatalogRepository`, catalog services and runtime state store. R4 deletes the graph itself.

- [ ] **Step 1: Port legacy presentation tests to new types first**

Update Home/Search/Story tests so they compile against `app.openstory.catalog.*` and common IDs. Do not create duplicate model adapters.

- [ ] **Step 2: Switch application composition to new storage/catalog**

Use the new Room database for both catalog and plugin state. Development emulator data may be cleared; do not add migration from the old DB.

- [ ] **Step 3: Delete old modules and all speculative models/tables**

Remove `core:model`, `core:matching`, `core:database` from settings, app/feature/test dependencies and architecture policy.

Update schema verification default path:

```text
storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase
```

Update database instrumentation Gradle target to `:storage:room`.

- [ ] **Step 4: Run full R3 functional gate**

```bash
./gradlew :core:common:test :catalog:test   :storage:room:testDebugUnitTest   :feature:home:test :feature:story:test   :app:testDebugUnitTest --stacktrace

./gradlew :storage:room:connectedDebugAndroidTest   :app:connectedDebugAndroidTest --stacktrace

./scripts/verify.sh
```

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "catalog: replace legacy model matching and database"
```

### Task 4: Record R3 acceptance

**Files:**
- Create: `docs/internal/checkpoints/architecture-baseline-2-r3.md`
- Modify: `docs/project/current-state.md`

- [ ] **Step 1: Assert deleted module paths**

```bash
test ! -d core/model
test ! -d core/matching
test ! -d core/database
```

- [ ] **Step 2: Verify target dependency direction**

```bash
./scripts/check-module-dependencies.sh
bash scripts/verify-package-boundaries.sh
```

Expected: **PASS**.

- [ ] **Step 3: Verify schema is exactly one new `1.json`**

```bash
./scripts/verify-room-schema-stability.sh
```

Expected: one fingerprint from the `storage/room` schema directory.

- [ ] **Step 4: Record evidence and advance state**

Set:

```text
Architecture Baseline 2 R3: ACCEPTED
Current active boundary: R4 - Presentation, Navigation, and DI
```

- [ ] **Step 5: Commit**

```bash
git add docs/internal/checkpoints/architecture-baseline-2-r3.md   docs/project/current-state.md
git commit -m "architecture: accept baseline two r3"
```


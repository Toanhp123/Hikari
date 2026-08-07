# Wave 02 — Domain and Local Storage Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Define the canonical story/chapter/release model and persist it transactionally without coupling the domain to Android or Room.

**Architecture:** Platform-neutral models express identity and invariants. Room uses normalized internal entities and explicit joins; repositories translate graphs inside transactions and expose flows/domain results to later features.

**Tech Stack:** Kotlin, kotlinx.serialization, Room 2.8.4, coroutines/Flow, AndroidX test, Room migration testing.

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
- Every persistence change needs a migration test; every plugin contract needs deterministic fixtures.
- TDD is mandatory: demonstrate the focused failure, implement the smallest behavior, run focused tests, then run the module suite.
- Commit after each task. Do not combine tasks across checkpoints.
- Any deterministic `*Fixture`, fake, or test assertion helper shown in a test block is created in that task’s listed test file or `:test:fixtures`; it must not call live websites.


## Role of This Wave

This wave is the source of truth for identity and durability. It prevents later plugin/UI code from inventing competing notions of a story, chapter, release, or progress record.

## Entry Dependencies

- Wave 01 checkpoint is approved.
- The app/common/model modules compile and CI is green.
- No product persistence exists outside `core:database`.

## Exit Deliverables

- Typed pure domain graph.
- Room v1 schema with committed JSON.
- Transactional local story/progress repositories.
- Migration and backup-policy harness.
- Metadata-only library support.

## File/Module Boundary

Each path listed in a task owns one responsibility. Do not move business rules into Compose screens, Room entities, JavaScript snippets, or WorkManager classes. Domain interfaces are the dependency boundary; Android adapters implement them.

---

### Task 1: Define typed domain identifiers and content/status enums

**Files:**
- Create: core/model/src/main/kotlin/app/openstory/model/Ids.kt
- Create: core/model/src/main/kotlin/app/openstory/model/ContentType.kt
- Create: core/model/src/main/kotlin/app/openstory/model/LibraryStatus.kt
- Create: core/model/src/main/kotlin/app/openstory/model/LanguageTag.kt
- Test: core/model/src/test/kotlin/app/openstory/model/IdsTest.kt

**Interfaces:**
- Consumes: `StableId` validation from Wave 01.
- Produces: Non-interchangeable value classes for story, chapter, release, plugin, catalog entry, content mapping, and download IDs; content/status/language types.

**Acceptance:**
- Blank IDs fail at construction.
- Language tags normalize underscores to BCP-47-style hyphens and lowercase language subtags.
- MVP content types explicitly include LIGHT_NOVEL and WEB_NOVEL while reserving MANGA and ANIME.

**Implementation notes:**
- Do not expose database `Long` keys outside `core:database`; stable string IDs cross module and package boundaries.
- Use `java.util.Locale.ROOT` for language normalization to avoid device-locale bugs.
- Keep enums serializable because plugin/package and saved-state layers consume them.

- [ ] **Step 1: Write the failing test**

Create `core/model/src/test/kotlin/app/openstory/model/IdsTest.kt`:

```kotlin
package app.openstory.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class IdsTest {
    @Test fun differentIdentifierTypesRetainStableValue() {
        assertEquals("story_1", StoryId("story_1").value)
        assertEquals("chapter_1", ChapterId("chapter_1").value)
        assertFailsWith<IllegalArgumentException> { ReleaseId(" ") }
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:model:test --tests app.openstory.model.IdsTest.differentIdentifierTypesRetainStableValue
```

Expected: **FAIL** because typed identifiers are undefined.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/model/src/main/kotlin/app/openstory/model/Ids.kt`:

```kotlin
package app.openstory.model

@JvmInline value class StoryId(val value: String) { init { require(value.isNotBlank() && value.none(Char::isWhitespace)) } }
@JvmInline value class ChapterId(val value: String) { init { require(value.isNotBlank() && value.none(Char::isWhitespace)) } }
@JvmInline value class ReleaseId(val value: String) { init { require(value.isNotBlank() && value.none(Char::isWhitespace)) } }
@JvmInline value class PluginId(val value: String) { init { require(value.isNotBlank() && value.none(Char::isWhitespace)) } }
@JvmInline value class CatalogEntryId(val value: String) { init { require(value.isNotBlank() && value.none(Char::isWhitespace)) } }
@JvmInline value class ContentMappingId(val value: String) { init { require(value.isNotBlank() && value.none(Char::isWhitespace)) } }
@JvmInline value class DownloadId(val value: String) { init { require(value.isNotBlank() && value.none(Char::isWhitespace)) } }
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:model:test --tests app.openstory.model.IdsTest.differentIdentifierTypesRetainStableValue
./gradlew :core:model:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/model/src/main/kotlin/app/openstory/model/Ids.kt core/model/src/main/kotlin/app/openstory/model/ContentType.kt core/model/src/main/kotlin/app/openstory/model/LibraryStatus.kt core/model/src/main/kotlin/app/openstory/model/LanguageTag.kt core/model/src/test/kotlin/app/openstory/model/IdsTest.kt
git commit -m "model: add typed ids and content enums"
```

### Task 2: Model canonical stories, catalog entries, and local library state

**Files:**
- Create: core/model/src/main/kotlin/app/openstory/model/CanonicalStory.kt
- Create: core/model/src/main/kotlin/app/openstory/model/CatalogEntry.kt
- Create: core/model/src/main/kotlin/app/openstory/model/LibraryEntry.kt
- Test: core/model/src/test/kotlin/app/openstory/model/CanonicalStoryTest.kt

**Interfaces:**
- Consumes: Typed IDs, content types, language tags, and Kotlin immutable collections conventions.
- Produces: Pure models preserving multiple catalog-specific metadata records under one canonical story and optional local library membership.

**Acceptance:**
- A story may exist without catalog entries after import/manual creation.
- Catalog-specific score scale and raw score are preserved rather than flattened destructively.
- Library state can exist before any readable content mapping is found.

**Implementation notes:**
- Keep source URLs in catalog/content adapter records, not as canonical identity.
- Store author names, genres, cover references, publication status, and external IDs in `CatalogEntry`; merge policies are added later.
- Represent library status and timestamps in `LibraryEntry`, separate from immutable story identity.

- [ ] **Step 1: Write the failing test**

Create `core/model/src/test/kotlin/app/openstory/model/CanonicalStoryTest.kt`:

```kotlin
package app.openstory.model

import kotlin.test.Test
import kotlin.test.assertEquals

class CanonicalStoryTest {
    @Test fun storyRetainsSeparateCatalogScores() {
        val story = CanonicalStory(
            id = StoryId("s1"), contentType = ContentType.WEB_NOVEL,
            preferredTitle = "Example", aliases = emptySet(),
            catalogEntries = listOf(
                CatalogEntry(CatalogEntryId("mal:1"), PluginId("mal"), "Example", null, 8.4, 10.0),
                CatalogEntry(CatalogEntryId("ani:1"), PluginId("ani"), "Example", null, 84.0, 100.0),
            )
        )
        assertEquals(listOf(10.0, 100.0), story.catalogEntries.map { it.scoreScale })
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:model:test --tests app.openstory.model.CanonicalStoryTest.storyRetainsSeparateCatalogScores
```

Expected: **FAIL** because canonical story and catalog entry models do not exist.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/model/src/main/kotlin/app/openstory/model/CanonicalStory.kt`:

```kotlin
package app.openstory.model

data class CanonicalStory(
    val id: StoryId,
    val contentType: ContentType,
    val preferredTitle: String,
    val aliases: Set<String>,
    val catalogEntries: List<CatalogEntry>,
) { init { require(preferredTitle.isNotBlank()) } }

data class CatalogEntry(
    val id: CatalogEntryId,
    val catalogPluginId: PluginId,
    val title: String,
    val description: String?,
    val score: Double?,
    val scoreScale: Double?,
) { init { require(score == null || (scoreScale != null && score in 0.0..scoreScale)) } }
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:model:test --tests app.openstory.model.CanonicalStoryTest.storyRetainsSeparateCatalogScores
./gradlew :core:model:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/model/src/main/kotlin/app/openstory/model/CanonicalStory.kt core/model/src/main/kotlin/app/openstory/model/CatalogEntry.kt core/model/src/main/kotlin/app/openstory/model/LibraryEntry.kt core/model/src/test/kotlin/app/openstory/model/CanonicalStoryTest.kt
git commit -m "model: add canonical story and library models"
```

### Task 3: Model content mappings, canonical chapters, releases, and progress

**Files:**
- Create: core/model/src/main/kotlin/app/openstory/model/ContentMapping.kt
- Create: core/model/src/main/kotlin/app/openstory/model/CanonicalChapter.kt
- Create: core/model/src/main/kotlin/app/openstory/model/ChapterRelease.kt
- Create: core/model/src/main/kotlin/app/openstory/model/ReadingProgress.kt
- Test: core/model/src/test/kotlin/app/openstory/model/ReadingProgressTest.kt

**Interfaces:**
- Consumes: Canonical story identifiers, plugin identifiers, language tags, and time primitives.
- Produces: MangaDex-style chapter/release domain: one canonical chapter has many releases; progress stores canonical and exact release position.

**Acceptance:**
- Two releases can share one canonical chapter without sharing plugin/source IDs.
- Progress can mark a canonical chapter read while retaining last release and position.
- Special chapters are represented by semantic kind plus optional numeric sort components, never forced to integers.

**Implementation notes:**
- Define `ChapterKind` as NUMBERED, PROLOGUE, EPILOGUE, SIDE_STORY, EXTRA, UNKNOWN.
- `ChapterRelease` carries language, group/uploader, source publication timestamps, content fingerprint, and availability state.
- `ContentMapping` records confidence, origin (automatic/plugin/user), and user lock so later sync cannot overwrite a correction.

- [ ] **Step 1: Write the failing test**

Create `core/model/src/test/kotlin/app/openstory/model/ReadingProgressTest.kt`:

```kotlin
package app.openstory.model

import kotlin.test.Test
import kotlin.test.assertEquals

class ReadingProgressTest {
    @Test fun progressRetainsCanonicalAndReleaseIdentity() {
        val progress = ReadingProgress(
            storyId = StoryId("s1"), chapterId = ChapterId("c100"),
            releaseId = ReleaseId("sourceA:100"), position = ReaderPosition.Paragraph(12, 0.45f),
            completed = true, updatedAtEpochMillis = 1000,
        )
        assertEquals(ChapterId("c100"), progress.chapterId)
        assertEquals(ReleaseId("sourceA:100"), progress.releaseId)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:model:test --tests app.openstory.model.ReadingProgressTest.progressRetainsCanonicalAndReleaseIdentity
```

Expected: **FAIL** because reading progress and release types are missing.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/model/src/main/kotlin/app/openstory/model/ReadingProgress.kt`:

```kotlin
package app.openstory.model

sealed interface ReaderPosition {
    data class Paragraph(val index: Int, val fraction: Float) : ReaderPosition {
        init { require(index >= 0); require(fraction in 0f..1f) }
    }
    data object Start : ReaderPosition
}

data class ReadingProgress(
    val storyId: StoryId,
    val chapterId: ChapterId,
    val releaseId: ReleaseId?,
    val position: ReaderPosition,
    val completed: Boolean,
    val updatedAtEpochMillis: Long,
)
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:model:test --tests app.openstory.model.ReadingProgressTest.progressRetainsCanonicalAndReleaseIdentity
./gradlew :core:model:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/model/src/main/kotlin/app/openstory/model/ContentMapping.kt core/model/src/main/kotlin/app/openstory/model/CanonicalChapter.kt core/model/src/main/kotlin/app/openstory/model/ChapterRelease.kt core/model/src/main/kotlin/app/openstory/model/ReadingProgress.kt core/model/src/test/kotlin/app/openstory/model/ReadingProgressTest.kt
git commit -m "model: add chapter release and progress domain"
```

### Task 4: Create Room database schema and lossless type converters

**Files:**
- Create: core/database/build.gradle.kts
- Create: core/database/src/main/kotlin/app/openstory/database/OpenStoryDatabase.kt
- Create: core/database/src/main/kotlin/app/openstory/database/DatabaseConverters.kt
- Create: core/database/src/main/kotlin/app/openstory/database/entity/StoryEntities.kt
- Create: core/database/src/main/kotlin/app/openstory/database/entity/ChapterEntities.kt
- Create: core/database/src/main/kotlin/app/openstory/database/entity/PluginStateEntities.kt
- Test: core/database/src/androidTest/kotlin/app/openstory/database/DatabaseConvertersTest.kt

**Interfaces:**
- Consumes: Pure domain model definitions from Tasks 1–3.
- Produces: Room schema version 1 with normalized tables, foreign keys, indices, and converters capable of lossless domain persistence.

**Acceptance:**
- Catalog entries and content mappings cascade only when their canonical story is intentionally deleted.
- Deleting a plugin does not delete canonical stories, progress, or explicit downloads.
- Release uniqueness is `(plugin_id, source_release_id)`.
- Committed schema JSON exactly matches version 1.

**Implementation notes:**
- Use explicit join tables for story-catalog, story-mapping, and canonical-chapter-release relationships.
- Enable foreign keys and WAL; expose no Room entity from the module public API.
- Export schemas to `core/database/schemas` and commit them.

- [ ] **Step 1: Write the failing test**

Create `core/database/src/androidTest/kotlin/app/openstory/database/DatabaseConvertersTest.kt`:

```kotlin
package app.openstory.database

import androidx.test.ext.junit.runners.AndroidJUnit4
import app.openstory.model.ReaderPosition
import kotlin.test.Test
import kotlin.test.assertEquals
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class DatabaseConvertersTest {
    @Test fun readerPositionRoundTrips() {
        val source = ReaderPosition.Paragraph(7, 0.25f)
        assertEquals(source, DatabaseConverters.toReaderPosition(DatabaseConverters.fromReaderPosition(source)))
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.database.DatabaseConvertersTest
```

Expected: **FAIL** because Room schema and converters are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/database/src/main/kotlin/app/openstory/database/DatabaseConverters.kt`:

```kotlin
package app.openstory.database

import androidx.room.TypeConverter
import app.openstory.model.ReaderPosition
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object DatabaseConverters {
    @Serializable private data class PositionDto(val kind: String, val index: Int = 0, val fraction: Float = 0f)

    @TypeConverter fun fromReaderPosition(value: ReaderPosition): String = Json.encodeToString(
        PositionDto.serializer(), when (value) {
            ReaderPosition.Start -> PositionDto("start")
            is ReaderPosition.Paragraph -> PositionDto("paragraph", value.index, value.fraction)
        }
    )
    @TypeConverter fun toReaderPosition(value: String): ReaderPosition =
        Json.decodeFromString(PositionDto.serializer(), value).let {
            if (it.kind == "start") ReaderPosition.Start else ReaderPosition.Paragraph(it.index, it.fraction)
        }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.database.DatabaseConvertersTest
./gradlew :core:database:testDebugUnitTest :core:database:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/database/build.gradle.kts core/database/src/main/kotlin/app/openstory/database/OpenStoryDatabase.kt core/database/src/main/kotlin/app/openstory/database/DatabaseConverters.kt core/database/src/main/kotlin/app/openstory/database/entity/StoryEntities.kt core/database/src/main/kotlin/app/openstory/database/entity/ChapterEntities.kt core/database/src/main/kotlin/app/openstory/database/entity/PluginStateEntities.kt core/database/src/androidTest/kotlin/app/openstory/database/DatabaseConvertersTest.kt
git commit -m "database: add normalized room schema version one"
```

### Task 5: Implement transactional DAOs and domain repositories

**Files:**
- Create: core/database/src/main/kotlin/app/openstory/database/dao/StoryDao.kt
- Create: core/database/src/main/kotlin/app/openstory/database/dao/ChapterDao.kt
- Create: core/database/src/main/kotlin/app/openstory/database/dao/ProgressDao.kt
- Create: core/database/src/main/kotlin/app/openstory/database/repository/LocalStoryRepository.kt
- Create: core/database/src/main/kotlin/app/openstory/database/repository/RoomStoryRepository.kt
- Test: core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomStoryRepositoryTest.kt

**Interfaces:**
- Consumes: Room schema version 1 and domain models.
- Produces: Flow-based read APIs and transaction-safe writes that map Room entities to domain objects without leaking database types.

**Acceptance:**
- Adding a metadata-only story and library entry is one atomic transaction.
- Replacing releases for one source does not remove releases from other plugins.
- Progress upsert uses newest `updatedAtEpochMillis` and cannot regress to stale work.

**Implementation notes:**
- Use `@Transaction` DAO methods for graph replacement and aggregate reads.
- Map errors to safe `AppError.Storage` codes; preserve cause only in internal structured logs.
- Use `distinctUntilChanged` at DAO query boundaries to avoid redundant Compose recomposition.

- [ ] **Step 1: Write the failing test**

Create `core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomStoryRepositoryTest.kt`:

```kotlin
package app.openstory.database.repository

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import app.openstory.database.OpenStoryDatabase
import app.openstory.model.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class RoomStoryRepositoryTest {
    @Test fun addMetadataOnlyStoryIsAtomic() = runTest {
        val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), OpenStoryDatabase::class.java).build()
        val repo = RoomStoryRepository(db)
        repo.addToLibrary(CanonicalStory(StoryId("s1"), ContentType.WEB_NOVEL, "Story", emptySet(), emptyList()), LibraryStatus.WANT_TO_READ)
        assertEquals("Story", repo.observeStory(StoryId("s1")).first()?.preferredTitle)
        db.close()
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.database.repository.RoomStoryRepositoryTest
```

Expected: **FAIL** because repository interfaces and Room transactions are not implemented.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/database/src/main/kotlin/app/openstory/database/repository/LocalStoryRepository.kt`:

```kotlin
package app.openstory.database.repository

import app.openstory.common.AppResult
import app.openstory.model.*
import kotlinx.coroutines.flow.Flow

interface LocalStoryRepository {
    fun observeStory(id: StoryId): Flow<CanonicalStory?>
    fun observeLibrary(): Flow<List<LibraryEntry>>
    suspend fun addToLibrary(story: CanonicalStory, status: LibraryStatus): AppResult<Unit>
    suspend fun upsertProgress(progress: ReadingProgress): AppResult<Unit>
    suspend fun replaceSourceReleases(mappingId: ContentMappingId, releases: List<ChapterRelease>): AppResult<Unit>
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:database:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.database.repository.RoomStoryRepositoryTest
./gradlew :core:database:testDebugUnitTest :core:database:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/database/src/main/kotlin/app/openstory/database/dao/StoryDao.kt core/database/src/main/kotlin/app/openstory/database/dao/ChapterDao.kt core/database/src/main/kotlin/app/openstory/database/dao/ProgressDao.kt core/database/src/main/kotlin/app/openstory/database/repository/LocalStoryRepository.kt core/database/src/main/kotlin/app/openstory/database/repository/RoomStoryRepository.kt core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomStoryRepositoryTest.kt
git commit -m "database: add transactional domain repositories"
```

### Task 6: Add migration harness, backup policy, and database integrity tests

**Files:**
- Create: core/database/src/androidTest/kotlin/app/openstory/database/MigrationTest.kt
- Create: core/database/src/androidTest/assets/database/v1/openstory.db
- Create: app/src/main/res/xml/backup_rules.xml
- Create: app/src/main/res/xml/data_extraction_rules.xml
- Create: core/database/src/test/kotlin/app/openstory/database/SchemaPolicyTest.kt
- Modify: app/src/main/AndroidManifest.xml

**Interfaces:**
- Consumes: Room version 1 schema, repositories, and exported schema JSON.
- Produces: A repeatable migration test harness, explicit Android backup exclusions for sensitive/plugin/cache data, and schema-policy enforcement.

**Acceptance:**
- Every future database version must add a migration fixture.
- Cookie/session tables, cache files, and downloaded chapter bodies are excluded from Android cloud backup.
- Library metadata/progress backup policy is explicit rather than accidental.
- Foreign-key integrity check passes after repository operations.

**Implementation notes:**
- Generate the v1 fixture by opening the real database and seeding representative story/catalog/mapping/chapter/release/progress rows.
- Use Room `MigrationTestHelper` even for version 1 so the harness exists before the first migration.
- Document whether library metadata participates in device transfer; never include auth sessions.

- [ ] **Step 1: Write the failing test**

Create `core/database/src/test/kotlin/app/openstory/database/SchemaPolicyTest.kt`:

```kotlin
package app.openstory.database

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class SchemaPolicyTest {
    @Test fun versionOneSchemaIsCommitted() {
        val schema = File("schemas/app.openstory.database.OpenStoryDatabase/1.json")
        assertTrue(schema.isFile, "Commit the Room v1 schema JSON")
        assertTrue("canonical_chapters" in schema.readText())
        assertTrue("chapter_releases" in schema.readText())
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:database:testDebugUnitTest --tests app.openstory.database.SchemaPolicyTest.versionOneSchemaIsCommitted
```

Expected: **FAIL** because schema policy and committed schema fixture are missing.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `app/src/main/res/xml/data_extraction_rules.xml`:

```kotlin
<?xml version="1.0" encoding="utf-8"?>
<data-extraction-rules>
    <cloud-backup>
        <exclude domain="database" path="plugin_sessions.db" />
        <exclude domain="file" path="cache/" />
        <exclude domain="file" path="downloads/" />
    </cloud-backup>
    <device-transfer>
        <exclude domain="database" path="plugin_sessions.db" />
    </device-transfer>
</data-extraction-rules>
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:database:testDebugUnitTest --tests app.openstory.database.SchemaPolicyTest.versionOneSchemaIsCommitted
./gradlew :core:database:testDebugUnitTest :core:database:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/database/src/androidTest/kotlin/app/openstory/database/MigrationTest.kt core/database/src/androidTest/assets/database/v1/openstory.db app/src/main/res/xml/backup_rules.xml app/src/main/res/xml/data_extraction_rules.xml core/database/src/test/kotlin/app/openstory/database/SchemaPolicyTest.kt app/src/main/AndroidManifest.xml
git commit -m "database: enforce schema migration and backup policy"
```

## Wave Checkpoint

Do not begin `2026-08-03-03-plugin-contracts-and-packages.md` until every item below is demonstrated on a clean checkout:

- [ ] A metadata-only story can be added and observed after database reopen.
- [ ] Two source releases persist under one canonical chapter.
- [ ] Deleting/disabling a plugin does not erase canonical progress or downloaded-content metadata.
- [ ] Database schema JSON and v1 fixture are committed.
- [ ] Instrumentation tests pass on API 26 and API 37 emulators.

## Full Verification

```bash
./gradlew clean testDebugUnitTest lintDebug --stacktrace
```

Expected: **BUILD SUCCESSFUL**, no ignored failing tests, no unresolved lint errors, and no generated database schema drift.

## Review Packet

Attach to the checkpoint review:

- Commit range for this wave.
- Focused test output for every task.
- Full verification output.
- Any deliberate deviations from the approved design, with rationale and updated spec text.
- Screenshots or screen recordings only when the wave changes visible UI.

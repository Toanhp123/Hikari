<!--
DOCUMENT LIFECYCLE
Status: READY TO START / ARCHITECTURE BASELINE 2 ACCEPTED
Current repository note: Begin at Task 01 on the accepted seven-module graph.
Canonical execution status: ../../project/current-state.md
-->

# Wave 06 - Library and Story Matching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let users add stories immediately to a metadata-only local Library, then find, explain, approve, and persist readable content mappings across installed JavaScript plugins.

**Architecture:** Wave 06 extends the accepted Architecture Baseline 2 graph without adding production modules. `:catalog` owns Library and matching behavior, `:storage:room` owns schema and transactions, `:feature:catalog` owns Library and mapping presentation, `:plugins:runtime` remains the only plugin execution facade, and `:app` owns composition, navigation, and Android scheduling adapters.

**Tech Stack:** Kotlin 2.4.10, Room 2.8.4, coroutines 1.11.0, Compose BOM 2026.06.00, Navigation 3 1.1.4, Hilt 2.60.1, WorkManager 2.11.2, AndroidX JavaScriptEngine 1.1.0.

## Global Constraints

- Preserve exactly `:app`, `:core:common`, `:catalog`, `:feature:catalog`, `:storage:room`, `:plugins:api`, and `:plugins:runtime`.
- Package-first, Gradle-module-second: this wave adds focused packages inside existing owners.
- Package namespace/application ID remains `app.openstory`; min SDK 26 and compile/target SDK 37 remain fixed.
- Build runtime remains JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0, and Kotlin 2.4.10.
- Room schema 1 is frozen Architecture Baseline 2. Every schema change uses an exported schema and a tested forward migration.
- JavaScript is the only plugin execution model. Content requests use `PluginOperation.CONTENT_*` through `PluginRuntime` and serialized protocol DTOs.
- Library membership commits locally before any plugin invocation or scheduled mapping search.
- Catalog owns matching decisions; Room persists decisions but never computes them.
- Room entities and DAOs remain internal to `:storage:room`.
- UI consumes catalog-owned services and models; it does not import Room or plugin runtime internals.
- No chapter synchronization, Reader, downloads, account sync, authentication, notifications, or release-hardening behavior is implemented in this wave.
- TDD is mandatory: focused RED, smallest GREEN, affected module suite, then commit.
- Commit after each task. Do not combine task commits.

## Entry Dependencies

- Architecture Baseline 2 checkpoint is accepted.
- The seven-module graph and package boundaries pass `./scripts/verify.sh`.
- Catalog story identity and details are stable.
- Content protocol operations execute through the secure JavaScript runtime.

## Exit Deliverables

- Immediate metadata-only Library membership and local status changes.
- Library list/filter UI with searching, linked, review, and no-mapping states.
- Deterministic, explainable content-story matching.
- Preferred-source quick search plus bounded deferred expansion.
- User-approved mappings protected from later automation.
- Manual source search and allowlisted URL resolution UI.

---

### Task 1: Add metadata-only Library persistence and commands

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/library/LibraryModels.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/library/LibraryRepository.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/library/LibraryService.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/library/LibraryServiceTest.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/library/LibraryEntity.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/library/LibraryDao.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/library/RoomLibraryRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/library/LibraryMigrationTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/CatalogModule.kt`
- Modify: `scripts/verify-architecture-baseline-2.sh`
- Modify: `scripts/tests/verify-architecture-baseline-2-test.sh`

**Interfaces:**

```kotlin
enum class LibraryStatus { WANT_TO_READ, READING, COMPLETED, ON_HOLD, DROPPED }

data class LibraryEntry(
    val storyId: StoryId,
    val status: LibraryStatus,
    val addedAtEpochMillis: Long,
    val updatedAtEpochMillis: Long,
)

interface LibraryRepository {
    fun observe(): Flow<List<LibraryEntry>>
    suspend fun add(storyId: StoryId, status: LibraryStatus, nowEpochMillis: Long)
    suspend fun remove(storyId: StoryId)
    suspend fun changeStatus(storyId: StoryId, status: LibraryStatus, nowEpochMillis: Long)
}
```

- [ ] **Step 1: Write focused RED tests**

Prove that `LibraryService.add` performs one repository write, does not know about plugin execution, and adding the same story preserves its existing status unless a status change is explicit. Add a Room migration test that opens schema 1, migrates to schema 2, and verifies the new membership table without altering Baseline 2 catalog/plugin rows. Update the architecture verifier contract test first: the accepted `1.json` hash must remain `adbd52a78feebd2eee197ccb58f0c209852ca059abd9fe1327bbfa962ba2011a`, contiguous later schema files are allowed, an edited schema 1 is rejected, and a numbered gap is rejected.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :catalog:test --tests app.openstory.catalog.library.LibraryServiceTest \
  :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.library.LibraryMigrationTest
./scripts/tests/verify-architecture-baseline-2-test.sh
```

Expected: **FAIL** because Library contracts, schema 2, the Room adapter, and the post-baseline schema-history rule do not exist.

- [ ] **Step 3: Implement the smallest local transaction**

Add catalog-owned Library contracts and a service using the injected `Clock`. Implement schema `1 -> 2`, an internal entity/DAO, and `RoomLibraryRepository`. The Room transaction inserts membership idempotently and never deletes the catalog story when membership is removed. Replace the verifier's `exactly 1.json` rule with a schema-history rule that permanently checks the accepted schema-one SHA-256, requires numeric exports to be contiguous from `1` through the current database version, and rejects missing/uncommitted exports. Keep the seven-module and all other Baseline 2 architecture assertions unchanged.

- [ ] **Step 4: Run focused and affected suites**

```bash
./gradlew :catalog:test :storage:room:testDebugUnitTest \
  :storage:room:connectedDebugAndroidTest :app:testDebugUnitTest --stacktrace
./scripts/tests/verify-architecture-baseline-2-test.sh
./scripts/verify-room-schema-stability.sh
```

Expected: **BUILD SUCCESSFUL** and a new accepted schema fingerprint for schema 2.

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/library \
  catalog/src/test/kotlin/app/openstory/catalog/library \
  storage/room/src/main/kotlin/app/openstory/storage/room \
  storage/room/src/androidTest/kotlin/app/openstory/storage/room/library \
  storage/room/schemas app/src/main/kotlin/app/openstory/di/CatalogModule.kt \
  scripts/verify-architecture-baseline-2.sh \
  scripts/tests/verify-architecture-baseline-2-test.sh
git commit -m "library: add metadata-only persistence"
```

### Task 2: Add Library presentation inside the catalog feature

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryUiState.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryViewModel.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryScreen.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/library/LibraryViewModelTest.kt`
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/library/LibraryScreenTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`

**Interfaces:**

```kotlin
enum class LibrarySourceState { SEARCHING, LINKED, REVIEW, NO_MAPPING, FAILED }
enum class LibrarySort { LAST_ACTIVITY, TITLE, DATE_ADDED, LAST_READ }

data class LibraryUiState(
    val items: List<LibraryItemModel> = emptyList(),
    val selectedStatus: LibraryStatus? = null,
    val sort: LibrarySort = LibrarySort.LAST_ACTIVITY,
)
```

- [ ] **Step 1: Write focused RED tests**

Prove a metadata-only entry remains visible as `NO_MAPPING`, filtering/sorting is local, stable keys use `StoryId`, and accessibility semantics expose title, Library status, and source state.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.library.LibraryViewModelTest \
  :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.ui.library.LibraryScreenTest
```

Expected: **FAIL** because the Library presentation package does not exist.

- [ ] **Step 3: Implement feature-owned presentation**

Observe `LibraryRepository`, join the already persisted catalog display projection through a catalog service, and map it to immutable UI state. Replace the `AppRoute.Library` placeholder with the Hilt-owned screen. Do not display a fabricated unread count before chapter aggregation exists.

- [ ] **Step 4: Run focused and affected suites**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  :feature:catalog:connectedDebugAndroidTest :app:testDebugUnitTest \
  :app:connectedDebugAndroidTest --stacktrace
```

- [ ] **Step 5: Commit**

```bash
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/library \
  feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/library \
  app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt
git commit -m "library: add local list and source states"
```

### Task 3: Extend catalog-owned matching for content stories

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/matching/content/ContentStoryFeatures.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/matching/content/ContentMatchPolicy.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/matching/content/ContentStoryMatcher.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/matching/content/ContentMatchResult.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/matching/content/ContentStoryMatcherTest.kt`

**Interfaces:**

```kotlin
enum class ContentMatchDecision { AUTO_LINK, REVIEW, REJECT }

data class ContentMatchResult(
    val score: Double,
    val decision: ContentMatchDecision,
    val explanation: ContentMatchExplanation,
    val policyVersion: Int,
)
```

- [ ] **Step 1: Write focused RED tests**

Cover trusted direct evidence, exact title with conflicting authors, missing optional fields, content-type conflict, threshold boundaries, deterministic ordering, and a bounded explanation containing component scores.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :catalog:test \
  --tests app.openstory.catalog.matching.content.ContentStoryMatcherTest
```

Expected: **FAIL** because content matching policy does not exist.

- [ ] **Step 3: Implement pure matching**

Reuse the catalog title-normalization conventions. Keep weights and thresholds in `ContentMatchPolicy(version = 1)`. Missing evidence contributes no penalty; explicit author/type conflicts prevent automatic linking. The matcher returns a decision only and never calls persistence.

- [ ] **Step 4: Run catalog suite**

```bash
./gradlew :catalog:test detekt --stacktrace
```

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/matching/content \
  catalog/src/test/kotlin/app/openstory/catalog/matching/content
git commit -m "catalog: score content story mappings"
```

### Task 4: Search content plugins in quick and deferred stages

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/content/ContentSource.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/content/PluginContentSource.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/content/ContentSourceRegistry.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/library/ContentMappingSearchService.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/library/ContentMappingSearchServiceTest.kt`
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/PluginOperation.kt`
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/content/ContentProtocol.kt`
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/PluginProtocolValidator.kt`
- Modify: `plugins/api/src/test/kotlin/app/openstory/plugins/api/protocol/PluginProtocolValidatorTest.kt`
- Modify: `app/build.gradle.kts`
- Create: `app/src/main/kotlin/app/openstory/work/LibraryMappingWorker.kt`
- Create: `app/src/test/kotlin/app/openstory/work/LibraryMappingWorkerTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/CatalogModule.kt`

**Interfaces:**

```kotlin
interface ContentSource {
    val pluginId: PluginId
    val version: String
    val allowedNetworkHosts: Set<String>
    suspend fun search(query: String): ContentSourceResult<List<ContentStoryCandidate>>
    suspend fun resolveStoryUrl(url: String): ContentSourceResult<ContentStoryCandidate?>
}

data class ContentSearchPlan(
    val quick: List<PluginId>,
    val deferred: List<PluginId>,
)
```

Add the optional additive protocol operation `CONTENT_RESOLVE_URL("content.resolve_url")`
with `ContentResolveUrlRequestDto(url)` and a nullable `ContentStoryCandidateDto`
response. `PluginContentSource` serializes protocol requests, calls
`PluginRuntime.invoke`, validates responses, and maps wire DTOs into catalog candidates.
Plugins that do not implement URL resolution return the normal bounded unsupported-operation
failure. No host-side plugin interface is introduced.

- [ ] **Step 1: Write focused RED tests**

Prove preferred sources complete in the quick stage without waiting for slow sources, one plugin failure does not cancel peers, queries are deduplicated/capped, only `AUTO_LINK` results are returned for persistence, the new URL operation accepts only HTTPS input and bounded output, and the worker invokes only the deferred stage for an existing Library entry.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :catalog:test \
  --tests app.openstory.catalog.library.ContentMappingSearchServiceTest \
  :plugins:api:test --tests app.openstory.plugins.api.protocol.PluginProtocolValidatorTest \
  :app:testDebugUnitTest --tests app.openstory.work.LibraryMappingWorkerTest
```

Expected: **FAIL** because content adapters, staged search, and the scheduling adapter do not exist.

- [ ] **Step 3: Implement bounded orchestration**

Use `supervisorScope`, per-source deadlines, deterministic plugin preference ordering, and capped title/alias queries. URL resolution parses HTTPS locally and selects only sources whose `allowedNetworkHosts` contain the parsed host before invoking `CONTENT_RESOLVE_URL`. `LibraryService.add` returns after the Room commit; its caller enqueues unique deferred work only after success. The worker delegates to the catalog service and contains no scoring or Room transaction.

- [ ] **Step 4: Run affected suites**

```bash
./gradlew :catalog:test :plugins:api:test :plugins:runtime:testDebugUnitTest \
  :app:testDebugUnitTest detekt --stacktrace
```

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/content \
  catalog/src/main/kotlin/app/openstory/catalog/library/ContentMappingSearchService.kt \
  catalog/src/test/kotlin/app/openstory/catalog/library/ContentMappingSearchServiceTest.kt \
  plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol \
  plugins/api/src/test/kotlin/app/openstory/plugins/api/protocol \
  app/src/main/kotlin/app/openstory/work app/src/test/kotlin/app/openstory/work \
  app/src/main/kotlin/app/openstory/di/CatalogModule.kt app/build.gradle.kts
git commit -m "library: search content sources in stages"
```

### Task 5: Persist protected content mappings

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/library/ContentMappingModels.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/library/ContentMappingRepository.kt`
- Create: `catalog/src/main/kotlin/app/openstory/catalog/library/ContentMappingService.kt`
- Create: `catalog/src/test/kotlin/app/openstory/catalog/library/ContentMappingServiceTest.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/library/ContentMappingEntity.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/library/LibraryDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/library/RoomLibraryRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/library/ContentMappingMigrationTest.kt`

**Interfaces:**

```kotlin
enum class MappingOrigin { AUTOMATED, USER_APPROVED, USER_URL }

data class ContentMapping(
    val storyId: StoryId,
    val pluginId: PluginId,
    val sourceStoryId: String,
    val origin: MappingOrigin,
    val policyVersion: Int?,
    val updatedAtEpochMillis: Long,
)

interface ContentMappingRepository {
    fun observe(storyId: StoryId): Flow<List<ContentMapping>>
    suspend fun storeAutomated(mapping: ContentMapping): MappingWriteResult
    suspend fun approve(mapping: ContentMapping)
    suspend fun reject(storyId: StoryId, pluginId: PluginId, sourceStoryId: String)
}
```

- [ ] **Step 1: Write focused RED tests**

Prove automated writes cannot overwrite `USER_APPROVED` or `USER_URL`, repeated approvals are idempotent, rejected candidates remain blocked for the same source version, and schema `2 -> 3` preserves Library membership.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :catalog:test \
  --tests app.openstory.catalog.library.ContentMappingServiceTest \
  :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.library.ContentMappingMigrationTest
```

- [ ] **Step 3: Implement storage-owned conflict rules**

The catalog service decides which semantic write is requested. The Room adapter performs the atomic compare-and-write transaction and returns whether an automated result was stored or ignored because a protected mapping already exists.

- [ ] **Step 4: Run focused and affected suites**

```bash
./gradlew :catalog:test :storage:room:testDebugUnitTest \
  :storage:room:connectedDebugAndroidTest --stacktrace
./scripts/verify-room-schema-stability.sh
```

- [ ] **Step 5: Commit**

```bash
git add catalog/src/main/kotlin/app/openstory/catalog/library \
  catalog/src/test/kotlin/app/openstory/catalog/library \
  storage/room/src/main/kotlin/app/openstory/storage/room \
  storage/room/src/androidTest/kotlin/app/openstory/storage/room/library \
  storage/room/schemas
git commit -m "library: protect approved content mappings"
```

### Task 6: Add mapping review, manual search, and URL resolution UI

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingUiState.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingViewModel.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/ManualSourceSearchScreen.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/mapping/MappingViewModelTest.kt`
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/mapping/MappingSheetTest.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt`

**Interfaces:**

The ViewModel consumes `ContentMappingSearchService` and `ContentMappingService`. URL resolution parses HTTPS locally, filters sources by manifest-declared allowed hosts before invocation, then calls only matching `ContentSource.resolveStoryUrl` adapters.

- [ ] **Step 1: Write focused RED tests**

Prove explanations show evidence instead of a misleading percentage, approval persists immediately, unlink/remap preserves unrelated catalog data, and a pasted URL reaches only plugins whose declared hosts match it.

- [ ] **Step 2: Verify RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests app.openstory.catalog.ui.mapping.MappingViewModelTest \
  :feature:catalog:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.ui.mapping.MappingSheetTest
```

- [ ] **Step 3: Implement feature-owned review UI**

Expose strong/possible/weak labels plus title, author, type, year, plugin, and language evidence. Approve/reject/manual-search actions call catalog services; Compose owns no plugin DTO parsing or persistence rules.

- [ ] **Step 4: Run affected suites**

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  :feature:catalog:connectedDebugAndroidTest :app:testDebugUnitTest \
  :app:connectedDebugAndroidTest lintDebug --stacktrace
```

- [ ] **Step 5: Commit**

```bash
git add feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping \
  feature/catalog/src/test/kotlin/app/openstory/catalog/ui/mapping \
  feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/mapping \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt
git commit -m "library: add content mapping review"
```

## Wave Checkpoint

Do not begin Wave 07 until every item below is demonstrated on a clean checkout:

- [ ] The production graph remains exactly the seven Architecture Baseline 2 modules.
- [ ] Add-to-Library returns after one local Room transaction and before plugin work.
- [ ] Metadata-only stories remain visible and usable.
- [ ] Schema migrations `1 -> 2 -> 3` preserve accepted Baseline 2 data.
- [ ] High-confidence mappings auto-link; ambiguous mappings require review.
- [ ] User-approved and URL-imported mappings survive repeated automated searches.
- [ ] A failed or slow plugin does not cancel other content searches.
- [ ] Pasted URLs reach only matching manifest-declared hosts.

## Full Verification

```bash
./scripts/verify.sh
ANDROID_SERIAL_API_26="$ANDROID_SERIAL_API_26" \
ANDROID_SERIAL_API_37="$ANDROID_SERIAL_API_37" \
  ./scripts/checkpoints/architecture-baseline-2.sh
```

Expected: **PASS** with no ignored failing tests, unresolved lint errors, architecture drift, or Room schema drift.

## Review Packet

- Commit range for Wave 06.
- Focused RED/GREEN output for every task.
- Room migration evidence and final schema fingerprint.
- Full local and API 26/API 37 verification output.
- Deep ownership review confirming catalog, storage, runtime, feature, and app responsibilities remain separated.

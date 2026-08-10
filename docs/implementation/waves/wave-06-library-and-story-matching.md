<!-- DOCUMENT LIFECYCLE: ACTIVE / TASK 01 VERIFIED / TASK 02 NEXT -->

# Wave 06 - Library and Story Matching Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`. Execute tasks in order with focused RED, smallest GREEN, affected suites, review, and commit.

**Goal:** Add an immediate metadata-only Library and protected readable-source mappings without expanding catalog ownership.

**Architecture:** Follows `../../superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`. Introduces `:library` as the owner of membership and content mappings. Catalog remains discovery metadata; Room remains the persistence adapter; Library UI remains in `:feature:catalog` because it shares the canonical story flow.

**Tech Stack:** Kotlin 2.4.10, Room 2.8.4, coroutines 1.11.0, Compose BOM 2026.06.00, Hilt 2.60.1, WorkManager 2.11.2, JavaScriptEngine 1.1.0.

## Global Constraints

- Entry module graph: the seven accepted Architecture Baseline 2 modules.
- Exit module graph: entry graph plus `:library`; no other production module.
- Baseline Room schema 1 is immutable; this wave exports contiguous schemas 2 and 3.
- `:library` may depend on `:core:common`, `:catalog`, `:plugins:api`, and the public `:plugins:runtime` facade.
- Library membership commits before plugin work. Matching is pure Library behavior; Room only persists decisions.
- JavaScript protocol operations are the only plugin execution path.
- No chapter synchronization, Reader, downloads, periodic scheduling, authentication, or notifications.

## Continuity

- Consumes from Baseline 2: `StoryId`, catalog story projections, `PluginRuntime`, content protocol DTOs, and Room schema 1.
- Produces for Wave 07: `LibraryRepository`, `ContentMappingRepository`, protected mappings, and enabled content-source access.

---

### Task 1: Introduce `:library` and metadata-only membership

**Files:**
- Create: `library/build.gradle.kts`
- Create: `library/src/main/kotlin/app/openstory/library/LibraryModels.kt`
- Create: `library/src/main/kotlin/app/openstory/library/LibraryRepository.kt`
- Create: `library/src/main/kotlin/app/openstory/library/LibraryService.kt`
- Test: `library/src/test/kotlin/app/openstory/library/LibraryServiceTest.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/library/LibraryEntity.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/library/LibraryDao.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/library/RoomLibraryRepository.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/library/LibraryMigrationTest.kt`
- Modify: `settings.gradle.kts`, `config/architecture/module-boundaries.json`, `storage/room/build.gradle.kts`, `app/build.gradle.kts`
- Create: `app/src/main/kotlin/app/openstory/di/LibraryModule.kt`
- Create: `scripts/verify-current-architecture.sh`, `scripts/tests/verify-current-architecture-test.sh`
- Modify: `scripts/verify.sh`

**Interfaces:** `LibraryEntry(storyId, status, addedAt, updatedAt)`, `LibraryStatus`, and `LibraryRepository.observe/add/remove/changeStatus`.

- [ ] Write RED tests proving idempotent local add, status preservation, no runtime dependency, schema `1 -> 2`, unchanged schema-one hash, and exact eight-module policy. Add a current-architecture contract that derives the exact module set/edges from `module-boundaries.json` instead of freezing one wave's graph.
- [ ] Run `./gradlew :library:test :storage:room:connectedDebugAndroidTest --stacktrace` and `./scripts/tests/verify-current-architecture-test.sh`; expect failure because the module/schema/current verifier do not exist.
- [ ] Implement the contracts, Room transaction, Hilt binding, module policy, and schema-history rule. Keep `verify-architecture-baseline-2.sh` as immutable R6 evidence; switch `verify.sh` to `verify-current-architecture.sh` for all post-baseline waves.
- [ ] Run `./gradlew :library:test :storage:room:testDebugUnitTest :storage:room:connectedDebugAndroidTest :app:testDebugUnitTest detekt --stacktrace` and `./scripts/verify.sh`; expect PASS.
- [ ] Commit `library: add metadata-only membership`.

### Task 2: Present Library state in `:feature:catalog`

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryUiState.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryViewModel.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryScreen.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/library/LibraryViewModelTest.kt`
- Test: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/library/LibraryScreenTest.kt`
- Modify: `feature/catalog/build.gradle.kts`, `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`

**Interfaces:** `LibraryUiState(items, selectedStatus, sort)` and source states `SEARCHING`, `LINKED`, `REVIEW`, `NO_MAPPING`, `FAILED`.

- [ ] Write RED tests for metadata-only visibility, stable `StoryId` keys, local filters/sort, and accessibility semantics.
- [ ] Run focused feature unit/instrumentation tests; expect missing Library presentation.
- [ ] Implement ViewModel mapping over Library plus a narrow catalog display projection and replace the Library placeholder route.
- [ ] Run `./gradlew :feature:catalog:testDebugUnitTest :feature:catalog:connectedDebugAndroidTest :app:connectedDebugAndroidTest --stacktrace`.
- [ ] Commit `library: add local list presentation`.

### Task 3: Implement explainable content-story matching

**Files:**
- Create: `library/src/main/kotlin/app/openstory/library/matching/ContentStoryFeatures.kt`
- Create: `library/src/main/kotlin/app/openstory/library/matching/ContentMatchPolicy.kt`
- Create: `library/src/main/kotlin/app/openstory/library/matching/ContentStoryMatcher.kt`
- Test: `library/src/test/kotlin/app/openstory/library/matching/ContentStoryMatcherTest.kt`

**Interfaces:** `ContentMatchDecision { AUTO_LINK, REVIEW, REJECT }` and `ContentMatchResult(score, decision, explanation, policyVersion)`.

- [ ] Write RED tests for direct evidence, conflicting authors/types, missing optional fields, thresholds, and deterministic explanations.
- [ ] Run the focused `:library:test`; expect missing matcher.
- [ ] Implement versioned pure scoring without persistence or runtime calls.
- [ ] Run `./gradlew :library:test detekt --stacktrace`.
- [ ] Commit `library: score content story mappings`.

### Task 4: Search content plugins in quick and deferred stages

**Files:**
- Create: `library/src/main/kotlin/app/openstory/library/content/ContentSource.kt`
- Create: `library/src/main/kotlin/app/openstory/library/content/PluginContentSource.kt`
- Create: `library/src/main/kotlin/app/openstory/library/content/ContentSourceRegistry.kt`
- Create: `library/src/main/kotlin/app/openstory/library/mapping/ContentMappingSearchService.kt`
- Test: `library/src/test/kotlin/app/openstory/library/mapping/ContentMappingSearchServiceTest.kt`
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/PluginOperation.kt`
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/content/ContentProtocol.kt`
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/PluginProtocolValidator.kt`
- Test: `plugins/api/src/test/kotlin/app/openstory/plugins/api/protocol/PluginProtocolValidatorTest.kt`
- Create: `app/src/main/kotlin/app/openstory/work/LibraryMappingWorker.kt`
- Test: `app/src/test/kotlin/app/openstory/work/LibraryMappingWorkerTest.kt`

**Interfaces:** content search uses `CONTENT_SEARCH`; optional HTTPS URL resolution adds `CONTENT_RESOLVE_URL`. Sources expose plugin ID/version/allowed hosts and return bounded Library candidates.

- [ ] Write RED tests for preferred-source deadlines, peer failure isolation, query caps, HTTPS/host filtering, unsupported URL operations, and worker delegation.
- [ ] Run focused Library, protocol, and app tests; expect missing adapters.
- [ ] Implement serialized runtime calls, `supervisorScope`, deterministic planning, and a thin unique WorkManager adapter invoked only after membership commit.
- [ ] Run `./gradlew :library:test :plugins:api:test :plugins:runtime:testDebugUnitTest :app:testDebugUnitTest detekt --stacktrace`.
- [ ] Commit `library: search content sources in stages`.

### Task 5: Persist protected mappings

**Files:**
- Create: `library/src/main/kotlin/app/openstory/library/mapping/ContentMappingModels.kt`
- Create: `library/src/main/kotlin/app/openstory/library/mapping/ContentMappingRepository.kt`
- Create: `library/src/main/kotlin/app/openstory/library/mapping/ContentMappingService.kt`
- Test: `library/src/test/kotlin/app/openstory/library/mapping/ContentMappingServiceTest.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/library/ContentMappingEntity.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/library/LibraryDao.kt`, `RoomLibraryRepository.kt`, `OpenStoryDatabase.kt`, `RoomMigrations.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/library/ContentMappingMigrationTest.kt`

**Interfaces:** `ContentMapping(storyId, pluginId, sourceStoryId, origin, policyVersion, updatedAt)` with origins `AUTOMATED`, `USER_APPROVED`, `USER_URL`.

- [ ] Write RED tests proving automation cannot overwrite protected mappings, rejection is version-aware, approvals are idempotent, and schema `2 -> 3` preserves membership.
- [ ] Run focused Library and Room tests; expect missing mapping persistence.
- [ ] Implement semantic decisions in `:library` and atomic compare/write in Room.
- [ ] Run `./gradlew :library:test :storage:room:connectedDebugAndroidTest --stacktrace` plus Room fingerprint verification.
- [ ] Commit `library: protect approved mappings`.

### Task 6: Add mapping review and URL import UI

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingUiState.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingViewModel.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/mapping/MappingViewModelTest.kt`
- Test: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/mapping/MappingSheetTest.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt`

- [ ] Write RED tests for evidence labels, approval/rejection, manual search, host-filtered URL resolution, and immediate state updates.
- [ ] Run focused feature tests; expect missing UI.
- [ ] Implement feature-owned UI calling only Library services.
- [ ] Run affected feature/app tests and lint.
- [ ] Commit `library: add mapping review`.

## Wave Checkpoint

- [ ] Exact exit graph and dependency policy pass.
- [ ] Schemas 1-3 are contiguous and earlier exports are stable.
- [ ] Metadata-only add returns before plugin work.
- [ ] Protected mappings survive automation; ambiguous candidates require review.
- [ ] URL input reaches only declared hosts.
- [ ] `./scripts/verify.sh` and API 26/API 37 checkpoint pass.
- [ ] Deep ownership review confirms `:catalog`, `:library`, Room, runtime, feature, and app responsibilities remain separate.

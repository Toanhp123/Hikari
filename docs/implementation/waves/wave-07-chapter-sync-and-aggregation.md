<!-- DOCUMENT LIFECYCLE: PLANNED / REBASELINED FOR POST-BASELINE GRAPH -->

# Wave 07 - Chapter Sync and Aggregation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`. Execute tasks in order with TDD, review, and one commit per task.

**Goal:** Synchronize readable releases and group them deterministically into user-correctable canonical chapters.

**Architecture:** Follows `../../superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`. Introduces `:chapters`; no generic scheduling module. Chapter policy is pure capability behavior, Room owns transactions, runtime owns JavaScript execution, and `:app` supplies only Android workers.

**Tech Stack:** Kotlin/coroutines, serialized content protocol, Room, WorkManager adapter, Compose/Hilt.

## Global Constraints

- Entry module graph: Wave 06 exit graph, including `:library`.
- Exit module graph: entry graph plus `:chapters`; no other production module.
- Introduces `:chapters` in Task 1.
- Consumes from Wave 06: protected `ContentMapping`, enabled content sources, and metadata-only Library entries.
- Produces for Wave 08: `CanonicalChapter`, `ChapterRelease`, `ChapterRepository`, synchronization reports, and user aggregation overrides.
- Room schema 3 is the entry; this wave exports schema 4.
- Missing releases become tombstones before deletion. User overrides always outrank automation.
- No Reader document rendering/progress, cache/downloads, periodic scheduling, auth, or notifications.

---

### Task 1: Introduce `:chapters` and normalize release labels

**Files:**
- Create: `chapters/build.gradle.kts`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/model/ChapterModels.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/normalization/ChapterLabelParser.kt`
- Test: `chapters/src/test/kotlin/app/openstory/chapters/normalization/ChapterLabelParserTest.kt`
- Modify: `settings.gradle.kts`, `config/architecture/module-boundaries.json`, `app/build.gradle.kts`

**Interfaces:** `ChapterKind`, `ParsedChapterLabel(kind, volume, chapter, part, normalizedTitle)`, stable `CanonicalChapterId`, and `ChapterReleaseId` in `:core:common` only when cross-capability identity requires them.

- [ ] Write RED parser tests for decimals, volume/chapter/part, prologue/epilogue/side-story/extra/special/unknown, localized prefixes, and malformed labels.
- [ ] Write the architecture RED asserting the exact Wave 07 exit graph and allowed dependencies.
- [ ] Implement the module, models, deterministic parser, and policy update.
- [ ] Run `./gradlew :chapters:test :verifyArchitecture detekt --stacktrace`.
- [ ] Commit `chapters: normalize release labels`.

### Task 2: Match and aggregate releases deterministically

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/aggregation/ChapterMatchPolicy.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/aggregation/ChapterMatchScorer.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/aggregation/ChapterAggregationEngine.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/aggregation/AggregationPlan.kt`
- Test: `chapters/src/test/kotlin/app/openstory/chapters/aggregation/ChapterAggregationEngineTest.kt`

**Interfaces:** engine input is existing canonical chapters, source releases, and protected overrides; output is a pure `AggregationPlan` containing creates, links, unlinks, tombstones, and review candidates.

- [ ] Write RED tests for explicit-number conflicts, equivalent releases, stable input-order independence, medium-confidence separation, and override precedence.
- [ ] Run `./gradlew :chapters:test --tests app.openstory.chapters.aggregation.ChapterAggregationEngineTest --stacktrace`; expect missing aggregation policy.
- [ ] Implement versioned scoring and a pure mutation plan with no DAO calls.
- [ ] Run `./gradlew :chapters:test detekt --stacktrace`.
- [ ] Commit `chapters: aggregate source releases`.

### Task 3: Adapt content chapter operations through the runtime facade

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/source/ChapterSource.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/source/PluginChapterSource.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/source/ChapterSourceRegistry.kt`
- Test: `chapters/src/test/kotlin/app/openstory/chapters/source/PluginChapterSourceTest.kt`
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/content/ContentProtocol.kt`
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/protocol/PluginProtocolValidator.kt`
- Test: `plugins/api/src/test/kotlin/app/openstory/plugins/api/protocol/PluginProtocolValidatorTest.kt`

**Interfaces:** Extend `ContentChaptersRequestDto` with defaulted `mode: ContentChapterListModeDto = FULL`, optional bounded `checkpoint`, and `nextToken`. `RECENT`, `FULL`, and `INCREMENTAL` all use `CONTENT_CHAPTERS`; output maps bounded protocol releases into chapter-owned candidates. Defaults preserve protocol-major-1 compatibility. One plugin failure never cancels peers.

- [ ] Write RED tests for recent/full/incremental serialization, checkpoint/token bounds, default FULL compatibility, invalid output, cancellation, and failure isolation.
- [ ] Run `./gradlew :chapters:test --tests app.openstory.chapters.source.PluginChapterSourceTest :plugins:api:test --stacktrace`; expect missing source/protocol support.
- [ ] Implement only against `PluginRuntime.invoke` and protocol DTOs.
- [ ] Run `./gradlew :chapters:test :plugins:api:test :plugins:runtime:testDebugUnitTest --stacktrace`.
- [ ] Commit `chapters: adapt content release sources`.

### Task 4: Persist chapter graphs and overrides transactionally

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/repository/ChapterRepository.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/repository/ChapterMutation.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterEntities.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterDao.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomChapterRepository.kt`
- Modify: `storage/room/build.gradle.kts`, `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`, `RoomMigrations.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/ChapterMigrationTest.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/RoomChapterRepositoryTest.kt`

- [ ] Write RED tests for schema `3 -> 4`, atomic plan commits, rollback, stable ordering, tombstones, and protected overrides.
- [ ] Run `./gradlew :storage:room:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.chapters.ChapterMigrationTest --stacktrace`; expect missing schema/repository.
- [ ] Implement internal entities/DAOs and one transaction per semantic aggregation commit.
- [ ] Run `./gradlew :chapters:test :storage:room:connectedDebugAndroidTest --stacktrace` and `./scripts/verify-room-schema-stability.sh`.
- [ ] Commit `chapters: persist canonical chapter graphs`.

### Task 5: Implement recent, full, and incremental synchronization

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncService.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncModels.kt`
- Test: `chapters/src/test/kotlin/app/openstory/chapters/sync/ChapterSyncServiceTest.kt`
- Create: `app/src/main/kotlin/app/openstory/work/InitialChapterSyncWorker.kt`
- Test: `app/src/test/kotlin/app/openstory/work/InitialChapterSyncWorkerTest.kt`

- [ ] Write RED tests proving recent results commit first, full sync resumes from cursor/fingerprint, failed commits do not advance state, and worker code only delegates IDs/mode/retry.
- [ ] Run `./gradlew :chapters:test --tests app.openstory.chapters.sync.ChapterSyncServiceTest :app:testDebugUnitTest --tests app.openstory.work.InitialChapterSyncWorkerTest --stacktrace`; expect missing orchestration.
- [ ] Implement capability-owned synchronization using protected Library mappings and `supervisorScope`; enqueue initial work only after mapping approval.
- [ ] Run `./gradlew :chapters:test :app:testDebugUnitTest :storage:room:connectedDebugAndroidTest --stacktrace`.
- [ ] Commit `chapters: synchronize mapped sources`.

### Task 6: Add canonical chapter-list presentation

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterListViewModel.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterReleaseRow.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterFiltersSheet.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/chapters/ChapterListViewModelTest.kt`
- Test: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/chapters/ChapterListTest.kt`
- Modify: `feature/catalog/build.gradle.kts`, `app/build.gradle.kts`, `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt`

- [ ] Write RED tests for canonical unread count, release expansion, filters, tombstone visibility, correction commands, and accessibility.
- [ ] Run `./gradlew :feature:catalog:testDebugUnitTest --tests app.openstory.catalog.ui.chapters.ChapterListViewModelTest --stacktrace`; expect missing chapter UI.
- [ ] Implement UI over chapter-owned projections and services; no aggregation in ViewModel/Compose.
- [ ] Run `./gradlew :feature:catalog:testDebugUnitTest :feature:catalog:connectedDebugAndroidTest :app:connectedDebugAndroidTest lintDebug --stacktrace`.
- [ ] Commit `chapters: add canonical chapter list`.

## Wave Checkpoint

- [ ] Exact exit graph and schema 4 pass.
- [ ] Recent results appear before full history completes.
- [ ] Equivalent releases group deterministically; ambiguous releases remain separate.
- [ ] User corrections survive subsequent synchronization.
- [ ] Plugin/commit failures do not corrupt cursors or other sources.
- [ ] `./scripts/verify.sh` and device checkpoint pass.
- [ ] Deep ownership review confirms chapter policy, runtime execution, Room transactions, UI, and workers remain separated.

<!-- DOCUMENT LIFECYCLE: PLANNED / REBASELINED FOR POST-BASELINE GRAPH -->

# Wave 08 - Reader and Reading Progress Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`; use TDD and commit each task.

**Goal:** Load sanitized chapter documents, choose releases deterministically, render an accessible Reader, and restore canonical plus exact-release progress.

**Architecture:** Follows `../../superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`. Introduces `:reader` and `:feature:reader`. Reader owns document/progress policy; runtime fetches JavaScript content; Room persists progress; app owns navigation.

## Global Constraints

- Entry module graph: Wave 07 exit graph, including `:library` and `:chapters`.
- Exit module graph: entry graph plus `:reader` and `:feature:reader`.
- Introduces `:reader` and `:feature:reader` in Task 1.
- Consumes from Wave 07: canonical chapters, releases, filters, and `ChapterRepository`.
- Produces for Wave 09: `ReaderDocumentRepository`, `ReaderDocumentStore` port, reading progress, and exact release-position contracts.
- Room schema 4 enters; schema 5 adds progress.
- Remote content becomes a bounded sanitized document before UI rendering.
- No cache/download implementation, periodic work, auth, or notifications.

### Task 1: Introduce Reader modules and document validation

**Files:**
- Create: `reader/build.gradle.kts`, `reader/src/main/kotlin/app/openstory/reader/document/ReaderDocument.kt`, `ReaderDocumentSanitizer.kt`
- Test: `reader/src/test/kotlin/app/openstory/reader/document/ReaderDocumentSanitizerTest.kt`
- Create: `feature/reader/build.gradle.kts`
- Modify: `settings.gradle.kts`, `config/architecture/module-boundaries.json`, `app/build.gradle.kts`

- [ ] Write RED tests for empty/oversized documents, unsupported blocks, control characters, bounded headings/paragraphs, and exact exit graph.
- [ ] Implement `ReaderDocument`, sealed `ReaderBlock`, `ReaderDocumentSanitizer`, both modules, and dependency policy.
- [ ] Run `./gradlew :reader:test :verifyArchitecture detekt --stacktrace`.
- [ ] Commit `reader: validate structured documents`.

### Task 2: Select the default release deterministically

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/selection/ReleaseSelectionPolicy.kt`, `ReleaseSelector.kt`, `ReleaseSelectionResult.kt`
- Test: `reader/src/test/kotlin/app/openstory/reader/selection/ReleaseSelectorTest.kt`

**Interfaces:** selection considers explicit choice, previous source/group, language order, health/completeness, update time, then stable plugin/release IDs.

- [ ] Write RED tests for every precedence rule and input-order stability.
- [ ] Implement a pure explained selection result.
- [ ] Run `./gradlew :reader:test --stacktrace`.
- [ ] Commit `reader: select chapter releases deterministically`.

### Task 3: Load content through a replaceable Reader repository

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentStore.kt`, `ReaderDocumentSource.kt`, `PluginReaderDocumentSource.kt`, `ReaderDocumentRepository.kt`
- Test: `reader/src/test/kotlin/app/openstory/reader/content/ReaderDocumentRepositoryTest.kt`, `PluginReaderDocumentSourceTest.kt`
- Create: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`

**Interfaces:** `ReaderDocumentStore` exposes `read(releaseId, fingerprint)`, `write(releaseId, fingerprint, document)`, and `quarantine(releaseId, fingerprint)`. `ReaderDocumentRepository.load` tries the store, selected source, then alternate releases; every valid sanitized network result is written through the store. Wave 08 binds a no-op store, and Wave 09 supplies the persistent implementation.

- [ ] Write RED tests for store-first lookup, sanitized network write-through, source fallback, invalid content rejection without write, cancellation, quarantine, and alternate release errors.
- [ ] Implement the network-only store miss implementation in app composition; Wave 09 will provide the file-backed decorator.
- [ ] Run `./gradlew :reader:test :plugins:api:test :plugins:runtime:testDebugUnitTest --stacktrace`.
- [ ] Commit `reader: load sanitized chapter content`.

### Task 4: Persist canonical and exact reading progress

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/progress/ReadingProgress.kt`, `ReadingProgressRepository.kt`, `ReadingProgressService.kt`
- Test: `reader/src/test/kotlin/app/openstory/reader/progress/ReadingProgressServiceTest.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/reader/ReadingProgressEntity.kt`, `ReadingProgressDao.kt`, `RoomReadingProgressRepository.kt`
- Modify: `storage/room/build.gradle.kts`, `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`, `RoomMigrations.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/reader/ReadingProgressMigrationTest.kt`, `RoomReadingProgressRepositoryTest.kt`

**Interfaces:** `ReadingProgress` stores canonical chapter, exact release, content fingerprint, block/paragraph identity, offset/fraction, and completion timestamp.

- [ ] Write RED tests for debounced writes, monotonic completion, release switching without unread regression, schema `4 -> 5`, rollback, and restoration after process recreation.
- [ ] Implement capability decisions and Room-owned atomic persistence.
- [ ] Run `./gradlew :reader:test :storage:room:connectedDebugAndroidTest --stacktrace` and `./scripts/verify-room-schema-stability.sh`.
- [ ] Commit `reader: persist exact reading progress`.

### Task 5: Add process-restorable Reader state and navigation

**Files:**
- Create: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderUiState.kt`, `ReaderViewModel.kt`
- Test: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppRoute.kt`, `AppNavHost.kt`, `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`

- [ ] Write RED tests for stable-ID saved state, initial load, retry, previous/next canonical chapter, release switching, and progress flush on lifecycle stop.
- [ ] Implement ViewModel orchestration with `viewModelScope`; routes carry IDs only.
- [ ] Run `./gradlew :feature:reader:testDebugUnitTest :app:testDebugUnitTest --stacktrace`.
- [ ] Commit `reader: add restorable reader state`.

### Task 6: Build accessible Compose Reader UI

**Files:**
- Create: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderScreen.kt`, `ReaderContent.kt`, `ReaderControls.kt`, `ReleaseSwitcher.kt`
- Test: `feature/reader/src/androidTest/kotlin/app/openstory/reader/ui/ReaderScreenTest.kt`

- [ ] Write RED tests for structured text rendering, semantics, font controls, system bars, orientation/process restoration, source switcher, and failure alternatives.
- [ ] Implement feature-owned Compose rendering without WebView/live HTML.
- [ ] Run `./gradlew :feature:reader:testDebugUnitTest :feature:reader:connectedDebugAndroidTest :app:connectedDebugAndroidTest lintDebug detekt --stacktrace`.
- [ ] Commit `reader: build accessible compose reader`.

## Wave Checkpoint

- [ ] Exact exit graph and schema 5 pass.
- [ ] Documents are sanitized before rendering.
- [ ] Release selection and fallback are deterministic and explained.
- [ ] Progress restores canonical and exact release position.
- [ ] `./scripts/verify.sh` and device checkpoint pass.
- [ ] Deep ownership review confirms Reader policy, UI, runtime, Room, and navigation separation.

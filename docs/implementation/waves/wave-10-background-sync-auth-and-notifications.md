<!-- DOCUMENT LIFECYCLE: PLANNED / CLEAN-ARCHITECTURE REBASELINE -->

# Wave 10 Background Work, Authentication, and Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development`
> (recommended) or `superpowers:executing-plans`. Execute one task at a time, preserve RED/GREEN
> evidence, review the diff, and commit before continuing.

**Goal:** Introduce typed local policy, clean Reader/cache policy boundaries, bounded background
work, plugin-scoped authentication, transactional chapter-change evidence, recoverable local
notifications, and Settings/status presentation.

**Architecture:** Consuming capabilities declare narrow policy ports; `:app` adapts `:settings` to
those ports. Existing mapping, chapter, download, cache, and canonical engines remain authoritative.
Room records chapter-change facts in the same transaction as chapter graph mutation, while `:app`
owns WorkManager, WebView, permission, notification, deep-link, and navigation adapters.

**Tech Stack:** Kotlin, coroutines/Flow, Preferences DataStore, WorkManager, Hilt, Android WebView,
Android Keystore AES-GCM, Room, Compose, Navigation 3, and Android notification APIs.

**Spec:** `../../superpowers/specs/2026-08-24-wave-10-clean-background-auth-notifications-design.md`

## Entry Baseline

- Production graph: 14 modules.
- Room schema: 10.
- Canonical queue leases and catalog-change outbox belong to `MIGRATION_9_10`.
- Existing one-time work names:
  - `library-mapping:<storyId>`
  - `initial-chapter-sync:<storyId>`
  - `chapter-download:<releaseId>`
  - `canonical-engine-drain`
  - `canonical-engine-safety`
- Reader font scale currently lives in `SavedStateHandle` and must migrate to typed policy.
- Reader release selection already accepts `languageOrder`, but current presentation does not supply it.
- Automatic Reader cache currently uses a 256 MiB constructor default and must migrate to a port.
- Managed credential lookup currently receives only plugin ID and hostname and must migrate to a full
  validated request target.
- Chapter graph commit is already transactional; Wave 10 extends that transaction with raw
  chapter-change evidence.

## Exit Baseline

- Production graph: exactly 16 modules by adding only `:settings` and `:feature:settings`.
- Room schema: exactly 11.
- Existing unique work names remain byte-for-byte stable.
- New stable work names:
  - `library-chapter-periodic`
  - `library-chapter-continuation`
  - `chapter-notification-drain`
- Reader/cache policy consumers use capability-owned ports backed by `:settings` adapters.
- Authentication sessions are plugin/HTTPS host/path/cookie/policy scoped and encrypted in
  no-backup storage.
- Chapter-change evidence and chapter graph mutations commit atomically.
- Permission denial becomes `IN_APP_ONLY`, never a retry loop or historical notification burst.
- Settings is a utility route, never a top-level destination.

## Global Invariants

1. Do not introduce a generic `:sync` module.
2. Do not add `:reader -> :settings`, `:downloads -> :settings`, or feature-to-platform dependencies.
3. Do not duplicate capability logic in workers, Compose, Room entities, or WebView callbacks.
4. Do not serialize complete candidate lists or domain objects into WorkManager `Data`.
5. Do not persist credentials in DataStore, Room, backup-enabled files, logs, diagnostics, or state
   restoration bundles.
6. Do not classify user-facing notifications inside Room transactions; persist raw facts there.
7. Do not leave old production policy paths active after the new port is connected.
8. Every migration test starts from an exported schema and verifies data plus foreign keys.
9. Every task ends with focused tests, the owning module suite, diff review, and one commit.

---

## Phase 0 - Freeze Entry Contracts And Guardrails

### Task 1: Freeze current WorkManager names and input keys

**Files:**
- Create: `app/src/test/kotlin/app/openstory/work/Wave10EntryWorkContractTest.kt`
- Read-only production references: current mapping, chapter, download, canonical, retry, and
  post-merge work adapters.

**Produces:** A failing-safe characterization test that prevents accidental renaming during later
centralization.

```kotlin
@Test
fun existingUniqueNamesRemainStable() {
    assertEquals("library-mapping:story:one", currentLibraryMappingName(StoryId("story:one")))
    assertEquals("initial-chapter-sync:story:one", currentInitialChapterSyncName(StoryId("story:one")))
    assertEquals("chapter-download:release:one", currentDownloadName(ChapterReleaseId("release:one")))
}
```

- [ ] Add assertions for all five frozen names plus canonical retry and post-merge derived name
  formats.
- [ ] Assert current worker input keys and invalid-ID failure behavior.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*Wave10EntryWorkContractTest*' --stacktrace`.
- [ ] Expected GREEN: characterization passes against current code without production changes.
- [ ] Commit `test: freeze wave 10 work contracts`.

### Task 2: Freeze Reader and cache legacy behavior before migration

**Files:**
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelTest.kt`
- Create: `downloads/src/test/kotlin/app/openstory/downloads/reader/LegacyCacheQuotaContractTest.kt`

**Produces:** Evidence of the behavior that must be preserved while its ownership changes.

- [ ] Add a Reader test proving current font bounds and language-neutral fallback selection.
- [ ] Add a cache test proving the current default quota is 256 MiB.
- [ ] Name tests with `legacyContract` so they are removed or rewritten in Tasks 9 and 11 rather than
  retained as approval for the old ownership.
- [ ] Run the two focused test classes; expected GREEN against the entry baseline.
- [ ] Commit `test: freeze reader and cache migration baseline`.

### Task 3: Add Wave 10 repository policy guardrails

**Files:**
- Modify: `config/architecture/module-boundaries.json`
- Modify: `scripts/tests/current-architecture-contract-test.sh`
- Create: `scripts/tests/wave-10-boundary-contract-test.sh`

**Produces:** Static rules for the planned 16-module graph and forbidden dependency directions.

- [ ] Write RED static assertions for absent `:settings` and `:feature:settings` entries.
- [ ] Add expected dependency allowlists:
  - `:settings -> :core:common`
  - `:feature:settings -> :core:common + :core:designsystem + :settings + :downloads`
  - `:app` consumes both new modules.
- [ ] Add forbidden production imports for WorkManager, Room, WebView, plugin runtime internals, and
  Android notification APIs inside `:feature:settings`.
- [ ] Do not make the static test GREEN until Task 4 creates the modules.
- [ ] Commit the RED guardrail with Task 4 rather than leaving the repository checkpoint broken.

**Phase 0 checkpoint:** Entry behavior is characterized; no production capability has changed.

---

## Phase 1 - Typed Settings Foundation

### Task 4: Create `:settings` and `:feature:settings` module boundaries

**Files:**
- Create: `settings/build.gradle.kts`
- Create: `feature/settings/build.gradle.kts`
- Modify: `settings.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `app/build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `gradle/verification-metadata.xml`

**Consumes:** Phase 0 architecture guardrail.

**Produces:** Two compiling Android library modules with no production source dependency violation.

- [ ] Add the reviewed Preferences DataStore dependency alias.
- [ ] Configure `:settings` as a non-Compose Android library depending only on `:core:common`.
- [ ] Configure `:feature:settings` with Compose, Hilt, Roborazzi, and the exact approved dependencies.
- [ ] Add both modules to `:app` and make the Phase 0 architecture test GREEN.
- [ ] Run `./gradlew :settings:assembleDebug :feature:settings:assembleDebug verifyArchitecture`.
- [ ] Expected GREEN: 16 production modules and unchanged single `:benchmark` android-test module.
- [ ] Commit `build: add settings module boundaries`.

### Task 5: Define normalized immutable settings models

**Files:**
- Create: `settings/src/main/kotlin/app/openstory/settings/AppSettings.kt`
- Create: `settings/src/main/kotlin/app/openstory/settings/SettingsDefaults.kt`
- Test: `settings/src/test/kotlin/app/openstory/settings/AppSettingsTest.kt`

**Produces:**

```kotlin
data class AppSettings(
    val contentLanguageOrder: List<String>,
    val periodicChapterCheckHours: Int,
    val requireUnmeteredNetwork: Boolean,
    val requireBatteryNotLow: Boolean,
    val protectedSourceBackgroundRefresh: Boolean,
    val notifyNewCanonicalChapters: Boolean,
    val notifyPreferredLanguageReleases: Boolean,
    val readerFontScale: Float,
    val automaticCacheQuotaBytes: Long,
)
```

**Invariants:**
- Languages are trimmed, lowercase, non-blank, control-free, whitespace-free, and deduplicated while
  preserving first occurrence.
- Cadence is exactly one of `1`, `3`, `6`, `12`, or `24` hours.
- Reader font scale is within the current Reader bounds.
- Cache quota is between 0 and the reviewed maximum safe value.

- [ ] Write RED tests for every invariant and exact defaults.
- [ ] Implement `AppSettings.normalized(defaults)` as the single normalization entry point.
- [ ] Keep device locale lookup in `SettingsDefaults`, not in the model.
- [ ] Run `./gradlew :settings:testDebugUnitTest --tests '*AppSettingsTest*'`.
- [ ] Expected GREEN: normalization is deterministic and idempotent.
- [ ] Commit `settings: define typed local policies`.

### Task 6: Define repository contract and deterministic in-memory fixture

**Files:**
- Create: `settings/src/main/kotlin/app/openstory/settings/AppSettingsRepository.kt`
- Create: `settings/src/test/kotlin/app/openstory/settings/FakeAppSettingsRepository.kt`
- Test: `settings/src/test/kotlin/app/openstory/settings/AppSettingsRepositoryContractTest.kt`

**Produces:**

```kotlin
interface AppSettingsRepository {
    val settings: Flow<AppSettings>
    suspend fun update(transform: (AppSettings) -> AppSettings)
}
```

- [ ] Write a reusable contract test proving initial emission, normalized atomic update, unrelated
  field preservation, and concurrent transformation serialization.
- [ ] Implement the fake with `MutableStateFlow` plus `Mutex` so later tests exercise repository
  semantics rather than ad-hoc setter fakes.
- [ ] Run the focused contract test; expected GREEN for the fake.
- [ ] Commit `settings: add repository contract`.

### Task 7: Implement Preferences DataStore persistence

**Files:**
- Create: `settings/src/main/kotlin/app/openstory/settings/DataStoreAppSettingsRepository.kt`
- Create: `settings/src/main/kotlin/app/openstory/settings/SettingsPreferenceCodec.kt`
- Test: `settings/src/test/kotlin/app/openstory/settings/DataStoreAppSettingsRepositoryTest.kt`

**Consumes:** `AppSettings`, `SettingsDefaults`, and `AppSettingsRepository`.

**Codec rule:** Each field is decoded independently. One wrong type/value falls back only that field;
an unreadable preferences file invokes the complete corruption fallback.

- [ ] Write RED tests for round-trip, process recreation with the same file, malformed single field,
  corrupt file, concurrent updates, and credentials never appearing in serialized preferences.
- [ ] Keep every Preferences key private inside `SettingsPreferenceCodec`.
- [ ] Normalize after decode and before encode.
- [ ] Use atomic DataStore `edit`; do not read-current/write-later outside the transaction.
- [ ] Run the focused DataStore test and repository contract test against the real adapter.
- [ ] Expected GREEN: valid sibling fields survive malformed input.
- [ ] Commit `settings: persist typed policies`.

### Task 8: Bind settings and add redacted diagnostics

**Files:**
- Create: `app/src/main/kotlin/app/openstory/di/SettingsModule.kt`
- Create: `settings/src/main/kotlin/app/openstory/settings/SettingsDiagnosticSink.kt`
- Create: `app/src/main/kotlin/app/openstory/settings/RedactedSettingsDiagnosticSink.kt`
- Test: `app/src/test/kotlin/app/openstory/settings/RedactedSettingsDiagnosticSinkTest.kt`

**Produces:** One application-scoped settings repository and diagnostics that expose only stable
error codes.

- [ ] Write RED tests rejecting serialized preference values, language lists, paths, and exception
  messages from diagnostics.
- [ ] Bind DataStore, defaults, repository, and diagnostic sink through Hilt.
- [ ] Run `:settings:testDebugUnitTest`, focused app tests, architecture verification, and Detekt.
- [ ] Commit `app: bind typed settings`.

**Phase 1 checkpoint:** Typed settings persist independently; no current consumer uses them yet.

---

## Phase 2 - Reader And Cache Policy Cleanup

### Task 9: Declare Reader-owned preferences port

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/preferences/ReaderPreferencesPort.kt`
- Test: `reader/src/test/kotlin/app/openstory/reader/preferences/ReaderPreferencesTest.kt`
- Create: `app/src/main/kotlin/app/openstory/settings/SettingsReaderPreferencesAdapter.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/SettingsModule.kt`

**Produces:**

```kotlin
data class ReaderPreferences(val fontScale: Float, val languageOrder: List<String>)

interface ReaderPreferencesPort {
    val preferences: Flow<ReaderPreferences>
    suspend fun setFontScale(value: Float)
}
```

- [ ] Write RED model tests for bounds and normalized language order.
- [ ] Implement the app adapter as a projection/update over `AppSettingsRepository`.
- [ ] Bind it as `ReaderPreferencesPort`; do not add a Reader dependency on settings.
- [ ] Run Reader and app focused tests plus architecture verification.
- [ ] Commit `reader: add preferences port`.

### Task 10: Migrate ReaderViewModel off SavedState policy

**Files:**
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt`
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderUiState.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelTest.kt`
- Remove or rewrite: Phase 0 Reader legacy-contract assertions.

**Behavior:** `SavedStateHandle` retains chapter/release navigation restoration only. Font scale and
language order come from `ReaderPreferencesPort`.

- [ ] Write RED tests for initial preference emission, live update, persisted increase/decrease,
  write failure preserving last good UI state, and language order passed to `ReleaseSelectionPolicy`.
- [ ] Inject `ReaderPreferencesPort` through the assisted ViewModel constructor.
- [ ] Collect preferences in `viewModelScope`; avoid starting duplicate collectors on reload.
- [ ] Make font buttons call the port and let the observed value update UI state.
- [ ] Remove `FONT_SCALE_KEY` production usage.
- [ ] Run focused ReaderViewModel tests, feature Reader suite, and screenshots.
- [ ] Commit `reader: migrate persistent preferences`.

### Task 11: Declare cache policy port and migrate automatic cache

**Files:**
- Create: `downloads/src/main/kotlin/app/openstory/downloads/cache/CachePolicyPort.kt`
- Create: `app/src/main/kotlin/app/openstory/settings/SettingsCachePolicyAdapter.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStore.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/DownloadModule.kt`
- Test: `downloads/src/test/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStorePolicyTest.kt`
- Remove or rewrite: Phase 0 cache legacy-contract test.

**Produces:**

```kotlin
data class CachePolicy(val quotaBytes: Long)
fun interface CachePolicyPort { suspend fun current(): CachePolicy }
```

- [ ] Write RED tests proving each write reads current policy, quota decreases evict on the next
  enforcement, policy read failure uses the safe 256 MiB fallback, and explicit downloads are never
  evicted as automatic cache.
- [ ] Move the normal quota source from constructor value to `CachePolicyPort`.
- [ ] Keep the fallback constant private and document it as recovery-only.
- [ ] Bind the settings adapter in `DownloadModule`.
- [ ] Run downloads, Reader, storage-file, and app tests.
- [ ] Commit `downloads: consume typed cache policy`.

**Phase 2 checkpoint:** Current Reader and cache paths consume typed policy; obsolete production
ownership is removed.

---

## Phase 3 - Bounded Background Scheduling Core

### Task 12: Centralize stable work names and input schemas

**Files:**
- Create: `app/src/main/kotlin/app/openstory/work/WorkNames.kt`
- Create: `app/src/main/kotlin/app/openstory/work/WorkInput.kt`
- Modify: `app/src/main/kotlin/app/openstory/work/LibraryMappingWorker.kt`
- Modify: `app/src/main/kotlin/app/openstory/work/InitialChapterSyncWorker.kt`
- Modify: `app/src/main/kotlin/app/openstory/work/ChapterDownloadWorker.kt`
- Modify: `app/src/main/kotlin/app/openstory/work/WorkManagerCanonicalEngineWorkScheduler.kt`
- Modify: `app/src/main/kotlin/app/openstory/work/WorkManagerPostMergeDerivedWorkDispatcher.kt`
- Test: `app/src/test/kotlin/app/openstory/work/WorkNamesTest.kt`
- Test: `app/src/test/kotlin/app/openstory/work/WorkInputTest.kt`

**Produces:** Typed builders/parsers for stable ID strings and these new names:

```text
library-chapter-periodic
library-chapter-continuation
chapter-notification-drain
```

- [ ] Write RED tests that duplicate all Phase 0 frozen values against `WorkNames`.
- [ ] Add versioned keys only for new periodic cursor input; do not rename existing input keys.
- [ ] Parse IDs with fail-safe `Result.failure()` behavior, never unchecked constructor exceptions.
- [ ] Migrate mapping, initial sync, download, canonical retry/safety/drain, and post-merge adapters.
- [ ] Run all app work tests; expected GREEN with no enqueue-policy change.
- [ ] Commit `app: centralize stable work contracts`.

### Task 13: Expose eligible chapter-sync candidates

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncCandidateSource.kt`
- Modify: `library/src/main/kotlin/app/openstory/library/LibraryRepository.kt`
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/repository/ChapterRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/library/LibraryDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/library/RoomLibraryRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterSyncDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomChapterRepository.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/RoomChapterSyncCandidateSourceTest.kt`

**Produces:**

```kotlin
data class ChapterSyncCandidate(
    val storyId: StoryId,
    val lastSuccessfulSyncAtEpochMillis: Long?,
)

fun interface ChapterSyncCandidateSource {
    suspend fun eligibleCandidates(): List<ChapterSyncCandidate>
}
```

- [ ] Write RED Room tests for Library-only eligibility, canonical story redirect resolution,
  duplicate removal, newest mapping aggregation, empty Library, and stable Story ID ordering.
- [ ] Define eligibility explicitly: current Library membership, at least one protected mapping, and
  background refresh not otherwise disabled by user policy.
- [ ] Return domain models only; keep entities/SQL private.
- [ ] Run chapters and Room focused tests.
- [ ] Commit `chapters: expose background sync candidates`.

### Task 14: Implement pure batch planning and compact cursor codec

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncBatchPlanner.kt`
- Create: `app/src/main/kotlin/app/openstory/work/ChapterSyncCursorCodec.kt`
- Test: `chapters/src/test/kotlin/app/openstory/chapters/sync/ChapterSyncBatchPlannerTest.kt`
- Test: `app/src/test/kotlin/app/openstory/work/ChapterSyncCursorCodecTest.kt`

**Produces:**

```kotlin
data class ChapterSyncBatchCursor(
    val nullTimestampBucket: Boolean,
    val lastSuccessfulSyncAtEpochMillis: Long?,
    val storyId: StoryId,
)

data class ChapterSyncBatch(
    val selected: List<ChapterSyncCandidate>,
    val nextCursor: ChapterSyncBatchCursor?,
)
```

- [ ] Write RED tests for null timestamp first, oldest success, Story ID tie-break, batch size 20,
  cursor continuation, malformed cursor, duplicate input, and no starvation within a cycle.
- [ ] Keep planner pure and deterministic.
- [ ] Encode only cursor fields into WorkManager `Data`; assert encoded size remains bounded.
- [ ] Run both focused suites.
- [ ] Commit `chapters: plan bounded sync batches`.

### Task 15: Map typed policy to WorkManager constraints

**Files:**
- Create: `app/src/main/kotlin/app/openstory/work/WorkConstraintsFactory.kt`
- Test: `app/src/test/kotlin/app/openstory/work/WorkConstraintsFactoryTest.kt`

**Rules:** Unmetered preference maps to `UNMETERED`, otherwise `CONNECTED`; battery preference maps to
`requiresBatteryNotLow`; cadence must meet WorkManager's platform minimum.

- [ ] Write RED tests for every policy combination and invalid cadence rejection.
- [ ] Keep storage admission inside downloads; do not translate cache quota into WorkManager storage
  constraints.
- [ ] Run the focused test.
- [ ] Commit `app: map background work constraints`.

### Task 16: Add scheduling port and unique periodic registration

**Files:**
- Create: `settings/src/main/kotlin/app/openstory/settings/work/BackgroundWorkSchedulePort.kt`
- Create: `app/src/main/kotlin/app/openstory/work/WorkManagerBackgroundWorkScheduleAdapter.kt`
- Test: `app/src/test/kotlin/app/openstory/work/WorkManagerBackgroundWorkScheduleAdapterTest.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/work/PeriodicWorkIdempotencyTest.kt`

**Produces:**

```kotlin
interface BackgroundWorkSchedulePort {
    suspend fun apply(settings: AppSettings)
    suspend fun cancelPeriodicChapterChecks()
}
```

- [ ] Write RED tests for one unique registration, identical settings no-op, changed cadence update,
  changed constraints update, disable cancellation, and canonical work unaffected.
- [ ] Choose and document the reviewed `ExistingPeriodicWorkPolicy` based on whether cadence changes
  must restart the interval anchor.
- [ ] Catch platform scheduling failures and expose a redacted result to status UI without corrupting
  saved settings.
- [ ] Run focused unit and instrumentation tests.
- [ ] Commit `app: register unique periodic work`.

### Task 17: Implement periodic dispatcher and continuation worker

**Files:**
- Create: `app/src/main/kotlin/app/openstory/work/PeriodicChapterSyncWorker.kt`
- Create: `app/src/main/kotlin/app/openstory/work/ChapterSyncContinuationWorker.kt`
- Test: `app/src/test/kotlin/app/openstory/work/PeriodicChapterSyncWorkerTest.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/work/PeriodicChapterContinuationTest.kt`

**Flow:** Read cursor -> snapshot candidates -> plan 20 -> enqueue story-specific existing sync work
with `KEEP` -> enqueue one continuation when `nextCursor != null` -> success.

- [ ] Write RED tests for empty batch, 20-item bound, cursor advance, enqueue failure isolation,
  duplicate existing story work, malformed cursor failure, and continuation completion.
- [ ] Never call `ChapterSyncService` from dispatcher/continuation workers.
- [ ] Do not use `Result.retry()` as pagination; continuation is explicit unique work.
- [ ] Ensure a lost continuation is recovered by the next periodic run.
- [ ] Run focused unit/instrumentation tests.
- [ ] Commit `app: dispatch bounded chapter checks`.

**Phase 3 checkpoint:** Periodic work is unique, policy-driven, cursor-bounded, and delegates to
existing story-specific work.

---

## Phase 4 - Trigger And Worker Cutover

### Task 18: Normalize capability scheduler contracts

**Files:**
- Modify: `library/src/main/kotlin/app/openstory/library/LibraryMappingScheduler.kt`
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncModels.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/DownloadModels.kt`
- Modify: scheduler fakes/tests in consuming modules.

**Rule:** Preserve SAM compatibility where possible. Add default cancellation methods rather than a
second scheduler interface when the capability already owns one.

- [ ] Write RED contract tests for mapping schedule, chapter schedule, download schedule/cancel, and
  exception isolation.
- [ ] Add only methods required by current production consumers.
- [ ] Run library, chapters, downloads, and feature test compilation.
- [ ] Commit `refactor: normalize capability schedulers`.

### Task 19: Refactor existing workers onto shared contracts

**Files:**
- Modify: `app/src/main/kotlin/app/openstory/work/LibraryMappingWorker.kt`
- Modify: `app/src/main/kotlin/app/openstory/work/InitialChapterSyncWorker.kt`
- Modify: `app/src/main/kotlin/app/openstory/work/ChapterDownloadWorker.kt`
- Modify: `app/src/main/kotlin/app/openstory/work/WorkManagerCanonicalEngineWorkScheduler.kt`
- Modify: `app/src/main/kotlin/app/openstory/work/WorkManagerPostMergeDerivedWorkDispatcher.kt`
- Test: `app/src/test/kotlin/app/openstory/work/CapabilityWorkerDecisionTest.kt`

- [ ] Write RED table-driven tests for invalid input, retryable global failure, source-specific
  failure, partial success, cancellation, and completed download.
- [ ] Use `WorkNames`, `WorkInput`, and `WorkConstraintsFactory` everywhere.
- [ ] Preserve current queue/outbox ownership and existing enqueue policies unless a focused test
  proves a required correction.
- [ ] Run all app work tests and Phase 0 frozen-contract tests.
- [ ] Commit `app: refactor capability workers`.

### Task 20: Add distinct background policy coordinator

**Files:**
- Create: `settings/src/main/kotlin/app/openstory/settings/work/BackgroundPolicyService.kt`
- Test: `settings/src/test/kotlin/app/openstory/settings/work/BackgroundPolicyServiceTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/OpenStoryApplication.kt` or reviewed startup owner.

**Behavior:** Observe only the scheduling projection of settings. Reader font or cache quota changes
must not reschedule periodic work.

- [ ] Write RED tests for initial apply, projection distinctness, disable cancellation, failure
  recovery, collector restart, and no duplicate apply.
- [ ] Start one application-scoped collector.
- [ ] Keep WorkManager types outside `:settings`.
- [ ] Run settings and app tests.
- [ ] Commit `settings: coordinate background policy`.

### Task 21: Preserve direct manual refresh and scheduler-owned downloads

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterListViewModel.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/download/DownloadViewModel.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsViewModel.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/chapters/ChapterListViewModelTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/download/DownloadViewModelTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/downloads/DownloadsViewModelTest.kt`

- [ ] Prove manual chapter refresh calls `ChapterSyncService.sync` exactly once and reports immediate
  UI state.
- [ ] Prove initial and periodic workers call the same service through adapters.
- [ ] Prove download UI calls `DownloadService.queue` then `DownloadScheduler.schedule`, and cancel
  calls both capability and scheduler cancellation boundaries.
- [ ] Do not modify `LibraryViewModel` unless a separately tested Library-wide refresh action exists.
- [ ] Run feature catalog and app tests.
- [ ] Commit `catalog: align manual and scheduled triggers`.

**Phase 4 checkpoint:** Manual, initial, periodic, download, post-merge, and canonical triggers share
engines without sharing platform implementation types.

---

## Phase 5 - Authentication Wire Contract

### Task 22: Define authentication manifest models

**Files:**
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/manifest/PluginManifest.kt`
- Test: `plugins/api/src/test/kotlin/app/openstory/plugins/api/manifest/PluginManifestTest.kt`
- Add/update deterministic JSON fixtures.

**Produces:** Auth start URL, navigation hosts, completion target, credential targets, cookie-name
allowlists, session TTL, and deterministic auth-policy fingerprint input.

- [ ] Write RED tests rejecting HTTP, wildcard, user-info, port, fragment in completion target,
  blank/non-normalized path, credential host outside network capability, duplicate cookie name, empty
  cookie allowlist, and TTL outside reviewed bounds.
- [ ] Add safe `null` default so current manifests remain valid.
- [ ] Make serialization deterministic for sets used by fingerprinting.
- [ ] Run plugins API tests.
- [ ] Commit `plugins: add authentication manifest`.

### Task 23: Migrate managed credentials to validated request target

**Files:**
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/ManagedCredentialProvider.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/PluginHttpCapability.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/MyAnimeListManagedCredentials.kt`
- Modify: `app/src/test/kotlin/app/openstory/di/MyAnimeListManagedCredentialsTest.kt`
- Modify: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/capabilities/http/PluginHttpCapabilityTest.kt`

**Produces:**

```kotlin
data class ManagedCredentialRequest(val pluginId: PluginId, val url: String)
fun interface ManagedCredentialProvider {
    suspend fun headers(request: ManagedCredentialRequest): Map<String, String>
}
```

- [ ] Write RED tests proving credentials receive every validated redirect target and never receive
  an unvalidated URL.
- [ ] Parse/validate URL first, then call credentials with the normalized HTTPS target.
- [ ] Migrate MyAnimeList credentials without changing its exact host behavior.
- [ ] Run runtime and app credential tests.
- [ ] Commit `plugins: scope credentials to request target`.

### Task 24: Add collision-rejecting credential composition

**Files:**
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/CompositeManagedCredentialProvider.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/capabilities/http/CompositeManagedCredentialProviderTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/PluginRuntimeModule.kt`

- [ ] Write RED tests for fixed provider order, case-insensitive duplicate header rejection, empty
  providers, provider exception redaction, and Cookie plus non-conflicting client header composition.
- [ ] Reject ambiguity; never silently select first/last ownership.
- [ ] Bind existing app credentials through the composite before session provider is added.
- [ ] Run focused tests.
- [ ] Commit `plugins: compose managed credentials safely`.

### Task 25: Rebaseline plugin SDK authentication/versioning docs

**Files:**
- Create: `docs/plugin-sdk/authentication.md`
- Modify: `package-format.md`, `javascript-runtime.md`, `api-versioning.md`, `repository-index.md`
- Modify: `scripts/tests/plugin-sdk-current-contract-test.sh`

- [ ] Document exact JSON examples and every validation rule from Task 22.
- [ ] State that the new host reads old packages, while old fail-closed hosts cannot read manifests
  containing the new field.
- [ ] Document that scripts never receive cookies/credentials and cannot override protected headers.
- [ ] Add fixture assertions to prevent prose/serializer drift.
- [ ] Run the SDK contract test during implementation.
- [ ] Commit `docs: specify plugin authentication`.

**Phase 5 checkpoint:** Authentication is a strict serialized contract and runtime credentials are
request-target aware; no session persistence exists yet.

---

## Phase 6 - Encrypted Sessions And Guarded WebView

### Task 26: Define runtime-owned session models and store contract

**Files:**
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/session/PluginSessionModels.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/session/PluginSessionStore.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/session/PluginSessionModelsTest.kt`

**Model fields:** plugin ID, target host, normalized path prefix, cookie name/value, created/expiry
time, and auth-policy fingerprint. Public summary excludes cookie name/value.

- [ ] Write RED tests for normalized key equality, TTL expiration, summary redaction, and invalid
  target rejection.
- [ ] Keep cookie value in a dedicated secret-bearing type whose `toString` is redacted.
- [ ] Commit `plugins: define session boundary`.

### Task 27: Implement atomic AES-GCM no-backup store

**Files:**
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/session/EncryptedPluginSessionStore.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/session/PluginSessionCipher.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/session/PluginSessionFileCodec.kt`
- Test: `plugins/runtime/src/androidTest/kotlin/app/openstory/plugins/runtime/session/EncryptedPluginSessionStoreTest.kt`

**File format:** versioned header, random 96-bit nonce, ciphertext/tag, atomic temporary file replace.
AAD binds plugin ID, target host/path, cookie name, and policy fingerprint.

- [ ] Write RED tests for confidentiality, round-trip, tamper rejection, record-swap rejection,
  partial-write recovery, key invalidation, unsupported format version, logout deletion, and location
  under `noBackupFilesDir`.
- [ ] Use Android Keystore AES/GCM directly; do not use deprecated security-crypto wrappers.
- [ ] On unreadable record, delete only the affected session and emit a redacted logged-out result.
- [ ] Run API 26 and current-target instrumentation during implementation.
- [ ] Commit `plugins: encrypt local sessions`.

### Task 28: Implement session service and lifecycle invalidation

**Files:**
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/session/PluginSessionService.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/update/PluginUpdateService.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/PluginInstaller.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntime.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/session/PluginSessionServiceTest.kt`

- [ ] Write RED tests for save/replace, logout, expiry, plugin disabled, auth capability removed,
  policy fingerprint changed on update, unchanged fingerprint preserved, rollback changed/preserved,
  and Wave 11 removal hook.
- [ ] Keep lifecycle ownership in runtime; app UI calls commands only.
- [ ] Ensure disabling a plugin immediately stops credential delivery even if encrypted rows remain.
- [ ] Run runtime install/update/session suites.
- [ ] Commit `plugins: enforce session lifecycle`.

### Task 29: Implement session credential provider

**Files:**
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/session/PluginSessionManagedCredentialProvider.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/session/PluginSessionManagedCredentialProviderTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/PluginRuntimeModule.kt`

- [ ] Write RED tests for plugin, exact HTTPS host, normalized path-prefix, cookie-name allowlist, TTL,
  policy fingerprint, disabled plugin, and cross-plugin denial.
- [ ] Return only one `Cookie` header assembled in deterministic cookie-name order.
- [ ] Do not return expired or mismatched records; schedule best-effort cleanup separately.
- [ ] Compose with existing managed credentials through Task 24 provider.
- [ ] Commit `plugins: deliver scoped session cookies`.

### Task 30: Define guarded login navigation policy

**Files:**
- Create: `app/src/main/kotlin/app/openstory/auth/LoginNavigationPolicy.kt`
- Test: `app/src/test/kotlin/app/openstory/auth/LoginNavigationPolicyTest.kt`

- [ ] Write RED tests for start URL, declared navigation, completion path-prefix, redirect resolution,
  HTTP/file/content/intent URLs, popup/new-window, download, user-info, port, and undeclared host.
- [ ] Produce explicit decisions: `ALLOW`, `COMPLETE`, `BLOCK`.
- [ ] Keep Android WebView classes out of the pure policy.
- [ ] Commit `auth: define guarded navigation policy`.

### Task 31: Implement serialized WebView login capture

**Files:**
- Create: `app/src/main/kotlin/app/openstory/auth/PluginLoginCaptureActivity.kt`
- Create: `app/src/main/kotlin/app/openstory/auth/PluginLoginCoordinator.kt`
- Create: `app/src/main/kotlin/app/openstory/auth/RuntimePluginSessionControlAdapter.kt`
- Create: `settings/src/main/kotlin/app/openstory/settings/session/PluginSessionControlPort.kt`
- Modify: `AndroidManifest.xml`, `PluginRuntimeModule.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/auth/PluginLoginCoordinatorTest.kt`

- [ ] Write RED device tests for single active flow, transient cleanup before/after, declared cookie
  capture, completion, cancel, process recreation denial, blocked navigation, no popup/download,
  logout, and no secret in saved instance state/logs.
- [ ] Disable file/content access, mixed content, geolocation, media capture, JavaScript interfaces,
  password saving, and release debugging.
- [ ] Capture only declared cookie names by querying declared credential target URLs after completion.
- [ ] Clear CookieManager, WebStorage, cache, history, form data, and WebView state in `finally`.
- [ ] Expose summaries/login/logout only through `PluginSessionControlPort`.
- [ ] Run API 26/API 37 focused instrumentation.
- [ ] Commit `auth: add guarded plugin login`.

**Phase 6 checkpoint:** Session storage and capture are encrypted, scoped, lifecycle-aware, and usable
without exposing runtime internals to presentation.

---

## Phase 7 - Transactional Chapter-Change Evidence

### Task 32: Define raw chapter-change facts and deterministic keys

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/notification/ChapterChangeModels.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/notification/ChapterChangeDetector.kt`
- Test: `chapters/src/test/kotlin/app/openstory/chapters/notification/ChapterChangeDetectorTest.kt`

**Kinds:** `NEW_CANONICAL_CHAPTER`, `ADDED_RELEASE`, `RESTORED_CANONICAL_CHAPTER`.

- [ ] Write RED tests comparing pre/post graph for new chapter, equivalent release, restored chapter,
  tombstone, relink, deletion, duplicate page commit, and canonical Story redirect.
- [ ] Generate deterministic event key from resolved Story ID, canonical chapter ID, release ID when
  present, kind, and commit fingerprint.
- [ ] Keep detector pure and free of settings/progress/notification policy.
- [ ] Commit `chapters: detect raw chapter changes`.

### Task 33: Define schema-11 entities and DAO contract

**Files:**
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterChangeEventEntity.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/NotificationDeliveryEntity.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/NotificationEventDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/DatabaseBaselineTest.kt`

**Delivery states:** `PENDING`, `CLAIMED`, `DELIVERED`, `IN_APP_ONLY`, `CONSUMED`.

- [ ] Write RED entity/DAO tests for unique event key, deterministic ordering, bounded pending query,
  claim token ownership, stale claim lookup, stable notification ID uniqueness, and cascade/restrict
  lifecycle choices.
- [ ] Store raw facts separately from delivery decisions.
- [ ] Add indexes for pending order, claim expiry, story/chapter status, and notification ID.
- [ ] Commit with Task 34 so schema version is never advanced without migration.

### Task 34: Implement `MIGRATION_10_11`

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/NotificationMigrationTest.kt`

- [ ] Start RED from exported schema 10 populated with catalog, canonical queue/lease/outbox, Library,
  mapping, chapter, Reader progress, and Download rows.
- [ ] Create only chapter-change and notification-delivery tables/indexes.
- [ ] Migrate to 11 and assert every seeded row, table, index, foreign key, and schema identity hash.
- [ ] Run `:storage:room:connectedDebugAndroidTest` focused migration and schema stability.
- [ ] Commit `storage: add schema 11 notification state`.

### Task 35: Insert raw events inside chapter commit transaction

**Files:**
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/repository/ChapterMutation.kt`
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/repository/ChapterRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomChapterRepository.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/ChapterChangeOutboxTransactionTest.kt`

- [ ] Write RED tests proving graph plus outbox commit together, forced outbox failure rolls back graph,
  retry produces no duplicate event, multi-page sync remains idempotent, and story redirect resolves
  before key creation.
- [ ] Read the pre-commit graph inside the transaction, apply mutation, read/derive committed graph,
  detect facts, and insert with unique conflict semantics.
- [ ] Do not call notification platform or classifier inside transaction.
- [ ] Run Room chapter repository and transaction suites.
- [ ] Commit `chapters: persist transactional change facts`.

### Task 36: Implement durable event repository and claim recovery

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/notification/NotificationEventRepository.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomNotificationEventRepository.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/RoomNotificationEventRepositoryTest.kt`

**Produces:** bounded claim, mark delivered, mark in-app-only, consume, release/reclaim stale claim, and
recent in-app summaries.

- [ ] Write RED tests for competing claimers, claim token mismatch, stale reclaim, process-death
  simulation, bounded order, idempotent terminal transitions, and collision-safe notification ID
  allocation.
- [ ] Use Room transactions for every state transition.
- [ ] Never treat permission denial as failed attempt.
- [ ] Commit `storage: add durable notification claims`.

**Phase 7 checkpoint:** Schema 11 exists; chapter graph and raw change facts are atomic; delivery has
recoverable durable state but no platform notifier yet.

---

## Phase 8 - Notification Classification And Delivery

### Task 37: Implement pure notification classification

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/notification/ChapterNotificationClassifier.kt`
- Test: `chapters/src/test/kotlin/app/openstory/chapters/notification/ChapterNotificationClassifierTest.kt`

**Inputs:** raw facts, notification policy, content-language order, simple read/relevance projection,
and current chapter target projection. Do not import Reader repository types into `:chapters`.

- [ ] Write RED tests for new-chapter precedence, preferred-language release, same-group suppression,
  already-read, irrelevant, tombstoned, missing target, restored chapter, disabled category, and
  deterministic output.
- [ ] Produce at most one new-chapter notification per canonical chapter and at most one preferred
  release when no new-chapter decision exists for that group.
- [ ] Commit `chapters: classify local update notifications`.

### Task 38: Build validated notification targets and deep links

**Files:**
- Create: `app/src/main/kotlin/app/openstory/notification/NotificationDeepLinkFactory.kt`
- Test: `app/src/test/kotlin/app/openstory/notification/NotificationDeepLinkFactoryTest.kt`

- [ ] Write RED tests for stable ID parsing, canonical Story redirect, missing story/chapter, tombstone,
  explicit immutable PendingIntent flags, request-code uniqueness, and malicious/invalid Intent data.
- [ ] Treat identifiers as untrusted routing input, not authorization secrets.
- [ ] Resolve current target before creating the PendingIntent.
- [ ] Commit `app: validate notification deep links`.

### Task 39: Add channels, permission decision, and platform notifier

**Files:**
- Create: `app/src/main/kotlin/app/openstory/notification/NotificationChannels.kt`
- Create: `app/src/main/kotlin/app/openstory/notification/AndroidChapterNotifier.kt`
- Create: `app/src/main/kotlin/app/openstory/notification/NotificationPermissionPolicy.kt`
- Modify: `AndroidManifest.xml`
- Test: `app/src/test/kotlin/app/openstory/notification/AndroidChapterNotifierTest.kt`
- Test: `app/src/test/kotlin/app/openstory/notification/NotificationPermissionPolicyTest.kt`

- [ ] Write RED tests for API <33, granted/denied permission, disabled channel, stable/collision-resolved
  notification ID, title/chapter/language copy, no chapter body, and redacted failure.
- [ ] Create separate chapter-update and long-running-download channels.
- [ ] Return a delivery decision; notifier never mutates repository state directly.
- [ ] Commit `app: add local notification adapter`.

### Task 40: Implement unique notification drain and wake port

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/notification/NotificationDrainScheduler.kt`
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterPageSynchronizer.kt`
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncService.kt`
- Create: `app/src/main/kotlin/app/openstory/notification/NotificationDeliveryWorker.kt`
- Create: `app/src/main/kotlin/app/openstory/notification/WorkManagerNotificationDrainScheduler.kt`
- Test: `app/src/test/kotlin/app/openstory/notification/NotificationDeliveryDecisionTest.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/notification/NotificationDeliveryRecoveryTest.kt`

- [ ] Write RED tests for claim/classify/deliver/mark flow, process death after claim, process death
  after platform publish, stale reclaim, permission denial to `IN_APP_ONLY`, disabled category consume,
  missing target consume, bounded batch, and retryable notifier failure.
- [ ] Request best-effort wake after successful chapter commit; scheduling failure does not affect
  chapter result.
- [ ] Register `chapter-notification-drain` at application start for durable recovery.
- [ ] Use one unique drain chain and bounded claims.
- [ ] Run unit and device recovery tests.
- [ ] Commit `app: drain durable chapter notifications`.

### Task 41: Expose narrow notification control/status port

**Files:**
- Create: `settings/src/main/kotlin/app/openstory/settings/notification/NotificationControlPort.kt`
- Create: `app/src/main/kotlin/app/openstory/notification/RuntimeNotificationControlAdapter.kt`
- Test: `app/src/test/kotlin/app/openstory/notification/RuntimeNotificationControlAdapterTest.kt`

**Produces:** permission/channel status, recent `IN_APP_ONLY` summaries, request-permission command, and
open-system-channel-settings command without Android types in `:settings`.

- [ ] Write RED tests for API-level behavior, contextual permission request, denied/granted refresh,
  channel disabled, recent status order, and adapter error redaction.
- [ ] Never request permission at startup.
- [ ] Commit `settings: expose notification controls`.

**Phase 8 checkpoint:** Notifications are classified, validated, delivered/recovered, and observable
without retry spam.

---

## Phase 9 - Settings And Status Presentation

### Task 42: Build SettingsViewModel state and actions

**Files:**
- Create: `feature/settings/src/main/kotlin/app/openstory/settings/ui/SettingsUiState.kt`
- Create: `feature/settings/src/main/kotlin/app/openstory/settings/ui/SettingsViewModel.kt`
- Test: `feature/settings/src/test/kotlin/app/openstory/settings/ui/SettingsViewModelTest.kt`

**Consumes:** `AppSettingsRepository`, session control, notification control, storage/cache summary,
and scheduling status projection. It does not consume WorkManager, Room, WebView, or runtime types.

- [ ] Write RED tests for initial combined state, update normalization, concurrent action serialization,
  session login/logout, permission request, channel settings, storage usage/quota, recent in-app-only
  events, errors, retry, and process recreation.
- [ ] Keep repository Flow authoritative; do not mirror policy into independent Compose-only state.
- [ ] Commit `settings: add settings presentation state`.

### Task 43: Build settings sections with design-system primitives

**Files:**
- Create: `feature/settings/src/main/kotlin/app/openstory/settings/ui/SettingsScreen.kt`
- Create: `feature/settings/src/main/kotlin/app/openstory/settings/ui/SyncSettings.kt`
- Create: `feature/settings/src/main/kotlin/app/openstory/settings/ui/ReaderSettings.kt`
- Create: `feature/settings/src/main/kotlin/app/openstory/settings/ui/NotificationSettings.kt`
- Create: `feature/settings/src/main/kotlin/app/openstory/settings/ui/SourceSessionSettings.kt`
- Create: `feature/settings/src/main/kotlin/app/openstory/settings/ui/StorageSettings.kt`
- Test: `feature/settings/src/androidTest/kotlin/app/openstory/settings/ui/SettingsScreenTest.kt`
- Test: `feature/settings/src/test/kotlin/app/openstory/settings/ui/SettingsScreenshotTest.kt`

- [ ] Write RED Compose tests for every control, disabled/loading/error state, contextual permission,
  login/logout confirmation, cache quota copy, Android deferral explanation, and no cloud/push claim.
- [ ] Use only Hikari tokens/primitives; add a design-system primitive only when it is genuinely
  domain-neutral and reused.
- [ ] Cover large font, screen widths, keyboard/focus order, talkback labels, reduced motion, light,
  and dark screenshot fixtures.
- [ ] Commit `settings: build local policy controls`.

### Task 44: Add utility navigation and restoration

**Files:**
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppRoute.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`
- Modify: `app/src/main/kotlin/app/openstory/ui/HikariUtilitySheet.kt`
- Create: `app/src/main/kotlin/app/openstory/navigation/SettingsDestination.kt`
- Modify: `app/src/main/kotlin/app/openstory/ui/HikariAppShell.kt` only if focus/chrome behavior requires it.
- Test: `app/src/test/kotlin/app/openstory/navigation/AppRouteSerializationTest.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/navigation/AppNavigationTest.kt`
- Test: `app/src/test/kotlin/app/openstory/navigation/AppShellScreenshotTest.kt`

- [ ] Write RED tests for `AppRoute.Settings`, utility sheet dismissal before navigation, Back to the
  originating active stack, process restoration, focus return, no floating navigation on Settings,
  and unchanged `TopLevelDestination.entries`.
- [ ] Push Settings on the active top-level back stack; do not create a fourth top-level stack.
- [ ] Commit `app: add settings utility route`.

**Phase 9 checkpoint:** All Wave 10 policy/session/notification/storage status is usable through one
non-top-level screen.

---

## Phase 10 - Integration, Hardening, And Acceptance

### Task 45: Add end-to-end policy and scheduling integration tests

**Files:**
- Create: `app/src/androidTest/kotlin/app/openstory/Wave10PolicySchedulingIntegrationTest.kt`

- [ ] Test settings persistence -> Reader update -> cache quota update -> periodic registration.
- [ ] Test unrelated Reader/cache changes do not reschedule periodic work.
- [ ] Test disable periodic work leaves manual refresh, downloads, canonical drain, and safety work.
- [ ] Test process recreation restores policy and one unique schedule.
- [ ] Commit `test: cover wave 10 policy integration`.

### Task 46: Add end-to-end auth integration tests

**Files:**
- Create: `app/src/androidTest/kotlin/app/openstory/Wave10AuthenticationIntegrationTest.kt`

- [ ] Use deterministic local HTTPS/WebView fixtures; never call live websites.
- [ ] Test login capture -> encrypted persistence -> runtime HTTP Cookie delivery -> logout denial.
- [ ] Test plugin B cannot receive plugin A session.
- [ ] Test path mismatch, expiry, disabled plugin, changed auth policy update, rollback, and process
  recreation.
- [ ] Commit `test: cover wave 10 authentication integration`.

### Task 47: Add end-to-end notification integration tests

**Files:**
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/Wave10NotificationTransactionTest.kt`
- Create: `app/src/androidTest/kotlin/app/openstory/Wave10NotificationDeliveryIntegrationTest.kt`

- [ ] Test chapter sync transaction creates deterministic raw event.
- [ ] Test equivalent releases classify once.
- [ ] Test process death at each claim/publish boundary.
- [ ] Test denied permission becomes in-app-only and later grant does not publish history.
- [ ] Test canonical Story redirect and deleted/tombstoned target handling.
- [ ] Commit `test: cover wave 10 notification integration`.

### Task 48: Run host/device acceptance and close documentation

**Files:**
- Modify: `docs/project/current-state.md`
- Modify: `docs/implementation/current-roadmap.md`
- Create: `docs/internal/checkpoints/wave-10-background-sync-auth-and-notifications.md`
- Update: plugin SDK docs and repository indexes if implementation paths differ from plan.

- [ ] Run every repository policy script and strict dependency verification.
- [ ] Run focused module suites after a clean checkout.
- [ ] Run `scripts/verify-fast.sh` and `scripts/verify.sh`.
- [ ] Run storage/app/auth/notification instrumentation on API 26 and API 37.
- [ ] Run screenshot/accessibility checks with large font and reduced motion.
- [ ] Record exact commands, environment, counts, failures/fixes, rerun evidence, schema export, module
  graph, and commit range in the checkpoint.
- [ ] Do not mark Wave 10 complete from source presence alone.
- [ ] Commit `docs: accept wave 10` only after all evidence is reviewed.

---

## Final Acceptance Matrix

### Architecture

- [ ] Exactly 16 production modules and one benchmark android-test module.
- [ ] `:settings` depends only on `:core:common`.
- [ ] `:reader` and `:downloads` own their policy ports and do not depend on settings.
- [ ] Feature Settings imports no Room, WorkManager, WebView, runtime internals, or notification APIs.

### Settings And Cleanup

- [ ] Per-field malformed settings preserve valid siblings; complete corruption uses safe defaults.
- [ ] Reader font scale no longer uses SavedState as policy persistence.
- [ ] Reader supplies typed language order to release selection.
- [ ] Automatic cache quota no longer uses a hard-coded normal production source.

### Background Work

- [ ] Frozen existing names remain stable.
- [ ] Periodic registration is unique and policy updates reschedule exactly once.
- [ ] Each dispatcher handles at most 20 stories and serializes only a compact cursor.
- [ ] Failing early stories/plugins do not starve later candidates in the same cycle.
- [ ] Manual refresh remains immediate and shares `ChapterSyncService` with scheduled work.

### Authentication

- [ ] Manifest navigation/completion/credential targets are exact and fail closed.
- [ ] Credential lookup uses validated plugin plus HTTPS URL/path context.
- [ ] Sessions are AES-GCM encrypted, no-backup, atomic, scoped, expiring, and redacted.
- [ ] Update/rollback policy change, logout, disablement, and removal prevent credential delivery.
- [ ] WebView transient state is cleared before and after every serialized login flow.

### Notifications

- [ ] `MIGRATION_10_11` preserves all schema-10 data and foreign keys.
- [ ] Chapter graph and raw change facts commit atomically.
- [ ] Claim/reclaim survives process death and terminal transitions are idempotent.
- [ ] New canonical chapter supersedes equivalent preferred-release alerts.
- [ ] Permission/channel denial becomes `IN_APP_ONLY` without later historical burst.
- [ ] Notification IDs are collision-safe and routes resolve current validated stable IDs.

### Presentation And Release Readiness

- [ ] Settings is available through the utility sheet and never top-level navigation.
- [ ] UI explains Android deferral, local-only behavior, permission/channel state, and logout effects.
- [ ] API 26/API 37, host verification, screenshots, accessibility, and strict dependency gates pass.
- [ ] Wave 11 enters from the accepted 16-module, schema-11 boundary.

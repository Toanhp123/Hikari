<!--
DOCUMENT LIFECYCLE
Status: PLANNED / NOT STARTED IN THIS SNAPSHOT
Current repository note: Start only after the Wave 09 checkpoint is accepted.
Canonical execution status: ../../project/current-state.md
Original planning text below is preserved rather than retroactively rewritten.
-->

# Wave 10 — Background Sync, Authentication, and Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete local automation: scheduled chapter checks, resumable action workers, scoped WebView sessions, and deduplicated local notifications.

**Architecture:** DataStore holds user policies; WorkManager adapters invoke pure matching/sync/download engines through unique idempotent work. Login uses a guarded WebView only to capture declared-host cookies into encrypted plugin-scoped storage, then clears transient state. Persisted change events feed a canonical notification classifier.

**Tech Stack:** WorkManager 2.11.2, Hilt workers, Proto DataStore, Android WebView/Keystore, notification APIs, Room, coroutines.

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

This wave adds convenience without introducing cloud infrastructure. Every automated action remains local, observable, retry-safe, and manually reproducible.

## Entry Dependencies

- Wave 09 checkpoint is approved.
- Pure chapter sync, mapping search, reader/cache/download engines are stable.
- Chapter change events are persisted idempotently.

## Exit Deliverables

- Local settings and scheduling policy.
- Periodic/manual/initial/mapping/download workers.
- Guarded WebView login and encrypted plugin sessions.
- Canonical notification classification and channels.
- Settings/plugin/story controls and status visibility.

## File/Module Boundary

Each path listed in a task owns one responsibility. Do not move business rules into Compose screens, Room entities, JavaScript snippets, or WorkManager classes. Domain interfaces are the dependency boundary; Android adapters implement them.

---

### Task 1: Persist local synchronization, language, and notification settings

**Files:**
- Create: feature/settings/build.gradle.kts
- Create: feature/settings/src/main/kotlin/app/openstory/settings/AppSettings.kt
- Create: feature/settings/src/main/kotlin/app/openstory/settings/AppSettingsRepository.kt
- Create: feature/settings/src/main/kotlin/app/openstory/settings/DataStoreAppSettingsRepository.kt
- Create: feature/settings/src/main/proto/app_settings.proto
- Test: feature/settings/src/test/kotlin/app/openstory/settings/DataStoreAppSettingsRepositoryTest.kt

**Interfaces:**
- Consumes: Language tags, plugin preferences, reader/storage settings, Android DataStore convention.
- Produces: Versioned local settings for ordered content languages, catalog languages, quick plugin count/order, sync interval/constraints, notification categories, cache quota, and per-plugin update mode.

**Acceptance:**
- Ordered language list round-trips without duplicates.
- Sync interval is clamped to supported WorkManager cadence and user-visible choices.
- Defaults derive once from device locale but remain user-controlled afterward.
- DataStore corruption resets to safe defaults and emits diagnostics.

**Implementation notes:**
- Keep secrets/cookies out of DataStore; session storage is Task 4.
- Use Proto DataStore migrations for future schema changes.
- UI will offer realistic periodic choices such as 1, 3, 6, 12, or 24 hours while explaining Android may defer background work.

- [ ] **Step 1: Write the failing test**

Create `feature/settings/src/test/kotlin/app/openstory/settings/DataStoreAppSettingsRepositoryTest.kt`:

```kotlin
package app.openstory.settings

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class DataStoreAppSettingsRepositoryTest {
    @Test fun languageOrderRoundTripsAndDeduplicates() = runTest {
        val fixture = settingsRepositoryFixture()
        fixture.repository.updateLanguages(listOf("vi", "en", "vi"))
        assertEquals(listOf("vi", "en"), fixture.repository.settings().first().contentLanguages.map { it.value })
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:settings:test --tests app.openstory.settings.DataStoreAppSettingsRepositoryTest.languageOrderRoundTripsAndDeduplicates
```

Expected: **FAIL** because persistent app settings and validation are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/settings/src/main/kotlin/app/openstory/settings/AppSettings.kt`:

```kotlin
package app.openstory.settings

data class AppSettings(
    val catalogLanguages: List<LanguageTag>,
    val contentLanguages: List<LanguageTag>,
    val syncIntervalHours: Int,
    val syncOnUnmeteredOnly: Boolean,
    val syncRequiresCharging: Boolean,
    val notifyNewChapters: Boolean,
    val notifyPreferredLanguageReleases: Boolean,
    val cacheQuotaBytes: Long,
)
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:settings:test --tests app.openstory.settings.DataStoreAppSettingsRepositoryTest.languageOrderRoundTripsAndDeduplicates
./gradlew :feature:settings:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/settings/build.gradle.kts feature/settings/src/main/kotlin/app/openstory/settings/AppSettings.kt feature/settings/src/main/kotlin/app/openstory/settings/AppSettingsRepository.kt feature/settings/src/main/kotlin/app/openstory/settings/DataStoreAppSettingsRepository.kt feature/settings/src/main/proto/app_settings.proto feature/settings/src/test/kotlin/app/openstory/settings/DataStoreAppSettingsRepositoryTest.kt
git commit -m "settings: persist local sync and language preferences"
```

### Task 2: Schedule idempotent WorkManager chapter checks with bounded batches

**Files:**
- Create: sync/src/main/kotlin/app/openstory/sync/work/ChapterSyncWorker.kt
- Create: sync/src/main/kotlin/app/openstory/sync/work/SyncScheduler.kt
- Create: sync/src/main/kotlin/app/openstory/sync/work/WorkManagerSyncScheduler.kt
- Create: sync/src/main/kotlin/app/openstory/sync/LibrarySyncCoordinator.kt
- Create: sync/src/test/kotlin/app/openstory/sync/LibrarySyncCoordinatorTest.kt
- Create: sync/src/androidTest/kotlin/app/openstory/sync/work/ChapterSyncWorkerTest.kt

**Interfaces:**
- Consumes: Chapter sync engine, Library/mapping repositories, app settings, WorkManager, clock, network state.
- Produces: Unique periodic/local work that syncs eligible Library mappings in bounded batches, respects constraints/backoff/rate limits, and records per-mapping results.

**Acceptance:**
- Only one periodic library sync chain is active.
- Changing schedule replaces policy without duplicating work.
- Worker batches and checkpoints progress so process death resumes safely.
- One mapping/plugin failure does not fail completed mappings.
- Retry result is used only for retryable global/network conditions; source-specific errors are recorded and batch continues.

**Implementation notes:**
- Use unique periodic work name `library-chapter-sync-v1`; one-time continuation may process remaining batches without changing periodic cadence.
- Apply WorkManager network constraints and host rate budgets; charging/unmetered are user settings.
- Prioritize stories with recent reading activity and stale last success, while guaranteeing eventual coverage.

- [ ] **Step 1: Write the failing test**

Create `sync/src/test/kotlin/app/openstory/sync/LibrarySyncCoordinatorTest.kt`:

```kotlin
package app.openstory.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class LibrarySyncCoordinatorTest {
    @Test fun pluginFailureDoesNotStopOtherMappings() = runTest {
        val fixture = librarySyncFixture(failingMappings = setOf("m2"), successfulMappings = setOf("m1", "m3"))
        val report = fixture.coordinator.syncBatch(fixture.allMappings)
        assertEquals(setOf("m1", "m3"), report.succeeded.map { it.value }.toSet())
        assertEquals(setOf("m2"), report.failed.keys.map { it.value }.toSet())
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :sync:test --tests app.openstory.sync.LibrarySyncCoordinatorTest.pluginFailureDoesNotStopOtherMappings
```

Expected: **FAIL** because background scheduler/coordinator and batch isolation are missing.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `sync/src/main/kotlin/app/openstory/sync/work/ChapterSyncWorker.kt`:

```kotlin
package app.openstory.sync.work

@HiltWorker
class ChapterSyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val coordinator: LibrarySyncCoordinator,
) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val report = coordinator.syncNextBatch(batchSize = 20)
        setProgress(workDataOf("completed" to report.completedCount, "remaining" to report.remainingCount))
        return when {
            report.hasMore -> Result.retry()
            report.globalRetryableFailure -> Result.retry()
            else -> Result.success(report.toOutputData())
        }
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :sync:test --tests app.openstory.sync.LibrarySyncCoordinatorTest.pluginFailureDoesNotStopOtherMappings
./gradlew :sync:test :sync:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add sync/src/main/kotlin/app/openstory/sync/work/ChapterSyncWorker.kt sync/src/main/kotlin/app/openstory/sync/work/SyncScheduler.kt sync/src/main/kotlin/app/openstory/sync/work/WorkManagerSyncScheduler.kt sync/src/main/kotlin/app/openstory/sync/LibrarySyncCoordinator.kt sync/src/test/kotlin/app/openstory/sync/LibrarySyncCoordinatorTest.kt sync/src/androidTest/kotlin/app/openstory/sync/work/ChapterSyncWorkerTest.kt
git commit -m "sync: schedule bounded local chapter checks"
```

### Task 3: Unify manual refresh, initial sync, deferred mapping search, and download workers

**Files:**
- Create: sync/src/main/kotlin/app/openstory/sync/work/ManualStorySyncWorker.kt
- Create: sync/src/main/kotlin/app/openstory/sync/work/InitialStorySyncWorker.kt
- Create: sync/src/main/kotlin/app/openstory/sync/work/DeferredMappingSearchWorker.kt
- Create: sync/src/main/kotlin/app/openstory/sync/work/ChapterDownloadWorker.kt
- Create: sync/src/main/kotlin/app/openstory/sync/work/WorkNames.kt
- Test: sync/src/androidTest/kotlin/app/openstory/sync/work/WorkIdempotencyTest.kt

**Interfaces:**
- Consumes: Pure mapping search, chapter sync, download manager, WorkManager scheduler, story/mapping/download IDs.
- Produces: Explicit worker adapters with stable unique names/input schemas so user actions and background work reuse the same domain engines safely.

**Acceptance:**
- Repeated enqueue for same story/mapping/download does not create duplicate concurrent work.
- Manual refresh uses expedited work when quota allows and falls back normally.
- Initial chain runs FAST_LATEST then FULL_INITIAL without blocking Add-to-Library.
- Deferred mapping search retries only eligible plugins/stories.
- Download worker foreground notification appears for long multi-item queues.

**Implementation notes:**
- Version worker input Data keys and fail safely when required IDs are missing.
- Workers resolve current repositories/host through DI, not serialized domain objects.
- Expose work progress through repository/UI projections rather than observing WorkManager directly in every screen.

- [ ] **Step 1: Write the failing test**

Create `sync/src/androidTest/kotlin/app/openstory/sync/work/WorkIdempotencyTest.kt`:

```kotlin
package app.openstory.sync.work

import androidx.work.WorkInfo
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class WorkIdempotencyTest {
    @Test fun sameStoryManualRefreshUsesOneUniqueWork() = runTest {
        val fixture = workManagerFixture()
        fixture.scheduler.manualStorySync(StoryId("s1"))
        fixture.scheduler.manualStorySync(StoryId("s1"))
        assertEquals(1, fixture.workManager.getWorkInfosForUniqueWork("story-sync-s1").get().count { it.state != WorkInfo.State.CANCELLED })
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :sync:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.sync.work.WorkIdempotencyTest
```

Expected: **FAIL** because worker adapters and stable unique-work names do not exist.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `sync/src/main/kotlin/app/openstory/sync/work/WorkNames.kt`:

```kotlin
package app.openstory.sync.work

object WorkNames {
    fun manualStory(storyId: StoryId) = "story-sync-${storyId.value}"
    fun initialStory(storyId: StoryId) = "story-initial-${storyId.value}"
    fun mappingSearch(storyId: StoryId) = "story-mapping-${storyId.value}"
    fun download(downloadId: DownloadId) = "chapter-download-${downloadId.value}"
    const val PERIODIC_LIBRARY_SYNC = "library-chapter-sync-v1"
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :sync:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.sync.work.WorkIdempotencyTest
./gradlew :sync:test :sync:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add sync/src/main/kotlin/app/openstory/sync/work/ManualStorySyncWorker.kt sync/src/main/kotlin/app/openstory/sync/work/InitialStorySyncWorker.kt sync/src/main/kotlin/app/openstory/sync/work/DeferredMappingSearchWorker.kt sync/src/main/kotlin/app/openstory/sync/work/ChapterDownloadWorker.kt sync/src/main/kotlin/app/openstory/sync/work/WorkNames.kt sync/src/androidTest/kotlin/app/openstory/sync/work/WorkIdempotencyTest.kt
git commit -m "sync: connect manual initial mapping and download work"
```

### Task 4: Implement WebView login capture with plugin-and-host-scoped session storage

**Files:**
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/auth/PluginSessionStore.kt
- Create: core/plugin-host/src/main/kotlin/app/openstory/plugin/host/auth/EncryptedPluginSessionStore.kt
- Create: feature/plugins/src/main/kotlin/app/openstory/plugins/auth/PluginLoginActivity.kt
- Create: feature/plugins/src/main/kotlin/app/openstory/plugins/auth/LoginNavigationGuard.kt
- Create: feature/plugins/src/main/kotlin/app/openstory/plugins/auth/PluginLoginViewModel.kt
- Test: core/plugin-host/src/androidTest/kotlin/app/openstory/plugin/host/auth/PluginSessionStoreTest.kt
- Test: feature/plugins/src/androidTest/kotlin/app/openstory/plugins/auth/PluginLoginActivityTest.kt

**Interfaces:**
- Consumes: Plugin manifest hosts/auth capability, scoped HTTP gateway cookie provider, Android WebView, Keystore-backed encryption.
- Produces: Explicit login flow that navigates only declared HTTPS hosts, captures exact-host session cookies after user completion, encrypts them per plugin, clears transient WebView state, and supports logout.

**Acceptance:**
- App/plugin never receives or stores plaintext username/password fields.
- Navigation to undeclared host is blocked or opened externally only after user confirmation; cookies from it are never captured.
- Stored cookie key is `(pluginId, host, cookieName, path)` and Secure/HttpOnly/expiry attributes are preserved.
- After capture, WebView cookies/storage are cleared to prevent cross-plugin leakage.
- Logout deletes encrypted session and gateway stops sending cookies immediately.

**Implementation notes:**
- Because Android WebView cookie storage is process-global, do not claim per-plugin WebView profiles. Use a single dedicated login activity, capture only declared-host cookies, then call WebStorage/CookieManager cleanup before another plugin login.
- Disable file/content access, mixed content, debugging in release, arbitrary JavaScript interfaces, downloads, popups, geolocation, and camera/mic permissions.
- Handle CAPTCHA/2FA only as the source site normally presents it; never automate bypass.

- [ ] **Step 1: Write the failing test**

Create `core/plugin-host/src/androidTest/kotlin/app/openstory/plugin/host/auth/PluginSessionStoreTest.kt`:

```kotlin
package app.openstory.plugin.host.auth

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertTrue

class PluginSessionStoreTest {
    @Test fun cookiesNeverCrossPluginBoundary() = runTest {
        val store = encryptedSessionStoreFixture()
        store.save(PluginId("plugin.a"), "source.example", listOf(sessionCookie("token", "secret")))
        assertTrue(store.cookiesFor(PluginId("plugin.b"), "source.example").isEmpty())
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:plugin-host:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.plugin.host.auth.PluginSessionStoreTest
```

Expected: **FAIL** because encrypted plugin-scoped session storage and guarded login flow are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/plugin-host/src/main/kotlin/app/openstory/plugin/host/auth/PluginSessionStore.kt`:

```kotlin
package app.openstory.plugin.host.auth

interface PluginSessionStore {
    suspend fun save(pluginId: PluginId, host: String, cookies: List<PluginSessionCookie>): AppResult<Unit>
    suspend fun cookiesFor(pluginId: PluginId, host: String): List<PluginSessionCookie>
    suspend fun clear(pluginId: PluginId): AppResult<Unit>
}

data class PluginSessionCookie(
    val name: String, val value: String, val domain: String, val path: String,
    val secure: Boolean, val httpOnly: Boolean, val expiresAtEpochMillis: Long?,
)
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:plugin-host:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.plugin.host.auth.PluginSessionStoreTest
./gradlew :core:plugin-host:connectedDebugAndroidTest :feature:plugins:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/plugin-host/src/main/kotlin/app/openstory/plugin/host/auth/PluginSessionStore.kt core/plugin-host/src/main/kotlin/app/openstory/plugin/host/auth/EncryptedPluginSessionStore.kt feature/plugins/src/main/kotlin/app/openstory/plugins/auth/PluginLoginActivity.kt feature/plugins/src/main/kotlin/app/openstory/plugins/auth/LoginNavigationGuard.kt feature/plugins/src/main/kotlin/app/openstory/plugins/auth/PluginLoginViewModel.kt core/plugin-host/src/androidTest/kotlin/app/openstory/plugin/host/auth/PluginSessionStoreTest.kt feature/plugins/src/androidTest/kotlin/app/openstory/plugins/auth/PluginLoginActivityTest.kt
git commit -m "auth: add isolated webview login sessions"
```

### Task 5: Classify and deduplicate local chapter notifications

**Files:**
- Create: sync/src/main/kotlin/app/openstory/sync/notification/ChapterNotificationClassifier.kt
- Create: sync/src/main/kotlin/app/openstory/sync/notification/NotificationEventRepository.kt
- Create: sync/src/main/kotlin/app/openstory/sync/notification/AndroidChapterNotifier.kt
- Create: app/src/main/kotlin/app/openstory/notification/NotificationChannels.kt
- Create: app/src/main/kotlin/app/openstory/notification/NotificationPermissionCoordinator.kt
- Test: sync/src/test/kotlin/app/openstory/sync/notification/ChapterNotificationClassifierTest.kt

**Interfaces:**
- Consumes: Persisted chapter change events, progress, content-language preferences, notification settings, Android notification permission/channel APIs.
- Produces: Classifier and notifier that produce one meaningful notification for new canonical chapters and optional preferred-language releases, consume events idempotently, and deep-link to story/chapter.

**Acceptance:**
- Multiple releases grouped into one new canonical chapter produce one new-chapter notification.
- Existing chapter gains preferred language: preferred-release notification only when enabled and chapter is relevant/unread.
- Same event is never notified twice across retries/process death.
- Notifications contain title/chapter/source-language labels but no chapter body.
- Denied notification permission leaves events consumed/visible in in-app activity without retry spam.

**Implementation notes:**
- Use separate channels for chapter updates and long-running downloads; users can control them independently.
- Request POST_NOTIFICATIONS contextually before enabling alerts, not on first launch.
- Use stable notification IDs from story/chapter/event kind and signed/validated deep-link arguments.

- [ ] **Step 1: Write the failing test**

Create `sync/src/test/kotlin/app/openstory/sync/notification/ChapterNotificationClassifierTest.kt`:

```kotlin
package app.openstory.sync.notification

import kotlin.test.Test
import kotlin.test.assertEquals

class ChapterNotificationClassifierTest {
    @Test fun equivalentReleasesProduceSingleNewChapterNotification() {
        val classifier = ChapterNotificationClassifier()
        val notifications = classifier.classify(
            events = listOf(newChapterEvent("c100", "rA"), newReleaseEvent("c100", "rB", language = "vi")),
            settings = notificationsEnabled(),
            progress = unreadProgress(),
        )
        assertEquals(1, notifications.count { it.kind == NotificationKind.NEW_CHAPTER })
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :sync:test --tests app.openstory.sync.notification.ChapterNotificationClassifierTest.equivalentReleasesProduceSingleNewChapterNotification
```

Expected: **FAIL** because notification classification/deduplication is not implemented.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `sync/src/main/kotlin/app/openstory/sync/notification/ChapterNotificationClassifier.kt`:

```kotlin
package app.openstory.sync.notification

class ChapterNotificationClassifier {
    fun classify(events: List<ChapterChangeEvent>, settings: NotificationSettings, progress: StoryProgress): List<PendingNotification> =
        events.groupBy { it.chapterId }.flatMap { (_, chapterEvents) ->
            val newChapter = chapterEvents.firstOrNull { it.kind == ChapterChangeKind.NEW_CHAPTER }
            when {
                newChapter != null && settings.newChapters -> listOf(PendingNotification.fromNewChapter(newChapter))
                settings.preferredLanguageReleases -> chapterEvents.filter { it.kind == ChapterChangeKind.NEW_PREFERRED_RELEASE }.take(1).map(PendingNotification::fromPreferredRelease)
                else -> emptyList()
            }
        }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :sync:test --tests app.openstory.sync.notification.ChapterNotificationClassifierTest.equivalentReleasesProduceSingleNewChapterNotification
./gradlew :sync:test :app:testDebugUnitTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add sync/src/main/kotlin/app/openstory/sync/notification/ChapterNotificationClassifier.kt sync/src/main/kotlin/app/openstory/sync/notification/NotificationEventRepository.kt sync/src/main/kotlin/app/openstory/sync/notification/AndroidChapterNotifier.kt app/src/main/kotlin/app/openstory/notification/NotificationChannels.kt app/src/main/kotlin/app/openstory/notification/NotificationPermissionCoordinator.kt sync/src/test/kotlin/app/openstory/sync/notification/ChapterNotificationClassifierTest.kt
git commit -m "notifications: add canonical chapter local alerts"
```

### Task 6: Expose synchronization, authentication, and notification controls in UI

**Files:**
- Create: feature/settings/src/main/kotlin/app/openstory/settings/sync/SyncSettingsScreen.kt
- Create: feature/settings/src/main/kotlin/app/openstory/settings/sync/SyncSettingsViewModel.kt
- Create: feature/plugins/src/main/kotlin/app/openstory/plugins/ui/PluginDetailScreen.kt
- Create: feature/plugins/src/main/kotlin/app/openstory/plugins/ui/PluginDetailViewModel.kt
- Create: feature/story/src/main/kotlin/app/openstory/story/ui/sync/StorySyncStatus.kt
- Test: feature/settings/src/test/kotlin/app/openstory/settings/sync/SyncSettingsViewModelTest.kt
- Test: feature/plugins/src/androidTest/kotlin/app/openstory/plugins/ui/PluginDetailScreenTest.kt

**Interfaces:**
- Consumes: App settings, sync scheduler/status repository, plugin diagnostics/session store/update service, notification permission coordinator.
- Produces: User controls for background cadence/constraints, notification types, per-plugin login/logout/update mode, diagnostics, and per-story manual/full refresh.

**Acceptance:**
- UI states Android may delay background work and shows last attempted/successful local checks.
- Changing cadence reschedules unique periodic work once.
- Plugin detail shows declared domains/capabilities, login state, version, update mode, last errors, rollback action.
- Manual full sync has explicit cost warning and per-plugin progress.
- No UI promises cloud/push behavior.

**Implementation notes:**
- Show WorkManager last-run status as informational; manual refresh remains available.
- Domain/capability changes during update require a dedicated review dialog with old/new diff.
- Logout warning explains offline downloads remain but future protected content refresh may fail.

- [ ] **Step 1: Write the failing test**

Create `feature/settings/src/test/kotlin/app/openstory/settings/sync/SyncSettingsViewModelTest.kt`:

```kotlin
package app.openstory.settings.sync

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncSettingsViewModelTest {
    @Test fun changingIntervalReschedulesOnce() = runTest {
        val fixture = syncSettingsViewModelFixture()
        fixture.viewModel.setIntervalHours(6)
        fixture.advanceUntilIdle()
        assertEquals(listOf(6), fixture.scheduler.requestedIntervals)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :feature:settings:test --tests app.openstory.settings.sync.SyncSettingsViewModelTest.changingIntervalReschedulesOnce
```

Expected: **FAIL** because settings/plugin/sync controls are not connected to schedulers and sessions.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `feature/settings/src/main/kotlin/app/openstory/settings/sync/SyncSettingsViewModel.kt`:

```kotlin
package app.openstory.settings.sync

@HiltViewModel
class SyncSettingsViewModel @Inject constructor(
    private val settings: AppSettingsRepository,
    private val scheduler: SyncScheduler,
) : ViewModel() {
    fun setIntervalHours(hours: Int) = viewModelScope.launch {
        settings.setSyncIntervalHours(hours)
        scheduler.reschedule(settings.settings().first())
    }
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :feature:settings:test --tests app.openstory.settings.sync.SyncSettingsViewModelTest.changingIntervalReschedulesOnce
./gradlew :feature:settings:test :feature:plugins:test :feature:plugins:connectedDebugAndroidTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add feature/settings/src/main/kotlin/app/openstory/settings/sync/SyncSettingsScreen.kt feature/settings/src/main/kotlin/app/openstory/settings/sync/SyncSettingsViewModel.kt feature/plugins/src/main/kotlin/app/openstory/plugins/ui/PluginDetailScreen.kt feature/plugins/src/main/kotlin/app/openstory/plugins/ui/PluginDetailViewModel.kt feature/story/src/main/kotlin/app/openstory/story/ui/sync/StorySyncStatus.kt feature/settings/src/test/kotlin/app/openstory/settings/sync/SyncSettingsViewModelTest.kt feature/plugins/src/androidTest/kotlin/app/openstory/plugins/ui/PluginDetailScreenTest.kt
git commit -m "settings: expose local sync auth and notification controls"
```

## Wave Checkpoint

Do not begin `2026-08-03-11-hardening-open-source-release.md` until every item below is demonstrated on a clean checkout:

- [ ] Only one periodic sync schedule exists after repeated settings changes.
- [ ] Plugin failure does not block other mappings or create retry loops.
- [ ] Cookies never cross plugin or undeclared host boundaries and are cleared on logout.
- [ ] Equivalent releases generate one chapter notification.
- [ ] Manual refresh and offline downloads remain functional when notifications are denied.

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

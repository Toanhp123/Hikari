# Wave 10 Production Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the incomplete Wave 10 prototype on `feat/wave-10-background-sync-auth-and-notifications` with production-wired, secure, bounded, recoverable background sync, plugin authentication, settings migration, Reader policy, and notification delivery.

**Architecture:** Keep the approved Wave 10 ownership model: capability modules declare narrow ports, `:app` owns Android adapters and composition, `:plugins:runtime` owns validated encrypted sessions and credential delivery, and `:storage:room` owns transactional chapter-change and notification-delivery persistence. Repair the existing branch in dependency order rather than layering compatibility wrappers over fake plugin descriptors, host-wide credentials, unbounded workers, or transient notification state.

**Tech Stack:** Kotlin 2.4.10, coroutines and Flow, Preferences DataStore, WorkManager, Android Keystore AES-GCM, guarded Android WebView, Room schema 11, Navigation 3, Hilt, Compose, Robolectric, Room instrumentation tests, API 26 and API 37 device gates.

**Spec:** `docs/superpowers/specs/2026-08-24-wave-10-clean-background-auth-notifications-design.md`

**Related baseline:** `docs/implementation/waves/wave-10-background-sync-auth-and-notifications.md`

## Status And Audit Baseline

- Review base: `b1d0c0b` (`docs: rebaseline wave 10 implementation plan`).
- Reviewed head: `f98d28b` (`feat(settings): decouple plugin auth port and complete integration gate`).
- Review scope: 114 changed files, approximately 9,314 insertions and 322 deletions.
- Fresh verification on the reviewed head: `:app:compileDebugKotlin`, selected app/settings/catalog/Reader unit-test tasks, and `verifyArchitecture` succeeded.
- Existing test reports contained 447 tests with zero failures, but the audit below identifies production wiring and behavioral gaps those tests do not exercise.
- This document is the repair source of truth. A green unit test or architecture task is not acceptance unless the production graph and device behavior named here are also verified.

## Global Constraints

- Preserve the approved 16-production-module graph; do not add a generic `:sync`, `:auth`, or `:notifications` module.
- Keep `:reader`, `:downloads`, `:chapters`, and `:plugins:runtime` independent of `:settings`.
- Keep WorkManager, WebView, permission, notification, intent, and navigation adapters in `:app`.
- Keep Room at schema 11. Because this branch is unmerged, repair `MIGRATION_10_11` and schema export `11.json`; do not create `MIGRATION_11_12` for Wave 10 defects.
- Keep stable existing work names and add exactly `library-chapter-periodic`, `library-chapter-continuation`, and `chapter-notification-drain`.
- Process at most 20 periodic Library candidates per dispatcher invocation; never serialize the complete candidate list into WorkManager `Data`.
- Re-throw `CancellationException` at every coroutine boundary.
- Credentials never enter DataStore or Room and are never stored with an APK-embedded key.
- Runtime credential delivery must validate plugin ID, full HTTPS request URL, host, normalized path, cookie allowlist, TTL, enabled state, and authentication-policy fingerprint.
- Notification evidence must commit in the same Room transaction as chapter graph changes.
- Permission denial and disabled channels transition delivery to `IN_APP_ONLY`; they do not retry or create a later historical burst.
- No task may retain fake plugin IDs, fake authenticated users, fake story/chapter deep-link targets, or test-only parsers that duplicate missing production behavior.
- Every task follows RED -> focused GREEN -> broader regression gate -> commit. Do not combine unrelated phases into one checkpoint commit.

---

## Review Finding Ledger

| ID | Severity | Finding | Evidence | Repair task |
|---|---|---|---|---|
| W10-R01 | Critical | AES key is embedded in source and recoverable from the APK. | `app/.../EncryptedSharedPreferencesCredentialStorage.kt:18` | 11 |
| W10-R02 | Critical | Credential writes use asynchronous `SharedPreferences.apply()` and are not atomic durable session commits. | `EncryptedSharedPreferencesCredentialStorage.kt:31` | 11 |
| W10-R03 | Critical | Login accepts arbitrary fields and immediately records `Authenticated` without server or completion verification. | `app/.../SettingsPluginAuthAdapter.kt:92` | 13-15 |
| W10-R04 | Critical | Hard-coded `plugin.mangadex` and `plugin.myanimelist` IDs do not match bundled package manifests. | `SettingsPluginAuthAdapter.kt:19`, bundled manifests | 10, 15 |
| W10-R05 | Critical | Saved sessions and auth state are not composed into the production HTTP capability or `auth.getState`. | `app/.../PluginRuntimeModule.kt:58`, `:80` | 12, 15 |
| W10-R06 | Critical | Managed username/password payloads are stored but ignored by the credential provider. | `plugins/runtime/.../PluginAuthCredentialProvider.kt:34` | 10, 12, 15 |
| W10-R07 | Critical | Schema 11 contains only plugin auth state and omits chapter-change outbox and notification-delivery durability. | `storage/room/.../RoomMigrations.kt:7` | 16-18 |
| W10-R08 | Critical | Notification permission, channel setup, and production deep-link parsing are absent. | `AndroidManifest.xml`, `NotificationChannelConfig.kt`, `MainActivity.kt` | 20-22 |
| W10-R09 | High | Periodic scheduling adapter has no production caller; settings changes do not update work. | `WorkManagerPeriodicSyncScheduler.kt:15` and repo-wide caller search | 5, 8 |
| W10-R10 | High | Production marks every plugin source protected, so unauthenticated default state skips mapped stories. | `app/.../BackgroundWorkModule.kt:71` | 8-9 |
| W10-R11 | High | The implementation conflates auth-protected sources with user-protected content mappings. | `ContentMappingModels.kt:12`, `BackgroundWorkModule.kt:71` | 8-10 |
| W10-R12 | High | Periodic work scans the whole Library sequentially with no due-time ordering, batch limit, cursor, continuation, or starvation control. | `catalog/.../PeriodicCatalogSyncEngine.kt:40` | 6-8 |
| W10-R13 | High | `newChaptersDetected` counts fetched releases, including existing releases, and can notify repeatedly. | `BackgroundWorkModule.kt:57`, `ChapterPageSynchronizer.kt:75` | 9, 16-19 |
| W10-R14 | High | Worker emits fake aggregate targets `sync-update` and `latest`. | `PeriodicCatalogSyncWorker.kt:56` | 19-22 |
| W10-R15 | High | Engine and worker swallow coroutine cancellation. | `PeriodicCatalogSyncEngine.kt:72`, `PeriodicCatalogSyncWorker.kt:68` | 8-9, 22 |
| W10-R16 | High | Credential provider ignores host/path scope and may leak cookies across allowed redirect hosts; header ownership collisions are unchecked. | `PluginAuthCredentialProvider.kt:15`, `PluginHttpCapability.kt:78` | 10, 12 |
| W10-R17 | Medium | Settings migration helpers are not registered with DataStore; corruption fallback does not repair corrupt storage. | `SettingsModule.kt:33`, `SettingsDataMigration.kt` | 3 |
| W10-R18 | High | Reader can load before the first preference emission and select the wrong language release. | `feature/reader/.../ReaderViewModel.kt:60`, `:68` | 4 |
| W10-R19 | Medium | Reader keeps an optimistic font value after persistence failure and swallows cancellation. | `ReaderViewModel.kt:203` | 4 |
| W10-R20 | High | Notification/PendingIntent IDs use unchecked XOR hashes; permission denial silently drops events. | `StoryNotificationBuilder.kt:28`, `NotificationDispatcher.kt:11` | 18, 21-22 |

## False-Confidence Tests To Replace

- `NotificationDeepLinkRoutingTest` currently tests a private parser implemented inside the test. It must call the production intent parser and current-target validator.
- `CredentialStorageSecurityTest` proves only that raw text is not visible. It must prove Android Keystore ownership, AAD binding, atomic replacement, key invalidation, tamper handling, and no-backup location.
- `WorkManagerPeriodicSyncSchedulerTest` checks a constant and default values. It must inspect actual unique work registration, constraints, update/cancel behavior, and stable names.
- `PeriodicCatalogSyncEngineTest` explicitly expects all Library stories to be processed. Replace that contract with a 20-item bounded planner and continuation behavior.
- `PeriodicCatalogSyncAuthGatingTest` uses synthetic protected/public plugins and does not exercise installed manifest auth policy or source-level eligibility.
- Settings migration tests call `migrateLegacyMap()` directly. Add a DataStore integration test proving the registered migration runs against real legacy storage once.
- Notification privacy tests inspect `toString()` only. Add notifier tests proving no body/credential content enters title, text, extras, diagnostics, or persisted delivery rows.

---

## Phase 0 - Freeze The Defect Baseline

### Task 1: Add production-graph regression tests for the reviewed failures

**Files:**
- Create: `app/src/test/kotlin/app/openstory/di/Wave10ProductionGraphTest.kt`
- Create: `app/src/test/kotlin/app/openstory/work/Wave10WorkRegistrationContractTest.kt`
- Create: `app/src/test/kotlin/app/openstory/notifications/Wave10NotificationEntryContractTest.kt`
- Modify: `app/src/test/kotlin/app/openstory/navigation/NotificationDeepLinkRoutingTest.kt`

**Interfaces:**
- Consumes: current Hilt modules, Android manifest, `MainActivity`, work-name constants, bundled plugin descriptors.
- Produces: failing tests that prove the production graph is missing auth composition, periodic registration, notification entry handling, and real plugin identity.

- [ ] Assert bundled auth summaries are derived from installed manifest IDs and never contain `plugin.mangadex` or `plugin.myanimelist`.
- [ ] Assert the production `ManagedCredentialProvider` is a collision-checking composition containing the static MAL provider and runtime session provider.
- [ ] Assert application startup registers notification channels, notification recovery work, and one periodic registration from persisted settings.
- [ ] Replace the private deep-link parser in `NotificationDeepLinkRoutingTest` with calls to the production parser introduced in Task 20; keep this test RED until Task 20.
- [ ] Run `./gradlew :app:testDebugUnitTest --tests '*Wave10*' --tests '*NotificationDeepLinkRoutingTest'` and record the expected missing-binding/missing-entry failures.
- [ ] Commit the RED contract separately: `test(wave10): expose production wiring gaps`.

### Task 2: Add structural policy checks against prototype shortcuts

**Files:**
- Create: `scripts/check-wave-10-production-policy.sh`
- Create: `scripts/tests/check-wave-10-production-policy-test.sh`
- Modify: `scripts/verify.sh`

**Interfaces:**
- Consumes: production source tree and manifest/resource files.
- Produces: a fast structural gate rejecting embedded AES byte arrays, fake plugin IDs, fake notification targets, `protectedSourceChecker = { true }`, and test-local deep-link parsers.

- [ ] Write shell fixtures that demonstrate one failure for each prohibited shortcut.
- [ ] Implement exact, path-scoped checks; exclude generated/build output and test fixtures unless the rule specifically targets a false-confidence test.
- [ ] Add the policy check to `scripts/verify.sh` before Gradle verification.
- [ ] Run `bash scripts/tests/check-wave-10-production-policy-test.sh`; expect all policy fixtures to pass.
- [ ] Run `bash scripts/check-wave-10-production-policy.sh`; expect failure on the reviewed head until later tasks remove every shortcut.
- [ ] Commit: `build: guard wave 10 production wiring`.

---

## Phase 1 - Settings And Consumer-Port Correctness

### Task 3: Register real settings migration and corruption recovery

**Files:**
- Modify: `settings/src/main/kotlin/app/openstory/settings/SettingsDataMigration.kt`
- Modify: `settings/src/main/kotlin/app/openstory/settings/AppSettings.kt`
- Modify: `settings/src/main/kotlin/app/openstory/settings/SettingsDefaults.kt`
- Modify: `settings/src/main/kotlin/app/openstory/settings/SettingsPreferenceCodec.kt`
- Create: `app/src/main/kotlin/app/openstory/settings/LegacySettingsDataMigration.kt`
- Create: `app/src/main/kotlin/app/openstory/settings/SettingsCorruptionHandler.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/SettingsModule.kt`
- Create: `app/src/test/kotlin/app/openstory/settings/SettingsDataStoreIntegrationTest.kt`

**Interfaces:**
- Produces: `LegacySettingsDataMigration : DataMigration<Preferences>` and a corruption handler that returns encoded safe defaults and emits only redacted diagnostic codes.

- [ ] Write integration tests that create legacy storage, open production DataStore, verify every field migrates once, then verify a second open does not rerun migration.
- [ ] Write a corrupt-file test that verifies all defaults are emitted, the corrupt bytes are replaced, and later writes succeed.
- [ ] Add explicit `periodicChapterChecksEnabled: Boolean = true` settings policy. The approved design requires disable cancellation but the reviewed model has no disable field; migrate existing installs to enabled so behavior is preserved.
- [ ] Register `migrations = listOf(LegacySettingsDataMigration(...))` and `corruptionHandler = SettingsCorruptionHandler(...)` in `PreferenceDataStoreFactory.create`.
- [ ] Ensure per-field decode failures retain other valid fields and never catch `CancellationException`.
- [ ] Run `./gradlew :settings:testDebugUnitTest :app:testDebugUnitTest --tests '*SettingsDataStoreIntegrationTest'`.
- [ ] Commit: `fix(settings): register migration and corruption recovery`.

### Task 4: Serialize Reader initialization with the first preference snapshot

**Files:**
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelTest.kt`

**Interfaces:**
- Consumes: `ReaderPreferencesPort.preferences`.
- Produces: one initialization coroutine that receives the first normalized preference value before initial document selection, then continues collecting live changes.

- [ ] Add a test whose preference Flow is initially suspended; assert no document load occurs before the first emission.
- [ ] Emit a non-default language order and assert the first `ReleaseSelectionPolicy` uses it.
- [ ] Add a persistence-failure test asserting font scale returns to the last persisted value and exposes a bounded UI error code.
- [ ] Implement initialization with `val initial = preferences.first()` before `load()`, then start live collection without double-applying the first value.
- [ ] Catch and rethrow `CancellationException`; on other write failures restore `currentPreferences.normalizedFontScale`.
- [ ] Run `./gradlew :feature:reader:testDebugUnitTest --tests '*ReaderViewModelTest'`.
- [ ] Commit: `fix(reader): initialize from persisted preferences`.

### Task 5: Add a distinct background policy coordinator

**Files:**
- Create: `settings/src/main/kotlin/app/openstory/settings/background/BackgroundWorkSchedulePort.kt`
- Create: `settings/src/main/kotlin/app/openstory/settings/background/BackgroundPolicyCoordinator.kt`
- Create: `app/src/main/kotlin/app/openstory/work/SettingsBackgroundWorkScheduleAdapter.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/SettingsModule.kt`
- Modify: `app/src/main/kotlin/app/openstory/OpenStoryApplication.kt`
- Test: `settings/src/test/kotlin/app/openstory/settings/background/BackgroundPolicyCoordinatorTest.kt`

**Interfaces:**

```kotlin
interface BackgroundWorkSchedulePort {
    suspend fun apply(policy: BackgroundWorkPolicy)
}

data class BackgroundWorkPolicy(
    val enabled: Boolean,
    val cadenceHours: Int,
    val requireUnmeteredNetwork: Boolean,
    val requireBatteryNotLow: Boolean,
)
```

- [ ] Test distinct policy projection: identical settings emissions cause one adapter call; cadence/network/battery changes cause exactly one update.
- [ ] Test disabling cancels only periodic dispatcher and continuation names.
- [ ] Implement coordinator collection in an application-scoped `SupervisorJob + AppDispatchers.io` scope.
- [ ] Start the coordinator from `OpenStoryApplication.onCreate()` after Hilt injection; do not start it from a Compose screen.
- [ ] Run `./gradlew :settings:testDebugUnitTest :app:testDebugUnitTest --tests '*BackgroundPolicyCoordinator*'`.
- [ ] Commit: `feat(settings): coordinate background policy`.

---

## Phase 2 - Bounded Periodic Chapter Dispatch

### Task 6: Introduce candidate snapshots, due-time ordering, and cursor codec

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncCandidate.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncBatchPlanner.kt`
- Create: `app/src/main/kotlin/app/openstory/work/ChapterSyncCursorCodec.kt`
- Test: `chapters/src/test/kotlin/app/openstory/chapters/sync/ChapterSyncBatchPlannerTest.kt`
- Test: `app/src/test/kotlin/app/openstory/work/ChapterSyncCursorCodecTest.kt`

**Interfaces:**

```kotlin
data class ChapterSyncCandidate(
    val storyId: StoryId,
    val lastSuccessfulSyncAtEpochMillis: Long?,
)

data class ChapterSyncBatchCursor(
    val timestampBucket: Long?,
    val storyId: StoryId,
)

data class ChapterSyncBatch(
    val selected: List<ChapterSyncCandidate>,
    val continuation: ChapterSyncBatchCursor?,
)
```

- [ ] Write RED tests for null timestamp first, oldest timestamp next, Story ID tie-break, exact 20-item bound, cursor continuation, empty batch, and no starvation after an early failure.
- [ ] Implement a pure stable sort and selection; never include plugin credentials, mapping lists, or the full Library snapshot in the cursor.
- [ ] Encode cursor as versioned bounded JSON/Base64 and reject blank, oversized, unknown-version, negative-timestamp, or invalid-ID input.
- [ ] Run `./gradlew :chapters:testDebugUnitTest --tests '*ChapterSyncBatchPlannerTest' :app:testDebugUnitTest --tests '*ChapterSyncCursorCodecTest'`.
- [ ] Commit: `feat(sync): plan bounded periodic chapter batches`.

### Task 7: Expose eligible candidates through a capability-owned reader

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncCandidateSource.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomChapterSyncCandidateSource.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/library/LibraryDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterDao.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/RoomChapterSyncCandidateSourceTest.kt`

**Interfaces:**

```kotlin
fun interface ChapterSyncCandidateSource {
    suspend fun eligibleCandidates(): List<ChapterSyncCandidate>
}
```

- [ ] Seed stories with no sync state, successful old/new states, removed Library rows, and multiple mappings.
- [ ] Assert one row per current Library story and `MIN(success timestamps)` or null when never successful.
- [ ] Implement one indexed Room query rather than `library.observe().first()` plus N mapping/auth reads.
- [ ] Verify deterministic output after story redirects and deletion.
- [ ] Run the connected test on the available device/emulator.
- [ ] Commit: `storage: expose periodic chapter candidates`.

### Task 8: Replace the unbounded engine with dispatcher, continuation, and story work

**Files:**
- Remove: `catalog/src/main/kotlin/app/openstory/catalog/sync/PeriodicCatalogSyncEngine.kt`
- Replace: `app/src/main/kotlin/app/openstory/work/PeriodicCatalogSyncWorker.kt`
- Create: `app/src/main/kotlin/app/openstory/work/PeriodicChapterDispatchWorker.kt`
- Create: `app/src/main/kotlin/app/openstory/work/PeriodicChapterContinuationWorker.kt`
- Create: `app/src/main/kotlin/app/openstory/work/WorkNames.kt`
- Create: `app/src/main/kotlin/app/openstory/work/WorkInput.kt`
- Modify: `app/src/main/kotlin/app/openstory/work/InitialChapterSyncWorker.kt`
- Modify: `app/src/main/kotlin/app/openstory/work/WorkManagerPeriodicSyncScheduler.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/BackgroundWorkModule.kt`

**Interfaces:**
- Dispatcher: snapshot -> plan at most 20 -> enqueue stable story work -> enqueue one continuation when present.
- Existing story-specific chapter worker: deserialize one `StoryId` -> call `ChapterSyncService.sync(storyId)` -> translate retry classification for both initial and periodic triggers.

- [ ] Replace tests that expect all stories to execute with tests for 20-item dispatch, continuation cursor, unique per-story names, enqueue failure isolation, and stable names.
- [ ] Use exactly `library-chapter-periodic` and `library-chapter-continuation`; enqueue the frozen existing `initial-chapter-sync:<storyId>` work with `KEEP` rather than creating a second story worker/name.
- [ ] Re-throw cancellation. Retry only retryable global/worker conditions; do not retry a dispatcher because one story fails.
- [ ] Remove `protectedSourceChecker = { true }`, per-story Flow `first()` calls, and aggregate fake notification dispatch from the worker.
- [ ] Run `./gradlew :catalog:testDebugUnitTest :app:testDebugUnitTest --tests '*Periodic*'`.
- [ ] Commit: `feat(sync): dispatch bounded resumable chapter work`.

### Task 9: Separate authentication eligibility from mapping protection and emit real change facts

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSourceEligibility.kt`
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncService.kt`
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterPageSynchronizer.kt`
- Test: `chapters/src/test/kotlin/app/openstory/chapters/sync/ChapterSyncEligibilityTest.kt`

**Interfaces:**

```kotlin
data class ChapterSourceEligibility(
    val pluginId: PluginId,
    val sourceStoryId: String,
    val allowed: Boolean,
    val denialCode: String?,
)
```

- [ ] Test that `ContentMappingOrigin.isProtected` continues to protect user mapping ownership but does not imply login is required.
- [ ] Test a mixed story where an unauthenticated protected source is skipped while eligible public sources still sync.
- [ ] Remove `releaseCount` and `newChaptersDetected` as notification inputs. Notification facts are produced only inside the Room chapter commit transaction in Phase 4.
- [ ] Preserve per-source failure isolation and rethrow cancellation from service and page synchronizer.
- [ ] Run `./gradlew :chapters:testDebugUnitTest`.
- [ ] Commit: `fix(chapters): separate auth eligibility from mapping policy`.

---

## Phase 3 - Manifest-Owned Authentication And Secure Sessions

### Task 10: Add validated optional authentication manifest policy

**Files:**
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/manifest/PluginManifest.kt`
- Create: `plugins/api/src/main/kotlin/app/openstory/plugins/api/manifest/PluginAuthenticationCapability.kt`
- Inspect and modify only after real-flow verification: `bundled-plugins/mangadex-content/manifest.json`
- Inspect and modify only after real-flow verification: `bundled-plugins/myanimelist-catalog/manifest.json`
- Test: `plugins/api/src/test/kotlin/app/openstory/plugins/api/manifest/PluginAuthenticationCapabilityTest.kt`
- Create: `docs/plugin-sdk/authentication.md`
- Modify: `docs/plugin-sdk/package-format.md`
- Modify: `docs/plugin-sdk/javascript-runtime.md`
- Modify: `docs/plugin-sdk/api-versioning.md`
- Modify: `docs/plugin-sdk/repository-index.md`

**Interfaces:**

```kotlin
@Serializable
data class PluginAuthenticationCapability(
    val loginStartUrl: String,
    val navigationHosts: Set<String>,
    val completion: PluginAuthenticationCompletionTarget,
    val credentialTargets: List<PluginAuthenticationCredentialTarget>,
    val sessionTtlSeconds: Long,
)

@Serializable
data class PluginAuthenticationCompletionTarget(
    val host: String,
    val pathPrefix: String,
)

@Serializable
data class PluginAuthenticationCredentialTarget(
    val host: String,
    val pathPrefix: String,
    val cookieNames: Set<String>,
)
```

- [ ] Test safe default absent and rejection of cleartext, wildcard, user-info, arbitrary port, non-normalized path, target host outside network capability, empty cookie allowlist, and unbounded TTL.
- [ ] Compute a canonical authentication-policy fingerprint from normalized fields.
- [ ] Remove the prototype generic `ManagedCredentials`, arbitrary form fields, OAuth token payload, completion header capture, and persisted arbitrary headers. Wave 10 supports only manifest-bounded WebView cookie capture.
- [ ] Do not add auth declarations to MangaDex/MAL until the actual guarded flow and credential target are known and integration-tested; UI must show only installed packages that declare auth.
- [ ] Document forward-host compatibility limits for older hosts parsing new manifest fields.
- [ ] Run `./gradlew :plugins:api:test`.
- [ ] Commit: `feat(plugins): declare bounded authentication policy`.

### Task 11: Replace SharedPreferences encryption with atomic Keystore session storage

**Files:**
- Remove: `app/src/main/kotlin/app/openstory/plugins/runtime/auth/EncryptedSharedPreferencesCredentialStorage.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/auth/PluginSessionStore.kt`
- Create: `app/src/main/kotlin/app/openstory/plugins/runtime/auth/AndroidKeystorePluginSessionStore.kt`
- Create: `app/src/androidTest/kotlin/app/openstory/plugins/runtime/auth/AndroidKeystorePluginSessionStoreTest.kt`

**Interfaces:**

```kotlin
interface PluginSessionStore {
    suspend fun readAll(pluginId: PluginId): List<PluginSessionRecord>
    suspend fun replaceAll(pluginId: PluginId, records: List<PluginSessionRecord>)
    suspend fun clear(pluginId: PluginId)
}

data class PluginSessionRecord(
    val pluginId: PluginId,
    val targetHost: String,
    val targetPathPrefix: String,
    val cookieName: String,
    val cookieValue: SecretCookieValue,
    val createdAtEpochMillis: Long,
    val expiresAtEpochMillis: Long,
    val authenticationPolicyFingerprint: String,
)

@JvmInline
value class SecretCookieValue private constructor(val raw: String) {
    override fun toString(): String = "<redacted>"

    companion object {
        fun of(raw: String): SecretCookieValue {
            require(raw.isNotBlank() && raw.none(Char::isISOControl))
            return SecretCookieValue(raw)
        }
    }
}
```

- [ ] Test storage under `noBackupFilesDir`, temporary-file write, fsync/close, atomic replace, and no plaintext secret in any file.
- [ ] Generate a non-exportable Android Keystore AES/GCM key; remove every literal key byte from source.
- [ ] Bind plugin ID, target host, normalized path, cookie name, record version, and policy fingerprint as AAD.
- [ ] Test ciphertext tamper, swapped plugin files, changed path, key invalidation, and interrupted replacement; each affected session must fail closed and clear safely.
- [ ] Run connected tests on API 26 and API 37.
- [ ] Commit: `security(auth): store sessions with Android Keystore`.

### Task 12: Upgrade managed credentials to validated request targets and collision-safe composition

**Files:**
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/ManagedCredentialProvider.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/PluginHttpCapability.kt`
- Replace: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/auth/PluginAuthCredentialProvider.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/CompositeManagedCredentialProvider.kt`
- Modify: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/capabilities/http/PluginHttpCapabilityTest.kt`
- Create: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/capabilities/http/CompositeManagedCredentialProviderTest.kt`
- Create: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/auth/PluginSessionManagedCredentialProviderTest.kt`

**Interfaces:**

```kotlin
data class ManagedCredentialRequest(val pluginId: PluginId, val url: String)

fun interface ManagedCredentialProvider {
    suspend fun headers(request: ManagedCredentialRequest): Map<String, String>
}
```

- [ ] Write RED tests for same host/different path, cross-host redirect, expired TTL, disabled plugin, changed policy fingerprint, undeclared cookie, and mixed-case header collision.
- [ ] Validate the full current URL before credential lookup on the initial request and every redirect.
- [ ] Session provider may return only `Cookie`; remove arbitrary persisted header injection and the unused generic `Credentials` payload path.
- [ ] Composite providers in fixed order and reject duplicate header ownership case-insensitively instead of overwriting with `staticHeaders + authHeaders`.
- [ ] Run `./gradlew :plugins:runtime:testDebugUnitTest`.
- [ ] Commit: `security(runtime): scope managed credentials to requests`.

### Task 13: Implement runtime session lifecycle and package invalidation

**Files:**
- Remove: `plugins/api/src/main/kotlin/app/openstory/plugins/api/auth/PluginAuthModels.kt`
- Remove: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/auth/PluginAuthStateRepository.kt`
- Remove: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/auth/PluginAuthStateEngine.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/auth/PluginSessionModels.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/auth/PluginSessionService.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/update/PluginUpdateService.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/install/PluginInstaller.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/DefaultPluginRuntime.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/auth/PluginSessionServiceTest.kt`

**Interfaces:**
- Produces: observe summaries, complete verified login, logout, expire, invalidate-on-policy-change, and deny-while-disabled operations.

```kotlin
enum class PluginSessionStatus { LOGGED_OUT, AUTHENTICATED, EXPIRED }

data class PluginSessionSummary(
    val pluginId: PluginId,
    val status: PluginSessionStatus,
    val expiresAtEpochMillis: Long?,
)

interface PluginSessionService {
    fun observeInstalledSessions(): Flow<List<PluginSessionSummary>>
    suspend fun completeVerifiedLogin(
        pluginId: PluginId,
        authenticationPolicyFingerprint: String,
        records: List<PluginSessionRecord>,
    ): PluginSessionSummary
    suspend fun logout(pluginId: PluginId)
    suspend fun sessionFor(request: ManagedCredentialRequest): List<PluginSessionRecord>
}
```

- [ ] Test package update/rollback with same fingerprint retains session; changed fingerprint clears it.
- [ ] Test disablement blocks delivery immediately without requiring file deletion; logout deletes all plugin records.
- [ ] Move host-owned authenticated/expired/logged-out summaries out of `:plugins:api`; the plugin API retains only the manifest authentication declaration from Task 10.
- [ ] Reconcile state and encrypted record after interrupted completion so `Authenticated` is never exposed without a readable matching session.
- [ ] Keep secret payloads out of Room auth summaries and diagnostics.
- [ ] Run focused runtime tests.
- [ ] Commit: `feat(auth): manage plugin session lifecycle`.

### Task 14: Add serialized guarded WebView login capture

**Files:**
- Create: `app/src/main/kotlin/app/openstory/auth/PluginLoginActivity.kt`
- Create: `app/src/main/kotlin/app/openstory/auth/PluginLoginNavigationPolicy.kt`
- Create: `app/src/main/kotlin/app/openstory/auth/PluginLoginCoordinator.kt`
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `app/src/test/kotlin/app/openstory/auth/PluginLoginNavigationPolicyTest.kt`
- Create: `app/src/androidTest/kotlin/app/openstory/auth/PluginLoginActivityTest.kt`

**Interfaces:**
- Consumes: installed manifest auth policy and `PluginSessionService`.
- Produces: success only after exact completion host/path and declared cookie capture.

- [ ] Test a global mutex/serialization rule so only one login capture runs at once.
- [ ] Clear cookies, WebStorage, cache, history, form data, and transient state before and after every run.
- [ ] Disable file/content access, mixed content, popups, downloads, geolocation, media capture, release debugging, and JavaScript interfaces.
- [ ] Block every navigation outside declared HTTPS hosts; capture only declared cookie names for declared credential target URLs.
- [ ] Never mark authenticated from arbitrary form fields or an empty OAuth map.
- [ ] Run API 26 and API 37 tests.
- [ ] Commit: `feat(auth): add guarded plugin login capture`.

### Task 15: Wire real installed-plugin auth into production and Settings

**Files:**
- Replace: `app/src/main/kotlin/app/openstory/settings/SettingsPluginAuthAdapter.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/PluginRuntimeModule.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/SettingsModule.kt`
- Modify: `feature/settings/src/main/kotlin/app/openstory/settings/ui/SettingsViewModel.kt`
- Create: `app/src/test/kotlin/app/openstory/di/PluginAuthProductionGraphTest.kt`
- Create: `app/src/test/kotlin/app/openstory/settings/SettingsPluginSessionAdapterTest.kt`
- Modify: `feature/settings/src/test/kotlin/app/openstory/settings/ui/SettingsViewModelTest.kt`

**Interfaces:**
- Settings sees only installed package summaries and login/logout commands; runtime owns descriptors and secret lifecycle.

- [ ] Delete hard-coded supported plugin descriptors and default fake UI plugins.
- [ ] List only installed, enabled packages whose validated manifest declares authentication.
- [ ] Compose static MAL credentials and session credentials through `CompositeManagedCredentialProvider`; pass auth state/session summary access into `CapabilityBroker` production construction.
- [ ] Keep login UI open on cancel/failure, honor Boolean/result values, expose stable redacted error codes, and rethrow cancellation.
- [ ] Prove a real runtime HTTP request receives only the target-scoped cookie and `auth.getState` matches session summary.
- [ ] Commit: `fix(auth): wire installed sessions into runtime`.

---

## Phase 4 - Transactional Chapter-Change Evidence

### Task 16: Define raw change facts and a pure pre/post graph detector

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/notification/ChapterChangeModels.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/notification/ChapterChangeDetector.kt`
- Test: `chapters/src/test/kotlin/app/openstory/chapters/notification/ChapterChangeDetectorTest.kt`

**Interfaces:**
- Produces deterministic facts for new canonical chapter, new release link, and tombstone restoration, including a deterministic event key based on story/chapter/release/change/fingerprint.

```kotlin
enum class ChapterChangeKind {
    CANONICAL_CHAPTER_CREATED,
    RELEASE_LINKED,
    CANONICAL_CHAPTER_RESTORED,
}

data class ChapterChangeFact(
    val eventKey: String,
    val storyId: StoryId,
    val chapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId?,
    val kind: ChapterChangeKind,
    val chapterCommitFingerprint: String,
    val occurredAtEpochMillis: Long,
)
```

- [ ] Test retry idempotency, no-op commit, release relink, tombstone restoration, deletion/tombstone, and stable ordering.
- [ ] Keep detector free of settings, read progress, Android notification policy, and Room types.
- [ ] Run `./gradlew :chapters:testDebugUnitTest --tests '*ChapterChangeDetectorTest'`.
- [ ] Commit: `feat(chapters): detect raw chapter changes`.

### Task 17: Repair schema 11 for event and delivery durability

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/RoomMigrations.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterChangeEventEntity.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/NotificationDeliveryEntity.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/NotificationEventDao.kt`
- Remove: `storage/room/src/main/kotlin/app/openstory/storage/room/auth/PluginAuthStateEntity.kt`
- Remove: `storage/room/src/main/kotlin/app/openstory/storage/room/auth/PluginAuthStateDao.kt`
- Remove: `storage/room/src/main/kotlin/app/openstory/storage/room/auth/RoomPluginAuthStateRepository.kt`
- Replace: `storage/room/schemas/app.openstory.storage.room.OpenStoryDatabase/11.json`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/Wave10Migration10To11Test.kt`

**Interfaces:**
- Schema 11 contains raw chapter-change events and notification-delivery rows with claim token/expiry, collision-safe notification ID, terminal status, and required indexes. Plugin authentication summaries come from the runtime-owned encrypted session service, not Room.

- [ ] Start the migration test from exported schema 10 populated with canonical queue leases/outbox, Library, mappings, chapters, progress, downloads, and plugin state.
- [ ] Assert every schema-10 row survives and only Wave 10 tables/indexes are added.
- [ ] Add unique event-key and notification-ID constraints plus pending-order, claim-expiry, and story/chapter status indexes.
- [ ] Keep story/chapter/release identifiers as raw fact columns without graph foreign keys so deletion can be classified as stale and consumed. Make each delivery row reference its event with `ON DELETE CASCADE`; keep one delivery row per event and a unique nullable platform notification ID.
- [ ] Remove the reviewed `plugin_auth_states` table, entity, DAO, repository, and production binding before regenerating schema 11.
- [ ] Regenerate schema 11; do not add schema 12.
- [ ] Run migration instrumentation tests on API 26 and API 37.
- [ ] Commit: `storage: repair schema 11 notification state`.

### Task 18: Commit chapter graph and raw evidence atomically; add claim recovery

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomChapterRepository.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/notification/NotificationEventRepository.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomNotificationEventRepository.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/RoomChapterNotificationTransactionTest.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/RoomNotificationEventRepositoryTest.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/notification/NotificationDrainScheduler.kt`

**Interfaces:**
- Produces bounded claim, stale reclaim, mark delivered, mark `IN_APP_ONLY`, consume, release, and collision-safe ID allocation.

```kotlin
interface NotificationEventRepository {
    suspend fun claim(limit: Int, nowEpochMillis: Long, leaseMillis: Long): NotificationEventClaim?
    suspend fun markDelivered(claimToken: String, eventId: Long, notificationId: Int)
    suspend fun markInAppOnly(claimToken: String, eventId: Long, reasonCode: String)
    suspend fun consume(claimToken: String, eventId: Long, reasonCode: String)
    suspend fun release(claimToken: String, eventId: Long, retryAtEpochMillis: Long, errorCode: String)
    suspend fun reclaimStale(nowEpochMillis: Long): Int
}

data class NotificationEventClaim(
    val token: String,
    val events: List<PendingChapterChangeEvent>,
    val expiresAtEpochMillis: Long,
)

data class PendingChapterChangeEvent(
    val eventId: Long,
    val fact: ChapterChangeFact,
    val attemptCount: Int,
)
```

- [ ] Test graph plus events commit together and forced event insertion failure rolls back the graph.
- [ ] Test repeated chapter commit creates no duplicate event.
- [ ] Test competing claimers, token mismatch, stale reclaim, process death before/after publish decision, terminal idempotency, and bounded order.
- [ ] Schedule a best-effort drain wake only after transaction success; scheduling failure must leave pending durable rows.
- [ ] Run connected Room tests.
- [ ] Commit: `storage: commit and claim chapter notifications`.

---

## Phase 5 - Notification Classification, Routing, And Delivery

### Task 19: Classify real events against current graph, policy, language, and progress

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/notification/ChapterNotificationClassifier.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/notification/ChapterNotificationModels.kt`
- Create: `chapters/src/test/kotlin/app/openstory/chapters/notification/ChapterNotificationClassifierTest.kt`

**Interfaces:**
- Produces at most one new-chapter notification per canonical chapter and optionally one preferred-language release notification when no new-chapter notification covers the same group.

```kotlin
sealed interface ChapterNotificationDecision {
    data class Publish(val candidate: ChapterNotificationCandidate) : ChapterNotificationDecision
    data class Consume(val reasonCode: String) : ChapterNotificationDecision
}

data class ChapterNotificationCandidate(
    val storyId: StoryId,
    val chapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId?,
    val languageTag: String?,
)
```

- [ ] Test new chapter, preferred release, duplicate releases, read chapter, tombstoned/deleted/stale target, redirect resolution, language fallback, disabled categories, and unresolved target consumption.
- [ ] Remove aggregate `newChaptersDetected` notification logic completely.
- [ ] Keep user-facing classification outside Room transactions.
- [ ] Commit: `feat(notifications): classify durable chapter changes`.

### Task 20: Build one production deep-link parser and target validator

**Files:**
- Create: `app/src/main/kotlin/app/openstory/notifications/NotificationDeepLinkFactory.kt`
- Create: `app/src/main/kotlin/app/openstory/navigation/NotificationIntentParser.kt`
- Modify: `app/src/main/kotlin/app/openstory/MainActivity.kt`
- Modify: `app/src/main/kotlin/app/openstory/ui/OpenStoryApp.kt`
- Replace: `app/src/test/kotlin/app/openstory/navigation/NotificationDeepLinkRoutingTest.kt`
- Create: `app/src/test/kotlin/app/openstory/notifications/NotificationDeepLinkFactoryTest.kt`

**Interfaces:**
- Parses untrusted string extras into stable IDs, resolves redirects/current graph, and returns `AppRoute.Reader`, `AppRoute.Story`, or null.

```kotlin
fun interface NotificationIntentParser {
    suspend fun route(intent: Intent): AppRoute?
}

data class ValidatedChapterNotificationTarget(
    val storyId: StoryId,
    val chapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId?,
    val storyTitle: String,
    val chapterLabel: String,
    val languageTag: String?,
    val contentIntent: PendingIntent,
)
```

- [ ] Test valid story/chapter/release, story-only fallback, malformed IDs, missing story/chapter, stale redirect, and forged extras.
- [ ] Use the same parser on cold start and `onNewIntent`; update navigation without recreating test-only logic.
- [ ] PendingIntent must be explicit, immutable, and contain only stable identifiers.
- [ ] Delete `sync-update` and `latest` targets.
- [ ] Run navigation unit and instrumentation tests.
- [ ] Commit: `fix(nav): validate notification deep links`.

### Task 21: Register channels, permission flow, and collision-safe platform IDs

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Replace: `app/src/main/kotlin/app/openstory/notifications/NotificationChannelConfig.kt`
- Replace: `app/src/main/kotlin/app/openstory/notifications/NotificationPermissionGate.kt`
- Replace: `app/src/main/kotlin/app/openstory/notifications/StoryNotificationBuilder.kt`
- Replace: `app/src/main/kotlin/app/openstory/notifications/NotificationDispatcher.kt`
- Modify: `app/src/main/kotlin/app/openstory/OpenStoryApplication.kt`
- Modify: `app/src/test/kotlin/app/openstory/notifications/NotificationChannelConfigTest.kt`
- Modify: `app/src/test/kotlin/app/openstory/notifications/NotificationPermissionGateTest.kt`
- Modify: `app/src/test/kotlin/app/openstory/notifications/StoryNotificationBuilderTest.kt`
- Modify: `app/src/test/kotlin/app/openstory/notifications/NotificationPrivacyAuditTest.kt`

**Interfaces:**
- Consumes persisted delivery ID and validated PendingIntent; returns `PUBLISHED`, `IN_APP_ONLY`, or redacted retryable platform failure.

```kotlin
sealed interface PlatformNotificationResult {
    data object Published : PlatformNotificationResult
    data class InAppOnly(val reasonCode: String) : PlatformNotificationResult
    data class RetryableFailure(val errorCode: String) : PlatformNotificationResult
}

fun interface AndroidChapterNotifier {
    suspend fun publish(
        notificationId: Int,
        target: ValidatedChapterNotificationTarget,
    ): PlatformNotificationResult
}
```

- [ ] Declare `POST_NOTIFICATIONS`; create channels at application startup on API 26+.
- [ ] Add UI-triggered permission request without requesting from a background worker.
- [ ] Persist or collision-resolve notification IDs; remove XOR hash IDs for notifications and PendingIntents.
- [ ] Treat denied permission or disabled channel as `IN_APP_ONLY`, not retry.
- [ ] Verify title/text/extras exclude chapter body, credentials, tokens, cookies, and exception messages.
- [ ] Run API 26 and API 37 tests.
- [ ] Commit: `feat(notifications): add permission-aware platform delivery`.

### Task 22: Implement the unique durable notification drain and startup recovery

**Files:**
- Create: `app/src/main/kotlin/app/openstory/notifications/NotificationDeliveryWorker.kt`
- Create: `app/src/main/kotlin/app/openstory/notifications/WorkManagerNotificationDrainScheduler.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/BackgroundWorkModule.kt`
- Modify: `app/src/main/kotlin/app/openstory/OpenStoryApplication.kt`
- Create: `app/src/test/kotlin/app/openstory/notifications/NotificationDeliveryWorkerTest.kt`
- Create: `app/src/androidTest/kotlin/app/openstory/notifications/NotificationDeliveryRecoveryTest.kt`

**Interfaces:**
- Uses unique work name `chapter-notification-drain`; bounded claim -> classify -> validate -> publish/mark terminal -> continue while bounded work remains.

- [ ] Test process death after claim and after publish, stale reclaim, disabled category consume, permission denial, enqueue failure, and multiple concurrent wake requests.
- [ ] Re-throw cancellation and release/expire claims according to repository policy.
- [ ] Register one startup recovery wake so pending durable rows survive a lost post-commit wakeup.
- [ ] Never use periodic chapter worker results as notification input.
- [ ] Commit: `feat(notifications): drain durable chapter events`.

---

## Phase 6 - Settings Status And Error Discipline

### Task 23: Expose narrow session, notification, storage, and schedule status ports

**Files:**
- Create: `settings/src/main/kotlin/app/openstory/settings/session/PluginSessionControlPort.kt`
- Create: `settings/src/main/kotlin/app/openstory/settings/notification/NotificationControlPort.kt`
- Create: `settings/src/main/kotlin/app/openstory/settings/background/BackgroundWorkStatusPort.kt`
- Create: `settings/src/main/kotlin/app/openstory/settings/storage/StorageSummaryPort.kt`
- Create: `app/src/main/kotlin/app/openstory/settings/RuntimePluginSessionControlAdapter.kt`
- Create: `app/src/main/kotlin/app/openstory/settings/AndroidNotificationControlAdapter.kt`
- Create: `app/src/main/kotlin/app/openstory/settings/WorkManagerBackgroundWorkStatusAdapter.kt`
- Create: `app/src/main/kotlin/app/openstory/settings/AppStorageSummaryAdapter.kt`
- Modify: `feature/settings/src/main/kotlin/app/openstory/settings/ui/SettingsViewModel.kt`
- Modify: `feature/settings/src/test/kotlin/app/openstory/settings/ui/SettingsViewModelTest.kt`
- Create: `feature/settings/src/test/kotlin/app/openstory/settings/ui/SettingsScreenSemanticsTest.kt`
- Modify: `feature/settings/src/test/kotlin/app/openstory/settings/ui/SettingsScreenScreenshotTest.kt`

**Interfaces:**
- `PluginSessionControlPort`: installed auth summaries plus login/logout commands.
- `NotificationControlPort`: permission/channel summary, permission request command, recent `IN_APP_ONLY` events.
- `BackgroundWorkStatusPort`: current registration and last bounded dispatch summary.
- `StorageSummaryPort`: usage/quota summary without Room or file-store imports in UI.

```kotlin
interface PluginSessionControlPort {
    val sessions: Flow<List<SettingsPluginSessionSummary>>
    suspend fun beginLogin(pluginId: PluginId): PluginLoginCommandResult
    suspend fun logout(pluginId: PluginId)
}

interface NotificationControlPort {
    val status: Flow<SettingsNotificationStatus>
    suspend fun requestPermission(): NotificationPermissionRequestResult
}

fun interface BackgroundWorkStatusPort {
    fun observe(): Flow<SettingsBackgroundWorkStatus>
}

fun interface StorageSummaryPort {
    fun observe(): Flow<SettingsStorageSummary>
}

data class SettingsPluginSessionSummary(
    val pluginId: PluginId,
    val displayName: String,
    val status: SettingsPluginSessionStatus,
    val expiresAtEpochMillis: Long?,
)

enum class SettingsPluginSessionStatus { LOGGED_OUT, AUTHENTICATED, EXPIRED }

sealed interface PluginLoginCommandResult {
    data object Launched : PluginLoginCommandResult
    data class Rejected(val errorCode: String) : PluginLoginCommandResult
}

data class SettingsNotificationStatus(
    val permissionGranted: Boolean,
    val channelEnabled: Boolean,
    val recentInAppOnlyCount: Int,
)

sealed interface NotificationPermissionRequestResult {
    data object Granted : NotificationPermissionRequestResult
    data object Denied : NotificationPermissionRequestResult
    data class Failed(val errorCode: String) : NotificationPermissionRequestResult
}

data class SettingsBackgroundWorkStatus(
    val registered: Boolean,
    val lastDispatchAtEpochMillis: Long?,
    val lastErrorCode: String?,
)

data class SettingsStorageSummary(
    val totalBytes: Long,
    val automaticCacheBytes: Long,
    val automaticCacheQuotaBytes: Long,
)
```

- [ ] Remove nullable production fallbacks that fabricate plugin/session state.
- [ ] Expose stable user-facing error codes/copy; never surface `ex.message`, paths, SQL, URLs, or secret-bearing failures.
- [ ] Keep submitting/reset/clear state coherent on failure and cancellation.
- [ ] Ensure changing scheduling settings reaches Task 5 coordinator exactly once.
- [ ] Run feature Settings unit, semantics, and screenshot tests.
- [ ] Commit: `fix(settings-ui): present real wave 10 status`.

---

## Phase 7 - Integration And Acceptance

### Task 24: Add production auth and redirect integration coverage

**Files:**
- Create: `app/src/androidTest/kotlin/app/openstory/auth/PluginSessionRuntimeIntegrationTest.kt`
- Create: `app/src/test/kotlin/app/openstory/di/PluginCredentialRedirectIntegrationTest.kt`
- Create: `app/src/androidTest/assets/plugins/authenticated-content/manifest.json`

- [ ] Prove login completion stores a Keystore-backed session, Settings observes authenticated state, `auth.getState` agrees, and an allowed target request receives only declared cookies.
- [ ] Prove same-host wrong-path and allowed cross-host redirect receive no cookie.
- [ ] Prove package disablement and changed-policy update stop delivery immediately.
- [ ] Prove logout removes encrypted state and summary state without leaving an authenticated/read-missing split.
- [ ] Run focused app/runtime integration gate.
- [ ] Commit: `test(auth): verify production session flow`.

### Task 25: Add bounded scheduling and notification recovery integration coverage

**Files:**
- Create: `app/src/androidTest/kotlin/app/openstory/work/PeriodicChapterDispatchIntegrationTest.kt`
- Create: `app/src/androidTest/kotlin/app/openstory/notifications/NotificationRecoveryIntegrationTest.kt`
- Create: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/NotificationClaimRecoveryTest.kt`

- [ ] Seed more than 40 due stories; prove dispatch occurs in bounded 20-item pages with stable continuation and no starvation.
- [ ] Prove one source failure does not block remaining stories and cancellation does not turn into retry.
- [ ] Commit a chapter change, simulate lost wake/process death, restart, and prove one terminal delivery result.
- [ ] Prove denied permission creates `IN_APP_ONLY` and later permission grant does not publish historical events.
- [ ] Prove notification IDs remain unique for adversarial hash-collision identifiers.
- [ ] Commit: `test(wave10): verify bounded recovery flows`.

### Task 26: Run final host/device gates and close the checkpoint

**Files:**
- Modify: `docs/implementation/waves/wave-10-background-sync-auth-and-notifications.md`
- Modify: `docs/implementation/current-roadmap.md`
- Create: `docs/internal/checkpoints/wave-10-production-remediation.md`

- [ ] Run `bash scripts/check-wave-10-production-policy.sh`; expect PASS.
- [ ] Run `./gradlew verifyArchitecture :build-logic:test test testDebugUnitTest lintDebug detekt :app:assembleDebug`.
- [ ] Run all schema `10 -> 11`, session-store, WebView, notification-delivery, and navigation instrumentation tests on API 26 and API 37.
- [ ] Run existing Discover/Home/Library/Reader/Downloads regression gates and confirm top-level navigation remains unchanged.
- [ ] Record exact commands, device/API identifiers, test counts, failures, retries, and final SHAs in the checkpoint.
- [ ] Re-run `git diff --check` and verify no credential-like values, local paths, generated APKs, or temporary session files are tracked.
- [ ] Mark Wave 10 complete only when every W10-R01..W10-R20 row has a passing test/gate and no Critical or High residual remains.
- [ ] Commit: `docs: close wave 10 production remediation`.

---

## Final Acceptance Matrix

### Security

- [ ] No embedded encryption key or plaintext credential persistence exists.
- [ ] Sessions are Keystore-backed, atomic, no-backup, AAD-bound, expiring, and invalidated by policy changes.
- [ ] Credential delivery is full-request-target scoped and collision-safe.
- [ ] WebView capture is serialized and blocks undeclared navigation/capabilities.

### Background Work

- [ ] Periodic work is registered from persisted settings and updated exactly once per distinct policy.
- [ ] Each dispatcher invocation handles at most 20 candidates with compact continuation state.
- [ ] Candidate lookup is bounded/query-based rather than whole-Library Flow plus N reads.
- [ ] Source failures and authentication denial are isolated; cancellation is never swallowed.

### Notification Durability

- [ ] Schema 11 contains idempotent raw events, delivery state, claims, indexes, and collision-safe IDs.
- [ ] Chapter graph and raw facts commit atomically.
- [ ] Lost wakeups and process death recover through the unique drain.
- [ ] Permission denial/channel disablement becomes `IN_APP_ONLY` without retries or later burst.
- [ ] Every platform target is validated against current story/chapter state.

### Settings And Reader

- [ ] Legacy settings migrate through registered DataStore migration and corrupt storage is repaired.
- [ ] Reader waits for initial preferences and uses language order on its first selection.
- [ ] Failed font persistence does not leave divergent UI state.
- [ ] Settings displays real installed plugin/session/work/notification/storage status and no fabricated fallback data.

### Verification

- [ ] Unit, architecture, lint, Detekt, assembly, and structural Wave 10 policy gates pass.
- [ ] Required API 26 and API 37 instrumentation gates pass.
- [ ] Worktree is clean and checkpoint evidence records the exact accepted SHA.

## Execution Order

1. Complete Phase 0 first so the reviewed defects become executable contracts.
2. Complete Phases 1 and 2 before auth/notification integration so scheduling and Reader policy no longer depend on prototype behavior.
3. Complete Phase 3 before enabling any authenticated production package.
4. Complete Phase 4 before Phase 5; notification delivery must never precede durable evidence.
5. Complete Phase 6 only against real ports from Phases 1-5; do not retain nullable fake UI fallbacks.
6. Run Phase 7 from a clean checkout and do not waive device or migration failures.

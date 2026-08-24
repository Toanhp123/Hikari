<!-- DOCUMENT LIFECYCLE: PLANNED / REBASELINED FOR SCHEMA-10 ENTRY -->

# Wave 10 - Background Work, Authentication, and Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`; use TDD and commit each task.

**Goal:** Add typed policies, idempotent local scheduling, usable plugin-scoped authentication, and deduplicated local notifications over the existing capability engines.

**Architecture:** Follows `../../superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`. Introduces `:settings` and `:feature:settings`; `:feature:settings` consumes the existing `:core:designsystem` presentation foundation. Pure mapping/chapter/download engines remain in their owners; WorkManager, permission, deep-link, and notification adapters remain in `:app`; encrypted sessions remain in `:plugins:runtime`.

## Global Constraints

- Entry module graph: current 14-module graph = Wave 09 verified exit graph plus the approved `:core:designsystem` UI-foundation boundary.
- Exit module graph: entry graph plus `:settings` and `:feature:settings`.
- Introduces `:settings` and `:feature:settings` in Task 1.
- Consumes from Wave 09: mapping/chapter/download commands, reconciliation, Reader preferences port, and quota state. It also preserves the Product UI/Discover boundary and the schema-10 canonical-engine durability foundation.
- Produces for Wave 11: typed settings, schedulers, usable session controls, notification evidence, and release-ready platform behavior.
- Room schema 10 enters. `MIGRATION_9_10` belongs exclusively to canonical-engine queue leases and the catalog-change outbox. Task 5 durable notification delivery state migrates schema `10 -> 11` through `MIGRATION_10_11`.
- Android background work is local, unique, bounded, battery-aware, and idempotent. Existing unique work names remain stable across the refactor.
- Existing `LibraryMappingScheduler`, `InitialChapterSyncScheduler`, `DownloadScheduler`, and `CanonicalEngineWorkScheduler` contracts are reused; Wave 10 must not create parallel scheduling ports for those capabilities.
- No cloud service, push backend, unrestricted WebView, deprecated `androidx.security.crypto` storage, or plaintext credentials.
- Wave 10 Settings enters through the avatar utility sheet and never top-level navigation.
- Discover / Home / Library remains the final top-level model; Settings must not be added to `TopLevelDestination`.

### Task 1: Introduce typed settings and DataStore persistence

**Files:**
- Create: `settings/build.gradle.kts`, `settings/src/main/kotlin/app/openstory/settings/AppSettings.kt`, `AppSettingsRepository.kt`, `DataStoreAppSettingsRepository.kt`
- Test: `settings/src/test/kotlin/app/openstory/settings/DataStoreAppSettingsRepositoryTest.kt`
- Create: `feature/settings/build.gradle.kts`
- Create: `app/src/main/kotlin/app/openstory/settings/SettingsReaderPreferencesAdapter.kt`
- Modify: `settings.gradle.kts`, `config/architecture/module-boundaries.json`, `gradle/libs.versions.toml`, `gradle/verification-metadata.xml`
- Create: `app/src/main/kotlin/app/openstory/di/SettingsModule.kt`

- [ ] Write RED tests for normalized language order, minimum periodic interval, network/battery/download/notification/reader policies, corruption fallback, and the exact 16-module production exit graph.
- [ ] Add the reviewed AndroidX DataStore dependency and implement typed immutable settings with one Preferences DataStore-backed repository. Keep serialization keys private and preserve valid settings when one field is malformed.
- [ ] Adapt settings to the existing Reader preferences port in `:app`; do not introduce a `:reader -> :settings` dependency.
- [ ] Run `./gradlew :settings:test :verifyArchitecture detekt --stacktrace` and strict dependency verification.
- [ ] Commit `settings: add typed local policies`.

### Task 2: Extend existing capability scheduling with WorkManager

**Files:**
- Modify: `library/src/main/kotlin/app/openstory/library/LibraryMappingScheduler.kt`
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncModels.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/DownloadModels.kt`
- Create: `settings/src/main/kotlin/app/openstory/settings/work/SettingsWorkSchedulePort.kt`
- Modify: `app/src/main/kotlin/app/openstory/work/LibraryMappingWorker.kt`, `InitialChapterSyncWorker.kt`, `ChapterDownloadWorker.kt`, `WorkManagerCanonicalEngineWorkScheduler.kt`
- Create: `app/src/main/kotlin/app/openstory/work/WorkNames.kt`, `WorkManagerCapabilitySchedulers.kt`
- Test: `app/src/test/kotlin/app/openstory/work/WorkManagerCapabilitySchedulersTest.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/work/WorkIdempotencyTest.kt`

- [ ] Write RED tests for stable existing work names, unique/replacement policy, minimum periodic cadence, network/battery/storage constraints, bounded batches, retry classification, cancellation, and plugin failure isolation.
- [ ] Centralize names without renaming `library-mapping:<storyId>`, `initial-chapter-sync:<storyId>`, `chapter-download:<releaseId>`, `canonical-engine-drain`, or `canonical-engine-safety`; already-enqueued work must remain addressable.
- [ ] Extend the existing scheduler contracts only where periodic/cancel behavior is required. App workers continue to deserialize stable IDs and call one capability service; feature modules never import WorkManager or `:app` types.
- [ ] Run `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest --stacktrace -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.work.WorkIdempotencyTest`.
- [ ] Commit `app: schedule bounded capability work`.

### Task 3: Unify manual, initial, deferred, and periodic triggers

**Files:**
- Modify: `library/src/main/kotlin/app/openstory/library/LibraryService.kt`, `library/src/main/kotlin/app/openstory/library/mapping/ContentMappingService.kt`
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncService.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/DownloadService.kt`
- Create: `settings/src/main/kotlin/app/openstory/settings/work/BackgroundPolicyService.kt`
- Test: `settings/src/test/kotlin/app/openstory/settings/work/BackgroundPolicyServiceTest.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryViewModel.kt`, `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt`

- [ ] Write RED tests proving manual refresh and scheduled work share engines, initial work is unique, periodic batches select only due work, cancellation is scoped, canonical-engine safety remains independently registered, and UI never calls WorkManager directly.
- [ ] Implement one settings-owned background policy coordinator over `SettingsWorkSchedulePort`; capability services retain matching, sync, download, cursor, quota, and retry policy.
- [ ] Preserve canonical outbox materialization and durable queue draining as the canonical engine's existing path; Wave 10 may schedule that path but must not duplicate it.
- [ ] Run `./gradlew :library:test :chapters:test :downloads:test :settings:test :feature:catalog:testDebugUnitTest :feature:reader:testDebugUnitTest :app:testDebugUnitTest --stacktrace`.
- [ ] Commit `app: unify local work triggers`.

### Task 4: Add plugin-scoped authentication sessions

**Files:**
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/manifest/PluginManifest.kt`
- Test: `plugins/api/src/test/kotlin/app/openstory/plugins/api/manifest/PluginManifestTest.kt`
- Modify: `docs/plugin-sdk/package-format.md`, `docs/plugin-sdk/javascript-runtime.md`, `docs/plugin-sdk/api-versioning.md`
- Create: `docs/plugin-sdk/authentication.md`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/session/PluginSessionStore.kt`, `EncryptedPluginSessionStore.kt`, `PluginSessionService.kt`, `PluginSessionManagedCredentialProvider.kt`
- Test: `plugins/runtime/src/androidTest/kotlin/app/openstory/plugins/runtime/session/PluginSessionStoreTest.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/session/PluginSessionManagedCredentialProviderTest.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/CompositeManagedCredentialProvider.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/capabilities/http/CompositeManagedCredentialProviderTest.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/PluginHttpCapability.kt`
- Create: `settings/src/main/kotlin/app/openstory/settings/session/PluginSessionControlPort.kt`
- Create: `app/src/main/kotlin/app/openstory/auth/PluginLoginCaptureActivity.kt`, `PluginLoginCoordinator.kt`, `RuntimePluginSessionControlAdapter.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/auth/PluginLoginCoordinatorTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/PluginRuntimeModule.kt`, `app/src/main/AndroidManifest.xml`

- [ ] Write RED tests for manifest authentication declarations, declared HTTPS navigation/redirect hosts, cookie scope, Android Keystore-backed AES-GCM encryption, no-backup storage, transient WebView clearing, logout/removal cleanup, redacted diagnostics, capability denial, and end-to-end HTTP credential composition without cross-plugin leakage.
- [ ] Add an optional authentication manifest capability without changing protocol major 1 unless the serialized wire contract becomes incompatible; document that decision in `api-versioning.md` and the contract fixture.
- [ ] Keep session ownership in `:plugins:runtime`. `CompositeManagedCredentialProvider` merges non-conflicting providers deterministically and rejects ambiguous header ownership; app composes existing managed credentials with matching plugin/host session credentials.
- [ ] Expose only session summaries plus login/logout commands through `PluginSessionControlPort`; this enables usable Wave 10 controls without giving `:feature:settings` a runtime dependency. Full plugin installation/update/rollback management remains Wave 11.
- [ ] Run `./gradlew :plugins:api:test :plugins:runtime:testDebugUnitTest :plugins:runtime:connectedDebugAndroidTest :app:connectedDebugAndroidTest --stacktrace` and `bash scripts/tests/plugin-sdk-current-contract-test.sh`.
- [ ] Commit `plugins: add scoped authenticated sessions`.

### Task 5: Classify and deliver local notifications

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/notification/ChapterNotificationClassifier.kt`, `NotificationEventRepository.kt`
- Test: `chapters/src/test/kotlin/app/openstory/chapters/notification/ChapterNotificationClassifierTest.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/NotificationEventEntity.kt`, `NotificationEventDao.kt`, `RoomNotificationEventRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`, `RoomMigrations.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/NotificationMigrationTest.kt`
- Create: `app/src/main/kotlin/app/openstory/notification/AndroidChapterNotifier.kt`, `NotificationChannels.kt`, `NotificationDeepLinkFactory.kt`
- Test: `app/src/test/kotlin/app/openstory/notification/AndroidChapterNotifierTest.kt`, `NotificationDeepLinkFactoryTest.kt`
- Modify: `app/src/main/AndroidManifest.xml`

- [ ] Write RED tests distinguishing new canonical chapters from added releases, preferred-language rules, duplicate suppression, permission denial, immutable/signed route arguments, stable notification IDs, and schema `10 -> 11`.
- [ ] Prove `MIGRATION_10_11` preserves canonical-engine leases, queued work, catalog-change outbox rows, schema-10 catalog state, Library/Chapter/Reader/Download state, and foreign keys before adding notification tables/indexes.
- [ ] Implement pure classification in `:chapters`, atomic pending/delivered/consumed state in Room, and platform delivery in `:app`. Permission denial consumes no delivery attempt and leaves events visible to in-app status without retry spam.
- [ ] Run `./gradlew :chapters:test :storage:room:connectedDebugAndroidTest :app:testDebugUnitTest --stacktrace` and `./scripts/verify-room-schema-stability.sh`.
- [ ] Commit `chapters: classify local update notifications`.

### Task 6: Build settings, session, and status UI

**Files:**
- Create: `feature/settings/src/main/kotlin/app/openstory/settings/ui/SettingsViewModel.kt`, `SettingsScreen.kt`, `SyncSettings.kt`, `NotificationSettings.kt`, `SourceSessionSettings.kt`, `StorageSettings.kt`
- Test: `feature/settings/src/test/kotlin/app/openstory/settings/ui/SettingsViewModelTest.kt`
- Test: `feature/settings/src/androidTest/kotlin/app/openstory/settings/ui/SettingsScreenTest.kt`
- Test: `feature/settings/src/test/kotlin/app/openstory/settings/ui/SettingsScreenshotTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppRoute.kt`, `AppNavHost.kt`, `app/src/main/kotlin/app/openstory/ui/HikariUtilitySheet.kt`, `HikariAppShell.kt`

- [ ] Write RED tests for policy persistence, one-shot rescheduling through `SettingsWorkSchedulePort`, contextual notification permission, storage summary, plugin session login/logout state, accessibility, process restoration, `AppRoute.Settings`, utility-row navigation, and the unchanged `TopLevelDestination` set.
- [ ] Implement feature-owned UI over `:settings`, `:downloads`, and the narrow `PluginSessionControlPort`; policy/session ownership remains in their capability owners while app supplies permission, session, and scheduling adapters through DI.
- [ ] Build the screen from `:core:designsystem` Hikari artwork, glass, and content-state primitives without copying policy into Compose state.
- [ ] Add `AppRoute.Settings` and a Settings row to `HikariUtilitySheet`; close the sheet before navigation and return to the originating top-level destination on back. Do not add Settings to `TopLevelDestination` or floating navigation.
- [ ] Run `./gradlew :feature:settings:testDebugUnitTest :feature:settings:connectedDebugAndroidTest :app:connectedDebugAndroidTest lintDebug detekt --stacktrace`.
- [ ] Commit `settings: add background auth and notification controls`.

## Wave Checkpoint

- [ ] Exact 16-module production exit graph and Room schema 11 pass.
- [ ] All workers are unique/idempotent, preserve existing work names, and delegate to capability engines.
- [ ] Existing canonical-engine outbox/lease durability remains intact across `10 -> 11`.
- [ ] Sessions remain plugin/host scoped, encrypted, removable, and reachable through minimal login/logout controls.
- [ ] Notifications distinguish chapters from additional releases, deduplicate durably, and deep-link through validated stable IDs.
- [ ] `./scripts/verify.sh`, API 26/API 37 device checkpoints, focused WebView/session tests, and notification permission/deep-link tests pass.
- [ ] Deep ownership review confirms settings, capability engines, runtime sessions, Room delivery state, UI, and platform adapters remain separated.

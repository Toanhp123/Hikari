<!-- DOCUMENT LIFECYCLE: PLANNED / REBASELINED FOR POST-BASELINE GRAPH -->

# Wave 10 - Background Work, Authentication, and Notifications Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`; use TDD and commit each task.

**Goal:** Add typed policies, idempotent local scheduling, plugin-scoped authentication, and deduplicated local notifications over existing capability engines.

**Architecture:** Follows `../../superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`. Introduces `:settings` and `:feature:settings`; `:feature:settings` consumes the existing `:core:designsystem` presentation foundation. Pure mapping/chapter/download engines remain in their owners; WorkManager and notification delivery remain in `:app`; sessions remain in `:plugins:runtime`.

## Global Constraints

- Entry module graph: current 14-module graph = Wave 09 verified exit graph plus the approved `:core:designsystem` UI-foundation boundary; Product UI and Discover follow-ups add no production module.
- Exit module graph: entry graph plus `:settings` and `:feature:settings`.
- Introduces `:settings` and `:feature:settings` in Task 1.
- Consumes from Wave 09: mapping/chapter/download commands, reconciliation, Reader preferences port, and quota state. It also preserves the accepted Product UI/Discover presentation boundary and schema-9 canonical-engine foundation without taking ownership of it.
- Produces for Wave 11: typed settings, schedulers, session controls, notification evidence, and release-ready platform behavior.
- Room schema 9 enters; Task 5 durable notification delivery state migrates 9 -> 10.
- Android background work is local, unique, bounded, battery-aware, and idempotent.
- No cloud service, push backend, unrestricted WebView, or plaintext credentials.
- Wave 10 Settings enters through the avatar utility sheet and never top-level navigation.
- Discover / Home / Library remains the final top-level model; Settings must not be added to `TopLevelDestination`.

### Task 1: Introduce typed settings and DataStore persistence

**Files:**
- Create: `settings/build.gradle.kts`, `settings/src/main/kotlin/app/openstory/settings/AppSettings.kt`, `AppSettingsRepository.kt`, `DataStoreAppSettingsRepository.kt`
- Test: `settings/src/test/kotlin/app/openstory/settings/DataStoreAppSettingsRepositoryTest.kt`
- Create: `feature/settings/build.gradle.kts`
- Create: `app/src/main/kotlin/app/openstory/settings/SettingsReaderPreferencesAdapter.kt`
- Modify: `settings.gradle.kts`, `config/architecture/module-boundaries.json`
- Create: `app/src/main/kotlin/app/openstory/di/SettingsModule.kt`

- [ ] Write RED tests for normalized language order, sync interval constraints, notification/download/reader policies, corruption fallback, and exact exit graph.
- [ ] Implement typed immutable settings and one DataStore-backed repository.
- [ ] Run `./gradlew :settings:test :verifyArchitecture detekt --stacktrace`.
- [ ] Commit `settings: add typed local policies`.

### Task 2: Schedule existing capability engines with WorkManager

**Files:**
- Create: `library/src/main/kotlin/app/openstory/library/work/LibraryWorkSchedulePort.kt`
- Create: `chapters/src/main/kotlin/app/openstory/chapters/work/ChapterWorkSchedulePort.kt`
- Create: `downloads/src/main/kotlin/app/openstory/downloads/work/DownloadWorkSchedulePort.kt`
- Create: `settings/src/main/kotlin/app/openstory/settings/work/SettingsWorkSchedulePort.kt`
- Create: `app/src/main/kotlin/app/openstory/work/WorkNames.kt`, `WorkManagerCapabilitySchedulers.kt`
- Test: `app/src/test/kotlin/app/openstory/work/WorkManagerCapabilitySchedulersTest.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/work/WorkIdempotencyTest.kt`

- [ ] Write RED tests for unique names, replacement policy, network/battery/storage constraints, bounded batches, retry classification, and plugin failure isolation.
- [ ] Implement app-owned port adapters and thin workers that deserialize stable IDs and call one capability service; feature modules never import app scheduling types.
- [ ] Run `./gradlew :app:testDebugUnitTest :app:connectedDebugAndroidTest --stacktrace` with `WorkIdempotencyTest` selected for the focused device run.
- [ ] Commit `app: schedule bounded capability work`.

### Task 3: Unify manual, initial, deferred, and periodic triggers

**Files:**
- Modify: `library/src/main/kotlin/app/openstory/library/LibraryService.kt`, `library/src/main/kotlin/app/openstory/library/mapping/ContentMappingService.kt`
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterSyncService.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/DownloadService.kt`
- Create: `settings/src/main/kotlin/app/openstory/settings/work/BackgroundPolicyService.kt`
- Test: `settings/src/test/kotlin/app/openstory/settings/work/BackgroundPolicyServiceTest.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryViewModel.kt`, `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt`

- [ ] Write RED tests proving manual refresh and scheduled work share engines, initial work is unique, cancellation is scoped, and UI never calls WorkManager directly.
- [ ] Implement capability services against their own schedule ports; feature ViewModels call capability services and never reference WorkManager or `:app` types.
- [ ] Run `./gradlew :library:test :chapters:test :downloads:test :settings:test :feature:catalog:testDebugUnitTest :feature:reader:testDebugUnitTest :app:testDebugUnitTest --stacktrace`.
- [ ] Commit `app: unify local work triggers`.

### Task 4: Add plugin-scoped authentication sessions

**Files:**
- Modify: `plugins/api/src/main/kotlin/app/openstory/plugins/api/manifest/PluginManifest.kt`
- Test: `plugins/api/src/test/kotlin/app/openstory/plugins/api/manifest/PluginManifestTest.kt`
- Modify: `docs/plugin-sdk/manifest.md`, `docs/plugin-sdk/security.md`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/session/PluginSessionStore.kt`, `EncryptedPluginSessionStore.kt`, `PluginSessionService.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/session/PluginSessionManagedCredentialProvider.kt`
- Test: `plugins/runtime/src/androidTest/kotlin/app/openstory/plugins/runtime/session/PluginSessionStoreTest.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/session/PluginSessionManagedCredentialProviderTest.kt`
- Create: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/CompositeManagedCredentialProvider.kt`
- Test: `plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/capabilities/http/CompositeManagedCredentialProviderTest.kt`
- Modify: `plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/PluginHttpCapability.kt`
- Create: `app/src/main/kotlin/app/openstory/auth/PluginLoginCaptureActivity.kt`, `PluginLoginCoordinator.kt`
- Test: `app/src/androidTest/kotlin/app/openstory/auth/PluginLoginCoordinatorTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/di/PluginRuntimeModule.kt`

- [ ] Write RED tests for manifest authentication declarations, SDK contract text, declared HTTPS hosts, cookie scope, encryption at rest, transient WebView clearing, logout, redacted diagnostics, capability denial, and an end-to-end runtime HTTP request receiving both existing managed client headers and only the matching plugin/host session headers without cross-plugin leakage.
- [ ] Implement session ownership in `:plugins:runtime`; `PluginSessionManagedCredentialProvider` adapts encrypted sessions to the existing HTTP credential boundary. `CompositeManagedCredentialProvider` merges non-conflicting providers deterministically, rejects ambiguous duplicate header ownership, and `PluginRuntimeModule` composes the existing `MyAnimeListManagedCredentials` with session credentials rather than replacing either. App captures only declared-host cookies and returns them through the runtime facade.
- [ ] Run `./gradlew :plugins:api:test :plugins:runtime:testDebugUnitTest :plugins:runtime:connectedDebugAndroidTest :app:connectedDebugAndroidTest --stacktrace` and `bash scripts/tests/plugin-sdk-current-contract-test.sh`.
- [ ] Commit `plugins: add scoped authenticated sessions`.

### Task 5: Classify and deliver local notifications

**Files:**
- Create: `chapters/src/main/kotlin/app/openstory/chapters/notification/ChapterNotificationClassifier.kt`, `NotificationEventRepository.kt`
- Test: `chapters/src/test/kotlin/app/openstory/chapters/notification/ChapterNotificationClassifierTest.kt`
- Create: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/NotificationEventEntity.kt`, `RoomNotificationEventRepository.kt`
- Modify: `storage/room/build.gradle.kts`, `storage/room/src/main/kotlin/app/openstory/storage/room/OpenStoryDatabase.kt`, `RoomMigrations.kt`
- Test: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/NotificationMigrationTest.kt`
- Create: `app/src/main/kotlin/app/openstory/notification/AndroidChapterNotifier.kt`, `NotificationChannels.kt`
- Test: `app/src/test/kotlin/app/openstory/notification/AndroidChapterNotifierTest.kt`

- [ ] Write RED tests distinguishing new canonical chapters from added releases, preferred-language rules, duplicate suppression, permission denial, deep links, and schema `9 -> 10`.
- [ ] Implement pure classification in Chapters, atomic delivery state in Room, and platform notification delivery in app.
- [ ] Run `./gradlew :chapters:test :storage:room:connectedDebugAndroidTest :app:testDebugUnitTest --stacktrace` and `./scripts/verify-room-schema-stability.sh`.
- [ ] Commit `chapters: classify local update notifications`.

### Task 6: Build settings and status UI

**Files:**
- Create: `feature/settings/src/main/kotlin/app/openstory/settings/ui/SettingsViewModel.kt`, `SettingsScreen.kt`, `SyncSettings.kt`, `NotificationSettings.kt`, `StorageSettings.kt`
- Test: `feature/settings/src/test/kotlin/app/openstory/settings/ui/SettingsViewModelTest.kt`
- Test: `feature/settings/src/androidTest/kotlin/app/openstory/settings/ui/SettingsScreenTest.kt`
- Create: `feature/settings/src/test/kotlin/app/openstory/settings/ui/SettingsScreenshotTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppRoute.kt`, `AppNavHost.kt`, and the app-shell owner of `HikariUtilitySheet`

- [ ] Write RED tests for policy persistence, rescheduling once through `SettingsWorkSchedulePort`, permission-request state, storage summary, accessibility, process restoration, `AppRoute.Settings` composition, Settings utility-row navigation, and the unchanged `TopLevelDestination` set. Session management UI remains Wave 11 ownership.
- [ ] Implement feature-owned UI over `:settings` and `:downloads` services only; typed settings and mutation ownership remain in `:settings`, while app supplies permission and scheduling port adapters through DI.
- [ ] Build the screen from `:core:designsystem` Hikari artwork, glass, and content-state primitives, matching the approved Settings target without copying policy into Compose state.
- [ ] Add `AppRoute.Settings` to `AppNavHost` and a Settings row to `HikariUtilitySheet`; close the sheet before navigation and return to the originating top-level destination on back. Do not add Settings to `TopLevelDestination` or the floating navigation.
- [ ] Run `./gradlew :feature:settings:testDebugUnitTest :feature:settings:connectedDebugAndroidTest :app:connectedDebugAndroidTest lintDebug detekt --stacktrace`.
- [ ] Commit `settings: add background and notification controls`.

## Wave Checkpoint

- [ ] Exact exit graph and schema 10 pass.
- [ ] All workers are unique/idempotent and delegate to capability engines.
- [ ] Sessions remain plugin/host scoped and encrypted.
- [ ] Notifications distinguish chapters from additional releases and deduplicate durably.
- [ ] `./scripts/verify.sh` and device checkpoint pass.
- [ ] Deep ownership review confirms settings, capability engines, runtime sessions, Room delivery state, UI, and platform adapters remain separated.

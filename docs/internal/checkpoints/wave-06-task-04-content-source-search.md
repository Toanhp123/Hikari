# Wave 06 Task 04 — Content-Source Search Verification

Date: 2026-08-11
Status: **VERIFIED**

## Scope

This evidence record covers Wave 06 Task 04 only: bounded plugin-backed content-source
search in quick/deferred stages plus the thin application WorkManager adapter. It does not
accept protected mapping persistence, Room schema 3, user approval/override semantics,
mapping review UI, user-facing URL import, chapter synchronization, or later-wave work.

## Implemented boundary

- `:library` owns `ContentSource`, `PluginContentSource`, `PluginContentSourceRegistry`,
  deterministic quick/deferred planning, source execution, and `ContentMappingSearchService`.
- Search queries/candidates are bounded. Per-source timeouts and exception isolation keep a
  slow or failing plugin from aborting peer sources; calls are serialized per cached
  plugin/version source instance.
- `:plugins:api` adds `CONTENT_RESOLVE_URL` alongside `CONTENT_SEARCH` and validates bounded
  content candidates plus HTTPS source URLs.
- Optional URL resolution checks URL length, HTTPS, and the plugin's declared/accepted host
  before invoking runtime execution; unsupported operations remain source-level failures.
- Library uses only the approved plugin API and narrow public runtime facade. It remains
  forbidden from plugin runtime persistence/install/security internals and from Room.
- `LibraryService.add()` commits metadata-only membership before scheduling mapping work. A
  scheduler failure cannot roll back that committed membership.
- `LibraryMappingWorker` lives in `:app`, carries a stable `StoryId`, uses unique WorkManager
  work, and delegates Library behavior rather than owning search or matching policy.
- Task 04 introduces no Room migration. Schema 2 remains current and schema 1 remains frozen.

## Verification environment

Verification was reviewed from the Windows checkout on 2026-08-11. JVM/Gradle commands
ran from PowerShell; repository verification ran from Git Bash. Android instrumentation
used a Pixel 10 Pro Android 17 AVD (API 37, `emulator-5556`) and a Pixel Android 8.0.0 AVD
(API 26, `emulator-5554`).

## Current evidence

| Gate | Result | Evidence |
|---|---|---|
| Library focused suite | PASS | `.\gradlew.bat :library:test --no-configuration-cache --stacktrace`; `BUILD SUCCESSFUL`. |
| Focused Task-04 JVM suites + Detekt | PASS | `.\gradlew.bat :catalog:testDebugUnitTest :feature:catalog:testDebugUnitTest :library:test :plugins:api:test :plugins:runtime:testDebugUnitTest :app:testDebugUnitTest detekt --no-configuration-cache --stacktrace`; `BUILD SUCCESSFUL`. |
| Detekt standalone | PASS | `.\gradlew.bat detekt --no-configuration-cache --stacktrace`; `BUILD SUCCESSFUL`. The Task-04 fixes use refactoring rather than new suppressions. |
| Dependency verification | PASS | New AndroidX WorkManager dependency artifacts were recorded in Gradle SHA-256 verification metadata; dependency verification remained enabled and the focused/full builds completed successfully. |
| App instrumentation API 37 | PASS with baseline live-test skip | `.\gradlew.bat :app:connectedDebugAndroidTest --no-configuration-cache --stacktrace` on `emulator-5556`; zero failures, with `MyAnimeListLiveCatalogIntegrationTest` reported `SKIPPED`. |
| App instrumentation API 26 | PASS with baseline MAL skips | Same app instrumentation command on `emulator-5554` / Android 8.0.0; zero failures, with the MAL contract and live integration tests reported `SKIPPED` under their baseline preconditions. |
| Exact architecture/package/source policy | PASS | Full verification reported `Module architecture verified for 8 modules`, `Package boundary policy verified`, `Source layout verified`, and `Current architecture verified: 8 modules, Room schema 1..2.` |
| Lint and full repository verification | PASS | `./scripts/verify.sh` completed lint and repository gates successfully with final `exit=0`. |
| Room history unchanged | PASS | Full verification reported `Room schema export remained stable during verification.` Task 04 adds no schema or migration. |

## Verification corrections retained

During focused verification, Gradle first exposed missing dependency-verification entries
for the new WorkManager graph, then Detekt/style issues, then a missing Library JSON
serialization classpath dependency. Those were corrected without disabling verification or
adding structural suppressions. Full verification also exposed an accidental Task-04 policy
regression that forbade `:storage:room` from the pre-approved
`plugins.runtime.persistence` SPI; the policy was restored narrowly and regression-tested
without granting Library that access.

## Decision

Wave 06 Task 04 is verified. Quick/deferred plugin content discovery and its app scheduling
adapter are accepted on the eight-module, Room-schema-2 boundary. No mapping decision is
persisted yet. The canonical continuation advances to Wave 06 Task 05, which must add
protected mapping semantics and the contiguous Room `2 -> 3` migration without allowing
automation to overwrite user-protected mappings.

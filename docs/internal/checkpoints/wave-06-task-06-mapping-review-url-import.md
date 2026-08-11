# Wave 06 Task 06 — Mapping Review and URL-Import Verification

Date: 2026-08-11
Status: **VERIFIED**

## Scope

This evidence record covers Wave 06 Task 06 and the Wave-06 exit boundary: mapping review,
manual search, user approval/rejection, and host-filtered URL import in
`:feature:catalog`. It does not accept chapter synchronization/aggregation, Reader work, or
later-wave capabilities.

## Implemented boundary

- `MappingViewModel`, `MappingUiState`, and `MappingSheet` live in `:feature:catalog` and call
  only Library-facing services; feature code does not import Room or plugin-runtime internals.
- The UI exposes current mappings, deterministic matcher evidence labels, source failures,
  manual candidate search, URL resolution, approval, rejection, and immediate candidate-state
  updates.
- Manual approval persists `USER_APPROVED`; accepted URL candidates persist `USER_URL`; both
  remain protected by Task-05 Library/Room semantics.
- URL input continues through the Task-04 HTTPS, size, and declared-host checks before any
  plugin runtime invocation. The UI does not bypass or duplicate runtime security policy.
- Story presentation composes mapping review through app/feature DI without moving mapping
  policy into Compose/ViewModel code.
- Task 06 introduces no Room migration. Wave 06 exits with the same eight production modules
  and Room schema 3.

## Verification environment

Verification was reviewed from the Windows checkout on 2026-08-11. JVM/Gradle and Android
instrumentation commands ran from PowerShell; repository verification ran from Git Bash.
Android instrumentation used a Pixel 10 Pro Android 17 AVD (API 37, `emulator-5556`) and a
Pixel Android 8.0.0 AVD (API 26, `emulator-5554`).

## Current evidence

| Gate | Result | Evidence |
|---|---|---|
| Focused feature/Library/app JVM suites + Detekt | PASS | `.\gradlew.bat :feature:catalog:testDebugUnitTest :library:test :app:testDebugUnitTest detekt --no-configuration-cache --stacktrace`; `BUILD SUCCESSFUL`. |
| Feature instrumentation API 37 | PASS | `:feature:catalog:connectedDebugAndroidTest` on `emulator-5556`; 15/15 tests passed. |
| Feature instrumentation API 26 | PASS | Same feature instrumentation command on `emulator-5554` / Android 8.0.0; 15/15 tests passed. |
| App instrumentation API 37 | PASS with baseline live-test skip | `:app:connectedDebugAndroidTest --no-configuration-cache --stacktrace` on `emulator-5556`; zero failures, with `MyAnimeListLiveCatalogIntegrationTest` reported `SKIPPED`. |
| App instrumentation API 26 | PASS with baseline MAL skips | Same app instrumentation command on `emulator-5554`; zero failures, with the MAL contract and live integration tests reported `SKIPPED` under baseline preconditions. |
| Full repository verification | PASS | `./scripts/verify.sh` completed architecture, package/source, structural, lint, and Gradle gates with final `exit=0`. |
| Exact Wave-06 exit graph/schema | PASS | Full verification reported `Module architecture verified for 8 modules`, `Current architecture verified: 8 modules, Room schema 1..3`, and `Room schema export remained stable during verification.` |

## Verification corrections retained

The first Task-06 focused build exposed one Kotlin cross-module smart-cast error and two
Detekt magic-number findings. They were corrected with a local nullable snapshot and named
constants; no suppression or behavior change was introduced. Existing Compose-test v1
deprecation warnings and native-symbol strip warnings remained non-blocking and are not
Wave-06 acceptance failures.

## Decision

Wave 06 Task 06 is verified and Wave 06 is complete. The accepted exit boundary is eight
production modules with Room schema 3, immediate local Library membership, explainable
matching, bounded plugin content search, protected mappings/rejections, and mapping review/
URL import. The canonical continuation advances to Wave 07 Task 01, which introduces
`:chapters` and deterministic chapter-label normalization without adding any other production
module.

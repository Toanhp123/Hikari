# Wave 06 Task 05 — Protected Content-Mapping Verification

Date: 2026-08-11
Status: **VERIFIED**

## Scope

This evidence record covers Wave 06 Task 05 only: protected content-mapping/rejection
persistence and the contiguous Room `2 -> 3` migration. It does not accept mapping-review
presentation, user-facing URL-import UI, chapter synchronization, Reader work, or later waves.

## Implemented boundary

- `:library` owns `ContentMapping`, `ContentMappingOrigin`, policy-versioned rejections, the
  mapping repository contract, and `ContentMappingService` decisions.
- Mapping identity is scoped by canonical `StoryId` plus `PluginId`; one selected source
  story is stored per plugin for a canonical story.
- `AUTOMATED` writes may replace only existing `AUTOMATED` mappings. `USER_APPROVED` and
  `USER_URL` mappings are protected from later automation.
- Repeated writes of the same mapping are idempotent and retain the stored mapping/timestamp.
- Rejections include matcher `policyVersion`, allowing a later policy version to reconsider
  a previously rejected source candidate.
- `:storage:room` owns `content_mappings`, `content_mapping_rejections`, DAO operations, and
  the atomic compare/write transaction. These rows follow canonical story lifetime rather
  than Library-membership lifetime.
- Room schema 3 is current. Schemas 1 and 2 remain unchanged historical exports, with schema
  1 still byte-frozen by the current architecture verifier.
- The Task-04 worker now delegates automatic mapping persistence through
  `ContentMappingService.automate()` instead of stopping at search/match results.

## Verification environment

Verification was reviewed from the Windows checkout on 2026-08-11. JVM/Gradle commands
ran from PowerShell. Android instrumentation used a Pixel 10 Pro Android 17 AVD (API 37,
`emulator-5556`) and a Pixel Android 8.0.0 AVD (API 26, `emulator-5554`).

## Current evidence

| Gate | Result | Evidence |
|---|---|---|
| Room schema export | PASS | `.\gradlew.bat :storage:room:compileDebugKotlin --no-configuration-cache --stacktrace`; `BUILD SUCCESSFUL`; Room generated new `3.json`. |
| Earlier schema immutability | PASS | `git diff` for schema `1.json` and `2.json` was empty after schema-3 export. |
| Focused Library/Room/app JVM suites + Detekt | PASS | `.\gradlew.bat :library:test :storage:room:testDebugUnitTest :app:testDebugUnitTest detekt --no-configuration-cache --stacktrace`; `BUILD SUCCESSFUL`. |
| Room instrumentation API 37 | PASS | `:storage:room:connectedDebugAndroidTest` on `emulator-5556`; 16/16 tests passed. |
| Room instrumentation API 26 | PASS | Same Room instrumentation command on `emulator-5554` / Android 8.0.0; 16/16 tests passed. |
| Wave-06 repository/schema gate | PASS | The subsequent Wave-06 full verification completed with `exit=0`, reported `Current architecture verified: 8 modules, Room schema 1..3`, and reported stable Room schema export. |

## Verification corrections retained

The first Task-05 focused test compile exposed two test-fixture issues: a generic constructor
reference that Kotlin could not infer and a duplicate top-level fake repository name. Both
were fixed only in tests. The first API-37 Room run then exposed a stale current-schema table
expectation in `DatabaseBaselineTest`; the expectation was updated to include the two schema-3
tables. No migration or production behavior was weakened to make the suite pass.

## Decision

Wave 06 Task 05 is verified. Protected mappings and version-aware rejections are accepted on
Room schema 3, and schemas 1-2 remain unchanged. Task 06 may consume only the public Library
mapping services for review and URL import; it must not rewrite Room ownership or protection
semantics.

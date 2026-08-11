# Wave 06 Task 01 — Metadata-Only Library Membership Verification

Date: 2026-08-10
Status: **VERIFIED**

## Scope

This evidence record covers Wave 06 Task 01 only: introducing `:library`, local
metadata-only Library membership, Room schema `1 -> 2`, Hilt composition, the exact
eight-module post-Baseline policy, and the current-architecture verifier. It does not
accept Wave 06 as a whole and does not authorize content matching, plugin-backed mapping
search, protected content mappings, chapters, Reader, downloads, or background work.

## Implemented boundary

- `:library` is the eighth production module and owns `LibraryEntry`, `LibraryStatus`,
  `LibraryRepository`, and `LibraryService`.
- Task 01 keeps `:library` dependent only on `:core:common`; metadata-only add therefore
  commits without any plugin/runtime dependency.
- Library add is idempotent and preserves an existing status instead of resetting it.
- `:storage:room` owns the private `library_entries` entity/DAO, transaction semantics,
  repository adapter, and migration `1 -> 2`.
- Room schema 1 remains the immutable Architecture Baseline 2 export. Schema 2 adds
  metadata-only Library membership and is the current schema.
- `:app` provides the Hilt binding from the Library persistence port to the Room adapter.
- `scripts/verify-architecture-baseline-2.sh` remains frozen R6 evidence;
  `scripts/verify-current-architecture.sh` derives the current module set and edges from
  `config/architecture/module-boundaries.json` for post-Baseline work.
- The current production graph is exactly eight modules: `:app`, `:core:common`,
  `:catalog`, `:feature:catalog`, `:storage:room`, `:plugins:api`, `:plugins:runtime`,
  and `:library`.

## Verification environment

Verification was reviewed from the target Windows checkout on 2026-08-10. JVM/Gradle
commands ran from PowerShell. Repository shell gates ran from Git Bash. Android
instrumentation ran on a Pixel Android 8.0.0 AVD (API 26) and a Pixel 10 Pro Android 17
AVD (API 37).

## Current evidence

| Gate | Result | Evidence |
|---|---|---|
| App architecture smoke regression | PASS | `./gradlew :app:testDebugUnitTest --rerun-tasks --stacktrace`; 18 tests completed successfully after the current-verifier expectation replaced the stale Baseline-2 expectation. |
| Library/Room/app JVM suites + Detekt | PASS | `./gradlew :library:test :storage:room:testDebugUnitTest :app:testDebugUnitTest detekt --stacktrace`; build successful. |
| Room instrumentation and migration | PASS | `./gradlew :storage:room:connectedDebugAndroidTest --stacktrace`; 13/13 tests passed on API 26 and 13/13 on API 37. |
| Current architecture verifier | PASS | `bash ./scripts/verify-current-architecture.sh`; reported `Current architecture verified: 8 modules, Room schema 1..2.` |
| Current verifier contract | PASS | `bash ./scripts/tests/verify-current-architecture-test.sh`; reported `verify-current-architecture.sh contract verified.` |
| Full repository verification | PASS | `./scripts/verify.sh`; repository script contracts, source/package/structural gates, exact eight-module policy, Gradle verification, lint, Detekt, tests, app assembly, and Room schema stability all passed; final exit status `0`. |
| Frozen Room schema 1 bytes | PASS | SHA-256 restored and verified as `adbd52a78feebd2eee197ccb58f0c209852ca059abd9fe1327bbfa962ba2011a`; full verification then reported `Room schema export remained stable during verification.` |

## Portability hardening

A Windows checkout exposed a byte-level CRLF/LF drift for the frozen schema 1 export:
the working-tree hash differed even though Git's normalized diff was empty. Restoring the
exact Git blob returned schema 1 to the frozen hash and all gates passed. The repository
now declares `storage/room/schemas/**/*.json text eol=lf` in `.gitattributes` so future
checkouts preserve the byte representation required by the schema stability verifier.

## Decision

Wave 06 Task 01 is verified. The canonical continuation advances to Wave 06 Task 02,
which presents Library state in `:feature:catalog`. Task 02 must consume the Task-01
Library contracts without adding another production module or moving Library ownership
into catalog, Room, or app.

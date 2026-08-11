<!-- DOCUMENT LIFECYCLE: EVIDENCE / CHECKPOINT VERIFIED -->

# Wave 08 Reader and Reading Progress Checkpoint

Date: 2026-08-11
Status: **VERIFIED**

## Implemented boundary

- The production graph contains eleven modules, including `:reader` and `:feature:reader`.
- Chapter payloads become bounded, sanitized `ReaderDocument` values before rendering.
- Release selection is pure, deterministic, explained, and retains alternate fallbacks.
- Content loading is store-first, preserves cancellation, quarantines invalid cached data,
  writes valid network results through the store, and falls back across releases.
- Reading progress retains canonical chapter ID, exact release ID, fingerprint, block ID,
  character offset, fraction, and monotonic completion state without carrying a stale
  source-specific position across a release switch.
- Room schema 5 adds transactional `reading_progress` persistence through migration `4 -> 5`.
- Navigation carries stable IDs only; `:feature:reader` owns restorable state and an
  accessible structured-text Compose surface without WebView or live HTML.

## Evidence captured in this environment

| Command | Result |
|---|---|
| `scripts/tests/*.sh` | **PASS** |
| `./scripts/verify-current-architecture.sh` | **PASS** — 11 modules, Room schemas 1..5 |
| `./scripts/verify-package-boundaries.sh` | **PASS** |
| `./scripts/verify-source-layout.sh` | **PASS** |
| `./scripts/verify-structural-suppressions.sh` | **PASS** |
| `./scripts/structural-review-report.sh` | **PASS** — hard policies verified; review candidates reported |
| `./scripts/verify-room-schema-stability.sh` | **PASS** |
| Gradle unit tests, lint, Detekt, APK assembly | **NOT RUN** — pinned Gradle distribution unavailable in the execution environment |
| API 26/API 37 Room, Reader, and app instrumentation | **NOT RUN** — device checkpoint unavailable |

## Acceptance commands still required

Run with the pinned Gradle wrapper in a network-prepared development environment:

```bash
./gradlew :reader:test :feature:reader:testDebugUnitTest :app:testDebugUnitTest \
  :plugins:api:test :plugins:runtime:testDebugUnitTest lintDebug detekt --stacktrace
./scripts/verify.sh
```

Then run `:storage:room`, `:feature:reader`, and `:app` connected instrumentation on both
API 26 and API 37, including launcher smoke and the Room `4 -> 5` migration suite. This
checkpoint must remain open until those outputs are reviewed.

## Ownership review

- `:reader` owns selection, document validation/loading, and progress policy.
- `:feature:reader` owns UI state and Compose rendering.
- `:plugins:runtime` remains the only JavaScript execution boundary.
- `:storage:room` owns entities, DAOs, migrations, and atomic persistence.
- `:app` owns DI composition and stable-ID navigation.

No Wave 09 cache/download/file-storage implementation is included.

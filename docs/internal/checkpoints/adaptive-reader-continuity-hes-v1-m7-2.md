# Adaptive Reader Continuity / HES-v1 - M7.2 Constitutional Hardening Checkpoint

Date: 2026-08-26
Status: **OPEN / REMEDIATION IN PROGRESS**
Updated: 2026-08-26 after Task 8; implementation paused before Task 9 by developer request.

Reason for reopening:

- process-wide foreground Reader REMOTE ceiling `<= 2` is not enforced;
- competitive runtime validation does not enforce total attempts `<= 7`;
- production local-store corruption can collapse into `MissingBlob`;
- pure-engine health percentile uses floating-point arithmetic;
- Task 30/L7 final evidence is narrower than the parent plan;
- session graph/process-shared ownership cleanup is required before re-freeze.

Historical M7/M7.1 command output remains historical evidence and is not rewritten.

## Fresh M7.2 Evidence

No final M7.2 completion or HES-v1 re-freeze is claimed while remediation is in progress.

## Progress Through Task 8

| Task | Result | Commit |
|---|---|---|
| 1 - reopen governance boundary | complete | `f217650` |
| 2 - central runtime limits and route guard | complete | `0895552` |
| 3 - process-wide foreground REMOTE ceiling | complete | `13c401d` |
| 4 - explicit process-shared runtime owners | complete | `f09428e` |
| 5 - typed Reader document-read boundary | complete | `af79826` |
| 6 - Downloads multi-namespace corruption semantics | complete | `a5a5e8b` |
| 7 - typed corruption through Reader execution | complete | `aa2ff90` |
| 8 - integer-only health percentile and static guard | complete | `3660f62` |
| 9-12 - indexed graph, L7 completion, final gates/re-freeze | not started in this execution segment | - |

## Implemented Invariants So Far

- one Reader runtime ceiling owner and one shared sequential/competitive route guard;
- total foreground attempts `<= 7` and foreground REMOTE attempts `<= 4` are checked before effects;
- foreground Reader REMOTE concurrency is `<= 2` process-wide;
- prefetch Reader REMOTE concurrency remains `<= 1` process-wide;
- Reader REMOTE concurrency remains `<= 1` per source, with same-source foreground preemption;
- coordinator/executor construction requires explicit process-shared health/limiter owners;
- local `Missing` and confirmed fingerprint/decode corruption remain distinct through Downloads and Reader;
- a valid automatic-cache copy wins over a corrupt explicit-download copy of the same locator;
- confirmed corruption remains known-invalid for later session snapshots while current bounded recovery continues;
- `:reader:engine` percentile calculation is integer-only and production Float/Double usage is rejected.

## Fresh Commands Recorded Through Task 8

| Command | Result | Notes |
|---|---|---|
| `.\\gradlew.bat :reader:engine:test :reader:testDebugUnitTest :downloads:testDebugUnitTest :feature:reader:testDebugUnitTest --no-daemon` | **PASS** | pre-remediation focused baseline; `BUILD SUCCESSFUL in 26s` |
| `.\\gradlew.bat :reader:testDebugUnitTest --tests '*ReaderRouteRuntimeGuardTest*' --tests '*ReaderRouteExecutorAdaptiveTest*' --tests '*ReaderCompetitiveExecutionTest*' --no-daemon` | **PASS** | Task 2 focused gate; `BUILD SUCCESSFUL in 40s` |
| `.\\gradlew.bat :reader:testDebugUnitTest --tests '*ReaderSourceExecutionLimiterTest*' --tests '*ReaderRuntimeStressTest*' --no-daemon` | **PASS** | Task 3 focused gate; `BUILD SUCCESSFUL in 32s` |
| `.\\gradlew.bat :reader:testDebugUnitTest :feature:reader:testDebugUnitTest :app:compileDebugKotlin --no-daemon` | **PASS** | Task 4 shared-owner gate; `BUILD SUCCESSFUL in 39s` |
| `.\\gradlew.bat :reader:testDebugUnitTest --tests '*ReaderRouteExecutorAdaptiveTest*' --tests '*ReaderRouteReplanTest*' --no-daemon` | **PASS** | Task 7 typed corruption/replan gate; `BUILD SUCCESSFUL in 27s` |
| `.\\gradlew.bat :downloads:testDebugUnitTest --tests '*DownloadAwareReaderDocumentStoreTest*' --tests '*DownloadAwareReaderCacheFactsTest*' --no-daemon` | **PASS** | Task 6 typed store/cache facts gate; `BUILD SUCCESSFUL in 29s` |
| `.\\gradlew.bat :reader:engine:test :build-logic:test --tests '*ModuleGraphTest*' --tests '*ModuleBoundaryVerifierTest*' --no-daemon` | **PASS** | Task 8 pure/architecture gate; `BUILD SUCCESSFUL in 30s` |
| `C:\\Program Files\\Git\\bin\\bash.exe scripts/tests/verify-package-boundaries-test.sh` | **PASS** | Git Bash used explicitly because Windows `bash.exe` resolved to an unavailable WSL runtime |
| `C:\\Program Files\\Git\\bin\\bash.exe scripts/verify-package-boundaries.sh` | **PASS** | `Package boundary policy verified.` |
| `rg` Float/Double and floating-literal scan over `reader/engine/src/main/**/*.kt` | **PASS** | no matches |

## Pause Boundary

The next implementation unit is Task 9, `ReaderSessionChapterGraph`. Tasks 9-12, the I01-I22 final
evidence table, canonical `verify-fast.sh` / `verify.sh`, and HES-v1 re-freeze remain outstanding.

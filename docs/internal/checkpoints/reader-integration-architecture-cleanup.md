# Reader Integration Architecture Cleanup Checkpoint

**Status:** IMPLEMENTED / NOT CLOSED — fresh Gradle host matrix pending.

## Scope

R1–R5 effect-layer cleanup after the HES-v1 Reader Engine final freeze. `reader/engine/**` is unchanged.

## Implemented contracts

- R1: source-availability observation failure degrades REMOTE only; LOCAL facts survive. Foreground and prefetch use a per-execution lazy `ReaderRemoteSourceResolver`; registry failure becomes the existing typed `reader.source_unavailable` attempt failure.
- R2: production `ReaderRouteCoordinator` no longer reads `ReaderDecisionTrace` for control flow; active route recording uses `competitiveSet.primary.releaseId`.
- R3: `ReaderForegroundResult.Committed` no longer duplicates previous/next chapter IDs; Feature Reader remains the reactive navigation projection owner.
- R4: internal route execution/planning/session state carries immutable `ReaderRoutingPreferences(languageOrder)` only; `fontScale` remains a presentation/settings fact.
- R5: `ReaderAttemptOwnership` uses `ReentrantLock`/`Condition`; checked-in baseline profile no longer references removed `ReaderDocumentRepository`, `ReaderLoadRequest`, or `reader/selection/*` contracts.

## Self-review

- No source change under `reader/engine/`.
- No HES/policy/version, ranking, health, hedge, Room, module graph, or dependency change.
- No production `decision.trace` read remains in `:reader`.
- No eager `ReaderRouteExecutor.enabledSources()` API remains.
- No previous/next field remains in `ReaderForegroundResult.Committed`.
- No `fontScale`/full `ReaderPreferences` enters execution/planning routing contexts.
- No raw `Object.wait/notifyAll` remains in `ReaderAttemptOwnership`.
- No retired legacy selector/repository descriptor remains in the checked-in baseline profile.

## Fresh evidence available in sandbox

- Kotlin local compile harness over `reader/engine` + all `reader/routing` production sources: PASS.
- Runtime harness using the actual edited production classes: PASS for availability-failure LOCAL preservation, LOCAL primary registry laziness, typed REMOTE registry failure, font-only no-replan/language hard-replan, and ownership publication/close blocking semantics.
- `git diff --check`: PASS on the final edited tree.
- `scripts/tests/verify-package-boundaries-test.sh`: PASS.
- `scripts/verify-package-boundaries.sh`: PASS.
- `scripts/tests/verify-current-architecture-test.sh`: PASS.
- `scripts/verify-current-architecture.sh`: PASS — 17 production modules, 1 android-test module, Room schema 1..11.
- `scripts/verify-room-schema-stability.sh`: PASS — digest `0c5aced22ed5f88395b422cc4171139e9c9081fbdb266893b37239f587b5fac0`.
- Gradle wrapper cannot bootstrap Gradle 9.5.0 in the sandbox because `services.gradle.org` is not resolvable. This is an environment blocker, not a test result.

## Required host closure matrix

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*RouteSnapshotAssembler*' \
  --tests '*ReaderRouteCoordinatorAdaptiveTest*' \
  --tests '*ReaderRouteExecutorAdaptiveTest*' \
  --tests '*ReaderRemoteSourceResolverTest*' \
  --tests '*ReaderChapterGraphInvalidationTest*' \
  --tests '*ReaderCompetitiveExecutionTest*' \
  --no-daemon

./gradlew :reader:testDebugUnitTest :feature:reader:testDebugUnitTest --no-daemon
./gradlew :app:compileDebugKotlin :downloads:testDebugUnitTest --no-daemon
./gradlew :build-logic:test verifyArchitecture --no-daemon
bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/tests/verify-current-architecture-test.sh
bash scripts/verify-current-architecture.sh
bash scripts/verify-fast.sh
bash scripts/verify-room-schema-stability.sh
git diff --check
```

Only after this matrix is fresh GREEN should current state change from `verification pending` to `VERIFIED/CLOSED` for the Reader integration cleanup. HES-v1 remains frozen regardless because no engine contract changes are part of this scope.

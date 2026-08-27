# Adaptive Reader Continuity / HES-v1 - M7.2 Constitutional Hardening Checkpoint

Date: 2026-08-26
Status: **VERIFIED / CLOSED**
Result: **HES-v1 RE-FROZEN**

M7.2 reopened the HES-v1 freeze prospectively to repair runtime and verification
conformance gaps. Historical M7/M7.1 command output remains historical evidence and
is not rewritten by this checkpoint.

## Closure Scope

M7.2 closes the following remediation boundary:

- exact foreground route and REMOTE-attempt runtime ceilings;
- process-wide foreground, prefetch, per-source, and HALF_OPEN ownership limits;
- explicit process-shared health and limiter construction;
- typed local missing/corruption semantics through Reader and Downloads;
- integer-only pure-engine health percentile math;
- one immutable indexed chapter graph snapshot per accepted session emission;
- bounded deterministic I01-I22 concurrent-model evidence;
- final architecture, policy, lint, assembly, and repository verification gates.

The final tree keeps HES-v1 public versions unchanged, keeps Room at schema 11,
and keeps the graph at 17 production modules plus `:benchmark`.

## Task Progress

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
| 9 - immutable indexed session chapter graph | complete | `c9eefec` |
| 10 - indexed graph migration through session/planning/assembler/prefetch | complete | `c7c33dd` |
| 11 - bounded L7 concurrency evidence | complete | `c7c33dd` |
| late final-gate defect repairs | complete | `c88f447` |
| 12 - final matrix, contradiction audit, and re-freeze | complete | this closure commit |

## Final Invariant-to-Test Evidence

All listed owners ran through fresh final-tree Reader, Feature Reader, or Downloads
commands recorded below.

| ID | Invariant | Exact test owner | Result |
|---|---|---|---|
| I01 | visible commits per generation `<= 1` | `ReaderCoordinatorModelTest.new user intent supersedes an older completion without changing committed state`; `ReaderCoordinatorModelTest.hard invalidation rejects stale plan completion and commits only the revised plan` | **PASS** |
| I02 | stale generation never commits | `ReaderCoordinatorModelTest.staleGenerationAndPlanRevisionCannotCommit` | **PASS** |
| I03 | stale plan revision never commits | `ReaderCoordinatorModelTest.staleGenerationAndPlanRevisionCannotCommit` | **PASS** |
| I04 | committed saved identity never changes before valid commit | `ReaderViewModelContinuityTest.openingNextChapterKeepsCommittedDocumentSavedIdentityAndProgressOwnerUntilCommit`; `ReaderViewModelContinuityTest.selectingReleaseDoesNotPersistOrReplaceCommittedContentUntilTheSelectionCommits` | **PASS** |
| I05 | navigation cancellation never lowers reliability | `ReaderSourceHealthRegistryTest.navigationHedgeAndPrefetchCancellationDoNotPenalizeProcessHealth`; `ReaderCompetitiveExecutionTest.navigation cancellation blocks late success from health and cache effects` | **PASS** |
| I06 | hedge-loser cancellation never lowers reliability | `ReaderSourceHealthRegistryTest.navigationHedgeAndPrefetchCancellationDoNotPenalizeProcessHealth`; `ReaderCompetitiveExecutionTest.hedge winner cancellation does not penalize primary health` | **PASS** |
| I07 | prefetch-preempt cancellation never lowers reliability | `ReaderSourceHealthRegistryTest.navigationHedgeAndPrefetchCancellationDoNotPenalizeProcessHealth`; `ReaderSourceExecutionLimiterTest.foregroundPreemptsActivePrefetchWithTypedNonPenalizingCancellation` | **PASS** |
| I08 | late normal success while OPEN never closes the circuit | `ReaderSourceHealthRegistryTest.lateNormalRemoteSuccessWhileOpenCannotCloseCircuit` | **PASS** |
| I09 | HALF_OPEN probe ownership is unique | `ReaderSourceExecutionLimiterTest.twoSessionsSharingLimiterAllowOnlyOneHalfOpenProbeLease` | **PASS** |
| I10 | ordinary fallbacks remain sequential outside one primary/hedge pair | `ReaderCompetitiveExecutionTest.sequential recovery waits until both competitive attempts fail` | **PASS** |
| I11 | foreground Reader REMOTE concurrency `<= 2` process-wide | `ReaderSourceExecutionLimiterTest.atMostTwoForegroundRemoteAttemptsAreActiveProcessWide`; `ReaderSourceExecutionLimiterTest.twoLogicalSessionsAcrossFourSourcesStayWithinProcessAndSourceCeilings` | **PASS** |
| I12 | foreground Reader REMOTE planned/executed total `<= 4` | `ReaderCompetitiveExecutionTest.foreground competition stays at two concurrent and four total remote attempts`; `ReaderRouteRuntimeGuardTest.competitiveRejectsMoreThanFourRemoteAttempts` | **PASS** |
| I13 | Reader REMOTE per-source lane `<= 1` | `ReaderSourceExecutionLimiterTest.onlyOneReaderRemoteAttemptPerSourceIsActiveAcrossCallers`; `ReaderSourceExecutionLimiterTest.twoLogicalSessionsAcrossFourSourcesStayWithinProcessAndSourceCeilings` | **PASS** |
| I14 | hard invalidation increments plan revision without incrementing generation | `ReaderCoordinatorModelTest.hardInvalidationReplansWithoutGenerationIncrement` | **PASS** |
| I15 | every new foreground user intent increments generation | `ReaderCoordinatorModelTest.navigationSelectionAndRetryEachStartNewGeneration` | **PASS** |
| I16 | soft graph update does not revoke a still-valid plan | `ReaderCoordinatorModelTest.softGraphAdditionDoesNotRevokeActivePlan` | **PASS** |
| I17 | two sessions share health but not generation/plan/commit state | `ReaderCoordinatorModelTest.twoSessionsShareHealthButKeepGenerationPlanAndCommitStateIsolated`; `ReaderRuntimeStressTest.twoSessionsKeepExecutionStateIndependentWhileSharingProcessHealthAndLimiterUnderLoad` | **PASS** |
| I18 | bounded exhaustion produces one semantic UI failure transition | `ReaderViewModelContinuityTest.boundedAttemptExhaustionProducesOneVisibleFailureTransitionForGeneration` | **PASS** |
| I19 | local missing does not create known-invalid state | `ReaderRouteReplanTest.typedMissingDoesNotBecomeKnownInvalidForLaterSnapshot` | **PASS** |
| I20 | confirmed corruption creates known-invalid state only when no valid exact copy survives | `ReaderRouteReplanTest.confirmedTypedCorruptionIsExcludedFromALaterSnapshot`; `DownloadAwareReaderDocumentStoreTest.valid cache survives corrupt explicit copy` | **PASS** |
| I21 | cancelled waiters leak no limiter permit or source lane | `ReaderSourceExecutionLimiterTest.cancellingForegroundWaitingForGlobalPermitDoesNotLeakItsSourceLane`; `ReaderSourceExecutionLimiterTest.foregroundPreemptsSameSourcePrefetchWaitingForGlobalPrefetchPermit` | **PASS** |
| I22 | one final graph context is reused within one graph revision | `ReaderRouteSessionStateTest.foregroundAndPrefetchReuseSameChapterGraphForOneRevision`; `ReaderRouteSessionStateTest.hardReplanReusesSameChapterGraphObjectAndEqualEmissionKeepsRevision` | **PASS** |

## Fresh M7.2 Final-Tree Commands

| Command | Result | Fresh evidence |
|---|---|---|
| `.\gradlew.bat :reader:engine:test --no-daemon` | **PASS** | `BUILD SUCCESSFUL in 8s` |
| `.\gradlew.bat :reader:testDebugUnitTest --no-daemon` | **PASS** | `BUILD SUCCESSFUL in 11s` |
| `.\gradlew.bat :downloads:testDebugUnitTest :feature:reader:testDebugUnitTest :app:testDebugUnitTest :app:compileDebugKotlin --no-daemon` | **PASS** | `BUILD SUCCESSFUL in 1m 2s` |
| `.\gradlew.bat :build-logic:test verifyArchitecture --no-daemon` | **PASS** | `BUILD SUCCESSFUL in 23s` |
| Git Bash package-boundary mutation test and verifier | **PASS** | both scripts reported policy verified |
| Git Bash current-architecture mutation test and verifier | **PASS** | 17 production modules, 1 android-test module, Room schemas 1..11 |
| `scripts/verify-fast.sh` through native Git Bash | **PASS** | `BUILD SUCCESSFUL in 33s`; 388 actionable tasks; Room schema export stable |
| `scripts/verify.sh` through native Git Bash | **PASS** | `BUILD SUCCESSFUL in 1m 58s`; 739 actionable tasks; Room schema export stable |
| Task 12 negated structural source scans | **PASS** | no engine floating point, no private owner defaults, no duplicated runtime constants, no graph hot-path copies |
| `scripts/verify-room-schema-stability.sh` through native Git Bash | **PASS** | schema digest `0c5aced22ed5f88395b422cc4171139e9c9081fbdb266893b37239f587b5fac0` |

No Task 12 blocking command was skipped or replaced with historical output.

## Explicit Constitutional Assertions

- current architecture verifier reports 17 production modules and 1 android-test module;
- schema exports are contiguous from 1 through 11;
- `OpenStoryDatabase` remains version 11;
- `RoomMigrations.MIGRATION_10_11` remains registered;
- no `MIGRATION_11_12` exists;
- `:reader:engine` uses the JVM convention plugin and its only production project dependency is `:core:common`;
- `:reader` consumes `:reader:engine` with `implementation`, never `api`;
- `HesContractVersion.HES_V1`, `ReaderRoutingAlgorithmVersion.READER_ROUTING_V1`, `ReaderPolicyVersion.READER_POLICY_V1`, and `HealthPolicyVersion.HEALTH_POLICY_V1` remain unchanged.

## Contradiction and Diff Audit

Final verification exposed and repaired real stale-tree defects before closure:

- Reader source enumeration was eager even when an exact LOCAL attempt won;
- architecture/performance/roadmap guards referenced retired owners or stale schema/task counts;
- Wave 10 lifecycle wording contradicted its authoritative closed checkpoint;
- behavior-preserving source filenames violated current source-layout policy;
- `ReaderRouteSession.kt` exceeded its source-layout threshold after Task 10;
- three blocking Detekt findings remained in touched Reader/Downloads code.

The repairs are isolated in `c88f447`; this closure commit is documentation-only.
Self-review found no remaining behavioral regression or unresolved constitutional conflict.

## Closure Decision

Every blocking Task 12 gate is evidenced on the final tree. M7.2 is therefore
**VERIFIED/CLOSED**, and **HES-v1 is RE-FROZEN** at the unchanged V1 contract,
17-production-module, Room-schema-11 boundary.

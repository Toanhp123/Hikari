# Adaptive Reader Continuity / HES-v1 — M6 Checkpoint

Status: **VERIFIED / CLOSED**

Next: **M7 READY / UNBLOCKED** at the HES milestone boundary. Wave 10 final host/API 26/API 37 acceptance remains independently open.

## Scope

M6 implements Tasks 27–30 from the rebased HES-v1 plan:

- injected Reader execution delay/monotonic scheduler with strictly increasing production completion stamps;
- pure hedge planning only for an initial REMOTE foreground primary on UNMETERED network after every p95/sample/alternate-score/reliability/source predicate passes;
- one primary plus zero/one hedge, with ordinary recovery remaining sequential;
- typed single-attempt execution shared by adaptive sequential and competitive paths;
- completion facts recorded before coordinator notification and ordered by completion time, PRIMARY role, then stable attempt ID;
- one session-visible semantic winner with best-effort loser cancellation and stale generation/revision barriers;
- deterministic seeded completion, navigation, replan, recovery, health, limiter, prefetch and two-session regression coverage.

Room remains schema 11. M6 adds no entity, DAO, index or migration and does not touch `MIGRATION_10_11`.

## Plan contradictions found and resolved

1. Removing the planned hedge from the ordinary recovery chain could skip the alternate when the primary failed before the 650 ms delay. M6 now starts that alternate immediately after an early primary failure, launches it competitively only while the primary remains unresolved, and suppresses it after an early primary success.
2. The sequential executor returned only an aggregated legacy result, which could not preserve typed recovery scope and cancellation ownership across competition. M6 adds one internal typed-attempt primitive and keeps adaptive sequential execution as its wrapper.
3. Equal completion timestamps and reversed notification delivery were underspecified. Production stamps are strictly increasing, while the registry still implements the full timestamp/PRIMARY/attempt-ID comparator for replay and virtual facts.
4. The first implementation review found the adaptive wrapper still using its older validation path. It was refactored onto the same typed primitive before closure so sequential, prefetch and competitive execution cannot drift.

## TDD and model evidence

Observed RED before implementation:

- `ReaderExecutionSchedulerTest` failed to compile because the scheduler boundary did not exist.
- `HedgePolicyTest` observed `HedgeDirective.Omitted` for the fully eligible foreground case.
- `ReaderCompetitiveExecutionTest` observed zero alternate fetches at the 650 ms hedge boundary.
- the competing execution-state assertion failed until the session gained an explicit PRIMARY/HEDGE state.

Focused GREEN coverage proves:

- virtual delay completes at the exact requested duration and production stamps never regress/repeat;
- every hedge predicate is independently necessary, PREFETCH never hedges, and local semantic strength cannot substitute for remote access score;
- primary-before-delay suppression, hedge-at-delay launch, early-failure immediate alternate, hedge win, equal-time PRIMARY win and reversed notification delivery;
- best-effort loser cancellation without health penalty, both-competitive-terminal recovery, no fallback while a live competitive attempt remains, maximum two concurrent remote attempts and four total remote attempts;
- stale generation and plan revision rejection, deterministic seeded completion permutations, limiter/prefetch/probe/health regression compatibility and navigation cancellation safety.

## Developer-host verification

The final M6 tree is GREEN for:

```text
:reader:engine:test
:reader:testDebugUnitTest
:feature:reader:testDebugUnitTest
:downloads:testDebugUnitTest
:app:testDebugUnitTest
:app:compileDebugKotlin
verifyArchitecture
performance-lifecycle-policy-test.sh
verify-package-boundaries-test.sh
verify-package-boundaries.sh
verify-current-architecture-test.sh
verify-current-architecture.sh
```

The broad Gradle matrix completed with 295 actionable tasks and verified the 18-module architecture view. Static architecture policy continues to report 17 production modules plus `:benchmark`, with Room schemas 1 through 11.

## Closure

M6 Tasks 27–30 are **VERIFIED/CLOSED** on the 17-production-module, Room-schema-11 HES tree. One foreground hedge is active only inside the exact HES-v1 eligibility envelope; deterministic completion arbitration and the session commit barrier prevent callback order, loser completion, navigation, replan or cross-session activity from producing a second visible commit. **M7 is READY / UNBLOCKED**. Wave 10 final API 26/API 37 acceptance remains independently open, so this checkpoint does not close Wave 10 or unblock Wave 11.

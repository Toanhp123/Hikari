# Adaptive Reader Continuity / HES-v1 - M7.5 Final Freeze Hardening Checkpoint

Date: 2026-08-27
Status: **VERIFIED/CLOSED — FRESH FINAL-TREE HOST MATRIX GREEN**
Freeze state: **HES-v1 FINAL RE-FROZEN / REFERENCE-GRADE**

## Scope repaired

1. multi-thread race between post-validation timestamp capture, valid-completion publication, winner snapshot, and loser ownership cancellation;
2. runtime completion/failure envelope coherence with the launched route attempt;
3. final `ReaderRouteSession` result identity + committed target-group/release coherence;
4. remaining obvious test/implementation-only Reader Engine exports;
5. governance decision that `DiagnosticNote.code : RejectionCode` naming cleanup belongs to HES-v2, not V1.

M7.4 `AccessReason` removal remains present. Its separate re-freeze was intentionally folded into this final-tree verification because M7.5 was discovered before M7.4 closure.

## Implementation facts

### Competitive completion

- registry `publish(...)` captures `completedAtNanos` and records the completion under one registry lock;
- `ReaderAttemptOwnership` now uses `OPEN / PUBLISHING / PUBLISHED / SEALED / CLOSED` states;
- winner selection seals future OPEN publications and waits for PUBLISHING publication to linearize before recomputing the registry winner;
- non-winner ownership closes only after the final winner snapshot;
- equal timestamps remain legal and use role then stable attemptId; no unique timestamp or callback-order key was added;
- REMOTE fetch latency remains the executor's fetch-duration measurement and does not include validation/publication/cache time.

### Runtime/result coherence

- `ReaderValidCompletion` requires identity attemptId, loaded releaseId/sourceId, and LOCAL/REMOTE locality to match the route attempt;
- failure outcomes are checked against launched release/source/access mode;
- final session gate requires returned foreground identity to equal the execution context;
- committed chapter group must equal the target group and committed release must belong to that group.

### API hygiene

Removed from production API:

```text
ReaderDecisionTrace.empty(...)
HedgeOmissionReason.NOT_EVALUATED
public HES_V1_MAX_HEALTH_FAILURE_THRESHOLD
```

The empty-trace fixture now exists only in test source. `ReaderDecisionTrace` data fields are unchanged.

## TDD / local sandbox evidence

### RED

A focused compile harness used the real pre-fix `CompetitiveCompletionRegistry.kt` and failed on the required publication API:

```text
error: unresolved reference: publish
```

### GREEN

After implementation, focused harnesses using the real `CompetitiveCompletionRegistry.kt` passed:

```text
GREEN: atomic publish API compiles and records completion
GREEN: seal waits for in-flight publication, equal-time PRIMARY wins, late publication is blocked
```

A local Kotlin compile of all `:reader:engine` production sources plus `:core:common` passed using the available Kotlin 1.9 toolchain with a temporary compatibility annotation for Kotlin 2.4's `ConsistentCopyVisibility`.

The final review then compiled all 15 `:reader:engine` test source files against the same production tree and executed them through a temporary reflection runner outside the repository:

```text
RESULT passed=98 failed=0
```

A fresh concurrency harness compiled the real `CompetitiveCompletionRegistry.kt` with minimal test-only stubs and verified:

```text
RUNTIME_HARNESS_PASS
```

The harness covers atomic publish/record, in-flight seal settlement, equal-time PRIMARY preference, blocking of OPEN late publication, close-vs-publication ordering, and malformed completion-envelope rejection.

A source/bytecode comparison against the supplied pre-M7.5 tree confirms the 20 `ReaderDecisionTrace` data fields/getters/component/copy shape is unchanged; the only class-level removal there is the expected `Companion` that existed solely for the retired `empty(...)` helper.

These local harnesses are additional compile/semantic evidence only; they do not substitute for the repository's Gradle host matrix.

## Final self-review corrections

The post-implementation review found and repaired two documentation/test-coverage gaps without widening behavior:

- runtime completion locality (`loaded.fromStore` vs `RouteAttempt.accessMode`) was already enforced in code but omitted from the M7.5 written invariant; canonical design/plan, M7.5 spec/plan, and this checkpoint now state it explicitly;
- dedicated regressions now reject a loaded source mismatch independently from release mismatch, and reject a committed chapter-group mismatch independently from release membership.

No additional ranking, eligibility, health, persistence, module, schema, or UI behavior change resulted from this review.

## Fresh source/static gates on the final implementation tree

```text
bash scripts/tests/verify-package-boundaries-test.sh  PASS
bash scripts/verify-package-boundaries.sh             PASS
bash scripts/tests/verify-current-architecture-test.sh PASS
bash scripts/verify-current-architecture.sh           PASS
  -> 17 production modules
  -> 1 android-test module
  -> Room schemas 1..11
bash scripts/verify-room-schema-stability.sh          PASS
  -> 0c5aced22ed5f88395b422cc4171139e9c9081fbdb266893b37239f587b5fac0
```

## Fresh real-host closure evidence

The final M7.5 tree was verified on the real repository host on 2026-08-27. The supplied execution log records:

| Gate | Result | Fresh evidence |
|---|---|---|
| `./gradlew :reader:engine:test --no-daemon` | **PASS** | `BUILD SUCCESSFUL in 17s`; 6 actionable tasks |
| `./gradlew :reader:testDebugUnitTest --no-daemon` | **PASS** | `BUILD SUCCESSFUL in 27s`; 61 actionable tasks |
| `./gradlew :feature:reader:testDebugUnitTest :app:compileDebugKotlin --no-daemon` | **PASS** | `BUILD SUCCESSFUL in 52s`; 212 actionable tasks |
| `./gradlew :downloads:testDebugUnitTest :app:testDebugUnitTest --no-daemon` | **PASS** | `BUILD SUCCESSFUL in 1m 5s`; 273 actionable tasks |
| `./gradlew :build-logic:test verifyArchitecture --no-daemon` | **PASS** | `BUILD SUCCESSFUL in 25s`; application identity + 18-module graph verified |
| package/current-architecture mutation + policy scripts | **PASS** | package policy verified; `17 production modules, 1 android-test module, Room schema 1..11` |
| retained host verification (`verify-fast.sh`, `verify.sh`) | **PASS** | structural/source/layout/UI/policy verification completed without blocking failure |
| `bash scripts/verify-room-schema-stability.sh` | **PASS** | digest `0c5aced22ed5f88395b422cc4171139e9c9081fbdb266893b37239f587b5fac0` |
| instrumentation Kotlin compilation | **PASS** | `BUILD SUCCESSFUL in 17s`; 240 actionable tasks |

The Reader compile emits one non-blocking Kotlin warning at `CompetitiveCompletionRegistry.kt:61` about using the Java `Object` type instead of `kotlin.Any`. It does not fail any M7.5 blocking gate and is outside the frozen pure-engine contract; it is retained for the subsequent `:reader` module architecture/hygiene review.

The earlier sandbox network blocker remains historical environment evidence only and is superseded for closure purposes by this fresh real-host matrix.

## Accepted fresh host closure matrix

```bash
./gradlew :reader:engine:test --no-daemon
./gradlew :reader:testDebugUnitTest --no-daemon
./gradlew :feature:reader:testDebugUnitTest :app:compileDebugKotlin --no-daemon
./gradlew :downloads:testDebugUnitTest :app:testDebugUnitTest --no-daemon
./gradlew :build-logic:test verifyArchitecture --no-daemon

bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/tests/verify-current-architecture-test.sh
bash scripts/verify-current-architecture.sh
bash scripts/verify-fast.sh
bash scripts/verify.sh
bash scripts/verify-room-schema-stability.sh

./gradlew :storage:room:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin --no-daemon

git diff --check
```

## Closure decision

**VERIFIED/CLOSED.** The fresh real-host final-tree matrix is GREEN, M7.4 is closed through the same accepted tree, and the final governance state is:

```text
M0–M7.5 VERIFIED/CLOSED
HES-v1 FINAL RE-FROZEN / REFERENCE-GRADE
```

No additional proactive HES-v1 bug-hunt phase is planned. Future Reader work begins as explicit logic/behavior evolution and receives a new design/revision decision when contract semantics change.

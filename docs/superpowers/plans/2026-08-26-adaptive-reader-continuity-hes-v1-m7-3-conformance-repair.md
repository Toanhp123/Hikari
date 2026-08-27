# Adaptive Reader Continuity / HES-v1 M7.3 Conformance Repair Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Repair the four confirmed post-freeze HES-v1 conformance defects without widening Reader scope: reject zero-denominator routing policy, make foreground runtime attempt/results self-identifying, restore true earliest-valid competitive completion semantics including equal-timestamp PRIMARY tie-breaks, and reconcile M7.2/M7.3 governance state.

**Architecture:** Keep `:reader:engine` pure and deterministic; policy invalidity is rejected at construction. Keep semantic failure classification identity-free, but make the foreground runtime outcome envelope carry `ReaderAttemptIdentity` and use that identity at coordinator/session gates instead of relying only on closure-captured context. Keep transport latency measurement separate from competitive valid-completion time. Correct the M6 documentation contradiction from “strictly increasing completion stamps” to a non-decreasing monotonic clock that preserves equal timestamps.

**Tech Stack:** Kotlin/JVM pure engine, Android/Kotlin `:reader`, kotlinx.coroutines/test scheduler, Gradle 9.5.x, existing HES architecture/package verifiers. No Room schema change, no new module, no public engine version bump.

**Spec:** `docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md`

## Scope

This repair includes only:

1. zero access-weight denominator fail-fast;
2. self-identifying foreground attempt/result runtime contracts;
3. valid-completion timestamp ordering + equal-timestamp production tie semantics;
4. HES-v1 governance/current-state reconciliation.

Explicitly out of scope:

- `AccessReason` API cleanup or redesign;
- HES-v2/public engine contract expansion;
- Reader ranking/eligibility formula changes other than policy-domain validation;
- persistence/schema changes;
- new telemetry;
- prefetch identity redesign;
- UI behavior changes.

## Global Constraints

- `:reader:engine` remains JVM-only and must not gain Android, coroutine, storage, network, session, or process-runtime dependencies.
- HES contract/version constants remain unchanged: `HES_V1`, `READER_ROUTING_V1`, `READER_POLICY_V1`, `HEALTH_POLICY_V1`.
- Room remains schema 11; no `MIGRATION_11_12`.
- Routing weights still total exactly `10_000` basis points; this repair adds the derived invariant that REMOTE access scoring must have a strictly positive access-weight denominator.
- Competitive winner order remains `valid completedAtNanos -> PRIMARY/HEDGE/FALLBACK role -> stable attemptId`.
- `completedAtNanos` means the timestamp immediately after document validation succeeds, before valid-completion publication/notification.
- Remote source latency remains fetch/source-effect latency and must not silently absorb validation/cache/notification time.
- Callback delivery order must never become winner policy.
- Foreground runtime results that cross the executor/competition/coordinator boundary must carry `(sessionId, generationId, planRevision, attemptId, targetChapterId)`.
- `ReaderAttemptFailure` remains a typed semantic failure fact; the identity-bearing runtime failure result/envelope owns attempt identity so the classifier does not acquire session-state responsibility.
- Visible state/commit checks must consume identity from the runtime result where a result is available; closure `context` may provide surrounding graph/preferences but must not be the sole stale-result proof.
- Existing process-shared health/limiter/scheduler ownership remains unchanged.
- No `AccessReason` modification in M7.3.

---

## File Structure / Expected Touch Set

### Pure engine

- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRoutingPolicy.kt`
  - own policy-domain denominator invariant.
- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/CandidateEvaluator.kt`
  - consume the canonical REMOTE access-weight denominator from policy.
- Modify: `reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderRoutingPolicyTest.kt`
  - exact fail-fast regression.
- Modify: `reader/engine/src/test/kotlin/app/openstory/reader/engine/CandidateEvaluatorTest.kt`
  - prove the smallest valid positive access denominator is safe.

### Reader runtime identity

- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderExecutionState.kt`
  - central attempt-identity derivation/matching helpers.
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/CompetitiveCompletionRegistry.kt`
  - identity-bearing completion/failure outcome and competition callbacks.
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
  - foreground attempt identity input and runtime outcome construction.
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
  - propagate base execution identity and gate returned results by their own identity.
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt`
  - include `sessionId` in active matching and accept result-owned attempt identity at validating gate.
- Keep semantic classifier ownership in:
  - `reader/src/main/kotlin/app/openstory/reader/routing/ReaderAttemptFailure.kt`
  - `reader/src/main/kotlin/app/openstory/reader/routing/ReaderSourceFailureClassifier.kt`
  - no structural change; retain these as identity-free semantic payload facts. Update comments only to state that the owning runtime envelope carries identity.

### Competitive timing

- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
  - move competitive timestamp capture to after successful validation while preserving fetch latency sampling.
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderExecutionScheduler.kt`
  - enforce non-decreasing, not artificially unique, timestamps.
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderExecutionSchedulerTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCompetitiveExecutionTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCoordinatorModelTest.kt`
- Modify if focused executor assertion is cleaner there: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteExecutorAdaptiveTest.kt`

### Governance

- Modify: `docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md`
- Modify: `docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md`
- Create: `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-3.md`
- Modify: `docs/project/current-state.md`
- Modify: `docs/implementation/current-roadmap.md`
  - keep the second canonical "current" status surface aligned with `current-state.md`; historical checkpoints remain untouched.
- This M7.3 plan file is retained as implementation evidence.

---

# Task 1 — Fail Fast on Zero REMOTE Access Weight

**Finding repaired:** High — valid routing policy can reach `CandidateEvaluator.remoteAccessScore()` with `totalAccessWeight == 0` and throw `ArithmeticException`.

**Files:**

- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRoutingPolicy.kt:26-52`
- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/CandidateEvaluator.kt:189-201`
- Modify: `reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderRoutingPolicyTest.kt:79-105`
- Modify: `reader/engine/src/test/kotlin/app/openstory/reader/engine/CandidateEvaluatorTest.kt`

**Interfaces:**

- Existing constructor remains `ReaderRoutingWeights(...)`.
- Add a derived property only if it improves readability:

```kotlin
internal val totalRemoteAccessWeight: Int
    get() = health.value + reliability.value + latency.value + cacheUtility.value
```

Use this canonical property in `CandidateEvaluator.remoteAccessScore(...)`; do not re-sum the denominator independently.

**Invariant:**

```text
weights.total == 10_000
AND
health + reliability + latency + cacheUtility > 0
```

Do not silently normalize or substitute another denominator.

- [ ] **Step 1: Add the RED policy regression**

Extend `invalidWeightsAndBudgetsFailFast()` or add a dedicated test:

```kotlin
@Test
fun zeroRemoteAccessWeightFailsFast() {
    assertFailsWith<IllegalArgumentException> {
        ReaderRoutingWeights(
            language = BasisPoints(10_000),
            continuity = BasisPoints(0),
            health = BasisPoints(0),
            reliability = BasisPoints(0),
            completeness = BasisPoints(0),
            latency = BasisPoints(0),
            freshness = BasisPoints(0),
            cacheUtility = BasisPoints(0),
        )
    }
}
```

Expected before fix: construction succeeds.

- [ ] **Step 2: Add the boundary-valid regression**

Prove the smallest positive denominator remains legal, e.g. one access point plus `9_999` semantic points. If using `CandidateEvaluatorTest`, run a REMOTE candidate through evaluation and assert no arithmetic failure and `remoteAccessScore` remains in `0..10_000`.

- [ ] **Step 3: Run focused engine tests and verify RED**

```bash
./gradlew :reader:engine:test \
  --tests '*ReaderRoutingPolicyTest*' \
  --tests '*CandidateEvaluatorTest*' \
  --no-daemon
```

Expected: zero-denominator regression fails before implementation; unrelated tests remain green.

- [ ] **Step 4: Implement the invariant at the policy boundary**

In `ReaderRoutingWeights.init`, retain the exact total check and add:

```kotlin
require(totalRemoteAccessWeight > 0) {
    "Reader routing REMOTE access weights must contain at least one positive weight."
}
```

If `CandidateEvaluator` uses the new property, replace its duplicated sum with `weights.totalRemoteAccessWeight`. Do not add a defensive `maxOf(1, denominator)` or fallback score; invalid policy must not enter planning.

- [ ] **Step 5: Run focused + full pure-engine GREEN**

```bash
./gradlew :reader:engine:test \
  --tests '*ReaderRoutingPolicyTest*' \
  --tests '*CandidateEvaluatorTest*' \
  --no-daemon

./gradlew :reader:engine:test --no-daemon
```

Acceptance:

- invalid zero-access policy fails at construction;
- minimum positive denominator works;
- no formula/ranking golden changes under default V1 policy.

- [ ] **Step 6: Commit isolated correctness fix**

```bash
git add reader/engine/src/main/kotlin/app/openstory/reader/engine/ReaderRoutingPolicy.kt \
        reader/engine/src/main/kotlin/app/openstory/reader/engine/internal/CandidateEvaluator.kt \
        reader/engine/src/test/kotlin/app/openstory/reader/engine/ReaderRoutingPolicyTest.kt \
        reader/engine/src/test/kotlin/app/openstory/reader/engine/CandidateEvaluatorTest.kt
git commit -m "fix(reader-engine): reject zero access-weight routing policy"
```

---

# Task 2 — Make Foreground Runtime Attempt/Result Identity Self-Contained

**Finding repaired:** Medium — foreground completion/failure correctness relies on closure-captured `context`; runtime result contracts do not themselves carry the full HES tuple.

**Design decision:** Do **not** push session identity into the semantic failure classifier. `ReaderAttemptFailure` remains typed failure facts. The identity-bearing runtime result is `ReaderAttemptOutcome` / route-execution outcome. `ReaderValidCompletion` also carries its own `ReaderAttemptIdentity` because it is independently recorded and compared by the competition registry.

**Files:**

- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderExecutionState.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/CompetitiveCompletionRegistry.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt`
- Modify tests:
  - `ReaderRouteSessionStateTest.kt`
  - `ReaderCoordinatorModelTest.kt`
  - `ReaderCompetitiveExecutionTest.kt`
  - relevant `ReaderRouteExecutor*Test.kt` helper construction

**Interfaces:**

Centralize derivation:

```kotlin
internal fun ReaderExecutionIdentity.forAttempt(attemptId: String) = ReaderAttemptIdentity(
    sessionId = sessionId,
    generationId = generationId,
    planRevision = planRevision,
    attemptId = attemptId,
    targetChapterId = targetChapterId,
)
```

Add execution matching:

```kotlin
internal fun ReaderAttemptIdentity.belongsTo(execution: ReaderExecutionIdentity): Boolean =
    sessionId == execution.sessionId &&
        generationId == execution.generationId &&
        planRevision == execution.planRevision &&
        targetChapterId == execution.targetChapterId
```

Completion contract becomes:

```kotlin
internal data class ReaderValidCompletion(
    val identity: ReaderAttemptIdentity,
    val attempt: RouteAttempt,
    val loaded: ReaderLoadResult.Success,
    val completedAtNanos: Long,
) {
    init {
        require(identity.attemptId == attempt.attemptId) {
            "Reader completion identity must match its route attempt."
        }
        require(completedAtNanos >= 0L)
    }
}
```

Failure runtime result carries identity without contaminating semantic facts:

```kotlin
internal sealed interface ReaderAttemptOutcome {
    val identity: ReaderAttemptIdentity

    data class Success(val completion: ReaderValidCompletion) : ReaderAttemptOutcome {
        override val identity: ReaderAttemptIdentity
            get() = completion.identity
    }

    data class Failure(
        override val identity: ReaderAttemptIdentity,
        val failure: ReaderAttemptFailure,
    ) : ReaderAttemptOutcome
}
```

`ReaderRouteExecutionOutcome.failures` must retain identity. Prefer:

```kotlin
internal data class ReaderRouteExecutionOutcome(
    val completion: ReaderValidCompletion?,
    val failures: List<ReaderAttemptOutcome.Failure>,
)
```

Coordinator converts only the payload at the public failure boundary:

```kotlin
execution.failures.map { it.failure.toLoadFailure() }
```

**Important:** `ReaderSourceFailureClassifier.classifyRemote(...)` remains identity-free and returns semantic `ReaderAttemptFailure` facts.

- [ ] **Step 1: Add RED identity contract tests**

In `ReaderRouteSessionStateTest`/`ReaderCoordinatorModelTest`, add assertions that:

```text
ReaderValidCompletion has identity tuple
ReaderAttemptOutcome.Failure has identity tuple
completion.identity.attemptId must equal completion.attempt.attemptId
cross-session identity cannot be accepted by a ReaderRouteSession
old generation identity cannot mark validating
old planRevision identity cannot mark validating
wrong targetChapterId identity cannot mark validating
```

Also strengthen the existing reflection contract so `ReaderAttemptIdentity` is exactly:

```text
sessionId
generationId
planRevision
attemptId
targetChapterId
```

with no second revision/hash.

- [ ] **Step 2: Run identity-focused tests and verify RED**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderRouteSessionStateTest*' \
  --tests '*ReaderCoordinatorModelTest*' \
  --tests '*ReaderCompetitiveExecutionTest*' \
  --no-daemon
```

Expected before fix: result types cannot satisfy the new identity assertions and cross-session matching exposes that `matchesActiveLocked()` currently does not compare `sessionId`.

- [ ] **Step 3: Centralize attempt identity construction**

Move duplicate construction from `ReaderRouteSession.attemptIdentity(...)` into `ReaderExecutionIdentity.forAttempt(...)`. Replace session-local construction with the shared helper.

Update `ReaderRouteSession.matchesActiveLocked(identity)` to include:

```kotlin
identity.sessionId == sessionId
```

before generation/revision/target checks.

- [ ] **Step 4: Propagate execution identity into competitive execution**

Change `ReaderCompetitiveExecution.execute(...)` to receive the base foreground identity:

```kotlin
suspend fun execute(
    executionIdentity: ReaderExecutionIdentity,
    primary: RouteAttempt,
    hedgeDirective: HedgeDirective,
    recoveryChain: List<RouteAttempt>,
): ReaderRouteExecutionOutcome
```

For every launched primary/hedge/fallback attempt derive once:

```kotlin
val attemptIdentity = executionIdentity.forAttempt(attempt.attemptId)
```

Pass that same object to:

- the attempt executor;
- `onAttemptStarted`;
- success/failure outcome construction;
- validating/loser paths where identity is relevant.

Do not regenerate identity independently in multiple layers for the same attempt.

- [ ] **Step 5: Make the executor construct identity-bearing foreground outcomes**

Extend the internal foreground attempt primitive to accept `identity: ReaderAttemptIdentity` and assert:

```kotlin
require(identity.attemptId == attempt.attemptId)
```

Success constructs `ReaderValidCompletion(identity = identity, ...)`.

Failure wraps semantic facts:

```kotlin
ReaderAttemptOutcome.Failure(
    identity = identity,
    failure = failure,
)
```

Keep `executeAdaptive(...)` as the prefetch/sequential compatibility path. Do **not** invent a fake foreground generation for prefetch. Split the executor internally into an identity-free effect outcome and a foreground identity-bearing wrapper. Add this private executor-local type:

```kotlin
private sealed interface ReaderAttemptEffectOutcome {
    data class Success(
        val loaded: ReaderLoadResult.Success,
        val completedAtNanos: Long,
    ) : ReaderAttemptEffectOutcome

    data class Failure(
        val failure: ReaderAttemptFailure,
    ) : ReaderAttemptEffectOutcome
}
```

`executeLocalAttempt(...)` and `executeRemoteAttempt(...)` return `ReaderAttemptEffectOutcome`. The foreground `executeAttempt(identity, ...)` wrapper converts that payload into `ReaderAttemptOutcome` and publishes `ReaderValidCompletion`. `executeAdaptive(...)` consumes `ReaderAttemptEffectOutcome` directly and therefore needs no synthetic or nullable foreground identity.

- [ ] **Step 6: Consume result-owned identity at the coordinator/session gate**

In `ReaderRouteCoordinator.executeRecordedForeground(...)`:

1. pass `context.identity` into `ReaderCompetitiveExecution.execute(...)`;
2. when a completion returns, verify `completion.identity.belongsTo(context.identity)`;
3. call a session validating gate that accepts the returned `ReaderAttemptIdentity`, not only `(context, attemptId)`;
4. if the identity is stale/mismatched, return `Superseded` or trigger the existing plan-revision replan behavior rather than allowing visible commit;
5. only then build `ReaderForegroundResult.Committed`.

The session remains final serialized visible-state authority. Result identity is additional explicit proof, not a replacement for the commit gate.

- [ ] **Step 7: Add a stale-result injection regression**

Add one coordinator/model test where an outcome/completion from:

```text
same attemptId but old generation
or
same generation but old planRevision
```

is delivered after the active execution advances. Assert:

```text
no Validating state for stale identity
no visible commit
no committedIdentity mutation
active generation/revision remains authoritative
```

This test must fail if result identity is ignored and correctness regresses back to closure-only context.

- [ ] **Step 8: Run all identity/routing tests GREEN**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderRouteSessionStateTest*' \
  --tests '*ReaderCoordinatorModelTest*' \
  --tests '*ReaderCompetitiveExecutionTest*' \
  --tests '*ReaderRouteExecutor*' \
  --tests '*ReaderRouteCoordinator*' \
  --no-daemon
```

Acceptance:

- each foreground attempt outcome is self-identifying;
- `ReaderValidCompletion` is self-identifying;
- session ID participates in active matching;
- stale result identity is actually consumed by the gate;
- classifier semantics remain unchanged;
- prefetch gets no synthetic foreground identity.

- [ ] **Step 9: Commit the runtime-contract repair**

```bash
git add reader/src/main/kotlin/app/openstory/reader/routing \
        reader/src/test/kotlin/app/openstory/reader/routing
git commit -m "fix(reader): carry execution identity in runtime outcomes"
```

---

# Task 3 — Restore Earliest Valid Completion and Real Equal-Timestamp Tie Semantics

**Finding repaired:** Medium — REMOTE completion time is captured before validation, and `DefaultReaderExecutionScheduler` converts equal/raw-backward timestamps into globally unique timestamps, preventing the normative PRIMARY equal-time tie-break from existing in production.

**Files:**

- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt:190-271`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderExecutionScheduler.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderExecutionSchedulerTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCompetitiveExecutionTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCoordinatorModelTest.kt`
- Modify normative docs in this task:
  - `docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md`
  - `docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md`

**Timing model:**

```text
startedNanos = clock()
fetch/source effect
fetchCompletedNanos = clock()
transportLatency = fetchCompletedNanos - startedNanos
validate document
validCompletedAtNanos = clock()
record ReaderValidCompletion(validCompletedAtNanos)
notify coordinator
health observation uses transportLatency
cache persistence remains best effort after semantic valid completion
```

A valid completion timestamp is not a fetch timestamp and not a cache-persistence timestamp.

**Scheduler model:**

Given raw monotonic readings, exported readings are non-decreasing:

```kotlin
next = maxOf(raw, previous)
```

not:

```kotlin
next = maxOf(raw, previous + 1L)
```

Therefore equal raw readings remain equal and role/attemptId comparator branches are reachable in production.

- [ ] **Step 1: Replace the scheduler test with exact non-decreasing semantics**

Change the current test `production completion stamps never move backward or repeat` to a contract such as:

```kotlin
@Test
fun `production monotonic stamps never move backward and preserve ties`() {
    val rawValues = ArrayDeque(listOf(10L, 9L, 10L, 15L))
    val scheduler = DefaultReaderExecutionScheduler.forTest(
        delayBlock = {},
        rawMonotonicNanos = rawValues::removeFirst,
    )

    assertEquals(listOf(10L, 10L, 10L, 15L), List(4) { scheduler.monotonicNanos() })
}
```

Expected before fix: observed values are strictly increasing and test fails.

- [ ] **Step 2: Add RED valid-completion timing regression**

Add a focused executor test proving that a successful REMOTE attempt consumes a **third** monotonic reading for valid completion after the fetch-completion reading used for latency. The competitive timestamp must therefore be a post-validation read, not the fetch timestamp.

Use the existing injected `monotonicNanos` boundary; no validator abstraction is added. Drive the clock with exact values such as `0L`, `2_000_000L`, `9_000_000L` for one successful REMOTE attempt and capture `ReaderValidCompletion` from `onValidCompletion`. After the fix assert `completedAtNanos == 9_000_000L` while `SourceObservation.Success.Remote.latencyMillis == 2L`. Before the fix the completion consumes the second reading and the third reading is unused.

This proves the competitive timestamp is a post-validation read while remote health latency still uses only source/fetch duration.

- [ ] **Step 3: Retain and strengthen equal-timestamp winner tests**

Existing model coverage already asserts equal timestamp PRIMARY wins across record/delivery permutations. Add a production-scheduler-backed path so the test proves equal timestamps can actually survive the real scheduler implementation.

Required cases:

```text
PRIMARY valid @700, HEDGE valid @700 -> PRIMARY
same role + same timestamp -> stable attemptId
record order reversed -> same winner
notification delivery order reversed -> same winner
```

- [ ] **Step 4: Run timing tests and verify RED**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderExecutionSchedulerTest*' \
  --tests '*ReaderCompetitiveExecutionTest*' \
  --tests '*ReaderCoordinatorModelTest*' \
  --tests '*ReaderRouteExecutor*' \
  --no-daemon
```

Expected before fix:

- scheduler tie-preservation regression fails;
- post-validation ordering regression fails for REMOTE success.

- [ ] **Step 5: Implement non-decreasing scheduler stamps**

Replace uniqueness forcing with non-decreasing clamping. Preserve:

```text
raw >= 0
thread-safe process-shared clock boundary
no wall-clock substitution
```

Do not add attempt-role knowledge to the scheduler. Tie policy stays exclusively in `CompetitiveCompletionRegistry`.

- [ ] **Step 6: Move REMOTE competitive timestamp after validation**

In `executeRemoteAttempt(...)` keep two independent readings:

```kotlin
val startedNanos = monotonicNanos()
val fetched = fetch(...)
val fetchCompletedNanos = monotonicNanos()
val latencyMillis = elapsedMillis(startedNanos, fetchCompletedNanos)
```

For `ReaderSourceResult.Success` + `ReaderDocumentValidation.Valid`, only after validation succeeds:

```kotlin
ensureOwned(ownership)
val validCompletedAtNanos = monotonicNanos()
val completion = ReaderValidCompletion(
    identity = identity,
    attempt = attempt,
    loaded = ...,
    completedAtNanos = validCompletedAtNanos,
)
onValidCompletion(completion)
```

Keep health observation using `latencyMillis` from `fetchCompletedNanos`. Keep best-effort persistence after semantic completion as today.

LOCAL already timestamps after local validation; retain that ordering and bring naming/comments into symmetry if useful.

- [ ] **Step 7: Correct the normative M6 contradiction**

In the HES-v1 design §49 retain/clarify:

```text
completedAtNanos is captured immediately after successful validation
clock is monotonic/non-decreasing
identical timestamps are legal
winner ties use PRIMARY, then stable attemptId
```

In canonical plan M6 execution amendment replace the contradictory claim:

```text
production completion stamps are strictly increasing
```

with normative wording equivalent to:

```text
production completion stamps are non-decreasing; equal readings are preserved so the
completedAtNanos -> PRIMARY -> attemptId comparator is semantically reachable in production.
```

Also make Task 29 wording explicit that “valid completion” means timestamp after validation, not after fetch.

Do not alter HES version constants.

- [ ] **Step 8: Run timing + full Reader regressions GREEN**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderExecutionSchedulerTest*' \
  --tests '*ReaderCompetitiveExecutionTest*' \
  --tests '*ReaderCoordinatorModelTest*' \
  --tests '*ReaderRouteExecutor*' \
  --no-daemon

./gradlew :reader:testDebugUnitTest --no-daemon
```

Acceptance:

- fetch-first does not imply valid-first;
- remote latency metric remains fetch latency;
- equal production timestamps survive scheduler;
- PRIMARY tie-break is reachable and deterministic;
- callback delivery order remains irrelevant.

- [ ] **Step 9: Commit timing semantics + normative correction together**

These source and docs edits must stay in one commit because either side alone leaves HES contradictory.

```bash
git add reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt \
        reader/src/main/kotlin/app/openstory/reader/routing/ReaderExecutionScheduler.kt \
        reader/src/test/kotlin/app/openstory/reader/routing/ReaderExecutionSchedulerTest.kt \
        reader/src/test/kotlin/app/openstory/reader/routing/ReaderCompetitiveExecutionTest.kt \
        reader/src/test/kotlin/app/openstory/reader/routing/ReaderCoordinatorModelTest.kt \
        reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteExecutorAdaptiveTest.kt \
        docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md \
        docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md
git commit -m "fix(reader): order hedge winners by valid completion"
```

---

# Task 4 — Reconcile M7.2 Governance and Close M7.3

**Finding repaired:** Low — canonical current state still reports M7.1 while the accepted checkpoint says M7.2 closed/re-frozen; after this repair the repository must have one unambiguous HES-v1 status.

**Files:**

- Modify: `docs/project/current-state.md`
- Modify: `docs/implementation/current-roadmap.md`
- Modify: `docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md`
- Modify: `docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md`
- Create: `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-3.md`

**Governance rule:** Do not mark M7.3 `VERIFIED/CLOSED` until all final-tree blocking commands below have fresh evidence. Until then wording is `M7.3 IN PROGRESS / HES-v1 freeze reopened for conformance repair`.

**Closure note (2026-08-27):** The initial supplied-ZIP verification was environment-limited, so governance correctly remained open. The patch was then applied in the real repository and the developer supplied fresh final-tree evidence: `:reader:engine:test`, `:reader:testDebugUnitTest`, downstream Reader/Downloads/App tests and compile, `:build-logic:test verifyArchitecture`, package/current-architecture mutation contracts and verifiers, `verify-fast.sh`, `verify.sh`, Room schema stability, and instrumentation compilation all passed. Room digest remains `0c5aced22ed5f88395b422cc4171139e9c9081fbdb266893b37239f587b5fac0`. M7.3 is therefore closed and HES-v1 re-frozen. `docs/implementation/current-roadmap.md` remains in the Task 4 touch set because Task 4 review identified it as a second canonical current-status surface.

- [x] **Step 1: Reopen status without rewriting accepted M7.2 history**

At implementation start, update the design/plan post-freeze note to state that M7.2 remains historical accepted evidence but M7.3 temporarily reopens the freeze for the four scoped conformance repairs.

Do not edit the old M7.2 checkpoint to pretend it never closed.

- [x] **Step 2: Update policy-validation normative text**

Design §62 must add the derived invariant explicitly:

```text
REMOTE access-score weight denominator (health + reliability + latency + cacheUtility) > 0
```

This makes Task 1 a normative contract rather than an undocumented implementation guard.

- [x] **Step 3: Clarify runtime identity wording without widening prefetch scope**

Design §44.3 and canonical plan global constraint must describe the actual repaired boundary precisely:

```text
Every foreground execution attempt/result that crosses the executor/competition/coordinator
runtime boundary carries (sessionId, generationId, planRevision, attemptId, targetChapterId).
```

Add one explicit sentence:

```text
Identity-free ReaderAttemptFailure is a semantic payload, not the runtime result envelope;
ReaderAttemptOutcome.Failure carries the owning ReaderAttemptIdentity.
```

Do not invent a fake foreground generation for opportunistic prefetch. Prefetch retains its existing session/token cancellation ownership and remains outside visible commit.

This wording resolves the prior over-broad phrase “every runtime attempt/result” without weakening foreground stale-commit safety.

- [x] **Step 4: Run focused final-tree suites**

```bash
./gradlew :reader:engine:test --no-daemon
./gradlew :reader:testDebugUnitTest --no-daemon
```

Expected: all engine and Reader tests pass.

- [x] **Step 5: Run downstream Reader regression suites**

```bash
./gradlew \
  :downloads:testDebugUnitTest \
  :feature:reader:testDebugUnitTest \
  :app:testDebugUnitTest \
  :app:compileDebugKotlin \
  --no-daemon
```

Expected: no behavior regression outside Reader runtime.

- [x] **Step 6: Run architecture/build-logic gates**

```bash
./gradlew :build-logic:test verifyArchitecture --no-daemon

bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/tests/verify-current-architecture-test.sh
bash scripts/verify-current-architecture.sh
```

Expected constitutional assertions remain:

```text
17 production modules
1 android-test module
Room schemas 1..11
:reader:engine JVM-only
:reader:engine production dependency only :core:common
:reader consumes :reader:engine via implementation
```

If the current-architecture mutation test remains pathologically slow specifically under Git Bash/Windows, record exact command, elapsed time, host, and termination as environment evidence; do not silently substitute the non-mutation verifier and claim full closure.

- [x] **Step 7: Run retained M7.2 final-tree host gates before re-freeze**

```bash
bash scripts/verify-fast.sh
bash scripts/verify.sh
bash scripts/verify-room-schema-stability.sh
```

No schema change is expected. Room schema digest/export must remain stable.

Always compile instrumentation as required by the canonical HES closure contract:

```bash
./gradlew :storage:room:compileDebugAndroidTestKotlin :app:compileDebugAndroidTestKotlin --no-daemon
```

When API 26/API 37 devices or emulators are available, rerun the HES-relevant connected matrix and record exact device/API evidence. When devices are unavailable, record the connected matrix as `NOT RUN — environment unavailable`; do not fabricate fresh device evidence. M7.3 does not alter Room/schema/device-only code, so prior M7.2 device evidence remains historical evidence, not a substitute for a claimed fresh run.

- [x] **Step 8: Perform contradiction/diff audit before closure**

Search the final tree for stale statements:

```bash
rg -n "M0.?M7\.1|M7\.1 VERIFIED|strictly increasing.*completion|never move backward or repeat|M7\.2.*FROZEN" docs reader
rg -n "AccessReason" reader docs/superpowers/specs docs/superpowers/plans
```

Interpretation:

- no stale M7.1 canonical HES status may remain;
- no normative “strictly increasing completion stamps” may remain;
- `AccessReason` hits are **expected and out of scope**; do not change them in this phase.

Also inspect:

```bash
git diff --check
git status --short
git diff --stat
```

- [x] **Step 9: Write M7.3 checkpoint only from fresh evidence**

Create `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-3.md` with:

```text
scope/findings repaired
exact commits
focused test counts/results
broad host-gate results
architecture assertions
schema unchanged statement
known environment blockers, if any
explicit AccessReason deferral
contradiction audit result
closure decision
```

If any blocking final-tree gate is not green, checkpoint status must remain `NOT CLOSED` and `current-state.md` must not claim re-freeze.

- [x] **Step 10: Update canonical current state after evidence is green**

Change `docs/project/current-state.md` HES entry to the final truth, e.g.:

```text
Adaptive Reader Continuity / HES-v1: M0–M7.3 VERIFIED/CLOSED; HES-v1 RE-FROZEN.
```

The paragraph must point to:

```text
canonical design
canonical plan
M7.2 historical checkpoint
M7.3 conformance-repair checkpoint
```

and summarize only the four M7.3 repairs. Do not claim `AccessReason` debt resolved.

- [x] **Step 11: Commit governance closure**

```bash
git add docs/project/current-state.md \
        docs/implementation/current-roadmap.md \
        docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md \
        docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md \
        docs/internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-3.md \
        docs/superpowers/plans/2026-08-26-adaptive-reader-continuity-hes-v1-m7-3-conformance-repair.md
git commit -m "docs(reader): close HES-v1 M7.3 conformance repair"
```

---

# Final Acceptance Matrix

M7.3 may close only when all applicable blocking rows are true:

| ID | Contract | Required evidence |
|---|---|---|
| C1 | zero REMOTE access denominator rejected at policy construction | `ReaderRoutingPolicyTest` |
| C2 | minimum positive access denominator remains valid | policy/evaluator focused test |
| C3 | foreground valid completion carries full `ReaderAttemptIdentity` | completion contract test |
| C4 | foreground failure runtime outcome carries full identity | outcome contract test |
| C5 | result identity attemptId matches route attemptId | constructor/guard test |
| C6 | session ID participates in active execution matching | cross-session stale test |
| C7 | stale generation result cannot validate/commit | coordinator/session regression |
| C8 | stale plan revision result cannot validate/commit | coordinator/session regression |
| C9 | valid-completion timestamp is captured after validation | executor/competition timing regression |
| C10 | remote health latency remains fetch/source latency | health observation assertion |
| C11 | production monotonic scheduler preserves equal timestamps | `ReaderExecutionSchedulerTest` |
| C12 | equal timestamp PRIMARY tie is reachable and deterministic | production-scheduler competition test |
| C13 | record/delivery order does not alter winner | permutation/model test |
| C14 | canonical M6 wording no longer requires unique timestamps | docs contradiction audit |
| C15 | design §62 owns positive access denominator invariant | docs review |
| C16 | `current-state.md` no longer reports stale M7.1 | docs contradiction audit |
| C17 | architecture/module/schema boundaries unchanged | architecture + schema verifiers |
| C18 | AccessReason explicitly remains deferred | M7.3 checkpoint + unchanged source |

## Required Commit Shape

Prefer four clean commits:

```text
1. fix(reader-engine): reject zero access-weight routing policy
2. fix(reader): carry execution identity in runtime outcomes
3. fix(reader): order hedge winners by valid completion
4. docs(reader): close HES-v1 M7.3 conformance repair
```

Do not squash Tasks 1–3 into one opaque remediation commit unless repository policy requires it. Each behavior repair should be independently reviewable and revertible.

## Stop Conditions

Stop and re-audit before proceeding if any of these occur:

```text
fix requires :reader:engine to know session/coroutine/runtime types
identity fix requires synthetic foreground IDs for prefetch
remote latency changes when only validation delay is changed
winner becomes dependent on callback/channel delivery order
fix requires HES/algorithm/policy version bump
Room schema or module graph changes
more than one new runtime identity/version space is introduced
AccessReason cleanup starts leaking into this patch
```

Those conditions indicate scope drift or a deeper architectural conflict rather than a valid M7.3 repair.

---

# Deferred Follow-up — API Hygiene (`AccessReason`)

This item is **intentionally deferred** from M7.3 and must not be treated as resolved by the four conformance repairs above.

## Current observation

`AccessReason` is exported from the pure Reader Engine contract surface, but the current production graph does not appear to consume it and route traces do not currently expose it as an observed semantic. That makes it a likely public-API hygiene debt: the type exists as part of the frozen contract without a demonstrated runtime consumer.

## Why M7.3 does not change it

M7.3 is constrained to correctness/conformance repairs that already have clear normative behavior. `AccessReason` requires a separate semantic decision: whether access-level reasoning is part of the durable HES-v1 explanation model or whether the enum is accidental/dead API. Removing it now could silently alter a frozen contract; wiring it into traces now could manufacture a use case that has not been justified.

Therefore, during M7.3:

```text
AccessReason source/API remains unchanged.
No new production consumer is added merely to justify the enum.
No removal/deprecation is performed.
No checkpoint may claim this debt is closed.
```

## Required future audit

A later API-hygiene review must answer all of the following before changing the type:

1. **Semantic ownership** — Is `AccessReason` a true Reader Engine decision fact, or is it redundant with existing route/access decisions and trace fields?
2. **Consumer evidence** — Is there a concrete production/debug/telemetry consumer that needs this information, rather than only tests that prove the enum exists?
3. **Trace model fit** — If retained, where exactly should it appear in the immutable route trace, and what deterministic rule produces each enum value?
4. **Contract stability** — Is the enum part of any external/module API or serialized/persisted surface whose removal would require deprecation or versioning?
5. **Redundancy** — Can the same explanation already be derived unambiguously from existing `ReaderRouteDecision` / access-path fields without another public type?
6. **Freeze implications** — Would retaining, integrating, deprecating, or removing it require a HES contract amendment or compatibility note?

## Decision rule for the future review

The follow-up must end in one of only two explicit outcomes:

### Outcome A — Retain and make semantic

Choose this only if there is a real consumer and the reason is not derivable cleanly from existing trace state. Then:

```text
AccessReason becomes part of an actual immutable route/decision trace contract.
Every value receives a deterministic production assignment rule.
Focused tests verify value semantics, not merely enum existence.
Canonical design/plan document the meaning and consumer.
```

### Outcome B — Retire from the exported contract

Choose this if no justified consumer exists or the value is redundant. Then:

```text
remove or deprecate AccessReason according to compatibility requirements;
remove existence-only tests;
update canonical design/plan and freeze evidence;
verify no production or external consumer depends on the type.
```

Do **not** keep a third state where `AccessReason` remains exported indefinitely with no semantic consumer.

## Tracking requirement

Until this dedicated review is completed, every HES closure/current-state update that discusses remaining debt should preserve a short explicit note equivalent to:

```text
Deferred API hygiene: AccessReason remains intentionally unresolved and unchanged; requires separate retain-vs-retire contract review.
```

This deferred item is **non-blocking for M7.3 closure**, but it is blocking for any later claim that the Reader Engine public API has undergone complete hygiene/finalization.

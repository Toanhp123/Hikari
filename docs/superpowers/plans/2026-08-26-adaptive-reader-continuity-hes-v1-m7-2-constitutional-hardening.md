# Adaptive Reader Continuity / HES-v1 M7.2 Constitutional Hardening Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Restore the final HES-v1 Reader tree to its own constitutional contracts: exact runtime budgets, typed local corruption semantics, integer-only pure-engine health math, explicit process ownership, one immutable indexed graph snapshot per emission, and complete bounded concurrent-model evidence.

**Architecture:** Keep `:reader:engine` pure and unchanged at the public decision-contract level. Put effect-runtime ceilings and validation in `:reader`, preserve corruption facts through a compatibility-safe `ReaderDocumentStore.readResult(...)` boundary, make health/limiter sharing explicit in constructors, and move chapter-graph indexing into one session-owned immutable wrapper. Complete L7 verification only after all production changes are on the final tree.

**Tech Stack:** Kotlin/JVM + Android library modules, kotlinx.coroutines/semaphores/test scheduler, Hilt DI, Gradle 9.5.x, shell architecture verifiers, Room schema 11 unchanged.

**Spec:** `docs/superpowers/specs/2026-08-26-adaptive-reader-continuity-hes-v1-m7-2-constitutional-hardening-design.md`

## Global Constraints

- Keep `HesContractVersion.HES_V1`, `ReaderRoutingAlgorithmVersion.READER_ROUTING_V1`, `ReaderPolicyVersion.READER_POLICY_V1`, and `HealthPolicyVersion.HEALTH_POLICY_V1` unchanged.
- Keep the production graph at 17 modules plus the benchmark test module.
- Keep Room schema 11 and do not add `MIGRATION_11_12`.
- Keep `:reader:engine` production dependency exactly `:core:common`.
- Keep `:reader` consuming `:reader:engine` with `implementation`, never `api`.
- Keep max recovery attempts `<= 6`.
- Keep total foreground route attempts `<= 7`.
- Keep foreground REMOTE attempts `<= 4`.
- Keep concurrent foreground Reader REMOTE `<= 2` process-wide.
- Keep concurrent Reader prefetch REMOTE `<= 1` process-wide.
- Keep concurrent Reader REMOTE per source ID `<= 1`.
- Keep one HALF_OPEN probe lease per `SourceOperationKey`.
- Keep visible commits per generation `<= 1`.
- Keep generic local I/O failures non-corruption and non-penalizing.
- Keep remote valid commit independent from best-effort cache persistence.
- Keep `PluginReaderDocumentSource.invocationMutex` in this remediation.
- Do not weaken any architecture/test guard to obtain GREEN.
- Every production fix starts from a focused RED regression where practical.

---

## Locked File Structure

### New Reader runtime ownership files

- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRuntimeLimits.kt`
  - Sole HES-v1 effect-runtime ceiling constants.
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteRuntimeGuard.kt`
  - Common + sequential + competitive defensive route validation.
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderSessionChapterGraph.kt`
  - Immutable session-owned chapter graph copy/index wrapper.

### Changed Reader production files

- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderSourceExecutionLimiter.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/CompetitiveCompletionRegistry.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRoutePlanningContext.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/RouteSnapshotAssembler.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentStore.kt`

### Changed pure engine files

- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/SourceHealth.kt`

### Changed Downloads adapter files

- Modify: `downloads/src/main/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStore.kt`

### Architecture/static verification files

- Modify: `scripts/verify-package-boundaries.sh`
- Modify: `scripts/tests/verify-package-boundaries-test.sh`

### Focused tests

- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderSourceExecutionLimiterTest.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteRuntimeGuardTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteExecutorAdaptiveTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCompetitiveExecutionTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteCoordinatorAdaptiveTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteCoordinatorCompatibilityTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteReplanTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/PrefetchCoordinatorTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteSessionStateTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/RouteSnapshotAssemblerTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/RouteSnapshotAssemblerAdaptiveTest.kt`
- Rewrite/expand: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCoordinatorModelTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRuntimeStressTest.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelContinuityTest.kt`
- Modify: `reader/engine/src/test/kotlin/app/openstory/reader/engine/SourceHealthReducerTest.kt`
- Modify: `reader/engine/src/test/kotlin/app/openstory/reader/engine/HedgePolicyTest.kt`
- Modify: `downloads/src/test/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStoreTest.kt`

### Test fixture call sites that must pass explicit process owners

- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelContinuityTest.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelTest.kt`

### Docs/checkpoint

- Create: `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-2.md`
- Modify: `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1.md`
- Modify: `docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md` with a short post-freeze M7.2 pointer only.
- Modify: `docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md` with a short post-freeze M7.2 pointer only.

---

# M7.2-A — Reopen Freeze and Lock RED Characterization

## Task 1: Record the post-freeze remediation boundary before production changes

**Files:**
- Create: `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-2.md`
- Modify: `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1.md`

**Interfaces:**
- Consumes: historical M7/M7.1 checkpoint evidence.
- Produces: explicit `M7.2 OPEN / HES-v1 FREEZE REOPENED FOR REMEDIATION` governance state.

- [ ] **Step 1: Create the M7.2 checkpoint header without inventing test evidence**

```markdown
# Adaptive Reader Continuity / HES-v1 — M7.2 Constitutional Hardening Checkpoint

Date: 2026-08-26
Status: **OPEN / REMEDIATION IN PROGRESS**

Reason for reopening:
- process-wide foreground Reader REMOTE ceiling `<= 2` is not enforced;
- competitive runtime validation does not enforce total attempts `<= 7`;
- production local-store corruption can collapse into `MissingBlob`;
- pure-engine health percentile uses floating-point arithmetic;
- Task 30/L7 final evidence is narrower than the parent plan;
- session graph/process-shared ownership cleanup is required before re-freeze.

Historical M7/M7.1 command output remains historical evidence and is not rewritten.
```

- [ ] **Step 2: Change only the final checkpoint status summary to show the prospective reopen**

Use wording equivalent to:

```markdown
Status: **M0–M7.1 HISTORICALLY VERIFIED/CLOSED; M7.2 OPEN; HES-v1 FREEZE REOPENED FOR REMEDIATION**
```

Do not delete historical verification tables.

- [ ] **Step 3: Verify docs contain both historical and reopened status**

Run:

```bash
rg -n 'M7\.2|REOPENED|historical|HISTORICALLY' \
  docs/internal/checkpoints/adaptive-reader-continuity-hes-v1.md \
  docs/internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-2.md
```

Expected: explicit M7.2 OPEN wording; historical M7/M7.1 evidence remains present.

- [ ] **Step 4: Commit the governance reopen**

```bash
git add docs/internal/checkpoints/adaptive-reader-continuity-hes-v1*.md
git commit -m "docs(reader): reopen HES-v1 for M7.2 remediation"
```

---

# M7.2-B — Runtime Constitutional Limits

## Task 2: Add one Reader runtime-limit authority and one defensive route guard

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRuntimeLimits.kt`
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteRuntimeGuard.kt`
- Create: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteRuntimeGuardTest.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/CompetitiveCompletionRegistry.kt`

**Interfaces:**
- Produces:
  - `ReaderRuntimeLimits.MAX_TOTAL_FOREGROUND_ATTEMPTS = 7`
  - `ReaderRuntimeLimits.MAX_FOREGROUND_REMOTE_ATTEMPTS = 4`
  - `ReaderRuntimeLimits.MAX_CONCURRENT_FOREGROUND_REMOTE = 2`
  - `ReaderRuntimeLimits.MAX_CONCURRENT_PREFETCH_REMOTE = 1`
  - `ReaderRuntimeLimits.MAX_CONCURRENT_REMOTE_PER_SOURCE = 1`
  - `ReaderRouteRuntimeGuard.validateSequential(...)`
  - `ReaderRouteRuntimeGuard.validateCompetitive(...)`
- Consumes: engine `RouteAttempt`, `AccessMode`, `AttemptRole`.

- [ ] **Step 1: Write RED guard tests for malformed sequential and competitive routes**

Add cases equivalent to:

```kotlin
private fun remoteAttempt(
    attemptId: String,
    releaseId: String,
    sourceId: String,
    role: AttemptRole,
) = RouteAttempt(
    attemptId = attemptId,
    releaseId = ChapterReleaseId(releaseId),
    sourceId = PluginId(sourceId),
    accessMode = AccessMode.REMOTE,
    localFingerprint = null,
    role = role,
)

private fun localAttempt(
    attemptId: String,
    releaseId: String,
    sourceId: String,
    fingerprint: String,
    role: AttemptRole,
) = RouteAttempt(
    attemptId = attemptId,
    releaseId = ChapterReleaseId(releaseId),
    sourceId = PluginId(sourceId),
    accessMode = AccessMode.LOCAL,
    localFingerprint = fingerprint,
    role = role,
)

@Test
fun sequentialRejectsMoreThanSevenTotalAttempts() {
    val attempts = buildList {
        add(remoteAttempt("a0", "r0", "s0", AttemptRole.PRIMARY))
        repeat(7) { index ->
            add(localAttempt("a${index + 1}", "r${index + 1}", "s${index + 1}", "fp-${index + 1}", AttemptRole.FALLBACK))
        }
    }

    assertFailsWith<IllegalArgumentException> {
        ReaderRouteRuntimeGuard.validateSequential(attempts)
    }
}

@Test
fun competitiveRejectsMoreThanSevenTotalAttempts() {
    val primary = remoteAttempt("p", "r0", "s0", AttemptRole.PRIMARY)
    val hedge = remoteAttempt("h", "r1", "s1", AttemptRole.HEDGE)
    val recovery = (2..7).map { index ->
        localAttempt("a$index", "r$index", "s$index", "fp-$index", AttemptRole.FALLBACK)
    }

    assertFailsWith<IllegalArgumentException> {
        ReaderRouteRuntimeGuard.validateCompetitive(primary, hedge, recovery)
    }
}

@Test
fun sequentialRejectsMoreThanFourRemoteAttempts() {
    val attempts = (0..4).map { index ->
        remoteAttempt(
            attemptId = "a$index",
            releaseId = "r$index",
            sourceId = "s$index",
            role = if (index == 0) AttemptRole.PRIMARY else AttemptRole.FALLBACK,
        )
    }
    assertFailsWith<IllegalArgumentException> {
        ReaderRouteRuntimeGuard.validateSequential(attempts)
    }
}

@Test
fun competitiveRejectsMoreThanFourRemoteAttempts() {
    val primary = remoteAttempt("p", "r0", "s0", AttemptRole.PRIMARY)
    val hedge = remoteAttempt("h", "r1", "s1", AttemptRole.HEDGE)
    val recovery = (2..4).map { index ->
        remoteAttempt("a$index", "r$index", "s$index", AttemptRole.FALLBACK)
    }
    assertFailsWith<IllegalArgumentException> {
        ReaderRouteRuntimeGuard.validateCompetitive(primary, hedge, recovery)
    }
}

@Test
fun commonGuardRejectsDuplicateAttemptIds() {
    val attempts = listOf(
        remoteAttempt("same", "r0", "s0", AttemptRole.PRIMARY),
        localAttempt("same", "r1", "s1", "fp-1", AttemptRole.FALLBACK),
    )
    assertFailsWith<IllegalArgumentException> {
        ReaderRouteRuntimeGuard.validateSequential(attempts)
    }
}

@Test
fun commonGuardRejectsDuplicateReleaseAccessLocator() {
    val attempts = listOf(
        remoteAttempt("a0", "r0", "s0", AttemptRole.PRIMARY),
        remoteAttempt("a1", "r0", "s0", AttemptRole.FALLBACK),
    )
    assertFailsWith<IllegalArgumentException> {
        ReaderRouteRuntimeGuard.validateSequential(attempts)
    }
}

@Test
fun sequentialRejectsHedgeRole() {
    val attempts = listOf(
        remoteAttempt("p", "r0", "s0", AttemptRole.PRIMARY),
        remoteAttempt("h", "r1", "s1", AttemptRole.HEDGE),
    )
    assertFailsWith<IllegalArgumentException> {
        ReaderRouteRuntimeGuard.validateSequential(attempts)
    }
}

@Test
fun competitiveRejectsFallbackAsHedge() {
    val primary = remoteAttempt("p", "r0", "s0", AttemptRole.PRIMARY)
    val malformedHedge = remoteAttempt("h", "r1", "s1", AttemptRole.FALLBACK)
    assertFailsWith<IllegalArgumentException> {
        ReaderRouteRuntimeGuard.validateCompetitive(primary, malformedHedge, emptyList())
    }
}

@Test
fun competitiveRejectsNonFallbackRecoveryRole() {
    val primary = remoteAttempt("p", "r0", "s0", AttemptRole.PRIMARY)
    val malformedRecovery = listOf(remoteAttempt("r", "r1", "s1", AttemptRole.PRIMARY))
    assertFailsWith<IllegalArgumentException> {
        ReaderRouteRuntimeGuard.validateCompetitive(primary, null, malformedRecovery)
    }
}

@Test
fun competitiveRejectsSameSourceHedge() {
    val primary = remoteAttempt("p", "r0", "same", AttemptRole.PRIMARY)
    val hedge = remoteAttempt("h", "r1", "same", AttemptRole.HEDGE)
    assertFailsWith<IllegalArgumentException> {
        ReaderRouteRuntimeGuard.validateCompetitive(primary, hedge, emptyList())
    }
}

@Test
fun competitiveRejectsLocalPrimaryWhenHedgeExists() {
    val primary = localAttempt("p", "r0", "s0", "fp-0", AttemptRole.PRIMARY)
    val hedge = remoteAttempt("h", "r1", "s1", AttemptRole.HEDGE)
    assertFailsWith<IllegalArgumentException> {
        ReaderRouteRuntimeGuard.validateCompetitive(primary, hedge, emptyList())
    }
}

@Test
fun competitiveRejectsLocalHedge() {
    val primary = remoteAttempt("p", "r0", "s0", AttemptRole.PRIMARY)
    val hedge = localAttempt("h", "r1", "s1", "fp-1", AttemptRole.HEDGE)
    assertFailsWith<IllegalArgumentException> {
        ReaderRouteRuntimeGuard.validateCompetitive(primary, hedge, emptyList())
    }
}
```

- [ ] **Step 2: Run the new tests and verify RED**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderRouteRuntimeGuardTest*' \
  --no-daemon
```

Expected: compile/test failure because runtime limits/guard do not exist.

- [ ] **Step 3: Add exact runtime limits**

`ReaderRuntimeLimits.kt`:

```kotlin
package app.openstory.reader.routing

internal object ReaderRuntimeLimits {
    const val MAX_TOTAL_FOREGROUND_ATTEMPTS = 7
    const val MAX_FOREGROUND_REMOTE_ATTEMPTS = 4
    const val MAX_CONCURRENT_FOREGROUND_REMOTE = 2
    const val MAX_CONCURRENT_PREFETCH_REMOTE = 1
    const val MAX_CONCURRENT_REMOTE_PER_SOURCE = 1
}
```

- [ ] **Step 4: Implement one common runtime guard**

Use a shape equivalent to:

```kotlin
internal object ReaderRouteRuntimeGuard {
    fun validateSequential(attempts: List<RouteAttempt>) {
        validateCommon(attempts)
        if (attempts.isEmpty()) return
        require(attempts.first().role == AttemptRole.PRIMARY)
        require(attempts.drop(1).all { it.role == AttemptRole.FALLBACK })
    }

    fun validateCompetitive(
        primary: RouteAttempt,
        hedge: RouteAttempt?,
        recoveryChain: List<RouteAttempt>,
    ) {
        require(primary.role == AttemptRole.PRIMARY)
        require(hedge == null || hedge.role == AttemptRole.HEDGE)
        require(recoveryChain.all { it.role == AttemptRole.FALLBACK })
        if (hedge != null) {
            require(primary.accessMode == AccessMode.REMOTE)
            require(hedge.accessMode == AccessMode.REMOTE)
            require(primary.sourceId != hedge.sourceId)
        }
        validateCommon(buildList {
            add(primary)
            hedge?.let(::add)
            addAll(recoveryChain)
        })
    }

    private fun validateCommon(attempts: List<RouteAttempt>) {
        require(attempts.size <= ReaderRuntimeLimits.MAX_TOTAL_FOREGROUND_ATTEMPTS)
        require(
            attempts.count { it.accessMode == AccessMode.REMOTE } <=
                ReaderRuntimeLimits.MAX_FOREGROUND_REMOTE_ATTEMPTS,
        )
        require(attempts.map { it.attemptId }.toSet().size == attempts.size)
        require(
            attempts.map { Triple(it.releaseId, it.accessMode, it.localFingerprint) }.toSet().size == attempts.size,
        )
    }
}
```

Preserve current exception messages where tests/users depend on them; otherwise use clear HES-v1 messages.

- [ ] **Step 5: Replace duplicated sequential validation**

At the start of `ReaderRouteExecutor.executeAdaptive(...)`:

```kotlin
ReaderRouteRuntimeGuard.validateSequential(attempts)
```

Delete local `MAX_TOTAL_FOREGROUND_ATTEMPTS` / `MAX_FOREGROUND_REMOTE_ATTEMPTS` constants and duplicated common checks after equivalent coverage exists in the guard.

- [ ] **Step 6: Replace duplicated competitive validation**

At the start of `ReaderCompetitiveExecution.execute(...)`:

```kotlin
val hedge = (hedgeDirective as? HedgeDirective.Launch)?.attempt
ReaderRouteRuntimeGuard.validateCompetitive(primary, hedge, recoveryChain)
```

Delete `ReaderCompetitiveExecution.MAX_FOREGROUND_REMOTE_ATTEMPTS`.

- [ ] **Step 7: Run focused guard + executor + competitive tests GREEN**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderRouteRuntimeGuardTest*' \
  --tests '*ReaderRouteExecutorAdaptiveTest*' \
  --tests '*ReaderCompetitiveExecutionTest*' \
  --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Add policy/runtime compatibility assertion**

In `ReaderRouteRuntimeGuardTest` or a focused limits test:

```kotlin
@Test
fun defaultHesPolicyFitsInsideRuntimeCeilings() {
    val policy = ReaderRoutingPolicy.v1()
    assertTrue(policy.maxPlannedForegroundRemoteAttempts <= ReaderRuntimeLimits.MAX_FOREGROUND_REMOTE_ATTEMPTS)
    assertTrue(1 + policy.maxRecoveryAttempts <= ReaderRuntimeLimits.MAX_TOTAL_FOREGROUND_ATTEMPTS)
}
```

- [ ] **Step 9: Commit**

```bash
git add reader/src/main reader/src/test
git commit -m "fix(reader): centralize HES runtime route ceilings"
```

---

## Task 3: Enforce the process-wide foreground REMOTE concurrency ceiling

**Files:**
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderSourceExecutionLimiter.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderSourceExecutionLimiterTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRuntimeStressTest.kt`

**Interfaces:**
- Consumes: `ReaderRuntimeLimits` from Task 2.
- Preserves: per-source lane, probe leases, same-source foreground preemption.
- Produces: max two active process-wide foreground Reader REMOTE blocks.

- [ ] **Step 1: Write a RED four-source foreground concurrency test**

Add a test that launches four FOREGROUND calls through one limiter, each with a distinct `PluginId`, and uses gates to keep admitted blocks open:

```kotlin
@Test
fun atMostTwoForegroundRemoteAttemptsAreActiveProcessWide() = runTest {
    val limiter = ReaderSourceExecutionLimiter()
    val release = CompletableDeferred<Unit>()
    val entered = Channel<Unit>(Channel.UNLIMITED)
    var active = 0
    var maximumActive = 0

    val jobs = (0 until 4).map { index ->
        launch {
            limiter.withRemotePermit(PluginId("source-$index"), ReaderRemoteWorkPriority.FOREGROUND) {
                active += 1
                maximumActive = maxOf(maximumActive, active)
                entered.send(Unit)
                release.await()
                active -= 1
            }
        }
    }

    repeat(2) { entered.receive() }
    runCurrent()
    assertEquals(2, maximumActive)
    assertEquals(2, active)

    release.complete(Unit)
    jobs.forEach { it.join() }
    assertEquals(2, maximumActive)
}
```

Use synchronization safe for the test scheduler; if mutable counters can be touched concurrently in the chosen scheduler, use `AtomicInteger`.

- [ ] **Step 2: Add RED cancellation-leak coverage**

Cover both:

```text
foreground cancelled while waiting for global foreground permit
prefetch cancelled/preempted while waiting for global prefetch permit
```

After cancellation, launch a fresh request and prove it enters; otherwise a permit/lane leaked.

- [ ] **Step 3: Run focused limiter tests and observe RED**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderSourceExecutionLimiterTest*' \
  --no-daemon
```

Expected: the four-source test reaches more than two active blocks before the fix.

- [ ] **Step 4: Add global foreground and shared prefetch permits from runtime limits**

In `ReaderSourceExecutionLimiter`:

```kotlin
private val remoteForegroundPermit = Semaphore(
    permits = ReaderRuntimeLimits.MAX_CONCURRENT_FOREGROUND_REMOTE,
)
private val remotePrefetchPermit = Semaphore(
    permits = ReaderRuntimeLimits.MAX_CONCURRENT_PREFETCH_REMOTE,
)
```

- [ ] **Step 5: Enforce source-lane-first acquisition order**

Replace the current priority branch with:

```kotlin
internal suspend fun <T> withRemotePermit(
    sourceId: PluginId,
    priority: ReaderRemoteWorkPriority,
    block: suspend () -> T,
): T = withSourceRemotePermit(sourceId, priority) {
    when (priority) {
        ReaderRemoteWorkPriority.FOREGROUND -> remoteForegroundPermit.withPermit { block() }
        ReaderRemoteWorkPriority.PREFETCH -> remotePrefetchPermit.withPermit { block() }
    }
}
```

Do not acquire a global permit before `withSourceRemotePermit`.

- [ ] **Step 6: Preserve existing preemption semantics**

Verify the existing foreground enqueue path still cancels active prefetch `workJob` for the same source and that cancellation while waiting in `withPermit` unwinds through `releaseActive(...)`.

Do not add a global cross-source prefetch cancellation policy.

- [ ] **Step 7: Add mixed-class evidence**

Add a test proving distinct sources may have:

```text
2 active foreground + 1 active prefetch
```

at the same time, while no source ID exceeds one active block.

- [ ] **Step 8: Run limiter + stress tests GREEN**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderSourceExecutionLimiterTest*' \
  --tests '*ReaderRuntimeStressTest*' \
  --no-daemon
```

Expected: BUILD SUCCESSFUL; maximum foreground active observed is exactly 2 in the saturation test.

- [ ] **Step 9: Commit**

```bash
git add reader/src/main/kotlin/app/openstory/reader/routing/ReaderSourceExecutionLimiter.kt \
        reader/src/test/kotlin/app/openstory/reader/routing/ReaderSourceExecutionLimiterTest.kt \
        reader/src/test/kotlin/app/openstory/reader/routing/ReaderRuntimeStressTest.kt
git commit -m "fix(reader): enforce process foreground remote concurrency"
```

---

## Task 4: Make process-shared health and limiter ownership explicit in construction

**Files:**
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteExecutorAdaptiveTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/PrefetchCoordinatorTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCompetitiveExecutionTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteCoordinatorAdaptiveTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteCoordinatorCompatibilityTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteReplanTest.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelContinuityTest.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelTest.kt`
- Verify unchanged wiring: `app/src/main/kotlin/app/openstory/di/ReaderModule.kt`

**Interfaces:**
- `ReaderRouteCoordinator(..., healthRegistry: ReaderSourceHealthRegistry, executionLimiter: ReaderSourceExecutionLimiter, ...)`
- `ReaderRouteExecutor(..., executionLimiter: ReaderSourceExecutionLimiter, ...)`

- [ ] **Step 1: Remove the two coordinator defaults and executor limiter default**

Change:

```kotlin
healthRegistry: ReaderSourceHealthRegistry = ReaderSourceHealthRegistry(),
executionLimiter: ReaderSourceExecutionLimiter = ReaderSourceExecutionLimiter(),
```

into required parameters.

Change:

```kotlin
private val executionLimiter: ReaderSourceExecutionLimiter = ReaderSourceExecutionLimiter(),
```

into a required constructor parameter.

- [ ] **Step 2: Compile Reader tests to expose every implicit owner**

```bash
./gradlew :reader:compileDebugKotlin :reader:compileDebugUnitTestKotlin --no-daemon
```

Expected before fixture updates: compilation failures at direct construction sites that omitted explicit owners.

- [ ] **Step 3: Update direct executor test construction explicitly**

Use named/explicit test owners, e.g.:

```kotlin
private fun executor(
    store: ReaderDocumentStore = AdaptiveStore(),
    registry: ReaderDocumentSourceRegistry = AdaptiveRegistry(emptyList()),
    limiter: ReaderSourceExecutionLimiter = ReaderSourceExecutionLimiter(),
) = ReaderRouteExecutor(
    store = store,
    sources = registry,
    executionLimiter = limiter,
)
```

Refactor repetitive construction in `ReaderRouteExecutorAdaptiveTest` to this helper without changing scenario semantics.

- [ ] **Step 4: Update coordinator test fixtures explicitly**

Every helper should either create both owners once:

```kotlin
val health = ReaderSourceHealthRegistry()
val limiter = ReaderSourceExecutionLimiter()
val coordinator = ReaderRouteCoordinator(
    store = store,
    sources = sources,
    progress = progress,
    healthRegistry = health,
    executionLimiter = limiter,
)
```

or accept them as parameters so two-session tests can intentionally share them.

Update:

```text
ReaderRouteCoordinatorAdaptiveTest
ReaderRouteCoordinatorCompatibilityTest
ReaderRouteReplanTest
ReaderCompetitiveExecutionTest
PrefetchCoordinatorTest
ReaderViewModelContinuityTest
ReaderViewModelTest
```

- [ ] **Step 5: Verify Hilt already supplies singleton instances**

Confirm these remain:

```kotlin
@Provides @Singleton
fun provideReaderSourceHealthRegistry(): ReaderSourceHealthRegistry

@Provides @Singleton
fun provideReaderSourceExecutionLimiter(): ReaderSourceExecutionLimiter
```

and that `provideReaderRouteCoordinator(...)` passes both explicitly.

Do not add a second provider.

- [ ] **Step 6: Run Reader + Feature Reader compile/tests**

```bash
./gradlew \
  :reader:testDebugUnitTest \
  :feature:reader:testDebugUnitTest \
  :app:compileDebugKotlin \
  --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 7: Commit**

```bash
git add reader/src feature/reader/src app/src/main/kotlin/app/openstory/di/ReaderModule.kt
git commit -m "refactor(reader): require shared runtime owners explicitly"
```

---

# M7.2-C — Typed Local Storage Semantics

## Task 5: Extend `ReaderDocumentStore` with a compatibility-safe typed exact-read result

**Files:**
- Modify: `reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentStore.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteExecutorAdaptiveTest.kt`

**Interfaces:**
- Produces `ReaderDocumentReadResult` and default `readResult(...)`.
- Keeps nullable `read(...)` unchanged for current consumers.

- [ ] **Step 1: Add the typed result to the Reader content boundary**

```kotlin
sealed interface ReaderDocumentReadResult {
    data class Hit(val document: ReaderDocument) : ReaderDocumentReadResult
    data object Missing : ReaderDocumentReadResult
    data object FingerprintOrDecodeMismatch : ReaderDocumentReadResult
}
```

- [ ] **Step 2: Add the default compatibility method**

```kotlin
suspend fun readResult(
    releaseId: ChapterReleaseId,
    fingerprint: String,
): ReaderDocumentReadResult =
    read(releaseId, fingerprint)
        ?.let { ReaderDocumentReadResult.Hit(it) }
        ?: ReaderDocumentReadResult.Missing
```

Keep `read`, `readCurrent`, `write`, and `quarantine` signatures unchanged.

- [ ] **Step 3: Add a small default-contract test through an existing fake**

In `ReaderRouteExecutorAdaptiveTest` or a content-boundary test, prove a fake that implements only nullable `read()` gets:

```text
non-null -> Hit
null     -> Missing
```

This locks source compatibility for existing test stores.

- [ ] **Step 4: Run Reader compile/tests**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderRouteExecutorAdaptiveTest*' --no-daemon
```

Expected: BUILD SUCCESSFUL; no production behavior change yet.

- [ ] **Step 5: Commit**

```bash
git add reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentStore.kt \
        reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteExecutorAdaptiveTest.kt
git commit -m "refactor(reader): add typed local document read result"
```

---

## Task 6: Preserve production corruption information across local namespaces

**Files:**
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStore.kt`
- Modify: `downloads/src/test/kotlin/app/openstory/downloads/reader/DownloadAwareReaderDocumentStoreTest.kt`

**Interfaces:**
- Overrides `ReaderDocumentStore.readResult(...)`.
- Preserves nullable `read(...)` behavior.

- [ ] **Step 1: Write RED typed-store tests**

Add exact tests:

```kotlin
@Test
fun `missing exact locator returns typed Missing`() = runTest {
    val store = DownloadAwareReaderDocumentStore(
        FakeBlobs(), FakeCacheRepository(), FakeDownloads(), { 10L },
    )
    assertIs<ReaderDocumentReadResult.Missing>(store.readResult(releaseId, fingerprint))
}

@Test
fun `only corrupt exact copy returns typed fingerprint or decode mismatch`() = runTest {
    val blobs = FakeBlobs()
    val store = DownloadAwareReaderDocumentStore(
        blobs, FakeCacheRepository(), FakeDownloads(), { 10L },
    )
    blobs.write(
        key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD),
        ChapterBlob.fromBytes("broken".encodeToByteArray()),
    )

    assertIs<ReaderDocumentReadResult.FingerprintOrDecodeMismatch>(
        store.readResult(releaseId, fingerprint),
    )
}

@Test
fun `valid cache survives corrupt explicit copy`() = runTest {
    val blobs = FakeBlobs()
    val store = DownloadAwareReaderDocumentStore(
        blobs, FakeCacheRepository(), FakeDownloads(), { 10L },
    )
    val explicitKey = key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD)
    val cacheKey = key(ChapterBlobNamespace.AUTOMATIC_CACHE)
    blobs.write(explicitKey, ChapterBlob.fromBytes("broken".encodeToByteArray()))
    blobs.write(cacheKey, ReaderDocumentBlobCodec.encode(document("cache")))

    val result = assertIs<ReaderDocumentReadResult.Hit>(store.readResult(releaseId, fingerprint))
    assertEquals("cache", result.document.title)
    assertNull(blobs.read(explicitKey))
    assertNotNull(blobs.read(cacheKey))
}

@Test
fun `cleanup failure does not erase confirmed corruption`() = runTest {
    val blobs = FakeBlobs(failDeletes = true)
    val store = DownloadAwareReaderDocumentStore(
        blobs, FakeCacheRepository(), FakeDownloads(), { 10L },
    )
    blobs.write(
        key(ChapterBlobNamespace.EXPLICIT_DOWNLOAD),
        ChapterBlob.fromBytes("broken".encodeToByteArray()),
    )

    assertIs<ReaderDocumentReadResult.FingerprintOrDecodeMismatch>(
        store.readResult(releaseId, fingerprint),
    )
}
```

Extend `FakeBlobs` with `failDeletes: Boolean = false`; in `delete`, throw `error("delete failed")` when enabled. Keep the existing `failWrites` behavior unchanged.

- [ ] **Step 2: Run Downloads store test and verify RED**

```bash
./gradlew :downloads:testDebugUnitTest \
  --tests '*DownloadAwareReaderDocumentStoreTest*' \
  --no-daemon
```

Expected: tests fail because production store does not override `readResult`.

- [ ] **Step 3: Refactor namespace read into a typed physical-read helper**

Use an internal helper equivalent to:

```kotlin
private sealed interface PhysicalRead {
    data class Hit(val document: ReaderDocument) : PhysicalRead
    data object Missing : PhysicalRead
    data object Corrupt : PhysicalRead
}
```

For one namespace:

```kotlin
private suspend fun readPhysical(
    namespace: ChapterBlobNamespace,
    releaseId: ChapterReleaseId,
    fingerprint: String,
): PhysicalRead {
    val key = ChapterBlobKey(namespace, releaseId, fingerprint)
    val blob = blobs.read(key) ?: return PhysicalRead.Missing
    val document = ReaderDocumentBlobCodec.decode(blob)
    if (document == null || document.fingerprint != fingerprint) {
        deleteCorruptBestEffort(key)
        return PhysicalRead.Corrupt
    }
    touchBestEffort(key)
    return PhysicalRead.Hit(document)
}
```

- [ ] **Step 4: Aggregate all namespaces before deciding Missing vs Corrupt**

```kotlin
override suspend fun readResult(
    releaseId: ChapterReleaseId,
    fingerprint: String,
): ReaderDocumentReadResult {
    var sawCorruption = false
    for (namespace in LOCAL_READ_ORDER) {
        when (val result = readPhysical(namespace, releaseId, fingerprint)) {
            is PhysicalRead.Hit -> return ReaderDocumentReadResult.Hit(result.document)
            PhysicalRead.Missing -> Unit
            PhysicalRead.Corrupt -> sawCorruption = true
        }
    }
    return if (sawCorruption) {
        ReaderDocumentReadResult.FingerprintOrDecodeMismatch
    } else {
        ReaderDocumentReadResult.Missing
    }
}
```

- [ ] **Step 5: Keep nullable exact-read and current-download reads as compatibility projections**

```kotlin
override suspend fun read(
    releaseId: ChapterReleaseId,
    fingerprint: String,
): ReaderDocument? = when (val result = readResult(releaseId, fingerprint)) {
    is ReaderDocumentReadResult.Hit -> result.document
    ReaderDocumentReadResult.Missing,
    ReaderDocumentReadResult.FingerprintOrDecodeMismatch,
    -> null
}

override suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument? {
    val record = downloads.find(releaseId)
        ?.takeIf { it.state == DownloadState.COMPLETED }
        ?: return null
    return when (
        val result = readPhysical(
            ChapterBlobNamespace.EXPLICIT_DOWNLOAD,
            releaseId,
            record.key.contentFingerprint,
        )
    ) {
        is PhysicalRead.Hit -> result.document
        PhysicalRead.Missing,
        PhysicalRead.Corrupt,
        -> null
    }
}
```

Because production overrides `readResult`, nullable `read()` does not recurse. `readCurrent()` remains explicit-download-only exactly as before.

- [ ] **Step 6: Make post-confirmation deletion best effort**

```kotlin
private suspend fun deleteCorruptBestEffort(key: ChapterBlobKey) {
    try {
        blobs.delete(key)
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (_: Exception) {
        // Corruption was already proven by decoded bytes; cleanup failure does not erase that fact.
    }
}
```

Keep blob `read(...)` exceptions uncaught so executor can classify infrastructure failure separately.

- [ ] **Step 7: Run Downloads store/cache-facts regression**

```bash
./gradlew :downloads:testDebugUnitTest \
  --tests '*DownloadAwareReaderDocumentStoreTest*' \
  --tests '*DownloadAwareReaderCacheFactsTest*' \
  --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 8: Commit**

```bash
git add downloads/src reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentStore.kt
git commit -m "fix(downloads): preserve typed reader local corruption"
```

---

## Task 7: Map typed store results through executor and session invalidation

**Files:**
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteExecutorAdaptiveTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteReplanTest.kt`

**Interfaces:**
- Consumes: `ReaderDocumentReadResult`.
- Produces exact HES local observations and `onLocalInvalidated` behavior.

- [ ] **Step 1: Add a RED executor test where nullable read would be `null` but typed result is corruption**

Extend `AdaptiveStore` with an optional typed result override:

```kotlin
private val readResultOverride: ReaderDocumentReadResult? = null

override suspend fun readResult(
    releaseId: ChapterReleaseId,
    fingerprint: String,
): ReaderDocumentReadResult = readResultOverride
    ?: read(releaseId, fingerprint)
        ?.let { ReaderDocumentReadResult.Hit(it) }
    ?: ReaderDocumentReadResult.Missing
```

Then assert:

```kotlin
@Test
fun typedStoreCorruptionIsNotCollapsedIntoMissingBlob() = runTest {
    val store = AdaptiveStore(
        readResultOverride = ReaderDocumentReadResult.FingerprintOrDecodeMismatch,
    )
    val observations = mutableListOf<SourceObservation>()
    val invalidated = mutableListOf<Pair<String, String>>()

    val result = executor(store, registryWithRemoteSuccess()).executeAdaptive(
        attempts = listOf(localPrimary("expected"), remoteFallback()),
        candidatesByRelease = candidates,
        onSourceObservation = { _, observation -> observations += observation },
        onLocalInvalidated = { releaseId, fp -> invalidated += releaseId.value to fp },
    )

    assertIs<ReaderLoadResult.Success>(result)
    assertIs<SourceObservation.LocalFailure.FingerprintOrDecodeMismatch>(observations.first())
    assertEquals(listOf("release" to "expected"), invalidated)
    assertEquals(listOf("release" to "expected"), store.quarantines)
}
```

- [ ] **Step 2: Add a paired typed-Missing test**

Assert `ReaderDocumentReadResult.Missing` produces `LocalFailure.MissingBlob`, no quarantine, and no `onLocalInvalidated` callback.

- [ ] **Step 3: Run focused executor tests and verify RED for typed corruption**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderRouteExecutorAdaptiveTest*' \
  --no-daemon
```

- [ ] **Step 4: Replace private nullable read adaptation with typed mapping**

Refactor executor local read to:

```kotlin
private suspend fun readExact(
    candidate: ChapterRelease,
    fingerprint: String,
): LocalReadResult = try {
    when (val result = store.readResult(candidate.id, fingerprint)) {
        is ReaderDocumentReadResult.Hit -> LocalReadResult.Hit(result.document)
        ReaderDocumentReadResult.Missing -> LocalReadResult.Failure(
            localFailure(
                candidate,
                SourceObservation.LocalFailure.MissingBlob,
                RecoveryScope.LOCAL_SCOPED,
                "reader.local_blob_missing",
                retryable = false,
            ),
        )
        ReaderDocumentReadResult.FingerprintOrDecodeMismatch -> LocalReadResult.ConfirmedCorruption(
            localFailure(
                candidate,
                SourceObservation.LocalFailure.FingerprintOrDecodeMismatch,
                RecoveryScope.LOCAL_SCOPED,
                "reader.local_fingerprint_or_decode_mismatch",
                retryable = false,
            ),
        )
    }
} catch (cancelled: CancellationException) {
    throw cancelled
} catch (_: Exception) {
    LocalReadResult.Failure(
        localFailure(
            candidate,
            SourceObservation.RuntimeFailure.Unexpected,
            RecoveryScope.CLIENT_SCOPED,
            "reader.local_read_failed",
            retryable = true,
        ),
    )
}
```

Define one internal diagnostic code for this previously-collapsed state, `reader.local_fingerprint_or_decode_mismatch`, and use it only for the typed store-level corruption branch. Keep validator-owned `reader.local_fingerprint_mismatch` / `reader.local_document_invalid` codes unchanged for materialized documents.

- [ ] **Step 5: Handle confirmed corruption before local document validation**

Add a `LocalReadResult.ConfirmedCorruption` branch that:

```text
ensure ownership
record non-penalizing observation
quarantine exact locator best-effort
ensure ownership
notify onLocalInvalidated(releaseId, fingerprint)
return failure
```

Keep `Hit -> validator.validateLocal(...)` behavior unchanged.

- [ ] **Step 6: Preserve current-chain recovery and prove later-snapshot known-invalid behavior**

First, keep/adapt `exactCorruptionQuarantinesLocatorThenRemoteProbeRecovers` so typed store corruption still allows the already-planned REMOTE fallback to succeed in the **same plan revision**. Assert no new hard replan is required merely because the local locator was invalid.

Then in `ReaderRouteReplanTest`, use a cache fact that continues to advertise the same exact fingerprint after a generation records confirmed corruption. Exhaust or complete that generation, issue a later retry/new snapshot, and assert the locator maps to `CandidateLocalAccess.KnownInvalid` and is not planned as LOCAL again.

Expected semantics:

```text
generation N / revision 0: LOCAL(expected) -> typed corruption
same immutable route:       REMOTE/fallback continues normally
session state:              expected fingerprint is known-invalid
later retry/snapshot:       LOCAL(expected) absent; remote/other fallback remains eligible
```

Do not call `hardInvalidateIfCurrent` solely for local corruption; that would violate the parent HES-v1 recovery contract.

- [ ] **Step 7: Run executor + replan tests GREEN**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderRouteExecutorAdaptiveTest*' \
  --tests '*ReaderRouteReplanTest*' \
  --no-daemon
```

- [ ] **Step 8: Commit**

```bash
git add reader/src
git commit -m "fix(reader): preserve local corruption through execution"
```

---

# M7.2-D — Pure Fixed-Point Hardening

## Task 8: Remove floating-point percentile arithmetic from `:reader:engine` and guard it constitutionally

**Files:**
- Modify: `reader/engine/src/main/kotlin/app/openstory/reader/engine/SourceHealth.kt`
- Modify: `reader/engine/src/test/kotlin/app/openstory/reader/engine/SourceHealthReducerTest.kt`
- Modify: `reader/engine/src/test/kotlin/app/openstory/reader/engine/HedgePolicyTest.kt`
- Modify: `scripts/verify-package-boundaries.sh`
- Modify: `scripts/tests/verify-package-boundaries-test.sh`

**Interfaces:**
- Pure output remains p50/p95 nearest-rank.
- No HES version change.

- [ ] **Step 1: Add exact percentile regression cases before changing production**

Cover at least:

```kotlin
@Test
fun nearestRankPercentilesUseExactIntegerRanks() {
    val three = SourceHealthState(recentLatencySamplesMillis = listOf(10L, 20L, 30L))
    assertEquals(20L, three.p50LatencyMillis)
    assertEquals(30L, three.p95LatencyMillis)

    val four = SourceHealthState(recentLatencySamplesMillis = listOf(10L, 20L, 30L, 40L))
    assertEquals(20L, four.p50LatencyMillis)
    assertEquals(40L, four.p95LatencyMillis)

    val twenty = SourceHealthState(recentLatencySamplesMillis = (1L..20L).toList())
    assertEquals(10L, twenty.p50LatencyMillis)
    assertEquals(19L, twenty.p95LatencyMillis)
}
```

Also preserve `null` when sample count is below the existing minimum of three.

- [ ] **Step 2: Add a RED static mutation case for `Double` and `Float` in engine production**

Extend `scripts/tests/verify-package-boundaries-test.sh` with fixture cases equivalent to:

```bash
run_case 'reader/engine/src/main/kotlin/F.kt' 'val leaked: Double = 1.0' 1
run_case 'reader/engine/src/main/kotlin/F.kt' 'val leaked: Float = 1f' 1
run_case 'reader/engine/src/main/kotlin/F.kt' 'val leaked = 100.0' 1
run_case 'reader/engine/src/main/kotlin/F.kt' 'val leaked = 25f' 1
```

Keep an integer-only source fixture passing.

- [ ] **Step 3: Run focused pure tests/static guard and observe static RED**

```bash
./gradlew :reader:engine:test --no-daemon
bash scripts/tests/verify-package-boundaries-test.sh
```

- [ ] **Step 4: Replace floating nearest-rank implementation**

In `SourceHealth.kt`, remove:

```kotlin
import kotlin.math.ceil
private const val PERCENTILE_SCALE: Double = 100.0
```

Use integer arithmetic:

```kotlin
private fun nearestRankLatency(percentile: Int): Long? {
    if (recentLatencySamplesMillis.size < MIN_LATENCY_SAMPLES_FOR_PERCENTILE) return null
    require(percentile in 1..100)
    val sorted = recentLatencySamplesMillis.sorted()
    val numerator = percentile.toLong() * sorted.size.toLong()
    val rank = ((numerator + 99L) / 100L).toInt().coerceIn(1, sorted.size)
    return sorted[rank - 1]
}
```

- [ ] **Step 5: Add engine source floating-point rejection**

In `verify-package-boundaries.sh`, scan only `reader/engine/src/main/**/*.kt` and reject either type tokens or floating numeric literals. Use the same pattern in the script's mutation tests:

```bash
FLOAT_PATTERN='\b(Float|Double)\b|[0-9]+\.[0-9]+([eE][+-]?[0-9]+)?[fF]?\b|[0-9]+[eE][+-]?[0-9]+[fF]?\b|[0-9]+[fF]\b'
```

A match is a constitutional failure. This intentionally rejects floating-looking literals even in engine source comments/strings; keep such representation out of the pure engine source entirely rather than weakening the guard.

Do not apply this ban to `reader/src/main` or Feature Reader.

- [ ] **Step 6: Run pure and architecture tests GREEN**

```bash
./gradlew :reader:engine:test :build-logic:test \
  --tests '*ModuleGraphTest*' \
  --tests '*ModuleBoundaryVerifierTest*' \
  --no-daemon
bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
```

- [ ] **Step 7: Confirm no production engine Float/Double remains**

```bash
rg -n '\b(Float|Double)\b|[0-9]+\.[0-9]+([eE][+-]?[0-9]+)?[fF]?\b|[0-9]+[eE][+-]?[0-9]+[fF]?\b|[0-9]+[fF]\b' reader/engine/src/main --glob '*.kt' && exit 1 || true
```

Expected: no matches.

- [ ] **Step 8: Commit**

```bash
git add reader/engine scripts
git commit -m "fix(reader-engine): keep health percentiles integer-only"
```

---

# M7.2-E — Session Graph Ownership Cleanup

## Task 9: Introduce one immutable indexed session chapter graph

**Files:**
- Create: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderSessionChapterGraph.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteSessionStateTest.kt`

**Interfaces:**
- Produces an internal immutable wrapper around existing `CanonicalChapterGroup` / `ChapterRelease`.
- No chapter-domain API change.

- [ ] **Step 1: Write tests for owned copy, indexes, next lookup, and release lookup**

Cover:

```kotlin
@Test
fun graphOwnsOneDefensiveCopyAndIndexesChapterAndRelease() {
    val graph = ReaderSessionChapterGraph.create(STORY_ID, listOf(groupA, groupB))

    assertEquals(0, graph.indexOf(groupA.chapter.id))
    assertEquals(1, graph.indexOf(groupB.chapter.id))
    assertEquals(groupA, graph.group(groupA.chapter.id))
    assertEquals(groupA, graph.previousBefore(groupB.chapter.id))
    assertEquals(groupB, graph.nextAfter(groupA.chapter.id))
    assertEquals(groupA.releases.single(), graph.release(groupA.releases.single().id))
}
```

Also reject only the story-ownership violations that `ReaderRouteSession.updateChapterGraph()` already rejects today:

```text
group chapter from another story
release from another story
```

Do not introduce new duplicate/canonical-membership rejection rules in M7.2. Index duplicate IDs with first-occurrence semantics so lookup behavior matches the old `indexOfFirst` / `firstOrNull` scans.

- [ ] **Step 2: Run focused test RED**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderRouteSessionStateTest*' \
  --no-daemon
```

- [ ] **Step 3: Implement the immutable graph wrapper**

Use construction equivalent to:

```kotlin
internal class ReaderSessionChapterGraph private constructor(
    val groups: List<CanonicalChapterGroup>,
    private val chapterIndexById: Map<CanonicalChapterId, Int>,
    private val groupByChapterId: Map<CanonicalChapterId, CanonicalChapterGroup>,
    private val releaseById: Map<ChapterReleaseId, ChapterRelease>,
    val releaseIds: Set<ChapterReleaseId>,
) {
    fun indexOf(chapterId: CanonicalChapterId): Int? = chapterIndexById[chapterId]
    fun group(chapterId: CanonicalChapterId): CanonicalChapterGroup? = groupByChapterId[chapterId]
    fun previousBefore(chapterId: CanonicalChapterId): CanonicalChapterGroup? =
        chapterIndexById[chapterId]?.let { groups.getOrNull(it - 1) }
    fun nextAfter(chapterId: CanonicalChapterId): CanonicalChapterGroup? =
        chapterIndexById[chapterId]?.let { groups.getOrNull(it + 1) }
    fun release(releaseId: ChapterReleaseId): ChapterRelease? = releaseById[releaseId]

    companion object {
        fun create(storyId: StoryId, groups: List<CanonicalChapterGroup>): ReaderSessionChapterGraph {
            val owned = groups.map { group ->
                require(group.chapter.storyId == storyId)
                require(group.releases.all { it.storyId == storyId })
                group.copy(
                    chapter = group.chapter.copy(releaseIds = group.chapter.releaseIds.toSet()),
                    releases = group.releases.toList(),
                )
            }
            val chapterIndexById = linkedMapOf<CanonicalChapterId, Int>()
            val groupByChapterId = linkedMapOf<CanonicalChapterId, CanonicalChapterGroup>()
            val releaseById = linkedMapOf<ChapterReleaseId, ChapterRelease>()
            owned.forEachIndexed { index, group ->
                chapterIndexById.putIfAbsent(group.chapter.id, index)
                groupByChapterId.putIfAbsent(group.chapter.id, group)
                group.releases.forEach { release ->
                    releaseById.putIfAbsent(release.id, release)
                }
            }
            return ReaderSessionChapterGraph(
                groups = owned,
                chapterIndexById = chapterIndexById.toMap(),
                groupByChapterId = groupByChapterId.toMap(),
                releaseById = releaseById.toMap(),
                releaseIds = releaseById.keys.toSet(),
            )
        }
    }
}
```

- [ ] **Step 4: Add equality/content comparison needed by session update**

Do not compare caller-owned mutable collections directly against the stored graph to save an emission-boundary allocation. Build one defensive candidate snapshot first, then under the session lock compare `candidate.groups == current?.groups`. If equal, discard the candidate, retain object identity/revision, and do not replace the stored graph. If changed, accept that one candidate as the new stored snapshot.

- [ ] **Step 5: Run focused graph tests GREEN**

```bash
./gradlew :reader:testDebugUnitTest --tests '*ReaderRouteSessionStateTest*' --no-daemon
```

- [ ] **Step 6: Commit**

```bash
git add reader/src/main/kotlin/app/openstory/reader/routing/ReaderSessionChapterGraph.kt \
        reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteSessionStateTest.kt
git commit -m "refactor(reader): index session chapter graph once per emission"
```

---

## Task 10: Migrate session, planning context, assembler, and prefetch to the indexed graph

**Files:**
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRoutePlanningContext.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/RouteSnapshotAssembler.kt`
- Modify: `reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteCoordinator.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteSessionStateTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/RouteSnapshotAssemblerTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/RouteSnapshotAssemblerAdaptiveTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/PrefetchCoordinatorTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteCoordinatorAdaptiveTest.kt`

**Interfaces:**
- `ReaderRouteExecutionContext.chapterGraph: ReaderSessionChapterGraph`
- `ReaderRoutePlanningContext.chapterGraph: ReaderSessionChapterGraph`
- Remove full `chapterGroups` copying from hot contexts.

- [ ] **Step 1: Add RED identity-reuse test for one graph revision**

Capture two execution contexts caused by a hard replan within one graph revision and assert:

```kotlin
assertSame(contexts[0].chapterGraph, contexts[1].chapterGraph)
assertEquals(contexts[0].chapterGraphRevision, contexts[1].chapterGraphRevision)
```

Add an equal graph emission case proving revision does not increment.

- [ ] **Step 2: Add RED prefetch reuse evidence**

Capture foreground and prefetch planning context after one committed chapter and assert both reference the same immutable `ReaderSessionChapterGraph` object for the same revision.

- [ ] **Step 3: Replace session `latestChapterGroups` with `latestChapterGraph`**

Change state to:

```kotlin
private var latestChapterGraph: ReaderSessionChapterGraph? = null
```

`updateChapterGraph(groups)` first builds one defensive candidate graph outside the lock. Inside `stateLock`: compare `candidate.groups` with the currently stored graph; if equal, keep the current object/revision. If changed, compute hard invalidation against the candidate, assign the candidate, increment revision once, prune stale known-invalid IDs, complete first-graph readiness, then apply hard invalidation. Call `refreshPrefetch()` only after releasing the lock and only for a changed emission.

- [ ] **Step 4: Prune stale known-invalid release IDs on accepted graph change**

Inside the state lock after assigning the new graph:

```kotlin
knownInvalidLocalFingerprints.keys.retainAll(nextGraph.releaseIds)
```

Add a test:

```text
mark release A invalid
emit graph without A
emit/read session state
A is no longer present in known-invalid map supplied to planning context
```

- [ ] **Step 5: Change foreground and planning contexts**

Replace:

```kotlin
val chapterGroups: List<CanonicalChapterGroup>
```

with:

```kotlin
val chapterGraph: ReaderSessionChapterGraph
```

Do not expose the graph type outside internal Reader routing APIs.

- [ ] **Step 6: Remove full graph copies from `buildContext()` and `refreshPrefetch()`**

Foreground:

```kotlin
chapterGraph = checkNotNull(latestChapterGraph)
```

Prefetch:

```kotlin
chapterGraph = graph
```

Delete the two current `groups.map { it.copy(releases = it.releases.toList()) }` hot-path copies.

- [ ] **Step 7: Replace session scans with indexed calls**

Use:

```kotlin
val nextGroup = graph.nextAfter(committed.chapterId)
val target = graph.group(active.targetChapterId)
```

and target release IDs from that indexed target group.

- [ ] **Step 8: Replace assembler scans/flattening and remove duplicated `targetIndex` state**

Assembler uses:

```kotlin
val targetGroup = context.chapterGraph.group(context.targetChapterId) ?: return null
val committedLanguage = context.committedIdentity
    ?.let { context.chapterGraph.release(it.releaseId) }
    ?.languageTag
```

Remove `targetIndex` from `AssembledRouteSnapshot`; it is no longer needed once navigation neighbors come from the indexed graph. Delete `indexOfFirst` and full graph `flatMap` for these lookups.

In `ReaderRouteCoordinator.committed(...)`, replace the two remaining `context.chapterGroups.getOrNull(...)` calls with:

```kotlin
previousChapterId = context.chapterGraph.previousBefore(context.identity.targetChapterId)?.chapter?.id
nextChapterId = context.chapterGraph.nextAfter(context.identity.targetChapterId)?.chapter?.id
```

Add/adjust coordinator assertions for previous/next chapter IDs so there is one navigation source of truth.

- [ ] **Step 9: Run focused session/assembler/prefetch/coordinator tests**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderRouteSessionStateTest*' \
  --tests '*RouteSnapshotAssemblerTest*' \
  --tests '*RouteSnapshotAssemblerAdaptiveTest*' \
  --tests '*PrefetchCoordinatorTest*' \
  --tests '*ReaderRouteReplanTest*' \
  --tests '*ReaderRouteCoordinatorAdaptiveTest*' \
  --no-daemon
```

- [ ] **Step 10: Run source scan proving hot full-graph copies are gone**

```bash
rg -n 'chapterGroups\s*=|groups\.map \{ it\.copy\(releases|indexOfFirst|flatMap \{ it\.releases' \
  reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt \
  reader/src/main/kotlin/app/openstory/reader/routing/RouteSnapshotAssembler.kt
```

Expected: no old hot-path patterns. A legitimate unrelated match must be manually reviewed, not mechanically deleted.

- [ ] **Step 11: Commit**

```bash
git add reader/src
git commit -m "refactor(reader): reuse indexed graph across route revisions"
```

---

# M7.2-F — Complete L7 Evidence on the Final Tree

## Task 11: Complete bounded deterministic concurrent-model coverage and map every invariant

**Files:**
- Rewrite/expand: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderCoordinatorModelTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderSourceExecutionLimiterTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderSourceHealthRegistryTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRouteReplanTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/PrefetchCoordinatorTest.kt`
- Modify: `reader/src/test/kotlin/app/openstory/reader/routing/ReaderRuntimeStressTest.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelContinuityTest.kt`

**Interfaces:**
- Verification-only. Add no production abstraction unless a deterministic failing scenario proves a real missing ownership boundary.

- [ ] **Step 1: Give every new model scenario a stable behavior-bearing test name**

In `ReaderCoordinatorModelTest`, use explicit names rather than an evidence-metadata enum. At minimum add/retain tests equivalent to:

```text
equalTimestampWinnerIsPrimaryAcrossRecordAndDeliveryPermutations
staleGenerationAndPlanRevisionCannotCommit
hardInvalidationReplansWithoutGenerationIncrement
softGraphAdditionDoesNotRevokeActivePlan
twoSessionsShareHealthButKeepGenerationPlanAndCommitStateIsolated
```

Evidence for invariants owned by other suites remains in those suites; Task 12 maps I01-I22 to exact executable test owners. Do not add a string-only registry that can become stale without proving the referenced test exists.

- [ ] **Step 2: Keep completion time and delivery order separate**

Retain and strengthen the current seeded winner test. For equal timestamps enumerate both completion-record order and both notification-delivery orders; PRIMARY must win stable ties.

Use exactly 512 deterministic seeds (`0..511`) for the multi-event seeded scenarios in this remediation. Every failure message includes the seed and event order.

- [ ] **Step 3: Add direct scenarios for navigation, release selection, retry, and hard replan**

Prove:

```text
NAVIGATE / SELECT_RELEASE / RETRY -> new generation
hard invalidation inside active intent -> same generation, planRevision + 1
old generation completion -> Superseded/no commit
old plan revision completion -> Superseded/no commit
committed identity changes only at valid commit
```

- [ ] **Step 4: Add direct graph/network/source/local invalidation scenarios**

Cover:

```text
GRAPH_REMOVE_RELEASE -> hard replan
GRAPH_ADD_LOWER_CANDIDATE -> active plan remains valid
NETWORK_OFFLINE -> no newly planned remote attempt on replan
SOURCE_OPEN -> remote path suppressed while valid local remains usable
LOCAL_CONFIRMED_INVALID -> current recovery chain continues; a later snapshot excludes the exact local locator
LANGUAGE_ORDER_CHANGE -> hard replan in same generation
```

- [ ] **Step 5: Add health/cancellation scenarios**

Use actual `ReaderSourceHealthRegistry` reducer calls where possible. Prove:

```text
navigation cancellation -> reliability unchanged
hedge loser cancellation -> reliability unchanged
prefetch preemption -> reliability unchanged
late normal remote success while OPEN -> does not close circuit
HALF_OPEN probe success/failure follows existing policy
```

- [ ] **Step 6: Add probe lease two-session scenario**

Two sessions share one `ReaderSourceExecutionLimiter`; only one `tryAcquireHalfOpenProbe(SourceOperationKey(...))` succeeds until release.

- [ ] **Step 7: Add process-concurrency two-session scenario**

Drive two logical sessions/callers sharing one limiter across four distinct sources. Assert:

```text
max foreground active == 2
per-source active <= 1
foreground remote total route budget <= 4 per generation
session A state mutations do not change session B generation/plan/commit state
```

- [ ] **Step 8: Add prefetch start/preempt scenario**

Prove a same-source foreground intent preempts Reader prefetch best-effort, the cancellation is non-penalizing, and a subsequent remote call enters (no leaked lane/permit).

- [ ] **Step 9: Lock the single semantic exhaustion surface in Feature Reader**

In `ReaderViewModelContinuityTest`, add/retain one test where all bounded Reader attempts exhaust and assert the UI receives one terminal Reader failure transition for the generation rather than one visible error per source attempt. Bind this executable test to I18 in Task 12.

- [ ] **Step 10: Add local missing/corruption paired invariant scenario**

Bind I19/I20 with tests that prove:

```text
Missing -> no known-invalid mark
Corruption with no valid copy -> known-invalid exact fingerprint
Corrupt explicit + valid cache -> Hit, no known-invalid mark
```

The valid-copy part is owned by `DownloadAwareReaderDocumentStoreTest` and is referenced in the Task 12 evidence table rather than recreating Downloads internals in `:reader`.

- [ ] **Step 11: Add graph snapshot reuse invariant**

Bind I22 to the Task 10 `assertSame` test. Task 12 records that exact test method and suite in the checkpoint evidence matrix.

- [ ] **Step 12: Make seeded assertion failures show the seed**

Use messages like:

```kotlin
assertEquals(expected, actual, "seed=$seed eventOrder=$events")
```

Never swallow/retry a failing seed.

- [ ] **Step 13: Run the complete focused L7 suite**

```bash
./gradlew :reader:testDebugUnitTest \
  --tests '*ReaderCoordinatorModelTest*' \
  --tests '*ReaderCompetitiveExecutionTest*' \
  --tests '*ReaderSourceExecutionLimiterTest*' \
  --tests '*ReaderSourceHealthRegistryTest*' \
  --tests '*ReaderRouteReplanTest*' \
  --tests '*PrefetchCoordinatorTest*' \
  --tests '*ReaderRuntimeStressTest*' \
  --no-daemon
./gradlew :feature:reader:testDebugUnitTest \
  --tests '*ReaderViewModelContinuityTest*' \
  --no-daemon
```

Expected: both commands BUILD SUCCESSFUL.

- [ ] **Step 14: Review every production change caused by a model failure**

For each model-found defect:

```text
1. retain the smallest failing deterministic test;
2. fix the ownership/root cause in production;
3. rerun the failing test alone;
4. rerun the full focused L7 command;
5. do not weaken the invariant.
```

- [ ] **Step 15: Commit**

```bash
git add reader/src/test reader/src/main \
        feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelContinuityTest.kt
git commit -m "test(reader): complete HES-v1 bounded concurrency model"
```

---

# M7.2-G — Final Verification and Re-Freeze

## Task 12: Run the final gate matrix, audit contradictions, and re-freeze HES-v1

**Files:**
- Modify: `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-2.md`
- Modify: `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1.md`
- Modify: parent HES-v1 design/plan with a short M7.2 addendum pointer.
- No production code changes are expected in this task unless a gate finds a real defect; if so return to the owning task and re-run from its focused gate.

**Interfaces:**
- Produces final evidence-driven M7.2 closure.

- [ ] **Step 1: Run pure engine tests**

```bash
./gradlew :reader:engine:test --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 2: Run Reader effect tests**

```bash
./gradlew :reader:testDebugUnitTest --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 3: Run Downloads + Feature Reader + app unit/compile gates**

```bash
./gradlew \
  :downloads:testDebugUnitTest \
  :feature:reader:testDebugUnitTest \
  :app:testDebugUnitTest \
  :app:compileDebugKotlin \
  --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 4: Run architecture Gradle gates**

```bash
./gradlew \
  :build-logic:test \
  verifyArchitecture \
  --no-daemon
```

Expected: BUILD SUCCESSFUL.

- [ ] **Step 5: Run shell constitutional gates**

```bash
bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/tests/verify-current-architecture-test.sh
bash scripts/verify-current-architecture.sh
```

Expected: all PASS.

- [ ] **Step 6: Run both canonical repository verification scripts**

```bash
bash scripts/verify-fast.sh
bash scripts/verify.sh
```

`verify.sh` is the final closure gate because it additionally runs the Wave 10 production policy check, `lintDebug`, and `:app:assembleDebug`. Do not claim either gate if environment/network prevents it; record the exact blocker and require developer-host evidence before closure.

- [ ] **Step 7: Run structural source assertions**

```bash
# Pure engine stays integer/fixed-point, including inferred literals.
! rg -n '\b(Float|Double)\b|[0-9]+\.[0-9]+([eE][+-]?[0-9]+)?[fF]?\b|[0-9]+[eE][+-]?[0-9]+[fF]?\b|[0-9]+[fF]\b' \
  reader/engine/src/main --glob '*.kt'

# No private process-owner defaults return.
! rg -n 'healthRegistry:\s*ReaderSourceHealthRegistry\s*=\s*ReaderSourceHealthRegistry\(' \
  reader/src/main/kotlin/app/openstory/reader/routing
! rg -n 'executionLimiter:\s*ReaderSourceExecutionLimiter\s*=\s*ReaderSourceExecutionLimiter\(' \
  reader/src/main/kotlin/app/openstory/reader/routing

# Old duplicated route-limit constants are gone from execution classes.
! rg -n 'MAX_TOTAL_FOREGROUND_ATTEMPTS|MAX_FOREGROUND_REMOTE_ATTEMPTS' \
  reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteExecutor.kt \
  reader/src/main/kotlin/app/openstory/reader/routing/CompetitiveCompletionRegistry.kt

# Hot path does not recreate full graph copies.
! rg -n 'groups\.map \{ it\.copy\(releases|chapterGroups\s*=\s*groups\.map' \
  reader/src/main/kotlin/app/openstory/reader/routing/ReaderRouteSession.kt
```

Expected: all negated scans succeed.

- [ ] **Step 8: Verify Room/module/version invariants explicitly**

Confirm with existing architecture scripts/tests and source scan:

```text
17 production modules
Room schema 11
MIGRATION_10_11 retained
no MIGRATION_11_12
:reader:engine -> exactly :core:common
:reader -> implementation(:reader:engine)
no HES version bump
```

- [ ] **Step 9: Build the final invariant-to-test evidence table**

In `adaptive-reader-continuity-hes-v1-m7-2.md`, include I01–I22 with:

```markdown
| ID | Invariant | Test owner | Result |
|---|---|---|---|
| I01 | visible commits/generation <= 1 | `ReaderCoordinatorModelTest` commit-uniqueness scenario | `PASS` only after fresh run |
| I02 | stale generation cannot commit | `ReaderCoordinatorModelTest` stale-generation scenario | `PASS` only after fresh run |
| I03 | stale plan revision cannot commit | `ReaderCoordinatorModelTest` stale-revision scenario | `PASS` only after fresh run |
```

Do not write PASS until the corresponding fresh command has actually run on the final tree.

- [ ] **Step 10: Record exact fresh commands/results**

Checkpoint must distinguish:

```text
fresh M7.2 local/developer-host evidence
historical M7/M7.1 evidence
commands blocked by sandbox/network, if any
```

Never copy an old BUILD SUCCESSFUL line as if it came from M7.2.

- [ ] **Step 11: Add a short parent-design/parent-plan post-freeze pointer**

Add a small note near the top/status section of each parent document:

```markdown
> Post-freeze note (2026-08-26): M7.2 constitutional hardening reopens and repairs
> runtime/verification conformance gaps. See `<new spec/plan path>`. Historical milestone
> evidence remains preserved; final freeze authority follows the M7.2 checkpoint.
```

Do not rewrite 2,000+ lines of historical plan text.

- [ ] **Step 12: Re-freeze only if every blocking gate is evidenced**

Final M7.2 status:

```markdown
Status: **VERIFIED / CLOSED**
Result: **HES-v1 RE-FROZEN**
```

Final aggregate checkpoint status:

```markdown
Status: **M0–M7.2 VERIFIED/CLOSED; HES-v1 FROZEN**
```

If any blocking gate lacks evidence, use `OPEN` or `BLOCKED ON HOST VERIFICATION`; do not mark FROZEN.

- [ ] **Step 13: Commit final closure docs**

```bash
git add \
  docs/internal/checkpoints/adaptive-reader-continuity-hes-v1-m7-2.md \
  docs/internal/checkpoints/adaptive-reader-continuity-hes-v1.md \
  docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md \
  docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md
git status --short
git diff --check
git commit -m "docs(reader): re-freeze HES-v1 after M7.2 hardening"
```

Task 12 is documentation-only. If a final gate finds a production/test defect, return to the owning Task 2–11, add the regression/fix there, commit it there, rerun the relevant focused gate, then restart Task 12 verification. Do not hide a late production fix inside the closure commit.

---

# Task-to-Spec Coverage Matrix

| Spec area | Plan task(s) |
|---|---|
| Freeze authority/reopen | 1, 12 |
| Runtime limits authority | 2 |
| Runtime route guard | 2 |
| Foreground concurrency `<=2` | 3, 11 |
| Prefetch concurrency `<=1` | 3, 11 |
| Per-source concurrency `<=1` | 3, 11 |
| Cancellation/permit cleanup | 3, 11 |
| Explicit process-shared owners | 4 |
| Typed store port | 5 |
| Multi-namespace corruption semantics | 6 |
| Executor missing/corruption mapping | 7 |
| Session known-invalid behavior | 7, 10, 11 |
| Integer health percentile | 8 |
| Engine floating-point guard | 8, 12 |
| Indexed chapter graph | 9, 10 |
| Graph reuse/no hot copy | 10, 11, 12 |
| Known-invalid pruning | 10 |
| L7 bounded model | 11 |
| Final architecture/regression gates | 12 |
| Final evidence/re-freeze | 12 |

---

# Acceptance-Criteria Coverage

| Design AC | Owning task |
|---:|---|
| 1 | 1, 12 |
| 2–7 | 8, 12 |
| 8–12 | 2 |
| 13–17 | 3, 11 |
| 18–19 | 4 |
| 20 | 5 |
| 21–24 | 6 |
| 25–26 | 7 |
| 27–33 | 9–10 |
| 34 | 12 source review; no removal task |
| 35–37 | 11 |
| 38–40 | 12 |
| 41–42 | 12 |

---

# Self-Review Record — Plan vs M7.2 Design and Current Tree

## PR-M7.2-01 — Guard implementation could still duplicate constants

**Risk:** central guard exists but old executor constants survive.

**Fix in plan:** Task 2 explicitly deletes execution-local constants; Task 12 source scan blocks their return.

## PR-M7.2-02 — Competitive validation could run after launching primary

**Risk:** malformed plans might perform effects before rejection.

**Fix in plan:** Task 2 calls `validateCompetitive(...)` before registry/channel/job creation and before `launchAttempt(primary, ...)`.

## PR-M7.2-03 — Foreground semaphore could be acquired before the source lane

**Risk:** global permits can be consumed by blocked same-source callers.

**Fix in plan:** Task 3 locks source-lane-first order and adds a saturation test.

## PR-M7.2-04 — Cancellation test could prove coroutine cancellation but not permit release

**Risk:** a cancelled waiter might leak a semaphore/lane unnoticed.

**Fix in plan:** every cancellation case is followed by a fresh request that must enter.

## PR-M7.2-05 — Typed store default could recurse with production nullable read

**Risk:** `read()` and `readResult()` could call each other indefinitely.

**Fix in plan:** interface default is `readResult -> read`; production overrides `readResult`, and production `read -> readResult` therefore dispatches to the override. Tests must exercise both methods.

## PR-M7.2-06 — Corrupt explicit + valid cache could be misclassified corrupt

**Risk:** early return on first corrupt physical copy.

**Fix in plan:** Task 6 aggregates namespaces and returns `Hit` if any valid exact copy exists.

## PR-M7.2-07 — Cleanup exception could mask confirmed corruption

**Risk:** `blobs.delete` failure becomes `reader.local_read_failed`.

**Fix in plan:** Task 6 isolates post-confirmation deletion in a best-effort helper, propagating only cancellation.

## PR-M7.2-08 — Executor could double-quarantine a valid surviving duplicate

**Risk:** if store reports corruption despite a valid duplicate, executor quarantine deletes the valid copy.

**Fix in plan:** store returns `Hit` whenever any valid exact duplicate survives; executor sees `Corrupt` only when no valid copy exists.

## PR-M7.2-09 — Generic local read exception could be accidentally swallowed by store

**Risk:** infrastructure failure becomes `Missing`.

**Fix in plan:** Task 6 does not catch blob-read exceptions; Task 7 retains client-scoped `reader.local_read_failed` mapping.

## PR-M7.2-10 — Graph wrapper could duplicate chapter-domain ownership

**Risk:** introducing new chapter DTOs would fork `:chapters` semantics.

**Fix in plan:** wrapper stores defensive copies of existing `CanonicalChapterGroup`/`ChapterRelease` only; no new domain translation.

## PR-M7.2-11 — Graph indexes could silently accept conflicting duplicate release IDs

**Risk:** map overwrite creates nondeterministic committed-language lookup.

**Fix in plan:** Task 9 requires duplicate/conflict validation and tests.

## PR-M7.2-12 — Equal graph emission could still allocate/replace a new snapshot

**Risk:** revision stays stable but hot ownership churn remains.

**Fix in plan:** Task 9/10 add identity-reuse assertions and require no accepted replacement on equal graph content.

## PR-M7.2-13 — Known-invalid pruning could happen before hard-invalidation comparison

**Risk:** current active-plan validation might lose state needed to classify the old graph transition.

**Fix in plan:** build next graph, compute hard invalidation from active plan + next graph release membership, then accept graph/prune state within the same session lock. Tests cover hard removal.

## PR-M7.2-14 — Two-session model could accidentally use separate limiters/health registries

**Risk:** test passes while not exercising process-sharing.

**Fix in plan:** Task 4 removes defaults; Task 11 explicitly constructs and shares the same owner instances.

## PR-M7.2-15 — “Exhaustive” model claim could return in checkpoint wording

**Risk:** seeded evidence is overstated.

**Fix in plan:** Task 11 names bounded deterministic coverage and Task 12 records invariant-to-test mapping rather than formal exhaustive proof.

## PR-M7.2-16 — Pure fixed-point guard could incorrectly scan all Reader code

**Risk:** legitimate Feature Reader/session `Float` restoration breaks architecture checks.

**Fix in plan:** Task 8 scopes the guard to `reader/engine/src/main` only.

## PR-M7.2-17 — Integer percentile could change output without version review

**Risk:** subtle replay drift is accepted as a cleanup.

**Fix in plan:** exact percentile tests + full engine goldens run before re-freeze; any drift outside intended equality blocks the task.

## PR-M7.2-18 — Removing `invocationMutex` would create an unplanned Downloads behavior change

**Risk:** cross-subsystem concurrency semantics change.

**Fix in plan:** no task removes it; Task 12 explicitly verifies scope rather than cleanup.

## PR-M7.2-19 — Final docs could declare GREEN from historical logs

**Risk:** repeats the original verification-evidence gap.

**Fix in plan:** Task 12 requires fresh command/result provenance and leaves status OPEN/BLOCKED if environment prevents a blocking gate.

## PR-M7.2-20 — Broad verification might reveal an unrelated failure and tempt scope creep

**Risk:** M7.2 becomes a repository-wide cleanup wave.

**Fix in plan:** only defects caused by or blocking HES constitutional correctness are fixed in this plan. Unrelated pre-existing failures are documented separately unless they invalidate a required final gate.

## PR-M7.2-21 — Task 9 draft accidentally tightened chapter-domain validity

**Risk:** rejecting duplicate/canonical-membership facts in the new index wrapper would be a behavior change not required by HES-v1 remediation.

**Fix in plan:** Task 9 validates only the existing story-ownership contract and builds indexes with first-occurrence semantics matching the old `indexOfFirst` / `firstOrNull` scans.

## PR-M7.2-22 — Guard tests tried to construct impossible `RouteAttempt` shapes

**Risk:** duplicate ownership and untestable fake malformed states.

**Fix in plan:** per-attempt locator shape stays in `RouteAttempt`; runtime guard tests cover budgets, duplicate IDs/locators, roles, and competitive REMOTE/distinct-source shape.

## PR-M7.2-23 — Hedge runtime defense was weaker than the parent HES-v1 contract

**Risk:** malformed future plan could run a LOCAL/same-source hedge.

**Fix in plan:** Task 2 rejects any hedge unless primary and hedge are both REMOTE and use distinct sources.

## PR-M7.2-24 — Equal-emission optimization could sacrifice defensive ownership

**Risk:** comparing caller-owned mutable collections before copy reopens aliasing races.

**Fix in plan:** create one defensive candidate per graph emission; equal content retains the existing stored object/revision. Hot route/replan/prefetch paths still reuse one stored graph object.

## PR-M7.2-25 — Float/Double token scan could miss inferred decimal literals

**Risk:** `val x = 100.0` reintroduces Double while passing a type-token-only guard.

**Fix in plan:** Task 8 rejects both type tokens and floating numeric literal forms, with mutation fixtures for each.

## PR-M7.2-26 — Test-side invariant metadata would be non-executable evidence

**Risk:** an enum/string registry can say a test exists without proving it.

**Fix in plan:** Task 11 uses behavior-bearing executable tests; Task 12 owns the I01-I22 evidence matrix tied to fresh commands.

## PR-M7.2-27 — Graph migration initially omitted coordinator neighbor lookup

**Risk:** compilation/behavior drift because `ReaderRouteCoordinator.committed()` still referenced `context.chapterGroups` and `assembled.targetIndex`.

**Fix in plan:** Task 10 modifies coordinator, removes `AssembledRouteSnapshot.targetIndex`, and derives previous/next from the same indexed graph object.

## PR-M7.2-28 — Typed physical-read refactor initially did not specify `readCurrent()`

**Risk:** deleting/replacing `readLocal()` could break completed-download restoration despite being outside the intended semantic change.

**Fix in plan:** Task 6 explicitly projects `readCurrent()` from explicit-download `PhysicalRead`, preserving `Hit -> document` and `Missing/Corrupt -> null`.

## PR-M7.2-29 — Calling the interface default from a fake store was unnecessarily compiler-sensitive

**Risk:** `super.readResult(...)` syntax/dispatch can distract from the compatibility behavior under test.

**Fix in plan:** the fake's override spells out the default projection (`read -> Hit/Missing`) directly; production still uses the interface default contract for untouched fakes.


## PR-M7.2-30 — Local corruption draft incorrectly forced a hard replan

**Risk:** changing `LOCAL corrupt -> continue current fallback chain` into `LOCAL corrupt -> planRevision++` would violate the parent HES-v1 recovery semantics.

**Fix in plan:** Task 7 keeps same-plan remote/fallback recovery, records the locator as session known-invalid, and proves only a later retry/snapshot excludes it.

## PR-M7.2-31 — Final closure commit could accidentally stage unrelated production work

**Risk:** broad `git add reader downloads app ...` in Task 12 can absorb dirty-tree changes and blur evidence provenance.

**Fix in plan:** the closure commit stages only the four checkpoint/parent-doc files. Any last code defect returns to its owning task and is committed before verification restarts.


---

# Final Plan Invariants

Before implementation starts, the executor should treat these as non-negotiable:

```text
HES-v1 contract/version unchanged
one runtime ceiling owner
one runtime route guard
source-lane-first semaphore order
foreground <= 2 process-wide
prefetch <= 1 process-wide
per-source <= 1
missing != corruption
valid duplicate beats corrupt duplicate
pure engine has no Float/Double
one indexed graph snapshot per changed emission
shared health/limiter dependencies are explicit
bounded L7 evidence covers I01-I22
no Room/module/settings/plugin-runtime scope expansion
fresh evidence before re-freeze
```

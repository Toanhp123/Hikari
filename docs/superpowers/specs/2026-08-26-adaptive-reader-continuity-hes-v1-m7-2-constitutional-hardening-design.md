# Adaptive Reader Continuity / HES-v1 M7.2 Constitutional Hardening Design

Date: 2026-08-26
Status: **DESIGN COMPLETE — REMEDIATION REQUIRED BEFORE HES-v1 MAY BE RE-FROZEN**
Parent design: `docs/superpowers/specs/2026-08-25-adaptive-reader-continuity-hes-v1-design.md`
Parent plan: `docs/superpowers/plans/2026-08-25-adaptive-reader-continuity-hes-v1.md`
Historical checkpoint: `docs/internal/checkpoints/adaptive-reader-continuity-hes-v1.md`

---

## 1. Purpose

M7.2 repairs post-freeze conformance gaps discovered by a source-level audit of the implemented HES-v1 Reader stack.

The patch is not a feature wave and does not introduce HES-v2 behavior. It restores the existing HES-v1 contract where the implementation, verification model, and final checkpoint currently disagree.

The remediation covers five classes of defect:

1. a missing process-wide foreground REMOTE concurrency ceiling;
2. duplicated and incomplete runtime route-budget validation;
3. loss of local corruption semantics at the storage adapter boundary;
4. floating-point arithmetic inside the pure routing engine despite the fixed-point contract;
5. verification and architecture-cleanliness debt around two-session races, graph ownership, and process-shared state.

M7.2 ends only when the final implementation tree and the HES-v1 checkpoint can again truthfully claim the same invariants.

---

## 2. Authority and Rebase Rule

The 2026-08-25 HES-v1 design remains normative unless this document explicitly clarifies or strengthens one of its contracts.

This document has authority over the M7.2 remediation only for these areas:

- §34.2 route/runtime budgets;
- §42 Reader source execution limiting;
- §40 local failure semantics at the Reader storage boundary;
- §12 fixed-point values as applied to health percentiles;
- §57 L7 concurrent-model verification;
- §60 runtime complexity/ownership contracts;
- §62 runtime-policy validation;
- final freeze evidence affected by the audit findings.

Historical checkpoints remain evidence snapshots of what was previously believed and tested. M7.2 does not rewrite historical command output. The implementation plan must explicitly mark HES-v1 as **REOPENED FOR REMEDIATION** before code changes and must create a new closure record after fresh verification.

If this document conflicts with an unchanged HES-v1 requirement outside the areas above, the parent HES-v1 design wins.

---

## 3. Audit Findings That Open M7.2

### 3.1 Missing process-wide foreground REMOTE ceiling

HES-v1 defines:

```text
max concurrent foreground REMOTE = 2
max concurrent prefetch REMOTE   = 1
max active Reader REMOTE per sourceId = 1
```

The implemented `ReaderSourceExecutionLimiter` currently owns:

- one lane per `sourceId`;
- a process-wide prefetch `Semaphore(1)`;
- no process-wide foreground `Semaphore(2)`.

A single session cannot exceed two competitive foreground requests, but two sessions on four different sources can exceed the process ceiling.

This is a runtime conformance defect.

### 3.2 Competitive route validation does not enforce the total-attempt ceiling

Sequential adaptive execution checks:

```text
total attempts <= 7
REMOTE attempts <= 4
unique attempt IDs
unique release/access/locator
sequential role layout
```

Competitive execution currently checks only:

```text
REMOTE attempts <= 4
unique attempt IDs
```

HES-v1 explicitly requires the effect executor to defend runtime ceilings even when a malformed or future planner supplies an invalid route.

This is a duplicated-guard drift defect.

### 3.3 Local corruption collapses into `MissingBlob`

The HES taxonomy distinguishes:

```text
missing local blob
    -> LocalFailure.MissingBlob

present blob with decode/fingerprint mismatch
    -> LocalFailure.FingerprintOrDecodeMismatch
```

The production `DownloadAwareReaderDocumentStore` currently deletes invalid bytes and returns `null`. `ReaderRouteExecutor` therefore sees only a nullable miss and reports `MissingBlob`.

The store test and executor test can both pass independently while the production adapter seam loses the semantic distinction.

This is a typed-boundary defect.

### 3.4 Health percentile calculation violates the fixed-point rule

`SourceHealthState.nearestRankLatency()` currently uses `Double`, `100.0`, and `ceil(...)`.

HES-v1 states that floating-point values do not participate in routing comparison or tie-breaking. p50 latency affects ranking and p95 affects hedge eligibility, so the implementation must remain integer/fixed-point.

This is a pure-engine conformance defect even if current sample counts happen to produce equivalent nearest-rank results.

### 3.5 Task 30 / L7 evidence is materially narrower than the plan

The parent plan required deterministic coverage of:

- navigation;
- graph invalidation;
- network changes;
- source OPEN/HALF_OPEN;
- local corruption;
- language change;
- prefetch and preemption;
- probe lease ownership;
- two-session races;
- global/per-source concurrency ceilings.

The current `ReaderCoordinatorModelTest` contains only three focused scenarios. Those scenarios are useful but do not justify the historical claim that Task 30's concurrent model was complete.

This is a verification-evidence defect.

### 3.6 Session graph state is repeatedly copied/scanned on the hot route path

`ReaderRouteSession` already makes an owned defensive copy on graph emission, but then:

- copies all chapter groups again when building a foreground context;
- copies them again for prefetch;
- scans the list to locate target/next chapter;
- flattens all releases to recover the committed release language.

This does not violate routing correctness, but it weakens the intended “one reactive chapter graph snapshot per emission” architecture and makes the Reader engine less suitable as a reference implementation.

This is an architecture/performance cleanliness defect included because the remediation already touches the same session/context boundary.

### 3.7 Process-shared state is correct in Hilt but optional in constructors

Production Hilt provides singleton `ReaderSourceHealthRegistry` and `ReaderSourceExecutionLimiter`, but `ReaderRouteCoordinator` and `ReaderRouteExecutor` can silently create private defaults.

The HES invariant is process-shared state, not “shared only if a caller remembers the right constructor arguments.”

This is an ownership-enforcement defect.

---

## 4. Goals

M7.2 must:

1. enforce at most two active foreground Reader REMOTE attempts process-wide;
2. keep at most one active Reader prefetch REMOTE attempt process-wide;
3. keep at most one active Reader REMOTE attempt per `sourceId` across Reader sessions and Reader prefetch;
4. preserve foreground-over-prefetch priority for the same source;
5. centralize runtime route-shape/budget validation so sequential and competitive execution cannot drift;
6. enforce total foreground attempts `<= 7` and foreground REMOTE attempts `<= 4` before execution;
7. preserve the distinction between a missing local blob and confirmed decode/fingerprint corruption;
8. mark confirmed local corruption as known-invalid for the owning session only when no valid copy of the same exact locator is available;
9. keep generic storage I/O failure non-corruption and client/local recovery scoped as already designed;
10. remove all floating-point arithmetic/types from `:reader:engine` production source;
11. keep nearest-rank p50/p95 semantics deterministic and integer-only;
12. replace repeated chapter-graph copies/scans with one immutable session-owned indexed graph snapshot per changed graph emission;
13. bound session known-invalid local state to releases still present in the current graph;
14. make process-shared health/limiter dependencies explicit at the coordinator/executor construction boundary;
15. complete the missing L7 race/invariant evidence on the final tree;
16. preserve Room schema 11, the existing module graph, Reader settings ownership, and HES-v1 public pure-engine semantics;
17. re-freeze HES-v1 only after fresh focused and broad verification.

---

## 5. Non-Goals

M7.2 must not:

- introduce a new HES contract, algorithm, policy, or health version;
- change ranking weights, hysteresis thresholds, hedge thresholds, language semantics, or cache selection policy;
- add a second foreground hedge;
- persist source health;
- add Room entities, indices, schema 12, or migrations;
- redesign plugin-runtime global concurrency outside Reader-originated work;
- change Wave 10 background/auth/notification ownership;
- wire the unrelated cache-quota setting debt;
- redesign Downloads concurrency;
- replace coroutines with another scheduling model;
- add telemetry/analytics infrastructure;
- remove `PluginReaderDocumentSource.invocationMutex` in this patch;
- refactor unrelated Reader presentation/UI code;
- turn bounded deterministic model testing into a formal model checker.

---

## 6. Versioning and Compatibility Rule

M7.2 is a conformance repair to HES-v1.

The following remain unchanged:

```text
HesContractVersion.HES_V1
ReaderRoutingAlgorithmVersion.READER_ROUTING_V1
ReaderPolicyVersion.READER_POLICY_V1
HealthPolicyVersion.HEALTH_POLICY_V1
```

No version bump is required because:

- the global concurrency ceiling already exists in the normative HES-v1 contract;
- the runtime budget checks already exist in the normative HES-v1 contract;
- typed local corruption already exists in the HES-v1 observation taxonomy;
- integer nearest-rank is the intended implementation of the existing percentile definition;
- graph indexing and explicit shared-state injection do not change pure decisions.

A version bump becomes necessary only if implementation work discovers that satisfying this document requires an intentional change to a pure decision for the same HES-v1 snapshot/policy/version tuple. In that case implementation must stop and open a separate design instead of silently changing replay semantics.

---

## 7. Constitutional Invariants After M7.2

The final tree must preserve all parent HES-v1 invariants plus the following explicit runtime table.

| Invariant | Exact ceiling/semantic | Owner |
|---|---:|---|
| Planned recovery attempts | `<= 6` | `:reader:engine` policy/planner |
| Total foreground route attempts | `<= 7` | `:reader` runtime guard |
| Planned/runtime foreground REMOTE attempts | `<= 4` | engine policy + runtime guard |
| Concurrent foreground Reader REMOTE | `<= 2` process-wide | `ReaderSourceExecutionLimiter` |
| Concurrent Reader prefetch REMOTE | `<= 1` process-wide | `ReaderSourceExecutionLimiter` |
| Concurrent Reader REMOTE per `sourceId` | `<= 1` | `ReaderSourceExecutionLimiter` |
| HALF_OPEN probe lease per source-operation key | `<= 1` | `ReaderSourceExecutionLimiter` |
| Visible commit per generation | `<= 1` | `ReaderRouteSession`/coordinator gate |
| Local missing vs corruption | never collapsed before executor classification | `ReaderDocumentStore` typed result |
| Pure-engine floating point | none in production | engine constitutional guard |
| Graph defensive copy | once per changed graph emission | `ReaderRouteSession` graph snapshot |
| Process health/limiter ownership | explicit shared dependencies | DI + constructors |

---

## 8. Runtime Budget Authority

### 8.1 Reader runtime limits

`:reader` owns effect-runtime ceilings in one internal definition:

```kotlin
internal object ReaderRuntimeLimits {
    const val MAX_TOTAL_FOREGROUND_ATTEMPTS = 7
    const val MAX_FOREGROUND_REMOTE_ATTEMPTS = 4
    const val MAX_CONCURRENT_FOREGROUND_REMOTE = 2
    const val MAX_CONCURRENT_PREFETCH_REMOTE = 1
    const val MAX_CONCURRENT_REMOTE_PER_SOURCE = 1
}
```

The exact type may be an `object` or equivalent immutable internal value holder. It must not be exposed as engine API.

The pure engine continues to own planning limits (`maxRecoveryAttempts`, `maxPlannedForegroundRemoteAttempts`). Runtime tests must assert the default HES-v1 policy cannot exceed the runtime ceilings.

### 8.2 One runtime route guard

A single internal route guard validates execution plans before any attempt body is launched.

Conceptually:

```kotlin
internal object ReaderRouteRuntimeGuard {
    fun validateSequential(attempts: List<RouteAttempt>)

    fun validateCompetitive(
        primary: RouteAttempt,
        hedge: RouteAttempt?,
        recoveryChain: List<RouteAttempt>,
    )
}
```

Both paths share a common validator for the cross-attempt invariants that can drift between execution implementations:

- total attempts `<= 7`;
- REMOTE attempts `<= 4`;
- unique `attemptId` values across the route;
- unique `(releaseId, accessMode, localFingerprint)` execution locators across the route.

`RouteAttempt` itself remains the single owner of per-attempt shape invariants: non-blank `attemptId`, non-blank LOCAL fingerprint, and no fingerprint on REMOTE attempts. M7.2 must not duplicate those constructor invariants in the effect-runtime guard or create tests that require constructing an impossible `RouteAttempt` state.

Sequential layout additionally requires:

```text
first attempt, when present = PRIMARY
all remaining attempts       = FALLBACK
no HEDGE role
```

Competitive layout additionally requires:

```text
primary.role = PRIMARY
hedge, when present, has role HEDGE
all recoveryChain roles = FALLBACK
if hedge exists: primary.accessMode = REMOTE
if hedge exists: hedge.accessMode = REMOTE
if hedge exists: primary.sourceId != hedge.sourceId
hedge must be a distinct attempt/locator from primary/recovery
```

These are runtime defenses for the existing HES-v1 hedge contract; they do not add a new routing rule.

The guard is defensive. It is not allowed to silently truncate malformed plans.

### 8.3 No duplicated magic ceilings

After migration, `ReaderRouteExecutor` and `ReaderCompetitiveExecution` must not declare independent `MAX_*` route constants.

The limiter likewise consumes the shared concurrency limits instead of repeating raw `1`/`2` values where practical.

---

## 9. Process-Wide Reader REMOTE Limiting

### 9.1 Required permits

`ReaderSourceExecutionLimiter` owns:

```text
source lane              -> one active Reader REMOTE per sourceId
global foreground permit -> two active foreground Reader REMOTE total
global prefetch permit   -> one active prefetch Reader REMOTE total
probe lease set          -> one HALF_OPEN probe owner per SourceOperationKey
```

### 9.2 Acquisition order

Every REMOTE attempt acquires the **per-source lane first**, then its work-class permit:

```text
acquire source lane
    -> if FOREGROUND: acquire global foreground permit
    -> if PREFETCH:   acquire global prefetch permit
    -> invoke source
release work-class permit
release source lane
```

This order is mandatory because acquiring a global foreground permit before waiting on a busy source could consume both process permits with callers that cannot yet execute, starving unrelated sources.

There is no cyclic lock order because an attempt never acquires a second source lane while holding the first.

### 9.3 Foreground/prefetch interaction

Foreground keeps priority over prefetch for the **same source**.

If a prefetch owns that source lane, foreground may cancel the Reader-owned prefetch work job. The cancellation can occur while prefetch is:

- invoking the source; or
- waiting for the global prefetch permit.

The lane must be released through structured cancellation in either case.

M7.2 does not create global foreground priority over a prefetch running on another source. The independent ceilings permit up to:

```text
2 foreground + 1 prefetch = 3 Reader REMOTE attempts across distinct sources
```

That is intentional and consistent with the existing HES-v1 budget table.

### 9.4 Cancellation

Waiting for either a source lane or global semaphore must be cancellable.

Cancellation must not leak:

- a source-lane active marker;
- a foreground/prefetch permit;
- a probe lease;
- a queued waiter.

A cancelled prefetch remains `Cancellation.PrefetchPreempted` for Reader health semantics. A cancelled navigation/hedge loser remains non-penalizing.

---

## 10. Typed Local Read Boundary

### 10.1 Compatibility-preserving port extension

`ReaderDocumentStore.read(releaseId, fingerprint): ReaderDocument?` remains available for existing compatibility callers.

The interface gains a typed Reader execution result:

```kotlin
sealed interface ReaderDocumentReadResult {
    data class Hit(val document: ReaderDocument) : ReaderDocumentReadResult
    data object Missing : ReaderDocumentReadResult
    data object FingerprintOrDecodeMismatch : ReaderDocumentReadResult
}

interface ReaderDocumentStore {
    suspend fun read(releaseId: ChapterReleaseId, fingerprint: String): ReaderDocument?

    suspend fun readResult(
        releaseId: ChapterReleaseId,
        fingerprint: String,
    ): ReaderDocumentReadResult =
        read(releaseId, fingerprint)
            ?.let { ReaderDocumentReadResult.Hit(it) }
            ?: ReaderDocumentReadResult.Missing

    suspend fun readCurrent(releaseId: ChapterReleaseId): ReaderDocument?
    suspend fun write(releaseId: ChapterReleaseId, fingerprint: String, document: ReaderDocument)
    suspend fun quarantine(releaseId: ChapterReleaseId, fingerprint: String)
}
```

The default preserves source/build compatibility for existing in-repository test fakes and non-HES implementations. HES execution must consume `readResult`, not nullable `read`. M7.2 makes no external binary-ABI guarantee for precompiled third-party `ReaderDocumentStore` implementations; none are part of the Hikari module contract.

Production `DownloadAwareReaderDocumentStore` overrides `readResult` to preserve corruption information.

### 10.2 Multi-namespace exact-locator semantics

The exact fingerprint can exist in both:

1. `EXPLICIT_DOWNLOAD`;
2. `AUTOMATIC_CACHE`.

Production read order remains explicit download first, automatic cache second.

For each namespace:

```text
blob absent
    -> continue

blob present + decode/fingerprint invalid
    -> remember confirmed corruption
    -> best-effort delete only that corrupt physical blob
    -> continue, because another namespace may contain a valid copy

valid exact document
    -> best-effort touch metadata
    -> return Hit(document)
```

After all namespaces:

```text
at least one corrupt physical copy and no valid copy -> FingerprintOrDecodeMismatch
no blob in any namespace                            -> Missing
```

A corrupt explicit copy must not cause a valid automatic-cache copy with the same exact locator to be quarantined or rejected.

### 10.3 Cleanup failure is not semantic read failure

Once decode/fingerprint mismatch has been observed, corruption is confirmed independently of cleanup success.

Best-effort deletion rules:

- cancellation propagates;
- ordinary delete failure is swallowed/diagnostic;
- the semantic result remains `FingerprintOrDecodeMismatch` unless another valid copy is found.

Generic blob-read I/O exceptions still propagate to `ReaderRouteExecutor`, which maps them to non-corruption `reader.local_read_failed` / client-scoped recovery.

### 10.4 Executor mapping

`ReaderRouteExecutor` maps:

```text
Hit(document)
    -> validate local document
    -> valid commit path or local validation mismatch

Missing
    -> LocalFailure.MissingBlob
    -> LOCAL_SCOPED
    -> no known-invalid mark
    -> no quarantine claim

FingerprintOrDecodeMismatch
    -> LocalFailure.FingerprintOrDecodeMismatch
    -> LOCAL_SCOPED
    -> diagnostic code reader.local_fingerprint_or_decode_mismatch
    -> quarantine exact fingerprint best-effort
    -> mark exact fingerprint known-invalid for the session
    -> continue the already-planned bounded remote/fallback chain
```

Confirmed local corruption does **not** itself create a hard replan. This preserves the parent HES-v1 contract: the current deterministic recovery chain gets first chance to recover, while any later snapshot/retry treats the exact locator as known-invalid. A local `Hit` that later fails `ReaderDocumentValidatorAdapter.validateLocal()` follows the same confirmed-corruption path as before, retaining its existing validator-owned diagnostic codes.

---

## 11. Pure-Engine Fixed-Point Health Percentiles

### 11.1 Integer nearest-rank formula

p50 and p95 remain nearest-rank percentiles.

For percentile `p` and sample count `n`:

```text
rank = ceil(p * n / 100)
```

The implementation uses integer arithmetic only:

```text
rank = ((p * n) + 99) / 100
```

with a sufficiently wide integer intermediate and final coercion to `1..n`.

No `Double`, `Float`, `ceil`, or floating literal is required.

### 11.2 Constitutional source guard

`:reader:engine/src/main` must contain no production floating-point representation: no `Float`/`Double` type tokens and no floating numeric literals.

The architecture/static verification layer must reject mutations that introduce either explicit primitive types or inferred floating literals into Reader engine production source.

This is intentionally stronger than merely scanning ranking functions. HES-v1 is a fixed-point reference engine, and no current production engine contract requires floating-point values.

### 11.3 Replay/version semantics

The integer implementation must preserve all existing nearest-rank outputs for valid HES-v1 sample sizes (`<= 20`).

If differential tests find an output difference for the same health samples, implementation must investigate before accepting it. The intended remediation is representation cleanup, not a percentile-definition change.

---

## 12. Session-Owned Indexed Chapter Graph

### 12.1 One owned snapshot per changed emission

`ReaderRouteSession.updateChapterGraph()` remains the only entry point that accepts external chapter-group collections.

On each changed emission it creates one immutable Reader-owned graph snapshot, conceptually:

```kotlin
internal class ReaderSessionChapterGraph private constructor(
    val groups: List<CanonicalChapterGroup>,
    private val chapterIndexById: Map<CanonicalChapterId, Int>,
    private val groupByChapterId: Map<CanonicalChapterId, CanonicalChapterGroup>,
    private val releaseById: Map<ChapterReleaseId, ChapterRelease>,
    val releaseIds: Set<ChapterReleaseId>,
) {
    fun indexOf(chapterId: CanonicalChapterId): Int?
    fun group(chapterId: CanonicalChapterId): CanonicalChapterGroup?
    fun previousBefore(chapterId: CanonicalChapterId): CanonicalChapterGroup?
    fun nextAfter(chapterId: CanonicalChapterId): CanonicalChapterGroup?
    fun release(releaseId: ChapterReleaseId): ChapterRelease?
}
```

Construction performs the defensive copies and validates the same story-ownership rules currently enforced by `ReaderRouteSession`.

Derived maps/indexes are immutable after construction. They use first-occurrence semantics for duplicate IDs so the optimization preserves the old `indexOfFirst` / `firstOrNull` behavior rather than inventing a stricter chapter-domain contract.

### 12.2 Contexts share the owned snapshot

Foreground and prefetch planning contexts carry the same immutable graph object for a given graph revision.

They must not call `map { copy(...) }` over the full graph for every plan/replan/prefetch.

### 12.3 Indexed operations

The final route path uses graph indexes for:

- target chapter lookup;
- target index;
- previous chapter lookup;
- next chapter lookup;
- committed release lookup/language;
- active-plan release membership checks;
- pruning known-invalid state.

Full-graph scans remain permitted only where the operation inherently needs full graph traversal; none of the operations above require it.

### 12.4 Revision behavior

An equal graph emission does not create a new revision and does not replace the session's currently owned snapshot. The implementation may construct one temporary defensive candidate snapshot to compare external mutable input safely; that candidate is discarded if content-equal.

A changed graph emission replaces the stored snapshot once and increments `ReaderChapterGraphRevision` exactly once.

The existing hard/soft invalidation semantics remain unchanged:

- target removal/tombstone/planned release removal -> hard invalidation;
- unrelated/lower-candidate/label/metric change that leaves the active route valid -> no plan-revision bump.

### 12.5 Known-invalid state is bounded

After accepting a new graph snapshot, session-local `knownInvalidLocalFingerprints` is pruned to release IDs still present in the graph.

This prevents unbounded lifetime growth while preserving correctness for every currently routable release.

If a removed release later reappears, it may be revalidated from current storage facts. That is acceptable because known-invalid state is intentionally session-local and non-persistent.

---

## 13. Process-Shared Ownership Must Be Explicit

### 13.1 Coordinator construction

`ReaderRouteCoordinator` must require explicit:

```text
ReaderSourceHealthRegistry
ReaderSourceExecutionLimiter
```

It must not silently instantiate private defaults.

Production Hilt continues to bind both as `@Singleton`.

### 13.2 Executor construction

`ReaderRouteExecutor` is internal and must require the limiter passed by its owning coordinator/test fixture. It must not create an implicit limiter.

This makes “shared process runtime state” a construction invariant rather than a DI convention.

### 13.3 Test fixtures

Tests may create fresh registries/limiters, but they must do so explicitly so single-session tests cannot accidentally masquerade as process-shared coverage.

Two-session tests must intentionally share the same instances.

---

## 14. `PluginReaderDocumentSource.invocationMutex` Decision

M7.2 does **not** remove the existing source-object invocation mutex.

Reason:

- `PluginReaderDocumentSourceRegistry` is used by Reader foreground/prefetch through the new process limiter;
- the same registry is also consumed by Downloads code outside `ReaderSourceExecutionLimiter`;
- the current mutex is not sufficient for process-wide Reader correctness because source objects can be recreated, but removing it in this patch would alter behavior outside the Reader-owned concurrency scope.

Therefore:

```text
ReaderSourceExecutionLimiter = sole authority for HES Reader process invariants
invocationMutex              = retained adapter-local serialization, not counted as HES proof
```

Any future unification of Reader and Downloads plugin-operation concurrency requires a separate cross-subsystem design.

---

## 15. L7 Concurrent Model Completion

### 15.1 Model claim

M7.2 must not use the word “exhaustive” to mean formal exhaustive state-space verification.

The required evidence is:

1. direct deterministic tests for every named invariant;
2. bounded pairwise/order permutations for race-sensitive event pairs;
3. deterministic seeded interleavings for multi-event scenarios;
4. a coverage table mapping each invariant to at least one test.

This is reproducible bounded model testing, not a model checker.

### 15.2 Required event alphabet

The model/evidence suite must cover at least:

```text
PRIMARY_VALID(time)
PRIMARY_FAILURE(time, scope)
HEDGE_VALID(time)
HEDGE_FAILURE(time, scope)
DELIVER_NOTIFICATION(attempt)
NAVIGATE(chapter)
SELECT_RELEASE(release)
RETRY
GRAPH_REMOVE_RELEASE
GRAPH_ADD_LOWER_CANDIDATE
NETWORK_OFFLINE
SOURCE_OPEN
LOCAL_CONFIRMED_INVALID
LANGUAGE_ORDER_CHANGE
PREFETCH_START
PREFETCH_PREEMPT
HALF_OPEN_LEASE_ACQUIRE
HALF_OPEN_LEASE_RELEASE
SESSION_B_START
SESSION_B_FOREGROUND
```

Not every event must live in one monolithic simulator. Focused deterministic runtime tests may own an invariant when that produces clearer evidence.

### 15.3 Required final invariants

The final evidence table must prove:

```text
I01 visible commits per generation <= 1
I02 stale generation never commits
I03 stale plan revision never commits
I04 committed saved identity never changes before valid commit
I05 navigation cancellation never lowers reliability
I06 hedge-loser cancellation never lowers reliability
I07 prefetch-preempt cancellation never lowers reliability
I08 late normal success while OPEN never closes circuit
I09 HALF_OPEN probe ownership is unique
I10 ordinary fallbacks remain sequential outside one primary/hedge pair
I11 foreground Reader REMOTE concurrent <= 2 process-wide
I12 foreground Reader REMOTE planned/executed total <= 4
I13 Reader REMOTE per source lane <= 1
I14 hard invalidation increments plan revision without incrementing generation
I15 new user intent increments generation
I16 soft graph update does not revoke a still-valid plan
I17 two sessions share health but not generation/plan/commit state
I18 one transition exhaustion produces one semantic UI failure, not per-source churn
I19 local missing does not create known-invalid state
I20 confirmed local corruption creates known-invalid state only when no valid exact local copy survives
I21 cancelled waiters leak no limiter permit/lane
I22 final graph context is reused within one graph revision
```

### 15.4 Determinism

Seeded tests must print/include the failing seed in assertion context.

No production code may depend on random input.

A race failure is fixed at the production ownership boundary; tests must not weaken an invariant to make a seed pass.

---

## 16. Testing Layers

### L1 — Pure engine unit/property tests

Must cover:

- integer p50/p95 nearest rank;
- sample-count boundaries;
- replay equality;
- existing hedge/ranking goldens unchanged;
- no floating-point production source.

### L2 — Reader effect unit tests

Must cover:

- runtime route guard malformed plans;
- global foreground concurrency two;
- global prefetch concurrency one;
- per-source concurrency one;
- same-source prefetch preemption;
- cancellation cleanup;
- explicit process-shared constructor dependencies.

### L3 — Storage adapter contract tests

Must cover:

- `Missing` for no physical copy;
- `FingerprintOrDecodeMismatch` for only-corrupt copies;
- valid automatic cache wins after corrupt explicit copy;
- corrupt physical copy is cleaned best-effort;
- cleanup failure does not erase confirmed-corruption semantic;
- nullable `read()` compatibility still returns the valid document/null as before.

### L4 — Session/graph tests

Must cover:

- one graph snapshot reused within one graph revision;
- changed graph creates one new snapshot/revision;
- equal graph emission is ignored;
- indexed target/next/release lookup preserves behavior;
- known-invalid map prunes removed releases;
- hard/soft invalidation semantics stay unchanged.

### L5 — Concurrent model/runtime evidence

Must satisfy §15.

### L6 — Broad regression and architecture gates

Must include at least:

```text
:reader:engine:test
:reader:testDebugUnitTest
:downloads:testDebugUnitTest
:feature:reader:testDebugUnitTest
:app:testDebugUnitTest
:app:compileDebugKotlin
verifyArchitecture
scripts/tests/verify-package-boundaries-test.sh
scripts/verify-package-boundaries.sh
scripts/tests/verify-current-architecture-test.sh
scripts/verify-current-architecture.sh
```

If repository policy requires additional Detekt/verification tasks at implementation time, they are also blocking.

---

## 17. Performance and Complexity Contracts

M7.2 adds no wall-clock threshold.

The deterministic complexity targets are:

```text
health latency history <= 20 samples
route attempts <= 7
foreground REMOTE attempts <= 4
concurrent foreground REMOTE <= 2
concurrent prefetch REMOTE <= 1
per-source Reader REMOTE <= 1
chapter graph defensive copy = once per changed graph emission
foreground context full-graph copy = 0
prefetch context full-graph copy = 0
target lookup = indexed
committed release lookup = indexed
next-chapter lookup = indexed
```

The implementation may use ordinary Kotlin maps/lists. No persistent-collection dependency is introduced.

---

## 18. Failure Semantics

M7.2 must preserve the HES failure taxonomy.

### 18.1 Confirmed local corruption

Confirmed only by:

- blob decode failure after bytes were successfully read; or
- decoded document fingerprint mismatch; or
- local document validation proving fingerprint/decode/content invalidity at the exact locator.

Result:

```text
LOCAL_SCOPED
non-penalizing source health
best-effort quarantine
session known-invalid exact fingerprint
remote recovery allowed when eligible
```

### 18.2 Missing local data

No bytes in any local namespace for the exact locator:

```text
LocalFailure.MissingBlob
LOCAL_SCOPED
non-penalizing
no corruption claim
no known-invalid mark
```

### 18.3 Generic local infrastructure failure

Blob store/read infrastructure throws before corruption is established:

```text
RuntimeFailure.Unexpected
CLIENT_SCOPED
non-penalizing
no quarantine
no known-invalid mark
remote recovery may continue
```

### 18.4 Cancellation

Cancellation remains authoritative and must not be converted into storage/source failure.

---

## 19. Migration Sequence

### M7.2-A — Reopen freeze and characterize regressions

Record the post-freeze audit gap, add RED tests for the missing constitutional behavior, and do not change production behavior yet.

### M7.2-B — Runtime limits and shared ownership

Introduce one runtime-limits authority, one route guard, process foreground semaphore `2`, and explicit health/limiter constructor ownership.

### M7.2-C — Typed local-read semantics

Extend `ReaderDocumentStore` compatibly, override the typed result in `DownloadAwareReaderDocumentStore`, and map it through executor/session invalidation.

### M7.2-D — Pure fixed-point hardening

Replace health percentile floating point with integer nearest-rank and strengthen constitutional source guards.

### M7.2-E — Session graph ownership cleanup

Introduce one immutable indexed graph snapshot per changed emission and prune stale known-invalid entries.

### M7.2-F — Complete L7 model and final verification

Complete deterministic invariant evidence on the final architecture, run focused/broad gates, update checkpoint/docs, and re-freeze HES-v1 only from fresh evidence.

---

## 20. Acceptance Criteria

M7.2 is complete only when all criteria below are true on the same final tree.

1. HES-v1 historical checkpoint is explicitly reopened before remediation and receives a new closure update after verification.
2. No HES/algorithm/policy/health version is changed.
3. No module is added or removed; the production graph remains 17 modules plus benchmark test module.
4. Room remains schema 11 and no migration 11->12 exists.
5. `:reader:engine` production dependency remains exactly `:core:common`.
6. `:reader:engine/src/main` contains no `Float`/`Double` type usage or floating numeric literals.
7. p50/p95 use integer nearest-rank and existing pure routing goldens/replay remain unchanged.
8. A shared `ReaderRuntimeLimits` authority exists in `:reader` and duplicated route-limit magic constants are removed from executor/competitive execution.
9. Sequential runtime validation rejects >7 attempts and >4 REMOTE attempts.
10. Competitive runtime validation rejects >7 attempts and >4 REMOTE attempts.
11. Both execution paths reject duplicate attempt IDs and duplicate release/access/locator attempts.
12. Both execution paths validate role layout appropriate to their execution model.
13. Two sessions sharing one limiter can never run more than two foreground REMOTE blocks concurrently across distinct sources.
14. At most one prefetch REMOTE block runs process-wide.
15. At most one Reader REMOTE block runs per source ID.
16. Same-source foreground still preempts queued/running Reader prefetch best-effort.
17. Cancelled waiters leak no source lane/global permit.
18. `ReaderRouteCoordinator` cannot silently create private health/limiter instances.
19. `ReaderRouteExecutor` cannot silently create a private limiter.
20. `ReaderDocumentStore` exposes typed exact-read semantics while retaining nullable `read()` compatibility.
21. Production no-blob read returns `ReaderDocumentReadResult.Missing`.
22. Production only-corrupt exact copies return `FingerprintOrDecodeMismatch` even after best-effort cleanup.
23. A corrupt explicit copy plus valid automatic-cache copy returns `Hit(valid)` and does not mark the locator globally invalid.
24. Generic blob-read I/O failure is not converted to corruption.
25. Executor maps typed missing to `LocalFailure.MissingBlob` with no known-invalid mark.
26. Executor maps confirmed corruption to `LocalFailure.FingerprintOrDecodeMismatch`, best-effort quarantine, and session known-invalid mark without forcing a hard replan; the current bounded recovery chain still runs.
27. Session owns one immutable indexed graph snapshot per changed graph emission.
28. Foreground replans within the same graph revision do not copy the full graph.
29. Prefetch within the same graph revision does not copy the full graph.
30. Target, previous/next-chapter, and committed-release lookup use graph indexes.
31. Equal graph emissions do not increment revision.
32. Hard/soft graph invalidation behavior remains compatible with current HES-v1 tests.
33. Session known-invalid fingerprints are pruned for releases removed from the current graph.
34. `PluginReaderDocumentSource.invocationMutex` is not treated as proof of Reader process concurrency and is not removed in this patch.
35. Every invariant I01–I22 in §15.3 maps to deterministic test evidence.
36. Failing seeded model assertions expose the seed.
37. No model-test invariant is weakened to hide a production race.
38. All focused Reader/engine/downloads tests pass on the final tree.
39. Feature Reader and app unit/compile regression gates pass.
40. Architecture/package-boundary verifier tests and scripts pass.
41. Final checkpoint distinguishes fresh M7.2 evidence from historical M7/M7.1 evidence.
42. HES-v1 is marked FROZEN again only after criteria 1–41 are evidenced.

---

# Self-Review Record

The following contradictions/gaps were found while writing this remediation design and resolved before finalization.

## SR-M7.2-01 — “Add foreground semaphore” could waste permits while waiting on a busy source

**Conflict:** acquiring the global foreground permit before the per-source lane lets two requests for the same busy source consume both process permits while doing no remote work.

**Resolution:** source lane is acquired first, then foreground/prefetch global permit. All Reader REMOTE paths use that order.

## SR-M7.2-02 — Source-first acquisition could deadlock if an attempt acquired multiple source lanes

**Conflict:** nested source-lane acquisition could create cycles.

**Resolution:** one attempt owns exactly one source lane and never acquires another before release. Probe lease acquisition is non-blocking and independent. No lock cycle is introduced.

## SR-M7.2-03 — One global semaphore for foreground + prefetch would change the HES budget

**Conflict:** a single total-REMOTE semaphore could reduce intended `2 foreground + 1 prefetch` behavior to two total requests.

**Resolution:** foreground and prefetch retain separate process-wide permits; per-source lane still composes them safely.

## SR-M7.2-04 — Returning corruption immediately could destroy a valid duplicate copy

**Conflict:** the same exact fingerprint may exist in explicit-download and automatic-cache namespaces. If the first copy is corrupt and the second is valid, immediate `Corrupt` would cause unnecessary quarantine of valid content.

**Resolution:** production typed read remembers corrupt physical copies, continues through all eligible namespaces, and returns `Hit` if any exact valid copy survives. `Corrupt` is returned only when no valid exact copy is found.

## SR-M7.2-05 — Cleanup failure could incorrectly erase confirmed-corruption information

**Conflict:** if blob deletion throws after decode failure, propagating the delete exception would reclassify confirmed corruption as generic I/O.

**Resolution:** post-confirmation cleanup is best-effort except cancellation. The semantic result remains corruption.

## SR-M7.2-06 — Making `ReaderDocumentStore.read()` typed would unnecessarily break compatibility

**Conflict:** the parent HES migration envelope explicitly preserves `ReaderDocumentStore`, and nullable `read()` has existing app/benchmark/test consumers.

**Resolution:** add `readResult()` with a compatibility default; keep nullable `read()` unchanged. HES execution migrates to the typed method.

## SR-M7.2-07 — A default typed method can still hide corruption in fake stores

**Conflict:** `readResult()` defaulting through nullable `read()` cannot infer corruption.

**Resolution:** this is intentional compatibility behavior for generic fakes. Production `DownloadAwareReaderDocumentStore` must override `readResult`, and storage contract tests assert the override returns the typed corruption state. Executor tests explicitly feed the typed state.

## SR-M7.2-08 — Removing `PluginReaderDocumentSource.invocationMutex` looked like cleanup but crosses subsystem ownership

**Conflict:** Downloads consumes the same source registry outside the Reader limiter.

**Resolution:** retain the mutex in M7.2 and document that it is not HES process-concurrency evidence. Any global plugin-operation limiter is separate work.

## SR-M7.2-09 — Centralizing all limits in the pure engine would mix effect policy into the reasoner

**Conflict:** concurrent foreground/prefetch permits are runtime/effect concerns, not pure route-decision inputs.

**Resolution:** keep runtime ceilings in internal `:reader` ownership and add contract tests that pure planned ceilings fit inside runtime ceilings.

## SR-M7.2-10 — Hardcoding `7` in multiple execution classes recreates the original drift

**Conflict:** the sequential and competitive paths could diverge again.

**Resolution:** one `ReaderRuntimeLimits` authority plus one `ReaderRouteRuntimeGuard`; no independent executor/competitive constants.

## SR-M7.2-11 — “No floating point” could accidentally ban UI restoration `Float`

**Conflict:** Reader presentation/session restoration legitimately uses `Float`, but the HES fixed-point rule applies to the pure engine.

**Resolution:** the strengthened ban is scoped exactly to `reader/engine/src/main`, not all Reader modules.

## SR-M7.2-12 — Integer percentile replacement might accidentally change HES replay semantics

**Conflict:** a representation cleanup must not silently alter p50/p95 outputs.

**Resolution:** exact sample-boundary/differential tests are mandatory. Any observed output drift stops implementation for versioning review.

## SR-M7.2-13 — Graph indexing could become a large unrelated data-model rewrite

**Conflict:** replacing chapter/domain models would expand scope and risk Wave 8/chapters ownership.

**Resolution:** `ReaderSessionChapterGraph` is an internal derived ownership/index wrapper around existing `CanonicalChapterGroup` and `ChapterRelease`; no chapter-domain API changes.

## SR-M7.2-14 — Rebuilding indexes on every replan would provide no benefit

**Conflict:** an indexed wrapper only helps if it is retained across plan revisions.

**Resolution:** construct it once per changed graph emission and pass the same immutable object through foreground/prefetch contexts for that graph revision.

## SR-M7.2-15 — Pruning known-invalid state might forget a corrupt release that later reappears

**Conflict:** keeping all history is safe but unbounded; pruning means a reintroduced release may be attempted again.

**Resolution:** prune releases no longer routable in the current graph. Session known-invalid state is intentionally non-persistent; a reintroduced release is revalidated against current storage and can be marked invalid again.

## SR-M7.2-16 — Requiring explicit shared dependencies makes unit tests more verbose

**Conflict:** constructor defaults are convenient but undermine the process-shared invariant.

**Resolution:** correctness wins. Tests construct fresh instances explicitly; two-session tests explicitly share them.

## SR-M7.2-17 — The old Task 30 uses “exhaustively model,” which overstates current and feasible proof

**Conflict:** seeded tests are not formal exhaustive verification.

**Resolution:** M7.2 defines bounded deterministic model evidence precisely: direct invariant tests, pairwise/order permutations, seeded interleavings, and a coverage matrix. Final docs must not claim formal exhaustive state-space proof.

## SR-M7.2-18 — Reopening HES could be mistaken for invalidating historical test evidence

**Conflict:** old M7/M7.1 commands did run and must not be rewritten.

**Resolution:** preserve historical checkpoint evidence, mark the freeze state reopened prospectively, and append fresh M7.2 closure evidence rather than replacing old transcripts.

## SR-M7.2-19 — Fixing local typed semantics could accidentally make cache persistence part of commit correctness

**Conflict:** touching storage paths can blur read correctness with best-effort cache writes.

**Resolution:** M7.2 changes local-read classification only. Existing remote valid-commit-before-best-effort-cache-write semantics remain unchanged.

## SR-M7.2-20 — Completing L7 before structural cleanup would verify a tree that is immediately changed

**Conflict:** tests written before graph/ownership refactors could miss final-tree races.

**Resolution:** add focused RED regression tests early, but perform the complete L7 coverage matrix and closure pass after all production M7.2 changes.


## SR-M7.2-21 — Runtime guard draft duplicated impossible per-attempt states

**Conflict:** `RouteAttempt` already rejects blank LOCAL fingerprints, REMOTE fingerprints, and blank attempt IDs. Re-testing those inside the effect guard would duplicate ownership, while tests could not construct the malformed state through the real type.

**Resolution:** `RouteAttempt` remains sole owner of per-attempt shape. `ReaderRouteRuntimeGuard` owns cross-attempt budgets, duplicate locator/ID rules, and execution-layout roles only.

## SR-M7.2-22 — Competitive guard initially under-specified the existing hedge contract

**Conflict:** checking only `HEDGE` role would allow a malformed future effect plan with a LOCAL primary, LOCAL hedge, or same-source hedge even though HES-v1 requires a REMOTE primary/hedge pair on distinct sources.

**Resolution:** when a hedge exists, runtime validation independently requires REMOTE/REMOTE and distinct `sourceId` values before any job is launched.

## SR-M7.2-23 — “No new snapshot on equal emission” conflicted with defensive-copy safety

**Conflict:** comparing the session-owned graph directly with caller-owned mutable collections before copying can reintroduce aliasing/race risk merely to avoid one allocation on an emission boundary.

**Resolution:** correctness wins. The implementation may build one temporary defensive candidate per emission, compare owned content, and retain the existing stored object/revision when equal. The hot-path guarantee is no full graph copy per plan/replan/prefetch, not zero allocation on upstream emissions.

## SR-M7.2-24 — Graph indexing must not invent stricter chapter-domain validity

**Conflict:** rejecting duplicate/misaligned canonical facts in the new Reader wrapper could change behavior beyond the M7.2 conformance patch. Existing Reader scans use first-match semantics and only enforce story ownership at session ingestion.

**Resolution:** the wrapper preserves existing domain behavior: validate story ownership, keep ordered groups, build indexes with first-occurrence semantics, and do not add new chapter-domain rejection rules. Upstream graph normalization remains `:chapters` ownership.

## SR-M7.2-25 — Type-token-only static scanning can miss inferred floating literals

**Conflict:** a future `val scale = 100.0` would use `Double` without containing the token `Double`.

**Resolution:** the engine constitutional guard rejects both `Float`/`Double` type tokens and floating numeric literals in `reader/engine/src/main/**/*.kt`. Tests include mutation fixtures for both forms.

## SR-M7.2-26 — A test-side invariant registry would look stronger than it is

**Conflict:** string metadata mapping invariant IDs to test names cannot prove that an external test exists or asserts the claimed behavior.

**Resolution:** do not add a metadata-only registry. Use executable named tests plus a final checkpoint evidence matrix whose rows cite exact test owners and fresh command results.


## SR-M7.2-27 — Indexed graph migration initially left a second derived navigation source

**Conflict:** keeping `AssembledRouteSnapshot.targetIndex` while contexts also own `ReaderSessionChapterGraph` would leave coordinator previous/next navigation dependent on a duplicated derived index.

**Resolution:** remove `targetIndex` from `AssembledRouteSnapshot`; assembler resolves only the target group, while coordinator derives previous/next directly from the same session graph via `previousBefore` / `nextAfter`.

## SR-M7.2-28 — Typed-store refactor could accidentally strand `readCurrent()` on a removed helper

**Conflict:** replacing `readLocal()` with typed physical reads changes the helper used by completed-download restoration even though `readCurrent()` is outside the HES exact-read classification change.

**Resolution:** explicitly project `readCurrent()` from the explicit-download `PhysicalRead`: `Hit -> document`, `Missing/Corrupt -> null`, preserving existing behavior and exception semantics.


## SR-M7.2-30 — Typed corruption draft accidentally turned local invalidation into a hard replan

**Conflict:** the parent HES-v1 contract explicitly says local locator corruption quarantines the exact fingerprint and **continues the current remote/fallback chain**. Forcing a plan-revision bump would change execution semantics rather than repair the adapter information loss.

**Resolution:** store/executor preserve and record corruption, but `onLocalInvalidated` only marks the session locator known-invalid. The active immutable recovery chain continues. A later retry/replan/snapshot excludes that locator.

---

## Final Design Invariants

The remediation is intentionally narrow:

```text
restore existing HES-v1 contracts
centralize effect-runtime budget ownership
preserve local failure information across adapters
keep pure routing integer/fixed-point
make process-shared state explicit
reuse one immutable graph snapshot per emission
prove the final tree with bounded deterministic concurrency evidence
change no Room schema, module graph, settings ownership, or HES version
```

Any implementation step that requires violating one of these invariants is outside M7.2 and requires a new design review.

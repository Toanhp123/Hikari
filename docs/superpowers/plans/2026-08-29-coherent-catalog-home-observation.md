# Coherent Catalog Home Observation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `CatalogRepository.observeHomes()` emit only internally coherent Home graphs while preserving the P2 home-scoped Room performance contract and simplifying Discover's bootstrap handoff assumptions.

**Architecture:** Replace four independently state-producing Room Flows plus `combine` with one Room invalidation Flow that triggers a transactional, home-scoped snapshot read. The repository remains the consistency boundary; Discover consumes one coherent public stream and keeps only feature-level bootstrap/semantic convergence state. Existing Room schema and public repository/service interfaces stay unchanged.

**Tech Stack:** Kotlin, kotlinx.coroutines Flow, AndroidX Room 2.8.4, Android instrumentation tests, feature JVM tests, Detekt, Bash architecture/performance policies.

**Spec:** `docs/superpowers/specs/2026-08-16-performance-wave-p2-catalog-room-chapters-design.md` and `docs/superpowers/specs/2026-08-27-content-state-contract-v1-design.md`

## Global Constraints

- No Room schema/version change.
- `DiscoverViewModel` retains exactly one long-lived production `repository.observeHomes()` subscription.
- Home observation must never materialize all `catalog_entries`; entry reads remain scoped to source IDs referenced by Home items.
- One public Home emission must be assembled from one transactional database read generation.
- Preserve sparse-refresh durable metadata semantics by reading committed Room rows, not `CatalogRefreshService` mutation snapshots.
- Manual refresh must retain prior Ready content during convergence.
- No debounce/timing heuristic may be used as a consistency barrier.

---

### Task 1: Repository-owned coherent Home snapshot

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/repository/CatalogRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogHomeDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt`

**Interfaces:**
- Consumes: `OpenStoryDatabase.invalidationTracker`, `CatalogHomeDao` suspend reads, `CatalogDao.homeEntries()` plus the existing refresh-only bulk `entries(pluginId, sourceIds)`.
- Produces: unchanged `CatalogRepository.observeHomes(): Flow<List<CatalogHomeSnapshot>>`, now with coherent-emission semantics.

- [x] **Step 1: Add repository regression test for atomic public Home emissions**

Add an instrumentation test that collects `observeHomes()` across a Home refresh replacing one source item with another and tags both refresh generations with distinct timestamps. Assert every observed single-provider Home is exactly generation 1 or generation 2 — never a mixed header/item generation or header/section-only transient graph.

- [x] **Step 2: Add synchronous Home structure reads**

Add `suspend fun snapshots()`, `suspend fun sections()`, and `suspend fun items()` to `CatalogHomeDao`, retaining existing ordered SQL semantics.

- [x] **Step 3: Replace four-Flow combine with one invalidation-driven transactional read**

Track `catalog_home_snapshots`, `catalog_home_sections`, `catalog_home_items`, and `catalog_entries` using `database.invalidationTracker.createFlow(...)`. For each signal, execute `database.withTransaction { readHomeSnapshots() }`, where structure rows are read once, catalog entries are loaded once through the existing Home-scoped JOIN as a suspend query, converted once, and mapped into `CatalogHomeSnapshot` values. Apply `distinctUntilChanged()` after the coherent snapshot is assembled.

- [ ] **Step 4: Run focused Room test**

Run:

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCatalogRepositoryTest \
  --no-daemon
```

Expected: PASS.

### Task 2: Simplify Discover bootstrap handoff after the repository contract is strengthened

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverRefreshPipeline.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverRefreshPipelineTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt`

**Interfaces:**
- Consumes: coherent `CatalogRepository.observeHomes()` emissions plus refresh outcome/report classification from `DiscoverRefreshExecution`.
- Produces: unchanged `DiscoverUiState`, authoritative-empty semantics, and one long-lived production Home subscription. Refresh reports derive committed timestamps directly from successful `CatalogRefreshResult` values, so refresh opens no second Home observation.

- [x] **Step 1: Remove storage-generation heuristics and the second Home source-of-truth**

Remove `homeEmissionVersion`, `DiscoverObservedHomes`, `startedAfterHomeVersion`, and the bootstrap `execution.homes` content bridge. Those existed to infer whether independently observed Room pieces had caught up and created a second Home authority inside Discover. Retain one monotonic feature-level `bootstrapSuperseded` latch. Before bootstrap, a non-empty coherent Home graph is authoritative cache. During automatic bootstrap, however, individual providers commit sequentially, so neither `InFlight` nor `AwaitingCommittedHome` may project intermediate Home graphs. Successful bootstrap refreshes retain every successful `(pluginId, refreshedAtEpochMillis)` pair and remain Pending until the long-lived repository observation contains each commit or a newer commit for the same provider. `NoEnabledProviders` and `Failed` remain explicit terminal bootstrap states. A coherent header with zero items becomes authoritative empty only after this refresh-attempt barrier is satisfied.

- [x] **Step 2: Remove the refresh-side Home observation and lock the long-lived authority regression**

Build refresh-report timestamps directly from `CatalogRefreshResult.Success.refreshedAtEpochMillis`, which is produced only after `commitHomeRefresh()` succeeds. Do not re-observe Home from `DiscoverRefreshPipeline`. Add a regression with two successful providers where the long-lived observation first exposes only one provider's coherent empty header: Discover must remain Pending, never emit `Ready(empty)`, and become Ready only after Home contains both successful commit timestamps or newer commits that supersede them. Keep the successful-empty regression backed by a committed header-only Home so authoritative empty still reaches Ready after the full attempt settles.

- [x] **Step 3: Run focused Discover regression tests**

Run:

```bash
./gradlew :feature:catalog:testDebugUnitTest \
  --tests '*DiscoverViewModelTest*' \
  --tests '*DiscoverCanonicalBootstrapPipelineTest*' \
  --tests '*DiscoverProjectionPipelineTest*' \
  --tests '*DiscoverProjectionTest*' \
  --no-daemon
```

Expected: PASS with no storage-specific readiness heuristic in `DiscoverViewModel`.

### Task 3: Update P2 guardrail and verification docs

**Files:**
- Modify: `scripts/tests/performance-wave-p2-policy-test.sh`
- Modify: `scripts/tests/performance-wave-4-policy-test.sh`
- Modify: `docs/internal/checkpoints/performance-wave-p2.md`
- Modify: `docs/superpowers/plans/2026-08-27-content-state-contract-v1-implementation-plan.md`

**Interfaces:**
- Policy must reject a return to all-entry Home materialization or independent public Home graph composition.

- [x] **Step 1: Change static P2 assertions**

Require `invalidationTracker.createFlow`, transactional Home snapshot assembly, suspend `homeEntries()` scoping, and absence of `dao.observeAllEntries()` / independently observed Home-entry state in `RoomCatalogRepository.observeHomes()`. Restore the Wave 4 guard that refresh reports use successful commit timestamps and do not open another Home observation.

- [x] **Step 2: Document the strengthened consistency invariant**

Record that P2's performance contract is home-scoped materialization and one-time row conversion, not mixed-generation public emissions; Home observation now uses one invalidation stream plus a transactional scoped rebuild. Correct the CSC implementation plan so it no longer instructs future work to reintroduce `DiscoverRefreshExecution.homes` or a post-refresh Home side-read.

- [x] **Step 3: Run static architecture/performance verification**

Run:

```bash
bash scripts/tests/performance-wave-p2-policy-test.sh
bash scripts/tests/performance-wave-4-policy-test.sh
bash scripts/verify-package-boundaries.sh
bash scripts/verify-current-architecture.sh
```

Expected: all commands PASS.

- [x] **Step 4: Run Detekt**

Run:

```bash
./gradlew detekt --no-daemon
```

Expected: PASS.

- [x] **Step 5: Self-review**

Review for: Room transaction nesting/deadlock risks; cancellation propagation; duplicate emissions; SQLite `IN` chunk bound; deterministic ordering; empty/no-provider semantics; sparse metadata preservation; Discover single-subscription guardrail; and policy contradictions. Fix any discovered issue before producing the patch.

## Sandbox verification status

- Focused Discover tests, `:build-logic:test`, `:storage:room:testDebugUnitTest`, and Detekt pass.
- Static P2/Wave-4/package-boundary/current-architecture checks pass under Git Bash.
- The connected Room instrumentation test remains intentionally unrun in this verification pass.

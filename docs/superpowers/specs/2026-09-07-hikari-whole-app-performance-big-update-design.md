# Hikari Whole-App Performance Big Update — Target Architecture Design

**Date:** 2026-09-07  
**Status:** Design baseline for implementation planning  
**Audit input:** `Hikari-performance-architecture-audit-whole-app-big-update-2026-09-07.md`  
**Original target snapshot:** `Hikari-perf-discover-end-to-end(1).zip`  
**Revalidated target snapshot:** `Hikari-master.zip` (`c5b1102387f5a310fc00b926964751212e900051`)
**Revalidated archive SHA-256:** `a4b7b17a5f59dde6ff5d7ae1884dae043f638f9d36c35b8b1b601a98feb1f9b1`
**Superseded v2 planning bundle SHA-256:** `d3813d2a3a1c4216ccfd2c1d33e1593949ac2fc59a7d3412da87a49d3139ad0d`

> This document defines the target architecture for the whole-application performance program. It does not optimize one screen or one module. It converts the 33 confirmed structural root-cause families and 7 structural risks from the accepted audit into explicit complexity, invalidation, ownership, allocation, failure-state, and lifetime contracts.

---

## 1. Goal

Make Hikari's foreground and application-lifetime cost scale primarily with the data that an operation actually needs, while preserving canonical identity, Reader integrity, background durability, warm-navigation behavior, and modular-monolith/clean boundaries.

The program is successful when:

1. point and bounded operations no longer reconstruct unrelated global state;
2. wide source metadata is not decoded/hashed when compact evidence or a projection is sufficient;
3. one logical durable work item has one execution owner or a join path;
4. reactive invalidation follows semantic domains rather than broad storage tables;
5. Reader/cache hot paths use incremental accounting, short critical sections, and bounded encoded-byte ownership;
6. Chapter synchronization applies deltas instead of repeatedly recomputing cumulative prefixes;
7. background pagination is true keyset/bounded work rather than repeated full-corpus scans;
8. inactive retained navigation preserves the P6 warm-navigation benefit while avoiding semantic work when hidden, if measurement proves that work exists;
9. benchmark gates measure scaling slope across aged state, payload width, redirect history, cache cardinality, Chapter graph size, and durable backlog.

---

## 2. Non-goals

This program does **not**:

- replace Hikari's modular-monolith architecture with a global performance manager;
- remove Reader SHA-256/integrity verification;
- remove retained top-level composition merely because a destination is hidden;
- pool JavaScript isolates without benchmark evidence;
- impose a cross-capability resource arbiter before contention is measured;
- change canonical identity/reconciliation semantics to gain speed;
- cap candidate fan-out or weaken matching quality without a collision-corpus benchmark;
- convert every global scan into a point query when the operation is explicitly maintenance/recovery work;
- introduce speculative caches whose invalidation semantics are weaker than the durable source of truth.

---

## 3. Architectural approach

### 3.1 Options considered

**Option A — Tactical hotspot patching.** Move Search CPU to `Dispatchers.Default`, add selected SQL indexes, cache a few lists, and batch a few DAO writes. This gives quick local wins but preserves the broken scaling laws behind A1/A5/A6/X8/X13/X15.

**Option B — One global performance coordinator.** Introduce one central scheduler/cache/index/resource manager for Catalog, Reader, Chapters, Downloads, and plugins. This creates a new god-layer, weakens module ownership, and couples unrelated correctness domains.

**Option C — Boundary-driven structural repair.** Keep ownership in the domain that owns the semantics, but give each shared boundary an explicit performance contract: compact evidence, point/bulk identity, coherent projection reads, durable work claims, incremental cache ledger, delta Chapter commits, and bounded reactive demand.

### 3.2 Decision

Use **Option C**.

The architecture is organized around seven target boundaries:

1. **Identity & Projection Access Plane**
2. **Catalog Evidence Plane**
3. **Canonical Work Plane**
4. **Reader/Automatic-Cache Plane**
5. **Chapter Delta & Scheduling Plane**
6. **Reactive UI Demand Plane**
7. **Performance Contracts & Risk Gates**

No boundary may become a generic cross-domain god object.

---

## 4. Global invariants

### 4.1 Correctness invariants

- Story redirect resolution remains cycle-safe and canonical.
- Reconciliation ranking/evidence semantics remain equivalent to the existing pure engine until a separately benchmarked/spec-reviewed change is approved.
- Canonical promotion remains guarded against stale identity/source/currentness state.
- Durable canonical work remains crash-recoverable.
- Reader security invalidation continues to outrank stale in-flight completion.
- Reader asset and Chapter blob integrity verification remains mandatory.
- Explicit downloads and automatic cache remain semantically distinct even if they share physical blob infrastructure.
- Chapter authoritative FULL sync removal semantics remain identical to the current implementation.
- Notification event identity/order remains contract-compatible.
- Plugin capability/network/authentication validation is not weakened for performance.
- Plugin capability expansion is never auto-approved merely to avoid a repeated `NEEDS_REVIEW` cost.
- Terminal plugin retry suppression is identity/state-bound; transient retryable failures and cancellation remain recoverable.
- Plaintext plugin credentials are not retained beyond existing semantics unless `RISK-PLUGIN-AUTH-CACHE` is promoted through benchmark + threat review.
- P6 retained-navigation warm-state behavior is preserved unless an explicit benchmark demonstrates a superior replacement.

### 4.2 Performance invariants

- A point lookup may not materialize a complete unrelated corpus by default.
- A bounded set API must have an explicit cardinality/chunking contract.
- Foreground user work may not synchronously drain historical recovery backlog.
- A hot-path lock may not cover filesystem I/O or broad Room reads.
- Encoded Reader payload ownership must have a bounded-copy contract.
- Page/batch processing must operate on deltas or true bounded pages, not repeated cumulative/full-corpus reprocessing.
- `distinctUntilChanged()` is not accepted as a performance fix when expensive work happens before distinctness is evaluated.
- A data-lifetime fix must preserve durable identity evidence even when presentation metadata is compacted.
- A plugin metadata/control-plane query may not load `main.js` or bundled `.osp` payload bytes unless the operation actually needs executable/package verification data.
- A stable terminal/user-action-required plugin outcome may not trigger the same expensive provision/package read on every unrelated runtime call; retryability must drive future retry ownership.
- One authenticated plugin HTTP request must not reread/decrypt the same secure-session snapshot solely to derive an in-process summary.

---

## 5. Target complexity contracts

| Operation | Current unwanted dependency | Target dominant dependency |
|---|---|---|
| Search page | historical `N`, metadata `B`, redirect `R`, backlog `Q` | current `M`, selective `K`, compact evidence delta |
| Home refresh | all catalog evidence / unrelated Search writes | Home membership + refreshed delta |
| Resolve one Story | full `R` | redirect-chain point lookups |
| Resolve Story set | `V × R` | bulk frontier/chunked chain resolution |
| Projection find | global `C` | one resolved Story projection |
| Projection observe set | global `C` / per-Story full redirect observers | bounded IDs + coherent projection read |
| Library render | global canonical/mapping/progress | Library `L` + demanded dependencies |
| Story resume target | all progress `G` | one Story's progress |
| Progress cache protection | all/sorted `G` | distinct incomplete release IDs |
| Canonical fusion | repeated source hydration/hash + repeated point reads | one coherent fusion input + thin currentness token |
| Search canonical settlement | serial `V` | bounded-concurrency `V` |
| Foreground canonical trigger | historical outbox `Q` | current evidence changes only |
| Reader viewport planning | `SUM(AC) + SUM(RA)` per planning update | in-memory incremental ledger + viewport working set |
| Cache eviction | global candidates + global reread per victim | one candidate page/snapshot + point currentness per victim |
| Reader image handoff | repeated `Y` copies | one owned payload + streaming/read-only views |
| Chapter page sync | cumulative prefix re-aggregation | page delta + incremental aggregation session |
| Chapter commit | cumulative relink + full before/after graph | mutation delta + minimal notification evidence |
| Periodic Chapter continuation | full `Lb` scan/sort each batch | indexed keyset next batch |
| Plugin operation discovery | candidate `main.js` bytes + package load | immutable manifest metadata only; script loaded on invoke |
| Bundled provisioning | all bundled `.osp` bytes before version/state decision | descriptors/state first; bytes only for missing/update-needed plugin |
| Stable plugin terminal state | repeat provision/package work per public runtime call | identity/state-bound terminal outcome; transient retry remains possible |
| Plugin credential injection | all plugin manifests + duplicate session reads + repeated per-record key lookup | point policy + one secure snapshot + one key acquisition per store operation |
| Hidden retained destination | potentially active semantic demand | approximately zero upstream semantic work when inactive, if risk is confirmed |

---

# 6. Identity & Projection Access Plane

## 6.1 Story identity resolution

### Existing problem

`RoomStoryIdentityResolver.resolve()` and `observeResolved()` materialize the complete redirect table. The default bounded-set helper combines one full-table observer per Story. Current merge-reversal planning also materializes all redirects to answer the bounded question “does this survivor have another incoming redirect besides the retired Story being reversed?”.

### Target API

Extend `StoryIdentityRepository` with an explicit bounded observation contract:

```kotlin
interface StoryIdentityRepository {
    fun observeResolved(storyId: StoryId): Flow<StoryId>
    fun observeResolvedSet(storyIds: Set<StoryId>): Flow<Set<StoryId>>
    suspend fun resolve(storyId: StoryId): StoryId
    suspend fun resolveAll(storyIds: Collection<StoryId>): Map<StoryId, StoryId>
    suspend fun identityState(storyId: StoryId): CanonicalIdentityState?
}
```

The default implementation may remain simple for test/in-memory repositories. `RoomStoryIdentityResolver` must override both bounded methods.

### Room strategy

- Point resolution follows redirect chains through indexed `retired_story_id` point queries inside one Room transaction.
- Bulk resolution works in frontier rounds:
  1. start with unresolved current IDs;
  2. query redirects for the current frontier in bounded chunks;
  3. advance only IDs that redirect;
  4. detect cycles with per-origin visited sets;
  5. stop when no frontier members redirect.
- Use an explicit chunk constant in `storage:room`, covered by cardinality tests.
- Observation treats redirect-table invalidation as a **trigger**, then performs one coherent bulk resolution, instead of creating `V` independent full-table flows.
- Bounded lineage checks use indexed `EXISTS`/`LIMIT 1` DAO queries such as `hasIncomingRedirectOtherThan(canonicalStoryId, excludedRetiredStoryId)`; merge/reversal code may not call the all-redirect materializer for a point decision.

### Contract

Point complexity is proportional to redirect-chain length, not total `R`. Bulk complexity is proportional to relevant frontier work and bounded query chunks, not `V × R`. Any bounded redirect predicate is proportional to the indexed predicate result, not total redirect history.

### Root causes closed

A6, A7; foundation for A8, X2, X4.

---

## 6.2 Projection-specific read model

### Existing problem

`CatalogStoryProjectionRepository.find()` defaults to `observe().first().find`, so one Story can require global canonical materialization. Full canonical state also hydrates source evidence that the projection does not use.

### Target

`RoomCatalogStoryProjectionRepository` becomes a true Room projection repository rather than a wrapper around global canonical observation.

Add projection-specific DAO queries that join only the tables/columns needed to create `CatalogStoryProjection`:

- Story identity/title/content type;
- active fused generation/presentation fields;
- canonical health/readiness fields required by the model.

Do **not** hydrate source evidence or compute source fingerprints for projection reads.

Target methods:

```kotlin
suspend fun find(storyId: StoryId): CatalogStoryProjection?
fun observeForStories(storyIds: Set<StoryId>): Flow<List<CatalogStoryProjection>>
fun observe(): Flow<List<CatalogStoryProjection>> // maintenance/global consumers only
```

Bounded observation uses Room invalidation as a trigger and then one coherent transactional bounded projection read.

### Root causes closed

A5, X3 projection branch, part of X4; enables A8 repairs.

---

## 6.3 Query-specific reading-progress projection

Add a repository API for cache protection:

```kotlin
fun observeIncompleteReleaseIds(): Flow<Set<ChapterReleaseId>>
```

Room implementation:

```sql
SELECT DISTINCT chapter_release_id
FROM reading_progress
WHERE completed_at_epoch_millis IS NULL
  AND chapter_release_id IS NOT NULL
ORDER BY chapter_release_id
```

Add an index aligned with the predicate/projection, for example:

```text
(completed_at_epoch_millis, chapter_release_id)
```

`AutomaticCachePolicyCoordinator` consumes this projection directly. It must not materialize/sort all progress rows before reducing them.

### Root causes closed

X2; reduces one A8-affected global observation.

---

# 7. Catalog Evidence Plane

## 7.1 Separate compact identity/reconciliation evidence from wide source presentation

### Existing problem

Foreground ingest rebuilds reconciliation from `CatalogSourceRecord`, which contains wide metadata and eagerly computed identity/fusion fingerprints. Search evidence also grows durable catalog state over time.

### Target data model

Introduce a compact durable reconciliation representation whose lifetime is independent from wide presentation metadata.

Logical model:

```kotlin
data class CatalogReconciliationEvidence(
    val sourceKey: CatalogSourceKey,
    val currentStoryId: StoryId,
    val contentType: ContentType,
    val comparisonTitles: Set<String>,
    val comparisonAuthors: Set<String>,
    val identifiers: Set<CatalogIdentifier>,
    val lineageTokens: Set<String>,
    val identityEvidenceFingerprint: String,
    val provenance: CatalogEvidenceProvenance,
    val lastSeenAtEpochMillis: Long,
)
```

Persist evidence and searchable postings separately from `catalog_entries` presentation payload. Because both evidence and postings carry current Story ownership, every catalog ownership mutation must update these rows in the same Room transaction as the source-entry mutation. Forward Story merge reassigns all retired-source evidence/postings to the survivor; controlled reversal reassigns exactly the restored historical source keys back to the retired Story with expected-owner checks.

Proposed Room tables:

- `catalog_reconciliation_evidence`
- `catalog_reconciliation_terms`
- `performance_backfill_state` (or a catalog-specific equivalent)

The exact serialization of comparison sets may use Room converters, but candidate lookup must not decode wide descriptions/genres/URLs/fusion metadata.

`catalog_reconciliation_terms` contains normalized lookup rows such as:

```text
source_plugin_id
source_id
term_kind  // IDENTIFIER, TITLE_TOKEN, AUTHOR, LINEAGE
term_value
strength
story_id
```

with indexes supporting `(term_kind, term_value)` and source/story maintenance.

### Candidate lookup

Add a runtime boundary:

```kotlin
interface CatalogReconciliationEvidenceRepository {
    suspend fun evidenceForSources(keys: Collection<CatalogSourceKey>): List<ReconciliationEvidence>
    suspend fun candidatesFor(incoming: ReconciliationEvidence): List<ReconciliationEvidence>
    suspend fun upsert(evidence: Collection<ReconciliationEvidence>)
    suspend fun deletePresentationForPrunableSources(...)
}
```

Candidate lookup uses postings to retrieve only candidate source/story evidence, then preserves the existing pure ranking/reconciliation engine.

### Root causes closed

A1, X3 ingest branch; provides safe basis for D1.

---

## 7.2 Request-local ingest session instead of global `fork()`

Replace `CatalogIngestReconciliationIndex.fork()` on runtime paths with a request-scoped overlay:

```kotlin
interface CatalogIngestSession {
    suspend fun reconcile(incoming: CatalogEntry): CatalogIngestDecision
    fun beginProvider(): CatalogProviderIngestSession
}

interface CatalogProviderIngestSession {
    suspend fun reconcile(incoming: CatalogEntry): CatalogIngestDecision
    fun recordResolved(evidence: ReconciliationEvidence)
    fun commitIntoParent()
    fun discard()
}
```

Semantics:

- durable compact evidence is immutable base for the request;
- parent overlay contains successfully committed current-request evidence;
- provider child contains provider-local delta;
- failed provider commit discards only its child overlay;
- successful provider commit merges child into parent;
- no provider copies/rebuilds historical `N`.

### Root causes closed

A2 and a major multiplier of A1.

---

## 7.3 Story-ID allocator with point reservation

Replace per-new-Story reconstruction of all existing Story IDs with a point-check allocator:

```kotlin
interface CatalogStoryIdAllocator {
    suspend fun allocate(incoming: ReconciliationEvidence): StoryId
}
```

The allocator:

1. computes the deterministic base ID using existing semantics;
2. checks point existence/reservation;
3. tries deterministic suffixes only on collision;
4. tracks request-local reservations in memory;
5. persists final Story insertion under the same commit transaction that consumes the allocation.

No full Story-ID set is materialized per allocation.

### Root causes closed

A3.

---

## 7.4 Batched catalog commit

`RoomCatalogRepository.commitSearchSummaries()` and related ingest commit paths must group rows by operation and use batched DAO APIs for:

- new Stories;
- canonical state initialization;
- catalog entries;
- identifiers;
- compact reconciliation evidence/postings;
- outbox/change evidence.

This remains `O(M)` row work but removes statement/API amplification.

### Root causes closed

L7.

---

## 7.5 Evidence lifetime and compaction

### Rule

**Identity evidence is durable; wide presentation state is conditionally compactable.**

Search-origin metadata must not be TTL-deleted blindly.

Define at least these provenance/reachability classes:

- Home/recent surface referenced;
- Library/user-state referenced;
- active Content Mapping referenced;
- currently open/Reader referenced;
- download/progress/history protected where identity continuity requires it;
- search-discovered only;
- compact identity-only retained.

A maintenance compactor may remove **wide presentation/source payload** for unreachable search-only records while retaining compact reconciliation evidence and identity linkage.

Compaction is background maintenance and never runs synchronously on a foreground Search.

### Root causes closed

D1 while preserving canonical continuity.

---

# 8. Canonical Work Plane

## 8.1 Coherent fusion input

Replace fragmented repeated repository calls during one fusion rebuild with one coherent input:

```kotlin
data class CanonicalFusionInput(
    val storyId: StoryId,
    val identityRevision: Long,
    val canonicalStateRevision: Long,
    val sources: List<CatalogSourceRecord>,
    val sourcePreference: CatalogSourcePreference,
    val activeGeneration: CanonicalGeneration?,
)
```

The Room repository reads it in one transaction. Fingerprints needed repeatedly should come from persisted/compact evidence when semantically equivalent rather than being recomputed from wide metadata on each caller path.

Promotion uses a thin expectation token:

```kotlin
data class CanonicalPromotionExpectation(
    val storyId: StoryId,
    val identityRevision: Long,
    val canonicalStateRevision: Long,
    val sourceEvidenceRevision: Long,
)
```

Currentness validation checks these revisions/keys, not another full source hydration.

### Root causes closed

L6, X3 canonical branch; supports X4.

---

## 8.2 Coherent reactive observation

Canonical reactive APIs use:

```text
Room invalidation trigger
    -> one resolved transactional read
    -> distinct semantic state
```

instead of combining independently emitted table snapshots and then re-reading them.

Projection observers remain separate from full canonical-state observers so UI consumers do not hydrate fusion inputs.

### Root causes closed

X4.

---

## 8.3 Unified durable work claim/join

The durable work queue remains the source of truth. Foreground code and workers must use one execution API:

```kotlin
interface CanonicalWorkExecutor {
    suspend fun executeOrJoin(key: CanonicalWorkKey): CanonicalWorkExecutionResult
    suspend fun executeReady(limit: Int): CanonicalWorkDrainResult
}
```

Rules:

- foreground never executes expensive canonical work without a durable claim;
- worker and foreground use the same lease/claim transition;
- in-process `CanonicalWorkSingleFlight` joins duplicate calls by `CanonicalWorkKey`;
- when another owner already holds the lease, foreground joins/observes completion rather than executing a duplicate;
- dirtied-while-leased semantics remain durable and deterministic;
- stale completion guards remain in place as correctness defense, not as duplicate-work coordination.

### Root causes closed

X5.

---

## 8.4 Separate interactive evidence from recovery backlog

`CanonicalEngineOrchestrator.onEvidenceChanges()` marks only current-request work dirty/runnable and executes/joins those keys.

It does **not** materialize an arbitrary historical outbox backlog before interactive settlement.

Historical outbox materialization belongs to a bounded recovery/worker lane with explicit batch budget.

### Root causes closed

X6.

---

## 8.5 Coalesce final fusion per Story

Per-source reconciliation can still process every semantically necessary evidence change. Final fusion rebuild is coalesced by resolved Story/work key so one logical batch does not rebuild the same Story repeatedly unless a new revision arrives after execution has begun.

### Root causes closed

L5.

---

## 8.6 Bulk reconciliation APIs

Reconciliation paths that currently loop over candidates/sources through point repository APIs receive explicit bulk methods:

```kotlin
suspend fun loadEvidenceForStories(storyIds: Set<StoryId>): Map<StoryId, List<ReconciliationEvidence>>
suspend fun resolveCandidates(keys: Set<StoryId>): Map<StoryId, StoryId>
```

Reevaluation of a Story with `S` sources loads its evidence once, then evaluates in-memory/bulk rather than outer `S` × inner `S` reloads.

### Root causes closed

X7.

---

## 8.7 Foreground canonical settlement concurrency and Discover single-flight

- Search canonical settlement uses the repository-native bounded concurrency vocabulary already used elsewhere; default target limit is **4**, adjustable only through a central domain policy, not ad hoc per call site.
- Discover automatic bootstrap and manual refresh share one refresh owner/single-flight. Manual refresh joins or explicitly supersedes under one contract; it does not launch an independent duplicate pipeline.

### Root causes closed

L3, L4.

---

# 9. Reactive Storage & UI Demand Plane

## 9.1 Home semantic invalidation projection

Search and Home may continue to share canonical/source identity state, but Home observation must not depend on unrelated `catalog_entries` writes.

Introduce a Home-specific materialized read projection, for example `catalog_home_entries`, updated transactionally by Home refresh/membership changes and by semantically relevant updates to sources currently referenced by Home.

`observeHomes()` then observes only Home-domain tables and performs one coherent read.

### Root cause closed

X1 Catalog branch.

---

## 9.2 Split explicit download metadata from automatic cache metadata

`chapter_storage_entries` currently contains two semantic namespaces whose writes invalidate one another at Room table granularity.

For the Big Update, prefer a clean schema split:

- `explicit_download_entries`
- `automatic_chapter_cache_entries`

Repositories become narrower:

- explicit download observation/write APIs use the explicit table;
- automatic cache accounting/eviction uses the automatic table;
- reconciliation/maintenance may union the two domains explicitly when required;
- physical blob storage remains shared where appropriate.

### Root cause closed

X1 storage branch and simplifies X9.

---

## 9.3 Bounded and demand-driven UI subscriptions

### Library

Derive Library Story IDs from membership first. Subscribe only to bounded projections required by current presentation controls.

`LibraryLocalDependencyPolicy` must control **subscription demand**, not only readiness semantics.

Examples:

- progress observer active only when current sort/filter/render actually requires it;
- mappings observer active only when mapping/source filter requires it;
- canonical projection uses `observeForStories(libraryStoryIds)`.

### Story

Use one-Story Library membership and progress/resume queries rather than `library.observe()/progress.observeAll()` + local filtering. Library membership observation is a point Flow keyed by resolved Story ID and preserves redirect resolution semantics.

For Story chapter download controls, derive the current release-ID set from `ChapterListContent.releaseTargets`, stabilize it with `distinctUntilChanged()`, and observe download states only for those IDs. Do not build a process-wide download-status map for one Story.

### Downloads

Derive relevant Story/release IDs from explicit download records, then request bounded Chapter/projection metadata. The standalone Downloads screen may observe the explicit-download domain globally because that screen is semantically global; its Chapter/catalog enrichment must remain bounded to those explicit release IDs.

### Settings storage summary

Use aggregate count/bytes queries instead of observing whole download/asset lists only to sum them.

### Root causes closed

A8, L8.

---

## 9.4 CPU execution boundary

Pure reducers/projectors whose cost can grow with bounded data sets execute on `AppDispatchers.default` before state publication.

The boundary is applied to Search and affected Library/Downloads/Home/Updates reductions **after or together with data-scope repair**. It is not used to hide a global-scope algorithm.

### Root cause closed

L1.

---

## 9.5 Retained navigation risk gate

Do not remove `PersistentTopLevelNavDisplay`.

First instrument/benchmark:

- subscriber count per retained destination;
- Room query executions while inactive;
- reducer executions while inactive;
- warm navigation frame time;
- RSS after repeated tab cycling.

If inactive destinations remain semantically active, introduce an **active-destination collection gate** that preserves:

- destination composition;
- `ViewModelStore`;
- saveable state;
- navigation stacks;

while suspending expensive upstream demand for inactive tabs.

This is a risk-gated change, not a guaranteed implementation task.

---

# 10. Plugin Runtime Control Plane

The plugin runtime gets an explicit separation between immutable control-plane metadata, executable/package payload loading, retry-state ownership, and security-sensitive request data. This wave deliberately does **not** change JavaScript isolate lifetime; isolate reuse remains benchmark-gated after the cheaper structural waste is removed.

## 10.1 Shared immutable manifest resolver

Introduce one process-scoped runtime component keyed by immutable active package identity:

```kotlin
data class PluginPackageIdentity(
    val pluginId: PluginId,
    val version: String,
    val packageLocation: String,
    val sha256: String,
)

interface InstalledPluginManifestResolver {
    suspend fun resolve(stored: StoredPluginState): PluginCallResult<PluginManifest>
}
```

Target properties:

- reads only `manifest.json`;
- caches success by immutable `PluginPackageIdentity` and replaces the prior identity for the same plugin;
- single-flights concurrent reads for the same identity;
- preserves the original `PluginCallResult.Failure`/retryability classification rather than converting every storage failure into one synthetic terminal code;
- a terminal/nonretryable manifest failure may be retained only by immutable identity; retryable failures are not permanently cached.

`DefaultPluginRuntime` and `InstalledPackageAuthenticationPolicySource` share this resolver instead of independently decoding the same package manifest.

### Root causes closed

Part of X16/X17.

---

## 10.2 Manifest-only operation discovery and lazy executable loading

`DefaultPluginRuntime.enabled(operation)` becomes:

```text
ensureProvisioned()
  -> state.all()
  -> service/enabled filter
  -> manifestResolver.resolve(stored)
  -> manifest.supports(operation)
```

It must not read or decode `main.js`.

Invocation becomes a two-stage load:

```text
manifestResolver.resolve(stored)
  -> supports(operation)
  -> executableSourceResolver.resolve(stored)   # reads main.js only now
  -> runner.run(...)
```

The executable cache remains keyed by immutable package identity and bounded by installed plugin cardinality. Manifest and executable failure state are not conflated.

### Root causes closed

X16 plus the package-load portion of X17.

---

## 10.3 Descriptor-first bundled provisioning with failure-aware outcome ownership

Replace the source contract that eagerly returns all package bytes:

```kotlin
interface BundledPluginSource {
    suspend fun descriptors(): List<BundledPluginDescriptor>
    suspend fun loadPackage(descriptor: BundledPluginDescriptor): PluginCallResult<BundledPluginPackage>
}
```

Provisioning evaluates lightweight descriptor + current installed state first:

```text
descriptor
  -> state.find(pluginId)
  -> already current/newer? SATISFIED without reading .osp
  -> missing/update-needed? load exactly this .osp and verify/apply
```

Maintain a small per-bundled-plugin outcome state keyed by:

```text
bundled descriptor identity (pluginId/version/sha256/assetPath)
+ relevant installed active-version/state fingerprint
```

Outcomes:

```kotlin
sealed interface ProvisionOutcome {
    data object Satisfied : ProvisionOutcome
    data class Terminal(val failure: PluginCallResult.Failure) : ProvisionOutcome
    data object Retryable : ProvisionOutcome
}
```

The exact implementation may use a more compact internal representation, but semantics are fixed:

- `Satisfied` and terminal/user-action-required outcome are stable while the key is unchanged;
- `NEEDS_REVIEW` is terminal for automatic provisioning, never auto-approved;
- retryable/transient failure is not permanently memoized and remains single-flight/bounded-retry;
- owner cancellation cannot leave a stale in-flight marker;
- installed-state or descriptor identity change forces reevaluation;
- one terminal plugin does not cause unrelated peers to reprovision repeatedly.

### Root cause closed

X17 and bundled-payload part of X16.

---

## 10.4 Point authentication policy access

Split point request-time access from explicit maintenance enumeration:

```kotlin
interface InstalledAuthenticationPolicySource {
    suspend fun find(pluginId: PluginId): InstalledAuthenticationPolicy?
    suspend fun all(): List<InstalledAuthenticationPolicy>
}
```

`find(pluginId)`:

1. `state.find(pluginId)`;
2. return null/disabled as contract requires;
3. resolve only that package manifest through the shared manifest resolver;
4. extract authentication capability.

`all()` remains available for Settings/login-policy invalidation workflows where all installed policies are the semantic working set. It may use `state.all()` and the same manifest resolver.

`DefaultPluginSessionService.sessionFor(request)` and `policy(pluginId)` use `find(pluginId)` only.

### Root cause closed

X16.

---

## 10.5 One secure-session snapshot per authenticated request

Refactor request credential selection so summary publication is derived from the exact records already read:

```kotlin
private suspend fun validSessionRecords(
    request: ManagedCredentialRequest,
    policy: InstalledAuthenticationPolicy,
): List<PluginSessionRecord> {
    val records = store.readAll(request.pluginId)
    val valid = filter(records, request, policy)
    publish(summaryForRecords(request.pluginId, policy, records, generation))
    return valid
}
```

The summary uses the **complete one-read snapshot**, not only the filtered records, preserving current session-status meaning while removing the second `readAll()`.

`AndroidKeystorePluginSessionStore` obtains one `SecretKey` per high-level `readAll()`/`replaceAll()` operation and passes it into every encrypt/decrypt call for that operation:

```text
readAll(pluginId)
  -> key() once
  -> decrypt(record1, key)
  -> decrypt(record2, key)
  -> ...
```

No long-lived plaintext-cookie cache is introduced in this confirmed-fix wave.

### Root cause closed

X18.

---

## 10.6 Security/performance risk gates after structural repair

Only after Sections 10.1–10.5 land:

- benchmark one-snapshot secure request construction to decide `RISK-PLUGIN-AUTH-CACHE`;
- benchmark isolate create/evaluate/close separately to decide `RISK-PLUGIN-ISOLATE`;
- benchmark provider concurrency under the cross-capability workload to inform `RISK-GLOBAL-RESOURCE`.

This ordering prevents isolate/security-cache decisions from being contaminated by unrelated control-plane/package/session duplicate work.

---

# 11. Reader & Automatic-Cache Plane

## 11.1 Incremental cache ledger

`AutomaticCacheBudgetCoordinator.snapshot()` must not recompute committed usage on every viewport plan.

Target state:

```kotlin
private var committedBytes: Long
private val pendingReservations: MutableMap<Long, Long>
```

is authoritative in-process after one initialization/recovery read.

Rules:

- initialization computes automatic-document + Reader-asset usage once;
- every successful publish/detach adjusts the ledger exactly once;
- reservations contribute only to accounted bytes, not committed bytes;
- `snapshot()` reads ledger state only;
- reconciliation may perform an occasional explicit repair/recompute outside the foreground hot path and asserts/diagnoses drift.

### Root cause closed

X8.

---

## 11.2 One candidate page + point currentness eviction

Replace `entries().filter(AUTOMATIC)` and per-victim global rereads with repository APIs such as:

```kotlin
suspend fun automaticEvictionCandidates(limit: Int): List<AutomaticDocumentCandidate>
suspend fun detachAutomaticIfCurrent(expected: AutomaticDocumentCandidate): AutomaticDocumentCandidate?
```

Reader asset metadata repository gets equivalent bounded candidate and point-detach operations.

A reconciliation pass:

1. reads one bounded candidate page/snapshot;
2. chooses victims using current retention rules;
3. atomically detaches each expected victim by key/generation/currentness;
4. updates the ledger;
5. deletes physical blobs after metadata detachment and outside state locks.

### Root cause closed

X9.

---

## 11.3 Split state lock from publication ordering

Replace the single broad `publicationGate` role with two concerns:

### `stateGate`

Protects only in-memory mutable quota state:

- quota;
- epochs;
- reservations;
- committed bytes;
- active protection snapshot.

No filesystem I/O, global Room reads, or blob deletion is allowed while holding `stateGate`.

### `publicationOrderGate`

Used only where security/invalidation ordering must linearize metadata publication against invalidation. It may cover the minimal atomic metadata operation required by correctness, but must not gate `cachePressure()`, reservation admission, or unrelated foreground reads.

Physical blob deletion always happens after logical detachment and outside both locks.

### Root cause closed

X10.

---

## 11.4 Bounded encoded-payload ownership

Preserve one defensive ownership boundary but stop exposing production code to repeated `copyOf()` for the same encoded page.

Target abstraction:

```kotlin
interface ReaderAssetEncodedSource {
    val byteSize: Long
    fun openStream(): InputStream
    suspend fun copyTo(output: OutputStream)
}
```

`ReaderAssetPayload` may implement/own this source. Production consumers use `openStream()`/`copyTo()`; a defensive `bytes()` helper may remain test-only or compatibility-only until removed.

`ReaderAssetBlobStore.writeAtomic` accepts a bounded source/stream plus declared size/checksum rather than requiring another `ByteArray` copy.

Coil fetcher reads the payload as a source without calling `bytes()`.

The same principle should remove avoidable Chapter document buffer duplication where it can be done without changing blob integrity semantics.

### Root cause closed

X11.

---

## 11.5 Bounded operational memoization

`DownloadReaderAssetStore.lastAccessTouches` becomes a bounded LRU/TTL memoization keyed by asset hash. `CatalogMetadataCoordinator.suppressions` also receives explicit bounded retention: expired cooldowns are pruned without requiring same-key revisit, total entries have a hard cap with deterministic eviction, and plugin-version suppression invalidation remains tied to plugin-version/current-key semantics. `inFlight` remains unchanged because completion already removes entries.

Required behavior:

- remove entry when metadata is detached/invalidated where the event is available;
- expire old entries after a defined touch-throttle horizon;
- hard-cap entries to prevent process-lifetime growth;
- preserve the existing write-throttle semantics.

The same rule applies to any process-lifetime suppression/memoization map discovered during implementation: every such map must declare owner, maximum cardinality or TTL, and invalidation path.

### Root cause closed

X12.

---

# 12. Chapter Delta & Scheduling Plane

## 12.1 Incremental aggregation session

Replace page-prefix full recomputation with a sync-local session:

```kotlin
interface ChapterAggregationSession {
    fun applyPage(releases: List<ChapterRelease>, pageKind: ChapterPageKind): ChapterMutationDelta
    fun finish(authoritativeSourceComplete: Boolean): ChapterMutationDelta
}
```

Initialization reads the current resolved Chapter graph once.

Each page:

- normalizes only new/changed releases;
- updates in-memory indexes incrementally;
- emits only creates/changed links/restores/tombstones/source-state changes;
- does not rerun aggregation over the entire cumulative release prefix.

For an authoritative FULL source sync, `finish()` computes removals against the complete source release-ID set gathered by the session.

A differential/property test must prove that final state is equivalent to the existing full `ChapterAggregationEngine.plan()` across page splits/orderings supported by the source contract.

### Root cause closed

X13.

---

## 12.2 Delta Chapter commit

Evolve `ChapterMutation` into/alongside a mutation delta carrying only changed writes:

```kotlin
data class ChapterMutationDelta(
    val creates: List<CanonicalChapter>,
    val releaseUpserts: List<ChapterRelease>,
    val linkChanges: List<ChapterReleaseLink>,
    val unlinks: List<ChapterReleaseId>,
    val restores: List<CanonicalChapterId>,
    val tombstones: List<CanonicalChapterId>,
    val syncState: ChapterSyncStateMutation?,
    val commitFingerprint: String,
)
```

`RoomChapterRepository.commit()` applies bulk delta writes in one transaction.

Notification evidence derives from the semantic delta rather than reading a full resolved graph before and after every page. Regression tests compare event keys/order with current behavior.

### Root cause closed

X14.

---

## 12.3 Materialized periodic Chapter schedule + keyset pagination

A SQL view over current Library/mapping/sync tables would still require grouping/scanning on each continuation, so use a materialized scheduling projection.

New logical table:

```text
chapter_sync_schedule
story_id PRIMARY KEY
eligible
last_successful_sync_at_epoch_millis
schedule_revision
```

Maintain it when:

- Library membership changes;
- Story identity merges/redirects affect membership;
- Chapter sync success/failure state changes in a way relevant to scheduling.

Expose:

```kotlin
sealed interface ChapterSyncCandidateRead {
    data class Ready(val batch: ChapterSyncBatch) : ChapterSyncCandidateRead
    data object BackfillPending : ChapterSyncCandidateRead
}

fun interface ChapterSyncCandidateSource {
    suspend fun nextBatch(
        cursor: ChapterSyncBatchCursor?,
        limit: Int,
    ): ChapterSyncCandidateRead
}
```

Room performs indexed keyset ordering and `LIMIT batchSize + 1`; worker no longer materializes/sorts all candidates. On a v12→v13 upgrade, the schedule table is created structurally empty and a bounded resumable worker populates it. Until the durable schedule-backfill completion bit is set, candidate reads return `BackfillPending` rather than treating an incomplete projection as an empty candidate set or falling back to a full Library scan.

### Root cause closed

X15.

---

# 13. Schema and migration strategy

Current Room schema version is 12.

## 13.1 Versioning decision

If the entire Big Update lands before the next released schema, consolidate new structural schema in **Room version 13 / `MIGRATION_12_13`**.

If any wave ships independently to users, stop and split schema versions per shipped wave. Do not mutate an already released migration contract.

## 13.2 Version-13 structural additions

Expected additions include:

- compact catalog reconciliation evidence/postings;
- backfill-state metadata;
- Home materialized projection if selected implementation needs it;
- progress predicate/projection index;
- explicit-download vs automatic-cache metadata split;
- Chapter sync schedule/indexes;
- any DAO indexes required by new bounded queries.

## 13.3 Migration budget

`MIGRATION_12_13` performs only bounded/simple structural SQL and direct row copies that do not require expensive normalization/hashing.

Expensive derived-data work such as:

- catalog term normalization/posting generation;
- compact evidence fingerprint backfill;
- Chapter schedule derivation on a large Library;

runs in resumable bounded background backfill after DB open.

## 13.4 Compatibility during backfill

- Catalog reconciliation may use its explicitly bounded legacy evidence fallback for source keys not yet indexed. Chapter periodic dispatch does not fall back to a global Library scan: it returns `BackfillPending` and retries after bounded schedule backfill advances.
- Foreground code must never synchronously complete the entire historical backfill.
- Backfill state is durable and resumable.
- Benchmarks include an upgraded aged database in both incomplete and completed backfill states.
- Cleanup may remove superseded runtime paths only when the upgrade contract remains valid for a fresh v12→v13 upgrader; durable migration/backfill support itself is never deleted merely because one test database has completed backfill.

---

# 14. Performance Contracts & Risk Gates

## 14.1 Benchmark dimensions

Required scaling axes:

```text
N  = 30 / 300 / 3,000 / 30,000
B  = narrow / medium / wide catalog metadata
R  = 0 / 100 / 1,000 / 10,000
G  = 30 / 300 / 3,000 / 30,000 progress rows
Q  = 0 / 100 / 1,000 / recovery-scale backlog
RA = Reader asset metadata cardinality sweep
AC = automatic document metadata cardinality sweep
Y  = small / medium / near-limit encoded page bytes
J/Z/Pg = small / medium / large Chapter graph/page counts
Lb = small / hundreds / thousands Library schedule rows
H  = 1 / 2 / 3+ retained destinations
V  = small / hundreds / thousands requested bounded Story sets
L  = 10 / 1,000 / 10,000 Library rows
D  = 10 / 1,000 / 10,000 explicit download records
P  = 1 / 2 / 4 / 8 providers
K  = low / medium / adversarial candidate collision
Pl = 1 / 4 / 16 installed plugins
JsB = small / medium / large aggregate plugin script bytes
Bp = small / medium / large bundled package bytes
Cr = 0 / 1 / 4 credential records
Hr = 1 / 10 / 100 authenticated request builds
```

## 14.2 Measure slope, not only milliseconds

Every structural benchmark records ratios as the unrelated historical dimension grows. A point/bounded operation violates the contract when its latency/query count/allocation grows materially with a dimension it should not need, even if absolute time remains acceptable on one high-end device.

## 14.3 Risk promotion gates

### RISK-A4 — candidate fan-out

Promote only if collision corpus shows candidate work dominates or grows near `M × N`. If promoted, design selectivity/caps without weakening identity quality.

### RISK-BIND — Story-ID cardinality

Bounded API implementation must chunk. Promote to defect only if real query-plan/bind tests show a remaining cliff beyond chunking.

### RISK-LIFECYCLE

Promote only if inactive retained destinations produce material upstream Room/Flow/reducer work.

### RISK-GLOBAL-RESOURCE

Do not build a global arbiter unless traces show simultaneous Reader/Downloads/Chapter/plugin work materially harms user-critical latency. If confirmed, design a neutral capacity contract in a separate spec.

### RISK-PLUGIN-ISOLATE

Pool/reuse isolates only if invocation benchmarks show fresh isolate setup is material **after X16–X18 close** and manifest/package/session duplicate work has been removed from the measured path. Security/isolation semantics get a separate threat review before reuse.

### RISK-PLUGIN-AUTH-CACHE

Do not retain decrypted credentials merely because secure storage is present on the request path. First close X16/X18. Promote only if real-device `Cr/Hr` measurements show the remaining single secure snapshot materially dominates request/provider latency. Any cache requires generation binding, short TTL/cap, logout/policy-change invalidation and threat review.

### RISK-STARTUP-CONTENTION

Promote only if aged/upgraded cold-start traces show app-scope policies/backfills materially contend with first-screen work.

---

# 15. Root-cause traceability — 33/33

| Root cause | Target architecture | Primary wave |
|---|---|---:|
| A1 | compact evidence + indexed candidate lookup | 2 |
| A2 | request/provider delta ingest session | 2 |
| A3 | point/reservation Story-ID allocator | 2 |
| A5 | true point/bounded projection repository | 1 |
| A6 | point/frontier redirect resolver + indexed bounded redirect predicates | 1 |
| A7 | bulk/frontier identity resolver + one observer | 1 |
| A8 | bounded/demand-driven UI reads, including one-Story membership/progress and current-release download status | 7 |
| L1 | explicit Default CPU projection boundary | 7 |
| L3 | Discover shared refresh single-flight | 3 |
| L4 | bounded concurrent Search settlement | 3 |
| L5 | coalesced final fusion by Story/work key | 3 |
| L6 | coherent fusion input + thin currentness token | 3 |
| L7 | batched catalog commit | 2 |
| L8 | subscription demand follows Library dependency policy | 7 |
| D1 | durable compact evidence + prunable wide metadata | 2 |
| X1 | Home projection + explicit/automatic storage split | 1/5 |
| X2 | incomplete-release-ID query projection | 1 |
| X3 | compact evidence + projection/fusion-specific read models | 1/2/3 |
| X4 | invalidation-triggered coherent canonical/projection reads | 1/3 |
| X5 | unified durable claim/join executor | 3 |
| X6 | historical recovery removed from interactive path | 3 |
| X7 | bulk reconciliation evidence/identity APIs | 3 |
| X8 | incremental cache ledger | 5 |
| X9 | bounded candidates + point detach | 5 |
| X10 | short state gate + separate publication ordering | 5 |
| X11 | streaming/owned encoded-source contract | 5 |
| X12 | bounded Reader touch + Catalog metadata suppression memoization | 5 |
| X13 | incremental Chapter aggregation session | 6 |
| X14 | delta Room Chapter commit + delta notifications | 6 |
| X15 | materialized schedule + keyset batch source | 6 |
| X16 | shared manifest resolver + manifest-only discovery + descriptor-first provisioning + point auth policy | 4 |
| X17 | failure-aware provisioning/package outcome ownership keyed to immutable identity/state | 4 |
| X18 | one secure-session snapshot/request + one Keystore key acquisition/store operation | 4 |

All 33 confirmed families are assigned to at least one implementation wave.

---

# 16. Program waves and dependency graph

```text
Wave 0  Performance contracts + fixtures
   |
   v
Wave 1  Bounded identity/projection/reactive storage foundations
   |\
   | \
   v  v
Wave 2  Catalog compact evidence + ingest/lifetime
   |   \
   v    \
Wave 3  Canonical work ownership/fusion/recovery
   |
   +-----------------------+-----------------------+
   |                       |                       |
   v                       v                       v
Wave 4 Plugin control   Wave 5 Reader/cache   Wave 6 Chapters/background
   |                       \                       /
   |                        \                     /
   +-------------------------+-------------------+
                             |
                             v
                    Wave 7 UI demand + execution + lifecycle acceptance
                             |
                             v
                    Wave 8 Risk acceptance + cleanup + final freeze
```

Wave 4 is independent of Room-v13 schema work after Wave-0 measurement contracts and may proceed in parallel with Waves 1–3 if branch integration is controlled. Waves 5 and 6 are independent after shared Room/benchmark foundations and may also be developed in parallel. Wave 7 consumes the bounded repository APIs from Waves 1/5/6; plugin runtime changes do not need to be forced through UI ownership.

---

# 17. Non-regression gates per wave

Every wave must pass:

```bash
bash scripts/verify-package-boundaries.sh
bash scripts/verify-structural-suppressions.sh
bash scripts/verify-room-schema-stability.sh
```

plus affected module tests and architecture checks.

Schema-changing waves additionally run migration instrumentation tests from 12 -> 13 and fresh-create schema validation.

Reader/cache waves additionally run RICC security/invalidation/process-recreation suites.

Canonical waves additionally run reconciliation/fusion/durable-worker tests.

Chapter waves additionally run differential page-split tests, Room Chapter repository tests, periodic dispatch integration tests, and notification contracts.

Plugin control-plane wave additionally runs plugin runtime contract/performance tests, session/security-generation tests, Android Keystore session-store instrumentation, and static checks proving `enabled(operation)` cannot read `main.js` and request-time policy lookup cannot enumerate all installed policies.

UI/lifecycle wave must preserve the existing warm-navigation benchmark baseline before accepting any lifecycle gating change. It also installs a fail-closed production-binding source guard so bounded UI repository APIs cannot silently regress to interface defaults implemented through global observation/materialization.

---

# 18. Self-review / red-team conflict analysis

## Conflict 1 — Fixing L2 by uncomposing hidden tabs would regress P6

**Resolution:** retained composition is preserved. Only semantic collection is risk-gated and measured.

## Conflict 2 — TTL deleting Search rows would break identity continuity

**Resolution:** compact identity evidence has durable lifetime; only wide presentation metadata is eligible for reachability-based compaction.

## Conflict 3 — Persistent reconciliation index could become stale

**Resolution:** evidence/postings are updated transactionally with source commit and carry durable backfill/index state. No unsynchronized process-only global index is source of truth.

## Conflict 4 — Story-ID point allocation can race

**Resolution:** deterministic candidate generation plus DB uniqueness/commit transaction and request-local reservations; collision retries are point operations.

## Conflict 5 — Point redirect chains could cause many SQL calls on pathological chains

**Resolution:** point path is chain-local; bulk path resolves frontiers in chunks. Add redirect-chain depth/cycle tests. Do not return to full `R` materialization as the default.

## Conflict 6 — Projection-specific Room reads could duplicate canonical business logic

**Resolution:** duplicate only read projection mapping, not canonical fusion/reconciliation rules. `CatalogStoryProjection` is explicitly a read model.

## Conflict 7 — Coherent trigger+read observation may emit less often than table-combine flows

**Resolution:** observe every semantically relevant table as invalidation triggers, but publish only one transactional resolved snapshot. Eventual semantic updates are preserved; mixed generations are removed.

## Conflict 8 — Unified canonical claim/join could deadlock foreground on a stalled worker lease

**Resolution:** lease expiry remains authoritative; join waits are bounded/cancellable; foreground can claim after expiry under the same durable transition. Never spin or execute concurrently merely because foreground is waiting.

## Conflict 9 — Coalescing fusion could skip meaningful intermediate evidence

**Resolution:** reconciliation still handles all evidence changes. Only final fusion is coalesced by final dirty revision; dirtied-while-leased semantics schedule another rebuild when revision advances.

## Conflict 10 — Incremental cache ledger can drift after crash or external filesystem change

**Resolution:** ledger initializes from durable metadata and reconciliation can repair/recompute. Durable metadata, not in-memory ledger, remains recovery truth.

## Conflict 11 — Moving blob deletion outside locks can race with new publication

**Resolution:** physical blobs are generation-addressed. Logical metadata detachment/currentness happens atomically first; deletion targets the detached generation and respects existing leases.

## Conflict 12 — Streaming Reader payload might weaken defensive ownership

**Resolution:** payload remains immutable to consumers; expose read-only stream/source from owned bytes or a bounded immutable backing source. No writable backing array is leaked.

## Conflict 13 — Delta Chapter aggregation may diverge from full engine semantics

**Resolution:** retain the old full engine as differential oracle during Wave 6 and require final-state equivalence across generated page splits before retiring prefix recomputation.

## Conflict 14 — Delta notification derivation may miss an event previously detected by before/after snapshots

**Resolution:** run dual computation in tests (and optionally diagnostics-only dev builds) until exact event-key/order equivalence is proven.

## Conflict 15 — Materialized Chapter schedule can become stale

**Resolution:** schedule updates are transactionally coupled to Library/sync/identity operations that change eligibility; add reconciliation/backfill maintenance and invariant tests.

## Conflict 16 — One v13 migration can become too large

**Resolution:** structural migration remains cheap; derived normalization/backfill is resumable post-open. If any wave ships before the program completes, schema versions split immediately.

## Conflict 17 — Bounded UI subscriptions can churn when controls change rapidly

**Resolution:** derive stable ID sets, apply `distinctUntilChanged()` before switching expensive bounded flows, and use `flatMapLatest` only at semantic dependency boundaries.

## Conflict 18 — `Dispatchers.Default` can hide excess work and oversubscribe CPU

**Resolution:** data-scope repairs land first/with the dispatcher boundary. Cross-capability CPU governance remains risk-gated rather than adding an eager global pool.

## Conflict 19 — Backfill can compete with foreground DB work

**Resolution:** bounded batches, persisted cursor/state, WorkManager/background lane, pause/yield budget, and aged-upgrade startup benchmarks. Never backfill full historical state in `Application.onCreate()`.

## Conflict 20 — Splitting storage tables can complicate reconciliation

**Resolution:** explicit and automatic metadata repositories remain semantically separate; maintenance reconciliation owns the explicit union. This is intentional domain separation, not duplicated business state.

## Conflict 21 — Compact reconciliation evidence can become stale across Story merge/reversal

**Resolution:** evidence/posting ownership is part of the same transactional Story/source ownership mutation. Forward merge moves retired-source evidence/postings to the survivor in the merge transaction; controlled reversal moves exactly the audited historical source keys back with expected-owner checks in the reversal transaction. Candidate lookup and Story-based evidence lookup are regression-tested after merge and reversal. If v13 backfill is incomplete, only rows that already exist are moved; missing evidence never blocks the identity mutation, and later bounded backfill derives the current owner from `catalog_entries`.

## Conflict 22 — Hard-capping metadata suppression can cause retries before a plugin version changes

**Resolution:** suppression is operational retry-control state, not canonical/durable truth. Use deterministic age/LRU eviction with an explicit high cap, always prune expired cooldowns first, preserve same-key plugin-version invalidation when retained, and test that eviction can only increase a later retry opportunity—it cannot change stored metadata, Story identity, or single-flight correctness. Record suppression-cardinality/retry behavior in the process-aging gate.

---

## Conflict 23 — Negative-caching plugin failures can turn transient I/O into permanent breakage

**Resolution:** preserve `PluginCallResult.Failure.retryable` from storage/verifier boundaries. Cache only terminal/nonretryable results, bind them to immutable package/descriptor + relevant state identity, and invalidate on identity/state change. Retryable failures and cancellation remain retryable/single-flight.

## Conflict 24 — Memoizing `NEEDS_REVIEW` could hide later user approval/state change

**Resolution:** the terminal provisioning key includes the installed state relevant to capability acceptance. Public runtime calls may perform a cheap `state.find(pluginId)`/descriptor comparison; they must not reread `.osp` bytes until that state/descriptor key changes. Explicit review/update actions also invalidate the outcome. Never auto-approve capability expansion.

## Conflict 25 — A shared manifest cache can become a second source of plugin truth

**Resolution:** active `PluginStateStore` remains authoritative for enabled state and package identity on every public operation. The manifest resolver caches only immutable package content by the active package identity provided by current state; a changed identity misses/replaces the cache.

## Conflict 26 — Reusing one Keystore key handle across records might extend secret lifetime

**Resolution:** reuse is scoped to one `readAll()`/`replaceAll()` operation only. The `SecretKey` handle is not a plaintext credential and is not promoted to a long-lived cookie cache by this wave. Any wider credential cache remains `RISK-PLUGIN-AUTH-CACHE` and needs threat review.

## Conflict 27 — Isolate benchmarks can falsely blame JavaScript startup for control-plane/package/auth work

**Resolution:** close X16–X18 first, then benchmark isolate create/evaluate/close with manifest/package/session work separately counted or warmed. `RISK-PLUGIN-ISOLATE` cannot be promoted from contaminated end-to-end timing alone.

---

# 19. Design freeze criteria

This design may be treated as the target-architecture baseline when all of the following hold:

- [x] all 33 confirmed root-cause families map to a target boundary;
- [x] all 7 risks have evidence gates rather than speculative fixes;
- [x] known non-bugs are protected by explicit non-regression rules;
- [x] data lifetime preserves identity continuity;
- [x] schema migration avoids unbounded startup backfill;
- [x] Reader integrity/security contracts are preserved;
- [x] canonical durable ownership has one source of truth;
- [x] Chapter delta design has a differential-equivalence strategy;
- [x] retained-navigation optimization is not accidentally removed;
- [x] plugin security/capability-expansion semantics are protected while terminal retry amplification is removed;
- [x] secure-session optimization does not require long-lived plaintext credentials;
- [x] waves have an acyclic dependency order and independent acceptance gates.

---

# 20. Final design statement

The Big Performance Update is not a collection of micro-optimizations. It is a scope-and-ownership correction across Hikari:

```text
operation-local work
    -> bounded/compact data
    -> coherent read snapshot
    -> one execution owner
    -> short critical section
    -> bounded allocation ownership
    -> delta/batch persistence
    -> control-plane/data-plane separation
    -> failure-aware retry ownership
    -> explicit lifetime/invalidation/security contract
```

Global knowledge remains where correctness requires it, but it is represented so point, bounded, and foreground operations do not repeatedly reconstruct the entire global corpus.

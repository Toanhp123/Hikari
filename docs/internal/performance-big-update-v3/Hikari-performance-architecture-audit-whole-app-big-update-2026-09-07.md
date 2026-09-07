# Hikari Whole-App Performance Architecture Audit — Big Update Baseline

**Original snapshot audited:** `Hikari-perf-discover-end-to-end(1).zip`  
**Revalidated against:** `Hikari-master.zip` (`c5b1102387f5a310fc00b926964751212e900051`)  
**Revalidated archive SHA-256:** `a4b7b17a5f59dde6ff5d7ae1884dae043f638f9d36c35b8b1b601a98feb1f9b1`  
**Superseded planning bundle SHA-256:** `d3813d2a3a1c4216ccfd2c1d33e1593949ac2fc59a7d3412da87a49d3139ad0d`  
**Audit date:** 2026-09-07  
**Revalidation date:** 2026-09-07  
**Purpose:** Replace a Catalog-centric interpretation of the earlier performance baseline with a whole-application performance architecture census suitable for a major performance update.

> This document is an **audit baseline**, not an implementation plan. It records confirmed structural defects, structural risks, effects/amplifiers, known-good reference patterns, and measurement gaps. Proposed implementation architecture and phasing should be designed only after this baseline is accepted.

---

## 1. Executive conclusion

The earlier red-team baseline was directionally strong. Its central rule remains correct:

> A bounded/user-local operation should not scale primarily with unrelated historical/global state.

However, the previous census was still disproportionately centered on Catalog/Canonical/Search. A whole-app pass found additional independent performance families in:

- Reader image continuity/cache planning;
- automatic-cache accounting and eviction;
- encoded payload ownership/allocation;
- Chapter aggregation and page commit;
- periodic background Chapter dispatch;
- process-lifetime memoization;
- cross-capability execution/resource governance;
- plugin control-plane metadata/package scope;
- plugin terminal-failure retry ownership;
- authenticated plugin HTTP credential/session hot-path work.

At the same time, one prior finding was too strongly classified:

- **L2 — retained top-level composition** must not be treated as a confirmed defect by itself. P6 production evidence shows retained composition with active-only measurement is an intentional optimization with material warm-navigation improvement and no material short-run RSS regression. The unverified question is whether *inactive retained destinations remain reactively active*.

### Revised census

| Category | Revised status |
|---|---:|
| Previously confirmed families | 23 |
| Prior family reclassified from confirmed to risk | -1 (`L2`) |
| Newly confirmed whole-app families | +11 (`X8`–`X18`) |
| **Confirmed structural root-cause families** | **33** |
| Structural/scaling risks | **7 primary risk families** |
| Effects/amplifiers | tracked separately |
| Non-bugs / intentional trade-offs | tracked separately |
| Measurement gaps | expanded whole-app matrix |

The number **33** is a root-cause-family census, not a benchmark-regression count and not a user-visible bug count.

---

## 2. Repository scope actually audited

Current Gradle graph contains:

- **19 production modules**;
- **1 benchmark module**;
- **20 Gradle modules total**.

Static source inventory used by this pass:

- approximately **612 Kotlin files** under production `src/main` / `src/benchmarkRelease`;
- approximately **330 Kotlin files** under `src/test` / `src/androidTest`.

### Module coverage

| Module / area | Audit focus | Result |
|---|---|---|
| `:app` | startup, navigation, WorkManager, notification/background ownership, DI, Android plugin auth/Keystore wiring | findings/risks in lifecycle/startup/periodic dispatch plus **X16/X18** auth integration evidence |
| `:core:common` | clocks, dispatchers, common utilities | no independent structural hotspot confirmed |
| `:core:designsystem` | backdrop/blur, image sizing, list/surface primitives | current top-level and Reader backdrop capture disabled; retained as reference-good behavior |
| `:catalog` | Search, Refresh, Details, canonical orchestration, metadata, reconciliation/fusion | major existing A/L/X families retained |
| `:catalog:model` | domain/read-model width | participates in X3 data-width cost, no separate family |
| `:catalog:engine` | ingest/candidate indexing, matching/reconciliation complexity | A1–A4/A3 retained |
| `:library` | mapping/search, bounded vs global observation | A5/A8/L8 and related risks retained |
| `:chapters` | multi-page sync, aggregation, source concurrency | **new X13**, cross-capability concurrency risk |
| `:reader` | routing, asset coordinator, working set, fetch arbiter | **new X8/X11/X12**, resource-governance risk |
| `:reader:engine` | pure routing/evaluation engine | no new independent performance defect confirmed in this pass |
| `:feature:catalog` | ViewModel execution context, global reducers, lifecycle demand | L1 broadened; A8/L8 affected surfaces expanded |
| `:feature:reader` | Lazy Reader UI, viewport publication, lifecycle flush | no rendering-root cause beyond Reader asset pipeline confirmed |
| `:feature:settings` | storage/background/session observation | aggregate-via-list is an A8 affected surface, not a new root family |
| `:downloads` | explicit downloads, automatic cache, Reader asset durable store | **new X8–X12** |
| `:settings` | policy/state contracts | no independent root family confirmed |
| `:storage:room` | query scope, indexes/query plans, invalidation domains, transaction granularity | existing X1/X2/X4 plus **new X8/X14/X15 evidence** |
| `:storage:files` | blob IO, leases, file locks, materialization | X10/X11 affected; file-lock maps correctly prune when idle |
| `:plugins:api` | protocol/contract bounds | no independent defect confirmed |
| `:plugins:runtime` | isolate lifecycle, package/control-plane loading, provisioning retry semantics, HTTP/credential bridge | **new X16–X18**; fresh-isolate cost remains risk-gated |
| `:benchmark` | scenario coverage and fixture dimensions | major coverage gaps confirmed |

---

## 3. Audit taxonomy

### 3.1 Confirmed structural root cause

A production call graph proves an architectural cost/ownership/lifetime property that is undesirable independent of exact device timing.

Examples:

- point lookup reads a global table;
- viewport planning performs global cache accounting;
- each page re-applies a cumulative graph through point updates;
- continuation batching rescans the complete candidate corpus.

### 3.2 Structural/scaling risk

Production architecture permits a pathological region, but severity/frequency still requires workload evidence.

Examples:

- candidate collision can make `K -> N`;
- fresh JS isolate per invocation may or may not dominate real workloads;
- retained inactive destinations may or may not continue Room/Flow work.

### 3.3 Effect/amplifier

Real user-visible/cross-feature consequence, but not an independent cause.

### 3.4 Intentional trade-off / non-bug

Costly-looking behavior that has an explicit correctness/security/performance reason and should not be “optimized away” without evidence.

---

## 4. Whole-app complexity variables

The earlier variables remain valid:

| Variable | Meaning |
|---|---|
| `N` | persisted catalog source-record footprint |
| `I` | external identifier rows |
| `B` | decoded/normalized/hashed catalog metadata bytes |
| `P` | enabled providers/sources |
| `M` | items in current Search/Refresh result |
| `U` | new Stories created by the current operation |
| `K` | reconciliation candidate count per item |
| `R` | Story redirect rows |
| `V` | Story IDs required by one UI operation |
| `C` | global canonical-catalog footprint |
| `L` | Library Story count |
| `D` | download-related item count |
| `E` | evidence changes in a logical commit |
| `H` | retained/reactively active top-level destination count |
| `S` | source-evidence rows owned by one Story |
| `G` | persisted reading-progress rows/history |
| `Q` | pending durable canonical/outbox backlog |
| `W` | persisted/debounced reading-progress writes |

This whole-app pass adds:

| Variable | Meaning |
|---|---|
| `RA` | persisted Reader asset metadata rows |
| `AC` | persisted automatic document-cache metadata rows |
| `Y` | encoded bytes of one Reader/chapter payload |
| `Fv` | accepted Reader viewport/planning updates over a workload |
| `J` | active Chapter releases for one Story |
| `Z` | canonical Chapters for one Story |
| `Pg` | source pages processed in one Chapter sync |
| `Bc` | cache eviction candidate count |
| `Lb` | Library candidates considered by periodic background dispatch (usually same order as `L`) |
| `Pl` | installed/enabled plugin count considered by a control-plane query |
| `JsB` | total `main.js` bytes read/decoded by a plugin-discovery workload |
| `Bp` | bundled `.osp` bytes materialized by provisioning for one pass |
| `Cr` | encrypted credential/session records for one plugin |
| `Hr` | authenticated plugin HTTP request-build count in one workload |

Performance contracts must reason about **row count, payload width, operation frequency, concurrency, failure state, and lifetime**, not only Big-O over one variable.

---

# 5. Revised confirmed root-cause index — 33 families

## 5.1 Catalog / canonical / global data-scope families retained from the prior baseline

### A1 — Foreground Search/Refresh/unowned Details rebuild global reconciliation evidence

Foreground operations call global source-record materialization and reconstruct ingest/reconciliation structures from historical catalog state.

**Cost axis:** `N + I + B`.  
**Type:** algorithm/data scope.  
**Priority:** P0 structural.

---

### A2 — Per-provider ingest `fork()` rebuilds global-ish index state

Provider-local ingest context clones/rebuilds structures whose cost scales with historical catalog size.

**Cost axis:** approximately `P × N log N` in the current abstraction.  
**Type:** algorithm.  
**Priority:** P0 structural.

---

### A3 — New Story creation reconstructs existing Story-ID membership per creation

For each newly created Story, callers rebuild the set of existing Story IDs rather than maintaining incremental membership.

**Cost axis:** approximately `U × N + U²`.  
**Type:** algorithm.  
**Priority:** P0 structural.

---

### A5 — Point canonical projection lookup performs a global canonical read

A request for one Story can route through global ready-canonical observation/materialization and then find one item locally.

**Cost axis:** `C` per point lookup; repeated path can become `V × C`.  
**Affected:** Discover settlement, Mapping preparation/URL resolution.  
**Priority:** P0 structural.

---

### A6 — Bounded Story-identity/redirect decisions materialize the full redirect table

The primary point resolver pays cost proportional to global redirect history rather than the relevant chain/indexed point structure. Revalidation against current `Hikari-master` found the same family in merge-reversal planning: `RoomStoryMergeReversalPlanner.prepareLineage()` calls `canonicalCatalogDao().redirects().any { ... }` to answer one bounded `EXISTS` question about incoming redirects to the survivor.

Confirmed production call sites therefore include:

- `RoomStoryIdentityResolver.resolve()/resolveAll()` global redirect materialization;
- `RoomStoryMergeReversalPlanner.prepareLineage()` full-table scan for one survivor/excluded-retired pair.

**Cost axis:** `R` per bounded decision.  
**Priority:** P1 systemic.

---

### A7 — Resolving a Story set builds per-Story full-redirect observers

Bulk resolution is implemented as multiple point observers, each backed by full redirect-table observation/materialization.

**Cost axis:** approximately `V × R`.  
**Priority:** P1 systemic.

---

### A8 — Bounded/aggregate UI problems subscribe to global datasets

Affected production families include:

- Library: global canonical catalog, mappings, progress;
- Story detail: `StoryViewModel` observes all Library entries and all progress records, then filters/reduces for one resolved Story;
- Downloads screen: global Chapter/canonical observations for a bounded explicit-download set;
- Story chapter controls: `DownloadViewModel.statuses` observes all download records and builds a global release-status map even though `StorySectionDependencies` needs only release IDs present in the current Story chapter state;
- Settings storage summary: full download-record observation to compute aggregate explicit bytes.

**Cost axis:** historical/global dataset sizes instead of `L`, `D`, the current release set, or one Story.  
**Priority:** P1.

---

## 5.2 Execution/scheduling/reactive families retained, with L1 broadened

### L1 — Foreground UI/service CPU projection lacks an explicit execution-boundary contract

The earlier finding named Search specifically. Whole-app inspection shows the pattern is broader.

Examples:

- Search reconciliation/projection launched from ViewModel/caller context;
- Library `combine { reduceLibraryState(...) }` performs map/group/filter/sort work before `stateIn(viewModelScope)`;
- Downloads builds global chapter/projection maps and groups/sorts records in the UI state pipeline;
- similar projection/reduction work exists in other foreground surfaces.

Room/network suspend points may leave their own thread pools, but CPU continuation/projection has no systematic `Default` boundary.

**Big-O:** unchanged by dispatcher fix; this is responsiveness/execution ownership.  
**Priority:** P0 UX / P1 systemic.

---

### L3 — Discover bootstrap and manual refresh lack one shared single-flight owner

Two lifecycle/user triggers can execute the same refresh pipeline concurrently.

**Type:** duplicate-work concurrency.  
**Priority:** P1.

---

### L4 — Search canonical settlement is serial

Bounded Story settlement accumulates wall-clock latency as a serial sum rather than bounded-concurrency waves.

**Type:** scheduling/wall-clock.  
**Priority:** P1 latency.

---

### L5 — Canonical fusion may rebuild once per evidence change instead of once per final affected Story

One logical batch can repeatedly trigger fusion for the same Story.

**Cost axis:** `E × fusionCost` versus unique affected Story count.  
**Priority:** P1.

---

### L6 — Canonical fusion repeatedly rehydrates/rehashes the same Story evidence

One rebuild performs multiple repository reads/identity resolutions/source-record hydrations/currentness checks over overlapping data.

**Amplifiers:** A6 (`R`) and X3 (`B`).  
**Priority:** P1.

---

### L7 — Search commit uses per-entry DAO statement calls

Persistence remains `O(M)` in rows but pays avoidable statement/transaction boundary overhead per item.

**Type:** DB call amplification.  
**Priority:** P1/P2.

---

### L8 — Library dependency policy controls readiness but not subscription demand

Library can know a dataset is semantically unnecessary for current filters/sort/search yet still keep observing/loading it.

**Interaction:** multiplies A8.  
**Priority:** P1.

---

## 5.3 Data-lifetime family retained

### D1 — Search evidence grows historical catalog state without a safe transient-lifetime model

Search-discovered evidence participates in identity/canonical semantics, yet no explicit provenance/reachability lifetime prevents indefinite `N(t)` growth.

**Important:** a blind TTL deletion would be unsafe; retention must preserve identity/canonical semantics.  
**Priority:** P0 long-term.

---

## 5.4 Previously added cross-system families retained

### X1 — Reactive invalidation domain does not match semantic data domain

Examples:

- Search and Home share `catalog_entries` invalidation domain;
- explicit Downloads and automatic cache share `chapter_storage_entries` table invalidation.

Table-level Room invalidation can wake consumers whose semantic state did not change.

**Priority:** P1 systemic.

---

### X2 — Application-scoped automatic-cache policy scans/sorts all reading progress

`AutomaticCachePolicyCoordinator` observes `progress.observeAll()` and derives incomplete release IDs after global materialization.

Room query plan for `ReadingProgressDao.observeAll()` on current schema:

```text
SCAN reading_progress
USE TEMP B-TREE FOR ORDER BY
```

The entity has no index starting with `updated_at_epoch_millis` matching the global order.

**Cost axis:** `W × G` materialization/sort/reduction in the long-lived path.  
**Priority:** P1 systemic.

---

### X3 — Over-wide read models + eager fingerprint recomputation make nominal `O(N)` work scale with bytes `B`

Reconciliation/projection/fusion paths decode, normalize, allocate, and hash broader metadata than many consumers need.

**Priority:** P1 major multiplier.

---

### X4 — Canonical reactive observation mixes trigger queries with repeated rehydration and lacks one coherent snapshot contract

Independent observed tables trigger separate resolved-state reads, creating duplicate query/allocation work and a structural mixed-generation risk.

**Confirmed:** duplicate trigger + rehydrate work.  
**Risk:** externally visible mixed-generation correctness state still needs targeted reproduction.  
**Priority:** P1.

---

### X5 — Foreground canonical execution and durable worker execution lack exclusive ownership/join semantics

Foreground may execute fusion directly while durable worker can lease the same runnable work item. Stale completion guards protect final state but do not refund duplicated CPU/DB work.

**Priority:** P1.

---

### X6 — Historical outbox recovery can leak into interactive Search/Refresh latency

Foreground evidence orchestration can materialize old pending outbox events before completing current user work.

**Cost axis:** historical backlog `Q`.  
**Priority:** P1.

---

### X7 — Catalog reconciliation performs batch work through point APIs

Candidate/reevaluation paths repeatedly resolve identity and reload Story source records one candidate/source at a time; reevaluation can approach an `S²` shape.

**Priority:** P1 systemic.

---

# 6. Newly confirmed whole-app root causes

## X8 — Reader viewport planning performs global cache-accounting work

### Production path

```text
Reader viewport change
  -> ReaderAssetCoordinator.updateViewport()
  -> schedulePlanning()
  -> currentCachePressure()
  -> ReaderAssetStore.cachePressure()
  -> AutomaticCacheBudgetCoordinator.snapshot()
  -> recomputeCommittedBytesLocked()
  -> automatic document SUM + Reader asset SUM
```

Representative source evidence:

- `reader/.../ReaderAssetCoordinator.kt:386-406` accepts a new viewport and calls `schedulePlanning()`;
- `ReaderAssetCoordinator.kt:727-748` obtains cache pressure before building each plan;
- `ReaderAssetCoordinator.kt:999-1004` delegates to `store.cachePressure()`;
- `downloads/.../DownloadReaderAssetStore.kt:106-119` calls `budget.snapshot()`;
- `downloads/.../AutomaticCacheBudgetCoordinator.kt:261-280` recomputes committed bytes on every snapshot;
- `storage/room/.../ReaderAssetDao.kt` implements `usageBytes()` as `SUM(byte_size)` over all Reader asset metadata.

Current query plan for Reader asset usage:

```text
SCAN reader_asset_entries
```

Automatic document usage is namespace-indexed, but still aggregates the complete automatic namespace.

### Cost shape

```text
Fv × [aggregate(AC) + aggregate(RA)]
```

Planning itself is viewport-local, but its cache-pressure input is historical/global.

### Classification

**Algorithmic/data-scope hot-path defect — P0/P1 Reader UX**.

### Required future contract

Reader viewport planning should consume an incrementally maintained/cache-local pressure snapshot rather than recalculate global usage on each plan request.

---

## X9 — Automatic-cache reconciliation rebuilds global candidate state and re-reads document metadata per victim

### Production path

`AutomaticCacheBudgetCoordinator.candidatesLocked()` builds candidates from:

```text
automaticDocumentsLocked()
  -> cacheRepository.entries()
  -> storage entries materialization
  -> Kotlin namespace filter

readerAssetMetadataRepository.all()
  -> all Reader asset metadata
```

For a document eviction candidate:

```text
detachDocumentIfCurrentLocked(expected)
  -> automaticDocumentsLocked()
  -> global document metadata reread
  -> firstOrNull(expected key)
```

The emergency maintenance loop can process up to 32 victims, and `shouldContinueReconciliation()` can rebuild candidates again after a pass.

### Cost shape

A pressure pass can contain:

```text
global candidate build
+ Bc × global document reread for document victims
+ post-pass global candidate rebuild
```

### Classification

**Algorithmic/I/O batch-via-global defect — P1 systemic**.

### Correction to earlier audit

The earlier `AMP-CACHE-BUDGET` note should be promoted from amplifier to this independent root cause.

---

## X10 — Automatic-cache publication mutex spans suspending DB/filesystem work

`AutomaticCacheBudgetCoordinator` uses one `publicationGate` mutex to linearize quota/publication state. The serialization itself is reasonable; the problem is critical-section width.

Confirmed work performed while holding the mutex includes examples such as:

- `clearAutomatic()` detaches metadata and calls document blob deletion inside the lock (`AutomaticCacheBudgetCoordinator.kt:233-257`);
- quota document eviction calls `deleteDocumentBestEffort()` inside the lock (`:367-375`);
- physical document relief deletes a document inside the lock (`:383-395`);
- publication callbacks can perform Room read/upsert while the authority is held.

Foreground Reader needs the same gate for:

- cache pressure;
- durable write authority capture;
- reservation/publication.

A visible remote asset path captures durable authority before entering content-fetch admission, so cache maintenance can delay visible fetch startup even though the network transfer itself is separately arbitrated.

### Classification

**Critical-section / foreground-background interference defect — P0/P1**.

### Distinction from X9

- X9: too much data/work per maintenance action.
- X10: unrelated foreground operations are serialized behind long suspending work.

---

## X11 — Encoded payload ownership causes repeated full-buffer copies/materialization

### Reader image path

`ReaderAssetPayload` deliberately owns a defensive copy:

- construction copies input bytes;
- `bytes()` returns another `copyOf()`.

A remote image can therefore pass through:

```text
HTTP stream
  -> ByteArrayOutputStream
  -> toByteArray
  -> ReaderAssetPayload defensive copy
  -> Coil bytes() copy
  -> durable persistence bytes() copy
  -> decoder / IO buffers
```

`MAX_READER_ASSET_BYTES` is 16 MiB, so these are not trivially small allocations.

Local persisted image verification also reads a bounded full blob into a `ByteArrayOutputStream`, verifies checksum, and retains the ByteArray in the read lease before Coil consumes it.

### Chapter/document path

The same ownership pattern exists in chapter blobs:

```text
ReaderDocumentBlobCodec ByteArrayOutputStream
  -> toByteArray
  -> ChapterBlob.fromBytes copy
  -> blob.bytes() copy
  -> DownloadService ChapterBlob.verified copy + checksum
```

Text documents are bounded by Reader sanitization (`MAX_DOCUMENT_CHARACTERS = 2,000,000`) but can still be multi-megabyte payloads.

### Classification

**Memory/GC/CPU allocation defect — P1**.

### Important non-bug boundary

Do **not** remove integrity verification merely to reduce cost. SHA/checksum verification is a correctness/security contract. The target is ownership/materialization strategy, not removal of validation semantics.

---

## X12 — Process-lifetime operational memoization lacks bounded retention

Confirmed examples after revalidation:

```kotlin
DownloadReaderAssetStore
private val lastAccessTouches = mutableMapOf<ReaderAssetKeyHash, Long>()
```

Successful touches keep a key indefinitely. Entries are removed only when the corresponding DB touch fails for the same timestamp. Asset eviction/session release does not prune the map.

```kotlin
@Singleton
CatalogMetadataCoordinator
private val suppressions = mutableMapOf<CatalogMetadataKey, AutomaticSuppression>()
```

Cooldown entries are lazily removed only when the same key is revisited after expiry; plugin-version suppressions are removed when the same key is revisited under a changed version or after a successful load. A process that encounters many distinct metadata keys and never revisits them has no global cardinality/age sweep or hard cap. (`inFlight` is not part of this finding because completion paths remove it.)

Therefore the family includes process-lifetime state whose retained cardinality can track distinct keys ever touched rather than the current working set.

Other process-level maps exist, but several correctly prune or are naturally bounded by installed plugins. They should not all be labeled defects. X12 applies only where owner/lifetime/cardinality are not bounded.

### Classification

**Memory-aging defect — P2**.

---

## X13 — Multi-page Chapter sync repeatedly re-aggregates the cumulative graph

`ChapterPageSynchronizer` maintains an in-memory rolling graph, which correctly avoids the older public `snapshot()` per page. However each page still calls:

```text
aggregation.plan(
  existing = graph.chapters,
  releases = all active/cumulative releases,
  overrides = graph.overrides
)
```

`ChapterAggregationEngine.plan()` iterates every release. `bestCandidate()` then scans every existing canonical chapter and every newly created candidate.

For page `i`, let active releases be `J_i` and canonical chapters `Z_i`:

```text
page CPU ~= O(J_i × Z_i)
```

Over a multi-page run:

```text
Σ_i O(J_i × Z_i)
```

If both grow approximately with cumulative release count and page size is roughly fixed, the repeated cumulative work can be substantially super-quadratic over the whole sync.

The same page path also re-sorts/re-hashes cumulative release IDs for the sync fingerprint.

### Classification

**Algorithmic cumulative-reprocessing defect — P1 Chapter sync**.

### Why this was easy to miss

Existing regression coverage proves the public Chapter repository snapshot is not called once per source page. It does not measure the aggregation cost over the cumulative in-memory graph.

---

## X14 — Chapter page commit re-applies cumulative links through point writes and rehydrates the full graph before/after every page

`RoomChapterRepository.commit()` performs, inside one transaction:

```text
identity.resolve
snapshotResolved(before)
write page releases/creates
for each plan.links -> dao.link(one release)
restore/tombstone
write sync state
snapshotResolved(after)
ChapterChangeDetector.detect(before, after)
notification evidence
```

`plan.links` is generated for all active releases, not only the newly changed releases. Therefore each later page can reissue link updates for already-known releases.

`snapshotResolved()` itself reads:

- canonical chapters with `@Relation` releases;
- the Story release list again;
- aggregation overrides.

It is called **twice per page** for notification diffing.

### Cost shape

For a multi-page run:

```text
Σ_i [full Story graph read before
     + J_i point link statements
     + full Story graph read after
     + graph diff]
```

### Classification

**Database/read-write amplification + cumulative commit defect — P1 systemic**.

### Distinction from X13

- X13 is CPU matching/aggregation complexity before persistence.
- X14 is transaction/DAO/readback cost after a plan is produced.

---

## X15 — Periodic Chapter continuation rescans/re-sorts the entire eligible Library for every batch

Periodic dispatch uses a batch size of 20.

Every initial or continuation worker executes:

```text
dispatch(cursor)
  -> eligibleCandidatesOrNull()
  -> Room Library chapterSyncCandidates()
  -> full eligible Library JOIN/GROUP/ORDER query
  -> materialize all candidates
  -> ChapterSyncBatchPlanner.plan(all, cursor)
      -> distinctBy(all)
      -> sortedWith(all)
      -> filter items after cursor
      -> take(20)
```

If more work remains, the continuation is enqueued; that continuation repeats the same full query/materialization/sort.

Current SQLite query-plan characteristics include:

```text
SCAN library ...
SEARCH redirect by retired_story_id
SEARCH chapter_sync_states by story_id
USE TEMP B-TREE FOR GROUP BY
USE TEMP B-TREE FOR ORDER BY
```

A complete cycle over `Lb` eligible Stories with batch size 20 can therefore perform approximately `ceil(Lb / 20)` global candidate scans/sorts.

### Classification

**Background batch-via-global-rescan defect — P1/P2 battery/DB contention/throughput**.

### Important distinction

One global scan in a periodic maintenance job can be legitimate. The defect is performing it again for every continuation page instead of carrying/performing keyset-bounded selection at the storage boundary.

---

## X16 — Plugin control-plane queries materialize payload or metadata at a scope wider than the semantic question

### Production paths

#### Operation capability discovery loads executable script bytes

`DefaultPluginRuntime.enabled(operation)` first obtains all enabled states for the service, then calls `loadPackage(stored)` for every candidate. `loadPackage()` uses `readPackage()`, which reads and decodes both:

```text
manifest.json
main.js
```

Only the manifest is needed to answer `manifest.supports(operation)`. The first cold capability-discovery pass can therefore scale with both plugin count and the sum of executable script bytes:

```text
enabled(operation)
  -> state.all()
  -> candidate plugins
  -> loadPackage(candidate)
  -> read manifest.json
  -> read main.js
  -> supports(operation)
```

The existing successful package cache improves later calls, but it does not change the cold control-plane cost shape and it couples metadata discovery to data-plane executable materialization.

#### Point authentication policy lookup scans all installed plugin manifests

`DefaultPluginSessionService.sessionFor(request)` needs the authentication policy for exactly `request.pluginId`, but `InstalledPackageAuthenticationPolicySource.installedAuthenticationPolicies()` currently executes:

```text
state.all()
  -> for each installed plugin
  -> read manifest.json
  -> decode manifest
  -> extract authentication capability
  -> caller singleOrNull(request.pluginId)
```

Because `PluginHttpCapability.buildRequest()` asks its managed credential provider on every plugin HTTP request build, this all-plugin manifest scan can leak into a request-local hot path.

#### Bundled provisioning materializes package bytes before version/state filtering

`AndroidBundledPluginSource.packages()` reads every descriptor-pinned `.osp` asset fully into a `ByteArray`; only afterwards does `BundledPluginProvisioner.provision()` compare the bundled version with installed state to decide whether install/update work is needed.

### Cost shape

Control-plane work can scale with unrelated payload width:

```text
operation discovery:  O(Pl + JsB)
point auth policy:     O(Pl + total manifest bytes)
bundled provision:    O(Bp) before installed-version filtering
```

### Classification

**Control-plane/data-plane scope defect — P1 systemic.**

This is the same architectural rule as the broader whole-app bounded→global findings, but it lives at a non-Room storage boundary: a metadata/point question is physically implemented by executable/package/all-plugin materialization.

### Required future contract

- capability discovery reads/caches immutable manifest metadata only;
- `main.js` is loaded only when an invocation actually executes;
- authentication policy has a point `pluginId` path and may keep a separate explicit all-plugin maintenance path;
- bundled provisioning is descriptor-first and reads `.osp` bytes only for a plugin that actually needs install/update verification.

---

## X17 — Plugin retry ownership ignores terminal/nonretryable outcome lifetime

### Production paths

`BundledPluginProvisioner` memoizes only a completely successful process pass:

```text
provisioningSucceeded = true
```

If even one bundled plugin returns a stable failure, the global success flag remains false. `PluginUpdateDecision.NEEDS_REVIEW` is converted to:

```text
PluginCallResult.Failure("plugin.update_needs_review", retryable = false)
```

but the next public runtime entry (`enabled(...)` or `invoke(...)`) calls `ensureProvisioned()` again, rereads bundled package sources, and retries the same pass. A single plugin requiring user action can therefore amplify work for every plugin runtime call in the process.

A related package-load pattern exists in `DefaultPluginRuntime`: successful immutable package identities are cached, while nonretryable manifest/package failures are not retained by identity, so repeated calls can repeat the same storage/parse failure work.

### Root cause

The runtime has single-flight for **one attempt**, but no lifetime contract that distinguishes:

```text
SATISFIED / SUCCESS
TERMINAL OR USER-ACTION-REQUIRED
TRANSIENT RETRYABLE
CANCELLED/ABORTED ATTEMPT
```

Therefore failure classification does not control future retry ownership.

### Important correctness precondition

Do **not** indiscriminately negative-cache every failure. The previous P3 policy intentionally retried failed passes. The repair must preserve that behavior for genuinely retryable/transient failures and must preserve cancellation semantics. Terminal memoization is valid only when keyed to an immutable package/descriptor + relevant installed-state identity; a changed package/state generation invalidates the terminal outcome.

Also preserve the original `PluginCallResult.Failure` from package storage instead of flattening an unknown storage failure into a synthetic terminal result before deciding whether it is safe to memoize.

### Classification

**Failure-amplification / retry-ownership defect — P1 systemic.**

### Required future contract

- each bundled descriptor has an outcome keyed to descriptor identity plus the installed state relevant to the decision;
- `NEEDS_REVIEW`/other terminal outcomes do not re-read/reverify package bytes on every unrelated runtime call;
- changed installed state, bundled descriptor/version/hash, or explicit update/review action invalidates the terminal stamp;
- transient retryable failures remain bounded-retry/single-flight and are never permanently suppressed;
- terminal package-load failures may be memoized only by immutable active-package identity and only after retryability is preserved correctly.

---

## X18 — Authenticated plugin HTTP request construction duplicates secure-session reads and Keystore lookup work

### Production path

For a request with an installed authentication policy:

```text
PluginHttpCapability.buildRequest()
  -> ManagedCredentialProvider.headers()
  -> PluginSessionManagedCredentialProvider.headers()
  -> DefaultPluginSessionService.sessionFor(request)
     -> policy lookup
     -> validSessionRecords()
        -> store.readAll(pluginId)          # first read/decrypt snapshot
        -> filter request-valid records
        -> refreshSummary(pluginId, policy)
           -> store.readAll(pluginId)       # second read/decrypt snapshot
```

`AndroidKeystorePluginSessionStore.readAll()` decrypts every stored credential record. Each `decrypt()` currently calls `key()`, and `key()` opens/loads Android Keystore and performs `getKey(...)` again.

For `Cr` records, one authenticated request build can therefore perform roughly two encrypted-file decodes, `2 × Cr` decrypt operations and `2 × Cr` Keystore key acquisitions/lookups, before counting the all-plugin policy scan from X16.

### Classification

**Secure hot-path duplicate-work defect — P1 latency/CPU.**

The security semantics are intentional; the duplicate reads/key acquisition are not.

### Required future contract

```text
point policy(pluginId)
  -> one session snapshot read/decrypt
  -> filter request records
  -> derive/publish summary from the SAME snapshot
```

and each `PluginSessionStore.readAll()/replaceAll()` operation obtains the `SecretKey` once and reuses that handle for all records in that storage operation. No long-lived plaintext cookie cache is required to close X18.

Whether a generation/TTL-bound decrypted session cache is worthwhile **after** these repairs remains an evidence-gated security/performance question (`RISK-PLUGIN-AUTH-CACHE`).

---

# 7. Reclassification: prior L2 is now a structural risk, not a confirmed defect

## Reclassification detail — retained composition is not itself the defect

`PersistentTopLevelNavDisplay` intentionally keeps visited top-level `NavDisplay`s composed but measures/places only the active route.

Current project performance evidence records P6 as production-promoted:

- repeatable ~90 ms Home/Discover rebuild spike removed;
- CPU P90 reduced materially;
- maximum-frame metric improved materially;
- no material short-run RSS regression.

Therefore this statement is **not** valid:

> “retained composition is itself a performance bug.”

The unresolved performance contract is:

```text
inactive retained route
  -> should retain navigation/ViewModel/saveable state
  -> should not perform material foreground Flow/Room/reducer work
```

Current tests prove composition/ViewModel retention and active-only measurement, but do not count:

- active Flow subscribers;
- Room query executions;
- reducer invocations;
- recompositions caused by DB invalidation while a route is inactive.

Promote this risk back to a confirmed family only if instrumentation proves inactive work remains active.

---

# 8. Primary structural/scaling risks — not included in the 33

## RISK-A4 — Reconciliation candidate fan-out can approach `K -> N`

Common title/author tokens may produce large posting lists. Needs collision-heavy corpus benchmark.

---

## RISK-BIND — Bounded Story-ID Room APIs lack an explicit cardinality/chunking contract

Replacing global observation with `WHERE story_id IN (...)` is not sufficient by itself for Library sets in the hundreds/thousands. Needs query-size/chunking/SQLite-limit benchmarks.

---

## RISK-LIFECYCLE — Inactive retained tabs may continue reactive DB/Flow work

Described above. Must be measured rather than “fixed” by undoing P6.

---

## RISK-GLOBAL-RESOURCE — Network/content execution governance is fragmented across capabilities

Reader documents, Reader assets, and explicit downloads share `ContentFetchArbiter` with a process-wide total of 3 and one reserved visible slot. This is a strong local design.

However Catalog Search/Refresh provider calls, Chapter plugin sync, plugin HTTP bridge calls, and WorkManager background jobs are outside that same capacity owner. Examples include:

- Catalog provider fan-out via `async` for every enabled source;
- Chapter sync `async` for every current mapping;
- periodic dispatch may enqueue up to 20 Story sync jobs per batch;
- plugin HTTP is ultimately governed by its own runtime/OkHttp constraints.

The architecture therefore permits cross-capability contention even though Reader-local work is bounded.

Do not call this a measured defect until contention workloads demonstrate it, but the big update needs an explicit process-level resource contract.

---

## RISK-PLUGIN-ISOLATE — One fresh JavaScript isolate per plugin invocation may be material

`AndroidxJavaScriptEngine.execute()` creates and closes a bounded isolate for each invocation.

This is **intentional current design**: the earlier Reader continuity work explicitly deferred pooling until benchmarks prove isolate startup material.

Therefore it is a benchmark target, not a confirmed bug.

Also note the runtime already contains positive optimizations:

- shared sandbox;
- loaded package cache;
- per-plugin package lock;
- LRU-capped invocation source literal cache;
- bounded output/bridge/HTTP payload limits.

---

## RISK-PLUGIN-AUTH-CACHE — Secure session read/decrypt per authenticated request may remain material after X16/X18

X18 removes duplicate snapshot reads and repeated per-record key acquisition without keeping plaintext credentials alive longer than necessary. After that repair, each authenticated plugin HTTP request may still need one encrypted session-file read/decrypt pass.

This is not statically classified as a defect because Android Keystore/file cost is device-dependent and the security trade-off of retaining decrypted credentials is material.

Benchmark at least:

```text
Cr = 0 / 1 / 4 credential records
Hr = 1 / 10 / 100 authenticated request builds
cold process / warm process
```

Promote only if the remaining one-snapshot secure read materially dominates request construction or provider latency. Any promoted cache must be generation-bound, short-lived/bounded, cleared on logout/policy change/security invalidation, and threat-reviewed before implementation.

---

## RISK-STARTUP-CONTENTION — App-scoped policy/recovery work may contend with cold foreground startup on aged state

`OpenStoryApplication.onCreate()` starts application-scope work including:

- Reader security invalidation observer;
- automatic cache policy coordinator;
- background policy coordinator;
- notification recovery wake.

Most of this work is not executed directly on Main, so static audit does **not** classify it as a startup-thread bug.

However on aged DB/cache state it can concurrently issue Room/WorkManager/cache work while first-screen startup is occurring. Needs cold-start tracing with large `G`, `RA`, `AC`, and recovery state.

---

# 9. Effects/amplifiers — do not patch independently

1. **Search -> Home wake-up** from X1 table invalidation mismatch.
2. **Automatic cache -> Downloads wake-up** from X1 mixed table invalidation.
3. **Inactive destinations amplify invalidations** if RISK-LIFECYCLE is reproduced.
4. **Redirect changes can fan out through A7** across progress/chapters/mappings/projection observers.
5. **Search jank** is a compound of L1 × A1/A2/A3 × X3 × canonical work.
6. **App aging** compounds D1, X2, X8/X9, and background candidate work.
7. **Reader viewport churn** multiplies X8 because stale planning cancellation cannot make already-started database aggregation free.
8. **Cache pressure** multiplies X9 and X10 simultaneously: more global candidate work occurs while foreground-sensitive state uses the same gate.
9. **Multi-page Chapter sources** multiply both X13 and X14.
10. **Large Library background cycles** multiply X15 and can contend with foreground Room consumers.
11. **Encoded page size `Y` and concurrent prefetch** multiply X11 allocation/GC pressure.
12. **Plugin/provider discovery frequency** multiplies X16 when many operation-specific registries repeatedly ask for enabled plugins.
13. **One stable bundled `NEEDS_REVIEW`/terminal package state** can multiply X17 across otherwise unrelated plugin runtime calls.
14. **Authenticated plugin HTTP request count `Hr` and credential count `Cr`** multiply X18; X16 adds unrelated installed-plugin manifest width to the same request path until the point-policy repair lands.

---

# 10. Query-plan findings from current Room schema

This audit intentionally checked representative SQLite plans rather than equating “missing annotation index” with “full scan.”

## 10.1 Confirmed expensive plan: global reading progress

```sql
SELECT * FROM reading_progress
ORDER BY updated_at_epoch_millis DESC, story_id ASC, canonical_chapter_id ASC
```

Plan:

```text
SCAN reading_progress
USE TEMP B-TREE FOR ORDER BY
```

Supports X2.

## 10.2 Bounded progress query uses Story PK prefix but still sorts

```text
SEARCH reading_progress USING primary-key autoindex (story_id=?)
USE TEMP B-TREE FOR ORDER BY
```

This is materially healthier than the global path; large ID-cardinality still belongs under RISK-BIND.

## 10.3 Automatic document usage is not an all-table scan

The `chapter_storage_entries` primary key starts with `namespace`, so:

```sql
WHERE namespace = 'AUTOMATIC_CACHE'
```

can use the PK autoindex.

Therefore this audit does **not** claim automatic-cache SUM scans explicit-download rows. X8 is about recomputing the entire relevant automatic-cache namespace and full Reader asset usage on a viewport-local path.

## 10.4 Download list is namespace-indexed but requires a temp order structure

Representative plan:

```text
SEARCH chapter_storage_entries USING PK autoindex (namespace=?)
USE TEMP B-TREE FOR ORDER BY
```

Not promoted as an independent defect in this pass.

## 10.5 Reader asset usage is a full metadata aggregate

```text
SCAN reader_asset_entries
```

Supports X8.

## 10.6 Periodic Library candidate selection is intentionally global but expensive to repeat

Plan includes:

```text
SCAN library
indexed redirect lookup
indexed chapter_sync_states lookup
TEMP B-TREE GROUP BY
TEMP B-TREE ORDER BY
```

One occurrence can be acceptable maintenance work. Repeating it for every batch continuation is X15.

---

# 11. Important non-bugs / architecture that should not be accidentally regressed

## 11.1 Retained top-level composition

P6 is measured production optimization. Preserve warm-switch benefits while adding a separate inactive-demand contract if needed.

## 11.2 Reader integrity verification

Local Reader asset checksum verification is part of durable-content correctness/security. Optimize streaming/ownership if justified; do not silently remove integrity semantics.

## 11.3 Per-operation JavaScript isolate

Intentional security/runtime baseline until benchmark evidence supports pooling or another execution model.

## 11.4 Global scans in explicit maintenance

A single bounded global scan in recovery/maintenance can be legitimate. The audit targets foreground leakage or repeated global scans per logical batch.

## 11.5 Existing process-wide Reader content arbiter

Reader documents/assets and explicit download fetching already share a priority-aware capacity owner with visible reservation. This is a positive pattern to preserve/possibly generalize, not remove.

## 11.6 File lock maps

`ReaderAssetBlobFileLocks` and `ChapterBlobFileLocks` prune entries when operations/leases become idle. They are not X12-style leaks.

## 11.7 Design-system backdrop capture

Current app shell and Reader set `captureBackdrop = false`; do not count dormant blur infrastructure as a current runtime hotspot merely because the library exists.

## 11.8 Lazy collection fundamentals

Major screens use Lazy containers and widespread stable keys. Rendering optimization should be evidence-driven rather than assuming list composition is the primary bottleneck.

---

# 12. Measurement gaps for the big update

The current benchmark suite is useful but does not cover the dimensions discovered by this audit.

## 12.1 Search engine scaling

Current `searchReopen` opens/closes Search but does not execute a representative real query against large persisted catalog state.

Required dimensions:

```text
N = 30 / 300 / 3,000 / 30,000
B = narrow / medium / wide
P = 1 / 2 / 4 / 8
candidate collision = low / medium / adversarial
Q = 0 / backlog
R = 0 / 100 / 1k / 10k
```

## 12.2 Application aging

Run the same foreground actions while independently varying:

- `N` catalog evidence;
- `G` progress history;
- `RA` Reader asset metadata;
- `AC` automatic document metadata;
- `Q` durable backlog.

Measure slope, not only one absolute timing.

## 12.3 Reader image pipeline benchmark

Current long Reader benchmark fixture is paragraph/text based and does not exercise the durable image-continuity pipeline.

Add image workloads varying:

```text
Y = small / medium / near max encoded page
RA = 0 / hundreds / thousands / tens of thousands
AC = 0 / hundreds / thousands
viewport scroll speed / direction churn
current + ahead prefetch pressure
memory-cache hit / RICC disk hit / remote miss
```

Measure:

- viewport-to-plan latency;
- Room query count;
- encoded allocation bytes;
- GC count/time;
- first-pixel latency;
- cache-pressure snapshot frequency;
- durable commit latency;
- network fetch count.

## 12.4 Automatic-cache pressure/eviction benchmark

Scenarios:

- under quota;
- just over high watermark;
- emergency storage pressure;
- mixed document/image cache;
- active read leases;
- 1 / 8 / 32 victims;
- concurrent visible Reader asset request.

Measure lock wait time and per-victim metadata queries.

## 12.5 Chapter multi-page scaling

Current Chapter tests are primarily correctness-oriented.

Add:

```text
Pg = 1 / 5 / 20 / 100
J = tens / hundreds / thousands
Z = low / proportional to J
sources/mappings = 1 / several
```

Collect separately:

- aggregation CPU;
- `dao.link()` statement count;
- before/after graph query count;
- transaction time per page;
- total full-sync slope.

## 12.6 Periodic background dispatch scaling

Vary Library candidate count around batch boundaries:

```text
Lb = 20 / 21 / 100 / 500 / 2,000
```

Measure:

- `chapterSyncCandidates()` invocations per complete cycle;
- rows materialized;
- temp sort/group CPU;
- WorkManager enqueue count;
- total DB time;
- foreground interference while the cycle runs.

## 12.7 Inactive retained destination work

Instrument `H = 1 / 2 / 3+` visited top-level routes and count while only one is visible:

- Flow subscriber count;
- Room query executions;
- reducer invocations;
- recompositions;
- CPU wakeups;
- RSS.

Must preserve the P6 warm-navigation A/B comparison.

## 12.8 UI reducer execution-context benchmark

Large Library/Downloads/Updates fixtures should trace CPU thread/context and frame timing during Room invalidations to validate L1, not merely scroll rendering.

## 12.9 Plugin invocation benchmark

Measure separately:

- first bundled provisioning;
- warm package cache;
- isolate create/evaluate/close;
- script size;
- bridge message size;
- output size;
- 1 / 2 / 4 / 8 concurrent providers.

Do not implement isolate pooling before this evidence.

## 12.10 Plugin control-plane / terminal-failure benchmark

Measure independently from JavaScript execution:

```text
Pl  = 1 / 4 / 16 installed plugins
JsB = tiny / medium / large aggregate main.js bytes
Bp  = tiny / medium / large bundled package bytes
state = already-current / missing / update-needed / NEEDS_REVIEW / terminal invalid package / transient failure
calls = 1 / 10 / 100 enabled(operation) or invoke entry calls
```

Collect:

- manifest reads;
- `main.js` reads/decoded bytes during `enabled(operation)`;
- bundled descriptor reads versus `.osp` byte reads;
- provisioning pass count;
- installer/verifier/update call count;
- behavior after immutable package/state identity changes.

X16 closes only when discovery/control-plane work is independent of `JsB` and irrelevant `Bp`; X17 closes only when terminal state does not amplify repeated calls while transient retry remains possible.

## 12.11 Plugin authenticated-request secure-session benchmark

Use a fake/counting policy source and session store for deterministic contract counts plus Android instrumentation for real Keystore cost.

```text
Cr = 0 / 1 / 4
Hr = 1 / 10 / 100
Pl = 1 / 4 / 16 unrelated installed plugins
```

Before X16/X18, characterize policy manifest reads, session-store reads, decrypt count and key lookup count per request. After repair require point policy scope, one session snapshot per request, and one key acquisition per store operation. Then decide `RISK-PLUGIN-AUTH-CACHE` from real-device traces.

## 12.12 Cross-capability contention benchmark

Critical scenario:

```text
visible Reader current-page fetch
+ Reader prefetch
+ explicit download
+ periodic Chapter sync
+ Catalog Search/Refresh
+ canonical worker/recovery
```

Measure visible request admission/start latency, SQLite wait time, CPU saturation, network socket concurrency, and WorkManager overlap.

## 12.13 Cold startup on aged state

Cold start with large `G`, `RA`, `AC`, pending notification/canonical recovery, and background policy enabled.

## 12.14 Memory-aging / memoization

Long-running Reader session visiting thousands of distinct page keys; verify process maps and heap after corresponding disk entries are evicted.

## 12.15 Canonical coherence and query count

Retain the earlier requirement for multi-table transaction tests proving no externally visible mixed-generation canonical state and quantify trigger-query + rehydrate duplication.

## 12.16 Large redirect set

`R = 100 / 1,000 / 10,000` for point and bulk identity APIs.

## 12.17 Large bounded-ID query contract

Hundreds/thousands of Story IDs for projections/progress/mappings/chapters to define chunking/query cardinality limits.

## 12.18 Story-screen bounded-history contract

Hold one Story and its visible Chapter/release set constant while independently sweeping:

- Library rows `L = 10 / 1,000 / 10,000`;
- explicit download records `D = 10 / 1,000 / 10,000`;
- progress history `G = 30 / 3,000 / 30,000`.

Story state/subscription work must remain approximately a function of the one Story/current release set, not unrelated Library/download/progress history. This benchmark exists specifically to prevent A8 from being declared closed merely because the standalone Library or Downloads screen was optimized.

---

# 13. Whole-app causal themes for solution design

The 33 families collapse into eight architecture themes. Future solution design should address themes rather than patch files independently.

## Theme A — Operation scope vs historical/global data scope

IDs:

```text
A1 A2 A3 A5 A6 A7 A8 D1 X2 X6 X8 X9 X15 X16
```

Desired rule:

> Foreground/bounded operations scale with the requested working set; maintenance/global work is explicit, budgeted, and not repeatedly reconstructed.

---

## Theme B — Read-model width and allocation ownership

IDs:

```text
X3 L6 X11
```

Desired rule:

> Consumers receive the narrow representation they need, and large encoded data has one clear ownership/streaming contract rather than defensive full copies at every boundary.

---

## Theme C — Reactive invalidation and lifecycle demand

IDs/risks:

```text
X1 X2 X4 A8 L8 RISK-LIFECYCLE
```

Desired rule:

> Observer lifetime and invalidation scope match semantic demand, not table layout or accidental composition lifetime.

---

## Theme D — Batch semantics vs repeated point/cumulative work

IDs:

```text
L7 X7 X9 X13 X14 X15
```

Desired rule:

> One logical batch should have bulk storage/algorithm APIs and should not repeatedly reprocess the prefix already handled.

---

## Theme E — Execution ownership and critical-section boundaries

IDs/risks:

```text
L1 L3 L4 L5 X5 X10 X17 RISK-GLOBAL-RESOURCE
```

Desired rule:

> One owner executes each expensive work item, critical sections protect state rather than I/O duration, and visible work has explicit capacity/priority.

---

## Theme F — Lifetime/aging control

IDs:

```text
D1 X12
```

plus aged dimensions `G`, `RA`, `AC`, and `Q` from other families.

Desired rule:

> Persistent and process-memory state has an explicit retention/reachability/eviction contract.

---

## Theme G — Plugin control-plane, failure-state and secure hot-path ownership

IDs/risks:

```text
X16 X17 X18 RISK-PLUGIN-AUTH-CACHE RISK-PLUGIN-ISOLATE
```

Desired rule:

> Metadata/control-plane questions do not load executable/package payloads, stable terminal state does not trigger repeated expensive retries, and security-sensitive request construction consumes one bounded point policy + one coherent credential snapshot before considering any longer-lived secret cache.

---

## Theme H — Performance observability as architecture contract

Not a runtime defect ID, but required to prevent regression.

Benchmarks must encode scaling dimensions, failure states, query/allocation ownership and secure/control-plane operation counts, not only UI navigation timings.

---

# 14. Big-update detailed work inventory

This is an audit inventory, not the implementation sequence.

## 14.1 Catalog / canonical

- [ ] remove foreground full reconciliation evidence reconstruction (A1);
- [ ] replace global-rebuilding ingest fork semantics (A2);
- [ ] maintain incremental Story-ID membership (A3);
- [ ] benchmark/bound candidate fan-out (RISK-A4);
- [ ] provide true point/bulk canonical projection APIs (A5);
- [ ] provide efficient point/bulk redirect resolution (A6/A7);
- [ ] narrow reconciliation/fusion read models (X3/L6);
- [ ] make canonical observation coherent and avoid trigger+rehydrate duplication (X4);
- [ ] coalesce fusion per final affected Story where semantics allow (L5);
- [ ] establish foreground/worker exclusive ownership or join semantics (X5);
- [ ] decouple/budget historical outbox recovery from interactive work (X6);
- [ ] replace reconciliation batch-via-point behavior (X7);
- [ ] define safe evidence lifetime/reachability for D1;
- [ ] preserve correctness and merge/reversal semantics throughout.

## 14.2 Foreground UI / lifecycle

- [ ] establish explicit CPU dispatcher/projection contract across Search/Library/Downloads/etc. (L1);
- [ ] migrate bounded screens from global observation to bounded/aggregate read models (A8);
- [ ] make Library subscription demand follow semantic dependency policy (L8);
- [ ] measure inactive retained destination collectors before changing P6 (RISK-LIFECYCLE);
- [ ] retain P6 active-only measurement and warm-navigation benefit;
- [ ] verify `WhileSubscribed` actually reaches zero when semantic demand disappears.

## 14.3 Room / reactive storage

- [ ] define semantic invalidation domains instead of relying only on shared-table invalidation (X1);
- [ ] replace global progress-history policy projection with query-specific/delta state (X2);
- [ ] add/validate workload-aligned indexes only after real query-plan analysis;
- [ ] define bind-cardinality/chunking contract (RISK-BIND);
- [ ] instrument Room query counts for canonical, cache, Chapter and background paths.

## 14.4 Reader / automatic cache / files

- [ ] remove global cache SUM from viewport planning path (X8);
- [ ] avoid repeated global cache candidate/reread per victim (X9);
- [ ] shrink publication mutex to state publication/authority transitions, not DB/filesystem duration (X10);
- [ ] redesign encoded payload ownership to reduce full-buffer copies without weakening checksum/security (X11);
- [ ] bound/prune process-lifetime touch memoization (X12);
- [ ] preserve RICC cache continuity, active-generation and security invalidation semantics;
- [ ] preserve shared visible-priority `ContentFetchArbiter` behavior;
- [ ] add real image-based Reader Macrobenchmarks.

## 14.5 Chapters

- [ ] stop re-running all-pairs cumulative aggregation for every page (X13);
- [ ] avoid cumulative relink statements on each page (X14);
- [ ] avoid two full graph rehydrates per page solely to derive notification delta where a narrower equivalent contract is possible (X14);
- [ ] preserve deterministic aggregation/override/tombstone semantics;
- [ ] preserve durable notification evidence correctness;
- [ ] benchmark long manga/novel sources and multi-source Stories.

## 14.6 Background / WorkManager

- [ ] replace periodic continuation full-Library rescan with storage-bound/keyset batch selection or equivalent bounded continuation (X15);
- [ ] measure cross-worker DB/network contention;
- [ ] establish a process-level resource/capacity contract across Reader, Downloads, Catalog, Chapter sync and plugin HTTP (RISK-GLOBAL-RESOURCE);
- [ ] retain bounded canonical worker batch/run budgets;
- [ ] verify startup recovery does not steal first-screen latency on aged state.

## 14.7 Plugin runtime

- [ ] split immutable manifest/control-plane access from executable `main.js` loading (X16);
- [ ] make `enabled(operation)` manifest-only and keep invocation script loading lazy (X16);
- [ ] make installed authentication policy point-addressable by `pluginId`; retain explicit all-plugin maintenance enumeration separately (X16);
- [ ] make bundled provisioning descriptor-first so current/newer installed versions do not force `.osp` byte materialization (X16);
- [ ] classify provisioning/package outcomes as satisfied vs terminal/user-action-required vs transient retryable and bind memoized terminal outcomes to immutable identity/state (X17);
- [ ] preserve transient retry and cancellation semantics; never auto-approve capability expansion for performance (X17);
- [ ] derive session summary from the same secure snapshot used for request credentials and acquire the Keystore key once per storage operation (X18);
- [ ] benchmark the remaining one-snapshot secure read before considering decrypted credential caching (RISK-PLUGIN-AUTH-CACHE);
- [ ] benchmark isolate startup only after X16–X18 are removed from the measurement path (RISK-PLUGIN-ISOLATE);
- [ ] benchmark provider-count concurrency and bridge/output allocation;
- [ ] preserve sandbox heap/output/bridge/HTTP bounds;
- [ ] preserve plugin isolation/security semantics;
- [ ] keep package/manifest/script/outcome caches bounded, identity-correct and invalidated by package/state changes.

## 14.8 Benchmark / acceptance

- [ ] real Search query benchmark;
- [ ] aged catalog/progress/cache/backlog fixtures;
- [ ] image Reader benchmark;
- [ ] automatic-cache pressure benchmark;
- [ ] multi-page Chapter sync micro/integration benchmark;
- [ ] periodic continuation scaling benchmark;
- [ ] inactive-tab reactive-work instrumentation;
- [ ] plugin control-plane/terminal-failure benchmark;
- [ ] plugin authenticated-request secure-session benchmark;
- [ ] plugin isolate/provider benchmark after X16–X18 closure;
- [ ] cross-capability contention benchmark;
- [ ] allocation/GC metrics for `Y`;
- [ ] query counts and query-plan assertions where stable enough;
- [ ] scaling-ratio gates, not one device number only.

---

# 15. Complexity contracts to freeze before implementation

| Operation | Must not primarily depend on | Target scope |
|---|---|---|
| Search query | historical `N/B/R/Q` | current page `M`, bounded/selective `K`, compact evidence delta |
| Home/Discover settle | `V × C`, `V × R` | visible/missing Story set `V` |
| Point identity | full `R` | indexed point/redirect chain |
| Bulk identity | `V × R` | bulk/indexed resolution |
| Library render | global canonical/mapping/progress | Library `L` and only demanded projections |
| Story render | all Library/progress | one Story |
| Downloads render | all chapters/canonical | download set `D` |
| Progress protection update | all `G` | protected-release projection/delta |
| Reader viewport planning | all `RA/AC` | current viewport + cached pressure state |
| Cache pressure victim removal | global reread per victim | one candidate snapshot + point currentness check |
| Reader encoded image handoff | repeated `Y` copies | bounded ownership/streaming with integrity preserved |
| Chapter page aggregation | complete prefix reprocessed each page | page delta + maintained matching state, or equivalent bounded update |
| Chapter commit | all cumulative links + 2 graph snapshots/page | changed delta + minimal coherent notification evidence |
| Periodic background continuation | full `Lb` each batch | keyset/bounded next batch |
| Plugin operation discovery | aggregate `main.js` bytes `JsB` | manifest metadata for candidate `Pl`; executable loaded only on invoke |
| Bundled provisioning warm/terminal state | all bundled bytes `Bp` per runtime call | descriptor/state comparison; package bytes only for needed install/update; terminal outcome stable until identity/state change |
| Plugin credential injection | all plugin manifests + duplicate secure reads + `Cr` key lookups | one plugin policy + one coherent session snapshot + one key acquisition/store operation |
| Hidden retained destination | active query/reducer work | approximately zero semantic foreground demand |
| Durable canonical work | multiple executors | one owner/join contract |
| Recovery | interactive latency tied to historical `Q` | separately budgeted recovery lane |

---

# 16. Self-review of this audit

The following checks were applied after the whole-app pass.

## 16.1 Duplicate-family review

- X8, X9, X10 are intentionally separate:
  - X8 = global accounting on a viewport-local read path;
  - X9 = global candidate/re-read work during eviction;
  - X10 = lock scope/serialization across I/O.
- X13 and X14 are intentionally separate:
  - X13 = CPU aggregation complexity;
  - X14 = storage/transaction/readback amplification.
- X15 is not “global maintenance is bad”; it is repeated global selection per continuation.
- Settings aggregate-via-list is an A8 call site, not a new family.
- Story-wide `DownloadViewModel.statuses` and one-Story Library membership are additional A8 call sites, not new root families.
- merge-reversal nested redirect scanning is an additional A6 call site, not a separate redirect root family.
- `CatalogMetadataCoordinator.suppressions` is a second confirmed X12 call site; `inFlight` is excluded because it self-prunes on completion.
- missing progress order index is part of X2, not a separate “missing index” family.
- broad UI Main-context reductions are folded into broadened L1 rather than one finding per ViewModel.
- X16, X17 and X18 are intentionally separate: X16 = control-plane physical scope, X17 = retry/failure lifetime ownership, X18 = duplicate secure-session/Keystore work on one authenticated request.
- all-plugin authentication-policy enumeration and `enabled(operation)` script materialization share X16 because both answer bounded metadata questions through wider physical package reads; X18 starts only after the policy is selected and concerns duplicate secure-session work.

## 16.2 False-positive review

Not promoted:

- retained top-level composition itself;
- JavaScript fresh-isolate policy;
- one secure session read/decrypt per authenticated request after X18; this remains `RISK-PLUGIN-AUTH-CACHE` until measured;
- Reader checksum verification;
- dormant backdrop implementation;
- file lock maps that prune idle entries;
- global scans performed once in explicit maintenance;
- namespace-scoped automatic-cache queries merely because no explicit `namespace` annotation index appears — the composite primary key already supplies a usable prefix index.

## 16.3 Severity confidence

Static production-path audit establishes scaling/ownership properties, but exact device severity still needs the benchmark matrix. P0/P1 labels here are **structural priority**, not measured milliseconds.

## 16.4 Coverage gap remaining after this pass

No static audit can prove:

- actual SQLite scheduler/contention timing on target devices;
- Compose inactive-nav lifecycle subscriber behavior;
- JS isolate startup cost on production WebView implementation;
- real provider/token collision distributions;
- Android heap/GC behavior at specific image sizes and concurrency;
- Android Keystore/file cost after duplicate session work is removed;
- real bundled failure-state frequency and plugin package-size distribution.

Those are deliberately recorded as measurement work rather than guessed as confirmed bugs.

---

# 17. Final baseline statement

The whole-app performance problem is broader than the previous Catalog-centered model.

Hikari currently has several places where **local user work becomes coupled to historical/global state**:

```text
Catalog:       N / B / R / Q
Progress:      G
Reader cache:  RA / AC
Chapter sync:  cumulative J / Z / Pg
Background:    repeated full Lb selection
Memory:        Y copies and process-lifetime memoization
Plugin runtime: Pl / JsB / Bp / Cr / Hr
```

It also has cross-cutting ownership mismatches:

```text
reactive invalidation domain != semantic invalidation domain
critical-section lifetime      != state-mutation lifetime
batch boundary                 != storage/algorithm boundary
foreground work owner          != durable worker owner
retained composition lifetime  may != reactive demand lifetime
plugin control-plane scope       != executable/package payload scope
terminal failure lifetime        != retry-at-every-call lifetime
secure request snapshot          != repeated file/key reads
```

Therefore the big update should **not** be organized as “optimize Search, then Reader, then Chapters.”

It should be organized around the eight architectural themes in Section 13 and validated by the whole-app measurement matrix before/after each structural change.

The correct next design stage is:

1. accept/freeze this whole-app audit baseline;
2. define benchmark fixtures and complexity budgets for every global dimension;
3. design solution families per architectural theme;
4. self-review interactions so one repair does not move the bottleneck elsewhere;
5. only then split implementation into phases/tasks.

**Revised whole-app confirmed root-cause family count: 33.**


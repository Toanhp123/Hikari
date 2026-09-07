# Hikari Performance Architecture Audit Baseline — Red-Team Revision

**Snapshot audited:** `Hikari-perf-discover-end-to-end.zip`  
**Audit date:** 2026-09-06  
**Red-team revision date:** 2026-09-07  
**Purpose:** Preserve the current performance-audit findings as a stable baseline for later solution analysis, architecture design, and phase planning. This revision incorporates an adversarial self-review that challenged the original taxonomy, removed duplicated root causes, promoted previously misclassified effects, and added newly confirmed runtime-path findings.

> This document intentionally records **what is wrong and why**. It does **not** yet prescribe the final solution architecture or implementation phases.  
> The goal is to prevent a symptom-by-symptom optimization cycle and instead preserve the causal model of performance problems.

---

# 1. Executive Summary

The current Hikari performance problem is not best described as “a few slow functions.”

The original audit identified the correct broad direction, but a later adversarial/self-review found that its taxonomy was still too Catalog/foreground-centric and that several findings were either over-counted, under-classified, or missing entirely.

The revised audit now distinguishes:

1. **Confirmed structural root-cause families** — runtime-path defects directly supported by the production call graph.
2. **Structural/scaling risks** — architectures that permit pathological scaling but still require workload evidence before being called measured defects.
3. **Secondary/local amplifiers** — real duplicate work that is downstream of a stronger root cause.
4. **Amplification effects** — user-visible or cross-feature consequences that should not be patched independently.
5. **Optimization opportunities / non-bugs** — potentially useful improvements not currently proven to be root causes.
6. **Measurement gaps** — missing benchmarks that could otherwise allow structural regressions to remain invisible.

The most important corrections to the original baseline are:

- **A4 candidate fan-out** is retained as a **structural worst-case risk**, not counted as a confirmed root cause until corpus benchmarks demonstrate actual production impact.
- **A9 duplicate reconciliation indices** is removed as an independent root cause. The two indices have different semantics/lifetimes; their repeated global reconstruction is better represented under A1 and the missing compact/versioned reconciliation-evidence boundary.
- **E1 Search → Home wake-up** is no longer treated as merely an effect. It reveals a separate **reactive invalidation-domain mismatch** that also appears in `chapter_storage_entries`.
- **L6 canonical fusion fragmentation** is strengthened: a single fusion rebuild can repeatedly hydrate/hash the same source evidence and repeatedly resolve Story identity.
- The audit is expanded beyond Catalog to include **application-scoped coordinators**, **Reader progress**, **storage invalidation**, **durable canonical work ownership**, **recovery backlog leakage**, and **batch-via-point reconciliation**.

Current revised census:

| Category | Count / status |
|---|---:|
| Confirmed structural root-cause families | **23** |
| Structural/scaling risks | **3** |
| Secondary/local amplifiers | tracked separately |
| Amplification effects | tracked separately |
| Optimization opportunities / non-bugs | not counted as defects |
| Measurement gaps | tracked separately |

> **Important:** “23 confirmed root-cause families” means 23 independent architectural causes visible in the production call graph. It does **not** mean 23 benchmarked regressions or 23 independent user-facing bugs.

The most important structural conclusion remains:

> Performance work should be planned from **complexity contracts, ownership boundaries, invalidation domains, and causal graphs**, not from isolated hot files.

A second conclusion is now equally important:

> The cost model must include not only row counts (`N`, `R`, `G`) but also **data width (`B`)**, **durable backlog (`Q`)**, **Story source count (`S`)**, and **subscription cardinality (`H`)**.


# 2. Audit Taxonomy

The audit uses four primary categories.

## 2.1 Algorithmic / Complexity Defect

A defect is classified here when the cost grows with a dimension that the operation should not fundamentally depend on.

Examples:

- a point lookup depending on total canonical catalog size;
- a bounded Story set depending on the entire redirect table once per Story;
- a page of `M` search results rebuilding an `N`-sized index repeatedly;
- provider work cloning/rebuilding the same `N`-sized index.

This category changes the **scaling law**, not merely the constant factor.

---

## 2.2 Logic / Scheduling / Lifecycle Defect

The total algorithmic work may retain the same Big-O order, but the system performs it:

- on the wrong dispatcher;
- more than once;
- serially when independent work could overlap;
- for hidden UI;
- with unnecessarily fragmented DB calls;
- without single-flight coordination.

These defects primarily affect:

- main-thread jank;
- wall-clock latency;
- CPU duplication;
- battery;
- DB round-trips;
- unnecessary subscriptions.

---

## 2.3 Amplification Effect

An effect is **not counted as an independent root cause** when it is a downstream consequence of one or more defects.

Examples:

- Search invalidates Home and causes Home to reload.
- Hidden destinations make a single Room invalidation wake multiple screens.
- `WhileSubscribed` appears ineffective because hidden destinations remain subscribed.
- Search jank is the visible result of Main-thread execution multiplied by global-index rebuild cost.

Effects must be recorded because they describe real user impact, but they should not be patched independently unless the underlying root cause has been addressed.

---

## 2.4 Intentional Trade-off / Optimization Opportunity

Not every expensive-looking behavior is a bug.

Examples:

- a global scan in an explicit maintenance job;
- sorting a small bounded page;
- absence of HTTP disk cache;
- a legacy matcher that has no production caller.

These are kept out of the root-cause count unless evidence shows they materially participate in the runtime path being audited.

---

# 3. Complexity Variables

To reason consistently about cost, this audit uses the following variables.

| Variable | Meaning |
|---|---|
| `N` | Total persisted catalog source records / `catalog_entries` footprint |
| `I` | Total external identifier rows |
| `B` | Total metadata bytes decoded, normalized, and/or fingerprinted along a catalog read path |
| `P` | Number of enabled catalog providers |
| `M` | Number of items returned by the current Search/Refresh operation |
| `U` | Number of new Stories created from those `M` items |
| `K` | Number of reconciliation candidates for one incoming item |
| `R` | Number of Story redirect rows |
| `V` | Number of Story IDs a UI operation needs to resolve/project |
| `C` | Total canonical-catalog footprint needed by a global canonical read |
| `L` | Number of Stories in the user's Library |
| `D` | Number of download/release items needed by Downloads UI |
| `E` | Number of evidence changes produced by a commit |
| `H` | Number of top-level destinations kept lifecycle/composition-active |
| `S` | Number of source-evidence rows belonging to one Story |
| `G` | Total persisted reading-progress rows/history |
| `Q` | Pending durable canonical/outbox backlog visible to recovery/materialization |
| `W` | Number of persisted/debounced reading-progress writes over a workload |

The desired architecture should make foreground work depend primarily on **operation-local dimensions** such as `M`, `V`, `L`, `D`, and selective `K`.

Current problematic paths frequently depend on **historical/global dimensions** such as `N`, `C`, `R`, `G`, `Q`, or total metadata width `B`.

This revision also distinguishes **row-count complexity** from **data-width complexity**:

```text
two paths may both be O(N)
but one can be dramatically more expensive if it eagerly decodes/hashes O(B)
```

Therefore `N` alone is not a sufficient performance model for the catalog runtime.


# 4. Algorithmic / Complexity Defects

## A1. Search, Refresh, and unowned Details rebuild a global reconciliation index

### Runtime pattern

The following production flows construct reconciliation context from the complete persisted catalog:

- `CatalogSearchService`
- `CatalogRefreshService`
- `CatalogDetailsLoader.resolveUnowned()`

The repository path loads all catalog entries and all identifiers, then builds `CatalogIngestReconciliationIndex`.

Conceptually:

```text
foreground operation
    -> repository.sourceRecords()
    -> all catalog entries
    -> all identifiers
    -> rebuild reconciliation candidate structures
```

### Cost

Approximate cost per global build:

```text
DB materialization: O(N + I)
index construction: O(N log N)   [depending on actual build structures]
```

The important issue is not the exact constant or sort implementation. It is that **every user-level Search/Refresh operation pays cost proportional to historical catalog size**.

### Classification

**Algorithmic / data-scope defect — P0**

### Why it matters

A Search page containing 20 items should not become progressively slower because the user accumulated tens of thousands of unrelated catalog records over previous months.

---

## A2. `fork()` rebuilds most of the ingest index per provider

The current `fork()` behavior constructs a new `CatalogIngestReconciliationIndex` from the current records rather than maintaining a lightweight delta or copy-on-write view.

Conceptually:

```text
base index over N records
    -> provider 1 fork -> rebuild
    -> provider 2 fork -> rebuild
    -> ...
    -> provider P fork -> rebuild
```

### Approximate cost

```text
base build:
O(N log N)

provider-local forks:
~ O(P × N log N)
```

Exact cost depends on provider sequencing and data accumulated in each local fork, but the architecture scales with both historical catalog size and provider count.

### Classification

**Algorithmic defect — P0**

### Key distinction

This is not merely “copying a few objects.”  
The fork abstraction currently hides **global-index reconstruction**.

---

## A3. Creating each new Story scans all existing Story IDs

When a new Story ID is required, the caller reconstructs the set of existing Story IDs from all current reconciliation records.

Conceptually:

```text
for each new incoming Story:
    scan all records
    extract all existing Story IDs
    build a Set
    call StoryIdFactory.create(...)
```

If `U` new Stories are created against an existing catalog of size `N`:

```text
O(N) + O(N+1) + O(N+2) + ...
≈ O(U × N + U²)
```

When `U` grows in proportion to `N`, this can trend toward quadratic behavior.

### Classification

**Algorithmic defect — P0**

### Important root cause

`CatalogStoryIdFactory` using Set membership is not itself the problem.

The defect is that the **caller rebuilds the Set from scratch for every creation** instead of maintaining an incremental `existingStoryIds` structure.

---

## A4. Candidate-index fan-out has no strict worst-case bound — structural risk

Reconciliation candidates are collected through evidence such as:

- identifiers;
- normalized title tokens;
- author information.

The current title tokenization is simple and candidate collection does not establish a strong architectural bound through:

- candidate caps;
- stop-word rules;
- posting-list selectivity thresholds;
- staged strong-evidence → weak-evidence pruning.

Let:

```text
K = candidate count for one incoming item
```

Ranking and evidence evaluation then cost approximately:

```text
O(K × comparisonCost + K log K)
```

In the worst case:

```text
K -> N
```

A page of `M` incoming items may therefore degenerate toward:

```text
O(M × N)
```

in addition to other global-index costs.

### Classification

**Structural/scaling risk — not counted in the 23 confirmed root causes**

### Confidence

The architecture permits this degeneration.

Actual production severity requires a corpus benchmark using common titles/tokens and adversarial candidate collisions. The architecture permits `K -> N`, but the current static audit does not prove that production workloads commonly reach that region.

---

## A5. Point projection lookup performs a global canonical read

The projection repository's default `find(storyId)` behavior is conceptually equivalent to:

```text
observe all ready canonical Stories
take first emission
find one Story in the full list
```

Room does not provide a true point lookup override for this path.

Therefore:

```text
find(one Story)
```

has cost proportional to the global canonical footprint:

```text
O(C)
```

### Confirmed affected production paths

- Discover canonical bootstrap/settlement
- Content Mapping preparation
- Content Mapping URL resolution

In Discover, if this is repeated for `V` Stories:

```text
O(V × C)
```

### Classification

**Algorithmic / data-scope defect — P0**

### Important detail

In the Discover path, canonical `ensureReady()` already returns a `Ready` state containing enough data to build a projection.

Querying the global canonical catalog again is therefore especially redundant.

---

## A6. Resolving one Story identity reads the full redirect table

`RoomStoryIdentityResolver.resolve(storyId)` currently materializes the full Story redirect set, builds a lookup map, and then resolves one Story.

Conceptually:

```text
resolve(one Story)
    -> SELECT all redirects
    -> associate all redirects
    -> follow chain for one Story
```

Therefore:

```text
point identity resolve = O(R)
```

instead of being proportional to the redirect chain or a cached/bounded structure.

### Systemic impact

Identity resolution is used throughout:

- canonical orchestration;
- reconciliation;
- fusion;
- catalog repositories;
- Library;
- Content Mapping;
- chapters;
- reading progress;
- merge paths.

Thus a seemingly small per-call cost is multiplied widely across the system.

### Classification

**Algorithmic / data-scope defect — P1 systemic**

---

## A7. Resolving a set of Story IDs creates one full redirect observer per Story

The current bulk helper is conceptually:

```text
combine(
    storyIds.map { observeResolved(oneStory) }
)
```

Each individual observer in Room observes the full redirect table.

For `V` Story IDs:

```text
V flows
× full redirect-table observation
× full redirect map construction
```

Subscription or redirect invalidation cost approaches:

```text
O(V × R)
```

rather than a desirable shape closer to:

```text
O(R + V)
```

### Affected bounded-observation families

This pattern appears underneath multiple systems including:

- canonical projections;
- reading progress;
- chapters;
- content mappings.

### Classification

**Algorithmic defect — P1 systemic**

### Architectural implication

Several upper-layer APIs that appear “bounded by Story IDs” still pay a global identity-resolution tax below the abstraction boundary.

---

## A8. Bounded UI problems use global observations

A recurring UI/data-access pattern is:

```text
UI needs a small known Story set
    but subscribes to entire datasets
    then filters locally
```

### Library

Library already has membership information that identifies relevant Story IDs, but still observes global datasets such as:

- canonical catalog;
- all mappings;
- all reading progress.

The local reducer then builds maps/groups from these global datasets on every emission.

Current cost depends on:

```text
O(C + totalMappings + totalProgress)
```

instead of primarily:

```text
O(L)
```

### Story

A Story screen needing one Story may derive data from:

- all Library memberships;
- all progress rows.

### Downloads

Downloads can subscribe to:

- all chapter data;
- global canonical catalog;

while only a bounded set corresponding to download records is needed.

### Classification

**Algorithmic data-scope defect — P1**

### Important validation

This is not an unavoidable repository limitation.

Other Hikari flows such as Home/Updates already use the healthier pattern:

```text
membership/visible IDs
    -> bounded Story IDs
    -> observeForStories(ids)
```

Therefore the architecture already contains a working bounded-reference implementation pattern.

---

## Historical note — former A9 removed from independent root-cause count

The original audit counted Search ingest reconciliation and canonical reconciliation candidate indexing as a separate root cause because both can rebuild over the global source-evidence corpus.

The red-team review found this classification too coarse.

The two structures have different semantics and lifetimes, so their coexistence is not itself a defect. The performance concern is better represented by **A1**:

> foreground/recovery paths repeatedly reconstruct or rematerialize global reconciliation evidence instead of operating against an appropriately compact, versioned, or otherwise reusable evidence boundary.

Therefore the former A9 finding is retained only as a **diagnostic note** and is **not included in the revised root-cause census**.


# 5. Logic / Scheduling / Lifecycle / I/O Defects

## L1. Search CPU orchestration runs from Main-thread ViewModel context

`SearchViewModel` launches work from `viewModelScope` and calls the search service without an execution-boundary dispatcher switch.

Raw Room and network operations may use their own executors, but CPU work after suspension can resume on the caller context.

Potential Main-thread work includes:

- JSON decoding and mapping;
- reconciliation index construction;
- candidate processing;
- sorting/grouping;
- canonical orchestration;
- search-result projection.

### Complexity

Moving work to `Dispatchers.Default` does **not** change Big-O.

An `O(N log N)` algorithm remains `O(N log N)`.

### Classification

**Execution-context logic defect — P0 UX**

### User-visible effect

- frame jank;
- responsiveness loss;
- ANR exposure under large catalogs;
- contention with Compose state publication.

---

## L2. Hidden top-level destinations remain composed and lifecycle-active

`PersistentTopLevelNavDisplay` intentionally keeps visited top-level routes in composition.

Hidden routes are suppressed in layout/semantics but are not necessarily:

- disposed;
- lifecycle-lowered in a way that stops collectors;
- removed from their independent top-level navigation stack.

As a result, hidden destinations may retain:

- `collectAsStateWithLifecycle()` collectors;
- Room observers;
- Compose state;
- Lazy layout state;
- image/artwork state;
- effects;
- recomposition dependencies.

### Complexity

This does not turn an `O(N)` algorithm directly into `O(N²)`.

Instead it increases the number of concurrently active work consumers:

```text
H = number of active retained destinations
```

### Classification

**Lifecycle logic defect — P0**

### Why severity is high

It multiplies other defects:

- global observation;
- Room invalidation;
- canonical updates;
- UI reductions.

---

## L3. Discover bootstrap and manual refresh are not single-flight

Automatic bootstrap and manual refresh are guarded by different Job fields.

Both can call the same refresh pipeline.

Therefore a user-triggered manual refresh during bootstrap can create two concurrent refresh executions.

Potential duplicated work:

- provider fetches;
- global reconciliation-index construction;
- DB commits;
- canonical settlement.

### Complexity

Usually the same Big-O work is duplicated rather than changing asymptotic order.

### Classification

**Concurrency / duplicate-work logic defect — P1**

---

## L4. Search waits for canonical settlement serially

Foreground canonical readiness is processed sequentially for a bounded number of Stories.

Wall-clock latency approximates:

```text
T1 + T2 + ... + Tv
```

rather than bounded-concurrency waves:

```text
~ ceil(V / concurrency) waves
```

### Total work

The total amount of canonical work may remain similar.

Thus this is not primarily a Big-O defect.

### Classification

**Scheduling / wall-clock latency defect — P1**

### Additional publication issue

Search currently returns a final result after service completion instead of exposing an explicit staged/provisional result stream.

This delays visible feedback even when provisional displayable information is already available.

---

## L5. Canonical fusion can run once per evidence change rather than once per final affected Story

The canonical orchestrator processes immediate evidence changes individually.

If multiple changes affect the same Story during one logical batch:

```text
change 1 -> reconcile -> fusion Story A
change 2 -> reconcile -> fusion Story A
change 3 -> reconcile -> fusion Story A
```

Source-level reconciliation may legitimately need per-change handling, but the final fusion rebuild can be repeated unnecessarily.

Let:

```text
E  = number of evidence changes
Ue = number of unique affected Stories
```

Current fusion work can approach:

```text
E × fusionCost
```

while a more consolidated architecture could preserve source reconciliation semantics and approach:

```text
per-source reconciliation
+
Ue × final fusionCost
```

### Classification

**Duplicate-work orchestration defect — P1**

---

## L6. Canonical fusion repeatedly rehydrates the same Story evidence and fragments ownership checks

One fusion rebuild obtains Story/canonical/source state through several repository calls, conceptually including:

- canonical state;
- source records;
- active generation;
- source preference;
- identity revision;
- currentness validation;
- obsolete-generation cleanup.

The red-team pass confirmed a stronger pattern than the original wording captured.

A successful promotion path can approximately resemble:

```text
canonical.state()
    -> identity.resolve
    -> Story/state
    -> source records + identifiers
    -> identity + fusion fingerprints

canonical.sourceRecords()
    -> identity.resolve
    -> SAME source records + identifiers
    -> SAME fingerprints again

activeGeneration()
    -> identity.resolve
    -> canonical/generation/provenance

sourcePreference()
    -> identity.resolve
    -> canonical state

identityRevision()
    -> identity.resolve
    -> canonical state

persistCandidateIfCurrent()
    -> identity.resolve
    -> canonical state
    -> SAME source records/fingerprints again for currentness checks
    -> ownership/provenance checks
    -> generation persistence

cleanupObsoleteGenerations()
    -> identity.resolve
```

`canonical.state()` can already construct `CanonicalStoryState.Ready.sources`, yet the fusion path then asks for `sourceRecords()` again.

Therefore the same source evidence can be:

- decoded;
- normalized;
- fingerprinted;
- allocated;

multiple times in one logical rebuild.

### Big-O distinction

If the number of repository calls is a fixed constant `Qf`, this does not create a new asymptotic order by itself.

However the constant is large, the hydrated payload is wide (`B`), and A6 makes each repeated identity resolve itself depend on `R`.

The practical cost therefore resembles:

```text
constant × [identity O(R) + source hydration O(S + source-bytes)]
```

with repeated hashing/allocation.

### Classification

**I/O / CPU / repository-granularity amplification defect — P1**

### Interaction with other findings

- **A6** multiplies each identity lookup by global redirect cost.
- **X3** multiplies each source hydration by wide metadata decoding/fingerprinting.
- **X5** can cause the same entire fusion rebuild to execute concurrently in foreground and durable worker paths.


## L7. Search commit uses per-entry DAO statement calls

Search summaries are committed entry-by-entry rather than primarily through batched DAO operations.

For `M` entries, writing `M` rows is inherently `O(M)`.

The defect is the number of database API/statement invocations, not the row-count asymptotics.

Current pattern resembles:

```text
for each entry:
    DAO statement
    possibly Story write
    possibly canonical-state setup
```

### Classification

**Database call amplification — P1/P2**

### Important distinction

A batched implementation would not magically make row persistence `O(1)`.

It reduces:

- transaction overhead;
- statement setup;
- context switching;
- invalidation pressure;
- JNI/SQLite boundary cost.

---

## L8. Library dependency policy controls presentation readiness but not subscription demand

Library already computes which local dependencies are semantically required for the current UI state.

For example, some dependencies are only necessary when:

- a search query is active;
- a particular sort is active;
- a source filter is active.

However the flows may still subscribe to global datasets regardless of whether those datasets are needed.

Thus the policy currently decides:

```text
which data blocks UI readiness
```

but not:

```text
which data should be loaded/observed at all
```

### Classification

**Demand-driven execution logic defect — P1**

### Interaction with A8

A8 makes these unnecessary observers global.

L8 makes them active even when the UI does not semantically need them.

Together they create unnecessary foreground/background database work.

---

# 6. Data-Lifetime / Performance-Aging Defect

## D1. Search evidence causes historical catalog size to grow without a clear transient-retention policy

Search results are intentionally persisted as source/canonical evidence.

This is not merely an accidental cache.

However the current persistence model does not provide a sufficiently explicit lifetime/provenance distinction between:

- durable identity evidence;
- Home-owned evidence;
- Library/History/Open-Story protected evidence;
- transient search-discovered evidence.

Therefore:

```text
N(t)
```

can increase as the user performs more searches over time.

Because multiple existing algorithms depend on `N`:

- A1 global ingest rebuild;
- A2 provider fork;
- A3 new-Story full scan;
- A4 candidate fan-out risk;
- repeated global reconciliation evidence reconstruction covered by A1;

the system can exhibit:

```text
application age ↑
    -> N ↑
    -> future foreground cost ↑
```

### Classification

**Data-lifetime / performance-aging defect — P0 long-term**

### Critical warning

A simple rule such as:

```text
delete search catalog entries after TTL
```

is unsafe without a proper evidence-lifetime model.

Search evidence currently participates in canonical identity/reconciliation semantics.

Deleting source facts solely because they originated from Search may damage:

- identity continuity;
- canonical ownership;
- redirect/merge reasoning;
- fused Story presentation;
- later evidence reconciliation.

Retention must therefore be designed around **evidence provenance and reachability**, not just query TTL.

---


# 7. Red-Team Root Causes Added by Adversarial Self-Review

The following seven root-cause families were not represented adequately in the original baseline.

They are included in the revised **23 confirmed structural root-cause families**.

## X1. Reactive invalidation domain does not match storage-table domain

Room invalidation is table-granular, while several tables contain records with different semantic lifetimes or reactive domains.

### Catalog example

Home observation depends on `catalog_entries` because Home rows join to catalog entries.

Search also writes search-discovered rows into the same table.

Therefore a search-only write can invalidate Home observation even when Home membership is unchanged.

### Storage example

`chapter_storage_entries` contains both:

```text
EXPLICIT_DOWNLOAD
AUTOMATIC_CACHE
```

Downloads query only explicit downloads, but automatic-cache insert/touch/evict operations still invalidate the entire table.

Therefore Reader/cache activity can wake:

- Downloads list observation;
- completed-download counts;

even when explicit download state did not change.

### Classification

**Reactive storage/invalidation-domain architecture defect — P1 systemic**

### Important distinction

This is not the same as L2 hidden lifecycle.

- **X1** determines which observers are unnecessarily invalidated.
- **L2** determines how many hidden observers remain alive to receive those invalidations.

They multiply each other.

---

## X2. Application-scoped automatic-cache policy scans all reading-progress history

`AutomaticCachePolicyCoordinator` is application-scoped and started from application startup.

It observes all reading progress and derives the set of incomplete-release IDs used for cache protection.

The semantic requirement is approximately:

```text
distinct release IDs whose progress is incomplete
```

but the current path can require:

```text
read all progress rows
order/materialize them
map domain objects
scan/filter all rows
build a Set
distinctUntilChanged()
```

Thus even when the final protected-release set does not change, the global read/reduction has already happened.

Let:

```text
G = total reading-progress history
W = persisted/debounced progress writes
```

Long-lived cost can approach:

```text
W × global-progress-materialization(G)
```

The DAO ordering also does not appear to have a matching index that directly satisfies the current ordering shape.

### Interaction with identity resolution

A persisted progress update can additionally:

```text
find existing progress -> identity.resolve -> O(R)
save progress          -> identity.resolve -> O(R)
write reading_progress
    -> application-scoped observer -> global G scan
```

Therefore one persisted progress update can touch global dimensions `R + R + G`.

### Classification

**Algorithmic data-scope + application-lifetime defect — P1 systemic**

---

## X3. Over-wide read models and eager fingerprint recomputation inflate `O(N)` paths by metadata width `B`

`sourceRecords()` does not materialize a compact reconciliation-only record.

It can hydrate broad catalog metadata including fields such as:

- title/aliases/authors;
- description;
- genres;
- language tags;
- URLs;
- score/popularity;
- publication/update metadata;
- identifiers.

Collection-like fields pass through Room converters and allocations.

Then `toSourceRecord()` can eagerly compute both:

- identity fingerprint;
- fusion fingerprint.

Fingerprinting itself performs normalization, ordering, digest creation, and hex-string materialization.

However ingest reconciliation often needs a much narrower identity evidence subset.

This means a global reconciliation read scales not only with:

```text
N = row count
```

but also:

```text
B = total decoded/normalized/hashed metadata bytes
```

### Projection overhydration

A similar problem appears when constructing canonical ready-state projections.

Projection consumers may need:

```text
Story + active fused generation + health/presentation
```

while the underlying canonical read can still construct `sources`, including source-record hydration and fingerprints, even when projection conversion does not consume them.

### Classification

**Data-width / eager-derived-data architecture defect — P1, major multiplier**

### Why this matters

An optimization that only caches an index but preserves the same over-wide hydration boundary may remove some `N log N` work while leaving substantial:

- JSON decoding;
- normalization;
- SHA work;
- allocation/GC pressure.

---

## X4. Canonical reactive observation mixes trigger queries with repeated rehydration and lacks a coherent snapshot contract

`RoomCanonicalCatalogRepository.observeStory()` uses several Room Flows as triggers and then discards their payloads before calling a separate resolved-state read.

Conceptually:

```text
observe canonical state
observe Story
observe entries
    -> discard payloads
    -> readResolvedState()
        -> query Story/state/entries/identifiers/generation/provenance again
```

Therefore one logical DB change can cause:

- trigger queries;
- followed by full rehydration queries;
- potentially repeated rehydration if several observed tables emit independently.

`mapLatest` may cancel later stages but cannot make already-started query/allocation work free.

### Coherence risk

Global/bounded canonical ready-story observation combines independent Room snapshots for multiple tables before enriching them with additional reads.

A single transaction touching several tables can therefore theoretically produce temporary combinations of:

```text
new state + old Story + old entries
new state + new Story + old entries
new state + new Story + new entries
```

before all upstream flows converge.

Hikari already fixed an analogous Home problem by using:

```text
invalidation as trigger
    -> one transactional coherent read
```

Canonical observation does not yet consistently enforce the same invariant.

### Classification

**Reactive coherence + duplicate-read root cause — P1**

### Confidence split

- Duplicate trigger-query + rehydrate work: **confirmed**.
- User-visible mixed-generation correctness failure: **high-confidence structural risk requiring a regression test**, not yet claimed as a reproduced correctness bug.

---

## X5. Foreground canonical execution and durable worker execution lack exclusive ownership of the same work item

Foreground canonical orchestration can:

```text
mark durable FUSION_REBUILD work runnable
    -> execute fusion rebuild directly
```

without leasing/claiming the durable row for foreground ownership.

A durable worker can independently:

```text
claimReady()
    -> lease same work
    -> execute fusion rebuild
```

If a worker is already active, `scheduleDrain = false` only prevents a new wake-up; it does not prevent the existing worker from claiming the row.

Possible race:

```text
Foreground                     Worker

markDirty(runnable)
                               claimReady()
                               lease row

fusion.rebuild()               fusion.rebuild()
```

Durable transition guards can keep final persisted state correct by rejecting stale completion, but they do not refund:

- CPU;
- DB reads;
- hashing;
- allocations.

If a promotion race is detected, fusion may additionally retry once, further amplifying duplicate work.

### Classification

**Executor ownership / duplicate-concurrency root cause — P1**

### Distinction from L5

- **L5:** one foreground batch rebuilds the same Story repeatedly for multiple evidence changes.
- **X5:** two independent execution owners may rebuild the same durable Story work concurrently.

---

## X6. Historical outbox recovery can leak into foreground Search/Refresh latency

Foreground evidence orchestration performs best-effort outbox materialization.

The materialization limit is large and the query is ordered over pending outbox events generally, not scoped only to the current user action.

Therefore after:

- crash recovery;
- deferred scheduler work;
- previous failures;
- backlog accumulation;

a new foreground Search/Refresh can first perform recovery work for historical events.

Conceptually:

```text
user performs Search/Refresh
    -> materialize up to Q historical pending events
    -> convert/coalesce durable work
    -> persist work queue changes
    -> then continue current foreground canonical work
```

This couples interactive latency to recovery backlog:

```text
Q
```

rather than only to the current request.

### Classification

**Foreground/recovery scope contamination — P1**

### Measurement implication

Fresh-install benchmarks with `Q = 0` cannot expose this defect.

---

## X7. Reconciliation performs batch work through point APIs, creating N+1 and per-Story quadratic behavior

Normal reconciliation obtains a candidate Story set and then can repeatedly perform per-candidate operations such as:

```text
resolve candidate Story identity
load source records for candidate Story
```

If there are `K` candidate Stories, this creates approximately:

```text
O(K) repository/transaction round-trips
+
multiple identity resolves per candidate
```

even when `K` itself is bounded.

### Reevaluation path

A Story with `S` source records can be reevaluated by iterating its source records and invoking normal reconciliation for each.

During correction/ownership work, each reconcile can reload the current Story's source records again.

This can produce a shape approximately like:

```text
outer load S
+
S × inner load S
≈ O(S²)
```

before candidate work is included.

### Classification

**Batch-via-point algorithmic/I/O defect — P1 systemic**

### Typical impact

This is especially relevant to:

- reconciliation reevaluation;
- policy reevaluation;
- maintenance work;
- durable queue throughput.

It may not always produce immediate UI jank, but it can reduce background drain throughput and increase DB contention with foreground operations.

---


# 8. Amplification Effects

These are real performance consequences but are deliberately not counted as independent root causes.

## E1. Search writes can wake Home observation — effect of X1

Home observation depends on `catalog_entries`, while Search also persists into `catalog_entries`.

Room invalidation operates at table granularity.

Therefore:

```text
Search writes unrelated catalog entry
    -> catalog_entries invalidated
    -> Home observer wakes
    -> coherent Home graph may be reread
```

even when the updated entry is not part of the current Home surface.

The original audit classified this only as an effect. The red-team review identified the stronger underlying root cause **X1: reactive invalidation domain does not match semantic data/lifecycle domain**.

Therefore E1 remains as the user-visible/cross-feature effect, while X1 is counted as the independent structural root cause.


## E2. Hidden destinations turn one invalidation into multi-screen fan-out

After the user has visited several top-level destinations:

```text
Discover
Home
Library
...
```

L2 can keep them alive.

Then one DB/canonical update may trigger work in multiple retained screens:

```text
one invalidation
    -> Discover work
    -> Home work
    -> Library work
    -> ...
```

Approximate foreground/background cost becomes:

```text
sum(work of H active destinations)
```

### Root causes involved

- L2 hidden lifecycle;
- A8 global observers;
- Room invalidation boundaries.

---

## E3. `SharingStarted.WhileSubscribed(...)` appears ineffective

Many ViewModels correctly use `SharingStarted.WhileSubscribed`.

However if hidden UI never loses its collector:

```text
subscriber count never reaches zero
```

then the stop timeout never becomes relevant.

### Root cause

**L2 hidden destination lifecycle**

The correct conclusion is not “WhileSubscribed is broken.”

---

## E4. Redirect updates can create an observer avalanche

Because multiple bounded repository APIs ultimately use A7:

```text
redirect table update
    -> projection Story observers
    -> progress Story observers
    -> chapter Story observers
    -> mapping Story observers
    -> ...
```

Each may process the full redirect table.

Aggregate invalidation work can approach:

```text
O(total observed Story IDs × R)
```

### Root causes

- A6 point identity global read;
- A7 per-Story global redirect observation.

---

## E5. Visible Search jank is a compound effect

Search jank can be produced by the interaction:

```text
L1 Main-thread execution
    ×
A1 global ingest rebuild
    ×
A2 provider forks
    ×
A3 new-Story scans
    ×
A4 candidate fan-out
    ×
L5 canonical work + A1-style global reconciliation reconstruction
```

Moving Search orchestration to `Dispatchers.Default` can improve UI responsiveness but would not remove:

- CPU cost;
- DB cost;
- battery cost;
- total latency;
- catalog-size scaling.

Therefore “move to Default” alone would be a symptom-oriented mitigation, not a complete performance fix.

---

## E6. The app can become slower as it ages

D1 causes:

```text
N(t) ↑
```

which amplifies:

```text
A1  O(N log N)
A2  O(P × N log N)
A3  O(U × N)
A4  worst-case O(M × N)
A1  additional global reconciliation-evidence reconstruction when initialization/recovery requires it
```

This creates an aging-performance profile:

```text
same user action today
<
same user action after months of accumulated evidence
```

unless the architecture bounds or compacts historical evidence cost.

---

# 9. Causal Graph — Search

```text
                      D1 Search evidence retention
                                |
                                v
                        N grows with usage
                                |
              +-----------------+------------------+
              |                 |                  |
              v                 v                  v
        A1 global ingest   A3 Story-ID scan   A4 fan-out risk
        O(N ...)           O(U × N)           worst K -> N
              |
              +-----> X3 over-wide hydration / hashing B
              |
              v
        A2 provider fork
        repeated global reconstruction
              |
              v
        search commit
              |
              +--> L7 per-entry DB calls
              |
              +--> X1 table-level invalidation mismatch
                        |
                        v
                  E1 Home wake-ups
                        |
                        v
                  L2 hidden screens
                        |
                        v
                  E2 multi-screen work

Meanwhile:

search evidence changes
        |
        +--> X6 historical outbox recovery Q
        |
        v
canonical orchestrator
        |
        +--> L5 repeated fusion per evidence
        |
        +--> A6 identity resolution O(R)
        |
        +--> L6 repeated source hydration/currentness reads
        |
        +--> X3 repeated decode/hash B
        |
        +--> X5 foreground/worker ownership race
        |
        +--> X7 batch-via-point reconciliation

Entire CPU orchestration
        |
        v
L1 caller context / Main-thread exposure
        |
        v
visible jank / latency / battery / DB pressure
```

The key insight is that Search latency and jank are compound effects of several independent dimensions:

```text
N = historical catalog rows
B = catalog metadata width / hashing payload
R = redirect graph
Q = historical durable backlog
P = providers
K = candidate cardinality
```

A dispatcher-only fix would address only L1.


# 10. Causal Graph — Cold Discover

```text
V visible Story IDs
       |
       +--> projection live observation
       |          |
       |          v
       |      A7 resolve-set fan-out
       |      O(V × R)
       |
       +--> settlement seed observation
       |          |
       |          v
       |      second bounded projection path
       |      (local duplicate-work amplifier)
       |
       v
missing canonical Story
       |
       v
ensureReady()
       |
       v
CanonicalStoryState.Ready already exists
       |
       v
projection.find(storyId)
       |
       v
A5 global canonical read O(C)
       |
       +--> X3 over-wide canonical/source hydration B
       |
       +--> X4 reactive rehydration / coherence risk
       |
       × V
       |
       v
global-cost amplification
```

Cold Discover therefore has several independent cost axes:

```text
identity resolution:      O(V × R)
projection lookup:        O(V × C)
projection data width:    B
duplicate observation:    local 2-path amplifier
canonical rehydration:    X4
```

The duplicate settlement/live projection subscription is a real local amplifier, but it is not counted as a separate core root-cause family because A5/A7/X4 are the stronger abstraction failures.


# 11. Causal Graph — Navigation, Reader Progress, and Background Work

```text
User visits multiple top-level destinations
                |
                v
L2 persistent top-level composition/lifecycle
                |
                v
H active subscriptions
                |
       +--------+--------+
       |        |        |
       v        v        v
   Discover    Home    Library
                        |
                        +--> global catalog
                        +--> global mappings
                        +--> global progress
                             A8 + L8

DB / canonical / storage write
        |
        +--> X1 table-level invalidation mismatch
        |
        v
all active observers may reevaluate
```

Reader progress adds a second application-lifetime path:

```text
persisted/debounced reading progress write
        |
        +--> A6 identity resolution during find/save
        |
        v
reading_progress invalidated
        |
        v
X2 AutomaticCachePolicyCoordinator
        |
        v
global progress history G
        |
        +--> filter incomplete rows
        +--> build protected release Set
        +--> distinctUntilChanged after work
```

Automatic cache storage can also cross-wake Downloads:

```text
Reader automatic-cache touch/insert/evict
        |
        v
chapter_storage_entries invalidated
        |
        v
Downloads explicit-download observers rerun
```

This is why lifecycle, storage schema, and reactive invalidation must be analyzed together rather than by screen.


# 12. Runtime Surfaces Explicitly Excluded from the Bug Count

## 11.1 HTTP disk cache

The plugin runtime creates an `OkHttpClient` in singleton runtime composition.

The confirmed gap is lack of explicit bounded disk-cache policy / richer cache semantics.

However this audit does **not** count that as one of the 23 confirmed structural root-cause families because static evidence does not establish that it is a principal source of the foreground scaling problems above.

Classification:

**Optimization opportunity / transport policy gap**

Not currently a proven performance defect.

---

## 11.2 Global maintenance scans

A global scan in an explicit maintenance/reconciliation maintenance job can be intentional.

The audit distinguishes:

```text
global work in background maintenance
```

from:

```text
global work on every foreground Search/Refresh/point lookup
```

Only the latter is inherently suspicious.

---

## 11.3 Sorting small bounded pages

Operations such as:

- sorting provider lists;
- sorting a bounded page;
- grouping a small visible item set;

are not classified as algorithmic defects merely because they use `sortedBy`, `groupBy`, etc.

Big-O must be evaluated against the actual dimension and expected bound.

---

## 11.4 Legacy matching code without production callers

Some matcher/index code may look expensive in isolation.

Production call-graph inspection found at least some legacy/test-only matching surfaces without active runtime callers.

Their complexity is not included in this audit's production performance census.

This prevents false positives caused by grep-based auditing.

---

# 13. Severity Table

## 13.1 Confirmed structural root-cause families

| ID | Root cause | Type | Structural severity |
|---|---|---|---|
| A1 | Global ingest/reconciliation evidence rebuild on foreground paths | Algorithm/data scope | **P0** |
| A2 | Full ingest-index fork/rebuild per provider | Algorithm | **P0** |
| A3 | New Story creation rebuilds existing-Story-ID set | Algorithm | **P0** |
| A5 | Point projection lookup -> global canonical read | Algorithm/data scope | **P0** |
| L1 | Search CPU work exposed to Main | Execution logic | **P0 UX** |
| L2 | Hidden top-level destinations remain active | Lifecycle logic | **P0** |
| D1 | Search evidence grows historical `N` without safe transient lifetime model | Data lifetime | **P0 long-term** |
| A6 | Point identity resolution reads full redirect table `O(R)` | Algorithm/data scope | **P1 systemic** |
| A7 | Story-set identity resolution creates `O(V × R)` observer work | Algorithm | **P1 systemic** |
| A8 | Bounded UI problems use global observations | Algorithm/data scope | **P1** |
| L3 | Discover bootstrap/manual refresh lack shared single-flight | Concurrency logic | **P1** |
| L4 | Search canonical settlement is serial | Scheduling | **P1 latency** |
| L5 | Canonical fusion repeats per evidence change | Duplicate-work logic | **P1** |
| L6 | Canonical fusion repeatedly rehydrates/rehashes Story evidence | I/O/CPU logic | **P1** |
| L7 | Search commit uses per-entry DAO calls | I/O logic | **P1/P2** |
| L8 | Library subscription demand ignores dependency policy | Demand logic | **P1** |
| X1 | Reactive invalidation domain does not match table/storage domain | Reactive storage architecture | **P1 systemic** |
| X2 | App-scoped cache policy scans global reading-progress history | Algorithm/application lifetime | **P1 systemic** |
| X3 | Over-wide read models + eager fingerprint recomputation | Data-width/CPU/allocation | **P1 multiplier** |
| X4 | Canonical reactive observation double-reads and lacks coherent snapshot contract | Reactive I/O/correctness | **P1** |
| X5 | Foreground and durable worker lack exclusive ownership of same canonical work | Concurrency | **P1** |
| X6 | Historical outbox recovery leaks into interactive foreground work | Recovery/data scope | **P1** |
| X7 | Reconciliation batch-via-point creates N+1 and `O(S²)` reevaluation | Algorithm/I/O | **P1 systemic** |

**Confirmed root-cause family count: 23**

## 13.2 Structural/scaling risks

These are not included in the 23 confirmed defects.

| Risk | Why it matters | Required evidence |
|---|---|---|
| **RISK-A4** — candidate fan-out can approach `K -> N` | Search/reconciliation may degenerate toward `O(M × N)` under common-token collisions | collision-heavy corpus benchmark |
| **RISK-BIND** — bounded reactive `IN (:storyIds)` APIs lack explicit chunk/cardinality contract | large Library/Story sets can create oversized bind lists or poor query plans | large-cardinality integration/scaling tests |
| **RISK-PROVIDER** — Search/Refresh launch enabled providers concurrently without explicit cap | future provider ecosystem can scale isolates/network/memory with `P` | provider-count workload benchmark |

## 13.3 Classification notes

- A9 from the original audit is removed from the independent count.
- E1 remains an effect, but its underlying root cause is now X1.
- Canonical mixed-generation visibility under X4 is a structural correctness risk until reproduced with a regression test.
- Severity values are structural priorities, not measured device-specific regressions.


# 14. Existing Architecture That Is Already Good

The audit also found useful positive reference patterns.

## 13.1 Pure boundaries

`catalog:model` and `catalog:engine` maintain relatively clean pure-JVM architecture boundaries with architecture tests.

This is important because future optimization work can remain isolated from Android/UI concerns.

## 13.2 Discover projection execution boundary

Discover already demonstrates the intended pattern of moving CPU projection work to `AppDispatchers.default`.

This provides a repository-native reference for a future Search execution boundary.

## 13.3 Bounded canonical settlement concurrency

Some canonical settlement already uses bounded concurrency, demonstrating that concurrency limits are part of current architectural vocabulary.

## 13.4 Bounded Story-ID observation pattern already exists

Home/Updates provide an important reference:

```text
membership / visible IDs
    -> Story IDs
    -> bounded repository observation
```

This proves A8 is repairable without inventing a completely foreign architecture.

## 13.5 UI list fundamentals

Stable Lazy keys, bounded surface item counts, and size-aware image requests reduce unrelated rendering noise.

## 13.6 Room / reader foundations

Room WAL and the Reader cache's quota/protection/LRU design are useful examples of explicit resource-lifetime policies.

## 13.7 Macrobenchmark infrastructure exists

The project already has baseline profile and Macrobenchmark infrastructure.

The primary problem is not absence of performance tooling but insufficient coverage of the newly identified scaling dimensions.

---

# 15. Measurement Gaps

Current tests can remain fully green while the architecture degrades at realistic data sizes, backlog states, or lifecycle combinations.

## 15.1 Search benchmark does not execute a real Search query

The current Search reopen benchmark mainly opens/closes the Search surface.

It does not exercise the full Search engine with a query and large persisted catalog.

Therefore it cannot detect A1-A3, A4 risk, A5-A7, L4-L6, D1, X3, X5, X6, or X7.

---

## 15.2 Benchmark fixture is too small for complexity detection

A fixture around tens of Stories cannot meaningfully distinguish:

```text
O(N)
O(N log N)
O(P × N)
O(U × N)
O(M × N)
```

at architectural scale.

---

## 15.3 No metadata-width benchmark

The original benchmark model varies row count but not row width.

X3 requires scenarios where `N` is held constant while metadata width `B` changes:

```text
short metadata
medium metadata
large descriptions/genres/aliases/identifiers
```

This is needed to expose decode/hash/allocation cost independently of row count.

---

## 15.4 No large redirect-set benchmark

There is no clear scaling gate for:

```text
R = 100
R = 1,000
R = 10,000
```

which is required to quantify A6/A7 and their multiplication inside L6/X2/X7.

---

## 15.5 No candidate-collision benchmark

A4 risk requires corpora with:

- unique titles;
- moderately common tokens;
- deliberately collision-heavy titles/authors.

Without this, candidate fan-out remains a structural risk rather than a measured production cost.

---

## 15.6 No navigation retained-state memory/subscription benchmark

The project needs a way to compare:

```text
H = 1
H = 2
H = 3+
```

and observe:

- active Flow collectors;
- Room query activity;
- retained composition memory;
- invalidation work;
- image/Lazy state retention.

---

## 15.7 No application-aging benchmark

D1 and X2 need the same foreground actions measured against progressively larger historical state:

```text
N = 30 / 300 / 3,000 / 30,000
G = 30 / 300 / 3,000 / 30,000
```

The important signal is the slope.

---

## 15.8 No durable-backlog benchmark

X6/X5 require canonical workloads with:

```text
Q = 0
Q = small backlog
Q = medium backlog
Q = recovery-scale backlog
```

and both:

```text
worker idle
worker already draining
```

Fresh-install benchmarks with `Q = 0` cannot expose foreground/recovery coupling or ownership races.

---

## 15.9 No Story source-cardinality benchmark

X7/L6 require varying:

```text
S = number of source records owned by one Story
```

especially for reevaluation/fusion paths.

---

## 15.10 No reactive-coherence regression test for canonical observation

X4 should be tested with multi-table transactional updates that deliberately stress emission ordering.

The required assertion is not only “eventually correct.”

It should verify:

```text
no mixed-generation externally visible canonical state
```

and count redundant rehydration/query work where practical.

---

## 15.11 No bind-cardinality contract test

Bounded `observeForStories(ids)` APIs should be tested against large Story-ID sets so a future A8 repair does not simply replace global observation with an oversized `IN (...)` query failure/performance cliff.


# 16. Recommended Complexity Contracts for Future Analysis

These are not implementation prescriptions. They are audit targets for later design work.

| Operation | Should not scale primarily with | Desired dominant scope |
|---|---|---|
| Search query | total historical `N`, full metadata `B`, total `C`, total `R`, historical backlog `Q` | current page `M`, selective `K`, provider count `P`, compact evidence delta |
| Home refresh | all historical catalog evidence | refreshed item delta |
| Discover cold settle | `V × C` or `V × R` | visible/missing Story set `V` |
| Resolve one Story identity | full redirect table `R` | redirect chain / indexed point structure |
| Resolve Story set | `V × R` | bounded/bulk identity work with explicit cardinality contract |
| Canonical projection | full source evidence when projection does not need it | projection-specific read model |
| Canonical fusion | repeated full source hydration/currentness snapshots | one coherent fusion input + minimal validation snapshot |
| Library render | full canonical/mapping/progress datasets | Library membership `L` |
| Story screen | all Library/progress rows | one Story |
| Downloads render | all chapter/catalog graph or unrelated automatic cache invalidations | download-related set `D` |
| Progress cache-protection update | full reading history `G` | protected-release delta / query-specific projection |
| Hidden top-level destination | active DB/UI collection | approximately zero foreground collection |
| Canonical batch | fusion once per evidence change or multiple executors | one owner per work item; fusion once per final affected Story where semantically safe |
| Recovery materialization | current user action latency tied to historical `Q` | recovery lane decoupled or explicitly budgeted |
| Reconciliation reevaluation | point API repeated over all `S` sources | batch/bulk Story evidence scope |


# 17. Proposed Scaling Sweep for Future Benchmark Work

When the project moves from audit to measurement, performance should be tested across dimensions rather than a single fixture.

## Catalog scale

```text
N = 30
N = 300
N = 3,000
N = 30,000
```

## Metadata width

```text
B = narrow
B = medium
B = wide
```

Use constant `N` while varying aliases, identifiers, descriptions, genres, URLs, and other metadata payload.

## Redirect scale

```text
R = 0
R = 100
R = 1,000
R = 10,000
```

## Reading-progress history

```text
G = 30
G = 300
G = 3,000
G = 30,000
```

## Durable backlog

```text
Q = 0
Q = 100
Q = 1,000
Q = recovery-scale
```

## Provider scale

```text
P = 1
P = 2
P = 4
P = 8
```

## Story source cardinality

```text
S = 1
S = 2
S = 4
S = 8+
```

## Candidate selectivity

```text
low collision
medium collision
high/adversarial collision
```

## Hidden destination count

```text
H = 1
H = 2
H = 3+
```

## Bounded Story-set cardinality

```text
V/L = small
V/L = hundreds
V/L = thousands
```

This should exercise bind-cardinality behavior in bounded reactive APIs.

## Key methodology

Do not ask only:

> “How many milliseconds does one large fixture take?”

Inspect scaling ratios:

```text
T(300)
T(3,000)
T(30,000)
```

and isolate dimensions:

```text
hold N constant, vary B
hold N/B constant, vary R
hold catalog constant, vary G
hold current request constant, vary Q
```

If a point/bounded operation increases with an unrelated historical/global dimension, the complexity contract is violated even if the absolute time remains acceptable on a high-end device.


# 18. Core Architectural Themes

The revised findings collapse into five structural themes.

## Theme 1 — Foreground work is too often global

Foreground actions that should be local/bounded repeatedly depend on:

- complete catalog evidence;
- complete canonical catalog;
- complete redirect graph;
- complete Library/mapping/progress datasets;
- historical durable backlog.

This remains the dominant algorithmic theme.

---

## Theme 2 — Global knowledge exists, but point/bulk access repeatedly reconstructs it

Canonical identity and reconciliation conceptually require global knowledge.

The problem is not the existence of global knowledge.

The problem is that foreground operations repeatedly pay for:

- global redirect materialization;
- global reconciliation-index reconstruction;
- global projection hydration;
- batch work through point APIs;
- repeated Story source hydration.

The required architectural property is:

> maintain global knowledge in a form that supports cheap point/bulk access without rebuilding or overhydrating the whole corpus.

---

## Theme 3 — Read models are broader than consumer semantics

X3/X4/L6 show that repositories frequently hydrate more state than the consumer needs.

Examples:

- reconciliation reads full catalog metadata;
- projection reads can construct source evidence that projection does not use;
- fusion repeatedly reconstructs Story source evidence and fingerprints.

Therefore future design must treat **read-model width** as a first-class performance boundary, not merely row count.

---

## Theme 4 — Lifecycle and invalidation domains do not match semantic work domains

L2/X1/X2 show that:

- hidden UI remains subscribed;
- tables combine data with different reactive lifetimes;
- application-scoped collectors perform global reductions;
- table-level writes wake unrelated consumers.

The architecture therefore needs explicit reasoning about:

```text
who owns the observer?
what semantic state invalidates it?
how long should it live?
what exact projection does it require?
```

---

## Theme 5 — Foreground, durable recovery, and worker execution lack a single clear ownership model

X5/X6 reveal a concurrency/latency theme not visible in the original audit.

Foreground code, durable queue recovery, and worker drains can overlap in ways that preserve eventual correctness but duplicate expensive CPU/DB work.

Future design must establish:

- one execution owner per durable work item;
- explicit foreground-vs-background priority;
- bounded recovery budgets;
- clear handoff/join semantics.


# 19. Audit Rules for Future Work

To prevent regression into hotspot-driven patching, future performance work should follow these rules.

## Rule 1 — Count root causes, not call sites

If the same architectural defect appears in Discover, Library, Mapping, Chapters, and Reader progress, record:

```text
1 root-cause family
+ multiple affected call sites
```

Do not inflate the bug count.

---

## Rule 2 — Prove the production call graph

Do not classify expensive-looking code as a runtime defect until a production caller is established.

Test-only or legacy surfaces remain separate.

---

## Rule 3 — Separate confirmed defects from structural risks

Examples:

- A4 candidate fan-out permits `K -> N`, but requires workload evidence before being called a measured production defect.
- large `IN (:storyIds)` APIs require cardinality tests.
- provider fan-out requires provider-count/resource benchmarks.

A plausible worst case is not automatically a confirmed runtime regression.

---

## Rule 4 — Separate total-work complexity from wall-clock scheduling

Examples:

- serial canonical settlement can be bad latency without changing total Big-O;
- per-entry SQL calls can have large constant I/O cost while remaining `O(M)`;
- Main-thread execution changes responsiveness, not asymptotic complexity.

---

## Rule 5 — Treat effects as causal evidence, not patch targets

Examples:

- Home wakes after Search;
- Downloads wakes after automatic-cache touch;
- ineffective `WhileSubscribed`;
- hidden-screen DB churn;
- Search jank.

Trace them back to X1/L2/A8/etc. before changing the symptom.

---

## Rule 6 — Evaluate cost against the correct dimension

`O(N)` is not inherently bad.

The relevant question is whether the operation should depend on `N` at all.

Likewise, include:

- `B` for payload width;
- `G` for reading history;
- `Q` for durable backlog;
- `S` for Story source count;
- `H` for active subscriptions.

---

## Rule 7 — Include application age and recovery state

A fresh-install benchmark can miss:

- D1 catalog evidence growth;
- X2 reading-history growth;
- X6 durable backlog;
- X5 worker/foreground overlap.

Performance contracts must include aged and recovery-state workloads.

---

## Rule 8 — Audit read-model width, not only query count

A single “bounded” query may still be expensive if it:

- decodes wide metadata;
- constructs unused source models;
- computes fingerprints eagerly;
- allocates large temporary collections.

---

## Rule 9 — Every reactive API needs an invalidation and cardinality contract

For each observer, explicitly answer:

```text
which semantic changes should wake it?
which tables currently wake it?
how many IDs can it accept?
does it chunk/batch?
does it expose coherent transactional state?
```

---

## Rule 10 — Every durable work item needs an execution-ownership contract

For foreground/worker systems, explicitly answer:

```text
who owns the work now?
can another executor claim it?
can foreground join an existing lease?
can worker join foreground work?
what happens when the work is dirtied while leased?
what recovery work is allowed on an interactive path?
```


# 20. Open Questions Reserved for the Next Design Stage

The audit deliberately does not answer these yet.

1. What is the correct lifetime model for search-discovered source evidence?
2. Which evidence must remain permanently identity-relevant?
3. What compact/versioned reconciliation-evidence boundary should replace repeated full-catalog reconstruction?
4. Should reconciliation use:
   - a shared in-memory/versioned index,
   - a persistent compact index,
   - repository-side SQL candidate lookup,
   - or a hybrid?
5. How should existing Story IDs be maintained so new Story creation does not scan all records?
6. How should candidate selectivity be bounded without degrading identity quality?
7. How should canonical identity redirects support efficient:
   - point lookup,
   - batch lookup,
   - observation,
   - merge invalidation?
8. What cardinality/chunking contract should `observeForStories(ids)` APIs enforce?
9. What projection-specific read models should exist so canonical UI projection does not hydrate unused source evidence?
10. What coherent transactional observation contract should replace X4's independent-flow rehydration pattern?
11. Can top-level navigation uncompose hidden destinations while retaining:
    - back stacks,
    - `ViewModelStore`,
    - saveable UI state,
    - expected Navigation 3 semantics?
12. Which tables should be split or which reactive views should be introduced to align storage invalidation domains with semantic lifetimes?
13. Should automatic-cache protection observe a query-specific protected-release projection rather than all reading progress?
14. Should canonical fusion consume one coherent fusion snapshot rather than repeatedly calling repository point APIs?
15. Should canonical fusion be coalesced per Story within a commit batch?
16. How should foreground canonical execution and durable workers establish exclusive ownership/join semantics?
17. What amount of historical outbox recovery, if any, is allowed on an interactive foreground path?
18. Which reconciliation paths need explicit bulk APIs to remove N+1 and `O(S²)` reevaluation?
19. What provisional/canonical publication contract should Search expose?
20. Which data sources should Library subscribe to only on demand?
21. What exact performance budgets should be enforced for each complexity contract?
22. Which repairs must precede benchmark baselining so the benchmark itself does not encode broken architecture?
23. Which changes are independent enough to phase separately without creating temporary duplicate architecture?
24. Which structural risks must be benchmarked before they are promoted to confirmed defects?

These questions should drive the next architecture/design session.


# 21. Final Baseline Statement

The revised performance audit should be summarized as follows:

> Hikari's main performance risk is not isolated expensive code.  
> It is a mismatch between **operation scope, data scope, read-model width, reactive invalidation scope, and execution ownership**.

Foreground and application-lifetime paths repeatedly pay costs tied to historical/global dimensions:

```text
N = historical catalog size
B = catalog metadata width
C = canonical catalog size
R = redirect-table size
G = reading-progress history
Q = durable recovery backlog
```

for operations whose useful result is often bounded by:

```text
M = current result page
V = visible Story set
L = Library membership
D = download-related set
S = one Story's source evidence
```

The revised causal model adds two principles that were underrepresented in the original audit:

1. **Reactive invalidation must align with semantic lifetime/domain**, not merely table layout.
2. **Foreground and durable/background executors require explicit exclusive ownership or join semantics**, not only eventual-correctness guards.

The next step should therefore be:

1. preserve this revised audit baseline;
2. analyze solution families per root-cause theme;
3. compare alternative architectures and their trade-offs;
4. detect interactions/conflicts between candidate fixes;
5. define target complexity/invalidation/ownership contracts;
6. promote structural risks to confirmed defects only when evidence supports it;
7. only then divide the work into implementation phases.

---

## Appendix A — Confirmed Root-Cause Index

### Algorithmic / data-scope / data-width

- **A1** — Search/Refresh/unowned Details rebuild global reconciliation evidence
- **A2** — Per-provider ingest index fork/rebuild
- **A3** — Per-new-Story full existing-Story-ID scan
- **A5** — Point projection lookup performs global canonical read
- **A6** — Point Story identity resolution reads full redirect table
- **A7** — Story-set resolution creates `V × R` redirect-observation work
- **A8** — Bounded UI problems use global observations
- **X2** — Application-scoped cache policy scans all reading-progress history
- **X3** — Over-wide read models + eager fingerprint recomputation
- **X7** — Reconciliation batch-via-point creates N+1 and `O(S²)` reevaluation

### Logic / scheduling / lifecycle / reactive / I/O

- **L1** — Search orchestration CPU work exposed to Main
- **L2** — Hidden top-level destinations remain active
- **L3** — Discover refresh lacks shared single-flight
- **L4** — Search canonical settlement is serial
- **L5** — Canonical fusion repeats per evidence change
- **L6** — Canonical fusion repeatedly rehydrates/rehashes Story evidence
- **L7** — Search commit uses per-entry DAO calls
- **L8** — Library dependency policy does not control subscription demand
- **X1** — Reactive invalidation domain does not match storage-table domain
- **X4** — Canonical reactive observation double-reads and lacks coherent snapshot contract
- **X5** — Foreground canonical execution and durable worker lack exclusive ownership
- **X6** — Historical outbox recovery leaks into foreground latency

### Data lifetime

- **D1** — Search evidence grows historical catalog size without a safe transient-retention model

**Total confirmed structural root-cause families: 23**

---

## Appendix B — Structural / Scaling Risks

These are intentionally not included in the 23 confirmed root causes.

- **RISK-A4** — candidate-index fan-out can approach `K -> N`
- **RISK-BIND** — bounded reactive Story-ID APIs lack explicit bind-cardinality/chunking contract
- **RISK-PROVIDER** — provider concurrency has no explicit catalog-level cap

---

## Appendix C — Effect / Amplifier Index

- **E1** — Search writes can wake Home observation because of X1
- **E2** — Hidden destinations turn one invalidation into multi-screen work
- **E3** — `WhileSubscribed` cannot stop flows while hidden destinations remain subscribed
- **E4** — Redirect update can create observer avalanche
- **E5** — Search jank is the compound result of several independent defects
- **E6** — Application performance can degrade with age as `N(t)` grows
- **E7** — Reader progress persistence can trigger `R + R + G` global work
- **E8** — Automatic-cache storage writes can wake Downloads observers
- **AMP-DISCOVER** — settlement seed and live projection observation duplicate bounded projection work for the same Story set
- **AMP-CACHE-BUDGET** — automatic cache budget eviction candidate construction can over-read unrelated storage namespace rows

---

## Appendix D — Important Non-Bugs / Deferred Opportunities

- HTTP disk cache: optimization/policy opportunity, not yet counted as a proven root cause.
- Global maintenance scan: acceptable when explicitly off the foreground path and properly budgeted.
- Small bounded sorting/grouping: not a structural performance defect.
- Legacy/test-only matcher code: excluded unless a production caller is established.
- Concurrent provider execution: not a confirmed defect until provider-count/resource evidence demonstrates a real problem.

---

## Appendix E — Revision Notes From the Red-Team Pass

The following corrections were made to the original baseline:

1. **A4** moved from confirmed root-cause census to structural risk.
2. **A9** removed from independent root-cause census and folded into A1/global reconciliation-evidence reconstruction.
3. **E1** retained as an effect but its underlying cause promoted to **X1**.
4. **L6** expanded from generic repository fragmentation to repeated source hydration/fingerprinting/currentness work.
5. Added `B`, `G`, `Q`, and `W` to the performance model.
6. Added seven root-cause families **X1-X7**.
7. Added explicit structural risks for bind cardinality and provider concurrency.
8. Expanded benchmark requirements to cover:
   - metadata width;
   - reading-history scale;
   - durable backlog;
   - Story source cardinality;
   - bounded Story-ID cardinality;
   - canonical coherence.
9. Expanded audit rules to require:
   - reactive invalidation contracts;
   - execution ownership contracts;
   - application-aging and recovery-state workloads.

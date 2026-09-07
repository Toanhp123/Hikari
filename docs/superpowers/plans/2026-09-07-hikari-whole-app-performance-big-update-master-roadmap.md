# Hikari Whole-App Performance Big Update — Master Implementation Roadmap

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this roadmap wave-by-wave. Each wave has its own detailed plan and must close its acceptance gate before dependent waves begin.

**Goal:** Replace Hikari's global/repeated foreground work with bounded, coherent, incrementally maintained, single-owner execution paths across the whole application without regressing canonical correctness, Reader integrity, background durability, or retained-navigation performance.

**Architecture:** The program repairs performance at domain ownership boundaries rather than adding a global performance manager. Shared storage/read boundaries gain explicit point/bulk/query-specific APIs; Catalog gets compact durable reconciliation evidence; canonical work gets durable claim/join ownership; plugin runtime separates control-plane manifest/state from executable/package payloads and owns terminal retry state explicitly; Reader/cache gets an incremental ledger and short critical sections; Chapters move to delta aggregation/commit and keyset scheduling; UI consumers switch to bounded demand after the underlying repositories support it.

**Tech Stack:** Kotlin, Coroutines/Flow, Room 12→13, WorkManager, Android Macrobenchmark, Hilt, existing pure JVM engines/tests.

**Spec:** `docs/superpowers/specs/2026-09-07-hikari-whole-app-performance-big-update-design.md`

## Global Constraints

- Preserve all canonical identity/reconciliation semantics unless a separate benchmark-backed design explicitly changes them.
- Preserve Reader SHA-256/integrity and security-invalidation contracts.
- Preserve P6 retained top-level composition/warm-navigation behavior unless the lifecycle risk gate proves a better measured alternative.
- Do not pool JavaScript isolates, retain decrypted plugin credentials long-term, or introduce a cross-capability global resource arbiter without the corresponding risk gate passing.
- Do not auto-approve plugin capability expansion for performance; terminal/user-action-required outcomes may be memoized only under explicit immutable identity/state invalidation.
- Retryable/transient plugin failures and cancellation remain retryable; no blanket negative cache.
- Foreground point/bounded work must not synchronously complete historical backfill/recovery work.
- If any intermediate wave ships to users, split Room schema versions rather than mutating an already released `MIGRATION_12_13`.
- Every production change starts with a failing contract/regression test and closes with module tests plus architecture/schema checks.
- Every completed task receives a self-review for architecture boundary drift, duplicate work, lifecycle/invalidation conflicts, and regression risk before commit.

---

## 1. Program ordering

### Wave 0 — Performance contracts and representative fixtures

**Purpose:** Build just enough measurement/contract coverage to prove structural improvements. Do not block the program on a perfect benchmark laboratory.

**Deliverables:**

- real Search-query benchmark instead of `searchReopen` only;
- aged catalog/progress/redirect/cache fixture controls plus independent Library-row and explicit-download-record dimensions;
- Reader image fixture that traverses the asset/cache pipeline, not text-only paragraphs;
- Chapter large-graph/page fixture;
- query-count/slope contract tests where deterministic;
- retained-navigation reactive-work diagnostic gate;
- baseline capture before production behavior changes.

**No root cause is declared closed in Wave 0.** It makes later closures measurable.

---

### Wave 1 — Bounded identity, projection, progress and invalidation foundations

**Root causes primarily addressed:** A5, A6, A7, X1-Catalog, X2, part of X3/X4.

**Deliverables:**

- point + bulk/frontier Story redirect resolution plus indexed bounded redirect predicates used by merge/reversal planning;
- bounded Story-ID chunking contract;
- true Room `CatalogStoryProjection` point/bounded queries;
- coherent invalidation-triggered bounded projection read;
- query-specific incomplete-release-ID progress observation;
- Home semantic read projection/invalidation boundary;
- Room v13 structural foundation/migration scaffolding needed by later waves.

**Dependency role:** Wave 2, Wave 3 and Wave 7 consume these bounded primitives.

---

### Wave 2 — Catalog compact evidence, ingest scaling and data lifetime

**Root causes:** A1, A2, A3, L7, D1, X3-Catalog.

**Deliverables:**

- compact durable reconciliation evidence/postings with atomic Story-ownership maintenance across ingest, merge and controlled reversal;
- bounded candidate lookup using current evidence semantics;
- request/provider overlay ingest session replacing full `fork()`;
- point/reservation Story-ID allocator;
- batched catalog commits;
- resumable compact-evidence backfill;
- safe reachability-based wide-metadata compaction while durable identity evidence remains.

**Dependency role:** Wave 3 canonical/reconciliation bulk work consumes compact evidence.

---

### Wave 3 — Canonical execution ownership, fusion and recovery separation

**Root causes:** L3, L4, L5, L6, X3-Canonical, X4, X5, X6, X7.

**Deliverables:**

- coherent `CanonicalFusionInput` + thin currentness token;
- canonical reactive trigger→transactional-read contract;
- one durable claim/join execution path for foreground and worker;
- no historical outbox materialization on interactive path;
- final fusion coalescing by Story/work revision;
- bulk reconciliation evidence APIs;
- bounded Search settlement concurrency;
- Discover refresh single-flight.

---

### Wave 4 — Plugin runtime control-plane, retry ownership and secure-session hot path

**Root causes:** X16, X17, X18. `RISK-PLUGIN-AUTH-CACHE` and `RISK-PLUGIN-ISOLATE` are measured after confirmed structural waste is removed.

**Deliverables:**

- one immutable package-manifest resolver shared by runtime and authentication-policy code;
- `enabled(operation)` manifest-only discovery; `main.js` loads only on actual invoke;
- descriptor-first bundled provisioning so current/newer installed plugins do not read `.osp` bytes;
- terminal/user-action-required provisioning/package outcomes keyed to immutable descriptor/package + relevant installed-state identity;
- transient retry/cancellation remains retryable/single-flight;
- point `pluginId` authentication-policy access for request-time credential injection;
- one secure-session snapshot per authenticated request and one Keystore key acquisition per store operation;
- plugin control-plane/failure/auth benchmark checkpoint;
- isolate pooling and decrypted credential caching remain forbidden unless promoted later by risk gates.

**Dependency role:** No Room-v13 dependency. Consumes Wave-0 plugin characterization and may proceed independently of Waves 1–3 if integration/DI changes are coordinated.

---

### Wave 5 — Reader/cache/storage memory and lock architecture

**Root causes:** X1-storage, X8, X9, X10, X11, X12.

**Deliverables:**

- explicit-download and automatic-cache reactive metadata split;
- incremental in-memory cache ledger with durable recovery reconciliation;
- bounded eviction candidates + point currentness/detach;
- short `stateGate` separated from publication/security ordering;
- physical blob deletion outside locks;
- encoded-source stream/read-only ownership contract through Reader/Coil/blob store;
- bounded Reader touch memoization and bounded Catalog metadata suppression retention;
- Reader image macrobenchmarks/heap allocation gates.

---

### Wave 6 — Chapter delta synchronization and periodic scheduling

**Root causes:** X13, X14, X15.

**Deliverables:**

- incremental `ChapterAggregationSession`;
- differential equivalence against current full aggregation engine;
- delta-only Room Chapter commit and notification evidence;
- materialized `chapter_sync_schedule` projection;
- indexed keyset `nextBatch(cursor, limit)` periodic source;
- continuation worker no longer rescans/sorts whole Library.

---

### Wave 7 — Bounded UI demand, CPU execution boundaries and lifecycle acceptance

**Root causes:** A8, L1, L8. `RISK-LIFECYCLE` is measured/classified here but is not counted as closed unless the evidence gate accepts it as a non-defect.

**Deliverables:**

- Library subscribes only to bounded data its current controls require;
- Story uses one-Story Library membership + progress/resume paths;
- Story chapter controls observe download status only for the current release-ID set;
- Downloads derives bounded Chapter/catalog IDs from explicit download records;
- Settings uses aggregate storage summaries;
- affected reducers/projectors execute on `AppDispatchers.default` after data-scope repair;
- retained-tab diagnostics classify whether material inactive semantic demand remains after bounded-data repairs; no lifecycle workaround is introduced without a focused promoted-risk design;
- P6 warm-navigation benchmark must not regress.

---

### Wave 8 — Risk acceptance, compatibility cleanup and performance freeze

**Risk families:** RISK-A4, RISK-BIND, RISK-LIFECYCLE, RISK-GLOBAL-RESOURCE, RISK-PLUGIN-ISOLATE, RISK-PLUGIN-AUTH-CACHE, RISK-STARTUP-CONTENTION.

**Deliverables:**

- benchmark each risk against explicit promotion threshold;
- implement only promoted risk fixes through separate focused design addenda;
- verify upgraded aged DB/backfill behavior;
- remove obsolete compatibility paths that are proven safe to retire;
- final macrobenchmark/scaling sweep;
- final source-level semantic-scope→physical-work revalidation across Room, filesystem/package storage, secure session/Keystore, process maps and work queues before any 33/33 closure claim;
- update baseline profile only after stable production paths are final;
- freeze performance contracts in docs/tests.

---

## 2. Cross-wave dependency map

| Consumer | Required predecessor | Reason |
|---|---|---|
| Wave 2 compact evidence | Wave 1 schema/migration foundation | v13/backfill and bounded identity primitives |
| Wave 3 canonical work | Waves 1–2 | bounded identity/projection + compact evidence |
| Wave 4 plugin runtime | Wave 0 plugin characterization | independent of Room schema; benchmark/control-plane/security contracts must be frozen first |
| Wave 5 Reader/cache | Wave 0; Wave 1 schema coordination | independent semantics, shared v13 migration ownership |
| Wave 6 Chapters | Wave 0; Wave 1 schema coordination | independent semantics, shared v13 migration ownership |
| Wave 7 UI | Waves 1, 5, 6 where consumers need new APIs | consumers switch only after bounded repositories exist |
| Wave 8 | Waves 0–7 | risks measured against final architecture, not old bottlenecks |

Wave 4 can proceed in parallel with Room-centric Waves 1–3 after Wave 0 because it has no v13 schema dependency. Waves 5 and 6 may run in parallel after schema ownership is coordinated. Wave 7 must not invent local workarounds for APIs that Waves 1/5/6 own. `RISK-PLUGIN-ISOLATE` and `RISK-PLUGIN-AUTH-CACHE` are not decided until Wave 4 removes X16–X18 measurement contamination.

---

## 3. Room v13 ownership ledger

The first schema-changing task creates one explicit ledger listing which wave owns each v13 object. This prevents multiple agents from editing `RoomMigrations.kt` inconsistently.

| Object | Owner wave | Purpose |
|---|---:|---|
| bounded redirect DAO queries/index use | 1 | A6/A7 |
| Home materialized read projection | 1 | X1 Catalog |
| progress incomplete-release index | 1 | X2 |
| compact reconciliation evidence/postings | 2 | A1/X3/D1 |
| generic resumable backfill state | 2 | shared derived-state backfills: Catalog evidence and Chapter schedule |
| explicit-download metadata table | 5 | X1 storage |
| automatic Chapter-cache metadata table | 5 | X1/X9 |
| Chapter sync schedule | 6 | X15 |

If implementation finds an object missing from this ledger, update the design/roadmap before adding it.

---

## 4. Root-cause closure matrix

A root cause is only marked closed when its complexity/ownership contract is verified, not merely when one call site is faster.

| ID | Closure criterion | Wave |
|---|---|---:|
| A1 | Search/Refresh reconciliation does not require global wide source-record materialization | 2 |
| A2 | provider child context creation is independent of historical `N` | 2 |
| A3 | new Story allocation uses point/reservation collision checks | 2 |
| A5 | `find(storyId)` executes bounded point projection query | 1 |
| A6 | no bounded identity/lineage decision materializes the full redirect table; point/frontier/`EXISTS` paths are indexed and bounded | 1 |
| A7 | set observation no longer creates one full redirect observer per Story | 1 |
| A8 | Library/Story/Downloads/Settings consumers use bounded/aggregate APIs; Story membership/progress and current-release download status do not observe global histories | 7 |
| L1 | scalable pure reducers/projectors have explicit Default boundary | 7 |
| L3 | bootstrap/manual Discover refresh cannot execute same pipeline concurrently | 3 |
| L4 | Search canonical settle uses bounded concurrency | 3 |
| L5 | one final dirty revision yields at most one fusion owner per Story/work key | 3 |
| L6 | one rebuild consumes one coherent fusion input; currentness check is thin | 3 |
| L7 | Search commit uses batch DAO operations rather than per-entry statements | 2 |
| L8 | Library dependency policy controls subscription demand | 7 |
| D1 | wide search-only presentation can be compacted without deleting durable identity evidence | 2 |
| X1 | unrelated Search/cache writes no longer wake Home/explicit-download observers | 1/5 |
| X2 | cache policy observes distinct incomplete release IDs directly | 1 |
| X3 | point/ingest/fusion paths hydrate only semantically needed widths | 1/2/3 |
| X4 | canonical/projection reactive read is trigger→coherent transactional snapshot | 1/3 |
| X5 | foreground/worker use same durable claim/join owner | 3 |
| X6 | interactive canonical path is independent of historical outbox `Q` | 3 |
| X7 | reevaluation/candidate work loads Story evidence in bulk once | 3 |
| X8 | `snapshot()` cache pressure is O(1) with respect to `RA/AC` after init | 5 |
| X9 | eviction uses bounded candidate read + point currentness per victim | 5 |
| X10 | foreground cache admission never waits behind filesystem/global metadata I/O under one lock | 5 |
| X11 | production Reader asset path has bounded full-payload copy count | 5 |
| X12 | confirmed process-lifetime memoization/suppression maps (`lastAccessTouches`, metadata `suppressions`) have explicit TTL/cap/invalidation; completion-bounded maps are documented separately | 5 |
| X13 | Chapter page work scales with page delta, not cumulative prefix | 6 |
| X14 | Room commit writes changed delta and does not snapshot full graph before/after each page | 6 |
| X15 | continuation requests one indexed bounded next batch | 6 |
| X16 | operation discovery/auth point queries use manifest/point metadata only; current bundled packages are descriptor-filtered before payload bytes | 4 |
| X17 | unchanged terminal/user-action-required plugin state does not repeat provision/package work; retryable/transient failures still retry and concurrent attempts remain single-flight | 4 |
| X18 | authenticated request uses one session snapshot and session-store crypto obtains one key per high-level store operation | 4 |

---

## 5. Per-wave quality gate template

Every task within a wave follows:

1. write a regression/contract test that fails against current behavior;
2. run only that focused test to prove the failure;
3. implement the smallest structural change that satisfies the target boundary;
4. rerun focused tests;
5. run the affected module suite;
6. self-review production call graph for duplicate fallback paths, semantic-scope→physical-work expansion, and stable-failure amplification;
7. verify transient/terminal/cancellation behavior for any new memoization/retry state;
8. for bounded APIs with global convenience defaults, prove the production binding overrides the fallback and keep a fail-closed source/architecture guard;
9. run architecture/schema/security scripts where applicable;
10. commit one independently reviewable unit.

At wave close, run the wave-specific scaling/benchmark gate and record before/after evidence in a checkpoint doc.

---

## 6. Commit strategy

Prefer one commit per independently reviewable contract, not one giant wave commit. Suggested prefixes:

```text
perf(storage): add bounded story identity resolution
perf(catalog): persist compact reconciliation evidence
perf(canonical): unify durable work claim and join
perf(plugin): separate control-plane metadata from executable loading
perf(plugin): make terminal provisioning state identity-bound
perf(reader): make cache pressure accounting incremental
perf(chapters): apply chapter sync deltas incrementally
perf(ui): bound library reactive demand
test(perf): add aged-state scaling contract
```

Do not mix unrelated cleanup with performance commits. Remove old paths only after the new path is covered and all callers have migrated.

---

## 7. Program-level stop conditions

Pause the program and update the design before implementation continues if any of these occur:

- a proposed optimization changes canonical matching/merge semantics;
- a Reader change requires weakening integrity or security invalidation;
- a v13 structural migration requires unbounded normalization/hashing during DB open;
- a bounded API cannot support required cardinality without a new architectural mechanism;
- a hidden-tab fix regresses the P6 warm-navigation baseline materially;
- a durable work join cannot preserve lease-expiry/crash recovery semantics;
- a Chapter delta implementation cannot prove equivalence to the full aggregation oracle;
- plugin retry memoization cannot distinguish terminal identity-bound outcomes from transient/cancelled failures;
- a plugin optimization would auto-approve capability expansion, weaken package/auth validation, or retain plaintext credentials without a promoted risk + threat review;
- a risk-gated optimization is being implemented without the required benchmark evidence.

---

## 8. Detailed plan files

Implementation proceeds through these documents in dependency order:

1. `2026-09-07-hikari-perf-wave-0-contracts-and-fixtures.md`
2. `2026-09-07-hikari-perf-wave-1-bounded-storage-foundations.md`
3. `2026-09-07-hikari-perf-wave-2-catalog-evidence-and-lifetime.md`
4. `2026-09-07-hikari-perf-wave-3-canonical-execution.md`
5. `2026-09-07-hikari-perf-wave-4-plugin-runtime-control-plane.md`
6. `2026-09-07-hikari-perf-wave-5-reader-cache-memory.md`
7. `2026-09-07-hikari-perf-wave-6-chapter-delta-and-background.md`
8. `2026-09-07-hikari-perf-wave-7-ui-demand-and-lifecycle.md`
9. `2026-09-07-hikari-perf-wave-8-risk-acceptance-and-freeze.md`

Each detailed plan is independently testable and reviewable; this roadmap is the dependency/source-of-truth map across them. Wave 4 may execute in parallel with Room-centric work after Wave 0; Waves 5 and 6 may execute in parallel after shared schema ownership is coordinated.

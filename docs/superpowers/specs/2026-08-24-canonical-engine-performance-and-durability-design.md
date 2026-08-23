# Canonical Engine Performance and Durability Design

**Date:** 2026-08-24

## Goal

Reduce Discover and Search foreground latency, prevent background canonical convergence from repeatedly rebuilding unrelated UI projections, and make durable engine work crash-safe and safe against duplicate consumers without changing reconciliation or fusion policy outcomes.

## Scope

This change covers four related areas:

1. scoped canonical projections and removal of catalog/identity/plugin-state N+1 reads;
2. Search foreground convergence for only the visible result set;
3. atomic queue claims, story-aware ordering, and batched queue state transitions;
4. a transactional catalog-change outbox that closes the provider-commit-to-engine-queue crash window.

The reconciliation policy, fusion policy, canonical field selection, source ranking, and user-visible semantic ordering remain unchanged.

## Architecture

Discover and Search continue to share `CanonicalEngineOrchestrator`, reconciliation, fusion, Room repositories, and the durable worker queue. Each feature owns only its foreground policy:

- Discover immediately converges the semantic Story IDs selected for its visible sections.
- Search immediately converges the first bounded group of ranked results and defers the remainder.
- All deferred work is represented durably and drained through the same worker path.

Storage provides bulk snapshots so one logical engine operation does not repeatedly reload redirects, identifiers, plugin availability, or queue rows.

## 1. Scoped Projection and Bulk Reads

### Discover projection

`DiscoverViewModel` derives its visible semantic Story ID set from committed homes and switches to `CatalogStoryProjectionRepository.observeForStories(ids)`. The projected content stream applies structural distinctness before publishing UI state. It must never subscribe to the global canonical catalog solely to render Discover.

Room may still invalidate an `IN (...)` query when an unrelated row in the same table changes. The restricted query keeps that re-read bounded, while `distinctUntilChanged` prevents unchanged visible content from re-running ranking and recomposition.

### Catalog records

`RoomCatalogRepository.sourceRecords()` loads all entries and all identifiers in a fixed number of queries, groups identifiers by `(pluginId, sourceId)`, and builds records in memory. Story-scoped reads use the existing `identifiersForStories` bulk query.

Home and Search commit paths preload durable ownership, existing stories, and identifiers needed by the mutation rather than querying them one entry at a time. Existing merge and fingerprint semantics are preserved.

### Identity and availability snapshots

Engine batch processing obtains one redirect snapshot for the batch and resolves Story IDs against it. It also obtains one enabled catalog-source snapshot and uses it for every fusion in that batch. Public one-Story APIs keep their current behavior.

## 2. Search Foreground Convergence

Search no longer calls `onEvidenceChanged` independently for every committed result. It accumulates committed changes across sources, ranks the durable result cards, chooses a deterministic foreground set, and calls the batch orchestration API once.

The foreground limit is 20 distinct Story IDs. Those Stories must be canonical-ready before the result is published. Changes outside that set are persisted to the durable queue before foreground work begins.

If a foreground Story cannot become ready, Search returns the existing per-source `canonical_not_ready` failure behavior for that Story. Deferred Stories are not omitted merely because their canonical generation is pending: Search may use their committed source cards as provisional results, clearly retaining the durable Story ID. Selecting a provisional result is valid because Story detail already bootstraps the selected Story.

The 300 ms query debounce and parallel source fetching remain unchanged.

## 3. Durable Queue Execution

### Atomic claim

`canonical_engine_work` gains nullable lease owner and lease expiry columns. A claim transaction:

1. selects ready, unleased/expired work in engine priority order;
2. updates the selected rows with a unique lease token and bounded expiry;
3. returns only rows bearing that token.

A crashed worker leaves rows recoverable after lease expiry. A second process cannot claim a live lease. Re-marking dirty work clears its previous lease and creates a fresh queue revision.

### Ordering and batching

SQL applies priority before `LIMIT`: reconciliation, fusion, policy reevaluation, then post-merge derived work. Selection is Story-aware so reconciliation and fusion for one Story are not unintentionally inverted at a batch boundary.

Successful completions, retries, and invariant blocks are written in one transaction per drained batch with compare-and-set semantics against the claimed revision and lease token. A stale completion cannot delete newer work. Processor counters count only transitions that actually commit.

The worker keeps the existing bounded-work principle. It may drain multiple database pages within one WorkManager execution up to a small elapsed-time budget, then schedules a continuation. Retry timestamps and parked invariant rows remain authoritative.

### Indexes

The queue uses a composite runnable-order index covering lease/runnable time, priority-compatible work type, Story ID, and work type where SQLite can use it. Migration tests must verify both upgraded and fresh schemas.

## 4. Transactional Catalog Change Outbox

Catalog commits write an immutable outbox row for every effective `CatalogCommitChange` in the same Room transaction that stores the corresponding catalog facts. The row contains the durable Story ID, source key, evidence-change flags, evidence level, reason, and a monotonic event identity.

After commit, orchestration consumes outbox rows as follows:

- rows belonging to the caller's foreground Story set are converged immediately;
- every row is translated idempotently into coalesced canonical queue work;
- an outbox row is acknowledged only in the same transaction that persists its queue representation;
- scheduler failure does not acknowledge or discard queue work.

Application startup and the daily safety worker both drain unacknowledged outbox rows before draining canonical work. This makes process death between catalog commit and orchestration recoverable without scanning and re-fingerprinting the entire catalog.

Outbox retention is bounded: acknowledged rows are deleted as part of acknowledgment, while unacknowledged rows remain until represented in the durable queue. Foreground convergence does not delete the durable representation until its compare-and-set completion succeeds.

## Failure and Cancellation Semantics

- Coroutine cancellation is always rethrown.
- Provider/network failure does not create an outbox event because no catalog fact was committed.
- Queue or scheduler failure after catalog commit leaves the outbox event recoverable.
- Foreground engine failure leaves durable work queued and returns the existing retryable UI failure contract.
- Lease expiry may cause at-least-once processing, so all completion and promotion operations retain compare-and-set guards.
- Unsupported future policy versions remain parked and are never reclaimed merely because a lease expires.

## Database Migration

The Room schema version increments from 9 to 10. Migration 9→10:

- adds lease columns to `canonical_engine_work`;
- adds queue ordering indexes;
- creates `catalog_change_outbox` and its unacknowledged ordering index;
- preserves all existing queue rows as immediately claimable and unleased.

The migration must not destructively recreate catalog, canonical, user-state, chapter, or download tables.

## Testing

Tests are written before production changes and cover:

- Discover observes only its semantic Story IDs and suppresses unrelated projection emissions;
- bulk source-record loading preserves identifiers/fingerprints and has bounded DAO call count;
- Search sends one batch with exactly the first 20 deterministic Story IDs and publishes provisional deferred results;
- two concurrent claimers cannot receive the same live lease;
- expired leases are reclaimable;
- reconciliation precedes fusion at a batch boundary;
- stale/batch completion cannot delete re-dirtied work;
- process death after catalog commit leaves an outbox row recoverable;
- outbox acknowledgment and queue persistence are atomic;
- migration 9→10 preserves pre-existing work and catalog state;
- 1,000 Story queue/outbox contracts complete with bounded query/transaction counts.

The 1,000 Story tests are scale-contract tests, not device benchmarks. Device performance is validated separately with Macrobenchmark/Perfetto and reports foreground convergence time, background drain throughput, Room transaction count, and frame jank.

## Acceptance Criteria

1. Discover refresh stops waiting after its visible semantic Story set converges; background promotions do not change its UI state when those Stories are unchanged.
2. Search does not synchronously canonicalize more than 20 distinct Story IDs per query.
3. Catalog size does not cause one identifiers query per existing entry during ingest-context creation.
4. No live queue row can be claimed by two consumers, and expired claims recover automatically.
5. A committed catalog change survives process death before orchestration and eventually creates canonical engine work.
6. Existing reconciliation/fusion policy tests remain unchanged and pass.
7. Fresh-schema and migration instrumentation tests pass on a connected Android device.

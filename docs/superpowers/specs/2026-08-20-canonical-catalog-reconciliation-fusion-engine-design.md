# Canonical Catalog Reconciliation and Metadata Fusion Engine Design

Date: 2026-08-20
Status: **NORMATIVE IMPLEMENTATION BASIS — Phases 0–4 / Tasks 1–32 VERIFIED/CLOSED on 2026-08-21; Phase 5 Task 33 active next**
Scope: host-owned canonical Story identity across multiple catalog providers, explainable reconciliation, metadata fusion, materialized canonical generations, durable review/lineage, atomic Story graph merge, source preference, event orchestration, retry/background safety, and integration with existing Catalog/Library/Chapters/Reader/Room boundaries
Baseline: plan-entry repository snapshot was Room schema 8 after the 2026-08-20 catalog metadata-lifecycle unification; verified Phase 1 advances the current persistence foundation to Room schema 9 through `MIGRATION_8_9`.

> **Implementation checkpoint (2026-08-21):** Tasks 5–11 are VERIFIED and Phase 1 is closed. The accepted boundary includes canonical read/identity contracts, the schema-9 persistence foundation, external-identifier/source-record persistence, canonical generation/redirect storage, durable work/audit foundations, representative 8→9 graph migration coverage, and local-only bootstrap. Developer-checkout evidence includes a green Catalog unit suite, Room schema build/export validation, 27/27 selected Room migration/repository connected tests, a green app composition-policy test, and a green canonical `./scripts/verify.sh` after the observer-race and Detekt line-length cleanup patches. Reconciliation policy, destructive Story merge, feature canonical read-path cutover, and Phase-2 fusion policy are still not enabled; Phase 2 now starts at Task 12.

> **Phase-2 acceptance checkpoint (2026-08-21):** Tasks 12–21 are **VERIFIED / CLOSED**. The accepted boundary includes versioned source usability/freshness and Fusion policy, deterministic primary selection with hysteresis/pinning, field-specific canonical Fusion with immutable contributor fingerprints, candidate validation/promotion/change suppression/retry, canonical projection materialization, and Story/Search/Discover/Library canonical presentation cutover with a cross-feature no-Fusion-on-UI-read guard. Search persists Summary facts before canonical presentation; Discover preserves Home feed/ranking semantics while canonical projection owns presentation fields. Developer-checkout evidence includes the full Phase-2 unit gate, unchanged schema-9 export, 22/22 selected Room connected tests, Search 7/7, app smoke/navigation 14/14, successful `homeDiscoverHome` and `storyTabs` macrobenchmarks, green Detekt, and green canonical `./scripts/verify.sh`. Room remains schema 9 and reconciliation/merge execution is still disabled. Task 22 is now the active next step.

> **Phase-3 acceptance checkpoint (2026-08-21):** Tasks 22–25 are **VERIFIED / CLOSED**. The accepted boundary adds versioned symmetric reconciliation evidence/decisions, deterministic candidate discovery and winning-lead safety, durable evidence/policy-aware reconciliation case revisions, and observe-only reconciliation routing from persisted Home/Search/Details evidence changes. Home/Search/unowned Details use `CatalogIngestReconciliationIndex`; existing `SourceKey` durable ownership wins before semantic reconciliation, provider/page forks are promoted only after successful commit, and the legacy `StoryMatcher`/`CatalogMatchIndex` remain characterization-only with zero runtime call sites in those services. Developer-checkout acceptance includes the focused Catalog gate, selected feature ViewModel tests, app composition policy, 30/30 selected Room connected tests, green Detekt, and green canonical `./scripts/verify.sh`; sandbox artifact checks retain static/schema/compile-oriented evidence. No destructive Story graph merge executor exists in this phase and Room remains schema 9. At the Phase-3 checkpoint, Task 26 was the active next task.

> **Phase-4 acceptance checkpoint (2026-08-21):** Tasks 26–32 are **VERIFIED / CLOSED**. The accepted boundary adds meaningful-user-state survivor selection; conservative Library, mapping, Chapter, sync/override, and Reader-progress merge policies; read-only full-graph preparation with authoritative stale-plan fingerprints; one atomic Room Story graph writer/coordinator with redirect flattening, merge/reversal audit, case/work re-keying, idempotency, and durable `FUSION_REBUILD` / `POST_MERGE_DERIVED` work; redirect-aware Story-keyed repositories; and reconciliation integration through the same guarded merge executor. Production now uses `APPLY_ELIGIBLE_AUTO_MERGES`. Developer-checkout evidence includes focused reconciliation unit tests, the cross-domain unit gate, 9/9 focused merge-coordinator connected tests, 86/86 full Room connected tests before final enablement, behavior-preserving Detekt cleanup without suppressions, a green pre-enable gate, and a green post-enable Catalog/Library/Chapters/Reader + Room connected + canonical `./scripts/verify.sh` gate on the enabled tree. Room remains schema 9. Phase 5 begins at Task 33.

### 2026-08-21 implementation checkpoint

The first Phase-0 slice is now represented in source and tests without advancing Room schema or enabling any reconciliation/fusion runtime behavior:

- Task 1: opaque `latestUpdate.releaseLabel` contract locked; Discover no longer prepends `"Ch. "`; plugin SDK contract added.
- Task 2: bounded external-identifier wire/host facts added; `SourceKey` moved to `catalog.identity`; identifiers flow through source/domain/legacy ingest models while the legacy matcher deliberately ignores identifier semantics. Identifier persistence remains deferred to the approved schema foundation in Phase 1 / Task 7.
- Task 3: deterministic evidence normalization plus independent identity/fusion SHA-256 fingerprints and `CatalogSourceRecord` are implemented.
- Task 4: current Search/Story/Discover/catalog-projection source-selection behavior is locked by characterization tests and explicitly marked for Phase-2 replacement.

That Phase-0 checkpoint did **not** claim canonical generations, durable reconciliation cases, retroactive merge, redirects, Story graph merge, or feature canonical-read cutover. Phase 0 was later verified on the developer checkout and remains historical evidence in its checkpoint.

### 2026-08-21 Phase-1 verified checkpoint

Tasks 5–11 now provide the non-destructive persistence/bootstrap foundation required before the Fusion and Reconciliation phases:

- canonical Story/read/identity domain contracts are Android-free and Room-free;
- `MIGRATION_8_9` creates the complete canonical-engine persistence foundation and bootstraps existing Stories as `REEVALUATING` + AUTO with coalesced Fusion rebuild work;
- external identifiers are persisted losslessly and source-record reads preserve Summary/Full provenance plus deterministic fingerprints;
- canonical generation persistence uses validated source ownership and one atomic candidate/provenance/valid/active-pointer transaction;
- redirect resolution is centralized below features and supports observers opened on historical Story IDs;
- durable work, reconciliation-case revision storage, merge/reversal audit foundation, and retired-Story re-key helpers are present without enabling merge execution;
- representative schema-8 graph fixtures exercise the 8→9 migration contract and FK integrity;
- canonical bootstrap depends only on canonical persistence plus a rebuild port and never on catalog fetching/network boundaries.

Verified Phase 1 still does **not** enable reconciliation policy, destructive auto-merge, Review UI, feature canonical presentation cutover, or the Phase-2 Fusion Engine. Offline pure/adapter compilation and exact migration-SQL replay remain supplementary evidence; acceptance is based on the developer-checkout Gradle/Room/connected/composition and canonical `./scripts/verify.sh` results recorded in the Phase-1 checkpoint.

## 1. Purpose

Hikari now has multiple real catalog providers and a durable user graph built on top of host-owned `StoryId`. The current implementation can preserve multiple provider records under one Story when matching succeeds during initial ingestion, but it does not yet provide a complete long-lived canonical identity engine or a single canonical metadata truth.

The system therefore needs a host-owned engine that can answer two different questions without conflating them:

1. **Identity:** do these catalog source records represent the same creative work?
2. **Presentation metadata:** once they are known to represent one work, what canonical metadata should Hikari expose right now?

The engine must preserve every provider source independently, keep provenance, respect user-owned state, survive provider disappearance, support later correction when richer evidence arrives, and remain generic when a fourth or fifth catalog provider is installed.

The target user experience is deliberately simpler than the internal model:

```text
many catalog records
        ↓
one canonical Story
        ↓
one stable canonical presentation
```

Internally, however, Hikari must retain the full source graph:

```text
Canonical Story
 ├─ provider/source A
 ├─ provider/source B
 └─ provider/source C
```

A user may inspect or choose those individual sources. Canonicalization must never destroy source-specific metadata or provenance.

This document is the normative design for that engine.

---

## 2. Relationship to Existing Architecture and Earlier Designs

This design extends, rather than rewrites, the accepted Baseline-2 architecture and the source-preserving catalog model introduced by Wave 05.

The following existing boundaries remain authoritative:

- `StoryId` is host-owned canonical identity.
- `(pluginId, sourceId)` is provider/source identity.
- catalog provider metadata remains source-specific and reversible.
- `:catalog` owns catalog-domain policy and services.
- `:library` owns Library membership and content-mapping semantics.
- `:chapters` owns chapter synchronization and aggregation semantics.
- `:reader` owns reading-progress semantics.
- `:storage:room` owns Room persistence and is the integration layer that can see the full local graph.
- feature/UI modules do not depend on Room internals.
- plugin operations provide facts; the host does not issue hidden `details()` calls merely because a listing omitted optional presentation fields.
- `CatalogDetailsLoader` remains the production boundary that invokes catalog `details()` for one source identity.
- Home/Search listing metadata remains a plugin-operation responsibility; valid omission is not a host-side enrichment failure.

This spec **supersedes the old practical assumption that an already-linked catalog source never needs identity reassessment**. That assumption was acceptable before multiple durable real catalog providers and user-owned downstream state existed. It is no longer sufficient.

This spec also supersedes feature-local source-selection/fusion rules wherever those rules currently create canonical presentation truth. Discover/Search/Story/Library may still perform presentation composition, but they must consume canonical metadata decisions produced by the engine rather than independently choosing provider truth.

This spec does **not** supersede historical checkpoint evidence. Historical documents remain evidence of what was implemented and verified at those points in time.

---

## 3. Current Repository Problems This Design Must Fix

### 3.1 Initial matching discards ambiguity

`StoryMatcher` currently has useful policy concepts:

```text
AUTO_LINK
REVIEW
SEPARATE
```

with title similarity, author evidence and an auto-link lead requirement. However, the normal resolution path only commits a link for `AUTO_LINK`; ambiguous `REVIEW` evidence is not persisted as a durable product state. If a new source is not auto-linked, Hikari creates another Story and loses the fact that the two Stories were plausible duplicates.

The engine must make `REVIEW` durable.

### 3.2 Details cannot repair an earlier sparse-data split

`CatalogDetailsLoader` currently checks `repository.metadataSnapshot(key)`. If the source already exists, it retains the source's persisted `StoryId` and does not rerun cross-catalog matching.

Room `commitDetails()` also preserves the existing source's `storyId`.

Therefore this sequence cannot currently repair itself:

```text
MAL Summary          → Story A
MangaUpdates Summary → Story B

later:
MangaUpdates Full reveals strong aliases/authors/identifier evidence
```

The new engine must treat source-to-canonical membership as a revisable host decision and reassess identity when identity-relevant evidence actually changes.

### 3.3 Canonical presentation is inconsistent across features

Today different consumers choose provider data differently:

- Search selection can use `firstOrNull()`.
- Story source ordering is primarily alphabetical by `pluginId/sourceId`.
- catalog projection can use the first sorted source for title/cover.
- Discover has a richer feature-local `presentationOrder` and field-specific combination logic.

This means the same canonical Story can have different effective provider truth depending on the screen.

The new engine must make one materialized canonical metadata generation the authoritative presentation read model.

### 3.4 Read models hide useful metadata provenance

Room schema 8 already stores Summary provenance and Full provenance on `catalog_entries`, but `StoryCatalogSnapshot` exposes only:

```text
Story
List<CatalogEntry>
```

The canonical engine needs a source-record read model that can distinguish Summary/Full evidence, freshness, source availability, external identifiers, and the provenance used by a decision.

### 3.5 StoryId is referenced throughout a durable graph

Room schema 8 has Story ownership in:

```text
stories
catalog_entries
library_entries
content_mappings
content_mapping_rejections
canonical_chapters
chapter_releases
chapter_aggregation_overrides
chapter_sync_states
reading_progress
```

`chapter_storage_entries` is indirectly tied through stable chapter-release IDs.

Foreign keys do not use `ON UPDATE CASCADE`. A Story merge therefore cannot be implemented as a casual primary-key rewrite. It requires a planned graph migration with conflict resolution and an all-or-nothing transaction.

### 3.6 The current database version is already reserved in the roadmap

The repository is currently on Room schema 8. Existing Wave 10 documentation plans to use `8 -> 9` for durable notification state and explicitly permits a separately reviewed migration to change that numbering.

This canonical engine is such a separately reviewed migration if it is implemented before Wave 10. The schema policy is defined later in this document; implementation must not silently collide with the old Wave 10 reservation.

---

## 4. Goals

The completed system must provide all of the following:

1. one host-owned canonical Story visible to users for one creative work;
2. independent preserved catalog source records beneath that Story;
3. provider-agnostic reconciliation based on normalized evidence;
4. explainable `AUTO_MERGE`, `REVIEW`, and `SEPARATE` decisions;
5. retroactive reassessment when richer identity evidence appears;
6. durable review/rejection state that does not reappear on unchanged refreshes;
7. safe automatic Story merges only when semantic evidence is strong and user-owned state does not conflict;
8. deterministic survivor selection that favors the Story with more meaningful user state;
9. atomic migration of all authoritative local state when Stories merge;
10. redirects/lineage so retired StoryIds remain resolvable;
11. controlled, auditable reversal when the graph still permits safe separation;
12. generic primary-source selection with hysteresis and user pinning;
13. field-specific metadata fusion rather than one-provider-for-everything;
14. field-level provenance and decision trace;
15. immutable/versioned canonical generations promoted atomically;
16. stable UI reads that never reason directly over raw provider data to repair canonical state;
17. event-driven reevaluation as the main runtime path;
18. a background safety worker that retries dirty/backlogged work without becoming a second engine;
19. policy versioning and lazy/background reevaluation when policy changes;
20. source-unavailability behavior that preserves historical data while reducing stale source influence;
21. support for standardized external identifiers without hard-coding provider names in core policy;
22. a phased implementation that can be verified safely before destructive auto-merge is enabled.

---

## 5. Non-Goals

The first implementation of this architecture must **not** expand into the following:

- no event-sourcing of the whole application database;
- no provider-specific `if MAL`, `if MangaUpdates`, provider priority tables, or provider reputation presets in the core engine;
- no plugin-defined confidence/quality score that the host trusts as canonical policy;
- no network fetching from Reconciliation or Fusion to fill missing fields;
- no automatic `details()` enrichment because a valid Summary/listing field is absent;
- no per-field user pinning in v1;
- no automatic full chapter-graph deduplication merely because two Stories merged;
- no regeneration of stable `CanonicalChapterId` or `ChapterReleaseId` solely because Story identity changed;
- no full admin/debug console in the first engine wave;
- no promise that every historical merge can always be automatically undone;
- no generic cross-module "God merge engine" that owns Library, Mapping, Chapter, and Reader semantics;
- no new production module solely for this engine unless later implementation evidence proves the existing module graph cannot support the design cleanly.

---

## 6. Terminology

### 6.1 Source key

```text
SourceKey = (pluginId, sourceId)
```

This is provider-specific identity. It is stable within the provider contract and is not canonical Story identity.

### 6.2 Raw source record

Host-persisted facts supplied by one catalog source, plus host-known provenance such as whether those facts came from Summary or Full metadata and when they were resolved.

### 6.3 Canonical Story

The host-owned creative-work identity represented by `StoryId`.

### 6.4 Canonical membership

The current host decision that a particular `SourceKey` belongs to a particular canonical `StoryId`.

Membership is durable but revisable when identity evidence changes.

### 6.5 Reconciliation

Reasoning about whether two source/canonical identities represent the same creative work.

### 6.6 Fusion

Reasoning about which source values should contribute to canonical presentation metadata after identity is already settled.

### 6.7 Primary source

A stable default source used for primary-oriented presentation fields and source navigation. It is **not** the source for every canonical field.

### 6.8 Canonical generation

An immutable, internally consistent materialized snapshot of canonical metadata, primary selection, health, and field provenance for one Story under specified policy versions.

### 6.9 Reconciliation case

A durable ambiguous/conflicting identity relationship requiring review, carrying versioned evidence and decision history.

### 6.10 Redirect

A durable mapping from a retired StoryId to the current surviving StoryId.

### 6.11 Authoritative state

State whose partial migration would leave the local graph internally contradictory. Story ownership, Library membership, protected mappings, chapter ownership, progress references, redirects, and merge audit are authoritative for merge transaction purposes.

### 6.12 Derived state

State that can be rebuilt after a committed identity change, such as canonical metadata generations, search projections, automatic mapping suggestions, and selected chapter aggregation work.

---

## 7. Normative Product and Architecture Decisions

This section records the decisions made during the design interview. Later implementation plans must not silently weaken or remove them.

### DECISION-CANONICAL-001 — User sees one Story; sources remain independently inspectable

The product uses a hybrid model:

```text
one canonical Story in normal UX
+
all provider records retained below it
```

Provider sources are never destructively folded into one raw record. Source inspection and explicit source selection remain possible.

### DECISION-CANONICAL-002 — Metadata uses a primary source plus field-specific fusion

Hikari does not choose one provider globally for every field. A primary source provides default presentation consistency, while field-specific policies may combine or select values from other sources.

### DECISION-CANONICAL-003 — Reconciliation automation is balanced, not aggressive

Strong, unambiguous evidence with no hard conflict may auto-merge. Ambiguous evidence becomes `REVIEW`. Clear evidence of different works becomes `SEPARATE`.

Title similarity alone is never sufficient to auto-merge.

### DECISION-CANONICAL-004 — Story survivor favors meaningful user state

When two canonical Stories merge, the survivor is selected in this order:

```text
more meaningful user state
    >
older canonical Story
    >
stable deterministic StoryId tie-break
```

Provider metadata quality does not choose the survivor.

### DECISION-CANONICAL-005 — Domain-safe state may merge automatically; strong user conflicts require review

The merge system uses hybrid conflict handling. Deterministic domain-safe state can be reconciled automatically. Conflicting protected/user-owned state that cannot be safely combined blocks automatic merge and requires review.

### DECISION-CANONICAL-006 — Primary source is dynamic but stable through hysteresis

Automatic primary selection is reevaluated when relevant evidence changes, but the current primary is retained when a challenger is only marginally better. Primary switches occur only when improvement is materially significant, the current source degrades, or the user pins a source.

### DECISION-CANONICAL-007 — Canonical presentation is materialized

UI does not recompute fusion on every read. Hikari persists a canonical metadata generation while retaining raw provider records and provenance.

### DECISION-CANONICAL-008 — Reconciliation is event-driven with a background safety pass

Identity reevaluation primarily reacts to meaningful evidence changes. Background processing retries dirty, failed, or stale-policy work and performs lightweight safety sweeps. It is not a separate reasoning implementation.

### DECISION-CANONICAL-009 — Review is both queued and contextual

Ambiguous cases are durable. Hikari may expose them in a dedicated Review Queue and contextually when the relevant Story is being viewed. Both surfaces operate on the same underlying `ReconciliationCase`.

### DECISION-CANONICAL-010 — Merge history is auditable and reversibility is controlled

Merge events retain enough lineage and domain-specific audit information to explain what happened and to assess whether a later split is safe. Hikari does not guarantee unconditional rollback after subsequent user state has evolved.

### DECISION-CANONICAL-011 — Canonical fields retain provenance and decision trace

Each canonical field records its contributor source(s), selection/fusion strategy, relevant policy version, and enough host-generated reason information to explain the result.

### DECISION-CANONICAL-012 — Host owns generic policy; plugins provide facts

Core policy contains no provider-ID branches or provider priority presets. Plugins provide bounded facts through the host-defined protocol. Host policy interprets those facts.

### DECISION-CANONICAL-013 — Source disappearance does not erase Story history

Unavailable/disabled/stale sources retain their last persisted raw evidence. They become less eligible for active primary/fusion decisions according to host policy. A canonical Story does not disappear simply because one catalog plugin is unavailable.

### DECISION-CANONICAL-014 — User may pin a Story-level primary source

A user can pin the primary source for one Story and later return to AUTO. Pinning affects default/primary-oriented presentation; it does not disable field-specific fusion. If the pinned source is temporarily unusable, Hikari may use a temporary effective fallback without deleting the pin.

### DECISION-CANONICAL-015 — Canonical identity is creative-work + compatible medium/adaptation lineage

Provider/language/edition representations of the same creative work may merge under one Story. Different media/adaptations, sequels, spin-offs, materially distinct remakes, and genuinely separate works remain separate canonical Stories.

For v1, `ContentType` is a hard medium boundary. The engine must not infer sequel/spin-off/adaptation equivalence from similar titles.

### DECISION-CANONICAL-016 — `SEPARATE` is durable but evidence/version aware

A user or engine separation decision is not forgotten on ordinary refresh. It may be reopened only when identity-relevant evidence materially changes or a relevant reconciliation policy version changes.

### DECISION-CANONICAL-017 — External identifiers are a standardized host contract

The catalog protocol supports bounded typed external identifiers so reconciliation can use stronger evidence than title similarity without learning provider-specific IDs.

### DECISION-CANONICAL-018 — Strong identifiers do not bypass hard conflicts

A strong identifier match is high-weight positive evidence, not absolute authority. Strong identifier evidence that conflicts with incompatible medium/lineage or mutually exclusive identity evidence yields `REVIEW`, not blind auto-merge.

### DECISION-CANONICAL-019 — Plugins do not self-score trustworthiness

Plugins may provide objective facts such as field presence, identifier values, source timestamps, and metadata operation output. They do not provide a host-trusted `quality = 0.95`. Host policy owns effective confidence and quality.

### DECISION-CANONICAL-020 — Canonical generations are validated before atomic promotion

A generation is built as a complete candidate, validated, persisted, then promoted atomically. UI sees either the prior generation or the new generation, never a partially updated mixture.

### DECISION-CANONICAL-021 — Policy changes use hybrid reevaluation

A relevant policy-version change marks affected canonical state for reevaluation. Active/used Stories can reevaluate eagerly or on demand; background work gradually processes the remainder. Existing valid generations remain readable while waiting.

### DECISION-CANONICAL-022 — Identity Core coordinates but domains own migration semantics

Canonical Identity owns identity, reconciliation, lineage, redirects, review state, and merge intent. Library/Mapping/Chapters/Reader retain ownership of their own merge semantics. No God engine absorbs those domains.

### DECISION-CANONICAL-023 — Authoritative Story merge is all-or-nothing

All domain-critical merge participants prepare and validate before commit. If any participant blocks or fails, no authoritative Story ownership change is committed.

### DECISION-CANONICAL-024 — UI reads last valid canonical generation plus health

Canonical presentation remains available through the last valid generation during reevaluation or failure. Health can expose `FRESH`, `STALE`, `REEVALUATING`, or `DEGRADED`. UI does not fall back directly to raw source data to invent its own canonical truth.

### DECISION-CANONICAL-025 — Architecture is complete up front; implementation is phased

The durable contracts/schema must not block later phases, but code rollout is split into small verified tasks. Reconciliation may run in observe-only mode before destructive automatic merge is enabled.

---

## 8. Top-Level Architecture

The recommended architecture is **Canonical Identity Core + Metadata Fusion Engine**, not an overlay-only solution and not full event sourcing.

```text
                   ┌──────────────────────┐
Catalog plugins →  │ Raw source records   │
                   └──────────┬───────────┘
                              │ normalized evidence
                              ▼
                   ┌──────────────────────┐
                   │ Reconciliation       │
                   │ Engine               │
                   └──────────┬───────────┘
                              │ identity decision
                              ▼
                   ┌──────────────────────┐
                   │ Canonical Identity   │
                   │ Core                 │
                   └──────┬────────┬──────┘
                          │        │
                    merge │        │ canonical source set
                          ▼        ▼
                 ┌────────────┐  ┌──────────────────┐
                 │ Room Graph │  │ Metadata Fusion  │
                 │ Merge      │  │ Engine           │
                 └─────┬──────┘  └────────┬─────────┘
                       │                  │
              domain policies            ▼
                       │        Canonical Generation
                       │                  │
                       └──────────┬───────┘
                                  ▼
                     Stable canonical read model
                                  │
                     Discover / Search / Story /
                           Library / other UI
```

Three authorities are deliberately separate:

```text
Reconciliation Engine
    answers "same work?"

Canonical Identity / Merge
    answers "which StoryId owns it?"

Fusion Engine
    answers "what canonical metadata is shown?"
```

A component must not silently take over another authority's responsibility.

---

## 9. Module Ownership and Component Boundaries

### 9.1 `:plugins:api` — bounded provider facts only

The plugin protocol is host-defined and remains data-oriented. It may be extended with typed external identifier values, but it does not contain host reconciliation policy or trust scores.

Required protocol extension:

```text
CatalogExternalIdentifierDto
- namespace
- value
- scope
```

with a bounded enum equivalent to:

```text
WORK
PUBLICATION
EDITION
PROVIDER_RECORD
```

The exact wire names must be stable and documented in the plugin SDK.

`CatalogItemDto` and `CatalogDetailsOutputDto` may carry a bounded collection of identifiers. Providers are not required to populate them. Their absence is ordinary missing evidence, not an error.

`CatalogLatestUpdateDto.releaseLabel` is explicitly an opaque, complete provider-formatted presentation label. The host does not prepend `"Ch. "` or infer numeric semantics from it.

No plugin field named or semantically equivalent to host-trusted `confidence`, `providerQuality`, or `priorityWeight` is introduced.

Objective capability facts may come from the existing plugin/runtime contract (for example whether a catalog operation is supported). Such capability flags remain booleans/contract facts; they do not become self-declared quality scores.

### 9.2 `:catalog` — Evidence Layer

`:catalog` maps protocol facts into host domain evidence:

```text
SourceKey
Summary/Full metadata level
field presence
source timestamps
host resolution timestamps
external identifiers
content type
normalized titles/aliases/authors
availability snapshot from runtime-facing source registry
```

The Evidence Layer performs normalization and fingerprinting. It does not decide provider quality and does not fetch additional data.

### 9.3 `:catalog` — Reconciliation Engine

A pure, deterministic, versioned component responsible for:

- candidate evaluation;
- evidence hierarchy;
- semantic conflict gates;
- candidate ranking and lead;
- `AUTO_MERGE`, `REVIEW`, `SEPARATE` assessment;
- explainable reason codes;
- identity evidence fingerprint semantics.

It does not:

- call catalog Home/Search/Details;
- write Room directly;
- select a merge survivor;
- migrate Library/Mapping/Chapter/Progress state;
- fuse presentation metadata.

### 9.4 `:catalog` — Canonical Identity Core

Owns domain contracts for:

- canonical source membership;
- Story redirect resolution contract;
- survivor-selection input/output;
- reconciliation case domain models;
- merge intent/request models;
- merge lineage/audit domain models;
- current canonical source preference;
- canonical read-model contracts.

It may produce a `StoryMergeRequest` or equivalent intent but does not implement Room graph surgery.

### 9.5 `:catalog` — Metadata Fusion Engine

A pure/versioned component that receives source records and previous canonical generation and produces a candidate canonical generation.

It owns:

- usable-source classification;
- automatic primary ranking;
- hysteresis;
- user pin application;
- field-specific selection/fusion;
- canonical health calculation inputs;
- field-level provenance;
- fusion evidence fingerprint semantics.

It does not fetch network data and does not change canonical Story membership.

### 9.6 `:library` — Library and content-mapping merge semantics

`:library` owns pure merge-policy types for:

- Library membership/status reconciliation;
- content-mapping protected-state conflict detection;
- automated-vs-protected resolution;
- content-mapping rejection union semantics.

The existing invariant remains:

```text
AUTOMATED      = replaceable
USER_APPROVED  = protected
USER_URL       = protected
```

### 9.7 `:chapters` — chapter-graph merge semantics

`:chapters` owns policies for:

- moving chapter ownership without regenerating stable IDs;
- validating aggregation overrides;
- handling duplicate/derived sync state;
- marking/requiring derived reaggregation/resync after identity merge.

Identity merge does not automatically claim two canonical chapter groups are equivalent.

### 9.8 `:reader` — reading-progress merge semantics

`:reader` owns policy for duplicate/conflicting progress where it can occur. Identity Core must not invent chapter-progress ordering rules.

A merge must not silently make the user appear to have progressed backwards.

### 9.9 `:storage:room` — atomic integration boundary

`:storage:room` is the only existing production module that depends on Catalog, Library, Chapters, Reader, and the Room database. It therefore owns:

- persisted canonical engine entities/DAOs;
- `RoomStoryIdentityResolver` or equivalent;
- graph merge planner integration;
- domain `prepare/validate` adapter calls;
- the single authoritative Room transaction;
- redirect flattening;
- merge audit persistence;
- dirty-work persistence;
- migration/FK validation.

This does not make Room the owner of identity policy. Room executes plans produced by domain policy.

### 9.10 `:feature:catalog`

Feature code owns UI composition only:

- Review Queue presentation;
- contextual review presentation;
- source inspection and user pin actions;
- health/status presentation where product-appropriate;
- Discover/Search/Story/Library composition from canonical read models.

It does not calculate provider trust, perform Story merges, or use raw-source fallback to repair canonical presentation.

### 9.11 No new module in v1

The existing dependency graph can support the design. A new identity module is explicitly deferred unless implementation proves `:catalog` cannot hold the pure contracts without violating package-boundary rules.

---

## 10. Fetching and Reasoning Must Remain Separate

This boundary is normative because it prevents the core engine from becoming an implicit plugin orchestrator.

Allowed flow:

```text
Catalog lifecycle / explicit product action
        ↓
Home/Search/Details fetch
        ↓
persist source facts
        ↓
evidence changed event
        ↓
Reconciliation/Fusion
```

Forbidden flow:

```text
Reconciliation sees missing author
        ↓
Reconciliation calls Details
        ↓
tries another provider
        ↓
changes identity
```

Forbidden flow:

```text
Fusion sees missing description
        ↓
Fusion calls Details on every provider
```

A separate product-level enrichment service may request Full metadata when the Story-detail experience requires Full metadata. Its fallback rule is operation-level:

```text
preferred eligible source fails/unavailable
    → try next eligible source
```

It must **not** treat a successful Full payload with optional missing fields as a provider failure and must not continue fetching merely to fill individual holes.

This preserves the existing metadata-lifecycle principle: the host states the metadata level it requires; plugins supply whatever optional fields their valid contract provides.

---

## 11. Raw Source Record and Evidence Model

### 11.1 Source records remain lossless

The logical source record must expose at least:

```text
SourceKey(pluginId, sourceId)
current canonical StoryId
CatalogEntry values
Summary provenance
Full provenance when present
external identifiers
identity evidence fingerprint
fusion evidence fingerprint
availability state
```

The implementation may continue storing Summary and Full values in one `catalog_entries` row where current schema semantics make that appropriate. The canonical engine contract must nevertheless distinguish provenance/metadata level.

Raw values are never rewritten to match a fused canonical value.

### 11.2 External identifier semantics

Host-domain equivalent:

```text
ExternalIdentifier(
    namespace,
    value,
    scope
)
```

Rules:

- `namespace` and `value` are bounded stable text;
- identifiers are compared only inside the same namespace and compatible scope;
- `WORK` means the identifier claims work-level identity;
- `PUBLICATION` and `EDITION` are supporting publication identity and do not automatically merge different creative works;
- `PROVIDER_RECORD` means provider record identity and is primarily useful for direct source/provenance reasoning, not cross-provider work identity;
- same work-level identifier is strong positive evidence;
- conflicting work-level identifiers in the same identity namespace may be a hard or review-level conflict according to policy;
- source URL is not automatically a strong work identifier;
- providers must not invent host confidence values around identifiers.

### 11.3 Evidence normalization

Normalization must be deterministic and locale-safe.

For title/alias/author identity keys, v1 may use the existing matcher normalization behavior where appropriate, but the new contract must explicitly separate:

```text
display value
normalized comparison key
```

Collection deduplication must not use aggressive fuzzy merging. At minimum:

```text
Unicode normalization
trim
collapse repeated whitespace
case-fold for comparison
```

Fuzzy similarity remains an explicit matcher operation, not storage canonicalization.

### 11.4 Identity evidence fingerprint

This fingerprint changes only when facts relevant to identity change, including:

```text
title
aliases
authors
content type / medium
work-level identifiers
publication/edition identity evidence where policy uses it
explicit lineage evidence when such a contract exists
```

It must not change merely because:

```text
cover changes
score changes
latest-update time changes
cache fetchedAt changes
provider refresh timestamp changes without identity facts changing
```

### 11.5 Fusion evidence fingerprint

This fingerprint covers values that can change canonical presentation or source eligibility, including:

```text
title/description/cover
aliases/authors/genres/language tags
score/popularity/status/latestUpdate
Summary/Full provenance
availability/freshness classification inputs
primary preference inputs
```

Identity and fusion fingerprints are deliberately independent.

---

## 12. Canonical Identity Model

### 12.1 Story remains canonical entity

The existing `stories` row and `StoryId` remain the canonical identity root. The engine does not introduce a provider-derived canonical key.

### 12.2 Source membership is revisable

A `catalog_entries.story_id` assignment is the current durable host decision, not an eternal fact. Reconciliation may later propose moving a source to another Story through an atomic merge.

The system must never bypass the merge path by directly rewriting source ownership when downstream user state exists.

### 12.3 Retired StoryIds resolve to survivors

A durable redirect concept is required:

```text
StoryRedirect(
    retiredStoryId,
    canonicalStoryId,
    mergeEventId,
    createdAt
)
```

Invariant:

```text
retiredStoryId != canonicalStoryId
```

and redirect targets must be active canonical Stories.

### 12.4 Redirect chains are flattened

If:

```text
B → A
A → C
```

then the second merge transaction must produce:

```text
B → C
A → C
```

while the merge audit continues to record historical sequence.

No runtime component should depend on arbitrarily long redirect traversal.

### 12.5 Redirect resolution is centralized

Room-backed repository implementations must resolve input StoryIds through one storage identity resolver.

Flows that were subscribed using a StoryId before it became retired must not silently become permanently empty after merge. Implementation must use either:

- a redirect-aware Room query; or
- an observable resolved-ID flow with `flatMapLatest` to the current canonical owner.

The exact mechanism belongs in the implementation plan, but behavior is mandatory.

---

## 13. Durable Reconciliation Cases

### 13.1 Review is not an ephemeral enum

A durable case represents a canonical Story pair whose identity decision needs or previously needed review.

The logical model requires:

```text
caseId
canonical unordered Story pair
current revision
current status
createdAt
lastEvaluatedAt
```

Each evaluated revision requires:

```text
identityEvidenceFingerprint
reconciliationPolicyVersion
assessment trace
semantic decision
resolution if any
resolvedBy
resolvedAt
```

### 13.2 Pair identity is unordered

A case for `(A, B)` is the same semantic case as `(B, A)`.

Storage must canonicalize pair keys deterministically, e.g. lexical StoryId ordering, so duplicate reversed cases cannot exist.

### 13.3 Case status

The durable workflow supports at least:

```text
PENDING
DEFERRED
RESOLVED_MERGED
RESOLVED_SEPARATE
SUPERSEDED
```

`DEFERRED` means "do not prompt contextually now" and is not equivalent to `SEPARATE`. A defer suppression is tied to the current case revision/evidence fingerprint; materially new identity evidence may return the case to `PENDING` even if the previous revision was deferred.

### 13.4 Reopening

A resolved-separate case can be reconsidered only when:

```text
identityEvidenceFingerprint materially changes
OR
reconciliation policy version changes in an identity-relevant way
```

A normal refresh producing the same identity fingerprint does not reopen the case.

A reopened case creates a new assessment revision or otherwise preserves the previous resolution history; it must not overwrite the old rejection trace so completely that audit becomes impossible.

### 13.5 Resolution origins

At least:

```text
ENGINE
USER
```

must be distinguishable. Merge audit further records whether a committed merge came from auto reconciliation, user approval, or a manual maintenance tool.

---

## 14. Reconciliation Engine

### 14.1 Pure deterministic function

Given identical normalized evidence, current policy version, and candidate set, reconciliation must return the same assessment independent of provider order.

Semantic pair assessment must satisfy:

```text
assess(A, B) == assess(B, A)
```

apart from presentation ordering of explanation fields.

### 14.2 Candidate discovery is separate from decision

Exact `SourceKey(pluginId, sourceId)` identity is resolved before semantic candidate discovery. If an incoming `SourceKey` already exists, it resolves to its current canonical owner; Hikari never creates a second active owner for the same source key. Identity reevaluation may still compare that owner's Story against other canonical Stories when the source's identity fingerprint changes.

Candidate discovery is a high-recall shortlist operation. It may use indexed facts such as:

```text
work identifier equality
title/alias tokens
author overlap
content type
```

It only answers:

> Which canonical Stories are worth full comparison?

It does not decide identity.

Candidate-discovery algorithms and indexes may change for performance without changing semantic matcher behavior.

### 14.3 Evidence hierarchy

The engine uses four conceptual tiers:

```text
Tier 1 — identity-grade evidence
  compatible strong WORK identifiers

Tier 2 — semantic identity
  content type / medium
  explicit work/publication scope
  explicit lineage evidence when available

Tier 3 — strong descriptive evidence
  titles / aliases
  authors / creators

Tier 4 — weak supporting evidence
  publication timing
  genres
  other non-identity metadata
```

Tier 4 evidence cannot by itself cause auto-merge.

### 14.4 Hard conflict gates

Hard conflicts are evaluated before final automatic action. A numerical score cannot compensate for them.

Required v1 hard/review gates include:

- incompatible `ContentType`;
- explicit evidence of different work/adaptation lineage when available;
- mutually incompatible strong work-identity evidence;
- impossible source ownership invariant;
- later merge-safety conflict from protected user state.

A semantic hard conflict generally yields `SEPARATE` when evidence clearly proves different works. Strong positive evidence colliding with a hard identity conflict yields `REVIEW` rather than blind merge so data-quality errors remain recoverable.

### 14.5 Title similarity alone never auto-merges

The current match policy's title/author behavior is retained as the v1 fallback baseline when no strong work identifier is available:

```text
auto-link title similarity baseline: 0.92
review title similarity baseline:    0.75
auto-link author similarity:         0.50
minimum winning-candidate lead:      0.05
```

These values move into a versioned reconciliation policy and are test-owned constants rather than UI/storage literals.

For title/author-only auto-merge, author evidence must exist and satisfy the policy, the content type must be compatible, no hard conflict may exist, and the best candidate must lead the runner-up by the configured margin.

The plan may tune these values only through an explicit policy-version change backed by adversarial fixtures; it must not silently change them during refactoring.

### 14.6 Strong identifier branch

A compatible equal `WORK` identifier can provide identity-grade positive evidence even when author data is missing. It still cannot cross incompatible content type/lineage or contradictory work identity.

When multiple canonical candidates present conflicting strong identifier relationships, the action becomes `REVIEW` instead of choosing an arbitrary winner.

### 14.7 Decision model

Reconciliation separates the semantic conclusion from the persistence action. The same strong `SAME_WORK` conclusion maps differently depending on whether the incoming source already owns a persistent Story:

```text
new/unowned incoming SourceKey + existing canonical Story
  → AUTO_LINK source directly to existing Story
  → no temporary duplicate Story and no graph merge transaction

existing Story A + existing Story B
  → AUTO_MERGE request
  → full prepare/validate/atomic graph merge path
```

For an incoming source that reaches `REVIEW`, Hikari creates/retains a separate canonical Story for now so every source has one durable owner, and persists a reconciliation case against the candidate Story. Later approval uses the normal graph merge path.

An assessment must expose at least:

```text
policyVersion
semanticDecision
candidate confidence/ranking information
winning-candidate lead
positive evidence reasons
conflict reasons
title similarity where evaluated
author similarity where evaluated
identifier matches/conflicts
content-type/lineage compatibility
identityEvidenceFingerprint
```

The exact Kotlin shape may evolve, but callers must not need to reconstruct reasons from private matcher internals.

### 14.8 Semantic decisions

A `REVIEW` assessment also carries merge eligibility. V1 distinguishes:

```text
REVIEW_MERGEABLE
  ambiguity that a user can resolve, including protected-domain conflict after explicit resolution

REVIEW_INVARIANT_BLOCKED
  incompatible medium/lineage or unresolved contradictory work-identity evidence
```

Normal Review UI may offer `MERGE` only for mergeable cases. An invariant-blocked case must remain separate until the conflicting evidence is corrected/reclassified or a separately designed maintenance override exists. V1 does not provide a force-merge button that can violate `Story.contentType`/identity invariants.

#### `AUTO_MERGE`

Allowed only when:

- identity evidence satisfies an auto path;
- no semantic hard conflict exists;
- candidate ambiguity/lead rules pass;
- no durable separation decision for the same fingerprint/policy blocks it.

#### `REVIEW`

Used when:

- evidence is materially suggestive but not decisive;
- best and runner-up candidates are too close;
- strong positive and strong conflict evidence collide;
- semantic match is strong but merge-safety preparation detects protected user conflict.

#### `SEPARATE`

Used when evidence sufficiently establishes different works.

Low-confidence candidate failure is treated internally as `NO_MATCH`, not as a durable confirmed separation. `NO_MATCH` creates no reconciliation case/rejection row unless an existing case must be updated. Durable `RESOLVED_SEPARATE` state is reserved for explicit user `KEEP_SEPARATE` decisions or engine evidence strong enough to affirmatively distinguish the works. This avoids filling persistence with every weak candidate pair while preserving meaningful rejections.

The public matcher API may remain three-valued if `NO_MATCH` is represented outside the persisted decision enum, but storage/review behavior must preserve this distinction.

### 14.9 Retroactive reassessment

Any already-persisted source whose **identity evidence fingerprint changes** must be eligible for reconciliation again.

The existence of `metadataSnapshot(key)` must not bypass reconciliation by definition.

Example:

```text
Summary A + Summary B
→ insufficient evidence
→ separate Stories + durable review

Full B arrives with aliases/authors/identifier
→ fingerprint changes
→ reassess
→ AUTO_MERGE only if the new assessment passes every auto gate; otherwise REVIEW/SEPARATE
```

### 14.10 Post-merge contradictory evidence does not auto-split

Identity evidence can become stronger or contradictory even after sources have been merged under one canonical Story. When an intra-Story source revision creates a hard identity contradiction, the engine must not silently detach the source or automatically split user state.

Instead it creates/updates a correction review tied to merge lineage and the new evidence fingerprint. The controlled Reverse Planner may be offered when historical ownership and current domain state make a safe split provable. If safe reversal cannot be proven, the case remains review/manual-repair work.

A reconciliation-policy change that would no longer have auto-merged a historical pair also does **not** automatically undo a committed merge. Policy changes may flag the merged identity for review when the current evidence violates new hard invariants, but committed identity is changed only through the controlled reverse/split path.

### 14.11 Reassessment is not triggered by irrelevant refresh churn

Changing only score, cover, status, latest update, or fetched timestamp may rebuild fusion but does not rerun identity reconciliation.

---

## 15. Survivor Selection

Reconciliation determines whether Stories are the same. A separate survivor policy determines which StoryId remains active.

### 15.1 User-state footprint

To make "more user state" deterministic without allowing one high-cardinality table to dominate accidentally, the host derives a `UserStateFootprint` from meaningful domains.

At minimum it tracks presence/count information for:

```text
Library membership
reading progress
protected content mappings
explicit Story-level primary-source pin
user-owned/manual chapter aggregation overrides when present
```

Automated mappings, cache rows, source metadata, sync cursors, and other derived state do not count as meaningful user ownership.

### 15.2 Comparison

The v1 survivor comparison is lexicographic:

1. greater number of meaningful user-state domains containing state;
2. greater total amount of meaningful user state within those domains;
3. older trustworthy Story creation/identity timestamp **when both Stories have one**;
4. stable lexical `StoryId` tie-break.

Schema 8 does not persist a trustworthy Story creation timestamp, and current catalog `fetchedAt`/Full resolution timestamps are mutable metadata-lifecycle timestamps rather than Story birth time. The engine must not fabricate historical age from them.

The schema foundation therefore adds an optional host-owned Story identity creation timestamp for canonical Stories created from that point forward. Existing schema-8 Stories are backfilled as **creation time unknown** rather than assigned a fake age. When either side lacks trustworthy creation time, survivor selection skips the age comparison and uses the stable `StoryId` tie-break after user-state footprint. This is the only honest deterministic behavior for legacy rows.

### 15.3 Provider quality never affects survivor identity

A provider with better metadata can become primary after merge without its former StoryId becoming the survivor.

---

## 16. Metadata Fusion Engine

### 16.1 Input

The logical input is:

```text
FusionInput
- StoryId
- all source records currently owned by Story
- previous active canonical generation, if any
- Story-level source preference
- source availability/freshness facts
- fusion policy version
- primary-selection policy version
- evaluation time
```

No caller preselects a single source and no `firstOrNull()` becomes canonical truth.

### 16.2 Source usability states

Host policy classifies source records using facts into at least:

```text
ACTIVE
STALE
TEMPORARILY_UNAVAILABLE
UNAVAILABLE
RETIRED
```

The exact persisted representation can be simplified if some states are derived, but fusion must distinguish currently usable data from historical fallback data.

Fusion does **not** invent a second network-refresh TTL. Metadata-level freshness is derived from the existing catalog metadata lifecycle/policy and persisted provenance; provider/runtime availability comes from the source/runtime boundary. Fusion consumes those facts to rank already-persisted evidence.

Unavailability does not delete raw evidence.

### 16.3 Primary selection is generic

Automatic primary ranking uses host-observable facts only. V1 defines a deterministic quality vector:

```text
PrimaryQuality(
    usabilityClass,
    metadataLevel,
    freshnessClass,
    primaryFieldCoverage,
    stableSourceKey
)
```

with these descending class orders:

```text
usability: ACTIVE > STALE > TEMPORARILY_UNAVAILABLE > UNAVAILABLE > RETIRED
metadata:  FULL > SUMMARY
freshness: FRESH > STALE > UNKNOWN
```

`primaryFieldCoverage` counts qualified non-empty values from the bounded set used by primary/default presentation:

```text
description
coverUrl
sourceUrl
authors
aliases
genres
publicationStatus
latestUpdate
score
```

Title is required by the catalog contract and therefore is not useful as a coverage discriminator.

When a Story has no previous primary and no usable pin, the highest quality vector wins; `stableSourceKey` is only the final deterministic tie-break. Provider names and plugin-provided trust scores are absent from the vector.

### 16.4 Hysteresis

Let `P` be the current automatic primary and `C` a challenger. A pinned source is handled first by Section 16.5. In AUTO mode, the v1 switch rule is deliberately categorical rather than a fragile provider score.

`C` may replace `P` only when one of these conditions is true:

1. `C` has a strictly better usability class;
2. usability is equal and `C` has a strictly better metadata level;
3. usability and metadata level are equal, `C` has a strictly better freshness class, and `C` does not have lower primary-field coverage;
4. usability, metadata level, and freshness class are equal, and `C` has at least **two** more covered primary-quality fields than `P`;
5. `P` is no longer eligible to act as an effective primary.

Otherwise `P` remains primary even if `C` would win a fresh initial-selection tie. This is the v1 hysteresis margin. A one-field coverage advantage cannot cause source flapping.

Changing these class orders, the two-field coverage margin, or switch conditions requires `primarySelectionPolicyVersion` advancement and dedicated policy tests.

### 16.5 User pin

Logical preference:

```text
CanonicalSourcePreference
- storyId
- mode = AUTO | PINNED
- pinned SourceKey when PINNED
```

Rules:

- pinned usable source becomes effective primary;
- pin is not deleted when source becomes temporarily unusable;
- temporary fallback may be selected while preserving the pin;
- when the pinned source becomes usable again, it is reevaluated under pinned semantics;
- pin affects primary/default presentation, not all field-specific fusion.

### 16.6 Primary-oriented scalar fields

At minimum:

```text
title
description
source URL used for default "open catalog source" action, if exposed
popularity rank when a canonical value is needed
```

Policy:

1. use effective primary's qualified value;
2. if missing, use highest-ranked qualified fallback source;
3. do not fetch another provider to fill the field;
4. record the actual contributor and fallback strategy.

### 16.7 Cover/artwork

Artwork is selected by generic usable-source quality and presence.

Rules:

- primary cover is preferred when valid;
- missing/invalid primary cover permits fallback to the best qualified source;
- no provider-specific "better artwork" preset exists;
- selected source and reason are recorded.

### 16.8 Collection fusion

At minimum:

```text
aliases
authors
genres
language tags
```

are eligible for normalized union.

Rules:

- deterministic stable order;
- exact normalized-key deduplication;
- no fuzzy author merging;
- canonical aliases are formed from every qualified source's `title` plus its explicit aliases, then the selected canonical title is removed by normalized-key equality;
- provenance retains all contributing source keys.

### 16.9 Publication status

Status is a policy-selected scalar rather than blindly primary-owned.

V1 selection order:

1. values from currently usable Full metadata outrank Summary-only values;
2. among equal metadata levels, fresher qualified evidence outranks older evidence;
3. effective primary breaks otherwise equivalent candidates;
4. stable `SourceKey` breaks the final tie.

If only stale/historical evidence exists, the last qualified status may be retained while canonical health reflects staleness/degradation.

### 16.10 Latest update is a coherent object

`CatalogLatestUpdate` remains:

```text
(timestamp, releaseLabel)
```

The engine selects the entire object from one source. It must not combine timestamp from one source with label from another.

V1 selection:

1. consider qualified source objects;
2. prefer usable sources;
3. choose greatest `atEpochMillis`;
4. apply effective-primary then stable-source tie-break for equal timestamps;
5. retain the selected source in provenance.

`releaseLabel` is opaque presentation text. The UI renders it exactly as the canonical selected label subject only to ordinary text bounds/ellipsis.

The current duplicated `"Ch. Ch. 56"` behavior must be removed as a Phase-0 regression fix.

### 16.11 Score

Raw `Score(value, scale)` remains source-specific.

Canonical score v1 uses provider-agnostic normalization:

```text
normalized = value / scale
```

When more than one qualified usable source contributes, v1 uses an unweighted arithmetic mean of normalized values. This avoids hard-coded provider weights and does not pretend that unsupported vote-count weighting exists.

If only one score exists, that source is the sole contributor.

If future protocol revisions add objective vote counts, a new fusion policy version may adopt a documented weighted policy. Plugins still do not supply trust weights.

The existing generic `AggregateRanking` facility must not inject provider-ID weight maps into canonical fusion.

### 16.12 Content type

Canonical `Story.contentType` is an identity invariant, not a fused presentation vote. Sources with incompatible content type must not coexist in a successfully auto-merged Story.

### 16.13 Canonical health

Canonical presentation health is separate from metadata values:

```text
FRESH
STALE
REEVALUATING
DEGRADED
```

Suggested semantics:

- `FRESH`: active generation is current under relevant policy and at least one primary-quality source is usable/fresh;
- `STALE`: active generation is valid but freshness policy says the evidence is old;
- `REEVALUATING`: active generation remains valid while newer evidence/policy work is pending;
- `DEGRADED`: last valid generation remains readable but recent rebuild or source-health conditions prevent a fully qualified current result.

Health never instructs UI to read raw sources directly.

---

## 17. Canonical Generation Model

### 17.1 Immutable generations

A canonical metadata generation is immutable after persistence.

Logical model:

```text
CanonicalGeneration
- StoryId
- generationId
- fusionPolicyVersion
- primarySelectionPolicyVersion
- effectivePrimary SourceKey
- canonical metadata values
- health at promotion
- source/evidence fingerprints used
- createdAt
```

Field provenance is attached to the generation, not mutated independently afterward.

### 17.2 Field provenance

Each field records:

```text
field key
strategy
contributor SourceKey(s)
contributor source revision/fusion fingerprint used for this generation
reason code(s)
metadata level(s) where relevant
policy version
```

Recording the contributor revision is required so later raw-source refreshes do not make an old generation's explanation appear to refer to newer provider facts that were not actually used.

Representative strategies:

```text
PRIMARY_WITH_FALLBACK
NORMALIZED_UNION
FRESHEST_QUALIFIED_VALUE
FRESHEST_COHERENT_OBJECT
NORMALIZED_MEAN
```

### 17.3 Candidate build and promotion

Required flow:

```text
read generation N
    ↓
compute candidate N+1
    ↓
validate invariants
    ↓
compare meaningful canonical state
    ↓
persist candidate N+1
    ↓
atomically promote active pointer N → N+1
```

A failed candidate never partially updates active canonical metadata.

### 17.4 Meaningful-change suppression

A source refresh does not automatically create a visible generation.

If canonical values, primary, provenance meaning, policy versions, and health do not materially change, the engine may mark work complete without promoting a new visible generation.

### 17.5 Generation retention

V1 is not an event store.

Persist at least:

```text
active successful generation
immediately previous successful generation
any in-progress candidate needed for crash recovery
```

Older generations are cleanup-eligible after promotion unless a merge/reversal audit explicitly references required domain information elsewhere.

Merge audit is the durable history mechanism; canonical generation history is intentionally bounded.

---

## 18. Canonical Read Model

The long-term read model exposed by Catalog must distinguish canonical presentation from inspectable sources.

Conceptually:

```text
CanonicalStorySnapshot
- Story
- active canonical metadata generation
- health
- source preference
- list of source records / source summaries for inspection
```

`StoryCatalogSnapshot(entries)` may remain temporarily during migration, but feature code must stop treating ordering of `entries` as canonical field policy.

Canonical consumers must be able to read presentation metadata without iterating provider entries and recreating fusion logic.

---

## 19. Atomic Story Graph Merge

### 19.1 Explicit request

A merge begins with a durable/idempotent intent equivalent to:

```text
StoryMergeRequest
- left StoryId
- right StoryId
- reconciliationCaseId when applicable
- origin
- evidence fingerprint
- reconciliation policy version
```

### 19.2 Resolve redirects before planning

Before planning:

```text
left  = resolveCanonical(left)
right = resolveCanonical(right)
```

If both resolve to the same Story, the operation succeeds as an idempotent no-op and does not create a duplicate merge event.

### 19.3 Stale-plan protection

A merge plan records the canonical graph revision/identity state it was prepared against.

If an overlapping merge commits before this plan, the stale plan must be discarded and re-resolved/reprepared. It must not execute against outdated Story ownership.

### 19.4 Prepare/validate before transaction

Each domain produces a side-effect-free plan.

Required participants:

```text
Catalog
Library
Content Mapping
Chapters
Reader Progress
```

Additional Room-owned identity/redirect validation is also required.

Each participant can return:

```text
READY
REQUIRES_REVIEW
BLOCKED/INVALID
```

No authoritative write occurs during prepare.

### 19.5 One Room transaction

Only when all participants are `READY` may Room execute the graph migration.

If any authoritative write or invariant check fails, the transaction rolls back completely.

There is no supported state where Catalog believes `A == B` but Library/Progress still authoritatively owns `B` as a separate Story.

---

## 20. Domain Merge Semantics

### 20.1 Catalog source membership

All source records move losslessly to the survivor.

```text
A: MAL/123
B: MangaUpdates/456

merge B → A

A:
  MAL/123
  MangaUpdates/456
```

No source metadata is overwritten by another provider source.

A duplicate `(pluginId, sourceId)` ownership anomaly is an invariant violation, not a tie resolved by source priority.

### 20.2 Canonical source preference

Story-level source preference is user-owned canonical state and participates in merge preparation.

Rules:

```text
AUTO + AUTO
  → AUTO

PINNED(X) + AUTO
  → preserve PINNED(X)

PINNED(X) + PINNED(X)
  → preserve one PINNED(X)

PINNED(X) + PINNED(Y), X != Y
  → REQUIRES_REVIEW
```

A pinned `SourceKey` remains valid when that source record moves from the retired Story to the survivor. Conflicting explicit pins are not resolved by survivor choice or provider quality because they represent two user decisions.

Canonical generations themselves are derived state. The survivor's last valid active generation may remain visible immediately after merge if all of its contributor sources still belong to the merged survivor, but health becomes `REEVALUATING` and Fusion is marked dirty. The retired Story's generations never become an independent canonical truth after commit and are cleanup-eligible after audit/recovery requirements are satisfied.

### 20.3 Library membership

When only one Story is in Library, that membership moves to the survivor.

When both have Library entries, v1 Library merge policy is:

```text
addedAt = earliest addedAt
status  = value from the most recently updated Library entry
updatedAt = latest updatedAt
```

If timestamps tie and statuses differ, use a stable StoryId/source-row tie-break defined in the Library policy so the result is deterministic.

The Identity Core does not encode status precedence such as `COMPLETED > READING`.

### 20.4 Content mappings

For the same content plugin:

#### same target mapping

Coalesce. Protected origin is preserved over automated origin. When both mappings are protected and target the same `sourceStoryId`, the most recently updated protected mapping supplies the surviving protected origin/timestamp; an exact timestamp tie uses a stable enum/source-row tie-break. Because the target is identical, this is not a semantic mapping conflict.

#### protected vs automated, different targets

Protected mapping wins. The automated mapping is replaceable derived state.

#### automated vs automated, different targets

Discard/recompute derived automation after merge rather than treating either as user truth. The merge may commit and schedule mapping automation again.

#### protected vs protected, different targets

Automatic merge is blocked:

```text
semantic identity = SAME WORK
merge safety      = USER CONFLICT
effective action  = REVIEW
```

The Review workflow must allow the user to choose the mapping resolution before the graph merge can commit.

### 20.5 Content-mapping rejections

Rejections are unioned onto the survivor.

Duplicate identical rejection keys are coalesced. A Story merge must not erase prior user rejection history and cause a previously rejected mapping to be silently proposed again.

### 20.6 Canonical chapters

`CanonicalChapterId` remains stable.

The merge changes Story ownership but does not regenerate IDs even if an ID string contains historical Story-derived text.

### 20.7 Chapter releases

`ChapterReleaseId` remains stable. `chapter_storage_entries` remains valid because it is keyed by release ID rather than StoryId.

### 20.8 Chapter aggregation

Identity merge is intentionally lossless and conservative.

If A and B each contain a canonical chapter that appears to be "Chapter 10", the Story merge does not automatically destroy one group merely because the Stories were merged.

After merge, Chapter-domain aggregation may prove equivalence and coalesce using its own evidence/policy. Otherwise both groups remain until explicit chapter reconciliation resolves them.

### 20.9 Chapter aggregation overrides

Manual/user-owned overrides must be preserved. Any impossible conflict discovered during prepare must block automatic merge and require review rather than silently dropping the override.

### 20.10 Chapter sync state

Sync cursors/checkpoints are derived operational state.

When rows can move without conflict, move them. When two rows collide for the same post-merge source key, prefer invalidation/resync over inventing a merged cursor. The coordinator may delete/mark the conflicting derived sync state and schedule a fresh sync after commit.

### 20.11 Reading progress

Progress references keep stable canonical chapter IDs.

Most rows can therefore move by Story ownership without semantic reinterpretation.

If a post-merge duplicate for the same canonical chapter requires reconciliation, Reader-domain policy must preserve the semantically furthest safe progress when comparable. If different content fingerprints/releases make ordering unsafe, prepare returns `REQUIRES_REVIEW` rather than guessing.

The Identity Core never compares progress fractions across unrelated chapter content by itself.

---

## 21. Merge Transaction Ordering

Logical ordering inside the authoritative Room transaction:

```text
1. verify resolved survivor/retired IDs are still current
2. verify merge plan graph revision is current
3. apply domain conflict-resolution writes/deletes required before key moves
4. move catalog source ownership
5. merge/move Library state
6. merge/move content mappings and rejections
7. move chapter and release Story ownership without regenerating IDs
8. merge/move overrides and sync state according to prepared plan
9. move/reconcile reading progress
10. validate post-move authoritative invariants
11. write merge audit header + domain audit payloads using historical StoryId text
12. create/flatten Story redirects referencing the merge event
13. retire/delete the losing active Story row
14. commit
```

Actual SQL order may vary to satisfy foreign keys, but these semantic guarantees are mandatory:

- redirect is not externally active before dependent authoritative state is safe;
- the retired Story does not remain as an independent active canonical owner after commit;
- failure before commit leaves pre-merge graph intact.

### 21.1 Reconciliation cases after merge

Cases referencing the retired StoryId are identity state and must be normalized in the same authoritative transaction or by an immediately coupled transaction-safe step before the retired row disappears from active ownership.

Rules:

- the reconciliation case that authorized the committed merge becomes `RESOLVED_MERGED` and retains its original historical pair/revision trace;
- any duplicate still-active case for `(retired, survivor)` becomes `SUPERSEDED`;
- an active `(retired, X)` relationship is re-keyed conceptually to `(survivor, X)` for future decisions, while its old revisions retain historical IDs;
- if an active `(survivor, X)` case already exists, the current case state is coalesced without deleting either side's prior revisions;
- a re-key that resolves to `(survivor, survivor)` is superseded/no-op;
- current assessment must be reevaluated if the merged source set changes the identity evidence fingerprint.

### 21.2 Dirty work after merge

Pending work keyed by the retired StoryId is remapped/coalesced onto the survivor where still meaningful. Obsolete work for the retired identity is completed/superseded rather than retried forever. At minimum Fusion is marked dirty for the survivor after every successful merge.

---

## 22. Merge Audit and Controlled Reversal

### 22.1 Merge audit

Every non-no-op committed merge records:

```text
mergeEventId
survivor StoryId
retired StoryId
origin
reconciliationCaseId when applicable
evidence fingerprint
policy version
mergedAt
pre-merge user-state footprint
per-domain merge summary
reversibility state
```

Origins include at least:

```text
AUTO_RECONCILIATION
USER_REVIEW_APPROVAL
MANUAL_MAINTENANCE
```

### 22.2 Audit is not a database snapshot

Hikari does not clone the entire graph for each merge.

Domains persist only the minimal reversible information they uniquely need, for example:

- prior Library rows when two rows were coalesced;
- original protected mapping ownership/targets when needed;
- original source membership;
- original chapter Story ownership;
- relevant post-merge state fingerprints used to decide whether an automatic reverse is still safe.

### 22.3 Reversibility states

At least:

```text
REVERSIBLE
REQUIRES_REVIEW_TO_REVERSE
NOT_AUTOMATICALLY_REVERSIBLE
```

Immediately after a simple lossless merge, the event may be `REVERSIBLE`.

Later user changes can move it to a review-required state logically, even if that status is calculated rather than eagerly rewritten.

### 22.4 Reverse planner

Reverse uses the same principles as forward merge:

```text
request reverse
    ↓
domain prepare/validate
    ↓
all safe?
  yes → one atomic split transaction
  no  → review/manual resolution
```

No domain is independently rolled back while the rest remains merged.

### 22.5 Ambiguous post-merge state blocks blind reversal

Examples:

```text
new protected mapping created after merge
new source linked after merge with no clear pre-merge owner
new chapter graph derived from combined mappings
user edited merged Library state after two historical states were coalesced
```

These conditions require review unless the relevant domain can prove a safe split.

---

## 23. Review Workflow

### 23.1 One case, multiple surfaces

Both:

```text
Review Queue
Contextual Story prompt
```

read and mutate the same durable `ReconciliationCase`.

### 23.2 User actions

At minimum for mergeable cases:

```text
MERGE
KEEP_SEPARATE
DEFER
```

Invariant-blocked review cases omit/disable `MERGE` and explain the blocking reason.

`MERGE` requests the canonical merge path. UI never updates Story ownership itself.

`KEEP_SEPARATE` creates a durable resolution tied to evidence fingerprint/policy.

`DEFER` suppresses contextual prompting according to UI policy while leaving the case reviewable in the queue.

### 23.3 Contextual prompt eligibility

A contextual prompt is allowed only when:

```text
case is pending/reviewable
relevant Story is currently visible
assessment confidence is high enough to be useful
case is not under defer/cooldown suppression
```

It must not appear on every navigation or refresh.

### 23.4 Queue ranking

Review Queue ranking may use presentation-only priority factors:

```text
higher ambiguity confidence
more user-state impact
newly changed evidence
case age
```

Queue priority does not alter semantic reconciliation decisions.

### 23.5 Protected conflict resolution

When user says two Stories are the same but protected domain state conflicts, the review flow may require a second explicit choice, such as which content mapping is correct. The final merge does not commit until all `prepare` participants become `READY`.

---

## 24. Event-Driven Orchestration

### 24.1 Event types

Representative domain signals:

```text
SourceSummaryChanged
SourceFullChanged
SourceAvailabilityChanged
SourceLinked
SourceUnlinked
UserPinnedPrimary
UserUnpinnedPrimary
ReconciliationReviewResolved
PolicyVersionChanged
StoryMerged
```

Names may change during implementation; semantics must remain narrow.

### 24.2 Events describe state change, not business commands

Allowed payload meaning:

```text
which source/story changed
which persisted revision/fingerprint changed
```

Forbidden event semantics:

```text
"load MangaUpdates details because MAL lacks authors"
"prefer provider X"
```

### 24.3 Coordinator behavior

A coordinator may route events to use cases:

```text
EvidenceChanged
  ├─ identity fingerprint changed → ReconciliationUseCase
  └─ fusion fingerprint changed   → FusionUseCase

ReviewResolved(MERGE)
  → MergeUseCase

StoryMerged
  ├─ mark Fusion dirty
  ├─ schedule derived chapter work if needed
  └─ invalidate/read-model projections
```

It must not contain provider-specific policy, missing-field enrichment heuristics, or merge semantics.

### 24.4 Coalescing

Dirty work is keyed so rapid events can collapse to latest persisted state rather than forcing replay of every intermediate refresh.

Typical key:

```text
StoryId + workType
```

---

## 25. Background Safety Work

### 25.1 Dirty-work model

Durable work must represent at least:

```text
RECONCILE
FUSE
POLICY_REEVALUATE
DERIVED_REBUILD
```

and carry:

```text
StoryId/source context
reason
relevant policy version
attempt count
next attempt time
last failure class/code where appropriate
```

### 25.2 Background uses the same use cases

Background worker invokes the same Reconciliation/Fusion/Merge-related use cases as foreground/event-driven execution. There is no second matcher or alternate fusion policy.

### 25.3 No frequent full database scan

Normal operation uses dirty markers/queues.

A periodic maintenance pass may look for:

```text
stale-policy generations
orphaned dirty work
retryable failures
redirect consistency issues
pending cases whose evidence revision changed
```

It must not compare every Story against every other Story on a timer.

### 25.4 Retry classification

#### transient

Examples:

```text
worker interrupted
process death
short-lived database failure
```

→ retry with bounded backoff.

#### semantic/policy conflict

Examples:

```text
ambiguous identity
protected mapping conflict
hard evidence disagreement
```

→ durable REVIEW/SEPARATE state, not retry loop.

#### invariant violation

Examples:

```text
redirect cycle
missing required Story owner
impossible duplicate source ownership
```

→ mark degraded/diagnostic and stop automatic mutation until repaired.

#### derived rebuild failure

Examples:

```text
canonical generation build failure
chapter reaggregation failure
```

→ retain authoritative state/last valid generation and retry derived work.

### 25.5 Process-death safety

Important states are durable:

```text
review case
redirect
merge audit
active generation pointer
dirty work
```

An unpromoted generation candidate after restart may be validated and completed or discarded/rebuilt. UI state is never used as recovery truth.

### 25.6 Scheduler ownership

`:catalog`, `:library`, `:chapters`, and `:reader` remain free of `androidx.work` and Android scheduling concerns. They expose use cases and durable work semantics only.

A platform scheduler adapter belongs at the existing app/infrastructure boundary. If this engine reaches Phase 7 before Wave 10's planned background-scheduling infrastructure exists, process-start/foreground maintenance may drain durable work without introducing WorkManager into domain modules. When Wave 10 scheduling exists, it may invoke the same maintenance use case.

This preserves one work queue and one policy implementation regardless of scheduler mechanism.

---

## 26. Policy Versioning

The engine uses independent policy versions:

```text
reconciliationPolicyVersion
fusionPolicyVersion
primarySelectionPolicyVersion
```

Do not use one monolithic engine version for all invalidation.

Examples:

- changing score aggregation increments `fusionPolicyVersion`, not reconciliation;
- changing primary switch margin increments `primarySelectionPolicyVersion`;
- changing title/identifier match semantics increments `reconciliationPolicyVersion`.

A review rejection only reopens for a policy change relevant to reconciliation identity semantics.

Canonical generations record both fusion and primary-selection policy versions.

---

## 27. Policy-Change Reevaluation

On a relevant policy change:

```text
existing generation/case remains readable
        ↓
mark needs reevaluation
        ↓
active/recent Story work may run promptly
        ↓
background gradually drains the rest
```

No provider refetch is inherently required because raw evidence remains persisted.

If a new policy requires a fact that was never collected, the existing evidence is simply missing. The core engine does not silently fetch to compensate. A separately designed enrichment/product lifecycle may collect new facts later.

---

## 28. Source Availability and Staleness

### 28.1 Source state is not source deletion

Plugin disable, temporary API failure, or source unavailability changes eligibility, not historical ownership.

### 28.2 Historical fallback

If all better active evidence disappears, the active canonical generation may continue exposing older canonical values from the last valid generation while health becomes `STALE` or `DEGRADED`.

### 28.3 Pinned unavailable source

The user's pin remains stored.

An effective temporary primary may be chosen so the Story remains usable. The UI may indicate source unavailability where appropriate, but the engine does not erase the preference.

### 28.4 Source returning

Availability restoration marks fusion dirty. Hysteresis/pin policy then decides whether primary changes; it is not forced merely because the source returned.

---

## 29. Search Integration

### 29.1 Search remains multi-provider and source-preserving

Enabled catalog sources may still be queried concurrently.

Search results are persisted/reconciled as source facts and grouped by canonical Story identity.

### 29.2 Search cards use canonical presentation

After source Summary persistence and canonical identity resolution, Search cards use canonical generation/read-model data rather than choosing `story.sources.firstOrNull()` as presentation truth.

During the migration phase, a temporary canonical generation may be built from local Summary evidence; Search must not call Details merely to decorate result cards.

### 29.3 Selecting a result navigates canonical StoryId

Search selection must no longer mean "choose first source and load its Details as the Story".

It navigates to canonical Story identity. The Story metadata lifecycle may then request Full metadata according to explicit Story-detail requirements.

### 29.4 Full metadata fallback is operation-level

When Story detail explicitly requires a Full request:

```text
effective primary source
  fails/unavailable
    → next ranked eligible source
```

A successful Full response with optional missing fields ends that source operation successfully. Missing optional fields do not trigger hidden provider hopping.

---

## 30. Story Screen Integration

### 30.1 AUTO mode

Default Story presentation consumes the active canonical generation.

It does not sort raw `CatalogEntry` by plugin ID and select the first one as canonical title/description.

### 30.2 Source inspection

The Sources area may still list each provider source and display raw source-specific metadata when the user explicitly inspects it. That is an inspection mode, not canonical truth.

### 30.3 User primary pin

The Story source UI can expose:

```text
AUTO
PIN THIS SOURCE
RETURN TO AUTO
```

Pin changes canonical preference and triggers fusion reevaluation.

### 30.4 Explicit source refresh

Refreshing a selected source may request that source's Full metadata through the existing lifecycle. The result becomes new evidence and may trigger reconciliation/fusion. UI does not apply the fetched values directly to canonical state.

---

## 31. Discover Integration

Discover retains source-provided semantic feed membership (`POPULAR`, `LATEST_UPDATES`, `TOP_RATED`) and cached Home semantics, but it stops implementing provider selection/fusion itself.

Required flow:

```text
cached Home feed membership
       ↓
map source items → canonical StoryIds
       ↓
deduplicate by canonical StoryId
       ↓
join active canonical generation
       ↓
Discover presentation projection
```

The feature may still rank/filter by semantic feed rules, but metadata fields come from canonical generation.

The existing feature-local `presentationOrder` must no longer be the authority for title/cover/status/score/latest-update fusion after the canonical read path is available.

Discover must remain Summary/cache-driven. It must not call Details to fill cards.

`Latest Updates` continues to use source-reported story update time, never cache-fetch time. The canonical `latestUpdate` object selected by Fusion preserves the source timestamp/label pair.

The `releaseLabel` UI must render the opaque canonical label directly; no `"Ch. "` prefix is added.

---

## 32. Library Integration

Library identity remains canonical `StoryId`.

Library cards read canonical metadata generation for presentation and do not independently choose provider entries.

A Story redirect must make existing Library navigation using a retired ID resolve to the survivor without exposing a duplicate/missing Story state.

Library merge semantics remain domain-owned as specified above.

---

## 33. Chapter and Reader Integration

Chapter sync continues to operate from protected content mappings, not catalog source preference.

A catalog primary-source change does not automatically remap readable content sources.

Story merge may schedule:

```text
chapter aggregation re-evaluation
sync-state refresh/resync
automated content-mapping refresh
```

as derived work after authoritative identity commit.

Reader progress remains anchored to stable canonical chapter/release identifiers and must survive Story merge without ID regeneration.

---

## 34. Required Persistent Concepts

The exact Room table split may be optimized during implementation, but schema foundation must persist the following concepts with equivalent constraints.

### 34.1 `catalog_entry_identifiers`

Logical key:

```text
(plugin_id, source_id, namespace, value, scope)
```

Requirements:

- FK to `catalog_entries(plugin_id, source_id)` with source deletion lifecycle consistent with the parent;
- indexes supporting namespace/value candidate lookup;
- bounded validated values;
- no provider trust score.

### 34.2 `story_redirects`

Logical fields:

```text
retired_story_id PK
canonical_story_id
merge_event_id
created_at_epoch_millis
```

V1 uses one explicit persistence model:

- after all children move, the retired row is removed from `stories`;
- `story_redirects.retired_story_id` is historical text and intentionally has no FK to `stories`;
- `story_redirects.canonical_story_id` references the active surviving `stories(story_id)` row;
- `merge_event_id` references/identifies the append-only merge audit record without cascade semantics that could erase lineage.

Requirements:

- retired ID cannot equal target;
- target must be an active Story at commit;
- chains are flattened on merge;
- cycle is impossible by transaction validation;
- deleting/retiring a Story can never cascade-delete its redirect history.

### 34.3 Canonical Story state

A persisted state row per active Story holds at least:

```text
active_generation_id
canonical health / reevaluation state
AUTO/PINNED source preference
pinned SourceKey when pinned
optional trustworthy Story identity creation timestamp (`null` for migrated legacy rows whose true age is unknown)
```

The implementation may split preference from active-generation state if that gives clearer constraints.

### 34.4 Canonical generations

Persist immutable canonical metadata, effective primary, policy versions, evidence/fusion fingerprint, creation time, and promotion state.

### 34.5 Canonical field provenance

Persist per-field strategy/contributors/reason data in a bounded host-owned form. A normalized child table is preferred over a large unbounded arbitrary JSON blob when it keeps queries/tests clearer.

If a serialized structured payload is chosen, it must have a versioned schema, bounded size, deterministic encoding, and tests. It must not store arbitrary plugin JSON.

### 34.6 Reconciliation cases and revisions

Persist stable unordered Story-pair case identity plus versioned assessment revisions/history so rejected cases can be reopened without erasing why they were previously resolved.

Current unresolved-case indexes/rows must be re-keyable when a Story retires. Historical revision StoryIds are audit text and must not depend on an FK to an active `stories` row. The schema must be able to preserve a `RESOLVED_MERGED` revision after one of its historical StoryIds has been deleted from active `stories`.

### 34.7 Story merge events

Persist append-only merge audit metadata and minimal domain-specific reversible payloads. `survivorStoryId` and `retiredStoryId` inside merge history are historical text, not cascading foreign keys to active `stories`, because the survivor may itself be retired by a later merge. Audit lineage must survive the entire redirect chain.

### 34.8 Engine dirty work

Persist coalescible work keyed by Story/work type with retry metadata.

---

## 35. Room Schema Versioning and the Existing Wave 10 Reservation

This is a normative repository-governance decision.

Current repository state:

```text
Room schema = 8
```

Existing roadmap:

```text
Wave 10 notification persistence planned as 8 → 9
```

The roadmap explicitly allows a separately reviewed migration to be introduced first. This canonical engine design is that reviewed migration **if implementation begins before Wave 10**, which is the intended ordering for this work.

Therefore:

```text
Canonical engine foundation: 8 → 9
Wave 10 notification persistence: rebase from planned 8 → 9 to 9 → 10
Wave 11 then enters on schema 10 unless another reviewed migration intervenes
```

The implementation plan must update the current roadmap/handbook references that currently reserve schema 9 for Wave 10. Historical documents/checkpoints are not rewritten.

If implementation ordering changes and Wave 10 reaches schema 9 before this engine starts, the canonical engine must instead rebase contiguously from the actual current schema. It must never create two distinct `MIGRATION_8_9` meanings.

### 35.1 One schema foundation migration for this engine

To avoid burning one Room version per implementation task, the first schema-focused phase should introduce the durable table/column foundation required by later engine phases in one reviewed migration. Later phases should consume that foundation without additional schema changes unless a proven design gap requires another reviewed migration.

### 35.2 Migration tests

The migration must be tested from an actual schema-8 fixture containing representative rows for:

```text
catalog entries + Home references
Library entries
protected/automated content mappings
mapping rejections
canonical chapters/releases/overrides/sync state
reading progress
chapter storage references
```

Fresh-install tests are insufficient.

### 35.3 Existing-data bootstrap and read-path cutover

Room migration itself must not try to run Kotlin reconciliation/fusion logic in SQL, and it must not fabricate canonical metadata using a simplified provider-first rule. Therefore schema-8 Stories initially migrate with canonical engine state present but may have no active generation yet.

V1 bootstrap behavior is:

1. schema migration creates required canonical state rows, empty identifier/review/redirect history where appropriate, and marks Stories that need initial fusion;
2. after application startup, a `CanonicalBootstrapUseCase` (name non-normative) builds generations from **persisted local source evidence only**;
3. normal dirty-work processing drains remaining Stories in the background/foreground maintenance path;
4. when a canonical read is requested for a Story whose initial generation has not yet been built, the Catalog layer may synchronously/priority-build that one generation off the main thread from local evidence before returning canonical presentation. This is a one-time engine bootstrap, not UI raw-source fallback and not network enrichment;
5. feature code never receives raw source entries as an emergency canonical presentation path. It receives canonical `Preparing`/health state or the built generation.

The implementation should prewarm Stories visible in Home/Library before broad background backlog to minimize placeholders after upgrade. Once a Story has an active generation, its steady-state UI read path remains `UI → active canonical generation`.

Destructive automatic Story merge must not be enabled until both candidate Stories can be represented through canonical engine state and post-merge Fusion can be scheduled reliably.

---

## 36. Concurrency, Idempotency, and Atomicity

### 36.1 Reconciliation idempotency key

At minimum:

```text
canonical/source context
identity evidence fingerprint
reconciliation policy version
```

Repeated evaluation of identical input must not create duplicate pending cases.

### 36.2 Fusion idempotency key

At minimum:

```text
StoryId
fusion evidence fingerprint
fusion policy version
primary-selection policy version
source preference revision
```

Repeated identical work must not publish duplicate semantically identical generations.

### 36.3 Merge idempotency

Retrying the same merge request after a successful commit resolves both IDs to the same survivor and becomes a no-op.

It must not create:

```text
duplicate redirect
duplicate merge event
duplicate domain migration
```

### 36.4 Overlapping merge serialization

Requests such as:

```text
A + B
B + C
```

must not both commit from stale independent plans.

The Room coordinator must serialize or version-check canonical graph mutations. The second operation re-resolves identities after the first commit.

### 36.5 Atomic generation promotion

The active-generation pointer update and final generation-valid marker must be in one transaction so no observer can see an active pointer to an incomplete generation.

---

## 37. Failure and Recovery Semantics

### 37.1 Reconciliation failure

Pure policy must not throw for ordinary missing evidence. Missing facts either leave the candidate below review relevance (`NO_MATCH`) or produce `REVIEW` when the configured review evidence threshold is met. They never trigger hidden fetching.

Unexpected invariant failure is diagnostic/degraded work, not an invitation to fetch more provider data.

### 37.2 Fusion failure

Keep current active generation. Record/mark degraded or retryable work. Do not expose a half-built candidate.

### 37.3 Merge preparation failure

No authoritative writes have happened. Handling is classified explicitly:

- semantic/user-owned conflict → persist/update `REVIEW`;
- invariant violation → block automatic mutation and record diagnostic/degraded state;
- transient infrastructure failure → keep the request retryable and reprepare before any later commit.

### 37.4 Merge transaction failure

Room rollback leaves both Stories unchanged. Retry only if failure is transient and the plan is still current; otherwise reprepare.

### 37.5 Derived post-merge failure

Identity merge remains committed. Derived work retries independently.

Examples:

```text
canonical fusion rebuild failed
chapter reaggregation failed
automatic mapping refresh failed
```

The graph still has one canonical Story identity.

---

## 38. Observability and Explainability

Debug/test tooling must be able to answer:

```text
Why was this candidate considered?
Why AUTO_MERGE / REVIEW / SEPARATE?
Why did a rejected case reopen?
Why did primary stay or switch?
Why did field X come from source Y?
Why was a merge blocked?
Why did generation promotion fail?
Which policy version produced this decision?
```

Persisted/logged reason information should use bounded host-owned reason codes and compact evidence summaries.

Do not dump entire plugin payloads or arbitrary user-state histories into diagnostics.

---

## 39. Performance Requirements

### 39.1 UI read path

Normal presentation path:

```text
UI → active canonical generation
```

not:

```text
UI → all sources → matcher → ranking → fusion
```

Provider count must not make Compose recomposition cost linearly rerun canonical reasoning.

### 39.2 Candidate matching

Use indexes/shortlisting before pair assessment. No `O(all stories²)` regular scan.

### 39.3 Dirty work

Use coalesced dirty markers rather than full periodic recomputation.

### 39.4 Fingerprints

Avoid reconciliation on fusion-only changes and avoid generation promotion on semantically unchanged results.

### 39.5 Benchmark scope

Pure policy phases do not require macrobenchmark on every task. Read-path integration phases affecting Discover/Search/Story/Library must rerun the relevant connected/UI/performance gates defined in the implementation plan.

---

## 40. Rollout Phases

Architecture is complete now; implementation remains phased.

### Phase 0 — Contract hardening and regression characterization

Deliverables:

- document/test `releaseLabel` as opaque complete presentation label;
- fix `Ch. Ch. 56` behavior;
- add/lock regression tests around current matcher behavior;
- add external-identifier wire/domain contract and bounded validation;
- establish identity/fusion fingerprint contracts;
- characterize current Search/Story/Discover source-selection behavior with tests before replacement.

No destructive Story merge is enabled.

### Phase 1 — Schema and canonical-generation foundation

Deliverables:

- reviewed Room schema migration foundation;
- source identifier persistence/indexing;
- redirect persistence;
- canonical Story state/preference;
- generation + provenance persistence;
- reconciliation-case/revision persistence;
- merge audit foundation;
- dirty-work persistence;
- redirect-aware identity resolver;
- migration tests from schema 8;
- roadmap schema-number rebase.

Unused future-phase tables are acceptable only because they are part of the approved engine foundation and prevent repeated schema churn. They must still have DAO/model tests and bounded constraints.

### Phase 2 — Metadata Fusion Engine

Deliverables:

- pure source usability classification;
- generic automatic primary selection;
- hysteresis;
- AUTO/PINNED preference;
- field-specific fusion;
- provenance;
- generation validation/promotion;
- failure keeps last valid generation;
- canonical read model.

Then migrate feature presentation progressively to canonical generations:

```text
Story
Search
Discover
Library
```

Exact order may be chosen for low-risk integration, but each migrated feature must stop creating its own provider truth.

### Phase 3 — Reconciliation Engine in observe-only mode

Deliverables:

- evidence normalization;
- external identifier evidence;
- candidate discovery/index use;
- hard conflict gates;
- explainable assessment;
- durable cases/rejections;
- retroactive fingerprint-triggered reassessment logic;
- adversarial fixture suite.

Initially, prospective auto-merges may be recorded/diagnosed without executing graph mutation. This phase is specifically intended to validate false-positive risk against real provider data.

### Phase 4 — Atomic identity merge

Deliverables:

- survivor policy;
- user-state footprint;
- domain prepare/validate policies;
- Room graph merge coordinator;
- all-or-nothing transaction;
- redirect flattening;
- merge audit;
- stale-plan/concurrency handling;
- idempotent retry;
- FK/invariant tests.

Only after this phase's safety tests pass can `AUTO_MERGE` perform real graph mutation.

### Phase 5 — Review workflow

Deliverables:

- Review Queue;
- contextual review;
- MERGE / KEEP_SEPARATE / DEFER;
- protected mapping conflict resolution;
- durable suppression/reopen behavior;
- accessibility/UI tests for review flows.

### Phase 6 — Runtime orchestration and retroactive reconciliation

Deliverables:

- evidence-change events/dirty marking;
- Details-derived identity revision triggers;
- fusion-only vs reconciliation change separation;
- event coalescing;
- post-merge derived-work scheduling;
- product-level Full metadata source fallback without missing-field self-healing.

### Phase 7 — Background safety and controlled reversal

Deliverables:

- retry/backoff worker;
- policy-version backlog reevaluation;
- lightweight maintenance consistency pass;
- reverse planner;
- domain reversal validation;
- audit/debug tooling sufficient to explain and repair a mistaken merge.

---

## 41. Test Strategy

### 41.1 Pure reconciliation tests

Required categories:

```text
exact same title, missing authors → no title-only auto merge
same title + author strong → auto only when lead/hard gates pass
same title + different ContentType → no auto merge
same work identifier + compatible medium → strong positive path
same identifier + hard medium conflict → review, not blind merge
different work identifiers + similar title → no false auto merge
two close candidates → review
candidate lead above threshold → deterministic winner
A/B assessment symmetry
ingestion/provider order does not change semantic result
unchanged fingerprint does not reopen rejection
identity-relevant new evidence does reopen/reassess
fusion-only evidence does not rerun identity match
```

### 41.2 Adversarial identity fixtures

Include realistic cases for:

- manga vs light-novel adaptation with near-identical titles;
- sequel numbered similarly to original;
- spin-off sharing creators/title stem;
- alternate language/transliteration;
- edition/re-release of same work;
- one-shot precursor vs serialization;
- creator spelling/romanization variants;
- exact title collision across unrelated authors;
- missing author data;
- erroneous/conflicting strong identifier;
- multiple candidate Stories with similar scores.

### 41.3 Pure fusion tests

Required:

```text
primary stays on marginal challenger improvement
primary switches on material improvement
primary switches when current source degrades
pinned usable source wins primary
pinned unavailable source keeps preference but allows effective fallback
returning pinned source is reevaluated
primary title/description with fallback
cover fallback
normalized union aliases/authors/genres
no fuzzy author collapse
status Full/freshness/primary tie rules
latestUpdate timestamp+label stay from same source
latestUpdate chooses newest qualified object
releaseLabel rendered without prefix fabrication
score normalization and unweighted mean
provider order does not change result
provider ID does not affect result except final stable tie-break
same evidence/policy/prior state gives same generation candidate
semantically unchanged result suppresses unnecessary promotion
```

### 41.4 Generation persistence tests

Required:

```text
candidate not visible before promotion
promotion switches active pointer atomically
failed build retains previous active generation
field provenance points only to owned source records
previous generation retention policy
crash-style unpromoted candidate recovery
policy versions persisted correctly
```

### 41.5 Room migration tests

Use schema-8 fixtures containing representative complete graph state.

Verify:

- all schema-8 data survives migration;
- Home foreign-key references remain valid;
- external identifier tables start empty safely;
- canonical state/generation foundation initializes deterministically;
- no legacy StoryId changes during schema migration itself;
- Room schema JSON is exported/committed contiguously;
- foreign-key check passes.

### 41.6 Merge integration tests

Required scenarios:

```text
catalog-only A+B merge
Library only on one side
Library on both sides
protected mapping vs automated
protected mapping vs conflicting protected → no writes
mapping rejections union
chapters move with stable IDs
chapter releases move with stable IDs
chapter storage remains valid by release ID
sync-state collision becomes resync/derived work
progress moves without ID regeneration
progress conflict uses Reader policy / review gate
redirect created only on successful commit
redirect chain flattening
old StoryId reads resolve survivor
observer opened before merge follows redirect to survivor
merge retry after success is no-op
overlapping A+B / B+C stale-plan handling
intentional transaction failure rolls back every authoritative table
foreign-key check after merge
```

### 41.7 Review tests

Required:

```text
pending case appears once
same fingerprint refresh does not duplicate/reopen
DEFER suppresses contextual prompt but remains in queue
KEEP_SEPARATE persists durable rejection
new identity fingerprint can create new review revision
user MERGE uses same merge coordinator as auto merge
protected conflict requires explicit resolution before commit
```

### 41.8 Feature/instrumentation tests

Required observable behaviors:

```text
Search/Story/Discover/Library show same canonical title/cover/status for one generation
source inspection still displays raw provider-specific values
pinning changes primary without disabling latestUpdate/collection fusion
retired Story navigation resolves survivor
fusion failure keeps visible canonical content
review queue/contextual prompt share one case state
Discover does not call Details for missing listing presentation fields
Story Full fallback is source-operation failure based, not optional-field based
```

### 41.9 Performance verification

After canonical read-path integration:

- existing `./scripts/verify.sh` remains green;
- architecture/static/package-boundary gates remain green;
- relevant connected app navigation tests rerun;
- Discover macrobenchmark reruns if Discover data path changes materially;
- Story tab/transition benchmark reruns if Story canonical observation materially changes;
- no UI read path performs per-recomposition full source fusion.

---

## 42. Acceptance Invariants

The implementation is not complete until tests can prove these statements.

### Identity

```text
One provider SourceKey has exactly one active canonical owner.
A retired StoryId resolves deterministically to one active Story.
Redirect cycles cannot exist.
Pair assessment is symmetric.
Provider ingestion order does not change semantic identity result.
Title similarity alone cannot auto-merge.
Hard semantic conflicts cannot be outweighed by a high similarity score.
Strong identifiers cannot bypass incompatible medium/lineage.
```

### Review

```text
REVIEW is durable.
Refresh with unchanged identity evidence does not resurrect a resolved rejection.
Relevant new evidence or reconciliation-policy change can reopen/reassess.
A policy change never auto-splits an already committed merge; correction uses controlled review/reversal.
DEFER is not equivalent to SEPARATE.
```

### Merge

```text
Protected conflicting user state cannot be silently auto-merged.
Merge is all-or-nothing across authoritative Story-owned state.
Stable chapter/release IDs survive Story merge.
A successful repeated merge request is idempotent.
A stale overlapping merge plan cannot commit.
Merge history is auditable.
Automatic reversal occurs only when every affected domain proves it safe.
```

### Fusion

```text
Raw provider facts are never overwritten by canonical fusion.
Primary source is not assumed to own every canonical field.
User pin is respected but does not disable field fusion.
Provider names do not influence core quality policy.
A canonical generation is internally consistent and atomically promoted.
Latest-update timestamp and label always come from the same source object.
Canonical score uses provider-agnostic normalized contributions.
```

### UI/runtime

```text
UI reads canonical presentation rather than reconstructing provider truth.
UI never falls back directly to raw source data as a hidden repair path.
Last valid generation remains readable during reevaluation/failure.
Discover remains Summary/cache-driven and does not Details-enrich missing card fields.
Engine reasoning never invokes network fetching.
Foreground and background use the same policies/use cases.
```

### Evolution

```text
A new catalog provider that follows the host contract does not require core provider-ID code.
Relevant policy changes can reevaluate persisted evidence without mandatory provider refetch.
Provider unavailability does not erase the canonical Story or its historical source evidence.
```

---

## 43. Documentation and Governance Updates Required During Implementation

Because this engine changes current roadmap/schema assumptions, implementation must update current normative docs, not just code.

At minimum review/update:

```text
docs/README.md
docs/PROJECT-HANDBOOK.md
docs/project/current-state.md
docs/implementation/current-roadmap.md
docs/implementation/waves/wave-10-background-sync-auth-and-notifications.md
docs/implementation/waves/wave-11-hardening-open-source-release.md
plugin-sdk catalog protocol docs if external identifiers are added
```

Historical checkpoints and archived plans must not be rewritten to claim work they did not contain.

This design document should be linked as the normative supersession for canonical catalog identity/fusion behavior.

---

## 44. Implementation Constraints

The future implementation plan must obey these constraints:

1. use TDD for each pure policy and migration/merge behavior;
2. do not combine the whole engine into one patch;
3. do not enable destructive `AUTO_MERGE` before observe-only reconciliation fixtures and graph-merge integration tests pass;
4. do not add provider-specific priorities as a shortcut;
5. do not bypass domain ownership by placing Library/Chapter/Reader semantics in `:catalog` or Room SQL alone;
6. do not put Room types into feature/domain public interfaces;
7. do not make UI responsible for redirect resolution;
8. do not change schema numbering without updating current roadmap governance;
9. do not introduce a second hidden metadata-enrichment path beside the explicit lifecycle;
10. do not treat derived-work failure as reason to roll back an already valid authoritative identity merge;
11. do not remove raw source records during fusion;
12. do not regenerate stable chapter/release IDs during Story merge;
13. do not ship a dead Review UI action before its resolution path exists;
14. do not publish a generation until validation succeeds;
15. do not claim reversal is safe without domain prepare/validate proof.

---

## 45. Deliberately Deferred Extensions

These are compatible with the architecture but intentionally not requirements for v1:

- per-field user source pinning;
- richer explicit adaptation/sequel/spin-off relation protocol beyond current content-type and identifier evidence;
- provider-independent objective rating vote-count weighting if protocol later exposes vote counts;
- long-term canonical-generation history UI;
- advanced admin graph explorer;
- cross-device/cloud merge audit synchronization;
- event-sourced replay of all identity state;
- automatic chapter-equivalence merge as part of Story identity transaction;
- provider-specific curated trust presets.

Deferral is deliberate. None of these may be used as justification to weaken the v1 invariants above.

---

## 46. Final Architecture Summary

The completed architecture is:

```text
Plugin facts
    ↓
Catalog Evidence Layer
    ↓
Reconciliation Engine
    ↓
Canonical Identity Core
    ├───────────────┐
    │               │
    ▼               ▼
Atomic Room      Metadata Fusion
Graph Merge      Engine
    │               │
    │               ▼
    │        Canonical Generation
    │               │
    └───────┬───────┘
            ▼
      Canonical Read Model
            ▼
Discover / Search / Story / Library
```

Around that core:

```text
Reconciliation Case / Review
Story Redirect / Lineage
Merge Audit / Controlled Reversal
Dirty Work / Background Safety
Policy Versioning / Reevaluation
```

The central invariant is:

> **Plugins provide facts. Reconciliation decides identity. Canonical Identity owns Story membership. Domain policies decide how their own user state can migrate. Room commits authoritative graph changes atomically. Fusion decides canonical metadata. UI consumes the resulting canonical state and never invents a separate truth.**

This structure allows Hikari to add more catalog providers without coupling the host engine to provider names, while preserving user state and making automatic identity decisions explainable and repairable.

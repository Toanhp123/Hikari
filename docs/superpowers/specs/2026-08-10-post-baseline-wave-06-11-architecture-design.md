# Post-Baseline Wave 06-11 Architecture Design

Date: 2026-08-10
Status: APPROVED

## Goal

Evolve the accepted Architecture Baseline 2 through Wave 06-11 without
reintroducing the removed shared-model, shared-database, generic-matching,
dual-plugin-runtime, manual-DI, or catch-all synchronization architecture.

## Decision

Architecture Baseline 2 remains the fixed starting graph. Later waves may add a
production module only when the wave begins a capability with its own models,
ports, behavior, dependency needs, and independent verification boundary.

The approved evolution is:

| Boundary | Modules introduced |
|---|---|
| Baseline 2 | `:app`, `:core:common`, `:catalog`, `:feature:catalog`, `:storage:room`, `:plugins:api`, `:plugins:runtime` |
| Wave 06 | `:library` |
| Wave 07 | `:chapters` |
| Wave 08 | `:reader`, `:feature:reader` |
| Wave 09 | `:downloads`, `:storage:files` |
| Wave 10 | `:settings`, `:feature:settings` |
| Wave 11 | `:feature:plugins` |

No other production module is introduced by these waves without a dedicated
architecture decision and matching policy update.

## Ownership

### `:catalog`

Owns catalog discovery, canonical catalog stories, catalog-source metadata,
catalog matching/ranking, and refresh/search/details services. It does not own
Library membership, readable chapter graphs, reading progress, downloads, or
settings.

### `:library`

Owns Library membership, reading status, content-source mappings, mapping search
planning, mapping decisions, and user-protected mapping overrides. It references
stories by `StoryId` and may query a narrow catalog projection without taking
ownership of the catalog `Story` aggregate.

### `:chapters`

Owns content release models, recent/full/incremental synchronization policy,
chapter-label parsing, deterministic release aggregation, tombstones, unread
events, and user aggregation overrides. It consumes approved Library content
mappings and the plugin runtime facade.

### `:reader`

Owns sanitized reader-document models, deterministic release selection, content
loading/fallback policy, exact reader position, and canonical reading-progress
commands. It does not own Compose or filesystem implementation.

### `:downloads`

Owns cache/download state machines, quotas, retention/eviction decisions,
integrity metadata, reconciliation plans, and content-resolution ordering. It
uses a file-storage port implemented by `:storage:files`.

### `:settings`

Owns typed user policies for language order, synchronization, notifications,
reader preferences, storage limits, and plugin preferences. Its Android
DataStore implementation remains inside the same focused Android library until
a second adapter creates a real extraction need.

### Presentation modules

`:feature:catalog` continues to own Home, Search, Story, Library, mapping, and
chapter-list presentation because those surfaces share the canonical story flow.
`:feature:reader` owns the immersive reader lifecycle and controls.
`:feature:settings` owns settings/storage/synchronization controls.
`:feature:plugins` begins only when full plugin-management UI starts in Wave 11.

### Adapters

`:storage:room` implements capability-owned persistence ports and owns every Room
entity, DAO, migration, and transaction. `:storage:files` implements atomic
app-private blob storage and filesystem inspection. `:app` owns Hilt composition,
Navigation 3 routes, WorkManager workers/schedulers, notification delivery,
permissions, and other Android entry points.

### Plugin boundary

`:plugins:api` remains the pure serialized wire/package contract.
`:plugins:runtime` remains the sole JavaScript execution, package lifecycle,
capability, authentication-session, and security boundary. Later capabilities
call its public facade or wrap it in capability-owned adapters; no host-side
`CatalogPlugin`/`ContentPlugin` interface returns.

## Dependency Direction

The approved capability direction is:

```text
:core:common

:plugins:api <- :plugins:runtime

:catalog ---------------> :plugins:api + :plugins:runtime
:library ----> :catalog + :plugins:api + :plugins:runtime
:chapters ---> :library + :plugins:api + :plugins:runtime
:reader ------> :chapters + :plugins:api + :plugins:runtime
:downloads ---> :reader + :chapters
:settings -----> :core:common

:storage:room --> capability persistence ports
:storage:files -> :downloads file-storage port

:feature:catalog  -> :catalog + :library + :chapters
:feature:reader   -> :reader + :chapters + :downloads + :settings
:feature:settings -> :settings + :downloads
:feature:plugins  -> :plugins:api + the public :plugins:runtime management facade

:app -> every production module needed for composition
```

Feature modules never import Room, filesystem adapters, plugin execution
internals, or WorkManager implementations. Capability modules never import
Compose, navigation, Hilt UI helpers, Room records, or app workers.

Reader declares a narrow preferences port when it needs user policy. `:app`
adapts `:settings` to that port, so `:reader` does not acquire a reverse
dependency on a capability introduced two waves later.

## Synchronization And Platform Work

There is no generic `:sync` module. Each capability owns its pure orchestration:

- Library owns deferred content-mapping search policy.
- Chapters owns release synchronization and aggregation.
- Downloads owns download/cache engines.

`:app` supplies thin WorkManager adapters that deserialize stable IDs, invoke one
capability service, translate retry/failure, and publish platform notifications.
Workers do not contain matching, aggregation, persistence, or download policy.

## Persistence Evolution

Baseline 2 Room schema 1 remains immutable. Every wave that changes Room:

1. writes a failing migration/transaction test;
2. adds exactly one forward migration for that task;
3. exports the next contiguous schema JSON;
4. proves all earlier exports remain byte-stable;
5. updates the current schema fingerprint evidence.

Room stores capability data but does not own capability decisions. File blobs,
plugin packages, and DataStore preferences do not become Room tables merely to
centralize storage.

## Wave Continuity

Each wave plan must declare:

- the exact graph entering the wave;
- modules introduced by the wave and the task that introduces them;
- contracts consumed from the previous wave;
- contracts produced for the next wave;
- schema versions introduced by each persistence task;
- focused RED/GREEN commands using existing or newly introduced modules;
- a checkpoint that rejects deleted architecture names and unapproved modules.

Wave 07 consumes protected Library mappings from Wave 06. Wave 08 consumes the
canonical chapter/release graph from Wave 07. Wave 09 consumes reader document
loading and progress contracts from Wave 08. Wave 10 schedules the same pure
engines and consumes download/settings state. Wave 11 hardens the complete graph
without changing capability ownership.

## Rejected Alternatives

- Keeping exactly seven modules through release would turn `:catalog` and `:app`
  into catch-all modules.
- Recreating one technical-layer module per concern would repeat the old
  architecture's ownership ambiguity.
- Recreating `:core:model`, `:core:database`, `:core:matching`,
  `:core:plugin-host`, or `:sync` is forbidden.
- Moving all future UI into `:feature:catalog` is rejected once Reader, Settings,
  or Plugin Management has an independent lifecycle and dependency boundary.

## Verification

Roadmap consistency tests scan active Wave 06-11 plans for removed modules,
legacy plugin interfaces, non-contiguous schema instructions, and invalid
wave-to-wave contracts. Every wave checkpoint runs repository verification,
affected JVM/Android suites, Room stability, structural review, and a deep
ownership review before advancing the canonical roadmap.

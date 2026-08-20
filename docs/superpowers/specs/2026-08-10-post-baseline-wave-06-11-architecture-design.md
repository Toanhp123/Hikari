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
| UI foundation | `:core:designsystem` |
| Wave 10 | `:settings`, `:feature:settings` |
| Wave 11 | `:feature:plugins` |

The UI foundation is an approved dedicated between-wave decision. No other
production module is introduced without a dedicated architecture decision and
matching policy update.


## 2026-08-20 Persistence Rebase

The 2026-08-19 Discover semantic-feed redesign advanced Room from schema 6 to schema 7,
and the 2026-08-20 catalog metadata-lifecycle unification then advanced Room from schema 7 to
schema 8. Neither change altered the approved module graph or capability ownership in this design.
Future persistence numbering is therefore rebased while the architecture decision remains unchanged:

- Wave 10 enters on Room schema 8.
- The planned durable notification-delivery state in Wave 10 migrates `8 -> 9`.
- Wave 11 treats schema 9 as stable unless a separately reviewed release-defect migration is required.

This is a contiguous-schema rebase only; it does not reopen Waves 06-09 or add a new capability owner.

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

### `:core:designsystem`

Owns the application Compose theme, visual tokens, and domain-neutral shared UX
presentation. It has no project dependencies. Only `:app` and presentation
modules consume it; capability, storage, and plugin modules remain independent.

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
All presentation modules consume `:core:designsystem` for application theme,
tokens, and domain-neutral shared states while retaining feature semantics.

Discover / Home / Library remains the final top-level model. Focused capability
screens enter through the avatar-owned `HikariUtilitySheet`: the current Product
UI checkpoint intentionally exposes Downloads and Updates first, Wave 10 later
adds Settings, and Wave 11 later adds Plugin Management. These reserved target
screens do not make Wave 10 or Wave 11 implemented and never expand top-level
navigation.

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

The runtime is provider-neutral and supports multiple installed packages for each service.
`:app`, as the distribution composition root, owns the production bundled descriptor list and
may ship multiple catalog/content packages. A provider-specific credential adapter may remain in
`:app`, but bundled package selection must not be encoded as a single-provider runtime invariant.

## Dependency Direction

The approved capability direction is:

```text
:core:common
:core:designsystem

:plugins:api <- :plugins:runtime

:catalog ---------------> :plugins:api + :plugins:runtime
:library ----> :catalog + :plugins:api + :plugins:runtime
:chapters ---> :library + :plugins:api + :plugins:runtime
:reader ------> :chapters + :plugins:api + :plugins:runtime
:downloads ---> :reader + :chapters
:settings -----> :core:common

:storage:room --> capability persistence ports
:storage:files -> :downloads file-storage port

:feature:catalog  -> :core:designsystem + :catalog + :library + :chapters
:feature:reader   -> :core:designsystem + :reader + :chapters + :downloads + :settings
:feature:settings -> :core:designsystem + :settings + :downloads
:feature:plugins  -> :core:designsystem + :plugins:api + the public :plugins:runtime management facade

:app -> :core:designsystem + every production module needed for composition
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
loading and progress contracts from Wave 08. The approved UI foundation then
adds `:core:designsystem` without changing capability ownership. Wave 10
schedules the same pure engines and consumes download/settings state. Wave 11
hardens the complete graph without changing capability ownership.

The Product UI checkpoint intentionally ships the supported Downloads and
Updates utility flows before the capability waves that own Settings and Plugin
Management. Wave 10 composes `AppRoute.Settings` from `:feature:settings` through
the avatar utility sheet; Wave 11 composes Plugin Management from
`:feature:plugins` over the public runtime facade through the same sheet. Neither
reserved visual target changes capability ownership or completion status.

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

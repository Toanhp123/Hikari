# Catalog Pure Engine Extraction Design

**Status:** Approved for implementation.

## Goal

Extract Catalog matching, reconciliation, and fusion into pure JVM modules with the same constitutional separation as `:reader:engine`, while preserving all current behavior.

The refactor must not change matching scores, reconciliation decisions, thresholds, ranking, fingerprints, fusion selection, hysteresis, provenance, persistence, plugin contracts, or UI behavior. Algorithm optimization is explicitly deferred until the extraction is complete and certified.

## Starting Point

The work branches from `perf/discover-end-to-end` after the Discover performance recovery was completed.

The current `:catalog` module is an Android library even though its core decision algorithms do not use Android APIs. It mixes:

- immutable Catalog domain data;
- deterministic matching, reconciliation, and fusion logic;
- coroutine orchestration and lifecycle services;
- repositories and persistence contracts;
- plugin/runtime adapters;
- diagnostics sinks and dependency injection.

This prevents the decision engines from having a mechanically enforced pure boundary.

## Scope

This design introduces two modules:

- `:catalog:model` for stable immutable Catalog domain data;
- `:catalog:engine` for deterministic matching, reconciliation, and fusion.

The existing `:catalog` module remains the effect and orchestration boundary.

## Non-goals

- No algorithm, policy, threshold, weight, or tie-break optimization.
- No Room schema or migration change.
- No plugin protocol or bundled-plugin change.
- No UI state, navigation, or presentation redesign.
- No change to canonical identity ownership or graph-merge semantics.
- No unrelated cleanup in Library, Chapters, Reader, Downloads, or Settings.

## Module Architecture

```text
:core:common
      |
      v
:catalog:model
      |
      v
:catalog:engine
      |
      v
:catalog
```

Consumers that only need Catalog data types depend on `:catalog:model`. Consumers that directly construct or test a pure engine depend on `:catalog:engine`. Effectful Catalog workflows depend on `:catalog`.

Dependencies must be explicit. `:catalog` must not re-export the engine merely to hide missing consumer dependencies.

### `:catalog:model`

`:catalog:model` uses `openstory.kotlin.jvm` and depends only on `:core:common`.

It owns stable immutable data required across module boundaries, including:

- Catalog entries, stories, feed snapshots, content types, publication status, scores, and latest-update values;
- source keys and external identifiers;
- canonical generation, metadata, provenance, source preference, and health values;
- metadata keys, levels, stamps, and snapshots;
- raw Catalog evidence records.

Existing package names remain stable where possible so moving a type between Gradle modules does not create unnecessary Kotlin import churn.

The module must not contain:

- repositories or persistence contracts;
- `Flow`, suspend access services, coroutine scopes, or synchronization;
- DI annotations;
- plugin runtime types;
- filesystem, network, Android, or clock access;
- mutable process-wide state.

Files that currently mix values with effects must be split. For example, metadata value types may move to `:catalog:model`, while metadata access contracts, failures tied to operations, and DI scope annotations remain in `:catalog`.

### `:catalog:engine`

`:catalog:engine` uses `openstory.kotlin.jvm` and depends only on `:core:common` and `:catalog:model`.

Its public algorithm surface lives under `app.openstory.catalog.engine.*`. Implementation details remain internal when consumers do not need them.

The module owns:

#### Matching

- title normalization;
- match policy and result values;
- candidate preparation and scoring;
- story resolution;
- deterministic in-memory match indexes.

#### Reconciliation

- reconciliation evidence and policy;
- conflict gates and candidate evaluation;
- semantic decision, merge eligibility, reason codes, ranking, and winning lead;
- candidate and ingest indexes;
- deterministic story-ID creation used by ingest resolution;
- evidence normalization and identity fingerprint calculation required by the decision.

#### Fusion

- source usability classification;
- primary-source ranking and hysteresis;
- user source preference application;
- field selection and normalized unions;
- canonical health and provenance construction;
- fusion fingerprints;
- candidate generation validation.

The engine receives complete immutable facts and returns typed decisions and traces. It never fetches additional data or executes a decision.

### `:catalog`

`:catalog` remains an Android library and owns all effects:

- Home, Search, and Details fetching;
- plugin source registries and runtime availability adapters;
- repositories and canonical persistence contracts;
- coroutine orchestration, mutexes, maintenance, and durable work;
- reconciliation review and merge execution;
- canonical generation promotion;
- diagnostics sinks and DI wiring.

It assembles engine inputs from repositories/runtime facts, invokes the pure engine, then applies the returned decision through the existing repositories and services.

## Data Flow

```text
plugin/runtime/repository facts
            |
            v
    :catalog snapshot assembly
            |
            v
 :catalog:engine pure decision
            |
            v
 typed decision + deterministic trace
            |
            v
 :catalog effect orchestration
            |
            v
 repositories / storage / plugin runtime
```

All time, previous-generation, availability, and preference facts must be passed in explicitly. Engine code must not read a clock or global runtime state.

Diagnostics are observational. Runtime control flow uses executable decision fields, not diagnostic traces. Persistence and logging sinks remain outside the engine.

## Behavior-preserving Migration

The extraction proceeds in compile-safe vertical slices:

1. Add `:catalog:model`, move immutable shared types, and update explicit consumer dependencies.
2. Add `:catalog:engine` plus constitutional module-boundary checks.
3. Move matching implementation and tests without changing behavior.
4. Move reconciliation implementation and tests without changing behavior.
5. Move fusion implementation and tests without changing behavior.
6. Adapt `:catalog` services and application DI to the new engine boundary.
7. Remove the old implementations only after equivalent outputs are proven.

Package moves may require consumer import changes, but public product behavior is frozen. Existing policy and algorithm version constants remain unchanged.

## Determinism and Error Semantics

Pure engine functions must:

- produce the same result for equivalent facts regardless of process state;
- use stable explicit tie-breaks instead of incidental collection order;
- preserve current hashing and fingerprint bytes;
- avoid floating-point or ordering changes during extraction;
- reject programmer-invalid snapshots with constructor or `require` invariants;
- represent ordinary domain outcomes with typed decisions rather than exceptions.

Cancellation, plugin failures, repository failures, and storage failures are handled by `:catalog`. They must not be translated into invented engine facts unless the existing behavior already defines that translation.

Stateful in-memory indexes are allowed inside the engine when their state is bounded to one deterministic operation and supports the current copy/fork semantics. They must not become shared process services.

## Constitutional Guards

Architecture verification must enforce that `:catalog:model` and `:catalog:engine` do not import:

- `android.*` or `androidx.*`;
- `kotlinx.coroutines.*`;
- `kotlinx.serialization.*`;
- `javax.inject.*`;
- `app.openstory.plugins.*`;
- `app.openstory.storage.*`;
- Catalog repositories, services, source registries, or orchestration packages;
- `java.io.*` or `java.net.*`;
- application clocks or dispatchers.

The module graph must reject reverse dependencies from either pure module to `:catalog`.

## Testing Strategy

The existing matching, reconciliation, and fusion suites contain 123 tests and remain authoritative during extraction.

Before or alongside each move:

- retain exact expected decisions and model equality assertions;
- add focused characterization/golden fixtures for ordering, reason codes, fingerprints, provenance, and canonical generation output where current tests do not freeze them;
- retain permutation, adversarial, and policy-boundary coverage;
- add architecture tests for plugin choice, dependencies, and forbidden imports;
- update Catalog, Feature Catalog, Storage Room, and App integration tests for explicit module dependencies and DI wiring.

Per-task verification stays focused on the affected module tests plus Detekt/module-boundary checks. Full branch verification runs once after all extraction tasks are complete.

## Acceptance Criteria

1. `:catalog:model` is a pure JVM module depending only on `:core:common`.
2. `:catalog:engine` is a pure JVM module depending only on `:core:common` and `:catalog:model`.
3. Matching, reconciliation, and fusion algorithm implementations no longer live in `:catalog`.
4. Effectful services, repositories, plugin adapters, review workflows, promotion, and DI remain outside the engine.
5. Existing matching, reconciliation, and fusion behavior is unchanged.
6. Fingerprints, ranking, reason codes, provenance, and canonical outputs match the pre-extraction baseline.
7. Room schema, plugin protocol, and UI contracts are unchanged.
8. Architecture guards prevent the pure modules from regaining framework or effect dependencies.
9. Focused module tests and Detekt checks pass after every implementation task.
10. Full branch verification passes before the branch is declared complete.

## Deferred Work

Only after this extraction is complete may a separate design revise matching quality, reconciliation policy, fusion performance, allocation behavior, or concurrency. Those changes must update the appropriate algorithm or policy version and must not be folded into this refactor.

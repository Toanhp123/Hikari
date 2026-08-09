# Repository Current State

Date: 2026-08-09
Purpose: single source of truth for the implemented repository boundary.

## Executive state

- Product baseline: approved Android-only, local-first unified novel library design.
- Package namespace and application ID: `app.openstory`.
- Current Gradle modules: 11.
- Wave 01-03 implementation is present.
- Wave 04 Tasks 01-06 implementation is present.
- Pre-MVP Baseline 1 project-wide refactor is complete.
- Wave 04 checkpoint is accepted with unit, lint, source-layout, and Android sandbox
  instrumentation evidence.
- Wave 05 Tasks 01-06 implementation is present.
- Wave 05 Tasks 01-06 verification is accepted by the Wave 05 checkpoint.
- Current active boundary: **Architecture Baseline 2 - R0 Freeze and Guardrails**.
- Wave 06 is frozen until Architecture Baseline 2 R6 is accepted.
- The Wave 05 checkpoint remains historical evidence, not a compatibility requirement.

## Independent version spaces

| Surface | Current baseline |
|---|---|
| Application | `versionCode = 1`, `versionName = 1.0` |
| Room database | schema 1, rebased current complete durable model |
| Declarative selector | schema 1, typed endpoint/binding contract |
| Plugin API | major/minor compatibility, baseline major 1 |
| Repository index | schema 1 |
| Package layout | no separate schema-version field in the current contract |

These versions are independent. A change in one does not imply a change in another.

## What is present

### Wave 01 boundary

The repository contains the JDK 17 build gate, `app.openstory` identity,
Compose/Navigation shell, dependency-boundary policy, shared verification, CI, and
module governance.

### Wave 02 boundary

The repository contains canonical story/chapter/release models and the complete current
Room database/repository implementation rebased as initial schema 1. Pre-baseline local
database migrations are intentionally unsupported; historical migration evidence remains
in documentation archives only.

### Wave 03 boundary

The repository contains:

- Plugin API major/minor compatibility rules;
- Catalog and Content wire contracts;
- package and repository-index formats;
- Selector Schema 1 typed endpoint and closed binding contracts;
- all four Catalog and all six Content endpoint declarations;
- install-time request, binding, output-shape, and manifest validation;
- deterministic complete contract fixtures;
- package inspection that rejects unsupported or malformed selectors before activation.

### Wave 04 boundary

Implementation present:

- allowlisted network gateway with resource, redirect, rate, and cancellation budgets;
- shared validation-only `PluginUrlPolicy` and isolated `BoundedResponseReader`;
- transactional plugin verification, staging, activation, registry, and rollback;
- neutral `PluginActivation` registry records implemented by `RoomPluginRegistry`;
- complete Selector Schema 1 runtime with bounded document acquisition, opaque DOM
  evaluation, Catalog/Content mapping, final wire validation, and runtime adapters;
- AndroidX JavaScriptEngine isolation with a validated capability bridge, bounded
  messages, timeout/cancellation handling, and host-controlled networking;
- capability-aware update review, staged activation, retained previous versions, and
  atomic rollback behavior;
- bounded redacted plugin diagnostics and a unified host facade whose batch operations
  isolate failures to the offending plugin;
- selector/JavaScript contract parity coverage using deterministic fixtures;
- no development-generation selector runtime or compatibility pipeline.

Wave 04 checkpoint acceptance proves:

- selector and JavaScript fixtures return the same contract DTO;
- undeclared hosts, traversal archives, oversized bodies, timeouts, and invalid bridge
  messages fail closed;
- failed updates and rollbacks leave the previously active plugin version usable;
- diagnostics exclude fixture secrets;
- batch host calls contain per-plugin failures.

### Wave 05 boundary

Implementation present through Task 06:

- Task 01 provides source-preserving catalog snapshots, source metadata, cached Home
  persistence, and the temporary source-isolated canonical resolver behind the approved
  resolver port.
- Task 02 provides the deterministic bundled default Catalog fixture, exact package-byte
  pinning, local asset loading, idempotent bootstrap behavior, and an update coordinator
  that delegates newer bundled versions to the normal capability-diff/update service.
- User-disabled state is preserved by the registry during activation; expanded-access
  bundled updates remain review-gated rather than being installed by the bootstrap path.
- Task 03 adds the pure Kotlin/JVM `:core:matching` module, deterministic title/author
  evidence scoring, trusted direct-mapping validation, source-isolated fallback identity,
  and aggregate ranking that preserves original catalog score/scale values.
- Task 04 adds the Android `:feature:home` application boundary, exact hosted-plugin to
  `CatalogSnapshot` normalization, bounded/isolated multi-catalog refresh, cached freshness
  reporting, and combined/source-specific cached Home projections using Task 03 ranking.
- Task 05 adds presentation-only `HomeScreenState`, combined and catalog-specific Compose
  screens, accessible source/score semantics, stable lazy-list keys, source switching,
  non-blocking partial-refresh status, and a cover-renderer seam without direct plugin,
  Room, or network access from Compose.
- Task 06 adds cancellable/debounced multi-catalog search, source-scoped filter values,
  Task-03 canonicalized search display that preserves per-source scores, memory-only recent
  searches, the Android `:feature:story` boundary, exact Catalog detail normalization, and
  source-preserving detail enrichment through `CatalogRepository.upsertSourceMetadata(...)`.
  Search pages remain transient and story-detail UI state exposes neither Room entities nor
  plugin DTOs.

Task 02 focused package/bootstrap tests, Android instrumentation, repository verification,
lint, module-boundary checks, and Room schema-stability verification are recorded as PASS
in `../internal/checkpoints/wave-05-task-02-bundled-default-catalog.md`. Task 03 focused
matching tests, the complete matching module suite, 9-module architecture verification,
repository verification, and Room schema-stability verification are recorded as PASS in
`../internal/checkpoints/wave-05-task-03-catalog-matching.md`. Task 04 verification is accepted
in `../internal/checkpoints/wave-05-task-04-home-refresh.md`. Task 05 unit, Compose
instrumentation, architecture, full repository, and Room schema-stability verification is
accepted in `../internal/checkpoints/wave-05-task-05-home-ui.md`. Task 06 verification is
recorded in `../internal/checkpoints/wave-05-task-06-search-and-story-detail.md`. The full
Wave 05 acceptance is recorded in
`../internal/checkpoints/wave-05-catalog-home-and-discovery.md`.

## Verification status

Implementation presence is not checkpoint acceptance. Evidence under
`../internal/checkpoints/` records commands actually run and keeps unexecuted gates as
`NOT RUN`. Wave 04 acceptance is recorded in
`../internal/checkpoints/wave-04-plugin-host-and-security.md`; Wave 05 Task 02 verification
is recorded in `../internal/checkpoints/wave-05-task-02-bundled-default-catalog.md`; Wave 05 Task 03
verification is accepted in `../internal/checkpoints/wave-05-task-03-catalog-matching.md`; Wave 05
Task 04 verification is accepted in `../internal/checkpoints/wave-05-task-04-home-refresh.md`;
Wave 05 Task 05 verification is accepted in
`../internal/checkpoints/wave-05-task-05-home-ui.md`; Wave 05 Task 06 verification is accepted in
`../internal/checkpoints/wave-05-task-06-search-and-story-detail.md`. The Wave 05 checkpoint is
accepted in `../internal/checkpoints/wave-05-catalog-home-and-discovery.md`.

## Source-of-truth rule

When documents disagree:

1. Approved product design owns scope and domain invariants.
2. Repository code and tests own what is physically implemented.
3. Accepted checkpoint evidence owns whether a gate passed.
4. This file owns current implementation position.
5. Archived documents are historical context, not execution entry points.

See `document-governance.md` for the complete precedence policy.

# Current Implementation Roadmap

Date: 2026-08-09
Status: **CANONICAL repository execution roadmap**

This roadmap preserves the approved product sequence while Architecture Baseline 2 resets
the pre-Wave-06 implementation architecture. Implementation presence and checkpoint
acceptance remain separate states.

## Status vocabulary

- **Implementation present**: production code and tests exist for the boundary.
- **Verification open**: required checkpoint evidence is missing or still `NOT RUN`.
- **In progress**: some deliverables exist and some remain.
- **Ready to start**: entry checkpoint is accepted, but the next wave has no
  implementation yet.
- **Planned**: approved work has not started in this repository.

## Current position

Architecture Baseline 2 is active at **R0 - Freeze and Guardrails**. Wave 05 remains
accepted historical evidence, but it does not require compatibility with superseded
development architecture. Wave 06 is frozen until Baseline 2 R6 is accepted.

Execute R0 through R6 in the order defined by
`../superpowers/plans/2026-08-09-architecture-baseline-2-refactor-roadmap.md`.

Wave 04 provides secure selector and JavaScript execution, transactional lifecycle, safe
updates/rollback, redacted diagnostics, and the unified host boundary now consumed by
Wave 05 catalog persistence and bundled catalog bootstrap.

## Current module graph

```text
:app
:core:common
:core:model
:core:database
:core:plugin-api
:core:network
:core:plugin-host
:core:matching
:feature:home
:feature:story
:test:fixtures
```

Direct dependencies are governed by
`../../config/architecture/module-boundaries.json`. Later-wave modules are created only
when their owning wave starts.

## Wave status

| Wave | Ownership | Status | Canonical document |
|---|---|---|---|
| 01 | Foundation, architecture, CI | Implementation present; historical checkpoint evidence retained | `waves/wave-01-foundation-and-architecture.md` |
| 02 | Domain and local storage | Implementation present on Room schema 1; checkpoint acceptance remains evidence-driven | `waves/wave-02-domain-and-local-storage.md` |
| 03 | Plugin contracts and packages | Implementation present on Selector Schema 1 | `waves/wave-03-plugin-contracts-and-packages.md` |
| 04 | Plugin host and security | **Implementation present; checkpoint accepted** | `waves/wave-04-plugin-host-and-security.md` |
| 05 | Catalog Home and discovery | **Implementation present; checkpoint accepted** | `waves/wave-05-catalog-home-and-discovery.md` |
| 06 | Library and story matching | **Frozen until Architecture Baseline 2 R6 acceptance** | `waves/wave-06-library-and-story-matching.md` |
| 07 | Chapter sync and aggregation | Planned | `waves/wave-07-chapter-sync-and-aggregation.md` |
| 08 | Reader and progress | Planned | `waves/wave-08-reader-and-reading-progress.md` |
| 09 | Cache, downloads, storage | Planned | `waves/wave-09-cache-downloads-and-storage.md` |
| 10 | Background sync, auth, notifications | Planned | `waves/wave-10-background-sync-auth-and-notifications.md` |
| 11 | Hardening and open-source release | Planned | `waves/wave-11-hardening-open-source-release.md` |

## Wave 04 decomposition

| Task | Outcome | State |
|---|---|---|
| 04.01 | Allowlisted HTTP gateway, shared URL policy, bounded body reader, budgets, sessions, decoding, redaction | Implementation present |
| 04.02 | Transactional install, neutral registry port, Room adapter, rollback | Implementation present |
| 04.03 | Selector Schema 1 document loading, typed evaluation, DTO mapping, adapters | Implementation present |
| 04.04 | JavaScript capability sandbox | Implementation present; Android instrumentation passed |
| 04.05 | Update and capability-diff lifecycle | Implementation present |
| 04.06 | Redacted diagnostics and unified host facade | Implementation present |

## Wave 05 decomposition

| Task | Outcome | State |
|---|---|---|
| 05.01 | Source-preserving catalog ingestion and cached Home persistence | Verified by Wave 05 checkpoint |
| 05.02 | Deterministic bundled default catalog and safe bootstrap/update boundary | Verified |
| 05.03 | Deterministic cross-catalog matching and aggregate ranking | Verified |
| 05.04 | Cached Home refresh/orchestration | Verified |
| 05.05 | Combined and catalog-specific Home UI | Verified |
| 05.06 | Search, filters, and source-preserving story detail | Verified |

## Critical dependency chain

```text
architecture
  -> canonical domain and Room
    -> plugin contracts and package validation
      -> secure plugin execution
        -> catalog discovery
             ^ Architecture Baseline 2 R0-R6 reset is active; Wave 06 is frozen
          -> story matching
            -> chapter aggregation
              -> reader
                -> offline storage
                  -> local background/auth/notifications
                    -> release hardening
```

## Execution rule

1. Execute Architecture Baseline 2 R0 through R6 in order.
2. Do not begin Wave 06 while the architecture reset is active.
3. Treat Wave 05 checkpoints as historical evidence, not compatibility authority.
4. Update current state only after each Baseline 2 checkpoint is accepted.

## Verification principle

Plans and source presence are not proof that a checkpoint passed. Checkpoint records must
name the command, environment, and result. Historical `NOT RUN` entries are preserved
rather than rewritten from later assumptions.

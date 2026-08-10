# Current Implementation Roadmap

Date: 2026-08-10
Status: **CANONICAL repository execution roadmap**

This roadmap preserves the approved product sequence after Architecture Baseline 2 reset
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

Architecture Baseline 2 is accepted. The active boundary is **Wave 06 Task 01 -
metadata-only Library persistence and story matching foundations**. Wave 01-05
checkpoints remain historical delivery evidence and do not require compatibility with
superseded development architecture. Wave 06 is ready to start.

Continue from `waves/wave-06-library-and-story-matching.md`, beginning with Task 01.

The retained implementation uses the Baseline 2 JavaScript protocol/runtime, bounded host
capabilities, transactional package lifecycle, catalog-owned services, Room-owned
persistence, and feature-owned presentation. The accepted Baseline 2 checkpoint freezes
those boundaries for the Wave 06 entry.

## Current module graph

```text
:app
:core:common
:catalog
:feature:catalog
:storage:room
:plugins:api
:plugins:runtime
```

Direct dependencies are governed by
`../../config/architecture/module-boundaries.json`. This seven-module graph is exact for
the Baseline 2 acceptance checkpoint; later-wave modules require an owning-wave decision.

## Wave status

| Wave | Ownership | Status | Canonical document |
|---|---|---|---|
| 01 | Foundation, architecture, CI | Implementation present; historical checkpoint evidence retained | `waves/wave-01-foundation-and-architecture.md` |
| 02 | Domain and local storage | Implementation present on Room schema 1; checkpoint acceptance remains evidence-driven | `waves/wave-02-domain-and-local-storage.md` |
| 03 | Plugin contracts and packages | Historical implementation superseded by the Baseline 2 protocol/package boundary | `waves/wave-03-plugin-contracts-and-packages.md` |
| 04 | Plugin host and security | **Implementation present; checkpoint accepted** | `waves/wave-04-plugin-host-and-security.md` |
| 05 | Catalog Home and discovery | **Implementation present; checkpoint accepted** | `waves/wave-05-catalog-home-and-discovery.md` |
| AB2 | Architecture reset between Wave 05 and Wave 06 | **Accepted** | `../internal/checkpoints/architecture-baseline-2.md` |
| 06 | Library and story matching | **Ready to start at Task 01** | `waves/wave-06-library-and-story-matching.md` |
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
| 04.03 | Historical selector runtime | Superseded and removed by Architecture Baseline 2 |
| 04.04 | JavaScript capability sandbox | Replaced by the Baseline 2 protocol/runtime boundary |
| 04.05 | Update and capability-diff lifecycle | Implementation present |
| 04.06 | Redacted diagnostics and unified host facade | Implementation present |

## Wave 05 decomposition

| Task | Outcome | State |
|---|---|---|
| 05.01 | Source-preserving catalog ingestion and cached Home persistence | Verified by Wave 05 checkpoint |
| 05.02 | Canonical MyAnimeList reference package and safe bootstrap/update boundary | Verified |
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
             ^ Architecture Baseline 2 accepted; Wave 06 Task 01 is ready
          -> story matching
            -> chapter aggregation
              -> reader
                -> offline storage
                  -> local background/auth/notifications
                    -> release hardening
```

## Execution rule

1. Begin Wave 06 with Task 01 from its canonical wave plan.
2. Preserve the accepted seven-module Baseline 2 boundaries unless a reviewed architecture decision changes them.
3. Treat Wave 01-05 checkpoints as historical evidence, not compatibility authority.
4. Update current state only after actual task/checkpoint evidence is reviewed.

## Verification principle

Plans and source presence are not proof that a checkpoint passed. Checkpoint records must
name the command, environment, and result. Historical `NOT RUN` entries are preserved
rather than rewritten from later assumptions.

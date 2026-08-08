# Current Implementation Roadmap

Date: 2026-08-08
Status: **CANONICAL repository execution roadmap**

This roadmap preserves the approved product sequence while interpreting it against the
pre-MVP Baseline 1 source tree. Implementation presence and checkpoint acceptance remain
separate states.

## Status vocabulary

- **Implementation present**: production code and tests exist for the boundary.
- **Verification open**: required checkpoint evidence is missing or still `NOT RUN`.
- **In progress**: some deliverables exist and some remain.
- **Ready to start**: entry checkpoint is accepted, but the next wave has no
  implementation yet.
- **Planned**: approved work has not started in this repository.

## Current position

The repository has accepted the **Wave 04 - Plugin Host and Security** checkpoint. The
next implementation boundary is **Wave 05 Task 01 - catalog ingestion repository and
canonical merge boundary**.

Wave 04 now provides secure selector and JavaScript execution, transactional lifecycle,
safe updates/rollback, redacted diagnostics, and the unified host boundary consumed by
Wave 05 catalog ingestion.

## Current module graph

```text
:app
:core:common
:core:model
:core:database
:core:plugin-api
:core:network
:core:plugin-host
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
| 05 | Catalog Home and discovery | **Ready to start at Task 01** | `waves/wave-05-catalog-home-and-discovery.md` |
| 06 | Library and story matching | Planned | `waves/wave-06-library-and-story-matching.md` |
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

## Critical dependency chain

```text
architecture
  -> canonical domain and Room
    -> plugin contracts and package validation
      -> secure plugin execution
        -> catalog discovery  <-- current position
          -> story matching
            -> chapter aggregation
              -> reader
                -> offline storage
                  -> local background/auth/notifications
                    -> release hardening
```

## Execution rule

1. Start Wave 05 Task 01 from `waves/wave-05-catalog-home-and-discovery.md`.
2. Keep catalog ingestion behind the accepted plugin-host and repository boundaries.
3. Preserve source identity while creating the canonical merge boundary.
4. Follow focused RED/GREEN verification before the affected module suite.

## Verification principle

Plans and source presence are not proof that a checkpoint passed. Checkpoint records must
name the command, environment, and result. Historical `NOT RUN` entries are preserved
rather than rewritten from later assumptions.

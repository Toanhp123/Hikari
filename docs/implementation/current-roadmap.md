# Current Implementation Roadmap

Date: 2026-08-07  
Status: **CANONICAL repository execution roadmap**

This roadmap preserves the approved product sequence while interpreting it against the
pre-MVP Baseline 1 source tree. Implementation presence and checkpoint acceptance remain
separate states.

## Status vocabulary

- **Implementation present**: production code and tests exist for the boundary.
- **Verification open**: required checkpoint evidence is missing or still `NOT RUN`.
- **In progress**: some deliverables exist and some remain.
- **Planned**: approved work has not started in this repository.

## Current position

The repository is inside **Wave 04 - Plugin Host and Security**, specifically
**Task 03 - Selector Schema 1 runtime execution**.

Wave 03 owns the canonical typed selector contract and install-time validation. Wave 04
owns document acquisition, DOM evaluation, DTO mapping, endpoint budgets, cancellation,
redaction, and final host output validation. The active work must consume the existing
contract rather than introduce a compatibility envelope.

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
| 04 | Plugin host and security | **In progress** | `waves/wave-04-plugin-host-and-security.md`; Task 03: `wave-04-selector-runtime.md` |
| 05 | Catalog Home and discovery | Planned | `waves/wave-05-catalog-home-and-discovery.md` |
| 06 | Library and story matching | Planned | `waves/wave-06-library-and-story-matching.md` |
| 07 | Chapter sync and aggregation | Planned | `waves/wave-07-chapter-sync-and-aggregation.md` |
| 08 | Reader and progress | Planned | `waves/wave-08-reader-and-reading-progress.md` |
| 09 | Cache, downloads, storage | Planned | `waves/wave-09-cache-downloads-and-storage.md` |
| 10 | Background sync, auth, notifications | Planned | `waves/wave-10-background-sync-auth-and-notifications.md` |
| 11 | Hardening and open-source release | Planned | `waves/wave-11-hardening-open-source-release.md` |

## Wave 04 decomposition

| Task | Outcome | State |
|---|---|---|
| 04.01 | Allowlisted HTTP gateway, request budgets, sessions, decoding, redaction | Implementation present |
| 04.02 | Transactional install, registry, rollback | Implementation present |
| 04.03 | Selector Schema 1 document loading, typed evaluation, DTO mapping, adapters | **Active**; document loader present, evaluator/mappers/adapters pending |
| 04.04 | JavaScript capability sandbox | Planned |
| 04.05 | Update and capability-diff lifecycle | Partial; rollback primitive present |
| 04.06 | Redacted diagnostics and unified host facade | Planned |

## Critical dependency chain

```text
architecture
  -> canonical domain and Room
    -> plugin contracts and package validation
      -> secure plugin execution  <-- current position
        -> catalog discovery
          -> story matching
            -> chapter aggregation
              -> reader
                -> offline storage
                  -> local background/auth/notifications
                    -> release hardening
```

## Execution rule

1. Complete `wave-04-selector-runtime.md` from its first remaining responsibility.
2. Close Wave 04 Task 03 with all Catalog/Content DTO execution, budgets,
   cancellation, URL validation, and redaction evidence.
3. Complete the remaining Wave 04 JavaScript, update, diagnostics, and host work.
4. Run and record the Wave 04 checkpoint.
5. Begin Wave 05 only after checkpoint acceptance.

## Verification principle

Plans and source presence are not proof that a checkpoint passed. Checkpoint records must
name the command, environment, and result. Historical `NOT RUN` entries are preserved
rather than rewritten from later assumptions.

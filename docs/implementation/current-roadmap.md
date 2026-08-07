# Current Implementation Roadmap

Date: 2026-08-07  
Status: Canonical repository execution roadmap

This file reconciles the approved 2026-08-03 planning baseline with the source
snapshot `Hikari-wave-04-task-03-selector-runtime(2).zip`. It does **not** change
the approved product scope. It changes only how the existing repository state is
interpreted for continued implementation.

## Status vocabulary

- **Implementation present**: production code/tests for the wave boundary are in this source snapshot.
- **Verification open**: the source contains checkpoint evidence with required gates still marked `NOT RUN`, or no final accepted checkpoint is embedded in the snapshot.
- **In progress**: some planned deliverables are implemented and some remain.
- **Planned**: the approved plan exists but implementation evidence is absent from this snapshot.

A wave is not called **checkpoint complete** merely because implementation is present.

## Current position

The repository is currently inside **Wave 04 — Plugin Host and Security**, specifically
**Task 03 — declarative selector runtime**.

The important ownership correction is:

- Wave 03 owns Selector V2 **public contracts and install-time validation**.
- Wave 04 owns Selector V2 **networking, DOM evaluation, DTO mapping, runtime budgets,
  cancellation, and final host output validation**.

Therefore Wave 04 Task 03 must continue from shared URL/output validation; it must
not recreate the V2 envelope, binding AST, Catalog endpoint declarations, or Content
endpoint declarations already present in `:core:plugin-api`.

## Current and target module graph

### Modules present now

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

The authoritative direct-dependency policy for these modules is
`config/architecture/module-boundaries.json`.

### Target modules from the approved roadmap

Later waves add modules only when their owning wave begins:

```text
:core:matching
:core:aggregation
:core:reader
:core:files
:sync
:feature:home
:feature:library
:feature:story
:feature:reader
:feature:plugins
:feature:settings
:benchmark
```

The 2026-08-03 roadmap described this full target map before the repository tree
existed. It must not be read as proof that all modules should already exist.

## Wave status

| Wave | Ownership | Snapshot status | Canonical document |
|---|---|---|---|
| 01 | Foundation, architecture, CI | Implementation present; embedded checkpoint evidence still historical/open | `waves/wave-01-foundation-and-architecture.md` + source remediation docs |
| 02 | Domain and local storage | Implementation present through Room schema v3; embedded checkpoint retains open target/CI gates | `waves/wave-02-domain-and-local-storage.md` + source remediation docs |
| 03 | Plugin contracts and packages | Implementation present, including Selector V2 contracts and package inspection; embedded checkpoint retains open target/CI gates | `waves/wave-03-plugin-contracts-and-packages.md` + source remediation docs |
| 04 | Plugin host and security | **In progress** | `waves/wave-04-plugin-host-and-security.md`; Task 03 continuation: `../wave-04-selector-v2-runtime.md` |
| 05 | Catalog Home and discovery | Planned | `waves/wave-05-catalog-home-and-discovery.md` |
| 06 | Library and story matching | Planned | `waves/wave-06-library-and-story-matching.md` |
| 07 | Chapter sync and aggregation | Planned | `waves/wave-07-chapter-sync-and-aggregation.md` |
| 08 | Reader and progress | Planned | `waves/wave-08-reader-and-reading-progress.md` |
| 09 | Cache/download/storage | Planned | `waves/wave-09-cache-downloads-and-storage.md` |
| 10 | Background sync/auth/notifications | Planned | `waves/wave-10-background-sync-auth-and-notifications.md` |
| 11 | Hardening/open-source release | Planned | `waves/wave-11-hardening-open-source-release.md` |

## Wave 04 decomposition from the snapshot

| Task | Planned outcome | Source evidence | Execution state |
|---|---|---|---|
| 04.01 | Allowlisted HTTP gateway | `:core:network` contains gateway, request budgets, session/header policies, decoder and redacting logger | Implementation present |
| 04.02 | Transactional install/registry | `:core:plugin-host` contains verifier, transactional storage, archive inspector/extractor, registry and rollback manager | Implementation present |
| 04.03a | Bounded Selector V1 interpreter | `SelectorRuntime`, `SelectorInterpreter`, `HtmlDocumentAdapter`, `SelectorExecutionContext`, `TransformRegistry` | Implementation present |
| 04.03b | Selector V2 runtime DTO execution | V2 contracts exist in `:core:plugin-api`; runtime evaluator/mappers/factory/shared URL-output validation are not present | **Active** |
| 04.04 | JavaScript capability sandbox | No implementation evidence in this snapshot | Planned within Wave 04 |
| 04.05 | Update/capability-diff lifecycle | Rollback primitive exists, but full update service/policy from the approved task is not evidenced | Partial / pending |
| 04.06 | Redacted diagnostics + unified host facade | No final `PluginHost`/diagnostics implementation evidence in this snapshot | Planned within Wave 04 |

## Critical dependency chain

```text
Architecture
  -> canonical domain + Room
    -> plugin contracts/package validation
      -> secure plugin execution   <-- current position
        -> catalog discovery
          -> story matching
            -> chapter aggregation
              -> reader
                -> offline storage
                  -> local background/auth/notifications
                    -> release hardening
```

Do not begin Wave 05 because a Home UI can be mocked independently. The approved
architecture requires catalog plugins to execute through the completed secure host.

## Execution rule from here

1. Continue `wave-04-selector-v2-runtime.md` from the first unchecked runtime task.
2. Close Wave 04 Task 03 with V1 compatibility, Catalog+Content DTO execution,
   cancellation, budgets, diagnostics redaction and shared validation evidence.
3. Complete remaining Wave 04 tasks (JavaScript sandbox, update lifecycle,
   diagnostics/unified host facade).
4. Run and record the Wave 04 checkpoint.
5. Only then begin Wave 05.

## Verification principle

Planning status and implementation status are not verification evidence. Checkpoints
must record commands and results. Historical `NOT RUN` entries are preserved in
`docs/internal/checkpoints` rather than silently rewritten from later assumptions.

# Current Implementation Roadmap

Date: 2026-08-21
Status: **CANONICAL repository execution roadmap**

This roadmap preserves the approved product sequence after Architecture Baseline 2 reset
the pre-Wave-06 implementation architecture. Implementation presence and checkpoint
acceptance remain separate states.

## Status vocabulary

- **Implementation present**: production code and tests exist for the boundary.
- **Patched / verification open**: source/test changes exist, but the required repository gate has not yet been reviewed.
- **Verification open**: required checkpoint evidence is missing or still `NOT RUN`.
- **In progress**: some deliverables exist and some remain.
- **Completed/accepted**: implementation and required checkpoint evidence for that boundary are closed.
- **Ready to start**: entry checkpoint is accepted, but the next wave has no
  implementation yet.
- **Planned**: approved work has not started in this repository.

## Current position

Architecture Baseline 2 is accepted. Waves 06-09 are verified and complete. The between-wave
Design System Foundation and the full Product UI checkpoint are accepted, preserving the 14-module
production graph. The 2026-08-19 Discover semantic-feed redesign is also implemented and verified;
it advanced Room from schema 6 to **schema 7** without changing module ownership. The 2026-08-20
catalog metadata-lifecycle unification subsequently advanced Room to **schema 8**, centralized
Summary/Full freshness and single-flight in `:catalog`, and preserved the same module graph.

The **Canonical Catalog Reconciliation & Fusion Engine is now the active pre-Wave-10 workstream**.
Its Phase 0 Tasks 1–4, Phase 1 Tasks 5–11, Phase 2 Tasks 12–21, and Phase 3 Tasks 22–25 are verified and closed while Room remains schema 9. Phase 3 is accepted as observe-only reconciliation; destructive Story merge remains unimplemented and Task 26 is now the active next task. The active design/plan are:

- `../superpowers/specs/2026-08-20-canonical-catalog-reconciliation-fusion-engine-design.md`
- `../superpowers/plans/2026-08-21-canonical-catalog-reconciliation-fusion-engine-implementation-plan.md`

Wave 10 remains planned and has not started. Canonical-engine Task 6 now owns the reviewed `8 -> 9`
schema foundation in this Phase-1 patch. Wave 10 notification persistence is rebased to `9 -> 10`, and
Wave 11 enters on schema 10 unless another separately reviewed migration intervenes. Do not create a
second, competing `MIGRATION_8_9`.

The completed Product UI and Discover implementation plans are execution records, not active next-work
instructions. Wave 01-05 checkpoints remain historical delivery evidence and do not require compatibility
with superseded development architecture.

Current Canonical Engine Phase-3 acceptance evidence is recorded in
`../internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-3.md`; Phase-2 acceptance remains in
`../internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-2.md`; accepted Phase-1 evidence remains in
`../internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-1.md`; Phase-0 acceptance remains in
`../internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-0.md`. Current Discover acceptance
evidence remains in `../internal/checkpoints/discover-semantic-feed-redesign.md`; keep the accepted Discover semantic-feed checkpoint as the Wave 10 entry baseline
for its completed feed/UI behavior while CCE remains
the active pre-Wave-10 engineering track. The accepted broader Product UI evidence remains in
`../internal/checkpoints/product-ui-redesign.md`. Wave-06 task evidence is recorded in:

- `../internal/checkpoints/wave-06-task-01-metadata-only-library.md`
- `../internal/checkpoints/wave-06-task-02-library-presentation.md`
- `../internal/checkpoints/wave-06-task-03-content-story-matching.md`
- `../internal/checkpoints/wave-06-task-04-content-source-search.md`
- `../internal/checkpoints/wave-06-task-05-protected-content-mappings.md`
- `../internal/checkpoints/wave-06-task-06-mapping-review-url-import.md`

The retained implementation uses the Baseline 2 JavaScript protocol/runtime, bounded host
capabilities, transactional package lifecycle, catalog-owned services, Room-owned
persistence, and feature-owned presentation. MyAnimeList and MangaDex are the current
production-bundled packages, while the runtime and app-owned bundled descriptor registry support
multiple catalog/content packages. The accepted Baseline 2 checkpoint freezes its historical
entry evidence, not the number of plugins allowed after that boundary.

## Current module graph

```text
:app
:core:common
:core:designsystem
:catalog
:library
:chapters
:reader
:downloads
:feature:catalog
:feature:reader
:storage:room
:storage:files
:plugins:api
:plugins:runtime
```

Direct dependencies are governed by
`../../config/architecture/module-boundaries.json`. The accepted Baseline 2 graph remains
historical evidence at seven modules; Wave 06 introduced `:library`, Wave 07 introduced
`:chapters`, Wave 08 added `:reader` and `:feature:reader`, and Wave 09 added
`:downloads` and `:storage:files`, producing the thirteen-module capability graph.
The approved between-wave UI foundation adds `:core:designsystem`, producing the
current fourteen-module graph. The approved post-baseline evolution is defined by
`../superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`.

## Approved module evolution

| Wave boundary | New production modules | Capability reason |
|---|---|---|
| 06 | `:library` | Library membership and protected content mappings |
| 07 | `:chapters` | Release synchronization and canonical aggregation |
| 08 | `:reader`, `:feature:reader` | Reader policy and independent immersive presentation |
| 09 | `:downloads`, `:storage:files` | Offline/cache policy and atomic file adapter |
| 09 -> 10 foundation | `:core:designsystem` | Application theme, visual tokens, and domain-neutral shared UX |
| 10 | `:settings`, `:feature:settings` | Typed policies and independent settings presentation |
| 11 | `:feature:plugins` | Full plugin-management presentation |

No catch-all synchronization module is planned. Pure orchestration stays with its
capability; WorkManager and notification adapters stay in `:app`.

## Wave status

| Wave | Ownership | Status | Canonical document |
|---|---|---|---|
| 01 | Foundation, architecture, CI | Implementation present; historical checkpoint evidence retained | `waves/wave-01-foundation-and-architecture.md` |
| 02 | Domain and local storage | Implementation present on Room schema 1; checkpoint acceptance remains evidence-driven | `waves/wave-02-domain-and-local-storage.md` |
| 03 | Plugin contracts and packages | Historical implementation superseded by the Baseline 2 protocol/package boundary | `waves/wave-03-plugin-contracts-and-packages.md` |
| 04 | Plugin host and security | **Implementation present; checkpoint accepted** | `waves/wave-04-plugin-host-and-security.md` |
| 05 | Catalog Home and discovery | **Implementation present; checkpoint accepted** | `waves/wave-05-catalog-home-and-discovery.md` |
| AB2 | Architecture reset between Wave 05 and Wave 06 | **Accepted** | `../internal/checkpoints/architecture-baseline-2.md` |
| 06 | Library and story matching | **Completed; Tasks 01-06 verified** | `waves/wave-06-library-and-story-matching.md` |
| 07 | Chapter sync and aggregation | **Completed; Tasks 01-06 verified** | `waves/wave-07-chapter-sync-and-aggregation.md` |
| 08 | Reader and progress | **Completed; Tasks 01-06 and checkpoint verified** | `waves/wave-08-reader-and-reading-progress.md` |
| 09 | Cache, downloads, storage | **Completed; Tasks 01-06 and checkpoint verified** | `waves/wave-09-cache-downloads-and-storage.md` |
| UIF | Between-wave design-system foundation | **Completed; checkpoint accepted 2026-08-12** | `../internal/checkpoints/design-system-foundation.md` |
| PUI | ReDantotsu-inspired Product UI redesign | **Completed; checkpoint accepted 2026-08-14** | `../internal/checkpoints/product-ui-redesign.md` |
| DSR | Discover semantic-feed redesign | **Completed; Room schema 7; focused/device/visual/benchmark verification complete** | `../internal/checkpoints/discover-semantic-feed-redesign.md` |
| CML | Catalog metadata lifecycle unification | **Implementation present; Room schema 8; unified Summary/Full lifecycle** | `../project/current-state.md` |
| CCE | Canonical Catalog Reconciliation & Fusion Engine | **Phase 0/1/2/3 Tasks 22–25 verified/closed; observe-only reconciliation accepted; Task 26 next; Room schema 9** | `../internal/checkpoints/canonical-catalog-reconciliation-fusion-phase-3.md` |
| 10 | Background work, auth, notifications | **Planned; not started; schema entry will be rebased by CCE Task 6 if that foundation lands first** | `waves/wave-10-background-sync-auth-and-notifications.md` |
| 11 | Hardening and open-source release | Planned; post-baseline plan approved | `waves/wave-11-hardening-open-source-release.md` |

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

## Wave 06 decomposition

| Task | Outcome | State |
|---|---|---|
| 06.01 | `:library`, metadata-only membership, Room schema 2, current architecture verifier | [Verified](../internal/checkpoints/wave-06-task-01-metadata-only-library.md) |
| 06.02 | Library presentation in `:feature:catalog` | [Verified](../internal/checkpoints/wave-06-task-02-library-presentation.md) |
| 06.03 | Explainable content-story matching | [Verified](../internal/checkpoints/wave-06-task-03-content-story-matching.md) |
| 06.04 | Quick/deferred content-plugin search | [Verified](../internal/checkpoints/wave-06-task-04-content-source-search.md) |
| 06.05 | Protected content mappings and Room schema 3 | [Verified](../internal/checkpoints/wave-06-task-05-protected-content-mappings.md) |
| 06.06 | Mapping review and URL import UI | [Verified](../internal/checkpoints/wave-06-task-06-mapping-review-url-import.md) |

## Wave 07 decomposition

| Task | Outcome | State |
|---|---|---|
| 07.01 | `:chapters` and deterministic chapter-label normalization | Verified |
| 07.02 | Pure deterministic release aggregation and protected overrides | Verified |
| 07.03 | Provider-neutral chapter operations through the runtime facade | Verified |
| 07.04 | Transactional chapter graph persistence and Room schema 4 | Verified |
| 07.05 | Recent/full/incremental synchronization and initial worker adapter | Verified |
| 07.06 | Canonical chapter-list presentation and correction controls | Verified |

## Wave 08 decomposition

| Task | Outcome | State |
|---|---|---|
| 08.01 | Reader modules and bounded structured text/image-document validation | Implementation present |
| 08.02 | Pure deterministic release selection | Implementation present |
| 08.03 | Store-first sanitized content loading and fallback | Implementation present |
| 08.04 | Debounced exact progress and Room schema 5 | Implementation present |
| 08.05 | Stable-ID navigation and process-restorable Reader state | Implementation present |
| 08.06 | Accessible structured text / vertical image-page Compose Reader UI | Implementation present |

## Critical dependency chain

```text
architecture
  -> canonical domain and Room
    -> plugin contracts and package validation
      -> secure plugin execution
        -> catalog discovery
             ^ Wave 06 complete: Library + protected mappings + review/URL import verified
          -> story matching
            -> chapter aggregation
                 ^ Wave 07 complete: sync, aggregation, schema 4, and presentation verified
              -> reader
                   ^ Wave 08 complete: Reader, progress, schema 5, and device checkpoint verified
                -> offline storage
                     ^ Wave 09 complete: cache, downloads, schema 6, reconciliation, and offline UI verified
                  -> UI foundation
                    -> Product UI redesign
                      -> semantic Discover redesign + Room schema 7
                        -> unified catalog metadata lifecycle + Room schema 8
                          -> canonical catalog reconciliation/fusion engine (Phase 1 verified/closed)
                            -> canonical schema foundation (schema 9 accepted)
                              -> metadata fusion/read-path cutover (Phase 2 Tasks 12-21 verified/closed)
                                -> reconciliation observe-only (Phase 3 Tasks 22-25 verified/closed)
                                  -> survivor/user-state-footprint policy (Task 26 next)
                                  -> local background/auth/notifications (Wave 10; schema 9 -> 10)
                      -> release hardening
```

## Execution rule

1. Use the current 14-module graph, Room schema 9, and the Canonical Engine design/plan as the active execution baseline.
2. Phase 1 Tasks 5–11, Phase 2 Tasks 12–21, and Phase 3 Tasks 22–25 are verified and closed. Phase 3 remains observe-only; Task 26 is the active next task. `MIGRATION_8_9` belongs exclusively to the canonical-engine foundation.
3. Wave 10 notification persistence is rebased to `MIGRATION_9_10`; never reintroduce another meaning for `MIGRATION_8_9`.
4. Evolve modules only at the owning wave boundary defined by the approved post-baseline architecture design.
5. Treat Wave 01-09, Product UI, and Discover checkpoints as accepted/historical evidence, not active implementation plans.
6. Require every capability wave to consume the prior boundary's named contracts and contiguous schema.
7. Update current state and checkpoints only after actual command/device evidence is reviewed.

Discover / Home / Library remains the final top-level model. Discover itself is now semantic rather
than catalog-selector driven: `Popular -> Manga | Light Novel -> Latest Updates -> Top Rated`.
Downloads and Updates remain avatar-utility routes. Wave 10 Settings enters through that utility sheet
and never top-level navigation; Wave 11 Plugin Management follows the same rule. Settings and Plugin
Management remain planned capability-wave work.

## Verification workflow

Use `./scripts/verify-fast.sh` during implementation iterations. It runs repository/static
contracts, exact architecture verification, local tests, Detekt, and Room schema stability
without Android lint or app assembly. Before a task/checkpoint is accepted, run
`./scripts/verify.sh`; it is still the canonical full host gate and additionally runs
`lintDebug` and `:app:assembleDebug`. Both entry points use strict dependency verification.
The full gate executes `verifyArchitecture` in the same Gradle invocation as the rest of the
Gradle workload, while project-level daemon reuse, configuration cache, parallel execution,
and local build cache reduce repeated verification cost without removing checks.

## Verification principle

Plans and source presence are not proof that a checkpoint passed. Checkpoint records must
name the command, environment, and result. Historical `NOT RUN` entries are preserved
rather than rewritten from later assumptions.

# Architecture Baseline 2 Refactor Roadmap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Sequence the complete pre-Wave-06 architecture reset into independently reviewable checkpoints R0 through R6.

**Architecture:** Use parallel replacement by vertical slice. Old and new implementations may coexist only inside the checkpoint that replaces a subsystem; each checkpoint closes with one canonical implementation for every replaced responsibility.

**Tech Stack:** Kotlin/JVM, Android, Gradle build logic, Hilt, Navigation 3, Room, AndroidX JavaScriptEngine, OkHttp, Jsoup, Detekt, shell verification.

## Global Constraints

- Architecture source of truth: `docs/superpowers/specs/2026-08-09-architecture-baseline-2-design.md`.
- This work is pre-Wave-06; do not implement Library, chapter sync, Reader, downloads, background sync, authentication, notifications, or release-hardening behavior.
- Android-native Kotlin remains fixed.
- Package namespace/application ID remains `app.openstory`.
- Minimum SDK remains 26; compile/target SDK remain 37 unless a dedicated architecture decision changes them.
- Build runtime remains JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0, Kotlin 2.4.10.
- Current retained libraries may be replaced only when a plan task explicitly does so; do not change versions opportunistically during this reset.
- Pre-MVP compatibility is intentionally breakable. Do not add permanent `Legacy*`, `Compat*`, `V1/V2` adapters, dual mappers, or Room migrations merely to preserve development-only contracts.
- Temporary migration-scoped bridges are allowed only when this plan names the bridge and its deletion task explicitly.
- Package-first, Gradle-module-second: do not create extra production modules beyond the approved target graph without a new architecture decision.
- TDD is mandatory for behavior changes: focused RED -> smallest GREEN -> affected module suite -> commit.
- Every task ends in a buildable, testable, independently reviewable repository state.
- Do not make a checkpoint green with `TODO()`, `error("not implemented")`, unconditional empty production results, or broad structural suppressions.
- Tests protect revalidated product/security invariants, not historical class shapes.
- Production Room entities/DAOs stay private to the storage adapter.
- Production plugin JavaScript receives only host-controlled capabilities and never Android `Context`, Room, filesystem paths, raw OkHttp, reflection, or plaintext managed credentials.

---
## Plan Set

Execute the plans in this exact order:

| Order | Checkpoint | Plan file | Canonical outcome |
|---|---|---|---|
| 1 | R0 | `2026-08-09-ab2-r0-freeze-and-guardrails.md` | Wave 06 frozen; invariants revalidated; anti-debt gates active |
| 2 | R1 | `2026-08-09-ab2-r1-foundation-and-module-graph.md` | Target modules exist beside legacy slices; target dependency rules compile |
| 3 | R2A | `2026-08-09-ab2-r2a-plugin-protocol-and-package-contract.md` | Pure plugin wire/package contract fixed before runtime work |
| 4 | R2B | `2026-08-09-ab2-r2b-plugin-runtime-and-security.md` | JS runtime/security/lifecycle ready behind new protocol; old host still runs product flows |
| 5 | R2C | `2026-08-09-ab2-r2c-reference-plugin-and-cutover.md` | MAL + consumers cut over; R2 closes with selector/old host/network removed |
| 6 | R3A | `2026-08-09-ab2-r3a-catalog-core.md` | Catalog models/matching/repository/services owned by `:catalog` using fake persistence |
| 7 | R3B | `2026-08-09-ab2-r3b-room-persistence-and-cutover.md` | Fresh Room baseline + catalog cutover; R3 closes with old model/matching/database removed |
| 8 | R4 | `2026-08-09-ab2-r4-presentation-navigation-di.md` | Home/Search/Story live in `:feature:catalog`; Hilt/Nav wiring canonical |
| 9 | R5 | `2026-08-09-ab2-r5-cleanup-and-quality-hardening.md` | Legacy source/docs/scripts/suppressions removed; final architecture gates hardened |
| 10 | R6 | `2026-08-09-ab2-r6-acceptance-and-freeze.md` | Full architecture checkpoint accepted; current state advances to Wave 06 Task 01 |

## Dependency Chain

```text
R0 freeze + invariant classification
  -> R1 target module shell + common primitives
    -> R2A plugin protocol/package contract
      -> R2B runtime/security/package lifecycle
        -> R2C MAL + consumer cutover + legacy plugin deletion
          -> R3A catalog core/matching/repository/services
            -> R3B Room baseline + catalog cutover + legacy core deletion
              -> R4 presentation/Hilt/navigation cutover
                -> R5 deletion + quality hardening
                  -> R6 full acceptance
```

Do not skip forward. A later plan may assume only the interfaces explicitly listed in the prior plan's checkpoint contract.

## Design-Spec Coverage Map

| Approved design area | Owning implementation plans |
|---|---|
| Pre-Wave-06 freeze, revalidated goals, breakable compatibility | R0 |
| Target module graph, common identity ownership, dependency direction | R1 |
| Pure plugin protocol and detached package integrity | R2A |
| JS-only runtime, HTTP/HTML/log capabilities, managed credentials, install/update/rollback | R2B |
| `CatalogSource` boundary, MAL reference package, deletion of Selector/old host/network | R2C |
| Catalog-owned Story/entry models, matching/ranking, repository/service boundaries | R3A |
| Fresh Room schema 1, private entities/DAOs, semantic transactions, old core deletion | R3B |
| Hilt constructor injection, `viewModelScope`, Navigation 3 stable routes, presentation ownership | R4 |
| Detekt/suppression/size anti-gaming rules, final module/package gates, stale-code deletion | R5 |
| Deterministic tests, API 26/37 checkpoint, manual ownership audit, architecture freeze | R6 |

Every normative design section has one named implementation owner above. If execution discovers a design requirement with no owner, amend the relevant plan before implementing it rather than silently adding work to another checkpoint.

## Canonical Target Production Modules

At R6 the production graph is:

```text
:app
:core:common
:catalog
:feature:catalog
:storage:room
:plugins:api
:plugins:runtime
```

`R5` deletes the legacy `:test:fixtures` module. Reusable fixtures that remain valuable are moved to the owning module test source set instead of preserving a cross-feature fixture module.

## Target Dependency Direction

```text
:feature:catalog -> :catalog
:catalog         -> :core:common
:catalog         -> :plugins:api
:catalog         -> :plugins:runtime
:plugins:runtime -> :core:common
:plugins:runtime -> :plugins:api
:storage:room    -> :core:common
:storage:room    -> :catalog
:storage:room    -> :plugins:runtime   # persistence/SPI package only
:app             -> all production modules required for composition
```

Forbidden final directions include:

```text
:feature:catalog -> :storage:room
:feature:catalog -> :plugins:runtime
:storage:room    -> plugin execution/install/network/js internals
:plugins:runtime -> :catalog
:plugins:api     -> Android or app/domain modules
:catalog         -> Compose
```

## Checkpoint Review Rule

At every R0-R6 boundary:

1. run the focused tests named in that plan;
2. run the affected module suite;
3. run `./scripts/check-module-dependencies.sh`;
4. run the checkpoint's architecture/source-layout gate;
5. record actual commands/results in the checkpoint evidence file;
6. update `docs/project/current-state.md` only after the checkpoint is accepted;
7. perform one final deep review of the checkpoint state (ownership, boundaries,
   behavior, documentation, and test gaps) and fix findings in the same checkpoint;
8. commit the checkpoint state before starting the next plan.

The deep review is intentionally a single end-of-checkpoint pass. It does not add
review/test cycles between individual tasks unless a task is blocked or the focused
gate exposes a failure.

## Final Acceptance Contract

Architecture Baseline 2 is not complete merely because the app launches. R6 must prove all of the following:

- one owner for every active model/responsibility;
- no feature-to-Room or feature-to-plugin-runtime dependency;
- no Room-to-plugin execution dependency;
- no Selector runtime in active source;
- no old plugin Kotlin host contract in active source;
- no roadmap-wide `core:model`;
- no old `core:database`, `core:matching`, `feature:home`, or `feature:story`;
- no `OpenStoryAppGraph` or custom ViewModel factory;
- no production structural suppression outside an explicit empty/approved allowlist;
- one Room schema `1.json` at the new storage path;
- Home, Search, Story, MAL catalog, plugin security, and persistence invariants pass;
- API 26 and API 37 checkpoint runs pass;
- `docs/project/current-state.md` says `Architecture Baseline 2: ACCEPTED` and `Next: Wave 06 Task 01`.

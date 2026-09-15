# Repository Current State

Date: 2026-09-15
Purpose: single source of truth for the implemented repository boundary.

## Executive State

- Hikari V2 Step 3 - Base App UX/UI Completion is implemented and accepted through **Task 10** on
  branch `v2/discover-story-foundation`.
- **Task 11 is not started.** Its Reading Source boundary requires a new explicitly authorized turn.
- Step 2 - Discover + Story Detail Foundation remains completed/accepted through Task 18 and is the
  inherited correctness/performance baseline for the Catalog slice.
- The repository currently includes 17 production modules plus the `:benchmark` Android test module.
  Thirteen production modules are in the live app-reachable Step 3 graph; four retained/quarantined
  modules remain intentionally unreachable from release execution.
- Current module inclusion and dependency edges are canonical in `../../settings.gradle.kts` and
  `../../config/architecture/module-boundaries.json`. This document describes responsibility, not a
  second dependency-policy source.

The next-work authority is `../implementation/current-roadmap.md`. Implementation presence and
checkpoint acceptance remain separate states.

## Live Step 3 Production Graph

| Module | Current responsibility |
| --- | --- |
| `:app` | Startup shell, app-owned Navigation 3 route stacks/lifecycle, and composition/runtime wiring |
| `:core:common` | Narrow cross-capability primitives and navigation lifecycle identifiers |
| `:core:artwork` | Process-shared bounded artwork admission/cache/coalescing/security ownership |
| `:core:designsystem` | Domain-neutral shared theme, layout/poster/control/navigation/sheet presentation policy |
| `:catalog:domain` | Catalog identity/provenance, bounded Discover/Story/Search/Section/Similar contracts and ports |
| `:catalog:storage` | Catalog Room truth, migrations, bounded reads/writes, retention and keyed persistence |
| `:catalog:runtime` | Catalog demand activation, acquisition/import orchestration, mutation/pin ownership and fixtures |
| `:library:domain` | Local Library identity, membership/query contracts and presentation snapshots |
| `:library:storage` | Dedicated Library Room truth, indexed bounded queries and idempotent mutations |
| `:library:runtime` | Library observation/query sessions and serialized mutation ownership |
| `:feature:catalog` | Discover presentation and Catalog-owned source/media presentation adapters |
| `:feature:library` | Home/Library presentation over local Library truth |
| `:feature:story` | Story Detail presentation composed from Catalog and Library facets |

`:benchmark` remains the Android test/performance module for startup/profile/Catalog journeys and
accepted performance evidence.

## Retained And Quarantined Sources

These modules are included for reviewed reference/retention purposes but remain outside the live app
production execution graph:

| Module | Status |
| --- | --- |
| `:catalog:model` | Quarantined pure-JVM reference models |
| `:catalog:engine` | Quarantined pure-JVM reference algorithms |
| `:reader:engine` | Retained HES-v1 candidate for later Reader admission |
| `:plugins:api` | Retained plugin protocol; current Catalog integration proof uses it only on the test edge |

Do not remove, rename, or reconnect these modules as routine cleanup. Their disposition is governed by
`../internal/v2/v1-salvage-ledger.md` and later capability admission.

## Runtime Boundary Through Task 10

- Release identity remains `app.openstory`; debug uses `app.openstory.v2dev`; benchmark targets use
  `app.openstory.v2benchmark`.
- App startup reaches **Home** without activating Catalog. Catalog demand starts only from explicit
  Explore/Discover navigation or another admitted Catalog consumer.
- App owns serializable route-wire/navigation mechanics and route lifecycle. Domain identities are
  reconstructed through validated composition/feature boundaries rather than made navigation types.
- Discover remains Room-backed, bounded and semantic for Manga and Light Novel. Search/section/similar
  domain contracts added in Task 6 do not imply that later Search/Listing product tasks are complete.
- Story Detail presentation lives in `:feature:story`; it observes source-keyed Catalog state and a
  point-scoped Library membership facet. Add/Remove Library mutations are Library-owned and do not
  trigger Catalog reacquisition.
- Home is a local Library surface backed by the dedicated Library database/runtime. Its Ready state is
  independent of Catalog or future Reading Source activation.
- `:core:artwork` owns the process-wide bounded artwork runtime. Catalog remains the descriptor/policy
  source for current catalog artwork presentation; artwork transport/acquisition work does not move
  into Design System.
- Catalog and Library persistence are separate databases with separate ownership. There is no shared
  cross-domain Room database or Catalog foreign-key/cascade ownership of Library truth.
- Production remote plugin execution, a general network client, Reading Source binding, Chapters and
  Reader consumption are not admitted by Tasks 1-10.

## Presentation Boundary Through Task 10

- `HikariTheme` is installed once at the app root.
- `:core:designsystem` owns repeated **domain-neutral** visual policy only: shared spacing/theme roles,
  600dp compact/wide policy, minimum touch target, poster geometry/frame/card/grid/rail/skeleton,
  search/filter/icon controls, floating destination navigation, focused headers, value/info rows,
  action/choice sheets and shared feedback/state/refresh primitives.
- Callers retain semantic state, copy, navigation decisions, persistence/runtime execution, paging,
  artwork acquisition and capability-specific behavior.
- The Task 10 Design System boundary does not authorize a generic presentation framework,
  `GlobalUiState`, feature models in core, or hidden work/lifecycle ownership.

The detailed current policy is `../ui/design-system.md`.

## Verification State

The Step 3 roadmap records Tasks 1-10 as completed/accepted. In particular, Task 10 records accepted
focused Design System/feature tests, affected build variants and Android-test compilation, Catalog,
Home, Story and real-App connected gates, plus the remediated broad architecture/Detekt and Design
System connected reruns.

Current host entrypoints are:

- `../../scripts/verify-fast.sh` for the normal host feedback loop;
- `../../scripts/verify.sh` for the canonical full host gate.

Both run the current repository/static contract set first, then one top-level Gradle invocation. The
Gradle architecture plugin owns `verifyArchitecture`, `verifyModuleBoundaries`,
`verifyProductionPackageStructure`, `verifyStep3BuildSurface`, and the Step 3 module aggregates.
Historical Step 2 freeze scripts are evidence/provenance and are not automatically reinterpreted as
current Step 3 law.

The immutable Step 1 startup baseline remains at
`../internal/v2/startup-baseline-2026-09-07.md`. Accepted Step 2 performance debt also remains
recorded at `../internal/v2/catalog-step2-performance-baseline-2026-09-08.md`; Step 3 Tasks 1-10 do
not relabel those measurements.

## Explicitly Not Implemented Yet

Task 11 Reading Source domain/storage/runtime, Reading binding/detect, production network/provider
admission, full Settings capability, Chapters, Reader UI/content, Downloads, background scheduling,
notifications, deep links and final onboarding are not made current merely because broader product
or future architecture documents describe them.

Any next capability follows the active owning plan and requires the authorization recorded in
`../implementation/current-roadmap.md`.

## Source-of-truth Rule

When documents disagree:

1. Approved product/scoped design owns product scope and domain invariants.
2. This file, then repository code/tests, owns what is implemented now.
3. Accepted checkpoint evidence owns whether a required gate passed.
4. `../implementation/current-roadmap.md` owns the next execution boundary.
5. `../../settings.gradle.kts` and `../../config/architecture/module-boundaries.json` own current
   module inclusion/dependency policy.
6. Archived/historical documents remain provenance, not current execution instructions.

See `document-governance.md` for the complete precedence policy.

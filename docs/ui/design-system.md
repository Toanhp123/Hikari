# Hikari V2 Design System

Date: 2026-09-15
Status: **CANONICAL V2 STEP 3 PRESENTATION POLICY THROUGH TASK 10**

## Ownership

`:core:designsystem` is a domain-neutral presentation Android library with zero project dependencies.
It owns repeated visual/layout/accessibility policy that has been explicitly admitted into the shared
surface. It owns no Catalog, Library, Reading Source, Story or app semantic state; no persistence,
network/artwork acquisition, retry/acquisition scheduling, navigation decisions, lifecycle owner,
coroutine/Flow collection, registry, cache, or other capability runtime work.

Exact project edges and forbidden imports remain authoritative in
`config/architecture/module-boundaries.json`. `:app` installs `HikariTheme` once at the root and may
also consume the admitted domain-neutral floating destination navigation. Feature modules consume
shared primitives while retaining their semantic models and execution ownership.

## Theme And Layout Policy

`HikariTheme(darkTheme, content)` selects stable Material 3 color schemes, typography and shapes.
Light `background` remains exactly white and dark `background` exactly black to preserve the accepted
window-to-first-Compose-surface continuity. Theme construction performs no I/O, runtime font lookup,
settings read, lifecycle observation, or work activation.

The shared token/layout surface currently includes:

- `HikariSpacing` / `MaterialTheme.hikariSpacing` for the fixed spacing scale;
- `HikariDimensions` for the shared compact/wide screen insets, 48dp minimum touch target and poster
  grid minimum width;
- `HikariBreakpoints` with the accepted `600.dp` compact/wide boundary and horizontal-inset policy;
- Material 3 color/typography/shape roles defined by the accepted Hikari visual vocabulary.

Task 10 intentionally moved only repeated **domain-neutral** geometry into this module. Feature-owned
hero composition, semantic ranking, source-specific layout, paging policy and capability copy remain
outside it.

## Admitted Shared Surface

The accepted Step 3 Task 10 shared surface is grouped by responsibility:

| Surface | Shared rule |
| --- | --- |
| Theme/layout | `HikariTheme`, `HikariSpacing`, `HikariDimensions`, `HikariBreakpoints` |
| Poster/content | `HikariArtworkFrame`, `HikariPosterGeometry`, `HikariPosterCard`, `HikariPosterGrid`, `HikariPosterRail`, `HikariPosterSkeleton`, `HikariSectionHeader` |
| Controls | `HikariSearchField`, `HikariFilterChip`, `HikariIconAction` / `HikariIconActionStyle` |
| Navigation/header | `HikariFloatingDestinationNav` / item, `HikariFocusedHeader` |
| Sheets/rows | `HikariActionSheet`, `HikariChoiceSheet`, `HikariValueRow`, `HikariInfoRow` |
| State/feedback | `HikariSkeleton`, `HikariEmptyState`, `HikariErrorState`, `HikariInlineFeedback` |
| Refresh | `HikariPullToRefresh` with resource-backed generic accessibility labels |

These primitives share presentation mechanics, not feature semantics. Callers provide labels, state,
callbacks, selected values, content slots and feature-owned consequences. A primitive must not gain a
Catalog/Library/Reading model merely to reduce mapping code.

## Material Direct Rule

Use Material 3 and Compose layout primitives directly when Hikari adds no stable repeated visual or
accessibility rule. Do not create generic wrappers for `Text`, `Row`, `Column`, `Box`, arbitrary
navigation hosts, generic paging state, or a flag-heavy universal row/card.

Task 10 does not authorize a generic `:core:presentation` module, `GlobalUiState`, or shared feature
reducer. New shared APIs after Task 10 require an owning task/design decision and must preserve the
Design System's zero-domain/zero-work boundary.

## State, Work And Sizing

Shared primitives may own local Compose presentation mechanics required by the component itself, but
they do not become lifecycle/runtime owners. In particular, the Design System must not:

- collect capability Flows or own ViewModels/repositories;
- launch acquisition/network/database work;
- own feature retry, paging, search, mutation or navigation decisions;
- import Catalog, Library, Reading, artwork-runtime, Room, WorkManager, OkHttp or other capability
  implementation surfaces.

The 48dp minimum interactive target is shared policy. Poster content and poster skeletons use the same
`HikariPosterGeometry` so loading geometry cannot drift from loaded geometry. Responsive compact/wide
policy uses the 600dp shared breakpoint; callers remain responsible for semantic composition within
that policy.

## Artwork Boundary

`HikariArtworkFrame` and poster primitives own only layout/fallback presentation. They do **not** load,
decode, cache, admit, validate or fetch artwork. Process artwork work belongs to `:core:artwork`; the
feature/composition layer supplies rendered artwork content to the Design System slot.

This distinction is load-bearing: sharing poster geometry does not make Design System an image
pipeline.

## Refresh Boundary

`HikariPullToRefresh` owns Material pull gesture/indicator/accessibility presentation only. It owns no
coroutine, source acquisition, retry state, scroll container or feature single-flight policy. The
caller provides refresh state and dispatch consequences. Task 10 localizes the generic refresh labels
through Design System resources rather than feature-owned hard-coded copy.

## Accessibility

Interactive targets preserve at least 48.dp. Selected/disabled state, headings, loading/error text,
modal choice state and available actions remain accessible without relying on color alone. Poster
content and skeleton geometry remain aligned; fallback artwork is decorative unless a feature-owned
content slot supplies its own semantics.

Shared components accept semantic labels/content from callers rather than hiding feature meaning in
private test tags.

## Explicit Non-Goals Through Task 10

The Design System does not own:

- Catalog/Library/Reading/Story domain state or reducers;
- artwork/network transport, caching, decode, acquisition or security policy;
- app route stacks or capability navigation decisions;
- persistence, Room migrations, DataStore settings, background work or notifications;
- feature copy, source/language policy, paging/search sessions or retry consequences;
- benchmark/screenshot infrastructure or performance admission policy.

Future Task 11+ controls may reuse the already admitted sheet/row/control primitives, but their
existence does not mean Reading Source or Settings runtime is implemented now.

## Verification

The Step 3 boundary is protected by the architecture/build-surface gates plus focused Design System
unit/connected contract tests. In particular:

- `verifyModuleBoundaries` and `verifyStep3BuildSurface` enforce dependency/source ownership;
- `HikariLayoutPolicyTest` covers the 600dp layout policy;
- `HikariDesignSystemContractTest` and `HikariStep3PresentationPolicyTest` cover shared geometry,
  minimum interaction sizing, selection/modal behavior and resource-backed refresh presentation.

The old `scripts/tests/v2-step2-designsystem-slice-test.sh` is a historical Step 2 source/API freeze.
It is retained only as provenance/manual historical evidence and is not a current Step 3 verification
law.

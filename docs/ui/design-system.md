# Hikari V2 Design System

Date: 2026-09-11
Status: **CANONICAL V2 STEP 2 PRESENTATION POLICY**

## Ownership

`:core:designsystem` is a presentation-only Android library with zero project dependencies.
It owns the root Material 3 visual environment and a deliberately small set of domain-neutral,
stateless primitives. It owns no application or Catalog semantic state, navigation, lifecycle,
I/O, cache, registry, coroutine, Flow collector, image pipeline, acquisition, or retry policy.

`:app` installs `HikariTheme` once in `HikariStartupApp`. App production code may import only
`app.openstory.designsystem.theme.HikariTheme` from this module. Catalog and other presentation
features consume the shared primitives; capability, storage, runtime, plugin, and engine modules do
not depend on the Design System. Exact edges remain authoritative in
`config/architecture/module-boundaries.json`.

## Theme

`HikariTheme(darkTheme, content)` selects stable top-level light/dark color schemes, typography,
and Material 3 shapes. Light `background` is exactly white and dark `background` is exactly black
to preserve the accepted Step 1 window-to-first-Compose-surface continuity. Theme construction does
not read Context, settings, DataStore, resources, services, or runtime fonts and starts no work.

The R2.8 visual vocabulary is:

- the exact neutral artwork-first palette with accessible Coral/Teal roles recorded in the R2.8
  Step 2 design;
- the exact R2.8 editorial Serif display/headline and Sans body/label Material 3 roles;
- Material 3 shape roles at `8 / 12 / 20 / 28 / 36.dp`;
- one fixed `HikariSpacing` singleton at `4 / 8 / 12 / 16 / 20 / 24 / 32.dp`, exposed as
  `MaterialTheme.hikariSpacing` without a `CompositionLocal`.

Features use `MaterialTheme.colorScheme`, `MaterialTheme.typography`, `MaterialTheme.shapes`, and
`MaterialTheme.hikariSpacing` for shared roles. Feature-specific geometry such as cover aspect
ratios, hero heights, card widths, and responsive composition stays feature-owned and may use local
named constants or literals. Step 2 does not admit global dimensions, breakpoints, layout ratios,
semantic shape families, opacity/brush families, or motion policy.

## Admitted Public Surface

| Public symbol | Production owner/caller | Stable rule |
|---|---|---|
| `HikariTheme` | `app/HikariStartupApp` | One root visual environment with no work ownership |
| `HikariSpacing` / `MaterialTheme.hikariSpacing` | Discover/Story shared spacing roles | Fixed allocation-free spacing scale |
| `HikariSectionHeader` | Discover semantic sections | Shared heading semantics and title treatment |
| `HikariSkeleton` | Discover and Story loading geometry | Static caller-sized fill; no shimmer/progress semantics |
| `HikariEmptyState` | Discover durable successful-empty state | Caller-sized title/body only; no implicit action |
| `HikariErrorState` | Discover no-content failure | Caller-sized title/body with a validated optional action pair |
| `HikariInlineFeedback` | Retained Discover and Story issues | Compact message with a validated optional action pair |
| `HikariPullToRefresh` | Durable Discover published states | Gesture, indicator, and accessibility presentation only |

Every public symbol requires a production caller and a stable semantic rule. Test-only,
future-facing, wrapper-for-wrapper, or parameter-heavy generic surfaces are rejected. Small local
Compose duplication is preferred when no shared rule exists.

Manga and Light Novel are feature-local Catalog media destinations presented by the Discover
floating navigation. Task 14 retired the migration-only shared segmented-control API and did not
replace it with a generic Design System navigation primitive.

## Material Direct Rule

Use Material 3 and Compose layout primitives directly when Hikari adds no stable visual or semantic
rule. Do not add generic Hikari wrappers for Text, Row, Column, Box, cards, artwork, navigation,
snackbars, or one-off controls. A new shared primitive requires an explicit reviewed admission,
production callers, and an update to the structural slice gate.

## State And Sizing

Shared state/feedback primitives are caller-sized. They do not append `fillMaxSize`, create a scroll
container, observe lifecycle, collect state, or start work. Features own copy, state classification,
action availability, retry consequences, geometry, navigation, and data/image/acquisition behavior.

`HikariSkeleton` is static. Initial loading may mirror final geometry, while refresh with durable
content keeps real content visible. No shimmer, infinite animation, blur, custom visual backdrop,
custom shadow, gradient, shader, or graphics-layer effect is admitted before performance evidence.

## Discover Pull Refresh

`HikariPullToRefresh` is enabled only for durable Discover `Published(empty)` or
`Published(content)` presentation states. It uses the caller modifier as its sizing contract and
owns no scroll state or scroll container. Its disabled branch is a plain `Box` and does not compose
Material 3 pull-gesture state.

The primitive exposes the accessibility action `Refresh`, renders the Material 3 indicator, and
suppresses dispatch while already refreshing. It owns no coroutine or single-flight state.
Discover's normal pull intent calls `DiscoverViewModel.refresh()`; visible failure actions call the
distinct `DiscoverViewModel.retry()`. Both converge on the feature/runtime single owner whenever
source acquisition is required. Initial Absent loading and no-content failure use explicit
loading/Retry presentation. Story Detail is not pull-refreshable in Step 2.

## Accessibility

Interactive targets preserve at least 48.dp height. Selection, disabled state, headings, loading
state, error text, and available actions remain visible to accessibility services without relying on
color alone. Shared components accept caller-provided labels and do not hide feature meaning behind
private test tags.

## Explicit Non-Goals

The Step 2 Design System does not own artwork, Coil, network transport, app-wide component catalogs,
responsive layout policy, feature copy, Catalog models, failure mapping, refresh/acquisition
scheduling, Story/Chapter refresh, Chapters/Reader actions, navigation hosts, Robolectric,
Roborazzi, screenshot infrastructure, or benchmark policy. Task 14 owns feature-local visual
restoration and composition quality without broadening this module.

Final visual acceptance for the R2.8 palette and typography remains user-owned and covers Ready,
Loading, Error, Empty, Refresh, light/dark, and compact/wide surfaces. Root backgrounds remain
exactly white/black; spacing and shapes remain unchanged.

`scripts/tests/v2-step2-designsystem-slice-test.sh` fail-closes the exact source/API budget,
dependency and hidden-work bans, app import authority, caller map, refresh ownership, and stale V1
policy exclusions before benchmark work.

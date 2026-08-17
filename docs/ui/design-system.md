# Hikari Design System

## Ownership

`:core:designsystem` owns the application Compose theme, visual tokens, and
domain-neutral loading, empty, error, offline, snackbar, and confirmation
presentation. Features own their copy, state, actions, failure classification,
retry policy, and consequences.

## Dependency rules

`:core:designsystem` has no project dependencies. `:app` and presentation
modules may consume it. Capability, storage, and plugin modules must not depend
on it. The exact allowed edges live in
`config/architecture/module-boundaries.json`.

## Theme and token usage

Application Compose roots use `HikariTheme`. Production presentation code treats the
Hikari theme as the single source of truth for visual values. Spacing, dimensions,
responsive breakpoints, layout ratios, semantic layout policy, semantic shapes, opacity,
artwork colors, and semantic typography come from the `MaterialTheme.hikari*` token
families. Feature and app code must not introduce local `dp`/`sp` literals, ad-hoc
rounded/circle shapes, palette colors, literal alpha values, local font family/weight
overrides, or feature-local numeric grid geometry.

Content geometry is semantic but is not exempt from tokenization. Poster sizes, hero
heights, reader insets, grid minimums, and similar measurements belong in named design
system dimension tokens rather than feature-local constants. Responsive decisions use
`MaterialTheme.hikariBreakpoints`; a feature must not invent its own viewport threshold.
Discrete layout decisions such as the compact two-column policy use
`MaterialTheme.hikariLayoutPolicy` rather than numeric counts at the screen call site.
Material 3 surface roles are also product-owned: `HikariColorScheme` explicitly defines
`surfaceBright`, `surfaceDim`, and the complete `surfaceContainer*` range so Cards, sheets,
menus, and other Material containers cannot fall back to an unrelated default neutral palette.
The base spacing scale is intentionally small: `4 / 8 / 12 / 16 / 20 / 24 / 32.dp`.
Generic icon sizing is limited to `20 / 24.dp`; product geometry such as poster sizes, reader
insets, breakpoints, hero heights, and glyph stroke measurements stays in named semantic
dimension tokens when the value has a real layout purpose. Semantic card radii use the deliberate
`20 / 24 / 28 / 36.dp` family instead of component-by-component 2dp steps.

Visible surface separation uses one Hikari shadow contract. `hikariSurfaceShadow` renders a
zero-offset `2.dp` drop shadow with semantic color/opacity tokens so the result stays balanced on
all four sides. `HikariContentCard`, non-blurred glass fallback, pull-to-refresh indicator chrome,
and popup menus consume this owner. Material `shadowElevation` is explicitly zero anywhere Hikari
adds visible shadow, and production app/feature code must not create `Modifier.shadow`, raw
`dropShadow`, or default-elevated popup/refresh surfaces. Real backdrop glass keeps its blur and
border treatment but no separate directional elevation shadow. Content/list cards remain
shadow-only with no outline/border. Nested list-item surfaces are avoided; child rows inherit
their parent card surface unless they represent an independently elevated object.

`scripts/verify-ui-tokens.sh` enforces this policy over production Compose sources and is
part of both repository verification entry points through `verification-common.sh`. It also
rejects direct `MaterialTheme.shapes.*` consumption outside token-definition files so semantic
Hikari shapes remain the application-facing contract. Token definition files under
`core/designsystem/.../theme/` are the only visual-literal allowlist.

## Shared component rule

A repeated visual pattern has one owner. Domain-neutral patterns such as content cards, glass panels,
round icon actions, glyphs, search chrome, filter chips, metadata groups/badges, wrapping
metadata badge collections, inline feedback, section/destination headers, bottom-sheet content
chrome, and application
navigation live in `:core:designsystem`. Domain-aware repeated story/catalog patterns live
in the owning presentation feature; for example update cards shared by Home and Updates
live under `feature/catalog/.../ui/components`. Screens compose those contracts and map
state; they do not fork a component to change padding, radius, border, alpha, icon geometry,
touch size, heading semantics, or failure chrome locally. Metadata badge collections use
`HikariMetadataBadgeGroup`: badges keep their existing visual treatment, lay out horizontally
first, and wrap only when the available width is exhausted.

`scripts/tests/ui-shared-component-policy-test.sh` guards the high-value ownership boundaries
that previously drifted so the repository static gates reject those feature-local forks.
The guard also rejects app/feature-owned vector path geometry, font-text chevrons, raw fixed
grid counts, redundant destination content-color wrappers, and heading-semantic forks that
bypass the shared design-system owner.

List-sized cover artwork uses `HikariListArtworkFrame` so compact thumbnails share the same
semantic cover rounding instead of rendering square inside rounded content cards. Filled product
CTAs use `HikariPrimaryAction`; content/list secondary actions use `HikariContentAction`, an
outlined pill with the shared minimum target. Compact toolbar and utility text actions use
`HikariUtilityAction`, while icon-only utilities use `HikariIconActionStyle.TONAL`. Filled and tonal
Hikari action owners explicitly disable Material button elevation so interaction states cannot
reintroduce a directional shadow. Confirmation
actions and destructive semantics remain separate contracts rather than being flattened into the
content-action style.

Story Overview, Chapters, and Sources use the same `HikariSectionHeader` mini-header contract
with aligned outer spacing. The header supports an optional subtitle and trailing utility action.
The three tab bodies consume the feature-owned `storySectionContentPadding()` outer inset contract;
nested content inherits that horizontal inset instead of stacking feature-local padding.

Cross-screen manual refresh uses `HikariPullToRefresh`. The design system owns the Material 3
pull gesture, zero-elevation indicator container, centered Hikari shadow, theme-driven progress
indicator, busy-state semantics, and the accessibility `Refresh` action; features only provide
their real refresh callback and scrollable content. Edge-to-edge
hosts pass the already-computed safe top inset into the refresh owner and remove that same top
inset from their scroll content padding, so the indicator and content consume system insets exactly
once. A pull gesture is enabled only where the feature has a matching refresh pipeline. Visible
Retry actions remain for retryable failures, and feature code must not reintroduce manual refresh
buttons, refresh glyphs, or duplicate refresh progress chrome for the same operation. Story Overview
and Sources are refreshable; Story Chapters is intentionally not refreshable until chapter
synchronization has its own pipeline. Source-detail refresh failures render only in Overview/Sources,
not above Chapters.

## When to use Material directly

Use Material 3 directly for standard controls and layout primitives when Hikari adds no
visual rule beyond the theme. Do not wrap every Material component. Once a visual rule
is Hikari-specific or repeats across screens, expose a shared component or semantic token
instead of customizing each call site. `FilterChip` is intentionally wrapped by
`HikariFilterChip` because Hikari owns its minimum interactive target; `DropdownMenu` is wrapped
by `HikariDropdownMenu` because popup shape/elevation/shadow are product-owned. Interactive
one-off Material controls may still be used directly when no Hikari-specific presentation exists.

## Loading

| Situation | Presentation |
|---|---|
| Initial content unavailable | `HikariLoadingState` |
| Refresh with existing content | Keep content; `HikariPullToRefresh` owns refresh progress where pull refresh applies |
| Pagination | Feature-owned local footer or progress |
| Action-local operation | Feature-owned local progress |
| Background work | Do not block the screen |

## Empty states

| Situation | Presentation |
|---|---|
| True empty | Feature copy with `HikariEmptyState` |
| Filtered empty | Feature copy with an optional clear-filter action |
| Search empty | Feature copy |
| Setup required | Feature copy with a feature-owned action |
| Offline with no cache | Feature mapping with `HikariOfflineState` |

## Error presentation

| Situation | Presentation |
|---|---|
| No usable content and failure | `HikariErrorState` |
| Usable cached or current content exists | Keep content and show a non-blocking failure |
| Retryable action | Feature supplies the Retry action |
| Domain exception | Map in the feature; never pass the exception to the design system |

## Offline presentation

Use `HikariOfflineState` only after the feature maps its connectivity and cache
state to an offline presentation. The feature supplies precise copy and the
appropriate retry, settings, or navigation action.

## Snackbar vs Toast

| Situation | Feedback |
|---|---|
| Active Compose surface transient feedback | Snackbar |
| Platform or lifecycle-only exceptional case | Toast may be reviewed |

Use the app-owned snackbar host without a singleton manager or global event bus.

## Confirmation and destructive actions

| Situation | Confirmation rule |
|---|---|
| Harmless action | No confirmation |
| Reversible action | Prefer undo when the feature supports it |
| Meaningful destructive action | Confirmation |
| Irreversible or high-impact action | Destructive confirmation with precise feature copy |

The feature owns the action consequence and dialog visibility. Destructive
style changes only the generic confirm-action presentation.

## Feature mapping boundary

Features translate domain state into design-system parameters. The design
system must not define a global `UiState`, `UiError`, exception mapper, retry
policy, navigation effect, or feature event type.

## Accessibility baseline

Visible state labels must remain available through Compose semantics. Controls
use explicit caller-provided labels, preserve minimum Material touch targets,
and keep error or destructive meaning available through text rather than color
alone.

## What the design system does not own

The design system does not own domain copy, feature state machines, navigation policy,
retry consequences, storage/capability behavior, or plugin semantics. Those remain with
their existing owners. It does own the visual vocabulary used to present them, including
semantic content geometry that must remain consistent across the product.

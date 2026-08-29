# Hikari Design System

Date: 2026-08-20
Status: **CANONICAL UI ownership and token policy**

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
semantic cover rounding instead of rendering square inside rounded content cards. Action hierarchy
is deliberately sparse: `HikariPrimaryAction` is reserved for the single dominant CTA,
`HikariUtilityAction` is the normal tonal secondary action, and `HikariInlineAction` is the
borderless low-priority/destructive text action used when another pill would add visual noise.
Outlined action chrome is intentionally not part of the default Hikari action hierarchy. Icon-only
utilities default to `HikariIconActionStyle.TONAL`; callers request
`GLASS` only when they are actually inside a `HikariBackdropHost` overlay and pass a real backdrop
scope. Filled and tonal Hikari action owners explicitly disable Material button elevation so
interaction states cannot reintroduce a directional shadow. A card or hero with child actions has
one click owner per semantic region: either the container owns navigation or child actions do, never
both through nested clickable modifiers.

Story Overview, Chapters, and Sources use the same `HikariSectionHeader` mini-header contract
with aligned outer spacing. The header supports an optional subtitle and trailing utility action.
Compact-width Story heroes stack Read/Download actions vertically so labels retain a full-width touch
target; large phones may use the wider horizontal hero action layout. Chapter filters use the shared
chip language in a wrapping `FlowRow`, while bulk/range download commands stay inline instead of
adding another layer of full-width pill chrome. The three tab bodies consume the feature-owned
`storySectionContentPadding()` outer inset contract; nested content inherits that horizontal inset
instead of stacking feature-local padding.

Cross-screen manual refresh uses `HikariPullToRefresh`. The design system owns the Material 3
pull gesture, zero-elevation indicator container, centered Hikari shadow, theme-driven progress
indicator, busy-state semantics, and the accessibility `Refresh` action; features only provide
their real refresh callback and scrollable content. Edge-to-edge
hosts pass the already-computed safe top inset into the refresh owner and remove that same top
inset from their scroll content padding, so the indicator and content consume system insets exactly
once. A pull gesture is enabled only where the feature has a matching refresh pipeline. Visible
Retry actions remain for retryable failures, and feature code must not reintroduce manual refresh
buttons, refresh glyphs, or duplicate refresh progress chrome for the same operation. Story Overview
and Sources invoke the Story-owned source-detail metadata refresh, while Story Chapters invokes its
own `ChapterSyncService.sync(storyId)` pipeline through Chapter state. All three sections are
refreshable, but their progress and failures remain operation-scoped: source-detail refresh feedback
renders only in Overview/Sources, and Chapter refresh feedback stays inside Chapters. Background sync
does not set either manual pull-refresh state.

The [Content State Contract v1](../superpowers/specs/2026-08-27-content-state-contract-v1-design.md)
is authoritative for feature state semantics. `:core:designsystem` owns only the rendering primitives;
it does not own feature `UiState`, cache lifetime, readiness classification, or refresh scheduling.

## Segmented selection and skeleton loading

`HikariSegmentedControl<T>` is the domain-neutral owner for equal-width single-choice
segmented selection. Callers provide `HikariSegmentedOption<T>` values, the selected key,
and selection callback. The control owns full-width layout, equal `weight(1f)` segments,
Material 3 segmented-button shapes, minimum Hikari touch target, focus handoff for the first
segment, selected semantics, and disabled-state behavior. Feature code owns the meaning of
keys/options and must not fork the chrome to create content-sized pseudo-segments.

`HikariSkeleton` is the shared static loading block. It deliberately has no shimmer or
feature-local animation policy; callers supply semantic Hikari shapes and tokenized geometry.
Initial screen loading may compose multiple skeleton blocks to mirror the final hierarchy,
but cached refreshes keep real content visible instead of replacing it with skeletons.

The current Discover implementation is the primary reference for both primitives: its media
selector uses the shared segmented control, while initial loading mirrors the Popular hero,
media selector, Latest grid, and Top Rated rows with static skeletons. Discover-specific labels,
feed limits, and semantic ranking remain in `:feature:catalog`, not `:core:designsystem`.

## UI hierarchy and performance rules

Scrollable collections must preserve real laziness. Repeated chapter releases, source mappings,
failures, candidates, and similar unbounded collections are emitted as independent `LazyListScope`
items with stable keys; they must not be rendered with `forEach` inside one lazy item. Heterogeneous
hot lists such as Reader also declare `contentType` so compatible compositions can be reused. A
vertical lazy container may contain bounded horizontal shelves, but nested vertical scroll owners
require an explicit architecture review.

Rapid gesture state stays as close to the gesture as possible. Slider drag values remain local and
commit feature/ViewModel state from `onValueChangeFinished`. Reader samples active scroll sessions at a bounded cadence before sending precise viewport
positions into its long-lived conflated/debounced persistence pipeline, and reports the exact final
position when scrolling stops. Visible progress is bucketed to whole percent inside a stable local
holder so per-pixel scroll does not invalidate the Reader screen tree.
Reader document content padding is stable whether chrome is visible or hidden; toolbar visibility is
an overlay concern and must not relayout the document.

`HikariModalSheet` owns Material modal-sheet shape, color, and zero tonal elevation. Features own
sheet content/state but do not instantiate `ModalBottomSheet` directly. Reusable visual brushes are
created once with the theme or remembered at the artwork boundary; composables should not allocate
full-surface gradient brushes on every recomposition. Discover hero scrims are drawn by the artwork
backdrop owner rather than stacked as extra full-size layout surfaces. The current semantic
Discover keeps one vertical `LazyColumn` owner: Popular may page horizontally, Latest is a
bounded non-scrolling three-column composition, and Top Rated emits bounded rows. Provider
identity and feed aggregation are resolved before Compose; the UI never infers semantic feed
meaning from source titles or plugin IDs.

Frame-sensitive UI paths are protected by Macrobenchmarks in addition to startup/navigation checks.
The benchmark fixture provides deterministic long Reader content and populated Discover/Library
collections. In addition to `readerScrollLongChapter`, `chaptersExpandAndScroll`, `libraryListScroll`,
and `discoverScroll`, paired P5 journeys isolate Reader backdrop blur, repeated Chapter-card shadows,
and legacy top-level navigation transitions with `FrameTimingMetric`. These measurements are the
runtime authority for jank regressions; code review alone is not evidence that scrolling is smooth.

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
| Offline with no cache | Feature-owned copy composed from shared empty/error feedback primitives |

## Error presentation

| Situation | Presentation |
|---|---|
| No usable content and failure | `HikariErrorState` |
| Usable cached or current content exists | Keep content and show a non-blocking failure |
| Retryable action | Feature supplies the Retry action |
| Domain exception | Map in the feature; never pass the exception to the design system |

## Offline presentation

Offline copy remains feature-owned because cache/connectivity semantics differ by destination.
Compose it from the shared empty/error/feedback primitives, with the feature supplying the precise
retry, settings, or navigation action.

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

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
responsive breakpoints, layout ratios, semantic shapes, opacity, artwork colors, and
semantic typography come from the `MaterialTheme.hikari*` token families. Feature and
app code must not introduce local `dp`/`sp` literals, ad-hoc rounded/circle shapes,
palette colors, literal alpha values, or local font family/weight overrides.

Content geometry is semantic but is not exempt from tokenization. Poster sizes, hero
heights, reader insets, grid minimums, and similar measurements belong in named design
system dimension tokens rather than feature-local constants. Responsive decisions use
`MaterialTheme.hikariBreakpoints`; a feature must not invent its own viewport threshold.

`scripts/verify-ui-tokens.sh` enforces this policy over production Compose sources and is
part of both repository verification entry points through `verification-common.sh`.
Token definition files under `core/designsystem/.../theme/` are the only visual-literal
allowlist.

## Shared component rule

A repeated visual pattern has one owner. Domain-neutral patterns such as glass panels,
round icon actions, glyphs, search chrome, metadata badges, feedback, and application
navigation live in `:core:designsystem`. Domain-aware repeated story/catalog patterns
live in the owning presentation feature. Screens compose those contracts and map state;
they do not fork a component to change padding, radius, border, alpha, icon geometry, or
touch size locally.

## When to use Material directly

Use Material 3 directly for standard controls and layout primitives when Hikari adds no
visual rule beyond the theme. Do not wrap every Material component. Once a visual rule
is Hikari-specific or repeats across screens, expose a shared component or semantic
token instead of customizing each call site.

## Loading

| Situation | Presentation |
|---|---|
| Initial content unavailable | `HikariLoadingState` |
| Refresh with existing content | Keep content and show local progress |
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

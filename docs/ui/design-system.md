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

Application Compose roots use `HikariTheme`. Use `MaterialTheme.hikariSpacing`
for repeated application spacing rules: 4, 8, 12, 16, 24, and 32 dp. Keep
feature-specific measurements local when they encode content geometry rather
than a global spacing rule.

## When to use Material directly

Use Material 3 directly for standard controls and layout primitives when Hikari
does not add an application-wide rule. Do not wrap every Material component.
Add a design-system primitive only when an existing cross-feature need proves a
stable domain-neutral contract.

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

## What this foundation does not standardize

This foundation does not redesign screens, define brand artwork, own domain
copy, replace feature state machines, select navigation, introduce global
feedback buses, or normalize feature-specific content geometry. Screen-level
visual design remains later roadmap work.

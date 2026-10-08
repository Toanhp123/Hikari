# ADR-015: Features consume only the canonical Material color scheme

Status: Accepted

## Context

ADR-014 consolidated the two root design systems, but preserved `HikariColors` as a parallel field-name adapter. Most of its state mirrored ColorScheme exactly; each new theme change would need to keep the adapter synchronized. Several media-type badge consumers used foreground colors as arbitrary background tints or duplicated status fallback hex values.

## Decision

Root `HikariTheme.darkTheme` is the only application ThemeData factory. Features use `Theme.of(context).colorScheme` / `textTheme`, with shared `HikariSpacing`, `HikariRadius`, `HikariMotion` and `HikariBreakpoints` tokens for geometry and animation. Delete `HikariColors` entirely. Keep the `HikariStatusColors` extension only for warning/info pairs absent from Material 3; its context accessor fails on a missing canonical theme. Resolve media badge container and foreground through `mediaTypeBadgeColors(context, type)` in the existing shared presentation module. No new dependency or generalized color-token registry is warranted.

Retain independent prose backgrounds for Novel Reader, and fixed image/video scrim blacks and whites where the visual is not an application Material surface. Do not replace business logic, reader color preferences, or the chosen custom adaptive navigation. Theme selection remains session-local until product requirements change.

## Consequences

Legacy colors used throughout the app are now native Material roles, which removes the original compatibility API. Theme-aware widget tests must install the root theme, including its required custom status extension. Future UI changes must resolve semantic colors through the root scheme or the two extra status pairs, never local hex fallbacks.

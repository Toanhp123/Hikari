# ADR-014: Consolidate one canonical Hikari application theme

Status: Accepted

## Context

ADR-013 initially established a separately defined, unwired Material 3 design foundation. Subsequent Home/navigation/SearchBar adoption left two theme owners in the same application. The majority of feature consumers still use `HikariTheme`, `HikariSpacing`, `HikariRadius`, `HikariTypography`, `HikariMotion`, `HikariBreakpoints` and `context.hikariColors`. Maintaining two systems allows OLED/accent-seed drift and transitive color fallback.

## Decision

Use the existing `hikari_*` vocabulary as the **only public design API**, porting the new palette, semantic native themes, typography, geometry, motion and bounded layout values into it. Remove `lib/app/theme/design_system/` and its scoped-theme consumers. The root `MaterialApp` is the sole owner of `HikariTheme.darkTheme` and supplies original OLED and accent-seed inputs. `HikariColors` remains a derived view of that exact `ColorScheme` for old feature consumers, not an independent palette. Keep independent reading palettes for prose. Preserve existing custom navigation, feature state ownership and media workflows in this pass.

## Alternatives

- Maintain both themes with long-lived compatibility bridges: more authorities, more regression seams and unnecessary migration complexity.
- Rewrite all feature consumers to direct `ColorScheme` immediately: broad blast radius for minimal initial value.
- Leave the target theme scoped in Home/navigation: perpetuates OLED/seed drift.

## Consequences

Existing feature colors and typography can change intentionally because the canonical root now uses the consolidated visual system. Style changes do not imply functional validation: screen reader, keyboard, nonlinear text scaling, navigation state-on-resize, bottom insets, and blur performance require separate work. Historical ADR-013 remains available for rationale. [Current contracts](../architecture/DESIGN_SYSTEM.md) live in one place.

# ADR-013: Independently defined, unwired design foundation

Status: Accepted

## Context

Hikari already has live theme tokens and shared widgets, but partial Material mapping, local visual constants and reader-specific palettes prevent a consistent migration target. Replacing the live theme now would violate the foundation-only scope. The architecture guard already isolates `app/theme` from domain, application, infrastructure and feature code; ordinary `core` remains Flutter-free.

## Decision

Place the canonical target in `lib/app/theme/design_system/`, exposed by one barrel. Use Flutter ColorScheme, TextTheme and native component themes, plus small geometry, width-class, motion and reading-palette APIs. Add a narrow warning/info foreground-container ThemeExtension because Material ColorScheme has no such roles; do not duplicate native semantic roles. No new dependencies or widget library.

Legacy theme and current screens remain unchanged. New values are independent of legacy tokens, so removing old files during migration cannot change the target. Support existing app dark/OLED modes and separate reading canvas choices; defer an app-wide light mode until product intent exists. Accent customization is a seed for accessible paired colors, not unchecked direct primary replacement.

[Design System](../architecture/DESIGN_SYSTEM.md) owns token roles, component contracts, audit evidence and adoption rules. Existing UI architecture remains authority for widget ownership. Tests enforce isolation and temporary no-wiring boundary; remove only the no-wiring assertion when adoption is explicitly requested.

## Alternatives

- Rewrite current theme: smallest file count, but changes every current screen immediately.
- Add `core/design_system`: conflicts with existing pure-core boundary and needs unrelated guard changes.
- Create wrappers for every component: duplicates Material and existing Hikari widgets without proven new behavior.

## Consequences

Two theme implementations coexist temporarily, explicitly current versus target rather than competing authorities. Migration must update shared primitives before switching root theme and must not mix legacy color extensions with target colors. Default controls retain native accessibility behavior, but feature layouts still require scaled-text, keyboard, TalkBack and real-artwork verification. No current-screen accessibility defect is claimed fixed by this foundation.

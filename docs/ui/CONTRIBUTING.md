# UI contributions

Owner: Hikari UI foundation maintainers. Canonical API: `lib/core/ui/hikari_ui.dart`.
Architecture authority: [ADR-026](../decisions/ADR-026-ui-foundation-rebuild.md).

## Lifecycle

- **Draft**: proposed contract, no consumption promise.
- **Preview**: implemented and testable; API may change through reviewed migration.
- **Stable**: promotion requires all checks below, not merely compilation.
- **Deprecated**: named replacement, exact remaining callers, owner and removal task.

## Promotion checklist

- Purpose/non-goals, typed API, variants and controlled/uncontrolled ownership documented.
- Controller/focus/listener disposal proven, including external replacement.
- Native keyboard, hover, focus, disabled/loading and semantics verified.
- 48dp Android targets, 1x/2x/3x text, narrow/local width, RTL and short-height/IME checked.
- Dark/OLED semantic contrast checked; platform screen-reader evidence recorded.
- Runnable gallery examples and focused regression tests; fresh analyzer/full checks.
- Migration matrix updated, no new implicit dependencies or wildcard exemptions.
- Owner review and changelog entry; physical NOT RUN stays NOT RUN.

## Change protocol

Read current callers and canonical contracts before changes. Test changed behavior
first, observe intended failure, then implement and rerun. Characterize existing
behavior honestly. Use Material primitives directly when no Hikari-specific
contract exists. Keep fetching, authorization and domain mapping feature-owned.

Update [component catalog](COMPONENTS.md), [controls](components/controls.md),
[migration matrix](MIGRATION_MATRIX.md) and affected architecture docs in same diff.
Record deprecations with replacement, exact last caller and expiry; do not keep
permanent duplicate APIs. No dependency without verified need and architecture
review. Release app must never import standalone gallery.

## Local gallery

`fvm flutter run -d windows -t tool/ui_gallery/main.dart`

Android: choose connected device with `-d <device-id>`. Gallery is standalone,
not production navigation. Widget smoke is not physical runtime certification.

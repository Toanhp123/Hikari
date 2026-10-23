# UI foundations

Status: **Phase 01 Preview foundation present; G1 OWNER-ACCEPTED (2026-10-23) for continuation**, not retrospectively certified for all physical tests.
See [owner decision](../roadmap/UI_FOUNDATION_OWNER_ACCEPTANCE_2026-10-23.md).
Current task/gate evidence: [Phase 01 execution](../roadmap/UI_FOUNDATION_PHASE_01.md).
Spacing, radius, motion, size, breakpoints, typography, status roles and the
required context getter live in `lib/core/ui/foundation`. Seven app/theme token
forwarders were removed after the last import migrated. Features and core UI
import foundation directly; app/theme has no token re-exports or accent aliases.
Raw accent presets live in `HikariAccentSeeds`, not generated primary colors.
The root composer only builds ThemeData.
Current theme composition is recorded in
[DESIGN_SYSTEM](../architecture/DESIGN_SYSTEM.md).

Task 01 evidence (2026-10-22): canonical-file ownership test failed before
relocation; unchanged boundary/invalid-width behavior characterized GREEN.
After relocation, 40 theme/architecture/inventory/documentation tests passed;
`fvm flutter analyze --no-pub` reported no issues. Migration-owner test also
observed RED then GREEN. Token values, timing and surface behavior unchanged;
no physical platform behavior claimed. Exact pending imports and forwarder
removal owners remain in [migration matrix](MIGRATION_MATRIX.md).

Decision authority: [ADR-026](../decisions/ADR-026-ui-foundation-rebuild.md).
Execution: [master](../superpowers/plans/2026-10-22-hikari-ui-rebuild-master.md).

Task 02 evidence (2026-10-22): missing canonical color/typography ownership
observed RED. After relocation, 12 theme/architecture tests passed and analyzer
reported no issues. Existing extension copy/lerp and contrast across both modes
and all six seeds characterized GREEN: tested text pairs >=4.5:1 and outline on
canvas >=3:1. These mathematical checks do not certify physical overlays,
TalkBack or native rendering. All typography values and status pairs preserved.

## Target dependency map

| Owner | Owns | Allowed dependencies / exclusions |
|---|---|---|
| core/ui/foundation | spacing, radius, dimensions, typography, motion, semantic extensions, responsive policy | Flutter; no component/widget-family dependencies |
| core/ui controls/surfaces/layout/media/content/navigation | domain-free presentation and common interaction mechanics | foundation, Flutter, explicit shared UI modules; no app/features/domain/application/infrastructure |
| app/theme | one root ThemeData composition | foundation; no feature UI |
| app/navigation | destination identity, selected tab, route composition | shared navigation mechanics and feature entrypoints |
| features | VM, data/query, domain mapping, authorization, stale outcomes | shared UI + domain/application abstractions; no app/** or infrastructure |

ColorScheme/TextTheme remain semantic authority; do not recreate HikariColors.
Required semantic extensions fail visibly if absent, never silent defaults.
Dark/OLED and raw accent-seed behavior remain protected; current implementation
has no light-theme factory. Reader paper/charcoal and video black are specialist
content surfaces, not application light-theme support.

Token relocation belongs to 01/01–02; import migration and temporary-forwarder
removal belong to 01/07. Completed token migration has no duplicate values or
permanent facade. Nineteen exact later-phase domain/modal sites remain staged;
no theme inversion is exempt, including import/export/part and relative URIs.

See [responsive](RESPONSIVE.md), [components](COMPONENTS.md),
[migration matrix](MIGRATION_MATRIX.md) and [baseline evidence](BASELINE_RUNS.md).

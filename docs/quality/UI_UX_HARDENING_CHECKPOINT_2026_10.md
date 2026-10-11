# UI/UX hardening checkpoint — October 2026

Status: **source-reviewed; runtime/device verification pending**.

## Scope and authority

This checkpoint follows the Home, Catalog, Library, Local and Settings UI passes,
plus the separate Reader Session, Search Consistency and Appearance Reliability
patches. It records the **current direction**, not a new architecture. The
canonical contracts remain in [`DESIGN_SYSTEM.md`](../architecture/DESIGN_SYSTEM.md),
[`UI_ARCHITECTURE.md`](../architecture/UI_ARCHITECTURE.md) and the
[`UI_UX_COMPLETION.md`](../roadmap/UI_UX_COMPLETION.md) roadmap.

The review pack under `docs/ui-ux/` is a **historical 2026-10-07 snapshot**.
In particular, its descriptions of dual theme authorities and an unfinished
app-wide migration no longer describe the current code. Preserve its rationale
and ADRs, but do not apply its old phase gates as live implementation status.

## Phase 4 changes

- Added explicit accessible labels and progress values to Manga Reader's page
  scrubber and Video Player's playback slider; existing navigation, seek and
  progress callbacks remain unchanged.
- A scoped Source Search label now exposes its full source name once, even when
  the visible text is truncated, instead of duplicating it in the semantics tree.
- Marked legacy UI review landing pages/checklist as historical; no ADR history
  has been rewritten.
- Added focused widget tests for the changed accessibility contracts.

## Verification status

| Gate | Status |
| --- | --- |
| Patch applicability on the supplied ZIP plus Phase 3 | Verify with `git apply --check` (patch deliverable) |
| Dart static analysis and formatter | **Not executed in this environment** (Flutter/Dart SDK unavailable) |
| Focused widget and full test suites | **Not executed in this environment** |
| Android release/debug build | **Not executed in this environment** |
| TalkBack, hardware keyboard and display/font scaling | **Device/manual verification required** |
| Performance | No performance claim or speculative optimization in Phase 4 |

On the project checkout after applying the Phase 3 and Phase 4 patches:

```powershell
fvm dart format --output=none --set-exit-if-changed lib test
fvm flutter analyze
fvm flutter test test/reader_control_accessibility_test.dart
fvm flutter test test/source_search_page_test.dart
fvm flutter test
.\tool\check.ps1
fvm flutter build apk --debug
```

On Android, also verify: TalkBack announces the **Manga page number/total** and
**Video playback percentage**, can adjust both sliders, and announces a long
Source Search scope **once**; navigate via Tab/Enter/Space on a keyboard.
Check 320–375dp and 200% system text, short landscape, desktop resizing, and
OLED/accent changes including an intentionally failing appearance save.

Do **not** mark the overall UI/UX phase complete until the runtime and manual
checks have actually passed. Separate feature roadmaps (cache management,
reader defaults, extension sources) remain outside this UI-only checkpoint.

# ADR-017: Page header contract

Status: Accepted for the UI/UX completion pass (2026-10-10).

## Context

Home has branded pinned sliver chrome, Library and Settings used similar hand-styled `AppBar` titles, and Local built a second title row inside its body. The same typography was being declared in several places. Home's pinned header uses scroll-offset-driven glass chrome; there was a risk of incorrectly replacing that behavior with `overlapsContent` without checking the sliver render implementation.

## Decision

- Use `HikariTheme.darkTheme().appBarTheme` as the only owner of the default top-level title styling; `Library`, `Local` and `Settings` compose the native `AppBar` without duplicating that styling.
- Use short top-level bars for destinations with a small title and contextual actions. Keep `HomeHeader` feature-owned because its brand and scroll-under surface are distinct. Keep detail/search headers feature-owned where their information architecture warrants it.
- Keep the Local folder picker as an explicit body action; the app bar contains only its contextual rescan. Do not add search or sort commands without implemented behavior.
- Preserve Home's gradual scroll response through `shrinkOffset`. The pinned renderer passes `min(scrollOffset, maxExtent)` to the delegate even when `minExtent == maxExtent`; `overlapsContent` checks an incoming overlap instead and is not a safe replacement for the first sliver. Make the branded title flexible to avoid width overflow at larger text scales.
- Keep the Library filters and displayed-count context above the scrollable collection, provide a recovery action for zero filter results, and offer catalog navigation only when the shell supplies a real callback.
- Prefer existing theme, `HikariIconButton`, `AsyncStateView`, and `HikariChip` to a new header hierarchy; extract shared visual primitives only if subsequent passes show genuine repeated behavior.
- Honor accessible text sizing, semantic heading, explicit icon tooltips, at-least-48dp interactive targets, and bounded content on wide windows.

## References

- Material 3 app bar anatomy and variants: https://m3.material.io/components/app-bars/overview
- Android Material Components top app bars: https://github.com/material-components/material-components-android/blob/master/docs/components/TopAppBar.md
- Flutter pinned renderer implementation: https://flutter.googlesource.com/mirrors/flutter.git/+/refs/heads/main/packages/flutter/lib/src/rendering/sliver_persistent_header.dart
- Flutter delegate contract: https://api.flutter.dev/flutter/widgets/SliverPersistentHeaderDelegate/build.html
- Flutter accessibility and tappable targets: https://docs.flutter.dev/ui/accessibility/ui-design-and-styling
- Android top app bar navigation/actions/scroll behavior: https://developer.android.com/develop/ui/compose/quick-guides/content/display-app-bar

## Verification

Run `fvm dart format .`, `fvm flutter analyze`, and `fvm flutter test` in the normal Flutter environment. Specifically validate pinned Home chrome at scroll offsets 0 and >0, compact widths with large fonts, Library empty/filter/view transitions, Local rescan/picker states, and Settings actions. Native mobile profiling remains a separate task.

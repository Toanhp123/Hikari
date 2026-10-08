# Hikari Design System

Status: **canonical root theme** (consolidated from the earlier parallel design foundation).

Public entry: `lib/app/theme/hikari_theme.dart`. One `HikariTheme.darkTheme(oled:, accentColor:)` is created at the application root. Home, navigation and SearchBar inherit it; they must not construct scoped application themes. [ADR-014](../decisions/ADR-014-canonical-theme-consolidation.md) records the consolidation; [ADR-013](../decisions/ADR-013-design-system-foundation.md) documents the original independent foundation.

## Product language

Artwork-first discovery, quiet chrome, dense metadata without tiny text, and restrained Catppuccin-inspired dark surfaces. The default user accent seed is `#B4BEFE`, with accent choices passed **once** as raw seeds to `HikariTheme.darkTheme`. `ColorScheme.fromSeed` generates accessible semantic color pairs; its generated `primary` is never recycled as a seed. The OLED variant makes the page and lowest surface pure black while preserving separate raised tonal surfaces.

Material 3 `ColorScheme`, `TextTheme` and native component themes own presentation semantics. Do not create a custom control for a behavior Material already handles unless product needs justify it. Shared filter and icon controls now delegate interaction, focus and semantics to native `FilterChip` / `IconButton`, retaining Hikari presentation where required. Reader canvases (OLED, charcoal, paper) remain separate from application chrome.

## Canonical API

| Responsibility | API | Contract |
| --- | --- | --- |
| Root/native theme | `HikariTheme.darkTheme(oled:, accentColor:)` | One app-owned `ThemeData`; native widget themes and semantic surface roles |
| Native colors | `Theme.of(context).colorScheme` | Primary/secondary/on-role pairs, tonal surfaces, outline/error/inverse roles |
| Existing semantic fields | `context.hikariColors` | `HikariColors` is derived from the SAME `ColorScheme`; not an independent palette |
| Warning/info | `HikariStatusColors` | Container/foreground pairs for roles absent from Material; always pair correctly |
| Typography | `HikariTypography.textTheme` / `Theme.of(context).textTheme` | 12dp minimum semantic metadata, Material text slots, platform font fallback |
| Spacing | `HikariSpacing` | xs/sm/md/lg/xl/xxl/xxxl = 4/8/12/16/24/32/48dp |
| Radii | `HikariRadius` | sm 8, md 14, lg 16, xl 24, `pill` StadiumBorder; xs 4 for small inner badges |
| Shared dimensions | `HikariSize` | Touch 48, field 48, icon 24; intrinsic poster/landscape ratios remain component-owned |
| Motion | `HikariMotion` | 150 interaction / 200 exit / 300 standard / 400 emphasized; responsive to disableAnimations/reduceMotion |
| Layout | `HikariBreakpoints` | compact <600, medium <840, expanded <1200, wide >=1200; content max 1440 |
| Reader palette | `NovelReaderTheme` (feature-owned) | OLED, charcoal and paper prose surfaces remain independent of app chrome |

Presentation widgets use inherited `Theme.of(context).textTheme` for fonts/weights; `HikariTypography.textTheme` defines the base scale in one place. No separate static-style accessors remain for feature text; feature-specific visual overrides remain local. `HikariColors` remains a compatibility view of the root scheme for existing consumers; it maps legacy properties such as `textPrimary` → `ColorScheme.onSurface` and `surfaceElevated` → `surfaceContainerHigh`. `badge*` roles remain for existing consumers; category/status color pairing should be tested per component. No blind map of warning/info foreground colors to text-on-badge backgrounds.

Use local `LayoutBuilder` constraints for a component's responsive content decisions; only root navigation classifies app window width. Effective navigation/safe-area/keyboard obstruction is runtime layout data, **not** an app-wide spacing token. Bottom obstruction is handled by the owning scrollable and scaffold, not by a fixed global spacer.

## Component behavior

- Action types: native FilledButton, OutlinedButton, TextButton; minimum 48dp touch targets; preserve focus, keyboard and disabled state semantics. Shared filter/icon actions delegate focus, semantics and keyboard behavior to native Material controls.
- Sections: semantic TextTheme slots, feature-owned heading/metadata and actions. No generic shelf DSL or mega-card component.
- Cards: stable artwork aspect ratio, separate scaled title area, useful error/fallback imagery, no default shadow; full labels available to assistive technology even when visual copy truncates.
- Search: native `SearchBar` theme is root-owned. Existing query controller and debounce behavior remain owned by `HikariSearchBar`.
- Loading/empty/error states: preserve feature state snapshots and retries; keep paired semantic status colors.
- Dialogs/sheets: use native component styling, safe area and content scrolling. Routes and focus restoration remain feature-owned.
- Navigation: the current custom docked bar/rail remains in this pass; separately evaluate native presentation against visual fidelity and accessibility, not by assumption.
- Player/reader: immersive black and paper/charcoal prose are reading/playback surfaces, not forced to use app page `surface`.

## Quality and outstanding work

Consolidation **does not certify** accessibility, layout resilience, or performance. Maintain tests for actual root inheritance (including accent/OLED), semantic pairs, width boundaries, reduced motion and existing search behavior. Profile blur/image decode on device before claiming a performance regression or replacing image policy. Historical audits cover resize state retention, text scaling, badge contrast, rail selection and bottom insets; current controls retain native focus/selection behavior. Native navigation remains a separate design decision; TalkBack/device validation remains manual.

## Reference principles

- [Flutter Material ThemeData](https://api.flutter.dev/flutter/material/ThemeData-class.html)
- [Flutter ColorScheme](https://api.flutter.dev/flutter/material/ColorScheme-class.html)
- [Flutter adaptive best practices](https://docs.flutter.dev/ui/adaptive-responsive/best-practices)
- [Flutter accessibility](https://docs.flutter.dev/ui/accessibility-and-internationalization/accessibility)
- [Flutter performance best practices](https://docs.flutter.dev/perf/best-practices)

The earlier parallel foundation was intentionally separate for its first milestone; the consolidation does not rewrite that history. All current source references and design guidance should use the canonical `hikari_*` APIs above.

## Theme hygiene (2026-10-08)

- `HikariTheme.defaultAccentSeed` and `HikariTheme.accentPresets` own the raw appearance choices. Settings offers a Default swatch to restore a nullable seed; appearance remains session-only by product policy.
- `MaterialApp` creates one active dark `ThemeData`, and Home/navbar/SearchBar inherit that root without scoped reconstruction.
- `HikariChip` delegates interactive filter selection to `FilterChip`; `HikariIconButton` delegates action handling to native `IconButton`. Both retain their existing feature call signatures.
- The dormant global `HikariReadingPalette` was removed. Novel prose continues to use its existing `NovelReaderTheme` without changes to rendered reading colors.
- Old snapshots and ADRs are historical evidence, not authoritative status for the current implementation.

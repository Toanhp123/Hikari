# Hikari UI Foundation Standardization & Decoupling Design

- **Status:** Proposed
- **Date:** 2026-10-21
- **Scope:** Architecture & implementation of the standard UI Kit in `lib/core/ui`, eliminating magic geometry calculations, decomposing monolithic pages, and enforcing pure presentation boundaries.

---

## 1. Context & Motivation

Hikari is a cross-platform media hub (Video, Manga/Comics, Light Novels) built with Flutter. While it has established clean layered architecture for domain and infrastructure (ADR-001, ADR-003, ADR-014), the presentation layer has accumulated structural clutter and tech debt over successive feature releases:

1. **Scattered Layout & Geometry Math (Magic Numbers):**
   Components such as `ContinueShelf`, `HomeRecentShelf`, and `MediaPoster` frequently instantiate local `TextPainter` instances to measure text line heights, and manually switch card dimensions (e.g. `isCompact ? 172.0 : 208.0`, `132.0`, `144.0`, `156.0`). These magic numbers create fragility and inconsistency across screens.
2. **Monolithic Page Widgets (God-Widgets):**
   Files like `catalog_detail_page.dart` (847 lines) and `apk_actions_page.dart` (666 lines with 20+ local state fields) mix async lifecycle, timers, dialog flows, and complex nested layout into single monolithic widgets.
3. **Leaky Component Boundaries:**
   Existing widgets in `lib/core/ui/patterns/` (such as `MediaPoster` and `MediaMetadataView`) combine artwork decoding, domain type formatting, layout calculations, and progress indicators. This tight coupling forces developers to build bespoke local widgets whenever a screen needs a slightly different card or list layout.

To ensure long-term maintainability, the UI foundation will be re-architected into a **Package-Ready, Pure Presentation UI Kit** located in `lib/core/ui`, accompanied by strict automated guardrails and an incremental migration path.

---

## 2. Goals & Non-Goals

### Goals
- **Strict Separation of Concerns:** Tiers in `lib/core/ui` are 100% "dumb" / pure presentation. They accept primitive data (`String`, `num`, `Widget`, callbacks) and never depend on `domain/`, `application/`, or `infrastructure/`.
- **Elimination of Magic Geometry:** Centralize aspect ratios (2:3, 16:9, 21:9, 1:1) and provide standard `AdaptiveGrid` and `HorizontalShelf` layout widgets. No local `TextPainter` height calculation hacks.
- **Tonal Surface Hierarchy:** Formalize Material 3 surface elevation roles (`surface`, `surfaceContainerLow`, `surfaceContainer`, `surfaceContainerHigh`, `surfaceContainerHighest`) using Catppuccin Mocha / OLED tokens, eliminating raw hex codes in components.
- **Adapter-Driven Feature Consumption:** Features map Domain models (`Media`, `CatalogEntry`) to UI props using clean adapter extensions, keeping page code declarative and concise.
- **Decomposition of Monolithic Pages:** Refactor giant pages into focused sub-widgets with clear responsibilities (<250 lines per file).
- **Automated Guardrail Enforcement:** Update `test/architecture_guard_test.dart` to compile-fail any prohibited import into `lib/core/ui/primitives` and `lib/core/ui/composites`.

### Non-Goals
- We are **not** splitting Hikari into a separate monorepo / multi-package workspace at this stage (consistent with ADR-003). The architecture is designed to be "Package-Ready" so it can be extracted in minutes if needed in the future.
- We are **not** altering the domain business logic, database schemas (Drift), or player/extension runtimes.
- We are **not** changing the visual brand language (Catppuccin Mocha dark theme + OLED support remains canonical).

---

## 3. Architecture & Directory Hierarchy

The new UI architecture organizes `lib/core/ui/` into four distinct layers:

```text
lib/core/ui/
├── tokens/
│   ├── aspect_ratios.dart           # Canonical media aspect ratios (2:3, 16:9, 21:9, 1:1)
│   ├── theme_extensions.dart        # Clean context extensions (context.colors, context.typography)
│   └── (re-exports of app/theme/ tokens: spacing, radius, breakpoints, motion, status colors)
│
├── primitives/                      # [Atoms & Molecules] 100% Dumb UI
│   ├── buttons/
│   │   ├── hikari_button.dart       # Primary, secondary, ghost, danger button variants
│   │   ├── hikari_icon_button.dart  # Bounded touch-target icon action
│   │   └── hikari_segmented_control.dart # Segmented choice control
│   ├── inputs/
│   │   ├── hikari_search_bar.dart   # Debounced search bar with submit semantics
│   │   └── hikari_choice_menu.dart  # Accessible anchor dropdown menu
│   ├── indicators/
│   │   ├── hikari_badge.dart        # Category & media-type badge (Anime, Manga, Novel, Status)
│   │   ├── hikari_progress_bar.dart # Reading/playback progress bar
│   │   └── async_state_view.dart    # Unified Loading, Empty, and Error state with retry
│   └── feedback/
│       ├── hikari_confirmation_dialog.dart # Standard confirmation modal
│       ├── hikari_modal_sheet.dart  # Bottom sheet presentation container
│       └── hikari_notice_banner.dart# Warning/info inline banners
│
├── composites/                      # [Organisms] Structured components built from Primitives
│   ├── media_card/
│   │   ├── media_poster_card.dart   # Vertical 2:3 card (artwork, title, subtitle, badge, progress)
│   │   └── media_landscape_card.dart# Horizontal 16:9 card for episode/continue shelves
│   ├── chapter_item/
│   │   └── chapter_row.dart         # Standard chapter/episode item with read & download states
│   └── section/
│       └── hikari_section_header.dart # Consistent section title, subtitle, and action link
│
└── layouts/                         # Standardized Responsive Layouts
    ├── adaptive_grid.dart           # Auto-sizing responsive poster grid (responsive to breakpoints)
    ├── horizontal_shelf.dart        # Uniform horizontal scrolling shelf with header & cards
    └── responsive_scaffold.dart     # Page container with integrated navigation clearance
```

---

## 4. Design Tokens & Tonal Surface Contract

### 4.1 Surface Elevation Levels
All UI components strictly resolve colors from `Theme.of(context).colorScheme`:

| Role | M3 Token | Dark Palette (Base) | OLED Palette | Use Case |
|---|---|---|---|---|
| Level 0 (Base Canvas) | `surface` | `#181825` | `#000000` | Scaffold background, root screen canvas |
| Level 1 (Low Surface) | `surfaceContainerLow` | `#1E1E2E` | `#11111B` | Navigation Rail, App Header, Subtle shelf container |
| Level 2 (Standard Card) | `surfaceContainer` | `#252739` | `#1E1E2E` | Media cards, search inputs, list item cards |
| Level 3 (Dialog & Sheet) | `surfaceContainerHigh` | `#2E3247` | `#252739` | Modal Bottom Sheet, Confirmation Dialogs, Dropdowns |
| Level 4 (Active / Hover) | `surfaceContainerHighest` | `#383C59` | `#313244` | Selected item highlight, hover states |

### 4.2 Aspect Ratios
```dart
abstract final class HikariAspectRatio {
  /// Vertical poster (Manga, Anime, Books, Comics, Movies): 2:3
  static const double poster = 2.0 / 3.0;

  /// Landscape media (Video episodes, screenshots): 16:9
  static const double landscape = 16.0 / 9.0;

  /// Cinematic hero banner: 21:9 (landscape) or 16:7 (compact)
  static const double hero = 21.0 / 9.0;
  static const double heroCompact = 16.0 / 7.0;

  /// Square avatar/icon: 1:1
  static const double square = 1.0;
}
```

### 4.3 Typography Constraints
- Card Titles: `maxLines: 2`, `overflow: TextOverflow.ellipsis`, `style: context.typography.titleSmall` or `labelLarge`.
- Card Subtitles / Metadata: `maxLines: 1`, `overflow: TextOverflow.ellipsis`, `style: context.typography.bodySmall`.
- Eliminates custom `TextPainter` layout hacks across feature cards.

---

## 5. Data Flow & Feature Adapter Pattern

Components in `core/ui/composites` remain independent of Domain models:

```dart
// lib/core/ui/composites/media_card/media_poster_card.dart
class MediaPosterCard extends StatelessWidget {
  const MediaPosterCard({
    super.key,
    required this.title,
    this.subtitle,
    this.artwork,
    this.badgeText,
    this.badgeColor,
    this.progressRatio,
    this.onTap,
  });

  final String title;
  final String? subtitle;
  final Widget? artwork;
  final String? badgeText;
  final Color? badgeColor;
  final double? progressRatio;
  final VoidCallback? onTap;

  @override
  Widget build(BuildContext context) { ... }
}
```

Feature pages adapt their domain models to these props via clean extensions or local factory methods:

```dart
// lib/features/home/adapters/home_media_adapters.dart
extension HomeMediaAdapter on Media {
  Widget toPosterCard({
    required VoidCallback onTap,
    required Future<Uint8List?> Function(SourceMediaRef)? artworkLoader,
  }) {
    return MediaPosterCard(
      title: title,
      subtitle: subtitleMetadata,
      badgeText: type.label,
      progressRatio: progress?.ratio,
      artwork: MediaArtworkThumbnail(ref: artworkRef, loader: artworkLoader),
      onTap: onTap,
    );
  }
}
```

---

## 6. Implementation & Migration Phases

### Phase 1: Core UI Foundation Construction (Parallel)
1. Establish `lib/core/ui/tokens/aspect_ratios.dart` and `theme_extensions.dart`.
2. Implement pure primitives:
   - `buttons/hikari_segmented_control.dart`
   - `indicators/hikari_badge.dart`
   - verify/polish `buttons/hikari_button.dart` and `inputs/hikari_search_bar.dart`.
3. Implement pure composites:
   - `media_card/media_poster_card.dart`
   - `media_card/media_landscape_card.dart`
   - `chapter_item/chapter_row.dart`
   - `section/hikari_section_header.dart`
4. Implement pure layouts:
   - `layouts/adaptive_grid.dart`
   - `layouts/horizontal_shelf.dart`
5. Create comprehensive widget test suite for each new component.

### Phase 2: Core Screen Migration (Home & Library)
1. **HomePage:** Refactor `ContinueShelf`, `HomeRecentShelf`, and discovery sections to use `HorizontalShelf` and `MediaPosterCard`. Eliminate all local `TextPainter` and magic width calculations.
2. **LibraryPage & LocalMediaPage:** Replace hardcoded grid delegates with `AdaptiveGrid` and `MediaPosterCard`.

### Phase 3: Monolithic Page Decomposition (Detail & Plugins)
1. **CatalogDetailPage:** Decompose the 847-line monster into:
   - `widgets/catalog_detail_header.dart` (backdrop, artwork, metadata, primary action)
   - `widgets/catalog_source_selection_sheet.dart` (source selection logic & sheet)
   - `widgets/catalog_chapter_section.dart` (chapter list using standardized `ChapterRow`)
   - Ensure `catalog_detail_page.dart` acts solely as coordinator (<200 lines).
2. **ApkActionsPage:** Clean up state sprawl by moving async operation state into its ViewModel, keeping the UI declarative with standard cards and buttons.

### Phase 4: Deprecation, Cleanup & Guardrail Hardening
1. Remove deprecated patterns in `lib/core/ui/patterns/`.
2. Update `test/architecture_guard_test.dart` to strictly forbid `core/ui/primitives` and `core/ui/composites` from importing any domain/application/infrastructure code.
3. Run full project test suite (`flutter test`) and analysis (`flutter analyze`) to guarantee zero regressions.

---

## 7. Verification & Success Criteria

- **Zero Analyzer Errors:** `fvm flutter analyze --no-pub` completes cleanly.
- **Architecture Guard Pass:** `test/architecture_guard_test.dart` enforces no leakage of domain models into `core/ui/primitives` and `core/ui/composites`.
- **Zero Magic Text Calculations:** Grep for `TextPainter` across `lib/features/` returns 0 occurrences for layout sizing.
- **Zero Regressions:** All existing widget and unit tests pass cleanly.
- **Reduced Line Count:** Major pages (`catalog_detail_page.dart`, `apk_actions_page.dart`, `home_page.dart`) reduced significantly in size and complexity.

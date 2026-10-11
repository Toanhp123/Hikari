# Detail Screen Hardening & Shared Metadata Patterns (Modules 2 & 3) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Upgrade Hikari's Catalog Detail screen and shared MediaMetadataView component to adhere to Material 3 design system standards, resilient text scaling (up to 200%), and adaptive layout constraints.

**Architecture:** Refactor `CatalogDetailHero`, `CatalogDetailContent`, and `CatalogDetailSections` to consume native Material 3 actions (`FilledButton.icon`), enforce local `LayoutBuilder` constraints over global media queries, and overhaul `MediaMetadataView` into a polished, responsive, accessible pattern component.

**Tech Stack:** Flutter 3.44.x, Dart 3.x, Material 3, Hikari Design System (`HikariTheme`, `HikariSpacing`, `HikariRadius`, `HikariBreakpoints`).

**Spec:** [docs/roadmap/UI_UX_COMPLETION.md](file:///f:/Project/SideProject/hikari/docs/roadmap/UI_UX_COMPLETION.md) Pass 3 (Details) & [docs/ui-ux/MIGRATION_PLAN.md](file:///f:/Project/SideProject/hikari/docs/ui-ux/MIGRATION_PLAN.md) Phase 2.

## Global Constraints

- Do not break existing Catalog or Source domain boundaries (`CatalogEntry` metadata remains distinct from `SourceMediaRef`).
- Use `ColorScheme` and `TextTheme` semantics; avoid raw color literals or unverified legacy fallbacks.
- Actions must use native Material buttons (`FilledButton`, `OutlinedButton`, `IconButton`) instead of custom button wrappers where applicable.
- Layout must be text-scaling resilient (tested at 1.0x and 2.0x without clipping or uncaught layout exceptions).
- Keep changes reviewable and bisectable; zero analyzer warnings (`fvm flutter analyze`).

## Review Focus

1. Text scale 200% on `CatalogDetailHero`: Header content must not overflow the flexible space bounds or crash the `CustomScrollView`.
2. Native action button accessibility: `FilledButton.icon` in hero must have explicit semantics and minimum 48dp touch target height.
3. Collapsible summary in `MediaMetadataView`: Toggling "Show more" / "Show less" must update state predictably without layout jumping.
4. Empty/partial metadata in `MediaMetadataView`: Missing summary, authors, tags, or rating must render gracefully without empty gaps or null errors.
5. Contrast on status badges & chip tags: Background and foreground pairings in light and dark/OLED modes must meet WCAG contrast thresholds.

---

### Task 1: Module 3 — Overhaul `MediaMetadataView` & Shared Presentation Patterns

**Files:**
- Modify: `lib/core/ui/patterns/media_metadata_view.dart`
- Create: `test/core/ui/patterns/media_metadata_view_test.dart`

**Interfaces:**
- Consumes:
  - `MediaMetadata` (`lib/domain/media/metadata.dart`)
  - `SourceMediaRef` (`lib/domain/media/media.dart`)
  - `HikariSpacing`, `HikariRadius` (`lib/app/theme/hikari_theme.dart`)
- Produces:
  - `MediaMetadataView`: Polished, responsive, accessible widget rendering cover artwork, source chip, status & rating badges, authors/artists, collapsible summary, and genre/tag chips.

- [ ] **Step 1: Write the widget tests for `MediaMetadataView`**

Create `test/core/ui/patterns/media_metadata_view_test.dart` covering:
- Rendering minimal metadata (only sourceName, all optional fields null/empty).
- Rendering rich metadata (authors, artists, summary, tags, genres, status, rating, publisher).
- Expanding/collapsing long summary with "Show more" / "Show less".
- Adaptive layout when width is wide (> 600) vs compact (<= 600).

- [ ] **Step 2: Run test to verify it fails or exposes gaps**

Run: `fvm flutter test test/core/ui/patterns/media_metadata_view_test.dart`

- [ ] **Step 3: Implement enhanced `MediaMetadataView`**

In `lib/core/ui/patterns/media_metadata_view.dart`:
- Convert or augment with a stateful wrapper to handle summary expansion (`_isSummaryExpanded`).
- Style `SourceArtwork` with rounded corners (`HikariRadius.borderMd`), outline border, and aspect ratio container.
- Build clean metadata badges:
  - Status badge with color-coded chip based on `PublicationStatus` (Ongoing, Completed, Hiatus, etc.).
  - Rating badge with star icon and formatted text.
  - Source badge displaying `sourceName`.
- Build tag chips:
  - Render genres and tags using a wrap of capsule-bordered chips with `Theme.of(context).colorScheme.surfaceContainer` and `outlineVariant`.
- Style authors and artists with distinct label and value typography.
- Use `LayoutBuilder` to adapt layout:
  - When maxWidth >= 600: place artwork on the left and primary metadata/summary on the right.
  - When maxWidth < 600: stack cleanly.

- [ ] **Step 4: Run test to verify it passes**

Run: `fvm flutter test test/core/ui/patterns/media_metadata_view_test.dart`
Expected: ALL PASS.

- [ ] **Step 5: Verify existing remote manga and novel series tests still pass**

Run: `fvm flutter test test/` (or targeted feature tests).

---

### Task 2: Module 2 — Standardize `CatalogDetailHero` Action Controls & Text Scaling Resilience

**Files:**
- Modify: `lib/features/catalog/widgets/catalog_detail_hero.dart`
- Modify: `lib/features/catalog/catalog_detail_page.dart`
- Modify: `test/features/catalog/catalog_detail_page_test.dart`

**Interfaces:**
- Consumes:
  - `CatalogEntry`, `CatalogEntryDetails` (`lib/domain/catalog/catalog.dart`)
  - Material 3 `FilledButton.icon`
  - `HikariSpacing`, `HikariRadius`, `HikariBreakpoints` (`lib/app/theme/hikari_theme.dart`)
- Produces:
  - Resilient `CatalogDetailHero` and `CatalogDetailPage` flexible space bar.

- [ ] **Step 1: Write failing widget test for 200% text scale in `catalog_detail_page_test.dart`**

Add a test in `test/features/catalog/catalog_detail_page_test.dart`:
```dart
testWidgets('catalog detail hero layout remains resilient at 200% text scale', (tester) async {
  tester.view.physicalSize = const Size(390, 844);
  tester.view.devicePixelRatio = 1.0;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);

  await tester.pumpWidget(
    MediaQuery(
      data: const MediaQueryData(textScaler: TextScaler.linear(2.0)),
      child: _app(
        CatalogDetailPage(
          initialEntry: _anime,
          loadDetails: LoadCatalogEntryDetails(_Provider()),
          resolveCatalogSource: _resolver(),
          openMedia: (_, _) async {},
          openRelated: (_) {},
          openSourceSearch: (_, _, _, _, _) {},
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
  expect(tester.takeException(), isNull);
  expect(find.text('Watch'), findsOneWidget);
});
```

- [ ] **Step 2: Run test to verify behavior**

Run: `fvm flutter test test/features/catalog/catalog_detail_page_test.dart`

- [ ] **Step 3: Implement Material 3 native action button and layout resilience in `CatalogDetailHero`**

In `lib/features/catalog/widgets/catalog_detail_hero.dart`:
- Replace `HikariButton` with native Material 3 `FilledButton.icon`:
  ```dart
  FilledButton.icon(
    onPressed: onPrimaryAction,
    icon: Icon(
      entry.type == MediaType.anime
          ? Icons.play_arrow_rounded
          : Icons.menu_book_rounded,
      size: 20,
    ),
    label: Text(actionLabel),
    style: FilledButton.styleFrom(
      minimumSize: const Size(120, 48),
      shape: RoundedRectangleBorder(borderRadius: HikariRadius.borderMd),
    ),
  )
  ```
- Replace `context.hikariStatusColors.onWarningContainer` star color in score row with `colors.primary` or high-contrast amber tone.
- Protect against overflow when text is scaled:
  - Make `CatalogDetailHero` layout content inside a `LayoutBuilder` that adjusts cover sizing and spacing dynamically.
  - In `catalog_detail_page.dart`, ensure `heroHeight` calculation provides adequate headroom for 2.0x text scale (`(context.isCompact ? 440.0 : 400.0) + (textScale - 1.0).clamp(0.0, 1.0) * 160.0`).

- [ ] **Step 4: Run tests to verify all pass**

Run: `fvm flutter test test/features/catalog/catalog_detail_page_test.dart`
Expected: ALL PASS.

---

### Task 3: Module 2 — Refine `CatalogDetailContent` & `CatalogDetailSections` Theme and Responsive Alignment

**Files:**
- Modify: `lib/features/catalog/widgets/catalog_detail_content.dart`
- Modify: `lib/features/catalog/widgets/catalog_detail_sections.dart`
- Modify: `test/features/catalog/catalog_detail_page_test.dart`

**Interfaces:**
- Consumes:
  - Material 3 `Theme.of(context).colorScheme`
  - `HikariSpacing`, `HikariRadius`
- Produces:
  - Cohesive sections (`_FactTile`, `_StaticTag`, `_InfoSurface`, `CatalogDetailNotice`) with consistent padding, borders, and typography.

- [ ] **Step 1: Write test for wide screen 2-column layout and notice surfaces**

Ensure tests in `test/features/catalog/catalog_detail_page_test.dart` assert supporting pane structure and notice semantics.

- [ ] **Step 2: Update sections to use semantic tokens and clean borders**

In `lib/features/catalog/widgets/catalog_detail_sections.dart`:
- In `_FactTile`: ensure minHeight and padding accommodate 2-line values with large fonts; use `colors.surfaceContainer` and `colors.outlineVariant`.
- In `_StaticTag`: use `HikariRadius.pill` and `Theme.of(context).colorScheme.surfaceContainerHigh` with clear outline.
- In `_InfoSurface`: clean up border color defaulting to `colors.outlineVariant.withValues(alpha: 0.5)`.
- In `CatalogDetailNotice` and `CatalogDetailWarning`: ensure warningContainer and onWarningContainer pairing provides >= 4.5:1 contrast.

- [ ] **Step 3: Run catalog detail tests to verify**

Run: `fvm flutter test test/features/catalog/catalog_detail_page_test.dart`
Expected: ALL PASS.

---

### Task 4: Full Suite Verification & Regression Check

**Files:**
- Entire codebase

- [ ] **Step 1: Run static analysis**

Run: `fvm flutter analyze`
Expected: "No issues found!"

- [ ] **Step 2: Run complete test suite**

Run: `fvm flutter test`
Expected: All unit and widget tests pass.

- [ ] **Step 3: Run project check script**

Run: `.\tool\check.ps1`
Expected: Pass.

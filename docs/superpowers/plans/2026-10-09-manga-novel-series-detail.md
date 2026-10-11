# Manga & Novel Series Detail Pages (Module 1) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Overhaul Manga and Novel Series Detail Pages into modern, polished reading hubs featuring a primary resume/start CTA button, responsive chapter sorting and filtering controls, enhanced chapter tiles, and adaptive desktop/tablet layout constraints.

**Architecture:** Create reusable shared patterns (`ChapterControlBar`, `PrimaryReadingCta`, `ChapterTile`) in `lib/core/ui/patterns/chapter_list_patterns.dart`. Modernize `MangaSeriesPage`/`MangaSeriesContent` and `NovelSeriesPage`/`NovelSeriesContent` into responsive, stateful hubs that support natural vs reversed sort order, instant title/number search filtering, and prominent single-tap reading starts, while strictly preserving domain contracts and existing test assertions.

**Tech Stack:** Flutter 3.44.x, Dart 3.x, Material 3, Hikari Design System (`HikariTheme`, `HikariSpacing`, `HikariRadius`, `HikariBreakpoints`).

**Spec:** [docs/roadmap/UI_UX_COMPLETION.md](file:///f:/Project/SideProject/hikari/docs/roadmap/UI_UX_COMPLETION.md) Pass 3 (Details) & [docs/ui-ux/MIGRATION_PLAN.md](file:///f:/Project/SideProject/hikari/docs/ui-ux/MIGRATION_PLAN.md) Phase 2.

## Global Constraints

- Do not alter or break provider-neutral domain contracts (`Media`, `MangaChapter`, `NovelChapter`, `SourceMediaRef`, `MangaSeriesDetails`, `NovelDetails`).
- Initial display order of chapters must preserve source order by default so existing expectations and tests remain 100% compatible.
- Primary CTA label must be generic ("Start reading") so it never collides with specific chapter titles in test finders (`find.text('Chapter A')`).
- Chapter subtitle formatting strings must preserve exact segments (`chapter.scanlator ?? sourceName`, `Chapter ${chapter.chapterNumber}`, date format, `Not readable in Hikari`, joined by `' · '`) to satisfy existing integration tests.
- Maximum content width on wide viewports must be constrained to `HikariBreakpoints.maxContentWidth` for reading ergonomics.
- Zero analyzer warnings (`fvm flutter analyze`) and full pass on all test suites (`fvm flutter test`).

## Review Focus

1. **Chapter title collision with CTA button:** CTA label must be `"Start reading"`, not containing chapter titles, preventing `findsOneWidget` test ambiguity.
2. **Preserving initial chapter order:** The default sorting mode must reflect the exact order returned by `details.chapters` before any user toggle.
3. **Filter query empty state vs source empty state:** If source has no chapters, show `"No readable chapters found."`. If chapters exist but filtering returns 0 matches, show `"No chapters matching \"$query\""` with a `"Clear filter"` action.
4. **Disabled / Unreadable tap prevention:** When `openingChapter` is true or `canReadPages` is false, tapping chapter tiles or the CTA must not invoke `openChapter`.
5. **Responsive wide-screen layout:** When viewed on tablets/desktop, content must center and constrain to `HikariBreakpoints.maxContentWidth` without breaking `RefreshIndicator` or `ListView` scrolling.

---

### Task 1: Shared Chapter List Patterns (`ChapterControlBar`, `PrimaryReadingCta`)

**Files:**
- Create: `lib/core/ui/patterns/chapter_list_patterns.dart`
- Create: `test/core/ui/patterns/chapter_list_patterns_test.dart`

**Interfaces:**
- Consumes:
  - `HikariSpacing`, `HikariRadius`, `HikariBreakpoints` (`lib/app/theme/hikari_theme.dart`)
  - Material 3 `FilledButton.icon`, `IconButton`, `TextField`
- Produces:
  - `ChapterControlBar`: Row toolbar containing chapter count badge, sort order toggle button, and search/filter toggle/input.
  - `PrimaryReadingCta`: Prominent `FilledButton.icon` with play icon, "Start reading" label, full-width / padded layout.

- [x] **Step 1: Write widget tests for `ChapterControlBar` and `PrimaryReadingCta`**
...
- [x] **Step 2: Run test to verify it fails**
...
- [x] **Step 3: Implement `lib/core/ui/patterns/chapter_list_patterns.dart`**
...
- [x] **Step 4: Run test to verify it passes**
...
- [x] **Step 5: Commit**

```bash
git add lib/core/ui/patterns/chapter_list_patterns.dart test/core/ui/patterns/chapter_list_patterns_test.dart
git commit -m "feat(ui): add ChapterControlBar and PrimaryReadingCta patterns"
```

---

### Task 2: Overhaul Manga Series Detail Page (`MangaSeriesPage` & `MangaSeriesContent`)

**Files:**
- Modify: `lib/features/remote_manga/widgets/manga_series_content.dart`
- Modify: `lib/features/remote_manga/manga_series_page.dart`
- Create: `test/features/remote_manga/manga_series_page_test.dart`

**Interfaces:**
- Consumes:
  - `ChapterControlBar`, `PrimaryReadingCta` (`lib/core/ui/patterns/chapter_list_patterns.dart`)
  - `MangaSeriesDetails`, `MangaChapter` (`lib/domain/media/manga.dart`)
  - `MangaSeriesUiState` (`lib/features/remote_manga/manga_series_view_model.dart`)
  - `MediaMetadataView` (`lib/core/ui/patterns/media_metadata_view.dart`)
- Produces:
  - Enhanced `MangaSeriesPage` with responsive centering and smooth header integration.
  - Enhanced `MangaSeriesContent` with sorting, filtering, primary start reading CTA, and polished chapter tiles.

- [x] **Step 1: Write widget tests for `MangaSeriesPage` and `MangaSeriesContent`**
...
- [x] **Step 2: Run test to verify it fails**
...
- [x] **Step 3: Update `MangaSeriesContent` and `MangaSeriesPage`**
...
- [x] **Step 4: Run new and existing manga tests to verify they pass**
...
- [x] **Step 5: Commit**

```bash
git add lib/features/remote_manga/ test/features/remote_manga/
git commit -m "feat(manga): modernize manga series page with CTA, sorting, and filtering"
```

---

### Task 3: Overhaul Novel Series Detail Page (`NovelSeriesPage` & `NovelSeriesContent`)

**Files:**
- Modify: `lib/features/remote_novel/widgets/novel_series_content.dart`
- Modify: `lib/features/remote_novel/novel_series_page.dart`
- Create: `test/features/remote_novel/novel_series_page_test.dart`

**Interfaces:**
- Consumes:
  - `ChapterControlBar`, `PrimaryReadingCta` (`lib/core/ui/patterns/chapter_list_patterns.dart`)
  - `NovelDetails`, `NovelChapter` (`lib/domain/media/novel.dart`)
  - `NovelSeriesUiState` (`lib/features/remote_novel/novel_series_view_model.dart`)
  - `MediaMetadataView` (`lib/core/ui/patterns/media_metadata_view.dart`)
- Produces:
  - Enhanced `NovelSeriesPage` with responsive centering and layout consistency.
  - Enhanced `NovelSeriesContent` with sorting, filtering, primary start reading CTA, and polished chapter tiles.

- [x] **Step 1: Write widget tests for `NovelSeriesPage` and `NovelSeriesContent`**
...
- [x] **Step 2: Run test to verify it fails**
...
- [x] **Step 3: Update `NovelSeriesContent` and `NovelSeriesPage`**
...
- [x] **Step 4: Run new and existing novel tests to verify they pass**
...
- [x] **Step 5: Commit**

```bash
git add lib/features/remote_novel/ test/features/remote_novel/
git commit -m "feat(novel): modernize novel series page with CTA, sorting, and filtering"
```

---

### Task 4: Whole-Repository Quality & Regression Verification

**Files:**
- Repository-wide verification.

- [ ] **Step 1: Run static analysis**

Run: `fvm flutter analyze`
Expected: Zero warnings / issues.

- [ ] **Step 2: Run the full test suite**

Run: `fvm flutter test`
Expected: All tests pass.

- [ ] **Step 3: Run repository quality checks**

Run: `powershell -ExecutionPolicy Bypass -File .\tool\check.ps1`
Expected: 0 warnings, formatting compliant, all suites pass.

- [ ] **Step 4: Commit any remaining refinements**

```bash
git commit -m "chore: verify series detail page modernization passes all checks"
```


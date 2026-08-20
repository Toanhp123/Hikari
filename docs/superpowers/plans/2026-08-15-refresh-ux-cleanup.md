# Refresh UX Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close the remaining pull-to-refresh UX drift by making the indicator safe-area aware, proving real gesture behavior, scoping Story refresh failures correctly, normalizing Story section insets, removing dead refresh-button API, and updating visual/docs evidence.

**Architecture:** Keep `HikariPullToRefresh` as the only pull-to-refresh owner in `core:designsystem`. Feature screens provide refresh state/callbacks and layout insets; Story owns a shared section-padding helper so Overview, Chapters, and Sources use one outer inset contract. Source-detail failures remain in state but are only rendered in source-detail sections.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose UI tests, Robolectric/Roborazzi, shell policy contracts.

## Global Constraints

- Pull-to-refresh remains enabled only for Discover, Story Overview, and Story Sources.
- Story Chapters must not expose pull-to-refresh until a chapter-sync pipeline exists.
- Retry remains visible for retryable error recovery.
- Feature code must not use Material `PullToRefreshBox` directly.
- All spacing and UI geometry must come from Hikari tokens or runtime insets.
- Large-screen/two-pane Story behavior must keep only the selected content pane refreshable.

---

### Task 1: Safe-area-aware refresh container

**Files:**
- Modify: `core/designsystem/src/main/kotlin/app/openstory/designsystem/refresh/HikariPullToRefresh.kt`
- Modify: `core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariDestinationScaffold.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt`
- Test: `core/designsystem/src/test/kotlin/app/openstory/designsystem/HikariProductPrimitivesTest.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverSemanticsTest.kt`

**Interfaces:**
- `HikariPullToRefresh(..., topInset: Dp = HikariDefaultDimensions.zero, ...)`
- `PaddingValues.withTop(top: Dp): PaddingValues`

- [x] Add failing policy/test coverage requiring the shared refresh owner to accept a top inset and Discover to consume safe top padding once.
- [x] Verify the new contract fails on the current implementation.
- [x] Add `topInset` to `HikariPullToRefresh` and apply it to the whole refresh region, not only the indicator.
- [x] Add `PaddingValues.withTop` so Discover can remove the already-consumed top inset from its list padding.
- [x] Pass `contentPadding.calculateTopPadding()` to the refresh container and set the LazyColumn top padding to zero while preserving start/end/bottom padding.
- [x] Add a Compose test that verifies a refreshing indicator/refresh region begins below a supplied top inset.

### Task 2: Real pull gesture coverage

**Files:**
- Modify: `core/designsystem/src/test/kotlin/app/openstory/designsystem/HikariProductPrimitivesTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverSemanticsTest.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryScreenshotTest.kt`

**Interfaces:**
- Existing `HikariPullToRefresh` gesture contract.

- [x] Add `performTouchInput { swipeDown() }` coverage to the shared primitive and assert one refresh callback.
- [x] Add integration gesture coverage for Discover.
- [x] Add integration gesture coverage for Story Overview and Story Sources.
- [x] Keep the existing Chapters negative test proving no refresh container exists there.

### Task 3: Scope Story refresh failures to source-detail sections

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt`
- Test: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryScreenshotTest.kt`

**Interfaces:**
- `StorySection.showsSourceDetailFailure(): Boolean`

- [x] Add a failing test where Chapters has a `StoryRefreshFailure` but does not render the source-detail retry banner.
- [x] Render `StoryFailureBanner` only for Overview and Sources.
- [x] Preserve retryable empty-story error behavior.

### Task 4: Normalize Story section outer insets

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySectionLayout.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryOverview.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySources.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt`
- Test: `scripts/tests/ui-shared-component-policy-test.sh`

**Interfaces:**
- `@Composable internal fun storySectionContentPadding(): PaddingValues`
- `ChapterList(..., contentPadding: PaddingValues? = null)`
- `MappingSheet(..., contentPadding: PaddingValues? = null)`

- [x] Add policy assertions requiring Overview, Chapters, and Sources to consume the shared Story padding owner.
- [x] Move Sources horizontal inset to its `LazyColumn.contentPadding`; keep only vertical item-local padding in `SourceCard`.
- [x] Allow embedded `MappingSheet` to inherit the Story horizontal inset while retaining its internal vertical section spacing and standalone default padding.
- [x] Pass the shared Story padding to ChapterList from StoryScreen.

### Task 5: Remove dead refresh-button API and stale guardrails

**Files:**
- Modify: `core/designsystem/src/main/kotlin/app/openstory/designsystem/icon/HikariGlyphs.kt`
- Modify: `core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariGlyphGeometry.kt`
- Modify: `core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariTypography.kt`
- Modify: `scripts/tests/ui-shared-component-policy-test.sh`

**Interfaces:**
- No replacement API; pull-to-refresh indicator is the refresh affordance.

- [x] Add a policy assertion that the obsolete `HikariRefreshGlyph` is absent from production design-system code.
- [x] Remove `HikariRefreshGlyph`, `HikariRefreshGlyphGeometry`, `HikariGlyphGeometry.refresh`, and semantic `refreshAction` typography.
- [x] Remove the stale policy requirement that forced the glyph to remain.

### Task 6: Visual and documentation evidence

**Files:**
- Regenerate: `feature/catalog/src/test/snapshots/story/compact-sources.png` and `cached-error.png` via Roborazzi recording after review.
- Modify: `docs/superpowers/plans/2026-08-15-pull-to-refresh-ux-system.md`
- Modify: `docs/internal/checkpoints/product-ui-redesign.md`
- Modify: `docs/ui/design-system.md`

**Interfaces:**
- Current Roborazzi Story/Discover snapshot contracts.

- [ ] Run focused unit tests and `compareRoborazziDebug`.
- [ ] Review expected Story/Discover diffs, then run `recordRoborazziDebug` and `verifyRoborazziDebug`.
- [x] Mark the original pull-to-refresh plan completed where implementation is now verified.
- [x] Record the cleanup acceptance commands and safe-area/gesture/failure-scope contracts in the UI checkpoint/design-system docs.
- [ ] Run `./scripts/verify.sh` and `git diff --check` (or equivalent patch whitespace verification in the archive workspace).
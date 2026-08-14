# Pull-to-Refresh UX System Design

## Goal

Replace space-consuming manual refresh controls with one consistent pull-to-refresh UX owned by the Hikari design system, while preserving visible Retry actions for error recovery and only enabling the gesture where a real refresh pipeline exists.

## Scope

Pull-to-refresh is enabled on:

- Discover, using `DiscoverViewModel.refresh()`.
- Story Overview, using the selected-source detail refresh pipeline.
- Story Sources, using the same selected-source detail refresh pipeline.

Pull-to-refresh is not enabled on Story Chapters, Library, Home/Updates, Downloads, or Reader because those screens either have no matching manual refresh pipeline or use Retry for a different failure/recovery semantic.

## Design-system ownership

Add a shared `HikariPullToRefresh` primitive in `:core:designsystem`. It owns Material 3 pull gesture integration, the theme-driven refresh indicator, refresh semantics, and the accessibility action. Feature modules only provide `refreshing`, `onRefresh`, and scrollable content.

The primitive must not create scrolling itself. The child remains the existing `LazyColumn` or other scrollable layout so scroll state, responsive behavior, and two-pane layouts remain feature-owned.

The visible indicator uses Material 3 defaults inside `HikariTheme`, so its surface/content roles remain product-owned without feature-local colors, sizes, or offsets. The refresh accessibility action is exposed as `Refresh`; while a refresh is already active it must not dispatch another request.

## Discover behavior

`DiscoverScreen` wraps its existing `LazyColumn` in `HikariPullToRefresh` and removes both the bottom `Refresh sources` utility action and the duplicate full-width refreshing progress row. Cached content and partial refresh failures remain visible while refreshing, with a single visible Retry action for recoverable refresh failures. Initial loading behavior remains unchanged.

## Story behavior

Rename the normal source-detail reload API from `StoryViewModel.retry()` to `StoryViewModel.refresh()` so the data action matches its normal use. Error Retry UI calls the same refresh function.

Story Overview and Sources wrap their existing scrollable content with `HikariPullToRefresh`. Story Chapters remains a normal non-refreshable list. The Sources header loses the explicit refresh icon because refresh is now a gesture/system action.

The global `LinearProgressIndicator` above Story tabs is removed to avoid duplicate feedback. Cached Story content stays visible during refresh. If no Story content is available, retryable failures keep the existing explicit Retry path visible while pull refresh is active; a no-content refresh without an existing failure may use the existing loading state. Initial/no-content loading remains represented by the existing loading/error states rather than a hidden gesture-only path.

## Error, empty, and accessibility behavior

Pull-to-refresh remains available when a refreshable screen has empty or cached content. Retry remains visible in retryable error UI. Pull-to-refresh is a convenience path, not the only recovery path.

Every refreshable container exposes an accessibility `Refresh` action. Story Chapters must not expose that action.

## Non-goals

This change does not add chapter synchronization, Library/Home background refresh, new repository APIs, success snackbars, haptics, scheduling, or new refresh behavior for Downloads/Reader.

## Verification

Add design-system tests for refresh semantics and active-refresh guarding; feature tests for Discover/Story refresh exposure and removal of manual refresh controls; ViewModel tests for the `refresh()` API; policy checks that feature code consumes the shared primitive and does not reintroduce the removed controls. Run relevant unit tests, Roborazzi comparison/record/verify on the developer machine, then `./scripts/verify.sh`.

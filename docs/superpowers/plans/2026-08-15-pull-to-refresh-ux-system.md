# Pull-to-Refresh UX System Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace manual refresh controls with a design-system-owned pull-to-refresh interaction on Discover and Story Overview/Sources without inventing refresh behavior for Chapters or unrelated screens.

**Architecture:** `:core:designsystem` owns `HikariPullToRefresh`, including Material 3 gesture wiring, indicator styling, semantics, and duplicate-request guarding. Discover and Story consume that primitive around their existing scrollable content; Story ViewModel exposes the data operation as `refresh()` and Retry UI delegates to it.

**Tech Stack:** Kotlin, Jetpack Compose Material 3, Compose UI testing, Robolectric/Roborazzi, Detekt, shell policy tests.

## Global Constraints

- Enable pull-to-refresh only on Discover, Story Overview, and Story Sources.
- Story Chapters must not expose pull-to-refresh until a chapter-sync pipeline exists.
- Keep Retry as a visible error-recovery action.
- Do not add feature-local refresh indicators, dimensions, colors, or gesture implementations.
- Do not add new repository/storage/network behavior.
- Preserve cached content while refreshing and avoid duplicate refresh feedback.

---

### Task 1: Shared Hikari pull-to-refresh primitive

**Files:**
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/refresh/HikariPullToRefresh.kt`
- Modify: `core/designsystem/src/test/kotlin/app/openstory/designsystem/HikariProductPrimitivesTest.kt`

**Interfaces:**
- Produces: `@Composable fun HikariPullToRefresh(refreshing: Boolean, onRefresh: () -> Unit, modifier: Modifier = Modifier, content: @Composable BoxScope.() -> Unit)`.
- Semantics: custom accessibility action labeled `Refresh`; action returns false and does not dispatch while `refreshing`.

- [ ] **Step 1: Write failing design-system tests**

Add tests that render `HikariPullToRefresh`, invoke its `Refresh` semantics action, assert one callback when idle, and assert no additional callback while `refreshing = true`.

- [ ] **Step 2: Run the targeted test and confirm RED**

Run: `./gradlew :core:designsystem:testDebugUnitTest --tests app.openstory.designsystem.HikariProductPrimitivesTest --stacktrace`

Expected before implementation: compile/test failure because `HikariPullToRefresh` does not exist.

- [ ] **Step 3: Implement the primitive**

Use Material 3 `PullToRefreshBox` + `rememberPullToRefreshState`; use `PullToRefreshDefaults.Indicator` aligned top-center with Hikari theme colors. Wrap `onRefresh` with `if (!refreshing) onRefresh()`. Add the `Refresh` custom accessibility action to the container semantics with the same guard.

- [ ] **Step 4: Re-run the targeted tests and confirm GREEN**

Run the command from Step 2 and expect success.

### Task 2: Discover migration

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt`
- Modify: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/discover/DiscoverScreenTest.kt`

**Interfaces:**
- Consumes: `HikariPullToRefresh(refreshing = state.refreshing, onRefresh = onRefresh)`.
- Removes: `RefreshAction`, `discover-refresh-action`, and the duplicate `discover-refreshing` linear progress item.

- [ ] **Step 1: Write failing Discover tests**

Assert the screen exposes a `Refresh` semantics action, invoking it calls `onRefresh`, `Refresh sources` is absent, and cached content + partial failure stay visible when `refreshing = true`.

- [ ] **Step 2: Run Discover screen tests and confirm RED**

Run: `./gradlew :feature:catalog:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.ui.discover.DiscoverScreenTest --stacktrace` or the project-equivalent Android test task available locally.

- [ ] **Step 3: Wrap the existing LazyColumn and remove manual/duplicate controls**

Keep all existing content padding, arrangements, focus behavior, shelves, and feedback items. Only the refresh container/chrome changes.

- [ ] **Step 4: Re-run the relevant tests and confirm GREEN**

Run the same test task.

### Task 3: Story refresh contract and section migration

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryOverview.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySources.kt`
- Modify: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/story/StoryScreenTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`

**Interfaces:**
- Rename `StoryViewModel.retry()` to `StoryViewModel.refresh()`.
- `StoryScreen` consumes `onRefresh: () -> Unit`; visible Retry actions call that callback.
- `StoryOverview` receives `refreshing` and `onRefresh` and owns a refresh wrapper around its `LazyColumn`.
- `StorySources` receives `refreshing` and `onRefresh`, owns a refresh wrapper around its `LazyColumn`, and removes the header refresh icon.
- Chapters receive no refresh callback and expose no refresh semantics.

- [ ] **Step 1: Write failing ViewModel and Story UI tests**

Rename ViewModel tests to call `refresh()`. Add Story tests proving Overview and Sources expose `Refresh`, Sources no longer has `story-source-refresh`, Chapters exposes no `Refresh`, Retry remains visible/actionable, and cached failure content remains visible while refreshing.

- [ ] **Step 2: Run targeted Story tests and confirm RED**

Run: `./gradlew :feature:catalog:testDebugUnitTest --tests app.openstory.catalog.ui.story.StoryViewModelTest --stacktrace` plus the Story Android UI test task.

- [ ] **Step 3: Implement the minimal migration**

Rename the ViewModel function, route AppNavHost/StoryScreen callbacks to `refresh`, remove the top `LinearProgressIndicator`, wrap only Overview and Sources in `HikariPullToRefresh`, and remove the Sources refresh icon/action imports and parameters.

- [ ] **Step 4: Re-run targeted tests and confirm GREEN**

Run the commands from Step 2.

### Task 4: Guardrails and verification

**Files:**
- Modify: `scripts/tests/ui-shared-component-policy-test.sh`
- Modify: `docs/internal/checkpoints/product-ui-redesign.md` if the current checkpoint tracks accepted UX changes.

**Interfaces:**
- Policy rejects direct `PullToRefreshBox`/`pullToRefresh` use outside design system.
- Policy rejects `Refresh sources`, `story-source-refresh`, and Story Source refresh icon ownership.
- Policy requires Discover, Story Overview, and Story Sources to consume `HikariPullToRefresh` and forbids it in Chapter list production UI.

- [ ] **Step 1: Add failing policy assertions and confirm RED**

Run: `./scripts/tests/ui-shared-component-policy-test.sh` and confirm it fails against pre-migration production code.

- [ ] **Step 2: Make production usage satisfy the contract**

Update only policy/documentation needed for the approved behavior; do not add suppression allowances.

- [ ] **Step 3: Run static gates**

Run: `./scripts/tests/ui-shared-component-policy-test.sh`, `./scripts/tests/ui-token-policy-test.sh`, `./scripts/verify-package-boundaries.sh`, `./scripts/verify-source-layout.sh`, and `git diff --check` when in a git checkout.

- [ ] **Step 4: Run developer-machine visual/full verification**

Run:

```bash
./gradlew :core:designsystem:testDebugUnitTest :feature:catalog:testDebugUnitTest --stacktrace
./gradlew compareRoborazziDebug --stacktrace
./gradlew recordRoborazziDebug --stacktrace
./gradlew verifyRoborazziDebug --stacktrace
./scripts/verify.sh
```

Review visual diffs before recording. Expected visual changes are removal of explicit refresh controls/progress bars and appearance of the pull indicator only in active/pulled refresh states.

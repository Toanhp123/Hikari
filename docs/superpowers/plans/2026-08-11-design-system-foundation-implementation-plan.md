# Hikari Design System Foundation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add `:core:designsystem` as Hikari's application-wide UI foundation, centralize theme/tokens, standardize domain-neutral loading/empty/error/offline/feedback/confirmation UX, and migrate existing presentation code onto the foundation without redesigning screens or changing product flows.

**Architecture:** `:core:designsystem` is a leaf Android/Compose UI module with no project dependencies. `:app`, `:feature:catalog`, and `:feature:reader` may depend on it; capability/storage/plugin modules may not. Feature code maps domain/application state into generic UX semantics; the design system renders those semantics but never imports Story, Library, Reader, plugin, persistence, network, or other capability models.

**Tech Stack:** Kotlin 2.4.10, Android Gradle Plugin via OpenStory convention plugins, JDK 17, compile/target SDK 37, min SDK 26, Jetpack Compose with Compose BOM `2026.06.00`, Material 3, Navigation 3, Gradle architecture verifier, Detekt, AndroidX Compose UI tests.

## Global Constraints

- Source specification: `docs/superpowers/specs/2026-08-11-design-system-architecture-design.md`.
- This work happens after the verified Wave 09 boundary and before Wave 10 implementation.
- Add exactly one production module in this foundation change: `:core:designsystem`.
- `:core:designsystem` starts with **zero direct project dependencies**.
- `:core:designsystem` may use Android, Compose, Material 3, and presentation-only tooling libraries.
- Capability modules must not depend on `:core:designsystem`.
- `:app`, `:feature:catalog`, and `:feature:reader` may depend on `:core:designsystem` when they consume it.
- The design system must not import domain/capability types, repository errors, persistence types, plugin runtime types, WorkManager, Navigation routes, Hilt ViewModels, or app workers.
- Do not create wrapper components merely to add a Hikari prefix. Keep direct use of `Row`, `Column`, `Box`, `Text`, `Button`, `FilterChip`, `LazyColumn`, and other Material/Compose primitives when no stable Hikari-wide rule exists.
- Required first-class token groups: color roles, typography, shapes, spacing.
- Loading, empty, error, offline, snackbar feedback, confirmation, and destructive-confirmation presentation must be standardized as domain-neutral UX.
- Compose snackbar is the default in-app transient-feedback surface. Android `Toast` is not banned globally, but any use must have a platform/lifecycle reason rather than duplicate active-surface feedback.
- Domain/application failures must be mapped by the owning feature before reaching the design system.
- Preserve existing screen structure, information hierarchy, navigation behavior, feature actions, state flow, and domain ownership.
- No Home, Search, Story, Library, Mapping, Chapter, Reader, Settings, or Plugins redesign is approved by this plan.
- Visual differences are acceptable only when caused by shared theme/token values, shared UX-state presentation, accessibility defaults, or removal of accidental inconsistency.
- Do not add speculative elevation, motion, responsive-breakpoint, icon, image, or component systems unless an existing repeated rule requires them during implementation.
- Keep Room schema untouched.
- Keep plugin protocol/runtime behavior untouched.
- Do not introduce new third-party dependencies unless the existing Compose/Material/Test stack cannot satisfy a concrete requirement.
- Architecture policy remains exact/fail-closed: every direct project dependency in Gradle must match `config/architecture/module-boundaries.json`.
- Run JDK 17 only.

---

## File Structure

The implementation should converge on this structure. Do not add files that have no responsibility listed here.

```text
core/designsystem/
├── build.gradle.kts
└── src/
    ├── main/kotlin/app/openstory/designsystem/
    │   ├── theme/
    │   │   ├── HikariTheme.kt
    │   │   ├── HikariColorScheme.kt
    │   │   ├── HikariTypography.kt
    │   │   ├── HikariShapes.kt
    │   │   └── HikariSpacing.kt
    │   ├── state/
    │   │   ├── HikariLoadingState.kt
    │   │   ├── HikariEmptyState.kt
    │   │   ├── HikariErrorState.kt
    │   │   └── HikariOfflineState.kt
    │   └── feedback/
    │       ├── HikariSnackbarHost.kt
    │       └── HikariConfirmDialog.kt
    └── androidTest/kotlin/app/openstory/designsystem/
        ├── HikariThemeTest.kt
        ├── HikariStateComponentsTest.kt
        └── HikariFeedbackComponentsTest.kt
```

Responsibilities:

- `HikariTheme.kt`: root Material 3 composition and CompositionLocal provisioning only.
- `HikariColorScheme.kt`: initial light/dark Material 3 color schemes; preserve current neutral/default direction instead of inventing screen styling.
- `HikariTypography.kt`: application typography object only.
- `HikariShapes.kt`: application Material 3 shape object only.
- `HikariSpacing.kt`: stable spacing scale and `MaterialTheme.hikariSpacing` accessor.
- `state/*`: generic full/embedded state presentation with text/action slots; no domain models.
- `HikariSnackbarHost.kt`: one shared rendering host around `SnackbarHostState`; no global event bus.
- `HikariConfirmDialog.kt`: generic standard/destructive confirmation dialog; consequences remain feature-owned.
- Feature modules retain all domain-aware components such as `HomeCard`, `StorySourceCard`, `ChapterReleaseRow`, `LibraryItem`, Reader controls, and mapping UI.

The first foundation intentionally does **not** create:

```text
HikariRow
HikariColumn
HikariBox
HikariText
HikariButton
HikariChip
HikariStoryCard
HikariChapterRow
global UiError / AppError
global event bus
global navigation wrapper
```

---

### Task 1: Register the `:core:designsystem` architecture boundary

**Files:**
- Modify: `settings.gradle.kts`
- Create: `core/designsystem/build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifierTest.kt`
- Modify: `scripts/tests/verify-current-architecture-test.sh`
- Modify: `README.md`
- Modify: `docs/contributing/adding-a-module.md`

**Interfaces:**
- Consumes: existing `openstory.android.library` and `openstory.compose` convention plugins.
- Produces: Gradle project `:core:designsystem`, namespace `app.openstory.designsystem`, zero project dependencies, and an approved `:app -> :core:designsystem` edge. Later tasks add feature edges only when the feature actually consumes the module.

- [ ] **Step 1: Write the architecture test that requires the new module**

Extend `ModuleBoundaryVerifierTest.kt` with:

```kotlin
@Test
fun designSystemIsAProjectIndependentAndroidUiFoundation() {
    val policy = ModuleBoundaryPolicyLoader.load(
        File("../config/architecture/module-boundaries.json"),
    )

    val designSystem = policy.modules.getValue(":core:designsystem")

    assertEquals(ModulePlatform.ANDROID_LIBRARY, designSystem.platform)
    assertTrue(designSystem.productionDependencies.isEmpty())
    assertTrue(designSystem.testDependencies.isEmpty())
    assertTrue(
        ":core:designsystem" in
            policy.modules.getValue(":app").productionDependencies,
    )
}
```

- [ ] **Step 2: Run the focused RED test**

```bash
./gradlew :build-logic:test \
  --tests app.openstory.build.architecture.ModuleBoundaryVerifierTest.designSystemIsAProjectIndependentAndroidUiFoundation \
  --stacktrace
```

Expected: **FAIL** because `:core:designsystem` is not declared in the policy.

- [ ] **Step 3: Create the module build file**

Create `core/designsystem/build.gradle.kts`:

```kotlin
plugins {
    id("openstory.android.library")
    id("openstory.compose")
}

android {
    namespace = "app.openstory.designsystem"

    defaultConfig {
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)

    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.runner)

    debugImplementation(libs.androidx.compose.ui.test.manifest)
    debugImplementation(libs.androidx.compose.ui.tooling)
}
```

Do not add Hilt, lifecycle, Navigation, coroutines, Room, WorkManager, or project dependencies.

- [ ] **Step 4: Register the module and add only the app edge**

Add to `settings.gradle.kts`:

```kotlin
include(":core:designsystem")
```

Add to `app/build.gradle.kts`:

```kotlin
implementation(project(":core:designsystem"))
```

Do **not** add feature dependencies yet.

- [ ] **Step 5: Update the exact architecture policy**

Add:

```json
":core:designsystem": {
  "path": "core/designsystem",
  "platform": "android-library",
  "dependencyMode": "exact",
  "productionDependencies": [],
  "testDependencies": [],
  "forbiddenProductionImports": [
    "app.openstory.catalog.",
    "app.openstory.library.",
    "app.openstory.chapters.",
    "app.openstory.reader.",
    "app.openstory.downloads.",
    "app.openstory.storage.",
    "app.openstory.plugins.",
    "app.openstory.navigation.",
    "androidx.room.",
    "androidx.work."
  ]
}
```

Add `":core:designsystem"` to `:app.productionDependencies`.

- [ ] **Step 6: Update shell verifier contract to 14 modules**

In `scripts/tests/verify-current-architecture-test.sh`:

```bash
[[ "$module_count" == 14 ]] || {
  echo "UI foundation boundary must contain exactly fourteen production modules." >&2
  exit 1
}
```

Use this sorted module set:

```bash
expected_modules=$':app\n:catalog\n:chapters\n:core:common\n:core:designsystem\n:downloads\n:feature:catalog\n:feature:reader\n:library\n:plugins:api\n:plugins:runtime\n:reader\n:storage:files\n:storage:room'
```

Add `:core:designsystem` to `expected_app_dependencies` directly after `:core:common`, and replace stale wording that freezes the graph at Wave 09.

- [ ] **Step 7: Update module documentation**

Add to `README.md`:

```text
- `:core:designsystem` — application-wide Compose theme, visual tokens, and domain-neutral shared UX presentation
```

Update `docs/contributing/adding-a-module.md` so presentation modules may consume `:core:designsystem` while capability/storage/runtime boundaries stay governed by the exact policy.

- [ ] **Step 8: Run architecture GREEN checks**

```bash
./gradlew projects
./gradlew :build-logic:test \
  --tests app.openstory.build.architecture.ModuleBoundaryVerifierTest \
  --stacktrace
bash scripts/tests/verify-current-architecture-test.sh
./scripts/check-module-dependencies.sh
```

Expected: the new module is recognized and exact policy passes.

- [ ] **Step 9: Commit**

```bash
git add \
  settings.gradle.kts \
  core/designsystem/build.gradle.kts \
  app/build.gradle.kts \
  config/architecture/module-boundaries.json \
  build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifierTest.kt \
  scripts/tests/verify-current-architecture-test.sh \
  README.md \
  docs/contributing/adding-a-module.md
git commit -m "arch: add design system foundation module"
```

---

### Task 2: Implement `HikariTheme` and foundational tokens

**Files:**
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariColorScheme.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariTypography.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariShapes.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariSpacing.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariTheme.kt`
- Create: `core/designsystem/src/androidTest/kotlin/app/openstory/designsystem/HikariThemeTest.kt`

**Interfaces:**
- Produces:
  - `@Composable fun HikariTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit)`
  - `val MaterialTheme.hikariSpacing: HikariSpacing`
  - `HikariSpacing(extraSmall=4.dp, small=8.dp, medium=12.dp, large=16.dp, extraLarge=24.dp, doubleExtraLarge=32.dp)`

- [ ] **Step 1: Write RED theme tests**

Create tests that capture `MaterialTheme.hikariSpacing.large == 16.dp`, `extraLarge == 24.dp`, and prove light/dark backgrounds differ.

- [ ] **Step 2: Run RED instrumentation**

```bash
./gradlew :core:designsystem:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.designsystem.HikariThemeTest \
  --stacktrace
```

Expected: compile failure because theme APIs do not exist.

- [ ] **Step 3: Add initial color ownership without inventing a brand palette**

```kotlin
package app.openstory.designsystem.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme

internal val HikariLightColorScheme = lightColorScheme()
internal val HikariDarkColorScheme = darkColorScheme()
```

- [ ] **Step 4: Add typography and shapes**

```kotlin
internal val HikariTypography = Typography()
internal val HikariShapes = Shapes()
```

Keep them in their own files.

- [ ] **Step 5: Add spacing tokens**

```kotlin
@Immutable
data class HikariSpacing(
    val extraSmall: Dp = 4.dp,
    val small: Dp = 8.dp,
    val medium: Dp = 12.dp,
    val large: Dp = 16.dp,
    val extraLarge: Dp = 24.dp,
    val doubleExtraLarge: Dp = 32.dp,
)

internal val LocalHikariSpacing = staticCompositionLocalOf { HikariSpacing() }

val MaterialTheme.hikariSpacing: HikariSpacing
    @Composable
    @ReadOnlyComposable
    get() = LocalHikariSpacing.current
```

- [ ] **Step 6: Implement the root theme**

```kotlin
@Composable
fun HikariTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalHikariSpacing provides HikariSpacing(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) HikariDarkColorScheme else HikariLightColorScheme,
            typography = HikariTypography,
            shapes = HikariShapes,
            content = content,
        )
    }
}
```

- [ ] **Step 7: Run GREEN tests**

```bash
./gradlew :core:designsystem:assembleDebug :core:designsystem:lintDebug --stacktrace
./gradlew :core:designsystem:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.designsystem.HikariThemeTest \
  --stacktrace
```

- [ ] **Step 8: Commit**

```bash
git add core/designsystem/src
git commit -m "feat: add Hikari theme and design tokens"
```

---

### Task 3: Add shared loading, empty, error, and offline state surfaces

**Files:**
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/state/HikariLoadingState.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/state/HikariEmptyState.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/state/HikariErrorState.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/state/HikariOfflineState.kt`
- Create: `core/designsystem/src/androidTest/kotlin/app/openstory/designsystem/HikariStateComponentsTest.kt`

**Interfaces:**

```kotlin
@Composable
fun HikariLoadingState(label: String, modifier: Modifier = Modifier)

@Composable
fun HikariEmptyState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
)

@Composable
fun HikariErrorState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
)

@Composable
fun HikariOfflineState(
    title: String,
    modifier: Modifier = Modifier,
    message: String? = null,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
)
```

No global `UiState`, `UiError`, error mapper, retry policy, or domain strings.

- [ ] **Step 1: Write RED state tests**

Test:
- Loading exposes its label through semantics.
- Empty optional action invokes callback.
- Error retry invokes callback.
- Offline uses caller-provided copy/action.

- [ ] **Step 2: Run RED**

```bash
./gradlew :core:designsystem:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.designsystem.HikariStateComponentsTest \
  --stacktrace
```

Expected: compile failure.

- [ ] **Step 3: Implement loading**

Use a centered `Column`, `CircularProgressIndicator`, visible label, `mergeDescendants`, and `MaterialTheme.hikariSpacing.small`.

- [ ] **Step 4: Implement empty/error/offline with one internal helper**

Use:

```kotlin
@Composable
internal fun HikariStateContent(
    title: String,
    modifier: Modifier,
    message: String?,
    actionLabel: String?,
    onAction: (() -> Unit)?,
    titleColor: Color,
)
```

Rules:
- always render title;
- render message only if non-null;
- render action only when label and callback are both non-null;
- error title uses `MaterialTheme.colorScheme.error`;
- empty/offline remain normal content color;
- no domain icon/copy.

- [ ] **Step 5: Run GREEN**

```bash
./gradlew :core:designsystem:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.designsystem.HikariStateComponentsTest \
  --stacktrace
./gradlew :core:designsystem:assembleDebug :core:designsystem:lintDebug --stacktrace
./scripts/check-module-dependencies.sh
```

- [ ] **Step 6: Commit**

```bash
git add \
  core/designsystem/src/main/kotlin/app/openstory/designsystem/state \
  core/designsystem/src/androidTest/kotlin/app/openstory/designsystem/HikariStateComponentsTest.kt
git commit -m "feat: standardize shared UI states"
```

---

### Task 4: Add snackbar and confirmation feedback primitives

**Files:**
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/feedback/HikariSnackbarHost.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/feedback/HikariConfirmDialog.kt`
- Create: `core/designsystem/src/androidTest/kotlin/app/openstory/designsystem/HikariFeedbackComponentsTest.kt`

**Interfaces:**

```kotlin
@Composable
fun HikariSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
)

enum class HikariConfirmationStyle {
    STANDARD,
    DESTRUCTIVE,
}

@Composable
fun HikariConfirmDialog(
    title: String,
    message: String,
    confirmLabel: String,
    dismissLabel: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    style: HikariConfirmationStyle = HikariConfirmationStyle.STANDARD,
)
```

No singleton snackbar manager, global channel, feature event type, or navigation effect bus.

- [ ] **Step 1: Write RED feedback tests**

Test:
- snackbar action from `SnackbarHostState.showSnackbar` is rendered/clickable;
- confirm calls `onConfirm`;
- dismiss request/button calls `onDismiss`;
- destructive style changes only generic presentation, not callback behavior.

- [ ] **Step 2: Run RED**

```bash
./gradlew :core:designsystem:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.designsystem.HikariFeedbackComponentsTest \
  --stacktrace
```

- [ ] **Step 3: Implement snackbar as a thin renderer**

```kotlin
@Composable
fun HikariSnackbarHost(
    hostState: SnackbarHostState,
    modifier: Modifier = Modifier,
) {
    SnackbarHost(
        hostState = hostState,
        modifier = modifier,
    )
}
```

Do not add speculative severity variants.

- [ ] **Step 4: Implement generic confirmation**

Use `AlertDialog` and `TextButton`.

For `DESTRUCTIVE`, only the confirm action receives `MaterialTheme.colorScheme.error`. Caller owns copy, state, consequence, and dismissal.

- [ ] **Step 5: Run GREEN and commit**

```bash
./gradlew :core:designsystem:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.designsystem.HikariFeedbackComponentsTest \
  --stacktrace

git add \
  core/designsystem/src/main/kotlin/app/openstory/designsystem/feedback \
  core/designsystem/src/androidTest/kotlin/app/openstory/designsystem/HikariFeedbackComponentsTest.kt
git commit -m "feat: add shared feedback and confirmation UX"
```

---

### Task 5: Move the app root onto `HikariTheme` and install the shared snackbar host

**Files:**
- Modify: `app/src/main/kotlin/app/openstory/ui/OpenStoryApp.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`
- Modify: `app/src/test/kotlin/app/openstory/ArchitectureSmokeTest.kt`
- Modify: `app/src/androidTest/kotlin/app/openstory/AppLaunchSmokeTest.kt`

**Interfaces:**
- Consumes `HikariTheme`, `HikariSnackbarHost`, `SnackbarHostState`.
- Produces one application-level shared snackbar rendering surface.
- Does not change route types, destination selection, bottom navigation, or ViewModel state collection.

- [ ] **Step 1: Add RED source-contract assertion**

Using the existing style in `ArchitectureSmokeTest.kt`, assert `OpenStoryApp.kt` contains `HikariTheme`.

- [ ] **Step 2: Run RED**

```bash
./gradlew :app:testDebugUnitTest \
  --tests app.openstory.ArchitectureSmokeTest \
  --stacktrace
```

Expected: FAIL until root migration.

- [ ] **Step 3: Replace root theme**

```kotlin
HikariTheme {
    AppNavHost(
        navigator = navigator,
        modifier = modifier,
    )
}
```

Remove direct root `MaterialTheme`.

- [ ] **Step 4: Install shared snackbar host**

Inside `AppNavHost`, create:

```kotlin
val snackbarHostState = remember { SnackbarHostState() }
```

Then add this argument to the existing `Scaffold` call without changing its current
`modifier`, `bottomBar`, or content lambda:

```kotlin
snackbarHost = {
    HikariSnackbarHost(hostState = snackbarHostState)
},
```

Do not add a message event bus.

- [ ] **Step 5: Run GREEN**

```bash
./gradlew :app:testDebugUnitTest \
  --tests app.openstory.ArchitectureSmokeTest \
  --stacktrace
./gradlew :app:assembleDebug :app:lintDebug --stacktrace
```

With device:

```bash
./gradlew :app:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.AppLaunchSmokeTest \
  --stacktrace
```

- [ ] **Step 6: Commit**

```bash
git add \
  app/src/main/kotlin/app/openstory/ui/OpenStoryApp.kt \
  app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt \
  app/src/test/kotlin/app/openstory/ArchitectureSmokeTest.kt \
  app/src/androidTest/kotlin/app/openstory/AppLaunchSmokeTest.kt
git commit -m "refactor: route app shell through Hikari theme"
```

---

### Task 6: Migrate Home, Search, and Story without redesign

**Files:**
- Modify: `feature/catalog/build.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifierTest.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/home/HomeScreen.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchScreen.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt`
- Modify: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/home/HomeScreenTest.kt`
- Modify: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/search/SearchScreenTest.kt`
- Modify: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/story/StoryScreenTest.kt`

**Interfaces:**
- Adds exact `:feature:catalog -> :core:designsystem`.
- Consumes `MaterialTheme.hikariSpacing`, `HikariLoadingState`, `HikariErrorState`.
- Does not change `HomeUiState`, `SearchUiState`, `StoryUiState`, ViewModels, callbacks, or domain models.

- [ ] **Step 1: Add RED architecture edge test**

```kotlin
@Test
fun catalogPresentationMayConsumeTheDesignSystem() {
    val policy = ModuleBoundaryPolicyLoader.load(
        File("../config/architecture/module-boundaries.json"),
    )
    assertTrue(
        ":core:designsystem" in
            policy.modules.getValue(":feature:catalog").productionDependencies,
    )
}
```

Run focused build-logic test and expect FAIL.

- [ ] **Step 2: Add Gradle/policy edge atomically**

Add to `feature/catalog/build.gradle.kts`:

```kotlin
implementation(project(":core:designsystem"))
```

Add `:core:designsystem` to the exact feature policy and run:

```bash
./scripts/check-module-dependencies.sh
```

- [ ] **Step 3: Replace only proven global spacing literals**

Map repeated application spacing:

```text
4dp  -> extraSmall
8dp  -> small
12dp -> medium
16dp -> large
24dp -> extraLarge
32dp -> doubleExtraLarge
```

Do not tokenise feature-specific sizes, cover dimensions, or every raw number.

- [ ] **Step 4: Normalize Story's no-content loading/failure**

If `story == null && state.refreshing`:

```kotlin
HikariLoadingState(
    label = "Loading story",
    modifier = modifier.fillMaxSize(),
)
```

If `story == null && !state.refreshing`:

```kotlin
HikariErrorState(
    title = "Story unavailable",
    message = state.failure?.let {
        "Source detail refresh failed: ${it.code}"
    },
    actionLabel = state.failure
        ?.takeIf { it.retryable }
        ?.let { "Retry" },
    onAction = state.failure
        ?.takeIf { it.retryable }
        ?.let { onRetry },
    modifier = modifier.fillMaxSize(),
)
```

Keep the existing inline failure when valid story content is already visible.

- [ ] **Step 5: Preserve Home/Search partial-failure behavior**

Do not convert cached-content/source failures into blocking error screens or new snackbar flows. Keep current content and progress/failure semantics; only normalize tokens and shared theme usage.

- [ ] **Step 6: Update Compose test wrappers to `HikariTheme`**

Retain assertions proving:
- cached Home content remains visible during partial failure;
- Search results remain visible with source failure;
- Story retry remains clickable when retryable;
- Story loading semantics are visible.

- [ ] **Step 7: Run GREEN**

```bash
./gradlew \
  :feature:catalog:testDebugUnitTest \
  :feature:catalog:assembleDebug \
  :feature:catalog:lintDebug \
  --stacktrace
```

With device, run Home/Search/Story Android tests.

- [ ] **Step 8: Commit**

```bash
git add \
  feature/catalog/build.gradle.kts \
  config/architecture/module-boundaries.json \
  build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifierTest.kt \
  feature/catalog/src/main \
  feature/catalog/src/androidTest
git commit -m "refactor: migrate catalog surfaces to UI foundation"
```

Review staged diff to ensure this commit does not accidentally include Library/Mapping/Chapter changes reserved for Task 7.

---

### Task 7: Normalize Library, Mapping, Chapters, and existing download states

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryScreen.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt`
- Modify only if justified by existing state: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/download/DownloadActionSheet.kt`
- Modify: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/library/LibraryScreenTest.kt`
- Modify: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/mapping/MappingSheetTest.kt`
- Modify: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/chapters/ChapterListTest.kt`

**Interfaces:**
- Reuses the dependency from Task 6.
- Uses `HikariEmptyState` only where the existing meaning is generic enough.
- Keeps failure classification, actions, and state in feature code.

- [ ] **Step 1: Lock existing empty-state behavior in tests**

Prove:
- Library true empty -> `"Your Library is empty."`
- Library filtered empty -> `"No stories with this status."`
- Chapter empty -> `"No chapters available"`
- Mapping empty copy/actions remain unchanged.

Run tests green before refactor.

- [ ] **Step 2: Replace Library ad-hoc empty text**

```kotlin
HikariEmptyState(
    title = if (state.selectedStatus == null) {
        "Your Library is empty."
    } else {
        "No stories with this status."
    },
    modifier = Modifier.padding(
        horizontal = MaterialTheme.hikariSpacing.large,
        vertical = MaterialTheme.hikariSpacing.medium,
    ),
)
```

No new illustration, CTA, list/grid, sorting, or filter UX.

- [ ] **Step 3: Normalize Chapter and Mapping generic empties**

Use `HikariEmptyState(title = "No chapters available")` inside the current chapter list item boundary.

For Mapping, use shared empty presentation only where it does not absorb mapping semantics or actions.

- [ ] **Step 4: Keep inline failures when context/content exists**

Do not create global error mapping. Keep mapping/chapter failure codes feature-owned. Normalize spacing/error color only.

- [ ] **Step 5: Apply YAGNI to `DownloadActionSheet`**

Migrate only if an existing repeated generic loading/error/confirmation state is clearly present. Otherwise leave it untouched. Do not create download-specific design-system APIs.

- [ ] **Step 6: Update tests to `HikariTheme` and run GREEN**

```bash
./gradlew :feature:catalog:testDebugUnitTest :feature:catalog:lintDebug --stacktrace
```

With device, run Library/Mapping/Chapter Android tests.

- [ ] **Step 7: Commit**

```bash
git add \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping \
  feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters \
  feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/library \
  feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/mapping \
  feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/chapters
git commit -m "refactor: normalize shared catalog UX states"
```

Stage `DownloadActionSheet.kt` only if it actually changed.

---

### Task 8: Migrate Reader loading/error presentation

**Files:**
- Modify: `feature/reader/build.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifierTest.kt`
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderScreen.kt`
- Modify: `feature/reader/src/androidTest/kotlin/app/openstory/reader/ui/ReaderScreenTest.kt`

**Interfaces:**
- Adds exact `:feature:reader -> :core:designsystem`.
- Consumes `HikariLoadingState`, `HikariErrorState`.
- Preserves Reader controls, content, release switching, progress flushing, retry, and navigation.

- [ ] **Step 1: Add RED architecture edge test**

Assert `:feature:reader.productionDependencies` contains `:core:designsystem`, then run focused build-logic test and expect FAIL.

- [ ] **Step 2: Add Gradle/policy edge atomically**

```kotlin
implementation(project(":core:designsystem"))
```

Add the exact policy edge and run:

```bash
./scripts/check-module-dependencies.sh
```

- [ ] **Step 3: Add Reader state regression tests**

Prove:
- loading semantics visible;
- no-document error still exposes Retry;
- clicking Retry calls `actions.onRetry`;
- valid document rendering remains unchanged.

- [ ] **Step 4: Replace ad-hoc Reader state surfaces**

Use the following two replacement branches while leaving the existing
existing `state.document != null` branch that calls `ReaderContent` byte-for-byte unchanged:

```kotlin
state.loading -> Centered {
    HikariLoadingState(label = "Loading reader")
}

else -> Centered {
    HikariErrorState(
        title = "Reader unavailable",
        message = state.failure,
        actionLabel = "Retry",
        onAction = actions.onRetry,
    )
}
```

Keep `Centered`, `Scaffold`, controls, padding, and lifecycle behavior.

- [ ] **Step 5: Update tests to `HikariTheme` and run GREEN**

```bash
./gradlew \
  :feature:reader:testDebugUnitTest \
  :feature:reader:assembleDebug \
  :feature:reader:lintDebug \
  --stacktrace
```

With device, run `ReaderScreenTest`.

- [ ] **Step 6: Commit**

```bash
git add \
  feature/reader/build.gradle.kts \
  config/architecture/module-boundaries.json \
  build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifierTest.kt \
  feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderScreen.kt \
  feature/reader/src/androidTest/kotlin/app/openstory/reader/ui/ReaderScreenTest.kt
git commit -m "refactor: migrate reader states to design system"
```

---

### Task 9: Document UX rules and post-Wave-09 architecture continuity

**Files:**
- Create: `docs/ui/design-system.md`
- Modify: `docs/superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`
- Modify: `docs/implementation/current-roadmap.md`
- Modify: `docs/implementation/waves/wave-10-background-sync-auth-and-notifications.md`
- Modify: `docs/implementation/waves/wave-11-hardening-open-source-release.md`
- Modify: `docs/PROJECT-HANDBOOK.md`
- Modify: `README.md`
- Modify/add the appropriate existing docs/roadmap contract test.

**Interfaces:**
- Produces canonical developer usage rules and a 14-module current graph.
- Keeps Wave 10/11 capability ownership intact.

- [ ] **Step 1: Add a RED docs-contract test**

Prove:
- current roadmap names `:core:designsystem`;
- post-baseline architecture records it as a dedicated between-wave decision;
- Wave 10 entry graph includes the foundation;
- Wave 11 still introduces only `:feature:plugins`.

Run the focused docs test and confirm FAIL on stale docs.

- [ ] **Step 2: Create `docs/ui/design-system.md`**

Required sections:

```markdown
# Hikari Design System

## Ownership
## Dependency rules
## Theme and token usage
## When to use Material directly
## Loading
## Empty states
## Error presentation
## Offline presentation
## Snackbar vs Toast
## Confirmation and destructive actions
## Feature mapping boundary
## Accessibility baseline
## What this foundation does not standardize
```

Required decision tables:

```text
Initial content unavailable          -> HikariLoadingState
Refresh with existing content        -> keep content + local progress
Pagination                           -> local footer/progress owned by feature
Action-local operation               -> local progress owned by feature
Background work                      -> do not block screen
```

```text
No usable content + failure          -> HikariErrorState
Usable cached/current content exists -> keep content; show non-blocking failure
Retryable action                     -> feature supplies Retry action
Domain exception                     -> map in feature; never pass exception to design system
```

```text
True empty       -> feature copy + HikariEmptyState
Filtered empty   -> feature copy + optional clear-filter action
Search empty     -> feature copy
Setup required   -> feature copy + feature-owned action
Offline/no cache -> feature mapping + HikariOfflineState
```

```text
Active Compose surface transient feedback -> Snackbar
Platform/lifecycle-only exceptional case  -> Toast may be reviewed
```

```text
Harmless action                 -> no confirmation
Reversible action               -> prefer undo when feature supports it
Meaningful destructive action   -> confirmation
Irreversible/high-impact action -> destructive confirmation + precise feature copy
```

- [ ] **Step 3: Amend post-Baseline architecture**

Add an explicit foundation row between Wave 09 and Wave 10:

```text
UI foundation | :core:designsystem
```

Add design-system dependencies only to presentation modules and `:app`.

- [ ] **Step 4: Correct canonical roadmap to 14 modules**

Document:
- Wave 09 produced the thirteen-module capability graph;
- the approved between-wave foundation adds `:core:designsystem`;
- Wave 10 remains next.

Do not mark Wave 10 started.

- [ ] **Step 5: Update Wave 10/11 entry continuity**

Wave 10 entry:

```text
Wave 09 verified exit graph plus the approved :core:designsystem UI-foundation boundary.
```

Wave 10 still introduces only `:settings`, `:feature:settings`; Wave 11 still introduces only `:feature:plugins`.

Future presentation modules consume `:core:designsystem`.

- [ ] **Step 6: Update handbook/README stale current-graph wording**

Preserve historical Baseline 2 evidence as historical; do not rewrite it as if design system existed then.

- [ ] **Step 7: Run GREEN docs/architecture checks**

```bash
bash scripts/tests/post-baseline-wave-roadmap-test.sh
bash scripts/tests/verify-current-architecture-test.sh
./gradlew :build-logic:test --stacktrace
./scripts/check-module-dependencies.sh
```

- [ ] **Step 8: Commit**

```bash
git add \
  docs/ui/design-system.md \
  docs/superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md \
  docs/implementation/current-roadmap.md \
  docs/implementation/waves/wave-10-background-sync-auth-and-notifications.md \
  docs/implementation/waves/wave-11-hardening-open-source-release.md \
  docs/PROJECT-HANDBOOK.md \
  README.md \
  build-logic/src/test \
  scripts/tests
git commit -m "docs: formalize design system UX foundation"
```

Stage only files actually changed.

---

### Task 10: Protect design-system isolation with anti-regression architecture tests

**Files:**
- Modify: `build-logic/src/test/kotlin/app/openstory/build/ArchitecturePolicyTest.kt` or the existing matching policy-test file.
- Modify: `config/architecture/module-boundaries.json` only if a missing forbidden prefix is discovered.

**Interfaces:**
- Produces fail-closed proof that design system stays domain-neutral.
- Reuses existing exact-dependency/forbidden-import machinery; no new architecture engine.

- [ ] **Step 1: Add dependency-direction tests**

```kotlin
@Test
fun designSystemHasNoProjectDependencies() {
    val policy = ModuleBoundaryPolicyLoader.load(
        File("../config/architecture/module-boundaries.json"),
    )
    assertTrue(
        policy.modules.getValue(":core:designsystem")
            .productionDependencies
            .isEmpty(),
    )
}

@Test
fun capabilityModulesDoNotDependOnDesignSystem() {
    val policy = ModuleBoundaryPolicyLoader.load(
        File("../config/architecture/module-boundaries.json"),
    )
    val capabilityModules = listOf(
        ":catalog",
        ":library",
        ":chapters",
        ":reader",
        ":downloads",
        ":storage:room",
        ":storage:files",
        ":plugins:api",
        ":plugins:runtime",
    )

    capabilityModules.forEach { module ->
        assertTrue(
            ":core:designsystem" !in
                policy.modules.getValue(module).productionDependencies,
            "$module must not depend on :core:designsystem",
        )
    }
}
```

- [ ] **Step 2: Add forbidden-import fixture coverage**

Using existing verifier-test patterns, create a temporary `:core:designsystem` source with:

```kotlin
import app.openstory.catalog.model.CatalogEntry
```

and assert verification rejects it. This protects the boundary if policy is loosened later.

- [ ] **Step 3: Run GREEN**

```bash
./gradlew :build-logic:test --stacktrace
./scripts/check-module-dependencies.sh
```

- [ ] **Step 4: Commit**

```bash
git add build-logic/src/test config/architecture/module-boundaries.json
git commit -m "test: protect design system architecture boundary"
```

---

### Task 11: Run full verification and record the foundation checkpoint

**Files:**
- Create: `docs/internal/checkpoints/design-system-foundation.md`

**Interfaces:**
- Consumes all prior tasks.
- Produces evidence that Wave 10 may start from the new approved graph.

- [ ] **Step 1: Capture environment**

```bash
git status --short
git log -10 --oneline
java -version
./gradlew --version
```

Record JDK 17.

- [ ] **Step 2: Run focused verification**

```bash
./gradlew \
  :core:designsystem:assembleDebug \
  :core:designsystem:lintDebug \
  :feature:catalog:testDebugUnitTest \
  :feature:reader:testDebugUnitTest \
  :app:testDebugUnitTest \
  --stacktrace
```

- [ ] **Step 3: Run architecture/full fast gate**

```bash
./scripts/check-module-dependencies.sh
./scripts/verify-current-architecture.sh
./scripts/verify.sh
```

Expected:
- 14-module policy accepted;
- no forbidden dependency/import;
- lint/detekt/tests/assemble pass;
- Room schema fingerprint unchanged.

- [ ] **Step 4: Run Compose instrumentation on API 26**

```bash
./gradlew \
  :core:designsystem:connectedDebugAndroidTest \
  :feature:catalog:connectedDebugAndroidTest \
  :feature:reader:connectedDebugAndroidTest \
  :app:connectedDebugAndroidTest \
  --stacktrace
```

- [ ] **Step 5: Repeat instrumentation on API 37**

Run the same command with the API 37 device selected.

- [ ] **Step 6: Manual behavior-preservation review**

Inspect:

```text
Home
Search
Library
Story
Mapping
Chapter list
Reader
top-level navigation
```

Verify only:
- same information;
- same actions;
- same destinations;
- cached-content failures remain non-blocking;
- empty states preserve meaning;
- Reader retry works;
- no new product flow;
- light/dark theme remains readable.

Do not approve/reject visual redesign because redesign is out of scope.

- [ ] **Step 7: Write checkpoint evidence**

First capture the execution date:

```bash
date +%F
```

Create `docs/internal/checkpoints/design-system-foundation.md` using that exact command
output as the `Date` value. Set `Status: ACCEPTED` only after every required host,
API 26, API 37, and manual check in this task has passed; otherwise set `Status: OPEN`.

The checkpoint must contain these fixed sections and facts:

```markdown
# Design System Foundation Checkpoint

## Boundary
Wave 09 verified graph + :core:designsystem

## Production module count
14

## Scope verified
- architecture boundary
- HikariTheme and tokens
- loading/empty/error/offline primitives
- snackbar host
- confirmation/destructive confirmation
- app root migration
- feature:catalog migration
- feature:reader migration
- no screen redesign

## Architecture review
- :core:designsystem project dependencies: none
- capability -> design-system edges: none
- presentation -> design-system edges: app, feature:catalog, feature:reader
- Room schema: unchanged
- plugin protocol/runtime: unchanged

## Next boundary
Wave 10
```

Add a `## Commands` table with one row for every command actually executed in Steps 1-5,
recording the exact command, environment (`JDK 17 host`, `API 26`, or `API 37`), and its
actual result. Add `## Manual behavior-preservation review` with one bullet for each surface
checked in Step 6 and the actual observed result.

Never record PASS for a command or manual check that was not run.

- [ ] **Step 8: Final verify and commit**

```bash
./scripts/verify.sh

git add docs/internal/checkpoints/design-system-foundation.md
git commit -m "docs: accept design system foundation checkpoint"
```

---

## Final Acceptance Checklist

- [ ] `:core:designsystem` exists and is registered.
- [ ] It has zero direct project dependencies.
- [ ] Exact policy matches all consumer edges.
- [ ] `:app`, `:feature:catalog`, and `:feature:reader` consume it.
- [ ] No capability/storage/plugin module consumes it.
- [ ] Root application uses `HikariTheme`.
- [ ] Color, typography, shapes, spacing have one owner.
- [ ] Spacing tokens represent actual global spacing rules, not every number.
- [ ] Loading, empty, error, offline shared surfaces exist.
- [ ] Snackbar host exists without a global event bus.
- [ ] Standard/destructive confirmation exists without feature consequences.
- [ ] Cached-content partial failures remain non-blocking.
- [ ] Domain failures are still mapped by feature presentation.
- [ ] Home/Search/Story/Library/Mapping/Chapter/Reader actions/navigation remain intact.
- [ ] No screen redesign or new navigation/gesture flow was introduced.
- [ ] Room schema unchanged.
- [ ] Plugin protocol/runtime unchanged.
- [ ] Roadmap and Wave 10/11 entry continuity acknowledge the foundation boundary.
- [ ] `docs/ui/design-system.md` documents the shared UX rules.
- [ ] `./scripts/verify.sh` passes.
- [ ] Compose instrumentation passes on API 26 and API 37.
- [ ] Checkpoint evidence contains only commands actually run.

## Execution Notes

Implement Task 1 through Task 11 in order. Do not parallelize graph/policy mutations without a single integrator.

At execution time, first create an isolated git worktree. Recommended execution mode is subagent-driven development with a fresh worker/review gate per task.

# Hikari ReDantotsu-Inspired Product UI Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace Hikari's sparse presentation with an artwork-first Discover/Home/Library product, rich Story and Reader flows, responsive target-pack assets, and roadmap-safe utility navigation inspired by ReDantotsu.

**Architecture:** Keep the current 14-module capability graph intact during the redesign checkpoint. `:app` composes routes and the adaptive shell, `:core:designsystem` owns domain-neutral artwork/glass/adaptive primitives, `:feature:catalog` owns story-aware product presentation and projections, and `:feature:reader` owns immersive reading. Wave 10 and Wave 11 remain the owners of full Settings and Plugin-management presentation; this plan defines their visual/navigation handoff without pulling those modules forward.

**Tech Stack:** Kotlin 2.4.10, Android Gradle Plugin 9.3.0, Jetpack Compose/Material 3 BOM 2026.06.00, Navigation 3, Hilt, Coroutines/Flow, Room 2.8.4, Coil 3.5.0, Backdrop 2.0.0, Roborazzi 1.71.0, Robolectric 4.16.1, Edge headless, PowerShell.

## Global Constraints

- Work from the approved spec: `docs/superpowers/specs/2026-08-12-redantotsu-inspired-product-ui-design.md`.
- Use the approved Hikari visual references under `docs/ui/references/product-ui/` for composition, density, hierarchy, artwork treatment, glass boundaries, and navigation placement; written spec requirements remain authoritative for behavior and scope.
- Android `minSdk` remains 26; `targetSdk` remains 37; builds use JDK 17.
- The current redesign checkpoint keeps exactly 14 production modules and adds no new production module.
- Room schema history 1 through 6 remains byte-stable; read-only DAO queries do not require a migration.
- Top-level navigation is exactly `Discover / Home / Library` and launches on `Home`.
- Plugins, Downloads, Updates, and Settings are utility destinations, not top-level destinations.
- Current-graph code implements Downloads and Updates; full Settings remains Wave 10 ownership and full Plugin management remains Wave 11 ownership.
- Do not copy ReDantotsu source, artwork, logos, fonts, or branded assets.
- Do not add social, recommendation, remote marketplace, or cloud-account placeholders.
- API 31+ may use bounded real backdrop blur; API 26-30 must use the same geometry with a translucent non-blur fallback.
- Required target sizes are `360x800dp`, `412x892dp`, and `600x960dp`; exports are 2x PNG.
- Cover and backdrop must consume one remembered artwork state and one cache identity.
- Cached content remains visible during refresh and partial failures.
- Motion is limited to navigation selection, hero/content transitions, sheets, and reader chrome; `LocalHikariMotionPolicy` reduces these to fades or no motion when reduction is enabled.
- Every screen preserves TalkBack labels, keyboard/D-pad focus order, font scaling, and minimum 48x48dp interactive targets.
- Every task uses RED/GREEN tests and ends with a focused commit.

---

## Approved Visual References

![Approved Discover, Home, and Library navigation](../../ui/references/product-ui/approved-navigation.png)

![Approved artwork-first visual system](../../ui/references/product-ui/approved-visual-system.png)

![Approved product flow and scope](../../ui/references/product-ui/approved-product-flow.png)

UI tasks must review their output against these images before recording Roborazzi
baselines. Do not copy the abstract cards literally when real plugin artwork exists, and
do not add controls shown only as future scope in the product-flow reference.

---

## File and Interface Map

### Design-system additions

```text
core/designsystem/src/main/kotlin/app/openstory/designsystem/artwork/HikariArtwork.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/artwork/HikariArtworkFallback.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/glass/HikariBackdropHost.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/glass/HikariGlassSurface.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/navigation/HikariFloatingNavigation.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariResponsiveContent.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/motion/HikariMotionPolicy.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariCoverCardFrame.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariSectionHeader.kt
core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariMetadataBadge.kt
```

Public contracts produced by Tasks 3-4:

```kotlin
@Immutable
data class HikariArtworkModel(
    val url: String?,
    val stableKey: String,
    val title: String,
)

@Immutable
data class HikariArtworkFallback(
    val startColor: Color,
    val endColor: Color,
    val monogram: String,
)

fun fallbackFor(stableKey: String, title: String): HikariArtworkFallback

@Stable
class HikariArtworkState internal constructor(
    internal val painter: Painter,
    val fallback: HikariArtworkFallback,
    val loading: Boolean,
)

@Composable
fun rememberHikariArtwork(model: HikariArtworkModel): HikariArtworkState

@Composable
fun HikariArtwork(
    state: HikariArtworkState,
    contentDescription: String?,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Crop,
)

@Composable
fun HikariArtworkBackdrop(
    state: HikariArtworkState,
    modifier: Modifier = Modifier,
    scrim: Brush = HikariBackdropDefaults.scrim,
)

object HikariBackdropDefaults {
    val scrim: Brush
        @Composable get
}

@Stable
class HikariBackdropScope internal constructor(
    internal val token: HikariBackdropToken,
)

internal class HikariBackdropToken(internal val backdrop: Backdrop)

@Composable
fun HikariBackdropHost(
    modifier: Modifier = Modifier,
    background: @Composable () -> Unit,
    overlay: @Composable HikariBackdropScope.() -> Unit,
)

@Composable
fun HikariGlassSurface(
    backdropScope: HikariBackdropScope,
    modifier: Modifier = Modifier,
    shape: Shape,
    contentPadding: PaddingValues = PaddingValues.Zero,
    content: @Composable () -> Unit,
)

enum class HikariWindowClass { COMPACT, LARGE_PHONE, MEDIUM }

fun classifyWindow(maxWidth: Dp): HikariWindowClass

@Immutable
data class HikariMotionPolicy(
    val reduceMotion: Boolean,
)

val LocalHikariMotionPolicy: ProvidableCompositionLocal<HikariMotionPolicy>
```

App-shell contract produced by Task 5:

```kotlin
internal val APP_START_ROUTE: AppRoute = AppRoute.Home
```

### Feature projections

```text
feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/    existing catalog Home renamed/rebuilt
feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/   new personal Home
feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/     rich status collection
feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/       Overview/Chapters/Sources detail
feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/     local library update feed
feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/   download queue/history
feature/catalog/src/main/kotlin/app/openstory/catalog/ui/components/  story-aware cover/shelf/hero components
```

### Read-only observation extensions

```kotlin
fun ReadingProgressRepository.observeAll(): Flow<List<ReadingProgress>>
fun ChapterRepository.observeAll(): Flow<List<CanonicalChapterGroup>>
fun DownloadRepository.observeAll(): Flow<List<DownloadRecord>>
```

These methods expose existing rows only. They do not add tables, columns, or migrations.

### Target-pack source

```text
tools/ui-target/src/index.html
tools/ui-target/src/styles.css
tools/ui-target/src/app.js
tools/ui-target/src/mock-data.js
tools/ui-target/render-ui-target.ps1
tools/ui-target/package-ui-target.ps1
```

Generated files live under `tools/ui-target/build/` and remain ignored. The packaging script writes `Hikari-UI-Target-Pack.zip` to a caller-provided path, including `E:\Downloads\Hikari-UI-Target-Pack.zip`.

---

### Task 1: Pin visual, artwork, and screenshot dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `gradle/verification-metadata.xml`
- Modify: `core/designsystem/build.gradle.kts`
- Modify: `feature/catalog/build.gradle.kts`
- Modify: `feature/reader/build.gradle.kts`
- Modify: `app/build.gradle.kts`
- Modify: `build.gradle.kts`
- Test: `build-logic/src/test/kotlin/app/openstory/build/RepositoryHygieneTest.kt`

**Interfaces:**
- Produces library aliases `coil-compose`, `coil-network-okhttp`, `backdrop`, `roborazzi-core`, `roborazzi-compose`, `robolectric`, and plugin alias `roborazzi`.
- Does not add a project dependency to `:core:designsystem`.

- [ ] **Step 1: Add a RED dependency-contract test**

Add assertions to `RepositoryHygieneTest` that the version catalog contains exact pinned versions:

```kotlin
assertTrue(catalog.contains("coil = \"3.5.0\""))
assertTrue(catalog.contains("backdrop = \"2.0.0\""))
assertTrue(catalog.contains("roborazzi = \"1.71.0\""))
assertTrue(catalog.contains("robolectric = \"4.16.1\""))
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :build-logic:test --tests app.openstory.build.RepositoryHygieneTest --stacktrace
```

Expected: FAIL because the aliases are absent.

- [ ] **Step 3: Add exact catalog aliases**

```toml
[versions]
coil = "3.5.0"
backdrop = "2.0.0"
roborazzi = "1.71.0"
robolectric = "4.16.1"

[libraries]
coil-compose = { module = "io.coil-kt.coil3:coil-compose", version.ref = "coil" }
coil-network-okhttp = { module = "io.coil-kt.coil3:coil-network-okhttp", version.ref = "coil" }
backdrop = { module = "io.github.kyant0:backdrop", version.ref = "backdrop" }
roborazzi-core = { module = "io.github.takahirom.roborazzi:roborazzi", version.ref = "roborazzi" }
roborazzi-compose = { module = "io.github.takahirom.roborazzi:roborazzi-compose", version.ref = "roborazzi" }
robolectric = { module = "org.robolectric:robolectric", version.ref = "robolectric" }
androidx-compose-foundation = { group = "androidx.compose.foundation", name = "foundation" }
androidx-compose-material-icons-extended = { group = "androidx.compose.material", name = "material-icons-extended" }

[plugins]
roborazzi = { id = "io.github.takahirom.roborazzi", version.ref = "roborazzi" }
```

- [ ] **Step 4: Wire module dependencies**

Apply `alias(libs.plugins.roborazzi)` to `:core:designsystem`, `:feature:catalog`, `:feature:reader`, and `:app`. Add Coil and Backdrop only to `:core:designsystem`; add Roborazzi/Robolectric and Compose UI test artifacts as test dependencies to all four screenshot-owning modules.

Enable Android resources for unit screenshot tests:

```kotlin
android {
    testOptions.unitTests.isIncludeAndroidResources = true
}
```

- [ ] **Step 5: Write strict dependency metadata**

```bash
./gradlew --write-verification-metadata sha256 \
  :core:designsystem:testDebugUnitTest \
  :feature:catalog:testDebugUnitTest \
  :feature:reader:testDebugUnitTest \
  :app:testDebugUnitTest
```

Review `gradle/verification-metadata.xml`; retain existing components and add only resolved artifacts.

- [ ] **Step 6: Run GREEN**

```bash
./gradlew :build-logic:test :core:designsystem:testDebugUnitTest --stacktrace
```

- [ ] **Step 7: Commit**

```bash
git add build.gradle.kts gradle/libs.versions.toml gradle/verification-metadata.xml \
  core/designsystem/build.gradle.kts feature/catalog/build.gradle.kts \
  feature/reader/build.gradle.kts app/build.gradle.kts build-logic/src/test
git commit -m "build: add product ui rendering toolchain"
```

---

### Task 2: Create a reproducible target-pack rendering pipeline

**Files:**
- Create: `tools/ui-target/src/index.html`
- Create: `tools/ui-target/src/styles.css`
- Create: `tools/ui-target/src/mock-data.js`
- Create: `tools/ui-target/src/app.js`
- Create: `tools/ui-target/render-ui-target.ps1`
- Create: `tools/ui-target/package-ui-target.ps1`
- Modify: `.gitignore`
- Test: `scripts/tests/ui-target-pack-test.ps1`

**Interfaces:**
- Consumes no production code.
- Produces deterministic 2x PNG files and the target ZIP.

- [ ] **Step 1: Write a RED pack-contract test**

The PowerShell test must call the renderer into a temporary directory and assert these exact files:

```powershell
$required = @(
  '00-overview-compact.png',
  '01-compact-360x800/01-discover.png',
  '01-compact-360x800/02-home.png',
  '01-compact-360x800/03-library.png',
  '01-compact-360x800/04-search.png',
  '01-compact-360x800/05-story-overview.png',
  '01-compact-360x800/06-story-sources.png',
  '01-compact-360x800/07-story-chapters.png',
  '01-compact-360x800/08-mapping.png',
  '01-compact-360x800/09-downloads.png',
  '01-compact-360x800/10-updates.png',
  '01-compact-360x800/11-reader.png',
  '01-compact-360x800/12-plugin-manager.png',
  '01-compact-360x800/13-settings.png',
  '02-large-phone-412x892/01-discover.png',
  '02-large-phone-412x892/02-home.png',
  '02-large-phone-412x892/03-library.png',
  '02-large-phone-412x892/04-story.png',
  '03-medium-600x960/01-discover.png',
  '03-medium-600x960/02-home.png',
  '03-medium-600x960/03-library.png',
  '03-medium-600x960/04-story.png',
  '04-ux-states-360x800/01-loading.png',
  '04-ux-states-360x800/02-empty.png',
  '04-ux-states-360x800/03-error.png',
  '04-ux-states-360x800/04-partial-failure.png',
  '04-ux-states-360x800/05-offline.png',
  '05-light-360x800/01-discover.png',
  '05-light-360x800/02-home.png',
  '05-light-360x800/03-library.png',
  '05-light-360x800/04-story.png',
  '05-light-360x800/05-plugin-manager.png',
  '05-light-360x800/06-settings.png',
  '05-light-360x800/07-reader.png'
)
```

Also assert each PNG is exactly double the dp dimensions encoded by its folder.

- [ ] **Step 2: Run RED**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/tests/ui-target-pack-test.ps1
```

Expected: FAIL because the renderer does not exist.

- [ ] **Step 3: Implement one query-driven HTML renderer**

`app.js` reads `screen`, `theme`, `width`, and `height` from `URLSearchParams`, selects data from `mock-data.js`, and renders only approved real flows. Use deterministic inline SVG artwork generated from story IDs; do not embed ReDantotsu screenshots or remote URLs.

Required dispatcher:

```javascript
const renderers = {
  discover: renderDiscover,
  home: renderHome,
  library: renderLibrary,
  search: renderSearch,
  storyOverview: renderStoryOverview,
  storySources: renderStorySources,
  storyChapters: renderStoryChapters,
  mapping: renderMapping,
  downloads: renderDownloads,
  updates: renderUpdates,
  reader: renderReader,
  pluginManager: renderPluginManager,
  settings: renderSettings,
  stateLoading: renderLoading,
  stateEmpty: renderEmpty,
  stateError: renderError,
  statePartialFailure: renderPartialFailure,
  stateOffline: renderOffline,
}
```

- [ ] **Step 4: Implement Edge-headless capture**

`render-ui-target.ps1` resolves Edge from:

```powershell
$edgeCandidates = @(
  'C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe',
  'C:\Program Files\Microsoft\Edge\Application\msedge.exe'
)
```

For a `360x800dp` target, invoke Edge with `--window-size=360,800`, `--force-device-scale-factor=2`, `--hide-scrollbars`, and `--virtual-time-budget=1500`. The renderer asserts `window.innerWidth === 360` and `window.innerHeight === 800`; the pack test asserts the resulting PNG is `720x1600`. Repeat for every matrix entry.

- [ ] **Step 5: Implement ZIP packaging**

`package-ui-target.ps1` runs the renderer, writes a UTF-8 `README.md` with the approved implementation rules, and uses `Compress-Archive` to produce the caller-provided output:

```powershell
param([string]$Output = 'E:\Downloads\Hikari-UI-Target-Pack.zip')
```

- [ ] **Step 6: Ignore generated output only**

Add:

```gitignore
/tools/ui-target/build/
```

Do not ignore `tools/ui-target/src` or scripts.

- [ ] **Step 7: Run GREEN and inspect the overview**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/tests/ui-target-pack-test.ps1
powershell -ExecutionPolicy Bypass -File tools/ui-target/package-ui-target.ps1 `
  -Output "$env:TEMP\Hikari-UI-Target-Pack.zip"
```

Open the generated overview plus one dark and one light reference. Confirm they use Discover/Home/Library, artwork-first composition, dense shelves, floating glass navigation, and no unsupported social/marketplace content. Plugin Manager and Settings are explicitly annotated as Wave 11 and Wave 10 visual targets rather than current clickable routes.

- [ ] **Step 8: Commit**

```bash
git add .gitignore tools/ui-target scripts/tests/ui-target-pack-test.ps1
git commit -m "design: add reproducible ui target pack"
```

---

### Task 3: Implement shared artwork state and stable fallbacks

**Files:**
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/artwork/HikariArtwork.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/artwork/HikariArtworkFallback.kt`
- Test: `core/designsystem/src/test/kotlin/app/openstory/designsystem/artwork/HikariArtworkFallbackTest.kt`
- Test: `core/designsystem/src/test/kotlin/app/openstory/designsystem/artwork/HikariArtworkScreenshotTest.kt`

**Interfaces:**
- Produces `HikariArtworkModel`, `HikariArtworkState`, `rememberHikariArtwork`, `HikariArtwork`, and `HikariArtworkBackdrop` exactly as declared in the file map.

- [ ] **Step 1: Write RED fallback tests**

```kotlin
@Test
fun sameStableKeyProducesSameFallback() {
    assertEquals(
        fallbackFor("story-42", "Moonlit Archive"),
        fallbackFor("story-42", "Moonlit Archive"),
    )
}

@Test
fun fallbackMonogramUsesFirstLetterOrQuestionMark() {
    assertEquals("M", fallbackFor("42", " Moonlit Archive ").monogram)
    assertEquals("?", fallbackFor("42", " ").monogram)
}
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :core:designsystem:testDebugUnitTest \
  --tests app.openstory.designsystem.artwork.HikariArtworkFallbackTest --stacktrace
```

- [ ] **Step 3: Implement deterministic fallback and one request identity**

Use SHA-256 of `stableKey` to select two colors from a fixed Hikari palette. Build the Coil request once in `rememberHikariArtwork`:

```kotlin
val cacheKey = remember(model.stableKey, model.url) {
    "hikari-artwork:${model.stableKey}:${model.url.orEmpty()}"
}
val request = ImageRequest.Builder(LocalPlatformContext.current)
    .data(model.url)
    .memoryCacheKey(cacheKey)
    .diskCacheKey(cacheKey)
    .crossfade(true)
    .build()
val painter = rememberAsyncImagePainter(request)
```

Both cover and backdrop receive the same remembered `HikariArtworkState`; neither builds another request.

- [ ] **Step 4: Add screenshot tests for loaded/loading/fallback geometry**

Use an injected/fake Coil `ImageLoader` and `captureRoboImage` to prove all states retain the same bounds and monogram semantics.

- [ ] **Step 5: Run GREEN**

```bash
./gradlew :core:designsystem:testDebugUnitTest recordRoborazziDebug --stacktrace
```

- [ ] **Step 6: Commit**

```bash
git add core/designsystem/src/main core/designsystem/src/test
git commit -m "feat: add shared artwork presentation"
```

---

### Task 4: Implement glass, responsive, and shared content primitives

**Files:**
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/glass/HikariBackdropHost.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/glass/HikariGlassSurface.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/navigation/HikariFloatingNavigation.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariResponsiveContent.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/motion/HikariMotionPolicy.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariCoverCardFrame.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariSectionHeader.kt`
- Create: `core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariMetadataBadge.kt`
- Test: `core/designsystem/src/test/kotlin/app/openstory/designsystem/HikariProductPrimitivesTest.kt`
- Test: `core/designsystem/src/test/kotlin/app/openstory/designsystem/HikariProductPrimitivesScreenshotTest.kt`

**Interfaces:**
- Produces opaque `HikariBackdropScope`; no Backdrop type escapes public APIs.
- Produces `HikariWindowClass { COMPACT, LARGE_PHONE, MEDIUM }` from max width.

- [ ] **Step 1: Write RED behavior tests**

Assert:

```kotlin
assertEquals(HikariWindowClass.COMPACT, classifyWindow(360.dp))
assertEquals(HikariWindowClass.LARGE_PHONE, classifyWindow(412.dp))
assertEquals(HikariWindowClass.MEDIUM, classifyWindow(600.dp))
```

Compose tests must assert three navigation items, exactly one selected item, selected semantics, and a minimum 48dp clickable area.

- [ ] **Step 2: Run RED**

```bash
./gradlew :core:designsystem:testDebugUnitTest --stacktrace
```

- [ ] **Step 3: Implement Backdrop 2.0 host and API fallback**

`HikariBackdropHost` records only background content using `rememberLayerBackdrop()` and `Modifier.layerBackdrop(backdrop)`. `HikariGlassSurface` uses `Modifier.drawBackdrop` with `blur(8.dp.toPx())` only when `SDK_INT >= 31`; otherwise render the same shape with a theme-derived `0xD9` translucent surface, 1dp border, and 8dp shadow.

Do not use `lens` or chromatic aberration in the first implementation.

- [ ] **Step 4: Implement shared content and motion primitives**

Keep them slot-based and domain-neutral. Example navigation item:

```kotlin
@Immutable
data class HikariNavigationItem(
    val key: String,
    val label: String,
    val icon: ImageVector,
)
```

`LocalHikariMotionPolicy` defaults to `reduceMotion = false`; navigation, hero, sheet, and reader call sites branch on it and use only fades or no transition when true.

- [ ] **Step 5: Record compact/light/dark/API-fallback screenshots**

Use Robolectric configs `sdk = [26]` and `sdk = [35]`; the API 26 golden must show the same geometry without blur.

- [ ] **Step 6: Run GREEN**

```bash
./gradlew :core:designsystem:testDebugUnitTest recordRoborazziDebug \
  :core:designsystem:lintDebug --stacktrace
```

- [ ] **Step 7: Commit**

```bash
git add core/designsystem/src/main core/designsystem/src/test
git commit -m "feat: add adaptive glass product primitives"
```

---

### Task 5: Replace the app shell with Discover/Home/Library and utility navigation

**Files:**
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppRoute.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/TopLevelDestination.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavigator.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`
- Create: `app/src/main/kotlin/app/openstory/ui/HikariUtilitySheet.kt`
- Modify: `app/src/test/kotlin/app/openstory/navigation/AppRouteSerializationTest.kt`
- Create: `app/src/test/kotlin/app/openstory/navigation/AppShellScreenshotTest.kt`
- Modify: `app/src/androidTest/kotlin/app/openstory/navigation/AppNavigationTest.kt`
- Modify: `app/src/androidTest/kotlin/app/openstory/AppLaunchSmokeTest.kt`

**Interfaces:**
- Produces routes `Discover`, `Home`, `Library`, `Search`, `Downloads`, `Updates`, `Plugins`, `Settings`, `Story`, and `Reader`.
- Shows floating navigation only for the first three.
- Produces `APP_START_ROUTE = AppRoute.Home`; both `rememberAppNavigator()` and tests consume this constant.

- [ ] **Step 1: Write RED route and navigation tests**

```kotlin
assertEquals(
    listOf("Discover", "Home", "Library"),
    topLevelDestinations.map(TopLevelDestination::label),
)
assertEquals(AppRoute.Home, APP_START_ROUTE)
```

Instrumentation must prove Story and Reader do not expose the floating bar. `AppShellScreenshotTest` records Home-selected dark, Discover-selected light, and the Downloads/Updates utility sheet at 360x800.

- [ ] **Step 2: Run RED**

```bash
./gradlew :app:testDebugUnitTest --tests '*AppRouteSerializationTest' --stacktrace
```

- [ ] **Step 3: Change the route model**

Add `AppRoute.Discover`, `Downloads`, and `Updates`. Keep `Plugins` and `Settings` serializable for Wave continuity, but do not render placeholder destinations. Change `rememberNavBackStack(AppRoute.Home)` to `rememberNavBackStack(APP_START_ROUTE)`.

Use:

```kotlin
fun AppRoute?.isTopLevel(): Boolean = topLevelDestinations.any { it.route == this }
```

- [ ] **Step 4: Implement the floating shell**

Wrap `NavDisplay` and `HikariFloatingNavigation` in `HikariBackdropHost`. Do not apply Scaffold bottom padding globally; focused screens and Reader must remain edge-to-edge. Apply safe content padding inside each top-level screen.

- [ ] **Step 5: Implement conditional utility sheet entries**

Current checkpoint entries are `Downloads` and `Updates`. `Plugins` and `Settings` are added by the Wave 11/10 continuity tasks, respectively; do not show disabled or fake rows now.

- [ ] **Step 6: Run GREEN**

```bash
./gradlew :app:testDebugUnitTest :app:assembleDebug --stacktrace
```

With a device, run `AppNavigationTest`.

- [ ] **Step 7: Commit**

```bash
git add app/src/main/kotlin/app/openstory/navigation app/src/main/kotlin/app/openstory/ui \
  app/src/test/kotlin/app/openstory/navigation app/src/androidTest/kotlin/app/openstory/navigation \
  app/src/androidTest/kotlin/app/openstory/AppLaunchSmokeTest.kt
git commit -m "refactor: establish discover home library shell"
```

---

### Task 6: Rename catalog Home to Discover and implement rich discovery

**Files:**
- Move: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/home/*` to `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/*`
- Move: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/home/HomeViewModelTest.kt` to `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModelTest.kt`
- Move: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/home/HomeScreenTest.kt` to `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/discover/DiscoverScreenTest.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/components/StoryCoverCard.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/components/StoryShelf.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverHero.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`

**Interfaces:**
- Produces `DiscoverUiState`, `DiscoverViewModel`, and `DiscoverScreen`.
- `DiscoverUiState.featured` is deterministic and nullable.

- [ ] **Step 1: Write RED featured-selection tests**

Required cases:

```kotlin
featuredPrefersArtworkThenScoreThenStableIdentity()
featuredFallsBackToHighestRankedEntryWithoutArtwork()
partialRefreshKeepsCachedShelvesVisible()
sourceSectionsPreservePluginOrderAndIdentity()
```

Tie-break by `pluginId.value`, then `sourceId`.

- [ ] **Step 2: Run RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest --tests '*Discover*' --stacktrace
```

- [ ] **Step 3: Perform the package rename atomically**

Rename classes and test packages. Delete no behavior: catalog selection, combined view, refresh reports, and partial-failure preservation remain.

- [ ] **Step 4: Add the feature-owned projection**

```kotlin
data class DiscoverUiState(
    val catalogs: List<CatalogHomeSnapshot> = emptyList(),
    val rankedStories: List<RankedCatalogStory> = emptyList(),
    val featured: CatalogEntry? = null,
    val selectedCatalogId: PluginId? = null,
    val refreshing: Boolean = false,
    val refreshReport: DiscoverRefreshReport? = null,
    val failure: DiscoverUiFailure? = null,
)

data class DiscoverRefreshReport(
    val succeeded: Set<PluginId> = emptySet(),
    val failed: Map<PluginId, String> = emptyMap(),
    val refreshedAtEpochMillis: Map<PluginId, Long?> = emptyMap(),
)

data class DiscoverUiFailure(
    val code: String,
    val retryable: Boolean,
)
```

Rename `HomeRefreshReport` to `DiscoverRefreshReport` and `HomeUiFailure` to `DiscoverUiFailure`; preserve their existing observation-versus-refresh failure behavior rather than creating a second error model.

- [ ] **Step 5: Build the ReDantotsu-inspired layout**

Compact layout order:

```text
search + utility avatar
featured backdrop/cover hero
quick category cards
combined or source-owned shelves
inline partial-failure status
floating navigation overlay
```

Use real `coverUrl`; no letter placeholder except `HikariArtwork` fallback. Quick categories are derived only from available source filter descriptors and current content metadata (genre, content type, language, latest/source group); omit a category when no source can execute the corresponding filter.

- [ ] **Step 6: Add semantics and screenshot tests**

Assert featured title/score/source semantics, shelf heading semantics, partial failure without loss of cards, 48dp search/avatar/category targets, and deterministic keyboard/D-pad order. Record compact/large/medium dark and compact light baselines.

- [ ] **Step 7: Run GREEN**

```bash
./gradlew :feature:catalog:testDebugUnitTest recordRoborazziDebug \
  :feature:catalog:lintDebug --stacktrace
```

- [ ] **Step 8: Commit**

```bash
git add feature/catalog/src app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt
git commit -m "feat: redesign multi-source discover"
```

---

### Task 7: Add read-only global observation for progress, chapters, and downloads

**Files:**
- Modify: `reader/src/main/kotlin/app/openstory/reader/progress/ReadingProgressRepository.kt`
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/repository/ChapterRepository.kt`
- Modify: `downloads/src/main/kotlin/app/openstory/downloads/DownloadRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/reader/ReadingProgressDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/reader/RoomReadingProgressRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomChapterRepository.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/DownloadDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepository.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/reader/RoomReadingProgressRepositoryTest.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/chapters/RoomChapterRepositoryTest.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/downloads/RoomDownloadRepositoryTest.kt`
- Modify: repository fakes returned by `rg -l "ReadingProgressRepository|ChapterRepository|DownloadRepository" --glob '*.kt' --glob '!**/build/**'`

**Interfaces:**
- Produces the three `observeAll()` methods declared in the file map.
- No schema change.

- [ ] **Step 1: Write RED repository tests**

Insert rows belonging to two stories/releases and assert these exact orders: progress by `updatedAtEpochMillis DESC`, then `storyId`, then `canonicalChapterId`; chapter groups by `storyId`, then `canonicalChapterId`; downloads by `updatedAtEpochMillis DESC`, then `releaseId`.

- [ ] **Step 2: Run RED**

```bash
./gradlew :storage:room:connectedDebugAndroidTest --stacktrace
```

- [ ] **Step 3: Add interface methods and DAO queries**

Use:

```sql
SELECT * FROM reading_progress ORDER BY updated_at_epoch_millis DESC, story_id ASC
```

```sql
SELECT * FROM canonical_chapters ORDER BY story_id ASC, canonical_chapter_id ASC
```

```sql
SELECT * FROM chapter_storage_entries
WHERE namespace = 'EXPLICIT_DOWNLOAD' AND download_state IS NOT NULL
ORDER BY updated_at_epoch_millis DESC, chapter_release_id ASC
```

- [ ] **Step 4: Update all fake implementations**

Every fake returns a `MutableStateFlow` for `observeAll()`; do not use `flowOf(emptyList())` where a test mutates state later.

- [ ] **Step 5: Prove schema stability**

```bash
./scripts/verify-room-schema-stability.sh
git diff --exit-code storage/room/schemas
```

- [ ] **Step 6: Run GREEN**

```bash
./gradlew :reader:test :chapters:test :downloads:test \
  :storage:room:connectedDebugAndroidTest --stacktrace
```

- [ ] **Step 7: Commit**

```bash
git add reader chapters downloads storage/room feature app/src/test
git commit -m "data: expose global reading activity streams"
```

---

### Task 8: Build the personal Home dashboard projection and screen

**Files:**
- Modify: `feature/catalog/build.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardUiState.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardProjector.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardViewModel.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardScreen.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/ContinueReadingCard.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/components/ReaderTarget.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardProjectorTest.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardViewModelTest.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardScreenshotTest.kt`
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardScreenTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`

**Interfaces:**
- Adds exact `:feature:catalog -> :reader` because personal Home consumes the public reading-progress port.
- Produces `ReaderTarget(storyId: StoryId, chapterId: CanonicalChapterId, releaseId: ChapterReleaseId)` for personal Home and Story Detail.

- [ ] **Step 1: Add RED architecture and projector tests**

Required projector tests:

```kotlin
latestIncompleteProgressPerStoryBecomesContinueReading()
completedProgressDoesNotBecomeContinueReading()
libraryStatusCreatesReadingPlannedPausedCompletedShelves()
latestMappedReleaseCreatesLibraryUpdate()
missingCatalogProjectionKeepsStoryVisibleWithStableFallback()
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :build-logic:test :feature:catalog:testDebugUnitTest --stacktrace
```

- [ ] **Step 3: Add the exact dependency edge**

Add `implementation(project(":reader"))` and update the exact architecture policy/test atomically. No other capability edge changes.

- [ ] **Step 4: Implement the pure projector**

```kotlin
data class HomeDashboardInput(
    val library: List<LibraryEntry>,
    val catalog: List<CatalogStoryProjection>,
    val progress: List<ReadingProgress>,
    val chapters: List<CanonicalChapterGroup>,
    val mappings: List<ContentMapping>,
    val downloads: List<DownloadRecord>,
)

data class ReaderTarget(
    val storyId: StoryId,
    val chapterId: CanonicalChapterId,
    val releaseId: ChapterReleaseId,
)

data class HomeDashboardUiState(
    val summary: HomeReadingSummary = HomeReadingSummary(),
    val continueReading: List<HomeDashboardItem> = emptyList(),
    val reading: List<HomeDashboardItem> = emptyList(),
    val planned: List<HomeDashboardItem> = emptyList(),
    val paused: List<HomeDashboardItem> = emptyList(),
    val completed: List<HomeDashboardItem> = emptyList(),
    val latestUpdates: List<HomeUpdateItem> = emptyList(),
    val loading: Boolean = true,
    val failure: HomeDashboardFailure? = null,
)

data class HomeDashboardFailure(val code: String, val retryable: Boolean)

class HomeDashboardProjector {
    fun project(input: HomeDashboardInput): HomeDashboardUiState
}
```

`HomeReadingSummary`, `HomeDashboardItem`, and `HomeUpdateItem` are immutable UI records in `HomeDashboardUiState.kt`; each story-bearing record contains `storyId`, `title`, and `coverUrl`, while only resumable records contain `ReaderTarget`. They must not contain Room entities or plugin-runtime types.

Sort continue-reading by progress update descending; updates by release published time descending, falling back to stable release ID.

- [ ] **Step 5: Implement the ViewModel**

Combine `LibraryService.observe`, catalog projections, progress `observeAll`, chapters `observeAll`, mappings `observeAll`, and downloads `observeAll`. Preserve latest content on observation failure and expose one non-blocking `HomeDashboardFailure`.

- [ ] **Step 6: Build the dashboard UI**

Order: local summary/backdrop, Continue Reading, Reading, Planned, Paused, Completed, Latest Updates. Omit empty secondary shelves. If every section is empty, show an action to Discover.

- [ ] **Step 7: Add navigation and screenshot coverage**

Resume calls:

```kotlin
AppRoute.Reader(storyId.value, chapterId.value, releaseId.value)
```

Record compact/large/medium dark, compact light, initial loading, and true-empty baselines. Instrumentation asserts shelf headings/cards expose TalkBack semantics and D-pad focus moves in visual order.

- [ ] **Step 8: Run GREEN**

```bash
./scripts/check-module-dependencies.sh
./gradlew :feature:catalog:testDebugUnitTest recordRoborazziDebug \
  :app:testDebugUnitTest --stacktrace
```

- [ ] **Step 9: Commit**

```bash
git add feature/catalog config/architecture app/src/main/kotlin/app/openstory/navigation \
  build-logic/src/test
git commit -m "feat: add personal reading home"
```

---

### Task 9: Redesign Library as a rich status collection

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryUiState.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryViewModel.kt`
- Replace: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryScreen.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryStoryCard.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryFilterBar.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/library/LibraryViewModelTest.kt`
- Modify: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/library/LibraryScreenTest.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/library/LibraryScreenshotTest.kt`

**Interfaces:**
- Produces `LibraryDisplayMode { GRID, LIST }`, query text, status counts, mapping filter, and progress enrichment.
- Persists display mode/query/filter in `SavedStateHandle`; Wave 10 may move durable preference ownership to typed settings.

- [ ] **Step 1: Write RED ViewModel tests**

Test title search, status count independent from selected filter, sort modes, mapping-state filter, latest progress per story, and process restoration from `SavedStateHandle`.

- [ ] **Step 2: Run RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest --tests '*LibraryViewModelTest' --stacktrace
```

- [ ] **Step 3: Extend state and combine inputs**

```kotlin
data class LibraryUiState(
    val items: List<LibraryItemUiModel> = emptyList(),
    val statusCounts: Map<LibraryStatus, Int> = emptyMap(),
    val selectedStatus: LibraryStatus? = null,
    val query: String = "",
    val sort: LibrarySort = LibrarySort.LAST_ACTIVITY,
    val displayMode: LibraryDisplayMode = LibraryDisplayMode.GRID,
    val sourceFilter: LibrarySourceState? = null,
)

enum class LibrarySort { LAST_ACTIVITY, TITLE, DATE_ADDED }
enum class LibraryDisplayMode { GRID, LIST }
```

Keep the existing `LibrarySourceState { SEARCHING, LINKED, REVIEW, NO_MAPPING, FAILED }`; filtering uses those exact values and does not introduce a second mapping-health vocabulary.

- [ ] **Step 4: Build responsive grid/list UI**

Compact and large-phone grids use two columns. Medium uses `GridCells.Adaptive(minSize = 144.dp)`, yielding three columns at 600dp after 16dp screen padding and grid spacing. List mode shows cover, title, status, progress, and mapping health. Floating navigation overlaps no final row; add navigation-bar-aware bottom content padding.

- [ ] **Step 5: Preserve empty distinctions**

True empty: `Your Library is empty` plus Discover action. Filtered/search empty: `No stories match these filters` plus clear-filter action.

- [ ] **Step 6: Add screenshots and semantics tests**

Record compact grid, compact list, medium grid, filtered-empty, dark/light. Assert status is readable without color, cover semantics contain title/progress, controls remain 48dp, and D-pad focus traverses filters before collection content.

- [ ] **Step 7: Run GREEN and commit**

```bash
./gradlew :feature:catalog:testDebugUnitTest recordRoborazziDebug \
  :feature:catalog:lintDebug --stacktrace
git add feature/catalog/src
git commit -m "feat: redesign status library"
```

---

### Task 10: Build adaptive Story Overview, Chapters, and Sources

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryUiState.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt`
- Replace: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryHero.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryOverview.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySources.kt`
- Modify: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryViewModelTest.kt`
- Modify: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/story/StoryScreenTest.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryScreenshotTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`

**Interfaces:**
- Produces `StorySection { OVERVIEW, CHAPTERS, SOURCES }`.
- Adds library membership/status and resume target to Story state without duplicating chapter/mapping state machines.

- [ ] **Step 1: Write RED aggregation tests**

Required cases:

```kotlin
heroPrefersSelectedSourceThenDeterministicBestArtwork()
overviewAggregatesAuthorsGenresAliasesWithoutDuplicates()
sourceSelectionDoesNotChangeConfirmedMapping()
libraryStatusChangeUsesLibraryService()
latestIncompleteProgressProducesResumeAction()
```

- [ ] **Step 2: Run RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest --tests '*StoryViewModelTest' --stacktrace
```

- [ ] **Step 3: Extend Story state**

Add nullable description, cover, score, authors, genres, language tags, library status, `ReaderTarget?`, and selected section using:

```kotlin
enum class StorySection { OVERVIEW, CHAPTERS, SOURCES }
```

Never synthesize an ongoing/releasing status if no source provides one.

- [ ] **Step 4: Implement compact Story**

Use shared artwork state for full backdrop and cover. Render primary actions `Read/Resume`, library status, and focused download action only when a readable release exists. Render section tabs below the hero.

- [ ] **Step 5: Implement medium Story**

At 600dp, use a two-pane layout: fixed hero/overview summary on the left and the active section on the right. Preserve one ViewModel and one section selection.

- [ ] **Step 6: Integrate existing chapter/mapping actions**

Pass existing `ChapterListUiState`, `ChapterListActions`, `MappingUiState`, and `MappingActions` into their selected section; do not copy their state into `StoryViewModel`.

- [ ] **Step 7: Add screenshot and behavior tests**

Record compact Overview/Sources/Chapters, large-phone Story, medium two-pane Story, missing-artwork fallback, and cached-error state. Assert section tabs/actions have 48dp targets, TalkBack announces the active section, and medium-pane focus order does not jump between panes.

- [ ] **Step 8: Run GREEN and commit**

```bash
./gradlew :feature:catalog:testDebugUnitTest recordRoborazziDebug \
  :app:testDebugUnitTest --stacktrace
git add feature/catalog/src app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt
git commit -m "feat: add rich adaptive story detail"
```

---

### Task 11: Redesign Search, mapping review, and chapter browsing

**Files:**
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchScreen.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchResultCard.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchFilters.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterReleaseRow.kt`
- Modify: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/search/SearchScreenTest.kt`
- Modify: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/mapping/MappingSheetTest.kt`
- Modify: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/chapters/ChapterListTest.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/search/SearchScreenshotTest.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/mapping/MappingScreenshotTest.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/chapters/ChapterListScreenshotTest.kt`

**Interfaces:**
- Reuses Task 3 artwork and Task 4 glass/content primitives.
- Does not change search, mapping, aggregation, download, or reader domain behavior.

- [ ] **Step 1: Lock current behavior with RED visual assertions**

Add tests proving partial search results survive a plugin failure; source, content-type, language, and genre selections remain keyed by the source-provided `CatalogSearchFilterGroup`/filter ID; mapping approval/rejection callbacks remain distinct; chapter filters retain counts; and read/download actions keep release identity.

- [ ] **Step 2: Rebuild focused Search**

Use a glass search field, source-grouped horizontal filter chips rendered from the existing runtime descriptors, artwork result rows/cards, per-source metadata, and inline source failure. Do not hardcode a filter unsupported by the selected plugin. No bottom navigation.

- [ ] **Step 3: Rebuild mapping review**

Show linked sources first, then candidate search/URL resolution. Each candidate includes plugin, score, evidence, language/source URL when present, explicit approve/reject, and no automatic UI-side mapping mutation.

- [ ] **Step 4: Rebuild chapter browsing**

Use compact rows with chapter label, release/source/language/freshness, read state, and download action. Preserve grouped-release expansion and tombstone filters.

- [ ] **Step 5: Record flow screenshots**

Record Search, Sources, Mapping, Chapters, partial-source failure, and offline cached Chapters at 360x800. Assert keyboard submission/filter navigation works, candidate approval/rejection has distinct labels, and every chapter action meets 48dp.

- [ ] **Step 6: Run GREEN and commit**

```bash
./gradlew :feature:catalog:testDebugUnitTest recordRoborazziDebug \
  :feature:catalog:lintDebug --stacktrace
git add feature/catalog/src
git commit -m "refactor: align search sources and chapters ui"
```

---

### Task 12: Add real Downloads and Updates utility flows

**Files:**
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/activity/LibraryActivityProjector.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsUiState.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsViewModel.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreen.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesUiState.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesViewModel.kt`
- Create: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesScreen.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/activity/LibraryActivityProjectorTest.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/downloads/DownloadsViewModelTest.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreenshotTest.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/updates/UpdatesViewModelTest.kt`
- Create: `feature/catalog/src/test/kotlin/app/openstory/catalog/ui/updates/UpdatesScreenshotTest.kt`
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreenTest.kt`
- Create: `feature/catalog/src/androidTest/kotlin/app/openstory/catalog/ui/updates/UpdatesScreenTest.kt`
- Modify: `app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt`
- Modify: `app/src/main/kotlin/app/openstory/ui/HikariUtilitySheet.kt`

**Interfaces:**
- Downloads is a projection over `DownloadRepository.observeAll()` plus chapter/catalog metadata.
- Updates is a projection over Library + mappings + chapter releases; it is not a notification inbox.

- [ ] **Step 1: Write RED projector/ViewModel tests**

Downloads tests: queued/running/completed/failed grouping, retry/remove identity, missing release metadata retained. Updates tests: library-only filtering, newest release first, duplicate release suppression, story navigation identity.

- [ ] **Step 2: Run RED**

```bash
./gradlew :feature:catalog:testDebugUnitTest --tests '*Downloads*' --tests '*Updates*' --stacktrace
```

- [ ] **Step 3: Implement shared activity projection**

`LibraryActivityProjector` owns only pure joins and sorting used by Home and Updates. Refactor Task 8 Home to consume it; do not duplicate release-to-story lookup.

- [ ] **Step 4: Build Downloads UI**

Sections: Active, Completed, Failed. Show story/chapter/source, bytes when known, and failure text. `DownloadsViewModel` injects `DownloadRepository`, `DownloadService`, and `DownloadScheduler`; retry queues and schedules the release, cancel cancels the scheduler then service record, and remove reuses the existing destructive confirmation before calling the current cancellation/removal consequence keyed by `ChapterReleaseId`.

- [ ] **Step 5: Build Updates UI**

Group by date label, show story cover/title, chapter/release/source/language, and navigate to Story or Reader only with valid identities.

- [ ] **Step 6: Wire utility routes**

`HikariUtilitySheet` exposes Downloads and Updates. Close the sheet before navigation. Back returns to the originating top-level destination.

- [ ] **Step 7: Add screenshots and run GREEN**

```bash
./gradlew :feature:catalog:testDebugUnitTest recordRoborazziDebug \
  :app:testDebugUnitTest --stacktrace
```

- [ ] **Step 8: Commit**

```bash
git add feature/catalog/src app/src/main/kotlin/app/openstory
git commit -m "feat: add downloads and reading updates flows"
```

---

### Task 13: Redesign Reader chrome without reducing the reading viewport

**Files:**
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderScreen.kt`
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderControls.kt`
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderContent.kt`
- Modify: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReleaseSwitcher.kt`
- Create: `feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderSettingsSheet.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderViewModelTest.kt`
- Modify: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderContentTest.kt`
- Modify: `feature/reader/src/androidTest/kotlin/app/openstory/reader/ui/ReaderScreenTest.kt`
- Create: `feature/reader/src/test/kotlin/app/openstory/reader/ui/ReaderScreenshotTest.kt`

**Interfaces:**
- Keeps `ReaderActions` identities and progress persistence unchanged.
- Secondary controls live in a sheet; main content remains edge-to-edge.

- [ ] **Step 1: Write RED behavior tests**

Prove tap toggles chrome, previous/next retains canonical chapter ID, release switch retains release ID, font controls call existing actions, progress flush occurs on lifecycle/back, and content remains present when controls hide.

- [ ] **Step 2: Run RED**

```bash
./gradlew :feature:reader:testDebugUnitTest --stacktrace
```

- [ ] **Step 3: Implement top and bottom glass chrome**

Top: back, chapter/story context, typography/settings action. Bottom: progress, previous/next. Use local `HikariBackdropHost`; API 26 gets translucent fallback.

- [ ] **Step 4: Move secondary controls into `ReaderSettingsSheet`**

Include release/source selection, font size, download state, and supported typography options only. Do not add unsupported themes, margins, or pagination modes.

- [ ] **Step 5: Preserve readable text behavior**

Keep document block rendering and position callbacks. Apply typography tokens only where they do not change saved block/offset identity.

- [ ] **Step 6: Record screenshots**

Record chrome visible/hidden, settings sheet, loading, error, dark/light, API 26 fallback, and reduce-motion mode. Assert reader text remains usable at 200% font scale and hidden chrome is absent from the semantics/focus tree.

- [ ] **Step 7: Run GREEN and commit**

```bash
./gradlew :feature:reader:testDebugUnitTest recordRoborazziDebug \
  :feature:reader:lintDebug --stacktrace
git add feature/reader/src
git commit -m "feat: redesign immersive reader controls"
```

---

### Task 14: Align golden baselines and replace the supplied target ZIP

**Files:**
- Modify: target source under `tools/ui-target/src`
- Create/update: Roborazzi baselines under each module's configured baseline directory
- External output: `E:\Downloads\Hikari-UI-Target-Pack.zip`

**Interfaces:**
- Consumes implemented screens from Tasks 6-13.
- Produces a target pack that matches real routes and state semantics.

- [ ] **Step 1: Run all screenshot tests in compare mode**

```bash
./gradlew compareRoborazziDebug --stacktrace
```

Review every diff against the approved spec and the three images under
`docs/ui/references/product-ui/`; do not record over a regression without tracing it to
an approved requirement.

- [ ] **Step 2: Update target HTML to match final component geometry**

Synchronize navigation height, card ratios, typography scale, breakpoints, empty/error copy, Story sections, and Reader chrome. Do not introduce target-only controls.

- [ ] **Step 3: Record accepted baselines**

```bash
./gradlew recordRoborazziDebug --stacktrace
./gradlew verifyRoborazziDebug --stacktrace
```

- [ ] **Step 4: Generate and inspect the final ZIP**

```powershell
powershell -ExecutionPolicy Bypass -File tools/ui-target/package-ui-target.ps1 `
  -Output 'E:\Downloads\Hikari-UI-Target-Pack.zip'
```

Verify the archive includes all required PNGs and README, and that no ReDantotsu screenshots/reference assets are present.

- [ ] **Step 5: Commit tracked source/baselines only**

```bash
git add tools/ui-target core/designsystem feature/catalog feature/reader
git commit -m "test: accept responsive product ui baselines"
```

The external ZIP is reported to the user but not added to Git.

---

### Task 15: Rebaseline Wave 10 Settings and Wave 11 Plugin-management continuity

**Files:**
- Modify: `docs/implementation/current-roadmap.md`
- Modify: `docs/implementation/waves/wave-10-background-sync-auth-and-notifications.md`
- Modify: `docs/implementation/waves/wave-11-hardening-open-source-release.md`
- Modify: `docs/superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`
- Modify: `scripts/tests/post-baseline-wave-roadmap-test.sh`

**Interfaces:**
- Wave 10 Task 6 adds Settings to `HikariUtilitySheet` and uses the new design system.
- Wave 11 Task 1 adds Plugins to `HikariUtilitySheet` and implements the target-pack Plugin screen.

- [ ] **Step 1: Add RED roadmap-contract assertions**

Assert active plans contain:

```text
Wave 10 Settings enters through the avatar utility sheet and never top-level navigation.
Wave 11 Plugin Management enters through the avatar utility sheet and never top-level navigation.
Discover / Home / Library remains the final top-level model.
```

- [ ] **Step 2: Run RED**

```bash
bash scripts/tests/post-baseline-wave-roadmap-test.sh
```

- [ ] **Step 3: Amend Wave 10 Task 6 exactly**

Require `feature:settings` to consume Hikari artwork/glass/content primitives, add `AppRoute.Settings` composition, add the Settings utility row, and preserve typed settings ownership. It must not add Settings to `TopLevelDestination`.

- [ ] **Step 4: Amend Wave 11 Task 1 exactly**

Require `feature:plugins` to implement installed/detail/install/update/rollback flows over the public runtime facade, match the approved Plugin target screen, add the Plugins utility row, and preserve the three top-level routes.

- [ ] **Step 5: Update the architecture continuity text**

Record that the current UI checkpoint intentionally ships Downloads/Updates first and reserves Settings/Plugins for their capability waves. Do not mark Wave 10 or 11 complete.

- [ ] **Step 6: Run GREEN and commit**

```bash
bash scripts/tests/post-baseline-wave-roadmap-test.sh
./gradlew :build-logic:test --stacktrace
git add docs/implementation docs/superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md \
  scripts/tests/post-baseline-wave-roadmap-test.sh
git commit -m "docs: align future waves with product ui"
```

---

### Task 16: Run the product UI checkpoint and deep review

**Files:**
- Create: `docs/internal/checkpoints/product-ui-redesign.md`

**Interfaces:**
- Consumes all current-graph tasks.
- Produces acceptance evidence without claiming future Wave 10/11 Settings/Plugins are implemented.

- [ ] **Step 1: Capture clean environment evidence**

```bash
git status --short
git log -20 --oneline
java -version
./gradlew --version
```

- [ ] **Step 2: Run host verification**

```bash
./scripts/check-module-dependencies.sh
./scripts/verify-current-architecture.sh
./gradlew verifyRoborazziDebug --stacktrace
./scripts/verify.sh
```

Expected: 14 modules, schema 1..6 stable, screenshot baselines match, full verification exit 0.

- [ ] **Step 3: Run API 26 instrumentation sequentially**

```bash
./gradlew :core:designsystem:connectedDebugAndroidTest --stacktrace
./gradlew :feature:catalog:connectedDebugAndroidTest --stacktrace
./gradlew :feature:reader:connectedDebugAndroidTest --stacktrace
./gradlew :app:connectedDebugAndroidTest --stacktrace
```

Confirm translucent fallback, responsive scrolling, and critical flows.

- [ ] **Step 4: Run current-API instrumentation sequentially**

Run the same four commands on API 37. Confirm real blur regions, no UTP/ADB contention, and no live-network failure is misreported as a UI failure.

- [ ] **Step 5: Perform deep product review**

Review dark/light and all sizes for:

```text
Discover density and partial failures
Home resume identity and empty setup
Library filters/grid/list
Search partial results
Story Overview/Chapters/Sources and medium two-pane layout
Mapping confirmation safety
Downloads retry/remove
Updates relevance/order
Reader chrome/settings/progress
floating navigation visibility
utility back stack
API 26 fallback
TalkBack labels and 48dp targets
keyboard/D-pad focus order and 200% font scaling
reduce-motion fallbacks
```

- [ ] **Step 6: Verify the external target pack**

```powershell
powershell -ExecutionPolicy Bypass -File scripts/tests/ui-target-pack-test.ps1
Get-FileHash 'E:\Downloads\Hikari-UI-Target-Pack.zip' -Algorithm SHA256
```

- [ ] **Step 7: Write checkpoint evidence**

Create `docs/internal/checkpoints/product-ui-redesign.md` with exact date, commit, commands/results, API devices, visual review results, target-pack SHA-256, current utility availability (`Downloads`, `Updates`), and deferred owners (`Settings` Wave 10, `Plugins` Wave 11).

Set `Status: ACCEPTED` only after every required command and review passes.

- [ ] **Step 8: Final verify and commit**

```bash
./scripts/verify.sh
./gradlew verifyRoborazziDebug --stacktrace
git add docs/internal/checkpoints/product-ui-redesign.md
git commit -m "docs: accept product ui redesign checkpoint"
```

---

## Checkpoint Acceptance

- [ ] Reproducible target renderer and ZIP packaging exist.
- [ ] Supplied ZIP at `E:\Downloads\Hikari-UI-Target-Pack.zip` is replaced and checksummed.
- [ ] No ReDantotsu code or assets exist in tracked files or target output.
- [ ] `:core:designsystem` still has zero project dependencies.
- [ ] Artwork cover/backdrop share one remembered state/cache identity.
- [ ] API 26 translucent fallback and API 31+ blur use the same geometry.
- [ ] Top-level destinations are exactly Discover, Home, Library.
- [ ] Discover and personal Home are separate projections and screens.
- [ ] Library supports status/search/sort/mapping and grid/list presentation.
- [ ] Story provides Overview/Chapters/Sources with no implicit remapping.
- [ ] Search, mapping, chapter, download, and Reader actions preserve stable IDs.
- [ ] Downloads and Updates are real utility routes.
- [ ] Settings and Plugin management remain assigned to Wave 10/11 and are not fake current routes.
- [ ] Compact, large-phone, medium, dark/light, and UX-state golden coverage passes.
- [ ] Reduce-motion, keyboard/D-pad order, TalkBack, 200% font scaling, and 48dp target checks pass.
- [ ] Room schema 1..6 remains unchanged.
- [ ] Architecture remains at 14 modules.
- [ ] `./scripts/verify.sh`, Roborazzi verification, API 26, and API 37 instrumentation pass.
- [ ] Deep product/visual/accessibility review is recorded from actual evidence.

## Execution Order

Execute Tasks 1-16 in order. Tasks 1-4 establish shared infrastructure; Tasks 5-13 change the product; Task 14 accepts the visual outputs; Task 15 protects Wave continuity; Task 16 closes the checkpoint. Do not parallelize changes to `AppNavHost`, exact architecture policy, repository interfaces, or Roborazzi configuration without one integrator.

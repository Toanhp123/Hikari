<!--
DOCUMENT LIFECYCLE
Status: HISTORICAL / IMPLEMENTATION PRESENT
Current repository note: Source-local Wave 01 remediation supersedes greenfield execution details. Keep this plan for product intent and original acceptance coverage.
Canonical execution status: ../../project/current-state.md
Original planning text below is preserved rather than retroactively rewritten.
-->

# Wave 01 — Foundation and Architecture Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` (recommended) or `superpowers:executing-plans` to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Establish a reproducible Android build, enforce module boundaries, and launch a testable Compose shell.

**Architecture:** A small composition-root app depends on platform-neutral common/model modules. Convention plugins centralize build policy, Navigation 3 owns route state, and Hilt provides explicit Android adapters without leaking Android types into the domain.

**Tech Stack:** Gradle 9.5, AGP 9.3.0, Kotlin 2.4.10, JDK 17, Compose, Navigation 3, Hilt, JUnit/Kotlin test.

## Global Constraints

- Android-only MVP; no account, cloud sync, remote chapter service, or push backend.
- Package namespace: `app.openstory`.
- Minimum SDK: 26. Compile and target SDK: 37.
- Build runtime: JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0.
- Language/UI: Kotlin 2.4.10, Jetpack Compose BOM 2026.06.00, Navigation 3 version 1.1.4.
- Persistence/background: Room 2.8.4 and WorkManager 2.11.2.
- Concurrency/serialization: Kotlin coroutines 1.11.0 and kotlinx.serialization 1.11.0.
- Dependency injection: Hilt 2.60.1.
- JavaScript plugins execute only through AndroidX JavaScriptEngine 1.1.0 with host-controlled capabilities.
- Catalog metadata and readable content remain separate plugin responsibilities.
- Reading progress belongs to `CanonicalChapter`; exact `ChapterRelease` and reader position are also retained.
- No native-code plugins, unrestricted filesystem access, arbitrary Android APIs, or undeclared network domains.
- Every persistence change needs a migration test; every plugin contract needs deterministic fixtures.
- TDD is mandatory: demonstrate the focused failure, implement the smallest behavior, run focused tests, then run the module suite.
- Commit after each task. Do not combine tasks across checkpoints.
- Any deterministic `*Fixture`, fake, or test assertion helper shown in a test block is created in that task’s listed test file or `:test:fixtures`; it must not call live websites.


## Role of This Wave

This wave creates the guardrails all later work relies on. It deliberately avoids product data, plugins, and real screens; its job is to make wrong dependency directions and untestable global state difficult to introduce.

## Entry Dependencies

- Approved design spec is present at `docs/superpowers/specs/2026-08-03-android-unified-novel-library-design.md`.
- Android SDK 37 and JDK 17 are installed.
- The repository is clean or work occurs in an isolated worktree.

## Exit Deliverables

- Reproducible Gradle project and version catalog.
- Module boundary checks and typed common primitives.
- Launchable Compose/Navigation shell.
- Hilt application root and testable dispatcher interface.
- CI and one-command local verification.

## File/Module Boundary

Each path listed in a task owns one responsibility. Do not move business rules into Compose screens, Room entities, JavaScript snippets, or WorkManager classes. Domain interfaces are the dependency boundary; Android adapters implement them.

---

### Task 1: Bootstrap reproducible Gradle build and module graph

**Files:**
- Create: settings.gradle.kts
- Create: build.gradle.kts
- Create: gradle/libs.versions.toml
- Create: gradle.properties
- Create: app/build.gradle.kts
- Create: core/common/build.gradle.kts
- Create: core/model/build.gradle.kts
- Create: test/fixtures/build.gradle.kts
- Test: build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt

**Interfaces:**
- Consumes: Approved module map and global version pins.
- Produces: A Gradle project where `:app`, `:core:common`, `:core:model`, and `:test:fixtures` configure under JDK 17 with no circular dependencies.

**Acceptance:**
- `./gradlew projects` lists the four bootstrap modules.
- All dependency versions come from `libs.versions.toml`.
- `:core:model` depends on no Android UI module.
- The build rejects Java runtimes other than 17.

**Implementation notes:**
- Create `build-logic` as an included build with focused convention plugins for Android application, Android library, Kotlin/JVM, Compose, Hilt, and Room.
- Pin SDK values in convention plugins instead of repeating them in feature modules.
- Add `org.gradle.jvmargs`, configuration cache, parallel execution, and Kotlin incremental compilation in `gradle.properties`.

- [ ] **Step 1: Write the failing test**

Create `build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt`:

```kotlin
package app.openstory.build

import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

class ModuleGraphTest {
    @Test fun settingsContainsBootstrapModules() {
        val settings = File("../settings.gradle.kts").readText()
        listOf(":app", ":core:common", ":core:model", ":test:fixtures").forEach {
            assertTrue("include(\"$it\")" in settings, "Missing module $it")
        }
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :build-logic:test --tests app.openstory.build.ModuleGraphTest.settingsContainsBootstrapModules
```

Expected: **FAIL** because the project and included modules do not exist yet.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories { google(); mavenCentral(); gradlePluginPortal() }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "OpenStory"
include(":app")
include(":core:common")
include(":core:model")
include(":test:fixtures")
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :build-logic:test --tests app.openstory.build.ModuleGraphTest.settingsContainsBootstrapModules
./gradlew projects :app:assembleDebug
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml gradle.properties app/build.gradle.kts core/common/build.gradle.kts core/model/build.gradle.kts test/fixtures/build.gradle.kts build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt
git commit -m "build: bootstrap modular Android project"
```

### Task 2: Define common result, typed error, clock, and identifier primitives

**Files:**
- Create: core/common/src/main/kotlin/app/openstory/common/AppError.kt
- Create: core/common/src/main/kotlin/app/openstory/common/AppResult.kt
- Create: core/common/src/main/kotlin/app/openstory/common/AppResultExtensions.kt
- Create: core/common/src/main/kotlin/app/openstory/common/Clock.kt
- Create: core/common/src/main/kotlin/app/openstory/common/StableId.kt
- Test: core/common/src/test/kotlin/app/openstory/common/AppResultTest.kt

**Interfaces:**
- Consumes: Kotlin standard library only.
- Produces: `AppResult<T>`, non-secret `AppError`, injectable `Clock`, and validated `StableId` used by every later module.

**Acceptance:**
- Errors carry stable code, retryability, and safe diagnostic detail.
- No exception message, URL query, cookie, or chapter body is exposed by `AppError.toString()`.
- Stable IDs reject blank or whitespace-containing values.

**Implementation notes:**
- Use stable machine-readable error codes such as `plugin.domain_denied`; UI localization happens later.
- Provide `SystemClock` and `FakeClock` so synchronization tests never sleep.
- Use inline value classes for identifiers only in `core:model`; `StableId` centralizes validation.

- [ ] **Step 1: Write the failing test**

Create `core/common/src/test/kotlin/app/openstory/common/AppResultTest.kt`:

```kotlin
package app.openstory.common

import kotlin.test.Test
import kotlin.test.assertEquals

class AppResultTest {
    @Test fun mapPreservesTypedFailure() {
        val error = AppError.Network(code = "network.timeout", retryable = true)
        val result: AppResult<Int> = AppResult.Failure(error)
        assertEquals(AppResult.Failure(error), result.map { it * 2 })
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:common:test --tests app.openstory.common.AppResultTest.mapPreservesTypedFailure
```

Expected: **FAIL** because `AppResult` and `AppError` are undefined.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/common/src/main/kotlin/app/openstory/common/AppResult.kt`:

```kotlin
package app.openstory.common

sealed interface AppResult<out T> {
    data class Success<T>(val value: T) : AppResult<T>
    data class Failure(val error: AppError) : AppResult<Nothing>

    fun <R> map(transform: (T) -> R): AppResult<R> = when (this) {
        is Success -> Success(transform(value))
        is Failure -> this
    }
    fun <R> flatMap(transform: (T) -> AppResult<R>): AppResult<R> = when (this) {
        is Success -> transform(value)
        is Failure -> this
    }
    fun getOrNull(): T? = (this as? Success<T>)?.value
    suspend fun tapSuccess(block: suspend (T) -> Unit): AppResult<T> = apply {
        if (this is Success) block(value)
    }
    suspend fun tapFailure(block: suspend (AppError) -> Unit): AppResult<T> = apply {
        if (this is Failure) block(error)
    }
}

sealed interface AppError {
    val code: String
    val retryable: Boolean
    data class Network(override val code: String, override val retryable: Boolean) : AppError
    data class Validation(override val code: String, override val retryable: Boolean = false) : AppError
    data class Storage(override val code: String, override val retryable: Boolean) : AppError
    data class Plugin(override val code: String, override val retryable: Boolean) : AppError
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:common:test --tests app.openstory.common.AppResultTest.mapPreservesTypedFailure
./gradlew :core:common:test
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/common/src/main/kotlin/app/openstory/common/AppError.kt core/common/src/main/kotlin/app/openstory/common/AppResult.kt core/common/src/main/kotlin/app/openstory/common/Clock.kt core/common/src/main/kotlin/app/openstory/common/StableId.kt core/common/src/test/kotlin/app/openstory/common/AppResultTest.kt
git commit -m "core: add typed result and error primitives"
```

### Task 3: Create Compose application shell with type-safe Navigation 3 routes

**Files:**
- Create: app/src/main/kotlin/app/openstory/OpenStoryApplication.kt
- Create: app/src/main/kotlin/app/openstory/MainActivity.kt
- Create: app/src/main/kotlin/app/openstory/navigation/AppRoute.kt
- Create: app/src/main/kotlin/app/openstory/navigation/OpenStoryNavDisplay.kt
- Create: app/src/main/kotlin/app/openstory/ui/OpenStoryApp.kt
- Test: app/src/test/kotlin/app/openstory/navigation/AppRouteSerializationTest.kt

**Interfaces:**
- Consumes: Bootstrap Gradle project and kotlinx.serialization.
- Produces: Serializable routes for Home, Library, Story, Reader, Plugins, and Settings plus a launchable Compose shell.

**Acceptance:**
- Routes contain stable IDs rather than whole domain objects.
- Back stack state is serializable across process recreation.
- Bottom navigation exposes Home, Library, and Plugins without feature logic.

**Implementation notes:**
- Use Navigation 3 runtime/UI and a remembered serializable back stack.
- Place only placeholders in each destination; feature screens arrive in later waves.
- Enable edge-to-edge and Material 3, but do not establish product colors before visual design review.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/app/openstory/navigation/AppRouteSerializationTest.kt`:

```kotlin
package app.openstory.navigation

import kotlinx.serialization.json.Json
import kotlin.test.Test
import kotlin.test.assertEquals

class AppRouteSerializationTest {
    @Test fun storyRouteRoundTrips() {
        val route: AppRoute = AppRoute.Story("story_123")
        val encoded = Json.encodeToString(AppRoute.serializer(), route)
        assertEquals(route, Json.decodeFromString(AppRoute.serializer(), encoded))
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :app:testDebugUnitTest --tests app.openstory.navigation.AppRouteSerializationTest.storyRouteRoundTrips
```

Expected: **FAIL** because `AppRoute` and its serializer do not exist.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `app/src/main/kotlin/app/openstory/navigation/AppRoute.kt`:

```kotlin
package app.openstory.navigation

import kotlinx.serialization.Serializable

@Serializable
sealed interface AppRoute {
    @Serializable data object Home : AppRoute
    @Serializable data object Library : AppRoute
    @Serializable data object Plugins : AppRoute
    @Serializable data object Settings : AppRoute
    @Serializable data class Story(val storyId: String) : AppRoute
    @Serializable data class Reader(val chapterId: String, val releaseId: String?) : AppRoute
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :app:testDebugUnitTest --tests app.openstory.navigation.AppRouteSerializationTest.storyRouteRoundTrips
./gradlew :app:testDebugUnitTest :app:assembleDebug
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add app/src/main/kotlin/app/openstory/OpenStoryApplication.kt app/src/main/kotlin/app/openstory/MainActivity.kt app/src/main/kotlin/app/openstory/navigation/AppRoute.kt app/src/main/kotlin/app/openstory/navigation/OpenStoryNavDisplay.kt app/src/main/kotlin/app/openstory/ui/OpenStoryApp.kt app/src/test/kotlin/app/openstory/navigation/AppRouteSerializationTest.kt
git commit -m "app: add compose navigation shell"
```

### Task 4: Install dependency injection and explicit coroutine dispatcher boundaries

**Files:**
- Create: core/common/src/main/kotlin/app/openstory/common/dispatchers/AppDispatchers.kt
- Create: app/src/main/kotlin/app/openstory/di/CoroutineModule.kt
- Modify: app/src/main/kotlin/app/openstory/OpenStoryApplication.kt
- Test: core/common/src/test/kotlin/app/openstory/common/dispatchers/AppDispatchersTest.kt

**Interfaces:**
- Consumes: Common primitives and Hilt convention plugin.
- Produces: `AppDispatchers` interface with injected Default, IO, and Main dispatchers; Hilt application root.

**Acceptance:**
- Repositories never call `Dispatchers.IO` directly.
- Unit tests can supply `StandardTestDispatcher`.
- Application class is the only Hilt process root.

**Implementation notes:**
- Annotate `OpenStoryApplication` with `@HiltAndroidApp` and `MainActivity` with `@AndroidEntryPoint`.
- Provide dispatchers through one singleton module; feature modules consume the interface.
- Enable the coroutine test library only in test configurations.

- [ ] **Step 1: Write the failing test**

Create `core/common/src/test/kotlin/app/openstory/common/dispatchers/AppDispatchersTest.kt`:

```kotlin
package app.openstory.common.dispatchers

import kotlinx.coroutines.test.StandardTestDispatcher
import kotlin.test.Test
import kotlin.test.assertSame

class AppDispatchersTest {
    @Test fun fakeDispatchersExposeProvidedInstances() {
        val dispatcher = StandardTestDispatcher()
        val fake = FixedAppDispatchers(dispatcher, dispatcher, dispatcher)
        assertSame(dispatcher, fake.io)
        assertSame(dispatcher, fake.default)
        assertSame(dispatcher, fake.main)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :core:common:test --tests app.openstory.common.dispatchers.AppDispatchersTest.fakeDispatchersExposeProvidedInstances
```

Expected: **FAIL** because dispatcher abstractions are absent.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `core/common/src/main/kotlin/app/openstory/common/dispatchers/AppDispatchers.kt`:

```kotlin
package app.openstory.common.dispatchers

import kotlinx.coroutines.CoroutineDispatcher

data class FixedAppDispatchers(
    override val io: CoroutineDispatcher,
    override val default: CoroutineDispatcher,
    override val main: CoroutineDispatcher,
) : AppDispatchers

interface AppDispatchers {
    val io: CoroutineDispatcher
    val default: CoroutineDispatcher
    val main: CoroutineDispatcher
}
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :core:common:test --tests app.openstory.common.dispatchers.AppDispatchersTest.fakeDispatchersExposeProvidedInstances
./gradlew :core:common:test :app:testDebugUnitTest
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add core/common/src/main/kotlin/app/openstory/common/dispatchers/AppDispatchers.kt app/src/main/kotlin/app/openstory/di/CoroutineModule.kt app/src/main/kotlin/app/openstory/OpenStoryApplication.kt core/common/src/test/kotlin/app/openstory/common/dispatchers/AppDispatchersTest.kt
git commit -m "build: add hilt and coroutine dispatcher boundaries"
```

### Task 5: Add repository quality gates and continuous integration

**Files:**
- Create: .github/workflows/android.yml
- Create: config/detekt/detekt.yml
- Create: scripts/verify.sh
- Create: scripts/check-module-dependencies.sh
- Create: README.md
- Test: app/src/test/kotlin/app/openstory/ArchitectureSmokeTest.kt

**Interfaces:**
- Consumes: Compiling bootstrap app and module graph.
- Produces: A single local/CI verification command enforcing tests, lint, static analysis, dependency boundaries, and debug assembly.

**Acceptance:**
- CI runs on pull requests and pushes to the main branch.
- Gradle dependency verification and wrapper checksum are committed.
- The architecture check fails if `core:model` imports Android or Compose APIs.
- README documents exact JDK/SDK bootstrap and verification commands.

**Implementation notes:**
- Use `actions/setup-java` with Temurin 17 and Gradle official setup action.
- Cache only through the Gradle action; do not hand-cache mutable build directories.
- Upload lint and test reports on failure, but never upload signing material.

- [ ] **Step 1: Write the failing test**

Create `app/src/test/kotlin/app/openstory/ArchitectureSmokeTest.kt`:

```kotlin
package app.openstory

import java.io.File
import kotlin.test.Test
import kotlin.test.assertFalse

class ArchitectureSmokeTest {
    @Test fun coreModelStaysPlatformIndependent() {
        val source = File("../core/model/src/main").walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .joinToString("
") { it.readText() }
        assertFalse("android." in source)
        assertFalse("androidx.compose" in source)
    }
}
```

- [ ] **Step 2: Run the focused test and verify the intended failure**

```bash
./gradlew :app:testDebugUnitTest --tests app.openstory.ArchitectureSmokeTest.coreModelStaysPlatformIndependent
```

Expected: **FAIL** because the architecture test and verification scripts are not present.

- [ ] **Step 3: Add the minimal implementation**

Create or modify `scripts/verify.sh`:

```kotlin
#!/usr/bin/env bash
set -euo pipefail
./gradlew --no-daemon \
  testDebugUnitTest \
  lintDebug \
  detekt \
  :app:assembleDebug \
  --stacktrace
./scripts/check-module-dependencies.sh
```

- [ ] **Step 4: Re-run focused and module tests**

```bash
./gradlew :app:testDebugUnitTest --tests app.openstory.ArchitectureSmokeTest.coreModelStaysPlatformIndependent
bash scripts/verify.sh
```

Expected: both commands finish with **BUILD SUCCESSFUL** and the new test passes.

- [ ] **Step 5: Commit the independently reviewable change**

```bash
git add .github/workflows/android.yml config/detekt/detekt.yml scripts/verify.sh scripts/check-module-dependencies.sh README.md app/src/test/kotlin/app/openstory/ArchitectureSmokeTest.kt
git commit -m "ci: enforce android verification gates"
```

## Wave Checkpoint

Do not begin `2026-08-03-02-domain-and-local-storage.md` until every item below is demonstrated on a clean checkout:

- [ ] Fresh clone configures with JDK 17 and no local-only files.
- [ ] `./gradlew projects` shows the intended bootstrap module graph.
- [ ] Debug APK launches and back navigation works across placeholder routes.
- [ ] CI executes the same verification command as local development.
- [ ] No Android/Compose imports exist under `core:model`.

## Full Verification

```bash
./gradlew clean testDebugUnitTest lintDebug --stacktrace
```

Expected: **BUILD SUCCESSFUL**, no ignored failing tests, no unresolved lint errors, and no generated database schema drift.

## Review Packet

Attach to the checkpoint review:

- Commit range for this wave.
- Focused test output for every task.
- Full verification output.
- Any deliberate deviations from the approved design, with rationale and updated spec text.
- Screenshots or screen recordings only when the wave changes visible UI.

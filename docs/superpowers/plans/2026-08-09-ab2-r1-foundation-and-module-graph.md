# Architecture Baseline 2 R1 - Foundation and Module Graph Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the Baseline 2 production module shells, move stable cross-capability identities into common ownership, and make architecture policy express allowed direction instead of the accidental legacy graph.

**Architecture:** Target modules are introduced beside legacy slices. New target modules must obey final dependency direction immediately; legacy modules remain temporary until their named replacement checkpoint.

**Tech Stack:** Gradle 9.5, custom build-logic, Kotlin/JVM, Android library conventions, existing Android/Kotlin convention plugins; R1 itself adds no Hilt, Room, or Compose behavior.

## Global Constraints

- Architecture source of truth: `docs/superpowers/specs/2026-08-09-architecture-baseline-2-design.md`.
- This work is pre-Wave-06; do not implement Library, chapter sync, Reader, downloads, background sync, authentication, notifications, or release-hardening behavior.
- Android-native Kotlin remains fixed.
- Package namespace/application ID remains `app.openstory`.
- Minimum SDK remains 26; compile/target SDK remain 37 unless a dedicated architecture decision changes them.
- Build runtime remains JDK 17, Gradle 9.5, Android Gradle Plugin 9.3.0, Kotlin 2.4.10.
- Current retained libraries may be replaced only when a plan task explicitly does so; do not change versions opportunistically during this reset.
- Pre-MVP compatibility is intentionally breakable. Do not add permanent `Legacy*`, `Compat*`, `V1/V2` adapters, dual mappers, or Room migrations merely to preserve development-only contracts.
- Temporary migration-scoped bridges are allowed only when this plan names the bridge and its deletion task explicitly.
- Package-first, Gradle-module-second: do not create extra production modules beyond the approved target graph without a new architecture decision.
- TDD is mandatory for behavior changes: focused RED -> smallest GREEN -> affected module suite -> commit.
- Every task ends in a buildable, testable, independently reviewable repository state.
- Do not make a checkpoint green with `TODO()`, `error("not implemented")`, unconditional empty production results, or broad structural suppressions.
- Tests protect revalidated product/security invariants, not historical class shapes.
- Production Room entities/DAOs stay private to the storage adapter.
- Production plugin JavaScript receives only host-controlled capabilities and never Android `Context`, Room, filesystem paths, raw OkHttp, reflection, or plaintext managed credentials.

---
### Task 1: Make module-boundary policy directional instead of requiring every allowed edge to be present

**Files:**
- Modify: `build-logic/src/main/kotlin/app/openstory/build/architecture/ModuleBoundaryModels.kt`
- Modify: `build-logic/src/main/kotlin/app/openstory/build/architecture/ModuleBoundaryPolicyLoader.kt`
- Modify: `build-logic/src/main/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifier.kt`
- Modify: `build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryPolicyLoaderTest.kt`
- Modify: `build-logic/src/test/kotlin/app/openstory/build/architecture/ModuleBoundaryVerifierTest.kt`
- Modify: `config/architecture/module-boundaries.json`

**Interfaces:**
- Consumes: existing architecture policy schema 1.
- Produces: schema 2 with `dependencyMode: "exact" | "allowlist"` per module. `allowlist` rejects unlisted dependencies but does not require every allowed edge to be currently used.

- [ ] **Step 1: Write RED tests**

Add verifier tests proving:

```kotlin
@Test fun allowlistModeAllowsUnusedApprovedEdge() {
    val violations = verify(actual = setOf(":a"), allowed = setOf(":a", ":b"), mode = ALLOWLIST)
    assertTrue(violations.isEmpty())
}
@Test fun allowlistModeStillRejectsUnapprovedEdge() {
    val violations = verify(actual = setOf(":c"), allowed = setOf(":a", ":b"), mode = ALLOWLIST)
    assertTrue(violations.any { it.code == "module_policy.production_dependency_denied" })
}
@Test fun exactModeStillReportsStaleAllowance() {
    val violations = verify(actual = setOf(":a"), allowed = setOf(":a", ":b"), mode = EXACT)
    assertTrue(violations.any { it.code == "module_policy.production_dependency_allowance_stale" })
}
```

- [ ] **Step 2: Run focused tests**

```bash
./gradlew :build-logic:test   --tests app.openstory.build.architecture.ModuleBoundaryVerifierTest   --stacktrace
```

Expected: **FAIL** because policy has no dependency mode.

- [ ] **Step 3: Implement schema 2**

Add:

```kotlin
enum class DependencyMode { EXACT, ALLOWLIST }
```

to each module rule, defaulting to `EXACT` only when the JSON field is absent for backward decoding. In `ALLOWLIST`, run only `actual - allowed` denial checks.

- [ ] **Step 4: Migrate current JSON without changing legacy permissions**

Set all current modules to `"dependencyMode": "exact"` first and `"schemaVersion": 2`.

Run:

```bash
./gradlew :build-logic:test verifyArchitecture --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add build-logic/src/main/kotlin/app/openstory/build/architecture   build-logic/src/test/kotlin/app/openstory/build/architecture   config/architecture/module-boundaries.json
git commit -m "architecture: support directional module allowlists"
```

### Task 2: Create the target production module shells

**Files:**
- Modify: `settings.gradle.kts`
- Create: `catalog/build.gradle.kts`
- Create: `feature/catalog/build.gradle.kts`
- Create: `storage/room/build.gradle.kts`
- Create: `plugins/api/build.gradle.kts`
- Create: `plugins/runtime/build.gradle.kts`
- Modify: `config/architecture/module-boundaries.json`
- Modify: `build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt`

**Interfaces:**
- Produces target project paths:
  `:catalog`, `:feature:catalog`, `:storage:room`, `:plugins:api`, `:plugins:runtime`.

- [ ] **Step 1: Write failing module-graph test**

Extend `ModuleGraphTest.kt` with:

```kotlin
@Test
fun baselineTwoTargetModulesAreIncludedDuringTransition() {
    val settings = File("../settings.gradle.kts").readText()
    listOf(
        ":catalog",
        ":feature:catalog",
        ":storage:room",
        ":plugins:api",
        ":plugins:runtime",
    ).forEach { module ->
        assertTrue("include(\"$module\")" in settings, "Missing $module")
    }
    assertTrue("include(\":feature:home\")" in settings, "R1 must not cut over legacy UI yet")
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :build-logic:test --tests app.openstory.build.ModuleGraphTest --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 3: Add module build files**

Create `plugins/api/build.gradle.kts`:

```kotlin
plugins {
    id("openstory.kotlin.jvm")
    alias(libs.plugins.kotlin.serialization)
}
dependencies {
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test-junit"))
}
```

Create `plugins/runtime/build.gradle.kts`:

```kotlin
plugins {
    id("openstory.android.library")
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "app.openstory.plugins.runtime"
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}
dependencies {
    implementation(project(":core:common"))
    implementation(project(":plugins:api"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    implementation(libs.okhttp.client)
    implementation(libs.jsoup)
    implementation(libs.androidx.javascriptengine)
    implementation(libs.bouncy.castle.provider)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
```

Create `catalog/build.gradle.kts`:

```kotlin
plugins { id("openstory.kotlin.jvm") }
dependencies {
    implementation(project(":core:common"))
    implementation(project(":plugins:api"))
    implementation(project(":plugins:runtime"))
    implementation(libs.kotlinx.coroutines.core)
    testImplementation(kotlin("test-junit"))
    testImplementation(libs.kotlinx.coroutines.test)
}
```

Create `feature/catalog/build.gradle.kts`:

```kotlin
plugins {
    id("openstory.android.library")
    id("openstory.compose")
    id("openstory.hilt")
}
android {
    namespace = "app.openstory.catalog.ui"
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}
dependencies {
    implementation(project(":core:common"))
    implementation(project(":catalog"))
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    testImplementation(kotlin("test-junit"))
}
```

Create `storage/room/build.gradle.kts`:

```kotlin
plugins {
    id("openstory.android.library")
    id("openstory.room")
    alias(libs.plugins.kotlin.serialization)
}
android {
    namespace = "app.openstory.storage.room"
    defaultConfig { testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
}
dependencies {
    implementation(project(":core:common"))
    implementation(project(":catalog"))
    implementation(project(":plugins:runtime"))
    implementation(libs.kotlinx.coroutines.core)
    implementation(libs.kotlinx.serialization.json)
    testImplementation(kotlin("test-junit"))
    androidTestImplementation(libs.androidx.room.testing)
}
```

- [ ] **Step 4: Add target policy entries in `ALLOWLIST` mode**

Target allowed production edges:

```text
:plugins:api     -> []
:plugins:runtime -> [:core:common, :plugins:api]
:catalog         -> [:core:common, :plugins:api, :plugins:runtime]
:feature:catalog -> [:core:common, :catalog]
:storage:room    -> [:core:common, :catalog, :plugins:runtime]
```

Legacy modules remain in policy until their deletion checkpoint.

Run:

```bash
./gradlew projects verifyArchitecture --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add settings.gradle.kts catalog feature/catalog storage/room plugins   config/architecture/module-boundaries.json   build-logic/src/test/kotlin/app/openstory/build/ModuleGraphTest.kt
git commit -m "architecture: introduce baseline two module shells"
```

### Task 3: Introduce generic result ownership for target modules without breaking legacy `AppResult`

**Files:**
- Create: `core/common/src/main/kotlin/app/openstory/common/Outcome.kt`
- Create: `core/common/src/test/kotlin/app/openstory/common/OutcomeTest.kt`

**Interfaces:**
- Produces:

```kotlin
sealed interface Outcome<out T, out E> {
    data class Success<T>(val value: T) : Outcome<T, Nothing>
    data class Failure<E>(val error: E) : Outcome<Nothing, E>
}
```

Legacy `AppResult` remains temporarily only for old slices. Target modules must use `Outcome` or capability-owned result types. R5 Task 1 deletes legacy `AppResult`, `AppError`, and `AppResultExtensions` after R3/R4 have removed all old consumers.

- [ ] **Step 1: Write RED tests**

Create `OutcomeTest.kt`:

```kotlin
class OutcomeTest {
    @Test fun mapTransformsSuccess() {
        assertEquals(Outcome.Success(4), Outcome.Success(2).map { it * 2 })
    }

    @Test fun mapPreservesFailure() {
        val failure: Outcome<Int, String> = Outcome.Failure("boom")
        assertEquals(failure, failure.map { it * 2 })
    }

    @Test fun mapErrorTransformsOnlyFailure() {
        val failure: Outcome<Int, String> = Outcome.Failure("boom")
        assertEquals(Outcome.Failure(4), failure.mapError(String::length))
    }
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :core:common:test --tests app.openstory.common.OutcomeTest --stacktrace
```

Expected: **FAIL**.

- [ ] **Step 3: Implement minimal `Outcome`**

Include `map`, `flatMap`, `mapError`, `getOrNull`.

- [ ] **Step 4: Run common suite**

```bash
./gradlew :core:common:test --stacktrace
```

Expected: **BUILD SUCCESSFUL**.

- [ ] **Step 5: Commit**

```bash
git add core/common/src/main/kotlin/app/openstory/common/Outcome.kt   core/common/src/test/kotlin/app/openstory/common/OutcomeTest.kt
git commit -m "core: add typed generic outcome"
```

### Task 4: Move stable `StoryId` and `PluginId` ownership into `:core:common` with an explicit R1-only typealias bridge

**Files:**
- Create: `core/common/src/main/kotlin/app/openstory/common/id/StoryId.kt`
- Create: `core/common/src/main/kotlin/app/openstory/common/id/PluginId.kt`
- Create: `core/common/src/test/kotlin/app/openstory/common/id/StableIdsTest.kt`
- Modify: `core/model/src/main/kotlin/app/openstory/model/Ids.kt`

**Interfaces:**
- Produces stable cross-capability IDs in package `app.openstory.common.id`.
- Legacy `app.openstory.model.StoryId` and `PluginId` become migration-only Kotlin `typealias` declarations so R1 does not force an unrelated repository-wide import churn.
- Fixed deletion point: R3B Task 3 deletes `:core:model`, which removes both aliases. All Baseline 2 modules created from R1 onward import `app.openstory.common.id.*` directly; no new source may import the aliases.

- [ ] **Step 1: Write RED tests**

Create `StableIdsTest.kt`:

```kotlin
class StableIdsTest {
    @Test fun storyIdRejectsWhitespace() =
        assertFailsWith<IllegalArgumentException> { StoryId("a b") }

    @Test fun pluginIdRetainsStableValue() =
        assertEquals("org.openstory.x", PluginId("org.openstory.x").value)
}
```

- [ ] **Step 2: Verify RED**

```bash
./gradlew :core:common:test --tests app.openstory.common.id.StableIdsTest --stacktrace
```

Expected: **FAIL** because the common IDs do not exist.

- [ ] **Step 3: Implement common IDs and the migration-only aliases**

Create each common ID as a `@JvmInline value class` that calls the existing `StableId.requireValid(value)`. In `core/model/Ids.kt`, remove only the two legacy value-class declarations and add:

```kotlin
typealias StoryId = app.openstory.common.id.StoryId
typealias PluginId = app.openstory.common.id.PluginId
```

Do not add aliases anywhere else. Add a package-boundary fixture in Task 5 proving target modules cannot import `app.openstory.model.StoryId` or `PluginId`.

- [ ] **Step 4: Run impacted suites**

```bash
./gradlew :core:common:test :core:model:test test testDebugUnitTest --stacktrace
```

Expected: **BUILD SUCCESSFUL** with legacy source still compiling through the explicit aliases.

- [ ] **Step 5: Commit**

```bash
git add core/common/src/main/kotlin/app/openstory/common/id \
  core/common/src/test/kotlin/app/openstory/common/id \
  core/model/src/main/kotlin/app/openstory/model/Ids.kt
git commit -m "core: move shared story and plugin id ownership"
```

### Task 5: Activate strict source-boundary checks for the new module shells

**Files:**
- Modify: `scripts/verify-package-boundaries.sh`
- Modify: `scripts/tests/verify-package-boundaries-test.sh`
- Modify: `config/architecture/module-boundaries.json`

**Interfaces:**
- Consumes: target module directories from Task 2.
- Produces: final package restrictions for target source roots even while their contents are sparse.

- [ ] **Step 1: Add RED cases for forbidden target imports**

Append exact cases to `verify-package-boundaries-test.sh`:

```bash
run_case 'plugins/api/src/main/kotlin/F.kt' 'import app.openstory.common.Outcome' 1
run_case 'catalog/src/main/kotlin/F.kt' 'import app.openstory.storage.room.OpenStoryDatabase' 1
run_case 'feature/catalog/src/main/kotlin/F.kt' 'import app.openstory.plugins.api.PluginManifest' 1
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.execution.PluginOperationRunner' 1
run_case 'storage/room/src/main/kotlin/F.kt' 'import app.openstory.plugins.runtime.persistence.PluginStateStore' 0
```

- [ ] **Step 2: Run checker tests and verify failures before rule updates**

```bash
bash scripts/tests/verify-package-boundaries-test.sh
```

Expected: **FAIL** until the new restrictions are implemented.

- [ ] **Step 3: Implement exact restrictions**

`plugins/api` must remain standalone JVM protocol + serialization. `feature/catalog` may only see `core:common` and `catalog` among project packages.

- [ ] **Step 4: Run gates**

```bash
bash scripts/tests/verify-package-boundaries-test.sh
bash scripts/verify-package-boundaries.sh
./scripts/check-module-dependencies.sh
```

Expected: **PASS**.

- [ ] **Step 5: Commit**

```bash
git add scripts/verify-package-boundaries.sh   scripts/tests/verify-package-boundaries-test.sh   config/architecture/module-boundaries.json
git commit -m "architecture: enforce target source boundaries"
```

### Task 6: Record R1 checkpoint

**Files:**
- Create: `docs/internal/checkpoints/architecture-baseline-2-r1.md`
- Modify: `docs/project/current-state.md`

- [ ] **Step 1: Run R1 gate**

```bash
./scripts/verify.sh
./gradlew :plugins:api:test :catalog:test :feature:catalog:testDebugUnitTest   :storage:room:testDebugUnitTest :plugins:runtime:testDebugUnitTest --stacktrace
```

Expected: all configured suites **PASS** even though target modules contain only foundation code.

- [ ] **Step 2: Verify no Wave 06 code exists**

```bash
test ! -d feature/library
```

Expected: exit 0.

- [ ] **Step 3: Record actual evidence**

Document command/environment/results in `architecture-baseline-2-r1.md`.

- [ ] **Step 4: Advance current state**

Set:

```text
Architecture Baseline 2 R1: ACCEPTED
Current active boundary: R2 - Plugin Subsystem VNext
```

- [ ] **Step 5: Commit**

```bash
git add docs/internal/checkpoints/architecture-baseline-2-r1.md   docs/project/current-state.md
git commit -m "architecture: accept baseline two r1"
```

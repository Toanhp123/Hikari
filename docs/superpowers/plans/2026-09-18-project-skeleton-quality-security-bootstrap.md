# Project Skeleton + Quality/Security Bootstrap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the first buildable Android Universal Media App project skeleton and close the remaining pre-feature quality/security bootstrap gates without implementing product feature breadth.

**Architecture:** Use the R4.15 moderate module graph, AGP 9 built-in Kotlin, one root version catalog, and an included `build-logic` build with capability-oriented convention plugins. `:app` is the composition root; all other modules are compile-time boundaries only at this stage. Quality is enforced by wrapper-based CI, Android Lint, Kotlin compiler warnings-as-errors, Spotless+ktlint formatting, and an explicit architecture verification task. Security starts deny-by-default: no broad storage/network permissions, only the launcher Activity is exported, platform backup is disabled until the explicit inclusion matrix is reviewed, and logging/secrets rules are documented.

**Tech Stack:** Gradle 9.4.1, AGP 9.2.1, JDK 17, Kotlin/KGP 2.4.20, Compose Compiler 2.4.20, Compose BOM 2026.08.00, compileSdk/targetSdk 37, minSdk 23, Spotless 8.10.2, ktlint 1.8.0, AndroidX Test stable line, Benchmark 1.5.0.

**Spec:** `docs/foundation/android-universal-media-app-optimized-roadmap-spec-v2-R4.15-qboot001-toolchain-bootstrap-baseline.md`

## Global Constraints

- Android applicationId baseline is `app.universalmedia`; debug suffix is `.debug`.
- Android modules use AGP 9 built-in Kotlin; never apply `org.jetbrains.kotlin.android`.
- Pure JVM Kotlin modules use Kotlin `2.4.20` and Java/Kotlin target 17.
- Central repositories only: `google()`, `mavenCentral()`, plugin portal where plugin resolution requires it.
- No dynamic dependency versions and no per-module repositories.
- Prefer `implementation`; do not expose implementation-only Android/Room/Media3 types across module boundaries.
- Release is non-debuggable and minified/resource-shrunk; release signing material is not committed.
- `:app` is the only top-level composition/navigation root.
- No feature implementation beyond the minimal launch/smoke surface needed to prove the skeleton.

---

### Task 1: Root Build Authority + Wrapper

**Files:**
- Create: `settings.gradle.kts`
- Create: `build.gradle.kts`
- Create: `gradle.properties`
- Create: `gradle/libs.versions.toml`
- Create: `gradle/wrapper/gradle-wrapper.properties`
- Create: `gradle/wrapper/gradle-wrapper.jar`
- Create: `gradlew`
- Create: `gradlew.bat`
- Create: `.gitignore`
- Create: `.editorconfig`

**Interfaces:**
- Consumes: Q-BOOT-001 exact matrix from R4.15.
- Produces: deterministic root plugin/dependency catalog and wrapper entrypoint used by all later tasks.

- [x] Add the root settings/build/catalog files with central repository ownership and exact pinned versions.
- [ ] Install/generate the official Gradle 9.4.1 wrapper JAR/scripts on a JDK 17 networked machine; this generation environment cannot materialize the binary. Checksum-verifying bootstrap scripts are included.
- [x] Verify wrapper properties/bootstrap scripts pin only Gradle 9.4.1 and the expected distribution/wrapper checksums; official JAR byte verification remains pending until generation.
- [x] Commit checkpoint: `build: establish reproducible Gradle bootstrap`.

### Task 2: Capability-Oriented Build Logic

**Files:**
- Create: `build-logic/settings.gradle.kts`
- Create: `build-logic/build.gradle.kts`
- Create: `build-logic/src/main/kotlin/universalmedia.kotlin.jvm.gradle.kts`
- Create: `build-logic/src/main/kotlin/universalmedia.android.application.gradle.kts`
- Create: `build-logic/src/main/kotlin/universalmedia.android.library.gradle.kts`
- Create: `build-logic/src/main/kotlin/universalmedia.android.compose.gradle.kts`

**Interfaces:**
- Consumes: root catalog values for AGP/KGP and SDK/JVM defaults.
- Produces: four convention plugin IDs mandated by Q-BOOT-001.

- [x] Configure the included build to import the root version catalog and depend only on public AGP/KGP APIs.
- [x] Configure JVM convention: Kotlin JVM plugin, JDK/JVM target 17, warnings-as-errors, JUnit 4 test baseline.
- [x] Configure Android application/library conventions: SDK 37/37/23, Java 17, warnings-as-errors, test runner, lint defaults.
- [x] Configure Compose convention: Compose compiler plugin 2.4.20 plus Compose build feature.
- [x] Verify no convention plugin configures product-feature dependencies.
- [x] Commit checkpoint: `build: add capability convention plugins`.

### Task 3: Reviewed Module Graph Skeleton

**Files:**
- Create module build files/manifests for `:app`, `:core:model`, `:core:domain`, `:core:designsystem`, `:data`, `:storage:local`, `:ingestion:local`, `:source:api`, `:source:local`, `:playback:api`, `:playback:media3`, `:reader:image`, `:reader:publication`, `:feature:library`, `:feature:settings`, `:benchmark`.

**Interfaces:**
- Consumes: Q-MOD-001 allowed dependency direction.
- Produces: compile-time module boundaries with no product implementation.

- [x] Add all reviewed modules to `settings.gradle.kts`.
- [x] Use pure JVM modules for Android-free contracts (`:core:model`, `:core:domain`, `:source:api`, `:playback:api`); keep Android framework out of those APIs.
- [x] Keep Android modules source-light and namespace-specific.
- [x] Declare only the minimal project dependencies needed to encode the reviewed graph.
- [x] Add `verifyArchitecture` to reject forbidden feature→feature, implementation→feature, and core→Android implementation edges by declared project dependencies.
- [x] Commit checkpoint: `build: encode initial module graph`.

### Task 4: Minimal Launch Surface + Test Baselines

**Files:**
- Create: `app/src/main/AndroidManifest.xml`
- Create: `app/src/main/kotlin/app/universalmedia/MainActivity.kt`
- Create: `feature/library/src/main/kotlin/app/universalmedia/feature/library/LibraryRoot.kt`
- Create: `core/model/src/test/kotlin/app/universalmedia/model/ModelModuleSmokeTest.kt`
- Create: `app/src/androidTest/kotlin/app/universalmedia/AppLaunchSmokeTest.kt`

**Interfaces:**
- Consumes: `:feature:library` UI root via `:app` composition.
- Produces: one minimal Compose launch path proving JVM and Android-test source sets are wired.

- [x] Keep JVM smoke tests wiring-only instead of inventing a production marker solely for testing; no product-domain behavior is introduced during bootstrap.
- [ ] Run the JVM smoke tests through Gradle on JDK 17 once the official wrapper/SDK environment is available.
- [x] Add the minimal Compose Activity and Library root text; no navigation/domain/data logic.
- [x] Add instrumentation smoke test that launches `MainActivity` and asserts the bootstrap UI marker is visible.
- [x] Commit checkpoint: `test: prove JVM and Android launch baselines`.

### Task 5: Formatting + Static Analysis + Fast Quality Gate

**Files:**
- Modify: `build.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Create: `config/lint/lint.xml`
- Create: `scripts/verify-fast.sh`
- Create: `scripts/verify-fast.ps1`

**Interfaces:**
- Produces: one developer command for format/static/build checks and CI parity.

- [x] Apply Spotless 8.10.2 at root and ktlint 1.8.0 to Kotlin/KTS sources.
- [x] Configure Android Lint to fail the build on errors; keep warnings visible and ratchetable rather than creating a giant baseline on day one.
- [x] Add compiler warnings-as-errors through conventions.
- [x] Add `verifyFast` aggregate: `spotlessCheck`, `verifyArchitecture`, JVM tests, Android lint, debug compile/assemble where supported.
- [x] Add Bash and PowerShell wrappers around the canonical Gradle command.
- [x] Commit checkpoint: `quality: add fast static and format gates`.

### Task 6: Benchmark Boundary

**Files:**
- Create: `benchmark/build.gradle.kts`
- Create: `benchmark/src/main/AndroidManifest.xml`
- Create: `benchmark/src/main/kotlin/app/universalmedia/benchmark/StartupBenchmark.kt`

**Interfaces:**
- Consumes: `:app` target package `app.universalmedia`.
- Produces: Macrobenchmark-ready Android test boundary; actual numeric budgets remain deferred.

- [x] Configure `com.android.test` benchmark module with target project `:app`, `android.experimental.self-instrumenting=true`, and a release-like target variant as required by Macrobenchmark.
- [x] Add Benchmark 1.5.0 dependency.
- [x] Add a cold-start benchmark as the first CUJ without asserting a numeric threshold.
- [x] Keep benchmark execution out of the fast PR gate; expose a dedicated command.
- [x] Commit checkpoint: `perf: wire macrobenchmark boundary`.

### Task 7: Security/Privacy Bootstrap

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`
- Create: `docs/security/uri-file-threat-model.md`
- Create: `docs/security/logging-secrets-policy.md`
- Create: `docs/security/exported-components.md`
- Create: `scripts/verify-security-baseline.sh`

**Interfaces:**
- Produces: explicit Phase-0 threat/logging/exported-component rules and a mechanical manifest check.

- [x] Keep manifest free of `MANAGE_EXTERNAL_STORAGE`, broad legacy storage permissions, and `INTERNET` until a consuming slice requires network capability.
- [x] Set platform backup disabled in bootstrap until the R4.15 explicit inclusion matrix is implemented; product logical backup remains future V1 work.
- [x] Make the launcher Activity the only exported component; document why launcher export is required.
- [x] Document URI/provider/archive input as untrusted, with capability checks, traversal/path-escape defenses, bounded parsing, cancellation and no URI/path identity promotion.
- [x] Document no secrets/tokens/full user paths or full content URIs in production logs.
- [x] Add Bash and PowerShell scripts that fail if forbidden manifest permissions or unexpected exported components appear.
- [x] Commit checkpoint: `security: establish deny-by-default bootstrap`.

### Task 8: CI + Documentation + Foundation Handoff

**Files:**
- Create: `.github/workflows/ci.yml`
- Create: `README.md`
- Copy: R4.15 foundation into `docs/foundation/`
- Create: `docs/engineering/bootstrap-verification.md`
- Create/update: R4.16 foundation after evidence is collected.

**Interfaces:**
- Produces: clean-checkout CI and the next-session handoff.

- [x] Configure CI with JDK 17, Android SDK, wrapper validation, fast gate, release assembly, and a separate instrumentation lane; Macrobenchmark remains an explicit device/performance gate rather than a normal PR timing gate.
- [x] Document exact local Bash/PowerShell commands and required SDK/JDK.
- [x] Record which checks were executed in this environment versus structurally verified only; Android/Gradle execution is explicitly pending.
- [x] Update the canonical foundation checklist: close only gates supported by evidence; do not claim Android build/device PASS if this environment cannot run it.
- [x] Archive project as a transport ZIP excluding build caches/output.
- [x] Final static verification: placeholder scan, dynamic-version scan, built-in-Kotlin guard, forbidden-permission scan, architecture allow-list scan, secret-pattern scan, XML/TOML/YAML parsing, Bash syntax, and security script all passed in the generation environment. Gradle/Android execution remains pending separately.


## Execution Evidence Note

The generation container has Java 21, no Android SDK, no system Gradle, and cannot materialize the official wrapper JAR binary. Static/source/security checks are executed locally; Gradle build, JVM JUnit, Android instrumentation, lint and release/benchmark assembly remain pending clean-checkout evidence on JDK 17 + SDK 37. This is an evidence limitation, not a substituted PASS.

### Task 9: Executable Bootstrap Gate Harness + CI Parity

**Files:**
- Create: `scripts/verify-bootstrap.sh`
- Create: `scripts/verify-bootstrap.ps1`
- Create: `scripts/tests/bootstrap-execution-harness-test.sh`
- Create: `scripts/tests/ci-bootstrap-contract-test.sh`
- Modify: `.github/workflows/ci.yml`
- Modify: `README.md`
- Modify: `docs/engineering/bootstrap-verification.md`

**Interfaces:**
- Consumes: pinned Q-BOOT-001 toolchain, wrapper checksums, `verifyFast`, `verifyRelease`, app instrumentation smoke test.
- Produces: one canonical local/CI execution order with explicit doctor/host/device modes.

- [x] RED: add harness test proving Java 21 is rejected, a fake JDK17+SDK37 environment is accepted by doctor mode, and missing API37 is rejected.
- [x] GREEN: implement Bash bootstrap verifier with JDK/SDK/wrapper integrity checks and ordered Gradle/device gates.
- [x] Add PowerShell parity for the Windows-first developer workflow.
- [x] RED: add CI contract test proving both jobs provision compileSdk 37/build-tools 37.0.0 and the host lane uses the canonical host verifier.
- [x] GREEN: fix instrumentation CI provisioning and local/CI command drift.
- [x] Re-run harness tests and Bash syntax checks.
- [x] Run the real full gate on Windows with Temurin JDK 17.0.20 + SDK `platforms;android-37.0` + connected Redmi Note 9S / Android 15; doctor, Gradle help, `verifyFast`, launch instrumentation and `verifyRelease` all passed.

### Task 10: Bootstrap Contract Correction + Lean Agent Routing

**Files:**
- Modify: `scripts/verify-bootstrap.sh`
- Modify: `scripts/verify-bootstrap.ps1`
- Modify: `scripts/tests/bootstrap-execution-harness-test.sh`
- Modify: `scripts/tests/ci-bootstrap-contract-test.sh`
- Modify: `scripts/tests/gradle-bootstrap-contract-test.sh`
- Modify: `.github/workflows/ci.yml`
- Remove: `gradle/gradle-daemon-jvm.properties` (stale JVM-25 criteria)
- Create/update: root `AGENTS.md` as a compact routing/discipline layer
- Update in place: `docs/foundation/android-universal-media-app-foundation.md`

**Interfaces:**
- Consumes: Q-BOOT-001 JDK/API/toolchain baseline and the bootstrap execution harness.
- Produces: one unambiguous SDK platform package/folder contract plus one canonical living foundation with token-efficient agent routing.

- [x] Standardize compile-platform installation/checks on `platforms;android-37.0`; retain Gradle `compileSdk/targetSdk = 37` and API-37 runtime lane.
- [x] Repair fake-SDK harness directory creation and Bash/PowerShell diagnostic parity.
- [x] Remove contradictory Gradle daemon JVM 25 criteria; add guards requiring JDK 17 if daemon criteria are reintroduced.
- [x] Keep `AGENTS.md` small: route to the foundation Current Control Block and task-specific decision records instead of duplicating project state/architecture.
- [x] Keep exactly one stable-path canonical foundation; update it in place and use Git history for revisions.
- [x] Remove parallel `docs/state/` / persistent handoff sources of truth.
- [x] Re-run repository-owned bootstrap/CI/Gradle/security contract tests and Bash syntax checks.
- [x] Collect real Windows JDK-17 + SDK-37.0 host/device/release-like evidence through the canonical PowerShell verifier.
- [x] Trigger the first real GitHub Actions run on canonical `master`; run `35363881972` proved the trigger fix but failed in `setup-android@v3` before Gradle because the removed legacy SDK package `tools` was requested.
- [x] Correct CI setup to `android-actions/setup-android@v4` with explicit `platform-tools` only and harden the CI contract against regression to v3/legacy `tools`.
- [x] Collect second real run `35364775034`: `verify` and API-23 instrumentation passed; Android-17 `37.0` installed/created/launched its AVD but remained `adb offline` until the 600-second boot timeout, so Gradle instrumentation never started in that lane.
- [x] Align the runner image's stale `cmdline-tools/latest` with the pinned setup-android 22.0 toolchain before `android-emulator-runner@v2`, and harden the CI contract so minor-version AVD creation cannot silently fall back to the preinstalled 12.0 tools.
- [x] Collect third real run `35366782420`: `verify` and API-23 remained green; Android-17 `37.0` booted and Gradle reached `AppLaunchSmokeTest`, which then failed in an older Espresso input-injection path with `NoSuchMethodException: InputManager.getInstance`.
- [x] Make the existing Espresso 3.7.0 catalog pin an explicit `:app` instrumentation dependency and guard it in the Gradle bootstrap contract; 3.7.0 contains the Android-17-compatible `getSystemService` fix.
- [ ] Collect a green GitHub Actions clean-checkout host + API-23 plus Android-17-`37.0` instrumentation retry before opening the first local vertical slice.

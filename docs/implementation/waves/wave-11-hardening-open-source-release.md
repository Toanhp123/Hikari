<!-- DOCUMENT LIFECYCLE: PLANNED / REBASELINED FOR POST-BASELINE GRAPH -->

# Wave 11 - Hardening and Open-Source Release Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use `superpowers:subagent-driven-development` or `superpowers:executing-plans`; use TDD and commit each task.

**Goal:** Complete plugin management, security, performance, accessibility, documentation, and reproducible release acceptance without changing capability ownership.

**Architecture:** Follows `../../superpowers/specs/2026-08-10-post-baseline-wave-06-11-architecture-design.md`. Introduces only `:feature:plugins` for independent plugin-management presentation, consuming the existing `:core:designsystem` foundation. All domain/security/storage/platform responsibilities remain in their established owners.

## Global Constraints

- Entry module graph: Wave 10 exit graph.
- Exit module graph: entry graph plus `:feature:plugins`.
- Introduces `:feature:plugins` in Task 1.
- Consumes from Wave 10: complete capability graph, typed settings, sessions, workers, notifications, downloads, and Reader.
- Produces: reproducible signed APK evidence and an open-source release-ready repository.
- Room schema 11 remains stable after Wave 10 notification persistence unless a proven release defect requires a reviewed migration.
- No new product capability or generic architecture layer.
- Wave 11 Plugin Management enters through the avatar utility sheet and never top-level navigation.
- Discover / Home / Library remains the final top-level model; Plugins must not become a `TopLevelDestination`.

### Task 1: Complete plugin-management presentation

**Files:**
- Create: `feature/plugins/build.gradle.kts`, `feature/plugins/src/main/kotlin/app/openstory/plugins/ui/PluginListScreen.kt`, `PluginDetailScreen.kt`, `InstallPluginViewModel.kt`, `PluginManagementViewModel.kt`
- Test: `feature/plugins/src/test/kotlin/app/openstory/plugins/ui/PluginManagementViewModelTest.kt`
- Test: `feature/plugins/src/androidTest/kotlin/app/openstory/plugins/ui/PluginManagementScreenTest.kt`
- Create: `feature/plugins/src/test/kotlin/app/openstory/plugins/ui/PluginManagementScreenshotTest.kt`
- Modify: `settings.gradle.kts`, `config/architecture/module-boundaries.json`, `app/build.gradle.kts`, `app/src/main/kotlin/app/openstory/navigation/AppRoute.kt`, `AppNavHost.kt`, and the app-shell owner of `HikariUtilitySheet`

- [ ] Write RED tests for URL/local/repository installs, installed/detail/install/update/rollback flows, capability/domain/signature confirmation, update diff, disable/remove, retained downloads, session clear, Plugins utility-row navigation, the unchanged top-level destination set, and exact exit graph.
- [ ] Implement installed/detail/install/update/rollback flows solely over the public `:plugins:runtime` management facade and package contracts; do not expose execution internals or duplicate runtime lifecycle state in the feature.
- [ ] Consume `:core:designsystem` and match the approved Plugin target screen for installed health, details, install confirmation, update capability diff, and rollback status.
- [ ] Add the Plugins focused route to `AppNavHost` and a Plugins row to `HikariUtilitySheet`; close the sheet before navigation and preserve Discover / Home / Library as the only three top-level routes.
- [ ] Run `./gradlew :feature:plugins:testDebugUnitTest :feature:plugins:connectedDebugAndroidTest :plugins:runtime:testDebugUnitTest :app:connectedDebugAndroidTest --stacktrace`.
- [ ] Commit `plugins: add management feature`.

### Task 2: Harden application and plugin security

**Files:**
- Modify: `app/src/main/AndroidManifest.xml`, runtime package/session/security classes, `app/build.gradle.kts`
- Create: `app/src/main/res/xml/network_security_config.xml`
- Test: `app/src/androidTest/kotlin/app/openstory/security/ReleaseSecurityTest.kt`, runtime package/session security suites

- [ ] Write RED tests for exported components, cleartext denial, redirect/host validation, archive limits, path traversal, session secrecy, backup rules, logging redaction, and release debuggability.
- [ ] Implement minimal hardening in owning adapters without cross-layer bypasses.
- [ ] Run `ANDROID_SERIAL_API_26="$ANDROID_SERIAL_API_26" ANDROID_SERIAL_API_37="$ANDROID_SERIAL_API_37" ./scripts/checkpoints/architecture-baseline-2.sh`, then `ANDROID_SERIAL="$ANDROID_SERIAL_API_37" ./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.security.ReleaseSecurityTest --stacktrace`.
- [ ] Commit `security: harden release boundaries`.

### Task 3: Benchmark complete product journeys

**Files:**
- Create: `app/src/androidTest/kotlin/app/openstory/performance/StartupPerformanceTest.kt`, `LargeLibraryPerformanceTest.kt`, `ReaderPerformanceTest.kt`
- Create: `app/src/androidTest/kotlin/app/openstory/performance/PerformanceBudgets.kt`
- Modify: `app/build.gradle.kts` to add a non-debuggable benchmark build type initialized from release and signed with the debug key only for local instrumentation

- [ ] Establish explicit budgets and write failing benchmark assertions for current regressions only.
- [ ] Profile before optimizing; change the owning subsystem rather than add shared caches/helpers.
- [ ] Run `./gradlew :app:connectedBenchmarkAndroidTest -Pandroid.testInstrumentationRunnerArguments.package=app.openstory.performance --stacktrace` on the documented benchmark device and record exact budgets/results.
- [ ] Commit `performance: enforce release budgets`.

### Task 4: Finish accessibility, localization, privacy, and destructive safety

**Files:**
- Modify: `app/src/main/res/values/strings.xml`, locale resources, feature Compose screens, privacy copy
- Test: `app/src/androidTest/kotlin/app/openstory/accessibility/AppAccessibilityTest.kt`, `app/src/test/kotlin/app/openstory/resources/LocalizationCompletenessTest.kt`

- [ ] Write RED tests for semantics, focus order, font scale, contrast, pluralization, missing translations, offline/privacy copy, and explicit destructive confirmations.
- [ ] Implement resource-backed UI and recoverable deletion flows.
- [ ] Run `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.accessibility.AppAccessibilityTest :app:testDebugUnitTest --tests app.openstory.resources.LocalizationCompletenessTest lintDebug --stacktrace`.
- [ ] Commit `ui: complete release accessibility and copy`.

### Task 5: Publish contributor and architecture documentation

**Files:**
- Modify: `docs/PROJECT-HANDBOOK.md`, `docs/README.md`, `docs/implementation/current-roadmap.md`, `docs/project/current-state.md`, `docs/project/document-governance.md`, `docs/plugin-sdk/`, contributor/security documentation
- Test: `scripts/tests/release-documentation-test.sh`

- [ ] Add docs contract tests proving module graph, commands, package format, protocol, security model, and active state match source.
- [ ] Update only current canonical documentation; archive superseded instructions.
- [ ] Run `for test_script in scripts/tests/*.sh; do bash "$test_script"; done` and `./scripts/verify.sh`.
- [ ] Commit `docs: publish release architecture and contribution guide`.

### Task 6: Create reproducible release pipeline and final acceptance

**Files:**
- Create: `.github/workflows/release.yml`, `scripts/checkpoints/release.sh`, `scripts/tests/release-checkpoint-test.sh`, `docs/internal/checkpoints/release.md`
- Modify: Gradle release/signing/dependency verification/SBOM/license configuration

- [ ] Write RED pipeline contracts for clean checkout, pinned toolchain, dependency verification, unit/lint/detekt, API 26/37 instrumentation, reproducible APK hash, signing separation, and artifact checksums.
- [ ] Implement the minimal release pipeline without storing credentials in the repository.
- [ ] Run `./scripts/checkpoints/release.sh` twice from separate clean temporary worktrees and compare the unsigned/release APK SHA-256 values before signing.
- [ ] Commit `release: add reproducible acceptance pipeline`.

## Release Checkpoint

- [ ] Exact final graph passes; no unapproved modules or dependency edges.
- [ ] Schema history, plugin protocol, packages, sessions, downloads, and settings are stable.
- [ ] Security and performance budgets pass on supported API levels.
- [ ] Accessibility/localization/privacy checks pass.
- [ ] Two clean release builds are reproducible and checksummed.
- [ ] Deep ownership review finds no catch-all module, mixed transaction owner, execution leak, or structural suppression debt.
- [ ] Canonical state records release readiness from actual evidence only.

# Performance Wave 4 Data + Benchmark Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Eliminate duplicate catalog/search/dashboard work and add reproducible Macrobenchmark/Baseline Profile coverage for the app's critical navigation journeys.

**Architecture:** Discover shares one home-snapshot stream; Search caches filter definitions by plugin id/version; Home/Updates scope domain observation to library story ids. A dedicated `:benchmark` Android test module measures frame timing and generates the Baseline Profile, with an opt-in blur-off benchmark path for A/B measurement.

**Tech Stack:** Kotlin 2.4.10, AGP 9.3.0, Compose BOM 2026.06.00, Room 2.8.4, Navigation 3 1.1.4, AndroidX Macrobenchmark/Baseline Profile.

## Global Constraints

- Preserve Wave 1-3.5 behavior.
- No Room schema migration.
- No direct feature -> storage dependency.
- Normal production visuals keep backdrop blur enabled.
- Benchmark/profile generation is not part of ordinary `verify.sh`.

---

### Task 1: Share Discover home observation

**Files:**
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/home/CatalogHomeQuery.kt`
- Modify: `feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt`
- Modify: corresponding unit tests

- [x] Add a failing test proving one repository home observation feeds both homes and ranking.
- [x] Run the focused test/contract and confirm RED.
- [x] Replace `CatalogHomeQuery.rankedStories` with `rank(homes)` and derive ranking from the ViewModel's shared homes flow.
- [x] Run focused tests and static policy to GREEN.

### Task 2: Cache Search filters by plugin version

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogFilterCache.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt`
- Modify: `catalog/src/test/kotlin/app/openstory/catalog/search/CatalogSearchServiceTest.kt`

- [x] Add RED tests for unchanged version reuse, version invalidation, disabled-source eviction, and failure retry.
- [x] Implement a process-local cache keyed by `(PluginId, version)`.
- [x] Run catalog tests to GREEN.

### Task 3: Scope Home/Updates observations to library stories

**Files:**
- Modify domain repository interfaces for chapter/mapping/download/progress observation.
- Modify Room DAOs/repositories with filtered query paths.
- Modify `HomeDashboardViewModel.kt` and `UpdatesViewModel.kt`.
- Modify focused tests and Room tests.

- [x] Add RED contract tests that Home/Updates no longer subscribe to unscoped activity flows.
- [x] Add scoped repository APIs with safe empty-set behavior.
- [x] Implement Room filtered observations without schema changes.
- [x] Use library story ids + `flatMapLatest` in Home/Updates.
- [x] Run feature/domain/storage tests to GREEN.

### Task 4: Add Macrobenchmark and Baseline Profile module

**Files:**
- Modify `settings.gradle.kts`, `gradle/libs.versions.toml`, root/app Gradle files.
- Modify architecture verifier/model/policy for `android-test`.
- Create `benchmark/build.gradle.kts`, manifest, macrobenchmark/profile tests.
- Add stable app-shell benchmark semantics/test tags where needed.

- [x] Add RED static contract for benchmark module/toolchain/release optimization.
- [x] Add `:benchmark` and AndroidX benchmark/profile dependencies/plugins.
- [x] Extend architecture policy/verifier to classify `com.android.test`.
- [x] Implement the five agreed FrameTiming CUJs and one Baseline Profile generator.
- [x] Run static architecture/toolchain gates to GREEN.

### Task 5: Add benchmark-only backdrop A/B

**Files:**
- Modify app/design-system backdrop ownership at the narrowest boundary.
- Modify benchmark tests.
- Add unit/static guardrails.

- [x] Add RED contract proving normal mode is blur-on and benchmark opt-out is explicit.
- [x] Implement benchmark-only disable switch with no production heuristic.
- [x] Add paired blur-on/blur-off frame benchmark.
- [x] Run static/UI tests to GREEN.

### Task 6: Documentation and verification

**Files:**
- Update performance spec/plan/checkpoint docs.
- Modify `scripts/tests/performance-lifecycle-policy-test.sh` or add a dedicated Wave 4 policy contract.

- [x] Run all shell/static gates.
- [x] Run `git diff --check` and patch apply-check on a pristine Wave 3.5 baseline.
- [x] Attempt Gradle focused/full verification; if network blocks wrapper resolution, record the exact blocker and provide user commands.
- [x] Generate the final continuation patch only after the apply-check tree passes all runnable gates.

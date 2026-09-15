# Feature Catalog Module Cleanup Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Reorganize `:feature:catalog` by responsibility, remove retired artwork compatibility adapters, and preserve accepted Step 3 Task 10 behavior.

**Architecture:** Keep the module graph and capability semantics unchanged. Split the root composition god file into runtime/artwork/discover owners, move Discover UI into responsibility-bearing subpackages, migrate `assets` to `artwork`, remove stale presentation/state buckets, and update only the smallest consumer cone required by package moves.

**Tech Stack:** Kotlin, Android, Jetpack Compose, Gradle 9.5.0, shell architecture gates.

**Spec:** `docs/project/file-package-ownership-policy.md`

## Global Constraints

- One primary cleanup module: `:feature:catalog`.
- No module dependency changes.
- No UI/behavior redesign and no Task 11 work.
- No compatibility aliases/wrappers for internal package moves.
- `CatalogVariantBinding.kt` and source-set `VariantCatalogBinding.kt` remain build-surface anchors.
- `CatalogCoverArtwork` remains owned by `:feature:catalog`.
- Historical `LegacyArtworkTestAdapters.kt` is deleted rather than renamed.

---

### Task 1: Ratchet the target package layout

**Files:**
- Create: `scripts/tests/v2-feature-catalog-layout-test.sh`
- Modify: `scripts/verification-common.sh`
- Modify: `scripts/tests/v2-verification-entrypoints-test.sh`

- [x] Write the layout regression for required responsibility roots and retired paths.
- [x] Run it and confirm RED on the current flat tree.
- [x] Register it as a current static contract without wildcard discovery.

### Task 2: Split production ownership

**Files:**
- Split/delete: `feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogComposition.kt`
- Delete: `CatalogScreen.kt`, `CatalogSectionResources.kt`, `CatalogArtworkEntryPoint.kt`
- Move/split: `assets`, `presentation`, `state`, and Discover rendering files into the approved target tree.

- [x] Move runtime access/holder and the Discover runtime adapter into `runtime/`.
- [x] Move artwork runtime/rendering/request adapters into `artwork/`.
- [x] Keep shared Discover state/contracts in `discover/`; move composition to `discover/composition/` and screen orchestration to `discover/screen/` so package dependencies remain acyclic.
- [x] Split header/editorial/section rendering into their responsibility packages.
- [x] Move the public Catalog entry point to `entrypoint/` while keeping `CatalogVariantBinding.kt` as the root build-surface anchor.
- [x] Remove dead `productEyebrowLabel` and redundant Catalog screen wrapper.

### Task 3: Migrate source sets and tests

**Files:**
- Move `VariantLocalCoverAssets.kt` in debug/release/benchmarkRelease into `feature/artwork/`.
- Move unit tests to mirror production ownership.
- Delete `LegacyArtworkTestAdapters.kt` and rewrite affected Android tests against `core:artwork` current APIs plus real Catalog adapters.

- [x] Update package/import paths atomically.
- [x] Preserve build-surface anchor locations.
- [x] Search for stale old-package references in active source; historical docs/generated profiles are handled according to their own authority.

### Task 4: Verify and self-review

- [x] Run the focused host Gradle/architecture cone (`testDebugUnitTest`, debug/androidTest/release/benchmark compilation, `:app:compileDebugKotlin`, architecture/foundation/package/module gates, and Detekt). After remediation of package-move defects, the accepted rerun returned `BUILD SUCCESSFUL in 39s` with `278 actionable tasks: 13 executed, 265 up-to-date`.
- [x] Run the focused connected rerun for the four materially rewritten instrumentation classes: `ArtworkPreflightInstrumentedTest`, `LocalCoverContinuityInstrumentedTest`, `MangaUpdatesCatalogIntegrationTest`, and `MangaUpdatesCatalogBoundaryIntegrationTest`. The Redmi Note 9S / Android 15 run completed 25 tests with zero skipped/failed and returned `BUILD SUCCESSFUL in 1m 44s` (`134 actionable tasks: 9 executed, 125 up-to-date`).
- [x] Run the Catalog layout ratchet, source-layout gate, verification-entrypoint regression, current static contract list, and structural review.
- [x] Confirm `settings.gradle.kts`, module-boundaries policy, and `feature/catalog/build.gradle.kts` did not change.
- [x] Self-review for behavior drift, stale compatibility layers, wrong package ownership, over-packaging, and generated-profile FQN staleness.
- [x] Update cleanup checkpoint to `CLEANUP COMPLETED/ACCEPTED`; stop before authorizing another module.

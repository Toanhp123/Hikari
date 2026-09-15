# Hikari Repository Module-Local Cleanup Checkpoint

Date: 2026-09-15
Status: **`:feature:catalog` CLEANUP COMPLETED/ACCEPTED**

## Authority

- Source placement and package ownership: `../../project/file-package-ownership-policy.md`
- Cleanup campaign constraints: `../../superpowers/specs/2026-09-15-hikari-module-local-cleanup-design.md`
- Current execution routing: `../../implementation/current-roadmap.md`
- Production dependency direction: `../../../config/architecture/module-boundaries.json`
- Active module plan: `../../superpowers/plans/2026-09-15-feature-catalog-module-cleanup.md`

## Accepted boundary before this patch

- Step 3 remains accepted through Task 10.
- Step 3 Task 11 remains not started during the cleanup interlude.
- Wave 0 repository-truth repair and cleanup governance are the prerequisite baseline.
- The cross-module production graph remains accepted; this patch does not add/remove modules or dependency edges.

## `:core:artwork` cleanup target

The approved cleanup keeps only module-wide primitives at the root and groups the remaining source by
responsibility:

```text
core/artwork/src/main/kotlin/app/openstory/artwork/
├── ArtworkFailure.kt
├── ArtworkLimits.kt
├── cache/
│   ├── ArtworkDecodedMemoryCache.kt
│   ├── ArtworkEncodedCache.kt
│   └── ArtworkMemoryPressure.kt
├── pipeline/
│   ├── ArtworkInFlight.kt
│   └── ArtworkPipelineCoordinator.kt
├── policy/
│   ├── ArtworkPolicy.kt
│   └── ArtworkPolicyResolver.kt
├── preflight/
│   ├── ArtworkImageContainer.kt
│   └── ArtworkImagePreflight.kt
├── remote/
│   ├── ArtworkRemotePolicy.kt
│   └── ArtworkTransport.kt
├── request/
│   ├── ArtworkRequest.kt
│   └── ArtworkRequestIdentity.kt
└── runtime/
    ├── ArtworkFetcher.kt
    └── ArtworkRuntime.kt
```

Core tests now mirror the same responsibility packages for cache, pipeline, policy, remote, and request
coverage. `ArtworkPolicyTest` was split so remote acquisition tests and helpers no longer live in the
policy-only test file.

## Behavior/architecture invariants preserved by the patch

- `settings.gradle.kts` and `config/architecture/module-boundaries.json` are unchanged.
- `:core:artwork` still depends only on its previously accepted dependency cone; no build dependency is added.
- Artwork limit values are unchanged: 32 MiB decoded memory, 128 MiB encoded disk, 8 MiB encoded payload,
  three redirects, 5/10/15 second connect/read/call timeouts, 8192 source dimension, 32M source pixels,
  and zero manual offscreen prefetch.
- Artwork failure reasons are unchanged and remain typed.
- HTTPS-only admission, canonical DNS host checks, redirect revalidation, bounded temporary payloads,
  media admission, JPEG/PNG/static-WebP validation, animated-image rejection, cache commit behavior,
  in-flight coalescing, last-consumer cancellation, decode/network admission, memory-pressure handling,
  and lazy runtime/session ownership are not intentionally changed.
- No compatibility aliases preserve the old flat artwork package; all repository consumers were migrated
  to the new responsibility-bearing package paths.

## Fresh agent-owned evidence

The cleanup workspace recorded the required RED before implementation:

- `bash scripts/tests/v2-core-artwork-layout-test.sh` — **FAIL as expected** because
  `ArtworkFailure.kt` and the approved package tree did not yet exist.

After implementation:

- `bash scripts/tests/v2-core-artwork-layout-test.sh` — **PASS**;
- `bash scripts/tests/v2-verification-entrypoints-test.sh` — **PASS**;
- `bash scripts/tests/v2-source-layout-policy-test.sh` — **PASS**;
- `source scripts/verification-common.sh && run_repository_static_contract_tests` — **PASS** for all
  current static contract tests, including the new artwork-layout ratchet;
- `bash scripts/structural-review-report.sh` — **PASS hard policies**; the former
  `ArtworkPolicy.kt` 628-line hard violation is gone;
- standalone `kotlinc` compile of the Android/Coil-independent artwork policy/remote/pipeline/request-
  identity cone with execution-contract stubs — **PASS**;
- declaration inventory comparison — **PASS**, 41 pre-cleanup declarations and 41 post-cleanup
  declarations with no missing/new named declarations;
- artwork limit-value comparison — **PASS**, exact values unchanged;
- `ArtworkFailureReason` member comparison — **PASS**, exact members/order unchanged;
- external-consumer normalization audit — **PASS**, all 18 changed Kotlin files outside `:core:artwork`
  normalize byte-for-byte to the baseline after reversing only the package-path migration.

## User-owned host Gradle verification — accepted

On 2026-09-15, the user ran the required focused Gradle cone from the real repository after applying the
cleanup patch:

```powershell
.\gradlew.bat :core:artwork:testDebugUnitTest `
  :core:artwork:compileDebugKotlin `
  :feature:catalog:compileDebugKotlin `
  :feature:catalog:compileDebugAndroidTestKotlin `
  :app:compileDebugKotlin `
  --no-daemon
```

Returned host evidence:

```text
BUILD SUCCESSFUL in 1m 2s
105 actionable tasks: 11 executed, 94 up-to-date
Configuration cache entry stored.
```

This closes the only open host Gradle verification item for the `:core:artwork` cleanup. The package
migration therefore compiles through `:core:artwork`, the direct `:feature:catalog` production and Android-
test consumers, and the app composition consumer while the focused artwork unit-test task is green.

The repository-wide `verify-source-layout.sh` still has the pre-existing, out-of-scope
`feature/catalog/src/androidTest/.../LegacyArtworkTestAdapters.kt` generation-labelled filename debt. That
adjacent Catalog test adapter is not part of the `:core:artwork` acceptance boundary and remains for the
`:feature:catalog` cleanup turn.

## Remaining review signals inside `:core:artwork`

`structural-review-report.sh` now reports only review-level signals in the module:

- `runtime/ArtworkRuntime.kt` has a broad import/public-method surface because it remains the intentional
  session/runtime integration boundary;
- `runtime/ArtworkFetcher.kt` and `remote/ArtworkRemotePolicy.kt` have more than 15 imports;
- `remote/ArtworkRemotePolicy.executeHop` remains a >50-line review signal;
- `pipeline/ArtworkPipelineCoordinator.kt` is flagged by the generic broad-name heuristic even though
  coordinator semantics are intentional.

These are not hard policy failures and are not authorization for further splitting in this turn.

## `:feature:catalog` cleanup — completed / accepted

The next explicitly authorized primary module is `:feature:catalog`. The cleanup keeps the accepted
module graph and Task 10 behavior while replacing flat/mixed source placement with responsibility-bearing
packages. No build dependency edge, storage/runtime semantic, UI behavior, or Task 11 capability is added.

Approved production tree:

```text
feature/catalog/src/main/kotlin/app/openstory/catalog/feature/
├── CatalogVariantBinding.kt
├── entrypoint/
│   └── CatalogEntryPoint.kt
├── artwork/
│   ├── CatalogArtworkRuntime.kt
│   ├── CatalogCoverArtwork.kt
│   ├── CoverArtworkState.kt
│   └── CoverRequest.kt
├── runtime/
│   ├── CatalogRuntimeAccess.kt
│   ├── CatalogRuntimeHolder.kt
│   └── DiscoverRuntime.kt
├── discover/
│   ├── DiscoverIssueUi.kt
│   ├── DiscoverSectionLabels.kt
│   ├── DiscoverTestTags.kt
│   ├── DiscoverUiState.kt
│   ├── DiscoverViewModel.kt
│   ├── DiscoverVisualMetrics.kt
│   ├── composition/
│   │   └── DiscoverComposition.kt
│   ├── screen/
│   │   └── DiscoverScreen.kt
│   ├── editorial/
│   ├── header/
│   └── section/
└── trace/
```

The cleanup deletes the retired `CatalogComposition.kt`, `CatalogScreen.kt`,
`CatalogSectionResources.kt`, `CatalogArtworkEntryPoint.kt`, the production `assets/presentation/state`
buckets, and `LegacyArtworkTestAdapters.kt`. Catalog artwork instrumentation now calls the current
`:core:artwork` APIs directly plus the real Catalog `CoverAssetKey -> ArtworkRequest` adapter rather than
recreating the retired feature-local artwork surface. Debug/release/benchmark local-cover bindings now live
under `feature/artwork/`; the build-surface `CatalogVariantBinding.kt` / `VariantCatalogBinding.kt` anchors
remain in their accepted locations.

### Fresh agent-owned evidence

The cleanup workspace recorded the required layout RED before source migration. After implementation:

- `bash scripts/tests/v2-feature-catalog-layout-test.sh` — **PASS**;
- `bash scripts/tests/v2-source-layout-policy-test.sh` — **PASS**;
- `bash scripts/tests/v2-verification-entrypoints-test.sh` — **PASS**;
- `source scripts/verification-common.sh && run_repository_static_contract_tests` — **PASS** for every
  current static contract, including the Catalog layout ratchet;
- `bash scripts/structural-review-report.sh` — **PASS hard policies**; only review-level signals remain;
- stale active-source scan — **PASS** for retired `CatalogComposition.kt`, `CatalogScreen.kt`,
  `CatalogArtworkEntryPoint.kt`, feature `assets/presentation/state` production packages, and
  `LegacyArtworkTestAdapters.kt`;
- `settings.gradle.kts`, `config/architecture/module-boundaries.json`, and
  `feature/catalog/build.gradle.kts` remain byte-unchanged.

The sandbox attempted the focused Gradle cone but could not download Gradle 9.5.0 because external network
access is unavailable. The first real host run then exposed three cleanup defects: `DiscoverScreen` lost the
`loadingSections` import after section extraction; the initial package tree created a strongly connected component
between the Catalog root/runtime/Discover rendering packages; and the previously accepted `:core:artwork`
`ArtworkImageContainer.kt` filename violated Detekt `MatchingDeclarationName`. The remediation keeps the root
Catalog package as a lower build-surface anchor, moves the public entry point to `entrypoint/`, moves Discover
runtime ownership to `runtime/`, moves Discover composition/screen orchestration above the shared Discover
contract package, restores the missing section import, and splits `EncodedImageBounds` into its matching file.
Two subsequent host reruns exposed only package-move import omissions (`DiscoverTestTags` in `DiscoverScreen`
and generated `R` in `DiscoverSectionCopyTest`); both were fixed without changing behavior or dependency edges.

### User-owned host verification — accepted

On 2026-09-15, the user reran the complete focused host/architecture cone from the real repository:

```powershell
.\gradlew.bat :feature:catalog:testDebugUnitTest `
  :feature:catalog:compileDebugKotlin `
  :feature:catalog:compileDebugAndroidTestKotlin `
  :feature:catalog:compileReleaseKotlin `
  :feature:catalog:compileBenchmarkReleaseKotlin `
  :app:compileDebugKotlin `
  verifyArchitecture :app:verifyFoundation verifyStep3BuildSurface `
  verifyProductionPackageStructure verifyModuleBoundaries detekt `
  --no-daemon
```

Returned host evidence:

```text
BUILD SUCCESSFUL in 39s
278 actionable tasks: 13 executed, 265 up-to-date
Configuration cache entry reused.
```

This closes package/import/type visibility across Catalog production, unit tests, Android instrumentation,
release/benchmark source sets, the App consumer, live Step 3 architecture checks, and Detekt. Detekt still
reported review-level warnings elsewhere in the repository, including the pre-existing `CatalogDebugDiagnostics`
function-count signal, but no Detekt error blocked the successful host gate.

Because this cleanup removed the test-only artwork compatibility façade and materially rewrote four
instrumentation classes to use the current `:core:artwork` API directly, the user also ran the focused connected
gate on Redmi Note 9S / Android 15:

```powershell
.\gradlew.bat :feature:catalog:connectedDebugAndroidTest `
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.feature.artwork.ArtworkPreflightInstrumentedTest,app.openstory.catalog.feature.artwork.LocalCoverContinuityInstrumentedTest,app.openstory.catalog.feature.plugin.MangaUpdatesCatalogIntegrationTest,app.openstory.catalog.feature.plugin.MangaUpdatesCatalogBoundaryIntegrationTest' `
  --no-daemon
```

Returned connected evidence:

```text
Starting 25 tests on Redmi Note 9S - 15
Finished 25 tests on Redmi Note 9S - 15
BUILD SUCCESSFUL in 1m 44s
134 actionable tasks: 9 executed, 125 up-to-date
Configuration cache entry stored.
```

All 25 focused connected tests completed with zero skipped and zero failed tests. This closes the materially
rewritten Android-test surface; Discover screenshot/lifecycle tests that only received package/type import
migrations remain covered by Android-test compilation here and by the later campaign/final broad gates.

### Baseline/startup profile note

The checked-in release Baseline/Startup Profile files still contain entries for pre-cleanup Catalog FQNs such
as `CatalogCompositionKt` and `feature/assets/*`. This cleanup does **not** synthesize replacement profile
entries or claim those generated artifacts are current performance evidence. The canonical Step 3 design
already requires final baseline/startup-profile regeneration after the final Step 3 production graph is
assembled; until that regeneration, stale moved-class entries may be ignored by ART and must not be used as
evidence that the moved Catalog paths remain profile-covered. This is a performance-artifact validity note,
not a correctness blocker for this module-local source cleanup.

## Exact resume boundary

**`:core:artwork` and `:feature:catalog` are both completed/accepted. STOP here before selecting the next
primary cleanup module; do not begin another module or Step 3 Task 11 without a new explicit authorization.**

Commit the Catalog cleanup independently at this accepted checkpoint. Task 11 remains frozen until the cleanup
interlude is explicitly ended.

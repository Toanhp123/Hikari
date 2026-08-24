#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

discover_vm="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt"
discover_pipeline="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverProjectionPipeline.kt"
discover_refresh_pipeline="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverRefreshPipeline.kt"
discover_latest="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverLatestGrid.kt"
discover_top_rated="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverTopRatedList.kt"
home_query="$root/catalog/src/main/kotlin/app/openstory/catalog/home/CatalogHomeQuery.kt"
search_service="$root/catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt"
filter_cache="$root/catalog/src/main/kotlin/app/openstory/catalog/search/CatalogFilterCache.kt"
home_vm="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardViewModel.kt"
updates_vm="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesViewModel.kt"
settings="$root/settings.gradle.kts"
versions="$root/gradle/libs.versions.toml"
app_build="$root/app/build.gradle.kts"
benchmark_build="$root/benchmark/build.gradle.kts"
benchmark_test="$root/benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt"
profile_test="$root/benchmark/src/main/kotlin/app/openstory/benchmark/BaselineProfileGenerator.kt"
architecture_models="$root/build-logic/src/main/kotlin/app/openstory/build/architecture/ModuleBoundaryModels.kt"
backdrop_mode="$root/core/designsystem/src/main/kotlin/app/openstory/designsystem/glass/HikariBackdropMode.kt"

fail() { echo "Performance Wave 4 policy violation: $1" >&2; exit 1; }

[[ -f "$filter_cache" ]] || fail "CatalogFilterCache owner is missing"
[[ -f "$benchmark_build" ]] || fail "benchmark module is missing"
[[ -f "$benchmark_test" ]] || fail "Macrobenchmark CUJ suite is missing"
[[ -f "$profile_test" ]] || fail "Baseline Profile generator is missing"
[[ -f "$backdrop_mode" ]] || fail "benchmark backdrop mode owner is missing"

# Discover must have one repository home observation and derive semantic projection from that same emission.
[[ $(grep -o 'repository\.observeHomes()' "$discover_vm" | wc -l) -eq 1 ]] ||
  fail "DiscoverViewModel must own exactly one repository.observeHomes() source"
[[ -f "$discover_pipeline" ]] || fail "Discover projection pipeline is missing"
[[ -f "$discover_refresh_pipeline" ]] || fail "Discover refresh scheduling pipeline is missing"
grep -q 'flatMapLatest(projections::observeForStories)' "$discover_vm" ||
  fail "Discover canonical presentation is not scoped to the visible Story set"
grep -q 'combine(homes, visibleProjections, selectedContentType)' "$discover_vm" ||
  fail "Discover semantic projection must combine Home feed semantics with scoped canonical presentation"
! grep -q 'projections.observe()' "$discover_vm" ||
  fail "Discover regressed to the unbounded canonical projection stream"
grep -q 'projection.project(currentHomes, canonical, contentType)' "$discover_vm" ||
  fail "Discover Home feed semantics and canonical presentation do not share one projection call"
grep -q 'projectSemanticDiscoverContent' "$discover_pipeline" ||
  fail "Discover semantic content is not computed inside the projection pipeline"
grep -q 'homes = homes' "$discover_pipeline" ||
  fail "Discover semantic projection is not derived from the shared homes emission"
grep -q 'projections = projections' "$discover_pipeline" ||
  fail "Discover presentation is not derived from canonical projections"
! grep -Eq 'loading|refreshing|refreshReport' "$discover_pipeline" ||
  fail "Discover projection pipeline still depends on transient UI flags"
grep -q 'content.toUiState' "$discover_vm" ||
  fail "Discover transient UI flags are not assembled after semantic projection"
! grep -q 'AppDispatchers' "$discover_vm" ||
  fail "DiscoverViewModel must not own platform scheduling"
grep -q 'withContext(dispatcher)' "$discover_refresh_pipeline" ||
  fail "Discover refresh CPU work is not isolated behind the feature scheduling boundary"
! grep -q 'CatalogHomeQuery' "$discover_pipeline" ||
  fail "Discover semantic pipeline still depends on the legacy aggregate ranking projector"
! grep -Eq 'CatalogFusionEngine|CanonicalFusionService|CatalogDetailsLoader|CatalogMetadataCoordinator' "$discover_vm" "$discover_pipeline" ||
  fail "Discover owns Details or canonical fusion instead of consuming canonical presentation"
grep -q 'LazyListScope.discoverLatestItems' "$discover_latest" ||
  fail "Discover Latest is not emitted as real lazy rows"
grep -q 'LazyListScope.discoverTopRatedItems' "$discover_top_rated" ||
  fail "Discover Top Rated is not emitted as real lazy rows"
! grep -q 'Column(' "$discover_latest" ||
  fail "Discover Latest regressed to one eager Column item"
! grep -q 'Column(' "$discover_top_rated" ||
  fail "Discover Top Rated regressed to one eager Column item"
! grep -q 'rankedStories = homes' "$discover_vm" || fail "Discover still owns a second homes-derived ranking flow"
! grep -q 'val rankedStories: Flow' "$home_query" || fail "CatalogHomeQuery still owns a second cold repository observation"
grep -q 'fun rank(homes: List<CatalogHomeSnapshot>)' "$home_query" || fail "CatalogHomeQuery is not a pure ranking projector"

# Search filters must be cached by plugin id/version.
grep -q 'CatalogFilterCache' "$search_service" || fail "CatalogSearchService does not consume the filter cache"
grep -q 'data class CatalogFilterCacheKey' "$filter_cache" || fail "filter cache key owner is missing"
grep -q 'source\.version' "$search_service" || fail "search filter cache does not include plugin version"
grep -q 'retainEnabled' "$filter_cache" || fail "filter cache does not evict disabled/replaced plugin versions"

# Home/Updates must use story-scoped activity observation rather than unbounded activity flows.
for vm in "$home_vm" "$updates_vm"; do
  grep -q 'observeForStories' "$vm" || fail "scoped observation missing: $vm"
  ! grep -q 'catalog\.observe()' "$vm" || fail "unbounded catalog projection observation remains: $vm"
  grep -q 'catalog\.observeForStories' "$vm" || fail "catalog projection is not scoped to library stories: $vm"
  ! grep -q 'chapters\.observeAll()' "$vm" || fail "unbounded chapter observation remains: $vm"
  ! grep -q 'mappings\.observeAll()' "$vm" || fail "unbounded mapping observation remains: $vm"
done
! grep -q 'downloads\.observeAll()' "$home_vm" || fail "Home still observes all downloads"
! grep -q 'progress\.observeAll()' "$home_vm" || fail "Home still observes all progress"

# Benchmark/profile toolchain.
grep -q 'include(":benchmark")' "$settings" || fail ":benchmark is not declared"
grep -q 'benchmarkMacro' "$versions" || fail "Macrobenchmark version is not pinned"
grep -q 'baselineProfile' "$versions" || fail "Baseline Profile plugin version is not pinned"
agp_version="$(sed -n 's/^agp = "\([^"]*\)"/\1/p' "$versions")"
baseline_profile_version="$(sed -n 's/^baselineProfile = "\([^"]*\)"/\1/p' "$versions")"
agp_major="${agp_version%%.*}"
baseline_profile_major="${baseline_profile_version%%.*}"
baseline_profile_rest="${baseline_profile_version#*.}"
baseline_profile_minor="${baseline_profile_rest%%.*}"
if [[ "$agp_major" =~ ^[0-9]+$ && "$baseline_profile_major" =~ ^[0-9]+$ && "$baseline_profile_minor" =~ ^[0-9]+$ ]]; then
  if (( agp_major >= 9 )) && (( baseline_profile_major < 1 || (baseline_profile_major == 1 && baseline_profile_minor < 5) )); then
    fail "AGP 9+ new DSL requires the Baseline Profile Gradle Plugin 1.5.x line or newer"
  fi
fi
grep -q 'ANDROID_TEST("android-test")' "$architecture_models" || fail "architecture verifier does not recognize android-test modules"
grep -q 'targetProjectPath = ":app"' "$benchmark_build" || fail "benchmark module does not target :app"
grep -q 'android.experimental.self-instrumenting' "$benchmark_build" || fail "benchmark module is not self-instrumenting"
grep -q 'create("benchmarkRelease")' "$app_build" || fail "benchmarkRelease signing customization is missing"
grep -A3 'create("benchmarkRelease")' "$app_build" | grep -q 'signingConfig = signingConfigs.getByName("debug")' || fail "benchmarkRelease is not locally installable"
grep -q 'FrameTimingMetric()' "$benchmark_test" || fail "Macrobenchmark does not measure frame timing"
grep -q 'startupMode = null' "$benchmark_test" || fail "interaction Macrobenchmarks must not force startup mode"
! grep -q 'StartupMode.WARM' "$benchmark_test" || fail "interaction Macrobenchmarks must not force warm startup"
grep -q 'killProcess()' "$benchmark_test" ||
  fail "interaction Macrobenchmarks must reset the target process before each iteration"
grep -q 'BaselineProfileRule' "$profile_test" || fail "Baseline Profile generator does not use BaselineProfileRule"
grep -q 'isMinifyEnabled = true' "$app_build" || fail "release minification is not enabled"
grep -q 'optimization {' "$app_build" || fail "release optimization block is missing"
grep -A2 'optimization {' "$app_build" | grep -q 'enable = true' || fail "release optimization is not enabled"
grep -q 'automaticGenerationDuringBuild = false' "$app_build" || fail "Baseline Profile generation must stay out of ordinary builds"
grep -q 'dexLayoutOptimization = true' "$app_build" || fail "Startup Profile dex layout optimization is not enabled"

# Blur A/B must be explicit and benchmark-only.
grep -q 'enum class HikariBackdropMode' "$backdrop_mode" || fail "backdrop mode is not explicit"
grep -q 'DISABLED_FOR_BENCHMARK' "$backdrop_mode" || fail "benchmark-only blur disable mode is missing"
grep -q 'backdropDisabled' "$benchmark_test" || fail "blur-off benchmark is missing"
grep -q 'backdropEnabled' "$benchmark_test" || fail "blur-on benchmark is missing"

echo "Performance Wave 4 policy verified."

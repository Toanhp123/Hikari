#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
macro="$root/benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt"
driver="$root/benchmark/src/main/kotlin/app/openstory/benchmark/HikariBenchmarkDriver.kt"
activity="$root/app/src/main/kotlin/app/openstory/MainActivity.kt"
app="$root/app/src/main/kotlin/app/openstory/ui/OpenStoryApp.kt"
shell="$root/app/src/main/kotlin/app/openstory/ui/HikariAppShell.kt"
backdrop_host="$root/core/designsystem/src/main/kotlin/app/openstory/designsystem/glass/HikariBackdropHost.kt"
nav="$root/app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt"
story_destination="$root/app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt"
story_deps="$root/app/src/main/kotlin/app/openstory/navigation/StorySectionDependencies.kt"
chapter_vm="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterListViewModel.kt"
chapter_ui="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt"
mapping_vm="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingViewModel.kt"
mapping_ui="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt"
discover_vm="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt"
discover_pipeline="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverProjectionPipeline.kt"
reader_progress="$root/feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderProgressTracking.kt"
reader_state="$root/feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderUiState.kt"
reader_vm="$root/feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt"
reader_screen="$root/feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderScreen.kt"
reader_styles="$root/feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderTextStyles.kt"
shadow="$root/core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariSurfaceShadow.kt"
checkpoint="$root/docs/internal/checkpoints/performance-wave-p5.md"

fail() { echo "Performance Wave P5 policy violation: $1" >&2; exit 1; }

[[ -f "$checkpoint" ]] || fail "P5 checkpoint documentation is missing"

# Benchmark compilation must require the packaged Baseline Profile instead of silently falling back.
grep -q 'BaselineProfileMode.Require' "$macro" || fail "Macrobenchmark does not require the packaged Baseline Profile"
! grep -q 'CompilationMode.DEFAULT' "$macro" || fail "Macrobenchmark silently tolerates Baseline Profile installation failure"

# Benchmark isolation must cover the user-observed interaction hot paths.
grep -q 'fun homeDiscoverWarm()' "$macro" || fail "warm Home/Discover CUJ is missing"
grep -q 'fun storyTabSources()' "$macro" || fail "isolated Sources-tab CUJ is missing"
grep -q 'fun storyTabChapters()' "$macro" || fail "isolated Chapters-tab CUJ is missing"
grep -q 'measureStoryTab("story-tab-sources")' "$macro" || fail "Sources-tab CUJ does not use the stabilized repeated-tab measurement"
grep -q 'measureStoryTab("story-tab-chapters")' "$macro" || fail "Chapters-tab CUJ does not use the stabilized repeated-tab measurement"
grep -q 'repeat(STORY_TAB_MEASUREMENT_CYCLE_COUNT)' "$macro" || fail "isolated Story-tab CUJs do not repeat enough interaction cycles for frame traces"
grep -q 'const val STORY_TAB_MEASUREMENT_CYCLE_COUNT = 3' "$macro" || fail "isolated Story-tab CUJ measurement cycle count changed without updating the trace-stability contract"
grep -q 'clickTag("story-tab-overview")' "$macro" || fail "isolated Story-tab CUJs do not return to the common Overview baseline"
grep -q 'fun readerScrollLongChapter()' "$macro" || fail "production Reader long-scroll CUJ is missing"
! grep -q 'fun readerScrollBackdropEnabled()' "$macro" || fail "obsolete Reader backdrop-on decision CUJ is still present"
! grep -q 'fun readerScrollBackdropDisabled()' "$macro" || fail "obsolete Reader backdrop-off decision CUJ is still present"
grep -q 'fun chaptersScrollShadowEnabled()' "$macro" || fail "chapter shadow-on scroll CUJ is missing"
grep -q 'fun chaptersScrollShadowDisabled()' "$macro" || fail "chapter shadow-off scroll CUJ is missing"
grep -q 'DISABLE_SURFACE_SHADOWS_EXTRA' "$driver" || fail "benchmark driver lacks the surface-shadow A/B switch"
grep -q 'LEGACY_NAVIGATION_TRANSITIONS_EXTRA' "$driver" || fail "benchmark driver lacks the legacy-navigation A/B switch"
grep -q 'BENCHMARK_DISABLE_SURFACE_SHADOWS_EXTRA' "$activity" || fail "app does not consume the shadow benchmark switch"
grep -q 'BENCHMARK_LEGACY_NAVIGATION_TRANSITIONS_EXTRA' "$activity" || fail "app does not consume the navigation benchmark switch"
grep -q 'HikariSurfaceShadowMode' "$app" || fail "OpenStoryApp does not carry the benchmark-only surface-shadow mode"
grep -q 'LocalHikariSurfaceShadowMode' "$shadow" || fail "surface shadow owner lacks benchmark isolation"

# Top-level destinations must not animate two full scenes in production.
grep -q 'topLevelEntryMetadata' "$nav" || fail "top-level navigation transition policy owner is missing"
grep -q 'EnterTransition.None togetherWith ExitTransition.None' "$nav" || fail "top-level production transitions still animate full scenes"
grep -q 'useLegacyNavigationTransitions' "$nav" || fail "legacy transition A/B path is missing"
grep -q 'noTopLevelTransitionMetadata' "$nav" || fail "top-level transition metadata is rebuilt instead of remaining identity-stable"
grep -q 'val showFloatingNavigation = shouldShowFloatingNavigation(currentRoute)' "$shell" || fail "app shell does not isolate backdrop ownership to floating-navigation routes"
grep -q 'captureBackdrop = showFloatingNavigation' "$shell" || fail "app shell moves navigation content between composition branches instead of toggling backdrop capture"
grep -q 'captureBackdrop: Boolean = true' "$backdrop_host" || fail "backdrop host cannot disable capture without disposing its background composition"

# Story section data is deferred until after first frame, then kept warm.
grep -q 'withFrameNanos' "$story_destination" || fail "Story section prewarm is not deferred past the first frame"
grep -q 'prewarmSections' "$story_destination" || fail "Story section prewarm state is missing"
grep -q 'prewarmSections' "$story_deps" || fail "Story dependencies do not honor the prewarm state"
grep -q 'loading = false' "$chapter_vm" || fail "chapter repository emission does not clear loading"
grep -q 'state.loading' "$chapter_ui" || fail "chapter UI does not distinguish loading from real empty state"
grep -q 'loading = false' "$mapping_vm" || fail "mapping repository emission does not clear loading"
grep -q 'state.loading' "$mapping_ui" || fail "mapping UI does not distinguish loading from real empty state"

# Discover preparation and semantic projection must be one main-safe pipeline, not multiple homes-derived flows recombined later.
[[ -f "$discover_pipeline" ]] || fail "DiscoverProjectionPipeline owner is missing"
grep -q 'AppDispatchers' "$discover_pipeline" || fail "Discover projection does not use the injected dispatcher boundary"
grep -q 'dispatchers.default' "$discover_pipeline" || fail "Discover CPU projection is not routed to the Default dispatcher"
grep -q 'projectSemanticDiscoverContent' "$discover_pipeline" ||
  fail "Discover semantic content is not computed inside the same pipeline"
grep -q 'homes = homes' "$discover_pipeline" ||
  fail "Discover semantic projection is not derived from the shared homes emission"
! grep -Eq 'loading|refreshing|refreshReport' "$discover_pipeline" ||
  fail "Discover projection pipeline still recomputes semantic content for transient UI flags"
grep -q 'combine(homes, selectedContentType)' "$discover_vm" ||
  fail "Discover semantic projection is not keyed only by homes plus content type"
grep -q 'content.toUiState' "$discover_vm" ||
  fail "Discover transient UI flags are not assembled after semantic projection"
! grep -q 'CatalogHomeQuery' "$discover_pipeline" ||
  fail "Discover semantic pipeline still depends on the legacy aggregate ranking projector"
! grep -q 'rankedStories = homes' "$discover_vm" || fail "DiscoverViewModel still owns a second homes-derived ranking flow"
! grep -q 'dependencies.rankedStories' "$discover_vm" || fail "DiscoverViewModel still combines homes with a separately-emitting ranked flow"

# Reader persistence work must be sampled instead of reacting to itemOffset every frame.
grep -q 'READER_PROGRESS_SAMPLE_MILLIS' "$reader_progress" || fail "Reader scroll sampling interval is missing"
grep -q 'listState.isScrollInProgress' "$reader_progress" || fail "Reader progress tracking is not driven by scroll sessions"
grep -q 'delay(READER_PROGRESS_SAMPLE_MILLIS)' "$reader_progress" || fail "Reader progress is not sampled at a bounded cadence"
! grep -q 'snapshotFlow { listState.viewport' "$reader_progress" || fail "Reader still collects viewport changes for every scroll frame"
grep -q 'rememberReaderTextStyles' "$reader_styles" || fail "Reader scaled text styles are still rebuilt per block"
grep -q 'restoredProgressFraction' "$reader_state" || fail "Reader UI state does not preserve the restored progress fraction"
grep -q 'restoredProgressFraction = restoredForRelease' "$reader_vm" || fail "Reader ViewModel does not expose the restored progress fraction"
grep -q 'ReaderProgressUiState(fractionToPercent(state.restoredProgressFraction))' "$reader_screen" || fail "Reader chrome does not initialize progress from restored state"
grep -q 'captureBackdrop = false' "$reader_screen" || fail "Reader production path still captures the expensive backdrop during scrolling"
! grep -q 'visibleItemsInfo.firstOrNull()?.index' "$reader_progress" || fail "Reader tracking still emits an initial pre-restoration viewport update"

echo "Performance Wave P5 policy verified."

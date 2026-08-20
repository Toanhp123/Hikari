# Performance Wave 4 Data + Benchmark Design

## Goal

Remove the remaining repeat work on the highest-frequency catalog journeys and add a reproducible performance measurement/profile toolchain without changing user-visible catalog, search, home, updates, story, or reader behavior.

## Scope

Wave 4 owns six related changes:

1. Discover derives ranking from the same observed home snapshots instead of opening a second `CatalogRepository.observeHomes()` subscription.
2. Search filter definitions are cached per enabled catalog plugin id + version so reopening Search does not re-run `catalog.filters` for unchanged plugins.
3. Home/Updates stop materializing catalog/chapter/mapping/progress graphs outside the user's library, while Home replaces the all-download-record stream with a scalar completed-download count. Domain repositories gain library-scoped observation contracts; Room implements them with filtered queries/flows while preserving current ordering and error semantics.
4. Add a dedicated Android test module for Macrobenchmark and Baseline Profile generation.
5. Benchmark the agreed CUJs with `FrameTimingMetric` and generate a Baseline Profile from the same navigation paths.
6. Add an opt-in benchmark-only switch that disables backdrop blur so blur cost can be measured A/B without changing normal production visuals.

## Constraints

- Preserve Wave 1 top-level back-stack/ViewModel retention.
- Preserve Wave 2 Story lazy workloads and aggregate download observation.
- Preserve Wave 3 in-session Reader chapter changes.
- Preserve Wave 3.5 Discover one-shot empty-cache bootstrap.
- No new database schema version; DAO changes must be query-only.
- No feature may depend on `storage:room` directly.
- Benchmark/profile code stays outside production modules except for stable test tags and the benchmark-only blur switch.
- Normal app builds keep backdrop blur enabled.
- Release optimization must be enabled so profile/benchmark results represent an optimized build.

## Discover

`CatalogHomeQuery` becomes a pure projector over `List<CatalogHomeSnapshot>` through `rank(homes)`. `DiscoverViewModel` owns the single `homes` flow, preserves its latest value on failure, and maps that same flow to ranked stories. The bootstrap refresh continues to inspect that shared homes flow and remains one-shot.

This removes the second cold Room subscription without introducing a global cache or changing ranking output.

## Search filter cache

`CatalogSearchService` owns an in-memory `CatalogFilterCache`. Cache keys are `(PluginId, version)` because `CatalogSource` already exposes both values. Each `filters()` call:

- gets the currently enabled sources;
- reuses a cached group when id+version is unchanged;
- invokes `source.filters()` only for missing/new versions;
- drops cache entries for disabled or replaced plugin versions;
- does not cache thrown/failed filter loads as a successful value, so a later Search open can retry.

The cache is process-local by design. Persistence is unnecessary because the plugin JS/runtime process is already warm after app startup and plugin version is the invalidation boundary.

## Library-scoped activity data

The expensive Home/Updates inputs are catalog projections, chapter groups, content mappings, reading progress, and the full download-record list. Story-owned activity is only relevant for stories that are in the library, while the existing Home download statistic is a global completed-download count.

Repositories gain scoped APIs:

- `CatalogStoryProjectionRepository.observeForStories(storyIds: Set<StoryId>)`
- `ChapterRepository.observeForStories(storyIds: Set<StoryId>)`
- `ContentMappingRepository.observeForStories(storyIds: Set<StoryId>)`
- `ReadingProgressRepository.observeForStories(storyIds: Set<StoryId>)`

`DownloadRepository` gains `observeCompletedCount()` so Home can retain the existing global count semantics without materializing every download record. Default domain implementations may derive from existing broad flows to preserve compatibility in tests/non-Room implementations, but `Room*Repository` overrides scoped/count APIs with filtered or aggregate DAO queries. Empty story sets must emit `emptyList()` without issuing an unbounded query.

Home and Updates share the library entries stream, derive a distinct library-story-id set, and `flatMapLatest` into the scoped repository flows. This ensures DB invalidations for unrelated stories do not rebuild dashboard/activity projections.

## Macrobenchmark + Baseline Profile

Add `:benchmark` as a `com.android.test` module targeting `:app`, using `androidx.benchmark:benchmark-macro-junit4` and the `androidx.baselineprofile` plugin. Architecture verification recognizes `android-test` as a non-production platform and allows exactly one project dependency from `:benchmark` to `:app` through the target project configuration.

Measured CUJs:

- Home -> Library -> Home
- Home -> Discover -> Home
- Story Overview -> Sources -> Chapters -> Overview
- Story -> Reader -> Next chapter repeated
- Home -> Search -> Back -> Search

Macrobenchmarks use `FrameTimingMetric`. Startup/profile generation uses the same stable Compose test tags exposed as resource ids from the app shell.

The Baseline Profile generator covers startup plus these navigation paths. Generation is manual/CI-explicit, not part of ordinary `verify.sh`.

## Backdrop A/B

The benchmark module passes an instrumentation/intent extra that selects a benchmark-only `DisableBackdrop` mode. Production defaults to normal blur. Hikari's backdrop owner reads the mode once at composition boundary and substitutes the existing non-blurred surface path only when the benchmark extra is set.

Two benchmark methods run the same CUJ with blur enabled and disabled. No production heuristic or device-specific visual downgrade is introduced.

## Verification

Required static/unit gates:

- Discover ranking uses one `observeHomes()` source.
- Search cache reuses unchanged plugin versions and invalidates version/enable changes.
- Home/Updates consume scoped observation APIs rather than broad catalog/chapter/mapping/progress flows, and Home uses the aggregate completed-download count instead of `observeAll()`.
- `:benchmark` is declared, architecture policy knows `android-test`, release optimization is enabled, and benchmark/profile dependencies are pinned in the version catalog.
- Existing UI/policy/architecture/Room-schema gates stay green.

Runtime gates on the user's machine:

- targeted app/catalog/storage tests;
- Roborazzi compare;
- full `scripts/verify.sh`;
- `:benchmark:connectedCheck` for Macrobenchmark;
- `:app:generateBaselineProfile` when a supported device is available.

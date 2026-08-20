# Discover Semantic Feed Redesign Checkpoint

Date: 2026-08-20
Status: **ACCEPTED**

## Scope

This checkpoint closes the 2026-08-19 Discover redesign implemented across Tasks 1-9.
It replaces source/category-driven Discover presentation with a source-agnostic semantic
feed while preserving the existing 14-module production graph, Search/Story navigation,
pull-to-refresh, retained top-level composition, artwork/backdrop ownership, and plugin
runtime boundaries.

Normative design:
`../../superpowers/specs/2026-08-19-discover-semantic-feed-redesign-design.md`.
Implementation record:
`../../superpowers/plans/2026-08-19-discover-semantic-feed-redesign-implementation-plan.md`.

## Accepted boundary

- `CatalogFeedKind` explicitly distinguishes `POPULAR`, `LATEST_UPDATES`, `TOP_RATED`,
  and `OTHER`; Discover does not infer semantic meaning from provider IDs or section titles.
- Catalog entries may carry normalized `PublicationStatus` plus coherent
  `CatalogLatestUpdate(atEpochMillis, releaseLabel)` metadata.
- Room migration `6 -> 7` persists feed kind, publication status, and latest-update fields.
  Sparse refresh preserves richer cached metadata; latest timestamp and release label move
  together as one value.
- Discover projection consumes cached Home snapshots only, deduplicates by canonical
  `StoryId`, and runs on the injected Default dispatcher.
- Current visible order is `Search -> Popular -> Manga | Light Novel -> Latest Updates -> Top Rated`.
- Popular is manual, full-width, capped at 5, and uses non-interactive page dots for 2+ pages.
- Manga is selected/enabled. Light Novel is visible but disabled for this delivery.
- Latest Updates is capped at 9 and rendered as a non-scrolling three-column composition
  inside the outer Discover `LazyColumn`; missing trustworthy latest-update timestamps are excluded.
- Top Rated is capped at 5 and renders rank, cover, title, score, compact genres, and optional status.
- `HikariSegmentedControl` and static `HikariSkeleton` are shared design-system primitives.
- No dead `See all` action, provider-branded primary shelf, quick-category card, or catalog/source
  selector remains in the current Discover composition.

## Verification evidence

### Generic contract, ingestion, and Room schema 7

| Command | Result |
|---|---|
| `./gradlew :storage:room:assembleDebug` | PASS; schema `7.json` generated. |
| `./gradlew :plugins:api:test :catalog:testDebugUnitTest --stacktrace` | PASS. |
| `./gradlew :storage:room:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.CatalogMigrationTest,app.openstory.storage.room.catalog.RoomCatalogRepositoryTest,app.openstory.storage.room.DatabaseBaselineTest --stacktrace` | PASS, 10/10 on Redmi Note 9S / API 35 test environment. |

One initial Room repository test failure was traced to an invalid test fixture that changed
`entries` without changing `sections.items`, violating the existing `CatalogHomeMutation`
invariant before repository code executed. The fixture was corrected without production
repository/schema changes.

### Semantic projection, ViewModel, and shared design-system primitives

| Command | Result |
|---|---|
| `./gradlew :feature:catalog:testDebugUnitTest --tests '*DiscoverProjectionTest*' --tests '*DiscoverProjectionPipelineTest*' --tests '*DiscoverViewModelTest*' --stacktrace` | PASS. |
| `./gradlew :core:designsystem:testDebugUnitTest --tests '*HikariProductPrimitivesTest*' --stacktrace` | PASS after test-compatibility corrections. |
| `./gradlew :core:designsystem:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.designsystem.HikariStateComponentsTest --stacktrace` | PASS, 4/4. |

The design-system checkpoint exposed two stale/compatibility test issues rather than
production regressions: Compose test APIs were being imported as top-level extensions not
available in the pinned version, and an older `HikariThemeTest` still referenced removed
spacing aliases. The tests were updated to current APIs/tokens. A one-off `No compose
hierarchies found` failure did not reproduce in two isolated reruns and the subsequent full
4-test class rerun passed.

### Discover UI, app-shell integration, and screenshots

| Command | Result |
|---|---|
| `./gradlew :feature:catalog:testDebugUnitTest --tests '*DiscoverSemanticsTest*' --tests '*DiscoverTopLevelChromeTest*' --tests '*DiscoverViewModelTest*' --tests '*DiscoverProjection*' --stacktrace` | PASS, 35/35. |
| `./gradlew :feature:catalog:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.catalog.ui.discover.DiscoverScreenTest --stacktrace` | PASS, 2/2. |
| `./gradlew :app:testDebugUnitTest --stacktrace` | PASS. |
| `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.AppLaunchSmokeTest,app.openstory.navigation.AppNavigationTest --stacktrace` | PASS, 14/14. |
| `./gradlew :feature:catalog:compareRoborazziDebug :app:compareRoborazziDebug` | PASS. |
| `./gradlew :feature:catalog:recordRoborazziDebug :app:recordRoborazziDebug` | PASS. |
| `./gradlew :feature:catalog:verifyRoborazziDebug :app:verifyRoborazziDebug` | PASS. |

A Top Rated test originally asserted `assertIsDisplayed()` before scrolling the outer
`LazyColumn` to the section. The test was corrected to scroll the actual outer owner before
asserting rank visibility; production list limits remained unchanged.

### Macrobenchmark snapshot

Command:

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#discoverScroll' \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark \
  --stacktrace
```

Result: **PASS**, 5 repeat iterations on Redmi Note 9S. The benchmark artifact reports
`compilationMode = run-from-apk`, so these values are a redesign checkpoint snapshot rather
than a release performance budget.

| Metric | Value |
|---|---:|
| Frame count median | 275 |
| frameDurationCpuMs P50 | 11.08 ms |
| frameDurationCpuMs P90 | 13.08 ms |
| frameDurationCpuMs P95 | 13.57 ms |
| frameDurationCpuMs P99 | 18.41 ms |
| frameOverrunMs P50 | 8.27 ms |
| frameOverrunMs P90 | 11.32 ms |
| frameOverrunMs P95 | 11.83 ms |
| frameOverrunMs P99 | 14.01 ms |

The benchmark build also reported stale startup-profile entries for classes/methods that no
longer exist. They did not fail the benchmark and are intentionally left for a separate
baseline/startup-profile regeneration pass rather than being mixed into this UI checkpoint.

## Full repository verification follow-up

The first full `./scripts/verify.sh` run exposed stale repository policy text that still
required the pre-redesign Discover ranking path and hard-coded Room schemas 1-6. Those
contracts were rebased to the current semantic projection and schema 7 without weakening the
underlying single-Home-emission, dispatcher, architecture, or schema-stability rules.

The next full run reached Detekt and found four blockers introduced by this redesign:
`MatchingDeclarationName` for `HikariSegmentedOption` and three skeleton `MagicNumber`
findings. The option type was split into its own file and the skeleton counts were named;
no Detekt suppression or baseline was added.

After that cleanup, the local operator reported the final verification pass before this
documentation refresh. The exact final console transcript is not embedded in this checkpoint,
so future release evidence should continue to rely on fresh `./scripts/verify.sh` output.

## Decision

The Discover semantic-feed redesign is accepted as the current Discover implementation and
Room schema 7 is the current persistence baseline. The broader Product UI checkpoint remains
accepted; this checkpoint supersedes only its Discover-specific source/category composition.
Wave 10 is the next capability wave and must enter on schema 7.

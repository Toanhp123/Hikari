# Whole-App Performance Big Update - Wave 0 Checkpoint

Date: 2026-09-07
Branch: `perf/whole-app-big-update-v3`
Status: **TASKS 1-2 VERIFIED AND CLOSED; TASK 3 IMPLEMENTATION PRESENT / DEVICE VERIFICATION OPEN**

Design: `../../superpowers/specs/2026-09-07-hikari-whole-app-performance-big-update-design.md`
Master roadmap: `../../superpowers/plans/2026-09-07-hikari-whole-app-performance-big-update-master-roadmap.md`
Wave plan: `../../superpowers/plans/2026-09-07-hikari-perf-wave-0-contracts-and-fixtures.md`

## Committed boundary

- `2db555f test(perf): parameterize whole-app benchmark fixtures` implements Wave 0 Task 1.
- `5095164 docs: import whole-app performance update plans` places the v3 design, plans, and audit
  provenance under the governed `docs/` hierarchy.
- `685a1f8 test(perf): checkpoint aged search benchmark setup` preserves the initial Task 2 Search
  implementation and its then-open AGED failure.
- The Task 2 closeout commit fixes that failure, records final verification evidence, and advances
  the resume boundary to Task 3.
- `b131352 test(perf): add reader image cache macrobenchmarks` implements Task 3 and leaves its
  three physical-device benchmark gates explicitly open.

## Task 2 implementation present

- `BenchmarkCatalogSource` contributes one deterministic benchmark-only Search source. The exact
  query is `hikari deterministic search` and the exact result title is
  `Hikari Deterministic Search Result`.
- `ExclusiveCatalogSourceRegistry` uses the benchmark-only source set when it is non-empty and
  otherwise delegates to the production plugin registry. The benchmark build therefore avoids
  external network/plugin discovery while normal production retains its existing fallback.
- Historical browse rows use unrelated, unique author values so the aged catalog does not match
  the deterministic Search query accidentally.
- `AGED_CATALOG` changes only `catalogStories` to 3,000 and `metadataWidth` to 4,096 relative to
  `SMALL`; unrelated Library, progress, download, Reader, and Chapter dimensions remain small.
- The driver enters text through the actual editable Android node, waits for the query to appear,
  waits for `search-progress` to disappear, and then waits for the merged accessibility description
  containing the deterministic result title.
- `searchQuerySmallCatalog` and `searchQueryAgedCatalog` seed their fixture once before
  `measureRepeated`. Fixture readiness currently permits up to 300 seconds.
- `CatalogSearchServiceTest.benchmarkQueryUsesDeterministicLocalSourceWithoutNetwork` verifies the
  exact query/result contract and verifies zero fallback-registry calls.
- Aged browse scores preserve the original descending fixture shape but clamp at `0.0`, keeping all
  3,000 generated `Score` values inside the catalog model's `0.0..scale` invariant.

## Resolved AGED fixture failure

Target device: Redmi Note 9S, Android 15.

The failure was caused before persistence by the browse fixture expression
`Score(10.0 - index * 0.1, 10.0)`. `Score` requires its value to remain within `0.0..scale`; the
expression becomes negative at index 101, so the 3,000-row AGED fixture threw
`IllegalArgumentException` while constructing its in-memory entries. The SMALL fixture never
crossed that boundary.

`BenchmarkCatalogFixtureTest.aged catalog keeps every generated score within its scale` protects the
full 3,000-row dimension. It was observed RED before `benchmarkBrowseScore` existed, then GREEN after
the score value was clamped at zero. No fixture dimension was reduced.

## Task 3 implementation present

- `BenchmarkReaderDocumentSource` supplies chapter 1 as a deterministic image document when the
  selected profile has `readerImagePages > 0`. Stable asset IDs and `https://benchmark.local/...`
  locators pass the same Reader document/manifest validation as plugin-delivered images.
- `BenchmarkReaderAssetDelivery` handles only that HTTPS host in `benchmarkRelease` and returns the
  packaged `drawable-nodpi/benchmark_reader_page.png`. It remains behind the production
  `ReaderAssetDeliveryPort`; no production HTTPS validation was weakened.
- Empty contribution sets return the original `PluginReaderDocumentSourceRegistry` and
  `OkHttpReaderAssetDelivery` directly. Normal production therefore retains the prior source,
  availability, delivery, and hot-path behavior.
- Fixture setup clears automatic metadata, seeds unrelated Reader-asset rows from
  `readerAssetMetadataRows` and unrelated automatic-document rows from `automaticCacheRows`, then
  reconciles the cache ledger before document/image publication. The visible image-page count stays
  independent from both historical cardinalities.
- Cold setup clears the benchmark source's durable image generations without preloading. Warm setup
  uses the real `ReaderAssetLoader` delivery/persistence path and waits for every visible page's
  metadata publication before the benchmark process restart.
- `readerImageScrollColdCache`, `readerImageScrollWarmCache`, and
  `readerImageMemoryWarmCache` use 12 visible pages with AGED Reader/cache metadata cardinalities
  (`RA=5,003`, `AC=7,001`) while unrelated catalog/progress/Library/Chapter dimensions stay SMALL.
- `readerScrollLongChapter` still uses the default profile (`readerImagePages=0`) and continues to
  persist/read the original 24-paragraph text document.
- The Task 1/2 benchmark-only profile and catalog fixture tests moved from `app/src/test` to
  `app/src/testBenchmarkRelease`; this restores the Task 3 plan's exact debug-test command without
  changing their assertions or benchmarkRelease ownership.

## Verification evidence

The focused contract was observed RED before the registry seam existed. An earlier Task 2 revision
then passed:

```text
.\gradlew.bat :catalog:testDebugUnitTest --tests '*CatalogSearchServiceTest*' :benchmark:assemble :app:assembleBenchmarkRelease detekt --no-daemon
  BUILD SUCCESSFUL
```

The generated Catalog result currently records 9 tests, 0 failures, including
`benchmarkQueryUsesDeterministicLocalSourceWithoutNetwork`.

Current final-tree host evidence:

```text
.\gradlew.bat :app:testBenchmarkReleaseUnitTest --tests '*BenchmarkCatalogFixtureTest*' --tests '*BenchmarkFixtureProfileTest*' :catalog:testDebugUnitTest --tests '*CatalogSearchServiceTest*' --no-daemon
  BUILD SUCCESSFUL in 51s

.\gradlew.bat :benchmark:assemble :app:assembleBenchmarkRelease --no-daemon
  BUILD SUCCESSFUL in 1m 16s

.\gradlew.bat detekt --no-daemon
  BUILD SUCCESSFUL (user-supplied gate evidence)
```

Task 3 TDD/host evidence:

```text
.\gradlew.bat :app:testDebugUnitTest --tests '*ReaderAssetIntegrationTest*' --no-daemon
  BUILD SUCCESSFUL; ReaderAssetIntegrationTest: 5 tests, 0 failures/errors

.\gradlew.bat :app:testBenchmarkReleaseUnitTest --tests '*ReaderAssetIntegrationTest*' \
  :reader:testDebugUnitTest --tests '*ReaderAssetManifestFactoryTest*' \
  :benchmark:assemble :app:assembleBenchmarkRelease :app:compileDebugKotlin --no-daemon
  BUILD SUCCESSFUL; ReaderAssetManifestFactoryTest: 5 tests, 0 failures/errors
```

The new integration case was observed RED first because the exclusive Reader source/delivery seams
did not exist, then GREEN after the benchmark contribution path was implemented. The independent
cross-module reviewer could not run because the child runtime returned `404 No active credentials`;
no reviewer verdict is claimed, and root self-review covered the changed dependency cone.

Both focused device benchmarks completed 5/5 measured iterations with `BUILD SUCCESSFUL`:

| Profile | Fixture dimensions | Frame count min / median / max | CPU frame duration P50 / P90 / P95 / P99 | Frame overrun P50 / P90 / P95 / P99 |
|---|---|---|---|---|
| SMALL | 30 stories, 96-char metadata | 30 / 30 / 33 | 9.6 / 14.8 / 20.1 / 28.2 ms | -2.1 / 6.2 / 10.7 / 21.7 ms |
| AGED_CATALOG | 3,000 stories, 4,096-char metadata | 95 / 97 / 146 | 5.8 / 10.5 / 12.6 / 23.7 ms | 0.0 / 1.7 / 2.4 / 36.0 ms |

These are characterization baselines for later Wave-2 comparison, not hard-coded pass/fail budgets.

## Commands intentionally not run at stop

- No `verify.sh` or `verify-fast.sh`; both remain deferred until the whole performance program is
  ready for its final aggregate verification, per execution instructions.
- No connected/device Macrobenchmark was agent-run. Physical-device frame/memory baselines remain
  user-owned and must be reviewed before Task 3 can close.

## Resume boundary

1. Tasks 1-2 are verified and closed. The only Task 1/2 delta in Task 3 is the source-set correction
   that restores debug-test compilation; do not reopen their behavior or device baselines.
2. Task 3 implementation and focused host/build evidence are present. Do not advance to Task 4 yet.
3. Run the three user-owned physical-device gates and return concise PASS summaries plus benchmark
   medians/tails:

   ```powershell
   .\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#readerImageScrollColdCache' --no-daemon
   .\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#readerImageScrollWarmCache' --no-daemon
   .\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#readerImageMemoryWarmCache' --no-daemon
   ```

4. After those three gates pass and their results are recorded here, close Task 3 and advance the
   roadmap to Wave 0 Task 4.

# Catalog Step 2 Performance Baseline

Date: 2026-09-12
Task: 16
Status: **READY FOR USER VERIFICATION**
Source/runtime base SHA before the uncommitted Task 16 delta: `0448157966b8fec15bec28f44b36ae57093a101e`

## Scope

This record owns the deterministic Task 16 benchmark/profile evidence for the accepted Step 2
Discover and Story Detail surface. The first preliminary device measurement has returned, but the
full device matrix and regenerated profile are not accepted yet. The checked-in generated
Baseline/Startup Profile files predate the current uncommitted Task 16 code and must not be used
for the final comparison.

## Frozen Journeys

| Journey | Preparation and hard evidence |
|---|---|
| `coldFreshInstall` | FirstRun path; activation, storage, acquisition, image-session, and transport counters remain zero at the initial display. |
| `coldReturningDiscover` | Persisted 5/9/5 data; StartupTiming plus storage-ready, first-snapshot, first-cover, and content-ready traces; exactly one Discover observation query. |
| `multiSectionDiscoverScroll` | Scroll to the final Top Rated section and back to item 0 with `FrameTimingMetric`. |
| `openStoryMemoryHit` | Wait for settled Discover decode work, reset transport, open the same cover key, require zero new decode and zero transport requests. |
| `openStoryDiskHit` | Prime through the production key/cache/fetch/preflight path, force-stop, let Discover settle, trim decoded memory, reset transport, then require a new bounded off-main decode and zero transport requests on Story open. |
| `storyBackToDiscover` | Story to Discover back navigation with `FrameTimingMetric`. |
| `persistedEmptyReturningDiscover` | Persisted `Published(empty)` close/reopen path with acquisition and transport at zero and one Discover observation query. |
| `agedStorageReturningDiscover` | 5,000 unrelated direct SQLite identity/summary rows, followed by a real importer refresh; returning acquisition stays zero, Discover observation stays one query, and mutation touched counts remain <=57/<=2. |
| `longBrowseCacheStability` | 20 scroll-bottom/top and Story open/back cycles; terminal collectors, cover jobs, runtime work, and pins return to zero; peak jobs/cache ownership and mutation work remain bounded. |

The benchmark fixture also has deterministic repeated-refresh and oversized-detail rejection
preparations. Existing accepted Task 4 pin/prune and orphan-overflow barrier/Room tests remain the
correctness authority for those concurrency branches; Task 16 adds bounded mutation counts to the
aged and long-browse device evidence rather than introducing a second retention implementation.

## Trace Semantics

The frozen trace labels remain owned by `CatalogTrace`:

```text
HikariV2:catalog-activation-start
HikariV2:catalog-storage-ready
HikariV2:discover-first-snapshot
HikariV2:discover-first-cover
HikariV2:discover-content-ready
HikariV2:story-detail-requested
HikariV2:story-detail-content-ready
```

- Storage-ready latency is storage-ready minus activation-start.
- First-snapshot latency is the first persisted Discover snapshot minus activation-start.
- First-cover latency is the first successful real cover minus activation-start.
- Discover content-ready is the first non-empty semantic section composed with complete bounded
  card data in final geometry; it does not wait for all covers.
- Story content-ready is measured from the active Story detail request.

## Frozen Review Triggers

On the Redmi Note 9S/API 35 reference class, `multiSectionDiscoverScroll` requires review if a
final five-iteration median exceeds CPU P95 `16.67 ms`, CPU P99 `25.00 ms`, or frame-overrun P95
`16.67 ms`. Story open/back requires review if CPU P95 or overrun P95 exceeds `33.33 ms`.
Any same-device final P95/overrun-P95 deterioration greater than 10% from the first
correctness-green Task 16 measurement requires explanation or optimization.

Returning TTID median above `453.8025943 ms` and fresh TTID median above `433.6315159 ms` require
review only on the same Redmi Note 9S/API 35 methodology. A different device establishes a new
Step 2 baseline and is not numerically compared to those values.

## Agent-Owned Evidence

- `:catalog:runtime:testDebugUnitTest :feature:catalog:testDebugUnitTest :benchmark:assemble
  --no-daemon`: PASS on 2026-09-12 (`BUILD SUCCESSFUL in 12s`, 121 actionable tasks).
- Focused runtime/feature diagnostics RED/GREEN: active-work/pin and mutation counts, cover-job
  peak/current counts, decode target/thread evidence, and benchmark diagnostics tests pass.
- `:catalog:runtime:testBenchmarkReleaseUnitTest :feature:catalog:testBenchmarkReleaseUnitTest
  :app:compileBenchmarkReleaseKotlin --no-daemon`: PASS on 2026-09-12.
- Fresh combined runtime/feature debug + benchmark-release tests and benchmark assemble with
  `--rerun-tasks`: PASS on 2026-09-12 (`BUILD SUCCESSFUL in 1m 26s`, 172/172 tasks executed).
- `:build-logic:test --tests app.openstory.build.architecture.Step2BuildSurfaceVerifierTest
  --no-daemon`: PASS on 2026-09-12.
- Final self-review split scenario-specific fixture work into benchmark-only retention, cover, and
  pin/prune barrier owners without suppression. The resulting source sizes are 112 lines for
  `BenchmarkCatalogFixture.kt`, 192 for `BenchmarkCatalogSource.kt`, and 39-108 for the split
  owners, all within the 200-line structural ratchet.
- Post-refactor `:feature:catalog:testBenchmarkReleaseUnitTest
  :app:compileBenchmarkReleaseKotlin --no-daemon`: PASS on 2026-09-12 (`BUILD SUCCESSFUL in 16s`,
  56 actionable tasks). Full `Step2BuildSurfaceVerifierTest` also passes (`BUILD SUCCESSFUL in
  16s`, 3 actionable tasks).
- After the first `coldReturningDiscover` review exposed a missing device assertion for the frozen
  `acquisition == 0` gate, the Macrobenchmark added that assertion and `:benchmark:assemble
  --no-daemon` passed (`BUILD SUCCESSFUL in 25s`, 70 actionable tasks). The corrected journey must
  be rerun before its device evidence is accepted.
- The remaining Macrobenchmark journeys, connected image/decode evidence, broad
  architecture/Detekt/shell gates, profile generation, and physical-device acceptance remain
  user-owned and `NOT RUN`.

## Returned User-Owned Device Evidence

### Preliminary `coldFreshInstall` - 2026-09-12

- Device/methodology: Redmi Note 9S (`curtana`), API 35, build ID `BP1A.250505.005`, five cold
  iterations, `CompilationMode.Partial(BaselineProfileMode.Require)`; generated context reports
  `compilationMode=run-from-apk` and the installed APK reports `speed-profile` compilation.
- Command result: PASS (`BUILD SUCCESSFUL in 1m 48s`, 1 test, 5 measured iterations). The journey's
  per-iteration assertions prove Catalog activation, storage, acquisition, image-session, and
  transport counters remained zero at the FirstRun display.
- TTID runs: `543.952239`, `479.502657`, `479.493906`, `484.353177`, and `495.045833 ms`; reported
  median `484.353177 ms`.
- The preliminary median is `22.867%` above the Step 1 fresh median and `50.721661 ms` (`11.697%`)
  above the frozen final-review trigger. This is not yet a valid final startup comparison: both
  checked-in profile files still have the stale Step 1 SHA-256
  `0c414b23dc0f409cb1f082ce63f7dd9935845b016539e441507846500e696ad0`, while Step 10 requires the
  regenerated final Task 16 profile.
- Trace-processor review of the median-valued iteration reports a `488.523437 ms` cold-start span,
  `106.663750 ms` in `OpenDexFilesFromOat`, and the warning `Main Thread - Time spent in
  OpenDexFilesFromOat*`. This is a diagnostic lead, not an established code root cause without a
  final-profile comparison; no production optimization is authorized from this result alone.
- The first run originally produced raw JSON at:
  `benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/Redmi Note 9S - 15/HikariMacrobenchmark_coldFreshInstall-benchmarkData.json`.
- Raw Perfetto traces are the five
  `HikariMacrobenchmark_coldFreshInstall_iter000..004_2026-09-12-05-23-26..05-24-23.perfetto-trace`
  files beside that JSON. All contain the Step 1 startup labels through
  `HikariV2:destination-ready`; no Catalog trace label appears on the fresh FirstRun path. The
  later failed connected rerun cleaned the benchmarkRelease additional-output directory, so these
  raw files are no longer present locally and must be regenerated for final evidence.

### Preliminary `coldReturningDiscover` - 2026-09-12, rerun required

- Device/methodology: the same Redmi Note 9S/API 35 device and five-iteration partial-profile cold
  methodology as `coldFreshInstall`.
- First command result: PASS at the instrumentation level. TTID runs were `483.546927`,
  `493.572552`, `470.146354`, `487.892968`, and `470.746979 ms`; reported median
  `483.546927 ms`.
- Median meaningful-content spans: storage ready `146.249896 ms`, first persisted snapshot
  `186.938281 ms`, Discover content ready `260.764011 ms`, and first real cover `263.222396 ms`.
  The first-run trace contains the complete startup and Catalog milestone sequence and reports one
  Discover observation query.
- The preliminary TTID median is `17.210%` above the Step 1 returning median and `29.744333 ms`
  (`6.554%`) above the frozen final-review trigger. Since the generated profiles are still stale,
  this remains diagnostic rather than a valid final startup comparison. Representative iteration
  0 reports `99.189375 ms` in `OpenDexFilesFromOat` and the same trace-processor warning as the
  preliminary fresh result.
- Review found that the journey asserted `discoverQueries == 1` but did not assert the separate
  frozen `acquisition == 0` returning-path gate. The benchmark now contains that missing assertion;
  therefore this first run is retained for baseline numbers but is incomplete acceptance evidence
  and must be rerun.
- The first run originally produced raw JSON at:
  `benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/Redmi Note 9S - 15/HikariMacrobenchmark_coldReturningDiscover-benchmarkData.json`
  (SHA-256 `70be99ee721a7a321df574a10311deedd8244369408cbec6213531ceed77d6a7`).
- Raw Perfetto traces are the five
  `HikariMacrobenchmark_coldReturningDiscover_iter000..004_2026-09-12-05-44-36..05-45-22.perfetto-trace`
  files beside that JSON. The later failed connected rerun cleaned the benchmarkRelease
  additional-output directory, so these raw files are no longer present locally; the values and
  original JSON hash above remain documentary evidence only until the corrected rerun replaces
  them.

### Corrected `coldReturningDiscover` attempt - 2026-09-12, blocked before measurement

- The corrected command was invoked on the same Redmi Note 9S with the new `acquisition == 0`
  assertion, but failed before any measured iteration: `V2 launch-state fixture did not become
  ready.` The Gradle result was `BUILD FAILED in 2m 41s`; 1 test failed and 0 benchmark iterations
  completed.
- Focused reproduction with the same installed benchmark APK proved the fixture itself completes
  normally (`am start -W` completed in 473-1,090 ms). Device-state inspection instead found the
  secure keyguard showing, `mCurrentFocus=NotificationShade`, and the UI hierarchy containing
  `Unlock with PIN or fingerprint` rather than `HIKARI_V2_BENCHMARK_READY`. The first inspection
  also reported `isSleeping=true`; waking the display cannot dismiss the secure PIN keyguard.
- This is blocked device-precondition evidence, not a failed Catalog correctness/performance gate.
  No replacement benchmark JSON or Perfetto trace was produced. The failed connected task also
  cleaned the earlier benchmarkRelease raw outputs, so the corrected rerun must restore the raw
  evidence. Rerun only after the reference device is physically unlocked and remains unlocked
  through fixture preparation.

### Corrected `coldReturningDiscover` - 2026-09-12, PASS

- Device/methodology: the same Redmi Note 9S (`curtana`), API 35, build ID `BP1A.250505.005`, five
  cold iterations, and `CompilationMode.Partial(BaselineProfileMode.Require)` methodology. The
  generated context reports `compilationMode=run-from-apk`.
- Command result: PASS (`BUILD SUCCESSFUL in 1m 47s`, 1 test, 5 measured iterations). The passing
  journey proves both frozen returning-path assertions: `discoverQueries == 1` and
  `acquisition == 0`.
- TTID runs: `473.043281`, `490.118645`, `475.166926`, `486.268385`, and `486.615937 ms`; reported
  median `486.268385 ms`.
- Median Catalog spans: storage ready `145.500573 ms`, first persisted snapshot `179.658958 ms`,
  Discover content ready `251.900208 ms`, and first real cover `255.871354 ms`.
- This remains a pre-regeneration diagnostic startup value. The checked-in profiles are stale, so
  the final Step 10 returning comparison is not accepted from this result.
- Retained raw evidence:
  `benchmark/build/task16-retained/2026-09-12/coldReturningDiscover-corrected/`. The retained JSON
  is `app.openstory.benchmark-benchmarkData.json` with SHA-256
  `75f016d214a6603aede2dd0a292d1b7a0f24ad59aba23290477c9e0aa0d0bfc4`; the directory also contains
  the five `HikariMacrobenchmark_coldReturningDiscover_iter000..004_2026-09-12-06-36-34..06-37-27.perfetto-trace`
  files, benchmark message, and Gradle log.

### `multiSectionDiscoverScroll` - 2026-09-12, PERFORMANCE GATE FAIL

- Device/methodology: the same unlocked Redmi Note 9S (`curtana`), API 35, build ID
  `BP1A.250505.005`, five iterations, and the accepted benchmark build/compilation class.
- Command result: instrumentation and journey correctness PASS (`BUILD SUCCESSFUL in 2m 34s`, 1
  test, 5 measured iterations). The journey reached the final Top Rated section, returned to item
  0, and verified the initial content was visible again.
- The frozen Step 4 frame gate fails all three review triggers: aggregate
  `frameDurationCpuMs` P95 `30.597154 ms > 16.67 ms`, P99
  `88.974780 ms > 25.00 ms`, and `frameOverrunMs` P95
  `32.106 ms > 16.67 ms`. Aggregate P50 is also elevated: CPU `22.833672 ms`, overrun
  `18.635 ms`.
- The failure is consistent across all five iterations rather than isolated to one run. Per-run
  CPU P95 is `30.579`, `32.666`, `30.652`, `29.408`, and `30.198 ms`; per-run overrun P95 is
  `31.050`, `30.655`, `33.688`, `29.983`, and `27.656 ms`.
- Median-trace diagnosis currently localizes the dominant cost to RenderThread/GPU work rather
  than Catalog query/source work: main CPU `1484.112 ms`, RenderThread CPU `1277.314 ms`, repeated
  `App Deadline Missed`/`Buffer Stuffing`, `flush commands` `523.734 ms` across 190 calls, and
  shader compilation/cache misses totaling about `232.683 ms`/`227.380 ms`. The trace also
  contains `AndroidEdgeEffectOverscrollEffect`; overscroll and newly revealed shader warm-up must
  be separated from steady rounded-shadow/texture cost before choosing a correction.
- Retained raw evidence:
  `benchmark/build/task16-retained/2026-09-12/multiSectionDiscoverScroll/`. The retained JSON is
  `app.openstory.benchmark-benchmarkData.json` with SHA-256
  `174cadd9ecd9f0ae91d7ab11268041766ed41176cd1155bf6f6f9b53bd8695dd`; the directory also contains
  five Perfetto traces, benchmark message, and Gradle log.
- Per Task 16 Step 8, later Step 7 journeys are paused. Continue with evidence-driven diagnosis and
  one bounded correction, then rerun only `multiSectionDiscoverScroll` before proceeding.

### `multiSectionDiscoverScroll` Step 8 correction - 2026-09-12, PASS

- Root cause had two benchmark-driver parts. `return@repeat` did not leave the bounded scroll loop,
  so the driver continued issuing attempts after the target selector was visible. More materially,
  `UiObject2.scroll(Direction, 0.8f)` generated fling/edge behavior unlike the retained V1
  methodology, while every frame-test iteration also called `pm clear`, discarding the app's GPU
  shader cache. The retained traces tied the slow phases to bottom `AndroidEdgeEffectOverscrollEffect`
  rendering and top pull-to-refresh circle/path shader compilation.
- Bounded change: use selector-terminated deterministic `UiDevice.swipe` geometry matching the V1
  one-fifth edge inset and 20 steps; clear package data only for the first frame-fixture preparation,
  then force-stop and idempotently reseed through the existing importer/Room fixture for later
  iterations. Startup journeys keep their existing per-iteration clear behavior.
- TDD evidence: `HikariBenchmarkDriverTest` first failed for the missing early-exit primitive,
  deterministic swipe geometry, and first-preparation policy; the final focused connected rerun
  passes 3 tests (`BUILD SUCCESSFUL in 46s`).
- Intermediate retained diagnostics remain available and are not acceptance evidence:
  `multiSectionDiscoverScroll-driver-fix/` SHA-256
  `d6bf5b0b5f1dd36380d6723cb411f99cfc7183abc51b5697641b380b25cc5e3b`,
  `multiSectionDiscoverScroll-short-gesture/` SHA-256
  `dbaa1a92736f4b7e107edbbb9779393ab381b0df0731c5be709b6072e849c4ec`, and
  `multiSectionDiscoverScroll-baseline-swipe/` SHA-256
  `98b6f134f1294658ca114bffc3804edc831ad93cbe7ba653531558304037f2cc`.
- Final corrected command: PASS (`BUILD SUCCESSFUL in 1m 55s`, 1 test, 5 measured iterations).
  AndroidX's JSON-level pooled percentiles remain CPU P95 `26.511 ms`, CPU P99 `37.572 ms`, and
  overrun P95 `20.734 ms`; the frozen plan gate is instead the median of the five per-iteration
  percentiles. Those authoritative medians are CPU P95 `13.637 ms <= 16.67 ms`, CPU P99
  `18.922 ms <= 25.00 ms`, and overrun P95 `7.961 ms <= 16.67 ms`.
- Per-iteration CPU P95: `28.877`, `16.181`, `12.872`, `13.637`, `13.147 ms`; CPU P99:
  `58.214`, `52.677`, `18.822`, `18.922`, `18.645 ms`; overrun P95: `24.419`, `9.775`,
  `7.909`, `7.909`, `7.961 ms`. The first two cold-shader tails remain visible in the retained
  evidence; the last three iterations are consistently below every frozen threshold.
- Final retained raw evidence:
  `benchmark/build/task16-retained/2026-09-12/multiSectionDiscoverScroll-warm-cache-policy/`.
  The JSON SHA-256 is `fc165d33a795a0e20cce09aa2e178c7325679aa4b47bf0026a0f6547d110ef88`;
  the directory also contains five Perfetto traces, benchmark message, and Gradle log.

### `openStoryMemoryHit` - 2026-09-12, PERFORMANCE GATE FAIL

- Command result: instrumentation and all frozen memory-hit/query assertions PASS (`BUILD
  SUCCESSFUL in 2m 38s`, 1 test, 5 measured iterations). The journey proves transport reads remain
  zero, successful decode count does not increase, and Story observation queries remain within
  `1..4`.
- Story content-ready latency runs are `67.377709`, `65.251615`, `56.020833`, `57.555573`, and
  `57.775625 ms`; median `57.775625 ms`.
- The frozen Story-open frame gate fails: median-of-five per-iteration CPU P95 is `40.166 ms >
  33.33 ms`, and overrun P95 is `41.294 ms > 33.33 ms`. Per-iteration CPU P95 is `40.166`,
  `35.918`, `48.665`, `48.175`, and `31.605 ms`; overrun P95 is `64.587`, `37.651`, `41.294`,
  `42.290`, and `26.014 ms`.
- Retained raw evidence: `benchmark/build/task16-retained/2026-09-12/openStoryMemoryHit/`. The JSON
  SHA-256 is `537462e8d393f82e1d72269caa234ab4ff07b6eeac34d818ab866a02bf3f4c03`; the directory also
  contains five Perfetto traces, benchmark message, and Gradle log.
- Later journeys are paused. Resume Step 8 with focused trace/root-cause review of this Story-open
  gate, then rerun only `openStoryMemoryHit` after a bounded correction.

### `openStoryMemoryHit` Step 8 optimization attempts - 2026-09-12, STILL OPEN

- Baseline trace diagnosis localizes the failed frame to main-thread Story publication/layout, not
  transport, decode, or query work. The failing median trace has `Recomposer:recompose` at
  `17.389 ms`, followed by `AndroidOwner:measureAndLayout` at `35.129 ms`; the heavy layout contains
  28 text-measure slices while the full summary and metadata replace the skeleton.
- Splitting metadata into separate `LazyColumn` items did not reliably defer enough visible work.
  Median per-iteration CPU P95 remained `43.685 ms` and overrun P95 `38.222 ms`; the diagnostic is
  retained under `benchmark/build/task16-retained/2026-09-12/openStoryMemoryHit-lazy-metadata/`
  with JSON SHA-256 `6def26a4f237abfc8b743872c0afae68a104e7a7c0afc61de7bc9730837f9804`.
- Applying the first runtime state before exposing the route improved content-ready median to
  `51.482 ms` but merged the initial Story work into a larger frame. `measureAndLayout` rose to
  `31.687..53.144 ms`, Compose recompose slices increased from 14 to 22, CPU P95 median reached
  `51.303 ms`, and overrun P95 median reached `58.401 ms`. Raw evidence and a metric summary are
  retained under
  `benchmark/build/task16-retained/2026-09-12/openStoryMemoryHit-first-state-before-route/`; JSON
  SHA-256 is `6e6bd5a1b1e6ac10885ad09587487a61f5c543a73382ba2a96e955e65dfd28f4`.
- Deferring the metadata subtree by one frame reduced median overrun P95 to `32.157 ms`, but CPU P95
  median remained red at `39.047 ms`; its four layout passes still had `21.961..38.658 ms` maxima,
  with a `54.905 ms` CPU P95 tail in iteration 5. Raw evidence and a metric summary are retained
  under `benchmark/build/task16-retained/2026-09-12/openStoryMemoryHit-deferred-metadata-frame/`;
  JSON SHA-256 is `bf22b499507e36925c53f0157ce79841d55b4bf6923741860b27f0a7e2df02c6`.
- All three production/test experiments were removed after their failed measurements. The working
  tree is back at the first correctness-green Story behavior. Per the three-failed-corrections
  debugging boundary, do not stack a fourth tactical UI tweak. Resume by reviewing the Story
  summary/detail publication and layout partition as an architectural performance decision, then
  create a new RED characterization before any further production change. Later journeys remain
  paused.

## Required User Evidence

Use one explicit serial and run one journey at a time. Keep the device awake and physically
unlocked before invoking the command; a secure PIN keyguard cannot be dismissed by the benchmark
harness. Prefer Redmi Note 9S/API 35; otherwise record the device/model/API as a new local baseline.
Retain raw benchmark JSON and Perfetto paths.

```powershell
$env:ANDROID_SERIAL = '<serial>'
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#coldFreshInstall' --no-daemon
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#coldReturningDiscover' --no-daemon
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#multiSectionDiscoverScroll' --no-daemon
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#openStoryMemoryHit' --no-daemon
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#openStoryDiskHit' --no-daemon
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#storyBackToDiscover' --no-daemon
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#agedStorageReturningDiscover' --no-daemon
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#persistedEmptyReturningDiscover' --no-daemon
.\gradlew.bat :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#longBrowseCacheStability' --no-daemon
Remove-Item Env:ANDROID_SERIAL
```

After the journeys are correctness-green and the code/layout/query shape is frozen, regenerate the
profiles from the final returning Discover -> Story -> Back journey:

```powershell
.\gradlew.bat :app:generateBaselineProfile --no-daemon
Get-FileHash -Algorithm SHA256 app/src/release/generated/baselineProfiles/baseline-prof.txt,app/src/release/generated/baselineProfiles/startup-prof.txt
```

Record the final source/runtime SHA, both profile hashes, five-iteration results, raw artifact
paths, trace values, equivalent AndroidX metric-name mapping if applicable, frozen-trigger review,
transport/decode/query/work counters, and terminal ownership evidence here before Task 16 can be
accepted.

## Resume Boundary

Resume Task 16 at Step 8 by diagnosing and optimizing the failed `openStoryMemoryHit` frame gate
from its retained traces. Make one evidence-backed bounded correction, run the focused owning
checks, and rerun only `openStoryMemoryHit` on the same unlocked Redmi Note 9S. Retain the
replacement JSON/Perfetto files before invoking another connected task. Do not run
`openStoryDiskHit` or later journeys until the memory-hit Story-open gate is green or its frozen-gate
review has an explicitly accepted explanation. Re-run/finally compare `coldFreshInstall` only after
final shape is stable and the Task 16 profile is regenerated; do not start Task 17 until all Task
16 device/profile evidence is reviewed and accepted.

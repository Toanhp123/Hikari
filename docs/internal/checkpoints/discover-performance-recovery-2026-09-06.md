# Discover Performance Recovery Checkpoint

Date: 2026-09-06
Branch: `perf/discover-end-to-end`
Device: Redmi Note 9S, API 35 (`adb-91f68893-N7oZEX._adb-tls-connect._tcp`)
Status: **IMPLEMENTED AND VERIFIED ON HOST/DEVICE**

Normative design: `../../superpowers/specs/2026-09-05-discover-performance-recovery-design.md`
Implementation plan: `../../superpowers/plans/2026-09-05-discover-performance-recovery.md`

## Accepted implementation boundary

- Discover refresh returns after durable browse commit and defers canonical convergence instead of synchronously rebuilding every visible Story.
- Missing visible canonical Stories settle in deterministic ordered batches with a maximum concurrency of four.
- Latest rows and Story cards retain stable Compose identity; the Popular pager does not issue redundant page resets while data grows.
- Discover, Home, Search, Library list, and Library grid share a bounded scroll-to-top operation. Repeated requests cancel the in-flight job and the completed operation reaches index 0 / offset 0.
- The app shell retains stable backdrop composition ownership but disables expensive top-level live backdrop capture. Floating navigation uses the fallback surface.
- Benchmark browse fixtures use deterministic local artwork and expose a ready-content tag before Discover scroll measurement starts.

## Host verification

```text
.\gradlew.bat :core:designsystem:testDebugUnitTest :feature:catalog:testDebugUnitTest :app:testDebugUnitTest --no-daemon
  PASS

.\gradlew.bat :app:assembleDebug :benchmark:assemble :detekt --no-daemon
  PASS; detekt reports existing warning-level structural findings only

.\gradlew.bat :app:assembleBenchmarkRelease :benchmark:assemble --no-daemon
  PASS

& 'C:\Program Files\Git\bin\bash.exe' ./scripts/verify-fast.sh
  PASS
  BUILD SUCCESSFUL in 2m 10s
  392 actionable tasks: 39 executed, 4 from cache, 349 up-to-date
  Room schema export remained stable

.\gradlew.bat :core:designsystem:testDebugUnitTest --tests
  "app.openstory.designsystem.scroll.HikariScrollToTopTest" --no-daemon
  PASS
```

The final shared scroll-action regression test was executed RED before implementation: compilation failed because `rememberHikariScrollToTopAction` did not exist. It then passed after the helper centralized cancellation of the prior in-flight scroll.

The app-shell Roborazzi targets changed by the no-capture fallback were verified and visually inspected (`discover-light.png`, `home-dark.png`). A full `:app:verifyRoborazziDebug` also reports the pre-existing unrelated `utility-sheet.png` baseline mismatch; that unrelated golden was not updated in this performance branch.

## Baseline profile regeneration

```text
$env:ANDROID_SERIAL='adb-91f68893-N7oZEX._adb-tls-connect._tcp'
.\gradlew.bat :app:generateBaselineProfile --no-daemon
  PASS
  Finished 38 tests on Redmi Note 9S - 15
  BUILD SUCCESSFUL in 11m 3s

baseline-prof.txt: 33,009 old rules -> 36,162 new rules
  5,196 added; 2,043 removed; 30,966 unchanged
startup-prof.txt: 24,150 old rules -> 25,487 new rules
  2,669 added; 1,332 removed; 22,818 unchanged
```

The generated release baseline and startup profiles are committed with this checkpoint so the measured Discover path and current signatures are packaged by `benchmarkRelease`.

## Physical-device Macrobenchmark

All journeys use `CompilationMode.Partial(BaselineProfileMode.Require)`, five measured iterations, the deterministic benchmark fixture, and unlocked device CPU clocks.

```text
:benchmark:connectedBenchmarkReleaseAndroidTest
  HikariMacrobenchmark#homeDiscoverWarm
  HikariMacrobenchmark#discoverScroll
  HikariMacrobenchmark#discoverBackToTop
  PASS: 3/3, 0 skipped, 0 failed
  BUILD SUCCESSFUL in 5m 32s

:benchmark:connectedBenchmarkReleaseAndroidTest
  HikariMacrobenchmark#homeDiscoverHome
  PASS: 1/1, 0 skipped, 0 failed
  BUILD SUCCESSFUL in 1m 47s
```

| Journey | Frames (median) | CPU P50 | CPU P90 | CPU P95 | CPU P99 | Overrun P95 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| `homeDiscoverHome` | 19 | 15.82 ms | 35.34 ms | 52.92 ms | 105.35 ms | 49.90 ms |
| `homeDiscoverWarm` | 10 | 11.43 ms | 18.40 ms | 25.27 ms | 26.09 ms | 14.51 ms |
| `discoverScroll` | 277 | 10.53 ms | 11.93 ms | 12.43 ms | 13.91 ms | 9.86 ms |
| `discoverBackToTop` | 300 | 10.77 ms | 27.43 ms | 28.21 ms | 29.35 ms | 26.18 ms |

Perfetto traces are under:

```text
benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/Redmi Note 9S - 15/
```

## Interpretation and residual risk

- `discoverScroll` is below the historical semantic-feed checkpoint at the high percentiles (historical CPU P95 13.57 ms / P99 18.41 ms; current P95 12.43 ms / P99 13.91 ms). This is a historical comparison, not a controlled paired before/after experiment.
- `homeDiscoverWarm` is also below the retained-composition P6 checkpoint at CPU P95 (historical 36.87 ms; current 25.27 ms), again without a paired control build.
- Refresh correctness and foreground completion are covered by unit/pipeline tests, but this benchmark suite has no dedicated pull-to-refresh timing journey.
- First-entry `homeDiscoverHome` still has a long frame tail on this device (CPU P95 52.92 ms / P99 105.35 ms). The branch removes known synchronous canonical and backdrop costs, but this evidence does not justify claiming that all first-entry jank is eliminated.
- `discoverBackToTop` completes the exact-top assertion in every iteration, but its combined swipe-plus-return journey still records CPU P95 28.21 ms. A future isolated gesture benchmark or trace comparison can distinguish scroll gesture cost from the return animation itself.

## Exit

The approved Discover recovery implementation, host gates, generated profiles, and required physical-device journeys are complete. No Room schema change was introduced. Remaining performance claims should use a controlled paired build and the captured Perfetto traces rather than infer improvement solely from successful execution.

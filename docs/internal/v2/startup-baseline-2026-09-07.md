# Hikari V2 Step 1 Startup Baseline

## Provenance

- Repository commit SHA: `eb4d3bfd869a5b9df78a5802de31510b3984c3c3`
- Measurement date: 2026-09-08
- Android device model: Redmi Note 9S (`curtana`)
- Android API level: 35
- Android build ID: `BP1A.250505.005`
- Android build fingerprint: `Redmi/curtana_global/curtana:12/RKQ1.211019.001/V14.0.4.0.SJWMIXM:user/release-keys`
- Benchmark target package: `app.openstory.v2benchmark`
- Benchmark build type: `benchmarkRelease`
- Compilation mode: `CompilationMode.Partial` with `BaselineProfileMode.Require`
- Generated benchmark context: `cpuLocked=false`, `sustainedPerformanceModeEnabled=false`,
  `compilationMode=run-from-apk`
- Metric: `StartupTimingMetric.timeToInitialDisplayMs`
- Startup mode: cold
- Iterations per scenario: 5

The source SHA is the Task 14 returning-destination fix commit. Both scenarios below were measured
from that source tree. Values are copied without rounding from the generated benchmark JSON.

## Cold Fresh Install

- Iteration 0: `401.294584 ms`
- Iteration 1: `381.185885 ms`
- Iteration 2: `387.863073 ms`
- Iteration 3: `394.210469 ms`
- Iteration 4: `429.462604 ms`
- Reported median: `394.210469 ms`

Generated artifacts under
`benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/Redmi Note 9S - 15/`:

- `HikariMacrobenchmark_coldFreshInstall-benchmarkData.json`
- `HikariMacrobenchmark_coldFreshInstall_iter000_2026-09-08-10-50-14.perfetto-trace`
- `HikariMacrobenchmark_coldFreshInstall_iter001_2026-09-08-10-50-21.perfetto-trace`
- `HikariMacrobenchmark_coldFreshInstall_iter002_2026-09-08-10-50-27.perfetto-trace`
- `HikariMacrobenchmark_coldFreshInstall_iter003_2026-09-08-10-50-34.perfetto-trace`
- `HikariMacrobenchmark_coldFreshInstall_iter004_2026-09-08-10-50-41.perfetto-trace`

## Cold Returning Launch

- Iteration 0: `402.590417 ms`
- Iteration 1: `412.547813 ms`
- Iteration 2: `414.661563 ms`
- Iteration 3: `382.469635 ms`
- Iteration 4: `418.783177 ms`
- Reported median: `412.547813 ms`

Generated artifacts under
`benchmark/build/outputs/connected_android_test_additional_output/benchmarkRelease/connected/Redmi Note 9S - 15/`:

- `HikariMacrobenchmark_coldReturningLaunch-benchmarkData.json`
- `HikariMacrobenchmark_coldReturningLaunch_iter000_2026-09-08-10-32-50.perfetto-trace`
- `HikariMacrobenchmark_coldReturningLaunch_iter001_2026-09-08-10-32-59.perfetto-trace`
- `HikariMacrobenchmark_coldReturningLaunch_iter002_2026-09-08-10-33-09.perfetto-trace`
- `HikariMacrobenchmark_coldReturningLaunch_iter003_2026-09-08-10-33-18.perfetto-trace`
- `HikariMacrobenchmark_coldReturningLaunch_iter004_2026-09-08-10-33-27.perfetto-trace`

## Trace Milestones

AndroidX Benchmark's bundled aarch64 trace processor queried the `slice` table in iteration 0 of
both scenarios. Each trace contains all required labels:

- `HikariV2:application-created`
- `HikariV2:activity-created`
- `HikariV2:content-requested`
- `HikariV2:first-frame`
- `HikariV2:launch-state-resolved`
- `HikariV2:destination-ready`

## Generated Profile Hashes

- `app/src/release/generated/baselineProfiles/baseline-prof.txt`: `0c414b23dc0f409cb1f082ce63f7dd9935845b016539e441507846500e696ad0`
- `app/src/release/generated/baselineProfiles/startup-prof.txt`: `0c414b23dc0f409cb1f082ce63f7dd9935845b016539e441507846500e696ad0`

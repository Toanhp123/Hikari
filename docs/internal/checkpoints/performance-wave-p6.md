# Performance Wave P6 Persistent Top-Level Composition

Date: 2026-08-18
Status: **PROMOTED TO PRODUCTION**

## Root cause

Perfetto traces for `homeDiscoverWarm` isolated the repeatable Home → Discover warm-switch spike to the app main thread. The dominant inspected slice was `AndroidOwner:measureAndLayout` (about 72–79 ms), accompanied by a full batch of Discover text layout and image-painter remember work. The previous single-pane Navigation 3 rendering path removed inactive top-level entries from the rendered scene; returning to a previously visited tab therefore rebuilt its composition/layout work.

## Device evidence

The first P6 experiment proved the lifetime hypothesis but still measured inactive retained layers. On Redmi Note 9S (API 35, unlocked CPU, 5 repetitions), it cut the heavy-frame tail by about 60% but increased median frame count from 8 to 11 and median summed frame CPU by about 5.5%. That host was not promoted.

P6.1 changed the retained host to a custom active-only `Layout`: visited tabs stay composed, while only the active route is measured and placed. A same-build control/persistent A/B then produced:

- control `homeDiscoverWarm`: CPU P50 15.44 ms, P90 90.21 ms, P95 90.62 ms, P99 91.18 ms; median frame count 6; median per-run maximum frame about 90.58 ms; median summed frame CPU about 178.24 ms;
- P6.1 persistent: CPU P50 12.35 ms, P90 28.90 ms, P95 36.87 ms, P99 38.61 ms; median frame count 10; median per-run maximum frame about 36.92 ms; median summed frame CPU about 155.98 ms;
- the repeatable ~90 ms control spike remained present in all 5 control iterations and disappeared from all 5 P6.1 iterations;
- P6.1 reduced CPU P90 by about 68%, the median maximum frame by about 59%, and median summed frame CPU by about 12.5%.

The paired memory gate showed no material resident-memory regression:

- control median heap 17,547 KB, RSS anon 69,348 KB, RSS file 121,796 KB;
- P6.1 median heap 10,323 KB, RSS anon 68,596 KB, RSS file 121,940 KB.

The large heap decrease is not treated as a production benefit because `MemoryUsageMetric.Mode.Max` and `Mode.Last` were identical within each short run. The shipping decision relies on the frame-time win plus effectively flat RSS.

## Production architecture

`PersistentTopLevelNavDisplay` is now the production top-level rendering path. It:

- starts with only the configured start route composed;
- composes a top-level route on first selection and then records it as visited;
- keeps each visited top-level back stack in a stable keyed `NavDisplay` composition;
- uses a custom `Layout` and stable `layoutId(route)` values to measure and place only the active route;
- keeps inactive visited layers out of measurement, placement, drawing, and semantics while preserving their composition/remember state;
- reuses each tab's existing decorated Navigation 3 entries and ViewModel-store/saveable-state ownership;
- leaves nested routes inside their existing per-tab back stacks.

The benchmark-only `persistentTopLevelCompositions` flag, intent extra, paired P6 frame CUJ, paired P6 memory CUJs, and focused memory helper are retired. `homeDiscoverWarm` remains as the shipping regression CUJ and now measures the production retained host.

## Regression gates

The instrumentation contract must keep proving both lifetime properties:

1. Discover is not eagerly composed on cold Home startup, is composed once on first visit, and is not disposed when Home becomes active again.
2. A visited inactive Discover layer is not measured when Home is active, including when the host constraints change; it is measured again only when Discover becomes active.

Run the focused navigation gate:

```bash
./gradlew :app:connectedDebugAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.navigation.AppNavigationTest' \
  --stacktrace
```

Run the shipping warm-switch regression benchmark:

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#homeDiscoverWarm' \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark \
  --stacktrace
```

After this production promotion, regenerate the Baseline/Startup Profiles before final shipping performance verification because the existing generated profile text predates the final P5/P6 source shape.

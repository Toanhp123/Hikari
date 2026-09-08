# Hikari V2 Step 1 - Foundation + Clean Boot

Date: 2026-09-08
Status: **ACCEPTED**

## Authority

- Approved design: `docs/superpowers/specs/2026-09-07-hikari-v2-foundation-clean-boot-design.md`
- Implementation plan: `docs/superpowers/plans/2026-09-07-hikari-v2-step-1-foundation-clean-boot.md`
- Final runtime/source SHA: `eb4d3bfd869a5b9df78a5802de31510b3984c3c3`
- Freeze evidence commit: the commit containing this checkpoint, the startup baseline, and the
  generated Baseline/Startup Profile files.
- Startup baseline: `docs/internal/v2/startup-baseline-2026-09-07.md`

The checkpoint commit changes evidence and generated profile files only. The SHA above is therefore
the final runtime source measured by both cold-start scenarios.

## Accepted Graph

The active graph has exactly these seven included modules:

1. `:app`
2. `:benchmark`
3. `:catalog:engine`
4. `:catalog:model`
5. `:core:common`
6. `:plugins:api`
7. `:reader:engine`

`:app` has zero production project dependencies. Its only project edge is the Baseline Profile
test/tooling edge to `:benchmark`. Gradle also materializes `:catalog`, `:core`, `:plugins`, and
`:reader` as grouping parent projects; they are not additional included capability modules.

## Salvage and Quarantine

- `:reader:engine`, `:plugins:api`, and reviewed narrow `:core:common` primitives remain retained
  transplant candidates.
- `:catalog:engine` and `:catalog:model` remain quarantine/reference candidates and are not reachable
  from `:app`.
- V1 runtime/integration modules and their build surface are absent from the active graph.
- Development/benchmark identities are isolated from production, backup is disabled, and a
  same-application-ID V1-to-V2 upgrade remains outside Step 1.

## Acceptance Gates

| Gate | Result | Evidence |
| --- | --- | --- |
| 0 - Knowledge/retention | PASS | `docs/internal/v2/v1-salvage-ledger.md` classifies retained transplant and quarantine/reference ownership; clean-install/backup/upgrade semantics are explicit. |
| A - Graph | PASS | Seven `include(...)` entries; `module-boundaries.json` gives `:app` no production project edge; architecture and retained-module builds pass. |
| B - Entry points | PASS | `HikariApplication` contains trace-only `onCreate`; `MainActivity` owns window/content setup only; source scans find no domain manager, collector, scheduler, repository, engine, or plugin runtime. |
| C - Hidden initialization | PASS | `:app:verifyFoundation` passes for debug, release, benchmarkRelease, and nonMinifiedRelease merged manifests. Only the classified ProfileInstaller initializer/receiver surface remains; no forbidden network/notification permission or domain service/provider is present. |
| D - State/UX | PASS | Eight startup instrumentation tests cover launcher smoke, fresh, completion, returning, recreation, Unknown, retryable failure, and Home exposure. Both final-tree cold-start scenarios reach their required destination. |
| E - Measurement | PASS | Five exact raw values and the reported median are recorded for fresh and returning startup. Iteration-0 traces from both scenarios each contain all six required `HikariV2:*` slices. |
| F - Regression | PASS | Startup unit/UI/architecture evidence and all retained/quarantine module test tasks pass. |
| G - V2 constitution | PASS | `PERF-01..08`, the ten-field capability admission record, strict foundation policy, zero test-only production API, no package SCC, and the production Kotlin line ratchet are active. |

## Verification Evidence

| Step | Command/evidence | Result |
| --- | --- | --- |
| 1 | `./gradlew --stop` then `bash scripts/verify-fast.sh` | PASS |
| 2 | `bash scripts/verify.sh` | PASS |
| 3 | `./gradlew :app:connectedDebugAndroidTest --no-daemon` | PASS, 8 tests |
| 4 | `./gradlew :app:generateBaselineProfile --no-daemon` | PASS; startup-only generator passed and both Macrobenchmarks were skipped as intended |
| 5 | `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#coldFreshInstall' --no-daemon` | PASS, 5 measured iterations; final-tree rerun `BUILD SUCCESSFUL` in 1m19s |
| 6 | `./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest '-Pandroid.testInstrumentationRunnerArguments.class=app.openstory.benchmark.HikariMacrobenchmark#coldReturningLaunch' --no-daemon` | PASS, 5 measured iterations; `BUILD SUCCESSFUL` in 1m47s |
| 7 | Exact generated benchmark JSON, trace filenames, device identity, and profile hashes copied to the startup baseline | PASS |
| 8 | AndroidX Benchmark trace processor query of one fresh and one returning trace | PASS; each of the 6 required labels occurs once in each inspected trace |
| 9 | `./gradlew verifyArchitecture :app:verifyFoundation :build-logic:test --no-daemon` | PASS; `BUILD SUCCESSFUL` in 16s |
| 9 | Forbidden production reference, test-only API, and broad-authority source scans | PASS, no matches |
| 10 | `grep -n '^include(' settings.gradle.kts` and `./gradlew projects --no-daemon` | PASS; exactly 7 included modules, Gradle command successful in 13s |
| 11 | `./gradlew :core:common:test :catalog:model:test :catalog:engine:test :reader:engine:test :plugins:api:test --no-daemon` | PASS; `BUILD SUCCESSFUL` in 16s |
| 12 | Final spec-to-implementation review of app shell, graph/policy, manifests, benchmarks, tests, profiles, salvage ledger, and admission contract | PASS; no unresolved in-scope defect |
| 12 | `./gradlew :app:testDebugUnitTest --tests app.openstory.startup.StartupTraceContractTest :app:compileDebugAndroidTestKotlin :app:verifyFoundation --no-daemon` after the Home destination fix | PASS; `BUILD SUCCESSFUL` in 11s and the line ratchet remains green |
| 14 | `C:\Program Files\Git\bin\bash.exe scripts/verify-fast.sh` after checkpoint documentation | PASS; `BUILD SUCCESSFUL` in 5s, 72 actionable tasks |

## Baseline Profile Hashes

- `app/src/release/generated/baselineProfiles/baseline-prof.txt`: `0c414b23dc0f409cb1f082ce63f7dd9935845b016539e441507846500e696ad0`
- `app/src/release/generated/baselineProfiles/startup-prof.txt`: `0c414b23dc0f409cb1f082ce63f7dd9935845b016539e441507846500e696ad0`

The generated profiles contain the V2 shell/startup path and no `app.openstory` Library, Discover,
Search, Story, Reader, Downloads, Catalog, Chapters, Settings, Storage, or plugin-runtime namespace.

## Deferred Non-goals

Step 1 deliberately does not introduce real Home, Discover, Search, Story, Reader, Library,
Downloads, plugin provisioning/runtime, background scheduling, notifications, deep links, final
onboarding, final design system, final dependency-injection framework, Room, network clients, image
loading, Navigation 3, or an upgrade/migration path from the V1 production data directory.

Future capability admission must compare against the recorded same-device startup baseline and
satisfy `docs/internal/v2/capability-admission-contract.md`; this checkpoint does not authorize or
plan any Step 2 capability.

## Residual Measurement Limits

- The baseline is device-specific: Redmi Note 9S, API 35, with CPU locking and sustained-performance
  mode unavailable in the generated benchmark context.
- Step 1 intentionally records an empirical comparison point rather than an absolute millisecond
  threshold.
- Perfetto traces and benchmark JSON remain generated build outputs; durable exact values, artifact
  names, device identity, source SHA, and profile hashes are frozen in the startup baseline document.

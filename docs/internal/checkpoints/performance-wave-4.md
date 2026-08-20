# Performance Wave 4 Checkpoint

Date: 2026-08-15
Status: **IMPLEMENTED; LOCAL STATIC VERIFIED; GRADLE/DEVICE VERIFICATION PENDING**

## Scope

Performance Wave 4 removes repeat work that remained after navigation/Reader lifecycle Waves 1-3.5 and adds a dedicated measurement/profile toolchain. It does not change Room schema ownership, user-visible catalog semantics, or the normal backdrop visual path.

## Implemented boundary

- Discover owns one shared `CatalogRepository.observeHomes()` stream. `CatalogHomeQuery` is now a pure ranking projector over those snapshots, so Home content and aggregate ranking do not open independent Room observation pipelines.
- Discover refresh reports use the timestamp returned by the successful refresh commit instead of re-observing Home state after a write. The Wave 3.5 one-shot empty-cache bootstrap remains unchanged.
- `CatalogSearchService` uses a process-local `CatalogFilterCache` keyed by `(PluginId, pluginVersion)`. Unchanged enabled plugins reuse filter definitions; version changes and disabled plugins invalidate entries; failures are not cached.
- Home and Updates derive a distinct library-story-id set and switch catalog/chapter/mapping/progress observations with `flatMapLatest`. Room adapters implement story-filtered DAO queries, and empty story sets return an empty flow result without issuing an unbounded query.
- Home consumes a completed-download count projection rather than materializing every download record.
- `:benchmark` is an `android-test` module, outside the 14-module production graph. It owns Macrobenchmark `FrameTimingMetric` CUJs and Baseline Profile generation against `:app`.
- The Baseline Profile Gradle Plugin is pinned to `1.5.0-beta01` because AGP `9.3.0` uses the new DSL by default; the `1.4.x` plugin train still depends on the legacy AGP DSL/variant surface. Macrobenchmark runtime remains on stable `1.4.1`.
- Release minification/optimization is enabled. Baseline Profile generation stays explicit (`automaticGenerationDuringBuild = false`), while DEX layout optimization is enabled.
- A benchmark-only intent switch can disable Hikari backdrop blur for paired A/B measurements. Normal app launches retain the existing blur path.

## Measurement journeys

The benchmark module covers Home -> Library -> Home, Home -> Discover -> Home, Search reopen, Story tab switching, Reader Next x10, and paired backdrop-enabled/backdrop-disabled navigation. Story/Reader journeys use the benchmarkRelease-only deterministic fixture and target its Library card by stable Story ID.

Baseline Profile generation covers startup and the same stable top-level/Search path, plus Story/Reader when seeded data is available. Profile generation must run on a rooted device or API 33+ connected device. Frame numbers intended for product decisions should be collected on a physical device rather than treated as authoritative from an emulator.

## Verification state

Local sandbox verification completed:

- all `scripts/tests/*.sh`;
- structural suppression policy;
- package-boundary policy;
- source-layout policy;
- UI-token policy;
- current architecture policy (`14 production modules, 1 android-test module, Room schema 1..6`);
- `git diff --check`.

Gradle verification is pending on the developer machine because the sandbox cannot resolve the Gradle wrapper distribution. Strict dependency verification also requires checksums for the newly introduced Benchmark/Baseline Profile/UiAutomator artifacts; generate those checksums on the trusted developer machine rather than weakening dependency verification.

## Developer verification sequence

```bash
./gradlew --write-verification-metadata sha256 \
  :benchmark:assemble \
  :app:assembleDebug \
  --stacktrace

./gradlew :build-logic:test \
  :catalog:testDebugUnitTest \
  :feature:catalog:testDebugUnitTest \
  :core:designsystem:testDebugUnitTest \
  :storage:room:compileDebugAndroidTestKotlin \
  :benchmark:assemble \
  --stacktrace

./gradlew compareRoborazziDebug --stacktrace
./scripts/verify.sh
```

After the focused/full gates are green, generate the profile explicitly and run benchmarks on an API 33+ device. For Story/Reader coverage, the benchmarkRelease fixture seeds the Story, 12 readable chapters, cached Reader documents, and progress automatically; no title argument or manual seed is required.

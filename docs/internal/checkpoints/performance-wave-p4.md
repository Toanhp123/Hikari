# Performance Wave P4 Final Checkpoint

Date: 2026-08-16
Status: **IMPLEMENTED; OFFLINE EQUIVALENCE + STATIC VERIFICATION COMPLETE; GRADLE/DEVICE MEASUREMENT PENDING**

## Scope

P4 closes the performance implementation pass after P1-P3. It targets the two remaining algorithm/allocation paths with clear code-level scaling evidence and removes the benchmark seed-data dependency that previously blocked complete Story/Reader measurement.

## Implemented boundary

- Reader document fingerprinting is incremental and no longer constructs a whole-document canonical String before hashing.
- SHA-256 hex encoding uses a fixed lowercase lookup table instead of per-byte formatting.
- Baseline block IDs and fingerprints are preserved exactly; randomized old-vs-new sanitizer equivalence is required.
- Reader cached loads defer plugin source enumeration until the first cache miss; fingerprint-backed navigation no longer enters plugin runtime on the hot path.
- Chapter aggregation selects the best candidate in one pass instead of sorting all candidates for every release.
- Existing score ordering, explicit-conflict filtering, and lexicographic chapter-id tie-break are preserved.
- `benchmarkRelease` owns a deterministic local fixture with one library story, 12 chapters/releases, cached Reader documents, and progress fingerprints; chapter 1 is the only incomplete row so Reader always starts there through Resume.
- Because the Baseline Profile Gradle plugin copies the original `release` build-type sources into `benchmarkRelease` during DSL finalization, `app/build.gradle.kts` re-attaches the benchmark-only Kotlin directory and manifest in a later `androidComponents.finalizeDsl` callback so the fixture is packaged in the benchmark APK without leaking into production release.
- Story/Reader benchmarks and Baseline Profile generation prepare that fixture outside measured iterations; Story/Reader navigation targets the deterministic fixture card by stable Story ID; `benchmarkStoryTitle` is no longer used.
- Macrobenchmark adds separate cold-start timing and Reader max-memory journeys. Existing frame timing journeys remain memory-metric free.
- `OpenStoryApplication` remains empty. No production startup/background behavior, Room schema, or visual path is changed.

## Offline verification performed while producing the patch

- fixed Reader canonical fingerprint/block-id vector passed on both baseline and P4 implementations;
- Reader sanitizer old-vs-new randomized equivalence: 5,000 / 5,000 identical;
- fixed chapter equal-score tie-break vector passed on the baseline sorter;
- chapter aggregation old-vs-new randomized equivalence: 5,000 / 5,000 identical;
- Reader cache-hit counter harness: zero source-registry enumerations;
- benchmark fixture source and Macrobenchmark/Baseline Profile sources compiled against focused API-contract stubs after their project signatures were checked against the uploaded tree;
- Performance lifecycle, Wave 4, P1, P2, P3, and P4 policies;
- package-boundary, source-layout, current-architecture and Room-schema stability gates;
- `git diff --check` and pristine patch apply-check.

Focused Gradle verification was attempted in the sandbox with `:reader:testDebugUnitTest`, `:chapters:testDebugUnitTest`, and `:benchmark:assemble`, but the wrapper could not resolve `services.gradle.org` while downloading Gradle 9.5.0. Therefore the Gradle and device gates below remain developer-machine requirements rather than inferred passes.

## Required developer-machine verification

Run focused tests and benchmark compilation:

```bash
./gradlew \
  :reader:testDebugUnitTest \
  :chapters:testDebugUnitTest \
  :benchmark:assemble \
  --stacktrace
```

Run Detekt and repository verification:

```bash
./gradlew detekt --stacktrace
./scripts/verify.sh
```

Run the deterministic Macrobenchmark suite on the physical Redmi Note 9S (or another representative physical device):

```bash
./gradlew :benchmark:connectedBenchmarkReleaseAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.androidx.benchmark.enabledRules=Macrobenchmark \
  --stacktrace
```

No `benchmarkStoryTitle` argument or manual database seed is used. The benchmark fixture is prepared by the benchmarkRelease-only fixture Activity before each benchmark method. The P4 policy also requires the post-Baseline-Profile source-set re-attachment so the fixture Activity is present in the installed benchmark target APK.

Generate the Baseline Profile separately on an API 33+ rooted/compatible device as required by the Baseline Profile toolchain.

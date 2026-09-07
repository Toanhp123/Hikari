# Whole-App Performance Big Update - Wave 0 Checkpoint

Date: 2026-09-07
Branch: `perf/whole-app-big-update-v3`
Status: **TASK 1 COMMITTED; TASK 2 IMPLEMENTATION PRESENT; AGED DEVICE BASELINE OPEN**

Design: `../../superpowers/specs/2026-09-07-hikari-whole-app-performance-big-update-design.md`
Master roadmap: `../../superpowers/plans/2026-09-07-hikari-whole-app-performance-big-update-master-roadmap.md`
Wave plan: `../../superpowers/plans/2026-09-07-hikari-perf-wave-0-contracts-and-fixtures.md`

## Committed boundary

- `2db555f test(perf): parameterize whole-app benchmark fixtures` implements Wave 0 Task 1.
- `5095164 docs: import whole-app performance update plans` places the v3 design, plans, and audit
  provenance under the governed `docs/` hierarchy.
- The commit containing this checkpoint preserves the current Task 2 implementation for the next
  session. Task 3 has not started.

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

## Verification evidence retained from this session

The focused contract was observed RED before the registry seam existed. An earlier Task 2 revision
then passed:

```text
.\gradlew.bat :catalog:testDebugUnitTest --tests '*CatalogSearchServiceTest*' :benchmark:assemble :app:assembleBenchmarkRelease detekt --no-daemon
  BUILD SUCCESSFUL
```

The generated Catalog result currently records 9 tests, 0 failures, including
`benchmarkQueryUsesDeterministicLocalSourceWithoutNetwork`.

This green host command predates the final `AGED_CATALOG`, 300-second readiness timeout, and latest
driver adjustments. It is retained as historical evidence only; the current committed tree has not
been reverified after those edits.

The SMALL physical-device Search journey completed five measured iterations after the editable-node
and merged-accessibility selectors were corrected. Its result artifacts were overwritten by the
subsequent AGED attempt, so this checkpoint intentionally records no performance numbers.

## Open AGED device failure

Target device: Redmi Note 9S, Android 15.

The latest `searchQueryAgedCatalog` attempt did not produce a valid benchmark baseline. During
fixture preparation, the target app displayed a white status screen containing:

```text
Hikari_Benchmark_Failed:IllegalArgumentException
```

The instrumentation run was then canceled. The retained XML reports zero test failures but also
contains `AndroidInstrumentationDriver was canceled` and a negative duration, so it must not be
interpreted as PASS. The retained per-test logcat ends near test startup and does not contain the
originating exception stack. Root cause is unresolved.

## Commands intentionally not run at stop

- No Task 2 Gradle test/build/Detekt rerun after the latest edits.
- No second AGED reproduction or logcat capture.
- No `verify.sh` or `verify-fast.sh`; both remain deferred until the whole performance program is
  ready for its final aggregate verification, per execution instructions.

## Resume boundary

1. Start from this branch and this checkpoint; do not reimplement Task 1 or the Task 2 Search wiring.
2. Reproduce only `searchQueryAgedCatalog` while capturing the application-side exception stack
   from fixture preparation.
3. Fix the setup failure without reducing the 3,000-row or 4,096-character AGED dimensions and
   without scaling unrelated fixture dimensions.
4. Rerun the focused Catalog test, benchmark/app assembly, and root `detekt` on the final Task 2 tree.
5. Capture valid SMALL and AGED baseline results in this checkpoint, then close Task 2.
6. Do not begin Wave 0 Task 3 until Task 2 device evidence is valid.


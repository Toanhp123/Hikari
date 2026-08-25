# Adaptive Reader Continuity / HES-v1 — M4 Checkpoint

Date: 2026-08-25
Status: **VERIFIED / CLOSED**
Next: **M5 READY / UNBLOCKED** at the HES boundary. Wave 10 final host/API 26/API 37 acceptance remains independently open.

## Scope

M4 implements Tasks 17–24 from the rebased HES-v1 plan without enabling M5+ UI/prefetch/hedge ownership:

- exact LOCAL/REMOTE/candidate eligibility and language/network hard rules;
- fixed-point feature normalization, deterministic ranking, and separate remote access evaluation;
- incumbent resolution, explicit arbitration, and 800/350 hysteresis thresholds;
- deterministic LOCAL -> same-winner REMOTE -> ranked fallback construction with recovery <= 6 and planned foreground REMOTE <= 4;
- Reader-owned cache facts backed by one bounded schema-11 metadata query and deterministic fingerprint selection without blob reads;
- Reader-owned foreground network facts with Android connectivity APIs isolated to `:app`;
- session-local distinct chapter-graph revisioning plus hard/soft graph and language-policy invalidation;
- adaptive snapshot assembly and same-generation plan revisioning for hard cache/network/availability/circuit/graph facts;
- process HALF_OPEN probe leases remain owned by the M3 limiter/registry; M4 passes only held permission into the pure engine;
- hedging and prefetch remain disabled.

## Self-review fixes incorporated

1. `ReaderCacheFactsPort` is a single-abstract-method `fun interface`, matching its lambda wiring/test usage.
2. Task 21 has an instrumentation contract proving a 32-release inspection is served by one bounded Room metadata query; no schema/entity/index/migration change was introduced.
3. Android connectivity adapter path/package follows the locked structure and no Android connectivity API leaks into `:reader` or `:reader:engine`.
4. Target disappearance, tombstone, empty target, planned-release removal, and release-ID rebound to another canonical chapter hard-invalidate an active uncommitted plan.
5. Source disablement or circuit OPEN/HALF_OPEN-without-held-lease after snapshot is revalidated immediately before REMOTE effect launch; stale transport is not launched.
6. A connection failure may resample network state. A fresh definite `OFFLINE` with no future local path hard-invalidates the plan; the old plan revision prevents stale REMOTE fallbacks from launching.
7. Confirmed local corruption records only the exact bad locator. The existing planned recovery chain gets first chance, so local failure does not blindly replan the same primary.
8. Target resume fingerprint is used only for exact cache locator/restoration semantics and is never passed to remote fetch as provider-truth integrity expectation.
9. Local-preferred candidate scoring is remote-health independent; OPEN/degraded remote health cannot degrade an otherwise usable LOCAL path.
10. M4 remains hedge-free and prefetch-free.

## Sandbox verification evidence

Before developer-host verification was available, the supplied execution environment had no cached Gradle 9.5.0 distribution and could not reach `services.gradle.org`. That limitation affected only sandbox Gradle execution; it did not replace the developer-host acceptance gate.

Fresh sandbox checks on the final M4 implementation tree were:

```text
pure :reader:engine Kotlin compile: GREEN
M4 engine behavior harness: M4_ENGINE_HARNESS_OK
schema guard: Room version remains 11; no MIGRATION_11_12/version 12
Android connectivity leakage guard: GREEN
Wave 10 background-network-policy coupling guard: GREEN
scripts/tests/verify-package-boundaries-test.sh: GREEN
scripts/verify-package-boundaries.sh: GREEN
scripts/verify-current-architecture.sh: GREEN
  -> 17 production modules, 1 android-test module, Room schema 1..11
```

## Developer-host verification evidence

Developer-host verification on 2026-08-25 first exposed regressions that focused M4 tests could not reveal. The closeout hotfix corrected the root causes before M4 was promoted:

1. legacy engine compatibility/differential tests were rebased from superseded M1/M3 ranking/availability assumptions to the M4 adaptive overlap envelope;
2. `ReaderRouteSession` first graph emission now establishes the readiness baseline without spuriously incrementing `ReaderPlanRevision`;
3. the language-policy invalidation regression now isolates one hard invalidation instead of combining an empty-target graph invalidation with the language change;
4. the Room 32-release query-count instrumentation now counts the bounded `chapter_storage_entries` SELECT rather than Room-internal bookkeeping SELECTs. Production still performs one bounded DAO metadata query.

After those fixes, the developer-host gates were rerun and reviewed:

```text
focused :reader:engine adaptive tests: BUILD SUCCESSFUL
focused :reader adaptive routing/replan tests: BUILD SUCCESSFUL
:downloads DownloadAwareReaderCacheFactsTest: BUILD SUCCESSFUL
:app AndroidReaderNetworkFactsPortTest: BUILD SUCCESSFUL
:storage:room unit/android-test compile + :app:compileDebugKotlin: BUILD SUCCESSFUL

./gradlew :reader:engine:test :reader:testDebugUnitTest --no-daemon
  -> BUILD SUCCESSFUL in 21s
  -> 63 actionable tasks: 7 executed, 56 up-to-date

./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.downloads.RoomDownloadRepositoryTest \
  --no-daemon
  -> Redmi Note 9S / Android 15
  -> 7 tests completed, 0 failures
  -> BUILD SUCCESSFUL in 40s
  -> 166 actionable tasks: 9 executed, 157 up-to-date

./gradlew :reader:engine:test \
  :reader:testDebugUnitTest \
  :downloads:testDebugUnitTest \
  :app:testDebugUnitTest \
  verifyArchitecture \
  --no-daemon
  -> verifyApplicationIdentity: app.openstory
  -> verifyModuleBoundaries: 18 modules
  -> BUILD SUCCESSFUL in 55s
  -> 278 actionable tasks: 14 executed, 264 up-to-date

scripts/tests/verify-package-boundaries-test.sh
  -> verify-package-boundaries.sh contract verified.
scripts/verify-package-boundaries.sh
  -> Package boundary policy verified.
scripts/verify-current-architecture.sh
  -> Current architecture verified: 17 production modules, 1 android-test module, Room schema 1..11.
```

The connected Room run above is useful M4 evidence but is not a substitute for the separately rebased Wave 10 API 26/API 37 final acceptance matrix.

## Closure

M4 Tasks 17–24 are **VERIFIED/CLOSED** on the 17-production-module, Room-schema-11 HES tree. M5 is **READY / UNBLOCKED** at the HES milestone boundary. Hedging and prefetch remain disabled until their owning later milestones. Wave 10 final host/API 26/API 37 acceptance remains independently open under the existing acceptance-rebase, so Wave 11 remains blocked by that separate boundary.

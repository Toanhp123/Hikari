# Reader Image Continuity Cache / RICC-v1 Checkpoint

Date: 2026-09-05
Status: **VERIFIED/CLOSED**

## Accepted boundary

- Production graph remains 17 modules plus the `:benchmark` android-test module.
- Room current source is schema 12; schemas 1-12 are contiguous and schema 1 remains byte-frozen.
- `MIGRATION_11_12` adds the private `reader_asset_entries` metadata table and indexes.
- `:feature:reader` has no `:downloads` edge, and RICC code does not enter `:reader:engine`.
- HES-v1 routing formulas, policy versions, trace fields, and pure-engine ownership remain unchanged.
- Bundled MangaDex `1.3.1` explicitly declares
  `STABLE_ID_CHANGES_WITH_CONTENT + MUTABLE_OR_UNKNOWN + PUBLIC`; sources without an equivalent reviewed
  contract remain fail-closed and non-persistent.

## Environment

- Branch: `feature/reader-image-continuity-cache`
- Base commit: `2131512`
- Host: Windows, Temurin JDK `17.0.20`
- Device: Redmi Note 9S, Android 15 / API 35

## Verification evidence

| Gate | Result | Evidence |
|---|---|---|
| Task 15 lifecycle/security focused suite | **PASS** | `./gradlew :plugins:runtime:test :app:testDebugUnitTest :downloads:testDebugUnitTest :reader:testDebugUnitTest --tests '*PluginSessionServiceSecurityGenerationTest*' --tests '*ReaderAssetSecurityInvalidationObserverTest*' --tests '*StorageReconciliationServiceTest*' --tests '*ReaderAsset*Test*' --no-daemon`; `BUILD SUCCESSFUL`, 279 actionable tasks. |
| New Task 16 integration/UI tests | **PASS** | `./gradlew :feature:reader:testDebugUnitTest :app:testDebugUnitTest --tests '*ReaderImageContinuityTest*' --tests '*ReaderAssetIntegrationTest*' --tests '*ReaderAssetProcessRecreationIntegrationTest*' --no-daemon`; `BUILD SUCCESSFUL`, 281 actionable tasks. |
| Post-review consumed-history integration rerun | **PASS** | `./gradlew :app:testDebugUnitTest --tests '*ReaderAssetIntegrationTest*' --tests '*ReaderAssetProcessRecreationIntegrationTest*' --no-daemon`; strengthened `A -> B -> C -> D -> A` evidence marks A consumed before navigation; `BUILD SUCCESSFUL`, 268 actionable tasks. |
| Task 16 focused acceptance | **PASS** | `./gradlew :reader:testDebugUnitTest :downloads:testDebugUnitTest :feature:reader:testDebugUnitTest :app:testDebugUnitTest --tests '*ReaderAsset*' --tests '*ReaderImageContinuityTest*' --tests '*PrefetchCoordinatorTest*' --tests '*ReaderRouteSessionStateTest*' --no-daemon`; initial `BUILD SUCCESSFUL` with 295 actionable tasks, then final-tree rerun `BUILD SUCCESSFUL` with 291 actionable tasks after the consumed-history test was strengthened. |
| Broad Reader/Downloads/Feature/App regression | **PASS** | `./gradlew :reader:engine:test :reader:testDebugUnitTest :downloads:testDebugUnitTest :feature:reader:testDebugUnitTest :app:testDebugUnitTest --no-daemon`; `BUILD SUCCESSFUL`, 297 actionable tasks. |
| Gradle architecture gate | **PASS** | `./gradlew :build-logic:test verifyArchitecture --no-daemon`; `BUILD SUCCESSFUL`, module architecture verified for 18 total modules. |
| Package boundaries | **PASS** | `bash scripts/verify-package-boundaries.sh`; `Package boundary policy verified.` Git Bash was invoked explicitly because Windows `bash.exe` points to unavailable WSL. |
| Current architecture | **PASS** | `bash scripts/verify-current-architecture.sh`; verified 17 production modules, 1 android-test module, and Room schemas 1..12. |
| Current-architecture verifier contract | **PASS** | `bash scripts/tests/verify-current-architecture-test.sh`; contract mutation suite completed with exit 0. |
| Architecture Baseline 2 wording contract | **PASS** | `bash scripts/tests/architecture-baseline-2-state-test.sh`; completed with exit 0. |
| Room migration/repository device gate | **PASS** | `./gradlew :storage:room:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.readerassets.Migration11To12Test,app.openstory.storage.room.readerassets.RoomReaderAssetMetadataRepositoryTest --no-daemon`; 3/3 tests on Redmi Note 9S/API 35, `BUILD SUCCESSFUL`, 170 actionable tasks. |
| Reader connected/UI smoke | **PASS** | `./gradlew :feature:reader:connectedDebugAndroidTest --no-daemon`; 11/11 tests on Redmi Note 9S/API 35, `BUILD SUCCESSFUL`, 182 actionable tasks. |
| MangaDex package generation | **PASS** | `./gradlew :app:packageMangaDexPlugin --no-daemon`; deterministic package task completed `UP-TO-DATE`, `BUILD SUCCESSFUL`. |
| MangaDex source-contract device gate | **PASS** | `./gradlew :app:connectedDebugAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.MangaDexContentContractIntegrationTest --no-daemon`; 1/1 test on Redmi Note 9S/API 35, `BUILD SUCCESSFUL`, 348 actionable tasks. |

The first direct PowerShell invocation of the Room device gate did not run tests: PowerShell stripped the
`-Pandroid` prefix and Gradle rejected `.testInstrumentationRunnerArguments...` as an unknown task. The exact
gate was rerun through `cmd.exe` so the project property reached Gradle unchanged; that corrected run is the
3/3 PASS recorded above.

## Acceptance counters

- Same-chapter/offline revisit persists ten trusted public pages, switches network facts to `OFFLINE`, clears
  the visible working path, and rereads the retained first page locally with zero additional image-delivery calls.
- Warm history `A -> B -> C -> D -> A` rereads retained A bytes locally with zero additional delivery calls.
- Runtime recreation reacquires the semantic document twice, performs one image delivery, records one disk hit,
  and returns identical persisted bytes from the recreated coordinator/single-flight/store-facing runtime.
- Fail-closed runtime replacement performs two remote deliveries and leaves zero Reader asset metadata rows.
- Compose/Coil acceptance clears memory before render and observes exactly one semantic
  `ReaderPageAssetRequest`, rather than a raw URL or storage-owned model.
- Room migration/repository instrumentation is 3/3, Reader connected UI is 11/11, and bundled MangaDex contract
  instrumentation is 1/1 on the API 35 device.

## Closure decision

RICC-v1 is accepted on the current final tree. Trusted and persistence-authorized image bytes can survive normal
Reader navigation and process-scoped runtime recreation within the unified automatic-cache budget. Semantic
Reader document reconstruction remains a separate operation after process recreation; the checkpoint proves two
document-source calls and only one image-delivery call rather than claiming offline semantic reconstruction.

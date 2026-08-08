# Wave 05 Task 04 — Home Refresh and Cached Projection Verification

Date: 2026-08-09
Status: **VERIFIED**

## Scope

This evidence record covers Wave 05 Task 04 only: the Android `:feature:home` module,
hosted Catalog DTO normalization, bounded resilient refresh, cached freshness reporting,
and combined/source-specific Home projections. It does not accept Task 05 UI or the
Wave 05 checkpoint as a whole.

## Implementation evidence

- `:feature:home` is declared as an Android library with direct production project
  dependencies limited to `:core:common`, `:core:model`, `:core:database`,
  `:core:plugin-host`, and `:core:matching`.
- `CatalogSnapshotMapper` is the plugin-wire boundary: hosted plugin ID/version,
  `contentType`, authors, image URL, and raw score/scale are copied without fallback
  guessing before persistence.
- `RefreshHome` bounds concurrent catalog operations, converts ordinary plugin exceptions
  to isolated typed failures, propagates cancellation, and persists each successful
  catalog independently before slow siblings need to finish.
- Failed plugin or repository operations do not call a destructive fallback; their last
  cached Home snapshot remains the source for refresh timestamp and stale state.
- `ObserveCombinedHome` reads only Task 01 cached repository flows. It deduplicates one
  source entry appearing in multiple sections before Task 03 aggregate ranking, groups
  cards by canonical `StoryId`, and retains every raw source score/scale plus source
  section membership.
- Task 04 changes no Room entity, DAO, database version, or committed schema file.

## Required target verification

Run on the JDK 17 target checkout:

```bash
./gradlew :feature:home:testDebugUnitTest \
  --tests app.openstory.home.domain.CatalogSnapshotMapperTest.hostedVersionAndCardContentTypeSurviveNormalization \
  --stacktrace
./gradlew :feature:home:testDebugUnitTest --stacktrace
./scripts/check-module-dependencies.sh
./scripts/verify.sh
```

Expected: all commands finish successfully, module-boundary verification reports 10
modules, and Room schema stability remains unchanged.

## Current evidence

| Gate | Result | Note |
|---|---|---|
| Focused `CatalogSnapshotMapperTest` | PASS | `:feature:home:testDebugUnitTest --tests app.openstory.home.domain.CatalogSnapshotMapperTest.hostedVersionAndCardContentTypeSurviveNormalization` finished with `BUILD SUCCESSFUL`. |
| Complete `:feature:home` unit suite | PASS | `:feature:home:testDebugUnitTest` finished with `BUILD SUCCESSFUL`. |
| Detekt | PASS | `./gradlew detekt --stacktrace` finished with `BUILD SUCCESSFUL` after the test-only line-length/opt-in cleanup. |
| Architecture Gradle gate | PASS | `scripts/check-module-dependencies.sh` reported `Module architecture verified for 10 modules.` |
| Repository `scripts/verify.sh` | PASS | Full repository verification finished with `BUILD SUCCESSFUL`; source layout and baseline architecture passed. |
| Room schema stability | PASS | Full verification reported `Room schema export remained stable during verification.` Task 04 changes no database/schema file. |
| Production Kotlin smoke compilation | PASS | Pre-target smoke compilation caught and fixed a callable-reference compile error in `ObserveCombinedHome` before target verification. |

## Exit condition

Satisfied. Wave 05 Task 04 is verified and Wave 05 Task 05 may begin. This record accepts
only the Task 04 Home refresh/cached projection boundary; it does not accept Task 05 UI or
the Wave 05 checkpoint as a whole.

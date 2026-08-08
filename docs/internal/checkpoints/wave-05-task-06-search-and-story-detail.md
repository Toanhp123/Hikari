# Wave 05 Task 06 — Search and Source-Preserving Story Detail Verification

Date: 2026-08-09
Status: **VERIFIED**

## Scope

This evidence record covers Wave 05 Task 06 only: cancellable multi-catalog search,
source-scoped filters, Task-03 canonicalized result presentation, memory-only recent
searches, the `:feature:story` module, exact Catalog detail normalization, source-preserving
metadata enrichment, and story-detail presentation. It does not accept the Wave 05 checkpoint
or authorize Wave 06.

## Implemented boundary

- Blank search requests stay local and do not invoke enabled Catalog plugins.
- Search uses debounce plus `flatMapLatest`; cancellation propagates instead of allowing an
  older request to replace a newer query.
- Each search request pins its enabled hosted Catalog instances once and keeps filter values
  scoped to the owning plugin ID.
- Search matching uses the Task-03 resolver against cached canonical Home candidates and
  request-local candidates; source badges and raw score/scale values remain separate.
- Search pages are transient and are not persisted to Room.
- Recent searches are presentation memory only; Task 06 adds no persistence table.
- `CatalogDetailsMapper` copies the hosted plugin ID/version and every detail field exposed by
  the current Catalog contract into `CatalogSourceMetadata` without guessing absent values.
- Story detail enriches only the requested source through
  `CatalogRepository.upsertSourceMetadata(...)`; it does not mutate Home membership or create
  Library membership.
- Story-detail UI projection preserves each linked catalog score, scale, source ID/version,
  and `fetchedAtEpochMillis` and exposes no Room entity or plugin DTO.
- `:feature:story` declares only the reviewed production dependencies: `:core:common`,
  `:core:model`, `:core:database`, `:core:plugin-host`, and `:core:matching`.
- Task 06 changes no Room entity, DAO, database version, migration, or committed schema file.

## Required target verification

Run on the target checkout with JDK 17:

```bash
./gradlew :feature:story:testDebugUnitTest \
  --tests app.openstory.story.domain.CatalogDetailsMapperTest.detailsMapToRichSourceMetadataWithHostedVersion \
  --stacktrace
./gradlew :feature:home:testDebugUnitTest \
  --tests app.openstory.home.domain.SearchCatalogsTest.changingQueryCancelsInFlightSearchBeforeNewDebounceExpires \
  --stacktrace
./gradlew :feature:home:testDebugUnitTest :feature:story:testDebugUnitTest --stacktrace
./gradlew :feature:home:connectedDebugAndroidTest :feature:story:connectedDebugAndroidTest --stacktrace
./scripts/check-module-dependencies.sh
./scripts/verify.sh
```

Expected architecture count after Task 06: **11 modules**. Room schema export must remain
stable.

## Current evidence

| Gate | Result | Evidence |
|---|---|---|
| Task-06 source/tests present | PASS | Search/domain/UI and story/domain/UI sources plus focused tests are present in the implementation patch. |
| Patch/source whitespace and static source-layout checks | PASS | Sandbox static checks completed before patch handoff. |
| Focused Gradle tests | PASS | Search cancellation and both Story Detail regression tests passed. |
| Feature unit suites | PASS | `:feature:home:testDebugUnitTest` and `:feature:story:testDebugUnitTest` passed. |
| Home + story Android instrumentation | PASS | Home: 5/5; Story: 1/1 on API 37 emulator. |
| Module-boundary Gradle gate | PASS | `check-module-dependencies.sh`; architecture verified for 11 modules. |
| Repository `scripts/verify.sh` | PASS | Source layout, architecture, lint, tests, and build verification passed. |
| Room schema stability | PASS | Database checkpoint passed on API 26 and API 37; schema remained stable. |

## Decision

Task 06 implementation and all required target gates are verified. This record accepts Task 06
only; it does not accept the broader Wave 05 checkpoint or authorize Wave 06.

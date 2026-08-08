# Wave 05 Catalog Home and Discovery Checkpoint

Date: 2026-08-09
Status: **PASS - Wave 05 accepted; Wave 06 Task 01 may begin**

## Reviewed boundary

- Branch: `feature/wave-05-task-06-search-and-story-detail`
- Verified implementation HEAD: `56767c9`
- Task commits: `724d8ed`, `e92282e`, `693dd96`, `6c8ee8d`, `e19da94`, `56767c9`
- Verification started from a clean working tree.

## Acceptance evidence

| Requirement | Result | Evidence |
|---|---|---|
| Room remains schema 1 with the catalog Home tables and source identity index | PASS | `SchemaPolicyTest.databaseBaselineIsExactlySchemaOne`, `SchemaPolicyTest.schemaOneContainsWave05CatalogHomePersistence`, and the API 26/API 37 database checkpoint |
| Refresh preserves ordering, plugin provenance, timestamps, and richer details | PASS | `RoomCatalogRepositoryTest.sectionAndItemOrderRoundTrips`, `pluginVersionAndSingleRefreshTimestampAreRetained`, and `homeRefreshDoesNotEraseRicherCatalogDetails` |
| One catalog failure preserves its cache and does not block successful catalogs | PASS | `RefreshHomeTest.oneCatalogFailureStillPersistsSuccessfulCatalog`, `failedCatalogReportsPreviousCachedTimestampWithoutReplacingIt`, and `thrownPluginFailureIsIsolatedFromSiblingCatalogs` |
| Removing Home membership preserves canonical/source data and Library boundaries | PASS | `RoomCatalogRepositoryTest.removedHomeCardKeepsCatalogEntryAndCanonicalStory`, `sourceMetadataUpsertDoesNotChangeHomeMembership`, and `discoveryIngestDoesNotCreateLibraryMembership` |
| Canonical cards retain separate catalog scores, scales, and labels | PASS | `SearchCatalogsTest.matchingCombinesDuplicateResultsButPreservesSourceScores`, `AggregateRankingTest.rankingNormalizesScoresWeightsPrioritiesAndPreservesSourceEntries`, and Story Detail projection tests |
| Search cancellation rejects stale results and detail enrichment preserves Home membership | PASS | `SearchCatalogsTest.changingQueryCancelsInFlightSearchBeforeNewDebounceExpires`, `StoryDetailViewModelTest.detailFollowsCanonicalStoryIdReturnedByMetadataUpsert`, and repository membership tests |
| Home and Story accessibility expose source, score, state, and actions without color-only meaning | PASS | Home instrumentation 5/5 and Story instrumentation 1/1 on API 37, including `HomeScreenTest` and `StoryDetailScreenTest` semantics coverage |
| Module governance accepts the Wave 05 graph | PASS | `check-module-dependencies.sh`; architecture verified for 11 modules with no stale permissions |

## Verification runs

| Command | Result |
|---|---|
| `./scripts/check-module-dependencies.sh` | PASS - `BUILD SUCCESSFUL`; 11 modules accepted |
| `./scripts/verify.sh` | PASS - source layout, architecture, unit tests, lint, detekt, assemble, and Room schema stability |
| Full Wave 05 Gradle suite from the implementation plan | PASS - model, database, plugin API/host, matching, Home, Story, and `:app:assembleDebug` |
| `./scripts/checkpoints/database.sh` | PASS - 31 database tests on API 26 and 31 on API 37; application checkpoint passed on both |
| Home and Story connected Android tests | PASS - Home 5/5 and Story 1/1 on API 37 |
| `git status --short` before verification | PASS - clean working tree |

Room remained at schema version 1, the committed schema export did not drift, and the
module graph remained at 11 governed modules. No ignored failing test, lint, detekt, or
module-boundary error remains.

## Decision

All Wave 05 checkpoint requirements are demonstrated against the verified implementation
HEAD. Wave 05 is closed, and the repository execution boundary advances to Wave 06
Task 01: metadata-only Library persistence and story matching foundations.

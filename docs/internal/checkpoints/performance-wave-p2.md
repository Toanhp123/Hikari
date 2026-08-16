# Performance Wave P2 Checkpoint

## Scope

Performance Wave P2 targets growth-dependent work in catalog matching, Room catalog persistence/projection, and chapter pagination.

### Catalog matching

- `CatalogMatchIndex` owns O(1) source identity lookup plus an inverted normalized-title-token shortlist.
- Candidate title/author normalization is prepared once per index snapshot/fork rather than once per incoming-item comparison.
- Evidence ranking retains only best and second auto-link candidates in one pass; it does not allocate/sort all match results.
- Multiple source rows for one canonical Story remain one indexed Story while preserving per-source title/author/content-type evidence and legacy minimum-lead self-competition semantics.
- Search, Home refresh, and Details use indexed story lookup rather than scanning candidates after resolution.
- Search/Home work on forked indices so an invalid source page cannot partially mutate shared projection state.

### Room catalog

- Home observation watches only catalog entries referenced by `catalog_home_items`, not all catalog entries.
- Home sections/items are grouped and catalog entries converted once per combined emission.
- Home refresh bulk-loads existing source rows with one `IN` query instead of one `findEntry` query per item.
- `matchSnapshot()` reads stories/entries transactionally and collapses rows by canonical Story without changing the Room schema.

### Chapter pagination

- A page-sync run lazily loads at most one `ChapterGraphSnapshot` after the first successful source page.
- Successful page commits roll the graph forward in memory for later pages; `commitPage()` does not reread the whole story graph.
- Canonical chapter restore is batched by linked chapter IDs rather than executed once per release link.
- Sync state remains committed after every successful page as before.

## Required verification

Run the focused JVM tests:

```bash
./gradlew \
  :catalog:testDebugUnitTest \
  :chapters:testDebugUnitTest \
  :storage:room:testDebugUnitTest \
  --stacktrace
```

Run the Room catalog instrumentation contract on a connected device/emulator:

```bash
./gradlew :storage:room:connectedDebugAndroidTest \
  -Pandroid.testInstrumentationRunnerArguments.class=app.openstory.storage.room.catalog.RoomCatalogRepositoryTest \
  --stacktrace
```

Then run the repository verification entry point:

```bash
./scripts/verify.sh
```

Expected static checkpoint lines include:

- `Performance lifecycle policy verified.`
- `Performance Wave 4 policy verified.`
- `Performance Wave P1 policy verified.`
- `Performance Wave P2 policy verified.`
- `Room schema export remained stable during verification.`

## Sandbox verification performed while producing the patch

- Production matching core compiled with `kotlinc`.
- 5,000 randomized resolutions matched the pre-P2 `StoryMatcher` exactly.
- 10,000 randomized resolutions matched full-list resolution and the token-indexed resolution exactly under the default policy.
- 5,000 randomized duplicate-canonical-Story cases matched the pre-P2 matcher exactly after evidence collapse.
- Catalog Search runtime harness merged two source cards into one canonical Story.
- Chapter two-page runtime harness completed with two durable commits and one graph snapshot.
- Modified Room catalog/chapter sources compiled against Room API stubs to validate Kotlin types/signatures.
- P1/P2/lifecycle/Wave 4 policies, package boundaries, source layout, current architecture, and Room schema-stability checks passed individually.

Full Gradle and Android instrumentation verification must be run in the real project environment before this wave is committed as verified.

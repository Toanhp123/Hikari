# Performance Wave P2 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Remove catalog matching, Room catalog, and chapter pagination work that scales unnecessarily with total stored data or page count.

**Architecture:** Add a copyable catalog match index around pre-normalized per-source evidence and a title-token shortlist; make Room return one candidate per canonical story and bulk/project only rows needed by Home; keep a rolling chapter graph across page commits. Preserve existing persistence boundaries and deterministic matching rules.

**Tech Stack:** Kotlin, coroutines/Flow, Room, kotlin.test, AndroidX Room instrumentation tests, Bash repository policies.

## Global Constraints

- No Room schema/version change.
- No matching threshold change.
- Invalid/failing source pages remain atomic.
- Chapter sync state remains committed page-by-page.
- No unrelated plugin-runtime/Reader optimization in this wave.

---

### Task 1: Indexed catalog matching

**Files:**
- Create: `catalog/src/main/kotlin/app/openstory/catalog/matching/CatalogMatchIndex.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/matching/StoryMatcher.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/matching/TitleNormalizer.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt`
- Modify: `catalog/src/main/kotlin/app/openstory/catalog/home/CatalogRefreshService.kt`
- Test: `catalog/src/test/kotlin/app/openstory/catalog/matching/CatalogMatchIndexTest.kt`

**Interfaces:**
- Produces: `CatalogMatchIndex.resolve(candidate)`, `story(storyId)`, `fork()`.
- Consumes: existing `CatalogMatchCandidate`, `StoryMatcher`, `StoryResolution`.

- [x] Write tests for direct source-key lookup, evidence match, fork isolation, and created-story lookup.
- [x] Run tests/policy and confirm RED because `CatalogMatchIndex` does not exist.
- [x] Implement prepared candidate normalization and single-pass best/runner-up selection.
- [x] Route Search and Home refresh through forked indices so page failure remains atomic.
- [x] Run catalog tests and static policy.

### Task 2: Room catalog bulk/scoped work

**Files:**
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogEntityMapper.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt`
- Modify: `storage/room/src/androidTest/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepositoryTest.kt`

**Interfaces:**
- Produces DAO bulk query `entries(pluginId, sourceIds)` and scoped `observeHomeEntries()`.
- `matchSnapshot()` returns one `CatalogMatchCandidate` per `StoryId` with unioned titles/authors/source keys.

- [x] Add instrumentation test for one canonical candidate from two source entries.
- [x] Add policy assertions that old full-entry Home observation and per-entry `findEntry` loop are absent.
- [x] Implement scoped Home entry observation and one-time grouping/conversion per Flow emission.
- [x] Implement bulk existing-entry load for refresh.
- [x] Implement canonical candidate collapse.
- [x] Run Room/static verification.

### Task 3: Rolling chapter pagination graph

**Files:**
- Modify: `chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterPageSynchronizer.kt`
- Modify: `chapters/src/test/kotlin/app/openstory/chapters/sync/ChapterSyncServiceTest.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterDao.kt`
- Modify: `storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomChapterRepository.kt`

**Interfaces:**
- A page-sync run calls `ChapterRepository.snapshot(storyId)` once and carries a `ChapterGraphSnapshot` forward after successful commits.
- DAO restore accepts a collection of canonical chapter IDs.

- [x] Add a multi-page sync test that counts `snapshot()` calls and expects one snapshot for the paginated run.
- [x] Confirm RED with current per-page snapshot implementation.
- [x] Move snapshot load outside the page loop and apply successful aggregation plans to a rolling graph.
- [x] Batch restore IDs in Room commit.
- [x] Run chapter tests/static verification.

### Task 4: P2 guardrail and checkpoint

**Files:**
- Create: `scripts/tests/performance-wave-p2-policy-test.sh`
- Create: `docs/internal/checkpoints/performance-wave-p2.md`

- [x] Guard removed hot-path patterns and required replacement structures.
- [x] Run P1/P2/lifecycle/Wave 4 policies plus architecture/source/schema checks.
- [x] Run `git diff --check` and create patch from verified P1 baseline to P2.
- [x] Apply-check the patch on a fresh P1-v4 tree.

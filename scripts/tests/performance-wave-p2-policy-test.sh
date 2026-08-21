#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
matcher="$root/catalog/src/main/kotlin/app/openstory/catalog/matching/StoryMatcher.kt"
match_index="$root/catalog/src/main/kotlin/app/openstory/catalog/matching/CatalogMatchIndex.kt"
search="$root/catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt"
refresh="$root/catalog/src/main/kotlin/app/openstory/catalog/home/CatalogRefreshService.kt"
details="$root/catalog/src/main/kotlin/app/openstory/catalog/details/CatalogDetailsLoader.kt"
room_repo="$root/storage/room/src/main/kotlin/app/openstory/storage/room/catalog/RoomCatalogRepository.kt"
catalog_dao="$root/storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogDao.kt"
chapter_sync="$root/chapters/src/main/kotlin/app/openstory/chapters/sync/ChapterPageSynchronizer.kt"
chapter_repo="$root/storage/room/src/main/kotlin/app/openstory/storage/room/chapters/RoomChapterRepository.kt"
chapter_dao="$root/storage/room/src/main/kotlin/app/openstory/storage/room/chapters/ChapterDao.kt"

fail() { echo "Performance Wave P2 policy violation: $1" >&2; exit 1; }

# The legacy matcher/index remain characterization-only after canonical reconciliation cutover.
[[ -f "$match_index" ]] || fail "legacy catalog match index characterization is missing"
grep -q 'storyBySource' "$match_index" || fail "legacy catalog direct-source index characterization is missing"
! grep -q 'sortedWith(matchOrdering)' "$matcher" || fail "StoryMatcher characterization regressed to sorting all evidence matches"
grep -q 'secondAutoLink' "$matcher" || fail "StoryMatcher characterization lost single-pass runner-up semantics"

# Runtime incoming ownership must use the reconciliation ingest index, never the legacy matcher/index.
for runtime_file in "$search" "$refresh" "$details"; do
  grep -q 'CatalogIngestReconciliationIndex' "$runtime_file" || fail "runtime catalog ingest path is not reconciliation-indexed: $runtime_file"
  ! grep -q 'StoryMatcher' "$runtime_file" || fail "runtime catalog ingest still references StoryMatcher: $runtime_file"
  ! grep -q 'CatalogMatchIndex' "$runtime_file" || fail "runtime catalog ingest still references CatalogMatchIndex: $runtime_file"
done
! grep -q 'first { it.story.id == resolution.storyId }' "$search" || fail "search still scans candidates after resolution"
! grep -q 'first { it.story.id == resolution.storyId }' "$refresh" || fail "home refresh still scans candidates after resolution"
! grep -q 'first { it.story.id == resolution.storyId }' "$details" || fail "details still scans candidates after resolution"
grep -q 'ingest.fork()' "$search" || fail "search source-page reconciliation is not atomically forked"
grep -q 'ingest.fork()' "$refresh" || fail "home source-page reconciliation is not atomically forked"
grep -q 'applyDurableOwnership' "$search" || fail "search does not reconcile the committed durable owner back into its ingest fork"
grep -q 'applyDurableOwnership' "$refresh" || fail "home refresh does not reconcile the committed durable owner back into its ingest fork"

# Home observation must scope catalog entries to home membership and pre-group rows once per emission.
grep -q 'observeHomeEntries()' "$catalog_dao" || fail "Room catalog DAO lacks home-scoped entry observation"
grep -q 'dao.observeHomeEntries()' "$room_repo" || fail "Home observation still consumes all catalog entries"
! grep -q 'dao.observeAllEntries()' "$room_repo" || fail "Room home observation still materializes all catalog entries"
grep -q 'sectionsByPlugin' "$room_repo" || fail "Home sections are not grouped once per emission"
grep -q 'itemsByPlugin' "$room_repo" || fail "Home items are not grouped once per emission"

# Home refresh must bulk load existing entries once.
grep -q 'suspend fun entries(pluginId: String, sourceIds: Collection<String>)' "$catalog_dao" || fail "bulk existing-entry query is missing"
grep -q 'dao.entries(mutation.pluginId.value, sourceIds)' "$room_repo" || fail "home refresh does not bulk load existing entries"
! grep -q 'mutation.entries.mapNotNull' "$room_repo" || fail "home refresh still performs per-entry lookup mapping"

# Matching snapshot must collapse source rows to canonical stories.
grep -q 'groupBy(CatalogEntryEntity::storyId)' "$room_repo" || fail "match snapshot is not collapsed by canonical story"
grep -q 'CatalogMatchEvidence' "$root/storage/room/src/main/kotlin/app/openstory/storage/room/catalog/CatalogEntityMapper.kt" || fail "collapsed story candidates do not preserve per-source evidence"

# Pagination must snapshot once per sync run and roll the graph forward.
grep -q 'graph ?: chapters.snapshot(storyId)' "$chapter_sync" || fail "chapter sync does not lazily load one rolling graph"
grep -q 'graph = committed.graph' "$chapter_sync" || fail "chapter sync does not carry committed graph forward"
grep -q 'graph.afterCommit' "$chapter_sync" || fail "chapter sync does not apply committed aggregation state in memory"
! sed -n '/private suspend fun commitPage/,/private fun ChapterSourcePage.toReleases/p' "$chapter_sync" | grep -q 'chapters.snapshot' || fail "commitPage still rereads the chapter graph"

# Restore canonical chapters in one batch rather than once per release link.
grep -q 'suspend fun restore(chapterIds: Collection<String>)' "$chapter_dao" || fail "chapter restore DAO is not batched"
grep -q 'dao.restore(linkedChapterIds)' "$chapter_repo" || fail "chapter repository does not batch restore linked chapters"

echo "Performance Wave P2 policy verified."

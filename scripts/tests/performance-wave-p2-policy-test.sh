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

[[ -f "$match_index" ]] || fail "catalog match index is missing"
grep -q 'storyBySource' "$match_index" || fail "catalog direct-source index is missing"
grep -q 'preparedByStory' "$match_index" || fail "catalog prepared candidate cache is missing"
grep -q 'storyIdsByTitleToken' "$match_index" || fail "catalog evidence shortlist index is missing"
grep -q 'matchingVariants()' "$match_index" || fail "collapsed canonical evidence is not expanded for legacy minimum-lead ranking"
! grep -q 'sortedWith(matchOrdering)' "$matcher" || fail "StoryMatcher still sorts all evidence matches"
grep -q 'secondAutoLink' "$matcher" || fail "StoryMatcher does not retain a single-pass runner-up"
! grep -q 'first { it.story.id == resolution.storyId }' "$search" || fail "search still scans candidates after resolution"
! grep -q 'first { it.story.id == resolution.storyId }' "$refresh" || fail "home refresh still scans candidates after resolution"
! grep -q 'first { it.story.id == resolution.storyId }' "$details" || fail "details still scans candidates after resolution"
grep -q 'CatalogMatchIndex' "$details" || fail "details does not use indexed catalog matching"
grep -q 'matchIndex.fork()' "$search" || fail "search source-page matching is not atomically forked"
grep -q 'matchIndex.fork()' "$refresh" || fail "home source-page matching is not atomically forked"

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

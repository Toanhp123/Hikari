#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
nav="$root/app/src/main/kotlin/app/openstory/navigation/AppNavigator.kt"
host="$root/app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt"
download_vm="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/download/DownloadViewModel.kt"
story_deps="$root/app/src/main/kotlin/app/openstory/navigation/StorySectionDependencies.kt"
story_destination="$root/app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt"
chapter_vm="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterListViewModel.kt"
mapping_vm="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingViewModel.kt"
discover_vm="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt"
home_vm="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardViewModel.kt"
library_vm="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryViewModel.kt"
story_vm="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt"
reader_vm="$root/feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderViewModel.kt"
nav_state="$root/app/src/main/kotlin/app/openstory/navigation/AppNavigationState.kt"

fail() { echo "Performance lifecycle policy violation: $1" >&2; exit 1; }

[[ -f "$nav_state" ]] || fail "AppNavigationState owner is missing"
! grep -q 'backStack\.clear()' "$nav" || fail "top-level navigation still clears the active back stack"
grep -q 'rememberDecoratedNavEntries' "$nav_state" || fail "top-level stacks are not decorated independently"
grep -q 'rememberViewModelStoreProvider' "$nav_state" || fail "top-level ViewModel stores are not retained per stack"
for top_level_vm in "$discover_vm" "$home_vm" "$library_vm"; do
    grep -q 'SharingStarted\.WhileSubscribed' "$top_level_vm" ||
        fail "retained top-level UI state is not demand-driven: $top_level_vm"
done

grep -q 'bootstrapEmptyCache()' "$discover_vm" || fail "Discover empty-cache bootstrap owner is missing"
grep -q 'dependencies.homes.first()' "$discover_vm" || fail "Discover bootstrap does not wait for the first cache emission"
grep -q 'bootstrapAttempted' "$discover_vm" || fail "Discover bootstrap is not guarded as a one-shot operation"

grep -q 'val statuses' "$download_vm" || fail "download status aggregation flow is missing"
! grep -q 'fun watch(' "$download_vm" || fail "per-release download watch collectors remain"
! grep -q 'downloadViewModel\.watch' "$host" || fail "Story still starts per-release download observers"

grep -q 'prewarmSections || section == StorySection.SOURCES' "$story_deps" ||
    fail "Story Sources are not deferred-then-prewarmed"
grep -q 'prewarmSections || section == StorySection.CHAPTERS' "$story_deps" ||
    fail "Story Chapters are not deferred-then-prewarmed"
grep -q 'withFrameNanos' "$story_destination" || fail "Story section prewarm does not wait until after the first frame"
! grep -q 'SharingStarted\.Eagerly' "$chapter_vm" || fail "ChapterList UI state is still eager"
! grep -q 'SharingStarted\.Eagerly' "$mapping_vm" || fail "Mapping UI state is still eager"
! grep -q 'SharingStarted\.Eagerly' "$story_vm" || fail "Story UI state is still eager while its retained NavEntry can be off-screen"
grep -q 'chapters\.snapshot(storyId)' "$story_vm" || fail "Story hero readable targets are not loaded from a one-shot chapter snapshot"
! grep -q 'chapters\.observe(storyId)\.first()' "$story_vm" || fail "Story hero still creates a chapter Flow observer for one-shot targets"

grep -q 'fun openChapter(' "$reader_vm" || fail "ReaderViewModel does not own chapter switching"
! grep -A8 'onPreviousChapter' "$host" | grep -q 'navigate(AppRoute.Reader' || fail "Previous chapter still pushes a Reader route"
! grep -A8 'onNextChapter' "$host" | grep -q 'navigate(AppRoute.Reader' || fail "Next chapter still pushes a Reader route"
grep -q 'groupBy.*canonicalChapterId' "$reader_vm" || fail "Reader chapter graph is not grouped in one pass"
grep -q 'cachedChapterGroups' "$reader_vm" || fail "Reader chapter graph is reloaded for every chapter switch"
! grep -q 'snapshot\.releases\.filter' "$reader_vm" || fail "Reader still scans every release for every chapter"

echo "Performance lifecycle policy verified."

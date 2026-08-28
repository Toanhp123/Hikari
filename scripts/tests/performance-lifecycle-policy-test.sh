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
story_screen="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt"
story_read_action="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryPrimaryReadAction.kt"
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

grep -q 'private val homeObservation = viewModelScope.retainedObservation' "$discover_vm" ||
    fail "Discover Home readiness is not owned by retained observation"
grep -q 'homeObservation.state.first { it is ObservationState.Available }' "$discover_vm" ||
    fail "Discover bootstrap does not wait for the first authoritative Home emission"
grep -q 'private fun startAutomaticBootstrap()' "$discover_vm" || fail "Discover automatic bootstrap owner is missing"
grep -q 'bootstrapJob?.isActive == true' "$discover_vm" || fail "Discover automatic bootstrap is not guarded against duplicate attempts"
if sed -n '/private fun startAutomaticBootstrap()/,/private suspend fun buildCandidate/p' "$discover_vm" | grep -q 'refreshState'; then
    fail "Discover automatic bootstrap is still presented as manual RefreshState"
fi

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
! grep -q 'ChapterRepository' "$story_vm" || fail "StoryViewModel still owns duplicate chapter availability state"
! grep -q 'chapters\.snapshot(storyId)' "$story_vm" || fail "Story hero still reads stale one-shot chapter targets"
grep -q 'storyPrimaryReadAction(chapterState, state.resumeTarget)' "$story_screen" ||
    fail "Story hero does not derive its action from reactive Chapter content state"
grep -q 'readerAvailabilityResolved' "$story_read_action" ||
    fail "Story hero does not gate source discovery on authoritative Reader capability"
grep -q 'observe = repository::observe' "$chapter_vm" ||
    fail "ChapterList content is not driven by the retained Chapter observation"
grep -q 'retainedObservation' "$chapter_vm" || fail "ChapterList does not retain/restart Chapter observation state"
grep -q 'ReaderSourceAvailability' "$chapter_vm" || fail "ChapterList does not capability-gate reader targets"

grep -q 'fun openChapter(' "$reader_vm" || fail "ReaderViewModel does not own chapter switching"
! grep -A8 'onPreviousChapter' "$host" | grep -q 'navigate(AppRoute.Reader' || fail "Previous chapter still pushes a Reader route"
! grep -A8 'onNextChapter' "$host" | grep -q 'navigate(AppRoute.Reader' || fail "Next chapter still pushes a Reader route"
grep -q 'chapters.observe(storyId)' "$reader_vm" || fail "Reader chapter graph is not reactively observed once per ViewModel"
! grep -q 'chapters.snapshot(storyId)' "$reader_vm" || fail "Reader still reads a stale one-shot chapter graph"
! grep -q 'cachedChapterGroups' "$reader_vm" || fail "Reader still owns a legacy chapter snapshot cache"
! grep -q 'snapshot\.releases\.filter' "$reader_vm" || fail "Reader still scans every release for every chapter"

echo "Performance lifecycle policy verified."

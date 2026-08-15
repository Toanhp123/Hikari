#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() {
  echo "UI shared-component policy violation: $1" >&2
  exit 1
}

assert_absent() {
  local pattern="$1"
  local path="$2"
  local description="$3"
  if rg -n --glob '*.kt' "$pattern" "$ROOT_DIR/$path" >/dev/null; then
    fail "$description"
  fi
}

assert_contains() {
  local needle="$1"
  local file="$2"
  local description="$3"
  if ! grep -Fq "$needle" "$ROOT_DIR/$file"; then
    fail "$description"
  fi
}

assert_absent 'private fun LibraryEmptyState\(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library' \
  'Library must use HikariEmptyState instead of a local empty-state fork'
assert_absent 'private fun MetadataGroup\(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story' \
  'Story metadata must use HikariMetadataBadge instead of a local badge fork'
assert_absent 'private fun (FailureBanner|FailureCard)\(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui' \
  'feature-local failure chrome must use HikariInlineFeedback'
assert_absent 'import androidx\.compose\.material3\.FilterChip' \
  'feature' \
  'features must use HikariFilterChip so touch geometry has one owner'
assert_absent 'private fun UpdateCard\(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui' \
  'catalog update cards must use the shared StoryUpdateCard component'
assert_absent 'ImageVector[.]Builder|PathParser' \
  'app/src/main/kotlin' \
  'app code must consume design-system glyphs instead of owning vector geometry'
assert_absent 'ImageVector[.]Builder|PathParser' \
  'feature' \
  'feature code must consume design-system glyphs instead of owning vector geometry'
assert_absent 'Text\(">"' \
  'feature' \
  'features must use a shared glyph instead of font text for chevron geometry'
assert_absent 'GridCells[.]Fixed\([0-9]+' \
  'feature' \
  'feature grid column counts must come from semantic layout policy tokens'
assert_absent '[/][[:space:]]*[0-9]+([^0-9]|$)' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover' \
  'Discover content geometry must not divide by a feature-local numeric column count'
assert_absent 'CompositionLocalProvider\(LocalContentColor provides MaterialTheme[.]colorScheme[.]onBackground\)' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardScreen.kt' \
  'Home must let HikariDestinationScaffold own destination content color'
assert_absent 'CompositionLocalProvider\(LocalContentColor provides MaterialTheme[.]colorScheme[.]onBackground\)' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryScreen.kt' \
  'Library must let HikariDestinationScaffold own destination content color'
assert_absent 'CompositionLocalProvider\(LocalContentColor provides MaterialTheme[.]colorScheme[.]onBackground\)' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreen.kt' \
  'Downloads must let HikariDestinationScaffold own destination content color'
assert_absent 'CompositionLocalProvider\(LocalContentColor provides MaterialTheme[.]colorScheme[.]onBackground\)' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesScreen.kt' \
  'Updates must let HikariDestinationScaffold own destination content color'
assert_absent 'CompositionLocalProvider\(LocalContentColor provides MaterialTheme[.]colorScheme[.]onBackground\)' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt' \
  'Story must let HikariDestinationScaffold own destination content color'

assert_contains 'HikariSearchBar(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchScreen.kt' \
  'Search screen must use the canonical HikariSearchBar'
assert_contains 'HikariMetadataGroup(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryOverview.kt' \
  'Story overview must consume the shared metadata-group presentation'
assert_contains 'HikariInlineFeedback(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchScreen.kt' \
  'Search failures must use HikariInlineFeedback'
assert_contains 'HikariInlineFeedback(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt' \
  'Mapping failures must use HikariInlineFeedback'
assert_contains 'HikariTopLevelHeader(title = "Downloads")' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreen.kt' \
  'Downloads must use the shared destination header'
assert_contains 'HikariTopLevelHeader(title = "Updates")' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesScreen.kt' \
  'Updates must use the shared destination header'
assert_contains 'HikariSectionHeader(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreen.kt' \
  'Downloads sections must use HikariSectionHeader'
assert_contains 'HikariSectionHeader(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesScreen.kt' \
  'Updates sections must use HikariSectionHeader'
assert_contains 'HikariSheetContent(' \
  'app/src/main/kotlin/app/openstory/ui/HikariUtilitySheet.kt' \
  'app utility sheet must use shared Hikari sheet chrome'
assert_contains 'HikariSheetContent(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryFilterBar.kt' \
  'Library filter sheet must use shared Hikari sheet chrome'
assert_contains 'HikariSheetContent(' \
  'feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderSettingsSheet.kt' \
  'Reader settings must use shared Hikari sheet chrome'
assert_contains 'keyboardOptions: KeyboardOptions = KeyboardOptions.Default' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariSearchBar.kt' \
  'HikariSearchBar must expose keyboard options for editable search consumers'
assert_contains 'keyboardActions: KeyboardActions = KeyboardActions.Default' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariSearchBar.kt' \
  'HikariSearchBar must expose keyboard actions for editable search consumers'
assert_contains 'fun HikariFilterChip(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariFilterChip.kt' \
  'design system must own the shared filter chip'
assert_contains '.semantics { heading() }' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariSectionHeader.kt' \
  'HikariSectionHeader must own heading semantics'
assert_contains 'fun HikariSheetContent(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariSheetContent.kt' \
  'design system must own repeated bottom-sheet content chrome'
assert_contains 'modifier = Modifier.semantics { heading() }' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariSheetContent.kt' \
  'HikariSheetContent title must expose heading semantics'
assert_contains 'fun HikariSectionTitle(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariSectionTitle.kt' \
  'design system must own standalone section-title presentation and heading semantics'
assert_contains 'modifier = modifier.semantics { heading() }' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariSectionTitle.kt' \
  'HikariSectionTitle must own heading semantics'
assert_contains 'object HikariNavigationGlyphs' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/icon/HikariNavigationGlyphs.kt' \
  'design system must own top-level navigation vector geometry'
assert_contains 'val interactionSource = remember { MutableInteractionSource() }' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/navigation/HikariFloatingNavigation.kt' \
  'floating navigation must own one interaction stream per item so visual state can stay inside the selection pill'
assert_contains 'indication = null' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/navigation/HikariFloatingNavigation.kt' \
  'floating navigation full-cell selectable must not draw an unclipped rectangular indication'
assert_contains '.clip(MaterialTheme.hikariShapes.navigationSelection)' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/navigation/HikariFloatingNavigation.kt' \
  'floating navigation interaction chrome must be clipped to the semantic selection pill'
assert_contains '.indication(interactionSource, LocalIndication.current)' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/navigation/HikariFloatingNavigation.kt' \
  'floating navigation pill must render hover focus and press feedback from the shared interaction stream'
assert_contains 'fun HikariChevronGlyph(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/icon/HikariGlyphs.kt' \
  'design system must own chevron geometry used by feature navigation affordances'
assert_contains 'HikariCoverCardVariant.POSTER' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/components/StoryCoverCard.kt' \
  'story poster artwork framing must consume a design-system cover-frame variant'
assert_contains 'HikariMetadataBadge(' \
  'feature/reader/src/main/kotlin/app/openstory/reader/ui/DownloadIndicator.kt' \
  'reader offline status must use the shared metadata/status badge presentation'
assert_contains 'StoryUpdateCard(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardScreen.kt' \
  'dashboard must consume StoryUpdateCard'
assert_contains 'StoryUpdateCard(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesScreen.kt' \
  'Updates destination must consume StoryUpdateCard'

assert_contains 'fun HikariMetadataBadgeGroup(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariMetadataBadgeGroup.kt' \
  'design system must own wrapping metadata badge collections'
assert_contains 'FlowRow(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariMetadataBadgeGroup.kt' \
  'metadata badge collections must lay out horizontally and wrap on width exhaustion'
assert_contains 'HikariMetadataBadgeGroup(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariMetadataGroup.kt' \
  'titled metadata groups must reuse the wrapping badge collection'
assert_contains 'HikariMetadataBadgeGroup(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchResultCard.kt' \
  'search result metadata badges must use the shared wrapping collection'
assert_contains 'HikariMetadataBadgeGroup(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreen.kt' \
  'download metadata badges must use the shared wrapping collection'
assert_contains 'HikariMetadataBadgeGroup(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterReleaseRow.kt' \
  'chapter release metadata badges must use the shared wrapping collection'
assert_contains 'HikariMetadataBadgeGroup(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt' \
  'mapping metadata badges must use the shared wrapping collection'
assert_contains 'surfaceBright =' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariColorScheme.kt' \
  'Hikari theme must explicitly own Material bright-surface role'
assert_contains 'surfaceDim =' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariColorScheme.kt' \
  'Hikari theme must explicitly own Material dim-surface role'
assert_contains 'surfaceContainerLowest =' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariColorScheme.kt' \
  'Hikari theme must explicitly own Material surface-container roles'
assert_contains 'surfaceContainerHighest =' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariColorScheme.kt' \
  'Hikari theme must explicitly own the full Material surface-container range'

assert_contains 'fun HikariContentCard(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariContentCard.kt' \
  'design system must own content/list card surface treatment'
assert_contains 'contentCardShadowElevation' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariDimensions.kt' \
  'content/list card shadow must come from a semantic dimension token'
assert_contains 'shadowElevation = dimensions.contentCardShadowElevation' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariContentCard.kt' \
  'content cards must use the semantic light shadow token'
assert_absent '[.]border\(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariContentCard.kt' \
  'content cards must be shadow-only and must not add an outline border'
assert_contains 'tonalElevation = dimensions.zero' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariContentCard.kt' \
  'content cards must not add tonal elevation on top of the shadow treatment'
assert_absent 'import androidx\.compose\.material3\.(Card|ElevatedCard|OutlinedCard)' \
  'feature' \
  'feature content/list cards must use HikariContentCard instead of direct Material card forks'
assert_absent 'import androidx\.compose\.material3\.(Card|ElevatedCard|OutlinedCard)' \
  'app/src/main/kotlin' \
  'app content/list cards must use HikariContentCard instead of direct Material card forks'
assert_absent 'HikariGlassPanel\(null' \
  'feature' \
  'features must not use a null-backdrop glass panel as content-card chrome'
assert_absent 'HikariGlassPanel\(null' \
  'app/src/main/kotlin' \
  'app code must not use a null-backdrop glass panel as content-card chrome'
assert_absent 'import androidx\.compose\.material3\.Surface|Surface\(|HikariContentCard\(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterReleaseRow.kt' \
  'chapter release content must remain flat inside its parent card instead of creating a nested surface'
assert_contains 'HikariContentCard(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'chapter list rows must use the shared content-card treatment'
assert_contains 'HikariContentCard(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt' \
  'mapping list items must use the shared content-card treatment'
assert_contains 'HikariContentCard(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchResultCard.kt' \
  'search results must use the shared content-card treatment'
assert_contains 'HikariContentCard(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreen.kt' \
  'download rows must use the shared content-card treatment'
assert_contains 'HikariContentCard(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryStoryCard.kt' \
  'library list rows must use the shared content-card treatment'
assert_contains 'HikariContentCard(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/components/StoryUpdateCard.kt' \
  'update/activity rows must use the shared content-card treatment'

# Story/list UI consistency contracts.
assert_contains 'fun HikariListArtworkFrame(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/artwork/HikariListArtworkFrame.kt' \
  'design system must own rounded list-artwork framing'
assert_contains '.clip(MaterialTheme.hikariShapes.cover)' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/artwork/HikariListArtworkFrame.kt' \
  'shared list-artwork framing must use the semantic cover shape'
assert_contains 'HikariListArtworkFrame(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryStoryCard.kt' \
  'library list artwork must use the shared rounded frame'
assert_contains 'HikariListArtworkFrame(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/components/StoryUpdateCard.kt' \
  'update/activity artwork must use the shared rounded list frame'
assert_contains 'fun HikariContentAction(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariContentAction.kt' \
  'design system must own outlined content/list actions'
assert_contains 'OutlinedButton(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariContentAction.kt' \
  'content/list secondary actions must expose visible outlined chrome'
assert_contains 'BorderStroke(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariContentAction.kt' \
  'content/list secondary actions must own their outline treatment'
assert_contains 'fun HikariUtilityAction(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariUtilityAction.kt' \
  'design system must own tonal text utility actions'
assert_contains 'FilledTonalButton(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariUtilityAction.kt' \
  'toolbar and utility text actions must use visible tonal chrome'
assert_contains 'TONAL' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariIconActionStyle.kt' \
  'icon actions must expose a tonal utility treatment'
assert_absent 'import androidx\.compose\.material3\.TextButton|TextButton\(' \
  'feature/catalog/src/main/kotlin' \
  'catalog actions must not bypass shared Hikari action treatments with bare TextButton chrome'
assert_absent 'import androidx\.compose\.material3\.TextButton|TextButton\(' \
  'feature/reader/src/main/kotlin' \
  'reader actions must use outlined or tonal shared Hikari action treatments'
assert_absent 'import androidx\.compose\.material3\.TextButton|TextButton\(' \
  'app/src/main/kotlin' \
  'app actions must use shared Hikari action treatments'
assert_contains 'HikariSectionHeader(title = "Details")' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryOverview.kt' \
  'Story Overview must use the shared Details mini-header'
assert_contains 'HikariSectionHeader(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'Story Chapters must use the shared mini-header contract'
assert_contains 'title = "Sources"' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySources.kt' \
  'Story Sources must use the normalized Sources mini-header title'
# Pull-to-refresh UX system contracts.
assert_contains 'fun HikariPullToRefresh(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/refresh/HikariPullToRefresh.kt' \
  'design system must own pull-to-refresh gesture, indicator, and accessibility semantics'
assert_contains 'PullToRefreshBox(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/refresh/HikariPullToRefresh.kt' \
  'Hikari pull-to-refresh must delegate gesture mechanics to Material 3'
assert_contains 'CustomAccessibilityAction(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/refresh/HikariPullToRefresh.kt' \
  'pull-to-refresh must expose an equivalent accessibility action'
assert_contains 'performTouchInput { swipeDown() }' \
  'core/designsystem/src/test/kotlin/app/openstory/designsystem/HikariProductPrimitivesTest.kt' \
  'shared pull-to-refresh must retain a real gesture regression test'
assert_contains 'pullGestureRefreshesDiscover' \
  'feature/catalog/src/test/kotlin/app/openstory/catalog/ui/discover/DiscoverSemanticsTest.kt' \
  'Discover must retain real pull gesture integration coverage'
assert_contains 'overviewPullGestureRefreshesSourceDetails' \
  'feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryScreenshotTest.kt' \
  'Story Overview must retain real pull gesture integration coverage'
assert_contains 'sourcesPullGestureRefreshesSourceDetails' \
  'feature/catalog/src/test/kotlin/app/openstory/catalog/ui/story/StoryScreenshotTest.kt' \
  'Story Sources must retain real pull gesture integration coverage'
assert_absent 'PullToRefreshBox\(|[.]pullToRefresh\(' \
  'feature' \
  'features must consume HikariPullToRefresh instead of owning pull-to-refresh mechanics'
assert_absent 'PullToRefreshBox\(|[.]pullToRefresh\(' \
  'app/src/main/kotlin' \
  'app code must consume HikariPullToRefresh instead of owning pull-to-refresh mechanics'
assert_contains 'HikariPullToRefresh(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt' \
  'Discover must expose shared pull-to-refresh'
assert_contains 'topInset: Dp = HikariDefaultDimensions.zero' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/refresh/HikariPullToRefresh.kt' \
  'shared pull-to-refresh must support a safe-area top inset'
assert_contains 'contentPadding.calculateTopPadding()' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt' \
  'Discover must place the pull indicator below the shell safe top inset'
assert_contains 'HikariPullToRefresh(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryOverview.kt' \
  'Story Overview must expose shared pull-to-refresh'
assert_contains 'HikariPullToRefresh(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySources.kt' \
  'Story Sources must expose shared pull-to-refresh'
assert_absent 'HikariPullToRefresh\(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters' \
  'Story Chapters must not expose pull-to-refresh before a chapter-sync pipeline exists'
assert_absent 'Refresh sources' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover' \
  'Discover must not keep a space-consuming manual refresh button'
assert_absent 'LinearProgressIndicator\(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt' \
  'Discover must not duplicate pull-to-refresh feedback with a linear progress row'
assert_absent 'story-source-refresh|HikariRefreshGlyph\(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySources.kt' \
  'Story Sources must not keep a manual refresh icon after pull-to-refresh migration'
assert_absent 'LinearProgressIndicator\(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt' \
  'Story must not duplicate pull-to-refresh feedback with a global linear progress bar'
assert_contains 'showsSourceDetailFailure()' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt' \
  'Story must scope source-detail refresh failures away from Chapters'
assert_contains 'storySectionContentPadding()' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryOverview.kt' \
  'Story Overview must consume the shared Story section inset owner'
assert_contains 'storySectionContentPadding()' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySources.kt' \
  'Story Sources must consume the shared Story section inset owner'
assert_contains 'storySectionContentPadding()' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt' \
  'Story Chapters must receive the shared Story section inset owner'
assert_absent 'fun HikariRefreshGlyph\(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/icon' \
  'obsolete manual-refresh glyph must not remain after pull-to-refresh migration'

echo "UI shared-component policy contract verified."

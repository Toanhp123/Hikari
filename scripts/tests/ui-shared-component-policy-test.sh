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

# Screen rhythm contracts: keep one semantic owner for top-level content insets and gaps.
assert_contains 'val screenGutter: Dp' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariSpacing.kt' \
  'design system must expose one semantic 16dp screen gutter'
assert_contains 'val sectionGap: Dp' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariSpacing.kt' \
  'design system must expose one semantic top-level section gap'
assert_contains 'val itemGap: Dp' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariSpacing.kt' \
  'design system must expose one semantic repeated-item gap'
assert_contains 'val sectionContentGap: Dp' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariSpacing.kt' \
  'design system must expose one semantic section-to-content gap'
assert_contains 'val screenBottom: Dp' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariSpacing.kt' \
  'design system must expose one semantic bottom breathing-room token'
assert_contains 'fun PaddingValues.withScreenContentInsets()' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariDestinationScaffold.kt' \
  'design system must own top-level scroll content insets'
assert_contains 'fun HikariSectionLead(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariSectionLead.kt' \
  'design system must own section-header to first-content rhythm'
assert_contains '.heightIn(min = dimensions.minimumTouchTarget)' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariSearchBar.kt' \
  'search bars must keep a minimum touch target while allowing large-font growth'
assert_absent '[.]height\(dimensions[.]minimumTouchTarget\)' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariSearchBar.kt' \
  'search bars must not clamp large-font content to a fixed touch-target height'
for section_rhythm_owner in \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesScreen.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreen.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryOverview.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySources.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt'; do
  assert_contains 'HikariSectionLead(' \
    "$section_rhythm_owner" \
    "sectioned lists must use the shared 8/12/16 rhythm owner: $section_rhythm_owner"
done
assert_contains 'hikariSectionHeader(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'chapter list must use the shared lazy section-header rhythm owner'
for rhythm_owner in \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeContent.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryContent.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchScreen.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesScreen.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreen.kt'; do
  assert_contains 'withScreenContentInsets()' \
    "$rhythm_owner" \
    "scrolling destinations must consume the shared screen inset contract: $rhythm_owner"
done
assert_contains 'MaterialTheme.hikariSpacing.screenGutter' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySectionLayout.kt' \
  'Story sections must align to the same semantic screen gutter'
assert_absent 'hikariSpacing[.]space20' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeContent.kt' \
  'Home list-level spacing must not fork the 16dp screen gutter with space20'
assert_absent 'padding\\(horizontal = MaterialTheme[.]hikariSpacing[.]space20\\)' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardShelves.kt' \
  'Home shelves must inherit the shared screen gutter instead of owning a 20dp outer inset'
assert_absent 'padding\\(horizontal = MaterialTheme[.]hikariSpacing[.]space20\\)' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardSummary.kt' \
  'Home summary must inherit the shared screen gutter instead of owning a 20dp outer inset'
assert_absent 'padding\\(horizontal = MaterialTheme[.]hikariSpacing[.]space20\\)' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt' \
  'Discover shelves must inherit the shared screen gutter instead of owning a 20dp outer inset'
assert_absent 'PaddingValues\\(horizontal = MaterialTheme[.]hikariSpacing[.]space20\\)' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover' \
  'Discover horizontal strips must inherit the shared 16dp screen gutter'

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
assert_absent 'import androidx\.compose\.material3\.AssistChip' \
  'feature' \
  'features must use a Hikari-owned suggestion chip instead of raw Material AssistChip'
assert_absent 'import androidx\.compose\.material3\.Surface' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover' \
  'Discover category affordances must use shared Hikari surface ownership instead of raw Material Surface'
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
assert_contains 'HikariSheetContent(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryHeroActions.kt' \
  'Story secondary actions must use shared Hikari sheet chrome'
assert_contains 'HikariIconAction(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryHeroActions.kt' \
  'Story hero overflow must use the shared Hikari icon action'
assert_contains 'HikariMoreGlyph()' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryHeroActions.kt' \
  'Story hero overflow must use the shared Hikari more glyph'
assert_contains 'fun HikariMoreGlyph(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/icon/HikariGlyphs.kt' \
  'design system must own the shared more-actions glyph geometry'
assert_contains 'hikariAtmosphereBrush' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryScreen.kt' \
  'Library must use the shared top-level atmosphere background'
assert_contains 'keyboardOptions: KeyboardOptions = KeyboardOptions.Default' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariSearchBar.kt' \
  'HikariSearchBar must expose keyboard options for editable search consumers'
assert_contains 'keyboardActions: KeyboardActions = KeyboardActions.Default' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariSearchBar.kt' \
  'HikariSearchBar must expose keyboard actions for editable search consumers'
assert_contains 'fun HikariFilterChip(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariFilterChip.kt' \
  'design system must own the shared filter chip'
assert_contains 'fun HikariCompactAction(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariCompactAction.kt' \
  'design system must own compact rounded-rectangle actions'
assert_contains 'fun HikariCompactIconAction(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariCompactAction.kt' \
  'design system must own compact rounded-rectangle icon actions'
assert_contains 'HikariCompactIconAction(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/navigation/HikariPagination.kt' \
  'pagination arrows must use shared compact rounded-rectangle controls'
assert_contains 'HikariCompactAction(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/navigation/HikariPagination.kt' \
  'pagination selector must use the shared compact rounded-rectangle control'
assert_contains 'HikariModalSheet(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterFiltersSheet.kt' \
  'chapter filters/actions must live in the shared modal-sheet chrome'
assert_contains 'HikariSheetContent(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterFiltersSheet.kt' \
  'chapter filters/actions sheet must use shared sheet content chrome'
assert_contains 'HikariCompactIconAction(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'chapter header options trigger must use the shared compact icon action'
assert_contains 'fun HikariSuggestionChip(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariSuggestionChip.kt' \
  'design system must own suggestion/recent-query chip presentation'
assert_contains 'HikariSuggestionChip(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchScreen.kt' \
  'Search recent queries must consume the shared suggestion chip'
assert_contains 'HikariContentCard(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverCategoryCard.kt' \
  'Discover quick categories must reuse shared Hikari content-card chrome'
assert_contains 'HikariContentCard(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardSummary.kt' \
  'Home summary must have shared surface hierarchy instead of a fixed empty feature block'
assert_absent 'dashboardFeatureHeight' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardSummary.kt' \
  'Home summary must size to content instead of reserving the legacy fixed feature height'
assert_contains 'HikariContentCard(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySources.kt' \
  'Story catalog-source entries must use the same shared content-card language as adjacent story content'
assert_absent 'Text\("Linked sources"' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt' \
  'Reading-source mapping must not add a redundant Linked sources heading below its section header'
assert_contains 'HikariSectionHeader(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt' \
  'Reading-source mapping must use the shared story section-header hierarchy'
assert_absent 'candidate[.]evidenceLabels[.]forEach' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt' \
  'Mapping candidate evidence must stay compact instead of emitting one text row per evidence label'
assert_absent 'contentType = "chapter-action"' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'chapter-level download actions must not create a loose list tier in the compact chapter feed'
assert_contains 'fun HikariPrimaryAction(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariPrimaryAction.kt' \
  'design system must own the shared filled primary action'
assert_contains 'elevation = null' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariPrimaryAction.kt' \
  'primary actions must explicitly disable Material directional button elevation'
assert_absent 'import androidx\.compose\.material3\.(Button|OutlinedButton)' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story' \
  'Story actions must use semantic Hikari action components instead of raw Material buttons'
assert_absent 'import androidx\.compose\.material3\.Button' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping' \
  'Mapping primary actions must use HikariPrimaryAction instead of raw Material Button'
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
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardShelves.kt' \
  'dashboard shelf owner must consume StoryUpdateCard'
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
assert_contains 'HikariMetadataLine(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterReleaseRow.kt' \
  'chapter release metadata must use the shared compact metadata line'
assert_contains 'fun HikariPagination(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/navigation/HikariPagination.kt' \
  'design system must own reusable numbered pagination controls'
assert_contains 'HikariPagination(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'chapter paging must consume the shared Hikari pagination component'
assert_contains 'fun LazyListScope.hikariSectionHeader(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariSectionHeader.kt' \
  'shared section-header owner must expose optional lazy sticky placement without a duplicate component'
assert_contains 'sticky = true' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'chapter controls must stay in the shared sticky section header'
assert_contains 'stickyBottomSeparation = true' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'chapter sticky header must preserve breathing room and shared bottom separation chrome'
assert_contains 'topPadding = headerTopPadding' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'chapter sticky header must own the section top inset so it survives sticky pinning'
assert_contains 'contentPadding = listContentPadding' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'chapter list must not scroll the sticky header top inset away as LazyColumn content padding'
assert_contains 'Surface(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariSectionHeader.kt' \
  'shared lazy section header must own an opaque surface around all sticky chrome'
assert_contains 'HikariBottomSeparationShadow(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariSectionHeader.kt' \
  'shared section-header owner must provide the same bottom separation shadow treatment used by top-level headers'
assert_absent 'chapter-pagination-bottom' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'chapter paging must live only in the sticky header instead of duplicating controls at the list tail'
assert_contains 'fun HikariDisclosureRow(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/content/HikariDisclosureRow.kt' \
  'design system must own reusable compact disclosure-row treatment'
assert_contains 'HikariDisclosureRow(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'chapter groups must consume the shared disclosure-row treatment'
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
assert_contains 'surfaceShadowRadius' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariDimensions.kt' \
  'all elevated Hikari surfaces must share one semantic shadow radius token'
assert_contains 'surfaceShadow' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariVisualTokens.kt' \
  'all elevated Hikari surfaces must share one semantic shadow color/opacity token'
assert_contains '.hikariSurfaceShadow(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariContentCard.kt' \
  'content cards must use the shared centered surface-shadow treatment'
assert_contains 'radius = MaterialTheme.hikariDimensions.surfaceShadowRadius' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariSurfaceShadow.kt' \
  'shared surface shadow must use the semantic 2dp radius token'
assert_contains 'offset = DpOffset.Zero' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariSurfaceShadow.kt' \
  'shared surface shadow must stay centered instead of casting downward'
assert_contains 'alpha = MaterialTheme.hikariOpacity.surfaceShadow' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariSurfaceShadow.kt' \
  'shared surface shadow must use the semantic subtle opacity token'
assert_contains 'shadowElevation = dimensions.zero' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariContentCard.kt' \
  'content cards must disable Material directional shadow elevation'
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
  'chapter release content must remain flat without creating a nested surface'
assert_absent 'HikariContentCard\(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'chapter list must stay flat and dense instead of wrapping canonical groups in cards'
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
assert_contains 'fun HikariUtilityAction(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariUtilityAction.kt' \
  'design system must own tonal text utility actions'
assert_contains 'FilledTonalButton(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariUtilityAction.kt' \
  'toolbar and utility text actions must use visible tonal chrome'
assert_contains 'elevation = null' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariUtilityAction.kt' \
  'utility actions must explicitly disable Material directional button elevation'
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
assert_contains 'title = "Details"' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryOverview.kt' \
  'Story Overview must use the shared Details mini-header'
assert_contains 'hikariSectionHeader(' \
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
assert_contains 'chaptersExposeSharedPullToRefreshAction' \
  'feature/catalog/src/test/kotlin/app/openstory/catalog/ui/chapters/ChapterListScreenshotTest.kt' \
  'Story Chapters must retain shared pull-to-refresh accessibility integration coverage'
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
assert_contains 'fun HikariTopLevelScaffold(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariTopLevelScaffold.kt' \
  'design system must own shared sticky top-level safe-area layout'
assert_contains 'contentPadding.calculateTopPadding()' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariDestinationScaffold.kt' \
  'shared safe destination viewport must consume the shell safe top inset outside scroll content'
for top_level_screen in \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardScreen.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryScreen.kt'; do
  assert_contains 'HikariTopLevelScaffold(' \
    "$top_level_screen" \
    "top-level browse destinations must share sticky safe-area chrome: $top_level_screen"
done
for top_level_screen in \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardScreen.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/library/LibraryScreen.kt'; do
  assert_contains 'headerScrolled =' \
    "$top_level_screen" \
    "top-level sticky chrome must expose scroll-driven separation shadow: $top_level_screen"
done
assert_contains 'HikariScrollToTopAction(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariDestinationScaffold.kt' \
  'shared sticky destination layout must own the reusable back-to-top action'
assert_contains 'fun HikariSafeDestinationViewport(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariDestinationScaffold.kt' \
  'design system must own one safe destination viewport primitive'
assert_contains 'fun HikariStickyDestinationScaffold(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariDestinationScaffold.kt' \
  'design system must own one sticky destination chrome primitive'
assert_contains 'HikariSafeDestinationViewport(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariDestinationScaffold.kt' \
  'sticky destination chrome must build on the shared safe destination viewport'
assert_contains 'HikariStickyDestinationScaffold(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariTopLevelScaffold.kt' \
  'top-level sticky layout must delegate safe-area and pinned-header mechanics to the shared primitive'
assert_contains 'val destinationContentGap: Dp' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariSpacing.kt' \
  'design system must own one semantic header-to-content spacing role'
assert_contains '.height(MaterialTheme.hikariSpacing.destinationContentGap)' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariDestinationScaffold.kt' \
  'sticky destination scaffold must own the fixed header-to-content rhythm outside scroll content'
assert_contains 'contentAlignment = Alignment.BottomCenter' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariDestinationScaffold.kt' \
  'sticky separation shadow must sit at the body edge after the header gap instead of before blank space'
assert_absent 'top = spacing.screenGutter' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariDestinationScaffold.kt' \
  'screen content insets must not duplicate the scaffold-owned header-to-content gap inside scroll content'
assert_contains 'headerScrolled: Boolean = false' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariDestinationScaffold.kt' \
  'shared sticky destination chrome must accept scroll state for conditional separation shadow'
assert_contains 'HikariBottomSeparationShadow(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariDestinationScaffold.kt' \
  'sticky destination chrome must use a shared bottom-only separation shadow'
assert_contains 'fun HikariBottomSeparationShadow(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariBottomSeparationShadow.kt' \
  'design system must own the bottom-only sticky chrome separation shadow'
assert_contains 'MaterialTheme.hikariDimensions.surfaceShadowRadius' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariBottomSeparationShadow.kt' \
  'sticky separation shadow must reuse the semantic surface shadow radius token'
assert_contains 'MaterialTheme.hikariColors.surfaceShadow' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariBottomSeparationShadow.kt' \
  'sticky separation shadow must reuse the semantic surface shadow color token'
assert_contains 'MaterialTheme.hikariOpacity.surfaceShadow' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariBottomSeparationShadow.kt' \
  'sticky separation shadow must reuse the semantic surface shadow opacity token'
assert_contains 'showScrollToTop: Boolean = false' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariDestinationScaffold.kt' \
  'shared sticky destination chrome must own optional back-to-top chrome'
assert_absent 'text = "HIKARI"' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt' \
  'Discover must not keep a redundant HIKARI label below its sticky search header'
for sticky_screen in \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchScreen.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreen.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesScreen.kt'; do
  assert_contains 'HikariStickyDestinationScaffold(' \
    "$sticky_screen" \
    "scrolling destinations with persistent chrome must share the sticky destination scaffold: $sticky_screen"
done
for sticky_screen in \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchScreen.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreen.kt' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/updates/UpdatesScreen.kt'; do
  assert_contains 'headerScrolled =' \
    "$sticky_screen" \
    "persistent destination chrome must expose scroll-driven separation shadow: $sticky_screen"
done
assert_contains 'HikariSafeDestinationViewport(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt' \
  'Story must retain safe top viewport handling around its hero/tab composition'
assert_contains 'item(key = "search-intro")' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchScreen.kt' \
  'Search guidance must remain scroll content instead of becoming part of sticky chrome'
assert_contains 'showScrollToTop = showScrollToTop.value' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchScreen.kt' \
  'Search must expose the shared back-to-top affordance after deep scrolling'
assert_contains 'onScrollToTop = { coroutineScope.launch { listState.animateScrollToItem(0) } }' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchScreen.kt' \
  'Search back-to-top must animate the existing search list state to its first item'
assert_absent 'contentPadding = contentPadding[.]plus' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchScreen.kt' \
  'Search must not encode the shell top safe inset as scrollable LazyColumn content padding'
assert_contains 'HikariPullToRefresh(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryOverview.kt' \
  'Story Overview must expose shared pull-to-refresh'
assert_contains 'HikariPullToRefresh(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySources.kt' \
  'Story Sources must expose shared pull-to-refresh'
assert_contains 'HikariPullToRefresh(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'Story Chapters must expose shared pull-to-refresh'
assert_contains 'private val syncService: ChapterSyncService' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterListViewModel.kt' \
  'Story Chapters pull-to-refresh must be backed by the chapter-sync pipeline'
assert_contains 'fun refresh()' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterListViewModel.kt' \
  'Story Chapters must expose a ViewModel-owned chapter refresh command'
assert_contains 'onRefresh = viewModel::refresh' \
  'app/src/main/kotlin/app/openstory/navigation/StorySectionDependencies.kt' \
  'Story Chapters pull-to-refresh must be wired to the chapter ViewModel refresh command'
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
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySections.kt' \
  'Story must scope source-detail refresh failures away from Chapters'
assert_contains 'storySectionContentPadding()' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryOverview.kt' \
  'Story Overview must consume the shared Story section inset owner'
assert_contains 'storySectionContentPadding()' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySources.kt' \
  'Story Sources must consume the shared Story section inset owner'
assert_contains 'storySectionContentPadding()' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySections.kt' \
  'Story Chapters must receive the shared Story section inset owner'
assert_absent 'fun HikariRefreshGlyph\(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/icon' \
  'obsolete manual-refresh glyph must not remain after pull-to-refresh migration'

assert_absent 'import androidx\.compose\.material3\.Button' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/state' \
  'state actions must consume HikariPrimaryAction instead of owning a raw Material Button'
assert_contains 'HikariPrimaryAction(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/state/HikariEmptyState.kt' \
  'empty/error state actions must use the shared Hikari primary action'

# Hikari surface shadow ownership: directional Material elevation shadows are forbidden.
assert_absent '[.]shadow\(' \
  'app/src/main/kotlin' \
  'app code must not create directional elevation shadows'
assert_absent '[.]shadow\(' \
  'feature' \
  'feature code must not create directional elevation shadows'
assert_absent '[.]dropShadow\(' \
  'app/src/main/kotlin' \
  'app code must consume the shared Hikari shadow owner instead of custom drop shadows'
assert_absent '[.]dropShadow\(' \
  'feature' \
  'feature code must consume the shared Hikari shadow owner instead of custom drop shadows'
raw_designsystem_drop_shadows="$(
  rg -n --glob '*.kt' '[.]dropShadow\(' "$ROOT_DIR/core/designsystem/src/main/kotlin" \
    | grep -v '/surface/HikariSurfaceShadow.kt:' || true
)"
if [[ -n "$raw_designsystem_drop_shadows" ]]; then
  fail "design-system drop shadows must route through HikariSurfaceShadow"
fi
nonzero_shadow_elevation="$(
  rg -n --glob '*.kt' 'shadowElevation[[:space:]]*=' \
    "$ROOT_DIR/app/src/main/kotlin" "$ROOT_DIR/feature" "$ROOT_DIR/core/designsystem/src/main/kotlin" \
    | grep -vE '=[[:space:]]*(zero|dimensions[.]zero|MaterialTheme[.]hikariDimensions[.]zero)[,)]?' || true
)"
if [[ -n "$nonzero_shadow_elevation" ]]; then
  fail "Material shadowElevation must remain zero; use HikariSurfaceShadow for visible elevation"
fi
assert_contains 'fun Modifier.hikariSurfaceShadow(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/surface/HikariSurfaceShadow.kt' \
  'design system must own the shared centered surface shadow'
assert_absent '[.]shadow\(' \
  'core/designsystem/src/main/kotlin' \
  'design-system surfaces must not use directional Modifier.shadow elevation'
assert_absent 'PullToRefreshDefaults[.]Indicator\(' \
  'core/designsystem/src/main/kotlin' \
  'pull-to-refresh must not use the Material indicator with built-in elevation shadow'
assert_contains 'PullToRefreshDefaults.IndicatorBox(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/refresh/HikariPullToRefresh.kt' \
  'pull-to-refresh must explicitly own its indicator container and shadow'
assert_contains 'elevation = MaterialTheme.hikariDimensions.zero' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/refresh/HikariPullToRefresh.kt' \
  'pull-to-refresh Material indicator elevation must be disabled'
assert_absent 'import androidx\.compose\.material3\.DropdownMenu([[:space:]]|$)|(^|[^[:alnum:]_])DropdownMenu\(' \
  'feature' \
  'features must use the shared Hikari dropdown menu so popup elevation cannot drift'
assert_contains 'fun HikariDropdownMenu(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/menu/HikariDropdownMenu.kt' \
  'design system must own popup menu shape and shadow'

echo "UI shared-component policy contract verified."

# UI architecture/performance hardening contracts.
assert_contains 'contentType = {' \
  'feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderContent.kt' \
  'Reader lazy content must declare content types so heterogeneous blocks can reuse compatible compositions'
assert_contains 'ReaderProgressUiState' \
  'feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderScreen.kt' \
  'Reader visible progress must be isolated in a stable local holder instead of a root per-pixel Float state'
assert_absent 'contentPadding[[:space:]]*=[[:space:]]*if[[:space:]]*\(chromeVisible\)' \
  'feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderScreen.kt' \
  'Reader document padding must remain stable when chrome visibility changes'
assert_absent 'chapter[.]releases[.]forEach' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'chapter releases must remain independent lazy items instead of eager children of one chapter group'
assert_contains 'contentType = { "chapter-release" }' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'chapter releases must declare a stable lazy content type'
assert_absent 'state[.](mappings|failures|candidates)[.]forEach' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt' \
  'mapping collections must be LazyListScope items instead of eager Column children'
assert_contains 'fun LazyListScope.mappingItems(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt' \
  'mapping UI must expose lazy items for Story Sources to compose lazily'
assert_contains 'mappingItems(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StorySources.kt' \
  'Story Sources must flatten mapping items into its existing LazyColumn'
assert_contains 'onValueChangeFinished = {' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/search/SearchFilters.kt' \
  'range filters must commit ViewModel state only after slider dragging finishes'
assert_contains 'fun HikariInlineAction(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariInlineAction.kt' \
  'design system must own the shared borderless low-priority action treatment'
assert_absent 'import androidx[.]compose[.]material3[.]ModalBottomSheet' \
  'app/src/main/kotlin' \
  'app UI must use HikariModalSheet instead of owning Material bottom-sheet chrome'
assert_absent 'import androidx[.]compose[.]material3[.]ModalBottomSheet' \
  'feature' \
  'feature UI must use HikariModalSheet instead of owning Material bottom-sheet chrome'
assert_contains 'fun HikariModalSheet(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/layout/HikariModalSheet.kt' \
  'design system must own the modal bottom-sheet surface contract'
assert_contains 'style: HikariIconActionStyle = HikariIconActionStyle.TONAL' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariIconAction.kt' \
  'icon actions must default to tonal; glass intent must be explicit at real backdrop call sites'
assert_contains 'HikariVisualBrushes' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariVisualTokens.kt' \
  'theme must own reusable visual brush tokens rather than allocate gradient brushes on every property read'
assert_contains 'ReaderDestination(' \
  'app/src/main/kotlin/app/openstory/navigation/AppDestinations.kt' \
  'destination ViewModel/screen adapters must live outside AppNavHost'
app_nav_lines="$(wc -l < "$ROOT_DIR/app/src/main/kotlin/app/openstory/navigation/AppNavHost.kt")"
if (( app_nav_lines > 260 )); then
  fail "AppNavHost must stay focused on graph/shell assembly (<=260 lines, found $app_nav_lines)"
fi
for benchmark in readerScrollLongChapter chaptersExpandAndScroll libraryListScroll discoverScroll; do
  assert_contains "fun $benchmark()" \
    'benchmark/src/main/kotlin/app/openstory/benchmark/HikariMacrobenchmark.kt' \
    "macrobenchmark suite must cover $benchmark frame timing"
done
assert_contains 'fun swipeUpOnTag(' \
  'benchmark/src/main/kotlin/app/openstory/benchmark/HikariBenchmarkDriver.kt' \
  'benchmark driver must own a reusable deterministic tagged scroll gesture helper'
assert_absent 'HikariContentAction\(' \
  'feature' \
  'feature actions must prefer primary, tonal, or inline hierarchy; outlined actions are reserved for explicit exceptional use'
assert_absent 'HikariContentAction\(' \
  'app/src/main/kotlin' \
  'app utility actions must prefer tonal or inline hierarchy instead of outlined pills'
assert_absent 'onClick[[:space:]]*=[[:space:]]*\{[[:space:]]*item[.]storyId[?][.]let\(actions[.]onStorySelected\)' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/downloads/DownloadsScreen.kt' \
  'download cards with child download controls must not also own the parent click target'
assert_contains 'val cardOwnsClick = action == null' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/components/StoryUpdateCard.kt' \
  'update cards with a sibling action must split interaction ownership instead of nesting click targets'
assert_contains 'val heroClickModifier = if (medium)' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverHero.kt' \
  'medium Discover hero must delegate navigation to its explicit CTA instead of nesting a CTA inside a clickable hero'
assert_contains 'val fallbackBrush = remember(' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/artwork/HikariArtwork.kt' \
  'artwork fallback gradients must be remembered instead of allocated on every recomposition'

# Final UI cleanup contracts.
assert_contains 'StoryHeroActions(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryHeroContent.kt' \
  'Story hero layouts must share one primary-plus-overflow action row'
assert_absent 'WideStoryHeroActions|NarrowStoryHeroActions|LibraryStatusMenu' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story' \
  'Story hero must not reintroduce duplicated wide/narrow action stacks or an inline Library menu'
assert_contains 'narrowHero = windowClass == HikariWindowClass.COMPACT' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt' \
  'compact-width Story must select the narrow hero while large phones retain the wider hero layout'
assert_contains 'narrow = narrowHero' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryLayouts.kt' \
  'phone Story layout must forward responsive hero width intent explicitly'
assert_contains 'FlowRow(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterFiltersSheet.kt' \
  'chapter filters must wrap cleanly instead of forcing all controls into one Row'
assert_absent 'Checkbox\(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterFiltersSheet.kt' \
  'Unavailable chapter filtering must use the shared HikariFilterChip instead of a raw checkbox/text pair'
assert_absent 'HikariUtilityAction\(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/chapters/ChapterList.kt' \
  'bulk chapter download affordances must stay quiet/inline instead of adding full-width tonal pill bars'
assert_contains 'Channel<ProgressUpdate>' \
  'reader/src/main/kotlin/app/openstory/reader/progress/ReadingProgressService.kt' \
  'reader progress persistence must use one long-lived conflated update pipeline'
assert_absent 'pendingWrite' \
  'reader/src/main/kotlin/app/openstory/reader/progress/ReadingProgressService.kt' \
  'reader scroll updates must not cancel and relaunch a coroutine job for every viewport change'
if [[ -e "$ROOT_DIR/core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariContentAction.kt" || \
      -e "$ROOT_DIR/core/designsystem/src/main/kotlin/app/openstory/designsystem/control/HikariContentActionTone.kt" ]]; then
  fail 'unused outlined HikariContentAction API must be removed from the production design system'
fi
if [[ -e "$ROOT_DIR/core/designsystem/src/main/kotlin/app/openstory/designsystem/state/HikariOfflineState.kt" ]]; then
  fail 'unused HikariOfflineState abstraction must be removed from the production design system'
fi
assert_absent 'onArtworkInverse' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariVisualTokens.kt' \
  'unused onArtworkInverse semantic color token must be removed'
discover_screen_lines="$(wc -l < "$ROOT_DIR/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt")"
if (( discover_screen_lines > 220 )); then
  fail "DiscoverScreen must remain orchestration-focused (<=220 lines, found $discover_screen_lines)"
fi
home_screen_lines="$(wc -l < "$ROOT_DIR/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardScreen.kt")"
if (( home_screen_lines > 220 )); then
  fail "HomeDashboardScreen must remain orchestration-focused (<=220 lines, found $home_screen_lines)"
fi
story_screen_lines="$(wc -l < "$ROOT_DIR/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt")"
if (( story_screen_lines > 180 )); then
  fail "StoryScreen must remain orchestration-focused (<=180 lines, found $story_screen_lines)"
fi
for screen_limit in \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverScreen.kt:30' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/dashboard/HomeDashboardScreen.kt:25' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryScreen.kt:25'; do
  screen_path="${screen_limit%:*}"
  max_imports="${screen_limit##*:}"
  import_count="$(grep -c '^import ' "$ROOT_DIR/$screen_path")"
  if (( import_count > max_imports )); then
    fail "$screen_path must remain orchestration-focused (<=$max_imports imports, found $import_count)"
  fi
done
assert_absent 'Row\(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/download/DownloadActionSheet.kt' \
  'DownloadActionSheet renders one action at a time and must not keep a redundant Row layout node'
assert_contains 'LibraryStatus.WANT_TO_READ' \
  'app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkFixtureActivity.kt' \
  'benchmark browse fixtures must use a valid LibraryStatus while populating the scroll collection'
assert_contains 'BENCHMARK_EPOCH_MILLIS - index - 1' \
  'app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkFixtureActivity.kt' \
  'benchmark browse fillers must sort behind the primary fixture so story-navigation benchmarks can still find it without pre-scrolling'
assert_contains 'library.changeStatus(' \
  'app/src/benchmarkRelease/kotlin/app/openstory/benchmark/BenchmarkFixtureActivity.kt' \
  'benchmark fixture reseeding must refresh Library ordering instead of relying on add-if-absent timestamps from a previous run'

# Image Reader contracts: lazy image pages must avoid subcomposition and keep sizing/cache identity explicit.
assert_contains 'rememberAsyncImagePainter(' \
  'feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderImagePage.kt' \
  'Reader image pages must use the non-subcomposed Coil painter path inside LazyColumn'
assert_contains 'rememberConstraintsSizeResolver()' \
  'feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderImagePage.kt' \
  'Reader image requests must be sized from Compose constraints'
assert_contains 'readerImagePlaceholderHeight' \
  'core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariDimensions.kt' \
  'Reader image loading geometry must remain a design-system token'
assert_contains 'diskCachePolicy(CachePolicy.DISABLED)' \
  'feature/reader/src/main/kotlin/app/openstory/reader/ui/ReaderImagePage.kt' \
  'online-only Reader image pages must not bypass Hikari storage quota through Coil disk cache'
assert_absent 'SubcomposeAsyncImage' \
  'feature/reader/src/main/kotlin/app/openstory/reader/ui' \
  'Reader LazyColumn must not use subcomposition for image loading states'

# Failure-presentation contract: machine/plugin/domain codes stay diagnostic state, never user-facing copy.
assert_contains 'fun catalogFailureMessage(' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui/feedback/CatalogFailureMessage.kt' \
  'catalog UI must own one failure-code presentation boundary'
assert_absent 'message[[:space:]]*=[[:space:]]*failure[.]code' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui' \
  'catalog UI must not render machine failure codes directly as feedback messages'
assert_absent 'supportingText[[:space:]]*=[[:space:]]*failure[.]code' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui' \
  'catalog UI must not render machine failure codes directly as supporting text'
assert_absent '[$][{][^}]*[.]code[}]' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui' \
  'catalog UI must not interpolate diagnostic failure codes into user-facing copy'
assert_absent 'HikariInlineFeedback\(message = failure\)' \
  'feature/catalog/src/main/kotlin/app/openstory/catalog/ui' \
  'raw string failures must pass through the catalog failure presenter before rendering'

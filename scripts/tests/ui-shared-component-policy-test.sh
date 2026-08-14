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

echo "UI shared-component policy contract verified."

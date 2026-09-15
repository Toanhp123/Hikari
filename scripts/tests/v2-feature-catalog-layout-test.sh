#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
SOURCE_ROOT="$ROOT_DIR/feature/catalog/src/main/kotlin/app/openstory/catalog/feature"

fail() {
  echo "$1" >&2
  exit 1
}

required_files=(
  'entrypoint/CatalogEntryPoint.kt|app.openstory.catalog.feature.entrypoint'
  'CatalogVariantBinding.kt|app.openstory.catalog.feature'
  'runtime/CatalogRuntimeAccess.kt|app.openstory.catalog.feature.runtime'
  'runtime/CatalogRuntimeHolder.kt|app.openstory.catalog.feature.runtime'
  'runtime/DiscoverRuntime.kt|app.openstory.catalog.feature.runtime'
  'artwork/CatalogArtworkRuntime.kt|app.openstory.catalog.feature.artwork'
  'artwork/CatalogCoverArtwork.kt|app.openstory.catalog.feature.artwork'
  'artwork/CoverArtworkState.kt|app.openstory.catalog.feature.artwork'
  'artwork/CoverRequest.kt|app.openstory.catalog.feature.artwork'
  'discover/composition/DiscoverComposition.kt|app.openstory.catalog.feature.discover.composition'
  'discover/screen/DiscoverScreen.kt|app.openstory.catalog.feature.discover.screen'
  'discover/DiscoverIssueUi.kt|app.openstory.catalog.feature.discover'
  'discover/DiscoverSectionLabels.kt|app.openstory.catalog.feature.discover'
  'discover/header/DiscoverHeader.kt|app.openstory.catalog.feature.discover.header'
  'discover/header/DiscoverSearchIcon.kt|app.openstory.catalog.feature.discover.header'
  'discover/editorial/DiscoverEditorialHero.kt|app.openstory.catalog.feature.discover.editorial'
  'discover/editorial/DiscoverEditorialQuote.kt|app.openstory.catalog.feature.discover.editorial'
  'discover/section/DiscoverSections.kt|app.openstory.catalog.feature.discover.section'
  'discover/section/DiscoverCoverImage.kt|app.openstory.catalog.feature.discover.section'
  'discover/section/DiscoverPosterStory.kt|app.openstory.catalog.feature.discover.section'
  'discover/section/DiscoverFeaturedStory.kt|app.openstory.catalog.feature.discover.section'
  'discover/section/DiscoverPosterTile.kt|app.openstory.catalog.feature.discover.section'
  'discover/section/DiscoverRankedStoryRow.kt|app.openstory.catalog.feature.discover.section'
)

for row in "${required_files[@]}"; do
  IFS='|' read -r relative package_name <<< "$row"
  file="$SOURCE_ROOT/$relative"
  [[ -f "$file" ]] || fail "Missing approved feature catalog source: $relative"
  grep -Fqx "package $package_name" "$file" ||
    fail "Wrong package declaration for feature catalog source: $relative"
done

for retired in \
  CatalogArtworkEntryPoint.kt \
  CatalogEntryPoint.kt \
  CatalogComposition.kt \
  CatalogScreen.kt \
  CatalogSectionResources.kt \
  assets \
  presentation \
  state; do
  [[ ! -e "$SOURCE_ROOT/$retired" ]] ||
    fail "Retired feature catalog source path remains after cleanup: $retired"
done

for retired_discover in \
  DiscoverComposition.kt \
  DiscoverRuntime.kt \
  DiscoverScreen.kt; do
  [[ ! -e "$SOURCE_ROOT/discover/$retired_discover" ]] ||
    fail "Retired flat Discover source path remains after cleanup: $retired_discover"
done

[[ ! -e "$ROOT_DIR/feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/assets/LegacyArtworkTestAdapters.kt" ]] ||
  fail 'Retired LegacyArtworkTestAdapters.kt remains after Catalog cleanup.'

android_artwork_tests=(
  'ArtworkPreflightInstrumentedTest.kt'
  'LocalCoverContinuityInstrumentedTest.kt'
)
for test_name in "${android_artwork_tests[@]}"; do
  test_file="$ROOT_DIR/feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/artwork/$test_name"
  [[ -f "$test_file" ]] || fail "Missing Catalog artwork Android test at responsibility path: $test_name"
  grep -Fqx 'package app.openstory.catalog.feature.artwork' "$test_file" ||
    fail "Wrong package for Catalog artwork Android test: $test_name"
done
[[ ! -e "$ROOT_DIR/feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/assets/CoverImagePreflightInstrumentedTest.kt" ]] ||
  fail 'Old assets/CoverImagePreflightInstrumentedTest.kt path remains.'
[[ ! -e "$ROOT_DIR/feature/catalog/src/androidTest/kotlin/app/openstory/catalog/feature/assets/LocalCoverContinuityInstrumentedTest.kt" ]] ||
  fail 'Old assets/LocalCoverContinuityInstrumentedTest.kt path remains.'

for variant in debug release benchmarkRelease; do
  variant_file="$ROOT_DIR/feature/catalog/src/$variant/kotlin/app/openstory/catalog/feature/artwork/VariantLocalCoverAssets.kt"
  [[ -f "$variant_file" ]] || fail "Missing $variant artwork-local resolver at responsibility path."
  grep -Fqx 'package app.openstory.catalog.feature.artwork' "$variant_file" ||
    fail "Wrong package for $variant VariantLocalCoverAssets.kt"
  [[ ! -e "$ROOT_DIR/feature/catalog/src/$variant/kotlin/app/openstory/catalog/feature/VariantLocalCoverAssets.kt" ]] ||
    fail "Old flat $variant VariantLocalCoverAssets.kt remains."
done

[[ -f "$ROOT_DIR/feature/catalog/src/main/kotlin/app/openstory/catalog/feature/CatalogVariantBinding.kt" ]] ||
  fail 'CatalogVariantBinding.kt build-surface anchor moved unexpectedly.'
for variant in debug release benchmarkRelease; do
  [[ -f "$ROOT_DIR/feature/catalog/src/$variant/kotlin/app/openstory/catalog/feature/VariantCatalogBinding.kt" ]] ||
    fail "$variant VariantCatalogBinding.kt build-surface anchor moved unexpectedly."
done

echo "V2 feature catalog package layout verified."

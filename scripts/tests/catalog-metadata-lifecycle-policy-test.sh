#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
loader="$root/catalog/src/main/kotlin/app/openstory/catalog/details/CatalogDetailsLoader.kt"
discover="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverViewModel.kt"
story="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/story/StoryViewModel.kt"
search="$root/catalog/src/main/kotlin/app/openstory/catalog/search/CatalogSearchService.kt"

fail() { echo "Catalog metadata lifecycle policy violation: $1" >&2; exit 1; }

[[ -f "$loader" ]] || fail "CatalogDetailsLoader is missing"
count="$(grep -R --include='*.kt' -n 'source\.details(' "$root/catalog/src/main/kotlin" | wc -l | tr -d ' ')"
[[ "$count" == "1" ]] || fail "expected exactly one production source.details call site, found $count"
grep -q 'source\.details(' "$loader" || fail "CatalogDetailsLoader is not the Details call site"
! grep -R --include='*.kt' -q 'hasLoadedDetails' "$root/catalog/src/main/kotlin" || fail "field-based details completeness remains"
! grep -q 'latestHydrationInFlight' "$discover" || fail "Discover still owns metadata in-flight state"
! grep -q 'CatalogDetailsService' "$discover" || fail "Discover still depends on CatalogDetailsService"
! grep -q 'CatalogDetailsService' "$story" || fail "Story still depends on CatalogDetailsService"
! grep -q 'CatalogDetailsService' "$search" || fail "Search still depends on CatalogDetailsService"
! grep -q 'CoroutineScope(' "$discover" || fail "Discover creates its own metadata scope"
! grep -q 'CoroutineScope(' "$story" || fail "Story creates its own metadata scope"

echo "Catalog metadata lifecycle policy verified."

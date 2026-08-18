#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"

fail() { echo "Source hygiene policy violation: $1" >&2; exit 1; }

grep -qx '/.kotlin/' "$root/.gitignore" || fail "/.kotlin/ must be ignored"

mapping="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/mapping/MappingSheet.kt"
nav_state="$root/app/src/main/kotlin/app/openstory/navigation/AppNavigationState.kt"
reader_store="$root/reader/src/main/kotlin/app/openstory/reader/content/ReaderDocumentStore.kt"
http_capability="$root/plugins/runtime/src/main/kotlin/app/openstory/plugins/runtime/capabilities/http/PluginHttpCapability.kt"
discover_state="$root/feature/catalog/src/main/kotlin/app/openstory/catalog/ui/discover/DiscoverUiState.kt"
nav_test="$root/app/src/androidTest/kotlin/app/openstory/navigation/AppNavigationTest.kt"

! grep -Eq '^fun MappingSheet\(' "$mapping" || fail "retired standalone MappingSheet wrapper is still in production"
grep -q '^fun LazyListScope.mappingItems(' "$mapping" || fail "production mapping lazy-item API is missing"

! grep -Eq '^[[:space:]]*val stacksInUse:' "$nav_state" || fail "retired aggregate top-level stack path is still present"
! grep -Eq '^fun AppNavigationState[.]decoratedEntries\(' "$nav_state" || fail "retired aggregate decoratedEntries path is still present"
grep -q 'PersistentTopLevelNavDisplay(' "$nav_test" || fail "navigation instrumentation does not exercise the shipping persistent host"
! grep -q 'navigationState[.]decoratedEntries(provider)' "$nav_test" || fail "navigation instrumentation still uses the retired aggregate path"

! grep -q '^object NoOpReaderDocumentStore' "$reader_store" || fail "test-only no-op ReaderDocumentStore remains in production"
! grep -Eq '^[[:space:]]*internal fun validateTarget\(' "$http_capability" || fail "test-only HTTP validation seam remains in production"
! grep -Eq '^[[:space:]]*val selectedCatalog:' "$discover_state" || fail "test-only Discover selectedCatalog convenience property remains in production"

echo "Source hygiene policy verified."

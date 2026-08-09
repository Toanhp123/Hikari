#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${OPENSTORY_ROOT_DIR:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
FILE="$ROOT_DIR/docs/internal/architecture-baseline-2/test-migration-inventory.md"

test -f "$FILE"

required_paths=(
  'plugins/api/src/test/kotlin/app/openstory/plugins/api/manifest/PluginManifestTest.kt'
  'plugins/api/src/test/kotlin/app/openstory/plugins/api/testing/MyAnimeListReferenceContractTest.kt'
  'plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/execution/PluginOperationRunnerTest.kt'
  'plugins/runtime/src/test/kotlin/app/openstory/plugins/runtime/install/PackageVerifierTest.kt'
  'core/database/src/androidTest/kotlin/app/openstory/database/repository/RoomCatalogRepositoryTest.kt'
  'core/matching/src/test/kotlin/app/openstory/matching/CatalogStoryResolverTest.kt'
  'feature/home/src/test/kotlin/app/openstory/home/domain/RefreshHomeTest.kt'
  'feature/home/src/test/kotlin/app/openstory/home/domain/SearchCatalogsTest.kt'
  'feature/story/src/test/kotlin/app/openstory/story/ui/StoryDetailViewModelTest.kt'
  'app/src/androidTest/kotlin/app/openstory/MyAnimeListCatalogContractIntegrationTest.kt'
)

for path in "${required_paths[@]}"; do
  grep -Fq "$path" "$FILE"
done

grep -q 'REWRITE' "$FILE"
grep -q 'DELETE_WITH_OWNER' "$FILE"
grep -q 'KEEP_UNTIL_REPLACED' "$FILE"

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"
SOURCE_ROOT="$ROOT_DIR/core/artwork/src/main/kotlin/app/openstory/artwork"

fail() {
  echo "$1" >&2
  exit 1
}

required_files=(
  'ArtworkFailure.kt|app.openstory.artwork'
  'ArtworkLimits.kt|app.openstory.artwork'
  'request/ArtworkRequest.kt|app.openstory.artwork.request'
  'request/ArtworkRequestIdentity.kt|app.openstory.artwork.request'
  'policy/ArtworkPolicy.kt|app.openstory.artwork.policy'
  'policy/ArtworkPolicyResolver.kt|app.openstory.artwork.policy'
  'remote/ArtworkRemotePolicy.kt|app.openstory.artwork.remote'
  'remote/ArtworkTransport.kt|app.openstory.artwork.remote'
  'preflight/ArtworkImagePreflight.kt|app.openstory.artwork.preflight'
  'preflight/ArtworkImageContainer.kt|app.openstory.artwork.preflight'
  'cache/ArtworkEncodedCache.kt|app.openstory.artwork.cache'
  'cache/ArtworkDecodedMemoryCache.kt|app.openstory.artwork.cache'
  'cache/ArtworkMemoryPressure.kt|app.openstory.artwork.cache'
  'pipeline/ArtworkInFlight.kt|app.openstory.artwork.pipeline'
  'pipeline/ArtworkPipelineCoordinator.kt|app.openstory.artwork.pipeline'
  'runtime/ArtworkRuntime.kt|app.openstory.artwork.runtime'
  'runtime/ArtworkFetcher.kt|app.openstory.artwork.runtime'
)

for row in "${required_files[@]}"; do
  IFS='|' read -r relative package_name <<< "$row"
  file="$SOURCE_ROOT/$relative"
  [[ -f "$file" ]] || fail "Missing approved core artwork source: $relative"
  grep -Fqx "package $package_name" "$file" ||
    fail "Wrong package declaration for core artwork source: $relative"
done

for removed in \
  ArtworkCaches.kt \
  ArtworkInFlight.kt \
  ArtworkPolicy.kt \
  ArtworkPolicyResolver.kt \
  ArtworkRequest.kt \
  ArtworkRequestIdentity.kt \
  ArtworkRuntime.kt \
  ArtworkTransport.kt; do
  [[ ! -e "$SOURCE_ROOT/$removed" ]] ||
    fail "Old flat core artwork source remains after package cleanup: $removed"
done


TEST_ROOT="$ROOT_DIR/core/artwork/src/test/kotlin/app/openstory/artwork"
required_tests=(
  'cache/ArtworkEncodedDiskCacheTest.kt|app.openstory.artwork.cache'
  'pipeline/ArtworkInFlightTest.kt|app.openstory.artwork.pipeline'
  'pipeline/ArtworkPipelineCoordinatorTest.kt|app.openstory.artwork.pipeline'
  'policy/ArtworkPolicyTest.kt|app.openstory.artwork.policy'
  'remote/ArtworkRemotePolicyTest.kt|app.openstory.artwork.remote'
  'request/ArtworkRequestIdentityTest.kt|app.openstory.artwork.request'
)

for row in "${required_tests[@]}"; do
  IFS='|' read -r relative package_name <<< "$row"
  file="$TEST_ROOT/$relative"
  [[ -f "$file" ]] || fail "Missing approved core artwork test: $relative"
  grep -Fqx "package $package_name" "$file" ||
    fail "Wrong package declaration for core artwork test: $relative"
done

for removed_test in \
  ArtworkEncodedDiskCacheTest.kt \
  ArtworkInFlightTest.kt \
  ArtworkPipelineCoordinatorTest.kt \
  ArtworkPolicyTest.kt \
  ArtworkRequestIdentityTest.kt; do
  [[ ! -e "$TEST_ROOT/$removed_test" ]] ||
    fail "Old flat core artwork test remains after package cleanup: $removed_test"
done

while IFS= read -r root_file; do
  case "$(basename "$root_file")" in
    ArtworkFailure.kt|ArtworkLimits.kt) ;;
    *) fail "Core artwork root package gained non-cross-cutting source: $(basename "$root_file")" ;;
  esac
done < <(find "$SOURCE_ROOT" -maxdepth 1 -type f -name '*.kt' | sort)

echo "V2 core artwork package layout verified."

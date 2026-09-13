#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

cd "$ROOT_DIR"

./gradlew verifyStep2BuildSurface verifyProductionPackageStructure \
  verifyModuleBoundaries --no-daemon

for forbidden in \
  okhttp3 \
  'java.net.HttpURLConnection' \
  'java.net.URL' \
  coil-network-okhttp; do
  if grep -R -F --include='*.kt' --include='*.java' --include='*.kts' \
    --exclude-dir=build --exclude-dir=.gradle --exclude-dir=androidTest \
    --exclude-dir=test "$forbidden" \
    app core/designsystem catalog/domain catalog/storage catalog/runtime feature/catalog; then
    echo "Forbidden Step 2 production surface found: $forbidden" >&2
    exit 1
  fi
done

if find feature/catalog/src/main feature/catalog/src/release \
  -type f \( -iname '*seed*source*' -o -iname '*plugin*harness*' \) \
  -print -quit 2>/dev/null | grep -q .; then
  echo "Release/main Catalog fixture source leaked into production." >&2
  exit 1
fi

for required in \
  'androidTestImplementation(project(":plugins:api"))' \
  'androidTestImplementation(libs.androidx.javascriptengine)' \
  'androidTestImplementation(libs.kotlinx.serialization.json)' \
  'androidTestImplementation(libs.kotlinx.coroutines.core)'; do
  grep -F -q "$required" feature/catalog/build.gradle.kts || {
    echo "Missing Task 17 androidTest dependency: $required" >&2
    exit 1
  }
done

if grep -E -q \
  '^[[:space:]]*(api|implementation|compileOnly|runtimeOnly|releaseImplementation|debugImplementation)[[:space:]]*\([^)]*(plugins:api|javascriptengine)' \
  feature/catalog/build.gradle.kts; then
  echo "Task 17 plugin harness dependency leaked outside androidTest." >&2
  exit 1
fi

if grep -R -F -q --include='AndroidManifest.xml' \
  'android.permission.INTERNET' feature/catalog/src/main feature/catalog/src/release 2>/dev/null; then
  echo "Task 17 added a production INTERNET permission." >&2
  exit 1
fi

AAR_DIR="$ROOT_DIR/feature/catalog/build/outputs/aar"
DEBUG_AAR="$AAR_DIR/catalog-debug.aar"
RELEASE_AAR="$AAR_DIR/catalog-release.aar"
BENCHMARK_AAR="$AAR_DIR/catalog-benchmarkRelease.aar"
NON_MINIFIED_AAR="$AAR_DIR/catalog-nonMinifiedRelease.aar"

for artifact in "$DEBUG_AAR" "$RELEASE_AAR" "$BENCHMARK_AAR" "$NON_MINIFIED_AAR"; do
  [[ -f "$artifact" ]] || {
    echo "Missing assembled Catalog artifact: $artifact" >&2
    exit 1
  }
done

ARTIFACT_TMP="$(mktemp -d)"
trap 'rm -rf "$ARTIFACT_TMP"' EXIT

inspect_aar() {
  local name="$1"
  local artifact="$2"
  local output="$ARTIFACT_TMP/$name"
  mkdir -p "$output"
  unzip -Z1 "$artifact" > "$output/aar-entries.txt"
  unzip -q "$artifact" classes.jar -d "$output"
  jar tf "$output/classes.jar" > "$output/class-entries.txt"
}

inspect_aar debug "$DEBUG_AAR"
inspect_aar release "$RELEASE_AAR"
inspect_aar benchmark "$BENCHMARK_AAR"
inspect_aar non-minified "$NON_MINIFIED_AAR"

require_entry() {
  local listing="$1"
  local expected="$2"
  grep -F -q "$expected" "$listing" || {
    echo "Expected artifact entry missing: $expected ($listing)" >&2
    exit 1
  }
}

require_drawable_entry() {
  local listing="$1"
  local resource="$2"
  grep -E -q "^res/drawable-nodpi(-v[0-9]+)?/$resource$" "$listing" || {
    echo "Expected drawable artifact entry missing: $resource ($listing)" >&2
    exit 1
  }
}

require_entry "$ARTIFACT_TMP/debug/class-entries.txt" \
  'app/openstory/catalog/feature/seed/LocalSeedCatalogSource.class'
require_drawable_entry "$ARTIFACT_TMP/debug/aar-entries.txt" \
  'catalog_debug_manga_a.webp'

for variant in benchmark non-minified; do
  require_entry "$ARTIFACT_TMP/$variant/class-entries.txt" \
    'app/openstory/catalog/feature/seed/BenchmarkCatalogSource.class'
  require_entry "$ARTIFACT_TMP/$variant/class-entries.txt" \
    'app/openstory/catalog/feature/seed/BenchmarkCatalogFixture.class'
  require_entry "$ARTIFACT_TMP/$variant/class-entries.txt" \
    'app/openstory/catalog/feature/seed/BenchmarkCatalogPreparation.class'
  require_entry "$ARTIFACT_TMP/$variant/class-entries.txt" \
    'app/openstory/catalog/feature/fixture/BenchmarkCoverFixture.class'
  require_entry "$ARTIFACT_TMP/$variant/class-entries.txt" \
    'app/openstory/catalog/feature/fixture/BenchmarkCatalogDiagnostics.class'
  require_drawable_entry "$ARTIFACT_TMP/$variant/aar-entries.txt" \
    'catalog_benchmark_manga_a.webp'
done

if grep -E -i -q \
  '(seed|Benchmark.*(Fixture|Diagnostics|Preparation)|LocalSeedCatalogSource|BenchmarkCatalogSource|ReferencePlugin|ControlledPlugin|MangaUpdatesCatalog|androidx/javascriptengine|app/openstory/plugins)' \
  "$ARTIFACT_TMP/release/class-entries.txt" "$ARTIFACT_TMP/release/aar-entries.txt"; then
  echo "Release Catalog AAR contains non-release fixture or plugin-harness content." >&2
  exit 1
fi

[[ ! -e "$ROOT_DIR/build-logic/src/main/kotlin/app/openstory/build/RoomConventionPlugin.kt" ]] || {
  echo "Generic Room convention is not admitted in Step 2." >&2
  exit 1
}

echo "V2 Step 2 build surface verified."

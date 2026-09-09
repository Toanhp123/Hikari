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
    app catalog/domain catalog/storage catalog/runtime feature/catalog; then
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

[[ ! -e "$ROOT_DIR/build-logic/src/main/kotlin/app/openstory/build/RoomConventionPlugin.kt" ]] || {
  echo "Generic Room convention is not admitted in Step 2." >&2
  exit 1
}

echo "V2 Step 2 build surface verified."

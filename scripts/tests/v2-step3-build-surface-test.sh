#!/usr/bin/env bash
set -euo pipefail

# Standalone Gradle-backed convenience check. Intentionally excluded from the host static-test stage.

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

cd "$ROOT_DIR"

./gradlew \
  verifyStep3BuildSurface \
  verifyProductionPackageStructure \
  verifyModuleBoundaries \
  --no-daemon

echo "V2 Step 3 build surface verified."

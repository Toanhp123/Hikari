#!/usr/bin/env bash
set -euo pipefail

SOURCE_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY_SCRIPT="$SOURCE_ROOT/scripts/tests/v2-build-surface-test.sh"

FIXTURE="$(mktemp -d)"
trap 'rm -rf "$FIXTURE"' EXIT

mkdir -p \
  "$FIXTURE/build-logic/src/main/kotlin" \
  "$FIXTURE/gradle"

cat > "$FIXTURE/gradle/libs.versions.toml" <<'EOF'
[versions]
navigation3 = "1.1.4"
profileInstaller = "1.4.1"

[libraries]
androidx-profileinstaller = { module = "androidx.profileinstaller:profileinstaller", version.ref = "profileInstaller" }
androidx-navigation3-runtime = { module = "androidx.navigation3:navigation3-runtime", version.ref = "navigation3" }
androidx-navigation3-ui = { module = "androidx.navigation3:navigation3-ui", version.ref = "navigation3" }
EOF

REPO_ROOT="$FIXTURE" bash "$VERIFY_SCRIPT" >/dev/null

echo "V2 Step 3 build-surface admission verified."

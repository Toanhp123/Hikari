#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="${REPO_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)}"

for removed in \
  HiltConventionPlugin.kt \
  RoomConventionPlugin.kt; do
  if find "$ROOT_DIR/build-logic/src/main/kotlin" -name "$removed" -print -quit | grep -q .; then
    echo "Inactive convention plugin still exists: $removed" >&2
    exit 1
  fi
done

[[ ! -e "$ROOT_DIR/build-logic/src/main/kotlin/app/openstory/build/packaging/CanonicalPluginPackageTask.kt" ]] || {
  echo "Inactive plugin packaging build task still exists." >&2
  exit 1
}

for forbidden in \
  'hilt = ' \
  'workManager = ' \
  'okhttp = ' \
  'navigation3 = ' \
  'javascriptEngine = ' \
  'backdrop = ' \
  'roborazzi = '; do
  if grep -Fq "$forbidden" "$ROOT_DIR/gradle/libs.versions.toml"; then
    echo "Inactive version-catalog surface remains: $forbidden" >&2
    exit 1
  fi
done

grep -Fq 'profileInstaller = ' "$ROOT_DIR/gradle/libs.versions.toml" || {
  echo "ProfileInstaller version was removed from the accepted V2 toolchain." >&2
  exit 1
}
grep -Fq 'androidx-profileinstaller' "$ROOT_DIR/gradle/libs.versions.toml" || {
  echo "ProfileInstaller library alias was removed from the accepted V2 toolchain." >&2
  exit 1
}

echo "V2 build surface verified."

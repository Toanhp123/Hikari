#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_MANIFEST="$ROOT/app/src/main/AndroidManifest.xml"
mapfile -t MANIFESTS < <(find "$ROOT" -path '*/src/main/AndroidManifest.xml' -not -path '*/build/*' -type f | sort)

for manifest in "${MANIFESTS[@]}"; do
  for forbidden in MANAGE_EXTERNAL_STORAGE READ_EXTERNAL_STORAGE WRITE_EXTERNAL_STORAGE android.permission.INTERNET; do
    if grep -q "$forbidden" "$manifest"; then
      echo "Forbidden Phase-0 permission in ${manifest#"$ROOT"/}: $forbidden" >&2
      exit 1
    fi
  done
done

exported_count=0
for manifest in "${MANIFESTS[@]}"; do
  count="$( (grep -o 'android:exported="true"' "$manifest" || true) | wc -l | tr -d ' ')"
  exported_count=$((exported_count + count))
done
[ "$exported_count" -eq 1 ] || {
  echo "Expected exactly one exported=true component across main manifests; found $exported_count." >&2
  exit 1
}

grep -q 'android:name=".MainActivity"' "$APP_MANIFEST"
grep -q 'android:exported="true"' "$APP_MANIFEST"
grep -q 'android:allowBackup="false"' "$APP_MANIFEST"
grep -q 'android:usesCleartextTraffic="false"' "$APP_MANIFEST"
echo "Security baseline static checks passed."

#!/usr/bin/env bash
set -euo pipefail

root="${WAVE10_POLICY_ROOT:-$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)}"

fail() {
  echo "Wave 10 production policy violation: $1" >&2
  exit 1
}

production_roots=(
  "$root/app/src/main"
  "$root/plugins/runtime/src/main"
)

existing_roots=()
for path in "${production_roots[@]}"; do
  [[ -d "$path" ]] && existing_roots+=("$path")
done

if ((${#existing_roots[@]} > 0)); then
  ! grep -RInE --include='*.kt' 'SecretKeySpec[[:space:]]*\([[:space:]]*byteArrayOf|AES_KEY_BYTES[[:space:]]*=' "${existing_roots[@]}" ||
    fail "APK-recoverable AES key material is forbidden"
  ! grep -RInE --include='*.kt' 'plugin[.]mangadex|plugin[.]myanimelist' "${existing_roots[@]}" ||
    fail "fake plugin IDs are forbidden"
  ! grep -RInE --include='*.kt' 'protectedSourceChecker[[:space:]]*=[[:space:]]*\{[[:space:]]*true[[:space:]]*\}' "${existing_roots[@]}" ||
    fail "host-wide protected-source shortcuts are forbidden"
fi

notification_roots=(
  "$root/app/src/main/kotlin/app/openstory/work"
  "$root/app/src/main/kotlin/app/openstory/notification"
  "$root/app/src/main/kotlin/app/openstory/notifications"
)
for path in "${notification_roots[@]}"; do
  [[ -d "$path" ]] || continue
  ! grep -RInE --include='*.kt' '"sync-update"|"latest"' "$path" ||
    fail "fake notification targets are forbidden"
done

deep_link_test="$root/app/src/test/kotlin/app/openstory/navigation/NotificationDeepLinkRoutingTest.kt"
if [[ -f "$deep_link_test" ]]; then
  ! grep -Eq 'private[[:space:]]+fun[[:space:]]+(parse|parseNotification|parseDeepLink)' "$deep_link_test" ||
    fail "notification routing tests must call the production parser"
fi

echo "Wave 10 production policy verified."

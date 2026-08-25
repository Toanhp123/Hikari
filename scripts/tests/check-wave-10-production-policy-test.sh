#!/usr/bin/env bash
set -euo pipefail

root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
policy="$root/scripts/check-wave-10-production-policy.sh"
fixture="$(mktemp -d)"
trap 'rm -rf "$fixture"' EXIT

mkdir -p "$fixture/app/src/main/kotlin" "$fixture/plugins/runtime/src/main/kotlin" \
  "$fixture/app/src/test/kotlin/app/openstory/navigation"

expect_failure() {
  local target="$1"
  local content="$2"
  find "$fixture" -type f -delete
  mkdir -p "$(dirname "$fixture/$target")"
  printf '%s\n' "$content" > "$fixture/$target"
  if WAVE10_POLICY_ROOT="$fixture" bash "$policy" >/dev/null 2>&1; then
    echo "Expected Wave 10 policy failure for $target" >&2
    exit 1
  fi
}

expect_failure app/src/main/kotlin/Credentials.kt 'val key = SecretKeySpec(byteArrayOf(1), "AES")'
expect_failure app/src/main/kotlin/Auth.kt 'val id = "plugin.mangadex"'
expect_failure app/src/main/kotlin/app/openstory/notification/Notification.kt 'val target = "sync-update"'
expect_failure app/src/main/kotlin/Work.kt 'protectedSourceChecker = { true }'
expect_failure app/src/test/kotlin/app/openstory/navigation/NotificationDeepLinkRoutingTest.kt \
  'private fun parseDeepLink(value: String) = value'

find "$fixture" -type f -delete
WAVE10_POLICY_ROOT="$fixture" bash "$policy" >/dev/null
echo "Wave 10 production policy fixtures verified."

#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

mkdir -p "$TEMP_DIR/scripts"
cp "$ROOT_DIR/scripts/verify-wave-checkpoint.sh" \
  "$TEMP_DIR/scripts/verify-wave-checkpoint.sh"
chmod +x "$TEMP_DIR/scripts/verify-wave-checkpoint.sh"

CALL_LOG="$TEMP_DIR/calls.log"

cat > "$TEMP_DIR/scripts/verify.sh" <<EOF_VERIFY
#!/usr/bin/env bash
set -euo pipefail
printf 'verify\n' >> "$CALL_LOG"
EOF_VERIFY
chmod +x "$TEMP_DIR/scripts/verify.sh"

cat > "$TEMP_DIR/scripts/verify-instrumentation.sh" <<EOF_INSTRUMENTATION
#!/usr/bin/env bash
set -euo pipefail
printf 'instrumentation api=%s serial=%s\n' "\${1:-}" "\${ANDROID_SERIAL:-}" >> "$CALL_LOG"
EOF_INSTRUMENTATION
chmod +x "$TEMP_DIR/scripts/verify-instrumentation.sh"

(
  cd "$TEMP_DIR"
  ANDROID_SERIAL_API_26="fake-api-26" \
  ANDROID_SERIAL_API_37="fake-api-37" \
    ./scripts/verify-wave-checkpoint.sh >/dev/null
)

EXPECTED_CALLS="$TEMP_DIR/expected.log"
cat > "$EXPECTED_CALLS" <<'EOF_EXPECTED'
verify
instrumentation api=26 serial=fake-api-26
instrumentation api=37 serial=fake-api-37
EOF_EXPECTED

diff -u "$EXPECTED_CALLS" "$CALL_LOG"

set +e
(
  cd "$TEMP_DIR"
  unset ANDROID_SERIAL_API_26 ANDROID_SERIAL_API_37
  ./scripts/verify-wave-checkpoint.sh >/dev/null 2>&1
)
STATUS=$?
set -e

if [[ "$STATUS" -eq 0 ]]; then
  echo "Expected missing device serials to fail." >&2
  exit 1
fi

echo "verify-wave-checkpoint.sh contract verified."

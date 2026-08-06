#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

mkdir -p "$TEMP_DIR/scripts"
cp "$ROOT_DIR/scripts/verify-wave-02-checkpoint.sh" \
  "$TEMP_DIR/scripts/verify-wave-02-checkpoint.sh"
chmod +x "$TEMP_DIR/scripts/verify-wave-02-checkpoint.sh"

CALL_LOG="$TEMP_DIR/calls.log"

cat > "$TEMP_DIR/scripts/verify-wave-checkpoint.sh" <<EOF_WAVE01
#!/usr/bin/env bash
set -euo pipefail
printf 'wave01 api26=%s api37=%s\n' \
  "\${ANDROID_SERIAL_API_26:-}" \
  "\${ANDROID_SERIAL_API_37:-}" >> "$CALL_LOG"
EOF_WAVE01
chmod +x "$TEMP_DIR/scripts/verify-wave-checkpoint.sh"

cat > "$TEMP_DIR/scripts/verify-database-instrumentation.sh" <<EOF_DATABASE
#!/usr/bin/env bash
set -euo pipefail
printf 'database api=%s serial=%s\n' \
  "\${1:-}" \
  "\${ANDROID_SERIAL:-}" >> "$CALL_LOG"
EOF_DATABASE
chmod +x "$TEMP_DIR/scripts/verify-database-instrumentation.sh"

(
  cd "$TEMP_DIR"
  ANDROID_SERIAL_API_26="fake-api-26" \
  ANDROID_SERIAL_API_37="fake-api-37" \
    ./scripts/verify-wave-02-checkpoint.sh >/dev/null
)

cat > "$TEMP_DIR/expected.log" <<'EOF_EXPECTED'
wave01 api26=fake-api-26 api37=fake-api-37
database api=26 serial=fake-api-26
database api=37 serial=fake-api-37
EOF_EXPECTED

diff -u "$TEMP_DIR/expected.log" "$CALL_LOG"

set +e
(
  cd "$TEMP_DIR"
  unset ANDROID_SERIAL_API_26 ANDROID_SERIAL_API_37
  ./scripts/verify-wave-02-checkpoint.sh >/dev/null 2>&1
)
STATUS=$?
set -e

if [[ "$STATUS" -eq 0 ]]; then
  echo "Expected missing Wave 02 device serials to fail." >&2
  exit 1
fi

echo "verify-wave-02-checkpoint.sh contract verified."

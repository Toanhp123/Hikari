#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
TEMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TEMP_DIR"' EXIT

mkdir -p "$TEMP_DIR/scripts"
cp "$ROOT_DIR/scripts/verify-wave-03-checkpoint.sh" \
  "$TEMP_DIR/scripts/verify-wave-03-checkpoint.sh"
chmod +x "$TEMP_DIR/scripts/verify-wave-03-checkpoint.sh"

CALL_LOG="$TEMP_DIR/calls.log"

cat > "$TEMP_DIR/scripts/verify.sh" <<EOF_VERIFY
#!/usr/bin/env bash
set -euo pipefail
printf 'verify gradlew=%s\n' "\${GRADLEW:-}" >> "$CALL_LOG"
EOF_VERIFY
chmod +x "$TEMP_DIR/scripts/verify.sh"

cat > "$TEMP_DIR/fake-gradlew" <<EOF_GRADLE
#!/usr/bin/env bash
set -euo pipefail
printf 'gradle %s\n' "\$*" >> "$CALL_LOG"
EOF_GRADLE
chmod +x "$TEMP_DIR/fake-gradlew"

(
  cd "$TEMP_DIR"
  GRADLEW="$TEMP_DIR/fake-gradlew" \
    ./scripts/verify-wave-03-checkpoint.sh >/dev/null
)

cat > "$TEMP_DIR/expected.log" <<EOF_EXPECTED
verify gradlew=$TEMP_DIR/fake-gradlew
gradle --no-daemon --dependency-verification strict :core:plugin-api:test :core:plugin-host:test :test:fixtures:test --stacktrace
EOF_EXPECTED

diff -u "$TEMP_DIR/expected.log" "$CALL_LOG"

echo "verify-wave-03-checkpoint.sh contract verified."

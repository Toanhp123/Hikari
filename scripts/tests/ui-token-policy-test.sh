#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
VERIFY_SCRIPT="$ROOT_DIR/scripts/verify-ui-tokens.sh"

if [[ ! -f "$VERIFY_SCRIPT" ]]; then
  echo "Missing scripts/verify-ui-tokens.sh" >&2
  exit 1
fi

if ! grep -Fq './scripts/verify-ui-tokens.sh' "$ROOT_DIR/scripts/verification-common.sh"; then
  echo "UI token policy is not wired into repository static gates" >&2
  exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "$TMP_DIR"' EXIT
mkdir -p \
  "$TMP_DIR/scripts" \
  "$TMP_DIR/app/src/main/kotlin/app/example" \
  "$TMP_DIR/core/designsystem/src/main/kotlin/app/openstory/designsystem/theme"
cp "$VERIFY_SCRIPT" "$TMP_DIR/scripts/verify-ui-tokens.sh"
chmod +x "$TMP_DIR/scripts/verify-ui-tokens.sh"

cat > "$TMP_DIR/core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/TestTokens.kt" <<'KOTLIN'
package app.openstory.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal val TestSpacing = 16.dp
internal val TestText = 16.sp
internal val TestShape = RoundedCornerShape(16.dp)
internal val TestColor = Color.White.copy(alpha = 0.8f)
internal val TestWeight = FontWeight.Bold
KOTLIN

cat > "$TMP_DIR/app/src/main/kotlin/app/example/Clean.kt" <<'KOTLIN'
package app.example

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.padding
import app.openstory.designsystem.theme.hikariSpacing

@Composable
fun Clean(modifier: Modifier = Modifier) {
    modifier.padding(MaterialTheme.hikariSpacing.space16)
}
KOTLIN

(
  cd "$TMP_DIR"
  ./scripts/verify-ui-tokens.sh >/dev/null
)

assert_rejected() {
  local name="$1"
  local source="$2"
  printf '%s\n' "$source" > "$TMP_DIR/app/src/main/kotlin/app/example/Violation.kt"
  if (
    cd "$TMP_DIR"
    ./scripts/verify-ui-tokens.sh >/dev/null 2>&1
  ); then
    echo "UI token policy did not reject $name" >&2
    exit 1
  fi
  rm -f "$TMP_DIR/app/src/main/kotlin/app/example/Violation.kt"
}

assert_rejected "dp literal" $'package app.example\nimport androidx.compose.ui.unit.dp\nval Bad = 16.dp'
assert_rejected "sp literal" $'package app.example\nimport androidx.compose.ui.unit.sp\nval Bad = 16.sp'
assert_rejected "custom rounded shape" $'package app.example\nimport androidx.compose.foundation.shape.RoundedCornerShape\nval Bad = RoundedCornerShape(percent = 20)'
assert_rejected "direct palette color" $'package app.example\nimport androidx.compose.ui.graphics.Color\nval Bad = Color.White'
assert_rejected "literal alpha" $'package app.example\nfun bad(color: androidx.compose.ui.graphics.Color) = color.copy(alpha = 0.72f)'
assert_rejected "feature font weight" $'package app.example\nimport androidx.compose.ui.text.font.FontWeight\nval Bad = FontWeight.Bold'
assert_rejected "feature font family" $'package app.example\nimport androidx.compose.ui.text.font.FontFamily\nval Bad = FontFamily.Serif'
assert_rejected "feature circle shape" $'package app.example\nimport androidx.compose.foundation.shape.CircleShape\nval Bad = CircleShape'

echo "UI token policy contract verified."

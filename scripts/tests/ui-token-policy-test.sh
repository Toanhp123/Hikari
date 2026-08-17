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
assert_rejected "raw Material shape token" $'package app.example\nimport androidx.compose.material3.MaterialTheme\nval Bad = MaterialTheme.shapes.large'
assert_rejected "direct palette color" $'package app.example\nimport androidx.compose.ui.graphics.Color\nval Bad = Color.White'
assert_rejected "literal alpha" $'package app.example\nfun bad(color: androidx.compose.ui.graphics.Color) = color.copy(alpha = 0.72f)'
assert_rejected "feature font weight" $'package app.example\nimport androidx.compose.ui.text.font.FontWeight\nval Bad = FontWeight.Bold'
assert_rejected "feature font family" $'package app.example\nimport androidx.compose.ui.text.font.FontFamily\nval Bad = FontFamily.Serif'
assert_rejected "feature circle shape" $'package app.example\nimport androidx.compose.foundation.shape.CircleShape\nval Bad = CircleShape'

# Approved Hikari visual scales must stay intentionally small.
SPACING_FILE="$ROOT_DIR/core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariSpacing.kt"
DIMENSIONS_FILE="$ROOT_DIR/core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariDimensions.kt"
SHAPES_FILE="$ROOT_DIR/core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariShapes.kt"
TYPOGRAPHY_FILE="$ROOT_DIR/core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariTypography.kt"

for retired in space2 space3 space5 space6 space7 space10 space14 space18 space28; do
  if grep -Eq "val ${retired}\\b" "$SPACING_FILE"; then
    echo "Retired spacing token remains: $retired" >&2
    exit 1
  fi
done
for value in 4 8 12 16 20 24 32; do
  if ! grep -Eq "val space${value}:[[:space:]]*Dp[[:space:]]*=[[:space:]]*${value}[.]dp" "$SPACING_FILE"; then
    echo "Approved spacing token missing or mis-sized: space${value} = ${value}.dp" >&2
    exit 1
  fi
done
spacing_tokens="$(grep -Eo 'val space[0-9]+' "$SPACING_FILE" | awk '{print $2}' | sort | tr '\n' ' ' | sed 's/ $//')"
if [[ "$spacing_tokens" != "space12 space16 space20 space24 space32 space4 space8" ]]; then
  echo "Spacing scale drifted outside the approved 4/8/12/16/20/24/32 tokens: $spacing_tokens" >&2
  exit 1
fi
icon_tokens="$(grep -Eo 'val icon[A-Za-z0-9_]+' "$DIMENSIONS_FILE" | awk '{print $2}' | sort | tr '\n' ' ' | sed 's/ $//')"
if [[ "$icon_tokens" != "iconMedium iconStandard" ]]; then
  echo "Generic icon scale must stay limited to iconMedium/iconStandard: $icon_tokens" >&2
  exit 1
fi
for retired in iconSmall iconBack glassShadowElevation contentCardShadowElevation contentCardShadowRadius; do
  if grep -Eq "(val|fun)[[:space:]]+${retired}\\b" "$DIMENSIONS_FILE"; then
    echo "Retired dimension token remains: $retired" >&2
    exit 1
  fi
done
if ! grep -Eq 'val surfaceShadowRadius:[[:space:]]*Dp[[:space:]]*=[[:space:]]*2[.]dp' "$DIMENSIONS_FILE"; then
  echo "Shared surface shadow radius must be 2.dp" >&2
  exit 1
fi
GLYPHS_FILE="$ROOT_DIR/core/designsystem/src/main/kotlin/app/openstory/designsystem/icon/HikariGlyphs.kt"
if ! grep -Fq '(dimensions.minimumTouchTarget - dimensions.iconMedium) / 2f' "$GLYPHS_FILE"; then
  echo "Touch-target glyph padding must derive from semantic touch/icon dimensions instead of owning a one-off spacing token" >&2
  exit 1
fi
for retired in compactCard prominentCard; do
  if grep -Eq "val ${retired}\\b" "$SHAPES_FILE"; then
    echo "Redundant semantic shape remains: $retired" >&2
    exit 1
  fi
done
shape_radii="$(grep -Eo 'RoundedCornerShape\([0-9]+[.]dp\)' "$SHAPES_FILE" | grep -Eo '[0-9]+' | sort -n -u | tr '\n' ' ' | sed 's/ $//')"
if [[ "$shape_radii" != "8 12 20 24 28 36" ]]; then
  echo "Rounded-corner scale drifted outside the approved 8/12/20/24/28/36 family: $shape_radii" >&2
  exit 1
fi
for retired in searchText emphasizedTitleSmall emphasizedTitleMedium heroScore readerNote; do
  if grep -Eq "val ${retired}\\b" "$TYPOGRAPHY_FILE"; then
    echo "No-op semantic typography alias remains: $retired" >&2
    exit 1
  fi
done

THEME_FILE="$ROOT_DIR/core/designsystem/src/main/kotlin/app/openstory/designsystem/theme/HikariTheme.kt"
if grep -Eq 'provides[[:space:]]+Hikari[A-Za-z]+\(\)' "$THEME_FILE"; then
  echo "HikariTheme must reuse immutable default token singletons instead of allocating token objects per composition" >&2
  exit 1
fi

echo "UI token policy contract verified."

#!/usr/bin/env bash
set -euo pipefail

GRADLE_VERSION="9.4.1"
DIST_SHA="2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb"
WRAPPER_SHA="55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c"
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

command -v java >/dev/null || { echo "JDK 17 is required" >&2; exit 1; }
JAVA_MAJOR="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
[ "$JAVA_MAJOR" = "17" ] || { echo "Expected JDK 17, found Java $JAVA_MAJOR" >&2; exit 1; }

ZIP="$TMP/gradle-${GRADLE_VERSION}-bin.zip"
curl --fail --location --proto '=https' --tlsv1.2 \
  "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
  --output "$ZIP"
printf '%s  %s\n' "$DIST_SHA" "$ZIP" | sha256sum --check --status
unzip -q "$ZIP" -d "$TMP"

GEN="$TMP/wrapper-project"
mkdir -p "$GEN"
printf 'rootProject.name = "wrapper-bootstrap"\n' > "$GEN/settings.gradle.kts"
"$TMP/gradle-${GRADLE_VERSION}/bin/gradle" -p "$GEN" wrapper \
  --gradle-version "$GRADLE_VERSION" --distribution-type bin

cp "$GEN/gradlew" "$ROOT/gradlew"
cp "$GEN/gradlew.bat" "$ROOT/gradlew.bat"
cp "$GEN/gradle/wrapper/gradle-wrapper.jar" "$ROOT/gradle/wrapper/gradle-wrapper.jar"
cp "$GEN/gradle/wrapper/gradle-wrapper.properties" "$ROOT/gradle/wrapper/gradle-wrapper.properties"
chmod +x "$ROOT/gradlew"

# Add distribution integrity pin if the generated properties do not already contain it.
if ! grep -q '^distributionSha256Sum=' "$ROOT/gradle/wrapper/gradle-wrapper.properties"; then
  printf '\ndistributionSha256Sum=%s\n' "$DIST_SHA" >> "$ROOT/gradle/wrapper/gradle-wrapper.properties"
fi
printf '%s  %s\n' "$WRAPPER_SHA" "$ROOT/gradle/wrapper/gradle-wrapper.jar" | sha256sum --check --status
grep -q "gradle-${GRADLE_VERSION}-bin.zip" "$ROOT/gradle/wrapper/gradle-wrapper.properties"
grep -q "distributionSha256Sum=${DIST_SHA}" "$ROOT/gradle/wrapper/gradle-wrapper.properties"
printf 'Official Gradle %s wrapper generated and verified.\n' "$GRADLE_VERSION"

#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
SCRIPT="$ROOT/scripts/verify-bootstrap.sh"
TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

make_fake_java() {
  local dir="$1"
  local major="$2"
  mkdir -p "$dir"
  cat > "$dir/java" <<JAVA
#!/usr/bin/env bash
echo 'openjdk version "${major}.0.0"' >&2
JAVA
  chmod +x "$dir/java"
}

make_fake_sdk() {
  local sdk="$1"
  mkdir -p "$sdk/platforms/android-37.0" "$sdk/build-tools/37.0.0" "$sdk/platform-tools"
  : > "$sdk/platforms/android-37.0/android.jar"
  : > "$sdk/build-tools/37.0.0/aapt2"
}

# Case 1: Java 21 must be rejected explicitly.
JAVA21="$TMP/java21"
SDK="$TMP/sdk"
make_fake_java "$JAVA21" 21
make_fake_sdk "$SDK"
set +e
OUTPUT="$(PATH="$JAVA21:$PATH" ANDROID_SDK_ROOT="$SDK" bash "$SCRIPT" --doctor-only 2>&1)"
STATUS=$?
set -e
[[ $STATUS -ne 0 ]] || fail "doctor accepted Java 21"
[[ "$OUTPUT" == *"Expected JDK 17"* ]] || fail "doctor did not explain Java mismatch: $OUTPUT"

# Case 2: Java 17 + SDK 37 should pass environment doctor even when wrapper JAR
# is not materialized yet; full execution owns wrapper bootstrap.
JAVA17="$TMP/java17"
make_fake_java "$JAVA17" 17
OUTPUT="$(PATH="$JAVA17:$PATH" ANDROID_SDK_ROOT="$SDK" bash "$SCRIPT" --doctor-only 2>&1)" || fail "doctor rejected valid fake environment: $OUTPUT"
[[ "$OUTPUT" == *"JDK 17: OK"* ]] || fail "missing JDK confirmation"
[[ "$OUTPUT" == *"Android SDK 37: OK"* ]] || fail "missing SDK confirmation"
[[ "$OUTPUT" == *"Wrapper JAR: PENDING"* || "$OUTPUT" == *"Wrapper JAR: OK"* ]] || fail "missing wrapper status"

# Case 3: Missing API 37.0 must fail even when Java is correct.
BAD_SDK="$TMP/bad-sdk"
mkdir -p "$BAD_SDK"
set +e
OUTPUT="$(PATH="$JAVA17:$PATH" ANDROID_SDK_ROOT="$BAD_SDK" bash "$SCRIPT" --doctor-only 2>&1)"
STATUS=$?
set -e
[[ $STATUS -ne 0 ]] || fail "doctor accepted SDK without android-37.0"
[[ "$OUTPUT" == *"Android SDK platform 37 not found"* ]] || fail "doctor did not explain missing platform: $OUTPUT"

# Case 4: Legacy android-37 must not satisfy the canonical android-37.0 contract.
LEGACY_SDK="$TMP/legacy-sdk"
mkdir -p "$LEGACY_SDK/platforms/android-37" "$LEGACY_SDK/build-tools/37.0.0"
: > "$LEGACY_SDK/platforms/android-37/android.jar"
set +e
OUTPUT="$(PATH="$JAVA17:$PATH" ANDROID_SDK_ROOT="$LEGACY_SDK" bash "$SCRIPT" --doctor-only 2>&1)"
STATUS=$?
set -e
[[ $STATUS -ne 0 ]] || fail "doctor accepted legacy android-37 in place of android-37.0"
[[ "$OUTPUT" == *"platforms/android-37.0"* ]] || fail "doctor did not name canonical android-37.0 path: $OUTPUT"

# Bash and PowerShell verifiers must share the same canonical SDK folder contract.
grep -Fq 'platforms/android-37.0/android.jar' "$ROOT/scripts/verify-bootstrap.sh" || fail "Bash verifier lost android-37.0 platform contract"
grep -Fq 'platforms\android-37.0\android.jar' "$ROOT/scripts/verify-bootstrap.ps1" || fail "PowerShell verifier lost android-37.0 platform contract"

# PowerShell 5.1 treats native stderr as error records under ErrorActionPreference=Stop.
# java -version intentionally writes to stderr, so both Windows scripts must use
# ProcessStartInfo capture instead of `2>&1` pipeline redirection.
for ps_script in "$ROOT/scripts/verify-bootstrap.ps1" "$ROOT/scripts/bootstrap-wrapper.ps1"; do
  ! grep -Fq 'java -version 2>&1' "$ps_script" || fail "$ps_script still uses PowerShell-native stderr redirection for java -version"
  grep -Fq 'System.Diagnostics.ProcessStartInfo' "$ps_script" || fail "$ps_script does not use native-process capture for java -version"
done

echo "bootstrap-execution-harness-test: PASS"

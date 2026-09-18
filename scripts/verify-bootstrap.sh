#!/usr/bin/env bash
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
EXPECTED_GRADLE="9.4.1"
EXPECTED_DIST_SHA="2ab2958f2a1e51120c326cad6f385153bb11ee93b3c216c5fccebfdfbb7ec6cb"
EXPECTED_WRAPPER_SHA="55243ef57851f12b070ad14f7f5bb8302daceeebc5bce5ece5fa6edb23e1145c"
DOCTOR_ONLY=false
HOST_ONLY=false

usage() {
  cat <<'USAGE'
Usage: bash scripts/verify-bootstrap.sh [--doctor-only | --host-only]

  --doctor-only  Validate JDK 17, SDK 37 and wrapper pins without running Gradle.
  --host-only    Run wrapper/help/verifyFast/verifyRelease but skip device smoke test.
  (no option)    Run the full bootstrap execution gate including connectedDebugAndroidTest.
USAGE
}

for arg in "$@"; do
  case "$arg" in
    --doctor-only) DOCTOR_ONLY=true ;;
    --host-only) HOST_ONLY=true ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Unknown option: $arg" >&2; usage >&2; exit 2 ;;
  esac
done

if "$DOCTOR_ONLY" && "$HOST_ONLY"; then
  echo "--doctor-only and --host-only are mutually exclusive" >&2
  exit 2
fi

command -v java >/dev/null 2>&1 || { echo "JDK 17 is required; java was not found" >&2; exit 1; }
JAVA_MAJOR="$(java -version 2>&1 | sed -n '1s/.*version "\([0-9]*\).*/\1/p')"
[ "$JAVA_MAJOR" = "17" ] || { echo "Expected JDK 17, found Java ${JAVA_MAJOR:-unknown}" >&2; exit 1; }
echo "JDK 17: OK"

SDK_ROOT="${ANDROID_SDK_ROOT:-${ANDROID_HOME:-}}"
[ -n "$SDK_ROOT" ] || { echo "ANDROID_SDK_ROOT (or ANDROID_HOME) must point to an Android SDK" >&2; exit 1; }
[ -f "$SDK_ROOT/platforms/android-37.0/android.jar" ] || {
  echo "Android SDK platform 37 not found under $SDK_ROOT/platforms/android-37.0" >&2
  exit 1
}
[ -d "$SDK_ROOT/build-tools/37.0.0" ] || {
  echo "Android build-tools 37.0.0 not found under $SDK_ROOT/build-tools/37.0.0" >&2
  exit 1
}
echo "Android SDK 37: OK"

WRAPPER_PROPS="$ROOT/gradle/wrapper/gradle-wrapper.properties"
[ -f "$WRAPPER_PROPS" ] || { echo "Missing $WRAPPER_PROPS" >&2; exit 1; }
grep -q "gradle-${EXPECTED_GRADLE}-bin.zip" "$WRAPPER_PROPS" || {
  echo "Gradle wrapper must pin ${EXPECTED_GRADLE}" >&2
  exit 1
}
grep -q "distributionSha256Sum=${EXPECTED_DIST_SHA}" "$WRAPPER_PROPS" || {
  echo "Gradle distribution SHA-256 pin is missing or incorrect" >&2
  exit 1
}
echo "Wrapper properties: OK"

DAEMON_JVM_PROPS="$ROOT/gradle/gradle-daemon-jvm.properties"
if [ -f "$DAEMON_JVM_PROPS" ]; then
  DAEMON_JVM_VERSION="$(sed -n 's/^toolchainVersion=//p' "$DAEMON_JVM_PROPS" | tail -n 1)"
  [ "$DAEMON_JVM_VERSION" = "17" ] || {
    echo "Gradle daemon JVM criteria must target JDK 17, found ${DAEMON_JVM_VERSION:-unset}" >&2
    exit 1
  }
  echo "Gradle daemon JVM criteria: JDK 17 OK"
fi

WRAPPER_JAR="$ROOT/gradle/wrapper/gradle-wrapper.jar"
if [ -f "$WRAPPER_JAR" ]; then
  printf '%s  %s\n' "$EXPECTED_WRAPPER_SHA" "$WRAPPER_JAR" | sha256sum --check --status || {
    echo "Gradle wrapper JAR checksum mismatch" >&2
    exit 1
  }
  echo "Wrapper JAR: OK"
else
  if "$DOCTOR_ONLY"; then
    echo "Wrapper JAR: PENDING (full run will materialize it with scripts/bootstrap-wrapper.sh)"
  else
    echo "Wrapper JAR missing; generating the official ${EXPECTED_GRADLE} wrapper..."
    bash "$ROOT/scripts/bootstrap-wrapper.sh"
    printf '%s  %s\n' "$EXPECTED_WRAPPER_SHA" "$WRAPPER_JAR" | sha256sum --check --status || {
      echo "Generated Gradle wrapper JAR checksum mismatch" >&2
      exit 1
    }
    echo "Wrapper JAR: OK"
  fi
fi

if "$DOCTOR_ONLY"; then
  echo "Bootstrap environment doctor: PASS"
  exit 0
fi

cd "$ROOT"

echo "== Gradle runtime =="
bash ./gradlew --version --no-daemon

echo "== Gradle configuration/help =="
bash ./gradlew help --no-daemon

echo "== Fast quality gate =="
bash ./gradlew verifyFast --no-daemon

if ! "$HOST_ONLY"; then
  ADB=""
  if command -v adb >/dev/null 2>&1; then
    ADB="$(command -v adb)"
  elif [ -x "$SDK_ROOT/platform-tools/adb" ]; then
    ADB="$SDK_ROOT/platform-tools/adb"
  elif [ -x "$SDK_ROOT/platform-tools/adb.exe" ]; then
    ADB="$SDK_ROOT/platform-tools/adb.exe"
  else
    echo "adb is required for the full bootstrap gate; use --host-only to skip device smoke" >&2
    exit 1
  fi

  if [ -n "${ANDROID_SERIAL:-}" ]; then
    "$ADB" -s "$ANDROID_SERIAL" get-state | grep -qx 'device' || {
      echo "ANDROID_SERIAL=$ANDROID_SERIAL is not an online device" >&2
      exit 1
    }
  else
    ONLINE_COUNT="$($ADB devices | awk 'NR > 1 && $2 == "device" { count++ } END { print count+0 }')"
    [ "$ONLINE_COUNT" -eq 1 ] || {
      echo "Expected exactly one online adb device when ANDROID_SERIAL is unset; found $ONLINE_COUNT" >&2
      exit 1
    }
  fi

  echo "== Android launch smoke =="
  bash ./gradlew :app:connectedDebugAndroidTest --no-daemon
fi

echo "== Release-like gate =="
bash ./gradlew verifyRelease --no-daemon

echo "Bootstrap execution gate: PASS"

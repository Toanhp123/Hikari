#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

GRADLEW="${GRADLEW:-./gradlew}"
export GRADLEW

: "${ANDROID_SERIAL_API_26:?Set ANDROID_SERIAL_API_26 to an API 26 emulator/device serial.}"
: "${ANDROID_SERIAL_API_37:?Set ANDROID_SERIAL_API_37 to an API 37 emulator/device serial.}"

./scripts/verify.sh

ANDROID_SERIAL="$ANDROID_SERIAL_API_26" \
  ./scripts/verify-instrumentation.sh 26

ANDROID_SERIAL="$ANDROID_SERIAL_API_37" \
  ./scripts/verify-instrumentation.sh 37

echo "Wave checkpoint verification passed on API 26 and API 37."

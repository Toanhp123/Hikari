#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
cd "$ROOT_DIR"

: "${ANDROID_SERIAL_API_26:?Set ANDROID_SERIAL_API_26 to an API 26 emulator/device serial.}"
: "${ANDROID_SERIAL_API_37:?Set ANDROID_SERIAL_API_37 to an API 37 emulator/device serial.}"

./scripts/checkpoints/app-shell.sh

ANDROID_SERIAL="$ANDROID_SERIAL_API_26" \
  ./scripts/instrumentation/database.sh 26

ANDROID_SERIAL="$ANDROID_SERIAL_API_37" \
  ./scripts/instrumentation/database.sh 37

echo "Database checkpoint passed on API 26 and API 37."

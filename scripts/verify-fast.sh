#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
exec "$ROOT/gradlew" -p "$ROOT" verifyFast --no-daemon "$@"

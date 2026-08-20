#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
FULL_VERIFY="$ROOT_DIR/scripts/verify.sh"
FAST_VERIFY="$ROOT_DIR/scripts/verify-fast.sh"
COMMON_VERIFY="$ROOT_DIR/scripts/verification-common.sh"
BOUNDARY_VERIFY="$ROOT_DIR/scripts/check-module-dependencies.sh"
GRADLE_PROPERTIES="$ROOT_DIR/gradle.properties"

[[ -f "$FAST_VERIFY" ]] || {
  echo "Missing scripts/verify-fast.sh development verification entry point." >&2
  exit 1
}
[[ -f "$COMMON_VERIFY" ]] || {
  echo "Missing scripts/verification-common.sh shared repository gates." >&2
  exit 1
}

full_source="$(cat "$FULL_VERIFY")"
fast_source="$(cat "$FAST_VERIFY")"
boundary_source="$(cat "$BOUNDARY_VERIFY")"
properties_source="$(cat "$GRADLE_PROPERTIES")"

[[ "$full_source" == *"verifyArchitecture"* ]] || {
  echo "Full verification must include verifyArchitecture in its Gradle invocation." >&2
  exit 1
}
[[ "$full_source" != *"./scripts/check-module-dependencies.sh"* ]] || {
  echo "Full verification must not spawn a separate Gradle architecture build." >&2
  exit 1
}
[[ "$full_source" != *"--no-daemon"* ]] || {
  echo "Full verification must allow the reusable Gradle daemon." >&2
  exit 1
}
[[ "$fast_source" != *"--no-daemon"* ]] || {
  echo "Fast verification must allow the reusable Gradle daemon." >&2
  exit 1
}
[[ "$boundary_source" != *"--no-daemon"* ]] || {
  echo "Standalone architecture verification must allow the reusable Gradle daemon." >&2
  exit 1
}

for task in verifyArchitecture :build-logic:test test testDebugUnitTest detekt; do
  [[ "$fast_source" == *"$task"* ]] || {
    echo "Fast verification is missing Gradle task: $task" >&2
    exit 1
  }
done

[[ "$fast_source" != *"lintDebug"* ]] || {
  echo "Fast verification must leave Android lint to the full gate." >&2
  exit 1
}
[[ "$fast_source" != *":app:assembleDebug"* ]] || {
  echo "Fast verification must leave app assembly to the full gate." >&2
  exit 1
}

for task in verifyArchitecture :build-logic:test test testDebugUnitTest lintDebug detekt :app:assembleDebug; do
  [[ "$full_source" == *"$task"* ]] || {
    echo "Full verification is missing Gradle task: $task" >&2
    exit 1
  }
done

[[ "$properties_source" == *"org.gradle.caching=true"* ]] || {
  echo "Local Gradle build cache must be enabled for repeated verification." >&2
  exit 1
}

for script in "$FULL_VERIFY" "$FAST_VERIFY"; do
  source_text="$(cat "$script")"
  [[ "$source_text" == *"verification-common.sh"* ]] || {
    echo "Verification entry points must share the repository/static gate helper: $script" >&2
    exit 1
  }
  [[ "$source_text" == *"run_repository_static_gates"* ]] || {
    echo "Verification entry points must run repository/static gates: $script" >&2
    exit 1
  }
  [[ "$source_text" == *"ROOM_SCHEMA_FINGERPRINT"* ]] || {
    echo "Verification entry points must protect Room schema stability: $script" >&2
    exit 1
  }
done

echo "Verification entry point contract verified."

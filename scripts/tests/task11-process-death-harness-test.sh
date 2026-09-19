#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/../.." && pwd)"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

TEST="$ROOT/app/src/androidTest/kotlin/app/universalmedia/ProcessDeathSafEndToEndTest.kt"
BASH_HARNESS="$ROOT/scripts/task11-process-death.sh"
PS_HARNESS="$ROOT/scripts/task11-process-death.ps1"
FIXTURE="$ROOT/playback/media3/src/androidTest/assets/fixture.mp4"

for file in "$TEST" "$BASH_HARNESS" "$PS_HARNESS" "$FIXTURE"; do
  [[ -f "$file" ]] || fail "missing Task 11 artifact: ${file#"$ROOT"/}"
done

for token in \
  'phaseA_establishDurableState' \
  'phaseB_reconstructAfterForceStop' \
  'persistedUriPermissions' \
  'SourceResolution.Ready' \
  'SELECT COUNT(*) FROM media' \
  'SELECT COUNT(*) FROM source_binding' \
  'SELECT COUNT(*) FROM asset' \
  'SELECT COUNT(*) FROM library_entry' \
  'SELECT COUNT(*) FROM scan_run' \
  'PAUSE_CHECKPOINT_TOLERANCE_MS' \
  'RESUME_TOLERANCE_MS' \
  'waitForPlaybackEstablished' \
  'failed before readiness' \
  'last playback state=' \
  'Lifecycle.State.RESUMED' \
  'instrumentation.waitForIdleSync()' \
  'waitForPlayerRoute' \
  'Could not connect to playback.'; do
  grep -Fq "$token" "$TEST" || fail "Task 11 instrumentation is missing: $token"
done

if grep -Fq 'grant.uri.toString().contains("HikariTask11")' "$TEST"; then
  fail "persisted SAF tree document IDs are opaque; do not infer the selected folder from URI text"
fi

for harness in "$BASH_HARNESS" "$PS_HARNESS"; do
  grep -Fq 'HikariTask11' "$harness" || fail "harness must stage the fixture in a DocumentsUI-visible folder"
  grep -Fq 'phaseA_establishDurableState' "$harness" || fail "harness must invoke Phase A"
  grep -Fq 'phaseB_reconstructAfterForceStop' "$harness" || fail "harness must invoke Phase B"
  grep -Fq 'force-stop' "$harness" || fail "harness must perform external real force-stop"
  grep -Fq 'fixture.mp4' "$harness" || fail "harness must reuse the self-owned MP4 fixture"
done

[[ "$(grep -c 'am instrument' "$BASH_HARNESS")" -eq 1 ]] || \
  fail "Bash harness should define one instrumentation primitive reused for two invocations"
[[ "$(grep -c "Invoke-Phase -Method" "$PS_HARNESS")" -eq 2 ]] || \
  fail "PowerShell harness must execute exactly two phase invocations"
[[ "$(grep -c "Invoke-Adb @('install'" "$PS_HARNESS")" -eq 2 ]] || \
  fail "PowerShell harness must install target/test APKs once before both phases"

force_line="$(grep -n 'am force-stop' "$BASH_HARNESS" | cut -d: -f1)"
phase_a_line="$(grep -n '^run_phase phaseA_establishDurableState$' "$BASH_HARNESS" | cut -d: -f1)"
phase_b_line="$(grep -n '^run_phase phaseB_reconstructAfterForceStop$' "$BASH_HARNESS" | cut -d: -f1)"
[[ -n "$force_line" && -n "$phase_a_line" && -n "$phase_b_line" ]] || fail "could not inspect Bash phase ordering"
(( phase_a_line < force_line && force_line < phase_b_line )) || \
  fail "external force-stop must occur between Phase A and Phase B"

if grep -R -n 'task11-process-death-evidence.json' \
  "$ROOT/app/src/main" "$ROOT/core" "$ROOT/data/src/main" "$ROOT/feature" \
  "$ROOT/ingestion/local/src/main" "$ROOT/playback" "$ROOT/source" "$ROOT/storage" \
  --include='*.kt' --exclude-dir=build | grep -v '/src/androidTest/' >/dev/null; then
  fail "production code must not consume Task 11 test evidence"
fi

bash -n "$BASH_HARNESS"
echo 'task11-process-death-harness-test: PASS'
